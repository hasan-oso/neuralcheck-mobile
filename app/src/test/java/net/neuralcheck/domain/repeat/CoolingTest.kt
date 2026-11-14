package net.neuralcheck.domain.repeat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The cooling check - A-P6-07 (PLAN-06:633-636, :599: «|الحالية − الأولى| ≤ 1°»; the server's `thresholdC: 1` and its tenth-of-a-degree rounding, A4 (1)). Everything is in tenths of a degree, as integers.
 * Rule 30: the decision is tested for EVERY difference over a range wide enough to hold both edges, not for the two values either side of one edge.
 */
class CoolingTest {

    private val reference = 328 // 32.8 degrees: the sample's start temperature

    private fun at(tenths: Int, peak: Int = 0) = coolingDecision(reference, TempRead.Present(tenths), peak)

    @Test
    fun `ready exactly when the phone is within one degree of the first scan's start - every difference from minus thirty to plus thirty tenths`() {
        for (diff in -300..300) {
            val decision = at(reference + diff)
            val within = Math.abs(diff) <= 10
            assertEquals("diff=$diff", within, decision == CoolingDecision.Ready)
            if (!within) {
                val side = (decision as CoolingDecision.NotCooled).side
                assertEquals("diff=$diff", if (diff > 0) CoolingSide.ABOVE else CoolingSide.BELOW, side)
            }
        }
    }

    @Test
    fun `the edges - exactly one degree above and below is ready, one tenth more is not`() {
        assertEquals(CoolingDecision.Ready, at(338))
        assertEquals(CoolingDecision.Ready, at(318))
        assertTrue(at(339) is CoolingDecision.NotCooled)
        assertTrue(at(317) is CoolingDecision.NotCooled)
    }

    @Test
    fun `the floating-point trap of the server's contract - 32_2 and 31_2 are exactly one degree apart in tenths`() {
        // A4 (1): `32.2 - 31.2 = 1.0000000000000036` in floating point; in tenths it is 322 - 312 = 10, which is within
        assertEquals(322, Cooling.tenthsOf(32.2))
        assertEquals(312, Cooling.tenthsOf(31.2))
        assertEquals(CoolingDecision.Ready, coolingDecision(Cooling.tenthsOf(31.2), TempRead.Present(322), 0))
        assertEquals(CoolingDecision.Ready, coolingDecision(Cooling.tenthsOf(32.2), TempRead.Present(312), 0))
    }

    @Test
    fun `the first scan's temperature is rounded to a tenth, half up, as the server rounds it - and kept raw until then`() {
        assertEquals(328, Cooling.tenthsOf(32.8))
        assertEquals(328, Cooling.tenthsOf(32.84))
        assertEquals(329, Cooling.tenthsOf(32.85))
        assertEquals(-5, Cooling.tenthsOf(-0.5))
        assertEquals(1, Cooling.THRESHOLD_TENTHS / 10)
    }

    @Test
    fun `no reading is no decision to start - the reason is kept, and there is no ready for any peak`() {
        for (why in listOf("no_battery_intent", "no_temperature_extra", "zero_is_the_missing_value")) {
            assertEquals(why, CoolingDecision.TemperatureUnreadable(why), coolingDecision(reference, TempRead.Unreadable(why), 0))
            assertEquals(why, CoolingDecision.TemperatureUnreadable(why), coolingDecision(reference, TempRead.Unreadable(why), 99))
        }
    }

    @Test
    fun `the bar is the share of the largest excess already shed - it grows as the phone cools, is zero when the phone is colder than the band, and has no number`() {
        // the band's upper edge is reference + 10 = 338; excess is how far above it the phone is
        assertEquals(0, excessTenths(reference, 338))
        assertEquals(0, excessTenths(reference, 300))
        assertEquals(40, excessTenths(reference, 378))
        var peak = 0
        val progress = mutableListOf<Float>()
        for (temp in listOf(378, 370, 360, 350, 340)) {
            peak = maxOf(peak, excessTenths(reference, temp))
            progress += (coolingDecision(reference, TempRead.Present(temp), peak) as CoolingDecision.NotCooled).progress
        }
        assertEquals(listOf(0f, 0.2f, 0.45f, 0.7f, 0.95f), progress.map { Math.round(it * 100f) / 100f })
        assertTrue("monotone while cooling", progress.zipWithNext().all { (a, b) -> b >= a })
        assertEquals("one tenth above the band, nothing shed yet of an excess of one tenth", 0f, (at(339, peak = 0) as CoolingDecision.NotCooled).progress, 0f)
        assertEquals("colder than the band by more than a degree: no progress, and not ready", CoolingDecision.NotCooled(CoolingSide.BELOW, 0f), at(300, peak = 40))
        assertEquals("the phone warms again after cooling a little: the bar goes back, the peak holds", 0.5f, (at(358, peak = 40) as CoolingDecision.NotCooled).progress, 0.001f)
    }
}
