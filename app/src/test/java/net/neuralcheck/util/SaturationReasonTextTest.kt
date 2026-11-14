package net.neuralcheck.util

import net.neuralcheck.data.local.files.VerificationSummary
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Locale

/**
 * fieldsVersion 67 — item 1: satCaveat / saturationFailureReason text.
 *
 * These strings are not logs: they ride in footer.stressEffectiveness into
 * the model prompt and are read as findings. The Samsung Galaxy S10 5G
 * field scan (2026-09-14) shipped:
 *
 *   "C4 peak_freq_capability failed (peak ratio ٠٫٨٥ < 0.85). Silicon was
 *    prevented from reaching nominal frequency, likely due to OEM governor
 *    policy (Xiaomi/HyperOS class). Other integrity checks passed."
 *
 * with governorCappingDetected=false in the same footer — three defects in
 * one sentence: a vendor attribution we never measured (on the wrong
 * vendor), a claim contradicting the neighbouring field, and Arabic-Indic
 * digits from a default-locale format.
 */
class SaturationReasonTextTest {

    private val defaultLocale: Locale = Locale.getDefault()

    @Before fun forceArabicLocale() {
        // Reproduces the field condition: the shop's phone runs ar-SA.
        Locale.setDefault(Locale.forLanguageTag("ar-SA"))
    }

    @After fun restoreLocale() { Locale.setDefault(defaultLocale) }

    private fun c4Caveat(
        governorCappingDetected: Boolean,
        c4Ratio: Float? = 0.84761906f
    ): String? = StressEffectivenessCalculator.composeSaturationReasons(
        conditionsMet = 3,
        c1Passed = true,
        c2Passed = true,
        c3Passed = true,
        c4Passed = false,
        maxPeakPower = 9000,
        pathA = true,
        pathB = false,
        pathC = false,
        freqDropPct = 14.301765f,
        verification = VerificationSummary(),
        c4Ratio = c4Ratio,
        governorCappingDetected = governorCappingDetected
    ).first

    // ── Vendor attribution ──

    @Test
    fun `c4 caveat never names a vendor or OS`() {
        for (capped in listOf(true, false)) {
            val text = c4Caveat(governorCappingDetected = capped)!!
            for (forbidden in listOf("Xiaomi", "HyperOS", "Samsung", "Honor",
                "MIUI", "ColorOS", "OneUI", "Realme", "Oppo")) {
                assertFalse("caveat must not name $forbidden (capped=$capped): $text",
                    text.contains(forbidden, ignoreCase = true))
            }
        }
    }

    @Test
    fun `governor policy is claimed only when capping was actually observed`() {
        val notObserved = c4Caveat(governorCappingDetected = false)!!
        assertFalse("must not assert a governor cap we never measured: $notObserved",
            notObserved.contains("governor", ignoreCase = true))
        // Still describes the measurable fact.
        assertTrue(notObserved.contains("Peak frequency"))
        assertTrue(notObserved.contains("below the nominal maximum"))

        val observed = c4Caveat(governorCappingDetected = true)!!
        assertTrue("watcher saw capping → the hypothesis is allowed: $observed",
            observed.contains("governor", ignoreCase = true))
        assertTrue(observed.contains("watcher"))
    }

    // ── Locale ──

    @Test
    fun `numbers are ASCII under an Arabic default locale`() {
        val text = c4Caveat(governorCappingDetected = true)!!
        assertTrue("expected the measured ratio in ASCII digits: $text",
            text.contains("0.848"))
        // No Arabic-Indic digits (U+0660..U+0669) anywhere in the string.
        assertFalse("caveat contains Arabic-Indic digits: $text",
            text.any { it in '٠'..'٩' })
        // No Arabic decimal separator U+066B either.
        assertFalse(text.contains('٫'))
    }

    @Test
    fun `c2 reason formats freq drop in ASCII under an Arabic locale`() {
        val reason = StressEffectivenessCalculator.composeSaturationReasons(
            conditionsMet = 2,
            c1Passed = true, c2Passed = false, c3Passed = true, c4Passed = false,
            maxPeakPower = 9000,
            pathA = false, pathB = false, pathC = false,
            freqDropPct = 14.3f,
            verification = VerificationSummary(),
            c4Ratio = 0.5f,
            governorCappingDetected = false
        ).second!!
        assertTrue(reason.contains("14.3"))
        assertFalse(reason.any { it in '٠'..'٩' })
    }

    @Test
    fun `formatters are locale independent`() {
        assertTrue(StressEffectivenessCalculator.fmt1(14.3f) == "14.3")
        assertTrue(StressEffectivenessCalculator.fmt3(0.84761906f) == "0.848")
    }

    // ── Precision ──

    @Test
    fun `ratio precision avoids the self-contradicting 0_85 less than 0_85`() {
        // The field value rounds to the threshold at 2 decimals.
        val text = c4Caveat(governorCappingDetected = false, c4Ratio = 0.84761906f)!!
        assertFalse("a ratio printed equal to its own threshold is unreadable: $text",
            text.contains("0.85 < 0.85"))
        assertTrue(text.contains("0.848 < 0.85"))
    }

    @Test
    fun `null ratio degrades to n slash a without crashing`() {
        val text = c4Caveat(governorCappingDetected = false, c4Ratio = null)!!
        assertTrue(text.contains("n/a"))
    }

    // ── Unchanged contract ──

    @Test
    fun `all four conditions met yields no caveat and no reason`() {
        val (caveat, reason) = StressEffectivenessCalculator.composeSaturationReasons(
            conditionsMet = 4,
            c1Passed = true, c2Passed = true, c3Passed = true, c4Passed = true,
            maxPeakPower = 9000, pathA = true, pathB = true, pathC = true,
            freqDropPct = 10f, verification = VerificationSummary(),
            c4Ratio = 0.9f, governorCappingDetected = false
        )
        assertNull(caveat)
        assertNull(reason)
    }

    @Test
    fun `three of four sets caveat only - two of four sets reason only`() {
        val three = StressEffectivenessCalculator.composeSaturationReasons(
            conditionsMet = 3, c1Passed = true, c2Passed = true, c3Passed = true,
            c4Passed = false, maxPeakPower = 9000, pathA = true, pathB = false,
            pathC = false, freqDropPct = 14f, verification = VerificationSummary(),
            c4Ratio = 0.8f, governorCappingDetected = false
        )
        assertNotNull(three.first); assertNull(three.second)

        val two = StressEffectivenessCalculator.composeSaturationReasons(
            conditionsMet = 2, c1Passed = false, c2Passed = true, c3Passed = true,
            c4Passed = false, maxPeakPower = 100, pathA = false, pathB = false,
            pathC = false, freqDropPct = 2f, verification = VerificationSummary(),
            c4Ratio = 0.4f, governorCappingDetected = false
        )
        assertNull(two.first); assertNotNull(two.second)
        assertTrue(two.second!!.contains("C1")); assertTrue(two.second!!.contains("C4"))
    }
}
