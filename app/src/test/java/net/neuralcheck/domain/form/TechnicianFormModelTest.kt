package net.neuralcheck.domain.form

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * The technician's form as logic - A-P6-08. Pure values, no Android. The expected wire values are WRITTEN OUT here from the protocol contract copy
 * (`evidence/run-2026-10-18/protocol-contract-A1-A13-neuralcheck-backend-8e5c426.md`, A5, lines 74-96; from neuralcheck-backend@8e5c426, a copy that drifts) and the approved levels
 * (`plan/TECH-LEVELS-WORDING-FINAL-2026-10-10.md`): a golden pin. Rule 30: every value of every enumeration is run, and what the logic MAKES of it is asserted - the note rule over every grade, the
 * contradiction rule over every overall x category pair, the request over every kind of battery-health answer and every prompt state.
 */
class TechnicianFormModelTest {

    private val open = openForm("sess-1", 1, Symptom.TWELVE, nowMs = 1_000)

    // ---- the wire values ----

    @Test
    fun `the four grades, the five answers, the rows and the answers are those of contract A5`() {
        assertEquals(listOf("battery", "performance", "thermal", "overall"), GradeCategory.values().map { it.wire })
        assertEquals(listOf("excellent", "good", "fair", "weak", "not_tested"), GradeAnswer.values().map { it.wire })
        assertEquals("not_tested is a fifth option, not a level", listOf(true, true, true, true, false), GradeAnswer.values().map { it.isLevel })
        assertEquals(listOf("screen_touch", "camera", "audio", "buttons", "charging", "network"), FunctionalRow.values().map { it.wire })
        assertEquals(listOf("replaced_screen", "water_damage", "prior_repair"), DefectRow.values().map { it.wire })
        assertEquals(listOf("works", "faulty", "not_tested"), RowAnswer.values().map { it.wire })
        assertEquals(listOf("percent", "state", "not_shown"), BatteryHealthKind.values().map { it.wire })
        assertEquals(listOf("normal", "weak", "bad"), BatteryStateKey.values().map { it.wire })
        assertEquals(2, TechnicianFormWire.FORM_CONTRACT_VERSION)
        assertEquals("submitTechnicianForm", TechnicianFormWire.SUBMIT_CALLABLE)
        assertEquals("getTechnicianFormConfig", TechnicianFormWire.CONFIG_CALLABLE)
        assertEquals("recordReportFirstOpened", ReportFirstOpenedWire.CALLABLE)
        assertEquals("FORM_SLOT_SAME_TECHNICIAN", TechnicianFormWire.REFUSAL_SLOT_SAME_TECHNICIAN)
    }

    @Test
    fun `the twelve symptoms and none_noticed are the contract's keys in the contract's table order - and no memory or storage grade exists`() {
        assertEquals(
            listOf(
                "battery_dies_before_day_end", "battery_jumps_or_shuts_down", "battery_swollen", "hot_while_charging", "charging_slow", "slow_or_freezes_daily",
                "restarts_under_load", "heats_fast_in_use", "system_heat_warning", "game_stutter_after_minutes", "game_screen_lines_or_flicker", "games_freeze_or_close",
                "none_noticed"
            ),
            Symptom.values().map { it.wire }
        )
        assertEquals(12, Symptom.TWELVE.size)
        assertFalse(Symptom.NONE_NOTICED in Symptom.TWELVE)
        for (outside in listOf("memory", "storage", "cpu_performance")) assertFalse(outside, GradeCategory.values().any { it.wire == outside })
    }

    @Test
    fun `the two prompt actions are the server's values - the design names the buttons, the server's reader names the values`() {
        // replaces [confirmed, revise] (rule 23): the server refuses them (technicianFormContract.js:34 PROMPT_ACTION = confirmed_send | will_review, refusal at :230; copy in
        // evidence/run-2026-10-18/technician-form-server-wire-neuralcheck-backend-05275ba.md) - 2026-10-18, A-P6-08 wire match
        assertEquals(listOf("confirmed_send", "will_review"), PromptAction.values().map { it.wire })
    }

    // ---- answers and the clock ----

    @Test
    fun `a grade is an answer event with the category's wire key, and the first judgement starts firstTapAt`() {
        assertNull(open.firstTapAtMs)
        val s = open.withGrade(GradeCategory.THERMAL, GradeAnswer.GOOD, nowMs = 2_000)
        assertEquals(GradeAnswer.GOOD, s.grades[GradeCategory.THERMAL])
        assertEquals(2_000L, s.firstTapAtMs)
        assertEquals(listOf(AnswerEvent("thermal", 2_000)), s.perAnswerAt)
        val t = s.withFunctional(FunctionalRow.CAMERA, RowAnswer.FAULTY, nowMs = 3_500)
        assertEquals("firstTapAt stays at the first", 2_000L, t.firstTapAtMs)
        assertEquals(listOf(AnswerEvent("thermal", 2_000), AnswerEvent("camera", 3_500)), t.perAnswerAt)
    }

    @Test
    fun `choosing who judges is not a judgement - no event and no firstTapAt - and a blank id is nobody`() {
        val s = open.withTechnician("tech-9")
        assertEquals("tech-9", s.technicianId)
        assertNull(s.firstTapAtMs)
        assertTrue(s.perAnswerAt.isEmpty())
        assertFalse(s.hasAnyAnswer)
        assertNull(open.withTechnician("  ").technicianId)
        assertNull(s.withTechnician(null).technicianId)
    }

    @Test
    fun `the same answer again changes nothing - no event, the same value`() {
        val s = open.withGrade(GradeCategory.BATTERY, GradeAnswer.FAIR, 2_000)
        assertSame(s, s.withGrade(GradeCategory.BATTERY, GradeAnswer.FAIR, 9_000))
        val r = open.withDefect(DefectRow.WATER_DAMAGE, RowAnswer.WORKS, 2_000)
        assertSame(r, r.withDefect(DefectRow.WATER_DAMAGE, RowAnswer.WORKS, 9_000))
        val f = open.withFunctional(FunctionalRow.AUDIO, RowAnswer.NOT_TESTED, 2_000)
        assertSame(f, f.withFunctional(FunctionalRow.AUDIO, RowAnswer.NOT_TESTED, 9_000))
        val g = open.withGameTried(true, 2_000)
        assertSame(g, g.withGameTried(true, 9_000))
        // changing the answer is an event
        assertEquals(2, s.withGrade(GradeCategory.BATTERY, GradeAnswer.WEAK, 3_000).perAnswerAt.size)
    }

    @Test
    fun `every defect and functional row records under its own wire key, and a row not answered stays absent`() {
        var s = open
        for ((i, row) in FunctionalRow.values().withIndex()) s = s.withFunctional(row, RowAnswer.WORKS, 2_000L + i)
        for ((i, row) in DefectRow.values().withIndex()) s = s.withDefect(row, RowAnswer.FAULTY, 3_000L + i)
        assertEquals(FunctionalRow.values().map { it.wire } + DefectRow.values().map { it.wire }, s.perAnswerAt.map { it.item })
        assertTrue(s.grades.isEmpty())
        assertNull("nothing set it", s.gameTried)
    }

    // ---- the note ----

    @Test
    fun `the note's first edit is an answer event, later edits are not, and an unchanged text is nothing`() {
        val a = open.withNote("x", 2_000)
        assertEquals(listOf(AnswerEvent("observedNote", 2_000)), a.perAnswerAt)
        val b = a.withNote("xy", 2_500)
        assertEquals("one event for the note", 1, b.perAnswerAt.size)
        assertSame(b, b.withNote("xy", 3_000))
    }

    @Test
    fun `a note longer than the limit is refused whole - the limit is accepted, one over is not, and nothing is cut`() {
        val limit = TechnicianFormLimits.NOTE_MAX_CHARS
        assertEquals(200, limit)
        val exact = "ب".repeat(limit)
        val accepted = open.withNote(exact, 2_000)
        assertEquals(limit, accepted.observedNote.length)
        val tooLong = accepted.withNote(exact + "ب", 3_000)
        assertSame("the state is returned unchanged", accepted, tooLong)
        assertEquals(exact, tooLong.observedNote)
        // over the limit from a DIFFERENT current text: refused whole, the current note stays (a cut would replace it with the first 200 characters of the new text - the mutant a first version of this test missed)
        val short = open.withNote("ab", 2_000)
        val over = short.withNote("x".repeat(limit + 1), 3_000)
        assertSame(short, over)
        assertEquals("ab", over.observedNote)
    }

    // ---- the mandatory note, over every grade (rule 30) ----

    @Test
    fun `a grade under good makes the note mandatory - for every category and every answer`() {
        for (category in GradeCategory.values()) {
            for (answer in GradeAnswer.values()) {
                val s = open.withGrade(category, answer, 2_000)
                val expected = answer == GradeAnswer.FAIR || answer == GradeAnswer.WEAK
                assertEquals("$category=$answer", expected, s.noteRequired)
                assertEquals("$category=$answer", expected, answer.belowGood)
            }
        }
        assertFalse("nothing answered", open.noteRequired)
        val two = open.withGrade(GradeCategory.BATTERY, GradeAnswer.EXCELLENT, 2_000).withGrade(GradeCategory.OVERALL, GradeAnswer.WEAK, 2_100)
        assertTrue("one grade under good among others is enough", two.noteRequired)
    }

    // ---- symptoms ----

    @Test
    fun `symptoms toggle independently and none_noticed excludes them both ways`() {
        val one = open.toggleSymptom(Symptom.CHARGING_SLOW, 2_000).toggleSymptom(Symptom.BATTERY_SWOLLEN, 2_100)
        assertEquals(setOf(Symptom.CHARGING_SLOW, Symptom.BATTERY_SWOLLEN), one.symptoms)
        assertEquals(setOf(Symptom.BATTERY_SWOLLEN), one.toggleSymptom(Symptom.CHARGING_SLOW, 2_200).symptoms)
        val none = one.toggleSymptom(Symptom.NONE_NOTICED, 2_300)
        assertEquals("none_noticed clears the others", setOf(Symptom.NONE_NOTICED), none.symptoms)
        val again = none.toggleSymptom(Symptom.HOT_WHILE_CHARGING, 2_400)
        assertEquals("a symptom clears none_noticed", setOf(Symptom.HOT_WHILE_CHARGING), again.symptoms)
        assertEquals("toggling none_noticed off leaves nothing selected", emptySet<Symptom>(), none.toggleSymptom(Symptom.NONE_NOTICED, 2_500).symptoms)
        assertEquals("every toggle is an event under the symptoms key (four taps on the way to this state)", List(4) { "symptoms" }, again.perAnswerAt.map { it.item })
    }

    @Test
    fun `the known-fault chip is true or not answered - there is no no control so false is never produced`() {
        val on = open.toggleKnownFault(2_000)
        assertEquals(true, on.knownFaultBeforeScan)
        val off = on.toggleKnownFault(2_100)
        assertNull(off.knownFaultBeforeScan)
        assertEquals(listOf("known_fault_before_scan", "known_fault_before_scan"), off.perAnswerAt.map { it.item })
    }

    @Test
    fun `battery health - a percentage is 0 to 100, a state is one of three, not shown is an answer, and the empty field is none`() {
        assertEquals(0, BatteryHealth.Percent(0).value)
        assertEquals(100, BatteryHealth.Percent(100).value)
        for (bad in listOf(-1, 101, 1_000)) {
            try {
                BatteryHealth.Percent(bad)
                fail("$bad must be refused")
            } catch (_: IllegalArgumentException) {
            }
        }
        assertNull("unanswered", open.batteryHealth)
        for (answer in listOf(BatteryHealth.Percent(88)) + BatteryStateKey.values().map { BatteryHealth.State(it) } + BatteryHealth.NotShown) {
            val s = open.withBatteryHealth(answer, 2_000)
            assertEquals(answer, s.batteryHealth)
            assertEquals(listOf(AnswerEvent("batteryHealth", 2_000)), s.perAnswerAt)
        }
        val answered = open.withBatteryHealth(BatteryHealth.NotShown, 2_000)
        assertSame("the same answer again is nothing", answered, answered.withBatteryHealth(BatteryHealth.NotShown, 9_000))
    }

    // ---- the contradiction rule, over every pair (rule 30) ----

    @Test
    fun `the contradiction applies when overall is excellent or good and a category is weak - every overall x every category`() {
        for (overall in GradeAnswer.values().toList() + null) {
            for (weakOne in GradeCategory.values().filter { it != GradeCategory.OVERALL }) {
                for (other in GradeAnswer.values()) {
                    val grades = buildMap {
                        if (overall != null) put(GradeCategory.OVERALL, overall)
                        put(weakOne, GradeAnswer.WEAK)
                        // the other two categories
                        GradeCategory.values().filter { it != GradeCategory.OVERALL && it != weakOne }.forEach { put(it, other) }
                    }
                    val expected = overall == GradeAnswer.EXCELLENT || overall == GradeAnswer.GOOD
                    assertEquals("overall=$overall weak=$weakOne other=$other", expected, contradictionApplies(grades))
                }
            }
        }
    }

    @Test
    fun `no category weak means no contradiction, and a weak overall is not a category`() {
        for (overall in GradeAnswer.values()) {
            val grades = mapOf(GradeCategory.OVERALL to overall, GradeCategory.BATTERY to GradeAnswer.FAIR, GradeCategory.PERFORMANCE to GradeAnswer.GOOD, GradeCategory.THERMAL to GradeAnswer.NOT_TESTED)
            assertFalse("overall=$overall", contradictionApplies(grades))
        }
        assertFalse("overall unanswered", contradictionApplies(mapOf(GradeCategory.BATTERY to GradeAnswer.WEAK)))
        assertFalse(contradictionApplies(emptyMap()))
        assertFalse("not_tested is not weak", contradictionApplies(mapOf(GradeCategory.OVERALL to GradeAnswer.GOOD, GradeCategory.BATTERY to GradeAnswer.NOT_TESTED)))
    }

    // ---- the submit decision ----

    private fun answered() = open.withTechnician("tech-1").withGrade(GradeCategory.OVERALL, GradeAnswer.EXCELLENT, 2_000)

    @Test
    fun `the checks run in order - no technician, then a missing mandatory note, then nothing answered`() {
        // no technician wins even when the note is also missing
        val noTech = open.withGrade(GradeCategory.BATTERY, GradeAnswer.WEAK, 2_000)
        assertEquals(SubmitDecision.Blocked(SubmitBlock.NO_TECHNICIAN), noTech.decideSubmit())
        // a technician and a weak grade with no note
        assertEquals(SubmitDecision.Blocked(SubmitBlock.NOTE_REQUIRED), noTech.withTechnician("tech-1").decideSubmit())
        // blank counts as empty
        assertEquals(SubmitDecision.Blocked(SubmitBlock.NOTE_REQUIRED), noTech.withTechnician("tech-1").withNote("   ", 2_100).decideSubmit())
        // a technician and nothing answered
        assertEquals(SubmitDecision.Blocked(SubmitBlock.NO_ANSWERS), open.withTechnician("tech-1").decideSubmit())
        // the note given: sent
        assertTrue(noTech.withTechnician("tech-1").withNote("swollen", 2_100).decideSubmit() is SubmitDecision.Send)
        assertEquals("every SubmitBlock is reachable", 3, SubmitBlock.values().size)
    }

    @Test
    fun `the quality flags never block - four equal grades sent in a blink go out`() {
        var s = open.withTechnician("tech-1")
        for (c in GradeCategory.values()) s = s.withGrade(c, GradeAnswer.GOOD, 2_000)
        assertTrue("a straight line sent at once is sent", s.decideSubmit() is SubmitDecision.Send)
    }

    @Test
    fun `the contradiction prompt shows once - then the form goes out whatever the technician did with it`() {
        val s = open.withTechnician("tech-1")
            .withGrade(GradeCategory.BATTERY, GradeAnswer.WEAK, 2_000)
            .withGrade(GradeCategory.OVERALL, GradeAnswer.GOOD, 2_100)
            .withNote("swollen back", 2_200)
        val first = s.decideSubmit()
        assertTrue(first is SubmitDecision.ShowPrompt)
        val shownState = (first as SubmitDecision.ShowPrompt).state
        val shown = shownState.prompt as PromptState.Shown
        assertNull("no action yet", shown.action)
        assertEquals(
            "the four grades as they were, an unanswered one null",
            mapOf(GradeCategory.BATTERY to GradeAnswer.WEAK, GradeCategory.PERFORMANCE to null, GradeCategory.THERMAL to null, GradeCategory.OVERALL to GradeAnswer.GOOD),
            shown.answersBefore
        )
        // confirmed
        val confirmed = shownState.confirmPrompt()
        assertEquals(PromptAction.CONFIRMED, (confirmed.prompt as PromptState.Shown).action)
        assertTrue(confirmed.decideSubmit() is SubmitDecision.Send)
        // revised, the technician changes his mind about overall, and sends: the prompt does not come again
        val revised = shownState.revisePrompt().withGrade(GradeCategory.OVERALL, GradeAnswer.FAIR, 5_000)
        assertEquals(PromptAction.REVISE, (revised.prompt as PromptState.Shown).action)
        assertEquals("the snapshot before is not rewritten by the revision", shown.answersBefore, (revised.prompt as PromptState.Shown).answersBefore)
        assertTrue(revised.decideSubmit() is SubmitDecision.Send)
        // even a revised form that STILL contradicts is not asked again
        assertTrue(shownState.revisePrompt().decideSubmit() is SubmitDecision.Send)
    }

    @Test
    fun `confirming or revising with no prompt shown does nothing`() {
        val s = answered()
        assertSame(s, s.confirmPrompt())
        assertSame(s, s.revisePrompt())
    }

    @Test
    fun `no prompt when the rule does not apply`() {
        val s = answered().withGrade(GradeCategory.BATTERY, GradeAnswer.FAIR, 2_100).withNote("n", 2_200)
        assertTrue(s.decideSubmit() is SubmitDecision.Send)
        assertEquals(PromptState.NotShown, s.prompt)
    }
}
