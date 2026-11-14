package net.neuralcheck.domain.model

/**
 * Aggregated scan report containing all test results, time-series summary,
 * anti-tamper flags, and user inputs for scoring.
 */
data class ScanReport(
    val scanId: String,
    val scanType: ScanType,
    val linkedScanId: String?,
    val deviceInfo: DeviceInfo,
    val scanMetadata: ScanMetadata,
    val functionalResults: List<TestResult>,
    val timeSeriesSummary: TimeSeriesSummaryDomain?,
    val antiTamperFlags: List<AntiTamperFlag>,
    val technicianInput: TechnicianInput?,
    val peerInput: PeerInput?
)

enum class ScanType {
    GENERAL,
    PRE_REPAIR,
    POST_REPAIR;

    companion object {
        fun fromString(value: String): ScanType = when (value) {
            "general" -> GENERAL
            "pre_repair" -> PRE_REPAIR
            "post_repair" -> POST_REPAIR
            else -> GENERAL
        }

        fun toDbString(type: ScanType): String = when (type) {
            GENERAL -> "general"
            PRE_REPAIR -> "pre_repair"
            POST_REPAIR -> "post_repair"
        }
    }
}

enum class TestStatus {
    PASS,
    FAIL,
    WARNING,
    UNAVAILABLE,
    NOT_TESTED,
    ERROR;

    companion object {
        fun fromString(value: String): TestStatus = when (value.lowercase()) {
            "pass" -> PASS
            "fail" -> FAIL
            "warning" -> WARNING
            "unavailable" -> UNAVAILABLE
            "not_tested" -> NOT_TESTED
            "error" -> ERROR
            else -> ERROR
        }

        fun toDbString(status: TestStatus): String = status.name.lowercase()
    }
}

data class DeviceInfo(
    val fingerprint: String,
    val model: String,
    val manufacturer: String,
    val androidVersion: String,
    val apiLevel: Int,
    val screenResolution: String,
    val cpuCores: Int
)

data class ScanMetadata(
    val startTimestamp: Long,
    val endTimestamp: Long?,
    val startBatteryLevel: Int,
    val endBatteryLevel: Int?,
    val startBatteryTemp: Float,
    val uptimeMs: Long,
    val isComplete: Boolean,
    val earlyStopReason: String?,
    val appVersion: String
)

data class TestResult(
    val testId: String,
    val componentName: String,
    val componentNameAr: String,
    val categoryName: String,
    val status: TestStatus,
    val score: Float?,
    val rawValue: String?,
    val rawValueUnit: String?,
    val notes: String?,
    /**
     * fieldsVersion 68 (S-29 (a), N-16): compound raw measurements of the
     * test, decoded from `FunctionalResultEntity.measurements`. null = the
     * producer supplied none (NOT a measured zero). Forwarded verbatim to
     * `FunctionalResultDto` / `ManualResultDto`.
     */
    val measurements: Map<String, Any?>? = null
)

/**
 * Domain-level time-series summary with computed diagnostic values.
 */
data class TimeSeriesSummaryDomain(
    val maxBatteryTemp: Float,
    val maxCpuTemp: Float?,
    val maxGpuTemp: Float?,
    val drainRatePerMinute: Float,          // B-06
    val cpuThrottlePercent: Float?,         // C-06
    val cpuStabilityStdDev: Float?,         // C-07
    val gpuFpsDropPercent: Float?,          // G-03
    val gpuThrottlePercent: Float?,         // G-04
    val maxTempReached: Float,              // T-01
    val heatRiseRatePerMinute: Float,       // T-02
    val cooldownRatePerMinute: Float?,      // T-03
    val peakTempTimestampMs: Long,
    val throttleStartTimestampMs: Long?,
    val totalSamples: Int,
    val durationMs: Long
)

data class AntiTamperFlag(
    val type: AntiTamperType,
    val value: String,
    val description: String
)

enum class AntiTamperType {
    COLD_START,              // Battery temp < 15°C at start
    RECENT_REBOOT,           // Uptime < 1 hour
    CHARGER_JUST_REMOVED,    // Hot + high voltage + near 100%
    HEAVY_BACKGROUND_USAGE   // CPU > 25% at baseline
}

data class TechnicianInput(
    val scanId: String,
    val isPreRepair: Boolean,
    val customerComplaint: String?,
    val faultCategories: List<String>,
    val confidence: Int?,
    val repairActions: List<String>?,
    val repairResult: String?,
    val notes: String?
)

data class PeerInput(
    val scanId: String,
    val batteryDrainsFast: Boolean?,
    val overheats: Boolean?,
    val sluggish: Boolean?
)
