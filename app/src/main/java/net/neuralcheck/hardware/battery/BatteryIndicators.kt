package net.neuralcheck.hardware.battery

import net.neuralcheck.data.local.files.TimeSeriesSample
import net.neuralcheck.service.collector.BatteryHealthSnapshot
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * 6 behavioral battery indicators measured during the actual scan, replacing
 * the discredited `sohOfficialPercent` single-point estimator that produced
 * impossible values like 102 % on the Samsung Galaxy S24 Ultra and 100 % on
 * a 2019 S10 5G. Accurate State-of-Health requires weeks of cycle tracking
 * (AccuBattery methodology) which is incompatible with an 8-minute one-shot
 * scan; this struct reports what we can ACTUALLY measure in the window we
 * have.
 *
 * See `docs/architecture/BATTERY_INDICATORS.md` for the public schema, the
 * healthy-range table, and the rationale the AI uses to combine these
 * signals.
 *
 * Field semantics:
 *  • [designCapacityMah] — manufacturer's declared capacity (from sysfs /
 *    PowerProfile reflection). Stable for the device, no measurement noise.
 *  • [cycleCount] — charge-cycle count from EXTRA_CYCLE_COUNT of the battery
 *    broadcast (API 34+) or sysfs (vendor-dependent), read by the one reader
 *    BatteryCycleCount.read (fieldsVersion 68, T-02). null on Android ≤ 13 /
 *    vendors that don't expose it; then cycleCountState says why. Best single
 *    SoH signal when available.
 *  • [cycleCountSource] — provenance tag ("extra_api34" /
 *    "sysfs:<path>" / "unavailable").
 *  • [dischargeRateUnderLoadMa] — average |battery_current_ua| across V2
 *    phases 3-5 (CPU sustained + GPU sustained + combined peak), divided
 *    by 1000 to get milliamps. Healthy flagships at sustained load draw
 *    1500-3500 mA; a battery losing capacity often shows a HIGHER current
 *    for the same delivered power because internal resistance has risen.
 *  • [batteryTempStartC] — battery temperature at the first sample.
 *    Mirrored from the header for AI-side convenience.
 *  • [batteryTempMaxC] — highest battery_temp_c observed during the scan.
 *  • [batteryTempRiseC] — `batteryTempMaxC - batteryTempStartC`. A battery
 *    in good health rises 2-6 °C under flagship-class stress; ≥8 °C
 *    suggests thermal degradation. fieldsVersion 68: `null` (with
 *    [batteryTempRiseCState]) when either temperature was unavailable — it
 *    was 0.0 °C, "the battery did not warm" — and WITH ITS SIGN otherwise (no
 *    clamp: a negative rise is start > max, sensor drift, shipped as it is).
 *  • [voltageStabilityMv] — population std-dev of battery_voltage_mv across
 *    V2 phases 3-5. Aged cells sag under load, raising the variance
 *    measurably. < 50 mV is healthy; > 100 mV warrants attention.
 *  • [levelDropPct] — `batteryLevelStart - batteryLevelEnd`, WITH ITS SIGN
 *    (fieldsVersion 68: no clamp — a negative value is a battery that gained
 *    charge during the scan, which the old `coerceAtLeast(0)` turned into "0 %
 *    used"); `null` (with [levelDropPctState]) when a level was never read.
 *    Typically 3-10 % over an 8-minute scan.
 *  • [dischargeVsDesignRatioMah] — `(levelDropPct / 100) × designCapacityMah`,
 *    i.e. the estimated mAh consumed during the scan if the battery still
 *    holds rated capacity. Cross-checked by the AI against actual current
 *    integration to flag capacity loss.
 */
data class BatteryRealIndicators(
    /** `null` = no design capacity found (designCapacitySource says so); it was 0. */
    val designCapacityMah: Int?,
    val cycleCount: Int?,
    val cycleCountSource: String,
    /** `null` = no non-zero current reading in the load window; see [dischargeRateUnderLoadMaState]. */
    val dischargeRateUnderLoadMa: Double?,
    val dischargeRateUnderLoadMaState: String,
    /** `null` = unavailable (see [batteryTempRiseCState]); it was 0.0. */
    val batteryTempStartC: Double?,
    /** `null` = unavailable (see [batteryTempRiseCState]); it was 0.0. */
    val batteryTempMaxC: Double?,
    /** `null` = start or max temperature unavailable; see [batteryTempRiseCState]. It was 0.0. */
    val batteryTempRiseC: Double?,
    val batteryTempRiseCState: String,
    /** `null` = fewer than 2 voltage readings in the load window; see [voltageStabilityMvState]. */
    val voltageStabilityMv: Double?,
    val voltageStabilityMvState: String,
    /** Signed, unclamped: negative = the battery gained charge. `null` = a level was never read; see [levelDropPctState]. */
    val levelDropPct: Int?,
    val levelDropPctState: String,
    /** `null` = no design capacity or no battery level; see [dischargeVsDesignRatioMahState]. */
    val dischargeVsDesignRatioMah: Double?,
    val dischargeVsDesignRatioMahState: String
)

/**
 * fieldsVersion 68 (T-18 row 9): the state of an indicator that may be absent —
 * `computed` | `not_computed:<reason>`. The reasons are the agent's (not yet
 * approved): see [BatteryIndicators].
 */
object IndicatorStates {
    const val COMPUTED = "computed"
    const val NOT_COMPUTED_PREFIX = "not_computed:"

    const val REASON_NO_CURRENT_IN_LOAD_WINDOW = "no_current_reading_in_load_window"
    const val REASON_FEWER_THAN_2_VOLTAGES = "fewer_than_2_voltage_readings_in_load_window"
    const val REASON_DESIGN_CAPACITY_UNAVAILABLE = "design_capacity_unavailable"
    const val REASON_BATTERY_LEVEL_UNAVAILABLE = "battery_level_unavailable"
    const val REASON_TEMP_START_UNAVAILABLE = "battery_temp_start_unavailable"
    const val REASON_TEMP_MAX_UNAVAILABLE = "battery_temp_max_unavailable"
    const val REASON_TEMP_START_AND_MAX_UNAVAILABLE = "battery_temp_start_and_max_unavailable"

    fun notComputed(reason: String): String = NOT_COMPUTED_PREFIX + reason
}

/**
 * Pure-function builder for [BatteryRealIndicators]. No Android Context, no
 * file-system access, no SystemFileReader dependency — everything is
 * computed from the in-memory `samples` buffer and the pre-built
 * [BatteryHealthSnapshot] / battery level + temperature scalars the call
 * site already has in scope.
 *
 * Phase filter: V2 phases 3-5 (`CPU sustained`, `GPU sustained`,
 * `combined peak`) — the sustained-stress window where the battery is
 * delivering its hardest sustained current. Init / probe / cooldown
 * phases are excluded because their current draw is too low to
 * differentiate aged from healthy cells.
 *
 * Defensive contracts:
 *  • The V5 TimeSeriesSample layout puts battery_voltage_mv at `v[2]` and
 *    battery_current_ua at `v[3]`. The reader guards on `v.size > 3` so a
 *    legacy V1 sample (different layout) drops out silently rather than
 *    poisoning the average.
 *  • Samples reporting `0f` for either field are treated as "sensor not
 *    populated this tick" and excluded — most Android vendors fill an
 *    unavailable register with zero rather than a sentinel.
 *  • Nothing is clamped (fieldsVersion 68, user decisions of 2026-10-08): a
 *    negative temperature delta (start temp > max temp, sensor drift) and a
 *    negative LEVEL drop (the battery climbed — e.g. the user plugged in
 *    mid-scan) are each shipped as the negative number they are ("a negative
 *    battery-temperature rise ships with its sign, no clamp"; "levelDropPct
 *    ships with its sign, no clamp"). What a negative value means is the
 *    server's to judge.
 *  • fieldsVersion 68 (T-18 row 9, rule 22): an indicator that CANNOT be
 *    computed is `null` with a `<name>State` that says why — NOT a "well-defined
 *    0.0". A 0.0 average current, a 0.0 voltage spread ("perfectly stable") and
 *    a 0.0 consumed-capacity ratio are readings a model would trust; the
 *    absence of the data behind them is not. A computed value (including a real
 *    0.0) states `computed`. Every value stays finite: nothing is NaN/Inf.
 */
object BatteryIndicators {

    /** V2 phases [3, 4, 5] correspond to CPU sustained / GPU sustained /
     *  combined peak. These are the only phases where the workload is
     *  saturating the SoC, drawing the kind of current that exposes
     *  battery wear. Earlier phases (init/probe) and later phases
     *  (cooldown) draw too little to differentiate. */
    private val UNDER_LOAD_PHASES = setOf(3, 4, 5)

    /** V5 sample-array index for `battery_voltage_mv`. */
    private const val V_IDX_VOLTAGE_MV = 2

    /** V5 sample-array index for `battery_current_ua`. */
    private const val V_IDX_CURRENT_UA = 3

    /** V5 sample-array index for `battery_temp_c`. */
    private const val V_IDX_TEMP_C = 1

    fun compute(
        samples: List<TimeSeriesSample>,
        batterySnapshot: BatteryHealthSnapshot,
        batteryLevelStart: Int,
        batteryLevelEnd: Int,
        batteryTempStartC: Double,
        batteryTempMaxC: Double
    ): BatteryRealIndicators {
        // ── 1. Filter to V2 phases 3-5 (sustained-load window) ──
        val underLoadSamples = samples.filter { it.phase in UNDER_LOAD_PHASES }

        // ── 2. dischargeRateUnderLoadMa: |current_µA| / 1000 across load window ──
        val currentsMa = underLoadSamples
            .asSequence()
            .filter { it.v.size > V_IDX_CURRENT_UA }
            .map { it.v[V_IDX_CURRENT_UA].toDouble() }
            // 0 µA = "register not populated" on most vendors; filter to
            // non-zero readings only so a single missing-data tick doesn't
            // halve the average.
            .filter { it != 0.0 }
            .map { abs(it) / 1000.0 }
            .toList()
        // fieldsVersion 68: no reading is no average (it was 0.0 mA = "draws nothing").
        val dischargeRateMa: Double? =
            if (currentsMa.isEmpty()) null else currentsMa.average()
        val dischargeRateState = if (dischargeRateMa == null) {
            IndicatorStates.notComputed(IndicatorStates.REASON_NO_CURRENT_IN_LOAD_WINDOW)
        } else IndicatorStates.COMPUTED

        // ── 3. voltageStabilityMv: stddev of voltage_mv across load window ──
        val voltagesMv = underLoadSamples
            .asSequence()
            .filter { it.v.size > V_IDX_VOLTAGE_MV }
            .map { it.v[V_IDX_VOLTAGE_MV].toDouble() }
            .filter { it > 0.0 }
            .toList()
        // fieldsVersion 68: the spread of fewer than 2 readings is undefined (it
        // was 0.0 mV = "perfectly stable").
        val voltageStability: Double? =
            if (voltagesMv.size < 2) null else stddev(voltagesMv)
        val voltageStabilityState = if (voltageStability == null) {
            IndicatorStates.notComputed(IndicatorStates.REASON_FEWER_THAN_2_VOLTAGES)
        } else IndicatorStates.COMPUTED

        // ── 4. batteryTempRiseC: max - start, WITH its sign (fieldsVersion 68: no clamp) ──
        // fieldsVersion 68: an unavailable temperature (non-finite here; the service
        // passes NaN for a reading it never got) is no rise, not "0.0 °C".
        val startTemp: Double? = batteryTempStartC.takeIf { it.isFinite() }
        val maxTemp: Double? = batteryTempMaxC.takeIf { it.isFinite() }
        val tempRise: Double? =
            if (startTemp == null || maxTemp == null) null else (maxTemp - startTemp)
        val tempRiseState = when {
            startTemp == null && maxTemp == null -> IndicatorStates.notComputed(IndicatorStates.REASON_TEMP_START_AND_MAX_UNAVAILABLE)
            startTemp == null -> IndicatorStates.notComputed(IndicatorStates.REASON_TEMP_START_UNAVAILABLE)
            maxTemp == null -> IndicatorStates.notComputed(IndicatorStates.REASON_TEMP_MAX_UNAVAILABLE)
            else -> IndicatorStates.COMPUTED
        }

        // ── 5. levelDropPct: start - end, WITH its sign (fieldsVersion 68: no clamp) ──
        // A level that was never read is -1 (BatteryReader's "no broadcast"): the
        // difference of two such is a meaningless 0, and of one such a meaningless
        // 81 — so it is absent, with the reason.
        val levelsKnown = batteryLevelStart >= 0 && batteryLevelEnd >= 0
        val levelDrop: Int? = if (levelsKnown) batteryLevelStart - batteryLevelEnd else null
        val levelDropState = if (levelDrop == null) {
            IndicatorStates.notComputed(IndicatorStates.REASON_BATTERY_LEVEL_UNAVAILABLE)
        } else IndicatorStates.COMPUTED

        // ── 6. dischargeVsDesignRatioMah: % × design capacity ──
        // fieldsVersion 68: without a design capacity (or without a battery
        // level, where start/end are -1 and the "drop" above is a meaningless 0)
        // the ratio cannot be computed: null + the reason, not "0 mAh consumed".
        val designMah: Int? = batterySnapshot.designCapacityMah
        // (From the SIGNED drop: a battery that gained charge gives a negative figure, not
        // "0 mAh consumed". A transitional aggregate - the server judges from the raw.)
        val dischargeMah: Double? =
            if (designMah == null || levelDrop == null) null else (levelDrop / 100.0) * designMah
        val dischargeMahState = when {
            designMah == null -> IndicatorStates.notComputed(IndicatorStates.REASON_DESIGN_CAPACITY_UNAVAILABLE)
            !levelsKnown -> IndicatorStates.notComputed(IndicatorStates.REASON_BATTERY_LEVEL_UNAVAILABLE)
            else -> IndicatorStates.COMPUTED
        }

        return BatteryRealIndicators(
            designCapacityMah = designMah,
            cycleCount = batterySnapshot.cycleCount,
            cycleCountSource = batterySnapshot.cycleCountSource,
            dischargeRateUnderLoadMa = dischargeRateMa,
            dischargeRateUnderLoadMaState = dischargeRateState,
            batteryTempStartC = startTemp,
            batteryTempMaxC = maxTemp,
            batteryTempRiseC = tempRise,
            batteryTempRiseCState = tempRiseState,
            voltageStabilityMv = voltageStability,
            voltageStabilityMvState = voltageStabilityState,
            levelDropPct = levelDrop,
            levelDropPctState = levelDropState,
            dischargeVsDesignRatioMah = dischargeMah,
            dischargeVsDesignRatioMahState = dischargeMahState
        )
    }

    /**
     * The `footer.batteryHealth` map (the wire shape of the indicators), built in
     * ONE place so the service and the wire test run the same code. A value that
     * could not be computed is `null` here — the footer FILE writes it as an
     * explicit null and the request payload omits the key — and the matching
     * `<name>State` key is always present and says `computed` or
     * `not_computed:<reason>` (fieldsVersion 68, T-18 row 9).
     *
     * `chargeCounterUah` / `energyCounterNwh` are kept for backward
     * compatibility with consumers that still read raw counter data (B-02
     * fallback); they are NOT used for SoH grading.
     */
    fun footerMap(real: BatteryRealIndicators, health: BatteryHealthSnapshot): Map<String, Any?> = mapOf(
        "designCapacityMah" to real.designCapacityMah,
        "designCapacitySource" to health.designCapacitySource,
        "cycleCount" to real.cycleCount,
        "cycleCountSource" to real.cycleCountSource,
        // fieldsVersion 68 (T-18 row 10): the three readings below ship AS READ, a 0
        // included; their state (`read` | `not_read:<reason>`) is what tells a real 0
        // from a device that reports nothing. The server judges.
        "cycleCountState" to health.cycleCountState,
        "dischargeRateUnderLoadMa" to real.dischargeRateUnderLoadMa,
        "dischargeRateUnderLoadMaState" to real.dischargeRateUnderLoadMaState,
        "batteryTempStartC" to real.batteryTempStartC,
        "batteryTempMaxC" to real.batteryTempMaxC,
        "batteryTempRiseC" to real.batteryTempRiseC,
        "batteryTempRiseCState" to real.batteryTempRiseCState,
        "voltageStabilityMv" to real.voltageStabilityMv,
        "voltageStabilityMvState" to real.voltageStabilityMvState,
        "levelDropPct" to real.levelDropPct,
        "levelDropPctState" to real.levelDropPctState,
        "dischargeVsDesignRatioMah" to real.dischargeVsDesignRatioMah,
        "dischargeVsDesignRatioMahState" to real.dischargeVsDesignRatioMahState,
        "chargeCounterUah" to health.chargeCounterUah,
        "chargeCounterUahState" to health.chargeCounterUahState,
        "energyCounterNwh" to health.energyCounterNwh,
        "energyCounterNwhState" to health.energyCounterNwhState
    )

    /**
     * V5 sample-array index for `battery_temp_c` — exposed for the call
     * site that needs to compute `batteryTempMaxC` from the raw sample
     * buffer (SamplerStats keeps `maxBattTempC` private, so the call
     * site does its own scan).
     */
    fun batteryTempIndex(): Int = V_IDX_TEMP_C

    /** Population standard deviation. */
    private fun stddev(values: List<Double>): Double {
        val mean = values.average()
        val variance = values.fold(0.0) { acc, v ->
            val d = v - mean
            acc + d * d
        } / values.size
        return sqrt(variance)
    }
}
