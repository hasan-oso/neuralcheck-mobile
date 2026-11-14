package net.neuralcheck.domain.evaluation

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import com.google.firebase.functions.FirebaseFunctionsException
import net.neuralcheck.R
import net.neuralcheck.data.local.db.dao.SessionEvaluationDao
import net.neuralcheck.data.remote.FirebaseAnalysisService
import net.neuralcheck.domain.scoring.networkErrorMessageOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Order 2026-09-30 («أمر — التقييم (ب)، ومهلة الجلسة، والبنود المعلّقة»،
 * البند ١): the two `submitEvaluation` rejections confirmed to be
 * TERMINAL — matched by CODE and TEXT together (القاعدة ٢١), never code
 * alone. An IOException-typed [Throwable.cause], or a message that doesn't
 * match the known server copy, means this is NOT reliably a genuine server
 * rejection (a local transport issue can coincidentally surface under the
 * same FirebaseFunctionsException code — the same guard
 * ViewModels.kt's joinSessionErrorMessage already applies to its own three
 * codes) — treated as TRANSIENT, retried, never dropped, in that case.
 *
 * `submitEvaluation.js:73-74` (neuralcheck-backend@c7446a4, relayed by the
 * coordinator, NOT verified from this repo — القاعدة ٢): PERMISSION_DENIED
 * "جلسة الفحص غير صالحة".
 * `submitEvaluation.js:76-80`: FAILED_PRECONDITION "الجلسة منتهية" — this
 * TEXT is what this function keys on, not the session-timeout DURATION,
 * which the coordinator says is changing server-side (15 minutes from
 * `createScanSession` → 60 minutes from join) without changing this
 * message — "ونصّ «الجلسة منتهية» يبقى رمزاً نهائياً" (coordinator's own
 * text).
 *
 * Deliberately STRICTER than [net.neuralcheck.presentation.viewmodel
 * .evaluationSubmissionErrorMessage]'s own code-based display logic: that
 * function only decides which TEXT to show (harmless if too lenient), this
 * one decides whether to PERMANENTLY stop retrying (harmful if too
 * lenient — an evaluation that could have succeeded later would be
 * silently dropped forever), so it additionally requires the exact
 * expected message text and rejects an IOException-typed cause.
 */
internal fun isFinalEvaluationRejection(t: Throwable): Boolean {
    if (t !is FirebaseFunctionsException) return false
    if (t.cause is java.io.IOException) return false
    val message = t.message?.trim().orEmpty()
    return (t.code == FirebaseFunctionsException.Code.PERMISSION_DENIED && message.contains("جلسة الفحص غير صالحة")) ||
        (t.code == FirebaseFunctionsException.Code.FAILED_PRECONDITION && message.contains("الجلسة منتهية"))
}

/**
 * Order 2026-09-30 («أمر — التقييم (ب)...» ثم «ملحق — النصّ، ورسالة
 * التقييم، والبروتوكول»): message for a rejected/failed submitEvaluation
 * call. Moved here from ViewModels.kt (presentation layer) once it gained a
 * real caller ([EvaluationSubmitter.submitOnScanComplete], domain layer) —
 * importing a presentation-layer function from the domain layer would be a
 * backwards dependency.
 *
 * Same pattern as ViewModels.kt's `joinSessionErrorMessage`: network
 * failures first (shared helper - [networkErrorMessageOrNull], the one reading of
 * «no connection»: the error text of the failure and of its cause chain, never for
 * a failure that carries a server reasonCode; batch 4, item 1 — an SDK transport
 * failure is a FirebaseFunctionsException("INTERNAL") whose CAUSE holds the text, and
 * this used to read the message only), then a curated FirebaseFunctionsException
 * code shows the backend's OWN message (trusted, curated server copy — not
 * raw diagnostic text, rule ٣٦, approved as-is: "نصّا الخادم للجلسة يُعرضان
 * كما هما"), everything else falls back to `eval_submit_failed_generic`
 * (also approved as-is).
 *
 * TWO rejection conditions are confirmed — relayed by the coordinator from
 * neuralcheck-backend@c7446a4 / @7e417a2, not read from that repo directly
 * (القاعدة ٢، هذا الوكيل محصور بـNeuralChek):
 *   `submitEvaluation.js:73-74`: PERMISSION_DENIED "جلسة الفحص غير صالحة".
 *   `submitEvaluation.js:76-80`: FAILED_PRECONDITION "الجلسة منتهية" — the
 *   session-timeout DURATION this checks changed server-side (creation-based
 *   15 minutes → a single 60-minute window from join, covering join +
 *   analysis + evaluation together, per @7e417a2) but this TEXT is
 *   unchanged, confirmed by the coordinator.
 *
 * This code-only check is deliberately more LENIENT than
 * [isFinalEvaluationRejection] (which also requires the exact message text
 * and rejects an IOException-typed cause) — showing an unexpected message
 * for one of these two codes is harmless, so this function does not
 * duplicate that stricter gate; only the "permanently stop retrying"
 * decision needs it.
 */
internal fun evaluationSubmissionErrorMessage(context: Context, t: Throwable): String {
    networkErrorMessageOrNull(context, t)?.let { return it }
    val boundedMessage = t.message?.take(200)
    Log.e("NeuralCheck", "submitEvaluation failed: ${t.javaClass.simpleName}: $boundedMessage")
    if (t is FirebaseFunctionsException && t.code in EVALUATION_SUBMIT_SERVER_TEXT_CODES) {
        return t.message?.takeIf { it.isNotBlank() } ?: context.getString(R.string.eval_submit_failed_generic)
    }
    return context.getString(R.string.eval_submit_failed_generic)
}

/**
 * The two `submitEvaluation` codes confirmed to carry a curated, user-safe
 * server message — see [evaluationSubmissionErrorMessage]'s doc for the
 * exact file:line citations. Not necessarily exhaustive.
 */
private val EVALUATION_SUBMIT_SERVER_TEXT_CODES = setOf(
    FirebaseFunctionsException.Code.PERMISSION_DENIED,
    FirebaseFunctionsException.Code.FAILED_PRECONDITION
)

/**
 * Order 2026-09-30 («ملحق — النصّ، ورسالة التقييم، والبروتوكول»): carries
 * the confirm-at-scan-completion submission's FAILURE outcome from
 * [EvaluationSubmitter] (via [EvaluationSubmitter.submitOnScanComplete],
 * called from ActiveScanViewModel) to ManualTestsViewModel — the next
 * screen on the customer's device. The two are different ViewModels scoped
 * to different NavBackStackEntry instances with no other shared state, and
 * the submission is fire-and-forget (does not delay navigation —
 * ViewModels.kt's `Completed` branch), so by the time ManualTestsScreen
 * composes, the outcome may not exist yet.
 *
 * NOT Room, and no new Room schema change here — this is transient,
 * in-memory, process-lifetime-only ROUTING information, not a durable
 * record. The durable record (SessionEvaluationEntity.submitted /
 * .finalFailure) is already written by [EvaluationSubmitter.submitOne]
 * regardless of whether anything is listening here.
 *
 * [MutableSharedFlow] with `replay = 0` — the EXACT pattern
 * [net.neuralcheck.util.InAppNotifier] already uses for the same reason
 * (see that class's own doc): an event only a LIVE subscriber sees. A
 * failure that resolves before ManualTestsScreen starts collecting (a
 * genuine race — unlikely, since collection starts at ViewModel
 * construction, well before any network round trip completes, but
 * possible — the same accepted risk InAppNotifier's own doc names for a
 * "newly composed SnackbarHost") or AFTER the user has already left that
 * screen (the collecting coroutine lives in ManualTestsViewModel's
 * viewModelScope, cancelled on onCleared()) is simply never delivered — by
 * design, per this order's own text ("وإن وصلت النتيجة بعد مغادرة الشاشة:
 * لا شيء يُعرض... ولا تنقل السطر إلى شاشة أخرى"). Success and "no
 * evaluation existed" publish NOTHING — there is no success text to show
 * (order: "ولا نصّ «نجح» جديد إلا بقرار").
 */
@Singleton
class EvaluationSubmissionStatusHolder @Inject constructor() {

    data class ScanFailure(val scanId: String, val message: String)

    private val _failures = MutableSharedFlow<ScanFailure>(
        replay = 0,
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val failures: SharedFlow<ScanFailure> = _failures.asSharedFlow()

    fun publishFailure(scanId: String, message: String) {
        _failures.tryEmit(ScanFailure(scanId, message))
    }
}

/**
 * feat/technician-evaluation — sends persisted technician evaluations to
 * the submitEvaluation callable and marks them submitted.
 *
 * Entry points:
 *  - [submitOnScanComplete]: the PRIMARY trigger, called from
 *    ActiveScanViewModel's `Completed` transition (order 2026-09-30، «أمر
 *    — التقييم (ب)...» — the moment the scan finishes, before
 *    analyzeDevice, while the session is still "scanning"; see that
 *    class's own doc). Launches on THIS SINGLETON's own [scope] — NOT the
 *    caller's viewModelScope — because ActiveScanScreen pops active_scan
 *    off the back stack (and therefore clears ActiveScanViewModel,
 *    cancelling its viewModelScope) moments after this fires; a
 *    submission launched on viewModelScope would risk being cancelled
 *    mid-flight. (This was a defect in this order's own previous round —
 *    corrected here; flagged in that round's follow-up report as a
 *    self-caught fix, not something asked for.) On failure, publishes to
 *    [EvaluationSubmissionStatusHolder] (order 2026-09-30، «ملحق — النصّ،
 *    ورسالة التقييم، والبروتوكول») so ManualTestsScreen — the next screen
 *    on the customer's device — can show a line; see that holder's own doc
 *    for exactly when it is and isn't delivered.
 *  - [submitAsync]: fire-and-forget, called from AiScoringEngine right
 *    after a successful analyzeDevice. Now a FALLBACK ONLY (see
 *    AiScoringEngine.kt's own comment at that call site) — does NOT
 *    publish to the status holder (ManualTestsScreen has long since been
 *    left by the time analysis runs).
 *  - [submitNow]: the low-level awaitable primitive both of the above
 *    could be built from; kept as a separate tested unit
 *    (EvaluationSubmitterOrderingTest) — propagates exceptions instead of
 *    swallowing them, unlike [submitAsync].
 *  - [submitPendingBlocking]: suspend variant consumed by the sync path
 *    (FirebaseSyncService via SyncScheduler/WorkManager) so a network
 *    outage never loses a label — the row stays in Room until it lands.
 *    Does NOT publish to the status holder either (runs hours later, no
 *    ManualTestsScreen anywhere nearby).
 *  - [discard]: order 2026-09-30 البند ١ — drops a CANCELLED scan's
 *    evaluation entirely (ActiveScanViewModel.stopScan()). Never sent, not
 *    even by the retry path, because the row no longer exists.
 *
 * [submitOne] classifies every failure via [isFinalEvaluationRejection]
 * (rule ٢١: code AND text together) and marks
 * [net.neuralcheck.data.local.db.entity.SessionEvaluationEntity.finalFailure]
 * on a CONFIRMED terminal rejection (session invalid/expired) so
 * [getPending]-driven retries never re-attempt it; every other failure
 * (network, or an uncertain/unrecognised code) leaves the row eligible for
 * retry — unchanged, and per this order's own text ("والإعادة العابرة عبر
 * عامل المزامنة تبقى كما صمّمتها").
 *
 * sessionId comes from the ROW (snapshotted at first answer), never from
 * ScanSessionHolder — the holder is already cleared by the time retries
 * run. A server response with `pending: true` is success by contract.
 */
@Singleton
class EvaluationSubmitter @Inject constructor(
    private val dao: SessionEvaluationDao,
    private val firebaseService: FirebaseAnalysisService,
    private val statusHolder: EvaluationSubmissionStatusHolder
) {
    private companion object {
        const val TAG = "NeuralCheck"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Order 2026-09-30 («أمر — التقييم (ب)...» ثم «ملحق — النصّ، ورسالة
     * التقييم، والبروتوكول»): see the class doc above for the full
     * rationale. [context] is only used to resolve
     * [evaluationSubmissionErrorMessage]'s string resources on failure —
     * no UI/ViewModel object is held past this call.
     */
    fun submitOnScanComplete(scanId: String, context: Context) {
        scope.launch {
            try {
                submitOne(scanId)
            } catch (t: Throwable) {
                statusHolder.publishFailure(scanId, evaluationSubmissionErrorMessage(context, t))
            }
        }
    }

    /** Fire-and-forget submission for [scanId]; safe to call when no row
     *  exists (no-op) or when already submitted (no-op). */
    fun submitAsync(scanId: String) {
        scope.launch { runCatching { submitOne(scanId) } }
    }

    /**
     * Order 2026-09-30 («أمر — التقييم (ب)، ومهلة الجلسة، والبنود
     * المعلّقة»، البند ١): the PRIMARY submission trigger, called from
     * ActiveScanViewModel's `Completed` transition — see that class for
     * exactly where and why. An AWAITABLE entry point (unlike
     * [submitAsync]): propagates any exception from [submitOne] (a
     * transient failure, or a submitEvaluation rejection — see
     * [isFinalEvaluationRejection] and
     * [net.neuralcheck.presentation.viewmodel.evaluationSubmissionErrorMessage])
     * so the caller can classify/log it instead of the failure silently
     * vanishing into a background coroutine — the caller still launches
     * this on its OWN coroutine rather than awaiting it inline, so a
     * network stall here never delays the caller's own next step.
     *
     * Same underlying [submitOne] as every other entry point, so it is
     * naturally idempotent with [submitAsync]/[submitPendingBlocking] — a
     * row marked `submitted = true` (or `finalFailure = true`) here makes
     * AiScoringEngine.kt's later `submitAsync` fallback call a no-op, never
     * a duplicate send and never a retry of a confirmed-terminal rejection.
     */
    suspend fun submitNow(scanId: String): Boolean = submitOne(scanId)

    /**
     * Order 2026-09-30 («أمر — التقييم (ب)...»، البند ١): the technician
     * cancelled the scan (ActiveScanScreen's stop-confirm dialog →
     * ActiveScanViewModel.stopScan()) — the evaluation is dropped from the
     * device entirely, never sent, per the coordinator's own text
     * ("يُسقَط تقييمه من الجهاز، ولا يُرسَل"). A no-op when no row exists
     * yet (the technician cancelled before the first tap).
     */
    suspend fun discard(scanId: String) {
        dao.deleteByScanId(scanId)
    }

    /** Sync-path retry: attempts every unsent row; returns the number
     *  successfully submitted. Failures keep rows for the next cycle. */
    suspend fun submitPendingBlocking(): Int {
        var sent = 0
        val pending = runCatching { dao.getPending() }.getOrElse { return 0 }
        for (row in pending) {
            runCatching { submitOne(row.scanId) }
                .onSuccess { if (it) sent++ }
                .onFailure { Log.w(TAG, "evaluation retry failed for ${row.scanId}: ${it.message}") }
        }
        return sent
    }

    /**
     * @return true when a submission actually happened and succeeded.
     * @throws Throwable whatever [FirebaseAnalysisService.submitEvaluation]
     *   throws, AFTER marking [SessionEvaluationDao.markFinalFailure] if
     *   [isFinalEvaluationRejection] confirms it's terminal (order
     *   2026-09-30، البند ١) — every caller ([submitAsync],
     *   [submitPendingBlocking] via `runCatching`, [submitNow] via
     *   propagation) still sees the exception; only the row's persisted
     *   state changes.
     */
    private suspend fun submitOne(scanId: String): Boolean {
        val row = dao.getByScanId(scanId) ?: return false
        // finalFailure: a confirmed-terminal rejection from a PRIOR attempt
        // — never retried (order 2026-09-30، البند ١). submitted: already
        // succeeded — never re-sent (unchanged, pre-existing guard).
        if (row.submitted || row.finalFailure) return false
        val payload = EvaluationPayloadBuilder.build(row) ?: run {
            // Judgement-free row (shouldn't exist — rows are written on
            // answers only) — mark submitted so it never loops in retry.
            dao.markSubmitted(scanId)
            return false
        }
        try {
            firebaseService.submitEvaluation(payload)
        } catch (t: Throwable) {
            if (isFinalEvaluationRejection(t)) {
                dao.markFinalFailure(scanId)
                Log.w(TAG, "technician evaluation PERMANENTLY rejected for " +
                    "scanId=$scanId (session invalid/expired) — will not retry")
            }
            throw t
        }
        dao.markSubmitted(scanId)
        Log.i(TAG, "technician evaluation submitted for scanId=$scanId " +
            "(keys=${payload.keys})")
        return true
    }
}
