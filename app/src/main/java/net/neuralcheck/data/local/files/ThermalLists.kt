package net.neuralcheck.data.local.files

/**
 * fieldsVersion 68 (ت-03, ت-04, N-02..N-04, order 2026-10-08, batch 3 part 2-a):
 * the two SLOW thermal channels leave `v[]` and become lists of timestamped
 * readings in the footer (S-01: "no absence inside v[]"; S-03/S-04).
 *
 * Both lists use `t` = milliseconds since the scan started, the same frame as
 * `v[0]` (N-04: `SystemClock.elapsedRealtime() - scanStartTime`).
 */

/**
 * ONE call of `PowerManager.getThermalHeadroom(0)` (ت-03). [headroom] is the
 * platform's value AS RETURNED — no clamping, no rounding (values >= 1.0 mean
 * throttling territory; they are forwarded as they came). It is `null` exactly
 * when [state] is not [ThermalListStates.HEADROOM_OK]: `NaN` cannot be written
 * (Gson refuses it and the whole file / request would fail), so a NaN return
 * becomes an absent value with its own state (rule 22) instead of a number.
 *
 * [state] is the TEXT of what the call did (rule 21), one of
 * [ThermalListStates]: `ok` · `nan` · `exception:<message>` ·
 * `api_below_30` · `non_finite:<value>`.
 */
data class ThermalHeadroomReading(
    val t: Long,
    val headroom: Float?,
    val state: String
)

/**
 * One value of `PowerManager.currentThermalStatus` (ت-04): the raw integer
 * 0..6 (`NONE=0 … SHUTDOWN=6`), never a classification. The first element of
 * the list is a read taken when the listener was registered; every later one
 * is a callback of `OnThermalStatusChangedListener`.
 */
data class ThermalStatusEvent(
    val t: Long,
    val status: Int
)

/**
 * The state vocabulary of both lists. Every literal the reading/event state
 * is decided on lives here once (rule 24); the collector writes them and the
 * tests assert against them.
 *
 * Approved by the coordinator (N-02, N-03): [HEADROOM_OK] · [HEADROOM_NAN] ·
 * [EXCEPTION_PREFIX] · [API_BELOW_30] · [STATUS_OK] · [API_BELOW_29] ·
 * [LISTENER_REGISTRATION_FAILED_PREFIX]. Added by the app agent while
 * implementing, NOT yet approved (reported in the commit and the report):
 * [NON_FINITE_PREFIX] · [NO_READING_TAKEN] · [START_FAILED_PREFIX] ·
 * [INITIAL_READ_FAILED_PREFIX].
 */
object ThermalListStates {
    // -- per reading (thermalHeadroomReadings[].state) --
    const val HEADROOM_OK = "ok"
    const val HEADROOM_NAN = "nan"
    const val API_BELOW_30 = "api_below_30"
    const val EXCEPTION_PREFIX = "exception:"

    /** [new, not yet approved] the call returned +-Infinity (Gson cannot write it). */
    const val NON_FINITE_PREFIX = "non_finite:"

    // -- channel level (thermalHeadroomState) --
    /** The collector ran and recorded readings; each reading carries its own state. */
    const val CHANNEL_OK = "ok"

    /** [new, not yet approved] the collector was never able to schedule / run (text follows). */
    const val START_FAILED_PREFIX = "start_failed:"

    /** [new, not yet approved] the collector ran but ended before its first reading. */
    const val NO_READING_TAKEN = "no_reading_taken"

    // -- channel level (thermalStatusState) --
    const val STATUS_OK = "ok"
    const val API_BELOW_29 = "api_below_29"
    const val LISTENER_REGISTRATION_FAILED_PREFIX = "listener_registration_failed:"

    /** [new, not yet approved] the listener registered but the first read threw (text follows). */
    const val INITIAL_READ_FAILED_PREFIX = "initial_read_failed:"
}

/**
 * Everything the thermal-list collector hands to the footer at the end of a
 * scan (see ThermalListCollector).
 */
data class ThermalLists(
    val headroomReadings: List<ThermalHeadroomReading>,
    val headroomState: String,
    val statusEvents: List<ThermalStatusEvent>,
    val statusState: String
)
