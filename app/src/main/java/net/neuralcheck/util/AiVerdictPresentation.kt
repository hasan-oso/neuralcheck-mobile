package net.neuralcheck.util

import net.neuralcheck.data.remote.model.AiPipeline

/**
 * Presentation rule for the AI verdict grade indicator — shared by the V6
 * verdict card (`ai.grade`) and the V7 overall-letter card (`overall.letter`).
 * Both are the SAME underlying distinction (is this string one of the
 * backend's real A/B/C/D letters, or is it absent/garbage), so it is typed
 * here once rather than re-implemented per call site (project rule: a
 * literal/classification a distinction is built on lives in exactly one
 * place — the "v7_findings_based" seven-file drift, A102, is the incident
 * that produced that rule; this is the same shape of risk for a smaller
 * value set).
 *
 * ── Why this exists ──
 *
 * `AiAnalysisResponse.grade` is documented "A/B/C/D — null when backend
 * omits" (see that file). `ReportScreen.AiVerdictCard` used to coalesce a
 * missing grade with `ai.grade ?: "?"` at the call site — a required
 * field's absence silently turned into a string literal indistinguishable,
 * to any later reader of that value, from a real (if unknown) grade. The
 * card never actually rendered the letter as text before this — the
 * literal only fed a colour lookup — so the practical blast radius was
 * small, but the pattern is exactly what project rule 22 (absence must
 * never be read as a value) forbids, and the same `?: "?"` shape
 * independently existed one layer up in `AiScoringEngine.kt` for
 * `ScoringResult.overallGrade` (flagged here as "out of scope for this
 * phase" when this class was written in Phase 3-b — fixed in Phase 3-c by
 * [resolveOverallGrade] below, once `ScoringResult.overallGrade`'s only
 * unbuilt consumer, `ReportPdfGenerator`, was unblocked).
 *
 * Pure + Android-free so the rule is unit-testable without Compose/Robolectric.
 */
object AiVerdictPresentation {
    private val KNOWN_GRADES = setOf("A", "B", "C", "D")

    /**
     * True when [grade] is one of the backend's real letter grades ("A"..
     * "D", case-insensitive, surrounding whitespace tolerated). False for
     * null, blank, or any other string (including a literal "?", should a
     * future backend ever send one) — callers must render an explicit
     * "grade unavailable" state for the false case, never fall back to
     * displaying the raw string as if it were a grade.
     */
    fun isKnownGrade(grade: String?): Boolean =
        grade != null && grade.trim().uppercase() in KNOWN_GRADES

    /**
     * Phase 3-c — the "flagged separately, not fixed here" gap this class's
     * own doc named above, fixed now because its only unbuilt consumer
     * (ReportPdfGenerator) is unblocked this phase.
     *
     * `AiAnalysisResponse.grade` is documented "A/B/C/D — null when backend
     * omits", but it is ALSO structurally null for every V7 response —
     * [net.neuralcheck.data.remote.model.AiAnalysisResponse]'s own field
     * doc: "AiPipeline.V7_FINDINGS_BASED -> ... grade/score/summary/...
     * above are NOT populated for this shape". The real letter lives at
     * `overall.letter` instead ([net.neuralcheck.data.remote.model.AiOverallV7.letter],
     * "finalLetter — the letter AFTER any model adjustment").
     *
     * Before this function existed, `AiScoringEngine.kt:393` read
     * `response.grade ?: "?"` unconditionally, so
     * `ScoringResult.overallGrade` (feeds `GradeBadge`/`GradeSummaryCard`/
     * [net.neuralcheck.util.ReportPdfGenerator]'s grade hero) printed the
     * "?" sentinel for every single V7 report, even though a real letter
     * was sitting in `overall.letter` the whole time — the SAME absence
     * (V7 not populating the flat field), transformed into "?" a second
     * time by a second call site, independently of the
     * `AiVerdictCard`/`AiCategoryRowV7` fix this class already made for
     * the on-screen report.
     *
     * [pipeline]/[v6Grade]/[v7Letter] are passed in rather than the whole
     * response/result object so this stays a pure function, callable from
     * a plain unit test without constructing an [AiScoringEngine] (which
     * needs Context/Firebase/Room DI and has no existing test harness).
     *
     * Returns the "?" sentinel — same one used everywhere else this class's
     * callers already treat as "grade unavailable" ([isKnownGrade] returns
     * false for it, [net.neuralcheck.util.ReportPdfGenerator.gradeColor]
     * renders it neutral) — when the pipeline-appropriate field is itself
     * null or blank. Never falls back to the OTHER shape's field: a V7
     * response's `grade` being accidentally non-null (it never is, per the
     * contract, but nothing enforces that at the type level) must not
     * silently override `overall.letter`.
     */
    fun resolveOverallGrade(pipeline: String?, v6Grade: String?, v7Letter: String?): String {
        val raw = if (pipeline == AiPipeline.V7_FINDINGS_BASED) v7Letter else v6Grade
        return raw?.takeIf { it.isNotBlank() } ?: "?"
    }

    /**
     * Which of the backend's four real letter grades — or the neutral
     * "unknown" bucket — [grade] maps to. Exists so every grade-to-colour
     * (or grade-to-caption) `when` block in the app shares ONE A/B/C/D/
     * unknown classification instead of each re-deriving it with its own
     * `when (grade) { "A" -> ...; "B" -> ...; "C" -> ...; else -> <the
     * D-shaped thing> }` — exactly the shape that turned an unrecognised
     * grade (including the literal "?" sentinel [resolveOverallGrade]
     * itself returns) into a D-grade failure signal at two call sites this
     * function replaces: `ReportScreen.kt`'s `GradeSummaryCard` (the score
     * number's colour fell through to `GradeD`/red) and
     * `ReportPdfGenerator`'s `gradeDescription` (the PDF caption under the
     * grade circle fell through to the "ضعيف — مشاكل جوهرية" / D-grade
     * description text). Both bugs existed despite [isKnownGrade] and this
     * class already existing — each call site had its own unreviewed `when`
     * instead of using it.
     *
     * Deliberately returns an enum, not a `Color`/string-resource id: this
     * file stays Android/Compose-free (see the class doc) so it is
     * unit-testable as plain JVM code (when written the test config of `app/build.gradle.kts`
     * had neither Robolectric nor a Compose-test dependency; both
     * are test dependencies since 3284cca). Each caller still owns its own
     * bucket -> `Color` / bucket -> string-resource mapping, written as an
     * EXHAUSTIVE `when` over this enum (no `else` branch) — the compiler
     * then refuses to build if a caller ever forgets the `UNKNOWN` case,
     * which is what let the bug happen in the first place.
     */
    enum class GradeBucket { A, B, C, D, UNKNOWN }

    fun gradeBucket(grade: String?): GradeBucket {
        if (!isKnownGrade(grade)) return GradeBucket.UNKNOWN
        return when (grade!!.trim().uppercase()) {
            "A" -> GradeBucket.A
            "B" -> GradeBucket.B
            "C" -> GradeBucket.C
            else -> GradeBucket.D // isKnownGrade guarantees this is "D" here
        }
    }
}
