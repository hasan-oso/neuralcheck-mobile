package net.neuralcheck.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** S-35 (order 2026-10-08): 200-character cap on every reason text. */
class ReasonTextTest {

    @Test
    fun `cap is 200`() {
        assertEquals(200, ReasonText.MAX_CHARS)
    }

    @Test
    fun `text within the cap is returned unchanged`() {
        val exactly = "e".repeat(200)
        assertEquals(exactly, ReasonText.cap(exactly))
        assertEquals("short", ReasonText.cap("short"))
        assertEquals("", ReasonText.cap(""))
    }

    @Test
    fun `longer text is cut to the cap and otherwise unaltered`() {
        val long = "x".repeat(250)
        val cut = ReasonText.cap(long)
        assertEquals(200, cut.length)
        assertEquals(long.substring(0, 200), cut)
    }

    @Test
    fun `a cut never leaves half of a surrogate pair`() {
        // 199 ASCII chars + one supplementary character (2 UTF-16 units):
        // a naive cut at 200 keeps only the high surrogate.
        val text = "a".repeat(199) + "😀" + "tail"
        val cut = ReasonText.cap(text)
        assertEquals(199, cut.length)
        assertFalse(Character.isHighSurrogate(cut.last()))
        assertTrue(cut.all { it == 'a' })
    }

    @Test
    fun `of uses the message text and names a missing message explicitly`() {
        assertEquals(
            "open failed: EACCES (Permission denied)",
            ReasonText.of(java.io.FileNotFoundException("open failed: EACCES (Permission denied)"))
        )
        assertEquals(ReasonText.NO_MESSAGE, ReasonText.of(RuntimeException()))
        assertEquals(ReasonText.NO_MESSAGE, ReasonText.of(RuntimeException("   ")))
        assertEquals(200, ReasonText.of(RuntimeException("m".repeat(900))).length)
    }
}
