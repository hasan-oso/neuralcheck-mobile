package net.neuralcheck.data.local.db

import net.neuralcheck.data.local.db.NeuralCheckDatabase.Companion.MIGRATION_4_5_SQL
import net.neuralcheck.data.local.db.NeuralCheckDatabase.Companion.MIGRATION_5_6_SQL
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.sql.Connection
import java.sql.DriverManager

/**
 * Order 2026-09-24 (القرار ٨٢ البند ٧): "اختبار للترحيل MIGRATION_4_5،
 * واختبار للسلسلة من الإصدار 4 إلى 6، على قاعدة فيها تقارير محفوظة."
 * Same technique as [ScanScoresMigration5To6JvmTest] (real SQLite JDBC on
 * the plain JVM — when written this module lacked Robolectric (a test
 * dependency since 3284cca), and `androidx.room:room-testing`'s
 * `MigrationTestHelper` still isn't one)
 * and the SAME kind of gap that test closed for 5⇐6: before this file,
 * `MIGRATION_1_2`/`MIGRATION_2_3`/`MIGRATION_3_4`/`MIGRATION_4_5` had NO
 * test at all — a device distributed at `be59137` (v4) walks
 * `MIGRATION_4_5` then `MIGRATION_5_6` to reach HEAD (v6), and only the
 * second leg was ever exercised.
 *
 * `MIGRATION_4_5_SQL` is a shared list constant (extracted from
 * `NeuralCheckDatabase.kt` in this same round, mirroring `MIGRATION_5_6_SQL`'s
 * existing pattern) so this test runs the EXACT SAME statement the real
 * `Migration(4, 5)` object runs, not a hand-retyped copy.
 *
 * `scans`/`scan_scores`/`functional_results` DDL at v4: `scans` includes
 * `userId` (`MIGRATION_2_3`) since v4 is AFTER that migration;
 * `scan_scores` is `oldScanScoresDdl`'s exact shape from
 * `ScanScoresMigration5To6JvmTest` (`shortId`/`aiAnalysisJson` present,
 * `overallScore` still `NOT NULL` — v4 is before `MIGRATION_5_6`).
 * `functional_results`/`technician_inputs`/`peer_inputs`/`ScanEntity`'s own
 * shape are confirmed byte-identical between `be59137` (v4) and HEAD by
 * `diff` on the entity source files (this task's report) — no migration
 * ever touches `functional_results`, so its v4 shape is its v1 shape.
 */
class RoomMigration4To6JvmTest {

    private lateinit var connection: Connection
    private lateinit var dbFile: File

    private val scansDdl = """
        CREATE TABLE `scans` (
            `scanId` TEXT NOT NULL,
            `deviceFingerprint` TEXT NOT NULL,
            `scanType` TEXT NOT NULL,
            `linkedScanId` TEXT,
            `timestamp` INTEGER NOT NULL,
            `batteryLevelStart` INTEGER NOT NULL,
            `batteryLevelEnd` INTEGER,
            `ambientTempEstimate` REAL,
            `appVersion` TEXT NOT NULL,
            `androidApiLevel` INTEGER NOT NULL,
            `deviceModel` TEXT NOT NULL,
            `androidVersion` TEXT NOT NULL,
            `isComplete` INTEGER NOT NULL,
            `earlyStopReason` TEXT,
            `earlyStopAtMs` INTEGER,
            `timeSeriesFilePath` TEXT,
            `isSynced` INTEGER NOT NULL DEFAULT 0,
            `userId` TEXT DEFAULT NULL,
            PRIMARY KEY(`scanId`)
        )
    """.trimIndent()

    // Identical to ScanScoresMigration5To6JvmTest.oldScanScoresDdl — the v4
    // (pre-5_6) shape: shortId/aiAnalysisJson present, overallScore still
    // NOT NULL.
    private val v4ScanScoresDdl = """
        CREATE TABLE `scan_scores` (
            `scanId` TEXT NOT NULL,
            `overallScore` REAL NOT NULL,
            `overallGrade` TEXT NOT NULL,
            `axisScoresJson` TEXT NOT NULL,
            `categoryScoresJson` TEXT NOT NULL,
            `criticalFailuresJson` TEXT NOT NULL,
            `antiTamperPenalty` REAL NOT NULL,
            `isIncomplete` INTEGER NOT NULL,
            `scoringMethod` TEXT NOT NULL,
            `calculatedAt` INTEGER NOT NULL,
            `shortId` TEXT,
            `aiAnalysisJson` TEXT,
            PRIMARY KEY(`scanId`),
            FOREIGN KEY(`scanId`) REFERENCES `scans`(`scanId`) ON DELETE CASCADE
        )
    """.trimIndent()

    private val functionalResultsDdl = """
        CREATE TABLE `functional_results` (
            `resultId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `scanId` TEXT NOT NULL,
            `testId` TEXT NOT NULL,
            `componentName` TEXT NOT NULL,
            `categoryName` TEXT NOT NULL,
            `status` TEXT NOT NULL,
            `score` REAL,
            `rawValue` TEXT,
            `rawValueUnit` TEXT,
            `notes` TEXT,
            `timestamp` INTEGER NOT NULL,
            FOREIGN KEY(`scanId`) REFERENCES `scans`(`scanId`) ON DELETE CASCADE
        )
    """.trimIndent()

    @Before
    fun setUp() {
        dbFile = File.createTempFile("room_migration_4_to_6", ".db")
        connection = DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}")
        connection.createStatement().use { it.execute("PRAGMA foreign_keys = ON") }
        connection.createStatement().use { it.execute(scansDdl) }
        connection.createStatement().use { it.execute(v4ScanScoresDdl) }
        connection.createStatement().use { it.execute(functionalResultsDdl) }
        seedSavedReports()
    }

    @After
    fun tearDown() {
        connection.close()
        dbFile.delete()
    }

    /** Two "devices with saved reports" — one signed-in (userId set), one
     *  anonymous/legacy (userId NULL, the MIGRATION_2_3 default) — each
     *  with a scan_scores row and two functional_results rows. */
    private fun seedSavedReports() {
        connection.prepareStatement(
            "INSERT INTO scans (scanId, deviceFingerprint, scanType, linkedScanId, timestamp, " +
                "batteryLevelStart, batteryLevelEnd, ambientTempEstimate, appVersion, androidApiLevel, " +
                "deviceModel, androidVersion, isComplete, earlyStopReason, earlyStopAtMs, " +
                "timeSeriesFilePath, isSynced, userId) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"
        ).use { ps ->
            ps.setString(1, "scan-signed-in")
            ps.setString(2, "fp-aaa")
            ps.setString(3, "general")
            ps.setString(4, null)
            ps.setLong(5, 1717000000000L)
            ps.setInt(6, 90)
            ps.setInt(7, 70)
            ps.setFloat(8, 25.5f)
            ps.setString(9, "1.11.0")
            ps.setInt(10, 33)
            ps.setString(11, "SM-G991B")
            ps.setString(12, "13")
            ps.setInt(13, 1)
            ps.setString(14, null)
            ps.setString(15, null)
            ps.setString(16, "scans/scan-signed-in.ts.json.gz")
            ps.setInt(17, 1)
            ps.setString(18, "uid-123")
            ps.addBatch()

            ps.setString(1, "scan-anonymous")
            ps.setString(2, "fp-bbb")
            ps.setString(3, "pre_repair")
            ps.setString(4, null)
            ps.setLong(5, 1717100000000L)
            ps.setInt(6, 55)
            ps.setInt(7, 40)
            ps.setFloat(8, 30.0f)
            ps.setString(9, "1.11.0")
            ps.setInt(10, 30)
            ps.setString(11, "Redmi Note 12")
            ps.setString(12, "12")
            ps.setInt(13, 0)
            ps.setString(14, "battery_temp_critical")
            ps.setLong(15, 1717100500000L)
            ps.setString(16, null)
            ps.setInt(17, 0)
            ps.setString(18, null)
            ps.addBatch()
            ps.executeBatch()
        }

        connection.prepareStatement(
            "INSERT INTO scan_scores (scanId, overallScore, overallGrade, axisScoresJson, " +
                "categoryScoresJson, criticalFailuresJson, antiTamperPenalty, isIncomplete, " +
                "scoringMethod, calculatedAt, shortId, aiAnalysisJson) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)"
        ).use { ps ->
            ps.setString(1, "scan-signed-in")
            ps.setDouble(2, 88.0)
            ps.setString(3, "A")
            ps.setString(4, "{}")
            ps.setString(5, "{}")
            ps.setString(6, "[]")
            ps.setDouble(7, 0.0)
            ps.setInt(8, 0)
            ps.setString(9, "ai_v1")
            ps.setLong(10, 1717000100000L)
            ps.setString(11, "NC-SIGNEDIN")
            ps.setString(12, "{\"pipeline\":\"v7_findings_based\"}")
            ps.addBatch()

            ps.setString(1, "scan-anonymous")
            ps.setDouble(2, 41.0)
            ps.setString(3, "D")
            ps.setString(4, "{}")
            ps.setString(5, "{}")
            ps.setString(6, "[\"T-03\"]")
            ps.setDouble(7, 0.0)
            ps.setInt(8, 1)
            ps.setString(9, "rule_based_v1")
            ps.setLong(10, 1717100600000L)
            ps.setString(11, null)
            ps.setString(12, null)
            ps.addBatch()
            ps.executeBatch()
        }

        connection.prepareStatement(
            "INSERT INTO functional_results (scanId, testId, componentName, categoryName, status, " +
                "score, rawValue, rawValueUnit, notes, timestamp) VALUES (?,?,?,?,?,?,?,?,?,?)"
        ).use { ps ->
            ps.setString(1, "scan-signed-in")
            ps.setString(2, "B-01")
            ps.setString(3, "Battery Health")
            ps.setString(4, "battery")
            ps.setString(5, "pass")
            ps.setFloat(6, 95f)
            ps.setString(7, "95")
            ps.setString(8, "%")
            ps.setString(9, null)
            ps.setLong(10, 1717000050000L)
            ps.addBatch()

            ps.setString(1, "scan-anonymous")
            ps.setString(2, "T-03")
            ps.setString(3, "Thermal Throttle")
            ps.setString(4, "thermal")
            ps.setString(5, "fail")
            ps.setNull(6, java.sql.Types.REAL)
            ps.setString(7, null)
            ps.setString(8, null)
            ps.setString(9, "early-stopped")
            ps.setLong(10, 1717100400000L)
            ps.addBatch()
            ps.executeBatch()
        }
    }

    private data class ScanRow(
        val scanId: String, val deviceFingerprint: String, val scanType: String,
        val timestamp: Long, val batteryLevelStart: Int, val isComplete: Int,
        val earlyStopReason: String?, val userId: String?
    )

    private data class ScanScoreRow(
        val scanId: String, val overallScore: Double?, val overallGrade: String,
        val shortId: String?, val aiAnalysisJson: String?
    )

    private data class FunctionalResultRow(
        val scanId: String, val testId: String, val status: String, val score: Float?
    )

    private fun readScans(): Map<String, ScanRow> {
        val rows = mutableMapOf<String, ScanRow>()
        connection.createStatement().use { stmt ->
            val rs = stmt.executeQuery(
                "SELECT scanId, deviceFingerprint, scanType, timestamp, batteryLevelStart, " +
                    "isComplete, earlyStopReason, userId FROM scans"
            )
            while (rs.next()) {
                val id = rs.getString("scanId")
                rows[id] = ScanRow(
                    scanId = id,
                    deviceFingerprint = rs.getString("deviceFingerprint"),
                    scanType = rs.getString("scanType"),
                    timestamp = rs.getLong("timestamp"),
                    batteryLevelStart = rs.getInt("batteryLevelStart"),
                    isComplete = rs.getInt("isComplete"),
                    earlyStopReason = rs.getString("earlyStopReason"),
                    userId = rs.getString("userId")
                )
            }
        }
        return rows
    }

    private fun readScanScores(): Map<String, ScanScoreRow> {
        val rows = mutableMapOf<String, ScanScoreRow>()
        connection.createStatement().use { stmt ->
            val rs = stmt.executeQuery(
                "SELECT scanId, overallScore, overallGrade, shortId, aiAnalysisJson FROM scan_scores"
            )
            while (rs.next()) {
                val id = rs.getString("scanId")
                rows[id] = ScanScoreRow(
                    scanId = id,
                    overallScore = rs.getDouble("overallScore").let { if (rs.wasNull()) null else it },
                    overallGrade = rs.getString("overallGrade"),
                    shortId = rs.getString("shortId"),
                    aiAnalysisJson = rs.getString("aiAnalysisJson")
                )
            }
        }
        return rows
    }

    private fun readFunctionalResults(): List<FunctionalResultRow> {
        val rows = mutableListOf<FunctionalResultRow>()
        connection.createStatement().use { stmt ->
            val rs = stmt.executeQuery("SELECT scanId, testId, status, score FROM functional_results")
            while (rs.next()) {
                rows.add(
                    FunctionalResultRow(
                        scanId = rs.getString("scanId"),
                        testId = rs.getString("testId"),
                        status = rs.getString("status"),
                        score = rs.getFloat("score").let { if (rs.wasNull()) null else it }
                    )
                )
            }
        }
        return rows
    }

    private fun tableExists(name: String): Boolean {
        connection.createStatement().use { stmt ->
            val rs = stmt.executeQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name='$name'"
            )
            return rs.next()
        }
    }

    @Test
    fun `MIGRATION_4_5 creates session_evaluations and leaves every saved-report row untouched`() {
        val scansBefore = readScans()
        val scoresBefore = readScanScores()
        val resultsBefore = readFunctionalResults()
        assertEquals("seed must have written both scans", 2, scansBefore.size)
        assertEquals("seed must have written both scores", 2, scoresBefore.size)
        assertEquals("seed must have written both functional results", 2, resultsBefore.size)
        assertTrue(
            "session_evaluations must not exist before the migration",
            !tableExists("session_evaluations")
        )

        MIGRATION_4_5_SQL.forEach { sql -> connection.createStatement().use { it.execute(sql) } }

        assertEquals(
            "MIGRATION_4_5 is purely additive — scans must be byte-for-byte identical",
            scansBefore, readScans()
        )
        assertEquals(
            "MIGRATION_4_5 is purely additive — scan_scores must be byte-for-byte identical",
            scoresBefore, readScanScores()
        )
        assertEquals(
            "MIGRATION_4_5 is purely additive — functional_results must be byte-for-byte identical",
            resultsBefore, readFunctionalResults()
        )
        assertTrue("session_evaluations must exist after the migration", tableExists("session_evaluations"))

        // The new table must actually accept a row shaped like
        // SessionEvaluationEntity, not just exist.
        connection.prepareStatement(
            "INSERT INTO session_evaluations (scanId, sessionId, technicianGradeRaw, " +
                "categoryRatingsJson, visibleDefectsJson, shownAtMs, lastAnswerAtMs, submitted) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
        ).use { ps ->
            ps.setString(1, "scan-signed-in")
            ps.setString(2, "session-xyz")
            ps.setString(3, "good")
            ps.setString(4, "{\"battery\":\"good\"}")
            ps.setString(5, "[]")
            ps.setLong(6, 1717000200000L)
            ps.setLong(7, 1717000250000L)
            ps.setInt(8, 1)
            assertEquals(1, ps.executeUpdate())
        }
    }

    @Test
    fun `the full v4-to-v6 chain (MIGRATION_4_5 then MIGRATION_5_6) preserves every saved report row`() {
        val scansBefore = readScans()
        val scoresBefore = readScanScores()
        val resultsBefore = readFunctionalResults()

        MIGRATION_4_5_SQL.forEach { sql -> connection.createStatement().use { it.execute(sql) } }
        MIGRATION_5_6_SQL.forEach { sql -> connection.createStatement().use { it.execute(sql) } }

        assertEquals(
            "the full v4->v6 chain must not lose or alter a single scans row",
            scansBefore, readScans()
        )
        assertEquals(
            "the full v4->v6 chain must not lose or alter a single scan_scores row " +
                "(overallScore/overallGrade/shortId/aiAnalysisJson all survive the recreate)",
            scoresBefore, readScanScores()
        )
        assertEquals(
            "the full v4->v6 chain must not lose or alter a single functional_results row",
            resultsBefore, readFunctionalResults()
        )
        assertTrue("session_evaluations must exist at v6", tableExists("session_evaluations"))

        // v6's own contribution: overallScore now accepts NULL (a V7
        // report with no score) — proven reachable via the FULL chain,
        // not just a standalone 5_6 run.
        connection.createStatement().use {
            it.execute("INSERT INTO scans (scanId, deviceFingerprint, scanType, timestamp, " +
                "batteryLevelStart, appVersion, androidApiLevel, deviceModel, androidVersion, " +
                "isComplete) VALUES ('scan-v7-no-score', 'fp-ccc', 'general', 1717200000000, 80, " +
                "'1.11.0', 34, 'Pixel 8', '14', 1)")
        }
        connection.prepareStatement(
            "INSERT INTO scan_scores (scanId, overallScore, overallGrade, axisScoresJson, " +
                "categoryScoresJson, criticalFailuresJson, antiTamperPenalty, isIncomplete, " +
                "scoringMethod, calculatedAt, shortId, aiAnalysisJson) VALUES (?, NULL, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
        ).use { ps ->
            ps.setString(1, "scan-v7-no-score")
            ps.setString(2, "C")
            ps.setString(3, "{}")
            ps.setString(4, "{}")
            ps.setString(5, "[]")
            ps.setDouble(6, 0.0)
            ps.setInt(7, 0)
            ps.setString(8, "ai_v1")
            ps.setLong(9, 1717200100000L)
            ps.setString(10, "NC-V7CHAIN")
            ps.setString(11, "{\"pipeline\":\"v7_findings_based\"}")
            ps.executeUpdate()
        }
        assertNull(
            "a V7-shaped row must persist overallScore = NULL after the full chain",
            readScanScores().getValue("scan-v7-no-score").overallScore
        )
    }
}
