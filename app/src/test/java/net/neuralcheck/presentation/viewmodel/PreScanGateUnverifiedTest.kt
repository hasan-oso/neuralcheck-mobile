package net.neuralcheck.presentation.viewmodel

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import net.neuralcheck.BuildConfig
import net.neuralcheck.data.remote.JoinSessionResult
import net.neuralcheck.presentation.viewmodel.PreScanViewModel.AccessState
import net.neuralcheck.presentation.viewmodel.PreScanViewModel.Unverified
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The pre-scan gate when the subscription check FAILS - A-SUB wave 1, item 5 (order 2026-10-17; user decision 5 «(أ)»: «AllowedUnverified(CheckFailed(اسم الصنف)) منفصلة عن Allowed وسطر سجلّ، بلا نصّ جديد»;
 * rules 22 and 37).
 *
 * Before: any failure of `checkSubscription` ended in [AccessState.Allowed] - the same state as "the check said yes" - with a `Log.w` that printed the exception's MESSAGE. Round-1 report line 23
 * (restriction 3): «فشل checkSubscription يمرّ Allowed بلا حالة صريحة (القاعدة 22)». No earlier test pinned that (`git grep -n "AccessState" -- app/src/test` held only the session-bypass and the
 * signed-out cases, which are unchanged), so none is replaced.
 *
 * Real: [PreScanViewModel] (`checkAccess`), [net.neuralcheck.data.remote.FirebaseAnalysisService.checkSubscription] (its `callWithTimeout` and the `await`), the SDK's parse of a refusal body, `EvalCardLog`.
 * Replaced: only the HTTP round trip and the auth object (the harness). The failures are the ones the harness already builds: the SDK's own `onFailure` shapes, a refusal parsed from wire bytes, a plain exception.
 * Launch flavor only (the gate is open in datacollect by construction): skipped, not failed, where `REQUIRE_SUBSCRIPTION` is false.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class) // the SDK's parser of a wire body needs a real org.json (SessionWireFixtures.exceptionFromWire)
@Config(sdk = [35])
class PreScanGateUnverifiedTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        assumeTrue("needs REQUIRE_SUBSCRIPTION (launch flavor)", BuildConfig.REQUIRE_SUBSCRIPTION)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun gate(configure: (RealJoinPathHarness) -> Unit): Pair<AccessState, List<String>> =
        RealJoinPathHarness(signedInAtStart = true).use { h ->
            configure(h)
            val vm = h.preScanViewModel()
            // the view model's init asks once for the trial banner (a signed-in device outside a session); the gate is the next ask
            val before = h.callableCalls("checkSubscription")
            vm.checkAccess()
            assertEquals("the real service was asked once by the gate", before + 1, h.callableCalls("checkSubscription"))
            vm.accessState.value to h.logLines.toList()
        }

    private fun unverifiedLines(lines: List<String>) = lines.filter { it.startsWith("scan-gate ") }

    @Test
    fun `a check that answers with credit is Allowed - no unverified state, no gate line`() {
        val (state, lines) = gate { it.checkSubscriptionResponse = hashMapOf<String, Any?>("plan" to "single", "scansRemaining" to 3, "scansTotal" to 5, "isActive" to true) }
        assertEquals(AccessState.Allowed, state)
        assertTrue(lines.toString(), unverifiedLines(lines).isEmpty())
    }

    @Test
    fun `a check that answers with no credit is NoScansRemaining as before`() {
        val (state, lines) = gate { it.checkSubscriptionResponse = hashMapOf<String, Any?>("plan" to "free", "scansRemaining" to 0, "scansTotal" to 0, "isActive" to false) }
        assertEquals(AccessState.NoScansRemaining, state)
        assertTrue(unverifiedLines(lines).isEmpty())
    }

    @Test
    fun `a check that FAILS lets the scan go as AllowedUnverified with the failure's class name - a state apart from Allowed - and prints one line`() {
        val failures: List<Pair<String, Exception>> = SessionWireFixtures.offlineFailures() + listOf(
            "a timeout of the call" to java.io.IOException("Callable 'checkSubscription' timed out after 30000ms"),
            "a refusal with a reasonCode" to SessionWireFixtures.Refusal("x", "PERMISSION_DENIED", "x", "SHOP_NO_CREDIT").exception(),
            "a refusal with none" to SessionWireFixtures.Refusal("x", "INTERNAL", "INTERNAL", null).exception(),
            "a plain exception" to IllegalStateException("XYZ-SECRET-LOOKING-TOKEN")
        )
        for ((label, failure) in failures) {
            val (state, lines) = gate { it.checkSubscriptionFailure = failure }
            assertTrue("$label: $state", state is AccessState.AllowedUnverified)
            assertTrue("$label: it is NOT the state a positive check gives", state != AccessState.Allowed)
            val reason = (state as AccessState.AllowedUnverified).reason
            assertTrue(label, reason is Unverified.CheckFailed)
            assertEquals("$label: the class name of what was thrown", failure.javaClass.simpleName, (reason as Unverified.CheckFailed).errorClass)
            val line = unverifiedLines(lines).single()
            assertEquals(label, "scan-gate decision=allowed-unverified reason=check-failed error=${failure.javaClass.simpleName}", line)
            assertFalse("$label: the message never reaches the line", line.contains("XYZ-SECRET-LOOKING-TOKEN") || line.contains("timed out"))
        }
    }

    @Test
    fun `a session scan never asks the check - Allowed as before - and a signed-out device needs login as before`() {
        RealJoinPathHarness(signedInAtStart = true).use { h ->
            h.holder.setFromJoin(JoinSessionResult(sessionId = "sess-1", ownerId = "owner-1", status = "scanning", evaluationEnabled = false))
            h.checkSubscriptionFailure = RuntimeException("would fail if asked")
            val vm = h.preScanViewModel()
            vm.checkAccess()
            assertEquals(AccessState.Allowed, vm.accessState.value)
            assertEquals(0, h.callableCalls("checkSubscription"))
            assertTrue(unverifiedLines(h.logLines).isEmpty())
        }
        RealJoinPathHarness(signedInAtStart = false).use { h ->
            val vm = h.preScanViewModel()
            vm.checkAccess()
            assertEquals(AccessState.NeedsLogin, vm.accessState.value)
        }
    }

    @Test
    fun `the unverified line is built from the class name alone and bounded like every other server-or-SDK text`() {
        assertEquals("scan-gate decision=allowed-unverified reason=check-failed error=IOException", net.neuralcheck.util.EvalCardLog.scanGateUnverifiedLine("IOException"))
        assertEquals("a name with characters outside the token alphabet is reduced, not printed", true,
            net.neuralcheck.util.EvalCardLog.scanGateUnverifiedLine("Weird Name\n x").lines().size == 1)
    }
}
