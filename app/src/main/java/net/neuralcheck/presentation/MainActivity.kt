package net.neuralcheck.presentation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.FragmentActivity
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import net.neuralcheck.R
import net.neuralcheck.data.local.prefs.DestructiveMigrationTracker
import net.neuralcheck.presentation.navigation.NeuralCheckNavHost
import net.neuralcheck.presentation.theme.CardDark
import net.neuralcheck.presentation.theme.NeuralCheckTheme
import net.neuralcheck.presentation.theme.NeuralGreen
import net.neuralcheck.presentation.theme.TextPrimary
import net.neuralcheck.presentation.theme.TextSecondary
import net.neuralcheck.util.InAppNotifier
import net.neuralcheck.util.LocaleHelper

// FragmentActivity (instead of ComponentActivity) is required by
// androidx.biometric:BiometricPrompt — the M-20/M-21 dialogs need to attach
// a Fragment internally to host the system biometric UI. FragmentActivity is
// a strict superset of ComponentActivity so this is invisible to the rest of
// the codebase (Hilt, Compose, ActivityResult APIs all keep working).
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    // Single process-wide notifier subscription lives at the Activity root so
    // a snackbar pushed from anywhere in the graph (ViewModels, background
    // callbacks) surfaces regardless of which screen the user is on. The
    // lower-level screens therefore don't need their own SnackbarHost, which
    // keeps the API for each screen focused on its own state.
    @Inject
    lateinit var inAppNotifier: InAppNotifier

    // Order 2026-09-24، القرار ٨٠ البند ١: injected so AppRoot can show the
    // one-time "report log was reset on this device" notice after a
    // fallbackToDestructiveMigration() wipe. See DestructiveMigrationTracker.
    @Inject
    lateinit var destructiveMigrationTracker: DestructiveMigrationTracker

    // POST_NOTIFICATIONS is a Tiramisu+ runtime permission. Without it the
    // foreground-service notification silently never appears on Android 13+
    // (Pixel/S24/etc.), and on some OEM forks the missing notification
    // causes a delayed ANR-style kill of the foreground service that's
    // very hard to diagnose. We request it once at launch — the user can
    // still deny, in which case the scan still works but the progress
    // notification is invisible.
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* result ignored */ }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Phase 1.15 (edge-to-edge fix): opt in explicitly so system-bar insets
        // are dispatched to Compose on every API level. On Android 15+
        // (targetSdk 35) edge-to-edge is forced regardless; calling this makes
        // the behaviour uniform on older devices too, so every screen consumes
        // the same insets (no overlap on new devices, no double-padding on old).
        enableEdgeToEdge()
        maybeRequestNotificationPermission()
        setContent {
            NeuralCheckTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppRoot(
                        inAppNotifier = inAppNotifier,
                        destructiveMigrationTracker = destructiveMigrationTracker
                    )
                }
            }
        }
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            try {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } catch (t: Throwable) {
                android.util.Log.w("NeuralCheck", "POST_NOTIFICATIONS request failed: ${t.message}")
            }
        }
    }
}

@Composable
private fun AppRoot(
    inAppNotifier: InAppNotifier,
    destructiveMigrationTracker: DestructiveMigrationTracker
) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // Collect in-app notifications → surface each as a Material3 snackbar.
    // The resId is resolved here (not at emit time) so locale changes are
    // reflected — a message queued before the user toggled ar↔tr will still
    // render in the currently-active language.
    LaunchedEffect(inAppNotifier) {
        inAppNotifier.events.collect { event ->
            val text = context.getString(event.messageResId)
            snackbarHostState.showSnackbar(
                message = text,
                duration = SnackbarDuration.Short
            )
        }
    }

    // Order 2026-09-24، القرار ٨٠ البند ١: a Snackbar (used above for
    // InAppNotifier) is deliberately NOT used for this — InAppNotifier's
    // SharedFlow has replay = 0, so a push emitted before this Composable's
    // LaunchedEffect subscribes would be silently dropped, and this notice
    // is exactly the kind of thing "الغياب لا يُقرأ قياساً" (order's own
    // القاعدة 22) argues against losing by race. consumePendingAlert() reads
    // straight from the same on-device flag DestructiveMigrationCallback
    // wrote, checked once here rather than pushed through a channel, so
    // there is no subscription-timing window it can fall through.
    //
    // rememberSaveable (not plain remember): consumePendingAlert() clears
    // the on-device flag the instant it returns true, so if we only held
    // "show the dialog" in `remember`, a rotation between that read and the
    // user actually seeing the dialog would drop it silently (Activity
    // recreated, LaunchedEffect reruns, tracker has nothing left to
    // consume). rememberSaveable survives that recreation, so the dialog
    // keeps showing through a rotation; only an actual process death before
    // the user dismisses it can still lose it (accepted — see
    // DestructiveMigrationTracker's own doc comment).
    var showDestructiveMigrationAlert by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(destructiveMigrationTracker) {
        if (destructiveMigrationTracker.consumePendingAlert()) {
            showDestructiveMigrationAlert = true
        }
    }
    if (showDestructiveMigrationAlert) {
        AlertDialog(
            containerColor = CardDark,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            onDismissRequest = { showDestructiveMigrationAlert = false },
            title = { Text(stringResource(R.string.destructive_migration_alert_title)) },
            text = { Text(stringResource(R.string.destructive_migration_alert_body)) },
            confirmButton = {
                TextButton(onClick = { showDestructiveMigrationAlert = false }) {
                    Text(stringResource(R.string.btn_ok))
                }
            }
        )
    }

    Scaffold(
        containerColor = Color.Transparent,
        // Phase 1.15 (edge-to-edge fix): the root Scaffold must NOT consume the
        // system-bar insets — otherwise the per-screen Scaffolds nested under
        // the NavHost (and their NavigationBar/BottomAppBar) receive ZERO bottom
        // inset and render flush over the navigation bar. WindowInsets(0) leaves
        // the full insets for each screen to consume itself.
        contentWindowInsets = WindowInsets(0),
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                // Severity is not carried into SnackbarData (Material3's API
                // doesn't expose custom slots beyond actionLabel). For now
                // every message renders with the neutral branded chrome;
                // when we need per-severity colours we'll wrap the emitted
                // events into a custom SnackbarVisuals subclass.
                Snackbar(
                    snackbarData = data,
                    containerColor = CardDark,
                    contentColor = TextPrimary,
                    actionColor = NeuralGreen
                )
            }
        }
    ) { padding ->
        Surface(
            modifier = Modifier
                .fillMaxSize(),
            color = Color.Transparent
        ) {
            // Intentionally ignoring `padding` — we want the NavHost's screens
            // to own their full-screen chrome (TopAppBar/NavigationBar), same
            // as before the Scaffold was introduced. The SnackbarHost floats
            // above content via its own slot so no layout gap is needed.
            NeuralCheckNavHost(navController)
            @Suppress("UNUSED_EXPRESSION") padding
        }
    }
}
