// NeuralCheck V2 — storage verification producer (fieldsVersion 68, T-16).
//
// STORAGE_STRESS writes a file; this reads the LAST 256 MiB of it back and
// compares every 1 MiB block with the pattern the writer put there. The
// pattern is a pure function of the byte OFFSET, so the verifier needs no copy
// of what was written and a misplaced or stale block is caught (a pattern
// that repeats every 1 MiB cannot tell block 7 from block 8).
//
// The pattern, per 1 MiB chunk with absolute index k:
//   - every byte is `j & 0xFF` (j = offset inside the chunk) EXCEPT
//   - the first 16 bytes of each 4 KiB page: an 8-byte little-endian absolute
//     byte offset of that page, then 8 bytes of splitmix64(offset).
// The writer fills the chunk once and re-stamps it per chunk (256 x 16 bytes
// per MiB: noise next to the write itself); the verifier recomputes the
// stamps. Both live HERE, so the two sides cannot drift apart.
//
// The read bypasses the page cache where it can: open(O_DIRECT) on an aligned
// buffer, the cached range dropped first with posix_fadvise(DONTNEED). A device
// or filesystem that refuses O_DIRECT is NOT a failure: the read falls back to
// a buffered read after DONTNEED, and the result says which method actually ran
// and why. (Android's file-based encryption can accept the O_DIRECT open and
// still serve the read from the cache; that case cannot be told from here and
// is why DONTNEED is applied on BOTH paths.)

#pragma once

#include <cstddef>
#include <cstdint>

namespace neuralcheck::stress {

constexpr size_t kStorageChunkBytes = 1u << 20;   // the writer's unit: 1 MiB
constexpr size_t kStoragePageBytes = 4096;
constexpr size_t kStorageStampBytes = 16;

// Fills a chunk with the base pattern (`j & 0xFF`). Call once; then stamp.
void storage_fill_base_chunk(uint8_t* chunk) noexcept;

// Writes the 256 page stamps of chunk number `chunk_index` into `chunk`
// (kStorageChunkBytes long, already holding the base pattern).
void storage_stamp_chunk(uint8_t* chunk, int64_t chunk_index) noexcept;

// Status codes of StorageVerifyResult::status. Their order is part of the JNI
// contract (mirrored in Kotlin: StorageVerifyLayout) — append, never reorder.
enum StorageVerifyStatus : int32_t {
    kStorageVerifyRan = 0,              // every planned byte was read and compared
    kStorageVerifyPartialTimeCap = 1,   // the time budget ended it
    kStorageVerifyPartialCancelled = 2, // the caller cancelled it
    kStorageVerifyOpenFailed = 3,       // neither O_DIRECT nor a buffered open worked: err_no says why
    kStorageVerifyReadFailed = 4,       // a read failed for a reason other than EIO: err_no says why
    kStorageVerifyAllocationFailed = 5, // the aligned read buffer: err_no says why
    kStorageVerifyInvalidArgument = 6,  // offset/length not whole MiB, or <= 0
};

enum StorageVerifyMethod : int32_t {
    kStorageMethodODirect = 0,
    kStorageMethodDontNeedFallback = 1,
};

// Where the fallback happened, when method == kStorageMethodDontNeedFallback.
enum StorageFallbackStage : int32_t {
    kStorageFallbackNone = 0,
    kStorageFallbackOpen = 1,   // open(O_DIRECT) failed
    kStorageFallbackRead = 2,   // the first O_DIRECT read failed (e.g. EINVAL: alignment)
};

struct StorageVerifyResult {
    int32_t status;
    int32_t method;
    int32_t fallback_stage;
    int32_t fallback_errno;     // errno that forced the fallback, else 0
    int32_t err_no;             // errno of a failure status, else 0
    int64_t bytes_planned;
    int64_t bytes_compared;     // includes blocks that failed with EIO or were missing past EOF (they are errors)
    int64_t blocks_mismatched;  // 1 MiB blocks that differ, could not be read (EIO), or were missing
    int64_t io_error_blocks;    // the EIO / missing subset of blocks_mismatched
    int64_t elapsed_ms;
};

// Reads [start_offset, start_offset + length) of `path` and compares it. Both
// must be whole MiB. `budget_ms` is a DURATION: no new read starts after it.
StorageVerifyResult run_storage_verify(const char* path,
                                       int64_t start_offset,
                                       int64_t length,
                                       int64_t budget_ms) noexcept;

}  // namespace neuralcheck::stress
