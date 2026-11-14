package net.neuralcheck.data.remote

import com.google.gson.Gson
import net.neuralcheck.data.remote.model.AiAnalysisResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * `ai_grade` must never reach the user.
 *
 * The confirmed backend output ships an unmeasured camera as
 * grade="?" + ai_grade="B": the model guessed a B while not a single
 * camera test ran, and the server preserved that opinion for
 * transparency without adopting it. Rendering it would contradict the
 * "غير مقيسة" state printed on the same card and hand the buyer a letter
 * that nothing measured.
 *
 * The same reasoning covers `grade_source` and `reconciled` — provenance
 * for the lab, noise (or worse) for a buyer.
 *
 * This test is a structural guard, not a behavioural one: it greps the
 * two render surfaces so a future edit that starts displaying the field
 * fails here instead of in front of a customer.
 */
class AiGradeNeverRenderedTest {


    private val renderSurfaces = listOf(
        "../app/src/main/java/net/neuralcheck/presentation/screen/ReportScreen.kt",
        "../app/src/main/java/net/neuralcheck/util/ReportPdfGenerator.kt",
        "app/src/main/java/net/neuralcheck/presentation/screen/ReportScreen.kt",
        "app/src/main/java/net/neuralcheck/util/ReportPdfGenerator.kt"
    ).map(::File).filter { it.exists() }

    @Test
    fun `the render surfaces exist so this guard is not vacuous`() {
        assertEquals("expected both render surfaces to resolve", 2, renderSurfaces.size)
    }

    /** Strips `//` line comments and block comments so the guard checks
     *  CODE, not the comments that explain why these fields are withheld. */
    private fun codeOnly(source: String): String = source
        .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), " ")
        .lines()
        .joinToString(" ") { it.substringBefore("//") }

    @Test
    fun `no render surface dereferences aiGrade, gradeSource or reconciled`() {
        for (file in renderSurfaces) {
            val code = codeOnly(file.readText())
            for (prop in listOf("aiGrade", "gradeSource", "reconciled")) {
                assertFalse(
                    "${file.name} dereferences `.$prop` — the model's unadopted " +
                        "letter and the server's provenance flags must never " +
                        "reach the user",
                    code.contains(".$prop")
                )
            }
        }
    }

    @Test
    fun `the comment-stripping guard actually strips comments`() {
        // Without this, the test above would pass vacuously on a file whose
        // only mention is a comment AND would fail on a file that only
        // documents the rule — both happened while writing it.
        val sample = """
            // mentions .aiGrade in a comment
            /* and .gradeSource in a block */
            val x = cat.declaredCount
        """.trimIndent()
        val code = codeOnly(sample)
        assertFalse(code.contains(".aiGrade"))
        assertFalse(code.contains(".gradeSource"))
        assertTrue(code.contains(".declaredCount"))
    }

    @Test
    fun `the unmeasured camera payload keeps its contradictory ai_grade parsed but unused`() {
        val json = """
            {"category_scores":{"camera":{
                "grade":"?","score":0,"pass_count":0,"total_count":0,
                "note":"لا اختبارات متاحة لهذه الفئة",
                "not_tested_count":9,"unavailable_count":0,"declared_count":9,
                "excluded_count":0,"measured":false,
                "grade_source":"unmeasured","ai_grade":"B"}}}
        """.trimIndent()
        val cam = Gson().fromJson(json, AiAnalysisResponse::class.java)
            .categoryScores!!.getValue("camera")
        // Parsed — we keep payload fidelity for the lab.
        assertEquals("B", cam.aiGrade)
        // But the grade the UI reads is the server's, not the model's.
        assertEquals("?", cam.grade)
        assertEquals(false, cam.measured)
    }

    @Test
    fun `conditional fields are absent rather than null-valued`() {
        // The contract states these keys are omitted entirely, so a
        // presence check is the correct test - `reconciled == true`, never
        // `!reconciled`.
        val cat = Gson().fromJson(
            """{"grade":"A","score":90,"pass_count":5,"total_count":5}""",
            net.neuralcheck.data.remote.model.AiCategoryScore::class.java
        )
        assertNull(cat.reconciled)
        assertNull(cat.expectedRange)
        assertNull(cat.aiGrade)
        assertTrue("absent boolean must not read as false", cat.reconciled != false)
    }
}
