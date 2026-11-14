#include "stress/known_answer.h"

#include <cstring>
#include <memory>
#include <new>

#include "reference/reference_values.h"
#include "stress/native_common.h"
#include "stress/prime_sieve.h"
#include "stress/sha256.h"

namespace neuralcheck::stress {

SieveKnownAnswer sieve_known_answer() noexcept {
    SieveKnownAnswer r{};
    // fieldsVersion 70: D's own limit (1,000,000), not the 10M pair that runPrimeSieveTest uses.
    r.limit = static_cast<int64_t>(PRIME_SIEVE_1M_LIMIT);
    const int64_t start_ns = monotonic_ns();
    const int64_t count = prime_sieve_count(PRIME_SIEVE_1M_LIMIT);
    r.duration_ms = elapsed_ms_since(start_ns);
    r.prime_count = count;
    if (count < 0) {
        // prime_sieve_count's sentinel for a failed allocation.
        r.status = kKnownAnswerAllocationFailed;
    } else if (count == PRIME_SIEVE_1M_REFERENCE) {
        r.status = kKnownAnswerMatch;
    } else {
        r.status = kKnownAnswerMismatch;
    }
    return r;
}

Sha256KnownAnswer sha256_known_answer() noexcept {
    Sha256KnownAnswer r{};
    const int64_t start_ns = monotonic_ns();
    auto finish = [&]() -> Sha256KnownAnswer {
        r.duration_ms = elapsed_ms_since(start_ns);
        return r;
    };

    // Step 1: a broken SHA implementation would corrupt step 2 and be blamed
    // on the device — rule it out first.
    if (!sha256_self_test()) {
        r.status = kKnownAnswerSelfTestFailed;
        return finish();
    }

    auto buf = std::unique_ptr<uint8_t[]>(new (std::nothrow) uint8_t[SHA256_TEST_INPUT_SIZE]);
    if (!buf) {
        r.status = kKnownAnswerAllocationFailed;
        return finish();
    }
    generate_sha_input(buf.get(), SHA256_TEST_INPUT_SIZE);

    uint8_t actual[kSha256DigestBytes];
    sha256_compute(buf.get(), SHA256_TEST_INPUT_SIZE, actual);
    std::memcpy(r.digest_head, actual, sizeof(r.digest_head));
    r.status = (std::memcmp(actual, SHA256_REF_HASH, sizeof(SHA256_REF_HASH)) == 0)
                   ? kKnownAnswerMatch
                   : kKnownAnswerMismatch;
    return finish();
}

}  // namespace neuralcheck::stress
