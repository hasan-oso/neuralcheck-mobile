package net.neuralcheck.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the A115 fix for `ReportScreen.kt`'s `ChangeAnalysisCard`: the
 * original `when (change.changeDirection?.lowercase()) { "improved" ->
 * ...; "deteriorated" -> ...; else -> StatusPass to change_analysis_stable
 * }` folded a null/unrecognized `changeDirection` into the same branch as
 * the real "stable" value, so an absent field (possible even while
 * `hasSignificantChange == true` — the card's call-site gate in
 * `AiAnalysisSection` does not require `changeDirection` to be non-null)
 * rendered the same green "stable" caption as a backend-confirmed
 * no-change scan.
 *
 * NEW tests — no prior test in this codebase exercised `changeDirection`
 * handling at all (confirmed by search before writing this: no test file
 * named `ChangeAnalysis*` existed, and at the time this project had neither Robolectric nor a
 * Compose-test dependency to exercise `ChangeAnalysisCard` itself (both are test dependencies since 3284cca) — see
 * `AiVerdictPresentationTest`'s class doc for the same constraint), so
 * nothing here replaces a test that pinned the old `else -> STABLE`
 * behaviour; there was none to replace (project rule 23's "if none
 * existed, say it's new").
 *
 * Each of the three real backend values gets its own case, plus explicit
 * absence (null) and an unrecognized value — project rule 30's "every
 * enumerated value is tested with all its values, plus explicit absence
 * and an unknown value".
 */
class ChangeAnalysisPresentationTest {

    @Test
    fun `improved is recognised, case-insensitively`() {
        assertEquals(
            ChangeAnalysisPresentation.DirectionKind.IMPROVED,
            ChangeAnalysisPresentation.classifyDirection("improved")
        )
        assertEquals(
            ChangeAnalysisPresentation.DirectionKind.IMPROVED,
            ChangeAnalysisPresentation.classifyDirection("IMPROVED")
        )
    }

    @Test
    fun `deteriorated is recognised, case-insensitively`() {
        assertEquals(
            ChangeAnalysisPresentation.DirectionKind.DETERIORATED,
            ChangeAnalysisPresentation.classifyDirection("deteriorated")
        )
        assertEquals(
            ChangeAnalysisPresentation.DirectionKind.DETERIORATED,
            ChangeAnalysisPresentation.classifyDirection("Deteriorated")
        )
    }

    @Test
    fun `stable is recognised, case-insensitively - it is a real backend value, not an absence marker`() {
        assertEquals(
            ChangeAnalysisPresentation.DirectionKind.STABLE,
            ChangeAnalysisPresentation.classifyDirection("stable")
        )
        assertEquals(
            ChangeAnalysisPresentation.DirectionKind.STABLE,
            ChangeAnalysisPresentation.classifyDirection("STABLE")
        )
    }

    @Test
    fun `surrounding whitespace is tolerated`() {
        assertEquals(
            ChangeAnalysisPresentation.DirectionKind.STABLE,
            ChangeAnalysisPresentation.classifyDirection("  stable  ")
        )
    }

    @Test
    fun `null changeDirection is UNKNOWN, not silently STABLE`() {
        // The exact bug this fix targets: a card that can render while
        // hasSignificantChange == true (see AiAnalysisSection's gate) with
        // no changeDirection at all must not display the same green
        // "stable" caption a real no-change scan gets.
        assertEquals(
            ChangeAnalysisPresentation.DirectionKind.UNKNOWN,
            ChangeAnalysisPresentation.classifyDirection(null)
        )
    }

    @Test
    fun `blank changeDirection is UNKNOWN, not STABLE`() {
        assertEquals(
            ChangeAnalysisPresentation.DirectionKind.UNKNOWN,
            ChangeAnalysisPresentation.classifyDirection("")
        )
        assertEquals(
            ChangeAnalysisPresentation.DirectionKind.UNKNOWN,
            ChangeAnalysisPresentation.classifyDirection("   ")
        )
    }

    @Test
    fun `an unrecognized changeDirection is UNKNOWN, not STABLE`() {
        // A value that is neither absent nor one of the three documented
        // strings — e.g. a future backend addition, or a typo'd payload —
        // must still be treated as unknown, never silently painted as
        // "stable"/green.
        assertEquals(
            ChangeAnalysisPresentation.DirectionKind.UNKNOWN,
            ChangeAnalysisPresentation.classifyDirection("unchanged")
        )
        assertEquals(
            ChangeAnalysisPresentation.DirectionKind.UNKNOWN,
            ChangeAnalysisPresentation.classifyDirection("worse")
        )
    }
}
