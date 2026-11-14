#include "stress/neon_fma.h"

#include <arm_neon.h>

#include <cstdint>
#include <cstring>

namespace neuralcheck::stress {

namespace {

// Maps a 32-bit LCG state to a [-0.5, 0.5) float in a way that's
// reproducible bit-for-bit across compilers: convert to double first
// (lossless for uint32_t) then divide and subtract, then truncate to
// float. This avoids the float(state) precision loss path which can
// produce different results under different rounding modes.
inline float lcg_to_float(uint32_t state) noexcept {
    return static_cast<float>(static_cast<double>(state) / 4294967296.0 - 0.5);
}

// CRC-32 (IEEE 802.3, reflected polynomial 0xEDB88320). Table-free
// (Barrett-style bitwise) — small enough that the inner inlining is
// faster than a 1 KiB lookup table fetched from cold cache. We only
// invoke this on Tier 2 failures, where speed isn't critical anyway.
inline uint32_t crc32_update(uint32_t crc, const uint8_t* data, size_t len) noexcept {
    crc = ~crc;
    for (size_t i = 0; i < len; ++i) {
        crc ^= data[i];
        for (int b = 0; b < 8; ++b) {
            // Bit n of polynomial 0xEDB88320 selects whether to xor on
            // each shift. The branchless form below produces identical
            // output to the if-tree but avoids per-iteration branches.
            const uint32_t mask = -(crc & 1u);
            crc = (crc >> 1) ^ (0xEDB88320u & mask);
        }
    }
    return ~crc;
}

}  // namespace

void neon_fma_generate_matrix(float* dst, uint32_t seed) noexcept {
    uint32_t state = seed;
    for (size_t i = 0; i < kNeonFmaCells; ++i) {
        state = state * 0x9E3779B1u + 0xDEADBEEFu;
        dst[i] = lcg_to_float(state);
    }
}

void neon_fma_matmul_scalar(const float* A, const float* B, float* C) noexcept {
    // Naive triple-loop — used as the deterministic in-process reference
    // for NEON Tier 1 verification, NOT as a stress workload. Slower
    // than NEON by ~4× on Snapdragon 855 but completes in ~30 ms for
    // 256³ ≈ 16.7M FMAs at 500 MFLOPS scalar.
    for (size_t i = 0; i < kNeonFmaDim; ++i) {
        for (size_t j = 0; j < kNeonFmaDim; ++j) {
            float sum = 0.0f;
            for (size_t k = 0; k < kNeonFmaDim; ++k) {
                // Explicit (a*b)+c form, separated by the +, so the
                // -ffp-contract=off CMake flag prevents the compiler
                // from fusing this into an FMA instruction. Without
                // that flag the scalar reference would silently become
                // identical to NEON output, defeating verification.
                sum = sum + A[i * kNeonFmaDim + k] * B[k * kNeonFmaDim + j];
            }
            C[i * kNeonFmaDim + j] = sum;
        }
    }
}

void neon_fma_matmul_neon(const float* A, const float* B, float* C) noexcept {
    // Row-major matmul, 4 columns at a time. For each (i, j-block) of
    // the output:
    //   sum_vec = Σ_k A[i,k] · B[k, j..j+3]   (broadcast A, vector B)
    // vfmaq_f32 produces a single-rounded FMA per lane → if the FMA
    // pipeline drops a bit, the lane's result diverges from scalar by
    // ≫ tolerance.
    for (size_t i = 0; i < kNeonFmaDim; ++i) {
        const float* a_row = A + i * kNeonFmaDim;
        float* c_row = C + i * kNeonFmaDim;
        for (size_t j = 0; j < kNeonFmaDim; j += 4) {
            float32x4_t sum = vdupq_n_f32(0.0f);
            for (size_t k = 0; k < kNeonFmaDim; ++k) {
                const float32x4_t a_v = vdupq_n_f32(a_row[k]);
                const float32x4_t b_v = vld1q_f32(&B[k * kNeonFmaDim + j]);
                sum = vfmaq_f32(sum, a_v, b_v);
            }
            vst1q_f32(&c_row[j], sum);
        }
    }
}

float neon_fma_trace(const float* M) noexcept {
    float trace = 0.0f;
    for (size_t i = 0; i < kNeonFmaDim; ++i) {
        trace = trace + M[i * kNeonFmaDim + i];
    }
    return trace;
}

uint32_t neon_fma_row_crc32(const float* row) noexcept {
    return crc32_update(0u,
                        reinterpret_cast<const uint8_t*>(row),
                        kNeonFmaDim * sizeof(float));
}

}  // namespace neuralcheck::stress
