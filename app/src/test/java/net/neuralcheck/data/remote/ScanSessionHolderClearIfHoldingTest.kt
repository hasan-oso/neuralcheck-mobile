package net.neuralcheck.data.remote

import net.neuralcheck.data.remote.ScanSessionHolder.ClearIfHoldingResult
import net.neuralcheck.data.remote.ScanSessionHolder.ClearReason
import net.neuralcheck.util.EvalCardLog
import net.neuralcheck.util.LogRedaction
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [ScanSessionHolder.clearIfHolding] — «ربط تفريغ الحامل بمعرّف جلسته» (batch 2, item 2): leaving a session, or being told the owner cancelled it, empties the
 * holder ONLY if it still holds THAT session. Without the id a leave that finishes after the customer joined another session would empty the new one - the
 * single-slot holder is the defect `ScanSessionRecords` was written around (order 2026-10-14), and `shouldClearSessionAfterAnalysis` applies the same rule
 * after an analysis. Each outcome has its own result and its own log line, with hashed ids only.
 */
class ScanSessionHolderClearIfHoldingTest {

    private val lines = mutableListOf<String>()

    @Before
    fun capture() {
        EvalCardLog.sink = { lines.add(it) }
    }

    @After
    fun release() {
        EvalCardLog.resetSink()
    }

    private fun holderHolding(id: String): ScanSessionHolder =
        ScanSessionHolder().also { it.setFromJoin(JoinSessionResult(sessionId = id, ownerId = "owner-1", status = "scanning", evaluationEnabled = true)) }

    @Test
    fun `it empties the holder when it holds the session named, and prints the clear line with the reason`() {
        val holder = holderHolding("sess-1")
        lines.clear()
        assertEquals(ClearIfHoldingResult.CLEARED, holder.clearIfHolding("sess-1", ClearReason.LEFT_BY_CUSTOMER))
        assertNull(holder.getInfo())
        assertEquals(
            listOf("holder-clear reason=left-by-customer hadInfo=true session=${LogRedaction.hashId("sess-1")} evaluationEnabled=true"),
            lines
        )
    }

    @Test
    fun `it leaves a different session alone and says so`() {
        val holder = holderHolding("sess-B")
        lines.clear()
        assertEquals(ClearIfHoldingResult.KEPT_OTHER_SESSION, holder.clearIfHolding("sess-A", ClearReason.CANCELLED_BY_OWNER))
        assertEquals("sess-B", holder.get())
        assertNotNull(holder.getInfo())
        assertEquals(
            listOf("holder-clear-skipped reason=cancelled-by-owner requested=${LogRedaction.hashId("sess-A")} held=${LogRedaction.hashId("sess-B")}"),
            lines
        )
        assertFalse("no raw id in the line", lines.single().contains("sess-A") || lines.single().contains("sess-B"))
    }

    @Test
    fun `an empty holder stays empty and the line says it was empty`() {
        val holder = ScanSessionHolder()
        assertEquals(ClearIfHoldingResult.WAS_EMPTY, holder.clearIfHolding("sess-1", ClearReason.LEFT_BY_CUSTOMER))
        assertNull(holder.getInfo())
        assertEquals(
            listOf("holder-clear-skipped reason=left-by-customer requested=${LogRedaction.hashId("sess-1")} held=missing"),
            lines
        )
    }

    /**
     * Batch 3, item 3: [JoinSessionResult.sessionId] is `null` when the answer carried none (it used to default to ""). The join function now refuses such an answer before it
     * reaches the holder, but the holder keeps its own rule - it never stores an id that is null or blank, and a join that carries none empties the slot (a new join replaces the
     * old answer; it does not merge) - and says so on its line.
     */
    @Test
    fun `setFromJoin with no usable session id empties the slot - null, empty and blank - and the line says nothing was stored`() {
        for ((label, id) in listOf("null" to null, "empty" to "", "blank" to "   ")) {
            val holder = holderHolding("sess-1")
            lines.clear()
            holder.setFromJoin(JoinSessionResult(sessionId = id, ownerId = "owner-2", status = "scanning", evaluationEnabled = true))
            assertNull("$label: the slot is emptied, it never holds a blank id", holder.getInfo())
            assertNull(label, holder.get())
            assertTrue("$label: ${lines.single()}", lines.single().startsWith("holder-set via=join stored=false "))
        }
    }

    @Test
    fun `the result wire strings are the ones the leave line prints`() {
        assertEquals(listOf("cleared", "kept-other-session", "was-empty"), ClearIfHoldingResult.values().map { it.wire })
        assertTrue(ClearReason.values().map { it.wire }.containsAll(listOf("left-by-customer", "cancelled-by-owner")))
    }
}
