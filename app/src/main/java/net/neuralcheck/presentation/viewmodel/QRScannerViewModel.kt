package net.neuralcheck.presentation.viewmodel

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.neuralcheck.R
import net.neuralcheck.data.auth.AuthManager
import net.neuralcheck.data.local.db.entity.ScanEntity
import net.neuralcheck.data.remote.FirebaseAnalysisService
import net.neuralcheck.data.remote.ReasonCodeRead
import net.neuralcheck.data.remote.ScanSessionHolder
import net.neuralcheck.data.remote.joinScanSessionWithDeviceIdentity
import net.neuralcheck.data.repository.ScanRepository
import net.neuralcheck.domain.consent.ConsentPartRead
import net.neuralcheck.util.LogRedaction
import net.neuralcheck.util.QrCodeHelper
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class QRScannerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val scanRepository: ScanRepository,
    private val authManager: AuthManager,
    // Wired up so the scanner can handle owner-issued session deeplinks
    // (`neuralcheck://session/NC-XXXXXX`) — the scanner used to only handle
    // 36-char scan UUIDs, which made the customer-device flow unusable
    // because every session QR was decoded as "invalid".
    private val analysisService: FirebaseAnalysisService,
    private val sessionHolder: ScanSessionHolder
) : ViewModel() {

    /**
     * Outcome of decoding a single QR frame:
     *  - [Scanning]: idle / no decode yet, or after a transient invalid clears.
     *  - [Found]: the QR resolved to a local scan UUID — used by the
     *    post-repair link flow to chain a new scan to the previous one.
     *  - [JoinSuccess]: the QR was a session deeplink and we've successfully
     *    claimed it via `joinScanSession`. Carries the resolved sessionId
     *    so the screen can route into the customer-device scan flow, and what the join's
     *    answer said about the shop's consent part (ق15: [ConsentPartRead], an absent key is
     *    [ConsentPartRead.Absent], not `false`) - the screen routes by it (`afterJoinRoute`).
     *  - [Joining]: a session deeplink was decoded and the network call is
     *    in flight. Used to gate the viewfinder + freeze further decodes.
     *  - [JoinFailed]: the join was refused or failed; [JoinFailed.message] is the text the chip shows. Batch 2, item 2: it is the SAME
     *    approved text the typed-code screen shows for the same refusal (SessionMessages.kt, decided by the server's details.reasonCode). What has no
     *    approved text shows the no-connection text when the failure says there is no connection (batch 5, item 1: the same reading as the typed screen) and
     *    the fixed qr_join_failed text for everything else - it used to be that fixed text for every failure, a disconnect included.
     *  - [NotFound]: parsed a UUID but no matching local scan — tells the
     *    user "we recognised the format but don't have that scan locally".
     *  - [Invalid]: the QR didn't match either format we understand.
     */
    sealed class QrState {
        data object Scanning : QrState()
        data class Found(val scan: ScanEntity) : QrState()
        data object Joining : QrState()
        data class JoinSuccess(val sessionId: String, val consentPart: ConsentPartRead) : QrState()
        data class JoinFailed(val message: String) : QrState()
        data object NotFound : QrState()
        data object Invalid : QrState()
    }

    private val _state = MutableStateFlow<QrState>(QrState.Scanning)
    val state: StateFlow<QrState> = _state

    // Pre-repair pickers must only show the signed-in user's own scans —
    // selecting "before-repair" must never let a technician pair a scan
    // belonging to another account on the same device. Datacollect (no auth)
    // emits an empty list — that flavor uses a different scan-link flow.
    val preRepairScans: StateFlow<List<ScanEntity>> =
        authManager.uidFlow
            .flatMapLatest { uid ->
                if (uid.isNullOrBlank()) flowOf(emptyList())
                else scanRepository.getScansByTypeForUser(uid, "pre_repair")
            }
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private var lastCode: String? = null

    // ---- A-SUB wave 1, item 6 (user decision 6: «نعم مع تمديده إلى QR غير الصالح») - one answer per code held in front of the camera ----

    /**
     * The clock the quiet-time rule reads, in milliseconds. A seam for the test (virtual time, as the chip's display time is measured), not a setting: the default is the monotonic clock.
     */
    internal var clock: () -> Long = { System.nanoTime() / 1_000_000L }

    /**
     * What a decoded frame does to the phone, besides the vibration: a seam for the test, like [clock]. The default is the real vibration. The decision of WHEN to vibrate is
     * [onQrDetected]'s and [joinSession]'s (once per code shown, never for an automatic retry); this only does it.
     */
    internal var vibrate: () -> Unit = { vibrateDevice() }

    /** The previous decoded frame: its raw text and when it came. The quiet-time rule asks "was THIS code in the previous frames, or did it leave and come back". */
    private var previousFrameRaw: String? = null
    private var previousFrameAtMs: Long = 0L

    /**
     * The raw text of a code the scanner has ANSWERED and must not ask about again while it stays in front of the camera: a join the server refused with a `reasonCode` (any code,
     * known or not - the server answered, and the same code gets the same answer), or a QR that is no format this app reads ([QrState.Invalid]). Cleared when the code leaves the camera
     * for [QUIET_MS] and comes back (a deliberate second try), or when another code is dispatched. Before this, `resetToScanning` emptied `lastCode` after the chip and the camera,
     * still decoding the same frame every few milliseconds, started the call again: a chip, a call and a vibration about every 6 seconds for as long as the code was held.
     */
    private var refusedCode: String? = null

    /** The raw text of the last code a join was started for, to tell the first decode of a code (vibrates) from the automatic retry of the same code (does not). */
    private var attemptedCode: String? = null

    /**
     * Decode dispatch order matters because the three formats overlap a
     * little (e.g., `NC-AB3F7K` could be either a session code or — in
     * the future — a free-text scanId). Order:
     *   1. Session deeplink — `neuralcheck://session/NC-XXXX` or raw
     *      `NC-XXXX` text. Resolved via `joinScanSession` callable; on
     *      success we route the screen to the customer-device scan flow.
     *   2. Scan deeplink — `neuralcheck://scan/{uuid}` (legacy QR for the
     *      post-repair link flow). Strips the prefix so the UUID lookup
     *      below recognises it.
     *   3. Bare UUID — kept for backward compatibility with QRs that were
     *      encoded as raw UUIDs before the deeplink wrapper landed.
     */
    fun onQrDetected(rawCode: String) {
        // A-SUB wave 1, item 10 (user decision «الآن»): the decoded text is a session code or a link carrying one, and logcat is readable by anything with the device - so the line
        // prints a HASH of it (LogRedaction.hashId, the format the server's own lines use) and its length, enough to see that the same code came twice, never the code. The event stays.
        Log.d(TAG_QR, "onQrDetected: raw=${LogRedaction.hashId(rawCode)} len=${rawCode.length}")
        // A-SUB wave 1, item 6: how long this code has been in front of the camera without leaving. Every frame passes here first - including the frames the guards below drop -
        // so the time of the previous frame is current whatever state the scanner is in.
        val now = clock()
        val quiet = rawCode == previousFrameRaw && now - previousFrameAtMs < QUIET_MS
        previousFrameRaw = rawCode
        previousFrameAtMs = now
        if (rawCode == refusedCode) {
            if (quiet) return
            refusedCode = null // it left and came back: a deliberate second try
        }
        if (rawCode == lastCode) return
        if (_state.value is QrState.Found ||
            _state.value is QrState.Joining ||
            _state.value is QrState.JoinSuccess) return
        lastCode = rawCode
        // the first decode of a code vibrates; the same code decoded again right after a failure with no named reason is the scanner's own retry, and does not
        val automatic = rawCode == attemptedCode && quiet

        // 1. Session code path. parseSessionCode now recognises three input
        //    shapes: bare `NC-XXXX`, `neuralcheck://session/NC-XXXX`, and
        //    `https://neuralcheck.net/scan?code=NC-XXXX`. It returns null
        //    on anything that doesn't look like a session code (notably
        //    36-char UUIDs are rejected by the {4,20} length window), so
        //    we trust it directly — earlier code added a defensive
        //    "must start with NC-" gate that mis-rejected codes whose
        //    backend format the client can't predict.
        val sessionCode = QrCodeHelper.parseSessionCode(rawCode)
        Log.d(TAG_QR, "  parseSessionCode -> ${LogRedaction.hashId(sessionCode)}")
        if (sessionCode != null) {
            Log.d(TAG_QR, "  dispatch: joinSession(${LogRedaction.hashId(sessionCode)})")
            joinSession(sessionCode, rawCode, automatic)
            return
        }

        // 2. Scan deeplink — strip `neuralcheck://scan/` prefix to get the UUID.
        val uuidFromDeeplink = QrCodeHelper.parseQrContent(rawCode)
        // 3. Bare UUID fallback.
        val uuid = uuidFromDeeplink ?: runCatching {
            java.util.UUID.fromString(rawCode).toString()
        }.getOrNull()
        Log.d(TAG_QR, "  parseQrContent -> $uuidFromDeeplink, finalUuid -> $uuid")

        if (uuid == null) {
            Log.d(TAG_QR, "  dispatch: Invalid (no parser matched)")
            // A-SUB wave 1, item 6 (user decision 6): a foreign QR held in front of the camera shows its chip once, not every 2 seconds
            refusedCode = rawCode
            _state.value = QrState.Invalid
            viewModelScope.launch {
                delay(2000)
                if (_state.value is QrState.Invalid) resetToScanning()
            }
            return
        }

        viewModelScope.launch {
            val scan = scanRepository.getScanById(uuid)
            if (scan != null) {
                vibrate()
                _state.value = QrState.Found(scan)
            } else {
                _state.value = QrState.NotFound
                delay(2500)
                if (_state.value is QrState.NotFound) resetToScanning()
            }
        }
    }

    private companion object {
        const val TAG_QR = "NeuralCheck.QR"

        /**
         * How long a code must be out of the camera's sight before it counts as shown again (see [refusedCode]). A judgment, not a user decision: 1.5 s is longer than the gap between two
         * frames of a code held still (the analyzer decodes the latest frame as fast as it can, tens of milliseconds) and shorter than lowering a phone and raising it again. No measured
         * value backs it - the device check reads it (item 1 of the A-SUB device list).
         */
        const val QUIET_MS = 1_500L

        /**
         * How long a refused join's text stays on the chip before the scanner goes back to scanning. It was 2.5 s when the chip said the same four
         * words for every failure; the approved texts (batch 2, item 2) are a full sentence, so they stay longer. A judgment, not a user
         * decision: 6 s is about the time to read the longest approved text once (no measured value backs it).
         */
        const val JOIN_FAILED_DISPLAY_MS = 6_000L
    }

    /**
     * Owner-issued session-code branch of [onQrDetected]. Calls
     * `joinScanSession`, stashes the returned sessionId in the holder so
     * AiScoringEngine can stamp the eventual analyzeDevice call with it,
     * and emits [QrState.JoinSuccess] so the screen can navigate. Failures
     * surface as [QrState.JoinFailed] with the server's reason — the
     * screen turns that into a localised toast. After the chip `lastCode` is cleared, so the
     * scanner can try the same QR again - by itself when the failure named no reason (a disconnect: quietly, no vibration), and only after the code left the camera for
     * [QUIET_MS] when the server named one ([refusedCode], A-SUB wave 1, item 6).
     */
    private fun joinSession(code: String, rawCode: String, automatic: Boolean) {
        _state.value = QrState.Joining
        attemptedCode = rawCode
        if (!automatic) vibrate()
        viewModelScope.launch {
            try {
                // Order 2026-10-10: sign in anonymously first (when not signed in),
                // so the join carries the device's own uid.
                // Batch 3, item 3: an answer without a session id is thrown by this function ([JoinAnswerWithoutSessionIdException]) and ends in the catch below,
                // like every other failure with no approved text - the chip text this point always showed, and the `session-refused` and `session-join-failed` lines.
                val joined = joinScanSessionWithDeviceIdentity(authManager, analysisService, code)
                // feat/technician-evaluation: keep the whole join context
                // (ownerId + evaluationEnabled) for the evaluation card gate.
                sessionHolder.setFromJoin(joined.answer)
                _state.value = QrState.JoinSuccess(joined.sessionId, joined.answer.consentPart())
            } catch (t: Throwable) {
                // the text is decided by the server's details.reasonCode, from the same table as the typed-code screen (SessionMessages.kt)
                _state.value = QrState.JoinFailed(message = sessionErrorMessage(context, SessionCall.JOIN_QR, t))
                // A-SUB wave 1, item 6: a refusal that carries a reasonCode is the server's answer - asking again while the code is still in front of the camera gets the same answer. A failure
                // with none (a disconnect, a timeout, `internal`, an answer with no session id) is not an answer: the scanner still retries it by itself, quietly (no vibration).
                if (ReasonCodeRead.of(t) !is ReasonCodeRead.Absent) {
                    refusedCode = rawCode
                    previousFrameRaw = rawCode
                    previousFrameAtMs = clock()
                }
                delay(JOIN_FAILED_DISPLAY_MS)
                if (_state.value is QrState.JoinFailed) resetToScanning()
            }
        }
    }

    fun resetToScanning() {
        _state.value = QrState.Scanning
        lastCode = null
    }

    @Suppress("DEPRECATION")
    private fun vibrateDevice() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(VibratorManager::class.java)
                vm?.defaultVibrator?.vibrate(
                    VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                val v = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v?.vibrate(VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    v?.vibrate(120)
                }
            }
        } catch (_: Exception) {}
    }
}
