package net.neuralcheck.domain.manual

/**
 * Scripted screens and clocks for the M-02 grid tests and the golden payload generator (fieldsVersion 71). Nothing here is a producer: the
 * recorder, the layout and the density selector are the REAL ones; this only stands in for the platform's answers (`TouchGridEnvironment`) and
 * for the clocks — a real run needs a screen and a finger.
 *
 * The default screen: 800 x 1500 px at 254 dpi = exactly 10 px per millimetre, so a 4 mm cell is 40 px and `800 / 40` is 20 columns: the cells are SQUARE, 40 x 40 px; the rows are the
 * whole cells that fit under the empty strip of two cells (`(1500 - 80) / 40` = 35), so the grid is 20 x 35 = 700 cells, 1400 px high, and the strip is the 100 px left above it: the touch
 * surface of the run is `TouchGridSurface(800, 1400, 0, 100)`. Density `XDPI`. The samples the tests feed the recorder are in that surface's frame (a sample in the strip has a negative `y`).
 */
object TouchGridTestKit {

    val surface = TouchGridSurface(widthPx = 800, heightPx = 1500, originXPx = 0, originYPx = 0)

    /** The platform's answers for a modern phone by default; every argument can be replaced by an absence. */
    fun environment(
        xdpi: Float = 254f,
        ydpi: Float = 254f,
        densityDpi: Int = 320,
        displayWidthPx: Reading<Int> = Reading.Value(800),
        displayHeightPx: Reading<Int> = Reading.Value(1500),
        modePhysicalWidthPx: Reading<Int> = Reading.Value(800),
        modePhysicalHeightPx: Reading<Int> = Reading.Value(1500),
        gestureInsetsPx: Reading<PxRect> = Reading.Value(PxRect(24, 0, 24, 48)),
        mandatoryGestureInsetsPx: Reading<PxRect> = Reading.Value(PxRect(0, 0, 0, 48)),
        exclusionRectsPx: Reading<List<PxRect>> = Reading.Value(listOf(PxRect(0, 0, 800, 1500))),
        systemBarsHidden: Reading<Boolean> = Reading.Value(true),
        cutoutRectsPx: Reading<List<PxRect>> = Reading.Value(emptyList()),
        roundedCorners: RoundedCorners = RoundedCorners.Radii(RoundedCornerRadii(80, 80, 80, 80)),
        sdkInt: Int = 34
    ) = TouchGridEnvironment(
        xdpi, ydpi, densityDpi, displayWidthPx, displayHeightPx, modePhysicalWidthPx, modePhysicalHeightPx,
        gestureInsetsPx, mandatoryGestureInsetsPx, exclusionRectsPx, systemBarsHidden, cutoutRectsPx, roundedCorners, sdkInt
    )

    /** A jump scripted into a sweep: the finger pauses [dtMs] and lands [distPx] further along its row. */
    class Jump(val dtMs: Long, val distPx: Float)

    /** A recorder on two scripted clocks (`now` = elapsedRealtime, `uptime` = uptimeMillis), already laid out on [surface] under [env]. */
    class Rig(
        env: TouchGridEnvironment = environment(),
        surface: TouchGridSurface = TouchGridTestKit.surface,
        maxPoints: Int = TouchGridContract.PATH_MAX_POINTS,
        nowAtOpen: Long = 1_000L,
        uptimeAtOpen: Long = 50_000L,
        configure: Boolean = true
    ) {
        var now: Long = nowAtOpen
        var uptime: Long = uptimeAtOpen
        val rec = TouchGridRecorder(nowMs = { now }, uptimeMs = { uptime }, maxPoints = maxPoints)

        init {
            if (configure) check(rec.configure(surface, env))
        }

        val layout: TouchGridLayout get() = rec.layout!!

        /** Both clocks move together. */
        fun tick(dtMs: Long) {
            now += dtMs
            uptime += dtMs
        }

        /** The centre of cell ([col], [row]) in the surface frame. */
        fun centre(col: Int, row: Int): Pair<Float, Float> =
            ((col + 0.5) * layout.cellWidthPx).toFloat() to ((row + 0.5) * layout.cellHeightPx).toFloat()

        fun sample(x: Float, y: Float, tMs: Long = uptime, major: Float? = 70f) = PointerSample(tMs, x, y, major)

        fun down(id: Int, x: Float, y: Float, tMs: Long = uptime, tool: Int = 1, major: Float? = 70f): Boolean =
            rec.pointerDown(id, tool, sample(x, y, tMs, major))

        fun move(id: Int, vararg points: Triple<Long, Float, Float>): Boolean =
            rec.pointerMoved(id, points.map { (t, x, y) -> PointerSample(t, x, y, 70f) })

        fun up(id: Int, x: Float, y: Float, tMs: Long): Boolean = rec.pointerEnded(id, TouchGridContract.StrokeEnds.UP, sample(x, y, tMs))

        /**
         * One finger zigzags over rows [fromRow] until [toRow] (left to right on the first, then back, and so on) along each row's centre line at
         * [stepPx] per [dtMs], both clocks moving; the finger lands on the first call ([land]) and is left DOWN at the last sample. The vertical
         * step to the next row is one cell high — under the jump threshold when the horizontal step is about half a cell. A row in [jumps] gets
         * the scripted jump when the finger passes the middle of a left-to-right row.
         */
        fun sweepRows(
            id: Int = 0,
            fromRow: Int = 0,
            toRow: Int = layout.rows,
            stepPx: Float = 30f,
            dtMs: Long = 8L,
            jumps: Map<Int, Jump> = emptyMap(),
            land: Boolean = true
        ) {
            val w = layout.surface.widthPx.toFloat()
            var leftToRight = true
            var landed = !land
            for (row in fromRow until toRow) {
                val y = ((row + 0.5) * layout.cellHeightPx).toFloat()
                var x = if (leftToRight) 1f else w - 1f
                if (!landed) {
                    down(id, x, y, uptime)
                    landed = true
                } else {
                    tick(dtMs)
                    move(id, Triple(uptime, x, y))
                }
                var jumped = false
                while (true) {
                    val jump = jumps[row]
                    if (jump != null && !jumped && leftToRight && x >= w / 2) {
                        jumped = true
                        tick(jump.dtMs)
                        x = minOf(x + jump.distPx, w - 1f)
                    } else {
                        x = if (leftToRight) minOf(x + stepPx, w - 1f) else maxOf(x - stepPx, 1f)
                        tick(dtMs)
                    }
                    move(id, Triple(uptime, x, y))
                    if ((leftToRight && x >= w - 1f) || (!leftToRight && x <= 1f)) break
                }
                leftToRight = !leftToRight
            }
        }

        /** [sweepRows] over every row. */
        fun sweepAllRows(id: Int = 0, stepPx: Float = 30f, dtMs: Long = 8L) = sweepRows(id, 0, layout.rows, stepPx, dtMs)
    }
}
