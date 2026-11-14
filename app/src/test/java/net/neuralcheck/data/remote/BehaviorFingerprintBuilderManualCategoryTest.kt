package net.neuralcheck.data.remote

import com.google.gson.Gson
import net.neuralcheck.domain.model.DeviceInfo
import net.neuralcheck.domain.model.ScanMetadata
import net.neuralcheck.domain.model.ScanReport
import net.neuralcheck.domain.model.ScanType
import net.neuralcheck.domain.model.TestResult
import net.neuralcheck.domain.model.TestStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Bug 5 fix (2026-05-17): verifies that [BehaviorFingerprintBuilder.build]
 * propagates `categoryName` from every M-* row in `scanReport.functionalResults`
 * onto the corresponding `manualResults` DTO, and that the resulting wire JSON
 * includes the field per the new V6 contract.
 *
 * Reference: docs/audits/bug_5_manual_tests/findings.md
 */
class BehaviorFingerprintBuilderManualCategoryTest {

    private val builder = BehaviorFingerprintBuilder()
    private val gson = Gson()

    /** Builds a minimal ScanReport carrying a custom set of TestResult rows. */
    private fun fakeScanReport(rows: List<TestResult>): ScanReport = ScanReport(
        scanId = "test-scan-id",
        scanType = ScanType.GENERAL,
        linkedScanId = null,
        deviceInfo = DeviceInfo(
            fingerprint = "fp",
            model = "TestModel",
            manufacturer = "TestMfr",
            androidVersion = "14",
            apiLevel = 34,
            screenResolution = "1080x2400",
            cpuCores = 8
        ),
        scanMetadata = ScanMetadata(
            startTimestamp = 0L,
            endTimestamp = 0L,
            startBatteryLevel = 100,
            endBatteryLevel = 95,
            startBatteryTemp = 27f,
            uptimeMs = 0L,
            isComplete = true,
            earlyStopReason = null,
            appVersion = "1.0.0"
        ),
        functionalResults = rows,
        timeSeriesSummary = null,
        antiTamperFlags = emptyList(),
        technicianInput = null,
        peerInput = null
    )

    /** Helper for one M-* row with the given category. Positional order matches
     *  TestResult: testId, componentName, componentNameAr, categoryName, status,
     *  score, rawValue, rawValueUnit, notes. */
    private fun manualRow(
        testId: String,
        status: TestStatus,
        categoryName: String
    ): TestResult = TestResult(
        testId = testId,
        componentName = testId,
        componentNameAr = testId,
        categoryName = categoryName,
        status = status,
        score = if (status == TestStatus.PASS) 100f else null,
        rawValue = null,
        rawValueUnit = null,
        notes = "manual"
    )

    private fun funcRow(
        testId: String,
        categoryName: String,
        status: TestStatus = TestStatus.PASS
    ): TestResult = TestResult(
        testId = testId,
        componentName = testId,
        componentNameAr = testId,
        categoryName = categoryName,
        status = status,
        score = 100f,
        rawValue = "x",
        rawValueUnit = "u",
        notes = null
    )

    // ─── Scenario 1: every M-* row produces a DTO with the matching category ───

    @Test
    fun `each M-row propagates its categoryName onto the wire DTO`() {
        val report = fakeScanReport(
            listOf(
                manualRow("M-01", TestStatus.PASS,       "display"),
                manualRow("M-17", TestStatus.PASS,       "camera"),
                manualRow("M-22", TestStatus.FAIL,       "camera"),
                manualRow("M-08", TestStatus.NOT_TESTED, "camera"),
                manualRow("M-12", TestStatus.PASS,       "battery")
            )
        )
        val fp = builder.build(report, timeSeries = null)
        // funcDtos must be empty — all rows started with M-
        assertEquals(0, fp.functionalResults.size)
        // manualDtos must carry the categoryName through unchanged
        assertEquals(5, fp.manualResults.size)
        assertEquals("display",  fp.manualResults.first { it.testId == "M-01" }.categoryName)
        assertEquals("camera",   fp.manualResults.first { it.testId == "M-17" }.categoryName)
        assertEquals("camera",   fp.manualResults.first { it.testId == "M-22" }.categoryName)
        assertEquals("camera",   fp.manualResults.first { it.testId == "M-08" }.categoryName)
        assertEquals("battery",  fp.manualResults.first { it.testId == "M-12" }.categoryName)
    }

    // ─── Scenario 2: legacy "manual" sentinel is rewritten to null ───

    @Test
    fun `legacy manual sentinel category is rewritten to null on the wire`() {
        // ManualTestsViewModel.setResult writes categoryName = "manual" when
        // TestSpec.fromId returns null (unknown M-* id). Sending the literal
        // string "manual" to the AI would mean the AI sees a non-canonical
        // category value. The fix swaps it for null.
        val report = fakeScanReport(
            listOf(manualRow("M-99", TestStatus.PASS, "manual"))
        )
        val fp = builder.build(report, timeSeries = null)
        assertEquals(1, fp.manualResults.size)
        assertNull(
            "expected null when source row carries the \"manual\" sentinel",
            fp.manualResults[0].categoryName
        )
    }

    // ─── Scenario 3: empty / blank categoryName is also null on the wire ───

    @Test
    fun `blank categoryName is rewritten to null on the wire`() {
        val report = fakeScanReport(
            listOf(manualRow("M-01", TestStatus.PASS, "  "))
        )
        val fp = builder.build(report, timeSeries = null)
        assertNull(fp.manualResults[0].categoryName)
    }

    // ─── Scenario 4: serialised JSON includes the new field ───

    @Test
    fun `serialised manualResults JSON includes categoryName key when populated`() {
        val report = fakeScanReport(
            listOf(manualRow("M-17", TestStatus.PASS, "camera"))
        )
        val fp = builder.build(report, timeSeries = null)
        val json = gson.toJson(fp.manualResults)
        // The JSON should be an array with one object containing all three keys
        assertTrue(
            "expected categoryName key in JSON, got: $json",
            json.contains("\"categoryName\":\"camera\"")
        )
        assertTrue(json.contains("\"testId\":\"M-17\""))
        assertTrue(json.contains("\"status\":\"pass\""))
    }

    @Test
    fun `serialised manualResults JSON omits categoryName key when null (Gson default)`() {
        // Gson's default behaviour drops null fields from output. This means an
        // older APK that ships an unpopulated row produces a {testId, status}
        // pair identical to the pre-1.14 wire shape — backwards compatible.
        val report = fakeScanReport(
            listOf(manualRow("M-99", TestStatus.PASS, "manual"))
        )
        val fp = builder.build(report, timeSeries = null)
        val json = gson.toJson(fp.manualResults)
        assertTrue(
            "categoryName should NOT appear when null; got: $json",
            !json.contains("categoryName")
        )
    }

    // ─── Scenario 5: split between funcDtos and manualDtos is unchanged ───

    @Test
    fun `non-M testId still routes to functionalResults, M-prefix routes to manual`() {
        val report = fakeScanReport(
            listOf(
                funcRow("B-01",  "battery"),
                funcRow("CM-01", "camera"),
                funcRow("T-01",  "thermal"),
                manualRow("M-01", TestStatus.PASS, "display"),
                manualRow("M-17", TestStatus.PASS, "camera")
            )
        )
        val fp = builder.build(report, timeSeries = null)
        assertEquals(3, fp.functionalResults.size)
        assertEquals(2, fp.manualResults.size)
        // Quick sanity: funcDtos still carry their categoryName via their own
        // DTO (unaffected by this commit).
        assertNotNull(fp.functionalResults.first { it.testId == "B-01" }.categoryName)
    }

    // ─── Scenario 6: all-not_tested case (Samsung S24 Ultra production scan) ───

    @Test
    fun `every M-row marked not_tested still ships its categoryName`() {
        // Reproduces production scan b69da673 (Samsung S24 Ultra) where the
        // user tapped Continue on ManualTestsScreen without answering anything
        // — every row landed as not_tested. The categoryName must still flow
        // so the AI can attribute each "not_tested" to the correct category
        // (otherwise it can't tell that camera and display both had data
        // available but unanswered, vs the user being on a device without
        // those features).
        //
        // Categories below reflect the post-rebucketing TestSpec catalog
        // (Bug 5 follow-up, commit 5d6ddf4) — audio for speakers/mics/jack,
        // input for hardware buttons, security for biometrics, sensors for
        // vibration.
        val ids = listOf(
            "M-01" to "display",  "M-02" to "display",  "M-03" to "display",
            "M-04" to "audio",    "M-05" to "audio",    "M-06" to "audio",
            "M-07" to "audio",    "M-08" to "audio",
            "M-09" to "input",    "M-10" to "input",    "M-11" to "input",
            "M-12" to "battery",  "M-14" to "battery",  "M-15" to "storage",
            "M-17" to "camera",   "M-18" to "camera",   "M-19" to "camera",
            "M-20" to "security", "M-21" to "security",
            "M-22" to "sensors"
        )
        val report = fakeScanReport(
            ids.map { (id, cat) -> manualRow(id, TestStatus.NOT_TESTED, cat) }
        )
        val fp = builder.build(report, timeSeries = null)
        assertEquals(20, fp.manualResults.size)
        fp.manualResults.forEach { dto ->
            assertEquals("not_tested", dto.status)
            assertNotNull(
                "Row ${dto.testId} lost its categoryName",
                dto.categoryName
            )
        }
        // Distribution check (post-rebucketing):
        //   display=3, audio=5, input=3, battery=2, storage=1,
        //   camera=3,  security=2, sensors=1  →  Σ = 20
        val byCategory = fp.manualResults.groupingBy { it.categoryName!! }.eachCount()
        assertEquals(3, byCategory["display"])
        assertEquals(5, byCategory["audio"])
        assertEquals(3, byCategory["input"])
        assertEquals(2, byCategory["battery"])
        assertEquals(1, byCategory["storage"])
        assertEquals(3, byCategory["camera"])    // M-17, M-18, M-19 only
        assertEquals(2, byCategory["security"])  // M-20, M-21 (biometrics)
        assertEquals(1, byCategory["sensors"])   // M-22 vibration
    }

    // ─── Scenario 8: M-04 specifically is NOT camera after rebucketing ───

    @Test
    fun `M-04 external speaker now reports audio category, not camera`() {
        // Direct regression guard: production Xiaomi scan 7a974e15 had
        // M-04 = pass and the AI graded camera = "?". After rebucketing,
        // M-04's pass signal flows to audio instead of camera so it can
        // no longer falsely inflate the camera grade.
        val report = fakeScanReport(
            listOf(manualRow("M-04", TestStatus.PASS, "audio"))
        )
        val fp = builder.build(report, timeSeries = null)
        assertEquals(1, fp.manualResults.size)
        assertEquals("audio", fp.manualResults[0].categoryName)
        assertNotEquals("camera", fp.manualResults[0].categoryName)
    }

    // ─── Scenario 9: M-20/M-21 biometrics now SECURITY, not CAMERA ───

    @Test
    fun `M-20 fingerprint and M-21 face unlock now report security category`() {
        val report = fakeScanReport(
            listOf(
                manualRow("M-20", TestStatus.PASS, "security"),
                manualRow("M-21", TestStatus.PASS, "security")
            )
        )
        val fp = builder.build(report, timeSeries = null)
        assertEquals(2, fp.manualResults.size)
        assertTrue(fp.manualResults.all { it.categoryName == "security" })
    }
}
