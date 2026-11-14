package net.neuralcheck.service.sampler

import net.neuralcheck.data.local.files.ThermalHeadroomReading
import net.neuralcheck.data.local.files.ThermalListStates
import net.neuralcheck.data.local.files.ThermalStatusEvent
import net.neuralcheck.service.collector.ThermalHeadroomRead
import net.neuralcheck.service.collector.ThermalPlatform
import net.neuralcheck.service.collector.ThermalStatusRegistration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

/**
 * fieldsVersion 68 (ت-03, ت-04, N-02..N-04; order 2026-10-08, S-03 / S-04 /
 * S-35): the slow thermal channels are LISTS of timestamped readings in the
 * footer, not columns of `v[]`. Every reading is the outcome of its own call
 * ("no pinning of «unsupported»: every reading with its own state").
 */
class ThermalListCollectorTest {

    /** A platform whose answers are scripted; every call is counted. */
    private class FakePlatform(
        override val sdkInt: Int = 35,
        headroomScript: List<() -> ThermalHeadroomRead> = emptyList(),
        private val statusNow: () -> Int = { 0 },
        private val registerFails: Exception? = null
    ) : ThermalPlatform {
        private val headroomQueue = ArrayDeque(headroomScript)
        var headroomCalls = 0
        var statusReads = 0
        var registrations = 0
        var unregistered = false
        var onChanged: ((Int) -> Unit)? = null
        var registeredExecutor: Executor? = null
        val firstHeadroomCall = CountDownLatch(1)

        override fun readHeadroom(): ThermalHeadroomRead {
            headroomCalls++
            firstHeadroomCall.countDown()
            val next = headroomQueue.removeFirstOrNull() ?: return ThermalHeadroomRead(0.5f, ThermalListStates.HEADROOM_OK)
            return next()
        }

        override fun currentStatus(): Int {
            statusReads++
            return statusNow()
        }

        override fun registerStatusListener(executor: Executor, onChanged: (Int) -> Unit): ThermalStatusRegistration {
            registrations++
            registerFails?.let { throw it }
            this.onChanged = onChanged
            registeredExecutor = executor
            return ThermalStatusRegistration { unregistered = true }
        }
    }

    private class Clock(var now: Long = 100_000L) : () -> Long {
        override fun invoke(): Long = now
    }

    private fun ok(v: Float) = { ThermalHeadroomRead(v, ThermalListStates.HEADROOM_OK) }

    /** A collector that is never scheduled: ticks are driven by hand. */
    private fun collector(platform: ThermalPlatform, clock: Clock) = ThermalListCollector(platform, clock)

    // ───────────────────────── headroom ─────────────────────────

    @Test
    fun `every tick is an element with t relative to the scan start and the value as returned`() {
        val clock = Clock(100_000L)
        val c = collector(FakePlatform(headroomScript = listOf(ok(0.42f), ok(1.05f))), clock)
        c.startWithoutScheduling(100_000L)
        c.tickHeadroom()
        clock.now = 110_000L
        c.tickHeadroom()

        val lists = c.snapshot()
        assertEquals(
            listOf(
                ThermalHeadroomReading(0L, 0.42f, "ok"),
                ThermalHeadroomReading(10_000L, 1.05f, "ok")
            ),
            lists.headroomReadings
        )
        assertEquals("a value >= 1.0 is forwarded as returned, not clamped", 1.05f, lists.headroomReadings[1].headroom!!, 0f)
        assertEquals(ThermalListStates.CHANNEL_OK, lists.headroomState)
    }

    @Test
    fun `NaN is an element with no number and the state nan`() {
        val platform = FakePlatform(headroomScript = listOf({ ThermalHeadroomRead(null, ThermalListStates.HEADROOM_NAN) }))
        val c = ThermalListCollector(platform, Clock())
        c.startWithoutScheduling(100_000L)
        c.tickHeadroom()

        val r = c.snapshot().headroomReadings.single()
        assertNull(r.headroom)
        assertEquals("nan", r.state)
    }

    @Test
    fun `a transient failure is one element - the next call is made and recorded (no pin)`() {
        val platform = FakePlatform(
            headroomScript = listOf(
                ok(0.4f),
                { ThermalHeadroomRead(null, "exception:getThermalHeadroom called too often") },
                ok(0.6f)
            )
        )
        val clock = Clock()
        val c = ThermalListCollector(platform, clock)
        c.startWithoutScheduling(100_000L)
        c.tickHeadroom(); clock.now += 10_000; c.tickHeadroom(); clock.now += 10_000; c.tickHeadroom()

        val readings = c.snapshot().headroomReadings
        assertEquals(3, platform.headroomCalls)
        assertEquals(listOf("ok", "exception:getThermalHeadroom called too often", "ok"), readings.map { it.state })
        assertEquals("the call after the failure got its value", 0.6f, readings[2].headroom!!, 0f)
    }

    @Test
    fun `a platform that throws is an exception element carrying the message, not the class`() {
        val platform = FakePlatform(headroomScript = listOf({ throw IllegalStateException("rate limit: 1 call per second") }))
        val c = ThermalListCollector(platform, Clock())
        c.startWithoutScheduling(100_000L)
        c.tickHeadroom()

        val r = c.snapshot().headroomReadings.single()
        assertNull(r.headroom)
        assertEquals("exception:rate limit: 1 call per second", r.state)
        assertFalse("rule 21: the message text, never the exception class", r.state.contains("IllegalStateException"))
    }

    @Test
    fun `API below 30 records api_below_30 elements and says so for the channel`() {
        val platform = FakePlatform(
            sdkInt = 29,
            headroomScript = listOf({ ThermalHeadroomRead(null, ThermalListStates.API_BELOW_30) })
        )
        val c = ThermalListCollector(platform, Clock())
        c.startWithoutScheduling(100_000L)
        c.tickHeadroom()

        val lists = c.snapshot()
        assertEquals(ThermalListStates.API_BELOW_30, lists.headroomReadings.single().state)
        assertEquals(ThermalListStates.API_BELOW_30, lists.headroomState)
    }

    @Test
    fun `a collector that ended before its first reading says no_reading_taken, not ok`() {
        val c = ThermalListCollector(FakePlatform(), Clock())
        c.startWithoutScheduling(100_000L)

        val lists = c.snapshot()
        assertTrue(lists.headroomReadings.isEmpty())
        assertEquals(ThermalListStates.NO_READING_TAKEN, lists.headroomState)
    }

    @Test
    fun `start schedules the first headroom call immediately and stop is idempotent`() {
        val platform = FakePlatform()
        val c = ThermalListCollector(platform, Clock())
        c.start(100_000L)
        try {
            assertTrue(
                "the first call must come at t=0, not one period later",
                platform.firstHeadroomCall.await(5, TimeUnit.SECONDS)
            )
        } finally {
            c.stop()
            c.stop()
        }
        assertTrue("the status listener was taken off", platform.unregistered)
    }

    // ───────────────────────── status ─────────────────────────

    @Test
    fun `the first status element is a read at registration and callbacks follow in order`() {
        val platform = FakePlatform(statusNow = { 0 })
        val clock = Clock(100_000L)
        val c = ThermalListCollector(platform, clock)
        c.startWithoutScheduling(100_000L)

        clock.now = 195_000L
        platform.onChanged!!.invoke(2)
        clock.now = 280_000L
        platform.onChanged!!.invoke(3)

        val lists = c.snapshot()
        assertEquals(
            listOf(ThermalStatusEvent(0L, 0), ThermalStatusEvent(95_000L, 2), ThermalStatusEvent(180_000L, 3)),
            lists.statusEvents
        )
        assertEquals(ThermalListStates.STATUS_OK, lists.statusState)
        assertEquals(1, platform.registrations)
        assertEquals(1, platform.statusReads)
    }

    @Test
    fun `status values are raw integers - nothing is classified`() {
        val platform = FakePlatform(statusNow = { 6 })
        val c = ThermalListCollector(platform, Clock())
        c.startWithoutScheduling(100_000L)
        platform.onChanged!!.invoke(1)

        assertEquals(listOf(6, 1), c.snapshot().statusEvents.map { it.status })
    }

    @Test
    fun `API below 29 registers nothing and says api_below_29`() {
        val platform = FakePlatform(sdkInt = 28)
        val c = ThermalListCollector(platform, Clock())
        c.startWithoutScheduling(100_000L)

        val lists = c.snapshot()
        assertEquals(0, platform.registrations)
        assertEquals(0, platform.statusReads)
        assertTrue(lists.statusEvents.isEmpty())
        assertEquals(ThermalListStates.API_BELOW_29, lists.statusState)
    }

    @Test
    fun `a failed registration is named with its text and the initial read is still recorded`() {
        val platform = FakePlatform(registerFails = SecurityException("no thermal listener for this uid"), statusNow = { 1 })
        val c = ThermalListCollector(platform, Clock())
        c.startWithoutScheduling(100_000L)

        val lists = c.snapshot()
        assertEquals("listener_registration_failed:no thermal listener for this uid", lists.statusState)
        assertFalse(lists.statusState.contains("SecurityException"))
        assertEquals("the status at the start is still stated", listOf(1), lists.statusEvents.map { it.status })
    }

    @Test
    fun `an initial read that throws leaves the listener registered and says so`() {
        val platform = FakePlatform(statusNow = { throw IllegalStateException("thermal service not ready") })
        val c = ThermalListCollector(platform, Clock())
        c.startWithoutScheduling(100_000L)
        platform.onChanged!!.invoke(2)

        val lists = c.snapshot()
        assertEquals("initial_read_failed:thermal service not ready", lists.statusState)
        assertEquals("the callback after it is still recorded", listOf(2), lists.statusEvents.map { it.status })
    }

    @Test
    fun `a callback after stop is ignored`() {
        val platform = FakePlatform()
        val c = ThermalListCollector(platform, Clock())
        c.startWithoutScheduling(100_000L)
        val listener = platform.onChanged!!
        c.stop()
        listener.invoke(4)

        assertEquals(listOf(0), c.snapshot().statusEvents.map { it.status })
        assertTrue(platform.unregistered)
    }

    @Test
    fun `the reason text is capped at 200 characters`() {
        val long = "x".repeat(500)
        val platform = FakePlatform(registerFails = IllegalStateException(long))
        val c = ThermalListCollector(platform, Clock())
        c.startWithoutScheduling(100_000L)

        assertEquals(200, c.snapshot().statusState.length)
    }
}
