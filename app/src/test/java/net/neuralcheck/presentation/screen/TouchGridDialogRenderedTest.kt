package net.neuralcheck.presentation.screen

import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.ComponentDialog
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import net.neuralcheck.domain.manual.TouchGridPattern
import net.neuralcheck.domain.manual.TouchGridRecorder
import net.neuralcheck.presentation.theme.NeuralCheckTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog

/**
 * M-02's run as the technician meets it: the REAL `TouchGridTestDialog` — hosted by the real `ImmersiveFullScreenDialog` (a real `ComponentDialog` with a real window), its real
 * `TouchGridSurfaceView`, `TouchGridRecorder` and `TouchGridOverlay`; nothing stands in for the host or the recorder (rule 37, rule 41) — driven with real touch input, one tap per cell.
 * User decision 2026-10-21: the test is a «frame + X» (TouchGridPattern), «إغلاق تلقائي عند اكتمال الخلايا المطلوبة»; «يعمل» does not open it (the row's, not this dialog's); the technician's two
 * buttons after 15 s cover no required cell.
 *
 * The grid stands under an empty strip at the top (user order 2026-10-21, to match the system's touch test): a tap's place is the cell's middle in the VIEW's frame, `gridTopPx` lower than in the
 * recorder's own (the surface's) frame.
 *
 * Proved at two screen sizes:
 *  (أ) a tap on every cell the pattern LEAVES OUT lights nothing, counts nothing and completes nothing;
 *  (ب) a tap on each required cell, in turn, lights it; the run does not end before the LAST required cell and ends the moment it is lit — `onResult("pass", raw)` once, the raw's
 *      `endReason` `auto_complete`, every required cell touched, none untouched, and the contract's relations (wire-m02-frame-x-2026-10-21.md) on that raw as the dialog wrote it;
 *      a later touch writes nothing more;
 *  (ج) with the buttons up (after 15 s) every required cell is still reached by a tap on its middle, and the run still ends by itself at the last one — not by a button;
 *  (د) «لا يعمل» writes `fail` with the raw (`user_marked_not_working`), «لم أختبر» writes `not_tested` with it (`user_marked_not_tested`), each once, and the grid's numbers are the same as before the buttons;
 *  (هـ) back with a required cell lit is a dismissal that carries the raw, back with nothing touched is a dismissal that carries none.
 *
 * Not proved (a device item of the field round, rule 19): that a real window manager gives this window the whole display with the bars hidden, and a finger's drawing of the diagonals.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35]) // = the app's targetSdk, as ManualUnavailableLineLayoutTest
class TouchGridDialogRenderedTest {

    @get:Rule(order = 0) val registerActivity = ManualUnavailableLineLayoutTest.RegisterComposeActivity()
    @get:Rule(order = 1) val rule = createAndroidComposeRule<ComponentActivity>()

    private class Written(val status: String, val raw: Map<String, Any?>?)

    private val results = mutableListOf<Written>()
    private val dismissals = mutableListOf<Map<String, Any?>?>()

    private fun show() {
        rule.setContent { NeuralCheckTheme { TouchGridTestDialog(onResult = { s, m -> results += Written(s, m) }, onDismiss = { dismissals += it }) } }
        rule.waitForIdle()
    }

    private fun dialog(): ComponentDialog = ShadowDialog.getLatestDialog() as ComponentDialog

    private fun findSurface(v: View): TouchGridSurfaceView? {
        if (v is TouchGridSurfaceView) return v
        if (v is ViewGroup) for (i in 0 until v.childCount) findSurface(v.getChildAt(i))?.let { return it }
        return null
    }

    /** The REAL recorder the dialog made: read from the surface view the dialog's tree holds (a private field, read, never written). */
    private fun recorder(): TouchGridRecorder {
        val view = findSurface(dialog().window!!.decorView)
        assertNotNull("the dialog's window holds the touch surface", view)
        val field = TouchGridSurfaceView::class.java.getDeclaredField("recorder")
        field.isAccessible = true
        return field.get(view) as TouchGridRecorder
    }

    private fun tapCell(rec: TouchGridRecorder, index: Int) {
        val l = rec.layout!!
        val x = (index % l.cols + 0.5) * l.cellWidthPx
        val y = l.gridTopPx + (index / l.cols + 0.5) * l.cellHeightPx          // the view's own frame: the grid starts under the empty strip
        rule.onNodeWithTag(TouchGridRunTags.SURFACE).performTouchInput { down(Offset(x.toFloat(), y.toFloat())); up() }
        rule.waitForIdle()
    }

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.ints(key: String) = (this[key] as List<Number>).map { it.toInt() }

    private fun assertRelations(tag: String, raw: Map<String, Any?>) {
        val cols = (raw[TouchGridRecorder.Keys.GRID_COLS] as Number).toInt()
        val rows = (raw[TouchGridRecorder.Keys.GRID_ROWS] as Number).toInt()
        val cutout = raw.ints(TouchGridRecorder.Keys.CUTOUT_CELLS)
        val notRequired = raw.ints(TouchGridRecorder.Keys.NOT_REQUIRED_CELLS)
        val untouched = raw.ints(TouchGridRecorder.Keys.UNTOUCHED_CELLS)
        val total = (raw[TouchGridRecorder.Keys.TOTAL_CELLS] as Number).toInt()
        val touched = (raw[TouchGridRecorder.Keys.TOUCHED_CELLS] as Number).toInt()
        assertEquals("$tag: gridPattern", "FRAME_AND_DIAGONALS", raw[TouchGridRecorder.Keys.GRID_PATTERN])
        assertEquals("$tag: relation 1", emptySet<Int>(), cutout.toSet() intersect notRequired.toSet())
        assertEquals("$tag: relation 2", cols * rows - cutout.size - notRequired.size, total)
        assertEquals("$tag: relation 4", total, touched + untouched.size)
        assertTrue("$tag: relation 4 - untouched cells are required ones", untouched.none { it in cutout || it in notRequired })
        val pattern = TouchGridPattern.cells(cols, rows)
        assertEquals("$tag: the left-out cells are the grid minus the pattern minus the camera", (0 until cols * rows).filter { it !in pattern && it !in cutout }, notRequired)
        assertEquals("$tag: relation 5", raw[TouchGridRecorder.Keys.END_REASON] == TouchGridRecorder.EndReasons.AUTO_COMPLETE, untouched.isEmpty())
    }

    private fun checkAutoComplete(name: String, withButtons: Boolean) {
        show()
        val rec = recorder()
        val l = rec.layout
        assertNotNull("$name: the surface laid the grid out", l)
        l!!
        val required = (0 until l.cols * l.rows).filter { l.isRequired(it) }
        assertEquals("$name: the required cells are the pattern's less the camera's", TouchGridPattern.cells(l.cols, l.rows).size - l.cutoutCells.count { it in TouchGridPattern.cells(l.cols, l.rows) }, required.size)
        var buttons = emptyList<androidx.compose.ui.geometry.Rect>()
        if (withButtons) {
            rule.mainClock.advanceTimeBy(TouchGridRecorder.TECHNICIAN_BUTTONS_AFTER_MS + 1_000)
            rule.waitForIdle()
            buttons = listOf(TouchGridRunTags.NOT_WORKING, TouchGridRunTags.NOT_TESTED).map { rule.onNodeWithTag(it).fetchSemanticsNode().boundsInRoot }
        }
        // a free cell under a button is the button's (it takes the touch, as it is for): those are not tapped as cells
        fun underAButton(i: Int): Boolean {
            val x = ((i % l.cols + 0.5) * l.cellWidthPx).toFloat()
            val y = (l.gridTopPx + (i / l.cols + 0.5) * l.cellHeightPx).toFloat()
            return buttons.any { x >= it.left && x < it.right && y >= it.top && y < it.bottom }
        }
        val freeToTap = l.notRequiredCells.filterNot { underAButton(it) }
        assertTrue("$name: the buttons stand on free cells only - no REQUIRED cell's middle is under one", required.none { underAButton(it) })

        // (أ) every free cell not under a button: nothing happens
        for (i in freeToTap) tapCell(rec, i)
        assertEquals("$name: the free cells light nothing", 0, rec.touchedCount)
        assertFalse("$name: and complete nothing", rec.isComplete)
        assertEquals("$name: no answer yet", 0, results.size + dismissals.size)

        // (ب)/(ج) every required cell but the last: lit one by one, the run goes on
        for ((n, i) in required.withIndex()) {
            if (n == required.size - 1) break
            tapCell(rec, i)
            assertTrue("$name: cell $i lit", rec.isTouched(i))
            assertEquals("$name: ${n + 1} cells counted", n + 1, rec.touchedCount)
            assertEquals("$name: the run is not over before the last required cell", 0, results.size + dismissals.size)
        }
        tapCell(rec, required.last())
        assertEquals("$name: the last required cell ends the run by itself - one answer, no dismissal", 1, results.size)
        assertEquals(0, dismissals.size)
        val w = results.single()
        assertEquals("$name: the answer", "pass", w.status)
        val raw = w.raw
        assertNotNull("$name: the answer carries the raw", raw)
        raw!!
        assertEquals("$name: auto_complete", TouchGridRecorder.EndReasons.AUTO_COMPLETE, raw[TouchGridRecorder.Keys.END_REASON])
        assertEquals("$name: every required cell touched", required.size, (raw[TouchGridRecorder.Keys.TOUCHED_CELLS] as Number).toInt())
        assertEquals("$name: none untouched", emptyList<Int>(), raw.ints(TouchGridRecorder.Keys.UNTOUCHED_CELLS))
        assertRelations(name, raw)
        // the raw keeps the touches of the free cells too (they were tapped first): one stroke per tap
        @Suppress("UNCHECKED_CAST") val strokes = raw[TouchGridRecorder.Keys.STROKES] as List<Map<String, Any?>>
        assertEquals("$name: every tap is a stroke of the raw, the free cells' included", freeToTap.size + required.size, strokes.size)
        // one verdict per run: a later touch writes nothing more
        tapCell(rec, required.first())
        assertEquals("$name: a finished run ignores a late touch", 1, results.size)
        assertEquals(0, dismissals.size)
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun `a tall phone - the free cells count for nothing and the last required cell ends the run by itself`() = checkAutoComplete("411x891dp", withButtons = false)

    @Test
    @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun `a tall phone with the buttons up - every required cell is still reached and the last one still ends the run`() = checkAutoComplete("411x891dp + buttons", withButtons = true)

    @Test
    @Config(qualifiers = "w768dp-h1024dp-xhdpi")
    fun `a 4 to 3 screen - the free cells count for nothing and the last required cell ends the run by itself`() = checkAutoComplete("768x1024dp", withButtons = false)

    private fun pressButton(tag: String, status: String, reason: String) {
        show()
        val rec = recorder()
        val l = rec.layout!!
        val total = rec.totalCells
        tapCell(rec, (0 until l.cols * l.rows).first { l.isRequired(it) })          // one required cell lit
        rule.mainClock.advanceTimeBy(TouchGridRecorder.TECHNICIAN_BUTTONS_AFTER_MS + 1_000)
        rule.waitForIdle()
        assertEquals("the buttons' appearing changes no number of the grid", total, rec.totalCells)
        rule.onNodeWithTag(tag).performClick()
        rule.waitForIdle()
        assertEquals("one answer", 1, results.size)
        assertEquals("and no dismissal", 0, dismissals.size)
        assertEquals(status, results.single().status)
        val raw = results.single().raw!!
        assertEquals(reason, raw[TouchGridRecorder.Keys.END_REASON])
        assertEquals(1, (raw[TouchGridRecorder.Keys.TOUCHED_CELLS] as Number).toInt())
        assertEquals(total, (raw[TouchGridRecorder.Keys.TOTAL_CELLS] as Number).toInt())
        assertRelations(tag, raw)
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun `the not-working button writes fail with the raw, once, and the grid is what it was`() =
        pressButton(TouchGridRunTags.NOT_WORKING, "fail", TouchGridRecorder.EndReasons.USER_MARKED_NOT_WORKING)

    @Test
    @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun `the not-tested button writes not_tested with the raw, once, and the grid is what it was`() =
        pressButton(TouchGridRunTags.NOT_TESTED, "not_tested", TouchGridRecorder.EndReasons.USER_MARKED_NOT_TESTED)

    @Test
    @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun `back with a required cell lit is a dismissal that carries the raw, and with nothing touched it carries none`() {
        show()
        val rec = recorder()
        val l = rec.layout!!
        tapCell(rec, (0 until l.cols * l.rows).first { l.isRequired(it) })
        dialog().onBackPressedDispatcher.onBackPressed()
        rule.waitForIdle()
        assertEquals(0, results.size)
        assertEquals(1, dismissals.size)
        val raw = dismissals.single()
        assertNotNull(raw)
        assertEquals(TouchGridRecorder.EndReasons.DISMISSED, raw!![TouchGridRecorder.Keys.END_REASON])
        assertRelations("dismissed", raw)
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun `a touch in the empty strip lights nothing and is in the raw - and a finger that goes from the strip into the first row lights the first row's cell it reaches`() {
        show()
        val rec = recorder()
        val l = rec.layout!!
        assertTrue("the grid starts below a strip of two cells at least", l.gridTopPx >= 2 * l.cellHeightPx - 1)
        val stripY = (l.gridTopPx / 2).toFloat()
        rule.onNodeWithTag(TouchGridRunTags.SURFACE).performTouchInput { down(Offset(l.cellWidthPx.toFloat() * 3.5f, stripY)); up() }
        rule.waitForIdle()
        assertEquals("a tap in the strip lights nothing", 0, rec.touchedCount)
        // from the strip down into row 0, column 3: only the cell the finger reaches is lit
        rule.onNodeWithTag(TouchGridRunTags.SURFACE).performTouchInput {
            down(Offset(l.cellWidthPx.toFloat() * 3.5f, stripY))
            moveTo(Offset(l.cellWidthPx.toFloat() * 3.5f, l.gridTopPx + l.cellHeightPx.toFloat() * 0.5f))
            up()
        }
        rule.waitForIdle()
        assertEquals(1, rec.touchedCount)
        assertTrue(rec.isTouched(3))
        val strokes = rec.measurements(TouchGridRecorder.EndReasons.DISMISSED)[TouchGridRecorder.Keys.STROKES] as List<*>
        assertEquals("both fingers are in the raw", 2, strokes.size)
        @Suppress("UNCHECKED_CAST") val firstY = ((strokes[0] as Map<String, Any?>)["y"] as List<Number>).map { it.toFloat() }
        assertTrue("the strip's sample has a negative y in the surface's frame: ${firstY}", firstY.all { it < 0f })
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun `back with nothing touched is a dismissal that carries no raw - an accidental open overwrites nothing`() {
        show()
        dialog().onBackPressedDispatcher.onBackPressed()
        rule.waitForIdle()
        assertEquals(0, results.size)
        assertEquals(1, dismissals.size)
        assertNull(dismissals.single())
    }
}
