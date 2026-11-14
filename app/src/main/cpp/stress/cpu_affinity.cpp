#include "stress/cpu_affinity.h"

#include <sched.h>
#include <unistd.h>

#include <algorithm>
#include <array>
#include <cstdio>
#include <cstdlib>
#include <cstring>

namespace neuralcheck::stress {

namespace {

// Hard cap on the number of CPUs we'll probe. Even high-end 2024 ARM
// SoCs ship with ≤16 cores; 64 is a generous future-proof bound that
// still fits comfortably in cpu_set_t (which is 1024 bits on Linux).
constexpr int kMaxCpus = 64;

struct CpuInfo {
    int cpu_id;
    long max_freq_khz;  // 0 = sysfs unreadable or CPU offline at probe
};

long read_cpu_max_freq(int cpu) noexcept {
    char path[80];
    std::snprintf(path, sizeof(path),
                  "/sys/devices/system/cpu/cpu%d/cpufreq/cpuinfo_max_freq", cpu);
    FILE* f = std::fopen(path, "rb");
    if (!f) return 0;
    char buf[32];
    const size_t n = std::fread(buf, 1, sizeof(buf) - 1, f);
    std::fclose(f);
    if (n == 0) return 0;
    buf[n] = '\0';
    return std::atol(buf);
}

// Enumerates online CPUs and reads their max_freq. Returns the count.
int enumerate_cpus(CpuInfo out[kMaxCpus]) noexcept {
    int count = 0;
    for (int cpu = 0; cpu < kMaxCpus; ++cpu) {
        // Stop at the first gap — Android numbers CPUs sequentially.
        char path[80];
        std::snprintf(path, sizeof(path),
                      "/sys/devices/system/cpu/cpu%d", cpu);
        if (access(path, F_OK) != 0) break;

        out[count].cpu_id = cpu;
        out[count].max_freq_khz = read_cpu_max_freq(cpu);
        ++count;
    }
    return count;
}

// Builds a cpu_set_t from a predicate over CpuInfo, then applies it to
// the current thread. Returns true iff sched_setaffinity succeeded.
template <typename Predicate>
bool pin_where(Predicate&& pred) noexcept {
    CpuInfo cpus[kMaxCpus];
    const int n = enumerate_cpus(cpus);
    if (n == 0) return false;

    cpu_set_t set;
    CPU_ZERO(&set);
    int matched = 0;
    for (int i = 0; i < n; ++i) {
        if (pred(cpus[i])) {
            CPU_SET(cpus[i].cpu_id, &set);
            ++matched;
        }
    }
    if (matched == 0) return false;

    // sched_setaffinity(0, …) targets the calling thread (gettid).
    // Returns 0 on success; -1 with errno (typically EPERM under
    // restrictive security policies) on failure.
    return sched_setaffinity(0, sizeof(set), &set) == 0;
}

// Sorts CpuInfo[] descending by max_freq, returns the count of
// distinct max_freq buckets (skipping zero / unreadable entries).
int sort_and_count_distinct(CpuInfo* cpus, int n, long distinct_freqs[3]) noexcept {
    std::sort(cpus, cpus + n, [](const CpuInfo& a, const CpuInfo& b) {
        return a.max_freq_khz > b.max_freq_khz;
    });
    int distinct = 0;
    long prev = -1;
    for (int i = 0; i < n && distinct < 3; ++i) {
        if (cpus[i].max_freq_khz <= 0) continue;
        if (cpus[i].max_freq_khz != prev) {
            distinct_freqs[distinct++] = cpus[i].max_freq_khz;
            prev = cpus[i].max_freq_khz;
        }
    }
    return distinct;
}

}  // namespace

bool pin_to_big_cluster() noexcept {
    return pin_where([](const CpuInfo& info) {
        // Find the max max_freq dynamically inside the predicate would
        // re-enumerate; simpler: enumerate here too.
        CpuInfo all[kMaxCpus];
        const int n = enumerate_cpus(all);
        long max_freq = 0;
        for (int i = 0; i < n; ++i) {
            if (all[i].max_freq_khz > max_freq) max_freq = all[i].max_freq_khz;
        }
        return info.max_freq_khz > 0 && info.max_freq_khz == max_freq;
    });
}

bool pin_to_little_cluster() noexcept {
    return pin_where([](const CpuInfo& info) {
        CpuInfo all[kMaxCpus];
        const int n = enumerate_cpus(all);
        long min_freq = 0;
        for (int i = 0; i < n; ++i) {
            if (all[i].max_freq_khz <= 0) continue;
            if (min_freq == 0 || all[i].max_freq_khz < min_freq) {
                min_freq = all[i].max_freq_khz;
            }
        }
        return info.max_freq_khz > 0 && info.max_freq_khz == min_freq;
    });
}

bool pin_to_mid_cluster() noexcept {
    CpuInfo cpus[kMaxCpus];
    const int n = enumerate_cpus(cpus);
    long distinct[3] = {0, 0, 0};
    const int d = sort_and_count_distinct(cpus, n, distinct);
    if (d < 3) return false;  // No mid cluster on 2-tier SoCs.
    const long mid_freq = distinct[1];

    return pin_where([mid_freq](const CpuInfo& info) {
        return info.max_freq_khz == mid_freq;
    });
}

bool reset_affinity_to_all() noexcept {
    CpuInfo cpus[kMaxCpus];
    const int n = enumerate_cpus(cpus);
    if (n == 0) return false;

    cpu_set_t set;
    CPU_ZERO(&set);
    for (int i = 0; i < n; ++i) {
        CPU_SET(cpus[i].cpu_id, &set);
    }
    return sched_setaffinity(0, sizeof(set), &set) == 0;
}

ClusterTopology detect_cluster_topology() noexcept {
    ClusterTopology t = {0, 0, 0, 0, 0};
    CpuInfo cpus[kMaxCpus];
    const int n = enumerate_cpus(cpus);
    t.total_cpus = n;
    if (n == 0) return t;

    long distinct[3] = {0, 0, 0};
    t.distinct_clusters = sort_and_count_distinct(cpus, n, distinct);

    // Count membership per cluster after sorting (already done by
    // sort_and_count_distinct).
    for (int i = 0; i < n; ++i) {
        if (cpus[i].max_freq_khz <= 0) continue;
        if (cpus[i].max_freq_khz == distinct[0]) ++t.big_cluster_size;
        else if (t.distinct_clusters == 3 && cpus[i].max_freq_khz == distinct[1]) ++t.mid_cluster_size;
        else ++t.little_cluster_size;
    }
    return t;
}

}  // namespace neuralcheck::stress
