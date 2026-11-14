package net.neuralcheck.service.stress

import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random

/**
 * CPU stress generator using matrix multiplication across all cores (ADR 4.9).
 * Creates 2× core count threads to ensure all cores are saturated.
 * Each thread runs an infinite loop of 128×128 float matrix multiplication.
 */
class CpuLoadGenerator {

    companion object { private const val TAG = "NeuralCheck" }

    private val isRunning = AtomicBoolean(false)
    private val threads = mutableListOf<Thread>()
    private val iterationCounters = ConcurrentHashMap<Int, AtomicLong>()

    fun start() {
        if (isRunning.get()) return
        isRunning.set(true)

        val coreCount = Runtime.getRuntime().availableProcessors()
        val threadCount = coreCount * 2 // Ensure all cores saturated

        repeat(threadCount) { index ->
            iterationCounters[index] = AtomicLong(0)
            val thread = Thread {
                // Pre-allocate matrices outside the loop
                val n = 128
                val matA = FloatArray(n * n) { Random.nextFloat() }
                val matB = FloatArray(n * n) { Random.nextFloat() }
                val matC = FloatArray(n * n)
                var iterations = 0L

                // Catch OOM / unexpected runtime errors so one dying worker can
                // never silently leave a core unloaded — we log and exit this
                // thread but other workers keep running.
                try {
                    while (isRunning.get()) {
                        multiplyMatrices(matA, matB, matC, n)
                        iterations++
                        if (iterations % 100 == 0L) {
                            iterationCounters[index]?.set(iterations)
                            if (!isRunning.get()) break
                        }
                    }
                } catch (t: Throwable) {
                    Log.w(TAG, "CPU worker $index exited with error: ${t.message}")
                }
            }
            thread.priority = Thread.MAX_PRIORITY
            thread.name = "NeuralCheck-CPU-$index"
            thread.isDaemon = true
            threads.add(thread)
            thread.start()
        }
    }

    fun stop() {
        isRunning.set(false)
        threads.forEach { thread ->
            try {
                thread.join(2000)
            } catch (e: InterruptedException) {
                thread.interrupt()
            }
        }
        threads.clear()
    }

    fun isActive(): Boolean = isRunning.get()

    fun getTotalIterations(): Long = iterationCounters.values.sumOf { it.get() }

    fun resetCounters() {
        iterationCounters.values.forEach { it.set(0) }
    }

    fun getThreadCount(): Int = threads.size

    /**
     * Calculate MFLOPS based on iterations in the given time window.
     * Each 128x128 matrix multiply = 2 * 128^3 FLOPs ≈ 4.2M FLOPs
     */
    fun calculateMflops(iterations: Long, durationMs: Long): Float {
        if (durationMs <= 0) return 0f
        val flopsPerIteration = 2.0 * 128.0 * 128.0 * 128.0
        val totalFlops = iterations * flopsPerIteration
        val seconds = durationMs / 1000.0
        return (totalFlops / seconds / 1_000_000.0).toFloat()
    }

    private fun multiplyMatrices(a: FloatArray, b: FloatArray, c: FloatArray, n: Int) {
        for (i in 0 until n) {
            for (j in 0 until n) {
                var sum = 0f
                for (k in 0 until n) {
                    sum += a[i * n + k] * b[k * n + j]
                }
                c[i * n + j] = sum
            }
        }
    }
}
