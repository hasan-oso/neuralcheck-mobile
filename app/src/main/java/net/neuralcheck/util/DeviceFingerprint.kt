package net.neuralcheck.util

import android.app.ActivityManager
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import com.google.firebase.installations.FirebaseInstallations
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.Signature

/**
 * Five-layer device profile key. Each layer is computed independently and
 * tolerates failure with a sentinel string ("no_keystore", "no_fid", …) so
 * a single missing signal cannot collapse the primary fingerprint. The
 * primary is the SHA-256 of the five layer values joined by '|', so the
 * same (Keystore key, FID, ANDROID_ID, build, sensors) tuple always produces
 * the same primary.
 *
 * Layers A, B and C below follow research/device-identifier-stability-
 * 2026-10-09.md (neuralcheck-workspace, 2026-10-09 +03), whose source codes
 * (K1..K5, F1, G1..G4, G10, G12, A1, A2) are listed at the end of this
 * comment. A statement carries a source code, or [inference] where the
 * research itself calls it an inference. The source texts are quoted as the
 * research gives them; this comment's author did not re-fetch them. NONE of
 * the behaviour below was measured on a device of ours:
 * غير مقيس على أجهزتنا.
 *
 * Layer A — RSA-2048 key in AndroidKeyStore (alias
 *           neuralcheck_device_key_v1); the layer value is
 *           sha256(hex(SHA256withRSA signature of a constant payload)). The
 *           key pair is generated at random in every install that does not
 *           find the alias (computeLayerA), so A is a per-install random
 *           value, not one derived from the hardware. Whether the key is
 *           TEE-backed is NOT checked in this file (no setIsStrongBoxBacked,
 *           no KeyInfo query).
 *           It does not survive a factory reset, nor removing the app, nor
 *           clearing its data: [K1] the keystore maintenance operation is
 *           "used ... when apps are uninstalled"; [K2] it "mainly gets called
 *           when an app gets removed", and deleteAllKeys is "Used when
 *           keystore is reset completely"; [K3, K4] clearing app data removes
 *           the app's keystore data (AOSP Android 11 and 14 sources, not
 *           Samsung's build). A factory reset removes the app, so the
 *           uninstall case applies.
 *           A system update keeps the key [K5: "the key is upgraded before it
 *           can be used"]; that the signature value stays the same is
 *           [inference] (PKCS#1 v1.5 signing is deterministic; checked with
 *           Node, not with Android Keystore). Moving the system back to an
 *           older version makes the key unusable [K5].
 *           A signing-key change without an APK signature lineage forces an
 *           uninstall [G12], so A changes; with a v3 lineage it stays
 *           [inference]. Two units of one model get different values.
 * Layer B — Firebase Installation ID, stored here as sha256. Not derived
 *           from the hardware. The SDK writes it to a file under
 *           getFilesDir() (firebase-installations 17.2.0, the version the
 *           BOM 32.7.0 most likely pins, and 18.0.0:
 *           PersistedInstallation.java:92).
 *           It changes on uninstall + reinstall [F1, G1: it "only persists
 *           as long as the app is installed"] and on clearing app data
 *           [G1, F1]; on a factory reset it changes [inference] from the
 *           uninstall case. It stays across app updates and Play-store
 *           updates [G1; applying that to updates is [inference]]. A
 *           signing-key change behaves as for A.
 *           EXCEPTION, NOT measured: Auto Backup includes the files under
 *           getFilesDir() and restores them "whenever the app is installed,
 *           whether from the Play Store, during device setup ..., or by
 *           running adb install" [G10]. Builds up to 1.0.10 carry
 *           allowBackup="true" with no backup rules, so they may get the same
 *           FID back after uninstall + install or after a factory reset, and
 *           may carry it to another device [inference]; no official source
 *           settles it. From 1.0.11 the manifest sets allowBackup="false" and
 *           points dataExtractionRules and fullBackupContent at
 *           exclude-everything files (user decision 2026-10-09; pinned by
 *           BackupDisabledManifestTest), so for an install of that build the
 *           FID file is not backed up and a restore cannot bring it back;
 *           datasets that earlier builds already uploaded are not touched by
 *           this, and what a later install does with them is not documented
 *           in the sources above and not measured. Two installs differ [F1:
 *           "different for every installation of every application"].
 * Layer C — Settings.Secure.ANDROID_ID, stored here as sha256. Scoped to
 *           (app signing key, user, device) [G3, G4].
 *           It stays across uninstall + reinstall when the package name and
 *           signing key are the same [G2: "does not change on package
 *           uninstall or reinstall"; G4], and across a system update [G2].
 *           Across clearing app data it stays [inference, A1: the wipe calls
 *           removeSettingsForPackageLocked on the system settings only, and
 *           the value is an HMAC recomputed with the same per-user key].
 *           A factory reset may change it [G3: "may change if a factory
 *           reset is performed on the device"; G4] — the documentation says
 *           "may", not "does".
 *           A signing-key change changes it [G3, G4]; with a v3 lineage the
 *           oldest certificate is reported and it stays [inference, A1, A2].
 *           This project's signing key was rotated once, in 0d37384
 *           (2026-05-17); that commit's report
 *           (docs/audits/phase_1_13_keystore_rotation/build-gradle-
 *           refactor.md) does not mention a lineage (absence of a mention is
 *           not proof of none).
 *           Both flavors (net.neuralcheck and net.neuralcheck.datacollect)
 *           use the same signing config, so on one device they likely share
 *           the value [inference, G3]. Two units of one model differ [G3].
 * Layer D — Composite build properties + total RAM + total storage. Falls
 *           back to sha256(Build.MODEL) on any I/O failure so the layer
 *           never returns a sentinel — it's the most-likely-to-collide
 *           layer and a hash of MODEL is still better than nothing.
 * Layer E — Sensor catalog signature (sorted type|vendor|version triples
 *           hashed together).
 *
 * Sources for layers A, B and C (codes as in the research; external texts,
 * not verified here):
 *   K1  https://source.android.com/docs/security/features/keystore
 *   K2  https://android.googlesource.com/platform/system/security/+/refs/heads/main/keystore2/aidl/android/security/maintenance/IKeystoreMaintenance.aidl
 *   K3  https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android11-release/services/core/java/com/android/server/pm/PackageManagerService.java
 *   K4  https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android14-release/services/core/java/com/android/server/pm/PackageManagerService.java
 *       and https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/services/core/java/com/android/server/pm/AppDataHelper.java
 *   K5  https://source.android.com/docs/security/features/keystore/version-binding
 *   F1  https://firebase.google.com/docs/projects/manage-installations
 *   G1  https://developer.android.com/identity/user-data-ids
 *   G2  https://developer.android.com/about/versions/oreo/android-8.0-changes
 *   G3  https://developer.android.com/reference/android/provider/Settings.Secure#ANDROID_ID
 *   G4  https://android-developers.googleblog.com/2017/04/changes-to-device-identifiers-in.html
 *   G10 https://developer.android.com/identity/data/autobackup
 *   G12 https://developer.android.com/studio/publish/app-signing
 *   A1  https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/main/packages/SettingsProvider/src/com/android/providers/settings/SettingsProvider.java
 *   A2  https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/main/core/java/android/content/pm/PackageInfo.java
 *
 * WIRE NAMES (user decision 2026-10-09, item 5, step one of the device-key fix). This class is
 * serialised by Gson inside the analyzeDevice request (DeviceInfo.deviceProfileKey) and lives in
 * net.neuralcheck.util, which no keep rule in proguard-rules.pro covers. So the release APK did not
 * send these six names: it sent R8's letters. Measured on the 1.0.10 release build (62507f4,
 * app-launch-release.apk, its classes.dex, dexdump of class Lgf/t;): six instance fields a..f, no
 * annotation; and that build's mapping.txt says `net.neuralcheck.util.DeviceProfileKey -> gf.t` with
 * primary -> a, layerA_keystore -> b, layerB_firebaseId -> c, layerC_androidId -> d,
 * layerD_buildSig -> e, layerE_sensorSig -> f. Every field now carries @SerializedName with its OWN
 * name, so the JSON key is that value whatever R8 does to the field (proguard-rules.pro keeps
 * annotations and, in a catch-all rule, the annotated fields themselves). Chosen over a keep rule
 * because the wire name is written at the field - renaming the Kotlin property can no longer change
 * what the server receives, it shows up as a failure in GsonRequestRenameProtectionTest - and
 * because a JVM test can check an annotation but not a rules file's effect. The debug request is
 * unchanged (the keys were already these names). Release builds up to 1.0.10 still send a..f; the
 * server reads both (see the commit that introduced this annotation).
 */
data class DeviceProfileKey(
    @SerializedName("primary") val primary: String,
    @SerializedName("layerA_keystore") val layerA_keystore: String,
    @SerializedName("layerB_firebaseId") val layerB_firebaseId: String,
    @SerializedName("layerC_androidId") val layerC_androidId: String,
    @SerializedName("layerD_buildSig") val layerD_buildSig: String,
    @SerializedName("layerE_sensorSig") val layerE_sensorSig: String
)

object DeviceFingerprint {
    private const val TAG = "NeuralCheck.Fp"

    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val KEY_ALIAS = "neuralcheck_device_key_v1"
    private const val SIGN_PAYLOAD = "neuralcheck_device_v1"

    private const val FALLBACK_NO_KEYSTORE = "no_keystore"
    private const val FALLBACK_NO_FID = "no_fid"
    private const val FALLBACK_NO_ANDROID_ID = "no_android_id"
    private const val FALLBACK_NO_SENSORS = "no_sensors"

    /**
     * Build the five-layer device profile key.
     *
     * [primary] is derived from all five layers, so hashing Layers B and C
     * changed it for every device. Confirmed with the backend before this
     * shipped: the `deviceProfiles` collection is EMPTY, so nothing is
     * keyed on a pre-change primary — no migration, nothing to break. The
     * cutover is only safe while that stays true, which is why this lands
     * before field scans start populating it.
     *
     * Suspend because Layer B awaits
     * a Firebase Installations result and Layer A may run a 2048-bit RSA
     * keypair generation (slow on first call) on Dispatchers.IO. Layers run
     * sequentially because the primary is order-dependent and we want
     * predictable logging order on failure.
     */
    suspend fun generateProfileKey(context: Context): DeviceProfileKey {
        val a = computeLayerA(context)
        val b = computeLayerB()
        val c = computeLayerC(context)
        val d = computeLayerD(context)
        val e = computeLayerE(context)
        val primary = sha256("$a|$b|$c|$d|$e")
        return DeviceProfileKey(
            primary = primary,
            layerA_keystore = a,
            layerB_firebaseId = b,
            layerC_androidId = c,
            layerD_buildSig = d,
            layerE_sensorSig = e
        )
    }

    /**
     * Legacy single-string fingerprint kept temporarily so the existing
     * call site in StressTestService compiles unchanged. The body delegates
     * to [generateProfileKey] so anyone still on the old call site benefits
     * from the new key without per-caller migration. Removed in Phase 4
     * after every caller switches to the suspend API.
     */
    @Deprecated(
        "Use generateProfileKey() instead. Removed in Phase 4.",
        ReplaceWith("generateProfileKey(context).primary")
    )
    fun generate(context: Context): String =
        runBlocking { generateProfileKey(context).primary }

    // ─────────────────────────── Layer A ───────────────────────────

    @Suppress("UNUSED_PARAMETER") // context kept for symmetry with other layers
    private suspend fun computeLayerA(context: Context): String = withContext(Dispatchers.IO) {
        try {
            val ks = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            if (!ks.containsAlias(KEY_ALIAS)) {
                val spec = KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
                )
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                    .setKeySize(2048)
                    // Intentionally NO setIsStrongBoxBacked: empirically
                    // ~65% of in-market devices either reject StrongBox or
                    // throw StrongBoxUnavailableException at generate time.
                    // TEE (the AndroidKeyStore default) covers ~95%.
                    .build()
                KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, KEYSTORE_PROVIDER)
                    .apply {
                        initialize(spec)
                        generateKeyPair()
                    }
            }
            val privateKey = ks.getKey(KEY_ALIAS, null) as? PrivateKey
                ?: error("KeyStore entry for $KEY_ALIAS is not a PrivateKey")
            val sig = Signature.getInstance("SHA256withRSA").apply {
                initSign(privateKey)
                update(SIGN_PAYLOAD.toByteArray(Charsets.UTF_8))
            }
            sha256(bytesToHex(sig.sign()))
        } catch (t: Throwable) {
            Log.w(TAG, "Layer A failed: ${t.javaClass.simpleName}: ${t.message}")
            FALLBACK_NO_KEYSTORE
        }
    }

    // ─────────────────────────── Layer B ───────────────────────────

    private suspend fun computeLayerB(): String = try {
        val id = FirebaseInstallations.getInstance().id.await()
        // Hashed before it leaves this function. The Firebase Installation
        // ID is a durable per-install identifier and this value is
        // serialised verbatim into the analyzeDevice payload
        // (DeviceInfo.deviceProfileKey) — it used to travel raw. The digest
        // keeps every property the key actually needs: same install → same
        // value, different install → different value.
        // The sentinel stays UNHASHED on purpose (see computeLayerA /
        // computeLayerE, which already work this way): it carries no
        // identifying information, and leaving it readable is what lets a
        // reader tell "this layer failed" from "this is an opaque digest".
        hashedOrSentinel(id, FALLBACK_NO_FID)
    } catch (t: Throwable) {
        Log.w(TAG, "Layer B failed: ${t.javaClass.simpleName}: ${t.message}")
        FALLBACK_NO_FID
    }

    // ─────────────────────────── Layer C ───────────────────────────

    private fun computeLayerC(context: Context): String = try {
        val v = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        // Hashed before it leaves this function. ANDROID_ID is the most
        // durable identifier the app touches — stable for the life of the
        // device per (signing key, user, device) and reset only by a factory
        // reset — and it too was serialised raw into the payload. Sentinel
        // left unhashed for the same reason as Layer B.
        hashedOrSentinel(v, FALLBACK_NO_ANDROID_ID)
    } catch (t: Throwable) {
        Log.w(TAG, "Layer C failed: ${t.javaClass.simpleName}: ${t.message}")
        FALLBACK_NO_ANDROID_ID
    }

    // ─────────────────────────── Layer D ───────────────────────────

    private fun computeLayerD(context: Context): String = try {
        val composite = buildString {
            append(Build.MODEL).append("|")
            append(Build.VERSION.RELEASE).append("|")
            append(getScreenSize(context)).append("|")
            append(Runtime.getRuntime().availableProcessors()).append("|")
            append(Build.BOARD).append("|")
            append(Build.HARDWARE).append("|")
            append(Build.BOOTLOADER).append("|")
            append(Build.FINGERPRINT).append("|")
            append(totalRamBytes(context)).append("|")
            append(totalStorageBytes())
        }
        sha256(composite)
    } catch (t: Throwable) {
        Log.w(TAG, "Layer D failed: ${t.javaClass.simpleName}: ${t.message}")
        sha256(Build.MODEL ?: "unknown")
    }

    // ─────────────────────────── Layer E ───────────────────────────

    private fun computeLayerE(context: Context): String = try {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensors = sm?.getSensorList(Sensor.TYPE_ALL)
        if (sensors.isNullOrEmpty()) {
            FALLBACK_NO_SENSORS
        } else {
            val profile = sensors
                .map { "${it.type}|${it.vendor}|${it.version}" }
                .sorted()
                .joinToString("\n")
            sha256(profile)
        }
    } catch (t: Throwable) {
        Log.w(TAG, "Layer E failed: ${t.javaClass.simpleName}: ${t.message}")
        FALLBACK_NO_SENSORS
    }

    // ─────────────────────────── helpers ───────────────────────────

    /**
     * The rule both identifier-bearing layers follow: a real value is
     * emitted as its SHA-256 digest, an absent one as the layer's plain
     * sentinel.
     *
     * Internal + extracted so the rule is unit-testable without Android
     * (FirebaseInstallations and Settings.Secure are not available on the
     * JVM), matching how the project handles other pure contracts
     * (BatteryChannelSanitizer, CategoryPresentation, CpuUsageProvenance).
     *
     * Sentinels are deliberately NOT hashed, and this is load-bearing on
     * the server, not a readability preference: `findDeviceProfile` skips a
     * stage when `stage.value === stage.sentinel`. Hash the sentinel and
     * that comparison stops matching, so the lookup degrades into an
     * equality query on "this device has no identifier" — which links
     * unrelated devices to each other. Layer A and Layer E already follow
     * the same rule (FALLBACK_NO_KEYSTORE / FALLBACK_NO_SENSORS stay
     * plain); this only brings B and C into line.
     */
    internal fun hashedOrSentinel(value: String?, sentinel: String): String =
        if (value.isNullOrBlank()) sentinel else sha256(value)

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return bytesToHex(digest.digest(input.toByteArray(Charsets.UTF_8)))
    }

    private fun bytesToHex(bytes: ByteArray): String =
        bytes.joinToString("") { "%02x".format(it) }

    @Suppress("DEPRECATION")
    private fun getScreenSize(context: Context): String = try {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        wm.defaultDisplay.getMetrics(metrics)
        "${metrics.widthPixels}x${metrics.heightPixels}"
    } catch (e: Exception) {
        "unknown"
    }

    private fun totalRamBytes(context: Context): Long = try {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        if (am == null) {
            0L
        } else {
            val info = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
            info.totalMem
        }
    } catch (t: Throwable) {
        0L
    }

    private fun totalStorageBytes(): Long = try {
        StatFs(Environment.getDataDirectory().absolutePath).totalBytes
    } catch (t: Throwable) {
        0L
    }
}
