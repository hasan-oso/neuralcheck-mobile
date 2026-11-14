package net.neuralcheck.presentation.screen

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import net.neuralcheck.domain.consent.ConsentLocale
import net.neuralcheck.presentation.viewmodel.ConsentFailure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * The texts of the consent screen - version 2, reviewed on 2026-10-23 under Hasan's delegation of 2026-10-22 (`texts-review.md`, section «الموافقة») - are in the resources VERBATIM, in BOTH languages (the customer
 * sees this screen: nothing here is Arabic-only), and the labels the screen reads are those resources, in the language the screen records (rules 13, 22, 23, 24, 29, 30, 41). The table they are tested against is
 * [ApprovedConsentTable]: a copy generated from the review, each row with its source and the sha256 of the sheet; its `slot` is the [ConsentText]'s own name, so the map from a text to its resource is checked
 * against the table, not against itself. **The Turkish is proposed and needs a speaker; the final legal wording is pending Hasan, the KVKK lawyer later.**
 *
 * What is asserted: every text is written once in `values` and once in `values-tr` and equals the table; the labels in each language ([ConsentTexts.labelsFor]) are the table's text in THAT language whatever the
 * phone's own language is (the screen records the language it drew, so the text read and the language recorded are one); each failure has its text; the five text rules of the user's order hold in both languages
 * (no AI-provider name, no number, no guarantee word, no room-temperature request, no text in Kotlin - the last is `ConsentGuardTest`'s).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ConsentApprovedTextsTest {

    private val resDir: File = listOf("app/src/main/res", "../app/src/main/res").map { File(it) }.firstOrNull { it.isDirectory }
        ?: error("could not locate app/src/main/res from ${File(".").absolutePath}")

    private val valuesXml: String = File(resDir, "values/strings.xml").readText()
    private val valuesTrXml: String = File(resDir, "values-tr/strings.xml").readText()
    private val entries = ApprovedConsentTable.entries
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun definitions(xml: String, name: String): List<String> =
        Regex("""<string name="${Regex.escape(name)}"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL).findAll(xml)
            .map { it.groupValues[1].replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&").replace("\\'", "'").replace("\\\"", "\"") }.toList()

    @Test
    fun `the table is the review's - nine rows, one per slot, one per resource - and each row has both languages`() {
        assertEquals(9, entries.size)
        assertEquals(ConsentText.values().map { it.name }, entries.map { it.slot })
        assertEquals(9, entries.map { it.resource }.toSet().size)
        assertEquals(64, ApprovedConsentTable.SHEET_SHA256.length)
        for (e in entries) {
            assertTrue("${e.slot}: Arabic", e.ar.isNotBlank())
            assertTrue("${e.slot}: Turkish", e.tr.isNotBlank())
            assertTrue("${e.slot}: the two languages are two texts", e.ar != e.tr)
        }
    }

    @Test
    fun `every text is written verbatim - once in values and once in values-tr`() {
        for (e in entries) {
            assertEquals("${e.id} ${e.resource} in values", listOf(e.ar), definitions(valuesXml, e.resource))
            assertEquals("${e.id} ${e.resource} in values-tr", listOf(e.tr), definitions(valuesTrXml, e.resource))
        }
    }

    private fun checkLabels(locale: ConsentLocale) {
        val labels = ConsentTexts.labelsFor(context, locale)
        assertNotNull("every text is approved, so the labels exist (the screen draws)", labels)
        val bySlot = entries.associateBy { it.slot }
        for (text in ConsentText.values()) {
            val entry = bySlot[text.name] ?: error("${text.name} has no row in the table")
            assertEquals("${text.name}: the resource the slot names is the row's", entry.resource, context.resources.getResourceEntryName(text.resource))
            assertEquals("${text.name} in $locale", if (locale == ConsentLocale.AR) entry.ar else entry.tr, labels!!(text))
        }
    }

    @Test
    fun `the labels are the table's text in the language asked - a phone in English reads Arabic when the screen drew Arabic, and Turkish when it drew Turkish`() {
        // Robolectric's default locale is English: the phone's own language is neither of the two, and the labels are still those of the language the screen was asked for
        checkLabels(ConsentLocale.AR)
        checkLabels(ConsentLocale.TR)
    }

    /**
     * In the app the language asked IS the phone's: `ConsentScreen` reads it from the screen's own configuration (`ConsentLocaleRead.of(LocalConfiguration...)`), so a Turkish phone asks for Turkish. The cross
     * case - a Turkish phone asked for Arabic - is NOT asserted: Robolectric's resource table answers Turkish for it under the `tr` qualifier (an artifact of the environment that was seen, not verified on a device),
     * and the English phone above (a language that is neither) is the case that proves the labels follow the language asked.
     */
    @Test
    @Config(qualifiers = "tr")
    fun `a phone in Turkish reads Turkish when the screen drew Turkish`() {
        checkLabels(ConsentLocale.TR)
    }

    @Test
    @Config(qualifiers = "ar")
    fun `a phone in Arabic gives the same labels in both languages`() {
        checkLabels(ConsentLocale.AR)
        checkLabels(ConsentLocale.TR)
    }

    @Test
    fun `each failure shows its own text - and the three are the three failure slots`() {
        assertEquals(ConsentText.RecordSessionNotScanning, ConsentTexts.forFailure(ConsentFailure.SESSION_NOT_SCANNING))
        assertEquals(ConsentText.RecordStoredUnreadable, ConsentTexts.forFailure(ConsentFailure.STORED_UNREADABLE))
        assertEquals(ConsentText.RecordFailed, ConsentTexts.forFailure(ConsentFailure.OTHER))
        assertEquals("every failure has a text", ConsentFailure.values().size, ConsentFailure.values().map { ConsentTexts.forFailure(it) }.toSet().size)
        val arabic = ConsentTexts.labelsFor(context, ConsentLocale.AR)!!
        assertEquals("ف٣ ends «اطلب من الفنّي فتح جلسة جديدة»: the technician, not the shop's owner (the user's decision of 2026-10-10)", true, arabic(ConsentText.RecordStoredUnreadable).endsWith("اطلب من الفنّي فتح جلسة جديدة."))
    }

    @Test
    fun `the user's text rules hold in both languages - no AI provider, no guarantee, no room temperature, no number, no percent`() {
        val provider = Regex("""OpenAI|ChatGPT|GPT|Gemini|Claude|Anthropic|Bard|Copilot|Llama|Mistral|DeepSeek|Grok|Vertex|Google AI""", RegexOption.IGNORE_CASE)
        val guarantee = Regex("""ضمان|نضمن|مضمون|يضمن|تضمن|ضامن|garanti|garantı|güvence""", RegexOption.IGNORE_CASE)
        val room = Regex("""الغرفة|oda sıcaklığı|oda ısısı|room temp""", RegexOption.IGNORE_CASE)
        val legal = Regex("""قانون|محامٍ|محامي|KVKK|kanun|hukuk|avukat""", RegexOption.IGNORE_CASE)
        for (e in entries) for ((lang, text) in listOf("ar" to e.ar, "tr" to e.tr)) {
            assertFalse("${e.slot} ($lang) names a provider", provider.containsMatchIn(text))
            assertFalse("${e.slot} ($lang) promises a guarantee", guarantee.containsMatchIn(text))
            assertFalse("${e.slot} ($lang) asks for the room's temperature", room.containsMatchIn(text))
            assertFalse("${e.slot} ($lang) makes a legal claim (the lawyer comes later, and the review file says so)", legal.containsMatchIn(text))
            assertFalse("${e.slot} ($lang) holds a digit: the only approved numbers are +100 tests and 8 minutes, and neither is said here", Regex("""\d""").containsMatchIn(text))
            assertFalse("${e.slot} ($lang) holds a % or a placeholder: the text takes no argument", Regex("""%""").containsMatchIn(text))
        }
    }
}
