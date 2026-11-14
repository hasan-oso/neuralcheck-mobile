package net.neuralcheck.data.local.prefs

import android.content.SharedPreferences

/**
 * Local-only record of a Room `fallbackToDestructiveMigration()` wipe.
 *
 * DatabaseModule.kt keeps `fallbackToDestructiveMigration()` as a safety net
 * for any future schema bump that ships without a matching `Migration`
 * (order 2026-09-24، القرار ٨٠ البند ١: "إزالته تجعل الترحيل الناقص انهياراً
 * عند فتح التطبيق، وإبقاؤه صامتاً يُخفي فقد البيانات"). This class is the
 * "غير صامت" half: [recordEvent] is called from
 * `RoomDatabase.Callback.onDestructiveMigration` the moment Room actually
 * drops and recreates the local tables, and [consumePendingAlert] lets the
 * UI layer show the user a one-time notice afterwards.
 *
 * Storage: SharedPreferences file [PREFS_NAME] — plain (not
 * [SecurePreferences]'s encrypted store), `Context.MODE_PRIVATE`, on-device
 * only. No network call anywhere in this class. The event carries no report
 * content, just a boolean flag and a timestamp, so it does not need
 * encryption; this mirrors `NeuralCheckApp.persistCrash`'s plain
 * `getSharedPreferences("neuralcheck_crashes", MODE_PRIVATE)` ring buffer.
 *
 * Cleared: [consumePendingAlert] flips [KEY_PENDING] back to `false` the
 * same time it reports `true` to its caller — so the flag is live for
 * exactly one read. The timestamp under [KEY_LAST_EVENT_AT_MS] is NOT
 * cleared by [consumePendingAlert]; it is overwritten by the next
 * [recordEvent] and otherwise stays as a local "last known wipe" audit
 * trail (never read by any UI right now, kept for future debugging /
 * support use, never synced anywhere).
 */
class DestructiveMigrationTracker(private val prefs: SharedPreferences) {

    /** Called from the Room callback the instant a destructive wipe happens. */
    fun recordEvent(atMs: Long = System.currentTimeMillis()) {
        prefs.edit()
            .putBoolean(KEY_PENDING, true)
            .putLong(KEY_LAST_EVENT_AT_MS, atMs)
            .apply()
    }

    /**
     * Returns `true` exactly once per [recordEvent] call. The caller (the
     * app-root Composable, on first display) is expected to show the alert
     * immediately when this returns `true`; the flag is cleared as part of
     * this same call, not on a later "dialog dismissed" callback, so a
     * config change or process death right after the dialog is shown can
     * never make it reappear on the next launch, but a config change or
     * process death BEFORE the dialog is shown could show it once, then
     * lose it. There is no destructive-migration event this can be lost
     * across app launches without the local record in [KEY_LAST_EVENT_AT_MS]
     * still proving it happened.
     */
    fun consumePendingAlert(): Boolean {
        val pending = prefs.getBoolean(KEY_PENDING, false)
        if (pending) {
            prefs.edit().putBoolean(KEY_PENDING, false).apply()
        }
        return pending
    }

    /** Epoch millis of the most recent recorded wipe, or null if none ever happened. */
    fun lastEventAtMsOrNull(): Long? {
        val value = prefs.getLong(KEY_LAST_EVENT_AT_MS, -1L)
        return if (value < 0L) null else value
    }

    companion object {
        const val PREFS_NAME = "neuralcheck_db_events"
        private const val KEY_PENDING = "destructive_migration_alert_pending"
        private const val KEY_LAST_EVENT_AT_MS = "destructive_migration_last_at_ms"
    }
}
