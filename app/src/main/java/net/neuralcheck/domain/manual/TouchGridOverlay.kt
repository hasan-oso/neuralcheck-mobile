package net.neuralcheck.domain.manual

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Where the title with the live count and the technician's two buttons («لا يعمل» / «لم أختبر») stand ON the full-screen M-02 window — the one place that decides it
 * (rule 24), pure Kotlin so a plain test can compute it on any screen and a rendered test can compare it with what the dialog really draws. Every box is in the VIEW's own frame
 * (the window's: where Compose draws), so the grid's cells are at [TouchGridLayout.gridTopPx] below the top.
 *
 * User decision 2026-10-21 (his words): M-02 is a «إطار + X» touch test — a frame of cells on the screen's edge and two crossing diagonals — and «الزرّان بعد 15 ث لا يغطّيان أي
 * خلية مطلوبة»; and, on his order of the same day, it takes the shape of the system's own touch test: «لا نصّ فوق الخلايا. إن احتاج العنوان والعدّاد مكاناً فليكن في الشريط العلوي
 * الفارغ». The technical decisions are the orders' (app-engineer-8, and this one). What they decided:
 *
 *  - The TEXT (title, count, hint) stands in the EMPTY STRIP above the grid ([TouchGridLayout.gridTopPx] high; no cell is there, so no text ever meets a cell, required or not), in the
 *    widest stretch of the strip the camera's cut-out does not cover ([textBox]). If a window has no strip worth the name (a window no phone has), it falls back to the largest rectangle of
 *    free cells as before ([largestRect]); and when there is none, the dialog draws it at the top. The text has NO pointer input: a touch on it goes to the surface under it.
 *  - The BUTTONS stand on the cells the pattern LEAVES OUT (`TouchGridLayout.notRequiredCells`: the four triangles the diagonals make inside the frame; a cut-out cell is not among
 *    them, so nothing is ever drawn under the camera) and on those alone, as rectangles of WHOLE cells: so no rectangle of an element meets a required cell's rectangle at all — not by
 *    half, not by a pixel (the boxes are rounded INWARD to whole pixels). The widest run of free cells over [BUTTON_ROWS] rows that lie one above the other in ONE triangle (a free
 *    cell is joined to its four neighbours; the diagonals, being chains of cells, close the four triangles off from each other); ties go to the lowest rows, so on a phone held upright
 *    the buttons are in the bottom triangle, above the frame's last row. They need a run of at least `2 x BUTTON_MIN_COLS + BUTTONS_GAP_COLS` cells; each is then `(run - gap) / 2`
 *    cells wide and [BUTTON_ROWS] cells high, centred in the run. If NO triangle has such a run, [Placement.buttons] is null and the dialog draws them along the bottom
 *    ([buttonRowWithoutGrid]): the buttons exist to end a run, so they are never left out — and that is the one case where they may cover a required cell; no phone is that small
 *    (the tests sweep the sizes and say which are).
 *
 * The numbers below are choices of this order, each with its reason; none is a contract value (nothing of them reaches the server). The sizes are in MILLIMETRES, as the cell is, and
 * come out as whole cells.
 */
object TouchGridOverlay {

    /** A button is at least this high, mm: the 9 mm (48 dp) a finger needs [design M-02:72]. */
    const val BUTTON_MIN_HEIGHT_MM = 9.0

    /** A button is at least this wide, mm (a finger pad is about that). */
    const val BUTTON_MIN_WIDTH_MM = 12.0

    /** The free space left between the two buttons, mm (a slip of the finger must not press the other one). */
    const val BUTTONS_GAP_MM = 8.0

    /** The text block is looked for, in the free-cell fallback, in rectangles at least this wide, mm (the title, the hint and the count need that). */
    const val TEXT_MIN_WIDTH_MM = 24.0

    /** A button is this many cells high: a whole number of cells, so the box meets no required cell. */
    val BUTTON_ROWS: Int = ceil(BUTTON_MIN_HEIGHT_MM / TouchGridContract.CELL_TARGET_MM).toInt()

    /** A button is at least this many cells wide. */
    val BUTTON_MIN_COLS: Int = ceil(BUTTON_MIN_WIDTH_MM / TouchGridContract.CELL_TARGET_MM).toInt()

    /** The free columns left between the two buttons. */
    val BUTTONS_GAP_COLS: Int = ceil(BUTTONS_GAP_MM / TouchGridContract.CELL_TARGET_MM).toInt()

    /** The free-cell fallback looks for text rectangles at least this many cells wide. */
    val TEXT_MIN_COLS: Int = ceil(TEXT_MIN_WIDTH_MM / TouchGridContract.CELL_TARGET_MM).toInt()

    /** A rectangle in the view's own pixels (the window's, since the grid is drawn in the window): [left, right) x [top, bottom). */
    data class Box(val left: Double, val top: Double, val right: Double, val bottom: Double) {
        val width: Double get() = right - left
        val height: Double get() = bottom - top
        fun contains(x: Double, y: Double): Boolean = x >= left && x < right && y >= top && y < bottom

        /** Whether the two share an area — a shared edge or corner is not an intersection. */
        fun intersects(o: Box): Boolean = left < o.right && o.left < right && top < o.bottom && o.top < bottom
    }

    /** The buttons' row, and the two buttons in it as the screen shows them left to right (the first one stands at the end side in a right-to-left language). */
    class ButtonRow(val row: Box, val left: Box, val right: Box) {
        val buttons: List<Box> get() = listOf(left, right)
    }

    /** What the window leaves room for: [buttons] null = no triangle holds them; [text] null = nowhere to put it. */
    class Placement(val buttons: ButtonRow?, val text: Box?)

    /** A rectangle of whole cells: [cols] x [rows] from ([col], [row]). */
    private class CellRect(val col: Int, val row: Int, val cols: Int, val rows: Int) {
        val area: Int get() = cols * rows
    }

    /** The place of the text and of the buttons in [layout]'s window. */
    fun place(layout: TouchGridLayout): Placement {
        val cols = layout.cols
        val rows = layout.rows
        val label = componentLabels(layout)

        // the buttons: the widest run, over BUTTON_ROWS rows, of cells of one triangle; ties to the lowest rows, then to the left
        var bestRow = -1
        var bestCol = -1
        var bestLen = 0
        for (r in 0..rows - BUTTON_ROWS) {
            var c = 0
            while (c < cols) {
                val id = label[r * cols + c]
                if (id < 0 || !sameBelow(label, cols, c, r, id)) { c++; continue }
                var e = c
                while (e < cols && label[r * cols + e] == id && sameBelow(label, cols, e, r, id)) e++
                val len = e - c
                if (len > bestLen || (len == bestLen && len > 0 && r > bestRow)) { bestLen = len; bestRow = r; bestCol = c }
                c = e
            }
        }
        val reserved = BooleanArray(cols * rows)
        var buttons: ButtonRow? = null
        if (bestLen >= 2 * BUTTON_MIN_COLS + BUTTONS_GAP_COLS) {
            val w = (bestLen - BUTTONS_GAP_COLS) / 2
            val used = 2 * w + BUTTONS_GAP_COLS
            val c0 = bestCol + (bestLen - used) / 2
            val left = cellBox(layout, c0, bestRow, w, BUTTON_ROWS)
            val right = cellBox(layout, c0 + w + BUTTONS_GAP_COLS, bestRow, w, BUTTON_ROWS)
            buttons = ButtonRow(Box(left.left, left.top, right.right, left.bottom), left, right)
            for (r in bestRow until bestRow + BUTTON_ROWS) for (c in c0 until c0 + used) reserved[r * cols + c] = true
        }

        // the text: in the strip above the grid; failing that the largest rectangle of free cells that is at least TEXT_MIN_COLS wide (the highest one when two are as large), and
        // failing that the largest of any width
        val usable = { i: Int -> label[i] >= 0 && !reserved[i] }
        val inStrip = textBox(layout)
        val text = inStrip ?: (largestRect(layout, TEXT_MIN_COLS, usable) ?: largestRect(layout, 1, usable))?.let { cellBox(layout, it.col, it.row, it.cols, it.rows) }
        return Placement(buttons, text)
    }

    /**
     * The box of the text in the empty strip: the widest stretch of the strip's width the camera's cut-out does not cover (the leftmost when two are as wide), rounded INWARD to whole
     * pixels, as high as the strip. Null when the window has no strip as high as a cell, or when no stretch is [TEXT_MIN_COLS] cells wide (a camera across the whole strip).
     */
    fun textBox(layout: TouchGridLayout): Box? {
        val top = layout.gridTopPx
        if (top < layout.cellHeightPx) return null
        val width = layout.windowSurface.widthPx.toDouble()
        // the cut-out's stretches over the strip, merged, left to right
        val covered = layout.cutoutRectsInViewPx
            .filter { it.top < top && it.bottom > 0 && it.right > 0 && it.left < width }
            .map { max(0.0, it.left.toDouble()) to min(width, it.right.toDouble()) }
            .sortedBy { it.first }
        var bestLeft = 0.0
        var bestRight = 0.0
        fun consider(l: Double, r: Double) { if (r - l > bestRight - bestLeft) { bestLeft = l; bestRight = r } }
        var cursor = 0.0
        for ((l, r) in covered) {
            if (l > cursor) consider(cursor, l)
            cursor = max(cursor, r)
        }
        if (cursor < width) consider(cursor, width)
        val box = Box(ceil(bestLeft), 0.0, floor(bestRight), floor(top.toDouble()))
        return if (box.width >= TEXT_MIN_COLS * layout.cellWidthPx) box else null
    }

    /**
     * Where the buttons stand when there is NO grid to place them on (the layout was never configured — the surface was never laid out) or the grid has no triangle for them:
     * along the bottom of the [widthPx] x [heightPx] window, [fallbackHeightPx] high. With no grid there is no required cell to hide; the buttons must still exist, because they are
     * how a technician ends a run that has no surface.
     */
    fun buttonRowWithoutGrid(widthPx: Int, heightPx: Int, fallbackHeightPx: Double): ButtonRow {
        val margin = widthPx * 0.05
        val bottom = heightPx - margin
        val top = bottom - fallbackHeightPx
        val gap = margin
        val buttonW = (widthPx - 2 * margin - gap) / 2
        return ButtonRow(
            row = Box(margin, top, widthPx - margin, bottom),
            left = Box(margin, top, margin + buttonW, bottom),
            right = Box(widthPx - margin - buttonW, top, widthPx - margin, bottom)
        )
    }

    /**
     * The REQUIRED cells of [layout] whose rectangle shares an area with one of [boxes] — empty is what [place] guarantees. Exact: every required cell's rectangle against every box, no
     * sampling; [boxes] are the elements that stand in the window, in the view's pixels (a test passes the REAL bounds of what the dialog drew).
     */
    fun requiredCellsMetBy(layout: TouchGridLayout, boxes: List<Box>): List<Int> =
        (0 until layout.cols * layout.rows).filter { index ->
            layout.isRequired(index) && boxes.any { it.intersects(cellBox(layout, index % layout.cols, index / layout.cols, 1, 1, inward = false)) }
        }

    /** The rectangle of the whole cells ([col], [row]) .. + ([cols], [rows]) in the view's pixels (the grid starts [TouchGridLayout.gridTopPx] below the top), rounded INWARD to whole pixels (so it lies inside the cells, never on a neighbour). */
    fun cellBox(layout: TouchGridLayout, col: Int, row: Int, cols: Int, rows: Int, inward: Boolean = true): Box {
        val top = layout.gridTopPx.toDouble()
        val l = col * layout.cellWidthPx
        val t = top + row * layout.cellHeightPx
        val r = (col + cols) * layout.cellWidthPx
        val b = top + (row + rows) * layout.cellHeightPx
        return if (inward) Box(ceil(l), ceil(t), floor(r), floor(b)) else Box(l, t, r, b)
    }

    private fun sameBelow(label: IntArray, cols: Int, c: Int, r: Int, id: Int): Boolean {
        for (k in 1 until BUTTON_ROWS) if (label[(r + k) * cols + c] != id) return false
        return true
    }

    /** The triangle (4-connected group of free cells) every free cell belongs to, numbered from 0; -1 for a cell that is not free. */
    private fun componentLabels(layout: TouchGridLayout): IntArray {
        val cols = layout.cols
        val rows = layout.rows
        val label = IntArray(cols * rows) { -1 }
        var next = 0
        val queue = ArrayDeque<Int>()
        for (start in label.indices) {
            if (label[start] != -1 || !layout.isNotRequired(start)) continue
            label[start] = next
            queue.add(start)
            while (queue.isNotEmpty()) {
                val i = queue.removeFirst()
                val r = i / cols
                val c = i % cols
                for ((dr, dc) in NEIGHBOURS) {
                    val nr = r + dr
                    val nc = c + dc
                    if (nr !in 0 until rows || nc !in 0 until cols) continue
                    val n = nr * cols + nc
                    if (label[n] == -1 && layout.isNotRequired(n)) { label[n] = next; queue.add(n) }
                }
            }
            next++
        }
        return label
    }

    private val NEIGHBOURS = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)

    /** The rectangle of the most cells, at least [minCols] wide, all of which satisfy [usable]; ties go to the highest, then the leftmost. */
    private fun largestRect(layout: TouchGridLayout, minCols: Int, usable: (Int) -> Boolean): CellRect? {
        val cols = layout.cols
        val rows = layout.rows
        val runRight = IntArray(cols * rows)
        for (r in 0 until rows) for (c in cols - 1 downTo 0) {
            val i = r * cols + c
            runRight[i] = if (usable(i)) 1 + (if (c + 1 < cols) runRight[i + 1] else 0) else 0
        }
        var best: CellRect? = null
        for (r0 in 0 until rows) for (c0 in 0 until cols) {
            var width = Int.MAX_VALUE
            var r1 = r0
            while (r1 < rows && runRight[r1 * cols + c0] > 0) {
                width = min(width, runRight[r1 * cols + c0])
                if (width < minCols) break
                val candidate = CellRect(c0, r0, width, r1 - r0 + 1)
                if (best == null || candidate.area > best.area) best = candidate
                r1++
            }
        }
        return best
    }
}
