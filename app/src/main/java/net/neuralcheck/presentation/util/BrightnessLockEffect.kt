package net.neuralcheck.presentation.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.Window
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import net.neuralcheck.service.stress.BrightnessLock

/**
 * Holds the screen's brightness constant, by the activity window's `screenBrightness` attribute, for as long as this composable is in the composition — and says what came
 * of it through [report] (once, on entering): fieldsVersion 71, row A-P6-13; contract docs/FV68_CONTRACT.md section 7-ي ف-05 (N71-08).
 *
 * What is held is [BrightnessLock.VALUE] — which is NULL today («معلّق: مواصفة غائبة»: no value is decided anywhere), so nothing is asked of the window and [report] says
 * `not_attempted:value_not_decided`. The scan service drains that report into the footer's `brightnessLock`. When a value is decided the same path applies it, and the
 * window's previous attribute (the system's choice) is given back on leaving.
 *
 * Verified by JVM tests only through [BrightnessLock.lock] (the decisions); that a window attribute holds on One UI is a field-round item (rule 19).
 */
@Composable
fun BrightnessLockEffect(report: (BrightnessLock.Result) -> Unit) {
    val context = LocalContext.current
    DisposableEffect(context) {
        val window = context.findBrightnessWindow()
        var previous = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        val result = BrightnessLock.lock(BrightnessLock.VALUE) { value ->
            val w = window ?: throw IllegalStateException("no activity window to hold the brightness on")
            val attributes = w.attributes
            previous = attributes.screenBrightness
            attributes.screenBrightness = value
            w.attributes = attributes
        }
        report(result)
        onDispose {
            if (result.state == BrightnessLock.States.APPLIED) {
                window?.let { w ->
                    val attributes = w.attributes
                    attributes.screenBrightness = previous
                    w.attributes = attributes
                }
            }
        }
    }
}

/** Compose's LocalContext can be a ContextWrapper rather than the Activity itself (as in [KeepScreenOn]): unwrap until the activity's window is found. */
private fun Context.findBrightnessWindow(): Window? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c.window
        c = c.baseContext
    }
    return null
}
