package net.neuralcheck.domain.manual

import java.util.TreeSet

/**
 * The cells an M-02 run REQUIRES — the «frame + X» pattern (user decision 2026-10-21, his words: «M-02: استبدل الشبكة الحالية باختبار لمس من نوع «إطار + X» … يفحص الزوايا
 * فقط وحرف X في المنتصف»; wire contract `wire-m02-frame-x-2026-10-21.md`, item 6, copied into `docs/FV68_CONTRACT.md` section 7-ي, paragraph of 2026-10-21; the SHAPE of the diagonals was
 * redrawn the same day on the user's order to match the system's touch test — paragraph 2 of 2026-10-21).
 * The ONE source of the shape (rule 24): [TouchGridLayout] asks it for the pattern of a grid, the tests and the golden samples read the same function. Pure Kotlin.
 *
 * On a grid of [cols] x [rows] cells (a cell's index is `row x cols + col`):
 *  - the FRAME is every cell of the first row, the last row, the first column and the last column — one cell thick;
 *  - the DIAGONALS are the cells the two straight lines between the INNER corners of the frame cross ([diagonalPath]): the first one from the inner top-left corner (the corner of
 *    cell (1, 1)) to the inner bottom-right corner (the corner of cell (rows - 2, cols - 2)), the second its mirror image left to right. Each line starts and ends AT the frame, so
 *    the pattern is one piece: nothing is left open at the ends, and there is no block of its own at the centre — the middle is only where the two lines cross;
 *  - the pattern is the frame together with the diagonals. A camera cut-out is taken from it by [TouchGridLayout] (a cell the camera covers is not required whatever it is).
 *
 * THE DIAGONAL, exactly (this is the algorithm the contract paragraph copies). The inner rectangle is `w = cols - 2` columns by `h = rows - 2` rows (no diagonal when either is below 1).
 * In inner coordinates the line runs from (0, 0) to (w, h); across the i-th inner row (i = 0 until h) it covers the columns from `x0 = i x w / h` to `x1 = (i + 1) x w / h`, and the
 * cells taken in that row are those whose area the line enters: columns `floor(x0)` to `ceil(x1) - 1`. That is EVERY cell the line passes through, none it only touches at a corner: a
 * finger that draws the line lights exactly these (the grid traversal of [TouchGridLayout.cellsCrossed] gives the same set, and the tests say so cell by cell). The chain is joined
 * by shared EDGES — a row's last column is the next row's first — except where the line crosses a lattice point of the grid exactly: there the next row begins one column further and the
 * two cells meet at that corner only (a finger on the line lights both; a finger a hair off it passes through the cell beside and lights both just the same). The lattice points the
 * line meets inside are the multiples of `(w / g, h / g)`, g = gcd(w, h): g - 1 of them, none when w and h have no common factor.
 * Proved by the tests over every grid up to 70 x 70: the frame is whole, both inner-corner cells are in, the chain is connected (eight-neighbourhood, edges but for those corners),
 * the line passes through every cell taken and every cell it passes through is taken, the pattern is symmetric left-right and top-bottom.
 */
object TouchGridPattern {

    /** The cells of the first and last row and of the first and last column, sorted. */
    fun frameCells(cols: Int, rows: Int): Set<Int> {
        requireGrid(cols, rows)
        val out = TreeSet<Int>()
        for (c in 0 until cols) { out.add(c); out.add((rows - 1) * cols + c) }
        for (r in 0 until rows) { out.add(r * cols); out.add(r * cols + cols - 1) }
        return out
    }

    /**
     * The diagonal from the inner top-left corner to the inner bottom-right corner as `(row, col)` pairs in grid coordinates, in order along the line (a row's cells left to
     * right, then the next row). Empty when the grid has no inside (a side of 2 cells or fewer).
     */
    fun diagonalPath(cols: Int, rows: Int): List<Pair<Int, Int>> {
        requireGrid(cols, rows)
        val w = cols - 2
        val h = rows - 2
        if (w < 1 || h < 1) return emptyList()
        val out = ArrayList<Pair<Int, Int>>(w + h)
        for (i in 0 until h) {
            val first = (i.toLong() * w / h).toInt()                                  // floor(i x w / h)
            val last = (((i + 1).toLong() * w + h - 1) / h).toInt() - 1               // ceil((i + 1) x w / h) - 1
            for (c in first..last) out.add((1 + i) to (1 + c))
        }
        return out
    }

    /** The cells of the two diagonals, sorted (the second one is the first mirrored left to right). */
    fun diagonalCells(cols: Int, rows: Int): Set<Int> {
        val out = TreeSet<Int>()
        for ((r, c) in diagonalPath(cols, rows)) {
            out.add(r * cols + c)
            out.add(r * cols + (cols - 1 - c))
        }
        return out
    }

    /** The frame together with the two diagonals, sorted — the cells of a grid of [cols] x [rows] the pattern asks for, before any camera cut-out is taken from them. */
    fun cells(cols: Int, rows: Int): Set<Int> {
        val out = TreeSet<Int>(frameCells(cols, rows))
        out.addAll(diagonalCells(cols, rows))
        return out
    }

    private fun requireGrid(cols: Int, rows: Int) = require(cols >= 1 && rows >= 1) { "a grid has at least one cell on each axis: $cols x $rows" }
}
