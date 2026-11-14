package net.neuralcheck.hardware.info

import android.content.Context
import android.util.Log
import net.neuralcheck.R
import net.neuralcheck.domain.model.TestResult
import net.neuralcheck.domain.model.TestStatus
import javax.inject.Inject

/**
 * Datacollect-only diagnostics that surface device-identity fields the launch
 * flavor must NOT collect (region targeting). All tests are gated by
 * BuildConfig.ENABLE_EXTENDED_INFO_TESTS at the runner level — this class
 * itself does not check the flag, so the runner is the single source of
 * truth for which flavor sees these results.
 *
 * Tests:
 *   - INFO-01: CSC / region code (Samsung-specific sales-code prop)
 */
class ExtendedInfoTests @Inject constructor(private val context: Context) {

    companion object {
        private const val TAG = "NeuralCheck"
    }

    fun runAll(): List<TestResult> = listOf(
        runCatching { testRegionCode() }.getOrElse {
            Log.w(TAG, "INFO-01 failed: ${it.message}")
            unavailable("INFO-01", R.string.test_info01_ar, R.string.test_info01_tr)
        }
    )

    /**
     * INFO-01: Samsung CSC (Customer Service Code) / region code. Reads three
     * candidate props in order: ro.csc.sales_code, ril.official_csc_code,
     * ro.csc.countryiso_code. Useful on the resale market because Samsung
     * region locks parts of the firmware update path to the original CSC —
     * a phone sold as TUR (Turkey) but flashed with XEU (Europe) firmware
     * is a sign of a previously rooted / unlocked device.
     */
    private fun testRegionCode(): TestResult {
        val nameAr = context.getString(R.string.test_info01_ar)
        val nameTr = context.getString(R.string.test_info01_tr)
        val unavailableAr = context.getString(R.string.value_unavailable_ar)

        val csc = sequenceOf(
            "ro.csc.sales_code",
            "ril.official_csc_code",
            "ro.csc.countryiso_code"
        ).map(::readSystemProp).firstOrNull { it.isNotBlank() }

        return if (csc.isNullOrBlank()) {
            TestResult("INFO-01", nameAr, nameTr, "device_info",
                TestStatus.UNAVAILABLE, null, unavailableAr, "", null)
        } else {
            TestResult("INFO-01", nameAr, nameTr, "device_info",
                TestStatus.PASS, null, csc, "", null)
        }
    }

    private fun unavailable(testId: String, nameArResId: Int, nameTrResId: Int): TestResult {
        val nameAr = runCatching { context.getString(nameArResId) }.getOrDefault(testId)
        val nameTr = runCatching { context.getString(nameTrResId) }.getOrDefault(testId)
        val unavailableAr = runCatching {
            context.getString(R.string.value_unavailable_ar)
        }.getOrDefault("N/A")
        return TestResult(testId, nameAr, nameTr, "device_info",
            TestStatus.UNAVAILABLE, null, unavailableAr, "", null)
    }

    private fun readSystemProp(key: String): String = try {
        val process = Runtime.getRuntime().exec(arrayOf("getprop", key))
        val out = process.inputStream.bufferedReader().use { it.readLine() }?.trim().orEmpty()
        process.waitFor()
        out
    } catch (t: Throwable) {
        Log.w(TAG, "getprop $key failed: ${t.message}")
        ""
    }
}
