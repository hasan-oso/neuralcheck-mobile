package net.neuralcheck.presentation.screen

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.util.DisplayMetrics
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import net.neuralcheck.domain.manual.PointerSample
import net.neuralcheck.domain.manual.PxRect
import net.neuralcheck.domain.manual.Reading
import net.neuralcheck.domain.manual.RoundedCornerRadii
import net.neuralcheck.domain.manual.TouchGridContract
import net.neuralcheck.domain.manual.TouchGridContract.StrokeEnds
import net.neuralcheck.domain.manual.TouchGridEnvironment
import net.neuralcheck.domain.manual.TouchGridReadings
import net.neuralcheck.domain.manual.TouchGridRecorder
import net.neuralcheck.domain.manual.TouchGridSurface

/**
 * What the platform says about the screen and the window, as readings (design §4.2, contract 7-ي ف-02): the one place the Android calls are
 * made; the decisions (an API level below a call's, a failed read) are [TouchGridReadings]'s and are tested there.
 *
 * Every read is its own try: one refusal never costs the others. The numbers go out raw — the density is chosen from them by
 * `DensitySelector` and the server can redo it (rule 20).
 */
internal object TouchGridEnvironmentReader {

    @SuppressLint("NewApi") // every call above API 28 is behind TouchGridReadings.sinceApi / roundedCorners, which test Build.VERSION.SDK_INT first
    fun read(view: View): TouchGridEnvironment {
        val sdk = Build.VERSION.SDK_INT
        val metrics = view.resources.displayMetrics
        fun display() = view.display ?: throw IllegalStateException("the view has no display (it is not attached to a window)")
        fun insets() = ViewCompat.getRootWindowInsets(view) ?: throw IllegalStateException("the view has no root window insets yet")
        fun androidx.core.graphics.Insets.toRect() = PxRect(left, top, right, bottom)
        fun Rect.toPx() = PxRect(left, top, right, bottom)

        val realSize: Reading<Pair<Int, Int>> = TouchGridReadings.attempt {
            val m = DisplayMetrics()
            @Suppress("DEPRECATION") // Display.getRealMetrics: the display's real size, which the window metrics of a dialog do not give
            display().getRealMetrics(m)
            m.widthPixels to m.heightPixels
        }
        val modeSize: Reading<Pair<Int, Int>> = TouchGridReadings.attempt {
            val mode = display().mode ?: throw IllegalStateException("the display has no current mode")
            mode.physicalWidth to mode.physicalHeight
        }

        return TouchGridEnvironment(
            xdpi = metrics.xdpi,
            ydpi = metrics.ydpi,
            densityDpi = metrics.densityDpi,
            displayWidthPx = realSize.map { it.first },
            displayHeightPx = realSize.map { it.second },
            modePhysicalWidthPx = modeSize.map { it.first },
            modePhysicalHeightPx = modeSize.map { it.second },
            gestureInsetsPx = TouchGridReadings.sinceApi(sdk, TouchGridReadings.FIRST_API_GESTURES) {
                insets().getInsets(WindowInsetsCompat.Type.systemGestures()).toRect()
            },
            mandatoryGestureInsetsPx = TouchGridReadings.sinceApi(sdk, TouchGridReadings.FIRST_API_GESTURES) {
                insets().getInsets(WindowInsetsCompat.Type.mandatorySystemGestures()).toRect()
            },
            exclusionRectsPx = TouchGridReadings.sinceApi(sdk, TouchGridReadings.FIRST_API_GESTURES) {
                view.systemGestureExclusionRects.map { it.toPx() }
            },
            systemBarsHidden = TouchGridReadings.attempt { !insets().isVisible(WindowInsetsCompat.Type.systemBars()) },
            cutoutRectsPx = TouchGridReadings.attempt {
                (insets().displayCutout?.boundingRects ?: emptyList()).map { it.toPx() }   // no cut-out: [] — an answer, not an absence
            },
            roundedCorners = TouchGridReadings.roundedCorners(sdk) {
                val wi = insets().toWindowInsets() ?: throw IllegalStateException("the root window insets have no platform form")
                fun radius(position: Int) = wi.getRoundedCorner(position)?.radius ?: 0   // a corner with no rounding is 0 — defined, not absent
                RoundedCornerRadii(
                    topLeft = radius(android.view.RoundedCorner.POSITION_TOP_LEFT),
                    topRight = radius(android.view.RoundedCorner.POSITION_TOP_RIGHT),
                    bottomRight = radius(android.view.RoundedCorner.POSITION_BOTTOM_RIGHT),
                    bottomLeft = radius(android.view.RoundedCorner.POSITION_BOTTOM_LEFT)
                )
            },
            sdkInt = sdk
        )
    }

    private fun <T, R> Reading<T>.map(f: (T) -> R): Reading<R> = when (this) {
        is Reading.Value -> Reading.Value(f(value))
        is Reading.Absent -> this
    }
}

/**
 * The M-02 touch surface (design §2.1): a plain `View`, because it needs what Compose's pointer input does not give — every historical sample
 * of a MOVE, the event times, the tool type, the contact's major axis, `FLAG_CANCELED` and unbuffered dispatch.
 *
 * It converts `MotionEvent`s into [PointerSample]s for the [TouchGridRecorder] (which holds the run) and draws the grid the recorder laid out:
 * the cells of the «frame + X» pattern — a lit cell green, an unlit one dark, a cut-out cell of the pattern outlined — and nothing in the gaps between them (the
 * dark background), then the marks the technician is shown as rings. No text is drawn here and none is new. The layout is (re)computed from the platform's answers on every size or insets change until the first finger lands, and once more
 * right before it ([TouchGridRecorder.canReconfigure]); after that the grid does not move.
 *
 * It fills the WHOLE dialog window (user decision 2026-10-21; `TouchGridRunContent`): nothing is laid out above it or below it. The GRID occupies the window below an empty strip
 * (`TouchGridLayout.gridTopPx`, user order 2026-10-21: the system's own touch test starts under such a strip), and the recorder's touch surface is the grid's region alone: every
 * sample is handed to it with the strip taken off `y`, so a touch in the strip arrives as a negative `y` — recorded raw, in no cell. Touches are consumed here (return
 * true); the Compose text drawn over it (title, hint, live count) stands in the strip and has no pointer input, so a touch on it comes here, and the technician's two buttons are the
 * only Compose elements that take a touch — they stand on cells the pattern leaves out, never on a required one (`TouchGridOverlay`).
 */
internal class TouchGridSurfaceView(
    context: Context,
    private val recorder: TouchGridRecorder,
    /** Called after every event and every layout: the dialog reads the counters and checks for completion. */
    private val onProgress: () -> Unit
) : View(context) {

    private val litPaint = Paint().apply { color = Color.rgb(0x22, 0xC5, 0x5E); style = Paint.Style.FILL }                 // StatusPass
    private val darkPaint = Paint().apply { color = Color.rgb(0x33, 0x33, 0x33); style = Paint.Style.FILL }               // dark grey
    private val cutoutPaint = Paint().apply {
        color = Color.argb(0xB0, 0xFF, 0xFF, 0xFF); style = Paint.Style.STROKE; strokeWidth = 2f * resources.displayMetrics.density
    }
    private val ringPaint = Paint().apply {
        color = Color.rgb(0xEF, 0x44, 0x44); style = Paint.Style.STROKE; strokeWidth = 2f * resources.displayMetrics.density; isAntiAlias = true
    }
    private val edgeRingPaint = Paint().apply {
        color = Color.rgb(0xF5, 0x9E, 0x0B); style = Paint.Style.STROKE; strokeWidth = 2f * resources.displayMetrics.density; isAntiAlias = true
    }
    private val cell = RectF()

    init {
        setBackgroundColor(Color.BLACK)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        requestApplyInsets()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        relayout()
    }

    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        val result = super.onApplyWindowInsets(insets)
        relayout()
        return result
    }

    /** Asks the platform to leave the whole surface to this app's gestures (design §4.2), then lays the grid out on what the platform answers. */
    @SuppressLint("NewApi") // behind the SDK_INT test
    private fun relayout() {
        if (width <= 0 || height <= 0 || !recorder.canReconfigure) return
        if (Build.VERSION.SDK_INT >= TouchGridReadings.FIRST_API_GESTURES) {
            try { systemGestureExclusionRects = listOf(Rect(0, 0, width, height)) } catch (e: Exception) { /* read back below: what the platform reports is what is recorded */ }
        }
        val origin = IntArray(2)
        getLocationInWindow(origin)
        recorder.configure(TouchGridSurface(width, height, origin[0], origin[1]), TouchGridEnvironmentReader.read(this))
        invalidate()
        onProgress()
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        var lit = false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (recorder.canReconfigure) relayout()    // the platform is asked once more right before the first finger
                recorder.setUnbufferedDispatch(
                    TouchGridReadings.attempt { requestUnbufferedDispatch(e); true }
                )
                lit = down(e, 0)
            }
            MotionEvent.ACTION_POINTER_DOWN -> lit = down(e, e.actionIndex)
            MotionEvent.ACTION_MOVE -> for (i in 0 until e.pointerCount) lit = moved(e, i) or lit
            MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_UP -> lit = ended(e, e.actionIndex, canceled = false)
            MotionEvent.ACTION_CANCEL -> for (i in e.pointerCount - 1 downTo 0) lit = ended(e, i, canceled = true) or lit
        }
        if (lit) invalidate()
        onProgress()
        return true
    }

    /** The strip above the grid, px: what is taken off every `y` so the sample is in the recorder's surface frame. */
    private fun gridTop(): Float = (recorder.layout?.gridTopPx ?: 0).toFloat()

    private fun sample(e: MotionEvent, i: Int) = PointerSample(e.eventTime, e.getX(i), e.getY(i) - gridTop(), e.getTouchMajor(i))

    private fun down(e: MotionEvent, i: Int): Boolean = recorder.pointerDown(e.getPointerId(i), e.getToolType(i), sample(e, i))

    /** History first (the platform groups samples into one MOVE; design §2.1), the current sample last — all of them, none dropped. */
    private fun moved(e: MotionEvent, i: Int): Boolean {
        val samples = ArrayList<PointerSample>(e.historySize + 1)
        val top = gridTop()
        for (h in 0 until e.historySize) {
            samples.add(PointerSample(e.getHistoricalEventTime(h), e.getHistoricalX(i, h), e.getHistoricalY(i, h) - top, e.getHistoricalTouchMajor(i, h)))
        }
        samples.add(sample(e, i))
        return recorder.pointerMoved(e.getPointerId(i), samples)
    }

    private fun ended(e: MotionEvent, i: Int, canceled: Boolean): Boolean {
        // FLAG_CANCELED (API 33): the platform says the touch was not meant (a palm) — recorded apart from a plain cancel (design §3.3)
        val flagged = (e.flags and MotionEvent.FLAG_CANCELED) != 0
        val end = when {
            flagged -> StrokeEnds.CANCEL_FLAGGED
            canceled -> StrokeEnds.CANCEL
            else -> StrokeEnds.UP
        }
        return recorder.pointerEnded(e.getPointerId(i), end, if (canceled) null else sample(e, i))
    }

    override fun onDraw(canvas: Canvas) {
        val l = recorder.layout ?: return
        val gap = 2f
        val cw = l.cellWidthPx.toFloat()
        val ch = l.cellHeightPx.toFloat()
        val top = l.gridTopPx.toFloat()        // the empty strip above the grid: nothing is drawn in it here (the title and the count are Compose text over it)
        for (r in 0 until l.rows) {
            for (c in 0 until l.cols) {
                val index = r * l.cols + c
                cell.set(c * cw + gap / 2, top + r * ch + gap / 2, (c + 1) * cw - gap / 2, top + (r + 1) * ch - gap / 2)
                when {
                    // the frame and the diagonals are the cells; what lies between them is the dark background, no cell at all (user decision 2026-10-21, «إطار + X»)
                    l.isCutout(index) -> if (l.isInPattern(index)) canvas.drawRect(cell, cutoutPaint)
                    !l.isRequired(index) -> Unit
                    recorder.isTouched(index) -> canvas.drawRect(cell, litPaint)
                    else -> canvas.drawRect(cell, darkPaint)
                }
            }
        }
        // the marks (design §3.4): a ring, about 4 mm across, at the middle of the segment — red for a jump, amber for an edge exit
        val radius = (2.0 * l.density.pxPerMm).toFloat()
        for (s in recorder.displayedSuspects) {
            val paint = if (s.kind == TouchGridContract.SuspectKinds.EDGE_EXIT) edgeRingPaint else ringPaint
            canvas.drawCircle((s.x0 + s.x1) / 2f, top + (s.y0 + s.y1) / 2f, radius, paint)
        }
    }
}
