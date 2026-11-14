package net.neuralcheck.files

import net.neuralcheck.data.local.files.StorageVerifyLayout
import net.neuralcheck.data.local.files.StorageVerifyOutcome
import net.neuralcheck.data.local.files.StorageWriteErrors
import net.neuralcheck.data.local.files.VerificationSummary
import net.neuralcheck.data.local.files.VerificationWireNames
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * fieldsVersion 68, batch 3 part 2-b (T-16, N-09, S-12, S-30): the storage
 * read-back PRODUCER's result as the app reads and ships it.
 *
 * Covered: a native result array -> state, raw block count, coverage, method; and
 * that the numbers reach the payload raw or are absent with a state. NOT covered:
 * the C++ reader, O_DIRECT, the pattern comparison — a JVM test hands in a built
 * array (rule 37); they run only on a device.
 */
class StorageVerifyOutcomeTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val window = 256L * 1024 * 1024

    private fun native(
        status: Long,
        method: Long = StorageVerifyLayout.METHOD_O_DIRECT,
        fallbackStage: Long = 0,
        fallbackErrno: Long = 0,
        errno: Long = 0,
        planned: Long = window,
        compared: Long = window,
        mismatched: Long = 0,
        ioErrors: Long = 0,
        elapsedMs: Long = 900
    ) = longArrayOf(
        StorageVerifyLayout.VERSION, StorageVerifyLayout.SIZE.toLong(),
        status, method, fallbackStage, fallbackErrno, errno,
        planned, compared, mismatched, ioErrors, elapsedMs
    )

    // ───────────────────────── reading the native result ─────────────────────────

    @Test
    fun `a complete read-back is ran with the raw block count - a zero is a measured zero`() {
        val o = StorageVerifyOutcome.fromNative(native(StorageVerifyLayout.STATUS_RAN))
        assertEquals("ran", o.state)
        assertEquals(0, o.readErrors)
        assertEquals(window, o.bytesCompared)
        assertEquals(window, o.bytesPlanned)
        assertEquals("o_direct_sequential", o.method)
        assertEquals(900L, o.durationMs)
        assertTrue(o.executed)
    }

    @Test
    fun `mismatching, unreadable and missing blocks are counted raw as 1 MiB blocks`() {
        val o = StorageVerifyOutcome.fromNative(
            native(StorageVerifyLayout.STATUS_RAN, mismatched = 7, ioErrors = 3)
        )
        assertEquals("ran", o.state)
        assertEquals("the count of 1 MiB blocks, no verdict derived", 7, o.readErrors)
    }

    @Test
    fun `a budget that ran out is partial - it compared LESS than planned and says so`() {
        val o = StorageVerifyOutcome.fromNative(
            native(StorageVerifyLayout.STATUS_PARTIAL_TIME_CAP, compared = 100L * 1024 * 1024)
        )
        assertEquals("partial:time_cap_reached", o.state)
        assertEquals(100L * 1024 * 1024, o.bytesCompared)
        assertTrue("never a pass for the whole window", o.bytesCompared!! < o.bytesPlanned!!)
        assertEquals(0, o.readErrors)
    }

    @Test
    fun `a cancelled read-back is partial cancelled`() {
        val o = StorageVerifyOutcome.fromNative(
            native(StorageVerifyLayout.STATUS_PARTIAL_CANCELLED, compared = 8L * 1024 * 1024)
        )
        assertEquals("partial:cancelled", o.state)
    }

    @Test
    fun `an O_DIRECT open that was refused is a declared fallback - not a failure`() {
        val o = StorageVerifyOutcome.fromNative(
            native(
                StorageVerifyLayout.STATUS_RAN,
                method = StorageVerifyLayout.METHOD_DONTNEED_FALLBACK,
                fallbackStage = StorageVerifyLayout.FALLBACK_STAGE_OPEN,
                fallbackErrno = 22
            )
        )
        assertEquals("ran", o.state)
        assertEquals("dontneed_sequential:o_direct_open_failed:errno=22 (EINVAL)", o.method)
        assertEquals(0, o.readErrors)
    }

    @Test
    fun `an O_DIRECT first read that was refused names the read stage`() {
        val o = StorageVerifyOutcome.fromNative(
            native(
                StorageVerifyLayout.STATUS_RAN,
                method = StorageVerifyLayout.METHOD_DONTNEED_FALLBACK,
                fallbackStage = StorageVerifyLayout.FALLBACK_STAGE_READ,
                fallbackErrno = 95
            )
        )
        assertEquals("dontneed_sequential:o_direct_read_failed:errno=95 (EOPNOTSUPP)", o.method)
    }

    @Test
    fun `a fallback stage this build does not know is declared - not dropped`() {
        val o = StorageVerifyOutcome.fromNative(
            native(
                StorageVerifyLayout.STATUS_RAN,
                method = StorageVerifyLayout.METHOD_DONTNEED_FALLBACK,
                fallbackStage = 9, fallbackErrno = 1
            )
        )
        assertEquals("dontneed_sequential:fallback_stage_unknown:9:errno=1 (EPERM)", o.method)
    }

    @Test
    fun `no read could be opened is a state with the errno text and no count`() {
        val o = StorageVerifyOutcome.fromNative(
            native(
                StorageVerifyLayout.STATUS_OPEN_FAILED, errno = 2, compared = 0,
                method = StorageVerifyLayout.METHOD_DONTNEED_FALLBACK,
                fallbackStage = StorageVerifyLayout.FALLBACK_STAGE_OPEN, fallbackErrno = 2
            )
        )
        assertEquals("not_run:read_open_failed:errno=2 (ENOENT)", o.state)
        assertNull(o.readErrors)
        assertFalse(o.executed)
    }

    @Test
    fun `a read that failed after some blocks is partial with the errno - before any it is not_run`() {
        val partial = StorageVerifyOutcome.fromNative(
            native(StorageVerifyLayout.STATUS_READ_FAILED, errno = 22, compared = 12L * 1024 * 1024, mismatched = 0)
        )
        assertEquals("partial:read_failed:errno=22 (EINVAL)", partial.state)
        assertEquals(0, partial.readErrors)
        assertEquals(12L * 1024 * 1024, partial.bytesCompared)

        val none = StorageVerifyOutcome.fromNative(
            native(StorageVerifyLayout.STATUS_READ_FAILED, errno = 22, compared = 0)
        )
        assertEquals("not_run:read_failed:errno=22 (EINVAL)", none.state)
        assertNull("nothing was compared, so there is no count", none.readErrors)
    }

    @Test
    fun `an aligned-buffer failure and an invalid argument are states`() {
        assertEquals(
            "not_run:allocation_failed:errno=12 (ENOMEM)",
            StorageVerifyOutcome.fromNative(native(StorageVerifyLayout.STATUS_ALLOCATION_FAILED, errno = 12)).state
        )
        assertEquals(
            "not_run:invalid_argument",
            StorageVerifyOutcome.fromNative(native(StorageVerifyLayout.STATUS_INVALID_ARGUMENT)).state
        )
    }

    @Test
    fun `a result of another shape is unreadable - never read as zeros`() {
        val good = native(StorageVerifyLayout.STATUS_RAN)
        assertEquals("not_run:native_result_unreadable:size=-1", StorageVerifyOutcome.fromNative(null).state)
        assertEquals("not_run:native_result_unreadable:size=11", StorageVerifyOutcome.fromNative(good.copyOf(11)).state)
        assertEquals(
            "not_run:native_result_unreadable:size=12",
            StorageVerifyOutcome.fromNative(good.copyOf().also { it[StorageVerifyLayout.IDX_VERSION] = 3 }).state
        )
        assertEquals(
            "not_run:native_status_unknown:77",
            StorageVerifyOutcome.fromNative(native(status = 77)).state
        )
        for (bad in listOf(null, good.copyOf(11))) {
            assertNull(StorageVerifyOutcome.fromNative(bad).readErrors)
        }
    }

    @Test
    fun `a JNI throw is the message text, capped - the writer's pattern prepare is not this path`() {
        val o = StorageVerifyOutcome.nativeCallFailed(UnsatisfiedLinkError("dlopen failed"))
        assertEquals("not_run:native_call_failed:dlopen failed", o.state)
        assertNull(o.readErrors)
    }

    @Test
    fun `a default summary says nothing ran and carries no storage counts or method`() {
        val v = VerificationSummary()
        assertEquals("not_run:no_v2_result", v.storageVerifyState)
        assertNull(v.storageWriteVerifyErrors)
        assertNull(v.storageReadVerifyErrors)
        assertNull(v.storageVerifyBytesCompared)
        assertNull(v.storageVerifyMethod)
        assertEquals(StorageVerifyOutcome.DEFAULT.state, v.storageVerifyState)
    }

    // ───────────────────────── the wire ─────────────────────────

    private fun vrOf(v: VerificationSummary) = FooterRoundTrip(tmp.newFolder()).let { rt ->
        rt.run(rt.baseFooter().copy(verificationResults = v)).wireFooter.getAsJsonObject("verificationResults")
    }

    private fun summaryOf(o: StorageVerifyOutcome, writeErrors: Int?) = VerificationSummary(
        // the write errors are the caller's (StorageWriteErrors.count) - they do not depend on
        // whether the read-back executed (part 2-c)
        storageWriteVerifyErrors = writeErrors,
        storageReadVerifyErrors = o.readErrors,
        storageVerifyState = o.state,
        storageVerifyBytesCompared = o.bytesCompared,
        storageVerifyBytesPlanned = o.bytesPlanned,
        storageVerifyDurationMs = o.durationMs,
        storageVerifyMethod = o.method
    )

    @Test
    fun `a read-back that executed ships its raw counts - zeros included - the coverage and the method`() {
        val vr = vrOf(
            summaryOf(
                StorageVerifyOutcome.fromNative(
                    native(StorageVerifyLayout.STATUS_PARTIAL_TIME_CAP, compared = 100L * 1024 * 1024, mismatched = 0)
                ),
                writeErrors = 0
            )
        )
        assertEquals("partial:time_cap_reached", vr.get("storageVerifyState").asString)
        assertEquals("a measured zero stays a zero", 0, vr.get("storageWriteVerifyErrors").asInt)
        assertEquals(0, vr.get("storageReadVerifyErrors").asInt)
        assertEquals(100L * 1024 * 1024, vr.get(VerificationWireNames.STORAGE_VERIFY_BYTES_COMPARED).asLong)
        assertEquals(window, vr.get(VerificationWireNames.STORAGE_VERIFY_BYTES_PLANNED).asLong)
        assertEquals(900L, vr.get(VerificationWireNames.STORAGE_VERIFY_DURATION_MS).asLong)
        assertEquals("o_direct_sequential", vr.get(VerificationWireNames.STORAGE_VERIFY_METHOD).asString)
    }

    // Part 2-c (user decision 2026-10-08: "write errors, fsync among them, ship whenever the
    // write ran, whether the read-back ran or not") REPLACES the test that stood here, which
    // asserted storageWriteVerifyErrors absent for every read-back that did not execute.
    @Test
    fun `a read-back that did not execute ships a state and no READ count - 0 would read as verified clean`() {
        for (failed in listOf(
            StorageVerifyOutcome.notRun("no_time_left"),
            StorageVerifyOutcome.notRun("write_failed:No space left on device"),
            StorageVerifyOutcome.notRun("file_smaller_than_256MiB:bytes_written=1048576"),
            StorageVerifyOutcome.notRun("pattern_unavailable:dlopen failed"),
            StorageVerifyOutcome.fromNative(null),
            StorageVerifyOutcome.nativeCallFailed(UnsatisfiedLinkError("x"))
        )) {
            val vr = vrOf(summaryOf(failed, writeErrors = StorageWriteErrors.count(true, 2, 0)))
            assertTrue(vr.get("storageVerifyState").asString.startsWith("not_run:"))
            for (key in listOf(
                "storageReadVerifyErrors",
                VerificationWireNames.STORAGE_VERIFY_BYTES_COMPARED,
                VerificationWireNames.STORAGE_VERIFY_BYTES_PLANNED
            )) {
                assertFalse("$key must be absent for ${failed.state}", vr.has(key))
            }
            assertEquals(
                "the write ran, so its errors ship although the read-back did not (${failed.state})",
                2, vr.get("storageWriteVerifyErrors").asInt
            )
        }
    }

    @Test
    fun `write errors ship whenever the write ran - a measured zero included`() {
        assertEquals(0, StorageWriteErrors.count(writeAttempted = true, failedSyncs = 0, writeExceptions = 0))
        assertEquals(3, StorageWriteErrors.count(true, failedSyncs = 3, writeExceptions = 0))
        assertEquals("a write that threw is an error beside the failed fsyncs", 2,
            StorageWriteErrors.count(true, failedSyncs = 1, writeExceptions = 1))

        val vr = vrOf(summaryOf(StorageVerifyOutcome.notRun("no_time_left"), StorageWriteErrors.count(true, 0, 0)))
        assertEquals("not_run:no_time_left", vr.get("storageVerifyState").asString)
        assertEquals("a real zero stays a zero", 0, vr.get("storageWriteVerifyErrors").asInt)
    }

    @Test
    fun `no write errors key when no write ran - the state says why`() {
        assertNull("nothing written, nothing counted", StorageWriteErrors.count(false, failedSyncs = 0, writeExceptions = 0))
        for (why in listOf("write_failed:open failed: EACCES (Permission denied)", "cancelled", "phase_not_reached")) {
            val vr = vrOf(summaryOf(StorageVerifyOutcome.notRun(why), StorageWriteErrors.count(false, 0, 0)))
            assertFalse("storageWriteVerifyErrors must be absent when no write ran", vr.has("storageWriteVerifyErrors"))
            assertEquals("not_run:$why", vr.get("storageVerifyState").asString)
        }
    }

    @Test
    fun `the app never writes a storage Passed verdict`() {
        val vr = vrOf(
            summaryOf(StorageVerifyOutcome.fromNative(native(StorageVerifyLayout.STATUS_RAN)), writeErrors = 0)
        )
        assertFalse(vr.keySet().any { it.startsWith("storage") && it.endsWith("Passed") })
        assertFalse("T-08: still gone", vr.has("storageSlcCacheSizeMb"))
    }
}
