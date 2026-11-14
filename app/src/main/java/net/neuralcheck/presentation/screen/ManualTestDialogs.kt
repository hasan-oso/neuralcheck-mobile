package net.neuralcheck.presentation.screen

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.ConsumerIrManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.usb.UsbManager
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.delay
import net.neuralcheck.R
import net.neuralcheck.domain.manual.MicTestRecordings
import net.neuralcheck.domain.manual.TouchGridRecorder
import net.neuralcheck.domain.manual.VolumeKeyRecorder
import net.neuralcheck.domain.model.ManualStatuses
import net.neuralcheck.presentation.theme.CardDark
import net.neuralcheck.presentation.theme.DeepSpace
import net.neuralcheck.presentation.theme.NeuralGreen
import net.neuralcheck.presentation.theme.StatusFail
import net.neuralcheck.presentation.theme.StatusPass
import net.neuralcheck.presentation.theme.TextPrimary
import net.neuralcheck.presentation.theme.TextSecondary
import java.io.File
import kotlin.math.PI
import kotlin.math.sin
import net.neuralcheck.util.NumberFormat

/**
 * Manual tests that expose an interactive runner.
 *
 * Each entry maps to a routing branch in [net.neuralcheck.presentation.screen.ReportScreen]'s
 * ManualTestRow. Tests NOT in this set are scored only by the user tapping
 * pass/fail/not_tested chips.
 */
val interactiveManualTests = setOf(
    "M-01", // dead pixel cycle
    "M-02", // touch grid
    "M-04", // speaker tone
    "M-05", // earpiece tone
    "M-06", // primary mic (record + playback)
    // M-07 (secondary mic) removed Phase 1.15: the CAMCORDER source can fall
    // back to the PRIMARY mic, so record/playback couldn't prove the secondary
    // mic specifically (false-pass risk). It is not in the catalog any more: no
    // dialog and no chip question (corrected in batch 2, item 5 - it said "now a yes/no chip question").
    "M-08", // headphone jack (wired audio device detection)
    "M-09", // volume up button
    "M-10", // volume down button
    "M-12", // charging port (BATTERY_PLUGGED_USB)
    // M-13 (OTG) + M-16 (IR) removed Phase 1.15: retired from the catalog and
    // have NO routing branch, so these entries were dead/misleading.
    "M-14", // wireless charging (BATTERY_PLUGGED_WIRELESS)
    "M-15", // SD card (removable StorageVolume)
    "M-17", // rear camera live preview
    "M-18", // front camera live preview
    "M-19", // torch / flashlight toggle
    // M-20 (fingerprint) removed Phase 1.15: firing a real BiometricPrompt is
    // inappropriate in a diagnostic scan (technician isn't the enrolled user;
    // repeated attempts can lock the sensor; needs an enrolled print). Now a
    // yes/no chip question, like its sibling M-21 (face).
    "M-21", // face recognition (yes/no question dialog)
    "M-22"  // vibrator one-shot
)

// ---------------------------------------------------------------------------
// M-01 — Dead pixel detector: full-screen color cycle, then Y/N question.
//
// The colour phase is the WHOLE display: ImmersiveFullScreenDialog (the host M-02's grid has — user decision 2026-10-21, «نعم، أصلح M-01 بالمضيف الجديد»): a window of its
// own, edge to edge, bars hidden, over the cut-out, with DeadPixelRunContent filling it. It was Compose's Dialog(DialogProperties(usePlatformDefaultWidth = false)), whose
// window was sized to the content measured without the system's decorations: the colour stopped short of the edges, where a dead pixel is looked for (the cause is quoted in
// ImmersiveFullScreenDialog's note). What the run DOES is what it did: the cycle (DeadPixelCycle.COLORS), a tap on the colour advancing it, the question after the last
// colour, the two answers it writes («pass» / «fail», no raw: M-01 records none) and back = dismissed. The question stays an AlertDialog: it is not the colour.
// ---------------------------------------------------------------------------
@Composable
fun DeadPixelTestDialog(
    onResult: (status: String) -> Unit,
    onDismiss: () -> Unit
) {
    var idx by remember { mutableStateOf(0) }
    var asking by remember { mutableStateOf(false) }

    if (!asking) {
        ImmersiveFullScreenDialog(onBack = onDismiss) {
            DeadPixelRunContent(
                colorIndex = idx,
                onTap = { if (idx < DeadPixelCycle.COLORS.lastIndex) idx++ else asking = true }
            )
        }
    } else {
        AlertDialog(
            containerColor = CardDark,
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.manual_dead_pixel_title), color = TextPrimary) },
            text = { Text(stringResource(R.string.manual_dead_pixel_question), color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = { onResult(ManualStatuses.PASS) }) {
                    Text(stringResource(R.string.manual_screen_ok), color = StatusPass)
                }
            },
            dismissButton = {
                TextButton(onClick = { onResult(ManualStatuses.FAIL) }) {
                    Text(stringResource(R.string.manual_screen_issue), color = StatusFail)
                }
            }
        )
    }
}

// ---------------------------------------------------------------------------
// M-02 — Touch response: a «frame + X» of square 4 mm cells under an empty strip at the top of the screen, filled by sweeping.
//
// fieldsVersion 71 (contract docs/FV68_CONTRACT.md section 7-ي; order A-M02 of 2026-10-18; design
// evidence/m02-touch-grid/design-m02-touch-grid-2026-10-13.md). Until fieldsVersion 70 the grid was a fixed 8 × 15 and a cell lit where a
// sample landed. One phase, no review screen:
//
//   - the dialog is the whole screen (ImmersiveFullScreenDialog: a window of its own, edge to edge, bars hidden, over the cut-out) and the grid is laid out
//     in it from the screen's density (TouchGridLayout): SQUARE cells of TouchGridContract.CELL_TARGET_MM = 4 mm, a count that comes out of the window (about 18
//     columns on a phone 72 mm wide; the rows as many whole cells as fit under an empty strip of at least TOP_STRIP_CELLS cells — the strip is what is left, so the grid ends
//     at the bottom edge; TouchGridRunContent, user decision 2026-10-21 — before it the grid ended at ~83% of the height, with a title bar above it and a bar below it,
//     because Compose's Dialog sized the window to the content measured without the system's decorations: ImmersiveFullScreenDialog's note, and then the shape of the
//     system's own touch test). The touch surface of the run is the grid's region of the window, the strip not in it;
//     and, since the user's decision of the same day («M-02: استبدل الشبكة الحالية باختبار لمس من نوع «إطار + X»»), the cells a run REQUIRES are the frame (one cell thick, on the
//     edge of the grid) and the two diagonals from the inner corners of the frame (TouchGridPattern) — the four triangles between them are the dark background, no cell,
//     nothing asked of them — minus the cells a camera cut-out touches;
//   - a cell turns green the moment a finger's PATH crosses it (a drag fills every cell it passes, a tap one); every sample the platform
//     delivers — the grouped history included, and a path across the triangles — is recorded raw (TouchGridRecorder.measurements: `strokes[]`), nothing smoothed;
//   - when EVERY required cell is lit the test passes by itself (endReason auto_complete) and the dialog closes: no marks are shown
//     after it (E21 item 2, approved 2026-10-15);
//   - the title, the live count and the hint are text with no pointer input, in the empty strip above the grid (no cell is there): a touch on them goes to the surface under them. And, 15 s
//     after the dialog opens (TECHNICIAN_BUTTONS_AFTER_MS — from OPENING, not from the first touch: a dead screen never gives one) the technician's two
//     buttons «لا يعمل» (fail, user_marked_not_working) and «لم أختبر» (not_tested, user_marked_not_tested) appear in the largest triangle (the bottom one on a phone held
//     upright), at a place TouchGridOverlay decides: the box of no element — text or button — meets a required cell's, they never change the grid's rectangle or its numbers
//     (user decision 2026-10-21; it replaces E21 item 4's bar below the surface, which is gone). There is NO "works" button — the grid itself says it;
//   - the marks shown while the run goes on (a ring where a moving finger's path jumped; amber where it left through an edge) are a display
//     log — `displayedSuspects` — and never a verdict: the server decides from the raw. The counter text for them and every guidance text
//     the design proposes (§9) are NOT here: they await the user's approval (E21 item 5) and no string resource carries them;
//   - back = dismissed (the dialog covers the display: there is no «outside» to touch).
//
// The surface asks the system to leave its gestures to the app (as far as the platform lets an app: at most 200 dp of each side edge, none of the bottom
// one) and the orientation is locked while the run is open (design §4.2, §5). None of it is verified on a device yet — a field-round item (rule 19).
// ---------------------------------------------------------------------------
@Composable
fun TouchGridTestDialog(
    /** [measurements] is null only if the run ended before the grid was ever laid out (never in practice): the answer is then written with no raw. */
    onResult: (status: String, measurements: Map<String, Any?>?) -> Unit,
    /** null = nothing was touched, so there is no run to record (an accidental open must not
     *  overwrite the raw of a real earlier run). */
    onDismiss: (measurements: Map<String, Any?>?) -> Unit
) {
    val recorder = remember {
        TouchGridRecorder(nowMs = { SystemClock.elapsedRealtime() }, uptimeMs = { SystemClock.uptimeMillis() })
    }
    // The surface view lives for the whole dialog; it must call the CURRENT callbacks.
    val currentOnResult by rememberUpdatedState(onResult)
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    var touched by remember { mutableIntStateOf(0) }
    var total by remember { mutableIntStateOf(0) }
    var buttonsVisible by remember { mutableStateOf(false) }
    // One verdict per run: a finished run ignores every later event (a late touch, the dismissal that follows).
    var finished by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(TouchGridRecorder.TECHNICIAN_BUTTONS_AFTER_MS)
        buttonsVisible = true
    }

    // The orientation is locked while the run is open (design §5: a rotation would lose the run), and given back after.
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val activity = context.findFragmentActivity()
        val previous = activity?.requestedOrientation
        activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LOCKED
        onDispose { if (activity != null && previous != null) activity.requestedOrientation = previous }
    }

    fun finish(endReason: String) {
        if (finished) return
        finished = true
        val raw = if (recorder.isConfigured) recorder.measurements(endReason) else null
        val status = recorder.statusFor(endReason)
        if (status != null) currentOnResult(status, raw)
        else currentOnDismiss(if (recorder.touchedCount > 0) raw else null)
    }

    // Called by the surface after every touch event and every layout: the counters, and the automatic completion.
    val onProgress: () -> Unit = {
        if (touched != recorder.touchedCount) touched = recorder.touchedCount
        if (total != recorder.totalCells) total = recorder.totalCells
        if (recorder.isComplete) finish(TouchGridRecorder.EndReasons.AUTO_COMPLETE)
    }

    ImmersiveFullScreenDialog(onBack = { finish(TouchGridRecorder.EndReasons.DISMISSED) }) {
        TouchGridRunContent(
            recorder = recorder,
            touched = touched,
            total = total,
            buttonsVisible = buttonsVisible,
            onProgress = onProgress,
            onNotWorking = { finish(TouchGridRecorder.EndReasons.USER_MARKED_NOT_WORKING) },
            onNotTested = { finish(TouchGridRecorder.EndReasons.USER_MARKED_NOT_TESTED) }
        )
    }
}

// ---------------------------------------------------------------------------
// M-04 / M-05 — Audio tone: plays 1 kHz sine for 2 s on speaker or earpiece,
// then asks "did you hear it?".
// ---------------------------------------------------------------------------
@Composable
fun AudioToneTestDialog(
    isEarpiece: Boolean,
    onResult: (status: String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var playing by remember { mutableStateOf(true) }

    DisposableEffect(Unit) {
        val track = playTone(context, isEarpiece)
        onDispose {
            try { track.stop() } catch (_: Exception) {}
            try { track.release() } catch (_: Exception) {}
            if (isEarpiece) {
                val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                am.mode = AudioManager.MODE_NORMAL
                am.isSpeakerphoneOn = true
            }
        }
    }

    LaunchedEffect(Unit) {
        delay(2200)
        playing = false
    }

    AlertDialog(
        containerColor = CardDark,
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.manual_audio_title), color = TextPrimary) },
        text = {
            Text(
                if (playing) stringResource(R.string.manual_audio_playing)
                else stringResource(R.string.manual_audio_question),
                color = TextSecondary
            )
        },
        confirmButton = {
            TextButton(
                enabled = !playing,
                onClick = { onResult("pass") }
            ) { Text(stringResource(R.string.manual_audio_heard), color = StatusPass) }
        },
        dismissButton = {
            TextButton(
                enabled = !playing,
                onClick = { onResult("fail") }
            ) { Text(stringResource(R.string.manual_audio_not_heard), color = StatusFail) }
        }
    )
}

// ---------------------------------------------------------------------------
// M-06 — Microphone test.
// Records 3 s via MediaRecorder then plays back via MediaPlayer. Caller picks
// audioSource; the only caller (M-06) passes MIC (primary). M-07 (CAMCORDER,
// "secondary") was removed in Phase 1.15 - see interactiveManualTests above -
// and nothing passes CAMCORDER any more (corrected in batch 2, item 5: this
// comment still described M-07 as a second caller).
// ---------------------------------------------------------------------------

/**
 * Order 2026-09-30 (بند ٢): pure decision behind [MicrophoneTestDialog]'s
 * RECORD_AUDIO permission-result callback. A denied permission means we
 * never got to try recording — that's not evidence the microphone hardware
 * is broken, so it must record "not_tested", never "fail" (the old
 * behaviour: `if (!granted) phase = "error"` shared its dialog UI with the
 * genuine MediaRecorder-start-failure branch further down, so a permission
 * denial and a real hardware failure both ended up forcing the same
 * confirmButton-only "لا يعمل" click — see ManualTestDialogs.kt's tracked
 * history for the exact prior lines).
 *
 * Extracted to a top-level function (rather than inlined in the launcher
 * lambda) so it is unit-testable without drawing the dialog: MicrophoneTestDialog
 * itself is a Composable. When this was written (checked 2026-09-30) the unit-test
 * setup lacked Robolectric and androidx.compose.ui.test, so the Composable's full
 * behaviour could not run under `testLaunchDebugUnitTest`; both are test dependencies since 3284cca
 * (batch 2, item 1, 2026-10-15), so a rendered test of this dialog is no longer ruled out
 * by the setup - none exists. This function IS the production decision the
 * dialog calls, not a re-implementation of it — MicrophoneTestDialog below
 * has no other branch that decides this.
 */
internal fun micPermissionResultToStatus(granted: Boolean): String? =
    if (granted) null else "not_tested"

@Composable
fun MicrophoneTestDialog(
    audioSource: Int,
    onResult: (status: String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var phase by remember { mutableStateOf("init") } // init, recording, playing, done, error
    var permGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED
        )
    }
    var permRequested by remember { mutableStateOf(false) }
    val permLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        permGranted = granted
        // Order 2026-09-30 (بند ٢): a denied RECORD_AUDIO permission is not
        // evidence the microphone is broken — we never got to try recording.
        // Auto-register "not_tested" and let the dialog close immediately
        // (onResult triggers ReportScreen's activeDialog = null) instead of
        // forcing the user through a confirmButton-only "لا يعمل" state that
        // used to record a false "mic failed" verdict — see the old
        // `phase = "error"` path this replaced, which shared its dialog UI
        // with the genuine MediaRecorder-start-failure branch below (that
        // branch is untouched: a recorder that fails to start after
        // permission WAS granted is a real hardware/OS failure, not a
        // permission problem, so it still surfaces as phase="error" →
        // "لا يعمل").
        micPermissionResultToStatus(granted)?.let { onResult(it) }
    }

    // the one source of the recording's name is MicTestRecordings (batch 2, item 5): the start-up sweep (NeuralCheckApp) reads the same prefix
    val file = remember { MicTestRecordings.newFile(context.cacheDir, System.currentTimeMillis()) }
    val recorderRef = remember { mutableStateOf<MediaRecorder?>(null) }
    val playerRef = remember { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            try { recorderRef.value?.stop() } catch (_: Exception) {}
            try { recorderRef.value?.release() } catch (_: Exception) {}
            try { playerRef.value?.stop() } catch (_: Exception) {}
            try { playerRef.value?.release() } catch (_: Exception) {}
            // Batch 2, item 5 (E28): a failed delete is no longer swallowed - the line carries the failure's message TEXT (rule 21); a recording that
            // survives (the process killed before this ran) is removed by the start-up sweep.
            MicTestRecordings.deleteAndLog(file, log = { Log.w(MicTestRecordings.LOG_TAG, it) })
        }
    }

    // Request permission once on first composition if not already granted.
    LaunchedEffect(Unit) {
        if (!permGranted && !permRequested) {
            permRequested = true
            permLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // Runs the full record → playback → done sequence whenever permission
    // becomes available. Keyed on permGranted (not phase) so the coroutine
    // mutating phase does not cancel itself — the earlier keying-on-phase
    // bug caused the dialog to get stuck on "recording".
    LaunchedEffect(permGranted) {
        if (!permGranted) return@LaunchedEffect
        // Record
        phase = "recording"
        val rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION") MediaRecorder()
        }
        recorderRef.value = rec
        val started = try {
            rec.setAudioSource(audioSource)
            rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            rec.setAudioSamplingRate(44_100)
            rec.setAudioEncodingBitRate(128_000)
            rec.setOutputFile(file.absolutePath)
            rec.prepare()
            rec.start()
            true
        } catch (_: Exception) { false }
        if (!started) {
            try { rec.release() } catch (_: Exception) {}
            recorderRef.value = null
            phase = "error"
            return@LaunchedEffect
        }
        delay(3000)
        try { rec.stop() } catch (_: Exception) {}
        try { rec.release() } catch (_: Exception) {}
        recorderRef.value = null

        // Playback
        phase = "playing"
        val mp = MediaPlayer()
        playerRef.value = mp
        val ok = try {
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            mp.setDataSource(file.absolutePath)
            mp.prepare()
            mp.start()
            true
        } catch (_: Exception) { false }
        if (!ok) {
            try { mp.release() } catch (_: Exception) {}
            playerRef.value = null
            // Playback failed but recording succeeded — still ask the user.
            phase = "done"
            return@LaunchedEffect
        }
        // Cap the wait so a runaway MediaPlayer can't freeze the dialog.
        val playStart = System.currentTimeMillis()
        while (playerRef.value?.isPlaying == true &&
               System.currentTimeMillis() - playStart < 10_000L) {
            delay(200)
        }
        try { mp.release() } catch (_: Exception) {}
        playerRef.value = null
        phase = "done"
    }

    AlertDialog(
        containerColor = CardDark,
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.manual_mic_title), color = TextPrimary) },
        text = {
            Column {
                val msg = when (phase) {
                    "init" -> stringResource(R.string.manual_mic_permission_required)
                    "ready" -> stringResource(R.string.manual_mic_ready)
                    "recording" -> stringResource(R.string.manual_mic_recording)
                    "playing" -> stringResource(R.string.manual_mic_playing)
                    "done" -> stringResource(R.string.manual_mic_question)
                    else -> stringResource(R.string.manual_mic_error)
                }
                Text(msg, color = TextSecondary)
                if (phase == "recording" || phase == "playing") {
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = NeuralGreen
                    )
                }
            }
        },
        confirmButton = {
            when (phase) {
                "done" -> TextButton(onClick = { onResult("pass") }) {
                    Text(stringResource(R.string.manual_audio_heard), color = StatusPass)
                }
                "error" -> TextButton(onClick = { onResult("fail") }) {
                    Text(stringResource(R.string.manual_not_works), color = StatusFail)
                }
                else -> TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.btn_cancel), color = TextSecondary)
                }
            }
        },
        dismissButton = {
            if (phase == "done") {
                TextButton(onClick = { onResult("fail") }) {
                    Text(stringResource(R.string.manual_audio_not_heard), color = StatusFail)
                }
            }
        }
    )
}

// ---------------------------------------------------------------------------
// M-08 — Headphone jack: watch AudioManager for a wired headset showing up
// on the device list. Gives the user an "unavailable" escape hatch for
// phones that no longer ship with a 3.5 mm jack.
// ---------------------------------------------------------------------------
@Composable
fun HeadphoneJackTestDialog(
    onResult: (status: String) -> Unit,
    onDismiss: () -> Unit,
    /** Whether this test offers the answer `unavailable` (button text: `manual_mark_unavailable`): `ManualUnavailableOffer.isOffered(testId)`, decided by the row. */
    offerUnavailable: Boolean
) {
    val context = LocalContext.current
    val am = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val timeoutMs = 15_000L
    var remainingSec by remember { mutableStateOf((timeoutMs / 1000).toInt()) }
    var detected by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeoutMs) {
            val outs = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            val hasJack = outs.any {
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES
            }
            if (hasJack) { detected = true; break }
            delay(250)
            remainingSec = ((timeoutMs - (System.currentTimeMillis() - start)) / 1000).toInt().coerceAtLeast(0)
        }
        if (detected) onResult("pass")
        // otherwise leave dialog open so the user can mark unavailable / not works
    }

    AlertDialog(
        containerColor = CardDark,
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.manual_headphone_title), color = TextPrimary) },
        text = {
            Column {
                Text(
                    if (detected) stringResource(R.string.manual_headphone_detected)
                    else stringResource(R.string.manual_headphone_waiting),
                    color = if (detected) StatusPass else TextSecondary
                )
                Spacer(Modifier.height(8.dp))
                if (!detected) {
                    Text(
                        NumberFormat.toLatin(
                            stringResource(R.string.manual_remaining_sec_fmt, remainingSec)
                        ),
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = 1f - (remainingSec.toFloat() / (timeoutMs / 1000)),
                        modifier = Modifier.fillMaxWidth(),
                        color = NeuralGreen
                    )
                }
            }
        },
        confirmButton = {
            if (detected) {
                TextButton(onClick = { onResult("pass") }) {
                    Text(stringResource(R.string.manual_works), color = StatusPass)
                }
            } else {
                TextButton(onClick = { onResult("fail") }) {
                    Text(stringResource(R.string.manual_not_works), color = StatusFail)
                }
            }
        },
        // Offered only where ManualUnavailableOffer says (the one list; the row's line under the chips reads it too).
        dismissButton = if (offerUnavailable) {
            {
                TextButton(onClick = { onResult(ManualStatuses.UNAVAILABLE) }) {
                    Text(stringResource(R.string.manual_mark_unavailable), color = TextSecondary)
                }
            }
        } else null
    )
}

// ---------------------------------------------------------------------------
// M-09 / M-10 — Volume button detector.
//
// fieldsVersion 71 (contract docs/FV68_CONTRACT.md section 7-ي ف-04; user approval E22 and N71-04 / N71-05 of 2026-10-15; order A-VOL of 2026-10-18).
// The ROW's «يعمل» chip does not open this dialog (user decision 2026-10-21, which replaced E22's «يعمل يفتح الحوار»): it writes `pass` and opens nothing —
// the dialog opens from «ابدأ الاختبار» alone (ManualRowActions.onChip, ManualChipActions).
// The run is recorded RAW by VolumeKeyRecorder: every volume-key event the dialog's window receives (never consumed, so the system still moves
// the level), the media stream's levels, and how it ended. Two ways to detect the press, whichever sees it first:
//   - the key event itself (`detectionSource = key_event`): the window's own dispatchKeyEvent is tapped, so no focus is needed — whether
//     the event reaches a Compose dialog on One UI was "not yet tried on a device" (VK:188) and is a field-round item;
//   - the media stream's level moving in the expected direction (`volume_level_change`), polled every 120 ms: volume keys mutate the level
//     whether or not the app's window gets the key event (AudioService may take the key first) — the reason this path stays.
// The dialog stays open for KEY_UP_GRACE_MS after a detection so the key's `up` (hence `holdMs`) can be recorded, then closes; no text is shown for it.
// The technician's «يعمل» button records `user_marked_working` (a claim, not a detection); a dismissal after at least one volume-key event
// records the answer the test already holds with the raw (`dismissed`), after none it records nothing.
// ---------------------------------------------------------------------------
@Composable
fun VolumeButtonTestDialog(
    isVolumeUp: Boolean,
    onResult: (status: String, measurements: Map<String, Any?>?) -> Unit,
    /** null = no volume-key event arrived, so there is no run to record (an accidental open must not overwrite the raw of a real earlier run). */
    onDismiss: (measurements: Map<String, Any?>?) -> Unit
) {
    val context = LocalContext.current
    val am = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val stream = AudioManager.STREAM_MUSIC
    fun readLevel(): Int? = runCatching { am.getStreamVolume(stream) }.getOrNull()
    val maxVol = remember { runCatching { am.getStreamMaxVolume(stream) }.getOrNull() }
    // Remember the user's original level so we can restore it on close.
    val originalVol = remember { readLevel() }
    val timeoutMs = 12_000L
    var remainingSec by remember { mutableStateOf((timeoutMs / 1000).toInt()) }
    val recorder = remember {
        VolumeKeyRecorder(
            expectedKeyCode = if (isVolumeUp) VolumeKeyRecorder.KEYCODE_VOLUME_UP else VolumeKeyRecorder.KEYCODE_VOLUME_DOWN,
            uptimeMs = { SystemClock.uptimeMillis() }
        )
    }
    val currentOnResult by rememberUpdatedState(onResult)
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    // How the press was detected (a VolumeKeyRecorder.DetectionSources value), once it was; null until then.
    var detectedBy by remember { mutableStateOf<String?>(null) }
    var levelAtOpen by remember { mutableStateOf<Int?>(null) }
    // One verdict per run: a finished run ignores every later event.
    var finished by remember { mutableStateOf(false) }

    fun finish(endReason: String, detectionSource: String? = null) {
        if (finished) return
        finished = true
        // the three levels are written only when the stream had room to be set to a mid level (maxVol >= 2); a level that could not be read is absent
        val levels = if (maxVol != null && maxVol >= 2) VolumeKeyRecorder.VolumeLevels(maxVol, levelAtOpen, readLevel()) else null
        val raw = recorder.measurements(endReason, detectionSource, levels)
        val status = VolumeKeyRecorder.statusFor(endReason)
        if (status != null) currentOnResult(status, raw)
        else currentOnDismiss(if (recorder.eventCount > 0) raw else null)
    }

    // Phase 1.15 fix: pre-set the media stream to mid-range so there is
    // headroom to move in BOTH directions. This kills the dead-ends where the
    // level was already at MAX (UP can't raise it) or at MIN (DOWN can't lower
    // it) — the old fixed-baseline check could then never pass. FLAG 0 = silent
    // (no system volume UI, no beep). Restored to the user's level on dispose.
    DisposableEffect(Unit) {
        runCatching { if (maxVol != null && maxVol >= 2) am.setStreamVolume(stream, maxVol / 2, 0) }
        levelAtOpen = readLevel()
        onDispose { runCatching { if (originalVol != null) am.setStreamVolume(stream, originalVol, 0) } }
    }

    // Tap the dialog window's key events: every volume-key event is recorded and passed on untouched. The window's own callback is used (not a
    // focused Compose node), so the dialog needs no focus to hear the key.
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.parent as? DialogWindowProvider)?.window
        val original = window?.callback
        if (window != null && original != null) {
            window.callback = KeyEventTap(original) { e ->
                val down = when (e.action) {
                    android.view.KeyEvent.ACTION_DOWN -> true
                    android.view.KeyEvent.ACTION_UP -> false
                    else -> null
                }
                val firstExpectedPress = recorder.onKeyEvent(e.keyCode, down, e.eventTime, e.repeatCount, e.deviceId, e.source)
                if (firstExpectedPress && detectedBy == null) detectedBy = VolumeKeyRecorder.DetectionSources.KEY_EVENT
            }
        }
        onDispose { if (window != null && original != null) window.callback = original }
    }

    // Detect a level change in the expected direction RELATIVE TO THE PREVIOUS
    // reading (not a fixed baseline), so the press is caught wherever the level
    // started. Detection is recorded as volume_level_change unless the key event got there first; on timeout we simply stop and let the
    // technician confirm with the manual buttons below — no silent auto-fail.
    LaunchedEffect(Unit) {
        var lastVol = readLevel()
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeoutMs && detectedBy == null) {
            delay(120)
            val now = readLevel()
            val moved = lastVol != null && now != null && (if (isVolumeUp) now > lastVol else now < lastVol)
            if (moved && detectedBy == null) { detectedBy = VolumeKeyRecorder.DetectionSources.VOLUME_LEVEL_CHANGE; return@LaunchedEffect }
            lastVol = now
            remainingSec = ((timeoutMs - (System.currentTimeMillis() - start)) / 1000)
                .toInt().coerceAtLeast(0)
        }
    }

    // After a detection the dialog stays a moment so the key's `up` is recorded (holdMs), then closes with the detection's name.
    LaunchedEffect(detectedBy) {
        val source = detectedBy ?: return@LaunchedEffect
        delay(KEY_UP_GRACE_MS)
        finish(VolumeKeyRecorder.EndReasons.AUTO_DETECTED, source)
    }

    AlertDialog(
        containerColor = CardDark,
        onDismissRequest = {
            val source = detectedBy
            if (source != null) finish(VolumeKeyRecorder.EndReasons.AUTO_DETECTED, source) else finish(VolumeKeyRecorder.EndReasons.DISMISSED)
        },
        title = { Text(stringResource(R.string.manual_button_title), color = TextPrimary) },
        text = {
            Column {
                Text(
                    if (isVolumeUp) stringResource(R.string.manual_button_press_volume_up)
                    else stringResource(R.string.manual_button_press_volume_down),
                    color = TextSecondary
                )
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = 1f - (remainingSec.toFloat() / (timeoutMs / 1000)),
                    modifier = Modifier.fillMaxWidth(),
                    color = NeuralGreen
                )
            }
        },
        // Phase 1.15: manual override so the technician can record the result
        // even if auto-detection doesn't fire (matches every other dialog;
        // the old dialog had only a "Close" button). Fieldsversion 71: it records `user_marked_working` — a claim — unless the press was already
        // detected, in which case the detection stands.
        confirmButton = {
            TextButton(onClick = {
                val source = detectedBy
                if (source != null) finish(VolumeKeyRecorder.EndReasons.AUTO_DETECTED, source)
                else finish(VolumeKeyRecorder.EndReasons.USER_MARKED_WORKING)
            }) {
                Text(stringResource(R.string.manual_works), color = StatusPass)
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { finish(VolumeKeyRecorder.EndReasons.USER_MARKED_NOT_WORKING) }) {
                    Text(stringResource(R.string.manual_not_works), color = StatusFail)
                }
                TextButton(onClick = { finish(VolumeKeyRecorder.EndReasons.USER_MARKED_NOT_TESTED) }) {
                    Text(stringResource(R.string.manual_not_tested_btn), color = TextSecondary)
                }
            }
        }
    )
}

/** How long the volume dialog stays open after a detection so the key's `up` (and `holdMs`) can be recorded. A technical figure, not a decision of the user's [ق]. */
private const val KEY_UP_GRACE_MS = 600L

/**
 * A window callback that lets [onKey] see every key event the window dispatches and then hands the event on unchanged (the result is the delegate's:
 * nothing is consumed here, so the system still moves the volume). Every other callback goes to [delegate] as it was.
 */
private class KeyEventTap(
    private val delegate: android.view.Window.Callback,
    private val onKey: (android.view.KeyEvent) -> Unit
) : android.view.Window.Callback by delegate {
    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        try { onKey(event) } catch (e: Exception) { Log.w("NeuralCheck.VolumeKeys", "key tap failed: ${e.message}") }
        return delegate.dispatchKeyEvent(event)
    }
}

// ---------------------------------------------------------------------------
// M-12 — Charging port: wait for ACTION_POWER_CONNECTED or a BatteryManager
// status flip to CHARGING via BATTERY_PLUGGED_USB/AC. Timeout = 20 s.
// ---------------------------------------------------------------------------
@Composable
fun ChargingPortTestDialog(
    onResult: (status: String) -> Unit,
    onDismiss: () -> Unit,
    /** Whether this test offers the answer `unavailable` (button text: `manual_mark_unavailable`): `ManualUnavailableOffer.isOffered(testId)`, decided by the row. */
    offerUnavailable: Boolean
) {
    DetectOnBatteryState(
        titleRes = R.string.manual_charging_title,
        waitingRes = R.string.manual_charging_waiting,
        detectedRes = R.string.manual_charging_detected,
        instructionsRes = R.string.manual_charging_instructions,
        timeoutMs = 20_000L,
        onResult = onResult,
        onDismiss = onDismiss,
        offerUnavailable = offerUnavailable
    ) { plugged ->
        plugged == BatteryManager.BATTERY_PLUGGED_USB ||
        plugged == BatteryManager.BATTERY_PLUGGED_AC
    }
}

// ---------------------------------------------------------------------------
// M-13 — OTG: watch UsbManager.ACTION_USB_DEVICE_ATTACHED or poll device list.
// ---------------------------------------------------------------------------
@Composable
fun OtgTestDialog(
    onResult: (status: String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val usb = remember { context.getSystemService(Context.USB_SERVICE) as UsbManager }
    val timeoutMs = 20_000L
    var remainingSec by remember { mutableStateOf((timeoutMs / 1000).toInt()) }
    var detected by remember { mutableStateOf(usb.deviceList.isNotEmpty()) }

    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                if (i?.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) detected = true
            }
        }
        val filter = IntentFilter(UsbManager.ACTION_USB_DEVICE_ATTACHED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        onDispose { try { context.unregisterReceiver(receiver) } catch (_: Exception) {} }
    }

    LaunchedEffect(detected) {
        if (detected) onResult("pass")
    }

    LaunchedEffect(Unit) {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeoutMs && !detected) {
            delay(400)
            if (usb.deviceList.isNotEmpty()) { detected = true; break }
            remainingSec = ((timeoutMs - (System.currentTimeMillis() - start)) / 1000).toInt().coerceAtLeast(0)
        }
        if (!detected) { /* leave dialog open for user choice */ }
    }

    AlertDialog(
        containerColor = CardDark,
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.manual_otg_title), color = TextPrimary) },
        text = {
            Column {
                Text(
                    if (detected) stringResource(R.string.manual_otg_detected)
                    else stringResource(R.string.manual_otg_instructions),
                    color = if (detected) StatusPass else TextSecondary
                )
                Spacer(Modifier.height(8.dp))
                if (!detected) {
                    Text(
                        NumberFormat.toLatin(
                            stringResource(R.string.manual_remaining_sec_fmt, remainingSec)
                        ),
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = 1f - (remainingSec.toFloat() / (timeoutMs / 1000)),
                        modifier = Modifier.fillMaxWidth(),
                        color = NeuralGreen
                    )
                }
            }
        },
        confirmButton = {
            if (detected) {
                TextButton(onClick = { onResult("pass") }) {
                    Text(stringResource(R.string.manual_works), color = StatusPass)
                }
            } else {
                TextButton(onClick = { onResult("fail") }) {
                    Text(stringResource(R.string.manual_not_works), color = StatusFail)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = { onResult(ManualStatuses.UNAVAILABLE) }) {
                Text(stringResource(R.string.manual_mark_unavailable), color = TextSecondary)
            }
        }
    )
}

// ---------------------------------------------------------------------------
// M-14 — Wireless charging: BATTERY_PLUGGED_WIRELESS in BatteryManager.
// ---------------------------------------------------------------------------
@Composable
fun WirelessChargingTestDialog(
    onResult: (status: String) -> Unit,
    onDismiss: () -> Unit,
    /** Whether this test offers the answer `unavailable` (button text: `manual_mark_unavailable`): `ManualUnavailableOffer.isOffered(testId)`, decided by the row. */
    offerUnavailable: Boolean
) {
    DetectOnBatteryState(
        titleRes = R.string.manual_wireless_title,
        waitingRes = R.string.manual_wireless_waiting,
        detectedRes = R.string.manual_wireless_detected,
        instructionsRes = R.string.manual_wireless_instructions,
        timeoutMs = 25_000L,
        onResult = onResult,
        onDismiss = onDismiss,
        offerUnavailable = offerUnavailable
    ) { plugged ->
        plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS
    }
}

// ---------------------------------------------------------------------------
// M-15 — SD card: look for a removable StorageVolume in mounted state.
// ---------------------------------------------------------------------------
@Composable
fun SdCardTestDialog(
    onResult: (status: String) -> Unit,
    onDismiss: () -> Unit,
    /** Whether this test offers the answer `unavailable` (button text: `manual_mark_unavailable`): `ManualUnavailableOffer.isOffered(testId)`, decided by the row. */
    offerUnavailable: Boolean
) {
    val context = LocalContext.current
    val timeoutMs = 15_000L
    var remainingSec by remember { mutableStateOf((timeoutMs / 1000).toInt()) }
    var detected by remember { mutableStateOf(false) }

    // StorageVolume.state is API 30+, but getExternalFilesDirs + MEDIA_MOUNTED
    // works from API 21. Secondary external dirs usually point at an SD card
    // (if any) — a non-null entry that's also MEDIA_MOUNTED is a reliable
    // signal without needing StorageManager APIs that differ per OEM.
    fun hasMountedRemovable(): Boolean {
        return try {
            val dirs = ContextCompat.getExternalFilesDirs(context, null)
            for (i in 1 until dirs.size) {
                val dir = dirs[i] ?: continue
                if (Environment.getExternalStorageState(dir) == Environment.MEDIA_MOUNTED) {
                    return true
                }
            }
            false
        } catch (_: Exception) { false }
    }

    LaunchedEffect(Unit) {
        detected = hasMountedRemovable()
        val start = System.currentTimeMillis()
        while (!detected && System.currentTimeMillis() - start < timeoutMs) {
            delay(500)
            if (hasMountedRemovable()) { detected = true; break }
            remainingSec = ((timeoutMs - (System.currentTimeMillis() - start)) / 1000).toInt().coerceAtLeast(0)
        }
        if (detected) onResult("pass")
    }

    AlertDialog(
        containerColor = CardDark,
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.manual_sd_title), color = TextPrimary) },
        text = {
            Column {
                Text(
                    if (detected) stringResource(R.string.manual_sd_detected)
                    else stringResource(R.string.manual_sd_instructions),
                    color = if (detected) StatusPass else TextSecondary
                )
                Spacer(Modifier.height(8.dp))
                if (!detected) {
                    Text(
                        NumberFormat.toLatin(
                            stringResource(R.string.manual_remaining_sec_fmt, remainingSec)
                        ),
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = 1f - (remainingSec.toFloat() / (timeoutMs / 1000)),
                        modifier = Modifier.fillMaxWidth(),
                        color = NeuralGreen
                    )
                }
            }
        },
        confirmButton = {
            if (detected) {
                TextButton(onClick = { onResult("pass") }) {
                    Text(stringResource(R.string.manual_works), color = StatusPass)
                }
            } else {
                TextButton(onClick = { onResult("fail") }) {
                    Text(stringResource(R.string.manual_not_works), color = StatusFail)
                }
            }
        },
        // Offered only where ManualUnavailableOffer says (the one list; the row's line under the chips reads it too).
        dismissButton = if (offerUnavailable) {
            {
                TextButton(onClick = { onResult(ManualStatuses.UNAVAILABLE) }) {
                    Text(stringResource(R.string.manual_mark_unavailable), color = TextSecondary)
                }
            }
        } else null
    )
}

// ---------------------------------------------------------------------------
// M-16 — IR blaster: FEATURE_CONSUMER_IR gate, then transmit a short 38 kHz
// pulse and ask the user whether the target device reacted.
// ---------------------------------------------------------------------------
@Composable
fun IrBlasterTestDialog(
    onResult: (status: String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val hasIr = remember {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_CONSUMER_IR)
    }
    val ir = remember {
        if (hasIr) context.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager
        else null
    }
    var transmitted by remember { mutableStateOf(false) }

    if (!hasIr || ir == null) {
        AlertDialog(
            containerColor = CardDark,
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.manual_ir_title), color = TextPrimary) },
            text = {
                Text(stringResource(R.string.manual_ir_not_available), color = TextSecondary)
            },
            confirmButton = {
                TextButton(onClick = { onResult(ManualStatuses.UNAVAILABLE) }) {
                    Text(stringResource(R.string.manual_mark_unavailable), color = TextSecondary)
                }
            }
        )
        return
    }

    AlertDialog(
        containerColor = CardDark,
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.manual_ir_title), color = TextPrimary) },
        text = {
            Column {
                Text(
                    if (transmitted) stringResource(R.string.manual_ir_question)
                    else stringResource(R.string.manual_ir_transmit),
                    color = TextSecondary
                )
                if (!transmitted) {
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            try {
                                // NEC-style header repeated a few times: visible to
                                // any IR receiver as a burst of 38 kHz modulation.
                                val pattern = intArrayOf(
                                    9000, 4500, 560, 560, 560, 560, 560, 560,
                                    560, 560, 560, 560, 560, 560, 560, 560,
                                    560, 40000
                                )
                                ir.transmit(38000, pattern)
                                transmitted = true
                            } catch (_: Exception) {
                                onResult("fail")
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeuralGreen.copy(alpha = 0.2f),
                            contentColor = NeuralGreen
                        )
                    ) {
                        Text(stringResource(R.string.manual_ir_send_btn))
                    }
                }
            }
        },
        confirmButton = {
            if (transmitted) {
                TextButton(onClick = { onResult("pass") }) {
                    Text(stringResource(R.string.manual_works), color = StatusPass)
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.btn_cancel), color = TextSecondary)
                }
            }
        },
        dismissButton = {
            if (transmitted) {
                TextButton(onClick = { onResult("fail") }) {
                    Text(stringResource(R.string.manual_not_works), color = StatusFail)
                }
            }
        }
    )
}

// ---------------------------------------------------------------------------
// Shared helper for tests that auto-pass when a particular BatteryManager
// "plugged" state is reached (M-12 wired charging, M-14 wireless charging).
//
// Polls the sticky ACTION_BATTERY_CHANGED intent every 400 ms. Receiver-based
// approaches are flaky because ACTION_POWER_CONNECTED doesn't include the
// plug-source code, and the stream can drop broadcasts while a Dialog is up.
// ---------------------------------------------------------------------------
@Composable
private fun DetectOnBatteryState(
    titleRes: Int,
    waitingRes: Int,
    detectedRes: Int,
    instructionsRes: Int,
    timeoutMs: Long,
    onResult: (status: String) -> Unit,
    onDismiss: () -> Unit,
    offerUnavailable: Boolean,
    matches: (plugged: Int) -> Boolean
) {
    val context = LocalContext.current
    var remainingSec by remember { mutableStateOf((timeoutMs / 1000).toInt()) }
    var detected by remember { mutableStateOf(false) }

    fun currentPlugged(): Int {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        return intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
    }

    LaunchedEffect(Unit) {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeoutMs && !detected) {
            if (matches(currentPlugged())) { detected = true; break }
            delay(400)
            remainingSec = ((timeoutMs - (System.currentTimeMillis() - start)) / 1000).toInt().coerceAtLeast(0)
        }
        if (detected) onResult("pass")
    }

    AlertDialog(
        containerColor = CardDark,
        onDismissRequest = onDismiss,
        title = { Text(stringResource(titleRes), color = TextPrimary) },
        text = {
            Column {
                Text(
                    if (detected) stringResource(detectedRes)
                    else stringResource(instructionsRes),
                    color = if (detected) StatusPass else TextSecondary
                )
                Spacer(Modifier.height(8.dp))
                if (!detected) {
                    Text(stringResource(waitingRes), color = TextSecondary)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        NumberFormat.toLatin(
                            stringResource(R.string.manual_remaining_sec_fmt, remainingSec)
                        ),
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = 1f - (remainingSec.toFloat() / (timeoutMs / 1000)),
                        modifier = Modifier.fillMaxWidth(),
                        color = NeuralGreen
                    )
                }
            }
        },
        confirmButton = {
            if (detected) {
                TextButton(onClick = { onResult("pass") }) {
                    Text(stringResource(R.string.manual_works), color = StatusPass)
                }
            } else {
                TextButton(onClick = { onResult("fail") }) {
                    Text(stringResource(R.string.manual_not_works), color = StatusFail)
                }
            }
        },
        // Offered only where ManualUnavailableOffer says (the one list; the row's line under the chips reads it too).
        dismissButton = if (offerUnavailable) {
            {
                TextButton(onClick = { onResult(ManualStatuses.UNAVAILABLE) }) {
                    Text(stringResource(R.string.manual_mark_unavailable), color = TextSecondary)
                }
            }
        } else null
    )
}

// ---------------------------------------------------------------------------
// AudioTrack-based 1 kHz tone. Earpiece path flips the audio mode to
// MODE_IN_COMMUNICATION + speakerphone=false so USAGE_VOICE_COMMUNICATION
// routes to the handset speaker; caller is responsible for restoring state.
// ---------------------------------------------------------------------------
private fun playTone(context: Context, useEarpiece: Boolean): AudioTrack {
    val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    if (useEarpiece) {
        am.mode = AudioManager.MODE_IN_COMMUNICATION
        am.isSpeakerphoneOn = false
    }
    val sampleRate = 44100
    val durationMs = 2000
    val numSamples = sampleRate * durationMs / 1000
    val samples = ShortArray(numSamples)
    for (i in 0 until numSamples) {
        val angle = 2.0 * PI * i * 1000.0 / sampleRate
        samples[i] = (sin(angle) * Short.MAX_VALUE * 0.4).toInt().toShort()
    }
    val bufferBytes = samples.size * 2
    val usage = if (useEarpiece) AudioAttributes.USAGE_VOICE_COMMUNICATION
                else AudioAttributes.USAGE_MEDIA
    val track = AudioTrack.Builder()
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(usage)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
        )
        .setAudioFormat(
            AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()
        )
        .setBufferSizeInBytes(bufferBytes)
        .setTransferMode(AudioTrack.MODE_STATIC)
        .build()
    track.write(samples, 0, samples.size)
    track.play()
    return track
}

// ===========================================================================
// M-17 / M-18 — Camera live preview.
//
// Opens CameraX with the requested lens (BACK / FRONT) and shows the preview
// inline so the user can judge focus, exposure, lens scratches, etc. with
// their own eyes — exactly the kind of perception check the automatic
// CM-01..CM-07 probes can't make. Permission is requested inline; if denied
// we surface the localised "permission required" message and let the user
// pick "lم أفحص" without crashing.
//
// Lifecycle: ProcessCameraProvider.bindToLifecycle ties the camera to the
// LocalLifecycleOwner, so the camera stops automatically when the dialog is
// dismissed (no explicit unbindAll() call needed in onDispose, but we do it
// anyway as a belt-and-braces because some OEMs leak the surface otherwise).
// ===========================================================================
@Composable
fun CameraPreviewTestDialog(
    useFront: Boolean,
    onResult: (status: String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var permGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED
        )
    }
    var permRequested by remember { mutableStateOf(false) }
    var openFailed by remember { mutableStateOf(false) }

    val permLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> permGranted = granted }

    LaunchedEffect(Unit) {
        if (!permGranted && !permRequested) {
            permRequested = true
            permLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val titleResId = if (useFront)
        R.string.manual_camera_preview_title_front
    else
        R.string.manual_camera_preview_title_rear

    AlertDialog(
        containerColor = CardDark,
        onDismissRequest = onDismiss,
        title = { Text(stringResource(titleResId), color = TextPrimary) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                when {
                    !permGranted && permRequested -> {
                        Text(
                            stringResource(R.string.manual_camera_permission_required),
                            color = TextSecondary
                        )
                    }
                    openFailed -> {
                        Text(
                            stringResource(R.string.manual_camera_open_failed),
                            color = StatusFail
                        )
                    }
                    permGranted -> {
                        // PreviewView is a SurfaceView-backed widget — embed via
                        // AndroidView so CameraX can render straight to its
                        // SurfaceProvider. Aspect ratio fixed at 4:3 to keep the
                        // preview inside the AlertDialog without making it the
                        // tallest element on screen.
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(3f / 4f)
                                .background(Color.Black, RoundedCornerShape(12.dp))
                        ) {
                            AndroidView(
                                modifier = Modifier.fillMaxSize(),
                                factory = { ctx ->
                                    PreviewView(ctx).apply {
                                        scaleType = PreviewView.ScaleType.FILL_CENTER
                                    }
                                },
                                update = { previewView ->
                                    val providerFuture =
                                        ProcessCameraProvider.getInstance(context)
                                    providerFuture.addListener({
                                        try {
                                            val cameraProvider = providerFuture.get()
                                            val preview = Preview.Builder().build().also {
                                                it.setSurfaceProvider(previewView.surfaceProvider)
                                            }
                                            val selector = if (useFront)
                                                CameraSelector.DEFAULT_FRONT_CAMERA
                                            else
                                                CameraSelector.DEFAULT_BACK_CAMERA
                                            cameraProvider.unbindAll()
                                            cameraProvider.bindToLifecycle(
                                                lifecycleOwner, selector, preview
                                            )
                                        } catch (t: Throwable) {
                                            Log.w(
                                                "NeuralCheck",
                                                "CameraX bind failed: ${t.message}"
                                            )
                                            openFailed = true
                                        }
                                    }, ContextCompat.getMainExecutor(context))
                                }
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            stringResource(R.string.manual_camera_preview_hint),
                            color = TextSecondary
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = permGranted && !openFailed,
                onClick = { onResult("pass") }
            ) { Text(stringResource(R.string.manual_camera_clear), color = StatusPass) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onResult("not_tested") }) {
                    Text(stringResource(R.string.manual_not_tested_btn), color = TextSecondary)
                }
                TextButton(
                    enabled = permGranted && !openFailed,
                    onClick = { onResult("fail") }
                ) { Text(stringResource(R.string.manual_camera_blurry), color = StatusFail) }
            }
        }
    )

    // Always release the camera when the dialog leaves the composition so the
    // next time it opens we get a clean PreviewView ↔ provider binding.
    DisposableEffect(Unit) {
        onDispose {
            try {
                val provider = ProcessCameraProvider.getInstance(context).get()
                provider.unbindAll()
            } catch (_: Throwable) { /* best-effort */ }
        }
    }
}

// ===========================================================================
// M-19 — Flash / torch.
//
// Uses CameraManager.setTorchMode against the camera ID that exposes
// FLASH_INFO_AVAILABLE = true. Hardcoding "0" (as the spec suggests) is
// brittle: many devices (and the Pixel/Samsung emulators) have rear cameras
// at IDs other than 0, or expose multiple sub-cameras with separate flash
// units. Discovery + iteration is one extra loop and survives them all.
//
// We DON'T release the camera with closeCamera; setTorchMode does NOT open
// a CameraDevice — it asks the framework to flip the flash unit directly,
// so other apps can still open the camera while torch is on.
// ===========================================================================
@Composable
fun FlashTorchTestDialog(
    onResult: (status: String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val cameraManager = remember {
        context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
    }
    // Pick the first camera ID that actually has a flash unit. On devices
    // with no flash this stays null and we surface "unsupported".
    val flashCameraId: String? = remember(cameraManager) {
        try {
            cameraManager?.cameraIdList?.firstOrNull { id ->
                val ch = cameraManager.getCameraCharacteristics(id)
                ch.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        } catch (t: Throwable) {
            Log.w("NeuralCheck", "FlashTorch: cameraIdList failed: ${t.message}")
            null
        }
    }

    var torchOn by remember { mutableStateOf(false) }
    var openFailed by remember { mutableStateOf(false) }
    val unsupported = flashCameraId == null

    fun setTorch(on: Boolean) {
        val id = flashCameraId ?: return
        val mgr = cameraManager ?: return
        try {
            mgr.setTorchMode(id, on)
            torchOn = on
            openFailed = false
        } catch (t: Throwable) {
            // CameraAccessException with reason MAX_CAMERAS_IN_USE if the
            // user has another camera-using app foregrounded; surface the
            // localised message rather than crash.
            Log.w("NeuralCheck", "setTorchMode($on) failed: ${t.message}")
            openFailed = true
        }
    }

    DisposableEffect(Unit) {
        onDispose { if (torchOn) runCatching { setTorch(false) } }
    }

    AlertDialog(
        containerColor = CardDark,
        onDismissRequest = {
            if (torchOn) runCatching { setTorch(false) }
            onDismiss()
        },
        title = { Text(stringResource(R.string.manual_flash_title), color = TextPrimary) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                when {
                    unsupported -> Text(
                        stringResource(R.string.manual_flash_unsupported),
                        color = TextSecondary
                    )
                    openFailed -> Text(
                        stringResource(R.string.manual_flash_open_failed),
                        color = StatusFail
                    )
                    else -> Text(
                        stringResource(R.string.manual_flash_instruction),
                        color = TextSecondary
                    )
                }
                if (!unsupported) {
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { setTorch(!torchOn) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (torchOn) StatusPass else NeuralGreen.copy(alpha = 0.2f),
                            contentColor = if (torchOn) DeepSpace else NeuralGreen
                        )
                    ) {
                        Text(
                            stringResource(
                                if (torchOn) R.string.manual_flash_torch_off
                                else R.string.manual_flash_torch_on
                            )
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !unsupported,
                onClick = {
                    if (torchOn) runCatching { setTorch(false) }
                    onResult("pass")
                }
            ) { Text(stringResource(R.string.manual_works), color = StatusPass) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = {
                    if (torchOn) runCatching { setTorch(false) }
                    onResult("not_tested")
                }) {
                    Text(stringResource(R.string.manual_not_tested_btn), color = TextSecondary)
                }
                TextButton(
                    enabled = !unsupported,
                    onClick = {
                        if (torchOn) runCatching { setTorch(false) }
                        onResult("fail")
                    }
                ) { Text(stringResource(R.string.manual_not_works), color = StatusFail) }
            }
        }
    )
}

// ===========================================================================
// M-20 / M-21 — BiometricPrompt (fingerprint or face).
//
// Why a single composable: BiometricPrompt is the canonical Android
// abstraction for any enrolled biometric — it routes to fingerprint, face,
// or iris automatically based on what's available + enrolled. The two
// callers differ only in:
//   - Authenticator strength (fingerprint = STRONG; face = STRONG | WEAK
//     because face is WEAK on most non-Pixel devices)
//   - The "no template enrolled" copy (fingerprint vs face)
//   - The fallback when the device has no face hardware at all
// All three are parameters on this single function. Authentication outcome
// → result chip: success = pass, hard failure = fail, cancel = not_tested.
// ===========================================================================
/**
 * M-21 face recognition — question-only. Face Unlock can only be invoked
 * by the system from the lock screen, so we can't legitimately probe it
 * from inside an open app session. The dialog asks the user to recall
 * whether face unlock works on their device and pick a chip. Three
 * outcomes: yes / no / not-tested. Pre-checks for hardware support so a
 * device with no face authenticator surfaces the localised "غير مدعوم"
 * copy and pre-selects "not tested" as the default chip.
 */
@Composable
private fun FaceRecognitionQuestionDialog(
    onResult: (status: String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val faceFeatureAvailable = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_FACE)
        } else true
    }
    val biometricManager = remember { BiometricManager.from(context) }
    // BIOMETRIC_WEAK is what most OEM face implementations report at; we
    // include STRONG so a (rare) face-class-3 sensor isn't excluded.
    val canAuth = remember {
        biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.BIOMETRIC_WEAK
        )
    }

    val statusMsgResId: Int? = when {
        !faceFeatureAvailable -> R.string.manual_biometric_face_unsupported
        canAuth == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
            R.string.manual_biometric_no_face_enrolled
        else -> null
    }

    AlertDialog(
        containerColor = CardDark,
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(R.string.manual_biometric_face_title),
                color = TextPrimary
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (statusMsgResId != null) {
                    Text(stringResource(statusMsgResId), color = TextSecondary)
                } else {
                    Text(
                        stringResource(R.string.manual_face_question),
                        color = TextSecondary
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onResult("pass") }) {
                Text(stringResource(R.string.manual_works), color = StatusPass)
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onResult("not_tested") }) {
                    Text(stringResource(R.string.manual_not_tested_btn), color = TextSecondary)
                }
                TextButton(onClick = { onResult("fail") }) {
                    Text(stringResource(R.string.manual_not_works), color = StatusFail)
                }
            }
        }
    )
}

@Composable
fun BiometricTestDialog(
    forFace: Boolean,
    onResult: (status: String) -> Unit,
    onDismiss: () -> Unit
) {
    // M-21 face recognition is a yes/no question, NOT an interactive probe.
    // BiometricPrompt's face path is only invoked by the platform when the
    // device is at the lock screen — we cannot legitimately exercise face
    // unlock from inside an open app session, so the previous "اختبر الآن"
    // button just opened a prompt the user couldn't actually authenticate
    // against. Strip that path entirely for face and ask the user to judge
    // it themselves. Fingerprint (M-20) keeps the interactive flow because
    // the BiometricPrompt fingerprint path DOES work in-app.
    if (forFace) {
        FaceRecognitionQuestionDialog(onResult = onResult, onDismiss = onDismiss)
        return
    }

    val context = LocalContext.current
    // BiometricPrompt requires a FragmentActivity (it attaches a Fragment to
    // host the system prompt). MainActivity was switched to FragmentActivity
    // for exactly this reason. If we're somehow rendered outside a
    // FragmentActivity (host swap during a config change?), gracefully fail
    // open with "could not run" rather than crash.
    val activity = remember(context) { context.findFragmentActivity() }

    val biometricManager = remember { BiometricManager.from(context) }
    // For fingerprint we ask for STRONG (Class 3 — typically fingerprint).
    // For face we ask for STRONG OR WEAK because most OEM face sensors
    // (Samsung Face Recognition, OnePlus FaceUnlock) are Class 2 / WEAK.
    val authenticators = if (forFace)
        BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.BIOMETRIC_WEAK
    else
        BiometricManager.Authenticators.BIOMETRIC_STRONG

    val canAuth = remember(authenticators) {
        biometricManager.canAuthenticate(authenticators)
    }
    val faceFeatureAvailable = remember {
        if (forFace) {
            // Face hardware is reported via PackageManager.FEATURE_FACE on
            // API 29+. On older devices we have no clean way to detect face
            // separately from fingerprint, so we let BiometricManager's
            // canAuthenticate be the source of truth.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.packageManager.hasSystemFeature(PackageManager.FEATURE_FACE)
            } else true
        } else true
    }

    var lastError by remember { mutableStateOf<String?>(null) }
    var lastErrorIsFatal by remember { mutableStateOf(false) }

    fun startPrompt() {
        val act = activity ?: run {
            lastError = context.getString(R.string.manual_biometric_failed)
            lastErrorIsFatal = true
            return
        }
        val executor = ContextCompat.getMainExecutor(context)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(
                result: BiometricPrompt.AuthenticationResult
            ) {
                onResult("pass")
            }
            override fun onAuthenticationFailed() {
                // "Failed" here = sensor saw a finger/face but didn't match.
                // Don't auto-dismiss — let the user retry or pick "not works".
                lastError = context.getString(R.string.manual_biometric_failed)
                lastErrorIsFatal = false
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                // User-cancel / negative-button → treat as "not_tested" so a
                // tap-out doesn't get logged as a real fail.
                if (errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                    errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                    errorCode == BiometricPrompt.ERROR_CANCELED) {
                    return
                }
                lastError = errString.toString()
                lastErrorIsFatal = true
            }
        }

        try {
            val prompt = BiometricPrompt(act, executor, callback)
            val titleResId = if (forFace)
                R.string.manual_biometric_face_title
            else
                R.string.manual_biometric_fp_title
            val subtitleResId = if (forFace)
                R.string.manual_biometric_face_subtitle
            else
                R.string.manual_biometric_fp_subtitle
            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle(context.getString(titleResId))
                .setSubtitle(context.getString(subtitleResId))
                .setNegativeButtonText(context.getString(R.string.manual_biometric_cancel))
                .setAllowedAuthenticators(authenticators)
                .build()
            prompt.authenticate(info)
        } catch (t: Throwable) {
            Log.w("NeuralCheck", "BiometricPrompt failed to start: ${t.message}")
            lastError = context.getString(R.string.manual_biometric_failed)
            lastErrorIsFatal = true
        }
    }

    val statusMsgResId: Int? = when {
        forFace && !faceFeatureAvailable -> R.string.manual_biometric_face_unsupported
        canAuth == BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE ||
                canAuth == BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE ->
            R.string.manual_biometric_no_hardware
        canAuth == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
            if (forFace) R.string.manual_biometric_no_face_enrolled
            else R.string.manual_biometric_no_fp_enrolled
        else -> null
    }
    val canRunPrompt = canAuth == BiometricManager.BIOMETRIC_SUCCESS &&
            (!forFace || faceFeatureAvailable)

    AlertDialog(
        containerColor = CardDark,
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (forFace) R.string.manual_biometric_face_title
                    else R.string.manual_biometric_fp_title
                ),
                color = TextPrimary
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                when {
                    statusMsgResId != null -> Text(
                        stringResource(statusMsgResId),
                        color = TextSecondary
                    )
                    lastError != null -> Text(
                        lastError ?: "",
                        color = if (lastErrorIsFatal) StatusFail else TextSecondary
                    )
                    else -> Text(
                        stringResource(
                            if (forFace) R.string.manual_biometric_face_subtitle
                            else R.string.manual_biometric_fp_subtitle
                        ),
                        color = TextSecondary
                    )
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { startPrompt() },
                    enabled = canRunPrompt,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeuralGreen.copy(alpha = 0.2f),
                        contentColor = NeuralGreen
                    )
                ) {
                    Text(stringResource(R.string.manual_biometric_try_btn))
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canRunPrompt,
                onClick = { onResult("pass") }
            ) { Text(stringResource(R.string.manual_works), color = StatusPass) }
        },
        dismissButton = {
            Row {
                // Default chip when the hardware/enrollment is missing — the
                // technician needs a way to advance the form even when the
                // test physically can't be run.
                TextButton(onClick = { onResult("not_tested") }) {
                    Text(stringResource(R.string.manual_not_tested_btn), color = TextSecondary)
                }
                TextButton(
                    enabled = canRunPrompt,
                    onClick = { onResult("fail") }
                ) { Text(stringResource(R.string.manual_not_works), color = StatusFail) }
            }
        }
    )
}

// ===========================================================================
// M-22 — Vibration.
//
// Fires a 1 s one-shot vibration so the user can feel whether the haptic
// motor still works. Using VibratorManager on API 31+ (the per-Vibrator
// API became the recommended path) and the legacy VIBRATOR_SERVICE on
// 30 and below.
// ===========================================================================
@Composable
fun VibrationTestDialog(
    onResult: (status: String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val vibrator = remember { context.resolveVibrator() }
    val unsupported = vibrator == null || vibrator.hasVibrator().not()
    var openFailed by remember { mutableStateOf(false) }

    fun fire() {
        val v = vibrator ?: return
        try {
            v.vibrate(VibrationEffect.createOneShot(1000, VibrationEffect.DEFAULT_AMPLITUDE))
            openFailed = false
        } catch (t: Throwable) {
            Log.w("NeuralCheck", "vibrate() failed: ${t.message}")
            openFailed = true
        }
    }

    AlertDialog(
        containerColor = CardDark,
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.manual_vibration_title), color = TextPrimary) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                when {
                    unsupported -> Text(
                        stringResource(R.string.manual_vibration_unsupported),
                        color = TextSecondary
                    )
                    openFailed -> Text(
                        stringResource(R.string.manual_vibration_open_failed),
                        color = StatusFail
                    )
                    else -> Text(
                        stringResource(R.string.manual_vibration_instruction),
                        color = TextSecondary
                    )
                }
                if (!unsupported) {
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { fire() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeuralGreen.copy(alpha = 0.2f),
                            contentColor = NeuralGreen
                        )
                    ) {
                        Text(stringResource(R.string.manual_vibration_try_btn))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !unsupported,
                onClick = { onResult("pass") }
            ) { Text(stringResource(R.string.manual_works), color = StatusPass) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onResult("not_tested") }) {
                    Text(stringResource(R.string.manual_not_tested_btn), color = TextSecondary)
                }
                TextButton(
                    enabled = !unsupported,
                    onClick = { onResult("fail") }
                ) { Text(stringResource(R.string.manual_not_works), color = StatusFail) }
            }
        }
    )
}

// ---------------------------------------------------------------------------
// Internal helpers shared by the new M-17..M-22 dialogs above.
// ---------------------------------------------------------------------------

/**
 * Resolve the appropriate Vibrator handle for the running platform.
 *
 * Android 12+ deprecated VIBRATOR_SERVICE in favour of VibratorManager, so
 * we use VibratorManager.defaultVibrator on S+ and fall back to the legacy
 * service on R and below. Returns null only if the device has no vibrator
 * subsystem at all (rare — usually only on automotive form factors).
 */
private fun Context.resolveVibrator(): Vibrator? = try {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)
            ?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
} catch (t: Throwable) {
    Log.w("NeuralCheck", "resolveVibrator failed: ${t.message}")
    null
}

/**
 * Walk the ContextWrapper chain to the FragmentActivity that hosts this
 * Compose tree. Required by [BiometricTestDialog] because BiometricPrompt
 * (1.1.0+) demands a FragmentActivity to attach its internal Fragment to.
 *
 * Returns null if the top-most Activity is not a FragmentActivity (e.g.,
 * if MainActivity is ever switched away from FragmentActivity, or this is
 * rendered inside a foreign hosting Activity in the future).
 */
private fun Context.findFragmentActivity(): FragmentActivity? {
    var ctx: Context? = this
    while (ctx is android.content.ContextWrapper) {
        if (ctx is FragmentActivity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
