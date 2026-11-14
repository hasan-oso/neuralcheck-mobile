#include "stress/memory_test.h"

#include <atomic>
#include <cstring>

#include "reference/reference_values.h"
#include "stress/sha256.h"

namespace neuralcheck::stress {

namespace {

// A compiler barrier. Each step below reads memory another step just wrote;
// without a barrier at the call site the optimiser is entitled to reason
// "I just memset this, so every byte I read is the pattern" and fold the
// verification away. The helpers are also noinline so no step can see into
// another one.
#define NC_COMPILER_BARRIER() asm volatile("" ::: "memory")

// Seed of the size-independent random pattern (fieldsVersion 68). A defined
// constant, not derived from anything on the device.
constexpr uint64_t kRandomPatternSeed = 0xD1B54A32D192ED03ull;

// ⇑(r<expect>, w<next>): forward sweep.
__attribute__((noinline))
int64_t sweep_up(uint8_t* buf, size_t bytes, uint8_t expect, uint8_t next) noexcept {
    int64_t errors = 0;
    for (size_t i = 0; i < bytes; ++i) {
        if (buf[i] != expect) ++errors;
        buf[i] = next;
    }
    return errors;
}

// ⇓(r<expect>, w<next>): reverse sweep.
__attribute__((noinline))
int64_t sweep_down(uint8_t* buf, size_t bytes, uint8_t expect, uint8_t next) noexcept {
    int64_t errors = 0;
    for (size_t i = bytes; i-- > 0;) {
        if (buf[i] != expect) ++errors;
        buf[i] = next;
    }
    return errors;
}

// ⇕(r<expect>): read-only sweep.
__attribute__((noinline))
int64_t read_all(const uint8_t* buf, size_t bytes, uint8_t expect) noexcept {
    int64_t errors = 0;
    for (size_t i = 0; i < bytes; ++i) {
        if (buf[i] != expect) ++errors;
    }
    return errors;
}

// One Walking-Ones pattern: fill with a single-bit value, read it back.
__attribute__((noinline))
int64_t walking_ones_pattern(uint8_t* buf, size_t bytes, int bit) noexcept {
    const uint8_t pattern = static_cast<uint8_t>(1u << bit);
    std::memset(buf, pattern, bytes);
    NC_COMPILER_BARRIER();
    return read_all(buf, bytes, pattern);
}

inline int64_t differing_bytes(uint64_t diff) noexcept {
    int64_t n = 0;
    for (int i = 0; i < 8; ++i) {
        if (((diff >> (8 * i)) & 0xFFu) != 0) ++n;
    }
    return n;
}

}  // namespace

int64_t march_c_minus(uint8_t* buf, size_t bytes) noexcept {
    if (bytes == 0) return 0;
    int64_t errors = 0;

    // Step 1 ⇕(w0): write 0x00 everywhere. No verification at this step
    // — we're just establishing the initial state.
    std::memset(buf, 0x00, bytes);
    NC_COMPILER_BARRIER();

    errors += sweep_up(buf, bytes, 0x00, 0xFF);    // Step 2 ⇑(r0,w1)
    errors += sweep_up(buf, bytes, 0xFF, 0x00);    // Step 3 ⇑(r1,w0)
    errors += sweep_down(buf, bytes, 0x00, 0xFF);  // Step 4 ⇓(r0,w1)
    errors += sweep_down(buf, bytes, 0xFF, 0x00);  // Step 5 ⇓(r1,w0)
    errors += read_all(buf, bytes, 0x00);          // Step 6 ⇕(r0)
    return errors;
}

int64_t walking_ones(uint8_t* buf, size_t bytes) noexcept {
    if (bytes == 0) return 0;
    int64_t errors = 0;
    for (int bit = 0; bit < 8; ++bit) {
        errors += walking_ones_pattern(buf, bytes, bit);
    }
    return errors;
}

void generate_memory_pattern(uint8_t* buf, size_t bytes) noexcept {
    // Distinct seed (0xCAFEBABE) and XOR mask (0x5A) from the SHA-256
    // input generator's (0x12345678 / 0xA5) so the two kernels exercise
    // different bit patterns; a defect that's quiet under one pattern
    // shows up under the other.
    uint32_t state = 0xCAFEBABEu;
    for (size_t i = 0; i < bytes; ++i) {
        state = state * 0x9E3779B1u + 0xDEADBEEFu;
        buf[i] = static_cast<uint8_t>((state >> 24) ^ 0x5Au);
    }
}

bool random_pattern_sha_verify(uint8_t* buf, size_t bytes) noexcept {
    if (bytes != MEMORY_RANDOM_SHA_BUFFER_SIZE) {
        // The precomputed reference is specific to a 32 MiB buffer;
        // any other size has no reference to compare against and is
        // treated as "skipped, not failed" by returning false. The JNI
        // layer logs the size mismatch separately so this isn't
        // confused with a real defect. (The stress phase no longer goes
        // through here: random_pattern_write/verify below have no size.)
        return false;
    }

    generate_memory_pattern(buf, bytes);

    uint8_t actual[kSha256DigestBytes];
    sha256_compute(buf, bytes, actual);

    return std::memcmp(actual, MEMORY_RANDOM_SHA_REFERENCE, kSha256DigestBytes) == 0;
}

uint64_t random_pattern_word(uint64_t index) noexcept {
    // splitmix64's finalizer over a counter: a bijection of the index, so no
    // two words of one run collide, and no word depends on another — the
    // pattern is a pure function of the absolute index.
    uint64_t x = index * 0x9E3779B97F4A7C15ull + kRandomPatternSeed;
    x ^= x >> 30;
    x *= 0xBF58476D1CE4E5B9ull;
    x ^= x >> 27;
    x *= 0x94D049BB133111EBull;
    x ^= x >> 31;
    return x;
}

__attribute__((noinline))
void random_pattern_write(uint8_t* buf, size_t bytes, uint64_t first_word_index) noexcept {
    const size_t words = bytes / 8;
    for (size_t i = 0; i < words; ++i) {
        const uint64_t w = random_pattern_word(first_word_index + i);
        std::memcpy(buf + i * 8, &w, 8);
    }
}

__attribute__((noinline))
int64_t random_pattern_verify(const uint8_t* buf, size_t bytes, uint64_t first_word_index) noexcept {
    const size_t words = bytes / 8;
    int64_t errors = 0;
    for (size_t i = 0; i < words; ++i) {
        uint64_t got;
        std::memcpy(&got, buf + i * 8, 8);
        const uint64_t want = random_pattern_word(first_word_index + i);
        if (got != want) {
            errors += differing_bytes(got ^ want);
        }
    }
    return errors;
}

TileTestResult memory_test_tile(uint8_t* tile,
                                size_t bytes,
                                uint64_t first_word_index,
                                const StopControl& stop) noexcept {
    TileTestResult r{};
    r.stopped_by = StopReason::kNone;
    if (bytes == 0) {
        r.completed = true;
        return r;
    }

    // Consulted before every step. A stop leaves `completed` false: the
    // errors already counted stay (a mismatch is a mismatch), the tile does
    // not count as covered.
    auto stop_requested = [&]() -> bool {
        r.stopped_by = stop.check();
        return r.stopped_by != StopReason::kNone;
    };

    // ── March C- ──
    if (stop_requested()) return r;
    std::memset(tile, 0x00, bytes);                                   // ⇕(w0)
    NC_COMPILER_BARRIER();
    if (stop_requested()) return r;
    r.march_errors += sweep_up(tile, bytes, 0x00, 0xFF);              // ⇑(r0,w1)
    if (stop_requested()) return r;
    r.march_errors += sweep_up(tile, bytes, 0xFF, 0x00);              // ⇑(r1,w0)
    if (stop_requested()) return r;
    r.march_errors += sweep_down(tile, bytes, 0x00, 0xFF);            // ⇓(r0,w1)
    if (stop_requested()) return r;
    r.march_errors += sweep_down(tile, bytes, 0xFF, 0x00);            // ⇓(r1,w0)
    if (stop_requested()) return r;
    r.march_errors += read_all(tile, bytes, 0x00);                    // ⇕(r0)

    // ── Walking Ones ──
    for (int bit = 0; bit < 8; ++bit) {
        if (stop_requested()) return r;
        r.walking_ones_errors += walking_ones_pattern(tile, bytes, bit);
    }

    // ── Random write / verify ──
    if (stop_requested()) return r;
    random_pattern_write(tile, bytes, first_word_index);
    NC_COMPILER_BARRIER();
    if (stop_requested()) return r;
    r.random_verify_errors += random_pattern_verify(tile, bytes, first_word_index);

    r.completed = true;
    return r;
}

}  // namespace neuralcheck::stress
