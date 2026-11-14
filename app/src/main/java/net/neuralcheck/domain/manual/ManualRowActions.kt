package net.neuralcheck.domain.manual

import net.neuralcheck.util.ManualAnswerLog

/**
 * Every way an answer — or the absence of one — leaves ONE manual-test row (batch 1.0.12, items 2 and 3).
 *
 * Pure Kotlin, no Android and no Compose (a plain test reaches it without drawing the row; since 3284cca
 * `ManualUnavailableLineLayoutTest` also renders the row): the row (`ManualTestRow`) is a
 * composable that owns the dialog's open/closed state and the `onResult` callback of its screen; this class holds
 * everything the row DECIDES — what a chip tap does ([ManualChipActions]), what a dialog's answer or dismissal does —
 * and prints the one `NeuralCheck.ManualAnswer` line each exit owes ([ManualAnswerLog]). The row calls nothing else:
 * `ManualRowSourceShapeTest` pins that, so a new exit cannot appear without a line.
 *
 * The exits, and the source each one's line names:
 *  - [onChip] — a chip tap: a write (`chip`) on every row and every chip, and nothing else — no row's «يعمل» opens a dialog (M-02's since 2026-10-18; the
 *    two volume rows' since the user's decision of 2026-10-21, which replaced E22); a dialog opens from [onStartTest] alone;
 *  - [onDialogAnswer] — the dialog handed up an answer (`dialog`): its own button, a finished run, and a run dismissed after it had
 *    recorded something — M-02's grid after at least one touch, a volume row's dialog after at least one volume-key event (that records the
 *    answer the test already holds, with the run's raw);
 *  - [onDialogDismissed] — the dialog closed with no answer (`dialog_dismissed`): nothing is written;
 *  - [onRunDismissed] — the dismissal of a dialog that records a raw run (M-02, M-09, M-10), which is one of the two above depending on whether
 *    the run recorded anything.
 * (The third source, `auto_fill`, is not a row's: `ManualTestsViewModel.commitAndContinue` writes it.)
 *
 * @param write the screen's own writer: `ManualTestsViewModel.setResult` in the post-scan screen,
 *   `ReportViewModel.saveManualResults` in the report's editor. This class never writes by any other way.
 * @param setActiveDialog the row's dialog state: a test id opens that test's dialog, `null` closes it.
 */
class ManualRowActions(
    private val testId: String,
    private val write: (testId: String, status: String, measurements: Map<String, Any?>?) -> Unit,
    private val setActiveDialog: (String?) -> Unit
) {

    /**
     * A tap on one of the answer chips; [status] is what the chip stands for. It writes that answer with no run (no raw of its own: what a write does to
     * the raw already stored is [ManualChipActions.rawToStore]'s) and opens nothing, on every row.
     */
    fun onChip(status: String) {
        written(status, null, ManualAnswerLog.WriteSource.CHIP)
    }

    /** The «ابدأ الاختبار» button: opens the row's dialog. It is not an answer, so it prints no line. */
    fun onStartTest() {
        setActiveDialog(testId)
    }

    /** The dialog handed up [status], with the raw of the run that produced it ([measurements]) or null; the dialog closes. */
    fun onDialogAnswer(status: String, measurements: Map<String, Any?>?) {
        written(status, measurements, ManualAnswerLog.WriteSource.DIALOG)
        setActiveDialog(null)
    }

    /** The dialog closed and has no answer: nothing is written; one line says so. */
    fun onDialogDismissed() {
        ManualAnswerLog.dialogDismissed(testId)
        setActiveDialog(null)
    }

    /**
     * The dismissal (back / outside) of a dialog that records a raw run — M-02's grid, and since fieldsVersion 71 (A-VOL) the volume rows M-09 and M-10: a
     * run that recorded something (at least one touch, at least one volume-key event: the dialog passes its raw, else null) is recorded with the answer
     * the test already holds ([currentStatus]; `not_tested` when it holds none) and the run's raw — an answer from the dialog; a dismissal
     * with nothing recorded records nothing — a dismissed dialog. ([TouchGridRecorder.recordOnDismiss] decides, for every such dialog.)
     */
    fun onRunDismissed(currentStatus: String?, raw: Map<String, Any?>?) {
        val record = TouchGridRecorder.recordOnDismiss(currentStatus, raw)
        if (record != null) onDialogAnswer(record.first, record.second) else onDialogDismissed()
    }

    private fun written(status: String, measurements: Map<String, Any?>?, source: ManualAnswerLog.WriteSource) {
        write(testId, status, measurements)
        ManualAnswerLog.answerWritten(testId, status, rawPresent = measurements != null, source = source)
    }
}
