package net.neuralcheck.data.remote

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import net.neuralcheck.R
import net.neuralcheck.data.remote.model.AiAnalysisResponse
import net.neuralcheck.data.remote.model.AiPipeline
import net.neuralcheck.util.AiV7Presentation
import net.neuralcheck.util.AiVerdictPresentation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File
import java.io.InputStreamReader

/**
 * Order 2026-09-25 (القرار ٩٥ البند ٤) — "قارئ الحرف المعدَّل يُختبر بالعيّنة
 * في التطبيق والموقع، بالشكلين B وC. وفي التطبيق B وحده": the app never
 * calls `verifyReport` (checked: `git grep -n "verifyReport" app/src/main`
 * returns zero hits — the two mentions in `AiAnalysisResponse.kt`/
 * `V7RootFieldPresence.kt` are comments only), so only the reports-document
 * shape (B) is exercised here.
 *
 * Full real path, traced end to end (rule 30 — proving what the reader
 * PRODUCES, not merely that a field is reachable):
 *
 *   raw `reports/{scanId}` document
 *   (`v7-reports-document-sample-security-downgrade.json`, copied verbatim
 *   from a neuralcheck-backend contract handoff, SHA256-verified)
 *   → [flattenReportDocument] → the SAME Gson round-trip
 *   [net.neuralcheck.data.remote.FirebaseAnalysisService.fetchExistingReport]
 *   performs (`gson.toJson(flat)` then
 *   `gson.fromJson(json, AiAnalysisResponse::class.java)` — see that
 *   function, ~lines 212-220) → `categories.security`
 *   → [AiVerdictPresentation.isKnownGrade] / [AiV7Presentation.classifyLetterSource]
 *   (the exact two calls `ReportScreen.kt`'s `AiCategoryRowV7` makes,
 *   lines 2635 and 2646) → the letterSource row a buyer actually sees for
 *   this category.
 *
 * Known, disclosed gap (same discipline as
 * `net.neuralcheck.data.remote.model.V7FullDocumentReadCoverageTest`'s own
 * doc): `AiCategoryRowV7`/`AiV7LetterSourceRow` in `ReportScreen.kt` are
 * `private` `@Composable` functions, and when this was written the module lacked Robolectric or
 * Compose-UI-test infrastructure (checked then: no `robolectric`/
 * `compose...test` entry under `testImplementation` in `app/build.gradle.kts`;
 * both have been there since 3284cca, and no test renders these two). This task's own instruction is also to leave
 * reader code untouched unless a bug is found (then stop and report, not
 * fix) — so no visibility was changed to make them directly callable
 * either. This test therefore:
 *   (a) calls every REAL, unmodified production function on the path —
 *       the flatten, the Gson parse, and BOTH classifiers — with the real
 *       security-downgrade document; and
 *   (b) for the final text-assembly step, reproduces — verbatim, each
 *       cited by its exact `ReportScreen.kt` line — the label's
 *       string-resource id and the reason fallback expression, resolving
 *       the ONE string resource that route needs via a mocked `Context`,
 *       the established idiom in this suite (see
 *       `ReportPdfGeneratorV7StateLineTest`).
 * It does not execute Compose composition and does not prove a pixel was
 * painted — only that every value reaching that point, and the text those
 * real expressions would produce from it, is correct. If a future change
 * to those `ReportScreen.kt` lines is not mirrored here, this test goes
 * stale silently (rule 39's concern) — there is no compiler tie between a
 * private `@Composable` and a JVM test.
 *
 * ~~Order 2026-09-25 (القرار ٩٥ البند ٤), ORIGINAL version of this file —
 * kept struck-through, not deleted (rule 8): the reason-mirroring test
 * below also mirrored a THIRD expression, the evidence join-then-blank-
 * check (`ReportScreen.kt:2786` join, `:2844` blank-check), and asserted
 * `displayedEvidence` equalled the joined raw evidence string
 * ("categoryStates.testResults[61].outcome") — i.e. it proved the raw
 * evidence WAS displayed.~~
 *
 * ~~Order 2026-09-25 (القرار ٩٩ البند ٤) — rule 23 replacement, kept
 * struck-through not deleted (rule 8): removed the evidence-mirroring
 * assertion above (no expression left to mirror — AiV7LetterSourceRow
 * dropped the `evidence` parameter entirely), kept the reason-mirroring
 * assertion (security was STILL `LLM_ADJUSTED` at that point, letter D,
 * with a real adjustmentReason), and added the structural guard test
 * below.~~
 *
 * **Order 2026-09-25 (القرار ٩٨ البند ١ / القرار ٩٩ البند ١، backend@057cf43)
 * — rule 23 replacement, second round.** The backend now only writes
 * `LLM_ADJUSTED` when the adjustment actually CHANGES the letter (`49c0e40`).
 * security's deterministic letter was already D — the old "adjustment" was
 * accepted but had no effect, so it is no longer written as an adjustment
 * at all: `security` now reads `DETERMINISTIC`, `adjustmentReason`/
 * `adjustmentEvidence` both `null` in the display shape, and
 * `aiAudit.adjustments[1].noEffectReason: "ACCEPTED_NO_EFFECT"` server-side
 * (unread by this app — confirmed: `git grep -n "aiAudit" app/src/main`
 * returns zero hits). This file's two tests below are REWRITTEN (not
 * appended to) to assert that real, current shape. Real, accepted
 * `LLM_ADJUSTED` coverage — a category whose letter DOES change — now lives
 * in the new `v7-reports-document-sample-cpu-c06-downgrade.json` fixture
 * and its own test file, `V7CpuLetterSourceReaderTest.kt` (القرار ٩٩ البند ٢).
 */
class V7SecurityLetterSourceReaderTest {

    private fun loadRawDoc(fileName: String): Map<String, Any?> {
        val stream = javaClass.classLoader!!.getResourceAsStream("contract/$fileName")
            ?: error("Fixture not found on classpath — expected at app/src/test/resources/contract/$fileName")
        val text = InputStreamReader(stream, Charsets.UTF_8).readText()
        val type = object : TypeToken<Map<String, Any?>>() {}.type
        return Gson().fromJson(text, type)
    }

    private fun parseResponse(flat: Map<String, Any?>): AiAnalysisResponse {
        val gson = Gson()
        return gson.fromJson(gson.toJson(flat), AiAnalysisResponse::class.java)
    }

    private fun fakeContext(): Context {
        val context = mock<Context>()
        // Order 2026-10-01 (A7): "النموذج" ⇐ "الذكاء الاصطناعي".
        whenever(context.getString(R.string.report_v7_letter_source_llm_adjusted))
            .thenReturn("حرف عدّله الذكاء الاصطناعي")
        // Order 2026-09-25 (القرار ٩٨ البند ١) — security is DETERMINISTIC
        // now, so this test file needs this resource stubbed too.
        whenever(context.getString(R.string.report_v7_letter_source_deterministic))
            .thenReturn("حرف محسوب مباشرة من نتائج القياس")
        return context
    }

    @Test
    fun `resync 2026-09-25 (القرار ٩٨ البند ١، backend@057cf43) — security-downgrade sample B now reads DETERMINISTIC, no-effect adjustment`() {
        val data = loadRawDoc("v7-reports-document-sample-security-downgrade.json")
        assertTrue(
            "a complete V7 doc with overall.letter present must pass fetchExistingReport's " +
                "own cache gate, or the real reader would never reach the parse branch at all",
            isCachedReportComplete(data)
        )
        val response = parseResponse(flattenReportDocument(data))
        assertEquals(AiPipeline.V7_FINDINGS_BASED, response.pipeline)

        val security = response.categories!!.getValue("security")
        // Rule 23 — resynced 2026-09-30 from neuralcheck-backend@c7446a4:
        // was PARTIAL/coverage 0.9 before 11 tests became non-health-bearing;
        // security is now MEASURED/coverage 1.0. Letter (D) is unaffected.
        assertEquals("MEASURED", security.state)
        assertEquals("D", security.letter)
        assertEquals("DETERMINISTIC", security.letterSource)
        assertNull("no adjustment was surfaced — accepted but no-effect, so no reason to report", security.adjustmentReason)
        assertNull("no adjustment was surfaced — accepted but no-effect, so no evidence to report", security.adjustmentEvidence)
    }

    @Test
    fun `resync 2026-09-25 — security's letter-source row now shows the DETERMINISTIC label alone, no reason and no evidence`() {
        val data = loadRawDoc("v7-reports-document-sample-security-downgrade.json")
        val response = parseResponse(flattenReportDocument(data))
        val security = response.categories!!.getValue("security")

        // ReportScreen.kt:2635 — AiCategoryRowV7's own `known` gate (القرار
        // ٨٧ البند ٤: the letterSource row is hidden entirely for a category
        // with no real letter). D is a real grade, so it renders here.
        val known = AiVerdictPresentation.isKnownGrade(security.letter)
        assertTrue("security has a real letter (D) — its letterSource row must render", known)

        // ReportScreen.kt:2646
        val kind = AiV7Presentation.classifyLetterSource(security.letterSource)
        assertEquals(AiV7Presentation.LetterSourceKind.DETERMINISTIC, kind)

        // ReportScreen.kt:2824-2825 — the label shown beside the ⚙️ icon for
        // the DETERMINISTIC branch specifically.
        val context = fakeContext()
        val label = context.getString(R.string.report_v7_letter_source_deterministic)
        assertEquals("حرف محسوب مباشرة من نتائج القياس", label)

        // ReportScreen.kt:2843's `if (kind == LLM_ADJUSTED)` block (reason,
        // and before القرار ٩٩ البند ٤ also evidence) is skipped entirely
        // for DETERMINISTIC — there is no reason/evidence expression to
        // mirror any more for this sample. The underlying field is null,
        // proven directly by the test above this one.
        assertNull(
            "DETERMINISTIC carries no adjustment reason to display — the " +
                "row's LLM_ADJUSTED-only reason block is never reached",
            security.adjustmentReason
        )

        // The letter itself, shown in the grade Surface right above this row
        // (ReportScreen.kt:2673-2683 — `known -> cat.letter!!.trim().uppercase()`).
        assertEquals("D", security.letter?.trim()?.uppercase())
    }

    // ── Order 2026-09-25 (القرار ٩٩ البند ٤) — structural guard, modelled
    // directly on this repo's own `AiGradeNeverRenderedTest` idiom: proves
    // "لا يُعرض الدليل في الموضعين" durably (fails on any future
    // reintroduction), not just for this one sample's shape. ──

    private val renderSurfaces = listOf(
        "../app/src/main/java/net/neuralcheck/presentation/screen/ReportScreen.kt",
        "../app/src/main/java/net/neuralcheck/util/ReportPdfGenerator.kt",
        "app/src/main/java/net/neuralcheck/presentation/screen/ReportScreen.kt",
        "app/src/main/java/net/neuralcheck/util/ReportPdfGenerator.kt"
    ).map(::File).filter { it.exists() }

    @Test
    fun `the two evidence render surfaces exist so the guard below is not vacuous`() {
        assertEquals("expected both render surfaces to resolve", 2, renderSurfaces.size)
    }

    /** Strips `//` line comments and block comments — same helper as
     *  `AiGradeNeverRenderedTest`'s, duplicated here rather than shared
     *  because that class is `private`-scoped test-local code, not a
     *  shared test util (no test-fixtures source set in this module). */
    private fun codeOnly(source: String): String = source
        .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), " ")
        .lines()
        .joinToString(" ") { it.substringBefore("//") }

    @Test
    fun `neither ReportScreen nor ReportPdfGenerator dereferences adjustmentEvidence any more`() {
        for (file in renderSurfaces) {
            val code = codeOnly(file.readText())
            assertFalse(
                "${file.name} dereferences `.adjustmentEvidence` — القرار ٩٩ البند ٤ " +
                    "requires the raw evidence reference to never reach the screen or the " +
                    "PDF; the model field itself may still be READ elsewhere (e.g. this " +
                    "test file, or a future non-display use), just not by these two " +
                    "render surfaces",
                code.contains(".adjustmentEvidence")
            )
        }
    }
}
