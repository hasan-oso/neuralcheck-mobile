package net.neuralcheck.presentation.screen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import net.neuralcheck.R
import net.neuralcheck.data.remote.CancelReason
import net.neuralcheck.data.remote.DefectType
import net.neuralcheck.data.remote.ScanPurposeChoice
import net.neuralcheck.presentation.components.NCPrimaryButton
import net.neuralcheck.presentation.theme.*
import net.neuralcheck.presentation.viewmodel.GenerateSessionViewModel
import net.neuralcheck.presentation.viewmodel.GenerateSessionViewModel.Phase
import net.neuralcheck.presentation.viewmodel.PurposeUi
import net.neuralcheck.presentation.viewmodel.RepeatNoticeUi
import net.neuralcheck.presentation.viewmodel.pairListVisible
import net.neuralcheck.presentation.viewmodel.purposeChipsLocked
import net.neuralcheck.presentation.viewmodel.ownerCancelledTextFor
import net.neuralcheck.util.LogRedaction
import net.neuralcheck.util.QrCodeHelper

/**
 * Shop-owner screen: creates a scan session, prints a QR + a short
 * "NC-XXXX" code, and reactively mirrors the customer device's progress
 * from "waiting → joined → scanning → completed". On completion the
 * owner gets a one-tap jump to the report.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenerateSessionScreen(
    navController: NavController,
    viewModel: GenerateSessionViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        containerColor = DeepSpace,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.generate_session_title), color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DeepSpace)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (state.phase) {
                Phase.CREATING -> CreatingState()
                Phase.ERROR -> ErrorState(state.error, state.retryable) { viewModel.create() }
                Phase.EXPIRED -> ExpiredState { viewModel.create() }
                Phase.CANCELLED -> CancelledState(state.cancelReason) { viewModel.create() }
                else -> {
                    // WAITING, SCANNING and COMPLETED are only ever set with a code (batch 2, item 4). A state that has none is a named failure with the
                    // existing "could not create the session" text - never an empty code chip.
                    val code = state.code
                    if (code == null) {
                        ErrorState(null, retryable = true) { viewModel.create() }
                    } else {
                        SessionContent(
                            state = state,
                            code = code,
                            navController = navController,
                            onCreateAnother = { viewModel.create() },
                            onCancelConfirmed = { viewModel.cancelSession() },
                            onSelectPurpose = { viewModel.onPurposeChip(it) },
                            onSelectDefectType = { viewModel.onDefectType(it) },
                            onSelectPair = { viewModel.onPairCandidate(it) },
                            onConfirmPair = { viewModel.onPairConfirm() },
                            onReportOpened = { viewModel.onReportOpened() },
                            onRepeatNotNow = { viewModel.dismissRepeatNotice() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CreatingState() {
    Spacer(Modifier.height(40.dp))
    CircularProgressIndicator(color = NeuralGreen)
    Spacer(Modifier.height(16.dp))
    Text(stringResource(R.string.session_creating), color = TextSecondary, fontSize = 14.sp)
}

/**
 * The failed state. `error` is the text the view model chose (it sets one at every place it enters the ERROR phase). The `?:` default is the one text the user
 * named for a session that could not be created (batch 2, item 4): it is there so a failed state is never blank, and it is what the guard above shows.
 */
@Composable
internal fun ErrorState(error: String?, retryable: Boolean, onRetry: () -> Unit) {
    Spacer(Modifier.height(40.dp))
    Icon(Icons.Default.HourglassTop, null, tint = StatusFail, modifier = Modifier.size(48.dp))
    Spacer(Modifier.height(12.dp))
    Text(
        error ?: stringResource(R.string.session_error_generic),
        color = TextPrimary,
        textAlign = TextAlign.Center,
        fontSize = 14.sp
    )
    // A-SUB wave 1 (user decision 4): a refusal that NAMES its reason (the shop's subscription ended, no credit) is answered the same on every retry - no button for it.
    if (retryable) {
        Spacer(Modifier.height(24.dp))
        NCPrimaryButton(
            text = stringResource(R.string.session_retry),
            icon = Icons.Default.Refresh,
            onClick = onRetry
        )
    }
}

@Composable
private fun ExpiredState(onRetry: () -> Unit) {
    Spacer(Modifier.height(40.dp))
    Icon(Icons.Default.HourglassTop, null, tint = StatusWarning, modifier = Modifier.size(48.dp))
    Spacer(Modifier.height(12.dp))
    Text(
        stringResource(R.string.session_expired),
        color = TextPrimary,
        fontSize = 14.sp
    )
    Spacer(Modifier.height(24.dp))
    NCPrimaryButton(
        text = stringResource(R.string.session_new_code),
        icon = Icons.Default.Refresh,
        onClick = onRetry
    )
}

/**
 * The session is cancelled - by this owner's button, or by the customer's device (it left, or declined the consent). The text is decided by the server's `cancelReason`
 * ([cancelReason], through [ownerCancelledTextFor]): «غادر العميل الجلسة» when the customer left (user decision 5, 2026-10-16), and text 3 «أُلغيت الجلسة» for the owner's
 * own cancel, for a declined consent, and for a reason that is absent or unknown (user decision 2026-10-15, text 3; the app logs which). The same «new code» button the
 * expired state has. `internal` so a rendered test can show the text on the screen (`OwnerCancelledStateTest`).
 */
@Composable
internal fun CancelledState(cancelReason: CancelReason.Read, onNewCode: () -> Unit) {
    Spacer(Modifier.height(40.dp))
    Icon(Icons.Default.Close, null, tint = StatusWarning, modifier = Modifier.size(48.dp))
    Spacer(Modifier.height(12.dp))
    Text(
        stringResource(ownerCancelledTextFor(cancelReason).resId),
        color = TextPrimary,
        fontSize = 14.sp
    )
    Spacer(Modifier.height(24.dp))
    NCPrimaryButton(
        text = stringResource(R.string.session_new_code),
        icon = Icons.Default.Refresh,
        onClick = onNewCode
    )
}

/**
 * The owner's cancel (batch 2, item 2; user decisions D1 and decision 90, texts 1 and 2): the button «إلغاء الجلسة» opens the confirmation
 * (title and body approved, the existing btn_confirm / btn_cancel); a confirmed cancel is sent for the session this screen shows. While it is out the
 * button is disabled; a refusal shows its text under the button. Shown on a `pending` or `scanning` session only.
 */
@Composable
private fun CancelSessionControl(cancel: GenerateSessionViewModel.CancelUi, onConfirmed: () -> Unit) {
    var confirming by rememberSaveable { mutableStateOf(false) }
    val inFlight = cancel is GenerateSessionViewModel.CancelUi.InFlight
    OutlinedButton(
        onClick = { confirming = true },
        enabled = !inFlight,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.5.dp, StatusFail.copy(alpha = 0.6f))
    ) {
        if (inFlight) {
            CircularProgressIndicator(color = StatusFail, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
        } else {
            Icon(Icons.Default.Close, contentDescription = null, tint = StatusFail, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(8.dp))
        Text(
            stringResource(R.string.session_cancel_btn),
            color = StatusFail,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
    if (cancel is GenerateSessionViewModel.CancelUi.Failed) {
        Spacer(Modifier.height(8.dp))
        Text(cancel.message, color = StatusFail, fontSize = 13.sp, textAlign = TextAlign.Center)
    }
    if (confirming) {
        AlertDialog(
            containerColor = CardDark,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            onDismissRequest = { confirming = false },
            title = { Text(stringResource(R.string.session_cancel_confirm_title)) },
            text = { Text(stringResource(R.string.session_cancel_confirm_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirming = false
                    onConfirmed()
                }) { Text(stringResource(R.string.btn_confirm), color = StatusFail) }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) {
                    Text(stringResource(R.string.btn_cancel), color = TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun SessionContent(
    state: GenerateSessionViewModel.State,
    code: String,
    navController: NavController,
    onCreateAnother: () -> Unit,
    onCancelConfirmed: () -> Unit,
    onSelectPurpose: (ScanPurposeChoice) -> Unit,
    onSelectDefectType: (DefectType) -> Unit,
    onSelectPair: (String) -> Unit,
    onConfirmPair: () -> Unit,
    onReportOpened: () -> Unit,
    onRepeatNotNow: () -> Unit
) {
    val context = LocalContext.current

    // ZXing encodes the same deeplink the customer device would type, so
    // scanning the QR from the existing QRScannerScreen (or any stock QR
    // app) yields a string we can parse back to the code.
    val qrBitmap = remember(code) {
        val payload = "neuralcheck://session/$code"
        // A-SUB wave 1, item 10: the payload IS the session code inside a link - hashed, with its length, never printed
        android.util.Log.d("NeuralCheck.QR", "GenerateSession: encoded QR = ${LogRedaction.hashId(payload)} len=${payload.length}")
        // a QR that cannot be drawn leaves the code chip below (the owner can still read it out or copy it): the failure is printed, not swallowed
        runCatching { QrCodeHelper.generateRawQrBitmap(payload) }
            .onFailure {
                android.util.Log.w("NeuralCheck.QR", "GenerateSession: the QR could not be drawn (${it.javaClass.simpleName}: ${it.message?.take(200)}); the code is shown as text")
            }
            .getOrNull()
    }

    if (qrBitmap != null) {
        Box(
            modifier = Modifier
                .size(240.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(androidx.compose.ui.graphics.Color.White)
                .padding(14.dp),
            contentAlignment = Alignment.Center
        ) {
            Image(
                bitmap = qrBitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize()
            )
        }
    }

    // The purpose chips (A-P6-03) and the «after maintenance» pair list, under the QR and above the code: one composable, so a test draws exactly what the screen draws.
    PurposeAndPairSections(
        state = state,
        onSelectPurpose = onSelectPurpose,
        onSelectDefectType = onSelectDefectType,
        onSelectPair = onSelectPair,
        onConfirmPair = onConfirmPair
    )

    Spacer(Modifier.height(22.dp))

    Text(
        stringResource(R.string.session_share_code),
        color = TextSecondary,
        fontSize = 13.sp
    )
    Spacer(Modifier.height(10.dp))

    // Code + copy chip in one row. Tap anywhere to copy so the owner
    // doesn't have to aim for a tiny icon.
    CodeChip(code, onCopy = {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("NeuralCheck session code", code))
    })

    // Live countdown — the session TTL is enforced server-side, but showing
    // the owner a concrete "⏳ 14:32" clock builds trust that the code they
    // just handed out is still valid. It needs the lifetime the server named (order 2026-10-15,
    // rule 22): an answer without it shows no countdown rather than the 15:00 it used to default to.
    state.expiresInMinutes?.let { totalMinutes ->
        Spacer(Modifier.height(14.dp))
        SessionCountdown(totalMinutes = totalMinutes)
    }

    Spacer(Modifier.height(20.dp))

    StatusCard(state.phase)

    // A-P6-08 (استمارة جهاز الفنّي, PLAN-06 section 10): the form is BEHIND THE GUARD - nothing is drawn unless TECH_FORM_TEXTS_APPROVED is true (it is, since 2026-10-23: the texts are approved and written) and
    // technicianFormLabels() has the wording (TechnicianFormSurface.kt). It shows while the session is scanning (the form is filled during the scan, before the result), and stays until the owner leaves for the report.
    val formLabels = technicianFormLabels()
    val formSessionId = state.sessionId
    if (TECH_FORM_TEXTS_APPROVED && formLabels != null && formSessionId != null && (state.phase == Phase.SCANNING || state.phase == Phase.COMPLETED)) {
        Spacer(Modifier.height(16.dp))
        TechnicianFormSection(sessionId = formSessionId, labels = formLabels)
    }

    // A-P6-07 (ف١٤: the notice that the scanned phone was chosen for a cold repeat): BEHIND ITS OWN GUARD - nothing is drawn unless REPEAT_TEXTS_APPROVED is true (it is, since 2026-10-23: the texts are
    // approved and written) and repeatLabels() has the wording (RepeatScanSurface.kt). It shows once the session is completed, for a phone the server chose, until the technician presses «ليس الآن» (this screen only).
    val repeatLabels = repeatLabels()
    val repeatNotice = state.repeatNotice
    if (REPEAT_TEXTS_APPROVED && repeatLabels != null && state.phase == Phase.COMPLETED && repeatNotice is RepeatNoticeUi.Notice && !repeatNotice.dismissed) {
        Spacer(Modifier.height(16.dp))
        RepeatNoticeSection(labels = repeatLabels, onNotNow = onRepeatNotNow)
    }

    // Batch 2, item 2: the owner can cancel a session that is pending (WAITING) or scanning, never one that is completed.
    if (state.phase == Phase.WAITING || state.phase == Phase.SCANNING) {
        Spacer(Modifier.height(16.dp))
        CancelSessionControl(cancel = state.cancel, onConfirmed = onCancelConfirmed)
    }

    if (state.phase == Phase.COMPLETED) {
        Spacer(Modifier.height(20.dp))
        NCPrimaryButton(
            text = stringResource(R.string.session_view_report),
            icon = Icons.Default.CheckCircle,
            enabled = !state.completedScanId.isNullOrBlank(),
            onClick = {
                state.completedScanId?.let { scanId ->
                    // A-P6-08: the report is about to be shown for the first time on this phone - the server is told (behind the form's guard, nothing is sent today); it does not delay the navigation
                    onReportOpened()
                    navController.navigate("report/$scanId")
                }
            }
        )
    }

    // "+ كود جديد لجهاز آخر" — always visible in WAITING / JOINED / SCANNING /
    // COMPLETED. Pressing it spawns a fresh sessionId via createScanSession;
    // GenerateSessionViewModel.create() detaches the prior listener and
    // attaches a new one to the new sessionId, so the displayed code rotates
    // immediately. The previous code stays valid server-side until its
    // TTL expires (60 minutes — relayed: createScanSession.js:29, not verified from this
    // repository; the comment said 15 until 2026-10-15) — a customer who already started
    // joining can still finish on the old code, and the resulting report is filed against
    // the owner's userId via the session's ownerId. Two live sessions for one owner stay
    // allowed (user decision 2026-10-15): this button is unchanged, and any cancel names
    // ONE session and touches no other.
    Spacer(Modifier.height(24.dp))
    OutlinedButton(
        onClick = onCreateAnother,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.5.dp, NeuralGreen.copy(alpha = 0.55f))
    ) {
        Icon(
            Icons.Default.Refresh,
            contentDescription = null,
            tint = NeuralGreen,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            stringResource(R.string.session_create_new_btn),
            color = NeuralGreen,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
    Spacer(Modifier.height(6.dp))
    Text(
        stringResource(R.string.session_create_new_hint),
        color = TextMuted,
        fontSize = 11.sp,
        textAlign = TextAlign.Center
    )
}

/**
 * The two sections of the owner's waiting screen that belong to the maintenance before/after cycle - the purpose chips with the fault-type list, and the pair list of an «after» session - exactly as [SessionContent]
 * draws them (it calls this): split out so that a test draws what the screen draws, with the real labels, for a state the real view model made.
 */
@Composable
internal fun PurposeAndPairSections(
    state: GenerateSessionViewModel.State,
    onSelectPurpose: (ScanPurposeChoice) -> Unit,
    onSelectDefectType: (DefectType) -> Unit,
    onSelectPair: (String) -> Unit,
    onConfirmPair: () -> Unit
) {
    // A-P6-03 (ف١: the purpose chips, under the QR and above the code). BEHIND THE GUARD: nothing is drawn unless PURPOSE_CHIPS_TEXTS_APPROVED is true (it is, since Hasan approved the texts on 2026-10-22), the create's answer
    // offered the shop the chips (purposeOffered), and purposeChipLabels() has the approved wording (SessionPurposeChips.kt). The logic (the shop's parts, the pending rule, the call) is the view model's and runs without the surface.
    val purposeOffered = state.purposes as? PurposeUi.Offered
    val purposeLabels = purposeChipLabels()
    if (PURPOSE_CHIPS_TEXTS_APPROVED && purposeOffered != null && purposeLabels != null) {
        Spacer(Modifier.height(16.dp))
        SessionPurposeChips(
            offered = purposeOffered,
            locked = purposeChipsLocked(state.phase, purposeOffered),
            labels = purposeLabels,
            onSelect = onSelectPurpose,
            onSelectType = onSelectDefectType
        )
    }

    // Order of 2026-10-22 (the «after maintenance» pair list, under the purpose chips). BEHIND THE GUARD: nothing is drawn unless PAIR_LIST_TEXTS_APPROVED is true (it is, since Hasan approved the texts on 2026-10-22),
    // pairLabels() has the approved wording (PairListSurface.kt) and the owner's session asks for the list (pairListVisible). The logic (when the list is asked for, the choice, the confirmation) is the view model's and
    // runs without the surface; the two calls leave the phone only through the service's gate, which reads the same guard.
    val pairLabels = pairLabels()
    if (PAIR_LIST_TEXTS_APPROVED && pairLabels != null && pairListVisible(state.phase, state.pairs)) {
        Spacer(Modifier.height(16.dp))
        PairListSection(
            ui = state.pairs,
            labels = pairLabels,
            actions = PairListActions(onSelect = onSelectPair, onConfirm = onConfirmPair)
        )
    }
}

@Composable
private fun CodeChip(code: String, onCopy: () -> Unit) {
    Surface(
        color = CardDark,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.5.dp, NeuralGreen.copy(alpha = 0.55f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                code,
                color = NeuralGreen,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold
            )
            // "Copy" is now a clearly tinted button instead of a borderline
            // invisible TextButton — matches the primary accent on the code
            // itself so the action reads as part of the same element.
            Surface(
                color = NeuralGreen.copy(alpha = 0.14f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, NeuralGreen.copy(alpha = 0.4f)),
                onClick = onCopy,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.ContentCopy, null,
                        tint = NeuralGreen,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(R.string.session_copy),
                        color = NeuralGreen,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

/**
 * Live MM:SS countdown anchored to the session's expiry (server-side TTL).
 * Ticks at 1 Hz; on reach-zero the remaining text sticks at "00:00" rather
 * than flipping negative — the server will mark the session expired and the
 * VM's snapshot listener will surface [Phase.EXPIRED].
 */
@Composable
private fun SessionCountdown(totalMinutes: Int) {
    // Anchor the expiry timestamp to first composition so that recompositions
    // from the parent (status changes, rotator flips) don't reset the clock.
    val expiryMs = remember(totalMinutes) {
        System.currentTimeMillis() + totalMinutes * 60_000L
    }
    var remaining by remember { mutableLongStateOf(expiryMs - System.currentTimeMillis()) }
    LaunchedEffect(expiryMs) {
        while (remaining > 0L) {
            delay(1_000L)
            remaining = (expiryMs - System.currentTimeMillis()).coerceAtLeast(0L)
        }
    }
    val mm = (remaining / 60_000L).toInt().coerceAtLeast(0)
    val ss = ((remaining / 1_000L) % 60L).toInt().coerceAtLeast(0)
    val mmSs = "%02d:%02d".format(mm, ss)
    Text(
        text = stringResource(R.string.session_time_remaining_fmt, mmSs),
        color = if (remaining > 60_000L) TextMuted else StatusWarning,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold
    )
}

/**
 * "بانتظار الربط" with three animated dots that pulse in sequence. Cheaper
 * than lottie and needs no asset — three scaled circles phased 200 ms apart.
 */
@Composable
private fun WaitingDots(text: String) {
    val transition = rememberInfiniteTransition(label = "waiting_dots")
    val phases = List(3) { i ->
        transition.animateFloat(
            initialValue = 0.4f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = 900,
                    delayMillis = i * 200,
                    easing = FastOutSlowInEasing
                ),
                repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
            ),
            label = "waiting_dot_$i"
        )
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text, color = TextPrimary, fontSize = 14.sp)
        Spacer(Modifier.width(2.dp))
        phases.forEach { phase ->
            Text(
                text = ".",
                color = TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.graphicsLayer(alpha = phase.value)
            )
        }
    }
}

@Composable
private fun StatusCard(phase: Phase) {
    // Batch 2, item 4 (rule 22): each phase this card is for has its colour and its text; the others have their own screens (CreatingState, ErrorState,
    // ExpiredState, CancelledState) and get NO card - they used to get an empty one (`else -> ""`).
    val (iconTint: androidx.compose.ui.graphics.Color, text: String) = when (phase) {
        Phase.WAITING -> StatusWarning to stringResource(R.string.session_waiting_dots)
        Phase.SCANNING -> SkyCyan to stringResource(R.string.session_scanning)
        Phase.COMPLETED -> StatusPass to stringResource(R.string.session_completed)
        Phase.CREATING, Phase.EXPIRED, Phase.CANCELLED, Phase.ERROR -> return
    }
    Surface(
        color = CardDark,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, CardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (phase == Phase.WAITING || phase == Phase.SCANNING) {
                CircularProgressIndicator(
                    color = iconTint,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(18.dp)
                )
            } else {
                Icon(Icons.Default.CheckCircle, null, tint = iconTint, modifier = Modifier.size(20.dp))
            }
            if (phase == Phase.WAITING) {
                WaitingDots(text = text)
            } else {
                Text(text, color = TextPrimary, fontSize = 14.sp)
            }
        }
    }
}
