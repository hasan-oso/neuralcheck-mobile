package net.neuralcheck.hardware.display

import android.content.Context
import android.util.DisplayMetrics
import android.view.WindowManager
import net.neuralcheck.domain.model.TestResult
import net.neuralcheck.domain.model.TestStatus
import javax.inject.Inject

/** Display tests D-01 to D-03. */
class DisplayTests @Inject constructor(private val context: Context) {
    @Suppress("DEPRECATION")
    fun runAll(): List<TestResult> {
        val d01NameAr = context.getString(net.neuralcheck.R.string.test_d01_ar)
        val d01NameTr = context.getString(net.neuralcheck.R.string.test_d01_tr)
        val d02NameAr = context.getString(net.neuralcheck.R.string.test_d02_ar)
        val d02NameTr = context.getString(net.neuralcheck.R.string.test_d02_tr)
        val d03NameAr = context.getString(net.neuralcheck.R.string.test_d03_ar)
        val d03NameTr = context.getString(net.neuralcheck.R.string.test_d03_tr)
        val unavailableAr = context.getString(net.neuralcheck.R.string.value_unavailable_ar)
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        val display = try { wm?.defaultDisplay } catch (_: Throwable) { null }
        if (wm == null || display == null) {
            // WindowManager.defaultDisplay was deprecated in Android R and
            // can return null on multi-display / virtual-display configs;
            // fall through to UNAVAILABLE rather than NPE'ing the runner.
            return listOf(
                TestResult("D-01", d01NameAr, d01NameTr, "display", TestStatus.UNAVAILABLE, null, unavailableAr, "px", null),
                TestResult("D-02", d02NameAr, d02NameTr, "display", TestStatus.UNAVAILABLE, null, unavailableAr, "DPI", null),
                TestResult("D-03", d03NameAr, d03NameTr, "display", TestStatus.UNAVAILABLE, null, unavailableAr, "Hz", null)
            )
        }
        val dm = try {
            DisplayMetrics().also { display.getRealMetrics(it) }
        } catch (t: Throwable) {
            android.util.Log.w("NeuralCheck", "getRealMetrics failed: ${t.message}")
            DisplayMetrics()
        }
        val refreshRate = try { display.refreshRate } catch (_: Throwable) { 0f }
        return listOf(
            TestResult("D-01", d01NameAr, d01NameTr, "display", TestStatus.PASS, null, "${dm.widthPixels}x${dm.heightPixels}", "px", null),
            TestResult("D-02", d02NameAr, d02NameTr, "display", TestStatus.PASS, null, "${dm.densityDpi}", "DPI", null),
            TestResult("D-03", d03NameAr, d03NameTr, "display",
                if (refreshRate >= 60f) TestStatus.PASS else TestStatus.WARNING, null, String.format(java.util.Locale.US, "%.0f", refreshRate), "Hz", null)
        )
    }
}
