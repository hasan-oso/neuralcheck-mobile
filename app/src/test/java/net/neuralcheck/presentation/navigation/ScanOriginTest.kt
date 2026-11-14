package net.neuralcheck.presentation.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * D3 (batch 2, item 2): «الرجوع إلى نقطة بداية الرسم أيّاً كانت (login أو home)». The route the scan funnel is popped down to is the graph's start when it is
 * on the back stack, else `home`. The call sites (the scan screen's completion and the two leave flows) are pinned by `SessionScreensSourceShapeTest`; the
 * NavController itself is not built here (no navigation-testing dependency, and none was asked for).
 */
class ScanOriginTest {

    @Test
    fun `a customer device starts at login and the funnel is popped down to login`() {
        assertEquals("login", resolveScanOriginRoute(graphStartRoute = "login", startIsOnBackStack = true))
    }

    @Test
    fun `a signed-in device starts at home and the funnel is popped down to home`() {
        assertEquals("home", resolveScanOriginRoute(graphStartRoute = "home", startIsOnBackStack = true))
    }

    @Test
    fun `a device that started at login and signed in has no login on its stack - its bottom is home`() {
        assertEquals("home", resolveScanOriginRoute(graphStartRoute = "login", startIsOnBackStack = false))
    }

    @Test
    fun `a graph with no start route falls back to home, never to a made-up route`() {
        assertEquals("home", resolveScanOriginRoute(graphStartRoute = null, startIsOnBackStack = false))
        assertEquals("home", resolveScanOriginRoute(graphStartRoute = null, startIsOnBackStack = true))
        assertEquals(FALLBACK_ORIGIN_ROUTE, resolveScanOriginRoute(null, false))
    }
}
