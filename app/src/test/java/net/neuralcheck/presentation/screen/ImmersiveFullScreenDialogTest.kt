package net.neuralcheck.presentation.screen

import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.ComponentDialog
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog
import java.io.File

/**
 * The host of the full-screen dialogs (M-02's grid, M-01's colours), as far as a unit test can see it: the window it asks the platform for, its lifecycle, its back, and the source shape that keeps
 * the cause of the device round's defect out (user decision 2026-10-21 — «الحوار بملء الشاشة»). What a unit test CANNOT see, and this file does not claim: that a real window
 * manager gives that window the whole display with the bars hidden, on every OEM — a device item of the field round (rule 19). Robolectric has no system decorations, so it cannot
 * even reproduce the defect (Compose's `Dialog` measures against `screenHeightDp`, which equals the display here): the guard against it is the source-shape test below and the
 * cause is quoted in `ImmersiveFullScreenDialog`'s note (`AndroidDialog.android.kt:230-266`, ui-android 1.6.6).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35]) // = the app's targetSdk, as ManualUnavailableLineLayoutTest
class ImmersiveFullScreenDialogTest {

    @get:Rule(order = 0) val registerActivity = ManualUnavailableLineLayoutTest.RegisterComposeActivity()
    @get:Rule(order = 1) val rule = createAndroidComposeRule<ComponentActivity>()

    private var open by mutableStateOf(true)
    private var backs = 0

    private fun showDialog() {
        rule.setContent {
            if (open) ImmersiveFullScreenDialog(onBack = { backs++ }) { Box(Modifier.fillMaxSize().testTag("content")) }
        }
        rule.waitForIdle()
    }

    @Test
    fun `the dialog's window is asked for the whole display - match parent, no limits, in screen, no dim, black, the cut-out used, the decor not fitting`() {
        showDialog()
        val dialog = ShadowDialog.getLatestDialog() as ComponentDialog
        assertTrue("the dialog is showing", dialog.isShowing)
        val window = dialog.window!!
        val lp = window.attributes
        assertEquals("width", ViewGroup.LayoutParams.MATCH_PARENT, lp.width)
        assertEquals("height", ViewGroup.LayoutParams.MATCH_PARENT, lp.height)
        assertTrue("the window may extend past the limits the system sets", lp.flags and WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS != 0)
        assertTrue("laid out in the screen", lp.flags and WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN != 0)
        assertEquals("nothing dimmed behind it: it covers it", 0, lp.flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        assertEquals("the cut-out is used on every edge from API 30 (this runs on 35)", WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS, lp.layoutInDisplayCutoutMode)
        val background = window.decorView.background
        assertTrue("the window's own background is opaque black: nothing of the screen behind can show through a gap", background is ColorDrawable && (background as ColorDrawable).color == AndroidColor.BLACK)
        assertEquals("transparent status bar", AndroidColor.TRANSPARENT, window.statusBarColor)
        assertEquals("transparent navigation bar", AndroidColor.TRANSPARENT, window.navigationBarColor)
        assertFalse("a touch outside is not a way to close it", shadowOf(dialog).isCancelableOnTouchOutside)
    }

    @Test
    fun `the content is composed inside the dialog's window`() {
        showDialog()
        rule.onNodeWithTag("content").assertExists()
        val dialog = ShadowDialog.getLatestDialog() as ComponentDialog
        val decor = dialog.window!!.decorView as ViewGroup
        assertTrue("the dialog's decor holds the compose view as its content, filling it", decor.childCount > 0)
    }

    @Test
    fun `back is handed to the caller and does not close the dialog by itself - the caller decides what a dismissal records`() {
        showDialog()
        val dialog = ShadowDialog.getLatestDialog() as ComponentDialog
        dialog.onBackPressedDispatcher.onBackPressed()
        assertEquals("one back, one call", 1, backs)
        assertTrue("the dialog is still showing: the caller closes it", dialog.isShowing)
        dialog.onBackPressedDispatcher.onBackPressed()
        assertEquals(2, backs)
    }

    @Test
    fun `the dialog goes when its composable leaves the composition`() {
        showDialog()
        val dialog = ShadowDialog.getLatestDialog() as ComponentDialog
        assertTrue(dialog.isShowing)
        open = false
        rule.waitForIdle()
        assertFalse("the window goes with the composable", dialog.isShowing)
    }

    @Test
    fun `the cut-out mode by platform - the short edges on 28 and 29, every edge from 30`() {
        for (sdk in 28..29) assertEquals("API $sdk", WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES, ImmersiveWindow.cutoutMode(sdk))
        for (sdk in 30..36) assertEquals("API $sdk", WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS, ImmersiveWindow.cutoutMode(sdk))
    }

    // ---- the source shape: the cause of the device round's defect stays out of the M-02 dialog ----

    private val screenDir: File = listOf("app/src/main/java", "../app/src/main/java")
        .map { File(it, "net/neuralcheck/presentation/screen") }
        .firstOrNull { it.isDirectory }
        ?: error("could not locate app/src/main/java from ${File(".").absolutePath}")

    private fun stripComments(source: String): String =
        source.replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), " ").lines().joinToString("\n") { it.substringBefore("//") }

    @Test
    fun `the full-screen dialogs - M-01's colours and M-02's grid - are not Compose Dialogs, they go through ImmersiveFullScreenDialog, and the host is a ComponentDialog in a full-screen theme`() {
        val dialogs = stripComments(File(screenDir, "ManualTestDialogs.kt").readText())
        val m01 = dialogs.substringAfter("fun DeadPixelTestDialog(").substringBefore("fun TouchGridTestDialog(")
        val m02 = dialogs.substringAfter("fun TouchGridTestDialog(").substringBefore("fun AudioToneTestDialog(")
        assertTrue("found the M-01 dialog", m01.contains("onResult") && m01.contains("DeadPixelRunContent"))
        assertTrue("found the M-02 dialog", m02.contains("onResult") && m02.contains("TouchGridRecorder"))
        for ((name, body) in listOf("M-01" to m01, "M-02" to m02)) {
            assertEquals("$name hosts its content in ImmersiveFullScreenDialog, once", 1, Regex("""\bImmersiveFullScreenDialog\(""").findAll(body).count())
            assertFalse("$name: no Compose Dialog( - its DialogLayout sizes the window to the content measured without the system's decorations", Regex("""(?<![A-Za-z])Dialog\(""").containsMatchIn(body))
            assertFalse("$name: no DialogProperties", body.contains("DialogProperties"))
            assertFalse("$name: no usePlatformDefaultWidth", body.contains("usePlatformDefaultWidth"))
        }

        val host = stripComments(File(screenDir, "ImmersiveFullScreenDialog.kt").readText())
        assertTrue("a ComponentDialog", host.contains("ComponentDialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen)"))
        assertFalse("not Compose's Dialog", host.contains("androidx.compose.ui.window.Dialog"))
        for (needed in listOf("MATCH_PARENT", "FLAG_LAYOUT_NO_LIMITS", "FLAG_LAYOUT_IN_SCREEN", "setDecorFitsSystemWindows(window, false)", "BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE", "Type.systemBars()", "layoutInDisplayCutoutMode")) {
            assertTrue("the window settings name $needed", host.contains(needed))
        }
    }

    @Test
    fun `no source of the app holds Compose's Dialog with DialogProperties - the pattern that left the full-screen dialogs short of the display cannot come back`() {
        val javaRoot = File(screenDir, "../../../..").canonicalFile // app/src/main/java
        val kotlinFiles = javaRoot.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        assertTrue("found the sources", kotlinFiles.size > 100)
        // a positive control, so a path or a stripping error cannot make the scan blind: the host's own file does name the pattern in its note (a comment)
        assertTrue("the note in the host's file names the pattern (control)", File(screenDir, "ImmersiveFullScreenDialog.kt").readText().contains("usePlatformDefaultWidth"))
        val offenders = kotlinFiles.filter { file ->
            val code = stripComments(file.readText())
            code.contains("usePlatformDefaultWidth") || Regex("""\bDialogProperties\b""").containsMatchIn(code) ||
                Regex("""import\s+androidx\.compose\.ui\.window\.Dialog\s*$""", RegexOption.MULTILINE).containsMatchIn(code)
        }.map { it.name }
        assertEquals("sources that use Compose's Dialog (DialogProperties / usePlatformDefaultWidth / import ...window.Dialog) - a full-screen dialog is an ImmersiveFullScreenDialog", emptyList<String>(), offenders)
    }

    @Test
    fun `the run's content has no bar - the surface fills the content and nothing weighs against it`() {
        val content = stripComments(File(screenDir, "TouchGridRunContent.kt").readText())
        // the arguments of the AndroidView( call itself: from its opening parenthesis to the matching one
        val open = content.indexOf("AndroidView(") + "AndroidView".length
        check(open >= "AndroidView".length) { "cannot find the AndroidView call" }
        var depth = 0
        var close = open
        for (i in open until content.length) {
            if (content[i] == '(') depth++
            if (content[i] == ')') { depth--; if (depth == 0) { close = i; break } }
        }
        val surfaceCall = content.substring(open, close + 1)
        assertTrue("the surface fills the content: `$surfaceCall`", Regex("""modifier = Modifier\.fillMaxSize\(\)""").containsMatchIn(surfaceCall))
        assertFalse("and is given no other size", Regex("""\.(height|width|size|fillMaxHeight|fillMaxWidth|requiredSize|weight)\(""").containsMatchIn(surfaceCall))
        // the text block's row gives its title the room the count leaves (`weight(1f, fill = false)`): that is the one weight() the file may have, and it is not a region of the screen
        val textBlockAt = content.indexOf("private fun TextBlock(")
        check(textBlockAt >= 0) { "cannot find the text block" }
        val textBlockEnd = content.indexOf("\n}\n", textBlockAt)
        val outsideTextBlock = content.substring(0, textBlockAt) + content.substring(textBlockEnd)
        assertFalse("no weighted bar region: the old layout gave the surface weight(1f) and a bar below it", outsideTextBlock.contains(".weight("))
        assertEquals("the text block's only weight is the title's slot in its row", 1, Regex(Regex.escape(".weight(1f, fill = false)")).findAll(content.substring(textBlockAt, textBlockEnd)).count())
        assertFalse("no fractional height for the surface", content.contains("fillMaxHeight("))
    }
}
