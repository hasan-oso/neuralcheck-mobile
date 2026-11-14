package net.neuralcheck.data.local.db

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
 * Order 2026-09-24 (القرار ٧٦/٧٧). Proves `NeuralCheckDatabase.MIGRATION_5_6`
 * (scan_scores.overallScore Float NOT NULL -> nullable) preserves every
 * saved report row — "لا ترحيل هادم" — WITHOUT building an APK or
 * installing on a device (both explicitly out of scope right now; the
 * order's own text: "واختبار الترقية على جهاز حقيقي بعد البناء يُجريه
 * غيرك").
 *
 * Why raw JDBC SQLite (`org.xerial:sqlite-jdbc`), not Room's own
 * `androidx.room:room-testing` `MigrationTestHelper`: when written this module did not
 * have Robolectric (a test dependency since 3284cca), and `MigrationTestHelper` needs a real Android SQLite
 * implementation, normally exercised through an instrumented
 * (device/emulator) test — exactly what's blocked right now. This test
 * instead executes the EXACT SAME SQL statements the real `Migration(5, 6)`
 * object runs (`MIGRATION_5_6_SQL` — a shared list constant in
 * `NeuralCheckDatabase.kt`, not a hand-retyped copy of them here) against a
 * real (not mocked) SQLite engine on the plain JVM.
 *
 * "بتقارير محفوظة" — the seeded "before" table models real saved-report
 * shapes this app actually writes, not synthetic placeholder rows:
 *   - a v1 row from BEFORE shortId/aiAnalysisJson existed (both NULL — the
 *     exact shape MIGRATION_1_2/MIGRATION_3_4's own DEFAULT NULL columns
 *     produce for a pre-existing row)
 *   - a fully-populated modern row (real shortId + aiAnalysisJson)
 *   - a row with isIncomplete=true (Boolean/INTEGER 0-or-1 edge case)
 *
 * Schema baseline: the CURRENT (pre-migration) `scan_scores` DDL below is
 * inferred from `ScanScoreEntity.kt` + Room's standard column-order/type
 * conventions (TEXT/REAL/INTEGER), cross-checked against
 * `git show be59137:app/src/main/java/net/neuralcheck/data/local/db/entity/ScanScoreEntity.kt`
 * (be59137 = commit nearest the distributed versionCode-10 APK by date,
 * 2026-06-09 — see this task's report) — byte-identical to this table's
 * shape at HEAD before this migration, so no drift between "what shipped"
 * and "what this test seeds". No exported Room schema JSON exists in this
 * repo to confirm the exact DDL more directly (order 2026-09-24، القرار
 * ٧٧: "الاستنتاج بالتاريخ" is the explicitly authorized substitute this
 * round, replacing the prior "توقّف وأبلغ" instruction).
 */
class ScanScoresMigration5To6JvmTest {

    private lateinit var connection: Connection
    private lateinit var dbFile: File

    private val oldScanScoresDdl = """
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

    private val scansDdl = """
        CREATE TABLE `scans` (
            `scanId` TEXT NOT NULL PRIMARY KEY
        )
    """.trimIndent()

    @Before
    fun setUp() {
        dbFile = File.createTempFile("scan_scores_migration_5_6", ".db")
        connection = DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}")
        connection.createStatement().use { it.execute("PRAGMA foreign_keys = ON") }
        connection.createStatement().use { it.execute(scansDdl) }
        connection.createStatement().use { it.execute(oldScanScoresDdl) }
        seedSavedReports()
    }

    @After
    fun tearDown() {
        connection.close()
        dbFile.delete()
    }

    private fun seedSavedReports() {
        connection.createStatement().use { stmt ->
            // Parent rows the FK requires.
            stmt.execute("INSERT INTO scans (scanId) VALUES ('scan-legacy-v1')")
            stmt.execute("INSERT INTO scans (scanId) VALUES ('scan-modern-full')")
            stmt.execute("INSERT INTO scans (scanId) VALUES ('scan-incomplete')")
        }
        connection.prepareStatement(
            "INSERT INTO scan_scores (scanId, overallScore, overallGrade, axisScoresJson, " +
                "categoryScoresJson, criticalFailuresJson, antiTamperPenalty, isIncomplete, " +
                "scoringMethod, calculatedAt, shortId, aiAnalysisJson) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
        ).use { ps ->
            // Row 1: legacy, pre-shortId/aiAnalysisJson (both NULL) — the
            // real shape MIGRATION_1_2/MIGRATION_3_4 leave existing rows in.
            ps.setString(1, "scan-legacy-v1")
            ps.setDouble(2, 72.5)
            ps.setString(3, "B")
            ps.setString(4, "{}")
            ps.setString(5, "{}")
            ps.setString(6, "[]")
            ps.setDouble(7, 0.0)
            ps.setInt(8, 0)
            ps.setString(9, "rule_based_v1")
            ps.setLong(10, 1717000000000L)
            ps.setString(11, null)
            ps.setString(12, null)
            ps.addBatch()

            // Row 2: modern, fully populated.
            ps.setString(1, "scan-modern-full")
            ps.setDouble(2, 91.0)
            ps.setString(3, "A")
            ps.setString(4, "{\"cpu\":1}")
            ps.setString(5, "{\"battery\":2}")
            ps.setString(6, "[]")
            ps.setDouble(7, 5.0)
            ps.setInt(8, 0)
            ps.setString(9, "ai_v1")
            ps.setLong(10, 1758000000000L)
            ps.setString(11, "NC-A7K9M2")
            ps.setString(12, "{\"pipeline\":\"v6_raw_timeseries\"}")
            ps.addBatch()

            // Row 3: incomplete scan (isIncomplete = 1 — Boolean/INTEGER edge case).
            ps.setString(1, "scan-incomplete")
            ps.setDouble(2, 40.0)
            ps.setString(3, "D")
            ps.setString(4, "{}")
            ps.setString(5, "{}")
            ps.setString(6, "[\"T-03\"]")
            ps.setDouble(7, 0.0)
            ps.setInt(8, 1)
            ps.setString(9, "rule_based_v1")
            ps.setLong(10, 1758500000000L)
            ps.setString(11, null)
            ps.setString(12, null)
            ps.addBatch()

            ps.executeBatch()
        }
    }

    private data class Row(
        val scanId: String,
        val overallScore: Double?,
        val overallGrade: String,
        val axisScoresJson: String,
        val categoryScoresJson: String,
        val criticalFailuresJson: String,
        val antiTamperPenalty: Double,
        val isIncomplete: Int,
        val scoringMethod: String,
        val calculatedAt: Long,
        val shortId: String?,
        val aiAnalysisJson: String?
    )

    private fun readAllRows(): Map<String, Row> {
        val rows = mutableMapOf<String, Row>()
        connection.createStatement().use { stmt ->
            val rs = stmt.executeQuery(
                "SELECT scanId, overallScore, overallGrade, axisScoresJson, categoryScoresJson, " +
                    "criticalFailuresJson, antiTamperPenalty, isIncomplete, scoringMethod, " +
                    "calculatedAt, shortId, aiAnalysisJson FROM scan_scores"
            )
            while (rs.next()) {
                val scanId = rs.getString("scanId")
                rows[scanId] = Row(
                    scanId = scanId,
                    overallScore = rs.getDouble("overallScore").let { if (rs.wasNull()) null else it },
                    overallGrade = rs.getString("overallGrade"),
                    axisScoresJson = rs.getString("axisScoresJson"),
                    categoryScoresJson = rs.getString("categoryScoresJson"),
                    criticalFailuresJson = rs.getString("criticalFailuresJson"),
                    antiTamperPenalty = rs.getDouble("antiTamperPenalty"),
                    isIncomplete = rs.getInt("isIncomplete"),
                    scoringMethod = rs.getString("scoringMethod"),
                    calculatedAt = rs.getLong("calculatedAt"),
                    shortId = rs.getString("shortId"),
                    aiAnalysisJson = rs.getString("aiAnalysisJson")
                )
            }
        }
        return rows
    }

    @Test
    fun `MIGRATION_5_6 preserves every saved report row exactly, then accepts a null overallScore`() {
        val before = readAllRows()
        assertEquals("seed must have written all 3 rows", 3, before.size)

        MIGRATION_5_6_SQL.forEach { sql ->
            connection.createStatement().use { it.execute(sql) }
        }

        val after = readAllRows()
        assertEquals("no row must be lost or added by the migration", before.keys, after.keys)
        for (scanId in before.keys) {
            assertEquals(
                "row $scanId must be byte-for-byte identical after the migration — a destructive migration would show up here as a diff, not just a missing row",
                before.getValue(scanId),
                after.getValue(scanId)
            )
        }

        // The actual schema change: overallScore now accepts NULL — the
        // exact V7 shape (a report with no score) this whole order exists
        // for. Confirms the RELAXED constraint, not just that data
        // survived under the OLD (still NOT NULL) one.
        connection.prepareStatement(
            "INSERT INTO scan_scores (scanId, overallScore, overallGrade, axisScoresJson, " +
                "categoryScoresJson, criticalFailuresJson, antiTamperPenalty, isIncomplete, " +
                "scoringMethod, calculatedAt, shortId, aiAnalysisJson) VALUES (?, NULL, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
        ).use { ps ->
            connection.createStatement().use { it.execute("INSERT INTO scans (scanId) VALUES ('scan-v7-no-score')") }
            ps.setString(1, "scan-v7-no-score")
            ps.setString(2, "C")
            ps.setString(3, "{}")
            ps.setString(4, "{}")
            ps.setString(5, "[]")
            ps.setDouble(6, 0.0)
            ps.setInt(7, 0)
            ps.setString(8, "ai_v1")
            ps.setLong(9, 1758600000000L)
            ps.setString(10, "NC-V7TEST")
            ps.setString(11, "{\"pipeline\":\"v7_findings_based\"}")
            ps.executeUpdate()
        }
        val v7Row = readAllRows().getValue("scan-v7-no-score")
        assertNull("a V7-shaped row must be able to persist overallScore = NULL after the migration", v7Row.overallScore)

        // FK still enforced after the recreate — proves the migration
        // didn't quietly drop the constraint along with the table.
        var fkRejected = false
        try {
            connection.createStatement().use {
                it.execute(
                    "INSERT INTO scan_scores (scanId, overallScore, overallGrade, axisScoresJson, " +
                        "categoryScoresJson, criticalFailuresJson, antiTamperPenalty, isIncomplete, " +
                        "scoringMethod, calculatedAt) VALUES ('no-such-scan', 1.0, 'A', '{}', '{}', '[]', 0.0, 0, 'ai_v1', 0)"
                )
            }
        } catch (e: java.sql.SQLException) {
            fkRejected = true
        }
        assertTrue("FOREIGN KEY(scanId) REFERENCES scans must survive the recreate", fkRejected)
    }
}
