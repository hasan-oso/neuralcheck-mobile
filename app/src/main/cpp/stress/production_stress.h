// NeuralCheck V2 — Production stress orchestrators (master plan §3.2).
//
// Long-running variants of the per-kernel test entries that:
//   - Spawn worker threads (one per big+mid core).
//   - Run kernels in a tight loop until a stop signal arrives.
//   - Apply intensity_factor to throttle iteration counts so the V2
//     service can dial down stress on budget-cooled devices without
//     touching the kernel code itself.
//   - Aggregate verification results across threads.
//
// These are the entry points the V2 StressTestServiceV2 calls during
// CPU_SUSTAINED, COMBINED_PEAK, and BURST_PATTERN phases (the memory
// verification producer lives in memory_verify.h).
// The single-shot test entries in stress_jni.cpp stay alongside as
// fast smoke checks (NeuralCheckApp uses them for first-launch health).

#pragma once

#include <cstddef>
#include <cstdint>

namespace neuralcheck::stress {

// CPU stress aggregate counters. Returned by stop_cpu_stress() so the
// V2 service can populate VerificationSummary.cpu* fields.
struct CpuStressResult {
    int64_t total_iterations;
    int64_t verified_iterations;
    int32_t fma_errors;
    bool prime_sieve_correct;
    int32_t sha_hashes_verified;
    // Bug fix: separate counter for total SHA attempts. Difference
    // (sha_hashes_attempted - sha_hashes_verified) = SHA mismatches,
    // which would indicate silent SHA-256 implementation drift
    // (extremely rare on validated hardware, but worth tracking).
    int32_t sha_hashes_attempted;
    // Pinning telemetry (V2 fix #22). Counts of workers that
    // successfully pinned to big/mid clusters at start, plus failures
    // (EPERM under restrictive security or 2-cluster SoC where mid
    // doesn't exist). The AI uses these to weight the temperature
    // signal — a scan with 0 pinned workers ran on whatever cores
    // the scheduler chose, so heat measurements are less indicative
    // of true SoC stress.
    int32_t workers_pinned_big;
    int32_t workers_pinned_mid;
    // Aggregate pin-failure counter (sum of per-cluster failures below).
    // Kept for backward compatibility with V2 fix #22 readers — V2 fix
    // #56-3 (fieldsVersion 59) splits failures per-cluster via the four
    // fields below. New code should prefer the per-cluster fields.
    int32_t workers_pin_failed;
    // V2 fix #56-3 (fieldsVersion 59): all-8-core workload + per-cluster
    // pin telemetry. Little-cluster pinning is best-effort: failure means
    // the scheduler chose where to run the worker (acceptable, just less
    // diagnostic for little-cluster silicon defects).
    int32_t workers_pinned_little;
    int32_t workers_pin_failed_big;
    int32_t workers_pin_failed_mid;
    int32_t workers_pin_failed_little;
};

// Starts a long-running CPU stress. Spawns (max(1, big+mid cluster
// size)) worker threads, each running the NEON FMA + Prime + SHA
// kernels in rotation. Returns 0 on success, -1 if a kernel was
// already running.
//
// `intensity_factor` ∈ [0, 1] scales the per-iteration work — a kernel
// at 0.65 sleeps for 35% of its cycle. Approximate; the goal is to
// reduce heat generation, not to be metric-perfect.
int start_cpu_stress(float intensity_factor, int64_t duration_ms_advisory) noexcept;

// Signals the workers to stop, joins them, returns the aggregate.
// Safe to call when nothing is running — returns a zeroed struct.
CpuStressResult stop_cpu_stress() noexcept;

}  // namespace neuralcheck::stress
