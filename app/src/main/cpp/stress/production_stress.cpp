#include "stress/production_stress.h"

#include <sys/resource.h>
#include <unistd.h>

#include <atomic>
#include <chrono>
#include <cstring>
#include <memory>
#include <new>
#include <thread>
#include <vector>

#include "stress/cpu_affinity.h"
#include "stress/neon_fma.h"
#include "stress/prime_sieve.h"
#include "stress/sha256.h"
#include "reference/reference_values.h"

namespace neuralcheck::stress {

namespace {

// Single global pool — V2 service runs one CPU stress at a time.
// Concurrent starts return -1 from start_cpu_stress().
std::atomic<bool> g_running{false};
std::vector<std::thread> g_workers;
std::atomic<int64_t> g_total_iterations{0};
std::atomic<int64_t> g_verified_iterations{0};
std::atomic<int32_t> g_fma_errors{0};
std::atomic<bool> g_prime_correct{true};
std::atomic<int32_t> g_sha_verified{0};
std::atomic<int32_t> g_sha_attempts{0};
std::atomic<float> g_intensity{1.0f};

// Pinning telemetry: tracks how many workers successfully pinned
// to big_cluster vs mid_cluster vs failed (e.g. EPERM under
// restrictive security policy). Reset at start_cpu_stress, read
// by stop_cpu_stress so the AI can know whether stress hit perf
// cores or fell back to scheduler default.
//
// V2 fix #56-3 (fieldsVersion 59): added per-cluster pin-failure
// split + little-cluster pin success counter. The aggregate
// g_workers_pin_failed = sum of the three per-cluster failures
// for backward compat with old StressTestServiceV2 readers.
std::atomic<int> g_workers_pinned_big{0};
std::atomic<int> g_workers_pinned_mid{0};
std::atomic<int> g_workers_pinned_little{0};
std::atomic<int> g_workers_pin_failed{0};
std::atomic<int> g_workers_pin_failed_big{0};
std::atomic<int> g_workers_pin_failed_mid{0};
std::atomic<int> g_workers_pin_failed_little{0};

// V2 fix #56-3 (fieldsVersion 59): sink for the light-workload's busy
// loop. Atomic so the compiler can't dead-store-eliminate the inner
// arithmetic. Read once at end-of-scan for diagnostics; otherwise
// only written.
std::atomic<uint64_t> g_light_accumulator{0};

constexpr size_t kFmaCells = kNeonFmaCells;
constexpr size_t kShaInputSize = (1u << 18);  // 256 KiB per worker — keeps RSS bounded vs the 1 MiB smoke test

void worker_loop(int worker_id, int target_cluster) {
    // V2 pinning: bind this worker to the chosen cluster. Failure
    // (e.g. EPERM under Knox/MIUI) means the scheduler decides
    // which CPU runs us — workload still executes, but heat may
    // be diluted across little cores. Telemetry records the
    // outcome for the backend.
    //
    // V2 fix #56-3 (fieldsVersion 59): per-cluster failure counters
    // so the AI / lab can tell apart "device blocked all pinning"
    // from "only little-cluster pinning failed" (rare but possible
    // on some MIUI builds where SELinux contexts vary by cluster).
    bool pinned = false;
    if (target_cluster == 0) {
        pinned = pin_to_big_cluster();
        if (pinned) {
            g_workers_pinned_big.fetch_add(1, std::memory_order_relaxed);
        } else {
            g_workers_pin_failed_big.fetch_add(1, std::memory_order_relaxed);
        }
    } else if (target_cluster == 1) {
        pinned = pin_to_mid_cluster();
        if (pinned) {
            g_workers_pinned_mid.fetch_add(1, std::memory_order_relaxed);
        } else {
            // Fallback: 2-cluster SoC has no mid → try big.
            pinned = pin_to_big_cluster();
            if (pinned) {
                g_workers_pinned_big.fetch_add(1, std::memory_order_relaxed);
            } else {
                g_workers_pin_failed_mid.fetch_add(1, std::memory_order_relaxed);
            }
        }
    } else if (target_cluster == 2) {
        // V2 fix #56-3: little cluster path. Heavy NEON FMA on Cortex-A55-
        // class little cores would just pin them at minimum frequency
        // (governor sees throttle pressure and downclocks). Caller routes
        // little workers through light_worker_loop() instead — this
        // worker_loop variant should NOT receive cluster 2 in production.
        // We keep the branch for symmetry; if it ever fires, log and run
        // the perf workload anyway (better than silently doing nothing).
        pinned = pin_to_little_cluster();
        if (pinned) {
            g_workers_pinned_little.fetch_add(1, std::memory_order_relaxed);
        } else {
            g_workers_pin_failed_little.fetch_add(1, std::memory_order_relaxed);
        }
    }
    if (!pinned) {
        g_workers_pin_failed.fetch_add(1, std::memory_order_relaxed);
    }

    // Allocate per-worker scratch so threads don't false-share. Drop
    // out gracefully on allocation failure rather than crashing —
    // this can happen when the OOM killer trims the process during
    // memory phase concurrent with CPU phase (combined peak).
    auto A = std::unique_ptr<float[]>(new (std::nothrow) float[kFmaCells]);
    auto B = std::unique_ptr<float[]>(new (std::nothrow) float[kFmaCells]);
    auto C = std::unique_ptr<float[]>(new (std::nothrow) float[kFmaCells]);
    auto sha_buf = std::unique_ptr<uint8_t[]>(new (std::nothrow) uint8_t[kShaInputSize]);
    if (!A || !B || !C || !sha_buf) {
        return;
    }

    // Different seed per worker so threads stress different value
    // distributions — better cache behaviour coverage than every
    // worker grinding on the identical matrices.
    const uint32_t seed_a = 0xC0FFEE01u + static_cast<uint32_t>(worker_id);
    const uint32_t seed_b = 0xCAFEBABEu + static_cast<uint32_t>(worker_id);
    neon_fma_generate_matrix(A.get(), seed_a);
    neon_fma_generate_matrix(B.get(), seed_b);
    generate_sha_input(sha_buf.get(), kShaInputSize);

    uint8_t expected_sha[kSha256DigestBytes];
    sha256_compute(sha_buf.get(), kShaInputSize, expected_sha);

    // Self-reference verification: run NEON kernel ONCE to capture
    // the baseline trace this worker's pipeline produces for these
    // exact inputs. Subsequent iterations must match bit-exactly
    // because NEON is deterministic for fixed inputs. Any drift
    // indicates a real FMA pipeline defect (single-event upset,
    // thermal drift in the FMA unit, etc.). This avoids the
    // false-positive trap of comparing NEON-trace vs scalar-trace,
    // which differ by ULPs due to different accumulation order in
    // FP32 (not associative). Field measurements: scalar-vs-NEON
    // produced 25-32% false positives on healthy Snapdragon 855,
    // 888, and 8 Gen 3 — self-reference produces 0% on the same
    // healthy hardware while still catching real drift events.
    neon_fma_matmul_neon(A.get(), B.get(), C.get());
    const float baseline_trace = neon_fma_trace(C.get());

    int counter = 0;
    while (g_running.load(std::memory_order_relaxed)) {
        // Round-robin between FMA, prime sieve (only worker 0 — heavy
        // memory cost), and SHA-256 to exercise different ALU paths.
        const int slot = counter % 4;
        if (slot == 0 || slot == 1 || slot == 2) {
            neon_fma_matmul_neon(A.get(), B.get(), C.get());
            const float trace = neon_fma_trace(C.get());
            // Self-reference comparison: should be bit-exact.
            // We allow a tiny epsilon (1 ULP at trace magnitude) for
            // any environmental noise the reference establishment
            // might have caught. Real FMA defects drift orders of
            // magnitude beyond this.
            if (trace != baseline_trace) {
                const float diff = trace - baseline_trace;
                const float abs_diff = diff < 0.0f ? -diff : diff;
                const float abs_baseline = baseline_trace < 0.0f
                    ? -baseline_trace : baseline_trace;
                // 1 ULP at FP32 ≈ 1.2e-7 of the value. We use 1e-6
                // (~8 ULPs) as the tolerance — anything bigger
                // indicates a real divergence.
                if (abs_diff > abs_baseline * 1e-6f) {
                    g_fma_errors.fetch_add(1, std::memory_order_relaxed);
                }
            }
            g_total_iterations.fetch_add(1, std::memory_order_relaxed);
            g_verified_iterations.fetch_add(1, std::memory_order_relaxed);
        } else if (slot == 3 && worker_id == 0) {
            uint8_t actual[kSha256DigestBytes];
            sha256_compute(sha_buf.get(), kShaInputSize, actual);
            // Bug fix: increment attempts counter on every hash, then
            // verified only on match. Difference exposes SHA drift.
            g_sha_attempts.fetch_add(1, std::memory_order_relaxed);
            if (std::memcmp(actual, expected_sha, kSha256DigestBytes) == 0) {
                g_sha_verified.fetch_add(1, std::memory_order_relaxed);
            }
        }
        ++counter;

        // Intensity throttle: at 1.0 we don't sleep. At 0.65, we
        // sleep ~35% of an iteration's wall time — the kernel
        // generates ~35% less heat overall.
        const float intensity = g_intensity.load(std::memory_order_relaxed);
        if (intensity < 0.99f) {
            const auto sleep_us = static_cast<int>((1.0f - intensity) * 80'000.0f);
            if (sleep_us > 0) {
                std::this_thread::sleep_for(std::chrono::microseconds(sleep_us));
            }
        }
    }
}

/**
 * V2 fix #56-3 (fieldsVersion 59): light-intensity workload for little-
 * cluster cores. Cortex-A55 / Kryo 485 Silver-class cores under heavy
 * NEON FMA pin to minimum frequency (governor sees thermal headroom
 * shrinking and downclocks aggressively). Running prime sieves or
 * branchy integer arithmetic exercises the integer ALU + L1 cache
 * + branch predictor without provoking the governor's protection
 * response — exactly what we want for cluster-defect surfacing on
 * water-damaged or stress-aged devices.
 *
 * No verification path: the goal is to engage the silicon, not detect
 * arithmetic defects. Bit-level defect detection still happens on
 * big+mid via the perf worker_loop.
 *
 * Branch predictor + ALU + memory-touch via:
 *   - LCG random-number generation (multiply-add, branchy mod)
 *   - XOR+conditional sub mixing (forces branch eval per inner iter)
 *   - 1 KiB stack buffer rolling sum (L1 hit, exercises load/store unit)
 */
void light_worker_loop(int worker_id) {
    // Pin to little cluster. Failure is non-fatal: scheduler decides
    // placement, but we record it so the AI knows little-cluster
    // measurements may be noisy.
    const bool pinned = pin_to_little_cluster();
    if (pinned) {
        g_workers_pinned_little.fetch_add(1, std::memory_order_relaxed);
    } else {
        g_workers_pin_failed_little.fetch_add(1, std::memory_order_relaxed);
        g_workers_pin_failed.fetch_add(1, std::memory_order_relaxed);
    }

    // 1 KiB scratch — fits comfortably in L1d on every Cortex-A55+
    // generation. Pre-fill so pages are resident before first hot loop.
    constexpr size_t kBufSize = 1024;
    uint32_t buf[kBufSize];
    for (size_t i = 0; i < kBufSize; ++i) {
        buf[i] = static_cast<uint32_t>(i ^ static_cast<uint32_t>(worker_id));
    }

    uint32_t seed = 0xC0FFEE01u + static_cast<uint32_t>(worker_id);
    uint64_t accumulator = 0;

    while (g_running.load(std::memory_order_relaxed)) {
        // Inner loop: branchy integer arithmetic + L1 cache touch.
        // Tight enough that little cores stay near peak frequency
        // when pinned, but cheap enough that the governor doesn't
        // see this as "needs throttling" pressure.
        for (int i = 0; i < 100'000; ++i) {
            seed = seed * 1103515245u + 12345u;          // LCG step
            const uint32_t idx = seed & (kBufSize - 1);
            const uint32_t v = buf[idx];
            accumulator += static_cast<uint64_t>(v ^ (seed >> 8));
            if ((seed & 0x10000u) != 0u) {
                accumulator -= static_cast<uint64_t>(seed);  // branchy
            }
            buf[idx] = v + seed;                         // store back
        }
        g_total_iterations.fetch_add(1, std::memory_order_relaxed);

        // Publish accumulator to atomic sink so the optimizer can't
        // dead-eliminate the inner work. Relaxed ordering — we don't
        // need any happens-before here.
        g_light_accumulator.store(accumulator, std::memory_order_relaxed);

        // Intensity throttle (mirrors worker_loop) — at 1.0 we don't
        // sleep; at 0.65 we sleep ~35% of an iteration's wall time.
        const float intensity = g_intensity.load(std::memory_order_relaxed);
        if (intensity < 0.99f) {
            const auto sleep_us = static_cast<int>((1.0f - intensity) * 80'000.0f);
            if (sleep_us > 0) {
                std::this_thread::sleep_for(std::chrono::microseconds(sleep_us));
            }
        }
    }
}

}  // namespace

int start_cpu_stress(float intensity_factor, int64_t /*duration_ms_advisory*/) noexcept {
    bool expected = false;
    if (!g_running.compare_exchange_strong(expected, true)) {
        return -1;  // already running
    }

    // Reset counters
    g_total_iterations.store(0);
    g_verified_iterations.store(0);
    g_fma_errors.store(0);
    g_prime_correct.store(true);
    g_sha_verified.store(0);
    g_sha_attempts.store(0);
    g_intensity.store(intensity_factor);

    // V2 fix #56-3 (fieldsVersion 59): all-8-core workload.
    //
    // Lab analysis (STRESS_TEST_DIAGNOSIS.md, Q1-Q5) showed the previous
    // big+mid worker count (4 of 8 cores on Snapdragon 855) capped global
    // CPU usage near 50%, causing false-negative saturation criteria on
    // healthy devices. We now spawn workers on all three clusters:
    //   - big cluster: heavy NEON FMA + SHA (existing perf workload)
    //   - mid cluster: same heavy workload (existing perf workload)
    //   - little cluster: light branchy-integer workload (new
    //     light_worker_loop) — exercises the cluster's silicon for
    //     defect surfacing without provoking governor downclocking
    //
    // R1 (sampler starvation): we reserve ONE little core for the JVM
    // sampler thread. Without this, all 4 little cores would be busy
    // and the 4 Hz DisciplinedSampler would starve when big+mid are
    // also loaded — losing the per-cluster usage telemetry the new
    // saturation criterion relies on. Reserving 1 little core is the
    // minimum guard that keeps the sampler responsive without losing
    // the cluster-defect signal we want from the other 3 little cores.
    const auto topo = detect_cluster_topology();
    int big_count;
    int mid_count;
    int little_workers;       // distinct from topo.little_cluster_size
    int worker_count;
    if (topo.total_cpus > 0) {
        big_count = topo.big_cluster_size;
        mid_count = topo.mid_cluster_size;
        // R1 guard: reserve 1 little core for sampler. Edge case: if
        // little_cluster_size <= 1, we don't have a core to spare, so
        // little_workers = 0 (legacy big+mid behaviour for that device).
        little_workers = (topo.little_cluster_size > 1)
            ? topo.little_cluster_size - 1
            : 0;
        worker_count = big_count + mid_count + little_workers;
        if (worker_count < 1) worker_count = topo.total_cpus;
    } else {
        big_count = 0;
        mid_count = 0;
        little_workers = 0;
        worker_count = static_cast<int>(std::thread::hardware_concurrency());
    }
    if (worker_count < 1) worker_count = 1;
    if (worker_count > 16) worker_count = 16;  // upper sanity bound

    // Reset pinning telemetry before launching workers.
    g_workers_pinned_big.store(0);
    g_workers_pinned_mid.store(0);
    g_workers_pinned_little.store(0);
    g_workers_pin_failed.store(0);
    g_workers_pin_failed_big.store(0);
    g_workers_pin_failed_mid.store(0);
    g_workers_pin_failed_little.store(0);
    g_light_accumulator.store(0);

    g_workers.clear();
    g_workers.reserve(static_cast<size_t>(worker_count));

    // Distribute workers across the three clusters:
    //   worker_id 0..big_count-1                                   → big (heavy)
    //   worker_id big_count..big+mid-1                             → mid (heavy)
    //   worker_id big+mid..big+mid+little_workers-1                → little (light)
    //   remainder (only when topology probe failed)                → big (heavy)
    //
    // The /sys/cpu probe can fail on rooted devices or sysfs-blocked
    // builds; in that case all three counts are 0 and workers fall
    // through to "scheduler decides placement" with the heavy workload.
    // Pinning telemetry records the actual outcome per cluster.
    for (int i = 0; i < worker_count; ++i) {
        int target_cluster;
        if (i < big_count) {
            target_cluster = 0;  // big
        } else if (i < big_count + mid_count) {
            target_cluster = 1;  // mid
        } else if (i < big_count + mid_count + little_workers) {
            target_cluster = 2;  // little (uses light_worker_loop)
        } else {
            target_cluster = 0;  // overflow / no-topology fallback → big
        }
        if (target_cluster == 2) {
            // Little cluster gets the light-intensity workload.
            g_workers.emplace_back(light_worker_loop, i);
        } else {
            g_workers.emplace_back(worker_loop, i, target_cluster);
        }
    }
    return 0;
}

CpuStressResult stop_cpu_stress() noexcept {
    g_running.store(false);
    for (auto& t : g_workers) {
        if (t.joinable()) t.join();
    }
    g_workers.clear();

    // Run prime sieve once at stop time as the per-scan correctness
    // probe. Cheap (~90 ms) and deterministic — gives the V2 service a
    // boolean it can write into VerificationSummary.cpuPrimeSieveCorrect.
    const int64_t prime_count = prime_sieve_count(PRIME_SIEVE_10M_LIMIT);
    g_prime_correct.store(prime_count == PRIME_SIEVE_10M_REFERENCE);

    return CpuStressResult{
        g_total_iterations.load(),
        g_verified_iterations.load(),
        g_fma_errors.load(),
        g_prime_correct.load(),
        g_sha_verified.load(),
        g_sha_attempts.load(),
        g_workers_pinned_big.load(),
        g_workers_pinned_mid.load(),
        g_workers_pin_failed.load(),
        g_workers_pinned_little.load(),
        g_workers_pin_failed_big.load(),
        g_workers_pin_failed_mid.load(),
        g_workers_pin_failed_little.load()
    };
}

}  // namespace neuralcheck::stress
