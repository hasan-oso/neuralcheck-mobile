// NeuralCheck V2 — helpers shared by the verification producers
// (fieldsVersion 68: memory verify, storage verify, CPU known-answer).
//
// Two things live here because more than one producer needs them:
//   - a monotonic clock in nanoseconds, so every deadline in native code is
//     measured on one clock and callers pass DURATIONS (never absolute
//     times: Kotlin's SystemClock.elapsedRealtime() is CLOCK_BOOTTIME, this
//     is CLOCK_MONOTONIC, and the two must not be mixed);
//   - one cancel flag the Kotlin side can raise to make a long native call
//     return early. The producers only ever READ it; the Kotlin caller owns
//     the protocol (set, wait for the call to return, clear).

#pragma once

#include <atomic>
#include <cstdint>
#include <ctime>

namespace neuralcheck::stress {

inline int64_t monotonic_ns() noexcept {
    timespec ts{};
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return static_cast<int64_t>(ts.tv_sec) * 1'000'000'000LL + static_cast<int64_t>(ts.tv_nsec);
}

inline int64_t elapsed_ms_since(int64_t start_ns) noexcept {
    return (monotonic_ns() - start_ns) / 1'000'000LL;
}

// The single cancel flag of the verification producers (defined in
// native_common.cpp). A producer checks it between units of work, never
// inside a tight loop.
std::atomic<bool>& native_verify_cancel_flag() noexcept;

// Why a cooperative stop was requested. kNone = keep going.
enum class StopReason : int32_t {
    kNone = 0,
    kDeadline = 1,
    kCancelled = 2,
};

// Deadline + cancel flag, checked between units of work (a sweep, a tile, a
// block) — long enough units that the clock read is free, short enough that
// the overshoot stays small.
struct StopControl {
    int64_t deadline_ns;  // CLOCK_MONOTONIC; <= 0 means "no deadline"
    const std::atomic<bool>* cancel;  // may be null

    StopReason check() const noexcept {
        if (cancel != nullptr && cancel->load(std::memory_order_relaxed)) {
            return StopReason::kCancelled;
        }
        if (deadline_ns > 0 && monotonic_ns() >= deadline_ns) {
            return StopReason::kDeadline;
        }
        return StopReason::kNone;
    }
};

}  // namespace neuralcheck::stress
