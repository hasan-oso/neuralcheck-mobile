package net.neuralcheck.hardware.gps

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.GnssStatus
import android.location.LocationListener
import android.location.LocationManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.neuralcheck.data.local.db.converter.MeasurementsJson
import net.neuralcheck.domain.model.TestStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

/**
 * fieldsVersion 68 (ت-11, S-14, S-34, N-15): L-02 run for real (the real
 * [GpsTests.testSatellites], a mocked LocationManager and virtual time):
 * the GNSS engine is asked to RUN for the whole window (requestLocationUpdates
 * on GPS — before, only a status callback was registered), the observation is
 * shipped as raw numbers in `measurements`, a permission revoked mid-test is a
 * declared state not an exception, and the listeners are always taken off.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GpsTestsL02Test {

    private val lm: LocationManager = mock {
        on { isProviderEnabled(LocationManager.GPS_PROVIDER) } doReturn true
    }

    private val context: Context = mock {
        on { getSystemService(Context.LOCATION_SERVICE) } doReturn lm
        on { getString(any()) } doReturn "txt"
        on { checkPermission(eq(Manifest.permission.ACCESS_FINE_LOCATION), any(), any()) } doReturn PackageManager.PERMISSION_GRANTED
    }

    private fun gnss(visible: Int, used: Int): GnssStatus = mock {
        on { satelliteCount } doReturn visible
        on { usedInFix(any()) } doReturn false
        for (i in 0 until used) on { usedInFix(i) } doReturn true
    }

    @Test
    fun `the engine is asked to run for the whole window and the raw numbers ship in measurements`() = runTest {
        val cbCaptor = argumentCaptor<GnssStatus.Callback>()
        val listenerCaptor = argumentCaptor<LocationListener>()

        var result: net.neuralcheck.domain.model.TestResult? = null
        val job = launch { result = GpsTests(context).testSatellites() }
        runCurrent()

        // registered and the GPS provider asked for updates, before any waiting finishes
        verify(lm).registerGnssStatusCallback(cbCaptor.capture(), any())
        verify(lm).requestLocationUpdates(eq(LocationManager.GPS_PROVIDER), eq(1_000L), eq(0f), listenerCaptor.capture(), anyOrNull())

        // 5 s in: a first fix and a status callback (the BEST sky of the window: 11 visible, 6 used);
        // 20 s in: a later callback with a worse one (9 visible, 3 used), and two location updates
        advanceTimeBy(5_000)
        cbCaptor.firstValue.onFirstFix(8_400)
        cbCaptor.firstValue.onSatelliteStatusChanged(gnss(visible = 11, used = 6))
        advanceTimeBy(15_000)
        cbCaptor.firstValue.onSatelliteStatusChanged(gnss(visible = 9, used = 3))
        listenerCaptor.firstValue.onLocationChanged(mock<android.location.Location>())
        listenerCaptor.firstValue.onLocationChanged(mock<android.location.Location>())
        advanceTimeBy(15_000)
        job.join()

        val r = result!!
        // the verdict is unchanged: the LAST callback (3 used of 9) decides, NOT the maximum seen (6 of 11)
        assertEquals("3 / 9", r.rawValue)
        assertEquals(TestStatus.WARNING, r.status)

        val m = r.measurements!!
        assertEquals("the last callback's count, not the maximum", 9, m["visibleSatellites"])
        assertEquals(3, m["usedSatellites"])
        assertEquals(2, m["satelliteStatusCallbacks"])
        assertEquals(2, m["locationUpdateCount"])
        assertEquals(8_400, m["timeToFirstFixMs"])
        assertEquals("measured", m["timeToFirstFixState"])
        assertEquals("registered", m["gnssCallbackState"])
        assertEquals("requested", m["locationUpdatesState"])
        assertEquals(true, m["gpsProviderEnabled"])

        // always taken off again
        verify(lm).removeUpdates(listenerCaptor.firstValue)
        verify(lm).unregisterGnssStatusCallback(cbCaptor.firstValue)
    }

    @Test
    fun `an engine that never reported is told apart from zero satellites - absent numbers vs a zero count`() = runTest {
        var silent: net.neuralcheck.domain.model.TestResult? = null
        val job = launch { silent = GpsTests(context).testSatellites() }
        runCurrent()
        advanceTimeBy(31_000)
        job.join()

        val s = silent!!
        val ms = s.measurements!!
        assertEquals(0, ms["satelliteStatusCallbacks"])
        assertFalse("no callback ever arrived: visible is ABSENT, not 0", ms.containsKey("visibleSatellites"))
        assertFalse(ms.containsKey("usedSatellites"))
        assertFalse(ms.containsKey("timeToFirstFixMs"))
        assertEquals("absent:no_first_fix_in_window", ms["timeToFirstFixState"])
        assertEquals(0, ms["locationUpdateCount"])
        // status and text unchanged: the old result for "nothing seen"
        assertEquals(TestStatus.FAIL, s.status)
        assertEquals("0", s.rawValue)

        // ...whereas a callback that DID arrive and saw no satellite is a real zero
        val cbCaptor = argumentCaptor<GnssStatus.Callback>()
        var seenNone: net.neuralcheck.domain.model.TestResult? = null
        val lm2: LocationManager = mock { on { isProviderEnabled(LocationManager.GPS_PROVIDER) } doReturn true }
        val ctx2: Context = mock {
            on { getSystemService(Context.LOCATION_SERVICE) } doReturn lm2
            on { getString(any()) } doReturn "txt"
            on { checkPermission(eq(Manifest.permission.ACCESS_FINE_LOCATION), any(), any()) } doReturn PackageManager.PERMISSION_GRANTED
        }
        val job2 = launch { seenNone = GpsTests(ctx2).testSatellites() }
        runCurrent()
        verify(lm2).registerGnssStatusCallback(cbCaptor.capture(), any())
        cbCaptor.firstValue.onSatelliteStatusChanged(gnss(visible = 0, used = 0))
        advanceTimeBy(31_000)
        job2.join()

        val ms2 = seenNone!!.measurements!!
        assertEquals(1, ms2["satelliteStatusCallbacks"])
        assertEquals("a real zero seen by a real callback", 0, ms2["visibleSatellites"])
        assertEquals(0, ms2["usedSatellites"])
    }

    @Test
    fun `permission revoked before the request is a declared state - not an exception, no 30 second wait`() = runTest {
        val lmDenied: LocationManager = mock {
            on { isProviderEnabled(LocationManager.GPS_PROVIDER) } doReturn true
            on { requestLocationUpdates(any<String>(), any<Long>(), any<Float>(), any<LocationListener>(), anyOrNull()) } doThrow
                SecurityException("ACCESS_FINE_LOCATION revoked")
        }
        val ctx: Context = mock {
            on { getSystemService(Context.LOCATION_SERVICE) } doReturn lmDenied
            on { getString(any()) } doReturn "denied text"
            on { checkPermission(eq(Manifest.permission.ACCESS_FINE_LOCATION), any(), any()) } doReturn PackageManager.PERMISSION_GRANTED
        }
        var r: net.neuralcheck.domain.model.TestResult? = null
        val job = launch { r = GpsTests(ctx).testSatellites() }
        runCurrent() // no time advanced: it must already be done
        assertTrue("no 30 s wait after a denial", job.isCompleted)

        assertEquals(TestStatus.NOT_TESTED, r!!.status)
        assertEquals("N/A", r!!.rawValue)
        val m = r!!.measurements!!
        assertEquals("request_failed:ACCESS_FINE_LOCATION revoked", m["locationUpdatesState"])
        assertEquals("registered", m["gnssCallbackState"])
        verify(lmDenied).unregisterGnssStatusCallback(any<GnssStatus.Callback>())
    }

    @Test
    fun `a failed callback registration does not start the engine and is stated with its message`() = runTest {
        val lmBroken: LocationManager = mock {
            on { isProviderEnabled(LocationManager.GPS_PROVIDER) } doReturn true
            on { registerGnssStatusCallback(any<GnssStatus.Callback>(), any()) } doThrow IllegalStateException("GNSS HAL not ready")
        }
        val ctx: Context = mock {
            on { getSystemService(Context.LOCATION_SERVICE) } doReturn lmBroken
            on { getString(any()) } doReturn "txt"
            on { checkPermission(eq(Manifest.permission.ACCESS_FINE_LOCATION), any(), any()) } doReturn PackageManager.PERMISSION_GRANTED
        }
        var r: net.neuralcheck.domain.model.TestResult? = null
        val job = launch { r = GpsTests(ctx).testSatellites() }
        runCurrent()
        assertTrue(job.isCompleted)

        assertEquals(TestStatus.NOT_TESTED, r!!.status)
        val m = r!!.measurements!!
        assertEquals("registration_failed:GNSS HAL not ready", m["gnssCallbackState"])
        assertEquals("not_requested:gnss_callback_registration_failed", m["locationUpdatesState"])
        verify(lmBroken, never()).requestLocationUpdates(any<String>(), any<Long>(), any<Float>(), any<LocationListener>(), anyOrNull())
    }

    @Test
    fun `a disabled GPS provider reports the raw fact and starts nothing`() = runTest {
        val lmOff: LocationManager = mock { on { isProviderEnabled(LocationManager.GPS_PROVIDER) } doReturn false }
        val ctx: Context = mock {
            on { getSystemService(Context.LOCATION_SERVICE) } doReturn lmOff
            on { getString(any()) } doReturn "txt"
            on { checkPermission(eq(Manifest.permission.ACCESS_FINE_LOCATION), any(), any()) } doReturn PackageManager.PERMISSION_GRANTED
        }
        var r: net.neuralcheck.domain.model.TestResult? = null
        val job = launch { r = GpsTests(ctx).testSatellites() }
        runCurrent()
        assertTrue(job.isCompleted)

        assertEquals(TestStatus.FAIL, r!!.status)
        assertEquals(mapOf<String, Any?>("gpsProviderEnabled" to false), r!!.measurements)
        verify(lmOff, never()).registerGnssStatusCallback(any<GnssStatus.Callback>(), any())
    }

    @Test
    fun `on the wire the L-02 raw is the compound map and a number that was not observed is an absent key`() {
        // production path: GpsTests -> Room column codec -> BehaviorFingerprintBuilder -> FirebaseAnalysisService's Gson
        val silent = L02Measurements.build(
            true, "registered", "requested",
            GnssObservation.Snapshot(callbacks = 0, visible = null, used = null, ttffMs = null, locationUpdates = 0)
        )
        val stored = MeasurementsJson.decode(MeasurementsJson.encode(silent))
        val report = net.neuralcheck.domain.model.ScanReport(
            scanId = "s", scanType = net.neuralcheck.domain.model.ScanType.GENERAL, linkedScanId = null,
            deviceInfo = net.neuralcheck.domain.model.DeviceInfo("fp", "M", "Mfr", "14", 34, "1080x2400", 8),
            scanMetadata = net.neuralcheck.domain.model.ScanMetadata(0L, 0L, 100, 95, 27f, 0L, true, null, "1.0.0"),
            functionalResults = listOf(
                net.neuralcheck.domain.model.TestResult(
                    "L-02", "L-02", "L-02", "gps", TestStatus.FAIL, null, "0", "u", null, measurements = stored
                )
            ),
            timeSeriesSummary = null, antiTamperFlags = emptyList(), technicianInput = null, peerInput = null
        )
        val fp = net.neuralcheck.data.remote.BehaviorFingerprintBuilder().build(report, timeSeries = null)
        val wire = com.google.gson.GsonBuilder().create().toJsonTree(fp).asJsonObject
            .getAsJsonArray("functionalResults")[0].asJsonObject.getAsJsonObject("measurements")

        assertEquals(0, wire.get("satelliteStatusCallbacks").asInt)
        assertFalse(wire.has("visibleSatellites"))
        assertFalse(wire.has("usedSatellites"))
        assertFalse(wire.has("timeToFirstFixMs"))
        assertEquals("absent:no_first_fix_in_window", wire.get("timeToFirstFixState").asString)
        assertEquals("requested", wire.get("locationUpdatesState").asString)
    }

    @Test
    fun `the measurements survive the Room column and are numbers of their own kind`() {
        val observed = GnssObservation.Snapshot(callbacks = 3, visible = 12, used = 7, ttffMs = 9_100, locationUpdates = 26)
        val measurements = L02Measurements.build(true, "registered", "requested", observed)

        val stored = MeasurementsJson.encode(measurements)!!
        val decoded = MeasurementsJson.decode(stored)!!

        // integers stay integers (Gson's default would turn them into 12.0), absent stays absent
        assertEquals(12L, decoded["visibleSatellites"])
        assertEquals(3L, decoded["satelliteStatusCallbacks"])
        assertEquals(9_100L, decoded["timeToFirstFixMs"])
        assertNull(decoded["noSuchKey"])
        assertTrue(decoded.containsKey("timeToFirstFixState"))
    }
}
