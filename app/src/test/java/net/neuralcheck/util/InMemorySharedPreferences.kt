package net.neuralcheck.util

import android.content.SharedPreferences

/**
 * Minimal in-memory [SharedPreferences] double for JVM tests (written when this module lacked Robolectric, a test dependency since 3284cca), shared by the tests of
 * `ScanSessionRecords` and the real-path harness. Not a mock: state genuinely persists across calls, and [edit]
 * applies its removals before its puts, as the real editor does for a key that is both removed and put.
 * [sameFileAs] hands out a second instance over the SAME backing map — what two `getSharedPreferences(name)` calls
 * across a process restart see — so a test can show that a value survives "the process".
 *
 * Same shape as the private `FakeSharedPreferences` in `DestructiveMigrationAlertTest` (which this does not replace:
 * that test file is about a different feature and keeps its own double).
 */
internal class InMemorySharedPreferences(
    private val values: MutableMap<String, Any?> = linkedMapOf()
) : SharedPreferences {

    /** A second view over the same stored values (a new process reading the same file). */
    fun sameFileAs(): InMemorySharedPreferences = InMemorySharedPreferences(values)

    /** What is stored, for assertions. */
    fun snapshot(): Map<String, Any?> = LinkedHashMap(values)

    override fun getAll(): MutableMap<String, *> = LinkedHashMap(values)

    override fun getString(key: String?, defValue: String?): String? = values[key] as? String ?: defValue

    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        @Suppress("UNCHECKED_CAST") (values[key] as? MutableSet<String> ?: defValues)

    override fun getInt(key: String?, defValue: Int): Int = values[key] as? Int ?: defValue

    override fun getLong(key: String?, defValue: Long): Long = values[key] as? Long ?: defValue

    override fun getFloat(key: String?, defValue: Float): Float = values[key] as? Float ?: defValue

    override fun getBoolean(key: String?, defValue: Boolean): Boolean = values[key] as? Boolean ?: defValue

    override fun contains(key: String?): Boolean = values.containsKey(key)

    override fun edit(): SharedPreferences.Editor = Editor()

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

    private inner class Editor : SharedPreferences.Editor {
        private val pending = linkedMapOf<String, Any?>()
        private val toRemove = linkedSetOf<String>()
        private var clearAll = false

        override fun putString(key: String?, value: String?): SharedPreferences.Editor = apply { if (key != null) pending[key] = value }
        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = apply { if (key != null) pending[key] = values }
        override fun putInt(key: String?, value: Int): SharedPreferences.Editor = apply { if (key != null) pending[key] = value }
        override fun putLong(key: String?, value: Long): SharedPreferences.Editor = apply { if (key != null) pending[key] = value }
        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = apply { if (key != null) pending[key] = value }
        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = apply { if (key != null) pending[key] = value }
        override fun remove(key: String?): SharedPreferences.Editor = apply { if (key != null) toRemove.add(key) }
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
