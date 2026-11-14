package net.neuralcheck.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import net.neuralcheck.R
import net.neuralcheck.domain.manual.TouchGridLayout
import net.neuralcheck.domain.manual.TouchGridOverlay
import net.neuralcheck.domain.manual.TouchGridRecorder
import net.neuralcheck.presentation.theme.CardDark
import net.neuralcheck.presentation.theme.StatusFail
import net.neuralcheck.util.NumberFormat
import kotlin.math.roundToInt

/** The tags the rendered tests find the run's parts by (one source, rule 24). */
internal object TouchGridRunTags {
    const val SURFACE = "touch_grid_surface"
    const val TITLE = "touch_grid_title"
    const val HINT = "touch_grid_hint"
    const val COUNTER = "touch_grid_counter"
    const val NOT_WORKING = "touch_grid_not_working"
    const val NOT_TESTED = "touch_grid_not_tested"
}

/**
 * What the M-02 dialog draws, whatever window it is in: the «frame + X» grid over the WHOLE of it (cells of the frame and of the two diagonals; between them the dark
 * background), the title, the live count and the hint in an EMPTY STRIP at the top, and — [buttonsVisible] — the technician's two buttons.
 *
 * The decision of 2026-10-21 (user: «الحوار بملء الشاشة، والشبكة تغطي كامل مساحة اللمس المتاحة للتطبيق», then «M-02: استبدل الشبكة الحالية باختبار لمس من نوع «إطار + X»», then, to
 * match the system's touch test, «لا نصّ فوق الخلايا. إن احتاج العنوان والعدّاد مكاناً فليكن في الشريط العلوي الفارغ»; the technical choices are the orders'): the surface fills the
 * content (`fillMaxSize`, nothing above or below it) — the window — and the grid is laid out in it under an empty strip ([TouchGridLayout.gridTopPx]); the text stands in the strip,
 * where no cell is, at the box [TouchGridOverlay.textBox] gives (clear of the camera) and has NO pointer input at all (a touch on it goes to the surface under it); the buttons stand
 * on the cells the pattern leaves out — the triangles inside the frame — at the places [TouchGridOverlay] decides, so no button's rectangle meets a required cell's; the only
 * touch-consuming parts are the two buttons. They appear after [TouchGridRecorder.TECHNICIAN_BUTTONS_AFTER_MS] (the caller's timer) and change nothing of the surface's rectangle or of
 * the grid's numbers: they are drawn over it.
 *
 * The title, the count and the two buttons' labels are the resources the dialog already had; the hint's wording is the user's proposal of 2026-10-21 (not yet approved by Hasan).
 *
 * @param touched the cells lit so far; @param total the required cells (both from the recorder, as the dialog read them after the last event)
 * @param onProgress called by the surface after every touch event and every layout: the dialog reads the counters and checks for completion
 */
@Composable
internal fun TouchGridRunContent(
    recorder: TouchGridRecorder,
    touched: Int,
    total: Int,
    buttonsVisible: Boolean,
    onProgress: () -> Unit,
    onNotWorking: () -> Unit,
    onNotTested: () -> Unit
) {
    val currentOnProgress by rememberUpdatedState(onProgress)
    // the grid the surface laid out, as state: the places of the text and of the buttons follow it (the surface lays out after the first composition, and again until the first touch)
    var layout by remember { mutableStateOf<TouchGridLayout?>(null) }
    // the text is drawn over cells that are dark grey or green: a shadow keeps it readable on both
    val shadow = Shadow(color = Color.Black, offset = Offset(0f, 0f), blurRadius = 8f)

    Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                factory = { ctx ->
                    TouchGridSurfaceView(ctx, recorder) {
                        val laid = recorder.layout
                        if (laid !== layout) layout = laid
                        currentOnProgress()
                    }
                },
                modifier = Modifier.fillMaxSize().testTag(TouchGridRunTags.SURFACE)
            )

            val shown = layout
            val placement = remember(shown) { shown?.let { TouchGridOverlay.place(it) } }
            if (shown != null) {
                TextBlock(box = placement?.text, touched = touched, total = total, shadow = shadow)
            }
            if (buttonsVisible) {
                TechnicianButtons(placement = placement, onNotWorking = onNotWorking, onNotTested = onNotTested)
            }
        }
    }
}

/**
 * The title, the live count and the hint: in [box] — the empty strip above the grid (or, when the window has none, a rectangle of cells the pattern leaves out) — and with no pointer input: a touch on it is a touch on the surface under it. With no
 * [box] (no strip and no free cell: a window no phone has) they stand at the top under the camera's cut-out, in a block 48 dp high.
 */
@Composable
private fun TextBlock(box: TouchGridOverlay.Box?, touched: Int, total: Int, shadow: Shadow) {
    val content: @Composable () -> Unit = {
        // the strip is a few millimetres high (two or three cells of 4 mm): two lines, each on ONE line (ellipsis, never a second line), and the user's font size does not enlarge
        // them here — a taller block would spill onto the frame's first row of cells
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = minOf(density.fontScale, 1f))) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center, modifier = Modifier.fillMaxSize()) {
                // the title first (at the start side), the count after it: when the stretch is narrow the title gives way, the count never does
                Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.manual_touch_title),
                        style = MaterialTheme.typography.titleSmall.copy(shadow = shadow, fontWeight = FontWeight.Bold),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false).testTag(TouchGridRunTags.TITLE)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = NumberFormat.toLatin(stringResource(R.string.manual_touch_result, touched, total)),
                        style = MaterialTheme.typography.titleSmall.copy(shadow = shadow),
                        color = Color.White,
                        maxLines = 1,
                        modifier = Modifier.testTag(TouchGridRunTags.COUNTER)
                    )
                }
                Text(
                    text = stringResource(R.string.manual_touch_hint_v2),
                    style = MaterialTheme.typography.bodySmall.copy(shadow = shadow),
                    color = Color.White.copy(alpha = 0.85f),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().testTag(TouchGridRunTags.HINT)
                )
            }
        }
    }
    if (box == null) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.displayCutout)
                .padding(top = 8.dp, start = 16.dp, end = 16.dp)
                .height(48.dp)
        ) { content() }
        return
    }
    Layout(content = content, modifier = Modifier.fillMaxSize()) { measurables, constraints ->
        val block = measurables.single().measure(Constraints.fixed(box.width.roundToInt(), box.height.roundToInt()))
        layout(constraints.maxWidth, constraints.maxHeight) { block.place(box.left.roundToInt(), box.top.roundToInt()) }
    }
}

/**
 * The technician's two buttons, each at the pixel box [TouchGridOverlay.place] gives (or along the bottom when the grid has no place for them), over the whole window. They draw
 * nothing else and take no other touch: the layer is as large as the window but only its two children take events. Plain boxes with `clickable` — a Material button would widen
 * its touch area to 48 dp around a smaller box, past the geometry the cells were kept free by.
 */
@Composable
private fun TechnicianButtons(placement: TouchGridOverlay.Placement?, onNotWorking: () -> Unit, onNotTested: () -> Unit) {
    val density = LocalDensity.current
    val fallbackHeightPx = with(density) { 40.dp.toPx() }.toDouble()
    Layout(
        content = {
            TechnicianButton(
                text = stringResource(R.string.manual_not_works),
                color = StatusFail,
                tag = TouchGridRunTags.NOT_WORKING,
                onClick = onNotWorking
            )
            TechnicianButton(
                text = stringResource(R.string.manual_not_tested_btn),
                color = Color.White.copy(alpha = 0.7f),
                tag = TouchGridRunTags.NOT_TESTED,
                onClick = onNotTested
            )
        },
        modifier = Modifier.fillMaxSize()
    ) { measurables, constraints ->
        val geometry = placement?.buttons ?: TouchGridOverlay.buttonRowWithoutGrid(constraints.maxWidth, constraints.maxHeight, fallbackHeightPx)
        // the first button (لا يعمل) stands at the start side: the right in a right-to-left language, as the old bar's row did
        val boxes = if (layoutDirection == LayoutDirection.Rtl) listOf(geometry.right, geometry.left) else listOf(geometry.left, geometry.right)
        val placeables = measurables.mapIndexed { i, m ->
            m.measure(Constraints.fixed(boxes[i].width.roundToInt(), boxes[i].height.roundToInt()))
        }
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeables.forEachIndexed { i, p -> p.place(boxes[i].left.roundToInt(), boxes[i].top.roundToInt()) }
        }
    }
}

@Composable
private fun TechnicianButton(text: String, color: Color, tag: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .testTag(tag)
            .clip(RoundedCornerShape(8.dp))
            .background(CardDark)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = color, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}
