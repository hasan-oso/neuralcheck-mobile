package net.neuralcheck.data.local.db

import net.neuralcheck.data.local.db.NeuralCheckDatabase.Companion.MIGRATION_7_8_SQL
import net.neuralcheck.data.local.db.converter.MeasurementsJson
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
 * Order 2026-10-08 («V8 الدفعة ٣ الجزء الأوّل»، البند ٣-ج-١): "اختبار ترحيل"
 * for MIGRATION_7_8 — `functional_results.measurements` (S-29 (a), N-16).
 *
 * Same technique as [SessionEvaluationMigration6To7JvmTest]: when written this module lacked
 * Robolectric (a test dependency since 3284cca) and `androidx.room:room-testing` is still not one,
 * so Room's `MigrationTestHelper` is unavailable; the shared
 * `MIGRATION_7_8_SQL` list — the EXACT statements the real `Migration(7, 8)`
 * executes — runs against a real SQLite engine through plain JDBC. What this
 * proves: the migration is additive (every pre-existing row survives with its
 * data), the new column reads NULL on those rows ("the producer supplied
 * none", never a measured zero), and the column round-trips the JSON that
 * [MeasurementsJson] writes. What it does NOT prove: Room's own generated
 * DAO code and its post-migration schema validation (those need an
 * instrumented test) — which is why the order also asks for the
 * 1.0.10 → fv68 upgrade to be checked on a real device in the next device
 * round (docs/FV68_CONTRACT.md §٣ بند 9 ي).
 *
 * The v7 DDL below is hand-typed from FunctionalResultEntity at schema 7
 * (the foreign key to `scans` is omitted: the table `scans` is not under
 * test and JDBC leaves foreign_keys off by default).
 */
class FunctionalResultsMigration7To8JvmTest {

    private lateinit var connection: Connection
    private lateinit var dbFile: File

    private val v7FunctionalResultsDdl = """
        CREATE TABLE functional_results (
            resultId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            scanId TEXT NOT NULL,
            testId TEXT NOT NULL,
            componentName TEXT NOT NULL,
            categoryName TEXT NOT NULL,
            status TEXT NOT NULL,
            score REAL,
            rawValue TEXT,
            rawValueUnit TEXT,
            notes TEXT,
            timestamp INTEGER NOT NULL
        )
    """.trimIndent()

    @Before
    fun setUp() {
        dbFile = File.createTempFile("functional_results_migration_7_to_8", ".db")
        connection = DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}")
        connection.createStatement().use { it.execute(v7FunctionalResultsDdl) }
    }

    @After
    fun tearDown() {
        connection.close()
        dbFile.delete()
    }

    private fun insertV7Row(scanId: String, testId: String, rawValue: String?, score: Double?) {
        connection.prepareStatement(
            "INSERT INTO functional_results (scanId, testId, componentName, categoryName, " +
                "status, score, rawValue, rawValueUnit, notes, timestamp) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
        ).use { ps ->
            ps.setString(1, scanId)
            ps.setString(2, testId)
            ps.setString(3, "component-$testId")
            ps.setString(4, "gps")
            ps.setString(5, "pass")
            if (score == null) ps.setNull(6, java.sql.Types.REAL) else ps.setDouble(6, score)
            if (rawValue == null) ps.setNull(7, java.sql.Types.VARCHAR) else ps.setString(7, rawValue)
            ps.setString(8, "u")
            ps.setNull(9, java.sql.Types.VARCHAR)
            ps.setLong(10, 1_717_000_000_000L)
            ps.executeUpdate()
        }
    }

    private fun applyMigration() {
        MIGRATION_7_8_SQL.forEach { sql -> connection.createStatement().use { it.execute(sql) } }
    }

    private fun readMeasurements(scanId: String, testId: String): String? {
        connection.prepareStatement(
            "SELECT measurements FROM functional_results WHERE scanId = ? AND testId = ?"
        ).use { ps ->
            ps.setString(1, scanId)
            ps.setString(2, testId)
            val rs = ps.executeQuery()
            assertTrue("expected a row for $scanId/$testId", rs.next())
            return rs.getString("measurements")
        }
    }

    @Test
    fun `MIGRATION_7_8 is additive - pre-existing rows keep every column and read measurements as NULL`() {
        insertV7Row("scan-a", "L-02", rawValue = "5 / 12", score = 62.5)
        insertV7Row("scan-a", "SN-04", rawValue = null, score = null)
        insertV7Row("scan-b", "M-02", rawValue = null, score = 100.0)

        applyMigration()

        connection.createStatement().use { stmt ->
            val rs = stmt.executeQuery("SELECT COUNT(*) AS n FROM functional_results")
            assertTrue(rs.next())
            assertEquals("no row may be added or lost by an ALTER TABLE ADD COLUMN", 3, rs.getInt("n"))
        }
        assertNull(readMeasurements("scan-a", "L-02"))
        assertNull(readMeasurements("scan-a", "SN-04"))
        assertNull(readMeasurements("scan-b", "M-02"))

        // The pre-existing columns are untouched.
        connection.prepareStatement(
            "SELECT rawValue, score, categoryName FROM functional_results WHERE scanId = ? AND testId = ?"
        ).use { ps ->
            ps.setString(1, "scan-a")
            ps.setString(2, "L-02")
            val rs = ps.executeQuery()
            assertTrue(rs.next())
            assertEquals("5 / 12", rs.getString("rawValue"))
            assertEquals(62.5, rs.getDouble("score"), 0.0)
            assertEquals("gps", rs.getString("categoryName"))
        }
    }

    @Test
    fun `MIGRATION_7_8 adds exactly one nullable TEXT column named measurements`() {
        val before = columnNames()
        applyMigration()
        val after = columnNames()

        assertEquals(listOf("measurements"), after - before.toSet())
        assertEquals(before.size + 1, after.size)
        connection.createStatement().use { stmt ->
            val rs = stmt.executeQuery("PRAGMA table_info(functional_results)")
            var found = false
            while (rs.next()) {
                if (rs.getString("name") == "measurements") {
                    found = true
                    assertEquals("TEXT", rs.getString("type"))
                    assertEquals("column must be nullable", 0, rs.getInt("notnull"))
                }
            }
            assertTrue(found)
        }
    }

    @Test
    fun `after MIGRATION_7_8 the column round-trips the JSON MeasurementsJson writes and reads`() {
        applyMigration()
        insertV7RowWithMeasurements(
            "scan-new", "L-02",
            mapOf(
                "visibleSatellites" to 9L,
                "usedSatellites" to 4L,
                "timeToFirstFixMs" to 8123L,
                "satelliteStatusCallbacks" to 31L,
                "locationUpdateCount" to 7L,
                "readings" to listOf(mapOf("t" to 1000L, "values" to listOf(0.5, 1.25)))
            )
        )

        val text = readMeasurements("scan-new", "L-02")
        val decoded = MeasurementsJson.decode(text)!!
        assertEquals(9L, decoded["visibleSatellites"])
        assertEquals("integers must stay integers through the column", 31L, decoded["satelliteStatusCallbacks"])
        @Suppress("UNCHECKED_CAST")
        val readings = decoded["readings"] as List<Map<String, Any?>>
        assertEquals(1000L, readings.single()["t"])
        assertEquals(listOf(0.5, 1.25), readings.single()["values"])
    }

    private fun insertV7RowWithMeasurements(scanId: String, testId: String, measurements: Map<String, Any?>) {
        insertV7Row(scanId, testId, rawValue = "x", score = null)
        connection.prepareStatement(
            "UPDATE functional_results SET measurements = ? WHERE scanId = ? AND testId = ?"
        ).use { ps ->
            ps.setString(1, MeasurementsJson.encode(measurements))
            ps.setString(2, scanId)
            ps.setString(3, testId)
            assertEquals(1, ps.executeUpdate())
        }
    }

    private fun columnNames(): List<String> {
        val names = mutableListOf<String>()
        connection.createStatement().use { stmt ->
            val rs = stmt.executeQuery("PRAGMA table_info(functional_results)")
            while (rs.next()) names.add(rs.getString("name"))
        }
        return names
    }
}
