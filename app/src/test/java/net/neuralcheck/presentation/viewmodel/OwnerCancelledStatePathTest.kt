package net.neuralcheck.presentation.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import net.neuralcheck.R
import net.neuralcheck.data.remote.CancelReason
import net.neuralcheck.presentation.screen.CancelledState
import net.neuralcheck.presentation.theme.NeuralCheckTheme
import net.neuralcheck.presentation.viewmodel.GenerateSessionViewModel.Phase
import org.junit.After
import org.junit.Assert.assertEquals
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
import org.robolectric.annotation.GraphicsMode

/**
 * What the OWNER reads when the session is cancelled - batch 3, item 2 (user decision 5, 2026-10-16: «ما يراه المالك: (ب) «غادر العميل الجلسة» / «Müşteri oturumdan ayrıldı»:
 * معتمد بلغتيه. ونصّ «رفض الموافقة» حين تُبنى شاشتها») - the path whole, for every `cancelReason` the server can leave on the document (rule 30), with the real texts.
 *
 * The path: a snapshot of `scanSessions/{id}` with `status = cancelled` and the `cancelReason` under test (the harness hands it to the real `SessionDocWatcher`, the way the SDK
 * would) -> the real [GenerateSessionViewModel] -> the state's `cancelReason` -> the text the app's own resources give for it, in Arabic and in Turkish -> the REAL
 * `CancelledState` composable drawn by Compose, whose text is read back from the semantics tree. The expected texts are written out in [ApprovedSessionTexts], not read from the
 * resource under test.
 *
 * Every value the order names - the three reasons, an absent one, an unknown one - plus a non-text one (a number: the watcher reads it as absent). REAL here: the view model, the
 * watcher's reading of the snapshot, the owner's own cancel through the real canceller, the resources, the composable, the log lines. REPLACED: the HTTP round trip, the auth object
 * and the Firestore listener (the harness; the snapshot is a mock, everything that reads it is real), and the theme's downloadable font (no provider here: the platform font draws).
 *
 * What it cannot see: the whole `GenerateSessionScreen` (it builds its view model with `hiltViewModel()`); that the branch `Phase.CANCELLED -> CancelledState(state.cancelReason)`
 * exists is pinned by `SessionScreensSourceShapeTest`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
abstract class OwnerCancelledStatePathBase {

    internal abstract fun approved(text: ApprovedSessionTexts.Text): String

    /** Runs BEFORE the compose rule starts its activity: registers `ComponentActivity` when the merged manifest of the variant (release) does not declare it. */
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

    private val left get() = approved(ApprovedSessionTexts.OWNER_CUSTOMER_LEFT_STATE)
    private val cancelled get() = approved(ApprovedSessionTexts.OWNER_CANCELLED_STATE)

    /** The `cancelReason` field a case puts on the document; [NONE] leaves the key out. */
    private data class Case(val label: String, val field: Any?, val present: Boolean, val expectedText: () -> String, val logReason: String, val logRaw: String)

    private fun cases(): List<Case> = listOf(
        Case("left_by_customer", "left_by_customer", true, { left }, "left_by_customer", "-"),
        Case("cancelled_by_owner", "cancelled_by_owner", true, { cancelled }, "cancelled_by_owner", "-"),
        Case("consent_declined", "consent_declined", true, { cancelled }, "consent_declined", "-"),
        Case("absent (no key)", null, false, { cancelled }, "absent", "-"),
        Case("null", null, true, { cancelled }, "absent", "-"),
        Case("not a text (7)", 7, true, { cancelled }, "absent", "-"),
        Case("unknown text", "left_by_someone_else", true, { cancelled }, "unknown", "left_by_someone_else"),
        // a near miss of a known value is unknown, not that value (matched exactly, as the statuses are)
        Case("unknown: upper case", "LEFT_BY_CUSTOMER", true, { cancelled }, "unknown", "LEFT_BY_CUSTOMER")
    )

    private var shown by mutableStateOf<CancelReason.Read>(CancelReason.Read.Absent)

    @Test
    fun `a cancelled session reaches the owner's screen with the text its cancelReason decides - every value, from the snapshot to the drawn text`() {
        rule.setContent { NeuralCheckTheme { CancelledState(shown) { } } }
        for (case in cases()) {
            RealJoinPathHarness(signedInAtStart = true, viewModelContext = context).use { h ->
                val vm = GenerateSessionViewModel(context, h.service, h.canceller)
                assertEquals(Phase.WAITING, vm.state.value.phase)
                val fields = buildMap<String, Any?> {
                    put("status", "cancelled")
                    if (case.present) put("cancelReason", case.field)
                }
                h.deliverSnapshot("sess-1", fields = fields)
                assertEquals("${case.label}: the screen is in the cancelled state", Phase.CANCELLED, vm.state.value.phase)

                // the text the decision gives, from the resources
                val expected = case.expectedText()
                assertEquals(case.label, expected, context.getString(ownerCancelledTextFor(vm.state.value.cancelReason).resId))

                // the log names the reason as read and the key shown, with the session hashed
                val line = h.logLines.single { it.startsWith("owner-cancelled-shown ") }
                assertTrue("${case.label}: $line", line.contains(" source=snapshot cancelReason=${case.logReason} cancelReasonRaw=${case.logRaw} "))
                assertTrue("${case.label}: $line", line.endsWith(" shown=${if (expected == left) "session_customer_left_state" else "session_cancelled_state"}"))
                assertTrue("${case.label}: the session id is hashed", !line.contains("sess-1") && line.contains("session=#"))

                // the composable draws that text, and only that one of the two
                shown = vm.state.value.cancelReason
                rule.waitForIdle()
                rule.onNodeWithText(expected).assertExists()
                rule.onNodeWithText(if (expected == left) cancelled else left).assertDoesNotExist()
            }
        }
    }

    @Test
    fun `the texts are the approved ones, in both languages, and are not the same text`() {
        assertEquals(left, context.getString(R.string.session_customer_left_state))
        assertEquals(cancelled, context.getString(R.string.session_cancelled_state))
        assertTrue(left != cancelled)
    }

    @Test
    fun `ownerCancelledTextFor - the three reasons, an absent one and an unknown one`() {
        assertEquals(OwnerCancelledText.CUSTOMER_LEFT, ownerCancelledTextFor(CancelReason.Read.Known(CancelReason.LEFT_BY_CUSTOMER)))
        assertEquals(OwnerCancelledText.CANCELLED, ownerCancelledTextFor(CancelReason.Read.Known(CancelReason.CANCELLED_BY_OWNER)))
        assertEquals(OwnerCancelledText.CANCELLED, ownerCancelledTextFor(CancelReason.Read.Known(CancelReason.CONSENT_DECLINED)))
        assertEquals(OwnerCancelledText.CANCELLED, ownerCancelledTextFor(CancelReason.Read.Absent))
        assertEquals(OwnerCancelledText.CANCELLED, ownerCancelledTextFor(CancelReason.Read.Unknown("left_by_customer ")))
        assertEquals("every reason the contract has is decided above", 3, CancelReason.values().size)
    }

    // ---- the owner's own cancel: the screen does not wait for the snapshot (batch 2, item 2) and must not overwrite the server's record of who cancelled ----

    private inline fun withOwner(body: (RealJoinPathHarness, GenerateSessionViewModel) -> Unit) {
        RealJoinPathHarness(signedInAtStart = true, viewModelContext = context).use { h ->
            val vm = GenerateSessionViewModel(context, h.service, h.canceller)
            assertEquals(Phase.WAITING, vm.state.value.phase)
            body(h, vm)
        }
    }

    @Test
    fun `the owner's own cancel, the one that cancelled it - the cancelled state at once, with the owner's reason and text 3`() {
        withOwner { h, vm ->
            h.cancelResponse = hashMapOf<String, Any?>("sessionId" to "sess-1", "status" to "cancelled", "alreadyCancelled" to false)
            vm.cancelSession()
            assertEquals(Phase.CANCELLED, vm.state.value.phase)
            assertEquals(CancelReason.Read.Known(CancelReason.CANCELLED_BY_OWNER), vm.state.value.cancelReason)
            assertEquals(cancelled, context.getString(ownerCancelledTextFor(vm.state.value.cancelReason).resId))
            assertTrue(h.logLines.any { it.startsWith("owner-cancelled-shown session=#") && it.contains(" source=own-cancel cancelReason=cancelled_by_owner ") && it.endsWith(" shown=session_cancelled_state") })
        }
    }

    @Test
    fun `the customer left while the owner's cancel was in flight, and the cancel is answered alreadyCancelled - the owner still reads that the customer left`() {
        withOwner { h, vm ->
            h.deliverSnapshot("sess-1", fields = mapOf("status" to "scanning"))
            assertEquals(Phase.SCANNING, vm.state.value.phase)
            val hold = com.google.android.gms.tasks.TaskCompletionSource<com.google.firebase.functions.HttpsCallableResult>()
            h.cancelHold = hold
            vm.cancelSession()
            assertEquals(GenerateSessionViewModel.CancelUi.InFlight, vm.state.value.cancel)
            // the customer's leave reaches the document first
            h.deliverSnapshot("sess-1", fields = mapOf("status" to "cancelled", "cancelReason" to "left_by_customer"))
            assertEquals(CancelReason.Read.Known(CancelReason.LEFT_BY_CUSTOMER), vm.state.value.cancelReason)
            // the owner's call is answered: the session was cancelled already
            hold.setResult(h.callableResultOf(hashMapOf<String, Any?>("sessionId" to "sess-1", "status" to "cancelled", "alreadyCancelled" to true)))
            assertEquals(Phase.CANCELLED, vm.state.value.phase)
            assertEquals("the owner's own button did not overwrite who cancelled", CancelReason.Read.Known(CancelReason.LEFT_BY_CUSTOMER), vm.state.value.cancelReason)
            assertEquals(left, context.getString(ownerCancelledTextFor(vm.state.value.cancelReason).resId))
        }
    }

    @Test
    fun `the owner's cancel answered without alreadyCancelled and before any snapshot - the reason is absent, text 3, and the log says absent`() {
        withOwner { h, vm ->
            h.cancelResponse = hashMapOf<String, Any?>("sessionId" to "sess-1", "status" to "cancelled")
            vm.cancelSession()
            assertEquals(Phase.CANCELLED, vm.state.value.phase)
            assertEquals(CancelReason.Read.Absent, vm.state.value.cancelReason)
            assertEquals(cancelled, context.getString(ownerCancelledTextFor(vm.state.value.cancelReason).resId))
            assertTrue(h.logLines.any { it.contains(" source=own-cancel cancelReason=absent ") && it.endsWith(" shown=session_cancelled_state") })
            // the snapshot that follows says who it was
            h.deliverSnapshot("sess-1", fields = mapOf("status" to "cancelled", "cancelReason" to "left_by_customer"))
            assertEquals(CancelReason.Read.Known(CancelReason.LEFT_BY_CUSTOMER), vm.state.value.cancelReason)
        }
    }

    @Test
    fun `a status that is not cancelled carries no reason, and a reason on another status is ignored`() {
        withOwner { h, vm ->
            h.deliverSnapshot("sess-1", fields = mapOf("status" to "scanning", "cancelReason" to "left_by_customer"))
            assertEquals(Phase.SCANNING, vm.state.value.phase)
            assertEquals(CancelReason.Read.Absent, vm.state.value.cancelReason)
            assertTrue("no cancelled line for a session that is not cancelled", h.logLines.none { it.startsWith("owner-cancelled-shown ") })
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "ar-ldrtl-w411dp-h800dp-xxhdpi")
class OwnerCancelledStatePathArabicTest : OwnerCancelledStatePathBase() {
    override fun approved(text: ApprovedSessionTexts.Text): String = text.ar
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "tr-w411dp-h800dp-xxhdpi")
class OwnerCancelledStatePathTurkishTest : OwnerCancelledStatePathBase() {
    override fun approved(text: ApprovedSessionTexts.Text): String = text.tr
}
