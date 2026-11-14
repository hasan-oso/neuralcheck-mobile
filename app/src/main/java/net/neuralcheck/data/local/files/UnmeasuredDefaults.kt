package net.neuralcheck.data.local.files

/**
 * The footer's `unmeasuredDefaults[]` declaration (fieldsVersion 65, extended in
 * 68 — T-18 rows 8 and 13, S-23): the names of footer fields whose value in THIS
 * file is a compile-time constant, not a measurement, so a consumer excludes
 * them from any device-quality judgement. A name with a dot is
 * `<object>.<field>` (a field inside a footer object).
 *
 * What belongs here is a constant by design that REMAINS in fieldsVersion 68.
 * What became an explicit state does not (the verification, pin, stillness and
 * pre-scan fields now say "not run" / "not attempted" / … themselves).
 */
object UnmeasuredDefaults {

    // ── fieldsVersion 65 ──
    const val DEVICE_TIER_MEASURED = "deviceTierMeasured"
    const val INTENSITY_FACTOR = "intensityFactor"
    const val TEMP_RISE_RATE_IN_PROBE = "tempRiseRateInProbe"

    // ── fieldsVersion 68 (row 8): the V5 schema moved the thermal channels out of v[]
    // and left these `dataQuality` fields as literals — "coverage % collapses to 0" and
    // the source tag `moved_to_footer_v5` (StressTestService.computeDataQualityFor).
    // They were shipped as if measured; they are declared, not deleted (deleting them is
    // a change of the dataQuality shape the contract did not decide).
    const val DQ_CPU_TEMP_COVERAGE_PCT = "dataQuality.cpuTempCoveragePct"
    const val DQ_GPU_TEMP_COVERAGE_PCT = "dataQuality.gpuTempCoveragePct"
    const val DQ_SKIN_TEMP_COVERAGE_PCT = "dataQuality.skinTempCoveragePct"
    const val DQ_CPU_TEMP_PRIMARY_SOURCE = "dataQuality.cpuTempPrimarySource"

    /** The V5-schema `dataQuality` literals above, which both paths (V1 and V2) write. */
    val DATA_QUALITY_CONSTANTS: List<String> = listOf(
        DQ_CPU_TEMP_COVERAGE_PCT,
        DQ_GPU_TEMP_COVERAGE_PCT,
        DQ_SKIN_TEMP_COVERAGE_PCT,
        DQ_CPU_TEMP_PRIMARY_SOURCE
    )

    /**
     * @param v2Ran whether the V2 stress path produced a result for this scan; on
     *   the V1 path no probe runs, so `tempRiseRateInProbe` is a constant 0.0 too.
     */
    fun forScan(v2Ran: Boolean): List<String> = buildList {
        add(DEVICE_TIER_MEASURED)
        add(INTENSITY_FACTOR)
        if (!v2Ran) add(TEMP_RISE_RATE_IN_PROBE)
        addAll(DATA_QUALITY_CONSTANTS)
    }
}
