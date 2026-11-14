package net.neuralcheck.domain.manual

import net.neuralcheck.domain.manual.TouchGridContract.DensityChecks
import net.neuralcheck.domain.manual.TouchGridContract.DensitySources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * fieldsVersion 71 (contract docs/FV68_CONTRACT.md 7-ي; design §1): the density the cells are drawn with, the grid it gives (SQUARE 4 mm cells under an empty strip at the top, user order
 * 2026-10-21), the cut-out cells, and the cells a segment crosses — on the screens the design computed (Galaxy S10 5G, S21 Ultra) and on the cases that make the procedure choose differently.
 * It REPLACES (rule 23) the assertions of the 6 mm grid that filled the window (12 x 26, 13 x 25, 113 required...), whose tests pinned the shape the user rejected.
 */
class TouchGridLayoutTest {

    private fun choose(xdpi: Float, ydpi: Float = xdpi, densityDpi: Int = 420, display: Int? = 1080, mode: Int? = 1080) =
        DensitySelector.choose(xdpi, ydpi, densityDpi, display, mode)

    /** A screen as the platform reports it: [displayW] x [displayH] px in the current mode, the panel's own [modeW] x [modeH]. */
    private fun env(xdpi: Float, densityDpi: Int, displayW: Int, displayH: Int, modeW: Int, modeH: Int, cutouts: List<PxRect> = emptyList()) =
        TouchGridTestKit.environment(
            xdpi = xdpi, ydpi = xdpi, densityDpi = densityDpi,
            displayWidthPx = Reading.Value(displayW), displayHeightPx = Reading.Value(displayH),
            modePhysicalWidthPx = Reading.Value(modeW), modePhysicalHeightPx = Reading.Value(modeH),
            cutoutRectsPx = Reading.Value(cutouts)
        )

    // ───────────────────────── the density (design §1.2) ─────────────────────────

    @Test
    fun `an unscaled screen with a believable xdpi uses xdpi - source XDPI, nothing failed`() {
        val d = choose(xdpi = 502f, display = 1440, mode = 1440)    // S10 5G at its panel's QHD+ resolution: 1440 / 502 x 25.4 = 72.9 mm
        assertEquals(DensitySources.XDPI, d.source)
        assertEquals(emptyList<String>(), d.failedChecks)
        assertEquals(502.0 / 25.4, d.pxPerMm, 1e-9)
    }

    @Test
    fun `Samsung's factory FHD+ on a QHD+ panel - the naive width is 54_6 mm, the scaled one 72_9 mm - XDPI_MODE_SCALED, and the grid is 18 columns by 36 rows under a strip of two cells`() {
        // design §1.1: the S10 5G ships at 1080 x 2280 on a 1440 x 3040 panel and may still report xdpi about 502
        val d = choose(xdpi = 502f, display = 1080, mode = 1440)
        assertEquals(DensitySources.XDPI_MODE_SCALED, d.source)
        assertEquals(emptyList<String>(), d.failedChecks)
        assertEquals("physical pixels per mm 502 / 25.4, times the share of them the display runs: 1080 / 1440", 502.0 / 25.4 * 1080 / 1440, d.pxPerMm, 1e-9)
        val layout = TouchGridLayout.compute(TouchGridSurface(1080, 2280, 0, 0), env(502f, 420, 1080, 2280, 1440, 3040))
        // 4 mm = 59.3 px: round(1080 / 59.3) = 18 columns of 60 px; (2280 - 2 x 60) / 60 = 36 rows exactly; the strip is the two cells left
        assertEquals(18, layout.cols)
        assertEquals(36, layout.rows)
        assertEquals(120, layout.gridTopPx)
    }

    @Test
    fun `the design's two phones at 4 mm - 18 x 36 on the S10 5G and 18 x 38 on the S21 Ultra, square 80 px cells under a strip of 160 px, 200 and 204 cells required`() {
        // design §1.3: S10 5G 72.86 x 153.82 mm and S21 Ultra 71.02 x 157.83 mm; the counts are those of tools-m02-shape-reference.js (independent of this code)
        val s10 = TouchGridLayout.compute(TouchGridSurface(1440, 3040, 0, 0), env(502f, 560, 1440, 3040, 1440, 3040))
        assertEquals(18 to 36, s10.cols to s10.rows)
        assertEquals(648, s10.cols * s10.rows)
        assertEquals(160, s10.gridTopPx)
        assertEquals("the surface is the grid's region: 1440 x 2880, 160 px below the window's top", TouchGridSurface(1440, 2880, 0, 160), s10.surface)
        assertEquals(80.0, s10.cellWidthPx, 0.0)
        assertEquals(80.0, s10.cellHeightPx, 0.0)
        // the frame 104 + 96 cells of the two diagonals beyond it
        assertEquals(200, s10.totalCells)
        assertEquals(648 - 200, s10.notRequiredCells.size)
        val s21 = TouchGridLayout.compute(TouchGridSurface(1440, 3200, 0, 0), env(515f, 560, 1440, 3200, 1440, 3200))
        assertEquals(18 to 38, s21.cols to s21.rows)
        assertEquals(684, s21.cols * s21.rows)
        assertEquals(160, s21.gridTopPx)
        assertEquals(204, s21.totalCells)
    }

    @Test
    fun `two believable candidates, or none, is an ambiguity - densityDpi draws the grid and densityCheck says which condition failed`() {
        // both: naive 1000 / 423.3 x 25.4 = 60 mm, scaled 60 x 1100 / 1000 = 66 mm — both are possible phone widths
        val both = choose(xdpi = 423.33f, densityDpi = 400, display = 1000, mode = 1100)
        assertEquals(DensitySources.DENSITY_DPI_FALLBACK, both.source)
        assertEquals(listOf(DensityChecks.BOTH_CANDIDATES_PLAUSIBLE), both.failedChecks)
        assertEquals("the fallback is densityDpi taken as pixels per inch", 400 / 25.4, both.pxPerMm, 1e-9)
        // none: 1080 / 100 x 25.4 = 274 mm, scaled 366 mm
        val none = choose(xdpi = 100f, densityDpi = 420, display = 1080, mode = 1440)
        assertEquals(DensitySources.DENSITY_DPI_FALLBACK, none.source)
        assertEquals(listOf(DensityChecks.WIDTH_OUT_OF_RANGE), none.failedChecks)
    }

    @Test
    fun `an xdpi that disagrees with ydpi by more than 5 percent is not believed - and says so`() {
        val d = choose(xdpi = 500f, ydpi = 540f, display = 1440, mode = 1440)       // 40 apart, the limit is 27
        assertEquals(DensitySources.DENSITY_DPI_FALLBACK, d.source)
        assertEquals(listOf(DensityChecks.DPI_AXES_DIFFER), d.failedChecks)
        val inside = choose(xdpi = 500f, ydpi = 525f, display = 1440, mode = 1440)  // 25 apart, the limit is 26.25: believed
        assertEquals(DensitySources.XDPI, inside.source)
    }

    @Test
    fun `an unusable xdpi, an unknown display width, or an unknown mode are each handled by name - never by a default width`() {
        assertEquals(listOf(DensityChecks.XDPI_UNUSABLE), choose(xdpi = 0f).failedChecks)
        assertEquals(listOf(DensityChecks.XDPI_UNUSABLE), choose(xdpi = Float.NaN).failedChecks)
        assertEquals(listOf(DensityChecks.DISPLAY_WIDTH_UNKNOWN), choose(xdpi = 400f, display = null).failedChecks)
        // an unknown mode is one candidate (the naive one): 1080 / 400 x 25.4 = 68.6 mm, believed
        assertEquals(DensitySources.XDPI, choose(xdpi = 400f, display = 1080, mode = null).source)
        assertEquals(DensitySources.DENSITY_DPI_FALLBACK, choose(xdpi = 400f, densityDpi = 0, display = null).source)
        assertEquals("a densityDpi of 0 is not divided by: 1 is the floor", 1 / 25.4, choose(xdpi = 400f, densityDpi = 0, display = null).pxPerMm, 1e-9)
    }

    @Test
    fun `every densitySource and every densityCheck the selector can produce is in the exported lists - and every listed one is reachable`() {
        val all = listOf(
            choose(502f, display = 1440, mode = 1440), choose(502f, display = 1080, mode = 1440),
            choose(423.33f, densityDpi = 400, display = 1000, mode = 1100), choose(100f, display = 1080, mode = 1440),
            choose(500f, 540f, display = 1440, mode = 1440), choose(0f), choose(400f, display = null)
        )
        assertEquals(DensitySources.ALL.toSet(), all.map { it.source }.toSet())
        assertEquals(DensityChecks.ALL.toSet(), all.flatMap { it.failedChecks }.toSet())
    }

    // ───────────────────────── the grid and its cut-out ─────────────────────────

    @Test
    fun `the default test screen is 20 x 35 square cells of 40 px under a strip of 100 px, and the cell target is 4 mm - 700 cells, of which the frame and the diagonals, 200, are required`() {
        assertEquals(4.0, TouchGridContract.CELL_TARGET_MM, 0.0)
        assertEquals(2, TouchGridContract.TOP_STRIP_CELLS)
        val l = TouchGridLayout.compute(TouchGridTestKit.surface, TouchGridTestKit.environment())
        assertEquals(DensitySources.XDPI, l.density.source)
        assertEquals(10.0, l.density.pxPerMm, 1e-9)
        assertEquals(20 to 35, l.cols to l.rows)
        assertEquals(40.0, l.cellWidthPx, 1e-9)
        assertEquals(40.0, l.cellHeightPx, 1e-9)
        assertEquals("the strip is what the whole rows leave, two cells at least", 100, l.gridTopPx)
        assertEquals(TouchGridSurface(800, 1500, 0, 0), l.windowSurface)
        assertEquals("the touch surface is the grid's region of the window", TouchGridSurface(800, 1400, 0, 100), l.surface)
        assertEquals(700, l.cols * l.rows)
        assertEquals("the frame is 106 cells, the diagonals add 94 (tools-m02-shape-reference.js)", 200, l.totalCells)
        assertEquals(700 - 200, l.notRequiredCells.size)
        assertEquals(emptyList<Int>(), l.cutoutCells)
        assertEquals("FRAME_AND_DIAGONALS", l.gridPattern)
        // a corner, an edge cell, the inner corner cell (1, 1) and a cell of the middle diagonal are required; a cell inside a triangle is not
        assertEquals(
            listOf(true, true, true, true, false),
            listOf(0, 10, 1 * 20 + 1, 17 * 20 + 9, 17 * 20 + 3).map { l.isRequired(it) }
        )
        assertTrue("and a cell of a triangle is free, not cut out", l.isNotRequired(17 * 20 + 3) && !l.isCutout(17 * 20 + 3))
    }

    @Test
    fun `a grid has at least one cell on each axis and never more than the memory guard`() {
        val tiny = TouchGridLayout.compute(TouchGridSurface(20, 20, 0, 0), TouchGridTestKit.environment())
        assertEquals(1 to 1, tiny.cols to tiny.rows)
        assertEquals("a window with no room for a strip has none", 0, tiny.gridTopPx)
        val huge = TouchGridLayout.compute(
            TouchGridSurface(8000, 8000, 0, 0),
            TouchGridTestKit.environment(displayWidthPx = Reading.Absent("x"), densityDpi = 1, xdpi = 0f)   // 1 dpi: a 0.16 px cell
        )
        // the columns hit the guard; the rows are the guard's cells of 40 px less the strip's two cells
        assertEquals(TouchGridContract.MAX_CELLS_PER_AXIS to TouchGridContract.MAX_CELLS_PER_AXIS - TouchGridContract.TOP_STRIP_CELLS, huge.cols to huge.rows)
    }

    @Test
    fun `a camera cut-out removes the cells it covers from the required set - window-frame rectangles shifted by the grid's origin, and a hole wholly in the strip takes none`() {
        // the window starts 40 px below the screen's top: the grid's surface starts at 40 + 100 = 140 in the window frame of the platform
        val inStrip = TouchGridLayout.compute(
            TouchGridSurface(800, 1500, 0, 40),
            TouchGridTestKit.environment(cutoutRectsPx = Reading.Value(listOf(PxRect(350, 0, 450, 60))))      // y 0..60: above the grid's top (140)
        )
        assertEquals(TouchGridSurface(800, 1400, 0, 140), inStrip.surface)
        assertEquals("a camera wholly in the strip covers no cell", emptyList<Int>(), inStrip.cutoutCells)
        assertEquals(200, inStrip.totalCells)
        // a hole 100 x 200 px at the screen's top: y 0..200 -> grid y -140..60: rows 0 and 1 (60 / 40 = 1.5 -> ceil 2 - 1 = 1); x 350..450 -> columns 8 (350 / 40 = 8.75) to 11 (450 / 40 = 11.25 -> ceil 12 - 1)
        val l = TouchGridLayout.compute(
            TouchGridSurface(800, 1500, 0, 40),
            TouchGridTestKit.environment(cutoutRectsPx = Reading.Value(listOf(PxRect(350, 0, 450, 200))))
        )
        assertEquals(listOf(8, 9, 10, 11, 28, 29, 30, 31), l.cutoutCells)
        // the four of row 0 are in the frame: the pattern's 200 lose them; the four of row 1 are inside a triangle, so the pattern never asked for them
        assertEquals(200 - 4, l.totalCells)
        assertTrue(l.isCutout(9) && !l.isCutout(7) && !l.isCutout(20 + 9 + 3))
        assertTrue("a cut-out cell is neither required nor left out: relation 1 of the wire contract", !l.isRequired(9) && !l.isNotRequired(9))
        assertEquals("the left-out cells are the grid minus the pattern minus the camera's cells that are not in it", 700 - 200 - 4, l.notRequiredCells.size)
        assertTrue(l.notRequiredCells.none { it in l.cutoutCells })
    }

    @Test
    fun `a camera cell that is not in the pattern is cut out and not left out - the two lists never share a cell, and the cut-out is the platform's rectangle's cells whatever the pattern is`() {
        // a hole covering a cell inside a triangle (row 3, column 6 of the 20 x 35 grid: x 240..280, grid y 120..160 = window y 220..260): cutout, and NOT in notRequiredCells
        val inside = 3 * 20 + 6
        val l = TouchGridLayout.compute(
            TouchGridTestKit.surface,
            TouchGridTestKit.environment(cutoutRectsPx = Reading.Value(listOf(PxRect(250, 230, 270, 250))))
        )
        assertEquals(listOf(inside), l.cutoutCells)
        assertFalse(inside in l.notRequiredCells)
        assertTrue(inside !in TouchGridPattern.cells(20, 35))
        assertEquals("the pattern's 200 are all still required: the camera took none of them", 200, l.totalCells)
        assertEquals(700 - 200 - 1, l.notRequiredCells.size)
    }

    @Test
    fun `no margin of the app's own is added to the camera's rectangle - one pixel inside a cell takes it, one pixel short of it does not, and an edge touch takes nothing`() {
        // 20 x 35 grid of 40 px cells on 800 x 1500: the grid starts at window y 100; column 5 is x 200..240, row 0 is window y 100..140, row 1 is 140..180
        fun cut(rect: PxRect) = TouchGridLayout.compute(TouchGridTestKit.surface, TouchGridTestKit.environment(cutoutRectsPx = Reading.Value(listOf(rect)))).cutoutCells
        assertEquals("a rectangle that ends on the row boundary only touches row 1's top edge", listOf(5), cut(PxRect(210, 100, 230, 140)))
        assertEquals("one pixel past the edge takes row 1's cell of that column too", listOf(5, 20 + 5), cut(PxRect(210, 100, 230, 141)))
        assertEquals("a rectangle that ends one pixel short of column 6's left edge (240) leaves column 6 alone", listOf(5), cut(PxRect(210, 100, 239, 120)))
        assertEquals("and one that ends past it takes it", listOf(5, 6), cut(PxRect(210, 100, 241, 120)))
        assertEquals("a rectangle that starts at column 5's right edge or after takes only the next column", listOf(6), cut(PxRect(240, 100, 260, 120)))
        assertEquals("a rectangle that ends on the grid's top edge takes nothing", emptyList<Int>(), cut(PxRect(210, 60, 230, 100)))
        assertEquals("one pixel into the grid takes row 0", listOf(5), cut(PxRect(210, 60, 230, 101)))
    }

    @Test
    fun `a rectangle that only touches a cell's edge does not cover it - and an empty or inverted one covers nothing`() {
        val l = TouchGridLayout.compute(
            TouchGridTestKit.surface,
            TouchGridTestKit.environment(cutoutRectsPx = Reading.Value(listOf(PxRect(0, 100, 80, 140), PxRect(500, 500, 500, 600), PxRect(700, 900, 600, 800))))
        )
        // (0,100)-(80,140): columns 0..1 (80 / 40 = 2.0 -> ceil 2 - 1 = 1), row 0 only (40 / 40 = 1.0 -> ceil 1 - 1 = 0: the row below is only touched)
        assertEquals(listOf(0, 1), l.cutoutCells)
    }

    @Test
    fun `a cut-out that cannot be read leaves every cell required - the absence is the recorder's to declare, not a guess here`() {
        val l = TouchGridLayout.compute(TouchGridTestKit.surface, TouchGridTestKit.environment(cutoutRectsPx = Reading.Absent("read_failed:x")))
        assertEquals(emptyList<Int>(), l.cutoutCells)
        assertEquals("no cut-out read: the whole pattern is required", 200, l.totalCells)
    }

    // ───────────────────────── the cells a segment crosses (design §2.2) ─────────────────────────

    private val grid = TouchGridLayout.compute(TouchGridTestKit.surface, TouchGridTestKit.environment())   // 20 x 35, 40 x 40 px, the grid 100 px below the window's top

    @Test
    fun `a segment inside one cell crosses that one cell`() {
        assertEquals(listOf(0), grid.cellsCrossed(10.0, 10.0, 30.0, 30.0))
        assertEquals(listOf(20 * 5 + 7), grid.cellsCrossed(280.0, 200.0, 280.0, 200.0))
    }

    @Test
    fun `a horizontal and a vertical segment cross every cell on their line, in order, both directions`() {
        assertEquals((0..19).toList(), grid.cellsCrossed(1.0, 30.0, 799.0, 30.0))
        assertEquals((19 downTo 0).toList(), grid.cellsCrossed(799.0, 30.0, 1.0, 30.0))
        assertEquals((0 until 35).map { it * 20 }, grid.cellsCrossed(30.0, 1.0, 30.0, 1399.0))
    }

    @Test
    fun `a segment through an exact cell corner steps diagonally`() {
        // 800 px wide: 20 columns of exactly 40 px (and rows of exactly 40), so the corner (40, 40) is exact in binary
        assertEquals(40.0, grid.cellWidthPx, 0.0)
        assertEquals(40.0, grid.cellHeightPx, 0.0)
        assertEquals(listOf(0, 20 + 1), grid.cellsCrossed(30.0, 30.0, 50.0, 50.0))
    }

    @Test
    fun `a segment outside the surface is clamped to it`() {
        assertEquals(listOf(0), grid.cellsCrossed(-50.0, -50.0, -10.0, -5.0))
        assertEquals(listOf(20 * 35 - 1), grid.cellsCrossed(900.0, 1600.0, 2000.0, 3000.0))
    }

    @Test
    fun `over two thousand random segments the cells crossed are exactly the cells the segment passes through`() {
        val rnd = Random(20261018)
        repeat(2000) {
            val x0 = rnd.nextDouble(0.0, 800.0); val y0 = rnd.nextDouble(0.0, 1400.0)
            val x1 = rnd.nextDouble(0.0, 800.0); val y1 = rnd.nextDouble(0.0, 1400.0)
            val got = grid.cellsCrossed(x0, y0, x1, y1)
            assertEquals("no cell twice", got.size, got.toSet().size)
            val want = HashSet<Int>()
            for (r in 0 until 35) for (c in 0 until 20) {
                if (segmentOverlapsOpenRect(x0, y0, x1, y1, c * grid.cellWidthPx, r * grid.cellHeightPx, (c + 1) * grid.cellWidthPx, (r + 1) * grid.cellHeightPx)) want.add(r * 20 + c)
            }
            assertEquals("segment ($x0,$y0)-($x1,$y1)", want, got.toSet())
            assertEquals("starts in the cell it starts in", grid.cellIndexAt(x0, y0), got.first())
            assertEquals("ends in the cell it ends in", grid.cellIndexAt(x1, y1), got.last())
        }
    }

    /** Liang-Barsky: the part of the segment inside the OPEN rectangle has positive length. */
    private fun segmentOverlapsOpenRect(x0: Double, y0: Double, x1: Double, y1: Double, l: Double, t: Double, r: Double, b: Double): Boolean {
        var t0 = 0.0
        var t1 = 1.0
        val dx = x1 - x0
        val dy = y1 - y0
        fun clip(p: Double, q: Double): Boolean {
            if (p == 0.0) return q > 0
            val u = q / p
            if (p < 0) { if (u > t1) return false; if (u > t0) t0 = u } else { if (u < t0) return false; if (u < t1) t1 = u }
            return true
        }
        if (!clip(-dx, x0 - l) || !clip(dx, r - x0) || !clip(-dy, y0 - t) || !clip(dy, b - y0)) return false
        return t1 - t0 > 1e-12
    }

    @Test
    fun `cellIndexAt clamps to the grid and a cell's index is row x cols + col`() {
        assertEquals(0, grid.cellIndexAt(-5.0, -5.0))
        assertEquals(20 * 35 - 1, grid.cellIndexAt(5000.0, 5000.0))
        assertEquals(20 * 2 + 5, grid.cellIndexAt(5.5 * grid.cellWidthPx, 2.5 * grid.cellHeightPx))
    }
}
