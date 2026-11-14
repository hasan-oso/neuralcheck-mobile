package net.neuralcheck.domain.form

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which slot the technician's screen opens - A-P6-10 (the double evaluation: PLAN-06:1300-1309, row ف٩; contract A7 (2): the phone learns the server's draw from `getTechnicianFormConfig` `slots[1].drawn`).
 * Rules 22, 30. THE ANSWERS ARE WRITTEN FROM THE CONTRACT, NOT PRODUCED BY THE SERVER (the server's half, `fd730ac`, is not deployed): the shape is the one `TechnicianFormConfigReadTest` reads, with `drawn` on the
 * second slot. Rule 30: every state of the first slot x every state of the second x every value of `drawn` - not the one combination the screen meets first.
 */
class TechnicianFormOpeningTest {

    private val twoTechnicians = """[{"technicianId":"t1","displayName":"n1"},{"technicianId":"t2","displayName":"n2"}]"""

    private fun slot(n: Int, state: String?, drawn: String? = null) = buildString {
        append("""{"slot":$n""")
        if (state != null) append(""","formState":"$state"""")
        if (drawn != null) append(""","drawn":$drawn""")
        append("}")
    }

    private fun read(first: String?, second: String?, drawn: String? = null, technicians: String? = twoTechnicians, slots: String? = null): TechnicianFormConfigRead {
        val slotsJson = slots ?: "[${slot(1, first)},${slot(2, second, drawn)}]"
        val techs = if (technicians == null) "" else ""","technicians":$technicians"""
        return TechnicianFormConfigRead.of(JsonParser.parseString("""{"sessionId":"s","formContractVersion":2,"enabled":true,"slots":$slotsJson,"symptomOrder":[],"settingsVersion":"x"$techs}"""))
    }

    private val sentFirst = FormOffer.SlotClosed(SlotClosedReason.SUBMITTED)

    @Test
    fun `while the first form is not sent the screen opens it - whatever the second slot says, even a draw it cannot read`() {
        for (drawn in listOf(null, "true", "false", "\"yes\"", "1", "null")) {
            for (second in listOf("not_submitted", "submitted", "weird", null)) {
                val opening = decideOpening(read("not_submitted", second, drawn))
                assertEquals("second=$second drawn=$drawn", 1, opening.slot)
                assertTrue("second=$second drawn=$drawn: ${opening.offer}", opening.offer is FormOffer.Offer)
            }
        }
    }

    @Test
    fun `once the first form is sent the screen goes to the second exactly when the draw picked this phone and the second is not sent`() {
        // the second form waiting: drawn true, not sent
        val waiting = decideOpening(read("submitted", "not_submitted", "true"))
        assertEquals(2, waiting.slot)
        assertTrue(waiting.offer is FormOffer.Offer)
        assertEquals(listOf("t1", "t2"), (waiting.offer as FormOffer.Offer).technicians.map { it.id })
        // not drawn: the first form's «sent» stands, as before
        assertEquals(FormOpening(1, sentFirst), decideOpening(read("submitted", "not_submitted", "false")))
        // the second already sent, drawn or not (the sent state is read first): both done
        assertEquals(FormOpening(1, sentFirst), decideOpening(read("submitted", "submitted", "true")))
        assertEquals(FormOpening(1, sentFirst), decideOpening(read("submitted", "submitted", "false")))
    }

    @Test
    fun `a draw the app cannot read is a named state of the second slot - never not drawn and never drawn - every shape, each its own reason`() {
        val cases = mapOf(
            null to "drawn_absent",
            "null" to "drawn_absent",
            "\"yes\"" to "drawn_not_a_boolean_string",
            "1" to "drawn_not_a_boolean_number",
            "[]" to "drawn_not_a_boolean_array",
            "{}" to "drawn_not_a_boolean_object"
        )
        for ((drawn, why) in cases) {
            val opening = decideOpening(read("submitted", "not_submitted", drawn))
            assertEquals("drawn=$drawn", FormOpening(2, FormOffer.Unreadable(why)), opening)
            assertEquals("drawn=$drawn", opening, decideSecondSlot(read("submitted", "not_submitted", drawn)))
        }
    }

    @Test
    fun `a second slot that cannot be read at all is a named state of the second slot`() {
        assertEquals(FormOpening(2, FormOffer.Unreadable("slot_state_absent")), decideOpening(read("submitted", null, "true")))
        assertEquals(FormOpening(2, FormOffer.Unreadable("slot_state_unrecognized")), decideOpening(read("submitted", "pending", "true")))
        assertEquals(FormOpening(2, FormOffer.Unreadable("slot_2_absent")), decideOpening(read(null, null, slots = "[${slot(1, "submitted")}]")))
    }

    @Test
    fun `the second form is not offered with nobody to choose - NoTechnicians, or the list missing - and the first form's sent is not that`() {
        assertEquals(FormOpening(2, FormOffer.NoTechnicians), decideOpening(read("submitted", "not_submitted", "true", technicians = "[]")))
        assertEquals(FormOpening(2, FormOffer.Unreadable("technicians_absent")), decideOpening(read("submitted", "not_submitted", "true", technicians = null)))
        // not drawn: nobody is needed
        assertEquals(FormOpening(1, sentFirst), decideOpening(read("submitted", "not_submitted", "false", technicians = "[]")))
    }

    @Test
    fun `the second slot is decided by the second slot's own answer - the same as asking for it by number - and a shop with the form off has none`() {
        for (drawn in listOf("true", "false")) {
            val read = read("submitted", "not_submitted", drawn)
            val second = decideSecondSlot(read)
            val direct = decideOffer(read, TechnicianFormConfigWire.SLOT_SECOND)
            if (drawn == "true") assertEquals(FormOpening(2, direct), second) else assertNull(second)
        }
        val off = TechnicianFormConfigRead.of(JsonParser.parseString("""{"sessionId":"s","formContractVersion":2,"enabled":false,"formState":"not_enabled"}"""))
        assertEquals(FormOpening(1, FormOffer.NotEnabled), decideOpening(off))
        assertNull(decideSecondSlot(off))
        // an answer that is not an answer: unreadable at the first slot (nothing says the first form was sent, so no second form is looked for), and unreadable for the second slot if it is asked
        assertEquals(FormOpening(1, FormOffer.Unreadable("absent")), decideOpening(TechnicianFormConfigRead.of(null)))
        assertEquals(FormOpening(2, FormOffer.Unreadable("absent")), decideSecondSlot(TechnicianFormConfigRead.of(null)))
    }
}
