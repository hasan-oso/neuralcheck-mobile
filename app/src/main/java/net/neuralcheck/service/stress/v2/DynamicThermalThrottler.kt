package net.neuralcheck.service.stress.v2

import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.util.Log
import net.neuralcheck.data.local.files.ThrottlingEvent
import net.neuralcheck.util.StressNative
import net.neuralcheck.util.ThermalFallback
import java.util.Collections
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * V2 master plan §2.7 — dynamic thermal throttler.
 *
 * Background scheduler that polls skin_temp every 2 seconds and records
 * a throttling event when thresholds are crossed. Distinct from
 * [SafetyMonitor] which kills the WHOLE scan.
 *
 * Thresholds (per master plan):
 *   skin > 45°C → RECORD "reduce intensity 20%" (multiply by 0.8)
 *   skin > 48°C → RECORD "reduce intensity 40%" (multiply by 0.6)
 *   skin > 50°C → terminate current phase, advance to next
 *
 * fieldsVersion 65 honesty note: the level-1/2 intensity reduction is
 * RECORDED ONLY — [effectiveIntensity] is computed and stamped into each
 * event's newIntensity, but no stress workload consumes it (every kernel
 * reads the probe's constant intensityFactor). Each [ThrottlingEvent]
 * therefore carries `applied`: false for level 1/2 always, and for
 * level 3 true only when the phase-terminate callback actually fired.
 * The only real load-changing action this class performs is the level-3
 * terminate.
 *
 * Each transition is recorded in [events] so the footer can ship a
 * full timeline of throttling decisions to the AI.
 */
class DynamicThermalThrottler(
    private val context: Context,
    private val initialIntensity: Float
) {
    companion object {
        private const val TAG = "NeuralCheck.V2.Throttler"
        private const val POLL_INTERVAL_MS: Long = 2_000L

        const val SKIN_THRESHOLD_LIGHT_C: Float = 45f
        const val SKIN_THRESHOLD_HEAVY_C: Float = 48f
        const val SKIN_THRESHOLD_TERMINATE_C: Float = 50f

        // Cumulative intensity multipliers applied at each level. Each
        // level resets to the base — they don't compound, because the
        // throttler computes the new factor as base × level_multiplier.
        private const val MULT_LIGHT: Float = 0.8f
        private const val MULT_HEAVY: Float = 0.6f
        private const val MULT_TERMINATE: Float = 0.0f
    }

    private val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "NeuralCheck-V2-Throttler").apply { isDaemon = true }
    }
    private val running = AtomicBoolean(false)
    private val currentPhase = AtomicInteger(0)
    private val currentLevel = AtomicInteger(0)  // 0=normal, 1=light, 2=heavy, 3=terminate
    @Volatile private var phaseTerminateRequest: () -> Unit = {}

    private val eventsList: MutableList<ThrottlingEvent> = Collections.synchronizedList(mutableListOf())

    /** Snapshot of all throttling events captured since [start]. The
     *  caller copies this into TimeSeriesFooter at scan completion. */
    val events: List<ThrottlingEvent> get() = synchronized(eventsList) { eventsList.toList() }

    /** Effective intensity right now: initial × level multiplier. */
    val effectiveIntensity: Float
        get() {
            val mult = when (currentLevel.get()) {
                1 -> MULT_LIGHT
                2 -> MULT_HEAVY
                3 -> MULT_TERMINATE
                else -> 1.0f
            }
            return initialIntensity * mult
        }

    /**
     * Starts the polling loop. [onTerminatePhase] fires when skin_temp
     * crosses [SKIN_THRESHOLD_TERMINATE_C] — the V2 service uses it to
     * cancel the current phase's job and advance to the next phase.
     */
    fun start(onTerminatePhase: () -> Unit) {
        if (running.getAndSet(true)) return
        phaseTerminateRequest = onTerminatePhase
        executor.scheduleAtFixedRate(
            { tick() },
            POLL_INTERVAL_MS,
            POLL_INTERVAL_MS,
            TimeUnit.MILLISECONDS
        )
    }

    fun setCurrentPhase(phaseId: Int) {
        currentPhase.set(phaseId)
        // Reset level on phase change — a hot device that cools between
        // phases gets to recover its full intensity for the next phase.
        currentLevel.set(0)
    }

    /**
     * Read-only access to the current phase id as last set by helper.
     * Used by StressTestService.executePhasesV2 so its phaseDriver can
     * synchronise the sampler with helper.runV2Scan's actual phase
     * progression — independent of any nominal timeline. Bug fix:
     * previously phaseDriver advanced on a fixed 480s timeline while
     * helper ran longer, causing phase 7 (COOLDOWN) to never get
     * samples on three test devices.
     */
    fun getCurrentPhase(): Int = currentPhase.get()

    fun stop() {
        running.set(false)
        executor.shutdownNow()
    }

    /**
     * V2 fix #54: read PowerManager.getCurrentThermalStatus directly.
     * Used as the authoritative termination signal when sysfs skin is
     * blocked. Android docs: 0=NONE, 1=LIGHT, 2=MODERATE, 3=SEVERE,
     * 4=CRITICAL, 5=EMERGENCY, 6=SHUTDOWN. Returns -1 if unavailable.
     */
    private fun readPowerManagerStatus(): Int {
        return try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return -1
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                ?: return -1
            pm.currentThermalStatus
        } catch (t: Throwable) {
            Log.w(TAG, "readPowerManagerStatus failed: ${t.message}")
            -1
        }
    }

    private fun tick() {
        if (!running.get()) return
        try {
            // ThermalFallback.read() guarantees a 4-element array even
            // when sysfs is SELinux-blocked (Samsung One UI / MIUI) by
            // synthesising skin from BatteryManager + PowerManager
            // thermal status. Without this fallback, throttling never
            // engages on those devices because skin always reads -1.
            val zones = ThermalFallback.read(context)
            if (zones.size < StressNative.THERMAL_COUNT) return
            val skin = zones[StressNative.THERMAL_SKIN]
            if (skin <= 0f) return  // sensor unreadable; nothing to do

            // Bug fix #27: check whether skin came from sysfs or was
            // synthesized from PowerManager.thermalStatus. Synthetic
            // values cross our 50°C TERMINATE threshold easily on
            // devices with restricted /sys/class/thermal (e.g. S10 5G
            // API 30 returned synthetic 52°C while battery was 39°C).
            val skinIsSysfs = ThermalFallback.isLastReadFromSysfs(
                StressNative.THERMAL_SKIN
            )

            val newLevel = when {
                skin >= SKIN_THRESHOLD_TERMINATE_C -> 3
                skin >= SKIN_THRESHOLD_HEAVY_C -> 2
                skin >= SKIN_THRESHOLD_LIGHT_C -> 1
                else -> 0
            }
            val previous = currentLevel.getAndSet(newLevel)
            if (newLevel != previous && newLevel > 0) {
                val reason = when (newLevel) {
                    1 -> "skin>${SKIN_THRESHOLD_LIGHT_C}"
                    2 -> "skin>${SKIN_THRESHOLD_HEAVY_C}"
                    3 -> "skin>${SKIN_THRESHOLD_TERMINATE_C}_terminate"
                    else -> "unknown"
                }
                // fieldsVersion 65: decide the ACTION first, then record the
                // event with an honest `applied` flag. Level 1/2 "reduce
                // intensity" transitions are applied=false by construction:
                // effectiveIntensity is recorded but no workload consumes it
                // (all kernels read the probe's constant intensityFactor).
                // Level 3 is applied=true only when the terminate callback
                // actually fires per the tiered-evidence rules below.
                var terminateApplied = false
                if (newLevel == 3) {
                    // Don't terminate COOLDOWN (phase 7) — it's an idle
                    // phase that measures thermal recovery. Terminating
                    // would lose the thermal time-constant signal. Same
                    // for INIT_PROFILING (phase 0): probe is brief and
                    // self-limiting.
                    val phaseId = currentPhase.get()
                    val isIdlePhase = phaseId == 0 || phaseId == 7

                    // V2 fix #54: terminate decision uses tiered evidence:
                    //   1. Real sysfs skin reading > 50°C → safe to terminate
                    //   2. Synthetic skin (battery proxy) > 50 + thermalStatus
                    //      >= CRITICAL (4) → AI says device is critical, terminate
                    //   3. Synthetic skin > 50 alone → DO NOT terminate
                    //      (battery-as-skin lags real die temp; thermalStatus
                    //      gives the OS's authoritative view)
                    val thermalStatus = readPowerManagerStatus()
                    when {
                        isIdlePhase -> {
                            Log.i(TAG, "skipping terminate during idle phase $phaseId " +
                                "(skin=${"%.1f".format(skin)}°C — event recorded but phase continues)")
                        }
                        skinIsSysfs -> {
                            // Real sysfs skin sensor measurement → trust it.
                            Log.i(TAG, "throttle terminate (real sysfs skin): " +
                                "phase=$phaseId skin=${"%.1f".format(skin)}°C")
                            terminateApplied = runCatching { phaseTerminateRequest() }.isSuccess
                        }
                        thermalStatus >= 4 -> {
                            // Synthetic skin BUT Android OS reports CRITICAL/EMERGENCY/SHUTDOWN.
                            // Trust the OS's enum (authoritative even when sysfs blocked).
                            Log.i(TAG, "throttle terminate (thermalStatus=$thermalStatus, " +
                                "synthetic skin ignored): phase=$phaseId")
                            terminateApplied = runCatching { phaseTerminateRequest() }.isSuccess
                        }
                        else -> {
                            // Synthetic skin > 50 + thermalStatus < CRITICAL.
                            // Battery-proxy reading isn't trustworthy for
                            // termination — log only.
                            Log.i(TAG, "throttle event recorded but NOT terminating: " +
                                "skin=${"%.1f".format(skin)}°C is synthetic + " +
                                "thermalStatus=$thermalStatus < CRITICAL. " +
                                "Phase continues; AI sees the event in footer.")
                        }
                    }
                }
                val event = ThrottlingEvent(
                    timestamp = System.currentTimeMillis(),
                    phase = currentPhase.get(),
                    skinTemp = skin,
                    newIntensity = effectiveIntensity,
                    reason = reason,
                    sourceIsSysfs = skinIsSysfs,
                    applied = terminateApplied
                )
                synchronized(eventsList) { eventsList += event }
                Log.w(
                    TAG,
                    "throttle: phase=${event.phase} skin=${"%.1f".format(skin)}°C " +
                        "level=$newLevel newIntensity=${"%.2f".format(effectiveIntensity)} " +
                        "reason=$reason source=${if (skinIsSysfs) "sysfs" else "synthetic"} " +
                        "applied=$terminateApplied"
                )
            }
        } catch (t: Throwable) {
            Log.w(TAG, "tick failed: ${t.message}")
        }
    }
}
