package net.neuralcheck.presentation.viewmodel

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import net.neuralcheck.R
import net.neuralcheck.presentation.viewmodel.QRScannerViewModel.QrState
import net.neuralcheck.presentation.viewmodel.SessionWireFixtures.Refusal
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A code held in front of the camera after the server answered it - A-SUB wave 1, item 6 (order 2026-10-17; user decision 6: «نعم مع تمديده إلى QR غير الصالح»; round-1 report section 2, lines 139-169;
 * rules 30 and 37).
 *
 * Before: `resetToScanning` emptied `lastCode` when the 6-second chip ended, the camera (which decodes the latest frame as fast as it can, `STRATEGY_KEEP_ONLY_LATEST`) decoded the same code
 * again, and `joinSession` started over - a call, a chip and a vibration about every 6 seconds for as long as the phone was held. No earlier test pinned that loop (round-1 report, line 141), so
 * none is replaced; the 6-second chip test (`SessionJoinRefusalPathTest`) stays valid and runs next to these.
 *
 * What is REAL: [QRScannerViewModel] (its real `onQrDetected`, `joinSession`, `resetToScanning`), `joinScanSessionWithDeviceIdentity`, [net.neuralcheck.data.remote.FirebaseAnalysisService]'s real
 * `joinScanSession`, the SDK's parse of the wire body, `sessionErrorMessage` and the app's strings. What is REPLACED: the HTTP round trip and the auth object (the harness), the camera (a loop of
 * decoded frames, one every [FRAME_MS]), the clock (the test dispatcher's virtual time, through the view model's `clock` seam) and the vibration (a counter, through its `vibrate` seam).
 * The quiet time (`QUIET_MS`, 1.5 s) is the coordinator's judgment from the report, not a measured value - the device check reads it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
abstract class QrRefusedCodeLoopBase {

    internal abstract fun approved(text: ApprovedSessionTexts.Text): String

    private lateinit var dispatcher: TestDispatcher

    @Before
    fun setUp() {
        dispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private companion object {
        const val FRAME_MS = 100L
        const val CODE = "NC-1234"
        const val OTHER_CODE = "NC-9999"
    }

    private class Scanner(val h: RealJoinPathHarness, val vm: QRScannerViewModel, private val dispatcher: TestDispatcher) {
        var vibrations = 0
        /** Every state the scanner passed through, in order (the unconfined dispatcher runs the collector at each emission). */
        val states = mutableListOf<QrState>()

        init {
            vm.clock = { dispatcher.scheduler.currentTime }
            vm.vibrate = { vibrations++ }
            states.add(vm.state.value)
        }

        fun joinCalls(): Int = h.callableCalls("joinScanSession")

        private fun record() {
            val s = vm.state.value
            if (states.last() != s) states.add(s)
        }

        /** The camera: the same [raw] decoded every [FRAME_MS] for [forMs] of virtual time. */
        fun hold(raw: String, forMs: Long) {
            var t = 0L
            while (t < forMs) {
                vm.onQrDetected(raw)
                record()
                dispatcher.scheduler.advanceTimeBy(FRAME_MS)
                dispatcher.scheduler.runCurrent()
                record()
                t += FRAME_MS
            }
        }

        /** The code is out of the camera's sight for [ms]. */
        fun nothing(ms: Long) {
            dispatcher.scheduler.advanceTimeBy(ms)
            dispatcher.scheduler.runCurrent()
            record()
        }

        fun invalidChips(): Int = states.count { it == QrState.Invalid }
    }

    private fun scanner(h: RealJoinPathHarness) = Scanner(h, h.scannerViewModel(), dispatcher)

    private fun withScanner(failure: Exception?, block: (Scanner) -> Unit) =
        RealJoinPathHarness(viewModelContext = context).use { h ->
            h.joinFailure = failure
            block(scanner(h))
        }

    private val namedRefusal = Refusal("a named refusal", "PERMISSION_DENIED", "x", "SHOP_SUBSCRIPTION_EXPIRED")

    // ---- a named refusal: one call, one vibration, however long the code is held ----

    @Test
    fun `a code the server refused with a reasonCode and that stays in front of the camera for 30 seconds is asked about once, with one vibration`() {
        withScanner(namedRefusal.exception()) { s ->
            s.hold(CODE, 30_000)
            assertEquals("one call", 1, s.joinCalls())
            assertEquals("one vibration", 1, s.vibrations)
            assertEquals("the scanner is back to scanning after the chip, and stays quiet", QrState.Scanning, s.vm.state.value)
            assertEquals("the chip appeared once", 1, s.states.count { it is QrState.JoinFailed })
        }
    }

    @Test
    fun `lifting the code for 1_5 seconds and bringing it back is a deliberate second try - one more call and one more vibration`() {
        withScanner(namedRefusal.exception()) { s ->
            s.hold(CODE, 10_000)
            assertEquals(1, s.joinCalls())
            s.nothing(1_500)
            s.hold(CODE, 2_000)
            assertEquals("the second call", 2, s.joinCalls())
            assertEquals("the second vibration", 2, s.vibrations)
            // and held again, it is quiet again
            s.hold(CODE, 20_000)
            assertEquals(2, s.joinCalls())
            assertEquals(2, s.vibrations)
        }
    }

    @Test
    fun `a gap shorter than the quiet time is the same hold - the boundary is 1_5 seconds, not less`() {
        withScanner(namedRefusal.exception()) { s ->
            s.hold(CODE, 8_000)
            // the last frame was decoded at t, the next comes after a 1.4 s gap (the frame loop already spent 100 ms): still the same code held
            s.nothing(1_300)
            s.hold(CODE, 5_000)
            assertEquals("a 1.4 s gap does not reopen the question", 1, s.joinCalls())
            assertEquals(1, s.vibrations)
        }
    }

    @Test
    fun `a different code passes at once, and the refused one, brought back after another, is a new try`() {
        withScanner(namedRefusal.exception()) { s ->
            s.hold(CODE, 3_000)
            assertEquals(1, s.joinCalls())
            // another code is shown while the first one's answer is still the last word
            s.hold(OTHER_CODE, 1_000)
            assertEquals("the other code is dispatched, not blocked by the first", 2, s.joinCalls())
            assertEquals(2, s.vibrations)
            // the first code comes back: it was away (the camera saw another one in between) - a deliberate try
            s.nothing(7_000) // lets the other chip end
            s.hold(CODE, 1_000)
            assertEquals(3, s.joinCalls())
        }
    }

    // ---- a failure with no named reason: the scanner retries by itself, quietly ----

    @Test
    fun `a disconnect keeps retrying while the code is held - and vibrates once, at the first decode`() {
        for ((label, failure) in SessionWireFixtures.offlineFailures()) {
            withScanner(failure) { s ->
                s.hold(CODE, 30_000)
                assertTrue("$label: the retry continues (${s.joinCalls()} calls)", s.joinCalls() >= 4)
                assertEquals("$label: one vibration, not one per retry", 1, s.vibrations)
            }
        }
    }

    @Test
    fun `a refusal with no reasonCode, and a malformed answer, retry like a disconnect - the server named nothing`() {
        for (row in SessionWireFixtures.JOIN_REFUSALS_WITHOUT_CODE) {
            withScanner(row.exception()) { s ->
                s.hold(CODE, 30_000)
                assertTrue("${row.label}: retries (${s.joinCalls()})", s.joinCalls() >= 4)
                assertEquals("${row.label}: one vibration", 1, s.vibrations)
            }
        }
        RealJoinPathHarness(viewModelContext = context).use { h ->
            h.joinResponse = hashMapOf<String, Any?>("ownerId" to "owner-1") // no session id
            val s = scanner(h)
            s.hold(CODE, 30_000)
            assertTrue("an answer without a session id retries", s.joinCalls() >= 4)
            assertEquals(1, s.vibrations)
        }
    }

    @Test
    fun `after a disconnect ends, the retry joins and the scanner stops`() {
        RealJoinPathHarness(viewModelContext = context).use { h ->
            h.joinFailure = SessionWireFixtures.offlineFailures().first().second
            val s = scanner(h)
            s.hold(CODE, 7_000)
            assertTrue(s.joinCalls() >= 1)
            h.joinFailure = null
            h.joinResponse = hashMapOf<String, Any?>("sessionId" to "sess-9", "ownerId" to "owner-1", "status" to "scanning", "evaluationEnabled" to true)
            s.hold(CODE, 8_000)
            assertEquals(QrState.JoinSuccess("sess-9", net.neuralcheck.domain.consent.ConsentPartRead.Absent), s.vm.state.value)
            assertEquals("sess-9", h.holder.get())
            assertEquals("still one vibration for the whole attempt", 1, s.vibrations)
        }
    }

    // ---- every join code, once, with its text in both languages (rule 30) ----

    /** The text the chip shows for each reasonCode of the join - written out here, not read from the code under test. */
    private fun expectedChip(reasonCode: String): String = when (reasonCode) {
        "SESSION_CODE_INVALID", "SESSION_EXPIRED" -> context.getString(R.string.join_session_invalid)
        "SESSION_ALREADY_JOINED" -> approved(ApprovedSessionTexts.JOIN_ALREADY_JOINED)
        "SESSION_CANCELLED" -> approved(ApprovedSessionTexts.JOIN_CANCELLED)
        "SESSION_COMPLETED" -> approved(ApprovedSessionTexts.JOIN_COMPLETED)
        "SHOP_SUBSCRIPTION_EXPIRED" -> approved(ApprovedSessionTexts.SHOP_SUBSCRIPTION_EXPIRED)
        else -> context.getString(R.string.qr_join_failed)
    }

    @Test
    fun `every reasonCode the join carries is asked once while held, and the chip shows the text of its code`() {
        val rows = SessionWireFixtures.JOIN_REFUSALS + ScanRefusalWire.JOIN_SHOP_SUBSCRIPTION_EXPIRED + SessionWireFixtures.JOIN_REFUSAL_UNRECOGNIZED
        for (row in rows) {
            withScanner(row.exception()) { s ->
                s.hold(CODE, 600) // the first frames: the chip is up
                val chip = s.vm.state.value
                assertTrue("${row.label}: the chip is up ($chip)", chip is QrState.JoinFailed)
                assertEquals("${row.reasonCode} / ${row.label}", if (row.reasonCode == "SESSION_BRAND_NEW_REASON") context.getString(R.string.qr_join_failed) else expectedChip(row.reasonCode!!), (chip as QrState.JoinFailed).message)
                s.hold(CODE, 30_000)
                assertEquals("${row.reasonCode} / ${row.label}: one call", 1, s.joinCalls())
                assertEquals("${row.reasonCode}: one vibration", 1, s.vibrations)
            }
        }
    }

    // ---- the invalid QR (the decision's extension) ----

    @Test
    fun `a foreign QR held in front of the camera for 30 seconds shows its chip once, not every two seconds`() {
        withScanner(null) { s ->
            s.hold("https://example.com/not-ours", 30_000)
            assertEquals("one invalid chip", 1, s.invalidChips())
            assertEquals("the scanner is quiet afterwards", QrState.Scanning, s.vm.state.value)
            assertEquals("no join was ever asked for", 0, s.joinCalls())
            assertEquals("and nothing vibrated", 0, s.vibrations)
        }
    }

    @Test
    fun `a foreign QR lifted for 1_5 seconds and brought back shows its chip again, and another foreign QR shows its own at once`() {
        withScanner(null) { s ->
            s.hold("https://example.com/not-ours", 6_000)
            assertEquals(1, s.invalidChips())
            s.hold("https://example.com/another", 1_000)
            assertEquals("a different foreign QR is not blocked by the first", 2, s.invalidChips())
            s.nothing(3_000)
            s.hold("https://example.com/not-ours", 6_000)
            assertEquals("the first one, brought back after being away, shows again", 3, s.invalidChips())
        }
    }

    // ---- what stays as it was ----

    @Test
    fun `a scan UUID that is no local scan still shows its chip as before - the decision did not name it`() {
        withScanner(null) { s ->
            s.hold("123e4567-e89b-12d3-a456-426614174000", 1_000)
            assertTrue(s.states.any { it == QrState.NotFound })
        }
    }

    @Test
    fun `the 6-second chip of a refusal still ends after 6 seconds`() {
        withScanner(namedRefusal.exception()) { s ->
            s.vm.onQrDetected(CODE)
            assertTrue(s.vm.state.value is QrState.JoinFailed)
            dispatcher.scheduler.advanceTimeBy(5_999); dispatcher.scheduler.runCurrent()
            assertTrue(s.vm.state.value is QrState.JoinFailed)
            dispatcher.scheduler.advanceTimeBy(1); dispatcher.scheduler.runCurrent()
            assertEquals(QrState.Scanning, s.vm.state.value)
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class QrRefusedCodeLoopArabicTest : QrRefusedCodeLoopBase() {
    override fun approved(text: ApprovedSessionTexts.Text): String = text.ar
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "tr")
class QrRefusedCodeLoopTurkishTest : QrRefusedCodeLoopBase() {
    override fun approved(text: ApprovedSessionTexts.Text): String = text.tr
}
