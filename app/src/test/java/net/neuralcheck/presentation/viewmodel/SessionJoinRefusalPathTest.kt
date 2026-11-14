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
import net.neuralcheck.presentation.viewmodel.SessionWireFixtures.Refusal
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The path of a REFUSED join, whole, at both join entry points, with the real texts — batch 2 (order 2026-10-15), item 2.
 *
 * The order: «لكل رمز في الملحق، في كل نداء يرميه، وفي نقطتَي الانضمام: اختبار يمرّ بالمسار كاملاً (RealJoinPathHarness القائم …)، ويفحص النصّ
 * المعروض، لا وصول الرمز فحسب (القاعدة 30)». And: the simulated server reply has the wire shape of Appendix A, including the same code under a
 * different message — «فإن نجح اختبار بمطابقة الرسالة، فهو لا يفحص القاعدة 21».
 *
 * What is REAL here: the SDK's parse of the wire JSON ([SessionWireFixtures.exceptionFromWire]), [net.neuralcheck.data.remote.FirebaseAnalysisService]
 * (`joinScanSession`, `callWithTimeout`, the `await` of the task), `joinScanSessionWithDeviceIdentity`, [JoinSessionViewModel] and [QRScannerViewModel],
 * `sessionErrorMessage`, and the app's own string resources (Arabic = the default set, Turkish = `values-tr`). What is REPLACED: only the
 * HTTP round trip and the Firebase auth object (the harness's two static mocks).
 *
 * What the text is compared with: for the approved texts, the literal the user approved ([ApprovedSessionTexts]); for the texts that already
 * existed, the resource — so a changed or swapped key fails, and a text matched by the SERVER'S message (which is the same sentence under several
 * codes) would fail too. Both languages run the same tests.
 */
@OptIn(ExperimentalCoroutinesApi::class)
abstract class SessionJoinRefusalPathBase {

    /** Which column of the approved table this run checks (Arabic is the default resource set, Turkish is `values-tr`). */
    internal abstract fun approved(text: ApprovedSessionTexts.Text): String

    /** The dispatcher the view models' coroutines run on; its scheduler is the virtual clock the QR chip's display time is measured on. */
    private lateinit var testDispatcher: TestDispatcher

    @Before
    fun setUp() {
        testDispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun existing(id: Int): String = context.getString(id)

    /** The text each reasonCode must show on the join, written out here (not read from the code under test). */
    private fun expectedJoinText(reasonCode: String?, qr: Boolean): String = when (reasonCode) {
        "SESSION_CODE_INVALID", "SESSION_EXPIRED" -> existing(R.string.join_session_invalid)
        "SESSION_ALREADY_JOINED" -> approved(ApprovedSessionTexts.JOIN_ALREADY_JOINED)
        "SESSION_CANCELLED" -> approved(ApprovedSessionTexts.JOIN_CANCELLED)
        "SESSION_COMPLETED" -> approved(ApprovedSessionTexts.JOIN_COMPLETED)
        // A-SUB wave 1 (order 2026-10-17; user decision 2: K1 as it is)
        "SHOP_SUBSCRIPTION_EXPIRED" -> approved(ApprovedSessionTexts.SHOP_SUBSCRIPTION_EXPIRED)
        // no approved text: the text this entry point showed before this change
        else -> if (qr) existing(R.string.qr_join_failed) else existing(R.string.error_generic)
    }

    private fun typedJoin(failure: Exception): Pair<String?, List<String>> =
        RealJoinPathHarness(viewModelContext = context).use { h ->
            h.joinFailure = failure
            val vm = h.joinViewModel()
            vm.join("NC-1234")
            assertNull("a refused join stores no session", h.holder.getInfo())
            vm.state.value.error to h.logLines.toList()
        }

    private fun qrJoin(failure: Exception): Pair<String?, List<String>> =
        RealJoinPathHarness(viewModelContext = context).use { h ->
            h.joinFailure = failure
            val vm = h.scannerViewModel()
            vm.onQrDetected("NC-1234")
            assertNull("a refused join stores no session", h.holder.getInfo())
            val state = vm.state.value
            assertTrue("the QR scanner shows the refusal ($state)", state is QRScannerViewModel.QrState.JoinFailed)
            (state as QRScannerViewModel.QrState.JoinFailed).message to h.logLines.toList()
        }

    private fun refusedLine(lines: List<String>, call: String): String =
        lines.single { it.startsWith("session-refused call=$call ") }

    // ---- the approved texts are in the resources, verbatim ----

    @Test
    fun `the resources hold the approved texts verbatim`() {
        val pairs = listOf(
            R.string.session_cancel_btn to ApprovedSessionTexts.OWNER_BUTTON,
            R.string.session_cancel_confirm_title to ApprovedSessionTexts.OWNER_CONFIRM_TITLE,
            R.string.session_cancel_confirm_text to ApprovedSessionTexts.OWNER_CONFIRM_BODY,
            R.string.session_cancelled_state to ApprovedSessionTexts.OWNER_CANCELLED_STATE,
            R.string.session_customer_left_state to ApprovedSessionTexts.OWNER_CUSTOMER_LEFT_STATE,
            R.string.session_join_completed to ApprovedSessionTexts.JOIN_COMPLETED,
            R.string.session_leave_btn to ApprovedSessionTexts.LEAVE_BUTTON,
            R.string.session_leave_confirm_title to ApprovedSessionTexts.LEAVE_CONFIRM_TITLE,
            R.string.session_leave_confirm_text to ApprovedSessionTexts.LEAVE_CONFIRM_BODY,
            R.string.session_join_already_joined to ApprovedSessionTexts.JOIN_ALREADY_JOINED,
            R.string.session_join_cancelled to ApprovedSessionTexts.JOIN_CANCELLED,
            R.string.session_cancel_completed to ApprovedSessionTexts.CANCEL_COMPLETED,
            R.string.session_cancel_analysis_in_flight to ApprovedSessionTexts.CANCEL_IN_FLIGHT,
            // A-SUB wave 1 (order 2026-10-17): K1-K4, verbatim
            R.string.shop_subscription_expired to ApprovedSessionTexts.SHOP_SUBSCRIPTION_EXPIRED,
            R.string.subscription_expired to ApprovedSessionTexts.SUBSCRIPTION_EXPIRED,
            R.string.shop_no_credit to ApprovedSessionTexts.SHOP_NO_CREDIT,
            R.string.free_device_used to ApprovedSessionTexts.FREE_DEVICE_USED
        )
        for ((id, text) in pairs) {
            assertEquals(context.resources.getResourceEntryName(id), approved(text), context.getString(id))
        }
    }

    // ---- the typed-code screen ----

    @Test
    fun `typed join - every refusal that carries a reasonCode shows the text of its code, whatever its Functions code and message`() {
        for (row in SessionWireFixtures.JOIN_REFUSALS) {
            val (shown, lines) = typedJoin(row.exception())
            assertEquals("${row.reasonCode} / ${row.label}", expectedJoinText(row.reasonCode, qr = false), shown)
            val line = refusedLine(lines, "join-typed")
            assertTrue("${row.label}: $line", line.contains(" reasonCode=${row.reasonCode} "))
        }
    }

    @Test
    fun `typed join - the same Functions code and the same message under two reasonCodes show two different texts (the message is not matched)`() {
        // Appendix A: NOT_FOUND + «الكود غير صالح أو منتهي الصلاحية» is sent for SESSION_CODE_INVALID, SESSION_ALREADY_JOINED, SESSION_COMPLETED,
        // SESSION_CANCELLED and SESSION_STATUS_UNKNOWN at the lookup. Five codes, one wire status, one message.
        val sameWire = SessionWireFixtures.JOIN_REFUSALS.filter {
            it.status == "NOT_FOUND" && it.message == SessionWireFixtures.JOIN_INVALID_OR_EXPIRED_MESSAGE
        }
        assertEquals(setOf(
            "SESSION_CODE_INVALID", "SESSION_ALREADY_JOINED", "SESSION_COMPLETED", "SESSION_CANCELLED", "SESSION_STATUS_UNKNOWN"
        ), sameWire.map { it.reasonCode }.toSet())
        val shownByCode = sameWire.associate { it.reasonCode!! to typedJoin(it.exception()).first }
        assertEquals("five codes on one wire status and one message, five different texts (four approved, one generic)", 5, shownByCode.values.toSet().size)
        assertNotEquals(shownByCode.getValue("SESSION_CODE_INVALID"), shownByCode.getValue("SESSION_COMPLETED"))
        assertNotEquals(shownByCode.getValue("SESSION_CODE_INVALID"), shownByCode.getValue("SESSION_CANCELLED"))
        assertNotEquals(shownByCode.getValue("SESSION_CODE_INVALID"), shownByCode.getValue("SESSION_ALREADY_JOINED"))
        assertEquals(
            "STATUS_UNKNOWN has no approved text: it falls back to the generic text (the same string the server's message would not give)",
            existing(R.string.error_generic), shownByCode.getValue("SESSION_STATUS_UNKNOWN")
        )
        // and the same code under two messages (lookup / transaction) shows the same text
        for (code in listOf("SESSION_CODE_INVALID", "SESSION_ALREADY_JOINED", "SESSION_COMPLETED", "SESSION_CANCELLED")) {
            val shown = SessionWireFixtures.JOIN_REFUSALS.filter { it.reasonCode == code }.map { typedJoin(it.exception()).first }
            assertEquals("$code: $shown", 1, shown.toSet().size)
        }
    }

    @Test
    fun `typed join - a refusal with no reasonCode shows the generic text and the log line says absent`() {
        for (row in SessionWireFixtures.JOIN_REFUSALS_WITHOUT_CODE) {
            val (shown, lines) = typedJoin(row.exception())
            assertEquals(row.label, existing(R.string.error_generic), shown)
            val line = refusedLine(lines, "join-typed")
            assertTrue("${row.label}: $line", line.contains(" reasonCode=absent ") && line.contains(" functionsException=true ") && line.endsWith(" shown=error_generic"))
        }
    }

    @Test
    fun `typed join - a reasonCode the app does not know is its own named state, not one of the known codes`() {
        val row = SessionWireFixtures.JOIN_REFUSAL_UNRECOGNIZED
        val (shown, lines) = typedJoin(row.exception())
        assertEquals(existing(R.string.error_generic), shown)
        val line = refusedLine(lines, "join-typed")
        assertTrue(line, line.contains(" reasonCode=unrecognized raw=SESSION_BRAND_NEW_REASON "))
    }

    /**
     * Batch 4, item 1 (user decision 1, 2026-10-16) REPLACES the test that stood here («a connection failure shows the connection text ...»): it built the failure as a bare
     * `IOException` whose own message carries the text - a shape the SDK never produces (it wraps a transport failure in a `FirebaseFunctionsException("INTERNAL")` with the
     * text in the CAUSE), so it passed while a real disconnect showed the generic text. Now every shape of [SessionWireFixtures.offlineFailures] - five built by the SDK's own
     * `onFailure`, one bare - shows the connection text, and a failure that carries a `reasonCode` never does: a known one, nor the unknown text «network».
     */
    @Test
    fun `typed join - a real disconnect shows the connection text, and a failure that carries a reasonCode - even the text network - does not`() {
        for ((label, failure) in SessionWireFixtures.offlineFailures()) {
            val (shown, lines) = typedJoin(failure)
            assertEquals(label, existing(R.string.login_error_network), shown)
            val line = refusedLine(lines, "join-typed")
            assertTrue(
                "$label: $line",
                line.contains(" reasonCode=absent ") &&
                    line.contains(" functionsException=${failure is com.google.firebase.functions.FirebaseFunctionsException} ") &&
                    line.endsWith(" shown=network")
            )
        }
        // a server answer with a code is the server's: the connection reading (an error TEXT match) does not override it
        val answered = Refusal("a known code whose message mentions the network", "FAILED_PRECONDITION", "network", "SESSION_STATUS_UNKNOWN")
        assertEquals(existing(R.string.error_generic), typedJoin(answered.exception()).first)
        val (shownForNetworkCode, lines) = typedJoin(SessionWireFixtures.REASON_CODE_NETWORK_REFUSAL.exception())
        assertEquals("a reasonCode that is the text network is an answer, not a disconnect", existing(R.string.error_generic), shownForNetworkCode)
        val line = refusedLine(lines, "join-typed")
        assertTrue(line, line.contains(" reasonCode=unrecognized raw=network ") && line.endsWith(" shown=error_generic"))
    }

    @Test
    fun `typed join - a plain exception shows the generic text, its raw message never reaches the screen, and text that merely looks like a code is not a code`() {
        // (the two behaviours the old JoinSessionErrorMessageTest pinned and that stay true: no raw message on screen; matching is never by text)
        val (shown, _) = typedJoin(RuntimeException("XYZ-SECRET-LOOKING-TOKEN from joinScanSession's underlying error"))
        assertEquals(existing(R.string.error_generic), shown)
        assertTrue(!shown!!.contains("XYZ-SECRET-LOOKING-TOKEN"))
        assertEquals(existing(R.string.error_generic), typedJoin(RuntimeException("not-found")).first)
        assertEquals(existing(R.string.error_generic), typedJoin(RuntimeException("SESSION_COMPLETED")).first)
    }

    // ---- the QR scanner ----

    @Test
    fun `qr join - every refusal that carries a reasonCode shows the SAME approved text as the typed screen`() {
        for (row in SessionWireFixtures.JOIN_REFUSALS) {
            val (typed, _) = typedJoin(row.exception())
            val (qr, lines) = qrJoin(row.exception())
            assertEquals("${row.reasonCode} / ${row.label}", expectedJoinText(row.reasonCode, qr = true), qr)
            if (row.reasonCode in listOf("SESSION_CODE_INVALID", "SESSION_EXPIRED", "SESSION_ALREADY_JOINED", "SESSION_CANCELLED", "SESSION_COMPLETED")) {
                assertEquals("the two join points agree on an approved text (${row.reasonCode})", typed, qr)
            }
            val line = refusedLine(lines, "join-qr")
            assertTrue("${row.label}: $line", line.contains(" reasonCode=${row.reasonCode} "))
        }
    }

    // ---- A-SUB wave 1 (order 2026-10-17): the join refused because the shop's subscription ended ----

    /**
     * The body is BUILT from the text of the order (ScanRefusalWire.JOIN_SHOP_SUBSCRIPTION_EXPIRED: not from probe-wire, not from production) and parsed by the SDK's own parser. Both join points show K1
     * (the approved text, verbatim), in both languages, and print the refusal line with the code known; the message and the Functions code are not read.
     */
    @Test
    fun `join - the shop's subscription ended shows K1 at both join points, whatever the message and the Functions code say`() {
        val rows = listOf(
            ScanRefusalWire.JOIN_SHOP_SUBSCRIPTION_EXPIRED,
            Refusal("a different message and Functions code for the same reasonCode", "FAILED_PRECONDITION", "رسالة أخرى", "SHOP_SUBSCRIPTION_EXPIRED")
        )
        for (row in rows) {
            val (typed, typedLines) = typedJoin(row.exception())
            val (qr, qrLines) = qrJoin(row.exception())
            assertEquals(row.label, approved(ApprovedSessionTexts.SHOP_SUBSCRIPTION_EXPIRED), typed)
            assertEquals("the QR chip shows the same text", typed, qr)
            assertTrue(refusedLine(typedLines, "join-typed"), refusedLine(typedLines, "join-typed").contains(" reasonCode=SHOP_SUBSCRIPTION_EXPIRED ") && refusedLine(typedLines, "join-typed").endsWith(" shown=shop_subscription_expired"))
            assertTrue(refusedLine(qrLines, "join-qr"), refusedLine(qrLines, "join-qr").contains(" reasonCode=SHOP_SUBSCRIPTION_EXPIRED ") && refusedLine(qrLines, "join-qr").endsWith(" shown=shop_subscription_expired"))
        }
    }

    /** The four scan-refusal codes the join does NOT send (they are not in its table): a join refused with one keeps the text of before at each point (rule 22: no guess). */
    @Test
    fun `join - a scan-refusal code that is not the shop's expiry has no join text and keeps the text of before`() {
        for (code in listOf("NO_CREDIT", "SUBSCRIPTION_EXPIRED", "FREE_DEVICE_USED", "SHOP_NO_CREDIT")) {
            val row = Refusal("not a join code", "PERMISSION_DENIED", "x", code)
            assertEquals(code, existing(R.string.error_generic), typedJoin(row.exception()).first)
            assertEquals(code, existing(R.string.qr_join_failed), qrJoin(row.exception()).first)
        }
    }

    // ---- batch 3, item 3: the join succeeded but its answer carries no usable session id (rule 22) ----

    /** What `joinScanSession` can answer with no usable id: the key missing, a JSON null, an empty text, a blank text, and no data at all (the SDK's null). */
    private fun answersWithoutSessionId(): List<Triple<String, Any?, String>> {
        fun answer(vararg id: Pair<String, Any?>) = hashMapOf<String, Any?>("ownerId" to "owner-1", "status" to "scanning", "evaluationEnabled" to true).also { it.putAll(id) }
        return listOf(
            Triple("no sessionId key", answer(), "absent"),
            Triple("sessionId is a JSON null", answer("sessionId" to null), "absent"),
            Triple("sessionId is an empty text", answer("sessionId" to ""), "blank"),
            Triple("sessionId is a blank text", answer("sessionId" to "   "), "blank"),
            Triple("an answer with no data", null, "absent")
        )
    }

    @Test
    fun `typed join - an answer without a usable session id is a failed join with the existing generic text, stores nothing, and the log names it`() {
        for ((label, response, expected) in answersWithoutSessionId()) {
            RealJoinPathHarness(viewModelContext = context).use { h ->
                h.joinResponse = response
                val vm = h.joinViewModel()
                vm.join("NC-1234")
                assertNull("$label: no session id reached the screen, so it does not navigate", vm.state.value.sessionId)
                assertEquals("$label: the failure is shown - it is not ignored in silence", existing(R.string.error_generic), vm.state.value.error)
                assertNull("$label: the holder holds nothing", h.holder.getInfo())
                assertEquals(label, 1, h.logLines.count { it == "session-join-failed sessionId=$expected" })
                val refused = refusedLine(h.logLines, "join-typed")
                assertTrue("$label: $refused", refused.contains(" reasonCode=absent ") && refused.contains(" functionsException=false ") && refused.endsWith(" shown=error_generic"))
            }
        }
    }

    @Test
    fun `qr join - an answer without a usable session id is the same failed join, with the chip text this point always showed, and the same log lines`() {
        for ((label, response, expected) in answersWithoutSessionId()) {
            RealJoinPathHarness(viewModelContext = context).use { h ->
                h.joinResponse = response
                val vm = h.scannerViewModel()
                vm.onQrDetected("NC-1234")
                val state = vm.state.value
                assertTrue("$label: the QR scanner shows a failure ($state)", state is QRScannerViewModel.QrState.JoinFailed)
                assertEquals(label, existing(R.string.qr_join_failed), (state as QRScannerViewModel.QrState.JoinFailed).message)
                assertNull("$label: the holder holds nothing", h.holder.getInfo())
                assertEquals(label, 1, h.logLines.count { it == "session-join-failed sessionId=$expected" })
                val refused = refusedLine(h.logLines, "join-qr")
                assertTrue("$label: $refused", refused.contains(" reasonCode=absent ") && refused.contains(" functionsException=false ") && refused.endsWith(" shown=qr_join_failed"))
            }
        }
    }

    @Test
    fun `a join answered WITH a session id still joins at both points, and prints no join-failed line`() {
        RealJoinPathHarness(viewModelContext = context).use { h ->
            h.joinResponse = hashMapOf<String, Any?>("sessionId" to "sess-9", "ownerId" to "owner-1", "status" to "scanning", "evaluationEnabled" to true)
            val vm = h.joinViewModel()
            vm.join("NC-1234")
            assertEquals("sess-9", vm.state.value.sessionId)
            assertNull(vm.state.value.error)
            assertEquals("sess-9", h.holder.get())
            assertTrue(h.logLines.none { it.startsWith("session-join-failed") })
        }
        RealJoinPathHarness(viewModelContext = context).use { h ->
            h.joinResponse = hashMapOf<String, Any?>("sessionId" to "sess-9", "ownerId" to "owner-1", "status" to "scanning", "evaluationEnabled" to true)
            val vm = h.scannerViewModel()
            vm.onQrDetected("NC-1234")
            assertEquals(QRScannerViewModel.QrState.JoinSuccess("sess-9", net.neuralcheck.domain.consent.ConsentPartRead.Absent), vm.state.value)
            assertEquals("sess-9", h.holder.get())
        }
    }

    @Test
    fun `qr join - a refusal with no approved text keeps the chip text it always showed, and says so in the log`() {
        for (row in SessionWireFixtures.JOIN_REFUSALS_WITHOUT_CODE + SessionWireFixtures.JOIN_REFUSAL_UNRECOGNIZED) {
            val (shown, lines) = qrJoin(row.exception())
            assertEquals(row.label, existing(R.string.qr_join_failed), shown)
            assertTrue(refusedLine(lines, "join-qr").endsWith(" shown=qr_join_failed"))
        }
    }

    /**
     * Batch 5, item 1 (user decision 1, 2026-10-16: «الانضمام بـ QR بلا اتّصال: يعرض نصّ الاتّصال أيضاً، من المصدر الواحد نفسه») REPLACES the last two lines of the test above, which pinned the
     * fixed chip text `qr_join_failed` for a disconnect (`qrJoin(IOException("Unable to resolve host"))` shows `qr_join_failed`): a bare `IOException` carrying the text, a shape the SDK never
     * makes. Now every shape of [SessionWireFixtures.offlineFailures] - five built by the SDK's own `onFailure`, the connect timeout among them, one bare - shows the connection text on the QR
     * chip, the same text the typed screen shows for the same failure, and the log line says `shown=network` as it does there.
     */
    @Test
    fun `qr join - a real disconnect shows the connection text, the same as the typed screen, and the log line says network`() {
        assertNotEquals("the connection text is not the chip's own", existing(R.string.login_error_network), existing(R.string.qr_join_failed))
        for ((label, failure) in SessionWireFixtures.offlineFailures()) {
            val (qr, lines) = qrJoin(failure)
            assertEquals(label, existing(R.string.login_error_network), qr)
            assertEquals("$label: the typed screen shows the same text", typedJoin(failure).first, qr)
            val line = refusedLine(lines, "join-qr")
            assertTrue(
                "$label: $line",
                line.contains(" reasonCode=absent ") &&
                    line.contains(" functionsException=${failure is com.google.firebase.functions.FirebaseFunctionsException} ") &&
                    line.endsWith(" shown=network")
            )
        }
    }

    /**
     * What is NOT a disconnect on the QR path keeps the chip's own text, whatever its message says (batch 5, item 1): a failure that carries a `reasonCode` - known with no approved text,
     * unknown, the text «network» - because the server answered; a timeout of the call; a plain exception, whose raw message never reaches the chip. A known code WITH an approved text keeps
     * that text even when its message says network.
     */
    @Test
    fun `qr join - a failure that carries a reasonCode, a timeout of the call, and a plain exception are not a disconnect - the chip keeps its own text, even when the message says network`() {
        val notDisconnects: List<Triple<String, Exception, String>> = listOf(
            Triple("a known code with no approved text, whose message says network", Refusal("x", "FAILED_PRECONDITION", "network", "SESSION_STATUS_UNKNOWN").exception(), "SESSION_STATUS_UNKNOWN"),
            Triple("an unknown code whose message says network", Refusal("x", "FAILED_PRECONDITION", "network error", "SESSION_BRAND_NEW_REASON").exception(), "unrecognized"),
            Triple("a reasonCode whose text is network", SessionWireFixtures.REASON_CODE_NETWORK_REFUSAL.exception(), "unrecognized"),
            Triple("the timeout of the call (callWithTimeout's IOException)", java.io.IOException("Callable 'joinScanSession' timed out after 30000ms"), "absent"),
            Triple("a plain exception", RuntimeException("XYZ-SECRET-LOOKING-TOKEN from joinScanSession's underlying error"), "absent")
        )
        for ((label, failure, code) in notDisconnects) {
            val (shown, lines) = qrJoin(failure)
            assertEquals(label, existing(R.string.qr_join_failed), shown)
            assertTrue("$label: no raw message on the chip", !shown!!.contains("XYZ-SECRET-LOOKING-TOKEN"))
            val line = refusedLine(lines, "join-qr")
            assertTrue("$label: $line", line.contains(" reasonCode=$code ") && line.endsWith(" shown=qr_join_failed"))
        }
        // a known code with an approved text wins over a message that says network: the server answered
        assertEquals(
            existing(R.string.join_session_invalid),
            qrJoin(Refusal("x", "FAILED_PRECONDITION", "network", "SESSION_EXPIRED").exception()).first
        )
    }

    /** The chip's display time (`JOIN_FAILED_DISPLAY_MS`, 6 s) is the same for the connection text as for every other text of it: the scanner goes back to scanning after it, not before (batch 5, item 1: «ومدّة الشريحة كما هي»). */
    @Test
    fun `qr join - the chip with the connection text stays as long as the chip with any other text, then the scanner goes back to scanning`() {
        for ((label, failure) in listOf(
            "a disconnect (the connection text)" to SessionWireFixtures.offlineFailures().first().second,
            "a refusal with no approved text (the chip's own text)" to SessionWireFixtures.JOIN_REFUSALS_WITHOUT_CODE.first().exception(),
            "a refusal with an approved text" to SessionWireFixtures.JOIN_REFUSALS.first().exception()
        )) {
            RealJoinPathHarness(viewModelContext = context).use { h ->
                h.joinFailure = failure
                val vm = h.scannerViewModel()
                vm.onQrDetected("NC-1234")
                assertTrue("$label: the chip is up (${vm.state.value})", vm.state.value is QRScannerViewModel.QrState.JoinFailed)
                testDispatcher.scheduler.advanceTimeBy(5_999)
                testDispatcher.scheduler.runCurrent()
                assertTrue("$label: 5.999 s on, it is still up (${vm.state.value})", vm.state.value is QRScannerViewModel.QrState.JoinFailed)
                testDispatcher.scheduler.advanceTimeBy(1)
                testDispatcher.scheduler.runCurrent()
                assertEquals("$label: 6 s on, the scanner scans again", QRScannerViewModel.QrState.Scanning, vm.state.value)
            }
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SessionJoinRefusalPathArabicTest : SessionJoinRefusalPathBase() {
    override fun approved(text: ApprovedSessionTexts.Text): String = text.ar
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "tr")
class SessionJoinRefusalPathTurkishTest : SessionJoinRefusalPathBase() {
    override fun approved(text: ApprovedSessionTexts.Text): String = text.tr
}
