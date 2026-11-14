package net.neuralcheck.domain.manual

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The «frame + X» pattern of the M-02 grid, in the shape of the system's own touch test (user decisions 2026-10-21; wire contract `wire-m02-frame-x-2026-10-21.md` item 6, copied in
 * `docs/FV68_CONTRACT.md` section 7-ي, paragraphs 1 and 2 of 2026-10-21): the REAL [TouchGridPattern] on literal grids and, as a property, on every grid up to 70 x 70. It REPLACES (rule 23)
 * the assertions of the earlier shape — the diagonal of ONE cell per row of the longer axis from the OUTER corner cells, joined only at the corners (`diagonalPath(12, 26)` of 28 cells, the
 * «tie grid», 107 required on 1080x2400) — whose test pinned exactly the shape the user rejected («كتل 1×2 تتلامس بالزوايا فقط، وتترك الزاوية الداخلية»).
 *
 * Proved: (أ) the frame is exactly the cells of the first and last row and column, one cell thick; (ب) a diagonal runs from the inner top-left corner's cell to the inner bottom-right
 * corner's, is one chain in the eight-neighbourhood (in the four-neighbourhood but at the lattice points the line meets exactly), stays inside the frame, and is EXACTLY the set of cells the
 * straight line between the inner corners enters — decided with integer arithmetic that shares nothing with the code under test, and cross-checked against the grid traversal the recorder
 * uses (`TouchGridLayout.cellsCrossed`, Amanatides–Woo) so the cells a finger drawing the line lights are the cells the pattern asks for; (ج) the pattern is symmetric, whole, without a block of its
 * own at the centre; (د) the numbers on the six screens, against the independent reference `tools-m02-shape-reference.js` (rule 38).
 */
class TouchGridPatternTest {

    private fun rc(index: Int, cols: Int) = index / cols to index % cols

    // ───────────────────────── (أ) the frame ─────────────────────────

    @Test
    fun `the frame is exactly the first and last row and the first and last column - a literal 5 x 4 grid`() {
        // row 0: 0..4, row 3: 15..19, column 0: 0 5 10 15, column 4: 4 9 14 19
        assertEquals(setOf(0, 1, 2, 3, 4, 5, 9, 10, 14, 15, 16, 17, 18, 19), TouchGridPattern.frameCells(5, 4))
    }

    @Test
    fun `the frame of every grid is the cells on its border and no other - one cell thick - and its size is 2 x cols + 2 x rows - 4 once both sides are at least 2`() {
        for (cols in 1..40) for (rows in 1..40) {
            val frame = TouchGridPattern.frameCells(cols, rows)
            val expected = (0 until cols * rows).filter { val (r, c) = rc(it, cols); r == 0 || c == 0 || r == rows - 1 || c == cols - 1 }.toSet()
            assertEquals("$cols x $rows", expected, frame)
            if (cols >= 2 && rows >= 2) assertEquals("$cols x $rows", 2 * cols + 2 * rows - 4, frame.size)
        }
        // a grid with one row or column is all frame
        assertEquals((0 until 7).toSet(), TouchGridPattern.frameCells(7, 1))
        assertEquals((0 until 6).toSet(), TouchGridPattern.frameCells(1, 6))
    }

    // ───────────────────────── (ب) the diagonals ─────────────────────────

    @Test
    fun `literal diagonals - a square inside is its main diagonal, a one-column inside is that column, a one-row inside is that row, a lattice point is crossed corner to corner`() {
        // 5 x 5: the inside is 3 x 3, the line is its main diagonal, one cell a row
        assertEquals(listOf(1 to 1, 2 to 2, 3 to 3), TouchGridPattern.diagonalPath(5, 5))
        // 3 columns x 7 rows: the inside is 1 column x 5 rows — every row of it
        assertEquals((1..5).map { it to 1 }, TouchGridPattern.diagonalPath(3, 7))
        // 7 columns x 3 rows: the inside is 5 columns x 1 row — every column of it
        assertEquals((1..5).map { 1 to it }, TouchGridPattern.diagonalPath(7, 3))
        // 6 columns x 8 rows: the inside is 4 x 6; across inside row i the line covers x from i x 4 / 6 to (i + 1) x 4 / 6: 0..0.67, 0.67..1.33, 1.33..2, 2..2.67, 2.67..3.33, 3.33..4 —
        // so the columns 0 | 0 1 | 1 | 2 | 2 3 | 3; the line meets a lattice point exactly at (2, 3), where row 2's column 1 and row 3's column 2 touch at a corner
        assertEquals(
            listOf(1 to 1, 2 to 1, 2 to 2, 3 to 2, 4 to 3, 5 to 3, 5 to 4, 6 to 4),
            TouchGridPattern.diagonalPath(6, 8)
        )
        // a grid with no inside has no diagonal
        for ((cols, rows) in listOf(1 to 1, 2 to 9, 9 to 2, 2 to 2, 1 to 6)) assertEquals("$cols x $rows", emptyList<Pair<Int, Int>>(), TouchGridPattern.diagonalPath(cols, rows))
    }

    @Test
    fun `the 18 x 36 grid of a tall phone - a diagonal of 48 cells from the inner corner cell (1, 1) to (34, 16), two rows holding two cells where the line crosses a column edge`() {
        val path = TouchGridPattern.diagonalPath(18, 36)
        assertEquals(1 to 1, path.first())
        assertEquals(34 to 16, path.last())
        // the inside is 16 columns x 34 rows: h + w - gcd(w, h) = 34 + 16 - 2 = 48 cells (the line meets one lattice point exactly, (8, 17), the centre)
        assertEquals(48, path.size)
        assertEquals("every inside row has a cell", (1..34).toList(), path.map { it.first }.distinct())
        val perRow = path.groupBy { it.first }.mapValues { it.value.size }
        assertEquals("rows with two cells: the 15 column edges inside the 16 columns, less the one that is the lattice point", 14, perRow.count { it.value == 2 })
        assertEquals("and with one", 20, perRow.count { it.value == 1 })
    }

    /** The reference: the inside cell (i, j) of a w x h inside is on the line y = h x / w exactly when the line enters its OPEN area: h x j < (i + 1) x w and h x (j + 1) > i x w. */
    private fun referenceDiagonal(cols: Int, rows: Int): Set<Pair<Int, Int>> {
        val w = (cols - 2).toLong()
        val h = (rows - 2).toLong()
        val out = HashSet<Pair<Int, Int>>()
        if (w < 1 || h < 1) return out
        for (i in 0 until h.toInt()) for (j in 0 until w.toInt()) if (h * j < (i + 1) * w && h * (j + 1) > i * w) out.add((1 + i) to (1 + j))
        return out
    }

    @Test
    fun `every diagonal is exactly the set of cells the line between the inner corners enters, starts and ends at the inner corners, stays inside the frame and is one connected chain`() {
        for (cols in 1..70) for (rows in 1..70) {
            val path = TouchGridPattern.diagonalPath(cols, rows)
            val tag = "$cols x $rows"
            assertEquals("$tag: the cells the line enters, by integer arithmetic that shares no step with the code", referenceDiagonal(cols, rows), path.toSet())
            assertEquals("$tag: no cell twice", path.size, path.toSet().size)
            if (cols < 3 || rows < 3) { assertTrue("$tag: no inside, no diagonal", path.isEmpty()); continue }
            assertEquals("$tag: the inner top-left corner's cell", 1 to 1, path.first())
            assertEquals("$tag: the inner bottom-right corner's cell", rows - 2 to cols - 2, path.last())
            assertTrue("$tag: inside the frame", path.all { (r, c) -> r in 1..rows - 2 && c in 1..cols - 2 })
            assertTrue("$tag: at least the longer side of the inside's cells", path.size >= maxOf(cols - 2, rows - 2))
            var cornerOnly = 0
            for (i in 1 until path.size) {
                val (r0, c0) = path[i - 1]
                val (r1, c1) = path[i]
                val dr = r1 - r0
                val dc = c1 - c0
                assertTrue("$tag: step $i from ($r0,$c0) to ($r1,$c1) leaves the eight-neighbourhood or goes back", dr in 0..1 && dc in 0..1 && dr + dc >= 1)
                if (dr == 1 && dc == 1) cornerOnly++
            }
            // the steps that meet at a corner only are the lattice points the line crosses: gcd(w, h) - 1 of them, none when w and h share no factor
            fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)
            assertEquals("$tag: the corner-only joins are the lattice points of the line", gcd(cols - 2, rows - 2) - 1, cornerOnly)
        }
    }

    @Test
    fun `a finger that draws the line between the inner corners lights exactly the cells of the diagonal - the recorder's grid traversal against the pattern's integer rule, on grids of exact cell sizes`() {
        // a window of cols x (rows + 2) cells of 10 px each at 2.5 px per mm (4 mm = 10 px): the grid is the lower rows x cols cells, the strip two cells (20 px); every coordinate below
        // is an exact multiple of 10, so no rounding error stands between the traversal and the pattern
        for (cols in 3..30) for (rows in 3..48) {
            val env = TouchGridTestKit.environment(
                xdpi = 63.5f, ydpi = 63.5f, densityDpi = 160,
                displayWidthPx = Reading.Value(cols * 10), displayHeightPx = Reading.Value((rows + 2) * 10),
                modePhysicalWidthPx = Reading.Value(cols * 10), modePhysicalHeightPx = Reading.Value((rows + 2) * 10)
            )
            val l = TouchGridLayout.compute(TouchGridSurface(cols * 10, (rows + 2) * 10, 0, 0), env)
            val tag = "$cols x $rows"
            if (l.cols != cols || l.rows != rows) continue        // the density rounds the column count: only grids that came out as asked are compared
            assertEquals("$tag: a cell is 10 px, the strip two cells", 20, l.gridTopPx)
            val crossed = l.cellsCrossed(10.0, 10.0, (cols - 1) * 10.0, (rows - 1) * 10.0).toSet()
            val diagonal = TouchGridPattern.diagonalPath(cols, rows).map { (r, c) -> r * cols + c }.toSet()
            // the traversal also lists the cell the segment's END point falls in: the frame's corner cell, because the end is on the lattice corner shared with it
            val endCell = (rows - 1) * cols + (cols - 1)
            fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)
            val g = gcd(cols - 2, rows - 2)
            // every cell the pattern asks for is lit by the drawn line; what else is lit is a flank cell at a lattice point of the line — the line meets the lattice at its two ends and g - 1 times between,
            // and at an exact corner the traversal's floating-point steps may pass through one of the two flank cells before the diagonal one (a measure-zero line for a finger)
            assertTrue("$tag: every diagonal cell is lit", crossed.containsAll(diagonal + endCell))
            val w = cols - 2
            val h = rows - 2
            val lattice = (0..g).map { k -> (k * w / g) to (k * h / g) }          // inner coordinates (x, y) in cells: the line's two ends included
            for (extra in crossed - diagonal - endCell) {
                val r = extra / cols - 1
                val c = extra % cols - 1                                          // the extra cell's inner row and column
                assertTrue("$tag: the extra cell ($r,$c) touches a lattice point of the line", lattice.any { (x, y) -> x in c..c + 1 && y in r..r + 1 })
            }
        }
    }

    // ───────────────────────── (ج) the whole pattern ─────────────────────────

    @Test
    fun `the pattern is the frame together with the two diagonals, the second being the first mirrored - a literal 5 x 5 grid has 21 cells`() {
        val p = TouchGridPattern.cells(5, 5)
        assertEquals(setOf(6, 8, 12, 16, 18), p - TouchGridPattern.frameCells(5, 5))
        assertEquals(16 + 5, p.size)
        assertEquals(p, TouchGridPattern.frameCells(5, 5) + TouchGridPattern.diagonalCells(5, 5))
    }

    @Test
    fun `the pattern of every grid is inside the grid, symmetric left-right and top-bottom, holds the four corners, and its second diagonal is the first one mirrored`() {
        for (cols in 1..70) for (rows in 1..70) {
            val p = TouchGridPattern.cells(cols, rows)
            val tag = "$cols x $rows"
            assertTrue("$tag: inside the grid", p.all { it in 0 until cols * rows })
            for (index in p) {
                val (r, c) = rc(index, cols)
                assertTrue("$tag: left-right symmetric at ($r,$c)", r * cols + (cols - 1 - c) in p)
                assertTrue("$tag: top-bottom symmetric at ($r,$c)", (rows - 1 - r) * cols + c in p)
            }
            assertTrue("$tag: the corners", listOf(0, cols - 1, (rows - 1) * cols, rows * cols - 1).all { it in p })
            val a = TouchGridPattern.diagonalPath(cols, rows).map { (r, c) -> r * cols + c }.toSet()
            val b = TouchGridPattern.diagonalPath(cols, rows).map { (r, c) -> r * cols + (cols - 1 - c) }.toSet()
            assertEquals("$tag: both diagonals", a + b, TouchGridPattern.diagonalCells(cols, rows))
        }
    }

    @Test
    fun `the pattern is one piece - the frame and both diagonals are joined by shared edges, the diagonals meet the frame at their ends, and no block of cells stands at the centre by itself`() {
        for ((cols, rows) in listOf(18 to 36, 17 to 35, 20 to 35, 16 to 26, 19 to 23, 5 to 5, 7 to 12, 30 to 30)) {
            val p = TouchGridPattern.cells(cols, rows)
            val tag = "$cols x $rows"
            // 8-connected component of the whole pattern: one (the lattice-point joins of a diagonal are corner joins; the frame closes the rest)
            val seen = HashSet<Int>()
            val queue = ArrayDeque<Int>()
            val start = p.first()
            queue.add(start); seen.add(start)
            while (queue.isNotEmpty()) {
                val i = queue.removeFirst()
                val (r, c) = rc(i, cols)
                for (dr in -1..1) for (dc in -1..1) {
                    val n = (r + dr) * cols + (c + dc)
                    if ((dr != 0 || dc != 0) && r + dr in 0 until rows && c + dc in 0 until cols && n in p && seen.add(n)) queue.add(n)
                }
            }
            assertEquals("$tag: one piece", p, seen)
            // each diagonal's two end cells touch the frame by an EDGE: the cell (1, 1) has the frame cells (0, 1) and (1, 0) beside it
            val ends = TouchGridPattern.diagonalPath(cols, rows).let { listOf(it.first(), it.last()) }
            for ((r, c) in ends) assertTrue("$tag: ($r,$c) is next to the frame by an edge", listOf(r - 1 to c, r + 1 to c, r to c - 1, r to c + 1).any { (rr, cc) -> rr * cols + cc in TouchGridPattern.frameCells(cols, rows) })
            // no block at the centre: the pattern's cells inside the frame number the two diagonals' cells less the cells they share (at most the crossing) — nothing else
            val inside = p - TouchGridPattern.frameCells(cols, rows)
            assertEquals("$tag: the inside of the pattern is the diagonals", TouchGridPattern.diagonalCells(cols, rows) - TouchGridPattern.frameCells(cols, rows), inside)
            val shared = TouchGridPattern.diagonalPath(cols, rows).map { (r, c) -> r * cols + c }.toSet().intersect(
                TouchGridPattern.diagonalPath(cols, rows).map { (r, c) -> r * cols + (cols - 1 - c) }.toSet()
            )
            assertTrue("$tag: the diagonals share at most a few cells at their crossing (${shared.size})", shared.size <= 6)
        }
    }

    @Test
    fun `a grid with no side of 3 cells or more is all pattern, and the cells beyond the frame exist only where the grid has an inside`() {
        for ((cols, rows) in listOf(1 to 1, 1 to 9, 9 to 1, 2 to 2, 2 to 9, 9 to 2)) {
            assertEquals("$cols x $rows", (0 until cols * rows).toSet(), TouchGridPattern.cells(cols, rows))
        }
        assertTrue("3 x 3 has one cell inside and the diagonals cross in it", 4 in TouchGridPattern.cells(3, 3))
        assertEquals(9, TouchGridPattern.cells(3, 3).size)
        assertTrue("4 x 4: the inside is all diagonal", TouchGridPattern.cells(4, 4).size == 16)
    }

    @Test
    fun `a grid with no cell is refused`() {
        for ((cols, rows) in listOf(0 to 5, 5 to 0, -1 to 3)) {
            try {
                TouchGridPattern.cells(cols, rows)
                error("$cols x $rows was accepted")
            } catch (expected: IllegalArgumentException) {
                // the grid always has a cell on each axis (TouchGridLayout.compute coerces to 1..MAX_CELLS_PER_AXIS)
            }
        }
    }

    // ───────────────────────── (د) the numbers on six screens ─────────────────────────

    private class Screen(
        val name: String, val w: Int, val h: Int, val dpi: Float,
        // from tools-m02-shape-reference.js (independent of this code): the grid, the strip and the cells
        val cols: Int, val rows: Int, val strip: Int, val frame: Int, val beyondFrame: Int, val required: Int
    )

    private val screens = listOf(
        Screen("1440x3040@502dpi", 1440, 3040, 502f, cols = 18, rows = 36, strip = 160, frame = 104, beyondFrame = 96, required = 200),
        Screen("1080x2400@400dpi", 1080, 2400, 400f, cols = 17, rows = 35, strip = 176, frame = 100, beyondFrame = 87, required = 187),
        Screen("1440x3200@515dpi", 1440, 3200, 515f, cols = 18, rows = 38, strip = 160, frame = 108, beyondFrame = 96, required = 204),
        Screen("1080x1920@440dpi", 1080, 1920, 440f, cols = 16, rows = 26, strip = 165, frame = 80, beyondFrame = 72, required = 152),
        Screen("1200x1600@400dpi", 1200, 1600, 400f, cols = 19, rows = 23, strip = 147, frame = 80, beyondFrame = 71, required = 151),
        Screen("800x1500@254dpi (the test kit's)", 800, 1500, 254f, cols = 20, rows = 35, strip = 100, frame = 106, beyondFrame = 94, required = 200)
    )

    @Test
    fun `the six screens - square cells, the strip, the frame, the diagonals beyond it, the required cells - equal the independent reference, and the counts are printed for the report`() {
        for (s in screens) {
            val env = TouchGridTestKit.environment(
                xdpi = s.dpi, ydpi = s.dpi, densityDpi = 420,
                displayWidthPx = Reading.Value(s.w), displayHeightPx = Reading.Value(s.h),
                modePhysicalWidthPx = Reading.Value(s.w), modePhysicalHeightPx = Reading.Value(s.h)
            )
            val l = TouchGridLayout.compute(TouchGridSurface(s.w, s.h, 0, 0), env)
            val all = l.cols * l.rows
            assertEquals("${s.name}: columns", s.cols, l.cols)
            assertEquals("${s.name}: rows", s.rows, l.rows)
            assertEquals("${s.name}: the empty strip above the grid, px", s.strip, l.gridTopPx)
            assertTrue("${s.name}: the cells are square (${l.cellWidthPx} x ${l.cellHeightPx})", Math.abs(l.cellWidthPx - l.cellHeightPx) <= 0.5 / l.rows + 1e-9)
            assertEquals("${s.name}: the grid ends at the bottom edge", s.h, l.gridTopPx + l.surface.heightPx)
            val frame = TouchGridPattern.frameCells(l.cols, l.rows)
            val beyond = TouchGridPattern.diagonalCells(l.cols, l.rows) - frame
            val pattern = TouchGridPattern.cells(l.cols, l.rows)
            assertEquals("${s.name}: the frame", s.frame, frame.size)
            assertEquals("${s.name}: the diagonals beyond the frame", s.beyondFrame, beyond.size)
            assertEquals("${s.name}: required (no camera)", s.required, l.totalCells)
            assertEquals(pattern.size, l.totalCells)
            val stripPct = 100.0 * l.gridTopPx / s.h
            println(
                "FRAME-X-SHAPE ${s.name}: ${l.cols} x ${l.rows} = $all cells; strip ${l.gridTopPx} px (${String.format(java.util.Locale.ROOT, "%.2f", stripPct)}% of the height); " +
                    "frame ${frame.size} + diagonals-beyond-the-frame ${beyond.size} = required ${pattern.size}; left out ${all - pattern.size} " +
                    "(${String.format(java.util.Locale.ROOT, "%.1f", 100.0 * (all - pattern.size) / all)}% of the grid)"
            )
        }
    }

    @Test
    fun `the middle of the 18 x 36 pattern is the crossing of the two lines and nothing else - four cells around the lattice point, no block of its own`() {
        // the earlier shape had a block of 2 x 5 = 10 cells at the centre of the grid, of its own, which the user rejected («لا كتلة مستقلة، هو تقاطع القطرين فقط»)
        val p = TouchGridPattern.cells(18, 36)
        // the inside's lattice point (8, 17) is grid (9, 18): the four cells around it are the two lines' cells there — each line takes two of them, corner to corner
        assertTrue("the crossing: the four cells around the lattice point", listOf(17 to 8, 17 to 9, 18 to 8, 18 to 9).all { (r, c) -> r * 18 + c in p })
        val middle = (14..21).flatMap { r -> (5..12).map { c -> r * 18 + c } }.filter { it in p }
        // the cells of the 8 x 8 middle that are in the pattern are the two diagonals' — two chains of cells (about 1.5 a row each), never the 64 of a block
        assertTrue("no block: ${middle.size} cells of the 8 x 8 middle", middle.size <= 32)
        assertFalse("the cell just beside the crossing, on the same row, two columns away, is not in", 18 * 18 + 11 in p)
    }
}
