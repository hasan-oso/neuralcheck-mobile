package net.neuralcheck.domain.usecase

import net.neuralcheck.data.repository.ScanRepository
import net.neuralcheck.data.repository.TestResultRepository
import net.neuralcheck.data.repository.TimeSeriesRepository
import net.neuralcheck.data.repository.PeerRepository
import net.neuralcheck.data.repository.TechnicianRepository
import net.neuralcheck.data.repository.ScoreRepository
import net.neuralcheck.data.local.db.entity.ScanEntity
import net.neuralcheck.data.local.db.entity.ScanScoreEntity
import net.neuralcheck.domain.model.*
import net.neuralcheck.domain.scoring.ScoringEngine
import net.neuralcheck.domain.scoring.ScoringResult
import com.google.gson.Gson
import javax.inject.Inject

/**
 * Aggregates all scan data, detects anti-tamper flags, runs scoring engine,
 * and produces the complete ScanReport with ScoringResult.
 */
class GenerateReportUseCase @Inject constructor(
    private val scanRepository: ScanRepository,
    private val testResultRepository: TestResultRepository,
    private val timeSeriesRepository: TimeSeriesRepository,
    private val technicianRepository: TechnicianRepository,
    private val peerRepository: PeerRepository,
    private val scoreRepository: ScoreRepository,
    private val scoringEngine: ScoringEngine
) {
    private val gson = Gson()

    /**
     * Generate a complete report for a scan.
     * @return Pair of ScanReport and ScoringResult
     */
    suspend fun execute(scanId: String): Pair<ScanReport, ScoringResult>? {
        val scanEntity = scanRepository.getScanById(scanId) ?: return null

        // Gather all test results
        val resultEntities = testResultRepository.getResults(scanId)
        val testResults = resultEntities.map { entity ->
            TestResult(
                testId = entity.testId,
                componentName = entity.componentName,
                componentNameAr = entity.componentName,
                categoryName = entity.categoryName,
                status = TestStatus.fromString(entity.status),
                score = entity.score,
                rawValue = entity.rawValue,
                rawValueUnit = entity.rawValueUnit,
                notes = entity.notes,
                measurements = net.neuralcheck.data.local.db.converter.MeasurementsJson
                    .decode(entity.measurements)
            )
        }

        // Read time-series summary
        val timeSeriesSummary = scanEntity.timeSeriesFilePath?.let { path ->
            val reader = timeSeriesRepository.getReader()
            val data = reader.readFromPath(path)
            data?.let {
                val summary = reader.calculateSummary(it)
                TimeSeriesSummaryDomain(
                    maxBatteryTemp = summary.maxBatteryTemp,
                    maxCpuTemp = if (summary.maxCpuTemp > -1f) summary.maxCpuTemp else null,
                    maxGpuTemp = null, // Calculated separately from gpu_temp
                    drainRatePerMinute = if (summary.durationMs > 0)
                        summary.batteryDrainPercent / (summary.durationMs / 60000f) else 0f,
                    cpuThrottlePercent = null, // Computed in Phase 08 scoring
                    cpuStabilityStdDev = null,
                    gpuFpsDropPercent = null,
                    gpuThrottlePercent = null,
                    maxTempReached = maxOf(summary.maxBatteryTemp, summary.maxCpuTemp),
                    heatRiseRatePerMinute = summary.heatRiseRatePerMin,
                    // NaN means TimeSeriesFileReader couldn't compute the rate
                    // (no phase-7 samples). A finite value — including 0 and
                    // negative — is a real measurement that downstream
                    // (ThermalTests) translates to PASS / WARNING / FAIL.
                    cooldownRatePerMinute = if (!summary.cooldownRatePerMin.isNaN())
                        summary.cooldownRatePerMin else null,
                    peakTempTimestampMs = 0L,
                    throttleStartTimestampMs = null,
                    totalSamples = summary.totalSamples,
                    durationMs = summary.durationMs.toLong()
                )
            }
        }

        // Detect anti-tamper flags
        val antiTamperFlags = detectAntiTamperFlags(scanEntity, timeSeriesSummary)

        // Get user inputs
        val techInput = technicianRepository.getByScanId(scanId)?.let { entity ->
            TechnicianInput(
                scanId = entity.scanId,
                isPreRepair = entity.isPreRepair,
                customerComplaint = entity.customerComplaint,
                faultCategories = try {
                    gson.fromJson(entity.faultCategories, Array<String>::class.java).toList()
                } catch (e: Exception) { emptyList() },
                confidence = entity.technicianConfidence,
                repairActions = entity.repairActions?.let {
                    try { gson.fromJson(it, Array<String>::class.java).toList() }
                    catch (e: Exception) { null }
                },
                repairResult = entity.repairResult,
                notes = entity.additionalNotes
            )
        }

        val peerInput = peerRepository.getByScanId(scanId)?.let { entity ->
            PeerInput(
                scanId = entity.scanId,
                batteryDrainsFast = entity.batteryDrainsFast,
                overheats = entity.overheats,
                sluggish = entity.sluggish
            )
        }

        // Build ScanReport
        val scanReport = ScanReport(
            scanId = scanId,
            scanType = ScanType.fromString(scanEntity.scanType),
            linkedScanId = scanEntity.linkedScanId,
            deviceInfo = DeviceInfo(
                fingerprint = scanEntity.deviceFingerprint,
                model = scanEntity.deviceModel,
                manufacturer = android.os.Build.MANUFACTURER,
                androidVersion = scanEntity.androidVersion,
                apiLevel = scanEntity.androidApiLevel,
                screenResolution = "",
                cpuCores = Runtime.getRuntime().availableProcessors()
            ),
            scanMetadata = ScanMetadata(
                startTimestamp = scanEntity.timestamp,
                endTimestamp = null,
                startBatteryLevel = scanEntity.batteryLevelStart,
                endBatteryLevel = scanEntity.batteryLevelEnd,
                startBatteryTemp = scanEntity.ambientTempEstimate ?: 0f,
                uptimeMs = android.os.SystemClock.elapsedRealtime(),
                isComplete = scanEntity.isComplete,
                earlyStopReason = scanEntity.earlyStopReason,
                appVersion = scanEntity.appVersion
            ),
            functionalResults = testResults,
            timeSeriesSummary = timeSeriesSummary,
            antiTamperFlags = antiTamperFlags,
            technicianInput = techInput,
            peerInput = peerInput
        )

        // Calculate score
        val scoringResult = scoringEngine.calculateScore(scanReport)

        // Save score to database
        scoreRepository.save(ScanScoreEntity(
            scanId = scanId,
            overallScore = scoringResult.overallScore,
            overallGrade = scoringResult.overallGrade,
            axisScoresJson = gson.toJson(scoringResult.axisScores),
            categoryScoresJson = gson.toJson(scoringResult.categoryScores),
            criticalFailuresJson = gson.toJson(scoringResult.criticalFailures),
            antiTamperPenalty = scoringResult.antiTamperPenalty,
            isIncomplete = scoringResult.isIncomplete,
            scoringMethod = scoringResult.scoringMethod,
            calculatedAt = System.currentTimeMillis()
        ))

        return Pair(scanReport, scoringResult)
    }

    private fun detectAntiTamperFlags(
        scan: ScanEntity,
        @Suppress("UNUSED_PARAMETER") summary: TimeSeriesSummaryDomain?
    ): List<AntiTamperFlag> {
        val flags = mutableListOf<AntiTamperFlag>()

        // COLD_START: Battery temp < 15°C at start
        val startTemp = scan.ambientTempEstimate ?: 0f
        if (startTemp in 0.1f..14.9f) {
            flags.add(AntiTamperFlag(
                type = AntiTamperType.COLD_START,
                value = "${startTemp}°C",
                description = "بدء بارد — درجة حرارة أولية منخفضة"
            ))
        }

        // RECENT_REBOOT: Uptime < 1 hour
        val uptimeMs = android.os.SystemClock.elapsedRealtime()
        if (uptimeMs < 3_600_000L) {
            flags.add(AntiTamperFlag(
                type = AntiTamperType.RECENT_REBOOT,
                value = "${uptimeMs / 60000}min",
                description = "إعادة تشغيل حديثة"
            ))
        }

        return flags
    }
}
