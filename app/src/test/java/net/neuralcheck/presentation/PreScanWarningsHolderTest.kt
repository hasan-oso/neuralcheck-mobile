package net.neuralcheck.presentation

import net.neuralcheck.data.local.files.PreScanWarningsStates
import net.neuralcheck.presentation.viewmodel.PreScanWarningsHolder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * fieldsVersion 68, batch 3 part 2-b (user decision 2026-10-08: "PreScanWarningsHolder:
 * a third state, not_written"). The holder used to return an empty list both for "the
 * pre-scan check ran and placed no warning" and for "nobody wrote anything" - so
 * `preScanWarningsState = read` with `[]` could mean either. The real holder, no
 * substitution: only the screen that calls `set` is outside the test.
 */
class PreScanWarningsHolderTest {

    private fun stateOf(holder: PreScanWarningsHolder) =
        PreScanWarningsStates.of(Result.success(holder.readAndClear()))

    @Test
    fun `a holder nobody wrote to is not_written`() {
        val holder = PreScanWarningsHolder()
        assertEquals("not_written", stateOf(holder))
    }

    @Test
    fun `a clean pre-scan check - set with an empty list - is read with no warnings`() {
        val holder = PreScanWarningsHolder()
        holder.set(emptyList())            // what PreScanScreen does on a clean check
        val read = holder.readAndClear()
        assertTrue(read.written)
        assertTrue(read.warnings.isEmpty())
        holder.set(emptyList())
        assertEquals("read", stateOf(holder))
    }

    @Test
    fun `accepted warnings are read as written`() {
        val holder = PreScanWarningsHolder()
        holder.set(listOf(PreScanWarningsHolder.WARN_HOT_BATTERY, PreScanWarningsHolder.WARN_RECENTLY_UNPLUGGED))
        val read = holder.readAndClear()
        assertTrue(read.written)
        assertEquals(listOf("hot_battery", "recently_unplugged"), read.warnings)
    }

    @Test
    fun `draining forgets - the next scan does not inherit the last one's state`() {
        val holder = PreScanWarningsHolder()
        holder.set(listOf("hot_battery"))
        holder.readAndClear()
        val second = holder.readAndClear()
        assertFalse("a drained holder is back to nothing written", second.written)
        assertTrue(second.warnings.isEmpty())
    }

    @Test
    fun `the holder keeps a copy - a list changed after set does not change what is read`() {
        val holder = PreScanWarningsHolder()
        val mine = mutableListOf("hot_battery")
        holder.set(mine)
        mine.clear()
        assertEquals(listOf("hot_battery"), holder.readAndClear().warnings)
    }
}
