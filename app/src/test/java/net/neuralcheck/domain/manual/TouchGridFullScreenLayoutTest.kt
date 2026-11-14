package net.neuralcheck.domain.manual

import net.neuralcheck.domain.manual.TouchGridOverlay.Box
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * The full-screen M-02 «frame + X» grid, computed — user decisions 2026-10-21: the dialog is the whole screen; the test is a frame of cells on the screen's edge with two crossing
 * diagonals («يفحص الزوايا فقط وحرف X في المنتصف»); the buttons cover no required cell; and, to match the system's own touch test, the cells are SQUARE 4 mm ones under an empty strip at
 * the top, and no text stands on the cells («لا نصّ فوق الخلايا. إن احتاج العنوان والعدّاد مكاناً فليكن في الشريط العلوي الفارغ»). The geometry is [TouchGridLayout] (the 4 mm rule, the
 * strip, the pattern of [TouchGridPattern], the cells a camera cut-out takes) on the WHOLE window and [TouchGridOverlay] (where the text and the technician's two buttons stand) — both the
 * REAL ones; only the platform's answers are scripted (`TouchGridTestKit`). The dialog's own drawing of that geometry is `TouchGridRunContentLayoutTest`'s (rendered); this file is the arithmetic.
 *
 * It REPLACES (rule 23) the file of the same name of app-engineer-8, whose checks pinned the 6 mm grid on the whole window (the text standing on the free triangles, a button two cells high,
 * the camera's hole of 4 columns x 2 rows). What it proves now, on five screens (the user's 1440x3040, 20:9 FHD+ and QHD+, 16:9, and a wider 3:4) and with three camera cut-outs (none; a punch
 * hole at the top centre that lies wholly in the strip; a hole 23 mm wide at the top right that reaches a cell and a half into the grid):
 *  (أ) the cells are square, tile the grid's region exactly, and the grid ends at the bottom edge with the strip above it;
 *  (ب) the text stands in the strip, clear of the camera, and the two buttons stand on free cells only — no REQUIRED cell's rectangle meets any of their boxes, and none of them stands on a
 *      cut-out cell — and the buttons are a finger's size; the checker is itself checked (rule 16: a tool is measured before its answer is believed);
 *  (ج) the contract's five relations (wire-m02-frame-x-2026-10-21.md) on the raw the recorder writes, on every screen with and without the camera, for every end reason;
 *  (د) the place of the text and the buttons is a function of the grid only;
 *  (هـ) which screen sizes the buttons fit in, swept; and the places on the five screens, printed for the report.
 */
class TouchGridFullScreenLayoutTest {

    private class Screen(val name: String, val w: Int, val h: Int, val xdpi: Float, val densityDpi: Int)

    private val screens = listOf(
        Screen("1440x3040 xdpi 502 (the user's phone's size)", 1440, 3040, 502f, 560),
        Screen("20:9 FHD+  1080x2400 xdpi 400", 1080, 2400, 400f, 420),
        Screen("20:9 QHD+  1440x3200 xdpi 515", 1440, 3200, 515f, 560),
        Screen("16:9       1080x1920 xdpi 440", 1080, 1920, 440f, 440),
        Screen("3:4 wider  1200x1600 xdpi 400", 1200, 1600, 400f, 420)
    )

    /** The camera cut-outs the checks run with (window frame; px). */
    private enum class Hole { NONE, TOP_CENTRE_IN_STRIP, TOP_RIGHT_DEEP }

    private fun holeRects(s: Screen, hole: Hole): List<PxRect> = when (hole) {
        Hole.NONE -> emptyList()
        // a punch hole 100 px wide and 100 px deep at the top centre: wholly inside the strip (147 px at the least on these screens)
        Hole.TOP_CENTRE_IN_STRIP -> listOf(PxRect(s.w / 2 - 50, 0, s.w / 2 + 50, 100))
        // 23 mm wide at the top right (the width the user measured on his device's earlier grid) and reaching a cell and a half into the grid: it takes cells of the first two rows
        Hole.TOP_RIGHT_DEEP -> {
            val l = TouchGridLayout.compute(TouchGridSurface(s.w, s.h, 0, 0), env(s, Hole.NONE))
            val width = Math.round(23.0 * l.density.pxPerMm).toInt()
            listOf(PxRect(s.w - width, 0, s.w, l.gridTopPx + Math.round(1.5 * l.cellHeightPx).toInt()))
        }
    }

    /** The platform's answers for [s]: the display is the window; the camera hole [hole] (window frame). */
    private fun env(s: Screen, hole: Hole = Hole.TOP_CENTRE_IN_STRIP) = TouchGridTestKit.environment(
        xdpi = s.xdpi, ydpi = s.xdpi, densityDpi = s.densityDpi,
        displayWidthPx = Reading.Value(s.w), displayHeightPx = Reading.Value(s.h),
        modePhysicalWidthPx = Reading.Value(s.w), modePhysicalHeightPx = Reading.Value(s.h),
        exclusionRectsPx = Reading.Value(listOf(PxRect(0, 0, s.w, s.h))),
        cutoutRectsPx = Reading.Value(holeRects(s, hole))
    )

    private fun layoutOf(s: Screen, hole: Hole = Hole.TOP_CENTRE_IN_STRIP) = TouchGridLayout.compute(TouchGridSurface(s.w, s.h, 0, 0), env(s, hole))

    /** Every cell whose rectangle shares an area with one of [boxes] (exact, no sampling; the cell's place written here, not read from the overlay) — the checker the tests below use, apart from the one under test. */
    private fun cellsMetBy(l: TouchGridLayout, boxes: List<Box>): List<Int> =
        (0 until l.cols * l.rows).filter { i ->
            val top = l.gridTopPx.toDouble()
            val cell = Box((i % l.cols) * l.cellWidthPx, top + (i / l.cols) * l.cellHeightPx, (i % l.cols + 1) * l.cellWidthPx, top + (i / l.cols + 1) * l.cellHeightPx)
            boxes.any { it.intersects(cell) }
        }

    private fun fmt(x: Double) = String.format(Locale.ROOT, "%.1f", x)

    // ───────────────────────── (أ) the grid is square, under a strip, to the bottom edge ─────────────────────────

    @Test
    fun `A - the cells are square, tile the grid's region exactly, the strip is two cells at least and less than three, and the grid ends at the bottom edge - on every screen`() {
        assertEquals("the 4 mm rule", 4.0, TouchGridContract.CELL_TARGET_MM, 0.0)
        for (s in screens) for (hole in Hole.values()) {
            val l = layoutOf(s, hole)
            val tag = "${s.name} / $hole"
            assertEquals("$tag: the grid's width is the window's", s.w.toDouble(), l.cols * l.cellWidthPx, 1e-6)
            assertEquals("$tag: the grid's height", l.surface.heightPx.toDouble(), l.rows * l.cellHeightPx, 1e-6)
            assertEquals("$tag: the window is what it was given", TouchGridSurface(s.w, s.h, 0, 0), l.windowSurface)
            assertEquals("$tag: the touch surface is the grid's region, below the strip", TouchGridSurface(s.w, s.h - l.gridTopPx, 0, l.gridTopPx), l.surface)
            assertEquals("$tag: the grid ends at the bottom edge", s.h, l.gridTopPx + l.surface.heightPx)
            assertTrue("$tag: the cells are square (${l.cellWidthPx} x ${l.cellHeightPx})", Math.abs(l.cellWidthPx - l.cellHeightPx) <= 0.5 / l.rows + 1e-9)
            assertTrue("$tag: the strip is two cells at least (${l.gridTopPx} px, a cell is ${l.cellHeightPx})", l.gridTopPx >= TouchGridContract.TOP_STRIP_CELLS * l.cellHeightPx - 1.0)
            assertTrue("$tag: and less than three (the rest is whole rows)", l.gridTopPx < (TouchGridContract.TOP_STRIP_CELLS + 1) * l.cellHeightPx)
            val cellMm = l.cellWidthPx / l.density.pxPerMm
            assertTrue("$tag: a cell is about 4 mm ($cellMm)", cellMm in 3.5..4.5)
            assertTrue("$tag: about 18 columns on a phone (${l.cols})", l.cols in 14..21)
        }
    }

    // ───────────────────────── (ب) the text is in the strip, the buttons stand on free cells ─────────────────────────

    @Test
    fun `B - the text stands in the strip clear of the camera and the buttons on cells the pattern leaves out - no required cell is met by any of them, on every screen and with every camera hole`() {
        for (s in screens) for (hole in Hole.values()) {
            val tag = "${s.name} / $hole"
            val l = layoutOf(s, hole)
            val p = TouchGridOverlay.place(l)
            val row = p.buttons
            val text = p.text
            assertNotNull("$tag: the buttons have a place", row)
            assertNotNull("$tag: the text has a place", text)
            row!!; text!!

            // the production checker, and a second one written here (they must agree); the buttons meet only cells the pattern leaves out - not a required cell, not a cut-out cell either
            val all = listOf(text, row.left, row.right, row.row)
            assertEquals("$tag: required cells met by the elements", emptyList<Int>(), TouchGridOverlay.requiredCellsMetBy(l, all))
            val met = cellsMetBy(l, all)
            assertTrue("$tag: every cell an element meets is a free one (met ${met.filterNot { l.isNotRequired(it) }})", met.all { l.isNotRequired(it) })
            assertTrue("$tag: the camera's cells are met by nothing", met.none { l.isCutout(it) })

            // the text: in the strip — no cell at all under it — and clear of every cut-out rectangle
            assertTrue("$tag: the text is in the strip (bottom ${text.bottom}, the strip ${l.gridTopPx})", text.top >= 0.0 && text.bottom <= l.gridTopPx)
            assertEquals("$tag: no cell of the grid is under the text", emptyList<Int>(), cellsMetBy(l, listOf(text)))
            for (r in l.cutoutRectsInViewPx) {
                val rb = Box(r.left.toDouble(), r.top.toDouble(), r.right.toDouble(), r.bottom.toDouble())
                assertFalse("$tag: the text meets the camera's rectangle $r", text.intersects(rb))
            }
            assertTrue("$tag: the text is at least ${TouchGridOverlay.TEXT_MIN_COLS} cells wide (${text.width} px)", text.width >= TouchGridOverlay.TEXT_MIN_COLS * l.cellWidthPx)

            // the sizes of the buttons: a finger's (9 mm, 48 dp) high and wide; the two apart by the free columns; whole pixels; inside the window
            val mm = l.density.pxPerMm
            for (b in row.buttons) {
                assertTrue("$tag: a button is at least 9 mm high (${b.height / mm} mm)", b.height >= 9.0 * mm)
                assertTrue("$tag: a button is at least 9 mm wide (${b.width / mm} mm)", b.width >= 9.0 * mm)
                assertTrue("$tag: inside the window", b.left >= 0 && b.top >= 0 && b.right <= s.w && b.bottom <= s.h)
                assertTrue("$tag: whole pixels, so no box leans on a required neighbour", b.left == Math.rint(b.left) && b.top == Math.rint(b.top) && b.right == Math.rint(b.right) && b.bottom == Math.rint(b.bottom))
            }
            assertEquals("$tag: both buttons are the same size", row.left.width, row.right.width, 1.0)
            assertEquals("$tag: and the same height", row.left.height, row.right.height, 0.0)
            assertTrue("$tag: free columns between the two (${row.right.left - row.left.right} px)", row.right.left - row.left.right >= TouchGridOverlay.BUTTONS_GAP_COLS * l.cellWidthPx - 2.0)
            // on a phone held upright the buttons are in the bottom triangle, above the frame's last row
            assertTrue("$tag: the buttons are in the lower half", row.row.top >= s.h / 2.0)
            assertTrue("$tag: above the last row of the frame", row.row.bottom <= l.gridTopPx + (l.rows - 1) * l.cellHeightPx)
            assertTrue("$tag: the text and the buttons do not meet", !text.intersects(row.row))
            println(
                "FRAME-X-PLACE $tag: ${l.cols} x ${l.rows}, strip ${l.gridTopPx} px; text ${text.width.toInt()}x${text.height.toInt()} px at (${text.left.toInt()},${text.top.toInt()}) = " +
                    "${fmt(text.width / mm)} x ${fmt(text.height / mm)} mm; buttons 2 x ${row.left.width.toInt()}x${row.left.height.toInt()} px = ${fmt(row.left.width / mm)} x ${fmt(row.left.height / mm)} mm " +
                    "at y ${row.row.top.toInt()} of ${s.h}; cut-out cells ${l.cutoutCells.size}"
            )
        }
    }

    @Test
    fun `B - the checker is measured - a box on a required cell is found, a box on a free cell is not, one pixel into a required cell is found, a shared edge is not, and a camera cell is not free`() {
        val s = screens[1]
        val l = layoutOf(s, Hole.TOP_RIGHT_DEEP)
        val row = 12                                                                         // a middle row: the frame's two cells and the diagonals' one or two
        val req = (0 until l.cols * l.rows).first { l.isRequired(it) && it / l.cols == row }
        fun cell(i: Int) = TouchGridOverlay.cellBox(l, i % l.cols, i / l.cols, 1, 1, inward = false)
        assertEquals("a box exactly on a required cell", listOf(req), TouchGridOverlay.requiredCellsMetBy(l, listOf(cell(req))))
        val freeBeforeRequired = l.notRequiredCells.first { it / l.cols == row && it % l.cols < l.cols - 1 && l.isRequired(it + 1) }     // a free cell with a required one to its right
        val freeAfterRequired = l.notRequiredCells.first { it / l.cols == row && it % l.cols > 0 && l.isRequired(it - 1) }                // and one with a required one to its left
        assertEquals("a box exactly on a free cell meets no required one", emptyList<Int>(), TouchGridOverlay.requiredCellsMetBy(l, listOf(cell(freeBeforeRequired))))
        // grown by nothing it only shares an edge with the required cell next to it (not a meeting); grown by one pixel into that cell, it meets it
        val f = cell(freeBeforeRequired)
        assertEquals("one pixel into the required cell on the right", listOf(freeBeforeRequired + 1), TouchGridOverlay.requiredCellsMetBy(l, listOf(Box(f.left, f.top, f.right + 1.0, f.bottom))))
        val g = cell(freeAfterRequired)
        assertEquals("one pixel into the required cell on the left", listOf(freeAfterRequired - 1), TouchGridOverlay.requiredCellsMetBy(l, listOf(Box(g.left - 1.0, g.top, g.right, g.bottom))))
        // the whole window meets every required cell; an empty list meets none; the strip alone meets none
        assertEquals((0 until l.cols * l.rows).filter { l.isRequired(it) }, TouchGridOverlay.requiredCellsMetBy(l, listOf(Box(0.0, 0.0, s.w.toDouble(), s.h.toDouble()))))
        assertEquals(emptyList<Int>(), TouchGridOverlay.requiredCellsMetBy(l, emptyList()))
        assertEquals("the strip meets no cell", emptyList<Int>(), TouchGridOverlay.requiredCellsMetBy(l, listOf(Box(0.0, 0.0, s.w.toDouble(), l.gridTopPx.toDouble()))))
        assertEquals("one pixel more and it meets the top row's required cells", (0 until l.cols).filter { l.isRequired(it) }, TouchGridOverlay.requiredCellsMetBy(l, listOf(Box(0.0, 0.0, s.w.toDouble(), l.gridTopPx + 1.0))))
        // the old bar shape: a strip one cell row high across the width meets every required cell of its row
        val r = 12
        val bar = Box(0.0, l.gridTopPx + r * l.cellHeightPx, s.w.toDouble(), l.gridTopPx + (r + 1) * l.cellHeightPx)
        assertEquals((0 until l.cols).map { r * l.cols + it }.filter { l.isRequired(it) }, TouchGridOverlay.requiredCellsMetBy(l, listOf(bar)))
        // the camera's cells are cut out - not required and not free: a box on them is not 'on a free cell' (cellsMetBy would list them as not free)
        val hole = l.cutoutCells.first()
        assertTrue(l.isCutout(hole) && !l.isNotRequired(hole) && !l.isRequired(hole))
        // and the inward rounding is inward: a one-cell box lies inside the cell
        val inside = TouchGridOverlay.cellBox(l, 3, 4, 2, 2)
        val exact = TouchGridOverlay.cellBox(l, 3, 4, 2, 2, inward = false)
        assertTrue(inside.left >= exact.left && inside.top >= exact.top && inside.right <= exact.right && inside.bottom <= exact.bottom)
    }

    @Test
    fun `B - the text's place follows the strip and the camera, the buttons' place the free cells - with no camera the text takes the strip's whole width, a camera at the top right moves it to the left of the hole, and the buttons stay where they were`() {
        val s = screens[0]
        val none = TouchGridOverlay.place(layoutOf(s, Hole.NONE))
        val deep = TouchGridOverlay.place(layoutOf(s, Hole.TOP_RIGHT_DEEP))
        val centre = TouchGridOverlay.place(layoutOf(s, Hole.TOP_CENTRE_IN_STRIP))
        assertEquals("the camera is at the top: the buttons are where they were", none.buttons!!.left, deep.buttons!!.left)
        assertEquals(none.buttons!!.right, deep.buttons!!.right)
        assertEquals(none.buttons!!.right, centre.buttons!!.right)
        assertEquals("no camera: the strip's whole width", 0.0 to s.w.toDouble(), none.text!!.left to none.text!!.right)
        val l = layoutOf(s, Hole.TOP_RIGHT_DEEP)
        assertTrue("the big hole took cells", l.cutoutCells.size >= 4)
        val holeLeft = l.cutoutRectsInViewPx.single().left
        assertTrue("the text is left of the hole (${deep.text!!.right} <= $holeLeft)", deep.text!!.right <= holeLeft)
        assertEquals("and it starts at the left edge", 0.0, deep.text!!.left, 0.0)
        // a punch hole at the centre, wholly in the strip: the text takes the wider side (the right one is as wide: the leftmost wins a tie)
        val c = layoutOf(s, Hole.TOP_CENTRE_IN_STRIP).cutoutRectsInViewPx.single()
        assertTrue("the text is on one side of the hole", centre.text!!.right <= c.left || centre.text!!.left >= c.right)
        assertTrue(cellsMetBy(l, listOf(deep.text!!)).isEmpty())
    }

    // ───────────────────────── (ج) the contract's relations ─────────────────────────

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.ints(key: String) = (this[key] as List<Number>).map { it.toInt() }

    private fun Map<String, Any?>.int(key: String) = (this[key] as Number).toInt()

    /** The five relations of `wire-m02-frame-x-2026-10-21.md` on one raw, with the grid's size. */
    private fun assertRelations(tag: String, raw: Map<String, Any?>) {
        val cols = raw.int(TouchGridRecorder.Keys.GRID_COLS)
        val rows = raw.int(TouchGridRecorder.Keys.GRID_ROWS)
        val cutout = raw.ints(TouchGridRecorder.Keys.CUTOUT_CELLS)
        val notRequired = raw.ints(TouchGridRecorder.Keys.NOT_REQUIRED_CELLS)
        val untouched = raw.ints(TouchGridRecorder.Keys.UNTOUCHED_CELLS)
        val total = raw.int(TouchGridRecorder.Keys.TOTAL_CELLS)
        val touched = raw.int(TouchGridRecorder.Keys.TOUCHED_CELLS)
        assertEquals("$tag: the pattern this build writes", "FRAME_AND_DIAGONALS", raw[TouchGridRecorder.Keys.GRID_PATTERN])
        for ((name, list) in listOf("cutoutCells" to cutout, "notRequiredCells" to notRequired, "untouchedCells" to untouched)) {
            assertTrue("$tag: $name are indexes of the grid", list.all { it in 0 until cols * rows })
            assertEquals("$tag: $name are sorted and without repeats", list.sorted().distinct(), list)
        }
        // 1. cutoutCells and notRequiredCells are separate
        assertEquals("$tag: relation 1", emptySet<Int>(), cutout.toSet() intersect notRequired.toSet())
        // 2. totalCells = cols x rows - |cutoutCells| - |notRequiredCells|
        assertEquals("$tag: relation 2", cols * rows - cutout.size - notRequired.size, total)
        // 3 and 4. touchedCells counts required cells; untouchedCells are required cells; touched + |untouched| = totalCells; neither list holds a cut-out or a left-out cell
        assertEquals("$tag: relation 4 (the sum)", total, touched + untouched.size)
        assertTrue("$tag: relation 4 (an untouched cell is required: not cut out, not left out)", untouched.none { it in cutout || it in notRequired })
        // the pattern: a left-out cell is not in the frame, and the cells that are not left out nor cut out are exactly the pattern minus the camera
        val pattern = TouchGridPattern.cells(cols, rows)
        assertEquals("$tag: the left-out cells are the grid minus the pattern minus the camera", (0 until cols * rows).filter { it !in pattern && it !in cutout }, notRequired)
        assertTrue("$tag: no frame cell is left out", notRequired.none { it in TouchGridPattern.frameCells(cols, rows) })
    }

    @Test
    fun `C - the relations hold on every screen, with and without the camera, for every end reason - a run that lit nothing, part of the required cells, and all of them`() {
        val seen = HashSet<String>()
        for (s in screens) for (hole in Hole.values()) {
            // a run that ended with the technician's button or a dismissal: nothing lit, and part of the required cells (with a finger on a LEFT-OUT cell as well: it counts for nothing and is recorded)
            for (endReason in listOf(TouchGridRecorder.EndReasons.USER_MARKED_NOT_WORKING, TouchGridRecorder.EndReasons.USER_MARKED_NOT_TESTED, TouchGridRecorder.EndReasons.DISMISSED)) {
                val tag = "${s.name} / $hole / $endReason"
                seen += endReason

                val none = TouchGridTestKit.Rig(env = env(s, hole), surface = TouchGridSurface(s.w, s.h, 0, 0))
                val rawNone = none.rec.measurements(endReason)
                assertRelations("$tag / nothing", rawNone)
                assertEquals("$tag / nothing", 0, rawNone.int(TouchGridRecorder.Keys.TOUCHED_CELLS))
                assertEquals("$tag / nothing: every required cell is untouched", none.layout.totalCells, rawNone.ints(TouchGridRecorder.Keys.UNTOUCHED_CELLS).size)

                val part = TouchGridTestKit.Rig(env = env(s, hole), surface = TouchGridSurface(s.w, s.h, 0, 0))
                val l = part.layout
                val required = (0 until l.cols * l.rows).filter { l.isRequired(it) }
                val free = l.notRequiredCells[l.notRequiredCells.size / 2]
                for ((n, i) in listOf(required.first(), required[required.size / 2], free).withIndex()) {
                    val (x, y) = part.centre(i % l.cols, i / l.cols)
                    part.down(n, x, y, 50_000L + n)
                    part.up(n, x, y, 50_010L + n)
                }
                val rawPart = part.rec.measurements(endReason)
                assertRelations("$tag / part", rawPart)
                assertEquals("$tag / part: two required cells lit, the left-out one counted for nothing", 2, rawPart.int(TouchGridRecorder.Keys.TOUCHED_CELLS))
                assertFalse("$tag / part: the left-out cell is not untouched - it was never required", free in rawPart.ints(TouchGridRecorder.Keys.UNTOUCHED_CELLS))
                @Suppress("UNCHECKED_CAST") val strokes = rawPart[TouchGridRecorder.Keys.STROKES] as List<Map<String, Any?>>
                assertEquals("$tag / part: the finger on the left-out cell is in the raw like the others", 3, strokes.size)
            }

            // a run that lit every required cell ends by itself: a sweep of every row lights them all (and crosses the left-out ones too)
            val tag = "${s.name} / $hole / auto_complete"
            val all = TouchGridTestKit.Rig(env = env(s, hole), surface = TouchGridSurface(s.w, s.h, 0, 0))
            all.sweepAllRows()
            assertTrue("$tag: every required cell is lit", all.rec.isComplete)
            val rawAll = all.rec.measurements(TouchGridRecorder.EndReasons.AUTO_COMPLETE)
            seen += TouchGridRecorder.EndReasons.AUTO_COMPLETE
            assertRelations(tag, rawAll)
            // relation 5: auto_complete <=> every required cell lit (the dialog writes auto_complete when the recorder is complete and never otherwise - the rendered dialog test proves that side)
            assertEquals(tag, all.layout.totalCells, rawAll.int(TouchGridRecorder.Keys.TOUCHED_CELLS))
            assertEquals(tag, emptyList<Int>(), rawAll.ints(TouchGridRecorder.Keys.UNTOUCHED_CELLS))
            assertEquals(tag, TouchGridRecorder.EndReasons.AUTO_COMPLETE, rawAll[TouchGridRecorder.Keys.END_REASON])
        }
        assertEquals("every end reason the contract has was run", TouchGridRecorder.EndReasons.ALL.toSet(), seen)
    }

    @Test
    fun `C - the recorder reports the layout's own numbers, and the camera's cells are the camera's alone - a hole in the strip takes none, one that reaches the grid takes the cells it overlaps`() {
        for (s in screens) {
            val inStrip = layoutOf(s, Hole.TOP_CENTRE_IN_STRIP)
            val deep = layoutOf(s, Hole.TOP_RIGHT_DEEP)
            val noHole = layoutOf(s, Hole.NONE)
            assertEquals("${s.name}: a punch hole wholly in the strip cuts no cell", emptyList<Int>(), inStrip.cutoutCells)
            assertTrue("${s.name}: a hole that reaches into the grid cuts cells", deep.cutoutCells.isNotEmpty())
            assertEquals("${s.name}: without a hole nothing is cut out", emptyList<Int>(), noHole.cutoutCells)
            val pattern = TouchGridPattern.cells(noHole.cols, noHole.rows)
            assertEquals("${s.name}: no camera: required = the pattern", pattern.size, noHole.totalCells)
            assertEquals("${s.name}: a camera in the strip: required = the pattern", pattern.size, inStrip.totalCells)
            assertEquals("${s.name}: with the deep camera: required = the pattern minus the camera's cells in it", pattern.size - deep.cutoutCells.count { it in pattern }, deep.totalCells)
            assertTrue("${s.name}: the hole is in the first two rows only", deep.cutoutCells.all { it / deep.cols <= 1 })

            val rig = TouchGridTestKit.Rig(env = env(s, Hole.TOP_RIGHT_DEEP), surface = TouchGridSurface(s.w, s.h, 0, 0))
            val l = rig.layout
            val raw = rig.rec.measurements(TouchGridRecorder.EndReasons.DISMISSED)
            assertEquals(l.totalCells, rig.rec.totalCells)
            assertEquals(l.totalCells, raw.int(TouchGridRecorder.Keys.TOTAL_CELLS))
            assertEquals(l.cols, raw.int(TouchGridRecorder.Keys.GRID_COLS))
            assertEquals(l.rows, raw.int(TouchGridRecorder.Keys.GRID_ROWS))
            assertEquals("${s.name}: surface width in the raw is the window's", s.w, raw.int(TouchGridRecorder.Keys.SURFACE_WIDTH_PX))
            assertEquals("${s.name}: surface height in the raw is the grid's region: the window less the strip", s.h - l.gridTopPx, raw.int(TouchGridRecorder.Keys.SURFACE_HEIGHT_PX))
            assertEquals("${s.name}: and it starts below the strip", l.gridTopPx, raw.int(TouchGridRecorder.Keys.SURFACE_ORIGIN_Y_PX))
            assertEquals("${s.name}: the declared cut-out cells are the layout's", l.cutoutCells, raw.ints(TouchGridRecorder.Keys.CUTOUT_CELLS))
            assertEquals("${s.name}: the declared left-out cells are the layout's", l.notRequiredCells, raw.ints(TouchGridRecorder.Keys.NOT_REQUIRED_CELLS))
            assertEquals(0, raw.int(TouchGridRecorder.Keys.TOUCHED_CELLS))
        }
    }

    // ───────────────────────── (د) the place is a function of the grid ─────────────────────────

    @Test
    fun `D - the place of the text and of the buttons depends on the grid alone - the same layout gives the same boxes whenever they are asked for, and a grid with no room still gets reachable buttons`() {
        for (s in screens) {
            val first = TouchGridOverlay.place(layoutOf(s))
            val later = TouchGridOverlay.place(layoutOf(s))          // 15 s later: the layout is the same, so are the boxes
            assertEquals(first.buttons!!.left, later.buttons!!.left)
            assertEquals(first.buttons!!.right, later.buttons!!.right)
            assertEquals(first.text, later.text)
        }
        // no grid, or no triangle for the buttons: along the bottom, inside the window, two separate boxes - they are how a technician ends a run with no surface
        val none = TouchGridOverlay.buttonRowWithoutGrid(1080, 2400, 120.0)
        for (b in none.buttons) assertTrue(b.left >= 0 && b.right <= 1080 && b.top >= 0 && b.bottom <= 2400 && b.height == 120.0)
        assertTrue(none.left.right < none.right.left)
    }

    @Test
    fun `a degenerate grid never fails - it has no place for the buttons or the text, and the dialog falls back (the one case where an element may meet a required cell)`() {
        for ((w, h) in listOf(20 to 20, 40 to 200, 800 to 70, 70 to 70)) {
            val l = TouchGridLayout.compute(TouchGridSurface(w, h, 0, 0), TouchGridTestKit.environment())
            val p = TouchGridOverlay.place(l)
            if (l.notRequiredCells.isEmpty()) {
                assertNull("${w}x$h: no free cell, no place for the buttons", p.buttons)
                assertNull("${w}x$h: nor for the text", p.text)
            }
            for (b in listOfNotNull(p.buttons?.left, p.buttons?.right, p.text)) assertTrue("${w}x$h: inside the window", b.left >= 0 && b.top >= 0 && b.right <= w && b.bottom <= h)
        }
    }

    // ───────────────────────── (هـ) which screens the buttons fit ─────────────────────────

    @Test
    fun `E - swept over screen sizes from 55 x 66 mm to a 200 mm tablet, upright and turned, no element meets a required cell wherever it stands, and the screens with no place for the buttons are printed`() {
        val pxPerMm = 400.0 / 25.4
        val failing = ArrayList<String>()
        var fitsSmallest: String? = null
        var smallestArea = Int.MAX_VALUE
        var swept = 0
        for (wMm in listOf(55, 58, 60, 62, 65, 68, 70, 72, 75, 78, 80, 85, 100, 120, 160, 200)) {
            for (ratio in listOf(1.2, 1.33, 1.5, 1.6, 1.78, 2.0, 2.1, 2.2, 2.3, 2.4)) for (turned in listOf(false, true)) {
                val a = Math.round(wMm * pxPerMm).toInt()
                val b = Math.round(wMm * ratio * pxPerMm).toInt()
                val (w, h) = if (turned) b to a else a to b
                val env = TouchGridTestKit.environment(
                    xdpi = 400f, ydpi = 400f, densityDpi = 400,
                    displayWidthPx = Reading.Value(w), displayHeightPx = Reading.Value(h), modePhysicalWidthPx = Reading.Value(w), modePhysicalHeightPx = Reading.Value(h)
                )
                val l = TouchGridLayout.compute(TouchGridSurface(w, h, 0, 0), env)
                val p = TouchGridOverlay.place(l)
                val name = "${wMm}mm x ratio $ratio ${if (turned) "turned" else "upright"} (${l.cols} x ${l.rows})"
                swept++
                val boxes = listOfNotNull(p.buttons?.left, p.buttons?.right, p.text)
                assertEquals("$name: an element meets a required cell", emptyList<Int>(), TouchGridOverlay.requiredCellsMetBy(l, boxes))
                if (p.buttons == null || p.text == null) failing += "$name buttons=${p.buttons != null} text=${p.text != null}"
                else if (l.cols * l.rows < smallestArea) { smallestArea = l.cols * l.rows; fitsSmallest = name }
            }
        }
        println("FRAME-X-FIT sweep of $swept screens: smallest grid with both places: $fitsSmallest; without a place (${failing.size}): ${failing.joinToString("; ")}")
        // the buttons need a run of 2 x 3 + 2 = 8 free columns over 3 rows inside one triangle: a narrow screen that is also short has none. Every one of them is a screen no phone is held upright
        // in — turned, or at most 3:2 (ratio 1.5) — and 65 mm wide at the most: a phone of 16:9 or taller, from 55 mm wide, has its place (the sweep prints the ones that fail)
        for (f in failing) {
            val width = f.substringBefore("mm").toInt()
            val ratio = f.substringAfter("ratio ").substringBefore(" ").toDouble()
            assertTrue("a screen with the buttons without a place is narrow ($width mm) and either turned or no taller than 3:2: $f", width <= 65 && (f.contains("turned") || ratio <= 1.5))
        }
    }
}
