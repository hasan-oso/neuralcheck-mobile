package net.neuralcheck.domain.manual

import net.neuralcheck.data.local.db.converter.MeasurementsJson
import net.neuralcheck.domain.manual.TouchGridContract.StrokeEnds
import net.neuralcheck.domain.manual.TouchGridContract.SuspectKinds
import net.neuralcheck.domain.manual.TouchGridRecorder.EndReasons
import net.neuralcheck.domain.manual.TouchGridRecorder.Keys
import net.neuralcheck.domain.manual.TouchGridTestKit.Rig
import net.neuralcheck.domain.model.ManualStatuses
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * fieldsVersion 71 (contract docs/FV68_CONTRACT.md 7-ي ف-01..ف-03; user approvals E18-E22 of 2026-10-15; A-M02 of 2026-10-18): the raw of one M-02
 * run, on scripted clocks and scripted samples — the REAL recorder, layout and density selector; only the platform's answers and the clocks are
 * scripted ([TouchGridTestKit]). REPLACES the fv69/fv70 tests of the fixed 8 x 15 grid (rule 23): those pinned 120 cells, a sample-lands-in-a-cell
 * fill and eight keys; the grid is now square 4 mm cells filled by sweeping and the raw carries the path.
 *
 * The default screen is 800 x 1500 px at 10 px per mm: 20 x 35 square cells of 40 px under an empty strip of 100 px, 700 in all, of which the «frame + X» pattern (user decisions 2026-10-21,
 * `TouchGridPattern`) requires 200: the frame (106) and the cells of the two diagonals beyond it (94) — the numbers of `tools-m02-shape-reference.js`, independent of this code. The samples
 * are in the touch surface's frame (the grid's region: 800 x 1400, 100 px below the window's top): a sample in the strip has a negative `y`. The tests that took the 6 mm grid's numbers
 * for granted (13 x 25, 113 required, a sample off the surface clamped into the border cell) are REPLACED by the same checks on the new grid (rule 23).
 */
class TouchGridRecorderTest {

    private fun rig(
        env: TouchGridEnvironment = TouchGridTestKit.environment(),
        maxPoints: Int = TouchGridContract.PATH_MAX_POINTS
    ) = Rig(env = env, maxPoints = maxPoints)

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.strokes(): List<Map<String, Any?>> = this[Keys.STROKES] as List<Map<String, Any?>>

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.suspects(): List<Map<String, Any?>> = this[Keys.DISPLAYED_SUSPECTS] as List<Map<String, Any?>>

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.absentReasons(): Map<String, String> = this[Keys.ABSENT_FIELD_REASONS] as Map<String, String>

    private fun Rig.tap(col: Int, row: Int, t: Long) {
        val (x, y) = centre(col, row)
        down(0, x, y, t)
        up(0, x, y, t + 10)
    }

    // ───────────────────────── the contract's constants and names ─────────────────────────

    @Test
    fun `the contract's constants - a 4 mm cell, the strip of two cells, the 15 000 point cap, the technician's buttons after 15 s`() {
        assertEquals(4.0, TouchGridContract.CELL_TARGET_MM, 0.0)
        assertEquals(2, TouchGridContract.TOP_STRIP_CELLS)
        assertEquals(15_000, TouchGridContract.PATH_MAX_POINTS)
        assertEquals(15_000L, TouchGridRecorder.TECHNICIAN_BUTTONS_AFTER_MS)
    }

    @Test
    fun `the wire names are the contract's - one source, spelled out here so a rename fails`() {
        assertEquals("gridCols", Keys.GRID_COLS)
        assertEquals("gridRows", Keys.GRID_ROWS)
        assertEquals("touchedCells", Keys.TOUCHED_CELLS)
        assertEquals("totalCells", Keys.TOTAL_CELLS)
        assertEquals("elapsedMs", Keys.ELAPSED_MS)
        assertEquals("timeToFirstTouchMs", Keys.TIME_TO_FIRST_TOUCH_MS)
        assertEquals("endReason", Keys.END_REASON)
        assertEquals("maxSimultaneousPointers", Keys.MAX_SIMULTANEOUS_POINTERS)
        assertEquals("surfaceWidthPx", Keys.SURFACE_WIDTH_PX)
        assertEquals("surfaceHeightPx", Keys.SURFACE_HEIGHT_PX)
        assertEquals("surfaceOriginXPx", Keys.SURFACE_ORIGIN_X_PX)
        assertEquals("surfaceOriginYPx", Keys.SURFACE_ORIGIN_Y_PX)
        assertEquals("displayWidthPx", Keys.DISPLAY_WIDTH_PX)
        assertEquals("displayHeightPx", Keys.DISPLAY_HEIGHT_PX)
        assertEquals("modePhysicalWidthPx", Keys.MODE_PHYSICAL_WIDTH_PX)
        assertEquals("modePhysicalHeightPx", Keys.MODE_PHYSICAL_HEIGHT_PX)
        assertEquals("xdpi", Keys.XDPI)
        assertEquals("ydpi", Keys.YDPI)
        assertEquals("densityDpi", Keys.DENSITY_DPI)
        assertEquals("pxPerMm", Keys.PX_PER_MM)
        assertEquals("densitySource", Keys.DENSITY_SOURCE)
        assertEquals("densityCheck", Keys.DENSITY_CHECK)
        assertEquals("cellTargetMm", Keys.CELL_TARGET_MM)
        assertEquals("cellWidthPx", Keys.CELL_WIDTH_PX)
        assertEquals("cellHeightPx", Keys.CELL_HEIGHT_PX)
        assertEquals("untouchedCells", Keys.UNTOUCHED_CELLS)
        assertEquals("cutoutCells", Keys.CUTOUT_CELLS)
        assertEquals("gridPattern", Keys.GRID_PATTERN)
        assertEquals("notRequiredCells", Keys.NOT_REQUIRED_CELLS)
        assertEquals("gestureInsetsPx", Keys.GESTURE_INSETS_PX)
        assertEquals("mandatoryGestureInsetsPx", Keys.MANDATORY_GESTURE_INSETS_PX)
        assertEquals("exclusionRectsPx", Keys.EXCLUSION_RECTS_PX)
        assertEquals("systemBarsHidden", Keys.SYSTEM_BARS_HIDDEN)
        assertEquals("cutoutRectsPx", Keys.CUTOUT_RECTS_PX)
        assertEquals("roundedCornerRadiiPx", Keys.ROUNDED_CORNER_RADII_PX)
        assertEquals("roundedCornersState", Keys.ROUNDED_CORNERS_STATE)
        assertEquals("absentFieldReasons", Keys.ABSENT_FIELD_REASONS)
        assertEquals("strokes", Keys.STROKES)
        assertEquals("unbufferedDispatch", Keys.UNBUFFERED_DISPATCH)
        assertEquals("resampledFlagState", Keys.RESAMPLED_FLAG_STATE)
        assertEquals("displayedSuspects", Keys.DISPLAYED_SUSPECTS)
        assertEquals("pathCap", Keys.PATH_CAP)
        // below the top level
        assertEquals(
            listOf("pointerId", "toolType", "downAtMs", "end", "dtMs", "x", "y", "touchMajorPx"),
            with(TouchGridContract.StrokeKeys) { listOf(POINTER_ID, TOOL_TYPE, DOWN_AT_MS, END, DT_MS, X, Y, TOUCH_MAJOR_PX) }
        )
        assertEquals("strokes.touchMajorPx", TouchGridContract.StrokeKeys.TOUCH_MAJOR_ABSENT_KEY)
        assertEquals(
            listOf("kind", "strokeIndex", "fromPointIndex", "toPointIndex"),
            with(TouchGridContract.SuspectKeys) { listOf(KIND, STROKE_INDEX, FROM_POINT_INDEX, TO_POINT_INDEX) }
        )
        assertEquals(listOf("maxPoints", "reached", "pointsDropped"), with(TouchGridContract.PathCapKeys) { listOf(MAX_POINTS, REACHED, POINTS_DROPPED) })
        assertEquals(listOf("left", "top", "right", "bottom"), with(TouchGridContract.RectKeys) { listOf(LEFT, TOP, RIGHT, BOTTOM) })
        assertEquals(
            listOf("topLeft", "topRight", "bottomRight", "bottomLeft"),
            with(TouchGridContract.CornerKeys) { listOf(TOP_LEFT, TOP_RIGHT, BOTTOM_RIGHT, BOTTOM_LEFT) }
        )
        // enumerated values
        assertEquals(listOf("auto_complete", "user_marked_not_working", "user_marked_not_tested", "dismissed"), EndReasons.ALL)
        assertEquals(listOf("up", "cancel", "cancel_flagged", "open_at_end"), StrokeEnds.ALL)
        assertEquals(listOf("gap_jump", "coord_jump", "edge_exit"), SuspectKinds.ALL)
        assertEquals(listOf("touch", "stylus", "mouse", "eraser", "unknown"), TouchGridContract.ToolTypes.ALL)
        assertEquals(listOf("XDPI", "XDPI_MODE_SCALED", "DENSITY_DPI_FALLBACK"), TouchGridContract.DensitySources.ALL)
        assertEquals(listOf("ALL_CELLS", "FRAME_AND_DIAGONALS"), TouchGridContract.GridPatterns.ALL)
    }

    @Test
    fun `the tool type is named from the platform's number - and a number the contract does not list is written unknown colon n`() {
        assertEquals("unknown", TouchGridContract.ToolTypes.nameOf(0))
        assertEquals("touch", TouchGridContract.ToolTypes.nameOf(1))
        assertEquals("stylus", TouchGridContract.ToolTypes.nameOf(2))
        assertEquals("mouse", TouchGridContract.ToolTypes.nameOf(3))
        assertEquals("eraser", TouchGridContract.ToolTypes.nameOf(4))
        assertEquals("unknown:7", TouchGridContract.ToolTypes.nameOf(7))
        val r = rig()
        r.down(0, 10f, 10f, tool = 2)
        r.down(1, 20f, 20f, tool = 7)
        assertEquals(
            listOf("stylus", "unknown:7"),
            r.rec.measurements(EndReasons.DISMISSED).strokes().map { it[TouchGridContract.StrokeKeys.TOOL_TYPE] }
        )
    }

    // ───────────────────────── the clocks ─────────────────────────

    @Test
    fun `elapsedMs counts from the FIRST TOUCH, not from opening - and timeToFirstTouchMs is its own field`() {
        val r = Rig(nowAtOpen = 1_000L)
        r.now = 1_250L                                   // first touch, 250 ms after opening
        r.down(0, 100f, 100f, tMs = 50_250L)
        r.now = 5_250L                                   // the technician presses «لا يعمل»
        val m = r.rec.measurements(EndReasons.USER_MARKED_NOT_WORKING)

        assertEquals("from the first touch to the end: 5250 - 1250", 4_000L, m[Keys.ELAPSED_MS])
        assertEquals("from opening to the first touch: 1250 - 1000", 250L, m[Keys.TIME_TO_FIRST_TOUCH_MS])
        assertTrue("the two are different numbers (elapsed from opening would be 4250)", m[Keys.ELAPSED_MS] != 4_250L)
    }

    @Test
    fun `with no touch at all both times are ABSENT - not 0 (rule 22) - touchedCells says why, and the raw still says what the grid was laid on`() {
        val r = rig()
        r.now = 16_000L
        val m = r.rec.measurements(EndReasons.USER_MARKED_NOT_TESTED)
        assertFalse(m.containsKey(Keys.ELAPSED_MS))
        assertFalse(m.containsKey(Keys.TIME_TO_FIRST_TOUCH_MS))
        assertEquals(0, m[Keys.TOUCHED_CELLS])
        assertEquals(0, m[Keys.MAX_SIMULTANEOUS_POINTERS])
        assertEquals(EndReasons.USER_MARKED_NOT_TESTED, m[Keys.END_REASON])
        assertEquals(20, m[Keys.GRID_COLS])
        assertEquals(35, m[Keys.GRID_ROWS])
        assertEquals("the frame and the diagonals: 200 of the 700 cells", 200, m[Keys.TOTAL_CELLS])
        assertEquals("FRAME_AND_DIAGONALS", m[Keys.GRID_PATTERN])
        assertEquals(800, m[Keys.SURFACE_WIDTH_PX])
        assertEquals("the touch surface is the grid's region: the window less the strip", 1400, m[Keys.SURFACE_HEIGHT_PX])
        assertEquals(0, m[Keys.SURFACE_ORIGIN_X_PX])
        assertEquals("and it starts below the strip", 100, m[Keys.SURFACE_ORIGIN_Y_PX])
        assertEquals("no finger: no stroke, an empty list, not an absent key", emptyList<Any>(), m.strokes())
        val l = r.layout
        assertEquals("every required cell is untouched - and only the required ones", (0 until 700).filter { l.isRequired(it) }, m[Keys.UNTOUCHED_CELLS])
        assertEquals(200, (m[Keys.UNTOUCHED_CELLS] as List<*>).size)
        assertEquals("the cells the pattern leaves out are declared, not untouched", (0 until 700).filter { l.isNotRequired(it) }, m[Keys.NOT_REQUIRED_CELLS])
        assertEquals(700 - 200, (m[Keys.NOT_REQUIRED_CELLS] as List<*>).size)
        assertEquals("pressed by the technician: the marks were shown, none seen", emptyList<Any>(), m.suspects())
    }

    @Test
    fun `auto_complete ends at the moment the LAST required cell lit, not when the map is read`() {
        val r = Rig(nowAtOpen = 1_000L)
        r.now = 1_250L
        r.tap(0, 0, 50_250L)
        var t = 50_300L
        for (row in 0 until 35) for (col in 0 until 20) {
            if (row == 0 && col == 0) continue
            r.now = if (row == 34 && col == 19) 6_000L else 1_300L
            r.tap(col, row, t)
            t += 20
        }
        assertTrue(r.rec.isComplete)
        r.now = 9_000L                                   // read long after
        val m = r.rec.measurements(EndReasons.AUTO_COMPLETE)
        assertEquals("6000 - 1250", 4_750L, m[Keys.ELAPSED_MS])
        assertEquals("every required cell was lit (taps on the cells the pattern leaves out counted for nothing)", 200, m[Keys.TOUCHED_CELLS])
        assertEquals(200, m[Keys.TOTAL_CELLS])
        assertEquals("complete: nothing is untouched", emptyList<Int>(), m[Keys.UNTOUCHED_CELLS])
    }

    @Test
    fun `downAtMs is the landing's event time minus the opening on the uptime clock - and dtMs is the gap to the stroke's previous sample, 0 for the first`() {
        val r = Rig(nowAtOpen = 1_000L, uptimeAtOpen = 50_000L)
        r.now = 40_000L                                  // elapsedRealtime is far from uptime: they are never subtracted from one another
        r.down(0, 100f, 100f, tMs = 50_730L)
        r.move(0, Triple(50_738L, 110f, 100f), Triple(50_751L, 120f, 100f))
        r.up(0, 125f, 100f, 50_760L)
        val s = r.rec.measurements(EndReasons.DISMISSED).strokes().single()
        assertEquals(730L, s[TouchGridContract.StrokeKeys.DOWN_AT_MS])
        assertEquals(listOf(0L, 8L, 13L, 9L), s[TouchGridContract.StrokeKeys.DT_MS])
        assertEquals(listOf(100f, 110f, 120f, 125f), s[TouchGridContract.StrokeKeys.X])
        assertEquals(StrokeEnds.UP, s[TouchGridContract.StrokeKeys.END])
        assertEquals("the stroke has the same number of samples in x, y, dtMs and touchMajorPx", 4, (s[TouchGridContract.StrokeKeys.TOUCH_MAJOR_PX] as List<*>).size)
    }

    @Test
    fun `a sample stamped before the previous one gets a dt of 0 - never a negative number`() {
        val r = rig()
        r.down(0, 100f, 100f, tMs = 50_100L)
        r.move(0, Triple(50_090L, 110f, 100f))
        assertEquals(listOf(0L, 0L), r.rec.measurements(EndReasons.DISMISSED).strokes().single()[TouchGridContract.StrokeKeys.DT_MS])
    }

    // ───────────────────────── filling by sweeping ─────────────────────────

    @Test
    fun `a tap lights one required cell and a cell touched twice counts once`() {
        val r = rig()
        val (x, y) = r.centre(2, 4)                       // row 4's diagonal cell (inside row 3 the line covers x from 1.09 to 1.64 of the inside: columns 2 and 3 of the grid)
        assertTrue(r.layout.isRequired(4 * 20 + 2))
        assertTrue(r.down(0, x, y))
        assertEquals(1, r.rec.touchedCount)
        r.up(0, x, y, 50_010L)
        assertFalse("the same cell again turns nothing on", r.down(0, x, y, 50_020L))
        assertEquals(1, r.rec.touchedCount)
        assertTrue(r.rec.isTouched(4 * 20 + 2))
    }

    @Test
    fun `a finger on a cell the pattern leaves out lights nothing and counts for nothing - and is in the raw like every other touch (rule 20)`() {
        val r = rig()
        val (x, y) = r.centre(6, 4)                       // inside the top triangle of the 20 x 35 grid: not in the frame, not on a diagonal
        assertTrue(r.layout.isNotRequired(4 * 20 + 6))
        assertFalse("nothing turns on", r.down(0, x, y, 50_000L))
        assertFalse(r.move(0, Triple(50_008L, x + 20f, y), Triple(50_016L, x + 40f, y + 10f)))
        r.up(0, x + 40f, y + 10f, 50_024L)
        assertEquals("not a lit cell", 0, r.rec.touchedCount)
        assertFalse(r.rec.isTouched(4 * 20 + 6))
        val m = r.rec.measurements(EndReasons.USER_MARKED_NOT_WORKING)
        assertEquals("not counted", 0, m[Keys.TOUCHED_CELLS])
        assertEquals("every required cell is still untouched", 200, (m[Keys.UNTOUCHED_CELLS] as List<*>).size)
        assertFalse("the cell it crossed is not in untouchedCells: it was never required", (4 * 20 + 6) in (m[Keys.UNTOUCHED_CELLS] as List<*>))
        assertTrue("it is declared as left out", (4 * 20 + 6) in (m[Keys.NOT_REQUIRED_CELLS] as List<*>))
        val s = m.strokes().single()
        assertEquals("every sample of it is written, as the platform gave it", listOf(x, x + 20f, x + 40f, x + 40f), s[TouchGridContract.StrokeKeys.X])
        assertEquals(listOf(y, y, y + 10f, y + 10f), s[TouchGridContract.StrokeKeys.Y])
        assertEquals(StrokeEnds.UP, s[TouchGridContract.StrokeKeys.END])
    }

    @Test
    fun `a stroke across a triangle lights the required cells it crosses on the way - the frame's and the diagonals' - and none of the free ones`() {
        val r = rig()
        // along row 12 (y = 500, the middle of the row) from the left edge to the right edge: the frame's two cells (columns 0 and 19) and one cell of each diagonal (columns 7 and 12)
        // are required; the other 16 are free
        r.down(0, 1f, 500f, 50_000L)
        r.move(0, Triple(50_008L, 799f, 500f))
        assertEquals(4, r.rec.touchedCount)
        for (col in listOf(0, 7, 12, 19)) assertTrue("column $col of row 12", r.rec.isTouched(12 * 20 + col))
        for (col in listOf(1, 6, 8, 11, 13, 18)) assertFalse("column $col of row 12 is free", r.rec.isTouched(12 * 20 + col))
    }

    @Test
    fun `a drag lights every cell it crosses - not only the cells a sample landed on`() {
        val r = rig()
        r.down(0, 1f, 30f)
        assertTrue(r.move(0, Triple(50_008L, 799f, 30f)))       // two samples 798 px apart: 20 cells between them, one MOVE event
        assertEquals(20, r.rec.touchedCount)
        for (col in 0..19) assertTrue("column $col", r.rec.isTouched(col))
        assertFalse(r.rec.isTouched(20))
    }

    @Test
    fun `positions on the surface's border land in the border cells, and a position off the surface lights no cell - the raw keeps it as the platform gave it`() {
        val r = rig()
        r.down(0, 799.9f, 1399.9f)
        r.down(1, 0f, 0f)
        assertTrue(r.rec.isTouched(20 * 35 - 1))
        assertTrue(r.rec.isTouched(0))
        assertEquals(2, r.rec.touchedCount)
        // off the surface: on its right edge (x = width), on its bottom edge, left of it, and in the strip above it (a negative y: the strip is not part of the surface)
        r.down(2, 800f, 100f)
        r.down(3, 100f, 1400f)
        r.down(4, -5f, 100f)
        r.down(5, 300f, -30f)
        assertEquals("none of the four lit a cell (the server's own test: x >= 0, y >= 0, x < width, y < height)", 2, r.rec.touchedCount)
        val strokes = r.rec.measurements(EndReasons.DISMISSED).strokes()
        assertEquals(6, strokes.size)
        assertEquals("the raw keeps every y as given, the strip's negative one too", listOf(1399.9f, 0f, 100f, 1400f, 100f, -30f), strokes.map { (it[TouchGridContract.StrokeKeys.Y] as List<*>).single() })
    }

    @Test
    fun `a finger in the strip lights nothing, one that comes from it into the grid lights only the cell it enters, and one that leaves the grid for it lights nothing new`() {
        val r = rig()
        assertFalse("a landing in the strip", r.down(0, 100f, -30f))
        assertEquals(0, r.rec.touchedCount)
        assertTrue("the first sample on the surface lights its own cell: row 0, column 2", r.move(0, Triple(50_008L, 100f, 30f)))
        assertEquals(1, r.rec.touchedCount)
        assertTrue(r.rec.isTouched(2))
        assertFalse("a segment that ends in the strip visits nothing", r.move(0, Triple(50_016L, 300f, -50f)))
        assertEquals(1, r.rec.touchedCount)
        assertTrue("a segment from the strip lights the cell of its end on the surface (column 12) and none between", r.move(0, Triple(50_024L, 500f, 30f)))
        assertEquals(2, r.rec.touchedCount)
        assertTrue(r.rec.isTouched(12))
        for (col in 3..11) assertFalse("column $col lies between", r.rec.isTouched(col))
        val y = r.rec.measurements(EndReasons.DISMISSED).strokes().single()[TouchGridContract.StrokeKeys.Y]
        assertEquals("the raw keeps the strip's samples as they came", listOf(-30f, 30f, -50f, 30f), y)
    }

    @Test
    fun `a sweep of every row lights every required cell, completes the run, and shows no mark - a regular sweep is not a dead spot`() {
        val r = rig()
        r.sweepAllRows()
        assertTrue(r.rec.isComplete)
        assertEquals("the 200 required cells; the 500 free ones are crossed and not counted", 200, r.rec.touchedCount)
        assertEquals(200, r.rec.totalCells)
        assertEquals("no false mark on a regular zigzag", 0, r.rec.displayedSuspects.size)
        val m = r.rec.measurements(EndReasons.AUTO_COMPLETE)
        assertEquals("the finger was still down when the last cell lit", StrokeEnds.OPEN_AT_END, m.strokes().single()[TouchGridContract.StrokeKeys.END])
        assertFalse("no marks are shown after an automatic completion (E21 item 2): the key is ABSENT, not []", m.containsKey(Keys.DISPLAYED_SUSPECTS))
    }

    @Test
    fun `a camera cut-out's cells are not required - the run completes without them and the raw says which they were`() {
        // a hole at the window's top, x 350..450, y 100..160: grid rows 0 and 1 (y 0..60 in the surface), columns 8..11 — four frame cells and four cells inside a triangle
        val r = rig(TouchGridTestKit.environment(cutoutRectsPx = Reading.Value(listOf(PxRect(350, 100, 450, 160)))))
        assertEquals("the pattern's 200 less the four frame cells the camera covers", 196, r.rec.totalCells)
        r.sweepAllRows()
        assertTrue(r.rec.isComplete)
        assertEquals(196, r.rec.touchedCount)
        assertFalse("a cut-out cell is never lit", r.rec.isTouched(9))
        val m = r.rec.measurements(EndReasons.AUTO_COMPLETE)
        assertEquals(196, m[Keys.TOTAL_CELLS])
        assertEquals(listOf(8, 9, 10, 11, 28, 29, 30, 31), m[Keys.CUTOUT_CELLS])
        assertEquals("a cut-out cell is not 'untouched': it was never required", emptyList<Int>(), m[Keys.UNTOUCHED_CELLS])
        val notRequired = m[Keys.NOT_REQUIRED_CELLS] as List<*>
        assertEquals("the contract's relation 2: totalCells = cols x rows - |cutoutCells| - |notRequiredCells|", 196, (m[Keys.GRID_COLS] as Int) * (m[Keys.GRID_ROWS] as Int) - (m[Keys.CUTOUT_CELLS] as List<*>).size - notRequired.size)
        assertTrue("and the two lists share no cell", notRequired.none { it in (m[Keys.CUTOUT_CELLS] as List<*>) })
    }

    @Test
    fun `a camera wholly in the strip takes no cell - it is above the grid, and the raw still says where it was`() {
        val r = rig(TouchGridTestKit.environment(cutoutRectsPx = Reading.Value(listOf(PxRect(350, 0, 450, 80)))))
        assertEquals(200, r.rec.totalCells)
        val m = r.rec.measurements(EndReasons.USER_MARKED_NOT_TESTED)
        assertEquals(emptyList<Int>(), m[Keys.CUTOUT_CELLS])
        assertEquals(listOf(linkedMapOf<String, Any?>("left" to 350, "top" to 0, "right" to 450, "bottom" to 80)), m[Keys.CUTOUT_RECTS_PX])
    }

    // ───────────────────────── what is shown (display only) ─────────────────────────

    /** A finger moving right at 3 mm per 8 ms along row 0, [n] steps from x = 100. Returns the time of the last sample. */
    private fun Rig.steady(n: Int, startT: Long = 50_000L): Long {
        var t = startT
        down(0, 100f, 30f, t)
        for (i in 1..n) {
            t += 8L
            move(0, Triple(t, 100f + 30f * i, 30f))
        }
        return t
    }

    @Test
    fun `a jump of a moving finger with the usual interval is a coord_jump - it is marked, and fills no cell between its ends`() {
        val r = rig()
        var t = r.steady(5)                                    // points 0..5, x 100..250, cells 2..6 lit
        t += 8L
        r.move(0, Triple(t, 650f, 30f))                        // 40 mm in one 8 ms step: the threshold is max(1.5, 3 x 3) = 9 mm; dt 8 is not above max(20, 3 x 8)
        assertEquals(1, r.rec.displayedSuspects.size)
        val s = r.rec.displayedSuspects.single()
        assertEquals(SuspectKinds.COORD_JUMP, s.kind)
        assertEquals(Triple(0, 5, 6), Triple(s.strokeIndex, s.fromPointIndex, s.toPointIndex))
        assertEquals("cells 2 to 6 (x 100..250 on 40 px cells) and the one where the finger now is (column 16) - the nine between are not lit", 6, r.rec.touchedCount)
        for (col in 7..15) assertFalse("column $col lies across the jump", r.rec.isTouched(col))
        assertTrue(r.rec.isTouched(16))
    }

    @Test
    fun `the same jump after a longer pause is a gap_jump`() {
        val r = rig()
        var t = r.steady(5)
        t += 60L                                               // 60 ms: above max(20 ms, 3 x the 8 ms median) = 24 ms
        r.move(0, Triple(t, 650f, 30f))
        assertEquals(SuspectKinds.GAP_JUMP, r.rec.displayedSuspects.single().kind)
    }

    @Test
    fun `the marks' wire form is the contract's four keys - kind, strokeIndex, fromPointIndex, toPointIndex - and nothing derived`() {
        val r = rig()
        var t = r.steady(5)
        t += 60L
        r.move(0, Triple(t, 650f, 30f))
        val m = r.rec.measurements(EndReasons.USER_MARKED_NOT_WORKING)
        assertEquals(
            listOf(linkedMapOf<String, Any?>("kind" to "gap_jump", "strokeIndex" to 0, "fromPointIndex" to 5, "toPointIndex" to 6)),
            m.suspects()
        )
    }

    @Test
    fun `a still finger is not read - a big step after a pause is no jump, and the segment fills`() {
        val r = rig()
        var t = 50_000L
        r.down(0, 100f, 30f, t)
        repeat(5) { t += 8L; r.move(0, Triple(t, 100f, 30f)) }  // 5 samples in the same place: speed 0
        t += 8L
        r.move(0, Triple(t, 650f, 30f))
        assertEquals(0, r.rec.displayedSuspects.size)
        assertEquals("cells 2 to 16 are lit: the segment filled", 15, r.rec.touchedCount)
    }

    @Test
    fun `a finger too slow to be moving, and a stroke with too few steps behind it, are not read either`() {
        val slow = rig()
        var t = 50_000L
        slow.down(0, 100f, 30f, t)
        for (i in 1..5) { t += 8L; slow.move(0, Triple(t, 100f + i, 30f)) }   // 0.1 mm per 8 ms = 12.5 mm/s, below 20
        t += 8L
        slow.move(0, Triple(t, 650f, 30f))
        assertEquals(0, slow.rec.displayedSuspects.size)

        val short = rig()
        t = 50_000L
        short.down(0, 100f, 30f, t)
        for (i in 1..2) { t += 8L; short.move(0, Triple(t, 100f + 30f * i, 30f)) }  // two steps only: the third is the jump
        t += 8L
        short.move(0, Triple(t, 650f, 30f))
        assertEquals(0, short.rec.displayedSuspects.size)
    }

    @Test
    fun `a stroke that lifts within 5 mm of an edge heading to it is marked edge_exit with the same point twice - and one that does not, is not`() {
        val r = rig()
        r.down(0, 700f, 100f, 50_000L)
        r.move(0, Triple(50_008L, 730f, 100f), Triple(50_016L, 760f, 100f), Triple(50_024L, 790f, 100f))
        r.up(0, 795f, 100f, 50_032L)                           // 5 px = 0.5 mm from the right edge, still heading right
        val s = r.rec.displayedSuspects.single()
        assertEquals(SuspectKinds.EDGE_EXIT, s.kind)
        assertEquals(Triple(0, 4, 4), Triple(s.strokeIndex, s.fromPointIndex, s.toPointIndex))

        val inside = rig()
        inside.down(0, 300f, 100f, 50_000L)
        inside.move(0, Triple(50_008L, 330f, 100f), Triple(50_016L, 360f, 100f))
        inside.up(0, 365f, 100f, 50_024L)
        assertEquals(0, inside.rec.displayedSuspects.size)

        val away = rig()                                         // within the margin, but heading away from the edge
        away.down(0, 700f, 100f, 50_000L)
        away.move(0, Triple(50_008L, 740f, 100f), Triple(50_016L, 790f, 100f))
        away.up(0, 780f, 100f, 50_024L)
        assertEquals(0, away.rec.displayedSuspects.size)
    }

    @Test
    fun `an edge exit by a cancel is a system gesture, not an exit - it is recorded as the stroke's end and marked nowhere`() {
        val r = rig()
        r.down(0, 700f, 100f, 50_000L)
        r.move(0, Triple(50_008L, 730f, 100f), Triple(50_016L, 760f, 100f), Triple(50_024L, 795f, 100f))
        r.rec.pointerEnded(0, StrokeEnds.CANCEL, null)
        assertEquals(0, r.rec.displayedSuspects.size)
        assertEquals(StrokeEnds.CANCEL, r.rec.measurements(EndReasons.DISMISSED).strokes().single()[TouchGridContract.StrokeKeys.END])
    }

    @Test
    fun `an UP that repeats the last position still has a direction - it comes from the nearest earlier sample at another place`() {
        val r = rig()
        r.down(0, 700f, 100f, 50_000L)
        r.move(0, Triple(50_008L, 730f, 100f), Triple(50_016L, 760f, 100f), Triple(50_024L, 790f, 100f))
        r.up(0, 790f, 100f, 50_032L)                           // the platform's UP reports where the last MOVE was
        val s = r.rec.displayedSuspects.single()
        assertEquals(SuspectKinds.EDGE_EXIT, s.kind)
        assertEquals(Triple(0, 4, 4), Triple(s.strokeIndex, s.fromPointIndex, s.toPointIndex))

        val still = rig()                                        // a finger that never moved has no direction: no exit
        still.down(0, 790f, 100f, 50_000L)
        still.up(0, 790f, 100f, 50_010L)
        assertEquals(0, still.rec.displayedSuspects.size)
    }

    // ───────────────────────── fingers and the strokes ─────────────────────────

    @Test
    fun `maxSimultaneousPointers is the most fingers down at once, and never goes back down - one stroke per finger`() {
        val r = rig()
        r.down(0, 10f, 10f, 50_000L)
        r.down(1, 100f, 100f, 50_001L)
        r.down(2, 200f, 200f, 50_002L)
        r.up(1, 100f, 100f, 50_003L)
        r.down(3, 300f, 300f, 50_004L)
        assertEquals(3, r.rec.maxSimultaneousPointers)
        val m = r.rec.measurements(EndReasons.DISMISSED)
        assertEquals(3, m[Keys.MAX_SIMULTANEOUS_POINTERS])
        assertEquals(listOf(0, 1, 2, 3), m.strokes().map { it[TouchGridContract.StrokeKeys.POINTER_ID] })
        assertEquals(
            listOf(StrokeEnds.OPEN_AT_END, StrokeEnds.UP, StrokeEnds.OPEN_AT_END, StrokeEnds.OPEN_AT_END),
            m.strokes().map { it[TouchGridContract.StrokeKeys.END] }
        )
    }

    @Test
    fun `every kind of end is recorded as itself - up, cancel, cancel_flagged - and an unknown end is refused`() {
        val r = rig()
        r.down(0, 10f, 10f, 50_000L)
        r.rec.pointerEnded(0, StrokeEnds.UP, r.sample(10f, 10f, 50_005L))
        r.down(1, 20f, 20f, 50_010L)
        r.rec.pointerEnded(1, StrokeEnds.CANCEL, null)
        r.down(2, 30f, 30f, 50_020L)
        r.rec.pointerEnded(2, StrokeEnds.CANCEL_FLAGGED, r.sample(30f, 30f, 50_025L))
        assertEquals(
            listOf(StrokeEnds.UP, StrokeEnds.CANCEL, StrokeEnds.CANCEL_FLAGGED),
            r.rec.measurements(EndReasons.DISMISSED).strokes().map { it[TouchGridContract.StrokeKeys.END] }
        )
        assertThrows(IllegalArgumentException::class.java) { r.rec.pointerEnded(0, "lifted", null) }
        assertFalse("an end for a finger that is not down is ignored", r.rec.pointerEnded(9, StrokeEnds.UP, null))
        assertFalse("a move of a finger that is not down is ignored", r.move(9, Triple(1L, 1f, 1f)))
    }

    @Test
    fun `a landing with the id of a finger never lifted closes the old stroke where it stood`() {
        val r = rig()
        r.down(0, 10f, 10f, 50_000L)
        r.down(0, 400f, 400f, 50_100L)
        val strokes = r.rec.measurements(EndReasons.DISMISSED).strokes()
        assertEquals(2, strokes.size)
        assertEquals(listOf(StrokeEnds.OPEN_AT_END, StrokeEnds.OPEN_AT_END), strokes.map { it[TouchGridContract.StrokeKeys.END] })
    }

    // ───────────────────────── the cap (E18) and what cannot be written ─────────────────────────

    /** One finger moving right at 3 mm per 8 ms along row 0: [n] samples, the first being the landing. */
    private fun longStroke(r: Rig, n: Int, startT: Long = 50_000L) {
        var t = startT
        r.down(0, 5f, 30f, t)
        for (i in 1 until n) {
            t += 8L
            r.move(0, Triple(t, 5f + 30f * i, 30f))
        }
    }

    @Test
    fun `past the cap recording stops, the run goes on, the cells keep counting, and pathCap says so - nothing is cut silently`() {
        val capped = rig(maxPoints = 10)
        val free = rig()
        longStroke(capped, 25)
        longStroke(free, 25)
        assertEquals("the cells count exactly as without a cap", free.rec.touchedCount, capped.rec.touchedCount)
        assertEquals("the sweep crosses 19 columns (x 5..725 on 40 px cells)", 19, capped.rec.touchedCount)
        val m = capped.rec.measurements(EndReasons.USER_MARKED_NOT_WORKING)
        val s = m.strokes().single()
        assertEquals(10, (s[TouchGridContract.StrokeKeys.X] as List<*>).size)
        assertEquals(10, (s[TouchGridContract.StrokeKeys.Y] as List<*>).size)
        assertEquals(10, (s[TouchGridContract.StrokeKeys.DT_MS] as List<*>).size)
        assertEquals(10, (s[TouchGridContract.StrokeKeys.TOUCH_MAJOR_PX] as List<*>).size)
        assertEquals(linkedMapOf<String, Any?>("maxPoints" to 10, "reached" to true, "pointsDropped" to 15), m[Keys.PATH_CAP])
        val fine = free.rec.measurements(EndReasons.USER_MARKED_NOT_WORKING)
        assertEquals(linkedMapOf<String, Any?>("maxPoints" to 15_000, "reached" to false, "pointsDropped" to 0), fine[Keys.PATH_CAP])
    }

    @Test
    fun `a finger that lands after the cap makes no stroke - its cell still lights and its sample is counted as dropped`() {
        val r = rig(maxPoints = 3)
        r.down(0, 5f, 30f, 50_000L)
        r.move(0, Triple(50_008L, 20f, 30f), Triple(50_016L, 40f, 30f))     // 3 points: the cap is reached with nothing dropped
        assertEquals(linkedMapOf<String, Any?>("maxPoints" to 3, "reached" to true, "pointsDropped" to 0), r.rec.measurements(EndReasons.DISMISSED)[Keys.PATH_CAP])
        val before = r.rec.touchedCount
        assertTrue(r.down(1, 260f, 460f, 50_100L))          // row 11, column 6: the diagonal's cell in that row
        assertTrue(r.rec.touchedCount > before)
        val m = r.rec.measurements(EndReasons.DISMISSED)
        assertEquals("no new stroke", 1, m.strokes().size)
        assertEquals(1, (m[Keys.PATH_CAP] as Map<*, *>)[TouchGridContract.PathCapKeys.POINTS_DROPPED])
    }

    @Test
    fun `a number that cannot be written is dropped and COUNTED - the stroke has no hole, and the raw still encodes`() {
        val r = rig()
        r.down(0, 100f, 100f, 50_000L)
        r.move(0, Triple(50_008L, Float.NaN, 100f), Triple(50_016L, 110f, Float.POSITIVE_INFINITY), Triple(50_024L, 120f, 100f))
        val m = r.rec.measurements(EndReasons.DISMISSED)
        val s = m.strokes().single()
        assertEquals(listOf(100f, 120f), s[TouchGridContract.StrokeKeys.X])
        assertEquals("dt is taken from the last RECORDED sample", listOf(0L, 24L), s[TouchGridContract.StrokeKeys.DT_MS])
        assertEquals(2, (m[Keys.PATH_CAP] as Map<*, *>)[TouchGridContract.PathCapKeys.POINTS_DROPPED])
        val encoded = MeasurementsJson.encode(m)!!
        assertFalse("an unwritable number would have made the whole map measurementsUnencodable", encoded.contains(MeasurementsJson.ENCODE_FAILED_KEY))
    }

    @Test
    fun `a contact major axis the platform does not give is left out of the stroke - never filled with zeros - and the reason is declared`() {
        val r = rig()
        r.down(0, 100f, 100f, 50_000L, major = null)
        r.down(1, 300f, 300f, 50_001L, major = 64.5f)
        val m = r.rec.measurements(EndReasons.DISMISSED)
        val (a, b) = m.strokes()
        assertFalse(a.containsKey(TouchGridContract.StrokeKeys.TOUCH_MAJOR_PX))
        assertEquals(listOf(64.5f), b[TouchGridContract.StrokeKeys.TOUCH_MAJOR_PX])
        assertEquals("not_reported_by_device", m.absentReasons()["strokes.touchMajorPx"])
    }

    // ───────────────────────── the screen's answers ─────────────────────────

    @Test
    fun `the map holds exactly the contract's keys when every answer is there - and an automatic completion has no displayedSuspects and no absentFieldReasons`() {
        val r = rig()
        r.rec.setUnbufferedDispatch(Reading.Value(true))
        r.sweepAllRows()
        val keys = r.rec.measurements(EndReasons.AUTO_COMPLETE).keys
        assertEquals(
            setOf(
                Keys.GRID_COLS, Keys.GRID_ROWS, Keys.TOTAL_CELLS, Keys.TOUCHED_CELLS, Keys.ELAPSED_MS, Keys.TIME_TO_FIRST_TOUCH_MS, Keys.END_REASON,
                Keys.MAX_SIMULTANEOUS_POINTERS, Keys.SURFACE_WIDTH_PX, Keys.SURFACE_HEIGHT_PX, Keys.SURFACE_ORIGIN_X_PX, Keys.SURFACE_ORIGIN_Y_PX,
                Keys.DISPLAY_WIDTH_PX, Keys.DISPLAY_HEIGHT_PX, Keys.MODE_PHYSICAL_WIDTH_PX, Keys.MODE_PHYSICAL_HEIGHT_PX,
                Keys.XDPI, Keys.YDPI, Keys.DENSITY_DPI, Keys.PX_PER_MM, Keys.DENSITY_SOURCE, Keys.DENSITY_CHECK,
                Keys.CELL_TARGET_MM, Keys.CELL_WIDTH_PX, Keys.CELL_HEIGHT_PX, Keys.UNTOUCHED_CELLS, Keys.CUTOUT_CELLS, Keys.GRID_PATTERN, Keys.NOT_REQUIRED_CELLS,
                Keys.GESTURE_INSETS_PX, Keys.MANDATORY_GESTURE_INSETS_PX, Keys.EXCLUSION_RECTS_PX, Keys.SYSTEM_BARS_HIDDEN, Keys.CUTOUT_RECTS_PX,
                Keys.ROUNDED_CORNER_RADII_PX, Keys.STROKES, Keys.UNBUFFERED_DISPATCH, Keys.RESAMPLED_FLAG_STATE, Keys.PATH_CAP
            ),
            keys
        )
        assertFalse(keys.contains(Keys.ROUNDED_CORNERS_STATE))
    }

    @Test
    fun `a button end carries displayedSuspects, even empty - and the density, the cell and the edges are written as the platform gave them`() {
        val r = rig()
        r.rec.setUnbufferedDispatch(Reading.Value(true))
        r.down(0, 100f, 100f, 50_000L)
        val m = r.rec.measurements(EndReasons.USER_MARKED_NOT_WORKING)
        assertEquals(emptyList<Any>(), m.suspects())
        assertEquals(254f, m[Keys.XDPI])
        assertEquals(254f, m[Keys.YDPI])
        assertEquals(320, m[Keys.DENSITY_DPI])
        assertEquals(10.0, m[Keys.PX_PER_MM] as Double, 1e-9)
        assertEquals("XDPI", m[Keys.DENSITY_SOURCE])
        assertEquals(emptyList<String>(), m[Keys.DENSITY_CHECK])
        assertEquals(4.0, m[Keys.CELL_TARGET_MM])
        assertEquals(40.0, m[Keys.CELL_WIDTH_PX])
        assertEquals("the cell is square: the grid's 1400 px over 35 rows", 40.0, m[Keys.CELL_HEIGHT_PX])
        assertEquals(800, m[Keys.DISPLAY_WIDTH_PX])
        assertEquals(1500, m[Keys.MODE_PHYSICAL_HEIGHT_PX])
        assertEquals(linkedMapOf<String, Any?>("left" to 24, "top" to 0, "right" to 24, "bottom" to 48), m[Keys.GESTURE_INSETS_PX])
        assertEquals(listOf(linkedMapOf<String, Any?>("left" to 0, "top" to 0, "right" to 800, "bottom" to 1500)), m[Keys.EXCLUSION_RECTS_PX])
        assertEquals(true, m[Keys.SYSTEM_BARS_HIDDEN])
        assertEquals("no cut-out is [] — an answer", emptyList<Any>(), m[Keys.CUTOUT_RECTS_PX])
        assertEquals(linkedMapOf<String, Any?>("topLeft" to 80, "topRight" to 80, "bottomRight" to 80, "bottomLeft" to 80), m[Keys.ROUNDED_CORNER_RADII_PX])
        assertEquals(true, m[Keys.UNBUFFERED_DISPATCH])
        assertFalse(m.containsKey(Keys.ABSENT_FIELD_REASONS))
    }

    @Test
    fun `an answer the platform could not give leaves its key out and its reason in absentFieldReasons - and the rounded corners are always one of two keys`() {
        val env = TouchGridTestKit.environment(
            modePhysicalWidthPx = Reading.Absent("read_failed:the display has no current mode"),
            modePhysicalHeightPx = Reading.Absent("read_failed:the display has no current mode"),
            gestureInsetsPx = Reading.Absent("api_below_29"),
            mandatoryGestureInsetsPx = Reading.Absent("api_below_29"),
            exclusionRectsPx = Reading.Absent("api_below_29"),
            systemBarsHidden = Reading.Absent("read_failed:the view has no root window insets yet"),
            cutoutRectsPx = Reading.Absent("read_failed:x"),
            roundedCorners = RoundedCorners.State("not_available_api"),
            sdkInt = 28
        )
        val r = rig(env)
        r.down(0, 100f, 100f, 50_000L)
        val m = r.rec.measurements(EndReasons.DISMISSED)
        for (k in listOf(
            Keys.MODE_PHYSICAL_WIDTH_PX, Keys.MODE_PHYSICAL_HEIGHT_PX, Keys.GESTURE_INSETS_PX, Keys.MANDATORY_GESTURE_INSETS_PX,
            Keys.EXCLUSION_RECTS_PX, Keys.SYSTEM_BARS_HIDDEN, Keys.CUTOUT_RECTS_PX
        )) assertFalse("$k is absent", m.containsKey(k))
        val reasons = m.absentReasons()
        assertEquals("api_below_29", reasons[Keys.GESTURE_INSETS_PX])
        assertEquals("api_below_29", reasons[Keys.EXCLUSION_RECTS_PX])
        assertEquals("read_failed:the display has no current mode", reasons[Keys.MODE_PHYSICAL_WIDTH_PX])
        assertEquals("read_failed:x", reasons[Keys.CUTOUT_RECTS_PX])
        assertEquals("never asked for unbuffered dispatch: said so", "not_reported_by_device", reasons[Keys.UNBUFFERED_DISPATCH])
        assertEquals("not_available_api", m[Keys.ROUNDED_CORNERS_STATE])
        assertFalse(m.containsKey(Keys.ROUNDED_CORNER_RADII_PX))
        assertEquals("api 28: the flag state the design names", "not_available_api", m[Keys.RESAMPLED_FLAG_STATE])
        assertEquals("with no cut-out answer the grid has no cut-out cells", emptyList<Int>(), m[Keys.CUTOUT_CELLS])
    }

    @Test
    fun `resampledFlagState is never absent - a state below API 35 and another from it`() {
        assertEquals("not_available_api", rig(TouchGridTestKit.environment(sdkInt = 34)).rec.measurements(EndReasons.DISMISSED)[Keys.RESAMPLED_FLAG_STATE])
        assertEquals("not_read_by_app", rig(TouchGridTestKit.environment(sdkInt = 35)).rec.measurements(EndReasons.DISMISSED)[Keys.RESAMPLED_FLAG_STATE])
    }

    @Test
    fun `a failed request for unbuffered dispatch is declared with the error's text`() {
        val r = rig()
        r.rec.setUnbufferedDispatch(Reading.Absent("read_failed:requestUnbufferedDispatch refused"))
        val m = r.rec.measurements(EndReasons.DISMISSED)
        assertFalse(m.containsKey(Keys.UNBUFFERED_DISPATCH))
        assertEquals("read_failed:requestUnbufferedDispatch refused", m.absentReasons()[Keys.UNBUFFERED_DISPATCH])
    }

    // ───────────────────────── the layout moves until the first finger ─────────────────────────

    @Test
    fun `the grid may be laid out again until the first finger lands - and never after`() {
        val r = Rig(configure = false)
        assertFalse(r.rec.isConfigured)
        assertFalse("a finger before any layout is ignored", r.down(0, 10f, 10f))
        assertThrows(IllegalStateException::class.java) { r.rec.measurements(EndReasons.DISMISSED) }
        assertTrue(r.rec.configure(TouchGridSurface(800, 1500, 0, 0), TouchGridTestKit.environment()))
        assertTrue(r.rec.configure(TouchGridSurface(760, 1400, 0, 0), TouchGridTestKit.environment()))     // the platform's answers changed: laid out again
        assertEquals(19, r.rec.layout!!.cols)
        assertEquals("(1400 - the strip's two cells of 40 px) / 40", 33, r.rec.layout!!.rows)
        r.down(0, 10f, 10f, 50_000L)
        assertFalse(r.rec.canReconfigure)
        assertFalse("the grid does not move under a finger", r.rec.configure(TouchGridSurface(800, 1500, 0, 0), TouchGridTestKit.environment()))
        assertEquals(33, r.rec.layout!!.rows)
    }

    // ───────────────────────── the verdict ─────────────────────────

    @Test
    fun `each end reason records its own answer, and a dismissal records none of its own`() {
        val rec = rig().rec
        assertEquals(ManualStatuses.PASS, rec.statusFor(EndReasons.AUTO_COMPLETE))
        assertEquals(ManualStatuses.FAIL, rec.statusFor(EndReasons.USER_MARKED_NOT_WORKING))
        assertEquals(ManualStatuses.NOT_TESTED, rec.statusFor(EndReasons.USER_MARKED_NOT_TESTED))
        assertNull(rec.statusFor(EndReasons.DISMISSED))
    }

    @Test
    fun `a dismissal never erases an answer - and with no touch it records nothing at all`() {
        val raw = mapOf<String, Any?>(Keys.TOUCHED_CELLS to 5)
        assertNull("no touch, no run to record", TouchGridRecorder.recordOnDismiss("pass", null))
        assertEquals("pass" to raw, TouchGridRecorder.recordOnDismiss("pass", raw))
        assertEquals("fail" to raw, TouchGridRecorder.recordOnDismiss("fail", raw))
        assertEquals("with no earlier answer it is not_tested", ManualStatuses.NOT_TESTED to raw, TouchGridRecorder.recordOnDismiss(null, raw))
    }

    @Test
    fun `an unknown end reason is refused - the wire never carries a value the contract does not list`() {
        assertThrows(IllegalArgumentException::class.java) { rig().rec.measurements("warning") }
    }

    // ───────────────────────── raw only (rule 20), and what survives storage ─────────────────────────

    @Test
    fun `the raw survives the Room codec - integers still integers, decimals exactly the digits the platform gave, lists and objects still lists and objects`() {
        val r = rig()
        r.rec.setUnbufferedDispatch(Reading.Value(true))
        r.down(0, 123.5f, 456.25f, 50_100L)
        r.move(0, Triple(50_108L, 124.7f, 457.1f))
        r.up(0, 125.3f, 458.9f, 50_116L)
        val back = MeasurementsJson.decode(MeasurementsJson.encode(r.rec.measurements(EndReasons.USER_MARKED_NOT_WORKING)))!!
        assertEquals(20L, back[Keys.GRID_COLS])
        assertEquals("FRAME_AND_DIAGONALS", back[Keys.GRID_PATTERN])
        assertEquals(200L, back[Keys.TOTAL_CELLS])
        assertEquals("the left-out cells are a list of whole numbers after the codec", (0 until 700).filter { r.layout.isNotRequired(it) }.map { it.toLong() }, back[Keys.NOT_REQUIRED_CELLS])
        assertEquals(1L, back[Keys.MAX_SIMULTANEOUS_POINTERS])
        assertEquals("XDPI", back[Keys.DENSITY_SOURCE])
        assertEquals(10.0, back[Keys.PX_PER_MM] as Double, 1e-9)
        @Suppress("UNCHECKED_CAST") val stroke = (back[Keys.STROKES] as List<Map<String, Any?>>).single()
        assertEquals(100L, stroke["downAtMs"])
        assertEquals(listOf(0L, 8L, 8L), stroke["dtMs"])
        assertEquals("a float written as its own shortest digits is read back as those digits", listOf(123.5, 124.7, 125.3), stroke["x"])
        assertEquals(listOf(456.25, 457.1, 458.9), stroke["y"])
        assertEquals("up", stroke["end"])
        @Suppress("UNCHECKED_CAST") val cap = back[Keys.PATH_CAP] as Map<String, Any?>
        assertEquals(15_000L, cap["maxPoints"])
        assertEquals(false, cap["reached"])
        assertEquals(0L, cap["pointsDropped"])
    }

    @Test
    fun `the largest run the cap allows is far under the report-raw guard - 15 000 points, measured on the real producer`() {
        val r = rig()
        r.rec.setUnbufferedDispatch(Reading.Value(true))
        var t = 50_000L
        var seed = 12345L
        fun next(): Float {
            seed = seed * 6364136223846793005L + 1442695040888963407L
            return ((seed ushr 33) % 100_000).toFloat() / 100_000f
        }
        var points = 0
        var stroke = 0
        while (points < 15_000) {
            val id = stroke % 8
            val x0 = 1f + next() * 700f
            val y0 = 1f + next() * 1400f
            r.down(id, x0, y0, t, major = 60f + next() * 40f)
            points++
            for (i in 1 until 2_500) {
                if (points >= 15_000) break
                t += 8L
                r.rec.pointerMoved(id, listOf(PointerSample(t, x0 + (i % 700) + next(), y0 + (i % 90) + next(), 60f + next() * 40f)))
                points++
            }
            r.rec.pointerEnded(id, StrokeEnds.UP, null)
            stroke++
        }
        val raw = r.rec.measurements(EndReasons.USER_MARKED_NOT_WORKING)
        val cap = raw[Keys.PATH_CAP] as Map<*, *>
        assertEquals("the cap was reached by the points recorded", true, cap[TouchGridContract.PathCapKeys.REACHED])
        val bytes = MeasurementsJson.encode(raw)!!.toByteArray(Charsets.UTF_8).size
        println("TOUCHGRID_RAW_BYTES_AT_15000_POINTS=$bytes")
        assertTrue("15 000 points encode to $bytes bytes: far under the 900 000 byte guard", bytes in 1..600_000)
    }
}
