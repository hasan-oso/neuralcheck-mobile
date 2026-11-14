package net.neuralcheck.util

/**
 * Presentation rules for one category row, shared by the report screen and
 * the PDF so both render an unmeasured category identically.
 *
 * ── Why this exists ──
 *
 * The backend now emits categories whose tests ALL came back not_tested /
 * unavailable: grade "?", score 0, pass/total 0/0, plus a note. Before this
 * helper the app turned that into a verdict of failure three different ways:
 *   • PDF dropped the row entirely (`filter { totalCount > 0 }`) — the
 *     category silently vanished from the document.
 *   • `CategoryHeader.gradeColor` and the PDF's `gradeColor` both map any
 *     unknown letter through `else -> GRADE_D`, i.e. "?" printed RED.
 *   • The AI section showed "0%" for a 0/0 category.
 * "Not measured" and "measured and failed" are opposite findings; a buyer
 * reading red on an untested category is being told something false.
 *
 * Pure + Android-free so the rules are unit-testable.
 */
object CategoryPresentation {

    /**
     * True when nothing in the category was actually measured.
     *
     * [measured] is the authoritative server signal. Reports written before
     * the backend change carry null, so we fall back to the shape that
     * defines the state anyway: an unknown grade with a zero denominator.
     * A 0/0 category with a real letter grade is NOT treated as unmeasured —
     * that would be the server contradicting itself, and we prefer the
     * explicit field.
     */
    fun isUnmeasured(
        measured: Boolean?,
        grade: String?,
        totalCount: Int
    ): Boolean {
        if (measured != null) return !measured
        return totalCount <= 0 && isUnknownGrade(grade)
    }

    /** "?" / blank / null — anything that is not a real A-D letter. */
    fun isUnknownGrade(grade: String?): Boolean {
        val g = grade?.trim()?.uppercase().orEmpty()
        return g !in setOf("A", "B", "C", "D")
    }

    /**
     * Tests declared for this category that produced no measurement.
     *
     * [excludedCount] is the server's own count of that gap and is
     * authoritative when present — it is written on every category by the
     * confirmed contract (gpu: excluded 3 of declared 5 with total 2).
     * Only when it is ABSENT (a report written before the field existed)
     * do we derive declaredCount - totalCount.
     *
     * Never notTestedCount + unavailableCount: the payload does not
     * guarantee those two counters are disjoint, so summing them can
     * overstate the gap.
     *
     * Returns null when there is no gap to report, so callers render
     * nothing rather than a "0 not run" line. That is also what a fully
     * unmeasured category gets (camera arrives excluded_count = 0): the
     * "not measured" badge already carries the whole message, and a
     * coverage line beside it would contradict it.
     */
    fun notRunCount(excludedCount: Int?, declaredCount: Int?, totalCount: Int): Int? {
        // Presence check, not a truthiness check: `excluded_count = 0` is a
        // real answer ("nothing was excluded"), distinct from the key being
        // absent ("this build didn't report it").
        if (excludedCount != null) return excludedCount.takeIf { it > 0 }
        val declared = declaredCount ?: return null
        if (declared <= 0) return null
        val gap = declared - totalCount
        return if (gap > 0) gap else null
    }

    /**
     * Denominator for the coverage line: how many tests the category
     * declares. Falls back to executed + notRun when declaredCount is
     * absent, so the line stays coherent on older payloads.
     *
     * NOTE ON NAMING: totalCount is what was EXECUTED, not what was
     * declared. gpu reads 2/2 — a perfect pass rate over two tests —
     * while declaring five. notRunCount is what corrects that impression,
     * which is why the coverage line renders both numbers.
     */
    fun declaredTotal(declaredCount: Int?, totalCount: Int, notRun: Int): Int =
        declaredCount ?: (totalCount + notRun)

    /**
     * Pass-rate percentage, or null when there is nothing to take a
     * percentage OF. Callers must render the null case as "not measured"
     * text — never as "0%", which reads as a total failure.
     */
    fun passPercent(passCount: Int, totalCount: Int): Int? {
        if (totalCount <= 0) return null
        return (passCount * 100) / totalCount
    }
}
