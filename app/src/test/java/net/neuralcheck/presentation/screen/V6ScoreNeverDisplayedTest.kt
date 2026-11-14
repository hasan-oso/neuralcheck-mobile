package net.neuralcheck.presentation.screen

import net.neuralcheck.util.QrCodeHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Order 2026-09-26 (القرار ١٠٢ البند ٣، القرار ٦ — "`score` لا يُعرض
 * للمستخدم إطلاقاً"). Structural guard, modelled directly on this repo's
 * own `net.neuralcheck.data.remote.AiGradeNeverRenderedTest` idiom: greps
 * the render surfaces (comments stripped) for the specific expressions this
 * order removed, so a future edit that reintroduces any of them fails here
 * instead of in front of a buyer.
 *
 * Deliberately narrow patterns per file (not a blanket ban on the
 * substring "score", which remains legitimate elsewhere in these same
 * files for unrelated, in-scope concepts this order does NOT touch —
 * per-category/per-axis breakdown percentages in `ReportScreen.kt`
 * (`cat.score`, `axis.score` — `CategoryRow`/`AxisCard`, a details view
 * with no accompanying letter to create القرار ٦'s "apparent contradiction"
 * concern) and `ReportPdfGenerator.kt`'s per-category fill fraction
 * (`cat.score`) — flagged in this task's own report as a judgment call,
 * not silently decided; القرار ١٠٢، الملحق الثالث، البند ٣ has since named
 * these too — see that task's own report for the current status).
 *
 * Known, disclosed exception NOT covered by this guard: `ReportScreen.kt`'s
 * `shareReport` function (`s.overallScore` — the Android share-sheet
 * subject text) still shows the score, currently unreachable
 * (`ReportPdfExportButtons.HIDDEN = true`) — covered instead by
 * `ShareTextScoreTripwireTest` (القرار ١٠٢، الملحق الثالث، البند ١), which
 * reads the built text rather than `ReportScreen.kt`'s source.
 *
 * Order 2026-09-26 (القرار ١٠٢، الملحق الثالث، البند ٢، القاعدة 37 —
 * "فحص نصّ الكود لا يثبت ما تعرضه الشاشة"): the `HomeScreen`/
 * `ScanHistoryScreen` guards this file used to carry are REMOVED, not kept
 * alongside a replacement — `HomeViewModel.LastScan`/
 * `ScanHistoryViewModel.CloudScan` no longer DECLARE a `score` field at
 * all (see `HomeAndHistoryV7SummaryTest`'s reflection-based proof), which
 * makes a source-grep on the SCREEN file both weaker (proves the file's
 * text, not the model) and redundant (the field cannot be re-read from a
 * struct that doesn't have it — a compiler error, not a silent
 * regression) than the model-level proof that already lives there.
 *
 * The `ReportScreen`/`ComparisonScreen`/`ReportPdfGenerator` guards below
 * are UNCHANGED for now — deliberately, not overlooked: both read
 * `ScoringResult`/`ScanScoreEntity` directly with NO intermediate
 * display-only struct today (`ScoringResult` is a 14+-consumer domain
 * object per its own doc — Room persistence, backend sync, comparison,
 * export, PDF; `ComparisonScreen.kt` reads `ScanScoreEntity` — the Room
 * row — directly, `ViewModels.kt:1135-1138`). Trimming the field from
 * either needs either a new display-only view layer or a change to a
 * core domain/persistence object with wide blast radius — this task's own
 * report flags this as unresolved rather than guessing.
 */
class V6ScoreNeverDisplayedTest {

    private fun resolve(path: String): File =
        listOf(File("../$path"), File(path)).first { it.exists() }

    private val reportScreen = resolve("app/src/main/java/net/neuralcheck/presentation/screen/ReportScreen.kt")
    private val comparisonScreen = resolve("app/src/main/java/net/neuralcheck/presentation/screen/ComparisonScreen.kt")
    private val neuralCheckComponents = resolve("app/src/main/java/net/neuralcheck/presentation/components/NeuralCheckComponents.kt")
    private val reportPdfGenerator = resolve("app/src/main/java/net/neuralcheck/util/ReportPdfGenerator.kt")
    private val qrCodeHelper = resolve("app/src/main/java/net/neuralcheck/util/QrCodeHelper.kt")

    @Test
    fun `all five files resolve so the guards below are not vacuous`() {
        assertEquals(
            5,
            listOf(
                reportScreen, comparisonScreen,
                neuralCheckComponents, reportPdfGenerator, qrCodeHelper
            ).count { it.exists() }
        )
    }

    /** Same comment-stripping idiom as `AiGradeNeverRenderedTest.codeOnly`. */
    private fun codeOnly(source: String): String = source
        .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), " ")
        .lines()
        .joinToString(" ") { it.substringBefore("//") }

    @Test
    fun `ReportScreen's GradeSummaryCard no longer dereferences scoring-overallScore`() {
        // Narrower than banning `overallScore` outright in this file — see
        // this class's own doc for the disclosed `s.overallScore`
        // (share-text) exception, deliberately not covered here.
        assertFalse(codeOnly(reportScreen.readText()).contains("scoring.overallScore"))
    }

    @Test
    fun `ComparisonScreen no longer dereferences overallScore anywhere`() {
        assertFalse(codeOnly(comparisonScreen.readText()).contains("overallScore"))
    }

    @Test
    fun `NeuralCheckComponents' GradeBadge no longer declares a score parameter`() {
        val code = codeOnly(neuralCheckComponents.readText())
        assertFalse(code.contains("score: Float?"))
        assertFalse(code.contains("score.toInt()"))
    }

    @Test
    fun `ReportPdfGenerator no longer draws overallScore under the grade circle`() {
        assertFalse(codeOnly(reportPdfGenerator.readText()).contains("overallScore"))
    }

    @Test
    fun `QrCodeHelper's generated QR content never carries an s= param, even with a real grade`() {
        // Real behavioural proof, not just a source grep — calls the actual
        // (now internal, extracted for testability) content builder.
        val content = QrCodeHelper.buildScanQrContent("11111111-2222-3333-4444-555555555555", "B")
        assertEquals("neuralcheck://scan/11111111-2222-3333-4444-555555555555?g=B", content)
        assertFalse("g= must survive — only s= (score) is removed", content.contains("s="))
        assertTrue(content.contains("g=B"))
    }

    @Test
    fun `QrCodeHelper's generateQrBitmap signature no longer accepts a score argument`() {
        // Compile-time guard, same technique as GradeBadge above: if a
        // future edit reintroduces a `score` parameter, this source-level
        // check goes stale but the signature itself would still need a
        // caller change to break — recorded here as a paired, explicit
        // check on the function's declared parameter list text.
        val sig = codeOnly(qrCodeHelper.readText())
            .substringAfter("fun generateQrBitmap(")
            .substringBefore(")")
        assertFalse(sig.contains("score"))
    }
}
