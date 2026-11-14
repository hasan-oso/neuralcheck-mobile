package net.neuralcheck.presentation.viewmodel

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import net.neuralcheck.BuildConfig
import net.neuralcheck.data.auth.AuthManager
import net.neuralcheck.data.remote.FirebaseAnalysisService
import net.neuralcheck.data.remote.JoinSessionResult
import net.neuralcheck.data.remote.ScanSessionHolder
import net.neuralcheck.data.remote.SubscriptionRefreshBus
import net.neuralcheck.data.remote.SubscriptionStatus
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.mock
import java.util.Locale

/**
 * Order 2026-10-10 (before the 1.0.11 build). User answer: «Hide it in a session
 * (Recommended)» — the free-trial banner of [PreScanViewModel], and the
 * `checkSubscription` call behind it, do not run while the device holds a
 * session id.
 *
 * Why: the shop session is billed to the owner, and the customer device's
 * anonymous uid has no `users` doc, so the server answers `plan: "free"` /
 * `scansRemaining: 0` for it (`checkSubscription.js:16-20` in neuralcheck-backend,
 * relayed by the order — NOT read from this repository) and the banner would
 * show «trial used — subscribe to continue» to a customer who owes nothing.
 *
 * What is real: [PreScanViewModel] (its init block, [PreScanViewModel.checkAccess]
 * and the banner reducer), [ScanSessionHolder] filled through `setFromJoin` (the
 * call both join paths make) and [SubscriptionRefreshBus]. What is mocked:
 * [AuthManager], [FirebaseAnalysisService] (mocked here; when this was written the module lacked
 * Robolectric, a test dependency since 3284cca) and [Context]. So this does not prove what the real
 * server answers, nor that the screen draws nothing for `Hidden` — only that the
 * view model asks nothing and says `Hidden`.
 *
 * Rule 23: no earlier test pinned the banner showing in a session (there was no
 * [PreScanViewModel] test at all; `git grep -i trialBanner -- app/src/test` was
 * empty before this file), so nothing is replaced.
 *
 * Flavor: the banner loads only when `BuildConfig.REQUIRE_SUBSCRIPTION` (launch).
 * The tests that need it are skipped, not failed, in a build where it is false.
 */
@OptIn(ExperimentalCoroutinesApi::class) // Dispatchers.setMain / UnconfinedTestDispatcher, as in the other view-model tests
class PreScanViewModelTrialBannerSessionTest {

    private val created = mutableListOf<PreScanViewModel>()
    private lateinit var savedLocale: Locale

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        // LocaleHelper.wrap (called by the real conditions loop) calls Locale.setDefault.
        savedLocale = Locale.getDefault()
    }

    @After
    fun tearDown() {
        // The conditions loop is `while (true) { …; delay(2000) }`; stop it.
        created.forEach { it.viewModelScope.cancel() }
        created.clear()
        Locale.setDefault(savedLocale)
        Dispatchers.resetMain()
    }

    private fun launchFlavorOnly() =
        assumeTrue("needs REQUIRE_SUBSCRIPTION (launch flavor)", BuildConfig.REQUIRE_SUBSCRIPTION)

    /**
     * A [Context] complete enough for the real `checkConditions()` that the init
     * block starts: Strings come back empty, Context-returning calls return the
     * same mock, and the two types LocaleHelper.wrap reads are stubbed. Anything
     * else answers Mockito's default (null / 0 / false).
     */
    private fun context(): Context {
        val prefs = mock<SharedPreferences> { on { getString(anyOrNull(), anyOrNull()) } doReturn "ar" }
        val resources = mock<Resources>()
        lateinit var ctx: Context
        ctx = Mockito.mock(Context::class.java, Answer<Any?> { inv: InvocationOnMock ->
            when (inv.method.returnType) {
                String::class.java -> ""
                Context::class.java -> ctx
                SharedPreferences::class.java -> prefs
                Resources::class.java -> resources
                else -> Mockito.RETURNS_DEFAULTS.answer(inv)
            }
        })
        return ctx
    }

    private class Rig(
        val auth: AuthManager,
        val service: FirebaseAnalysisService,
        val bus: SubscriptionRefreshBus,
        val holder: ScanSessionHolder,
        val checkSubscriptionCalls: MutableList<SubscriptionStatus>
    ) {
        /** What the (mocked) server answers to the next checkSubscription. */
        @Volatile var answer: SubscriptionStatus = FREE_NO_SCANS_LEFT
    }

    private fun rig(signedIn: Boolean): Rig {
        val calls = mutableListOf<SubscriptionStatus>()
        lateinit var rig: Rig
        val auth = mock<AuthManager> { on { isLoggedIn } doReturn signedIn }
        val service = mock<FirebaseAnalysisService> {
            onBlocking { checkSubscription() } doSuspendableAnswer {
                val a = rig.answer
                calls.add(a)
                a
            }
        }
        rig = Rig(auth, service, SubscriptionRefreshBus(), ScanSessionHolder(), calls)
        return rig
    }

    /** The holder as both join paths fill it (JoinSessionViewModel.join / QRScannerViewModel). */
    private fun joinSession(holder: ScanSessionHolder) {
        holder.setFromJoin(
            JoinSessionResult(sessionId = "sess-1", ownerId = "owner-1", status = "scanning", evaluationEnabled = false)
        )
    }

    private fun viewModel(r: Rig): PreScanViewModel =
        PreScanViewModel(
            context = context(),
            authManager = r.auth,
            firebaseService = r.service,
            sessionHolder = r.holder,
            subscriptionRefreshBus = r.bus,
            warningsHolder = PreScanWarningsHolder()
        ).also { created.add(it) }

    // ---- in a session: nothing is asked, the banner stays Hidden ----

    @Test
    fun `in a session - the trial banner does not load - no checkSubscription call and the banner stays Hidden`() {
        launchFlavorOnly()
        val r = rig(signedIn = true) // the device's anonymous uid: signed in, with no users doc
        joinSession(r.holder)

        val vm = viewModel(r)

        assertEquals("no checkSubscription call from the banner", 0, r.checkSubscriptionCalls.size)
        assertEquals(PreScanViewModel.TrialBannerState.Hidden, vm.trialBanner.value)
    }

    @Test
    fun `in a session - a plan-change tick on the bus starts no checkSubscription call either`() {
        launchFlavorOnly()
        val r = rig(signedIn = true)
        joinSession(r.holder)
        val vm = viewModel(r)

        r.bus.emit()
        r.bus.emit()

        assertEquals("the bus collector is not started in a session", 0, r.checkSubscriptionCalls.size)
        assertEquals(PreScanViewModel.TrialBannerState.Hidden, vm.trialBanner.value)
    }

    // ---- outside a session: unchanged ----

    @Test
    fun `no session, signed in - the banner loads as before - free plan with no scans left reads trial-used`() {
        launchFlavorOnly()
        val r = rig(signedIn = true)
        r.answer = FREE_NO_SCANS_LEFT

        val vm = viewModel(r)

        assertEquals(1, r.checkSubscriptionCalls.size)
        assertEquals(PreScanViewModel.TrialBannerState.TrialUsed, vm.trialBanner.value)
    }

    @Test
    fun `no session, signed in - free plan with a scan left reads has-trial`() {
        launchFlavorOnly()
        val r = rig(signedIn = true)
        r.answer = SubscriptionStatus(plan = "free", scansRemaining = 1, isActive = true)

        val vm = viewModel(r)

        assertEquals(1, r.checkSubscriptionCalls.size)
        assertEquals(PreScanViewModel.TrialBannerState.HasTrial, vm.trialBanner.value)
    }

    @Test
    fun `no session, signed in - the bus collector still runs - an approved payment flips the banner to Hidden`() {
        launchFlavorOnly()
        val r = rig(signedIn = true)
        r.answer = FREE_NO_SCANS_LEFT
        val vm = viewModel(r)
        assertEquals(PreScanViewModel.TrialBannerState.TrialUsed, vm.trialBanner.value)

        r.answer = SubscriptionStatus(plan = "single", scansRemaining = 1, isActive = true)
        r.bus.emit()

        assertEquals("one call at init and one per tick", 2, r.checkSubscriptionCalls.size)
        assertEquals(PreScanViewModel.TrialBannerState.Hidden, vm.trialBanner.value)
    }

    @Test
    fun `no session, not signed in - nothing is loaded and the banner stays Hidden`() {
        launchFlavorOnly()
        val r = rig(signedIn = false)

        val vm = viewModel(r)
        r.bus.emit()

        assertEquals(0, r.checkSubscriptionCalls.size)
        assertEquals(PreScanViewModel.TrialBannerState.Hidden, vm.trialBanner.value)
    }

    // ---- the gate and the banner read the same session ----

    @Test
    fun `checkAccess in a session is Allowed and asks nothing - the same session the banner skips on`() {
        val r = rig(signedIn = true)
        joinSession(r.holder)
        val vm = viewModel(r)

        vm.checkAccess()

        assertEquals(PreScanViewModel.AccessState.Allowed, vm.accessState.value)
        assertEquals(0, r.checkSubscriptionCalls.size)
    }

    @Test
    fun `checkAccess outside a session, signed out - NeedsLogin as before`() {
        launchFlavorOnly() // with REQUIRE_SUBSCRIPTION false the gate is Allowed
        val r = rig(signedIn = false)
        val vm = viewModel(r)

        vm.checkAccess()

        assertEquals(PreScanViewModel.AccessState.NeedsLogin, vm.accessState.value)
    }

    // ---- rig honesty ----

    @Test
    fun `rig check - the real conditions loop ran in the init block (the mock Context is complete)`() {
        val r = rig(signedIn = true)
        val vm = viewModel(r)

        assertTrue("checkConditions() threw or never ran — the Context mock is incomplete",
            vm.conditions.value.isNotEmpty())
    }

    private companion object {
        /** What the server answers an anonymous uid with no `users` doc (relayed in the order, not read here). */
        val FREE_NO_SCANS_LEFT = SubscriptionStatus(plan = "free", scansRemaining = 0, isActive = false)
    }
}
