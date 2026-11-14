# Battery Indicators (V6 / `fieldsVersion = 63`)

## Philosophy

We do NOT report State-of-Health (SoH) as a percentage. Single-point estimation from `chargeCounter / batteryLevel` is unreliable — production scans produced values like **102 %** on a Samsung Galaxy S24 Ultra (clearly impossible) and **100 %** on a 7-year-old Samsung S10 5G (also clearly wrong). Accurate SoH requires weeks of cycle tracking (the AccuBattery methodology), which is incompatible with an 8-minute one-shot scan.

Instead, the V6 footer reports **6 behavioral indicators** the scan can actually measure in its window, plus 3 contextual fields (design capacity, cycle count, and a derived discharge-vs-design ratio). The AI combines these in the prompt rather than evaluating any one in isolation.

This is a **truth-over-comfort** decision. A buyer reading the report sees what the device actually did during the scan, not a fabricated single-number score.

## Schema

Lives at `footer.batteryHealth` (a `Map<String, Any?>` so older Backends keep parsing it; new Backends pull the v63 keys explicitly).

| Field | Type | Meaning | Healthy range |
|---|---|---|---|
| `designCapacityMah` | `Int` | Manufacturer's declared capacity (sysfs / PowerProfile reflection). Stable for the device, no measurement noise. | Device-specific |
| `designCapacitySource` | `String` | Provenance: `"sysfs_design_capacity"` / `"powerprofile_reflection"` / `"unavailable"` | — |
| `cycleCount` | `Int?` | Charge cycles: `EXTRA_CYCLE_COUNT` of the battery broadcast (Android 14+, API 34), else the sysfs `cycle_count` file; one reader, `BatteryCycleCount.read`, shared by the footer and B-08 (fieldsVersion 68, T-02). A 0 ships as read. `null` when unavailable — then `cycleCountState` (`not_read:<sysfs reason>_and_<extra reason>`) says why. There is NO public `BATTERY_PROPERTY_CYCLE_COUNT` (javap on android.jar 33-36 lists `BATTERY_PROPERTY_*` 1 to 6 only). | < 500 ideal, < 800 acceptable, > 800 aged |
| `cycleCountSource` | `String` | Provenance, fieldsVersion 68 vocabulary: `"extra_api34"` / `"sysfs:<path>"` (today `sysfs:/sys/class/power_supply/battery/cycle_count`) / `"unavailable"`. Files written before 68 carry `"sysfs_cycle_count"` / `"battery_property_cycle_count_api_34"` / `"unavailable"` / `"no_battery_manager"`; the old second value named a property that is not public and never produced a count. | — |
| `dischargeRateUnderLoadMa` | `Double` | Average `\|battery_current_ua\|` across V2 phases 3-5 (CPU sustained + GPU sustained + combined peak), ÷ 1000 → mA | 1500-3500 mA typical for flagships under sustained load |
| `batteryTempStartC` | `Double` | Battery temperature at the first sample (mirrored from header for AI convenience) | < 35 °C recommended scan start |
| `batteryTempMaxC` | `Double` | Highest `battery_temp_c` observed during the scan | < 45 °C |
| `batteryTempRiseC` | `Double` | `batteryTempMaxC − batteryTempStartC`, clamped ≥ 0 | < 8 °C flagship, < 12 °C mid-range |
| `voltageStabilityMv` | `Double` | Population std-dev of `battery_voltage_mv` across V2 phases 3-5 | < 50 mV healthy, > 100 mV concerning |
| `levelDropPct` | `Int` | `batteryLevelStart − batteryLevelEnd`, clamped ≥ 0 | 3-10 % typical for an 8-min scan |
| `dischargeVsDesignRatioMah` | `Double` | `(levelDropPct / 100) × designCapacityMah` — estimated mAh consumed during scan if the battery still holds rated capacity | Should approximately match `dischargeRateUnderLoadMa × scanDurationMin × (load_fraction)` — large divergence flags capacity loss |
| `chargeCounterUah` | `Long?` | Raw `BATTERY_PROPERTY_CHARGE_COUNTER` reading at finalize. **NOT used for SoH** — kept for diagnostic / calibration consumers (B-02 fallback and ChannelDiscoveryProbe). | — |
| `energyCounterNwh` | `Long?` | Raw `BATTERY_PROPERTY_ENERGY_COUNTER` reading at finalize. **NOT used for SoH** — kept for diagnostic use. | — |

## Why these matter — AI interpretation

The Backend prompt is expected to interpret these **in combination**, not as standalone thresholds:

- **High `dischargeRateUnderLoadMa` + small `levelDropPct`** → battery is delivering current but its capacity may be smaller than the design figure suggests. Suspect capacity loss.
- **High `voltageStabilityMv` (std-dev)** → voltage sagging under load. Aged cells exhibit this because internal resistance has risen.
- **High `batteryTempRiseC`** → poor thermal performance. Possible aged or damaged cell, or a thermal pad / casing issue.
- **High `cycleCount`** (when available) → simply an old battery. Apple's threshold is 80 % rated capacity at 500 cycles; Android OEMs vary. Treat > 800 as a strong signal regardless of other indicators.
- **`dischargeVsDesignRatioMah` vs `dischargeRateUnderLoadMa`** — if the per-minute current integration over the scan duration is significantly higher than what the level drop implies, the battery is registering less actual capacity than the design figure.

No single threshold fails the battery — the AI weighs the combination. A device with 600 cycles but stable voltage, normal temperature rise, and a 4 % level drop is fine. A device with 200 cycles but voltage sagging by 150 mV and an 8 % drop in 8 minutes is suspicious.

## Why this replaced `sohOfficialPercent`

The previous footer carried `sohOfficialPercent` computed as `(chargeCounter / batteryLevel) × 100`. Concrete failures the field-trial pulled in:

| Device | `batteryLevel` | `chargeCounter (µAh)` | computed SoH | reality |
|---|---|---|---|---|
| Samsung Galaxy S24 Ultra (2024, new) | 91 % | 4,545,000 | **102 %** | impossible |
| Samsung Galaxy S10 5G (2019, well used) | 87 % | 4,200,000 | **100 %** | improbable |

Why the math breaks: `chargeCounter` is the **current charge level in µAh**, not the battery's full-charge capacity. Dividing by `batteryLevel` would only recover full-charge capacity IF the battery were perfectly linear and IF the registers were both calibrated against the same reference voltage. Neither holds on any production device.

A correct SoH measurement needs paired charge-counter readings at two different battery levels separated by enough time + load to integrate out noise (AccuBattery's method, which takes weeks). An 8-minute scan can't produce that data, so we stopped trying.

## Compatibility

| Footer `fieldsVersion` | Battery fields present |
|---|---|
| ≤ 62 (legacy) | `designCapacityMah`, `currentCapacityMah`, `cycleCount`, `sohOfficialPercent`, `chargeCounterUah`, `energyCounterNwh` |
| ≥ 63 (this) | `designCapacityMah`, `cycleCount`, `dischargeRateUnderLoadMa`, `batteryTempStartC/MaxC/RiseC`, `voltageStabilityMv`, `levelDropPct`, `dischargeVsDesignRatioMah`, `chargeCounterUah`, `energyCounterNwh` |

The Backend prompt has to handle both shapes if any v62 scans are still being processed. The cleanest contract:

```
batteryHealth.sohOfficialPercent != null  →  scan is v62 (legacy)
batteryHealth.dischargeRateUnderLoadMa != null → scan is v63
```

Both fields never coexist in the same footer.

## Implementation

| Concern | File |
|---|---|
| Pure computation of the 10 fields | [`app/src/main/java/net/neuralcheck/hardware/battery/BatteryIndicators.kt`](../../app/src/main/java/net/neuralcheck/hardware/battery/BatteryIndicators.kt) |
| Cycle-count reading (sysfs cascade + API 34) | [`BatteryReader.kt`](../../app/src/main/java/net/neuralcheck/service/collector/BatteryReader.kt) (lines 547-567) |
| Wire-up to footer map | [`StressTestService.kt`](../../app/src/main/java/net/neuralcheck/service/stress/StressTestService.kt) (around line 1330) |
| B-02 functional test (design capacity only) | [`BatteryTests.kt`](../../app/src/main/java/net/neuralcheck/hardware/battery/BatteryTests.kt) (lines ~270-330) |
| Schema version constant | [`app/build.gradle.kts`](../../app/build.gradle.kts) (search for `fieldsVersion`) |
| Unit tests | [`BatteryIndicatorsTest.kt`](../../app/src/test/java/net/neuralcheck/hardware/battery/BatteryIndicatorsTest.kt) |

## Out of scope for fieldsVersion 63

- **Per-cycle SoH tracking**: requires a long-running background service that integrates current over multiple charge cycles. Future work, not on the 8-minute scan timeline.
- **Internal resistance estimation**: theoretically derivable from voltage/current pairs at different loads, but requires the ability to step the load — adds scan time + power budget. Future work.
- **AC adapter detection**: present-but-not-tracked. If implemented, would let the AI flag "scan was charged mid-run, ignore levelDropPct".
- **Battery model database**: matching `designCapacityMah` against a curated list of (Build.MODEL → expected capacity) entries exists separately in `KnownBatteryCapacities`. Not part of this schema.
