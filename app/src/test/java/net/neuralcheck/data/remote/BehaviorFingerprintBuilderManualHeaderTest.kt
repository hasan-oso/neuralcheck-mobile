package net.neuralcheck.data.remote

import net.neuralcheck.data.remote.model.ManualResultsHeaderDto
import net.neuralcheck.domain.model.DeviceInfo
import net.neuralcheck.domain.model.ManualStatuses
import net.neuralcheck.domain.model.ScanMetadata
import net.neuralcheck.domain.model.ScanReport
import net.neuralcheck.domain.model.ScanType
import net.neuralcheck.domain.model.TestResult
import net.neuralcheck.domain.model.TestStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * fieldsVersion 69 (N-17, S-15; rule 41): what a reader of the request payload
 * sees for the manual-results header and the fourth answer, taken from the wire map
 * `AnalysisRequestSerializer.fingerprintToMap` builds — the same function
 * `FirebaseAnalysisService.analyzeDevice` calls — not from the DTO.
 */
class BehaviorFingerprintBuilderManualHeaderTest {

    private val builder = BehaviorFingerprintBuilder()

    private fun report(rows: List<TestResult>) = ScanReport(
        scanId = "scan", scanType = ScanType.GENERAL, linkedScanId = null,
        deviceInfo = DeviceInfo("fp", "Model", "Mfr", "14", 34, "1080x2400", 8),
        scanMetadata = ScanMetadata(0L, 0L, 100, 95, 27f, 0L, true, null, "1.0.0"),
        functionalResults = rows, timeSeriesSummary = null, antiTamperFlags = emptyList(),
        technicianInput = null, peerInput = null
    )

    private fun manual(id: String, status: TestStatus) = TestResult(
        testId = id, componentName = id, componentNameAr = id, categoryName = "audio",
        status = status, score = null, rawValue = null, rawValueUnit = null, notes = "manual"
    )

    private fun wire(rows: List<TestResult>, header: ManualResultsHeaderDto?): Map<String, Any> =
        AnalysisRequestSerializer.fingerprintToMap(
            builder.build(report(rows), timeSeries = null, manualResultsHeader = header)
        )

    @Test
    fun `the header goes on the wire next to manualResults, with the hidden tests named`() {
        val w = wire(listOf(manual("M-01", TestStatus.PASS)), ManualResultsHeaderDto(listOf("M-14", "M-15")))
        assertEquals(mapOf("hiddenByModel" to listOf("M-14", "M-15")), w["manualResultsHeader"])
    }

    @Test
    fun `nothing hidden is an EMPTY LIST on the wire - not an absent key (rule 22)`() {
        val w = wire(listOf(manual("M-01", TestStatus.PASS)), ManualResultsHeaderDto(emptyList()))
        assertTrue("the header is present", w.containsKey("manualResultsHeader"))
        assertEquals(mapOf("hiddenByModel" to emptyList<String>()), w["manualResultsHeader"])
    }

    @Test
    fun `a caller that supplies no header leaves the key ABSENT - "told us nothing" is not "nothing hidden"`() {
        val w = wire(listOf(manual("M-01", TestStatus.PASS)), null)
        assertFalse(w.containsKey("manualResultsHeader"))
        assertNull(w["manualResultsHeader"])
    }

    @Test
    fun `the fourth answer reaches the wire as unavailable and a hidden test has no row`() {
        val rows = listOf(
            manual("M-01", TestStatus.PASS), manual("M-08", TestStatus.UNAVAILABLE),
            manual("M-11", TestStatus.NOT_TESTED), manual("M-19", TestStatus.FAIL)
        )
        val w = wire(rows, ManualResultsHeaderDto(listOf("M-15")))
        @Suppress("UNCHECKED_CAST")
        val manualRows = w["manualResults"] as List<Map<String, Any>>
        assertEquals(ManualStatuses.ALL.toSet(), manualRows.map { it["status"] }.toSet())
        assertEquals("unavailable", manualRows.first { it["testId"] == "M-08" }["status"])
        assertFalse("M-15 is hidden: no row", manualRows.any { it["testId"] == "M-15" })
    }
}
