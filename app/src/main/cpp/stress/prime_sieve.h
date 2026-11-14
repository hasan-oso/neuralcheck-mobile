// NeuralCheck V2 — Sieve of Eratosthenes (sub-phase 1b).
//
// Counts primes ≤ limit using the classic Eratosthenes algorithm with a
// 1-byte-per-cell sieve. The 1-byte cell (rather than std::bitset or a
// bit-packed buffer) is intentional: per master plan §3.3 this kernel
// is supposed to stress integer ALU + branch predictor + L1/L2 cache,
// and a byte-per-cell sieve hits the cache subsystem more reliably than
// a bit-packed one (which compresses 8× and loses the cache-line angle).

#pragma once

#include <cstdint>

namespace neuralcheck::stress {

// Returns the count of primes p such that 2 ≤ p ≤ limit. Allocates a
// (limit + 1)-byte buffer on the heap; returns -1 on allocation failure
// so a caller on a memory-constrained device can degrade gracefully
// instead of crashing on bad_alloc.
//
// For limit = 10,000,000 the returned value MUST equal
// PRIME_SIEVE_10M_REFERENCE from reference/reference_values.h. Anything
// else indicates a CPU/memory defect — see the comment in
// reference_values.h for the verification contract.
int64_t prime_sieve_count(uint32_t limit) noexcept;

}  // namespace neuralcheck::stress
