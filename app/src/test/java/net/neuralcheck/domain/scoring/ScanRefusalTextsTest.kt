package net.neuralcheck.domain.scoring

import net.neuralcheck.data.remote.ReasonCodeRead
import net.neuralcheck.data.remote.SessionErrorCode
import net.neuralcheck.presentation.viewmodel.CREATE_TEXTS
import net.neuralcheck.presentation.viewmodel.SessionText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The matrix `reasonCode x context` and the four approved texts - A-SUB wave 1 (order 2026-10-17; user decisions 2, 3, 7; rule 30: every enumerated value is classified, not one
 * from a sample; decision 90: the texts are approved verbatim, written out HERE and compared with the resources, never read from the code under test).
 *
 * The expected matrix is written out below from `app-engineer-asub-round1-fcb1d13-2026-10-17.md` section 4.3 (lines 228-237); a cell that is `null` "does not occur" by the contract
 * relayed from neuralcheck-backend `PROTOCOL_CONTRACT.md` 6-d-1 at `47ec36e` (not verified from this repository, branch `asub-wave1`, not deployed).
 */
class ScanRefusalTextsTest {

    private companion object {
        private fun resFile(relative: String): File {
            val fromModule = File(relative)
            if (fromModule.exists()) return fromModule
            val fromRoot = File("app", relative)
            check(fromRoot.exists()) { "strings file not found from '${File(".").absolutePath}': $relative" }
            return fromRoot
        }

        private fun loadKeys(relative: String): Map<String, String> {
            val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(resFile(relative))
            val nodes = doc.getElementsByTagName("string")
            val out = mutableMapOf<String, String>()
            for (i in 0 until nodes.length) {
                val node = nodes.item(i)
                val name = node.attributes.getNamedItem("name")?.nodeValue ?: continue
                out[name] = node.textContent
            }
            return out
        }
    }

    private val ar = loadKeys("src/main/res/values/strings.xml")
    private val tr = loadKeys("src/main/res/values-tr/strings.xml")

    /** The four approved texts, verbatim (K1-K4, order of 2026-10-17 lines 24-27), and the existing no-credit text that K3's cousin reuses. */
    private val approved: Map<String, Pair<String, String>> = mapOf(
        "shop_subscription_expired" to ("اشتراك المحلّ انتهى، تواصل مع الإدارة" to "Mağaza aboneliği sona erdi. Lütfen yönetimle iletişime geçin."),
        "subscription_expired" to ("انتهى اشتراكك، يرجى التجديد" to "Aboneliğiniz sona erdi. Lütfen yenileyin."),
        "shop_no_credit" to ("رصيد فحوصات المحلّ انتهى، تواصل مع الإدارة" to "Mağazanın tarama hakkı kalmadı. Lütfen yönetimle iletişime geçin."),
        "free_device_used" to ("سبق استخدام الفحص المجاني لهذا الجهاز" to "Bu cihaz için ücretsiz tarama daha önce kullanıldı."),
        // 2026-10-20 (order app-engineer-5; the user's decision of that date: the Arabic as it is, the Turkish approved): DECISIONS-FOR-HASAN-2026-10-18.md section 2, row 1
        "scan_report_not_available" to ("تعذّر إتمام الطلب" to "İşlem tamamlanamadı")
    )

    @Test
    fun `every key has a non-empty Arabic and Turkish text, and the approved ones are verbatim`() {
        val keys = ScanRefusalText.values().map { it.key }
        for (key in keys) {
            assertTrue("$key is missing or blank in values/strings.xml", ar[key]?.isNotBlank() == true)
            assertTrue("$key is missing or blank in values-tr/strings.xml", tr[key]?.isNotBlank() == true)
        }
        for ((key, texts) in approved) {
            assertTrue("$key is one of the keys the matrix names", key in keys)
            assertEquals("$key (ar)", texts.first, ar[key])
            assertEquals("$key (tr)", texts.second, tr[key])
        }
        assertEquals(
            "the existing no-credit text is the one the personal no-credit refusal shows (K3's cousin, not a new text)",
            "لا يوجد رصيد فحوصات. يرجى شراء اشتراك." to "Tarama hakkınız kalmadı. Lütfen abonelik satın alın.",
            ar["subscription_no_scans"] to tr["subscription_no_scans"]
        )
    }

    /**
     * The two texts of the order of 2026-10-20, written out from `DECISIONS-FOR-HASAN-2026-10-18.md` section 2, rows 1 and 2 (the user's decision of 2026-10-20: the Arabic as it is, the Turkish approved) - never read from the
     * code under test. Rule 24: one source for each - a key in the resources that one reader names - and no copy of either sentence in any Kotlin source.
     */
    private val textsOf20261020: Map<String, Pair<String, String>> = mapOf(
        "scan_report_not_available" to ("تعذّر إتمام الطلب" to "İşlem tamamlanamadı"),
        "shop_plan_required" to ("هذه الميزة غير متاحة في باقتك الحالية" to "Bu özellik mevcut paketinizde kullanılamıyor")
    )

    @Test
    fun `the two texts of 2026-10-20 are verbatim in both languages, the text of one key each, read by one place each, and spelled in no Kotlin source (rule 24)`() {
        val sources = resFile("src/main/java").walkTopDown().filter { it.isFile && it.extension == "kt" }.map { it.name to it.readText() }.toList()
        assertTrue("the main sources were found: ${sources.size}", sources.size > 100)
        for ((key, texts) in textsOf20261020) {
            assertEquals("$key (ar)", texts.first, ar[key])
            assertEquals("$key (tr)", texts.second, tr[key])
            assertEquals("the Arabic sentence of $key is the text of one key only", 1, ar.values.count { it == texts.first })
            assertEquals("the Turkish sentence of $key is the text of one key only", 1, tr.values.count { it == texts.second })
            for ((name, code) in sources) {
                assertTrue("$name spells the Arabic text of $key", texts.first !in code)
                assertTrue("$name spells the Turkish text of $key", texts.second !in code)
            }
            assertEquals("$key is named by exactly one place in the main sources", 1, sources.sumOf { (_, code) -> code.split("R.string.$key").size - 1 })
        }
        assertEquals("scan_report_not_available", ScanRefusalText.SCAN_REPORT_NOT_AVAILABLE.key)
        assertEquals("shop_plan_required", SessionText.CREATE_PLAN_REQUIRED.key)
        assertEquals("the create's table reads the plan key for the plan code", SessionText.CREATE_PLAN_REQUIRED, CREATE_TEXTS[SessionErrorCode.SHOP_PLAN_REQUIRED])
    }

    @Test
    fun `each approved key is declared exactly once in each resource file (rule 24)`() {
        val keys = ScanRefusalText.values().map { it.key }
        assertEquals("one key per text", keys.size, keys.toSet().size)
        for (key in approved.keys) {
            assertEquals("$key is declared once in values/strings.xml", 1, resFile("src/main/res/values/strings.xml").readText().split("name=\"$key\"").size - 1)
            assertEquals("$key is declared once in values-tr/strings.xml", 1, resFile("src/main/res/values-tr/strings.xml").readText().split("name=\"$key\"").size - 1)
        }
    }

    /** The matrix, written out: key of the text shown for each (context, code), or null = no cell (the text of before + `combination=unexpected` or `no-approved-text`). */
    private val expectedCell: Map<Pair<RefusalContext, SessionErrorCode>, String> = mapOf(
        (RefusalContext.PERSONAL to SessionErrorCode.NO_CREDIT) to "subscription_no_scans",
        (RefusalContext.PERSONAL to SessionErrorCode.SUBSCRIPTION_EXPIRED) to "subscription_expired",
        (RefusalContext.PERSONAL to SessionErrorCode.FREE_DEVICE_USED) to "free_device_used",
        (RefusalContext.SESSION to SessionErrorCode.FREE_DEVICE_USED) to "free_device_used",
        (RefusalContext.SESSION to SessionErrorCode.SHOP_NO_CREDIT) to "shop_no_credit",
        (RefusalContext.SESSION to SessionErrorCode.SHOP_SUBSCRIPTION_EXPIRED) to "shop_subscription_expired",
        // 2026-10-20: the neutral stored-report refusal exists on the account's own scan only; the session path's code is the server's and has no cell
        (RefusalContext.PERSONAL to SessionErrorCode.SCAN_REPORT_NOT_AVAILABLE) to "scan_report_not_available"
    )

    /** Rule 23: REPLACES `the matrix has exactly the six cells of section 4-3` (it fixed a six-cell matrix and so failed the day a seventh cell was approved). */
    @Test
    fun `the matrix has exactly the six cells of section 4-3 and the one of 2026-10-20, and no other, for every code in both contexts`() {
        for (context in RefusalContext.values()) {
            for (code in SessionErrorCode.values()) {
                assertEquals("$context x ${code.wire}", expectedCell[context to code], analysisRefusalText(context, code)?.key)
            }
        }
        assertEquals(7, ANALYSIS_REFUSAL_TEXTS.values.sumOf { it.size })
    }

    @Test
    fun `every code in every context is classified - expected, unexpected or no approved text - and the classification matches the cells`() {
        for (context in RefusalContext.values()) {
            for (code in SessionErrorCode.values()) {
                val combination = refusalCombination(context, ReasonCodeRead.Known(code))
                val expected = when {
                    (context to code) in expectedCell -> RefusalCombination.EXPECTED
                    // the five in the wrong context, and (2026-10-20) the stored-report refusal on a session scan
                    code in setOf(
                        SessionErrorCode.NO_CREDIT, SessionErrorCode.SUBSCRIPTION_EXPIRED, SessionErrorCode.FREE_DEVICE_USED,
                        SessionErrorCode.SHOP_NO_CREDIT, SessionErrorCode.SHOP_SUBSCRIPTION_EXPIRED, SessionErrorCode.SCAN_REPORT_NOT_AVAILABLE
                    ) -> RefusalCombination.UNEXPECTED
                    // the session-state codes the analysis now carries (order 2026-10-17): no approved text this wave
                    code in setOf(
                        SessionErrorCode.SESSION_NOT_PARTICIPANT, SessionErrorCode.SESSION_NOT_STARTED, SessionErrorCode.SESSION_COMPLETED,
                        SessionErrorCode.SESSION_CANCELLED, SessionErrorCode.SESSION_EXPIRED, SessionErrorCode.SESSION_EXPIRES_AT_INVALID,
                        SessionErrorCode.SESSION_STATUS_UNKNOWN
                    ) -> RefusalCombination.NO_APPROVED_TEXT
                    // a code of the join / cancel / create calls that analyzeDevice is not expected to send
                    else -> RefusalCombination.UNEXPECTED
                }
                assertEquals("$context x ${code.wire}", expected, combination)
            }
        }
        for (context in RefusalContext.values()) {
            assertEquals("a code this app has never heard of", RefusalCombination.UNEXPECTED, refusalCombination(context, ReasonCodeRead.Unrecognized("BRAND_NEW")))
            assertNull("no reasonCode is not a classified refusal", refusalCombination(context, ReasonCodeRead.Absent))
        }
    }

    /** Rule 23: REPLACES `the analysis codes are exactly the five refusals and the seven session states`. */
    @Test
    fun `the analysis codes are exactly the six refusals and the seven session states`() {
        assertEquals(
            setOf(
                "NO_CREDIT", "SUBSCRIPTION_EXPIRED", "FREE_DEVICE_USED", "SHOP_NO_CREDIT", "SHOP_SUBSCRIPTION_EXPIRED", "SCAN_REPORT_NOT_AVAILABLE",
                "SESSION_NOT_PARTICIPANT", "SESSION_NOT_STARTED", "SESSION_COMPLETED", "SESSION_CANCELLED", "SESSION_EXPIRED",
                "SESSION_EXPIRES_AT_INVALID", "SESSION_STATUS_UNKNOWN"
            ),
            SessionErrorCode.analyzeCodes.map { it.wire }.toSet()
        )
    }
}
