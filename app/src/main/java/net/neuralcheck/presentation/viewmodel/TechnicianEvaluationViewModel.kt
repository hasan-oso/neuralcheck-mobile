package net.neuralcheck.presentation.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.neuralcheck.data.local.db.dao.SessionEvaluationDao
import net.neuralcheck.data.local.db.entity.SessionEvaluationEntity
import net.neuralcheck.data.remote.ScanSessionHolder
import net.neuralcheck.data.remote.ScanSessionRecords
import net.neuralcheck.domain.evaluation.EvaluationAnswers
import net.neuralcheck.util.EvalCardLog
import javax.inject.Inject

/**
 * feat/technician-evaluation — state for the blind technician questions
 * card shown on the SCANNED device while the scan runs.
 *
 * Measurement-safety contract (non-negotiable, diagnosis §8):
 *  - The ONLY recomposition source is a technician tap: no timers, no
 *    infinite transitions, and this ViewModel observes NO measurement
 *    flow — it never touches scanState, the sampler, or any telemetry.
 *    (That also enforces the product rule: nothing of our verdict can
 *    leak into the questions.)
 *  - Every tap = one in-memory state update (main thread, O(1)) + one
 *    Room upsert launched on Dispatchers.IO. Nothing else runs on main.
 *
 * Persist-on-tap is structural: on completion ActiveScanScreen
 * auto-navigates to manual_tests and pops active_scan off the back stack,
 * so an unsaved answer at that instant is gone.
 *
 * Session snapshot: [sessionSnapshot] is read from [ScanSessionHolder]
 * ONCE at construction (card first appears) and persisted into the row
 * with the first answer — the holder is cleared after the first
 * analyzeDevice and on sign-out, so it must never be consulted later.
 *
 * Order 2026-10-14 («Record the session at scan start»): [sessionSnapshot] now decides only whether the card is
 * SHOWN ([isEvaluationSession], the gate the card and the handoff line read). The session id the DRAFT carries
 * is the one the scan started in — [ScanSessionRecords.sessionFor], copied by StressTestService the moment the
 * scan's id was minted — so the draft and the scan's analysis name the same session whatever was joined since.
 */
@HiltViewModel
class TechnicianEvaluationViewModel @Inject constructor(
    private val dao: SessionEvaluationDao,
    sessionHolder: ScanSessionHolder,
    private val scanSessionRecords: ScanSessionRecords
) : ViewModel() {

    /** Immutable snapshot of the join context, taken exactly once. */
    private val sessionSnapshot: ScanSessionHolder.ScanSessionInfo? = sessionHolder.getInfo()

    /** Visibility gate: QR session AND owner entitled. Computed once —
     *  never re-evaluated, so it can't become a recomposition source.
     *
     *  `== true`, on purpose (order 2026-10-15, rule 22): a join answer that did not carry the flag
     *  (`evaluationEnabled == null`) does NOT show the card — that is THIS gate's decision about an
     *  absence, declared on `eval-vm-built` (`evaluationEnabled=absent`) and, at [bind], by name
     *  ([EvalCardLog.BindResult.FLAG_ABSENT]); it is not a `false` the server never said. */
    val isEvaluationSession: Boolean =
        sessionSnapshot != null && sessionSnapshot.evaluationEnabled == true

    init {
        // Order 2026-10-10 (permanent named log lines): what this view model froze, once, at construction.
        EvalCardLog.viewModelBuilt(
            snapshotPresent = sessionSnapshot != null,
            snapshotEvaluationEnabled = sessionSnapshot?.let { EvalCardLog.Flag.of(it.evaluationEnabled) },
            isEvaluationSession = isEvaluationSession,
            sessionId = sessionSnapshot?.sessionId
        )
    }

    /** Technician pressed "تخطّي" — hides the card for this scan. Already
     *  persisted answers stay saved and WILL be submitted (a voluntary
     *  70% evaluation beats none). */
    var dismissed by mutableStateOf(false)
        private set

    /** The current draft. Null until [bind] runs (scanId unknown). Only
     *  tap handlers (and one-shot bind/restore) write it. */
    var draft by mutableStateOf<SessionEvaluationEntity?>(null)
        private set

    private var bound = false

    /**
     * How [bind] builds a fresh draft (the production value calls [EvaluationAnswers.emptyDraft]). A seam for the test
     * of the failure path (order 2026-10-14): a factory that throws, in a test, is how `eval-bind-draft-failed` is
     * reached without a release build. The default is a lambda, not a function reference: a reference to a member of
     * the `EvaluationAnswers` object would initialise that object HERE, in this constructor, on the main thread —
     * the very failure the guard in [bind] is there to contain.
     */
    @androidx.annotation.VisibleForTesting
    internal var draftFactory: (scanId: String, sessionIdSnapshot: String, shownAtMs: Long) -> SessionEvaluationEntity =
        { scanId, sessionIdSnapshot, shownAtMs ->
            EvaluationAnswers.emptyDraft(scanId, sessionIdSnapshot, shownAtMs)
        }

    /**
     * One-shot: attach the card to [scanId] (available from the first
     * Running emission). Restores a previously persisted row after
     * process death — answers survive; shownAtMs keeps the ORIGINAL
     * first-shown timestamp so durationMs stays honest.
     *
     * Called from a LaunchedEffect keyed on the first non-blank scanId —
     * it fires once per scan, not per recomposition.
     */
    fun bind(scanId: String) {
        // Order 2026-10-10 (permanent named log lines): the refusal and its name are ONE decision —
        // the same conditions, in the same order, as before; the line says which one held.
        val refusal = when {
            bound -> EvalCardLog.BindResult.ALREADY_BOUND
            scanId.isBlank() -> EvalCardLog.BindResult.SCAN_ID_BLANK
            // an absent flag is refused by its own name, not as a "not an evaluation session" the server never said
            !isEvaluationSession ->
                if (sessionSnapshot != null && sessionSnapshot.evaluationEnabled == null) EvalCardLog.BindResult.FLAG_ABSENT
                else EvalCardLog.BindResult.NOT_EVALUATION_SESSION
            else -> null
        }
        if (refusal != null) {
            EvalCardLog.bindResult(refusal, scanId)
            return
        }
        bound = true
        // Order 2026-10-14 (user decision «Record the session at scan start»): "its analysis and its evaluation draft
        // use that one [the session the scan started in], never the session joined later." The draft's session is
        // the scan's own record, not the holder's current value and not this view model's construction-time
        // snapshot. No record = the scan started in no session (a personal scan: nothing to evaluate); a record
        // whose join answer said evaluationEnabled=false = not an evaluation session, and one whose join answer did not
        // carry the flag (order 2026-10-15) = refused as flag-absent. All three refuse, by name.
        val recorded = scanSessionRecords.sessionFor(scanId, ScanSessionRecords.Use.DRAFT)
        if (recorded == null) {
            EvalCardLog.bindResult(EvalCardLog.BindResult.NO_SESSION_ID, scanId)
            return
        }
        if (recorded.evaluationEnabled != true) {
            EvalCardLog.bindResult(
                if (recorded.evaluationEnabled == null) EvalCardLog.BindResult.FLAG_ABSENT
                else EvalCardLog.BindResult.NOT_EVALUATION_SESSION,
                scanId
            )
            return
        }
        val sessionId = recorded.sessionId
        EvalCardLog.bindResult(EvalCardLog.BindResult.STARTED, scanId)
        val shownAt = System.currentTimeMillis()
        viewModelScope.launch(Dispatchers.IO) {
            // Order 2026-10-14 (diagnostic build, two log lines around the Room read; no behaviour change): the start
            // line is the first statement of this coroutine, the done line follows the read on the path that did not
            // throw (a throw prints the read-failed line instead).
            EvalCardLog.bindReadStart(scanId)
            val read = runCatching { dao.getByScanId(scanId) }
            val readError = read.exceptionOrNull()
            if (readError != null) {
                EvalCardLog.draftReadFailed(readError, scanId)
            } else {
                EvalCardLog.bindReadDone(found = read.getOrNull() != null, scanId = scanId)
            }
            val existing = read.getOrNull()
            // Order 2026-10-14 (user decision «Root fix + guards»): "A failure while building the draft is caught and
            // logged as eval-bind-draft-failed error=<class> - no silent death; no new screen text." On 1.0.8-1.0.13
            // this statement threw (EvaluationAnswers failed to initialise in the release build — see its comment)
            // and nothing was printed. Throwable, not Exception: that failure is an ExceptionInInitializerError.
            // The card needs a draft to show, so on a failure it does not show — and says nothing new on screen.
            val built = runCatching {
                existing ?: draftFactory(scanId, sessionId, shownAt)
            }
            val buildError = built.exceptionOrNull()
            if (buildError != null) {
                EvalCardLog.draftFailed(buildError, scanId)
                return@launch
            }
            draft = built.getOrThrow()
            // The draft is what un-hides the card: this line means it is set.
            EvalCardLog.draftReady(
                if (existing != null) EvalCardLog.DraftState.RESTORED else EvalCardLog.DraftState.NEW,
                scanId
            )
        }
    }

    fun dismiss() {
        dismissed = true
        EvalCardLog.dismissed(draftReady = draft != null, sessionId = sessionSnapshot?.sessionId)
    }

    /**
     * Order 2026-10-10 (permanent named log lines): called once, from ActiveScanScreen's existing
     * scan-completed handler just before it navigates to manual_tests — what the card's three gates were when the
     * automated part ended. Reads state; changes nothing.
     */
    fun logHandoffToManualTests(scanId: String) {
        EvalCardLog.handoff(
            isEvaluationSession = isEvaluationSession,
            draftReady = draft != null,
            dismissed = dismissed,
            scanId = scanId
        )
    }

    fun onGradeTap(grade: String) = applyOnTap { e, now ->
        EvaluationAnswers.withGrade(e, grade, now)
    }

    fun onCategoryTap(category: String, rating: String) = applyOnTap { e, now ->
        EvaluationAnswers.withCategoryRating(e, category, rating, now)
    }

    fun onDefectTap(defect: String) = applyOnTap { e, now ->
        EvaluationAnswers.withDefectToggled(e, defect, now)
    }

    /** Tap pipeline: pure transform on main (O(1)) → immediate IO upsert.
     *  A judgement-free draft (everything deselected again) is not
     *  persisted as an empty row — but an existing row is rewritten so
     *  deselection survives too. */
    private inline fun applyOnTap(
        transform: (SessionEvaluationEntity, Long) -> SessionEvaluationEntity
    ) {
        val current = draft ?: return
        val updated = transform(current, System.currentTimeMillis())
        draft = updated
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                if (EvaluationAnswers.hasAnyJudgement(updated) ||
                    dao.getByScanId(updated.scanId) != null
                ) {
                    dao.upsert(updated)
                }
            }
        }
    }
}
