package net.neuralcheck.util

import android.util.Log

/**
 * Kotlin entry-point for the V2 native stress library (libstress.so).
 *
 * Sub-phase 1a (current): only [pingNative] is implemented as a smoke-test
 * that proves the JNI bridge works end-to-end before any kernel lands.
 * CPU/memory/thermal/GPU-verification kernels arrive in 1b–1g; each adds
 * a corresponding `external fun` here plus a matching JNI function in
 * `app/src/main/cpp/jni/stress_jni.cpp`.
 *
 * The library is loaded once at object init. If loading fails (ABI
 * mismatch, stripped APK, OS denial, missing .so for the device's ABI)
 * [isLoaded] stays false and every subsequent external call would throw
 * UnsatisfiedLinkError. Caller sites should `runCatching` to keep the
 * rest of the scan working in degraded mode rather than aborting the
 * whole flow over a missing native lib.
 */
object StressNative {
    private const val TAG = "NeuralCheck.Native"

    /**
     * Sentinel value returned by [pingNative] on a healthy JNI bridge.
     * Anything else (including UnsatisfiedLinkError from a missing lib)
     * means the build pipeline is misconfigured — see sub-phase 1a in
     * IMPLEMENTATION_NOTES_V2.md.
     */
    const val PING_EXPECTED: Int = 42

    /**
     * π(10⁷) — count of primes ≤ 10,000,000. Published mathematical
     * constant (OEIS A006880). [runPrimeSieveTest] must return exactly
     * this value on a healthy CPU; any other value indicates silent
     * data corruption (bit flip in counter, bad cache line, etc.) —
     * see sub-phase 1b in IMPLEMENTATION_NOTES_V2.md.
     */
    const val PRIME_SIEVE_10M_EXPECTED: Long = 664_579L

    @Volatile
    private var libraryLoaded: Boolean = false

    init {
        try {
            System.loadLibrary("stress")
            libraryLoaded = true
            Log.i(TAG, "libstress.so loaded successfully")
        } catch (t: Throwable) {
            // Log but do not rethrow — the rest of the app must keep
            // running even if libstress.so is unavailable. Direct calls
            // to the external functions below will throw
            // UnsatisfiedLinkError at the call site; callers handle it.
            Log.e(
                TAG,
                "Failed to load libstress.so: ${t.javaClass.simpleName}: ${t.message}",
                t
            )
        }
    }

    /** True iff `libstress.so` loaded successfully at object init. False
     *  means every external call below will throw UnsatisfiedLinkError. */
    fun isLoaded(): Boolean = libraryLoaded

    /**
     * JNI smoke-test. Returns the literal [PING_EXPECTED] on success.
     * Used by sub-phase 1a (and the [verifyPing] helper below) to verify
     * the native ↔ Kotlin bridge before adding actual kernels. Any other
     * return value means the build pipeline is misconfigured.
     */
    external fun pingNative(): Int

    /**
     * Sub-phase 1b verification entry point: runs Sieve of Eratosthenes
     * to 10⁷ in native code and returns the prime count. Allocates ~10
     * MB on the heap; takes 50–250 ms on a typical 2020+ ARM64 device.
     * Returns -1 on allocation failure. See [verifyPrimeSieve] for the
     * convenience wrapper that asserts the value.
     */
    external fun runPrimeSieveTest(): Long

    /**
     * fieldsVersion 68 (T-20): the prime-sieve known answer WITH its raw count
     * and its status — [runPrimeSieveTest] returns only the count and a -1 that
     * means "could not allocate". LongArray layout: version, length, status
     * (0 match, 1 mismatch, 2 allocation failed), prime count (-1 = none),
     * duration ms; mirrored by `KnownAnswerLayout`.
     *
     * fieldsVersion 70: layout 2, six slots — the sixth is the limit N the sieve
     * was run to (D's own `PRIME_SIEVE_1M_LIMIT`, not the 10⁷ of [runPrimeSieveTest]).
     */
    external fun runSieveKnownAnswer(): LongArray

    /**
     * fieldsVersion 68 (T-20): the SHA-256 known answer with the CAUSE of a
     * failure — [runSha256Test] returns one boolean for "self-test failed",
     * "could not allocate" and "wrong digest" alike. LongArray layout: version,
     * length, status (0 match, 1 mismatch, 2 allocation failed, 3 self-test
     * failed), duration ms.
     */
    external fun runSha256KnownAnswer(): LongArray

    /**
     * Sub-phase 1c verification entry point: runs the SHA-256 NIST
     * self-test, then hashes a 1 MiB deterministic buffer and compares
     * to the precomputed reference. Returns true iff both steps pass.
     * Allocates ~1 MiB; takes 15–40 ms on a typical 2020+ ARM64 device
     * (scalar implementation; ARMv8 SHA crypto extensions land later).
     */
    external fun runSha256Test(): Boolean

    /**
     * Sub-phase 1d verification entry point: 256×256 FP32 matmul, both
     * scalar reference and NEON FMA, asserts trace(NEON) ≈ trace(scalar)
     * within 1e-7 ratio (Q2.1 tolerance). On mismatch the native side
     * runs the Tier 2 per-row CRC32 localisation and logs the first 8
     * diverging rows to Logcat. Allocates ~1 MiB; runs in 80–150 ms on
     * Snapdragon 855.
     */
    external fun runNeonFmaTest(): Boolean

    /**
     * Sub-phase 1e verification entry point: allocates a 32 MiB scratch
     * buffer and runs March C- + Walking Ones + Random+SHA in sequence.
     * Returns true iff all three pass with zero errors. Allocates 32
     * MiB; runs in ~600–1200 ms on Snapdragon 855 (March is the slowest
     * step at ~6 passes × 32 MiB linear access = ~200 MiB of read+write).
     */
    external fun runMemoryTest(): Boolean

    /**
     * Sub-phase 1f entry point. Reads `/sys/class/thermal/thermal_zone*`
     * and returns the first matching temperature in °C for each of four
     * categories, indexed by [THERMAL_SKIN], [THERMAL_BATTERY],
     * [THERMAL_CPU], [THERMAL_GPU]. Categories with no matching zone
     * are [THERMAL_NOT_FOUND] (-1.0f). Cheap call (~1 ms); used by V2's
     * StressTestService in every phase to populate skin_temp_c +
     * gpu_temp_c samples that the legacy /sys path missed.
     */
    external fun readThermalZones(): FloatArray

    // Indices into the float array returned by [readThermalZones].
    const val THERMAL_SKIN: Int    = 0
    const val THERMAL_BATTERY: Int = 1
    const val THERMAL_CPU: Int     = 2
    const val THERMAL_GPU: Int     = 3
    const val THERMAL_COUNT: Int   = 4
    const val THERMAL_NOT_FOUND: Float = -1.0f

    /**
     * Sub-phase 1g entry points. Pin the calling thread to a CPU
     * cluster (big = highest cpuinfo_max_freq, little = lowest, mid =
     * middle on 3-tier SoCs only). Return false on any error including
     * EPERM under restrictive security policies (Samsung Knox / MIUI);
     * the caller should treat that as "degraded mode, no pinning" and
     * keep the scan running rather than aborting.
     */
    external fun pinThreadToBigCluster(): Boolean
    external fun pinThreadToLittleCluster(): Boolean
    external fun resetAffinityToAll(): Boolean

    /**
     * Returns the SoC's cluster topology as a 5-int array:
     *   [0] total_cpus
     *   [1] distinct_clusters (1, 2, or 3)
     *   [2] big_cluster_size  (count of cores at the highest max_freq)
     *   [3] mid_cluster_size  (0 on 2-tier SoCs)
     *   [4] little_cluster_size
     * Empty array on enumeration failure (sysfs blocked).
     */
    external fun detectClusterTopology(): IntArray

    // ─────────── V2 Production stress entry points (master plan §3.2) ───────────

    /**
     * Starts a long-running CPU stress that pins (2 × cluster_size)
     * threads to the big + mid clusters and runs the NEON FMA kernel
     * in an infinite loop until [stopCpuStress] is called. Returns
     * 0 on success, -1 if a kernel was already running.
     *
     * `intensityFactor` (0.65 / 0.8 / 1.0) scales the inner-loop
     * iteration count per matmul — at 0.65 the kernel cycles through
     * the matrix ~65% of the V1 baseline before yielding to the
     * thread scheduler. The V2 service uses this when
     * [AdaptiveIntensityController] classifies the device as
     * budget_cooling / mid_cooling.
     *
     * `durationMs` is advisory — the kernel keeps running until
     * stopCpuStress regardless. Embedded in the verification result so
     * the post-run iteration-count math can compute MFLOPS accurately.
     */
    external fun startCpuStress(intensityFactor: Float, durationMs: Long): Long

    /**
     * Signals the running CPU stress to stop, joins all worker
     * threads, and returns aggregate verification + pinning telemetry.
     *
     * Array layout (V2 fix #56-3, fieldsVersion 59 — extended from 9 → 13):
     *   [0]  total_iterations  (across all threads, all clusters)
     *   [1]  verified_iterations
     *   [2]  fma_errors
     *   [3]  prime_sieve_correct (0 = false, 1 = true)
     *   [4]  sha_hashes_verified
     *   [5]  sha_hashes_attempted
     *   [6]  workers_pinned_big          (V2 fix #22)
     *   [7]  workers_pinned_mid          (V2 fix #22)
     *   [8]  workers_pin_failed_total    (V2 fix #22; sum of 10/11/12)
     *   [9]  workers_pinned_little       (V2 fix #56-3 — new)
     *   [10] workers_pin_failed_big      (V2 fix #56-3 — new)
     *   [11] workers_pin_failed_mid      (V2 fix #56-3 — new)
     *   [12] workers_pin_failed_little   (V2 fix #56-3 — new)
     *
     * Backward compatibility: callers reading size >= 5 work unchanged
     * (verification counters); the pinning telemetry tail is additive.
     * Returns an empty array if no kernel was running.
     */
    external fun stopCpuStress(): IntArray

    /**
     * fieldsVersion 68 (T-17): the memory verification producer. Maps a
     * buffer of `percentRam` percent of RAM (bounded by free memory, 512 MiB
     * floor, 8 GiB ceiling) and runs March C- + Walking Ones + a
     * size-independent random write/verify over it TILE BY TILE, starting no
     * new step after `timeCapMs` (a duration, ms). Replaces `runMemoryStress`,
     * which returned `{0,0,0,0}` on a failure and a constant 0 for the random
     * verify.
     *
     * Returns a LongArray in [net.neuralcheck.data.local.files.MemoryVerifyLayout]'s
     * layout (version, length, status, errno, requested bytes, buffer bytes,
     * tile bytes, tiles total, tiles completed, the three error counts in
     * bytes, elapsed ms). A failure is a status, never zeros. Blocks the
     * calling thread for up to the cap plus one step; see [setNativeVerifyCancel].
     */
    external fun runMemoryVerify(percentRam: Int, timeCapMs: Long): LongArray

    /**
     * fieldsVersion 68 (T-16): fills the writer's 1 MiB chunk with the base
     * storage pattern (once). The pattern is defined in C++ only, next to the
     * code that checks it. 0 = done, negative = the array is not exactly one chunk.
     */
    external fun prepareStoragePattern(chunk: ByteArray): Int

    /**
     * fieldsVersion 68 (T-16): stamps the chunk with chunk number `chunkIndex`'s
     * per-page offset stamps (256 x 16 bytes). Called before each write; the
     * pattern is then a function of the byte offset. 0 = done.
     */
    external fun stampStoragePattern(chunk: ByteArray, chunkIndex: Long): Int

    /**
     * fieldsVersion 68 (T-16): reads `[startOffset, startOffset + lengthBytes)` of
     * `path` back (O_DIRECT, falling back to DONTNEED + a buffered read) and
     * compares every 1 MiB block with the pattern. `budgetMs` is a duration.
     * Returns a LongArray in [net.neuralcheck.data.local.files.StorageVerifyLayout]'s
     * layout. Blocks the calling thread up to the budget; see [setNativeVerifyCancel].
     */
    external fun runStorageVerify(path: String, startOffset: Long, lengthBytes: Long, budgetMs: Long): LongArray

    /**
     * Raises (`true`) or clears (`false`) the cancel flag the long verification
     * producers ([runMemoryVerify], and the storage read-back) poll between
     * steps. The caller owns the protocol: set it, wait for the native call to
     * return, clear it — the producers never clear it themselves.
     */
    external fun setNativeVerifyCancel(cancel: Boolean)

    /**
     * Returns the instantaneous package power in milliwatts. Bug 2 fix:
     * delegates to BatteryReader so vendor scaling/sign detection is applied
     * uniformly. The signature retains a Context parameter for backward
     * compatibility with V2 sampler call sites; internally it constructs
     * a transient BatteryReader. For high-frequency sampling the V2 path
     * should call BatteryReader.readPackagePowerMw directly via
     * DisciplinedSampler — see Tedeel 5 below.
     */
    @Deprecated(
        "Use DisciplinedSampler's batteryReader.readPackagePowerMw() instead",
        replaceWith = ReplaceWith("batteryReader.readPackagePowerMw()")
    )
    fun readPackagePower(context: android.content.Context): Float {
        return try {
            // Construct a transient reader. Doesn't share the scaling
            // lock with the long-lived service-scoped reader, so the
            // first few calls run on the default heuristic until a
            // decisive raw reading arrives. Acceptable because V2
            // sampler is migrated to call BatteryReader directly.
            val reader = net.neuralcheck.service.collector.BatteryReader(context)
            reader.register()
            val mw = reader.readPackagePowerMw()
            reader.unregister()
            mw
        } catch (t: Throwable) {
            Log.w(TAG, "readPackagePower failed: ${t.message}")
            -1f
        }
    }

    /**
     * Convenience wrapper around [pingNative] that catches the two failure
     * modes (lib not loaded → UnsatisfiedLinkError; symbol returns the
     * wrong value → assertion in caller) and reports both as a single
     * boolean. Logs the outcome to the `NeuralCheck.Native` tag so the
     * sub-phase 1a manual on-device test can be verified via:
     *     adb logcat | grep NeuralCheck.Native
     */
    fun verifyPing(): Boolean = try {
        val result = pingNative()
        val ok = result == PING_EXPECTED
        if (ok) {
            Log.i(TAG, "verifyPing: OK (got $result)")
        } else {
            Log.w(TAG, "verifyPing: WRONG VALUE (got $result, expected $PING_EXPECTED)")
        }
        ok
    } catch (t: Throwable) {
        Log.e(TAG, "verifyPing: FAILED — ${t.javaClass.simpleName}: ${t.message}", t)
        false
    }

    /**
     * Convenience wrapper around [runPrimeSieveTest] that catches both
     * UnsatisfiedLinkError (lib not loaded) and a wrong-count return,
     * reports both as a single boolean, and logs the outcome to the
     * `NeuralCheck.Native` tag for sub-phase 1b's manual on-device
     * verification:
     *     adb logcat | grep NeuralCheck.Native
     */
    fun verifyPrimeSieve(): Boolean = try {
        val count = runPrimeSieveTest()
        val ok = count == PRIME_SIEVE_10M_EXPECTED
        if (ok) {
            Log.i(TAG, "verifyPrimeSieve: OK (got $count)")
        } else {
            Log.w(
                TAG,
                "verifyPrimeSieve: WRONG VALUE " +
                    "(got $count, expected $PRIME_SIEVE_10M_EXPECTED)"
            )
        }
        ok
    } catch (t: Throwable) {
        Log.e(TAG, "verifyPrimeSieve: FAILED — ${t.javaClass.simpleName}: ${t.message}", t)
        false
    }

    /**
     * Convenience wrapper around [runSha256Test] that catches
     * UnsatisfiedLinkError, reports the result as a single boolean, and
     * logs the outcome. Native side already logs the actual-vs-expected
     * digest bytes on mismatch — see runSha256Test in stress_jni.cpp.
     */
    fun verifySha256(): Boolean = try {
        val ok = runSha256Test()
        if (ok) {
            Log.i(TAG, "verifySha256: OK")
        } else {
            Log.w(TAG, "verifySha256: FAILED — see preceding native log for details")
        }
        ok
    } catch (t: Throwable) {
        Log.e(TAG, "verifySha256: FAILED — ${t.javaClass.simpleName}: ${t.message}", t)
        false
    }

    /**
     * Convenience wrapper around [runNeonFmaTest]. Native side already
     * logs the trace values, the diff vs threshold, and (on mismatch)
     * the first 8 diverging rows — see runNeonFmaTest in stress_jni.cpp.
     */
    fun verifyNeonFma(): Boolean = try {
        val ok = runNeonFmaTest()
        if (ok) {
            Log.i(TAG, "verifyNeonFma: OK")
        } else {
            Log.w(TAG, "verifyNeonFma: FAILED — see preceding native log for details")
        }
        ok
    } catch (t: Throwable) {
        Log.e(TAG, "verifyNeonFma: FAILED — ${t.javaClass.simpleName}: ${t.message}", t)
        false
    }

    /**
     * Convenience wrapper around [runMemoryTest]. Native side logs each
     * of the three sub-tests' error counts plus the overall verdict.
     */
    fun verifyMemory(): Boolean = try {
        val ok = runMemoryTest()
        if (ok) {
            Log.i(TAG, "verifyMemory: OK")
        } else {
            Log.w(TAG, "verifyMemory: FAILED — see preceding native log for details")
        }
        ok
    } catch (t: Throwable) {
        Log.e(TAG, "verifyMemory: FAILED — ${t.javaClass.simpleName}: ${t.message}", t)
        false
    }

    /**
     * Convenience wrapper around [readThermalZones] for the smoke test.
     * Returns true if at least one of the four categories produced a
     * non-sentinel reading — most devices expose at least battery or
     * cpu, so a fully empty result is itself a defect signal worth
     * surfacing. The native side already logs the four values.
     */
    fun verifyThermalZones(): Boolean = try {
        val temps = readThermalZones()
        val any = temps.any { it != THERMAL_NOT_FOUND }
        if (any) {
            Log.i(TAG, "verifyThermalZones: OK (got ${temps.toList()})")
        } else {
            Log.w(TAG, "verifyThermalZones: NO ZONES MATCHED — sysfs may be SELinux-blocked")
        }
        any
    } catch (t: Throwable) {
        Log.e(TAG, "verifyThermalZones: FAILED — ${t.javaClass.simpleName}: ${t.message}", t)
        false
    }

    /**
     * Sub-phase 1g smoke test. Detects topology, pins the current
     * thread to the big cluster, restores affinity, and reports the
     * outcome. Returns true only when topology was detected AND
     * pinning succeeded; pinning EPERM (Samsung Knox / MIUI) returns
     * false but logs the topology — see Q2.6 in
     * IMPLEMENTATION_NOTES_V2.md for the degraded-mode rationale.
     */
    fun verifyAffinity(): Boolean {
        return try {
            val topo = detectClusterTopology()
            if (topo.isEmpty() || topo[0] == 0) {
                Log.w(TAG, "verifyAffinity: cluster topology unavailable (sysfs blocked?)")
                return false
            }
            Log.i(
                TAG,
                "verifyAffinity: topology cpus=${topo[0]} clusters=${topo[1]} " +
                    "big=${topo[2]} mid=${topo[3]} little=${topo[4]}"
            )
            val pinOk = pinThreadToBigCluster()
            // Always restore — leaving the JVM-managed thread pinned
            // would affect every subsequent JNI call on the same thread.
            runCatching { resetAffinityToAll() }
            if (!pinOk) {
                Log.w(TAG, "verifyAffinity: pin failed (likely EPERM) — running degraded")
            }
            pinOk
        } catch (t: Throwable) {
            Log.e(TAG, "verifyAffinity: FAILED — ${t.javaClass.simpleName}: ${t.message}", t)
            false
        }
    }
}
