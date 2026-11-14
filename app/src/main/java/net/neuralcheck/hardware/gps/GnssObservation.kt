package net.neuralcheck.hardware.gps

import net.neuralcheck.domain.model.TestStatus
import net.neuralcheck.util.ReasonText

/**
 * fieldsVersion 68 (ت-11, S-14, S-34, N-15; order of batch 3 part 2-a): what the
 * L-02 satellite test OBSERVED during its 30-second window, as raw numbers.
 *
 * "The app sends the numbers; the server names the state" (S-34): a device whose
 * GNSS engine never ran and a device that saw 0 satellites are told apart not by
 * a status the app invents but by [Snapshot.callbacks] — 0 callbacks means no
 * satellite-status callback ever arrived, so `visibleSatellites` and
 * `usedSatellites` are ABSENT, not 0.
 *
 * Thread-safety: the GNSS callback and the location listener run on the main
 * looper, [snapshot] is read from the test coroutine — one monitor.
 */
class GnssObservation {

    private val lock = Any()
    private var callbacks = 0
    private var visible: Int? = null
    private var used: Int? = null
    private var ttffMs: Int? = null
    private var locationUpdates = 0

    /** One `GnssStatus.Callback.onSatelliteStatusChanged`: the LAST call wins (not the maximum). */
    fun onSatelliteStatus(visibleCount: Int, usedCount: Int) {
        synchronized(lock) {
            callbacks++
            visible = visibleCount
            used = usedCount
        }
    }

    /** `GnssStatus.Callback.onFirstFix(ttffMillis)`; only the first one is kept. */
    fun onFirstFix(ttffMillis: Int) {
        synchronized(lock) { if (ttffMs == null) ttffMs = ttffMillis }
    }

    /** One `LocationListener.onLocationChanged`. */
    fun onLocationUpdate() {
        synchronized(lock) { locationUpdates++ }
    }

    fun snapshot(): Snapshot = synchronized(lock) {
        Snapshot(callbacks, visible, used, ttffMs, locationUpdates)
    }

    /**
     * @property callbacks how many satellite-status callbacks arrived.
     * @property visible `satelliteCount` of the LAST callback; `null` when none arrived.
     * @property used satellites used in the fix in the LAST callback; `null` when none arrived.
     * @property ttffMs time to first fix as the platform reported it; `null` = `onFirstFix` was not called.
     * @property locationUpdates how many location updates arrived.
     */
    data class Snapshot(
        val callbacks: Int,
        val visible: Int?,
        val used: Int?,
        val ttffMs: Int?,
        val locationUpdates: Int
    )
}

/**
 * The raw measurements of L-02 (`TestResult.measurements`, carried by the Room
 * column added in the batch's first part). One place for the key and state
 * literals (rule 24); the producer ([GpsTests]) and the tests use them.
 *
 * Contract names (N-15, approved): [VISIBLE_SATELLITES] · [USED_SATELLITES] ·
 * [TIME_TO_FIRST_FIX_MS] · [SATELLITE_STATUS_CALLBACKS] · [LOCATION_UPDATE_COUNT].
 * Added by the agent ("the explicit-absence names that are needed", N-15) and
 * APPROVED by the user on 2026-10-08 (docs/FV68_CONTRACT.md section 7-b, item 7;
 * the approval of the nine items is quoted there, "ومفاتيح L-02"):
 * [GPS_PROVIDER_ENABLED] · [GNSS_CALLBACK_STATE] · [LOCATION_UPDATES_STATE] ·
 * [TIME_TO_FIRST_FIX_STATE]. (This comment said "not yet approved" until batch
 * 1.0.12; the golden samples now show all nine keys from the real producer.)
 */
object L02Measurements {
    const val VISIBLE_SATELLITES = "visibleSatellites"
    const val USED_SATELLITES = "usedSatellites"
    const val TIME_TO_FIRST_FIX_MS = "timeToFirstFixMs"
    const val SATELLITE_STATUS_CALLBACKS = "satelliteStatusCallbacks"
    const val LOCATION_UPDATE_COUNT = "locationUpdateCount"

    const val GPS_PROVIDER_ENABLED = "gpsProviderEnabled"
    const val GNSS_CALLBACK_STATE = "gnssCallbackState"
    const val LOCATION_UPDATES_STATE = "locationUpdatesState"
    const val TIME_TO_FIRST_FIX_STATE = "timeToFirstFixState"

    // gnssCallbackState
    const val CALLBACK_REGISTERED = "registered"
    const val CALLBACK_REGISTRATION_FAILED_PREFIX = "registration_failed:"

    // locationUpdatesState
    const val UPDATES_REQUESTED = "requested"
    const val UPDATES_REQUEST_FAILED_PREFIX = "request_failed:"

    /**
     * Location updates were never asked for because the GNSS status callback could not be registered (the engine is not
     * started without it). The contract lists this value (docs/FV68_CONTRACT.md section 7-b, item 7). One source (rule 24):
     * until batch 1.0.12 it was a literal inside `GpsTests.testSatellites`; `L02StateLiteralsGuardTest` keeps it here.
     */
    const val UPDATES_NOT_REQUESTED_CALLBACK_REGISTRATION_FAILED = "not_requested:gnss_callback_registration_failed"

    // timeToFirstFixState
    const val TTFF_MEASURED = "measured"
    const val TTFF_ABSENT_NO_FIRST_FIX = "absent:no_first_fix_in_window"

    /** `registration_failed:<message>` / `request_failed:<message>` — the message TEXT (rule 21), the state capped at 200. */
    fun failed(prefix: String, t: Throwable): String = ReasonText.cap(prefix + ReasonText.of(t))

    /**
     * The measurements of a test that got as far as registering: provider flag,
     * the two registration states, and what was observed. A number that was not
     * observed is ABSENT (not 0): `visibleSatellites` / `usedSatellites` without a
     * callback, `timeToFirstFixMs` without a first fix (its state says why).
     *
     * @param gnssCallbackState `registered` | `registration_failed:<message>`
     * @param locationUpdatesState `requested` | `request_failed:<message>`
     */
    fun build(
        gpsProviderEnabled: Boolean,
        gnssCallbackState: String,
        locationUpdatesState: String,
        observed: GnssObservation.Snapshot
    ): Map<String, Any?> = buildMap {
        put(GPS_PROVIDER_ENABLED, gpsProviderEnabled)
        put(GNSS_CALLBACK_STATE, gnssCallbackState)
        put(LOCATION_UPDATES_STATE, locationUpdatesState)
        put(SATELLITE_STATUS_CALLBACKS, observed.callbacks)
        observed.visible?.let { put(VISIBLE_SATELLITES, it) }
        observed.used?.let { put(USED_SATELLITES, it) }
        put(LOCATION_UPDATE_COUNT, observed.locationUpdates)
        if (observed.ttffMs != null) {
            put(TIME_TO_FIRST_FIX_MS, observed.ttffMs)
            put(TIME_TO_FIRST_FIX_STATE, TTFF_MEASURED)
        } else {
            put(TIME_TO_FIRST_FIX_STATE, TTFF_ABSENT_NO_FIRST_FIX)
        }
    }

    /** Measurements of a test that stopped before registering anything: only whether the provider was enabled. */
    fun providerOnly(gpsProviderEnabled: Boolean): Map<String, Any?> =
        mapOf(GPS_PROVIDER_ENABLED to gpsProviderEnabled)
}

/**
 * The status L-02 has always given for what it observed — unchanged by
 * fieldsVersion 68 (S-34: no new state name comes from the app). Kept as a pure
 * function so the numbers it reads are the ones that also ship raw.
 */
object L02Verdict {

    class Verdict(
        val status: TestStatus,
        /** `null` when the verdict is FAIL for "no satellite seen at all". */
        val score: Float?,
        val rawValue: String,
        /** True when the test saw no satellite (the "indoors?" hint applies). */
        val noSatellitesSeen: Boolean
    )

    /**
     * @param visible the last callback's satellite count, or `null` when no
     *   callback arrived (treated, as before, like "none seen": `total == 0`).
     */
    fun of(visible: Int?, used: Int?): Verdict {
        val total = visible ?: 0
        val usedCount = used ?: 0
        if (total == 0) return Verdict(TestStatus.FAIL, null, "0", noSatellitesSeen = true)
        val status = when {
            usedCount >= 4 -> TestStatus.PASS
            usedCount >= 1 -> TestStatus.WARNING
            else -> TestStatus.FAIL
        }
        val score = (usedCount.toFloat() / 8f * 100f).coerceIn(0f, 100f)
        return Verdict(status, score, "$usedCount / $total", noSatellitesSeen = false)
    }
}
