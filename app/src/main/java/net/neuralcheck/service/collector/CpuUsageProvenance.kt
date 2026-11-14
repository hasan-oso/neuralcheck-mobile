package net.neuralcheck.service.collector

/**
 * fieldsVersion 66 — pure helpers that turn the sampler's v[6] provenance
 * telemetry into the two dataQuality fields. Kept free of Android types so
 * the contract is unit-testable.
 *
 * Background: [SystemFileReader.readCpuUsage] reads the global `/proc/stat`
 * line and, the first time that fails, flips a STICKY flag and serves the
 * process-level fallback (`Process.getElapsedCpuTime` ÷ wall × cores — this
 * app's own CPU share) for the rest of the scan. Builds ≤65 reported
 * `dataQuality.procStatAvailable = (any v[6] > 0)`, which is true in both
 * modes and therefore said nothing about the source.
 */
object CpuUsageProvenance {
    const val SOURCE_PROC_STAT = "procstat"
    const val SOURCE_PROCESS_FALLBACK = "process_fallback"

    /** Label for the reader's end-of-scan state (the fallback is sticky). */
    fun sourceLabel(readerStillOnProcStat: Boolean): String =
        if (readerStillOnProcStat) SOURCE_PROC_STAT else SOURCE_PROCESS_FALLBACK

    /**
     * "At least one sample produced /proc/stat-based usage" — the documented
     * meaning of dataQuality.procStatAvailable, now derived honestly:
     *   fallbackFromSample == -1 → never fell back → true
     *   fallbackFromSample ==  0 → fell back at/before the first sample → false
     *   fallbackFromSample  >  0 → samples [0, index) were /proc/stat → true
     */
    fun procStatEverUsed(fallbackFromSample: Int): Boolean = fallbackFromSample != 0
}
