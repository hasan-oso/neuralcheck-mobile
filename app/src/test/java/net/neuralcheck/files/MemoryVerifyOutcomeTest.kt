package net.neuralcheck.files

import net.neuralcheck.data.local.files.ErrnoText
import net.neuralcheck.data.local.files.MemoryVerifyLayout
import net.neuralcheck.data.local.files.MemoryVerifyOutcome
import net.neuralcheck.data.local.files.PhaseBoundary
import net.neuralcheck.data.local.files.PhaseWindow
import net.neuralcheck.data.local.files.PhaseWindowNames
import net.neuralcheck.data.local.files.VerificationSummary
import net.neuralcheck.data.local.files.VerificationWireNames
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * fieldsVersion 68, batch 3 part 2-b (T-17, N-10, S-11, S-30): the memory
 * verification PRODUCER's result as the app reads and ships it.
 *
 * What this covers: how a native result array becomes a state + raw counts,
 * and that the counts reach the payload raw (or are absent with a state).
 * What it does NOT cover: the C++ kernel and the JNI call that fills the array
 * — a JVM test replaces them with a hand-built array (rule 37). Those run only
 * on a device.
 */
class MemoryVerifyOutcomeTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val tile = 256L * 1024 * 1024

    /** A well-formed native result, in `MemoryVerifyLayout`'s order. */
    private fun native(
        status: Long,
        errno: Long = 0,
        requested: Long = 0,
        buffer: Long = 0,
        tileBytes: Long = tile,
        tilesTotal: Long = 0,
        tilesCompleted: Long = 0,
        march: Long = 0,
        walk: Long = 0,
        random: Long = 0,
        elapsedMs: Long = 0,
        /** What sysinfo reported (bytes); -1 = sysinfo failed. A device with 6 GiB free by default. */
        freeBytes: Long = 6L * 1024 * 1024 * 1024
    ) = longArrayOf(
        MemoryVerifyLayout.VERSION, MemoryVerifyLayout.SIZE.toLong(),
        status, errno, requested, buffer, tileBytes, tilesTotal, tilesCompleted,
        march, walk, random, elapsedMs, freeBytes
    )

    // ───────────────────────── reading the native result ─────────────────────────

    @Test
    fun `a complete run is ran with every count real - a zero is a measured zero`() {
        val o = MemoryVerifyOutcome.fromNative(
            native(MemoryVerifyLayout.STATUS_RAN, requested = 3 * tile, buffer = 3 * tile,
                tilesTotal = 3, tilesCompleted = 3, elapsedMs = 21_000),
            timeCapMs = 30_000
        )
        assertEquals("ran", o.state)
        assertEquals(3 * tile, o.verifyBytes)
        assertEquals(3 * tile, o.bufferBytes)
        assertEquals(0L, o.marchErrors)
        assertEquals(0L, o.walkingOnesErrors)
        assertEquals(0L, o.randomVerifyErrors)
        assertEquals(30_000L, o.timeCapMs)
        assertEquals(21_000L, o.durationMs)
    }

    @Test
    fun `a run the time cap stopped is partial - it covered LESS than the buffer and says so`() {
        val o = MemoryVerifyOutcome.fromNative(
            native(MemoryVerifyLayout.STATUS_PARTIAL_TIME_CAP, buffer = 8 * tile,
                tilesTotal = 8, tilesCompleted = 5, march = 0, walk = 0, random = 0, elapsedMs = 30_050),
            timeCapMs = 30_000
        )
        assertEquals("partial:time_cap_reached", o.state)
        assertEquals("covered bytes = completed tiles only", 5 * tile, o.verifyBytes)
        assertEquals(8 * tile, o.bufferBytes)
        assertTrue("a partial run must not read as covering the whole buffer", o.verifyBytes!! < o.bufferBytes!!)
        assertEquals("the counts are real for what it covered", 0L, o.marchErrors)
    }

    @Test
    fun `errors found in a tile that did not finish are kept - a mismatch is a mismatch`() {
        val o = MemoryVerifyOutcome.fromNative(
            native(MemoryVerifyLayout.STATUS_PARTIAL_TIME_CAP, buffer = 4 * tile, tilesTotal = 4,
                tilesCompleted = 0, march = 17, walk = 0, random = 3),
            timeCapMs = 30_000
        )
        assertEquals(0L, o.verifyBytes)
        assertEquals(17L, o.marchErrors)
        assertEquals(3L, o.randomVerifyErrors)
    }

    @Test
    fun `a cancelled run is partial cancelled`() {
        val o = MemoryVerifyOutcome.fromNative(
            native(MemoryVerifyLayout.STATUS_PARTIAL_CANCELLED, buffer = 2 * tile, tilesTotal = 2, tilesCompleted = 1),
            timeCapMs = 30_000
        )
        assertEquals("partial:cancelled", o.state)
        assertEquals(tile, o.verifyBytes)
    }

    @Test
    fun `a sysinfo failure is a state with the errno text and no counts - not zeros`() {
        val o = MemoryVerifyOutcome.fromNative(
            native(MemoryVerifyLayout.STATUS_SYSINFO_FAILED, errno = 1, elapsedMs = 0, freeBytes = -1),
            timeCapMs = 30_000
        )
        assertEquals("not_run:sysinfo_failed:errno=1 (EPERM)", o.state)
        assertNull("sysinfo failed: the free memory was never read - absent, not -1 and not 0", o.freeBytes)
        assertNull("the old {0,0,0,0} read as 'no errors'", o.marchErrors)
        assertNull(o.walkingOnesErrors)
        assertNull(o.randomVerifyErrors)
        assertNull(o.verifyBytes)
        assertFalse(o.executed)
    }

    @Test
    fun `an allocation failure names the size asked for and the errno text`() {
        val o = MemoryVerifyOutcome.fromNative(
            native(MemoryVerifyLayout.STATUS_ALLOCATION_FAILED, errno = 12, requested = 4_831_838_208L),
            timeCapMs = 30_000
        )
        assertEquals("not_run:allocation_failed:requested_bytes=4831838208, errno=12 (ENOMEM)", o.state)
        assertNull(o.marchErrors)
        assertNull(o.verifyBytes)
        assertNull("nothing was mapped, so there is no coverage denominator", o.bufferBytes)
    }

    @Test
    fun `an errno the table does not know is shipped as its number - not guessed at`() {
        assertEquals("errno=12 (ENOMEM)", ErrnoText.of(12))
        assertEquals("errno=9999", ErrnoText.of(9999))
    }

    @Test
    fun `a result of another shape is unreadable - never read as zeros`() {
        val good = native(MemoryVerifyLayout.STATUS_RAN, buffer = tile, tilesTotal = 1, tilesCompleted = 1)

        assertEquals(
            "not_run:native_result_unreadable:size=-1",
            MemoryVerifyOutcome.fromNative(null, 30_000).state
        )
        assertEquals(
            "not_run:native_result_unreadable:size=13",
            MemoryVerifyOutcome.fromNative(good.copyOf(13), 30_000).state
        )
        val otherVersion = good.copyOf().also { it[MemoryVerifyLayout.IDX_VERSION] = 3 }
        assertEquals(
            "not_run:native_result_unreadable:size=14",
            MemoryVerifyOutcome.fromNative(otherVersion, 30_000).state
        )
        // the layout this build read BEFORE the free-memory field: version 1, 13 entries
        val v1 = longArrayOf(1, 13, MemoryVerifyLayout.STATUS_RAN, 0, 0, tile, tile, 1, 1, 0, 0, 0, 5)
        assertEquals(
            "an older layout is declared unreadable, not read with a missing field",
            "not_run:native_result_unreadable:size=13",
            MemoryVerifyOutcome.fromNative(v1, 30_000).state
        )
        val lyingLength = good.copyOf().also { it[MemoryVerifyLayout.IDX_LENGTH] = 15 }
        assertEquals(
            "not_run:native_result_unreadable:size=14",
            MemoryVerifyOutcome.fromNative(lyingLength, 30_000).state
        )
        for (bad in listOf(null, good.copyOf(13), otherVersion, lyingLength, v1)) {
            val o = MemoryVerifyOutcome.fromNative(bad, 30_000)
            assertNull(o.marchErrors)
            assertNull(o.verifyBytes)
        }
    }

    @Test
    fun `a status this build does not know is declared - not mapped to ran`() {
        val o = MemoryVerifyOutcome.fromNative(native(status = 42), 30_000)
        assertEquals("not_run:native_status_unknown:42", o.state)
        assertNull(o.marchErrors)
    }

    @Test
    fun `a JNI throw becomes the exception's MESSAGE text, capped - not its class`() {
        val o = MemoryVerifyOutcome.nativeCallFailed(
            UnsatisfiedLinkError("dlopen failed: library \"libstress.so\" not found"), timeCapMs = 30_000
        )
        assertEquals(
            "not_run:native_call_failed:dlopen failed: library \"libstress.so\" not found",
            o.state
        )
        assertNull(o.marchErrors)
        assertEquals(30_000L, o.timeCapMs)

        val long = MemoryVerifyOutcome.nativeCallFailed(RuntimeException("x".repeat(500)))
        assertEquals("the reason text is capped at 200 characters (S-35)",
            "not_run:native_call_failed:".length + 200, long.state.length)

        assertEquals(
            "a throw with no message still says so",
            "not_run:native_call_failed:exception without message",
            MemoryVerifyOutcome.nativeCallFailed(RuntimeException()).state
        )
    }

    // ───────────────────────── free memory (part 2-c, reading (b)) ─────────────────────────

    private val mib = 1024L * 1024

    @Test
    fun `too little free memory is a state with the raw free bytes - no buffer, no counts`() {
        val o = MemoryVerifyOutcome.fromNative(
            native(MemoryVerifyLayout.STATUS_INSUFFICIENT_FREE_MEMORY, freeBytes = 900 * mib, elapsedMs = 0),
            timeCapMs = 30_000
        )
        assertEquals("not_run:insufficient_free_memory", o.state)
        assertEquals("the raw free memory ships; available_safe is derived and does not", 900 * mib, o.freeBytes)
        assertNull(o.marchErrors)
        assertNull(o.walkingOnesErrors)
        assertNull(o.randomVerifyErrors)
        assertNull(o.verifyBytes)
        assertNull("no mapping was attempted", o.bufferBytes)
        assertFalse(o.executed)
    }

    @Test
    fun `every native status code is classified - none falls through to unknown`() {
        // Rule 30: the whole enumeration, not the new value. Each entry is what the native side can
        // return; the test fails when a status is added to the layout without a case here.
        val expected = linkedMapOf(
            MemoryVerifyLayout.STATUS_RAN to "ran",
            MemoryVerifyLayout.STATUS_PARTIAL_TIME_CAP to "partial:time_cap_reached",
            MemoryVerifyLayout.STATUS_PARTIAL_CANCELLED to "partial:cancelled",
            MemoryVerifyLayout.STATUS_SYSINFO_FAILED to "not_run:sysinfo_failed:errno=1 (EPERM)",
            MemoryVerifyLayout.STATUS_ALLOCATION_FAILED to "not_run:allocation_failed:requested_bytes=1048576, errno=12 (ENOMEM)",
            MemoryVerifyLayout.STATUS_INVALID_ARGUMENT to "not_run:invalid_argument:time_cap_ms=30000",
            MemoryVerifyLayout.STATUS_INSUFFICIENT_FREE_MEMORY to "not_run:insufficient_free_memory"
        )
        assertEquals("every status the layout declares has a case", MemoryVerifyLayout.ALL_STATUSES.toSet(), expected.keys)
        for ((status, state) in expected) {
            val arr = native(
                status, errno = if (status == MemoryVerifyLayout.STATUS_ALLOCATION_FAILED) 12 else 1,
                requested = mib, buffer = 2 * tile, tilesTotal = 2, tilesCompleted = 1,
                freeBytes = if (status == MemoryVerifyLayout.STATUS_SYSINFO_FAILED) -1 else 6_000 * mib
            )
            val o = MemoryVerifyOutcome.fromNative(arr, 30_000)
            assertEquals("status $status", state, o.state)
            assertFalse("status $status must not fall to unknown", o.state.contains("native_status_unknown"))
        }
        assertEquals(
            "a code outside the list is declared unknown",
            "not_run:native_status_unknown:99",
            MemoryVerifyOutcome.fromNative(native(status = 99), 30_000).state
        )
    }

    @Test
    fun `free memory is shipped whenever sysinfo succeeded - whatever else happened`() {
        for (status in MemoryVerifyLayout.ALL_STATUSES) {
            val o = MemoryVerifyOutcome.fromNative(native(status, errno = 12, requested = mib, freeBytes = 3_000 * mib), 30_000)
            if (status == MemoryVerifyLayout.STATUS_SYSINFO_FAILED) continue
            assertEquals("status $status", 3_000 * mib, o.freeBytes)
        }
        val failed = MemoryVerifyOutcome.fromNative(native(MemoryVerifyLayout.STATUS_SYSINFO_FAILED, errno = 1, freeBytes = -1), 30_000)
        assertNull(failed.freeBytes)
        // and on an unreadable array / a throw there is no number to ship
        assertNull(MemoryVerifyOutcome.fromNative(null, 30_000).freeBytes)
        assertNull(MemoryVerifyOutcome.nativeCallFailed(RuntimeException("x")).freeBytes)
    }

    @Test
    fun `a default summary says nothing ran and carries no memory counts - never a pass`() {
        val v = VerificationSummary()
        assertEquals("not_run:no_v2_result", v.memoryVerifyState)
        assertNull(v.memoryVerifyBytes)
        assertNull(v.memoryMarchErrors)
        assertNull(v.memoryWalkingOnesErrors)
        assertNull(v.memoryRandomVerifyErrors)
        assertNull(v.memoryVerifyFreeBytes)
        assertNull(v.memoryPhaseWindows)
    }

    // ───────────────────────── the wire (writer -> reader -> builder -> Gson) ─────────────────────────

    private fun vrOf(v: VerificationSummary) = FooterRoundTrip(tmp.newFolder()).let { rt ->
        rt.run(rt.baseFooter().copy(verificationResults = v)).wireFooter.getAsJsonObject("verificationResults")
    }

    private fun summaryOf(o: MemoryVerifyOutcome, windows: List<PhaseWindow>? = null) = VerificationSummary(
        memoryVerifyState = o.state,
        memoryVerifyBytes = o.verifyBytes,
        memoryMarchErrors = o.marchErrors,
        memoryWalkingOnesErrors = o.walkingOnesErrors,
        memoryRandomVerifyErrors = o.randomVerifyErrors,
        memoryVerifyBufferBytes = o.bufferBytes,
        memoryVerifyTimeCapMs = o.timeCapMs,
        memoryVerifyDurationMs = o.durationMs,
        memoryVerifyFreeBytes = o.freeBytes,
        memoryPhaseWindows = windows
    )

    @Test
    fun `a run that executed ships its raw counts - zeros included - and the coverage`() {
        val vr = vrOf(
            summaryOf(
                MemoryVerifyOutcome.fromNative(
                    native(MemoryVerifyLayout.STATUS_PARTIAL_TIME_CAP, buffer = 8 * tile, tilesTotal = 8,
                        tilesCompleted = 5, march = 0, walk = 0, random = 2, elapsedMs = 30_050),
                    30_000
                ),
                windows = listOf(
                    PhaseWindow(PhaseWindowNames.MEMORY_KERNEL, 60_000, 90_050),
                    PhaseWindow(PhaseWindowNames.MEMORY_WORKLOAD, 90_050, 120_000)
                )
            )
        )
        assertEquals("partial:time_cap_reached", vr.get(VerificationWireNames.MEMORY_VERIFY_STATE).asString)
        assertEquals(5 * tile, vr.get(VerificationWireNames.MEMORY_VERIFY_BYTES).asLong)
        assertEquals(8 * tile, vr.get(VerificationWireNames.MEMORY_VERIFY_BUFFER_BYTES).asLong)
        assertEquals("a measured zero stays a zero", 0L, vr.get(VerificationWireNames.MEMORY_MARCH_ERRORS).asLong)
        assertEquals(0L, vr.get(VerificationWireNames.MEMORY_WALKING_ONES_ERRORS).asLong)
        assertEquals(2L, vr.get(VerificationWireNames.MEMORY_RANDOM_VERIFY_ERRORS).asLong)
        assertEquals(30_000L, vr.get(VerificationWireNames.MEMORY_VERIFY_TIME_CAP_MS).asLong)
        assertEquals(30_050L, vr.get(VerificationWireNames.MEMORY_VERIFY_DURATION_MS).asLong)
        val windows = vr.getAsJsonArray(VerificationWireNames.MEMORY_PHASE_WINDOWS)
        assertEquals(2, windows.size())
        assertEquals("memory_kernel", windows[0].asJsonObject.get(VerificationWireNames.WINDOW_NAME).asString)
        assertEquals(60_000L, windows[0].asJsonObject.get(VerificationWireNames.WINDOW_START_MS).asLong)
        assertEquals("memory_workload", windows[1].asJsonObject.get(VerificationWireNames.WINDOW_NAME).asString)
        assertEquals(120_000L, windows[1].asJsonObject.get(VerificationWireNames.WINDOW_END_MS).asLong)
    }

    @Test
    fun `a run that did not execute ships its state and NO count key - 0 would read as no errors`() {
        for (failed in listOf(
            MemoryVerifyOutcome.fromNative(native(MemoryVerifyLayout.STATUS_SYSINFO_FAILED, errno = 1), 30_000),
            MemoryVerifyOutcome.fromNative(native(MemoryVerifyLayout.STATUS_ALLOCATION_FAILED, errno = 12, requested = 1), 30_000),
            MemoryVerifyOutcome.fromNative(null, 30_000),
            MemoryVerifyOutcome.nativeCallFailed(UnsatisfiedLinkError("no lib"), 30_000),
            MemoryVerifyOutcome.notRun("no_time_left")
        )) {
            val vr = vrOf(summaryOf(failed))
            assertTrue(vr.get(VerificationWireNames.MEMORY_VERIFY_STATE).asString.startsWith("not_run:"))
            for (key in listOf(
                VerificationWireNames.MEMORY_VERIFY_BYTES,
                VerificationWireNames.MEMORY_MARCH_ERRORS,
                VerificationWireNames.MEMORY_WALKING_ONES_ERRORS,
                VerificationWireNames.MEMORY_RANDOM_VERIFY_ERRORS,
                VerificationWireNames.MEMORY_VERIFY_BUFFER_BYTES,
                VerificationWireNames.MEMORY_PHASE_WINDOWS
            )) {
                assertFalse("$key must be absent for ${failed.state}", vr.has(key))
            }
        }
    }

    @Test
    fun `the state and the raw free memory reach the payload when the test did not run for lack of memory`() {
        val vr = vrOf(
            summaryOf(
                MemoryVerifyOutcome.fromNative(
                    native(MemoryVerifyLayout.STATUS_INSUFFICIENT_FREE_MEMORY, freeBytes = 1011 * mib), 30_000
                )
            )
        )
        assertEquals("not_run:insufficient_free_memory", vr.get(VerificationWireNames.MEMORY_VERIFY_STATE).asString)
        assertEquals(1011 * mib, vr.get(VerificationWireNames.MEMORY_VERIFY_FREE_BYTES).asLong)
        for (key in listOf(
            VerificationWireNames.MEMORY_MARCH_ERRORS, VerificationWireNames.MEMORY_VERIFY_BYTES,
            VerificationWireNames.MEMORY_VERIFY_BUFFER_BYTES
        )) assertFalse("$key must be absent: nothing ran", vr.has(key))
        assertFalse("available_safe is derived from the raw and a constant: it is not shipped",
            vr.keySet().any { it.contains("vailableSafe", ignoreCase = true) })
    }

    @Test
    fun `free memory also reaches the payload of a run that happened - and is absent when sysinfo failed`() {
        val ran = vrOf(
            summaryOf(
                MemoryVerifyOutcome.fromNative(
                    native(MemoryVerifyLayout.STATUS_RAN, buffer = tile, tilesTotal = 1, tilesCompleted = 1,
                        freeBytes = 6_000 * mib), 30_000
                )
            )
        )
        assertEquals(6_000 * mib, ran.get(VerificationWireNames.MEMORY_VERIFY_FREE_BYTES).asLong)

        val sysinfoFailed = vrOf(
            summaryOf(
                MemoryVerifyOutcome.fromNative(
                    native(MemoryVerifyLayout.STATUS_SYSINFO_FAILED, errno = 1, freeBytes = -1), 30_000
                )
            )
        )
        assertFalse(sysinfoFailed.has(VerificationWireNames.MEMORY_VERIFY_FREE_BYTES))
    }

    @Test
    fun `the app never writes a verdict the server derives - no Passed key in any state`() {
        val ran = vrOf(
            summaryOf(
                MemoryVerifyOutcome.fromNative(
                    native(MemoryVerifyLayout.STATUS_RAN, buffer = tile, tilesTotal = 1, tilesCompleted = 1), 30_000
                )
            )
        )
        val notRun = vrOf(VerificationSummary())
        for (vr in listOf(ran, notRun)) {
            for (name in VerificationWireNames.SERVER_DERIVED_NAMES) {
                assertFalse("$name is the server's to derive (rule 20)", vr.has(name))
            }
            assertFalse(vr.keySet().any { it.startsWith("memory") && it.endsWith("Passed") })
        }
    }

    @Test
    fun `the kernel and workload windows survive the file - declared inside the phase's own boundary`() {
        val rt = FooterRoundTrip(tmp.newFolder())
        val boundary = PhaseBoundary(
            phaseId = 2, phaseName = "MEMORY_STRESS", startMs = 60_000, endMs = 120_000,
            windows = listOf(
                PhaseWindow(PhaseWindowNames.MEMORY_KERNEL, 60_000, 90_000),
                PhaseWindow(PhaseWindowNames.MEMORY_WORKLOAD, 90_000, 120_000)
            )
        )
        val plain = PhaseBoundary(phaseId = 3, phaseName = "CPU_SUSTAINED", startMs = 120_000, endMs = 195_000)
        val r = rt.run(rt.baseFooter().copy(phaseBoundaries = listOf(boundary, plain)))

        val read = r.data.footer.phaseBoundaries
        assertEquals("one entry per phase: a lookup by phaseId finds exactly one", 1, read.count { it.phaseId == 2 })
        val mem = read.first { it.phaseId == 2 }
        assertNotNull(mem.windows)
        assertEquals(listOf("memory_kernel", "memory_workload"), mem.windows!!.map { it.name })
        assertEquals(90_000L, mem.windows!![0].endMs)
        assertNull("a phase that declares no windows has none", read.first { it.phaseId == 3 }.windows)
    }
}
