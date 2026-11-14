package net.neuralcheck.presentation.screen

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import net.neuralcheck.domain.form.FormTechnician
import net.neuralcheck.domain.form.GradeAnswer
import net.neuralcheck.domain.form.GradeCategory
import net.neuralcheck.domain.form.SlotClosedReason
import net.neuralcheck.domain.form.Symptom
import net.neuralcheck.domain.form.SymptomOrder
import net.neuralcheck.domain.form.SymptomOrderSource
import net.neuralcheck.domain.form.TechnicianFormState
import net.neuralcheck.domain.form.openForm
import net.neuralcheck.domain.form.withGrade
import net.neuralcheck.presentation.theme.NeuralCheckTheme
import net.neuralcheck.presentation.viewmodel.TechnicianFormViewModel.ConfigUi
import net.neuralcheck.presentation.viewmodel.TechnicianFormViewModel.Submission
import net.neuralcheck.presentation.viewmodel.TechnicianFormViewModel.UiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The flip of [TECH_FORM_TEXTS_APPROVED] (2026-10-23), seen from the screens, with the app's REAL labels - [formLabelsOf] over the real resources, the real `technicianFormLabels()` and the real guard: nothing
 * between the resources and the pixels is replaced by the test (rules 37, 41). Its change REPLACES (rule 23) the tests that pinned the guard false: `TechnicianFormGuardTest` (`the guard is still false ...`) and the
 * unopened-gate cases listed in the commit message.
 *
 * Guard open x the shop WITHOUT the form part and WITH it:
 *  - with the part (the server answers a configuration with technicians): the form draws, each text the approved one;
 *  - without it (`ConfigUi.NotEnabled`): the surface draws NOTHING - no text, no control;
 *  - and the scanned phone (`ScannedPhoneScreen.parts`): the old evaluation card leaves EVERY scan screen as soon as the guard is open, whatever the shop has - so a shop without the form part has NO
 *    evaluation on either phone. That is a known effect of the one switch (PLAN-06:1239 «نعم = مسار تقييم واحد»), reported in the commit and in the report, and PINNED here: the day the card is kept for a
 *    shop that has no form, the last test below fails on purpose and is replaced in that change.
 * Not tested, and said so: what the screens look like on a phone (no run on a device).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "ar")
class FormLiveScreenShapeTest {

    @get:Rule(order = 0) val registerActivity = TechnicianFormSurfaceTest.RegisterComposeActivity()
    @get:Rule(order = 1) val rule = createAndroidComposeRule<ComponentActivity>()

    private val labels: FormLabels by lazy { formLabelsOf(ApplicationProvider.getApplicationContext<Application>().resources) }

    private fun approved(resource: String): String = ApprovedTextsTable.form.single { it.resource == resource }.ar

    private val technicians = listOf(FormTechnician("t1", "first"), FormTechnician("t2", "second"))
    private val order = SymptomOrder(Symptom.TWELVE, SymptomOrderSource.CONTRACT_ORDER)

    private fun ready(form: TechnicianFormState = openForm("sess-1", 1, Symptom.TWELVE, 1_000), promptOpen: Boolean = false) =
        UiState(config = ConfigUi.Ready(technicians, order), form = form, submission = Submission.Editing(), promptOpen = promptOpen)

    private var ui by mutableStateOf(UiState(config = ConfigUi.Loading))

    private fun show(first: UiState) {
        ui = first
        rule.setContent {
            NeuralCheckTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    TechnicianFormSurface(ui = ui, labels = labels, actions = TechnicianFormActions({ }, { _, _ -> }, { _, _ -> }, { _, _ -> }, { }, { }, { }, { }, { }, { }, { }, { }, { }))
                }
            }
        }
        rule.waitForIdle()
    }

    private fun update(next: UiState) {
        ui = next
        rule.waitForIdle()
    }

    @Test
    fun `the guard is open and the real labels exist - both read by the screens, not by the test`() {
        assertTrue("the form's texts are approved and written: the guard is open", TECH_FORM_TEXTS_APPROVED)
        var seen: FormLabels? = null
        rule.setContent { seen = technicianFormLabels() }
        rule.waitForIdle()
        assertNotNull("technicianFormLabels() gives the resources' labels once the guard is open", seen)
        assertEquals(approved("tf_title"), seen!!(FormText.Title))
    }

    @Test
    fun `a shop WITH the form part - the form draws, each text the approved one`() {
        show(ready())
        for (r in listOf("tf_title", "tf_who_judges", "tf_rule_1", "tf_rule_2", "tf_rule_3", "tf_grade_battery", "tf_grade_performance", "tf_grade_thermal", "tf_grade_overall", "tf_symptoms_header", "tf_known_fault", "tf_submit")) {
            rule.onNodeWithText(approved(r)).assertExists()
        }
        // the four categories x the answer chips: each level word is drawn on every category's row («ضعيف» is also the battery state's word, so one more)
        for (r in listOf("tf_level_excellent", "tf_level_good", "tf_level_fair", "tf_level_weak", "tf_level_not_tested")) {
            rule.onAllNodesWithText(approved(r)).assertCountEquals(GradeCategory.values().size + if (r == "tf_level_weak") 1 else 0)
        }
        // a chosen level shows its description under the row: the approved sentence with its example
        update(ready(openForm("sess-1", 1, Symptom.TWELVE, 1_000).withGrade(GradeCategory.THERMAL, GradeAnswer.GOOD, 2_000)))
        rule.onNodeWithText(approved("tf_desc_thermal_good")).assertExists()
    }

    @Test
    fun `the prompt shows its text whole - no placeholder is left on the screen`() {
        show(ready(promptOpen = true))
        rule.onNodeWithText(approved("tf_prompt_body")).assertExists()
        rule.onAllNodesWithText("%1\$s", substring = true).assertCountEquals(0)
        rule.onNodeWithText(approved("tf_prompt_confirm")).assertExists()
        rule.onNodeWithText(approved("tf_prompt_revise")).assertExists()
    }

    @Test
    fun `the configuration's states have their approved texts - loading, failed with the existing retry, no technicians, already sent`() {
        show(UiState(config = ConfigUi.Loading))
        rule.onNodeWithText(approved("tf_config_loading")).assertExists()
        update(UiState(config = ConfigUi.Failed))
        rule.onNodeWithText(approved("tf_config_failed")).assertExists()
        rule.onNodeWithText("إعادة المحاولة").assertExists()
        update(UiState(config = ConfigUi.NoTechnicians))
        rule.onNodeWithText(approved("tf_no_technicians")).assertExists()
        update(UiState(config = ConfigUi.SlotClosed(SlotClosedReason.SUBMITTED)))
        rule.onNodeWithText(approved("tf_submitted")).assertExists()
    }

    @Test
    fun `a shop WITHOUT the form part - the surface draws nothing at all, no text and no control`() {
        show(UiState(config = ConfigUi.NotEnabled))
        rule.onNodeWithTag(TECH_FORM_TAG).onChildren().assertCountEquals(0)
    }

    @Test
    fun `the scanned phone with the guard open - the old evaluation card is gone from every scan screen, a session's and a personal one's and one not yet known, and the sentence is a session's alone`() {
        for (started in listOf(true, false, null)) {
            val parts = ScannedPhoneScreen.parts(startedInSession = started, formLive = TECH_FORM_TEXTS_APPROVED)
            assertFalse("startedInSession=$started: the card leaves with the switch that lets the form in", parts.evaluationCard)
            assertEquals("the sentence is a session scan's", started == true, parts.inProgressSentence)
            assertEquals("the charts are a personal scan's", started == false, parts.charts)
            assertEquals("the live tiles are a personal scan's", started == false, parts.liveTiles)
        }
    }

    @Test
    fun `KNOWN EFFECT - a shop without the form part has no evaluation on either phone once the guard is open - the card is gone and the form draws nothing`() {
        // the scanned phone: no card, for any scan (the policy reads the guard, not the shop)
        assertFalse(ScannedPhoneScreen.parts(startedInSession = true, formLive = TECH_FORM_TEXTS_APPROVED).evaluationCard)
        // the technician's phone: the surface draws nothing for that shop
        show(UiState(config = ConfigUi.NotEnabled))
        rule.onNodeWithTag(TECH_FORM_TAG).onChildren().assertCountEquals(0)
        // Fails on purpose the day the card is kept for a shop that has no form (a decision for the user: the scanned phone does not know the shop's configuration today).
    }
}
