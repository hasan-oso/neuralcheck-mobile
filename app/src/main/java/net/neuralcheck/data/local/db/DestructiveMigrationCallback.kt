package net.neuralcheck.data.local.db

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import net.neuralcheck.data.local.prefs.DestructiveMigrationTracker

/**
 * Wired into `Room.databaseBuilder(...).addCallback(...)` in DatabaseModule.kt
 * alongside `fallbackToDestructiveMigration()` (order 2026-09-24، القرار ٨٠
 * البند ١). `onDestructiveMigration` is Room's own hook — it fires only when
 * the destructive fallback actually ran (i.e. Room found no registered
 * `Migration` for the device's on-disk version and dropped + recreated every
 * table), never on a normal migration or a fresh install. A device that
 * upgrades through the registered `MIGRATION_1_2` … `MIGRATION_5_6` chain
 * never reaches this method.
 *
 * Kept as its own small class (instead of an anonymous object inline in
 * DatabaseModule) so a JVM test can call `onDestructiveMigration` directly
 * against a fake `SupportSQLiteDatabase` without needing Room's runtime or
 * Robolectric — see DestructiveMigrationAlertTest.
 */
class DestructiveMigrationCallback(
    private val tracker: DestructiveMigrationTracker
) : RoomDatabase.Callback() {

    override fun onDestructiveMigration(db: SupportSQLiteDatabase) {
        super.onDestructiveMigration(db)
        tracker.recordEvent()
    }
}
