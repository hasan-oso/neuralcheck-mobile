// NeuralCheck V2 — NEON FMA matrix multiply (sub-phase 1d).
//
// 256×256 FP32 matrix multiply using ARMv8 NEON intrinsics (vfmaq_f32).
// Per master plan §3.3 this kernel is supposed to stress FPU + FMA
// pipelines + L1/L2 cache (the column-stride access pattern on B is
// deliberately cache-unfriendly).
//
// Verification strategy is the two-tier scheme decided in
// IMPLEMENTATION_NOTES_V2.md Q2.1:
//
//   Tier 1 (fast, every call): tolerance check on trace(C). Reference
//          is computed in-process by a scalar matmul on the SAME inputs,
//          so we don't depend on a hardcoded device-specific reference.
//          A NEON-only FMA pipeline defect produces a NEON output that
//          differs from the scalar reference by ≫ 1e-7 ratio; ordinary
//          FP rounding stays well inside it.
//
//   Tier 2 (only on Tier 1 fail, sub-phase 1d follow-up): per-row CRC32
//          comparison to localise the defect. Hooks are in place (see
//          neon_fma_row_crc32 below) but the persisted reference array
//          is captured during the first verified run on a known-good
//          device — until then a Tier 1 fail surfaces as
//          verifyNeonFma() = false without per-row attribution.

#pragma once

#include <cstddef>
#include <cstdint>

namespace neuralcheck::stress {

constexpr size_t kNeonFmaDim = 256;
constexpr size_t kNeonFmaCells = kNeonFmaDim * kNeonFmaDim;  // 65,536 floats

// Tolerance ratio for Tier 1 trace verification, per Q2.1: 1e-7 of the
// reference value (≈ 3-5 ULPs around 4×10⁶). Tighter than the 1e-5 first
// proposal because trace(NEON output) vs trace(scalar reference) share
// the same input bytes — the only divergence source is the order in
// which 256 partial products are summed, which is deterministic per
// implementation. Real FMA pipeline defects exceed this by 100+ ULPs.
constexpr float kNeonFmaToleranceRatio = 1e-7f;

// Generates a 256×256 FP32 matrix in row-major order. Two seeds (one
// per matrix) so A and B aren't trivially related — we want to
// exercise dot products across distinct distributions, not a × a.
// Values land in [-0.5, 0.5) so trace(C) magnitude doesn't explode.
void neon_fma_generate_matrix(float* dst, uint32_t seed) noexcept;

// Reference matmul: pure scalar `for (i,j,k) sum += A[i,k] * B[k,j]`.
// No NEON, no SIMD. Deterministic across compilers thanks to the
// `-ffp-contract=off` + `-fno-fast-math` flags pinned in CMakeLists.txt.
// Used to produce the in-process Tier 1 reference trace.
void neon_fma_matmul_scalar(const float* A, const float* B, float* C) noexcept;

// NEON FMA matmul: row of A × columns of B, 4 columns at a time via
// vfmaq_f32. The kernel under verification — should match
// neon_fma_matmul_scalar within kNeonFmaToleranceRatio in trace value.
void neon_fma_matmul_neon(const float* A, const float* B, float* C) noexcept;

// Returns trace(M) = M[0,0] + M[1,1] + … + M[255,255] for a row-major
// 256×256 matrix.
float neon_fma_trace(const float* M) noexcept;

// Returns CRC32 (IEEE 802.3 polynomial, 0xEDB88320 reflected) of a
// single row's 256 float bytes (1024 bytes total). Used for Tier 2
// per-row defect localisation when Tier 1 fails — bit-exact, picks up
// single-bit mantissa flips that survive the trace tolerance.
uint32_t neon_fma_row_crc32(const float* row) noexcept;

}  // namespace neuralcheck::stress
