package net.neuralcheck.data.remote

import net.neuralcheck.domain.model.DeviceInfo
import net.neuralcheck.domain.model.ScanMetadata
import net.neuralcheck.domain.model.ScanReport
import net.neuralcheck.domain.model.ScanType
import net.neuralcheck.domain.model.TestResult
import net.neuralcheck.domain.model.TestStatus
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * fieldsVersion 67 — item 2: DeviceInfo storage fields were sourced from
 * the wrong results.
 *
 * Field evidence (2026-09-14):
 *   Honor MTN-NX1   storagePercent="635"  availableStorageGb="67.5"
 *                   (real free space 149.2 GB; 635 is S-03 write MB/s)
 *   Samsung S10 5G  storagePercent="166"  (166 is S-03 write MB/s)
 *
 * Root cause: `storagePercent` read S-03 (sequential WRITE SPEED, MB/s) and
 * `availableStorageGb` read S-02's rawValue, which StorageTests documents as
 * the percent-free — the GB figure lives in that row's rawValueUnit
 * ("% (149.2 GB)"). Both now read S-02, each from the field that holds it.
 */
class DeviceInfoStorageFieldsTest {

    private val builder = BehaviorFingerprintBuilder()

    private fun row(
        testId: String,
        rawValue: String,
        unit: String,
        category: String = "storage"
    ) = TestResult(
        testId = testId,
        componentName = testId,
        componentNameAr = testId,
        categoryName = category,
        status = TestStatus.PASS,
        score = null,
        rawValue = rawValue,
        rawValueUnit = unit,
        notes = null
    )

    /** Exactly the rows StorageTests emits on a 223 GB Honor MTN-NX1. */
    private fun honorRows() = listOf(
        row("S-01", "223.6", "GB"),
        row("S-02", "67.5", "% (149.2 GB)"),
        row("S-03", "635", "MB/s"),
        row("S-04", "1100", "MB/s")
    )

    private fun reportWith(rows: List<TestResult>) = ScanReport(
        scanId = "s",
        scanType = ScanType.GENERAL,
        linkedScanId = null,
        deviceInfo = DeviceInfo(
            fingerprint = "fp", model = "Honor MTN-NX1", manufacturer = "Honor",
            androidVersion = "15", apiLevel = 35,
            screenResolution = "1080x2400", cpuCores = 8
        ),
        scanMetadata = ScanMetadata(
            startTimestamp = 0L, endTimestamp = 0L, startBatteryLevel = 100,
            endBatteryLevel = 95, startBatteryTemp = 27f, uptimeMs = 0L,
            isComplete = true, earlyStopReason = null, appVersion = "1.0.0"
        ),
        functionalResults = rows,
        timeSeriesSummary = null,
        antiTamperFlags = emptyList(),
        technicianInput = null,
        peerInput = null
    )

    private fun device(rows: List<TestResult>) =
        builder.build(reportWith(rows), timeSeries = null).device

    @Test
    fun `availableStorageGb carries gigabytes not the percentage`() {
        val d = device(honorRows())
        assertEquals("149.2", d.availableStorageGb)
    }

    @Test
    fun `storagePercent carries the percent free not the write speed`() {
        val d = device(honorRows())
        assertEquals("67.5", d.storagePercent)
    }

    @Test
    fun `the field-observed wrong values can no longer be produced`() {
        val d = device(honorRows())
        // "635" was S-03's MB/s landing in a percentage field.
        assert(d.storagePercent != "635")
        // "67.5" was the percent landing in a gigabytes field.
        assert(d.availableStorageGb != "67.5")
    }

    @Test
    fun `totalStorageGb still reads S-01 unchanged`() {
        assertEquals("223.6", device(honorRows()).totalStorageGb)
    }

    @Test
    fun `samsung field row set maps correctly too`() {
        val rows = listOf(
            row("S-01", "119.2", "GB"),
            row("S-02", "18.3", "% (21.8 GB)"),
            row("S-03", "166", "MB/s")
        )
        val d = device(rows)
        assertEquals("21.8", d.availableStorageGb)
        assertEquals("18.3", d.storagePercent)
    }

    @Test
    fun `missing S-02 degrades to the zero sentinel rather than a wrong number`() {
        val rows = listOf(row("S-01", "223.6", "GB"), row("S-03", "635", "MB/s"))
        val d = device(rows)
        assertEquals("0", d.availableStorageGb)
        assertEquals("0", d.storagePercent)
    }

    @Test
    fun `malformed S-02 unit yields the sentinel not a parse crash`() {
        val rows = listOf(
            row("S-01", "223.6", "GB"),
            row("S-02", "67.5", "%")          // unit without the (NNN GB) part
        )
        assertEquals("0", device(rows).availableStorageGb)
        // The percent is still trustworthy — it comes from rawValue.
        assertEquals("67.5", device(rows).storagePercent)
    }

    @Test
    fun `integer gigabyte values parse`() {
        val rows = listOf(row("S-02", "50.0", "% (64 GB)"))
        assertEquals("64", device(rows).availableStorageGb)
    }

    @Test
    fun `memory fields remain correct - the shift was storage only`() {
        val rows = honorRows() + listOf(
            row("R-01", "8.0", "GB", category = "memory"),
            row("R-02", "3.4", "GB", category = "memory")
        )
        val d = device(rows)
        assertEquals("8.0", d.totalRamGb)
        assertEquals("3.4", d.availableRamGb)
    }
}
