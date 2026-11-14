package net.neuralcheck.hardware.security

import android.app.KeyguardManager
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Log
import net.neuralcheck.BuildConfig
import net.neuralcheck.domain.model.TestResult
import net.neuralcheck.domain.model.TestStatus
import java.io.File
import javax.inject.Inject

/**
 * Security tests SEC-01 to SEC-11.
 *
 * Each individual test is wrapped in [safeTest] so a single failing probe
 * (e.g. DevicePolicyManager access denied, or a SecurityException on
 * Settings.Global reads) does not abort the entire category — which was
 * previously causing zero security results to be persisted.
 */
class SecurityTests @Inject constructor(private val context: Context) {

    companion object {
        private const val TAG = "NeuralCheck"
    }

    fun runAll(): List<TestResult> {
        val base = listOf(
            safeTest("SEC-01", net.neuralcheck.R.string.test_sec01_ar, net.neuralcheck.R.string.test_sec01_tr) { testScreenLock() },
            safeTest("SEC-02", net.neuralcheck.R.string.test_sec02_ar, net.neuralcheck.R.string.test_sec02_tr) { testEncryption() },
            safeTest("SEC-03", net.neuralcheck.R.string.test_sec03_ar, net.neuralcheck.R.string.test_sec03_tr) { testSecurityPatch() },
            safeTest("SEC-04", net.neuralcheck.R.string.test_sec04_ar, net.neuralcheck.R.string.test_sec04_tr) { testRoot() },
            safeTest("SEC-05", net.neuralcheck.R.string.test_sec05_ar, net.neuralcheck.R.string.test_sec05_tr) { testDevMode() },
            safeTest("SEC-06", net.neuralcheck.R.string.test_sec06_ar, net.neuralcheck.R.string.test_sec06_tr) { testUsbDebug() },
            safeTest("SEC-07", net.neuralcheck.R.string.test_sec07_ar, net.neuralcheck.R.string.test_sec07_tr) { testUnknownSources() },
            safeTest("SEC-08", net.neuralcheck.R.string.test_sec08_ar, net.neuralcheck.R.string.test_sec08_tr) { testPlayProtect() },
            safeTest("SEC-09", net.neuralcheck.R.string.test_sec09_ar, net.neuralcheck.R.string.test_sec09_tr) { testOsVersion() },
            safeTest("SEC-10", net.neuralcheck.R.string.test_sec10_ar, net.neuralcheck.R.string.test_sec10_tr) { testApiLevel() },
            safeTest("SEC-11", net.neuralcheck.R.string.test_sec11_ar, net.neuralcheck.R.string.test_sec11_tr) { testBootloader() }
        )
        // SEC-12 (bootloader lock state) is datacollect-only. It reads
        // ro.boot.flash.locked / ro.boot.verifiedbootstate via getprop, which
        // surfaces a security-relevant tamper signal we don't want shown to
        // consumers (and that the launch flavor can't ship to Firestore as
        // a public field anyway).
        return if (BuildConfig.ENABLE_EXTENDED_INFO_TESTS) {
            base + safeTest(
                "SEC-12",
                net.neuralcheck.R.string.test_sec12_ar,
                net.neuralcheck.R.string.test_sec12_tr
            ) { testBootloaderLock() }
        } else {
            base
        }
    }

    private inline fun safeTest(
        testId: String,
        nameArResId: Int,
        nameTrResId: Int,
        block: () -> TestResult
    ): TestResult = try {
        block()
    } catch (e: Exception) {
        Log.w(TAG, "Security test $testId failed: ${e.message}")
        val nameAr = runCatching { context.getString(nameArResId) }.getOrDefault(testId)
        val nameTr = runCatching { context.getString(nameTrResId) }.getOrDefault(testId)
        TestResult(testId, nameAr, nameTr, "security",
            TestStatus.UNAVAILABLE, null, "N/A", "", e.message)
    }

    private fun testScreenLock(): TestResult {
        val nameAr = context.getString(net.neuralcheck.R.string.test_sec01_ar)
        val nameTr = context.getString(net.neuralcheck.R.string.test_sec01_tr)
        val enabledAr = context.getString(net.neuralcheck.R.string.value_enabled_ar)
        val disabledAr = context.getString(net.neuralcheck.R.string.value_disabled_ar)
        val unavailableAr = context.getString(net.neuralcheck.R.string.value_unavailable_ar)
        val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            ?: return TestResult("SEC-01", nameAr, nameTr, "security",
                TestStatus.UNAVAILABLE, null, unavailableAr, "", null)
        val secure = km.isDeviceSecure
        return TestResult("SEC-01", nameAr, nameTr, "security",
            if (secure) TestStatus.PASS else TestStatus.WARNING, null, if (secure) enabledAr else disabledAr, "", null)
    }

    private fun testEncryption(): TestResult {
        val nameAr = context.getString(net.neuralcheck.R.string.test_sec02_ar)
        val nameTr = context.getString(net.neuralcheck.R.string.test_sec02_tr)
        val encryptedAr = context.getString(net.neuralcheck.R.string.value_encrypted_ar)
        val unencryptedAr = context.getString(net.neuralcheck.R.string.value_unencrypted_ar)
        val unavailableAr = context.getString(net.neuralcheck.R.string.value_unavailable_ar)
        val dm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
            ?: return TestResult("SEC-02", nameAr, nameTr, "security",
                TestStatus.UNAVAILABLE, null, unavailableAr, "", null)
        val status = dm.storageEncryptionStatus
        val encrypted = status == DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE ||
                        status == DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE_DEFAULT_KEY ||
                        status == DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE_PER_USER
        return TestResult("SEC-02", nameAr, nameTr, "security", if (encrypted) TestStatus.PASS else TestStatus.FAIL, null,
            if (encrypted) encryptedAr else unencryptedAr, "", null)
    }

    private fun testSecurityPatch(): TestResult {
        val patch = Build.VERSION.SECURITY_PATCH ?: "unknown"
        val nameAr = context.getString(net.neuralcheck.R.string.test_sec03_ar)
        val nameTr = context.getString(net.neuralcheck.R.string.test_sec03_tr)
        return TestResult("SEC-03", nameAr, nameTr, "security", TestStatus.PASS, null, patch, "", null)
    }

    private fun testRoot(): TestResult {
        val suPaths = listOf("/system/bin/su", "/system/xbin/su", "/sbin/su", "/system/app/Superuser.apk", "/data/local/su")
        val suExists = suPaths.any { runCatching { File(it).exists() }.getOrDefault(false) }
        val testKeys = try { Build.TAGS?.contains("test-keys") ?: false } catch (e: Exception) { false }
        val rootDetected = suExists || testKeys
        val nameAr = context.getString(net.neuralcheck.R.string.test_sec04_ar)
        val nameTr = context.getString(net.neuralcheck.R.string.test_sec04_tr)
        val detectedAr = context.getString(net.neuralcheck.R.string.value_detected_ar)
        val notDetectedAr = context.getString(net.neuralcheck.R.string.value_not_detected_ar)
        return TestResult("SEC-04", nameAr, nameTr, "security",
            if (rootDetected) TestStatus.FAIL else TestStatus.PASS, null,
            if (rootDetected) detectedAr else notDetectedAr, "", null)
    }

    private fun testDevMode(): TestResult {
        val devMode = Settings.Global.getInt(context.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) != 0
        val nameAr = context.getString(net.neuralcheck.R.string.test_sec05_ar)
        val nameTr = context.getString(net.neuralcheck.R.string.test_sec05_tr)
        val enabledAr = context.getString(net.neuralcheck.R.string.value_enabled_ar)
        val disabledAr = context.getString(net.neuralcheck.R.string.value_disabled_ar)
        return TestResult("SEC-05", nameAr, nameTr, "security",
            if (devMode) TestStatus.WARNING else TestStatus.PASS, null, if (devMode) enabledAr else disabledAr, "", null)
    }

    private fun testUsbDebug(): TestResult {
        val adb = Settings.Global.getInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0) != 0
        val nameAr = context.getString(net.neuralcheck.R.string.test_sec06_ar)
        val nameTr = context.getString(net.neuralcheck.R.string.test_sec06_tr)
        val enabledAr = context.getString(net.neuralcheck.R.string.value_enabled_ar)
        val disabledAr = context.getString(net.neuralcheck.R.string.value_disabled_ar)
        return TestResult("SEC-06", nameAr, nameTr, "security",
            if (adb) TestStatus.WARNING else TestStatus.PASS, null, if (adb) enabledAr else disabledAr, "", null)
    }

    private fun testUnknownSources(): TestResult {
        val enabled = if (Build.VERSION.SDK_INT >= 26) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            @Suppress("DEPRECATION")
            Settings.Global.getInt(context.contentResolver, Settings.Global.INSTALL_NON_MARKET_APPS, 0) != 0
        }
        val nameAr = context.getString(net.neuralcheck.R.string.test_sec07_ar)
        val nameTr = context.getString(net.neuralcheck.R.string.test_sec07_tr)
        val allowedAr = context.getString(net.neuralcheck.R.string.value_allowed_ar)
        val forbiddenAr = context.getString(net.neuralcheck.R.string.value_forbidden_ar)
        return TestResult("SEC-07", nameAr, nameTr, "security",
            if (enabled) TestStatus.WARNING else TestStatus.PASS, null, if (enabled) allowedAr else forbiddenAr, "", null)
    }

    /**
     * Order 2026-09-30 (فحص NC-TM7PDN، البند ٢): this used to unconditionally
     * return PASS/"مفعّل" without reading anything — no SafetyNet/Play
     * Integrity API call, no PackageManager/Settings read of any kind, just
     * a hardcoded TestStatus.PASS. The launch flavor's POS-security summary
     * then told the buyer "Play Protect مفعّل" as if it were a measured
     * fact, which is a false claim on a device where Play Protect is
     * actually off. Until this test reads the real state (there is no
     * public API that returns Play Protect's on/off toggle directly — it
     * would need either SafetyNetApi's verifyApps status or Play Protect's
     * ContentProvider, neither wired up here), it must say so instead of
     * guessing "on".
     */
    private fun testPlayProtect(): TestResult {
        val nameAr = context.getString(net.neuralcheck.R.string.test_sec08_ar)
        val nameTr = context.getString(net.neuralcheck.R.string.test_sec08_tr)
        val unavailableAr = context.getString(net.neuralcheck.R.string.value_unavailable_ar)
        val notRead = context.getString(net.neuralcheck.R.string.sec08_status_not_read)
        return TestResult("SEC-08", nameAr, nameTr, "security", TestStatus.UNAVAILABLE, null, unavailableAr, "", notRead)
    }

    private fun testOsVersion(): TestResult {
        val nameAr = context.getString(net.neuralcheck.R.string.test_sec09_ar)
        val nameTr = context.getString(net.neuralcheck.R.string.test_sec09_tr)
        return TestResult("SEC-09", nameAr, nameTr, "security", TestStatus.PASS, null, "Android ${Build.VERSION.RELEASE}", "", null)
    }

    private fun testApiLevel(): TestResult {
        val api = Build.VERSION.SDK_INT
        val status = when { api >= 30 -> TestStatus.PASS; api >= 26 -> TestStatus.WARNING; else -> TestStatus.FAIL }
        val nameAr = context.getString(net.neuralcheck.R.string.test_sec10_ar)
        val nameTr = context.getString(net.neuralcheck.R.string.test_sec10_tr)
        return TestResult("SEC-10", nameAr, nameTr, "security", status, null, "$api", "", null)
    }

    private fun testBootloader(): TestResult {
        val bl = Build.BOOTLOADER ?: "unknown"
        val nameAr = context.getString(net.neuralcheck.R.string.test_sec11_ar)
        val nameTr = context.getString(net.neuralcheck.R.string.test_sec11_tr)
        return TestResult("SEC-11", nameAr, nameTr, "security", TestStatus.PASS, null, bl, "", null)
    }

    /**
     * SEC-12 (datacollect-only): bootloader lock state. Reads two complementary
     * Android boot props via `getprop`:
     *   - ro.boot.flash.locked      — "1" locked, "0" unlocked, "" unknown
     *   - ro.boot.verifiedbootstate — "green"=locked, "yellow"/"orange"=unlocked
     * An unlocked bootloader is a security failure (allows fastboot flashing,
     * bypasses verified boot) — used as a strong tamper indicator on Turkey-
     * market resale phones.
     */
    private fun testBootloaderLock(): TestResult {
        val nameAr = context.getString(net.neuralcheck.R.string.test_sec12_ar)
        val nameTr = context.getString(net.neuralcheck.R.string.test_sec12_tr)
        val unavailableAr = context.getString(net.neuralcheck.R.string.value_unavailable_ar)
        val lockedAr = context.getString(net.neuralcheck.R.string.value_locked_ar)
        val unlockedAr = context.getString(net.neuralcheck.R.string.value_unlocked_ar)

        val flashLocked = readSystemProp("ro.boot.flash.locked")
        val verifiedBoot = readSystemProp("ro.boot.verifiedbootstate")

        val isLocked = flashLocked == "1" || verifiedBoot.equals("green", ignoreCase = true)
        val isUnlocked = flashLocked == "0" ||
            verifiedBoot.equals("orange", ignoreCase = true) ||
            verifiedBoot.equals("yellow", ignoreCase = true)

        return when {
            isLocked -> TestResult("SEC-12", nameAr, nameTr, "security",
                TestStatus.PASS, null, lockedAr, "", verifiedBoot.takeIf { it.isNotBlank() })
            isUnlocked -> TestResult("SEC-12", nameAr, nameTr, "security",
                TestStatus.FAIL, null, unlockedAr, "", verifiedBoot.takeIf { it.isNotBlank() })
            else -> TestResult("SEC-12", nameAr, nameTr, "security",
                TestStatus.UNAVAILABLE, null, unavailableAr, "", null)
        }
    }

    /**
     * Read an Android system property via the `getprop` binary. Used by
     * SEC-12 to access boot-state props that aren't exposed through any
     * public API. Returns "" on any failure.
     */
    private fun readSystemProp(key: String): String = try {
        val process = Runtime.getRuntime().exec(arrayOf("getprop", key))
        val out = process.inputStream.bufferedReader().use { it.readLine() }?.trim().orEmpty()
        process.waitFor()
        out
    } catch (t: Throwable) {
        Log.w(TAG, "getprop $key failed: ${t.message}")
        ""
    }
}
