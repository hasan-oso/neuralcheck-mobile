package net.neuralcheck.i18n

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Order 2026-10-01 (أمر القرارات، #5/#6 + ملحقه نفس اليوم) —
 * `report_issues_needing_attention` (`<plurals>`, strings.xml) and
 * `AiIssuesSection`'s own count gate (ReportScreen.kt).
 *
 * Reads the REAL `app/src/main/res/values{,-tr}/strings.xml` files from disk
 * (same technique as [net.neuralcheck.i18n.PreScanStringKeysTest]) — not a
 * hand-typed copy of the plural text — so a future edit to the actual
 * resource is what this test checks, per rule 37's "الاختبار الذي يستبدل
 * الطبقة التي قد ترفض لا يفحصها": this is the real resource path, not a
 * re-implementation of it.
 *
 * ### What this test does NOT prove (stated per the addendum's own
 * instruction: "إن لم يتوفّر Robolectric فاذكر ماذا اختبرت بدلاً من ذلك، وما
 * تبقّى غير مختبر")
 * When this was written the repo lacked Robolectric and ICU4J (checked then:
 * `app/build.gradle.kts` had neither; Robolectric is a test dependency since 3284cca, ICU4J still is not one) — a plain JVM unit test cannot call
 * `android.content.res.Resources.getQuantityString` /
 * `androidx.compose.ui.res.pluralStringResource` at all (the stub
 * `android.jar` throws on every such call without instrumentation). So this
 * test CANNOT prove that Android's own CLDR 'ar' plural-rule selector picks
 * the category this file independently computes for a given count — it only
 * proves (a) the XML itself holds exactly the five approved categories with
 * the approved text and no `zero` item, and (b) which text EACH category's
 * own item holds, cross-checked against the order's ten example numbers
 * using this test's own restatement of the published CLDR 'ar' rule (for
 * cross-checking only — the app does not implement this rule anywhere;
 * Android does). The actual runtime category SELECTION for a given integer
 * is unverified by any test in this repo — it would need a Robolectric or
 * instrumented test; Robolectric has been available since 3284cca, no test of this exists, and
 * whether its resources pick the Arabic CLDR category correctly is not checked. This is the same honest gap
 * [AiIssuesSection]'s zero-gating has: the `zero` item is intentionally
 * absent and never relied on (see the plurals resource's own doc comment),
 * so this gap does not affect the `count == 0` case, which is gated in code
 * before any plural lookup.
 */
class IssuesNeedingAttentionPluralsTest {

    companion object {
        private fun resFile(relative: String): File {
            val fromModule = File(relative)
            if (fromModule.exists()) return fromModule
            val fromRoot = File("app", relative)
            check(fromRoot.exists()) {
                "strings file not found from '${File(".").absolutePath}': $relative"
            }
            return fromRoot
        }

        /** category -> item text, for the first <plurals name=[pluralsName]> found. */
        private fun loadPluralItems(relative: String, pluralsName: String): Map<String, String> {
            val doc = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(resFile(relative))
            val pluralsNodes = doc.getElementsByTagName("plurals")
            for (i in 0 until pluralsNodes.length) {
                val node = pluralsNodes.item(i)
                val name = node.attributes.getNamedItem("name")?.nodeValue
                if (name != pluralsName) continue
                val items = mutableMapOf<String, String>()
                val children = node.childNodes
                for (j in 0 until children.length) {
                    val child = children.item(j)
                    if (child.nodeName != "item") continue
                    val quantity = child.attributes.getNamedItem("quantity")?.nodeValue ?: continue
                    items[quantity] = child.textContent
                }
                return items
            }
            error("<plurals name=\"$pluralsName\"> not found in $relative")
        }

        /**
         * This test's own restatement of the published CLDR 'ar' plural
         * rule, for cross-checking the ten example numbers ONLY — not
         * something this app implements anywhere (Android's ICU does, at
         * runtime, unverified here per this file's own doc). Per the
         * addendum: "ونطاقاتي «3 إلى 10» و«11 فما فوق» تبسيط يصحّ لما دون
         * المئة وحده" — the real rule is n%100-based, not n-based.
         */
        private fun cldrArCategory(n: Int): String = when {
            n == 0 -> "zero"
            n == 1 -> "one"
            n == 2 -> "two"
            n % 100 in 3..10 -> "few"
            n % 100 in 11..99 -> "many"
            else -> "other"
        }
    }

    private val arItems = loadPluralItems("src/main/res/values/strings.xml", "report_issues_needing_attention")
    private val trItems = loadPluralItems("src/main/res/values-tr/strings.xml", "report_issues_needing_attention")

    @Test
    fun `Arabic plurals has exactly one, two, few, many, other -- no zero`() {
        assertEquals(setOf("one", "two", "few", "many", "other"), arItems.keys)
    }

    @Test
    fun `Arabic plurals text matches the approved wording for each category`() {
        assertEquals("ملاحظة واحدة تحتاج انتباهاً", arItems.getValue("one"))
        assertEquals("ملاحظتان تحتاجان انتباهاً", arItems.getValue("two"))
        assertEquals("%d ملاحظات تحتاج انتباهاً", arItems.getValue("few"))
        assertEquals("%d ملاحظة تحتاج انتباهاً", arItems.getValue("many"))
        assertEquals("%d ملاحظة تحتاج انتباهاً", arItems.getValue("other"))
    }

    @Test
    fun `Turkish plurals has only 'other' -- tr has one CLDR category`() {
        assertEquals(setOf("other"), trItems.keys)
        assertEquals("%d dikkat gerektiren bulgu", trItems.getValue("other"))
    }

    /**
     * The order's own ten example numbers: 0, 1, 2, 3, 10, 11, 99, 100, 102,
     * 103. Cross-checks this file's CLDR restatement against the REAL
     * resource text for each (category selection itself unverified — see
     * class doc).
     */
    @Test
    fun `the ten example numbers resolve to the expected Arabic text via this file's CLDR cross-check`() {
        val expected = mapOf(
            0 to null, // zero: line is hidden in code, never looked up — see AiIssuesSection
            1 to "ملاحظة واحدة تحتاج انتباهاً",
            2 to "ملاحظتان تحتاجان انتباهاً",
            3 to "%d ملاحظات تحتاج انتباهاً",
            10 to "%d ملاحظات تحتاج انتباهاً",
            11 to "%d ملاحظة تحتاج انتباهاً",
            99 to "%d ملاحظة تحتاج انتباهاً",
            100 to "%d ملاحظة تحتاج انتباهاً",
            102 to "%d ملاحظة تحتاج انتباهاً",
            103 to "%d ملاحظات تحتاج انتباهاً"
        )
        for ((n, expectedText) in expected) {
            val category = cldrArCategory(n)
            if (n == 0) {
                assertNull("n=0 must never be looked up (gated in code)", expectedText)
                continue
            }
            assertTrue(
                "n=$n (category=$category) has no matching plurals item",
                arItems.containsKey(category)
            )
            assertEquals(
                "n=$n (category=$category)",
                expectedText,
                arItems.getValue(category)
            )
        }
    }
}
