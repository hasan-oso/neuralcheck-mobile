package net.neuralcheck.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.neuralcheck.R
import net.neuralcheck.presentation.theme.NeuralGreen
import net.neuralcheck.presentation.theme.TextSecondary

/**
 * What the manual-test row and its layout test (ManualUnavailableLineLayoutTest, batch 2) agree on about the «unavailable» line. One source (rule 24): the row reads
 * the gap and the tags here, and nothing else holds those literals.
 */
internal object ManualUnavailableLineSpec {

    /**
     * The least space, in dp, between the chip row and the line (user decision 2026-10-15, station-2 build: «بمسافة لا تقلّ عن 16dp
     * عن الصفّ»). The row puts a spacer of exactly this height between the two — layout bounds, so the visible gap is at least this.
     */
    const val MIN_GAP_FROM_CHIPS_DP: Int = 16

    /** Test tag of the chip row (the `FlowRow` of the three answer chips). */
    const val TAG_CHIPS: String = "manual_row_chips"

    /** Test tag of the line. */
    const val TAG_LINE: String = "manual_unavailable_line"
}

/**
 * The answer `unavailable` as its OWN small line under the chip row — not a fourth chip in the row (user decision 2026-10-15, station-2
 * build: «تخرج من صفّ الشرائح السريعة إلى سطر منفصل تحتها، أصغر»). Its text is `manual_mark_unavailable`, the same single string the five
 * dialogs' buttons read (rule 24); no other resource and no literal copy.
 *
 * Behaviour (the decisions, in the order's words): one tap writes the answer — no confirmation dialog; when it is the row's answer it is
 * drawn SELECTED (the green container and border, the 🚫 mark, a bolder label, `selected` in the semantics); and it is cancelled by a tap on
 * ANY chip of the row, which writes that chip's answer in its place exactly as a chip does today (the row's `ManualRowActions.onChip` —
 * this line never un-selects itself, and a second tap on it writes the same answer again, as the fourth chip did).
 *
 * Smaller than a chip on purpose: the label is `labelSmall` (11 sp; the chips' is `labelLarge`, 14 sp) and the line is padded 4 dp
 * vertically (a chip is 32 dp tall). Its touch target is therefore its own size, below the 48 dp Material asks of a control — that is
 * the «أصغر» of the decision, not an oversight, and the report of this change names it.
 */
@Composable
internal fun ManualUnavailableLine(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(8.dp)
    val container = if (selected) NeuralGreen.copy(alpha = 0.18f) else Color.Transparent
    // the unselected line is outlined like an unselected chip (the theme's outline colour), so it reads as an option and not as a caption
    val borderColor = if (selected) NeuralGreen else MaterialTheme.colorScheme.outline
    val contentColor = if (selected) NeuralGreen else TextSecondary
    Row(
        modifier = modifier
            .clip(shape)
            .background(container)
            .border(width = 1.dp, color = borderColor, shape = shape)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selected) {
            Text("🚫", style = MaterialTheme.typography.labelSmall, color = contentColor)
            Spacer(Modifier.width(4.dp))
        }
        Text(
            text = stringResource(R.string.manual_mark_unavailable),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = contentColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
