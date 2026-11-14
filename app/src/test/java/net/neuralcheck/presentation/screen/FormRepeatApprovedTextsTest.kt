package net.neuralcheck.presentation.screen

import android.content.Context
import android.content.res.Resources
import androidx.test.core.app.ApplicationProvider
import net.neuralcheck.domain.form.GradeAnswer
import net.neuralcheck.domain.form.GradeCategory
import net.neuralcheck.domain.repeat.RepeatRefusal
import net.neuralcheck.presentation.viewmodel.RepeatStartUi
import net.neuralcheck.service.stress.ScanBatteryWindow
import net.neuralcheck.util.NumberFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * The texts of the technician's form, the repeat scan and the scan-start battery window - reviewed on 2026-10-23 under Hasan's delegation of 2026-10-22 (`scratchpad/app-delegated/texts-review.md`) - are in the
 * resources VERBATIM, and the labels the screens read are those resources (rules 13, 22, 23, 24, 29, 30, 41). The table they are tested against is [ApprovedTextsTable]: a copy generated from the approval,
 * each row with its source and the sha256 of the sources. REPLACES the tests that pinned the ABSENCE of these texts from the resources (rule 23: `no resource holds a text of the form / the repeat screens that is not
 * approved`, `the sentence ... no resource holds the proposed sentence`).
 *
 * What is asserted: every text is written once in `values` and equals the table; the technician's texts (Arabic only) have NO entry in `values-tr` and the scanned phone's fifteen have one; nothing else with the
 * features' prefixes was written; the placeholders are the same in both languages and are the three the dialog fills; the resources AS THE APP READS THEM under the Arabic and the Turkish locales give the table's
 * text (Android falls back to the Arabic for a technician's text, on purpose); [formLabelsOf] and [repeatLabelsOf] - the two functions the screens' labels are made by - give every text of [FormTexts.all] and
 * [RepeatTexts.all] its resource's text in each language, and each text has its own resource; the three cases that have no text throw instead of drawing one; the battery window's numbers come from
 * [ScanBatteryWindow]; the five text rules of the user's order (no AI-provider name, no number beyond the approved ones, no guarantee word, no room-temperature request, no text in Kotlin) hold for every text written.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FormRepeatApprovedTextsTest {

    private val resDir: File = listOf("app/src/main/res", "../app/src/main/res").map { File(it) }.firstOrNull { it.isDirectory }
        ?: error("could not locate app/src/main/res from ${File(".").absolutePath}")

    private val mainDir: File = listOf("app/src/main", "../app/src/main").map { File(it) }.firstOrNull { it.isDirectory }
        ?: error("could not locate app/src/main from ${File(".").absolutePath}")

    private val valuesXml: String = File(resDir, "values/strings.xml").readText()
    private val valuesTrXml: String = File(resDir, "values-tr/strings.xml").readText()

    private val form = ApprovedTextsTable.form
    private val repeat = ApprovedTextsTable.repeat
    private val window = ApprovedTextsTable.window
    private val all = form + repeat + window

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    /** The raw bodies of every `<string name="[name]">` in [xml], with the XML and Android escapes undone the way AAPT does. */
    private fun definitions(xml: String, name: String): List<String> =
        Regex("""<string name="${Regex.escape(name)}"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL).findAll(xml)
            .map { it.groupValues[1].replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&").replace("\\'", "'").replace("\\\"", "\"") }.toList()

    private fun namesWithPrefixes(xml: String): Set<String> =
        Regex("""<string name="([^"]+)"""").findAll(xml).map { it.groupValues[1] }.filter { n -> PREFIXES.any { n.startsWith(it) } }.toSet()

    private fun id(resources: Resources, name: String): Int {
        val id = resources.getIdentifier(name, "string", context.packageName)
        assertTrue("no string resource named $name", id != 0)
        return id
    }

    private fun resource(resources: Resources, name: String): String = resources.getString(id(resources, name))

    private fun stripComments(source: String): String =
        source.replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), " ").lines().joinToString("\n") { it.substringBefore("//") }

    // ---- the table ----

    @Test
    fun `the table is the approval's - 86 texts of the form, 16 of the repeat scan, the window - with the counts the commit quotes, and every resource name is unique`() {
        assertEquals(86, form.size)
        assertEquals("38 approved before (5 level words, 16 descriptions, 3 rules, 13 symptoms, the game question) and 48 by delegation", 38, form.count { it.id == "approved-before" })
        assertEquals(48, form.count { it.id != "approved-before" })
        assertEquals(16, repeat.size)
        assertEquals("the notice's two are the technician's, the other fourteen the scanned phone's", 14, repeat.count { it.scannedPhone })
        assertEquals("one sentence of the form is the scanned phone's", listOf("tf_scan_in_progress"), form.filter { it.scannedPhone }.map { it.resource })
        assertEquals(form.size + repeat.size + 1, all.map { it.resource }.toSet().size)
        assertEquals("every row has its own slot key", all.size, all.map { it.slot }.toSet().size)
        // the rows' slots are exactly the texts' own: nothing in the table the lists do not hold, nothing in the lists that the table lacks (the existing retry is the one text with no row)
        assertEquals(form.map { it.slot }.toSet() + "ConfigRetry", FormTexts.all.map { it.toString() }.toSet())
        assertEquals(repeat.map { it.slot }.toSet(), RepeatTexts.all.map { it.toString() }.toSet())
        for (e in all) assertEquals("${e.resource}: a Turkish wording only for the scanned phone", e.scannedPhone, e.tr != null)
        assertEquals(64, ApprovedTextsTable.SHEET_SHA256.length)
        assertEquals(64, ApprovedTextsTable.LEVELS_SHA256.length)
        assertEquals(64, ApprovedTextsTable.CONTRACT_COPY_SHA256.length)
    }

    // ---- the files ----

    @Test
    fun `every text is written verbatim - once in values - and the scanned phone's fifteen once in values-tr as well`() {
        for (e in all) {
            assertEquals("${e.id} ${e.resource} in values", listOf(e.ar), definitions(valuesXml, e.resource))
            val inTr = definitions(valuesTrXml, e.resource)
            if (e.tr != null) assertEquals("${e.id} ${e.resource} in values-tr", listOf(e.tr), inTr) else assertEquals("${e.resource}: the technician's text has NO Turkish entry (Arabic only)", emptyList<String>(), inTr)
        }
    }

    @Test
    fun `no other text with the features' prefixes was written - the names are exactly the table's in both files`() {
        assertEquals((form + repeat).map { it.resource }.toSet(), namesWithPrefixes(valuesXml))
        assertEquals((form + repeat).filter { it.scannedPhone }.map { it.resource }.toSet(), namesWithPrefixes(valuesTrXml))
        assertEquals(15, namesWithPrefixes(valuesTrXml).size)
        assertEquals(1, Regex("""name="scan_error_battery_window_fmt"""").findAll(valuesXml).count())
        assertEquals(1, Regex("""name="scan_error_battery_window_fmt"""").findAll(valuesTrXml).count())
    }

    @Test
    fun `the placeholders are the same in both languages - and only the window's text has any, the three of the dialog`() {
        val placeholder = Regex("""%\d+\$[sd]""")
        for (e in all) {
            val ar = placeholder.findAll(e.ar).map { it.value }.toList()
            if (e.tr != null) assertEquals("${e.resource}: Arabic and Turkish placeholders", ar, placeholder.findAll(e.tr).map { it.value }.toList())
            assertEquals("${e.resource}: the placeholders the screen fills", if (e === window) listOf("%1\$d", "%2\$d", "%3\$d") else emptyList<String>(), ar)
        }
        // and the same check on the files themselves, not on the table
        for (e in all) {
            val inValues = placeholder.findAll(definitions(valuesXml, e.resource).single()).map { it.value }.toList()
            if (e.tr != null) assertEquals(e.resource, inValues, placeholder.findAll(definitions(valuesTrXml, e.resource).single()).map { it.value }.toList())
        }
        // a plain percent sign is only ever written where no argument is filled (formatted="false"), and the window's own is doubled
        for (e in form + repeat) if ('%' in e.ar) assertTrue("${e.resource} holds a % so it says formatted=\"false\"", Regex("""name="${e.resource}" formatted="false"""").containsMatchIn(valuesXml))
        assertTrue(window.ar.contains("%1\$d%%") && window.ar.contains("%2\$d%%") && window.ar.contains("%3\$d%%"))
        assertTrue(window.tr!!.contains("%%%1\$d") && window.tr!!.contains("%%%2\$d") && window.tr!!.contains("%%%3\$d"))
    }

    // ---- the resources as the app reads them ----

    @Test
    @Config(qualifiers = "ar")
    fun `the resources give the table's Arabic under the Arabic locale - every text`() {
        for (e in form + repeat) assertEquals("${e.id} ${e.resource}", e.ar, resource(context.resources, e.resource))
    }

    @Test
    @Config(qualifiers = "tr")
    fun `under the Turkish locale the scanned phone's texts are Turkish and every technician's text falls back to the Arabic, on purpose`() {
        for (e in form + repeat) {
            val text = resource(context.resources, e.resource)
            if (e.tr != null) {
                assertEquals("${e.id} ${e.resource}", e.tr, text)
                assertTrue("${e.resource} fell back to the Arabic", text != e.ar)
            } else {
                assertEquals("${e.resource}: no Turkish, the Arabic shows", e.ar, text)
            }
        }
    }

    // ---- the window: the numbers are ScanBatteryWindow's, not the text's ----

    private fun windowText(resources: Resources, level: Int): String =
        NumberFormat.toLatin(resources.getString(id(resources, window.resource), ScanBatteryWindow.MIN_PCT, ScanBatteryWindow.MAX_PCT, level))

    @Test
    @Config(qualifiers = "ar")
    fun `the window's Arabic text is filled with the two limits of ScanBatteryWindow and the level now`() {
        assertEquals("يجب أن يكون الشحن بين ${ScanBatteryWindow.MIN_PCT}% و${ScanBatteryWindow.MAX_PCT}% لبدء الفحص — الآن 92%", windowText(context.resources, 92))
        assertEquals("30% and 85% are the user's decision 7, and only ScanBatteryWindow holds them", listOf(30, 85), listOf(ScanBatteryWindow.MIN_PCT, ScanBatteryWindow.MAX_PCT))
        assertFalse("no limit is written in the text", Regex("""\d""").containsMatchIn(window.ar.replace(Regex("""%\d+[$]d"""), "")))
        assertFalse(Regex("""\d""").containsMatchIn(window.tr!!.replace(Regex("""%\d+[$]d"""), "")))
    }

    @Test
    @Config(qualifiers = "tr")
    fun `the window's Turkish text is filled the same way`() {
        assertEquals("Taramayı başlatmak için şarj %${ScanBatteryWindow.MIN_PCT} ile %${ScanBatteryWindow.MAX_PCT} arasında olmalı — şu an %92", windowText(context.resources, 92))
    }

    @Test
    fun `a refusal above the window carries its level - and one without a readable level is not given the text, so no number is made up`() {
        assertEquals(92, batteryAboveWindowLevel("BATTERY_TOO_HIGH:92"))
        assertEquals(ScanBatteryWindow.REFUSAL_ABOVE + ":100", "BATTERY_TOO_HIGH:100")
        assertNull("no level", batteryAboveWindowLevel("BATTERY_TOO_HIGH"))
        assertNull("an unreadable level", batteryAboveWindowLevel("BATTERY_TOO_HIGH:abc"))
        assertNull("the refusal below the window has its own text", batteryAboveWindowLevel("BATTERY_TOO_LOW:19"))
        assertNull("another code", batteryAboveWindowLevel("DEVICE_TOO_HOT"))
    }

    @Test
    fun `the start-failure dialog fills the window's text with the window's own two limits and the level, and only for a refusal above it - the text is read nowhere else`() {
        val source = File(mainDir, "java/net/neuralcheck/presentation/screen/ActiveScanScreen.kt").readText()
        val screen = stripComments(source).replace(Regex("""\s+"""), " ")
        assertTrue("the level is read once, before the dialog's body", screen.contains("val aboveWindowLevel = batteryAboveWindowLevel(errorMsg)"))
        assertEquals(
            "the first branch of the body: the window's text, both limits from ScanBatteryWindow (no number is written in Kotlin or in the text), the level of the refusal",
            1,
            Regex("""val dialogBody = when \{ aboveWindowLevel != null -> NumberFormat\.toLatin\( stringResource\( R\.string\.scan_error_battery_window_fmt, minBatteryPct, net\.neuralcheck\.service\.stress\.ScanBatteryWindow\.MAX_PCT, aboveWindowLevel \) \)""")
                .findAll(screen).count()
        )
        val readers = File(mainDir, "java").walkTopDown().filter { it.isFile && it.extension == "kt" && stripComments(it.readText()).contains("R.string.scan_error_battery_window_fmt") }.map { it.name }.toList()
        assertEquals("one reader", listOf("ActiveScanScreen.kt"), readers)
        assertTrue("the refusal below the window keeps its own approved text", screen.contains("errorMsg.startsWith(\"BATTERY_TOO_LOW\") ->"))
    }

    // ---- the labels the screens are made of ----

    private fun expected(entry: ApprovedTextsTable.Entry, tr: Boolean): String = if (tr && entry.tr != null) entry.tr else entry.ar

    /**
     * The row of the table is found by the text's OWN `toString()` (the row's [ApprovedTextsTable.Entry.slot]), never by the resource the map names: a map that sends a text to another text's resource is caught
     * here (mutant F of this change's review: `FormText.Submit -> R.string.tf_yes`), not agreed with.
     */
    private fun checkFormLabels(resources: Resources, tr: Boolean) {
        val labels = formLabelsOf(resources)
        val bySlot = form.associateBy { it.slot }
        for (text in FormTexts.all) {
            val name = resources.getResourceEntryName(text.resource())
            if (text === FormText.ConfigRetry) {
                assertEquals("the one existing text is btn_retry", "btn_retry", name)
                assertEquals(resource(resources, "btn_retry"), labels(text))
                continue
            }
            val entry = bySlot[text.toString()] ?: error("$text has no row in the table")
            assertEquals("$text: the resource the map names is the row's", entry.resource, name)
            assertEquals("$text -> $name", expected(entry, tr), labels(text))
        }
    }

    private fun checkRepeatLabels(resources: Resources, tr: Boolean) {
        val labels = repeatLabelsOf(resources)
        val bySlot = repeat.associateBy { it.slot }
        for (text in RepeatTexts.all) {
            val name = resources.getResourceEntryName(text.resource())
            val entry = bySlot[text.toString()] ?: error("$text has no row in the table")
            assertEquals("$text: the resource the map names is the row's", entry.resource, name)
            assertEquals("$text -> $name", expected(entry, tr), labels(text))
        }
    }

    @Test
    @Config(qualifiers = "ar")
    fun `the form's labels and the repeat scan's give the table's text for every text - in Arabic`() {
        checkFormLabels(context.resources, tr = false)
        checkRepeatLabels(context.resources, tr = false)
    }

    @Test
    @Config(qualifiers = "tr")
    fun `the form's labels and the repeat scan's give the table's text for every text - in Turkish for the scanned phone's, in Arabic for the technician's`() {
        checkFormLabels(context.resources, tr = true)
        checkRepeatLabels(context.resources, tr = true)
    }

    @Test
    fun `every text of the form and of the repeat scan has its own resource - the form's 87 are the table's 86 and the existing retry, the repeat scan's 16 are the table's`() {
        val resources = context.resources
        val formNames = FormTexts.all.map { resources.getResourceEntryName(it.resource()) }
        assertEquals("one resource per text", FormTexts.all.size, formNames.toSet().size)
        assertEquals(form.map { it.resource }.toSet() + "btn_retry", formNames.toSet())
        assertEquals(87, FormTexts.all.size)
        val repeatNames = RepeatTexts.all.map { resources.getResourceEntryName(it.resource()) }
        assertEquals(16, RepeatTexts.all.size)
        assertEquals(repeat.map { it.resource }.toSet(), repeatNames.toSet())
        assertEquals("the sentence of the scanned phone", "tf_scan_in_progress", resources.getResourceEntryName(FormText.ScanInProgress.resource()))
        assertEquals("the drawn-by-the-scanned-phone list is exactly that sentence", setOf<FormText>(FormText.ScanInProgress), FormTexts.drawnByScannedPhone)
        // the table's technician-or-scanned split is the surfaces' split: every text in values-tr is drawn by the scanned phone
        val inTr = namesWithPrefixes(valuesTrXml)
        assertEquals(setOf("tf_scan_in_progress") + repeat.filter { it.scannedPhone }.map { it.resource }, inTr)
    }

    @Test
    fun `the cases that have no text throw instead of drawing one - a not-tested description, a fourth rule, a refusal with no text of its own`() {
        try {
            FormText.LevelDescription(GradeCategory.BATTERY, GradeAnswer.NOT_TESTED).resource()
            fail("a «not_tested» answer has no description")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("not a level"))
        }
        try {
            FormText.Rule(4).resource()
            fail("the rules are numbered 1 to 3")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("1 to 3"))
        }
        val withText = RepeatTexts.refusalsWithText.toSet()
        for (refusal in RepeatRefusal.values()) {
            if (refusal in withText) {
                assertTrue(context.resources.getResourceEntryName(RepeatText.StartRefused(refusal).resource()).startsWith("rp_refused_"))
            } else {
                try {
                    RepeatText.StartRefused(refusal).resource()
                    fail("$refusal has no text of its own")
                } catch (e: IllegalArgumentException) {
                    assertTrue(e.message!!.contains(refusal.wire))
                }
                // and the screen never asks for it: a failed start with that refusal shows the general failure text
                assertEquals(RepeatText.StartNotSent, RepeatTexts.forFailure(RepeatStartUi.Failed(refusal, refusal.wire)))
            }
        }
        assertEquals("seven refusals have a text, five have none", listOf(7, 5), listOf(withText.size, RepeatRefusal.values().size - withText.size))
    }

    // ---- the user's text rules, over every text written ----

    @Test
    fun `no text names an AI provider, promises a guarantee or asks for the room's temperature`() {
        val provider = Regex("""OpenAI|ChatGPT|GPT|Gemini|Claude|Anthropic|Bard|Copilot|Llama|Mistral|DeepSeek|Grok|Vertex|Google AI""", RegexOption.IGNORE_CASE)
        val guarantee = Regex("""ضمان|نضمن|مضمون|يضمن|تضمن|ضامن|garanti|garantı|güvence""", RegexOption.IGNORE_CASE)
        val room = Regex("""الغرفة|oda sıcaklığı|oda ısısı|room temp""", RegexOption.IGNORE_CASE)
        for (e in all) for ((lang, text) in listOf("ar" to e.ar, "tr" to e.tr)) {
            if (text == null) continue
            assertFalse("${e.resource} ($lang) names a provider", provider.containsMatchIn(text))
            assertFalse("${e.resource} ($lang) promises a guarantee", guarantee.containsMatchIn(text))
            assertFalse("${e.resource} ($lang) asks for the room's temperature", room.containsMatchIn(text))
        }
    }

    @Test
    fun `the only numbers in the texts are the approved level examples of the battery - and every other text has none beyond its placeholders`() {
        // TL (`TECH-LEVELS-WORDING-FINAL-2026-10-10.md`, final approval 2026-10-10): the technician's examples of the battery's levels. Anything else that carries a digit is a new number: none is approved
        // (the user's rule: only «+100 اختبار» and «8 دقائق», which no text of these features says).
        val allowed = mapOf(
            "tf_desc_battery_good" to setOf("80", "89"),
            "tf_desc_battery_fair" to setOf("40", "25"),
            "tf_desc_battery_weak" to setOf("15")
        )
        for (e in all) for ((lang, text) in listOf("ar" to e.ar, "tr" to e.tr)) {
            if (text == null) continue
            val digits = Regex("""\d+""").findAll(text.replace(Regex("""%\d+\$[sd]"""), "")).map { it.value }.toSet()
            assertEquals("${e.resource} ($lang): the numbers in the text", allowed[e.resource] ?: emptySet<String>(), digits)
        }
        for (name in allowed.keys) assertEquals(1, form.count { it.resource == name })
    }

    @Test
    fun `no text of the form, the repeat scan or the window is written in Kotlin - the two maps and the two surfaces hold no Arabic or Turkish letter outside comments`() {
        val files = listOf(
            "presentation/screen/FormTextResources.kt", "presentation/screen/RepeatTextResources.kt",
            "presentation/screen/TechnicianFormSurface.kt", "presentation/screen/RepeatScanSurface.kt"
        )
        val letters = Regex("""[؀-ۿݐ-ݿÇçĞğİıÖöŞşÜü]""")
        for (f in files) {
            val code = stripComments(File(mainDir, "java/net/neuralcheck/$f").readText())
            val hit = letters.find(code)
            assertTrue("$f holds the letter «${hit?.value}» outside a comment", hit == null)
        }
        // and no Kotlin file of main holds one of the table's texts as a literal
        val sources = File(mainDir, "java").walkTopDown().filter { it.isFile && it.extension == "kt" }.map { it.name to stripComments(it.readText()) }.toList()
        for (e in all) {
            if (e.ar.length < 14) continue // a single word («البطارية») is another file's label as well; the sentences are what must not be copied into Kotlin
            for ((name, code) in sources) assertFalse("$name holds the text of ${e.resource} as a literal", code.contains(e.ar))
        }
    }

    companion object {
        private val PREFIXES = listOf("tf_", "rp_")
    }
}
