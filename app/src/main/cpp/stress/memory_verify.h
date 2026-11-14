// NeuralCheck V2 — memory verification producer (fieldsVersion 68, T-17).
//
// Allocates the biggest safe buffer, then runs the whole battery (March C-,
// Walking Ones, a size-independent random write/verify) over it TILE BY
// TILE, inside a time cap. Replaces run_memory_stress(), which
//   - returned {0,0,0,0} on a sysinfo or allocation failure (read as "no
//     errors, nothing tested" — a success),
//   - returned a constant 0 for the random verify,
//   - had no time bound (a 512 MiB .. 8 GiB buffer, 20+ sweeps).
//
// Why tiles: a stop in the middle of one 8 GiB March pass leaves no honest
// answer to "what did you cover?". With tiles every counted byte received
// the WHOLE battery, a stop costs at most the step in progress, and the
// result carries exactly how many bytes were covered. A partial run is a
// declared state, never a pass for the whole buffer.
//
// Failures are statuses with the errno that caused them, never zeros.

#pragma once

#include <cstddef>
#include <cstdint>

namespace neuralcheck::stress {

// Status codes of MemoryVerifyResult::status. Their order is part of the
// JNI contract (mirrored in Kotlin: MemoryVerifyLayout) — append, never reorder.
enum MemoryVerifyStatus : int32_t {
    kMemoryVerifyRan = 0,              // every tile completed
    kMemoryVerifyPartialTimeCap = 1,   // the time cap stopped it: bytes_fully_tested < buffer_bytes
    kMemoryVerifyPartialCancelled = 2, // the caller cancelled it
    kMemoryVerifySysinfoFailed = 3,    // sysinfo() failed: err_no says why
    kMemoryVerifyAllocationFailed = 4, // mmap failed (also after the halved retry): err_no says why
    kMemoryVerifyInvalidArgument = 5,  // time cap <= 0
    // fieldsVersion 68, part 2-c (user decision 2026-10-08). No mapping was attempted:
    // memory_verify_insufficient_free_memory(free_bytes) held. free_bytes in the result
    // is what sysinfo reported.
    kMemoryVerifyInsufficientFreeMemory = 6,
};

// The two sizes the buffer decision rests on — ONE source each (rule 24); Kotlin keeps no
// copy: it receives the raw free memory and the status, never these numbers.
//
// 500 MiB: what is left to the system. The buffer may take at most free memory minus this.
constexpr size_t kMemoryVerifyReservedBytes = 500ull * 1024ull * 1024ull;
// 512 MiB: the smallest buffer the test runs on ("a floor so the kernels have a real
// effect", V2 fix #30). Also the line below which there is not enough free memory.
constexpr size_t kMemoryVerifyFloorBytes = 512ull * 1024ull * 1024ull;

// What the buffer may take: free memory minus the reserve, or 0 when free memory does not
// exceed the reserve.
constexpr size_t memory_verify_available_safe(size_t free_bytes) noexcept {
    return (free_bytes > kMemoryVerifyReservedBytes) ? (free_bytes - kMemoryVerifyReservedBytes) : 0;
}

// THE CONDITION (user decision 2026-10-08, reading (b)): the test does not run when
//     available_safe < 512 MiB,   available_safe = free - 500 MiB (0 when free <= 500 MiB)
// i.e. when free memory is below 1012 MiB. It is evaluated after sysinfo and BEFORE any
// mapping. When it does not hold, available_safe >= 512 MiB, so the floor below (which only
// raises a buffer computed from a SMALL share of RAM) never exceeds available_safe: that
// case runs exactly as before.
constexpr bool memory_verify_insufficient_free_memory(size_t free_bytes) noexcept {
    return memory_verify_available_safe(free_bytes) < kMemoryVerifyFloorBytes;
}

// The boundary, proved by the COMPILER (no JVM test can run this file): 1011 MiB free is
// below the line, 1012 MiB is on it and runs; at or under the 500 MiB reserve nothing is
// available at all.
static_assert(memory_verify_insufficient_free_memory(0), "no free memory");
static_assert(memory_verify_insufficient_free_memory(500ull * 1024ull * 1024ull), "free == reserve");
static_assert(memory_verify_insufficient_free_memory(1011ull * 1024ull * 1024ull), "1011 MiB is below 1012");
static_assert(!memory_verify_insufficient_free_memory(1012ull * 1024ull * 1024ull), "1012 MiB runs");
static_assert(!memory_verify_insufficient_free_memory(8ull * 1024ull * 1024ull * 1024ull), "8 GiB runs");

// 256 MiB. A tile is the unit that counts as "covered": big enough that every
// sweep is DRAM-bound (far beyond any L3), small enough that one step is
// ~50-100 ms and a stop request is honoured quickly. The agent's proposal,
// not a user decision.
constexpr size_t kMemoryVerifyTileBytes = 256ull * 1024ull * 1024ull;

struct MemoryVerifyResult {
    int32_t status;
    int32_t err_no;                  // errno of a sysinfo / mmap failure, else 0
    int64_t requested_bytes;         // the buffer size first asked for (0 before it was computed)
    int64_t buffer_bytes;            // what was mapped and divided into tiles (0 if none)
    int64_t tile_bytes;
    int64_t tiles_total;
    int64_t tiles_completed;
    int64_t march_errors;            // bytes, summed over the 5 read passes
    int64_t walking_ones_errors;     // bytes, summed over the 8 patterns
    int64_t random_verify_errors;    // bytes
    int64_t elapsed_ms;              // whole call, on CLOCK_MONOTONIC
    // Free memory as sysinfo reported it (freeram * mem_unit), in bytes: set whenever sysinfo
    // succeeded, run or not (rule 20: raw). -1 = sysinfo failed, so it was never read. (An
    // internal "not read" marker of this struct: Kotlin turns it into an ABSENT value.)
    int64_t free_bytes;
};

// `percent_ram` sizes the buffer as before (1..80% of RAM, capped by free
// memory minus 500 MiB, 512 MiB floor, 8 GiB ceiling). `time_cap_ms` is a
// DURATION: the call stops starting new steps once it has elapsed.
MemoryVerifyResult run_memory_verify(int percent_ram, int64_t time_cap_ms) noexcept;

}  // namespace neuralcheck::stress
