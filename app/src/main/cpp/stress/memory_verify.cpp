#include "stress/memory_verify.h"

#include <sys/mman.h>
#include <sys/sysinfo.h>

#include <cerrno>
#include <cstring>

#include "stress/memory_test.h"
#include "stress/native_common.h"

namespace neuralcheck::stress {

namespace {

void* map_anonymous(size_t bytes, int* err_no) noexcept {
    // MAP_NORESERVE: the mapping is lazily committed, only the tiles actually
    // tested ever become resident. Tiles stay mapped until the end so each new
    // tile is served from DIFFERENT physical pages (freeing a tile would let the
    // kernel hand the same frames to the next one and re-test the same cells).
    void* p = mmap(nullptr, bytes, PROT_READ | PROT_WRITE,
                   MAP_PRIVATE | MAP_ANONYMOUS | MAP_NORESERVE, -1, 0);
    if (p == MAP_FAILED) {
        *err_no = errno;
        return nullptr;
    }
    return p;
}

}  // namespace

MemoryVerifyResult run_memory_verify(int percent_ram, int64_t time_cap_ms) noexcept {
    MemoryVerifyResult r{};
    r.free_bytes = -1;  // not read yet
    const int64_t start_ns = monotonic_ns();
    auto finish = [&]() -> MemoryVerifyResult {
        r.elapsed_ms = elapsed_ms_since(start_ns);
        return r;
    };

    if (time_cap_ms <= 0) {
        r.status = kMemoryVerifyInvalidArgument;
        return finish();
    }

    // V2 fix #30: read available memory (freeram), not only total, to avoid an
    // OOM-kill on devices with little free memory. freeram counts the pages
    // that are free right now (not buffers/cache) — what the app can take
    // without a reclaim. The philosophy: allocate the biggest buffer that is
    // safe, between 0.5 GiB and 8 GiB.
    struct sysinfo info {};
    if (sysinfo(&info) != 0) {
        r.status = kMemoryVerifySysinfoFailed;
        r.err_no = errno;
        return finish();
    }
    const size_t total_bytes = static_cast<size_t>(info.totalram) * info.mem_unit;
    const size_t free_bytes = static_cast<size_t>(info.freeram) * info.mem_unit;
    r.free_bytes = static_cast<int64_t>(free_bytes);

    // Not enough free memory: no buffer is attempted (before ANY mapping), and the free
    // memory is shipped raw so the lab can see how far below the line the device was.
    if (memory_verify_insufficient_free_memory(free_bytes)) {
        r.status = kMemoryVerifyInsufficientFreeMemory;
        return finish();
    }

    // target from total, then bounded by (free - 500 MiB) so the system keeps headroom.
    const size_t available_safe = memory_verify_available_safe(free_bytes);

    if (percent_ram < 1) percent_ram = 1;
    if (percent_ram > 80) percent_ram = 80;
    const size_t target_from_total = (total_bytes * static_cast<size_t>(percent_ram)) / 100u;
    size_t target = (target_from_total < available_safe) ? target_from_total : available_safe;

    // V2 fix #22: ceiling raised from 2 GiB to 8 GiB (flagship 12-16 GB devices
    // were tested on 12-17% of RAM, far below the requested 40%).
    constexpr size_t k8GiB = 8ull * 1024ull * 1024ull * 1024ull;
    if (target > k8GiB) target = k8GiB;

    // V2 fix #30: floor of 512 MiB so the kernels have a real effect. (Past the check above
    // available_safe >= 512 MiB, so this never raises the buffer beyond available_safe.)
    if (target < kMemoryVerifyFloorBytes) target = kMemoryVerifyFloorBytes;

    r.requested_bytes = static_cast<int64_t>(target);

    int err_no = 0;
    size_t mapped = target;
    uint8_t* buf = static_cast<uint8_t*>(map_anonymous(mapped, &err_no));
    if (buf == nullptr) {
        // One halved retry, as before; then a STATUS, not an empty result.
        mapped = target / 2;
        buf = static_cast<uint8_t*>(map_anonymous(mapped, &err_no));
        if (buf == nullptr) {
            r.status = kMemoryVerifyAllocationFailed;
            r.err_no = err_no;
            return finish();
        }
    }

    // Whole tiles only; the sub-tile remainder of the mapping is never touched.
    size_t tile_bytes = kMemoryVerifyTileBytes;
    if (mapped < tile_bytes) {
        tile_bytes = mapped & ~static_cast<size_t>(7);  // a multiple of 8 (the random pattern's word)
    }
    const size_t tiles_total = (tile_bytes == 0) ? 0 : mapped / tile_bytes;
    r.tile_bytes = static_cast<int64_t>(tile_bytes);
    r.tiles_total = static_cast<int64_t>(tiles_total);
    r.buffer_bytes = static_cast<int64_t>(tiles_total * tile_bytes);

    const StopControl stop{start_ns + time_cap_ms * 1'000'000LL, &native_verify_cancel_flag()};

    StopReason stopped_by = StopReason::kNone;
    for (size_t t = 0; t < tiles_total; ++t) {
        uint8_t* tile = buf + t * tile_bytes;
        const uint64_t first_word = static_cast<uint64_t>(t) * (tile_bytes / 8);
        const TileTestResult tr = memory_test_tile(tile, tile_bytes, first_word, stop);
        // Errors found are real even in a tile that did not finish.
        r.march_errors += tr.march_errors;
        r.walking_ones_errors += tr.walking_ones_errors;
        r.random_verify_errors += tr.random_verify_errors;
        if (!tr.completed) {
            stopped_by = tr.stopped_by;
            break;
        }
        ++r.tiles_completed;
        // Between tiles too: a stop requested after the last step of a tile.
        if (static_cast<size_t>(r.tiles_completed) < tiles_total) {
            const StopReason between = stop.check();
            if (between != StopReason::kNone) {
                stopped_by = between;
                break;
            }
        }
    }

    munmap(buf, mapped);

    if (static_cast<size_t>(r.tiles_completed) == tiles_total) {
        r.status = kMemoryVerifyRan;
    } else if (stopped_by == StopReason::kCancelled) {
        r.status = kMemoryVerifyPartialCancelled;
    } else {
        r.status = kMemoryVerifyPartialTimeCap;
    }
    return finish();
}

}  // namespace neuralcheck::stress
