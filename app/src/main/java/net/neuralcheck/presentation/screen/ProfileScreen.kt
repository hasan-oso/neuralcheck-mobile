@file:OptIn(ExperimentalMaterial3Api::class)
package net.neuralcheck.presentation.screen

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import net.neuralcheck.R
import net.neuralcheck.presentation.components.NCCard
import net.neuralcheck.presentation.theme.CardBorder
import net.neuralcheck.presentation.theme.CardDark
import net.neuralcheck.presentation.theme.DeepSpace
import net.neuralcheck.presentation.theme.NeonCyan
import net.neuralcheck.presentation.theme.NeuralGreen
import net.neuralcheck.presentation.theme.OutfitFamily
import net.neuralcheck.presentation.theme.SkyCyan
import net.neuralcheck.presentation.theme.StatusFail
import net.neuralcheck.presentation.theme.TextMuted
import net.neuralcheck.presentation.theme.TextPrimary
import net.neuralcheck.presentation.theme.TextSecondary
import net.neuralcheck.presentation.viewmodel.ProfileViewModel

/**
 * Launch-flavor account screen. Shown only when `BuildConfig.REQUIRE_SUBSCRIPTION`
 * is true (datacollect never sees this screen) — so we render freely assuming a
 * signed-in Firebase user. If a user slips through unauthenticated (race on
 * sign-out while the screen is on the back stack) the profile ViewModel falls
 * back to "Guest" copy rather than crashing, since that degrades better than
 * throwing across the recomposition.
 */
@Composable
fun ProfileScreen(
    navController: NavController,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val promoState by viewModel.promoState.collectAsState()

    var showAboutDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showPromoDialog by remember { mutableStateOf(false) }

    // Auto-dismiss the promo dialog once the VM flips `justRedeemed`. The
    // VM resets `justRedeemed` back to false on the next load cycle, and
    // the snackbar handles the user-facing success feedback.
    LaunchedEffect(promoState.justRedeemed) {
        if (promoState.justRedeemed && showPromoDialog) {
            showPromoDialog = false
            viewModel.resetPromoState()
        }
    }

    Scaffold(
        containerColor = DeepSpace,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.profile_title), color = TextPrimary) },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(DeepSpace)
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(16.dp))

            // ==== Avatar header ====
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AvatarCircle(letter = state.avatarLetter)
                Spacer(Modifier.size(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.displayName,
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary
                    )
                    Text(
                        text = state.email,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // ==== Stat cards row ====
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatCard(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.profile_stat_plan),
                    value = state.planDisplayName.ifBlank {
                        stringResource(R.string.profile_plan_none)
                    },
                    isLoading = state.isLoading
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.profile_stat_scans_left),
                    value = if (state.isLoading) "…" else state.subscription.scansRemaining.toString(),
                    isLoading = state.isLoading
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.profile_stat_days_left),
                    value = when {
                        state.isLoading -> "…"
                        state.expiryEpochMs == null -> stringResource(R.string.profile_no_expiry)
                        else -> state.daysRemaining.toString()
                    },
                    isLoading = state.isLoading
                )
            }

            Spacer(Modifier.height(16.dp))

            // ==== Actions card ====
            NCCard(modifier = Modifier.fillMaxWidth(), contentPadding = 0.dp) {
                ActionRow(
                    icon = Icons.Default.CreditCard,
                    iconTint = NeuralGreen,
                    title = stringResource(R.string.profile_action_upgrade),
                    subtitle = stringResource(R.string.profile_action_upgrade_desc),
                    onClick = { navController.navigate("subscription") }
                )
                // Shop-setup row — visible only to shop subscribers (any
                // tier). The single-scan plan and unauthenticated /
                // not-yet-subscribed users have no storefront concept, so
                // hiding the entry point keeps the menu uncluttered for
                // them. Falls back to hidden while the subscription is
                // still loading (`plan` is empty), same conservative
                // default the Home screen uses for the cross-device CTA.
                //
                // Substring match so the same gate works whether the
                // backend returns `shop_basic` / `shop_pro` (current),
                // `basic` / `pro` (legacy), or any case variation thereof.
                val plan = state.subscription.plan
                val isShop = plan.contains("basic", ignoreCase = true) ||
                    plan.contains("pro", ignoreCase = true)
                if (isShop) {
                    ActionDivider()
                    ActionRow(
                        icon = Icons.Default.Storefront,
                        iconTint = NeuralGreen,
                        title = stringResource(R.string.shop_setup_action),
                        subtitle = stringResource(R.string.shop_setup_action_desc),
                        onClick = { navController.navigate("shop_setup") }
                    )
                }
                ActionDivider()
                ActionRow(
                    icon = Icons.Default.CardGiftcard,
                    iconTint = SkyCyan,
                    title = stringResource(R.string.promo_action_title),
                    subtitle = stringResource(R.string.promo_action_subtitle),
                    onClick = {
                        // Fresh dialog state on every open so an error from a
                        // previous attempt doesn't bleed into the new one.
                        viewModel.resetPromoState()
                        showPromoDialog = true
                    }
                )
                ActionDivider()
                ActionRow(
                    icon = Icons.Default.Receipt,
                    iconTint = NeuralGreen,
                    title = stringResource(R.string.profile_action_payment_history),
                    subtitle = stringResource(R.string.profile_action_payment_history_desc),
                    onClick = { navController.navigate("payment_history") }
                )
                ActionDivider()
                ActionRow(
                    icon = Icons.Default.CloudDownload,
                    iconTint = SkyCyan,
                    title = stringResource(R.string.profile_action_scan_history),
                    subtitle = stringResource(R.string.profile_action_scan_history_desc),
                    onClick = { navController.navigate("scan_history") }
                )
                ActionDivider()
                ActionRow(
                    icon = Icons.Default.Info,
                    iconTint = NeonCyan,
                    title = stringResource(R.string.profile_action_about),
                    subtitle = stringResource(R.string.profile_action_about_desc),
                    onClick = { showAboutDialog = true }
                )
            }

            Spacer(Modifier.height(16.dp))

            // ==== Logout ====
            if (viewModel.isLoggedIn) {
                OutlinedButton(
                    onClick = { showLogoutDialog = true },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = CardDark,
                        contentColor = StatusFail
                    )
                ) {
                    Icon(Icons.AutoMirrored.Filled.Logout, null, tint = StatusFail)
                    Spacer(Modifier.size(10.dp))
                    Text(stringResource(R.string.profile_action_logout), fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    if (showAboutDialog) {
        AlertDialog(
            containerColor = CardDark,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            onDismissRequest = { showAboutDialog = false },
            title = { Text(stringResource(R.string.profile_about_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.profile_about_body,
                        net.neuralcheck.BuildConfig.VERSION_NAME
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text(stringResource(R.string.btn_ok), color = NeuralGreen)
                }
            }
        )
    }

    if (showLogoutDialog) {
        AlertDialog(
            containerColor = CardDark,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            onDismissRequest = { showLogoutDialog = false },
            title = { Text(stringResource(R.string.logout)) },
            text = { Text(stringResource(R.string.logout_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutDialog = false
                    // Wait for the Room/Firestore wipe in AuthManager.signOut()
                    // before navigating, so the History/Home tiles can't read
                    // the old user's rows during the brief composition gap
                    // between popUpTo(0) and the login screen mounting.
                    viewModel.signOut {
                        navController.navigate("login") {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                }) { Text(stringResource(R.string.logout), color = StatusFail) }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text(stringResource(R.string.btn_cancel), color = TextSecondary)
                }
            }
        )
    }

    if (showPromoDialog) {
        PromoCodeDialog(
            promoState = promoState,
            onDismiss = {
                showPromoDialog = false
                viewModel.resetPromoState()
            },
            onSubmit = { code -> viewModel.redeemPromoCode(code) }
        )
    }
}

/**
 * Input dialog for redeeming a promo code. The Activate button flips to an
 * in-place spinner while the redemption is in flight; an inline error
 * surface underneath the field shows the already-localized failure message
 * from the VM. Auto-dismissal on success is handled at the parent level so
 * the snackbar + stat-card refresh sequence happens atomically after the
 * dialog closes.
 */
@Composable
private fun PromoCodeDialog(
    promoState: ProfileViewModel.PromoState,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit
) {
    var code by remember { mutableStateOf("") }
    val submit = { if (code.isNotBlank()) onSubmit(code) }

    AlertDialog(
        containerColor = CardDark,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.promo_dialog_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text(stringResource(R.string.promo_code_label)) },
                    singleLine = true,
                    enabled = !promoState.isSubmitting,
                    // Force uppercase on the keyboard because promo codes are
                    // typed by hand and case-folding on the server would be
                    // one more failure mode to debug.
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeuralGreen,
                        unfocusedBorderColor = CardBorder,
                        focusedLabelColor = NeuralGreen,
                        unfocusedLabelColor = TextSecondary,
                        cursorColor = NeuralGreen,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )
                if (promoState.error != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = promoState.error,
                        style = MaterialTheme.typography.bodySmall,
                        color = StatusFail
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = submit,
                enabled = !promoState.isSubmitting && code.isNotBlank()
            ) {
                if (promoState.isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = NeuralGreen,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        stringResource(R.string.promo_btn_activate),
                        color = NeuralGreen,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !promoState.isSubmitting) {
                Text(stringResource(R.string.btn_cancel), color = TextSecondary)
            }
        }
    )
}

@Composable
private fun AvatarCircle(letter: String) {
    val brush = Brush.linearGradient(listOf(NeuralGreen, SkyCyan))
    Box(
        modifier = Modifier
            .size(72.dp)
            .background(brush, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = letter,
            fontFamily = OutfitFamily,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 32.sp,
            color = DeepSpace
        )
    }
}

@Composable
private fun StatCard(
    modifier: Modifier,
    label: String,
    value: String,
    isLoading: Boolean
) {
    Box(
        modifier = modifier
            .height(92.dp)
            .background(CardDark, RoundedCornerShape(16.dp))
            .padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxSize()) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted
            )
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = NeuralGreen,
                    strokeWidth = 2.dp
                )
            } else {
                Text(
                    text = value,
                    fontFamily = OutfitFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = TextPrimary
                )
            }
        }
    }
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String?,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(iconTint.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, tint = iconTint) }
        Spacer(Modifier.size(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }
        Icon(Icons.Default.ChevronRight, null, tint = TextMuted)
    }
}

@Composable
private fun ActionDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .background(CardBorder)
    )
}

