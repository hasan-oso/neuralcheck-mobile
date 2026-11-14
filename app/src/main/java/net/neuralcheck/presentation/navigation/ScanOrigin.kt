package net.neuralcheck.presentation.navigation

import androidx.navigation.NavController

/**
 * Where the scan funnel started — batch 2, item 2, decision D3 (user, 2026-10-15, recommendation adopted): «الرجوع إلى نقطة بداية الرسم أيّاً كانت (login أو
 * home؛ NeuralCheckNavigation.kt:19-23). بعد المغادرة: إليها. وبعد الاكتمال: التقرير كاليوم، وما تحته يُزال حتى البداية. لا مسار جديد يُخترع.»
 *
 * The graph's start destination is `login` for a launch-flavor device with no signed-in user (the customer device) and `home` otherwise
 * (`NeuralCheckNavHost`). The funnel (`join_session` -> `qr_scanner` -> `pre_scan` -> `active_scan`) sits on top of that start; leaving, or finishing and
 * moving on to the report, removes it down to it. Before this change the scan's completion popped up to the literal `home`, which on a customer device (start =
 * `login`) is not on the back stack: nothing was removed, and Back from the report walked back through the whole funnel.
 *
 * One case needs a rule rather than the graph's start: a device that STARTED at `login` and signed in has no `login` on its stack any more (the login screen pops itself,
 * `popUpTo("login") { inclusive = true }`) and its real bottom is `home`. [resolveScanOriginRoute] says: the graph's start when it is on the back stack, else `home`.
 */
internal const val FALLBACK_ORIGIN_ROUTE = "home"

/** The pure rule: the graph's start route when it is on the back stack, else [FALLBACK_ORIGIN_ROUTE]. */
internal fun resolveScanOriginRoute(graphStartRoute: String?, startIsOnBackStack: Boolean): String =
    if (graphStartRoute != null && startIsOnBackStack) graphStartRoute else FALLBACK_ORIGIN_ROUTE

/** The route the scan funnel is popped down to: see the class doc above. */
internal fun NavController.scanOriginRoute(): String {
    val start = graph.startDestinationRoute
    val onStack = start != null && runCatching { getBackStackEntry(start) }.isSuccess
    return resolveScanOriginRoute(start, onStack)
}

/** Leaves the funnel: removes everything above the origin (the origin stays). */
internal fun NavController.leaveToScanOrigin() {
    popBackStack(scanOriginRoute(), inclusive = false)
}
