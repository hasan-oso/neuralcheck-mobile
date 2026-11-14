package net.neuralcheck.presentation.screen

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import net.neuralcheck.domain.form.BatteryHealth
import net.neuralcheck.domain.form.BatteryStateKey
import net.neuralcheck.domain.form.DefectRow
import net.neuralcheck.domain.form.FormTechnician
import net.neuralcheck.domain.form.FunctionalRow
import net.neuralcheck.domain.form.GradeAnswer
import net.neuralcheck.domain.form.GradeCategory
import net.neuralcheck.domain.form.RowAnswer
import net.neuralcheck.domain.form.SlotClosedReason
import net.neuralcheck.domain.form.SubmitBlock
import net.neuralcheck.domain.form.Symptom
import net.neuralcheck.domain.form.SymptomOrder
import net.neuralcheck.domain.form.SymptomOrderSource
import net.neuralcheck.domain.form.TechnicianFormState
import net.neuralcheck.domain.form.openForm
import net.neuralcheck.domain.form.withBatteryHealth
import net.neuralcheck.domain.form.withDefect
import net.neuralcheck.domain.form.withFunctional
import net.neuralcheck.domain.form.withGameTried
import net.neuralcheck.domain.form.withGrade
import net.neuralcheck.domain.form.withTechnician
import net.neuralcheck.presentation.theme.NeuralCheckTheme
import net.neuralcheck.presentation.viewmodel.TechnicianFormViewModel.ConfigUi
import net.neuralcheck.presentation.viewmodel.TechnicianFormViewModel.FormFailure
import net.neuralcheck.presentation.viewmodel.TechnicianFormViewModel.Submission
import net.neuralcheck.presentation.viewmodel.TechnicianFormViewModel.UiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The technician's form drawn - A-P6-08. The REAL surface composable in the app's theme; the labels are the TEST's own ([FormText.toString], one distinct text per slot) so that each control and each
 * slot is found by what it is, not by its wording (the real labels, the approved texts, are drawn by `FormLiveScreenShapeTest`; the wording is pinned by `FormRepeatApprovedTextsTest`). What is asserted: each control reports its own tap and nothing else, the selected one is selected, every control is disabled
 * while a call is out, after the server accepted and behind the contradiction prompt, the states of the configuration and the blocked / failed lines show, and - the one that keeps the approval list honest -
 * that the slots the surface asks for are EXACTLY [FormTexts.all] (no text the list misses, no entry in the list the surface never uses). Rule 30: every grade x every answer, every row x every answer.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class TechnicianFormSurfaceTest {

    /** As `ManualUnavailableLineLayoutTest.RegisterComposeActivity`. */
    class RegisterComposeActivity : TestWatcher() {
        override fun starting(description: Description) {
            val app = ApplicationProvider.getApplicationContext<Application>()
            shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app.packageName, ComponentActivity::class.java.name))
        }
    }

    @get:Rule(order = 0) val registerActivity = RegisterComposeActivity()
    @get:Rule(order = 1) val rule = createAndroidComposeRule<ComponentActivity>()

    private val requested = linkedSetOf<FormText>()
    private val labels: FormLabels = { requested.add(it); it.toString() }
    private val calls = mutableListOf<String>()

    private val actions = TechnicianFormActions(
        chooseTechnician = { calls += "technician:$it" },
        grade = { c, a -> calls += "grade:${c.wire}:${a.wire}" },
        functional = { r, a -> calls += "functional:${r.wire}:${a.wire}" },
        defect = { r, a -> calls += "defect:${r.wire}:${a.wire}" },
        toggleSymptom = { calls += "symptom:${it.wire}" },
        toggleKnownFault = { calls += "knownFault" },
        batteryHealth = { calls += "battery:$it" },
        gameTried = { calls += "game:$it" },
        note = { calls += "note:$it" },
        submit = { calls += "submit" },
        confirmPrompt = { calls += "promptConfirm" },
        revisePrompt = { calls += "promptRevise" },
        retryConfig = { calls += "retryConfig" }
    )

    private val technicians = listOf(FormTechnician("t1", "first"), FormTechnician("t2", "second"))
    private val order = SymptomOrder(Symptom.TWELVE, SymptomOrderSource.CONTRACT_ORDER)

    private fun editing(form: TechnicianFormState = openForm("sess-1", 1, Symptom.TWELVE, 1_000), submission: Submission = Submission.Editing(), blocked: SubmitBlock? = null, promptOpen: Boolean = false) =
        UiState(config = ConfigUi.Ready(technicians, order), form = form, submission = submission, blocked = blocked, promptOpen = promptOpen)

    private var ui by mutableStateOf(editing())

    private fun show() {
        // the screen scrolls the form (GenerateSessionScreen); the test scrolls it too, so a control below the fold is reachable
        rule.setContent { NeuralCheckTheme { Column(Modifier.verticalScroll(rememberScrollState())) { TechnicianFormSurface(ui = ui, labels = labels, actions = actions) } } }
        rule.waitForIdle()
    }

    private fun update(next: UiState) {
        ui = next
        rule.waitForIdle()
    }

    private fun tag(vararg parts: String) = rule.onNodeWithTag(formTag(*parts))

    private fun click(vararg parts: String) {
        tag(*parts).performScrollTo().performClick()
    }

    @Test
    fun `every control reports its own tap - every grade x answer, every row x answer, symptoms, battery, game, technicians`() {
        show()
        for (c in GradeCategory.values()) for (a in GradeAnswer.values()) click("grade", c.wire, a.wire)
        for (r in FunctionalRow.values()) for (a in RowAnswer.values()) click("functional_${r.wire}", a.wire)
        for (r in DefectRow.values()) for (a in RowAnswer.values()) click("defect_${r.wire}", a.wire)
        for (s in Symptom.values()) click("symptom", s.wire)
        click("knownFault")
        for (k in BatteryStateKey.values()) click("batteryState", k.wire)
        click("batteryNotShown")
        click("game", "yes"); click("game", "no")
        for (t in technicians) click("technician", t.id)
        click("submit")
        val expected = buildList {
            for (c in GradeCategory.values()) for (a in GradeAnswer.values()) add("grade:${c.wire}:${a.wire}")
            for (r in FunctionalRow.values()) for (a in RowAnswer.values()) add("functional:${r.wire}:${a.wire}")
            for (r in DefectRow.values()) for (a in RowAnswer.values()) add("defect:${r.wire}:${a.wire}")
            for (s in Symptom.values()) add("symptom:${s.wire}")
            add("knownFault")
            for (k in BatteryStateKey.values()) add("battery:${BatteryHealth.State(k)}")
            add("battery:${BatteryHealth.NotShown}")
            add("game:true"); add("game:false")
            for (t in technicians) add("technician:${t.id}")
            add("submit")
        }
        assertEquals(expected, calls)
    }

    @Test
    fun `the percentage field reports a number from 0 to 100, ignores letters and a number over 100, and clears only its own answer`() {
        show()
        tag("batteryPercent").performScrollTo().performTextInput("92")
        assertEquals(listOf("battery:${BatteryHealth.Percent(92)}"), calls)
        calls.clear()
        tag("batteryPercent").performTextInput("abc")
        assertEquals("letters are not a percentage and clear nothing the field did not hold", emptyList<String>(), calls)
        tag("batteryPercent").performTextInput("250")
        assertEquals("over 100 is not reported", emptyList<String>(), calls)
        // the field holding a percentage and then emptied: back to not answered
        ui = editing(openForm("sess-1", 1, Symptom.TWELVE, 1_000).withTechnician("t1").withBatteryHealth(BatteryHealth.Percent(40), 2_000))
        rule.waitForIdle()
        tag("batteryPercent").performTextClearance()
        assertEquals(listOf("battery:null"), calls)
    }

    @Test
    fun `the note reports its text`() {
        show()
        tag("note").performScrollTo().performTextInput("abc")
        assertTrue(calls.toString(), calls.any { it.startsWith("note:") && it.endsWith("abc") })
    }

    @Test
    fun `what the form holds is what is selected - grades, rows, defects, symptoms, battery, game, technician`() {
        val form = openForm("sess-1", 1, Symptom.TWELVE, 1_000).withTechnician("t2")
            .withGrade(GradeCategory.BATTERY, GradeAnswer.FAIR, 2_000).withGrade(GradeCategory.THERMAL, GradeAnswer.NOT_TESTED, 2_100)
            .withFunctional(FunctionalRow.AUDIO, RowAnswer.FAULTY, 2_200).withDefect(DefectRow.PRIOR_REPAIR, RowAnswer.WORKS, 2_300)
            .withBatteryHealth(BatteryHealth.State(BatteryStateKey.BAD), 2_400).withGameTried(false, 2_500)
        ui = editing(form)
        show()
        tag("technician", "t2").assertIsSelected(); tag("technician", "t1").assertIsNotSelected()
        tag("grade", "battery", "fair").assertIsSelected(); tag("grade", "battery", "good").assertIsNotSelected()
        tag("grade", "thermal", "not_tested").assertIsSelected()
        tag("grade", "performance", "excellent").assertIsNotSelected()
        tag("functional_audio", "faulty").assertIsSelected(); tag("functional_audio", "works").assertIsNotSelected()
        tag("defect_prior_repair", "works").assertIsSelected()
        tag("batteryState", "bad").assertIsSelected(); tag("batteryState", "normal").assertIsNotSelected()
        tag("game", "no").assertIsSelected(); tag("game", "yes").assertIsNotSelected()
    }

    @Test
    fun `while the call is out, after the server accepted, and behind the prompt every control is disabled - and only the submitted state says submitted`() {
        show()
        tag("grade", "battery", "good").assertIsEnabled()
        tag("submit").assertIsEnabled()
        update(editing(submission = Submission.Sending))
        tag("grade", "battery", "good").assertIsNotEnabled()
        tag("technician", "t1").assertIsNotEnabled()
        tag("note").assertIsNotEnabled()
        tag("submit").assertDoesNotExist()
        tag("submitted").assertDoesNotExist()
        update(editing(submission = Submission.Submitted))
        tag("grade", "battery", "good").assertIsNotEnabled()
        tag("submitted").assertExists()
        tag("submit").assertDoesNotExist()
        update(editing(promptOpen = true))
        tag("grade", "battery", "good").assertIsNotEnabled()
        tag("prompt").assertExists()
    }

    @Test
    fun `the prompt's two buttons report their own tap`() {
        ui = editing(promptOpen = true)
        show()
        tag("promptConfirm").performClick()
        tag("promptRevise").performClick()
        assertEquals(listOf("promptConfirm", "promptRevise"), calls)
    }

    @Test
    fun `the blocked reason and the failure of the last send show, each its own, and the configuration states have their screens`() {
        show()
        for (b in SubmitBlock.values()) {
            update(editing(blocked = b))
            tag("blocked").assertExists()
        }
        update(editing())
        tag("blocked").assertDoesNotExist()
        tag("failure").assertDoesNotExist()
        for (f in listOf(FormFailure.SameTechnician, FormFailure.Refused("FORM_X"), FormFailure.NotSent, FormFailure.Unbuildable)) {
            update(editing(submission = Submission.Editing(f)))
            tag("failure").assertExists()
        }
        update(UiState(config = ConfigUi.Loading))
        tag("grade", "battery", "good").assertDoesNotExist()
        update(UiState(config = ConfigUi.Failed))
        click("retryConfig")
        assertEquals(listOf("retryConfig"), calls)
        update(UiState(config = ConfigUi.NoTechnicians))
        tag("submit").assertDoesNotExist()
        // A-P6-08 wire match: an answer that cannot be read is a failed load on the screen (the same two existing texts, the retry button asks again)
        calls.clear()
        update(UiState(config = ConfigUi.Unreadable("slots_absent")))
        click("retryConfig")
        assertEquals(listOf("retryConfig"), calls)
        tag("submit").assertDoesNotExist()
        // a slot already sent shows the existing "sent" text under its own tag, and no form
        update(UiState(config = ConfigUi.SlotClosed(SlotClosedReason.SUBMITTED)))
        tag("slotSubmitted").assertExists()
        tag("submit").assertDoesNotExist()
        update(UiState(config = ConfigUi.SlotClosed(SlotClosedReason.NOT_DRAWN)))
        tag("slotSubmitted").assertDoesNotExist()
        tag("submit").assertDoesNotExist()
    }

    @Test
    fun `the second form of the double evaluation says another technician is needed and to hand the phone over - the first form says neither - and the second is a form with nobody chosen`() {
        show()
        tag("secondEval").assertDoesNotExist()
        tag("handOver").assertDoesNotExist()
        update(editing(openForm("sess-1", 2, Symptom.TWELVE, 1_000)))
        tag("secondEval").assertExists()
        tag("handOver").assertExists()
        assertTrue(FormText.SecondEvalNeeded in requested && FormText.HandOverDevice in requested)
        // the chooser is the same «من يقيّم؟» and nobody is chosen on a new form
        tag("technician", "t1").assertIsNotSelected()
        tag("technician", "t2").assertIsNotSelected()
        click("technician", "t2")
        assertEquals(listOf("technician:t2"), calls)
        // and back to a first form: the two lines go
        update(editing(openForm("sess-2", 1, Symptom.TWELVE, 1_000)))
        tag("secondEval").assertDoesNotExist()
        tag("handOver").assertDoesNotExist()
    }

    @Test
    fun `a shop with the form off draws nothing - no form, no retry, and no text asked for - it is not the no-technicians screen`() {
        show()
        update(UiState(config = ConfigUi.Loading))
        val asked = requested.toSet()
        update(UiState(config = ConfigUi.NotEnabled))
        assertEquals("not enabled asks for no text at all", asked, requested.toSet())
        tag("submit").assertDoesNotExist()
        tag("retryConfig").assertDoesNotExist()
        tag("slotSubmitted").assertDoesNotExist()
        // while no-technicians is a screen with its text
        update(UiState(config = ConfigUi.NoTechnicians))
        assertTrue(FormText.NoTechnicians in requested)
    }

    @Test
    fun `the slots the surface asks for are exactly the list that needs approval - no text missing from it, none in it that nothing shows`() {
        show()
        // every level selected for every category in turn: the 16 descriptions
        for (level in GradeAnswer.values().filter { it.isLevel }) {
            var form = openForm("sess-1", 1, Symptom.TWELVE, 1_000)
            for (c in GradeCategory.values()) form = form.withGrade(c, level, 2_000)
            update(editing(form))
        }
        for (b in SubmitBlock.values()) update(editing(blocked = b))
        for (f in listOf(FormFailure.SameTechnician, FormFailure.Refused("x"), FormFailure.NotSent)) update(editing(submission = Submission.Editing(f)))
        update(editing(submission = Submission.Sending))
        update(editing(submission = Submission.Submitted))
        update(editing(promptOpen = true))
        update(UiState(config = ConfigUi.Loading))
        update(UiState(config = ConfigUi.Failed))
        update(UiState(config = ConfigUi.NoTechnicians))
        update(UiState(config = ConfigUi.Unreadable("slots_absent")))
        update(UiState(config = ConfigUi.NotEnabled))
        update(UiState(config = ConfigUi.SlotClosed(SlotClosedReason.SUBMITTED)))
        update(UiState(config = ConfigUi.SlotClosed(SlotClosedReason.NOT_DRAWN)))
        // A-P6-10 (the list gained the two lines of row ف٩): the second form of the double evaluation asks for them
        update(editing(openForm("sess-1", 2, Symptom.TWELVE, 1_000)))
        val listed = FormTexts.all.toSet()
        assertEquals("every slot is distinct", FormTexts.all.size, listed.size)
        // A-P6-09 (replaces "the slots the surface asks for are EXACTLY the list", rule 23): the list also holds the one text the scanned phone's SCAN screen draws (FormText.ScanInProgress), which this
        // surface never asks for - so the surface's slots are the list less FormTexts.drawnByScannedPhone, and that set is named, not implied.
        val surfaceSlots = listed - FormTexts.drawnByScannedPhone
        assertEquals("asked for but not in the list: ${requested - listed}", emptySet<FormText>(), requested - listed)
        assertEquals("in the list but never asked for: ${surfaceSlots - requested}", emptySet<FormText>(), surfaceSlots - requested)
        assertEquals("the scanned phone's slots are not the surface's: ${requested.intersect(FormTexts.drawnByScannedPhone)}", emptySet<FormText>(), requested.intersect(FormTexts.drawnByScannedPhone))
    }
}
