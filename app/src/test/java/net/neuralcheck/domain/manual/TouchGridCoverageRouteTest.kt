package net.neuralcheck.domain.manual

import net.neuralcheck.domain.manual.TouchGridTestKit.Rig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How few finger movements light every cell M-02 requires (the user's order of 2026-10-21: «أقلّ عدد حركات لتغطية كل خلية (دورة الإطار + القطران)»), proved on the REAL recorder: a finger is drawn along
 * the route — dense samples, 1/8 of a cell apart, 8 ms apart, so no step is a «jump» to the display detector — and the run must END BY ITSELF (`isComplete`, every required cell lit). The routes come from
 * `tools-m02-shape-reference.js` (workspace; it finds them with point sampling, a method that shares nothing with the recorder's grid traversal, rule 38): there the same routes light every required cell on
 * the six screens below.
 *
 * What a «movement» is: one straight segment of the finger's path. The pattern has six lines — the four sides of the frame and the two diagonals — and no straight movement lights cells of two of them:
 *  - lifting the finger between them: SIX strokes of one movement each (the four sides along the middle of the frame's cells and the two diagonals exactly from inner corner to inner corner);
 *  - never lifting it: the six lines meet at four corners where three of them end (an odd number each), so one side has to be gone over twice: at least SEVEN movements (an Euler walk with one repeated side),
 *    which the reference finds only with a finger on the cells' own boundary lines, exactly — a route that leans on ties of the traversal and is NOT tried here; and NINE movements that lean on no boundary
 *    (`nine` below: two short hops between an inner corner and the frame's corner cell are the two extra movements).
 * Not proved here (a device item): that a finger follows any of it, how long it takes, and that a 4 mm cell lights under a real fingertip.
 */
class TouchGridCoverageRouteTest {

    private class Screen(val name: String, val w: Int, val h: Int, val dpi: Float)

    private val screens = listOf(
        Screen("1440x3040@502dpi", 1440, 3040, 502f),
        Screen("1080x2400@400dpi", 1080, 2400, 400f),
        Screen("1440x3200@515dpi", 1440, 3200, 515f),
        Screen("1080x1920@440dpi", 1080, 1920, 440f),
        Screen("1200x1600@400dpi", 1200, 1600, 400f),
        Screen("800x1500@254dpi", 800, 1500, 254f)
    )

    private fun rig(s: Screen): Rig {
        val env = TouchGridTestKit.environment(
            xdpi = s.dpi, ydpi = s.dpi, densityDpi = 420,
            displayWidthPx = Reading.Value(s.w), displayHeightPx = Reading.Value(s.h),
            modePhysicalWidthPx = Reading.Value(s.w), modePhysicalHeightPx = Reading.Value(s.h)
        )
        return Rig(env = env, surface = TouchGridSurface(s.w, s.h, 0, 0))
    }

    /** The named points of a grid of [cols] x [rows] cells, in CELL units (x along the columns, y along the rows): the four centres of the frame's corner cells (`f`) and the four inner corners of the frame (`i`). */
    private fun points(cols: Int, rows: Int) = mapOf(
        "TLf" to (0.5 to 0.5), "TRf" to (cols - 0.5 to 0.5), "BRf" to (cols - 0.5 to rows - 0.5), "BLf" to (0.5 to rows - 0.5),
        "TLi" to (1.0 to 1.0), "TRi" to (cols - 1.0 to 1.0), "BRi" to (cols - 1.0 to rows - 1.0), "BLi" to (1.0 to rows - 1.0)
    )

    /** Draws [route] (point names) as ONE stroke of the finger — down at the first, up at the last — samples 1/8 of a cell apart, 8 ms apart. */
    private fun Rig.drawStroke(id: Int, route: List<String>) {
        val p = points(layout.cols, layout.rows)
        val cw = layout.cellWidthPx
        val ch = layout.cellHeightPx
        val xy = route.map { p.getValue(it).let { (x, y) -> x * cw to y * ch } }
        val step = cw / 8
        var last = xy.first()
        down(id, last.first.toFloat(), last.second.toFloat(), uptime)
        for (k in 1 until xy.size) {
            val (x1, y1) = xy[k]
            val n = Math.ceil(Math.hypot(x1 - last.first, y1 - last.second) / step).toInt().coerceAtLeast(1)
            for (j in 1..n) {
                tick(8L)
                move(id, Triple(uptime, (last.first + (x1 - last.first) * j / n).toFloat(), (last.second + (y1 - last.second) * j / n).toFloat()))
            }
            last = x1 to y1
        }
        tick(8L)
        up(id, last.first.toFloat(), last.second.toFloat(), uptime)
        tick(120L)
    }

    private val sixStrokes = listOf(
        listOf("TLf", "TRf"), listOf("TRf", "BRf"), listOf("BRf", "BLf"), listOf("BLf", "TLf"),     // the four sides, along the middle of the frame's cells
        listOf("TLi", "BRi"), listOf("TRi", "BLi")                                                     // the two diagonals, exactly from the inner corner to the opposite one
    )

    private val nine = listOf("TLi", "BRi", "BRf", "TRf", "TLf", "BLf", "BRf", "TRf", "TRi", "BLi")

    @Test
    fun `six strokes of one straight movement each - the four sides and the two diagonals - light every required cell and the run ends by itself, on six screens`() {
        for (s in screens) {
            val r = rig(s)
            sixStrokes.forEachIndexed { i, route -> r.drawStroke(i, route) }
            val tag = "${s.name}: ${r.layout.cols} x ${r.layout.rows}, ${r.layout.totalCells} required"
            assertTrue("$tag: complete, ${r.rec.touchedCount} lit", r.rec.isComplete)
            assertEquals(tag, r.layout.totalCells, r.rec.touchedCount)
            val raw = r.rec.measurements(TouchGridRecorder.EndReasons.AUTO_COMPLETE)
            assertEquals("$tag: nothing is left untouched", emptyList<Any?>(), raw[TouchGridRecorder.Keys.UNTOUCHED_CELLS])
            assertEquals("$tag: six strokes", 6, (raw[TouchGridRecorder.Keys.STROKES] as List<*>).size)
            println("FRAME-X-ROUTE six strokes $tag: complete after ${(raw[TouchGridRecorder.Keys.STROKES] as List<*>).sumOf { ((it as Map<*, *>)[TouchGridContract.StrokeKeys.X] as List<*>).size }} samples")
        }
    }

    @Test
    fun `one stroke of nine straight movements that leans on no cell boundary lights every required cell and the run ends by itself, on six screens`() {
        for (s in screens) {
            val r = rig(s)
            r.drawStroke(0, nine)
            val tag = "${s.name}: ${r.layout.cols} x ${r.layout.rows}, ${r.layout.totalCells} required"
            assertTrue("$tag: complete, ${r.rec.touchedCount} lit", r.rec.isComplete)
            assertEquals("$tag: ONE stroke, the finger never lifted", 1, (r.rec.measurements(TouchGridRecorder.EndReasons.AUTO_COMPLETE)[TouchGridRecorder.Keys.STROKES] as List<*>).size)
            assertEquals("$tag: the run's display log saw no jump on a steady finger", 0, r.rec.displayedSuspects.count { it.kind != TouchGridContract.SuspectKinds.EDGE_EXIT })
        }
    }

    @Test
    fun `the check is not vacuous - without the second diagonal, or with a side left out, the run does not complete and says which cells are untouched`() {
        for (s in screens) {
            val noSecondDiagonal = rig(s)
            sixStrokes.dropLast(1).forEachIndexed { i, route -> noSecondDiagonal.drawStroke(i, route) }
            assertFalse("${s.name}: no second diagonal", noSecondDiagonal.rec.isComplete)
            val missing = noSecondDiagonal.layout.totalCells - noSecondDiagonal.rec.touchedCount
            assertTrue("${s.name}: the second diagonal's own cells (${missing}) are untouched", missing > 0)

            val noBottom = rig(s)
            sixStrokes.filterIndexed { i, _ -> i != 2 }.forEachIndexed { i, route -> noBottom.drawStroke(i, route) }
            assertFalse("${s.name}: no bottom side", noBottom.rec.isComplete)
        }
    }
}
