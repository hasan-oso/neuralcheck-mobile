package net.neuralcheck.service.stress.v2

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.neuralcheck.data.local.files.StorageVerifyLayout
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * fieldsVersion 68, batch 3 part 2-b (T-16): the Kotlin half of the storage
 * producer — the window it asks the reader for, the time budget it gives it, the
 * cancel protocol it owns, and the write-side pattern's fallbacks.
 *
 * The native layer is a FAKE (rule 37): these tests prove the Kotlin logic, not the
 * C++ reader or the pattern it compares.
 */
class StorageVerifyRunnerTest {

    private val mib = 1024L * 1024
    private val window = 256L * mib

    private fun ran(compared: Long = window, mismatched: Long = 0) = longArrayOf(
        StorageVerifyLayout.VERSION, StorageVerifyLayout.SIZE.toLong(),
        StorageVerifyLayout.STATUS_RAN, StorageVerifyLayout.METHOD_O_DIRECT, 0, 0, 0,
        window, compared, mismatched, 0, 500
    )

    private fun cancelled() = longArrayOf(
        StorageVerifyLayout.VERSION, StorageVerifyLayout.SIZE.toLong(),
        StorageVerifyLayout.STATUS_PARTIAL_CANCELLED, StorageVerifyLayout.METHOD_O_DIRECT, 0, 0, 0,
        window, 8 * mib, 0, 0, 90
    )

    // ───────────────────────── the runner ─────────────────────────

    @Test
    fun `it reads the LAST 256 MiB of what was written, and leaves the phase's margin`() = runBlocking {
        val fake = FakeNativeVerification(onStorage = { _, _, _, _, _ -> ran() })
        val outcome = StorageVerifyRunner(fake).run("/cache/v2_storage_stress.tmp", bytesWritten = 4096 * mib, phaseRemainingMs = 40_000)

        assertEquals(listOf<Any>("/cache/v2_storage_stress.tmp", (4096 - 256) * mib, window, 40_000 - StorageVerifyRunner.SAFETY_MARGIN_MS),
            fake.lastStorageArgs)
        assertEquals("ran", outcome.state)
        assertEquals(0, outcome.readErrors)
    }

    @Test
    fun `the window starts on a whole MiB even if the byte count is not`() = runBlocking {
        val fake = FakeNativeVerification(onStorage = { _, _, _, _, _ -> ran() })
        StorageVerifyRunner(fake).run("/p", bytesWritten = 1024 * mib + 12_345, phaseRemainingMs = 40_000)
        assertEquals(768 * mib, fake.lastStorageArgs!![1])
        assertEquals(window, fake.lastStorageArgs!![2])
    }

    @Test
    fun `a file shorter than the window is not_run with the byte count - not a read of less`() = runBlocking {
        val fake = FakeNativeVerification()
        val runner = StorageVerifyRunner(fake)
        val o = runner.run("/p", bytesWritten = 255 * mib + 999, phaseRemainingMs = 40_000)
        assertEquals("not_run:file_smaller_than_256MiB:bytes_written=${255 * mib + 999}", o.state)
        assertEquals("the reader was never asked", 0, fake.storageCalls.get())
        assertNull(o.readErrors)
        assertEquals(o, runner.lastOutcome)
    }

    @Test
    fun `with no time left after the write loop the read-back is not_run no_time_left`() = runBlocking {
        val fake = FakeNativeVerification()
        val tooLittle = StorageVerifyRunner.MIN_USEFUL_BUDGET_MS + StorageVerifyRunner.SAFETY_MARGIN_MS - 1
        assertEquals("not_run:no_time_left", StorageVerifyRunner(fake).run("/p", 4096 * mib, tooLittle).state)
        assertEquals("not_run:no_time_left", StorageVerifyRunner(fake).run("/p", 4096 * mib, 0).state)
        assertEquals("not_run:no_time_left", StorageVerifyRunner(fake).run("/p", 4096 * mib, -3_000).state)
        assertEquals(0, fake.storageCalls.get())
    }

    @Test
    fun `a throw from the native reader is the message text in the state`() = runBlocking {
        val fake = FakeNativeVerification(onStorage = { _, _, _, _, _ -> throw UnsatisfiedLinkError("no libstress") })
        val o = StorageVerifyRunner(fake).run("/p", 4096 * mib, 40_000)
        assertEquals("not_run:native_call_failed:no libstress", o.state)
        assertNull(o.readErrors)
    }

    @Test
    fun `cancelling the caller raises the flag, waits for the reader, clears it, and keeps its partial result`() {
        val started = CountDownLatch(1)
        val fake = FakeNativeVerification(onStorage = { _, _, _, _, raised ->
            started.countDown()
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
            while (!raised.get() && System.nanoTime() < deadline) Thread.sleep(5)
            assertTrue("the flag was never raised", raised.get())
            cancelled()
        })
        val runner = StorageVerifyRunner(fake)
        var cancelledSeen = false
        runBlocking {
            val job = launch(Dispatchers.Default) {
                try {
                    runner.run("/p", 4096 * mib, 40_000)
                } catch (ce: CancellationException) {
                    cancelledSeen = true
                    throw ce
                }
            }
            assertTrue(started.await(5, TimeUnit.SECONDS))
            withTimeout(15_000) { job.cancelAndJoin() }
        }
        assertTrue(cancelledSeen)
        assertEquals(listOf(true, false), fake.cancelCalls.toList())
        assertFalse(fake.cancelFlag.get())
        assertEquals("partial:cancelled", runner.lastOutcome?.state)
        assertEquals(8 * mib, runner.lastOutcome?.bytesCompared)
    }

    // ───────────────────────── the write-side pattern ─────────────────────────

    @Test
    fun `the writer's chunk is one MiB and the native prepare fills it`() {
        val fake = FakeNativeVerification(onPrepare = { chunk -> chunk.fill(0x5A); 0 })
        val pattern = StorageWritePattern(fake)
        assertEquals(1024 * 1024, pattern.chunk.size)
        assertNull(pattern.unavailableReason)
        assertTrue("the native layer filled it, Kotlin did not overwrite it", pattern.chunk.all { it == 0x5A.toByte() })
    }

    @Test
    fun `each stamp is for the chunk number the writer is on`() {
        val fake = FakeNativeVerification()
        val pattern = StorageWritePattern(fake)
        pattern.stamp(0); pattern.stamp(1); pattern.stamp(4095)
        assertEquals(listOf(0L, 1L, 4095L), fake.stampedIndices.toList())
        assertNull(pattern.unavailableReason)
    }

    @Test
    fun `without the native library the chunk is EXACTLY the old content and the reason is kept`() {
        val fake = FakeNativeVerification(onPrepare = { throw UnsatisfiedLinkError("dlopen failed: no libstress") })
        val pattern = StorageWritePattern(fake)
        assertEquals("dlopen failed: no libstress", pattern.unavailableReason)
        val old = ByteArray(1024 * 1024) { (it and 0xFF).toByte() }  // what the writer used before fieldsVersion 68
        assertArrayEquals("the write loop's content must not change without native", old, pattern.chunk)
        pattern.stamp(7)
        assertTrue("no stamp once it is unavailable", fake.stampedIndices.isEmpty())
    }

    @Test
    fun `a prepare that returns non-zero is unavailable too - never a silently unstamped file`() {
        val pattern = StorageWritePattern(FakeNativeVerification(onPrepare = { -1 }))
        assertEquals("prepare returned -1", pattern.unavailableReason)
        assertEquals(0.toByte(), pattern.chunk[0])
        assertEquals(1.toByte(), pattern.chunk[1])
    }

    @Test
    fun `a stamp that fails mid-file makes the read-back unavailable - the file is no longer offset-keyed`() {
        val fake = FakeNativeVerification(onStamp = { _, i -> if (i == 3L) throw RuntimeException("jni gone") else 0 })
        val pattern = StorageWritePattern(fake)
        pattern.stamp(2)
        assertNull(pattern.unavailableReason)
        pattern.stamp(3)
        assertNotNull(pattern.unavailableReason)
        assertEquals("jni gone", pattern.unavailableReason)
        pattern.stamp(4)
        assertEquals("no more native calls after the first failure", listOf(2L, 3L), fake.stampedIndices.toList())
    }

    @Test
    fun `a stamp that returns non-zero names the chunk it failed at`() {
        val pattern = StorageWritePattern(FakeNativeVerification(onStamp = { _, i -> if (i == 5L) -2 else 0 }))
        pattern.stamp(5)
        assertEquals("stamp returned -2 at chunk 5", pattern.unavailableReason)
    }
}
