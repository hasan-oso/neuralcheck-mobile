# NeuralCheck — Tech-debt register

Open low-priority items that don't block any current phase but should be
addressed before whatever future work un-blocks them. Each entry: title,
severity, where discovered, what to do.

| ID | Item | Severity | Discovered | Status |
|----|------|----------|------------|--------|
| [TD-001](#td-001) | `production_stress.cpp:446` — 8 GiB literal arithmetic | low | phase_1_7 (Realme RMX3231 dual-ABI build attempt) | open |

---

## TD-001 — `production_stress.cpp:446` 8 GiB literal arithmetic

### What

Line 446 of `app/src/main/cpp/stress/production_stress.cpp` declares an 8 GiB cap with the literal:

```cpp
constexpr size_t k8GiB = 8ull * 1024ull * 1024ull * 1024ull;
```

The expression is correct on **arm64-v8a** where `size_t` is 64 bits. On any **32-bit** target (`armeabi-v7a`, `x86`), `size_t` is 32 bits and the value `8'589'934'592` does not fit. The compiler emits:

```
production_stress.cpp:446:55: warning: implicit conversion from 'unsigned long long'
to 'const size_t' (aka 'const unsigned int') changes value from 8589934592 to 0
[-Wconstant-conversion]
```

Result: on a 32-bit build the cap silently becomes **0 bytes**, making the workload skip all real work without erroring.

### Why it's only "low" severity

* NeuralCheck currently ships **arm64-v8a only** per [ADR-001](architecture/decisions/ADR-001-no-32bit-support.md). The bug is unreachable in production builds.
* The compiler emits a clear `-Wconstant-conversion` warning at every build, so anyone reading the CMake output sees it.
* The bug surfaces as "workload completes instantly with 0 bytes touched" — easy to spot in a single test scan.

### Why it must be fixed before any 32-bit re-enable

The moment `armeabi-v7a` (or any 32-bit ABI) returns to `app/build.gradle.kts` `abiFilters`, scans on those devices would silently pass with no actual memory stress. Lab analysis would see `memoryTestedBytes = 0` and either flag the device as broken or — worse — accept the scan as valid because the structure is otherwise complete.

### Fix

Replace the literal so each operand is `ull` (unsigned long long), and use `static_cast<size_t>(...)` so the truncation is explicit + reviewable rather than silent:

```cpp
// Before:
constexpr size_t k8GiB = 8ull * 1024ull * 1024ull * 1024ull;

// After (option A — long long math, explicit cast):
constexpr unsigned long long k8GiB_ull =
    8ull * 1024ull * 1024ull * 1024ull;
constexpr size_t k8GiB = static_cast<size_t>(k8GiB_ull);  // truncates to 0 on 32-bit; intentional

// After (option B — recompute on 32-bit so the cap stays meaningful):
#if SIZE_MAX > 0xFFFFFFFFu
    constexpr size_t k8GiB = 8ull * 1024ull * 1024ull * 1024ull;
#else
    // 32-bit address space caps any single allocation well below 8 GiB anyway.
    // Set the cap to 1 GiB so the workload still does real work.
    constexpr size_t k8GiB = 1u * 1024u * 1024u * 1024u;
#endif
```

Recommend **option B** when the 32-bit re-enable lands, because it keeps the workload meaningful instead of degrading to a silent no-op.

### Acceptance

* Compile clean on both `arm64-v8a` and (when re-enabled) `armeabi-v7a` — no `-Wconstant-conversion` warning.
* On a 32-bit build, `runMemoryStress` reports `tested_bytes > 0` after a 60s phase. Suggested validation: log `memoryTestedBytes` in the V2 footer and confirm it scales with phase duration.

### References

* Discovered during: [`docs/audits/phase_1_7/realme_build_failure.md`](audits/phase_1_7/realme_build_failure.md)
* Related decision: [`ADR-001`](architecture/decisions/ADR-001-no-32bit-support.md)
