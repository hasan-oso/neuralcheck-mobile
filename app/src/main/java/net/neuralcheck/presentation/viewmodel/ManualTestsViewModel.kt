package net.neuralcheck.presentation.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import net.neuralcheck.data.local.db.converter.MeasurementsJson
import net.neuralcheck.data.local.db.entity.FunctionalResultEntity
import net.neuralcheck.data.repository.ScanRepository
import net.neuralcheck.data.repository.TestResultRepository
import net.neuralcheck.data.repository.withStoredRawKept
import net.neuralcheck.domain.evaluation.EvaluationSubmissionStatusHolder
import net.neuralcheck.domain.manual.ManualTestVisibility
import net.neuralcheck.domain.model.ManualStatuses
import net.neuralcheck.domain.model.TestSpec
import net.neuralcheck.service.sync.SyncScheduler
import net.neuralcheck.util.ManualAnswerLog
import java.util.Locale
import javax.inject.Inject

/**
 * Backs [net.neuralcheck.presentation.screen.ManualTestsScreen] — the new
 * post-scan step that collects user-driven manual test answers BEFORE the
 * report screen runs scoring. The launch flavor used to skip this entirely
 * (ReportScreen's launch branch only renders the AI section, never the
 * manual chips), which meant the AI never saw any manual results in the
 * fingerprint. By moving manual tests to a dedicated screen on the
 * post-scan path, both flavors produce a complete dataset for scoring.
 *
 * Storage convention mirrors what [ReportViewModel.saveManualResults]
 * already writes:
 *  - testId starts with "M-" so [BehaviorFingerprintBuilder] picks it up
 *    into the `manualResults` array before POSTing to `analyzeDevice`.
 *  - notes = "manual" so the report screen's `existingResults` map filters
 *    correctly (kept in sync with the existing convention).
 *  - score = 100f for "pass", 0f for "fail", null for "not_tested" and
 *    "unavailable" so the rule-based engine treats skipped tests (and parts the
 *    device does not have) as ignored rather than 0 — [ManualStatuses.scoreOf].
 */
@HiltViewModel
class ManualTestsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val testResultRepository: TestResultRepository,
    private val scanRepository: ScanRepository,
    // Order 2026-09-30 («ملحق — النصّ، ورسالة التقييم، والبروتوكول»): the
    // evaluation-submission failure line — see [observeEvaluationFailure].
    private val evaluationSubmissionStatusHolder: EvaluationSubmissionStatusHolder
) : ViewModel() {

    private val _results = MutableStateFlow<Map<String, String>>(emptyMap())
    val results: StateFlow<Map<String, String>> = _results

    private val _isCommitting = MutableStateFlow(false)
    val isCommitting: StateFlow<Boolean> = _isCommitting

    /**
     * Order 2026-09-30 («ملحق — النصّ، ورسالة التقييم، والبروتوكول»): the
     * confirm-at-scan-completion evaluation submission's failure message for
     * [scanId], or null when it succeeded, there was no evaluation to send,
     * or nothing has arrived (yet, or ever — see below). Never a "success"
     * line — none was ordered ("ولا نصّ «نجح» جديد إلا بقرار").
     */
    private val _evaluationFailureMessage = MutableStateFlow<String?>(null)
    val evaluationFailureMessage: StateFlow<String?> = _evaluationFailureMessage

    /**
     * One-shot: starts collecting [EvaluationSubmissionStatusHolder.failures]
     * for [scanId]. Called once from ManualTestsScreen's `LaunchedEffect
     * (scanId)`, alongside [load].
     *
     * Order 2026-09-30 («ملحق — النصّ، ورسالة التقييم، والبروتوكول»): a
     * failure that resolves AFTER the user has already left this screen is
     * never shown, ANYWHERE — this collector runs in [viewModelScope],
     * which is cancelled the instant this ViewModel's `onCleared()` fires
     * (i.e. the moment ManualTestsScreen's NavBackStackEntry is popped), so
     * a late-arriving event on [EvaluationSubmissionStatusHolder.failures]
     * (a `SharedFlow` with `replay = 0` — no history for a subscriber that
     * isn't listening at emission time) simply has no one left to deliver
     * it to. Nothing propagates the message to ReportScreen or any other
     * later screen — there is no code path that could, since this state
     * lives ONLY in this ViewModel instance.
     */
    fun observeEvaluationFailure(scanId: String) {
        viewModelScope.launch {
            evaluationSubmissionStatusHolder.failures.collect { failure ->
                if (failure.scanId == scanId) {
                    _evaluationFailureMessage.value = failure.message
                }
            }
        }
    }

    /** Loads any manual answers previously saved for [scanId] so the UI
     * can pre-select the chips when the user re-enters the screen. */
    fun load(scanId: String) {
        viewModelScope.launch {
            val existing = testResultRepository.getResults(scanId)
                .filter { it.notes == "manual" }
                .associate { it.testId to it.status }
            _results.value = existing
        }
    }

    /** Persists a single manual answer immediately. Mirroring upserts on
     * each tap means the user's progress survives a process death without
     * a separate "save draft" flow.
     *
     * fieldsVersion 69 (N-19): [measurements] is the raw of the RUN that produced this
     * answer (the touch grid's), stored in the row's own `measurements` column; null = this
     * answer came without a run (a chip tap) and the column is NULL. The raw belongs to the
     * answer written with it: the latest write replaces both.
     *
     * The one exception (M7(ب) 2026-10-18, widened by E21 item 3, approved 2026-10-15; the two volume rows joined on 2026-10-21): a chip tap on M-02,
     * M-09 or M-10 — any of its four answers, over any stored answer — writes the answer only and keeps the raw stored with the row (a grid run's, a
     * volume run's) — [withStoredRawKept] / `ManualChipActions.rawToStore`. A new run's raw replaces it. */
    fun setResult(scanId: String, testId: String, status: String, measurements: Map<String, Any?>? = null) {
        viewModelScope.launch {
            val spec = TestSpec.fromId(testId)
            testResultRepository.upsertResults(
                listOf(
                    testResultRepository.withStoredRawKept(
                        FunctionalResultEntity(
                            scanId = scanId,
                            testId = testId,
                            componentName = testId,
                            categoryName = spec?.category?.name?.lowercase(Locale.US) ?: "manual",
                            status = status,
                            score = ManualStatuses.scoreOf(status),
                            rawValue = null,
                            rawValueUnit = null,
                            notes = "manual",
                            timestamp = System.currentTimeMillis(),
                            measurements = MeasurementsJson.encode(measurements)
                        )
                    )
                )
            )
            _results.value = _results.value + (testId to status)
        }
    }

    /** Fills any unanswered tests in [allIds] as "not_tested", marks the
     * scan unsynced, kicks off a sync, and invokes [onComplete] on the
     * main dispatcher. The scoring/report screen runs after this — by
     * which point every manual test the screen asked has a row in Room (skipped
     * rows included) so [BehaviorFingerprintBuilder.manualResults] is complete.
     *
     * fieldsVersion 69 (N-17): [allIds] is the tests the screen ASKED — a test the
     * device model does not have is not in it ([ManualTestVisibility.idsToAnswer]),
     * so it gets no "not_tested" row; it is named in
     * `manualResultsHeader.hiddenByModel` instead. */
    fun commitAndContinue(
        scanId: String,
        allIds: List<String>,
        onComplete: () -> Unit
    ) {
        if (_isCommitting.value) return
        _isCommitting.value = true
        viewModelScope.launch {
            try {
                val current = _results.value
                val unanswered = allIds.filter { it !in current.keys }
                if (unanswered.isNotEmpty()) {
                    val entries = unanswered.map { id ->
                        val spec = TestSpec.fromId(id)
                        FunctionalResultEntity(
                            scanId = scanId,
                            testId = id,
                            componentName = id,
                            categoryName = spec?.category?.name?.lowercase(Locale.US) ?: "manual",
                            status = ManualStatuses.NOT_TESTED,
                            score = null,
                            rawValue = null,
                            rawValueUnit = null,
                            notes = "manual",
                            timestamp = System.currentTimeMillis()
                        )
                    }
                    testResultRepository.upsertResults(entries)
                    // Batch 1.0.12, item 3: one `NeuralCheck.ManualAnswer` line per row filled here — the third source
                    // (`auto_fill`) of the manual-answer lines; the chip and dialog sources are printed by the row itself
                    // (ManualRowActions), which is why `setResult` above prints nothing. After the write, like the row's.
                    unanswered.forEach { id ->
                        ManualAnswerLog.answerWritten(
                            testId = id,
                            status = ManualStatuses.NOT_TESTED,
                            rawPresent = false,
                            source = ManualAnswerLog.WriteSource.AUTO_FILL
                        )
                    }
                    _results.value = _results.value +
                        unanswered.associateWith { ManualStatuses.NOT_TESTED }
                }
                runCatching {
                    scanRepository.markUnsynced(scanId)
                    SyncScheduler.syncNow(context)
                }
                onComplete()
            } finally {
                _isCommitting.value = false
            }
        }
    }
}
