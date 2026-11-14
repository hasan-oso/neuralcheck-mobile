package net.neuralcheck.util

import android.util.Log
import net.neuralcheck.domain.model.ManualStatuses

/**
 * A permanent, named log line for every manual answer the app writes, and for every dialog closed without one.
 *
 * Batch 1.0.12, item 3 (user decision 2026-10-13, option «Approve both as written»): "a permanent log line per manual
 * answer", so that a technician's logcat says what the app recorded for each manual test and where the answer came from —
 * the way `NeuralCheck.EvalCard` (see [EvalCardLog]) shows the evaluation card's chain. Why it is needed: until now a
 * manual answer left no trace anywhere but the Room row, and a dialog closed without an answer left nothing at all.
 *
 * ── The contract of these lines ──
 *  - ONE tag, [TAG] (rule 24). It is none of the three tags `DiagnosticLoggingGuardTest` forbids (that test scans the
 *    source text, comments included, so they are not spelled out here): those are TEMPORARY diagnosis tags; these lines
 *    are permanent by the user's decision.
 *  - A line carries a test id, a status, a boolean and a named source. NO scan id, NO device id, NO user data: the only
 *    strings that reach a line are a catalog test id (checked against its shape — anything else prints as
 *    `invalid-shape`), a [ManualStatuses] value (anything else prints as `unrecognized`) and the wire name of a source.
 *  - One line per EVENT, never per frame; nothing here is called from a composable's body.
 *  - Named values are written as explicit wire strings, never `Enum.name`, a class name or a stack trace: R8 renames
 *    classes in the release build, so those would print letters.
 *  - A failure inside a log line never changes behaviour: every emit is wrapped, and nothing here throws.
 *  - Not a displayed text (decision 90: no new displayed text without approval): logcat only.
 *  - The line says an answer LEFT the row (or the view model) toward the store. It does not say Room confirmed it.
 *
 * The lines:
 *   manual-answer     testId= status= rawPresent= source=   (source: chip | dialog | auto_fill)
 *   manual-dismissed  testId= source=dialog_dismissed        (a dialog was closed and wrote nothing)
 *
 * `rawPresent` is whether the write carried a `measurements` raw (the M-02 grid's run and the volume dialogs' runs do; a chip never does). It
 * describes the WRITE, not the stored row: any chip tap on M-02, M-09 or M-10 prints `rawPresent=false` and the store keeps the raw it holds
 * (`ManualChipActions.rawToStore`, 2026-10-18; every chip since E21 item 3; the two volume rows since 2026-10-21).
 * [sink] is where a finished line goes; the default prints it with `Log.i` (INFO survives logcat verbosity filters, as
 * [EvalCardLog] notes). Tests replace it to read the lines.
 */
object ManualAnswerLog {

    const val TAG = "NeuralCheck.ManualAnswer"

    /** Where a WRITTEN answer came from. Wire strings are explicit (R8-safe). */
    enum class WriteSource(val wire: String) {
        /** One of the row's answer chips. */
        CHIP("chip"),

        /** The row's dialog handed an answer up (its own button, or a finished run). */
        DIALOG("dialog"),

        /** `commitAndContinue` filled a test nobody answered as `not_tested`. */
        AUTO_FILL("auto_fill")
    }

    /** The `source` of the line for a dialog that was closed with no answer. */
    const val SOURCE_DIALOG_DISMISSED = "dialog_dismissed"

    /** What a catalog test id looks like (`M-02`, `CM-03`, `SN-08`). Anything else is never printed. */
    private val TEST_ID_SHAPE = Regex("""[A-Z]{1,3}-\d{2,3}""")

    private val defaultSink: (String) -> Unit = { Log.i(TAG, it) }

    @Volatile
    internal var sink: (String) -> Unit = defaultSink

    internal fun resetSink() {
        sink = defaultSink
    }

    private inline fun emit(build: () -> String) {
        try {
            sink(build())
        } catch (_: Throwable) {
            // A log line must never change what the app does.
        }
    }

    /** One manual answer written (or handed to the store). [rawPresent]: the write carried a `measurements` raw. */
    fun answerWritten(testId: String, status: String, rawPresent: Boolean, source: WriteSource) =
        emit { answerWrittenLine(testId, status, rawPresent, source) }

    /** A dialog was closed and wrote nothing. */
    fun dialogDismissed(testId: String) = emit { dialogDismissedLine(testId) }

    internal fun answerWrittenLine(testId: String, status: String, rawPresent: Boolean, source: WriteSource): String =
        "manual-answer testId=${testIdText(testId)} status=${statusText(status)} rawPresent=$rawPresent source=${source.wire}"

    internal fun dialogDismissedLine(testId: String): String =
        "manual-dismissed testId=${testIdText(testId)} source=$SOURCE_DIALOG_DISMISSED"

    internal fun testIdText(testId: String): String = if (TEST_ID_SHAPE.matches(testId)) testId else "invalid-shape"

    internal fun statusText(status: String): String = if (status in ManualStatuses.ALL) status else "unrecognized"
}
