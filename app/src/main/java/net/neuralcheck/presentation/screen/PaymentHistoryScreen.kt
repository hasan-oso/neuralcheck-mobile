@file:OptIn(ExperimentalMaterial3Api::class)
package net.neuralcheck.presentation.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.Receipt
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
import net.neuralcheck.presentation.components.NCPrimaryButton
import net.neuralcheck.presentation.theme.CardBorder
import net.neuralcheck.presentation.theme.DeepSpace
import net.neuralcheck.presentation.theme.NeonCyan
import net.neuralcheck.presentation.theme.NeuralGreen
import net.neuralcheck.presentation.theme.OutfitFamily
import net.neuralcheck.presentation.theme.StatusFail
import net.neuralcheck.presentation.theme.StatusPass
import net.neuralcheck.presentation.theme.StatusWarning
import net.neuralcheck.presentation.theme.TextMuted
import net.neuralcheck.presentation.theme.TextPrimary
import net.neuralcheck.presentation.theme.TextSecondary
import net.neuralcheck.presentation.viewmodel.PaymentHistoryViewModel
import net.neuralcheck.util.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Read-only list of the signed-in user's ShamCash payment requests. Complements
 * [PaymentScreen]: that one *submits* a request, this one shows what happened
 * afterwards — pending (yellow), confirmed (green), rejected (red, with the
 * admin's reason if one was recorded).
 *
 * Three render states mirror [ScanHistoryScreen]:
 *   - isLoading (initial fetch or manual refresh): centred spinner.
 *   - empty + no error: empty-state illustration, so users who haven't paid
 *     yet aren't left staring at a blank list.
 *   - otherwise: list. A non-destructive error banner sits above the list so
 *     a failed refresh doesn't wipe out previously-loaded data.
 */
@Composable
fun PaymentHistoryScreen(
    navController: NavController,
    viewModel: PaymentHistoryViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val pullState = rememberPullToRefreshState()

    if (pullState.isRefreshing) {
        LaunchedEffect(true) { viewModel.load() }
    }
    LaunchedEffect(state.isLoading) {
        if (!state.isLoading && pullState.isRefreshing) pullState.endRefresh()
    }

    Scaffold(
        containerColor = DeepSpace,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.payment_history_title), color = TextPrimary) },
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
                    state.isLoading && state.payments.isEmpty() -> LoadingState()
                    state.payments.isEmpty() && state.error == null -> EmptyState()
                    state.payments.isEmpty() && state.error != null -> ErrorState(onRetry = { viewModel.load() })
                    else -> {
                        if (state.error != null) {
                            ErrorBanner()
                        }
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(state.payments, key = { it.id }) { payment ->
                                PaymentRow(payment = payment)
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
        CircularProgressIndicator(color = NeonCyan)
    }
}

@Composable
private fun EmptyState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.Receipt, null,
                modifier = Modifier.size(72.dp),
                tint = TextMuted
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.payment_history_empty_title),
                style = MaterialTheme.typography.titleMedium,
                color = TextSecondary,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.payment_history_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted
            )
        }
    }
}

@Composable
private fun ErrorState(onRetry: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            Text(
                text = stringResource(R.string.payment_history_error),
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary
            )
            Spacer(Modifier.height(16.dp))
            NCPrimaryButton(
                text = stringResource(R.string.payment_history_retry),
                onClick = onRetry
            )
        }
    }
}

@Composable
private fun ErrorBanner() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        color = StatusFail.copy(alpha = 0.12f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            text = stringResource(R.string.payment_history_error),
            style = MaterialTheme.typography.bodyMedium,
            color = TextPrimary,
            modifier = Modifier.padding(12.dp)
        )
    }
}

/**
 * One payment row. The status strip on the left is a thin tinted column
 * (not a chip) so the colour reads at a glance across a long list without
 * eating card real estate.
 */
@Composable
private fun PaymentRow(payment: PaymentHistoryViewModel.Payment) {
    val accent: Color = when (payment.status) {
        PaymentHistoryViewModel.Status.PENDING -> StatusWarning
        PaymentHistoryViewModel.Status.CONFIRMED -> StatusPass
        PaymentHistoryViewModel.Status.REJECTED -> StatusFail
        PaymentHistoryViewModel.Status.UNKNOWN -> TextMuted
    }
    val statusText: String = when (payment.status) {
        PaymentHistoryViewModel.Status.PENDING -> stringResource(R.string.payment_history_status_pending)
        PaymentHistoryViewModel.Status.CONFIRMED -> stringResource(R.string.payment_history_status_confirmed)
        PaymentHistoryViewModel.Status.REJECTED -> stringResource(R.string.payment_history_status_rejected)
        PaymentHistoryViewModel.Status.UNKNOWN -> "—"
    }
    val dateStr = if (payment.createdAtMs > 0L) {
        NumberFormat.toLatin(
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(payment.createdAtMs))
        )
    } else "—"
    val amountStr = payment.amount?.let {
        stringResource(R.string.payment_history_amount_syp, NumberFormat.toLatin(it.toString()))
    }

    NCCard(
        modifier = Modifier
            .fillMaxWidth()
            .border(BorderStroke(1.dp, accent.copy(alpha = 0.35f)), RoundedCornerShape(18.dp)),
        contentPadding = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            // Left status strip
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .fillMaxHeight()
                    .background(accent)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = planDisplayForPayment(payment.plan),
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = dateStr,
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                    }
                    if (amountStr != null) {
                        Text(
                            text = amountStr,
                            fontFamily = OutfitFamily,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp,
                            color = NeuralGreen
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .background(accent.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelLarge,
                        color = accent,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (payment.reference.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(
                            R.string.payment_history_reference_label,
                            NumberFormat.toLatin(payment.reference)
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }

                if (payment.status == PaymentHistoryViewModel.Status.REJECTED &&
                    !payment.reason.isNullOrBlank()
                ) {
                    Spacer(Modifier.height(6.dp))
                    Surface(
                        color = StatusFail.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, CardBorder)
                    ) {
                        Text(
                            text = stringResource(R.string.payment_history_reason_label, payment.reason),
                            style = MaterialTheme.typography.bodySmall,
                            color = StatusFail,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Maps a canonical plan key to the user-facing localized name. Falls back to
 * the raw key so an unknown plan still renders something the user can quote
 * to support — same pattern [ProfileScreen] uses for its stat card.
 */
@Composable
private fun planDisplayForPayment(key: String): String = when (key.lowercase()) {
    "single" -> stringResource(R.string.subscription_plan_single_name)
    "shop_basic" -> stringResource(R.string.subscription_plan_shop_basic_name)
    "shop_pro" -> stringResource(R.string.subscription_plan_shop_pro_name)
    else -> key
}
