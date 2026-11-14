package net.neuralcheck.data.local.files

import net.neuralcheck.util.ReasonText

/**
 * The layout of the LongArray `StressNative.runStorageVerify` returns. The C++
 * side (stress_jni.cpp, `runStorageVerify`) writes it in exactly this order; the
 * array carries its own version and length so a reader built against another
 * layout says so (rule 25). The JVM cannot compare the two sides — see
 * [StorageVerifyOutcome.fromNative].
 */
object StorageVerifyLayout {
    const val VERSION = 1L
    const val SIZE = 12

    const val IDX_VERSION = 0
    const val IDX_LENGTH = 1
    const val IDX_STATUS = 2
    const val IDX_METHOD = 3
    const val IDX_FALLBACK_STAGE = 4
    const val IDX_FALLBACK_ERRNO = 5
    const val IDX_ERRNO = 6
    const val IDX_BYTES_PLANNED = 7
    const val IDX_BYTES_COMPARED = 8
    const val IDX_BLOCKS_MISMATCHED = 9
    const val IDX_IO_ERROR_BLOCKS = 10
    const val IDX_ELAPSED_MS = 11

    // StorageVerifyStatus in storage_verify.h
    const val STATUS_RAN = 0L
    const val STATUS_PARTIAL_TIME_CAP = 1L
    const val STATUS_PARTIAL_CANCELLED = 2L
    const val STATUS_OPEN_FAILED = 3L
    const val STATUS_READ_FAILED = 4L
    const val STATUS_ALLOCATION_FAILED = 5L
    const val STATUS_INVALID_ARGUMENT = 6L

    // StorageVerifyMethod
    const val METHOD_O_DIRECT = 0L
    const val METHOD_DONTNEED_FALLBACK = 1L

    // StorageFallbackStage
    const val FALLBACK_STAGE_OPEN = 1L
    const val FALLBACK_STAGE_READ = 2L
}

/**
 * The values of `storageVerifyMethod` (N-09): HOW the read-back was done.
 * `o_direct_sequential` — opened with O_DIRECT and read sequentially;
 * `dontneed_sequential:<why>` — O_DIRECT was refused (the text says where and
 * with which errno), so the cached range was dropped (posix_fadvise DONTNEED)
 * and read buffered. DONTNEED is applied on both paths; neither string can
 * promise the device was really read (see storage_verify.h on encrypted
 * files), only what was requested and what the kernel accepted.
 */
object StorageVerifyMethods {
    const val O_DIRECT_SEQUENTIAL = "o_direct_sequential"
    const val DONTNEED_SEQUENTIAL_PREFIX = "dontneed_sequential:"
    const val REASON_O_DIRECT_OPEN_FAILED = "o_direct_open_failed"
    const val REASON_O_DIRECT_READ_FAILED = "o_direct_read_failed"
}

/**
 * `storageWriteVerifyErrors` — the errors of the WRITE loop (N-09: an I/O exception, a short
 * write, a failed `sync`). Shipped whenever the write ran, whether or not the read-back did
 * (user decision 2026-10-08: "write errors, fsync among them, ship whenever the write ran,
 * whether the read-back ran or not"); absent when no write ran, and `storageVerifyState` then
 * says why. A 0 is a measured 0 only because a write ran. (A short write cannot occur:
 * `FileOutputStream.write(byte[])` writes it all or throws.)
 */
object StorageWriteErrors {
    /**
     * @param writeAttempted whether the loop reached a `write` at all.
     * @param failedSyncs `fsync` calls that threw, counted at the periodic and the final sync.
     * @param writeExceptions writes that threw (the loop stops at the first, so 0 or 1).
     * @return the error count, or `null` when no write ran.
     */
    fun count(writeAttempted: Boolean, failedSyncs: Int, writeExceptions: Int): Int? =
        if (!writeAttempted) null else failedSyncs + writeExceptions
}

/**
 * What the storage read-back producer produced in STORAGE_STRESS
 * (fieldsVersion 68, T-16, N-09): the last 256 MiB of the written file, read
 * back and compared block by block with a pattern that is a function of the
 * byte offset.
 *
 * [readErrors] is a RAW count of 1 MiB blocks that differ, could not be read
 * (EIO) or were missing (rule 20: the app derives no `…Passed`). It is `null`
 * unless a comparison executed — and a state of `ran` / `partial:*` is what
 * makes a 0 a measured 0 (rule 22).
 */
data class StorageVerifyOutcome(
    val state: String,
    /** 1 MiB blocks that differ / unreadable / missing. `null` = no comparison executed. */
    val readErrors: Int? = null,
    /** Bytes read back and compared. LESS than [bytesPlanned] on a partial run. */
    val bytesCompared: Long? = null,
    /** [م] What the read-back set out to cover (256 MiB). */
    val bytesPlanned: Long? = null,
    val durationMs: Long? = null,
    val method: String? = null
) {
    /** True iff the comparison ran at all, so [readErrors] means something. */
    val executed: Boolean get() = readErrors != null

    companion object {
        /** Bytes read back: the last 256 MiB (S-30: "a read-back of the last 256 MiB"). */
        const val WINDOW_BYTES: Long = 256L * 1024 * 1024
        const val MIB: Long = 1024L * 1024

        val DEFAULT = StorageVerifyOutcome(
            state = VerificationStates.notRun(VerificationStates.REASON_NO_V2_RESULT)
        )

        fun notRun(reason: String, durationMs: Long? = null, method: String? = null) = StorageVerifyOutcome(
            state = VerificationStates.notRun(reason),
            durationMs = durationMs,
            method = method
        )

        fun nativeCallFailed(t: Throwable) =
            notRun(VerificationStates.REASON_NATIVE_CALL_FAILED + ":" + ReasonText.of(t))

        private fun methodOf(result: LongArray): String =
            if (result[StorageVerifyLayout.IDX_METHOD] == StorageVerifyLayout.METHOD_O_DIRECT) {
                StorageVerifyMethods.O_DIRECT_SEQUENTIAL
            } else {
                val why = when (result[StorageVerifyLayout.IDX_FALLBACK_STAGE]) {
                    StorageVerifyLayout.FALLBACK_STAGE_OPEN -> StorageVerifyMethods.REASON_O_DIRECT_OPEN_FAILED
                    StorageVerifyLayout.FALLBACK_STAGE_READ -> StorageVerifyMethods.REASON_O_DIRECT_READ_FAILED
                    else -> "fallback_stage_unknown:${result[StorageVerifyLayout.IDX_FALLBACK_STAGE]}"
                }
                StorageVerifyMethods.DONTNEED_SEQUENTIAL_PREFIX + why + ":" +
                    ErrnoText.of(result[StorageVerifyLayout.IDX_FALLBACK_ERRNO])
            }

        /**
         * Reads the native result. An array that is not the layout this build was
         * written against is declared unreadable — never read as zeros.
         */
        fun fromNative(result: LongArray?): StorageVerifyOutcome {
            if (result == null ||
                result.size != StorageVerifyLayout.SIZE ||
                result[StorageVerifyLayout.IDX_VERSION] != StorageVerifyLayout.VERSION ||
                result[StorageVerifyLayout.IDX_LENGTH] != StorageVerifyLayout.SIZE.toLong()
            ) {
                return notRun("${VerificationStates.REASON_NATIVE_RESULT_UNREADABLE}:size=${result?.size ?: -1}")
            }
            val status = result[StorageVerifyLayout.IDX_STATUS]
            val errno = result[StorageVerifyLayout.IDX_ERRNO]
            val planned = result[StorageVerifyLayout.IDX_BYTES_PLANNED]
            val compared = result[StorageVerifyLayout.IDX_BYTES_COMPARED]
            val elapsed = result[StorageVerifyLayout.IDX_ELAPSED_MS]
            val method = methodOf(result)

            fun executed(state: String) = StorageVerifyOutcome(
                state = state,
                readErrors = result[StorageVerifyLayout.IDX_BLOCKS_MISMATCHED].toInt(),
                bytesCompared = compared,
                bytesPlanned = planned,
                durationMs = elapsed,
                method = method
            )

            return when (status) {
                StorageVerifyLayout.STATUS_RAN -> executed(VerificationStates.VERIFY_RAN)
                StorageVerifyLayout.STATUS_PARTIAL_TIME_CAP ->
                    executed(VerificationStates.partial(VerificationStates.REASON_TIME_CAP_REACHED))
                StorageVerifyLayout.STATUS_PARTIAL_CANCELLED ->
                    executed(VerificationStates.partial(VerificationStates.REASON_CANCELLED))
                StorageVerifyLayout.STATUS_READ_FAILED ->
                    // A read that failed for a reason other than EIO. With nothing compared yet
                    // there is no comparison to call partial.
                    if (compared > 0L) {
                        executed(
                            VerificationStates.partial(
                                "${VerificationStates.REASON_READ_FAILED}:${ErrnoText.of(errno)}"
                            )
                        )
                    } else {
                        notRun(
                            "${VerificationStates.REASON_READ_FAILED}:${ErrnoText.of(errno)}",
                            elapsed, method
                        )
                    }
                StorageVerifyLayout.STATUS_OPEN_FAILED -> notRun(
                    "${VerificationStates.REASON_READ_OPEN_FAILED}:${ErrnoText.of(errno)}",
                    elapsed, method
                )
                StorageVerifyLayout.STATUS_ALLOCATION_FAILED -> notRun(
                    "${VerificationStates.REASON_ALLOCATION_FAILED}:${ErrnoText.of(errno)}",
                    elapsed
                )
                StorageVerifyLayout.STATUS_INVALID_ARGUMENT -> notRun(
                    VerificationStates.REASON_INVALID_ARGUMENT, elapsed
                )
                else -> notRun("${VerificationStates.REASON_NATIVE_STATUS_UNKNOWN}:$status", elapsed)
            }
        }
    }
}
