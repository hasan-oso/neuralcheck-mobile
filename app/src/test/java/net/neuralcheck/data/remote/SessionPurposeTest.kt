package net.neuralcheck.data.remote

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The wire of the purpose chips and the reading of `protocolParts` - A-P6-03 (غرض الفحص). The expected values are WRITTEN OUT here from the protocol contract copy
 * (`evidence/run-2026-10-18/protocol-contract-A1-A13-neuralcheck-backend-8e5c426.md`, A2 and A10; from neuralcheck-backend@8e5c426, a copy that drifts): a golden pin, so an enum that drifts from the
 * contract's spelling fails here and not on a technician's phone (rule 24). Since 2026-10-22 the purposes and the fault type are also compared with the contract copy of neuralcheck-backend@f3c1bab
 * (`MaintenancePairContractCopyTest`), and the request builder is run over every combination below.
 *
 * Rule 30 (value AND meaning): `protocolParts` is read for EVERY kind of JSON value, in the key and in each part, and what a reader MAKES of each is asserted - [ProtocolPartsRead.purposesOffered] is true
 * for exactly one input, and every other shape is a declared state, none of them read as "on" and none read as another.
 */
class SessionPurposeTest {

    private fun json(text: String) = JsonParser.parseString(text)

    // ---- the wire values ----

    /**
     * Rule 23: REPLACES `the purposes the app can ask for are the three the contract lists at creation` (it pinned `normal`, `battery_before`, `battery_after` - the list the order of 2026-10-22 changes: contract A2
     * addendum, copy @f3c1bab, `SETTABLE_SESSION_PURPOSE` = `normal`, `maintenance_before`, `maintenance_after`; the two battery values stay in the server's `SCAN_PURPOSE` as historical read values and are no longer chosen).
     */
    @Test
    fun `the purposes the app can ask for are the three the contract lists at creation since 2026-10-22 - and none of the server's derived, outside or historical values`() {
        assertEquals(listOf("normal", "maintenance_before", "maintenance_after"), ScanPurposeChoice.values().map { it.wire })
        val exported = ScanPurposeChoice.values().map { it.wire }.toSet()
        // contract A2: derived by the server (repeat_*), outside the sessions (NO_SESSION), not declared (UNDECLARED) - this app asks for none of them; battery_* are historical read values, no longer chosen
        for (serverOnly in listOf("repeat_cold", "repeat_hot", "repeat_unclassified", "NO_SESSION", "UNDECLARED", "battery_before", "battery_after")) {
            assertFalse("$serverOnly is the server's, not a chip", serverOnly in exported)
        }
        // contract A2: «`return` أُسقطت رقاقةً عند الإنشاء (قرار 2026-10-10) ... لا تُصدَّران»
        assertFalse("return was dropped as a chip", "return" in exported)
        assertFalse("return_not_working was dropped with it", "return_not_working" in exported)
    }

    @Test
    fun `the six protocol parts are those of contract A10 - and the design's old name batteryPairs is not exported`() {
        assertEquals(
            listOf("purposes", "batteryBeforeAfter", "coldRepeat", "returns", "doubleEval", "consent"),
            ProtocolPart.values().map { it.wire }
        )
        assertFalse("batteryPairs collided with an existing payload field and was renamed", "batteryPairs" in ProtocolPart.values().map { it.wire })
    }

    @Test
    fun `the callable and its request keys are one source - the name and the three keys are the contract's (A2 addendum of 2026-10-22, copy @f3c1bab)`() {
        assertEquals("setSessionPurpose", SessionPurposeWire.CALLABLE)
        assertEquals("sessionId", SessionPurposeWire.KEY_SESSION_ID)
        assertEquals("purpose", SessionPurposeWire.KEY_PURPOSE)
        assertEquals("defectType", SessionPurposeWire.KEY_DEFECT_TYPE)
        assertEquals("protocolParts", ProtocolPartsRead.KEY)
    }

    /**
     * Rule 23: REPLACES `PURPOSE_SESSION_NOT_PENDING is the one code setSessionPurpose throws` (it pinned a set of ONE: the contract addendum of 2026-10-22, copy @b76c791, adds `DEFECT_TYPE_REQUIRED`, `DEFECT_TYPE_INVALID`
     * and `DEFECT_TYPE_NOT_APPLICABLE` to `PURPOSE_REFUSAL`) and then `SET_PURPOSE_INVALID`, which the server's reply samples carry for a purpose that is none of the three (found by `MaintenancePairWireSamplesTest`);
     * and then (decision Q31, copy @f3c1bab: `PURPOSE_REFUSAL` / `PART_NOT_ENABLED`) `PURPOSE_PART_NOT_ENABLED`, the refusal of a shop that lacks the part the purpose needs - the set of five is now six.
     */
    @Test
    fun `setSessionPurpose throws six codes - the lock, the three of the fault type, the invalid purpose and the part not enabled - and no other call throws any of them`() {
        assertEquals("PURPOSE_SESSION_NOT_PENDING", SessionErrorCode.PURPOSE_SESSION_NOT_PENDING.wire)
        assertEquals("PURPOSE_PART_NOT_ENABLED", SessionErrorCode.PURPOSE_PART_NOT_ENABLED.wire)
        assertEquals(
            setOf(
                SessionErrorCode.PURPOSE_SESSION_NOT_PENDING, SessionErrorCode.DEFECT_TYPE_REQUIRED, SessionErrorCode.DEFECT_TYPE_INVALID, SessionErrorCode.DEFECT_TYPE_NOT_APPLICABLE,
                SessionErrorCode.SET_PURPOSE_INVALID, SessionErrorCode.PURPOSE_PART_NOT_ENABLED
            ),
            SessionErrorCode.purposeCodes
        )
        assertEquals(setOf("DEFECT_TYPE_REQUIRED", "DEFECT_TYPE_INVALID", "DEFECT_TYPE_NOT_APPLICABLE"), listOf(SessionErrorCode.DEFECT_TYPE_REQUIRED, SessionErrorCode.DEFECT_TYPE_INVALID, SessionErrorCode.DEFECT_TYPE_NOT_APPLICABLE).map { it.wire }.toSet())
        val others = SessionErrorCode.joinCodes + SessionErrorCode.cancelCodes + SessionErrorCode.createCodes + SessionErrorCode.analyzeCodes
        for (code in SessionErrorCode.purposeCodes) assertFalse(code.wire, code in others)
    }

    // ---- the request: the choice and the fault type go together, or nothing is built (contract A2 addendum) ----

    private fun built(choice: ScanPurposeChoice, defectType: DefectType?) =
        (SessionPurposeWire.request("sess-1", choice, defectType) as PurposeRequestBuild.Built).request

    private fun rejected(choice: ScanPurposeChoice, defectType: DefectType?) =
        (SessionPurposeWire.request("sess-1", choice, defectType) as PurposeRequestBuild.Rejected).reason

    @Test
    fun `a maintenance purpose is built for each of the five fault types with defectType in the contract's order of keys - for both maintenance choices`() {
        for (choice in listOf(ScanPurposeChoice.MAINTENANCE_BEFORE, ScanPurposeChoice.MAINTENANCE_AFTER)) {
            for (type in DefectType.values()) {
                val request = built(choice, type)
                assertEquals("${choice.wire}/${type.wire}", listOf("sessionId", "purpose", "defectType"), request.keys.toList())
                assertEquals(mapOf("sessionId" to "sess-1", "purpose" to choice.wire, "defectType" to type.wire), request)
            }
        }
    }

    @Test
    fun `normal is built with no defectType key at all - not a null, because the server counts a key that is there as sent`() {
        val request = built(ScanPurposeChoice.NORMAL, null)
        assertEquals(listOf("sessionId", "purpose"), request.keys.toList())
        assertEquals(mapOf("sessionId" to "sess-1", "purpose" to "normal"), request)
        assertFalse(request.containsKey("defectType"))
    }

    @Test
    fun `a maintenance purpose with no fault type is refused locally - and so is normal with one - for every combination, nothing is built`() {
        for (choice in listOf(ScanPurposeChoice.MAINTENANCE_BEFORE, ScanPurposeChoice.MAINTENANCE_AFTER)) {
            assertEquals(choice.wire, PurposeRequestRejection.DEFECT_TYPE_MISSING, rejected(choice, null))
        }
        for (type in DefectType.values()) {
            assertEquals(type.wire, PurposeRequestRejection.DEFECT_TYPE_NOT_APPLICABLE, rejected(ScanPurposeChoice.NORMAL, type))
        }
        // the rule is written once and the two entry points agree over the whole 3 x 6 grid
        for (choice in ScanPurposeChoice.values()) {
            for (type in DefectType.values().toList() + null) {
                val rejection = purposeRequestRejection(choice, type)
                val build = SessionPurposeWire.request("s", choice, type)
                assertEquals("${choice.wire}/${type?.wire}", rejection == null, build is PurposeRequestBuild.Built)
                assertEquals("${choice.wire}/${type?.wire}", rejection == null, choice.needsDefectType == (type != null))
            }
        }
        assertEquals(setOf("defect_type_missing", "defect_type_not_applicable"), PurposeRequestRejection.values().map { it.token }.toSet())
    }

    // ---- the key itself, in every shape ----

    @Test
    fun `a missing key and a JSON null are absent - the same named state, not an empty object`() {
        assertEquals(ProtocolPartsRead.Absent, ProtocolPartsRead.of(null))
        assertEquals(ProtocolPartsRead.Absent, ProtocolPartsRead.of(json("null")))
        assertFalse(ProtocolPartsRead.Absent.purposesOffered())
    }

    @Test
    fun `a key that is not an object is a named state with its kind, and offers nothing`() {
        for ((text, kind) in listOf("\"purposes\"" to "string", "7" to "number", "true" to "boolean", "false" to "boolean", "[]" to "array", "[true]" to "array", "1.5" to "number")) {
            val read = ProtocolPartsRead.of(json(text))
            assertEquals(text, ProtocolPartsRead.NotAnObject(kind), read)
            assertFalse(text, read.purposesOffered())
        }
    }

    @Test
    fun `an empty object lists no part - every one of the six is not listed, and that is the contract's off`() {
        val read = ProtocolPartsRead.of(json("{}")) as ProtocolPartsRead.Read
        assertEquals(ProtocolPart.values().toSet(), read.parts.keys)
        for (part in ProtocolPart.values()) assertEquals(part.wire, PartRead.NotListed, read.parts[part])
        assertTrue(read.unknownKeys.isEmpty())
        assertFalse(read.purposesOffered())
        assertFalse(read.maintenanceOffered())
    }

    @Test
    fun `a part read for every kind of value - only the JSON true is on`() {
        val expected = listOf(
            "true" to PartRead.Enabled,
            "false" to PartRead.Disabled,
            "null" to PartRead.Unrecognized("null"),
            "\"true\"" to PartRead.Unrecognized("string"),
            "\"yes\"" to PartRead.Unrecognized("string"),
            "1" to PartRead.Unrecognized("number"),
            "0" to PartRead.Unrecognized("number"),
            "{}" to PartRead.Unrecognized("object"),
            "{\"enabled\":true}" to PartRead.Unrecognized("object"),
            "[]" to PartRead.Unrecognized("array")
        )
        for ((value, part) in expected) {
            val read = ProtocolPartsRead.of(json("""{"purposes":$value}""")) as ProtocolPartsRead.Read
            assertEquals("purposes=$value", part, read.parts[ProtocolPart.PURPOSES])
            assertEquals("purposes=$value offers the chips only for true", value == "true", read.purposesOffered())
        }
    }

    @Test
    fun `each of the six parts reads on its own key and no part reads another's value`() {
        for (on in ProtocolPart.values()) {
            val obj = ProtocolPart.values().joinToString(",", "{", "}") { "\"${it.wire}\":${it == on}" }
            val read = ProtocolPartsRead.of(json(obj)) as ProtocolPartsRead.Read
            for (part in ProtocolPart.values()) {
                assertEquals("$obj / ${part.wire}", if (part == on) PartRead.Enabled else PartRead.Disabled, read.parts[part])
            }
            // the chips exist for the `purposes` part alone: another part on does not offer them
            assertEquals(obj, on == ProtocolPart.PURPOSES, read.purposesOffered())
            // and the maintenance chips need the second part too (Q31): no single part, on its own, offers them
            assertFalse(obj, read.maintenanceOffered())
        }
        // the two together do, and nothing else does
        val both = ProtocolPartsRead.of(json("""{"purposes":true,"batteryBeforeAfter":true}""")) as ProtocolPartsRead.Read
        assertTrue(both.purposesOffered())
        assertTrue(both.maintenanceOffered())
    }

    @Test
    fun `a key that is none of the six is declared, sorted - and the old batteryPairs lands there`() {
        val read = ProtocolPartsRead.of(json("""{"purposes":true,"zeta":true,"batteryPairs":true,"alpha":1}""")) as ProtocolPartsRead.Read
        assertEquals(listOf("alpha", "batteryPairs", "zeta"), read.unknownKeys)
        assertEquals("the old name is not read as the new part", PartRead.NotListed, read.parts[ProtocolPart.BATTERY_BEFORE_AFTER])
        assertTrue(read.purposesOffered())
        assertFalse("the old name does not offer the maintenance chips either", read.maintenanceOffered())
    }

    @Test
    fun `the key's case matters - Purposes is an unknown key, not the part`() {
        val read = ProtocolPartsRead.of(json("""{"Purposes":true}""")) as ProtocolPartsRead.Read
        assertEquals(listOf("Purposes"), read.unknownKeys)
        assertEquals(PartRead.NotListed, read.parts[ProtocolPart.PURPOSES])
        assertFalse(read.purposesOffered())
    }

    // ---- through the DTO, as Gson builds it ----

    @Test
    fun `the DTO keeps the answer's protocolParts as it came - absent stays null, a JSON null is the null element, nothing is defaulted`() {
        val gson = AnalysisRequestSerializer.gson
        val missing = gson.fromJson("""{"sessionId":"s","code":"NC-1"}""", ScanSession::class.java)
        assertNull(missing.protocolParts)
        assertEquals(ProtocolPartsRead.Absent, missing.protocolPartsRead())
        val jsonNull = gson.fromJson("""{"sessionId":"s","code":"NC-1","protocolParts":null}""", ScanSession::class.java)
        assertEquals(ProtocolPartsRead.Absent, jsonNull.protocolPartsRead())
        val on = gson.fromJson("""{"sessionId":"s","code":"NC-1","protocolParts":{"purposes":true}}""", ScanSession::class.java)
        assertTrue(on.protocolPartsRead().purposesOffered())
        val empty = gson.fromJson("{}", ScanSession::class.java)
        assertNull(empty.protocolParts)
    }
}
