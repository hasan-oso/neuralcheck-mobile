// NeuralCheck V2 — CPU known-answer checks (fieldsVersion 68, T-20, decision D).
//
// Two small checks whose right answer is known in advance and does not depend on
// the device: count the primes up to PRIME_SIEVE_1M_LIMIT (reference
// PRIME_SIEVE_1M_REFERENCE — a published constant; both in
// reference/reference_values.h, fieldsVersion 70: it was 10,000,000 until fv69)
// and hash a 1 MiB deterministic buffer with SHA-256 (a reference
// hash computed off-device). Run while the CPU is loaded, a wrong answer is a
// real fault of the CPU or of the memory it just used; unlike the NEON self-
// reference check it needs no baseline run on the same device.
//
// There is deliberately NO floating-point check here: the only FP kernel in the
// library compares NEON with its own scalar twin, which is not a known answer.
//
// Each result is the raw outcome — a status and, for the sieve, the COUNT — so
// the app ships the number and the server judges. The status names which of
// the failure causes happened (a bare `false` could not tell a broken SHA
// implementation from a failed allocation from a wrong digest).

#pragma once

#include <cstdint>

namespace neuralcheck::stress {

// Order is part of the JNI contract (mirrored in Kotlin: KnownAnswerLayout) —
// append, never reorder.
enum KnownAnswerStatus : int32_t {
    kKnownAnswerMatch = 0,
    kKnownAnswerMismatch = 1,
    kKnownAnswerAllocationFailed = 2,
    kKnownAnswerSelfTestFailed = 3,   // SHA only: the FIPS 180-4 "abc" vector failed
};

struct SieveKnownAnswer {
    int32_t status;
    int64_t prime_count;   // what the kernel counted; -1 when it could not run
    int64_t duration_ms;
    int64_t limit;         // the N the sieve was run to (fv70: shipped raw as `sieveLimit`)
};

struct Sha256KnownAnswer {
    int32_t status;
    int64_t duration_ms;
    uint8_t digest_head[8];   // first bytes of the digest actually produced (for the native log only)
};

SieveKnownAnswer sieve_known_answer() noexcept;
Sha256KnownAnswer sha256_known_answer() noexcept;

}  // namespace neuralcheck::stress
