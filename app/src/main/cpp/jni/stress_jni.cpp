// NeuralCheck V2 — JNI bridge for libstress.so.
//
// Sub-phase 1a: only pingNative() is implemented, as the smoke-test that
// proves JNI plumbing (symbol mangling, calling convention, jint width,
// .so loading) works end-to-end before any kernel arrives.
//
// All public symbols follow the JNI spec mangling:
//     Java_<package>_<class>_<method>
// where '.' becomes '_', '_' becomes '_1', and '$' becomes '_00024'.
// `extern "C"` is mandatory — the JVM looks up symbols by exact C name,
// and C++ name mangling would prevent the lookup from finding them.

#include <jni.h>
#include <android/log.h>

#include <cstring>
#include <memory>

#include "reference/reference_values.h"
#include "stress/cpu_affinity.h"
#include "stress/known_answer.h"
#include "stress/memory_test.h"
#include "stress/memory_verify.h"
#include "stress/native_common.h"
#include "stress/neon_fma.h"
#include "stress/prime_sieve.h"
#include "stress/production_stress.h"
#include "stress/sha256.h"
#include "stress/storage_verify.h"
#include "stress/thermal_reader.h"

namespace {

constexpr const char* kLogTag = "NeuralCheck.Native";

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  kLogTag, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN,  kLogTag, __VA_ARGS__)

}  // namespace

extern "C" JNIEXPORT jint JNICALL
Java_net_neuralcheck_util_StressNative_pingNative(JNIEnv* /*env*/, jobject /*thiz*/) {
    // 42 is the canonical "I'm alive" sentinel. The Kotlin caller asserts
    // exactly this value — any other return means JNI plumbing is wrong
    // (wrong calling convention, wrong symbol name, wrong jint width).
    LOGI("pingNative: returning 42");
    return 42;
}

extern "C" JNIEXPORT jlong JNICALL
Java_net_neuralcheck_util_StressNative_runPrimeSieveTest(JNIEnv* /*env*/,
                                                         jobject /*thiz*/) {
    // Sub-phase 1b verification entry point. Runs Eratosthenes to 10⁷
    // and returns the prime count; Kotlin asserts this against
    // PRIME_SIEVE_10M_REFERENCE. Stays in the codebase post-1b as a unit
    // test hook — when the kernel is integrated into the parallel CPU
    // stress (sub-phase 1d/1e), the production path will call the
    // underlying C++ function directly without going through this JNI
    // shim, but this entry point keeps the algorithm individually
    // testable.
    LOGI("runPrimeSieveTest: starting (limit=%u)", PRIME_SIEVE_10M_LIMIT);
    const int64_t count = neuralcheck::stress::prime_sieve_count(PRIME_SIEVE_10M_LIMIT);
    LOGI("runPrimeSieveTest: count=%lld (expected %d)",
         static_cast<long long>(count),
         PRIME_SIEVE_10M_REFERENCE);
    return static_cast<jlong>(count);
}

extern "C" JNIEXPORT jlong JNICALL
Java_net_neuralcheck_util_StressNative_startCpuStress(JNIEnv* /*env*/,
                                                      jobject /*thiz*/,
                                                      jfloat intensity_factor,
                                                      jlong duration_ms) {
    // V2 production CPU stress entry. Spawns workers on big+mid
    // clusters running NEON FMA + SHA in a tight loop until
    // stopCpuStress(). intensity_factor scales heat generation per
    // master plan §2.6.
    LOGI("startCpuStress: intensity=%.2f duration_ms=%lld",
         static_cast<double>(intensity_factor),
         static_cast<long long>(duration_ms));
    return static_cast<jlong>(
        neuralcheck::stress::start_cpu_stress(intensity_factor, duration_ms)
    );
}

extern "C" JNIEXPORT jintArray JNICALL
Java_net_neuralcheck_util_StressNative_stopCpuStress(JNIEnv* env,
                                                     jobject /*thiz*/) {
    const auto result = neuralcheck::stress::stop_cpu_stress();
    LOGI("stopCpuStress: total=%lld verified=%lld fma_err=%d prime=%d "
         "sha_ver=%d sha_att=%d pinned(b=%d m=%d l=%d) "
         "pin_failed(total=%d b=%d m=%d l=%d)",
         static_cast<long long>(result.total_iterations),
         static_cast<long long>(result.verified_iterations),
         result.fma_errors,
         result.prime_sieve_correct ? 1 : 0,
         result.sha_hashes_verified,
         result.sha_hashes_attempted,
         result.workers_pinned_big,
         result.workers_pinned_mid,
         result.workers_pinned_little,
         result.workers_pin_failed,
         result.workers_pin_failed_big,
         result.workers_pin_failed_mid,
         result.workers_pin_failed_little);

    // V2 fix #56-3 (fieldsVersion 59): array layout extended from 9 to 13.
    // Old Kotlin readers (size >= 5) still work because they index 0..5
    // for the verification counters; pinning telemetry is at the tail.
    jintArray arr = env->NewIntArray(13);
    if (arr != nullptr) {
        const jint values[13] = {
            static_cast<jint>(result.total_iterations),
            static_cast<jint>(result.verified_iterations),
            static_cast<jint>(result.fma_errors),
            result.prime_sieve_correct ? jint{1} : jint{0},
            static_cast<jint>(result.sha_hashes_verified),
            static_cast<jint>(result.sha_hashes_attempted),
            // V2 fix #22 pinning telemetry — backward-compatible layout.
            //   [6] big-cluster pinned count
            //   [7] mid-cluster pinned count
            //   [8] aggregate pin-failure count (sum of [10..12])
            static_cast<jint>(result.workers_pinned_big),
            static_cast<jint>(result.workers_pinned_mid),
            static_cast<jint>(result.workers_pin_failed),
            // V2 fix #56-3 (fieldsVersion 59) — per-cluster split.
            //   [9]  little-cluster pinned count (NEW)
            //   [10] big-cluster pin failure count (NEW; subset of [8])
            //   [11] mid-cluster pin failure count (NEW; subset of [8])
            //   [12] little-cluster pin failure count (NEW; subset of [8])
            static_cast<jint>(result.workers_pinned_little),
            static_cast<jint>(result.workers_pin_failed_big),
            static_cast<jint>(result.workers_pin_failed_mid),
            static_cast<jint>(result.workers_pin_failed_little)
        };
        env->SetIntArrayRegion(arr, 0, 13, values);
    }
    return arr;
}

// fieldsVersion 68 (T-17): the memory verification producer. Replaces
// runMemoryStress, which returned {0,0,0,0} on a failure (read as "no errors,
// nothing tested") and a constant 0 for the random verify.
//
// Returns a LongArray in the layout Kotlin's MemoryVerifyLayout mirrors:
//   [0]  layout version (2; 1 had 13 entries, without [13])
//   [1]  array length  (14) — a reader that finds another length knows it is
//                        not talking to the build it was written against
//   [2]  status        (MemoryVerifyStatus: 0 ran, 1 partial/time cap,
//                        2 partial/cancelled, 3 sysinfo failed,
//                        4 allocation failed, 5 invalid argument,
//                        6 insufficient free memory)
//   [3]  errno of a sysinfo / mmap failure, else 0
//   [4]  requested buffer bytes
//   [5]  buffer bytes (whole tiles actually mapped)
//   [6]  tile bytes
//   [7]  tiles total
//   [8]  tiles completed
//   [9]  march errors (bytes)
//   [10] walking-ones errors (bytes)
//   [11] random-verify errors (bytes)
//   [12] elapsed ms
//   [13] free memory as sysinfo reported it, bytes (-1 = sysinfo failed: never read)
extern "C" JNIEXPORT jlongArray JNICALL
Java_net_neuralcheck_util_StressNative_runMemoryVerify(JNIEnv* env,
                                                       jobject /*thiz*/,
                                                       jint percent_ram,
                                                       jlong time_cap_ms) {
    LOGI("runMemoryVerify: percent_ram=%d time_cap_ms=%lld",
         percent_ram, static_cast<long long>(time_cap_ms));
    const auto r = neuralcheck::stress::run_memory_verify(percent_ram, time_cap_ms);
    LOGI("runMemoryVerify done: status=%d errno=%d free=%lld tiles=%lld/%lld tile=%lld "
         "march=%lld walk=%lld random=%lld elapsed_ms=%lld",
         r.status, r.err_no, static_cast<long long>(r.free_bytes),
         static_cast<long long>(r.tiles_completed), static_cast<long long>(r.tiles_total),
         static_cast<long long>(r.tile_bytes),
         static_cast<long long>(r.march_errors), static_cast<long long>(r.walking_ones_errors),
         static_cast<long long>(r.random_verify_errors), static_cast<long long>(r.elapsed_ms));

    constexpr jsize kLen = 14;
    jlongArray arr = env->NewLongArray(kLen);
    if (arr != nullptr) {
        const jlong values[kLen] = {
            2, kLen,
            r.status, r.err_no,
            r.requested_bytes, r.buffer_bytes, r.tile_bytes,
            r.tiles_total, r.tiles_completed,
            r.march_errors, r.walking_ones_errors, r.random_verify_errors,
            r.elapsed_ms, r.free_bytes
        };
        env->SetLongArrayRegion(arr, 0, kLen, values);
    }
    return arr;
}

// The cancel flag of the verification producers (memory, storage). The
// Kotlin caller owns the protocol: set it, wait for the native call to
// return, clear it. The producers only read it.
extern "C" JNIEXPORT void JNICALL
Java_net_neuralcheck_util_StressNative_setNativeVerifyCancel(JNIEnv* /*env*/,
                                                             jobject /*thiz*/,
                                                             jboolean cancel) {
    neuralcheck::stress::native_verify_cancel_flag().store(
        cancel == JNI_TRUE, std::memory_order_relaxed);
}

// fieldsVersion 68 (T-16): the storage write pattern. The writer (Kotlin, one
// FileOutputStream.write per MiB) keeps ONE 1 MiB ByteArray; this fills it with
// the base pattern once and re-stamps it per chunk, so the pattern is defined
// only in storage_verify.cpp, next to the code that checks it. Returns 0, or
// -1 when the array is not exactly one chunk long.
extern "C" JNIEXPORT jint JNICALL
Java_net_neuralcheck_util_StressNative_prepareStoragePattern(JNIEnv* env,
                                                             jobject /*thiz*/,
                                                             jbyteArray chunk) {
    if (chunk == nullptr ||
        env->GetArrayLength(chunk) != static_cast<jsize>(neuralcheck::stress::kStorageChunkBytes)) {
        return -1;
    }
    void* p = env->GetPrimitiveArrayCritical(chunk, nullptr);
    if (p == nullptr) return -2;
    neuralcheck::stress::storage_fill_base_chunk(static_cast<uint8_t*>(p));
    env->ReleasePrimitiveArrayCritical(chunk, p, 0);
    return 0;
}

extern "C" JNIEXPORT jint JNICALL
Java_net_neuralcheck_util_StressNative_stampStoragePattern(JNIEnv* env,
                                                           jobject /*thiz*/,
                                                           jbyteArray chunk,
                                                           jlong chunk_index) {
    if (chunk == nullptr ||
        env->GetArrayLength(chunk) != static_cast<jsize>(neuralcheck::stress::kStorageChunkBytes)) {
        return -1;
    }
    void* p = env->GetPrimitiveArrayCritical(chunk, nullptr);
    if (p == nullptr) return -2;
    neuralcheck::stress::storage_stamp_chunk(static_cast<uint8_t*>(p), chunk_index);
    env->ReleasePrimitiveArrayCritical(chunk, p, 0);
    return 0;
}

// fieldsVersion 68 (T-16): the storage read-back. Returns a LongArray in the
// layout Kotlin's StorageVerifyLayout mirrors:
//   [0]  layout version (1)
//   [1]  array length  (12)
//   [2]  status        (StorageVerifyStatus: 0 ran, 1 partial/time, 2 partial/cancelled,
//                        3 open failed, 4 read failed, 5 allocation failed, 6 invalid argument)
//   [3]  method        (0 O_DIRECT, 1 DONTNEED fallback)
//   [4]  fallback stage (0 none, 1 open, 2 first read)
//   [5]  errno that forced the fallback, else 0
//   [6]  errno of a failure status, else 0
//   [7]  bytes planned
//   [8]  bytes compared
//   [9]  1 MiB blocks mismatched (incl. EIO / missing)
//   [10] the EIO / missing subset of [9]
//   [11] elapsed ms
extern "C" JNIEXPORT jlongArray JNICALL
Java_net_neuralcheck_util_StressNative_runStorageVerify(JNIEnv* env,
                                                        jobject /*thiz*/,
                                                        jstring path,
                                                        jlong start_offset,
                                                        jlong length_bytes,
                                                        jlong budget_ms) {
    const char* c_path = (path != nullptr) ? env->GetStringUTFChars(path, nullptr) : nullptr;
    LOGI("runStorageVerify: offset=%lld length=%lld budget_ms=%lld",
         static_cast<long long>(start_offset), static_cast<long long>(length_bytes),
         static_cast<long long>(budget_ms));
    const auto r = neuralcheck::stress::run_storage_verify(c_path, start_offset, length_bytes, budget_ms);
    if (c_path != nullptr) env->ReleaseStringUTFChars(path, c_path);
    LOGI("runStorageVerify done: status=%d method=%d fallback(stage=%d errno=%d) errno=%d "
         "compared=%lld/%lld mismatched=%lld io_errors=%lld elapsed_ms=%lld",
         r.status, r.method, r.fallback_stage, r.fallback_errno, r.err_no,
         static_cast<long long>(r.bytes_compared), static_cast<long long>(r.bytes_planned),
         static_cast<long long>(r.blocks_mismatched), static_cast<long long>(r.io_error_blocks),
         static_cast<long long>(r.elapsed_ms));

    constexpr jsize kLen = 12;
    jlongArray arr = env->NewLongArray(kLen);
    if (arr != nullptr) {
        const jlong values[kLen] = {
            1, kLen,
            r.status, r.method, r.fallback_stage, r.fallback_errno, r.err_no,
            r.bytes_planned, r.bytes_compared, r.blocks_mismatched, r.io_error_blocks,
            r.elapsed_ms
        };
        env->SetLongArrayRegion(arr, 0, kLen, values);
    }
    return arr;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_net_neuralcheck_util_StressNative_pinThreadToBigCluster(JNIEnv* /*env*/,
                                                             jobject /*thiz*/) {
    const bool ok = neuralcheck::stress::pin_to_big_cluster();
    LOGI("pinThreadToBigCluster: %s", ok ? "OK" : "FAILED (likely EPERM)");
    return ok ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_net_neuralcheck_util_StressNative_pinThreadToLittleCluster(JNIEnv* /*env*/,
                                                                jobject /*thiz*/) {
    const bool ok = neuralcheck::stress::pin_to_little_cluster();
    LOGI("pinThreadToLittleCluster: %s", ok ? "OK" : "FAILED (likely EPERM)");
    return ok ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_net_neuralcheck_util_StressNative_resetAffinityToAll(JNIEnv* /*env*/,
                                                          jobject /*thiz*/) {
    const bool ok = neuralcheck::stress::reset_affinity_to_all();
    LOGI("resetAffinityToAll: %s", ok ? "OK" : "FAILED");
    return ok ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jintArray JNICALL
Java_net_neuralcheck_util_StressNative_detectClusterTopology(JNIEnv* env,
                                                             jobject /*thiz*/) {
    // Returns [total_cpus, distinct_clusters, big_size, mid_size, little_size].
    // Used by the smoke test (and later by adaptive intensity logic) to
    // log SoC topology before pinning kernels to clusters.
    const auto t = neuralcheck::stress::detect_cluster_topology();
    LOGI("detectClusterTopology: cpus=%d clusters=%d big=%d mid=%d little=%d",
         t.total_cpus, t.distinct_clusters,
         t.big_cluster_size, t.mid_cluster_size, t.little_cluster_size);

    jintArray result = env->NewIntArray(5);
    if (result != nullptr) {
        const jint values[5] = {
            t.total_cpus, t.distinct_clusters,
            t.big_cluster_size, t.mid_cluster_size, t.little_cluster_size,
        };
        env->SetIntArrayRegion(result, 0, 5, values);
    }
    return result;
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_net_neuralcheck_util_StressNative_readThermalZones(JNIEnv* env,
                                                        jobject /*thiz*/) {
    // Sub-phase 1f entry point. Returns a 4-element float array with
    // the first temperatures matched in [skin, battery, cpu, gpu]
    // order. Slots that found no matching zone are -1.0f. Cheap (~1
    // ms) — just file reads from sysfs, no allocations beyond the JNI
    // array.
    float values[neuralcheck::stress::kThermalCount];
    neuralcheck::stress::read_thermal_zones(values);

    LOGI("readThermalZones: skin=%.1f battery=%.1f cpu=%.1f gpu=%.1f",
         static_cast<double>(values[neuralcheck::stress::kThermalSkin]),
         static_cast<double>(values[neuralcheck::stress::kThermalBattery]),
         static_cast<double>(values[neuralcheck::stress::kThermalCpu]),
         static_cast<double>(values[neuralcheck::stress::kThermalGpu]));

    jfloatArray result = env->NewFloatArray(neuralcheck::stress::kThermalCount);
    if (result != nullptr) {
        env->SetFloatArrayRegion(result, 0,
                                 neuralcheck::stress::kThermalCount,
                                 values);
    }
    return result;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_net_neuralcheck_util_StressNative_runMemoryTest(JNIEnv* /*env*/,
                                                     jobject /*thiz*/) {
    // Sub-phase 1e verification entry. Allocates a 32 MiB scratch
    // buffer and runs three classical memory-defect kernels in
    // sequence: March C- (stuck-at + adjacent coupling), walking ones
    // (address decoder + bit-line), random+SHA (data-dependent
    // coupling). Returns true iff all three pass with zero errors.
    //
    // 32 MiB is fixed because the random+SHA reference is tied to that
    // size (see MEMORY_RANDOM_SHA_BUFFER_SIZE). A future stress phase
    // can run the same kernels against a larger 40%-of-RAM allocation,
    // but skip the SHA stage there or hash the result without comparing.
    LOGI("runMemoryTest: starting (32 MiB, March + Walking + Random+SHA)");

    auto buf = std::unique_ptr<uint8_t[]>(
        new (std::nothrow) uint8_t[MEMORY_RANDOM_SHA_BUFFER_SIZE]);
    if (!buf) {
        LOGW("runMemoryTest: failed to allocate %u bytes",
             MEMORY_RANDOM_SHA_BUFFER_SIZE);
        return JNI_FALSE;
    }

    const int64_t march_errors =
        neuralcheck::stress::march_c_minus(buf.get(), MEMORY_RANDOM_SHA_BUFFER_SIZE);
    LOGI("runMemoryTest:   March C- errors=%lld", static_cast<long long>(march_errors));

    const int64_t walk_errors =
        neuralcheck::stress::walking_ones(buf.get(), MEMORY_RANDOM_SHA_BUFFER_SIZE);
    LOGI("runMemoryTest:   Walking Ones errors=%lld", static_cast<long long>(walk_errors));

    const bool sha_ok = neuralcheck::stress::random_pattern_sha_verify(
        buf.get(), MEMORY_RANDOM_SHA_BUFFER_SIZE);
    LOGI("runMemoryTest:   Random+SHA %s", sha_ok ? "OK" : "FAILED");

    const bool all_ok = (march_errors == 0) && (walk_errors == 0) && sha_ok;
    LOGI("runMemoryTest: %s (march=%lld walk=%lld sha=%d)",
         all_ok ? "ALL OK" : "DEFECTS DETECTED",
         static_cast<long long>(march_errors), static_cast<long long>(walk_errors),
         sha_ok ? 1 : 0);
    return all_ok ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_net_neuralcheck_util_StressNative_runNeonFmaTest(JNIEnv* /*env*/,
                                                      jobject /*thiz*/) {
    // Sub-phase 1d verification entry. Generates two deterministic
    // 256×256 FP32 matrices, runs both the scalar reference matmul AND
    // the NEON FMA matmul on the same inputs, and asserts the trace
    // values agree within Q2.1 tolerance. Allocates ~3 × 256 KiB on
    // the heap; runs in ~80–150 ms on Snapdragon 855 (most of which is
    // the slower scalar reference path).
    LOGI("runNeonFmaTest: starting (256x256 FP32, two-tier verification)");

    using neuralcheck::stress::kNeonFmaCells;
    using neuralcheck::stress::kNeonFmaToleranceRatio;

    auto A = std::unique_ptr<float[]>(new (std::nothrow) float[kNeonFmaCells]);
    auto B = std::unique_ptr<float[]>(new (std::nothrow) float[kNeonFmaCells]);
    auto C_ref = std::unique_ptr<float[]>(new (std::nothrow) float[kNeonFmaCells]);
    auto C_neon = std::unique_ptr<float[]>(new (std::nothrow) float[kNeonFmaCells]);
    if (!A || !B || !C_ref || !C_neon) {
        LOGW("runNeonFmaTest: allocation failed (need ~1 MiB total)");
        return JNI_FALSE;
    }

    // Distinct seeds so A != B; LCG sequence is patternless but still
    // 100% deterministic across runs and devices.
    neuralcheck::stress::neon_fma_generate_matrix(A.get(), 0xC0FFEE01u);
    neuralcheck::stress::neon_fma_generate_matrix(B.get(), 0xCAFEBABEu);

    neuralcheck::stress::neon_fma_matmul_scalar(A.get(), B.get(), C_ref.get());
    neuralcheck::stress::neon_fma_matmul_neon(A.get(), B.get(), C_neon.get());

    const float trace_ref = neuralcheck::stress::neon_fma_trace(C_ref.get());
    const float trace_neon = neuralcheck::stress::neon_fma_trace(C_neon.get());

    const float diff = trace_neon - trace_ref;
    const float abs_diff = diff < 0.0f ? -diff : diff;
    const float abs_ref = trace_ref < 0.0f ? -trace_ref : trace_ref;
    const float threshold = abs_ref * kNeonFmaToleranceRatio;
    const bool tier1_ok = abs_diff <= threshold;

    LOGI("runNeonFmaTest: trace_ref=%.6f trace_neon=%.6f diff=%.6e (threshold=%.6e)",
         static_cast<double>(trace_ref),
         static_cast<double>(trace_neon),
         static_cast<double>(abs_diff),
         static_cast<double>(threshold));

    if (!tier1_ok) {
        // Tier 2 hook: localise to the first row that doesn't match by
        // CRC32. Since we don't have a hardcoded reference array yet,
        // we compare NEON CRC against scalar CRC for each row — same
        // intent (find which row diverges) without persistent state.
        LOGW("runNeonFmaTest: TIER 1 FAIL — running TIER 2 row-CRC localisation");
        size_t mismatched_rows = 0;
        for (size_t i = 0; i < neuralcheck::stress::kNeonFmaDim; ++i) {
            const uint32_t crc_ref =
                neuralcheck::stress::neon_fma_row_crc32(&C_ref[i * neuralcheck::stress::kNeonFmaDim]);
            const uint32_t crc_neon =
                neuralcheck::stress::neon_fma_row_crc32(&C_neon[i * neuralcheck::stress::kNeonFmaDim]);
            if (crc_ref != crc_neon) {
                if (mismatched_rows < 8) {
                    LOGW("  row %zu: crc_ref=0x%08x crc_neon=0x%08x",
                         i, crc_ref, crc_neon);
                }
                ++mismatched_rows;
            }
        }
        LOGW("runNeonFmaTest: TIER 2 — %zu rows diverge (256 total)", mismatched_rows);
    }

    return tier1_ok ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_net_neuralcheck_util_StressNative_runSha256Test(JNIEnv* /*env*/,
                                                     jobject /*thiz*/) {
    // Sub-phase 1c verification entry point. Two-step verification:
    //   1. Self-test against FIPS 180-4 "abc" — fails fast if the SHA
    //      implementation itself is broken (would corrupt step 2).
    //   2. Hash a 1 MiB LCG-generated buffer and compare against the
    //      precomputed reference. Bit-exact comparison; no tolerance.
    // fieldsVersion 68: the two steps live in sha256_known_answer(), shared with
    // runSha256KnownAnswer below (which keeps the cause instead of a bare bool).
    LOGI("runSha256Test: starting");
    const auto r = neuralcheck::stress::sha256_known_answer();
    switch (r.status) {
        case neuralcheck::stress::kKnownAnswerMatch:
            LOGI("runSha256Test: hash matches reference");
            break;
        case neuralcheck::stress::kKnownAnswerSelfTestFailed:
            LOGW("runSha256Test: SHA-256 self-test FAILED — implementation is broken");
            break;
        case neuralcheck::stress::kKnownAnswerAllocationFailed:
            LOGW("runSha256Test: failed to allocate %u bytes", SHA256_TEST_INPUT_SIZE);
            break;
        default:
            // Print first 8 bytes of actual + expected so on-device debug
            // can spot the divergence pattern (single-byte flip vs. wholesale
            // wrong vs. zeros etc.) without needing host-side hex tools.
            LOGW("runSha256Test: HASH MISMATCH");
            LOGW("  actual:   %02x %02x %02x %02x %02x %02x %02x %02x …",
                 r.digest_head[0], r.digest_head[1], r.digest_head[2], r.digest_head[3],
                 r.digest_head[4], r.digest_head[5], r.digest_head[6], r.digest_head[7]);
            LOGW("  expected: %02x %02x %02x %02x %02x %02x %02x %02x …",
                 SHA256_REF_HASH[0], SHA256_REF_HASH[1], SHA256_REF_HASH[2], SHA256_REF_HASH[3],
                 SHA256_REF_HASH[4], SHA256_REF_HASH[5], SHA256_REF_HASH[6], SHA256_REF_HASH[7]);
            break;
    }
    return r.status == neuralcheck::stress::kKnownAnswerMatch ? JNI_TRUE : JNI_FALSE;
}

// fieldsVersion 68 (T-20): the CPU known-answer checks, with the CAUSE kept.
// Both return a LongArray; Kotlin's KnownAnswerLayout mirrors it.
//   sieve (6):  [0] layout version (2)  [1] array length (6)
//               [2] status (0 match, 1 mismatch, 2 allocation failed)
//               [3] prime count the kernel produced (-1 when it could not run)
//               [4] duration ms
//               [5] the limit N the sieve was run to (fieldsVersion 70: layout 1 had 5 slots, no limit)
//   sha256 (4): [0] layout version (1)  [1] array length (4)
//               [2] status (0 match, 1 mismatch, 2 allocation failed, 3 self-test failed)
//               [3] duration ms
extern "C" JNIEXPORT jlongArray JNICALL
Java_net_neuralcheck_util_StressNative_runSieveKnownAnswer(JNIEnv* env, jobject /*thiz*/) {
    const auto r = neuralcheck::stress::sieve_known_answer();
    LOGI("runSieveKnownAnswer: status=%d limit=%lld count=%lld (expected %d) duration_ms=%lld",
         r.status, static_cast<long long>(r.limit), static_cast<long long>(r.prime_count),
         PRIME_SIEVE_1M_REFERENCE, static_cast<long long>(r.duration_ms));
    constexpr jsize kLen = 6;
    jlongArray arr = env->NewLongArray(kLen);
    if (arr != nullptr) {
        const jlong values[kLen] = {2, kLen, r.status, r.prime_count, r.duration_ms, r.limit};
        env->SetLongArrayRegion(arr, 0, kLen, values);
    }
    return arr;
}

extern "C" JNIEXPORT jlongArray JNICALL
Java_net_neuralcheck_util_StressNative_runSha256KnownAnswer(JNIEnv* env, jobject /*thiz*/) {
    const auto r = neuralcheck::stress::sha256_known_answer();
    LOGI("runSha256KnownAnswer: status=%d duration_ms=%lld", r.status,
         static_cast<long long>(r.duration_ms));
    if (r.status == neuralcheck::stress::kKnownAnswerMismatch) {
        LOGW("  actual:   %02x %02x %02x %02x %02x %02x %02x %02x …",
             r.digest_head[0], r.digest_head[1], r.digest_head[2], r.digest_head[3],
             r.digest_head[4], r.digest_head[5], r.digest_head[6], r.digest_head[7]);
    }
    constexpr jsize kLen = 4;
    jlongArray arr = env->NewLongArray(kLen);
    if (arr != nullptr) {
        const jlong values[kLen] = {1, kLen, r.status, r.duration_ms};
        env->SetLongArrayRegion(arr, 0, kLen, values);
    }
    return arr;
}

// JNI_OnLoad gives us a one-shot hook the JVM invokes the moment the
// library finishes loading. We use it for two things:
//   1. Confirm the load succeeded via Logcat — visible BEFORE any Kotlin
//      code runs, which matters for diagnosing crash-on-init scenarios.
//   2. Pin the JNI version. Returning JNI_VERSION_1_6 declares we'll only
//      call functions present in API 9+; the JVM rejects loads that ask
//      for a newer version than it implements (we never will).
extern "C" JNIEXPORT jint JNICALL
JNI_OnLoad(JavaVM* /*vm*/, void* /*reserved*/) {
    LOGI("JNI_OnLoad: libstress.so initialised");
    return JNI_VERSION_1_6;
}
