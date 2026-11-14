package net.neuralcheck.presentation.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.Window
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

/**
 * Holds `FLAG_KEEP_SCREEN_ON` for as long as this composable is in the
 * composition and [active] is true. Releases it on disposal.
 *
 * ── Why a reference count and not a plain add/clear ──
 *
 * The flag lives on the ACTIVITY window, which every screen shares, while
 * the add/clear calls live in per-screen effects. During a Compose
 * Navigation transition the incoming screen composes BEFORE the outgoing
 * screen is disposed, so a naive implementation runs:
 *
 *     ManualTests.add()  →  ActiveScan.onDispose.clear()
 *
 * and the screen goes dark on the screen that just asked for it. The
 * counter makes acquire/release order-independent: the flag is cleared
 * only when the LAST holder releases it.
 *
 * `addFlags` is idempotent, so re-acquiring on a fresh window (activity
 * recreation after a configuration change) re-applies it correctly.
 *
 * Passing `active = false` holds nothing — used by the report screen,
 * which keeps the screen awake only while the analysis is still running.
 */
@Composable
fun KeepScreenOn(active: Boolean = true) {
    val context = LocalContext.current
    DisposableEffect(context, active) {
        if (!active) {
            onDispose { }
        } else {
            val window = context.findActivityWindow()
            ScreenWakeCounter.acquire(window)
            onDispose { ScreenWakeCounter.release(window) }
        }
    }
}

/**
 * Compose's LocalContext can be a ContextWrapper rather than the Activity
 * itself, so a direct `as? Activity` cast silently returns null and the
 * flag is never applied. Unwrap until we find it.
 */
private fun Context.findActivityWindow(): Window? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c.window
        c = c.baseContext
    }
    return null
}

/**
 * Process-wide holder count for FLAG_KEEP_SCREEN_ON. Single-threaded by
 * contract — every call comes from a Compose DisposableEffect, which runs
 * on the main thread — so a plain Int is sufficient and avoids implying
 * thread-safety this doesn't need.
 */
internal object ScreenWakeCounter {

    private var holders: Int = 0

    /** Visible for tests: how many screens currently want the screen awake. */
    internal fun holderCount(): Int = holders

    fun acquire(window: Window?) {
        holders++
        // Always (re-)apply: addFlags is idempotent on the same window, and
        // after an activity recreation this is what re-applies the flag to
        // the NEW window while the count is already > 0.
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    fun release(window: Window?) {
        // coerceAtLeast guards the one asymmetric case: a release whose
        // matching acquire happened against a previous window (activity
        // recreated mid-flow). Going negative would leave the flag stuck on
        // for the rest of the process.
        holders = (holders - 1).coerceAtLeast(0)
        if (holders == 0) {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    /** Test-only reset so cases start from a known state. */
    internal fun resetForTest() { holders = 0 }
}
