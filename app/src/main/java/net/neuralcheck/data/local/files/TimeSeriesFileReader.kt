package net.neuralcheck.data.local.files

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.util.zip.GZIPInputStream

/**
 * Reads and parses GZIP-compressed time-series JSON files.
 * Provides access to header, samples, footer and computed summary statistics.
 */
class TimeSeriesFileReader(private val context: Context) {

    companion object {
        private const val TAG = "NeuralCheck"
        private const val FINAL_DIR = "timeseries"
        // Mirrors TimeSeriesFileWriter.FOOTER_BACKUP_DIR — the footer-only
        // safety net written by completeScan BEFORE the main GZIP finalize.
        private const val FOOTER_BACKUP_DIR = "timeseries_footer"
    }

    private val gson: Gson = GsonBuilder().create()

    /**
     * Read and decompress a time-series file.
     *
     * fieldsVersion 65: when the main .ts.json.gz is missing or corrupt,
     * falls back to the `.footer.json` backup (written by completeScan
     * before finalize — until now it was written but never read). The
     * fallback returns a TimeSeriesData with EMPTY samples/variables and
     * empty-string header fields (the established "structurally empty"
     * convention from BehaviorFingerprintBuilder's null branch, with
     * scanId preserved) — so the AI still receives the footer's
     * verification/quality/battery fields instead of nothing, and can
     * tell it is a recovery artifact from samples.isEmpty() +
     * footer.totalSamples > 0.
     */
    suspend fun readFile(scanId: String): TimeSeriesData? = withContext(Dispatchers.IO) {
        val file = File(context.filesDir, "$FINAL_DIR/$scanId.ts.json.gz")
        readFile(file) ?: readFooterBackup(scanId)
    }

    /**
     * fieldsVersion 65: parse the footer-only backup for [scanId], or null
     * when it doesn't exist / doesn't parse. See [readFile] for semantics.
     */
    private fun readFooterBackup(scanId: String): TimeSeriesData? {
        val backup = File(context.filesDir, "$FOOTER_BACKUP_DIR/$scanId.footer.json")
        if (!backup.exists()) return null
        return try {
            val footer = backup.bufferedReader(Charsets.UTF_8).use { reader ->
                gson.fromJson(reader, TimeSeriesFooter::class.java)
            } ?: return null
            Log.w(TAG, "readFile: main .ts.json.gz unavailable for $scanId — " +
                "recovered footer from .footer.json backup (samples lost)")
            TimeSeriesData(
                header = TimeSeriesHeader(
                    scanId = scanId,
                    deviceFingerprint = "",
                    scanType = "",
                    appVersion = "",
                    androidApiLevel = 0,
                    deviceModel = "",
                    cpuCores = 0,
                    totalRamMb = 0,
                    startTimestamp = 0L,
                    batteryLevelStart = 0,
                    batteryTempStart = 0f
                ),
                variables = emptyList(),
                samples = emptyList(),
                footer = footer,
                recoveredFromFooterBackup = true
            )
        } catch (e: Exception) {
            Log.w(TAG, "readFooterBackup failed for $scanId: ${e.message}")
            null
        }
    }

    /**
     * Read and decompress a time-series file from a specific path.
     * @param filePath absolute path to the .ts.json.gz file
     * @return parsed TimeSeriesData or null if read fails
     */
    suspend fun readFromPath(filePath: String): TimeSeriesData? = withContext(Dispatchers.IO) {
        // fieldsVersion 65: same footer-backup fallback as readFile(scanId).
        // The scanId is the file's basename by construction
        // ("<scanId>.ts.json.gz" — see TimeSeriesFileWriter.finalize).
        readFile(File(filePath))
            ?: readFooterBackup(File(filePath).name.removeSuffix(".ts.json.gz"))
    }

    private fun readFile(file: File): TimeSeriesData? {
        if (!file.exists()) {
            Log.w(TAG, "Time-series file not found: ${file.absolutePath}")
            return null
        }

        val parsed: TimeSeriesData? = try {
            FileInputStream(file).use { fis ->
                GZIPInputStream(fis).use { gzis ->
                    InputStreamReader(gzis, Charsets.UTF_8).use { reader ->
                        gson.fromJson(reader, TimeSeriesData::class.java)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read time-series file: ${e.message}")
            return null
        }
        // fieldsVersion 68 (batch 3 part 2-b, rule 25): Gson builds this class without
        // running constructors, so a file that lacks one of its four parts yields an
        // object whose "non-null" part is null — and every reader that trusted the type
        // then threw. The reader says what it asked for and did not find; callers get
        // `null` (the established "no usable file" answer: the footer backup, then the
        // payload's `no_file`) instead of an object that cannot be used.
        val missing = missingParts(parsed)
        if (missing.isNotEmpty()) {
            Log.w(TAG, "Time-series file ${file.name} has no ${missing.joinToString(", ")} — unusable")
            return null
        }
        return parsed
    }

    /** The parts of a parsed file that are absent; empty when the file is usable. */
    private fun missingParts(data: TimeSeriesData?): List<String> {
        if (data == null) return listOf("content")
        val missing = mutableListOf<String>()
        if (nullable(data.header) == null) missing += "header"
        if (nullable(data.variables) == null) missing += "variables"
        if (nullable(data.samples) == null) missing += "samples"
        if (nullable(data.footer) == null) missing += "footer"
        return missing
    }

    /** [value] typed nullable: a parsed file can hold null where the Kotlin type says it cannot. */
    @Suppress("NOTHING_TO_INLINE")
    private inline fun <T> nullable(value: T?): T? = value

    /**
     * Check if a time-series file exists for a given scan.
     */
    fun fileExists(scanId: String): Boolean {
        val file = File(context.filesDir, "$FINAL_DIR/$scanId.ts.json.gz")
        return file.exists()
    }

    /**
     * Get the file path for a scan's time-series data.
     */
    fun getFilePath(scanId: String): String? {
        val file = File(context.filesDir, "$FINAL_DIR/$scanId.ts.json.gz")
        return if (file.exists()) file.absolutePath else null
    }

    /**
     * Delete a time-series file.
     */
    suspend fun deleteFile(scanId: String): Boolean = withContext(Dispatchers.IO) {
        val file = File(context.filesDir, "$FINAL_DIR/$scanId.ts.json.gz")
        if (file.exists()) file.delete() else false
    }

    /**
     * Calculate summary statistics from the time-series data.
     *
     * fieldsVersion 68: columns are resolved BY NAME against
     * `data.variables`, never by position or by the length of the list. The
     * list is no longer one of two fixed sizes — a V2 file is the 11-column
     * base plus the per-device channels that passed discovery (charge
     * counter, one frequency per cpufreq policy), so its length differs
     * between devices, and a length test (the former `variables.size == 11`)
     * would have read such a file as the legacy 18-column layout and taken
     * `cpu_freq_max_khz` for a temperature and `memory_bandwidth_mbps` for
     * CPU usage. A file of the legacy V1/V4 layout resolves its own names the
     * same way.
     *
     * A name this summary NEEDS and the file does not declare is listed in
     * [TimeSeriesSummary.unresolvedColumns] (rule 22: the reader declares
     * what it asked for and did not find) and the metrics that depend on it
     * keep their documented "absent" sentinels; it is also logged.
     * `cpu_temp_c` is the one name that is absent BY DESIGN in the V2 layout
     * (it moved to the footer, V5 schema), so it is optional here and is not
     * reported as unresolved — heat-rise / cooldown then fall back to
     * `battery_temp_c` exactly as before.
     */
    fun calculateSummary(data: TimeSeriesData): TimeSeriesSummary {
        val samples = data.samples
        if (samples.isEmpty()) {
            return TimeSeriesSummary()
        }

        val columns = data.variables.associate { it.name to it.index }
        val unresolved = mutableListOf<String>()
        fun required(name: String): Int? =
            columns[name].also { if (it == null) unresolved += name }

        val timestampIdx = required(ChannelNames.TIMESTAMP_MS)
        val batteryTempIdx = required(ChannelNames.BATTERY_TEMP_C)
        val batteryLevelIdx = required(ChannelNames.BATTERY_LEVEL_PCT)
        val cpuUsageIdx = required(ChannelNames.CPU_USAGE_PCT)
        val gpuFpsIdx = required(ChannelNames.GPU_FPS)
        // Optional by design: absent in the V2 layout (moved to the footer).
        val cpuTempIdx = columns[ChannelNames.CPU_TEMP_C]
        if (unresolved.isNotEmpty()) {
            Log.w(
                TAG,
                "calculateSummary: columns not declared by this file's variables[]: " +
                    "$unresolved (declared=${data.variables.map { it.name }})"
            )
        }

        fun column(idx: Int?): List<Float> =
            if (idx == null) emptyList() else samples.mapNotNull { it.v.getOrNull(idx) }

        val batteryTemps = column(batteryTempIdx).filter { it > -1f }
        // cpu_temp_c exists only in legacy layouts; V2 reads it from the footer.
        val cpuTemps = column(cpuTempIdx).filter { it > -1f }
        val batteryLevels = column(batteryLevelIdx)
        val cpuUsages = column(cpuUsageIdx)
        val gpuFps = column(gpuFpsIdx).filter { it > 0f }

        fun timestampOf(sample: TimeSeriesSample): Float? =
            timestampIdx?.let { sample.v.getOrNull(it) }

        val durationMs = if (samples.size >= 2) {
            (timestampOf(samples.last()) ?: 0f) - (timestampOf(samples.first()) ?: 0f)
        } else 0f

        val batteryDrain = if (batteryLevels.size >= 2) {
            batteryLevels.first() - batteryLevels.last()
        } else 0f

        // Heat-rise / cooldown rates require cpu_temp from v[]. Under V2 the
        // column is absent, so these collapse to 0 — see
        // footer.thermalSensorAvailability for the authoritative thermal
        // channel status.
        val stressSamples = samples.filter { it.phase in 3..4 }
        val heatRiseRate = if (cpuTempIdx != null && stressSamples.size >= 2 && cpuTemps.isNotEmpty()) {
            val stressCpuTemps = stressSamples.mapNotNull { it.v.getOrNull(cpuTempIdx) }.filter { it > -1f }
            if (stressCpuTemps.size >= 2) {
                val tempRise = stressCpuTemps.last() - stressCpuTemps.first()
                val timeMin = ((timestampOf(stressSamples.last()) ?: 0f) -
                    (timestampOf(stressSamples.first()) ?: 0f)) / 60000f
                if (timeMin > 0) tempRise / timeMin else 0f
            } else 0f
        } else 0f

        // T-03 cooling rate. V2 path puts COOLDOWN at phase 7 (V2Phase enum);
        // the previous `phase == 5` filter dated back to V1 when 5 WAS the
        // cooldown — under V2 phase 5 is COMBINED_PEAK so the rate was being
        // computed across the hottest phase and always landed near zero.
        //
        // Temperature source, by name: cpu_temp_c where the layout has it
        // (legacy V1/V4 — matches what the live SamplerStats path picks via
        // tempSource = cpu > … > battery), otherwise battery_temp_c (the only
        // in-sample thermal channel of the V2 layout).
        val cooldownSamples = samples.filter { it.phase == 7 }
        val cooldownTempIdx = cpuTempIdx ?: batteryTempIdx
        // Float.NaN signals "no phase-7 samples / can't compute" so the
        // consumer (ThermalTests / GenerateReportUseCase) can distinguish
        // "no data" (UNAVAILABLE / null) from a real measurement of 0 or
        // negative (rising temp during cooldown → FAIL).
        val cooldownRate = if (cooldownSamples.size >= 2 && cooldownTempIdx != null) {
            val coolTemps = cooldownSamples.mapNotNull { it.v.getOrNull(cooldownTempIdx) }.filter { it > -1f }
            if (coolTemps.size >= 2) {
                val tempDrop = coolTemps.first() - coolTemps.last()
                val timeMin = ((timestampOf(cooldownSamples.last()) ?: 0f) -
                    (timestampOf(cooldownSamples.first()) ?: 0f)) / 60000f
                if (timeMin > 0) tempDrop / timeMin else Float.NaN
            } else Float.NaN
        } else Float.NaN

        return TimeSeriesSummary(
            totalSamples = samples.size,
            durationMs = durationMs,
            maxBatteryTemp = batteryTemps.maxOrNull() ?: -1f,
            minBatteryTemp = batteryTemps.minOrNull() ?: -1f,
            avgBatteryTemp = batteryTemps.average().toFloat(),
            maxCpuTemp = cpuTemps.maxOrNull() ?: -1f,
            minCpuTemp = cpuTemps.minOrNull() ?: -1f,
            avgCpuTemp = cpuTemps.average().toFloat(),
            batteryDrainPercent = batteryDrain,
            avgCpuUsage = cpuUsages.average().toFloat(),
            maxCpuUsage = cpuUsages.maxOrNull() ?: 0f,
            avgGpuFps = if (gpuFps.isNotEmpty()) gpuFps.average().toFloat() else 0f,
            minGpuFps = gpuFps.minOrNull() ?: 0f,
            maxGpuFps = gpuFps.maxOrNull() ?: 0f,
            heatRiseRatePerMin = heatRiseRate,
            cooldownRatePerMin = cooldownRate,
            unresolvedColumns = unresolved.toList()
        )
    }
}

data class TimeSeriesSummary(
    val totalSamples: Int = 0,
    val durationMs: Float = 0f,
    val maxBatteryTemp: Float = -1f,
    val minBatteryTemp: Float = -1f,
    val avgBatteryTemp: Float = 0f,
    val maxCpuTemp: Float = -1f,
    val minCpuTemp: Float = -1f,
    val avgCpuTemp: Float = 0f,
    val batteryDrainPercent: Float = 0f,
    val avgCpuUsage: Float = 0f,
    val maxCpuUsage: Float = 0f,
    val avgGpuFps: Float = 0f,
    val minGpuFps: Float = 0f,
    val maxGpuFps: Float = 0f,
    val heatRiseRatePerMin: Float = 0f,
    // NaN default = "no data / not computed". Distinguishes from a real
    // measurement of 0 °C/min (slow but valid cooling) or a negative
    // measurement (rising temperature, surfaced as FAIL downstream).
    val cooldownRatePerMin: Float = Float.NaN,
    /**
     * fieldsVersion 68: names this summary needed from the file's
     * `variables[]` and did not find (see [TimeSeriesFileReader.calculateSummary]).
     * Empty = every needed column was found, so the metrics are real. A
     * non-empty list means the metrics that depend on the listed names are
     * at their absent sentinels, NOT measured values.
     */
    val unresolvedColumns: List<String> = emptyList()
)
