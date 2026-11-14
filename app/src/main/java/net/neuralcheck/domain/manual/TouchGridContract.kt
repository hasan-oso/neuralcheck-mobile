package net.neuralcheck.domain.manual

import net.neuralcheck.util.ReasonText

/**
 * fieldsVersion 71 (docs/FV68_CONTRACT.md section 7-ي; order A-M02 of 2026-10-18; design
 * `evidence/m02-touch-grid/design-m02-touch-grid-2026-10-13.md`, cited below as "design §n"): the M-02 grid's constants and every
 * enumerated wire value — ONE source (rule 24), read by [TouchGridLayout], [TouchGridRecorder] and the surface view, and spelled out in
 * the tests so a rename fails.
 *
 * Pure Kotlin, no Android. The app records and counts; it never judges a device (rule 20): the 95/80-style thresholds stay on the server.
 * The detector thresholds below only decide what the technician is SHOWN on the screen (`displayedSuspects` is a display log, design §6.3).
 *
 * Marks: [ح] = the user's decision or an approved contract line; [ت] = design §n's own figure, tagged «اجتهاد» there; [ق] = this build's
 * technical decision (reported with the commit).
 */
object TouchGridContract {

    // ───────────────────────── geometry (design §1) ─────────────────────────

    /**
     * The grid cell, in millimetres. 4 mm since the user's decision of 2026-10-21 (his order: M-02 takes the shape of the system's own touch test, about 18 columns on a phone —
     * 72 mm / 4 mm — the cells square, their number computed from the screen, no count fixed in the code). It was 6 mm [ح: M7(أ) 2026-10-18 — «صغّرها مع حدّ أدنى معقول للمس
     * بالإصبع» — design M-02's 6 mm, design:72-73: a cell is smaller than the finger pad, whose own resolution is about 5 mm]; the new figure is BELOW that resolution and is the
     * user's order, not a finding of ours: whether a finger on a real phone lights every 4 mm cell of the frame is a field-round item (rule 19), not measured here.
     */
    const val CELL_TARGET_MM: Double = 4.0

    /**
     * The empty strip above the grid, in cells (the user's order of 2026-10-21: the system's test starts under an empty strip of about 5% of the height, and no text stands on the
     * cells): the grid's cells are exactly square (`width / cols` on both axes), the rows are as many whole cells as fit under this strip, and the strip is what is left over
     * — at least this many cells high (2 x 4 mm = 8 mm = about 5% of a tall phone), and less than one cell more. The title and the live count stand in it.
     */
    const val TOP_STRIP_CELLS: Int = 2

    /** 25.4 mm per inch: the dpi-to-pixels-per-millimetre divisor. */
    const val MM_PER_INCH: Double = 25.4

    /** The narrowest and widest a phone's screen can be, mm [ت: design §1.2 step 3]: a candidate density outside it is not believed. */
    const val PHONE_WIDTH_MIN_MM: Double = 55.0
    const val PHONE_WIDTH_MAX_MM: Double = 85.0

    /** `|xdpi - ydpi|` may not exceed this share of the larger [ت: design §1.2 step 3]. */
    const val DPI_AXES_MAX_SPREAD: Double = 0.05

    /** A memory guard, never reached by a real phone (200 cells x 6 mm = 1.2 m): the most cells one axis can have [ق]. */
    const val MAX_CELLS_PER_AXIS: Int = 200

    // ───────────────────────── the path (design §6.3, E18) ─────────────────────────

    /**
     * `pathCap.maxPoints`: the most points recorded over ALL strokes of one run [ح: E18, «سقف 15,000 عيّنة معلَن», user approval 2026-10-15].
     * Past it, recording stops (the run goes on, the cells still count, the stroke in progress is closed where it was stopped) and `pathCap`
     * says so — never a silent cut.
     */
    const val PATH_MAX_POINTS: Int = 15_000

    // ───────────────────────── what is shown (design §3) — display only ─────────────────────────

    /** The finger counts as moving when its speed over the last [MOVING_STEPS] steps reaches this, mm/s [ت: design §3.1 `V_MIN`]. */
    const val V_MIN_MM_S: Double = 20.0

    /** The smallest step that can be a jump, mm [ت: design §3.1 `J_MIN`]. */
    const val J_MIN_MM: Double = 1.5

    /** A step is a jump when it exceeds this many times the median of the last [STEP_HISTORY] steps [ت: design §3.1]. */
    const val JUMP_MEDIAN_FACTOR: Double = 3.0

    /** The steps looked back on for the median [ت: design §3.1]. */
    const val STEP_HISTORY: Int = 8

    /** The steps whose speed decides "moving" [ت: design §3.1: «آخر 3 خطوات»]. */
    const val MOVING_STEPS: Int = 3

    /** A jump with a time gap longer than max([GAP_MIN_MS], [GAP_MEDIAN_FACTOR] x the median interval) is a `gap_jump`; otherwise `coord_jump` [ت: design §3.1]. */
    const val GAP_MIN_MS: Long = 20L
    const val GAP_MEDIAN_FACTOR: Double = 3.0

    /** A stroke that ends this close to an edge, heading to it, ends there [ت: design §3.3 `EDGE_MARGIN`]. */
    const val EDGE_MARGIN_MM: Double = 5.0

    // ───────────────────────── enumerated wire values ─────────────────────────

    /**
     * `gridPattern` — the shape of the set of cells a run REQUIRES [ح: user decision 2026-10-21 «M-02: استبدل الشبكة الحالية باختبار لمس من نوع «إطار + X»»; the two
     * values and the key are the coordinator's wire contract `wire-m02-frame-x-2026-10-21.md`, copied into `docs/FV68_CONTRACT.md`, section 7-ي, paragraph of 2026-10-21].
     * [FRAME_AND_DIAGONALS] is what this build writes, always ([TouchGridPattern]); [ALL_CELLS] is the older shape (every cell not cut out) — it stays a contract value so the
     * server can say what a build that does not declare the key (`NOT_DECLARED`) was running, and no code of the app produces it any more.
     */
    object GridPatterns {
        const val ALL_CELLS = "ALL_CELLS"
        const val FRAME_AND_DIAGONALS = "FRAME_AND_DIAGONALS"
        val ALL: List<String> = listOf(ALL_CELLS, FRAME_AND_DIAGONALS)
    }

    /** `densitySource` [ح: design §1.2, M2:52-54; the names are the design's]. */
    object DensitySources {
        const val XDPI = "XDPI"
        const val XDPI_MODE_SCALED = "XDPI_MODE_SCALED"
        const val DENSITY_DPI_FALLBACK = "DENSITY_DPI_FALLBACK"
        val ALL: List<String> = listOf(XDPI, XDPI_MODE_SCALED, DENSITY_DPI_FALLBACK)
    }

    /**
     * The elements of `densityCheck` — the conditions that failed when [DensitySources.DENSITY_DPI_FALLBACK] was used; `[]` otherwise [ق: the
     * contract (7-ي ف-02) leaves the names to the build order and the design (§1.2) names only the conditions in words — these four spell them].
     */
    object DensityChecks {
        const val XDPI_UNUSABLE = "xdpi_or_ydpi_not_positive"
        const val DPI_AXES_DIFFER = "xdpi_ydpi_differ_over_5pct"
        const val WIDTH_OUT_OF_RANGE = "phone_width_outside_55_85mm"
        const val BOTH_CANDIDATES_PLAUSIBLE = "both_candidates_plausible"
        const val DISPLAY_WIDTH_UNKNOWN = "display_width_unknown"
        val ALL: List<String> = listOf(XDPI_UNUSABLE, DPI_AXES_DIFFER, WIDTH_OUT_OF_RANGE, BOTH_CANDIDATES_PLAUSIBLE, DISPLAY_WIDTH_UNKNOWN)
    }

    /** `strokes[].toolType` [ح: contract ف-03 — touch · stylus · mouse · eraser · unknown; any other value is written `unknown:<n>`]. */
    object ToolTypes {
        const val TOUCH = "touch"
        const val STYLUS = "stylus"
        const val MOUSE = "mouse"
        const val ERASER = "eraser"
        const val UNKNOWN = "unknown"
        val ALL: List<String> = listOf(TOUCH, STYLUS, MOUSE, ERASER, UNKNOWN)

        // MotionEvent.TOOL_TYPE_UNKNOWN / FINGER / STYLUS / MOUSE / ERASER (0..4), spelled here so this file stays pure Kotlin.
        fun nameOf(androidToolType: Int): String = when (androidToolType) {
            0 -> UNKNOWN
            1 -> TOUCH
            2 -> STYLUS
            3 -> MOUSE
            4 -> ERASER
            else -> "$UNKNOWN:$androidToolType"
        }
    }

    /** `strokes[].end` [ح: contract ف-03]. `open_at_end`: the finger is still down when the run ends. */
    object StrokeEnds {
        const val UP = "up"
        const val CANCEL = "cancel"
        const val CANCEL_FLAGGED = "cancel_flagged"
        const val OPEN_AT_END = "open_at_end"
        val ALL: List<String> = listOf(UP, CANCEL, CANCEL_FLAGGED, OPEN_AT_END)
    }

    /** `displayedSuspects[].kind` [ح: contract ف-03, E19]. */
    object SuspectKinds {
        const val GAP_JUMP = "gap_jump"
        const val COORD_JUMP = "coord_jump"
        const val EDGE_EXIT = "edge_exit"
        val ALL: List<String> = listOf(GAP_JUMP, COORD_JUMP, EDGE_EXIT)
    }

    /**
     * `absentFieldReasons` values (N71-01, approved): `api_below_<n>` · `read_failed:<error text>` (the message text, never the class — rule 21,
     * capped by [ReasonText]) · `not_reported_by_device`.
     */
    object AbsentReasons {
        const val NOT_REPORTED_BY_DEVICE = "not_reported_by_device"
        const val API_BELOW_PREFIX = "api_below_"
        const val READ_FAILED_PREFIX = "read_failed:"
        fun apiBelow(api: Int): String = "$API_BELOW_PREFIX$api"
        fun readFailed(t: Throwable): String = READ_FAILED_PREFIX + ReasonText.of(t)
        fun readFailed(text: String): String = READ_FAILED_PREFIX + ReasonText.cap(text)
    }

    /** `roundedCornersState` when the radii are not read [ح: design §4.2 / contract ف-02: `not_available_api` on API < 31; else `read_failed:<text>`]. */
    object RoundedCornersStates {
        const val NOT_AVAILABLE_API = "not_available_api"
        fun readFailed(t: Throwable): String = AbsentReasons.readFailed(t)
    }

    /**
     * `resampledFlagState` — contract ف-03 says «قيمه لا تصلني» (the values are not in the sources) [ق, PENDING: مواصفة غائبة]: two values, so the
     * key is never absent (an absence is an anomaly at stamp >= 71): `not_available_api` below API 35 (design §2.1: `PointerCoords.isResampled`
     * is API 35 and «يُسجَّل ذلك حالة صريحة»), and `not_read_by_app` from API 35 (this build does not read it per sample).
     */
    object ResampledFlagStates {
        const val NOT_AVAILABLE_API = "not_available_api"
        const val NOT_READ_BY_APP = "not_read_by_app"
        const val FIRST_API_WITH_FLAG = 35
        fun stateFor(sdkInt: Int): String = if (sdkInt < FIRST_API_WITH_FLAG) NOT_AVAILABLE_API else NOT_READ_BY_APP
    }

    // ───────────────────────── key names, below the top level ─────────────────────────

    /** The keys of one `strokes[]` element [ح: contract ف-03]. */
    object StrokeKeys {
        const val POINTER_ID = "pointerId"
        const val TOOL_TYPE = "toolType"
        const val DOWN_AT_MS = "downAtMs"
        const val END = "end"
        const val DT_MS = "dtMs"
        const val X = "x"
        const val Y = "y"
        const val TOUCH_MAJOR_PX = "touchMajorPx"

        /** The key under which a missing per-stroke `touchMajorPx` is explained in `absentFieldReasons` [ح: contract ف-03]. */
        const val TOUCH_MAJOR_ABSENT_KEY = "strokes.touchMajorPx"
    }

    /** The keys of `displayedSuspects[]` elements [ح: N71-03]. */
    object SuspectKeys {
        const val KIND = "kind"
        const val STROKE_INDEX = "strokeIndex"
        const val FROM_POINT_INDEX = "fromPointIndex"
        const val TO_POINT_INDEX = "toPointIndex"
    }

    /** The keys of `pathCap` [ح: N71-02]. */
    object PathCapKeys {
        const val MAX_POINTS = "maxPoints"
        const val REACHED = "reached"
        const val POINTS_DROPPED = "pointsDropped"
    }

    /** The keys of a rectangle / inset set [ح: contract ف-02: `{left, top, right, bottom}`]. */
    object RectKeys {
        const val LEFT = "left"
        const val TOP = "top"
        const val RIGHT = "right"
        const val BOTTOM = "bottom"
    }

    /** The keys of `roundedCornerRadiiPx` [ح: contract ف-02]. */
    object CornerKeys {
        const val TOP_LEFT = "topLeft"
        const val TOP_RIGHT = "topRight"
        const val BOTTOM_RIGHT = "bottomRight"
        const val BOTTOM_LEFT = "bottomLeft"
    }
}

/** A rectangle, or the four sides of an inset, in pixels. */
data class PxRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    fun toWire(): Map<String, Any?> = linkedMapOf(
        TouchGridContract.RectKeys.LEFT to left,
        TouchGridContract.RectKeys.TOP to top,
        TouchGridContract.RectKeys.RIGHT to right,
        TouchGridContract.RectKeys.BOTTOM to bottom
    )
}

/** The four rounded-corner radii of the window, px (a corner with no rounding is 0 — a defined answer, not an absence). */
data class RoundedCornerRadii(val topLeft: Int, val topRight: Int, val bottomRight: Int, val bottomLeft: Int) {
    fun toWire(): Map<String, Any?> = linkedMapOf(
        TouchGridContract.CornerKeys.TOP_LEFT to topLeft,
        TouchGridContract.CornerKeys.TOP_RIGHT to topRight,
        TouchGridContract.CornerKeys.BOTTOM_RIGHT to bottomRight,
        TouchGridContract.CornerKeys.BOTTOM_LEFT to bottomLeft
    )
}

/** One thing the platform was asked: its answer, or why there is none (rule 22 — an absence is a stated state, never a default). */
sealed interface Reading<out T> {
    data class Value<out T>(val value: T) : Reading<T>
    data class Absent(val reason: String) : Reading<Nothing>
}

/** `roundedCornerRadiiPx` OR `roundedCornersState` — exactly one of them is always written (contract ف-02). */
sealed interface RoundedCorners {
    data class Radii(val radii: RoundedCornerRadii) : RoundedCorners
    data class State(val state: String) : RoundedCorners
}
