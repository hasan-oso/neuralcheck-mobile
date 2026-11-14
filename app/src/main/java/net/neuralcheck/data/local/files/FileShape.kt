package net.neuralcheck.data.local.files

/**
 * What a time-series file written BEFORE fieldsVersion 68 cannot carry
 * (fieldsVersion 68, batch 3 part 2-b, item "legacy file").
 *
 * The payload builder reads the file by fixed names (rule 25): every name it
 * asks for and does not find must be declared, not filled in. This is the one
 * source (rule 24) of those names, so the declaration and the tests read the
 * same list.
 *
 * The STAMP decides, because it is the one thing in the footer that names the
 * shape: a footer stamped below [STATES_STAMP] was written by a build that had
 * none of the fields below, whatever Gson later put in their place (Gson builds
 * `VerificationSummary` through its no-arg constructor, so a missing key comes
 * back as the Kotlin default — and the defaults are "nothing ran" states, a
 * claim that is FALSE for a scan that did run).
 */
object FileShape {

    /** The first stamp whose files carry the explicit-state fields below. */
    const val STATES_STAMP: Int = 68

    /** The first stamp whose files carry the battery window and the brightness lock (fieldsVersion 71, A-P6-13; the header also gains `powerSaveState`). */
    const val WINDOW_STAMP: Int = 71

    /** Footer-level fields that exist only in files stamped >= [WINDOW_STAMP]. */
    val FOOTER_FIELDS_SINCE_71: List<String> = listOf("batteryWindowPct", "brightnessLock")

    /** Footer-level fields that exist only in files stamped >= [STATES_STAMP]. */
    val FOOTER_FIELDS_SINCE_68: List<String> = listOf(
        "cpuPolicies",
        "channelReadFailures",
        "channelReadFailureCounts",
        "thermalHeadroomReadings",
        "thermalHeadroomState",
        "thermalStatusEvents",
        "thermalStatusState",
        "batteryPairs",
        "batteryPairsState",
        "transitionalAggregates",
        "cpuTempFinalC",
        "cpuTempFinalSource",
        "preScanWarningsState"
    )

    /** Fields inside footer objects that exist only in files stamped >= [STATES_STAMP]. */
    val NESTED_FIELDS_SINCE_68: List<String> = listOf(
        "channelDiscovery.reasons",
        "scanStillness.stillnessState",
        "batteryHealth.dischargeRateUnderLoadMaState",
        "batteryHealth.voltageStabilityMvState",
        "batteryHealth.dischargeVsDesignRatioMahState",
        "batteryHealth.cycleCountState",
        "batteryHealth.chargeCounterUahState",
        "batteryHealth.energyCounterNwhState",
        "batteryHealth.batteryTempRiseCState",
        "batteryHealth.levelDropPctState"
    )

    /** The prefix `fieldsNotInFile` gives a `verificationResults` key. */
    const val VERIFICATION_PREFIX: String = "verificationResults."

    /** True when a footer with this stamp predates the explicit-state fields. */
    fun predatesStates(fieldsVersion: Int): Boolean = fieldsVersion < STATES_STAMP
}
