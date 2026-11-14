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
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import net.neuralcheck.domain.consent.ConsentLocale
import net.neuralcheck.domain.consent.ConsentLocaleRead
import net.neuralcheck.presentation.theme.NeuralCheckTheme
import net.neuralcheck.presentation.viewmodel.ConsentFailure
import net.neuralcheck.presentation.viewmodel.ConsentUi
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
 * The flip of [CONSENT_TEXTS_APPROVED] (2026-10-23), seen from the consent surface, with the app's REAL labels - [ConsentTexts.labelsFor] over the real resources, in each of the two languages - and the real
 * guard: nothing between the resources and the pixels is replaced by the test (rules 37, 41). REPLACES (rule 23) the guard-false pins listed in the commit message. What is asserted, in Arabic AND in Turkish (rule 30:
 * every text, every failure, every finished state): the screen draws the six texts (title, three paragraphs, two buttons) of the review, word for word, and none of the retention sentence; each failure shows ITS
 * text; once the screen is finished - recorded, the server's «لا ينطبق», declined, no session - both buttons are disabled. A phone whose language is neither Arabic nor Turkish gets no labels at all (the screen
 * draws nothing: a known limit, reported). What it cannot see: the composition of `ConsentScreen` itself (a Hilt view model; `ConsentGuardTest` pins its source) and a phone.
 * **The Turkish is proposed and needs a speaker; the final legal wording is pending Hasan, the KVKK lawyer later.**
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ConsentLiveScreenShapeTest {

    @get:Rule(order = 0) val registerActivity = ConsentSurfaceTest.RegisterComposeActivity()
    @get:Rule(order = 1) val rule = createAndroidComposeRule<ComponentActivity>()

    private val entries = ApprovedConsentTable.entries.associateBy { it.slot }

    private fun text(slot: ConsentText, locale: ConsentLocale): String = entries.getValue(slot.name).let { if (locale == ConsentLocale.AR) it.ar else it.tr }

    private var ui by mutableStateOf<ConsentUi>(ConsentUi.Idle)
    private val calls = mutableListOf<String>()

    private fun show(locale: ConsentLocale) {
        val labels = ConsentTexts.labelsFor(ApplicationProvider.getApplicationContext<Application>(), locale)
        assertNotNull("the guard is open and every text is approved: the labels exist", labels)
        rule.setContent {
            NeuralCheckTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    ConsentSection(ui = ui, labels = labels!!, actions = ConsentActions(accept = { calls += "accept" }, decline = { calls += "decline" }))
                }
            }
        }
        rule.waitForIdle()
    }

    private fun update(next: ConsentUi) {
        ui = next
        rule.waitForIdle()
    }

    private fun checkIn(locale: ConsentLocale) {
        assertTrue("the consent screen's guard is open", CONSENT_TEXTS_APPROVED)
        show(locale)
        for (slot in listOf(ConsentText.Title, ConsentText.Measures, ConsentText.DoesNotRead, ConsentText.Microphone, ConsentText.Accept, ConsentText.Decline)) {
            rule.onNodeWithText(text(slot, locale)).assertExists()
        }
        for (failure in listOf(ConsentText.RecordFailed, ConsentText.RecordSessionNotScanning, ConsentText.RecordStoredUnreadable)) {
            rule.onAllNodesWithText(text(failure, locale)).assertCountEquals(0)
        }
        // each failure shows ITS text, the buttons stay enabled (the user may try again or refuse)
        for (failure in ConsentFailure.values()) {
            update(ConsentUi.RecordFailed(failure))
            rule.onNodeWithText(text(ConsentTexts.forFailure(failure), locale)).assertExists()
            rule.onNodeWithTag(consentTag("accept")).assertIsEnabled()
            rule.onNodeWithTag(consentTag("decline")).assertIsEnabled()
        }
        // finished: the buttons are disabled and no failure is shown - the server's «لا ينطبق» is finished like a recorded consent
        for (finished in listOf(ConsentUi.Recorded, ConsentUi.NotApplicable, ConsentUi.Declined, ConsentUi.NoSession)) {
            update(finished)
            rule.onNodeWithTag(consentTag("accept")).assertIsNotEnabled()
            rule.onNodeWithTag(consentTag("decline")).assertIsNotEnabled()
            rule.onAllNodesWithText(text(ConsentText.RecordFailed, locale)).assertCountEquals(0)
        }
        assertTrue("a disabled button reports nothing", calls.isEmpty())
    }

    @Test
    @Config(qualifiers = "ar")
    fun `the consent screen draws the six reviewed texts and each failure in Arabic - and is finished by recorded, not applicable, declined and no session`() = checkIn(ConsentLocale.AR)

    @Test
    @Config(qualifiers = "tr")
    fun `the consent screen draws the six reviewed texts and each failure in Turkish - and is finished by recorded, not applicable, declined and no session`() = checkIn(ConsentLocale.TR)

    @Test
    fun `the retention sentence of version 1 is nowhere on the screen - and the microphone paragraph is`() {
        show(ConsentLocale.AR)
        rule.onAllNodesWithText("تُحفظ نتائج الفحص سنتين لتحسين دقّته.").assertCountEquals(0)
        rule.onNodeWithTag(consentTag("microphone")).assertExists()
        rule.onAllNodesWithText("ثلاث ثوانٍ", substring = true).assertCountEquals(0)
    }

    @Test
    fun `only Arabic and Turkish get a consent screen - a phone in another language, or in none, is Unsupported or Absent and the screen draws nothing`() {
        assertEquals(ConsentLocaleRead.Known(ConsentLocale.AR), ConsentLocaleRead.of("ar"))
        assertEquals(ConsentLocaleRead.Known(ConsentLocale.TR), ConsentLocaleRead.of("tr"))
        assertEquals(ConsentLocaleRead.Unsupported("en"), ConsentLocaleRead.of("en"))
        assertEquals(ConsentLocaleRead.Absent, ConsentLocaleRead.of(null))
        assertEquals(ConsentLocaleRead.Absent, ConsentLocaleRead.of(""))
    }
}
