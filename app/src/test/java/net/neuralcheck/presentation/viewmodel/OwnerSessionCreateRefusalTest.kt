package net.neuralcheck.presentation.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import net.neuralcheck.R
import net.neuralcheck.data.remote.SessionErrorCode
import net.neuralcheck.presentation.screen.ErrorState
import net.neuralcheck.presentation.theme.NeuralCheckTheme
import net.neuralcheck.presentation.viewmodel.GenerateSessionViewModel.Phase
import net.neuralcheck.presentation.viewmodel.SessionWireFixtures.Refusal
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The owner's phone when `createScanSession` is refused - A-SUB wave 1, item 4 (order 2026-10-17; user decision 4: «نعم»; the coordinator's decision for the owner's text: `SHOP_SUBSCRIPTION_EXPIRED`
 * <- `subscription_expired` (K2), `SHOP_NO_CREDIT` <- the existing `subscription_no_scans`, no `details` <- the existing generic text as today). Rules 30 and 37.
 *
 * Real: the owner's view model, `FirebaseAnalysisService.createScanSession`, the SDK's own parse of the wire body, `sessionErrorMessage` and its CREATE table, the string resources (Arabic =
 * the default set, Turkish = `values-tr`), and the error screen composable. Replaced: only the HTTP round trip and the Firebase auth object (the harness).
 *
 * The refusal bodies are BUILT from the text of the order ([ScanRefusalWire.CREATE_BODIES]: not from probe-wire, not from production - see that object): the two of A-SUB wave 1 and, since the order of 2026-10-20,
 * the plan refusal (`SHOP_PLAN_REQUIRED`: the approved text in the app's language, no retry); a server that sends NO `details` on the first two is the third row of the tests.
 */
@OptIn(ExperimentalCoroutinesApi::class)
abstract class OwnerSessionCreateRefusalBase {

    internal abstract fun approved(text: ApprovedSessionTexts.Text): String

    /** Runs BEFORE the compose rule starts its activity (as `OwnerCancelledStatePathTest`). */
    class RegisterComposeActivity : TestWatcher() {
        override fun starting(description: Description) {
            val app = ApplicationProvider.getApplicationContext<Application>()
            shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app.packageName, ComponentActivity::class.java.name))
        }
    }

    @get:Rule(order = 0) val registerActivity = RegisterComposeActivity()
    @get:Rule(order = 1) val rule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun generic(): String = context.getString(R.string.session_error_generic)

    /**
     * What each code must show on the create, written out here (not read from the code under test): K2, the existing no-credit text and (order of 2026-10-20) the plan text; everything else, the generic text.
     * Rule 23: this REPLACES the version that gave `SHOP_PLAN_REQUIRED` the generic text (an unnamed code, with the retry).
     */
    private fun expectedCreateText(code: String?): String = when (code) {
        "SHOP_SUBSCRIPTION_EXPIRED" -> approved(ApprovedSessionTexts.SUBSCRIPTION_EXPIRED)
        "SHOP_NO_CREDIT" -> approved(ApprovedSessionTexts.NO_CREDIT_EXISTING)
        "SHOP_PLAN_REQUIRED" -> approved(ApprovedSessionTexts.SHOP_PLAN_REQUIRED)
        else -> generic()
    }

    /** The three codes the create names: each shows its own text and hides the retry. */
    private val namedCreateCodes = setOf("SHOP_SUBSCRIPTION_EXPIRED", "SHOP_NO_CREDIT", "SHOP_PLAN_REQUIRED")

    private fun created(failure: Exception): Triple<GenerateSessionViewModel.State, List<String>, Int> =
        RealJoinPathHarness(signedInAtStart = true, viewModelContext = context).use { h ->
            h.createFailure = failure
            val vm = GenerateSessionViewModel(context, h.service, h.canceller)
            assertEquals("the real service was called once", 1, h.callableCalls("createScanSession"))
            Triple(vm.state.value, h.logLines.toList(), h.snapshotListeners.size)
        }

    private fun refusedLine(lines: List<String>): String = lines.single { it.startsWith("session-refused call=create ") }

    @Test
    fun `the three named refusals show their text, hide the retry, and the log names the code and the text`() {
        assertEquals("the fixture rows are the three codes the create names", namedCreateCodes, ScanRefusalWire.CREATE_BODIES.map { it.reasonCode }.toSet())
        for (row in ScanRefusalWire.CREATE_BODIES) {
            val (state, lines, listeners) = created(row.exception())
            assertEquals(row.label, Phase.ERROR, state.phase)
            assertEquals("${row.reasonCode}: the text of the order's decision", expectedCreateText(row.reasonCode), state.error)
            assertFalse("${row.reasonCode}: a named refusal has no retry", state.retryable)
            assertNull(state.sessionId)
            assertNull(state.code)
            assertEquals("no listener attached to anything", 0, listeners)
            val line = refusedLine(lines)
            val shown = when (row.reasonCode) {
                "SHOP_SUBSCRIPTION_EXPIRED" -> "subscription_expired"
                "SHOP_NO_CREDIT" -> "subscription_no_scans"
                else -> "shop_plan_required"
            }
            assertTrue(line, line.contains(" reasonCode=${row.reasonCode} ") && line.endsWith(" shown=$shown"))
        }
    }

    /**
     * Order of 2026-10-20, item 1 and 3 (rule 30, the value AND its meaning): the plan refusal shows the approved text of the language the app runs in - never the server's own message (here the server's sentence is
     * deliberately different from the approved one, so a text taken from the wire would fail), and never `session_error_generic` (what it showed before: the code was not named).
     */
    @Test
    fun `the plan refusal shows the approved text in this language - not the generic one, not the server's message`() {
        val serverWords = "رسالة الخادم التي لا يقرؤها التطبيق"
        val (state, _, _) = created(Refusal("plan, another message", "FAILED_PRECONDITION", serverWords, "SHOP_PLAN_REQUIRED").exception())
        assertEquals(approved(ApprovedSessionTexts.SHOP_PLAN_REQUIRED), state.error)
        assertTrue("not the text the unnamed code showed before", state.error != generic())
        assertTrue("not the server's message", state.error != serverWords)
        assertFalse("the retry would ask the same plan the same question", state.retryable)
    }

    @Test
    fun `the message and the Functions code are not read - the same reasonCode under another message shows the same text`() {
        for (code in namedCreateCodes) {
            val (state, _, _) = created(Refusal("x", "FAILED_PRECONDITION", "a message this app has never seen", code).exception())
            assertEquals(code, expectedCreateText(code), state.error)
            assertFalse(state.retryable)
        }
    }

    @Test
    fun `a refusal with no details - what the deployed server sends today - shows the generic text and keeps the retry`() {
        // the two refusals as the server of today sends them: permission-denied with the messages and NO details
        for ((label, message) in listOf("no credit" to "لا يوجد رصيد فحوصات", "expired" to ScanRefusalWire.EXPIRED_MESSAGE)) {
            val (state, lines, _) = created(Refusal("today's server: $label", "PERMISSION_DENIED", message, null).exception())
            assertEquals(label, Phase.ERROR, state.phase)
            assertEquals(label, generic(), state.error)
            assertTrue("$label: the retry stays", state.retryable)
            val line = refusedLine(lines)
            assertTrue(line, line.contains(" reasonCode=absent ") && line.endsWith(" shown=session_error_generic"))
        }
    }

    @Test
    fun `every reasonCode this app knows, in every other shape - only the three named ones are final`() {
        for (code in SessionErrorCode.values()) {
            val (state, _, _) = created(Refusal("x", "PERMISSION_DENIED", "x", code.wire).exception())
            assertEquals(code.wire, expectedCreateText(code.wire), state.error)
            assertEquals(code.wire, code.wire !in namedCreateCodes, state.retryable)
        }
        val (unknown, lines, _) = created(Refusal("x", "PERMISSION_DENIED", "x", "BRAND_NEW_REFUSAL").exception())
        assertEquals(generic(), unknown.error)
        assertTrue("an unknown code keeps the retry", unknown.retryable)
        assertTrue(refusedLine(lines).contains(" reasonCode=unrecognized raw=BRAND_NEW_REFUSAL "))
    }

    @Test
    fun `a disconnect and a plain exception keep the generic text and the retry, and their raw message never reaches the screen`() {
        for ((label, failure) in SessionWireFixtures.offlineFailures() + ("a plain exception" to RuntimeException("XYZ-SECRET-LOOKING-TOKEN"))) {
            val (state, _, _) = created(failure)
            assertEquals(label, generic(), state.error)
            assertTrue(label, state.retryable)
            assertFalse(label, state.error!!.contains("XYZ-SECRET-LOOKING-TOKEN"))
        }
    }

    @Test
    fun `a retry after a named refusal is not offered, but the view model still creates when asked and the next answer is shown`() {
        RealJoinPathHarness(signedInAtStart = true, viewModelContext = context).use { h ->
            h.createFailure = ScanRefusalWire.CREATE_BODIES.first().exception()
            val vm = GenerateSessionViewModel(context, h.service, h.canceller)
            assertFalse(vm.state.value.retryable)
            h.createFailure = null
            vm.create()
            assertEquals("a later create (a new visit to the screen) works and resets the flag", Phase.WAITING, vm.state.value.phase)
            assertTrue(vm.state.value.retryable)
        }
    }

    // ---- the screen ----

    @Test
    fun `the error screen shows the retry button only when the failure is retryable, in both languages`() {
        val retry = context.getString(R.string.session_retry)
        val shownText = expectedCreateText("SHOP_SUBSCRIPTION_EXPIRED")
        var retryable = false
        rule.setContent { NeuralCheckTheme { ErrorState(shownText, retryable) { } } }
        rule.onNodeWithText(shownText).assertExists()
        rule.onNodeWithText(retry).assertDoesNotExist()
    }

    @Test
    fun `the plan refusal reaches the error screen as its approved text, with no retry button`() {
        val (state, _, _) = created(ScanRefusalWire.CREATE_BODIES.single { it.reasonCode == "SHOP_PLAN_REQUIRED" }.exception())
        val retry = context.getString(R.string.session_retry)
        val text = approved(ApprovedSessionTexts.SHOP_PLAN_REQUIRED)
        rule.setContent { NeuralCheckTheme { ErrorState(state.error!!, state.retryable) { } } }
        rule.onNodeWithText(text).assertExists()
        rule.onNodeWithText(retry).assertDoesNotExist()
    }

    @Test
    fun `the error screen with a retryable failure shows the text and the retry button`() {
        val retry = context.getString(R.string.session_retry)
        rule.setContent { NeuralCheckTheme { ErrorState(generic(), true) { } } }
        rule.onNodeWithText(generic()).assertExists()
        rule.onNodeWithText(retry).assertExists()
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OwnerSessionCreateRefusalArabicTest : OwnerSessionCreateRefusalBase() {
    override fun approved(text: ApprovedSessionTexts.Text): String = text.ar
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "tr")
class OwnerSessionCreateRefusalTurkishTest : OwnerSessionCreateRefusalBase() {
    override fun approved(text: ApprovedSessionTexts.Text): String = text.tr
}
