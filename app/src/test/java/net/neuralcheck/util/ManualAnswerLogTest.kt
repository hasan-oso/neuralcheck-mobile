package net.neuralcheck.util

import net.neuralcheck.domain.model.ManualStatuses
import net.neuralcheck.domain.model.TestSpec
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Batch 1.0.12, item 3 (user decision 2026-10-13): the text of each [ManualAnswerLog] line, for EVERY value of every named
 * state it can carry (rule 30 — the whole enumeration, not the one a sample showed), and that nothing but a catalog test id,
 * a status and a named source ever reaches a line. The lines are read through [ManualAnswerLog.sink] (a plain
 * `(String) -> Unit` the object prints through; `isReturnDefaultValues` alone would swallow `Log.i` and leave nothing to read).
 * That each EXIT of a manual row prints its line is `ManualRowActionsTest`; that `commitAndContinue` does is
 * `ManualTestsViewModelAnswerLogTest`; what the source text guarantees is `ManualAnswerLogSourceShapeTest`.
 */
class ManualAnswerLogTest {

    @After
    fun tearDown() {
        ManualAnswerLog.resetSink()
    }

    @Test
    fun `the tag is the one exported constant`() {
        assertEquals("NeuralCheck.ManualAnswer", ManualAnswerLog.TAG)
    }

    @Test
    fun `the three write sources have the wire names of the order, and the dismissal has its own`() {
        assertEquals(
            listOf("chip" to ManualAnswerLog.WriteSource.CHIP, "dialog" to ManualAnswerLog.WriteSource.DIALOG, "auto_fill" to ManualAnswerLog.WriteSource.AUTO_FILL),
            ManualAnswerLog.WriteSource.values().map { it.wire to it }
        )
        assertEquals("dialog_dismissed", ManualAnswerLog.SOURCE_DIALOG_DISMISSED)
    }

    @Test
    fun `an answer line - exact text for every status, every source and both values of rawPresent`() {
        for (status in ManualStatuses.ALL) for (source in ManualAnswerLog.WriteSource.values()) for (raw in listOf(true, false)) {
            assertEquals(
                "manual-answer testId=M-02 status=$status rawPresent=$raw source=${source.wire}",
                ManualAnswerLog.answerWrittenLine("M-02", status, raw, source)
            )
        }
    }

    @Test
    fun `a dismissed line - exact text`() {
        assertEquals("manual-dismissed testId=M-15 source=dialog_dismissed", ManualAnswerLog.dialogDismissedLine("M-15"))
    }

    @Test
    fun `every test id of the catalog prints as itself`() {
        for (spec in TestSpec.values()) assertEquals(spec.id, ManualAnswerLog.testIdText(spec.id))
    }

    @Test
    fun `an id that is not a catalog id shape is never printed - a scan id, a uuid, a blank`() {
        val notIds = listOf("", " ", "scan-0001", "3f2b9c1e-8a4d-4c0e-9d1b-7f6a5e4d3c2b", "M-2", "m-02", "M-02 ", "M-02\nstatus=pass", "anon-uid-1", "NC-AB3F7K")
        for (bad in notIds) {
            assertEquals("`$bad`", "invalid-shape", ManualAnswerLog.testIdText(bad))
            val line = ManualAnswerLog.answerWrittenLine(bad, ManualStatuses.PASS, false, ManualAnswerLog.WriteSource.CHIP)
            assertFalse("`$line` carries `$bad`", bad.isNotBlank() && line.contains(bad))
            assertEquals("a line is one line", 1, line.lines().size)
        }
    }

    @Test
    fun `a status outside the four is named unrecognized, not defaulted to anything`() {
        for (bad in listOf("", "PASS", "warning", "error", "works", "pass\n")) {
            assertEquals("`$bad`", "unrecognized", ManualAnswerLog.statusText(bad))
        }
        for (ok in ManualStatuses.ALL) assertEquals(ok, ManualAnswerLog.statusText(ok))
    }

    @Test
    fun `a failure inside the sink never reaches the caller`() {
        ManualAnswerLog.sink = { throw IllegalStateException("the sink broke") }
        ManualAnswerLog.answerWritten("M-02", ManualStatuses.PASS, true, ManualAnswerLog.WriteSource.DIALOG)
        ManualAnswerLog.dialogDismissed("M-02")
        // nothing thrown: the log must never change what the app does
    }

    @Test
    fun `the public entry points print through the sink, one line each`() {
        val lines = mutableListOf<String>()
        ManualAnswerLog.sink = { lines += it }
        ManualAnswerLog.answerWritten("M-08", ManualStatuses.UNAVAILABLE, false, ManualAnswerLog.WriteSource.AUTO_FILL)
        ManualAnswerLog.dialogDismissed("M-08")
        assertEquals(
            listOf(
                "manual-answer testId=M-08 status=unavailable rawPresent=false source=auto_fill",
                "manual-dismissed testId=M-08 source=dialog_dismissed"
            ),
            lines
        )
        assertTrue(lines.none { it.contains('\n') })
    }
}
