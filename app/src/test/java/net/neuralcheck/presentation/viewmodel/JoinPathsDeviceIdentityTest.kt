package net.neuralcheck.presentation.viewmodel

import android.content.Context
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import net.neuralcheck.data.auth.AuthManager
import net.neuralcheck.data.remote.FirebaseAnalysisService
import net.neuralcheck.data.remote.JoinSessionResult
import net.neuralcheck.data.remote.ScanSessionHolder
import net.neuralcheck.data.repository.ScanRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.mock

/**
 * Order 2026-10-10 (station 1, before the 1.0.11 build): BOTH entry points of
 * the customer join — the typed-code screen ([JoinSessionViewModel.join]) and
 * the QR scanner ([QRScannerViewModel.onQrDetected], session branch) — sign the
 * device in anonymously before `joinScanSession`.
 * `JoinWithDeviceIdentityTest` pins the function; this file pins that each real
 * caller goes through it (a caller that called `joinScanSession` directly again
 * would pass that test and fail here).
 *
 * What is real: the two ViewModels, [ScanSessionHolder], and
 * `joinScanSessionWithDeviceIdentity`. What is mocked: [AuthManager],
 * [FirebaseAnalysisService] and [Context] (mocked here; when this was written the module
 * lacked Robolectric - a test dependency since 3284cca), plus [ScanRepository], which the QR join branch
 * never touches. So these do not prove that a real Firebase sign-in or a real
 * server answer works — only the order and what reaches the user.
 */
@OptIn(ExperimentalCoroutinesApi::class) // Dispatchers.setMain / UnconfinedTestDispatcher, as in ManualTestsViewModelEvaluationFailureTest
class JoinPathsDeviceIdentityTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val joinResult = JoinSessionResult(
        sessionId = "sess-1",
        ownerId = "owner-1",
        status = "scanning",
        evaluationEnabled = true
    )

    private class Rig(
        val auth: AuthManager,
        val service: FirebaseAnalysisService,
        val events: MutableList<String>
    )

    private fun rig(signedIn: Boolean, signInFails: Boolean = false): Rig {
        val events = mutableListOf<String>()
        val auth = mock<AuthManager> {
            on { isLoggedIn } doReturn signedIn
            // QRScannerViewModel builds a flow on this in its initialiser.
            on { uidFlow } doReturn flowOf<String?>(null)
            onBlocking { signInAnonymously() } doSuspendableAnswer {
                events.add("signIn")
                if (signInFails) throw java.io.IOException("A network error has occurred.")
                mock<FirebaseUser>()
            }
        }
        val service = mock<FirebaseAnalysisService> {
            onBlocking { joinScanSession(any()) } doSuspendableAnswer {
                events.add("join:" + it.getArgument<String>(0))
                joinResult
            }
        }
        return Rig(auth, service, events)
    }

    private fun joinViewModel(r: Rig, holder: ScanSessionHolder) = JoinSessionViewModel(
        context = mock<Context>(),
        analysisService = r.service,
        authManager = r.auth,
        sessionHolder = holder
    )

    private fun scannerViewModel(r: Rig, holder: ScanSessionHolder) = QRScannerViewModel(
        context = mock<Context>(),
        scanRepository = mock<ScanRepository>(),
        authManager = r.auth,
        analysisService = r.service,
        sessionHolder = holder
    )

    // ---- typed-code screen ----

    @Test
    fun `typed code - not signed in - signs in anonymously first, then joins, and the join succeeds`() {
        val r = rig(signedIn = false)
        val holder = ScanSessionHolder()
        val vm = joinViewModel(r, holder)

        vm.join("  nc-ab12 ")

        assertEquals(listOf("signIn", "join:NC-AB12"), r.events)
        assertEquals("sess-1", vm.state.value.sessionId)
        assertNull(vm.state.value.error)
        assertEquals("the join context is still stored for the scan", "sess-1", holder.get())
    }

    @Test
    fun `typed code - signed in - no sign-in, the join is unchanged`() {
        val r = rig(signedIn = true)
        val holder = ScanSessionHolder()
        val vm = joinViewModel(r, holder)

        vm.join("NC-AB12")

        assertEquals(listOf("join:NC-AB12"), r.events)
        assertEquals("sess-1", vm.state.value.sessionId)
        assertNull(vm.state.value.error)
    }

    @Test
    fun `typed code - the sign-in fails - the join proceeds and no error reaches the user`() {
        val r = rig(signedIn = false, signInFails = true)
        val holder = ScanSessionHolder()
        val vm = joinViewModel(r, holder)

        vm.join("NC-AB12")

        assertEquals(listOf("signIn", "join:NC-AB12"), r.events)
        assertEquals("sess-1", vm.state.value.sessionId)
        assertNull("the sign-in failure must not become a user-visible error", vm.state.value.error)
        assertEquals("sess-1", holder.get())
    }

    // ---- QR scanner ----

    @Test
    fun `QR scan - not signed in - signs in anonymously first, then joins, and the join succeeds`() {
        val r = rig(signedIn = false)
        val holder = ScanSessionHolder()
        val vm = scannerViewModel(r, holder)

        vm.onQrDetected("NC-AB3F7K")

        assertEquals(listOf("signIn", "join:NC-AB3F7K"), r.events)
        assertEquals(QRScannerViewModel.QrState.JoinSuccess("sess-1", net.neuralcheck.domain.consent.ConsentPartRead.Absent), vm.state.value)
        assertEquals("sess-1", holder.get())
    }

    @Test
    fun `QR scan - signed in - no sign-in, the join is unchanged`() {
        val r = rig(signedIn = true)
        val holder = ScanSessionHolder()
        val vm = scannerViewModel(r, holder)

        vm.onQrDetected("NC-AB3F7K")

        assertEquals(listOf("join:NC-AB3F7K"), r.events)
        assertEquals(QRScannerViewModel.QrState.JoinSuccess("sess-1", net.neuralcheck.domain.consent.ConsentPartRead.Absent), vm.state.value)
    }

    @Test
    fun `QR scan - the sign-in fails - the join proceeds and the scanner reports success, not a failure`() {
        val r = rig(signedIn = false, signInFails = true)
        val holder = ScanSessionHolder()
        val vm = scannerViewModel(r, holder)

        vm.onQrDetected("NC-AB3F7K")

        assertEquals(listOf("signIn", "join:NC-AB3F7K"), r.events)
        assertEquals(QRScannerViewModel.QrState.JoinSuccess("sess-1", net.neuralcheck.domain.consent.ConsentPartRead.Absent), vm.state.value)
        assertEquals("sess-1", holder.get())
    }
}
