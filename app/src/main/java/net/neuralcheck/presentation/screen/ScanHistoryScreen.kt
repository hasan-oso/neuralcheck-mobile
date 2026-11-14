@file:OptIn(ExperimentalMaterial3Api::class)
package net.neuralcheck.presentation.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import net.neuralcheck.R
import net.neuralcheck.presentation.components.NCCard
import net.neuralcheck.presentation.theme.DeepSpace
import net.neuralcheck.presentation.theme.GradeA
import net.neuralcheck.presentation.theme.GradeB
import net.neuralcheck.presentation.theme.GradeC
import net.neuralcheck.presentation.theme.GradeD
import net.neuralcheck.presentation.theme.NeonCyan
import net.neuralcheck.presentation.theme.NeuralGreen
import net.neuralcheck.presentation.theme.OutfitFamily
import net.neuralcheck.presentation.theme.TextMuted
import net.neuralcheck.presentation.theme.TextPrimary
import net.neuralcheck.presentation.theme.TextSecondary
import net.neuralcheck.presentation.viewmodel.ScanHistoryViewModel
import net.neuralcheck.util.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Cloud-backed scan history — reads the `reports` Firestore collection
 * (limited to 50, descending by `createdAt`) and surfaces each scan as a
 * clickable card that opens the local report viewer. We deliberately do not
 * duplicate the local HistoryScreen here: that one is repository-driven,
 * includes unsynced scans, and is used by the scan-type selector flow; this
 * one is the user-facing "my account's history" list and only shows what
 * the backend has actually acknowledged.
 *
 * Three render states:
 *   - isLoading: spinner (initial fetch + manual refresh)
 *   - scans.isEmpty() && error == null: empty-state illustration
 *   - otherwise: the list. Errors get an inline banner above the list
 *     rather than replacing it, so a refresh that fails doesn't wipe out
 *     the previously-loaded data.
 */
@Composable
fun ScanHistoryScreen(
    navController: NavController,
    viewModel: ScanHistoryViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val pullState = rememberPullToRefreshState()

    // Kick off the refresh when the user pulls past the threshold.
    if (pullState.isRefreshing) {
        LaunchedEffect(true) { viewModel.load() }
    }
    // End the indicator once the VM reports the load is done — watching
    // `isLoading` is more reliable than racing a timer.
    LaunchedEffect(state.isLoading) {
        if (!state.isLoading && pullState.isRefreshing) pullState.endRefresh()
    }

    Scaffold(
        containerColor = DeepSpace,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.scan_history_title), color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = TextSecondary)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.load() }) {
                        Icon(Icons.Default.Refresh, null, tint = TextSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DeepSpace,
                    titleContentColor = TextPrimary
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DeepSpace)
                .padding(padding)
                .nestedScroll(pullState.nestedScrollConnection)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                when {
                    state.isLoading && state.scans.isEmpty() -> LoadingState()
                    state.scans.isEmpty() && state.error == null -> EmptyState()
                    else -> {
                        if (state.error != null) ErrorBanner()
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(state.scans, key = { it.scanId }) { scan ->
                                CloudScanRow(scan = scan, onClick = {
                                    navController.navigate("report/${scan.scanId}")
                                })
                            }
                        }
                    }
                }
            }
            PullToRefreshContainer(
                state = pullState,
                modifier = Modifier.align(Alignment.TopCenter),
                containerColor = DeepSpace,
                contentColor = NeuralGreen
            )
        }
    }
}

@Composable
private fun LoadingState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = NeonCyan)
            Spacer(Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.scan_history_loading),
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun EmptyState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.FolderOpen, null,
                modifier = Modifier.size(72.dp), tint = TextMuted
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.scan_history_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun ErrorBanner() {
    // Non-destructive: sits above the list so a stale list is still
    // readable. The card uses StatusFail's tint rather than the full colour
    // so it doesn't look like a hard failure — more like a yellow flag.
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        color = Color(0xFFEF4444).copy(alpha = 0.12f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.CloudOff, null, tint = Color(0xFFEF4444))
            Spacer(Modifier.width(10.dp))
            Text(
                text = stringResource(R.string.scan_history_error_generic),
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary
            )
        }
    }
}

/**
 * Single scan row. Grade chip on the left (colour-coded), device model +
 * verdict in the middle, score + date on the right. Kept visually dense so
 * a user can scan (pun intended) a long list without horizontal jumps.
 */
@Composable
private fun CloudScanRow(
    scan: ScanHistoryViewModel.CloudScan,
    onClick: () -> Unit
) {
    val gradeColor: Color = when (scan.grade.uppercase()) {
        "A" -> GradeA; "B" -> GradeB; "C" -> GradeC; "D" -> GradeD
        else -> TextMuted
    }
    val dateStr = if (scan.createdAtMs > 0L) {
        NumberFormat.toLatin(
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(scan.createdAtMs))
        )
    } else "—"

    NCCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        contentPadding = 14.dp
    ) {
        // Order 2026-09-25 (القرار ٨٦ البند ٤): scan.grade can now be the
        // full "الحرف غير متوفر" sentence — same smaller-font treatment as
        // HomeScreen's LastScanCard / AiV7OverallCard's own unavailable badge.
        val isKnownGrade = net.neuralcheck.util.AiVerdictPresentation.isKnownGrade(scan.grade)
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Grade chip
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = gradeColor.copy(alpha = 0.18f),
                border = BorderStroke(1.dp, gradeColor.copy(alpha = 0.5f))
            ) {
                Text(
                    text = scan.grade,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    fontFamily = OutfitFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = if (isKnownGrade) 20.sp else 11.sp,
                    color = gradeColor
                )
            }
            Spacer(Modifier.width(14.dp))

            // Centre column: device + verdict
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = net.neuralcheck.util.DeviceName.prettify(scan.deviceModel),
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                if (scan.verdict.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = scan.verdict,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        maxLines = 1
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = dateStr,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
            }

            // Order 2026-09-26 (القرار ١٠٢ البند ٣، القرار ٦ — "score لا
            // يُعرض للمستخدم إطلاقاً"): the right-column numeric score +
            // its "Score" caption (already null-safe for V7, order
            // 2026-09-25 القرار ٨٦ البند ٤) are removed entirely — V6's
            // real score must never reach the screen either. `scan.score`
            // itself is UNCHANGED in `ScanHistoryViewModel.CloudScan` —
            // only this display is gone. The grade letter (`scan.grade`)
            // is already shown in the chip to the left, unaffected.

            Spacer(Modifier.width(6.dp))
            Icon(Icons.Default.ChevronRight, null, tint = TextMuted)
        }
    }
}
