package net.neuralcheck.data.local.files

/**
 * fieldsVersion 68 (T-10, S-13, S-33, N-08; order of batch 3 part 2-a): the
 * footer's CPU temperature stops being called a MAXIMUM when it is not one.
 *
 * `footer.maxCpuTemp` (≤ 67) held, depending on a three-branch cascade
 * (StressTestService.selectFooterCpuTemp):
 *   1. a LIVE read taken at finalize, after the cooldown — the last reading,
 *      not a maximum (and the sampler's own maximum can be 40 °C higher on the
 *      same scan: the S24 Ultra scan 18dfd6b4 had 81 °C vs 43.6 °C);
 *   2. otherwise the sampler's maximum — a real maximum, but an on-device
 *      aggregate over samples;
 *   3. otherwise `-1f`, a sentinel that looked like a temperature.
 * The name said "max" for all three. S-33 decided: ONE field, whose SOURCE is
 * declared — [cpuTempFinalC] and [cpuTempFinalSource] replace `maxCpuTemp`.
 * T-01 keeps using the same cascade's value; its behavior does not change.
 */
data class FooterCpuTemp(
    /** The temperature in °C, or `null` when no trustworthy CPU reading exists (then [source] says why). */
    val valueC: Float?,
    /** `live_read_at_finalize` | `sampler_max_cpu` | `absent:<reason>` — see [CpuTempSources]. */
    val source: String
) {
    /**
     * The value in the form T-01 / T-02 / T-03 consume: `-1f` = "no trustworthy
     * reading". That sentinel belongs to those on-device tests' interface only
     * (they map it to UNAVAILABLE); it is no longer written to the footer.
     */
    fun forT01(): Float = valueC ?: -1f
}

/** The literals of [FooterCpuTemp.source], one source (rule 24). */
object CpuTempSources {
    /** The value is a fresh `readCpuTemp()` taken when the scan was finalised — the last reading, not a maximum. */
    const val LIVE_READ_AT_FINALIZE = "live_read_at_finalize"

    /** The value is the sampler's maximum over the scan, accepted only when its source class is `cpu`. */
    const val SAMPLER_MAX_CPU = "sampler_max_cpu"

    /** Prefix of `absent:<reason>`. */
    const val ABSENT_PREFIX = "absent:"

    /** The payload had no time series at all (contract N-08). */
    const val ABSENT_NO_TIME_SERIES_FILE = "absent:no_time_series_file"
}
