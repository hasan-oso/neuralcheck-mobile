# ADR-001: NeuralCheck supports arm64-v8a only (defer 32-bit support)

**Status:** Accepted
**Date:** 2026-05-08
**Branch:** `phase-1.7-cleanup`
**Supersedes:** none
**Superseded by:** none

## Decision

NeuralCheck ships **arm64-v8a only**. The `app/build.gradle.kts` `ndk.abiFilters` block stays at `+= "arm64-v8a"`. 32-bit ARM (`armeabi-v7a`) is **out-of-scope** until a future, post-Phase-1.8 market-data-driven re-evaluation.

Devices with 32-bit-only userspace — most notably **Realme C11 2021 / RMX3231** (Android Go on Helio G35), and any equivalent entry-tier MediaTek-based OEM build — cannot install this APK. They are knowingly excluded.

## Context

NeuralCheck targets `minSdk = 28` (Android 9.0). The original assumption (documented in a since-removed comment on the `abiFilters` block) was "every supported device on minSdk 28 is 64-bit". This held for the Snapdragon / Exynos / Kirin tier we validated against (Samsung S10 5G, Xiaomi 14T, etc.).

Realme broke that assumption: the Realme C-series budget tier on Helio G35 / G37 silicon ships an Android Go userland that's 32-bit ARMv7 only on Android 10/11/12, even though the SoC itself is 64-bit-capable. Trying to install the arm64-only APK on RMX3231 fails with `INSTALL_FAILED_NO_MATCHING_ABIS`.

A dual-ABI rebuild was attempted on `phase-1.7-cleanup` (Phase 1.7 follow-up). The arm64-v8a slice compiled cleanly; the armeabi-v7a slice failed at the C++ stage:

```
neon_fma.cpp:86:23: error: use of undeclared identifier 'vfmaq_f32'
```

`vfmaq_f32` is the FMA intrinsic that's mandatory + always declared on AArch64 but requires `-mfpu=neon-vfpv4` (VFPv4 FPU extension) on ARMv7 — not enabled by the default NDK ARMv7 toolchain.

## Rationale

1. **Phase scope**: The Phase 1.7 brief explicitly forbade modifying C++ / NDK / CMakeLists. The fix needed touches all three. Doing it as an undocumented detour would have run against the spec.
2. **Effort vs payoff**: A proper port needs three things — `-mfpu=neon-vfpv4` flag (or scalar fallback in `neon_fma.cpp`), audit of all NEON intrinsics in the kernel set for similar gaps, and a fix for the latent 8 GiB literal-overflow bug in `production_stress.cpp:446` that only manifests when `size_t` is 32-bit. Estimated 2–4 hours focused C++ work plus a re-validation pass on a 32-bit device.
3. **Phase 1.8 simplification**: The next phase introduces relative-calibration scoring, which may collapse some of the per-architecture diagnostic guarantees that currently rely on FMA-specific defect detection. Deferring the 32-bit port until after Phase 1.8 lands means the eventual port can target a smaller, more stable surface.
4. **Market priority**: NeuralCheck's primary diagnostic targets are mid-and-flagship phones in the used-phone resale market. The 32-bit-only entry tier (Realme C-series / Tecno / Itel / similar) is a different market segment with different unit economics and a much narrower upside for AI-camouflage stress diagnostics. Re-evaluation should be data-driven (% of incoming used-phone units actually running 32-bit Android) rather than driven by a single device showing up at the test bench.

## Consequences

* APK keeps a smaller binary (no ~5 MB ARMv7 native-lib slice).
* CI / build matrix stays simple — single ABI, single CMake invocation per build.
* Realme C11 2021 and equivalent 32-bit-only Android-Go devices cannot run NeuralCheck. Users attempting installation see `INSTALL_FAILED_NO_MATCHING_ABIS` from `pm install`.
* Latent 32-bit bug in `production_stress.cpp:446` (8 GiB literal arithmetic with intermediate `int` overflow → silently truncates to 0) is not blocking but is tracked as tech debt.
* If/when the 32-bit decision is reversed, the work has a known starting point (this ADR + the build-failure analysis) and won't be re-investigated from scratch.

## Build evidence

Failed dual-ABI build session:

* Full analysis + classification: [`docs/audits/phase_1_7/realme_build_failure.md`](../../audits/phase_1_7/realme_build_failure.md)
* Last 200 lines of build log: [`docs/audits/phase_1_7/realme_build_failure_tail.txt`](../../audits/phase_1_7/realme_build_failure_tail.txt)
* Full build log (transient — not committed): `build_dual_abi.log` at repo root, 290 lines, BUILD FAILED in 51s
* Device that triggered the attempt: Realme RMX3231 (`product:RMX3231TR`, brand:realme, ABI:`armeabi-v7a,armeabi`, Android 11)

The arm64-v8a slice compiled cleanly during that same build, so the failure is exclusively about the 32-bit toolchain interaction with NEON-FMA intrinsics.

## Tracked separately

The 8 GiB literal-overflow warning observed during the failed build is **not** related to the abiFilter decision but was uncovered by the same compile attempt. It is tracked in [`docs/tech_debt.md`](../../tech_debt.md) so it gets fixed before any future 32-bit re-enable lands.

## Re-evaluation triggers

Reopen this ADR if any of these become true:

* >5% of incoming used-phone units in the lab pipeline ship a 32-bit-only userland.
* A specific 32-bit OEM device family becomes a prioritized diagnostic target (commercial agreement, region-specific rollout, etc.).
* Phase 1.8 calibration work removes the dependency on architecture-specific FMA defect detection, lowering the cost of the port.
* The TFLite-based AI workloads (Phase 1.6) become the sole stress path and the synthetic NEON kernels can be deleted from `app/src/main/cpp/stress/` entirely — at that point the 32-bit port is only about NDK target flags, not intrinsic compatibility.
