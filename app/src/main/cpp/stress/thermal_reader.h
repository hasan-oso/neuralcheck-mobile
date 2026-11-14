// NeuralCheck V2 — Thermal zone reader (sub-phase 1f).
//
// Parses /sys/class/thermal/thermal_zone*/type + /temp and classifies
// each zone by its type-string keyword. Returns the first temperature
// found in each of four categories (skin, battery, cpu, gpu) so the
// caller can read the four headline thermals without enumerating
// vendor-specific zone names every time.
//
// Vendor type-string variation (per master plan §8 tip #6):
//   Samsung: "skin_thermal", "battery", "skin_therm"
//   Pixel:   "skin_temp", "tsens_tz_sensor1" (cpu), "tsens_tz_sensor2"
//   Xiaomi:  "skin-therm", "soc_thermal"
//   Generic: "cpu0-thermal", "gpu-thermal", "battery"
//
// Matching is substring + lowercase, NOT exact equality. The first
// match wins per category — vendors sometimes expose the same physical
// sensor under multiple zones (with different type strings) and we
// don't want to overwrite a good reading with a noisier alias.

#pragma once

#include <cstddef>

namespace neuralcheck::stress {

// Categories returned in the float array, in this fixed order.
enum ThermalCategory {
    kThermalSkin    = 0,
    kThermalBattery = 1,
    kThermalCpu     = 2,
    kThermalGpu     = 3,
    kThermalCount   = 4,
};

// Sentinel returned when no zone matched the category. Distinct from
// any plausible temperature reading (-1°C is below any device-survival
// floor) so callers can `if (t < 0) skip;` without knowing the actual
// sentinel value.
constexpr float kThermalNotFound = -1.0f;

// Reads /sys/class/thermal/thermal_zone* and writes the first matching
// temperature in °C into out[kThermalSkin..kThermalGpu]. Slots that
// found no match stay at kThermalNotFound. Caller provides `out` of
// length kThermalCount.
void read_thermal_zones(float out[kThermalCount]) noexcept;

}  // namespace neuralcheck::stress
