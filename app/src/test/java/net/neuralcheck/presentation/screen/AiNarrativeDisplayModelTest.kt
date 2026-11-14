package net.neuralcheck.presentation.screen

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import net.neuralcheck.data.remote.flattenReportDocument
import net.neuralcheck.data.remote.model.AiAnalysisResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.InputStreamReader

/**
 * Order 2026-09-26 (القرار ١٠٢، الملحق السادس، البند ١، القاعدة 41):
 * "اختبار بالعيّنة الحقيقية: الحقلان غائبان عن نموذج العرض، والملخّص
 * والنصائح والملاحظات حاضرة."
 *
 * Uses the REAL `v7-reports-document-sample.json` golden sample (the
 * SAME document whose `narrative.details` at line 1722 was independently
 * re-verified — before this fix — to contain a raw numeric score,
 * "مع أن متوسط الفئات المقيسة بلغ 93.75"), parsed through the exact same
 * [flattenReportDocument] + Gson chain
 * `FirebaseAnalysisService.fetchExistingReport` uses, not a hand-built
 * `AiNarrativeV7`.
 */
class AiNarrativeDisplayModelTest {

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

    private fun realNarrative() = parseResponse(
        flattenReportDocument(loadRawDoc("v7-reports-document-sample.json"))
    ).narrative!!

    @Test
    fun `real V7 sample sanity check — details and verdict both carry a raw numeric score today, in the data layer`() {
        // Confirms the CONTAMINATION this order's own reasoning cites is
        // real, from a live run — not just the order's word — before
        // asserting anything about the display model. AiNarrativeV7 itself
        // (the data layer) is untouched, so these values are STILL here.
        val narrative = realNarrative()
        assertTrue(narrative.details!!.contains("93.75"))
        assertTrue(narrative.verdict!!.isNotBlank())
    }

    @Test
    fun `AiNarrativeDisplayModel does not declare a details or verdict field at all`() {
        val model = realNarrative().toDisplayModel()
        val fieldNames = model::class.java.declaredFields.map { it.name }
        assertFalse("AiNarrativeDisplayModel must not declare details", fieldNames.contains("details"))
        assertFalse("AiNarrativeDisplayModel must not declare verdict", fieldNames.contains("verdict"))
    }

    @Test
    fun `AiNarrativeDisplayModel still carries the real summary and buyer advice`() {
        val model = realNarrative().toDisplayModel()
        assertTrue(
            "the real summary must still flow through",
            model.summary!!.isNotBlank()
        )
        assertFalse(model.summary!!.contains("93.75"))
        val advice = model.buyerAdvice!!
        assertTrue(advice.casual!!.isNotBlank())
        assertTrue(advice.gamer!!.isNotBlank())
        assertTrue(advice.photographer!!.isNotBlank())
    }

    @Test
    fun `what AiAnalysisSectionV7 would actually display — summary and buyer-advice cards render, no details or verdict card, no empty card either`() {
        val model = realNarrative().toDisplayModel()

        // ReportScreen.kt's AiAnalysisSectionV7, post this order: the
        // `narrative?.summary?.takeIf { it.isNotBlank() }?.let { AiSummaryCard(...) }`
        // expression still resolves to a real, shown card.
        val summaryShown = model.summary?.takeIf { it.isNotBlank() } != null
        assertTrue(summaryShown)

        // The `narrative?.details?...` / `narrative?.verdict?...` expressions
        // that used to feed AiDetailsCard/AiVerdictCard no longer exist at
        // all — there is no field to even attempt reading, so neither card
        // is ever constructed (not constructed-then-hidden, and not an
        // empty card either — the call sites themselves are gone).
        val fieldNames = model::class.java.declaredFields.map { it.name }
        assertFalse(fieldNames.contains("details"))
        assertFalse(fieldNames.contains("verdict"))

        // Buyer-advice card: same real gating logic AiAnalysisSectionV7
        // applies (at least one persona non-blank).
        val advice = model.buyerAdvice
        val adviceShown = advice != null && (
            !advice.casual.isNullOrBlank() || !advice.gamer.isNullOrBlank() || !advice.photographer.isNullOrBlank()
        )
        assertTrue(adviceShown)
    }
}
