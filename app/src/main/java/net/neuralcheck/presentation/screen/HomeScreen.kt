package net.neuralcheck.presentation.screen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LightbulbCircle
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import kotlinx.coroutines.delay
import net.neuralcheck.BuildConfig
import net.neuralcheck.R
import net.neuralcheck.presentation.components.NCPrimaryButton
import net.neuralcheck.presentation.theme.CardBorder
import net.neuralcheck.presentation.theme.CardDark
import net.neuralcheck.presentation.theme.DeepSpace
import net.neuralcheck.presentation.theme.GradeA
import net.neuralcheck.presentation.theme.GradeB
import net.neuralcheck.presentation.theme.GradeC
import net.neuralcheck.presentation.theme.GradeD
import net.neuralcheck.presentation.theme.NeonCyan
import net.neuralcheck.presentation.theme.NeuralGreen
import net.neuralcheck.presentation.theme.OutfitFamily
import net.neuralcheck.presentation.theme.SkyCyan
import net.neuralcheck.presentation.theme.TextMuted
import net.neuralcheck.presentation.theme.TextPrimary
import net.neuralcheck.presentation.theme.TextSecondary
import net.neuralcheck.presentation.viewmodel.HomeViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Launch-flavor home dashboard. Layout top-to-bottom:
 *  - Brand wordmark + tagline
 *  - Welcome card (greeting + scans-remaining badge)
 *  - Last Scan card (conditional — only shown when the user has at least one
 *    synced cloud scan)
 *  - Two-tile stats row (total scans / this month)
 *  - "Start scan" primary CTA
 *  - "Scan another device" CTA (shop plans only)
 *  - Rotating tips strip (swaps every 5 s)
 *  - Bottom nav (Home / History / Settings / Profile)
 *
 * Datacollect flavor keeps a minimal view: wordmark + primary scan CTAs,
 * nothing cloud-dependent.
 */
@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val subscription by viewModel.subscription.collectAsState()
    val displayName by viewModel.displayName.collectAsState()
    val scansTotal by viewModel.scanCount.collectAsState()
    val scansThisMonth by viewModel.scansThisMonth.collectAsState()
    val lastScan by viewModel.lastScan.collectAsState()

    // Substring-based match so we catch every shape the backend has shipped
    // for shop tiers — `shop_basic`, `shop_pro`, plain `basic`/`pro`,
    // upper-case variants, etc. Same matcher style ProfileViewModel uses for
    // the localized plan name, so a value the profile screen treats as
    // "shop tier" also flips the cross-device CTA on here.
    val isShopOwner = subscription?.plan
        ?.let { p -> p.contains("basic", ignoreCase = true) || p.contains("pro", ignoreCase = true) }
        ?: false
    val isLaunchFlavor = BuildConfig.REQUIRE_SUBSCRIPTION

    // Order 2026-09-30 (أمر «مسار الشريك...»، البند ٢): moved to
    // PreScanScreen.kt — Home is NOT the only path into a scan. A
    // customer-device session joined via JoinSessionScreen.kt navigates
    // straight to "pre_scan/general/none" (JoinSessionScreen.kt:54) and
    // never composes Home at all, so a gate here would leave that path with
    // no permission check. PreScanScreen is the one composable both the
    // Home "بدء الفحص" CTA and the join-session flow route through — see
    // PreScanScreen.kt's own ScanPermissionsGate() call for the up-to-date
    // placement, and ScanPermissionsGatePlacementTest for the static check
    // that both paths reach it and Home does not call it a second time.

    Scaffold(
        containerColor = DeepSpace,
        bottomBar = { HomeBottomBar(navController) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(DeepSpace)
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── Brand header ────────────────────────────────────────────
            GradientWordmark()
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.app_tagline),
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )

            Spacer(Modifier.height(24.dp))

            if (isLaunchFlavor) {
                WelcomeCard(
                    name = displayName,
                    subscription = subscription
                )
                Spacer(Modifier.height(12.dp))

                // Last-scan card is only useful once the user has at least
                // one cloud-synced scan. Until then we skip it to keep the
                // home vertical tight.
                lastScan?.let { scan ->
                    LastScanCard(scan, onClick = {
                        navController.navigate("report/${scan.scanId}")
                    })
                    Spacer(Modifier.height(12.dp))
                }

                // Two-tile stats row — sourced from local Room so they stay
                // accurate even offline.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatTile(
                        modifier = Modifier.weight(1f),
                        label = stringResource(R.string.home_total_scans),
                        value = scansTotal.toString()
                    )
                    StatTile(
                        modifier = Modifier.weight(1f),
                        label = stringResource(R.string.home_this_month),
                        value = scansThisMonth.toString()
                    )
                }

                Spacer(Modifier.height(20.dp))
            } else {
                // Datacollect keeps the simpler greeting the original flow used.
                Text(
                    text = stringResource(R.string.home_greeting),
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary
                )
                Spacer(Modifier.height(20.dp))
            }

            // ── Primary CTA ─────────────────────────────────────────────
            NCPrimaryButton(
                text = stringResource(
                    if (isLaunchFlavor) R.string.home_start_scan else R.string.scan_general
                ),
                icon = Icons.Default.PlayArrow,
                onClick = { navController.navigate("pre_scan/general/none") }
            )

            // "Scan another device" — only wired through for shop owners.
            // createScanSession rejects non-shop UIDs, so surfacing it to
            // single-scan users would fail downstream.
            if (isLaunchFlavor && isShopOwner) {
                Spacer(Modifier.height(14.dp))
                HomeScanChoice(
                    icon = Icons.Default.PhoneAndroid,
                    tint = SkyCyan,
                    label = stringResource(R.string.scan_other_device),
                    onClick = { navController.navigate("generate_session") }
                )
            }

            // Pre/post-repair CTAs remain datacollect-only (hidden in launch
            // via BuildConfig.SHOW_MAINTENANCE_SCAN).
            if (BuildConfig.SHOW_MAINTENANCE_SCAN) {
                Spacer(Modifier.height(14.dp))
                HomeScanChoice(
                    icon = Icons.Default.PlayArrow,
                    tint = SkyCyan,
                    label = stringResource(R.string.scan_pre_repair),
                    onClick = { navController.navigate("pre_scan/pre_repair/none") }
                )
                Spacer(Modifier.height(10.dp))
                HomeScanChoice(
                    icon = Icons.Default.PlayArrow,
                    tint = NeonCyan,
                    label = stringResource(R.string.scan_post_repair),
                    onClick = { navController.navigate("qr_scanner") }
                )
            }

            // ── Rotating tips (launch only) ─────────────────────────────
            if (isLaunchFlavor) {
                Spacer(Modifier.height(20.dp))
                TipsRotator(
                    tips = listOf(
                        R.string.home_tip_battery,
                        R.string.home_tip_duration,
                        R.string.home_tip_share
                    )
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Brand wordmark
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun GradientWordmark() {
    val brush = Brush.horizontalGradient(listOf(NeuralGreen, NeonCyan, SkyCyan))
    Text(
        text = stringResource(R.string.app_name),
        fontFamily = OutfitFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 38.sp,
        color = TextPrimary,
        modifier = Modifier
            .graphicsLayer(alpha = 0.99f)
            .drawWithContent {
                drawContent()
                drawRect(brush = brush, blendMode = BlendMode.SrcIn)
            }
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Welcome card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun WelcomeCard(
    name: String,
    subscription: net.neuralcheck.data.remote.SubscriptionStatus?
) {
    val scansRemainingLabel = when {
        subscription == null -> null // still loading — suppress the chip
        // Phase 1.15: shop_pro is now a finite 150-scan/month tier (was
        // advertised "unlimited"). Show the real server-provided
        // scansRemaining for EVERY plan so the chip follows the actual
        // balance and auto-adapts if the limit ever changes server-side —
        // no longer keyed off the plan name.
        else -> stringResource(R.string.home_scans_left_fmt, subscription.scansRemaining)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark),
        border = BorderStroke(1.dp, CardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = stringResource(R.string.home_welcome_fmt, name),
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
            if (scansRemainingLabel != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = scansRemainingLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = NeuralGreen
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Last-scan card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LastScanCard(
    scan: HomeViewModel.LastScan,
    onClick: () -> Unit
) {
    val gradeColor = when (scan.grade.uppercase()) {
        "A" -> GradeA
        "B" -> GradeB
        "C" -> GradeC
        "D" -> GradeD
        else -> TextMuted
    }
    val dateStr = if (scan.createdAtMs > 0L) {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(scan.createdAtMs))
    } else "—"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark),
        border = BorderStroke(1.dp, CardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.home_last_scan),
                style = MaterialTheme.typography.labelMedium,
                color = TextMuted
            )
            Spacer(Modifier.height(10.dp))
            // Order 2026-09-25 (القرار ٨٦ البند ٤): scan.grade can now be
            // the full "الحرف غير متوفر" sentence (a V7 report with a
            // legitimately absent letter), not just a single A/B/C/D — same
            // smaller-font treatment AiV7OverallCard already uses for its
            // own "unavailable" badge (ReportScreen.kt).
            val isKnownGrade = net.neuralcheck.util.AiVerdictPresentation.isKnownGrade(scan.grade)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = gradeColor.copy(alpha = 0.18f),
                    border = BorderStroke(1.dp, gradeColor.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = scan.grade,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        fontFamily = OutfitFamily,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = if (isKnownGrade) 20.sp else 11.sp,
                        color = gradeColor
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = net.neuralcheck.util.DeviceName.prettify(scan.deviceModel),
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = dateStr,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                }
                // Order 2026-09-26 (القرار ١٠٢ البند ٣، القرار ٦ — "score
                // لا يُعرض للمستخدم إطلاقاً"): this used to render
                // `scan.score` for V6 (already null-safe for V7, order
                // 2026-09-25 القرار ٨٦ البند ٤) — removed entirely, not
                // just left null-guarded, since V6's score must never
                // reach the screen either. `scan.score` itself is
                // UNCHANGED in `HomeViewModel.LastScan` (still the real
                // V6 number) — only this display is gone. The grade
                // letter (`scan.grade`) is already shown in the Surface
                // above, unaffected.
            }
            if (scan.summary.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = scan.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.home_view_report),
                style = MaterialTheme.typography.labelLarge,
                color = NeuralGreen,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Stats tile
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StatTile(
    modifier: Modifier = Modifier,
    label: String,
    value: String
) {
    Card(
        modifier = modifier.height(74.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark),
        border = BorderStroke(1.dp, CardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp).fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = TextMuted)
            Text(
                text = value,
                fontFamily = OutfitFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 22.sp,
                color = TextPrimary
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Home scan choice (kept for maintenance-scan flavors + shop-owner CTA)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun HomeScanChoice(
    icon: ImageVector,
    tint: Color,
    label: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark),
        border = BorderStroke(1.dp, CardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(tint.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) { Icon(icon, null, tint = tint) }
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Rotating tips strip
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Tip ticker that rotates through [tips] one at a time. Cycle is 5 s per tip
 * per the product spec; the crossfade avoids a jarring text swap on slow
 * render frames (common on the devices the launch flavor is targeted at).
 */
@Composable
private fun TipsRotator(tips: List<Int>) {
    if (tips.isEmpty()) return
    var index by remember { mutableIntStateOf(0) }
    LaunchedEffect(tips.size) {
        // Infinite rotator — cancelled when the composable leaves the tree so
        // we don't keep firing ticks after the user navigates away.
        while (true) {
            delay(5_000L)
            index = (index + 1) % tips.size
        }
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark),
        border = BorderStroke(1.dp, CardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Default.LightbulbCircle,
                contentDescription = null,
                tint = NeuralGreen
            )
            AnimatedContent(
                targetState = index,
                transitionSpec = {
                    (fadeIn() togetherWith fadeOut())
                },
                label = "home_tip"
            ) { i ->
                Text(
                    text = stringResource(tips[i]),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Bottom nav
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun HomeBottomBar(navController: NavController) {
    NavigationBar(containerColor = CardDark, contentColor = TextSecondary) {
        val itemColors = NavigationBarItemDefaults.colors(
            selectedIconColor = NeuralGreen,
            selectedTextColor = NeuralGreen,
            unselectedIconColor = TextMuted,
            unselectedTextColor = TextMuted,
            indicatorColor = NeuralGreen.copy(alpha = 0.12f)
        )
        NavigationBarItem(
            selected = true, onClick = {},
            icon = { Icon(Icons.Default.Home, null) },
            label = { Text(stringResource(R.string.nav_home)) },
            colors = itemColors
        )
        NavigationBarItem(
            selected = false, onClick = { navController.navigate("history") },
            icon = { Icon(Icons.Default.History, null) },
            label = { Text(stringResource(R.string.nav_history)) },
            colors = itemColors
        )
        NavigationBarItem(
            selected = false, onClick = { navController.navigate("settings") },
            icon = { Icon(Icons.Default.Settings, null) },
            label = { Text(stringResource(R.string.nav_settings)) },
            colors = itemColors
        )
        if (BuildConfig.REQUIRE_SUBSCRIPTION) {
            NavigationBarItem(
                selected = false, onClick = { navController.navigate("profile") },
                icon = { Icon(Icons.Default.Person, null) },
                label = { Text(stringResource(R.string.nav_profile)) },
                colors = itemColors
            )
        }
    }
}
