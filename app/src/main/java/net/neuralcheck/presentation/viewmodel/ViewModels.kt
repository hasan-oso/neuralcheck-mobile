package net.neuralcheck.presentation.viewmodel

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.ktx.Firebase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import net.neuralcheck.R
import net.neuralcheck.data.auth.AuthManager
import net.neuralcheck.data.local.db.entity.PeerInputEntity
import net.neuralcheck.data.local.db.entity.ScanEntity
import net.neuralcheck.data.local.db.entity.TechnicianInputEntity
import net.neuralcheck.data.local.prefs.SecurePreferences
import net.neuralcheck.domain.evaluation.EvaluationSubmitter
import net.neuralcheck.domain.scoring.networkErrorMessageOrNull
import net.neuralcheck.data.remote.CancelReason
import net.neuralcheck.data.remote.CancelRole
import net.neuralcheck.data.remote.CustomerSessionWatch
import net.neuralcheck.data.remote.DefectType
import net.neuralcheck.data.remote.FirebaseAnalysisService
import net.neuralcheck.data.remote.PairLinkClient
import net.neuralcheck.data.remote.ScanPurposeChoice
import net.neuralcheck.data.remote.ScanSessionHolder
import net.neuralcheck.data.remote.ScanSessionRecords
import net.neuralcheck.data.remote.SessionCanceller
import net.neuralcheck.data.remote.SessionDocWatcher
import net.neuralcheck.data.remote.SessionStatus
import net.neuralcheck.data.remote.SubscriptionStatus
import net.neuralcheck.data.remote.ReportFirstOpenedRecorder
import net.neuralcheck.data.remote.RepeatScanClient
import net.neuralcheck.data.remote.joinScanSessionWithDeviceIdentity
import net.neuralcheck.data.remote.model.AiPipeline
import net.neuralcheck.data.repository.*
import net.neuralcheck.domain.model.isComplete
import net.neuralcheck.service.stress.StressTestService
import net.neuralcheck.service.sync.SyncScheduler
import net.neuralcheck.util.InAppNotifier
import net.neuralcheck.util.LocaleHelper
import net.neuralcheck.util.LogRedaction
import net.neuralcheck.util.NotificationHelper
import java.util.concurrent.TimeUnit
import javax.inject.Inject

// flatMapLatest is still flagged @ExperimentalCoroutinesApi in 1.7 even though
// it's been stable in practice for years. Opt-in at the class level so we
// don't have to sprinkle annotations on every Flow chain.
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val scanRepository: ScanRepository,
    private val securePreferences: SecurePreferences,
    private val firebaseService: FirebaseAnalysisService,
    private val authManager: AuthManager,
    private val subscriptionRefreshBus: net.neuralcheck.data.remote.SubscriptionRefreshBus
) : ViewModel() {

    /**
     * Snapshot of the signed-in user's most recent cloud scan. Optional — the
     * Home screen hides the "Last Scan" card when this is null (unauthenticated
     * user, fresh account, or Firestore fetch failure). We deliberately
     * duplicate the essential fields here instead of reusing
     * [ScanHistoryViewModel.CloudScan] so this VM stays decoupled from that
     * screen and can ship a minimal read without the full history logic.
     */
    data class LastScan(
        val scanId: String,
        val deviceModel: String,
        /**
         * Order 2026-09-25 (القرار ٨٦ البند ٤): the real letter (V6 or V7),
         * OR — when this is a V7 report whose letter is legitimately absent
         * (harnessError / noDataAtAll) — [net.neuralcheck.R.string.report_grade_unavailable]'s
         * resolved text ("الحرف غير متوفر"), never the "?" sentinel.
         *
         * ~~Only a pipeline this reader recognises as NEITHER V6 nor V7
         * still falls back to "?" — an unmapped future shape this reader
         * has no contract for at all (unchanged, out of this order's
         * scope).~~ **Order 2026-09-26 (القرار ١٠٢ البند ٢) — no longer
         * true, kept struck-through not deleted (rule 8): an unrecognised
         * pipeline shape now ALSO resolves to this same
         * `report_grade_unavailable` text, not "?" — see [buildHomeLastScan]'s
         * own doc.**
         */
        val grade: String,
        /**
         * V6 summary/verdict text, OR — for a V7 report whose letter is
         * absent — [V7OverallSummary.absenceReasonText] (the SAME text
         * `AiV7OverallCard` shows for the identical state), reusing this
         * existing secondary-text slot rather than adding a new one.
         */
        val summary: String,
        val createdAtMs: Long,
        /**
         * Phase 3-a / rule-22 follow-through: this reader reads the RAW
         * `reports/{scanId}` doc directly (not through
         * [net.neuralcheck.data.remote.model.AiAnalysisResponse]'s Gson
         * mapping). Since القرار ٨٦ البند ٤ it ALSO reads the V7 shape
         * (`aiResponse.overall.letter` — see [net.neuralcheck.data.remote.readV7OverallSummary]),
         * not just V6's flat `grade`/`score`/`summary`. [isFlatShapeRecognized]
         * now means "the pipeline is V6/legacy" specifically — false means a
         * V7 doc (handled via the V7 reader above, not a placeholder) OR a
         * genuinely unrecognised future pipeline shape (still "?"/null/"" —
         * this reader truly has no contract for it). See
         * [net.neuralcheck.data.remote.model.AiPipeline.isRecognizedFlatShape].
         */
        val isFlatShapeRecognized: Boolean = true,
        /** Raw `pipeline` value off the doc, or null when absent (legacy).
         *  Preserved rather than swallowed so an unrecognised value is
         *  visible for diagnosis instead of silently read as V6. */
        val pipeline: String? = null
    )

    private val _isFirstLaunch = MutableStateFlow(!securePreferences.isOnboardingComplete)
    val isFirstLaunch: StateFlow<Boolean> = _isFirstLaunch

    // Reactive view of the local scan DB, scoped to the signed-in account.
    // flatMapLatest re-queries Room as soon as the auth state changes so an
    // account switch on the same device flips the tiles to the new user's
    // numbers without any manual refresh. Datacollect (no auth) emits an
    // empty list — that flavor never used these tiles anyway.
    private val allScans: StateFlow<List<net.neuralcheck.data.local.db.entity.ScanEntity>> =
        authManager.uidFlow
            .flatMapLatest { uid ->
                if (uid.isNullOrBlank()) kotlinx.coroutines.flow.flowOf(emptyList())
                else scanRepository.getAllScansByUserId(uid)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val scanCount: StateFlow<Int> =
        allScans.map { it.size }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val scansThisMonth: StateFlow<Int> =
        allScans.map { scans ->
            val monthStart = firstOfCurrentMonthMs()
            scans.count { it.timestamp >= monthStart }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    // Greeting on the Home hero card. Falls back to the localized "Guest" copy
    // when there is no Firebase user (datacollect) or when the user's
    // displayName/email are both blank.
    val displayName: StateFlow<String> = MutableStateFlow(resolveDisplayName())

    // `null` = not yet loaded / backend unreachable. The Home screen treats
    // null the same as "not a shop" and hides the Scan-Other-Device CTA,
    // which is the safe default — showing the button then failing the
    // createScanSession call later would be a worse UX than never offering it
    // to users who don't have the plan for it.
    private val _subscription = MutableStateFlow<SubscriptionStatus?>(null)
    val subscription: StateFlow<SubscriptionStatus?> = _subscription

    private val _lastScan = MutableStateFlow<LastScan?>(null)
    val lastScan: StateFlow<LastScan?> = _lastScan

    init {
        // The datacollect flavor never authenticates, so checkSubscription()
        // would throw on requireAuth(). Gate the fetch on the same flag that
        // gates every other launch-flavor callable — datacollect users see
        // the hidden-by-default branch and never pay a network round-trip.
        if (net.neuralcheck.BuildConfig.REQUIRE_SUBSCRIPTION) {
            viewModelScope.launch { refreshSubscription() }
            // Re-fetch on every bus tick. The bus fires when the FCM service
            // observes a `payment_approved` data push, when the Profile
            // screen redeems a promo, etc. — anywhere the server-side plan
            // could have changed without the user manually navigating.
            viewModelScope.launch {
                subscriptionRefreshBus.events.collect {
                    Log.d("NeuralCheck", "HomeViewModel: refresh tick from bus")
                    refreshSubscription()
                }
            }
            // Last scan card — same Firestore query ScanHistoryViewModel uses,
            // just limited to 1. Silent failure keeps the card hidden instead
            // of showing a fetch error banner on Home.
            viewModelScope.launch {
                fetchLastScan()
            }
        }
    }

    /**
     * Single source of truth for the dashboard subscription tile. Called on
     * init AND on every [SubscriptionRefreshBus] tick (FCM payment-approved
     * push, promo-redeem success, etc.) so the UI stays in sync with the
     * server without requiring a screen reopen.
     */
    private suspend fun refreshSubscription() {
        try {
            val sub = firebaseService.checkSubscription()
            Log.d(
                "NeuralCheck",
                "HomeViewModel subscription: plan='${sub.plan}' isActive=${sub.isActive}"
            )
            _subscription.value = sub
        } catch (t: Throwable) {
            // Swallow — failure keeps whatever value we already had. Logging
            // at warn so a persistent outage is noticeable in logcat without
            // turning into a user-visible error.
            Log.w("NeuralCheck", "HomeViewModel checkSubscription failed: ${t.message}")
        }
    }

    private suspend fun fetchLastScan() {
        val uid = authManager.currentUser?.uid ?: return
        try {
            val snapshot = Firebase.firestore
                .collection("reports")
                .whereEqualTo("userId", uid)
                .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(1)
                .get()
                .await()
            val doc = snapshot.documents.firstOrNull() ?: return
            val createdAtMs = when (val raw = doc.get("createdAt")) {
                is Timestamp -> raw.toDate().time
                is Number -> raw.toLong()
                else -> doc.getDate("createdAt")?.time ?: 0L
            }
            val data = doc.data ?: emptyMap()
            val pipeline = data["pipeline"] as? String
            if (!AiPipeline.isRecognizedFlatShape(pipeline) && pipeline != net.neuralcheck.data.remote.model.AiPipeline.V7_FINDINGS_BASED) {
                Log.w(
                    "NeuralCheck",
                    "fetchLastScan: doc ${doc.id} has pipeline='$pipeline', " +
                        "which neither the flat nor the V7 reader can parse — " +
                        "grade/score/summary will show as unmeasured placeholders, not real values"
                )
            }
            _lastScan.value = buildHomeLastScan(
                scanId = doc.id,
                data = data,
                createdAtMs = createdAtMs,
                context = context
            )
        } catch (t: Throwable) {
            Log.w("NeuralCheck", "HomeViewModel lastScan fetch failed: ${t.message}")
        }
    }

    private fun resolveDisplayName(): String {
        val user = authManager.currentUser
        return user?.displayName?.takeIf { it.isNotBlank() }
            ?: user?.email?.substringBefore("@")?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.home_guest_name)
    }

    private fun firstOfCurrentMonthMs(): Long {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.DAY_OF_MONTH, 1)
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    fun setFirstLaunchDone() {
        securePreferences.isOnboardingComplete = true
        _isFirstLaunch.value = false
    }
}

/**
 * Order 2026-09-25 (القرار ٨٦ البند ٤). Builds [HomeViewModel.LastScan]
 * from a raw `reports/{scanId}` document map — extracted from
 * [HomeViewModel.fetchLastScan] (A110/A109 precedent: a top-level function
 * so it's unit-testable without mocking `DocumentSnapshot`/`FirebaseFirestore`/
 * the whole Hilt-injected ViewModel).
 *
 * V7 ([net.neuralcheck.data.remote.model.AiPipeline.V7_FINDINGS_BASED]):
 * reads the real letter via [net.neuralcheck.data.remote.readV7OverallSummary]
 * — [HomeViewModel.LastScan.grade] is the letter, or (when legitimately
 * absent) `report_grade_unavailable`'s text plus the resolved reason in
 * [HomeViewModel.LastScan.summary]; [HomeViewModel.LastScan.score] is
 * always null (no score is ever shown for V7 — prior order).
 *
 * Flat (V6/legacy) shape: real `grade`/`score`/`summary` off the document
 * root. Order 2026-09-26 (القرار ١٠٢ البند ٢) widened which TAGS route
 * here: [net.neuralcheck.data.remote.model.AiPipeline.V3_LEGACY] /
 * [net.neuralcheck.data.remote.model.AiPipeline.V5_PRECOMPUTED] (both
 * root-shape V6 — see their own docs) and
 * [net.neuralcheck.data.remote.model.AiPipeline.CACHED] WHEN its actual
 * content is V6-shaped (checked, not assumed from the tag — see
 * [net.neuralcheck.data.remote.model.AiPipeline.classifyShape]).
 *
 * A genuinely UNKNOWN shape (an unrecognised tag, or `cached` with
 * NEITHER a V6 nor a V7 root signal): declares the absence explicitly via
 * the same `report_grade_unavailable` text used for V7's own absent-letter
 * case — never the bare "?" sentinel (rule 22). The raw tag is logged for
 * diagnosis, never shown on screen (this app's own established
 * convention for unrecognised codes).
 */
internal fun buildHomeLastScan(
    scanId: String,
    data: Map<String, Any?>,
    createdAtMs: Long,
    context: Context
): HomeViewModel.LastScan {
    val pipeline = data["pipeline"] as? String
    val deviceModel = (data["deviceModel"] as? String) ?: (data["device"] as? String) ?: "—"
    val v7 = net.neuralcheck.data.remote.readV7OverallSummary(data, context)
    val hasRootGrade = (data["grade"] as? String)?.isNotBlank() == true

    return when (
        net.neuralcheck.data.remote.model.AiPipeline.classifyShape(
            pipeline,
            hasRootLetterOrGrade = hasRootGrade,
            hasV7Overall = v7 != null
        )
    ) {
        net.neuralcheck.data.remote.model.AiPipeline.ShapeKind.V7 -> HomeViewModel.LastScan(
            scanId = scanId,
            deviceModel = deviceModel,
            grade = v7?.letter ?: context.getString(R.string.report_grade_unavailable),
            summary = v7?.absenceReasonText ?: "",
            createdAtMs = createdAtMs,
            isFlatShapeRecognized = false,
            pipeline = pipeline
        )
        net.neuralcheck.data.remote.model.AiPipeline.ShapeKind.V6 -> HomeViewModel.LastScan(
            scanId = scanId,
            deviceModel = deviceModel,
            grade = (data["grade"] as? String) ?: "?",
            summary = (data["summary"] as? String) ?: (data["verdict"] as? String) ?: "",
            createdAtMs = createdAtMs,
            isFlatShapeRecognized = true,
            pipeline = pipeline
        )
        net.neuralcheck.data.remote.model.AiPipeline.ShapeKind.UNKNOWN -> {
            Log.w("NeuralCheck", "buildHomeLastScan($scanId): unrecognised pipeline shape, tag='$pipeline'")
            HomeViewModel.LastScan(
                scanId = scanId,
                deviceModel = deviceModel,
                grade = context.getString(R.string.report_grade_unavailable),
                summary = "",
                createdAtMs = createdAtMs,
                isFlatShapeRecognized = false,
                pipeline = pipeline
            )
        }
    }
}

@HiltViewModel
class ActiveScanViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val scanRepository: ScanRepository,
    private val testResultRepository: TestResultRepository,
    private val timeSeriesRepository: TimeSeriesRepository,
    // Order 2026-09-30 («أمر — التقييم (ب)، ومهلة الجلسة، والبنود المعلّقة»،
    // البند ١): the technician-evaluation submit/discard trigger now lives
    // here — see the Completed/stopScan() call sites below.
    private val evaluationSubmitter: EvaluationSubmitter,
    // fieldsVersion 69 (SN-04): raised by SensorTests while the light sensor is asked to be covered.
    coverPromptChannel: net.neuralcheck.hardware.sensor.CoverPromptChannel,
    // Batch 2, item 2 (D5): the scan screen follows the document of the session its scan started in - see CustomerSessionWatch.
    scanSessionRecords: ScanSessionRecords,
    sessionHolder: ScanSessionHolder
) : ViewModel() {

    private val _scanState = MutableStateFlow<ScanUiState>(ScanUiState.Idle)
    val scanState: StateFlow<ScanUiState> = _scanState

    /**
     * Batch 2, item 2 (D5, user 2026-10-15): reads `scanSessions/{id}` of the session this scan STARTED in, from the scan's first Running state until the scan
     * ends, is stopped, or this view model is cleared (all four call [CustomerSessionWatch.stop]). It starts and keeps alive NO service: it is a Firestore listener
     * and the scan service is untouched except by the same [stopScan] the stop button uses. A personal scan has no recorded session and nothing starts.
     */
    private val sessionWatch = CustomerSessionWatch(SessionDocWatcher(), sessionHolder, scanSessionRecords) { onSessionCancelledByOwner() }

    private val _sessionCancelledByOwner = MutableStateFlow(false)

    /** true once the owner cancelled the session this scan is in: the scan was stopped; the screen shows the approved text, then goes back (D3). */
    val sessionCancelledByOwner: StateFlow<Boolean> = _sessionCancelledByOwner

    private val _scanStartedInSession = MutableStateFlow<Boolean?>(null)

    /**
     * A-P6-09 (the scanned phone shows a progress bar alone): whether the scan STARTED in a session - `true` for a session's scan, `false` for a personal one, and `null` until the first Running state names
     * the scan (rule 22: not known yet is not "no session"). It is the answer of [CustomerSessionWatch.start] - the one place that asks the scan's record - taken once per scan, so it never changes
     * afterwards: the owner's cancellation or the end of the read do not turn a session's scan into a personal one.
     */
    val scanStartedInSession: StateFlow<Boolean?> = _scanStartedInSession

    /** The scan id the watch was last attempted for: one attempt per scan (the Running state is re-emitted every tick; a personal scan would otherwise print its records' lines each time). */
    @Volatile
    private var sessionWatchAttemptedFor: String? = null

    /** true while the technician is asked to cover the top of the screen (the light sensor, SN-04). */
    val coverPromptActive: StateFlow<Boolean> = coverPromptChannel.active

    // Phase 1.11 H2 fix — Layer 2 of the defense in depth (Layer 1 is the
    // LaunchedEffect key swap in ActiveScanScreen.kt). One-shot latch
    // that prevents a second startScan() invocation on the same VM from
    // dispatching another ACTION_START. The flag is set BEFORE
    // startForegroundService so even a re-fire that races the service
    // round-trip is rejected here. The VM is scoped to the active_scan
    // NavBackStackEntry, so a fresh scan always gets a fresh VM and a
    // fresh latch — no manual reset required.
    @Volatile
    private var startScanRequested = false

    private var service: StressTestService? = null
    private var bound = false

    private val tempHistory = mutableListOf<Float>()
    private val cpuHistory = mutableListOf<Float>()
    private val batteryHistory = mutableListOf<Float>()
    // V2 fix #38: separate buffer for battery_temp history.
    // Previously tempHistory mixed cpu/gpu/battery via tempChartSource;
    // the redesigned charts give battery temp its own dedicated visual.
    private val batteryTempHistory = mutableListOf<Float>()

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val localBinder = binder as? StressTestService.LocalBinder ?: return
            service = localBinder.getService()
            bound = true
            // Observe service scan state
            viewModelScope.launch {
                service?.scanState?.collect { state ->
                    _scanState.value = when (state) {
                        is StressTestService.ScanState.Idle -> ScanUiState.Idle
                        is StressTestService.ScanState.Running -> {
                            // Batch 2, item 2 (D5): once per scan, the first time its id is known
                            if (state.scanId.isNotBlank() && sessionWatchAttemptedFor != state.scanId) {
                                sessionWatchAttemptedFor = state.scanId
                                // A-P6-09: the first and only call for this scan, so `false` is "no session" (a personal scan), never "a read is already running"
                                _scanStartedInSession.value = sessionWatch.start(state.scanId)
                            }
                            // Pick the most informative temperature signal available.
                            // Priority: cpu_temp (most direct stress signal) →
                            // gpu_temp → battery_temp (always available but lagging).
                            // The chosen value is plotted; the chart label updates
                            // to reflect which source it is via tempChartSource below.
                            val plottedTemp = when {
                                state.cpuTemp > 0f -> state.cpuTemp
                                state.gpuTemp > 0f -> state.gpuTemp
                                else -> state.batteryTemp
                            }
                            tempHistory.add(plottedTemp)
                            // readCpuUsage() already returns 0–100, do NOT multiply.
                            // Clamp to 0 when fallback has not stabilised yet.
                            cpuHistory.add(state.cpuUsage.coerceAtLeast(0f))
                            batteryHistory.add(state.batteryLevel.toFloat())
                            // V2 fix #38: track raw battery temp every tick.
                            // The chart side dedups consecutive duplicates
                            // (Android EXTRA_TEMPERATURE updates every 30-60s).
                            batteryTempHistory.add(state.batteryTemp)
                            // V2 scan duration is 480s with samples every 500ms,
                            // yielding up to ~960 points. We cap at 1000 to bound
                            // memory while showing the full scan trajectory in
                            // the chart. Previous cap of 60 only showed last ~30s.
                            if (tempHistory.size > 1000) tempHistory.removeAt(0)
                            if (cpuHistory.size > 1000) cpuHistory.removeAt(0)
                            if (batteryHistory.size > 1000) batteryHistory.removeAt(0)
                            if (batteryTempHistory.size > 1000) batteryTempHistory.removeAt(0)

                            ScanUiState.Running(
                                progress = state.progressPercent,
                                phase = state.phase.ordinal + 1,
                                scanId = state.scanId,
                                v2PhaseId = state.v2PhaseId,
                                elapsed = state.elapsedSeconds,
                                batteryTemp = state.batteryTemp,
                                cpuTemp = state.cpuTemp,
                                gpuTemp = state.gpuTemp,
                                cpuUsage = state.cpuUsage.coerceAtLeast(0f),
                                batteryLevel = state.batteryLevel,
                                tempHistory = tempHistory.toList(),
                                cpuHistory = cpuHistory.toList(),
                                batteryLevelHistory = batteryHistory.toList(),
                                batteryTempHistory = batteryTempHistory.toList(),
                                tempChartSource = when {
                                    state.cpuTemp > 0f -> "cpu"
                                    state.gpuTemp > 0f -> "gpu"
                                    else -> "battery"
                                }
                            )
                        }
                        is StressTestService.ScanState.SafetyStopped -> {
                            sessionWatch.stop()
                            ScanUiState.Error(state.reason)
                        }
                        is StressTestService.ScanState.Completed -> {
                            // Order 2026-09-30 («أمر — التقييم (ب)، ومهلة
                            // الجلسة، والبنود المعلّقة»، البند ١، ثم «ملحق —
                            // النصّ، ورسالة التقييم، والبروتوكول»): submit the
                            // technician's evaluation THE MOMENT the scan
                            // finishes — HERE, before `_scanState.value` even
                            // flips to Completed below — not at analysis time
                            // (AiScoringEngine.kt's `submitAsync` call, which
                            // now runs only as a fallback: see that call
                            // site's own comment). The session is still
                            // "scanning" at this exact instant; analyzeDevice
                            // (which flips it to "completed" server-side,
                            // relayed by the coordinator, not verified from
                            // this repo — القاعدة ٢) is not invoked until a
                            // LATER screen (manual_tests → report), well
                            // after ActiveScanScreen's own LaunchedEffect
                            // (ActiveScanScreen.kt:218-220) has already
                            // navigated away.
                            //
                            // submitOnScanComplete launches on
                            // EvaluationSubmitter's OWN persistent singleton
                            // scope, NOT viewModelScope — a self-caught fix
                            // this round: viewModelScope.launch (this call's
                            // form in the previous round) would have risked
                            // the submission being CANCELLED mid-flight,
                            // because ActiveScanScreen pops active_scan off
                            // the back stack moments after this fires,
                            // clearing this ViewModel and cancelling
                            // viewModelScope. Still fire-and-forget from
                            // HERE's perspective — the `when` branch below
                            // still returns immediately, so this NEVER delays
                            // _scanState flipping to Completed, and therefore
                            // never delays ActiveScanScreen's navigation to
                            // manual_tests. No timeout is applied because
                            // nothing here is awaited at all. A no-op when no
                            // evaluation row exists (non-QR scan, or a QR
                            // scan where the technician never answered a
                            // single question) — EvaluationSubmitter
                            // .submitOne's existing `dao.getByScanId(...) ?:
                            // return false` guard, unchanged. On failure,
                            // publishes to EvaluationSubmissionStatusHolder
                            // so ManualTestsScreen (the next screen) can show
                            // a line — see that holder's own doc for exactly
                            // when it is/isn't delivered.
                            sessionWatch.stop()
                            evaluationSubmitter.submitOnScanComplete(state.scanId, context)
                            ScanUiState.Completed(state.scanId)
                        }
                    }
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            bound = false
        }
    }

    fun startScan(scanType: String, linkedScanId: String?) {
        Log.d("NeuralCheck", "ActiveScanViewModel.startScan(scanType=$scanType) called")
        // Phase 1.11 H2 fix — Layer 2 in-flight guard. Reject any caller
        // (recomposed LaunchedEffect, retry button hooked up later, deep
        // link, etc.) once the first startScan has been kicked off.
        //
        // The latch fires for BOTH: scan in progress (state = Running)
        // AND scan already completed (state = Completed/Error). The
        // "completed" branch is the one that actually causes duplicate
        // reports/ docs in the wild — see audit §7 race-table — because
        // the service-side guard at StressTestService.kt:282 only
        // catches the Running case and a Completed→fresh-ACTION_START
        // transition silently spawns a new UUID.
        if (startScanRequested || _scanState.value !is ScanUiState.Idle) {
            Log.d(
                "NeuralCheck",
                "ActiveScanViewModel.startScan: ignored — startScanRequested=$startScanRequested " +
                    "state=${_scanState.value::class.simpleName}"
            )
            return
        }
        startScanRequested = true
        // Subscription gate (launch flavor only). The datacollect flavor
        // sets REQUIRE_SUBSCRIPTION=false and bypasses this entirely. For
        // the launch flavor this is where a future checkSubscription() call
        // will short-circuit the scan and surface a paywall instead.
        if (net.neuralcheck.BuildConfig.REQUIRE_SUBSCRIPTION) {
            // TODO: call SubscriptionManager.isActive() and emit
            //       ScanUiState.Error(paywallReason) when inactive. Until the
            //       billing flow ships we let the scan proceed so the build
            //       does not regress existing QA paths.
        }
        tempHistory.clear()
        cpuHistory.clear()
        batteryHistory.clear()
        batteryTempHistory.clear()

        // Pre-flight check: NotificationManager must be reachable. Without
        // it, a foreground service on Android 14+ has no chance of
        // surfacing its required notification, and the OS will throw
        // ForegroundServiceStartNotAllowedException a few ms later.
        // Failing fast here is preferable to crashing inside the OS.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val nmReachable = try {
                context.getSystemService(NotificationManager::class.java) != null
            } catch (t: Throwable) {
                Log.w("NeuralCheck", "NotificationManager pre-flight check failed: ${t.message}")
                false
            }
            if (!nmReachable) {
                _scanState.value = ScanUiState.Error("NOTIFICATION_SERVICE_UNAVAILABLE")
                return
            }
        }

        val intent = Intent(context, StressTestService::class.java).apply {
            action = StressTestService.ACTION_START
            putExtra(StressTestService.EXTRA_SCAN_TYPE, scanType)
            linkedScanId?.let { putExtra(StressTestService.EXTRA_LINKED_SCAN_ID, it) }
        }

        // ContextCompat.startForegroundService handles the API-26 fork
        // internally, but the actual cause of crashes here is *uncaught*
        // exceptions from the OS itself: ForegroundServiceStartNotAllowed-
        // Exception (Android 12+) and the SPECIAL_USE-without-Play-Console
        // rejection observed on One UI 6/7. Wrapping the call surfaces the
        // failure as an in-UI error state instead of letting it propagate
        // to Compose's coroutine handler — which on some devices terminates
        // the activity and locks the app into a crash loop.
        try {
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        } catch (t: Throwable) {
            Log.e("NeuralCheck", "startForegroundService threw: ${t.javaClass.simpleName}: ${t.message}", t)
            _scanState.value = ScanUiState.Error("FGS_START_DENIED:${t.javaClass.simpleName}")
            return
        }
        try {
            context.bindService(
                Intent(context, StressTestService::class.java),
                connection, Context.BIND_AUTO_CREATE
            )
        } catch (t: Throwable) {
            Log.e("NeuralCheck", "bindService threw: ${t.javaClass.simpleName}: ${t.message}", t)
            _scanState.value = ScanUiState.Error("BIND_FAILED:${t.javaClass.simpleName}")
            return
        }
    }

    fun stopScan() {
        // Order 2026-09-30 («أمر — التقييم (ب)...»، البند ١): a cancelled
        // scan's evaluation is dropped from the device entirely, never sent
        // ("يُسقَط تقييمه من الجهاز، ولا يُرسَل" — coordinator's own text).
        // Read the scanId from the CURRENT Running state before stopping —
        // once stopped there is no later Completed emission to read it
        // from (ActiveScanScreen's stop-confirm dialog pops the back stack
        // immediately after this call, never waiting on it).
        val scanId = (_scanState.value as? ScanUiState.Running)?.scanId
        sessionWatch.stop()
        service?.stopScan()
        if (!scanId.isNullOrBlank()) {
            viewModelScope.launch(Dispatchers.IO) {
                runCatching { evaluationSubmitter.discard(scanId) }
            }
        }
    }

    /** The owner cancelled the session this scan is in ([CustomerSessionWatch] already dealt with the holder and the scan's record): stop the scan exactly as the stop button does, then tell the screen. */
    private fun onSessionCancelledByOwner() {
        stopScan()
        _sessionCancelledByOwner.value = true
    }

    override fun onCleared() {
        super.onCleared()
        sessionWatch.stop()
        if (bound) {
            try { context.unbindService(connection) } catch (_: Exception) {}
            bound = false
        }
    }

    sealed class ScanUiState {
        data object Idle : ScanUiState()
        data class Running(
            val progress: Float = 0f,
            val phase: Int = 1,
            /** feat/technician-evaluation: the scan's UUID, available from
             *  the first Running emission (StressTestService mints it
             *  before any phase starts) — lets the evaluation card persist
             *  answers keyed by scanId from the first second. */
            val scanId: String = "",
            /** V2 only — granular 0..7 phase id, propagated from
             *  StressTestService.ScanState.Running.v2PhaseId. UI checks
             *  this first to render V2 phase names; falls back to V1
             *  [phase] when null (V1 path active or pre-V2 build). */
            val v2PhaseId: Int? = null,
            val elapsed: Int = 0,
            val batteryTemp: Float = 0f,
            val cpuTemp: Float = -1f,
            val gpuTemp: Float = -1f,
            val cpuUsage: Float = 0f,
            val batteryLevel: Int = 100,
            val batteryLevelHistory: List<Float> = emptyList(),
            val tempHistory: List<Float> = emptyList(),
            val cpuHistory: List<Float> = emptyList(),
            val cpuFreqHistory: List<Float> = emptyList(),
            // V2 fix #38: dedicated history for battery_temp chart.
            val batteryTempHistory: List<Float> = emptyList(),
            // V2 chart fix: identifies which temperature source the
            // tempHistory is plotting. UI uses this to label the chart
            // ("CPU temperature" / "GPU temperature" / "Battery temperature")
            // and to choose an appropriate y-axis range. One of:
            // "cpu" / "gpu" / "battery".
            val tempChartSource: String = "battery"
        ) : ScanUiState()
        data class Completed(val scanId: String) : ScanUiState()
        data class Error(val message: String) : ScanUiState()
    }
}

/**
 * A110: shared cache-hit category-score mapping, extracted so it can be
 * unit-tested without the ViewModel's Hilt/Android dependencies.
 *
 * Mirrors AiScoringEngine's live-path field-by-field copy (see
 * AiScoringEngine.kt, the `catScores` construction feeding
 * ScoringResult.categoryScores) so a re-opened report built from
 * `aiAnalysisJson` carries the same unmeasured-category contract as a
 * fresh AI response, instead of silently dropping measured/declaredCount/
 * excludedCount/note on the cache-hit path.
 *
 * `measured`/`declaredCount`/`excludedCount` stay null when the cached
 * JSON was serialised before those fields existed on AiCategoryScore —
 * CategoryScore already types them nullable for exactly that "unknown,
 * not zero" case (see ScoringResult.kt), so no default is invented here.
 */
internal fun categoryScoresFromCachedAi(
    categoryScores: Map<String, net.neuralcheck.data.remote.model.AiCategoryScore>?
): Map<String, net.neuralcheck.domain.scoring.CategoryScore> =
    categoryScores
        ?.mapValues { (key, value) ->
            net.neuralcheck.domain.scoring.CategoryScore(
                name = key, nameAr = key, nameTr = "",
                score = value.score.toFloat(),
                grade = value.grade,
                passCount = value.passCount,
                failCount = (value.totalCount - value.passCount).coerceAtLeast(0),
                totalCount = value.totalCount,
                measured = value.measured,
                declaredCount = value.declaredCount,
                excludedCount = value.excludedCount,
                note = value.note
            )
        }
        .orEmpty()

/**
 * ملحق القرار ١٠٤ البند ٢. `loadScan` has FOUR entry points that all call
 * it directly with no guard against concurrent invocation: `ReportScreen.kt`'s
 * initial `LaunchedEffect(scanId)` (:155), the "scan not found" retry
 * (:343), the AI-error retry (:378), and `saveManualResults`'s own
 * `loadScan(scanId)` refresh call (this file, `saveManualResults`). A
 * second call firing while the first is still in flight is not merely
 * redundant: the verifier's own reading of `AiScoringEngine.kt:263-309`
 * shows a concurrent second call that hits a backend rejection (relayed,
 * not independently checked in this repo — neuralcheck-backend is out of
 * this agent's scope: `analyzeDevice.js` rejecting a duplicate in-flight
 * request with `already-exists`) returns an ERROR `ScoringResult`
 * (`aiAnalysis = null`), which this file's `loadScan` then writes to both
 * `_scoringResult.value` (around the `finalResult` assignment) and a Room
 * `REPLACE`-conflict row (`scoreRepository.save`, `ScanScoreDao.kt`'s
 * `REPLACE` strategy) — silently overwriting a FIRST call's already-
 * successful result with a permanent-looking error card.
 *
 * Extracted (A110 precedent — [categoryScoresFromCachedAi] above, same
 * file, same rationale) so the single-flight semantics are unit-testable
 * without constructing a real [ReportViewModel] — see this class's own
 * test file, [net.neuralcheck.presentation.viewmodel.SingleFlightGuardTest],
 * for why a literal `ReportViewModel.loadScan()` end-to-end test is
 * structurally blocked in this repo (ReportViewModel's constructor
 * requires a real `FirebaseAnalysisService`, whose `functions:
 * FirebaseFunctions = Firebase.functions` field initializer
 * (`FirebaseAnalysisService.kt`) runs eagerly and unconditionally at
 * construction — it throws in a plain JVM unit test with no `FirebaseApp`,
 * and the class is declared `final` with no `mock-maker-inline` Mockito
 * config present in this repo (confirmed absent by `find`), so it cannot
 * be mocked around either).
 *
 * Not thread-confined by a lock/mutex: `loadScan` is only ever called from
 * `viewModelScope` (`Dispatchers.Main.immediate`) call sites — Compose
 * click handlers and `LaunchedEffect` bodies all run on Main — so
 * [tryStart] is always read-then-written on the same thread as every
 * caller, with no interleaving window to race.
 */
internal class SingleFlightGuard {
    private var inFlight = false

    /** True and marks in-flight if nothing was already running; false
     *  (no state change at all) if a prior [tryStart] has not yet been
     *  released by [finish] — the caller must treat `false` as "ignore
     *  this call entirely", not retry it. */
    fun tryStart(): Boolean {
        if (inFlight) return false
        inFlight = true
        return true
    }

    /** Releases the guard so the next [tryStart] succeeds. Callers must
     *  invoke this from a `finally` block around the guarded work so an
     *  exception never leaves the guard permanently stuck "in flight". */
    fun finish() {
        inFlight = false
    }
}

@HiltViewModel
class ReportViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val scanRepository: ScanRepository,
    private val testResultRepository: TestResultRepository,
    private val scoreRepository: ScoreRepository,
    private val peerRepository: PeerRepository,
    private val technicianRepository: TechnicianRepository,
    private val scoringEngine: net.neuralcheck.domain.scoring.ScoringEngine,
    // A20: coarse stage of the ~3-minute analysis wait. Exposed verbatim —
    // the ViewModel adds no interpretation, it just hands the screen what
    // the engine reports.
    private val analysisProgress: net.neuralcheck.domain.scoring.AnalysisProgress,
    // Order 2026-09-24 (القرار ٨٠/٨٢ البند ٦): needed only for loadScan's
    // Room-miss fallback to fetchExistingReport — every other path in this
    // ViewModel is Room-only, same as before this field was added.
    private val firebaseService: net.neuralcheck.data.remote.FirebaseAnalysisService,
    // A-P6-08 (the technician form's blindness): the session the scan started in, read before the analysis spends it, so the customer's phone can tell the server the report was shown.
    private val scanSessionRecords: ScanSessionRecords,
    // A-P6-07 (the repeat scan): the holder the child session is handed to when the server starts it for this phone (ScanSessionHolder.adoptRepeatChild).
    private val sessionHolder: net.neuralcheck.data.remote.ScanSessionHolder
) : ViewModel() {

    // ---- A-P6-07: the scanned phone's repeat (PLAN-06 section 4; screen ف١٣). Nothing happens here while REPEAT_TEXTS_APPROVED is false (it is true since 2026-10-23): the calls are gated at the exit, the screen is behind
    // it, and the session read below is skipped unless one of the two gates is open. The logic is ScannedRepeatCooling's (RepeatScanUi.kt), tested without this class (its constructor is not buildable in a test, see the
    // note at the A-P6-08 read below); what this class does is pinned as source shape in RepeatScanGuardTest.
    private val repeatCooling = ScannedRepeatCooling(RepeatScanClient(firebaseService)) { childSessionId -> sessionHolder.adoptRepeatChild(childSessionId) }

    /** What the report screen knows about the repeat of the scan it shows. */
    val repeatUi: StateFlow<ScannedRepeatUi> = repeatCooling.state

    /** A reading of the phone's own battery temperature, from the cooling screen. */
    fun onRepeatTemperature(read: net.neuralcheck.domain.repeat.TempRead) = repeatCooling.onTemperature(read)

    /** «انتظر». */
    fun chooseRepeatWait() = repeatCooling.chooseWait()

    /** «جاهز — ابدأ الفحص المكرّر». */
    fun startRepeatReady() {
        viewModelScope.launch { repeatCooling.startReady() }
    }

    /** «ابدأ رغم ذلك». */
    fun startRepeatAnyway() {
        viewModelScope.launch { repeatCooling.startAnyway() }
    }

    /** The screen went to the child's scan. */
    fun repeatStartConsumed() = repeatCooling.startedConsumed()

    /** A20: the engine's current stage, for the loading overlay. */
    val analysisStage: kotlinx.coroutines.flow.StateFlow<net.neuralcheck.domain.scoring.AnalysisStage> =
        analysisProgress.stage

    /** A20: wall-clock ms when this analysis began, or null when idle.
     *  The overlay turns it into an elapsed counter — the one number in
     *  this wait that is honestly known. */
    fun analysisStartedAtMs(): Long? = analysisProgress.startedAtMs
    private val _scan = MutableStateFlow<ScanEntity?>(null)
    val scan: StateFlow<ScanEntity?> = _scan

    private val _scoringResult = MutableStateFlow<net.neuralcheck.domain.scoring.ScoringResult?>(null)
    val scoringResult: StateFlow<net.neuralcheck.domain.scoring.ScoringResult?> = _scoringResult

    private val _results = MutableStateFlow<List<net.neuralcheck.data.local.db.entity.FunctionalResultEntity>>(emptyList())
    val results: StateFlow<List<net.neuralcheck.data.local.db.entity.FunctionalResultEntity>> = _results

    // null = not yet loaded; true/false = definitive answer from DB.
    // Report uses this to auto-open the appropriate survey exactly once —
    // without the DB check, re-entering the report after submit re-prompts.
    private val _hasSurveyData = MutableStateFlow<Boolean?>(null)
    val hasSurveyData: StateFlow<Boolean?> = _hasSurveyData

    /**
     * Order 2026-09-24 (القرار ٨٠/٨٢ البند ٦). Populated ONLY on the
     * Room-miss fallback path — a minimal, honestly-sourced header
     * (device name + when the backend finished analysing it) for a scan
     * this device has no local [ScanEntity] row for at all. `null` on the
     * normal Room-present path (where [scan] itself supplies the same
     * information) and while nothing has attempted the fallback yet.
     *
     * Order 2026-09-26 (القرار ١٠٢ البند ١): `scanTimestampMs` is now
     * nullable — a real, complete report can legitimately have neither
     * `analyzedAt` (V6 never sends it) nor a usable `createdAt` (see
     * [loadFromRemoteFallback]). Absence of a time must not mean absence
     * of the report (rule 22) — `ReportScreen` renders an explicit
     * "time unavailable" line instead of a formatted date when this is
     * null, rather than the report failing to load at all.
     */
    data class RemoteReportHeader(val deviceModel: String, val scanTimestampMs: Long?)
    private val _remoteHeader = MutableStateFlow<RemoteReportHeader?>(null)
    val remoteHeader: StateFlow<RemoteReportHeader?> = _remoteHeader

    /**
     * Order 2026-09-24 (القرار ٨٠/٨٢ البند ٦، القاعدة 22): explicit
     * "could not load this report at all" — neither Room nor the cloud
     * (`fetchExistingReport`) has it. Sits alongside [scoringResult]/[scan]
     * instead of overloading either with a sentinel value, so
     * ReportScreen can show an honest failure state INSTEAD OF an
     * AiLoadingOverlay that would otherwise spin forever (this ViewModel
     * previously just `return@launch`ed on a Room miss, leaving both
     * [scan] and [scoringResult] permanently null with no way for the UI
     * to tell "still loading" apart from "will never load").
     */
    private val _loadFailed = MutableStateFlow(false)
    val loadFailed: StateFlow<Boolean> = _loadFailed

    // ملحق القرار ١٠٤ البند ٢ — see [SingleFlightGuard]'s own doc (this
    // file, above this class) for the full rationale. Guards ALL FOUR of
    // loadScan's real entry points (ReportScreen.kt:155/343/378,
    // saveManualResults below) from a single place, since all four call
    // this function directly rather than some per-site wrapper.
    private val loadGuard = SingleFlightGuard()

    fun loadScan(scanId: String) {
        // Chosen behaviour (coordinator left this to the agent, per its
        // own order): ignore the duplicate call outright rather than
        // disabling a button — a single guard here automatically covers
        // all four call sites above without threading an "isLoading" flag
        // out to each of them individually.
        if (!loadGuard.tryStart()) {
            Log.d("NeuralCheck.DB", "loadScan($scanId): ignored — a load is already in progress")
            return
        }
        viewModelScope.launch {
            try {
            _loadFailed.value = false
            _remoteHeader.value = null
            // ملحق القرار ١٠٤ البند ٢: clear the PRIOR result too (not just
            // loadFailed/remoteHeader, the original two) so a retry/reopen
            // shows AiLoadingOverlay instead of leaving the old report (or
            // the old error card) on screen for the duration of the new
            // load — `hasRenderableReport` (ReportScreen.kt:133) reads
            // `scoring != null`, so this alone flips the screen to the
            // pending state. Safe to clear unconditionally here: this line
            // only runs when [loadGuard.tryStart] just admitted a genuinely
            // new load, never for an ignored duplicate.
            _scoringResult.value = null
            val scanEntity = scanRepository.getScanById(scanId)
            if (scanEntity == null) {
                loadFromRemoteFallback(scanId)
                return@launch
            }
            _scan.value = scanEntity
            _hasSurveyData.value = checkSurveyData(scanId, scanEntity.scanType)

            // Pre-load any cached score row. Used as the *primary* source
            // for the human-readable shortId on re-open: the first
            // successful AI run minted it, and re-runs (which may fail or
            // return a different value if the backend regenerates) should
            // never blow it away on screen / PDF.
            val cachedScore = runCatching { scoreRepository.getByScanId(scanId) }.getOrNull()

            // FAST PATH: if we already have a cached AI result for this scan,
            // rehydrate it from Room and short-circuit the entire scoring
            // flow. No Gemini call, no Firestore round-trip, no risk of the
            // backend returning a different verdict on re-open. This is the
            // canonical reopen behaviour — the first AI run is the one we
            // freeze on disk, and every subsequent open shows EXACTLY what
            // the user saw the first time.
            Log.d(
                "NeuralCheck.DB",
                "loadScan($scanId): cachedScore=${cachedScore != null} " +
                    "aiAnalysisJson length=${cachedScore?.aiAnalysisJson?.length}"
            )
            val cachedAiJson = cachedScore?.aiAnalysisJson?.takeIf { it.isNotBlank() }
            if (cachedAiJson != null) {
                val cachedResult = runCatching {
                    val gson = com.google.gson.Gson()
                    val ai = gson.fromJson(cachedAiJson, net.neuralcheck.domain.model.AiAnalysisResult::class.java)
                    // A110: was an inline mapValues that dropped measured/
                    // declaredCount/excludedCount/note — extracted to
                    // categoryScoresFromCachedAi (above) so it mirrors
                    // AiScoringEngine's live path and is unit-testable.
                    val catScores: Map<String, net.neuralcheck.domain.scoring.CategoryScore> =
                        categoryScoresFromCachedAi(ai.categoryScores)
                    net.neuralcheck.domain.scoring.ScoringResult(
                        overallScore = cachedScore.overallScore,
                        overallGrade = cachedScore.overallGrade,
                        gradeDescription = "",
                        gradeDescriptionTr = "",
                        axisScores = emptyMap(),
                        categoryScores = catScores,
                        individualScores = emptyMap(),
                        criticalFailures = emptyList(),
                        antiTamperPenalty = cachedScore.antiTamperPenalty,
                        isIncomplete = cachedScore.isIncomplete,
                        scoringMethod = cachedScore.scoringMethod,
                        aiAnalysis = ai,
                        shortId = cachedScore.shortId ?: ai.shortId,
                        error = null
                    )
                }.getOrNull()
                if (cachedResult != null) {
                    _scoringResult.value = cachedResult
                    // Even when we serve from cache, also load the functional
                    // results list so the rule-based / datacollect path still
                    // sees per-test rows. These are tiny rows, fast to read.
                    _results.value = testResultRepository.getResults(scanId)
                    return@launch
                }
            }

            // Load functional results and convert to domain model
            val resultEntities = testResultRepository.getResults(scanId)
            _results.value = resultEntities
            val testResults = resultEntities.map { entity ->
                net.neuralcheck.domain.model.TestResult(
                    testId = entity.testId,
                    componentName = entity.componentName,
                    componentNameAr = entity.componentName,
                    categoryName = entity.categoryName,
                    status = net.neuralcheck.domain.model.TestStatus.fromString(entity.status),
                    score = entity.score,
                    rawValue = entity.rawValue,
                    rawValueUnit = entity.rawValueUnit,
                    notes = entity.notes,
                    measurements = net.neuralcheck.data.local.db.converter.MeasurementsJson
                        .decode(entity.measurements)
                )
            }

            // Build ScanReport for scoring
            val scanReport = net.neuralcheck.domain.model.ScanReport(
                scanId = scanId,
                scanType = net.neuralcheck.domain.model.ScanType.fromString(scanEntity.scanType),
                linkedScanId = scanEntity.linkedScanId,
                deviceInfo = net.neuralcheck.domain.model.DeviceInfo(
                    fingerprint = scanEntity.deviceFingerprint,
                    model = scanEntity.deviceModel,
                    manufacturer = "",
                    androidVersion = scanEntity.androidVersion,
                    apiLevel = scanEntity.androidApiLevel,
                    screenResolution = "",
                    cpuCores = Runtime.getRuntime().availableProcessors()
                ),
                scanMetadata = net.neuralcheck.domain.model.ScanMetadata(
                    startTimestamp = scanEntity.timestamp,
                    endTimestamp = null,
                    startBatteryLevel = scanEntity.batteryLevelStart,
                    endBatteryLevel = scanEntity.batteryLevelEnd,
                    startBatteryTemp = scanEntity.ambientTempEstimate ?: 30f,
                    uptimeMs = 0L,
                    isComplete = scanEntity.isComplete,
                    earlyStopReason = scanEntity.earlyStopReason,
                    appVersion = scanEntity.appVersion
                ),
                functionalResults = testResults,
                timeSeriesSummary = null,
                antiTamperFlags = emptyList(),
                technicianInput = null,
                peerInput = null
            )

            // A-P6-08: the session this scan started in, for the first-open record below. Read BEFORE calculateScore: a successful analysis spends the scan's record. Asked only while the form's gate or the
            // repeat scan's gate (A-P6-07: the same read serves the cooling screen's offer) is open (nothing is read or printed for it today); a personal scan has no record and stays personal (null).
            val sessionAtScan = if (firebaseService.formCallsOpen() || firebaseService.repeatCallsOpen()) scanSessionRecords.sessionFor(scanId, ScanSessionRecords.Use.REPORT)?.sessionId else null

            // Calculate score
            val result = scoringEngine.calculateScore(scanReport)

            // Resolve shortId with a three-tier fallback so it's never lost
            // between sessions:
            //   1) cached Room row (canonical — set on the first AI run)
            //   2) the live AI response (covers a fresh first scan)
            //   3) Firestore reports/{scanId}.shortId (covers a reinstall
            //      where Room is empty but the cloud report still exists)
            val resolvedShortId: String? = cachedScore?.shortId
                ?.takeIf { it.isNotBlank() }
                ?: result.shortId?.takeIf { it.isNotBlank() }
                ?: result.aiAnalysis?.shortId?.takeIf { it.isNotBlank() }
                ?: fetchFirestoreShortId(scanId)

            // Patch the result so the UI's `displayId` resolution finds the
            // ID at either accessor (top-level or nested in aiAnalysis).
            val finalResult = if (resolvedShortId != null && resolvedShortId != result.shortId) {
                result.copy(
                    shortId = resolvedShortId,
                    aiAnalysis = result.aiAnalysis?.copy(shortId = resolvedShortId)
                )
            } else result
            _scoringResult.value = finalResult

            // A-P6-08: the report of a session scan is on this customer's screen for the first time - tell the server (the first open of EITHER device wins there). Only a real report counts (the same test as
            // markSynced below), only a scan that started in a session, and never in the way of what is shown: it is launched, not awaited. The recorder never throws; nothing is sent while the gate is closed.
            if (sessionAtScan != null && finalResult.aiAnalysis != null && finalResult.error == null) {
                viewModelScope.launch {
                    net.neuralcheck.data.remote.ReportFirstOpenedRecorder(firebaseService).record(sessionAtScan, net.neuralcheck.data.remote.ReportFirstOpenedRecorder.Source.CUSTOMER)
                }
                // A-P6-07: the same real report of a session scan is where the repeat scan's offer is asked for (the server chose, at the analysis, whether this phone is to be scanned again after it cools).
                // Launched, not awaited; one question per (session, scan); nothing is sent while the repeat scan's gate is closed.
                viewModelScope.launch { repeatCooling.onReportShown(sessionAtScan, scanId) }
            }

            // Persist score to DB — include shortId AND the full AiAnalysis
            // JSON so the next re-open hits the FAST PATH above and skips
            // the AI call entirely.
            //
            // Sparse-write guard: only serialise + save the JSON when the
            // AiAnalysis carries actual content. Saving a grade+score-only
            // verdict (e.g. when the Cloud Function wrote a partial doc, or
            // the backend returned a stripped response on re-open) would
            // lock the user into a half-rendered report forever — every
            // future re-open would hit the FAST PATH with empty content.
            // Falling back to `cachedScore?.aiAnalysisJson` preserves
            // whatever good data we had before.
            //
            // Phase 3-a / A109 follow-through: this used to inline the
            // exact same V6-only OR-chain as the pre-fix
            // AiAnalysisResponse.isComplete() — a V7 result (whose content
            // lives in ai.overall/ai.categories/ai.narrative, not
            // ai.summary/ai.details/...) always failed it, so
            // aiAnalysisJson was never written for a V7 scan and every
            // reopen re-ran the full AI call. Now delegates to
            // AiAnalysisResult.isComplete(), the domain-level mirror of the
            // DTO gate, so both caches (Firestore in FirebaseAnalysisService,
            // Room here) agree on what "complete" means per pipeline.
            val ai = finalResult.aiAnalysis
            val aiHasContent = ai != null && ai.isComplete()
            val aiJson: String? = if (aiHasContent) {
                runCatching { com.google.gson.Gson().toJson(ai) }.getOrNull()
            } else null
            Log.d(
                "NeuralCheck.DB",
                "loadScan($scanId): saving aiAnalysisJson length=${aiJson?.length} " +
                    "aiHasContent=$aiHasContent " +
                    "(preserveExisting=${cachedScore?.aiAnalysisJson?.length})"
            )
            scoreRepository.save(
                net.neuralcheck.data.local.db.entity.ScanScoreEntity(
                    scanId = scanId,
                    overallScore = finalResult.overallScore,
                    overallGrade = finalResult.overallGrade,
                    axisScoresJson = com.google.gson.Gson().toJson(finalResult.axisScores),
                    categoryScoresJson = com.google.gson.Gson().toJson(finalResult.categoryScores),
                    criticalFailuresJson = com.google.gson.Gson().toJson(finalResult.criticalFailures),
                    antiTamperPenalty = finalResult.antiTamperPenalty,
                    isIncomplete = finalResult.isIncomplete,
                    scoringMethod = finalResult.scoringMethod,
                    calculatedAt = System.currentTimeMillis(),
                    shortId = resolvedShortId,
                    aiAnalysisJson = aiJson ?: cachedScore?.aiAnalysisJson
                )
            )

            // analyzeDevice (or the cache-hit Firestore lookup inside
            // AiScoringEngine) succeeded → the report IS in `reports/{scanId}`
            // server-side, written by the Cloud Function. Mark the local
            // ScanEntity as synced so the cloud icon flips green even when
            // FirebaseSyncService's separate `scans/` upload is blocked by
            // Firestore rules. The user-perceptible "this scan is in the
            // cloud" guarantee is met by analyzeDevice alone.
            if (finalResult.aiAnalysis != null && finalResult.error == null) {
                runCatching { scanRepository.markSynced(scanId) }
                    .onFailure { Log.w("NeuralCheck", "markSynced failed: ${it.message}") }
            }

            // Backend-resolved device name wins over the local DeviceName map
            // because the server's catalog is fuller and updated more often.
            // When the AI returns one, write it back to ScanEntity.deviceModel
            // so EVERY downstream screen (history list, home tile, share PDF,
            // Firestore mirror via FirebaseSyncService) reads the same
            // canonical string — without each having to know the priority
            // chain. Refresh _scan.value too so the currently-rendered
            // ReportScreen flips to the new label without a navigation hop.
            val aiName = finalResult.aiAnalysis?.deviceMarketingName?.takeIf { it.isNotBlank() }
            if (aiName != null && aiName != scanEntity.deviceModel) {
                Log.d(
                    "NeuralCheck.Device",
                    "loadScan($scanId): AI provided deviceMarketingName='$aiName' " +
                        "(was='${scanEntity.deviceModel}') — updating ScanEntity"
                )
                val updated = scanEntity.copy(deviceModel = aiName)
                runCatching { scanRepository.updateScan(updated) }
                    .onFailure { Log.w("NeuralCheck", "updateScan(deviceModel) failed: ${it.message}") }
                _scan.value = updated
            }
            } finally {
                // ملحق القرار ١٠٤ البند ٢: always releases, including on the
                // two return@launch early exits above (Room-miss fallback,
                // FAST PATH) and on any uncaught exception — a guard stuck
                // permanently "in flight" would silently disable every
                // future retry for this scan for the rest of the
                // ViewModel's lifetime, an outage worse than the race this
                // guard exists to prevent.
                loadGuard.finish()
            }
        }
    }

    /**
     * Order 2026-09-24 (القرار ٨٠/٨٢ البند ٦). Called from [loadScan] when
     * [scanRepository.getScanById] found nothing — e.g. a shared report
     * link, a history entry surviving a reinstall, or the shop-owner
     * viewing a customer-device QR-session scan the owner's own device
     * never ran. Falls back to [FirebaseAnalysisService.fetchExistingReport]
     * (the same Firestore-backed lookup [net.neuralcheck.domain.scoring.AiScoringEngine]'s
     * own cache-hit path uses, now reading `aiResponse.overall.letterSource`
     * correctly — this round's gate fix) before giving up.
     *
     * On success: builds the same [net.neuralcheck.domain.scoring.ScoringResult]
     * shape a fresh analysis would, via the shared
     * [net.neuralcheck.domain.scoring.buildScoringResultFromResponse], and a
     * minimal [RemoteReportHeader] sourced ONLY from real fields already on
     * the response (`deviceMarketingName`, required — missing it is still
     * treated as a failed load, never a fabricated placeholder name — and
     * `analyzedAt`/`createdAtMs` for the timestamp, order 2026-09-26،
     * القرار ١٠٢ البند ١: EITHER may be absent without failing the load
     * any more — see this function's body). `antiTamperPenalty=0f`/`isIncomplete=false` are inert on
     * this path: neither is read by `ReportContent`'s AI/launch branch
     * (verified — both are datacollect/rule-based-only reads: `ReportScreen.kt`'s
     * `AntiTamperBanner` and the `!isComplete` `IncompleteBanner` both sit
     * after that branch's own `return@LazyColumn`) except `ReportPdfGenerator`'s
     * tamper badge, which simply stays hidden for a report this device has
     * no local anti-tamper signal for — not a claim of "verified
     * untampered", just "nothing to flag".
     *
     * On failure (no Room row AND no usable remote report): sets
     * [_loadFailed] so the UI shows an explicit state — never an
     * indefinite [net.neuralcheck.presentation.screen.ReportScreen]'s
     * AiLoadingOverlay (rule 22: absence must surface).
     */
    private suspend fun loadFromRemoteFallback(scanId: String) {
        val remote = runCatching { firebaseService.fetchExistingReport(scanId) }.getOrNull()
        val header = remote?.let { response ->
            val deviceModel = response.deviceMarketingName?.takeIf { it.isNotBlank() }
            // Order 2026-09-26 (القرار ١٠٢ البند ١): "في مسار الجلب من
            // الخادم، يُقرأ الوقت من analyzedAt، فإن غاب فمن createdAt،
            // فإن غابا يُعرض التقرير بلا وقت." V6 never sends `analyzedAt`
            // at all — every V6 report opened via this fallback used to
            // fail the WHOLE load here (`لم يتم العثور على الفحص`) purely
            // because of a missing timestamp, despite the document being
            // real and complete. `createdAtMs` (Firestore's own
            // server-write time, present on every document regardless of
            // pipeline — see its own doc) is the fallback; if BOTH are
            // absent, the header still builds (`scanTimestampMs = null`,
            // rule 22) instead of refusing to render the report at all.
            val timestampMs = response.analyzedAt ?: response.createdAtMs
            if (deviceModel != null) {
                RemoteReportHeader(deviceModel = deviceModel, scanTimestampMs = timestampMs)
            } else {
                null
            }
        }
        if (remote != null && header != null) {
            Log.d("NeuralCheck.Report", "loadScan($scanId): Room miss, remote fallback succeeded")
            _remoteHeader.value = header
            _scoringResult.value = net.neuralcheck.domain.scoring.buildScoringResultFromResponse(
                response = remote,
                antiTamperPenalty = 0f,
                isIncomplete = false
            )
            _results.value = emptyList()
        } else {
            Log.w(
                "NeuralCheck.Report",
                "loadScan($scanId): Room miss AND remote fallback failed " +
                    "(remote=${remote != null} header=${header != null})"
            )
            _loadFailed.value = true
        }
    }

    /**
     * Last-resort lookup of [shortId] from the cloud `reports/{scanId}` doc
     * for the case where the device has nothing cached locally — typically
     * after a reinstall or a different device opening a shared report. We
     * swallow every error and return null because this is purely a UX
     * polish: failure means the report still renders, just with the raw
     * UUID instead of the human-readable ID.
     */
    private suspend fun fetchFirestoreShortId(scanId: String): String? = try {
        Firebase.firestore.collection("reports").document(scanId)
            .get()
            .await()
            .getString("shortId")
            ?.takeIf { it.isNotBlank() }
    } catch (t: Throwable) {
        Log.w("NeuralCheck", "ReportViewModel: Firestore shortId lookup failed: ${t.message}")
        null
    }

    private suspend fun checkSurveyData(scanId: String, scanType: String): Boolean {
        return when (scanType) {
            "general" -> peerRepository.getByScanId(scanId) != null
            "pre_repair" -> technicianRepository.getByScanId(scanId)?.isPreRepair == true
            "post_repair" -> technicianRepository.getByScanId(scanId)?.isPreRepair == false
            else -> true
        }
    }

    fun refreshSurveyStatus(scanId: String) {
        viewModelScope.launch {
            val type = _scan.value?.scanType ?: return@launch
            _hasSurveyData.value = checkSurveyData(scanId, type)
        }
    }

    fun saveManualResults(
        scanId: String,
        results: List<net.neuralcheck.data.local.db.entity.FunctionalResultEntity>,
        onComplete: (Boolean) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                // upsert (not append): re-tapping a manual chip should overwrite
                // the previous row for that (scanId, testId), not add another.
                // M7(ب), user decision 2026-10-18: a «يعمل» tap on M-02 over a stored `pass` keeps the stored grid raw
                // (the same rule the post-scan screen's setResult applies — withStoredRawKept; every other entity passes through).
                testResultRepository.upsertResults(results.map { testResultRepository.withStoredRawKept(it) })
                scanRepository.markUnsynced(scanId)
                SyncScheduler.syncNow(context)
                // Refresh on-screen results + recompute score to reflect new manual inputs
                loadScan(scanId)
                onComplete(true)
            } catch (e: Exception) {
                onComplete(false)
            }
        }
    }
}

@HiltViewModel
class TechnicianViewModel @Inject constructor(
    private val technicianRepository: TechnicianRepository,
    private val scanRepository: ScanRepository
) : ViewModel() {
    val complaint = MutableStateFlow("")
    val confidence = MutableStateFlow(3f)
    val notes = MutableStateFlow("")
    val selectedCategories = MutableStateFlow<List<String>>(emptyList())

    fun savePreRepair(scanId: String, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            technicianRepository.save(
                TechnicianInputEntity(
                    scanId = scanId,
                    isPreRepair = true,
                    customerComplaint = complaint.value,
                    faultCategories = selectedCategories.value.joinToString(","),
                    technicianConfidence = confidence.value.toInt(),
                    repairActions = null,
                    repairResult = null,
                    additionalNotes = notes.value.ifBlank { null },
                    timestamp = System.currentTimeMillis()
                )
            )
            onComplete()
        }
    }

    fun savePostRepair(
        scanId: String,
        repairResult: String,
        repairActions: List<String>,
        onComplete: () -> Unit = {}
    ) {
        viewModelScope.launch {
            technicianRepository.save(
                TechnicianInputEntity(
                    scanId = scanId,
                    isPreRepair = false,
                    customerComplaint = null,
                    faultCategories = selectedCategories.value.joinToString(","),
                    technicianConfidence = null,
                    repairActions = repairActions.joinToString(","),
                    repairResult = repairResult,
                    additionalNotes = notes.value.ifBlank { null },
                    timestamp = System.currentTimeMillis()
                )
            )
            onComplete()
        }
    }
}

@HiltViewModel
class ComparisonViewModel @Inject constructor(
    private val scanRepository: ScanRepository,
    private val scoreRepository: ScoreRepository,
    private val testResultRepository: TestResultRepository
) : ViewModel() {
    private val _beforeScan = MutableStateFlow<ScanEntity?>(null)
    val beforeScan: StateFlow<ScanEntity?> = _beforeScan
    private val _afterScan = MutableStateFlow<ScanEntity?>(null)
    val afterScan: StateFlow<ScanEntity?> = _afterScan

    private val _beforeScore = MutableStateFlow<net.neuralcheck.data.local.db.entity.ScanScoreEntity?>(null)
    val beforeScore: StateFlow<net.neuralcheck.data.local.db.entity.ScanScoreEntity?> = _beforeScore
    private val _afterScore = MutableStateFlow<net.neuralcheck.data.local.db.entity.ScanScoreEntity?>(null)
    val afterScore: StateFlow<net.neuralcheck.data.local.db.entity.ScanScoreEntity?> = _afterScore

    private val _categoryDeltas = MutableStateFlow<List<CategoryDelta>>(emptyList())
    val categoryDeltas: StateFlow<List<CategoryDelta>> = _categoryDeltas

    data class CategoryDelta(val name: String, val beforeScore: Float, val afterScore: Float) {
        val delta: Float = afterScore - beforeScore
    }

    fun loadScans(scanId1: String, scanId2: String) {
        viewModelScope.launch {
            _beforeScan.value = scanRepository.getScanById(scanId1)
            _afterScan.value = scanRepository.getScanById(scanId2)
            val before = scoreRepository.getByScanId(scanId1)
            val after = scoreRepository.getByScanId(scanId2)
            _beforeScore.value = before
            _afterScore.value = after

            if (before != null && after != null) {
                val gson = com.google.gson.Gson()
                val mapType = object : com.google.gson.reflect.TypeToken<Map<String, Map<String, Any>>>() {}.type
                val beforeCats: Map<String, Map<String, Any>> = runCatching {
                    gson.fromJson<Map<String, Map<String, Any>>>(before.categoryScoresJson, mapType)
                }.getOrNull() ?: emptyMap()
                val afterCats: Map<String, Map<String, Any>> = runCatching {
                    gson.fromJson<Map<String, Map<String, Any>>>(after.categoryScoresJson, mapType)
                }.getOrNull() ?: emptyMap()

                val deltas = (beforeCats.keys + afterCats.keys).distinct().map { key ->
                    val beforeScore = (beforeCats[key]?.get("score") as? Number)?.toFloat() ?: 0f
                    val afterScore = (afterCats[key]?.get("score") as? Number)?.toFloat() ?: 0f
                    val name = (afterCats[key]?.get("nameAr") ?: beforeCats[key]?.get("nameAr"))?.toString() ?: key
                    CategoryDelta(name, beforeScore, afterScore)
                }
                _categoryDeltas.value = deltas
            }
        }
    }
}

@HiltViewModel
class PeerSurveyViewModel @Inject constructor(
    private val peerRepository: PeerRepository
) : ViewModel() {
    val batteryDrainsFast = MutableStateFlow<Boolean?>(null)
    val overheats = MutableStateFlow<Boolean?>(null)
    val sluggish = MutableStateFlow<Boolean?>(null)

    fun save(scanId: String, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            peerRepository.save(
                PeerInputEntity(
                    scanId = scanId,
                    batteryDrainsFast = batteryDrainsFast.value,
                    overheats = overheats.value,
                    sluggish = sluggish.value,
                    timestamp = System.currentTimeMillis()
                )
            )
            onComplete()
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val scanRepository: ScanRepository,
    private val authManager: AuthManager
) : ViewModel() {
    // Switches the scan list to the signed-in user's rows whenever auth state
    // changes. flatMapLatest cancels the previous query as soon as a new uid
    // arrives, which keeps the UI from briefly rendering the previous user's
    // scans during an account switch.
    val scans = authManager.uidFlow
        .flatMapLatest { uid ->
            if (uid.isNullOrBlank()) flowOf(emptyList())
            else scanRepository.getAllScansByUserId(uid)
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val securePreferences: SecurePreferences,
    private val authManager: AuthManager
) : ViewModel() {
    val syncEnabled = MutableStateFlow(securePreferences.syncEnabled)
    val theme = MutableStateFlow(securePreferences.theme)
    val language = MutableStateFlow(securePreferences.language)

    // Exposed so SettingsScreen can hide the logout button when the user
    // was never signed in (datacollect flavor, or launch flavor right
    // after install). Not a Flow because auth state only changes through
    // [signOut] below or a fresh login on LoginScreen, which recreates the
    // graph anyway.
    val isLoggedIn: Boolean get() = authManager.isLoggedIn

    fun setSyncEnabled(enabled: Boolean) {
        securePreferences.syncEnabled = enabled
        syncEnabled.value = enabled
    }

    fun setTheme(value: String) {
        securePreferences.theme = value
        theme.value = value
    }

    fun setLanguage(value: String) {
        securePreferences.language = value
        LocaleHelper.persist(context, value)
        language.value = value
    }

    fun syncNow() {
        SyncScheduler.syncNow(context)
    }

    /**
     * Suspends on [AuthManager.signOut] (which clears Room + Firestore
     * cache) and only invokes [onComplete] once the wipe has finished, so
     * the screen can navigate to login without racing the DAO clear.
     */
    fun signOut(onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            authManager.signOut()
            onComplete()
        }
    }
}

// ============================================================================
// ProfileViewModel — backs ProfileScreen in the launch flavor. Holds the
// Firebase user identity + a SubscriptionStatus snapshot fetched from the
// `checkSubscription` Cloud Function. Decoupled from the scoring pipeline
// so the datacollect flavor (which never authenticates) can still compile
// this class into the graph without the backend being reachable.
// ============================================================================
@HiltViewModel
class ProfileViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authManager: AuthManager,
    private val firebaseService: FirebaseAnalysisService,
    private val securePreferences: SecurePreferences,
    private val inAppNotifier: InAppNotifier,
    private val subscriptionRefreshBus: net.neuralcheck.data.remote.SubscriptionRefreshBus
) : ViewModel() {

    init {
        // Re-fetch the profile (which includes subscription state) whenever
        // the back-office signals a plan change. Without this the user has to
        // background + foreground the app to see their just-approved payment
        // reflected in the stats card.
        viewModelScope.launch {
            subscriptionRefreshBus.events.collect {
                Log.d("NeuralCheck", "ProfileViewModel: refresh tick from bus")
                loadProfile()
            }
        }
    }

    /**
     * Promo-code dialog state. [error] carries the already-localized copy so
     * the dialog layer can render it without its own string lookup; [error]
     * is cleared when the dialog is dismissed or the user edits the input.
     * [justRedeemed] flips true for one composition frame after a successful
     * redemption so the dialog can auto-close before the snackbar fires.
     */
    data class PromoState(
        val isSubmitting: Boolean = false,
        val error: String? = null,
        val justRedeemed: Boolean = false
    )

    data class ProfileState(
        val displayName: String = "",
        val email: String = "",
        val avatarLetter: String = "",
        val isLoading: Boolean = true,
        val subscription: SubscriptionStatus = SubscriptionStatus(),
        val expiryEpochMs: Long? = null,
        val daysRemaining: Int = 0,
        // Localized copy of [subscription.plan], resolved against the current
        // app locale so the stat card shows "متجر — أساسي" / "Mağaza — Temel"
        // instead of the raw backend key "shop_basic". Computed in the VM so
        // we can log the inbound key at the same place we translate it — makes
        // it obvious when the backend is returning an unexpected value.
        val planDisplayName: String = "",
        val error: String? = null
    )

    private val _state = MutableStateFlow(ProfileState())
    val state: StateFlow<ProfileState> = _state

    private val _promoState = MutableStateFlow(PromoState())
    val promoState: StateFlow<PromoState> = _promoState

    val language = MutableStateFlow(securePreferences.language)

    init {
        loadProfile()
    }

    fun loadProfile() {
        val user = authManager.currentUser
        val name = user?.displayName?.takeIf { it.isNotBlank() }
            ?: user?.email?.substringBefore("@")?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.profile_guest_name)
        val email = user?.email ?: context.getString(R.string.profile_guest_email)
        val avatarLetter = name.trim().firstOrNull()?.uppercase() ?: "?"

        _state.value = _state.value.copy(
            displayName = name,
            email = email,
            avatarLetter = avatarLetter,
            isLoading = true,
            error = null
        )

        viewModelScope.launch {
            try {
                val sub = firebaseService.checkSubscription()
                Log.d(
                    "NeuralCheck",
                    "checkSubscription result: plan='${sub.plan}' " +
                        "isActive=${sub.isActive} scansRemaining=${sub.scansRemaining} " +
                        "scansTotal=${sub.scansTotal}"
                )
                val expiryMs = extractEpochMillis(sub.expiresAt)
                val days = expiryMs?.let {
                    val diff = it - System.currentTimeMillis()
                    if (diff > 0) TimeUnit.MILLISECONDS.toDays(diff).toInt() else 0
                } ?: 0
                _state.value = _state.value.copy(
                    isLoading = false,
                    subscription = sub,
                    expiryEpochMs = expiryMs,
                    daysRemaining = days,
                    planDisplayName = resolvePlanName(sub.plan),
                    error = null
                )
            } catch (t: Throwable) {
                Log.e("NeuralCheck", "checkSubscription failed: ${t.message}", t)
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = t.message
                )
            }
        }
    }

    /**
     * Maps the backend plan key to a short, stat-chip-sized localized name
     * ("فردي" / "Tek" / "أساسي" / "Temel" etc.). The stat card is cramped, so
     * we use [plan_short_*] keys here — the full-length names
     * ([profile_plan_*]) are still used elsewhere (SubscriptionScreen).
     *
     * Matching is intentionally substring-based on a lower-cased value so
     * the same code handles every shape the backend has shipped over time:
     * `shop_basic`, `SHOP_BASIC`, `basic`, `Shop Basic`, etc. The order of
     * checks matters — "pro" is tested before "basic" so a future
     * "shop_basic_pro" tier wouldn't be misclassified, and "single" is last
     * because it's the most specific token. Empty / unknown values fall
     * through to [plan_short_none].
     */
    private fun resolvePlanName(rawKey: String): String {
        Log.d("NeuralCheck", "Raw plan from Firestore: '$rawKey'")
        val plan = rawKey.trim().lowercase()
        val resId = when {
            plan.isEmpty() || plan == "none" || plan == "free" -> R.string.plan_short_none
            plan.contains("pro") -> R.string.plan_short_shop_pro
            plan.contains("basic") -> R.string.plan_short_shop_basic
            plan.contains("single") -> R.string.plan_short_single
            else -> {
                Log.w("NeuralCheck", "Unknown subscription plan key: '$rawKey'")
                R.string.plan_short_none
            }
        }
        return context.getString(resId)
    }

    /**
     * The `expiresAt` field on [SubscriptionStatus] is typed as `Any?` because
     * Cloud Functions may encode Firestore timestamps several different ways
     * depending on the serializer path. Concretely we've observed:
     *  - `Number` — plain epoch millis
     *  - `Map` with `_seconds`/`nanoseconds` or `seconds`/`nanoseconds` keys
     *     (Firestore Admin SDK output)
     *  - `String` — ISO-8601 stamp, legacy payloads
     *  - `com.google.firebase.Timestamp` — native type when Gson round-trips
     *     through the client SDK
     * Returning nullable Long keeps the UI code simple: callers just display
     * "—" when the value is missing or unparseable.
     */
    private fun extractEpochMillis(raw: Any?): Long? = when (raw) {
        null -> null
        is Number -> raw.toLong()
        is Timestamp -> raw.toDate().time
        is String -> runCatching { raw.toLong() }.getOrNull()
        is Map<*, *> -> {
            val seconds = (raw["_seconds"] ?: raw["seconds"]) as? Number
            seconds?.toLong()?.let { it * 1000L }
        }
        else -> null
    }

    fun setLanguage(value: String) {
        securePreferences.language = value
        LocaleHelper.persist(context, value)
        language.value = value
    }

    val isLoggedIn: Boolean get() = authManager.isLoggedIn

    /**
     * Suspends on [AuthManager.signOut] (which clears Room + Firestore
     * cache) and only invokes [onComplete] once the wipe has finished, so
     * the screen can navigate to login without racing the DAO clear.
     */
    fun signOut(onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            authManager.signOut()
            onComplete()
        }
    }

    // ─────────────────────── Promo code flow ───────────────────────

    /**
     * Redeems [rawCode] against the `redeemPromoCode` callable. On success
     * pushes an in-app snackbar, flips [PromoState.justRedeemed] true for
     * one cycle so the dialog can auto-dismiss, and re-runs
     * [loadProfile] so the stat card reflects the new subscription /
     * scans-remaining count. Three specific failure reasons get localized
     * copy — anything else falls back to a generic error message.
     */
    fun redeemPromoCode(rawCode: String) {
        val code = rawCode.trim()
        if (code.isBlank()) {
            _promoState.value = _promoState.value.copy(
                error = context.getString(R.string.promo_error_invalid)
            )
            return
        }
        _promoState.value = PromoState(isSubmitting = true)
        viewModelScope.launch {
            try {
                val result = firebaseService.redeemPromoCode(code)
                if (result.success) {
                    Log.d(
                        "NeuralCheck",
                        "redeemPromoCode success: plan='${result.plan}' " +
                            "scans=${result.scans}"
                    )
                    _promoState.value = PromoState(
                        isSubmitting = false,
                        justRedeemed = true
                    )
                    inAppNotifier.push(
                        messageResId = R.string.promo_success_message,
                        severity = InAppNotifier.Severity.SUCCESS
                    )
                    // Refresh the subscription so the user sees the new
                    // plan / scans-remaining on the stat card immediately.
                    loadProfile()
                } else {
                    _promoState.value = PromoState(
                        isSubmitting = false,
                        error = mapPromoError(result.message)
                    )
                }
            } catch (t: Throwable) {
                Log.w("NeuralCheck", "redeemPromoCode failed: ${t.message}", t)
                _promoState.value = PromoState(
                    isSubmitting = false,
                    error = mapPromoError(t.message)
                )
            }
        }
    }

    /** Called when the dialog is dismissed so stale error copy doesn't show. */
    fun resetPromoState() {
        _promoState.value = PromoState()
    }

    /**
     * Maps a server reason token — or a raw Firebase Functions exception
     * message — to the appropriate localized string. Patterns are loose
     * (`"expired"`, `"already"` / `"used"`, `"invalid"` / `"not_found"`) so
     * the mapping holds up whether the backend sends short tokens or
     * full-sentence error messages.
     */
    private fun mapPromoError(raw: String?): String {
        val msg = raw.orEmpty().lowercase()
        val resId = when {
            "expired" in msg -> R.string.promo_error_expired
            "already" in msg || "consumed" in msg ||
                ("used" in msg && "already" in msg) -> R.string.promo_error_already_used
            "invalid" in msg || "not_found" in msg || "not found" in msg ->
                R.string.promo_error_invalid
            else -> R.string.promo_error_generic
        }
        return context.getString(resId)
    }
}

// ============================================================================
// ShopSetupViewModel — backs ShopSetupScreen. Loads the current shop profile
// from `users/{uid}` (so a returning shop owner sees their existing values
// pre-filled), holds the live form state, and persists via
// FirebaseAnalysisService.updateShopInfo on save.
//
// Save success pushes a snackbar via [InAppNotifier] and clears the picked
// logo URI so a second save without re-picking won't re-upload the same
// image. We don't pop the back stack here — the screen is also reachable
// from the Profile menu and a user editing one field should stay put.
// ============================================================================
@HiltViewModel
class ShopSetupViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authManager: AuthManager,
    private val firebaseService: FirebaseAnalysisService,
    private val inAppNotifier: InAppNotifier
) : ViewModel() {

    data class State(
        val isLoading: Boolean = true,
        val isSaving: Boolean = false,
        val shopName: String = "",
        val shopDescription: String = "",
        val shopCity: String = "",
        val shopPhone: String = "",
        val shopAddress: String = "",
        val shopSlug: String = "",
        val savedLogoUrl: String = "",
        val pickedLogoUri: android.net.Uri? = null,
        val nameError: Boolean = false,
        // null = no error, otherwise the resource key for an inline message.
        // Using a resource ID instead of a raw string keeps the error
        // localizable and avoids Context coupling in pure-state code.
        val slugError: SlugError? = null,
        val error: String? = null
    )

    /** Discriminates client-side slug failures so the screen renders the right copy. */
    enum class SlugError { INVALID_FORMAT, ALREADY_TAKEN }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    init {
        load()
    }

    fun load() {
        val uid = authManager.currentUser?.uid
        if (uid.isNullOrBlank()) {
            _state.value = State(isLoading = false)
            return
        }
        _state.value = _state.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            try {
                val doc = Firebase.firestore.collection("users").document(uid)
                    .get().await()
                _state.value = State(
                    isLoading = false,
                    shopName = doc.getString("shopName").orEmpty(),
                    shopDescription = doc.getString("shopDescription").orEmpty(),
                    shopCity = doc.getString("shopCity").orEmpty(),
                    shopPhone = doc.getString("shopPhone").orEmpty(),
                    shopAddress = doc.getString("shopAddress").orEmpty(),
                    shopSlug = doc.getString("shopSlug").orEmpty(),
                    savedLogoUrl = doc.getString("shopLogoUrl").orEmpty()
                )
            } catch (t: Throwable) {
                Log.w("NeuralCheck", "ShopSetupViewModel load failed: ${t.message}", t)
                _state.value = _state.value.copy(isLoading = false, error = t.message)
            }
        }
    }

    fun setShopName(value: String) {
        _state.value = _state.value.copy(shopName = value.take(80), nameError = false)
    }

    fun setShopDescription(value: String) {
        _state.value = _state.value.copy(shopDescription = value.take(200))
    }

    fun setShopCity(value: String) {
        _state.value = _state.value.copy(shopCity = value.take(60))
    }

    fun setShopPhone(value: String) {
        _state.value = _state.value.copy(shopPhone = value.take(30))
    }

    fun setShopAddress(value: String) {
        _state.value = _state.value.copy(shopAddress = value.take(200))
    }

    /**
     * Sanitizes slug input as the user types: lowercases, drops anything
     * outside `[a-z0-9-]`, collapses repeat dashes, and caps length at 30.
     * The keyboard still allows Arabic/uppercase/spaces for accessibility,
     * but the stored value is always URL-safe — no surprising server-side
     * silent rewrites.
     */
    fun setShopSlug(value: String) {
        val cleaned = value
            .lowercase()
            .replace(Regex("[^a-z0-9-]"), "")
            .replace(Regex("-+"), "-")
            .trimStart('-')
            .take(30)
        _state.value = _state.value.copy(shopSlug = cleaned, slugError = null)
    }

    fun setLogoUri(uri: android.net.Uri?) {
        _state.value = _state.value.copy(pickedLogoUri = uri)
    }

    fun save() {
        val s = _state.value
        if (s.shopName.isBlank()) {
            _state.value = s.copy(nameError = true)
            return
        }
        // A user-supplied slug must satisfy the URL format. Empty is fine —
        // the backend then auto-generates one from the shop name (existing
        // behavior). We only validate when the user actually typed something.
        val rawSlug = s.shopSlug.trim().trimEnd('-')
        if (rawSlug.isNotBlank() && !SLUG_REGEX.matches(rawSlug)) {
            _state.value = s.copy(slugError = SlugError.INVALID_FORMAT)
            return
        }
        _state.value = s.copy(isSaving = true, error = null, slugError = null)
        viewModelScope.launch {
            try {
                // Uniqueness check — only when the user typed a custom slug
                // (server-side path also runs one for auto-generated slugs,
                // so we don't double-check the empty case). Excluding the
                // current uid lets the same user re-save without tripping
                // their own previously-stored slug.
                if (rawSlug.isNotBlank()) {
                    val uid = authManager.currentUser?.uid.orEmpty()
                    val taken = isSlugTaken(rawSlug, excludeUid = uid)
                    if (taken) {
                        _state.value = _state.value.copy(
                            isSaving = false,
                            slugError = SlugError.ALREADY_TAKEN
                        )
                        return@launch
                    }
                }

                firebaseService.updateShopInfo(
                    shopName = s.shopName.trim(),
                    shopDescription = s.shopDescription.trim(),
                    shopCity = s.shopCity.trim(),
                    shopPhone = s.shopPhone.trim(),
                    shopAddress = s.shopAddress.trim(),
                    logoUri = s.pickedLogoUri,
                    customSlug = rawSlug.ifBlank { null }
                )
                inAppNotifier.push(
                    R.string.shop_setup_save_success,
                    InAppNotifier.Severity.SUCCESS
                )
                // Reload so the saved logo URL replaces the just-uploaded
                // local URI preview, and so the form reflects the
                // server-canonical state (in case the slug got suffixed).
                load()
            } catch (t: Throwable) {
                Log.w("NeuralCheck", "ShopSetupViewModel save failed: ${t.message}", t)
                _state.value = _state.value.copy(
                    isSaving = false,
                    error = t.message ?: context.getString(R.string.shop_setup_save_failed)
                )
            }
        }
    }

    /**
     * Queries `users` for any document with this `shopSlug` other than the
     * current user's. Limit 2 because we only need to know "owned by someone
     * other than me" — fetching more would burn reads for no information.
     */
    private suspend fun isSlugTaken(slug: String, excludeUid: String): Boolean = try {
        val snap = Firebase.firestore.collection("users")
            .whereEqualTo("shopSlug", slug)
            .limit(2)
            .get()
            .await()
        snap.documents.any { it.id != excludeUid }
    } catch (t: Throwable) {
        Log.w("NeuralCheck", "isSlugTaken query failed: ${t.message}", t)
        // Network failure → fall through and let updateShopInfo's server-side
        // suffix-on-collision logic handle it. Better than blocking the save.
        false
    }

    private companion object {
        // 3–30 chars total: alphanumeric start, alphanumeric or dash in the
        // middle, alphanumeric end. Three explicit groups (1 + 1..28 + 1)
        // make the bounds easy to audit and rule out the leading/trailing
        // dash edge case the simpler `[a-z0-9-]{3,30}` pattern would allow.
        val SLUG_REGEX = Regex("^[a-z0-9][a-z0-9-]{1,28}[a-z0-9]$")
    }
}

// ============================================================================
// SubscriptionViewModel — backs SubscriptionScreen. Fetches the current plan
// via `checkSubscription` so the plan-picker can badge the user's existing
// plan ("خطتك الحالية") and flip each card's CTA between Renew / Upgrade /
// Downgrade / Buy-extra. Separate from ProfileViewModel (instead of sharing
// state) because the two screens have independent lifecycles — opening
// Subscription after a plan change should always re-query, never show stale
// data that happened to be cached on the Profile screen.
// ============================================================================
@HiltViewModel
class SubscriptionViewModel @Inject constructor(
    private val firebaseService: FirebaseAnalysisService
) : ViewModel() {

    data class State(
        val isLoading: Boolean = true,
        val currentPlan: String = "",
        val error: String? = null
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    init {
        load()
    }

    fun load() {
        _state.value = State(isLoading = true)
        viewModelScope.launch {
            try {
                val sub = firebaseService.checkSubscription()
                Log.d(
                    "NeuralCheck",
                    "SubscriptionViewModel: plan='${sub.plan}' isActive=${sub.isActive}"
                )
                // Inactive subscription → treat as "no plan" so every card
                // shows the neutral default CTA instead of the renew/downgrade
                // branches, which only make sense for an active plan.
                val normalized = if (sub.isActive) sub.plan.trim().lowercase() else ""
                _state.value = State(isLoading = false, currentPlan = normalized)
            } catch (t: Throwable) {
                Log.e("NeuralCheck", "SubscriptionViewModel: load failed: ${t.message}", t)
                _state.value = State(isLoading = false, error = t.message)
            }
        }
    }
}

// ============================================================================
// PaymentViewModel — drives PaymentScreen. The single callable is
// `requestPayment(plan, reference)` which returns a paymentId + status the
// user doesn't need to act on (back-office reviews the reference manually).
// We fire both a system-tray notification and an in-app snackbar on success
// so the confirmation is visible regardless of whether the user stays in the
// app or backgrounds it immediately after tapping Confirm.
// ============================================================================
@HiltViewModel
class PaymentViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val firebaseService: FirebaseAnalysisService,
    private val notificationHelper: NotificationHelper,
    private val inAppNotifier: InAppNotifier
) : ViewModel() {

    data class PaymentState(
        val isSubmitting: Boolean = false,
        val isSubmitted: Boolean = false,
        val error: String? = null
    )

    private val _state = MutableStateFlow(PaymentState())
    val state: StateFlow<PaymentState> = _state

    val reference = MutableStateFlow("")

    /**
     * The ShamCash transfer number the user is asked to send the payment to.
     * Backed by `settings/app.shamCashNumber` in Firestore so the operator
     * can swap accounts (e.g., move to a new wallet) without shipping an
     * APK update. Falls back to the hardcoded `payment_transfer_number`
     * string resource on any failure (Firestore unreachable, doc missing,
     * field absent) so the screen ALWAYS has a number to display.
     *
     * Initial value is the resource fallback so the row never flashes
     * empty during the first composition; the Firestore read swaps it in
     * a moment later if the back-office published a different value.
     */
    private val _transferNumber = MutableStateFlow(
        context.getString(R.string.payment_transfer_number)
    )
    val transferNumber: StateFlow<String> = _transferNumber

    init {
        loadTransferNumber()
    }

    private fun loadTransferNumber() {
        viewModelScope.launch {
            Log.d("NC.Pay", "=== loadTransferNumber START ===")
            Log.d("NC.Pay", "auth user: ${Firebase.auth.currentUser?.uid}")
            Log.d("NC.Pay", "auth email: ${Firebase.auth.currentUser?.email}")
            try {
                val doc = Firebase.firestore
                    .collection("settings")
                    .document("app")
                    .get()
                    .await()
                Log.d("NC.Pay", "doc exists: ${doc.exists()}")
                Log.d("NC.Pay", "doc data: ${doc.data}")
                val remote = doc.getString("shamCashNumber")
                Log.d("NC.Pay", "shamCashNumber value: '$remote'")
                val nonBlank = remote?.takeIf { it.isNotBlank() }
                if (nonBlank != null) {
                    Log.d("NC.Pay", "transferNumber set to Firestore value: $nonBlank")
                    _transferNumber.value = nonBlank
                } else {
                    Log.w("NC.Pay", "shamCashNumber missing/blank — keeping resource fallback '${_transferNumber.value}'")
                }
            } catch (e: Exception) {
                Log.e("NC.Pay", "FAILED: ${e.javaClass.simpleName}: ${e.message}")
                Log.e("NC.Pay", "cause: ${e.cause?.message}")
            }
        }
    }

    fun setReference(value: String) {
        reference.value = value
        if (_state.value.error != null) {
            _state.value = _state.value.copy(error = null)
        }
    }

    fun submit(plan: String) {
        val ref = reference.value.trim()
        if (ref.isBlank()) {
            _state.value = _state.value.copy(error = "empty")
            return
        }
        _state.value = _state.value.copy(isSubmitting = true, error = null)
        viewModelScope.launch {
            Log.d("NC.Pay", "=== submit START ===")
            Log.d("NC.Pay", "auth user: ${Firebase.auth.currentUser?.uid}")
            Log.d("NC.Pay", "auth email: ${Firebase.auth.currentUser?.email}")
            Log.d("NC.Pay", "plan='$plan'  ref='$ref'")
            try {
                // Only the actual callable is allowed to fail the submit. The
                // tray + snackbar side-effects below were earlier inside the
                // same try/catch — when POST_NOTIFICATIONS was denied on
                // Android 13+, `nm.notify` threw SecurityException AFTER the
                // payment had already been recorded server-side, so the user
                // saw "تعذّر إرسال الدفع" while the admin saw the request.
                Log.d("NC.Pay", "calling firebaseService.requestPayment …")
                firebaseService.requestPayment(plan = plan, reference = ref)
                Log.d("NC.Pay", "requestPayment SUCCESS")
                _state.value = _state.value.copy(
                    isSubmitting = false,
                    isSubmitted = true,
                    error = null
                )
                // Best-effort UX confirmations — failures here MUST NOT roll
                // back the success state we just published. Wrapping each
                // call in runCatching keeps a missing notification permission
                // (or a transient snackbar host issue) from making a healthy
                // submit look broken.
                runCatching { notificationHelper.showPaymentSubmitted() }
                    .onFailure {
                        Log.w("NeuralCheck", "showPaymentSubmitted failed (non-fatal): ${it.message}")
                    }
                runCatching {
                    inAppNotifier.push(R.string.in_app_payment_submitted, InAppNotifier.Severity.SUCCESS)
                }.onFailure {
                    Log.w("NeuralCheck", "inAppNotifier.push failed (non-fatal): ${it.message}")
                }
            } catch (t: Throwable) {
                Log.e("NC.Pay", "requestPayment FAILED: ${t.javaClass.simpleName}: ${t.message}")
                Log.e("NC.Pay", "cause: ${t.cause?.message}")
                _state.value = _state.value.copy(
                    isSubmitting = false,
                    error = t.message ?: "generic"
                )
            }
        }
    }
}

// ============================================================================
// ScanHistoryViewModel — reads the user's scans from Firestore's `reports`
// collection. Deliberately uses the client SDK (not ScanRepository) because
// this screen shows SYNCED scans (the canonical cloud record), while the
// existing HistoryScreen is local-first and can include un-synced scans.
// Keeping the two paths separate means one has no clever caching to
// complicate the other's error handling.
// ============================================================================
/**
 * Order 2026-09-25 (القرار ٨٦ البند ٤). [ScanHistoryViewModel]'s twin of
 * [buildHomeLastScan] — see that function's doc for the full rationale.
 *
 * Order 2026-09-26 (القرار ١٠٢ البند ٢): same widened V6/V7/UNKNOWN
 * three-way split via [net.neuralcheck.data.remote.model.AiPipeline.classifyShape],
 * same reuse of [net.neuralcheck.data.remote.readV7OverallSummary] — kept
 * as a parallel twin rather than a shared generic helper, matching this
 * pair's own pre-existing convention (the two return different data
 * classes: `LastScan.summary` vs `CloudScan.verdict`).
 */
internal fun buildCloudScan(
    scanId: String,
    data: Map<String, Any?>,
    createdAtMs: Long,
    context: Context
): ScanHistoryViewModel.CloudScan {
    val pipeline = data["pipeline"] as? String
    val deviceModel = (data["deviceModel"] as? String) ?: (data["device"] as? String) ?: "—"
    val v7 = net.neuralcheck.data.remote.readV7OverallSummary(data, context)
    val hasRootGrade = (data["grade"] as? String)?.isNotBlank() == true

    val shape = net.neuralcheck.data.remote.model.AiPipeline.classifyShape(
        pipeline,
        hasRootLetterOrGrade = hasRootGrade,
        hasV7Overall = v7 != null
    )

    if (shape == net.neuralcheck.data.remote.model.AiPipeline.ShapeKind.V7) {
        return ScanHistoryViewModel.CloudScan(
            scanId = scanId,
            deviceModel = deviceModel,
            grade = v7?.letter ?: context.getString(R.string.report_grade_unavailable),
            verdict = v7?.absenceReasonText ?: "",
            createdAtMs = createdAtMs,
            isFlatShapeRecognized = false,
            pipeline = pipeline
        )
    }
    if (shape == net.neuralcheck.data.remote.model.AiPipeline.ShapeKind.UNKNOWN) {
        Log.w("NeuralCheck", "buildCloudScan($scanId): unrecognised pipeline shape, tag='$pipeline'")
        return ScanHistoryViewModel.CloudScan(
            scanId = scanId,
            deviceModel = deviceModel,
            grade = context.getString(R.string.report_grade_unavailable),
            verdict = "",
            createdAtMs = createdAtMs,
            isFlatShapeRecognized = false,
            pipeline = pipeline
        )
    }

    // shape == V6 here
    return ScanHistoryViewModel.CloudScan(
        scanId = scanId,
        deviceModel = deviceModel,
        grade = (data["grade"] as? String) ?: "?",
        verdict = (data["verdict"] as? String) ?: (data["summary"] as? String) ?: "",
        createdAtMs = createdAtMs,
        isFlatShapeRecognized = true,
        pipeline = pipeline
    )
}

@HiltViewModel
class ScanHistoryViewModel @Inject constructor(
    private val authManager: AuthManager,
    // Order 2026-09-25 (القرار ٨٦ البند ٤): needed only to resolve the V7
    // absence-reason strings via net.neuralcheck.data.remote.readV7OverallSummary
    // — every other path in this ViewModel is unaffected.
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context
) : ViewModel() {

    data class CloudScan(
        val scanId: String,
        val deviceModel: String,
        /** Same meaning as [HomeViewModel.LastScan.grade] (order 2026-09-25،
         *  القرار ٨٦ البند ٤) — the real letter, "الحرف غير متوفر" text for
         *  a V7 report with a legitimately absent letter, and (order
         *  2026-09-26، القرار ١٠٢ البند ٢) also for a genuinely
         *  unrecognised future pipeline shape — never "?" any more. */
        val grade: String,
        /** V6 verdict/summary text, or — for a V7 report with an absent
         *  letter — the same resolved reason text [HomeViewModel.LastScan.summary]
         *  carries (see [net.neuralcheck.data.remote.V7OverallSummary.absenceReasonText]). */
        val verdict: String,
        val createdAtMs: Long,
        /** Same meaning as [HomeViewModel.LastScan.isFlatShapeRecognized] —
         *  this is the same raw-Firestore flat-shape reader pattern,
         *  duplicated between the two ViewModels (see that class's doc for
         *  the full rationale). False means [grade]/[verdict] are this
         *  struct's own placeholders, not measured data. */
        val isFlatShapeRecognized: Boolean = true,
        val pipeline: String? = null
    )

    data class HistoryState(
        val isLoading: Boolean = true,
        val scans: List<CloudScan> = emptyList(),
        val error: String? = null
    )

    private val _state = MutableStateFlow(HistoryState())
    val state: StateFlow<HistoryState> = _state

    init {
        load()
    }

    fun load() {
        val uid = authManager.currentUser?.uid
        if (uid.isNullOrBlank()) {
            _state.value = HistoryState(isLoading = false, scans = emptyList(), error = null)
            return
        }
        _state.value = _state.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            try {
                // Logged at Info so the "Create Index" link Firestore prints
                // for missing composite indexes is easy to correlate with
                // the exact query that triggered it.
                Log.d("NeuralCheck", "Querying reports for uid: $uid")
                val snapshot = Firebase.firestore
                    .collection("reports")
                    .whereEqualTo("userId", uid)
                    .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                    .limit(50)
                    .get()
                    .await()

                val scans = snapshot.documents.mapNotNull { doc ->
                    val createdAtMs = when (val raw = doc.get("createdAt")) {
                        is Timestamp -> raw.toDate().time
                        is Number -> raw.toLong()
                        else -> doc.getDate("createdAt")?.time ?: 0L
                    }
                    val data = doc.data ?: emptyMap()
                    val pipeline = data["pipeline"] as? String
                    if (!AiPipeline.isRecognizedFlatShape(pipeline) && pipeline != net.neuralcheck.data.remote.model.AiPipeline.V7_FINDINGS_BASED) {
                        Log.w(
                            "NeuralCheck",
                            "ScanHistoryViewModel.load: doc ${doc.id} has " +
                                "pipeline='$pipeline', which neither the flat nor the V7 " +
                                "reader can parse — grade/score/verdict will show as " +
                                "unmeasured placeholders, not real values"
                        )
                    }
                    buildCloudScan(scanId = doc.id, data = data, createdAtMs = createdAtMs, context = context)
                }
                _state.value = HistoryState(
                    isLoading = false,
                    scans = scans,
                    error = null
                )
            } catch (t: Throwable) {
                Log.e("NeuralCheck", "reports query failed: ${t.message}", t)
                _state.value = HistoryState(
                    isLoading = false,
                    scans = emptyList(),
                    error = t.message
                )
            }
        }
    }
}

// ============================================================================
// PaymentHistoryViewModel — reads the signed-in user's payment requests from
// Firestore's `payments` collection so they can see pending submissions and
// the outcome (approved / rejected + reason) without having to contact
// support. Separate from PaymentViewModel because that one drives the
// *submission* flow; this one is read-only and needs no callable.
//
// We deliberately do NOT attach a snapshot listener. The user lands on this
// screen rarely; a one-shot `.get()` avoids keeping a live Firestore channel
// open when they drill in, and the "retry" button gives them a manual
// refresh for the common case where a push arrives while they're on this
// screen and they want to see the new state.
// ============================================================================
@HiltViewModel
class PaymentHistoryViewModel @Inject constructor(
    private val authManager: AuthManager
) : ViewModel() {

    enum class Status { PENDING, CONFIRMED, REJECTED, UNKNOWN }

    data class Payment(
        val id: String,
        val plan: String,
        val amount: Long?,
        val reference: String,
        val status: Status,
        val reason: String?,
        val createdAtMs: Long
    )

    data class State(
        val isLoading: Boolean = true,
        val payments: List<Payment> = emptyList(),
        val error: String? = null
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    init {
        load()
    }

    fun load() {
        val uid = authManager.currentUser?.uid
        if (uid.isNullOrBlank()) {
            _state.value = State(isLoading = false, payments = emptyList(), error = null)
            return
        }
        _state.value = _state.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            try {
                Log.d("NeuralCheck", "Querying payments for uid: $uid")
                val snapshot = Firebase.firestore
                    .collection("payments")
                    .whereEqualTo("userId", uid)
                    .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                    .limit(20)
                    .get()
                    .await()

                val payments = snapshot.documents.map { doc ->
                    val createdAtMs = when (val raw = doc.get("createdAt")) {
                        is Timestamp -> raw.toDate().time
                        is Number -> raw.toLong()
                        else -> doc.getDate("createdAt")?.time ?: 0L
                    }
                    Payment(
                        id = doc.id,
                        plan = doc.getString("plan") ?: "",
                        amount = (doc.get("amount") as? Number)?.toLong(),
                        reference = doc.getString("reference") ?: "",
                        status = parseStatus(doc.getString("status")),
                        // Back-office writes either `reason` or `rejectionReason`
                        // depending on which admin tool was used; we accept both
                        // so the user sees whichever was populated.
                        reason = doc.getString("rejectionReason")
                            ?: doc.getString("reason"),
                        createdAtMs = createdAtMs
                    )
                }
                _state.value = State(isLoading = false, payments = payments, error = null)
            } catch (t: Throwable) {
                Log.e("NeuralCheck", "payments query failed: ${t.message}", t)
                _state.value = State(isLoading = false, payments = emptyList(), error = t.message)
            }
        }
    }

    private fun parseStatus(raw: String?): Status = when (raw?.lowercase()) {
        "pending", "submitted", "under_review" -> Status.PENDING
        "confirmed", "approved", "active" -> Status.CONFIRMED
        "rejected", "denied", "refused" -> Status.REJECTED
        else -> Status.UNKNOWN
    }
}

// ============================================================================
// GenerateSessionViewModel — shop-owner side. Creates a scan session via
// Cloud Functions, exposes the short code + QR content, and subscribes to
// the `scanSessions/{id}` doc so the UI can reactively show "waiting →
// joined → scanning → completed" as the customer device progresses.
//
// No polling: Firestore's snapshot listener gives us sub-second updates
// for free, and we detach it in onCleared() to avoid stale callbacks.
// ============================================================================

/**
 * ملحق خامس بالقرار ١٠٤ البند ٣، مصحَّحاً بالملحق السادس البند ١.
 * `createScanSession`'s thrown message is built server-side from the
 * underlying caught error's own text (`createScanSession.js:76`,
 * relayed, not independently verified — neuralcheck-backend is a
 * separate repository, rule 2) — unlike `analyzeDevice`'s ten
 * hand-authored `HttpsError` strings ([analysisErrorMessage] in
 * `AiScoringEngine.kt`), this could carry raw technical detail or
 * third-party-provider text. `GenerateSessionScreen.kt`'s `ErrorState`
 * (:110-111) now always receives `session_error_generic` ("تعذّر إنشاء
 * الجلسة" / "Oturum oluşturulamadı", already committed with a Turkish
 * translation, independently verified — `values/strings.xml:1225`,
 * `values-tr/strings.xml:953`) for this catch, never the raw message
 * (القرار ٨٢ البند ٦). Chosen over the more generic `error_generic`:
 * `session_error_generic` is the existing, on-topic string for THIS
 * exact screen and failure ("session could not be created"), not a
 * placeholder — this order's own correction from the previous round,
 * which had used `error_generic` here.
 *
 * Rule 36 judgment (explicitly asked for): logged WITHOUT passing [t] as
 * `Log.e`'s third argument — passing a `Throwable` there makes Android
 * append its own full, UNBOUNDED `toString()` + stack trace, which would
 * defeat truncating the message text below. The message is capped to
 * its first 200 characters instead. Stricter than
 * [analysisErrorMessage]'s judgment for the same reason named above —
 * that message is a deliberately client-safe string the backend authors
 * chose; this one is an arbitrary re-thrown error's text. The trade-off
 * (losing the full stack trace in this one log line) is intentional.
 *
 * Extracted (A110 precedent) so this is unit-testable with a mocked
 * [Context] without constructing [FirebaseAnalysisService] (same
 * construction blocker as [analysisErrorMessage]'s own tests —
 * `FirebaseAnalysisService`'s eager `Firebase.functions` field
 * initializer throws with no `FirebaseApp` in a plain JVM test).
 */
internal fun createSessionErrorMessage(context: Context, t: Throwable): String =
    // A-SUB wave 1: the create has a table like the other session calls (SessionMessages.kt, SessionCall.CREATE): a refusal that names SHOP_SUBSCRIPTION_EXPIRED or SHOP_NO_CREDIT shows
    // its approved text; every other failure shows session_error_generic exactly as above, and the bounded log line (rule 36) is printed there, once, for every call.
    sessionErrorMessage(context, SessionCall.CREATE, t)

/**
 * ملحق سادس بالقرار ١٠٤ البند ١ (2026-09-27). `attachListener`'s
 * Firestore snapshot listener used to show `"[listener $code]
 * ${err.message}"` verbatim — the same class of raw-message leak
 * `createSessionErrorMessage` (above) already fixed for the `create()`
 * path, on the same screen (`GenerateSessionScreen.kt`'s `ErrorState`,
 * same `State.error` field).
 *
 * No existing string is specific to a listen failure: `session_expired`
 * ("انتهت صلاحية الكود" / "Kodun süresi doldu") describes the
 * `scanSessions` document's own `status` field once a snapshot is
 * successfully delivered — a business state, never a listen-level
 * error — and `join_session_invalid` is the JOIN screen's own concern
 * (a different Cloud Function, `joinScanSession`, not this Firestore
 * read). Every code a Firestore listener actually delivers here
 * (`PERMISSION_DENIED` — a security-rule rejection, per this file's own
 * existing comment — plus `UNAVAILABLE`/`CANCELLED`/`DEADLINE_EXCEEDED`/
 * etc., all genuine database-access failures) is about database access,
 * not about the code's validity or the session's expiry. `error_generic`
 * is therefore the only existing string that fits.
 *
 * Rule 36: logged WITHOUT passing [err] as `Log.e`'s third argument
 * (would append Android's own unbounded `toString()`/stack trace) and
 * with the message capped to 200 characters — same treatment as
 * [createSessionErrorMessage], for the same reason: a Firestore
 * exception's message is not a hand-authored, deliberately client-safe
 * string the way `analyzeDevice`'s ten `HttpsError` calls are.
 *
 * Extracted (A110 precedent) so this is unit-testable with a mocked
 * [Context] and a mocked [FirebaseFirestoreException], without
 * constructing [GenerateSessionViewModel] or [FirebaseAnalysisService].
 */
internal fun sessionListenerErrorMessage(
    context: Context,
    collection: String,
    sessionId: String,
    err: Throwable
): String {
    val code = (err as? FirebaseFirestoreException)?.code
    val boundedMessage = err.message?.take(200)
    Log.e(
        "NeuralCheck",
        "session listener error: path=$collection/${LogRedaction.hashId(sessionId)} " +
            "code=$code class=${err.javaClass.simpleName} message=$boundedMessage"
    )
    return context.getString(R.string.error_generic)
}

@HiltViewModel
class GenerateSessionViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val analysisService: FirebaseAnalysisService,
    // Batch 2, item 2: the owner's cancel button sends through the one canceller the customer's leave uses too.
    private val canceller: SessionCanceller
) : ViewModel() {

    /**
     * CANCELLED (batch 2, item 2): the session's status is "cancelled" - by this owner's button, or by the joined device (left / declined consent).
     * There is no JOINED (batch 2, item 3): the server writes no `joined` status - the join writes `scanning` - so no status could ever have produced it.
     * What each status shows is [ownerPhaseFor]'s, one place.
     */
    enum class Phase { CREATING, WAITING, SCANNING, COMPLETED, EXPIRED, CANCELLED, ERROR }

    /** The owner's cancel request, for the session this screen shows. */
    sealed interface CancelUi {
        data object Idle : CancelUi

        /** The call is out: the button is disabled, a second tap does nothing. */
        data object InFlight : CancelUi

        /** The server refused, or the call failed: [message] is the text decided by SessionMessages.kt (the approved text, or the one that point already showed). */
        data class Failed(val message: String) : CancelUi
    }

    data class State(
        val phase: Phase = Phase.CREATING,
        /** null until a session was created - never "" (batch 2, item 4, rule 22: an empty code must not reach the chip or the QR). */
        val sessionId: String? = null,
        /** null until a session was created - same as [sessionId]. */
        val code: String? = null,
        /** null = the server named no lifetime (rule 22): the screen shows no countdown. */
        val expiresInMinutes: Int? = null,
        val completedScanId: String? = null,
        val error: String? = null,
        /**
         * Whether the error screen offers «إعادة المحاولة». False only when the create was refused with a NAMED reason ([createRefusalIsNamed]): retrying the same call for the same
         * account gets the same answer. True for every other failure (no code, an unknown code, a disconnect, a malformed answer) - A-SUB wave 1, user decision 4.
         */
        val retryable: Boolean = true,
        /** Batch 2, item 2: the owner's cancel of THIS session ([sessionId]); reset by every [create]. */
        val cancel: CancelUi = CancelUi.Idle,
        /**
         * Batch 3, item 2: why the session is cancelled, as the server's `cancelReason` reads ([CancelReason.Read]: a known reason, `Unknown`, or `Absent`). It decides the text of
         * the cancelled state ([ownerCancelledTextFor]) and means something ONLY while [phase] is [Phase.CANCELLED]. `Absent` is the named state "no reason read yet / the document
         * carried none" - the screen shows text 3 for it and the log says so (`owner-cancelled-shown`); it is never read as one of the three reasons.
         */
        val cancelReason: CancelReason.Read = CancelReason.Read.Absent,
        /**
         * A-P6-03 (غرض الفحص): the purpose chips of THIS session - [PurposeUi.NotRead] until the create answers, then whether the shop has the part (`protocolParts.purposes`) and
         * what the chips are. Reset by every [create]. The logic lives in `SessionPurposeUi.kt`; the screen draws it only when the chips' texts are approved.
         */
        val purposes: PurposeUi = PurposeUi.NotRead,
        /**
         * A-P6-07 (ف١٤: the notice that the scanned phone was chosen for a cold repeat): what the server said about the repeat of the scan this session completed with. [RepeatNoticeUi.NotAsked] until the session is
         * completed and the call is open. Reset by every [create]. The screen draws it only while [net.neuralcheck.presentation.screen.REPEAT_TEXTS_APPROVED] is true (the repeat screens' texts are approved and written since 2026-10-23).
         */
        val repeatNotice: RepeatNoticeUi = RepeatNoticeUi.NotAsked,
        /**
         * The «after maintenance» pair list of THIS session (order of 2026-10-22): [PairListUi.NotShown] until the server accepted a `maintenance_after` purpose for it, then the list the server gave, the technician's choice
         * and its confirmation. Reset by every [create] and by every accepted purpose that is not `maintenance_after`. The logic lives in `PairListUi.kt`; the screen draws it only when the list's texts are approved
         * ([net.neuralcheck.presentation.screen.PAIR_LIST_TEXTS_APPROVED]) - and the two calls leave the phone only through the service's gate.
         */
        val pairs: PairListUi = PairListUi.NotShown
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    /** A-P6-07: the owner's phone asks the server whether its completed session's scan was chosen for a repeat. The pair asked about, so a second snapshot of the same completion does not ask again. */
    private val repeatClient = RepeatScanClient(analysisService)
    private var repeatAskedFor: Pair<String, String>? = null

    /** The maintenance link's two calls (order of 2026-10-22): the list of open «before» scans of this session and the owner's confirmation. Nothing is sent while the pair list's texts are not approved (the service's gate). */
    private val pairClient = PairLinkClient(analysisService)

    /** Which asking of the list (or the confirmation of it) is current: a late answer for an earlier one - another purpose, another code - is dropped, the way [selectPurpose] drops an answer for another session. */
    private var pairsGeneration = 0

    /** Batch 2, item 3: the owner's screen reads the session document through the same watcher the customer's scan screen uses; the status it reads is [SessionStatus]'s five. */
    private val sessionWatcher = SessionDocWatcher()

    private var listener: SessionDocWatcher.Registration? = null

    init {
        create()
    }

    fun create() {
        _state.value = State(phase = Phase.CREATING)
        // order of 2026-10-22: a new session is a new asking for the pair list too - an answer still out for the old session is dropped
        pairsGeneration++
        // A-P6-07: a new session is a new question for the repeat scan's notice
        repeatAskedFor = null
        viewModelScope.launch {
            try {
                val authUid = Firebase.auth.currentUser?.uid
                Log.d("NeuralCheck", "createScanSession: calling callable (authUid=$authUid)")
                val session = analysisService.createScanSession()
                // A-SUB wave 1, item 10: the session id and the code are hashed (LogRedaction.hashId), never printed; a missing one prints "missing", the event stays
                Log.d(
                    "NeuralCheck",
                    "createScanSession result: sessionId=${LogRedaction.hashId(session.sessionId)}, " +
                        "code=${LogRedaction.hashId(session.code)}, expiresInMinutes=${session.expiresInMinutes}"
                )
                if (session.expiresInMinutes == null) {
                    // declared, not defaulted: the owner's screen shows no countdown for this session
                    Log.w("NeuralCheck", "createScanSession: the answer carried no expiresInMinutes - no countdown is shown")
                }
                // Batch 2, item 4 (rule 22; user decision: «رمز الجلسة الفارغ في الشريحة»): an answer without a session id or without a code is a FAILURE to
                // create the session - a named one, in the log and on the screen - not an empty code chip and an empty QR. A blank text counts as absent.
                val sessionId = session.sessionId?.takeIf { it.isNotBlank() }
                val code = session.code?.takeIf { it.isNotBlank() }
                if (sessionId == null || code == null) {
                    _state.value = State(
                        phase = Phase.ERROR,
                        error = sessionCreateFailedMessage(context, sessionIdPresent = sessionId != null, codePresent = code != null)
                    )
                    return@launch
                }
                // A-P6-03: the shop's parts, read by the one function that tells a missing key from a part that is off; the chips exist only for `purposes` read as enabled, and the log says what was read
                val partsRead = session.protocolPartsRead()
                logProtocolPartsRead(partsRead)
                _state.value = State(
                    phase = Phase.WAITING,
                    sessionId = sessionId,
                    code = code,
                    expiresInMinutes = session.expiresInMinutes,
                    purposes = purposeUiFor(partsRead)
                )
                attachListener(sessionId)
            } catch (t: Throwable) {
                // ملحق خامس بالقرار ١٠٤ البند ٣ — see createSessionErrorMessage's
                // own doc (above this class) for the full rationale. This
                // banner used to show "[create] ${t.message}" verbatim.
                _state.value = State(
                    phase = Phase.ERROR,
                    error = createSessionErrorMessage(context, t),
                    retryable = !createRefusalIsNamed(t)
                )
            }
        }
    }

    /**
     * The owner's «إلغاء الجلسة» (batch 2, item 2): cancels the session THIS screen shows - the one named by [State.sessionId] - with
     * `cancelled_by_owner`, and touches no other (user decision 2026-10-15: two live sessions for one owner stay allowed; the
     * «+ كود جديد» button is unchanged and cancels nothing). Offered, and accepted, only on a session that is `pending` ([Phase.WAITING]) or
     * `scanning` ([Phase.SCANNING]); a tap while a cancel is in flight does nothing. On success the screen shows the cancelled state at once (the
     * listener will say the same); on a refusal [State.cancel] carries the text, and the session stays as it was.
     */
    fun cancelSession() {
        val before = _state.value
        val sessionId = before.sessionId?.takeIf { it.isNotBlank() } ?: return
        if (before.phase != Phase.WAITING && before.phase != Phase.SCANNING) return
        if (before.cancel is CancelUi.InFlight) return
        _state.value = before.copy(cancel = CancelUi.InFlight)
        viewModelScope.launch {
            try {
                val outcome = canceller.cancel(CancelRole.OWNER, sessionId, CancelReason.CANCELLED_BY_OWNER)
                // follow the session this cancel NAMED: the owner may have made another code meanwhile - that screen is not this session's
                _state.update {
                    if (it.sessionId == sessionId) {
                        it.copy(phase = Phase.CANCELLED, cancel = CancelUi.Idle, cancelReason = ownerCancelReasonAfterOwnCancel(it.cancelReason, outcome))
                    } else it
                }
                _state.value.takeIf { it.sessionId == sessionId }
                    ?.let { noteOwnerCancelledShown(sessionId, OwnerCancelledSource.OWN_CANCEL, it.cancelReason) }
            } catch (ce: kotlinx.coroutines.CancellationException) {
                throw ce
            } catch (t: Throwable) {
                val message = sessionErrorMessage(context, SessionCall.CANCEL_OWNER, t)
                _state.update { if (it.sessionId == sessionId) it.copy(cancel = CancelUi.Failed(message)) else it }
            }
        }
    }

    /** The sessions whose first report-open this screen already told the server about and got an answer to (main thread only), so a second tap on the same session's «عرض التقرير» does not ask again. */
    private val reportOpenAnswered = HashSet<String>()

    /**
     * The owner tapped «عرض التقرير» of the completed session: the report is about to be shown for the first time on this phone (A-P6-08, the form's blindness - the server compares a technician's form
     * with the first time EITHER device shows the report; see [ReportFirstOpenedRecorder]). The owner may call it because the session is his (the server accepts the owner and the joined device).
     * It asks the server once per session per screen, without waiting: nothing the screen shows depends on it, and a refusal or a failure is only logged (`report-first-opened`) - the next tap asks again.
     * Nothing is sent while the form's texts are not approved (the gate is in [FirebaseAnalysisService]).
     */
    fun onReportOpened() {
        val s = _state.value
        val sessionId = s.sessionId?.takeIf { it.isNotBlank() } ?: return
        if (s.phase != Phase.COMPLETED || sessionId in reportOpenAnswered) return
        viewModelScope.launch {
            when (ReportFirstOpenedRecorder(analysisService).record(sessionId, ReportFirstOpenedRecorder.Source.OWNER)) {
                ReportFirstOpenedRecorder.Outcome.Recorded, ReportFirstOpenedRecorder.Outcome.AlreadyRecorded -> reportOpenAnswered.add(sessionId)
                else -> Unit
            }
        }
    }

    /**
     * A tap on a purpose CHIP (A-P6-03; order of 2026-10-22). «عادي» is sent at once ([selectPurpose], with no type). A maintenance chip sends NOTHING: it opens its list of the five fault types (the chip is
     * then the one awaiting its type) and the purpose goes to the server only when a type is tapped ([onDefectType]) - there is no path that sends «قبل الصيانة» or «بعد الصيانة» without a type and no default
     * type. The same named reasons as a send ignore a tap that cannot count (no offered chips, no session, not pending, locked, a call out).
     */
    fun onPurposeChip(choice: ScanPurposeChoice) {
        val before = _state.value
        val sessionId = before.sessionId?.takeIf { it.isNotBlank() }
        when (val tap = purposeChipTapDecision(before.purposes, before.phase, hasSession = sessionId != null, choice)) {
            is PurposeChipTap.Ignore -> logPurposeIgnored(tap.reason)
            is PurposeChipTap.OpenTypes -> {
                val offered = before.purposes as? PurposeUi.Offered ?: return
                _state.value = before.copy(purposes = offered.typeRequested(tap.choice))
            }
            PurposeChipTap.SendNormal -> selectPurpose(choice, null)
        }
    }

    /**
     * A tap on one of the five fault types (order of 2026-10-22): sent as the purpose of the chip whose list of types is on the screen - the chip awaiting its type, or the chip the server already accepted (order of
     * 2026-10-28: a type chosen by mistake is changed with one tap while the session is pending; the server takes the change, `setSessionPurpose` being accepted as long as the session is `pending`) - with that type.
     * A type tapped while no list is on the screen is ignored with a named reason and nothing leaves the phone.
     */
    fun onDefectType(type: DefectType) {
        val before = _state.value
        val sessionId = before.sessionId?.takeIf { it.isNotBlank() }
        when (val tap = purposeTypeTapDecision(before.purposes, before.phase, hasSession = sessionId != null, type)) {
            is PurposeTypeTap.Ignore -> logPurposeIgnored(tap.reason)
            is PurposeTypeTap.Send -> selectPurpose(tap.selection.choice, tap.selection.defectType)
        }
    }

    /**
     * A tap on a purpose chip (A-P6-03). Sent to the server - even for the chip that is already highlighted, because the server can tell a chosen «normal» from the default one only if
     * the choice reaches it (rule 22; `purposeSource` is the server's, rule 20) - when the shop has the chips, the screen holds a session, the session is still pending (this screen is WAITING),
     * no refusal said it is not, and no other call is out; otherwise the tap is ignored with a named reason in the log ([purposeTapDecision]). The state moves only on the server's answer: an
     * accepted call confirms [choice], a refused or failed one leaves the confirmed purpose as it was and records how it ended (classified by `details.reasonCode`, rule 21).
     * The answer is applied to the session this call NAMED: the owner may have made another code meanwhile.
     */
    fun selectPurpose(choice: ScanPurposeChoice, defectType: DefectType?) {
        val before = _state.value
        val sessionId = before.sessionId?.takeIf { it.isNotBlank() }
        val tap = purposeTapDecision(before.purposes, before.phase, hasSession = sessionId != null)
        if (tap is PurposeTap.Ignore) {
            logPurposeIgnored(tap.reason)
            return
        }
        // contract A2 addendum of 2026-10-22: a maintenance purpose carries its fault type and «normal» carries none - any other pair is not sent (and the service refuses it too)
        val selection = when (val built = PurposeSelection.of(choice, defectType)) {
            is PurposeSelectionBuild.Built -> built.selection
            is PurposeSelectionBuild.Rejected -> {
                logPurposeIgnored(built.reason.token)
                return
            }
        }
        val offered = before.purposes as? PurposeUi.Offered ?: return
        if (sessionId == null) return
        // Q31: the server refuses a maintenance purpose for a shop without both parts (PURPOSE_PART_NOT_ENABLED); the app does not send it
        if (selection.choice.needsDefectType && !offered.maintenanceOffered) {
            logPurposeIgnored("maintenance_not_offered")
            return
        }
        _state.value = before.copy(purposes = offered.started(selection))
        viewModelScope.launch {
            try {
                val answer = analysisService.setSessionPurpose(sessionId, selection.choice, selection.defectType)
                logPurposeAccepted(sessionId, selection, answer)
                _state.update { s ->
                    val current = s.purposes as? PurposeUi.Offered
                    if (s.sessionId == sessionId && current != null) s.copy(purposes = current.accepted(selection)) else s
                }
                afterPurposeAccepted(sessionId, selection)
            } catch (ce: kotlinx.coroutines.CancellationException) {
                throw ce
            } catch (t: Throwable) {
                logPurposeEnded(sessionId, selection, t)
                _state.update { s ->
                    val current = s.purposes as? PurposeUi.Offered
                    if (s.sessionId == sessionId && current != null) s.copy(purposes = current.failed(t)) else s
                }
            }
        }
    }

    /**
     * The server accepted [selection] for [sessionId] (order of 2026-10-22): a `maintenance_after` purpose asks for the list of open «before» scans of this phone's fault type - every acceptance asks again, the
     * server having cleared the earlier choice with the purpose - and any other purpose takes the list away. What decides is the purpose the SERVER accepted, not the chip that was tapped.
     */
    private fun afterPurposeAccepted(sessionId: String, selection: PurposeSelection) {
        if (_state.value.sessionId != sessionId) return
        if (selection is PurposeSelection.Maintenance && selection.choice == ScanPurposeChoice.MAINTENANCE_AFTER) loadPairs(sessionId) else resetPairs(sessionId)
    }

    private fun resetPairs(sessionId: String) {
        pairsGeneration++
        _state.update { if (it.sessionId == sessionId && it.pairs != PairListUi.NotShown) it.copy(pairs = PairListUi.NotShown) else it }
    }

    private fun loadPairs(sessionId: String) {
        val generation = ++pairsGeneration
        _state.update { if (it.sessionId == sessionId) it.copy(pairs = PairListUi.Loading) else it }
        viewModelScope.launch {
            val outcome = pairClient.listOpen(sessionId)
            _state.update { s -> if (s.sessionId == sessionId && generation == pairsGeneration) s.copy(pairs = pairListFor(outcome)) else s }
        }
    }

    /**
     * A tap on a candidate «before» scan of the pair list (order of 2026-10-22): selects it when the list is on the screen and the id is one of the listed, and no confirmation is out; otherwise it is ignored
     * with a named reason in the log. Nothing is sent: the choice is the technician's until «تأكيد».
     */
    fun onPairCandidate(beforeScanId: String) {
        val before = _state.value
        val sessionId = before.sessionId?.takeIf { it.isNotBlank() }
        when (val tap = pairSelectDecision(before.pairs, before.phase, hasSession = sessionId != null, beforeScanId)) {
            is PairSelectTap.Select -> _state.value = before.copy(pairs = before.pairs.selected(tap.beforeScanId))
            is PairSelectTap.Ignore -> logPairTapIgnored(sessionId, tap.reason)
        }
    }

    /**
     * A tap on «تأكيد» (order of 2026-10-22): confirms the selected candidate for THIS session with the server. The state moves only on the server's answer: a confirmation makes the list linked, a refusal or a
     * failure keeps the choice and shows its text (classified by `details.reasonCode`, rule 21). The answer is applied only to the asking it belongs to: a purpose changed meanwhile drops it.
     */
    fun onPairConfirm() {
        val before = _state.value
        val sessionId = before.sessionId?.takeIf { it.isNotBlank() }
        val beforeScanId = when (val tap = pairConfirmDecision(before.pairs, before.phase, hasSession = sessionId != null)) {
            is PairConfirmTap.Confirm -> tap.beforeScanId
            is PairConfirmTap.Ignore -> {
                logPairTapIgnored(sessionId, tap.reason)
                return
            }
        }
        if (sessionId == null) return
        val generation = pairsGeneration
        _state.value = before.copy(pairs = before.pairs.confirmStarted(beforeScanId))
        viewModelScope.launch {
            val outcome = pairClient.confirm(sessionId, beforeScanId)
            _state.update { s ->
                if (s.sessionId != sessionId || generation != pairsGeneration) s
                else when (outcome) {
                    PairLinkClient.ConfirmOutcome.Confirmed -> s.copy(pairs = s.pairs.confirmed(beforeScanId))
                    PairLinkClient.ConfirmOutcome.GuardClosed -> s.copy(pairs = s.pairs.confirmNotMade())
                    else -> pairConfirmFailureFor(outcome)?.let { s.copy(pairs = s.pairs.confirmFailed(it)) } ?: s
                }
            }
        }
    }

    private fun attachListener(sessionId: String) {
        // Detach any prior listener (e.g. after a retry from ERROR) before
        // wiring up a new one so we don't leak a callback on the old doc.
        listener?.remove()
        val authUid = Firebase.auth.currentUser?.uid
        Log.d(
            "NeuralCheck",
            "Attaching session listener: path=${SessionDocWatcher.COLLECTION}/${LogRedaction.hashId(sessionId)} (authUid=$authUid)"
        )
        listener = sessionWatcher.watch(
            sessionId = sessionId,
            onSnapshot = { snapshot -> applySnapshot(sessionId, snapshot) },
            onError = { err ->
                // FirebaseFirestoreException.code is the canonical signal —
                // PERMISSION_DENIED here means a security rule rejected the
                // read (wrong collection name, wrong ownerId match, or the
                // owner uid not matching request.auth.uid). Every code this
                // listener can actually receive (PERMISSION_DENIED,
                // UNAVAILABLE, CANCELLED, DEADLINE_EXCEEDED, etc. — a
                // Firestore LISTEN-level failure) is about database access,
                // never about the scanSessions document's own `status`
                // field ("session expired" is a business-logic state read
                // from a successfully-delivered snapshot, not a listen
                // error) or about code validity (that's the join screen's
                // own concern, a different Cloud Function entirely) — see
                // sessionListenerErrorMessage's own doc.
                _state.update {
                    if (it.sessionId == sessionId) {
                        it.copy(
                            phase = Phase.ERROR,
                            error = sessionListenerErrorMessage(context, SessionDocWatcher.COLLECTION, sessionId, err)
                        )
                    } else it
                }
            }
        )
    }

    /**
     * One snapshot of the session this screen shows. A status that is one of the five the server writes moves the screen to what [ownerPhaseFor] says (and
     * remembers the `scanId` the server wrote on completion). A missing document, a status that is NOT one of the five, or one that is not a text, moves nothing:
     * the screen keeps the phase it was in, and the watcher has printed which of the three it was (`session-watch-event`). It is never read as one of the five (rules 22, 25).
     */
    private fun applySnapshot(sessionId: String, snapshot: SessionDocWatcher.Snapshot) {
        val read = snapshot.status as? SessionStatus.Read.Known ?: return
        val phase = ownerPhaseFor(read.status)
        // the reason matters only for a cancelled session; the text it decides is printed once, here (batch 3, item 2)
        val cancelReason = if (read.status == SessionStatus.CANCELLED) snapshot.cancelReason else CancelReason.Read.Absent
        if (read.status == SessionStatus.CANCELLED) noteOwnerCancelledShown(sessionId, OwnerCancelledSource.SNAPSHOT, cancelReason)
        _state.update {
            if (it.sessionId == sessionId) {
                it.copy(phase = phase, cancelReason = cancelReason, completedScanId = snapshot.scanId ?: it.completedScanId)
            } else it
        }
        askRepeatOffer(sessionId)
    }

    /**
     * A-P6-07: the session this screen shows is completed and the server named its scan - ask once, for that pair, whether the scanned phone was chosen for a cold repeat (`getRepeatScanOffer`; the owner's
     * fetch is the one that moves the offer from `SELECTED` to `PROMPT_DELIVERED`, PLAN-06:662). Nothing happens - no state change, no call, no line - while the repeat screens' texts are not approved (the gate
     * is in [FirebaseAnalysisService]; this reads it only to skip the work). The answer is applied to the session this call NAMED: the owner may have made another code meanwhile.
     */
    private fun askRepeatOffer(sessionId: String) {
        val s = _state.value
        val scanId = s.completedScanId?.takeIf { it.isNotBlank() } ?: return
        if (s.sessionId != sessionId || s.phase != Phase.COMPLETED) return
        if (!analysisService.repeatCallsOpen()) return
        if (repeatAskedFor == sessionId to scanId) return
        repeatAskedFor = sessionId to scanId
        _state.update { if (it.sessionId == sessionId) it.copy(repeatNotice = RepeatNoticeUi.Asking) else it }
        viewModelScope.launch {
            val ui = repeatNoticeFor(repeatClient.offer(sessionId, scanId, RepeatScanClient.Role.OWNER))
            _state.update { if (it.sessionId == sessionId && it.completedScanId == scanId) it.copy(repeatNotice = ui) else it }
        }
    }

    /**
     * «ليس الآن» (PLAN-06:956): hides the notice on this screen. Nothing is sent - the plan names no call that records it (the server's `DECLINED` has no writer: A4, «معلّق»), and the server keeps the offer
     * startable ("ليست نهائية"). A tap when there is no notice does nothing.
     */
    fun dismissRepeatNotice() {
        _state.update { s -> (s.repeatNotice as? RepeatNoticeUi.Notice)?.let { s.copy(repeatNotice = it.copy(dismissed = true)) } ?: s }
    }

    override fun onCleared() {
        super.onCleared()
        listener?.remove()
    }
}

// ============================================================================
// JoinSessionViewModel — customer-device side. Submits the owner's code to
// the backend; on success stashes the returned sessionId in
// ScanSessionHolder so the ensuing scan + analyzeDevice call get tagged
// with it instead of relying on a (likely absent) Firebase Auth user.
// ============================================================================

/*
 * joinSessionErrorMessage and its three-code «show the server's own message» branch were REMOVED in batch 2 (order 2026-10-15, item 2). What
 * the customer reads when a join is refused is decided by SessionMessages.kt's sessionErrorMessage(context, SessionCall.JOIN_TYPED, t):
 * by the server's details.reasonCode (never the Functions code or the message - rule 21), from this app's own approved texts. The
 * tests that pinned the old behaviour (JoinSessionErrorMessageTest: NOT_FOUND by Functions code, the server message shown verbatim for
 * INVALID_ARGUMENT / FAILED_PRECONDITION / DEADLINE_EXCEEDED, the IOException gate) were replaced, not kept (rule 23).
 */

// evaluationSubmissionErrorMessage moved to
// net.neuralcheck.domain.evaluation.EvaluationSubmitter.kt (order 2026-09-30،
// «ملحق — النصّ، ورسالة التقييم، والبروتوكول»): now called directly by
// EvaluationSubmitter.submitOnScanComplete (domain layer) — importing a
// presentation-layer function from there would be a backwards dependency.
// See that file's own doc for the full history and exact citations.

@HiltViewModel
class JoinSessionViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val analysisService: FirebaseAnalysisService,
    // Order 2026-10-10: the join carries the device's own (anonymous) uid —
    // see joinScanSessionWithDeviceIdentity.
    private val authManager: AuthManager,
    private val sessionHolder: net.neuralcheck.data.remote.ScanSessionHolder
) : ViewModel() {

    /**
     * [consentPart]: what the join's answer said about the shop's consent part (ق15) - `null` only while there is no join answer yet (a loading or failed state); set together with [sessionId] by
     * the one assignment that ends a successful join, so the screen routes by it (`afterJoinRoute`) and an absent key is [ConsentPartRead.Absent], never a default.
     */
    data class State(
        val isLoading: Boolean = false,
        val sessionId: String? = null,
        val error: String? = null,
        val consentPart: net.neuralcheck.domain.consent.ConsentPartRead? = null
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    fun join(rawCode: String) {
        val code = rawCode.trim().uppercase()
        if (code.isBlank()) return
        _state.value = State(isLoading = true)
        viewModelScope.launch {
            try {
                Log.d("NeuralCheck", "Joining session with code: ${LogRedaction.hashId(code)}")
                val joined = joinScanSessionWithDeviceIdentity(authManager, analysisService, code)
                // feat/technician-evaluation: keep the whole join context
                // (ownerId + evaluationEnabled), not just the id string —
                // the evaluation card's visibility gate reads getInfo().
                sessionHolder.setFromJoin(joined.answer)
                _state.value = State(isLoading = false, sessionId = joined.sessionId, consentPart = joined.answer.consentPart())
            } catch (t: Throwable) {
                // Batch 2, item 2: the text is decided by the server's details.reasonCode - see SessionMessages.kt.
                _state.value = State(isLoading = false, error = sessionErrorMessage(context, SessionCall.JOIN_TYPED, t))
            }
        }
    }

    /** Clears the error banner so the user can retry without leaving the screen. */
    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }
}
