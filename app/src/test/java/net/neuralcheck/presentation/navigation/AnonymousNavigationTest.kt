package net.neuralcheck.presentation.navigation

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import net.neuralcheck.data.auth.AuthKind
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * A customer's phone through a REAL `NavHost` and a REAL `NavController` - A-SUB wave 1, user decision 1 «P1» (rule 37: the decisions and the guard run on the real navigation, not on a stand-in).
 *
 * The graph here is a miniature of `NeuralCheckNavHost`: the same route names, the same start decision ([startDestinationFor]), the same guard ([ClosedToAnonymous]) around the closed
 * routes, and the report's button calls the same [startNewScan]. Its screens are texts. What it proves that the pure tests cannot: where the back stack ends up after «فحص جديد» (the report
 * is gone, Back reaches login and not the previous visitor's report), and that a closed screen is never shown to an anonymous device even when something navigates to it.
 * What it does NOT prove: that the real screens (Hilt view models) render; that is the device check.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AnonymousNavigationTest {

    /** Runs BEFORE the compose rule starts its activity: registers `ComponentActivity` when the merged manifest of the variant does not declare it (as `OwnerCancelledStatePathTest`). */
    class RegisterComposeActivity : TestWatcher() {
        override fun starting(description: Description) {
            val app = ApplicationProvider.getApplicationContext<Application>()
            shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app.packageName, ComponentActivity::class.java.name))
        }
    }

    @get:Rule(order = 0) val registerActivity = RegisterComposeActivity()
    @get:Rule(order = 1) val rule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var nav: NavHostController

    /** The miniature of the graph: start decided by [startDestinationFor], the account routes wrapped by [ClosedToAnonymous], the funnel and the report open. */
    private fun show(kind: AuthKind, requireSubscription: Boolean = true) {
        rule.setContent {
            nav = rememberNavController()
            NavHost(nav, startDestination = startDestinationFor(requireSubscription, kind)) {
                composable("login") { Text("SCREEN-LOGIN") }
                composable("join_session") { Text("SCREEN-JOIN") }
                composable("pre_scan") { Text("SCREEN-PRESCAN") }
                composable("report/{scanId}") { Text("SCREEN-REPORT") }
                for (route in ROUTES_CLOSED_TO_ANONYMOUS) {
                    composable(route) { ClosedToAnonymous(nav, effectiveAuthKind(requireSubscription, kind)) { Text("SCREEN-ACCOUNT-$route") } }
                }
            }
        }
        rule.waitForIdle()
    }

    /** The route patterns on the back stack, bottom first (the graph's own root entry left out). */
    private fun stack(): List<String> =
        nav.currentBackStack.value.mapNotNull { it.destination.route }

    private fun current(): String? = nav.currentBackStackEntry?.destination?.route

    @Test
    fun `a device with an anonymous user opens at login, not at home`() {
        show(AuthKind.ANONYMOUS)
        assertEquals("login", current())
        rule.onNodeWithText("SCREEN-LOGIN").assertExists()
        rule.onNodeWithText("SCREEN-ACCOUNT-home").assertDoesNotExist()
    }

    @Test
    fun `a device with an account opens at home, and one with no user at login`() {
        show(AuthKind.REGISTERED)
        assertEquals("home", current())
        rule.onNodeWithText("SCREEN-ACCOUNT-home").assertExists()
    }

    @Test
    fun `a device with no user opens at login`() {
        show(AuthKind.NONE)
        assertEquals("login", current())
        rule.onNodeWithText("SCREEN-LOGIN").assertExists()
    }

    @Test
    fun `new scan on an anonymous device removes the report and lands on the code-entry screen, and Back reaches login`() {
        show(AuthKind.ANONYMOUS)
        // the customer's first visit: login -> (join, pre-scan, scan: popped as the real app pops them) -> report
        rule.runOnUiThread { nav.navigate("report/scan-1") }
        rule.waitForIdle()
        assertEquals(listOf("login", "report/{scanId}"), stack())

        rule.runOnUiThread { nav.startNewScan(AuthKind.ANONYMOUS) }
        rule.waitForIdle()

        assertEquals("join_session", current())
        assertEquals("the report is gone: the previous visitor's scan is not under the next code", listOf("login", "join_session"), stack())
        rule.onNodeWithText("SCREEN-JOIN").assertExists()
        rule.onNodeWithText("SCREEN-ACCOUNT-home").assertDoesNotExist()

        rule.runOnUiThread { nav.popBackStack() }
        rule.waitForIdle()
        assertEquals("Back from the code-entry screen reaches login, never the report", "login", current())
    }

    @Test
    fun `new scan on an anonymous device that started at login twice in a row does not stack a second code-entry screen`() {
        show(AuthKind.ANONYMOUS)
        rule.runOnUiThread { nav.navigate("report/scan-1") }
        rule.runOnUiThread { nav.startNewScan(AuthKind.ANONYMOUS) }
        rule.waitForIdle()
        rule.runOnUiThread { nav.navigate("report/scan-2") }
        rule.runOnUiThread { nav.startNewScan(AuthKind.ANONYMOUS) }
        rule.waitForIdle()
        assertEquals(listOf("login", "join_session"), stack())
    }

    @Test
    fun `new scan on an account goes to its home, which is already on its stack, exactly as before`() {
        show(AuthKind.REGISTERED)
        rule.runOnUiThread { nav.navigate("report/scan-1") }
        rule.waitForIdle()
        assertEquals(listOf("home", "report/{scanId}"), stack())

        rule.runOnUiThread { nav.startNewScan(AuthKind.REGISTERED) }
        rule.waitForIdle()

        assertEquals("home", current())
        assertEquals(listOf("home"), stack())
        rule.onNodeWithText("SCREEN-ACCOUNT-home").assertExists()
    }

    @Test
    fun `an anonymous device sent to a closed route by anything is taken to login and never sees it - every closed route`() {
        show(AuthKind.ANONYMOUS) // one composition: after each case the stack is back to login alone
        for (route in ROUTES_CLOSED_TO_ANONYMOUS) {
            // a stale entry, a deep link, a future button: whatever navigates there
            val concrete = route.replace("{scanType}", "post_repair").replace("{plan}", "single")
            rule.runOnUiThread { nav.navigate(concrete) }
            rule.waitForIdle()
            assertEquals("$route: the device ends at login", "login", current())
            assertEquals("$route: login is the only screen of the stack", listOf("login"), stack())
            rule.onNodeWithText("SCREEN-ACCOUNT-$route").assertDoesNotExist()
        }
    }

    @Test
    fun `an account is shown every closed route`() {
        show(AuthKind.REGISTERED)
        for (route in ROUTES_CLOSED_TO_ANONYMOUS) {
            val concrete = route.replace("{scanType}", "post_repair").replace("{plan}", "single")
            rule.runOnUiThread { nav.navigate(concrete) }
            rule.waitForIdle()
            assertEquals(route, route, current())
            rule.onNodeWithText("SCREEN-ACCOUNT-$route").assertExists()
        }
    }

    @Test
    fun `the datacollect flavor shows an anonymous device home and the account routes - nothing is closed there`() {
        show(AuthKind.ANONYMOUS, requireSubscription = false)
        assertEquals("home", current())
        rule.onNodeWithText("SCREEN-ACCOUNT-home").assertExists()
    }
}
