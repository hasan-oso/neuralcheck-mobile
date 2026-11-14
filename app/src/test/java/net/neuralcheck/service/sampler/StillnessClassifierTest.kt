package net.neuralcheck.service.sampler

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * fieldsVersion 67 — item 3: footer.scanStillness.isStill was useless.
 *
 * Under the 0.1 m/s² threshold every field scan reported isStill=false,
 * including seven where the device sat flat on a table for the whole run.
 * A flag that fires on 100 % of correct placements carries no information.
 */
class StillnessClassifierTest {

    /** The seven motionless field measurements the threshold is based on
     *  (Honor MTN-NX1, Samsung Galaxy S10 5G, Samsung Galaxy A51). */
    private val motionlessFieldScans = floatArrayOf(
        0.2515322f,  // Honor MTN-NX1,   fv66
        0.2526f,     // Galaxy A51,      fv64
        0.25401366f, // Galaxy S10 5G,   fv66
        0.2609f,     // Honor MTN-NX1,   fv65
        0.3376f,     // Honor MTN-NX1,   fv65
        0.3518f,     // Galaxy S10 5G,   fv65
        0.3723f      // Galaxy S10 5G,   fv65  <- highest observed
    )

    @Test
    fun `every motionless field scan is now classified still`() {
        for (v in motionlessFieldScans) {
            assertTrue("table-flat scan stdDev=$v must classify as still",
                StillnessClassifier.isStill(v))
        }
    }

    @Test
    fun `the old threshold would have failed all of them`() {
        // Locks the regression this change fixes: 0.1 rejected every one.
        val oldThreshold = 0.1f
        for (v in motionlessFieldScans) {
            assertTrue("pre-67 behaviour check for $v", v > oldThreshold)
        }
    }

    @Test
    fun `threshold keeps headroom over the highest motionless observation`() {
        val maxObserved = motionlessFieldScans.max()
        assertTrue("threshold must sit above the measured floor",
            StillnessClassifier.STD_DEV_THRESHOLD > maxObserved)
        val headroom = StillnessClassifier.STD_DEV_THRESHOLD / maxObserved
        assertTrue("headroom $headroom too tight", headroom >= 1.3f)
    }

    @Test
    fun `gross motion is still flagged`() {
        // Walking / hand-carrying produces 1.5-4 m/s2 on this statistic;
        // 0.7992 was measured on a scan that was disturbed mid-run.
        assertFalse(StillnessClassifier.isStill(0.7992f))
        assertFalse(StillnessClassifier.isStill(1.5f))
        assertFalse(StillnessClassifier.isStill(4.0f))
    }

    @Test
    fun `boundary is inclusive`() {
        assertTrue(StillnessClassifier.isStill(0.5f))
        assertFalse(StillnessClassifier.isStill(0.5001f))
    }

    // fieldsVersion 68 (ت-18 row 6, N-13) REPLACES the test that used to stand
    // here ("unavailable std dev reports still but keeps its sentinel"): it
    // pinned absence read as stillness (isStill = true beside accelStdDev = -1).
    // The sentinel no longer exists; an std-dev that could not be computed is
    // null and the record claims nothing about motion.

    @Test
    fun `fewer than 2 readings claim nothing about motion - the state says so`() {
        val r = StillnessClassifier.report(accelStdDev = null, sampleCount = 1)
        assertNull("not 'still'", r.isStill)
        assertNull("not -1", r.accelStdDev)
        assertEquals(1, r.sampleCount)
        assertEquals("insufficient_accel_samples", r.stillnessState)
    }

    @Test
    fun `a measured std dev is judged and stated measured`() {
        val still = StillnessClassifier.report(accelStdDev = 0.3f, sampleCount = 4_000)
        assertEquals(true, still.isStill)
        assertEquals(0.3f, still.accelStdDev!!, 0f)
        assertEquals("measured", still.stillnessState)

        val moving = StillnessClassifier.report(accelStdDev = 1.5f, sampleCount = 4_000)
        assertEquals(false, moving.isStill)
        assertEquals("measured", moving.stillnessState)
    }

    @Test
    fun `a measured zero is still - zero variance is a value`() {
        val r = StillnessClassifier.report(accelStdDev = 0f, sampleCount = 10)
        assertEquals(true, r.isStill)
        assertEquals(0f, r.accelStdDev!!, 0f)
        assertEquals("measured", r.stillnessState)
    }

    @Test
    fun `zero variance is still`() {
        assertTrue(StillnessClassifier.isStill(0f))
    }
}
