@file:OptIn(ExperimentalMaterial3Api::class)
package net.neuralcheck.presentation.screen

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import net.neuralcheck.R
import net.neuralcheck.data.local.db.entity.ScanEntity
import net.neuralcheck.presentation.components.NCCard
import net.neuralcheck.presentation.viewmodel.HistoryViewModel
import net.neuralcheck.presentation.theme.CardDark
import net.neuralcheck.presentation.theme.DeepSpace
import net.neuralcheck.presentation.theme.NeonCyan
import net.neuralcheck.presentation.theme.NeuralGreen
import net.neuralcheck.presentation.theme.SkyCyan
import net.neuralcheck.presentation.theme.Surface
import net.neuralcheck.presentation.theme.TextMuted
import net.neuralcheck.presentation.theme.TextPrimary
import net.neuralcheck.presentation.theme.TextSecondary
import net.neuralcheck.util.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    navController: NavController,
    selectForScanType: String? = null,
    viewModel: HistoryViewModel = hiltViewModel()
) {
    var searchQuery by remember { mutableStateOf("") }
    val allScans by viewModel.scans.collectAsState()
    val visibleScans = remember(allScans, selectForScanType) {
        if (selectForScanType == "post_repair") {
            val preOnly = allScans.filter { it.scanType == "pre_repair" }
            if (preOnly.isNotEmpty()) preOnly else allScans
        } else allScans
    }
    val filtered = remember(visibleScans, searchQuery) {
        if (searchQuery.isBlank()) visibleScans
        else visibleScans.filter {
            it.deviceModel.contains(searchQuery, ignoreCase = true) ||
                it.scanType.contains(searchQuery, ignoreCase = true)
        }
    }

    val isSelectionMode = selectForScanType != null

    Scaffold(
        containerColor = DeepSpace,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isSelectionMode) stringResource(R.string.history_select_title)
                               else stringResource(R.string.title_history),
                        color = TextPrimary
                    )
                },
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
            if (!isSelectionMode) {
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
                        selected = true, onClick = {},
                        icon = { Icon(Icons.Default.History, null) },
                        label = { Text(stringResource(R.string.nav_history)) },
                        colors = itemColors
                    )
                    NavigationBarItem(
                        selected = false,
                        onClick = { navController.navigate("settings") },
                        icon = { Icon(Icons.Default.Settings, null) },
                        label = { Text(stringResource(R.string.nav_settings)) },
                        colors = itemColors
                    )
                    if (net.neuralcheck.BuildConfig.REQUIRE_SUBSCRIPTION) {
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
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(DeepSpace)
                .padding(padding)
        ) {
            if (isSelectionMode) {
                Text(
                    text = stringResource(R.string.history_select_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                placeholder = { Text(stringResource(R.string.history_search), color = TextMuted) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = NeonCyan) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Surface,
                    unfocusedContainerColor = Surface,
                    focusedBorderColor = NeuralGreen,
                    unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    cursorColor = NeuralGreen
                )
            )

            if (filtered.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.FolderOpen, null,
                            modifier = Modifier.size(72.dp), tint = TextMuted
                        )
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = stringResource(R.string.history_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextSecondary
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filtered, key = { it.scanId }) { scan ->
                        ScanHistoryItem(scan = scan, onClick = {
                            if (selectForScanType != null) {
                                navController.navigate("pre_scan/$selectForScanType/${scan.scanId}") {
                                    popUpTo("home")
                                }
                            } else {
                                navController.navigate("report/${scan.scanId}")
                            }
                        })
                    }
                }
            }
        }
    }
}

@Composable
private fun ScanHistoryItem(scan: ScanEntity, onClick: () -> Unit) {
    val dateStr = NumberFormat.toLatin(SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(scan.timestamp)))
    // Colour the left icon by scan type so types are distinguishable at a glance
    // without another dedicated strip of UI chrome.
    val accent = when (scan.scanType) {
        "general" -> NeuralGreen
        "pre_repair" -> SkyCyan
        "post_repair" -> NeonCyan
        else -> TextMuted
    }

    NCCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        contentPadding = 14.dp
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(accent.copy(alpha = 0.14f), CircleShape),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.PhoneAndroid, null, tint = accent) }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                // Legacy rows still hold the raw codename (Build.MODEL), so
                // run them through DeviceName.prettify on display. New rows
                // are already pretty at write time.
                Text(
                    net.neuralcheck.util.DeviceName.prettify(scan.deviceModel),
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
                Text(dateStr, style = MaterialTheme.typography.bodySmall, color = TextMuted)
                Text(scan.scanType, style = MaterialTheme.typography.labelSmall, color = accent)
            }
            if (!scan.isSynced) {
                Icon(Icons.Default.CloudOff, null, tint = TextMuted, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
            }
            Icon(Icons.Default.ChevronRight, null, tint = TextMuted)
        }
    }
}
