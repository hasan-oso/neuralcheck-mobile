package net.neuralcheck.hardware.gps

import android.Manifest
import android.content.Context
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import net.neuralcheck.domain.model.TestResult
import net.neuralcheck.domain.model.TestStatus
import javax.inject.Inject
import kotlin.coroutines.resume

/** GPS tests L-01 to L-03. */
class GpsTests @Inject constructor(private val context: Context) {

    private companion object {
        /** L-02 observation window — what has always been the satellite-count window. */
        const val OBSERVATION_WINDOW_MS = 30_000L

        /** How often the GPS provider is asked for a fix during the window (1 Hz, the usual GNSS rate). */
        const val UPDATE_INTERVAL_MS = 1_000L
    }

    suspend fun runAll(): List<TestResult> = listOf(
        testGpsAvailable(),
        testSatellites(),
        testAccuracy()
    )

    private fun testGpsAvailable(): TestResult {
        val nameAr = context.getString(net.neuralcheck.R.string.test_l01_ar)
        val nameTr = context.getString(net.neuralcheck.R.string.test_l01_tr)
        val availableAr = context.getString(net.neuralcheck.R.string.value_available_ar)
        val disabledAr = context.getString(net.neuralcheck.R.string.value_disabled_ar)
        // LOCATION_SERVICE returning null is rare on real devices but can
        // happen on AOSP forks / restricted profiles; the safe cast keeps
        // the runner from crashing out of the GPS category entirely.
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return TestResult(
                "L-01", nameAr, nameTr, "gps",
                TestStatus.UNAVAILABLE, null, disabledAr, "", null
            )
        val gpsEnabled = try {
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
        } catch (_: SecurityException) {
            // Android 14 tightened LocationManager probes — providers list
            // may require ACCESS_COARSE_LOCATION on some OEM builds.
            false
        } catch (_: Exception) {
            false
        }
        return TestResult(
            "L-01", nameAr, nameTr, "gps",
            if (gpsEnabled) TestStatus.PASS else TestStatus.FAIL,
            null,
            if (gpsEnabled) availableAr else disabledAr,
            "", null
        )
    }

    /** `internal` so a JVM test can drive it with a mocked LocationManager (fieldsVersion 68). */
    internal suspend fun testSatellites(): TestResult {
        val hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PermissionChecker.PERMISSION_GRANTED

        val nameAr = context.getString(net.neuralcheck.R.string.test_l02_ar)
        val nameTr = context.getString(net.neuralcheck.R.string.test_l02_tr)
        val permissionDeniedAr = context.getString(net.neuralcheck.R.string.gps_permission_denied)
        val gpsDisabledAr = context.getString(net.neuralcheck.R.string.gps_disabled)
        val indoorHintAr = context.getString(net.neuralcheck.R.string.gps_indoor_hint)
        // Order 2026-09-30 (أمر «مسار الشريك...»، البند ١): registerGnssStatusCallback
        // failing (below) is NOT a permission denial — hasPermission is
        // already confirmed true by this point. Separate string from
        // gps_permission_denied so this branch describes what actually
        // happened instead of a false permission claim.
        val satelliteDataUnreadableAr = context.getString(net.neuralcheck.R.string.gps_satellite_data_unreadable)
        val satelliteUnit = context.getString(net.neuralcheck.R.string.unit_satellite)

        if (!hasPermission) {
            return TestResult(
                "L-02", nameAr, nameTr, "gps",
                TestStatus.NOT_TESTED, null, "N/A", "", permissionDeniedAr
            )
        }

        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return TestResult(
                "L-02", nameAr, nameTr, "gps",
                TestStatus.UNAVAILABLE, null, "0", satelliteUnit, gpsDisabledAr
            )
        val gpsEnabled = try {
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
        } catch (_: Exception) { false }
        if (!gpsEnabled) {
            return TestResult(
                "L-02", nameAr, nameTr, "gps",
                TestStatus.FAIL, null, "0", satelliteUnit, gpsDisabledAr,
                measurements = L02Measurements.providerOnly(gpsProviderEnabled = false)
            )
        }

        // fieldsVersion 68 (T-11, S-14, S-34, N-15): the window is observed with
        // the GNSS engine actually RUNNING (requestLocationUpdates on GPS for the
        // whole window — before, only a status callback was registered and nothing
        // started the engine), and the numbers go out raw in measurements. The
        // order L-02 before L-03 stays.
        val observation = GnssObservation()

        val callback = object : GnssStatus.Callback() {
            override fun onSatelliteStatusChanged(status: GnssStatus) {
                val count = status.satelliteCount
                var used = 0
                for (i in 0 until count) {
                    if (status.usedInFix(i)) used++
                }
                // the LAST callback wins (not the maximum): see GnssObservation
                observation.onSatelliteStatus(count, used)
            }

            override fun onFirstFix(ttffMillis: Int) {
                observation.onFirstFix(ttffMillis)
            }
        }

        val locationListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                observation.onLocationUpdate()
            }

            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) = Unit
            override fun onProviderEnabled(provider: String) = Unit
            override fun onProviderDisabled(provider: String) = Unit
        }

        val handler = Handler(Looper.getMainLooper())
        // Both register and unregister can throw SecurityException on
        // Android 14+ if the FINE permission is revoked between our check
        // above and the actual call (or if Knox policy blocks GNSS access).
        // Either crash here would propagate to the runner's safeRun and
        // wipe every later L-* test from the report, so we swallow.
        var callbackState: String
        val registered = try {
            lm.registerGnssStatusCallback(callback, handler)
            callbackState = L02Measurements.CALLBACK_REGISTERED
            true
        } catch (t: Throwable) {
            android.util.Log.w("NeuralCheck", "registerGnssStatusCallback failed: ${t.message}")
            callbackState = L02Measurements.failed(L02Measurements.CALLBACK_REGISTRATION_FAILED_PREFIX, t)
            false
        }

        var updatesState = L02Measurements.UPDATES_NOT_REQUESTED_CALLBACK_REGISTRATION_FAILED
        var permissionRevoked = false
        if (registered) {
            // GPS location updates for the whole window (S-14). A SecurityException
            // here is the permission being revoked between our check and this call:
            // it is declared as a state, it does not escape and wipe the later tests.
            updatesState = try {
                lm.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER, UPDATE_INTERVAL_MS, 0f, locationListener, Looper.getMainLooper()
                )
                L02Measurements.UPDATES_REQUESTED
            } catch (t: SecurityException) {
                permissionRevoked = true
                android.util.Log.w("NeuralCheck", "requestLocationUpdates denied: ${t.message}")
                L02Measurements.failed(L02Measurements.UPDATES_REQUEST_FAILED_PREFIX, t)
            } catch (t: Throwable) {
                android.util.Log.w("NeuralCheck", "requestLocationUpdates failed: ${t.message}")
                L02Measurements.failed(L02Measurements.UPDATES_REQUEST_FAILED_PREFIX, t)
            }
        }
        try {
            if (registered && !permissionRevoked) delay(OBSERVATION_WINDOW_MS)
        } finally {
            if (registered) {
                try { lm.removeUpdates(locationListener) } catch (_: Throwable) {}
                try { lm.unregisterGnssStatusCallback(callback) } catch (_: Throwable) {}
            }
        }

        val observed = observation.snapshot()
        val measurements = L02Measurements.build(
            gpsProviderEnabled = true,
            gnssCallbackState = callbackState,
            locationUpdatesState = updatesState,
            observed = observed
        )

        if (!registered) {
            return TestResult(
                "L-02", nameAr, nameTr, "gps",
                TestStatus.NOT_TESTED, null, "N/A", satelliteUnit, satelliteDataUnreadableAr,
                measurements = measurements
            )
        }
        if (permissionRevoked) {
            return TestResult(
                "L-02", nameAr, nameTr, "gps",
                TestStatus.NOT_TESTED, null, "N/A", satelliteUnit, permissionDeniedAr,
                measurements = measurements
            )
        }

        // The status and the textual rawValue are what they have always been
        // (S-34: no new state name comes from the app); the raw numbers are in
        // measurements, where the server names the state ("engine did not run"
        // = satelliteStatusCallbacks 0, vs "0 satellites" = callbacks > 0, visible 0).
        val verdict = L02Verdict.of(observed.visible, observed.used)
        return TestResult(
            "L-02", nameAr, nameTr, "gps",
            verdict.status, verdict.score, verdict.rawValue, satelliteUnit,
            if (verdict.noSatellitesSeen) indoorHintAr else null,
            measurements = measurements
        )
    }

    private suspend fun testAccuracy(): TestResult {
        val hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PermissionChecker.PERMISSION_GRANTED

        val nameAr = context.getString(net.neuralcheck.R.string.test_l03_ar)
        val nameTr = context.getString(net.neuralcheck.R.string.test_l03_tr)
        val permissionDeniedAr = context.getString(net.neuralcheck.R.string.gps_permission_denied)
        val gpsDisabledAr = context.getString(net.neuralcheck.R.string.gps_disabled)
        val noSignalAr = context.getString(net.neuralcheck.R.string.gps_no_signal)
        val meterUnit = context.getString(net.neuralcheck.R.string.unit_meter_short)

        if (!hasPermission) {
            return TestResult(
                "L-03", nameAr, nameTr, "gps",
                TestStatus.NOT_TESTED, null, "N/A", "", permissionDeniedAr
            )
        }

        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return TestResult(
                "L-03", nameAr, nameTr, "gps",
                TestStatus.UNAVAILABLE, null, "N/A", meterUnit, gpsDisabledAr
            )
        val gpsEnabled = try {
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
        } catch (_: Exception) { false }
        if (!gpsEnabled) {
            return TestResult(
                "L-03", nameAr, nameTr, "gps",
                TestStatus.FAIL, null, "N/A", meterUnit, gpsDisabledAr
            )
        }

        val location: Location? = withTimeoutOrNull(30_000L) {
            suspendCancellableCoroutine { cont ->
                val listener = object : LocationListener {
                    override fun onLocationChanged(loc: Location) {
                        lm.removeUpdates(this)
                        if (cont.isActive) cont.resume(loc)
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) = Unit
                    override fun onProviderEnabled(provider: String) = Unit
                    override fun onProviderDisabled(provider: String) = Unit
                }

                @Suppress("DEPRECATION")
                try {
                    lm.requestSingleUpdate(LocationManager.GPS_PROVIDER, listener, Looper.getMainLooper())
                } catch (_: SecurityException) {
                    if (cont.isActive) cont.resume(null)
                    return@suspendCancellableCoroutine
                }

                cont.invokeOnCancellation { lm.removeUpdates(listener) }
            }
        }

        if (location == null) {
            return TestResult(
                "L-03", nameAr, nameTr, "gps",
                TestStatus.FAIL, null, "N/A", meterUnit,
                noSignalAr
            )
        }

        val accuracyMeters = location.accuracy
        val status = when {
            accuracyMeters <= 10f -> TestStatus.PASS
            accuracyMeters <= 30f -> TestStatus.WARNING
            else -> TestStatus.FAIL
        }
        val score = (1f - (accuracyMeters / 50f)).coerceIn(0f, 1f) * 100f
        return TestResult(
            "L-03", nameAr, nameTr, "gps",
            status, score, String.format(java.util.Locale.US, "%.1f", accuracyMeters), meterUnit, null
        )
    }
}
