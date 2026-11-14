package net.neuralcheck.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unmeasured-category contract (backend fix, 2026-09).
 *
 * The backend now emits categories whose tests ALL came back not_tested /
 * unavailable: grade "?", score 0, pass/total 0/0, plus a note. Four
 * separate app mechanisms used to turn that into a verdict of failure:
 *   1. the PDF dropped the row (`filter { totalCount > 0 }`) — it vanished;
 *   2. gradeColor mapped "?" through `else -> GRADE_D` — it printed RED;
 *   3. the AI section rendered "0%";
 *   4. the badge showed a bare "?" with no explanation.
 * This class locks the rules those fixes are built on.
 */
class CategoryPresentationTest {

    // ── isUnmeasured ──

    @Test
    fun `explicit measured false is unmeasured whatever the counts say`() {
        assertTrue(CategoryPresentation.isUnmeasured(measured = false, grade = "?", totalCount = 0))
        // The server field wins even if the shape looks measured.
        assertTrue(CategoryPresentation.isUnmeasured(measured = false, grade = "A", totalCount = 5))
    }

    @Test
    fun `explicit measured true is never unmeasured`() {
        assertFalse(CategoryPresentation.isUnmeasured(measured = true, grade = "A", totalCount = 5))
        // Even a 0/0 shape: the server says it measured something, believe it.
        assertFalse(CategoryPresentation.isUnmeasured(measured = true, grade = "?", totalCount = 0))
    }

    @Test
    fun `legacy report without the measured field falls back to shape`() {
        // The exact payload the backend now sends, minus the new flag.
        assertTrue(CategoryPresentation.isUnmeasured(measured = null, grade = "?", totalCount = 0))
        assertTrue(CategoryPresentation.isUnmeasured(measured = null, grade = "", totalCount = 0))
        assertTrue(CategoryPresentation.isUnmeasured(measured = null, grade = null, totalCount = 0))
    }

    @Test
    fun `a graded category is measured even at zero total`() {
        // Server contradicting itself: prefer the letter over the shape,
        // so we never hide a real verdict behind "not measured".
        assertFalse(CategoryPresentation.isUnmeasured(measured = null, grade = "D", totalCount = 0))
    }

    @Test
    fun `an ordinary graded category is measured`() {
        for (g in listOf("A", "B", "C", "D", "a", "d")) {
            assertFalse(g, CategoryPresentation.isUnmeasured(measured = null, grade = g, totalCount = 4))
        }
    }

    @Test
    fun `unknown grade detection covers blanks and nulls`() {
        assertTrue(CategoryPresentation.isUnknownGrade("?"))
        assertTrue(CategoryPresentation.isUnknownGrade(""))
        assertTrue(CategoryPresentation.isUnknownGrade(null))
        assertTrue(CategoryPresentation.isUnknownGrade("  "))
        assertTrue(CategoryPresentation.isUnknownGrade("F"))
        assertFalse(CategoryPresentation.isUnknownGrade("a"))
        assertFalse(CategoryPresentation.isUnknownGrade(" B "))
    }

    // ── notRunCount: the gap rule ──

    @Test
    fun `server excludedCount is authoritative - the gpu field case`() {
        // gpu arrives total_count=2, declared_count=5, excluded_count=3.
        assertEquals(3, CategoryPresentation.notRunCount(
            excludedCount = 3, declaredCount = 5, totalCount = 2))
    }

    @Test
    fun `excludedCount wins over the derivation when they disagree`() {
        // The server computed the gap itself; we do not second-guess it.
        assertEquals(1, CategoryPresentation.notRunCount(
            excludedCount = 1, declaredCount = 9, totalCount = 2))
    }

    @Test
    fun `excluded zero is a real answer not an absent field`() {
        // A fully unmeasured camera arrives excluded_count=0: the "not
        // measured" badge already says everything, so no coverage line.
        assertNull(CategoryPresentation.notRunCount(
            excludedCount = 0, declaredCount = 3, totalCount = 0))
    }

    @Test
    fun `gap is NOT the sum of notTested and unavailable`() {
        // The payload does not guarantee those counters are disjoint, so
        // adding them can overstate the gap. The server's 3 is
        // authoritative even if notTested(3) + unavailable(2) would say 5.
        val gap = CategoryPresentation.notRunCount(
            excludedCount = 3, declaredCount = 5, totalCount = 2)
        assertEquals(3, gap)
        assertTrue("the naive sum would have overstated the gap", (3 + 2) > gap!!)
    }

    @Test
    fun `legacy payload without excludedCount derives from declared minus total`() {
        assertEquals(3, CategoryPresentation.notRunCount(
            excludedCount = null, declaredCount = 5, totalCount = 2))
    }

    @Test
    fun `fully covered category reports no gap`() {
        assertNull(CategoryPresentation.notRunCount(
            excludedCount = 0, declaredCount = 5, totalCount = 5))
        assertNull(CategoryPresentation.notRunCount(
            excludedCount = null, declaredCount = 5, totalCount = 5))
    }

    @Test
    fun `legacy report without either field reports no gap`() {
        assertNull(CategoryPresentation.notRunCount(
            excludedCount = null, declaredCount = null, totalCount = 2))
    }

    @Test
    fun `inconsistent counts never produce a negative or zero gap`() {
        assertNull(CategoryPresentation.notRunCount(
            excludedCount = null, declaredCount = 2, totalCount = 5))
        assertNull(CategoryPresentation.notRunCount(
            excludedCount = -4, declaredCount = 2, totalCount = 5))
    }

    // ── declaredTotal: the coverage denominator ──

    @Test
    fun `declared denominator prefers the server field`() {
        assertEquals(5, CategoryPresentation.declaredTotal(
            declaredCount = 5, totalCount = 2, notRun = 3))
    }

    @Test
    fun `declared denominator falls back to executed plus not-run`() {
        assertEquals(5, CategoryPresentation.declaredTotal(
            declaredCount = null, totalCount = 2, notRun = 3))
    }

    // ── passPercent: no fabricated 0% ──

    @Test
    fun `zero of zero yields null not zero percent`() {
        assertNull("0/0 must not render as 0% - that reads as total failure",
            CategoryPresentation.passPercent(passCount = 0, totalCount = 0))
    }

    @Test
    fun `a genuine zero pass rate still reports zero percent`() {
        // Measured and everything failed IS 0% — that must survive.
        assertEquals(0, CategoryPresentation.passPercent(passCount = 0, totalCount = 4))
    }

    @Test
    fun `ordinary rates round down`() {
        assertEquals(100, CategoryPresentation.passPercent(2, 2))
        assertEquals(57, CategoryPresentation.passPercent(4, 7))
    }
}
