package net.neuralcheck.domain.usecase

import android.content.Context
import net.neuralcheck.data.repository.ScanRepository
import net.neuralcheck.data.repository.TestResultRepository
import net.neuralcheck.data.repository.ScoreRepository
import net.neuralcheck.data.repository.TimeSeriesRepository
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject

/**
 * Exports all scan data as a ZIP file containing:
 * - scans.json: all scan metadata
 * - results/{scanId}.json: test results per scan
 * - scores/{scanId}.json: scoring data per scan
 * - timeseries/{scanId}.ts.json.gz: raw time-series files
 */
class ExportDataUseCase @Inject constructor(
    private val context: Context,
    private val scanRepository: ScanRepository,
    private val testResultRepository: TestResultRepository,
    private val scoreRepository: ScoreRepository,
    private val timeSeriesRepository: TimeSeriesRepository
) {
    private val gson = GsonBuilder().setPrettyPrinting().create()

    /**
     * Export all data to a ZIP file.
     * @return File path of the created ZIP, or null on failure
     */
    suspend fun execute(): String? = withContext(Dispatchers.IO) {
        try {
            val exportDir = File(context.cacheDir, "export")
            exportDir.mkdirs()
            val zipFile = File(exportDir, "neuralcheck_export_${System.currentTimeMillis()}.zip")

            val scans = scanRepository.getAllScans().first()
            if (scans.isEmpty()) return@withContext null

            ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                // Export scans metadata
                zos.putNextEntry(ZipEntry("scans.json"))
                zos.write(gson.toJson(scans).toByteArray(Charsets.UTF_8))
                zos.closeEntry()

                for (scan in scans) {
                    // Export test results
                    val results = testResultRepository.getResults(scan.scanId)
                    if (results.isNotEmpty()) {
                        zos.putNextEntry(ZipEntry("results/${scan.scanId}.json"))
                        zos.write(gson.toJson(results).toByteArray(Charsets.UTF_8))
                        zos.closeEntry()
                    }

                    // Export scores
                    val score = scoreRepository.getByScanId(scan.scanId)
                    if (score != null) {
                        zos.putNextEntry(ZipEntry("scores/${scan.scanId}.json"))
                        zos.write(gson.toJson(score).toByteArray(Charsets.UTF_8))
                        zos.closeEntry()
                    }

                    // Export time-series files
                    val tsPath = timeSeriesRepository.getTimeSeriesPath(scan.scanId)
                    if (tsPath != null) {
                        val tsFile = File(tsPath)
                        if (tsFile.exists()) {
                            zos.putNextEntry(ZipEntry("timeseries/${scan.scanId}.ts.json.gz"))
                            tsFile.inputStream().use { input ->
                                input.copyTo(zos)
                            }
                            zos.closeEntry()
                        }
                    }
                }

                // Export metadata
                val metadata = mapOf(
                    "exportTimestamp" to System.currentTimeMillis(),
                    "appVersion" to net.neuralcheck.BuildConfig.VERSION_NAME,
                    "totalScans" to scans.size,
                    "format" to "neuralcheck_export_v1"
                )
                zos.putNextEntry(ZipEntry("metadata.json"))
                zos.write(gson.toJson(metadata).toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }

            zipFile.absolutePath
        } catch (e: Exception) {
            android.util.Log.w("NeuralCheck", "Export failed: ${e.message}")
            null
        }
    }
}
