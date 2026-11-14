package net.neuralcheck.domain.pair

import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import net.neuralcheck.data.remote.DefectType
import net.neuralcheck.data.remote.PurposeRequestBuild
import net.neuralcheck.data.remote.ScanPurposeChoice
import net.neuralcheck.data.remote.SessionPurposeWire
import net.neuralcheck.files.AppRequestPayloadGoldenTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * The requests of the maintenance link - order of 2026-10-22 (rules 39 and 41). What leaves the phone for `setSessionPurpose` with its fault type, `listOpenMaintenanceBefores` and
 * `confirmMaintenanceAfterLink`, key by key, built by the REAL producers ([SessionPurposeWire.request], [PairLinkWire.listRequest], [PairLinkWire.confirmRequest]) - the functions `FirebaseAnalysisService` hands to
 * the SDK, which `MaintenancePairRequestRealPathTest` proves by comparing what the SDK mock receives with these files; and five golden samples of them, for the server's reader (rule 41: a reader is tested with a
 * sample made by the real producer, at the point where it reads). They are copied byte for byte to `neuralcheck-backend` BY ITS AGENT; this repository writes nothing there.
 *
 * Rule 39: a change to the wire regenerates the samples in the SAME commit (`NC_REGENERATE_GOLDEN=1`, see `derived/README.md`); the committed files are compared with what the builders make today, byte for byte, and the
 * values in them are compared with the contract copy's vocabulary table, so a sample cannot keep a purpose or a type the contract no longer has. The ids are made-up (`sess-golden-*`).
 */
class MaintenancePairRequestTest {

    private val gson = GsonBuilder().setPrettyPrinting().serializeNulls().disableHtmlEscaping().create()

    private fun purposeRequest(sessionId: String, choice: ScanPurposeChoice, type: DefectType?): String =
        gson.toJson((SessionPurposeWire.request(sessionId, choice, type) as PurposeRequestBuild.Built).request) + "\n"

    /** The five samples: file name to text, in this order. */
    private fun samples(): Map<String, String> = linkedMapOf(
        "maintenance-pair-request-set-session-purpose-maintenance-before-battery.json" to purposeRequest("sess-golden-1", ScanPurposeChoice.MAINTENANCE_BEFORE, DefectType.BATTERY),
        "maintenance-pair-request-set-session-purpose-maintenance-after-charging-port.json" to purposeRequest("sess-golden-2", ScanPurposeChoice.MAINTENANCE_AFTER, DefectType.CHARGING_PORT),
        "maintenance-pair-request-set-session-purpose-normal-no-defect-type.json" to purposeRequest("sess-golden-3", ScanPurposeChoice.NORMAL, null),
        "maintenance-pair-request-list-open-maintenance-befores.json" to gson.toJson(PairLinkWire.listRequest("sess-golden-2")) + "\n",
        "maintenance-pair-request-confirm-maintenance-after-link.json" to gson.toJson(PairLinkWire.confirmRequest("sess-golden-2", "scan-golden-before-1")) + "\n"
    )

    private val begin = "<!-- maintenance-pair-sha256:begin -->"
    private val end = "<!-- maintenance-pair-sha256:end -->"

    private fun sha256(bytes: ByteArray) = java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    /** README.md as LF text (a Windows checkout turns it into CRLF; the samples themselves are `-text`). */
    private fun readme(): String = File(AppRequestPayloadGoldenTest.derivedDir, "README.md").readText(Charsets.UTF_8).replace("\r\n", "\n")

    private fun shaBlock(): String = buildString {
        append(begin).append('\n')
        for (name in samples().keys) append("- `").append(name).append("` sha256 `").append(sha256(File(AppRequestPayloadGoldenTest.derivedDir, name).readBytes())).append("`\n")
        append(end)
    }

    @Before
    fun regenerateWhenAsked() {
        if (System.getenv(AppRequestPayloadGoldenTest.REGENERATE_ENV) == "1") {
            for ((name, text) in samples()) File(AppRequestPayloadGoldenTest.derivedDir, name).writeBytes(text.toByteArray(Charsets.UTF_8))
            val text = readme()
            val from = text.indexOf(begin)
            val to = text.indexOf(end)
            check(from >= 0 && to > from) { "README.md must carry the $begin ... $end block" }
            File(AppRequestPayloadGoldenTest.derivedDir, "README.md").writeText(text.substring(0, from) + shaBlock() + text.substring(to + end.length), Charsets.UTF_8)
        }
    }

    @Test
    fun `the committed samples are what the real builders make today - byte for byte`() {
        for ((name, text) in samples()) {
            val file = File(AppRequestPayloadGoldenTest.derivedDir, name)
            assertTrue("$name is not committed - regenerate with ${AppRequestPayloadGoldenTest.REGENERATE_ENV}=1 (see MaintenancePairRequestTest)", file.isFile)
            assertEquals("$name drifted from the builder (rule 39: regenerate in the same change)", text, String(file.readBytes(), Charsets.UTF_8))
        }
    }

    @Test
    fun `README carries the sha256 of every sample - what a copy receives is what the README says`() {
        val text = readme()
        val block = text.substring(text.indexOf(begin), text.indexOf(end) + end.length)
        assertEquals(shaBlock(), block)
    }

    @Test
    fun `each sample has the contract's keys in order - defectType for a maintenance purpose, ABSENT (not null) for normal, and the list and the confirmation carry the ids alone`() {
        val parsed = samples().mapValues { JsonParser.parseString(it.value).asJsonObject }
        val byName = parsed.entries.associate { it.key.removePrefix("maintenance-pair-request-").removeSuffix(".json") to it.value }
        assertEquals(listOf("sessionId", "purpose", "defectType"), byName.getValue("set-session-purpose-maintenance-before-battery").keySet().toList())
        assertEquals(listOf("sessionId", "purpose", "defectType"), byName.getValue("set-session-purpose-maintenance-after-charging-port").keySet().toList())
        val normal = byName.getValue("set-session-purpose-normal-no-defect-type")
        assertEquals(listOf("sessionId", "purpose"), normal.keySet().toList())
        assertFalse("the server counts a defectType key that is there - even a null - as sent", normal.has("defectType"))
        assertEquals(listOf("sessionId"), byName.getValue("list-open-maintenance-befores").keySet().toList())
        assertEquals(listOf("sessionId", "beforeScanId"), byName.getValue("confirm-maintenance-after-link").keySet().toList())
        // every value is a string: no null, no number, no object
        for ((name, json) in parsed) for ((key, value) in json.entrySet()) assertTrue("$name.$key", value.isJsonPrimitive && value.asJsonPrimitive.isString)
    }

    @Test
    fun `the purposes and the fault types in the samples are rows of the contract copy's vocabulary - a sample cannot keep a value the contract no longer has`() {
        val copy = javaClass.classLoader!!.getResourceAsStream("contract/protocol-contract-A2-A3-maintenance-pairs-neuralcheck-backend-c289a2d.md")!!.readBytes().toString(Charsets.UTF_8)
        fun values(group: String) = Regex("""\| `$group` \| `\w+` \| `([^`]+)` \|""").findAll(copy).map { it.groupValues[1] }.toList()
        val settable = values("SETTABLE_SESSION_PURPOSE")
        val types = values("DEFECT_TYPE")
        assertEquals(3, settable.size)
        assertEquals(5, types.size)
        for ((name, text) in samples()) {
            val json = JsonParser.parseString(text).asJsonObject
            json.get("purpose")?.let { assertTrue("$name: purpose ${it.asString} is not choosable in the contract", it.asString in settable) }
            json.get("defectType")?.let { assertTrue("$name: defectType ${it.asString} is not in the contract", it.asString in types) }
        }
        // the samples together show both maintenance purposes, two different types and the absence for normal
        val all = samples().values.map { JsonParser.parseString(it).asJsonObject }
        assertEquals(setOf("maintenance_before", "maintenance_after", "normal"), all.mapNotNull { it.get("purpose")?.asString }.toSet())
        assertEquals(setOf("BATTERY", "CHARGING_PORT"), all.mapNotNull { it.get("defectType")?.asString }.toSet())
    }
}
