// NeuralCheck V2 — Memory tests (sub-phase 1e; extended for fieldsVersion 68).
//
// Three classical memory-test kernels for detecting bad cells, address
// decoder faults, and data-dependent (coupling) faults that throughput
// metrics never see:
//
//   - March C- (master plan §3.4): 6-pass marching pattern of 0x00/0xFF
//     reads and writes. Detects most stuck-at faults plus state-coupling
//     faults between adjacent cells.
//
//   - Walking Ones: for each of 8 single-bit patterns (0x01, 0x02, …,
//     0x80), write across the buffer then read back. Detects address
//     decoder faults and bit-line shorts.
//
//   - Random verify (two forms):
//       * random_pattern_sha_verify(): fill 32 MiB with the LCG sequence,
//         SHA-256 it, compare to a precomputed reference. Tied to ONE size
//         (the smoke test, runMemoryTest).
//       * random_pattern_write()/random_pattern_verify(): a counter-based
//         pseudo-random pattern, a pure function of the absolute 64-bit
//         word index. NOT tied to any buffer size, needs no reference hash:
//         the verify pass regenerates what the write pass stored. This is the
//         one the stress phase uses (fieldsVersion 68, S-11/S-30: "a real
//         random verify in C++, independent of size").
//
// All operate on a caller-provided buffer; the caller is responsible for
// sizing and freeing. Error counts are 64-bit: a defective DIMM can produce
// more mismatches than a 32-bit int holds (8 walking-ones reads over a
// 256 MiB tile is already 2^31 bytes).

#pragma once

#include <cstddef>
#include <cstdint>

#include "stress/native_common.h"

namespace neuralcheck::stress {

// March C- on [buf, buf + bytes). Returns the number of cells that
// produced a wrong read at any of the 5 read passes (sum across all
// passes — a cell that's wrong in 3 passes counts 3 times). Zero means
// every cell behaved correctly under all 6 marching-element steps.
int64_t march_c_minus(uint8_t* buf, size_t bytes) noexcept;

// Walking ones across the same buffer. For each of the 8 single-bit
// values, writes that pattern everywhere then reads back; counts read
// mismatches. Returns the total mismatch count across the 8 passes.
int64_t walking_ones(uint8_t* buf, size_t bytes) noexcept;

// Generates a deterministic LCG sequence into the buffer (seed 0xCAFEBABE,
// XOR mask 0x5A — distinct from the SHA-256 input generator's seed so the
// two kernels test different bit patterns), then SHA-256-hashes the whole
// buffer and compares to MEMORY_RANDOM_SHA_REFERENCE in
// reference_values.h.
//
// REQUIRES bytes == MEMORY_RANDOM_SHA_BUFFER_SIZE (32 MiB) to match the
// precomputed reference. Returns true on hash match, false otherwise
// (including on size mismatch).
bool random_pattern_sha_verify(uint8_t* buf, size_t bytes) noexcept;

// Fills buf with the same deterministic LCG sequence used by
// random_pattern_sha_verify. Exposed separately so a future stress
// phase can mix this generator into a multi-step memory torture test
// without re-running SHA verification.
void generate_memory_pattern(uint8_t* buf, size_t bytes) noexcept;

// ───────────── fieldsVersion 68: size-independent random verify ─────────────

// The word the pattern holds at absolute 64-bit word index `index`
// (splitmix64's finalizer over a counter). Public so the pattern is defined
// in exactly one place.
uint64_t random_pattern_word(uint64_t index) noexcept;

// Writes words first_word_index .. first_word_index + bytes/8 - 1 into buf.
// `bytes` must be a multiple of 8.
void random_pattern_write(uint8_t* buf, size_t bytes, uint64_t first_word_index) noexcept;

// Reads buf back against the same words. Returns the number of BYTES that
// differ (the unit march_c_minus and walking_ones count in).
int64_t random_pattern_verify(const uint8_t* buf, size_t bytes, uint64_t first_word_index) noexcept;

// Outcome of the full battery on one tile.
struct TileTestResult {
    // true iff every step ran. A stop request between steps leaves this
    // false: the tile is NOT counted as covered, but the errors it already
    // found are real and are still returned.
    bool completed;
    StopReason stopped_by;
    int64_t march_errors;
    int64_t walking_ones_errors;
    int64_t random_verify_errors;
};

// March C- (6 steps) + Walking Ones (8 patterns) + random write/verify, in
// that order, over [tile, tile + bytes). `bytes` must be a multiple of 8.
// `first_word_index` is the tile's absolute word offset in the whole buffer
// so no two tiles of one run hold the same random data. The stop control is
// consulted before every step.
TileTestResult memory_test_tile(uint8_t* tile,
                                size_t bytes,
                                uint64_t first_word_index,
                                const StopControl& stop) noexcept;

}  // namespace neuralcheck::stress
