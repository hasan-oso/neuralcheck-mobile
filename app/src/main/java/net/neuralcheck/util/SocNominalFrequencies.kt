package net.neuralcheck.util

import android.util.Log
import java.io.File

/**
 * V2 fix #54 — SoC nominal max frequency lookup.
 *
 * Used by [net.neuralcheck.util.StressEffectivenessCalculator] to compute
 * a freq-ratio (peak observed vs nominal) and detect "stress test failed
 * to stress" — when the OS governor pre-emptively caps frequency so the
 * test never reaches saturation.
 *
 * This is NOT a per-SoC rule engine. It's a tiny reference table mapping
 * known [Build.HARDWARE] values to their published max prime/big-cluster
 * frequency, plus a sysfs fallback for unknown SoCs. The AI is still the
 * judge; this code only reports the physical reality of "did the device
 * reach its rated peak under our load?"
 *
 * Values are in kHz to match scaling_cur_freq sysfs format.
 * Sources: Qualcomm/MediaTek/Samsung published datasheets for the
 * highest-frequency cluster. Numbers may be rounded down by the governor
 * even on healthy hardware (typical -3% loss).
 */
object SocNominalFrequencies {
    private const val TAG = "NeuralCheck.SoC"

    /**
     * Returns the SoC's published peak frequency in kHz for the
     * fastest cluster (prime/big), or -1 if unknown.
     *
     * Caller should fall back to [readSysCpuFreqMax] when this returns
     * -1, since the kernel sometimes exposes the real ceiling via sysfs
     * even when the SoC name isn't in the table.
     */
    fun getSocNominalMaxFreq(buildHardware: String): Int {
        val hw = buildHardware.lowercase()
        return when {
            // ── MediaTek Dimensity flagships ──────────────────────────
            hw.contains("mt6991") -> 3_626_000  // Dimensity 9400 prime
            hw.contains("mt6989") -> 3_250_000  // Dimensity 9300 X4
            hw.contains("mt6985") -> 3_350_000  // Dimensity 9200
            hw.contains("mt6983") -> 3_050_000  // Dimensity 9000
            // ── Qualcomm Snapdragon (multiple chips share "qcom") ────
            // We can't disambiguate by hardware string alone — caller
            // must use sysfs fallback. Common values for reference:
            //   SD 8 Gen 3: 3300000 (prime), 8 Gen 2: 3200000,
            //   8 Gen 1: 3000000, 888: 2840000, 870: 3200000, 855: 2840000.
            hw.contains("qcom") -> -1  // sysfs fallback
            // ── Samsung Exynos ────────────────────────────────────────
            hw.contains("exynos2400") -> 3_210_000   // S24 (Korea/EU)
            hw.contains("exynos2200") -> 2_800_000   // S22
            hw.contains("exynos9820") -> 2_730_000   // S10 5G prime
            hw.contains("exynos9810") -> 2_700_000   // S9
            hw.contains("exynos8895") -> 2_316_000   // S8
            // ── Apple silicon: only relevant if running on iPad/macOS
            // ports; not applicable to Android scans, leave -1.
            else -> -1
        }
    }

    /**
     * Reads cpuinfo_max_freq from /sys/devices/system/cpu/cpuN/cpufreq/
     * across all online CPUs and returns the maximum value found. Used
     * as fallback when [getSocNominalMaxFreq] returns -1 (unknown SoC,
     * e.g. any Snapdragon variant).
     *
     * The kernel-reported max is the unthrottled ceiling — what the
     * governor *can* reach, not what it currently schedules. So this
     * is a valid baseline for the freq-ratio calculation.
     */
    fun readSysCpuFreqMax(): Int {
        var maxKhz = -1
        try {
            // Probe up to 16 CPUs (covers all current ARM SoCs).
            for (cpu in 0 until 16) {
                val path = "/sys/devices/system/cpu/cpu$cpu/cpufreq/cpuinfo_max_freq"
                val file = File(path)
                if (!file.exists()) break  // sequential numbering, stop at first gap
                runCatching {
                    val raw = file.readText().trim()
                    val khz = raw.toIntOrNull() ?: 0
                    if (khz > maxKhz) maxKhz = khz
                }.onFailure {
                    Log.w(TAG, "readSysCpuFreqMax cpu$cpu failed: ${it.message}")
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "readSysCpuFreqMax failed: ${t.message}")
        }
        return maxKhz
    }

    /**
     * V2 fix #56-3 (fieldsVersion 58): reads cpuinfo_max_freq per-CPU and
     * returns the kHz value for each CPU index. Used by [getPerClusterMaxKhz]
     * to derive per-cluster nominal frequencies. Returns null on enumeration
     * failure (no /sys/devices/system/cpu, sysfs locked).
     *
     * Sized to the actual online CPU count, not the 16-CPU probe ceiling
     * used by [readSysCpuFreqMax] — this lets downstream cluster grouping
     * know exactly which CPU indices exist. CPUs that fail to read (offline
     * at probe time, or sysfs locked) are stored as -1L in the array.
     */
    fun readPerCpuMaxFreqKhz(): LongArray? {
        val freqs = mutableListOf<Long>()
        try {
            for (cpu in 0 until 16) {
                val path = "/sys/devices/system/cpu/cpu$cpu/cpufreq/cpuinfo_max_freq"
                val file = File(path)
                if (!file.exists()) break  // sequential numbering, stop at first gap
                val khz = runCatching {
                    file.readText().trim().toLongOrNull() ?: -1L
                }.getOrDefault(-1L)
                freqs += khz
            }
        } catch (t: Throwable) {
            Log.w(TAG, "readPerCpuMaxFreqKhz failed: ${t.message}")
            return null
        }
        return if (freqs.isEmpty()) null else freqs.toLongArray()
    }

    /**
     * V2 fix #56-3 (fieldsVersion 58): groups [perCpuMaxFreqKhz] by distinct
     * value to derive per-cluster nominal max frequencies. Returns Triple
     * (big, mid, little) in kHz. Each slot is null when:
     *   - The cluster doesn't exist on this SoC (e.g. mid is null on a
     *     2-cluster SoC like Snapdragon 855's 1+3+4 misclassified as 2+4).
     *   - All CPUs in that cluster failed to read (-1L in the array).
     *
     * Algorithm: collect distinct positive freq values, sort descending.
     *   - 3 distinct values → [big, mid, little]
     *   - 2 distinct values → [big, null, little]
     *   - 1 distinct value  → [big, null, null] (uniform SoC, rare)
     *
     * Mirrors the C++ logic in detect_cluster_topology() so per-cluster
     * nominal here matches what the NDK pinning code uses for affinity.
     */
    fun getPerClusterMaxKhz(perCpuMaxFreqKhz: LongArray?): Triple<Int?, Int?, Int?> {
        if (perCpuMaxFreqKhz == null || perCpuMaxFreqKhz.isEmpty()) {
            return Triple(null, null, null)
        }
        val distinct = perCpuMaxFreqKhz
            .filter { it > 0L }
            .toSet()
            .sortedDescending()
        return when (distinct.size) {
            0 -> Triple(null, null, null)
            1 -> Triple(distinct[0].toInt(), null, null)
            2 -> Triple(distinct[0].toInt(), null, distinct[1].toInt())
            else -> Triple(distinct[0].toInt(), distinct[1].toInt(), distinct.last().toInt())
        }
    }

    /**
     * V2 fix #56-3 (fieldsVersion 58): returns cluster-id (0=little, 1=mid,
     * 2=big) for each CPU index, derived from [perCpuMaxFreqKhz]. CPUs that
     * failed to read (-1L) get cluster-id -1 so downstream code can skip them.
     *
     * Useful for the StressEffectivenessCalculator to bucket per-CPU usage
     * data into per-cluster averages without re-deriving the cluster cuts.
     */
    fun assignClusterIds(perCpuMaxFreqKhz: LongArray?): IntArray {
        if (perCpuMaxFreqKhz == null) return IntArray(0)
        val (big, mid, little) = getPerClusterMaxKhz(perCpuMaxFreqKhz)
        return IntArray(perCpuMaxFreqKhz.size) { i ->
            val freq = perCpuMaxFreqKhz[i]
            when {
                freq <= 0L -> -1
                big != null && freq.toInt() == big -> 2
                mid != null && freq.toInt() == mid -> 1
                little != null && freq.toInt() == little -> 0
                else -> -1
            }
        }
    }
}
