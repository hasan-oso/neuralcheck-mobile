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
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import net.neuralcheck.presentation.theme.NeuralCheckTheme
import net.neuralcheck.presentation.viewmodel.ConsentFailure
import net.neuralcheck.presentation.viewmodel.ConsentUi
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
 * The consent screen's surface drawn - A-P6-12. The REAL composable in the app's theme; the labels are the TEST's own ([ConsentText.toString], one distinct text per slot) so that each slot is found by what it is,
 * not by its wording (the real labels, the approved texts of version 2 in both languages, are drawn by `ConsentLiveScreenShapeTest`; the wording is pinned by `ConsentApprovedTextsTest`). What is asserted: each button reports its own tap and nothing else; both are disabled while a call is out and once the
 * screen is finished; a bar is drawn only while a call is out; each failed recording shows ITS text (the three texts, one per failure) and nothing else shows a failure; a failed refusal shows the text it carries; no
 * text of the screen is written in the surface itself (every text on it is a label) and the slots the surface asks for are EXACTLY the ones [ConsentTexts] lists as the screen's own.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ConsentSurfaceTest {

    /** As `TechnicianFormSurfaceTest.RegisterComposeActivity`. */
    class RegisterComposeActivity : TestWatcher() {
        override fun starting(description: Description) {
            val app = ApplicationProvider.getApplicationContext<Application>()
            shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app.packageName, ComponentActivity::class.java.name))
        }
    }

    @get:Rule(order = 0) val registerActivity = RegisterComposeActivity()
    @get:Rule(order = 1) val rule = createAndroidComposeRule<ComponentActivity>()

    private val requested = linkedSetOf<ConsentText>()
    private val labels: ConsentLabels = { requested.add(it); it.toString() }
    private val calls = mutableListOf<String>()
    private val actions = ConsentActions(accept = { calls += "accept" }, decline = { calls += "decline" })

    private var ui by mutableStateOf<ConsentUi>(ConsentUi.Idle)

    private fun show() {
        rule.setContent { NeuralCheckTheme { Column(Modifier.verticalScroll(rememberScrollState())) { ConsentSection(ui = ui, labels = labels, actions = actions) } } }
        rule.waitForIdle()
    }

    private fun update(next: ConsentUi) {
        ui = next
        rule.waitForIdle()
    }

    private fun tag(vararg parts: String) = rule.onNodeWithTag(consentTag(*parts))

    private fun exists(vararg parts: String): Boolean = rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.TestTag).and(SemanticsMatcher("tag ${consentTag(*parts)}") {
        it.config.getOrNull(SemanticsProperties.TestTag) == consentTag(*parts)
    })).fetchSemanticsNodes().isNotEmpty()

    private val screensOwn = setOf(ConsentText.Title, ConsentText.Measures, ConsentText.DoesNotRead, ConsentText.Microphone, ConsentText.Accept, ConsentText.Decline)

    @Test
    fun `the text and the two buttons are drawn - each button reports its own tap and nothing else, and no failure and no bar is there`() {
        show()
        for (t in listOf("title", "measures", "doesNotRead", "microphone")) assertTrue(t, exists(t))
        assertFalse("the retention paragraph is gone (version 2: the code does not prove it)", exists("retention"))
        assertEquals("the screen's own six texts, nothing more", screensOwn, requested)
        for (t in listOf("failure", "declineFailure", "progress")) assertFalse(t, exists(t))
        tag("accept").assertIsEnabled().performClick()
        assertEquals(listOf("accept"), calls)
        tag("decline").assertIsEnabled().performClick()
        assertEquals(listOf("accept", "decline"), calls)
    }

    @Test
    fun `while a call is out a bar is drawn and both buttons are disabled - for the recording and for the refusal`() {
        show()
        for (busy in listOf(ConsentUi.Recording, ConsentUi.Declining)) {
            update(busy)
            assertTrue("$busy", exists("progress"))
            tag("accept").assertIsNotEnabled()
            tag("decline").assertIsNotEnabled()
            update(ConsentUi.Idle)
            assertFalse("$busy: the bar goes with the call", exists("progress"))
            tag("accept").assertIsEnabled()
        }
        assertTrue("a disabled button reports nothing", calls.isEmpty())
    }

    @Test
    fun `once the screen is finished both buttons are disabled - nothing can be pressed twice`() {
        show()
        for (finished in listOf(ConsentUi.Recorded, ConsentUi.NotApplicable, ConsentUi.Declined, ConsentUi.NoSession)) {
            update(finished)
            tag("accept").assertIsNotEnabled()
            tag("decline").assertIsNotEnabled()
            assertFalse("$finished: no bar", exists("progress"))
        }
        assertTrue(calls.isEmpty())
    }

    @Test
    fun `each failed recording shows ITS text, the user may press again, and a failed refusal shows the text it carries`() {
        show()
        val expected = mapOf(
            ConsentFailure.SESSION_NOT_SCANNING to ConsentText.RecordSessionNotScanning,
            ConsentFailure.STORED_UNREADABLE to ConsentText.RecordStoredUnreadable,
            ConsentFailure.OTHER to ConsentText.RecordFailed
        )
        assertEquals("every failure has a text", ConsentFailure.values().toSet(), expected.keys)
        for ((failure, text) in expected) {
            requested.clear()
            update(ConsentUi.RecordFailed(failure))
            assertTrue("$failure", exists("failure"))
            assertEquals("$failure: the screen's own six and this failure's text", screensOwn + text, requested)
            assertEquals(text, ConsentTexts.forFailure(failure))
            tag("accept").assertIsEnabled()
            tag("decline").assertIsEnabled()
            assertFalse("$failure: a recording failure is not a refusal's failure", exists("declineFailure"))
        }
        update(ConsentUi.DeclineFailed("the cancel's own text"))
        assertTrue(exists("declineFailure"))
        assertFalse("a refusal's failure is not a recording failure", exists("failure"))
        tag("decline").assertIsEnabled()
    }

    @Test
    fun `the three failure texts are the only slots outside the screen's own, and every slot is one text`() {
        assertEquals(screensOwn + setOf(ConsentText.RecordFailed, ConsentText.RecordSessionNotScanning, ConsentText.RecordStoredUnreadable), ConsentTexts.all.toSet())
        assertEquals("no two slots share a name (the test's labels are distinct per slot)", ConsentTexts.all.size, ConsentTexts.all.map { it.toString() }.toSet().size)
    }
}
