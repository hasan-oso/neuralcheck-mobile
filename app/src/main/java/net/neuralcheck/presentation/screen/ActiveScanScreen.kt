package net.neuralcheck.presentation.screen

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.patrykandpatrick.vico.compose.axis.axisLabelComponent
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.line.lineChart
import com.patrykandpatrick.vico.compose.chart.line.lineSpec
import com.patrykandpatrick.vico.compose.component.lineComponent
import com.patrykandpatrick.vico.compose.component.shape.shader.fromBrush
import com.patrykandpatrick.vico.core.DefaultAlpha
import com.patrykandpatrick.vico.core.axis.AxisItemPlacer
import com.patrykandpatrick.vico.core.chart.DefaultPointConnector
import com.patrykandpatrick.vico.core.chart.values.AxisValuesOverrider
import com.patrykandpatrick.vico.core.component.shape.shader.DynamicShaders
import com.patrykandpatrick.vico.core.entry.ChartEntryModelProducer
import com.patrykandpatrick.vico.core.entry.FloatEntry
import net.neuralcheck.R
import net.neuralcheck.data.remote.SessionLeaver
import net.neuralcheck.presentation.components.GradientProgressBar
import net.neuralcheck.presentation.components.NCCard
import net.neuralcheck.presentation.navigation.leaveToScanOrigin
import net.neuralcheck.presentation.navigation.scanOriginRoute
import net.neuralcheck.presentation.util.KeepScreenOn
import net.neuralcheck.presentation.viewmodel.LeaveSessionViewModel
import net.neuralcheck.presentation.theme.CardBorder
import net.neuralcheck.presentation.theme.CardDark
import net.neuralcheck.presentation.theme.DeepSpace
import net.neuralcheck.presentation.theme.NeonCyan
import net.neuralcheck.presentation.theme.NeuralGreen
import net.neuralcheck.presentation.theme.OutfitFamily
import net.neuralcheck.presentation.theme.SkyCyan
import net.neuralcheck.presentation.theme.StatusFail
import net.neuralcheck.presentation.theme.StatusPass
import net.neuralcheck.presentation.theme.StatusWarning
import net.neuralcheck.presentation.theme.Surface
import net.neuralcheck.presentation.theme.TextMuted
import net.neuralcheck.presentation.theme.TextPrimary
import net.neuralcheck.presentation.theme.TextSecondary
import net.neuralcheck.util.NumberFormat

@Composable
fun ActiveScanScreen(
    navController: NavController,
    scanType: String,
    linkedScanId: String?,
    viewModel: net.neuralcheck.presentation.viewmodel.ActiveScanViewModel = androidx.hilt.navigation.compose.hiltViewModel()
) {
    val scanState by viewModel.scanState.collectAsState()
    // fieldsVersion 69 (SN-04): the "cover the light sensor" request of the sensor test.
    val coverPromptActive by viewModel.coverPromptActive.collectAsState()

    // Batch 2, item 2: the customer's leave (D2: the stop dialog and Back send left_by_customer for a session scan) and the owner's cancellation reaching this
    // device (D5: the scan stops, the approved text shows, then D3).
    val leaveViewModel: LeaveSessionViewModel = androidx.hilt.navigation.compose.hiltViewModel()
    val leaveState by leaveViewModel.state.collectAsState()
    val sessionCancelledByOwner by viewModel.sessionCancelledByOwner.collectAsState()

    // A-P6-09 (the scanned phone shows a progress bar alone): which parts of the body this scan draws - the charts and the two live-reading tiles (temperature, CPU usage) only for a scan known to have
    // started in no session, the evaluation card only while the technician's form is not live, the one sentence only in a session scan with the form live. The decision is ScannedPhoneScreen.parts,
    // in one place; this screen only obeys it.
    val startedInSession by viewModel.scanStartedInSession.collectAsState()
    val scanParts = ScannedPhoneScreen.parts(startedInSession, TECH_FORM_TEXTS_APPROVED)

    // V2 fix #37: keep screen on during the scan. The user is
    // expected to watch the 8-minute progress, so screen-off
    // would be a bad experience. Foreground service + wakelock
    // keep the CPU/sampling alive even with screen off, but
    // visibility matters for trust ("is it actually working?").
    //
    // fix/keep-screen-on: the hold is no longer keyed on
    // `isScanRunning`. That dropped the flag the instant the scan
    // completed — which is the same instant we navigate to the
    // questions screen, so the technician was answering on a screen
    // that had just been handed back to the system timeout. The flag
    // now lives for as long as this screen is composed, and the
    // reference count in KeepScreenOn hands it over to
    // ManualTestsScreen without a gap.
    KeepScreenOn()

    // fieldsVersion 71 (A-P6-13): hold the screen's brightness constant while the scan runs and report what came of it to the footer (BrightnessLock.VALUE is not decided
    // yet, so nothing is asked of the window and the report says not_attempted:value_not_decided; PENDING: «معلّق: مواصفة غائبة»).
    val brightnessLockViewModel: net.neuralcheck.presentation.viewmodel.BrightnessLockViewModel = androidx.hilt.navigation.compose.hiltViewModel()
    net.neuralcheck.presentation.util.BrightnessLockEffect { brightnessLockViewModel.report(it) }

    // feat/technician-evaluation: the scan UUID arrives with the FIRST
    // Running emission (service mints it before any phase), letting the
    // evaluation card persist answers keyed by scanId from second one.
    var currentScanId by remember { mutableStateOf("") }
    // Second, independent ViewModel: deliberately observes NO scan flow —
    // its state changes only on technician taps (measurement-safety
    // contract, see TechnicianEvaluationViewModel docs).
    val evalViewModel: net.neuralcheck.presentation.viewmodel.TechnicianEvaluationViewModel =
        androidx.hilt.navigation.compose.hiltViewModel()

    var progress by remember { mutableFloatStateOf(0f) }
    var phase by remember { mutableIntStateOf(1) }
    var v2PhaseId by remember { mutableStateOf<Int?>(null) }
    var elapsedSec by remember { mutableIntStateOf(0) }
    var batteryTemp by remember { mutableFloatStateOf(30f) }
    var cpuTemp by remember { mutableFloatStateOf(-1f) }
    var gpuTemp by remember { mutableFloatStateOf(-1f) }
    var cpuUsage by remember { mutableFloatStateOf(0f) }
    var batteryLevel by remember { mutableIntStateOf(85) }
    var tempHistory by remember { mutableStateOf<List<Float>>(emptyList()) }
    var cpuHistory by remember { mutableStateOf<List<Float>>(emptyList()) }
    var batteryTempHistory by remember { mutableStateOf<List<Float>>(emptyList()) }
    // V2 chart fix: which temperature source is plotted in tempHistory.
    // ViewModel picks cpu_temp → gpu_temp → battery_temp by availability.
    // UI reflects the choice in chart title + y-axis range + header value.
    var tempChartSource by remember { mutableStateOf("battery") }
    var showStopDialog by remember { mutableStateOf(false) }
    var showPowerSaveDialog by remember { mutableStateOf(false) }
    var startupErrorMessage by remember { mutableStateOf<String?>(null) }

    // Order 2026-10-15 (batch item 3, "BackHandler in the scan screen"): the system Back used to pop this screen while the scan
    // kept running in its foreground service - only the stop button, behind its confirm dialog, stopped it. While a scan is
    // Running, Back is now the SAME request as that button: it opens the SAME confirm dialog (an existing text, no new one),
    // and the confirm stops the scan and leaves exactly as the button does. Not running (idle, errored, completed) Back is
    // unchanged. Notifying the server of a session scan that is left is NOT done here: it waits for the server contract.
    BackHandler(
        enabled = scanState is net.neuralcheck.presentation.viewmodel.ActiveScanViewModel.ScanUiState.Running
    ) {
        showStopDialog = true
    }

    // Seed the producers with a single zero-entry so Vico's Chart never
    // sees the [ChartValuesProvider.Empty] sentinel — that singleton's
    // getChartValues() throws IllegalStateException at first composition,
    // which on Samsung S24 Ultra and other devices was killing the
    // ActiveScanScreen before the first sampler tick had a chance to
    // populate real data via setEntries(). The seed value is overwritten
    // in-place by the LaunchedEffect once chartHistory.size > 1, so it
    // never appears as a visible point on screen.
    val cpuProducer = remember {
        ChartEntryModelProducer(listOf(FloatEntry(0f, 0f)))
    }
    val tempProducer = remember {
        ChartEntryModelProducer(listOf(FloatEntry(0f, 0f)))
    }

    // V2 fix #38: scroll specs are now local to each Chart Composable
    // (CpuUsageChart / BatteryTempChart) since ChartScrollSpec<Model>
    // generic doesn't pass cleanly across function boundaries.

    // Phase 1.11 H2 fix — Layer 1 of the defense in depth.
    //
    // The previous key `Unit` made this effect re-fire whenever the
    // Composable left and re-entered composition (configuration change,
    // process death + restore, navigation pop-back). Each re-fire
    // dispatched a fresh ACTION_START to StressTestService; if the
    // service had already completed the prior scan (currentScanId == null
    // and _scanState != Running), the service-level guard at line 282
    // did not catch it and a brand-new scan started with a new UUID →
    // a duplicate reports/{scanId} doc landed in Firestore.
    //
    // Keying on `viewModel` instead binds the effect's lifecycle to the
    // ViewModel identity. The ViewModel is scoped to the active_scan
    // NavBackStackEntry, so recompositions inside the same entry keep
    // the same instance and do NOT re-fire. A brand-new active_scan
    // entry (which is the only legitimate "start another scan" path)
    // produces a different ViewModel and correctly re-fires.
    //
    // Layer 2 (the ViewModel's in-flight latch) catches the residual
    // case where the Composable does still leave and re-enter
    // composition with the SAME ViewModel (e.g., a stray re-creation
    // path we haven't enumerated).
    LaunchedEffect(viewModel) { viewModel.startScan(scanType, linkedScanId) }

    LaunchedEffect(scanState) {
        when (val state = scanState) {
            is net.neuralcheck.presentation.viewmodel.ActiveScanViewModel.ScanUiState.Running -> {
                progress = state.progress
                phase = state.phase
                currentScanId = state.scanId
                v2PhaseId = state.v2PhaseId
                elapsedSec = state.elapsed
                batteryTemp = state.batteryTemp
                cpuTemp = state.cpuTemp
                gpuTemp = state.gpuTemp
                cpuUsage = state.cpuUsage
                batteryLevel = state.batteryLevel
                tempHistory = state.tempHistory
                cpuHistory = state.cpuHistory
                batteryTempHistory = state.batteryTempHistory
                tempChartSource = state.tempChartSource
            }
            is net.neuralcheck.presentation.viewmodel.ActiveScanViewModel.ScanUiState.Completed -> {
                // Route through the manual-tests screen so the AI / RuleBased
                // engine sees a populated `manualResults` array instead of an
                // empty list. The launch flavor's old direct-to-report jump
                // was the bug we're fixing here. Pop the whole scan funnel
                // (pre_scan + active_scan) off the back stack — otherwise
                // "back" from later screens lands on pre_scan and kicks off
                // a second scan.
                //
                // Order 2026-10-10 (permanent named log lines): ONE line, here, per scan — what the
                // card's gates were when the automated part ended (an event handler, not a composition).
                evalViewModel.logHandoffToManualTests(state.scanId)
                // Batch 2, item 2 (D3): what is under the report is removed down to where the funnel started (login on a customer
                // device, home otherwise) - before, the literal "home" removed nothing on a device whose start is login.
                navController.navigate("manual_tests/${state.scanId}") {
                    popUpTo(navController.scanOriginRoute())
                }
            }
            is net.neuralcheck.presentation.viewmodel.ActiveScanViewModel.ScanUiState.Error -> {
                when {
                    state.message == "POWER_SAVE_MODE" -> showPowerSaveDialog = true
                    // Any other failure surfaces in a generic dialog so the
                    // user sees an explanation instead of a frozen scan
                    // screen. The error code (FGS_START_DENIED:..., BIND_
                    // FAILED:..., NOTIFICATION_SERVICE_UNAVAILABLE,
                    // INIT_FAILED:..., FOREGROUND_DENIED) is included so
                    // a screenshot makes the cause obvious without logcat.
                    else -> startupErrorMessage = state.message
                }
            }
            else -> {}
        }
    }

    // feat/technician-evaluation: one-shot attach of the questions card to
    // this scan. Fires exactly once when scanId first becomes non-blank
    // (key change "" → uuid); bind() is additionally latched internally,
    // so this is NOT a periodic recomposition source.
    LaunchedEffect(currentScanId) {
        if (currentScanId.isNotBlank()) evalViewModel.bind(currentScanId)
    }

    // V2 fix #41: outer LaunchedEffects للـ producers أُزيلت.
    // كل Composable للـ chart (CpuUsageChart / BatteryTempChart) يدير
    // الآن producer.setEntries بنفسه مع rolling window 60/30. الـ
    // history الكاملة تبقى في الـ ViewModel (cap 1000) للتقرير.

    val elapsedMin = elapsedSec / 60

    DisposableEffect(Unit) {
        onDispose {
            net.neuralcheck.service.stress.GpuFpsTracker.gpuLoadGenerator?.stop()
            net.neuralcheck.service.stress.GpuFpsTracker.gpuLoadGenerator = null
        }
    }

    // V2 phase labels (8 phases, indexed 0..7). Used when v2PhaseId
    // is non-null; the V1 5-phase labels below stay as fallback for
    // V1-flavor builds + legacy reports.
    val v2PhaseNames = listOf(
        stringResource(R.string.scan_v2_phase_init_profiling),
        stringResource(R.string.scan_v2_phase_storage_stress),
        stringResource(R.string.scan_v2_phase_memory_stress),
        stringResource(R.string.scan_v2_phase_cpu_sustained),
        stringResource(R.string.scan_v2_phase_gpu_sustained),
        stringResource(R.string.scan_v2_phase_combined_peak),
        stringResource(R.string.scan_v2_phase_burst_pattern),
        stringResource(R.string.scan_v2_phase_cooldown)
    )
    val phaseNames = listOf(
        stringResource(R.string.scan_phase_1),
        stringResource(R.string.scan_phase_2),
        stringResource(R.string.scan_phase_3),
        stringResource(R.string.scan_phase_4),
        stringResource(R.string.scan_phase_5)
    )

    // Fallback chain for the live temperature tile: CPU → GPU → Battery.
    // On devices like SM-G977N the /sys/class/thermal CPU zone is blocked
    // by SELinux, so this keeps the tile useful instead of showing "—".
    val hasCpuTemp = cpuTemp > 0f
    val hasGpuTemp = gpuTemp > 0f
    val hasBatteryTemp = batteryTemp > 0f
    val displayTemp: Float? = when {
        hasCpuTemp -> cpuTemp
        hasGpuTemp -> gpuTemp
        hasBatteryTemp -> batteryTemp
        else -> null
    }
    val tempLabel = when {
        hasCpuTemp -> stringResource(R.string.scan_cpu_temp)
        hasGpuTemp -> stringResource(R.string.scan_gpu_temp)
        hasBatteryTemp -> stringResource(R.string.scan_battery_temp)
        else -> stringResource(R.string.scan_temperature)
    }
    val tempTileColor = when {
        displayTemp == null -> TextMuted
        displayTemp > 85f -> StatusFail
        displayTemp > 60f -> StatusWarning
        else -> StatusPass
    }
    val degree = stringResource(R.string.degree_celsius)

    Scaffold(
        containerColor = DeepSpace,
        topBar = {
            if (coverPromptActive) CoverSensorPrompt()
        },
        bottomBar = {
            androidx.compose.material3.Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding(),
                color = DeepSpace,
                tonalElevation = 8.dp
            ) {
                Button(
                    onClick = { showStopDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StatusFail.copy(alpha = 0.12f),
                        contentColor = StatusFail
                    ),
                    border = BorderStroke(1.dp, StatusFail.copy(alpha = 0.4f))
                ) {
                    Icon(Icons.Default.Stop, null, tint = StatusFail)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.btn_cancel_scan),
                        style = MaterialTheme.typography.titleMedium,
                        color = StatusFail
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // feat/technician-evaluation: blind technician questions, shown
            // only on entitled QR sessions (renders nothing otherwise). At
            // the top so the technician can answer without scrolling while
            // the scan fills the waiting time. Static card — recomposes on
            // taps only; shows nothing of our measurements by design.
            // A-P6-09: the card leaves this screen when the technician's form replaces it (PLAN-06:1241) - the same switch, ScannedPhoneScreen.parts.
            if (scanParts.evaluationCard) TechnicianEvaluationCard(evalViewModel)

            // Phase label + 5-dot indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.scan_phase),
                        style = MaterialTheme.typography.labelMedium,
                        color = TextMuted
                    )
                    Text(
                        text = v2PhaseId?.let { id -> v2PhaseNames.getOrNull(id) }
                            ?: phaseNames.getOrElse(phase - 1) { stringResource(R.string.scan_phase_unknown) },
                        style = MaterialTheme.typography.titleLarge,
                        color = NeuralGreen
                    )
                }
                Row {
                    repeat(5) { index ->
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(
                                    color = if (index + 1 == phase) NeuralGreen
                                            else if (index + 1 < phase) NeuralGreen.copy(alpha = 0.3f)
                                            else TextMuted.copy(alpha = 0.25f),
                                    shape = CircleShape
                                )
                        )
                        if (index < 4) Spacer(Modifier.width(6.dp))
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            // Progress row — all digits forced to Latin for consistency
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = NumberFormat.toLatin(stringResource(R.string.scan_progress_minute_fmt, elapsedMin)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Text(
                    text = NumberFormat.percent(progress, 0),
                    fontFamily = OutfitFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 26.sp,
                    color = NeuralGreen
                )
            }
            Spacer(Modifier.height(8.dp))
            GradientProgressBar(progress = progress / 100f)
            // A-P6-09 (ف١٢): the one sentence of a session scan (FormText.ScanInProgress, A4-1: approved by delegation 2026-10-23, Arabic and Turkish) - drawn only while the form is live and the labels exist
            if (scanParts.inProgressSentence) {
                val sentenceLabels = technicianFormLabels()
                if (sentenceLabels != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = sentenceLabels(FormText.ScanInProgress),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }
            Spacer(Modifier.height(14.dp))

            // Rotating tip under the progress bar — keeps the user oriented
            // on what's safe / what they should be doing while the 11-minute
            // stress test runs.
            ScanTipsRotator(
                tips = listOf(
                    R.string.scan_tip_no_close,
                    R.string.scan_tip_no_charger,
                    R.string.scan_tip_flat,
                    R.string.scan_tip_ai
                )
            )

            Spacer(Modifier.height(16.dp))

            // V2 fix #38: redesigned charts. الرسم الأول للمعالج بمنحنى
            // ناعم cubic interpolation، والثاني لحرارة البطارية بـ
            // stepped (cubic=0) لأن EXTRA_TEMPERATURE تُحدَّث نادراً.
            // الرسمان لهما auto-scroll + LTR override + danger zone.
            // A-P6-09 (PLAN-06:954, ف١٢: «والرسمان يخرجان في الجلسة»): the two charts are drawn only for a scan known to have started in no session - a session's scan (the scanned phone, watched
            // by the technician who evaluates blind) shows no chart (ScannedPhoneScreen.parts). The measuring is untouched: the history is still collected by the view model for the report.
            if (scanParts.charts) {
                CpuUsageChart(
                    cpuHistory = cpuHistory,
                    currentValue = cpuUsage,
                    cpuProducer = cpuProducer,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(10.dp))

                BatteryTempChart(
                    batteryTempHistory = batteryTempHistory,
                    currentValue = batteryTemp,
                    tempProducer = tempProducer,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(14.dp))
            }

            // Live metric grid (2x2) — each tile shows a distinct signal.
            // Battery temperature is not duplicated: the single temperature
            // tile picks the best available source (CPU → GPU → Battery) and
            // labels itself accordingly. The fourth tile shows time remaining
            // so the user can judge the scan's pace without doing mental math.
            // A-P6-09 follow-up (coordinator's decision 2026-10-21): the two LIVE READINGS - the temperature tile and the CPU-usage tile - are drawn only for a scan known to have started in no
            // session (ScannedPhoneScreen.parts.liveTiles): a session's scan is watched by the technician who evaluates blind, and these two tiles show the same live measurement the charts did.
            // The tiles of time remaining and battery level stay on every scan (functional, not a reading of the scan's result).
            if (scanParts.liveTiles) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricCard(
                        modifier = Modifier.weight(1f),
                        label = tempLabel,
                        value = if (displayTemp != null) NumberFormat.format(displayTemp, 0) + degree else "—",
                        color = tempTileColor
                    )
                    MetricCard(
                        modifier = Modifier.weight(1f),
                        label = stringResource(R.string.scan_cpu_usage),
                        value = NumberFormat.percent(cpuUsage, 0),
                        color = when {
                            cpuUsage >= 70f -> StatusPass
                            cpuUsage >= 30f -> StatusWarning
                            else -> TextMuted
                        }
                    )
                }
                Spacer(Modifier.height(10.dp))
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val totalScanSec = if (v2PhaseId != null) 480 else 690
                val remainingSec = (totalScanSec - elapsedSec).coerceAtLeast(0)
                val mm = remainingSec / 60
                val ss = remainingSec % 60
                MetricCard(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.scan_time_remaining),
                    value = NumberFormat.toLatin("%02d:%02d".format(mm, ss)),
                    color = TextPrimary
                )
                MetricCard(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.category_battery),
                    value = NumberFormat.percent(batteryLevel),
                    color = if (batteryLevel < 15) StatusFail else StatusPass
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    // Batch 2, item 2 (D2, D3): a leave that the server confirmed stops the scan and goes back to where the funnel started
    LaunchedEffect(leaveState) {
        if (leaveState is LeaveSessionViewModel.State.Left) {
            showStopDialog = false
            viewModel.stopScan()
            navController.leaveToScanOrigin()
        }
    }

    if (showStopDialog) {
        val leaving = leaveState is LeaveSessionViewModel.State.InProgress
        val leaveFailure = (leaveState as? LeaveSessionViewModel.State.Failed)?.message
        val dialogText: (@Composable () -> Unit)? =
            if (leaving) {
                { CircularProgressIndicator(color = NeuralGreen, strokeWidth = 2.dp, modifier = Modifier.size(20.dp)) }
            } else if (leaveFailure != null) {
                { Text(leaveFailure, color = StatusFail) }
            } else null
        AlertDialog(
            containerColor = CardDark,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            onDismissRequest = { if (!leaving) { showStopDialog = false; leaveViewModel.dismiss() } },
            title = { Text(stringResource(R.string.scan_cancel_confirm)) },
            text = dialogText,
            confirmButton = {
                TextButton(enabled = !leaving, onClick = {
                    // A scan that started in a session leaves that session (D2): the server must confirm before the scan stops. A personal scan
                    // (no recorded session) stops and leaves exactly as it always did.
                    val sessionId = leaveViewModel.sessionOfScan(currentScanId)
                    if (sessionId == null) {
                        showStopDialog = false
                        viewModel.stopScan()
                        navController.popBackStack()
                    } else {
                        leaveViewModel.leave(SessionLeaver.Phase.SCAN, sessionId, currentScanId)
                    }
                }) { Text(stringResource(R.string.btn_confirm), color = StatusFail) }
            },
            dismissButton = {
                TextButton(enabled = !leaving, onClick = { showStopDialog = false; leaveViewModel.dismiss() }) {
                    Text(stringResource(R.string.btn_cancel), color = TextSecondary)
                }
            }
        )
    }

    // Batch 2, item 2 (D5): the owner cancelled the session this scan is in. The view model already stopped the scan; the text is the approved
    // SESSION_CANCELLED one, and the way out is D3's.
    if (sessionCancelledByOwner) {
        AlertDialog(
            containerColor = CardDark,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            onDismissRequest = { navController.leaveToScanOrigin() },
            text = { Text(stringResource(R.string.session_join_cancelled)) },
            confirmButton = {
                TextButton(onClick = { navController.leaveToScanOrigin() }) {
                    Text(stringResource(R.string.btn_ok), color = NeuralGreen)
                }
            }
        )
    }

    if (showPowerSaveDialog) {
        AlertDialog(
            containerColor = CardDark,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            onDismissRequest = { showPowerSaveDialog = false; navController.popBackStack() },
            title = { Text(stringResource(R.string.power_save_dialog_title)) },
            text = { Text(stringResource(R.string.power_save_dialog_message)) },
            confirmButton = {
                TextButton(onClick = { showPowerSaveDialog = false; navController.popBackStack() }) {
                    Text(stringResource(R.string.btn_ok), color = NeuralGreen)
                }
            }
        )
    }

    // Startup / safety-stop error dialog.
    //
    // fix/prescan-battery-gate: known reason tokens now get a localized
    // human message — previously the raw token ("BATTERY_TOO_LOW",
    // "battery_temp_critical", ...) was interpolated into the body and read
    // like an untranslated string key. Two families are mapped:
    //   • pre-flight rejections from StressTestService.startScan
    //     (BATTERY_TOO_LOW[:level], DEVICE_TOO_HOT, CHARGER_CONNECTED,
    //     STORAGE_FULL, RAM_INSUFFICIENT) — the battery one carries the
    //     measured level after ':' so the body can quote "requires
    //     ≥ScanBatteryWindow.MIN_PCT% — currently N%";
    //   • mid-scan SafetyMonitor stops (battery_temp_critical,
    //     cpu_temp_critical, excessive_drain, battery_level_critical) —
    //     these wire up the long-dormant scan_safety_* strings and get the
    //     scan_safety_stop title (the scan DID start; "couldn't start" would
    //     be wrong).
    // Unknown technical codes (FGS_START_DENIED:*, BIND_FAILED:*,
    // INIT_FAILED:*, NOTIFICATION_SERVICE_UNAVAILABLE, FOREGROUND_DENIED,
    // v2_error, ...) keep the deliberate raw-code body: gibberish to the
    // user, but a support screenshot stays self-explanatory.
    val errorMsg = startupErrorMessage
    if (errorMsg != null) {
        val isMidScanSafetyStop = errorMsg in setOf(
            "battery_temp_critical", "cpu_temp_critical",
            "excessive_drain", "battery_level_critical"
        )
        val dialogTitle = if (isMidScanSafetyStop) {
            stringResource(R.string.scan_safety_stop)
        } else {
            stringResource(R.string.scan_start_failed_title)
        }
        val minBatteryPct = net.neuralcheck.service.stress.ScanBatteryWindow.MIN_PCT
        // fieldsVersion 71 (A5h-2, approved by delegation 2026-10-23): a refusal ABOVE the window carries its level ("BATTERY_TOO_HIGH:<level>") and gets the window's text, both limits from ScanBatteryWindow
        // (rule 24: no number is written in the text); one without a level keeps the raw-code body below - the absence stays visible (rule 22)
        val aboveWindowLevel = batteryAboveWindowLevel(errorMsg)
        val dialogBody = when {
            aboveWindowLevel != null -> NumberFormat.toLatin(
                stringResource(
                    R.string.scan_error_battery_window_fmt,
                    minBatteryPct, net.neuralcheck.service.stress.ScanBatteryWindow.MAX_PCT, aboveWindowLevel
                )
            )
            errorMsg.startsWith("BATTERY_TOO_LOW") -> {
                val currentPct = errorMsg
                    .substringAfter(':', missingDelimiterValue = "")
                    .toIntOrNull()
                if (currentPct != null) {
                    NumberFormat.toLatin(
                        stringResource(
                            R.string.scan_error_battery_too_low_fmt,
                            minBatteryPct, currentPct
                        )
                    )
                } else {
                    NumberFormat.toLatin(
                        stringResource(
                            R.string.scan_error_battery_too_low_generic,
                            minBatteryPct
                        )
                    )
                }
            }
            errorMsg == "DEVICE_TOO_HOT" ->
                stringResource(R.string.scan_error_device_too_hot)
            errorMsg == "CHARGER_CONNECTED" ->
                stringResource(R.string.scan_error_charger_connected)
            errorMsg == "STORAGE_FULL" ->
                stringResource(R.string.scan_error_storage_full)
            errorMsg == "RAM_INSUFFICIENT" ->
                stringResource(R.string.scan_error_ram_insufficient)
            errorMsg == "battery_temp_critical" ->
                stringResource(R.string.scan_safety_battery_temp)
            errorMsg == "cpu_temp_critical" ->
                stringResource(R.string.scan_safety_cpu_temp)
            errorMsg == "excessive_drain" ->
                stringResource(R.string.scan_safety_battery_drain)
            errorMsg == "battery_level_critical" ->
                stringResource(R.string.scan_safety_battery_low)
            else ->
                stringResource(R.string.scan_start_failed_body_fmt, errorMsg)
        }
        AlertDialog(
            containerColor = CardDark,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            onDismissRequest = {
                startupErrorMessage = null
                navController.popBackStack()
            },
            title = { Text(dialogTitle) },
            text = { Text(dialogBody) },
            confirmButton = {
                TextButton(onClick = {
                    startupErrorMessage = null
                    navController.popBackStack()
                }) {
                    Text(stringResource(R.string.btn_ok), color = NeuralGreen)
                }
            }
        )
    }
}

/**
 * The level a refusal ABOVE the battery window reports: the service's reason is `BATTERY_TOO_HIGH:<level>` ([net.neuralcheck.service.stress.ScanBatteryWindow.REFUSAL_ABOVE]), and the level is what the
 * dialog quotes. Null when [reason] is not that refusal, or carries no readable level - the dialog then shows the generic body with the raw code, not a text with a made-up number (rule 22).
 */
internal fun batteryAboveWindowLevel(reason: String): Int? =
    if (reason.startsWith(net.neuralcheck.service.stress.ScanBatteryWindow.REFUSAL_ABOVE)) reason.substringAfter(':', missingDelimiterValue = "").toIntOrNull() else null

/**
 * Rotates through [tips] every 5 seconds. Pulled out as its own composable
 * (rather than inlined in the scan column) so the ticker's LaunchedEffect
 * is bound to just this section — nav changes or scan-state updates at the
 * parent level don't reset the rotation.
 */
@Composable
private fun ScanTipsRotator(tips: List<Int>) {
    if (tips.isEmpty()) return
    var index by remember { mutableIntStateOf(0) }
    LaunchedEffect(tips.size) {
        while (true) {
            kotlinx.coroutines.delay(5_000L)
            index = (index + 1) % tips.size
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Surface, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(NeuralGreen, CircleShape)
        )
        AnimatedContent(
            targetState = index,
            transitionSpec = {
                fadeIn() togetherWith fadeOut()
            },
            label = "scan_tip"
        ) { i ->
            Text(
                text = stringResource(tips[i]),
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun ChartHeader(title: String, dotColor: Color, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(dotColor))
        Spacer(Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            fontFamily = OutfitFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = dotColor
        )
    }
}

@Composable
private fun CollectingPlaceholder() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(R.string.scan_collecting),
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted
        )
    }
}

@Composable
private fun MetricCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    color: Color
) {
    Column(
        modifier = modifier
            .height(78.dp)
            .background(Surface, RoundedCornerShape(16.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = TextMuted
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = value,
            fontFamily = OutfitFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            color = color
        )
    }
}

// ═══════════════════════════════════════════════════════════════════
// V2 fix #38 — Redesigned charts. CpuUsageChart بمنحنى cubic ناعم
// (تحديثات سريعة كل 250ms)، BatteryTempChart بـ stepped (cubic=0)
// مع dedup للقيم المتكررة، خط خطر عند 45°C.
//
// كلاهما داخل NCCard متّسق + header (label + min/max + current)،
// LTR override للـ chart، auto-scroll OnModelSizeIncreased.
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun CpuUsageChart(
    cpuHistory: List<Float>,
    currentValue: Float,
    cpuProducer: ChartEntryModelProducer,
    modifier: Modifier = Modifier
) {
    // V2 fix #41: rolling window — نعرض آخر 60 sample فقط
    // (15 ثانية بمعدّل 4Hz). البيانات الكاملة (حتى 1000) محفوظة في
    // cpuHistory وتُرسَل للنموذج وتظهر في التقرير. هذا يلغي الحاجة
    // لـ scroll الذي لم يعمل بشكل موثوق في Vico 1.13.1 (#36، #37).
    val WINDOW_SIZE = 60
    val visibleCpu = remember(cpuHistory.size, cpuHistory.lastOrNull()) {
        cpuHistory.takeLast(WINDOW_SIZE)
    }

    // Push window into producer reactively
    LaunchedEffect(visibleCpu.size, visibleCpu.lastOrNull()) {
        if (visibleCpu.size > 1) {
            cpuProducer.setEntries(
                visibleCpu.mapIndexed { i, v -> FloatEntry(i.toFloat(), v) }
            )
        }
    }

    // min/max من cpuHistory الكامل (ليس visibleCpu) — الـ header
    // يعكس مدى الفحص بأكمله، ليس آخر 15 ثانية فقط.
    val minValue = cpuHistory.minOrNull() ?: 0f
    val maxValue = cpuHistory.maxOrNull() ?: 0f
    NCCard(modifier = modifier, contentPadding = 14.dp) {
        // Header: title + min/max + current value (large)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(NeuralGreen))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.scan_cpu_usage),
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary
                    )
                }
                if (cpuHistory.size > 1) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = NumberFormat.toLatin(stringResource(
                            R.string.scan_chart_min_max_pct,
                            minValue.toInt(), maxValue.toInt()
                        )),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                }
            }
            Text(
                text = "${currentValue.toInt()}%",
                fontFamily = OutfitFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 26.sp,
                color = NeuralGreen
            )
        }
        Spacer(Modifier.height(10.dp))
        // Chart body
        Box(modifier = Modifier.fillMaxWidth().height(160.dp)) {
            key(cpuHistory.size >= 2) {
                if (cpuHistory.size <= 1) {
                    CollectingPlaceholder()
                } else {
                    val cpuAxisOverride = remember {
                        AxisValuesOverrider.fixed(minY = 0f, maxY = 100f)
                    }
                    CompositionLocalProvider(
                        LocalLayoutDirection provides LayoutDirection.Ltr
                    ) {
                        Chart(
                            chart = lineChart(
                                lines = listOf(
                                    lineSpec(
                                        lineColor = NeuralGreen,
                                        lineThickness = 2.5.dp,
                                        lineBackgroundShader = DynamicShaders.fromBrush(
                                            brush = Brush.verticalGradient(
                                                listOf(
                                                    NeuralGreen.copy(alpha = 0.30f),
                                                    NeuralGreen.copy(alpha = 0.0f)
                                                )
                                            )
                                        ),
                                        // cubicStrength=0.5 منحنى ناعم بدون مبالغة
                                        pointConnector = DefaultPointConnector(cubicStrength = 0.5f)
                                    )
                                ),
                                axisValuesOverrider = cpuAxisOverride
                            ),
                            chartModelProducer = cpuProducer,
                            startAxis = rememberStartAxis(
                                label = axisLabelComponent(
                                    color = TextMuted.copy(alpha = 0.7f),
                                    textSize = 10.sp
                                ),
                                axis = null,
                                tick = null,
                                guideline = lineComponent(
                                    color = TextPrimary.copy(alpha = 0.08f),
                                    thickness = 1.dp
                                ),
                                itemPlacer = AxisItemPlacer.Vertical.default(maxItemCount = 5),
                                valueFormatter = { v, _ -> "${v.toInt()}%" }
                            ),
                            bottomAxis = null,  // مخفي — الزمن واضح من progress bar
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BatteryTempChart(
    batteryTempHistory: List<Float>,
    currentValue: Float,
    tempProducer: ChartEntryModelProducer,
    modifier: Modifier = Modifier
) {
    // V2 fix #38: dedup consecutive duplicates. Android EXTRA_TEMPERATURE
    // يُحدَّث كل 30-60 ث، فالـ ViewModel يُكرّر آخر قيمة كل 250ms.
    // الـ chart يحتاج فقط النقاط الفعلية المختلفة.
    val uniqueTemps = remember(batteryTempHistory) {
        batteryTempHistory.fold(mutableListOf<Float>()) { acc, v ->
            if (v > 0f && acc.lastOrNull() != v) acc.add(v)
            acc
        }
    }

    // V2 fix #41: rolling window — آخر 30 قيمة فريدة (تكفي ~15-30
    // دقيقة من تحديثات battery temp بمعدّل 30-60s). لكل فحص 8 دقائق
    // عملياً ~9-16 قيمة فريدة، فالـ window نادراً ما يُقصّ شيئاً.
    val BATTERY_WINDOW = 30
    val visibleTemps = remember(uniqueTemps.size) {
        uniqueTemps.takeLast(BATTERY_WINDOW)
    }

    // min/max من uniqueTemps الكامل (ليس visibleTemps) — تعكس مدى
    // الحرارة طوال الفحص.
    val minValue = uniqueTemps.minOrNull() ?: currentValue
    val maxValue = uniqueTemps.maxOrNull() ?: currentValue
    val tempColor = StatusWarning  // أصفر/برتقالي مميَّز عن CPU الأخضر

    // Push window into producer reactively
    LaunchedEffect(visibleTemps.size, visibleTemps.lastOrNull()) {
        if (visibleTemps.size >= 1) {
            tempProducer.setEntries(
                visibleTemps.mapIndexed { i, v -> FloatEntry(i.toFloat(), v) }
            )
        }
    }

    NCCard(modifier = modifier, contentPadding = 14.dp) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(tempColor))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.scan_battery_temp),
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary
                    )
                }
                if (uniqueTemps.size > 1) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = stringResource(
                            R.string.scan_chart_min_max_temp,
                            NumberFormat.format(minValue, 1), NumberFormat.format(maxValue, 1)
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                }
            }
            Text(
                text = "${"%.1f".format(currentValue)}°C",
                fontFamily = OutfitFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 26.sp,
                color = tempColor
            )
        }
        Spacer(Modifier.height(10.dp))
        Box(modifier = Modifier.fillMaxWidth().height(160.dp)) {
            key(uniqueTemps.size >= 2) {
                if (uniqueTemps.size <= 1) {
                    CollectingPlaceholder()
                } else {
                    val tempAxisOverride = remember {
                        AxisValuesOverrider.fixed(minY = 20f, maxY = 50f)
                    }
                    CompositionLocalProvider(
                        LocalLayoutDirection provides LayoutDirection.Ltr
                    ) {
                        Chart(
                            chart = lineChart(
                                lines = listOf(
                                    lineSpec(
                                        lineColor = tempColor,
                                        lineThickness = 2.5.dp,
                                        lineBackgroundShader = DynamicShaders.fromBrush(
                                            brush = Brush.verticalGradient(
                                                listOf(
                                                    tempColor.copy(alpha = 0.25f),
                                                    tempColor.copy(alpha = 0.0f)
                                                )
                                            )
                                        ),
                                        // cubicStrength=0 = خطوط مستقيمة بين النقاط
                                        // (data طبيعتها قفزات، ليست منحنى مستمر)
                                        pointConnector = DefaultPointConnector(cubicStrength = 0f)
                                    )
                                ),
                                axisValuesOverrider = tempAxisOverride
                            ),
                            chartModelProducer = tempProducer,
                            startAxis = rememberStartAxis(
                                label = axisLabelComponent(
                                    color = TextMuted.copy(alpha = 0.7f),
                                    textSize = 10.sp
                                ),
                                axis = null,
                                tick = null,
                                guideline = lineComponent(
                                    color = TextPrimary.copy(alpha = 0.08f),
                                    thickness = 1.dp
                                ),
                                itemPlacer = AxisItemPlacer.Vertical.default(maxItemCount = 4),
                                valueFormatter = { v, _ -> "${v.toInt()}°" }
                            ),
                            bottomAxis = null,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    // Danger threshold label overlay (Vico 1.13.1 has no
                    // HorizontalLine decoration). Visual cue: 45°C =
                    // SafetyMonitor stop threshold.
                    Text(
                        text = stringResource(R.string.scan_chart_danger_threshold),
                        style = MaterialTheme.typography.labelSmall,
                        color = StatusFail,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 4.dp, end = 8.dp)
                    )
                }
            }
        }
    }
}

/**
 * fieldsVersion 69 (SN-04, contract section 7-h (c)): asks the technician to cover the light sensor, which sits
 * at the top of the screen. Shown only while the sensor test has the request up (CoverPromptChannel); the app
 * does not check that anyone obeyed.
 */
@Composable
private fun CoverSensorPrompt() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .background(NeuralGreen, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.PanTool, contentDescription = null, tint = DeepSpace)
        Spacer(Modifier.width(10.dp))
        Text(
            text = stringResource(R.string.scan_cover_light_sensor_prompt),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = DeepSpace
        )
    }
}
