package net.neuralcheck.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.neuralcheck.R

/** The colours of M-01's run, in the order the technician sees them (one source: the dialog draws them, the tests read them, rule 24). */
internal object DeadPixelCycle {
    val COLORS: List<Color> = listOf(Color.Red, Color.Green, Color.Blue, Color.White, Color.Black)
}

/** The tags the rendered tests find the run's parts by (one source, rule 24). */
internal object DeadPixelRunTags {
    const val SURFACE = "dead_pixel_surface"
    const val HINT = "dead_pixel_hint"
}

/**
 * What M-01's colour phase draws, whatever window it is in: ONE colour over the WHOLE of it, edge to edge — [DeadPixelCycle.COLORS] at [colorIndex] — and the hint text
 * («اضغط للون التالي») over the colour at the bottom. A tap anywhere advances the run (the dialog's [onTap]).
 *
 * The window is [ImmersiveFullScreenDialog]'s: the whole display (user decision 2026-10-21, «نعم، أصلح M-01 بالمضيف الجديد»). Until then the colour was in Compose's
 * `Dialog(DialogProperties(usePlatformDefaultWidth = false))`, whose window was sized to the content measured without the system's decorations — so the colour stopped short
 * of the display's edges, which is where a dead pixel is looked for (ImmersiveFullScreenDialog's note carries the cause). This composable is what the dialog had inside that
 * window, unchanged: the colour fills its box (`fillMaxSize`, no padding, no inset), the hint is a plain text with no pointer input (a tap on it is a tap on the colour under it —
 * the only thing that takes the touch is the colour's own tap), and the colours, their order and the hint are the old ones. No text is new.
 */
@Composable
internal fun DeadPixelRunContent(colorIndex: Int, onTap: () -> Unit) {
    val color = DeadPixelCycle.COLORS[colorIndex]
    val currentOnTap by rememberUpdatedState(onTap)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag(DeadPixelRunTags.SURFACE)
            .background(color)
            .pointerInput(colorIndex) {
                detectTapGestures { currentOnTap() }
            }
    ) {
        val isBright = color == Color.White || color == Color.Green
        Text(
            text = stringResource(R.string.manual_next_color),
            color = if (isBright) Color.Black else Color.White,
            fontSize = 14.sp,
            modifier = Modifier.align(Alignment.BottomCenter).padding(32.dp).testTag(DeadPixelRunTags.HINT)
        )
    }
}
