package net.neuralcheck.domain.consent

import com.google.gson.annotations.SerializedName
import net.neuralcheck.data.remote.JoinSessionResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/**
 * The app's consent constants, pinned to the COPY of the contract (rules 24, 25, 29): `app/src/test/resources/contract/protocol-contract-A8-consent-neuralcheck-backend-c289a2d.md` - lines of neuralcheck-backend
 * `docs/PROTOCOL_CONTRACT.md` at c289a2d (the A8 section with its four dated addenda of 2026-10-23, and the vocabulary rows `CONSENT_STATE`, `CONSENT_LOCALE` and `CONSENT_TEXT_VERSION`), cut by anchors and not edited; its
 * header carries the sha256 of each block, and the blocks' sha256 were checked against `git show c289a2d:docs/PROTOCOL_CONTRACT.md | sed -n '<from>,<to>p' | sha256sum` when it was written.
 *
 * What is compared, value by value: the key `consentEnabled` of the join's answer (the contract's shape line, and the `@SerializedName` of the field that reads it); the refusal `CONSENT_PART_NOT_ENABLED` (name and
 * status) and the other eight; the request and the answer of `recordScanConsent`; the two languages; the accepted state; the version of the text (the vocabulary row against [ConsentTextVersion.CURRENT]); and that the app
 * has NO reader of `inherited` (the repeat child's consent, ق14: a server-side field that reaches no client). A copy drifts (rule 29): when the server changes the contract this file is re-copied and this test says what moved.
 */
class ConsentContractCopyTest {

    private val copy: String = javaClass.classLoader!!.getResourceAsStream("contract/protocol-contract-A8-consent-neuralcheck-backend-c289a2d.md")
        ?.readBytes()?.toString(Charsets.UTF_8)
        ?: error("the consent contract copy is not on the test classpath - expected at app/src/test/resources/contract/")

    private val mainDir: File = listOf("app/src/main", "../app/src/main").map { File(it) }.firstOrNull { it.isDirectory }
        ?: error("could not locate app/src/main from ${File(".").absolutePath}")

    private fun sha256(text: String) = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    private fun between(begin: Regex, end: String): String {
        val start = begin.find(copy) ?: error("no $begin in the copy")
        val from = start.range.last + 2
        val to = copy.indexOf(end, from)
        check(to > from) { "no $end after $begin" }
        return copy.substring(from, to)
    }

    private val blockD get() = between(Regex("""<!-- BLOCK-D:BEGIN [^>]*-->"""), "<!-- BLOCK-D:END -->")

    private val vocabularyBlocks: List<String> =
        Regex("""<!-- VOCAB-ROWS:BEGIN -->\n(.*?)<!-- VOCAB-ROWS:END -->""", RegexOption.DOT_MATCHES_ALL).findAll(copy).map { it.groupValues[1] }.toList()

    private data class Row(val group: String, val key: String, val value: String, val tag: String)

    private val rows: List<Row> by lazy {
        val row = Regex("""^\| `(\w+)` \| `?(\w+)`? \| `?([^`|]+?)`? \| (\w+) \| .+ \|$""")
        vocabularyBlocks.joinToString("").lines().filter { it.isNotBlank() }.map { line ->
            val m = row.matchEntire(line) ?: error("a row of the vocabulary blocks does not read: $line")
            Row(m.groupValues[1], m.groupValues[2], m.groupValues[3].trim(), m.groupValues[4])
        }
    }

    private fun stripComments(source: String): String =
        source.replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), " ").lines().joinToString("\n") { it.substringBefore("//") }

    @Test
    fun `each block of the copy has the sha256 its own header states - and the header names the commit`() {
        val header = Regex("""^\| ([DEV]) \| [\d-]+ \| .+ \| `([0-9a-f]{64})` \|$""", RegexOption.MULTILINE).findAll(copy).associate { it.groupValues[1] to it.groupValues[2] }
        assertEquals(setOf("D", "E", "V"), header.keys)
        assertEquals(header["D"], sha256(blockD))
        assertEquals("two vocabulary blocks, E and V", 2, vocabularyBlocks.size)
        assertEquals(header["E"], sha256(vocabularyBlocks[0]))
        assertEquals(header["V"], sha256(vocabularyBlocks[1]))
        assertTrue("the header names the commit the copy was taken at", copy.contains("c289a2dc3ec4b52624398737b7a6e838483ca0be"))
    }

    @Test
    fun `the join's answer carries consentEnabled - the contract's shape line, the app's constant and the field that reads it are one spelling`() {
        val shape = "`{sessionId: string, ownerId: string, status: \"scanning\", evaluationEnabled: boolean, ${ConsentWire.KEY_CONSENT_ENABLED}: boolean}`"
        assertTrue("the shape line of joinScanSession's answer", blockD.contains("`joinScanSession` ⟵ $shape"))
        assertTrue("always a boolean on the wire: no missing key, no null", blockD.contains("**`${ConsentWire.KEY_CONSENT_ENABLED}` منطقية دائماً**"))
        val field = JoinSessionResult::class.java.getDeclaredField("consentEnabled")
        assertEquals("the field is read under the contract's key and no other", ConsentWire.KEY_CONSENT_ENABLED, field.getAnnotation(SerializedName::class.java)!!.value)
        assertTrue("and has no snake-case alternate", field.getAnnotation(SerializedName::class.java)!!.alternate.isEmpty())
        assertEquals("the type is nullable (a boxed Boolean): an answer without the key is absent, not false", Boolean::class.javaObjectType, field.type)
    }

    @Test
    fun `recordScanConsent's request and answer are the contract's lines - and the nine consent refusals are the contract's, the new one with its status`() {
        assertTrue(blockD.contains("`${ConsentWire.CALLABLE}({${ConsentWire.KEY_SESSION_ID}, ${ConsentWire.KEY_TEXT_VERSION}, ${ConsentWire.KEY_LOCALE}})`"))
        assertTrue(
            blockD.contains(
                "`{${ConsentWire.KEY_SESSION_ID}, ${ConsentWire.KEY_STATE}: \"${ConsentWire.STATE_ACCEPTED}\", ${ConsentWire.KEY_TEXT_VERSION}, ${ConsentWire.KEY_LOCALE}, ${ConsentWire.KEY_ALREADY_RECORDED}: boolean}`"
            )
        )
        for (refusal in ConsentRefusal.values()) assertTrue("${refusal.wire} is named in the contract's A8", blockD.contains("`${refusal.wire}`"))
        assertTrue("ق40: the name and its status", blockD.contains("**`${ConsentRefusal.PART_NOT_ENABLED.wire}`** (`failed-precondition`"))
    }

    @Test
    fun `the two languages and the accepted state are the vocabulary's - and the app has no reader of inherited, declined or not_collected`() {
        assertEquals(listOf("ar", "tr"), rows.filter { it.group == "CONSENT_LOCALE" }.map { it.value })
        assertEquals(ConsentLocale.values().map { it.wire }, rows.filter { it.group == "CONSENT_LOCALE" }.map { it.value })
        val states = rows.filter { it.group == "CONSENT_STATE" }
        assertEquals(listOf("accepted", "declined", "not_collected", "inherited"), states.map { it.value })
        assertEquals(ConsentWire.STATE_ACCEPTED, states.single { it.key == "ACCEPTED" }.value)
        // ق14: the repeat child's consent is `inherited` on the SERVER's scanProtocol - a field that reaches no client (the contract's own words), so the app spells none of the three other states
        assertTrue(blockD.contains("`CONSENT_STATE.INHERITED = \"inherited\"`"))
        val sources = File(mainDir, "java").walkTopDown().filter { it.isFile && it.extension == "kt" }.map { it.name to stripComments(it.readText()) }.toList()
        for (literal in listOf("inherited", "declined", "not_collected")) {
            val holders = sources.filter { it.second.contains("\"$literal\"") }.map { it.first }
            assertEquals("the app spells \"$literal\" in $holders", emptyList<String>(), holders)
        }
    }

    @Test
    fun `the version of the text is the vocabulary's - raised to 2, and the contract says the older one stays accepted`() {
        // this row has another layout than the group rows: | `CONSENT_TEXT_VERSION` | `2` | number | decided | E11 | - name, value, type, tag, source
        val row = rows.single { it.group == "CONSENT_TEXT_VERSION" }
        assertEquals("2", row.key)
        assertEquals("number", row.value)
        assertEquals("the app sends the contract's current version: one source, two copies of the number kept equal here", row.key, ConsentTextVersion.CURRENT.toString())
        assertTrue(blockD.contains("**الحالية الآن 2**"))
        assertTrue("version 1 stays accepted", blockD.contains("**والنسخة 1 تبقى مقبولة**"))
    }
}
