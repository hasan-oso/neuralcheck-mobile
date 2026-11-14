package net.neuralcheck.domain.repeat

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The repeat scan's wire reader and decisions - A-P6-07 (rules 22, 25, 30). THE SAMPLES ARE WRITTEN FROM THE CONTRACT, NOT PRODUCED BY THE SERVER: the server's half (`cccd027`, inside `fd730ac`) is not
 * deployed and was not run from here. Each answer below is spelled key by key as A4 (5) and (4) of `evidence/run-2026-10-18/protocol-contract-A0-A10-neuralcheck-backend-fd730ac.md` spell them (a copy,
 * rule 29); a difference found on the server later is a difference to report. What is asserted is what the app makes of each, for EVERY value of the enumerations (the eleven states, the five temperature
 * states, the twelve refusals) - not for one value seen in one sample.
 */
class RepeatScanReadTest {

    private val session = "sess-1"
    private val scan = "scan-1"

    private fun json(text: String) = JsonParser.parseString(text)

    /** The seven keys of A4 (5), each given as JSON text (`null` for a JSON null). A key given as `<missing>` is left out of the answer. */
    private fun offer(
        state: String? = "\"SELECTED\"",
        reason: String = "null",
        temp: String = """{"state":"PRESENT","valueC":32.8}""",
        child: String = "null",
        childStatus: String = "null",
        sessionId: String = "\"$session\"",
        scanId: String = "\"$scan\""
    ): String = buildString {
        append("{")
        val parts = mutableListOf<String>()
        parts += "\"sessionId\":$sessionId"
        parts += "\"scanId\":$scanId"
        if (state != "<missing>") parts += "\"state\":${state ?: "null"}"
        if (reason != "<missing>") parts += "\"reason\":$reason"
        if (temp != "<missing>") parts += "\"firstScanStartTemp\":$temp"
        if (child != "<missing>") parts += "\"childSessionId\":$child"
        if (childStatus != "<missing>") parts += "\"childStatus\":$childStatus"
        append(parts.joinToString(","))
        append("}")
    }

    private fun decided(text: String) = decide(RepeatOfferRead.of(json(text), session, scan))

    // ---- the eleven states, each one: rule 30 ----

    @Test
    fun `every one of the eleven states is classified, and the four the server lets start are the startable ones`() {
        assertEquals(11, RepeatState.values().size)
        val expected: Map<RepeatState, Class<out RepeatDecision>> = mapOf(
            RepeatState.NOT_ELIGIBLE to RepeatDecision.NotApplicable::class.java,
            RepeatState.NOT_SELECTED to RepeatDecision.NotApplicable::class.java,
            RepeatState.SELECTED to RepeatDecision.Startable::class.java,
            RepeatState.PROMPT_DELIVERED to RepeatDecision.Startable::class.java,
            RepeatState.DECLINED to RepeatDecision.Startable::class.java,
            RepeatState.WAITING_COOLING to RepeatDecision.Startable::class.java,
            RepeatState.STARTED to RepeatDecision.NotStartable::class.java,
            RepeatState.COMPLETED_COLD to RepeatDecision.NotStartable::class.java,
            RepeatState.COMPLETED_HOT to RepeatDecision.NotStartable::class.java,
            RepeatState.COMPLETED_UNCLASSIFIED to RepeatDecision.NotStartable::class.java,
            RepeatState.EXPIRED to RepeatDecision.NotStartable::class.java
        )
        assertEquals("the table names every state", RepeatState.values().toSet(), expected.keys)
        for ((state, kind) in expected) {
            val text = when (state) {
                RepeatState.NOT_ELIGIBLE -> offer("\"${state.wire}\"", reason = "\"PURPOSE_NOT_NORMAL\"", temp = "null")
                RepeatState.NOT_SELECTED -> offer("\"${state.wire}\"", temp = "null")
                else -> offer("\"${state.wire}\"")
            }
            val decision = decided(text)
            assertEquals("$state", kind, decision::class.java)
            assertEquals("startable is the property the decision follows", state.startable, decision is RepeatDecision.Startable)
        }
        assertEquals(setOf(RepeatState.SELECTED, RepeatState.PROMPT_DELIVERED, RepeatState.DECLINED, RepeatState.WAITING_COOLING), RepeatState.values().filter { it.startable }.toSet())
    }

    @Test
    fun `the wire texts are the contract's, spelled once each`() {
        assertEquals(
            listOf(
                "NOT_ELIGIBLE", "NOT_SELECTED", "SELECTED", "PROMPT_DELIVERED", "DECLINED", "WAITING_COOLING", "STARTED", "COMPLETED_COLD", "COMPLETED_HOT", "COMPLETED_UNCLASSIFIED", "EXPIRED"
            ),
            RepeatState.values().map { it.wire }
        )
        assertEquals(listOf("PRESENT", "ABSENT", "SENTINEL_ZERO", "NOT_A_NUMBER", "STORED_MALFORMED"), FirstTempState.values().map { it.wire })
        assertEquals("getRepeatScanOffer", RepeatScanWire.OFFER_CALLABLE)
        assertEquals("startRepeatScan", RepeatScanWire.START_CALLABLE)
    }

    @Test
    fun `a NOT_ELIGIBLE answer carries its reason as a text, and the reason is kept for the log`() {
        val d = decided(offer("\"NOT_ELIGIBLE\"", reason = "\"PART_NOT_ENABLED\"", temp = "null")) as RepeatDecision.NotApplicable
        assertEquals(RepeatState.NOT_ELIGIBLE, d.state)
        assertEquals("PART_NOT_ENABLED", d.reason)
        assertEquals("not_applicable_not_eligible", d.token)
        // no reason, a blank one, a number: each its own unreadable answer - never "eligible" and never "no reason needed"
        for (reason in listOf("null", "<missing>", "\"  \"", "7")) {
            assertEquals("reason $reason", RepeatDecision.Unreadable("not_eligible_without_reason"), decided(offer("\"NOT_ELIGIBLE\"", reason = reason, temp = "null")))
        }
    }

    @Test
    fun `the reference must be null for the two states that do not apply, and an object for the four that start`() {
        assertEquals(RepeatDecision.Unreadable("not_applicable_temp_not_null"), decided(offer("\"NOT_SELECTED\"")))
        assertEquals(RepeatDecision.Unreadable("not_applicable_temp_not_null"), decided(offer("\"NOT_ELIGIBLE\"", reason = "\"RATE_ABSENT\"")))
        for (state in listOf("SELECTED", "PROMPT_DELIVERED", "DECLINED", "WAITING_COOLING")) {
            assertEquals("$state with null", RepeatDecision.Unreadable("temp_null_on_startable"), decided(offer("\"$state\"", temp = "null")))
            assertEquals("$state with the key missing", RepeatDecision.Unreadable("temp_absent"), decided(offer("\"$state\"", temp = "<missing>")))
        }
    }

    // ---- the five temperature states, each one ----

    @Test
    fun `every one of the five temperature states reads as itself - only PRESENT with a number is a reference`() {
        for (state in FirstTempState.values()) {
            val temp = if (state == FirstTempState.PRESENT) """{"state":"PRESENT","valueC":32.8}""" else """{"state":"${state.wire}","valueC":null}"""
            val d = decided(offer(temp = temp)) as RepeatDecision.Startable
            when (state) {
                FirstTempState.PRESENT -> assertEquals(StartableReference.Usable(32.8), d.reference)
                else -> assertEquals("$state", StartableReference.NotUsable(state), d.reference)
            }
        }
        // the sentinel is never a temperature, even if the server sent its number
        assertEquals(StartableReference.NotUsable(FirstTempState.SENTINEL_ZERO), (decided(offer(temp = """{"state":"SENTINEL_ZERO","valueC":0.0}""")) as RepeatDecision.Startable).reference)
    }

    @Test
    fun `a malformed reference is its own unreadable answer - not an object, a state that is none of the five, PRESENT without a usable number`() {
        val cases = mapOf(
            "\"hot\"" to "temp_not_an_object_string",
            "[]" to "temp_not_an_object_array",
            """{"valueC":32.8}""" to "temp_state_absent",
            """{"state":null,"valueC":32.8}""" to "temp_state_absent",
            """{"state":7,"valueC":32.8}""" to "temp_state_not_a_string_number",
            """{"state":"WARM","valueC":32.8}""" to "temp_state_unrecognized",
            """{"state":"PRESENT"}""" to "temp_present_without_value",
            """{"state":"PRESENT","valueC":null}""" to "temp_present_without_value",
            """{"state":"PRESENT","valueC":"32.8"}""" to "temp_value_not_a_number_string",
            """{"state":"PRESENT","valueC":true}""" to "temp_value_not_a_number_boolean"
        )
        for ((temp, why) in cases) assertEquals(temp, RepeatDecision.Unreadable(why), decided(offer(temp = temp)))
    }

    // ---- the shape of the whole answer ----

    @Test
    fun `an answer that is not an object, or names no state, or a state that is none of the eleven, is unreadable - each by its own token`() {
        assertEquals(RepeatDecision.Unreadable("absent"), decide(RepeatOfferRead.of(null, session, scan)))
        assertEquals(RepeatDecision.Unreadable("absent"), decided("null"))
        assertEquals(RepeatDecision.Unreadable("not_an_object_array"), decided("[]"))
        assertEquals(RepeatDecision.Unreadable("not_an_object_string"), decided("\"STARTED\""))
        assertEquals(RepeatDecision.Unreadable("state_absent"), decided(offer(state = "<missing>")))
        assertEquals(RepeatDecision.Unreadable("state_absent"), decided(offer(state = null)))
        assertEquals(RepeatDecision.Unreadable("state_not_a_string_number"), decided(offer(state = "3")))
        assertEquals(RepeatDecision.Unreadable("state_unrecognized"), decided(offer(state = "\"COMPLETED_WARM\"")))
        assertEquals("a state in another case is not the state", RepeatDecision.Unreadable("state_unrecognized"), decided(offer(state = "\"selected\"")))
    }

    @Test
    fun `the answer must be this request's - another session, another scan, or no echo, is unreadable`() {
        assertEquals(RepeatDecision.Unreadable("echo_sessionId_differs"), decided(offer(sessionId = "\"sess-2\"")))
        assertEquals(RepeatDecision.Unreadable("echo_scanId_differs"), decided(offer(scanId = "\"scan-2\"")))
        assertEquals(RepeatDecision.Unreadable("echo_sessionId_missing"), decided(offer(sessionId = "null")))
        assertEquals(RepeatDecision.Unreadable("echo_scanId_missing"), decided(offer(scanId = "7")))
    }

    @Test
    fun `a key that is none of the seven is declared and changes nothing`() {
        val read = RepeatOfferRead.of(json(offer().dropLast(1) + ""","stateHistory":[],"zeta":1}"""), session, scan) as RepeatOfferRead.Read
        assertEquals(listOf("stateHistory", "zeta"), read.unknownKeys)
        assertTrue(decide(read) is RepeatDecision.Startable)
    }

    @Test
    fun `the child and its status are kept as named states - a child, none, a blank one, a number`() {
        val read = RepeatOfferRead.of(json(offer("\"STARTED\"", child = "\"child-1\"", childStatus = "\"cancelled\"")), session, scan) as RepeatOfferRead.Read
        assertEquals(TextRead.Text("child-1"), read.childSessionId)
        assertEquals(TextRead.Text("cancelled"), read.childStatus)
        val none = RepeatOfferRead.of(json(offer()), session, scan) as RepeatOfferRead.Read
        assertEquals(TextRead.Null, none.childSessionId)
        val odd = RepeatOfferRead.of(json(offer(child = "\" \"", childStatus = "5")), session, scan) as RepeatOfferRead.Read
        assertEquals(TextRead.Blank(" "), odd.childSessionId)
        assertEquals(TextRead.NotAString("number"), odd.childStatus)
        val gone = RepeatOfferRead.of(json(offer(child = "<missing>", childStatus = "<missing>")), session, scan) as RepeatOfferRead.Read
        assertEquals(TextRead.Absent, gone.childSessionId)
        assertEquals(TextRead.Absent, gone.childStatus)
    }

    // ---- the start's answer ----

    private fun started(text: String) = RepeatStartRead.of(json(text), session)

    @Test
    fun `the start's answer is a child session - or a named reason it is not one`() {
        assertEquals(RepeatStartRead.Started("child-1", 60), started("""{"sessionId":"child-1","parentSessionId":"sess-1","state":"STARTED","expiresInMinutes":60}"""))
        assertEquals("no lifetime named: none is made up", RepeatStartRead.Started("child-1", null), started("""{"sessionId":"child-1","parentSessionId":"sess-1","state":"STARTED"}"""))
        assertEquals("a fraction or zero is no lifetime", RepeatStartRead.Started("child-1", null), started("""{"sessionId":"child-1","parentSessionId":"sess-1","state":"STARTED","expiresInMinutes":0.5}"""))
        val cases = mapOf(
            "null" to "absent",
            "[]" to "not_an_object_array",
            """{"sessionId":"c","parentSessionId":"sess-1"}""" to "state_absent",
            """{"sessionId":"c","parentSessionId":"sess-1","state":3}""" to "state_not_a_string_number",
            """{"sessionId":"c","parentSessionId":"sess-1","state":"SELECTED"}""" to "state_not_started",
            """{"sessionId":"c","state":"STARTED"}""" to "parent_session_id_not_a_text",
            """{"sessionId":"c","parentSessionId":"sess-9","state":"STARTED"}""" to "parent_session_id_differs",
            """{"parentSessionId":"sess-1","state":"STARTED"}""" to "child_session_id_not_a_text",
            """{"sessionId":" ","parentSessionId":"sess-1","state":"STARTED"}""" to "child_session_id_not_a_text",
            """{"sessionId":"sess-1","parentSessionId":"sess-1","state":"STARTED"}""" to "child_session_id_is_the_parent"
        )
        for ((text, why) in cases) assertEquals(text, RepeatStartRead.Unreadable(why), started(text))
    }

    // ---- the requests ----

    @Test
    fun `the requests are the contract's - the pair, and the flag only when it is true`() {
        assertEquals(mapOf("sessionId" to "sess-1", "scanId" to "scan-1"), RepeatScanWire.offerRequest("sess-1", "scan-1"))
        assertEquals("a cooled start declares nothing: absent is stored as null, not as false", mapOf("sessionId" to "sess-1", "scanId" to "scan-1"), RepeatScanWire.startRequest("sess-1", "scan-1", null))
        assertEquals(mapOf("sessionId" to "sess-1", "scanId" to "scan-1", "startedBeforeCooling" to true), RepeatScanWire.startRequest("sess-1", "scan-1", true))
    }

    // ---- the refusals ----

    @Test
    fun `the twelve refusal codes are the contract's, each reads as itself, and any other text is none of them`() {
        val wires = listOf(
            "REPEAT_NOT_AUTHENTICATED", "REPEAT_SESSION_ID_REQUIRED", "REPEAT_SCAN_ID_REQUIRED", "REPEAT_STARTED_BEFORE_COOLING_INVALID", "REPEAT_PAIR_NOT_FOUND",
            "REPEAT_SESSION_NOT_COMPLETED", "REPEAT_RECORD_ABSENT", "REPEAT_STATE_UNKNOWN", "REPEAT_NOT_SELECTED", "REPEAT_ALREADY_STARTED", "REPEAT_PARENT_OWNER_UNKNOWN", "REPEAT_PARENT_JOINER_UNKNOWN"
        )
        assertEquals(wires, RepeatRefusal.values().map { it.wire })
        for (w in wires) assertEquals(w, w, RepeatRefusal.ofWire(w)?.wire)
        for (other in listOf("SESSION_STATUS_UNKNOWN", "repeat_already_started", "REPEAT_NEW", "")) assertNull(other, RepeatRefusal.ofWire(other))
    }
}
