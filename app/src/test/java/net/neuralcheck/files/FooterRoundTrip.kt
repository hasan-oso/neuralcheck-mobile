package net.neuralcheck.files

import android.content.Context
import com.google.gson.JsonObject
import kotlinx.coroutines.runBlocking
import net.neuralcheck.data.local.files.TimeSeriesData
import net.neuralcheck.data.local.files.TimeSeriesFileReader
import net.neuralcheck.data.local.files.TimeSeriesFileWriter
import net.neuralcheck.data.local.files.TimeSeriesFooter
import net.neuralcheck.data.local.files.TimeSeriesHeader
import net.neuralcheck.data.local.files.TimeSeriesSample
import net.neuralcheck.data.local.files.V2_SAMPLE_VAR_COUNT
import net.neuralcheck.data.remote.AnalysisRequestSerializer
import net.neuralcheck.data.remote.BehaviorFingerprintBuilder
import net.neuralcheck.data.remote.model.BehaviorFingerprint
import net.neuralcheck.domain.model.DeviceInfo
import net.neuralcheck.domain.model.ScanMetadata
import net.neuralcheck.domain.model.ScanReport
import net.neuralcheck.domain.model.ScanType
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.io.File

/**
 * Test support (fieldsVersion 68, rule 37 / rule 41): pushes ONE footer through
 * the real chain with nothing substituted — the real [TimeSeriesFileWriter]
 * writes a `.ts.json.gz`, the real [TimeSeriesFileReader] reads it back, the
 * real [BehaviorFingerprintBuilder] builds the request payload, and the payload
 * is serialised with the Gson configuration `FirebaseAnalysisService` uses
 * (`AnalysisRequestSerializer.gson`: no serializeNulls).
 *
 * What it cannot reach — the real sampler and `StressTestService.completeScan`
 * (they need a device) — is the device round's job (contract §٣ بند 7 مرحلة ٢).
 */
class FooterRoundTrip(private val filesDir: File) {

    /** The production configuration: AnalysisRequestSerializer.kt (used by FirebaseAnalysisService.analyzeDevice). */
    val wireGson = AnalysisRequestSerializer.gson

    private val context: Context = mock { on { filesDir } doReturn this@FooterRoundTrip.filesDir }

    /** The footer as a base footer with the given overrides applied by [build]. */
    fun baseFooter(): TimeSeriesFooter = TimeSeriesFooter(
        endTimestamp = 20_000L, totalSamples = 2, isComplete = true, earlyStopReason = null,
        batteryLevelEnd = 75, maxBatteryTemp = 34f,
        cpuTempFinalC = null, cpuTempFinalSource = "absent:test fixture", fieldsVersion = 68
    )

    class Result(
        val data: TimeSeriesData,
        val fingerprint: BehaviorFingerprint,
        /** `timeSeries.footer` of the payload as the server receives it. */
        val wireFooter: JsonObject,
        /** the whole payload as the server receives it. */
        val wire: JsonObject
    )

    fun run(
        footer: TimeSeriesFooter,
        scanId: String = "scan-roundtrip",
        report: ScanReport = report(scanId),
        /** The header's power-save answer (fieldsVersion 71): by default what a scan that read "off" writes. */
        isPowerSaveMode: Boolean? = false,
        powerSaveState: String? = "off"
    ): Result {
        val path = runBlocking {
            val writer = TimeSeriesFileWriter(context)
            writer.enableV2Mode()
            writer.startNewFile(
                TimeSeriesHeader(
                    scanId = scanId, deviceFingerprint = "fp", scanType = "general",
                    appVersion = "test", androidApiLevel = 35, deviceModel = "UnitTest", cpuCores = 8,
                    totalRamMb = 8192, startTimestamp = 1_000L, batteryLevelStart = 80, batteryTempStart = 25f,
                    isPowerSaveMode = isPowerSaveMode, powerSaveState = powerSaveState
                )
            )
            for (i in 0 until 2) {
                val values = FloatArray(V2_SAMPLE_VAR_COUNT)
                values[0] = (i * 250).toFloat()
                values[1] = 30f + i
                values[4] = 80f - i
                writer.addSample(TimeSeriesSample(ts = 10_000L + i, phase = 3, v = values))
            }
            writer.finalize(footer)
        } ?: error("finalize must write the file")
        val data = runBlocking { TimeSeriesFileReader(context).readFromPath(path) }
            ?: error("the file just written must read back")
        val fingerprint = BehaviorFingerprintBuilder().build(report, data)
        val wire = wireGson.toJsonTree(fingerprint).asJsonObject
        return Result(data, fingerprint, wire.getAsJsonObject("timeSeries").getAsJsonObject("footer"), wire)
    }

    fun report(scanId: String = "scan-roundtrip") = ScanReport(
        scanId = scanId, scanType = ScanType.GENERAL, linkedScanId = null,
        deviceInfo = DeviceInfo("fp", "Model", "Mfr", "14", 34, "1080x2400", 8),
        scanMetadata = ScanMetadata(0L, 0L, 100, 95, 27f, 0L, true, null, "1.0.0"),
        functionalResults = emptyList(), timeSeriesSummary = null, antiTamperFlags = emptyList(),
        technicianInput = null, peerInput = null
    )
}
