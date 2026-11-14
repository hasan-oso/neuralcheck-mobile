package net.neuralcheck.hardware.thermal

import android.content.Context
import net.neuralcheck.domain.model.TestResult
import net.neuralcheck.domain.model.TestStatus
import net.neuralcheck.service.collector.SystemFileReader
import java.util.Locale
import javax.inject.Inject

/** Thermal tests T-01 to T-04 — mostly computed from time-series summary. */
class ThermalTests @Inject constructor(
    private val systemFileReader: SystemFileReader,
    private val context: Context
) {
    fun runStaticTests(): List<TestResult> = listOf(testThermalZones())

    private fun testThermalZones(): TestResult {
        val zones = systemFileReader.readThermalZones()
        val count = zones.size
        val status = if (count > 0) TestStatus.PASS else TestStatus.WARNING
        val nameAr = context.getString(net.neuralcheck.R.string.test_t04_ar)
        val nameTr = context.getString(net.neuralcheck.R.string.test_t04_tr)
        return TestResult("T-04", nameAr, nameTr, "thermal", status, null, "$count", "zones", null)
    }

    fun computeMaxTemp(maxTemp: Float, tempSource: String = "cpu"): TestResult {
        val nameAr = context.getString(net.neuralcheck.R.string.test_t01_ar)
        val nameTr = context.getString(net.neuralcheck.R.string.test_t01_tr)
        if (maxTemp <= 0f) {
            return TestResult("T-01", nameAr, nameTr, "thermal",
                TestStatus.UNAVAILABLE, null, "N/A", "°C",
                context.getString(net.neuralcheck.R.string.thermal_source_none))
        }
        val status = when {
            maxTemp < 40f -> TestStatus.PASS
            maxTemp < 45f -> TestStatus.WARNING
            else -> TestStatus.FAIL
        }
        return TestResult("T-01", nameAr, nameTr, "thermal", status,
            (100f - maxTemp).coerceIn(0f, 100f),
            String.format(Locale.US, "%.1f", maxTemp), "°C", sourceNote(tempSource))
    }

    fun computeHeatRise(rate: Float, tempSource: String = "cpu"): TestResult {
        val nameAr = context.getString(net.neuralcheck.R.string.test_t02_ar)
        val nameTr = context.getString(net.neuralcheck.R.string.test_t02_tr)
        if (rate < 0f) {
            return TestResult("T-02", nameAr, nameTr, "thermal",
                TestStatus.UNAVAILABLE, null, "N/A", "°C/min",
                context.getString(net.neuralcheck.R.string.thermal_source_none))
        }
        // Phase 1.14: thresholds recalibrated after the T-02 metric became a
        // real °C/min rate in SamplerStats (was a raw °C delta pre-1.14, with
        // these same thresholds applied to a value 7-8× larger). New cutoffs
        // match device-class behaviour on V2's ~8 min stress profile:
        //   PASS    rate < 1.0  °C/min — flagship with vapor chamber, sustained light heating
        //   WARNING rate < 3.0  °C/min — mid-range or heavy thermal load
        //   FAIL    rate ≥ 3.0  °C/min — thermal-runaway concern
        val status = when {
            rate < 1.0f -> TestStatus.PASS
            rate < 3.0f -> TestStatus.WARNING
            else -> TestStatus.FAIL
        }
        return TestResult("T-02", nameAr, nameTr, "thermal", status, null,
            String.format(Locale.US, "%.2f", rate), "°C/min", sourceNote(tempSource))
    }

    fun computeCooldown(rate: Float, tempSource: String = "cpu"): TestResult {
        val nameAr = context.getString(net.neuralcheck.R.string.test_t03_ar)
        val nameTr = context.getString(net.neuralcheck.R.string.test_t03_tr)
        // NaN sentinel from upstream means "phase 7 was never captured"
        // (early-stopped scan, missing thermal channel). Any finite value
        // — including 0 and negative — is a real measurement.
        if (rate.isNaN()) {
            return TestResult("T-03", nameAr, nameTr, "thermal",
                TestStatus.UNAVAILABLE, null, "N/A", "°C/min",
                context.getString(net.neuralcheck.R.string.thermal_source_none))
        }
        // T-03 demoted to informational on 2026-05-14 after the 4-device
        // tester run (S10 5G −0.91, S24 Ultra +0.20, S21 Ultra 0.00,
        // A51 −0.30 °C/min) showed even a healthy mid-range device failed
        // the previous physics-tuned thresholds. The 60-second phase-7
        // window simply doesn't yield reliable cooling-rate measurements
        // across the device population — battery-temperature lag, ambient
        // coupling, and thermal mass differences dominate the signal.
        //
        // Until we either lengthen phase 7 or add a per-device baseline,
        // T-03 reports the raw rate for AI consumption but always grades
        // PASS so it cannot pull the overall thermal category down. The
        // note field carries the caveat through to the report UI.
        val informationalNote = context.getString(
            net.neuralcheck.R.string.thermal_t03_informational_note
        )
        val sourceNote = sourceNote(tempSource)
        val combinedNote = if (sourceNote.isNullOrBlank()) informationalNote
            else "$informationalNote — $sourceNote"
        return TestResult("T-03", nameAr, nameTr, "thermal", TestStatus.PASS, null,
            String.format(Locale.US, "%.2f", rate), "°C/min", combinedNote)
    }

    private fun sourceNote(tempSource: String): String? {
        val resId = when (tempSource) {
            "cpu" -> net.neuralcheck.R.string.thermal_source_cpu
            "gpu" -> net.neuralcheck.R.string.thermal_source_gpu
            "battery" -> net.neuralcheck.R.string.thermal_source_battery
            else -> return null
        }
        return context.getString(resId)
    }
}
