package net.neuralcheck.util

/**
 * V2 change-vs-prior-scan direction classification, shared by
 * `ReportScreen.ChangeAnalysisCard`. Pulled into a pure function —
 * mirrors [AiV7Presentation]/[AiVerdictPresentation]'s convention — so the
 * four-way distinction below is unit-testable without Compose/Robolectric
 * (this project has no such test harness; see [AiVerdictPresentation]'s
 * class doc).
 *
 * A115: [net.neuralcheck.data.remote.model.ChangeAnalysis.changeDirection]
 * is documented as a closed 3-value enum ("stable"/"improved"/
 * "deteriorated" — that class's own doc, `AiAnalysisResponse.kt:350`), so
 * "stable" is itself a real, legitimate backend value — not an absence
 * marker. But `ChangeAnalysisCard`'s original `when` used a single `else`
 * for BOTH that real "stable" value AND a null/unrecognized one, so a
 * card that CAN legitimately render while `hasSignificantChange == true`
 * (see the call site's gate in `AiAnalysisSection`) with no
 * `changeDirection` at all displayed the exact same green "stable"
 * caption a backend-confirmed no-change scan gets. [DirectionKind.UNKNOWN]
 * is its own bucket, exactly as [AiVerdictPresentation.GradeBucket.UNKNOWN]
 * already does for grades, so that case surfaces distinctly instead of
 * reading as success (project rule 22).
 */
object ChangeAnalysisPresentation {
    enum class DirectionKind { IMPROVED, DETERIORATED, STABLE, UNKNOWN }

    /**
     * Case-insensitive, whitespace-tolerant (matches the pre-existing
     * `changeDirection?.lowercase()` call site it replaces). Null, blank,
     * or any string other than the three documented values is UNKNOWN —
     * never folded into STABLE.
     */
    fun classifyDirection(direction: String?): DirectionKind =
        when (direction?.trim()?.lowercase()) {
            "improved" -> DirectionKind.IMPROVED
            "deteriorated" -> DirectionKind.DETERIORATED
            "stable" -> DirectionKind.STABLE
            else -> DirectionKind.UNKNOWN
        }
}
