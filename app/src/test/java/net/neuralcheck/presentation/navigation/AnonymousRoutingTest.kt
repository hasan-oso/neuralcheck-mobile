package net.neuralcheck.presentation.navigation

import net.neuralcheck.data.auth.AuthKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The routing decisions for a customer's phone (an anonymous user) - A-SUB wave 1, user decision 1 «P1». Rule 30: every kind of identity x both flavors x every route in the closed set, not one case.
 * The expected values are written out here from the order's table (round-1 report section 1.5, P1: «فحص جديد» for the anonymous <- the code-entry screen; reopening the app with an anonymous
 * user <- login; home, profile and history not reached), never read from the code under test.
 */
class AnonymousRoutingTest {

    @Test
    fun `the launch flavor starts at login for no user and for an anonymous user, and at home for an account`() {
        assertEquals("login", startDestinationFor(requireSubscription = true, kind = AuthKind.NONE))
        assertEquals("login", startDestinationFor(requireSubscription = true, kind = AuthKind.ANONYMOUS))
        assertEquals("home", startDestinationFor(requireSubscription = true, kind = AuthKind.REGISTERED))
    }

    @Test
    fun `the datacollect flavor always starts at home, whatever the identity`() {
        for (kind in AuthKind.values()) assertEquals(kind.name, "home", startDestinationFor(requireSubscription = false, kind = kind))
    }

    @Test
    fun `the report's new scan goes to the code-entry screen for an anonymous device and to home for everyone else`() {
        assertEquals("join_session", newScanRouteFor(AuthKind.ANONYMOUS))
        assertEquals("home", newScanRouteFor(AuthKind.REGISTERED))
        assertEquals("a device with no user keeps what it had: home", "home", newScanRouteFor(AuthKind.NONE))
    }

    @Test
    fun `an anonymous device is refused exactly the ten account routes, and no other`() {
        val closed = setOf(
            "home", "history", "history_select/{scanType}", "settings", "profile", "subscription", "shop_setup",
            "payment/{plan}", "scan_history", "payment_history"
        )
        assertEquals("the closed set", closed, ROUTES_CLOSED_TO_ANONYMOUS)
        for (route in closed) {
            assertFalse("$route must not open for an anonymous device", routeOpensFor(route, AuthKind.ANONYMOUS))
            assertTrue("$route opens for an account", routeOpensFor(route, AuthKind.REGISTERED))
            assertTrue("$route opens for no user (a device with no user is not anonymous; the graph sends it to login before it matters)", routeOpensFor(route, AuthKind.NONE))
        }
        // the routes the customer's own flow uses stay open for it
        for (route in listOf(
            "login", "join_session", "qr_scanner", "pre_scan/{scanType}/{linkedScanId}", "active_scan/{scanType}/{linkedScanId}",
            "manual_tests/{scanId}", "report/{scanId}", "generate_session", "onboarding"
        )) {
            assertTrue("$route opens for an anonymous device", routeOpensFor(route, AuthKind.ANONYMOUS))
        }
    }

    @Test
    fun `the datacollect flavor reads every device as an account - nothing is closed there`() {
        for (kind in AuthKind.values()) assertEquals(AuthKind.REGISTERED, effectiveAuthKind(requireSubscription = false, actual = kind))
        for (kind in AuthKind.values()) assertEquals(kind, effectiveAuthKind(requireSubscription = true, actual = kind))
    }
}
