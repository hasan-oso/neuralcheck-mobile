# NeuralCheck V2 — native stress library.
#
# Sub-phase 1a: only the JNI bridge with a pingNative() smoke-test. Real
# kernels (NEON FMA matrix multiply, prime sieve, SHA-256, March memory
# tests, thermal reader) land in 1b–1g; each new source goes both into
# add_library() below AND into a matching extern declaration / dispatch
# in jni/stress_jni.cpp.
#
# Per Q2.5 of IMPLEMENTATION_NOTES_V2.md, this library is built without
# -ffast-math anywhere — every kernel that arrives in subsequent sub-phases
# is verification-bearing and IEEE-754 strictness is mandatory for the
# verification step to be deterministic.

cmake_minimum_required(VERSION 3.22.1)
project(stress LANGUAGES CXX)

# C++17 — gives us std::string_view, structured bindings, and constexpr
# improvements without committing to C++20 features that NDK r27
# occasionally still flags as unstable on certain toolchain combos.
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# Hide every symbol that isn't explicitly marked JNIEXPORT. Smaller .so +
# we don't accidentally promise ABI stability on internal helpers that
# later sub-phases will refactor.
set(CMAKE_CXX_VISIBILITY_PRESET hidden)
set(CMAKE_VISIBILITY_INLINES_HIDDEN ON)

add_library(stress SHARED
    jni/stress_jni.cpp
    stress/prime_sieve.cpp
    stress/sha256.cpp
    stress/neon_fma.cpp
    stress/memory_test.cpp
    stress/thermal_reader.cpp
    stress/cpu_affinity.cpp
    stress/production_stress.cpp
    stress/native_common.cpp
    stress/memory_verify.cpp
    stress/storage_verify.cpp
    stress/known_answer.cpp
)

# Make `#include "stress/foo.h"` and `#include "reference/foo.h"` resolve
# from any source file regardless of its own location. Avoids brittle
# `../stress/foo.h` relatives in the JNI bridge.
target_include_directories(stress PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
)

# Per-target compile options. We pin -ffp-contract=off and -fno-fast-math
# at the library level so future kernels inherit the IEEE-754 contract by
# default — opting individual files INTO faster-but-non-deterministic math
# would require a deliberate set_source_files_properties() override (and a
# code review).
target_compile_options(stress PRIVATE
    -O3
    -ffunction-sections
    -fdata-sections
    -ffp-contract=off
    -fno-fast-math
    -Wall
    -Wextra
)

# Strip the release .so to shrink the APK; keep symbols in debug builds so
# addr2line / ndk-stack can resolve Logcat traces from pre-release devices
# without a separate symbol-server fetch.
target_link_options(stress PRIVATE
    "$<$<CONFIG:Release>:-Wl,--gc-sections>"
    "$<$<CONFIG:Release>:-Wl,--strip-all>"
)

# Android log API — every JNI function entry/exit logs to the
# `NeuralCheck.Native` tag for consistent on-device debug.
find_library(android-log log)
target_link_libraries(stress PRIVATE ${android-log})
