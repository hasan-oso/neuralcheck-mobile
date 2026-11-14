package net.neuralcheck.data.remote

import com.google.gson.Gson
import com.google.gson.JsonObject
import net.neuralcheck.data.local.db.converter.MeasurementsJson
import net.neuralcheck.domain.model.DeviceInfo
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
 * fieldsVersion 68 (N-16, S-29 (a)): `measurements` — the compound raw of a
 * test — travels from TestResult through [BehaviorFingerprintBuilder] to the
 * functional and manual result DTOs, and from there into the wire JSON
 * exactly as FirebaseAnalysisService serialises it
 * (`AnalysisRequestSerializer.gson`, no serializeNulls).
 *
 * This commit adds the CARRIER only: nothing in the app writes a measurement
 * yet, so the contract for the wire is "absent key = the test supplied none".
 * The tests lock that absence rule and the verbatim forwarding when a value
 * does exist (including through the Room column codec, so the integers that
 * were written are the integers that are sent).
 */
class BehaviorFingerprintBuilderMeasurementsTest {

    private val builder = BehaviorFingerprintBuilder()

    /** The production configuration: AnalysisRequestSerializer.kt. */
    private val wireGson: Gson = AnalysisRequestSerializer.gson

    private fun report(rows: List<TestResult>) = ScanReport(
        scanId = "scan-measurements",
        scanType = ScanType.GENERAL,
        linkedScanId = null,
        deviceInfo = DeviceInfo("fp", "Model", "Mfr", "14", 34, "1080x2400", 8),
        scanMetadata = ScanMetadata(0L, 0L, 100, 95, 27f, 0L, true, null, "1.0.0"),
        functionalResults = rows,
        timeSeriesSummary = null,
        antiTamperFlags = emptyList(),
        technicianInput = null,
        peerInput = null
    )

    private fun row(
        testId: String,
        category: String,
        measurements: Map<String, Any?>? = null
    ) = TestResult(
        testId = testId,
        componentName = testId,
        componentNameAr = testId,
        categoryName = category,
        status = TestStatus.PASS,
        score = null,
        rawValue = "raw",
        rawValueUnit = "u",
        notes = null,
        measurements = measurements
    )

    @Test
    fun `a functional result without measurements carries null and the wire key is absent`() {
        val fp = builder.build(report(listOf(row("L-02", "gps"))), timeSeries = null)

        assertNull(fp.functionalResults.single().measurements)
        val json = wireGson.toJsonTree(fp.functionalResults.single()).asJsonObject
        assertFalse("absent means none supplied — never a zero", json.has("measurements"))
    }

    @Test
    fun `a manual result without measurements carries null and the wire key is absent`() {
        val fp = builder.build(report(listOf(row("M-02", "display"))), timeSeries = null)

        assertNull(fp.manualResults.single().measurements)
        val json = wireGson.toJsonTree(fp.manualResults.single()).asJsonObject
        assertFalse(json.has("measurements"))
    }

    @Test
    fun `functional measurements are forwarded verbatim and reach the wire with integers intact`() {
        // The path a real value will take: written as a Map, stored as the
        // Room column text, decoded on read, then sent.
        val written = mapOf<String, Any?>(
            "visibleSatellites" to 9L,
            "usedSatellites" to 4L,
            "satelliteStatusCallbacks" to 31L,
            "timeToFirstFixMs" to 8123L
        )
        val stored = MeasurementsJson.encode(written)
        val fp = builder.build(
            report(listOf(row("L-02", "gps", MeasurementsJson.decode(stored)))),
            timeSeries = null
        )

        val dto = fp.functionalResults.single()
        assertEquals(written, dto.measurements)

        val wire = wireGson.toJsonTree(dto).asJsonObject.getAsJsonObject("measurements")
        assertEquals("9", wire.get("visibleSatellites").toString())
        assertEquals("31", wire.get("satelliteStatusCallbacks").toString())
        assertEquals("8123", wire.get("timeToFirstFixMs").toString())
    }

    @Test
    fun `manual measurements are forwarded verbatim next to status and category`() {
        val grid = mapOf<String, Any?>(
            "gridCols" to 8L,
            "gridRows" to 15L,
            "touchedCells" to 87L,
            "totalCells" to 120L,
            "elapsedMs" to 4250L,
            "endReason" to "auto_complete",
            "maxSimultaneousPointers" to 3L
        )
        val fp = builder.build(
            report(listOf(row("M-02", "display", MeasurementsJson.decode(MeasurementsJson.encode(grid))))),
            timeSeries = null
        )

        val dto = fp.manualResults.single()
        assertEquals("M-02", dto.testId)
        assertEquals("display", dto.categoryName)
        assertEquals(grid, dto.measurements)
        val wire: JsonObject = wireGson.toJsonTree(dto).asJsonObject.getAsJsonObject("measurements")
        assertEquals("87", wire.get("touchedCells").toString())
        assertEquals("\"auto_complete\"", wire.get("endReason").toString())
    }

    @Test
    fun `measurements of one test never leak onto another`() {
        val fp = builder.build(
            report(
                listOf(
                    row("L-02", "gps", mapOf("usedSatellites" to 4L)),
                    row("L-03", "gps"),
                    row("M-02", "display", mapOf("touchedCells" to 1L)),
                    row("M-03", "display")
                )
            ),
            timeSeries = null
        )

        assertEquals(mapOf<String, Any?>("usedSatellites" to 4L), fp.functionalResults.first { it.testId == "L-02" }.measurements)
        assertNull(fp.functionalResults.first { it.testId == "L-03" }.measurements)
        assertEquals(mapOf<String, Any?>("touchedCells" to 1L), fp.manualResults.first { it.testId == "M-02" }.measurements)
        assertNull(fp.manualResults.first { it.testId == "M-03" }.measurements)
        assertTrue(fp.functionalResults.size == 2 && fp.manualResults.size == 2)
    }
}
