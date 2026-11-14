package net.neuralcheck.domain.manual

import net.neuralcheck.domain.manual.TouchGridContract.DensityChecks
import net.neuralcheck.domain.manual.TouchGridContract.DensitySources
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

/** What the grid is drawn on: the touch surface, in pixels, with its origin in the window frame (design §6.1: `surface*Px`). */
data class TouchGridSurface(val widthPx: Int, val heightPx: Int, val originXPx: Int, val originYPx: Int)

/**
 * Everything the platform said about the screen when the grid was laid out — raw, as readings: a value or the reason there is none (rule 22).
 * Built by the Android side (`TouchGridEnvironmentReader`) and by the tests; this class has no Android in it.
 *
 * Frames, as the platform gives them (the server converts with `surfaceOrigin*Px`): [cutoutRectsPx] and the insets are in the WINDOW frame;
 * [exclusionRectsPx] in the VIEW frame.
 */
class TouchGridEnvironment(
    /** `DisplayMetrics.xdpi` / `ydpi` / `densityDpi` — always answered by the platform. */
    val xdpi: Float,
    val ydpi: Float,
    val densityDpi: Int,
    val displayWidthPx: Reading<Int>,
    val displayHeightPx: Reading<Int>,
    val modePhysicalWidthPx: Reading<Int>,
    val modePhysicalHeightPx: Reading<Int>,
    val gestureInsetsPx: Reading<PxRect>,
    val mandatoryGestureInsetsPx: Reading<PxRect>,
    val exclusionRectsPx: Reading<List<PxRect>>,
    val systemBarsHidden: Reading<Boolean>,
    val cutoutRectsPx: Reading<List<PxRect>>,
    val roundedCorners: RoundedCorners,
    /** `Build.VERSION.SDK_INT` — only for `resampledFlagState`. */
    val sdkInt: Int
)

/** The pixel density the cells are drawn with, how it was chosen, and which conditions failed when the fallback was used. */
data class DensityChoice(val pxPerMm: Double, val source: String, val failedChecks: List<String>)

/**
 * Which density to draw the 6 mm cells with — design §1.2, the exact procedure (every input is also sent raw, so the server can redo it):
 *
 *  1. two candidates for the screen's width in mm: `displayWidthPx / xdpi` (the naive one) and, when the display runs in a scaled mode (the mode's
 *     physical width differs from the display's), that times `modeWidthPx / displayWidthPx`;
 *  2. a candidate is believed when it is a possible phone width ([TouchGridContract.PHONE_WIDTH_MIN_MM]..MAX) and `|xdpi - ydpi|` is within
 *     [TouchGridContract.DPI_AXES_MAX_SPREAD] of the larger;
 *  3. exactly one believed candidate is used (`XDPI` / `XDPI_MODE_SCALED`); none, or both, is an ambiguity and `densityDpi` draws the grid
 *     (`DENSITY_DPI_FALLBACK`) with the failed conditions in `densityCheck`. With no scaled mode there is one candidate.
 */
object DensitySelector {

    fun choose(
        xdpi: Float,
        ydpi: Float,
        densityDpi: Int,
        displayWidthPx: Int?,
        modePhysicalWidthPx: Int?
    ): DensityChoice {
        val mm = TouchGridContract.MM_PER_INCH
        fun fallback(failed: List<String>) =
            DensityChoice(max(densityDpi, 1) / mm, DensitySources.DENSITY_DPI_FALLBACK, failed.distinct())

        val usable = xdpi.isFinite() && ydpi.isFinite() && xdpi > 0f && ydpi > 0f
        if (!usable) return fallback(listOf(DensityChecks.XDPI_UNUSABLE))
        if (displayWidthPx == null || displayWidthPx <= 0) return fallback(listOf(DensityChecks.DISPLAY_WIDTH_UNKNOWN))

        val axesOk = abs(xdpi - ydpi) <= TouchGridContract.DPI_AXES_MAX_SPREAD * max(xdpi, ydpi)
        fun widthOk(widthMm: Double) = widthMm >= TouchGridContract.PHONE_WIDTH_MIN_MM && widthMm <= TouchGridContract.PHONE_WIDTH_MAX_MM

        val naiveMm = displayWidthPx / xdpi.toDouble() * mm
        val scaled = modePhysicalWidthPx != null && modePhysicalWidthPx > 0 && modePhysicalWidthPx != displayWidthPx
        val scaledMm = if (scaled) naiveMm * modePhysicalWidthPx!! / displayWidthPx else null

        val naiveOk = axesOk && widthOk(naiveMm)
        val scaledOk = scaledMm != null && axesOk && widthOk(scaledMm)

        return when {
            naiveOk && !scaledOk -> DensityChoice(xdpi / mm, DensitySources.XDPI, emptyList())
            scaledOk && !naiveOk ->
                DensityChoice(xdpi / mm * displayWidthPx / modePhysicalWidthPx!!, DensitySources.XDPI_MODE_SCALED, emptyList())
            naiveOk && scaledOk -> fallback(listOf(DensityChecks.BOTH_CANDIDATES_PLAUSIBLE))
            else -> fallback(
                buildList {
                    if (!axesOk) add(DensityChecks.DPI_AXES_DIFFER)
                    if (!widthOk(naiveMm) && (scaledMm == null || !widthOk(scaledMm))) add(DensityChecks.WIDTH_OUT_OF_RANGE)
                }
            )
        }
    }
}

/**
 * The grid for one run (design §1.3, redrawn on the user's order of 2026-10-21 to the shape of the system's own touch test). The cells are SQUARE: `gridCols = round(width / cell)` for a
 * [TouchGridContract.CELL_TARGET_MM] cell at the chosen density, and a cell is `width / gridCols` on both axes; the rows are as many whole cells as fit under an empty strip at the top of
 * the window ([TouchGridContract.TOP_STRIP_CELLS] cells high at least, less than one cell more) and the strip is what is left, so the grid ends exactly at the bottom edge. Nothing is
 * fixed in the code: the columns, the rows and the strip come from the screen.
 *
 * The TOUCH SURFACE of the run is the grid's region of the window — [surface], the window less the strip — not the window: the server places every recorded point in a cell by
 * `floor(x / cellWidthPx)`, `floor(y / cellHeightPx)` in the surface's own frame and takes the surface's edges for the edges of the grid (`touchPath.js`), so the surface and the grid
 * must be the same rectangle. A point in the strip is OUTSIDE the surface (a negative `y`): recorded raw, in no cell. [windowSurface] is the window itself, the frame the screen's text and
 * buttons are drawn in. A cell's index is `row x gridCols + col` (contract ف-02).
 *
 * Which of its cells a run REQUIRES (contract section 7-ي, paragraph of 2026-10-21, relations 1-5): the cells of the «frame + X» pattern ([TouchGridPattern]) minus the cells a camera cut-out touches.
 * Three disjoint kinds of cell result:
 *  - `cutoutCells` — every cell whose area the platform's cut-out rectangles overlap (the display is not there, whatever the pattern says; an edge-only touch is not an overlap, and
 *    NO margin is added: a rectangle 1 px inside a cell takes the cell, 1 px short of it does not). A camera that lies wholly in the strip takes no cell;
 *  - `notRequiredCells` — the cells the pattern leaves out (the four triangles between the frame and the diagonals) that are not cut out;
 *  - the REQUIRED cells — the rest: `totalCells = gridCols x gridRows - |cutoutCells| - |notRequiredCells|`.
 */
class TouchGridLayout private constructor(
    /** The window the run is drawn in — the view's own rectangle, its origin in the window frame. */
    val windowSurface: TouchGridSurface,
    /** The grid's region of the window: what is sent as `surface*Px`. Its origin is the window's origin moved down by [gridTopPx]. */
    val surface: TouchGridSurface,
    val density: DensityChoice,
    val cols: Int,
    val rows: Int,
    /** Sorted cell indexes. */
    val cutoutCells: List<Int>,
    /** Sorted cell indexes; none of them is in [cutoutCells]. */
    val notRequiredCells: List<Int>,
    /** The platform's cut-out rectangles in the VIEW's frame (the window's, less the view's origin), whole — the strip's text keeps clear of them. */
    val cutoutRectsInViewPx: List<PxRect>
) {
    val cellWidthPx: Double = surface.widthPx.toDouble() / cols
    val cellHeightPx: Double = surface.heightPx.toDouble() / rows

    /** The height of the empty strip above the grid, px: where the grid starts in the view's frame. */
    val gridTopPx: Int get() = surface.originYPx - windowSurface.originYPx

    private val cutout: BooleanArray = BooleanArray(cols * rows).also { a -> cutoutCells.forEach { a[it] = true } }
    private val notRequired: BooleanArray = BooleanArray(cols * rows).also { a -> notRequiredCells.forEach { a[it] = true } }
    private val inPattern: BooleanArray = BooleanArray(cols * rows).also { a -> TouchGridPattern.cells(cols, rows).forEach { a[it] = true } }

    /** `measurements.gridPattern`: the shape this layout asks for — always the frame and the diagonals. */
    val gridPattern: String = TouchGridContract.GridPatterns.FRAME_AND_DIAGONALS

    /** The cells that must be lit for the run to complete: every cell that is neither cut out nor left out by the pattern. */
    val totalCells: Int get() = cols * rows - cutoutCells.size - notRequiredCells.size

    fun isCutout(index: Int): Boolean = cutout[index]

    /** Left out by the pattern and not cut out (the cell is free: nothing is asked of it). */
    fun isNotRequired(index: Int): Boolean = notRequired[index]

    /** A cell a run asks to be lit: not cut out and not left out by the pattern. */
    fun isRequired(index: Int): Boolean = !cutout[index] && !notRequired[index]

    /** A cell of the frame or of a diagonal, cut out or not (what the screen draws: a required one lit or dark, a cut-out one outlined). */
    fun isInPattern(index: Int): Boolean = inPattern[index]

    /** Whether the point (surface frame) is on the surface — the server's own test (`x >= 0 && y >= 0 && x < surfaceWidthPx && y < surfaceHeightPx`): a point off it is in no cell. */
    fun inside(x: Double, y: Double): Boolean = x >= 0.0 && y >= 0.0 && x < surface.widthPx && y < surface.heightPx

    /** The cell holding the point; a point off the surface is clamped to the nearest cell — callers ask [inside] first. */
    fun cellIndexAt(x: Double, y: Double): Int = row(y) * cols + col(x)

    private fun col(x: Double): Int = floor(x / cellWidthPx).toInt().coerceIn(0, cols - 1)
    private fun row(y: Double): Int = floor(y / cellHeightPx).toInt().coerceIn(0, rows - 1)

    /**
     * Every cell the straight segment from (x0, y0) to (x1, y1) passes through, in order, both end cells included (the grid traversal of
     * Amanatides and Woo, design §2.2). Coordinates outside the surface are clamped to it. A segment through an exact cell corner steps
     * diagonally.
     */
    fun cellsCrossed(x0: Double, y0: Double, x1: Double, y1: Double): List<Int> {
        val ax = x0.coerceIn(0.0, surface.widthPx.toDouble()); val ay = y0.coerceIn(0.0, surface.heightPx.toDouble())
        val bx = x1.coerceIn(0.0, surface.widthPx.toDouble()); val by = y1.coerceIn(0.0, surface.heightPx.toDouble())
        var c = col(ax); var r = row(ay)
        val endC = col(bx); val endR = row(by)
        val out = ArrayList<Int>(4)
        out.add(r * cols + c)
        if (c == endC && r == endR) return out
        val dx = bx - ax; val dy = by - ay
        val stepC = if (dx > 0) 1 else if (dx < 0) -1 else 0
        val stepR = if (dy > 0) 1 else if (dy < 0) -1 else 0
        val tDeltaX = if (dx != 0.0) cellWidthPx / abs(dx) else Double.POSITIVE_INFINITY
        val tDeltaY = if (dy != 0.0) cellHeightPx / abs(dy) else Double.POSITIVE_INFINITY
        var tMaxX = when {
            dx > 0 -> ((c + 1) * cellWidthPx - ax) / dx
            dx < 0 -> (ax - c * cellWidthPx) / -dx
            else -> Double.POSITIVE_INFINITY
        }
        var tMaxY = when {
            dy > 0 -> ((r + 1) * cellHeightPx - ay) / dy
            dy < 0 -> (ay - r * cellHeightPx) / -dy
            else -> Double.POSITIVE_INFINITY
        }
        var guard = cols + rows + 2
        while ((c != endC || r != endR) && guard-- > 0) {
            if (tMaxX < tMaxY) { c += stepC; tMaxX += tDeltaX }
            else if (tMaxY < tMaxX) { r += stepR; tMaxY += tDeltaY }
            else { c += stepC; r += stepR; tMaxX += tDeltaX; tMaxY += tDeltaY }
            c = c.coerceIn(0, cols - 1); r = r.coerceIn(0, rows - 1)
            out.add(r * cols + c)
        }
        return out
    }

    companion object {
        /**
         * The layout of the run in [window] under [environment]: the density, the square cells, the strip, the grid's surface, the cut-out cells (window-frame rects, shifted to the grid's
         * surface; the system's rectangles taken as they are — no margin of the app's own, user decision 2026-10-21 «بأصغر هامش ممكن») and the cells the pattern leaves out.
         */
        fun compute(window: TouchGridSurface, environment: TouchGridEnvironment): TouchGridLayout {
            require(window.widthPx > 0 && window.heightPx > 0) { "the surface has no size: ${window.widthPx} x ${window.heightPx}" }
            val density = DensitySelector.choose(
                environment.xdpi,
                environment.ydpi,
                environment.densityDpi,
                (environment.displayWidthPx as? Reading.Value)?.value,
                (environment.modePhysicalWidthPx as? Reading.Value)?.value
            )
            val cellPx = TouchGridContract.CELL_TARGET_MM * density.pxPerMm
            val cols = (window.widthPx / cellPx).roundToInt().coerceIn(1, TouchGridContract.MAX_CELLS_PER_AXIS)
            val side = window.widthPx.toDouble() / cols
            // as many whole cells as fit under the strip's minimum (the epsilon: an exact fit must not be lost to a rounding error of the division)
            val rows = floor((window.heightPx - TouchGridContract.TOP_STRIP_CELLS * side) / side + 1e-9).toInt().coerceIn(1, TouchGridContract.MAX_CELLS_PER_AXIS)
            val gridHeight = minOf((rows * side).roundToInt(), window.heightPx)
            val surface = TouchGridSurface(window.widthPx, gridHeight, window.originXPx, window.originYPx + (window.heightPx - gridHeight))
            val cw = surface.widthPx.toDouble() / cols
            val ch = surface.heightPx.toDouble() / rows
            val cutouts = (environment.cutoutRectsPx as? Reading.Value)?.value.orEmpty()
            val cells = sortedSetOf<Int>()
            for (rect in cutouts) {
                val l = rect.left - surface.originXPx.toDouble(); val t = rect.top - surface.originYPx.toDouble()
                val rr = rect.right - surface.originXPx.toDouble(); val b = rect.bottom - surface.originYPx.toDouble()
                if (rr <= l || b <= t) continue
                // the cells whose area overlaps the rectangle's area (an edge-only touch is not an overlap); a rectangle above the grid (in the strip) has no cell
                val c0 = floor(l / cw).toInt().coerceAtLeast(0); val c1 = (Math.ceil(rr / cw).toInt() - 1).coerceAtMost(cols - 1)
                val r0 = floor(t / ch).toInt().coerceAtLeast(0); val r1 = (Math.ceil(b / ch).toInt() - 1).coerceAtMost(rows - 1)
                for (r in r0..r1) for (c in c0..c1) cells.add(r * cols + c)
            }
            val pattern = TouchGridPattern.cells(cols, rows)
            val notRequired = (0 until cols * rows).filter { it !in pattern && it !in cells }
            val inView = cutouts.map { PxRect(it.left - window.originXPx, it.top - window.originYPx, it.right - window.originXPx, it.bottom - window.originYPx) }
            return TouchGridLayout(window, surface, density, cols, rows, cells.toList(), notRequired, inView)
        }
    }
}
