package net.neuralcheck.data.remote

import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import net.neuralcheck.data.auth.AuthManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import java.io.IOException

/**
 * Order 2026-10-10 (station 1, before the 1.0.11 build): the customer device
 * signs in anonymously BEFORE it calls `joinScanSession`
 * ([joinScanSessionWithDeviceIdentity]), so the join carries a uid.
 *
 * What this pins, case by case (the three cases the order named, plus the two
 * edges its "the join proceeds as today" clause needs):
 *  1. not signed in  -> sign-in happens first, then the join, in that order;
 *  2. signed in      -> no sign-in at all (any user, anonymous or real);
 *  3. sign-in fails  -> the join still happens, nothing is thrown to the caller;
 *  4. sign-in never finishes -> the join proceeds once the bound has passed;
 *  5. the caller is cancelled during the sign-in -> the cancellation propagates
 *     and no join is sent.
 *
 * What is real and what is not (rule 37): the function under test is the real
 * production function. [AuthManager] and [FirebaseAnalysisService] are Mockito
 * mocks (neither can be built on the JVM: both read Firebase static accessors),
 * so this does NOT prove that the real Firebase Auth sign-in succeeds, nor that
 * the server accepts the uid — only the ordering and the failure handling around
 * those two calls. `verifyNoMoreInteractions` also pins that nothing else is
 * touched: no `registerUser`, no account creation, no second callable.
 */
@OptIn(ExperimentalCoroutinesApi::class) // `currentTime` (virtual-time check in the bound test)
class JoinWithDeviceIdentityTest {

    private val joinResult = JoinSessionResult(
        sessionId = "sess-1",
        ownerId = "owner-1",
        status = "scanning",
        evaluationEnabled = true
    )

    private class Rig(
        val auth: AuthManager,
        val service: FirebaseAnalysisService,
        /** Every sign-in / join call, in the order it was made. */
        val events: MutableList<String>
    )

    private fun rig(
        signedIn: Boolean,
        signIn: suspend () -> Unit = {},
        answer: JoinSessionResult = joinResult
    ): Rig {
        val events = mutableListOf<String>()
        val auth = mock<AuthManager> {
            on { isLoggedIn } doReturn signedIn
            onBlocking { signInAnonymously() } doSuspendableAnswer {
                events.add("signIn")
                signIn()
                mock<FirebaseUser>()
            }
        }
        val service = mock<FirebaseAnalysisService> {
            onBlocking { joinScanSession(any()) } doSuspendableAnswer {
                events.add("join:" + it.getArgument<String>(0))
                answer
            }
        }
        return Rig(auth, service, events)
    }

    @Test
    fun `not signed in - the anonymous sign-in happens before the join, in that order`() = runBlocking {
        val r = rig(signedIn = false)

        val result = joinScanSessionWithDeviceIdentity(r.auth, r.service, "NC-1234")

        assertEquals(listOf("signIn", "join:NC-1234"), r.events)
        assertEquals("the join's own answer is returned unchanged", joinResult, result.answer)
        assertEquals("and its session id is the validated one", "sess-1", result.sessionId)
        inOrder(r.auth, r.service) {
            verify(r.auth).isLoggedIn
            verify(r.auth).signInAnonymously()
            verify(r.service).joinScanSession("NC-1234")
        }
        // Nothing else was touched: no registerUser, no account creation.
        verifyNoMoreInteractions(r.auth, r.service)
    }

    @Test
    fun `already signed in - no sign-in at all, the join goes straight through`() = runBlocking {
        val r = rig(signedIn = true)

        val result = joinScanSessionWithDeviceIdentity(r.auth, r.service, "NC-1234")

        assertEquals(listOf("join:NC-1234"), r.events)
        assertEquals(joinResult, result.answer)
        verify(r.auth, never()).signInAnonymously()
        verify(r.auth).isLoggedIn
        verify(r.service).joinScanSession("NC-1234")
        verifyNoMoreInteractions(r.auth, r.service)
    }

    @Test
    fun `sign-in fails - the join still happens and the failure does not reach the caller`() = runBlocking {
        // Two failure shapes: the network one the order named, and an arbitrary one.
        val failures = listOf<Throwable>(
            IOException("A network error (such as timeout, interrupted connection or unreachable host) has occurred."),
            IllegalStateException("anything else")
        )
        for (failure in failures) {
            val r = rig(signedIn = false, signIn = { throw failure })

            val result = joinScanSessionWithDeviceIdentity(r.auth, r.service, "NC-1234")

            assertEquals(
                "join must proceed exactly as it does today (no identity) after ${failure.javaClass.simpleName}",
                listOf("signIn", "join:NC-1234"),
                r.events
            )
            assertEquals(joinResult, result.answer)
        }
    }

    @Test
    fun `sign-in never finishes - the join proceeds once the bound has passed`() = runTest {
        val r = rig(signedIn = false, signIn = { awaitCancellation() })

        val result = joinScanSessionWithDeviceIdentity(r.auth, r.service, "NC-1234")

        assertEquals(listOf("signIn", "join:NC-1234"), r.events)
        assertEquals(joinResult, result.answer)
        // Virtual time: the join waited for the bound and not longer.
        assertEquals(DEVICE_IDENTITY_SIGN_IN_TIMEOUT_MS, currentTime)
    }

    /**
     * Batch 3, item 3 (rule 22): an answer WITHOUT a usable session id is not a join - the one function both entry points call ends it in a named failure and one
     * `session-join-failed` line, whatever the shape of the absence: the key missing (null), a blank text. (The key missing and a JSON null reach here the same way: both are
     * null in the DTO.) A normal answer prints nothing.
     */
    @Test
    fun `an answer without a usable session id is a named failure with one log line, and a normal answer prints none`() = runBlocking {
        val cases = listOf(
            Triple("no sessionId key, or a JSON null", JoinSessionResult(sessionId = null, ownerId = "owner-1", status = "scanning", evaluationEnabled = true), "absent"),
            Triple("an empty text", JoinSessionResult(sessionId = "", ownerId = "owner-1", status = "scanning", evaluationEnabled = true), "blank"),
            Triple("a blank text", JoinSessionResult(sessionId = "   ", ownerId = "owner-1", status = "scanning", evaluationEnabled = true), "blank"),
            Triple("every field absent (an answer with no data)", JoinSessionResult(), "absent")
        )
        val before = net.neuralcheck.util.EvalCardLog.sink
        try {
            for ((label, answer, expected) in cases) {
                val lines = mutableListOf<String>()
                net.neuralcheck.util.EvalCardLog.sink = { lines.add(it) }
                val r = rig(signedIn = true, answer = answer)
                val failure = try {
                    joinScanSessionWithDeviceIdentity(r.auth, r.service, "NC-1234")
                    null
                } catch (e: JoinAnswerWithoutSessionIdException) {
                    e
                }
                assertTrue("$label: the join call was made, and then refused as an answer", failure != null && r.events == listOf("join:NC-1234"))
                assertEquals("$label: absent means no key; blank means a text with nothing in it", expected == "absent", failure!!.absent)
                assertEquals(label, listOf("session-join-failed sessionId=$expected"), lines)
            }
            val lines = mutableListOf<String>()
            net.neuralcheck.util.EvalCardLog.sink = { lines.add(it) }
            val normal = rig(signedIn = true)
            val ok = joinScanSessionWithDeviceIdentity(normal.auth, normal.service, "NC-1234")
            assertEquals("sess-1", ok.sessionId)
            assertTrue("a normal answer prints no join-failed line: $lines", lines.none { it.startsWith("session-join-failed") })
        } finally {
            net.neuralcheck.util.EvalCardLog.sink = before
        }
    }

    @Test
    fun `the caller's own cancellation during the sign-in propagates and no join is sent`() = runTest {
        val r = rig(signedIn = false, signIn = { awaitCancellation() })

        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            joinScanSessionWithDeviceIdentity(r.auth, r.service, "NC-1234")
        }
        // Started undispatched: it is suspended inside the sign-in right now.
        assertEquals(listOf("signIn"), r.events)
        job.cancelAndJoin()

        assertTrue("the coroutine ends cancelled, not completed", job.isCancelled)
        assertEquals("no join after the caller is gone", listOf("signIn"), r.events)
    }
}
