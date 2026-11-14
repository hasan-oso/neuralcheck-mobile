package net.neuralcheck.service.collector

import android.content.Context
import android.os.Build
import android.os.HardwarePropertiesManager
import android.os.PowerManager
import android.os.Process
import android.os.SystemClock
import android.util.Log
import net.neuralcheck.util.FileTextRead
import net.neuralcheck.util.ReasonText
import java.io.File

/**
 * Reads system information from /sys/ and /proc/ filesystem (ADR 4.4).
 * All reads wrapped in try/catch - returns -1 or null on failure.
 *
 * `context` is optional so existing call sites can construct a no-arg
 * instance. When provided, it unlocks the [HardwarePropertiesManager]
 * fallback for CPU/GPU temperature on devices where /sys/class/thermal/
 * is empty or unreadable (observed on Samsung Galaxy A30s with 0 thermal
 * zones, and many Knox-protected configurations).
 */
class SystemFileReader(private val context: Context? = null) {

    companion object {
        private const val TAG = "NeuralCheck"
        private const val CPU_BASE = "/sys/devices/system/cpu"
        private const val THERMAL_BASE = "/sys/class/thermal"
        private const val BATTERY_BASE = "/sys/class/power_supply/battery"

        /**
         * The one sysfs file the cycle count is read from (fieldsVersion 68, T-02). The probe list in
         * `BatteryTests.CYCLE_COUNT_PROBE_PATHS` is DIAGNOSTIC (it holds `battery_health`, `asoc`, `aging`,
         * which are not cycle counts), so it is not a list of sources.
         */
        const val BATTERY_CYCLE_COUNT_PATH = "$BATTERY_BASE/cycle_count"

        // Thermal-zone type names used by various OEMs for CPU sensors.
        // All comparisons are case-insensitive substring matches against the
        // zone's `type` file. Order matters: more specific names first so
        // "cpu-0-0" doesn't get hijacked by a future shorter match.
        // Sources: AOSP, Samsung Exynos kernel, Qualcomm tsens driver,
        // MediaTek mtktscpu driver, Pixel Tensor.
        private val CPU_ZONE_NAMES = listOf(
            // Generic / AOSP
            "cpu", "soc", "cpu_thermal", "soc_thermal",
            // Qualcomm tsens (S10 5G uses Snapdragon 855 / Exynos 9820)
            "tsens_tz_sensor", "msm_therm",
            "cpu-0-0", "cpu-0-1", "cpu-1-0", "cpu-0-0-0", "cpu-1-0-0",
            "cpuss-0", "cpuss-1", "cpuss0", "cpuss1", "cpuss",
            // Samsung Exynos (9820, 7904 — A30s / S10 5G international)
            "mngs", "big", "little", "mid", "middle", "cluster", "exynos",
            "ap_therm", "ap-therm", "ap_thermal",
            // MediaTek
            "mtkts_cpu", "mtktscpu",
            // Pixel Tensor / Google
            "BIG", "MID", "LITTLE", "neutral_therm"
        )

        private val GPU_ZONE_NAMES = listOf(
            "gpu", "gpu_thermal", "gpu-thermal",
            "g3d", "G3D",
            "mali", "Mali",
            "adreno", "Adreno",
            "kgsl", "kgsl_thermal"
        )

        // Non-CPU thermal-zone token list. A zone whose `type` substring-
        // contains any of these is rejected from BOTH the Tier 1 named-zone
        // search and the Tier 2 max-zone fallback — we treat the whole class
        // as "definitely not silicon".
        //
        // Phase 1.14 expansion (was BATTERY_ZONE_BLACKLIST, battery-only):
        //   • battery family — original entries, kept verbatim
        //   • charger / power-IC / USB — wireless and wired charging dies can
        //     hit 60–85 °C during fast charge; on Samsung Galaxy S24 Ultra
        //     these were the prime suspects for T-01's 80.2 °C anomaly
        //   • modem / RF — 5G mmWave + cellular PA sensors routinely sit at
        //     50–80 °C under load
        //   • skin / ambient / case — device-back-surface sensors, not
        //     silicon
        //   • PMIC / flash / display — peripheral dies and panel sensors
        //     that have no relationship to CPU thermal state
        //   • pa_therm — RF power amplifier; common on flagships
        //
        // Substring match (not prefix-anchored) so this is deliberately
        // aggressive: better to drop a genuine CPU zone with an unfortunate
        // name (e.g. hypothetical `cpuss_skin`) than to leak a charger /
        // modem reading into T-01. The Tier-2 fallback gives us another
        // shot at recovering a sane CPU reading from whatever non-CPU
        // zones remain.
        private val NON_CPU_ZONE_BLACKLIST = listOf(
            // battery (original tokens)
            "battery", "bat", "battery_thermal", "batt_therm",
            "battery_temp", "batt_temp", "battery_skin",
            // charger / power-IC / USB
            "charger", "wpc", "usb", "chg",
            // modem / RF
            "modem", "cp_therm", "mdm", "pa_therm",
            // skin / ambient / case
            "skin", "ambient", "case",
            // PMIC / flash / display
            "pmic", "flash", "display", "lcd", "oled",
            // misc
            "xo"
        )

        // Plausible temperature window (°C). Anything outside this is either
        // a unit-conversion artifact (already-decimalised vs millidegrees)
        // or a kernel scratch register left at a sentinel value.
        private const val MIN_VALID_TEMP_C = 10f
        private const val MAX_VALID_TEMP_C = 120f
        // For the max-zone fallback we tighten the lower bound — a CPU
        // sensor reading 12°C means the device is ice-cold or the sensor
        // is broken; either way it would mislead the AI.
        private const val MIN_FALLBACK_TEMP_C = 15f
        // Phase 1.14 sanity cap for CPU readings. Modern SoCs throttle hard
        // and then thermal-shutdown well below 110 °C — any single zone
        // reporting above this is almost certainly mis-classified (non-CPU
        // sensor with a different unit, charger IC at peak fast-charge, or
        // a kernel scratch register).
        private const val MAX_PLAUSIBLE_CPU_TEMP_C = 105f

        /**
         * Phase 1.14: anchored token match. Returns true when [type] equals
         * [token] (case-insensitive) or starts with `${token}_` / `${token}-`.
         * Replaces the previous unconditional `contains(...)` which let short
         * tokens like "big" / "mid" / "AP" leak into unrelated zone names
         * such as `wpc_big_thermal`, `usb_midbus`, `modem_ap`.
         */
        internal fun typeMatchesCpuToken(type: String, token: String): Boolean {
            val t = type.trim().lowercase()
            val k = token.lowercase()
            return t == k || t.startsWith("${k}_") || t.startsWith("${k}-")
        }

        internal fun isNonCpuZone(type: String): Boolean =
            NON_CPU_ZONE_BLACKLIST.any { type.contains(it, ignoreCase = true) }

        /**
         * Phase 1.14: pure-function CPU-zone selector — pulled out of
         * [readCpuTemp] so unit tests can exercise the zone-selection logic
         * without touching the file system. Returns -1f when no zone passes
         * Tier 1 + Tier 2 (caller falls back to HardwarePropertiesManager).
         */
        internal fun selectCpuTempFromZones(zones: List<ThermalZone>): Float {
            // Tier 1 — name-matched CPU zone that is NOT on the non-CPU blacklist
            val nameMatched = zones.firstOrNull { z ->
                !isNonCpuZone(z.type) &&
                CPU_ZONE_NAMES.any { typeMatchesCpuToken(z.type, it) }
            }
            if (nameMatched != null) {
                val c = milliCelsiusToCelsius(nameMatched.tempMilliC)
                if (c in MIN_VALID_TEMP_C..MAX_PLAUSIBLE_CPU_TEMP_C) return c
            }
            // Tier 2 — hottest non-blacklisted zone in the plausible band
            val maxZoneTemp = zones
                .filter { !isNonCpuZone(it.type) }
                .map { milliCelsiusToCelsius(it.tempMilliC) }
                .filter { it in MIN_FALLBACK_TEMP_C..MAX_PLAUSIBLE_CPU_TEMP_C }
                .maxOrNull()
            return maxZoneTemp ?: -1f
        }

        private fun milliCelsiusToCelsius(raw: Long): Float =
            if (raw > 1_000) raw / 1_000f else raw.toFloat()
    }

    // V2 fix #56 (3.2): cache thermal zones with 1-second TTL.
    // 4Hz sampling × 4 phases × 75s = ~1200 calls per scan, each scanning
    // /sys/class/thermal — a relatively expensive directory walk. With a
    // 1s cache, calls drop to ~60 per scan with no observable accuracy
    // loss (thermal sensor update rates are typically 250ms-1s anyway).
    @Volatile private var cachedZones: List<ThermalZone>? = null
    @Volatile private var cachedZonesAtMs: Long = 0L
    private val ZONES_CACHE_TTL_MS = 1000L

    private var prevCpuTotal = 0L
    private var prevCpuIdle = 0L

    // V2 fix #56-3 (fieldsVersion 58): per-CPU /proc/stat history. Lazy-initialized
    // on first successful per-CPU read so a device with N online CPUs gets an
    // N-element history without a fixed 16-element ceiling.
    private var prevPerCpuTotal: LongArray? = null
    private var prevPerCpuIdle: LongArray? = null
    @Volatile private var perCpuStatAvailable = true

    // Process-level CPU fallback state — used on modern Android where /proc/stat is blocked
    private var prevProcCpuMs = 0L
    private var prevWallMs = 0L
    @Volatile private var procStatAvailable = true

    /**
     * fieldsVersion 66 — provenance of [readCpuUsage]'s output. true while
     * the global `/proc/stat` line is readable; flips to false (sticky for
     * the life of this reader) the first time it isn't, after which every
     * reading is the process-level fallback (this app's own CPU share ÷
     * cores — NOT system utilisation). The sampler records the sample
     * index of that flip so dataQuality can label v[6]'s real source.
     */
    fun isProcStatAvailable(): Boolean = procStatAvailable

    // PowerManager.getThermalHeadroom is the only thermal signal that survives
    // SELinux on stock Samsung — but the platform throws IllegalStateException
    // if called more than once a second, so we cache the last value and only
    // hit the API on a >=1.1s stride.
    private val powerManager: PowerManager? = try {
        context?.getSystemService(Context.POWER_SERVICE) as? PowerManager
    } catch (t: Throwable) {
        Log.w(TAG, "POWER_SERVICE unavailable: ${t.message}")
        null
    }
    @Volatile private var lastHeadroom: Float = Float.NaN
    @Volatile private var lastHeadroomReadMs: Long = 0L
    @Volatile private var headroomUnsupported: Boolean = false

    /**
     * fieldsVersion 68 (ت-03, ت-04): the un-cached, un-pinned view of this
     * reader's PowerManager that the thermal LISTS use (see
     * [ThermalPlatform]). The cached/pinned [readThermalHeadroom] above keeps
     * serving the governor guard, the aggregator and the probe unchanged
     * (contract S-03).
     */
    fun thermalPlatform(): ThermalPlatform = PowerManagerThermalPlatform(powerManager)

    /**
     * Read current frequency of a specific CPU core in kHz.
     *
     * V2 fix #56 (3.3): on devices that gate per-CPU cpufreq nodes behind
     * SELinux, fall back to /sys/devices/system/cpu/cpufreq/policyN/
     * scaling_cur_freq. Policy nodes are sometimes readable when per-CPU
     * nodes aren't (different SELinux label). Mapping is heuristic but
     * matches the typical 4-cluster (0/4/7) and 3-cluster (0/4/7) layouts
     * across recent ARM SoCs.
     */
    fun readCpuFrequency(coreIndex: Int): Long {
        // Tier 1: per-core node (the existing fast path)
        val perCore = readLongFromFile("$CPU_BASE/cpu$coreIndex/cpufreq/scaling_cur_freq")
        if (perCore > 0L) return perCore

        // Tier 2: policy{N} node — coreIndex maps to a policy index.
        // Most ARM SoCs expose policies at cluster heads (0, 4, 7).
        // We try the obvious match first, then fall back to a small set.
        val policyCandidates = when (coreIndex) {
            0, 1, 2, 3 -> listOf(0)             // little cluster head
            4, 5, 6 -> listOf(4, 6)             // mid cluster (common heads)
            7 -> listOf(7, 6, 4)                // big/prime
            else -> listOf(coreIndex, 0, 4, 7)  // unknown topology — try several
        }
        for (policyIndex in policyCandidates) {
            val v = readLongFromFile(
                "$CPU_BASE/cpufreq/policy$policyIndex/scaling_cur_freq"
            )
            if (v > 0L) return v
        }
        return -1L
    }

    /**
     * Read maximum frequency of a specific CPU core in kHz.
     */
    fun readCpuMaxFrequency(coreIndex: Int): Long {
        return readLongFromFile("$CPU_BASE/cpu$coreIndex/cpufreq/cpuinfo_max_freq")
    }

    /**
     * Read cpu0 max frequency (kHz). Convenience accessor — the cpufreq
     * sysfs node is one of the few `/sys` paths that survive SELinux on
     * modern Samsung One UI builds, so even when [readCpuTemp] returns -1
     * this typically still works.
     */
    fun readCpuMaxFreq(): Long? {
        val v = readLongFromFile("$CPU_BASE/cpu0/cpufreq/cpuinfo_max_freq")
        return if (v > 0L) v else null
    }

    /**
     * Read cpu0 current scaling frequency (kHz).
     */
    fun readCpuCurrentFreq(): Long? {
        val v = readLongFromFile("$CPU_BASE/cpu0/cpufreq/scaling_cur_freq")
        return if (v > 0L) v else null
    }

    /**
     * Read PowerManager thermal headroom, a normalised 0..1+ scalar where
     * <0.5 = cool, ~0.75 = normal load, ~0.9 = warm, >=1.0 = throttling.
     * Available on Android 11+ via [PowerManager.getThermalHeadroom]; the
     * call is rate-limited to once per second by the platform, so we cache
     * the last value and only refresh on a >=1.1s stride. Returns
     * [Float.NaN] when unsupported (older API, vendor stub) — the sampler
     * downgrades to thermal-zone / battery-temp without surfacing an error.
     *
     * This is the headline thermal signal on devices where /sys/class/thermal
     * is blocked by SELinux (most modern Samsung builds).
     */
    fun readThermalHeadroom(): Float {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return Float.NaN
        if (headroomUnsupported) return Float.NaN
        val pm = powerManager ?: return Float.NaN
        val now = SystemClock.elapsedRealtime()
        if (now - lastHeadroomReadMs < 1100L) return lastHeadroom
        return try {
            val v = pm.getThermalHeadroom(0) // 0 = "right now"
            lastHeadroom = v
            lastHeadroomReadMs = now
            v
        } catch (t: Throwable) {
            // UnsupportedOperationException on a handful of vendor builds;
            // pin the flag so we stop hammering the API for the rest of run.
            Log.w(TAG, "getThermalHeadroom failed once — disabling: ${t.message}")
            headroomUnsupported = true
            Float.NaN
        }
    }

    /**
     * Read PowerManager.currentThermalStatus on Android 10+. Values:
     * 0=NONE, 1=LIGHT, 2=MODERATE, 3=SEVERE, 4=CRITICAL, 5=EMERGENCY,
     * 6=SHUTDOWN. Returns -1 if unsupported or readable.
     *
     * This complements [readThermalHeadroom] — headroom is a continuous
     * gauge, status is the kernel's own classification of the same signal.
     * We surface both because on Samsung Exynos builds headroom occasionally
     * lags the status flip by a sample or two.
     */
    fun readThermalStatus(): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return -1
        val pm = powerManager ?: return -1
        return try {
            pm.currentThermalStatus
        } catch (t: Throwable) {
            -1
        }
    }

    /**
     * Read CPU temperature in Celsius, or −1 if unavailable.
     *
     * Three-tier lookup:
     *   1. Walk every /sys/class/thermal/thermal_zone* and match the `type`
     *      file against [CPU_ZONE_NAMES]. Covers Samsung Exynos (mngs, big,
     *      ap_therm), Qualcomm (tsens_tz_sensor, cpuss-N), MediaTek (mtktscpu).
     *   2. If no name matched, fall back to the **highest** valid temperature
     *      from any thermal zone. On stress runs the hottest zone almost
     *      always tracks CPU, and on devices where vendor sensor names are
     *      anonymised this is the only signal we have.
     *   3. If /sys/class/thermal is empty or unreadable (Samsung Galaxy A30s
     *      reported 0 zones in field traces), try [HardwarePropertiesManager]
     *      via the optional [context]. That API is privileged on most
     *      builds, but on a few OEM forks it still serves untrusted apps.
     *
     * Anything still missing returns −1; the sampler's snapshotStats() then
     * promotes battery temperature to the canonical "tempSource".
     */
    fun readCpuTemp(): Float {
        // Phase 1.14: Tier 1 + Tier 2 zone-search delegated to the pure
        // [selectCpuTempFromZones] helper in the companion object so the
        // selection logic is unit-testable without touching /sys.
        val zones = readThermalZones()
        val fromZones = selectCpuTempFromZones(zones)
        if (fromZones > 0f) return fromZones

        // Tier 3 — HardwarePropertiesManager (privileged on most builds; harmless to try)
        val hpmTemp = readCpuTempFromHpm()
        if (hpmTemp != null) return hpmTemp

        return -1f
    }

    /**
     * Read GPU temperature in Celsius, or −1 if unavailable. Same three-tier
     * strategy as [readCpuTemp]: vendor name match → ignored (no max-zone
     * fallback for GPU because it would shadow the CPU result on devices
     * where only one sensor is exposed) → [HardwarePropertiesManager].
     */
    fun readGpuTemp(): Float {
        val zones = readThermalZones()
        val nameMatched = zones.firstOrNull { z ->
            GPU_ZONE_NAMES.any { z.type.contains(it, ignoreCase = true) }
        }
        if (nameMatched != null) {
            val c = milliCelsiusToCelsius(nameMatched.tempMilliC)
            if (c in MIN_VALID_TEMP_C..MAX_VALID_TEMP_C) return c
        }
        val hpmTemp = readGpuTempFromHpm()
        if (hpmTemp != null) return hpmTemp
        return -1f
    }

    // milliCelsiusToCelsius lives on the companion object (Phase 1.14 refactor)
    // so the pure selectCpuTempFromZones helper can call it from a static
    // context. Instance methods (readGpuTemp below, etc.) still resolve the
    // unqualified name to the companion member, so the call sites are
    // unchanged.

    private fun readCpuTempFromHpm(): Float? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return null
        val ctx = context ?: return null
        return try {
            val hpm = ctx.getSystemService(Context.HARDWARE_PROPERTIES_SERVICE)
                as? HardwarePropertiesManager ?: return null
            val temps = hpm.getDeviceTemperatures(
                HardwarePropertiesManager.DEVICE_TEMPERATURE_CPU,
                HardwarePropertiesManager.TEMPERATURE_CURRENT
            )
            temps?.toList()?.filter { it in MIN_VALID_TEMP_C..MAX_VALID_TEMP_C }?.maxOrNull()
        } catch (_: Throwable) {
            // SecurityException is the common case (API restricted to system apps).
            // Suppress entirely — we already exhausted the public-API tiers.
            null
        }
    }

    private fun readGpuTempFromHpm(): Float? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return null
        val ctx = context ?: return null
        return try {
            val hpm = ctx.getSystemService(Context.HARDWARE_PROPERTIES_SERVICE)
                as? HardwarePropertiesManager ?: return null
            val temps = hpm.getDeviceTemperatures(
                HardwarePropertiesManager.DEVICE_TEMPERATURE_GPU,
                HardwarePropertiesManager.TEMPERATURE_CURRENT
            )
            temps?.toList()?.filter { it in MIN_VALID_TEMP_C..MAX_VALID_TEMP_C }?.maxOrNull()
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Calculate CPU usage percentage.
     *
     * Tries /proc/stat first (global, accurate). On modern Android (API 26+) this path
     * is frequently blocked by SELinux for untrusted apps, so we fall back to a
     * process-level reading using Process.getElapsedCpuTime() normalised by wall-clock
     * time and core count. During the stress test this approximates system utilisation
     * closely because the CpuLoadGenerator saturates every core with MAX_PRIORITY threads.
     *
     * Returns 0–100 on success, or -1f only if both paths fail catastrophically.
     */
    fun readCpuUsage(): Float {
        if (procStatAvailable) {
            val globalPct = readCpuUsageFromProcStat()
            if (globalPct >= 0f) return globalPct
            // Mark as unavailable so future calls go straight to the fallback
            procStatAvailable = false
            Log.i(TAG, "/proc/stat unreadable — switching to process-level CPU usage fallback")
        }
        return readCpuUsageFromProcess()
    }

    private fun readCpuUsageFromProcStat(): Float {
        return try {
            val statLine = File("/proc/stat").readLines().firstOrNull() ?: return -1f
            val parts = statLine.split("\\s+".toRegex())
            if (parts.size < 8) return -1f

            val user = parts[1].toLongOrNull() ?: 0L
            val nice = parts[2].toLongOrNull() ?: 0L
            val system = parts[3].toLongOrNull() ?: 0L
            val idle = parts[4].toLongOrNull() ?: 0L
            val iowait = parts[5].toLongOrNull() ?: 0L
            val irq = parts[6].toLongOrNull() ?: 0L
            val softirq = parts[7].toLongOrNull() ?: 0L

            val total = user + nice + system + idle + iowait + irq + softirq
            val totalDelta = total - prevCpuTotal
            val idleDelta = idle - prevCpuIdle

            prevCpuTotal = total
            prevCpuIdle = idle

            if (totalDelta <= 0) return -1f
            ((totalDelta - idleDelta).toFloat() / totalDelta.toFloat()) * 100f
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read /proc/stat: ${e.message}")
            -1f
        }
    }

    /**
     * V2 fix #56-3 (fieldsVersion 58): per-CPU usage from /proc/stat lines
     * `cpu0`, `cpu1`, ... `cpuN`. Returns FloatArray indexed by CPU number,
     * each element 0..100 (utilization percentage over the interval since
     * the previous call). Returns null when /proc/stat is unreadable or
     * doesn't expose per-CPU lines (some MIUI/HyperOS builds redact them).
     *
     * Single-threaded usage from the sampler thread; prev counter arrays
     * are lazy-initialized on first successful read to size to the actual
     * online CPU count. The first call after [reset] returns -1f for every
     * CPU (no delta yet); subsequent calls return real percentages.
     *
     * Distinct from [readCpuUsage] which only reads the global aggregate.
     * Used by [DisciplinedSampler] in V2 mode to feed per-cluster usage
     * into [SamplerStats] for the per-cluster saturation criterion.
     */
    fun readPerCpuUsage(): FloatArray? {
        if (!perCpuStatAvailable) return null
        return try {
            val lines = File("/proc/stat").readLines()
            // Find lines starting with "cpu0", "cpu1", ... — skip the
            // overall "cpu " line (with trailing space) and any non-CPU
            // lines (intr, ctxt, btime, etc.).
            val cpuLines = lines
                .filter { it.startsWith("cpu") && it.length > 3 && it[3].isDigit() }
            if (cpuLines.isEmpty()) {
                // /proc/stat exists but per-CPU lines redacted — pin sticky
                // false so we don't keep parsing every sample.
                Log.i(TAG, "/proc/stat per-CPU lines unavailable — disabling perCpuUsage")
                perCpuStatAvailable = false
                return null
            }

            val n = cpuLines.size
            // Lazy-init prev arrays on first read.
            if (prevPerCpuTotal == null || prevPerCpuTotal!!.size != n) {
                prevPerCpuTotal = LongArray(n)
                prevPerCpuIdle = LongArray(n)
            }
            val prevTotal = prevPerCpuTotal!!
            val prevIdle = prevPerCpuIdle!!

            val out = FloatArray(n) { -1f }
            for (i in 0 until n) {
                val parts = cpuLines[i].split("\\s+".toRegex())
                if (parts.size < 8) continue
                val user = parts[1].toLongOrNull() ?: 0L
                val nice = parts[2].toLongOrNull() ?: 0L
                val system = parts[3].toLongOrNull() ?: 0L
                val idle = parts[4].toLongOrNull() ?: 0L
                val iowait = parts[5].toLongOrNull() ?: 0L
                val irq = parts[6].toLongOrNull() ?: 0L
                val softirq = parts[7].toLongOrNull() ?: 0L
                val total = user + nice + system + idle + iowait + irq + softirq
                val totalDelta = total - prevTotal[i]
                val idleDelta = idle - prevIdle[i]
                prevTotal[i] = total
                prevIdle[i] = idle
                if (totalDelta > 0) {
                    out[i] = ((totalDelta - idleDelta).toFloat() / totalDelta.toFloat()) * 100f
                }
            }
            out
        } catch (e: Exception) {
            // Block any further reads to avoid hammering a known-broken
            // path. The footer's perCpuUsageAvailable=false records this
            // for the AI to see.
            Log.w(TAG, "readPerCpuUsage failed (disabling): ${e.message}")
            perCpuStatAvailable = false
            null
        }
    }

    private fun readCpuUsageFromProcess(): Float {
        return try {
            val now = SystemClock.elapsedRealtime()
            val procCpu = Process.getElapsedCpuTime() // ms summed across all threads / cores
            val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)

            if (prevWallMs == 0L) {
                prevWallMs = now
                prevProcCpuMs = procCpu
                return 0f
            }

            val wallDelta = now - prevWallMs
            val cpuDelta = procCpu - prevProcCpuMs
            prevWallMs = now
            prevProcCpuMs = procCpu

            if (wallDelta <= 0L) return 0f
            // cpuDelta is aggregated across cores, so divide by cores to get 0–100%
            ((cpuDelta.toFloat() / (wallDelta.toFloat() * cores)) * 100f).coerceIn(0f, 100f)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read process CPU usage: ${e.message}")
            -1f
        }
    }

    /**
     * Read all thermal zones with their types and temperatures.
     *
     * V2 fix #56 (3.2): wrapped with a 1s TTL cache. The directory walk
     * + 2 file reads per zone × 30+ zones is a measurable cost at 4Hz
     * sampling (called from readCpuTemp + readGpuTemp + ThermalFallback,
     * three times per sample). Cache reduces the call rate from ~1200/scan
     * to ~60/scan with no observable accuracy loss — sensor update rates
     * are typically 250ms-1s already.
     */
    fun readThermalZones(): List<ThermalZone> {
        val now = SystemClock.elapsedRealtime()
        val cached = cachedZones
        if (cached != null && (now - cachedZonesAtMs) < ZONES_CACHE_TTL_MS) {
            return cached
        }
        val fresh = doReadThermalZones()
        cachedZones = fresh
        cachedZonesAtMs = now
        return fresh
    }

    private fun doReadThermalZones(): List<ThermalZone> {
        val zones = mutableListOf<ThermalZone>()
        try {
            val thermalDir = File(THERMAL_BASE)
            if (!thermalDir.exists()) return zones

            thermalDir.listFiles()?.filter { it.name.startsWith("thermal_zone") }
                ?.forEach { zoneDir ->
                    try {
                        val index = zoneDir.name.removePrefix("thermal_zone").toIntOrNull() ?: return@forEach
                        val type = File(zoneDir, "type").readText().trim()
                        val temp = File(zoneDir, "temp").readText().trim().toLongOrNull() ?: 0L
                        zones.add(ThermalZone(index, type, temp))
                    } catch (e: Exception) {
                        // Skip unreadable zones
                    }
                }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read thermal zones: ${e.message}")
        }
        return zones
    }

    // ─────────────────── DIAGNOSTIC LOGGING (temporary) ───────────────────
    // Dumps every thermal_zone* under /sys/class/thermal/ along with what
    // readCpuTemp()/readGpuTemp() resolved to. Helps pinpoint why CPU temp
    // shows -1 on Samsung Galaxy S10 5G (Exynos 9820) — vendor zone names
    // such as "mngs", "big", "little", "AP", "G3D" don't match the current
    // contains("cpu"/"soc"/"tsens") filter. One-shot per process to avoid
    // flooding the sampler logs. Remove once diagnosed.
    private val thermalDumped = java.util.concurrent.atomic.AtomicBoolean(false)

    fun dumpAllThermalZones() {
        if (!thermalDumped.compareAndSet(false, true)) return
        Log.d(TAG, "════════════════════ THERMAL ZONES DIAGNOSTIC ════════════════════")
        Log.d(TAG, "Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} (board=${android.os.Build.BOARD}, hw=${android.os.Build.HARDWARE})")

        var found = 0
        for (i in 0..50) {
            val typeFile = File("$THERMAL_BASE/thermal_zone$i/type")
            val tempFile = File("$THERMAL_BASE/thermal_zone$i/temp")
            if (!typeFile.exists()) continue
            try {
                val type = if (typeFile.canRead()) typeFile.readText().trim() else "(unreadable)"
                val tempRaw = if (tempFile.exists() && tempFile.canRead()) tempFile.readText().trim() else "n/a"
                val tempMilliC = tempRaw.toLongOrNull()
                val tempC = if (tempMilliC != null) {
                    java.lang.String.format(java.util.Locale.US, "%.1f°C", tempMilliC / 1000f)
                } else "n/a"
                Log.d(TAG, "thermal_zone$i: type='$type' temp_raw=$tempRaw → $tempC")
                found++
            } catch (e: Exception) {
                Log.d(TAG, "thermal_zone$i: read error ${e.javaClass.simpleName}:${e.message}")
            }
        }
        Log.d(TAG, "Total thermal zones found: $found")

        // Also enumerate cooling devices — sometimes CPU temp is exposed
        // there instead of (or in addition to) thermal_zone* on Exynos SoCs.
        Log.d(TAG, "── /sys/class/thermal/cooling_device* enumeration ──")
        var coolingFound = 0
        for (i in 0..30) {
            val typeFile = File("$THERMAL_BASE/cooling_device$i/type")
            if (!typeFile.exists()) continue
            try {
                val type = if (typeFile.canRead()) typeFile.readText().trim() else "(unreadable)"
                Log.d(TAG, "cooling_device$i: type='$type'")
                coolingFound++
            } catch (e: Exception) {
                Log.d(TAG, "cooling_device$i: read error ${e.message}")
            }
        }
        Log.d(TAG, "Total cooling devices found: $coolingFound")

        Log.d(TAG, "Current readCpuTemp() = ${readCpuTemp()}°C  (filter: contains 'cpu'|'soc'|'tsens')")
        Log.d(TAG, "Current readGpuTemp() = ${readGpuTemp()}°C  (filter: contains 'gpu')")
        Log.d(TAG, "═════════════════════════════════════════════════════════════════")
    }

    /**
     * Count online CPU cores.
     */
    fun readCpuCoreCount(): Int {
        return try {
            Runtime.getRuntime().availableProcessors()
        } catch (e: Exception) {
            -1
        }
    }

    /**
     * Read a battery charge cycle count from the sysfs file at [path] — the sysfs step of
     * [BatteryCycleCount.read], the one reader of the cycle count (footer and B-08 both call it).
     * `null` for ANY failure (absent, SELinux-refused, not an integer): this reader keeps no error
     * text (the rule-21 limit), so its `null` is "not readable or absent", never a guessed cause.
     */
    fun readBatteryCycleCountAt(path: String): Int? {
        return try {
            File(path).readText().trim().toIntOrNull()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Read battery design capacity in µAh.
     */
    fun readChargeFullDesign(): Long? {
        return try {
            val value = File("$BATTERY_BASE/charge_full_design").readText().trim()
            value.toLongOrNull()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Read battery's *current* full-charge capacity in µAh — what the cell
     * can hold today after aging. Comparing this against [readChargeFullDesign]
     * yields a state-of-health percentage (`charge_full / charge_full_design`)
     * which is much more reliable than the BatteryManager.CHARGE_COUNTER
     * + level extrapolation (CHARGE_COUNTER is the *current* charge, not
     * full-charge capacity, and gets misinterpreted as full-capacity by a
     * naive `× 100 / level` formula when the device is mid-discharge).
     *
     * Available on most Samsung / Pixel kernels via /sys/class/power_supply/
     * battery/charge_full but absent on a non-trivial fraction of devices —
     * callers should fall back to BatteryManager when this returns null.
     */
    fun readChargeFull(): Long? {
        return try {
            val value = File("$BATTERY_BASE/charge_full").readText().trim()
            value.toLongOrNull()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Get CPU architecture ABI.
     */
    fun readCpuAbi(): String {
        return try {
            android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }
    }

    private fun readLongFromFile(path: String): Long {
        return try {
            File(path).readText().trim().toLongOrNull() ?: -1L
        } catch (e: Exception) {
            -1L
        }
    }

    /**
     * fieldsVersion 68 (rule 21): reads a small text file and, on failure,
     * keeps the error TEXT. [readLongFromFile] above swallows every
     * exception and returns -1, so a caller can never tell "EACCES (Permission
     * denied)" from "ENOENT (No such file or directory)" — both reach it as
     * one FileNotFoundException — nor either of them from an empty file. The
     * message of that exception is exactly what separates them (on Android:
     * "<path>: open failed: EACCES (Permission denied)"), so it is returned,
     * capped at 200 characters (ReasonText), as the failure reason.
     * Used by channel discovery and by the per-policy frequency channels.
     */
    fun readTextDetailed(path: String): FileTextRead {
        return try {
            FileTextRead.Text(File(path).readText().trim())
        } catch (e: Exception) {
            FileTextRead.Failed(ReasonText.of(e))
        }
    }
}

data class ThermalZone(
    val index: Int,
    val type: String,
    val tempMilliC: Long
)
