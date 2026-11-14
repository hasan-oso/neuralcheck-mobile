@file:OptIn(ExperimentalMaterial3Api::class)
package net.neuralcheck.presentation.screen

import android.content.Intent
import android.graphics.Bitmap
import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import net.neuralcheck.data.auth.AuthManager
import net.neuralcheck.presentation.navigation.startNewScan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.neuralcheck.R
import net.neuralcheck.data.local.db.entity.FunctionalResultEntity
import net.neuralcheck.data.remote.model.AiPipeline
import net.neuralcheck.domain.manual.ManualRowActions
import net.neuralcheck.domain.manual.ManualTestVisibility
import net.neuralcheck.domain.manual.ManualUnavailableOffer
import net.neuralcheck.domain.manual.TouchGridRecorder
import net.neuralcheck.domain.manual.VolumeKeyRecorder
import net.neuralcheck.domain.model.AiAnalysisResult
import net.neuralcheck.domain.model.IssueSeverity
import net.neuralcheck.domain.model.ManualStatuses
import net.neuralcheck.domain.model.TestSpec
import net.neuralcheck.domain.scoring.ScoringResult
import net.neuralcheck.presentation.components.CategoryHeader
import net.neuralcheck.presentation.components.GradeBadge
import net.neuralcheck.presentation.components.NCCard
import net.neuralcheck.presentation.theme.*
import net.neuralcheck.presentation.viewmodel.ReportViewModel
import net.neuralcheck.presentation.viewmodel.ScannedRepeatUi
import net.neuralcheck.presentation.util.KeepScreenOn
import net.neuralcheck.util.AiV7Presentation
import net.neuralcheck.util.AiVerdictPresentation
import net.neuralcheck.util.CategoryPresentation
import net.neuralcheck.util.CategoryTranslations
import net.neuralcheck.util.NumberFormat
import net.neuralcheck.util.QrCodeHelper
import net.neuralcheck.util.ReportPdfGenerator
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal data class ManualTestItem(
    val id: String,
    @StringRes val nameResId: Int,
    @StringRes val instructionResId: Int
)

// M-13 (OTG) and M-16 (IR blaster) were retired from the public report: too
// many target devices lack the hardware (or lack the accessory needed to
// verify it), so the chip choices were mostly noise. Dialogs + TestSpec
// entries for both remain in the codebase in case we surface them again.
//
// M-17..M-22 are the camera/biometric/vibration user-perception checks
// added alongside the existing automatic CM-01..CM-11 capability probes
// (which only verify hardware presence + characteristics, not whether the
// component actually does its job from the user's perspective). Each has
// an interactive dialog now: M-17/M-18 open a CameraX preview, M-19 toggles
// CameraManager.setTorchMode, M-20/M-21 fire BiometricPrompt, and M-22
// runs a 1 s VibrationEffect. The user can verify the part with their own
// senses before answering يعمل / لا يعمل / لم أفحص.
internal val manualTestItems = listOf(
    ManualTestItem("M-01", R.string.manual_dead_pixels, R.string.manual_dead_pixels_instruction),
    ManualTestItem("M-02", R.string.manual_touch_response, R.string.manual_touch_response_instruction),
    ManualTestItem("M-03", R.string.manual_multi_touch, R.string.manual_multi_touch_instruction),
    ManualTestItem("M-04", R.string.manual_speaker, R.string.manual_speaker_instruction),
    ManualTestItem("M-05", R.string.manual_earpiece, R.string.manual_earpiece_instruction),
    ManualTestItem("M-06", R.string.manual_main_mic, R.string.manual_main_mic_instruction),
    ManualTestItem("M-08", R.string.manual_headphone_jack, R.string.manual_headphone_jack_instruction),
    ManualTestItem("M-09", R.string.manual_volume_up, R.string.manual_volume_up_instruction),
    ManualTestItem("M-10", R.string.manual_volume_down, R.string.manual_volume_down_instruction),
    ManualTestItem("M-11", R.string.manual_power_button, R.string.manual_power_button_instruction),
    ManualTestItem("M-12", R.string.manual_charging_port, R.string.manual_charging_port_instruction),
    ManualTestItem("M-14", R.string.manual_wireless_charging, R.string.manual_wireless_charging_instruction),
    ManualTestItem("M-15", R.string.manual_sd_card, R.string.manual_sd_card_instruction),
    ManualTestItem("M-17", R.string.manual_rear_camera, R.string.manual_rear_camera_instruction),
    ManualTestItem("M-18", R.string.manual_front_camera, R.string.manual_front_camera_instruction),
    ManualTestItem("M-19", R.string.manual_flash, R.string.manual_flash_instruction),
    ManualTestItem("M-20", R.string.manual_fingerprint, R.string.manual_fingerprint_instruction),
    ManualTestItem("M-21", R.string.manual_face_unlock, R.string.manual_face_unlock_instruction),
    ManualTestItem("M-22", R.string.manual_vibration, R.string.manual_vibration_instruction),
)

@Composable
fun ReportScreen(
    navController: NavController,
    scanId: String,
    viewModel: ReportViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scan by viewModel.scan.collectAsState()
    val scoring by viewModel.scoringResult.collectAsState()
    val results by viewModel.results.collectAsState()
    val hasSurveyData by viewModel.hasSurveyData.collectAsState()
    // A20: real analysis stage for the loading overlay.
    val analysisStage by viewModel.analysisStage.collectAsState()
    // Order 2026-09-24 (القرار ٨٠/٨٢ البند ٦): loadScan's Room-miss
    // fallback to fetchExistingReport. `remoteHeader` supplies deviceModel/
    // scanTimestamp when `scan` (Room-only) is null but the cloud copy
    // rendered successfully; `loadFailed` is the explicit "neither Room
    // nor the cloud has this report" state — shown instead of an
    // AiLoadingOverlay that would otherwise spin forever (rule 22).
    val remoteHeader by viewModel.remoteHeader.collectAsState()
    val loadFailed by viewModel.loadFailed.collectAsState()

    val hasRenderableReport = scoring != null && (scan != null || remoteHeader != null)

    // fix/keep-screen-on: hold the screen awake ONLY while the analysis is
    // still running. This wait is ~3 minutes with no progress indicator —
    // one field log shows a user tapping 11 times in 6 seconds at a silent
    // screen — and a display that sleeps on top of that reads as a hang.
    // The condition mirrors the loading gate further down, so the flag is
    // released the moment the report renders OR the load explicitly fails:
    // the user is reading now (or looking at a dead end), and the system
    // timeout is the correct owner of the screen again. Leaving early
    // (back / error) disposes this composable and releases it too.
    val isAnalysisPending = !hasRenderableReport && !loadFailed
    KeepScreenOn(active = isAnalysisPending)

    var showQrDialog by remember { mutableStateOf(false) }
    var snackbarMessage by remember { mutableStateOf<String?>(null) }
    val snackbarHost = remember { SnackbarHostState() }

    val manualResults = remember(results) {
        results.filter { it.notes == "manual" }.associate { it.testId to it.status }
    }

    LaunchedEffect(scanId) { viewModel.loadScan(scanId) }

    // A-P6-07 (ف١٣: the scanned phone waits for itself to cool, then starts the repeat scan): BEHIND ITS OWN GUARD - nothing is drawn unless REPEAT_TEXTS_APPROVED is true (it is, since 2026-10-23: the
    // texts are approved and written) and repeatLabels() has the wording (RepeatScanSurface.kt). The screen draws only the state ReportViewModel.repeatUi holds, and goes to the child's scan when the server started it.
    val repeatUi by viewModel.repeatUi.collectAsState()
    val repeatLabels = repeatLabels()
    LaunchedEffect(repeatUi) {
        val started = repeatUi as? ScannedRepeatUi.Started
        if (REPEAT_TEXTS_APPROVED && started != null) {
            // the holder already holds the child (ReportViewModel); this is the scan funnel's own entry, the one the join takes (JoinSessionScreen.kt:54)
            navController.navigate("pre_scan/general/none")
            viewModel.repeatStartConsumed()
        }
    }

    // Re-check survey presence when returning from PeerSurvey / TechPre / TechPost
    // so the report reflects the submit without needing a full loadScan round-trip.
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, scanId) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                viewModel.refreshSurveyStatus(scanId)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // ADR 2.9 / 2.10 / 2.12 — "immediately after the report screen, only if scan type X":
    //   general     → PeerSurvey (3 yes/no questions)
    //   pre_repair  → TechPreRepair (technician diagnosis, confidence)
    //   post_repair → TechPostRepair (what was fixed, was it resolved)
    // Guard by DB (hasSurveyData) AND by a local `alreadyPrompted` flag.
    // The flag is critical: after the user submits the survey and popBackStack
    // returns to ReportScreen, there is a brief window before ON_RESUME fires
    // `refreshSurveyStatus` and its coroutine finishes the DB read. During
    // that window `hasSurveyData` is still `false`; any recomposition that
    // lands in between would re-fire this LaunchedEffect and loop the user
    // back into the survey. `alreadyPrompted` closes that race by ensuring we
    // only ever navigate once per report view.
    var alreadyPrompted by rememberSaveable(scanId) { mutableStateOf(false) }
    LaunchedEffect(scan, hasSurveyData, alreadyPrompted) {
        if (alreadyPrompted) return@LaunchedEffect
        val type = scan?.scanType ?: return@LaunchedEffect
        if (hasSurveyData != false) return@LaunchedEffect
        // Peer-survey follow-up is a data-collection feature: it is only
        // reachable in the datacollect flavor. In the commercial build the
        // general-scan branch is silently skipped; the pre/post-repair
        // branches are also ordinarily unreachable there because the entry
        // points on HomeScreen are hidden, but are left wired up defensively.
        val route = when (type) {
            "general" -> if (net.neuralcheck.BuildConfig.SHOW_PEER_INPUT) "peer_survey/$scanId" else null
            "pre_repair" -> "tech_pre_repair/$scanId"
            "post_repair" -> "tech_post_repair/$scanId"
            else -> null
        } ?: return@LaunchedEffect
        alreadyPrompted = true
        navController.navigate(route)
    }

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let {
            snackbarHost.showSnackbar(it)
            snackbarMessage = null
        }
    }

    val pdfGen = remember { ReportPdfGenerator(context) }
    // The backend mints a short, human-readable ID (e.g. "NC-A7K9M2") and
    // returns it on the AI response. We use it everywhere the user sees the
    // identifier — report header, QR encoding, QR caption, and any PDF the
    // user shares.
    //
    // Three-step fallback: nested AiAnalysisResult → mirrored top-level
    // ScoringResult.shortId → raw scanId UUID. The middle step covers any
    // future code path that populates `ScoringResult.shortId` directly
    // (e.g. a cached score retrieved from Room) without re-running the AI
    // pipeline; either source resolves to the same value when both are set.
    val displayId: String =
        scoring?.aiAnalysis?.shortId?.takeIf { it.isNotBlank() }
            ?: scoring?.shortId?.takeIf { it.isNotBlank() }
            ?: scanId
    val qrBitmap = remember(displayId, scoring?.overallGrade) {
        // Order 2026-09-24 (جولة جرد القيم الافتراضية، البند ز):
        // `scoring?.overallGrade` is AiVerdictPresentation.resolveOverallGrade's
        // return value — a non-null String that is the literal "?" sentinel
        // (not null) when no real grade is known (see that function's doc).
        // QrCodeHelper.generateQrBitmap already omits the `g=` query param
        // when `grade == null`, but "?" is not null, so the sentinel used
        // to leak into the QR payload verbatim as `g=?`. isKnownGrade()
        // converts the sentinel (and anything else that isn't a real
        // A/B/C/D letter) to null here, so the existing null-check in
        // QrCodeHelper omits the parameter instead.
        //
        // Order 2026-09-26 (القرار ١٠٢ البند ٣): the `&s=<score>` param
        // (and the `score` argument that fed it) is removed from
        // generateQrBitmap entirely — see that function's own doc.
        val knownGrade = scoring?.overallGrade?.takeIf { AiVerdictPresentation.isKnownGrade(it) }
        QrCodeHelper.generateQrBitmap(displayId, knownGrade)
    }

    Scaffold(
        containerColor = DeepSpace,
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Compact brand wordmark fills the TopAppBar title
                        // slot so the report reads as an official artefact;
                        // a generic "Report" string was too unbranded for a
                        // PDF that may be shared with a buyer.
                        ReportHeaderWordmark()
                        Spacer(Modifier.width(10.dp))
                        Text(
                            stringResource(R.string.title_report),
                            color = TextSecondary,
                            fontSize = 14.sp
                        )
                    }
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
            // The Share / QR / Save buttons are exports — they only make
            // sense for paid scans. Free-trial reports are view-only inside
            // the app: hide the whole bottom bar so the user is steered
            // toward subscribing to unlock the export surface.
            val isFreeTrial = scoring?.aiAnalysis?.isFreeTrial == true
            if (!isFreeTrial) {
                BottomAppBar(
                    containerColor = CardDark,
                    contentColor = TextSecondary
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        // Order 2026-09-23 (القرار ٧٣، البند ٢) — single flag,
                        // see ReportPdfExportButtons's own doc for scope
                        // (Share + Save only, NOT the QR button below).
                        if (!ReportPdfExportButtons.HIDDEN) {
                            TextButton(onClick = {
                                val s = scoring ?: return@TextButton
                                val sc = scan ?: return@TextButton
                                scope.launch {
                                    val ok = shareReport(context, pdfGen, qrBitmap, s, net.neuralcheck.util.DeviceName.prettify(sc.deviceModel), sc.timestamp, scanId, displayId, results)
                                    snackbarMessage = context.getString(if (ok) R.string.report_share_opened else R.string.report_share_failed)
                                }
                            }) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.Share, null, tint = NeuralGreen)
                                    Text(stringResource(R.string.report_share), color = TextSecondary)
                                }
                            }
                        }
                        TextButton(onClick = { showQrDialog = true }) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.QrCode, null, tint = SkyCyan)
                                Text(stringResource(R.string.report_qr_code), color = TextSecondary)
                            }
                        }
                        if (!ReportPdfExportButtons.HIDDEN) {
                            TextButton(onClick = {
                                val s = scoring ?: return@TextButton
                                val sc = scan ?: return@TextButton
                                scope.launch {
                                    val path = saveReport(context, pdfGen, qrBitmap, s, net.neuralcheck.util.DeviceName.prettify(sc.deviceModel), sc.timestamp, scanId, displayId, results)
                                    snackbarMessage = if (path != null) context.getString(R.string.report_saved_fmt, path) else context.getString(R.string.report_save_failed)
                                }
                            }) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.Save, null, tint = NeonCyan)
                                    Text(stringResource(R.string.btn_save), color = TextSecondary)
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        if (loadFailed) {
            // Order 2026-09-24 (القرار ٨٠/٨٢ البند ٦، القاعدة 22): explicit
            // failure — neither Room nor the cloud has this report — shown
            // instead of an AiLoadingOverlay that would otherwise spin
            // forever. Reuses AiAnalysisErrorCard's presentation and the
            // existing "scan not found" string (qr_scan_not_found) rather
            // than inventing new copy — the meaning is identical: this
            // scanId resolves to nothing this device can show.
            Box(
                modifier = Modifier.fillMaxSize().background(DeepSpace).padding(padding).padding(16.dp)
            ) {
                AiAnalysisErrorCard(
                    error = stringResource(R.string.qr_scan_not_found),
                    onRetry = { viewModel.loadScan(scanId) }
                )
            }
        } else if (!hasRenderableReport) {
            AiLoadingOverlay(
                modifier = Modifier.fillMaxSize().background(DeepSpace).padding(padding),
                stage = analysisStage,
                startedAtMs = viewModel.analysisStartedAtMs()
            )
        } else {
            // Order 2026-09-24 (القرار ٨٠/٨٢ البند ٦): deviceModel/
            // scanTimestamp come from the Room row when present, otherwise
            // from remoteHeader (the fetchExistingReport fallback) — both
            // are real, honestly-sourced values, never fabricated.
            // isComplete/earlyStopReason stay Room-only: both are
            // genuinely unknown for a remote-fallback report (this device
            // never ran it), and — verified by reading ReportContent below
            // — are read ONLY in the datacollect/rule-based branch, which
            // this remote-fallback path (launch-flavor only, per
            // FirebaseAnalysisService) never reaches. `true`/`null` here
            // are therefore inert placeholders, not a claim about the
            // scan's actual completeness.
            val header = scan
            ReportContent(
                modifier = Modifier.padding(padding),
                scoring = scoring!!,
                deviceModel = header?.deviceModel ?: remoteHeader!!.deviceModel,
                scanTimestamp = header?.timestamp ?: remoteHeader!!.scanTimestampMs,
                isComplete = header?.isComplete ?: true,
                earlyStopReason = header?.earlyStopReason,
                displayId = displayId,
                manualResults = manualResults,
                // A-P6-07: the cooling screen sits right above the «new scan» button (PLAN-06:955, «بعد التقرير `ReportScreen.kt:380-389`»); nothing is drawn unless the guard is true, the labels exist and the server chose this phone
                repeatSection = {
                    val cooling = repeatUi as? ScannedRepeatUi.Cooling
                    if (REPEAT_TEXTS_APPROVED && repeatLabels != null && cooling != null) {
                        CoolingWaitSection(
                            ui = cooling,
                            labels = repeatLabels,
                            actions = CoolingActions(
                                onTemperature = viewModel::onRepeatTemperature,
                                startReady = viewModel::startRepeatReady,
                                startAnyway = viewModel::startRepeatAnyway,
                                wait = viewModel::chooseRepeatWait
                            )
                        )
                        Spacer(Modifier.height(16.dp))
                    }
                },
                // Re-running loadScan re-enters AiScoringEngine.calculateScore,
                // which re-POSTs the fingerprint — so "retry" on the error
                // banner is the same code path as first load.
                onRetryAi = { viewModel.loadScan(scanId) },
                onNewScan = {
                    // Jump straight back to Home without stacking the scan
                    // funnel behind it. popUpTo home inclusive=false keeps
                    // the Home destination alive so its state (last scan,
                    // stats) reflects the scan we just finished.
                    // A-SUB wave 1 (user decision 1, P1): that is for an ACCOUNT. A customer's phone (an anonymous user) has no home on its stack - it started at
                    // login - and home is closed to it: its «new scan» goes to the code-entry screen (AnonymousRouting.kt, startNewScan).
                    navController.startNewScan(AuthManager.kindOf(Firebase.auth.currentUser))
                },
                onManualTestResult = { testId, status, measurements ->
                    val spec = TestSpec.fromId(testId) ?: return@ReportContent
                    viewModel.saveManualResults(
                        scanId,
                        listOf(FunctionalResultEntity(
                            scanId = scanId,
                            testId = testId,
                            componentName = testId,
                            categoryName = spec.category.name.lowercase(Locale.US),
                            status = status,
                            score = ManualStatuses.scoreOf(status),
                            rawValue = null,
                            rawValueUnit = null,
                            notes = "manual",
                            timestamp = System.currentTimeMillis(),
                            // fieldsVersion 69 (N-19): the raw of the run that produced this answer.
                            measurements = net.neuralcheck.data.local.db.converter.MeasurementsJson.encode(measurements)
                        ))
                    )
                }
            )
        }
    }

    if (showQrDialog) {
        AlertDialog(
            containerColor = CardDark,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            onDismissRequest = { showQrDialog = false },
            title = { Text(stringResource(R.string.report_qr_code)) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = "QR",
                        modifier = Modifier
                            .size(240.dp)
                            .background(Color.White, androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    // Prominent display of the human-readable identifier.
                    // Falls back to the UUID when no shortId is present —
                    // we still want *something* to anchor the QR so the
                    // user can read it back manually if scanning fails.
                    Text(
                        text = displayId,
                        fontFamily = OutfitFamily,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 22.sp,
                        color = TextPrimary
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showQrDialog = false }) {
                    Text(stringResource(R.string.btn_close), color = NeuralGreen)
                }
            }
        )
    }

}

/**
 * Order 2026-09-26 (القرار ١٠٢ البند ١). The scan-header line under
 * `ShortIdChip` used to unconditionally format `scanTimestamp` — the
 * remote-fallback path (ViewModels.kt's `loadFromRemoteFallback`) can now
 * legitimately have no timestamp at all (neither `analyzedAt` nor
 * `createdAt`), and the report is still rendered (rule 22) rather than
 * failing to load. `unavailableText` is composed by the ONE caller below
 * from two EXISTING, already-approved strings — `report_scan_date`
 * ("تاريخ الفحص") + `report_v7_field_unavailable` ("غير متوفر") — per the
 * order's own instruction; no new text resource was added for this.
 *
 * `internal`, not `private`, and takes the resolved fallback text as a
 * plain `String` rather than calling `stringResource` itself — same
 * reasoning as `ReportPdfGenerator.v7StateLine`'s doc: this makes the
 * date-vs-fallback DECISION directly unit-testable without drawing the screen
 * (Robolectric and `ui-test-junit4` are test dependencies since 3284cca, so a
 * rendered test is no longer ruled out by the setup; none exists).
 */
internal fun scanHeaderDateText(scanTimestampMs: Long?, unavailableText: String): String =
    if (scanTimestampMs != null) {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(scanTimestampMs))
    } else {
        unavailableText
    }

@Composable
private fun ReportContent(
    modifier: Modifier,
    scoring: ScoringResult,
    deviceModel: String,
    // Order 2026-09-26 (القرار ١٠٢ البند ١): nullable — the remote-fallback
    // path can legitimately have neither `analyzedAt` nor `createdAt`
    // (see ViewModels.kt's loadFromRemoteFallback). `scanHeaderLine` below
    // renders an explicit "time unavailable" line instead of failing or
    // guessing a date.
    scanTimestamp: Long?,
    isComplete: Boolean,
    earlyStopReason: String?,
    displayId: String,
    manualResults: Map<String, String>,
    repeatSection: @Composable () -> Unit,
    onRetryAi: () -> Unit,
    onNewScan: () -> Unit,
    onManualTestResult: (testId: String, status: String, measurements: Map<String, Any?>?) -> Unit
) {
    val isTurkish = Locale.getDefault().language == "tr"
    // Translate the stored codename into a marketing name for display.
    // Idempotent on already-pretty values (new scans are pretty at write
    // time, old scans get translated lazily here).
    val prettyDeviceModel = remember(deviceModel) {
        net.neuralcheck.util.DeviceName.prettify(deviceModel)
    }
    val scanDateText = scanHeaderDateText(
        scanTimestamp,
        unavailableText = "${stringResource(R.string.report_scan_date)}: ${stringResource(R.string.report_v7_field_unavailable)}"
    )
    LazyColumn(modifier = modifier.fillMaxSize().background(DeepSpace).padding(16.dp)) {
        // Launch flavor: backend is the single source of truth. Render ONLY
        // the AI section (grade + summary + verdict + issues + predicted
        // problems). If the call failed, render ONLY the error card with a
        // retry button — NO rule-based grade, categories, axes, or critical
        // failures leak through. If scoring is set but neither populated (a
        // shouldn't-happen state), fall through to a spinner.
        if (net.neuralcheck.BuildConfig.USE_AI_SCORING) {
            val ai = scoring.aiAnalysis
            val err = scoring.error
            when {
                ai != null -> {
                    // Free-trial banner sits at the very top of the report so
                    // the user immediately understands why the bottom-bar
                    // exports are missing. The banner copy explicitly names
                    // the locked features (PDF + QR) so subscribing reads as
                    // "unlock these specific things", not a vague upgrade nag.
                    if (ai.isFreeTrial) {
                        item { FreeTrialBanner() }
                        item { Spacer(Modifier.height(14.dp)) }
                    }
                    // Order 2026-09-24 (القرار ٧٦، البند ١) — "لا تُعرض الدرجة
                    // ولا النسبة على V7 في أي شاشة، ومنها GradeSummaryCard".
                    // V7 responses never carry `response.score`
                    // (AiScoringEngine.kt:395 currently coerces the absence to
                    // 0f — a separate, BLOCKED fix pending a Room schema
                    // question, see this task's report), so GradeSummaryCard
                    // would otherwise render a fabricated "0%". Suppressed
                    // here unconditionally for V7 regardless of the
                    // underlying float value — this does not depend on that
                    // value ever becoming a real null.
                    if (AiV7Presentation.shouldShowGradeSummary(
                            ai.pipeline,
                            hasRootLetterOrGrade = !ai.grade.isNullOrBlank(),
                            hasV7Overall = ai.overall != null
                        )
                    ) {
                        item { GradeSummaryCard(scoring) }
                        item { Spacer(Modifier.height(10.dp)) }
                    }
                    item { ShortIdChip(displayId) }
                    item { Spacer(Modifier.height(8.dp)) }
                    item {
                        Text(
                            text = NumberFormat.toLatin("$prettyDeviceModel · $scanDateText"),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                    // Phase 1.15: prominent buyer note — frames depth (what we
                    // guarantee) vs surface (inspector-stated, re-check yourself).
                    item { BuyerSurfaceNoteCard() }
                    item { Spacer(Modifier.height(14.dp)) }
                    item { AiAnalysisSection(ai) }
                    item { Spacer(Modifier.height(24.dp)) }
                    // A-P6-07: the scanned phone's cooling screen (nothing when it has none to draw)
                    item { repeatSection() }
                    item { NewScanFooterButton(onClick = onNewScan) }
                    item { Spacer(Modifier.height(24.dp)) }
                }
                scoring.analysisInProgress -> {
                    // fieldsVersion 69 (S-26): the server is still processing this scan — «انتظر», not an error.
                    item { AiAnalysisWaitCard(onRetry = onRetryAi) }
                    item { Spacer(Modifier.height(24.dp)) }
                }
                err != null -> {
                    item { AiAnalysisErrorCard(error = err, onRetry = onRetryAi) }
                    item { Spacer(Modifier.height(24.dp)) }
                }
                else -> {
                    // Belt-and-braces: the outer Scaffold already renders a
                    // spinner while `scoring == null`, so by the time we're
                    // here we expect either `ai` or `err`. Show a spinner
                    // anyway instead of silently rendering an empty screen.
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            @Suppress("DEPRECATION")
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth(0.6f),
                                color = NeuralGreen,
                                trackColor = Surface
                            )
                        }
                    }
                }
            }
            return@LazyColumn
        }

        // Datacollect flavor: full rule-based view.
        item { GradeSummaryCard(scoring) }
        item { Spacer(Modifier.height(10.dp)) }
        item { ShortIdChip(displayId) }
        item { Spacer(Modifier.height(8.dp)) }
        item {
            Text(
                text = NumberFormat.toLatin("$prettyDeviceModel · $scanDateText"),
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
        }
        item { Spacer(Modifier.height(24.dp)) }

        if (!isComplete) {
            item { IncompleteBanner(earlyStopReason) }
            item { Spacer(Modifier.height(24.dp)) }
        }

        if (scoring.criticalFailures.isNotEmpty()) {
            item { CriticalFailuresCard(scoring.criticalFailures) }
            item { Spacer(Modifier.height(24.dp)) }
        }

        if (scoring.antiTamperPenalty > 0f) {
            item { AntiTamperBanner(scoring.antiTamperPenalty) }
            item { Spacer(Modifier.height(24.dp)) }
        }

        // Axis scores — 2x2 grid. Earlier a single-row SpaceEvenly layout
        // broke with long Arabic axis names ("الاتصال والحساسات") overflowing
        // their cells, and produced a visible vertical gap in Turkish because
        // the row auto-sized to the tallest cell. A 2x2 weight(1f) grid keeps
        // cells equal-sized and wrap-capable in both languages.
        item {
            Text(
                text = stringResource(R.string.report_summary),
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary
            )
        }
        item { Spacer(Modifier.height(8.dp)) }
        item {
            val axes = scoring.axisScores.values.toList()
            Column(modifier = Modifier.fillMaxWidth()) {
                axes.chunked(2).forEachIndexed { index, rowAxes ->
                    if (index > 0) Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowAxes.forEach { axis ->
                            val axisName = if (isTurkish && axis.nameTr.isNotBlank()) axis.nameTr else axis.nameAr
                            AxisCard(
                                modifier = Modifier.weight(1f),
                                name = axisName,
                                score = axis.score
                            )
                        }
                        // Fill the trailing cell so widths stay aligned when
                        // the final row is a single axis (rare, but ADR allows).
                        if (rowAxes.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }

        // Category scores
        item {
            Text(
                text = stringResource(R.string.report_categories),
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary
            )
        }
        item { Spacer(Modifier.height(8.dp)) }
        items(scoring.categoryScores.values.toList()) { cat ->
            val catName = if (isTurkish && cat.nameTr.isNotBlank()) cat.nameTr else cat.nameAr
            CategoryRow(catName, cat.score, cat.passCount, cat.totalCount)
        }
        item { Spacer(Modifier.height(24.dp)) }
        item { ManualTestsSection(manualResults, onManualTestResult) }
        item { Spacer(Modifier.height(24.dp)) }
        item { NewScanFooterButton(onClick = onNewScan) }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

/**
 * "New Scan" CTA shown at the end of the report. Full-width outlined
 * button instead of a primary button — the header's Share/Save actions
 * are the primary CTAs on this screen; "new scan" is a recovery path,
 * not the main goal of the screen.
 */
/**
 * Banner pinned to the top of free-trial reports. Pairs the "this is a
 * trial" copy with a primary CTA that jumps straight to the subscription
 * picker, so the user can convert in one tap. Uses the SkyCyan accent
 * (already in use for the AI summary card) so the banner reads as
 * informational, not as an error.
 */
@Composable
private fun FreeTrialBanner() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SkyCyan.copy(alpha = 0.10f)),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, SkyCyan.copy(alpha = 0.45f))
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("✨", fontSize = 18.sp)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.free_trial_report_banner),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                    lineHeight = 19.sp
                )
            }
        }
    }
}

@Composable
private fun NewScanFooterButton(onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, NeuralGreen.copy(alpha = 0.45f)),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = CardDark,
            contentColor = NeuralGreen
        )
    ) {
        Icon(Icons.Default.PlayArrow, null, tint = NeuralGreen)
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.report_new_scan),
            fontWeight = FontWeight.SemiBold
        )
    }
}

/**
 * Compact version of the home-screen gradient wordmark, sized for the
 * TopAppBar title slot. The gradient-on-text trick mirrors the one on
 * HomeScreen — selectable text, no rasterised asset.
 */
@Composable
private fun ReportHeaderWordmark() {
    val brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
        listOf(NeuralGreen, NeonCyan, SkyCyan)
    )
    Text(
        text = stringResource(R.string.app_name),
        fontFamily = net.neuralcheck.presentation.theme.OutfitFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 18.sp,
        color = TextPrimary,
        modifier = Modifier
            .graphicsLayer(alpha = 0.99f)
            .drawWithContent {
                drawContent()
                drawRect(brush = brush, blendMode = androidx.compose.ui.graphics.BlendMode.SrcIn)
            }
    )
}

/**
 * Rich loading state shown while the scan report loads and the AI
 * analysis is still in flight. Rotates through five status strings every
 * 3 s, shows a pulsing wordmark, an indeterminate progress strip, and a
 * fixed "collected N samples" line so the user understands the wait is
 * real work rather than a network stall.
 */
@Composable
private fun AiLoadingOverlay(
    modifier: Modifier = Modifier,
    // A20: the engine's real stage, replacing a 3-second carousel that
    // rotated through five messages unrelated to anything actually
    // happening. IDLE/DONE fall back to the first stage's wording so the
    // overlay never renders blank during the brief gap before the engine
    // reports in.
    stage: net.neuralcheck.domain.scoring.AnalysisStage =
        net.neuralcheck.domain.scoring.AnalysisStage.READING_MEASUREMENTS,
    startedAtMs: Long? = null
) {
    val stageText = when (stage) {
        net.neuralcheck.domain.scoring.AnalysisStage.PREPARING_PAYLOAD -> R.string.ai_stage_preparing
        net.neuralcheck.domain.scoring.AnalysisStage.CHECKING_CACHE -> R.string.ai_stage_checking
        net.neuralcheck.domain.scoring.AnalysisStage.ANALYSING -> R.string.ai_stage_analysing
        net.neuralcheck.domain.scoring.AnalysisStage.BUILDING_REPORT -> R.string.ai_stage_building
        else -> R.string.ai_stage_reading
    }

    // Elapsed seconds — the one number in this wait that is honestly
    // known. Ticks once per second; nothing else in the overlay depends on
    // a timer any more.
    var elapsedSec by remember(startedAtMs) { mutableIntStateOf(0) }
    LaunchedEffect(startedAtMs) {
        if (startedAtMs == null) return@LaunchedEffect
        while (true) {
            elapsedSec = ((System.currentTimeMillis() - startedAtMs) / 1000L)
                .coerceAtLeast(0L).toInt()
            kotlinx.coroutines.delay(1_000L)
        }
    }

    // Gentle scale pulse on the wordmark — signals activity without the
    // heavyweight chart-redraw path that CircularProgressIndicator uses
    // on some BOM resolutions.
    val pulse = androidx.compose.animation.core.rememberInfiniteTransition(label = "ai_pulse")
    val scale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(
                durationMillis = 900,
                easing = androidx.compose.animation.core.FastOutSlowInEasing
            ),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "ai_pulse_scale"
    )

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
            listOf(NeuralGreen, NeonCyan, SkyCyan)
        )
        Text(
            text = stringResource(R.string.app_name),
            fontFamily = net.neuralcheck.presentation.theme.OutfitFamily,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 36.sp,
            color = TextPrimary,
            modifier = Modifier
                .graphicsLayer(alpha = 0.99f, scaleX = scale, scaleY = scale)
                .drawWithContent {
                    drawContent()
                    drawRect(brush = brush, blendMode = androidx.compose.ui.graphics.BlendMode.SrcIn)
                }
        )

        Spacer(Modifier.height(20.dp))

        // Fixed-height AnimatedContent so the crossfade doesn't shift layout
        // when the stage changes.
        androidx.compose.animation.AnimatedContent(
            targetState = stageText,
            transitionSpec = {
                androidx.compose.animation.fadeIn() togetherWith androidx.compose.animation.fadeOut()
            },
            label = "ai_stage",
            modifier = Modifier.height(24.dp)
        ) { res ->
            Text(
                text = stringResource(res),
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
        }

        Spacer(Modifier.height(10.dp))

        // Elapsed counter + how long this normally takes. Between them they
        // answer the question the silent screen left open ("is this stuck?")
        // without inventing a percentage the app cannot know.
        if (startedAtMs != null) {
            Text(
                text = net.neuralcheck.util.NumberFormat.toLatin(
                    stringResource(R.string.ai_stage_elapsed_fmt, elapsedSec / 60, elapsedSec % 60)
                ),
                style = MaterialTheme.typography.labelLarge,
                fontFamily = net.neuralcheck.presentation.theme.OutfitFamily,
                color = TextMuted
            )
            Spacer(Modifier.height(6.dp))
        }
        Text(
            text = stringResource(R.string.ai_stage_hint),
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(0.8f)
        )

        Spacer(Modifier.height(18.dp))

        @Suppress("DEPRECATION")
        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .height(3.dp),
            color = NeuralGreen,
            trackColor = Surface
        )
        // Removed Phase 1.10 cleanup (FLAG-8): the previous "تم جمع 1240 عينة
        // من 18 متغير" line baked V1 schema constants (1240 samples × 18
        // variables) into the loading overlay. The current V5 schema uses
        // 11 universal v[] channels and the sample count is variable per
        // run, so the line was misleading rather than reassuring. The
        // rotating status messages above + the indeterminate progress bar
        // already convey "real work is happening".
    }
}

@Composable
private fun ManualTestsSection(
    existingResults: Map<String, String>,
    onResult: (testId: String, status: String, measurements: Map<String, Any?>?) -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val completed = existingResults.count { it.value == ManualStatuses.PASS || it.value == ManualStatuses.FAIL }
    // fieldsVersion 69 (N-17): the tests the device model does not have are not shown
    // here either (ManualTestVisibility.RULES is empty today — nothing is hidden yet).
    val context = LocalContext.current
    val shownItems = remember {
        ManualTestVisibility.visible(
            manualTestItems, ManualTestVisibility.hiddenByModel(context)
        ) { it.id }
    }
    val total = shownItems.size

    NCCard(modifier = Modifier.fillMaxWidth(), contentPadding = 0.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Checklist, null, tint = NeuralGreen)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.manual_tests_section_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    NumberFormat.toLatin(stringResource(R.string.manual_tests_progress_fmt, completed, total)),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = TextSecondary
            )
        }

        if (expanded) {
            HorizontalDivider(color = CardBorder, thickness = 0.5.dp)
            shownItems.forEach { item ->
                ManualTestRow(item, existingResults[item.id], onResult)
                HorizontalDivider(color = CardBorder, thickness = 0.5.dp)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class) // FlowRow: experimental in foundation-layout 1.6.x (BOM 2024.04.01); TechnicianEvaluationCard opts in the same way
@Composable
internal fun ManualTestRow(
    item: ManualTestItem,
    currentStatus: String?,
    onResult: (testId: String, status: String, measurements: Map<String, Any?>?) -> Unit
) {
    val isInteractive = item.id in interactiveManualTests
    var activeDialog by rememberSaveable(item.id) { mutableStateOf<String?>(null) }
    val currentOnResult by rememberUpdatedState(onResult)
    // Batch 1.0.12 (second part), narrowed 2026-10-15: the rows that offer the answer `unavailable` — ONE list, read here by the line under
    // the chips and by the routed dialogs' own button (ManualUnavailableOffer: M-08, M-14, M-15).
    val offersUnavailable = ManualUnavailableOffer.isOffered(item.id)

    // Every way an answer — or the lack of one — leaves this row is ManualRowActions' (batch 1.0.12, items 2 and 3):
    // what a chip tap does (every chip of every row writes its answer — «يعمل» of M-02 since 2026-10-18 and of M-09 / M-10 since 2026-10-21 too; a dialog opens from
    // «ابدأ الاختبار» alone), what a dialog's answer or
    // dismissal does, and the NeuralCheck.ManualAnswer line each exit owes. The row calls nothing else —
    // ManualRowSourceShapeTest pins that — so an exit cannot appear here without its line.
    val actions = remember(item.id) {
        ManualRowActions(
            testId = item.id,
            write = { id, status, measurements -> currentOnResult(id, status, measurements) },
            setActiveDialog = { activeDialog = it }
        )
    }

    // The one way a dialog's answer leaves the row: the answer goes up — with the raw, when the
    // dialog measured one (fieldsVersion 69, N-19) — and the dialog closes. (A dialog with no run has no raw: null.)
    fun answer(status: String, measurements: Map<String, Any?>? = null) = actions.onDialogAnswer(status, measurements)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            stringResource(item.nameResId),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = TextPrimary
        )
        Text(
            stringResource(item.instructionResId),
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(8.dp))

        if (isInteractive) {
            Button(
                onClick = { actions.onStartTest() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = NeuralGreen.copy(alpha = 0.18f),
                    contentColor = NeuralGreen
                )
            ) {
                Icon(Icons.Default.PlayArrow, null, tint = NeuralGreen)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.manual_start_test))
            }
            Spacer(Modifier.height(8.dp))
        }

        // Batch 1.0.12 (second part, user decision 2026-10-13: "Chips wrap to a 2nd line"): the chips take their NATURAL width
        // and wrap (FlowRow) instead of sharing the row in equal weights - four equal slots left 39 dp for a label on a 360 dp
        // phone, so a long label was cut. Station-2 build (user decision 2026-10-15): the row holds the THREE answer chips only; the
        // answer `unavailable` is its own line below (ManualUnavailableLine), not a fourth chip.
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(ManualUnavailableLineSpec.TAG_CHIPS),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            FilterChip(
                selected = currentStatus == ManualStatuses.PASS,
                onClick = { actions.onChip(ManualStatuses.PASS) },
                label = { Text(stringResource(R.string.manual_works), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = if (currentStatus == ManualStatuses.PASS) { { Text("✅") } } else null
            )
            FilterChip(
                selected = currentStatus == ManualStatuses.FAIL,
                onClick = { actions.onChip(ManualStatuses.FAIL) },
                label = { Text(stringResource(R.string.manual_not_works), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = if (currentStatus == ManualStatuses.FAIL) { { Text("❌") } } else null
            )
            FilterChip(
                selected = currentStatus == ManualStatuses.NOT_TESTED,
                onClick = { actions.onChip(ManualStatuses.NOT_TESTED) },
                label = { Text(stringResource(R.string.manual_not_tested_btn), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = if (currentStatus == ManualStatuses.NOT_TESTED) { { Text("⏭️") } } else null
            )
        }
        // fieldsVersion 69 (S-15): "unavailable" is the technician's fourth answer (the text is the one string manual_mark_unavailable).
        // A row that holds it must SAY so (otherwise an answered test would look unanswered: no chip selected).
        //  - On the rows that OFFER it (ManualUnavailableOffer) this is the answer's own line, under the chips, at least
        //    ManualUnavailableLineSpec.MIN_GAP_FROM_CHIPS_DP from the chip row: one tap writes it (no confirmation), it is drawn selected
        //    when it is the row's answer, and a tap on ANY chip above writes that chip's answer in its place (actions.onChip - the
        //    same exit every chip uses). The text and the 🚫 mark are the line's own; the text is only the resource.
        //  - On any OTHER row that holds it (an answer from an earlier session or from a row that left the list, e.g. M-12) this
        //    plain label says it: not an option there, so not a control. The "🚫 " in front is a mark, not part of the resource.
        if (offersUnavailable) {
            Spacer(Modifier.height(ManualUnavailableLineSpec.MIN_GAP_FROM_CHIPS_DP.dp))
            ManualUnavailableLine(
                selected = currentStatus == ManualStatuses.UNAVAILABLE,
                onClick = { actions.onChip(ManualStatuses.UNAVAILABLE) },
                modifier = Modifier.testTag(ManualUnavailableLineSpec.TAG_LINE)
            )
        } else if (currentStatus == ManualStatuses.UNAVAILABLE) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "🚫 " + stringResource(R.string.manual_mark_unavailable),
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
    }

    // Route the per-test interactive dialog.
    when (activeDialog) {
        "M-01" -> DeadPixelTestDialog(
            onResult = { status -> answer(status) },
            onDismiss = { actions.onDialogDismissed() }
        )
        TouchGridRecorder.TEST_ID -> TouchGridTestDialog(
            onResult = { status, raw -> answer(status, raw) },
            // Dismissed (back / outside): a run with at least one touch is recorded with the answer
            // the test already holds (or not_tested); a dismissal with no touch records nothing.
            // ManualRowActions.onRunDismissed decides, and prints the line for whichever it was.
            onDismiss = { raw -> actions.onRunDismissed(currentStatus, raw) }
        )
        "M-04" -> AudioToneTestDialog(
            isEarpiece = false,
            onResult = { status -> answer(status) },
            onDismiss = { actions.onDialogDismissed() }
        )
        "M-05" -> AudioToneTestDialog(
            isEarpiece = true,
            onResult = { status -> answer(status) },
            onDismiss = { actions.onDialogDismissed() }
        )
        "M-06" -> MicrophoneTestDialog(
            audioSource = android.media.MediaRecorder.AudioSource.MIC,
            onResult = { status -> answer(status) },
            onDismiss = { actions.onDialogDismissed() }
        )
        // M-07 (secondary mic) is not in the catalog (removed Phase 1.15): no row, no chip question, no dialog.
        // CAMCORDER capture couldn't be guaranteed to use the SECONDARY mic. (Corrected in batch 2, item 5: this said "now a yes/no chip question".)
        "M-08" -> HeadphoneJackTestDialog(
            onResult = { status -> answer(status) },
            onDismiss = { actions.onDialogDismissed() },
            offerUnavailable = offersUnavailable
        )
        // fieldsVersion 71 (A-VOL): the two volume rows, routed by the exported ids, answer with the run's raw like M-02 and are dismissed the same way —
        // a run with at least one volume-key event is recorded with the answer the test already holds (or not_tested), a dismissal with none records nothing.
        VolumeKeyRecorder.TEST_ID_UP -> VolumeButtonTestDialog(
            isVolumeUp = true,
            onResult = { status, raw -> answer(status, raw) },
            onDismiss = { raw -> actions.onRunDismissed(currentStatus, raw) }
        )
        VolumeKeyRecorder.TEST_ID_DOWN -> VolumeButtonTestDialog(
            isVolumeUp = false,
            onResult = { status, raw -> answer(status, raw) },
            onDismiss = { raw -> actions.onRunDismissed(currentStatus, raw) }
        )
        "M-12" -> ChargingPortTestDialog(
            onResult = { status -> answer(status) },
            onDismiss = { actions.onDialogDismissed() },
            offerUnavailable = offersUnavailable
        )
        "M-14" -> WirelessChargingTestDialog(
            onResult = { status -> answer(status) },
            onDismiss = { actions.onDialogDismissed() },
            offerUnavailable = offersUnavailable
        )
        "M-15" -> SdCardTestDialog(
            onResult = { status -> answer(status) },
            onDismiss = { actions.onDialogDismissed() },
            offerUnavailable = offersUnavailable
        )
        "M-17" -> CameraPreviewTestDialog(
            useFront = false,
            onResult = { status -> answer(status) },
            onDismiss = { actions.onDialogDismissed() }
        )
        "M-18" -> CameraPreviewTestDialog(
            useFront = true,
            onResult = { status -> answer(status) },
            onDismiss = { actions.onDialogDismissed() }
        )
        "M-19" -> FlashTorchTestDialog(
            onResult = { status -> answer(status) },
            onDismiss = { actions.onDialogDismissed() }
        )
        // M-20 (fingerprint) is now a yes/no chip question — no dialog. Firing
        // a real BiometricPrompt inside a diagnostic scan was inappropriate.
        "M-21" -> BiometricTestDialog(
            forFace = true,
            onResult = { status -> answer(status) },
            onDismiss = { actions.onDialogDismissed() }
        )
        "M-22" -> VibrationTestDialog(
            onResult = { status -> answer(status) },
            onDismiss = { actions.onDialogDismissed() }
        )
    }
}

@Composable
private fun GradeSummaryCard(scoring: ScoringResult) {
    // Unknown-grade fix: this used to be its own `when (scoring.overallGrade)
    // { "A" -> GradeA; "B" -> GradeB; "C" -> GradeC; else -> GradeD }` — any
    // non-A/B/C/D value, including the explicit "?" sentinel AiScoringEngine
    // emits when a grade can't be resolved (AiVerdictPresentation
    // .resolveOverallGrade's "?" fallback, and the unconditional "?" on a
    // failed AI call), fell into `else` and painted the score percentage
    // GradeD red — the strongest "this failed" signal on the card — while
    // GradeBadge right above it (NeuralCheckComponents.gradeColor) already
    // rendered that same unknown grade neutral. Both now read
    // AiVerdictPresentation.gradeBucket, the single shared classification
    // (see its doc), and the `when` below is exhaustive over the enum (no
    // `else`) so a forgotten bucket is a compile error, not a silent red.
    val gradeColor = when (AiVerdictPresentation.gradeBucket(scoring.overallGrade)) {
        AiVerdictPresentation.GradeBucket.A -> GradeA
        AiVerdictPresentation.GradeBucket.B -> GradeB
        AiVerdictPresentation.GradeBucket.C -> GradeC
        AiVerdictPresentation.GradeBucket.D -> GradeD
        AiVerdictPresentation.GradeBucket.UNKNOWN -> TextMuted
    }
    val isTurkish = Locale.getDefault().language == "tr"
    val description = if (isTurkish && scoring.gradeDescriptionTr.isNotBlank())
        scoring.gradeDescriptionTr else scoring.gradeDescription
    NCCard(modifier = Modifier.fillMaxWidth(), contentPadding = 24.dp) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Order 2026-09-26 (القرار ١٠٢ البند ٣، القرار ٦): "الدرجة
            // الكلية" heading + the score percentage that used to render
            // under it (`scoring.overallScore?.let { NumberFormat.percent(it, 0) }
            // ?: "—"`) are REMOVED, not hidden behind a null-check — score
            // must never reach the user. Nothing replaces them: V6 (the
            // only pipeline that ever reaches this card — shouldShowGradeSummary
            // above) has no coverage/gate-reason fields for القرار ٦'s
            // "المعروض: الحرف · التغطية · سبب البوابة إن أطلقت" to fall back
            // to, so only the letter (already shown by GradeBadge) remains.
            GradeBadge(
                grade = scoring.overallGrade,
                size = 120.dp
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Bold, monospace-feeling identifier shown directly under the grade card.
 * Reads as the canonical scan handle the user can quote when contacting
 * support or attach to a printed receipt. Uses the brand wordmark font for
 * the same techy/dense feel as scores elsewhere on the page; size is large
 * enough to read across the room when the buyer holds up the phone.
 */
@Composable
private fun ShortIdChip(displayId: String) {
    // Outer Box stretches the row so the chip centers within the LazyColumn,
    // matching the centered grade card above it. The inner Box hosts the
    // tinted pill itself.
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .background(
                    NeuralGreen.copy(alpha = 0.12f),
                    androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                )
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = displayId,
                fontFamily = OutfitFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 22.sp,
                color = NeuralGreen
            )
        }
    }
}

@Composable
private fun AxisCard(
    modifier: Modifier = Modifier,
    name: String,
    score: Float
) {
    val color = when { score >= 80 -> StatusPass; score >= 60 -> StatusWarning; else -> StatusFail }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .background(color.copy(alpha = 0.08f), androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        Text(
            text = NumberFormat.percent(score, 0),
            fontFamily = OutfitFamily,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleLarge,
            color = color
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted,
            textAlign = TextAlign.Center,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun CategoryRow(name: String, score: Float, pass: Int, total: Int) {
    val grade = when { score >= 85 -> "A"; score >= 70 -> "B"; score >= 50 -> "C"; else -> "D" }
    NCCard(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        contentPadding = 16.dp
    ) {
        CategoryHeader(
            nameAr = name,
            passCount = pass,
            totalCount = total,
            grade = grade
        )
    }
}

@Composable
private fun IncompleteBanner(reason: String?) {
    Card(
        colors = CardDefaults.cardColors(containerColor = StatusFail.copy(alpha = 0.12f)),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, StatusFail.copy(alpha = 0.4f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Warning, null, tint = StatusFail)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    stringResource(R.string.report_incomplete),
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
                if (!reason.isNullOrBlank()) {
                    Text(reason, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
            }
        }
    }
}

@Composable
private fun CriticalFailuresCard(failures: List<String>) {
    Card(
        colors = CardDefaults.cardColors(containerColor = StatusFail.copy(alpha = 0.10f)),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, StatusFail.copy(alpha = 0.35f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Error, null, tint = StatusFail)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = NumberFormat.toLatin(stringResource(R.string.report_critical_issues_fmt, failures.size)),
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
            }
            Spacer(Modifier.height(8.dp))
            failures.forEach {
                Text("• $it", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        }
    }
}

@Composable
private fun AntiTamperBanner(penalty: Float) {
    Card(
        colors = CardDefaults.cardColors(containerColor = StatusWarning.copy(alpha = 0.10f)),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, StatusWarning.copy(alpha = 0.35f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Shield, null, tint = StatusWarning)
            Spacer(Modifier.width(12.dp))
            Text(
                text = NumberFormat.toLatin(stringResource(R.string.report_tamper_penalty_fmt, NumberFormat.format(penalty, 0))),
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary
            )
        }
    }
}

/**
 * Order 2026-09-26 (القرار ١٠٢، الملحق السابع — يلغي "لا تُغيَّر فرع V6"
 * في الملحق السادس البند ١؛ نفس السبب: النموذج يكتب الدرجة داخل هذين
 * الحقلين، والخادم والموقع يتّسقان في إخفائهما). Narrow display-only
 * shape for the SPECIFIC summary/details/verdict block below — NOT a
 * general replacement for [net.neuralcheck.domain.model.AiAnalysisResult]
 * (which every OTHER card in this function, e.g. [CriticalDefectAlertCard]/
 * [ChangeAnalysisCard]/[AiIssuesSection], keeps reading directly and
 * unaffected). `AiAnalysisResult` itself — Room's `aiAnalysisJson`
 * round-trip (the June-app-upgrade path) AND the server
 * (`fetchExistingReport`) path both funnel into this same class before
 * either ever reaches this Composable — is COMPLETELY UNCHANGED; hiding
 * at this ONE render point covers both sources at once, with no need to
 * touch either reader separately. `grade` is not carried either — its
 * only consumer in this function was the now-removed verdict card call.
 */
internal data class AiV6NarrativeDisplayModel(val summary: String)

internal fun net.neuralcheck.domain.model.AiAnalysisResult.toV6NarrativeDisplayModel() =
    AiV6NarrativeDisplayModel(summary = summary)

/**
 * Launch-flavor AI report layout. Seven cards stacked top-to-bottom:
 *   1. Summary
 *   2. Detailed analysis (collapsible)
 *   3. Verdict (colored border keyed to the overall grade)
 *   4. Issues list (one card per issue, border keyed to severity)
 *   5. Per-category evaluation (sorted D→A so the worst categories come first)
 *   6. Predicted problems
 *
 * Each section is wrapped in [NCCard] so the report reads as a stack of
 * first-class statements rather than a wall of paragraphs — this makes the
 * PDF export and the on-screen view look like the same document.
 *
 * Sections render only when the corresponding AI field has content, so a
 * partial backend payload (e.g. no `predictedProblems`) silently skips that
 * card instead of showing an empty header.
 *
 * Phase 3-b: this is the V6/legacy body. A V7 result (`ai.pipeline ==
 * [AiPipeline.V7_FINDINGS_BASED]`) is routed to [AiAnalysisSectionV7]
 * instead, further down this file — V6 and V7 carry the finding in
 * different root shapes (flat grade/score/summary/... vs
 * overall/categories/narrative; see [AiPipeline] and
 * [net.neuralcheck.domain.model.AiAnalysisResult]'s V7-block doc), and this
 * V6 body must keep reading exactly the V6 fields it always has — every
 * current user's report is V6/legacy (or absent-`pipeline`, treated the
 * same way), so this branch must not change behaviour for them.
 */
@Composable
private fun AiAnalysisSection(ai: AiAnalysisResult) {
    // Order 2026-09-26 (القرار ١٠٢ البند ٢): was a raw `ai.pipeline ==
    // V7_FINDINGS_BASED` check — `cached` (root shape depends on content,
    // not the tag — AiPipeline.CACHED's doc) always fell through to the
    // V6/legacy body below regardless of what it actually held.
    val shape = AiPipeline.classifyShape(
        ai.pipeline,
        hasRootLetterOrGrade = !ai.grade.isNullOrBlank(),
        hasV7Overall = ai.overall != null
    )
    if (shape == AiPipeline.ShapeKind.V7) {
        AiAnalysisSectionV7(ai)
        return
    }
    // Order 2026-09-26 (القرار ١٠٤ البند ١): was a known, disclosed-but-
    // unfixed limitation (078c9f9's own commit message: "an UNKNOWN-shape
    // response falls through to the V6 body with blank fields"). Falling
    // through to the V6 body silently rendered every field blank — an
    // unannounced absence (rule 22's own concern). UNKNOWN now short-
    // circuits here, matching Home/History's own treatment of the same
    // shape (`buildHomeLastScan`/`buildCloudScan`,
    // `report_grade_unavailable`, "الحرف غير متوفر") — the ONLY thing
    // shown for this shape, no summary/details/verdict/issues/categories,
    // since none of that content is trustworthy for a shape this app
    // cannot classify. `GradeSummaryCard` above this function (gated by
    // `shouldShowGradeSummary`, which already routes UNKNOWN the same way
    // this order asks) is already hidden for this case — this is the only
    // remaining place inside the AI section that used to leak blank V6
    // fields.
    if (shape == AiPipeline.ShapeKind.UNKNOWN) {
        AiV7StatusLine(
            icon = "❔",
            text = stringResource(R.string.report_grade_unavailable),
            color = TextMuted
        )
        return
    }
    val isTurkish = Locale.getDefault().language == "tr"
    Column(modifier = Modifier.fillMaxWidth()) {
        // V2 critical defect alert FIRST — the most consequential AI
        // signal (silent silicon corruption). Renders only when at
        // least one verification kernel reported a defect; null/false
        // suppresses it entirely (most scans).
        ai.expectedPerformance?.let { /* no-op anchor */ }
        if (hasCriticalDefect(ai)) {
            CriticalDefectAlertCard(ai = ai)
            Spacer(Modifier.height(14.dp))
        }

        // V2 change analysis — only when this device has a prior scan
        // on record. Sits at the top so a returning user sees "what
        // changed" before re-reading the verdict body.
        ai.changeAnalysis?.let { change ->
            if (change.hasSignificantChange == true || !change.changeReasons.isNullOrEmpty() || change.maintenanceSuspected == true) {
                ChangeAnalysisCard(change = change)
                Spacer(Modifier.height(14.dp))
            }
        }

        // Order 2026-09-26 (القرار ١٠٢، الملحق السابع / الملحق السادس
        // البند ١): built via `toV6NarrativeDisplayModel()` — no
        // `details`/`verdict` at all. `AiDetailsCard`/`AiVerdictCard` are
        // REMOVED from this V6 render path entirely, not null-guarded —
        // there is no expression left to guard, so neither card is ever
        // constructed (no empty card either).
        val v6Narrative = ai.toV6NarrativeDisplayModel()
        if (v6Narrative.summary.isNotBlank()) {
            AiSummaryCard(text = v6Narrative.summary)
            Spacer(Modifier.height(14.dp))
        }

        if (ai.issues.isNotEmpty()) {
            AiIssuesSection(issues = ai.issues)
            Spacer(Modifier.height(14.dp))
        } else if (ai.issuesDeclared == false) {
            // Order 2026-09-24 (جولة جرد القيم الافتراضية، البند ج): an
            // empty `ai.issues` used to render nothing at all — identical
            // to a report that genuinely declared `issues: []` (a real
            // "nothing found" answer). `issuesDeclared == false` means the
            // raw report never carried the `issues` key at all; that must
            // be announced, not silently folded into "no issues found".
            // Order 2026-10-01 (A5): named subject, was bare "غير متوفر".
            AiV7StatusLine(
                icon = "❔",
                text = stringResource(R.string.report_v7_issues_list_unavailable),
                color = TextMuted
            )
            Spacer(Modifier.height(14.dp))
        }

        // Root-cause hypothesis sits *between* issues and category breakdown
        // so the reader's mental model goes: symptoms → cause → per-component
        // detail. Backend may also send the literal string "null" (Gemini's
        // "no opinion" sentinel) — guard against that explicitly.
        ai.rootCause?.let { rootCause ->
            if (rootCause.isNotBlank() && !rootCause.equals("null", ignoreCase = true)) {
                AiRootCauseCard(text = rootCause)
                Spacer(Modifier.height(14.dp))
            }
        }

        // Buyer-advice block: optional triple (casual / gamer / photographer).
        // Render only when at least one persona has copy — an all-null block
        // means the AI passed but had no useful opinion.
        ai.buyerAdvice?.let { advice ->
            if (!advice.casual.isNullOrBlank() ||
                !advice.gamer.isNullOrBlank() ||
                !advice.photographer.isNullOrBlank()
            ) {
                AiBuyerAdviceCard(advice = advice)
                Spacer(Modifier.height(14.dp))
            }
        }

        val cats = ai.categoryScores
        if (!cats.isNullOrEmpty()) {
            // Order 2026-09-26 (القرار ١٠٢، الملحق الرابع، البند ١ — جواب
            // على الملحق الثالث البند ٢): AiCategoryScore (the data-layer
            // DTO — Room's aiAnalysisJson round-trip, PDF, etc.) is
            // UNCHANGED; a narrower display-only model is built here,
            // without `score`/`passCount` at all, for this ONE screen
            // section — see AiCategoryDisplayModel's own doc.
            AiCategoryEvaluationSection(
                categoryScores = cats.mapValues { it.value.toDisplayModel() },
                isTurkish = isTurkish
            )
            Spacer(Modifier.height(14.dp))
        }

        if (ai.predictedProblems.isNotEmpty()) {
            AiPredictedProblemsCard(problems = ai.predictedProblems)
            Spacer(Modifier.height(14.dp))
        }

        // V2 expected-performance card — collapsed by default. Sits
        // near the bottom so it doesn't bury the verdict, but stays
        // visible so the user can audit the AI's prior knowledge.
        ai.expectedPerformance?.let { ep ->
            if (hasAnyExpectedPerformance(ep)) {
                ExpectedPerformanceCard(ep = ep)
                Spacer(Modifier.height(14.dp))
            }
        }

        // The ai_model identifier (e.g. "deepseek-v4-flash" / "gemini-2.5-flash")
        // is intentionally NOT rendered. Internal-only field — kept on the
        // domain model so analytics / Firestore retain it for traceability,
        // but the user must never see which specific model produced their
        // verdict (changes too often, exposes vendor rotation as a UX signal).
    }
}

// ── Root-cause card (amber theme, between issues and categories) ────────────
@Composable
private fun AiRootCauseCard(text: String) {
    // Amber palette — visually distinct from the green AI-summary tier and
    // the red issue cards. Communicates "diagnostic insight, not a problem
    // by itself" without relying on copy alone.
    val amberAccent = Color(0xFFFFB74D)
    val amberBg = Color(0xFF2A1F00)
    val amberBorder = Color(0xFFB45309)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = amberBg),
        border = androidx.compose.foundation.BorderStroke(1.dp, amberBorder.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🔍", fontSize = 20.sp)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.root_cause_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = amberAccent
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary,
                lineHeight = 20.sp
            )
        }
    }
}

// ── Buyer-advice card (one row per persona that has copy) ───────────────────
@Composable
private fun AiBuyerAdviceCard(advice: net.neuralcheck.data.remote.model.BuyerAdvice) {
    NCCard(modifier = Modifier.fillMaxWidth(), contentPadding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("💡", fontSize = 20.sp)
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.buyer_advice_title),
                style = MaterialTheme.typography.titleMedium,
                color = NeuralGreen,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.height(12.dp))
        // Skip personas with blank/null copy so a partial backend payload
        // (e.g. only `casual` populated) doesn't render empty rows.
        advice.casual?.takeIf { it.isNotBlank() }?.let {
            BuyerAdviceRow(icon = "📱", titleResId = R.string.advice_casual, text = it)
        }
        advice.gamer?.takeIf { it.isNotBlank() }?.let {
            BuyerAdviceRow(icon = "🎮", titleResId = R.string.advice_gamer, text = it)
        }
        advice.photographer?.takeIf { it.isNotBlank() }?.let {
            BuyerAdviceRow(icon = "📷", titleResId = R.string.advice_photographer, text = it)
        }
    }
}

@Composable
private fun BuyerAdviceRow(icon: String, titleResId: Int, text: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(icon, fontSize = 16.sp)
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(titleResId),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            lineHeight = 19.sp,
            modifier = Modifier.padding(start = 28.dp, top = 2.dp)
        )
    }
}

// ── Section 2: summary card ─────────────────────────────────────────────────
@Composable
private fun AiSummaryCard(text: String) {
    NCCard(modifier = Modifier.fillMaxWidth(), contentPadding = 16.dp) {
        Text(
            text = stringResource(R.string.report_section_summary),
            style = MaterialTheme.typography.titleMedium,
            color = NeuralGreen,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = TextPrimary,
            lineHeight = 22.sp
        )
    }
}

// ── Section 3: details card (collapsible past 4 lines) ──────────────────────
@Composable
private fun AiDetailsCard(text: String) {
    // Heuristic: anything ≥160 chars is likely to wrap past ~4 lines in Arabic
    // RTL and Turkish — short paragraphs stay fully visible without a toggle
    // so we don't show a "more" button under a two-line paragraph.
    val isLong = text.length > 160
    var expanded by rememberSaveable(text) { mutableStateOf(false) }
    val collapsedMaxLines = 4

    NCCard(modifier = Modifier.fillMaxWidth(), contentPadding = 16.dp) {
        Text(
            text = stringResource(R.string.report_section_details),
            style = MaterialTheme.typography.titleMedium,
            color = SkyCyan,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            lineHeight = 20.sp,
            maxLines = if (!isLong || expanded) Int.MAX_VALUE else collapsedMaxLines,
            overflow = if (!isLong || expanded) TextOverflow.Clip else TextOverflow.Ellipsis
        )
        if (isLong) {
            Spacer(Modifier.height(6.dp))
            TextButton(
                onClick = { expanded = !expanded },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(
                    text = stringResource(
                        if (expanded) R.string.btn_show_less else R.string.btn_show_more
                    ),
                    color = NeuralGreen
                )
            }
        }
    }
}

// ── Section 4: verdict card (grade-colored border + thick accent bar) ───────
/**
 * [grade] is nullable — both `ai.grade` (V6) and `overall.letter` (V7,
 * via [AiAnalysisSectionV7]) are required-by-contract fields that can
 * still arrive null on a partial payload. A null/unrecognized grade
 * renders an explicit pill (`report_grade_unavailable`) instead of the
 * silent `"?"` this call used to receive pre-coalesced at the call site
 * (Phase 3-b fix; see [AiVerdictPresentation]).
 */
@Composable
private fun AiVerdictCard(text: String, grade: String?) {
    val known = AiVerdictPresentation.isKnownGrade(grade)
    val accent = if (known) when (grade!!.trim().uppercase()) {
        "A", "B" -> GradeA
        "C" -> GradeC
        "D" -> GradeD
        else -> TextMuted
    } else TextMuted
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark),
        border = androidx.compose.foundation.BorderStroke(2.dp, accent.copy(alpha = 0.6f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.report_section_verdict),
                    style = MaterialTheme.typography.titleMedium,
                    color = accent,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                    color = accent.copy(alpha = 0.18f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = if (known) grade!!.trim().uppercase() else stringResource(R.string.report_grade_unavailable),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = if (known) 14.sp else 11.sp,
                        color = accent
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = text,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        }
    }
}

// ── Section 5: issues list (card per issue, severity-colored border) ────────
@Composable
private fun AiIssuesSection(issues: List<net.neuralcheck.domain.model.PredictedIssue>) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                // Order 2026-10-01 (#4، أمر القرارات): "المشاكل المكتشفة" ⇐
                // "الملاحظات" — not every listed item is a problem
                // (POSITIVE/INFO severities exist too, see IssueSeverity).
                text = NumberFormat.toLatin(
                    stringResource(R.string.report_section_issues_title_fmt, issues.size)
                ),
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            // Order 2026-10-01 (#5/#6، أمر القرارات، مُصحَّحاً بملحق اليوم
            // نفسه): count of items needing attention, shown beside the
            // title. "والعدد = FAIL + WARNING + unknown، كما هو" — DANGER is
            // where IssueSeverity.fromString maps the V7 "fail" string (see
            // that enum's doc), and UNKNOWN is this app's own
            // unrecognised/absent-severity bucket (added per the addendum:
            // "وإضافة الشدّة غير المعروفة إلى العدّاد كما أمرتُ" — this
            // round's earlier WARNING+DANGER-only count left UNKNOWN out,
            // corrected here). CRITICAL/HIGH/INFO excluded — not named by
            // the order.
            val needsAttentionCount = issues.count {
                it.severity == IssueSeverity.WARNING ||
                    it.severity == IssueSeverity.DANGER ||
                    it.severity == IssueSeverity.UNKNOWN
            }
            // "0: لا يُعرض السطر" — hidden in CODE before any
            // pluralStringResource call, never by relying on the plurals
            // resource's absent 'zero' item (see
            // report_issues_needing_attention's own doc in strings.xml for
            // why: Android does not reliably select 'zero' for Arabic on
            // every OS version — the addendum's own caution).
            if (needsAttentionCount > 0) {
                Text(
                    text = NumberFormat.toLatin(
                        pluralStringResource(
                            R.plurals.report_issues_needing_attention,
                            needsAttentionCount,
                            needsAttentionCount
                        )
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = StatusWarning,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        issues.forEach { issue ->
            // V2 severity expansion: CRITICAL = silent silicon defect
            // (darker red than DANGER); HIGH = serious-but-not-silicon
            // (orange). Both reuse existing colour tokens with hex
            // literals applied at the call site to avoid bloating the
            // palette with two new theme values.
            val (accent, emoji) = when (issue.severity) {
                IssueSeverity.CRITICAL -> androidx.compose.ui.graphics.Color(0xFFB91C1C) to "🚨"
                IssueSeverity.DANGER -> StatusFail to "❗"
                IssueSeverity.HIGH -> androidx.compose.ui.graphics.Color(0xFFEA580C) to "⚠️"
                IssueSeverity.WARNING -> StatusWarning to "⚠️"
                IssueSeverity.INFO -> SkyCyan to "ℹ️"
                // Order 2026-09-24 (جولة جرد القيم الافتراضية، البند ١):
                // an unrecognised/absent severity string used to fall into
                // INFO's calm blue "ℹ️" — indistinguishable from a real
                // informational finding. Distinct neutral grey (TextSecondary,
                // already used elsewhere for muted/secondary text, no new
                // color token invented) + "❓" + the explicit label below,
                // so this state cannot be mistaken for INFO.
                IssueSeverity.UNKNOWN -> TextSecondary to "❓"
            }
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                colors = CardDefaults.cardColors(containerColor = CardDark),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, accent.copy(alpha = 0.5f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(emoji, fontSize = 16.sp)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            // Order 2026-09-24 (جولة جرد القيم الافتراضية،
                            // البند ب): AiScoringEngine maps a missing/blank
                            // `issues[].title` to `""` — rendered here as a
                            // blank title, indistinguishable from a real
                            // (if oddly empty) string. Declares the absence
                            // instead, reusing the existing generic
                            // report_v7_field_unavailable string (same
                            // mechanism AiV7GateReasonCard already uses for
                            // an absent `name`/`ceilingLetter`), rather than
                            // rendering nothing.
                            // Order 2026-10-01 (A5): named subject, was bare
                            // "غير متوفر" with nothing saying it was the title.
                            text = issue.title.takeIf { it.isNotBlank() }
                                ?: stringResource(R.string.report_v7_issue_title_unavailable),
                            fontWeight = FontWeight.Bold,
                            color = accent,
                            fontSize = 15.sp,
                            modifier = Modifier.weight(1f)
                        )
                        if (issue.severity == IssueSeverity.UNKNOWN) {
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.report_v7_issue_severity_unknown),
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                    if (issue.description.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = issue.description,
                            color = TextSecondary,
                            fontSize = 13.sp,
                            lineHeight = 19.sp
                        )
                    }
                }
            }
        }
    }
}

// ── Section 6: category evaluation (sorted D→A) ─────────────────────────────
/**
 * Order 2026-09-26 (القرار ١٠٢، الملحق الرابع، البند ١ — جواب على الملحق
 * الثالث البند ٢: "ScoringResult طبقة بيانات ... وما تقرؤه بطاقة فئة
 * launch يُبنى منه نموذجَ عرض بلا passCount وبلا score، وفيه الحرف
 * والتغطية"). Built FROM [net.neuralcheck.data.remote.model.AiCategoryScore]
 * (the real data-layer DTO — unchanged; Room's `aiAnalysisJson` round-trip,
 * PDF generation, etc. keep reading it directly) at the ONE call site that
 * feeds [AiCategoryEvaluationSection] — not a general replacement for
 * `AiCategoryScore` itself. `totalCount`/`declaredCount`/`excludedCount`
 * are kept because [CategoryPresentation.notRunCount] (the coverage line)
 * needs them; `passCount` and `score` are NOT carried at all — this
 * struct cannot re-leak either one by accident, the same guarantee
 * `HomeViewModel.LastScan`/`ScanHistoryViewModel.CloudScan` already have
 * for the overall score (القرار ١٠٢، الملحق الثالث، البند ٢).
 *
 * `internal`, not `private` — same reasoning as `scanHeaderDateText`
 * elsewhere in this file: makes the mapping directly unit-testable
 * (rule 41, against the real `v6-reports-document-sample.json`) without
 * drawing the screen (Robolectric and Compose test infrastructure are test dependencies since 3284cca).
 */
internal data class AiCategoryDisplayModel(
    val grade: String,
    val measured: Boolean?,
    val totalCount: Int,
    val declaredCount: Int?,
    val excludedCount: Int?,
    val note: String,
    val expectedRange: String?
)

internal fun net.neuralcheck.data.remote.model.AiCategoryScore.toDisplayModel() = AiCategoryDisplayModel(
    grade = grade,
    measured = measured,
    totalCount = totalCount,
    declaredCount = declaredCount,
    excludedCount = excludedCount,
    note = note,
    expectedRange = expectedRange
)

@Composable
private fun AiCategoryEvaluationSection(
    categoryScores: Map<String, AiCategoryDisplayModel>,
    isTurkish: Boolean
) {
    // D comes first so the worst categories sit at the top of the card — it
    // matches how a technician reads the report: what's broken, then what's
    // healthy. `?`/unknown grades sink to the bottom.
    val priority = mapOf("D" to 4, "C" to 3, "B" to 2, "A" to 1)
    val sorted = categoryScores.entries.sortedByDescending {
        priority[it.value.grade.uppercase()] ?: 0
    }
    // "?" grades keep sinking to the bottom via the `?: 0` above — an
    // unmeasured category is not a finding, so it must not compete with
    // real D-grade failures for the reader's attention.

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.report_section_category_eval),
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        sorted.forEach { (key, cat) ->
            val label = CategoryTranslations.labelFor(key)
            val displayName = if (isTurkish) label.tr else label.ar
            // Unmeasured = every test in the category came back
            // not_tested / unavailable. Rendered as an explicit neutral
            // state: never red (that would read as "failed"), never green.
            val unmeasured = CategoryPresentation.isUnmeasured(
                measured = cat.measured,
                grade = cat.grade,
                totalCount = cat.totalCount
            )
            val gradeColor = when {
                unmeasured -> TextMuted
                else -> when (cat.grade.uppercase()) {
                    "A" -> GradeA; "B" -> GradeB; "C" -> GradeC; "D" -> GradeD
                    else -> TextMuted
                }
            }
            NCCard(
                modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                contentPadding = 14.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = label.icon,
                        fontSize = 22.sp
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    // Order 2026-09-26 (القرار ١٠٢، الملحق الرابع، البند ١):
                    // the pass-rate percentage ("57%") that used to render
                    // here (`CategoryPresentation.passPercent(cat.passCount,
                    // cat.totalCount)`, `cat.passCount`/`score` from
                    // AiCategoryScore) is REMOVED, not hidden behind a
                    // null-check — القرار ٦ enumerates what's shown ("الحرف
                    // · التغطية · سبب البوابة") and a pass rate is not one
                    // of them; showing it beside a gate-ceilinged letter is
                    // exactly the "C مع 100 أسوأ من C وحدها" contradiction
                    // the order names. The coverage line below (`notRun`/
                    // `report_category_coverage_fmt`) is unaffected — it is
                    // the allowed "التغطية". `AiCategoryDisplayModel` (this
                    // section's own parameter type, see its doc) does not
                    // even carry `passCount`/`score` any more, so this
                    // cannot silently come back.
                    Surface(
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                        color = gradeColor.copy(alpha = 0.18f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, gradeColor.copy(alpha = 0.5f))
                    ) {
                        // The bare "?" is meaningless to a buyer — replace
                        // it with the words, so the pill itself explains.
                        Text(
                            text = if (unmeasured) {
                                stringResource(R.string.report_category_unmeasured)
                            } else cat.grade,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = if (unmeasured) 12.sp else 14.sp,
                            color = gradeColor
                        )
                    }
                }
                // Coverage line: how much of the category was actually
                // exercised. `totalCount` is what EXECUTED, not what the
                // category declares — gpu arrives 2/2 while declaring 5,
                // so without this the row reads "100% — A" though three
                // tests never ran. The gap is the server's own
                // excludedCount when present (authoritative), derived only
                // on payloads predating that field.
                val notRun = CategoryPresentation.notRunCount(
                    excludedCount = cat.excludedCount,
                    declaredCount = cat.declaredCount,
                    totalCount = cat.totalCount
                )
                if (notRun != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = NumberFormat.toLatin(
                            stringResource(
                                R.string.report_category_coverage_fmt,
                                cat.totalCount,
                                CategoryPresentation.declaredTotal(
                                    cat.declaredCount, cat.totalCount, notRun
                                ),
                                notRun
                            )
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = StatusWarning,
                        fontWeight = FontWeight.Medium
                    )
                }
                if (cat.note.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = cat.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        lineHeight = 19.sp
                    )
                }
                // expected_range contextualises a MEASUREMENT against what
                // the device's tier normally does. An unmeasured category
                // has no measurement to contextualise, so it is withheld
                // there — printing a "typical range" beside "not measured"
                // invites the reader to fill the blank with the range.
                // NOTE: ai_grade / grade_source / reconciled are parsed but
                // deliberately NEVER rendered (see AiCategoryScore docs).
                val range = cat.expectedRange
                if (!unmeasured && !range.isNullOrBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "📐 " + range,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted,
                        lineHeight = 17.sp
                    )
                }
                // Phase 1.15: surface categories are inspector-stated, not
                // NeuralCheck-verified — tag so the buyer re-checks them.
                if (key.trim().lowercase() in SURFACE_CATEGORY_KEYS) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "🔎 " + stringResource(R.string.report_surface_category_tag),
                        style = MaterialTheme.typography.labelSmall,
                        color = SkyCyan,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

// ── Phase 1.15: depth-vs-surface framing ────────────────────────────────────
/** Categories a buyer can verify by eye/hand in seconds → inspector-stated,
 *  NOT guaranteed by NeuralCheck (which guarantees the hidden "depth":
 *  cpu/gpu/storage/thermal/memory/battery/security/cellular/gps/sensors).
 *  Tagged in the report so the buyer re-checks them. NOTE: `wireless` mixes
 *  WiFi/BT (surface) with NFC (depth) — tagged surface as the practical
 *  majority; revisit if NFC needs separate treatment.
 *
 *  Order 2026-10-01 (A5): used ONLY by [AiCategoryEvaluationSection] (the V6
 *  render path, `AiCategoryDisplayModel`) below — [AiCategoryRowV7] (V7) now
 *  derives the same tag from `categories.<key>.state == ATTESTED_ONLY`
 *  instead (see its own call site), because this hand-written key list
 *  disagreed with the V7 payload's own field (display/wireless are
 *  state=MEASURED, not attested, in the golden sample). `AiCategoryScore`
 *  (V6's DTO — grade/measured/totalCount/declaredCount/excludedCount/note/
 *  expectedRange only, see `AiCategoryDisplayModel`'s doc) carries no
 *  equivalent state field, so this hand-written list could not be replaced
 *  the same way for V6 without guessing (rule 13) — left unchanged, flagged
 *  in this round's report rather than silently decided either way. */
private val SURFACE_CATEGORY_KEYS = setOf("display", "camera", "audio", "input", "wireless")

@Composable
private fun BuyerSurfaceNoteCard() {
    NCCard(modifier = Modifier.fillMaxWidth(), contentPadding = 16.dp) {
        Text(
            text = "🛡️ " + stringResource(R.string.report_buyer_note_title),
            style = MaterialTheme.typography.titleMedium,
            color = NeuralGreen,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.report_buyer_note_body),
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            lineHeight = 20.sp
        )
    }
}

// ── Section 7: predicted problems card (bullet list) ────────────────────────
@Composable
private fun AiPredictedProblemsCard(problems: List<net.neuralcheck.data.remote.model.PredictedProblem>) {
    NCCard(modifier = Modifier.fillMaxWidth(), contentPadding = 16.dp) {
        Text(
            text = stringResource(R.string.report_section_predicted),
            style = MaterialTheme.typography.titleMedium,
            color = StatusWarning,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(10.dp))
        problems
            .filter { it.prediction.isNotBlank() }
            .forEach { problem ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        "•",
                        color = StatusWarning,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = problem.prediction,
                            color = TextSecondary,
                            fontSize = 14.sp,
                            lineHeight = 20.sp
                        )
                        // Timeframe gets a small muted line below the
                        // prediction when present — when null (legacy
                        // bare-string entry) we render only the prediction
                        // exactly as the V1 layout did.
                        if (!problem.timeframe.isNullOrBlank()) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = problem.timeframe,
                                color = TextMuted,
                                fontSize = 11.sp,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                            )
                        }
                    }
                }
            }
    }
}

// ── V2: Critical defect alert (UI 4) ─────────────────────────────────────────

/** Returns true iff the AI reported at least one CRITICAL-severity
 *  issue (silicon defect) — the trigger for the prominent red banner
 *  at the top of the report. */
private fun hasCriticalDefect(ai: AiAnalysisResult): Boolean =
    ai.issues.any { it.severity == IssueSeverity.CRITICAL }

@Composable
private fun CriticalDefectAlertCard(ai: AiAnalysisResult) {
    // Categories are derived from the CRITICAL issues themselves so
    // the alert says "fail in [cpu, memory]" instead of a generic
    // "fail somewhere". Falls back to "verification" when no issue
    // ships a category tag.
    val cats = ai.issues
        .filter { it.severity == IssueSeverity.CRITICAL }
        .mapNotNull { it.category?.takeIf { c -> c.isNotBlank() } }
        .distinct()
    val context = LocalContext.current
    val categoriesText = if (cats.isEmpty()) {
        stringResource(R.string.critical_defect_categories_cpu)  // fallback
    } else {
        cats.joinToString(" / ") { c ->
            when (c.lowercase()) {
                "cpu" -> context.getString(R.string.critical_defect_categories_cpu)
                "gpu" -> context.getString(R.string.critical_defect_categories_gpu)
                "memory" -> context.getString(R.string.critical_defect_categories_memory)
                "storage" -> context.getString(R.string.critical_defect_categories_storage)
                else -> c
            }
        }
    }
    val criticalRed = androidx.compose.ui.graphics.Color(0xFFB91C1C)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = criticalRed.copy(alpha = 0.18f)),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(2.dp, criticalRed),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(18.dp)) {
            Text(
                text = stringResource(R.string.critical_defect_title),
                style = MaterialTheme.typography.titleMedium,
                color = criticalRed,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.critical_defect_body, categoriesText),
                color = TextPrimary,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        }
    }
}

// ── V2: Change analysis card (UI 2) ──────────────────────────────────────────

@Composable
private fun ChangeAnalysisCard(change: net.neuralcheck.data.remote.model.ChangeAnalysis) {
    // A115: see net.neuralcheck.util.ChangeAnalysisPresentation's class doc
    // — the old `when` folded a null/unrecognized changeDirection into the
    // same `else` branch as the real "stable" value, so an absent field
    // rendered as green/"stable". Exhaustive `when` over the enum below (no
    // `else`) so a forgotten bucket fails to compile instead of silently
    // reading as a value, same convention as AiVerdictPresentation.gradeBucket's
    // call sites.
    val (accent, statusKey) = when (
        net.neuralcheck.util.ChangeAnalysisPresentation.classifyDirection(change.changeDirection)
    ) {
        net.neuralcheck.util.ChangeAnalysisPresentation.DirectionKind.IMPROVED ->
            SkyCyan to R.string.change_analysis_improved
        net.neuralcheck.util.ChangeAnalysisPresentation.DirectionKind.DETERIORATED ->
            StatusFail to R.string.change_analysis_deteriorated
        net.neuralcheck.util.ChangeAnalysisPresentation.DirectionKind.STABLE ->
            StatusPass to R.string.change_analysis_stable
        net.neuralcheck.util.ChangeAnalysisPresentation.DirectionKind.UNKNOWN ->
            StatusWarning to R.string.change_analysis_unknown
    }
    NCCard(modifier = Modifier.fillMaxWidth(), contentPadding = 16.dp) {
        Text(
            text = stringResource(R.string.change_analysis_title),
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(statusKey),
            color = accent,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
        change.changeReasons?.takeIf { it.isNotEmpty() }?.let { reasons ->
            Spacer(Modifier.height(10.dp))
            reasons.filter { it.isNotBlank() }.forEach { reason ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text("•", color = accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = reason,
                        color = TextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        // Phase 1.15: "possibly serviced" flag — a manual test went fail→pass
        // since the prior scan (set by the backend's re-evaluation logic).
        if (change.maintenanceSuspected == true) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = "🛠 " + (change.maintenanceNote?.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.report_maintenance_suspected)),
                color = StatusWarning,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 18.sp
            )
        }
    }
}

// ── V2: Expected performance card (UI 2) ─────────────────────────────────────

private fun hasAnyExpectedPerformance(ep: net.neuralcheck.data.remote.model.ExpectedPerformance): Boolean =
    !ep.cpuThrottleExpected.isNullOrBlank() ||
    !ep.cpuTempExpected.isNullOrBlank() ||
    !ep.gpuFpsStabilityExpected.isNullOrBlank() ||
    !ep.memoryExpected.isNullOrBlank() ||
    !ep.storageExpected.isNullOrBlank() ||
    !ep.batteryDrainExpected.isNullOrBlank()

@Composable
private fun ExpectedPerformanceCard(ep: net.neuralcheck.data.remote.model.ExpectedPerformance) {
    val confidenceText = when (ep.knowledgeConfidence?.lowercase()) {
        "high" -> stringResource(R.string.expected_performance_confidence_high)
        "medium" -> stringResource(R.string.expected_performance_confidence_medium)
        "low" -> stringResource(R.string.expected_performance_confidence_low)
        else -> null
    }
    NCCard(modifier = Modifier.fillMaxWidth(), contentPadding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.expected_performance_title),
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            confidenceText?.let { conf ->
                Surface(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                    color = SkyCyan.copy(alpha = 0.18f)
                ) {
                    Text(
                        text = conf,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        fontSize = 11.sp,
                        color = SkyCyan,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        // Each non-null field gets one bullet line. Order matters —
        // CPU first since it's the headline V2 stress, then GPU /
        // memory / storage / battery.
        listOfNotNull(
            ep.cpuThrottleExpected?.takeIf { it.isNotBlank() },
            ep.cpuTempExpected?.takeIf { it.isNotBlank() },
            ep.gpuFpsStabilityExpected?.takeIf { it.isNotBlank() },
            ep.memoryExpected?.takeIf { it.isNotBlank() },
            ep.storageExpected?.takeIf { it.isNotBlank() },
            ep.batteryDrainExpected?.takeIf { it.isNotBlank() }
        ).forEach { line ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                verticalAlignment = Alignment.Top
            ) {
                Text("•", color = SkyCyan, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = line,
                    color = TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

// ── V7 findings-based report section (Phase 3-b) ─────────────────────────────
//
// Reached only from AiAnalysisSection's `ai.pipeline == AiPipeline.V7_FINDINGS_BASED`
// branch above. V7's root shape carries `overall`/`categories`/`narrative`
// instead of V6's flat `grade`/`score`/`summary`/`details`/`verdict`/
// `categoryScores` (AiPipeline.kt, AiAnalysisResult.kt's V7-block doc) —
// this section reads ONLY the V7-shaped fields. It must never fall back to
// the V6 fields on the same AiAnalysisResult: AiScoringEngine does not
// populate them for a V7 response (narrative text lives at
// ai.narrative.summary, not ai.summary — see AiScoringEngine.kt's
// pass-through comment on the V7 block).
//
// `ai.issues` is the one exception — it IS shared shape between V6 and V7
// (AiScoringEngine maps `response.issues` into `List<PredictedIssue>`
// unconditionally, not gated on pipeline; see AiIssuesSection reuse below),
// so it is read the same way here as in the V6 body.
//
// Every overall-level field with NO V6 equivalent — coverage/coveragePct/
// partialAssessment/partialAssessmentTag/governingGateReason/noDataAtAll/
// zeroMeasuredMessage/abstentionReasons/harnessError/harnessErrorReason —
// and every category-level letterSource/adjustmentReason/adjustmentEvidence
// is surfaced explicitly below, never silently dropped (Phase 3-b's
// mandatory rule — a required field's absence must produce a visible state,
// not a default/blank/success-looking render). The golden sample
// (`app/src/test/resources/contract/v7-client-payload-sample.json`) has
// zero LLM_ADJUSTED categories — every one of its 15 is DETERMINISTIC — so
// that branch below is exercised only by the artificial fixture in
// AiV7PresentationTest, not by any real observed payload.

/**
 * Order 2026-09-26 (القرار ١٠٢، الملحق السادس، البند ١). `narrative.details`
 * and `narrative.verdict` are REMOVED from the V7 render path — the model
 * wrote real numeric scores into these two free-text fields in production
 * golden samples (e.g. "مع أن متوسط الفئات المقيسة بلغ 93.75",
 * `v7-reports-document-sample.json:1722`; "المعالج: الحرف A ونجاح 5 من 5
 * ودرجة 100", `v7-reports-document-sample-cpu-c06-downgrade.json` and
 * `-security-downgrade.json` — all three independently re-verified here,
 * not taken on the order's word alone), which القرار ٦ forbids showing.
 * The website already withholds these two fields from buyers, so both
 * surfaces now agree.
 *
 * [net.neuralcheck.data.remote.model.AiNarrativeV7] itself (the shared
 * data-layer DTO — `AiAnalysisResponse`, `AiAnalysisResult`, Room's
 * `aiAnalysisJson` round-trip) is COMPLETELY UNCHANGED — this narrower
 * struct is built at the ONE V7 render call site below, `internal` (not
 * `private`) for the same testability reason as `AiCategoryDisplayModel`.
 *
 * The V6 body (`ai.details`/`ai.verdict`, ReportScreen.kt ~:1386/:1391)
 * is DELIBERATELY NOT touched here — out of this order's named scope
 * (it only names the V7 lines), pending the user's own answer on V6.
 */
internal data class AiNarrativeDisplayModel(
    val summary: String?,
    val buyerAdvice: net.neuralcheck.data.remote.model.BuyerAdvice?
)

internal fun net.neuralcheck.data.remote.model.AiNarrativeV7.toDisplayModel() = AiNarrativeDisplayModel(
    summary = summary,
    buyerAdvice = buyerAdvice
)

@Composable
private fun AiAnalysisSectionV7(ai: AiAnalysisResult) {
    val isTurkish = Locale.getDefault().language == "tr"
    val overall = ai.overall

    Column(modifier = Modifier.fillMaxWidth()) {
        if (overall == null) {
            // ai.pipeline says V7 but there is no `overall` block to read —
            // the same condition AiAnalysisResult.isComplete() itself
            // treats as "not a renderable V7 result". Explicit top-level
            // failure state; never fall through to an empty section.
            AiV7IncompleteCard()
            return@Column
        }

        AiV7OverallCard(
            overall = overall,
            // Order 2026-09-24 (جولة جرد القيم الافتراضية، البند ح):
            // reversed default, same as narrativeIncompleteDeclared/
            // issuesDeclared above — an unknown (never-computed) declared
            // signal must NOT be read as "confirmed present".
            harnessErrorDeclared = ai.harnessErrorDeclared ?: false,
            noDataAtAllDeclared = ai.noDataAtAllDeclared ?: false,
            partialAssessmentDeclared = ai.partialAssessmentDeclared ?: false,
            // Order 2026-10-01 (أمر «abstentionReasons»): needed to pick the
            // Arabic/Turkish category name for each abstentionReasons entry.
            isTurkish = isTurkish
        )
        Spacer(Modifier.height(14.dp))

        // Order 2026-10-07 (A1 + decisions S1 and "item 4"): the gate card is
        // shown ONLY when the server sent its `text` sentence; otherwise it
        // is hidden ENTIRELY — no card, no title on its own, and no Spacer
        // left behind (the Spacer below is inside the Show branch). The
        // decision is [gateReasonCardDecision], unit-tested directly. The
        // overall letter and the partial-assessment tag are rendered by
        // [AiV7OverallCard] ABOVE this call and are not touched by it.
        when (val gate = gateReasonCardDecision(overall.governingGateReason)) {
            is GateReasonCardDecision.Show -> {
                AiV7GateReasonCard(sentence = gate.sentence)
                Spacer(Modifier.height(14.dp))
            }
            GateReasonCardDecision.HideNoGate -> Unit
            GateReasonCardDecision.HideTextAbsent -> {
                // Rule 22: the absence is explicit in the LOG, not on screen.
                val absent = overall.governingGateReason
                LaunchedEffect(absent?.name, absent?.source) {
                    Log.w(
                        "NeuralCheck.Report",
                        "governingGateReason without text (report from before the server's " +
                            "sentence, or UNMATCHED) — card hidden; name='${absent?.name}' " +
                            "ceilingLetter='${absent?.ceilingLetter}' source='${absent?.source}' " +
                            "logged, not displayed"
                    )
                }
            }
        }

        // narrativeIncomplete banner (order 2026-09-23، القرار ٧٢، البند ٢).
        //
        // REVERSED 2026-09-24 (جولة جرد القيم الافتراضية، البند د) from this
        // section's prior `?: true` default ("nothing known missing" ->
        // COMPLETE) — mirroring 0887730's reversal for
        // categoriesMissingLetterCeilingReason's `?: emptySet()` -> `?: cats.keys`.
        // An older Room-cached AiAnalysisResult whose narrativeIncompleteDeclared
        // was never computed (predates this field) is NOT "known complete" —
        // that is a real answer this app cannot honestly give for a
        // pre-existing row, and `?: true` was silently giving it anyway
        // (classifyNarrativeIncomplete(true, null) -> COMPLETE, banner
        // never shown). `?: false` now routes that case to NOT_DECLARED,
        // which IS distinguished on screen below (reusing the existing
        // generic report_v7_field_unavailable string, same precedent as
        // the ceiling-reason fix) instead of being silently indistinguishable
        // from a genuinely complete narrative.
        when (
            AiV7Presentation.classifyNarrativeIncomplete(
                declared = ai.narrativeIncompleteDeclared ?: false,
                narrativeIncomplete = ai.narrativeIncomplete
            )
        ) {
            AiV7Presentation.NarrativeIncompleteKind.INCOMPLETE -> {
                AiV7StatusLine(
                    icon = "⚠️",
                    text = stringResource(R.string.report_v7_narrative_incomplete),
                    color = StatusWarning
                )
                Spacer(Modifier.height(14.dp))
            }
            AiV7Presentation.NarrativeIncompleteKind.NOT_DECLARED -> {
                // Order 2026-10-01 (A5): named subject, was bare "غير متوفر".
                AiV7StatusLine(
                    icon = "❔",
                    text = stringResource(R.string.report_v7_narrative_status_unavailable),
                    color = TextMuted
                )
                Spacer(Modifier.height(14.dp))
            }
            AiV7Presentation.NarrativeIncompleteKind.COMPLETE -> Unit
        }

        // Order 2026-09-26 (القرار ١٠٢، الملحق السادس، البند ١): built via
        // `toDisplayModel()` — `AiNarrativeDisplayModel` carries no
        // `details`/`verdict` at all (see that struct's own doc for why).
        // `AiDetailsCard`/`AiVerdictCard` are REMOVED from this V7 render
        // path entirely, not null-guarded — there is no expression left to
        // guard. Summary and buyer-advice are unaffected.
        val narrative = ai.narrative?.toDisplayModel()
        narrative?.summary?.takeIf { it.isNotBlank() }?.let {
            AiSummaryCard(text = it)
            Spacer(Modifier.height(14.dp))
        }
        narrative?.buyerAdvice?.let { advice ->
            if (!advice.casual.isNullOrBlank() ||
                !advice.gamer.isNullOrBlank() ||
                !advice.photographer.isNullOrBlank()
            ) {
                AiBuyerAdviceCard(advice = advice)
                Spacer(Modifier.height(14.dp))
            }
        }

        if (ai.issues.isNotEmpty()) {
            AiIssuesSection(issues = ai.issues)
            Spacer(Modifier.height(14.dp))
        } else if (ai.issuesDeclared == false) {
            // See the V6 body's identical branch above for the full
            // rationale (order 2026-09-24، جولة جرد القيم الافتراضية، البند ج).
            // Order 2026-10-01 (A5): named subject, was bare "غير متوفر".
            AiV7StatusLine(
                icon = "❔",
                text = stringResource(R.string.report_v7_issues_list_unavailable),
                color = TextMuted
            )
            Spacer(Modifier.height(14.dp))
        }

        val cats = ai.categories
        if (!cats.isNullOrEmpty()) {
            AiCategoryEvaluationSectionV7(
                categories = cats,
                isTurkish = isTurkish,
                // Order 2026-09-23 (القرار ٧٢) item 3 — REVERSED from this
                // section's prior fallback (`?: emptySet()`), per the
                // backend's confirmed answer (neuralcheck-backend @
                // e8cd67c, analyzeDevice.js:109-114/buildDisplayShapeV7.js:288,
                // test/v7AbsenceVsNullFieldsGuard.test.js 56/56): the
                // backend ALWAYS emits letterCeilingReason on every
                // category (null when no ceiling), so a live report NEVER
                // has a null list here — `ai.categoriesMissingLetterCeilingReason == null`
                // now means ONLY "this whole signal was never computed"
                // (a Room-cached AiAnalysisResult from before this field
                // existed), not "nothing is missing". Falling back to
                // `cats.keys` (every category in THIS report) makes every
                // category classify NOT_DECLARED in that case — an
                // explicit "undeclared" state, distinct from the real
                // NO_CEILING answer a present-but-empty set still produces
                // (unchanged: `?:` only fires on null, never on an actual
                // empty Set).
                missingLetterCeilingReason = ai.categoriesMissingLetterCeilingReason ?: cats.keys
            )
            Spacer(Modifier.height(14.dp))
        }
    }
}

/** `ai.pipeline == V7_FINDINGS_BASED` but `ai.overall == null` — explicit failure state. */
@Composable
private fun AiV7IncompleteCard() {
    Card(
        colors = CardDefaults.cardColors(containerColor = StatusFail.copy(alpha = 0.12f)),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, StatusFail.copy(alpha = 0.4f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Warning, null, tint = StatusFail)
            Spacer(Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.report_v7_incomplete),
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )
        }
    }
}

/**
 * Overall-block card: final letter + explicit-absence pill (reuses the
 * same [AiVerdictPresentation] rule as the V6 verdict card), coverage
 * percentage, and the ranked harnessError / noDataAtAll / partialAssessment
 * status line (see [AiV7Presentation.classifyOverallStatus] for the
 * priority — these three are independent nullable fields in the DTO but
 * are presented as a single top banner). zeroMeasuredMessage and
 * abstentionReasons are independent auxiliary fields, shown underneath
 * whenever present regardless of which status branch fired above.
 */
@Composable
private fun AiV7OverallCard(
    overall: net.neuralcheck.data.remote.model.AiOverallV7,
    harnessErrorDeclared: Boolean,
    noDataAtAllDeclared: Boolean,
    partialAssessmentDeclared: Boolean,
    isTurkish: Boolean
) {
    val known = AiVerdictPresentation.isKnownGrade(overall.letter)
    val accent = if (known) when (overall.letter!!.trim().uppercase()) {
        "A", "B" -> GradeA
        "C" -> GradeC
        "D" -> GradeD
        else -> TextMuted
    } else TextMuted
    val status = AiV7Presentation.classifyOverallStatus(
        harnessError = overall.harnessError,
        noDataAtAll = overall.noDataAtAll,
        partialAssessment = overall.partialAssessment,
        harnessErrorDeclared = harnessErrorDeclared,
        noDataAtAllDeclared = noDataAtAllDeclared,
        partialAssessmentDeclared = partialAssessmentDeclared
    )
    val coveragePct = AiV7Presentation.coveragePercent(overall.coveragePct, overall.coverage)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark),
        border = androidx.compose.foundation.BorderStroke(2.dp, accent.copy(alpha = 0.6f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.report_v7_overall_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = accent,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                    color = accent.copy(alpha = 0.18f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = if (known) overall.letter!!.trim().uppercase() else stringResource(R.string.report_grade_unavailable),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = if (known) 16.sp else 11.sp,
                        color = accent
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            // Order 2026-10-01 (A2): general provenance line — the letter is
            // computed from measurement, the narrative/advice are AI. One
            // line here, not repeated per category (the existing per-
            // category report_v7_letter_source_deterministic/llm_adjusted
            // rows below cover that distinct DETERMINISTIC/LLM_ADJUSTED
            // question — see A7, untouched by this line).
            Text(
                text = stringResource(R.string.report_v7_letter_basis_note),
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (coveragePct != null) {
                    NumberFormat.toLatin(stringResource(R.string.report_v7_coverage_fmt, coveragePct))
                } else {
                    stringResource(R.string.report_v7_coverage_unavailable)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (coveragePct != null) TextSecondary else TextMuted
            )
            when (status) {
                AiV7Presentation.OverallStatusKind.HARNESS_ERROR -> {
                    // Order 2026-10-01 (أمر القرارات، A8/S-13): was
                    // overall.harnessErrorReason shown VERBATIM — a raw
                    // internal code (e.g. "FUNCTIONAL_RESULTS_UNAVAILABLE"),
                    // exactly rule 22's "الرمز الخام لا يُعرض على الشاشة".
                    // Now harnessErrorMessage (the new human-readable field)
                    // or an explicit "unavailable" line — the raw reason is
                    // never read for display here again, only logged.
                    Spacer(Modifier.height(10.dp))
                    if (AiV7Presentation.harnessErrorDisplayText(overall.harnessErrorMessage) == null &&
                        !overall.harnessErrorReason.isNullOrBlank()
                    ) {
                        LaunchedEffect(overall.harnessErrorReason) {
                            Log.w(
                                "NeuralCheck.Report",
                                "harnessError=true, no harnessErrorMessage — raw harnessErrorReason=" +
                                    "'${overall.harnessErrorReason}' logged, not displayed"
                            )
                        }
                    }
                    AiV7StatusLine(
                        icon = "⛔",
                        text = AiV7Presentation.harnessErrorDisplayText(overall.harnessErrorMessage)
                            ?: stringResource(R.string.report_v7_harness_error_message_unavailable),
                        color = StatusFail
                    )
                }
                AiV7Presentation.OverallStatusKind.NO_DATA_AT_ALL -> {
                    Spacer(Modifier.height(10.dp))
                    AiV7StatusLine(
                        icon = "🚫",
                        text = stringResource(R.string.report_v7_no_data_at_all),
                        color = StatusFail
                    )
                }
                AiV7Presentation.OverallStatusKind.PARTIAL -> {
                    Spacer(Modifier.height(10.dp))
                    // Order 2026-09-24 (جولة جرد القيم الافتراضية، البند هـ):
                    // `tag.label` removed from this fallback chain. `label`
                    // is a short tag ("تقييم جزئي") and `text` is the full
                    // sentence WITH the coverage number baked in ("تقييم
                    // جزئي — تغطية 45%") — see AiPartialAssessmentTagV7's
                    // class doc. Falling back to `label` when `text` is
                    // absent silently displayed the short tag as if it were
                    // the detailed reason, with no written justification
                    // for treating one field as a stand-in for the other.
                    // No such reason exists, so this declares the absence
                    // instead, via the string already reserved for exactly
                    // this case (report_v7_partial_no_detail — "تقييم جزئي
                    // — لا تفاصيل إضافية متوفرة").
                    val tag = overall.partialAssessmentTag
                    val tagText = tag?.text?.takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.report_v7_partial_no_detail)
                    AiV7StatusLine(icon = "⚠️", text = tagText, color = StatusWarning)
                }
                AiV7Presentation.OverallStatusKind.NOT_DECLARED -> {
                    // Order 2026-09-24 (جولة جرد القيم الافتراضية، البند ح):
                    // at least one of harnessError/noDataAtAll/partialAssessment
                    // was never declared in the raw report (an older
                    // Room-cached result, or a malformed/partial doc) —
                    // the backend confirmed all three are always emitted on
                    // a live report, so this app cannot read that absence
                    // as a genuine "false" and conclude NORMAL. Announced
                    // via the same generic string used for every other
                    // "signal never computed" state in this file.
                    // Order 2026-10-01 (A5): named subject, was bare "غير متوفر".
                    Spacer(Modifier.height(10.dp))
                    AiV7StatusLine(
                        icon = "❔",
                        text = stringResource(R.string.report_v7_overall_status_unavailable),
                        color = TextMuted
                    )
                }
                AiV7Presentation.OverallStatusKind.NORMAL -> {
                    // No abstention state — nothing to show beyond the
                    // letter + coverage already rendered above.
                }
            }
            overall.zeroMeasuredMessage?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(8.dp))
                AiV7StatusLine(icon = "ℹ️", text = it, color = SkyCyan)
            }
            // Order 2026-10-01 (أمر «abstentionReasons»): was `"• $r"` —
            // the RAW backend reason code shown verbatim (rule 22's "الرمز
            // الخام لا يُعرض على الشاشة", same class of bug as A8's
            // harnessErrorReason fix earlier this round). Now: category
            // name as a bold title, the approved text for that reason
            // underneath — "كما في صفوف الفئات" (نصّ المستخدم) — same
            // layout convention as AiCategoryRowV7's own displayName Text.
            // The decision (which name, which approved text, skip or not)
            // lives in [abstentionReasonDisplayOrNull] — a pure `internal`
            // function this Composable calls, so it is unit-tested directly
            // (AbstentionReasonDisplayTest) instead of being buried in an
            // inline `when` here that only a rendered test could reach (none
            // exists; Robolectric is a test dependency since 3284cca).
            overall.abstentionReasons?.takeIf { it.isNotEmpty() }?.let { reasons ->
                val languageCode = if (isTurkish) "tr" else "ar"
                Spacer(Modifier.height(8.dp))
                Column {
                    reasons.forEach { entry ->
                        val display = abstentionReasonDisplayOrNull(entry, languageCode)
                        if (display == null) {
                            LaunchedEffect(entry.category, entry.reason) {
                                Log.w(
                                    "NeuralCheck.Report",
                                    "abstentionReasons: entry with blank category skipped " +
                                        "(reason='${entry.reason}') — no category to title it by"
                                )
                            }
                            return@forEach
                        }
                        if (display.reasonWasUnrecognised) {
                            LaunchedEffect(entry.category, entry.reason) {
                                Log.w(
                                    "NeuralCheck.Report",
                                    "abstentionReasons: unrecognised reason='${entry.reason}' " +
                                        "for category='${entry.category}' — showing #15 fallback " +
                                        "text, raw reason not displayed"
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = display.categoryName,
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(display.textRes),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
            }
        }
    }
}

/**
 * Order 2026-10-01 (أمر «abstentionReasons»): what one
 * `overall.abstentionReasons[]` entry shows — category name as a bold title
 * ([categoryName], from [CategoryTranslations] alone) and the approved text
 * underneath ([textRes], a string resource id, never the raw backend
 * `reason`). [reasonWasUnrecognised] is true when the reason code mapped to
 * the "anything else" fallback and was non-blank — the caller logs the raw
 * code (never displays it).
 */
internal data class AbstentionReasonDisplay(
    val categoryName: String,
    @androidx.annotation.StringRes val textRes: Int,
    val reasonWasUnrecognised: Boolean
)

/**
 * Pure decision for one abstentionReasons entry — see
 * [AbstentionReasonDisplay]. Returns `null` for an entry with a blank
 * category (nothing to title it by): the caller skips it AND logs — no
 * approved text exists for that case, and one is not invented here.
 *
 * The code is matched DIRECTLY by [AiV7Presentation.classifyAbstentionReason]
 * against the shared reason constants (rule 24) — it does NOT go through the
 * per-category-row classifier [AiV7Presentation.classifyCategoryReason]
 * (order 2026-10-02: the user's table maps the code itself, no `state`, so
 * no fake state is borrowed). The kind→text mapping below is the user's
 * approved table, row for row:
 *   - ATTESTED_ONLY (`MANUAL_ATTESTATION_ONLY`) → `report_v7_state_attested_only` (#14)
 *   - NO_HEALTH_BEARING_TESTS_DECLARED → `report_v7_reason_no_health_bearing_declared`
 *   - DECLARED_BUT_NONE_EXECUTED → `report_v7_reason_declared_none_executed` (#15)
 *   - BATTERY_UNCALIBRATED (`NO_CALIBRATED_THRESHOLD (…)`) →
 *       `report_v7_reason_battery_uncalibrated` (#16) — the battery WAS
 *       measured, it just gets no letter; "لم تُقَس" would be false here
 *   - HARNESS_ERROR_UNMEASURED (`HARNESS_ERROR`) → `report_v7_reason_harness_error_unmeasured`
 *   - BATTERY_UNMEASURED (`NO_BATTERY_INDICATORS_MEASURED`) → `report_v7_reason_battery_unmeasured`
 *   - UNKNOWN → `report_v7_reason_declared_none_executed` (#15) — "any
 *       other code → #15" (the user's decision for THIS list), NOT
 *       `report_v7_state_unknown`, where the per-category ROW's own
 *       `when (reasonKind)` sends ITS unrecognised reason. The two lists
 *       differ only in this final step, each kept next to its own list; no
 *       classifier logic is copied between them.
 */
internal fun abstentionReasonDisplayOrNull(
    entry: net.neuralcheck.data.remote.model.AiAbstentionReasonV7,
    languageCode: String
): AbstentionReasonDisplay? {
    val category = entry.category?.takeIf { it.isNotBlank() } ?: return null
    val kind = AiV7Presentation.classifyAbstentionReason(entry.reason)
    val textRes = when (kind) {
        AiV7Presentation.AbstentionReasonKind.ATTESTED_ONLY -> R.string.report_v7_state_attested_only
        AiV7Presentation.AbstentionReasonKind.NO_HEALTH_BEARING_TESTS_DECLARED ->
            R.string.report_v7_reason_no_health_bearing_declared
        AiV7Presentation.AbstentionReasonKind.DECLARED_BUT_NONE_EXECUTED ->
            R.string.report_v7_reason_declared_none_executed
        AiV7Presentation.AbstentionReasonKind.BATTERY_UNCALIBRATED ->
            R.string.report_v7_reason_battery_uncalibrated
        AiV7Presentation.AbstentionReasonKind.HARNESS_ERROR_UNMEASURED ->
            R.string.report_v7_reason_harness_error_unmeasured
        AiV7Presentation.AbstentionReasonKind.BATTERY_UNMEASURED ->
            R.string.report_v7_reason_battery_unmeasured
        AiV7Presentation.AbstentionReasonKind.UNKNOWN ->
            R.string.report_v7_reason_declared_none_executed
    }
    return AbstentionReasonDisplay(
        categoryName = CategoryTranslations.localizedName(category, languageCode),
        textRes = textRes,
        reasonWasUnrecognised = kind == AiV7Presentation.AbstentionReasonKind.UNKNOWN &&
            !entry.reason.isNullOrBlank()
    )
}

@Composable
private fun AiV7StatusLine(icon: String, text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(icon, fontSize = 14.sp)
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = color, fontWeight = FontWeight.Medium)
    }
}

/**
 * Whether the overall gate card is shown, and with what — see
 * [gateReasonCardDecision]. Order 2026-10-07.
 */
internal sealed class GateReasonCardDecision {
    /** Show the card: the server's own sentence, verbatim — the ONLY body. */
    data class Show(val sentence: String) : GateReasonCardDecision()

    /** `governingGateReason` is null: no gate fired (e.g. Honor), nothing
     *  to explain — no card, nothing logged. */
    object HideNoGate : GateReasonCardDecision()

    /** An object arrived but with no usable `text` (a report from before
     *  the server sent it, an old Room-cached one, or `UNMATCHED`): the
     *  card is hidden ENTIRELY and the caller logs the absence. */
    object HideTextAbsent : GateReasonCardDecision()
}

/**
 * Pure decision for `overall.governingGateReason`.
 *
 * Decision S1 (user, via the coordinator, 2026-10-07): "لكل بوّابة جملة
 * عربية للعرض … ولا يُعرض الاسم … ولا المصدر … ويبقى الاسم والمصدر في
 * البيانات للّوحة"; the same day, "item 4": "البطاقة حين يغيب
 * governingGateReason.text … تُخفى كلّها في التطبيق والموقع، ولا يُعرض
 * عنوانها وحده. ولا نصّ جديد. ويبقى الحرف العامّ ووسم التقييم الجزئي."; and
 * the letter badge "السقف: C" was deleted because the server's sentence
 * already says «أعلى حرف C». So:
 *   - `text` present → [GateReasonCardDecision.Show], the sentence
 *     VERBATIM and nothing else: no name, no source, no letter badge.
 *   - object with `text` absent/blank → [GateReasonCardDecision.HideTextAbsent].
 *   - null → [GateReasonCardDecision.HideNoGate].
 * `name`, `source` and `ceilingLetter` are never inputs to the displayed
 * string; they only reach the caller's `Log.w`.
 */
internal fun gateReasonCardDecision(
    reason: net.neuralcheck.data.remote.model.AiGoverningGateReasonV7?
): GateReasonCardDecision {
    if (reason == null) return GateReasonCardDecision.HideNoGate
    val sentence = AiV7Presentation.governingGateSentenceOrNull(reason.text)
        ?: return GateReasonCardDecision.HideTextAbsent
    return GateReasonCardDecision.Show(sentence)
}

/**
 * `overall.governingGateReason` — explains WHY the final letter was capped
 * (e.g. ROOT_DETECTED forcing a ceiling of "C" even though most measured
 * categories graded A). Order 2026-10-07: shows the server's [sentence]
 * ONLY (no name, no source, no separate letter badge) and is not composed
 * at all unless [gateReasonCardDecision] says Show. The separate 🚧 icon
 * is unchanged; the title is `report_v7_gate_reason_title` (now the
 * approved «سبب الحرف العامّ» / «Genel harf notunun nedeni», no emoji of
 * its own — it used to start with 🚧 too, so the emoji showed twice).
 */
@Composable
private fun AiV7GateReasonCard(sentence: String) {
    NCCard(modifier = Modifier.fillMaxWidth(), contentPadding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🚧", fontSize = 18.sp)
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.report_v7_gate_reason_title),
                style = MaterialTheme.typography.titleMedium,
                color = SkyCyan,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = sentence,
            style = MaterialTheme.typography.bodyMedium,
            color = TextPrimary
        )
    }
}

/**
 * Per-category breakdown for V7. Distinct shape from V6's
 * [AiCategoryEvaluationSection] — `state`/`letter`/`letterSource`/
 * `adjustmentReason`/`adjustmentEvidence`/`coverage`/`reason` instead of
 * `grade`/`score`/`passCount`/`totalCount`/`note` — but keeps the same
 * D-first sort and the same 15 category keys (reuses [CategoryTranslations]
 * unchanged). Order 2026-10-01 (A5): no longer reuses [SURFACE_CATEGORY_KEYS]
 * for the surface tag — see [AiCategoryRowV7]'s own call site.
 */
@Composable
private fun AiCategoryEvaluationSectionV7(
    categories: Map<String, net.neuralcheck.data.remote.model.AiCategoryV7>,
    isTurkish: Boolean,
    /** Category keys whose raw report JSON never carried the
     *  `letterCeilingReason` key at all — see
     *  [net.neuralcheck.data.remote.model.AiAnalysisResponse.categoriesMissingLetterCeilingReason]'s
     *  doc. Empty set (not null) so every call site must decide the
     *  "nothing known missing" default explicitly, per rule 22. */
    missingLetterCeilingReason: Set<String>
) {
    val priority = mapOf("D" to 4, "C" to 3, "B" to 2, "A" to 1)
    val sorted = categories.entries.sortedByDescending { (_, cat) ->
        // Order 2026-09-24 (جولة جرد القيم الافتراضية، البند و): a category
        // with no letter at all (unmeasured, harness error, etc.) used to
        // fall to priority 0 — sorted BELOW even grade A, i.e. displayed
        // last, as if "no letter" were the least concerning outcome. That
        // silently reads an absent measurement as good news (rule 22).
        // Ranked above D (the worst NAMED grade) instead, so an unmeasured
        // category surfaces at the very top rather than being buried at the
        // bottom where a reader skimming top-to-bottom might never reach it.
        priority[cat.letter?.trim()?.uppercase()] ?: 5
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.report_section_category_eval),
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        sorted.forEach { (key, cat) ->
            AiCategoryRowV7(
                key = key,
                cat = cat,
                isTurkish = isTurkish,
                letterCeilingReasonDeclared = key !in missingLetterCeilingReason
            )
        }
    }
}

@Composable
private fun AiCategoryRowV7(
    key: String,
    cat: net.neuralcheck.data.remote.model.AiCategoryV7,
    isTurkish: Boolean,
    letterCeilingReasonDeclared: Boolean
) {
    val label = CategoryTranslations.labelFor(key)
    val displayName = if (isTurkish) label.tr else label.ar
    val stateKind = AiV7Presentation.classifyState(cat.state)
    val known = AiVerdictPresentation.isKnownGrade(cat.letter)
    val gradeColor = when {
        known -> when (cat.letter!!.trim().uppercase()) {
            "A" -> GradeA; "B" -> GradeB; "C" -> GradeC; "D" -> GradeD; else -> TextMuted
        }
        else -> TextMuted
    }
    // coveragePct only exists on the overall block, not per category — this
    // row always falls back to the fraction form (AiV7Presentation.coveragePercent
    // still returns null, the explicit case, when even that is absent).
    val coveragePct = AiV7Presentation.coveragePercent(coveragePct = null, coverage = cat.coverage)
    val letterSourceKind = AiV7Presentation.classifyLetterSource(cat.letterSource)

    NCCard(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp), contentPadding = 14.dp) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label.icon, fontSize = 22.sp)
            Spacer(Modifier.width(10.dp))
            Text(
                text = displayName,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            if (coveragePct != null) {
                Text(
                    // Order 2026-10-01 (A3): was a bare "%1$d%%" with no
                    // label at all — e.g. battery showed "100%" beside
                    // "الحرف غير متوفر" with nothing saying what the number
                    // was. Now labelled "التغطية %1$d%%".
                    text = NumberFormat.toLatin(
                        stringResource(R.string.report_v7_category_coverage_label_fmt, coveragePct)
                    ),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextMuted
                )
                Spacer(Modifier.width(10.dp))
            }
            Surface(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                color = gradeColor.copy(alpha = 0.18f),
                border = androidx.compose.foundation.BorderStroke(1.dp, gradeColor.copy(alpha = 0.5f))
            ) {
                Text(
                    text = when {
                        known -> cat.letter!!.trim().uppercase()
                        stateKind == AiV7Presentation.CategoryStateKind.UNMEASURED -> stringResource(R.string.report_category_unmeasured)
                        else -> stringResource(R.string.report_grade_unavailable)
                    },
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = if (known) 14.sp else 12.sp,
                    color = gradeColor
                )
            }
        }
        // categories.<key>.letterCeilingReason.text, verbatim, right beside
        // the letter Surface above ("سبب البوّابة يصل العرض بنصّه" —
        // 2026-09-22 order). Never built, translated, or derived from
        // `.name` in this app — see AiV7Presentation.letterCeilingReasonDisplayText's
        // doc.
        //
        // Order 2026-09-23 (القرار ٧٢) item 3: NOT_DECLARED (the whole
        // categoriesMissingLetterCeilingReason signal was unavailable for
        // this report — an older Room-cached result predating that field,
        // see AiAnalysisSectionV7's `cats.keys` fallback above) must be
        // announced on screen, never silently read as the real NO_CEILING
        // answer. Reuses the existing generic report_v7_field_unavailable
        // string — no new wording invented. NO_CEILING and PRESENT_NO_TEXT
        // stay silent, unchanged: both remain genuine "nothing to report"
        // answers, not an unknown one — see that function's doc.
        if (AiV7Presentation.classifyLetterCeilingReason(letterCeilingReasonDeclared, cat.letterCeilingReason) ==
            AiV7Presentation.LetterCeilingReasonKind.NOT_DECLARED
        ) {
            // Order 2026-10-01 (A5): named subject, was bare "غير متوفر".
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.report_v7_ceiling_reason_unavailable),
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
                lineHeight = 19.sp
            )
        }
        AiV7Presentation.letterCeilingReasonDisplayText(
            declared = letterCeilingReasonDeclared,
            reason = cat.letterCeilingReason
        )?.let { ceilingText ->
            Spacer(Modifier.height(6.dp))
            Text(ceilingText, style = MaterialTheme.typography.bodySmall, color = StatusWarning, lineHeight = 19.sp)
        }
        // Order 2026-09-25 (القرار ٨٤ البند ١ / القرار ٨٦ البند ٣): the
        // (state, reason) pair now drives ONE line, replacing both the old
        // generic state text AND the raw cat.reason text that used to show
        // verbatim below it (a server-internal code like "NO_CALIBRATED_THRESHOLD
        // (PLAN-03 لم يُنجَز — انظر batteryLayer.js)" reaching the screen —
        // rule 22's own concern, "الرمز الخام لا يُعرض على الشاشة"). A
        // reason-specific text (when the pair is in the table) REPLACES the
        // generic state text, shown once, never duplicated in a separate
        // reason line. HIDDEN pairs ((HARNESS_ERROR,HARNESS_ERROR) and
        // (ATTESTED_ONLY,MANUAL_ATTESTATION_ONLY), plus a blank/absent
        // reason) fall back to the plain per-state text — the reason would
        // be redundant with what the state text already says. A pair
        // outside the table (rule: order 2026-09-25 القرار ٨٧ البند ٥) shows
        // the existing report_v7_state_unknown text — NOT a new "حالة غير
        // معروفة" string, and NOT the raw code — with the raw code logged,
        // never displayed.
        val reasonKind = AiV7Presentation.classifyCategoryReason(cat.state, cat.reason)
        if (reasonKind == AiV7Presentation.CategoryReasonKind.UNKNOWN) {
            LaunchedEffect(key, cat.state, cat.reason) {
                Log.w(
                    "NeuralCheck.Report",
                    "category '$key': unrecognised (state='${cat.state}', reason='${cat.reason}') " +
                        "pair — showing report_v7_state_unknown, raw reason not displayed"
                )
            }
        }
        val primaryLine: String? = when (reasonKind) {
            AiV7Presentation.CategoryReasonKind.NO_HEALTH_BEARING_TESTS_DECLARED ->
                stringResource(R.string.report_v7_reason_no_health_bearing_declared)
            AiV7Presentation.CategoryReasonKind.DECLARED_BUT_NONE_EXECUTED ->
                stringResource(R.string.report_v7_reason_declared_none_executed)
            AiV7Presentation.CategoryReasonKind.BATTERY_UNCALIBRATED ->
                stringResource(R.string.report_v7_reason_battery_uncalibrated)
            AiV7Presentation.CategoryReasonKind.BATTERY_UNMEASURED ->
                stringResource(R.string.report_v7_reason_battery_unmeasured)
            AiV7Presentation.CategoryReasonKind.HARNESS_ERROR_UNMEASURED ->
                stringResource(R.string.report_v7_reason_harness_error_unmeasured)
            AiV7Presentation.CategoryReasonKind.UNKNOWN ->
                stringResource(R.string.report_v7_state_unknown)
            AiV7Presentation.CategoryReasonKind.HIDDEN -> when (stateKind) {
                AiV7Presentation.CategoryStateKind.PARTIAL -> stringResource(R.string.report_v7_state_partial)
                AiV7Presentation.CategoryStateKind.UNMEASURED -> stringResource(R.string.report_category_unmeasured)
                AiV7Presentation.CategoryStateKind.UNKNOWN -> stringResource(R.string.report_v7_state_unknown)
                AiV7Presentation.CategoryStateKind.ATTESTED_ONLY ->
                    stringResource(R.string.report_v7_state_attested_only)
                AiV7Presentation.CategoryStateKind.HARNESS_ERROR ->
                    stringResource(R.string.report_v7_state_harness_error)
                AiV7Presentation.CategoryStateKind.NO_DATA ->
                    stringResource(R.string.report_v7_state_uninterpreted_fmt, cat.state ?: "?")
                AiV7Presentation.CategoryStateKind.MEASURED -> null
            }
        }
        primaryLine?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, style = MaterialTheme.typography.labelSmall, color = StatusWarning, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.height(8.dp))
        // Order 2026-09-25 (القرار ٨٧ البند ٤): "لا يظهر إلا لفئة لها حرف" —
        // this row claims "حرف محسوب مباشرة من نتائج القياس" (or its
        // LLM_ADJUSTED/UNKNOWN siblings), which is meaningless — and used
        // to render right beside "الحرف غير متوفر" — for a category with
        // no letter at all.
        if (known) {
            // Order 2026-09-25 (القرار ٩٩ البند ٤): "مسار الدليل الخامّ
            // (evidenceRefs) لا يُعرض في شاشة التقرير". adjustmentEvidence
            // (List<String>? — see AiAnalysisResponse.kt) is still READ off
            // the model (the field itself is unchanged, per the order), it
            // is simply never passed to AiV7LetterSourceRow any more — a
            // raw internal reference like "categoryStates.testResults[61].outcome"
            // is not something a buyer can act on. A people-readable form
            // (e.g. "فشل الاختبار C-06" by test name) is explicitly deferred
            // to after the field test, pending the field-swap fix.
            AiV7LetterSourceRow(kind = letterSourceKind, reason = cat.adjustmentReason)
        }
        // Order 2026-10-01 (A5): was a hand-written key-name whitelist
        // (SURFACE_CATEGORY_KEYS — "display","camera","audio","input",
        // "wireless") shared with the unrelated V6 card below. That list
        // disagreed with this V7 payload's own `state` field: the golden
        // sample (v7-client-payload-sample.json) has display and wireless
        // both state=MEASURED with real letters, i.e. NeuralCheck-measured,
        // not inspector-attested — showing "إفادة الفاحص — افحصها بنفسك" on
        // them was wrong. ATTESTED_ONLY (`categories.<key>.state`, read via
        // `stateKind` above — AiV7Presentation.classifyState,
        // AiV7Presentation.kt:79-87) is this payload's actual "measured by
        // the technician's tap, not automatically" signal, so the tag now
        // follows that field instead of the category's name. No golden
        // sample currently has an ATTESTED_ONLY category to prove this
        // renders — see the report's own "ما لم أفعله" section.
        if (stateKind == AiV7Presentation.CategoryStateKind.ATTESTED_ONLY) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "🔎 " + stringResource(R.string.report_surface_category_tag),
                style = MaterialTheme.typography.labelSmall,
                color = SkyCyan,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * The distinction the Phase 3-b order calls out by name: "letterSource
 * للفئة: DETERMINISTIC | LLM_ADJUSTED. ~~ومعه السبب ومرجع الدليل~~ — فالمستخدم
 * يعرف لماذا نزلت الفئة، ومن أنزلها." Shown on every category row (not just
 * the LLM_ADJUSTED ones) so the field is always visible per the mandatory
 * rule, not just surfaced when it happens to be the interesting case.
 * UNKNOWN (null or an unrecognized string) is flagged in the warning color,
 * not blended into the routine DETERMINISTIC styling — an unreadable
 * source is itself a finding, not a "nothing to report" state.
 *
 * Order 2026-09-25 (القرار ٩٩ البند ٤) — "مرجع الدليل" (the raw
 * `adjustmentEvidence` reference, struck through in the quote above, kept
 * not deleted per rule 8) no longer renders: a raw internal path like
 * `categoryStates.testResults[61].outcome` is not something a buyer can
 * act on. The reason still does. A people-readable evidence form is
 * explicitly deferred, pending a separate field-swap fix.
 */
@Composable
private fun AiV7LetterSourceRow(
    kind: AiV7Presentation.LetterSourceKind,
    reason: String?
) {
    val icon: String
    val label: String
    val color: Color
    when (kind) {
        AiV7Presentation.LetterSourceKind.DETERMINISTIC -> {
            icon = "⚙️"; label = stringResource(R.string.report_v7_letter_source_deterministic); color = TextMuted
        }
        AiV7Presentation.LetterSourceKind.LLM_ADJUSTED -> {
            icon = "🤖"; label = stringResource(R.string.report_v7_letter_source_llm_adjusted); color = SkyCyan
        }
        AiV7Presentation.LetterSourceKind.UNKNOWN -> {
            icon = "❔"; label = stringResource(R.string.report_v7_letter_source_unknown); color = StatusWarning
        }
    }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(icon, fontSize = 12.sp)
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Medium)
        }
        if (kind == AiV7Presentation.LetterSourceKind.LLM_ADJUSTED) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = reason?.takeIf { it.isNotBlank() } ?: stringResource(R.string.report_v7_adjustment_reason_unavailable),
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                modifier = Modifier.padding(start = 18.dp)
            )
            // Order 2026-09-25 (القرار ٩٩ البند ٤): the raw evidence
            // reference (e.g. "categoryStates.testResults[61].outcome")
            // used to render here, verbatim — removed. The reason above is
            // unaffected; its own "unavailable" fallback is untouched.
        }
    }
}

/**
 * fieldsVersion 69 (S-26, contract section 7-h (d)): shown INSTEAD of the error card when the server says it is
 * still processing this scan's analysis. Retry re-runs `loadScan`, which reads `reports/{scanId}` first — so
 * once the first request has finished on the server its report is picked up, no second analysis is paid for.
 */
@Composable
private fun AiAnalysisWaitCard(onRetry: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = NeuralGreen.copy(alpha = 0.10f)),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, NeuralGreen.copy(alpha = 0.35f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Refresh, null, tint = NeuralGreen)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.report_ai_wait_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.report_ai_wait_body),
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onRetry) {
                Icon(Icons.Default.Refresh, null, tint = NeuralGreen)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.btn_retry), color = NeuralGreen)
            }
        }
    }
}

@Composable
private fun AiAnalysisErrorCard(error: String, onRetry: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = StatusWarning.copy(alpha = 0.10f)),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, StatusWarning.copy(alpha = 0.35f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, null, tint = StatusWarning)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.report_ai_error_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onRetry) {
                Icon(Icons.Default.Refresh, null, tint = NeuralGreen)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.btn_retry), color = NeuralGreen)
            }
        }
    }
}

private suspend fun shareReport(
    context: android.content.Context,
    gen: ReportPdfGenerator,
    qr: Bitmap,
    s: ScoringResult,
    deviceModel: String,
    scanTimestamp: Long,
    scanId: String,
    displayId: String,
    results: List<net.neuralcheck.data.local.db.entity.FunctionalResultEntity>
): Boolean = withContext(Dispatchers.IO) {
    try {
        val file = gen.generateReport(
            scoring = s,
            functionalResults = results,
            deviceModel = deviceModel,
            scanTimestamp = scanTimestamp,
            scanId = scanId,
            displayId = displayId,
            qrBitmap = qr,
            aiAnalysis = s.aiAnalysis
        )
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.report_pdf_share_subject))
            // Order 2026-09-24 (القرار ٧٦، البند ١): overallScore is now
            // nullable. Currently unreachable (the Share button that calls
            // this is hidden — ReportPdfExportButtons.HIDDEN) but must
            // still compile and not fabricate a number if that changes.
            val scoreText = s.overallScore?.let { NumberFormat.format(it, 0) } ?: "—"
            putExtra(Intent.EXTRA_TEXT, NumberFormat.toLatin(context.getString(R.string.report_share_subject_fmt, s.overallGrade, scoreText)))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, context.getString(R.string.report_share_chooser)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
        true
    } catch (e: Exception) {
        android.util.Log.w("ReportScreen", "Share failed: ${e.message}", e)
        false
    }
}

private suspend fun saveReport(
    context: android.content.Context,
    gen: ReportPdfGenerator,
    qr: Bitmap,
    s: ScoringResult,
    deviceModel: String,
    scanTimestamp: Long,
    scanId: String,
    displayId: String,
    results: List<net.neuralcheck.data.local.db.entity.FunctionalResultEntity>
): String? = withContext(Dispatchers.IO) {
    try {
        val cacheFile = gen.generateReport(
            scoring = s,
            functionalResults = results,
            deviceModel = deviceModel,
            scanTimestamp = scanTimestamp,
            scanId = scanId,
            displayId = displayId,
            qrBitmap = qr,
            aiAnalysis = s.aiAnalysis
        )
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "NeuralCheck_${timestamp}.pdf"

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(android.provider.MediaStore.Downloads.MIME_TYPE, "application/pdf")
                put(android.provider.MediaStore.Downloads.RELATIVE_PATH, "Download/NeuralCheck")
            }
            val uri = context.contentResolver.insert(
                android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values
            ) ?: return@withContext null
            context.contentResolver.openOutputStream(uri)?.use { out ->
                cacheFile.inputStream().use { input -> input.copyTo(out) }
            }
            "Download/NeuralCheck/$fileName"
        } else {
            val dir = File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS), "NeuralCheck").apply { mkdirs() }
            val outFile = File(dir, fileName)
            FileOutputStream(outFile).use { out ->
                cacheFile.inputStream().use { input -> input.copyTo(out) }
            }
            outFile.absolutePath
        }
    } catch (e: Exception) {
        android.util.Log.w("ReportScreen", "Save failed: ${e.message}", e)
        null
    }
}
