package net.neuralcheck.presentation.screen

import android.app.Application
import android.content.ComponentName
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import net.neuralcheck.domain.repeat.CoolingDecision
import net.neuralcheck.domain.repeat.CoolingSide
import net.neuralcheck.domain.repeat.RepeatRefusal
import net.neuralcheck.domain.repeat.TempRead
import net.neuralcheck.presentation.theme.NeuralCheckTheme
import net.neuralcheck.presentation.viewmodel.RepeatStartUi
import net.neuralcheck.presentation.viewmodel.ScannedRepeatUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
 * The repeat scan's two surfaces drawn - A-P6-07. The REAL composables in the app's theme; the labels are the TEST's own ([RepeatText.toString], one distinct text per slot) so that each control and each slot
 * is found by what it is, not by its wording (the real labels, the approved texts, in both languages, are drawn by `RepeatLiveScreenShapeTest`; the wording is pinned by `FormRepeatApprovedTextsTest`). What is asserted: each control reports its own tap and nothing else; the ready button exists ONLY for a phone within the degree, and the
 * warning with «ابدأ رغم ذلك» and «انتظر» only for one that is not (and only until «انتظر»); an unreadable temperature shows its text and NO start button; no temperature is written anywhere (every text on the
 * screen is a label - the bar has no number); every control is disabled while the start is out; a failed start shows its text; the phone's own temperature is read and reported while the screen is composed; and
 * the slots the surfaces ask for are EXACTLY [RepeatTexts.all] (no text the list misses, no entry the surfaces never use).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class RepeatScanSurfaceTest {

    /** As `TechnicianFormSurfaceTest.RegisterComposeActivity`. */
    class RegisterComposeActivity : TestWatcher() {
        override fun starting(description: Description) {
            val app = ApplicationProvider.getApplicationContext<Application>()
            shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app.packageName, ComponentActivity::class.java.name))
        }
    }

    @get:Rule(order = 0) val registerActivity = RegisterComposeActivity()
    @get:Rule(order = 1) val rule = createAndroidComposeRule<ComponentActivity>()

    private val requested = linkedSetOf<RepeatText>()
    private val labels: RepeatLabels = { requested.add(it); it.toString() }
    private val calls = mutableListOf<String>()
    private val reads = mutableListOf<TempRead>()

    private val actions = CoolingActions(
        onTemperature = { reads += it },
        startReady = { calls += "ready" },
        startAnyway = { calls += "anyway" },
        wait = { calls += "wait" }
    )

    private fun cooling(decision: CoolingDecision?, waitChosen: Boolean = false, start: RepeatStartUi = RepeatStartUi.Idle) =
        ScannedRepeatUi.Cooling(referenceTenths = 328, decision = decision, waitChosen = waitChosen, start = start)

    private var ui by mutableStateOf(cooling(null))

    /** The phone's temperature as this test reads it (the real read is `BatteryTemperatureTest`'s); the poll is far longer than the test, so the read happens once per composition. */
    private val readTemperature: (Context) -> TempRead = { TempRead.Present(333) }

    private fun showCooling() {
        rule.setContent {
            NeuralCheckTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    CoolingWaitSection(ui = ui, labels = labels, actions = actions, readTemperature = readTemperature, pollMs = 3_600_000L)
                }
            }
        }
        rule.waitForIdle()
    }

    private fun update(next: ScannedRepeatUi.Cooling) {
        ui = next
        rule.waitForIdle()
    }

    private fun tag(vararg parts: String) = rule.onNodeWithTag(repeatTag(*parts))

    private fun exists(vararg parts: String): Boolean = rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.TestTag).and(SemanticsMatcher("tag ${repeatTag(*parts)}") {
        it.config.getOrNull(SemanticsProperties.TestTag) == repeatTag(*parts)
    })).fetchSemanticsNodes().isNotEmpty()

    // ---- the technician's notice ----

    @Test
    fun `the notice draws its sentence and the not-now button - the button reports its tap and nothing else`() {
        rule.setContent { NeuralCheckTheme { RepeatNoticeSection(labels = labels, onNotNow = { calls += "notNow" }) } }
        rule.waitForIdle()
        tag("notice").assertIsEnabled()
        assertEquals(setOf<RepeatText>(RepeatText.NoticeBody, RepeatText.NotNow), requested)
        tag("notNow").performClick()
        assertEquals(listOf("notNow"), calls)
    }

    // ---- the scanned phone's cooling screen ----

    @Test
    fun `before the first reading there is a bar and no button - nothing can start`() {
        showCooling()
        assertTrue(exists("bar", "unknown"))
        for (t in listOf("ready", "anyway", "wait", "warning", "unreadable", "failure")) assertFalse(t, exists(t))
        assertEquals(setOf<RepeatText>(RepeatText.WaitTitle), requested)
    }

    @Test
    fun `a phone within the degree has the ready button and nothing else - the warning and the other two buttons are not there`() {
        ui = cooling(CoolingDecision.Ready)
        showCooling()
        tag("ready").assertIsEnabled().performClick()
        assertEquals(listOf("ready"), calls)
        for (t in listOf("anyway", "wait", "warning", "unreadable")) assertFalse(t, exists(t))
        assertEquals(setOf<RepeatText>(RepeatText.WaitTitle, RepeatText.Ready), requested)
    }

    @Test
    fun `a phone not within the degree has the bar, the warning, start anyway and wait - and no ready button, above or below the band`() {
        ui = cooling(null)
        showCooling()
        for (side in CoolingSide.values()) {
            calls.clear()
            update(cooling(CoolingDecision.NotCooled(side, 0.4f)))
            assertTrue("$side", exists("bar", "progress"))
            assertFalse("$side: no ready", exists("ready"))
            tag("warning").assertIsEnabled()
            tag("anyway").assertIsEnabled().performClick()
            tag("wait").assertIsEnabled().performClick()
            assertEquals("$side", listOf("anyway", "wait"), calls)
        }
    }

    @Test
    fun `after wait the warning and the two buttons go - the bar stays, and when the phone is within the degree the ready button comes`() {
        ui = cooling(CoolingDecision.NotCooled(CoolingSide.ABOVE, 0.4f), waitChosen = true)
        showCooling()
        assertTrue(exists("bar", "progress"))
        for (t in listOf("warning", "anyway", "wait", "ready")) assertFalse(t, exists(t))
        update(cooling(CoolingDecision.Ready, waitChosen = true))
        tag("ready").assertIsEnabled()
        assertFalse(exists("bar", "progress"))
    }

    @Test
    fun `an unreadable temperature shows its text and no start button of either kind`() {
        ui = cooling(CoolingDecision.TemperatureUnreadable("no_battery_intent"))
        showCooling()
        tag("unreadable").assertIsEnabled()
        for (t in listOf("ready", "anyway", "wait", "warning")) assertFalse(t, exists(t))
        assertFalse("no bar either: there is no reading to move it", exists("bar", "progress") || exists("bar", "unknown"))
        assertEquals(setOf<RepeatText>(RepeatText.WaitTitle, RepeatText.TemperatureUnreadable), requested)
    }

    @Test
    fun `every control is disabled while the start is out`() {
        ui = cooling(CoolingDecision.NotCooled(CoolingSide.ABOVE, 0.2f), start = RepeatStartUi.InFlight)
        showCooling()
        tag("anyway").assertIsNotEnabled()
        tag("wait").assertIsNotEnabled()
        update(cooling(CoolingDecision.Ready, start = RepeatStartUi.InFlight))
        tag("ready").assertIsNotEnabled()
    }

    @Test
    fun `a failed start shows its text under the controls - the refusal's own when it has one, the general one otherwise`() {
        ui = cooling(CoolingDecision.Ready, start = RepeatStartUi.Failed(RepeatRefusal.ALREADY_STARTED, "REPEAT_ALREADY_STARTED"))
        showCooling()
        tag("failure").assertIsEnabled()
        assertTrue(requested.toString(), RepeatText.StartRefused(RepeatRefusal.ALREADY_STARTED) in requested)
        tag("ready").assertIsEnabled()
        update(cooling(CoolingDecision.Ready, start = RepeatStartUi.Failed(null, "not_sent")))
        assertTrue(requested.toString(), RepeatText.StartNotSent in requested)
        // every refusal of the contract maps to a slot: the seven the server's builder wrote a text for have their own, the rest the general one
        for (r in RepeatRefusal.values()) {
            val text = RepeatTexts.forFailure(RepeatStartUi.Failed(r, r.wire))
            assertEquals(r.name, if (r in RepeatTexts.refusalsWithText) RepeatText.StartRefused(r) else RepeatText.StartNotSent, text)
        }
        assertEquals(7, RepeatTexts.refusalsWithText.size)
    }

    @Test
    fun `while it is composed the cooling screen reads the phone's temperature and reports it - and no number is ever written on it`() {
        ui = cooling(CoolingDecision.NotCooled(CoolingSide.ABOVE, 0.6f))
        showCooling()
        assertEquals("read once on entering, with the poll far away", listOf<TempRead>(TempRead.Present(333)), reads)
        val texts = rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text)).fetchSemanticsNodes().flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty() }.map { it.text }
        assertTrue(texts.toString(), texts.isNotEmpty())
        val allowed = requested.map { it.toString() }.toSet()
        for (t in texts) assertTrue("«$t» is not one of the labels", t in allowed)
        assertTrue("no digit on the screen: $texts", texts.none { s -> s.any { it.isDigit() } })
    }

    @Test
    fun `the slots the surfaces ask for are exactly the list of texts - no text the list misses and no entry the surfaces never use`() {
        rule.setContent {
            NeuralCheckTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    RepeatNoticeSection(labels = labels, onNotNow = {})
                    for (decision in listOf(null, CoolingDecision.Ready, CoolingDecision.NotCooled(CoolingSide.ABOVE, 0.5f), CoolingDecision.TemperatureUnreadable("x"))) {
                        CoolingWaitSection(ui = cooling(decision), labels = labels, actions = actions, readTemperature = readTemperature, pollMs = 3_600_000L)
                    }
                    for (failure in RepeatTexts.refusalsWithText.map { RepeatStartUi.Failed(it, it.wire) } + RepeatStartUi.Failed(null, "x")) {
                        CoolingWaitSection(ui = cooling(CoolingDecision.Ready, start = failure), labels = labels, actions = actions, readTemperature = readTemperature, pollMs = 3_600_000L)
                    }
                }
            }
        }
        rule.waitForIdle()
        assertEquals(RepeatTexts.all.toSet(), requested)
        assertEquals("no entry twice", RepeatTexts.all.size, RepeatTexts.all.toSet().size)
    }
}
