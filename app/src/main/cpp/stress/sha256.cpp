// NeuralCheck V2 — SHA-256 scalar implementation.
//
// Direct transcription of FIPS 180-4 §6.2.2. No vendor crypto extensions,
// no SIMD — the goal of sub-phase 1c is a *correct* baseline; speed
// optimisations land later. Performance on Snapdragon 855 is ~80 MB/s
// scalar, which means the 1 MB workload takes ~13 ms. Acceptable for a
// smoke-test entry point and for the verification step inside the
// memory test (sub-phase 1e).

#include "stress/sha256.h"

#include <cstring>

namespace neuralcheck::stress {

namespace {

// FIPS 180-4 §4.2.2 — first 32 bits of fractional parts of cube roots
// of the first 64 primes. These constants are universal; any deviation
// in the array below means the source was corrupted in transit.
constexpr uint32_t K[64] = {
    0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1,
    0x923f82a4, 0xab1c5ed5, 0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3,
    0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174, 0xe49b69c1, 0xefbe4786,
    0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
    0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7, 0xc6e00bf3, 0xd5a79147,
    0x06ca6351, 0x14292967, 0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13,
    0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85, 0xa2bfe8a1, 0xa81a664b,
    0xc24b8b70, 0xc76c51a3, 0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
    0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a,
    0x5b9cca4f, 0x682e6ff3, 0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208,
    0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2,
};

constexpr inline uint32_t rotr(uint32_t x, uint32_t n) noexcept {
    return (x >> n) | (x << (32u - n));
}

// FIPS 180-4 §4.1.2 logical functions
constexpr inline uint32_t Ch(uint32_t x, uint32_t y, uint32_t z) noexcept {
    return (x & y) ^ (~x & z);
}
constexpr inline uint32_t Maj(uint32_t x, uint32_t y, uint32_t z) noexcept {
    return (x & y) ^ (x & z) ^ (y & z);
}
constexpr inline uint32_t BigSigma0(uint32_t x) noexcept {
    return rotr(x, 2) ^ rotr(x, 13) ^ rotr(x, 22);
}
constexpr inline uint32_t BigSigma1(uint32_t x) noexcept {
    return rotr(x, 6) ^ rotr(x, 11) ^ rotr(x, 25);
}
constexpr inline uint32_t SmallSigma0(uint32_t x) noexcept {
    return rotr(x, 7) ^ rotr(x, 18) ^ (x >> 3);
}
constexpr inline uint32_t SmallSigma1(uint32_t x) noexcept {
    return rotr(x, 17) ^ rotr(x, 19) ^ (x >> 10);
}

// Process one 512-bit block (64 bytes). Reads big-endian — SHA-256 is
// defined that way regardless of host byte order, so we shift bytes
// rather than memcpy + ntohl (avoids a header dep on <netinet/in.h>).
inline void sha256_transform(uint32_t state[8], const uint8_t block[64]) noexcept {
    uint32_t W[64];
    for (size_t i = 0; i < 16; ++i) {
        W[i] = (uint32_t(block[i * 4]) << 24)
             | (uint32_t(block[i * 4 + 1]) << 16)
             | (uint32_t(block[i * 4 + 2]) << 8)
             | (uint32_t(block[i * 4 + 3]));
    }
    for (size_t i = 16; i < 64; ++i) {
        W[i] = SmallSigma1(W[i - 2]) + W[i - 7] + SmallSigma0(W[i - 15]) + W[i - 16];
    }

    uint32_t a = state[0], b = state[1], c = state[2], d = state[3];
    uint32_t e = state[4], f = state[5], g = state[6], h = state[7];

    for (size_t i = 0; i < 64; ++i) {
        const uint32_t T1 = h + BigSigma1(e) + Ch(e, f, g) + K[i] + W[i];
        const uint32_t T2 = BigSigma0(a) + Maj(a, b, c);
        h = g; g = f; f = e;
        e = d + T1;
        d = c; c = b; b = a;
        a = T1 + T2;
    }

    state[0] += a; state[1] += b; state[2] += c; state[3] += d;
    state[4] += e; state[5] += f; state[6] += g; state[7] += h;
}

}  // namespace

void sha256_compute(const uint8_t* input, size_t len, uint8_t out[kSha256DigestBytes]) noexcept {
    // FIPS 180-4 §5.3.3 — initial hash values: first 32 bits of
    // fractional parts of square roots of the first 8 primes.
    uint32_t state[8] = {
        0x6a09e667, 0xbb67ae85, 0x3c6ef372, 0xa54ff53a,
        0x510e527f, 0x9b05688c, 0x1f83d9ab, 0x5be0cd19,
    };

    // Process all complete 64-byte blocks straight from input. Avoids
    // copying bulk data into a staging buffer for the common case
    // (1 MB input → 16,384 blocks of zero-copy work).
    const uint8_t* p = input;
    size_t remaining = len;
    while (remaining >= 64) {
        sha256_transform(state, p);
        p += 64;
        remaining -= 64;
    }

    // Final block(s): tail bytes + padding. SHA-256 requires the
    // message length appended as a 64-bit big-endian value at the end,
    // followed by enough zeros to round to a 64-byte boundary, with a
    // single 0x80 byte separating message from padding. Two final
    // blocks are needed when remaining ≥ 56 (length doesn't fit).
    uint8_t buf[128] = {};
    std::memcpy(buf, p, remaining);
    buf[remaining] = 0x80;

    const size_t pad_blocks = (remaining < 56) ? 1 : 2;
    const size_t total_pad = pad_blocks * 64;

    // Encode bit-length as big-endian 64-bit at the very end.
    const uint64_t bit_len = static_cast<uint64_t>(len) * 8u;
    for (size_t i = 0; i < 8; ++i) {
        buf[total_pad - 1 - i] = static_cast<uint8_t>(bit_len >> (i * 8));
    }

    sha256_transform(state, buf);
    if (pad_blocks == 2) {
        sha256_transform(state, buf + 64);
    }

    // Big-endian output.
    for (size_t i = 0; i < 8; ++i) {
        out[i * 4]     = static_cast<uint8_t>(state[i] >> 24);
        out[i * 4 + 1] = static_cast<uint8_t>(state[i] >> 16);
        out[i * 4 + 2] = static_cast<uint8_t>(state[i] >> 8);
        out[i * 4 + 3] = static_cast<uint8_t>(state[i]);
    }
}

void generate_sha_input(uint8_t* buf, size_t len) noexcept {
    // 32-bit LCG with Knuth multiplicative constant + DEADBEEF additive.
    // Pattern-free but 100% deterministic; XOR with 0xA5 prevents the
    // top-byte of the LCG state from ever being a "round" value visible
    // in hexdump diagnostics.
    uint32_t state = 0x12345678u;
    for (size_t i = 0; i < len; ++i) {
        state = state * 0x9E3779B1u + 0xDEADBEEFu;
        buf[i] = static_cast<uint8_t>((state >> 24) ^ 0xA5u);
    }
}

bool sha256_self_test() noexcept {
    // FIPS 180-4 §A.1 test vector: SHA-256("abc") =
    //   ba7816bf 8f01cfea 414140de 5dae2223
    //   b00361a3 96177a9c b410ff61 f20015ad
    static constexpr uint8_t kInput[] = { 'a', 'b', 'c' };
    static constexpr uint8_t kExpected[32] = {
        0xba, 0x78, 0x16, 0xbf, 0x8f, 0x01, 0xcf, 0xea,
        0x41, 0x41, 0x40, 0xde, 0x5d, 0xae, 0x22, 0x23,
        0xb0, 0x03, 0x61, 0xa3, 0x96, 0x17, 0x7a, 0x9c,
        0xb4, 0x10, 0xff, 0x61, 0xf2, 0x00, 0x15, 0xad,
    };

    uint8_t actual[32];
    sha256_compute(kInput, sizeof(kInput), actual);
    return std::memcmp(actual, kExpected, 32) == 0;
}

}  // namespace neuralcheck::stress
