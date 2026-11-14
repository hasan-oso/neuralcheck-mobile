package net.neuralcheck.hardware.battery

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.util.Log
import net.neuralcheck.BuildConfig
import net.neuralcheck.domain.model.TestResult
import net.neuralcheck.domain.model.TestStatus
import net.neuralcheck.service.collector.BatteryCycleCount
import net.neuralcheck.service.collector.SystemFileReader
import java.io.File
import java.util.Locale
import javax.inject.Inject

/**
 * Battery functional tests B-01 through B-08.
 */
class BatteryTests @Inject constructor(
    private val context: Context,
    private val systemFileReader: SystemFileReader
) {
    companion object {
        // Extracted verbatim from the inline `cyclePaths` local val that used
        // to live inside logBatteryDiagnostics() (was BatteryTests.kt:170-196
        // before this extraction) so the datacollect-only Maxim fuel-gauge
        // probe (net.neuralcheck.diagnostics.BatteryProbeReceiver, source set
        // app/src/datacollect) can reuse the exact same 22 paths instead of
        // retyping them by hand. No path added, removed, or reordered here.
        // At the time of this extraction logBatteryDiagnostics()'s own
        // calling behavior (both flavors, unconditional per runAll()) was
        // unchanged — this list move was purely a name/location change.
        // That has since changed (A94): runAll() now gates the
        // logBatteryDiagnostics() call itself behind
        // BuildConfig.ENABLE_EXTENDED_INFO_TESTS, so it still fires in the
        // datacollect flavor but no longer in launch. This list stays
        // reachable and unconditional either way — BatteryProbeReceiver
        // reads CYCLE_COUNT_PROBE_PATHS directly, without going through
        // logBatteryDiagnostics() or runAll() at all.
        internal val CYCLE_COUNT_PROBE_PATHS: List<String> = listOf(
            "/sys/class/power_supply/battery/cycle_count",
            "/sys/class/power_supply/battery/batt_cycle",
            "/sys/class/power_supply/battery/battery_cycle",
            "/sys/class/power_supply/battery/cycle_count_id",
            "/sys/class/power_supply/battery/charge_cycle",
            "/sys/class/power_supply/battery/cycle",
            "/sys/class/power_supply/bms/cycle_count",
            "/sys/class/power_supply/bms/charge_cycle",
            "/sys/class/power_supply/sec_battery/cycle_count",
            "/sys/class/power_supply/sec-battery/cycle_count",
            "/sys/class/power_supply/main-battery/cycle_count",
            "/sys/class/power_supply/dc/cycle_count",
            "/sys/class/sec_battery/cycle_count",
            "/sys/class/sec/battery/cycle_count",
            "/sys/devices/platform/battery/power_supply/battery/cycle_count",
            "/sys/devices/platform/battery/cycle_count",
            // Samsung "battery_info" / "battery_history" directories — used on
            // some One UI builds to surface charge-cycle telemetry.
            "/sys/class/power_supply/battery/battery_cycle_count",
            "/sys/class/power_supply/battery/battery_health",
            "/sys/class/power_supply/battery/asoc",
            "/sys/class/power_supply/battery/aging",
            // Qualcomm BMS variants (just in case the device exposes both)
            "/sys/class/power_supply/bms/charge_cycles",
            "/sys/class/qcom-battery/cycle_count"
        )
    }

    // Hilt instantiates this class as part of the FunctionalTestRunner graph
    // when StressTestService.onCreate() injects its dependencies. A hard
    // ClassCastException here on Samsung One UI 6/7 (S24 Ultra) would crash
    // the service before its own try/catch in onCreate gets a chance to run.
    // The safe cast keeps the graph build alive — every individual test path
    // already null-guards `batteryManager` below.
    private val batteryManager: BatteryManager? = try {
        context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
    } catch (t: Throwable) {
        Log.w("NeuralCheck", "BATTERY_SERVICE unavailable in BatteryTests: ${t.message}")
        null
    }

    suspend fun runAll(): List<TestResult> {
        val intent = try {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        } catch (t: Throwable) {
            Log.w("NeuralCheck", "registerReceiver(BATTERY_CHANGED) failed: ${t.message}")
            null
        }
        // A94: this dump is technician/field-diagnosis output (device model,
        // every BatteryManager extra/property, and a scan of /sys/class/
        // power_supply/) — the same category of consumer-facing-vs-internal
        // split SecurityTests.kt (SEC-12) and SensorTests.kt (SN-13) already
        // gate behind this flag. It must not run, and therefore must not log,
        // in the launch flavor.
        if (BuildConfig.ENABLE_EXTENDED_INFO_TESTS) {
            try { logBatteryDiagnostics(intent) } catch (t: Throwable) {
                Log.w("NeuralCheck", "logBatteryDiagnostics failed: ${t.message}")
            }
        }
        return listOf(
            testHealthStatus(intent),
            testCapacity(intent),
            testTemperature(intent),
            testVoltage(intent),
            testCurrentDraw(),
            testTechnology(intent),
            testCycleCount(intent)
        )
    }

    // ─────────────── DIAGNOSTIC LOGGING (datacollect-only, A94) ───────────
    // Dumps every BatteryManager EXTRA, every BatteryManager.getIntProperty/
    // getLongProperty value, and every readable file under
    // /sys/class/power_supply/battery/ — so we can see why B-01 returns just
    // "GOOD" and B-02 sometimes resolves to UNAVAILABLE on Samsung devices
    // (Galaxy S10 5G / Exynos 9820 in particular). Originally unconditional
    // in both flavors ("Remove once diagnosed" — never removed). A94: gated
    // behind BuildConfig.ENABLE_EXTENDED_INFO_TESTS at the call site in
    // runAll() instead, so it keeps firing for field diagnosis in
    // datacollect builds but no longer runs (or logs) in launch.
    private fun logBatteryDiagnostics(intent: Intent?) {
        val tag = "NeuralCheck"
        Log.d(tag, "════════════════════ BATTERY DIAGNOSTICS ════════════════════")
        Log.d(tag, "Device: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, API ${Build.VERSION.SDK_INT})")

        if (intent == null) {
            Log.d(tag, "ACTION_BATTERY_CHANGED intent: null")
        } else {
            val health = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)
            val healthLabel = when (health) {
                BatteryManager.BATTERY_HEALTH_GOOD -> "GOOD"
                BatteryManager.BATTERY_HEALTH_DEAD -> "DEAD"
                BatteryManager.BATTERY_HEALTH_OVERHEAT -> "OVERHEAT"
                BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "OVER_VOLTAGE"
                BatteryManager.BATTERY_HEALTH_COLD -> "COLD"
                BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "UNSPECIFIED_FAILURE"
                BatteryManager.BATTERY_HEALTH_UNKNOWN -> "UNKNOWN"
                else -> "UNRECOGNIZED"
            }
            Log.d(tag, "EXTRA_HEALTH      = $health ($healthLabel)")
            Log.d(tag, "EXTRA_LEVEL       = ${intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)}")
            Log.d(tag, "EXTRA_SCALE       = ${intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)}")
            Log.d(tag, "EXTRA_VOLTAGE     = ${intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)} mV")
            Log.d(tag, "EXTRA_TEMPERATURE = ${intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)} (deci-°C)")
            Log.d(tag, "EXTRA_TECHNOLOGY  = ${intent.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY)}")
            Log.d(tag, "EXTRA_STATUS      = ${intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)}")
            Log.d(tag, "EXTRA_PLUGGED     = ${intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)}")
            Log.d(tag, "EXTRA_PRESENT     = ${intent.getBooleanExtra(BatteryManager.EXTRA_PRESENT, false)}")
        }

        fun safeIntProp(prop: Int): String = try {
            batteryManager?.getIntProperty(prop)?.toString() ?: "(BATTERY_SERVICE null)"
        } catch (e: Exception) {
            "ERROR(${e.javaClass.simpleName}:${e.message})"
        }
        fun safeLongProp(prop: Int): String = try {
            batteryManager?.getLongProperty(prop)?.toString() ?: "(BATTERY_SERVICE null)"
        } catch (e: Exception) {
            "ERROR(${e.javaClass.simpleName}:${e.message})"
        }

        Log.d(tag, "PROP_CAPACITY        = ${safeIntProp(BatteryManager.BATTERY_PROPERTY_CAPACITY)} %")
        Log.d(tag, "PROP_CHARGE_COUNTER  = ${safeIntProp(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)} µAh")
        Log.d(tag, "PROP_CURRENT_NOW     = ${safeIntProp(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)} µA")
        Log.d(tag, "PROP_CURRENT_AVERAGE = ${safeIntProp(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE)} µA")
        Log.d(tag, "PROP_ENERGY_COUNTER  = ${safeLongProp(BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER)} nWh")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Log.d(tag, "PROP_STATUS          = ${safeIntProp(BatteryManager.BATTERY_PROPERTY_STATUS)}")
        }

        // Known Samsung / generic Linux power-supply attributes that often
        // hold the "real" full-charge / cycle-count / capacity values when
        // Android's BatteryManager API returns blanks.
        val sysPaths = listOf(
            "capacity", "charge_counter", "charge_full", "charge_full_design",
            "cycle_count", "voltage_now", "voltage_max", "voltage_max_design",
            "temp", "health", "energy_full", "energy_full_design",
            "batt_capacity_max", "batt_misc_event", "current_now", "current_max",
            "current_avg", "status", "technology", "present", "model_name",
            "manufacturer", "type", "batt_full_capacity", "batt_chg_current",
            "batt_slate_mode"
        )
        Log.d(tag, "── /sys/class/power_supply/battery/ readouts ──")
        sysPaths.forEach { f ->
            val path = "/sys/class/power_supply/battery/$f"
            val file = File(path)
            val info = when {
                !file.exists() -> "(missing)"
                !file.canRead() -> "(exists, NOT readable)"
                else -> try {
                    file.readText().trim().take(200)
                } catch (e: Exception) {
                    "ERROR(${e.javaClass.simpleName}:${e.message})"
                }
            }
            Log.d(tag, "$path = $info")
        }

        // List every power_supply node so we can see if Samsung exposes
        // batt_capacity_max under a different supply (e.g. /battery_info,
        // /sec-battery, /bms, etc.) and which sysfs files each contains.
        Log.d(tag, "── /sys/class/power_supply/ inventory ──")
        try {
            val supplies = File("/sys/class/power_supply").listFiles()
            if (supplies == null) {
                Log.d(tag, "/sys/class/power_supply/ listing returned null")
            } else {
                supplies.forEach { dir ->
                    val children = try {
                        dir.list()?.sorted()?.joinToString(",") ?: "(no children)"
                    } catch (e: Exception) {
                        "ERROR(${e.message})"
                    }
                    Log.d(tag, "supply ${dir.name}: $children")
                }
            }
        } catch (e: Exception) {
            Log.d(tag, "power_supply enumeration failed: ${e.message}")
        }

        // ── Charge-cycle count probes (B-08 reads EXTRA_CYCLE_COUNT, then only the first path) ──
        // On Samsung Exynos devices the standard /sys/class/power_supply/
        // battery/cycle_count is frequently 0 or SELinux-denied. The real
        // cycle counter lives in vendor-specific nodes (sec_battery, bms,
        // battery_history, etc.) — enumerate every known variant so we can
        // see which one (if any) returns a sane non-zero integer.
        Log.d(tag, "── Cycle-count probes (every known sysfs alternative) ──")
        val cyclePaths = CYCLE_COUNT_PROBE_PATHS
        cyclePaths.forEach { path ->
            val file = File(path)
            val info = when {
                !file.exists() -> "(missing)"
                !file.canRead() -> "(exists, NOT readable)"
                else -> try {
                    val raw = file.readText().trim().take(200)
                    val parsed = raw.toIntOrNull()
                    if (parsed != null) "$raw  → parses to $parsed cycles" else "$raw  (NOT an integer)"
                } catch (e: SecurityException) {
                    "SECURITY DENIED(${e.message})"
                } catch (e: Exception) {
                    "ERROR(${e.javaClass.simpleName}:${e.message})"
                }
            }
            Log.d(tag, "$path = $info")
        }

        // Recursive scan: any file whose name contains "cycle" anywhere under
        // /sys/class/power_supply/ (depth 2 only, to avoid pathological loops).
        Log.d(tag, "── Recursive 'cycle' scan under /sys/class/power_supply/ ──")
        try {
            File("/sys/class/power_supply").listFiles()?.forEach { supply ->
                supply.listFiles()?.forEach { f ->
                    if (f.isFile && f.name.contains("cycle", ignoreCase = true)) {
                        val v = try {
                            if (f.canRead()) f.readText().trim().take(120) else "(NOT readable)"
                        } catch (e: Exception) {
                            "ERROR(${e.message})"
                        }
                        Log.d(tag, "${f.absolutePath} = $v")
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(tag, "cycle recursive scan failed: ${e.message}")
        }
        Log.d(tag, "Note: B-08 and the footer read EXTRA_CYCLE_COUNT first (API >= 34), then ONLY '${SystemFileReader.BATTERY_CYCLE_COUNT_PATH}' → ${try { File(SystemFileReader.BATTERY_CYCLE_COUNT_PATH).readText().trim() } catch (e: Exception) { "ERROR(${e.message})" }}")

        Log.d(tag, "═════════════════════════════════════════════════════════════")
    }

    // B-01: Battery Health Status
    private fun testHealthStatus(intent: Intent?): TestResult {
        val health = intent?.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN)
            ?: BatteryManager.BATTERY_HEALTH_UNKNOWN
        val (status, desc) = when (health) {
            BatteryManager.BATTERY_HEALTH_GOOD -> TestStatus.PASS to "GOOD"
            BatteryManager.BATTERY_HEALTH_DEAD -> TestStatus.FAIL to "DEAD"
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> TestStatus.FAIL to "FAILURE"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> TestStatus.WARNING to "OVERHEAT"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> TestStatus.WARNING to "OVER_VOLTAGE"
            BatteryManager.BATTERY_HEALTH_COLD -> TestStatus.WARNING to "COLD"
            else -> TestStatus.WARNING to "UNKNOWN"
        }
        val nameAr = context.getString(net.neuralcheck.R.string.test_b01_ar)
        val nameTr = context.getString(net.neuralcheck.R.string.test_b01_tr)
        return TestResult("B-01", nameAr, nameTr, "battery", status, null, desc, "", null)
    }

    // B-02: Battery Capacity (state of health).
    //
    // V2 fix #56 (2.5): unified through BatteryReader.snapshotHealth() which
    // already implements the multi-tier lookup (sysfs → SOH API → coulomb
    // extrapolation → reflection). This avoids two divergent implementations
    // (one here, one in BatteryReader) drifting over time. The notes field
    // surfaces the source tag so AI can see exactly which path produced
    // the value.
    //
    // The notes also include a cross-reference against KnownBatteryCapacities
    // when applicable — a "likely_single_cell_reading" classification means
    // the kernel reported half the published value (dual-cell halving), and
    // AI should double the figure before SoH math.
    private fun testCapacity(intent: Intent?): TestResult {
        val nameAr = context.getString(net.neuralcheck.R.string.test_b02_ar)
        val nameTr = context.getString(net.neuralcheck.R.string.test_b02_tr)

        // Build a transient BatteryReader to call snapshotHealth(). The
        // long-lived service-scoped reader is owned by StressTestService
        // and not available in this Hilt graph — registering+unregistering
        // a transient receiver here is cheap (~10ms) and keeps a single
        // source of truth for the design-capacity lookup logic.
        val reader = net.neuralcheck.service.collector.BatteryReader(context)
        runCatching { reader.register() }
        val health = runCatching { reader.snapshotHealth(systemFileReader) }
            .getOrNull()
        runCatching { reader.unregister() }

        if (health == null) {
            return TestResult("B-02", nameAr, nameTr, "battery",
                TestStatus.UNAVAILABLE, null, "N/A", "", null)
        }

        val knownCap = net.neuralcheck.util.KnownBatteryCapacities.get(
            Build.MODEL.orEmpty()
        )
        val capCls = net.neuralcheck.util.KnownBatteryCapacities.classifyAgainstKnown(
            sysfsDesignMah = health.designCapacityMah,
            knownDesignMah = knownCap
        )

        // fieldsVersion 63 (2026-05-18): B-02 reports design capacity ONLY.
        // The pre-63 versions also returned a "current capacity" /
        // "sohOfficialPercent" pair derived from chargeCounter ÷
        // batteryLevel at a single point in time. That estimator was
        // fundamentally unreliable (102 % on S24 Ultra, 100 % on 2019
        // S10 5G), so we removed both the SoH path and the current-
        // capacity / chargeCounter fallbacks rather than continuing to
        // report values we couldn't defend. Actual battery wear is now
        // surfaced via the 6 behavioral indicators in
        // footer.batteryHealth (cycleCount, dischargeRateUnderLoadMa,
        // voltageStabilityMv, batteryTempRiseC, ...) which the AI
        // interprets in combination per
        // docs/architecture/BATTERY_INDICATORS.md.
        val designMah = health.designCapacityMah
        return if (designMah != null && designMah > 0) {
            val notes = buildString {
                append("source=").append(health.designCapacitySource)
                if (knownCap != null) append(", known=$knownCap mAh, classify=$capCls")
            }
            TestResult("B-02", nameAr, nameTr, "battery",
                TestStatus.PASS, null,
                "$designMah", "mAh", notes)
        } else {
            TestResult("B-02", nameAr, nameTr, "battery",
                TestStatus.UNAVAILABLE, null, "N/A", "",
                "design_source=${health.designCapacitySource}")
        }
    }

    // B-03: Battery Temperature
    private fun testTemperature(intent: Intent?): TestResult {
        val tempRaw = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        val tempC = tempRaw / 10f
        val status = when {
            tempC in 20f..35f -> TestStatus.PASS
            tempC < 15f -> TestStatus.WARNING
            tempC > 38f -> TestStatus.WARNING
            else -> TestStatus.PASS
        }
        val nameAr = context.getString(net.neuralcheck.R.string.test_b03_ar)
        val nameTr = context.getString(net.neuralcheck.R.string.test_b03_tr)
        return TestResult("B-03", nameAr, nameTr, "battery", status, null, String.format(Locale.US, "%.1f", tempC), "°C", null)
    }

    // B-04: Battery Voltage
    private fun testVoltage(intent: Intent?): TestResult {
        val voltageMv = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
        val status = when {
            voltageMv in 3500..4400 -> TestStatus.PASS
            voltageMv < 3300 -> TestStatus.WARNING
            voltageMv > 4450 -> TestStatus.WARNING
            else -> TestStatus.PASS
        }
        val nameAr = context.getString(net.neuralcheck.R.string.test_b04_ar)
        val nameTr = context.getString(net.neuralcheck.R.string.test_b04_tr)
        return TestResult("B-04", nameAr, nameTr, "battery", status, null, "$voltageMv", "mV", null)
    }

    // B-05: Current Draw
    private fun testCurrentDraw(): TestResult {
        val currentUa = try {
            batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) ?: 0
        } catch (_: Exception) { 0 }
        val status = when {
            currentUa < 0 -> TestStatus.PASS // Discharging (negative = drawing current)
            currentUa == 0 -> TestStatus.WARNING
            else -> TestStatus.WARNING // Positive while should be unplugged
        }
        val nameAr = context.getString(net.neuralcheck.R.string.test_b05_ar)
        val nameTr = context.getString(net.neuralcheck.R.string.test_b05_tr)
        return TestResult("B-05", nameAr, nameTr, "battery", status, null, "$currentUa", "µA", null)
    }

    // B-06: Drain Rate — computed from time-series after test (placeholder)
    fun computeDrainRate(startLevel: Int, endLevel: Int, durationMinutes: Float): TestResult {
        val drainRate = if (durationMinutes > 0) (startLevel - endLevel).toFloat() / durationMinutes else 0f
        val status = when {
            drainRate < 1f -> TestStatus.PASS
            drainRate < 1.5f -> TestStatus.WARNING
            else -> TestStatus.FAIL
        }
        val nameAr = context.getString(net.neuralcheck.R.string.test_b06_ar)
        val nameTr = context.getString(net.neuralcheck.R.string.test_b06_tr)
        return TestResult("B-06", nameAr, nameTr, "battery", status, (100f - drainRate * 10f).coerceIn(0f, 100f),
            String.format(Locale.US, "%.2f", drainRate), "%/min", null)
    }

    // B-07: Battery Technology
    private fun testTechnology(intent: Intent?): TestResult {
        val tech = intent?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: ""
        val status = if (tech.isNotBlank() && tech.lowercase() != "unknown") TestStatus.PASS else TestStatus.WARNING
        val nameAr = context.getString(net.neuralcheck.R.string.test_b07_ar)
        val nameTr = context.getString(net.neuralcheck.R.string.test_b07_tr)
        return TestResult("B-07", nameAr, nameTr, "battery", status, null, tech, "", null)
    }

    // B-08: Cycle Count. fieldsVersion 68 (T-02): the SAME reader the footer's batteryHealth.cycleCount uses
    // (BatteryCycleCount.read: EXTRA_CYCLE_COUNT of the battery broadcast on API >= 34, then sysfs), so the
    // test and the footer cannot disagree on one battery. A count that could not be read is UNAVAILABLE.
    private fun testCycleCount(intent: Intent?): TestResult {
        val cycles = BatteryCycleCount.read(
            intent = intent,
            sdkInt = Build.VERSION.SDK_INT,
            readSysfs = { path -> systemFileReader.readBatteryCycleCountAt(path) }
        ).count
        val nameAr = context.getString(net.neuralcheck.R.string.test_b08_ar)
        val nameTr = context.getString(net.neuralcheck.R.string.test_b08_tr)
        return if (cycles != null && cycles >= 0) {
            val status = when {
                cycles < 300 -> TestStatus.PASS
                cycles < 500 -> TestStatus.PASS
                cycles < 800 -> TestStatus.WARNING
                else -> TestStatus.FAIL
            }
            val score = when {
                cycles < 300 -> 100f
                cycles < 500 -> 80f
                cycles < 800 -> 50f
                else -> 20f
            }
            TestResult("B-08", nameAr, nameTr, "battery", status, score, "$cycles", "cycles", null)
        } else {
            TestResult("B-08", nameAr, nameTr, "battery", TestStatus.UNAVAILABLE, null, "N/A", "", null)
        }
    }
}
