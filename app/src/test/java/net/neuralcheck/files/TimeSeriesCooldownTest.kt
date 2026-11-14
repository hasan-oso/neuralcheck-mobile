package net.neuralcheck.files

import android.content.Context
import net.neuralcheck.data.local.files.TimeSeriesData
import net.neuralcheck.data.local.files.TimeSeriesFileReader
import net.neuralcheck.data.local.files.TimeSeriesFooter
import net.neuralcheck.data.local.files.TimeSeriesHeader
import net.neuralcheck.data.local.files.ChannelNames
import net.neuralcheck.data.local.files.TimeSeriesSample
import net.neuralcheck.data.local.files.TimeSeriesVariables
import net.neuralcheck.data.local.files.V2_SAMPLE_VAR_COUNT
import net.neuralcheck.data.local.files.V2_VARIABLE_DEFINITIONS
import net.neuralcheck.data.local.files.VariableDefinition
import net.neuralcheck.domain.model.TestStatus
import net.neuralcheck.hardware.thermal.ThermalTests
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import kotlin.math.abs

/**
 * Two-layer regression guard for T-03 (Soğutma Hızı / cooling rate):
 *
 * 1. Rate computation — locked to V2 phase 7 (COOLDOWN) on both schemas
 *    (the V2 layout has no cpu_temp_c column and reads battery_temp_c; the
 *    legacy V1/V4 layout has cpu_temp_c and reads that). Columns are resolved
 *    BY NAME from variables[] (fieldsVersion 68) — never from the list
 *    length, which now differs between devices (base 11 + per-device
 *    channels).
 *    `Float.NaN` is the canonical "no data" sentinel so 0 °C/min and
 *    negative °C/min remain distinguishable real measurements.
 *
 * 2. Threshold → status — the physics-realistic 0.3 / 0.0 cascade for
 *    the ~60 s phase-7 cooldown window. Replaces the previous
 *    1.5 / 0.5 thresholds that were physically unreachable in this
 *    window and graded every healthy device as FAIL.
 *
 * Empirical anchor: Samsung S10 5G scan 4b634973 phase 7 measured
 * 36.20 → 36.20 °C ⇒ rate ≈ 0.00 °C/min, which is WARNING (not FAIL)
 * under the new thresholds — a healthy device with very limited
 * measurable cooling over 60 s, exactly as Newton's Law of Cooling
 * predicts for a 36 °C body in a 28 °C ambient.
 */
class TimeSeriesCooldownTest {

    // ─────────────────── Rate computation tests ───────────────────

    /** V5 fingerprint with a clean cooldown drop on phase 7. */
    @Test
    fun `v5 schema computes cooldown rate from phase 7 battery temp drop`() {
        // Phase 5 here would be the WRONG target — temperature climbing.
        // Phase 7 is the actual cooldown — temperature dropping ~1.3 °C/min.
        val samples = listOf(
            // Phase 5 (COMBINED_PEAK) — temp rising. Pre-fix this was read
            // as the cooldown window and would have produced rate ≈ 0.
            sampleV5(tsMs = 300_000L, phase = 5, battTempC = 38.4f),
            sampleV5(tsMs = 330_000L, phase = 5, battTempC = 38.55f),
            sampleV5(tsMs = 360_000L, phase = 5, battTempC = 38.7f),
            // Phase 7 (COOLDOWN) — temp falling. Should drive the rate.
            sampleV5(tsMs = 420_000L, phase = 7, battTempC = 39.9f),
            sampleV5(tsMs = 450_000L, phase = 7, battTempC = 39.2f),
            sampleV5(tsMs = 480_000L, phase = 7, battTempC = 38.6f)
        )
        val data = makeV5Data(samples)
        val summary = TimeSeriesFileReader(mock<Context>()).calculateSummary(data)

        // Drop = 39.9 − 38.6 = 1.3 °C over (480_000 − 420_000) / 60_000 = 1.0 min
        // ⇒ 1.30 °C/min. Permit a small tolerance for float math.
        assertTrue(
            "Expected cooldown rate ≈ 1.30 °C/min, got ${summary.cooldownRatePerMin}",
            abs(summary.cooldownRatePerMin - 1.30f) < 0.01f
        )
    }

    /** Pre-V5 (legacy 18-channel) fingerprint keeps reading cpu_temp at v[5]. */
    @Test
    fun `legacy v1 schema reads cpu_temp_c at v5 for phase 7 cooldown rate`() {
        val samples = listOf(
            sampleLegacy(tsMs = 420_000L, phase = 7, cpuTempC = 55.0f),
            sampleLegacy(tsMs = 450_000L, phase = 7, cpuTempC = 53.5f),
            sampleLegacy(tsMs = 480_000L, phase = 7, cpuTempC = 52.0f)
        )
        val data = makeLegacyData(samples)
        val summary = TimeSeriesFileReader(mock<Context>()).calculateSummary(data)

        // Drop = 3.0 °C over 1 min ⇒ 3.0 °C/min.
        assertTrue(
            "Expected cooldown rate ≈ 3.00 °C/min, got ${summary.cooldownRatePerMin}",
            abs(summary.cooldownRatePerMin - 3.00f) < 0.01f
        )
    }

    /**
     * If phase 5 is the ONLY phase with cooldown-shaped data, the reader
     * must IGNORE it (phase-7 filter is correct) and emit NaN to signal
     * "no phase-7 data" — NOT 0, because 0 is a valid real measurement
     * (slow cooling) under the new threshold scheme.
     */
    @Test
    fun `v5 phase 5 only data yields NaN cooldown rate`() {
        val samples = listOf(
            sampleV5(tsMs = 300_000L, phase = 5, battTempC = 40.0f),
            sampleV5(tsMs = 360_000L, phase = 5, battTempC = 38.0f)
        )
        val data = makeV5Data(samples)
        val summary = TimeSeriesFileReader(mock<Context>()).calculateSummary(data)

        assertTrue(
            "Expected NaN for no phase-7 data, got ${summary.cooldownRatePerMin}",
            summary.cooldownRatePerMin.isNaN()
        )
    }

    /** No samples at all → NaN (matches the empty-input contract). */
    @Test
    fun `empty samples list returns NaN cooldown rate`() {
        val data = makeV5Data(emptyList())
        val summary = TimeSeriesFileReader(mock<Context>()).calculateSummary(data)
        assertTrue(
            "Expected NaN for empty samples, got ${summary.cooldownRatePerMin}",
            summary.cooldownRatePerMin.isNaN()
        )
    }

    // ─────────────────── fieldsVersion 68: resolve by NAME ───────────────────
    //
    // These replace the old size-based detection test (a V5 file was the one
    // with `variables.size == 11`). The length of variables[] is now
    // 11 + (per-device channels that passed discovery), different on every
    // device, so the length can no longer say which layout a file has.

    /** Real 2-policy device: base 11 + charge counter + 2 policy frequencies = 14 columns. */
    private val v2WithExtras = listOf(
        ChannelNames.CHARGE_COUNTER_RAW,
        ChannelNames.cpuFreqPolicy(0),
        ChannelNames.cpuFreqPolicy(4)
    )

    @Test
    fun `v2 file with appended per-device channels still computes the cooldown rate from battery temp`() {
        val samples = listOf(
            sampleV5(tsMs = 420_000L, phase = 7, battTempC = 39.9f, extraWidth = 3),
            sampleV5(tsMs = 450_000L, phase = 7, battTempC = 39.2f, extraWidth = 3),
            sampleV5(tsMs = 480_000L, phase = 7, battTempC = 38.6f, extraWidth = 3)
        )
        val data = makeV5Data(samples, extraNames = v2WithExtras)
        assertEquals("the length is 14, not 11 and not 18", 14, data.variables.size)

        val summary = TimeSeriesFileReader(mock<Context>()).calculateSummary(data)

        assertTrue(
            "Expected ≈ 1.30 °C/min from battery_temp_c, got ${summary.cooldownRatePerMin}",
            abs(summary.cooldownRatePerMin - 1.30f) < 0.01f
        )
        assertTrue("every needed column is declared", summary.unresolvedColumns.isEmpty())
    }

    @Test
    fun `a v2 file whose length happens to be 18 is still read as v2 - not as the legacy layout`() {
        // base 11 + charge counter + 6 policies = 18 columns. A length test
        // would route this to the legacy branch and read v[5]
        // (cpu_freq_max_khz = 99_999 here) as a temperature.
        val extras = listOf(ChannelNames.CHARGE_COUNTER_RAW) + (0 until 6).map { ChannelNames.cpuFreqPolicy(it) }
        val samples = listOf(
            sampleV5(tsMs = 420_000L, phase = 7, battTempC = 39.9f, extraWidth = extras.size),
            sampleV5(tsMs = 480_000L, phase = 7, battTempC = 38.6f, extraWidth = extras.size)
        )
        samples.forEach { it.v[5] = 99_999f }
        val data = makeV5Data(samples, extraNames = extras)
        assertEquals(18, data.variables.size)

        val summary = TimeSeriesFileReader(mock<Context>()).calculateSummary(data)

        assertTrue(
            "cooldown must come from battery_temp_c (1.30), got ${summary.cooldownRatePerMin}",
            abs(summary.cooldownRatePerMin - 1.30f) < 0.01f
        )
        assertEquals("cpu_temp_c is not a column of the V2 layout", -1f, summary.maxCpuTemp, 0f)
    }

    @Test
    fun `cpu usage and gpu fps are read from their named columns under a longer layout`() {
        val samples = listOf(
            sampleV5(tsMs = 0L, phase = 3, battTempC = 30f, extraWidth = 3),
            sampleV5(tsMs = 60_000L, phase = 3, battTempC = 31f, extraWidth = 3)
        )
        samples[0].v[6] = 40f; samples[1].v[6] = 80f    // cpu_usage_pct
        samples[0].v[10] = 55f; samples[1].v[10] = 61f  // gpu_fps
        val data = makeV5Data(samples, extraNames = v2WithExtras)

        val summary = TimeSeriesFileReader(mock<Context>()).calculateSummary(data)

        assertEquals(60f, summary.avgCpuUsage, 0.001f)
        assertEquals(80f, summary.maxCpuUsage, 0.001f)
        assertEquals(58f, summary.avgGpuFps, 0.001f)
        assertEquals(60_000f, summary.durationMs, 0.001f)
    }

    @Test
    fun `a column the file does not declare is listed as unresolved and its metric stays absent`() {
        // A file whose variables[] lacks gpu_fps and cpu_usage_pct (e.g. a
        // truncated or foreign schema): the reader says so instead of reading
        // whatever sits at v[10] / v[6].
        val variables = V2_VARIABLE_DEFINITIONS.filterNot {
            it.name == ChannelNames.GPU_FPS || it.name == ChannelNames.CPU_USAGE_PCT
        }
        val samples = listOf(
            sampleV5(tsMs = 0L, phase = 3, battTempC = 30f),
            sampleV5(tsMs = 60_000L, phase = 3, battTempC = 31f)
        )
        samples.forEach { it.v[10] = 77f; it.v[6] = 66f }
        val data = TimeSeriesData(makeHeader(), variables, samples, makeFooter())

        val summary = TimeSeriesFileReader(mock<Context>()).calculateSummary(data)

        assertEquals(
            listOf(ChannelNames.CPU_USAGE_PCT, ChannelNames.GPU_FPS),
            summary.unresolvedColumns
        )
        assertEquals("absent, not the 77 sitting at v[10]", 0f, summary.maxGpuFps, 0f)
        assertEquals("absent, not the 66 sitting at v[6]", 0f, summary.maxCpuUsage, 0f)
    }

    @Test
    fun `a file with no variables at all declares every needed column unresolved`() {
        val samples = listOf(
            sampleV5(tsMs = 0L, phase = 7, battTempC = 30f),
            sampleV5(tsMs = 60_000L, phase = 7, battTempC = 29f)
        )
        val data = TimeSeriesData(makeHeader(), emptyList(), samples, makeFooter())

        val summary = TimeSeriesFileReader(mock<Context>()).calculateSummary(data)

        assertEquals(
            listOf(
                ChannelNames.TIMESTAMP_MS,
                ChannelNames.BATTERY_TEMP_C,
                ChannelNames.BATTERY_LEVEL_PCT,
                ChannelNames.CPU_USAGE_PCT,
                ChannelNames.GPU_FPS
            ),
            summary.unresolvedColumns
        )
        assertTrue("no battery_temp_c column => no cooldown rate", summary.cooldownRatePerMin.isNaN())
    }

    // ─────────────────── Threshold → status tests ───────────────────

    // T-03 demoted to informational on 2026-05-14 — the four-device tester
    // run (S10 5G −0.91, S24 Ultra +0.20, S21 Ultra 0.00, A51 −0.30 °C/min)
    // showed even a HEALTHY mid-range failed under physics-tuned thresholds.
    // The 60-second phase-7 window can't yield a reliable cooling rate
    // across the device population, so every finite measurement now
    // grades PASS and the report carries an informational note instead.
    // NaN (no data) keeps mapping to UNAVAILABLE.

    /** 0.50 °C/min — healthy cooling, PASS. */
    @Test
    fun `informational rate 0_5 cmin is PASS`() {
        val result = thermalTests().computeCooldown(rate = 0.5f, tempSource = "battery")
        assertEquals(TestStatus.PASS, result.status)
    }

    /** 0.10 °C/min — slow but real cooling, still PASS under informational mode. */
    @Test
    fun `informational rate 0_1 cmin is PASS`() {
        val result = thermalTests().computeCooldown(rate = 0.1f, tempSource = "battery")
        assertEquals(TestStatus.PASS, result.status)
    }

    /** 0.00 °C/min — the S10 5G empirical case from 1.0.7 testing. PASS now. */
    @Test
    fun `informational rate zero is PASS`() {
        val result = thermalTests().computeCooldown(rate = 0.0f, tempSource = "battery")
        assertEquals(TestStatus.PASS, result.status)
    }

    /**
     * −0.20 °C/min — temperature rose during cooldown. Under informational
     * mode this is no longer FAIL because the 60 s window measurement is
     * unreliable across devices. The rate value is still reported for AI
     * consumption (so the model can apply its own judgment), but T-03 no
     * longer pulls the thermal category down on its own.
     */
    @Test
    fun `informational rate negative is PASS not FAIL`() {
        val result = thermalTests().computeCooldown(rate = -0.2f, tempSource = "battery")
        assertEquals(TestStatus.PASS, result.status)
    }

    /**
     * Float.NaN sentinel = "no phase-7 data" → UNAVAILABLE.
     * The new sentinel replaces the previous `-1f` so a real
     * measurement of −1.0 °C/min would still grade FAIL (not collide
     * with UNAVAILABLE).
     */
    @Test
    fun `NaN rate is UNAVAILABLE`() {
        val result = thermalTests().computeCooldown(rate = Float.NaN, tempSource = "battery")
        assertEquals(TestStatus.UNAVAILABLE, result.status)
    }

    // ─────────────────── helpers ───────────────────

    /** [extraWidth] = per-device channels appended after the V2 base block. */
    private fun sampleV5(tsMs: Long, phase: Int, battTempC: Float, extraWidth: Int = 0): TimeSeriesSample =
        TimeSeriesSample(
            ts = tsMs,
            phase = phase,
            v = FloatArray(V2_SAMPLE_VAR_COUNT + extraWidth).apply {
                this[0] = tsMs.toFloat()  // timestamp_ms
                this[1] = battTempC       // battery_temp_c
                // remaining base channels stay at 0f — irrelevant for cooldown
                // computation. Appended channels (if any) get a value that would
                // wreck the result if a reader mistook one of them for a base
                // column.
                for (i in V2_SAMPLE_VAR_COUNT until size) this[i] = 99_999f
            }
        )

    private fun sampleLegacy(tsMs: Long, phase: Int, cpuTempC: Float): TimeSeriesSample =
        TimeSeriesSample(
            ts = tsMs,
            phase = phase,
            v = FloatArray(TimeSeriesVariables.definitions.size).apply {
                this[0] = tsMs.toFloat()  // timestamp_ms
                this[1] = 35.0f           // battery_temp_c (irrelevant here)
                this[5] = cpuTempC        // cpu_temp_c — V1/V4 position
            }
        )

    /**
     * V2 layout as a real scan writes it: the REAL base definitions, then
     * [extraNames] appended (charge counter, per-policy frequencies, ...).
     * The reader must resolve columns by NAME — the list length is not a
     * layout discriminator any more (fieldsVersion 68).
     */
    private fun makeV5Data(
        samples: List<TimeSeriesSample>,
        extraNames: List<String> = emptyList()
    ): TimeSeriesData {
        val variables = V2_VARIABLE_DEFINITIONS + extraNames.mapIndexed { i, name ->
            VariableDefinition(V2_SAMPLE_VAR_COUNT + i, name, "kHz", "test")
        }
        return TimeSeriesData(makeHeader(), variables, samples, makeFooter())
    }

    private fun makeLegacyData(samples: List<TimeSeriesSample>): TimeSeriesData {
        // The real 18-entry legacy (V1 / datacollect) definitions.
        return TimeSeriesData(makeHeader(), TimeSeriesVariables.definitions, samples, makeFooter())
    }

    private fun makeHeader() = TimeSeriesHeader(
        scanId = "test-scan",
        deviceFingerprint = "test-fp",
        scanType = "general",
        appVersion = "1.0.0-test",
        androidApiLevel = 34,
        deviceModel = "TestDevice",
        cpuCores = 8,
        totalRamMb = 8192L,
        startTimestamp = 0L,
        batteryLevelStart = 80,
        batteryTempStart = 30.0f
    )

    private fun makeFooter() = TimeSeriesFooter(
        endTimestamp = 480_000L,
        totalSamples = 0,
        isComplete = true,
        earlyStopReason = null,
        batteryLevelEnd = 70,
        maxBatteryTemp = 40.0f,
        cpuTempFinalC = 60.0f,
        cpuTempFinalSource = "sampler_max_cpu"
    )

    /**
     * Build a ThermalTests instance with a stubbed Context. Only the
     * `getString(resId: Int)` overload is called from `computeCooldown`
     * (for nameAr / nameTr / source-note strings), so returning a fixed
     * placeholder is sufficient — the test asserts on `status`, not on
     * any localised text.
     */
    private fun thermalTests(): ThermalTests {
        val ctx: Context = mock {
            on { getString(any<Int>()) } doReturn "stub-string"
        }
        return ThermalTests(systemFileReader = mock(), context = ctx)
    }
}
