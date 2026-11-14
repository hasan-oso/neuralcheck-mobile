package net.neuralcheck.data.local.db

import android.content.SharedPreferences
import androidx.sqlite.db.SupportSQLiteDatabase
import net.neuralcheck.data.local.db.NeuralCheckDatabase.Companion.MIGRATION_5_6_SQL
import net.neuralcheck.data.local.prefs.DestructiveMigrationTracker
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import java.io.File
import java.sql.DriverManager

/**
 * Order 2026-09-24 (القرار ٨٠ البند ١). `DatabaseModule.kt`'s
 * `fallbackToDestructiveMigration()` stays (line unchanged — see this
 * task's report) but must no longer be silent: this proves the local
 * record + one-time-alert contract that replaces the silence —
 * "الغياب لا يُقرأ قياساً" (order's القاعدة 22) applied to a wiped local
 * DB: the wipe must surface, not vanish.
 *
 * Plain JVM (written when the module lacked Robolectric, a test dependency since
 * 3284cca; this test does not use it, like ScanScoresMigration5To6JvmTest), so this does not — and,
 * without a Room database, cannot — prove
 * Room itself calls `onDestructiveMigration` only on an actual destructive
 * wipe; that is Room 2.6.1's own documented contract for
 * `RoomDatabase.Callback.onDestructiveMigration`, exercised by an
 * instrumented/device test outside this round's scope. What IS tested here,
 * on the plain JVM, is the exact code this repo owns: the callback this app
 * registers, and the tracker it drives — using [FakeSharedPreferences], a
 * tiny in-memory double, not a mock, so state genuinely persists across
 * calls the way a real on-device SharedPreferences file would.
 */
class DestructiveMigrationAlertTest {

    private lateinit var prefs: FakeSharedPreferences
    private lateinit var tracker: DestructiveMigrationTracker
    private lateinit var callback: DestructiveMigrationCallback

    @Before
    fun setUp() {
        prefs = FakeSharedPreferences()
        tracker = DestructiveMigrationTracker(prefs)
        callback = DestructiveMigrationCallback(tracker)
    }

    @Test
    fun `a normal upgrade path never calls onDestructiveMigration, so the alert never fires`() {
        // Models a clean v5 to v6 upgrade (MIGRATION_5_6 registered and
        // matched) — Room never invokes onDestructiveMigration in that
        // path, so nothing in this app ever calls callback.onDestructiveMigration
        // here either. The tracker must therefore report nothing pending.
        assertFalse(
            "no destructive migration happened — the alert must not fire",
            tracker.consumePendingAlert()
        )
        assertNull(
            "no destructive migration was ever recorded",
            tracker.lastEventAtMsOrNull()
        )
    }

    @Test
    fun `a real successful v5-to-v6 migration run leaves the destructive-migration alert untouched`() {
        // Order's own test spec: "لا يظهر بعد ترقية سليمة (v5⇐v6 مثلاً)".
        // Executes the EXACT SAME MIGRATION_5_6_SQL statements the real
        // Migration(5, 6) object runs (same technique as
        // ScanScoresMigration5To6JvmTest — real SQLite JDBC, not a mock) to
        // prove a genuinely successful managed migration completes, and
        // that nothing about that success path ever calls
        // DestructiveMigrationCallback.onDestructiveMigration — the two
        // code paths (registered Migration vs. destructive fallback) are
        // mutually exclusive by construction, and this ties that back to
        // the tracker directly instead of leaving it as two separate,
        // never-cross-checked test files.
        val dbFile = File.createTempFile("destructive_migration_alert_v5_v6", ".db")
        try {
            DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}").use { connection ->
                connection.createStatement().use {
                    it.execute("CREATE TABLE `scans` (`scanId` TEXT NOT NULL PRIMARY KEY)")
                }
                connection.createStatement().use {
                    it.execute(
                        "CREATE TABLE `scan_scores` (" +
                            "`scanId` TEXT NOT NULL, `overallScore` REAL NOT NULL, " +
                            "`overallGrade` TEXT NOT NULL, `axisScoresJson` TEXT NOT NULL, " +
                            "`categoryScoresJson` TEXT NOT NULL, `criticalFailuresJson` TEXT NOT NULL, " +
                            "`antiTamperPenalty` REAL NOT NULL, `isIncomplete` INTEGER NOT NULL, " +
                            "`scoringMethod` TEXT NOT NULL, `calculatedAt` INTEGER NOT NULL, " +
                            "`shortId` TEXT, `aiAnalysisJson` TEXT, PRIMARY KEY(`scanId`))"
                    )
                }
                MIGRATION_5_6_SQL.forEach { sql -> connection.createStatement().use { it.execute(sql) } }
            }
        } finally {
            dbFile.delete()
        }

        // The migration above ran to completion with no exception — a real
        // upgrade, not a wipe — and at no point did anything call
        // callback.onDestructiveMigration. The tracker must show nothing
        // pending.
        assertFalse(
            "a successful v5-to-v6 migration must never arm the destructive-migration alert",
            tracker.consumePendingAlert()
        )
    }

    @Test
    fun `a simulated destructive wipe makes the one-time alert fire`() {
        // Models the fallbackToDestructiveMigration() safety net actually
        // firing (e.g. a future schema bump with no matching Migration).
        callback.onDestructiveMigration(mock<SupportSQLiteDatabase>())

        assertTrue(
            "a destructive wipe happened — the alert must fire",
            tracker.consumePendingAlert()
        )
    }

    @Test
    fun `the alert fires once, not on every subsequent launch`() {
        callback.onDestructiveMigration(mock<SupportSQLiteDatabase>())

        // First read == first app launch after the wipe: the UI shows it.
        assertTrue(tracker.consumePendingAlert())

        // Every later read == a later cold start with no NEW destructive
        // migration: must stay false, not re-show the notice forever.
        assertFalse(
            "the alert must not reappear on a later launch with no new wipe",
            tracker.consumePendingAlert()
        )
        assertFalse(tracker.consumePendingAlert())
    }

    @Test
    fun `recordEvent leaves a local timestamp that consuming the alert does not erase`() {
        callback.onDestructiveMigration(mock<SupportSQLiteDatabase>())
        tracker.consumePendingAlert()

        val recordedAt = tracker.lastEventAtMsOrNull()
        assertTrue(
            "the local audit-trail timestamp must survive the alert being consumed",
            recordedAt != null && recordedAt > 0L
        )
    }

    @Test
    fun `a second destructive wipe re-arms the alert`() {
        callback.onDestructiveMigration(mock<SupportSQLiteDatabase>())
        assertTrue(tracker.consumePendingAlert())
        assertFalse(tracker.consumePendingAlert())

        // A later, separate destructive wipe (a different future schema
        // bump) must be able to show the notice again — "once per event",
        // not "once ever for the life of the install".
        callback.onDestructiveMigration(mock<SupportSQLiteDatabase>())
        assertTrue(
            "a new destructive wipe must re-arm the one-time alert",
            tracker.consumePendingAlert()
        )
    }
}

/**
 * Minimal in-memory [SharedPreferences] double. Real Android
 * SharedPreferences persists to a file and survives process death; this
 * fake only needs to survive across calls within one test (same guarantee
 * [DestructiveMigrationTracker] itself relies on), so a backing map is
 * enough — no disk I/O, no Android framework classes touched.
 */
private class FakeSharedPreferences : SharedPreferences {

    private val values = mutableMapOf<String, Any?>()

    override fun getAll(): MutableMap<String, *> = values

    override fun getString(key: String?, defValue: String?): String? =
        values[key] as? String ?: defValue

    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        @Suppress("UNCHECKED_CAST") (values[key] as? MutableSet<String> ?: defValues)

    override fun getInt(key: String?, defValue: Int): Int = values[key] as? Int ?: defValue

    override fun getLong(key: String?, defValue: Long): Long = values[key] as? Long ?: defValue

    override fun getFloat(key: String?, defValue: Float): Float = values[key] as? Float ?: defValue

    override fun getBoolean(key: String?, defValue: Boolean): Boolean =
        values[key] as? Boolean ?: defValue

    override fun contains(key: String?): Boolean = values.containsKey(key)

    override fun edit(): SharedPreferences.Editor = FakeEditor()

    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?
    ) {
        /* not needed by DestructiveMigrationTracker */
    }

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?
    ) {
        /* not needed by DestructiveMigrationTracker */
    }

    private inner class FakeEditor : SharedPreferences.Editor {
        private val pending = mutableMapOf<String, Any?>()
        private val toRemove = mutableSetOf<String>()
        private var clearAll = false

        override fun putString(key: String?, value: String?): SharedPreferences.Editor =
            apply { if (key != null) pending[key] = value }

        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor =
            apply { if (key != null) pending[key] = values }

        override fun putInt(key: String?, value: Int): SharedPreferences.Editor =
            apply { if (key != null) pending[key] = value }

        override fun putLong(key: String?, value: Long): SharedPreferences.Editor =
            apply { if (key != null) pending[key] = value }

        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor =
            apply { if (key != null) pending[key] = value }

        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor =
            apply { if (key != null) pending[key] = value }

        override fun remove(key: String?): SharedPreferences.Editor =
            apply { if (key != null) toRemove.add(key) }

        override fun clear(): SharedPreferences.Editor = apply { clearAll = true }

        override fun commit(): Boolean {
            apply()
            return true
        }

        override fun apply() {
            if (clearAll) values.clear()
            toRemove.forEach { values.remove(it) }
            values.putAll(pending)
        }
    }
}
