package net.neuralcheck.presentation.screen

import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Order 2026-09-26 (القرار ١٠٢، الملحق الثالث، البند ١) — supersedes the
 * previous message on this same subject (which proposed committing a new
 * grade-only string resource): "لا يُضاف نصّ جديد الآن، ولا يُلتزم التركي
 * المقترح. يُضاف اختبار يفشل إن كانت المشاركة مفعّلة (HIDDEN = false)
 * ونصّ report_share_subject_fmt يحمل الدرجة."
 *
 * Reads the REAL [ReportPdfExportButtons.HIDDEN] flag and the REAL
 * `report_share_subject_fmt` string resource VALUES (parsed directly from
 * `strings.xml` / `values-tr/strings.xml` — not a hand-copied literal in
 * this test file, and not `ReportScreen.kt`'s source text) in both
 * languages, formats each exactly the way
 * `Context.getString(R.string.report_share_subject_fmt, grade, scoreText)`
 * would (`String.format` with the same `%1$s`/`%2$s` positional
 * placeholders — Java's formatter treats `%%` as a literal `%` the same
 * way Android's `getString(Int, vararg Any?)` does, so the Turkish
 * string's `%%%2$s` round-trips identically), and asserts the built text
 * does not carry the score.
 *
 * Deliberately passes VACUOUSLY today: `HIDDEN` is `true`, so the
 * assertion block never executes — this file adds zero real coverage
 * while the buttons stay hidden, by design (order's own text: "المشاركة
 * مطفأة، فلا يرى أحد النصّ اليوم"). Its purpose is to fail the INSTANT a
 * future commit flips `HIDDEN` to `false` while `report_share_subject_fmt`
 * still has a second placeholder for the score — forcing that fix (or a
 * translation round-trip) before the button can ship enabled, rather than
 * relying on someone remembering this order months later.
 */
class ShareTextScoreTripwireTest {

    /** Parses `<string name="...">...</string>` out of a real strings.xml
     *  via a standard XML parser (entity-safe — not a fragile regex). */
    private fun readStringResource(relativePath: String, name: String): String {
        val file = listOf(File("../$relativePath"), File(relativePath)).first { it.exists() }
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = doc.getElementsByTagName("string")
        for (i in 0 until nodes.length) {
            val node = nodes.item(i)
            val nameAttr = node.attributes?.getNamedItem("name")?.nodeValue
            if (nameAttr == name) return node.textContent
        }
        error("<string name=\"$name\"> not found in $relativePath")
    }

    @Test
    fun `the resource files and the flag resolve so the guard below is not vacuous by accident`() {
        // Confirms the INPUTS exist and are readable, independent of
        // HIDDEN's value — so a missing/renamed resource fails loudly here
        // instead of the real guard below silently never running for the
        // wrong reason.
        readStringResource("app/src/main/res/values/strings.xml", "report_share_subject_fmt")
        readStringResource("app/src/main/res/values-tr/strings.xml", "report_share_subject_fmt")
    }

    @Test
    fun `share text must not carry the score once the Share button is enabled`() {
        val sampleGrade = "B"
        val sampleScoreText = "78"

        val builtAr = String.format(
            readStringResource("app/src/main/res/values/strings.xml", "report_share_subject_fmt"),
            sampleGrade, sampleScoreText
        )
        val builtTr = String.format(
            readStringResource("app/src/main/res/values-tr/strings.xml", "report_share_subject_fmt"),
            sampleGrade, sampleScoreText
        )

        if (!ReportPdfExportButtons.HIDDEN) {
            assertFalse(
                "report_share_subject_fmt (ar) still carries the score once Share is enabled: $builtAr",
                builtAr.contains(sampleScoreText)
            )
            assertFalse(
                "report_share_subject_fmt (tr) still carries the score once Share is enabled: $builtTr",
                builtTr.contains(sampleScoreText)
            )
        }
        // else: HIDDEN is true today — nobody sees this text, so this
        // assertion is intentionally skipped (see class doc). The two
        // `builtAr`/`builtTr` computations above still ran unconditionally,
        // so a malformed format string (wrong placeholder count/type)
        // still fails this test today via the String.format call itself,
        // not just the day HIDDEN flips.
    }
}
