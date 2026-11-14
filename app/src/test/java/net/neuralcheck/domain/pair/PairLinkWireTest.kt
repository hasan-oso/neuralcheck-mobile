package net.neuralcheck.domain.pair

import com.google.gson.JsonParser
import net.neuralcheck.domain.consent.ConsentLocale
import net.neuralcheck.domain.consent.ConsentLocaleRead
import net.neuralcheck.domain.repeat.TextRead
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.util.Locale

/**
 * The wire of the maintenance link and the reading of its answers - order of 2026-10-22. The expected shapes are written out from the contract copy
 * (`app/src/test/resources/contract/protocol-contract-A2-A3-maintenance-pairs-neuralcheck-backend-c289a2d.md`, A3 items 2 and 3; the enumerations are compared with its table by
 * `MaintenancePairContractCopyTest`). The answers here are WRITTEN BY THE TEST from the contract, not produced by the server (its half is not deployed).
 *
 * Rule 30 (value AND meaning): the answer is read for every kind of JSON value in every key, and what the reader MAKES of each is asserted - a name made up, a candidate dropped, a time read from the
 * wrong key, a number that is not whole and a candidate list that is empty are all different states, none read as another (rules 22 and 25).
 */
class PairLinkWireTest {

    private fun json(text: String) = JsonParser.parseString(text)

    private fun listed(text: String) = OpenBeforesRead.of(json(text))

    private fun read(text: String) = listed(text) as OpenBeforesRead.Read

    private fun unreadable(text: String) = (listed(text) as OpenBeforesRead.Unreadable).why

    private val mappedName = """{"state":"mapped","model":"SM-G975F","names":{"ar":"سامسونج جالاكسي S10+","tr":"Samsung Galaxy S10+","en":"Samsung Galaxy S10+"},"reason":null}"""
    private val unmappedName = """{"state":"name_unmapped","model":"XYZ-1","names":null,"reason":"MODEL_NOT_IN_MAP"}"""

    private fun candidate(id: String = "scan-1", name: String = mappedName, at: String = "1760000000000") = """{"beforeScanId":"$id","deviceName":$name,"scannedAtMs":$at}"""

    private fun answer(hours: String = "72", candidates: String = "[${candidate()}]") = """{"validityHours":$hours,"candidates":$candidates}"""

    // ---- the requests ----

    @Test
    fun `the list's request is the session id and nothing else - the confirmation's is the session id and the scan id`() {
        assertEquals(mapOf("sessionId" to "sess-1"), PairLinkWire.listRequest("sess-1"))
        assertEquals(listOf("sessionId"), PairLinkWire.listRequest("sess-1").keys.toList())
        assertEquals(mapOf("sessionId" to "sess-1", "beforeScanId" to "scan-9"), PairLinkWire.confirmRequest("sess-1", "scan-9"))
        assertEquals(listOf("sessionId", "beforeScanId"), PairLinkWire.confirmRequest("sess-1", "scan-9").keys.toList())
        assertEquals("listOpenMaintenanceBefores", PairLinkWire.LIST_CALLABLE)
        assertEquals("confirmMaintenanceAfterLink", PairLinkWire.CONFIRM_CALLABLE)
    }

    // ---- an answer that reads ----

    @Test
    fun `a full answer reads - the hours, each candidate with its id, name state and time, and no key declared unknown`() {
        val r = read(answer(candidates = "[${candidate("a", mappedName, "1760000000000")},${candidate("b", unmappedName, "1760000100000")}]"))
        assertEquals(72, r.validityHours)
        assertEquals(listOf("a", "b"), r.candidates.map { it.beforeScanId })
        assertEquals(listOf(1760000000000L, 1760000100000L), r.candidates.map { it.scannedAtMs })
        assertTrue(r.candidates[0].deviceName is DeviceNameRead.Mapped)
        assertTrue(r.candidates[1].deviceName is DeviceNameRead.Unmapped)
        assertTrue(r.unknownKeys.isEmpty())
        assertTrue(r.candidateUnknownKeys.isEmpty())
    }

    @Test
    fun `an empty candidate list is the none-open state - a read answer, not a failure`() {
        val r = read(answer(hours = "24", candidates = "[]"))
        assertEquals(24, r.validityHours)
        assertTrue(r.candidates.isEmpty())
    }

    @Test
    fun `the order is the server's - newest first is the contract's, and the reader keeps it, never sorts`() {
        val r = read(answer(candidates = "[${candidate("old", at = "1000")},${candidate("new", at = "9000")}]"))
        assertEquals("kept as sent", listOf("old", "new"), r.candidates.map { it.beforeScanId })
    }

    // ---- an answer that does not read: every kind of value in every key ----

    @Test
    fun `an answer that is not an object is unreadable with its kind`() {
        assertEquals("absent", (OpenBeforesRead.of(null) as OpenBeforesRead.Unreadable).why)
        assertEquals("absent", unreadable("null"))
        for ((text, kind) in listOf("[]" to "array", "\"x\"" to "string", "7" to "number", "true" to "boolean")) {
            assertEquals(text, "not_an_object_$kind", unreadable(text))
        }
    }

    @Test
    fun `the validity hours is read for every kind - only a whole number from one up is a number of hours`() {
        assertEquals("validity_hours_absent", unreadable("""{"candidates":[]}"""))
        assertEquals("validity_hours_absent", unreadable("""{"validityHours":null,"candidates":[]}"""))
        for ((value, why) in listOf(
            "\"72\"" to "validity_hours_not_a_number_string", "true" to "validity_hours_not_a_number_boolean", "[]" to "validity_hours_not_a_number_array",
            "{}" to "validity_hours_not_a_number_object", "72.5" to "validity_hours_not_whole", "0" to "validity_hours_out_of_range", "-3" to "validity_hours_out_of_range"
        )) {
            assertEquals("validityHours=$value", why, unreadable(answer(hours = value, candidates = "[]")))
        }
        assertEquals("a whole number written with a fraction is whole", 72, read(answer(hours = "72.0", candidates = "[]")).validityHours)
        assertEquals("the contract's upper end", 8760, read(answer(hours = "8760", candidates = "[]")).validityHours)
    }

    @Test
    fun `the candidates are read for every kind - a missing list is not an empty one`() {
        assertEquals("candidates_absent", unreadable("""{"validityHours":72}"""))
        assertEquals("candidates_absent", unreadable("""{"validityHours":72,"candidates":null}"""))
        for ((value, kind) in listOf("\"x\"" to "string", "{}" to "object", "7" to "number", "true" to "boolean")) {
            assertEquals(value, "candidates_not_an_array_$kind", unreadable(answer(candidates = value)))
        }
        assertEquals("candidate_0_not_an_object_string", unreadable(answer(candidates = "[\"x\"]")))
        assertEquals("candidate_1_not_an_object_null", unreadable(answer(candidates = "[${candidate()},null]")))
    }

    @Test
    fun `one candidate that does not read makes the whole answer unreadable - nothing is dropped and nothing is drawn from half`() {
        val bad = listOf(
            """{"deviceName":$mappedName,"scannedAtMs":1760000000000}""" to "candidate_1_before_scan_id_absent",
            """{"beforeScanId":null,"deviceName":$mappedName,"scannedAtMs":1760000000000}""" to "candidate_1_before_scan_id_absent",
            """{"beforeScanId":7,"deviceName":$mappedName,"scannedAtMs":1760000000000}""" to "candidate_1_before_scan_id_not_a_text",
            """{"beforeScanId":"  ","deviceName":$mappedName,"scannedAtMs":1760000000000}""" to "candidate_1_before_scan_id_blank",
            """{"beforeScanId":"b","scannedAtMs":1760000000000}""" to "candidate_1_device_name_absent",
            """{"beforeScanId":"b","deviceName":"Samsung","scannedAtMs":1760000000000}""" to "candidate_1_device_name_not_an_object_string",
            """{"beforeScanId":"b","deviceName":$mappedName}""" to "candidate_1_scanned_at_ms_absent",
            """{"beforeScanId":"b","deviceName":$mappedName,"scannedAtMs":"1760000000000"}""" to "candidate_1_scanned_at_ms_not_a_number_string",
            """{"beforeScanId":"b","deviceName":$mappedName,"scannedAtMs":1760000000000.5}""" to "candidate_1_scanned_at_ms_not_whole",
            """{"beforeScanId":"b","deviceName":$mappedName,"scannedAtMs":0}""" to "candidate_1_scanned_at_ms_not_positive",
            """{"beforeScanId":"b","deviceName":$mappedName,"scannedAtMs":-5}""" to "candidate_1_scanned_at_ms_not_positive"
        )
        for ((item, why) in bad) {
            assertEquals(item, why, unreadable(answer(candidates = "[${candidate("a")},$item]")))
        }
    }

    @Test
    fun `the order's scannedAt is not the contract's scannedAtMs - an answer that carries it instead has no time, and the key is declared`() {
        val instead = """{"beforeScanId":"a","deviceName":$mappedName,"scannedAt":1760000000000}"""
        assertEquals("candidate_0_scanned_at_ms_absent", unreadable(answer(candidates = "[$instead]")))
        // and beside the contract's key it is declared, never read as the time
        val both = """{"beforeScanId":"a","deviceName":$mappedName,"scannedAtMs":1760000000000,"scannedAt":1}"""
        val r = read(answer(candidates = "[$both]"))
        assertEquals(1760000000000L, r.candidates.single().scannedAtMs)
        assertEquals(listOf("scannedAt"), r.candidateUnknownKeys)
    }

    @Test
    fun `two candidates with the same scan id are unreadable - the choice would name two scans`() {
        assertEquals("duplicate_before_scan_id", unreadable(answer(candidates = "[${candidate("a")},${candidate("a", at = "2")}]")))
    }

    @Test
    fun `keys that are none of the contract's are declared, sorted - at the top, on a candidate and on a device name`() {
        val c = """{"beforeScanId":"a","hardwareId":"x","deviceName":{"state":"name_unmapped","model":"M","names":null,"reason":"MODEL_ABSENT","extra":1},"scannedAtMs":5}"""
        val r = read("""{"validityHours":72,"zeta":1,"alpha":2,"candidates":[$c]}""")
        assertEquals(listOf("alpha", "zeta"), r.unknownKeys)
        assertEquals(listOf("extra", "hardwareId"), r.candidateUnknownKeys)
    }

    // ---- the device name ----

    @Test
    fun `a device name has two states and nothing in between - mapped with its names, or unmapped with its model and reason`() {
        val m = read(answer(candidates = "[${candidate(name = mappedName)}]")).candidates.single().deviceName as DeviceNameRead.Mapped
        assertEquals(TextRead.Text("سامسونج جالاكسي S10+"), m.ar)
        assertEquals(TextRead.Text("Samsung Galaxy S10+"), m.tr)
        assertEquals(TextRead.Text("Samsung Galaxy S10+"), m.en)
        assertEquals(TextRead.Text("SM-G975F"), m.model)
        val u = read(answer(candidates = "[${candidate(name = unmappedName)}]")).candidates.single().deviceName as DeviceNameRead.Unmapped
        assertEquals(TextRead.Text("XYZ-1"), u.model)
        assertEquals(TextRead.Text("MODEL_NOT_IN_MAP"), u.reason)
    }

    @Test
    fun `a device name that is none of the two is unreadable - a missing state, another kind, an unknown one, a mapped name with no names`() {
        val cases = listOf(
            """{"model":"M"}""" to "candidate_0_device_name_state_not_a_text",
            """{"state":7}""" to "candidate_0_device_name_state_not_a_text",
            """{"state":"renamed","model":"M"}""" to "candidate_0_device_name_state_unrecognized",
            """{"state":"mapped","model":"M"}""" to "candidate_0_device_name_mapped_without_names",
            """{"state":"mapped","model":"M","names":null}""" to "candidate_0_device_name_mapped_without_names",
            """{"state":"mapped","model":"M","names":"x"}""" to "candidate_0_device_name_mapped_without_names"
        )
        for ((name, why) in cases) assertEquals(name, why, unreadable(answer(candidates = "[${candidate(name = name)}]")))
    }

    @Test
    fun `an unmapped name with no model is a state of its own - and a mapped name keeps a language it has no text for as not a text`() {
        val noModel = read(answer(candidates = """[${candidate(name = """{"state":"name_unmapped","names":null,"reason":"MODEL_ABSENT"}""")}]""")).candidates.single().deviceName as DeviceNameRead.Unmapped
        assertEquals(TextRead.Absent, noModel.model)
        val partial = read(answer(candidates = """[${candidate(name = """{"state":"mapped","names":{"ar":"اسم","tr":null,"en":""}}""")}]""")).candidates.single().deviceName as DeviceNameRead.Mapped
        assertEquals(TextRead.Text("اسم"), partial.ar)
        assertEquals(TextRead.Null, partial.tr)
        assertEquals(TextRead.Blank(""), partial.en)
    }

    private fun named(text: String) = read(answer(candidates = "[${candidate(name = text)}]")).candidates.single().deviceName

    private val arabic = ConsentLocaleRead.Known(ConsentLocale.AR)
    private val turkish = ConsentLocaleRead.Known(ConsentLocale.TR)

    @Test
    fun `the name shown follows the screen's language - the marketing name, or the phone's own model string declared as such, or no name - never one made up`() {
        assertEquals(CandidateName.Marketing("سامسونج جالاكسي S10+"), nameFor(named(mappedName), arabic))
        assertEquals(CandidateName.Marketing("Samsung Galaxy S10+"), nameFor(named(mappedName), turkish))
        // not mapped: the model string the phone reported - a state of its own, NOT a marketing name
        assertEquals(CandidateName.Model("XYZ-1", "unmapped"), nameFor(named(unmappedName), arabic))
        assertEquals(CandidateName.Model("XYZ-1", "unmapped"), nameFor(named(unmappedName), turkish))
        // not mapped and no model: nothing to show, said so
        assertEquals(
            CandidateName.NoName("unmapped_no_model"),
            nameFor(named("""{"state":"name_unmapped","names":null,"reason":"MODEL_ABSENT"}"""), arabic)
        )
        assertEquals(
            CandidateName.NoName("unmapped_no_model"),
            nameFor(named("""{"state":"name_unmapped","model":"  ","names":null,"reason":"MODEL_ABSENT"}"""), arabic)
        )
    }

    @Test
    fun `a mapped name with no text in the screen's language falls to the model string declared, and to no name when there is none - and an unsupported or missing language chooses no marketing name`() {
        val noTurkish = named("""{"state":"mapped","model":"SM-G975F","names":{"ar":"اسم","tr":null,"en":"Name"}}""")
        assertEquals(CandidateName.Marketing("اسم"), nameFor(noTurkish, arabic))
        assertEquals(CandidateName.Model("SM-G975F", "language_name_absent"), nameFor(noTurkish, turkish))
        val noTurkishNoModel = named("""{"state":"mapped","names":{"ar":"اسم","tr":"","en":"Name"}}""")
        assertEquals(CandidateName.NoName("language_name_absent_no_model"), nameFor(noTurkishNoModel, turkish))
        // the language is the screen's: an unsupported one (the app has no English screen) or none does not pick the English name
        assertEquals(CandidateName.Model("SM-G975F", "language_not_supported"), nameFor(named(mappedName), ConsentLocaleRead.Unsupported("fr")))
        assertEquals(CandidateName.Model("SM-G975F", "language_absent"), nameFor(named(mappedName), ConsentLocaleRead.Absent))
        assertEquals(CandidateName.NoName("language_absent_no_model"), nameFor(noTurkishNoModel, ConsentLocaleRead.Absent))
    }

    @Test
    fun `a row is the name, the date and the hour of the before scan in the phone's zone - and holds no scan id`() {
        val c = read(answer(candidates = "[${candidate("secret-scan-id", mappedName, "1760000000000")}]")).candidates.single()
        // 1760000000000 ms = 2025-10-09T08:53:20Z
        val utc = candidateRow(c, arabic, ZoneId.of("UTC"), Locale.US)
        assertEquals(CandidateName.Marketing("سامسونج جالاكسي S10+"), utc.name)
        assertEquals("Oct 9, 2025", utc.date)
        assertEquals("8:53 AM", utc.time.plain())
        val istanbul = candidateRow(c, turkish, ZoneId.of("Europe/Istanbul"), Locale.US)
        assertEquals("the same instant in the +03 zone", "11:53 AM", istanbul.time.plain())
        assertEquals("Oct 9, 2025", istanbul.date)
        assertFalse("no scan id in what a row shows", utc.toString().contains("secret-scan-id"))
        // a moment that crosses midnight changes the date with the zone
        val late = read(answer(candidates = "[${candidate("x", mappedName, "1760054400000")}]")).candidates.single() // 2025-10-10T00:00:00Z
        assertEquals("Oct 9, 2025", candidateRow(late, arabic, ZoneId.of("America/New_York"), Locale.US).date)
        assertEquals("Oct 10, 2025", candidateRow(late, arabic, ZoneId.of("UTC"), Locale.US).date)
    }

    /** JDK 20 and later write a narrow no-break space before AM/PM; the test compares the text with plain spaces so it does not depend on the JDK's data. */
    private fun String.plain() = replace('\u202f', ' ').replace('\u00a0', ' ')

    // ---- the confirmation's answer ----

    private fun confirmed(text: String?) = ConfirmAnswerRead.of(text?.let { json(it) }, "sess-1", "scan-9")

    @Test
    fun `the confirmation is the echo of the request and linkMethod manual - nothing else is a confirmation`() {
        assertEquals(ConfirmAnswerRead.Confirmed, confirmed("""{"sessionId":"sess-1","beforeScanId":"scan-9","linkMethod":"manual"}"""))
        val cases = listOf(
            null to "absent",
            "null" to "absent",
            "[]" to "not_an_object_array",
            "\"ok\"" to "not_an_object_string",
            """{"beforeScanId":"scan-9","linkMethod":"manual"}""" to "session_id_missing",
            """{"sessionId":7,"beforeScanId":"scan-9","linkMethod":"manual"}""" to "session_id_missing",
            """{"sessionId":"sess-2","beforeScanId":"scan-9","linkMethod":"manual"}""" to "session_id_differs",
            """{"sessionId":"sess-1","linkMethod":"manual"}""" to "before_scan_id_missing",
            """{"sessionId":"sess-1","beforeScanId":"scan-8","linkMethod":"manual"}""" to "before_scan_id_differs",
            """{"sessionId":"sess-1","beforeScanId":"scan-9"}""" to "link_method_absent",
            """{"sessionId":"sess-1","beforeScanId":"scan-9","linkMethod":null}""" to "link_method_absent",
            """{"sessionId":"sess-1","beforeScanId":"scan-9","linkMethod":"auto_confirmed"}""" to "link_method_not_manual",
            """{"sessionId":"sess-1","beforeScanId":"scan-9","linkMethod":"Manual"}""" to "link_method_not_manual"
        )
        for ((text, why) in cases) assertEquals("$text", ConfirmAnswerRead.Unreadable(why), confirmed(text))
    }

    // ---- the refusals ----

    @Test
    fun `every refusal has a place in the failure texts - the six named ones go to their own, the eight with no text of their own to the general one - and nothing is read as another`() {
        val expected = mapOf(
            PairRefusal.BEFORE_NOT_FOUND to PairFailureKind.NOT_AVAILABLE,
            PairRefusal.BEFORE_NOT_OWNED to PairFailureKind.NOT_AVAILABLE,
            PairRefusal.BEFORE_WRONG_PURPOSE to PairFailureKind.NOT_AVAILABLE,
            PairRefusal.BEFORE_ALREADY_PAIRED to PairFailureKind.ALREADY_PAIRED,
            PairRefusal.BEFORE_EXPIRED to PairFailureKind.EXPIRED,
            PairRefusal.BEFORE_WRONG_DEFECT_TYPE to PairFailureKind.WRONG_DEFECT_TYPE,
            PairRefusal.OWNER_MISMATCH to PairFailureKind.NOT_AVAILABLE,
            PairRefusal.PART_NOT_ENABLED to PairFailureKind.NOT_ENABLED,
            PairRefusal.VALIDITY_SETTING_ABSENT to PairFailureKind.NOT_ENABLED,
            PairRefusal.NOT_AUTHENTICATED to PairFailureKind.GENERAL,
            PairRefusal.SESSION_ID_REQUIRED to PairFailureKind.GENERAL,
            PairRefusal.SESSION_NOT_AFTER to PairFailureKind.GENERAL,
            PairRefusal.SESSION_NOT_ACTIVE to PairFailureKind.GENERAL,
            PairRefusal.SESSION_DEFECT_TYPE_ABSENT to PairFailureKind.GENERAL,
            PairRefusal.BEFORE_SCAN_ID_REQUIRED to PairFailureKind.GENERAL,
            PairRefusal.BEFORE_UNREADABLE to PairFailureKind.GENERAL,
            PairRefusal.SCAN_CAP_EXCEEDED to PairFailureKind.GENERAL
        )
        assertEquals("all seventeen, none missing", PairRefusal.values().toSet(), expected.keys)
        for (refusal in PairRefusal.values()) {
            assertEquals(refusal.wire, expected[refusal], failureKindOf(refusal))
            assertEquals("from the raw text too: ${refusal.wire}", expected[refusal], failureKindOf(refusal.wire))
        }
        // every kind is reachable, and the general one is also what any other failure gets
        assertEquals(PairFailureKind.values().toSet(), expected.values.toSet())
        for (other in listOf<String?>(null, "", "SESSION_EXPIRED", "SESSION_NOT_PARTICIPANT", "BEFORE_BRAND_NEW", "before_expired", "AMBIGUOUS_SHORT_ID", "SESSION_CODE_NOT_REPORT_CODE")) {
            assertEquals("$other", PairFailureKind.GENERAL, failureKindOf(other))
        }
    }

    @Test
    fun `the case of a refusal matters - an upper-case code is not a lower-case one`() {
        assertNull(PairRefusal.ofWire("before_already_paired"))
        assertEquals(PairRefusal.BEFORE_ALREADY_PAIRED, PairRefusal.ofWire("BEFORE_ALREADY_PAIRED"))
    }
}
