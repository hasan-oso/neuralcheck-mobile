package net.neuralcheck.service.stress

import net.neuralcheck.data.local.files.V2_SAMPLE_VAR_COUNT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 1.14 hotfix (2026-05-17): unit tests for the cascade that picks
 * the CPU temperature shared between the footer (`footer.maxCpuTemp` ≤ 67,
 * `cpuTempFinalC` + `cpuTempFinalSource` from fieldsVersion 68) and the T-01
 * input. The helper [StressTestService.selectFooterCpuTemp] was extracted
 * from an inline `when` block specifically so this contract is testable
 * without instantiating the full service.
 *
 * Production reference: scan 18dfd6b4 (Samsung Galaxy S24 Ultra) reported
 * T-01 = 81 °C FAIL while the same scan's footer.maxCpuTemp = 43.6 °C,
 * because T-01 read `stats.maxTempC` directly while the footer ran this
 * cascade. The split is fixed at the call site (one read, both
 * consumers); these tests document the cascade's contract so a future
 * refactor cannot regress the bug.
 */
class StressTestServiceTest {

    // fieldsVersion 68 (T-10, S-13, S-33): the cascade's result is now
    // (valueC?, source) — selectFooterCpuTemp — and the footer no longer carries
    // the -1f sentinel under the name "maxCpuTemp". The scenarios below are the
    // SAME ones that used to be asserted on selectFooterMaxTemp's -1f/Float; they
    // are REPLACED by assertions on the new result (rule 23), and one table pins
    // that T-01's input (forT01) is still the old number, -1f included.

    // ─── Scenario 1: live read available → trumps the snapshot ───

    @Test
    fun `live CPU read trumps the snapshot peak when both are available - source live_read_at_finalize`() {
        // Reproduces production scan 18dfd6b4: the per-tick sampler picked
        // up a non-CPU zone hitting 81 °C, but the end-of-scan live read
        // (post-cooldown) returned the actual CPU sensor at 43.6 °C.
        val result = StressTestService.selectFooterCpuTemp(
            liveCpu = 43.6f,
            snapshotMaxTempC = 81f,
            snapshotTempSource = "cpu"
        )
        assertEquals(43.6f, result.valueC!!, 0.001f)
        assertEquals("live_read_at_finalize", result.source)
    }

    // ─── Scenario 2: no live read → snapshot wins iff tempSource is CPU ───

    @Test
    fun `snapshot peak is used when live read fails AND tempSource is cpu - source sampler_max_cpu`() {
        // Devices where systemFileReader.readCpuTemp() returns -1f at
        // finalize (transient sysfs read failure, e.g. cgroup blocked
        // mid-scan). The snapshot's accumulated peak is the best fallback
        // we have — accept it ONLY when the source class is "cpu".
        val result = StressTestService.selectFooterCpuTemp(
            liveCpu = -1f,
            snapshotMaxTempC = 81f,
            snapshotTempSource = "cpu"
        )
        assertEquals(81f, result.valueC!!, 0.001f)
        assertEquals("sampler_max_cpu", result.source)
    }

    // ─── Scenario 3: snapshot tempSource is NOT cpu → refuse ───

    @Test
    fun `snapshot peak from battery source is refused - no value, the source says why`() {
        // Samsung One UI / HyperOS frequently block CPU/GPU thermal zones
        // entirely; SamplerStats cascades to battery temp as a last resort
        // and sets tempSource="battery". Battery temp is fundamentally a
        // different signal (lagging, ambient-coupled, not silicon) — must
        // NOT leak into the CPU field or T-01.
        val result = StressTestService.selectFooterCpuTemp(
            liveCpu = -1f,
            snapshotMaxTempC = 81f,
            snapshotTempSource = "battery"
        )
        assertNull("no -1f in the footer, no value at all", result.valueC)
        assertTrue(result.source, result.source.startsWith("absent:"))
        assertTrue(result.source.contains("'battery'"))
        assertTrue(result.source.contains("readCpuTemp returned -1.0"))
    }

    @Test
    fun `snapshot peak from gpu source is refused`() {
        // Same reasoning as battery — only "cpu" passes the cascade gate.
        val result = StressTestService.selectFooterCpuTemp(
            liveCpu = -1f,
            snapshotMaxTempC = 75f,
            snapshotTempSource = "gpu"
        )
        assertNull(result.valueC)
        assertTrue(result.source.startsWith("absent:") && result.source.contains("'gpu'"))
    }

    // ─── Scenario 4: edge values around the 0f boundary ───

    @Test
    fun `liveCpu equal to zero falls through to snapshot`() {
        // The cascade uses `liveCpu > 0f`, not `liveCpu >= 0f` — a 0 °C
        // reading is treated as "sensor returned a sentinel" and falls
        // through. Real silicon should never be at 0 °C anyway.
        val result = StressTestService.selectFooterCpuTemp(
            liveCpu = 0f,
            snapshotMaxTempC = 45f,
            snapshotTempSource = "cpu"
        )
        assertEquals(45f, result.valueC!!, 0.001f)
        assertEquals("sampler_max_cpu", result.source)
    }

    @Test
    fun `snapshot peak equal to zero is refused even with cpu source`() {
        // Both gates must be true: tempSource == "cpu" AND snapshotMaxTempC
        // > 0f. A zero snapshot value usually means "no valid samples"
        // (stats.maxTempC defaults to -1f when no readings landed, but
        // 0 can also surface if the per-tick reader returned 0 sentinels
        // before the first valid read).
        val result = StressTestService.selectFooterCpuTemp(
            liveCpu = -1f,
            snapshotMaxTempC = 0f,
            snapshotTempSource = "cpu"
        )
        assertNull(result.valueC)
        assertTrue(result.source.startsWith("absent:"))
        assertTrue("says the cpu-sourced maximum was not positive", result.source.contains("a maximum of 0.0 (not > 0)"))
    }

    // ─── Scenario 5: nothing available → absent, with its reason ───

    @Test
    fun `everything unavailable is absent with the reason - never -1`() {
        // Worst case: SELinux blocked the CPU thermal zone for the whole
        // scan, no readings landed, the live read also failed. The footer
        // used to carry -1f ("the documented missing-data marker") under the
        // name maxCpuTemp; it now carries NO value and a source that says why.
        val result = StressTestService.selectFooterCpuTemp(
            liveCpu = -1f,
            snapshotMaxTempC = -1f,
            snapshotTempSource = "none"
        )
        assertNull(result.valueC)
        assertEquals(
            "absent:no live CPU read (readCpuTemp returned -1.0, not > 0) and the sampler's temperature source was 'none', not 'cpu'",
            result.source
        )
    }

    // ─── Scenario 6: headroom source treated like non-cpu (refused) ───

    @Test
    fun `headroom-sourced snapshot is refused`() {
        // SamplerStats may promote PowerManager.getThermalHeadroom() to
        // tempSource="headroom" when neither CPU/GPU sysfs nor battery
        // temp is available. That value is a normalized 0..1 ratio, not
        // a Celsius reading — must not be conflated with T-01's °C scale.
        val result = StressTestService.selectFooterCpuTemp(
            liveCpu = -1f,
            snapshotMaxTempC = 0.85f,           // headroom ratio, not °C
            snapshotTempSource = "headroom"
        )
        assertNull(result.valueC)
        assertTrue(result.source.contains("'headroom'"))
    }

    // ─── T-01's input: the SAME decision, in the old number ───

    @Test
    fun `T-01 keeps reading the cascade as before - the value, or -1f when there is none`() {
        val cases = listOf(
            Triple(43.6f, 81f, "cpu") to 43.6f,
            Triple(-1f, 81f, "cpu") to 81f,
            Triple(-1f, 81f, "battery") to -1f,
            Triple(-1f, 75f, "gpu") to -1f,
            Triple(0f, 45f, "cpu") to 45f,
            Triple(-1f, 0f, "cpu") to -1f,
            Triple(-1f, -1f, "none") to -1f,
            Triple(-1f, 0.85f, "headroom") to -1f
        )
        for ((input, expectedForT01) in cases) {
            val (live, snap, source) = input
            val r = StressTestService.selectFooterCpuTemp(live, snap, source)
            assertEquals("T-01 input for $input", expectedForT01, r.forT01(), 0.001f)
            assertEquals("the footer value and T-01's are one decision for $input", r.valueC ?: -1f, r.forT01(), 0f)
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // Phase 1.14 hotfix continuation (2026-05-18): T-02 cascade.
    //
    // computeHeatRiseFromFooter recomputes the °C/min rate against the
    // same maxTemp T-01 trusts (footerMaxTemp) rather than the raw
    // stats.heatRiseC that uses the contaminated maxBestTemp internally.
    // ─────────────────────────────────────────────────────────────────

    @Test
    fun `S24 Ultra reproducer — 43_6C peak across 8 min ≈ 1_9 C per min`() {
        // Production scan 18dfd6b4 with the post-hotfix T-01 value:
        //   footerMaxTemp = 43.6 °C  (cascade-gated, was stats.maxTempC=81)
        //   firstBestTempC = 28.4 °C (first CPU sample at scan start)
        //   scanDurationMs = 480_000 (8 min of stress)
        //   → expected rate = (43.6 - 28.4) / 8 = 1.9 °C/min
        // Under Phase 1.14 thresholds (< 1 PASS / < 3 WARNING / >= 3 FAIL)
        // this lands in WARNING — much more honest than the pre-hotfix
        // FAIL (~6.6 °C/min derived from the contaminated 81 °C peak).
        val result = StressTestService.computeHeatRiseFromFooter(
            footerMaxTemp = 43.6f,
            firstBestTempC = 28.4f,
            scanDurationMs = 480_000L
        )
        assertEquals(1.9f, result, 0.05f)
    }

    @Test
    fun `cascade refused footerMaxTemp returns -1`() {
        // When the cascade has no value (forT01() = -1f: all CPU sources unavailable),
        // T-02 also reports UNAVAILABLE rather than a rate computed from a
        // partial signal.
        val result = StressTestService.computeHeatRiseFromFooter(
            footerMaxTemp = -1f,
            firstBestTempC = 28.4f,
            scanDurationMs = 480_000L
        )
        assertEquals(-1f, result, 0.001f)
    }

    @Test
    fun `firstBestTemp is NaN returns -1`() {
        // SamplerStats sets firstBestTemp = NaN when no temperature source
        // ever reported a valid reading during the scan. T-02 must surface
        // UNAVAILABLE in that case rather than fabricating a delta against
        // a missing baseline.
        val result = StressTestService.computeHeatRiseFromFooter(
            footerMaxTemp = 43.6f,
            firstBestTempC = Float.NaN,
            scanDurationMs = 480_000L
        )
        assertEquals(-1f, result, 0.001f)
    }

    @Test
    fun `firstBestTemp is zero returns -1`() {
        // Same logic as the NaN case — 0 °C is a sentinel value, not a real
        // CPU reading. The function treats it as missing data.
        val result = StressTestService.computeHeatRiseFromFooter(
            footerMaxTemp = 43.6f,
            firstBestTempC = 0f,
            scanDurationMs = 480_000L
        )
        assertEquals(-1f, result, 0.001f)
    }

    @Test
    fun `firstBestTemp negative returns -1`() {
        // Defensive: a negative firstBestTemp would produce a positive delta
        // (because the function uses coerceAtLeast(0) AFTER subtraction —
        // ah wait, that's not safe; we guard at the input instead).
        // Reject negatives upfront.
        val result = StressTestService.computeHeatRiseFromFooter(
            footerMaxTemp = 43.6f,
            firstBestTempC = -5f,
            scanDurationMs = 480_000L
        )
        assertEquals(-1f, result, 0.001f)
    }

    @Test
    fun `zero scan duration returns -1`() {
        // Should never happen in practice (scan finalize implies sampling
        // ran at least once) but defends against early-stopped scans where
        // firstSampleElapsedMs == lastSampleElapsedMs.
        val result = StressTestService.computeHeatRiseFromFooter(
            footerMaxTemp = 43.6f,
            firstBestTempC = 28.4f,
            scanDurationMs = 0L
        )
        assertEquals(-1f, result, 0.001f)
    }

    @Test
    fun `negative scan duration returns -1`() {
        val result = StressTestService.computeHeatRiseFromFooter(
            footerMaxTemp = 43.6f,
            firstBestTempC = 28.4f,
            scanDurationMs = -1000L
        )
        assertEquals(-1f, result, 0.001f)
    }

    @Test
    fun `negative delta is clamped to zero`() {
        // Defensive: if firstBestTemp > footerMaxTemp (sensor drift /
        // cold start anomaly where the first reading was warmer than the
        // final peak somehow), the delta clamps at 0 so the rate is 0,
        // not negative. 0 °C/min is honest — the device didn't heat up.
        val result = StressTestService.computeHeatRiseFromFooter(
            footerMaxTemp = 30f,
            firstBestTempC = 35f,
            scanDurationMs = 480_000L
        )
        assertEquals(0f, result, 0.001f)
    }

    @Test
    fun `flat temperature flagship reads 0 C per min`() {
        // Healthy flagship scenario: vapor chamber + idle workload, no
        // measurable temperature rise. Rate should be 0, not UNAVAILABLE.
        val result = StressTestService.computeHeatRiseFromFooter(
            footerMaxTemp = 32f,
            firstBestTempC = 32f,
            scanDurationMs = 480_000L
        )
        assertEquals(0f, result, 0.001f)
    }

    @Test
    fun `mid-range device under heavy load reads in WARNING band`() {
        // Mid-range device with throttled cooling, e.g. 8 °C rise over a
        // 4-minute scan → 2 °C/min — lands in WARNING band per Phase 1.14
        // thresholds (< 1 PASS / < 3 WARNING / >= 3 FAIL).
        val result = StressTestService.computeHeatRiseFromFooter(
            footerMaxTemp = 38f,
            firstBestTempC = 30f,
            scanDurationMs = 240_000L
        )
        assertEquals(2.0f, result, 0.05f)
    }

    // ─────────────────────────────────────────────────────────────────
    // fix/prescan-battery-gate: pre-flight battery gate contract.
    //
    // fieldsVersion 71 (A-P6-13, user decision «بداية الشحن بين 30 و85% تُفرض»): the gate judges the start against ScanBatteryWindow —
    // 30..85 % — REPLACING the lone temporary 25 % floor (2026-09-11) and its constant; the tests below REPLACE the ones that pinned 25
    // (rule 23). They also pin the sentinel handling of BATTERY_PROPERTY_CAPACITY and the sticky-intent fallback.
    // ─────────────────────────────────────────────────────────────────

    @Test
    fun `battery gate blocks below the 30 pct minimum`() {
        assertTrue(StressTestService.isPreflightBatteryBlocked(0))
        assertTrue(StressTestService.isPreflightBatteryBlocked(25))   // the old floor: it passed before fieldsVersion 71
        assertTrue(StressTestService.isPreflightBatteryBlocked(29))   // boundary - 1
    }

    @Test
    fun `battery gate blocks above the 85 pct maximum`() {
        assertTrue(StressTestService.isPreflightBatteryBlocked(86))   // boundary + 1
        assertTrue(StressTestService.isPreflightBatteryBlocked(100))
    }

    @Test
    fun `battery gate passes inside the window - both boundaries are in`() {
        assertFalse(StressTestService.isPreflightBatteryBlocked(30))  // boundary
        assertFalse(StressTestService.isPreflightBatteryBlocked(31))
        assertFalse(StressTestService.isPreflightBatteryBlocked(34))  // the original field-bug device
        assertFalse(StressTestService.isPreflightBatteryBlocked(60))
        assertFalse(StressTestService.isPreflightBatteryBlocked(84))
        assertFalse(StressTestService.isPreflightBatteryBlocked(85))  // boundary
    }

    @Test
    fun `battery gate is permissive on unknown or implausible levels`() {
        // null = both sources failed; out-of-range = sentinel leaked past
        // the resolver (defensive). Unknown must PASS, not block — the old
        // inline read let Integer.MIN_VALUE hard-block the scan.
        // (PENDING, «معلّق: مواصفة غائبة»: the contract and the plan do not say what an unknown level should do; it passes as it always did.)
        assertFalse(StressTestService.isPreflightBatteryBlocked(null))
        assertFalse(StressTestService.isPreflightBatteryBlocked(-1))
        assertFalse(StressTestService.isPreflightBatteryBlocked(Int.MIN_VALUE))
        assertFalse(StressTestService.isPreflightBatteryBlocked(101))
    }

    @Test
    fun `the gate and the window are one thing - every level from -200 to 300 agrees with ScanBatteryWindow, and the old constant is gone`() {
        for (level in -200..300) {
            assertEquals("level $level", ScanBatteryWindow.refuses(level), StressTestService.isPreflightBatteryBlocked(level))
        }
        assertEquals(30, ScanBatteryWindow.MIN_PCT)
        assertEquals(85, ScanBatteryWindow.MAX_PCT)
        assertTrue(
            "PRE_FLIGHT_MIN_BATTERY_PCT is replaced, not kept beside the window",
            StressTestService::class.java.declaredFields.none { it.name == "PRE_FLIGHT_MIN_BATTERY_PCT" } &&
                StressTestService.Companion::class.java.declaredFields.none { it.name == "PRE_FLIGHT_MIN_BATTERY_PCT" }
        )
    }

    @Test
    fun `resolver prefers a plausible CAPACITY property`() {
        assertEquals(
            34,
            StressTestService.resolvePreflightBatteryLevel(
                propertyCapacityPct = 34, stickyLevel = 80, stickyScale = 100
            )
        )
    }

    @Test
    fun `resolver falls back to sticky intent on MIN_VALUE sentinel`() {
        // BATTERY_PROPERTY_CAPACITY returns Integer.MIN_VALUE when the
        // vendor fork doesn't support the property.
        assertEquals(
            67,
            StressTestService.resolvePreflightBatteryLevel(
                propertyCapacityPct = Int.MIN_VALUE, stickyLevel = 67, stickyScale = 100
            )
        )
    }

    @Test
    fun `resolver falls back to sticky intent on stale zero`() {
        // 0 = battery service not yet populated (same sentinel
        // BatteryReader.register's probe treats as stale).
        assertEquals(
            55,
            StressTestService.resolvePreflightBatteryLevel(
                propertyCapacityPct = 0, stickyLevel = 55, stickyScale = 100
            )
        )
    }

    @Test
    fun `resolver normalises non-100 sticky scales`() {
        // EXTRA_SCALE is not guaranteed to be 100 — some vendors report 1000.
        assertEquals(
            50,
            StressTestService.resolvePreflightBatteryLevel(
                propertyCapacityPct = null, stickyLevel = 500, stickyScale = 1000
            )
        )
    }

    @Test
    fun `resolver returns null when both sources are invalid`() {
        assertNull(
            StressTestService.resolvePreflightBatteryLevel(
                propertyCapacityPct = Int.MIN_VALUE, stickyLevel = -1, stickyScale = -1
            )
        )
        assertNull(
            StressTestService.resolvePreflightBatteryLevel(
                propertyCapacityPct = null, stickyLevel = null, stickyScale = null
            )
        )
    }

    @Test
    fun `resolver rejects an implausible 101+ property reading`() {
        // >100% is a unit artifact, never a real level — fall to the sticky
        // pair rather than trusting it.
        assertEquals(
            88,
            StressTestService.resolvePreflightBatteryLevel(
                propertyCapacityPct = 101, stickyLevel = 88, stickyScale = 100
            )
        )
    }

    // ─────────────────────────────────────────────────────────────────
    // fieldsVersion 66: footer.maxBatteryTemp must be the scan MAXIMUM.
    //
    // Field reference (S10 5G scan 4b1cd24d): footer wrote 38.1 (the
    // end-of-scan snapshot) while the raw v[1] maximum was 38.5 — the
    // field's name promised the max and batteryHealth.batteryTempMaxC
    // already had it. One helper now feeds both.
    // ─────────────────────────────────────────────────────────────────

    private fun sample(tempC: Float) = net.neuralcheck.data.local.files.TimeSeriesSample(
        ts = 0L, phase = 3, v = FloatArray(V2_SAMPLE_VAR_COUNT).also { it[1] = tempC }
    )

    @Test
    fun `max battery temp is the scan maximum, not the last sample`() {
        val samples = listOf(sample(31.0f), sample(38.5f), sample(38.1f))
        val max = StressTestService.computeMaxBatteryTempC(samples, fallbackC = 38.1f, batteryTempIdx = 1)
        assertEquals(38.5, max, 0.001)
    }

    @Test
    fun `non-positive battery temps are sentinels and are skipped`() {
        val samples = listOf(sample(0f), sample(-1f), sample(36.2f))
        val max = StressTestService.computeMaxBatteryTempC(samples, fallbackC = 20f, batteryTempIdx = 1)
        assertEquals(36.2, max, 0.001)
    }

    @Test
    fun `no positive sample falls back to the supplied temperature`() {
        val samples = listOf(sample(0f), sample(0f))
        val max = StressTestService.computeMaxBatteryTempC(samples, fallbackC = 37.4f, batteryTempIdx = 1)
        assertEquals(37.4, max, 0.001)
        assertEquals(25.0, StressTestService.computeMaxBatteryTempC(emptyList(), 25f, 1), 0.001)
    }

    @Test
    fun `short legacy sample arrays are ignored rather than crashing`() {
        val short = net.neuralcheck.data.local.files.TimeSeriesSample(0L, 3, FloatArray(1))
        val max = StressTestService.computeMaxBatteryTempC(listOf(short, sample(33.3f)), 20f, 1)
        assertEquals(33.3, max, 0.001)
    }
}
