package net.neuralcheck.domain.consent

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The consent call's wire - A-P6-12 (rules 22, 24, 25, 30). The answers are SPELLED from the contract, not produced by the server (its half, `5ab5eaf` inside `5361116`, is not deployed): A8 (1) of
 * `evidence/run-2026-10-18/protocol-contract-A8-A11-A12-neuralcheck-backend-5361116.md` (a copy, rule 29) - «الردّ `{sessionId, state: "accepted", textVersion, locale, alreadyRecorded: boolean}` - القيم المخزَّنة».
 * What is asserted: the request is exactly the three keys the server requires, none with a default; an answer is a recorded consent ONLY when its state is «accepted», it names this session, and its stored
 * version and language are readable - every other shape is its own named reason and none is a recorded consent (PLAN-06:1402: «فالموافقة غير المسجَّلة لا تُقرأ موافقة»); the eight refusals and the two languages
 * are the contract's, spelled once.
 */
class ScanConsentWireTest {

    private fun answer(json: String, session: String = "sess-1") = ConsentAnswerRead.of(JsonParser.parseString(json), session)

    private fun accepted(extra: String = "") =
        """{"sessionId":"sess-1","state":"accepted","textVersion":1,"locale":"ar","alreadyRecorded":false$extra}"""

    private fun unreadable(json: String): String = (answer(json) as ConsentAnswerRead.Unreadable).why

    // ---- the request ----

    @Test
    fun `the request is exactly the three keys of the contract - the version is a number, the language the wire value - and nothing else`() {
        for (locale in ConsentLocale.values()) {
            val request = ConsentWire.request("sess-1", ConsentTextVersion.CURRENT, locale)
            assertEquals(setOf("sessionId", "textVersion", "locale"), request.keys)
            assertEquals("sess-1", request["sessionId"])
            assertEquals("a number, not a text: the server refuses anything else", 2, request["textVersion"])
            assertTrue(request["textVersion"] is Int)
            assertEquals(locale.wire, request["locale"])
        }
        assertEquals("recordScanConsent", ConsentWire.CALLABLE)
        // Rule 23: REPLACES «E11, approved 2026-10-15: the version of the text is 1» - the text was reworded (version 2, 2026-10-23) and the server's own current version is 2
        assertEquals("the version of the text is 2", 2, ConsentTextVersion.CURRENT)
    }

    // ---- the answer ----

    @Test
    fun `the contract's answer is a recorded consent - with the STORED values and the server's flag`() {
        assertEquals(ConsentAnswerRead.Accepted(1, ConsentLocale.AR, false, emptyList()), answer(accepted()))
        assertEquals(
            "a second call answers the first's values, whatever it sent: the stored ones are what is read",
            ConsentAnswerRead.Accepted(1, ConsentLocale.TR, true, emptyList()),
            answer("""{"sessionId":"sess-1","state":"accepted","textVersion":1,"locale":"tr","alreadyRecorded":true}""")
        )
        // the number as the SDK decodes it can be an Int or a Double: both are the version
        assertEquals(ConsentAnswerRead.Accepted(1, ConsentLocale.AR, false, emptyList()), answer(accepted().replace("\"textVersion\":1", "\"textVersion\":1.0")))
    }

    @Test
    fun `a flag the answer did not carry is kept absent - it is not read as false, and it does not make the consent unreadable`() {
        val withoutFlag = """{"sessionId":"sess-1","state":"accepted","textVersion":1,"locale":"ar"}"""
        assertEquals(ConsentAnswerRead.Accepted(1, ConsentLocale.AR, null, emptyList()), answer(withoutFlag))
        assertNull((answer(accepted().replace("\"alreadyRecorded\":false", "\"alreadyRecorded\":null")) as ConsentAnswerRead.Accepted).alreadyRecorded)
    }

    @Test
    fun `a key the contract does not name is declared, sorted - never ignored silently`() {
        val read = answer(accepted(""","zeta":1,"alpha":"x"""")) as ConsentAnswerRead.Accepted
        assertEquals(listOf("alpha", "zeta"), read.unknownKeys)
    }

    @Test
    fun `every shape that is not a recorded consent is its own reason - and never a recorded consent`() {
        val cases = listOf(
            """null""" to "absent",
            """[]""" to "not_an_object_array",
            """"accepted"""" to "not_an_object_string",
            """7""" to "not_an_object_number",
            """{}""" to "session_id_missing",
            """{"sessionId":null,"state":"accepted","textVersion":1,"locale":"ar"}""" to "session_id_missing",
            """{"sessionId":5,"state":"accepted","textVersion":1,"locale":"ar"}""" to "session_id_missing",
            """{"sessionId":"sess-2","state":"accepted","textVersion":1,"locale":"ar"}""" to "session_id_differs",
            """{"sessionId":"sess-1","textVersion":1,"locale":"ar"}""" to "state_absent",
            """{"sessionId":"sess-1","state":null,"textVersion":1,"locale":"ar"}""" to "state_absent",
            """{"sessionId":"sess-1","state":true,"textVersion":1,"locale":"ar"}""" to "state_not_a_string_boolean",
            """{"sessionId":"sess-1","state":"declined","textVersion":1,"locale":"ar"}""" to "state_not_accepted",
            """{"sessionId":"sess-1","state":"not_collected","textVersion":1,"locale":"ar"}""" to "state_not_accepted",
            """{"sessionId":"sess-1","state":"Accepted","textVersion":1,"locale":"ar"}""" to "state_not_accepted",
            """{"sessionId":"sess-1","state":"accepted","locale":"ar"}""" to "text_version_absent",
            """{"sessionId":"sess-1","state":"accepted","textVersion":"1","locale":"ar"}""" to "text_version_not_a_number_string",
            """{"sessionId":"sess-1","state":"accepted","textVersion":1.5,"locale":"ar"}""" to "text_version_not_a_version",
            """{"sessionId":"sess-1","state":"accepted","textVersion":0,"locale":"ar"}""" to "text_version_not_a_version",
            """{"sessionId":"sess-1","state":"accepted","textVersion":-1,"locale":"ar"}""" to "text_version_not_a_version",
            """{"sessionId":"sess-1","state":"accepted","textVersion":1}""" to "locale_absent",
            """{"sessionId":"sess-1","state":"accepted","textVersion":1,"locale":3}""" to "locale_not_a_string_number",
            """{"sessionId":"sess-1","state":"accepted","textVersion":1,"locale":"en"}""" to "locale_unrecognized",
            """{"sessionId":"sess-1","state":"accepted","textVersion":1,"locale":"AR"}""" to "locale_unrecognized",
            """{"sessionId":"sess-1","state":"accepted","textVersion":1,"locale":"ar","alreadyRecorded":"yes"}""" to "already_recorded_not_a_boolean_string",
            """{"sessionId":"sess-1","state":"accepted","textVersion":1,"locale":"ar","alreadyRecorded":1}""" to "already_recorded_not_a_boolean_number"
        )
        for ((json, why) in cases) assertEquals(json, why, unreadable(json))
        assertEquals("no answer at all", "absent", (ConsentAnswerRead.of(null, "sess-1") as ConsentAnswerRead.Unreadable).why)
    }

    // ---- the refusals and the languages ----

    /** Rule 23: REPLACES `the eight consent refusals ...` - ق40 added the ninth, `CONSENT_PART_NOT_ENABLED` (the shop has no consent part). */
    @Test
    fun `the nine consent refusals are the contract's, each read as itself - and the four session ones are not spelled here`() {
        assertEquals(
            listOf(
                "CONSENT_NOT_AUTHENTICATED", "CONSENT_SESSION_ID_REQUIRED", "CONSENT_TEXT_VERSION_REQUIRED", "CONSENT_TEXT_VERSION_INVALID",
                "CONSENT_LOCALE_REQUIRED", "CONSENT_LOCALE_INVALID", "CONSENT_SESSION_NOT_SCANNING", "CONSENT_STORED_UNREADABLE", "CONSENT_PART_NOT_ENABLED"
            ),
            ConsentRefusal.values().map { it.wire }
        )
        for (r in ConsentRefusal.values()) assertEquals(r, ConsentRefusal.ofWire(r.wire))
        for (shared in listOf("SESSION_NOT_PARTICIPANT", "SESSION_EXPIRED", "SESSION_EXPIRES_AT_INVALID", "SESSION_STATUS_UNKNOWN", "CONSENT_NEW_ONE", "consent_session_not_scanning")) {
            assertNull("$shared is none of the nine", ConsentRefusal.ofWire(shared))
        }
    }

    @Test
    fun `the language is read as the contract's two values - exactly, with no default for any other`() {
        assertEquals(ConsentLocaleRead.Known(ConsentLocale.AR), ConsentLocaleRead.of("ar"))
        assertEquals(ConsentLocaleRead.Known(ConsentLocale.TR), ConsentLocaleRead.of("tr"))
        assertEquals(ConsentLocaleRead.Unsupported("en"), ConsentLocaleRead.of("en"))
        assertEquals("case matters, as on the server", ConsentLocaleRead.Unsupported("AR"), ConsentLocaleRead.of("AR"))
        assertEquals(ConsentLocaleRead.Absent, ConsentLocaleRead.of(null))
        assertEquals(ConsentLocaleRead.Absent, ConsentLocaleRead.of(" "))
        assertEquals(listOf("ar", "tr"), ConsentLocale.values().map { it.wire })
    }
}
