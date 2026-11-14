package net.neuralcheck.hardware.storage

import android.content.Context
import android.os.Environment
import android.os.StatFs
import net.neuralcheck.domain.model.TestResult
import net.neuralcheck.domain.model.TestStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.Locale
import javax.inject.Inject
import kotlin.random.Random

/**
 * Storage functional tests S-01 through S-06.
 */
class StorageTests @Inject constructor(private val context: Context) {

    companion object {
        private const val SEQ_FILE_SIZE = 64L * 1024 * 1024  // 64MB
        private const val RANDOM_OPS = 1000
        private const val RANDOM_BLOCK = 4096
    }

    suspend fun runAll(): List<TestResult> = withContext(Dispatchers.IO) {
        listOf(
            testTotalStorage(),
            testAvailableStorage(),
            testSequentialWrite(),
            testSequentialRead(),
            testRandomRead(),
            testRandomWrite()
        )
    }

    private fun testTotalStorage(): TestResult {
        val stat = StatFs(Environment.getDataDirectory().path)
        val totalGb = stat.totalBytes / (1024.0 * 1024 * 1024)
        val nameAr = context.getString(net.neuralcheck.R.string.test_s01_ar)
        val nameTr = context.getString(net.neuralcheck.R.string.test_s01_tr)
        return TestResult("S-01", nameAr, nameTr, "storage", TestStatus.PASS, null, String.format(Locale.US, "%.1f", totalGb), "GB", null)
    }

    private fun testAvailableStorage(): TestResult {
        val stat = StatFs(Environment.getDataDirectory().path)
        val availGb = stat.availableBytes / (1024.0 * 1024 * 1024)
        val totalBytes = stat.totalBytes
        val pct = if (totalBytes > 0) {
            (stat.availableBytes.toDouble() / totalBytes * 100).toFloat()
        } else 0f
        val status = when {
            pct > 20f -> TestStatus.PASS
            pct > 10f -> TestStatus.PASS
            pct > 5f -> TestStatus.WARNING
            else -> TestStatus.FAIL
        }
        val nameAr = context.getString(net.neuralcheck.R.string.test_s02_ar)
        val nameTr = context.getString(net.neuralcheck.R.string.test_s02_tr)
        // rawValue is the percent-free so the scoring engine can interpolate
        // against scoring_config.json#S-02 ("percent_of_total").
        return TestResult("S-02", nameAr, nameTr, "storage", status, null,
            String.format(Locale.US, "%.1f", pct), "% (${String.format(Locale.US, "%.1f", availGb)} GB)", null)
    }

    private fun testSequentialWrite(): TestResult {
        val tempFile = File(context.cacheDir, "nc_seq_write.tmp")
        val nameAr = context.getString(net.neuralcheck.R.string.test_s03_ar)
        val nameTr = context.getString(net.neuralcheck.R.string.test_s03_tr)
        try {
            val data = ByteArray(1024 * 1024) { Random.nextInt().toByte() } // 1MB chunk
            val start = System.nanoTime()
            FileOutputStream(tempFile).use { fos ->
                val channel = fos.channel
                repeat((SEQ_FILE_SIZE / data.size).toInt()) {
                    fos.write(data)
                }
                channel.force(true)
            }
            val elapsed = (System.nanoTime() - start) / 1_000_000_000.0
            val speedMbps = SEQ_FILE_SIZE / (1024.0 * 1024) / elapsed
            val status = when {
                speedMbps > 100 -> TestStatus.PASS
                speedMbps > 50 -> TestStatus.WARNING
                else -> TestStatus.FAIL
            }
            return TestResult("S-03", nameAr, nameTr, "storage", status, null, String.format(Locale.US, "%.0f", speedMbps), "MB/s", null)
        } catch (e: Exception) {
            return TestResult("S-03", nameAr, nameTr, "storage", TestStatus.ERROR, null, e.message ?: "error", "", null)
        } finally {
            tempFile.delete()
        }
    }

    private fun testSequentialRead(): TestResult {
        val tempFile = File(context.cacheDir, "nc_seq_read.tmp")
        val nameAr = context.getString(net.neuralcheck.R.string.test_s04_ar)
        val nameTr = context.getString(net.neuralcheck.R.string.test_s04_tr)
        try {
            // Write file first
            FileOutputStream(tempFile).use { fos ->
                val data = ByteArray(1024 * 1024)
                repeat((SEQ_FILE_SIZE / data.size).toInt()) { fos.write(data) }
                fos.channel.force(true)
            }
            // Clear cache by writing extra data
            val cacheFile = File(context.cacheDir, "nc_cache_clear.tmp")
            try {
                FileOutputStream(cacheFile).use { fos ->
                    val zeros = ByteArray(1024 * 1024)
                    repeat(128) { fos.write(zeros) }
                }
            } catch (e: Exception) { /* best effort */ }
            cacheFile.delete()

            val buf = ByteArray(1024 * 1024)
            val start = System.nanoTime()
            tempFile.inputStream().buffered().use { fis ->
                while (fis.read(buf) > 0) { /* read */ }
            }
            val elapsed = (System.nanoTime() - start) / 1_000_000_000.0
            val speedMbps = SEQ_FILE_SIZE / (1024.0 * 1024) / elapsed
            val status = when {
                speedMbps > 200 -> TestStatus.PASS
                speedMbps > 100 -> TestStatus.WARNING
                else -> TestStatus.FAIL
            }
            return TestResult("S-04", nameAr, nameTr, "storage", status, null, String.format(Locale.US, "%.0f", speedMbps), "MB/s", null)
        } catch (e: Exception) {
            return TestResult("S-04", nameAr, nameTr, "storage", TestStatus.ERROR, null, e.message ?: "error", "", null)
        } finally {
            tempFile.delete()
        }
    }

    private fun testRandomRead(): TestResult {
        val tempFile = File(context.cacheDir, "nc_rand.tmp")
        val nameAr = context.getString(net.neuralcheck.R.string.test_s05_ar)
        val nameTr = context.getString(net.neuralcheck.R.string.test_s05_tr)
        try {
            // Create test file
            FileOutputStream(tempFile).use { fos ->
                val data = ByteArray(1024 * 1024)
                repeat(16) { fos.write(data) } // 16MB
            }
            val fileSize = tempFile.length()
            val rand = Random(42) // Fixed seed
            val buf = ByteArray(RANDOM_BLOCK)
            val raf = RandomAccessFile(tempFile, "r")
            val start = System.nanoTime()
            repeat(RANDOM_OPS) {
                val pos = rand.nextLong(fileSize - RANDOM_BLOCK)
                raf.seek(pos)
                raf.readFully(buf)
            }
            raf.close()
            val elapsed = (System.nanoTime() - start) / 1_000_000_000.0
            val iops = RANDOM_OPS / elapsed
            val status = when {
                iops > 5000 -> TestStatus.PASS
                iops > 2000 -> TestStatus.WARNING
                else -> TestStatus.FAIL
            }
            return TestResult("S-05", nameAr, nameTr, "storage", status, null, String.format(Locale.US, "%.0f", iops), "IOPS", null)
        } catch (e: Exception) {
            return TestResult("S-05", nameAr, nameTr, "storage", TestStatus.ERROR, null, e.message ?: "error", "", null)
        } finally {
            tempFile.delete()
        }
    }

    private fun testRandomWrite(): TestResult {
        val tempFile = File(context.cacheDir, "nc_rand_w.tmp")
        val nameAr = context.getString(net.neuralcheck.R.string.test_s06_ar)
        val nameTr = context.getString(net.neuralcheck.R.string.test_s06_tr)
        try {
            FileOutputStream(tempFile).use { fos ->
                val data = ByteArray(1024 * 1024)
                repeat(16) { fos.write(data) }
            }
            val fileSize = tempFile.length()
            val rand = Random(42)
            val buf = ByteArray(RANDOM_BLOCK) { rand.nextInt().toByte() }
            val raf = RandomAccessFile(tempFile, "rw")
            val start = System.nanoTime()
            repeat(RANDOM_OPS) { i ->
                val pos = rand.nextLong(fileSize - RANDOM_BLOCK)
                raf.seek(pos)
                raf.write(buf)
                if (i % 100 == 99) raf.fd.sync()
            }
            raf.close()
            val elapsed = (System.nanoTime() - start) / 1_000_000_000.0
            val iops = RANDOM_OPS / elapsed
            val status = when {
                iops > 3000 -> TestStatus.PASS
                iops > 1000 -> TestStatus.WARNING
                else -> TestStatus.FAIL
            }
            return TestResult("S-06", nameAr, nameTr, "storage", status, null, String.format(Locale.US, "%.0f", iops), "IOPS", null)
        } catch (e: Exception) {
            return TestResult("S-06", nameAr, nameTr, "storage", TestStatus.ERROR, null, e.message ?: "error", "", null)
        } finally {
            tempFile.delete()
        }
    }
}
