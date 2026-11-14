package net.neuralcheck.service.collector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * fieldsVersion 66 — v[6] provenance helpers behind
 * dataQuality.cpuUsageSource / cpuUsageFallbackFromSample / procStatAvailable.
 *
 * Field reference: both comparison devices (Honor MTN-NX1 API 36, Samsung
 * S10 5G API 30) reported procStatAvailable=true under the ≤65 "any
 * v[6] > 0" derivation while perCpuUsageAvailable=false — the same
 * /proc/stat file — i.e. every v[6] sample was the process-level fallback.
 */
class CpuUsageProvenanceTest {

    @Test
    fun `source label follows the reader's sticky flag`() {
        assertEquals("procstat", CpuUsageProvenance.sourceLabel(readerStillOnProcStat = true))
        assertEquals("process_fallback", CpuUsageProvenance.sourceLabel(readerStillOnProcStat = false))
    }

    @Test
    fun `fallback from the very first sample means proc stat was never used`() {
        // Covers the common case AND the UI-read-before-sampler case: the
        // phaseDriver calls readCpuUsage() before the first tick, so the
        // sticky flag may already be false when sample 0 is built.
        assertFalse(CpuUsageProvenance.procStatEverUsed(fallbackFromSample = 0))
    }

    @Test
    fun `no fallback at all means proc stat was used throughout`() {
        assertTrue(CpuUsageProvenance.procStatEverUsed(fallbackFromSample = -1))
    }

    @Test
    fun `mid-scan fallback means the early samples were proc stat`() {
        assertTrue(CpuUsageProvenance.procStatEverUsed(fallbackFromSample = 1))
        assertTrue(CpuUsageProvenance.procStatEverUsed(fallbackFromSample = 812))
    }
}

/**
 * fieldsVersion 67 — item 4: is `cpuUsageFallbackFromSample = 0` a real
 * measurement or a default masquerading as one?
 *
 * Both field devices (Honor MTN-NX1 API 36, Samsung S10 5G API 30) reported
 * cpuUsageSource=process_fallback, cpuUsageFallbackFromSample=0,
 * procStatAvailable=false. The question matters because a field whose
 * "nothing happened" value is 0 would read identically to "happened at
 * index 0".
 *
 * It is NOT a default. The counter's absent value is -1:
 *   DisciplinedSampler declares AtomicInteger(-1), start() resets to -1,
 *   and the only writer is compareAndSet(-1, sampleCount.get()) guarded by
 *   !isProcStatAvailable(). sampleCount is incremented AFTER the sample is
 *   built, so index 0 is genuinely the first sample. 0 therefore means
 *   "/proc/stat was already unreadable when the first sample was built" —
 *   the expected outcome on SELinux-restricted Android.
 */
class CpuUsageFallbackSentinelTest {

    @Test
    fun `absent value is minus one - zero is a real index`() {
        // -1 = never fell back. If 0 were the "absent" default, this would
        // have to report false, and every healthy scan would look broken.
        assertTrue(CpuUsageProvenance.procStatEverUsed(-1))
        assertFalse(CpuUsageProvenance.procStatEverUsed(0))
    }

    @Test
    fun `the three states are distinguishable`() {
        val states = mapOf(
            -1 to true,   // never fell back        → /proc/stat used
            0 to false,   // fell back at sample 0  → never used
            1 to true,    // fell back at sample 1  → sample 0 was /proc/stat
            1450 to true  // fell back late         → most samples real
        )
        for ((index, everUsed) in states) {
            assertEquals("fallbackFromSample=$index", everUsed,
                CpuUsageProvenance.procStatEverUsed(index))
        }
    }

    @Test
    fun `field observation is internally consistent`() {
        // Honor + Samsung both shipped this exact triple.
        val fallbackFromSample = 0
        assertFalse(CpuUsageProvenance.procStatEverUsed(fallbackFromSample))
        assertEquals("process_fallback",
            CpuUsageProvenance.sourceLabel(readerStillOnProcStat = false))
    }
}
