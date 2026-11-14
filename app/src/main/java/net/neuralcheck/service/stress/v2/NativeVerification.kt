package net.neuralcheck.service.stress.v2

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import net.neuralcheck.data.local.files.MemoryVerifyOutcome
import net.neuralcheck.data.local.files.StorageVerifyOutcome
import net.neuralcheck.data.local.files.VerificationStates
import net.neuralcheck.util.ReasonText
import net.neuralcheck.util.StressNative
import java.util.concurrent.atomic.AtomicReference

/**
 * The seam between Kotlin and `libstress.so` for the native verification
 * producers (fieldsVersion 68, batch 3 part 2-b). Everything the producers need
 * from native code goes through here, so the Kotlin LOGIC (time budgets,
 * cancellation, mapping a native result to a state) can be tested on the JVM
 * with a fake — and so it is stated plainly what such a test does NOT cover:
 * the native side itself. A fake replaces the very layer that can refuse or
 * misbehave (rule 37); that layer is exercised only on a device.
 *
 * Every function may throw (UnsatisfiedLinkError when the library did not load,
 * or whatever JNI raises). Callers turn the throw into a state carrying its
 * message — never into a zero.
 */
interface NativeVerificationApi {
    /** `StressNative.runMemoryVerify`; the layout is `MemoryVerifyLayout`. */
    fun runMemoryVerify(percentRam: Int, timeCapMs: Long): LongArray

    /** `StressNative.prepareStoragePattern`: fills the writer's chunk with the base pattern. 0 = done. */
    fun prepareStoragePattern(chunk: ByteArray): Int

    /** `StressNative.stampStoragePattern`: stamps the chunk for chunk number [chunkIndex]. 0 = done. */
    fun stampStoragePattern(chunk: ByteArray, chunkIndex: Long): Int

    /** `StressNative.runStorageVerify`; the layout is `StorageVerifyLayout`. */
    fun runStorageVerify(path: String, startOffset: Long, lengthBytes: Long, budgetMs: Long): LongArray

    /** `StressNative.runSieveKnownAnswer`; the layout is `KnownAnswerLayout` (sieve). */
    fun runSieveKnownAnswer(): LongArray

    /** `StressNative.runSha256KnownAnswer`; the layout is `KnownAnswerLayout` (sha). */
    fun runSha256KnownAnswer(): LongArray

    /** Raises/clears the cancel flag the long producers poll. */
    fun setCancel(cancel: Boolean)
}

/** The production implementation: straight onto [StressNative]. */
object StressNativeVerification : NativeVerificationApi {
    override fun runMemoryVerify(percentRam: Int, timeCapMs: Long): LongArray =
        StressNative.runMemoryVerify(percentRam, timeCapMs)

    override fun prepareStoragePattern(chunk: ByteArray): Int = StressNative.prepareStoragePattern(chunk)

    override fun stampStoragePattern(chunk: ByteArray, chunkIndex: Long): Int =
        StressNative.stampStoragePattern(chunk, chunkIndex)

    override fun runStorageVerify(path: String, startOffset: Long, lengthBytes: Long, budgetMs: Long): LongArray =
        StressNative.runStorageVerify(path, startOffset, lengthBytes, budgetMs)

    override fun runSieveKnownAnswer(): LongArray = StressNative.runSieveKnownAnswer()

    override fun runSha256KnownAnswer(): LongArray = StressNative.runSha256KnownAnswer()

    override fun setCancel(cancel: Boolean) = StressNative.setNativeVerifyCancel(cancel)
}

/**
 * Runs a blocking native call on a worker thread such that cancelling the
 * calling coroutine makes the call return: the native producers poll a cancel
 * flag between steps, and this is the caller that owns the protocol — set the
 * flag, wait for the native call to RETURN (it cannot be interrupted), clear
 * the flag, then let the cancellation propagate. The flag is never left set:
 * a stale one would stop the next producer at its first step.
 */
internal suspend fun <T> NativeVerificationApi.callCancellable(block: () -> T): T = coroutineScope {
    val call = async(Dispatchers.IO) { block() }
    try {
        call.await()
    } catch (ce: CancellationException) {
        setCancel(true)
        try {
            withContext(NonCancellable) { call.join() }
        } finally {
            setCancel(false)
        }
        throw ce
    }
}

/**
 * The memory verification producer's Kotlin half: gives the native kernel its
 * time cap, and turns what comes back — or what is thrown — into a
 * [MemoryVerifyOutcome]. It computes no verdict (rule 20): the counts go out
 * raw and the server decides.
 */
class MemoryVerifyRunner(private val native: NativeVerificationApi = StressNativeVerification) {

    companion object {
        /**
         * The kernel's time cap inside the 60 s MEMORY_STRESS phase — the agent's
         * proposal [م], NOT a user decision (the user decided "a time cap inside
         * the sixty seconds" and left the number open). Half the phase.
         *
         * Evidence from the code, none of it measured on a device: (1) a comment
         * in StressTestServiceV2 (the memory phase's "wait out the remainder")
         * claims an 8 GB device finished March + Walking in 25 s — that claim is
         * unmeasured, and the random verify adds two passes to it; (2) the AI
         * memory workload that takes the rest publishes v[9] at ~1 Hz, so 30 s
         * leaves ~30 samples of it. Reaching the cap before the whole buffer is
         * covered is a DECLARED state (`partial:time_cap_reached`, with the bytes
         * covered), never read as a pass.
         */
        const val TIME_CAP_MS: Long = 30_000L

        /** Below this much phase time left, the kernel is not started: `not_run:no_time_left`. */
        const val MIN_USEFUL_BUDGET_MS: Long = 2_000L
    }

    /**
     * The outcome of the most recent [run] — also when that run was CANCELLED
     * after the native call had started: the kernel stops at its next step and
     * returns what it covered, and that partial result is kept here (the
     * exception that cancelled the caller cannot carry it). `null` = no run
     * has produced anything yet.
     */
    @Volatile
    var lastOutcome: MemoryVerifyOutcome? = null
        private set

    /**
     * @param percentRam the share of RAM the buffer aims at (the caller's sizing).
     * @param phaseRemainingMs what is left of the phase; the cap never exceeds it.
     */
    suspend fun run(percentRam: Int, phaseRemainingMs: Long): MemoryVerifyOutcome {
        val capMs = minOf(TIME_CAP_MS, phaseRemainingMs)
        if (capMs < MIN_USEFUL_BUDGET_MS) {
            return MemoryVerifyOutcome
                .notRun(VerificationStates.REASON_NO_TIME_LEFT, timeCapMs = capMs.coerceAtLeast(0L))
                .also { lastOutcome = it }
        }
        val holder = AtomicReference<LongArray?>(null)
        return try {
            native.callCancellable { holder.set(native.runMemoryVerify(percentRam, capMs)) }
            MemoryVerifyOutcome.fromNative(holder.get(), capMs).also { lastOutcome = it }
        } catch (ce: CancellationException) {
            // The native call may have returned a (partial) result before the
            // cancellation landed; keep it, then let the cancellation through.
            holder.get()?.let { lastOutcome = MemoryVerifyOutcome.fromNative(it, capMs) }
            throw ce
        } catch (t: Throwable) {
            MemoryVerifyOutcome.nativeCallFailed(t, capMs).also { lastOutcome = it }
        }
    }
}

/**
 * The storage phase's WRITE side of the verification pattern (fieldsVersion 68,
 * T-16): the one reusable 1 MiB chunk the writer keeps, filled and stamped so
 * that every byte the file holds is a function of its offset.
 *
 * The pattern is defined in C++ only (storage_verify.cpp), next to the code that
 * checks it; this class only asks for it. The cost on the write loop is one JNI
 * call and 256 x 16 byte stores per MiB — not measured on a device. When the
 * native library cannot do it, the chunk gets EXACTLY the content the writer
 * had before this version (`j & 0xFF`, nothing stamped), the write loop is
 * unchanged, and [unavailableReason] says why the read-back cannot be run.
 */
class StorageWritePattern(private val native: NativeVerificationApi = StressNativeVerification) {

    companion object {
        const val CHUNK_BYTES: Int = 1024 * 1024
    }

    val chunk = ByteArray(CHUNK_BYTES)

    /** `null` = every chunk is stamped by the native pattern. Else why it is not (and so why no read-back). */
    @Volatile
    var unavailableReason: String? = null
        private set

    init {
        try {
            val rc = native.prepareStoragePattern(chunk)
            if (rc != 0) fallBack("prepare returned $rc")
        } catch (t: Throwable) {
            fallBack(ReasonText.of(t))
        }
    }

    /** Stamps the chunk as chunk number [chunkIndex] of the file. Call before each write. */
    fun stamp(chunkIndex: Long) {
        if (unavailableReason != null) return
        try {
            val rc = native.stampStoragePattern(chunk, chunkIndex)
            if (rc != 0) unavailableReason = "stamp returned $rc at chunk $chunkIndex"
        } catch (t: Throwable) {
            // Chunks written from here on carry a stale stamp: the file is no longer a
            // function of its offset, so the read-back must not judge it.
            unavailableReason = ReasonText.of(t)
        }
    }

    private fun fallBack(reason: String) {
        unavailableReason = reason
        for (i in chunk.indices) chunk[i] = (i and 0xFF).toByte()  // the writer's old content, byte for byte
    }
}

/**
 * The storage read-back producer's Kotlin half: decides whether and how long
 * the read-back may run in what is left of the 75 s phase, hands the native
 * reader its window, and turns the result — or a throw — into a
 * [StorageVerifyOutcome]. No verdict is computed here (rule 20): the block
 * count goes out raw.
 */
class StorageVerifyRunner(private val native: NativeVerificationApi = StressNativeVerification) {

    companion object {
        /** Below this much budget the read-back is not started: `not_run:no_time_left`. */
        const val MIN_USEFUL_BUDGET_MS: Long = 2_000L

        /**
         * [م] Phase time kept in hand after the read-back's budget, so the phase
         * still ends on its nominal clock. The agent's number, unmeasured.
         */
        const val SAFETY_MARGIN_MS: Long = 500L
    }

    /** See [MemoryVerifyRunner.lastOutcome]: also keeps a result returned after the caller was cancelled. */
    @Volatile
    var lastOutcome: StorageVerifyOutcome? = null
        private set

    /**
     * @param path the file the write loop wrote and has closed (and fsynced).
     * @param bytesWritten how much of it is there; the LAST 256 MiB is read.
     * @param phaseRemainingMs what is left of the phase NOW.
     */
    suspend fun run(path: String, bytesWritten: Long, phaseRemainingMs: Long): StorageVerifyOutcome {
        val window = StorageVerifyOutcome.WINDOW_BYTES
        val mib = StorageVerifyOutcome.MIB
        val alignedEnd = bytesWritten / mib * mib
        if (alignedEnd < window) {
            return StorageVerifyOutcome
                .notRun("${VerificationStates.REASON_FILE_SMALLER_THAN_WINDOW}:bytes_written=$bytesWritten")
                .also { lastOutcome = it }
        }
        val budgetMs = phaseRemainingMs - SAFETY_MARGIN_MS
        if (budgetMs < MIN_USEFUL_BUDGET_MS) {
            return StorageVerifyOutcome.notRun(VerificationStates.REASON_NO_TIME_LEFT).also { lastOutcome = it }
        }
        val holder = AtomicReference<LongArray?>(null)
        return try {
            native.callCancellable {
                holder.set(native.runStorageVerify(path, alignedEnd - window, window, budgetMs))
            }
            StorageVerifyOutcome.fromNative(holder.get()).also { lastOutcome = it }
        } catch (ce: CancellationException) {
            holder.get()?.let { lastOutcome = StorageVerifyOutcome.fromNative(it) }
            throw ce
        } catch (t: Throwable) {
            StorageVerifyOutcome.nativeCallFailed(t).also { lastOutcome = it }
        }
    }
}
