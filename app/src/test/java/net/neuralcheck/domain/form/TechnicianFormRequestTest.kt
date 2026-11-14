package net.neuralcheck.domain.form

import com.google.gson.GsonBuilder
import com.google.gson.JsonNull
import com.google.gson.JsonParser
import net.neuralcheck.files.AppRequestPayloadGoldenTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * The request of `submitTechnicianForm` - A-P6-08. What leaves the phone, key by key, built by the real [TechnicianFormRequest] from a state; and three golden samples of it, for the server's reader
 * (rule 41: a reader is tested with a sample made by the real producer, at the point where it reads).
 *
 * Which names are the contract's and which are this app's: see [TechnicianFormWire] ([A5] / [APP] on each). The server reads this from the same contract copy and builds its reader in parallel; the
 * [APP] names and the samples below are what it has to match (reported to the coordinator).
 *
 * Rule 20: the request carries what the technician did, raw - an unanswered item is an explicit null, nothing the SERVER decides is in it ([serverOwnedKeys]).
 */
class TechnicianFormRequestTest {

    private val expectedKeyOrder = listOf(
        "sessionId", "slot", "formContractVersion", "technicianId", "grades", "functional", "defects", "symptoms", "symptomOrderShown", "known_fault_before_scan",
        "batteryHealth", "gameTried", "observedNote", "openedAt", "firstTapAt", "perAnswerAt", "submittedAt", "answersBeforePrompt", "answersAfterPrompt", "promptAction"
    )

    /**
     * What the server computes or stamps (contract A5; and the fields its handler writes itself, technicianForm.js:650-683 in the evidence copy): never sent. `sentOffline` is not here: it is the CLIENT's
     * key, and this app sends none (the test "the request has no sentOffline" below).
     */
    private val serverOwnedKeys = listOf(
        "receivedAt", "blindness", "blindnessBasis", "reportFirstOpenedAt", "fast", "allFourGradesEqual", "formState", "clock", "contradictionRules", "batteryHealthState", "ownerId"
    )

    private fun built(state: TechnicianFormState, submittedAt: Long = 90_000): Map<String, Any?> =
        (TechnicianFormRequest.build(state, submittedAt) as FormRequestBuild.Built).request

    private val blank = openForm("sess-1", 1, Symptom.TWELVE, 1_000).withTechnician("tech-1")

    @Test
    fun `the request has these keys in this order - and none the server owns`() {
        val request = built(blank.withGrade(GradeCategory.OVERALL, GradeAnswer.GOOD, 2_000))
        assertEquals(expectedKeyOrder, request.keys.toList())
        for (key in serverOwnedKeys) assertFalse("$key is the server's (rule 20)", key in request)
    }

    @Test
    fun `an item not answered is an explicit null - every grade, row, defect, and the optional questions`() {
        val request = built(blank.withGrade(GradeCategory.OVERALL, GradeAnswer.GOOD, 2_000))
        @Suppress("UNCHECKED_CAST")
        val grades = request["grades"] as Map<String, Any?>
        assertEquals(listOf("battery", "performance", "thermal", "overall"), grades.keys.toList())
        assertEquals(mapOf("battery" to null, "performance" to null, "thermal" to null, "overall" to "good"), grades)
        @Suppress("UNCHECKED_CAST")
        val functional = request["functional"] as Map<String, Any?>
        assertEquals(listOf("screen_touch", "camera", "audio", "buttons", "charging", "network"), functional.keys.toList())
        assertTrue("every functional row is an explicit null, never not_tested", functional.values.all { it == null })
        @Suppress("UNCHECKED_CAST")
        val defects = request["defects"] as Map<String, Any?>
        assertEquals(listOf("replaced_screen", "water_damage", "prior_repair"), defects.keys.toList())
        assertTrue(defects.values.all { it == null })
        assertEquals(emptyList<String>(), request["symptoms"])
        assertTrue("known_fault_before_scan is present and null", "known_fault_before_scan" in request && request["known_fault_before_scan"] == null)
        assertTrue("batteryHealth is present and null", "batteryHealth" in request && request["batteryHealth"] == null)
        assertTrue("gameTried is present and null", "gameTried" in request && request["gameTried"] == null)
        assertTrue("observedNote is present and null", "observedNote" in request && request["observedNote"] == null)
    }

    @Test
    fun `every answer is its wire value - grades, rows, defects, game, known fault, note`() {
        var s = blank
        GradeCategory.values().forEachIndexed { i, c -> s = s.withGrade(c, GradeAnswer.values()[i], 2_000L + i) }
        FunctionalRow.values().forEachIndexed { i, r -> s = s.withFunctional(r, RowAnswer.values()[i % 3], 3_000L + i) }
        DefectRow.values().forEachIndexed { i, r -> s = s.withDefect(r, RowAnswer.values()[(i + 1) % 3], 4_000L + i) }
        s = s.withGameTried(false, 5_000).toggleKnownFault(5_100).withNote("ما لاحظته", 5_200)
        val r = built(s)
        @Suppress("UNCHECKED_CAST") val grades = r["grades"] as Map<String, Any?>
        assertEquals(mapOf("battery" to "excellent", "performance" to "good", "thermal" to "fair", "overall" to "weak"), grades)
        @Suppress("UNCHECKED_CAST") val functional = r["functional"] as Map<String, Any?>
        assertEquals(listOf("works", "faulty", "not_tested", "works", "faulty", "not_tested"), functional.values.toList())
        @Suppress("UNCHECKED_CAST") val defects = r["defects"] as Map<String, Any?>
        assertEquals(listOf("faulty", "not_tested", "works"), defects.values.toList())
        assertEquals(false, r["gameTried"])
        assertEquals(true, r["known_fault_before_scan"])
        assertEquals("ما لاحظته", r["observedNote"])
        assertEquals("a not_tested grade is an answer, written as itself", "not_tested", (built(blank.withGrade(GradeCategory.BATTERY, GradeAnswer.NOT_TESTED, 2_000))["grades"] as Map<*, *>)["battery"])
    }

    @Test
    fun `the symptoms go in the order they were shown, the order shown is stored, and none_noticed is an element`() {
        val order = listOf(Symptom.CHARGING_SLOW) + Symptom.TWELVE.filter { it != Symptom.CHARGING_SLOW }
        var s = openForm("sess-1", 1, order, 1_000).withTechnician("tech-1")
        s = s.toggleSymptom(Symptom.BATTERY_SWOLLEN, 2_000).toggleSymptom(Symptom.CHARGING_SLOW, 2_100)
        val r = built(s)
        assertEquals("shown order, not tap order", listOf("charging_slow", "battery_swollen"), r["symptoms"])
        assertEquals(order.map { it.wire }, r["symptomOrderShown"])
        assertEquals(listOf("none_noticed"), built(s.toggleSymptom(Symptom.NONE_NOTICED, 2_200))["symptoms"])
        assertEquals("the twelve only: none_noticed is drawn last and is no part of the order", 12, (r["symptomOrderShown"] as List<*>).size)
    }

    @Test
    fun `the battery health has the three shapes of the contract - and an unanswered one is null, not a shape`() {
        val percent = built(blank.withBatteryHealth(BatteryHealth.Percent(87), 2_000))["batteryHealth"]
        assertEquals(mapOf("kind" to "percent", "percent" to 87, "stateKey" to null), percent)
        assertEquals(listOf("kind", "percent", "stateKey"), (percent as Map<*, *>).keys.toList())
        for (key in BatteryStateKey.values()) {
            assertEquals(mapOf("kind" to "state", "percent" to null, "stateKey" to key.wire), built(blank.withBatteryHealth(BatteryHealth.State(key), 2_000))["batteryHealth"])
        }
        assertEquals(mapOf("kind" to "not_shown", "percent" to null, "stateKey" to null), built(blank.withBatteryHealth(BatteryHealth.NotShown, 2_000))["batteryHealth"])
        assertNull(built(blank.withGrade(GradeCategory.OVERALL, GradeAnswer.GOOD, 2_000))["batteryHealth"])
    }

    @Test
    fun `the times are the device clock's, the answer events are in order, and firstTapAt is null before any judgement`() {
        val none = built(blank, submittedAt = 77_000)
        assertEquals(1_000L, none["openedAt"])
        assertNull(none["firstTapAt"])
        assertEquals(emptyList<Any>(), none["perAnswerAt"])
        assertEquals(77_000L, none["submittedAt"])
        val s = blank.withGrade(GradeCategory.BATTERY, GradeAnswer.GOOD, 2_000).toggleSymptom(Symptom.CHARGING_SLOW, 3_000).withNote("n", 4_000)
        val r = built(s, submittedAt = 9_000)
        assertEquals(2_000L, r["firstTapAt"])
        // replaces [{item, at}] entries (rule 23): the server reads a plain array of numbers (technicianFormContract.js:155-157, ENTRY_OUT_OF_RANGE for anything else) - the item stays on the phone, in the state
        assertEquals(listOf(2_000L, 3_000L, 4_000L), r["perAnswerAt"])
        assertEquals("the item each time belongs to is still in the state", listOf("battery", "symptoms", "observedNote"), s.perAnswerAt.map { it.item })
        // every entry is a number the server accepts: between openedAt and submittedAt (inclusive), whatever the answer events were
        val times = r["perAnswerAt"] as List<*>
        assertTrue(times.all { it is Long && it >= r["openedAt"] as Long && it <= r["submittedAt"] as Long })
    }

    @Test
    fun `the request has no sentOffline - this app has no offline send - and none of the older names`() {
        // sentOffline: the server reads it as "sent without a network" (technicianFormContract.js:263, absent = false); a form here is built and sent in the same call, so there is nothing true to put in it.
        // The day an offline queue exists it must send sentOffline: true and THIS test is replaced then (rule 23), not deleted by whoever finds it red.
        val request = built(blank.withGrade(GradeCategory.OVERALL, GradeAnswer.GOOD, 2_000))
        assertFalse("sentOffline", "sentOffline" in request)
        assertFalse("the older name of the defects container (the server reads defects)", "visibleDefects" in request)
    }

    @Test
    fun `the prompt keys are null when it was not shown, and carry what the technician saw and did when it was`() {
        val plain = built(blank.withGrade(GradeCategory.OVERALL, GradeAnswer.GOOD, 2_000))
        assertTrue(listOf("answersBeforePrompt", "answersAfterPrompt", "promptAction").all { it in plain && plain[it] == null })
        val s = blank.withGrade(GradeCategory.BATTERY, GradeAnswer.WEAK, 2_000).withGrade(GradeCategory.OVERALL, GradeAnswer.GOOD, 2_100).withNote("n", 2_200)
        val shownState = (s.decideSubmit() as SubmitDecision.ShowPrompt).state
        for ((action, wire) in listOf(PromptAction.CONFIRMED to "confirmed_send", PromptAction.REVISE to "will_review")) {
            val after = (if (action == PromptAction.CONFIRMED) shownState.confirmPrompt() else shownState.revisePrompt()).withGrade(GradeCategory.OVERALL, GradeAnswer.FAIR, 3_000)
            val r = built(after)
            assertEquals(wire, r["promptAction"])
            assertEquals(mapOf("battery" to "weak", "performance" to null, "thermal" to null, "overall" to "good"), r["answersBeforePrompt"])
            assertEquals(mapOf("battery" to "weak", "performance" to null, "thermal" to null, "overall" to "fair"), r["answersAfterPrompt"])
        }
        // shown, no action recorded: the keys say so (the action is null, the snapshots are there)
        assertNull(built(shownState)["promptAction"])
    }

    @Test
    fun `a state that cannot be sent is refused with its reason - nothing is cut and nothing is defaulted`() {
        val open = openForm("sess-1", 1, Symptom.TWELVE, 1_000)
        assertEquals(FormRequestBuild.Refused(FormRequestBuild.Refused.Reason.NO_TECHNICIAN), TechnicianFormRequest.build(open.withGrade(GradeCategory.BATTERY, GradeAnswer.GOOD, 2_000), 9_000))
        // a note over the limit cannot get in through withNote; a state built another way is refused whole
        val tooLong = blank.copy(observedNote = "x".repeat(TechnicianFormLimits.NOTE_MAX_CHARS + 1))
        assertEquals(FormRequestBuild.Refused(FormRequestBuild.Refused.Reason.NOTE_TOO_LONG), TechnicianFormRequest.build(tooLong, 9_000))
        assertTrue(TechnicianFormRequest.build(blank.copy(observedNote = "x".repeat(TechnicianFormLimits.NOTE_MAX_CHARS)), 9_000) is FormRequestBuild.Built)
    }

    @Test
    fun `a blank note is not sent as text - it is the explicit null of an unanswered note`() {
        assertNull(built(blank.copy(observedNote = "   "))["observedNote"])
        assertEquals("kept as typed, spaces inside and all", " ab ", built(blank.copy(observedNote = " ab "))["observedNote"])
    }

    @Test
    fun `the slot and the session are the form's id, and the version is 2`() {
        val r = built(openForm("sess-77", 2, Symptom.TWELVE, 1_000).withTechnician("tech-2"))
        assertEquals("sess-77", r["sessionId"])
        assertEquals(2, r["slot"])
        assertEquals(2, r["formContractVersion"])
        assertEquals("tech-2", r["technicianId"])
    }

    // ---- the golden samples (rule 41) ----

    private val gson = GsonBuilder().setPrettyPrinting().serializeNulls().disableHtmlEscaping().create()

    /** The three states the samples are built from: each a different way a form leaves the phone. */
    private fun samples(): Map<String, String> {
        val allAnswered = run {
            var s = openForm("sess-golden-1", 1, Symptom.TWELVE, 1_760_000_000_000L).withTechnician("tech-golden-a")
            for ((i, c) in GradeCategory.values().withIndex()) s = s.withGrade(c, if (i == 3) GradeAnswer.EXCELLENT else GradeAnswer.GOOD, 1_760_000_002_000L + i * 900)
            FunctionalRow.values().forEachIndexed { i, r -> s = s.withFunctional(r, RowAnswer.WORKS, 1_760_000_006_000L + i * 700) }
            DefectRow.values().forEachIndexed { i, r -> s = s.withDefect(r, if (i == 1) RowAnswer.NOT_TESTED else RowAnswer.WORKS, 1_760_000_011_000L + i * 600) }
            s = s.toggleSymptom(Symptom.NONE_NOTICED, 1_760_000_013_000L).withBatteryHealth(BatteryHealth.Percent(92), 1_760_000_014_000L).withGameTried(false, 1_760_000_015_000L)
            gson.toJson(built(s, submittedAt = 1_760_000_017_000L))
        }
        val partial = run {
            var s = openForm("sess-golden-2", 1, listOf(Symptom.CHARGING_SLOW, Symptom.HOT_WHILE_CHARGING) + Symptom.TWELVE.filter { it != Symptom.CHARGING_SLOW && it != Symptom.HOT_WHILE_CHARGING }, 1_760_000_100_000L)
                .withTechnician("tech-golden-b")
            s = s.withGrade(GradeCategory.BATTERY, GradeAnswer.FAIR, 1_760_000_103_000L).withGrade(GradeCategory.THERMAL, GradeAnswer.NOT_TESTED, 1_760_000_104_000L)
                .withFunctional(FunctionalRow.CHARGING, RowAnswer.FAULTY, 1_760_000_105_000L).toggleSymptom(Symptom.CHARGING_SLOW, 1_760_000_106_000L)
                .toggleKnownFault(1_760_000_107_000L).withBatteryHealth(BatteryHealth.State(BatteryStateKey.WEAK), 1_760_000_108_000L).withNote("يسخن عند الشحن", 1_760_000_109_000L)
            gson.toJson(built(s, submittedAt = 1_760_000_111_000L))
        }
        val contradiction = run {
            var s = openForm("sess-golden-3", 1, Symptom.TWELVE, 1_760_000_200_000L).withTechnician("tech-golden-c")
            s = s.withGrade(GradeCategory.BATTERY, GradeAnswer.WEAK, 1_760_000_202_000L).withGrade(GradeCategory.PERFORMANCE, GradeAnswer.GOOD, 1_760_000_203_000L)
                .withGrade(GradeCategory.THERMAL, GradeAnswer.GOOD, 1_760_000_204_000L).withGrade(GradeCategory.OVERALL, GradeAnswer.GOOD, 1_760_000_205_000L)
                .withBatteryHealth(BatteryHealth.NotShown, 1_760_000_206_000L).withNote("الغطاء الخلفي مرتفع", 1_760_000_207_000L)
            val shown = (s.decideSubmit() as SubmitDecision.ShowPrompt).state.revisePrompt().withGrade(GradeCategory.OVERALL, GradeAnswer.FAIR, 1_760_000_212_000L)
            gson.toJson(built(shown, submittedAt = 1_760_000_214_000L))
        }
        return linkedMapOf(
            "technician-form-request-all-answered-no-prompt.json" to allAnswered + "\n",
            "technician-form-request-partial-explicit-nulls.json" to partial + "\n",
            "technician-form-request-contradiction-revised.json" to contradiction + "\n"
        )
    }

    private val begin = "<!-- technician-form-sha256:begin -->"
    private val end = "<!-- technician-form-sha256:end -->"

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
    fun `the committed samples are what the real builder makes today - byte for byte`() {
        for ((name, text) in samples()) {
            val file = File(AppRequestPayloadGoldenTest.derivedDir, name)
            assertTrue("$name is not committed - regenerate with ${AppRequestPayloadGoldenTest.REGENERATE_ENV}=1 (see TechnicianFormRequestTest)", file.isFile)
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
    fun `each sample parses, has the contract's keys in order, explicit nulls kept, and none the server owns`() {
        for ((name, text) in samples()) {
            val json = JsonParser.parseString(text).asJsonObject
            assertEquals(name, expectedKeyOrder, json.keySet().toList())
            for (key in serverOwnedKeys) assertFalse("$name has $key", json.has(key))
            assertTrue("$name: an unanswered item is a JSON null, not a missing key", json.has("known_fault_before_scan") && json.has("promptAction"))
            if (name.contains("partial")) {
                assertTrue(json.getAsJsonObject("grades").get("performance") is JsonNull)
                assertTrue(json.get("gameTried") is JsonNull)
            }
        }
    }
}
