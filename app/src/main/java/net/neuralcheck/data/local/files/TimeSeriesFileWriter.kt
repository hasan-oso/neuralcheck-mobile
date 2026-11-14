package net.neuralcheck.data.local.files

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedWriter
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FileWriter
import java.io.OutputStreamWriter
import java.util.zip.GZIPOutputStream

/**
 * Handles buffered time-series data writing as specified in ADR section 4.6.
 *
 * Buffer samples in memory, flush to temp file periodically, and finalize
 * by building the full JSON structure and GZIP compressing it.
 */
class TimeSeriesFileWriter(private val context: Context) {

    companion object {
        private const val TAG = "NeuralCheck"
        private const val TEMP_DIR = "timeseries_temp"
        private const val FINAL_DIR = "timeseries"
        // Footer-only backup written next to the main .ts.json.gz. Small
        // (~2 KB), uncompressed JSON. Survives even when the main file is
        // truncated by mid-write OOM / I/O failure (observed on Xiaomi
        // MT6991 / HyperOS), so the AI pipeline can still recover the
        // critical V2 fix #56 fields (stressEffectiveness, dataQuality,
        // batteryHealth, knownCapacityClassification).
        private const val FOOTER_BACKUP_DIR = "timeseries_footer"
        private const val FLUSH_INTERVAL_SAMPLES = 10
    }

    // serializeNulls(): Gson default skips null fields, which makes
    // "missing in JSON" indistinguishable from "explicitly null at runtime".
    // Forcing null serialization removes that ambiguity — every footer
    // field appears in the JSON, with `null` as value when not populated.
    // Critical for diagnosing the Xiaomi MT6991 footer-truncation issue
    // (V2 fix #56 follow-up): if a field appears as `null` we know the
    // computation produced null; if it's missing entirely we know
    // serialization itself was truncated.
    private val gson: Gson = GsonBuilder()
        .serializeNulls()
        // fieldsVersion 68: footer.maxCpuTemp is kept on the class only so that a
        // pre-68 file can still be READ; a ≥ 68 file must not carry the key (it
        // was replaced by cpuTempFinalC / cpuTempFinalSource). Serialization-only,
        // so the reader's own Gson still fills it from an old file.
        .addSerializationExclusionStrategy(LegacyFooterFields)
        .create()
    private val sampleBuffer = ArrayList<TimeSeriesSample>()
    private var tempFile: File? = null
    private var tempWriter: BufferedWriter? = null
    private var header: TimeSeriesHeader? = null
    private var sampleCount = 0

    // fieldsVersion 65 — write-integrity telemetry:
    /** Flush attempts that failed (buffer kept, retried next flush). */
    @Volatile private var flushFailures: Int = 0

    /** fieldsVersion 65: exposed so completeScan can stamp the footer's
     *  [TimeSeriesFooter.flushFailureCount] before finalize. */
    fun getFlushFailureCount(): Int = flushFailures

    // Bug 7 fix: track sample shape so finalize() emits the right
    // VariableDefinition list (18-V1 vs 22-V2). Set by enableV2Mode()
    // before startNewFile.
    @Volatile private var v2Mode: Boolean = false

    /**
     * Bug 7 fix: enable V2 sample format. Call BEFORE startNewFile
     * when the sampler will emit 22-var samples instead of 18.
     * The variables list in the final JSON is selected based on
     * this flag so consumers can read v[18..21] without guessing.
     */
    fun enableV2Mode() { v2Mode = true }

    /**
     * fieldsVersion 68: the `variables[]` THIS scan's samples have — the V2
     * base block plus the per-device channels that passed discovery
     * ([ScanChannelLayout.variables]). Set after discovery and before the
     * first sample, because the sampler writes samples of exactly this
     * width. Without it [finalize] declares the fixed V2 base list (11) or
     * the V1 list (18), as before. Cleared by [cleanup] so a layout never
     * leaks into the next scan.
     */
    fun setVariableDefinitions(definitions: List<VariableDefinition>) {
        variableDefinitionsOverride = definitions
    }

    @Volatile private var variableDefinitionsOverride: List<VariableDefinition>? = null

    /**
     * Start a new time-series recording session.
     * @return path to the temporary file being written
     */
    fun startNewFile(header: TimeSeriesHeader): String {
        this.header = header
        sampleBuffer.clear()
        sampleCount = 0
        flushFailures = 0  // fieldsVersion 65: per-scan telemetry

        val tempDir = File(context.filesDir, TEMP_DIR)
        tempDir.mkdirs()
        tempFile = File(tempDir, "${header.scanId}.ts.tmp")

        try {
            tempWriter = BufferedWriter(FileWriter(tempFile!!, false))
            // Write a marker to indicate an active session
            tempWriter?.write("ACTIVE_SESSION:${header.scanId}\n")
            tempWriter?.flush()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to create temp file: ${e.message}")
        }

        return tempFile?.absolutePath ?: ""
    }

    /**
     * Add a single time-series sample to the buffer.
     * Auto-flushes when buffer reaches threshold.
     */
    fun addSample(sample: TimeSeriesSample) {
        sampleBuffer.add(sample)
        sampleCount++

        if (sampleBuffer.size >= FLUSH_INTERVAL_SAMPLES) {
            flush()
        }
    }

    /**
     * Flush buffered samples to the temp file.
     *
     * fieldsVersion 65 hardening: the buffer is cleared ONLY after the
     * whole chunk was written AND flushed successfully. On any failure
     * the buffer is kept intact (no sample loss) and [flushFailures] is
     * incremented; the next flush retries the whole buffer. A partial
     * failure can therefore leave some lines duplicated in the temp
     * file — [finalize] de-duplicates via TimeSeriesSample's
     * content-equality (ts + phase + v), so duplicates never reach the
     * final .ts.json.gz. When the temp writer never opened
     * (startNewFile I/O failure) the buffer is also kept: finalize
     * merges it in directly from memory.
     */
    fun flush() {
        if (sampleBuffer.isEmpty()) return

        val writer = tempWriter
        if (writer == null) {
            // No temp file — keep everything buffered; finalize() will
            // merge the in-memory buffer. Count once per flush attempt so
            // the footer telemetry shows the condition.
            flushFailures++
            return
        }
        try {
            // Serialize the whole chunk first so a Gson failure can't
            // leave a half-written line behind.
            val chunk = buildString {
                for (sample in sampleBuffer) {
                    append(gson.toJson(sample))
                    append('\n')
                }
            }
            writer.write(chunk)
            writer.flush()
            sampleBuffer.clear()
        } catch (e: Exception) {
            flushFailures++
            Log.w(TAG, "Failed to flush samples (buffer kept, " +
                "failure #$flushFailures): ${e.message}")
        }
    }

    /**
     * Writes a footer-only JSON file as a safety net BEFORE the main
     * .ts.json.gz finalize call. If [finalize] later fails (mid-write OOM
     * on Xiaomi MT6991, I/O interrupt, GZIP corruption), this small file
     * still carries the V2 fix #56 footer fields (stressEffectiveness,
     * dataQuality, batteryHealth, knownCapacityClassification) that the AI
     * pipeline depends on.
     *
     * Uncompressed (typically <2 KB) and synchronous so even a kill mid-
     * sample-buffer-flush leaves a recoverable footer. Returns the absolute
     * path or null on failure (best-effort — never blocks main flow).
     */
    fun writeFooterBackup(scanId: String, footer: TimeSeriesFooter): String? {
        return try {
            val backupDir = File(context.filesDir, FOOTER_BACKUP_DIR)
            backupDir.mkdirs()
            val backupFile = File(backupDir, "$scanId.footer.json")
            FileOutputStream(backupFile).use { fos ->
                OutputStreamWriter(fos, Charsets.UTF_8).use { writer ->
                    gson.toJson(footer, writer)
                }
            }
            // Force fsync so even a power-cut keeps the backup intact.
            // Best-effort; any Throwable here is benign.
            runCatching {
                java.io.FileOutputStream(backupFile, true).use { it.fd.sync() }
            }
            Log.i(TAG, "writeFooterBackup: wrote ${backupFile.length()} bytes to ${backupFile.absolutePath}")
            backupFile.absolutePath
        } catch (t: Throwable) {
            // Catch Throwable so OOM during footer-only write never disrupts
            // the surrounding completeScan flow. The main file write still
            // happens; AI just loses the secondary safety net.
            Log.w(TAG, "writeFooterBackup failed: ${t.javaClass.simpleName}: ${t.message}")
            null
        }
    }

    /**
     * Phase 1.8 — assemble the `"calibration"` footer block when both
     * the probe profile and the applied config are available. Snake-case
     * keys throughout to match existing footer conventions
     * (`battery_health`, `data_quality`, etc. all use snake_case in the
     * serialised JSON). The map is built explicitly rather than letting
     * Gson reflect over the dataclasses so the wire shape is independent
     * of any future field renames inside the dataclasses.
     */
    private fun buildCalibrationBlock(
        profile: net.neuralcheck.calibration.DeviceCapabilityProfile,
        config: net.neuralcheck.calibration.CalibratedWorkloadConfig
    ): Map<String, Any?> {
        return mapOf(
            "applied" to true,
            "profile" to mapOf(
                "memory_mbps" to profile.memoryMbps,
                "cpu_inferences_per_sec" to profile.cpuInferencesPerSec,
                "gpu_fps_at_1024sq" to profile.gpuFpsAt1024Sq,
                "storage_mbps" to profile.storageMbps,
                "probe_started_at_ms" to profile.probeStartedAtMs,
                "probe_duration_ms" to profile.probeDurationMs,
                "probe_failed_subsystems" to profile.probeFailedSubsystems
            ),
            "params_applied" to mapOf(
                "memory_buffer_bytes" to config.memoryBufferBytes,
                "memory_iteration_rate_hz" to config.memoryIterationRateHz,
                "cpu_inferences_per_sec" to config.cpuInferencesPerSec,
                "gpu_fbo_side_pixels" to config.gpuFboSidePixels,
                "storage_write_bytes_per_sec" to config.storageWriteBytesPerSec,
                "burst_fma_per_pulse" to config.burstFmaPerPulse
            ),
            "factors" to mapOf(
                "memory" to config.factors.memory,
                "cpu" to config.factors.cpu,
                "gpu" to config.factors.gpu,
                "storage" to config.factors.storage
            ),
            "flags" to mapOf(
                "probe_failed" to config.flags.probeFailed,
                "probe_below_floor" to config.flags.probeBelowFloor,
                "probe_above_ceiling" to config.flags.probeAboveCeiling
            )
        )
    }

    /**
     * Finalize the recording: build full JSON, GZIP compress, write final file.
     *
     * Phase 1.8 (fieldsVersion 62): two new optional parameters carry the
     * relative-calibration probe results + applied workload config. When
     * both are non-null, [finalize] builds the `calibration` footer block
     * documented in `TimeSeriesFooter.calibration` and copies it onto the
     * footer before serialisation. Either-side-null collapses to
     * `{"applied": false}`. Default `null` for both keeps Phase 1.7
     * behaviour for callers that haven't been migrated yet.
     *
     * fieldsVersion 65 hardening (silent-data-loss fixes):
     *  1. The GZIP payload is written to `<scanId>.ts.json.gz.tmp` and
     *     atomically renamed to the final name ONLY after a successful
     *     write + fsync. A failure deletes the partial tmp and returns
     *     NULL — the caller must treat the scan as having no time-series
     *     file instead of storing a path to a corrupt one. (Pre-65 the
     *     write failure was logged and the path returned as if valid.)
     *  2. Samples are read from the temp file into a de-duplicating set
     *     (guards against flush-retry duplicates), then any samples still
     *     sitting in the in-memory buffer (temp writer never opened, or
     *     final flush failed) are merged in — closing the path where a
     *     failed startNewFile silently dropped every sample.
     *  3. footer.samplesSha256 is stamped with a content checksum of the
     *     samples exactly as serialised into this file.
     *  4. A sidecar `<scanId>.ts.json.gz.sha256` with the SHA-256 of the
     *     final compressed file is written best-effort next to it, so
     *     upload pipelines can verify file-level integrity end-to-end.
     *
     * @param footer the footer object built by the caller. Will be
     *               copy()'d with the calibration block before write.
     * @param profile raw capability probe results, or null if no probe ran.
     * @param config calibrated workload params, or null if no calibration.
     * @return path to the final .ts.json.gz file, or NULL when the final
     *         write failed (no valid file exists; the .footer.json backup
     *         written earlier by completeScan remains the only artifact).
     */
    suspend fun finalize(
        footer: TimeSeriesFooter,
        profile: net.neuralcheck.calibration.DeviceCapabilityProfile? = null,
        config: net.neuralcheck.calibration.CalibratedWorkloadConfig? = null
    ): String? = withContext(Dispatchers.IO) {
        // Phase 1.8 — decide what calibration block to attach.
        // Defensive: log + treat as null when only one half is supplied.
        val calibrationBlock: Map<String, Any?> = when {
            profile == null && config == null ->
                mapOf("applied" to false)
            profile != null && config != null ->
                buildCalibrationBlock(profile, config)
            else -> {
                Log.w(
                    TAG,
                    "finalize: calibration profile/config asymmetry " +
                        "(profile=${profile != null}, config=${config != null}) — " +
                        "treating as not-applied"
                )
                mapOf("applied" to false)
            }
        }
        // cleanup() must run no matter what — otherwise a crash here would
        // leave a stale .tmp behind and findIncompleteSessions() would keep
        // flagging it every time the app opens.
        try {
            // Flush any remaining buffered samples
            flush()

            val scanId = header?.scanId ?: throw IllegalStateException("No active session")
            val finalDir = File(context.filesDir, FINAL_DIR)
            finalDir.mkdirs()
            val finalFile = File(finalDir, "$scanId.ts.json.gz")

            // Read all samples from temp file.
            // fieldsVersion 65: LinkedHashSet keeps write order but drops
            // duplicates left by a flush that failed after partially
            // writing its chunk (content-equality via TimeSeriesSample's
            // ts + phase + v.contentEquals — two genuinely distinct
            // samples always differ in ts).
            val allSamples = LinkedHashSet<TimeSeriesSample>()
            try {
                tempFile?.let { file ->
                    if (file.exists()) {
                        file.bufferedReader().useLines { lines ->
                            lines.forEach { line ->
                                if (!line.startsWith("ACTIVE_SESSION:") && line.isNotBlank()) {
                                    try {
                                        allSamples.add(gson.fromJson(line, TimeSeriesSample::class.java))
                                    } catch (e: Exception) {
                                        Log.w(TAG, "Skipping malformed sample line: ${e.message}")
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error reading temp file: ${e.message}")
            }
            // fieldsVersion 65: merge whatever is still in the in-memory
            // buffer — non-empty when the temp writer never opened
            // (startNewFile I/O failure) or the final flush failed. Before
            // v65 these samples were silently dropped and the scan
            // "succeeded" with an empty samples array.
            if (sampleBuffer.isNotEmpty()) {
                Log.w(TAG, "finalize: merging ${sampleBuffer.size} unflushed " +
                    "in-memory samples (flushFailures=$flushFailures)")
                allSamples.addAll(sampleBuffer)
            }
            val orderedSamples = allSamples.toList()

            // fieldsVersion 65: content checksum over the samples exactly
            // as they will appear in this file (canonical per-sample JSON
            // joined by '\n'). Consumers re-serialise + hash to verify.
            val samplesSha = runCatching {
                val digest = java.security.MessageDigest.getInstance("SHA-256")
                for ((i, s) in orderedSamples.withIndex()) {
                    if (i > 0) digest.update('\n'.code.toByte())
                    digest.update(gson.toJson(s).toByteArray(Charsets.UTF_8))
                }
                digest.digest().joinToString("") { "%02x".format(it) }
            }.getOrNull()

            val effectiveFooter = footer.copy(
                calibration = calibrationBlock,
                samplesSha256 = samplesSha,
                flushFailureCount = flushFailures
            )

            // Build complete JSON structure
            // Bug 7 fix: pick the variable definitions matching the sample
            // shape — V2 mode emits V5 samples and must declare the
            // matching entries, otherwise consumers have no schema.
            val variableDefs = variableDefinitionsOverride
                ?: if (v2Mode) V2_VARIABLE_DEFINITIONS else TimeSeriesVariables.definitions
            // fieldsVersion 68: every sample must be as wide as the list that
            // declares its columns. A mismatch means the sampler and the
            // writer were configured with different layouts; the file would
            // still be written (losing the scan is worse), but say so.
            val widthMismatches = orderedSamples.count { it.v.size != variableDefs.size }
            if (widthMismatches > 0) {
                Log.e(TAG, "finalize: $widthMismatches of ${orderedSamples.size} samples are not " +
                    "${variableDefs.size} columns wide (variables[] vs sample width) — " +
                    "readers resolving by name will mis-index them")
            }
            val fullData = TimeSeriesData(
                header = header!!,
                variables = variableDefs,
                samples = orderedSamples,
                footer = effectiveFooter
            )

            // GZIP compress and write — ATOMICALLY (fieldsVersion 65).
            //
            // V2 fix #56 (2026-05-04): catch Throwable instead of Exception.
            // OutOfMemoryError etc. are NOT Exceptions — they used to leave
            // partial JSON inside a valid GZIP trailer that parsed as a
            // truncated-footer file (Xiaomi MT6991 / HyperOS bug).
            //
            // fieldsVersion 65: the write goes to a .tmp sibling first and
            // is renamed onto the final name only after success + fsync, so
            // a failure can never leave a half-written file under the final
            // name — and the failure now propagates as a NULL return
            // instead of a valid-looking path.
            val gzTmp = File(finalDir, "$scanId.ts.json.gz.tmp")
            val written: Boolean = try {
                FileOutputStream(gzTmp).use { fos ->
                    GZIPOutputStream(fos).use { gzos ->
                        OutputStreamWriter(gzos, Charsets.UTF_8).use { writer ->
                            gson.toJson(fullData, writer)
                        }
                    }
                }
                // fsync before rename so the rename never publishes a file
                // whose blocks are still in the page cache on power-cut.
                runCatching { FileOutputStream(gzTmp, true).use { it.fd.sync() } }
                if (finalFile.exists()) finalFile.delete()
                val renamed = gzTmp.renameTo(finalFile)
                if (!renamed) {
                    Log.e(TAG, "finalize: atomic rename failed " +
                        "(${gzTmp.name} → ${finalFile.name})")
                }
                renamed
            } catch (t: Throwable) {
                Log.e(
                    TAG,
                    "Failed to write final GZIP file: ${t.javaClass.name}: ${t.message} " +
                        "cause=${t.cause?.javaClass?.simpleName}:${t.cause?.message} " +
                        "(returning null; footer backup .footer.json should still exist)",
                    t
                )
                false
            } finally {
                if (gzTmp.exists()) runCatching { gzTmp.delete() }
            }

            if (!written) {
                return@withContext null
            }

            // fieldsVersion 65: best-effort file-level checksum sidecar so
            // upload/training pipelines can verify the .gz end-to-end.
            runCatching {
                val digest = java.security.MessageDigest.getInstance("SHA-256")
                FileInputStream(finalFile).use { fis ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = fis.read(buf)
                        if (n < 0) break
                        digest.update(buf, 0, n)
                    }
                }
                val hex = digest.digest().joinToString("") { "%02x".format(it) }
                File(finalDir, "$scanId.ts.json.gz.sha256").writeText(hex)
            }.onFailure {
                Log.w(TAG, "finalize: sha256 sidecar failed: ${it.message}")
            }

            Log.i(TAG, "finalize: wrote ${finalFile.length()} bytes " +
                "(samples=${orderedSamples.size} fieldsVersion=${footer.fieldsVersion} " +
                "flushFailures=$flushFailures samplesSha256=${samplesSha?.take(12)}…)")
            finalFile.absolutePath
        } finally {
            cleanup()
        }
    }

    /**
     * Delete temp files for this session.
     */
    fun cleanup() {
        try {
            tempWriter?.close()
            tempWriter = null
            tempFile?.delete()
            tempFile = null
            variableDefinitionsOverride = null
        } catch (e: Exception) {
            Log.w(TAG, "Cleanup error: ${e.message}")
        }
    }

    /**
     * Find incomplete sessions from previous crashes.
     * @return list of scan IDs with incomplete temp files
     */
    fun findIncompleteSessions(): List<String> {
        val tempDir = File(context.filesDir, TEMP_DIR)
        if (!tempDir.exists()) return emptyList()

        return tempDir.listFiles()
            ?.filter { it.extension == "tmp" }
            ?.mapNotNull { file ->
                try {
                    val firstLine = file.bufferedReader().readLine()
                    if (firstLine?.startsWith("ACTIVE_SESSION:") == true) {
                        firstLine.removePrefix("ACTIVE_SESSION:")
                    } else null
                } catch (e: Exception) {
                    null
                }
            } ?: emptyList()
    }
}

// --- Data Classes for Time-Series ---

data class TimeSeriesHeader(
    val scanId: String,
    val deviceFingerprint: String,
    val scanType: String,
    val appVersion: String,
    val androidApiLevel: Int,
    val deviceModel: String,
    val cpuCores: Int,
    val totalRamMb: Long,
    val startTimestamp: Long,
    val batteryLevelStart: Int,
    val batteryTempStart: Float,
    /**
     * fieldsVersion 71 (A-P6-13): `true` / `false` when the power-saving mode was read; null — an ABSENT key — when it could not be
     * ([powerSaveState] `read_failed`) or the writer was not told (it used to default to `false`: an absence read as "off", rule 22).
     */
    val isPowerSaveMode: Boolean? = null,
    /**
     * fieldsVersion 71 (A-P6-13, `powerSaveState`): `off` | `on` | `read_failed` ([net.neuralcheck.service.stress.PowerSaveReading]). null = a file written before
     * fieldsVersion 71 (stamp < 71), whose reader reads [isPowerSaveMode].
     */
    val powerSaveState: String? = null
)

data class TimeSeriesSample(
    val ts: Long,          // timestamp_ms
    val phase: Int,        // 1-5
    val v: FloatArray      // 18 values
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as TimeSeriesSample
        return ts == other.ts && phase == other.phase && v.contentEquals(other.v)
    }

    override fun hashCode(): Int {
        var result = ts.hashCode()
        result = 31 * result + phase
        result = 31 * result + v.contentHashCode()
        return result
    }
}

data class TimeSeriesFooter(
    val endTimestamp: Long,
    val totalSamples: Int,
    val isComplete: Boolean,
    val earlyStopReason: String?,
    val batteryLevelEnd: Int,
    val maxBatteryTemp: Float,
    /**
     * LEGACY (≤ 67 files only): the CPU temperature under a name that said "max"
     * and held, by a three-branch cascade, a live read at finalize, the sampler's
     * maximum, or the sentinel -1f. fieldsVersion 68 (S-33) replaced it by
     * [cpuTempFinalC] + [cpuTempFinalSource]; this property exists so an OLD file
     * can still be read and forwarded to the server, which reads it for ≤ 67
     * (BehaviorFingerprintBuilder). It is null in every file this build writes and
     * the writer's Gson never serializes it. Do not set it.
     */
    val maxCpuTemp: Float? = null,
    /**
     * PowerManager.getThermalHeadroom mean across the run (Android 11+).
     * Default null preserves backward compatibility with pre-headroom
     * .ts.json.gz files written before this field existed; Gson populates
     * the default when the JSON property is absent.
     */
    val thermalHeadroomAvg: Float? = null,
    /** Worst-case (highest) thermal headroom across the run; null on older API/unsupported. */
    val thermalHeadroomPeak: Float? = null,
    /** Worst-case PowerManager.currentThermalStatus during the run (0..6); null on older API. */
    val thermalStatusMax: Int? = null,
    /** Effective tempSource: "cpu", "gpu", "headroom", "battery", or "none". */
    val tempSource: String? = null,
    // ─────────────────── V2 additions ────────────────────────────────
    // All optional with defaults so V1 .ts.json.gz files keep deserialising.
    /** V2 adaptive intensity factor. ⚠ CONSTANT since V2 fix #30 disabled
     *  local tier classification: always 1.0 on the V2 path (all devices
     *  get full intensity; the AI judges cooling from raw
     *  tempRiseRateInProbe). Listed in [unmeasuredDefaults] since
     *  fieldsVersion 65 so consumers don't mistake it for a measurement. */
    val intensityFactor: Float = 1.0f,
    /** V2 device tier classification. ⚠ CONSTANT since V2 fix #30:
     *  always "flagship_cooling" on the V2 path (see
     *  AdaptiveIntensityController.probe — tier hardcoded, classification
     *  delegated to the AI). Listed in [unmeasuredDefaults] since
     *  fieldsVersion 65. */
    val deviceTierMeasured: String = "v1_no_probe",
    /** Bug 2 fix: scaling/sign decision applied to battery_current_ua samples.
     *  One of: "x1_no_flip" (AOSP-compliant), "x1000_no_flip" (mA without inversion),
     *  "x1000_flip" (Samsung-style), "x1_flip" (rare), or "default_<vendor>" /
     *  "data_ua_format" / "data_ma_format" / "unresolved" for diagnostics. */
    val batteryCurrentScalingApplied: String = "unresolved",
    /** V2 INIT_PROFILING probe rate of CPU temp rise during the 8 s mini-burst. */
    val tempRiseRateInProbe: Float = 0.0f,
    /** V2 throttling events recorded by DynamicThermalThrottler. */
    val throttlingEvents: List<ThrottlingEvent> = emptyList(),
    /** V2 verification kernel results (CPU/GPU/memory/storage). */
    val verificationResults: VerificationSummary = VerificationSummary(),
    /** V2 phase outcomes: phaseId → "completed" | "early_terminated" | "skipped". */
    val phaseCompletionStatus: Map<Int, String> = emptyMap(),
    /**
     * G2/P2.3: observed start/end (ms since scan start, same reference
     * frame as v[0]) for every phase this scan actually ran, recorded
     * directly inside StressTestServiceV2.runPhase() rather than inferred
     * from the sampler's per-sample phase tag. Empty on the V1 path (no
     * per-phase rate table there either — see [expectedSamples]) and on
     * builds before this field existed.
     *
     * Added so a consumer can locate the exact GPU_SUSTAINED (phaseId 4)
     * / COMBINED_PEAK (phaseId 5) window in v[] and recompute what G-03
     * (FPS drop), G-04 (GPU thermal throttle), and G-05 (min sustained
     * FPS) used to compute on-device from SamplerStats' running max/min/
     * initial FPS — that on-device aggregation (peak-picking + a derived
     * ratio) was removed as part of this same change; see GpuTests.kt and
     * FunctionalTestRunner.computeTimeSeriesTests. The app now ships only
     * the raw v[10] gpu_fps series plus this boundary list; G-03/G-04/G-05
     * are the server's computation to make, using them.
     */
    val phaseBoundaries: List<PhaseBoundary> = emptyList(),
    /**
     * V2 fix #54: stress effectiveness metrics — detects when the OS
     * thermal governor pre-emptively capped frequency, preventing the
     * test from reaching saturation. AI uses [passedSaturationThreshold]
     * to gate confidence in the rest of the metrics.
     */
    val stressEffectiveness: StressEffectiveness = StressEffectiveness(),
    /**
     * V2 fix #56 (2.3): battery health snapshot taken at scan completion.
     * Design + current capacity, cycle count, official SoH. Each field
     * tagged with its source so the AI can weight by reliability.
     * Map<String,Any?> shape (not a Kotlin data class) so it serialises
     * cleanly to JSON without needing the AI side to know the structure.
     */
    val batteryHealth: Map<String, Any?>? = null,
    /**
     * V2 fix #56 (2.4): per-sensor coverage and source tags. Tells the AI
     * which signals were populated (and how) over the scan duration.
     */
    val dataQuality: DataQuality = DataQuality(),
    /**
     * V2 fix #56 (3.4): comparison of sysfs design capacity vs the
     * known-published value for this Build.MODEL. Tags such as
     * "likely_single_cell_reading" or "full_capacity" let the AI catch
     * dual-cell halving anomalies that pure sysfs would miss.
     * Null when model isn't in the table or sysfs design unreadable.
     */
    val knownCapacityClassification: String? = null,
    /**
     * V2 fix #56 follow-up (2026-05-04): build version stamp that proves
     * the device wrote this footer with the latest schema. Read from
     * BuildConfig.FIELDS_VERSION at construction time. Lets the AI /
     * backend tell apart "device on old build" from "device on new build
     * but field truncation occurred" — the bug where Xiaomi MT6991
     * dropped stressEffectiveness, dataQuality, batteryHealth,
     * knownCapacityClassification while Samsung wrote them correctly.
     *
     * Default 0 means "pre-stamp build" — a real value means the writer
     * code intentionally populated this footer.
     */
    val fieldsVersion: Int = 0,
    /**
     * V2 fix #56 follow-up (2026-05-04): per-field computation status
     * map. Captures "ok" | "fail:<exception class>" for each runCatching
     * block in StressTestService.completeScan, so the AI can see whether
     * a missing/empty field is the result of:
     *   a. computation failure on this device (e.g. SecurityException
     *      reading sysfs on HyperOS), recorded as fail:<class>
     *   b. a successful computation that legitimately yielded null/empty
     *      (recorded as ok)
     *   c. or — the worst case — a serialization truncation where the
     *      diagnostics map itself isn't preserved (in which case the
     *      footer backup file at .footer.json fills the gap)
     *
     * Keys mirror the field names in this class: "stressEffectiveness",
     * "batteryHealth", "dataQuality", "knownCapacityClassification".
     */
    val footerDiagnostics: Map<String, String>? = null,
    /**
     * V5 schema (Task D): probe of every signal source attempted at scan
     * start. Lets the AI see "we tried channel X — outcome was N" rather
     * than guess from sentinel values inside v[]. Default null preserves
     * compatibility with V4 .ts.json.gz files that pre-date this field.
     */
    val channelDiscovery: ChannelDiscoveryReport? = null,
    /**
     * V5 schema (Task F): per-thermal-channel availability flag. Derived
     * from [channelDiscovery] but easier for AI to read at the top level.
     * Null when discovery didn't run (V1 datacollect path).
     */
    val thermalSensorAvailability: ThermalSensorAvailability? = null,
    /**
     * V5 schema (Task E): aggregate stillness boolean from accelerometer
     * std-dev. AI weights motion-correlated metrics down when isStill =
     * false. Null when the record could not be computed at all (the reason is in
     * footerDiagnostics.scanStillness) or discovery didn't run. With fewer
     * than 2 accel readings the record EXISTS and says so: fieldsVersion 68
     * leaves isStill / accelStdDev absent and writes
     * stillnessState = insufficient_accel_samples.
     */
    val scanStillness: StillnessReport? = null,
    /**
     * Phase 1.7.D — list of pre-scan warning IDs the user
     * acknowledged-but-proceeded-through. Empty list means a clean
     * pre-scan check; null means the holder wasn't read (legacy paths
     * / V1 datacollect flavor).
     *
     * Stable IDs (NOT localized strings):
     *   "hot_battery"        — battery temp ≥ 34°C at scan start
     *   "recently_unplugged" — charger removed within 15 min of start
     *   "charger_plugged"    — never appears (hard block; scan blocked)
     *
     * Lab parsers can downgrade scan trust based on these flags.
     */
    val preScanWarnings: List<String>? = null,
    /**
     * fieldsVersion 68 (ت-18 row 7, N-14): whether [preScanWarnings] could be
     * read — `read` | `read_failed:<message>`. Before, a failed read was
     * written as an empty list, i.e. "clean pre-scan check"; now the list is
     * null on a failed read and this says so. null = a file written before
     * fieldsVersion 68.
     */
    val preScanWarningsState: String? = null,
    /** fieldsVersion 71 (A-P6-13, N71-07): the battery window the scan's start was judged against; null = a file written before 71. */
    val batteryWindowPct: BatteryWindowPctFooter? = null,
    /** fieldsVersion 71 (A-P6-13, N71-08): what came of holding the screen's brightness; null = a file written before 71. */
    val brightnessLock: BrightnessLockFooter? = null,
    /**
     * Phase 1.8 (fieldsVersion 62): relative-calibration block.
     *
     * Populated by [TimeSeriesFileWriter.finalize] when both a
     * [net.neuralcheck.calibration.DeviceCapabilityProfile] and a
     * [net.neuralcheck.calibration.CalibratedWorkloadConfig] are
     * supplied to the call. When either is missing the writer emits
     * `{"applied": false}` instead of populating this field with the
     * full block — see [TimeSeriesFileWriter.finalize] for the full
     * decision table and JSON shape.
     *
     * Default null preserves Phase 1.7 footer behaviour for any caller
     * that hasn't yet wired calibration. Probe + calibration logic land
     * in Phase 1.8 Tasks 2–5; this field is the destination they target.
     */
    val calibration: Map<String, Any?>? = null,
    // ─────── fieldsVersion 65 (additive) — measurement integrity ───────
    /**
     * Names of footer fields whose values in THIS file are compile-time
     * constants, not measurements. Written by completeScan so training
     * pipelines and the AI can exclude them. On the V2 path this always
     * contains "deviceTierMeasured" and "intensityFactor" (constant since
     * V2 fix #30); on the V1 path it additionally contains
     * "tempRiseRateInProbe" (no probe runs). Null = footer written by a
     * pre-65 build (no declaration available).
     */
    val unmeasuredDefaults: List<String>? = null,
    /**
     * Expected sample count for a COMPLETE scan, computed from the
     * nominal phase durations × per-phase sampling rates
     * (Σ V2Phase.durationMs/1000 × samplingHz = 1605 for V2). Compare
     * with [totalSamples]: a large deficit on an isComplete=true scan
     * means silent sampler loss; a deficit on an early-stopped scan is
     * expected. Null on the V1 path (no per-phase rate table) and on
     * pre-65 builds.
     */
    val expectedSamples: Int? = null,
    /**
     * SHA-256 (lowercase hex) over the canonical serialisation of the
     * samples array as written to this file — computed by
     * [TimeSeriesFileWriter.finalize] over `gson.toJson(sample)` lines
     * joined by '\n'. A consumer re-serialising the parsed samples the
     * same way and hashing MUST reproduce this value; a mismatch means
     * sample-level corruption between write and read. Null on pre-65
     * builds or when hashing failed (see footerDiagnostics).
     */
    val samplesSha256: String? = null,
    /**
     * Number of buffered-flush attempts that FAILED during the scan
     * (writer kept the buffer and retried on the next flush; finalize
     * de-duplicates any partially-written lines). >0 with a healthy
     * [samplesSha256] means the recovery path worked; large values
     * indicate storage pressure on the device. Null on pre-65 builds.
     */
    val flushFailureCount: Int? = null,
    // ─────── fieldsVersion 68 (additive) — per-device channel layout ───────
    /**
     * fieldsVersion 68 (N-06, S-06): the cpufreq policies of the device, raw
     * (see [CpuPolicyInfo]) — one element per policy whose existence the
     * discovery saw, ascending policy number, including policies whose
     * current-frequency channel was NOT admitted (their reason is in the
     * element and in channelDiscovery.reasons). Empty list = discovery ran
     * and found no policy; null = discovery did not run / failed (V1 flavor,
     * or the probe threw — footerDiagnostics says which) or a file written
     * before fieldsVersion 68.
     */
    val cpuPolicies: List<CpuPolicyInfo>? = null,
    /**
     * fieldsVersion 68 (S-27): per per-device channel of this scan, the
     * 0-based indices in samples[] whose read FAILED; the value stored in
     * v[] at such an index is the last valid one (stale) and must be left out
     * of any use of that channel. Every per-device channel of the scan is a
     * key (an empty list = no failed read); null = not tracked (V1 flavor or
     * a file written before fieldsVersion 68). At most 200 indices per
     * channel — see [channelReadFailureCounts] for the true total.
     */
    val channelReadFailures: Map<String, List<Int>>? = null,
    /**
     * fieldsVersion 68 (S-27): per per-device channel, the TOTAL number of
     * failed reads. When a count exceeds the size of that channel's list in
     * [channelReadFailures] the list is truncated at 200 and the channel's
     * remaining failures are unlocated. null = not tracked.
     */
    val channelReadFailureCounts: Map<String, Int>? = null,
    // ─────── fieldsVersion 68 (additive) — slow thermal channels as lists ───────
    /**
     * fieldsVersion 68 (ت-03, N-02, S-03, S-35): one element per
     * `getThermalHeadroom(0)` call, a call every 10 s starting at t≈0
     * (see ThermalListCollector). Every element carries the state of its own
     * call, so an element whose call did not yield a number (`nan`,
     * `exception:<message>`, `api_below_30`, …) has `headroom = null` and says
     * why. The channel never was in `v[]`; the old [thermalHeadroomAvg] /
     * [thermalHeadroomPeak] stay beside this list as transitional aggregates.
     * null = not tracked (V1 flavor or a pre-68 file), and then
     * [thermalHeadroomState] is absent too.
     */
    val thermalHeadroomReadings: List<ThermalHeadroomReading>? = null,
    /** fieldsVersion 68 (N-02): the state of the headroom channel as a whole —
     *  see [ThermalListStates] (`ok` · `api_below_30` · `no_reading_taken` ·
     *  `start_failed:<text>`). Present whenever [thermalHeadroomReadings] is. */
    val thermalHeadroomState: String? = null,
    /**
     * fieldsVersion 68 (ت-04, N-03, S-04): the thermal status over the scan —
     * first element a read at listener registration, then one per callback;
     * `status` is the raw 0..6. No `v[13]` channel exists for it. The old
     * [thermalStatusMax] stays beside it as a transitional aggregate.
     */
    val thermalStatusEvents: List<ThermalStatusEvent>? = null,
    /** fieldsVersion 68 (N-03): `ok` · `api_below_29` ·
     *  `listener_registration_failed:<text>` (· `initial_read_failed:<text>`).
     *  Present whenever [thermalStatusEvents] is. */
    val thermalStatusState: String? = null,
    // ─────── fieldsVersion 68 (additive) — raw battery pairs ───────
    /**
     * fieldsVersion 68 (ت-06, N-05, S-10, S-28 corrected): the raw battery pairs
     * taken at each ACTION_BATTERY_CHANGED broadcast — see [BatteryPair]. At most
     * 200, the FIRST 200; `t` = ms since scan start. Raw: no unit conversion,
     * no scaling or sign flip of the current, no integer percentage. A field that
     * was not a value is null here and absent on the wire.
     * null = not recorded (V1 flavor, a scan that ended before recording began,
     * or a pre-68 file) — and then [batteryPairsState] is absent too.
     */
    val batteryPairs: List<BatteryPair>? = null,
    /** fieldsVersion 68 (N-05): the explicit state of [batteryPairs] — see
     *  [BatteryPairsState]. Present whenever [batteryPairs] is. */
    val batteryPairsState: BatteryPairsState? = null,
    // ─────── fieldsVersion 68 (additive) — transitional aggregates declared ───────
    /**
     * fieldsVersion 68 (N-23, S-20, S-31): the names of the footer fields that
     * are on-device AGGREGATES (a mean, a peak, a ratio, a verdict) shipped
     * beside the raw for the transitional display, to be removed when the server
     * reads the raw instead — see [TransitionalAggregates]. A name with a dot is
     * `<object>.<field>`. null = a file written before fieldsVersion 68 (no
     * declaration available).
     */
    val transitionalAggregates: List<String>? = null,
    // ─────── fieldsVersion 68 (additive) — the CPU temperature, with its source ───────
    /**
     * fieldsVersion 68 (T-10, S-13, S-33, N-08): the footer's CPU temperature in °C,
     * `null` when no trustworthy reading existed — never the sentinel -1f. It is
     * NOT a maximum unless [cpuTempFinalSource] says `sampler_max_cpu`: with
     * `live_read_at_finalize` it is the reading taken when the scan was finalised.
     * Replaces `maxCpuTemp`.
     */
    val cpuTempFinalC: Float? = null,
    /** `live_read_at_finalize` | `sampler_max_cpu` | `absent:<reason>`
     *  ([CpuTempSources]). Present whenever [cpuTempFinalC] could be decided;
     *  null only in a pre-68 file. */
    val cpuTempFinalSource: String? = null
)

/** Excludes the legacy footer fields from what the writer's Gson serializes (see [TimeSeriesFileWriter]). */
private object LegacyFooterFields : com.google.gson.ExclusionStrategy {
    override fun shouldSkipField(f: com.google.gson.FieldAttributes): Boolean =
        f.declaringClass == TimeSeriesFooter::class.java && f.name == "maxCpuTemp"

    override fun shouldSkipClass(clazz: Class<*>?): Boolean = false
}

data class TimeSeriesData(
    val header: TimeSeriesHeader,
    val variables: List<VariableDefinition>,
    val samples: List<TimeSeriesSample>,
    val footer: TimeSeriesFooter,
    /**
     * fieldsVersion 68 (T-18 row 11): true ONLY for the object
     * [TimeSeriesFileReader] builds when the main file was unreadable and just
     * the footer-only backup was recovered — its `header` then holds the
     * placeholders 0 / "" and its samples and variables are empty, none of which
     * are readings. `@Transient`: never written to or read from a file (Gson
     * skips it), set only by the recovery path.
     */
    @Transient val recoveredFromFooterBackup: Boolean = false
)

data class VariableDefinition(
    val index: Int,
    val name: String,
    val unit: String,
    val source: String
)

object TimeSeriesVariables {
    val definitions = listOf(
        VariableDefinition(0, ChannelNames.TIMESTAMP_MS, "ms", "SystemClock.elapsedRealtime()"),
        VariableDefinition(1, ChannelNames.BATTERY_TEMP_C, "°C", "BatteryManager EXTRA_TEMPERATURE / 10"),
        VariableDefinition(2, ChannelNames.BATTERY_VOLTAGE_MV, "mV", "EXTRA_VOLTAGE"),
        VariableDefinition(3, ChannelNames.BATTERY_CURRENT_UA, "µA", "BATTERY_PROPERTY_CURRENT_NOW"),
        VariableDefinition(4, ChannelNames.BATTERY_LEVEL_PCT, "%", "EXTRA_LEVEL / EXTRA_SCALE × 100"),
        VariableDefinition(5, ChannelNames.CPU_TEMP_C, "°C", "thermal_zone (cpu) or -1"),
        VariableDefinition(6, ChannelNames.CPU_FREQ_BIG_KHZ, "kHz", "scaling_cur_freq (big cluster) or -1"),
        VariableDefinition(7, ChannelNames.CPU_FREQ_LITTLE_KHZ, "kHz", "scaling_cur_freq (little cluster) or -1"),
        VariableDefinition(8, ChannelNames.CPU_FREQ_MID_KHZ, "kHz", "scaling_cur_freq (mid cluster) or -1"),
        VariableDefinition(9, ChannelNames.CPU_USAGE_PCT, "%", "/proc/stat calculation"),
        VariableDefinition(10, ChannelNames.GPU_FPS, "fps", "OpenGL frame counter"),
        VariableDefinition(11, ChannelNames.GPU_TEMP_C, "°C", "thermal_zone (gpu) or -1"),
        VariableDefinition(12, ChannelNames.ACCEL_X, "m/s²", "TYPE_ACCELEROMETER"),
        VariableDefinition(13, ChannelNames.ACCEL_Y, "m/s²", "TYPE_ACCELEROMETER"),
        VariableDefinition(14, ChannelNames.ACCEL_Z, "m/s²", "TYPE_ACCELEROMETER"),
        VariableDefinition(15, ChannelNames.GYRO_X, "rad/s", "TYPE_GYROSCOPE"),
        VariableDefinition(16, ChannelNames.GYRO_Y, "rad/s", "TYPE_GYROSCOPE"),
        VariableDefinition(17, ChannelNames.GYRO_Z, "rad/s", "TYPE_GYROSCOPE")
    )
}
