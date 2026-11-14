#include "stress/thermal_reader.h"

#include <cctype>
#include <cstdio>
#include <cstdlib>
#include <cstring>

namespace neuralcheck::stress {

namespace {

// Maximum thermal_zone index we'll probe. AOSP devices in the field
// have ranged from ~10 zones (older Snapdragon) to ~80 (Pixel 7+).
// 100 is a safe upper bound that still bounds the syscall budget.
constexpr int kMaxThermalZones = 100;

// Reads a file's whole content (up to buf_size-1 bytes) into buf and
// null-terminates. Returns the number of bytes read (excluding the
// terminator). Returns 0 on any error — the calling code treats 0 as
// "skip this zone" rather than aborting the whole sweep.
size_t read_file_to_string(const char* path, char* buf, size_t buf_size) noexcept {
    FILE* f = std::fopen(path, "rb");
    if (!f) return 0;
    const size_t n = std::fread(buf, 1, buf_size - 1, f);
    std::fclose(f);
    buf[n] = '\0';
    // Trim trailing whitespace / newline — /sys files conventionally
    // end with '\n' which would interfere with substring matching.
    size_t trimmed = n;
    while (trimmed > 0 && std::isspace(static_cast<unsigned char>(buf[trimmed - 1]))) {
        buf[--trimmed] = '\0';
    }
    return trimmed;
}

// Lowercases an ASCII string in place. /sys type files are ASCII, no
// UTF-8 multibyte, so per-byte tolower is safe.
void lower_in_place(char* s) noexcept {
    for (; *s; ++s) {
        *s = static_cast<char>(std::tolower(static_cast<unsigned char>(*s)));
    }
}

// Substring containment check, both args must be lowercase. Returns
// true iff `needle` appears anywhere in `haystack`.
bool contains(const char* haystack, const char* needle) noexcept {
    return std::strstr(haystack, needle) != nullptr;
}

// Classify a lowercased type-string into one of kThermalCategory or -1
// (unknown / ignored). Order of checks matters: "skin" is checked
// before "skin-therm" wouldn't matter (substring), but "tcpu" must be
// caught by the CPU branch even though it doesn't contain plain "cpu";
// likewise "tgpu" / "tsens_tz_sensor*" gets a defensive fallthrough.
int classify_type(const char* type_lower) noexcept {
    // Skin first — distinct from cpu/gpu and easy to confuse otherwise.
    if (contains(type_lower, "skin") || contains(type_lower, "case") ||
        contains(type_lower, "shell")) {
        return kThermalSkin;
    }
    if (contains(type_lower, "battery") || contains(type_lower, "batt")) {
        return kThermalBattery;
    }
    if (contains(type_lower, "cpu") || contains(type_lower, "tcpu") ||
        contains(type_lower, "soc") || contains(type_lower, "ap_thermal")) {
        return kThermalCpu;
    }
    if (contains(type_lower, "gpu") || contains(type_lower, "tgpu")) {
        return kThermalGpu;
    }
    return -1;
}

}  // namespace

void read_thermal_zones(float out[kThermalCount]) noexcept {
    for (int i = 0; i < kThermalCount; ++i) {
        out[i] = kThermalNotFound;
    }

    char path[64];
    char type_buf[64];
    char temp_buf[32];

    for (int zone = 0; zone < kMaxThermalZones; ++zone) {
        // Stop at first non-existent zone — kernels number zones
        // sequentially starting from 0 with no gaps in current AOSP.
        std::snprintf(path, sizeof(path),
                      "/sys/class/thermal/thermal_zone%d/type", zone);
        if (read_file_to_string(path, type_buf, sizeof(type_buf)) == 0) {
            // First missing zone → assume sequence ended. Avoids
            // 100 fopen/ENOENT spam on devices with only ~12 zones.
            break;
        }
        lower_in_place(type_buf);
        const int cat = classify_type(type_buf);
        if (cat < 0) continue;

        // First-match-wins per category — don't overwrite a good
        // reading with a noisier alias zone.
        if (out[cat] != kThermalNotFound) continue;

        std::snprintf(path, sizeof(path),
                      "/sys/class/thermal/thermal_zone%d/temp", zone);
        if (read_file_to_string(path, temp_buf, sizeof(temp_buf)) == 0) continue;

        // /sys/class/thermal/thermal_zone*/temp contains the temp in
        // millidegrees Celsius (e.g. "32500" = 32.5°C). atoi handles
        // leading whitespace and stops at the first non-digit.
        const int millideg = std::atoi(temp_buf);
        if (millideg <= 0 || millideg > 200000) {
            // Bogus reading (negative, zero, or >200°C). Some devices
            // expose disabled-sensor zones that return -40000 or 0;
            // treat both as missing rather than reporting nonsense.
            continue;
        }
        out[cat] = static_cast<float>(millideg) / 1000.0f;
    }
}

}  // namespace neuralcheck::stress
