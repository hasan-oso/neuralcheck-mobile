package net.neuralcheck.data.local.files

import net.neuralcheck.util.ReasonText

/**
 * errno -> text for the failures the native verification producers report as a
 * bare number (sysinfo, mmap, open, pread). Rule 21: a failure is told apart by
 * what it says, not by its class — and a bare errno number says nothing to a
 * reader, so the well-known ones are named. An errno this table does not know
 * is shipped as its number alone, never guessed at.
 *
 * Linux errno numbers are the same on every ABI for these (asm-generic).
 */
object ErrnoText {
    private val NAMES: Map<Long, String> = mapOf(
        1L to "EPERM",
        2L to "ENOENT",
        4L to "EINTR",
        5L to "EIO",
        9L to "EBADF",
        11L to "EAGAIN",
        12L to "ENOMEM",
        13L to "EACCES",
        14L to "EFAULT",
        16L to "EBUSY",
        22L to "EINVAL",
        24L to "EMFILE",
        28L to "ENOSPC",
        30L to "EROFS",
        75L to "EOVERFLOW",
        95L to "EOPNOTSUPP"
    )

    /** `errno=12 (ENOMEM)`, or `errno=<n>` when the number is not in the table. */
    fun of(errno: Long): String =
        NAMES[errno]?.let { "errno=$errno ($it)" } ?: "errno=$errno"
}

/**
 * Names of the sub-windows a phase declares inside its own boundary
 * (fieldsVersion 68, batch 3 part 2-b, S-30: "the kernel's window declared in
 * phaseBoundaries"). One source (rule 24).
 */
object PhaseWindowNames {
    /** The memory verification kernel, which runs first inside MEMORY_STRESS. */
    const val MEMORY_KERNEL = "memory_kernel"

    /** The AI memory workload that takes the rest of MEMORY_STRESS. */
    const val MEMORY_WORKLOAD = "memory_workload"
}

/**
 * A named sub-window of a phase, in the SAME frame as `v[0]` and
 * [PhaseBoundary] (ms since scan start). While the memory kernel runs, `v[9]`
 * is 0 because nothing publishes it — the window is what tells a reader that
 * zero is "no publisher" and not "no bandwidth".
 */
data class PhaseWindow(
    val name: String,
    val startMs: Long,
    val endMs: Long
)

/**
 * The layout of the LongArray `StressNative.runMemoryVerify` returns. The C++
 * side (stress_jni.cpp, `runMemoryVerify`) writes it in exactly this order; the
 * array carries its own version and length so a reader built against another
 * layout says so instead of misreading (rule 25). The two sides cannot be
 * compared by a JVM test — see [MemoryVerifyOutcome.fromNative] for what the
 * reader does with a shape it does not recognise.
 *
 * Version 2 (part 2-c, the free-memory condition) adds [IDX_FREE_BYTES]; version 1
 * had 13 entries.
 */
object MemoryVerifyLayout {
    const val VERSION = 2L
    const val SIZE = 14

    const val IDX_VERSION = 0
    const val IDX_LENGTH = 1
    const val IDX_STATUS = 2
    const val IDX_ERRNO = 3
    const val IDX_REQUESTED_BYTES = 4
    const val IDX_BUFFER_BYTES = 5
    const val IDX_TILE_BYTES = 6
    const val IDX_TILES_TOTAL = 7
    const val IDX_TILES_COMPLETED = 8
    const val IDX_MARCH_ERRORS = 9
    const val IDX_WALKING_ONES_ERRORS = 10
    const val IDX_RANDOM_VERIFY_ERRORS = 11
    const val IDX_ELAPSED_MS = 12
    /** Free memory as `sysinfo` reported it, bytes; -1 = sysinfo failed, so it was never read. */
    const val IDX_FREE_BYTES = 13

    // MemoryVerifyStatus in memory_verify.h
    const val STATUS_RAN = 0L
    const val STATUS_PARTIAL_TIME_CAP = 1L
    const val STATUS_PARTIAL_CANCELLED = 2L
    const val STATUS_SYSINFO_FAILED = 3L
    const val STATUS_ALLOCATION_FAILED = 4L
    const val STATUS_INVALID_ARGUMENT = 5L
    const val STATUS_INSUFFICIENT_FREE_MEMORY = 6L

    /** Every status code the native side can return, in order — the classification test walks this list. */
    val ALL_STATUSES: List<Long> = listOf(
        STATUS_RAN, STATUS_PARTIAL_TIME_CAP, STATUS_PARTIAL_CANCELLED, STATUS_SYSINFO_FAILED,
        STATUS_ALLOCATION_FAILED, STATUS_INVALID_ARGUMENT, STATUS_INSUFFICIENT_FREE_MEMORY
    )
}

/**
 * What the memory verification producer (C++ March C- + Walking Ones + a
 * size-independent random verify, tile by tile inside a time cap) produced in
 * MEMORY_STRESS (fieldsVersion 68, T-17, N-10).
 *
 * Every count is a RAW count of bytes (rule 20): the app never derives a
 * `…Passed`; the server does. A run that did not happen has NO counts — a
 * `null` here is "nothing was measured", never a 0 (rule 22), and [state] says
 * which of the three it is:
 *  - `ran`                 — every planned tile completed; a 0 count is a measured 0;
 *  - `partial:<reason>`    — it stopped early: the counts are real for the
 *                            [verifyBytes] it covered, which is LESS than
 *                            [bufferBytes]. NOT a pass for the whole buffer;
 *  - `not_run:<reason>`    — it did not run (or its result was unreadable).
 */
data class MemoryVerifyOutcome(
    val state: String,
    /** Bytes that received the WHOLE battery (completed tiles only). `null` = not run. */
    val verifyBytes: Long? = null,
    /** March C- mismatching bytes, summed over its 5 read passes. `null` = not run. */
    val marchErrors: Long? = null,
    /** Walking-Ones mismatching bytes, summed over its 8 patterns. `null` = not run. */
    val walkingOnesErrors: Long? = null,
    /** Random-pattern mismatching bytes. `null` = not run. */
    val randomVerifyErrors: Long? = null,
    /** Bytes the buffer was divided into tiles over — what "covered everything" means. `null` = none mapped. */
    val bufferBytes: Long? = null,
    /** The time cap this run was given, ms. */
    val timeCapMs: Long? = null,
    /** Wall time the native call took, ms. `null` = not measured (it did not return). */
    val durationMs: Long? = null,
    /**
     * Free memory as `sysinfo` reported it (`freeram × mem_unit`), bytes — RAW (rule 20), shipped
     * whenever `sysinfo` succeeded, whether the test ran or not. `null` = `sysinfo` failed, so
     * it was never read. The buffer the test may take (free − 500 MiB) is derived from this and
     * a constant; it is not shipped.
     */
    val freeBytes: Long? = null
) {
    /** Counts exist only when the battery actually executed. */
    val executed: Boolean get() = marchErrors != null

    companion object {
        /** The default of a summary no V2 scan filled in: nothing ran. */
        val DEFAULT = MemoryVerifyOutcome(
            state = VerificationStates.notRun(VerificationStates.REASON_NO_V2_RESULT)
        )

        fun notRun(
            reason: String,
            timeCapMs: Long? = null,
            durationMs: Long? = null,
            freeBytes: Long? = null
        ) = MemoryVerifyOutcome(
            state = VerificationStates.notRun(reason),
            timeCapMs = timeCapMs,
            durationMs = durationMs,
            freeBytes = freeBytes
        )

        /** The JNI call threw: the state carries the exception's MESSAGE (rule 21), capped. */
        fun nativeCallFailed(t: Throwable, timeCapMs: Long? = null) = notRun(
            VerificationStates.REASON_NATIVE_CALL_FAILED + ":" + ReasonText.of(t),
            timeCapMs
        )

        /**
         * Reads the native result. An array that is not the layout this build
         * was written against (null, another length, another version) is
         * declared unreadable — never read as zeros.
         */
        fun fromNative(result: LongArray?, timeCapMs: Long): MemoryVerifyOutcome {
            if (result == null ||
                result.size != MemoryVerifyLayout.SIZE ||
                result[MemoryVerifyLayout.IDX_VERSION] != MemoryVerifyLayout.VERSION ||
                result[MemoryVerifyLayout.IDX_LENGTH] != MemoryVerifyLayout.SIZE.toLong()
            ) {
                return notRun(
                    "${VerificationStates.REASON_NATIVE_RESULT_UNREADABLE}:size=${result?.size ?: -1}",
                    timeCapMs
                )
            }
            val status = result[MemoryVerifyLayout.IDX_STATUS]
            val errno = result[MemoryVerifyLayout.IDX_ERRNO]
            val requested = result[MemoryVerifyLayout.IDX_REQUESTED_BYTES]
            val buffer = result[MemoryVerifyLayout.IDX_BUFFER_BYTES]
            val tile = result[MemoryVerifyLayout.IDX_TILE_BYTES]
            val tilesCompleted = result[MemoryVerifyLayout.IDX_TILES_COMPLETED]
            val elapsed = result[MemoryVerifyLayout.IDX_ELAPSED_MS]
            // The native side's "not read" marker (-1) is an ABSENT value here, never a number.
            val free = result[MemoryVerifyLayout.IDX_FREE_BYTES].takeIf { it >= 0L }

            return when (status) {
                MemoryVerifyLayout.STATUS_RAN,
                MemoryVerifyLayout.STATUS_PARTIAL_TIME_CAP,
                MemoryVerifyLayout.STATUS_PARTIAL_CANCELLED -> MemoryVerifyOutcome(
                    state = when (status) {
                        MemoryVerifyLayout.STATUS_RAN -> VerificationStates.VERIFY_RAN
                        MemoryVerifyLayout.STATUS_PARTIAL_TIME_CAP ->
                            VerificationStates.partial(VerificationStates.REASON_TIME_CAP_REACHED)
                        else -> VerificationStates.partial(VerificationStates.REASON_CANCELLED)
                    },
                    verifyBytes = tilesCompleted * tile,
                    marchErrors = result[MemoryVerifyLayout.IDX_MARCH_ERRORS],
                    walkingOnesErrors = result[MemoryVerifyLayout.IDX_WALKING_ONES_ERRORS],
                    randomVerifyErrors = result[MemoryVerifyLayout.IDX_RANDOM_VERIFY_ERRORS],
                    bufferBytes = buffer,
                    timeCapMs = timeCapMs,
                    durationMs = elapsed,
                    freeBytes = free
                )
                MemoryVerifyLayout.STATUS_SYSINFO_FAILED -> notRun(
                    "${VerificationStates.REASON_SYSINFO_FAILED}:${ErrnoText.of(errno)}",
                    timeCapMs, elapsed, free
                )
                MemoryVerifyLayout.STATUS_ALLOCATION_FAILED -> notRun(
                    "${VerificationStates.REASON_ALLOCATION_FAILED}:requested_bytes=$requested, ${ErrnoText.of(errno)}",
                    timeCapMs, elapsed, free
                )
                MemoryVerifyLayout.STATUS_INVALID_ARGUMENT -> notRun(
                    "${VerificationStates.REASON_INVALID_ARGUMENT}:time_cap_ms=$timeCapMs",
                    timeCapMs, elapsed, free
                )
                // Part 2-c: no mapping was attempted; the free memory is the raw reason.
                MemoryVerifyLayout.STATUS_INSUFFICIENT_FREE_MEMORY -> notRun(
                    VerificationStates.REASON_INSUFFICIENT_FREE_MEMORY,
                    timeCapMs, elapsed, free
                )
                else -> notRun(
                    "${VerificationStates.REASON_NATIVE_STATUS_UNKNOWN}:$status",
                    timeCapMs, elapsed, free
                )
            }
        }
    }
}
