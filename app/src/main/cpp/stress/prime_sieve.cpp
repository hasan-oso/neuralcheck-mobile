#include "stress/prime_sieve.h"

#include <cstdint>
#include <memory>
#include <new>

namespace neuralcheck::stress {

int64_t prime_sieve_count(uint32_t limit) noexcept {
    if (limit < 2) {
        return 0;
    }

    // sieve[i] == 0 → i is prime; sieve[i] == 1 → i is composite.
    // unique_ptr<uint8_t[]> with `nothrow` so we report allocation
    // failure as a sentinel return rather than std::bad_alloc — the
    // JNI bridge can't propagate C++ exceptions cleanly to Kotlin.
    const size_t size = static_cast<size_t>(limit) + 1;
    auto sieve = std::unique_ptr<uint8_t[]>(new (std::nothrow) uint8_t[size]());
    if (!sieve) {
        return -1;
    }

    sieve[0] = 1;
    sieve[1] = 1;

    // Standard Eratosthenes optimization: mark composites starting from
    // p² (smaller multiples of p have already been marked by primes
    // < p). Loop bound is p² ≤ limit, i.e. p ≤ √limit. We use uint64_t
    // for the inner index so p × p never overflows even if a future
    // caller raises the limit toward UINT32_MAX.
    for (uint32_t p = 2; static_cast<uint64_t>(p) * p <= limit; ++p) {
        if (sieve[p] == 0) {
            for (uint64_t i = static_cast<uint64_t>(p) * p; i <= limit; i += p) {
                sieve[i] = 1;
            }
        }
    }

    int64_t count = 0;
    for (uint32_t i = 2; i <= limit; ++i) {
        if (sieve[i] == 0) {
            ++count;
        }
    }
    return count;
}

}  // namespace neuralcheck::stress
