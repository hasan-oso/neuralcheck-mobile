package net.neuralcheck.hardware.network

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.nfc.NfcAdapter
import android.os.Build
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import net.neuralcheck.domain.model.TestResult
import net.neuralcheck.domain.model.TestStatus
import javax.inject.Inject

/**
 * Network tests N-01 to N-13, mapped strictly to ADR section 3.8:
 *
 *  N-01 Baseband version        (critical — empty/null = dead modem)
 *  N-02 SIM 1 state
 *  N-03 SIM 2 state             (excluded on single-slot devices)
 *  N-04 Data network type
 *  N-05 Cellular signal strength (0-4)
 *  N-06 Operator name
 *  N-07 eSIM support
 *  N-08 Wi-Fi connectivity
 *  N-09 Wi-Fi link speed + freq
 *  N-10 Wi-Fi RSSI
 *  N-11 Bluetooth adapter
 *  N-12 Bluetooth LE feature
 *  N-13 NFC adapter
 *
 * Every test is defensively wrapped because TelephonyManager and WifiManager
 * can throw SecurityException on some OEM builds even with the documented
 * permission present.
 */
class NetworkTests @Inject constructor(private val context: Context) {

    fun runAll(): List<TestResult> = listOf(
        testBaseband(),
        testSim1State(),
        testSim2State(),
        testNetworkType(),
        testSignalStrength(),
        testOperatorName(),
        testEsim(),
        testWifiState(),
        testWifiLinkSpeed(),
        testWifiRssi(),
        testBluetooth(),
        testBluetoothLe(),
        testNfc()
    )

    // ---- N-01 Baseband version (ADR critical check) ----
    private fun testBaseband(): TestResult {
        val nameAr = context.getString(net.neuralcheck.R.string.test_n01_ar)
        val nameTr = context.getString(net.neuralcheck.R.string.test_n01_tr)
        val baseband = try { Build.getRadioVersion() } catch (_: Exception) { null }
        val status = if (!baseband.isNullOrBlank()) TestStatus.PASS else TestStatus.FAIL
        val unknownAr = context.getString(net.neuralcheck.R.string.value_unknown_ar)
        return TestResult("N-01", nameAr, nameTr, "cellular", status, null,
            baseband?.ifBlank { unknownAr } ?: unknownAr, "", null)
    }

    // ---- N-02 / N-03 SIM slot state ----
    @Suppress("DEPRECATION")
    private fun testSim1State(): TestResult = evaluateSim("N-02", slot = 0)

    @Suppress("DEPRECATION")
    private fun testSim2State(): TestResult {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        val slotCount = try { tm?.phoneCount ?: 1 } catch (_: Exception) { 1 }
        val nameAr = context.getString(net.neuralcheck.R.string.test_n03_ar)
        val nameTr = context.getString(net.neuralcheck.R.string.test_n03_tr)
        if (slotCount < 2) {
            val singleSlot = context.getString(net.neuralcheck.R.string.value_slot_single)
            return TestResult("N-03", nameAr, nameTr, "cellular",
                TestStatus.UNAVAILABLE, null, singleSlot, "", null)
        }
        return evaluateSim("N-03", slot = 1)
    }

    @Suppress("DEPRECATION")
    private fun evaluateSim(testId: String, slot: Int): TestResult {
        val nameAr = context.getString(
            if (testId == "N-02") net.neuralcheck.R.string.test_n02_ar else net.neuralcheck.R.string.test_n03_ar
        )
        val nameTr = context.getString(
            if (testId == "N-02") net.neuralcheck.R.string.test_n02_tr else net.neuralcheck.R.string.test_n03_tr
        )
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            ?: return TestResult(testId, nameAr, nameTr, "cellular",
                TestStatus.UNAVAILABLE, null, "N/A", "", null)

        val state = try {
            // getSimState(slotIndex) was introduced at API 26 — we target >=26,
            // so it's safe, but still guard against rare OEM throws.
            if (Build.VERSION.SDK_INT >= 26) tm.getSimState(slot) else tm.simState
        } catch (_: Exception) { TelephonyManager.SIM_STATE_UNKNOWN }

        val (status, rawValueRes) = when (state) {
            TelephonyManager.SIM_STATE_READY ->
                TestStatus.PASS to net.neuralcheck.R.string.value_ready_ar
            TelephonyManager.SIM_STATE_ABSENT ->
                TestStatus.UNAVAILABLE to net.neuralcheck.R.string.value_not_found_ar
            TelephonyManager.SIM_STATE_PIN_REQUIRED,
            TelephonyManager.SIM_STATE_PUK_REQUIRED ->
                TestStatus.WARNING to net.neuralcheck.R.string.value_unknown_ar
            TelephonyManager.SIM_STATE_NETWORK_LOCKED ->
                TestStatus.FAIL to net.neuralcheck.R.string.value_unknown_ar
            else -> TestStatus.WARNING to net.neuralcheck.R.string.value_unknown_ar
        }
        return TestResult(testId, nameAr, nameTr, "cellular", status, null,
            context.getString(rawValueRes), "", null)
    }

    // ---- N-04 data network type ----
    private fun testNetworkType(): TestResult {
        val nameAr = context.getString(net.neuralcheck.R.string.test_n04_ar)
        val nameTr = context.getString(net.neuralcheck.R.string.test_n04_tr)
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            ?: return TestResult("N-04", nameAr, nameTr, "cellular",
                TestStatus.UNAVAILABLE, null, "N/A", "", null)

        val hasPhonePerm = ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_PHONE_STATE
        ) == PermissionChecker.PERMISSION_GRANTED
        if (!hasPhonePerm) {
            return TestResult("N-04", nameAr, nameTr, "cellular",
                TestStatus.NOT_TESTED, null, "N/A", "", null)
        }
        val type = try { tm.dataNetworkType } catch (_: Exception) { TelephonyManager.NETWORK_TYPE_UNKNOWN }
        val label = when (type) {
            TelephonyManager.NETWORK_TYPE_NR -> "5G"
            TelephonyManager.NETWORK_TYPE_LTE -> "4G"
            TelephonyManager.NETWORK_TYPE_HSPAP,
            TelephonyManager.NETWORK_TYPE_HSPA,
            TelephonyManager.NETWORK_TYPE_HSDPA,
            TelephonyManager.NETWORK_TYPE_UMTS -> "3G"
            TelephonyManager.NETWORK_TYPE_EDGE,
            TelephonyManager.NETWORK_TYPE_GPRS,
            TelephonyManager.NETWORK_TYPE_CDMA,
            TelephonyManager.NETWORK_TYPE_1xRTT -> "2G"
            else -> "disconnected"
        }
        val status = if (label == "disconnected") TestStatus.UNAVAILABLE else TestStatus.PASS
        return TestResult("N-04", nameAr, nameTr, "cellular", status, null, label, "", null)
    }

    // ---- N-05 cellular signal strength (0-4) ----
    @Suppress("DEPRECATION")
    private fun testSignalStrength(): TestResult {
        val nameAr = context.getString(net.neuralcheck.R.string.test_n05_ar)
        val nameTr = context.getString(net.neuralcheck.R.string.test_n05_tr)
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            ?: return TestResult("N-05", nameAr, nameTr, "cellular",
                TestStatus.UNAVAILABLE, null, "N/A", "", null)

        // TelephonyManager.getSignalStrength() was added in API 28 but our min
        // is 26. Gate the call and fall back to UNAVAILABLE on 26/27 rather
        // than crashing.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return TestResult("N-05", nameAr, nameTr, "cellular",
                TestStatus.UNAVAILABLE, null, "N/A", "", null)
        }

        return try {
            val level = tm.signalStrength?.level ?: -1
            val status = when {
                level >= 3 -> TestStatus.PASS
                level >= 1 -> TestStatus.WARNING
                level == 0 -> TestStatus.FAIL
                else -> TestStatus.UNAVAILABLE
            }
            TestResult("N-05", nameAr, nameTr, "cellular", status,
                if (level >= 0) level.toFloat() else null,
                if (level >= 0) "$level/4" else "N/A", "", null)
        } catch (_: Exception) {
            TestResult("N-05", nameAr, nameTr, "cellular",
                TestStatus.NOT_TESTED, null, "N/A", "", null)
        }
    }

    // ---- N-06 operator name ----
    private fun testOperatorName(): TestResult {
        val nameAr = context.getString(net.neuralcheck.R.string.test_n06_ar)
        val nameTr = context.getString(net.neuralcheck.R.string.test_n06_tr)
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            ?: return TestResult("N-06", nameAr, nameTr, "cellular",
                TestStatus.UNAVAILABLE, null, "N/A", "", null)
        val operator = try { tm.networkOperatorName } catch (_: Exception) { "" } ?: ""
        val unknownAr = context.getString(net.neuralcheck.R.string.value_unknown_ar)
        val simAbsent = try { tm.simState == TelephonyManager.SIM_STATE_ABSENT } catch (_: Exception) { false }
        val status = when {
            operator.isNotBlank() -> TestStatus.PASS
            simAbsent -> TestStatus.UNAVAILABLE
            else -> TestStatus.WARNING
        }
        return TestResult("N-06", nameAr, nameTr, "cellular", status, null,
            operator.ifBlank { unknownAr }, "", null)
    }

    // ---- N-07 eSIM support ----
    private fun testEsim(): TestResult {
        val nameAr = context.getString(net.neuralcheck.R.string.test_n07_ar)
        val nameTr = context.getString(net.neuralcheck.R.string.test_n07_tr)
        val hasEsim = if (Build.VERSION.SDK_INT >= 28) {
            try {
                val em = context.getSystemService(Context.EUICC_SERVICE) as? android.telephony.euicc.EuiccManager
                em?.isEnabled ?: false
            } catch (_: Exception) { false }
        } else false
        val supportedAr = context.getString(net.neuralcheck.R.string.value_supported_ar)
        val unsupportedAr = context.getString(net.neuralcheck.R.string.value_unsupported_ar)
        return TestResult("N-07", nameAr, nameTr, "cellular", TestStatus.PASS, null,
            if (hasEsim) supportedAr else unsupportedAr, "", null)
    }

    // ---- N-08 Wi-Fi connectivity ----
    private fun testWifiState(): TestResult {
        val nameAr = context.getString(net.neuralcheck.R.string.test_n08_ar)
        val nameTr = context.getString(net.neuralcheck.R.string.test_n08_tr)
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return TestResult("N-08", nameAr, nameTr, "wireless",
                TestStatus.UNAVAILABLE, null, "N/A", "", null)
        val caps = try { cm.getNetworkCapabilities(cm.activeNetwork) } catch (_: Exception) { null }
        val connected = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val enabled = try { wm?.isWifiEnabled == true } catch (_: Exception) { false }
        val connectedAr = context.getString(net.neuralcheck.R.string.value_connected_ar)
        val enabledAr = context.getString(net.neuralcheck.R.string.value_enabled_ar)
        val disabledAr = context.getString(net.neuralcheck.R.string.value_disabled_ar)
        val status = when {
            connected -> TestStatus.PASS
            enabled -> TestStatus.WARNING
            else -> TestStatus.UNAVAILABLE
        }
        return TestResult("N-08", nameAr, nameTr, "wireless", status, null,
            when { connected -> connectedAr; enabled -> enabledAr; else -> disabledAr }, "", null)
    }

    // ---- N-09 Wi-Fi link speed + frequency ----
    @Suppress("DEPRECATION")
    private fun testWifiLinkSpeed(): TestResult {
        val nameAr = context.getString(net.neuralcheck.R.string.test_n09_ar)
        val nameTr = context.getString(net.neuralcheck.R.string.test_n09_tr)
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            ?: return TestResult("N-09", nameAr, nameTr, "wireless",
                TestStatus.UNAVAILABLE, null, "N/A", "", null)
        return try {
            val info = wm.connectionInfo
            val linkMbps = info?.linkSpeed ?: -1
            val freq = info?.frequency ?: 0
            val band = if (freq > 4900) "5 GHz" else if (freq > 0) "2.4 GHz" else ""
            val status = when {
                linkMbps <= 0 -> TestStatus.UNAVAILABLE
                else -> TestStatus.PASS
            }
            val rawValue = if (linkMbps > 0) "$linkMbps" else "N/A"
            val display = if (band.isNotEmpty()) "$linkMbps Mbps, $band" else rawValue
            TestResult("N-09", nameAr, nameTr, "wireless", status,
                if (linkMbps > 0) linkMbps.toFloat() else null,
                display, "Mbps", null)
        } catch (_: Exception) {
            TestResult("N-09", nameAr, nameTr, "wireless",
                TestStatus.NOT_TESTED, null, "N/A", "", null)
        }
    }

    // ---- N-10 Wi-Fi RSSI ----
    @Suppress("DEPRECATION")
    private fun testWifiRssi(): TestResult {
        val nameAr = context.getString(net.neuralcheck.R.string.test_n10_ar)
        val nameTr = context.getString(net.neuralcheck.R.string.test_n10_tr)
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            ?: return TestResult("N-10", nameAr, nameTr, "wireless",
                TestStatus.UNAVAILABLE, null, "N/A", "", null)
        return try {
            val rssi = wm.connectionInfo?.rssi ?: 0
            if (rssi == 0 || rssi == -127) {
                return TestResult("N-10", nameAr, nameTr, "wireless",
                    TestStatus.UNAVAILABLE, null, "N/A", "dBm", null)
            }
            // Thresholds realigned 2026-05-14 to standard 802.11 reception
            // bands (NetSpot / Wi-Fi Alliance / industry practice):
            //   ≥ -70 dBm        : excellent → good signal, reliable for
            //                      all common uses (browsing, streaming,
            //                      calls). PASS.
            //   -70 to -80 dBm   : fair → usable for browsing, may have
            //                      issues with HD video / VoIP. WARNING.
            //   <  -80 dBm       : poor → unreliable connection. FAIL.
            //
            // Previous bar (-50 / -70) was tester-induced: a healthy
            // A51 at -57 dBm (well within standard "good" range) was
            // flagging WARNING and degrading the wireless category
            // grade for no actual reception problem. -57 dBm is the
            // signal level a phone right beside a 5 GHz router typically
            // reads — anything below that on a normal home connection
            // is the norm.
            val status = when {
                rssi >= -70 -> TestStatus.PASS
                rssi >= -80 -> TestStatus.WARNING
                else -> TestStatus.FAIL
            }
            TestResult("N-10", nameAr, nameTr, "wireless", status, rssi.toFloat(), "$rssi", "dBm", null)
        } catch (_: Exception) {
            TestResult("N-10", nameAr, nameTr, "wireless",
                TestStatus.NOT_TESTED, null, "N/A", "", null)
        }
    }

    // ---- N-11 Bluetooth adapter ----
    private fun testBluetooth(): TestResult {
        val nameAr = context.getString(net.neuralcheck.R.string.test_n11_ar)
        val nameTr = context.getString(net.neuralcheck.R.string.test_n11_tr)
        val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = bm?.adapter
        val unavailableAr = context.getString(net.neuralcheck.R.string.value_unavailable_ar)
        val enabledAr = context.getString(net.neuralcheck.R.string.value_enabled_ar)
        val disabledAr = context.getString(net.neuralcheck.R.string.value_disabled_ar)
        return when {
            adapter == null -> TestResult("N-11", nameAr, nameTr, "wireless",
                TestStatus.FAIL, null, unavailableAr, "", null)
            adapter.isEnabled -> TestResult("N-11", nameAr, nameTr, "wireless",
                TestStatus.PASS, null, enabledAr, "", null)
            else -> TestResult("N-11", nameAr, nameTr, "wireless",
                TestStatus.PASS, null, disabledAr, "", null)
        }
    }

    // ---- N-12 Bluetooth LE feature ----
    private fun testBluetoothLe(): TestResult {
        val nameAr = context.getString(net.neuralcheck.R.string.test_n12_ar)
        val nameTr = context.getString(net.neuralcheck.R.string.test_n12_tr)
        val hasBle = context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
        val supportedAr = context.getString(net.neuralcheck.R.string.value_supported_ar)
        val unsupportedAr = context.getString(net.neuralcheck.R.string.value_unsupported_ar)
        return TestResult("N-12", nameAr, nameTr, "wireless",
            if (hasBle) TestStatus.PASS else TestStatus.UNAVAILABLE, null,
            if (hasBle) supportedAr else unsupportedAr, "", null)
    }

    // ---- N-13 NFC adapter ----
    private fun testNfc(): TestResult {
        val nameAr = context.getString(net.neuralcheck.R.string.test_n13_ar)
        val nameTr = context.getString(net.neuralcheck.R.string.test_n13_tr)
        val nfc = NfcAdapter.getDefaultAdapter(context)
        val unsupportedAr = context.getString(net.neuralcheck.R.string.value_unsupported_ar)
        val enabledAr = context.getString(net.neuralcheck.R.string.value_enabled_ar)
        val disabledAr = context.getString(net.neuralcheck.R.string.value_disabled_ar)
        return when {
            nfc == null -> TestResult("N-13", nameAr, nameTr, "wireless",
                TestStatus.UNAVAILABLE, null, unsupportedAr, "", null)
            nfc.isEnabled -> TestResult("N-13", nameAr, nameTr, "wireless",
                TestStatus.PASS, null, enabledAr, "", null)
            else -> TestResult("N-13", nameAr, nameTr, "wireless",
                TestStatus.PASS, null, disabledAr, "", null)
        }
    }
}
