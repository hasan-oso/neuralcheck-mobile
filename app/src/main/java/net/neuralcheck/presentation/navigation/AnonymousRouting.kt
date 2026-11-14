package net.neuralcheck.presentation.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import net.neuralcheck.data.auth.AuthKind

/**
 * Where a customer's phone goes - A-SUB wave 1, user decision 1: «P1» (round-1 report section 1.5, options table).
 *
 * The customer's phone is a device that joined a shop's session by code and was signed in ANONYMOUSLY ([AuthKind.ANONYMOUS]; the one reading is `AuthManager.kindOf`). It is not an account:
 * it has no plan, no purchase, and no business seeing the previous visitor's scan. Before this change it was treated like any signed-in user: reopening the app started at `home`
 * («مرحباً زائر ... 0 فحص متبقي»), and «فحص جديد» on the report pushed `home` over the report - a dead end, with no way to type the next code except by signing out.
 *
 * What P1 says, and where each part lives (all of it reads [AuthKind], from the one source):
 *  1. a device with an anonymous user STARTS at `login` (it has the «لدي كود فحص» button) - [startDestinationFor];
 *  2. the report's «فحص جديد» takes an anonymous device to the code-entry screen, not to `home` - [newScanRouteFor] and [startNewScan];
 *  3. `home`, the profile, the history and the account screens do not open for an anonymous device - [ROUTES_CLOSED_TO_ANONYMOUS] and [ClosedToAnonymous]: the graph wraps each of them,
 *     so no path (a stale back stack, a deep link, a future button) shows one.
 * The login screen does not forward an anonymous device to `home` either (`LoginViewModel`), or step 1 would loop.
 *
 * The datacollect flavor has no account flow at all (`REQUIRE_SUBSCRIPTION` is false): it always starts at `home` and nothing is closed there ([effectiveAuthKind]).
 *
 * Pure functions on purpose: the NavController cannot be built in a plain test (`ScanOriginTest`), so the decisions are values the tests read, and the one graph file
 * ([NeuralCheckNavHost]) only applies them (`AnonymousRoutingSourceShapeTest` pins that it does).
 */
internal const val ROUTE_LOGIN = "login"
internal const val ROUTE_HOME = "home"
internal const val ROUTE_JOIN_SESSION = "join_session"

/**
 * The routes an anonymous device never sees: the signed-in home and what hangs off it. Written as the graph's route patterns (the strings `composable(...)` is given), so the graph and this
 * set cannot drift unseen (the source-shape test compares them).
 */
internal val ROUTES_CLOSED_TO_ANONYMOUS: Set<String> = setOf(
    "home",
    "history",
    "history_select/{scanType}",
    "settings",
    "profile",
    "subscription",
    "shop_setup",
    "payment/{plan}",
    "scan_history",
    "payment_history"
)

/** The kind the routing decisions read: the real one in the launch flavor, [AuthKind.REGISTERED] (nothing restricted) where the app has no account flow. */
internal fun effectiveAuthKind(requireSubscription: Boolean, actual: AuthKind): AuthKind =
    if (requireSubscription) actual else AuthKind.REGISTERED

/**
 * The graph's start destination (step 1 above). The launch flavor starts at `login` for a device with no user and for one with an anonymous user, at `home` for an account; the
 * datacollect flavor always starts at `home`.
 */
internal fun startDestinationFor(requireSubscription: Boolean, kind: AuthKind): String =
    if (!requireSubscription) ROUTE_HOME
    else when (kind) {
        AuthKind.NONE, AuthKind.ANONYMOUS -> ROUTE_LOGIN
        AuthKind.REGISTERED -> ROUTE_HOME
    }

/** Where the report's «فحص جديد» goes (step 2): the code-entry screen for an anonymous device, `home` for everyone else. */
internal fun newScanRouteFor(kind: AuthKind): String =
    when (kind) {
        AuthKind.ANONYMOUS -> ROUTE_JOIN_SESSION
        AuthKind.NONE, AuthKind.REGISTERED -> ROUTE_HOME
    }

/** Whether [route] (a route pattern of the graph) opens for [kind] (step 3). */
internal fun routeOpensFor(route: String, kind: AuthKind): Boolean =
    !(kind == AuthKind.ANONYMOUS && route in ROUTES_CLOSED_TO_ANONYMOUS)

/**
 * The report's «فحص جديد» (step 2). An account goes to its `home` exactly as before (`popUpTo("home") { inclusive = false }` keeps the Home that is already on the stack, so its stats show
 * the scan just finished). An anonymous device has no `home`: the funnel above the graph's start (`login`) is removed - the report included - and the code-entry screen goes on top, so Back
 * from it reaches `login`, never the previous visitor's report.
 */
internal fun NavController.startNewScan(kind: AuthKind) {
    val target = newScanRouteFor(kind)
    if (kind == AuthKind.ANONYMOUS) {
        navigate(target) {
            popUpTo(scanOriginRoute()) { inclusive = false }
            launchSingleTop = true
        }
    } else {
        navigate(target) {
            popUpTo(ROUTE_HOME) { inclusive = false }
            launchSingleTop = true
        }
    }
}

/**
 * Wraps a route of [ROUTES_CLOSED_TO_ANONYMOUS] in the graph (step 3). For an anonymous device it shows nothing and sends the device to `login` as the only screen of a fresh stack
 * (the whole graph is popped, so no closed screen stays underneath); for anyone else it shows [content] as it was.
 */
@Composable
internal fun ClosedToAnonymous(navController: NavController, kind: AuthKind, content: @Composable () -> Unit) {
    if (kind == AuthKind.ANONYMOUS) {
        LaunchedEffect(Unit) {
            navController.navigate(ROUTE_LOGIN) {
                popUpTo(navController.graph.id) { inclusive = true }
                launchSingleTop = true
            }
        }
        Box(Modifier)
    } else {
        content()
    }
}
