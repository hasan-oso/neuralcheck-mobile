package net.neuralcheck.hardware.cpu

import android.content.Context
import net.neuralcheck.R
import net.neuralcheck.domain.model.TestStatus
import net.neuralcheck.service.collector.SystemFileReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Phase 1.14: unit tests for [CpuTests.computeStability] — specifically the
 * new "constant frequency" caveat note. When `freqReadings.distinct().size
 * == 1` the RSD computation is mathematically 0 but uninformative (governor
 * almost certainly pinned the cluster), so a localised note is now attached
 * to the result alongside the PASS status.
 *
 * Reference: docs/audits/phase_1_14_thermal_diagnosis/findings.md
 */
class CpuStabilityTest {

    private lateinit var ctx: Context
    private lateinit var cpuTests: CpuTests

    private val noteStub = "STUB_CONSTANT_FREQ_NOTE"
    private val nameArStub = "AR_STUB"
    private val nameTrStub = "TR_STUB"

    @Before
    fun setup() {
        ctx = mock()
        whenever(ctx.getString(R.string.test_c07_ar)).thenReturn(nameArStub)
        whenever(ctx.getString(R.string.test_c07_tr)).thenReturn(nameTrStub)
        whenever(ctx.getString(R.string.c07_constant_freq_note)).thenReturn(noteStub)
        // All other getString stubs return ""; computeStability only reads
        // the three keys above, so this is sufficient.
        cpuTests = CpuTests(systemFileReader = mock<SystemFileReader>(), context = ctx)
    }

    // ─── Scenario 1: S24-Ultra-like constant freq → PASS with note ───

    @Test
    fun `all readings identical attaches the constant-freq note`() {
        // Mimics Samsung S24 Ultra scan b69da673 where phase 3's three
        // sampled cores stayed at 3340.8 MHz the whole time.
        val readings = List(20) { 3340.8f }
        val result = cpuTests.computeStability(readings)
        assertEquals(TestStatus.PASS, result.status)
        assertEquals("0.0", result.rawValue)
        assertEquals("%RSD", result.rawValueUnit)
        assertNotNull("expected the constant-freq note", result.notes)
        assertEquals(noteStub, result.notes)
    }

    // ─── Scenario 2: realistic variation → no note ───

    @Test
    fun `varied freq readings carry no constant-freq note`() {
        // Mix of cluster frequencies as the governor scales under load.
        val readings = listOf(
            1500f, 1600f, 2000f, 2500f, 2700f,
            3000f, 3300f, 2200f, 1800f, 2000f
        )
        val result = cpuTests.computeStability(readings)
        assertNull(
            "Variance is non-zero — note should NOT be attached",
            result.notes
        )
        // RSD should also be non-zero — sanity check on the math
        assertTrue(result.rawValue.toString().toFloat() > 0f)
    }

    // ─── Scenario 3: too few samples → UNAVAILABLE, no note ───

    @Test
    fun `fewer than 10 readings returns UNAVAILABLE with no note`() {
        val readings = listOf(1500f, 1600f, 1700f)
        val result = cpuTests.computeStability(readings)
        assertEquals(TestStatus.UNAVAILABLE, result.status)
        assertEquals("N/A", result.rawValue)
        assertNull(result.notes)
    }

    // ─── Scenario 4: one outlier in otherwise constant series → no note ───

    @Test
    fun `single outlier in otherwise constant series carries no note`() {
        // distinct().size == 2 means the sampler IS picking up variation;
        // the RSD figure (tiny but non-zero) is informative on its own.
        val readings = List(19) { 3000f } + listOf(2900f)
        val result = cpuTests.computeStability(readings)
        assertEquals(
            "distinct().size > 1 → no note",
            null, result.notes
        )
    }

    // ─── Scenario 5: empty list → UNAVAILABLE (via size < 10 branch) ───

    @Test
    fun `empty list returns UNAVAILABLE with no note`() {
        val result = cpuTests.computeStability(emptyList())
        assertEquals(TestStatus.UNAVAILABLE, result.status)
        assertNull(result.notes)
    }
}
