package net.neuralcheck.hardware.memory

import android.app.ActivityManager
import android.content.Context
import net.neuralcheck.domain.model.TestResult
import net.neuralcheck.domain.model.TestStatus
import java.util.Locale
import javax.inject.Inject

/** Memory tests R-01 to R-03. */
class MemoryTests @Inject constructor(private val context: Context) {
    fun runAll(): List<TestResult> {
        val r01NameAr = context.getString(net.neuralcheck.R.string.test_r01_ar)
        val r01NameTr = context.getString(net.neuralcheck.R.string.test_r01_tr)
        val unavailableAr = context.getString(net.neuralcheck.R.string.value_unavailable_ar)
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        if (am == null) {
            // ACTIVITY_SERVICE is virtually never null on real devices, but
            // returning UNAVAILABLE here keeps the runner from crashing the
            // whole memory category if Knox or a restricted profile blocks it.
            return listOf(
                TestResult("R-01", r01NameAr, r01NameTr, "memory",
                    TestStatus.UNAVAILABLE, null, unavailableAr, "", null)
            )
        }
        val mi = try {
            ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        } catch (t: Throwable) {
            android.util.Log.w("NeuralCheck", "getMemoryInfo failed: ${t.message}")
            return listOf(
                TestResult("R-01", r01NameAr, r01NameTr, "memory",
                    TestStatus.UNAVAILABLE, null, unavailableAr, "", null)
            )
        }
        val totalGb = mi.totalMem / (1024.0 * 1024 * 1024)
        val availGb = mi.availMem / (1024.0 * 1024 * 1024)
        val pctFree = if (totalGb > 0.0) (availGb / totalGb * 100).toFloat() else 0f
        val r02NameAr = context.getString(net.neuralcheck.R.string.test_r02_ar)
        val r02NameTr = context.getString(net.neuralcheck.R.string.test_r02_tr)
        val r03NameAr = context.getString(net.neuralcheck.R.string.test_r03_ar)
        val r03NameTr = context.getString(net.neuralcheck.R.string.test_r03_tr)
        val lowMemStr = context.getString(net.neuralcheck.R.string.value_low_ar)
        val normalMemStr = context.getString(net.neuralcheck.R.string.value_normal_ar)

        // R-02: Display the figure in GB (what the user wants to see) but
        // pre-compute the score from the *percent* free so the scoring engine
        // does not mis-interpolate 2.5 GB as "2.5%" and dock the device for
        // memory pressure it does not actually have. Thresholds here are
        // intentionally generous: Android aggressively reclaims idle pages
        // into caches, so a healthy device routinely sits at 25–40% "free"
        // and anything above ~15% is not a concern.
        val r02Score = when {
            pctFree > 30f -> 100f
            pctFree > 15f -> 85f
            pctFree > 8f  -> 60f
            else          -> 20f
        }
        val r02Status = when {
            pctFree > 15f -> TestStatus.PASS
            pctFree > 8f  -> TestStatus.WARNING
            else          -> TestStatus.FAIL
        }

        return listOf(
            TestResult("R-01", r01NameAr, r01NameTr, "memory", TestStatus.PASS, null, String.format(Locale.US, "%.1f", totalGb), "GB", null),
            TestResult("R-02", r02NameAr, r02NameTr, "memory", r02Status, r02Score,
                String.format(Locale.US, "%.1f", availGb), "GB", null),
            TestResult("R-03", r03NameAr, r03NameTr, "memory",
                if (mi.lowMemory) TestStatus.FAIL else TestStatus.PASS, null,
                if (mi.lowMemory) lowMemStr else normalMemStr, "", null)
        )
    }
}
