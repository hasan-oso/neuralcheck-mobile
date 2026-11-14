package net.neuralcheck.data.remote

import net.neuralcheck.domain.pair.PairLinkWire
import net.neuralcheck.domain.pair.PairRefusal
import net.neuralcheck.util.MarketingNameWire
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

/**
 * The app's wire rows of the maintenance before/after link, pinned to the COPY of the contract (rules 24, 25, 29). The copy is
 * `app/src/test/resources/contract/protocol-contract-A2-A3-maintenance-pairs-neuralcheck-backend-c289a2d.md`: lines of neuralcheck-backend `docs/PROTOCOL_CONTRACT.md` at c289a2d (blocks A, B and C are line for line those of f3c1bab: the server changed A4, A8, A10 and two vocabulary rows, none inside them), cut and not edited (its header
 * says how to reproduce each block and carries the sha256 of each). What is read here is the contract's own VOCABULARY TABLE (the rows between the `VOCAB-ROWS` markers) and a few lines of its text: every enumeration
 * of the app is compared with the table's group, in the table's order, value by value - so an enum that drifts from the contract fails here and not on a technician's phone.
 *
 * A copy drifts (rule 29): when the server changes the contract, this file is re-copied from the new commit and this test says what moved. What is NOT here is the server's code (`protocolVocabulary.js`): the table is
 * the contract's, and the contract's test on the server pins the table to the code.
 */
class MaintenancePairContractCopyTest {

    private val copy: String = javaClass.classLoader!!.getResourceAsStream("contract/protocol-contract-A2-A3-maintenance-pairs-neuralcheck-backend-c289a2d.md")
        ?.readBytes()?.toString(Charsets.UTF_8)
        ?: error("the contract copy is not on the test classpath - expected at app/src/test/resources/contract/")

    private fun sha256(text: String) = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    /** The text of a marked block, exactly as the lines are (the trailing line break included). */
    private fun between(begin: Regex, end: String): String {
        val start = begin.find(copy) ?: error("no $begin in the copy")
        val from = start.range.last + 2 // after the marker's line break
        val to = copy.indexOf(end, from)
        check(to > from) { "no $end after $begin" }
        return copy.substring(from, to)
    }

    private val blockA get() = between(Regex("""<!-- BLOCK-A:BEGIN [^>]*-->"""), "<!-- BLOCK-A:END -->")

    /** Both vocabulary blocks: the rows between their `VOCAB-ROWS` markers. */
    private val vocabularyText: String
        get() = Regex("""<!-- VOCAB-ROWS:BEGIN -->\n(.*?)<!-- VOCAB-ROWS:END -->""", RegexOption.DOT_MATCHES_ALL).findAll(copy).joinToString("") { it.groupValues[1] }

    private data class Row(val group: String, val key: String, val value: String, val tag: String)

    private val rows: List<Row> by lazy {
        val row = Regex("""^\| `(\w+)` \| `(\w+)` \| `([^`]+)` \| (\w+) \| .+ \|$""")
        vocabularyText.lines().filter { it.isNotBlank() }.map { line ->
            val m = row.matchEntire(line) ?: error("a row of the vocabulary blocks does not read: $line")
            Row(m.groupValues[1], m.groupValues[2], m.groupValues[3], m.groupValues[4])
        }
    }

    private fun values(group: String): List<String> = rows.filter { it.group == group }.map { it.value }

    // ---- the copy is what its header says it is ----

    @Test
    fun `each block of the copy has the sha256 its own header states - the header is the copy's claim about the source`() {
        val header = Regex("""^\| ([ABC]) \| [\d-]+ \| .+ \| `([0-9a-f]{64})` \|$""", RegexOption.MULTILINE).findAll(copy).associate { it.groupValues[1] to it.groupValues[2] }
        assertEquals(setOf("A", "B", "C"), header.keys)
        assertEquals(header["A"], sha256(blockA))
        val vocabularyBlocks = Regex("""<!-- VOCAB-ROWS:BEGIN -->\n(.*?)<!-- VOCAB-ROWS:END -->""", RegexOption.DOT_MATCHES_ALL).findAll(copy).map { it.groupValues[1] }.toList()
        assertEquals("two vocabulary blocks, B and C", 2, vocabularyBlocks.size)
        assertEquals(header["B"], sha256(vocabularyBlocks[0]))
        assertEquals(header["C"], sha256(vocabularyBlocks[1]))
        assertTrue("the header names the commit the copy was taken at", copy.contains("c289a2dc3ec4b52624398737b7a6e838483ca0be"))
    }

    /**
     * Decision Q36 (2026-10-22, contract copy @f3c1bab, block A unchanged at c289a2d): `scanProtocol/{afterScanId}.linkModelCheck` - `LINK_MODEL_MATCH` | `LINK_MODEL_MISMATCH` | `LINK_MODEL_ABSENT`, or null - is the SERVER's field: it is in no request and no
     * reply. The copy says so in its own words, and the app has nothing to read it with: no enumeration, no reader, no text (`MaintenancePairVisibilityTest` pins that no main file spells it).
     */
    @Test
    fun `the server-only field linkModelCheck is in the copy as the server's alone - and the app has no enumeration of its three values`() {
        assertTrue("the field is named in block A", blockA.contains("`linkModelCheck`"))
        assertTrue("and said to be the server's, never the client's", blockA.contains("`linkModelCheck` — **خادمي** (لا يصل العميل)"))
        for (value in listOf("LINK_MODEL_MATCH", "LINK_MODEL_MISMATCH", "LINK_MODEL_ABSENT")) assertTrue("$value is in block A", blockA.contains("`$value`") || blockA.contains("\"$value\""))
        // the three vocabulary rows come after block B's last row: the copy does not carry them, and nothing of the app is compared with them
        assertTrue("no LINK_MODEL_CHECK row in the copy's vocabulary blocks", rows.none { it.group == "LINK_MODEL_CHECK" })
        assertEquals("the app's refusal and failure enumerations hold none of the three values", emptyList<String>(), PairRefusal.values().map { it.wire }.filter { it.startsWith("LINK_MODEL") })
    }

    // ---- the enumerations of the app, against the table ----

    @Test
    fun `DefectType is the contract's DEFECT_TYPE - five values, in the table's order, spelled in the same case, decided by the user`() {
        val table = rows.filter { it.group == "DEFECT_TYPE" }
        assertEquals(listOf("BATTERY", "CHARGING_PORT", "THERMAL", "MOTHERBOARD", "SOFTWARE"), table.map { it.value })
        assertEquals(table.map { it.value }, DefectType.values().map { it.wire })
        assertEquals("the key is the value, for every row", table.map { it.key }, table.map { it.value })
        assertTrue("decided by the user 2026-10-22 (E47)", table.all { it.tag == "decided" })
        // the closed list: no «other», no screen, and the case matters
        for (outside in listOf("OTHER", "SCREEN", "battery", "Battery", "")) assertFalse("'$outside' is outside the list", outside in DefectType.values().map { it.wire })
        assertEquals("no Enum.name on the wire (R8): each wire is its own constant's spelling", DefectType.values().map { it.name }, DefectType.values().map { it.wire })
    }

    @Test
    fun `ScanPurposeChoice is the contract's SETTABLE_SESSION_PURPOSE - normal and the two maintenance purposes - and the two old battery values are historical, not chosen`() {
        assertEquals(listOf("normal", "maintenance_before", "maintenance_after"), values("SETTABLE_SESSION_PURPOSE"))
        assertEquals(values("SETTABLE_SESSION_PURPOSE"), ScanPurposeChoice.values().map { it.wire })
        val scanPurpose = values("SCAN_PURPOSE")
        for (wire in ScanPurposeChoice.values().map { it.wire }) assertTrue("$wire is a SCAN_PURPOSE too", wire in scanPurpose)
        // battery_before / battery_after stay in the server's SCAN_PURPOSE as historical read values (contract A2 addendum) - and are none of what this app asks for
        for (historical in listOf("battery_before", "battery_after")) {
            assertTrue("$historical is still in the contract's SCAN_PURPOSE", historical in scanPurpose)
            assertFalse("$historical is not choosable", historical in values("SETTABLE_SESSION_PURPOSE"))
            assertFalse("$historical is not sent by this app", historical in ScanPurposeChoice.values().map { it.wire })
        }
        // which choice needs a type: exactly the two maintenance ones, and only they
        assertEquals(setOf("maintenance_before", "maintenance_after"), ScanPurposeChoice.values().filter { it.needsDefectType }.map { it.wire }.toSet())
    }

    @Test
    fun `PairRefusal is the contract's two refusal groups less the two the manual way would use - and nothing else`() {
        val callGroup = values("PAIR_CALL_REFUSAL")
        val linkGroup = values("PAIR_LINK_REFUSAL")
        assertEquals("ten at 0b89db1, eleven since S8 (PAIR_OWNER_MISMATCH)", 11, callGroup.size)
        assertEquals(8, linkGroup.size)
        // AMBIGUOUS_SHORT_ID and SESSION_CODE_NOT_REPORT_CODE belong to the manual way (scanning the report's code): a second phase, NOT built, so they are not in the enum
        val manualWay = setOf("AMBIGUOUS_SHORT_ID", "SESSION_CODE_NOT_REPORT_CODE")
        assertTrue(manualWay.all { it in linkGroup })
        assertEquals(callGroup + linkGroup.filter { it !in manualWay }, PairRefusal.values().map { it.wire })
        assertEquals("the seventeen", 17, PairRefusal.values().size)
        for (wire in manualWay) assertEquals("$wire is not a PairRefusal", null, PairRefusal.ofWire(wire))
        // the table's order is the enum's order, group by group
        assertEquals(callGroup, PairRefusal.values().take(11).map { it.wire })
        assertEquals(linkGroup.filter { it !in manualWay }, PairRefusal.values().drop(11).map { it.wire })
    }

    /**
     * Rule 23: REPLACES `the three refusals of the fault type, and the lock, are the contract's PURPOSE_REFUSAL - the app names all four`: the table had four rows at b76c791 and has five at b0a0f66 (Q31 added
     * `PART_NOT_ENABLED` / `PURPOSE_PART_NOT_ENABLED`, `proposal`, A2).
     */
    @Test
    fun `the lock, the three refusals of the fault type and the part-not-enabled refusal are the contract's PURPOSE_REFUSAL - the app names all five, and SET_PURPOSE_INVALID the contract's text names`() {
        assertEquals(
            listOf("PURPOSE_SESSION_NOT_PENDING", "DEFECT_TYPE_REQUIRED", "DEFECT_TYPE_INVALID", "DEFECT_TYPE_NOT_APPLICABLE", "PURPOSE_PART_NOT_ENABLED"),
            values("PURPOSE_REFUSAL")
        )
        val partRow = rows.single { it.group == "PURPOSE_REFUSAL" && it.key == "PART_NOT_ENABLED" }
        assertEquals("the name is a proposal of the server's, the behaviour is Hasan's decision Q31 (the contract's A2 text)", "proposal", partRow.tag)
        assertEquals(SessionErrorCode.PURPOSE_PART_NOT_ENABLED.wire, partRow.value)
        // SET_PURPOSE_INVALID is not a row of the table: the contract's TEXT names it as the answer to a purpose that is none of the three (A2 addendum)
        assertTrue(blockA.contains("`SET_PURPOSE_INVALID`"))
        assertEquals(values("PURPOSE_REFUSAL").toSet() + "SET_PURPOSE_INVALID", SessionErrorCode.purposeCodes.map { it.wire }.toSet())
        // the rule behind the new code, as the contract's text states it: a purpose other than normal needs `purposes`, maintenance_* needs `batteryBeforeAfter` too, `=== true` alone enables (Q31)
        val purposes = ProtocolPart.PURPOSES.wire
        val battery = ProtocolPart.BATTERY_BEFORE_AFTER.wire
        assertTrue(
            "the rule of the two parts",
            blockA.contains("فيُرفض `purpose ≠ normal` إن كان `protocolParts.$purposes !== true`، ويُرفض `maintenance_*` إن كان `protocolParts.$battery !== true` أيضاً")
        )
        assertTrue("the code, its status and its part of the table", blockA.contains("**رمز واحد مسمّى `PURPOSE_PART_NOT_ENABLED`** (`failed-precondition`"))
        assertTrue("normal is accepted as before", blockA.contains("**`normal` يبقى مقبولاً كما كان**"))
        // each is known to the reader of details.reasonCode, so a server that sends one is read as itself and not as an unrecognized code
        for (wire in values("PURPOSE_REFUSAL")) assertTrue(wire, SessionErrorCode.values().any { it.wire == wire })
    }

    // ---- the request and answer lines, against the app's wire constants ----

    @Test
    fun `the request keys the app sends are the ones the contract's lines spell`() {
        val text = blockA
        assertTrue(
            "setSessionPurpose's request",
            text.contains("`setSessionPurpose({${SessionPurposeWire.KEY_SESSION_ID}, ${SessionPurposeWire.KEY_PURPOSE}, ${SessionPurposeWire.KEY_DEFECT_TYPE}?})`")
        )
        assertEquals("setSessionPurpose", SessionPurposeWire.CALLABLE)
        assertTrue("the list: callable and request", text.contains("`${PairLinkWire.LIST_CALLABLE}({${PairLinkWire.KEY_SESSION_ID}})`"))
        assertTrue("the confirmation: callable and request", text.contains("`${PairLinkWire.CONFIRM_CALLABLE}({${PairLinkWire.KEY_SESSION_ID}, ${PairLinkWire.KEY_BEFORE_SCAN_ID}})`"))
        assertTrue(
            "the list's answer",
            text.contains(
                "`{${PairLinkWire.KEY_VALIDITY_HOURS}, ${PairLinkWire.KEY_CANDIDATES}: [{${PairLinkWire.KEY_BEFORE_SCAN_ID}, ${PairLinkWire.KEY_DEVICE_NAME}, ${PairLinkWire.KEY_SCANNED_AT_MS}}]}`"
            )
        )
        assertTrue(
            "the confirmation's answer",
            text.contains("`{${PairLinkWire.KEY_SESSION_ID}, ${PairLinkWire.KEY_BEFORE_SCAN_ID}, ${PairLinkWire.KEY_LINK_METHOD}: \"${PairLinkWire.LINK_METHOD_MANUAL}\"}`")
        )
        assertTrue(
            "deviceName's shape",
            text.contains("`deviceName` = `{${PairLinkWire.KEY_NAME_STATE}, ${PairLinkWire.KEY_NAME_MODEL}, ${PairLinkWire.KEY_NAME_NAMES}, ${PairLinkWire.KEY_NAME_REASON}}`")
        )
        assertTrue("the two states of a name", text.contains("`state ∈ {\"${MarketingNameWire.MAPPED}\", \"${MarketingNameWire.NAME_UNMAPPED}\"}`"))
        assertTrue("the three languages", text.contains("`names = {ar, tr, en}`"))
    }

    @Test
    fun `the contract says scannedAtMs and not scannedAt - the difference from the order is on the record, and the app follows the contract`() {
        val text = blockA
        assertTrue(text.contains("اسم الحقل `scannedAtMs` لا `scannedAt`"))
        assertEquals("scannedAtMs", PairLinkWire.KEY_SCANNED_AT_MS)
        assertFalse("scannedAt is not a key the app reads", PairLinkWire.CANDIDATE_KEYS.contains("scannedAt"))
    }
}
