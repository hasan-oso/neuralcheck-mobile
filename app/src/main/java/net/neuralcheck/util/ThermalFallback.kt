package net.neuralcheck.util

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.util.Log

/**
 * Kotlin-side thermal probe with fallbacks for devices where the native
 * `/sys/class/thermal/thermal_zone*` reader returns -1.0 (SELinux blocks
 * direct sysfs access — observed on Samsung One UI 11+, MIUI, ColorOS).
 *
 * Read priority:
 *   1. [StressNative.readThermalZones] — native sysfs scan; works on AOSP,
 *      Pixel, most non-Samsung. Returns 4 distinct zones (skin/battery/
 *      cpu/gpu) when present.
 *   2. PowerManager.getCurrentThermalStatus (API 29+) — coarse 0..6 enum
 *      mapped to a synthesized temperature so the existing code paths
 *      that expect Celsius keep working without branching.
 *   3. BatteryManager.EXTRA_TEMPERATURE (API 1+, universal) — battery
 *      temperature as a coarse skin-temp proxy. Typically 5–10°C cooler
 *      than CPU silicon under stress, but the rate-of-change signal
 *      (which is what AdaptiveIntensityController and
 *      DynamicThermalThrottler actually use) is preserved.
 *
 * Returned array layout matches [StressNative]:
 *   [0] skin, [1] battery, [2] cpu, [3] gpu (all in °C; -1f if unavailable)
 *
 * When falling back to battery temperature, ALL four slots return the
 * same value — callers cannot distinguish skin from cpu in degraded
 * mode. The footer's `deviceTierMeasured` should record the degradation
 * so the AI can discount per-zone reasoning when it sees them.
 */
object ThermalFallback {
    private const val TAG = "NeuralCheck.Thermal"

    /**
     * Tracks per-slot source of the last read() call. true =
     * sysfs (real measurement); false = synthetic (battery temp
     * passthrough used as skin proxy, or PowerManager.thermalStatus
     * mapping for CPU/GPU). Length 4 matches the FloatArray read()
     * returns. Updated in-place by read(). Volatile reference assign
     * publishes the snapshot atomically across threads.
     */
    @Volatile
    private var lastSourceWasSysfs: BooleanArray = BooleanArray(4)

    /**
     * V2 fix #56 (2.4): per-call source tally for skin slot. Lets the
     * post-scan DataQuality calculator surface coverage info to the AI
     * (e.g. "skin_temp: 60% sysfs, 40% battery_proxy"). Reset at the
     * start of every scan via [resetSourceTracking].
     */
    private val sourceCount = java.util.concurrent.ConcurrentHashMap<String, Int>()

    /** V2 fix #56 (2.4): snapshot the per-source counts, e.g. for footer. */
    fun getSourceBreakdown(): Map<String, Int> = sourceCount.toMap()

    /** V2 fix #56 (2.4): reset counters at scan start. */
    fun resetSourceTracking() {
        sourceCount.clear()
    }

    /**
     * Bug fix #27: callers (throttler) need to distinguish
     * sysfs-measured values from synthetic ones derived from
     * PowerManager.thermalStatus. read() returns the FloatArray
     * because every sample needs a number — but using a synthetic
     * number for terminate decisions amounts to trusting an
     * interpretation as if it were measurement.
     *
     * This companion call returns true when the most recent read
     * for [slotIndex] was filled from sysfs (real measurement);
     * false when it was filled from PowerManager status mapping
     * (synthetic). Stored in lastSourceWasSysfs[] which read()
     * updates each call. Thread-safety: readers and the throttler
     * tick share the same singleton, but the array is volatile-
     * referenced and read() runs synchronously per call, so a
     * caller will see a consistent snapshot of the last read.
     */
    fun isLastReadFromSysfs(slotIndex: Int): Boolean {
        val mask = lastSourceWasSysfs
        if (slotIndex < 0 || slotIndex >= mask.size) return false
        return mask[slotIndex]
    }

    /**
     * Reads thermal zones with fallback. Always returns a 4-element
     * FloatArray. Slots are -1f only when ALL three sources fail
     * (battery permission denied, no PowerManager, no native sysfs) —
     * that's a real "no thermal data" state worth surfacing, not just
     * a sysfs-blocked Samsung.
     */
    fun read(context: Context): FloatArray {
        // Bug fix: previously the function returned native zones as-is
        // whenever ANY zone had a valid reading, leaving missing slots
        // (e.g. skin) as -1. On Samsung S24 Ultra, native CPU zone
        // works but native skin zone doesn't — so skin_temp_c (sample
        // index 18) was -1 across all samples despite a fallback path
        // existing. The fix is per-slot: read native, then for each
        // slot still at -1, attempt to fill it from secondary sources.
        // The output is a "best available" reading per slot rather
        // than all-or-nothing.

        val native = runCatching { StressNative.readThermalZones() }
            .getOrDefault(FloatArray(StressNative.THERMAL_COUNT) { StressNative.THERMAL_NOT_FOUND })

        // Output starts as a copy of native (already contains real
        // readings where available). We then fill -1 slots with
        // fallbacks per slot, not all-at-once.
        val out = if (native.size >= StressNative.THERMAL_COUNT) {
            native.copyOf()
        } else {
            FloatArray(StressNative.THERMAL_COUNT) { StressNative.THERMAL_NOT_FOUND }
        }

        // Bug fix #27: track per-slot source. true = real sysfs
        // measurement; false = synthetic (PowerManager status proxy
        // for SKIN/CPU/GPU). BATTERY is always true when populated
        // because BatteryManager.EXTRA_TEMPERATURE is a hardware sensor
        // reading, not an OS interpretation. Initialised to true for
        // any slot already filled by native (above), false otherwise.
        val sourceMask = BooleanArray(StressNative.THERMAL_COUNT)
        for (i in 0 until StressNative.THERMAL_COUNT) {
            sourceMask[i] = out[i] > 0f  // native sysfs filled this
        }

        // Identify which slots still need fallback after native read.
        val needsFallback = (0 until StressNative.THERMAL_COUNT).any {
            out[it] <= 0f
        }
        if (!needsFallback) {
            // Every slot from sysfs — publish mask and return.
            lastSourceWasSysfs = sourceMask
            return out
        }

        // Read secondary source. Battery temp is a real sensor reading.
        val battTemp = readBatteryTemp(context)

        // V2 fix #54: PowerManager.thermalStatus enum (0..6) is no
        // longer mapped to synthetic Celsius. The previous step-function
        // (status=3 → 52°C) polluted skin_temp_c samples with values
        // that look like sensor readings but are actually OS interpretations
        // of unknown thermal state. Effects observed:
        //   - throttler terminated phases on synthetic 52°C
        //   - AI saw "skin_temp = 52.0" and assumed real measurement
        //   - false-positive heat assessments on devices that block sysfs
        //
        // New policy:
        //   - skin slot: use battery_temp if available (real thermistor),
        //     else -1 (clearly "unavailable" for AI)
        //   - cpu/gpu slots: -1 if native unavailable (no fake values)
        //   - thermalStatus integer is preserved separately via
        //     SystemFileReader.readThermalStatus() and surfaces in
        //     footer.thermalStatusMax — AI gets the OS's view as a
        //     coarse signal, but never confused with actual temperature.

        // Skin slot fallback: ONLY battery_temp (real sensor). No synthetic.
        if (out[StressNative.THERMAL_SKIN] <= 0f) {
            if (battTemp > 0f) {
                out[StressNative.THERMAL_SKIN] = battTemp
                // sourceMask[SKIN] stays false: battery-as-skin proxy
                // is still synthetic in the sense that it's not the
                // true skin sensor — but at least the underlying
                // measurement is a real thermistor reading, not an
                // OS interpretation. Throttler treats it accordingly.
            }
            // else: leave -1f. AI sees "unavailable" and reasons correctly.
        }

        // Battery slot fallback: only EXTRA_TEMPERATURE is meaningful.
        // Real thermistor reading → counts as "sysfs/real" per #27.
        if (out[StressNative.THERMAL_BATTERY] <= 0f && battTemp > 0f) {
            out[StressNative.THERMAL_BATTERY] = battTemp
            sourceMask[StressNative.THERMAL_BATTERY] = true  // real sensor
        }

        // V2 fix #54: CPU and GPU slots — NO synthetic fallback.
        // Previously mapped statusCelsius (PowerManager enum → fake °C)
        // into these slots, polluting cpu_temp_c and gpu_temp_c samples
        // with values that crossed safety thresholds artificially.
        // Now: leave at -1 (sentinel "unavailable") so the AI knows
        // these signals are missing rather than inferring from synthetic.
        // The AI still gets thermalStatus integer in footer for
        // OS-level heat awareness.

        Log.i(
            TAG,
            "per-slot fallback: native=[${native.joinToString(",") { "%.1f".format(it) }}] " +
                "battTemp=${"%.1f".format(battTemp)} → " +
                "out=[${out.joinToString(",") { "%.1f".format(it) }}] " +
                "src=[${sourceMask.joinToString(",") { if (it) "sysfs" else "unavail/proxy" }}]"
        )

        // V2 fix #56 (2.4): tally the skin slot source per call.
        val skinSource = when {
            sourceMask[StressNative.THERMAL_SKIN] -> "sysfs_skin"
            out[StressNative.THERMAL_SKIN] > 0f -> "battery_proxy"
            else -> "missing"
        }
        sourceCount.merge(skinSource, 1) { a, b -> a + b }

        // Publish source mask atomically (volatile reference assign).
        lastSourceWasSysfs = sourceMask
        return out
    }

    /** BatteryManager.EXTRA_TEMPERATURE is in deci-Celsius (e.g. 320 → 32.0°C). */
    private fun readBatteryTemp(context: Context): Float = try {
        val intent: Intent? = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val deci = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
        if (deci > 0) deci / 10.0f else -1f
    } catch (t: Throwable) {
        Log.w(TAG, "readBatteryTemp failed: ${t.message}")
        -1f
    }

    /** Returns PowerManager.getCurrentThermalStatus (API 29+) or -1. */
    private fun readPowerManagerStatus(context: Context): Int {
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
}
