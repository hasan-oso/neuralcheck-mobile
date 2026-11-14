package net.neuralcheck.presentation.screen

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.navigation.NavController
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import net.neuralcheck.R
import net.neuralcheck.presentation.navigation.afterJoinRoute
import net.neuralcheck.presentation.viewmodel.QRScannerViewModel
import net.neuralcheck.util.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

// ---------------------------------------------------------------------------
// ZXing ImageAnalysis.Analyzer
// ---------------------------------------------------------------------------

private class QrCodeAnalyzer(
    private val onDecoded: (String) -> Unit
) : ImageAnalysis.Analyzer {

    private val reader = MultiFormatReader().apply {
        setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)))
    }

    // ImageProxy.getImage() is annotated with @ExperimentalGetImage so the lint
    // marker must live on the method that actually calls it, not at file scope.
    @ExperimentalGetImage
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            try {
                val yPlane = mediaImage.planes[0]
                val buffer = yPlane.buffer.apply { rewind() }
                val rowStride = yPlane.rowStride
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)
                val source = PlanarYUVLuminanceSource(
                    bytes, rowStride, mediaImage.height,
                    0, 0, mediaImage.width, mediaImage.height, false
                )
                val result = reader.decodeWithState(BinaryBitmap(HybridBinarizer(source)))
                onDecoded(result.text)
            } catch (_: NotFoundException) {
                // no QR code in this frame — expected
            } catch (_: Exception) {
                // other decode errors, ignore
            } finally {
                reader.reset()
            }
        }
        imageProxy.close()
    }
}

// ---------------------------------------------------------------------------
// CameraX Preview + ImageAnalysis composable
// ---------------------------------------------------------------------------

@Composable
private fun CameraPreviewWithAnalysis(
    modifier: Modifier = Modifier,
    onQrDetected: (String) -> Unit
) {
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val analyzer = remember { QrCodeAnalyzer(onQrDetected) }

    DisposableEffect(Unit) {
        onDispose { analysisExecutor.shutdown() }
    }

    AndroidView(
        factory = { ctx ->
            // Activity context is a LifecycleOwner — used to bind CameraX
            val lifecycleOwner = ctx as LifecycleOwner
            PreviewView(ctx).apply {
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                val future = ProcessCameraProvider.getInstance(ctx)
                future.addListener({
                    val cameraProvider = future.get()
                    val preview = Preview.Builder().build()
                        .also { it.setSurfaceProvider(surfaceProvider) }
                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also { it.setAnalyzer(analysisExecutor, analyzer) }
                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            analysis
                        )
                    } catch (_: Exception) { /* camera unavailable */ }
                }, ContextCompat.getMainExecutor(ctx))
            }
        },
        modifier = modifier
    )
}

// ---------------------------------------------------------------------------
// Viewfinder overlay: dark mask + clear QR window + corner brackets
// ---------------------------------------------------------------------------

@Composable
private fun ViewfinderOverlay(isSuccess: Boolean, isError: Boolean) {
    // Pull from the theme tokens (StatusPass / StatusFail) so the viewfinder
    // success/error frame stays consistent with every other pass/fail surface
    // in the app — chips, grade pills, error cards. Earlier hex values
    // (0xFF4CAF50 / 0xFFF44336) were Material defaults that didn't match the
    // dark theme's StatusPass=#22C55E and StatusFail=#EF4444.
    val frameColor by animateColorAsState(
        targetValue = when {
            isSuccess -> net.neuralcheck.presentation.theme.StatusPass
            isError   -> net.neuralcheck.presentation.theme.StatusFail
            else      -> Color.White
        },
        label = "frame_color"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                val boxSize  = minOf(size.width, size.height) * 0.65f
                val left     = (size.width  - boxSize) / 2f
                val top      = (size.height - boxSize) / 2f
                val right    = left + boxSize
                val bottom   = top  + boxSize
                val cLen     = 48.dp.toPx()
                val cStroke  = 3.dp.toPx()

                // Dark overlay over entire screen
                drawRect(Color.Black.copy(alpha = 0.62f))

                // Punch a transparent hole for the QR window
                drawRoundRect(
                    color = Color.Transparent,
                    topLeft = Offset(left, top),
                    size = Size(boxSize, boxSize),
                    cornerRadius = CornerRadius(8.dp.toPx()),
                    blendMode = BlendMode.Clear
                )

                // Thin frame around hole
                drawRoundRect(
                    color = frameColor.copy(alpha = 0.5f),
                    topLeft = Offset(left, top),
                    size = Size(boxSize, boxSize),
                    cornerRadius = CornerRadius(8.dp.toPx()),
                    style = Stroke(1.dp.toPx())
                )

                // Corner brackets — top-left
                drawLine(frameColor, Offset(left, top), Offset(left + cLen, top), cStroke, StrokeCap.Round)
                drawLine(frameColor, Offset(left, top), Offset(left, top + cLen), cStroke, StrokeCap.Round)
                // top-right
                drawLine(frameColor, Offset(right - cLen, top), Offset(right, top), cStroke, StrokeCap.Round)
                drawLine(frameColor, Offset(right, top), Offset(right, top + cLen), cStroke, StrokeCap.Round)
                // bottom-left
                drawLine(frameColor, Offset(left, bottom - cLen), Offset(left, bottom), cStroke, StrokeCap.Round)
                drawLine(frameColor, Offset(left, bottom), Offset(left + cLen, bottom), cStroke, StrokeCap.Round)
                // bottom-right
                drawLine(frameColor, Offset(right - cLen, bottom), Offset(right, bottom), cStroke, StrokeCap.Round)
                drawLine(frameColor, Offset(right, bottom - cLen), Offset(right, bottom), cStroke, StrokeCap.Round)
            }
    )
}

// ---------------------------------------------------------------------------
// Main QRScannerScreen
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QRScannerScreen(navController: NavController) {
    val viewModel: QRScannerViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()
    val preRepairScans by viewModel.preRepairScans.collectAsState()
    val context = LocalContext.current

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED
        )
    }
    var showBottomSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasPermission) permLauncher.launch(Manifest.permission.CAMERA)
    }

    // Auto-navigate based on the resolved scan-state. Two terminal paths:
    //   - Found: a previous scan UUID — used by the post-repair link flow.
    //   - JoinSuccess: an owner-issued session code that we successfully
    //     claimed via joinScanSession. Routes into the customer-device
    //     general-scan flow; the ScanSessionHolder the VM populated is
    //     what AiScoringEngine reads at analyzeDevice time.
    LaunchedEffect(state) {
        when (val s = state) {
            is QRScannerViewModel.QrState.Found -> {
                navController.navigate("pre_scan/post_repair/${s.scan.scanId}") {
                    popUpTo("qr_scanner") { inclusive = true }
                }
            }
            is QRScannerViewModel.QrState.JoinSuccess -> {
                // A-P6-12 / ق15: through the consent screen when it is live AND the join's answer says the shop has the consent part (afterJoinRoute), else straight to the pre-scan screen as before.
                navController.navigate(afterJoinRoute(CONSENT_TEXTS_APPROVED, s.consentPart)) {
                    popUpTo("qr_scanner") { inclusive = true }
                }
            }
            else -> Unit
        }
    }

    Scaffold(
        containerColor = net.neuralcheck.presentation.theme.DeepSpace,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.title_qr_scanner),
                        color = net.neuralcheck.presentation.theme.TextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            tint = net.neuralcheck.presentation.theme.TextSecondary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = net.neuralcheck.presentation.theme.DeepSpace,
                    titleContentColor = net.neuralcheck.presentation.theme.TextPrimary
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (hasPermission) {
                // --- Camera preview (full area) ---
                CameraPreviewWithAnalysis(
                    modifier = Modifier.fillMaxSize(),
                    onQrDetected = viewModel::onQrDetected
                )

                // --- Dark overlay + QR frame ---
                // Treat both Found (UUID match) and JoinSuccess (session code
                // claimed) as "success" for the frame colour; JoinFailed
                // joins the existing error states alongside Invalid/NotFound.
                ViewfinderOverlay(
                    isSuccess = state is QRScannerViewModel.QrState.Found ||
                                state is QRScannerViewModel.QrState.JoinSuccess,
                    isError   = state is QRScannerViewModel.QrState.Invalid ||
                                state is QRScannerViewModel.QrState.NotFound ||
                                state is QRScannerViewModel.QrState.JoinFailed
                )

                // --- Instruction at top ---
                Text(
                    text = stringResource(R.string.qr_instruction),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 20.dp, start = 32.dp, end = 32.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            } else {
                // --- Permission denied ---
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(net.neuralcheck.presentation.theme.DeepSpace)
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = null,
                        modifier = Modifier.size(72.dp),
                        tint = net.neuralcheck.presentation.theme.NeonCyan
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.qr_permission_denied),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = net.neuralcheck.presentation.theme.TextPrimary
                    )
                    Spacer(Modifier.height(20.dp))
                    Button(
                        onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", context.packageName, null)
                                }
                            )
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = net.neuralcheck.presentation.theme.NeuralGreen,
                            contentColor = net.neuralcheck.presentation.theme.DeepSpace
                        )
                    ) { Text(stringResource(R.string.qr_open_settings)) }
                }
            }

            // --- Status message (above manual button) ---
            AnimatedContent(
                targetState = state,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 96.dp, start = 24.dp, end = 24.dp),
                label = "qr_status"
            ) { currentState ->
                when (currentState) {
                    QRScannerViewModel.QrState.Scanning -> { /* silent — instruction is at top */ }

                    QRScannerViewModel.QrState.Invalid ->
                        StatusChip(
                            text = stringResource(R.string.qr_error_invalid),
                            icon = Icons.Default.Warning,
                            containerColor = net.neuralcheck.presentation.theme.StatusFail.copy(alpha = 0.18f),
                            contentColor = net.neuralcheck.presentation.theme.StatusFail
                        )

                    QRScannerViewModel.QrState.NotFound ->
                        StatusChip(
                            text = stringResource(R.string.qr_scan_not_found),
                            icon = Icons.Default.Warning,
                            containerColor = net.neuralcheck.presentation.theme.StatusWarning.copy(alpha = 0.18f),
                            contentColor = net.neuralcheck.presentation.theme.StatusWarning
                        )

                    QRScannerViewModel.QrState.Joining ->
                        StatusChip(
                            text = stringResource(R.string.qr_joining),
                            icon = Icons.Default.QrCodeScanner,
                            containerColor = net.neuralcheck.presentation.theme.SkyCyan.copy(alpha = 0.18f),
                            contentColor = net.neuralcheck.presentation.theme.SkyCyan
                        )

                    is QRScannerViewModel.QrState.JoinSuccess ->
                        StatusChip(
                            text = stringResource(R.string.qr_join_success),
                            icon = Icons.Default.CheckCircle,
                            containerColor = net.neuralcheck.presentation.theme.StatusPass.copy(alpha = 0.22f),
                            contentColor = net.neuralcheck.presentation.theme.StatusPass
                        )

                    is QRScannerViewModel.QrState.JoinFailed ->
                        StatusChip(
                            text = currentState.message,
                            icon = Icons.Default.Warning,
                            containerColor = net.neuralcheck.presentation.theme.StatusFail.copy(alpha = 0.18f),
                            contentColor = net.neuralcheck.presentation.theme.StatusFail
                        )

                    is QRScannerViewModel.QrState.Found -> {
                        val dateStr = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
                            .format(Date(currentState.scan.timestamp))
                        StatusChip(
                            text = NumberFormat.toLatin(stringResource(R.string.qr_linked_success_fmt, dateStr)),
                            icon = Icons.Default.CheckCircle,
                            containerColor = net.neuralcheck.presentation.theme.StatusPass.copy(alpha = 0.22f),
                            contentColor = net.neuralcheck.presentation.theme.StatusPass
                        )
                    }
                }
            }

            // --- Manual selection button (always visible at bottom) ---
            OutlinedButton(
                onClick = { showBottomSheet = true },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 24.dp, vertical = 24.dp)
                    .fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = net.neuralcheck.presentation.theme.CardDark.copy(alpha = 0.92f),
                    contentColor = net.neuralcheck.presentation.theme.TextPrimary
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    net.neuralcheck.presentation.theme.NeuralGreen.copy(alpha = 0.45f)
                )
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.FormatListBulleted,
                    contentDescription = null,
                    tint = net.neuralcheck.presentation.theme.NeuralGreen
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.qr_manual_select))
            }
        }
    }

    // --- BottomSheet: previous pre-repair scans ---
    if (showBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showBottomSheet = false },
            sheetState = sheetState
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(
                    text = stringResource(R.string.qr_select_hint),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = stringResource(R.string.history_select_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                if (preRepairScans.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.history_empty),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn {
                        items(preRepairScans, key = { it.scanId }) { scan ->
                            val dateStr = NumberFormat.toLatin(SimpleDateFormat("yyyy/MM/dd  HH:mm", Locale.getDefault())
                                .format(Date(scan.timestamp)))
                            ListItem(
                                headlineContent = { Text(net.neuralcheck.util.DeviceName.prettify(scan.deviceModel)) },
                                supportingContent = { Text(dateStr) },
                                leadingContent = {
                                    Icon(
                                        Icons.Default.QrCodeScanner,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                },
                                trailingContent = {
                                    Icon(Icons.Default.ChevronRight, contentDescription = null)
                                },
                                modifier = Modifier.clickable {
                                    showBottomSheet = false
                                    navController.navigate("pre_scan/post_repair/${scan.scanId}") {
                                        popUpTo("qr_scanner") { inclusive = true }
                                    }
                                }
                            )
                            HorizontalDivider()
                        }
                    }
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Helper: status chip
// ---------------------------------------------------------------------------

@Composable
private fun StatusChip(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    containerColor: Color,
    contentColor: Color
) {
    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
