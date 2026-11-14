@file:OptIn(ExperimentalMaterial3Api::class)
package net.neuralcheck.presentation.screen

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.scale
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import net.neuralcheck.R
import net.neuralcheck.presentation.components.NCPrimaryButton
import net.neuralcheck.presentation.theme.CardBorder
import net.neuralcheck.presentation.theme.CardDark
import net.neuralcheck.presentation.theme.DeepSpace
import net.neuralcheck.presentation.theme.NeonCyan
import net.neuralcheck.presentation.theme.NeuralGreen
import net.neuralcheck.presentation.theme.OutfitFamily
import net.neuralcheck.presentation.theme.SkyCyan
import net.neuralcheck.presentation.theme.TextMuted
import net.neuralcheck.presentation.theme.TextPrimary
import net.neuralcheck.presentation.theme.TextSecondary
import net.neuralcheck.presentation.viewmodel.SubscriptionViewModel

/**
 * Three-plan picker for the launch flavor. The plan keys (`single`,
 * `shop_basic`, `shop_pro`) match what the Cloud Functions backend expects on
 * `requestPayment` — using them verbatim in the nav argument avoids a
 * translation table on either end.
 *
 * Uses [SubscriptionViewModel] to fetch the user's active plan so each card
 * can flip its CTA (Renew / Upgrade / Downgrade / Buy-extra) and the current
 * plan gets a "✓ خطتك الحالية" badge in place of the "Most Popular" chip.
 */
@Composable
fun SubscriptionScreen(
    navController: NavController,
    viewModel: SubscriptionViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val currentPlan = state.currentPlan

    Scaffold(
        containerColor = DeepSpace,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.subscription_title), color = TextPrimary) },
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
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.subscription_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary
            )

            Spacer(Modifier.height(20.dp))

            PlanCard(
                plan = "single",
                name = stringResource(R.string.subscription_plan_single_name),
                price = stringResource(R.string.subscription_plan_single_price),
                period = stringResource(R.string.subscription_plan_single_period),
                scansLabel = stringResource(R.string.subscription_plan_single_scans),
                features = listOf(
                    stringResource(R.string.subscription_plan_single_feature_1),
                    stringResource(R.string.subscription_plan_single_feature_2),
                    stringResource(R.string.subscription_plan_single_feature_3),
                    stringResource(R.string.subscription_plan_single_feature_4)
                ),
                // Single is the reference rate ($0.50/scan) — no "save X%" badge.
                savingsPercent = null,
                isPopular = false,
                accent = SkyCyan,
                currentPlan = currentPlan,
                onSelect = { navController.navigate("payment/single") }
            )

            Spacer(Modifier.height(14.dp))

            PlanCard(
                plan = "shop_basic",
                name = stringResource(R.string.subscription_plan_shop_basic_name),
                price = stringResource(R.string.subscription_plan_shop_basic_price),
                period = stringResource(R.string.subscription_plan_shop_basic_period),
                scansLabel = stringResource(R.string.subscription_plan_shop_basic_scans),
                features = listOf(
                    stringResource(R.string.subscription_plan_shop_basic_feature_1),
                    stringResource(R.string.subscription_plan_shop_basic_feature_2),
                    stringResource(R.string.subscription_plan_shop_basic_feature_3),
                    stringResource(R.string.subscription_plan_shop_basic_feature_4),
                    stringResource(R.string.subscription_plan_shop_basic_feature_5)
                ),
                // 50 scans/month for $10 vs. $25 single-rate (0.50 × 50)
                // → 60% off the per-scan rate. Recomputed because the single
                // price dropped from $1 to $0.50; the badge stays accurate.
                savingsPercent = 60,
                isPopular = true,
                accent = NeuralGreen,
                currentPlan = currentPlan,
                onSelect = { navController.navigate("payment/shop_basic") }
            )

            Spacer(Modifier.height(14.dp))

            PlanCard(
                plan = "shop_pro",
                name = stringResource(R.string.subscription_plan_shop_pro_name),
                price = stringResource(R.string.subscription_plan_shop_pro_price),
                period = stringResource(R.string.subscription_plan_shop_pro_period),
                scansLabel = stringResource(R.string.subscription_plan_shop_pro_scans),
                features = listOf(
                    stringResource(R.string.subscription_plan_shop_pro_feature_1),
                    stringResource(R.string.subscription_plan_shop_pro_feature_2),
                    stringResource(R.string.subscription_plan_shop_pro_feature_3),
                    stringResource(R.string.subscription_plan_shop_pro_feature_4),
                    stringResource(R.string.subscription_plan_shop_pro_feature_5),
                    stringResource(R.string.subscription_plan_shop_pro_feature_6)
                ),
                // 150 scans/month for $20 vs. $75 single-rate (0.50 × 150)
                // → ~73% off the per-scan rate. (shop_pro was advertised
                // "unlimited" pre-Phase-1.15; it is now a finite 150-scan
                // tier to match the unified pricing across backend + web + app.)
                savingsPercent = 73,
                isPopular = false,
                accent = NeonCyan,
                currentPlan = currentPlan,
                onSelect = { navController.navigate("payment/shop_pro") }
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * Returns the button label resource that makes sense for going from
 * [currentPlan] to [cardPlan]. Rules:
 *  - Same plan → Renew.
 *  - Target is `single` while the user already has a recurring plan → Buy
 *    Extra (the backend treats single purchases as additive top-ups, not
 *    plan changes).
 *  - User has no active plan → default "Select" CTA.
 *  - Target tier > current → Upgrade.
 *  - Target tier < current → Downgrade.
 */
private fun buttonLabelRes(currentPlan: String, cardPlan: String): Int {
    val current = currentPlan.trim().lowercase()
    if (current == cardPlan) return R.string.subscription_btn_renew
    val currentTier = planTier(current)
    val cardTier = planTier(cardPlan)
    if (cardPlan == "single" && currentTier >= 2) {
        return R.string.subscription_btn_buy_extra
    }
    return when {
        currentTier == 0 -> R.string.subscription_select_button
        cardTier > currentTier -> R.string.subscription_btn_upgrade
        cardTier < currentTier -> R.string.subscription_btn_downgrade
        else -> R.string.subscription_select_button
    }
}

private fun planTier(plan: String): Int = when (plan) {
    "shop_pro" -> 3
    "shop_basic" -> 2
    "single" -> 1
    else -> 0
}

/**
 * A single plan card. When [plan] matches [currentPlan], the card gets the
 * highlighted border + a "✓ خطتك الحالية" badge (which replaces the "Most
 * Popular" badge since both occupy the same top-end corner). Otherwise the
 * popular variant — [isPopular] — keeps its gradient border. The CTA text
 * switches between Select / Renew / Upgrade / Downgrade / Buy-Extra based on
 * the relationship between [currentPlan] and [plan].
 */
@Composable
private fun PlanCard(
    plan: String,
    name: String,
    price: String,
    period: String,
    scansLabel: String,
    features: List<String>,
    savingsPercent: Int?,
    isPopular: Boolean,
    accent: Color,
    currentPlan: String,
    onSelect: () -> Unit
) {
    val isCurrent = currentPlan.isNotBlank() && currentPlan == plan
    val borderBrush = when {
        isCurrent -> Brush.linearGradient(listOf(NeuralGreen, NeuralGreen))
        isPopular -> Brush.linearGradient(listOf(NeuralGreen, SkyCyan))
        else -> Brush.linearGradient(listOf(CardBorder, CardBorder))
    }
    val borderWidth = if (isCurrent || isPopular) 2.dp else 1.dp

    // Whole-card press scale — 0.97 while the finger is down, springs back
    // to 1.0 on release. Ties a tactile feel to the card (as opposed to the
    // inner CTA button) because the entire surface is a "choose this plan"
    // action from the user's perspective.
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = tween(durationMillis = 120),
        label = "plan_card_scale"
    )

    Box(modifier = Modifier.scale(scale)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(CardDark, RoundedCornerShape(22.dp))
                .border(BorderStroke(borderWidth, borderBrush), RoundedCornerShape(22.dp))
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onSelect
                )
                .padding(20.dp)
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = price,
                    fontFamily = OutfitFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 26.sp,
                    color = accent
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = period,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted
                )
                if (savingsPercent != null) {
                    Spacer(Modifier.weight(1f))
                    Box(
                        modifier = Modifier
                            .background(
                                NeuralGreen.copy(alpha = 0.16f),
                                RoundedCornerShape(10.dp)
                            )
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.subscription_save_fmt, savingsPercent),
                            fontFamily = OutfitFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = NeuralGreen
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Text(
                text = scansLabel,
                style = MaterialTheme.typography.titleMedium,
                color = TextSecondary,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(14.dp))

            features.forEach { feature ->
                FeatureRow(text = feature, accent = accent)
                Spacer(Modifier.height(6.dp))
            }

            Spacer(Modifier.height(14.dp))
            NCPrimaryButton(
                text = stringResource(buttonLabelRes(currentPlan, plan)),
                onClick = onSelect
            )
        }

        // The "current plan" badge takes precedence over "most popular" —
        // only one can occupy the top-end corner, and the user's own plan
        // is the more useful signal once we know what it is.
        val badgeRes = when {
            isCurrent -> R.string.subscription_current_badge
            isPopular -> R.string.subscription_popular_badge
            else -> null
        }
        if (badgeRes != null) {
            // The badge floats above the card's top edge. `Modifier.padding`
            // rejects negative values (IllegalArgumentException at runtime),
            // so we use `Modifier.offset` for the upward shift and keep
            // padding for the positive end inset.
            val badgeBrush = if (isCurrent) {
                Brush.horizontalGradient(listOf(NeuralGreen, NeuralGreen))
            } else {
                Brush.horizontalGradient(listOf(NeuralGreen, SkyCyan))
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(y = (-10).dp)
                    .padding(end = 16.dp)
                    .background(badgeBrush, RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = stringResource(badgeRes),
                    fontFamily = OutfitFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 12.sp,
                    color = DeepSpace
                )
            }
        }
    }
}

@Composable
private fun FeatureRow(text: String, accent: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .background(accent.copy(alpha = 0.15f), RoundedCornerShape(11.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Check, null,
                tint = accent,
                modifier = Modifier.size(14.dp)
            )
        }
        Spacer(Modifier.size(10.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = TextPrimary
        )
    }
}
