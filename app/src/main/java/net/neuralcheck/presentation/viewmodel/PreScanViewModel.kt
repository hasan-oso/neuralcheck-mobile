package net.neuralcheck.presentation.viewmodel

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.neuralcheck.BuildConfig
import net.neuralcheck.R
import net.neuralcheck.data.auth.AuthManager
import net.neuralcheck.data.remote.FirebaseAnalysisService
import net.neuralcheck.data.remote.ScanSessionHolder
import net.neuralcheck.data.remote.SubscriptionRefreshBus
import net.neuralcheck.presentation.screen.ConditionLevel
import net.neuralcheck.presentation.screen.PreScanCondition
import net.neuralcheck.util.EvalCardLog
import net.neuralcheck.util.LocaleHelper
import net.neuralcheck.util.LogRedaction
import net.neuralcheck.util.NumberFormat
import javax.inject.Inject

@HiltViewModel
class PreScanViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authManager: AuthManager,
    private val firebaseService: FirebaseAnalysisService,
    private val sessionHolder: ScanSessionHolder,
    private val subscriptionRefreshBus: SubscriptionRefreshBus,
    private val warningsHolder: PreScanWarningsHolder
) : ViewModel() {

    companion object {
        // Phase 1.7.D — pre-scan warning thresholds.
        private const val PREFS_NAME = "neuralcheck_prescan"
        private const val PREF_LAST_UNPLUG_MS = "lastChargerUnplugTimestampMs"
        private const val HOT_BATTERY_THRESHOLD_C = 34.0f
        // Phase 1.11 follow-up (2026-05-14): above this point the dialog
        // becomes blocking — the user must explicitly confirm "do scan
        // anyway" to override. Empirical anchor: S21 Ultra started at
        // 36.8 °C and produced thermally-capped results (CPU governor
        // throttled the workload before saturation, contaminating every
        // downstream metric). 35 °C is the inflection above which battery
        // ≈ ambient + workload-induced offset is implausibly hot for a
        // pre-scan idle reading.
        private const val HOT_BATTERY_BLOCKING_THRESHOLD_C = 35.0f
        private const val RECENTLY_UNPLUGGED_WINDOW_MS = 15L * 60L * 1000L
    }

    /**
     * Phase 1.7.D — Result of [evaluatePreScanWarnings]. Drives the UX
     * confirmation dialog before scan start.
     *
     * `chargerPlugged` is a hard block (no proceed button). Hot battery
     * and recent unplug are soft warnings — user can proceed but the
     * stable-id strings land in [PreScanWarningsHolder] and ride the
     * footer all the way to the AI.
     */
    sealed class PreScanWarning {
        data object ChargerPlugged : PreScanWarning()
        /** [blocking] = true above [HOT_BATTERY_BLOCKING_THRESHOLD_C] — the
         *  PreScanScreen renders a confirm-to-override dialog whose
         *  primary button is "wait and retry" (closes without scanning)
         *  and whose secondary button is "scan anyway" (proceeds). */
        data class HotBattery(val tempC: Float, val blocking: Boolean = false) : PreScanWarning()
        data class RecentlyUnplugged(val minutesAgo: Long) : PreScanWarning()
    }
    private val _conditions = MutableStateFlow<List<PreScanCondition>>(emptyList())
    val conditions: StateFlow<List<PreScanCondition>> = _conditions

    /**
     * Subscription gate for the launch flavor. The sealed states are:
     *  - [Idle]: no check has been requested yet — default on entry.
     *  - [Checking]: the network call to `checkSubscription` is in flight.
     *  - [Allowed]: either the user has scans remaining OR we are in a build
     *    where [BuildConfig.REQUIRE_SUBSCRIPTION] is false, so the gate is
     *    bypassed entirely.
     *  - [NeedsLogin]: no signed-in user — the screen should redirect to
     *    `login` instead of starting a scan.
     *  - [NoScansRemaining]: user is signed in but out of credit; screen
     *    should surface the "buy a subscription" message.
     *
     * Failures of the Cloud Function itself let the scan go - as [AllowedUnverified], NOT as [Allowed] (A-SUB wave 1, item 5; user decision 5 «(أ)»; rule 22): the server-side
     * `analyzeDevice` callable re-checks credit and will refuse the actual analysis, so a transient check failure shouldn't block the user from collecting data they'd
     * otherwise be entitled to - but "the check could not be made" is a state of its own, with the failure's class name and a log line, not a silent pass that reads like
     * "the check said yes".
     */
    sealed class AccessState {
        data object Idle : AccessState()
        data object Checking : AccessState()
        data object Allowed : AccessState()

        /**
         * The scan may start, and the check that should have said so FAILED ([reason]). The screen treats it exactly like [Allowed] (no new text: the decision is «بلا نصّ جديد»);
         * what differs is that the state says so and the log prints `scan-gate decision=allowed-unverified` (the round-1 report, section 5 question 3, names the risk that stays: the
         * incident «ب٨» repeats when the check alone fails - and this line is what shows it in a logcat).
         */
        data class AllowedUnverified(val reason: Unverified) : AccessState()
        data object NeedsLogin : AccessState()
        data object NoScansRemaining : AccessState()
    }

    /** Why a scan was allowed without the subscription check's answer. */
    sealed interface Unverified {
        /** `checkSubscription` threw. [errorClass] is the exception's simple class name (no message: rule 36); in a release build R8 may have renamed the class - the log line says what it printed. */
        data class CheckFailed(val errorClass: String) : Unverified
    }

    private val _accessState = MutableStateFlow<AccessState>(AccessState.Idle)
    val accessState: StateFlow<AccessState> = _accessState.asStateFlow()

    /**
     * Free-trial banner state. We classify the user as:
     *   - HasTrial: backend says scansRemaining > 0 AND no paid plan is set
     *     (plan is blank, "free", "trial", or "free_trial"). The banner
     *     reads "you have one free trial scan".
     *   - TrialUsed: scansRemaining == 0 AND plan still empty/trial-like.
     *     Banner reads "trial used — subscribe to continue".
     *   - Hidden: any other state (paid plan, datacollect flavor, error, or
     *     the device holds a session id — nothing is loaded then, see init).
     * Loaded once on init; cheap network round-trip to checkSubscription
     * that the gate already pays anyway.
     */
    sealed class TrialBannerState {
        data object Hidden : TrialBannerState()
        data object HasTrial : TrialBannerState()
        data object TrialUsed : TrialBannerState()
    }

    private val _trialBanner = MutableStateFlow<TrialBannerState>(TrialBannerState.Hidden)
    val trialBanner: StateFlow<TrialBannerState> = _trialBanner.asStateFlow()

    init {
        viewModelScope.launch {
            while (true) {
                _conditions.value = checkConditions()
                delay(2000) // Refresh every 2 seconds
            }
        }
        // Initial trial-banner check + a long-lived collector that re-runs it
        // every time the back-office signals a plan change. The user can stay
        // on this screen while the admin approves their payment and watch
        // the banner flip from "trial used" to Hidden without rebuilding the
        // screen.
        //
        // Order 2026-10-10 (user answer "Hide it in a session"): NOT while the
        // device holds a session id. The shop session is billed to the owner,
        // so the trial banner means nothing on the customer's phone — and the
        // device's anonymous uid has no `users` doc, so the server answers
        // plan "free" / scansRemaining 0 and the banner would read "trial used
        // — subscribe to continue" (R.string.free_trial_used) to a customer
        // who owes nothing. Neither the first check nor the bus collector
        // (and so no checkSubscription call) starts in that case, and the
        // banner stays [TrialBannerState.Hidden]. The session id is read by
        // [activeSessionId], the same read checkAccess() gates on. The holder
        // is filled before the join screen navigates here
        // (JoinSessionViewModel.join / QRScannerViewModel), so it is already
        // set when this view model is created; the check is made once, here.
        if (BuildConfig.REQUIRE_SUBSCRIPTION && authManager.isLoggedIn && activeSessionId() == null) {
            viewModelScope.launch { refreshTrialBanner() }
            viewModelScope.launch {
                subscriptionRefreshBus.events.collect { refreshTrialBanner() }
            }
        }
    }

    /**
     * The session id this device holds, or null when it holds none. The ONE
     * read of [ScanSessionHolder] in this view model: [checkAccess] (the scan
     * gate) and the init block (the trial banner) both go through it, so the
     * two can never disagree about whether the device is in a session.
     * Blank counts as none, exactly as the gate always treated it.
     */
    private fun activeSessionId(): String? = sessionHolder.get()?.takeIf { it.isNotBlank() }

    private suspend fun refreshTrialBanner() {
        _trialBanner.value = try {
            val s = firebaseService.checkSubscription()
            val plan = s.plan.lowercase().trim()
            val isTrialPlan = plan.isBlank() ||
                plan == "free" || plan == "trial" || plan == "free_trial" ||
                plan == "none"
            val nextState = when {
                isTrialPlan && s.scansRemaining > 0 -> TrialBannerState.HasTrial
                isTrialPlan && s.scansRemaining <= 0 -> TrialBannerState.TrialUsed
                else -> TrialBannerState.Hidden
            }
            android.util.Log.d(
                "NeuralCheck.PreScan",
                "refreshTrialBanner: plan='${s.plan}' scansRemaining=${s.scansRemaining} " +
                    "isActive=${s.isActive} → isTrialPlan=$isTrialPlan → state=$nextState"
            )
            nextState
        } catch (t: Throwable) {
            android.util.Log.w(
                "NeuralCheck.PreScan",
                "refreshTrialBanner: checkSubscription failed — keeping banner hidden: ${t.message}"
            )
            TrialBannerState.Hidden
        }
    }

    /**
     * Called when the user confirms "start scan". The gate has three
     * branches in priority order:
     *
     *  1. **Customer-device session path** — when the user reached this
     *     screen via JoinSessionScreen / QRScannerScreen, the resolved
     *     sessionId is sitting in [ScanSessionHolder]. The shop owner has
     *     already paid for the scan; we MUST NOT redirect this user to
     *     the login screen and MUST NOT consume one of THEIR scans by
     *     calling checkSubscription against an anonymous Firebase user.
     *     The server-side analyzeDevice callable validates the session and
     *     bills the owner — so the right thing to do here is bypass every
     *     local gate and go straight to Allowed.
     *
     *  2. **Datacollect flavor** — no auth, no subscription. Always Allowed.
     *
     *  3. **Owner / paid path (launch flavor with no session)** — auth
     *     required, then checkSubscription. This is the original behaviour.
     *
     * Failures of the Cloud Function fall through as Allowed: the server-
     * side analyzeDevice re-checks credit and will refuse the actual
     * analysis, so a transient check failure shouldn't block the user from
     * collecting data they'd otherwise be entitled to.
     */
    fun checkAccess() {
        // 1. Session-code path takes precedence over BuildConfig flags so it
        //    works in both flavors. The holder is non-null only after a
        //    successful joinScanSession (callable or QR scanner).
        val activeSessionId = activeSessionId()
        if (activeSessionId != null) {
            android.util.Log.d(
                "NeuralCheck.PreScan",
                "checkAccess: bypassing auth/subscription — sessionId=${LogRedaction.hashId(activeSessionId)}"
            )
            _accessState.value = AccessState.Allowed
            return
        }

        // 2. Datacollect flavor (or any build with REQUIRE_SUBSCRIPTION=false)
        if (!BuildConfig.REQUIRE_SUBSCRIPTION) {
            _accessState.value = AccessState.Allowed
            return
        }

        // 3. Standard launch path: requires sign-in + scan credit.
        if (!authManager.isLoggedIn) {
            _accessState.value = AccessState.NeedsLogin
            return
        }
        viewModelScope.launch {
            _accessState.value = AccessState.Checking
            _accessState.value = try {
                val status = firebaseService.checkSubscription()
                android.util.Log.d(
                    "NeuralCheck.PreScan",
                    "checkAccess: server says plan='${status.plan}' " +
                        "scansRemaining=${status.scansRemaining} " +
                        "isActive=${status.isActive}"
                )
                // SIMPLE GATE: only the server-reported scans-remaining count
                // matters. We deliberately don't check `plan` here — that
                // would re-introduce the bug where a freshly-approved user
                // (plan flipped from "free" to "single") was still gated as
                // if on the free trial. The server is the single source of
                // truth, and analyzeDevice re-validates regardless.
                if (status.scansRemaining <= 0) AccessState.NoScansRemaining
                else AccessState.Allowed
            } catch (t: Throwable) {
                // Network / Functions error — let the scan proceed and rely
                // on the server-side guard in analyzeDevice. A-SUB wave 1, item 5: but say it was NOT verified - a state of its own and a log line, no new screen text.
                val errorClass = t.javaClass.simpleName
                EvalCardLog.scanGateUnverified(errorClass)
                AccessState.AllowedUnverified(Unverified.CheckFailed(errorClass))
            }
        }
    }

    fun consumeAccessState() {
        _accessState.value = AccessState.Idle
    }

    /**
     * Phase 1.7.D — checked when the user taps "start scan" in the
     * pre-scan UI. Returns the list of warnings that apply; the screen
     * decides whether to block (charger) or soft-warn (hot/recent).
     *
     * Reads `EXTRA_PLUGGED` and `EXTRA_TEMPERATURE` from the sticky
     * `ACTION_BATTERY_CHANGED` intent — same source the existing
     * `checkConditions` uses, so the values are consistent with the
     * coloured indicators already on screen.
     */
    fun evaluatePreScanWarnings(): List<PreScanWarning> {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val tempC = (intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f

        val warnings = mutableListOf<PreScanWarning>()
        if (plugged > 0) {
            warnings += PreScanWarning.ChargerPlugged
        } else {
            if (tempC >= HOT_BATTERY_THRESHOLD_C) {
                warnings += PreScanWarning.HotBattery(
                    tempC = tempC,
                    blocking = tempC > HOT_BATTERY_BLOCKING_THRESHOLD_C
                )
            }
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val lastUnplugMs = prefs.getLong(PREF_LAST_UNPLUG_MS, 0L)
            if (lastUnplugMs > 0L) {
                val sinceMs = System.currentTimeMillis() - lastUnplugMs
                if (sinceMs in 0L until RECENTLY_UNPLUGGED_WINDOW_MS) {
                    warnings += PreScanWarning.RecentlyUnplugged(sinceMs / 60_000L)
                }
            }
        }
        return warnings
    }

    /**
     * Phase 1.7.D — called by the screen when the user accepts (or
     * dismisses) a soft warning and proceeds. Stores the stable-id
     * strings in [PreScanWarningsHolder]; the stress service folds
     * them into TimeSeriesFooter.preScanWarnings.
     */
    fun acceptWarningsAndProceed(warnings: List<PreScanWarning>) {
        val ids = warnings.mapNotNull {
            when (it) {
                is PreScanWarning.HotBattery -> PreScanWarningsHolder.WARN_HOT_BATTERY
                is PreScanWarning.RecentlyUnplugged -> PreScanWarningsHolder.WARN_RECENTLY_UNPLUGGED
                // ChargerPlugged is a hard block, never reaches here —
                // but defended against to keep the UI from wiring the
                // wrong path silently.
                is PreScanWarning.ChargerPlugged -> null
            }
        }
        warningsHolder.set(ids)
    }

    /** Tracks the previous plugged-state across [checkConditions] ticks
     *  so we can write the unplug timestamp on the charging→not edge. */
    @Volatile private var lastPluggedState: Int = -1

    private fun checkConditions(): List<PreScanCondition> {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.let {
            val l = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val s = it.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (l >= 0 && s > 0) (l * 100) / s else -1
        } ?: -1
        val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val tempRaw = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        val tempC = tempRaw / 10f
        val uptimeHours = SystemClock.elapsedRealtime() / 3_600_000f

        // Phase 1.7.D — detect charging→not transition and persist the
        // timestamp. The pre-scan screen polls every 2 s, so this
        // captures the unplug within ~2 s of it happening — accurate
        // enough for the 15-minute warning window.
        val prev = lastPluggedState
        if (prev > 0 && plugged == 0) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putLong(PREF_LAST_UNPLUG_MS, System.currentTimeMillis())
                .apply()
        }
        lastPluggedState = plugged

        val ctx = LocaleHelper.wrap(context)
        val chargerLbl = ctx.getString(
            if (plugged > 0) R.string.prescan_charger_connected_lbl else R.string.prescan_charger_disconnected_lbl
        )
        return listOf(
            PreScanCondition(
                NumberFormat.toLatin(ctx.getString(R.string.prescan_battery_level_fmt, level)),
                // fix/prescan-battery-gate: the bands must agree with the
                // service's hard gate (ScanBatteryWindow — the
                // original field bug was 20/30 bands showing GREEN while the
                // gate refused). RED = the gate WILL refuse: below the window's
                // minimum or, since fieldsVersion 71, above its maximum (colour
                // only — the text for the high side awaits approval). YELLOW =
                // passes but within 10 points of the minimum: a worst-case scan
                // draining ~6.5% from there ends close to SafetyMonitor's 10%
                // abort territory — worth a visual caution before the user commits.
                // (An unknown level, -1 here, still reads RED while the gate lets it
                // through — unchanged; PENDING: what to do with an unknown level.)
                when {
                    level < net.neuralcheck.service.stress.ScanBatteryWindow.MIN_PCT -> ConditionLevel.RED
                    level > net.neuralcheck.service.stress.ScanBatteryWindow.MAX_PCT -> ConditionLevel.RED
                    level < net.neuralcheck.service.stress.ScanBatteryWindow.MIN_PCT +
                        net.neuralcheck.service.stress.ScanBatteryWindow.YELLOW_BAND_POINTS -> ConditionLevel.YELLOW
                    else -> ConditionLevel.GREEN
                }
            ),
            PreScanCondition(
                ctx.getString(R.string.prescan_charger_status_fmt, chargerLbl),
                if (plugged > 0) ConditionLevel.RED else ConditionLevel.GREEN
            ),
            PreScanCondition(
                NumberFormat.toLatin(ctx.getString(R.string.prescan_temperature_fmt, NumberFormat.format(tempC, 1))),
                when {
                    tempC < 15 || tempC > 38 -> ConditionLevel.YELLOW
                    else -> ConditionLevel.GREEN
                }
            ),
            PreScanCondition(
                NumberFormat.toLatin(ctx.getString(R.string.prescan_uptime_fmt, NumberFormat.format(uptimeHours, 1))),
                if (uptimeHours < 1f) ConditionLevel.YELLOW else ConditionLevel.GREEN
            ),
            PreScanCondition(ctx.getString(R.string.prescan_close_apps), ConditionLevel.GREEN),
            PreScanCondition(ctx.getString(R.string.prescan_flat_surface), ConditionLevel.GREEN)
        )
    }
}
