package net.neuralcheck.data.local.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import net.neuralcheck.util.LocaleHelper

class SecurePreferences(private val context: Context) {
    private val prefs: SharedPreferences = try {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            "neuralcheck_prefs",
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        // Fallback to regular prefs if crypto fails
        context.getSharedPreferences("neuralcheck_prefs_fallback", Context.MODE_PRIVATE)
    }

    var isOnboardingComplete: Boolean
        get() = prefs.getBoolean("onboarding_complete", false)
        set(value) = prefs.edit().putBoolean("onboarding_complete", value).apply()

    var syncEnabled: Boolean
        get() = prefs.getBoolean("sync_enabled", true)
        set(value) = prefs.edit().putBoolean("sync_enabled", value).apply()

    var language: String
        get() = prefs.getString("language", "ar") ?: "ar"
        set(value) {
            prefs.edit().putString("language", value).apply()
            LocaleHelper.persist(context, value)
        }

    var theme: String
        get() = prefs.getString("theme", "auto") ?: "auto"
        set(value) = prefs.edit().putString("theme", value).apply()

    var devToolsUnlocked: Boolean
        get() = prefs.getBoolean("dev_tools_unlocked", false)
        set(value) = prefs.edit().putBoolean("dev_tools_unlocked", value).apply()
}
