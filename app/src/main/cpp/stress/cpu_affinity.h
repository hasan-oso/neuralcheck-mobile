// NeuralCheck V2 — CPU affinity helpers (sub-phase 1g).
//
// Detects per-cluster CPU groupings from /sys/devices/system/cpu and
// exposes pin-current-thread-to-cluster wrappers around
// sched_setaffinity(2).
//
// Cluster detection: groups CPUs by their cpuinfo_max_freq value.
// Most modern phones have 2-3 distinct max frequencies (big/mid/little
// or big/little). Lowest freq = "little", highest = "big", anything
// in-between = "mid". CPUs that fail to read max_freq (offline at probe
// time, or sysfs locked) are excluded from every cluster.
//
// Per Q2.6 in IMPLEMENTATION_NOTES_V2.md, sched_setaffinity may silently
// return EPERM under Samsung Knox / MIUI. The pin wrappers return false
// in that case so the caller can record a "testingMode =
// degraded_no_pinning" entry in the time-series footer rather than
// aborting the scan.

#pragma once

#include <cstddef>

namespace neuralcheck::stress {

// Pins the calling thread to the cluster of CPUs with the highest
// cpuinfo_max_freq. Returns true on success, false on any error
// (including EPERM from a restrictive security policy).
bool pin_to_big_cluster() noexcept;

// Pins to the lowest-freq cluster. Same return semantics as
// pin_to_big_cluster.
bool pin_to_little_cluster() noexcept;

// Pins to the middle-freq cluster IF the SoC has at least 3 distinct
// max_freq values. On 2-cluster SoCs, returns false without touching
// affinity (caller can fall back to big or little). Same return
// semantics as pin_to_big_cluster.
bool pin_to_mid_cluster() noexcept;

// Restores the calling thread's affinity to "all available CPUs".
// Useful when a kernel finished its pinned phase and wants the OS
// scheduler to migrate it freely again.
bool reset_affinity_to_all() noexcept;

// Cluster summary: number of distinct cpuinfo_max_freq values and the
// total CPU count detected. Used by the smoke test to log topology
// before exercising pinning. Both are 0 on enumeration failure (sysfs
// blocked, no /sys/devices/system/cpu, etc.).
struct ClusterTopology {
    int total_cpus;
    int distinct_clusters;  // 1, 2, or 3 typically
    int big_cluster_size;
    int little_cluster_size;
    int mid_cluster_size;   // 0 when distinct_clusters < 3
};

ClusterTopology detect_cluster_topology() noexcept;

}  // namespace neuralcheck::stress
