package net.neuralcheck.domain.usecase

import net.neuralcheck.data.local.db.entity.ScanEntity
import net.neuralcheck.data.local.files.TimeSeriesFooter
import net.neuralcheck.data.local.files.TimeSeriesHeader
import net.neuralcheck.data.local.files.TimeSeriesSample
import net.neuralcheck.data.repository.ScanRepository
import net.neuralcheck.data.repository.TestResultRepository
import net.neuralcheck.data.repository.TimeSeriesRepository
import net.neuralcheck.domain.model.ScanType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import java.util.UUID
import javax.inject.Inject

/**
 * Orchestrates the complete 5-phase stress test execution.
 * Manages scan lifecycle: creation → time-series recording → result storage.
 * The actual hardware stress is driven by StressTestService; this use case
 * handles the data orchestration layer.
 */
class ExecuteStressTestUseCase @Inject constructor(
    private val scanRepository: ScanRepository,
    private val testResultRepository: TestResultRepository,
    private val timeSeriesRepository: TimeSeriesRepository
) {
    private val _progress = MutableSharedFlow<ScanProgress>(replay = 1)
    val progress: Flow<ScanProgress> = _progress

    /**
     * Create a new scan record and prepare time-series recording.
     * @return scanId (UUID) for the new scan
     */
    suspend fun startScan(
        scanType: ScanType,
        linkedScanId: String?,
        deviceFingerprint: String,
        batteryLevelStart: Int,
        batteryTempStart: Float,
        deviceModel: String,
        androidVersion: String,
        androidApiLevel: Int,
        appVersion: String
    ): String {
        val scanId = UUID.randomUUID().toString()

        // Create scan record in database
        val scanEntity = ScanEntity(
            scanId = scanId,
            deviceFingerprint = deviceFingerprint,
            scanType = ScanType.toDbString(scanType),
            linkedScanId = linkedScanId,
            timestamp = System.currentTimeMillis(),
            batteryLevelStart = batteryLevelStart,
            batteryLevelEnd = null,
            ambientTempEstimate = batteryTempStart,
            appVersion = appVersion,
            androidApiLevel = androidApiLevel,
            deviceModel = deviceModel,
            androidVersion = androidVersion,
            isComplete = false,
            earlyStopReason = null,
            earlyStopAtMs = null,
            timeSeriesFilePath = null,
            isSynced = false
        )
        scanRepository.createScan(scanEntity)

        // Initialize time-series writer
        val header = TimeSeriesHeader(
            scanId = scanId,
            deviceFingerprint = deviceFingerprint,
            scanType = ScanType.toDbString(scanType),
            appVersion = appVersion,
            androidApiLevel = androidApiLevel,
            deviceModel = deviceModel,
            cpuCores = Runtime.getRuntime().availableProcessors(),
            totalRamMb = Runtime.getRuntime().maxMemory() / (1024 * 1024),
            startTimestamp = System.currentTimeMillis(),
            batteryLevelStart = batteryLevelStart,
            batteryTempStart = batteryTempStart
        )
        timeSeriesRepository.getWriter().startNewFile(header)

        _progress.emit(ScanProgress(
            scanId = scanId,
            phase = 0,
            phaseProgress = 0f,
            overallProgress = 0f,
            currentPhaseName = "initializing",
            timeRemainingMs = 690_000L // 11.5 minutes
        ))

        return scanId
    }

    /**
     * Record a single time-series sample during the scan.
     */
    fun recordSample(sample: TimeSeriesSample) {
        timeSeriesRepository.getWriter().addSample(sample)
    }

    /**
     * Update scan progress for UI.
     */
    suspend fun updateProgress(progress: ScanProgress) {
        _progress.emit(progress)
    }

    /**
     * Complete the scan: finalize time-series, update scan record.
     */
    /**
     * fieldsVersion 65: returns null when the writer's final GZIP write
     * failed — the scan record then carries timeSeriesFilePath = null
     * instead of a path to a corrupt/missing file.
     */
    suspend fun completeScan(
        scanId: String,
        batteryLevelEnd: Int,
        maxBatteryTemp: Float,
        maxCpuTemp: Float,
        isComplete: Boolean,
        earlyStopReason: String?
    ): String? {
        val footer = TimeSeriesFooter(
            endTimestamp = System.currentTimeMillis(),
            totalSamples = 0, // Will be counted from file
            isComplete = isComplete,
            earlyStopReason = earlyStopReason,
            batteryLevelEnd = batteryLevelEnd,
            maxBatteryTemp = maxBatteryTemp,
            maxCpuTemp = maxCpuTemp
        )

        val filePath = timeSeriesRepository.getWriter().finalize(footer)

        // Update scan record
        val scan = scanRepository.getScanById(scanId)
        scan?.let {
            scanRepository.updateScan(it.copy(
                batteryLevelEnd = batteryLevelEnd,
                isComplete = isComplete,
                earlyStopReason = earlyStopReason,
                earlyStopAtMs = if (!isComplete) System.currentTimeMillis() else null,
                timeSeriesFilePath = filePath
            ))
        }

        return filePath
    }
}

/**
 * Progress state emitted during scan execution.
 */
data class ScanProgress(
    val scanId: String,
    val phase: Int,              // 1-5 (0 = initializing)
    val phaseProgress: Float,    // 0.0-1.0 within current phase
    val overallProgress: Float,  // 0.0-1.0 overall
    val currentPhaseName: String,
    val timeRemainingMs: Long,
    val batteryTemp: Float = 0f,
    val cpuTemp: Float = 0f,
    val cpuUsage: Float = 0f,
    val gpuFps: Float = 0f,
    val isSafetyStop: Boolean = false,
    val safetyStopReason: String? = null
)
