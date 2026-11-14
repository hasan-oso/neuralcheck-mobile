package net.neuralcheck.domain.manual

import net.neuralcheck.domain.model.ManualStatuses
import net.neuralcheck.presentation.screen.manualTestItems
import net.neuralcheck.util.ManualAnswerLog
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Batch 1.0.12, items 2 and 3: every way an answer — or the absence of one — leaves a manual row, as [ManualRowActions]
 * carries it out: what is WRITTEN, what the row's dialog is told to do, and the one `NeuralCheck.ManualAnswer` line each
 * exit owes. The write and the dialog are recorded by this test; the lines are read through [ManualAnswerLog.sink].
 *
 * Why here and not on the composable: when this was written (batch 1.0.12) the unit tests did not have a Compose harness (one exists since 3284cca: `ManualUnavailableLineLayoutTest` renders the row), so the row's decisions live in a class
 * that has none (`ManualRowSourceShapeTest` proves the row calls only this class).
 */
class ManualRowActionsTest {

    private val lines = mutableListOf<String>()

    private class Recording(testId: String) {
        data class Write(val testId: String, val status: String, val measurements: Map<String, Any?>?)

        val writes = mutableListOf<Write>()

        /** Each call to the dialog state, in order: a test id opens, null closes. */
        val dialog = mutableListOf<String?>()
        val actions = ManualRowActions(
            testId = testId,
            write = { id, status, m -> writes += Write(id, status, m) },
            setActiveDialog = { dialog += it }
        )
    }

    private fun row(testId: String) = Recording(testId)

    @Before
    fun setUp() {
        ManualAnswerLog.sink = { lines += it }
    }

    @After
    fun tearDown() {
        ManualAnswerLog.resetSink()
    }

    private val grid = mapOf<String, Any?>("touchedCells" to 120, "endReason" to "auto_complete")

    // ---- the chips ----

    @Test
    fun `a chip on any row writes its answer with no raw, prints one chip line and opens nothing - every row times every answer, the works chip of every row included`() {
        val ids = manualTestItems.map { it.id }
        assertTrue(ids.size >= 15 && "M-02" in ids && "M-09" in ids && "M-10" in ids)
        for (id in ids) for (status in ManualStatuses.ALL) {
            lines.clear()
            val r = row(id)
            r.actions.onChip(status)
            assertEquals("$id/$status", listOf(Recording.Write(id, status, null)), r.writes)
            assertEquals("$id/$status prints one line", listOf("manual-answer testId=$id status=$status rawPresent=false source=chip"), lines)
            assertTrue("$id/$status: a chip does not touch the dialog", r.dialog.isEmpty())
        }
    }

    @Test
    fun `the works chip of M-09 and M-10 writes pass with no raw, prints one chip line and does not open the dialog - the user's decision of 2026-10-21, which replaced E22`() {
        for (id in listOf(VolumeKeyRecorder.TEST_ID_UP, VolumeKeyRecorder.TEST_ID_DOWN)) {
            lines.clear()
            val r = row(id)
            r.actions.onChip(ManualStatuses.PASS)
            assertEquals("$id: the tap is the technician's answer", listOf(Recording.Write(id, ManualStatuses.PASS, null)), r.writes)
            assertEquals("$id: and prints its chip line", listOf("manual-answer testId=$id status=pass rawPresent=false source=chip"), lines)
            assertTrue("$id: the dialog stays closed", r.dialog.isEmpty())
        }
    }

    @Test
    fun `the start button of M-09 and M-10 is what opens their dialog - nothing is written, nothing is printed`() {
        for (id in listOf(VolumeKeyRecorder.TEST_ID_UP, VolumeKeyRecorder.TEST_ID_DOWN)) {
            lines.clear()
            val r = row(id)
            r.actions.onStartTest()
            assertEquals("$id: the dialog opens", listOf<String?>(id), r.dialog)
            assertTrue("$id: opening is not an answer", r.writes.isEmpty())
            assertTrue("$id: and prints no line", lines.isEmpty())
        }
    }

    @Test
    fun `M-02 works chip writes pass with no raw and prints one chip line - it does not open the grid (M7 b, user decision 2026-10-18)`() {
        val r = row("M-02")
        r.actions.onChip(ManualStatuses.PASS)
        assertEquals(listOf(Recording.Write("M-02", ManualStatuses.PASS, null)), r.writes)
        assertEquals(listOf("manual-answer testId=M-02 status=pass rawPresent=false source=chip"), lines)
        assertTrue("the tap marks the answer without opening the test", r.dialog.isEmpty())
    }

    @Test
    fun `M-02 not-works and not-tested chips write their answer with no raw, as every other row`() {
        for (status in listOf(ManualStatuses.FAIL, ManualStatuses.NOT_TESTED)) {
            lines.clear()
            val r = row("M-02")
            r.actions.onChip(status)
            assertEquals(listOf(Recording.Write("M-02", status, null)), r.writes)
            assertEquals(listOf("manual-answer testId=M-02 status=$status rawPresent=false source=chip"), lines)
            assertTrue(r.dialog.isEmpty())
        }
    }

    @Test
    fun `on M-02 every chip tap of every answer writes that answer, in order, and none of them opens the dialog`() {
        val r = row("M-02")
        for (status in ManualStatuses.ALL) r.actions.onChip(status)
        assertEquals(ManualStatuses.ALL, r.writes.map { it.status })
        assertTrue("no write carries a raw: a chip is not a run", r.writes.all { it.measurements == null })
        assertTrue(r.dialog.isEmpty())
    }

    // ---- the start button ----

    @Test
    fun `the start button opens the dialog - not an answer, no write, no line`() {
        val r = row("M-08")
        r.actions.onStartTest()
        assertEquals(listOf<String?>("M-08"), r.dialog)
        assertTrue(r.writes.isEmpty())
        assertTrue(lines.isEmpty())
    }

    @Test
    fun `M-02 start button still opens the grid - nothing is written and nothing is printed (M7 b - the grid opens from the button alone)`() {
        val r = row("M-02")
        r.actions.onStartTest()
        assertEquals(listOf<String?>("M-02"), r.dialog)
        assertTrue("opening the grid is not an answer", r.writes.isEmpty())
        assertTrue("and prints no line", lines.isEmpty())
    }

    // ---- the dialog's answer ----

    @Test
    fun `a dialog answer with no run writes with no raw, prints a dialog line with rawPresent false, and closes the dialog`() {
        val r = row("M-08")
        r.actions.onDialogAnswer(ManualStatuses.UNAVAILABLE, null)
        assertEquals(listOf(Recording.Write("M-08", ManualStatuses.UNAVAILABLE, null)), r.writes)
        assertEquals(listOf("manual-answer testId=M-08 status=unavailable rawPresent=false source=dialog"), lines)
        assertEquals(listOf<String?>(null), r.dialog)
    }

    @Test
    fun `a finished grid run writes pass WITH its raw and prints rawPresent true`() {
        val r = row("M-02")
        r.actions.onDialogAnswer(ManualStatuses.PASS, grid)
        assertEquals(listOf(Recording.Write("M-02", ManualStatuses.PASS, grid)), r.writes)
        assertEquals(listOf("manual-answer testId=M-02 status=pass rawPresent=true source=dialog"), lines)
        assertEquals(listOf<String?>(null), r.dialog)
    }

    @Test
    fun `every status a dialog can hand up prints its own status`() {
        for (status in ManualStatuses.ALL) {
            lines.clear()
            row("M-15").actions.onDialogAnswer(status, null)
            assertEquals(listOf("manual-answer testId=M-15 status=$status rawPresent=false source=dialog"), lines)
        }
    }

    // ---- the dialog closed with no answer ----

    @Test
    fun `a dialog closed with no answer writes nothing, prints one dismissed line, and closes the dialog`() {
        val r = row("M-12")
        r.actions.onDialogDismissed()
        assertTrue(r.writes.isEmpty())
        assertEquals(listOf("manual-dismissed testId=M-12 source=dialog_dismissed"), lines)
        assertEquals(listOf<String?>(null), r.dialog)
    }

    // ---- a run dismissed: the M-02 grid, and (A-VOL) the volume dialogs ----

    @Test
    fun `the grid dismissed after a touch records the answer the test already holds, with the run's raw - an answer from the dialog`() {
        val r = row("M-02")
        r.actions.onRunDismissed(ManualStatuses.FAIL, grid)
        assertEquals(listOf(Recording.Write("M-02", ManualStatuses.FAIL, grid)), r.writes)
        assertEquals(listOf("manual-answer testId=M-02 status=fail rawPresent=true source=dialog"), lines)
        assertEquals(listOf<String?>(null), r.dialog)
    }

    @Test
    fun `the grid dismissed after a touch on a test that holds no answer records not_tested with the raw`() {
        val r = row("M-02")
        r.actions.onRunDismissed(null, grid)
        assertEquals(listOf(Recording.Write("M-02", ManualStatuses.NOT_TESTED, grid)), r.writes)
        assertEquals(listOf("manual-answer testId=M-02 status=not_tested rawPresent=true source=dialog"), lines)
    }

    @Test
    fun `the grid dismissed with no touch writes nothing - it is a dismissed dialog, and says so`() {
        val r = row("M-02")
        r.actions.onRunDismissed(ManualStatuses.FAIL, null)
        assertTrue("an accidental open must not overwrite the raw of a real earlier run", r.writes.isEmpty())
        assertEquals(listOf("manual-dismissed testId=M-02 source=dialog_dismissed"), lines)
        assertEquals(listOf<String?>(null), r.dialog)
    }

    private val volumeRaw = mapOf<String, Any?>("expectedKeyCode" to 24, "eventsTotal" to 1, "endReason" to "dismissed")

    @Test
    fun `a volume dialog dismissed after a key event records the answer the test already holds, with the run's raw - and with none the answer is not_tested`() {
        val r = row("M-09")
        r.actions.onRunDismissed(ManualStatuses.FAIL, volumeRaw)
        assertEquals(listOf(Recording.Write("M-09", ManualStatuses.FAIL, volumeRaw)), r.writes)
        assertEquals(listOf("manual-answer testId=M-09 status=fail rawPresent=true source=dialog"), lines)
        assertEquals(listOf<String?>(null), r.dialog)
        lines.clear()
        val s = row("M-10")
        s.actions.onRunDismissed(null, volumeRaw)
        assertEquals(listOf(Recording.Write("M-10", ManualStatuses.NOT_TESTED, volumeRaw)), s.writes)
    }

    @Test
    fun `a volume dialog dismissed with no key event writes nothing - an accidental open must not overwrite the raw of a real earlier run`() {
        val r = row("M-10")
        r.actions.onRunDismissed(ManualStatuses.PASS, null)
        assertTrue(r.writes.isEmpty())
        assertEquals(listOf("manual-dismissed testId=M-10 source=dialog_dismissed"), lines)
        assertEquals(listOf<String?>(null), r.dialog)
    }

    @Test
    fun `a volume run's answer from the dialog writes with its raw and prints rawPresent true`() {
        val r = row("M-09")
        r.actions.onDialogAnswer(ManualStatuses.PASS, volumeRaw)
        assertEquals(listOf(Recording.Write("M-09", ManualStatuses.PASS, volumeRaw)), r.writes)
        assertEquals(listOf("manual-answer testId=M-09 status=pass rawPresent=true source=dialog"), lines)
    }

    // ---- the lines themselves ----

    @Test
    fun `no line carries anything but a test id, a status, a boolean and a named source`() {
        val r = row("M-02")
        r.actions.onChip(ManualStatuses.FAIL)
        r.actions.onDialogAnswer(ManualStatuses.PASS, grid)
        r.actions.onDialogDismissed()
        val shape = Regex("""manual-answer testId=[A-Z]{1,3}-\d{2,3} status=(pass|fail|not_tested|unavailable) rawPresent=(true|false) source=(chip|dialog|auto_fill)|manual-dismissed testId=[A-Z]{1,3}-\d{2,3} source=dialog_dismissed""")
        assertEquals(3, lines.size)
        lines.forEach { assertTrue("`$it` is not one of the two line shapes", shape.matches(it)) }
    }
}
