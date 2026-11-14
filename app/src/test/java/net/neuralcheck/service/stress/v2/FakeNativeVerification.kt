package net.neuralcheck.service.stress.v2

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Test double for [NativeVerificationApi] (fieldsVersion 68, batch 3 part 2-b).
 *
 * It REPLACES `libstress.so`. That is exactly the layer that can refuse or
 * misbehave on a device — a test built on this double proves the Kotlin logic
 * around the native calls (budgets, cancellation, mapping results to states)
 * and nothing about the C++ (rule 37). Every behaviour is a lambda a test sets;
 * a call the test did not expect fails loudly instead of returning a quiet zero.
 */
class FakeNativeVerification(
    var onMemory: (percentRam: Int, timeCapMs: Long, cancelRaised: AtomicBoolean) -> LongArray =
        { _, _, _ -> error("runMemoryVerify was not expected") },
    var onStorage: (path: String, startOffset: Long, lengthBytes: Long, budgetMs: Long, cancelRaised: AtomicBoolean) -> LongArray =
        { _, _, _, _, _ -> error("runStorageVerify was not expected") },
    var onPrepare: (chunk: ByteArray) -> Int = { 0 },
    var onStamp: (chunk: ByteArray, chunkIndex: Long) -> Int = { _, _ -> 0 },
    var onSieve: () -> LongArray = { error("runSieveKnownAnswer was not expected") },
    var onSha: () -> LongArray = { error("runSha256KnownAnswer was not expected") }
) : NativeVerificationApi {

    val cancelFlag = AtomicBoolean(false)
    /** Every [setCancel] call, in order. */
    val cancelCalls = CopyOnWriteArrayList<Boolean>()

    val runCalls = AtomicInteger(0)
    val lastTimeCap = AtomicLong(-1)
    val lastPercent = AtomicInteger(-1)

    val storageCalls = AtomicInteger(0)
    @Volatile var lastStorageArgs: List<Any>? = null

    val stampedIndices = CopyOnWriteArrayList<Long>()

    override fun runMemoryVerify(percentRam: Int, timeCapMs: Long): LongArray {
        runCalls.incrementAndGet()
        lastTimeCap.set(timeCapMs)
        lastPercent.set(percentRam)
        return onMemory(percentRam, timeCapMs, cancelFlag)
    }

    override fun prepareStoragePattern(chunk: ByteArray): Int = onPrepare(chunk)

    override fun stampStoragePattern(chunk: ByteArray, chunkIndex: Long): Int {
        stampedIndices += chunkIndex
        return onStamp(chunk, chunkIndex)
    }

    override fun runStorageVerify(path: String, startOffset: Long, lengthBytes: Long, budgetMs: Long): LongArray {
        storageCalls.incrementAndGet()
        lastStorageArgs = listOf(path, startOffset, lengthBytes, budgetMs)
        return onStorage(path, startOffset, lengthBytes, budgetMs, cancelFlag)
    }

    override fun runSieveKnownAnswer(): LongArray = onSieve()

    override fun runSha256KnownAnswer(): LongArray = onSha()

    override fun setCancel(cancel: Boolean) {
        cancelCalls += cancel
        cancelFlag.set(cancel)
    }
}
