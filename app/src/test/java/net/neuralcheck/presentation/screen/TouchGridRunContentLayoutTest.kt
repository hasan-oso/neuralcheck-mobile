package net.neuralcheck.presentation.screen

import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import net.neuralcheck.domain.manual.TouchGridContract
import net.neuralcheck.domain.manual.TouchGridOverlay
import net.neuralcheck.domain.manual.TouchGridOverlay.Box
import net.neuralcheck.domain.manual.TouchGridPattern
import net.neuralcheck.domain.manual.TouchGridRecorder
import net.neuralcheck.presentation.theme.NeuralCheckTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.roundToInt

/**
 * The full-screen M-02 «frame + X» grid as the dialog DRAWS it — the real [TouchGridRunContent] (the composable the dialog hands to its window) with the real
 * `TouchGridSurfaceView`, the real [TouchGridRecorder] and the real [TouchGridOverlay], rendered at four screen sizes, and the real touch input (Compose's test input
 * injection into the same view tree). User decisions 2026-10-21: the dialog is the whole screen, and the test is a frame of cells with two crossing diagonals under an empty strip at the top that holds the title, the count and the
 * hint (no text stands on the cells), and the two buttons cover no required cell («الزرّان بعد 15 ث لا يغطّيان أي خلية مطلوبة»).
 *
 * It REPLACES the file of the same name of app-engineer-8 (rule 23), whose checks pinned the earlier shape: the grid over the whole window with its surface the window's, and the title, the hint
 * and the count stacked in a box of free cells inside the frame.
 *
 * What the screen is: the content fills the test activity's window, which Robolectric sizes from the qualifiers — standing for the dialog's window, which
 * `ImmersiveFullScreenDialog` makes the display (that part, the window itself, is a device item: not proved here and said so in the report). Robolectric has no display cut-out,
 * so the camera is the geometry test's (`TouchGridFullScreenLayoutTest`, with three holes) and not this one's.
 *
 * Proved at every size, from the REAL bounds of what was drawn (the bounds are read from the semantics tree, never from the geometry object that placed them):
 *  (أ) the surface view's rectangle IS the window's, to the pixel; the grid the recorder laid out is its region below the empty strip: `surfaceWidthPx x surfaceHeightPx` with
 *      `surfaceOriginYPx` the strip's height, and the strip plus the grid are the window;
 *  (ب) the title with the live count (one line) and the hint (under it) stand inside the box [TouchGridOverlay] gives the text — the strip —, none of them overflows, and no cell at all is under them;
 *      the two buttons, after they appear, stand where [TouchGridOverlay] says, no required cell is met by either, each is at least 48 dp each way, and its label fits;
 *  (ج) `totalCells = cols x rows - |cutoutCells| - |notRequiredCells|` is what the count on the screen shows;
 *  (د) the buttons' appearing changes neither the surface's rectangle nor the grid's numbers;
 *  (هـ) touch: a tap on a required cell lights it; a tap on the live count (in the strip) and one on a free cell light nothing and are recorded as strokes (the text takes no touch, the free cells ask for none);
 *      a tap on a button calls its callback once and is no stroke.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35]) // = the app's targetSdk, as ManualUnavailableLineLayoutTest
class TouchGridRunContentLayoutTest {

    @get:Rule(order = 0) val registerActivity = ManualUnavailableLineLayoutTest.RegisterComposeActivity()
    @get:Rule(order = 1) val rule = createAndroidComposeRule<ComponentActivity>()

    private val recorder = TouchGridRecorder(nowMs = { SystemClock.elapsedRealtime() }, uptimeMs = { SystemClock.uptimeMillis() })

    // the dialog's own wiring of the content (touched / total / buttonsVisible are the dialog's state; onProgress is its callback)
    private var touched by mutableIntStateOf(0)
    private var total by mutableIntStateOf(0)
    private var buttonsVisible by mutableStateOf(false)
    private var notWorkingTaps = 0
    private var notTestedTaps = 0

    private fun show() {
        rule.setContent {
            NeuralCheckTheme {
                TouchGridRunContent(
                    recorder = recorder,
                    touched = touched,
                    total = total,
                    buttonsVisible = buttonsVisible,
                    onProgress = {
                        if (touched != recorder.touchedCount) touched = recorder.touchedCount
                        if (total != recorder.totalCells) total = recorder.totalCells
                    },
                    onNotWorking = { notWorkingTaps++ },
                    onNotTested = { notTestedTaps++ }
                )
            }
        }
        rule.waitForIdle()
    }

    private fun bounds(tag: String): Rect = rule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

    private fun Rect.toBox() = Box(left.toDouble(), top.toDouble(), right.toDouble(), bottom.toDouble())

    private fun tap(x: Double, y: Double) {
        rule.onRoot().performTouchInput { down(Offset(x.toFloat(), y.toFloat())); up() }
        rule.waitForIdle()
    }

    /** The text layout of the Text a tag names (the Text itself, or the first child of a box that holds one). */
    private fun textLayout(tag: String, inBox: Boolean = false): TextLayoutResult {
        val node = if (inBox) rule.onNodeWithTag(tag, useUnmergedTree = true).onChildren().onFirst().fetchSemanticsNode() else rule.onNodeWithTag(tag).fetchSemanticsNode()
        val results = ArrayList<TextLayoutResult>()
        val action = node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action
        assertNotNull("$tag has a text layout", action)
        action!!.invoke(results)
        return results.first()
    }

    private fun checkScreen(name: String) {
        show()
        val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
        val surface = bounds(TouchGridRunTags.SURFACE)

        // (أ) the surface is the window — no bar above it, none below it, no margin at a side
        assertEquals("$name: surface left", 0f, surface.left, 0f)
        assertEquals("$name: surface top", 0f, surface.top, 0f)
        assertEquals("$name: surface right = the window's", root.right, surface.right, 0f)
        assertEquals("$name: surface bottom = the window's", root.bottom, surface.bottom, 0f)
        val layout = recorder.layout
        assertNotNull("$name: the grid was laid out by the surface", layout)
        layout!!
        assertEquals("$name: the window the grid was laid out in is the view's", root.width.roundToInt() to root.height.roundToInt(), layout.windowSurface.widthPx to layout.windowSurface.heightPx)
        assertEquals("$name: the grid's surface width is the window's", root.width.roundToInt(), layout.surface.widthPx)
        assertEquals("$name: the grid's surface starts below the empty strip (origin y = the strip's height)", layout.gridTopPx, layout.surface.originYPx)
        assertEquals("$name: the strip and the grid are the window's height", root.height.roundToInt(), layout.gridTopPx + layout.surface.heightPx)
        assertTrue("$name: the strip is two cells high at least (${layout.gridTopPx} px)", layout.gridTopPx >= 2 * layout.cellHeightPx - 1.0)
        assertEquals("$name: origin x", 0, layout.surface.originXPx)
        assertEquals("$name: the cells tile the window's width", root.width.toDouble(), layout.cols * layout.cellWidthPx, 1e-3)
        assertEquals("$name: the cells tile the grid's height", layout.surface.heightPx.toDouble(), layout.rows * layout.cellHeightPx, 1e-3)

        // (ج) the numbers, before any button: the frame and the diagonals, the camera (none here) taken from them
        val pattern = TouchGridPattern.cells(layout.cols, layout.rows)
        assertEquals("$name: totalCells = cols x rows - cut-out - left-out", layout.cols * layout.rows - layout.cutoutCells.size - layout.notRequiredCells.size, recorder.totalCells)
        assertEquals("$name: and it is the pattern's size less the camera's cells in it", pattern.size - layout.cutoutCells.count { it in pattern }, recorder.totalCells)
        assertEquals("$name: the count on screen is the total", recorder.totalCells, total)
        val counter = rule.onNodeWithTag(TouchGridRunTags.COUNTER).fetchSemanticsNode()
        val counterText = counter.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }.orEmpty()
        assertTrue("$name: the live count shows the total (`$counterText`)", counterText.contains(recorder.totalCells.toString()))
        println("FRAME-X-RENDERED $name: window ${root.width.roundToInt()}x${root.height.roundToInt()} px -> ${layout.cols} cols x ${layout.rows} rows, required ${recorder.totalCells}, left out ${layout.notRequiredCells.size}")

        // (ب) the text: the box the overlay gives it — the strip —, no cell under it at all, nothing overflowing
        val expected = TouchGridOverlay.place(layout)
        val textBox = expected.text
        assertNotNull("$name: the text has a place", textBox)
        textBox!!
        assertTrue("$name: the text's place is the strip (bottom ${textBox.bottom}, the strip ${layout.gridTopPx})", textBox.top >= 0.0 && textBox.bottom <= layout.gridTopPx)
        val texts = listOf(TouchGridRunTags.TITLE, TouchGridRunTags.HINT, TouchGridRunTags.COUNTER).map { it to bounds(it).toBox() }
        for ((tag, b) in texts) {
            assertTrue("$name: $tag inside the text box (${b.left},${b.top},${b.right},${b.bottom} in ${textBox.left},${textBox.top},${textBox.right},${textBox.bottom})",
                b.left >= textBox.left - 0.5 && b.top >= textBox.top - 0.5 && b.right <= textBox.right + 0.5 && b.bottom <= textBox.bottom + 0.5)
            val layoutResult = textLayout(tag)
            assertFalse("$name: $tag does not overflow its height", layoutResult.didOverflowHeight)
            // a one-line text with an ellipsis is laid out at the width it was OFFERED (the paragraph is wider than the text it holds), so `didOverflowWidth` would say «overflow» of every such text:
            // what must hold is that it is one line and that nothing was cut (no ellipsis)
            assertEquals("$name: $tag is a single line", 1, layoutResult.lineCount)
            assertFalse("$name: $tag is not cut by an ellipsis (size ${layoutResult.size}, intrinsic ${layoutResult.multiParagraph.maxIntrinsicWidth}, bounds $b, box $textBox)", layoutResult.isLineEllipsized(0))
        }
        assertEquals("$name: no required cell is met by the title, the hint or the count", emptyList<Int>(), TouchGridOverlay.requiredCellsMetBy(layout, texts.map { it.second }))
        assertTrue("$name: no cell of the grid at all is under any of them (they end at ${texts.maxOf { it.second.bottom }}, the grid starts at ${layout.gridTopPx})", texts.all { it.second.bottom <= layout.gridTopPx + 0.5 })
        val title = texts[0].second
        val hint = texts[1].second
        val count = texts[2].second
        assertTrue("$name: the title and the count are on one line, apart", title.right <= count.left + 0.5 || count.right <= title.left + 0.5)
        assertTrue("$name: and the hint is under both", hint.top >= maxOf(title.bottom, count.bottom) - 0.5)

        // (د) the buttons appear: the grid and the surface do not move
        buttonsVisible = true
        rule.waitForIdle()
        val surfaceAfter = bounds(TouchGridRunTags.SURFACE)
        assertEquals("$name: the surface's rectangle is the same", surface, surfaceAfter)
        assertSame("$name: the grid is the same one", layout, recorder.layout)
        assertEquals("$name: the required cells are the same number", layout.cols * layout.rows - layout.cutoutCells.size - layout.notRequiredCells.size, recorder.totalCells)
        assertEquals("$name: and the count on screen", recorder.totalCells, total)

        // (ب) the buttons stand where the geometry says, and meet no required cell
        val row = expected.buttons
        assertNotNull("$name: the buttons have a place on this grid", row)
        row!!
        val notWorking = bounds(TouchGridRunTags.NOT_WORKING).toBox()
        val notTested = bounds(TouchGridRunTags.NOT_TESTED).toBox()
        val actual = listOf(notWorking, notTested).sortedBy { it.left }
        for ((a, e) in actual.zip(row.buttons)) {
            assertEquals("$name: button left", e.left, a.left, 1.0)
            assertEquals("$name: button top", e.top, a.top, 1.0)
            assertEquals("$name: button right", e.right, a.right, 1.0)
            assertEquals("$name: button bottom", e.bottom, a.bottom, 1.0)
        }
        assertEquals("$name: no required cell is met by the REAL buttons", emptyList<Int>(), TouchGridOverlay.requiredCellsMetBy(layout, actual))
        // read from the drawn bounds, not from the geometry object (a checker must not read what it checks): above the frame's last row, clear of the frame's columns, apart from each other
        for (b in actual) {
            assertTrue("$name: above the last row of the frame", b.bottom <= layout.gridTopPx + (layout.rows - 1) * layout.cellHeightPx + 0.5)
            assertTrue("$name: right of the frame's first column", b.left >= layout.cellWidthPx - 0.5)
            assertTrue("$name: left of the frame's last column", b.right <= root.width - layout.cellWidthPx + 0.5)
        }
        assertTrue("$name: the two buttons are apart", actual[0].right < actual[1].left)
        val fingerPx = with(rule.density) { 48.dp.toPx() }
        for ((tag, b) in listOf(TouchGridRunTags.NOT_WORKING to notWorking, TouchGridRunTags.NOT_TESTED to notTested)) {
            assertTrue("$name: $tag is at least 48 dp high (${b.height} px, 48 dp = $fingerPx px)", b.height >= fingerPx)
            assertTrue("$name: $tag is at least 48 dp wide (${b.width} px)", b.width >= fingerPx)
            val label = textLayout(tag, inBox = true)
            // the label is one line (maxLines = 1): its natural width must be inside the button's (`didOverflowWidth` is no test of that for a label that wraps its content)
            assertTrue("$name: the label of $tag fits its button's width (button ${b.width} px, the label's own width ${label.multiParagraph.maxIntrinsicWidth} px)", label.multiParagraph.maxIntrinsicWidth <= b.width)
            assertEquals("$name: and it is a single line", 1, label.lineCount)
            assertTrue("$name: and its height (${label.size.height} px in ${b.height} px)", label.size.height <= b.height)
        }
        assertTrue("$name: the buttons are below the text", actual.all { it.top >= texts.maxOf { t -> t.second.bottom } })

        // (هـ) touch. A required cell: the frame's corner, lit by a tap on its middle
        fun cellMiddle(index: Int) = ((index % layout.cols + 0.5) * layout.cellWidthPx) to (layout.gridTopPx + (index / layout.cols + 0.5) * layout.cellHeightPx)
        val corner = 0
        assertFalse(recorder.isTouched(corner))
        tap(cellMiddle(corner).first, cellMiddle(corner).second)
        assertTrue("$name: a tap on the corner cell lit it", recorder.isTouched(corner))
        assertEquals("$name: one cell", 1, recorder.touchedCount)
        assertEquals("$name: and the screen's count follows", 1, touched)

        // the live count is text in the strip and consumes no touch: the touch goes through to the surface, which is off its grid there (a negative y) — nothing lights, a stroke is recorded
        val c = counter.boundsInRoot
        val countY = ((c.top + c.bottom) / 2).toDouble()
        assertTrue("$name: the count is above the grid (y $countY, the grid starts at ${layout.gridTopPx})", countY < layout.gridTopPx)
        tap(((c.left + c.right) / 2).toDouble(), countY)
        assertEquals("$name: a tap on the live count lit nothing", 1, recorder.touchedCount)

        // a free cell in the middle of the screen's left triangle (no element on it): the same - nothing lights
        val free = layout.notRequiredCells.first { i ->
            val (x, y) = cellMiddle(i)
            (texts.map { it.second } + actual).none { it.contains(x, y) } && (i / layout.cols) in layout.rows / 3..layout.rows * 2 / 3
        }
        tap(cellMiddle(free).first, cellMiddle(free).second)
        assertEquals("$name: a tap on a free cell lit nothing", 1, recorder.touchedCount)

        // the buttons do what they are for — and a tap on one is no stroke (it never reaches the surface)
        tap((notWorking.left + notWorking.right) / 2, (notWorking.top + notWorking.bottom) / 2)
        assertEquals("$name: «لا يعمل» is called once by a tap on it", 1, notWorkingTaps)
        assertEquals(0, notTestedTaps)
        tap((notTested.left + notTested.right) / 2, (notTested.top + notTested.bottom) / 2)
        assertEquals("$name: «لم أختبر» is called once by a tap on it", 1, notTestedTaps)
        assertEquals(1, notWorkingTaps)
        @Suppress("UNCHECKED_CAST")
        val strokes = recorder.measurements(TouchGridRecorder.EndReasons.DISMISSED)[TouchGridRecorder.Keys.STROKES] as List<Map<String, Any?>>
        assertEquals("$name: the corner, the count and the free cell are strokes; the two buttons are not", 3, strokes.size)
        assertTrue("$name: the count's stroke has a negative y in the surface's frame (the strip is not part of the surface)", ((strokes[1][TouchGridContract.StrokeKeys.Y] as List<*>).first() as Number).toFloat() < 0f)
    }

    @Test
    @Config(qualifiers = "w360dp-h800dp-xxhdpi")
    fun `a 20 to 9 phone, 360 x 800 dp`() = checkScreen("360x800dp")

    @Test
    @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun `a taller 20 to 9 phone, 411 x 891 dp`() = checkScreen("411x891dp")

    @Test
    @Config(qualifiers = "w600dp-h960dp-xhdpi")
    fun `a wider screen, 600 x 960 dp`() = checkScreen("600x960dp")

    @Test
    @Config(qualifiers = "w768dp-h1024dp-xhdpi")
    fun `a 4 to 3 screen, 768 x 1024 dp`() = checkScreen("768x1024dp")

    @Test
    @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun `before the buttons appear nothing but the surface and the text is on the screen`() {
        show()
        rule.onNodeWithTag(TouchGridRunTags.SURFACE).assertExists()
        rule.onNodeWithTag(TouchGridRunTags.COUNTER).assertExists()
        rule.onNodeWithTag(TouchGridRunTags.NOT_WORKING).assertDoesNotExist()
        rule.onNodeWithTag(TouchGridRunTags.NOT_TESTED).assertDoesNotExist()
    }
}
