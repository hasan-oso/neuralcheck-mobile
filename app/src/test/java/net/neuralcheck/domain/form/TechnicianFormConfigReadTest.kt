package net.neuralcheck.domain.form

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How the form's configuration reads - A-P6-08. The answers are the server's two returns as its handler spells them (`technicianForm.js:312` for a shop that has the form off, `:325-336` for one that has it
 * on; the copy is `evidence/run-2026-10-18/technician-form-server-wire-neuralcheck-backend-05275ba.md`, neuralcheck-backend@05275ba - rule 29, a copy that drifts). They are written out here, key by key,
 * because the handler cannot run on this JVM: this is the producer's output as its code defines it, not the app's own idea of it. Rules 22 and 25: the reader names every shape it can meet and reads none of
 * them as another; rule 30: every kind of JSON value in each piece, and what the reader MAKES of it - above all [decideOffer], [effectiveSymptomOrder] and [choosableTechnicians], the things the form is built from.
 *
 * NOT known from the copy: the value of `settingsVersion` (the server sends its `SETTINGS_ABSENT` constant, defined in `protocolVocabulary.js`, which is not in the copy), so no test depends on it.
 */
class TechnicianFormConfigReadTest {

    private fun read(text: String) = TechnicianFormConfigRead.of(JsonParser.parseString(text))

    private fun readObject(text: String) = read(text) as TechnicianFormConfigRead.Read

    /** technicianForm.js:312 - the shop has the form off. */
    private val notEnabledAnswer = """{"sessionId":"sess-1","formContractVersion":2,"enabled":false,"formState":"not_enabled"}"""

    private val twoTechnicians = """[{"technicianId":"t1","displayName":"A"},{"technicianId":"t2","displayName":"B"}]"""
    private val allTwelve = """["battery_dies_before_day_end","battery_jumps_or_shuts_down","battery_swollen","hot_while_charging","charging_slow","slow_or_freezes_daily","restarts_under_load","heats_fast_in_use","system_heat_warning","game_stutter_after_minutes","game_screen_lines_or_flicker","games_freeze_or_close"]"""
    private val bothSlots = """[{"slot":1,"formState":"not_submitted"},{"slot":2,"formState":"not_submitted","drawn":false}]"""

    /** technicianForm.js:325-336 - the shop has the form on; every part replaceable by raw JSON, a part given as null is left out of the object. */
    private fun enabledAnswer(
        slots: String? = bothSlots, technicians: String? = twoTechnicians, symptomOrder: String? = allTwelve, settingsVersion: String? = "\"x\"", version: String? = "2", enabled: String? = "true"
    ): String = buildString {
        append("""{"sessionId":"sess-1"""")
        if (version != null) append(""","formContractVersion":$version""")
        if (enabled != null) append(""","enabled":$enabled""")
        if (slots != null) append(""","slots":$slots""")
        if (technicians != null) append(""","technicians":$technicians""")
        if (symptomOrder != null) append(""","symptomOrder":$symptomOrder""")
        if (settingsVersion != null) append(""","settingsVersion":$settingsVersion""")
        append("}")
    }

    private fun offer(json: String, slot: Int = 1) = decideOffer(read(json), slot)

    // ---- the whole answer ----

    @Test
    fun `no answer, a JSON null and a value that is not an object are three named states`() {
        assertEquals(TechnicianFormConfigRead.Absent, TechnicianFormConfigRead.of(null))
        assertEquals(TechnicianFormConfigRead.Absent, read("null"))
        for ((text, kind) in listOf("[]" to "array", "\"x\"" to "string", "5" to "number", "true" to "boolean")) {
            assertEquals(text, TechnicianFormConfigRead.NotAnObject(kind), read(text))
        }
    }

    @Test
    fun `the server's two answers read piece by piece - not enabled carries a state and nothing else, enabled carries the form`() {
        val off = readObject(notEnabledAnswer)
        assertEquals(EnabledRead.Value(false), off.enabled)
        assertEquals(FormStateRead.NotEnabled, off.formState)
        assertEquals(ContractVersionRead.Value(2), off.contractVersion)
        assertEquals(SlotsRead.Absent, off.slots)
        assertEquals(TechniciansRead.Absent, off.technicians)
        assertEquals(SymptomOrderRead.Absent, off.symptomOrder)
        assertEquals(SettingsVersionRead.Absent, off.settingsVersion)
        assertEquals(emptyList<String>(), off.unknownKeys)

        val on = readObject(enabledAnswer())
        assertEquals(EnabledRead.Value(true), on.enabled)
        assertEquals("an enabled answer carries no top-level formState (its slots do)", FormStateRead.Absent, on.formState)
        assertEquals(ContractVersionRead.Value(2), on.contractVersion)
        assertEquals(
            SlotsRead.Listed(listOf(SlotRead(1, SlotStateRead.Known(SlotFormState.NOT_SUBMITTED), DrawnRead.Absent), SlotRead(2, SlotStateRead.Known(SlotFormState.NOT_SUBMITTED), DrawnRead.Value(false))), 0, 0),
            on.slots
        )
        assertEquals(TechniciansRead.Listed(listOf(FormTechnician("t1", "A"), FormTechnician("t2", "B")), 0, 0), on.technicians)
        assertEquals(12, (on.symptomOrder as SymptomOrderRead.Listed).keys.size)
        assertEquals(SettingsVersionRead.Present("string", "x"), on.settingsVersion)
        assertEquals(emptyList<String>(), on.unknownKeys)
    }

    @Test
    fun `a key that is none of the eight the server names is listed, sorted - never ignored silently`() {
        val r = readObject("""{"zeta":1,"alpha":true,"enabled":true,"sessionId":"s"}""")
        assertEquals(listOf("alpha", "zeta"), r.unknownKeys)
        assertEquals(SlotsRead.Absent, r.slots)
        assertEquals(TechniciansRead.Absent, r.technicians)
    }

    // ---- each piece, every kind of value ----

    @Test
    fun `enabled reads for every kind of value - a missing or other-shaped one is not either answer`() {
        fun enabled(json: String?) = readObject(if (json == null) "{}" else """{"enabled":$json}""").enabled
        assertEquals(EnabledRead.Absent, enabled(null))
        assertEquals(EnabledRead.Absent, enabled("null"))
        assertEquals(EnabledRead.Value(true), enabled("true"))
        assertEquals(EnabledRead.Value(false), enabled("false"))
        for ((json, kind) in listOf("\"true\"" to "string", "1" to "number", "0" to "number", "{}" to "object", "[]" to "array")) {
            assertEquals(json, EnabledRead.NotABoolean(kind), enabled(json))
        }
    }

    @Test
    fun `the top-level form state, and the contract version, read for every kind of value`() {
        fun state(json: String) = readObject("""{"formState":$json}""").formState
        assertEquals(FormStateRead.Absent, state("null"))
        assertEquals(FormStateRead.NotEnabled, state("\"not_enabled\""))
        assertEquals("the case matters", FormStateRead.Unrecognized("Not_Enabled"), state("\"Not_Enabled\""))
        assertEquals(FormStateRead.Unrecognized("submitted"), state("\"submitted\""))
        for ((json, kind) in listOf("1" to "number", "true" to "boolean", "{}" to "object", "[]" to "array")) assertEquals(json, FormStateRead.NotAString(kind), state(json))

        fun version(json: String?) = readObject(if (json == null) "{}" else """{"formContractVersion":$json}""").contractVersion
        assertEquals(ContractVersionRead.Absent, version(null))
        assertEquals(ContractVersionRead.Absent, version("null"))
        assertEquals(ContractVersionRead.Value(2), version("2"))
        assertEquals("a whole number written with a fraction is still that number", ContractVersionRead.Value(2), version("2.0"))
        assertEquals(ContractVersionRead.Value(3), version("3"))
        assertEquals(ContractVersionRead.NotAnInteger("number"), version("2.5"))
        for ((json, kind) in listOf("\"2\"" to "string", "true" to "boolean", "{}" to "object", "[]" to "array")) assertEquals(json, ContractVersionRead.NotAnInteger(kind), version(json))
    }

    @Test
    fun `the slots read for every kind of value and of entry`() {
        fun slots(json: String) = readObject("""{"slots":$json}""").slots
        assertEquals(SlotsRead.Absent, slots("null"))
        for ((json, kind) in listOf("\"x\"" to "string", "1" to "number", "true" to "boolean", "{}" to "object")) assertEquals(json, SlotsRead.NotAList(kind), slots(json))
        assertEquals(SlotsRead.Listed(emptyList(), 0, 0), slots("[]"))
        val mixed = slots(
            """[
              {"slot":1,"formState":"submitted"},
              {"slot":1,"formState":"not_submitted"},
              {"slot":2,"formState":"nope","drawn":true},
              {"slot":3,"formState":"submitted"},
              {"slot":1.5,"formState":"submitted"},
              {"slot":"2","formState":"submitted"},
              {"formState":"submitted"},
              "x",
              null
            ]"""
        )
        assertEquals(
            "usable: slot 1 or 2 only, the first of a repeated slot wins; the rest counted",
            SlotsRead.Listed(listOf(SlotRead(1, SlotStateRead.Known(SlotFormState.SUBMITTED), DrawnRead.Absent), SlotRead(2, SlotStateRead.Unrecognized("nope"), DrawnRead.Value(true))), unusable = 6, duplicates = 1),
            mixed
        )
        fun entry(json: String) = (slots("[$json]") as SlotsRead.Listed).entries.single()
        assertEquals("a slot number written as 1.0 is slot 1", 1, entry("""{"slot":1.0,"formState":"submitted"}""").slot)
        assertEquals(SlotStateRead.Absent, entry("""{"slot":1}""").formState)
        assertEquals(SlotStateRead.Absent, entry("""{"slot":1,"formState":null}""").formState)
        for ((json, kind) in listOf("1" to "number", "true" to "boolean", "{}" to "object")) assertEquals(json, SlotStateRead.NotAString(kind), entry("""{"slot":1,"formState":$json}""").formState)
        assertEquals(DrawnRead.Absent, entry("""{"slot":2,"formState":"submitted"}""").drawn)
        assertEquals(DrawnRead.Value(true), entry("""{"slot":2,"formState":"submitted","drawn":true}""").drawn)
        for ((json, kind) in listOf("\"true\"" to "string", "1" to "number", "{}" to "object")) assertEquals(json, DrawnRead.NotABoolean(kind), entry("""{"slot":2,"formState":"submitted","drawn":$json}""").drawn)
    }

    @Test
    fun `the settings version is read and named for every kind of value - the app acts on none of it`() {
        fun settings(json: String) = readObject("""{"settingsVersion":$json}""").settingsVersion
        assertEquals(SettingsVersionRead.Absent, settings("null"))
        assertEquals(SettingsVersionRead.Present("string", "abc"), settings("\"abc\""))
        assertEquals(SettingsVersionRead.Present("number", "7"), settings("7"))
        assertEquals(SettingsVersionRead.Present("boolean", "true"), settings("true"))
        assertEquals(SettingsVersionRead.Present("object", ""), settings("{}"))
        assertEquals(SettingsVersionRead.Present("array", ""), settings("[]"))
        assertEquals(SettingsVersionRead.Absent, readObject("{}").settingsVersion)
    }

    @Test
    fun `the symptom order reads for every kind of value`() {
        fun order(json: String) = (read("""{"symptomOrder":$json}""") as TechnicianFormConfigRead.Read).symptomOrder
        assertEquals(SymptomOrderRead.Absent, order("null"))
        for ((json, kind) in listOf("\"charging_slow\"" to "string", "3" to "number", "true" to "boolean", "{}" to "object")) {
            assertEquals(json, SymptomOrderRead.NotAList(kind), order(json))
        }
        assertEquals(SymptomOrderRead.Listed(emptyList(), 0, 0), order("[]"))
        assertEquals(
            "the known keys in the server's order, the rest counted",
            SymptomOrderRead.Listed(listOf(Symptom.CHARGING_SLOW, Symptom.BATTERY_SWOLLEN), unusable = 3, duplicates = 1),
            order("""["charging_slow","nope","battery_swollen","charging_slow",7,"none_noticed"]""")
        )
        assertEquals("none_noticed is outside the twelve: not usable in the order", SymptomOrderRead.Listed(emptyList(), 1, 0), order("""["none_noticed"]"""))
        assertEquals("the case matters", SymptomOrderRead.Listed(emptyList(), 1, 0), order("""["Charging_Slow"]"""))
    }

    @Test
    fun `the technicians read for every kind of value and of entry`() {
        fun techs(json: String) = (read("""{"technicians":$json}""") as TechnicianFormConfigRead.Read).technicians
        assertEquals(TechniciansRead.Absent, techs("null"))
        for ((json, kind) in listOf("\"x\"" to "string", "1" to "number", "false" to "boolean", "{}" to "object")) {
            assertEquals(json, TechniciansRead.NotAList(kind), techs(json))
        }
        assertEquals(TechniciansRead.Listed(emptyList(), 0, 0), techs("[]"))
        val mixed = techs(
            """[
              {"technicianId":"t1","displayName":"A"},
              {"technicianId":"t2","displayName":"B","active":true},
              {"technicianId":"t3","displayName":"C","active":false},
              {"technicianId":"","displayName":"D"},
              {"technicianId":"t5"},
              {"technicianId":"t6","displayName":null},
              {"displayName":"F"},
              {"technicianId":7,"displayName":"G"},
              {"technicianId":"t8","displayName":"  "},
              "t9",
              null
            ]"""
        )
        assertEquals(
            "usable: with an id and a name, not marked inactive; the rest counted and never shown (the server sends displayName: null for a technician with no name)",
            TechniciansRead.Listed(listOf(FormTechnician("t1", "A"), FormTechnician("t2", "B")), unusable = 8, inactive = 1),
            mixed
        )
        // active that is not a boolean does not hide the entry (it is not the explicit false)
        assertEquals(
            TechniciansRead.Listed(listOf(FormTechnician("t1", "A")), 0, 0),
            techs("""[{"technicianId":"t1","displayName":"A","active":"no"}]""")
        )
    }

    // ---- what the form makes of it ----

    @Test
    fun `a shop with the form off is NOT enabled - it is not the same as no technicians`() {
        assertEquals(FormOffer.NotEnabled, offer(notEnabledAnswer))
        assertEquals(FormOffer.NotEnabled, offer(notEnabledAnswer, slot = 2))
        assertTrue("a different state", (FormOffer.NotEnabled as FormOffer) != FormOffer.NoTechnicians)
        // while the same technician-less answer from an ENABLED shop IS no technicians
        assertEquals(FormOffer.NoTechnicians, offer(enabledAnswer(technicians = "[]")))
    }

    @Test
    fun `an enabled answer is offered for slot 1 with its technicians and the server's chip order`() {
        val o = offer(enabledAnswer()) as FormOffer.Offer
        assertEquals(listOf(FormTechnician("t1", "A"), FormTechnician("t2", "B")), o.technicians)
        assertEquals(SymptomOrderSource.SERVER, o.symptomOrder.source)
        assertEquals(Symptom.TWELVE, o.symptomOrder.order)
        assertEquals("offered", o.token)
    }

    @Test
    fun `the slot's own state decides - submitted is closed, the second needs its draw, and nothing is read as not submitted`() {
        val submittedFirst = enabledAnswer(slots = """[{"slot":1,"formState":"submitted"},{"slot":2,"formState":"not_submitted","drawn":true}]""")
        assertEquals(FormOffer.SlotClosed(SlotClosedReason.SUBMITTED), offer(submittedFirst, slot = 1))
        assertTrue("slot 2 is still open", offer(submittedFirst, slot = 2) is FormOffer.Offer)

        assertEquals(FormOffer.SlotClosed(SlotClosedReason.NOT_DRAWN), offer(enabledAnswer(), slot = 2))
        val drawn = enabledAnswer(slots = """[{"slot":1,"formState":"submitted"},{"slot":2,"formState":"not_submitted","drawn":true}]""")
        assertTrue(offer(drawn, slot = 2) is FormOffer.Offer)
        val drawnAndSubmitted = enabledAnswer(slots = """[{"slot":1,"formState":"submitted"},{"slot":2,"formState":"submitted","drawn":true}]""")
        assertEquals(FormOffer.SlotClosed(SlotClosedReason.SUBMITTED), offer(drawnAndSubmitted, slot = 2))
        assertEquals("submitted decides before the draw is looked at", FormOffer.SlotClosed(SlotClosedReason.SUBMITTED), offer(enabledAnswer(slots = """[{"slot":1,"formState":"not_submitted"},{"slot":2,"formState":"submitted","drawn":false}]"""), slot = 2))
        assertEquals("the first slot does not need a draw", true, offer(enabledAnswer(slots = """[{"slot":1,"formState":"not_submitted"}]"""), slot = 1) is FormOffer.Offer)
    }

    @Test
    fun `every answer the reader cannot make one of those is unreadable and says the first thing that did not read`() {
        val cases = listOf(
            null to "absent",
            "null" to "absent",
            "[]" to "not_an_object_array",
            "\"x\"" to "not_an_object_string",
            "{}" to "contract_version_absent",
            enabledAnswer(version = null) to "contract_version_absent",
            enabledAnswer(version = "\"2\"") to "version_not_an_int_string",
            enabledAnswer(version = "2.5") to "version_not_an_int_number",
            enabledAnswer(version = "3") to "contract_version_differs",
            enabledAnswer(version = "1") to "contract_version_differs",
            enabledAnswer(enabled = null) to "enabled_absent",
            enabledAnswer(enabled = "\"true\"") to "enabled_not_a_boolean_string",
            enabledAnswer(enabled = "1") to "enabled_not_a_boolean_number",
            """{"formContractVersion":2,"enabled":false}""" to "not_enabled_no_form_state",
            """{"formContractVersion":2,"enabled":false,"formState":"submitted"}""" to "not_enabled_no_form_state",
            """{"formContractVersion":2,"enabled":false,"formState":7}""" to "not_enabled_no_form_state",
            """{"formContractVersion":2,"enabled":true,"formState":"not_enabled","slots":$bothSlots,"technicians":$twoTechnicians}""" to "enabled_with_form_state",
            """{"formContractVersion":2,"enabled":true,"formState":"submitted","slots":$bothSlots,"technicians":$twoTechnicians}""" to "enabled_with_form_state",
            enabledAnswer(slots = null) to "slots_absent",
            enabledAnswer(slots = "\"x\"") to "slots_not_a_list_string",
            enabledAnswer(slots = "[]") to "slot_1_absent",
            enabledAnswer(slots = """[{"slot":2,"formState":"not_submitted","drawn":true}]""") to "slot_1_absent",
            enabledAnswer(slots = """[{"slot":1}]""") to "slot_state_absent",
            enabledAnswer(slots = """[{"slot":1,"formState":5}]""") to "slot_state_not_string_number",
            enabledAnswer(slots = """[{"slot":1,"formState":"draft"}]""") to "slot_state_unrecognized",
            enabledAnswer(technicians = null) to "technicians_absent",
            enabledAnswer(technicians = "{}") to "technicians_not_list_object"
        )
        for ((json, why) in cases) {
            val decision = decideOffer(TechnicianFormConfigRead.of(json?.let { JsonParser.parseString(it) }), 1)
            assertEquals("answer $json", FormOffer.Unreadable(why), decision)
            assertEquals("unreadable_$why", decision.token)
            assertTrue("a token the log prints whole (<= 40): ${decision.token}", decision.token.length <= 40)
        }
        // and for the second slot, the draw has to be there to be read
        assertEquals(FormOffer.Unreadable("drawn_absent"), offer(enabledAnswer(slots = """[{"slot":1,"formState":"not_submitted"},{"slot":2,"formState":"not_submitted"}]"""), slot = 2))
        assertEquals(FormOffer.Unreadable("drawn_not_a_boolean_string"), offer(enabledAnswer(slots = """[{"slot":1,"formState":"not_submitted"},{"slot":2,"formState":"not_submitted","drawn":"yes"}]"""), slot = 2))
        assertEquals(FormOffer.Unreadable("slot_2_absent"), offer(enabledAnswer(slots = """[{"slot":1,"formState":"not_submitted"}]"""), slot = 2))
    }

    @Test
    fun `no technician to choose from - every way it can be, on an answer that is otherwise readable`() {
        for (techs in listOf("[]", """[{"technicianId":"t","displayName":"x","active":false}]""", """["junk"]""", """[{"technicianId":"t","displayName":null}]""")) {
            assertEquals(techs, FormOffer.NoTechnicians, offer(enabledAnswer(technicians = techs)))
        }
        assertEquals("no_technicians", FormOffer.NoTechnicians.token)
    }

    @Test
    fun `the symptom order shown - the server's list first, the missing keys after it in the contract's order, and a contract order declared when there is no usable list`() {
        fun order(json: String) = effectiveSymptomOrder(read("""{"symptomOrder":$json}"""))
        val server = order("""["charging_slow","battery_swollen"]""")
        assertEquals(SymptomOrderSource.SERVER, server.source)
        assertEquals(listOf(Symptom.CHARGING_SLOW, Symptom.BATTERY_SWOLLEN) + Symptom.TWELVE.filter { it != Symptom.CHARGING_SLOW && it != Symptom.BATTERY_SWOLLEN }, server.order)
        assertEquals("all twelve are shown", 12, server.order.size)
        assertEquals("none repeated", 12, server.order.toSet().size)
        for (json in listOf("null", "[]", "\"x\"", "[\"nope\"]", "[\"none_noticed\"]")) {
            assertEquals(json, SymptomOrder(Symptom.TWELVE, SymptomOrderSource.CONTRACT_ORDER), order(json))
        }
        assertEquals(SymptomOrder(Symptom.TWELVE, SymptomOrderSource.CONTRACT_ORDER), effectiveSymptomOrder(TechnicianFormConfigRead.Absent))
        assertEquals(SymptomOrder(Symptom.TWELVE, SymptomOrderSource.CONTRACT_ORDER), effectiveSymptomOrder(TechnicianFormConfigRead.NotAnObject("array")))
        assertEquals(listOf("server", "contract_order"), SymptomOrderSource.values().map { it.token })
    }

    @Test
    fun `the technicians to choose from - null means nobody, and it is not an empty list that looks like a choice`() {
        assertNull(choosableTechnicians(TechnicianFormConfigRead.Absent))
        assertNull(choosableTechnicians(TechnicianFormConfigRead.NotAnObject("array")))
        assertNull(choosableTechnicians(read("""{}""")))
        assertNull(choosableTechnicians(read("""{"technicians":[]}""")))
        assertNull("every entry inactive or unusable", choosableTechnicians(read("""{"technicians":[{"technicianId":"t","displayName":"x","active":false},"junk"]}""")))
        val list = choosableTechnicians(read("""{"technicians":$twoTechnicians}"""))
        assertEquals(listOf(FormTechnician("t1", "A"), FormTechnician("t2", "B")), list)
        assertTrue(list!!.all { it.id.isNotBlank() && it.displayName.isNotBlank() })
    }
}
