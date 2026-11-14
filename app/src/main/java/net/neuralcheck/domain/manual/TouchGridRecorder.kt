package net.neuralcheck.domain.manual

import net.neuralcheck.domain.manual.TouchGridContract.AbsentReasons
import net.neuralcheck.domain.manual.TouchGridContract.PathCapKeys
import net.neuralcheck.domain.manual.TouchGridContract.StrokeEnds
import net.neuralcheck.domain.manual.TouchGridContract.StrokeKeys
import net.neuralcheck.domain.manual.TouchGridContract.SuspectKeys
import net.neuralcheck.domain.manual.TouchGridContract.SuspectKinds
import net.neuralcheck.domain.model.ManualStatuses
import kotlin.math.hypot
import kotlin.math.max

/**
 * One sample of one finger: [tMs] is the event time on the `uptimeMillis` clock (what the platform stamps on a touch event), [x] / [y] are
 * pixels in the touch surface's own frame, [touchMajorPx] the contact's major axis when the platform gives it.
 */
class PointerSample(val tMs: Long, val x: Float, val y: Float, val touchMajorPx: Float?)

/** What the technician was shown as a mark: the segment between two recorded points of one stroke (kinds: [TouchGridContract.SuspectKinds]). */
class DisplayedSuspect(
    val kind: String,
    val strokeIndex: Int,
    val fromPointIndex: Int,
    val toPointIndex: Int,
    val x0: Float,
    val y0: Float,
    val x1: Float,
    val y1: Float
) {
    fun toWire(): Map<String, Any?> = linkedMapOf(
        SuspectKeys.KIND to kind,
        SuspectKeys.STROKE_INDEX to strokeIndex,
        SuspectKeys.FROM_POINT_INDEX to fromPointIndex,
        SuspectKeys.TO_POINT_INDEX to toPointIndex
    )
}

/**
 * The raw record of ONE run of the M-02 touch grid.
 *
 * fieldsVersion 69 (docs/FV68_CONTRACT.md 7-h (b), N-19): the counters and the two clocks. fieldsVersion 71 (7-ي, A-M02): a grid of 4 mm square
 * cells laid out from the window and the screen's density ([TouchGridLayout]); cells filled by SWEEPING (every cell a finger's path crosses,
 * not only the ones a sample landed on); and the RAW PATH of every finger — `strokes[]`, every sample, nothing smoothed, rounded, merged or
 * dropped on a judgement (rule 20; only a non-finite number cannot be written, and it is counted, and a cap on the points is declared).
 * Since 2026-10-21 (user decision, contract section 7-ي paragraph of that date) the cells a run REQUIRES are the «frame + X» ones ([TouchGridPattern]) minus the camera's:
 * `touchedCells` counts the REQUIRED cells that were lit, `untouchedCells` lists the required ones that were not, `totalCells` is their number; a cell the pattern leaves out
 * (`notRequiredCells`) is neither lit nor counted — but a finger that crosses it is recorded in `strokes[]` exactly like any other (the raw is not touched, rule 20).
 *
 * Pure Kotlin, no Android and no Compose: the surface view feeds it (converting `MotionEvent`s), and the tests feed it scripted samples on a
 * scripted clock. It records and COUNTS; it does not judge — the thresholds that decide a dead spot live on the server. What the technician
 * is shown (the marks) is a display log (`displayedSuspects`), computed here with [TouchGridContract]'s tagged figures.
 *
 * What it keeps, and what each number means:
 *  - the grid ([TouchGridLayout]) and how many REQUIRED cells (not a cut-out's) were touched;
 *  - [measurements]' `timeToFirstTouchMs`: from the moment the run was OPENED to the first touch (clock [nowMs], `elapsedRealtime`);
 *  - the elapsed time of the run: from the FIRST TOUCH to the end — the last cell lit, a technician's button, or a dismissal (same clock);
 *  - the stroke clock: `downAtMs` is the event time of the finger's landing minus the moment the run was opened, on [uptimeMs]
 *    (`uptimeMillis`, the clock the touch events use — two clocks, never subtracted from one another: contract 7-ي, point 5);
 *  - the most pointers seen down at once;
 *  - why it ended ([EndReasons]).
 * With no touch at all there is no first touch, so both times are ABSENT from the map (not 0: rule 22), and `touchedCells = 0` says why.
 *
 * The layout is set by [configure] — as often as the platform's answers change, until the first finger lands (so a run ended with no touch
 * still carries the surface it was laid on). [configure] is given the WINDOW; the touch surface of the run is the layout's own `surface`, the window less the empty strip above the
 * grid, and every sample is in THAT frame (the view subtracts the strip): a sample in the strip has a negative `y`, is recorded raw like any other, and lights no cell — the server's
 * own rule for a sample off the surface (`touchPath.js`: such a sample visits no cell; the segment from or to it visits only the cell of the end that is on the surface).
 *
 * @param nowMs a monotonic clock in ms (the dialog passes `SystemClock.elapsedRealtime`).
 * @param uptimeMs the clock touch events are stamped on (the dialog passes `SystemClock.uptimeMillis`).
 * @param maxPoints the cap on recorded points ([TouchGridContract.PATH_MAX_POINTS] in production; the tests lower it).
 */
class TouchGridRecorder(
    private val nowMs: () -> Long,
    private val uptimeMs: () -> Long = nowMs,
    private val maxPoints: Int = TouchGridContract.PATH_MAX_POINTS
) {

    companion object {
        /**
         * The test this grid runs for: M-02. The one source of the literal where a decision stands on it
         * (rule 24) — the row's dialog route in `ReportScreen` and [ManualChipActions.TAP_KEEPS_STORED_RAW] read it.
         */
        const val TEST_ID = "M-02"

        /**
         * The technician's buttons («لا يعمل» / «لم أختبر») appear this long after the run is
         * OPENED — not after the first touch: a dead digitizer never gives a first touch, and
         * the buttons are how a technician ends that run.
         */
        const val TECHNICIAN_BUTTONS_AFTER_MS = 15_000L

        /**
         * What a DISMISSED run records (contract 7-h (b)): NOTHING when no cell was touched
         * (`raw == null` — an accidental open must not overwrite the raw of a real earlier
         * run); otherwise the raw together with the answer the test already holds — a
         * dismissal never erases an answer — or `not_tested` when it held none.
         */
        fun recordOnDismiss(currentStatus: String?, raw: Map<String, Any?>?): Pair<String, Map<String, Any?>>? =
            raw?.let { (currentStatus ?: ManualStatuses.NOT_TESTED) to it }
    }

    /** The values of `measurements.endReason` (N-19) — one source (rule 24). */
    object EndReasons {
        const val AUTO_COMPLETE = "auto_complete"
        const val USER_MARKED_NOT_WORKING = "user_marked_not_working"
        const val USER_MARKED_NOT_TESTED = "user_marked_not_tested"
        const val DISMISSED = "dismissed"
        val ALL: List<String> = listOf(AUTO_COMPLETE, USER_MARKED_NOT_WORKING, USER_MARKED_NOT_TESTED, DISMISSED)
    }

    /** The keys of the `measurements` map (N-19 and fieldsVersion 71, contract 7-ي ف-01..ف-03) — one source (rule 24). */
    object Keys {
        // fv69 — kept with their meaning (the meaning of gridCols/gridRows/totalCells/touchedCells and of auto_complete changed in fv71)
        const val GRID_COLS = "gridCols"
        const val GRID_ROWS = "gridRows"
        const val TOUCHED_CELLS = "touchedCells"
        const val TOTAL_CELLS = "totalCells"
        const val ELAPSED_MS = "elapsedMs"
        const val TIME_TO_FIRST_TOUCH_MS = "timeToFirstTouchMs"
        const val END_REASON = "endReason"
        const val MAX_SIMULTANEOUS_POINTERS = "maxSimultaneousPointers"

        // fv71 — the surface, the screen, the density, the cell (design M2:184-187)
        const val SURFACE_WIDTH_PX = "surfaceWidthPx"
        const val SURFACE_HEIGHT_PX = "surfaceHeightPx"
        const val SURFACE_ORIGIN_X_PX = "surfaceOriginXPx"
        const val SURFACE_ORIGIN_Y_PX = "surfaceOriginYPx"
        const val DISPLAY_WIDTH_PX = "displayWidthPx"
        const val DISPLAY_HEIGHT_PX = "displayHeightPx"
        const val MODE_PHYSICAL_WIDTH_PX = "modePhysicalWidthPx"
        const val MODE_PHYSICAL_HEIGHT_PX = "modePhysicalHeightPx"
        const val XDPI = "xdpi"
        const val YDPI = "ydpi"
        const val DENSITY_DPI = "densityDpi"
        const val PX_PER_MM = "pxPerMm"
        const val DENSITY_SOURCE = "densitySource"
        const val DENSITY_CHECK = "densityCheck"
        const val CELL_TARGET_MM = "cellTargetMm"
        const val CELL_WIDTH_PX = "cellWidthPx"
        const val CELL_HEIGHT_PX = "cellHeightPx"

        // fv71 — the final state and the edges (design M2:188-189)
        const val UNTOUCHED_CELLS = "untouchedCells"
        const val CUTOUT_CELLS = "cutoutCells"

        // fv71, 2026-10-21 — «frame + X»: the pattern of the required set and the cells it leaves out (wire-m02-frame-x-2026-10-21.md; contract 7-ي paragraph of that date)
        const val GRID_PATTERN = "gridPattern"
        const val NOT_REQUIRED_CELLS = "notRequiredCells"
        const val GESTURE_INSETS_PX = "gestureInsetsPx"
        const val MANDATORY_GESTURE_INSETS_PX = "mandatoryGestureInsetsPx"
        const val EXCLUSION_RECTS_PX = "exclusionRectsPx"
        const val SYSTEM_BARS_HIDDEN = "systemBarsHidden"
        const val CUTOUT_RECTS_PX = "cutoutRectsPx"
        const val ROUNDED_CORNER_RADII_PX = "roundedCornerRadiiPx"
        const val ROUNDED_CORNERS_STATE = "roundedCornersState"

        // fv71 — N71-01, the path, the display log, the cap (contract ف-03)
        const val ABSENT_FIELD_REASONS = "absentFieldReasons"
        const val STROKES = "strokes"
        const val UNBUFFERED_DISPATCH = "unbufferedDispatch"
        const val RESAMPLED_FLAG_STATE = "resampledFlagState"
        const val DISPLAYED_SUSPECTS = "displayedSuspects"
        const val PATH_CAP = "pathCap"
    }

    // ───────────────────────── state ─────────────────────────

    private val openedAtMs: Long = nowMs()
    private val openedAtUptimeMs: Long = uptimeMs()

    /** The grid of this run; null until [configure] was called once. */
    var layout: TouchGridLayout? = null
        private set
    private var environment: TouchGridEnvironment? = null
    private var touched = BooleanArray(0)

    var touchedCount: Int = 0
        private set

    /** The clock reading at the first touch; null until one happens. */
    private var firstTouchAtMs: Long? = null

    /** The clock reading when the LAST required cell was lit; null while some required cell is untouched. */
    private var completedAtMs: Long? = null

    var maxSimultaneousPointers: Int = 0
        private set

    private var unbufferedDispatch: Reading<Boolean>? = null

    private class Stroke(val pointerId: Int, val toolType: String, val downAtMs: Long) {
        val dtMs = ArrayList<Long>()
        val x = ArrayList<Float>()
        val y = ArrayList<Float>()

        /** NaN where the platform gave none for that sample — the key is then left out of the stroke (never filled with zeros). */
        val touchMajorPx = ArrayList<Float>()
        var lastTMs = 0L
        var end: String? = null
    }

    /** A finger that is down: its stroke (null when the cap stopped recording before it landed) and what the display detector remembers. */
    private class Track(val stroke: Stroke?, val strokeIndex: Int) {
        var lastX = 0f
        var lastY = 0f
        var lastT = 0L
        var lastRecordedIndex = -1
        var hasLast = false
        val stepsMm = ArrayList<Double>()
        val stepDtMs = ArrayList<Long>()
    }

    private val tracks = LinkedHashMap<Int, Track>()
    private val strokes = ArrayList<Stroke>()
    private var recordedPoints = 0
    private var pointsDropped = 0
    private val suspects = ArrayList<DisplayedSuspect>()

    val isConfigured: Boolean get() = layout != null

    /** Whether [configure] may still change the layout: no finger has landed yet. */
    val canReconfigure: Boolean get() = firstTouchAtMs == null

    val totalCells: Int get() = layout?.totalCells ?: 0

    val isComplete: Boolean get() = isConfigured && totalCells > 0 && touchedCount == totalCells

    /** The marks the technician sees, in the order they appeared (a copy). */
    val displayedSuspects: List<DisplayedSuspect> get() = suspects.toList()

    /** Whether the cell is required AND lit — a cut-out cell and a cell the pattern leaves out are never lit. */
    fun isTouched(index: Int): Boolean = index in touched.indices && touched[index]

    /**
     * Lays the grid on [surface] under [environment]. Allowed until the first finger lands ([canReconfigure]); later calls are ignored and
     * return false — the run's grid does not move under a finger.
     */
    fun configure(surface: TouchGridSurface, environment: TouchGridEnvironment): Boolean {
        if (!canReconfigure) return false
        val l = TouchGridLayout.compute(surface, environment)
        layout = l
        this.environment = environment
        touched = BooleanArray(l.cols * l.rows)
        touchedCount = 0
        return true
    }

    /** What the surface view found out when it asked the platform for unbuffered dispatch (design §2.1): requested, or why not. */
    fun setUnbufferedDispatch(reading: Reading<Boolean>) {
        unbufferedDispatch = reading
    }

    // ───────────────────────── events ─────────────────────────

    /** A finger landed. Returns true if a cell turned on (the caller redraws). */
    fun pointerDown(pointerId: Int, androidToolType: Int, s: PointerSample): Boolean {
        val l = layout ?: return false
        val now = nowMs()
        if (firstTouchAtMs == null) firstTouchAtMs = now
        tracks.remove(pointerId)?.let { closeTrack(it, StrokeEnds.OPEN_AT_END) }   // a pointer id that was never lifted: closed where it stands
        val stroke = if (recordedPoints < maxPoints) {
            Stroke(pointerId, TouchGridContract.ToolTypes.nameOf(androidToolType), s.tMs - openedAtUptimeMs).also { strokes.add(it) }
        } else null
        val track = Track(stroke, if (stroke != null) strokes.size - 1 else -1)
        tracks[pointerId] = track
        if (tracks.size > maxSimultaneousPointers) maxSimultaneousPointers = tracks.size
        return addSample(l, track, s, isFirst = true, now = now)
    }

    /** One MOVE event of one finger: the samples the platform grouped into it (history first, the current one last), in time order. */
    fun pointerMoved(pointerId: Int, samples: List<PointerSample>): Boolean {
        val l = layout ?: return false
        val track = tracks[pointerId] ?: return false
        val now = nowMs()
        var lit = false
        for (s in samples) if (addSample(l, track, s, isFirst = false, now = now)) lit = true
        return lit
    }

    /**
     * A finger left: [end] is [StrokeEnds.UP] / [StrokeEnds.CANCEL] / [StrokeEnds.CANCEL_FLAGGED]; [last] is its position as the ending event
     * reports it (null when there is none worth recording). Returns true if a cell turned on.
     */
    fun pointerEnded(pointerId: Int, end: String, last: PointerSample?): Boolean {
        require(end == StrokeEnds.UP || end == StrokeEnds.CANCEL || end == StrokeEnds.CANCEL_FLAGGED) { "unknown stroke end \"$end\"" }
        val l = layout ?: return false
        val track = tracks.remove(pointerId) ?: return false
        var lit = false
        if (last != null) lit = addSample(l, track, last, isFirst = false, now = nowMs())
        closeTrack(track, end)
        return lit
    }

    /** The run is over: every finger still down ends as `open_at_end`. */
    fun finishOpenStrokes() {
        for (track in tracks.values.toList()) closeTrack(track, StrokeEnds.OPEN_AT_END)
        tracks.clear()
    }

    private fun closeTrack(track: Track, end: String) {
        val stroke = track.stroke ?: return
        stroke.end = end
        // design §3.3: a stroke that lifts within EDGE_MARGIN of an edge, heading to it, ends there — it is not a dead spot (marked, not counted)
        if (end == StrokeEnds.UP && track.lastRecordedIndex >= 1) edgeExit(track, stroke)
    }

    private fun edgeExit(track: Track, stroke: Stroke) {
        val l = layout ?: return
        val i = track.lastRecordedIndex
        val x = stroke.x[i]
        val y = stroke.y[i]
        // the direction of travel comes from the nearest earlier sample that is NOT at the same place: an UP event repeats the last position
        var j = i - 1
        while (j >= 0 && stroke.x[j] == x && stroke.y[j] == y) j--
        if (j < 0) return
        val dx = (x - stroke.x[j]).toDouble()
        val dy = (y - stroke.y[j]).toDouble()
        val marginPx = TouchGridContract.EDGE_MARGIN_MM * l.density.pxPerMm
        val toLeft = x < marginPx && dx < 0
        val toRight = l.surface.widthPx - x < marginPx && dx > 0
        val toTop = y < marginPx && dy < 0
        val toBottom = l.surface.heightPx - y < marginPx && dy > 0
        if (toLeft || toRight || toTop || toBottom) {
            suspects.add(DisplayedSuspect(SuspectKinds.EDGE_EXIT, track.strokeIndex, i, i, x, y, x, y))
        }
    }

    // ───────────────────────── one sample ─────────────────────────

    private fun addSample(l: TouchGridLayout, track: Track, s: PointerSample, isFirst: Boolean, now: Long): Boolean {
        if (!s.x.isFinite() || !s.y.isFinite()) { pointsDropped++; return false }   // cannot be written: counted, never silent (E18)

        // the display detector (design §3.1) — only on a step that has a previous sample, and never on a dropped one
        var flagged = false
        if (track.hasLast && !isFirst) {
            val stepMm = hypot((s.x - track.lastX).toDouble(), (s.y - track.lastY).toDouble()) / l.density.pxPerMm
            val dt = (s.tMs - track.lastT).coerceAtLeast(0L)
            val kind = jumpKind(track, stepMm, dt)
            if (kind != null) {
                flagged = true
                if (track.stroke != null && recordedPoints < maxPoints && track.lastRecordedIndex >= 0) {
                    suspects.add(
                        DisplayedSuspect(kind, track.strokeIndex, track.lastRecordedIndex, track.lastRecordedIndex + 1, track.lastX, track.lastY, s.x, s.y)
                    )
                }
            }
            track.stepsMm.add(stepMm)
            track.stepDtMs.add(dt)
            if (track.stepsMm.size > TouchGridContract.STEP_HISTORY) { track.stepsMm.removeAt(0); track.stepDtMs.removeAt(0) }
        }

        // the cells: every cell the segment crosses — except across a flagged jump, which fills only where the finger now is — and, as the server reads the raw, none for a sample off
        // the surface (the strip above the grid), and only the cell of the end that is on it for a segment that has one end off it
        val here = l.inside(s.x.toDouble(), s.y.toDouble())
        val cells = when {
            !here -> emptyList()
            track.hasLast && !isFirst && !flagged && l.inside(track.lastX.toDouble(), track.lastY.toDouble()) ->
                l.cellsCrossed(track.lastX.toDouble(), track.lastY.toDouble(), s.x.toDouble(), s.y.toDouble())
            else -> listOf(l.cellIndexAt(s.x.toDouble(), s.y.toDouble()))
        }
        var lit = false
        for (c in cells) {
            if (!touched[c] && l.isRequired(c)) {
                touched[c] = true
                touchedCount++
                lit = true
            }
        }
        if (lit && isComplete && completedAtMs == null) completedAtMs = now

        // the raw path
        val stroke = track.stroke
        if (stroke != null && recordedPoints < maxPoints) {
            stroke.dtMs.add(if (stroke.x.isEmpty()) 0L else (s.tMs - stroke.lastTMs).coerceAtLeast(0L))
            stroke.x.add(s.x)
            stroke.y.add(s.y)
            stroke.touchMajorPx.add(s.touchMajorPx?.takeIf { it.isFinite() } ?: Float.NaN)
            stroke.lastTMs = s.tMs
            recordedPoints++
            track.lastRecordedIndex = stroke.x.size - 1
        } else {
            pointsDropped++
        }

        track.lastX = s.x
        track.lastY = s.y
        track.lastT = s.tMs
        track.hasLast = true
        return lit
    }

    /** `gap_jump` / `coord_jump` when this step is a jump of a finger that was moving (design §3.1); null otherwise. */
    private fun jumpKind(track: Track, stepMm: Double, dtMs: Long): String? {
        val steps = track.stepsMm
        if (steps.size < TouchGridContract.MOVING_STEPS) return null
        val recentDt = track.stepDtMs.takeLast(TouchGridContract.MOVING_STEPS).sum()
        if (recentDt <= 0L) return null
        val speed = steps.takeLast(TouchGridContract.MOVING_STEPS).sum() / (recentDt / 1000.0)
        if (speed < TouchGridContract.V_MIN_MM_S) return null
        val threshold = max(TouchGridContract.J_MIN_MM, TouchGridContract.JUMP_MEDIAN_FACTOR * median(steps))
        if (stepMm <= threshold) return null
        val dtThreshold = max(
            TouchGridContract.GAP_MIN_MS.toDouble(),
            TouchGridContract.GAP_MEDIAN_FACTOR * median(track.stepDtMs.map { it.toDouble() })
        )
        return if (dtMs > dtThreshold) SuspectKinds.GAP_JUMP else SuspectKinds.COORD_JUMP
    }

    private fun median(values: List<Double>): Double {
        val sorted = values.sorted()
        val n = sorted.size
        return if (n % 2 == 1) sorted[n / 2] else (sorted[n / 2 - 1] + sorted[n / 2]) / 2.0
    }

    // ───────────────────────── the raw ─────────────────────────

    /**
     * The raw for this run, as `measurements` (N-19, fieldsVersion 71 contract 7-ي). The end of the run is `completedAtMs` for
     * [EndReasons.AUTO_COMPLETE] (the moment the last required cell lit) and "now" for the others. `elapsedMs` and `timeToFirstTouchMs` are
     * ABSENT when nothing was touched. `displayedSuspects` is ABSENT after an automatic completion (no marks were shown, E21 item 2) and
     * PRESENT — possibly `[]` — when the run ended by a button or a dismissal.
     *
     * Call it once the run is over; fingers still down are closed as `open_at_end` first.
     */
    fun measurements(endReason: String): Map<String, Any?> {
        require(endReason in EndReasons.ALL) { "unknown endReason \"$endReason\"" }
        val l = checkNotNull(layout) { "the grid was never laid out: configure() comes before any raw" }
        val env = checkNotNull(environment)
        finishOpenStrokes()

        val absent = LinkedHashMap<String, String>()
        val map = LinkedHashMap<String, Any?>()

        map[Keys.GRID_COLS] = l.cols
        map[Keys.GRID_ROWS] = l.rows
        map[Keys.GRID_PATTERN] = l.gridPattern
        map[Keys.TOTAL_CELLS] = l.totalCells
        map[Keys.TOUCHED_CELLS] = touchedCount
        val first = firstTouchAtMs
        if (first != null) {
            val end = if (endReason == EndReasons.AUTO_COMPLETE) completedAtMs ?: nowMs() else nowMs()
            map[Keys.TIME_TO_FIRST_TOUCH_MS] = first - openedAtMs
            map[Keys.ELAPSED_MS] = end - first
        }
        map[Keys.END_REASON] = endReason
        map[Keys.MAX_SIMULTANEOUS_POINTERS] = maxSimultaneousPointers

        // the surface, the screen
        map[Keys.SURFACE_WIDTH_PX] = l.surface.widthPx
        map[Keys.SURFACE_HEIGHT_PX] = l.surface.heightPx
        map[Keys.SURFACE_ORIGIN_X_PX] = l.surface.originXPx
        map[Keys.SURFACE_ORIGIN_Y_PX] = l.surface.originYPx
        put(map, absent, Keys.DISPLAY_WIDTH_PX, env.displayWidthPx)
        put(map, absent, Keys.DISPLAY_HEIGHT_PX, env.displayHeightPx)
        put(map, absent, Keys.MODE_PHYSICAL_WIDTH_PX, env.modePhysicalWidthPx)
        put(map, absent, Keys.MODE_PHYSICAL_HEIGHT_PX, env.modePhysicalHeightPx)

        // the density and the cell
        map[Keys.XDPI] = env.xdpi
        map[Keys.YDPI] = env.ydpi
        map[Keys.DENSITY_DPI] = env.densityDpi
        map[Keys.PX_PER_MM] = l.density.pxPerMm
        map[Keys.DENSITY_SOURCE] = l.density.source
        map[Keys.DENSITY_CHECK] = l.density.failedChecks
        map[Keys.CELL_TARGET_MM] = TouchGridContract.CELL_TARGET_MM
        map[Keys.CELL_WIDTH_PX] = l.cellWidthPx
        map[Keys.CELL_HEIGHT_PX] = l.cellHeightPx

        // the final state
        val untouched = ArrayList<Int>()
        for (i in 0 until l.cols * l.rows) if (!touched[i] && l.isRequired(i)) untouched.add(i)
        map[Keys.UNTOUCHED_CELLS] = untouched
        map[Keys.CUTOUT_CELLS] = l.cutoutCells
        map[Keys.NOT_REQUIRED_CELLS] = l.notRequiredCells

        // the edges and gestures
        put(map, absent, Keys.GESTURE_INSETS_PX, env.gestureInsetsPx) { it.toWire() }
        put(map, absent, Keys.MANDATORY_GESTURE_INSETS_PX, env.mandatoryGestureInsetsPx) { it.toWire() }
        put(map, absent, Keys.EXCLUSION_RECTS_PX, env.exclusionRectsPx) { rects -> rects.map { it.toWire() } }
        put(map, absent, Keys.SYSTEM_BARS_HIDDEN, env.systemBarsHidden)
        put(map, absent, Keys.CUTOUT_RECTS_PX, env.cutoutRectsPx) { rects -> rects.map { it.toWire() } }
        when (val rc = env.roundedCorners) {
            is RoundedCorners.Radii -> map[Keys.ROUNDED_CORNER_RADII_PX] = rc.radii.toWire()
            is RoundedCorners.State -> map[Keys.ROUNDED_CORNERS_STATE] = rc.state
        }

        // the raw path
        val strokeList = ArrayList<Map<String, Any?>>()
        var touchMajorMissing = false
        for (st in strokes) {
            val m = LinkedHashMap<String, Any?>()
            m[StrokeKeys.POINTER_ID] = st.pointerId
            m[StrokeKeys.TOOL_TYPE] = st.toolType
            m[StrokeKeys.DOWN_AT_MS] = st.downAtMs
            m[StrokeKeys.END] = st.end ?: StrokeEnds.OPEN_AT_END
            m[StrokeKeys.DT_MS] = st.dtMs
            m[StrokeKeys.X] = st.x
            m[StrokeKeys.Y] = st.y
            if (st.touchMajorPx.none { it.isNaN() }) m[StrokeKeys.TOUCH_MAJOR_PX] = st.touchMajorPx else touchMajorMissing = true
            strokeList.add(m)
        }
        map[Keys.STROKES] = strokeList
        if (touchMajorMissing) absent[StrokeKeys.TOUCH_MAJOR_ABSENT_KEY] = AbsentReasons.NOT_REPORTED_BY_DEVICE
        put(map, absent, Keys.UNBUFFERED_DISPATCH, unbufferedDispatch ?: Reading.Absent(AbsentReasons.NOT_REPORTED_BY_DEVICE))
        map[Keys.RESAMPLED_FLAG_STATE] = TouchGridContract.ResampledFlagStates.stateFor(env.sdkInt)
        if (endReason != EndReasons.AUTO_COMPLETE) map[Keys.DISPLAYED_SUSPECTS] = suspects.map { it.toWire() }
        map[Keys.PATH_CAP] = linkedMapOf<String, Any?>(
            PathCapKeys.MAX_POINTS to maxPoints,
            PathCapKeys.REACHED to (recordedPoints >= maxPoints),
            PathCapKeys.POINTS_DROPPED to pointsDropped
        )

        if (absent.isNotEmpty()) map[Keys.ABSENT_FIELD_REASONS] = absent
        return map
    }

    private fun <T> put(map: MutableMap<String, Any?>, absent: MutableMap<String, String>, key: String, reading: Reading<T>) =
        put(map, absent, key, reading) { it }

    /** A value goes in under [key]; an absence leaves the key out and its reason in `absentFieldReasons` (N71-01) — never a default. */
    private fun <T> put(
        map: MutableMap<String, Any?>,
        absent: MutableMap<String, String>,
        key: String,
        reading: Reading<T>,
        wire: (T) -> Any?
    ) {
        when (reading) {
            is Reading.Value -> map[key] = wire(reading.value)
            is Reading.Absent -> absent[key] = reading.reason
        }
    }

    /** The answer a run that ended for [endReason] records; a dismissal records none of its own. */
    fun statusFor(endReason: String): String? = when (endReason) {
        EndReasons.AUTO_COMPLETE -> ManualStatuses.PASS
        EndReasons.USER_MARKED_NOT_WORKING -> ManualStatuses.FAIL
        EndReasons.USER_MARKED_NOT_TESTED -> ManualStatuses.NOT_TESTED
        else -> null
    }
}
