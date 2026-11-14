package net.neuralcheck.domain.scoring

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.firebase.functions.FirebaseFunctionsException
import net.neuralcheck.R
import net.neuralcheck.presentation.viewmodel.SessionWireFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [failureSaysNoConnection] and [networkErrorMessageOrNull] - the ONE reading of «this failure is no connection» (batch 4, item 1; rule 24), tested on its own. What each of the
 * six places does with it is in the path tests (`SessionJoinRefusalPathTest` - the typed join and the QR join -, `SessionCancelPathTest`, `SessionLeavePathTest`, `ConnectionTextRealPathTest`); here the reading.
 * Robolectric, because the failures are built the way the SDK builds them (its own `onFailure` callback; its own parse of the wire JSON), and the text is the app's resource.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NoConnectionReadTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `each phrase of the list is read, in any case, in the failure's own message`() {
        for (phrase in listOf(
            "A network error (such as timeout, interrupted connection or unreachable host) has occurred.",
            "Unable to resolve host \"cloudfunctions.net\"",
            "No address associated with hostname",
            "java.net.UnknownHostException: cloudfunctions.net",
            "Failed to connect to /142.250.185.74:443"
        )) {
            assertTrue(phrase, failureSaysNoConnection(java.io.IOException(phrase)))
            assertTrue("upper case: $phrase", failureSaysNoConnection(java.io.IOException(phrase.uppercase())))
        }
        assertFalse("a failure with no message says nothing", failureSaysNoConnection(RuntimeException()))
    }

    @Test
    fun `the text is read in the cause chain - the shape the SDK makes of a transport failure`() {
        for ((label, failure) in SessionWireFixtures.offlineFailures()) {
            assertTrue(label, failureSaysNoConnection(failure))
        }
        val sdk = SessionWireFixtures.exceptionFromTransportFailure(java.net.UnknownHostException("Unable to resolve host \"cloudfunctions.net\""))
        assertEquals("the SDK's own message is the bare code", "INTERNAL", sdk.message)
        assertTrue("... and the text is in the cause only", failureSaysNoConnection(sdk))
        assertFalse("a reading of the message alone would not see it", failureSaysNoConnection(RuntimeException(sdk.message)))
    }

    /**
     * Batch 5, item 2 (user decision 2, 2026-10-16: «مهلة الاتّصال («failed to connect») تُعدّ «لا اتّصال» ... ومهلة القراءة لا تُعدّ»). The READ timeout is pinned by the test below (its row
     * «SDK: a socket timeout (DEADLINE_EXCEEDED)»); this pins the other half - a CONNECT timeout is a disconnect - on the failure the SDK itself makes of it, which has the same code as the
     * read timeout (`DEADLINE_EXCEEDED`): so it is the TEXT of the cause that decides, and the reading would be wrong the day it looked at the code.
     */
    @Test
    fun `a socket connect timeout is a disconnect - the SDK wraps it as DEADLINE_EXCEEDED, and the text of its cause decides, not the code`() {
        val connect = SessionWireFixtures.connectTimeoutFailure()
        assertEquals("the SDK's own wrapper of a socket timeout", FirebaseFunctionsException.Code.DEADLINE_EXCEEDED, connect.code)
        assertEquals("its message is the bare code name, as for the other transport failures", "DEADLINE_EXCEEDED", connect.message)
        assertTrue("the connection text is in the cause only", connect.cause is java.net.SocketTimeoutException)
        assertEquals(SessionWireFixtures.CONNECT_TIMEOUT_MESSAGE, connect.cause!!.message)
        assertTrue(failureSaysNoConnection(connect))
        assertFalse("a reading of the message alone would not see it", failureSaysNoConnection(RuntimeException(connect.message)))
        assertEquals(context.getString(R.string.login_error_network), networkErrorMessageOrNull(context, connect))
        assertTrue("in any case", failureSaysNoConnection(SessionWireFixtures.exceptionFromTransportFailure(java.net.SocketTimeoutException(SessionWireFixtures.CONNECT_TIMEOUT_MESSAGE.uppercase()))))
    }

    @Test
    fun `a failure with no connection phrase anywhere is not a disconnect - a timeout, a reset, a TLS failure, the server's own internal`() {
        for ((label, failure) in listOf<Pair<String, Throwable>>(
            "callWithTimeout's IOException" to java.io.IOException("Callable 'analyzeDevice' timed out after 300000ms", RuntimeException("Timed out waiting for 300000 ms")),
            "SDK: a socket timeout (DEADLINE_EXCEEDED)" to SessionWireFixtures.exceptionFromTransportFailure(java.net.SocketTimeoutException("timeout")),
            "SDK: a reset connection" to SessionWireFixtures.exceptionFromTransportFailure(java.io.IOException("Connection reset")),
            "SDK: a TLS failure" to SessionWireFixtures.exceptionFromTransportFailure(javax.net.ssl.SSLException("Read error: ssl=0x1: I/O error during system call")),
            "the server's own internal, no reasonCode" to SessionWireFixtures.CANCEL_REFUSALS_WITHOUT_CODE.single().exception()
        )) {
            assertFalse(label, failureSaysNoConnection(failure))
        }
    }

    @Test
    fun `the cause chain is read eight deep and no further, and a cycle in it ends`() {
        val a = RuntimeException("outer")
        val b = RuntimeException("inner", a)
        a.initCause(b) // a -> b -> a: a cycle no real SDK makes; the read must still end
        assertFalse(failureSaysNoConnection(a))
        var deep: Throwable = java.net.UnknownHostException("Unable to resolve host")
        repeat(NO_CONNECTION_CAUSE_DEPTH_MAX - 1) { deep = RuntimeException("wrapper", deep) } // the text is the 8th message: read
        assertTrue(failureSaysNoConnection(deep))
        repeat(2) { deep = RuntimeException("wrapper", deep) } // the 10th: past the bound, not read
        assertFalse(failureSaysNoConnection(deep))
        assertEquals("the bound is the one the leave decision measured", 8, NO_CONNECTION_CAUSE_DEPTH_MAX)
    }

    @Test
    fun `a failure that carries a server reasonCode is never a disconnect - known, unknown, the text network, and a connection text in its cause`() {
        for ((label, failure) in listOf<Pair<String, Throwable>>(
            "a known code whose message says network" to SessionWireFixtures.Refusal("x", "FAILED_PRECONDITION", "network", "SESSION_EXPIRED").exception(),
            "an unknown code whose message says network" to SessionWireFixtures.Refusal("x", "FAILED_PRECONDITION", "network error", "SESSION_BRAND_NEW_REASON").exception(),
            "the reasonCode is the text network" to SessionWireFixtures.REASON_CODE_NETWORK_REFUSAL.exception(),
            "a known code whose message says Unable to resolve host" to SessionWireFixtures.Refusal("x", "PERMISSION_DENIED", "Unable to resolve host", "SESSION_NOT_PARTICIPANT").exception(),
            // the server answered AND a connection text sits in the cause: the answer wins
            "a reasonCode with a connection text in the cause" to mock<FirebaseFunctionsException> {
                on { details } doReturn mapOf("reasonCode" to "network")
                on { message } doReturn "INTERNAL"
                on { cause } doReturn java.net.UnknownHostException("Unable to resolve host \"cloudfunctions.net\"")
            }
        )) {
            assertFalse(label, failureSaysNoConnection(failure))
        }
        // and the same failure with no reasonCode IS one: the exclusion is the reasonCode, not the text
        assertTrue(failureSaysNoConnection(SessionWireFixtures.Refusal("x", "FAILED_PRECONDITION", "network error", null).exception()))
    }

    @Test
    fun `networkErrorMessageOrNull says the app's connection text for a disconnect and nothing for any other failure`() {
        for ((label, failure) in SessionWireFixtures.offlineFailures()) {
            assertEquals(label, context.getString(R.string.login_error_network), networkErrorMessageOrNull(context, failure))
        }
        assertNull(networkErrorMessageOrNull(context, java.io.IOException("Callable 'analyzeDevice' timed out after 300000ms")))
        assertNull(networkErrorMessageOrNull(context, SessionWireFixtures.REASON_CODE_NETWORK_REFUSAL.exception()))
    }
}
