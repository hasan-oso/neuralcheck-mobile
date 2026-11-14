package net.neuralcheck.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.neuralcheck.presentation.theme.CardBorder
import net.neuralcheck.presentation.theme.CardDark
import net.neuralcheck.presentation.theme.DeepSpace
import net.neuralcheck.presentation.theme.GradeA
import net.neuralcheck.presentation.theme.GradeB
import net.neuralcheck.presentation.theme.GradeC
import net.neuralcheck.presentation.theme.GradeD
import net.neuralcheck.presentation.theme.NeuralGreen
import net.neuralcheck.presentation.theme.OutfitFamily
import net.neuralcheck.presentation.theme.SkyCyan
import net.neuralcheck.presentation.theme.StatusFail
import net.neuralcheck.presentation.theme.StatusPass
import net.neuralcheck.presentation.theme.StatusUnavailable
import net.neuralcheck.presentation.theme.StatusWarning
import net.neuralcheck.presentation.theme.TextMuted
import net.neuralcheck.presentation.theme.TextPrimary

/**
 * Reusable building blocks that encode the NeuralCheck dark brand identity.
 * Screens must prefer these over raw Material3 primitives so colour/font
 * tweaks propagate from one place.
 */

/** 1 — NCCard: dark card with 1 dp hairline border, 20 dp rounded corners. */
@Composable
fun NCCard(
    modifier: Modifier = Modifier,
    contentPadding: Dp = 20.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark),
        border = BorderStroke(1.dp, CardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(contentPadding), content = content)
    }
}

/**
 * 2 — Grade badge: circle with the grade letter only.
 *
 * Order 2026-09-26 (القرار ١٠٢ البند ٣، القرار ٦ — "`score` لا يُعرض
 * للمستخدم إطلاقاً"): the `score: Float?` parameter this composable used to
 * take, and the small percentage `Text` it painted under the letter, are
 * removed — not just passed `null` at every call site. Removing the
 * parameter is a compile-time guarantee no future caller can silently
 * reintroduce the number (the same technique this codebase already used
 * for `AiV7LetterSourceRow`'s `evidence` parameter, القرار ٩٩ البند ٤).
 * `score`'s raw value is UNCHANGED in the data layer (`HomeViewModel.LastScan`,
 * `ScoringResult.overallScore`, `ScanScoreEntity.overallScore`, etc.) — this
 * order only hides it from every display surface.
 */
@Composable
fun GradeBadge(
    grade: String,
    modifier: Modifier = Modifier,
    size: Dp = 100.dp
) {
    val color = gradeColor(grade)
    Box(
        modifier = modifier
            .size(size)
            .background(color.copy(alpha = 0.12f), CircleShape)
            .border(2.dp, color.copy(alpha = 0.3f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = grade,
            fontFamily = OutfitFamily,
            fontWeight = FontWeight.ExtraBold,
            fontSize = (size.value * 0.40f).sp,
            color = color
        )
    }
}

/** 3 — Status pill: rounded chip for per-test PASS/WARN/FAIL. */
@Composable
fun StatusPill(
    status: String,
    text: String,
    modifier: Modifier = Modifier
) {
    val color = statusColor(status)
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(100.dp),
        color = color.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(Modifier.size(8.dp).background(color, CircleShape))
            Text(
                text = text,
                fontFamily = OutfitFamily,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = color
            )
        }
    }
}

/** 4 — Test result row: name on start, numeric value + colour on end. */
@Composable
fun TestResultRow(
    name: String,
    value: String,
    status: String,
    unit: String = "",
    showDivider: Boolean = true,
    modifier: Modifier = Modifier
) {
    val valueColor = statusColor(status)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            color = TextPrimary
        )
        Text(
            text = if (unit.isNotEmpty()) "$value $unit" else value,
            fontFamily = OutfitFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            color = valueColor
        )
    }
    if (showDivider) HorizontalDivider(color = CardBorder, thickness = 0.5.dp)
}

/** 5 — Gradient progress bar: 6 dp tall, colour shifts by progress value. */
@Composable
fun GradientProgressBar(
    progress: Float,
    modifier: Modifier = Modifier
) {
    val clamped = progress.coerceIn(0f, 1f)
    val brush = when {
        clamped >= 0.85f -> Brush.horizontalGradient(listOf(NeuralGreen, SkyCyan))
        clamped >= 0.70f -> Brush.horizontalGradient(listOf(GradeB, SkyCyan))
        clamped >= 0.50f -> Brush.horizontalGradient(listOf(GradeC, GradeC.copy(alpha = 0.6f)))
        else -> Brush.horizontalGradient(listOf(GradeD, GradeC))
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp)
            .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(3.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(clamped)
                .background(brush, RoundedCornerShape(3.dp))
        )
    }
}

/** 6 — Primary gradient button (NeuralGreen → SkyCyan). */
@Composable
fun NCPrimaryButton(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    onClick: () -> Unit
) {
    // Disable taps while a request is in flight so the caller doesn't have to
    // remember to flip `enabled` too — the spinner already signals "don't
    // tap me again".
    val effectiveEnabled = enabled && !isLoading
    Button(
        onClick = onClick,
        enabled = effectiveEnabled,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent
        ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
    ) {
        val brush = if (effectiveEnabled || isLoading) {
            Brush.horizontalGradient(listOf(NeuralGreen, SkyCyan))
        } else {
            Brush.horizontalGradient(listOf(TextMuted.copy(alpha = 0.4f), TextMuted.copy(alpha = 0.4f)))
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(brush, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = DeepSpace,
                    strokeWidth = 2.dp
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (icon != null) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = DeepSpace
                        )
                    }
                    Text(
                        text = text,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = DeepSpace
                    )
                }
            }
        }
    }
}

/** 7 — Category header: name on start, pass/total + grade chip on end. */
@Composable
fun CategoryHeader(
    nameAr: String,
    passCount: Int,
    totalCount: Int,
    grade: String,
    modifier: Modifier = Modifier
) {
    val color = gradeColor(grade)
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = nameAr,
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$passCount/$totalCount",
                fontFamily = OutfitFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = color
            )
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = color.copy(alpha = 0.15f)
            ) {
                Text(
                    text = grade,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    fontFamily = OutfitFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp,
                    color = color
                )
            }
        }
    }
}

// ---------- helpers ----------
private fun gradeColor(grade: String) = when (grade.uppercase()) {
    "A" -> GradeA
    "B" -> GradeB
    "C" -> GradeC
    "D" -> GradeD
    // Unmeasured-category fix: "?" (and any other non-letter) used to fall
    // through to GradeD and print RED — telling the reader a category we
    // never measured had failed. Unknown is neutral, not a verdict.
    else -> TextMuted
}

private fun statusColor(status: String) = when (status.lowercase()) {
    "pass" -> StatusPass
    "warning", "warn" -> StatusWarning
    "fail", "error" -> StatusFail
    "unavailable" -> StatusUnavailable
    else -> TextMuted
}
