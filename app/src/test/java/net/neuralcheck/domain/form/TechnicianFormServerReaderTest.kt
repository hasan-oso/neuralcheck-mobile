package net.neuralcheck.domain.form

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.neuralcheck.files.AppRequestPayloadGoldenTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The app's real producer judged by the server's rules - A-P6-08, rules 41 and 30. The producer is [TechnicianFormRequest.build] over states the real functions make (and the real `decideSubmit` /
 * `confirmPrompt` path of a press of «أرسل»); the judge is [ServerFormRulesCopy], a port of the server's `normalizeTechnicianForm` from the copy in
 * `evidence/run-2026-10-18/technician-form-server-wire-neuralcheck-backend-05275ba.md` (neuralcheck-backend@05275ba). Both check what ARRIVES and what it MEANS (rule 30): a field that reaches the server
 * under a name it does not read is silently dropped (it ignores a top-level key it does not know), so the tests compare what the server would STORE with what the technician answered.
 *
 * NOT TESTED against the server's acceptance: the enumerated VALUES (`protocolVocabulary.js` is not in the copy - see [ServerFormRulesCopy.Vocabulary]); the Firestore side (the session, the technician's
 * document, the second slot's draw, the blindness - `submitTechnicianForm`'s transaction); `getTechnicianFormConfig` and `recordReportFirstOpened` answers are tested in their own tests. No claim is made that
 * the deployed function behaves as the copy does.
 */
class TechnicianFormServerReaderTest {

    private val gson = GsonBuilder().serializeNulls().disableHtmlEscaping().create()

    private fun json(request: Map<String, Any?>): JsonObject = JsonParser.parseString(gson.toJson(request)).asJsonObject

    private fun built(state: TechnicianFormState, submittedAt: Long = 100_000): Map<String, Any?> =
        (TechnicianFormRequest.build(state, submittedAt) as FormRequestBuild.Built).request

    private val blank = openForm("sess-1", 1, Symptom.TWELVE, 1_000).withTechnician("tech-1")

    /** What a press of «أرسل» does: the app's own gates, then (when the prompt opens) «نعم، أرسل»; the request is built from the state that goes out. */
    private fun pressSend(state: TechnicianFormState, submittedAt: Long = 100_000): Map<String, Any?> {
        var decision = state.decideSubmit()
        if (decision is SubmitDecision.ShowPrompt) decision = decision.state.confirmPrompt().decideSubmit()
        val sent = (decision as? SubmitDecision.Send)?.state ?: throw AssertionError("the app's own gates stopped it: $decision")
        return built(sent, submittedAt)
    }

    private fun accepted(request: JsonObject): Map<String, Any?> = when (val v = ServerFormRulesCopy.normalize(request)) {
        is ServerFormRulesCopy.Verdict.Accepted -> v.form
        is ServerFormRulesCopy.Verdict.Refused -> throw AssertionError("the server's rules refuse it: ${v.refusal}${v.extra}  request=$request")
    }

    private fun accepted(request: Map<String, Any?>): Map<String, Any?> = accepted(json(request))

    private fun refused(request: Map<String, Any?>): ServerFormRulesCopy.Verdict.Refused =
        ServerFormRulesCopy.normalize(json(request)) as? ServerFormRulesCopy.Verdict.Refused ?: throw AssertionError("the server's rules accept it: ${gson.toJson(request)}")

    private fun sample(name: String): JsonObject = JsonParser.parseString(String(File(AppRequestPayloadGoldenTest.derivedDir, name).readBytes(), Charsets.UTF_8)).asJsonObject

    private val withNote = blank.withNote("n", 1_500)

    // ---- the three committed golden samples ----

    @Test
    fun `the three golden samples are accepted by the server's rules, and the server stores what they say`() {
        val all = accepted(sample("technician-form-request-all-answered-no-prompt.json"))
        assertEquals(mapOf("battery" to "good", "performance" to "good", "thermal" to "good", "overall" to "excellent"), all["grades"])
        assertEquals(mapOf("replaced_screen" to "works", "water_damage" to "not_tested", "prior_repair" to "works"), all["defects"])
        assertEquals(listOf("none_noticed"), all["symptoms"])
        assertEquals(mapOf("kind" to "percent", "percent" to 92L, "stateKey" to null), all["batteryHealth"])
        assertEquals(false, all["gameTried"])
        assertEquals(null, all["promptAction"])
        assertEquals("one time per answer the technician gave", 4 + 6 + 3 + 1 + 1 + 1, (all["perAnswerAt"] as List<*>).size)

        val partial = accepted(sample("technician-form-request-partial-explicit-nulls.json"))
        assertEquals(mapOf("battery" to "fair", "performance" to null, "thermal" to "not_tested", "overall" to null), partial["grades"])
        assertEquals("charging", "faulty", (partial["functional"] as Map<*, *>)["charging"])
        assertEquals(listOf("charging_slow"), partial["symptoms"])
        assertEquals(true, partial["knownFaultBeforeScan"])
        assertEquals(mapOf("kind" to "state", "percent" to null, "stateKey" to "weak"), partial["batteryHealth"])
        assertEquals("يسخن عند الشحن", partial["observedNote"])

        val contradiction = accepted(sample("technician-form-request-contradiction-revised.json"))
        assertEquals("will_review", contradiction["promptAction"])
        assertEquals(mapOf("battery" to "weak", "performance" to "good", "thermal" to "good", "overall" to "good"), contradiction["answersBeforePrompt"])
        assertEquals(mapOf("battery" to "weak", "performance" to "good", "thermal" to "good", "overall" to "fair"), contradiction["answersAfterPrompt"])
        assertEquals(mapOf("kind" to "not_shown", "percent" to null, "stateKey" to null), contradiction["batteryHealth"])
    }

    // ---- everything the phone lets out is accepted, and arrives as what was answered (rule 30: every value, not one) ----

    @Test
    fun `every grade of every category arrives as answered`() {
        for (category in GradeCategory.values()) {
            for (answer in GradeAnswer.values()) {
                // a grade below good makes the note mandatory (the app blocks the press without it) - the note is always given here
                val stored = accepted(pressSend(withNote.withGrade(category, answer, 2_000)))
                val grades = stored["grades"] as Map<*, *>
                assertEquals("${category.wire}=${answer.wire}", answer.wire, grades[category.wire])
                assertEquals("the other three stay null", 3, grades.values.count { it == null })
            }
        }
    }

    @Test
    fun `every functional row and every defect row arrives as answered - the defects under the name the server reads`() {
        for (row in FunctionalRow.values()) for (answer in RowAnswer.values()) {
            val stored = accepted(pressSend(blank.withFunctional(row, answer, 2_000)))
            assertEquals("${row.wire}=${answer.wire}", answer.wire, (stored["functional"] as Map<*, *>)[row.wire])
        }
        for (row in DefectRow.values()) for (answer in RowAnswer.values()) {
            val stored = accepted(pressSend(blank.withDefect(row, answer, 2_000)))
            assertEquals("${row.wire}=${answer.wire} (the server's \"defects\")", answer.wire, (stored["defects"] as Map<*, *>)[row.wire])
        }
    }

    @Test
    fun `every symptom, none_noticed, every battery health shape, the chips and the note arrive as answered`() {
        for (symptom in Symptom.values()) {
            assertEquals(symptom.wire, listOf(symptom.wire), accepted(pressSend(blank.toggleSymptom(symptom, 2_000)))["symptoms"])
        }
        val twoSymptoms = accepted(pressSend(blank.toggleSymptom(Symptom.HEATS_FAST_IN_USE, 2_000).toggleSymptom(Symptom.BATTERY_SWOLLEN, 2_100)))
        assertEquals("in the order they were SHOWN", listOf("battery_swollen", "heats_fast_in_use"), twoSymptoms["symptoms"])
        for (percent in listOf(0, 1, 50, 100)) {
            assertEquals(mapOf("kind" to "percent", "percent" to percent.toLong(), "stateKey" to null), accepted(pressSend(blank.withBatteryHealth(BatteryHealth.Percent(percent), 2_000)))["batteryHealth"])
        }
        for (key in BatteryStateKey.values()) {
            assertEquals(mapOf("kind" to "state", "percent" to null, "stateKey" to key.wire), accepted(pressSend(blank.withBatteryHealth(BatteryHealth.State(key), 2_000)))["batteryHealth"])
        }
        assertEquals(mapOf("kind" to "not_shown", "percent" to null, "stateKey" to null), accepted(pressSend(blank.withBatteryHealth(BatteryHealth.NotShown, 2_000)))["batteryHealth"])
        assertEquals(true, accepted(pressSend(blank.toggleKnownFault(2_000)))["knownFaultBeforeScan"])
        for (answer in listOf(true, false)) assertEquals(answer, accepted(pressSend(blank.withGameTried(answer, 2_000)))["gameTried"])
        assertEquals("the note as typed", "ما لاحظته", accepted(pressSend(blank.withGrade(GradeCategory.OVERALL, GradeAnswer.WEAK, 2_000).withNote("ما لاحظته", 2_100)))["observedNote"])
        assertEquals("and the whole twelve order that was shown", Symptom.TWELVE.map { it.wire }, accepted(pressSend(blank.toggleSymptom(Symptom.CHARGING_SLOW, 2_000)))["symptomOrderShown"])
    }

    @Test
    fun `the answer times arrive as the numbers the server reads - one per answer event, in order, inside the form's span`() {
        val s = withNote.withGrade(GradeCategory.BATTERY, GradeAnswer.GOOD, 2_000).toggleSymptom(Symptom.CHARGING_SLOW, 3_000).withGrade(GradeCategory.THERMAL, GradeAnswer.GOOD, 4_000)
        val stored = accepted(built(s, submittedAt = 9_000))
        // withNote's own event (1_500) came first
        assertEquals(listOf(1_500L, 2_000L, 3_000L, 4_000L), stored["perAnswerAt"])
        assertEquals(1_000L, stored["openedAt"])
        assertEquals(1_500L, stored["firstTapAt"])
        assertEquals(9_000L, stored["submittedAt"])
        assertEquals("the app sends no sentOffline, the server reads that as false", false, stored["sentOffline"])
    }

    @Test
    fun `the prompt record arrives complete - both actions, the snapshots before and after`() {
        for ((action, wire) in listOf(PromptAction.CONFIRMED to "confirmed_send", PromptAction.REVISE to "will_review")) {
            val shown = (blank.withGrade(GradeCategory.BATTERY, GradeAnswer.WEAK, 2_000).withGrade(GradeCategory.OVERALL, GradeAnswer.GOOD, 2_100).withNote("n", 2_200).decideSubmit() as SubmitDecision.ShowPrompt).state
            val acted = (if (action == PromptAction.CONFIRMED) shown.confirmPrompt() else shown.revisePrompt()).withGrade(GradeCategory.OVERALL, GradeAnswer.FAIR, 3_000)
            val stored = accepted(built(acted))
            assertEquals(wire, stored["promptAction"])
            assertEquals(mapOf("battery" to "weak", "performance" to null, "thermal" to null, "overall" to "good"), stored["answersBeforePrompt"])
            assertEquals(mapOf("battery" to "weak", "performance" to null, "thermal" to null, "overall" to "fair"), stored["answersAfterPrompt"])
        }
        // pressing «نعم، أرسل» through the real path (decideSubmit, confirmPrompt) sends a complete record too
        val confirmedPath = accepted(pressSend(blank.withGrade(GradeCategory.THERMAL, GradeAnswer.WEAK, 2_000).withGrade(GradeCategory.OVERALL, GradeAnswer.EXCELLENT, 2_100).withNote("n", 2_200)))
        assertEquals("confirmed_send", confirmedPath["promptAction"])
    }

    // ---- the rules have teeth: what the app sent before 2026-10-18 is what the verifier found, and the judge sees it ----

    @Test
    fun `the older shapes are caught - the defects under the old name vanish, the item pairs and the old action values are refused`() {
        val answered = withNote.withDefect(DefectRow.WATER_DAMAGE, RowAnswer.FAULTY, 2_000).withGrade(GradeCategory.OVERALL, GradeAnswer.GOOD, 2_100)
        val now = built(answered)
        assertEquals("faulty", ((accepted(now)["defects"]) as Map<*, *>)["water_damage"])

        // the old container name: the server ignores a top-level key it does not know, so the answer is DROPPED without a refusal (rule 25)
        val oldName = LinkedHashMap(now).also { it["visibleDefects"] = it.remove("defects") }
        assertTrue("the defects the technician gave are gone", (accepted(oldName)["defects"] as Map<*, *>).values.all { it == null })

        // the old {item, at} entries: ENTRY_OUT_OF_RANGE, the finding of the independent verifier
        val oldTimes = LinkedHashMap(now).also { m -> m["perAnswerAt"] = answered.perAnswerAt.map { mapOf("item" to it.item, "at" to it.atMs) } }
        assertEquals(ServerFormRulesCopy.Verdict.Refused("TIMES_INVALID", " cause=ENTRY_OUT_OF_RANGE field=perAnswerAt"), refused(oldTimes))

        // the old action values
        val contradiction = sample("technician-form-request-contradiction-revised.json")
        for (old in listOf("confirmed", "revise")) {
            val oldAction = contradiction.deepCopy().also { it.addProperty("promptAction", old) }
            assertEquals(ServerFormRulesCopy.Verdict.Refused("GRADES_INVALID", " cause=PROMPT_ACTION_NOT_ALLOWED"), ServerFormRulesCopy.normalize(oldAction))
        }
    }

    @Test
    fun `the judge refuses what its copy refuses - the other named causes, one each`() {
        val ok = built(withNote.withGrade(GradeCategory.OVERALL, GradeAnswer.GOOD, 2_000))
        fun mutated(change: (MutableMap<String, Any?>) -> Unit): Map<String, Any?> = LinkedHashMap(ok).also(change)
        assertEquals("CONTRACT_VERSION_INVALID", refused(mutated { it["formContractVersion"] = 1 }).refusal)
        assertEquals("SLOT_INVALID", refused(mutated { it["slot"] = 3 }).refusal)
        assertEquals("TECHNICIAN_UNKNOWN", refused(mutated { it["technicianId"] = "a/b" }).refusal)
        assertEquals("SESSION_ID_REQUIRED", refused(mutated { it["sessionId"] = "" }).refusal)
        assertEquals("GRADES_INVALID", refused(mutated { it["grades"] = mapOf("memory" to "good") }).refusal)
        assertEquals("FUNCTIONAL_INVALID", refused(mutated { it["functional"] = mapOf("camera" to "perfect") }).refusal)
        assertEquals("SYMPTOM_ORDER_INVALID", refused(mutated { it["symptomOrderShown"] = emptyList<String>() }).refusal)
        assertEquals("NOTE_INVALID", refused(built(blank.withGrade(GradeCategory.BATTERY, GradeAnswer.WEAK, 2_000))).refusal)
        assertEquals("TIMES_INVALID", refused(mutated { it["perAnswerAt"] = listOf(999_999_999L) }).refusal)
        assertEquals("BATTERY_HEALTH_INVALID", refused(mutated { it["batteryHealth"] = mapOf("kind" to "percent", "percent" to 101, "stateKey" to null) }).refusal)
    }

    // ---- KNOWN DIFFERENCES: states the app lets out and the server refuses by design. Pinned so that a change on either side forces a decision (rule 23: replaced, not deleted, when decided). ----

    @Test
    fun `KNOWN DIFFERENCE - a form whose only taps were undone is sent by the app and refused by the server as empty`() {
        val undone = blank.toggleKnownFault(2_000).toggleKnownFault(3_000)
        assertTrue("the app's gate counts the two taps as answers", undone.hasAnyAnswer)
        assertTrue(undone.decideSubmit() is SubmitDecision.Send)
        assertEquals(ServerFormRulesCopy.Verdict.Refused("EMPTY", ""), refused(built(undone)))
    }

    @Test
    fun `KNOWN DIFFERENCE - the prompt shown with no action recorded is a request the server refuses as an incomplete prompt record, and the view model never sends it`() {
        val shown = (blank.withGrade(GradeCategory.BATTERY, GradeAnswer.WEAK, 2_000).withGrade(GradeCategory.OVERALL, GradeAnswer.GOOD, 2_100).withNote("n", 2_200).decideSubmit() as SubmitDecision.ShowPrompt).state
        assertEquals(ServerFormRulesCopy.Verdict.Refused("GRADES_INVALID", " cause=PROMPT_RECORD_INCOMPLETE"), refused(built(shown)))
    }

    @Test
    fun `KNOWN DIFFERENCE - a form with no judgement has no firstTapAt, which the server refuses - the app's gate stops it before it is built for sending`() {
        assertEquals(ServerFormRulesCopy.Verdict.Refused("TIMES_INVALID", " cause=ABSENT field=firstTapAt"), refused(built(blank)))
        assertEquals(SubmitDecision.Blocked(SubmitBlock.NO_ANSWERS), blank.decideSubmit())
    }
}
