package net.neuralcheck.presentation.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.neuralcheck.R
import net.neuralcheck.domain.evaluation.EvaluationAnswers
import net.neuralcheck.presentation.components.NCCard
import net.neuralcheck.presentation.theme.CardBorder
import net.neuralcheck.presentation.theme.CardDark
import net.neuralcheck.presentation.theme.NeuralGreen
import net.neuralcheck.presentation.theme.SkyCyan
import net.neuralcheck.presentation.theme.TextMuted
import net.neuralcheck.presentation.theme.TextPrimary
import net.neuralcheck.presentation.theme.TextSecondary
import net.neuralcheck.presentation.viewmodel.TechnicianEvaluationViewModel

/**
 * feat/technician-evaluation — blind technician questions rendered ABOVE
 * the running scan (inside ActiveScanScreen's scroll column).
 *
 * Measurement-safety contract (diagnosis §8 — non-negotiable):
 *  - Fully static composable: NO animations, NO AnimatedVisibility, NO
 *    infinite transitions/shimmer/spinner, NO LaunchedEffect/timer here.
 *  - Recomposes ONLY when the ViewModel's tap-driven state changes.
 *  - Shows NOTHING of our measurements: no grade, no live values, no
 *    hints — that isolation is the entire point of asking mid-scan.
 *
 * All answers are single taps on ≥48dp targets (thumb-sized on a device
 * that isn't the technician's own); every tap is persisted immediately
 * by the ViewModel on Dispatchers.IO.
 */
@Composable
fun TechnicianEvaluationCard(viewModel: TechnicianEvaluationViewModel) {
    if (!viewModel.isEvaluationSession || viewModel.dismissed) return
    val draft = viewModel.draft ?: return

    val ratings = EvaluationAnswers.parseCategoryRatings(draft.categoryRatingsJson)
    val defects = EvaluationAnswers.parseDefects(draft.visibleDefectsJson)

    val gradeLabels = listOf(
        EvaluationAnswers.GRADE_EXCELLENT to stringResource(R.string.eval_grade_excellent),
        EvaluationAnswers.GRADE_GOOD to stringResource(R.string.eval_grade_good),
        EvaluationAnswers.GRADE_FAIR to stringResource(R.string.eval_grade_fair),
        EvaluationAnswers.GRADE_WEAK to stringResource(R.string.eval_grade_weak)
    )
    val categoryLabels = listOf(
        EvaluationAnswers.CAT_BATTERY to stringResource(R.string.eval_cat_battery),
        EvaluationAnswers.CAT_CPU_PERFORMANCE to stringResource(R.string.eval_cat_cpu),
        EvaluationAnswers.CAT_MEMORY to stringResource(R.string.eval_cat_memory),
        EvaluationAnswers.CAT_STORAGE to stringResource(R.string.eval_cat_storage)
    )
    val defectLabels = listOf(
        EvaluationAnswers.DEFECT_WEAK_BATTERY to stringResource(R.string.eval_defect_weak_battery),
        EvaluationAnswers.DEFECT_REPLACED_SCREEN to stringResource(R.string.eval_defect_replaced_screen),
        EvaluationAnswers.DEFECT_WATER_DAMAGE to stringResource(R.string.eval_defect_water_damage),
        EvaluationAnswers.DEFECT_PRIOR_REPAIR to stringResource(R.string.eval_defect_prior_repair),
        EvaluationAnswers.DEFECT_NONE_NOTICED to stringResource(R.string.eval_defect_none)
    )

    NCCard(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Column(Modifier.padding(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.eval_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = SkyCyan
                )
                TextButton(onClick = { viewModel.dismiss() }) {
                    Text(
                        text = stringResource(R.string.eval_skip),
                        color = TextMuted,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Text(
                text = stringResource(R.string.eval_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )

            // ── Q1: overall grade — 2×2 thumb-sized grid ──
            SectionLabel(stringResource(R.string.eval_q_overall))
            gradeLabels.chunked(2).forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowItems.forEach { (key, label) ->
                        ChoiceButton(
                            label = label,
                            selected = draft.technicianGradeRaw == key,
                            modifier = Modifier.weight(1f).height(48.dp),
                            onClick = { viewModel.onGradeTap(key) }
                        )
                    }
                }
            }

            // ── Q2: depth categories — 4-point scale + "لم أفحصه" ──
            SectionLabel(stringResource(R.string.eval_q_categories))
            categoryLabels.forEach { (catKey, catLabel) ->
                Text(
                    text = catLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    modifier = Modifier.padding(top = 10.dp, bottom = 6.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    gradeLabels.forEach { (rateKey, rateLabel) ->
                        ChoiceButton(
                            label = rateLabel,
                            selected = ratings[catKey] == rateKey,
                            compact = true,
                            modifier = Modifier.weight(1f).height(44.dp),
                            onClick = { viewModel.onCategoryTap(catKey, rateKey) }
                        )
                    }
                }
                ChoiceButton(
                    label = stringResource(R.string.eval_not_tested),
                    selected = ratings[catKey] == EvaluationAnswers.RATING_NOT_TESTED,
                    compact = true,
                    neutral = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(40.dp),
                    onClick = {
                        viewModel.onCategoryTap(catKey, EvaluationAnswers.RATING_NOT_TESTED)
                    }
                )
            }

            // ── Q3: visible defects — multi-select chips ──
            SectionLabel(stringResource(R.string.eval_q_defects))
            DefectChips(
                defectLabels = defectLabels,
                selected = defects,
                onToggle = { viewModel.onDefectTap(it) }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DefectChips(
    defectLabels: List<Pair<String, String>>,
    selected: List<String>,
    onToggle: (String) -> Unit
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        defectLabels.forEach { (key, label) ->
            ChoiceButton(
                label = label,
                selected = key in selected,
                compact = true,
                modifier = Modifier.height(44.dp),
                onClick = { onToggle(key) }
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = TextSecondary,
        modifier = Modifier.padding(top = 16.dp)
    )
    Spacer(Modifier.height(2.dp))
}

/** Static toggle button — selection is a plain color/border swap, no
 *  animation of any kind. [neutral] renders the "لم أفحصه" attestation in
 *  a muted tone so it reads as an explicit answer, not a highlight. */
@Composable
private fun ChoiceButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    neutral: Boolean = false
) {
    val accent = if (neutral) TextMuted else NeuralGreen
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = if (compact) 6.dp else 12.dp, vertical = 4.dp
        ),
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) accent else CardBorder
        ),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (selected) accent.copy(alpha = 0.16f) else CardDark,
            contentColor = if (selected) accent else TextSecondary
        )
    ) {
        Text(
            text = label,
            fontSize = if (compact) 12.sp else 15.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1
        )
    }
}
