package net.neuralcheck.domain.consent

import net.neuralcheck.presentation.screen.ApprovedConsentTable
import net.neuralcheck.presentation.screen.ConsentText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/**
 * The wording of the consent text is pinned to its version - A-P6-12 (contract A8 (5) decision 1: «رفع `CONSENT_TEXT_VERSION` يلزم عند كل تعديل لنصّ شاشة الموافقة بأيّ لغة (E11)، وإلا بقيت نسخة قديمة تُسجَّل
 * لنصّ جديد»). The server records the VERSION the user read; if a word of the text changes and [ConsentTextVersion.CURRENT] does not, the old version would be recorded for a text it never named. So the six texts
 * of the screen are read FROM THE RESOURCES (the files the build packs), hashed in the order of the screen (title, measures, does-not-read, microphone, accept, decline), and the hash is the one of the reviewed
 * wording of VERSION 2 - in each language.
 *
 * Rule 23: REPLACES `version 1 is the approved Arabic wording ...` (the digest `43d213d4b54163f98e2ac1c8eeb4da6ed950e4467d3728480da9b1f3af02fd55` of the six Kotlin strings of version 1: the wording is gone from
 * Kotlin and the text changed) and `the six approved texts are the ones of the plan's table, in its order ...` (it pinned the two sentences that version 2 removed from the text).
 *
 * The expected values are NOT computed from the code: they are the SHA-256 of the six Arabic cells and of the six Turkish cells of the review's wording (`texts-review.md`, section «الموافقة»: rows م١-م٦ and
 * A7-ت١-ت٦, from `DECISIONS-FOR-HASAN-2026-10-21.md`), joined by a newline, computed by `gen_consent.py` from the review's spec file and that file only. When a text changes - in either language, the Turkish
 * speaker's correction included: raise the version, write the new digests, and say in the commit that the version went up.
 */
class ConsentTextVersionTest {

    private val resDir: File = listOf("app/src/main/res", "../app/src/main/res").map { File(it) }.firstOrNull { it.isDirectory }
        ?: error("could not locate app/src/main/res from ${File(".").absolutePath}")

    private fun sha256(text: String): String = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    /** The body of `<string name="[name]">` in [file] under `res/`, with the XML and Android escapes undone the way AAPT does; exactly one definition. */
    private fun resource(file: String, name: String): String {
        val found = Regex("""<string name="${Regex.escape(name)}"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL).findAll(File(resDir, file).readText()).map { it.groupValues[1] }.toList()
        assertEquals("$file defines $name once", 1, found.size)
        return found.single().replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&").replace("\\'", "'").replace("\\\"", "\"")
    }

    private val screenOrder = listOf(ConsentText.Title, ConsentText.Measures, ConsentText.DoesNotRead, ConsentText.Microphone, ConsentText.Accept, ConsentText.Decline)

    private fun resourceName(text: ConsentText): String = ApprovedConsentTable.entries.single { it.slot == text.name }.resource

    @Test
    fun `version 2 is the reviewed wording in both languages - a changed word fails here until the version is raised`() {
        assertEquals("the server's own current version is 2 (the coordinator's message of 2026-10-23, protocolVocabulary.js:55)", 2, ConsentTextVersion.CURRENT)
        val ar = screenOrder.map { resource("values/strings.xml", resourceName(it)) }
        val tr = screenOrder.map { resource("values-tr/strings.xml", resourceName(it)) }
        assertEquals(
            "the Arabic wording of version ${ConsentTextVersion.CURRENT} changed: raise ConsentTextVersion.CURRENT (the server records the version the user read) and write the new digest",
            "c47ba5afc708ed5de3daf3cd28a347084d93665ecdc2305823c820d261cd012e",
            sha256(ar.joinToString("\n"))
        )
        assertEquals(
            "the Turkish wording of version ${ConsentTextVersion.CURRENT} changed: raise ConsentTextVersion.CURRENT and write the new digest",
            "5d8c2fc6d380612739a6a51d3ebc54349bd541b0129a6156754fc6d2ff8434c8",
            sha256(tr.joinToString("\n"))
        )
    }

    @Test
    fun `the six texts of the version are the screen's first six slots, in its order - the three failure texts come after and are not among them`() {
        assertEquals(screenOrder, ConsentText.values().take(6))
        assertEquals(ApprovedConsentTable.versionTexts, screenOrder.map { it.name })
        assertEquals(listOf(ConsentText.RecordFailed, ConsentText.RecordSessionNotScanning, ConsentText.RecordStoredUnreadable), ConsentText.values().drop(6))
    }

    @Test
    fun `what version 2 took out of version 1 stays out - the claim of an unrecoverable code, the retention sentence, the number of seconds`() {
        for (file in listOf("values/strings.xml", "values-tr/strings.xml")) {
            val texts = screenOrder.joinToString("\n") { resource(file, resourceName(it)) }
            for (gone in listOf("لا يمكن أن يُستعاد", "ويحفظ رمزاً", "سنتين", "تُحفظ نتائج الفحص", "ثلاث ثوانٍ", "iki yıl", "üç saniye", "geri dönüştürülemeyen")) {
                assertFalse("$file still holds «$gone» of version 1", texts.contains(gone))
            }
            assertFalse("$file: the consent text carries no digit (the user's rule: only +100 tests and 8 minutes are approved numbers, and neither is said here)", Regex("""\d""").containsMatchIn(texts))
        }
    }
}
