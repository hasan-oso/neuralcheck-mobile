package net.neuralcheck.service.stress.v2

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.neuralcheck.data.local.files.MemoryVerifyLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * fieldsVersion 68, batch 3 part 2-b (T-17): the Kotlin half of the memory
 * verification producer — the time budget it hands the kernel, the protocol it
 * owns around cancellation, and what it turns a throw into.
 *
 * The native layer is a FAKE here, and a fake replaces exactly the layer that can
 * refuse or misbehave (rule 37). These tests prove the Kotlin logic; they do not
 * prove the C++ kernel, which only a device run exercises.
 */
class MemoryVerifyRunnerTest {

    private val tile = 256L * 1024 * 1024

    private fun result(status: Long, tilesCompleted: Long = 0, tilesTotal: Long = 4, march: Long = 0) = longArrayOf(
        MemoryVerifyLayout.VERSION, MemoryVerifyLayout.SIZE.toLong(),
        status, 0, tilesTotal * tile, tilesTotal * tile, tile, tilesTotal, tilesCompleted,
        march, 0, 0, 1_234, 6L * 1024 * 1024 * 1024
    )

    private fun FakeNative(onRun: (percentRam: Int, timeCapMs: Long, cancelRaised: AtomicBoolean) -> LongArray) =
        FakeNativeVerification(onMemory = onRun)

    @Test
    fun `the kernel gets the time cap constant when the phase has plenty left`() = runBlocking {
        val fake = FakeNative { _, _, _ -> result(MemoryVerifyLayout.STATUS_RAN, tilesCompleted = 4) }
        val outcome = MemoryVerifyRunner(fake).run(percentRam = 40, phaseRemainingMs = 60_000)

        assertEquals(30_000L, MemoryVerifyRunner.TIME_CAP_MS)
        assertEquals(30_000L, fake.lastTimeCap.get())
        assertEquals(40, fake.lastPercent.get())
        assertEquals("ran", outcome.state)
        assertEquals(30_000L, outcome.timeCapMs)
        assertEquals(4 * tile, outcome.verifyBytes)
    }

    @Test
    fun `the cap never exceeds what the phase has left`() = runBlocking {
        val fake = FakeNative { _, _, _ -> result(MemoryVerifyLayout.STATUS_PARTIAL_TIME_CAP, tilesCompleted = 1) }
        val outcome = MemoryVerifyRunner(fake).run(percentRam = 40, phaseRemainingMs = 12_000)

        assertEquals(12_000L, fake.lastTimeCap.get())
        assertEquals("partial:time_cap_reached", outcome.state)
        assertEquals(12_000L, outcome.timeCapMs)
    }

    @Test
    fun `with too little phase left the kernel is not started - no_time_left, not a zero`() = runBlocking {
        val fake = FakeNative { _, _, _ -> error("must not be called") }
        val runner = MemoryVerifyRunner(fake)
        val outcome = runner.run(percentRam = 40, phaseRemainingMs = MemoryVerifyRunner.MIN_USEFUL_BUDGET_MS - 1)

        assertEquals(0, fake.runCalls.get())
        assertEquals("not_run:no_time_left", outcome.state)
        assertNull(outcome.marchErrors)
        assertEquals("the runner keeps it for the summary", outcome, runner.lastOutcome)

        val negative = runner.run(percentRam = 40, phaseRemainingMs = -5_000)
        assertEquals("not_run:no_time_left", negative.state)
        assertEquals("a negative remainder is not shipped as a negative cap", 0L, negative.timeCapMs)
    }

    @Test
    fun `a throw from the native layer is the message text in the state - and the scan goes on`() = runBlocking {
        val fake = FakeNative { _, _, _ -> throw UnsatisfiedLinkError("dlopen failed: libstress.so not found") }
        val runner = MemoryVerifyRunner(fake)
        val outcome = runner.run(percentRam = 40, phaseRemainingMs = 60_000)

        assertEquals("not_run:native_call_failed:dlopen failed: libstress.so not found", outcome.state)
        assertNull(outcome.marchErrors)
        assertEquals(outcome, runner.lastOutcome)
        assertEquals("no cancel was involved, so the flag was never touched", emptyList<Boolean>(), fake.cancelCalls.toList())
    }

    @Test
    fun `a result of another shape is declared unreadable`() = runBlocking {
        val fake = FakeNative { _, _, _ -> longArrayOf(1, 2, 3) }
        val outcome = MemoryVerifyRunner(fake).run(percentRam = 40, phaseRemainingMs = 60_000)
        assertEquals("not_run:native_result_unreadable:size=3", outcome.state)
        assertNull(outcome.verifyBytes)
    }

    @Test
    fun `cancelling the caller raises the flag, waits for the native call, clears the flag, and keeps the partial result`() {
        val started = CountDownLatch(1)
        // Blocks like the real kernel: it cannot be interrupted, it returns when it sees the flag.
        val fake = FakeNative { _, _, cancelRaised ->
            started.countDown()
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
            while (!cancelRaised.get() && System.nanoTime() < deadline) Thread.sleep(5)
            assertTrue("the flag was never raised", cancelRaised.get())
            result(MemoryVerifyLayout.STATUS_PARTIAL_CANCELLED, tilesCompleted = 2)
        }
        val runner = MemoryVerifyRunner(fake)

        var cancelledSeen = false
        runBlocking {
            val job = launch(Dispatchers.Default) {
                try {
                    runner.run(percentRam = 40, phaseRemainingMs = 60_000)
                } catch (ce: CancellationException) {
                    cancelledSeen = true
                    throw ce
                }
            }
            assertTrue(started.await(5, TimeUnit.SECONDS))
            withTimeout(15_000) { job.cancelAndJoin() }
        }

        assertTrue("the cancellation must propagate", cancelledSeen)
        assertEquals("set, then cleared — in that order, nothing else", listOf(true, false), fake.cancelCalls.toList())
        assertFalse("a stale flag would stop the NEXT producer at its first step", fake.cancelFlag.get())
        val kept = runner.lastOutcome
        assertEquals("partial:cancelled", kept?.state)
        assertEquals(2 * tile, kept?.verifyBytes)
    }

    @Test
    fun `a cancel that lands before the native call starts leaves no outcome and no raised flag`() {
        val fake = FakeNative { _, _, _ -> error("the call must not start") }
        val runner = MemoryVerifyRunner(fake)
        runBlocking {
            val job = launch(Dispatchers.Default, start = kotlinx.coroutines.CoroutineStart.LAZY) {
                runner.run(percentRam = 40, phaseRemainingMs = 60_000)
            }
            job.cancel()
            job.join()
        }
        assertEquals(0, fake.runCalls.get())
        assertNull(runner.lastOutcome)
        assertFalse(fake.cancelFlag.get())
    }
}
