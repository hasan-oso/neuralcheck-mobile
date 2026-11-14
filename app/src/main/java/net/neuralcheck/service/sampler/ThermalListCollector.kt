package net.neuralcheck.service.sampler

import android.os.SystemClock
import android.util.Log
import net.neuralcheck.data.local.files.ThermalHeadroomReading
import net.neuralcheck.data.local.files.ThermalListStates
import net.neuralcheck.data.local.files.ThermalLists
import net.neuralcheck.data.local.files.ThermalStatusEvent
import net.neuralcheck.service.collector.ThermalHeadroomRead
import net.neuralcheck.service.collector.ThermalPlatform
import net.neuralcheck.service.collector.ThermalStatusRegistration
import net.neuralcheck.util.ReasonText
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * fieldsVersion 68 (ت-03, ت-04): records the two slow thermal channels as
 * lists with timestamps, outside `v[]`.
 *
 *  - **headroom**: one `getThermalHeadroom(0)` call every [HEADROOM_PERIOD_MS]
 *    (S-35: "the call every 10 seconds", the first one at t≈0). Each call is a
 *    list element `{t, headroom, state}` whatever it returned — a value, a NaN,
 *    an exception with its message, "API below 30". Nothing is cached or
 *    pinned, so one transient failure is one element, not a verdict for the
 *    rest of the scan (S-35: "no pinning of «unsupported»").
 *  - **status**: `getCurrentThermalStatus()` read when the listener is
 *    registered (the first element), then one element per
 *    `OnThermalStatusChangedListener` callback (S-04).
 *
 * `t` is milliseconds since the scan started, the frame of `v[0]` (N-04):
 * `elapsedRealtime() - scanStartElapsedMs`.
 *
 * Thread-safety: the headroom tick runs on its own scheduler thread, status
 * callbacks on the listener executor, [snapshot] on the service thread — one
 * monitor guards every list.
 *
 * The governor guard (StressTestServiceV2.kt `readThermalHeadroom` every
 * second), the sampler's aggregator and the discovery probe keep their own
 * cached, pinned read (contract S-03: "the guard stays as it is"); this class
 * makes its own platform call. Whether the platform rate-limits across both is
 * NOT measured — if it answers `NaN` to a call that collides with the guard,
 * that element says `nan`, it is not hidden.
 */
class ThermalListCollector(
    private val platform: ThermalPlatform,
    private val elapsedRealtime: () -> Long = { SystemClock.elapsedRealtime() }
) {
    companion object {
        private const val TAG = "NeuralCheck"

        /** S-35: the call is made every 10 seconds. */
        const val HEADROOM_PERIOD_MS: Long = 10_000L
    }

    private val lock = Any()
    private var startMs = 0L
    private var started = false
    private var stopped = false

    private val headroomReadings = ArrayList<ThermalHeadroomReading>()
    private val statusEvents = ArrayList<ThermalStatusEvent>()
    private var headroomStartFailure: String? = null
    private var statusState: String = ThermalListStates.STATUS_OK

    private var scheduler: ScheduledExecutorService? = null
    private var statusExecutor: ExecutorService? = null
    private var registration: ThermalStatusRegistration? = null

    /**
     * Starts both channels. [scanStartElapsedMs] is the `elapsedRealtime()` the
     * scan (and `v[0]`) counts from. Never throws: a failure to start is
     * recorded as the channel's state.
     */
    fun start(scanStartElapsedMs: Long) = startInternal(scanStartElapsedMs, schedule = true)

    /** Test hook: everything [start] does except scheduling, so a test drives [tickHeadroom] by hand. */
    internal fun startWithoutScheduling(scanStartElapsedMs: Long) = startInternal(scanStartElapsedMs, schedule = false)

    private fun startInternal(scanStartElapsedMs: Long, schedule: Boolean) {
        synchronized(lock) {
            if (started) return
            started = true
            startMs = scanStartElapsedMs
            startStatusLocked()
            if (schedule) startHeadroomLocked()
        }
    }

    // ── status ─────────────────────────────────────────────────────────

    private fun startStatusLocked() {
        if (platform.sdkInt < 29) {
            statusState = ThermalListStates.API_BELOW_29
            return
        }
        var registrationError: String? = null
        try {
            val executor = Executors.newSingleThreadExecutor { r ->
                Thread(r, "NeuralCheck-ThermalStatus").apply { isDaemon = true }
            }
            statusExecutor = executor
            // The callbacks run on [executor] and take [lock] first, so none can
            // overtake the registration read below: the list starts with it.
            registration = platform.registerStatusListener(executor) { status -> onStatusChanged(status) }
        } catch (e: Exception) {
            registrationError = ReasonText.of(e)
        }
        var readError: String? = null
        try {
            // The first element is a read at registration time (S-04) — taken
            // even when the registration failed, so a device that refuses the
            // listener still states the status it was in at the start.
            statusEvents.add(ThermalStatusEvent(now(), platform.currentStatus()))
        } catch (e: Exception) {
            readError = ReasonText.of(e)
        }
        statusState = when {
            registrationError != null && readError != null -> ReasonText.cap(
                ThermalListStates.LISTENER_REGISTRATION_FAILED_PREFIX + registrationError +
                    " | " + ThermalListStates.INITIAL_READ_FAILED_PREFIX + readError
            )
            registrationError != null ->
                ReasonText.cap(ThermalListStates.LISTENER_REGISTRATION_FAILED_PREFIX + registrationError)
            readError != null ->
                ReasonText.cap(ThermalListStates.INITIAL_READ_FAILED_PREFIX + readError)
            else -> ThermalListStates.STATUS_OK
        }
    }

    private fun onStatusChanged(status: Int) {
        synchronized(lock) {
            if (stopped) return
            statusEvents.add(ThermalStatusEvent(now(), status))
        }
    }

    // ── headroom ───────────────────────────────────────────────────────

    private fun startHeadroomLocked() {
        try {
            val s = Executors.newSingleThreadScheduledExecutor { r ->
                Thread(r, "NeuralCheck-ThermalHeadroom").apply { isDaemon = true }
            }
            scheduler = s
            // A task that throws is never run again by a ScheduledExecutor, so
            // [tickHeadroom] catches everything itself.
            s.scheduleAtFixedRate({ tickHeadroom() }, 0L, HEADROOM_PERIOD_MS, TimeUnit.MILLISECONDS)
        } catch (e: Exception) {
            headroomStartFailure = ReasonText.of(e)
            Log.w(TAG, "ThermalListCollector: headroom scheduling failed: ${e.message}")
        }
    }

    /** One headroom call, recorded whatever it returned. Visible for tests. */
    internal fun tickHeadroom() {
        try {
            if (synchronized(lock) { stopped }) return
            val t = now()
            val read: ThermalHeadroomRead = try {
                platform.readHeadroom()
            } catch (e: Exception) {
                ThermalHeadroomRead(null, ThermalListStates.EXCEPTION_PREFIX + ReasonText.of(e))
            }
            synchronized(lock) {
                if (stopped) return
                headroomReadings.add(ThermalHeadroomReading(t, read.headroom, ReasonText.cap(read.state)))
            }
        } catch (t: Throwable) {
            Log.w(TAG, "ThermalListCollector: headroom tick failed: ${t.message}")
        }
    }

    // ── end of scan ────────────────────────────────────────────────────

    /** Stops both channels. Idempotent; never throws. */
    fun stop() {
        val toUnregister: ThermalStatusRegistration?
        synchronized(lock) {
            if (stopped) return
            stopped = true
            toUnregister = registration
            registration = null
        }
        runCatching { scheduler?.shutdownNow() }
        runCatching { toUnregister?.unregister() }
            .onFailure { Log.w(TAG, "ThermalListCollector: removing the status listener failed: ${it.message}") }
        runCatching { statusExecutor?.shutdownNow() }
    }

    /** The lists as they stand now (call [stop] first for the final ones). */
    fun snapshot(): ThermalLists = synchronized(lock) {
        ThermalLists(
            headroomReadings = headroomReadings.toList(),
            headroomState = headroomChannelState(),
            statusEvents = statusEvents.toList(),
            statusState = statusState
        )
    }

    private fun headroomChannelState(): String = when {
        headroomStartFailure != null ->
            ReasonText.cap(ThermalListStates.START_FAILED_PREFIX + headroomStartFailure)
        platform.sdkInt < 30 -> ThermalListStates.API_BELOW_30
        headroomReadings.isEmpty() -> ThermalListStates.NO_READING_TAKEN
        else -> ThermalListStates.CHANNEL_OK
    }

    private fun now(): Long = elapsedRealtime() - startMs
}
