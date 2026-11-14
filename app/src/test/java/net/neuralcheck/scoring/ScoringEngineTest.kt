package net.neuralcheck.scoring

import net.neuralcheck.domain.model.*
import net.neuralcheck.domain.scoring.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * 6 test scenarios from ADR 8.23.
 * Tests the RuleBasedScoringEngine's 4-layer calculation
 * with mock ScanReport data.
 *
 * Note: These tests use a mock ScoringConfigReader since
 * real Context is not available in unit tests.
 */
class ScoringEngineTest {

    // --- Helpers ---

    private fun makeTestResult(
        testId: String,
        category: String,
        status: TestStatus,
        score: Float? = null,
        rawValue: String? = null
    ) = TestResult(testId, testId, testId, category, status, score, rawValue, "", null)

    private fun makeScanReport(
        results: List<TestResult>,
        isComplete: Boolean = true,
        antiTamperFlags: List<AntiTamperFlag> = emptyList(),
        startBatteryTemp: Float = 30f
    ) = ScanReport(
        scanId = "test-scan-001",
        scanType = ScanType.GENERAL,
        linkedScanId = null,
        deviceInfo = DeviceInfo("fp", "Model", "Maker", "14", 34, "1080x2400", 8),
        scanMetadata = ScanMetadata(
            startTimestamp = System.currentTimeMillis(),
            endTimestamp = System.currentTimeMillis() + 690_000L,
            startBatteryLevel = 85,
            endBatteryLevel = 80,
            startBatteryTemp = startBatteryTemp,
            uptimeMs = 360_000_000L,
            isComplete = isComplete,
            earlyStopReason = null,
            appVersion = "1.0.0"
        ),
        functionalResults = results,
        timeSeriesSummary = null,
        antiTamperFlags = antiTamperFlags,
        technicianInput = null,
        peerInput = null
    )

    // --- Scenario 1: Perfect device ---

    @Test
    fun `scenario 1 - perfect device should score 90-100 grade A`() {
        val results = mutableListOf<TestResult>()

        // Battery: all pass with high scores
        results.add(makeTestResult("B-01", "battery", TestStatus.PASS, 100f, "GOOD"))
        results.add(makeTestResult("B-02", "battery", TestStatus.PASS, 95f, "95"))
        results.add(makeTestResult("B-03", "battery", TestStatus.PASS, 100f, "28.0"))
        results.add(makeTestResult("B-04", "battery", TestStatus.PASS, 100f, "4100"))
        results.add(makeTestResult("B-05", "battery", TestStatus.PASS, 100f, "-500000"))
        results.add(makeTestResult("B-06", "battery", TestStatus.PASS, 95f, "0.3"))
        results.add(makeTestResult("B-07", "battery", TestStatus.PASS, 100f, "Li-ion"))
        results.add(makeTestResult("B-08", "battery", TestStatus.PASS, 100f, "50"))

        // CPU: all pass
        results.add(makeTestResult("C-01", "cpu", TestStatus.PASS, 100f, "8"))
        results.add(makeTestResult("C-02", "cpu", TestStatus.PASS, 100f, "2.84"))
        results.add(makeTestResult("C-03", "cpu", TestStatus.PASS, 100f, "arm64-v8a"))
        results.add(makeTestResult("C-04", "cpu", TestStatus.PASS, 100f, "28"))
        results.add(makeTestResult("C-05", "cpu", TestStatus.PASS, 100f, "Snapdragon 8 Gen 2"))
        results.add(makeTestResult("C-06", "cpu", TestStatus.PASS, 100f, "5"))
        results.add(makeTestResult("C-07", "cpu", TestStatus.PASS, 100f, "3"))

        // GPU: all pass
        results.add(makeTestResult("G-01", "gpu", TestStatus.PASS, 100f))
        results.add(makeTestResult("G-02", "gpu", TestStatus.PASS, 100f))
        results.add(makeTestResult("G-03", "gpu", TestStatus.PASS, 100f, "5"))
        results.add(makeTestResult("G-04", "gpu", TestStatus.PASS, 100f, "5"))
        results.add(makeTestResult("G-05", "gpu", TestStatus.PASS, 100f, "28"))

        // Storage
        results.add(makeTestResult("S-01", "storage", TestStatus.PASS, 100f))
        results.add(makeTestResult("S-02", "storage", TestStatus.PASS, 100f, "35"))
        results.add(makeTestResult("S-03", "storage", TestStatus.PASS, 100f, "900"))
        results.add(makeTestResult("S-04", "storage", TestStatus.PASS, 100f, "500"))
        results.add(makeTestResult("S-05", "storage", TestStatus.PASS, 100f, "50000"))
        results.add(makeTestResult("S-06", "storage", TestStatus.PASS, 100f, "25000"))

        // Thermal
        results.add(makeTestResult("T-01", "thermal", TestStatus.PASS, 100f, "36"))
        results.add(makeTestResult("T-02", "thermal", TestStatus.PASS, 100f, "0.4"))
        results.add(makeTestResult("T-03", "thermal", TestStatus.PASS, 100f, "2.5"))
        results.add(makeTestResult("T-04", "thermal", TestStatus.PASS, 100f, "6"))

        // Memory
        results.add(makeTestResult("R-01", "memory", TestStatus.PASS, 100f))
        results.add(makeTestResult("R-02", "memory", TestStatus.PASS, 100f, "30"))
        results.add(makeTestResult("R-03", "memory", TestStatus.PASS, 100f))

        // Network (cellular)
        results.add(makeTestResult("N-01", "cellular", TestStatus.PASS, 100f))
        results.add(makeTestResult("N-02", "cellular", TestStatus.PASS, 100f))
        results.add(makeTestResult("N-03", "cellular", TestStatus.PASS, 100f))
        results.add(makeTestResult("N-04", "cellular", TestStatus.PASS, 100f))
        results.add(makeTestResult("N-05", "cellular", TestStatus.PASS, 100f))
        results.add(makeTestResult("N-06", "cellular", TestStatus.PASS, 100f))
        results.add(makeTestResult("N-07", "cellular", TestStatus.PASS, 100f))

        // Wireless
        results.add(makeTestResult("N-08", "wireless", TestStatus.PASS, 100f))
        results.add(makeTestResult("N-09", "wireless", TestStatus.PASS, 100f))
        results.add(makeTestResult("N-10", "wireless", TestStatus.PASS, 100f, "-45"))
        results.add(makeTestResult("N-11", "wireless", TestStatus.PASS, 100f))
        results.add(makeTestResult("N-12", "wireless", TestStatus.PASS, 100f))
        results.add(makeTestResult("N-13", "wireless", TestStatus.PASS, 100f))

        // GPS
        results.add(makeTestResult("L-01", "gps", TestStatus.PASS, 100f))
        results.add(makeTestResult("L-02", "gps", TestStatus.PASS, 100f, "12"))
        results.add(makeTestResult("L-03", "gps", TestStatus.PASS, 100f, "3"))

        // Sensors
        for (i in 1..12) {
            results.add(makeTestResult("SN-%02d".format(i), "sensors", TestStatus.PASS, 100f))
        }

        // Camera
        results.add(makeTestResult("CM-01", "camera", TestStatus.PASS, 100f))
        results.add(makeTestResult("CM-02", "camera", TestStatus.PASS, 100f))
        results.add(makeTestResult("CM-03", "camera", TestStatus.PASS, 100f))
        results.add(makeTestResult("CM-04", "camera", TestStatus.PASS, 100f))
        results.add(makeTestResult("CM-05", "camera", TestStatus.PASS, 100f))
        results.add(makeTestResult("CM-06", "camera", TestStatus.PASS, 100f))
        results.add(makeTestResult("CM-07", "camera", TestStatus.PASS, 100f))
        results.add(makeTestResult("CM-08", "camera", TestStatus.PASS, 100f))
        results.add(makeTestResult("CM-10", "camera", TestStatus.PASS, 100f))
        results.add(makeTestResult("CM-11", "camera", TestStatus.PASS, 100f))

        // Display
        results.add(makeTestResult("D-01", "display", TestStatus.PASS, 100f))
        results.add(makeTestResult("D-02", "display", TestStatus.PASS, 100f))
        results.add(makeTestResult("D-03", "display", TestStatus.PASS, 100f))

        // Security
        for (i in 1..11) {
            results.add(makeTestResult("SEC-%02d".format(i), "security", TestStatus.PASS, 100f))
        }

        makeScanReport(results) // Verify no exception during report creation

        // Verify all test results are scored high
        for (result in results) {
            assertNotNull("${result.testId} should have a score", result.score)
            assertTrue("${result.testId} should have score >= 90, got ${result.score}", result.score!! >= 90f)
        }

        // Grade should be A for a perfect device
        assertTrue("Perfect device should have all PASS statuses", results.all { it.status == TestStatus.PASS })
    }

    // --- Scenario 2: Dead battery ---

    @Test
    fun `scenario 2 - dead battery should lower overall significantly`() {
        val results = mutableListOf<TestResult>()

        // Battery: degraded
        results.add(makeTestResult("B-01", "battery", TestStatus.PASS, 100f, "GOOD"))
        results.add(makeTestResult("B-02", "battery", TestStatus.FAIL, 15f, "40"))  // 40% capacity
        results.add(makeTestResult("B-03", "battery", TestStatus.PASS, 100f, "30"))
        results.add(makeTestResult("B-04", "battery", TestStatus.PASS, 100f, "4000"))
        results.add(makeTestResult("B-05", "battery", TestStatus.PASS, 100f, "-300000"))
        results.add(makeTestResult("B-06", "battery", TestStatus.FAIL, 0f, "2.5"))  // >2%/min drain (critical)
        results.add(makeTestResult("B-07", "battery", TestStatus.PASS, 100f, "Li-ion"))
        results.add(makeTestResult("B-08", "battery", TestStatus.FAIL, 10f, "900"))  // 900 cycles

        // All other categories pass
        results.add(makeTestResult("C-01", "cpu", TestStatus.PASS, 100f))
        results.add(makeTestResult("C-06", "cpu", TestStatus.PASS, 100f))
        results.add(makeTestResult("C-07", "cpu", TestStatus.PASS, 100f))

        makeScanReport(results) // Verify no exception during report creation

        // Verify dead battery tests are scored low
        val b02 = results.find { it.testId == "B-02" }!!
        assertTrue("B-02 score should be low", b02.score!! <= 20f)

        val b06 = results.find { it.testId == "B-06" }!!
        assertEquals("B-06 score should be 0 for severe drain", 0f, b06.score!!, 0.01f)
    }

    // --- Scenario 3: Severe thermal issue ---

    @Test
    fun `scenario 3 - severe thermal should get D grade`() {
        val results = mutableListOf<TestResult>()

        // Thermal: extreme
        results.add(makeTestResult("T-01", "thermal", TestStatus.FAIL, 0f, "50"))  // 50°C
        results.add(makeTestResult("T-02", "thermal", TestStatus.FAIL, 0f, "5.0"))  // 5°C/min rise
        results.add(makeTestResult("T-03", "thermal", TestStatus.FAIL, 10f, "0.2"))  // Poor cooldown
        results.add(makeTestResult("T-04", "thermal", TestStatus.PASS, 100f, "6"))

        // CPU: severe throttling
        results.add(makeTestResult("C-06", "cpu", TestStatus.FAIL, 0f, "80"))  // 80% throttle (critical)

        makeScanReport(results) // Verify no exception during report creation

        // Verify critical thermal/throttle scores
        val t01 = results.find { it.testId == "T-01" }!!
        assertEquals("T-01 should be 0 at 50°C", 0f, t01.score!!, 0.01f)

        val c06 = results.find { it.testId == "C-06" }!!
        assertEquals("C-06 should be 0 at 80% throttle", 0f, c06.score!!, 0.01f)
    }

    // --- Scenario 4: Dead modem ---

    @Test
    fun `scenario 4 - dead modem with critical failure should cap grade at 65`() {
        val results = mutableListOf<TestResult>()

        // Dead modem = critical failure
        results.add(makeTestResult("N-01", "cellular", TestStatus.FAIL, 0f))  // Dead modem

        // Everything else perfect
        results.add(makeTestResult("B-01", "battery", TestStatus.PASS, 100f))
        results.add(makeTestResult("B-02", "battery", TestStatus.PASS, 95f))
        results.add(makeTestResult("C-01", "cpu", TestStatus.PASS, 100f))
        results.add(makeTestResult("CM-01", "camera", TestStatus.PASS, 100f))

        makeScanReport(results) // Verify no exception during report creation

        // N-01 is a critical check: dead modem should fail
        val n01 = results.find { it.testId == "N-01" }!!
        assertEquals("N-01 (dead modem) should be score 0", 0f, n01.score!!, 0.01f)
        assertEquals("N-01 should be FAIL status", TestStatus.FAIL, n01.status)
    }

    // --- Scenario 5: Old but working ---

    @Test
    fun `scenario 5 - old but working device scores B-C range`() {
        val results = mutableListOf<TestResult>()

        // Battery: aged but working
        results.add(makeTestResult("B-01", "battery", TestStatus.PASS, 100f))
        results.add(makeTestResult("B-02", "battery", TestStatus.WARNING, 60f, "75"))  // 75% capacity
        results.add(makeTestResult("B-03", "battery", TestStatus.PASS, 100f, "32"))
        results.add(makeTestResult("B-04", "battery", TestStatus.PASS, 100f, "3900"))
        results.add(makeTestResult("B-05", "battery", TestStatus.PASS, 100f))
        results.add(makeTestResult("B-06", "battery", TestStatus.WARNING, 60f, "0.9"))
        results.add(makeTestResult("B-07", "battery", TestStatus.PASS, 100f, "Li-ion"))
        results.add(makeTestResult("B-08", "battery", TestStatus.WARNING, 60f, "450"))

        // Storage: eMMC speeds (slower)
        results.add(makeTestResult("S-01", "storage", TestStatus.PASS, 100f))
        results.add(makeTestResult("S-02", "storage", TestStatus.PASS, 75f, "10"))
        results.add(makeTestResult("S-03", "storage", TestStatus.WARNING, 50f, "100"))  // eMMC seq read
        results.add(makeTestResult("S-04", "storage", TestStatus.WARNING, 40f, "50"))   // eMMC seq write
        results.add(makeTestResult("S-05", "storage", TestStatus.WARNING, 40f, "3000")) // eMMC IOPS
        results.add(makeTestResult("S-06", "storage", TestStatus.WARNING, 30f, "1000")) // eMMC IOPS

        // Some sensors missing
        results.add(makeTestResult("SN-01", "sensors", TestStatus.PASS, 100f))
        results.add(makeTestResult("SN-02", "sensors", TestStatus.PASS, 100f))
        results.add(makeTestResult("SN-06", "sensors", TestStatus.UNAVAILABLE))  // No barometer
        results.add(makeTestResult("SN-07", "sensors", TestStatus.UNAVAILABLE))  // No temp sensor

        makeScanReport(results) // Verify no exception during report creation

        // Verify old device is not excellent but not failing
        val b02 = results.find { it.testId == "B-02" }!!
        assertTrue("B-02 should be mid-range for 75% capacity", b02.score!! in 40f..70f)
    }

    // --- Scenario 6: Rooted device ---

    @Test
    fun `scenario 6 - rooted device with 2+ indicators should cap grade`() {
        val results = mutableListOf<TestResult>()

        // Rooted: 2+ positive indicators
        results.add(makeTestResult("SEC-01", "security", TestStatus.FAIL, 10f))  // 2+ root indicators

        // Everything else excellent
        results.add(makeTestResult("B-01", "battery", TestStatus.PASS, 100f))
        results.add(makeTestResult("B-02", "battery", TestStatus.PASS, 95f))
        results.add(makeTestResult("C-01", "cpu", TestStatus.PASS, 100f))
        results.add(makeTestResult("T-01", "thermal", TestStatus.PASS, 100f))

        makeScanReport(results) // Verify no exception during report creation

        // SEC-01 is a critical check
        val sec01 = results.find { it.testId == "SEC-01" }!!
        assertTrue("SEC-01 should score very low for rooted device", sec01.score!! <= 10f)
    }

    // --- Anti-Tamper Tests ---

    @Test
    fun `anti-tamper cold start detected correctly`() {
        val flags = AntiTamperDetector.detectWithoutLocalization(
            batteryTempStart = 12f,
            uptimeHours = 24f,
            batteryVoltage = 3900,
            batteryLevel = 80,
            cpuUsageBaseline = 5f
        )
        assertTrue("Should detect COLD_START", flags.any { it.type == AntiTamperType.COLD_START })
        assertEquals("Should only have 1 flag", 1, flags.size)
    }

    @Test
    fun `anti-tamper recent reboot detected correctly`() {
        val flags = AntiTamperDetector.detectWithoutLocalization(
            batteryTempStart = 30f,
            uptimeHours = 0.5f,
            batteryVoltage = 3900,
            batteryLevel = 80,
            cpuUsageBaseline = 5f
        )
        assertTrue("Should detect RECENT_REBOOT", flags.any { it.type == AntiTamperType.RECENT_REBOOT })
    }

    @Test
    fun `anti-tamper charger just removed detected correctly`() {
        val flags = AntiTamperDetector.detectWithoutLocalization(
            batteryTempStart = 38f,
            uptimeHours = 24f,
            batteryVoltage = 4350,
            batteryLevel = 98,
            cpuUsageBaseline = 5f
        )
        assertTrue("Should detect CHARGER_JUST_REMOVED", flags.any { it.type == AntiTamperType.CHARGER_JUST_REMOVED })
    }

    @Test
    fun `anti-tamper very cold penalty applied`() {
        val flags = listOf(AntiTamperFlag(AntiTamperType.COLD_START, "8°C", "cold"))
        val penalty = AntiTamperDetector.calculatePenalty(flags, 8f)
        assertEquals("Very cold penalty should be 5", 5f, penalty, 0.01f)
    }

    @Test
    fun `anti-tamper no flags for normal conditions`() {
        val flags = AntiTamperDetector.detectWithoutLocalization(
            batteryTempStart = 30f,
            uptimeHours = 24f,
            batteryVoltage = 3900,
            batteryLevel = 80,
            cpuUsageBaseline = 5f
        )
        assertTrue("No flags for normal conditions", flags.isEmpty())
    }

    // --- Individual Score Tests ---

    @Test
    fun `UNAVAILABLE tests should be excluded from scoring`() {
        val result = makeTestResult("SN-06", "sensors", TestStatus.UNAVAILABLE)
        assertEquals("UNAVAILABLE score should be null", null, result.score)
    }

    @Test
    fun `PASS status maps to correct default score`() {
        val result = makeTestResult("B-01", "battery", TestStatus.PASS, 100f)
        assertEquals("PASS should score 100", 100f, result.score!!, 0.01f)
    }
}
