@file:OptIn(ExperimentalMaterial3Api::class)
package net.neuralcheck.presentation.screen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import net.neuralcheck.presentation.theme.CardDark
import net.neuralcheck.presentation.theme.DeepSpace
import net.neuralcheck.presentation.theme.NeonCyan
import net.neuralcheck.presentation.theme.NeuralGreen
import net.neuralcheck.presentation.theme.OutfitFamily
import net.neuralcheck.presentation.theme.SkyCyan
import net.neuralcheck.presentation.theme.StatusFail
import net.neuralcheck.presentation.theme.Surface
import net.neuralcheck.presentation.theme.TextMuted
import net.neuralcheck.presentation.theme.TextPrimary
import net.neuralcheck.presentation.theme.TextSecondary
import net.neuralcheck.presentation.viewmodel.PaymentViewModel

/**
 * Payment/ShamCash flow. The user copies the transfer number, completes the
 * transfer outside the app, then pastes the reference here and taps Confirm.
 * The callable `requestPayment(plan, reference)` records the pending payment
 * and returns a paymentId we don't surface (back-office reviews manually).
 *
 * On success we flip to a "request received" card so the user has an
 * unambiguous end-state. Back navigation takes them to the Profile screen —
 * not Subscription — so they don't accidentally re-submit another plan.
 */
@Composable
fun PaymentScreen(
    navController: NavController,
    plan: String,
    viewModel: PaymentViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val reference by viewModel.reference.collectAsState()
    // ShamCash transfer number: viewmodel reads it from settings/app on init
    // and emits the resource fallback meanwhile. Collecting here means the
    // row updates the moment the Firestore read resolves, with no flicker.
    val transferNumber by viewModel.transferNumber.collectAsState()
    val context = LocalContext.current

    Scaffold(
        containerColor = DeepSpace,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.payment_title), color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = TextSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DeepSpace,
                    titleContentColor = TextPrimary
                )
            )
        }
    ) { padding ->
        if (state.isSubmitted) {
            PaymentSuccess(
                modifier = Modifier.fillMaxSize().padding(padding),
                onDone = {
                    navController.navigate("profile") {
                        popUpTo("profile") { inclusive = true }
                    }
                }
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(DeepSpace)
                    .padding(padding)
                    .padding(horizontal = 20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Spacer(Modifier.height(8.dp))

                // ==== Plan + amount summary ====
                NCCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.payment_plan_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = TextMuted
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = planDisplay(plan),
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(12.dp))
                    Row {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.payment_amount_label),
                                style = MaterialTheme.typography.labelMedium,
                                color = TextMuted
                            )
                            Text(
                                text = priceDisplay(plan),
                                fontFamily = OutfitFamily,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 20.sp,
                                color = NeuralGreen
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.payment_method_label),
                                style = MaterialTheme.typography.labelMedium,
                                color = TextMuted
                            )
                            Text(
                                text = stringResource(R.string.payment_method_value),
                                style = MaterialTheme.typography.titleMedium,
                                color = NeonCyan
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // ==== Instructions ====
                NCCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.payment_instructions_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.payment_instructions_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(14.dp))
                    TransferNumberRow(
                        number = transferNumber,
                        onCopy = {
                            copyToClipboard(
                                context,
                                transferNumber
                            )
                            Toast.makeText(
                                context,
                                context.getString(R.string.payment_copied),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    )
                }

                Spacer(Modifier.height(14.dp))

                // ==== Reference input ====
                NCCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.payment_reference_label),
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = reference,
                        onValueChange = { viewModel.setReference(it) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text(
                                stringResource(R.string.payment_reference_hint),
                                color = TextMuted
                            )
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Surface,
                            unfocusedContainerColor = Surface,
                            focusedBorderColor = NeuralGreen,
                            unfocusedBorderColor = CardBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            cursorColor = NeuralGreen
                        )
                    )

                    val errorText = when (state.error) {
                        "empty" -> stringResource(R.string.payment_error_empty_reference)
                        null -> null
                        else -> stringResource(R.string.payment_error_generic)
                    }
                    if (errorText != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = errorText,
                            color = StatusFail,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                NCPrimaryButton(
                    text = stringResource(R.string.payment_confirm_button),
                    isLoading = state.isSubmitting,
                    onClick = { viewModel.submit(plan) }
                )

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun TransferNumberRow(number: String, onCopy: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Surface, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.payment_transfer_to_label),
                style = MaterialTheme.typography.labelMedium,
                color = TextMuted
            )
            Text(
                text = number,
                fontFamily = OutfitFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = SkyCyan
            )
        }
        Row(
            modifier = Modifier
                .height(40.dp)
                .background(NeuralGreen.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                .clickable(onClick = onCopy)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                Icons.Default.ContentCopy, null,
                tint = NeuralGreen,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = stringResource(R.string.payment_copy),
                color = NeuralGreen,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

@Composable
private fun PaymentSuccess(modifier: Modifier, onDone: () -> Unit) {
    Column(
        modifier = modifier
            .background(DeepSpace)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .background(NeuralGreen.copy(alpha = 0.14f), RoundedCornerShape(48.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.CheckCircle, null,
                tint = NeuralGreen,
                modifier = Modifier.size(56.dp)
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.payment_success_title),
            style = MaterialTheme.typography.headlineSmall,
            color = TextPrimary,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.payment_success_body),
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
        Spacer(Modifier.height(28.dp))
        NCPrimaryButton(
            text = stringResource(R.string.payment_success_done),
            onClick = onDone
        )
    }
}

@Composable
private fun planDisplay(key: String): String = when (key.lowercase()) {
    "single" -> stringResource(R.string.subscription_plan_single_name)
    "shop_basic" -> stringResource(R.string.subscription_plan_shop_basic_name)
    "shop_pro" -> stringResource(R.string.subscription_plan_shop_pro_name)
    else -> key
}

@Composable
private fun priceDisplay(key: String): String = when (key.lowercase()) {
    "single" -> stringResource(R.string.subscription_plan_single_price)
    "shop_basic" -> stringResource(R.string.subscription_plan_shop_basic_price)
    "shop_pro" -> stringResource(R.string.subscription_plan_shop_pro_price)
    else -> ""
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("transfer_number", text))
}
