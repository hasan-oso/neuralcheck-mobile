#include "stress/storage_verify.h"

#include <fcntl.h>
#include <unistd.h>

#include <cerrno>
#include <cstdlib>
#include <cstring>

#include "stress/memory_test.h"  // random_pattern_word: the one definition of the mixing function
#include "stress/native_common.h"

namespace neuralcheck::stress {

namespace {

constexpr size_t kPagesPerChunk = kStorageChunkBytes / kStoragePageBytes;  // 256
constexpr size_t kReadUnitBytes = 4u << 20;                                // 4 MiB per read

// The 16 bytes at the start of the 4 KiB page that begins at absolute byte
// `offset`: the offset itself (little-endian) and a hash of it.
void expected_stamp(int64_t offset, uint8_t out[kStorageStampBytes]) noexcept {
    const uint64_t off = static_cast<uint64_t>(offset);
    const uint64_t h = random_pattern_word(off);
    std::memcpy(out, &off, 8);
    std::memcpy(out + 8, &h, 8);
}

// One page of the base pattern, built once: `j & 0xFF`.
const uint8_t* base_page() noexcept {
    static uint8_t page[kStoragePageBytes];
    static const bool ready = [] {
        for (size_t j = 0; j < kStoragePageBytes; ++j) page[j] = static_cast<uint8_t>(j & 0xFF);
        return true;
    }();
    (void)ready;
    return page;
}

// Does the 1 MiB block at `block` equal chunk `chunk_index` of the pattern?
bool block_matches(const uint8_t* block, int64_t chunk_index) noexcept {
    const uint8_t* base = base_page();
    for (size_t p = 0; p < kPagesPerChunk; ++p) {
        const uint8_t* page = block + p * kStoragePageBytes;
        uint8_t stamp[kStorageStampBytes];
        expected_stamp(chunk_index * static_cast<int64_t>(kStorageChunkBytes) +
                           static_cast<int64_t>(p * kStoragePageBytes),
                       stamp);
        if (std::memcmp(page, stamp, kStorageStampBytes) != 0) return false;
        if (std::memcmp(page + kStorageStampBytes, base + kStorageStampBytes,
                        kStoragePageBytes - kStorageStampBytes) != 0) {
            return false;
        }
    }
    return true;
}

// pread until `want` bytes arrive, EOF, or an error. Returns bytes read, or -1
// with errno set. EINTR is retried here and is never an error.
ssize_t read_fully(int fd, uint8_t* buf, size_t want, int64_t offset) noexcept {
    size_t got = 0;
    while (got < want) {
        const ssize_t n = pread(fd, buf + got, want - got, static_cast<off_t>(offset + static_cast<int64_t>(got)));
        if (n < 0) {
            if (errno == EINTR) continue;
            return -1;
        }
        if (n == 0) break;  // EOF
        got += static_cast<size_t>(n);
    }
    return static_cast<ssize_t>(got);
}

}  // namespace

void storage_fill_base_chunk(uint8_t* chunk) noexcept {
    for (size_t j = 0; j < kStorageChunkBytes; ++j) chunk[j] = static_cast<uint8_t>(j & 0xFF);
}

void storage_stamp_chunk(uint8_t* chunk, int64_t chunk_index) noexcept {
    const int64_t chunk_base = chunk_index * static_cast<int64_t>(kStorageChunkBytes);
    for (size_t p = 0; p < kPagesPerChunk; ++p) {
        expected_stamp(chunk_base + static_cast<int64_t>(p * kStoragePageBytes),
                       chunk + p * kStoragePageBytes);
    }
}

StorageVerifyResult run_storage_verify(const char* path,
                                       int64_t start_offset,
                                       int64_t length,
                                       int64_t budget_ms) noexcept {
    StorageVerifyResult r{};
    const int64_t start_ns = monotonic_ns();
    r.bytes_planned = length;
    auto finish = [&]() -> StorageVerifyResult {
        r.elapsed_ms = elapsed_ms_since(start_ns);
        return r;
    };

    const int64_t mib = static_cast<int64_t>(kStorageChunkBytes);
    if (path == nullptr || start_offset < 0 || length <= 0 || budget_ms <= 0 ||
        start_offset % mib != 0 || length % mib != 0) {
        r.status = kStorageVerifyInvalidArgument;
        return finish();
    }

    // O_DIRECT first. A refusal is not a failure of the storage: fall back to a
    // buffered read, and say so.
    r.method = kStorageMethodODirect;
    int fd = open(path, O_RDONLY | O_CLOEXEC | O_DIRECT);
    if (fd < 0) {
        r.method = kStorageMethodDontNeedFallback;
        r.fallback_stage = kStorageFallbackOpen;
        r.fallback_errno = errno;
        fd = open(path, O_RDONLY | O_CLOEXEC);
        if (fd < 0) {
            r.status = kStorageVerifyOpenFailed;
            r.err_no = errno;
            return finish();
        }
    }

    // Drop the cached pages of the range on BOTH paths. After the writer's fsync
    // they are clean, so DONTNEED can evict them; with O_DIRECT it makes sure a
    // filesystem that quietly serves the read from the cache (file-based
    // encryption without inline crypto) still has to go to the device.
    posix_fadvise(fd, static_cast<off_t>(start_offset), static_cast<off_t>(length), POSIX_FADV_DONTNEED);

    void* raw = nullptr;
    const int alloc_rc = posix_memalign(&raw, kStoragePageBytes, kReadUnitBytes);
    if (alloc_rc != 0 || raw == nullptr) {
        r.status = kStorageVerifyAllocationFailed;
        r.err_no = alloc_rc;  // posix_memalign returns the error number
        close(fd);
        return finish();
    }
    uint8_t* buf = static_cast<uint8_t*>(raw);

    const StopControl stop{start_ns + budget_ms * 1'000'000LL, &native_verify_cancel_flag()};
    int64_t offset = start_offset;
    const int64_t end = start_offset + length;
    r.status = kStorageVerifyRan;

    while (offset < end) {
        const StopReason why = stop.check();
        if (why != StopReason::kNone) {
            r.status = (why == StopReason::kCancelled) ? kStorageVerifyPartialCancelled
                                                       : kStorageVerifyPartialTimeCap;
            break;
        }
        const size_t want = static_cast<size_t>((end - offset) < static_cast<int64_t>(kReadUnitBytes)
                                                    ? (end - offset)
                                                    : static_cast<int64_t>(kReadUnitBytes));
        const int64_t blocks = static_cast<int64_t>(want / kStorageChunkBytes);

        ssize_t got = read_fully(fd, buf, want, offset);
        if (got < 0) {
            const int e = errno;
            if (r.method == kStorageMethodODirect && r.bytes_compared == 0) {
                // The first O_DIRECT read was refused (e.g. EINVAL: a buffer/offset
                // alignment this filesystem wants stricter). Reopen buffered, drop the
                // cache, retry THIS unit.
                close(fd);
                fd = open(path, O_RDONLY | O_CLOEXEC);
                if (fd < 0) {
                    r.status = kStorageVerifyOpenFailed;
                    r.err_no = errno;
                    break;
                }
                r.method = kStorageMethodDontNeedFallback;
                r.fallback_stage = kStorageFallbackRead;
                r.fallback_errno = e;
                posix_fadvise(fd, static_cast<off_t>(start_offset), static_cast<off_t>(length), POSIX_FADV_DONTNEED);
                continue;
            }
            if (e == EIO) {
                // The device could not read it back: that IS the finding. Count the
                // unit's blocks as errors and go on to the next unit.
                r.blocks_mismatched += blocks;
                r.io_error_blocks += blocks;
                r.bytes_compared += static_cast<int64_t>(want);
                offset += static_cast<int64_t>(want);
                continue;
            }
            r.status = kStorageVerifyReadFailed;
            r.err_no = e;
            break;
        }

        // Compare block by block. Past EOF the blocks are MISSING data: errors.
        for (int64_t b = 0; b < blocks; ++b) {
            const int64_t block_offset = offset + b * mib;
            const size_t block_start = static_cast<size_t>(b) * kStorageChunkBytes;
            if (static_cast<size_t>(got) < block_start + kStorageChunkBytes) {
                ++r.blocks_mismatched;
                ++r.io_error_blocks;
            } else if (!block_matches(buf + block_start, block_offset / mib)) {
                ++r.blocks_mismatched;
            }
        }
        r.bytes_compared += static_cast<int64_t>(want);
        offset += static_cast<int64_t>(want);
    }

    free(buf);
    if (fd >= 0) close(fd);
    return finish();
}

}  // namespace neuralcheck::stress
