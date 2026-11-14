package net.neuralcheck.domain.scoring

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coarse progress for the post-scan analysis wait.
 *
 * The wait runs ~3 minutes. Before this, the report screen showed a
 * five-message carousel on a 3-second timer plus a pulse — motion, but
 * nothing tied to any real step, so it never told the user WHERE in the
 * wait they were. A field log captured 11 taps in 6 seconds against it.
 *
 * These are honest coarse stages, not a percentage: the app cannot know
 * how far along the model is, and inventing a bar that creeps to 90% and
 * stalls would trade one lie for a worse one. What the app does know is
 * which of its own steps it is on, and that is what this reports.
 */
enum class AnalysisStage {
    /** Nothing running (idle, or the report is already on screen). */
    IDLE,

    /** Reading the scan's time-series off disk. */
    READING_MEASUREMENTS,

    /** Assembling the payload (fingerprint + device key). */
    PREPARING_PAYLOAD,

    /** Checking whether a previous run already produced a verdict. */
    CHECKING_CACHE,

    /** In the callable: uploading, model, validator, Firestore write.
     *  The long one — the other four are seconds at most. */
    ANALYSING,

    /** Verdict received; mapping it into the report. */
    BUILDING_REPORT,

    /** Terminal: the report is ready to render. */
    DONE
}

/**
 * Holder the scoring engine writes and the report screen observes.
 *
 * A separate @Singleton rather than a parameter on
 * [ScoringEngine.calculateScore], because the interface is shared with
 * RuleBasedScoringEngine (datacollect), which finishes synchronously and
 * has nothing to report. This keeps the contract untouched and leaves the
 * rule-based path emitting nothing at all.
 *
 * Reports the app's OWN steps only. Nothing here changes the payload, the
 * verdict, or any measurement — it is observation of work already
 * happening.
 */
@Singleton
class AnalysisProgress @Inject constructor() {

    private val _stage = MutableStateFlow(AnalysisStage.IDLE)
    val stage: StateFlow<AnalysisStage> = _stage.asStateFlow()

    /** Wall-clock ms when the current run entered its first non-idle
     *  stage, or null when idle. Lets the UI show elapsed time, which is
     *  the one genuinely truthful number available during the wait. */
    @Volatile
    var startedAtMs: Long? = null
        private set

    fun begin(nowMs: Long = System.currentTimeMillis()) {
        startedAtMs = nowMs
        _stage.value = AnalysisStage.READING_MEASUREMENTS
    }

    fun set(next: AnalysisStage) {
        _stage.value = next
    }

    /** Terminal reset. Called on success AND on failure: a stuck spinner
     *  after an error is exactly the silence this feature exists to end. */
    fun finish() {
        _stage.value = AnalysisStage.DONE
        startedAtMs = null
    }
}
