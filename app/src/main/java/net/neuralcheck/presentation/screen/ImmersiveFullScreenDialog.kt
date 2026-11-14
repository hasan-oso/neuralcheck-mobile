package net.neuralcheck.presentation.screen

import android.content.Context
import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import androidx.activity.ComponentDialog
import androidx.activity.addCallback
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * A dialog that is the WHOLE screen — a window of its own, edge to edge, over the display cut-out where the system allows it, with the status and
 * navigation bars hidden (a swipe from an edge shows them for a moment) — and whose [content] fills that window exactly. Its users: M-02's grid
 * (`TouchGridTestDialog`) and, since the user's decision of 2026-10-21 («نعم، أصلح M-01 بالمضيف الجديد»), M-01's colour phase (`DeadPixelTestDialog`).
 *
 * Why it is not Compose's `Dialog(DialogProperties(usePlatformDefaultWidth = false))` (the grid of M-02 was one until the user's device round on the APK of f1a38be,
 * 2026-10-21: «شريط العنوان ظاهر، والشبكة تنتهي عند ~83% من الارتفاع، ثم شريط أسود…، ثم شريط يظهر منه زرّ «متابعة إلى التقرير» من الشاشة خلف الحوار»):
 * Compose's `DialogLayout` measures its content against `Configuration.screenWidthDp x screenHeightDp` — the app's available area WITHOUT the system's
 * decorations — and then sets the window to the size its content measured, `window.setLayout(child.measuredWidth, child.measuredHeight)`
 * (androidx.compose.ui:ui-android 1.6.6, `AndroidDialog.android.kt:230-266` in the sources jar: `internalOnMeasure` :230-244, `internalOnLayout` :246-254,
 * `displayWidth` / `displayHeight` :256-266). The window, a dialog's, is centred: the screen behind shows above it and below it. Whatever the window
 * asks for before that (`MATCH_PARENT`, immersive mode, the cut-out mode — M-02's dialog asked for all three) is overridden by that size on the first layout. This
 * dialog is a plain `androidx.activity.ComponentDialog` (the base class Compose's own wrapper has) with a [ComposeView] as its content, in a full-screen
 * theme: nothing measures the content against anything but the window, and the window is the display.
 *
 * What it does to the window ([ImmersiveWindow.apply]), once at creation and again when the dialog is shown: black background, `MATCH_PARENT` both ways,
 * `FLAG_LAYOUT_NO_LIMITS` and `FLAG_LAYOUT_IN_SCREEN`, no dim, the decor not fitting the system windows, transparent bars that stay hidden, the cut-out mode
 * `ALWAYS` (API 30+) or `SHORT_EDGES` (API 28-29, minSdk), the system bars hidden with `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`. Nothing of the activity's own
 * window is touched, so nothing of it has to be given back when the dialog closes: the dialog's window goes with the dialog (the one thing the caller sets on the
 * activity, the orientation lock, is the caller's own to restore). Touches: the window covers the display, so nothing behind it is drawn or reached; a touch
 * outside it is not possible, and [setCanceledOnTouchOutside] is false anyway.
 *
 * What is NOT proved by the unit tests (no device here): that every OEM's window manager gives the dialog the whole display with the bars hidden (a device item of
 * the field round, rule 19).
 *
 * @param onBack the system's back (button, gesture): the run is dismissed — the caller records what a dismissal records.
 */
@Composable
internal fun ImmersiveFullScreenDialog(
    onBack: () -> Unit,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val parentComposition = rememberCompositionContext()
    val currentContent by rememberUpdatedState(content)
    val currentOnBack by rememberUpdatedState(onBack)
    val layoutDirection = LocalLayoutDirection.current

    val host = remember { ImmersiveDialogHost(context, parentComposition, { currentOnBack() }) { currentContent() } }
    SideEffect { host.setLayoutDirection(layoutDirection) }
    DisposableEffect(host) {
        host.show()
        onDispose { host.dismiss() }
    }
}

/** The dialog, its window and its [ComposeView]. */
internal class ImmersiveDialogHost(
    context: Context,
    parentComposition: CompositionContext,
    onBack: () -> Unit,
    content: @Composable () -> Unit
) {
    private val dialog = ComponentDialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
    private val composeView = ComposeView(context)

    init {
        dialog.setCanceledOnTouchOutside(false)
        // The system's back: always consumed here (as Compose's own dialog does), so the dismissal is the caller's to make.
        dialog.onBackPressedDispatcher.addCallback(dialog) { onBack() }
        composeView.setParentCompositionContext(parentComposition)
        composeView.setContent(content)
        dialog.setContentView(composeView, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        dialog.window?.let { ImmersiveWindow.apply(it) }
        dialog.setOnShowListener { dialog.window?.let { ImmersiveWindow.apply(it) } }
    }

    fun setLayoutDirection(direction: LayoutDirection) {
        composeView.layoutDirection = if (direction == LayoutDirection.Rtl) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
    }

    fun show() {
        if (!dialog.isShowing) dialog.show()
    }

    fun dismiss() {
        composeView.disposeComposition()
        if (dialog.isShowing) dialog.dismiss()
    }
}

/** The window settings of [ImmersiveFullScreenDialog]; one place, idempotent. */
internal object ImmersiveWindow {

    private const val TAG = "NeuralCheck.ImmersiveWindow"

    @Suppress("DEPRECATION") // status/navigation bar colours and the contrast flags: deprecated in API 35, still how a transparent bar is asked for on 28-34
    fun apply(window: Window) {
        window.setBackgroundDrawable(ColorDrawable(AndroidColor.BLACK))
        window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = AndroidColor.TRANSPARENT
        window.navigationBarColor = AndroidColor.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }
        window.attributes = window.attributes.also { it.layoutInDisplayCutoutMode = cutoutMode(Build.VERSION.SDK_INT) }
        try {
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } catch (e: Exception) {
            // never a reason to lose the run: the window is still the display, only the bars may show
            Log.w(TAG, "the system bars could not be hidden: ${e.javaClass.simpleName}")
        }
    }

    /**
     * The display cut-out mode for the platform at [sdkInt]: `ALWAYS` from API 30 (the window extends under the cut-out on every edge), `SHORT_EDGES` below it
     * (API 28 and 29: the short edges — the orientation is locked while the grid is open, so the camera's edge is one of them on a phone held upright).
     */
    fun cutoutMode(sdkInt: Int): Int =
        if (sdkInt >= Build.VERSION_CODES.R) WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        else WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
}
