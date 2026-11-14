package net.neuralcheck.presentation.viewmodel

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import net.neuralcheck.data.remote.ScanSessionHolder
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Order 2026-10-10 (user decision «Permanent named log lines», coordinator's test order): every `NeuralCheck.EvalCard`
 * line is written AT ITS EVENT, with the fields the order lists, and never carries a raw id — through the real join
 * path of [RealJoinPathHarness] (only the network / identity boundary replaced; see that file), not through a stand-in
 * for the service.
 *
 * How the lines are read: [net.neuralcheck.util.EvalCardLog.sink] — the function the object prints through — is
 * replaced by the harness with a list. (`isReturnDefaultValues` alone would turn `Log.i` into a no-op and leave
 * nothing to read.) What this does NOT show: that logcat prints them (the default sink is one `Log.i(TAG, line)`
 * call; R8 keeps it — see `EvalCardLogSourceShapeTest`, which pins the rules, and the report's reading of the merged
 * configuration), nor anything about the real server.
 *
 * The ids of the harness — "sess-1", "owner-1", "anon-uid-1" (the device's uid), "scan-0001", the code "NC-AB3F7K" —
 * hash to the prefixes written literally below; the prefixes were computed with Node (see `LogRedactionTest`).
 * So each expected line here is also, literally, what logcat shows for that event.
 *
 * Both entry points run: the typed code and the QR scanner.
 */
@OptIn(ExperimentalCoroutinesApi::class) // Dispatchers.setMain / UnconfinedTestDispatcher, as in the other view-model tests
@RunWith(Parameterized::class)
class EvalCardLogRealPathTest(private val path: JoinEntryPoint) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun paths(): List<JoinEntryPoint> = JoinEntryPoint.values().toList()

        private const val CODE = "NC-AB3F7K"
        private const val SCAN_ID = "scan-0001"

        private const val SESSION = "#abe633f3a47a"  // sess-1
        private const val OWNER = "#391887cbcf92"    // owner-1
        private const val JOINER = "#9bfcd2b216cf"   // anon-uid-1
        private const val SCAN = "#d6b64f1f202b"     // scan-0001

        private val RAW_IDS = listOf("sess-1", "owner-1", "anon-uid-1", SCAN_ID, CODE)
    }

    private lateinit var harness: RealJoinPathHarness

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        harness = RealJoinPathHarness()
    }

    @After
    fun tearDown() {
        harness.close()
        Dispatchers.resetMain()
    }

    private fun answer(vararg extra: Pair<String, Any?>): HashMap<String, Any?> =
        hashMapOf<String, Any?>("sessionId" to "sess-1", "ownerId" to "owner-1", "status" to "scanning")
            .also { m -> extra.forEach { (k, v) -> m[k] = v } }

    private fun join() {
        when (path) {
            JoinEntryPoint.TYPED_CODE -> harness.joinViewModel().join(CODE)
            JoinEntryPoint.QR -> harness.scannerViewModel().onQrDetected(CODE)
        }
    }

    private val lines: List<String> get() = harness.logLines.toList()

    private fun assertNoRawIdAnywhere() {
        lines.forEach { line ->
            RAW_IDS.forEach { id -> assertFalse("`$line` carries the raw id $id", line.contains(id)) }
        }
    }

    // ======================= the whole chain, in order =======================

    @Test
    fun `the chain prints these lines in this order - join, holder, view model, scan start, bind, read start, read done, draft, handoff`() {
        harness.joinResponse = answer("evaluationEnabled" to true)

        join()
        val dao = FakeSessionEvaluationDao()
        val vm = harness.evaluationViewModel(dao)
        harness.startScan(SCAN_ID) // what StressTestService does when it mints the scan's id: the draft's session is the one the scan started in
        vm.bind(SCAN_ID)
        harness.awaitDraft(vm)
        vm.logHandoffToManualTests(SCAN_ID)

        assertEquals(
            listOf(
                "join-response-raw data=Map evaluationEnabled=Boolean:true evaluation_enabled=absent session=$SESSION owner=$OWNER joiner=$JOINER",
                "join-response-parsed evaluationEnabled=true session=$SESSION owner=$OWNER joiner=$JOINER",
                // ق15 (rule 23: the chain gained one line): these answers carry no consentEnabled, so the line says absent
                "join-consent-part consentEnabled=absent session=$SESSION",
                "holder-set via=join stored=true session=$SESSION owner=$OWNER evaluationEnabled=true previousEvaluationEnabled=none",
                "eval-vm-built snapshot=present evaluationEnabled=true isEvaluationSession=true session=$SESSION",
                "scan-session-recorded scan=$SCAN session=$SESSION evaluationEnabled=true",
                "eval-bind result=started scan=$SCAN",
                "eval-bind-read-start scan=$SCAN",
                "eval-bind-read-done found=false scan=$SCAN",
                "eval-bind-draft state=new scan=$SCAN",
                "eval-handoff isEvaluationSession=true draftReady=true dismissed=false scan=$SCAN"
            ),
            lines
        )
        assertNoRawIdAnywhere()
    }

    // ======================= the received flag: every value (rule 30) =======================

    private data class Case(
        val name: String,
        val response: HashMap<String, Any?>,
        val rawKey: String,
        val rawAlt: String,
        /** The word the parsed line, the holder line and the view-model line print: `true`, `false` or `absent` (order 2026-10-15). */
        val parsed: String,
        val isEvaluation: Boolean
    )

    private fun cases() = listOf(
        Case("true", answer("evaluationEnabled" to true), "Boolean:true", "absent", "true", true),
        Case("false", answer("evaluationEnabled" to false), "Boolean:false", "absent", "false", false),
        // an answer that did not carry the flag prints ABSENT end to end - it used to print false (the DTO defaulted it)
        Case("absent", answer(), "absent", "absent", "absent", false),
        Case("null", answer("evaluationEnabled" to null), "null", "absent", "absent", false),
        Case("string true", answer("evaluationEnabled" to "true"), "String:true", "absent", "true", true),
        Case("alternate key true", answer("evaluation_enabled" to true), "absent", "Boolean:true", "true", true),
        Case("alternate key false", answer("evaluation_enabled" to false), "absent", "Boolean:false", "false", false)
    )

    @Test
    fun `each received value prints its raw kind, then the parsed flag, then what the holder and the view model made of it`() {
        cases().forEach { c ->
            // a fresh harness per case: the static mocks and the holder are per instance
            harness.close()
            harness = RealJoinPathHarness()
            harness.joinResponse = c.response

            join()
            val vm = harness.evaluationViewModel(FakeSessionEvaluationDao())

            val expected = listOf(
                "join-response-raw data=Map evaluationEnabled=${c.rawKey} evaluation_enabled=${c.rawAlt} session=$SESSION owner=$OWNER joiner=$JOINER",
                "join-response-parsed evaluationEnabled=${c.parsed} session=$SESSION owner=$OWNER joiner=$JOINER",
                // ق15 (rule 23: the chain gained one line): these answers carry no consentEnabled, so the line says absent
                "join-consent-part consentEnabled=absent session=$SESSION",
                "holder-set via=join stored=true session=$SESSION owner=$OWNER evaluationEnabled=${c.parsed} previousEvaluationEnabled=none",
                "eval-vm-built snapshot=present evaluationEnabled=${c.parsed} isEvaluationSession=${c.isEvaluation} session=$SESSION"
            )
            assertEquals("case ${c.name}", expected, lines)
            assertEquals("case ${c.name}", c.isEvaluation, vm.isEvaluationSession)
            assertNoRawIdAnywhere()
        }
    }

    // ======================= the holder =======================

    @Test
    fun `a second answer prints what it replaced - including a second answer that lacks the flag`() {
        harness.joinResponse = answer("evaluationEnabled" to true)
        join()
        harness.joinResponse = answer()
        join()

        val holderLines = lines.filter { it.startsWith("holder-set") }
        assertEquals(
            listOf(
                "holder-set via=join stored=true session=$SESSION owner=$OWNER evaluationEnabled=true previousEvaluationEnabled=none",
                "holder-set via=join stored=true session=$SESSION owner=$OWNER evaluationEnabled=absent previousEvaluationEnabled=true"
            ),
            holderLines
        )
        assertNoRawIdAnywhere()
    }

    @Test
    fun `the legacy setter prints too - stored or emptied`() {
        harness.holder.set("sess-1")
        harness.holder.set(null)

        assertEquals(
            listOf(
                "holder-set via=legacy stored=true session=$SESSION owner=missing evaluationEnabled=absent previousEvaluationEnabled=none",
                "holder-set via=legacy stored=false session=missing owner=missing evaluationEnabled=absent previousEvaluationEnabled=absent"
            ),
            lines
        )
    }

    @Test
    fun `sign-out - the real AuthManager clears the holder and the line says why`() {
        harness.joinResponse = answer("evaluationEnabled" to true)
        join()

        harness.signOut()

        assertNull(harness.holder.getInfo())
        assertEquals(
            "holder-clear reason=sign-out hadInfo=true session=$SESSION evaluationEnabled=true",
            lines.single { it.startsWith("holder-clear") }
        )
        assertNoRawIdAnywhere()
    }

    @Test
    fun `every named clear reason prints its own wire string - with something to clear, then with nothing`() {
        harness.joinResponse = answer("evaluationEnabled" to true)
        join()

        ScanSessionHolder.ClearReason.values().forEach { reason ->
            harness.logLines.clear()
            join() // fill it again
            harness.logLines.clear()

            harness.holder.clear(reason)
            harness.holder.clear(reason)

            assertEquals(
                listOf(
                    "holder-clear reason=${reason.wire} hadInfo=true session=$SESSION evaluationEnabled=true",
                    "holder-clear reason=${reason.wire} hadInfo=false session=missing evaluationEnabled=none"
                ),
                lines
            )
        }
        assertNoRawIdAnywhere()
    }

    // ======================= the card's view model =======================

    @Test
    fun `a view model built with nothing in the holder says so`() {
        harness.evaluationViewModel(FakeSessionEvaluationDao())

        assertEquals(
            listOf("eval-vm-built snapshot=absent evaluationEnabled=none isEvaluationSession=false session=missing"),
            lines
        )
    }

    @Test
    fun `bind prints each of its named results`() {
        harness.joinResponse = answer("evaluationEnabled" to true)
        join()
        val vm = harness.evaluationViewModel(FakeSessionEvaluationDao())
        harness.startScan(SCAN_ID) // the scan started (and recorded its session) before the card's bind
        harness.logLines.clear()

        vm.bind("")                // scan-id-blank
        vm.bind(SCAN_ID)           // started
        harness.awaitDraft(vm)
        vm.bind(SCAN_ID)           // already-bound

        assertEquals(
            listOf(
                "eval-bind result=scan-id-blank scan=missing",
                "eval-bind result=started scan=$SCAN",
                "eval-bind-read-start scan=$SCAN",
                "eval-bind-read-done found=false scan=$SCAN",
                "eval-bind-draft state=new scan=$SCAN",
                "eval-bind result=already-bound scan=$SCAN"
            ),
            lines
        )
    }

    @Test
    fun `bind in a session that is not an evaluation session prints not-evaluation-session and starts nothing`() {
        harness.joinResponse = answer("evaluationEnabled" to false)
        join()
        val dao = FakeSessionEvaluationDao()
        val vm = harness.evaluationViewModel(dao)
        harness.logLines.clear()

        vm.bind(SCAN_ID)

        assertEquals(listOf("eval-bind result=not-evaluation-session scan=$SCAN"), lines)
        assertEquals(0, dao.getByScanIdCalls)
    }

    @Test
    fun `bind in a session whose join answer did not carry the flag prints flag-absent - its own name, not not-evaluation-session - and starts nothing`() {
        // order 2026-10-15 (rule 22): the absence is declared by name at the gate, not read as the server saying no
        harness.joinResponse = answer()
        join()
        val dao = FakeSessionEvaluationDao()
        val vm = harness.evaluationViewModel(dao)
        harness.logLines.clear()

        vm.bind(SCAN_ID)

        assertEquals(listOf("eval-bind result=flag-absent scan=$SCAN"), lines)
        assertEquals(0, dao.getByScanIdCalls)
    }

    @Test
    fun `the draft line says restored when an earlier row exists`() {
        harness.joinResponse = answer("evaluationEnabled" to true)
        join()
        val dao = FakeSessionEvaluationDao()
        dao.rows[SCAN_ID] = net.neuralcheck.domain.evaluation.EvaluationAnswers.emptyDraft(SCAN_ID, "sess-1", 1L)
        val vm = harness.evaluationViewModel(dao)
        harness.startScan(SCAN_ID) // the scan started (and recorded its session) before the card's bind
        harness.logLines.clear()

        vm.bind(SCAN_ID)
        harness.awaitDraft(vm)

        assertEquals(
            listOf(
                "eval-bind result=started scan=$SCAN",
                "eval-bind-read-start scan=$SCAN",
                "eval-bind-read-done found=true scan=$SCAN",
                "eval-bind-draft state=restored scan=$SCAN"
            ),
            lines
        )
    }

    @Test
    fun `a failed read prints the exception class, not its message - and the draft is still built new`() {
        harness.joinResponse = answer("evaluationEnabled" to true)
        join()
        val dao = FakeSessionEvaluationDao()
        dao.readFailure = java.io.IOException("could not read $SCAN_ID for sess-1")
        val vm = harness.evaluationViewModel(dao)
        harness.startScan(SCAN_ID) // the scan started (and recorded its session) before the card's bind
        harness.logLines.clear()

        vm.bind(SCAN_ID)
        harness.awaitDraft(vm)

        assertEquals(
            listOf(
                "eval-bind result=started scan=$SCAN",
                "eval-bind-read-start scan=$SCAN",
                "eval-bind-read-failed error=java.io.IOException scan=$SCAN",
                "eval-bind-draft state=new scan=$SCAN"
            ),
            lines
        )
        assertFalse("a read that threw prints no read-done line", lines.any { it.startsWith("eval-bind-read-done") })
        assertTrue("the behaviour is unchanged: a draft exists", vm.draft != null)
        assertNoRawIdAnywhere()
    }

    @Test
    fun `a draft that cannot be built prints eval-bind-draft-failed with the exception class - no draft, no ready line, no raw id`() {
        // Order 2026-10-14 («Root fix + guards», item 3): "A failure while building the draft is caught and logged as
        // eval-bind-draft-failed error=<class> - no silent death; no new screen text." The factory is the one seam
        // (see TechnicianEvaluationViewModel.draftFactory); the three throwables are what the release failure on
        // 1.0.8-1.0.13 produces — an ExceptionInInitializerError first (an Error, which `catch (e: Exception)` would
        // have missed), a NoClassDefFoundError on every later access — and an ordinary exception.
        val failures = listOf(
            IllegalStateException("TypeToken must be created with a type argument for sess-1 $SCAN_ID") to "java.lang.IllegalStateException",
            ExceptionInInitializerError(IllegalStateException("x")) to "java.lang.ExceptionInInitializerError",
            NoClassDefFoundError("Could not initialize class net.neuralcheck.domain.evaluation.EvaluationAnswers") to "java.lang.NoClassDefFoundError"
        )
        failures.forEach { (failure, className) ->
            harness.close()
            harness = RealJoinPathHarness()
            harness.joinResponse = answer("evaluationEnabled" to true)
            join()
            val vm = harness.evaluationViewModel(FakeSessionEvaluationDao())
            vm.draftFactory = { _, _, _ -> throw failure }
            harness.startScan(SCAN_ID) // the scan started (and recorded its session) before the card's bind
            harness.logLines.clear()

            vm.bind(SCAN_ID)
            awaitCondition("the failure line for $className") { lines.any { it.startsWith("eval-bind-draft-failed") } }

            assertEquals(
                className,
                listOf(
                    "eval-bind result=started scan=$SCAN",
                    "eval-bind-read-start scan=$SCAN",
                    "eval-bind-read-done found=false scan=$SCAN",
                    "eval-bind-draft-failed error=$className scan=$SCAN"
                ),
                lines
            )
            assertNull("no draft, so the card stays hidden ($className)", vm.draft)
            assertFalse("no ready line after a failure", lines.any { it.startsWith("eval-bind-draft ") })
            assertNoRawIdAnywhere() // the exception message, which holds ids, is never printed
        }
    }

    @Test
    fun `a restored draft never reaches the factory - the guard does not turn a restored row into a failure`() {
        harness.joinResponse = answer("evaluationEnabled" to true)
        join()
        val dao = FakeSessionEvaluationDao()
        dao.rows[SCAN_ID] = net.neuralcheck.domain.evaluation.EvaluationAnswers.emptyDraft(SCAN_ID, "sess-1", 1L)
        val vm = harness.evaluationViewModel(dao)
        vm.draftFactory = { _, _, _ -> throw IllegalStateException("the factory must not be called for a restored row") }
        harness.startScan(SCAN_ID) // the scan started (and recorded its session) before the card's bind
        harness.logLines.clear()

        vm.bind(SCAN_ID)
        harness.awaitDraft(vm)

        assertTrue(lines.contains("eval-bind-draft state=restored scan=$SCAN"))
        assertFalse(lines.any { it.startsWith("eval-bind-draft-failed") })
        assertEquals(dao.rows[SCAN_ID], vm.draft)
    }

    @Test
    fun `a read that has started and not returned prints the start line and nothing after it - then the done line and the draft once it returns`() {
        harness.joinResponse = answer("evaluationEnabled" to true)
        join()
        val dao = FakeSessionEvaluationDao()
        val gate = java.util.concurrent.CountDownLatch(1)
        dao.readGate = gate
        val vm = harness.evaluationViewModel(dao)
        harness.startScan(SCAN_ID) // the scan started (and recorded its session) before the card's bind
        harness.logLines.clear()

        try {
            vm.bind(SCAN_ID)
            awaitCondition("the read to start") { dao.getByScanIdCalls == 1 }
            awaitCondition("the start line") { lines.any { it.startsWith("eval-bind-read-start") } }

            // "start without done = Room stuck": the read is inside the dao, no later line exists, no draft exists
            assertEquals(
                listOf("eval-bind result=started scan=$SCAN", "eval-bind-read-start scan=$SCAN"),
                lines
            )
            assertNull("no draft while the read has not returned", vm.draft)
        } finally {
            gate.countDown() // never leave an IO thread parked, whatever the assertions said
        }

        harness.awaitDraft(vm)
        assertEquals(
            listOf(
                "eval-bind result=started scan=$SCAN",
                "eval-bind-read-start scan=$SCAN",
                "eval-bind-read-done found=false scan=$SCAN",
                "eval-bind-draft state=new scan=$SCAN"
            ),
            lines
        )
        assertNoRawIdAnywhere()
    }

    // ======================= dismiss and the handoff =======================

    @Test
    fun `dismiss prints, with and without a ready draft, and the handoff says it was dismissed`() {
        harness.joinResponse = answer("evaluationEnabled" to true)
        join()

        // before any draft exists
        val early = harness.evaluationViewModel(FakeSessionEvaluationDao())
        harness.logLines.clear()
        early.dismiss()
        assertEquals(listOf("eval-dismissed draftReady=false session=$SESSION"), lines)

        // after the draft is ready
        val vm = harness.evaluationViewModel(FakeSessionEvaluationDao())
        harness.startScan(SCAN_ID) // the scan started (and recorded its session) before the card's bind
        vm.bind(SCAN_ID)
        harness.awaitDraft(vm)
        harness.logLines.clear()
        vm.dismiss()
        vm.logHandoffToManualTests(SCAN_ID)

        assertEquals(
            listOf(
                "eval-dismissed draftReady=true session=$SESSION",
                "eval-handoff isEvaluationSession=true draftReady=true dismissed=true scan=$SCAN"
            ),
            lines
        )
    }

    @Test
    fun `the handoff line answers whether the card could have shown - for each state the card can be in`() {
        // not an evaluation session
        harness.joinResponse = answer("evaluationEnabled" to false)
        join()
        val notSession = harness.evaluationViewModel(FakeSessionEvaluationDao())
        harness.logLines.clear()
        notSession.logHandoffToManualTests(SCAN_ID)
        assertEquals(listOf("eval-handoff isEvaluationSession=false draftReady=false dismissed=false scan=$SCAN"), lines)

        // an evaluation session whose bind never ran (no draft)
        harness.close()
        harness = RealJoinPathHarness()
        harness.joinResponse = answer("evaluationEnabled" to true)
        join()
        val unbound = harness.evaluationViewModel(FakeSessionEvaluationDao())
        harness.logLines.clear()
        unbound.logHandoffToManualTests(SCAN_ID)
        assertEquals(listOf("eval-handoff isEvaluationSession=true draftReady=false dismissed=false scan=$SCAN"), lines)

        // an evaluation session with its draft
        val bound = harness.evaluationViewModel(FakeSessionEvaluationDao())
        harness.startScan(SCAN_ID) // the scan started (and recorded its session) before the card's bind
        bound.bind(SCAN_ID)
        harness.awaitDraft(bound)
        harness.logLines.clear()
        bound.logHandoffToManualTests(SCAN_ID)
        assertEquals(listOf("eval-handoff isEvaluationSession=true draftReady=true dismissed=false scan=$SCAN"), lines)
        assertNoRawIdAnywhere()
    }
}
