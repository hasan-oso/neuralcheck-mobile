package net.neuralcheck.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import net.neuralcheck.data.local.db.converter.Converters
import net.neuralcheck.data.local.db.dao.FunctionalResultDao
import net.neuralcheck.data.local.db.dao.PeerInputDao
import net.neuralcheck.data.local.db.dao.ScanDao
import net.neuralcheck.data.local.db.dao.ScanScoreDao
import net.neuralcheck.data.local.db.dao.SessionEvaluationDao
import net.neuralcheck.data.local.db.dao.TechnicianInputDao
import net.neuralcheck.data.local.db.entity.FunctionalResultEntity
import net.neuralcheck.data.local.db.entity.PeerInputEntity
import net.neuralcheck.data.local.db.entity.ScanEntity
import net.neuralcheck.data.local.db.entity.ScanScoreEntity
import net.neuralcheck.data.local.db.entity.SessionEvaluationEntity
import net.neuralcheck.data.local.db.entity.TechnicianInputEntity

@Database(
    entities = [
        ScanEntity::class,
        FunctionalResultEntity::class,
        TechnicianInputEntity::class,
        PeerInputEntity::class,
        ScanScoreEntity::class,
        SessionEvaluationEntity::class
    ],
    version = NeuralCheckDatabase.SCHEMA_VERSION,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class NeuralCheckDatabase : RoomDatabase() {
    abstract fun scanDao(): ScanDao
    abstract fun functionalResultDao(): FunctionalResultDao
    abstract fun technicianInputDao(): TechnicianInputDao
    abstract fun peerInputDao(): PeerInputDao
    abstract fun scanScoreDao(): ScanScoreDao
    abstract fun sessionEvaluationDao(): SessionEvaluationDao

    companion object {
        const val DATABASE_NAME = "neuralcheck_db"

        /**
         * The current Room schema version — the single source for the
         * `@Database(version = ...)` above and for the migration-chain test
         * (NeuralCheckDatabaseMigrationChainTest). Bump it together with a new
         * MIGRATION_x_y in [ALL_MIGRATIONS].
         */
        const val SCHEMA_VERSION = 8

        // v1 → v2: add shortId column to scan_scores so the AI-minted
        // human-readable ID survives a report re-open (and is printed on
        // the PDF / QR caption) even when the AI re-run fails. Plain
        // ALTER TABLE — no data backfill needed; existing rows get NULL
        // and the report screen falls back to a Firestore lookup.
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE scan_scores ADD COLUMN shortId TEXT DEFAULT NULL")
            }
        }

        // v2 → v3: add userId column to scans so Home / History can filter
        // queries by the signed-in account. Pre-existing rows get NULL —
        // those scans were started before we plumbed Firebase Auth into the
        // scan-creation path; queries treat NULL as "anonymous / legacy"
        // and surface them only when no signed-in uid filter is applied.
        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE scans ADD COLUMN userId TEXT DEFAULT NULL")
            }
        }

        // v3 → v4: add aiAnalysisJson column to scan_scores so the full AI
        // verdict (summary / details / verdict / issues / root_cause /
        // buyer_advice / category_scores / predicted_problems / category_
        // notes) survives a process restart. Earlier rows get NULL and the
        // ReportViewModel falls back to the live scoring flow on next open
        // until the user re-runs the report, at which point the cache fills.
        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE scan_scores ADD COLUMN aiAnalysisJson TEXT DEFAULT NULL")
            }
        }

        // v4 → v5 (feat/technician-evaluation): new session_evaluations
        // table for the blind technician judgement captured on the scanned
        // device during a QR-session scan. Purely additive — no existing
        // table is touched. Column types mirror SessionEvaluationEntity;
        // Room's expected schema requires NOT NULL to match Kotlin
        // non-null fields exactly.
        //
        // MIGRATION_4_5_SQL extracted the same way as MIGRATION_5_6_SQL
        // below (order 2026-09-24، القرار ٨٠/٨٢ البند ٧) — a shared list,
        // not inlined into migrate() directly, so RoomMigration4To6JvmTest
        // executes the EXACT SAME statement via plain JDBC, not a
        // hand-retyped copy of it.
        val MIGRATION_4_5_SQL: List<String> = listOf(
            "CREATE TABLE IF NOT EXISTS session_evaluations (" +
                "scanId TEXT NOT NULL PRIMARY KEY, " +
                "sessionId TEXT NOT NULL, " +
                "technicianGradeRaw TEXT DEFAULT NULL, " +
                "categoryRatingsJson TEXT NOT NULL DEFAULT '{}', " +
                "visibleDefectsJson TEXT NOT NULL DEFAULT '[]', " +
                "shownAtMs INTEGER NOT NULL, " +
                "lastAnswerAtMs INTEGER DEFAULT NULL, " +
                "submitted INTEGER NOT NULL DEFAULT 0)"
        )

        val MIGRATION_4_5: Migration = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_4_5_SQL.forEach { db.execSQL(it) }
            }
        }

        // v5 → v6 (order 2026-09-24، القرار ٧٦/٧٧): scan_scores.overallScore
        // Float NOT NULL -> nullable. A V7 report never carries
        // `response.score`; ScoringResult.overallScore/AiScoringEngine.kt
        // stopped coercing that absence to 0f (a fabricated score a buyer
        // could see and that synced to Firestore) — this column must
        // accept the same real absence, or every V7 save would still need
        // to invent a placeholder right here.
        //
        // SQLite has no `ALTER TABLE ... ALTER COLUMN` — this is the
        // standard Room "recreate table" pattern: new table with the
        // relaxed constraint, copy every column explicitly (never
        // `SELECT *`, so a future column silently vanishing would show up
        // as a compile-time mismatch here instead), drop old, rename new.
        // No other column changes — schema shape otherwise unchanged since
        // v4 (confirmed: `git show be59137:.../ScanScoreEntity.kt`, the
        // commit nearest the distributed versionCode-10 APK by date, is
        // byte-identical to this table's shape at HEAD before this
        // migration — "استنتاج بالتاريخ" per the order, no exported schema
        // JSON exists in this repo to confirm the DDL more directly, see
        // this task's report).
        //
        // MIGRATION_5_6_SQL is a shared list, not inlined into `migrate()`
        // directly, so ScanScoresMigration5To6JvmTest (written when this module lacked
        // Robolectric - a test dependency since 3284cca - and still without Room's own androidx.room:room-testing
        // MigrationTestHelper) executes the EXACT SAME statements via
        // plain JDBC against a real SQLite engine, not a re-typed copy of
        // them.
        val MIGRATION_5_6_SQL: List<String> = listOf(
            "CREATE TABLE `scan_scores_new` (" +
                "`scanId` TEXT NOT NULL, " +
                "`overallScore` REAL, " +
                "`overallGrade` TEXT NOT NULL, " +
                "`axisScoresJson` TEXT NOT NULL, " +
                "`categoryScoresJson` TEXT NOT NULL, " +
                "`criticalFailuresJson` TEXT NOT NULL, " +
                "`antiTamperPenalty` REAL NOT NULL, " +
                "`isIncomplete` INTEGER NOT NULL, " +
                "`scoringMethod` TEXT NOT NULL, " +
                "`calculatedAt` INTEGER NOT NULL, " +
                "`shortId` TEXT, " +
                "`aiAnalysisJson` TEXT, " +
                "PRIMARY KEY(`scanId`), " +
                "FOREIGN KEY(`scanId`) REFERENCES `scans`(`scanId`) ON DELETE CASCADE)",
            "INSERT INTO `scan_scores_new` (" +
                "scanId, overallScore, overallGrade, axisScoresJson, categoryScoresJson, " +
                "criticalFailuresJson, antiTamperPenalty, isIncomplete, scoringMethod, " +
                "calculatedAt, shortId, aiAnalysisJson) " +
                "SELECT scanId, overallScore, overallGrade, axisScoresJson, categoryScoresJson, " +
                "criticalFailuresJson, antiTamperPenalty, isIncomplete, scoringMethod, " +
                "calculatedAt, shortId, aiAnalysisJson FROM `scan_scores`",
            "DROP TABLE `scan_scores`",
            "ALTER TABLE `scan_scores_new` RENAME TO `scan_scores`"
        )

        val MIGRATION_5_6: Migration = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_5_6_SQL.forEach { db.execSQL(it) }
            }
        }

        // v6 → v7 (order 2026-09-30، «أمر — التقييم (ب)، ومهلة الجلسة،
        // والبنود المعلّقة»، البند ١): add session_evaluations.finalFailure
        // so a CONFIRMED terminal submitEvaluation rejection (session
        // invalid or expired — see SessionEvaluationEntity.finalFailure's
        // doc) can be distinguished from a transient one (network, etc.)
        // and never retried by the sync path. Plain additive ALTER TABLE,
        // same pattern as MIGRATION_1_2/2_3/3_4 — no existing row's data
        // changes; every pre-migration row reads back finalFailure = 0.
        //
        // MIGRATION_6_7_SQL is a shared list constant (same reasoning as
        // MIGRATION_4_5_SQL/MIGRATION_5_6_SQL above) so
        // SessionEvaluationMigration6To7JvmTest runs the EXACT SAME
        // statement this real Migration object runs, not a hand-retyped
        // copy of it.
        val MIGRATION_6_7_SQL: List<String> = listOf(
            "ALTER TABLE session_evaluations ADD COLUMN finalFailure INTEGER NOT NULL DEFAULT 0"
        )

        val MIGRATION_6_7: Migration = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_6_7_SQL.forEach { db.execSQL(it) }
            }
        }

        // v7 → v8 (fieldsVersion 68, order 2026-10-08 «V8 الدفعة ٣ الجزء
        // الأوّل»، البند ٣-ج-١، S-29 (a)): functional_results.measurements —
        // the compound raw measurements of one test as a JSON object text
        // (see FunctionalResultEntity.measurements). Plain additive ALTER
        // TABLE, same pattern as MIGRATION_1_2/2_3/3_4/6_7: every
        // pre-migration row reads back measurements = NULL, which means "the
        // producer supplied none" — never a measured zero. No row is
        // rewritten, so an upgrade over an installed 1.0.10 keeps every
        // saved report (the order asks for that to be checked on a real
        // device in the next device round).
        //
        // MIGRATION_7_8_SQL is a shared list constant (same reasoning as
        // MIGRATION_4_5_SQL/5_6_SQL/6_7_SQL above) so
        // FunctionalResultsMigration7To8JvmTest runs the EXACT SAME statement
        // this real Migration object runs, not a hand-retyped copy of it.
        val MIGRATION_7_8_SQL: List<String> = listOf(
            "ALTER TABLE functional_results ADD COLUMN measurements TEXT DEFAULT NULL"
        )

        val MIGRATION_7_8: Migration = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_7_8_SQL.forEach { db.execSQL(it) }
            }
        }

        /**
         * Every migration, in version order. DatabaseModule registers exactly
         * this list: a bump that forgets its migration is caught by
         * NeuralCheckDatabaseMigrationChainTest instead of reaching
         * `fallbackToDestructiveMigration()`, which would wipe a technician's
         * saved reports on upgrade.
         */
        val ALL_MIGRATIONS: List<Migration> = listOf(
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5,
            MIGRATION_5_6,
            MIGRATION_6_7,
            MIGRATION_7_8
        )
    }
}
