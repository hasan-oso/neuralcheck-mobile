package net.neuralcheck.presentation.screen

import android.app.Application
import android.content.Context
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
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import net.neuralcheck.domain.repeat.CoolingDecision
import net.neuralcheck.domain.repeat.CoolingSide
import net.neuralcheck.domain.repeat.RepeatRefusal
import net.neuralcheck.domain.repeat.TempRead
import net.neuralcheck.presentation.theme.NeuralCheckTheme
import net.neuralcheck.presentation.viewmodel.RepeatStartUi
import net.neuralcheck.presentation.viewmodel.ScannedRepeatUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The flip of [REPEAT_TEXTS_APPROVED] (2026-10-23), seen from the screens, with the app's REAL labels - [repeatLabelsOf] over the real resources, the real `repeatLabels()` and the real guard: nothing between the
 * resources and the pixels is replaced by the test (rules 37, 41). REPLACES (rule 23) the guard-false pins listed in the commit message.
 *
 * What is asserted, in Arabic AND in Turkish (rule 30: every text, not one): the scanned phone's cooling screen draws the approved title, the ready button for a phone within the degree, the warning with «ابدأ رغم ذلك»
 * and «انتظر» for one that is not, the unreadable-temperature text with no button; every refusal with a text of its own and the general failure draw THEIR text under the controls; and the technician's notice is
 * Arabic under BOTH locales (it has no Turkish entry, on purpose: the technician's interface is Arabic only).
 * The shop's side: whether the shop has the repeat part is the SERVER's answer (an offer of `NOT_ELIGIBLE` / `NOT_SELECTED` draws no notice and no cooling screen: the screens are called only for a
 * `RepeatNoticeUi.Notice` and a `ScannedRepeatUi.Cooling`, pinned as source shape by `RepeatScanGuardTest`), so the surfaces below are what a shop WITH the part gets.
 * Not tested, and said so: what the screens look like on a phone (no run on a device).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "ar")
class RepeatLiveScreenShapeTest {

    @get:Rule(order = 0) val registerActivity = RepeatScanSurfaceTest.RegisterComposeActivity()
    @get:Rule(order = 1) val rule = createAndroidComposeRule<ComponentActivity>()

    private val resources get() = ApplicationProvider.getApplicationContext<Application>().resources
    private val labels: RepeatLabels by lazy { repeatLabelsOf(resources) }

    private fun entry(resource: String) = ApprovedTextsTable.repeat.single { it.resource == resource }

    /** What the screen must say in [tr] language: the Turkish of a scanned phone's text, the Arabic of the technician's. */
    private fun text(resource: String, tr: Boolean): String = entry(resource).let { if (tr && it.tr != null) it.tr else it.ar }

    private fun cooling(decision: CoolingDecision?, waitChosen: Boolean = false, start: RepeatStartUi = RepeatStartUi.Idle) =
        ScannedRepeatUi.Cooling(referenceTenths = 328, decision = decision, waitChosen = waitChosen, start = start)

    private var ui by mutableStateOf(cooling(null))

    private val noActions = CoolingActions(onTemperature = { }, startReady = { }, startAnyway = { }, wait = { })
    private val readTemperature: (Context) -> TempRead = { TempRead.Present(333) }

    private fun showCooling(first: ScannedRepeatUi.Cooling) {
        ui = first
        rule.setContent {
            NeuralCheckTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    CoolingWaitSection(ui = ui, labels = labels, actions = noActions, readTemperature = readTemperature, pollMs = 3_600_000L)
                }
            }
        }
        rule.waitForIdle()
    }

    private fun update(next: ScannedRepeatUi.Cooling) {
        ui = next
        rule.waitForIdle()
    }

    // ---- the guard and the labels ----

    @Test
    fun `the guard is open and the real labels exist - both read by the screens, not by the test`() {
        assertTrue("the repeat screens' texts are approved and written: the guard is open", REPEAT_TEXTS_APPROVED)
        var seen: RepeatLabels? = null
        rule.setContent { seen = repeatLabels() }
        rule.waitForIdle()
        assertNotNull("repeatLabels() gives the resources' labels once the guard is open", seen)
        assertEquals(text("rp_wait_title", tr = false), seen!!(RepeatText.WaitTitle))
    }

    // ---- the scanned phone's cooling screen ----

    private fun checkCooling(tr: Boolean) {
        showCooling(cooling(null))
        rule.onNodeWithText(text("rp_wait_title", tr)).assertExists()
        // a phone within the degree: the ready button and nothing of the warning
        update(cooling(CoolingDecision.Ready))
        rule.onNodeWithText(text("rp_ready", tr)).assertExists()
        rule.onAllNodesWithText(text("rp_start_anyway", tr)).assertCountEquals(0)
        // a phone not within the degree, above or below the band: the warning, start anyway and wait
        for (side in CoolingSide.values()) {
            update(cooling(CoolingDecision.NotCooled(side, 0.4f)))
            rule.onNodeWithText(text("rp_not_cooled_warning", tr)).assertExists()
            rule.onNodeWithText(text("rp_start_anyway", tr)).assertExists()
            rule.onNodeWithText(text("rp_wait", tr)).assertExists()
            rule.onAllNodesWithText(text("rp_ready", tr)).assertCountEquals(0)
        }
        // after «انتظر» the warning and the two buttons go and the bar stays
        update(cooling(CoolingDecision.NotCooled(CoolingSide.ABOVE, 0.4f), waitChosen = true))
        rule.onAllNodesWithText(text("rp_not_cooled_warning", tr)).assertCountEquals(0)
        // an unreadable temperature: its text, and no way to start
        update(cooling(CoolingDecision.TemperatureUnreadable("no_temperature_extra")))
        rule.onNodeWithText(text("rp_temperature_unreadable", tr)).assertExists()
        rule.onAllNodesWithText(text("rp_ready", tr)).assertCountEquals(0)
        rule.onAllNodesWithText(text("rp_start_anyway", tr)).assertCountEquals(0)
    }

    @Test
    @Config(qualifiers = "ar")
    fun `the cooling screen draws the approved texts in Arabic - the title, ready, the warning with its two buttons, the unreadable temperature`() = checkCooling(tr = false)

    @Test
    @Config(qualifiers = "tr")
    fun `the cooling screen draws the approved texts in Turkish - the title, ready, the warning with its two buttons, the unreadable temperature`() = checkCooling(tr = true)

    private fun checkFailures(tr: Boolean) {
        showCooling(cooling(CoolingDecision.Ready))
        for (refusal in RepeatTexts.refusalsWithText) {
            val name = resources.getResourceEntryName(RepeatText.StartRefused(refusal).resource())
            update(cooling(CoolingDecision.Ready, start = RepeatStartUi.Failed(refusal, refusal.wire)))
            rule.onNodeWithTag(repeatTag("failure")).assertExists()
            rule.onNodeWithText(text(name, tr)).assertExists()
        }
        // every other failure of the start - a disconnect, an unreadable answer, a refusal that has no text of its own - is the general one
        for (failed in listOf(RepeatStartUi.Failed(null, "not_sent"), RepeatStartUi.Failed(RepeatRefusal.PAIR_NOT_FOUND, RepeatRefusal.PAIR_NOT_FOUND.wire), RepeatStartUi.Failed(RepeatRefusal.NOT_AUTHENTICATED, "x"))) {
            update(cooling(CoolingDecision.Ready, start = failed))
            rule.onNodeWithText(text("rp_start_not_sent", tr)).assertExists()
        }
    }

    @Test
    @Config(qualifiers = "ar")
    fun `a failed start shows the text of its own refusal, or the general one - in Arabic`() = checkFailures(tr = false)

    @Test
    @Config(qualifiers = "tr")
    fun `a failed start shows the text of its own refusal, or the general one - in Turkish`() = checkFailures(tr = true)

    // ---- the technician's notice: Arabic under both locales ----

    private fun checkNotice(tr: Boolean) {
        rule.setContent { NeuralCheckTheme { RepeatNoticeSection(labels = labels, onNotNow = { }) } }
        rule.waitForIdle()
        rule.onNodeWithText(text("rp_notice_body", tr)).assertExists()
        rule.onNodeWithText(text("rp_not_now", tr)).assertExists()
        // the technician's two texts have no Turkish: the Arabic shows under the Turkish locale too
        assertEquals(entry("rp_notice_body").ar, text("rp_notice_body", tr))
        assertEquals(entry("rp_not_now").ar, text("rp_not_now", tr))
    }

    @Test
    @Config(qualifiers = "ar")
    fun `the technician's notice is Arabic`() = checkNotice(tr = false)

    @Test
    @Config(qualifiers = "tr")
    fun `the technician's notice is still Arabic under the Turkish locale - it has no Turkish entry on purpose`() = checkNotice(tr = true)
}
