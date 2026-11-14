@file:OptIn(ExperimentalMaterial3Api::class)
package net.neuralcheck.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import net.neuralcheck.R
import net.neuralcheck.presentation.components.GradeBadge
import net.neuralcheck.presentation.components.NCCard
import net.neuralcheck.presentation.theme.DeepSpace
import net.neuralcheck.presentation.theme.OutfitFamily
import net.neuralcheck.presentation.theme.StatusFail
import net.neuralcheck.presentation.theme.StatusPass
import net.neuralcheck.presentation.theme.TextMuted
import net.neuralcheck.presentation.theme.TextPrimary
import net.neuralcheck.presentation.theme.TextSecondary
import net.neuralcheck.presentation.viewmodel.ComparisonViewModel
import net.neuralcheck.util.NumberFormat

@Composable
fun ComparisonScreen(
    navController: NavController,
    scanId1: String,
    scanId2: String,
    viewModel: ComparisonViewModel = hiltViewModel()
) {
    val beforeScore by viewModel.beforeScore.collectAsState()
    val afterScore by viewModel.afterScore.collectAsState()
    val deltas by viewModel.categoryDeltas.collectAsState()

    LaunchedEffect(scanId1, scanId2) { viewModel.loadScans(scanId1, scanId2) }

    Scaffold(
        containerColor = DeepSpace,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_comparison), color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = TextSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DeepSpace, titleContentColor = TextPrimary
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(DeepSpace)
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ScoreCard(
                    label = stringResource(R.string.comparison_before),
                    grade = beforeScore?.overallGrade ?: "—",
                    modifier = Modifier.weight(1f).padding(4.dp)
                )
                ScoreCard(
                    label = stringResource(R.string.comparison_after),
                    grade = afterScore?.overallGrade ?: "—",
                    modifier = Modifier.weight(1f).padding(4.dp)
                )
            }

            // Order 2026-09-26 (القرار ١٠٢ البند ٣، القرار ٦ — "score لا
            // يُعرض للمستخدم إطلاقاً"): the whole "Overall Change" delta
            // card (an interpolated +/-N.N built entirely from overallScore)
            // is REMOVED — not just null-guarded — since it has no
            // letter-based equivalent to show instead and V6 has no
            // coverage/gate-reason concept to fall back to. Was: `if
            // (beforeOverall != null && afterOverall != null) { NCCard {
            // ... afterOverall - beforeOverall ... } }` (order 2026-09-24،
            // القرار ٧٦ البند ١, which only null-guarded it against a V7
            // NullPointerException — it never questioned showing the
            // number at all).

            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.report_categories),
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary
            )
            Spacer(Modifier.height(8.dp))

            if (deltas.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.comparison_loading),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(deltas) { d -> CategoryDeltaRow(d) }
                }
            }
        }
    }
}

@Composable
private fun ScoreCard(label: String, grade: String, modifier: Modifier = Modifier) {
    // Order 2026-09-26 (القرار ١٠٢ البند ٣): `score: Float?` param removed —
    // GradeBadge itself no longer accepts one (see its own doc).
    NCCard(modifier = modifier, contentPadding = 14.dp) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = TextSecondary
            )
            Spacer(Modifier.height(10.dp))
            GradeBadge(grade = grade, size = 72.dp)
        }
    }
}

@Composable
private fun CategoryDeltaRow(d: ComparisonViewModel.CategoryDelta) {
    val color = when {
        d.delta > 2f -> StatusPass
        d.delta < -2f -> StatusFail
        else -> TextMuted
    }
    NCCard(modifier = Modifier.fillMaxWidth(), contentPadding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = d.name,
                style = MaterialTheme.typography.bodyLarge,
                color = TextPrimary,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${NumberFormat.format(d.beforeScore, 0)} → ${NumberFormat.format(d.afterScore, 0)}",
                fontFamily = OutfitFamily,
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
            Spacer(Modifier.width(10.dp))
            val sign = if (d.delta >= 0) "+" else ""
            Text(
                text = "$sign${NumberFormat.format(d.delta, 0)}",
                fontFamily = OutfitFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                color = color
            )
        }
    }
}
