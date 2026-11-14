@file:OptIn(ExperimentalMaterial3Api::class)
package net.neuralcheck.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import net.neuralcheck.R
import net.neuralcheck.domain.manual.ManualTestVisibility
import net.neuralcheck.presentation.util.KeepScreenOn
import net.neuralcheck.presentation.components.NCCard
import net.neuralcheck.presentation.theme.CardBorder
import net.neuralcheck.presentation.theme.CardDark
import net.neuralcheck.presentation.theme.DeepSpace
import net.neuralcheck.presentation.theme.NeuralGreen
import net.neuralcheck.presentation.theme.StatusWarning
import net.neuralcheck.presentation.theme.TextMuted
import net.neuralcheck.presentation.theme.TextPrimary
import net.neuralcheck.presentation.theme.TextSecondary
import net.neuralcheck.presentation.viewmodel.ManualTestsViewModel
import net.neuralcheck.util.NumberFormat

/**
 * Post-scan manual-tests collector. Inserted between [ActiveScanScreen]
 * and [ReportScreen] in the navigation graph so the user always answers
 * (or explicitly skips) every manual test before the report runs scoring.
 *
 * Both flavors funnel through this screen:
 *  - launch (USE_AI_SCORING=true): manual answers populate
 *    `BehaviorFingerprint.manualResults` so the AI sees them. Without this
 *    screen the launch flow used to ship an empty manual list, which was
 *    the original bug.
 *  - datacollect (USE_AI_SCORING=false): RuleBased scoring already accepts
 *    manual results from the existing in-report ManualTestsSection; this
 *    screen just front-loads the question so the report opens with the
 *    final score, not a half-filled section.
 *
 * Each row reuses [ManualTestRow] from ReportScreen so the chip layout +
 * interactive-dialog routing stay in one place. The "متابعة" button at
 * the bottom calls [ManualTestsViewModel.commitAndContinue], which writes
 * any unanswered tests as `not_tested` (so the AI/scoring layer always
 * sees a complete row set, never a missing key).
 */
@Composable
fun ManualTestsScreen(
    navController: NavController,
    scanId: String,
    viewModel: ManualTestsViewModel = hiltViewModel()
) {
    val results by viewModel.results.collectAsState()
    val isCommitting by viewModel.isCommitting.collectAsState()
    val evaluationFailureMessage by viewModel.evaluationFailureMessage.collectAsState()

    LaunchedEffect(scanId) {
        viewModel.load(scanId)
        // Order 2026-09-30 («ملحق — النصّ، ورسالة التقييم، والبروتوكول»):
        // starts listening for the scan-completion evaluation submission's
        // outcome — see ManualTestsViewModel.observeEvaluationFailure's own
        // doc for exactly when this can and cannot still show something.
        viewModel.observeEvaluationFailure(scanId)
    }

    // fix/keep-screen-on: the technician is answering questions here, one
    // tap at a time, right after an 8-minute scan. A screen that sleeps
    // mid-sequence costs more than comfort — it breaks the run, and an
    // unanswered test is stored as `not_tested`, which the report reads as
    // a real attestation ("the technician did not check this"). Held for
    // the whole screen; the reference count means ActiveScanScreen's
    // disposal, which happens AFTER this composes, cannot clear it.
    KeepScreenOn()

    // fieldsVersion 69 (N-17, S-15 (c)): a test the device model does not have is
    // not shown, not counted, and not completed as "not_tested" below — it is named
    // in manualResultsHeader.hiddenByModel instead. ManualTestVisibility.RULES is
    // empty today (the signal per test is a user decision), so nothing is hidden yet.
    val context = LocalContext.current
    val hiddenByModel = remember { ManualTestVisibility.hiddenByModel(context) }
    val shownItems = remember(hiddenByModel) {
        ManualTestVisibility.visible(manualTestItems, hiddenByModel) { it.id }
    }
    val total = shownItems.size
    // Count anything answered (pass / fail / not_tested / unavailable) toward
    // progress — the user's deliberate "skip" is just as valid as a yes/no answer.
    val answered = results.count { it.value.isNotBlank() }

    Scaffold(
        containerColor = DeepSpace,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.manual_tests_screen_title),
                        color = TextPrimary
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DeepSpace,
                    titleContentColor = TextPrimary
                )
            )
        },
        bottomBar = {
            // Sticky CTA so the user can always jump to the report without
            // scrolling back up. Disabled while the commit is in flight to
            // avoid double-fire if Compose recomposes mid-tap.
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding(),
                color = DeepSpace,
                tonalElevation = 8.dp
            ) {
                Button(
                    onClick = {
                        viewModel.commitAndContinue(
                            scanId = scanId,
                            allIds = ManualTestVisibility.idsToAnswer(
                                manualTestItems.map { it.id }, hiddenByModel
                            )
                        ) {
                            // Drop manual_tests off the back stack so back
                            // from the report goes to home, not back here.
                            navController.navigate("report/$scanId") {
                                popUpTo("manual_tests/$scanId") { inclusive = true }
                            }
                        }
                    },
                    enabled = !isCommitting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeuralGreen,
                        contentColor = DeepSpace
                    )
                ) {
                    if (isCommitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.height(20.dp),
                            color = DeepSpace,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = DeepSpace)
                        Spacer(Modifier.fillMaxWidth(0f))
                        Text(
                            stringResource(R.string.manual_tests_continue_btn),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(DeepSpace)
                .padding(padding)
        ) {
            // Header card — explains what this screen is for + shows
            // X/N progress so the user understands they don't have to
            // answer every question individually.
            NCCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                contentPadding = 16.dp
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Checklist, null, tint = NeuralGreen)
                        Spacer(Modifier.fillMaxWidth(0f))
                        Text(
                            text = stringResource(R.string.manual_tests_screen_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            modifier = Modifier
                                .padding(start = 12.dp)
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = NumberFormat.toLatin(
                            stringResource(R.string.manual_tests_progress_fmt, answered, total)
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                    Spacer(Modifier.height(6.dp))
                    @Suppress("DEPRECATION")
                    LinearProgressIndicator(
                        progress = if (total == 0) 0f else answered.toFloat() / total,
                        color = NeuralGreen,
                        trackColor = CardDark,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Order 2026-09-30 («ملحق — النصّ، ورسالة التقييم، والبروتوكول»):
            // one line, shown only on a confirmed FAILURE of the
            // scan-completion evaluation submission (ActiveScanViewModel's
            // `Completed` branch → EvaluationSubmitter.submitOnScanComplete).
            // Success and "no evaluation existed" render nothing — no
            // success line exists (none was ordered). A failure that
            // resolves after the user has already left this screen never
            // reaches here — see ManualTestsViewModel.observeEvaluationFailure.
            evaluationFailureMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = StatusWarning,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            // The actual list — each row reuses ReportScreen.ManualTestRow
            // so the chip layout, interactive dialogs, and per-test state
            // stay consistent across both the post-scan flow and the
            // datacollect in-report editor.
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(shownItems, key = { it.id }) { item ->
                    NCCard(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = 0.dp
                    ) {
                        ManualTestRow(
                            item = item,
                            currentStatus = results[item.id],
                            onResult = { testId, status, measurements ->
                                viewModel.setResult(scanId, testId, status, measurements)
                            }
                        )
                    }
                }
                item { Spacer(Modifier.height(8.dp)) }
            }
        }
    }
}
