@file:OptIn(ExperimentalMaterial3Api::class)
package net.neuralcheck.presentation.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import net.neuralcheck.presentation.theme.TextMuted
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import net.neuralcheck.R
import net.neuralcheck.data.remote.SessionLeaver
import net.neuralcheck.presentation.components.NCCard
import net.neuralcheck.presentation.components.NCPrimaryButton
import net.neuralcheck.presentation.navigation.leaveToScanOrigin
import net.neuralcheck.presentation.viewmodel.LeaveSessionViewModel
import net.neuralcheck.util.NumberFormat
import net.neuralcheck.presentation.theme.CardDark
import net.neuralcheck.presentation.theme.DeepSpace
import net.neuralcheck.presentation.theme.NeuralGreen
import net.neuralcheck.presentation.theme.StatusFail
import net.neuralcheck.presentation.theme.StatusPass
import net.neuralcheck.presentation.theme.StatusWarning
import net.neuralcheck.presentation.theme.TextPrimary
import net.neuralcheck.presentation.theme.TextSecondary
import net.neuralcheck.presentation.viewmodel.PreScanViewModel

@Composable
fun PreScanScreen(
    navController: NavController,
    scanType: String,
    linkedScanId: String?,
    viewModel: PreScanViewModel = hiltViewModel()
) {
    val conditions by viewModel.conditions.collectAsState()
    val accessState by viewModel.accessState.collectAsState()
    val trialBanner by viewModel.trialBanner.collectAsState()
    var showDialog by remember { mutableStateOf(false) }
    // Phase 1.7.D — the warnings list captured when the user taps Start.
    // Drives the warning dialog content; emptied/cleared on cancel.
    var pendingWarnings by remember {
        mutableStateOf<List<PreScanViewModel.PreScanWarning>>(emptyList())
    }
    var subscriptionError by remember { mutableStateOf<String?>(null) }
    val hasBlocker = conditions.any { it.level == ConditionLevel.RED }

    // Batch 2, item 2 (D2, D3; texts 5 and 6): a device that holds a session leaves it through a confirmation - the leave button, Back and the toolbar arrow
    // all open it; a confirmed leave sends left_by_customer and goes back to where the funnel started. A device in no session (the owner's own scan) is unchanged.
    val leaveViewModel: LeaveSessionViewModel = hiltViewModel()
    val leaveState by leaveViewModel.state.collectAsState()
    val heldSessionId = leaveViewModel.sessionOfPreScan()
    var showLeaveDialog by remember { mutableStateOf(false) }
    BackHandler(enabled = heldSessionId != null) { showLeaveDialog = true }
    LaunchedEffect(leaveState) {
        if (leaveState is LeaveSessionViewModel.State.Left) navController.leaveToScanOrigin()
    }

    // Order 2026-09-30 (أمر «مسار الشريك، والنصوص، وإصلاح الخادم»، البند ٢):
    // moved here from HomeScreen.kt. PreScanScreen is the ONE composable both
    // scan-start paths route through before active_scan — the Home "بدء
    // الفحص" CTA navigates to "pre_scan/{scanType}/{linkedScanId}"
    // (NeuralCheckNavigation.kt's route), and so does the customer-device
    // join-session flow the instant a session code resolves
    // (JoinSessionScreen.kt:54, which never composes Home at all — a gate
    // placed in HomeScreen.kt would silently skip every joined session).
    // Called once per PreScanScreen composition, same as it was once per
    // Home composition before the move — no duplicate call exists anywhere
    // else (see ScanPermissionsGatePlacementTest, a static source check).
    ScanPermissionsGate()

    // Respond to the subscription gate. Transitions fire when the user taps
    // "Confirm" and [viewModel.checkAccess] runs — the side effects
    // (navigate to login / show paywall error / proceed to scan) happen
    // here instead of in the ViewModel so navigation stays UI-only.
    val noScansMsg = stringResource(R.string.subscription_no_scans)
    LaunchedEffect(accessState) {
        when (accessState) {
            // A-SUB wave 1, item 5: a scan allowed without the check's answer (it failed) starts exactly like an allowed one - no new text; the state and the log line say the difference
            is PreScanViewModel.AccessState.Allowed, is PreScanViewModel.AccessState.AllowedUnverified -> {
                viewModel.consumeAccessState()
                navController.navigate("active_scan/$scanType/${linkedScanId ?: "none"}")
            }
            is PreScanViewModel.AccessState.NeedsLogin -> {
                viewModel.consumeAccessState()
                navController.navigate("login")
            }
            is PreScanViewModel.AccessState.NoScansRemaining -> {
                subscriptionError = noScansMsg
                viewModel.consumeAccessState()
            }
            else -> Unit
        }
    }

    Scaffold(
        containerColor = DeepSpace,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.title_pre_scan),
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { if (heldSessionId != null) showLeaveDialog = true else navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = TextSecondary)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = DeepSpace
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
        ) {
            Text(
                text = stringResource(R.string.prescan_instruction),
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary
            )
            Spacer(Modifier.size(16.dp))

            // Free-trial banner — slots in above the conditions list when the
            // backend reports the user is on the complimentary trial plan.
            // HasTrial: green-tinged celebratory copy; TrialUsed: warning-
            // tinged "subscribe to continue". Hidden state renders nothing.
            when (trialBanner) {
                PreScanViewModel.TrialBannerState.HasTrial -> {
                    NCCard(modifier = Modifier.fillMaxWidth(), contentPadding = 12.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, null, tint = NeuralGreen)
                            Spacer(Modifier.size(10.dp))
                            Text(
                                text = stringResource(R.string.free_trial_banner),
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary
                            )
                        }
                    }
                    Spacer(Modifier.size(14.dp))
                }
                PreScanViewModel.TrialBannerState.TrialUsed -> {
                    NCCard(modifier = Modifier.fillMaxWidth(), contentPadding = 12.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, null, tint = StatusWarning)
                            Spacer(Modifier.size(10.dp))
                            Text(
                                text = stringResource(R.string.free_trial_used),
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary
                            )
                        }
                    }
                    Spacer(Modifier.size(14.dp))
                }
                PreScanViewModel.TrialBannerState.Hidden -> Unit
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(conditions) { ConditionCard(it) }
            }

            Spacer(Modifier.size(12.dp))
            if (subscriptionError != null) {
                Text(
                    text = subscriptionError!!,
                    style = MaterialTheme.typography.bodyMedium,
                    color = StatusFail
                )
                Spacer(Modifier.size(8.dp))
            }
            NCPrimaryButton(
                text = stringResource(R.string.btn_start_scan),
                enabled = !hasBlocker,
                isLoading = accessState is PreScanViewModel.AccessState.Checking,
                onClick = {
                    // Phase 1.7.D: evaluate warnings on click so the
                    // dialog reflects state at the moment the user
                    // tapped Start, not the last 2-second poll tick.
                    pendingWarnings = viewModel.evaluatePreScanWarnings()
                    showDialog = true
                }
            )
            if (heldSessionId != null) {
                Spacer(Modifier.size(8.dp))
                OutlinedButton(onClick = { showLeaveDialog = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.session_leave_btn), color = StatusFail)
                }
            }
            // V2 UI 6 — preconditions text under the start button. Tells
            // the user the new scan is 8 minutes (was 11.5 in V1) and
            // lists the three things that must hold for a clean run:
            // battery ≥ 25%, free storage ≥ 8 GB, no foreground use.
            Spacer(Modifier.size(8.dp))
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                Text(
                    text = stringResource(R.string.scan_v2_duration_label),
                    color = TextMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.size(4.dp))
                listOf(
                    R.string.scan_v2_pre_battery,
                    R.string.scan_v2_pre_storage,
                    R.string.scan_v2_pre_no_use
                ).forEach { resId ->
                    Text(
                        text = "• ${stringResource(resId)}",
                        color = TextMuted,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            Spacer(Modifier.size(12.dp))
        }
    }

    if (showLeaveDialog && heldSessionId != null) {
        val leaving = leaveState is LeaveSessionViewModel.State.InProgress
        val leaveFailure = (leaveState as? LeaveSessionViewModel.State.Failed)?.message
        AlertDialog(
            containerColor = CardDark,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            onDismissRequest = { if (!leaving) { showLeaveDialog = false; leaveViewModel.dismiss() } },
            title = { Text(stringResource(R.string.session_leave_confirm_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.session_leave_confirm_text))
                    if (leaving) {
                        Spacer(Modifier.size(12.dp))
                        CircularProgressIndicator(color = NeuralGreen, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                    }
                    if (leaveFailure != null) {
                        Spacer(Modifier.size(12.dp))
                        Text(leaveFailure, color = StatusFail)
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = !leaving, onClick = { leaveViewModel.leave(SessionLeaver.Phase.PRE_SCAN, heldSessionId) }) {
                    Text(stringResource(R.string.btn_confirm), color = StatusFail)
                }
            },
            dismissButton = {
                TextButton(enabled = !leaving, onClick = { showLeaveDialog = false; leaveViewModel.dismiss() }) {
                    Text(stringResource(R.string.btn_cancel), color = TextSecondary)
                }
            }
        )
    }

    if (showDialog) {
        // Phase 1.7.D + Phase 1.11 follow-up — four branches:
        //   1) ChargerPlugged → hard block (single OK button, no proceed)
        //   2) HotBattery(blocking=true) above 35 °C → confirm-to-override
        //      dialog (primary "wait", secondary "scan anyway")
        //   3) Other soft warnings → existing two-button confirm
        //   4) No warnings → simple confirm
        val chargerWarn = pendingWarnings.firstOrNull {
            it is PreScanViewModel.PreScanWarning.ChargerPlugged
        }
        val hotBlocker = pendingWarnings.firstOrNull {
            it is PreScanViewModel.PreScanWarning.HotBattery && it.blocking
        } as? PreScanViewModel.PreScanWarning.HotBattery
        val softWarns = pendingWarnings.filter {
            it !is PreScanViewModel.PreScanWarning.ChargerPlugged &&
                !(it is PreScanViewModel.PreScanWarning.HotBattery && it.blocking)
        }
        when {
            chargerWarn != null -> AlertDialog(
                containerColor = CardDark,
                titleContentColor = TextPrimary,
                textContentColor = TextSecondary,
                onDismissRequest = { showDialog = false },
                title = { Text(stringResource(R.string.prescan_warn_title)) },
                text = { Text(stringResource(R.string.prescan_warn_charger)) },
                confirmButton = {
                    TextButton(onClick = { showDialog = false }) {
                        Text(stringResource(R.string.btn_ok), color = NeuralGreen)
                    }
                }
            )
            hotBlocker != null -> AlertDialog(
                // Confirm-to-override semantic: the PRIMARY (confirmButton, more
                // visually prominent) is the SAFE action — wait. The SECONDARY
                // (dismissButton) is the override "scan anyway". Reversed from
                // the soft-warning dialog below because at >35 °C every measured
                // metric is contaminated by thermal-governor capping; we want
                // the path of least resistance to be "don't scan now".
                containerColor = CardDark,
                titleContentColor = TextPrimary,
                textContentColor = TextSecondary,
                onDismissRequest = { showDialog = false },
                title = { Text(stringResource(R.string.prescan_warn_hot_blocking_title)) },
                text = {
                    Text(
                        stringResource(
                            R.string.prescan_warn_hot_blocking_body_fmt,
                            NumberFormat.format(hotBlocker.tempC, 1)
                        )
                    )
                },
                confirmButton = {
                    // PRIMARY — wait (closes without scanning).
                    TextButton(onClick = { showDialog = false }) {
                        Text(
                            stringResource(R.string.prescan_warn_hot_blocking_wait),
                            color = NeuralGreen
                        )
                    }
                },
                dismissButton = {
                    // SECONDARY — proceed override. Record the stable IDs of
                    // every pending warning (including the blocker) before
                    // proceeding so the footer carries the override decision
                    // through to the AI and audit logs.
                    TextButton(onClick = {
                        viewModel.acceptWarningsAndProceed(pendingWarnings)
                        showDialog = false
                        subscriptionError = null
                        viewModel.checkAccess()
                    }) {
                        Text(
                            stringResource(R.string.prescan_warn_hot_blocking_proceed),
                            color = TextSecondary
                        )
                    }
                }
            )
            softWarns.isNotEmpty() -> AlertDialog(
                containerColor = CardDark,
                titleContentColor = TextPrimary,
                textContentColor = TextSecondary,
                onDismissRequest = { showDialog = false },
                title = { Text(stringResource(R.string.prescan_warn_title)) },
                text = {
                    Column {
                        softWarns.forEach { w ->
                            val msg = when (w) {
                                is PreScanViewModel.PreScanWarning.HotBattery ->
                                    stringResource(
                                        R.string.prescan_warn_hot_fmt,
                                        NumberFormat.format(w.tempC, 1)
                                    )
                                is PreScanViewModel.PreScanWarning.RecentlyUnplugged ->
                                    stringResource(
                                        R.string.prescan_warn_recently_unplugged_fmt,
                                        w.minutesAgo.toInt()
                                    )
                                else -> ""
                            }
                            if (msg.isNotEmpty()) {
                                Text("• $msg", modifier = Modifier.padding(vertical = 2.dp))
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        // User accepted the warning; record stable IDs
                        // for the footer and proceed to subscription gate.
                        viewModel.acceptWarningsAndProceed(softWarns)
                        showDialog = false
                        subscriptionError = null
                        viewModel.checkAccess()
                    }) {
                        Text(stringResource(R.string.btn_proceed_anyway), color = NeuralGreen)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDialog = false }) {
                        Text(stringResource(R.string.btn_cancel), color = TextSecondary)
                    }
                }
            )
            else -> AlertDialog(
                containerColor = CardDark,
                titleContentColor = TextPrimary,
                textContentColor = TextSecondary,
                onDismissRequest = { showDialog = false },
                title = { Text(stringResource(R.string.title_active_scan)) },
                text = { Text(stringResource(R.string.prescan_start_confirm)) },
                confirmButton = {
                    TextButton(onClick = {
                        // No warnings — clear any stale ones from a
                        // previous flow and proceed normally.
                        viewModel.acceptWarningsAndProceed(emptyList())
                        showDialog = false
                        subscriptionError = null
                        viewModel.checkAccess()
                    }) {
                        Text(stringResource(R.string.btn_confirm), color = NeuralGreen)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDialog = false }) {
                        Text(stringResource(R.string.btn_cancel), color = TextSecondary)
                    }
                }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Pre-scan permission + location-service gate (order 2026-09-30: طلب
// الأذونات، بند ١، ثم البند ٤ الملحق في فحص NC-TM7PDN، ثم أمر «مسار الشريك»
// البند ٢ الذي نقلها من HomeScreen.kt إلى هنا)
//
// Two stages, run in this fixed order the first time THIS composable enters
// composition — i.e. every time a scan is about to start, whether the
// technician came from Home or a customer device just joined a session:
//   1. PERMISSIONS — requests every runtime permission a functional test
//      needs (CAMERA, RECORD_AUDIO, ACCESS_FINE_LOCATION, READ_PHONE_STATE —
//      see ScanRuntimePermission) as one batch. A denial never blocks the
//      scan — CameraTests/GpsTests/NetworkTests already turn a missing
//      permission into NOT_TESTED, never FAIL, test by test (see
//      ScanPermissions.kt's doc comment for the exact file:line for each) —
//      this only surfaces that decision up front with one notice.
//   2. LOCATION SERVICE — checked only after stage 1 is dismissed (either
//      button), because it answers a different question ("is GPS turned on
//      at all") that only matters once we know whether the app can even ask
//      for a location fix. Added per NC-TM7PDN (Galaxy S21 Ultra, Android 14):
//      location services were off, so L-01 came back "معطّل" (FAIL, not
//      NOT_TESTED — GpsTests.kt's testGpsAvailable() has no permission gate
//      at all, see ScanPermissions.kt's doc comment) and L-02/L-03 came back
//      a status this order calls ENVIRONMENT_UNSUITABLE — relayed by the
//      user, not verified against this repo (it isn't a TestStatus literal
//      anywhere in NeuralChek; it may be a backend-side classification of
//      GpsTests.kt's FAIL + "gps_indoor_hint" / "لا توجد إشارة" combination —
//      out of scope to confirm here, flagged as an unverified relay per
//      القاعدة ٢٩).
//
// "لا تسأل ثانية" for stage 1 needs no extra code: ActivityResultContracts
// .RequestMultiplePermissions() is a thin wrapper over the platform's own
// requestPermissions() — once the user has permanently denied a permission
// (system-tracked, not app-tracked), the OS itself returns denied immediately
// without showing any UI, on every future call. Stage 2 has no OS-level
// "don't ask again" concept (it's a device toggle, not an app permission), so
// re-checking on ON_RESUME after "افتح إعدادات الموقع" is this gate's own job.
// ─────────────────────────────────────────────────────────────────────────────
/**
 * Pure decision behind [ScanPermissionsGate]'s stage 2: whether the
 * location-service ("خدمة الموقع") notice should show, given the current
 * GPS_PROVIDER on/off state (ScanPermissionsGate.isLocationServiceOn()'s
 * result). Extracted for the same reason as ManualTestDialogs.kt's
 * micPermissionResultToStatus: when this was written the unit-test setup lacked
 * Robolectric (a test dependency since 3284cca), so LocationManager.isProviderEnabled() itself could not run
 * under plain JUnit (see ScanPermissionsGateTest) — this function IS the
 * actual decision the gate calls, not a re-implementation of it.
 */
internal fun shouldShowLocationServiceNotice(locationServiceOn: Boolean): Boolean = !locationServiceOn

@Composable
private fun ScanPermissionsGate() {
    val context = androidx.compose.ui.platform.LocalContext.current

    fun isGranted(permission: String) =
        androidx.core.content.ContextCompat.checkSelfPermission(context, permission) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

    // Same check GpsTests.kt's L-01 (testGpsAvailable) already makes —
    // isProviderEnabled(GPS_PROVIDER), not the broader LocationManagerCompat
    // .isLocationEnabled() (which would also count network-only location
    // mode as "on" while GPS itself stays off, understating the problem
    // these three tests actually have).
    fun isLocationServiceOn(): Boolean {
        val lm = context.getSystemService(android.content.Context.LOCATION_SERVICE)
            as? android.location.LocationManager
        return try {
            lm?.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) == true
        } catch (_: Exception) {
            false
        }
    }

    // rememberSaveable so a configuration change (rotation) doesn't re-fire
    // the batch request mid-flight; a fresh PreScanScreen composition (a new
    // scan being started — from Home or from a joined session) does re-run
    // it, which is the intended "once per scan-start attempt" cadence.
    var hasRequested by androidx.compose.runtime.saveable.rememberSaveable {
        androidx.compose.runtime.mutableStateOf(false)
    }
    var deniedPermissions by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf<List<net.neuralcheck.presentation.permissions.ScanRuntimePermission>>(emptyList())
    }
    var showDeniedNotice by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var permissionStageDone by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var showLocationServiceNotice by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    fun enterLocationServiceStage() {
        permissionStageDone = true
        showLocationServiceNotice = shouldShowLocationServiceNotice(isLocationServiceOn())
    }

    val permLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val denied = net.neuralcheck.presentation.permissions.ScanRuntimePermission.ALL
            .filter { results[it.androidPermission] == false }
        deniedPermissions = denied
        if (denied.isNotEmpty()) {
            showDeniedNotice = true
        } else {
            enterLocationServiceStage()
        }
    }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (hasRequested) return@LaunchedEffect
        hasRequested = true
        // Android 12+ (API 31+) platform requirement, not derived from any
        // test in this repo: requesting ACCESS_FINE_LOCATION without also
        // requesting ACCESS_COARSE_LOCATION in the SAME call can make the
        // system silently ignore the FINE request (both are already
        // declared in AndroidManifest.xml:7-8). COARSE has no
        // ScanRuntimePermission entry of its own — no test here gates on it
        // independently of FINE, so nothing would be "skipped" by its denial
        // that FINE's entry doesn't already cover.
        val missing = net.neuralcheck.presentation.permissions.ScanRuntimePermission.ALL
            .filter { !isGranted(it.androidPermission) }
        if (missing.isNotEmpty()) {
            val toRequest = missing.map { it.androidPermission }.toMutableList()
            if (missing.contains(net.neuralcheck.presentation.permissions.ScanRuntimePermission.LOCATION)) {
                toRequest += android.Manifest.permission.ACCESS_COARSE_LOCATION
            }
            permLauncher.launch(toRequest.toTypedArray())
        } else {
            enterLocationServiceStage()
        }
    }

    // On return from either "افتح الإعدادات" (stage 1) or "افتح إعدادات
    // الموقع" (stage 2): re-check silently on ON_RESUME. Does NOT re-launch
    // the OS permission dialog and does NOT re-open Settings on its own —
    // only refreshes which notice (if any) should still be showing.
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                if (!permissionStageDone) return@LifecycleEventObserver
                deniedPermissions = deniedPermissions.filter { !isGranted(it.androidPermission) }
                if (deniedPermissions.isEmpty()) showDeniedNotice = false
                if (showLocationServiceNotice && !shouldShowLocationServiceNotice(isLocationServiceOn())) {
                    showLocationServiceNotice = false
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (showDeniedNotice && deniedPermissions.isNotEmpty()) {
        AlertDialog(
            containerColor = CardDark,
            onDismissRequest = { showDeniedNotice = false; enterLocationServiceStage() },
            title = { Text(stringResource(R.string.scan_permission_denied_title), color = TextPrimary) },
            text = {
                Column {
                    Text(stringResource(R.string.scan_permission_denied_intro), color = TextSecondary)
                    Spacer(Modifier.size(8.dp))
                    deniedPermissions.forEach { perm ->
                        Text(
                            "• ${stringResource(perm.labelRes)} — ${perm.autoNotTestedIds.joinToString("، ")}",
                            color = TextSecondary
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    context.startActivity(
                        android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = android.net.Uri.fromParts("package", context.packageName, null)
                        }
                    )
                    showDeniedNotice = false
                    enterLocationServiceStage()
                }) {
                    Text(stringResource(R.string.scan_permission_open_settings), color = NeuralGreen)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeniedNotice = false; enterLocationServiceStage() }) {
                    Text(stringResource(R.string.scan_permission_continue), color = TextSecondary)
                }
            }
        )
    }

    if (permissionStageDone && showLocationServiceNotice) {
        AlertDialog(
            containerColor = CardDark,
            onDismissRequest = { showLocationServiceNotice = false },
            title = { Text(stringResource(R.string.scan_location_service_off_title), color = TextPrimary) },
            text = { Text(stringResource(R.string.scan_location_service_off_body), color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    context.startActivity(android.content.Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                    showLocationServiceNotice = false
                }) {
                    Text(stringResource(R.string.scan_location_service_open_settings), color = NeuralGreen)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLocationServiceNotice = false }) {
                    Text(stringResource(R.string.scan_permission_continue), color = TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun ConditionCard(condition: PreScanCondition) {
    val (icon, color) = conditionIconAndColor(condition.level)
    NCCard(modifier = Modifier.fillMaxWidth(), contentPadding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(color.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) { Icon(icon, null, tint = color) }
            Spacer(Modifier.size(14.dp))
            Text(
                text = condition.text,
                style = MaterialTheme.typography.bodyLarge,
                color = TextPrimary
            )
        }
    }
}

private fun conditionIconAndColor(level: ConditionLevel): Pair<ImageVector, androidx.compose.ui.graphics.Color> =
    when (level) {
        ConditionLevel.GREEN -> Icons.Default.CheckCircle to StatusPass
        ConditionLevel.YELLOW -> Icons.Default.Warning to StatusWarning
        ConditionLevel.RED -> Icons.Default.Cancel to StatusFail
    }

data class PreScanCondition(val text: String, val level: ConditionLevel)
enum class ConditionLevel { GREEN, YELLOW, RED }
