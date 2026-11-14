package net.neuralcheck.presentation.screen

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.ComponentDialog
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import net.neuralcheck.R
import net.neuralcheck.presentation.theme.NeuralCheckTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog
import kotlin.math.ceil
import kotlin.math.floor

/**
 * M-01's dead-pixel run as the user sees it: the REAL `DeadPixelTestDialog` — hosted by the real `ImmersiveFullScreenDialog` (a real `ComponentDialog` with a real window; nothing
 * stands in for the host, rule 37) — DRAWN into a bitmap, pixel by pixel, at four screen sizes, and driven with real touch input. User decision 2026-10-21 («نعم، أصلح M-01
 * بالمضيف الجديد»): until then the colour was in Compose's `Dialog(DialogProperties(usePlatformDefaultWidth = false))`, whose window stopped short of the display's edges — and a
 * dead pixel at an edge was never looked at.
 *
 * Proved at every size, for each of the five colours (the expected colours are written here as literals, not read from `DeadPixelCycle`: a checker must not read what it checks):
 *  (أ) the window the colour is in IS the display, to the pixel, and every pixel of it outside the hint text's own box is the colour — the four corners and every edge included;
 *  (ب) the run goes through the five colours in their order, one tap one step (a tap on a corner, on the hint, at the far corner, in the middle), and after the fifth the
 *      question comes up and the colour's window is gone;
 *  (د) nothing in the colour phase takes the touch but the colour itself: no node has a click action, and a tap on the hint text advances the run like any other.
 * And at one size: (ج) every outcome the question can write — «pass», «fail» — and its dismissal write what they wrote before; back is the caller's at each of the five colours.
 *
 * What a unit test CANNOT see, and this file does not claim: that a real window manager gives that window the whole display with the bars hidden on every OEM (Robolectric has no
 * system decorations — it could not even show the old defect). A device item of the field round (rule 19).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35]) // = the app's targetSdk, as ManualUnavailableLineLayoutTest
class DeadPixelDialogRenderedTest {

    @get:Rule(order = 0) val registerActivity = ManualUnavailableLineLayoutTest.RegisterComposeActivity()
    @get:Rule(order = 1) val rule = createAndroidComposeRule<ComponentActivity>()

    private val results = mutableListOf<String>()
    private var dismissals = 0

    /** red, green, blue, white, black — the order the run is specified in, as opaque ARGB ints. */
    private val expectedColours = intArrayOf(0xFFFF0000.toInt(), 0xFF00FF00.toInt(), 0xFF0000FF.toInt(), 0xFFFFFFFF.toInt(), 0xFF000000.toInt())
    private val colourNames = listOf("red", "green", "blue", "white", "black")

    private fun show() {
        rule.setContent { NeuralCheckTheme { DeadPixelTestDialog(onResult = { results += it }, onDismiss = { dismissals++ }) } }
        rule.waitForIdle()
    }

    private fun colourDialog(): ComponentDialog = ShadowDialog.getLatestDialog() as ComponentDialog

    private fun hintBox() = rule.onNodeWithTag(DeadPixelRunTags.HINT).fetchSemanticsNode().boundsInRoot

    /** Draws the colour dialog's whole window — the decor, the real view tree — and returns its pixels with its size. */
    private fun drawWindow(dialog: ComponentDialog): Triple<IntArray, Int, Int> {
        val decor = dialog.window!!.decorView
        val w = decor.width
        val h = decor.height
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        decor.draw(Canvas(bitmap))
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        return Triple(pixels, w, h)
    }

    private fun hex(argb: Int) = "#" + Integer.toHexString(argb)

    /** (أ) for one colour. */
    private fun assertWindowIsTheColour(name: String, step: Int) {
        val dialog = colourDialog()
        assertTrue("$name/${colourNames[step]}: the colour dialog is showing", dialog.isShowing)
        val (pixels, w, h) = drawWindow(dialog)
        val metrics = rule.activity.resources.displayMetrics
        assertEquals("$name/${colourNames[step]}: the window is the display's width", metrics.widthPixels, w)
        assertEquals("$name/${colourNames[step]}: the window is the display's height", metrics.heightPixels, h)

        val surface = rule.onNodeWithTag(DeadPixelRunTags.SURFACE).fetchSemanticsNode()
        assertEquals("$name/${colourNames[step]}: the colour's box is the window's: left", 0f, surface.boundsInRoot.left, 0f)
        assertEquals("$name/${colourNames[step]}: top", 0f, surface.boundsInRoot.top, 0f)
        assertEquals("$name/${colourNames[step]}: right", w.toFloat(), surface.boundsInRoot.right, 0f)
        assertEquals("$name/${colourNames[step]}: bottom", h.toFloat(), surface.boundsInRoot.bottom, 0f)

        val colour = expectedColours[step]
        // the hint text is the one thing drawn over the colour: the pixels inside ITS box may differ, no other pixel of the window may
        val hint = hintBox()
        val hl = floor(hint.left).toInt(); val hr = ceil(hint.right).toInt()
        val ht = floor(hint.top).toInt(); val hb = ceil(hint.bottom).toInt()
        assertTrue("$name: the hint is a small box at the window's bottom, not a cover (${hint.width}x${hint.height} in $w x $h)", hint.width < w * 0.9f && hint.height < h * 0.1f && hint.top > h * 0.8f)
        var outsideWrong = 0
        var firstWrong = ""
        for (y in 0 until h) {
            val row = y * w
            val inHintRows = y in ht until hb
            for (x in 0 until w) {
                if (pixels[row + x] != colour && !(inHintRows && x in hl until hr)) {
                    if (outsideWrong == 0) firstWrong = "x=$x y=$y is ${hex(pixels[row + x])}"
                    outsideWrong++
                }
            }
        }
        assertEquals("$name/${colourNames[step]}: pixels of the window outside the hint that are not ${hex(colour)} (first: $firstWrong)", 0, outsideWrong)
        // the corners and the middle of each edge, by name (they are inside the loop above; this says which ones a failure would be)
        for ((label, x, y) in listOf(
            Triple("top-left", 0, 0), Triple("top-right", w - 1, 0), Triple("bottom-left", 0, h - 1), Triple("bottom-right", w - 1, h - 1),
            Triple("top edge", w / 2, 0), Triple("left edge", 0, h / 2), Triple("right edge", w - 1, h / 2)
        )) {
            assertEquals("$name/${colourNames[step]}: the $label pixel", colour, pixels[y * w + x])
        }
    }

    private fun tapSurfaceAt(x: Float, y: Float) {
        rule.onNodeWithTag(DeadPixelRunTags.SURFACE).performTouchInput { click(Offset(x, y)) }
        rule.waitForIdle()
    }

    private fun checkScreen(name: String) {
        show()
        val first = colourDialog()
        // the window the colour runs in is the one the host asks the platform for (ImmersiveFullScreenDialogTest has the rest of its settings)
        val lp = first.window!!.attributes
        assertEquals("$name: window width", ViewGroup.LayoutParams.MATCH_PARENT, lp.width)
        assertEquals("$name: window height", ViewGroup.LayoutParams.MATCH_PARENT, lp.height)
        assertTrue("$name: no limits", lp.flags and WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS != 0)
        assertEquals("$name: the cut-out is used on every edge", WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS, lp.layoutInDisplayCutoutMode)

        // (د) nothing in the colour phase takes a touch by itself
        rule.onAllNodes(hasClickAction()).assertCountEquals(0)

        val w = first.window!!.decorView.width.toFloat()
        val h = first.window!!.decorView.height.toFloat()
        val hint = hintBox()
        // a tap at each of: the top-left corner, the hint's centre, the bottom-right corner, the middle — one tap, one step
        val taps = listOf(
            Offset(0f, 0f),
            Offset((hint.left + hint.right) / 2, (hint.top + hint.bottom) / 2),
            Offset(w - 1, h - 1),
            Offset(w / 2, h / 2)
        )
        for (step in 0..4) {
            assertWindowIsTheColour(name, step)
            assertEquals("$name/${colourNames[step]}: no answer and no dismissal while the colours go on", 0, results.size + dismissals)
            if (step < 4) {
                tapSurfaceAt(taps[step].x, taps[step].y)
                assertTrue("$name: the same colour dialog is still the one showing after tap ${step + 1}", first.isShowing)
            }
        }
        // the fifth tap (anywhere) asks the question: the colour's window is gone, the question is up, nothing is answered yet
        tapSurfaceAt(w / 3, h / 3)
        assertFalse("$name: the colour dialog is gone after the last colour", first.isShowing)
        rule.onNodeWithText(rule.activity.getString(R.string.manual_dead_pixel_title)).assertExists()
        rule.onNodeWithTag(DeadPixelRunTags.SURFACE).assertDoesNotExist()
        assertEquals("$name: asking writes nothing", 0, results.size + dismissals)
    }

    @Test
    @Config(qualifiers = "w360dp-h800dp-xxhdpi")
    fun `a 20 to 9 phone, 360 x 800 dp`() = checkScreen("360x800dp")

    @Test
    @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun `a taller 20 to 9 phone, 411 x 891 dp`() = checkScreen("411x891dp")

    @Test
    @Config(qualifiers = "w768dp-h1024dp-xhdpi")
    fun `a 4 to 3 screen, 768 x 1024 dp`() = checkScreen("768x1024dp")

    @Test
    @Config(qualifiers = "w891dp-h411dp-land-xxhdpi")
    fun `a phone held sideways, 891 x 411 dp (the run does not lock the orientation)`() = checkScreen("891x411dp-land")

    // ---- (ج) what the question writes: every value, and the dismissals ----

    private fun tapThroughTheColours() {
        show()
        repeat(5) { tapSurfaceAt(10f, 10f) }
        rule.onNodeWithText(rule.activity.getString(R.string.manual_dead_pixel_title)).assertExists()
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun `the screen is fine writes pass - once, with no dismissal`() {
        tapThroughTheColours()
        rule.onNodeWithText(rule.activity.getString(R.string.manual_screen_ok)).performClick()
        rule.waitForIdle()
        assertEquals(listOf("pass"), results)
        assertEquals(0, dismissals)
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun `there is a problem writes fail - once, with no dismissal`() {
        tapThroughTheColours()
        rule.onNodeWithText(rule.activity.getString(R.string.manual_screen_issue)).performClick()
        rule.waitForIdle()
        assertEquals(listOf("fail"), results)
        assertEquals(0, dismissals)
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun `back on the question is a dismissal and writes nothing`() {
        tapThroughTheColours()
        val question = ShadowDialog.getLatestDialog() as ComponentDialog
        assertTrue("the question is a dialog of its own, showing", question.isShowing)
        question.onBackPressedDispatcher.onBackPressed()
        rule.waitForIdle()
        assertEquals(emptyList<String>(), results)
        assertEquals(1, dismissals)
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun `back during any of the five colours is a dismissal and writes nothing - the caller closes the dialog`() {
        show()
        for (step in 0..4) {
            val dialog = colourDialog()
            dialog.onBackPressedDispatcher.onBackPressed()
            rule.waitForIdle()
            assertEquals("back at colour ${step + 1}: one dismissal more", step + 1, dismissals)
            assertEquals("back at colour ${step + 1}: nothing written", emptyList<String>(), results)
            assertTrue("back does not close the dialog by itself: the caller's callback does", dialog.isShowing)
            if (step < 4) tapSurfaceAt(10f, 10f)
        }
    }
}
