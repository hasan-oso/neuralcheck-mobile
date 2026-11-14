@file:OptIn(ExperimentalMaterial3Api::class)
package net.neuralcheck.presentation.screen

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import net.neuralcheck.BuildConfig
import net.neuralcheck.R
import net.neuralcheck.presentation.components.NCCard
import net.neuralcheck.presentation.theme.CardBorder
import net.neuralcheck.presentation.theme.CardDark
import net.neuralcheck.presentation.theme.DeepSpace
import net.neuralcheck.presentation.theme.NeuralGreen
import net.neuralcheck.presentation.theme.StatusFail
import net.neuralcheck.presentation.theme.Surface
import net.neuralcheck.presentation.theme.TextMuted
import net.neuralcheck.presentation.theme.TextPrimary
import net.neuralcheck.presentation.theme.TextSecondary
import net.neuralcheck.presentation.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val syncEnabled by viewModel.syncEnabled.collectAsState()
    val language by viewModel.language.collectAsState()
    val context = LocalContext.current
    @Suppress("UNUSED_VARIABLE")
    val scope = rememberCoroutineScope()
    var versionTapCount by remember { mutableIntStateOf(0) }
    var showDevTools by remember { mutableStateOf(false) }

    val changeLanguage: (String) -> Unit = remember(context) {
        { newLang ->
            if (newLang != language) {
                viewModel.setLanguage(newLang)
                (context as? Activity)?.recreate()
            }
        }
    }

    Scaffold(
        containerColor = DeepSpace,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_settings), color = TextPrimary) },
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
        },
        bottomBar = {
            NavigationBar(containerColor = CardDark, contentColor = TextSecondary) {
                val itemColors = NavigationBarItemDefaults.colors(
                    selectedIconColor = NeuralGreen,
                    selectedTextColor = NeuralGreen,
                    unselectedIconColor = TextMuted,
                    unselectedTextColor = TextMuted,
                    indicatorColor = NeuralGreen.copy(alpha = 0.12f)
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { navController.navigate("home") { popUpTo("home") { inclusive = true } } },
                    icon = { Icon(Icons.Default.Home, null) },
                    label = { Text(stringResource(R.string.nav_home)) },
                    colors = itemColors
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { navController.navigate("history") },
                    icon = { Icon(Icons.Default.History, null) },
                    label = { Text(stringResource(R.string.nav_history)) },
                    colors = itemColors
                )
                NavigationBarItem(
                    selected = true, onClick = {},
                    icon = { Icon(Icons.Default.Settings, null) },
                    label = { Text(stringResource(R.string.nav_settings)) },
                    colors = itemColors
                )
                // Profile tab — launch flavor only (datacollect has no auth /
                // backend subscription flow for the profile screen to display).
                if (BuildConfig.REQUIRE_SUBSCRIPTION) {
                    NavigationBarItem(
                        selected = false,
                        onClick = { navController.navigate("profile") },
                        icon = { Icon(Icons.Default.Person, null) },
                        label = { Text(stringResource(R.string.nav_profile)) },
                        colors = itemColors
                    )
                }
            }
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

            // Sync section
            NCCard(modifier = Modifier.fillMaxWidth()) {
                SectionTitle(stringResource(R.string.settings_sync))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (syncEnabled) stringResource(R.string.settings_sync_enabled)
                               else stringResource(R.string.settings_sync_disabled),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Switch(
                        checked = syncEnabled,
                        onCheckedChange = { viewModel.setSyncEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = DeepSpace,
                            checkedTrackColor = NeuralGreen,
                            uncheckedThumbColor = TextMuted,
                            uncheckedTrackColor = Surface
                        )
                    )
                }
                TextButton(onClick = { viewModel.syncNow() }) {
                    Text(stringResource(R.string.settings_sync_now), color = NeuralGreen)
                }
            }

            Spacer(Modifier.height(14.dp))

            // Language
            NCCard(modifier = Modifier.fillMaxWidth()) {
                SectionTitle(stringResource(R.string.settings_language))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    BrandFilterChip(
                        selected = language == "ar",
                        label = stringResource(R.string.settings_language_ar),
                        onClick = { changeLanguage("ar") }
                    )
                    BrandFilterChip(
                        selected = language == "tr",
                        label = stringResource(R.string.settings_language_tr),
                        onClick = { changeLanguage("tr") }
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // About
            NCCard(modifier = Modifier.fillMaxWidth()) {
                SectionTitle(stringResource(R.string.settings_about))
                Text(
                    // Read the version straight from BuildConfig so this string
                    // tracks app/build.gradle.kts versionName automatically. The
                    // previous hardcoded "1.0.0" went stale on every release —
                    // see Phase 1.11 follow-up.
                    text = "${stringResource(R.string.settings_version)}: ${net.neuralcheck.BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary,
                    modifier = Modifier.clickable {
                        versionTapCount++
                        if (versionTapCount >= 7) showDevTools = true
                    }
                )
            }

            if (showDevTools) {
                Spacer(Modifier.height(14.dp))
                NCCard(modifier = Modifier.fillMaxWidth()) {
                    SectionTitle(stringResource(R.string.settings_dev_tools), color = StatusFail)
                    OutlinedButton(
                        onClick = { },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.settings_export_data), color = TextPrimary) }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusFail)
                    ) { Text(stringResource(R.string.settings_clear_data)) }
                }
            }

            // Sign-out button — only shown in the launch flavor (datacollect
            // never authenticates) and only when there is actually a Firebase
            // user to sign out. Confirmation dialog avoids stranding a user
            // who tapped by mistake halfway through a scan session.
            if (BuildConfig.REQUIRE_SUBSCRIPTION && viewModel.isLoggedIn) {
                var showLogoutDialog by remember { mutableStateOf(false) }
                Spacer(Modifier.height(14.dp))
                NCCard(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { showLogoutDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusFail)
                    ) {
                        Text(stringResource(R.string.logout))
                    }
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
                                // Wait for AuthManager.signOut() to finish wiping
                                // Room + Firestore cache before navigating, so
                                // the new user can't see the previous account's
                                // scans on History/Home through Room reads that
                                // don't filter by userId.
                                // popUpTo(0) clears the entire back stack so
                                // History/Home can't be reached via back-press
                                // after sign-out.
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
            }

            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String, color: androidx.compose.ui.graphics.Color = TextPrimary) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        color = color
    )
    Spacer(Modifier.height(10.dp))
    HorizontalDivider(color = CardBorder, thickness = 0.5.dp)
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun BrandFilterChip(
    selected: Boolean,
    label: String,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Surface,
            labelColor = TextSecondary,
            selectedContainerColor = NeuralGreen.copy(alpha = 0.15f),
            selectedLabelColor = NeuralGreen
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = CardBorder,
            selectedBorderColor = NeuralGreen
        )
    )
}
