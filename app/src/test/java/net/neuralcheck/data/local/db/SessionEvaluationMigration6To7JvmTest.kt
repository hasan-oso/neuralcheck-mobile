package net.neuralcheck.data.local.db

import net.neuralcheck.data.local.db.NeuralCheckDatabase.Companion.MIGRATION_6_7_SQL
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.sql.Connection
import java.sql.DriverManager

/**
 * Order 2026-09-30 («أمر — التقييم (ب)، ومهلة الجلسة، والبنود المعلّقة»،
 * البند ١): "اذكر رقم الترحيل واختبره" — MIGRATION_6_7 adds
 * session_evaluations.finalFailure. Same technique as
 * [RoomMigration4To6JvmTest]/[ScanScoresMigration5To6JvmTest] (real SQLite
 * via plain JDBC — when written this module lacked Robolectric (a test dependency
 * since 3284cca), and `androidx.room:room-testing`'s `MigrationTestHelper` is still not one).
 *
 * `MIGRATION_6_7_SQL` is the exact shared list constant the real
 * `Migration(6, 7)` object executes (NeuralCheckDatabase.kt) — this test
 * runs that same statement, not a hand-retyped copy of it.
 *
 * This does NOT exercise Room's actual generated DAO/query code (this test does not
 * use Robolectric) — it proves the raw SQL migration is additive and that the
 * new column accepts the values SessionEvaluationEntity.finalFailure needs,
 * nothing about SessionEvaluationDao's Kotlin-level behaviour (that is
 * covered separately, with a mocked DAO, in EvaluationSubmitterOrderingTest).
 */
class SessionEvaluationMigration6To7JvmTest {

    private lateinit var connection: Connection
    private lateinit var dbFile: File

    // v6 shape (post-MIGRATION_4_5, pre-MIGRATION_6_7) — no finalFailure column.
    private val v6SessionEvaluationsDdl = """
        CREATE TABLE session_evaluations (
            scanId TEXT NOT NULL PRIMARY KEY,
            sessionId TEXT NOT NULL,
            technicianGradeRaw TEXT DEFAULT NULL,
            categoryRatingsJson TEXT NOT NULL DEFAULT '{}',
            visibleDefectsJson TEXT NOT NULL DEFAULT '[]',
            shownAtMs INTEGER NOT NULL,
            lastAnswerAtMs INTEGER DEFAULT NULL,
            submitted INTEGER NOT NULL DEFAULT 0
        )
    """.trimIndent()

    @Before
    fun setUp() {
        dbFile = File.createTempFile("session_evaluation_migration_6_to_7", ".db")
        connection = DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}")
        connection.createStatement().use { it.execute(v6SessionEvaluationsDdl) }
    }

    @After
    fun tearDown() {
        connection.close()
        dbFile.delete()
    }

    private fun insertV6Row(scanId: String, submitted: Int) {
        connection.prepareStatement(
            "INSERT INTO session_evaluations (scanId, sessionId, technicianGradeRaw, " +
                "categoryRatingsJson, visibleDefectsJson, shownAtMs, lastAnswerAtMs, submitted) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
        ).use { ps ->
            ps.setString(1, scanId)
            ps.setString(2, "session-xyz")
            ps.setString(3, "good")
            ps.setString(4, "{\"battery\":\"good\"}")
            ps.setString(5, "[]")
            ps.setLong(6, 1_717_000_000_000L)
            ps.setLong(7, 1_717_000_050_000L)
            ps.setInt(8, submitted)
            ps.executeUpdate()
        }
    }

    private fun readFinalFailure(scanId: String): Int {
        connection.prepareStatement(
            "SELECT finalFailure FROM session_evaluations WHERE scanId = ?"
        ).use { ps ->
            ps.setString(1, scanId)
            val rs = ps.executeQuery()
            assertTrue("expected a row for $scanId", rs.next())
            return rs.getInt("finalFailure")
        }
    }

    @Test
    fun `MIGRATION_6_7 is additive - pre-existing rows survive and default finalFailure to 0`() {
        insertV6Row("scan-pending", submitted = 0)
        insertV6Row("scan-already-sent", submitted = 1)

        MIGRATION_6_7_SQL.forEach { sql -> connection.createStatement().use { it.execute(sql) } }

        assertEquals(0, readFinalFailure("scan-pending"))
        assertEquals(0, readFinalFailure("scan-already-sent"))
        // submitted must be untouched by a purely additive column add.
        connection.createStatement().use { stmt ->
            val rs = stmt.executeQuery("SELECT scanId, submitted FROM session_evaluations ORDER BY scanId")
            assertTrue(rs.next())
            assertEquals("scan-already-sent", rs.getString("scanId"))
            assertEquals(1, rs.getInt("submitted"))
            assertTrue(rs.next())
            assertEquals("scan-pending", rs.getString("scanId"))
            assertEquals(0, rs.getInt("submitted"))
        }
    }

    @Test
    fun `after MIGRATION_6_7 a row can be written with finalFailure = 1`() {
        MIGRATION_6_7_SQL.forEach { sql -> connection.createStatement().use { it.execute(sql) } }
        insertV6Row("scan-will-mark-final", submitted = 0)

        connection.prepareStatement(
            "UPDATE session_evaluations SET finalFailure = 1 WHERE scanId = ?"
        ).use { ps ->
            ps.setString(1, "scan-will-mark-final")
            assertEquals(1, ps.executeUpdate())
        }

        assertEquals(1, readFinalFailure("scan-will-mark-final"))
    }

    @Test
    fun `getPending's exclusion mirrors the DAO query text - excludes submitted or finalFailure rows`() {
        // Mirrors SessionEvaluationDao.getPending()'s exact @Query text
        // ("SELECT * FROM session_evaluations WHERE submitted = 0 AND
        // finalFailure = 0 ORDER BY shownAtMs ASC") against real SQLite —
        // it does NOT execute that Room-generated method itself (this test does
        // not use Robolectric), so a typo in the annotation string
        // that this literal copy doesn't share would not be caught here.
        MIGRATION_6_7_SQL.forEach { sql -> connection.createStatement().use { it.execute(sql) } }
        insertV6Row("scan-truly-pending", submitted = 0)
        insertV6Row("scan-already-sent", submitted = 1)
        insertV6Row("scan-final-rejection", submitted = 0)
        connection.prepareStatement("UPDATE session_evaluations SET finalFailure = 1 WHERE scanId = ?").use {
            it.setString(1, "scan-final-rejection")
            it.executeUpdate()
        }

        val pendingIds = mutableListOf<String>()
        connection.createStatement().use { stmt ->
            val rs = stmt.executeQuery(
                "SELECT scanId FROM session_evaluations WHERE submitted = 0 AND finalFailure = 0 ORDER BY shownAtMs ASC"
            )
            while (rs.next()) pendingIds.add(rs.getString("scanId"))
        }

        assertEquals(listOf("scan-truly-pending"), pendingIds)
    }
}
