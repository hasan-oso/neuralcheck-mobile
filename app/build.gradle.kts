import java.util.Properties
import java.io.FileInputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
    id("com.google.gms.google-services")
}

// Phase 1.13 keystore rotation: signing credentials live in keystore.properties
// at the repo root (gitignored). Loaded into a Properties object once, then
// consumed by the signingConfigs block below. The .exists() guard keeps Gradle
// sync working in CI environments that don't have the secret file mounted.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

// ── Release build guards (order 2026-09-23، القرار ٧٣، البند ١) ──
//
// Two independent guards on the launchRelease build, modeled on the
// neuralcheck-backend deploy hook (commit d4b151e pattern, relayed by the
// coordinating session — this repo cannot read that one): (a) reject a
// build whose TRACKED tree is dirty and stamp the exact commit into the
// APK; (b) after R8 has run, reject if it gave any DeviceProfileKey field
// a name other than the one the committed reference lists
// (app/guard/deviceProfileKeyMapping.expected.properties — until 2026-10-09
// generated from a real R8 mapping.txt as a snapshot of R8's letters a..f;
// since then it states the SOURCE names, each field against itself, and a
// unit test ties it to DeviceProfileKey's fields, rule 24). Both guards run at TASK
// EXECUTION time only (inside `doLast`), never at Gradle configuration
// time — running the git/file checks merely by evaluating this script
// would break `./gradlew tasks`, IDE sync, and every debug build, none of
// which this order's guards are meant to touch.
//
// execGit: minimal helper, no external process-exec plugin dependency —
// this whole guard mechanism is meant to work on any machine that already
// has git + a JDK, nothing else.
fun execGit(vararg args: String): String {
    val process = ProcessBuilder(listOf("git") + args)
        .directory(rootDir)
        .redirectErrorStream(true)
        .start()
    val output = process.inputStream.bufferedReader().use { it.readText() }
    val exitCode = process.waitFor()
    if (exitCode != 0) {
        throw GradleException("execGit(${args.joinToString(" ")}) failed (exit $exitCode):\n$output")
    }
    return output
}

// Read-only, configuration-time — never fails the build by itself (a
// missing/broken git is a real environment problem outside this guard's
// scope, not something this stamp should silently paper over; if execGit
// throws here, Gradle configuration fails loudly, which is the correct
// behavior for a value that gets baked into every APK, debug included).
val gitCommitSha: String = execGit("rev-parse", "HEAD").trim()

android {
    namespace = "net.neuralcheck"
    compileSdk = 35

    // Pin the NDK version explicitly so a developer with a different
    // default-installed NDK doesn't silently rebuild libstress.so against
    // a different toolchain (different libc++ ABI, different intrinsic
    // headers). 27.1 is the first release fully validated against AGP
    // 8.2 + compute shaders ES 3.1 per Q4.3 in IMPLEMENTATION_NOTES_V2.md.
    ndkVersion = "27.1.12297006"

    defaultConfig {
        minSdk = 28
        targetSdk = 35
        // Order 2026-09-24 (القرار ٧٥، البند ٣): both the 2026-06-09 and
        // 2026-09-20 reference builds shipped versionCode 10 — the number
        // alone couldn't distinguish them (flagged, not decided, in the
        // 2026-09-23 report).
        //
        // Order 2026-09-26 (القرار ١٠٢ البند ٥; history — the current values are
        // set below this block): versionName updated to
        // "1.0.9" — "1.0.8-v6beta" was false advertising on a V7 build; it
        // is also baked into every calibration-table row via `appVersion`
        // (StressTestService.kt, ExportDataUseCase.kt, SettingsScreen.kt,
        // ProfileScreen.kt — verified: none of the 5 readers of
        // BuildConfig.VERSION_NAME branch on its text, `git grep -n
        // "PackageInfo\|versionNameSuffix"` is empty, and no test asserts
        // "1.0.8"/"1.0.8-v6beta" as a build value — the contract fixtures'
        // own `"appVersion": "1.0.8-v6beta"` are recorded field data, not
        // touched). versionCode stayed 11 — untouched by that order.
        //
        // Order 2026-10-18 (station 2 build prep; the decision is
        // plan/PLAN-STATION2-2026-10-15.md:87 — 1.0.12 was the number the approved
        // table gave this build, 1.0.14 was used in device check 03, "so the next
        // build is 1.0.15"): versionCode 16 -> 17, versionName "1.0.14" -> "1.0.15".
        // `git grep` found no reader that pins the old value: the 5 sites of
        // BuildConfig.VERSION_NAME are pinned by site, not by value
        // (BuildStampSitesTest), and the golden request samples carry the literal
        // GENERATED_APP_VERSION = "1.0.11" (AppRequestPayloadGolden.kt), decoupled
        // from the build on purpose — so this bump is not a golden change.
        // fieldsVersion is a separate number (below) and is untouched by this order.
        versionCode = 17
        versionName = "1.0.15"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
        // Order 2026-09-23 (القرار ٧٣، البند ١أ) — "يسجّل رقم الالتزام داخل
        // الـAPK". Present on every variant (debug included, harmless); only
        // the RELEASE build's tree-cleanliness rejection is guard-gated —
        // see checkReleaseTreeClean below.
        buildConfigField("String", "GIT_COMMIT_SHA", "\"$gitCommitSha\"")

        // arm64-v8a only. Per Q4.1 in IMPLEMENTATION_NOTES_V2.md: minSdk 28
        // (Android 9.0) means every supported device is 64-bit, so shipping
        // armeabi-v7a would add ~1.5 MB to the APK to support a market
        // segment that doesn't exist anymore. NEON is mandatory on
        // arm64-v8a, so we never need __ARM_NEON runtime checks either.
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    // Point AGP at the native build root. Sources, target lists, and
    // compile options all live in the CMakeLists itself — keeping this
    // block minimal so any C++ change happens in one place.
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    // Two flavors share the same codebase. Differences are driven entirely
    // via BuildConfig.* flags and the resValue-generated app_name.
    //   - launch:      commercial build, AI scoring, peer input hidden,
    //                  maintenance scan hidden, subscription gated.
    //   - datacollect: technician/internal build, RuleBased scoring, peer
    //                  input + maintenance scan shown, no subscription.
    flavorDimensions += "version"
    productFlavors {
        // FIELDS_VERSION stamps every TimeSeriesFooter and lets the AI / backend
        // verify that the device is running the build it thinks it is. Bump on
        // every footer schema change. Same value across both flavors so a
        // datacollect device baseline carries the same stamp as a launch
        // production device.
        //   56 → V2 fix #56 baseline (added stressEffectiveness, dataQuality,
        //        batteryHealth, knownCapacityClassification)
        //   57 → V2 fix #56 (2026-05-04): Throwable-safe finalize + footer
        //        backup file + footerDiagnostics + serializeNulls — shipped
        //        to diagnose Xiaomi MT6991 footer truncation.
        //   58 → V2 fix #56-3 (2026-05-04): per-cluster saturation criterion.
        //        StressEffectiveness gains phase3CpuFreqRatioBig/Mid/Little
        //        + phase3CpuUsageAvgBig/Mid/Little + perCpuUsageAvailable.
        //        Sampler captures per-CPU /proc/stat usage during phase 3
        //        and per-CPU cpuinfo_max_freq topology at scan start.
        //        Deprecated aliases (phase3CpuFreqRatio, phase3CpuUsageAvg)
        //        kept for one release; removal target fieldsVersion 60.
        //   59 → V2 fix #56-3 Commit 2+3 (2026-05-05): all-8-core workload
        //        with per-cluster affinity pinning + governor-capping early
        //        exit. NDK production_stress runs heavy NEON workload on
        //        big+mid AND a light branchy-integer workload on
        //        (little_size - 1) cores; one little core reserved for
        //        sampler thread (R1 guard). VerificationSummary gains
        //        workersPinnedLittle + per-cluster pin failure counts.
        //        StressEffectiveness gains governorCappingDetected; when
        //        true, V2 service skips phases 4/5/6 (~3 minute total
        //        scan duration). Pre-flight battery floor 30% → 40% to
        //        cover the higher 8-core drain envelope.
        //   60 → V2 fix #56-4 Commit 4 (2026-05-05): multi-axis evidence-based
        //        saturation criterion. Replaces the unachievable 0.85 mean
        //        freq gate with C1-C4 conditions (peak power, sustained
        //        compute via 3 OR-paths, verification integrity, peak freq
        //        capability). satConditionsMet ≥ 3 of 4 → PASS; ==3 sets
        //        explicit satCaveat. New diagnostic fields
        //        peakPackagePowerMwPhase3/5, peakBigFreqKhzAnywhere,
        //        freqDropFromBurstToSustainedPct. governorCappingDetected
        //        no longer force-fails saturation — multi-axis criterion
        //        determines verdict. Deprecated phase3CpuFreqRatio* removal
        //        target moved 59 → 61 (kept one more release).
        //   61 → V5 schema cleanup: v[] reduced from 22 to 11 universal
        //        channels. Blocked thermals + accel/gyro moved to footer.
        //        New footer fields: channelDiscovery,
        //        thermalSensorAvailability, scanStillness. Schema break —
        //        v4 .ts.json.gz files are not parser-compatible.
        //   62 → Phase 1.8 calibration block in footer (additive).
        //   63 → 2026-05-18: BREAKING battery schema change. Dropped
        //        sohOfficialPercent / currentCapacityMah /
        //        currentCapacitySource from footer.batteryHealth because
        //        the single-point chargeCounter/batteryLevel estimator
        //        produced impossible values (102 % on S24 Ultra, 100 % on
        //        2019 S10 5G). Replaced with 6 behavioral indicators
        //        measured during the actual scan: cycleCount (+source),
        //        dischargeRateUnderLoadMa, batteryTempStartC,
        //        batteryTempMaxC, batteryTempRiseC, voltageStabilityMv,
        //        levelDropPct, dischargeVsDesignRatioMah. See
        //        docs/architecture/BATTERY_INDICATORS.md for the public
        //        schema and the AI guidance the backend prompt uses to
        //        combine the signals. Backend prompt update is required
        //        BEFORE this APK ships — v62 footers continue to carry
        //        the legacy fields; v63 footers do not.
        //   64 → 2026-06-04 (Phase 1.15, ADDITIVE — no backend prompt change
        //        required): VerificationSummary gains aiDelegateInitError
        //        (the captured TFLite interpreter-construction failure when
        //        the CPU delegate falls to STUB) and cpuStressFallbackUsed
        //        (true when the synthetic NEON kernels ran because the AI
        //        delegate couldn't load). Fixes the Honor X9d zero-CPU-load
        //        scan: phases 3/5 now fall back to NEON on STUB, and
        //        StressEffectivenessCalculator invalidates the saturation
        //        verdict ("stress_not_applied") when no load was applied.
        //        Backend may ignore both fields safely; v63 parsers stay
        //        compatible (additive only).
        //   65 → 2026-09-10 (measurement integrity, ADDITIVE for every field —
        //        no backend prompt change required; v64 parsers stay
        //        compatible). Field-by-field:
        //        • footer.unmeasuredDefaults (NEW, List<String>?): names of
        //          footer fields whose values are compile-time constants,
        //          not measurements — always contains deviceTierMeasured +
        //          intensityFactor (hardcoded since V2 fix #30), plus
        //          tempRiseRateInProbe on the V1 path. Additive.
        //        • footer.expectedSamples (NEW, Int?): nominal sample count
        //          Σ(phase duration × rate) = 1605 for V2; deficit vs
        //          totalSamples on a complete scan = silent sampler loss.
        //          Additive.
        //        • footer.samplesSha256 (NEW, String?): SHA-256 of the
        //          canonical samples serialisation in the .ts.json.gz;
        //          a .sha256 sidecar next to the file carries the file-level
        //          hash. Additive.
        //        • footer.flushFailureCount (NEW, Int?): failed buffered
        //          flushes (writer retried; finalize de-duplicates).
        //          Additive.
        //        • dataQuality.batteryScalingLockAtSample /
        //          batteryInvalidCurrentReadings /
        //          batteryVoltageUvScaledCount (NEW): calibration-lock
        //          sample index (-1 = never), filtered Int.MIN_VALUE
        //          CURRENT_NOW readings, applied µV→mV conversions.
        //          Additive.
        //        • throttlingEvents[].applied (NEW, Boolean): true only when
        //          the event changed the workload (level-3 terminate that
        //          fired); level-1/2 intensity reductions are recorded-only.
        //          Additive.
        //        Behavioural (no schema shape change): Int.MIN_VALUE
        //        CURRENT_NOW filtered inside snapshot() (was register()-only)
        //        and kept out of the scaling-lock evidence counters;
        //        EXTRA_VOLTAGE µV auto-detection (raw > 100000 → ÷1000);
        //        v[7] computed from the SAME read as v[2]/v[3] (one
        //        CURRENT_NOW read per tick — v[7] now always reproducible
        //        as |v[2]×v[3]|/1e6); finalize() writes GZIP to .tmp then
        //        atomically renames, and returns NULL on failure instead of
        //        a path to a corrupt file (timeSeriesFilePath stays null);
        //        finalize merges unflushed in-memory samples and
        //        de-duplicates flush-retry lines; the .footer.json backup is
        //        now actually READ as a fallback when the main file is
        //        missing/corrupt.
        //   66 → 2026-09-14 (measurement corrections from the Honor MTN-NX1 /
        //        Samsung S10 5G two-device comparison). ⚠ THREE SEMANTIC
        //        CHANGES — scans ≥66 are NOT directly comparable with ≤65 in
        //        v[10], v[9] and footer.maxBatteryTemp. Field-by-field:
        //        SEMANTIC (value meaning changed, shape unchanged):
        //        • v[10] gpu_fps: ≥1 s sliding-window rate (last 4 sampler
        //          queries) instead of per-query rate. Removes the 30-frame
        //          fence aliasing that produced 0/120/0/120 on ~60 fps GPUs
        //          (Honor MTN-NX1) — ≤65 values in the 60–120 fps band are
        //          artefacts; fast GPUs (S10 5G ~1245) unchanged.
        //        • v[9] memory_bandwidth_mbps: LIVE rolling MB/s published
        //          ~1 Hz by the memory workload. ≤65 files carry 0 in ≥85 %
        //          of phase-2 samples (in-memory backfill never reached disk).
        //        • footer.maxBatteryTemp: now the true max of v[1] across the
        //          scan (was the END-of-scan snapshot despite the name;
        //          equals batteryHealth.batteryTempMaxC).
        //        • dataQuality.procStatAvailable: now derived from the
        //          reader's provenance (true iff ≥1 sample came from
        //          /proc/stat); ≤65 computed "any v[6] > 0" and was true
        //          even when every sample was the process-level fallback.
        //        ADDITIVE (new fields, defaults on ≤65 parsers):
        //        • dataQuality.cpuUsageSource ("procstat"|"process_fallback"|
        //          "unknown") + dataQuality.cpuUsageFallbackFromSample (Int,
        //          -1 = never): v[6] provenance without touching v[].
        //        • verificationResults.gpuFenceTimeoutCount (Int): 30-frame
        //          fence waits that timed out in phases 4+5; >0 ⇒ v[10] may
        //          overstate (issued-frame counter ran ahead of the GPU).
        //        • verificationResults.storageBytesWritten (Long, -1 = n/a) +
        //          storageWriteDurationMs (Long, -1 = n/a): real extent of
        //          the STORAGE_STRESS write loop — v[8] = 0 after the last
        //          non-zero value means the byte cap was reached, not I/O
        //          collapse. The byte cap itself is UNCHANGED (flash-wear
        //          decision, 2026-09-14).
        //        • variables[9].source / variables[10].source strings
        //          updated to describe the live series / sliding window.
        //        BEHAVIOURAL (no field change): channelDiscovery
        //        "skin_temp_c_real" (and thermalSensorAvailability.skinTemp)
        //        report "missing" when the only skin reading is the battery
        //        proxy — ≤65 reported "available" for it.
        //        Backend: systemPromptV6 channel descriptions for v[9]/v[10]
        //        and any consumer of maxBatteryTemp / procStatAvailable need
        //        updating; new fields may be ignored safely.
        //   67 → 2026-09-14 (reading cleanup, same two-device comparison).
        //        ⚠ FOUR SEMANTIC CHANGES, ZERO SCHEMA CHANGES — no footer
        //        field is added, removed, or retyped, so every ≤66 parser
        //        keeps working unmodified. The bump exists ONLY to make the
        //        value-meaning change discoverable:
        //
        //        ⚠⚠ DO NOT POOL ACROSS THE 66/67 BOUNDARY: device.
        //        storagePercent and scanStillness.isStill changed
        //        DISTRIBUTION, not shape. A consumer that mixes ≤66 and ≥67
        //        rows for either field is training or reasoning on two
        //        different variables under one name. Split the corpus on
        //        fieldsVersion >= 67 for these two; the other two semantic
        //        changes below are corrections that only improve ≥67 rows.
        //
        //        SEMANTIC (value meaning changed, shape unchanged):
        //        • device.storagePercent: now S-02's percent-FREE (0–100).
        //          ≤66 read S-03 — sequential WRITE SPEED in MB/s — so the
        //          field carried 635 (Honor MTN-NX1) and 166 (S10 5G) where
        //          a percentage was expected. ≤66 values are NOT percentages
        //          and must be discarded, not rescaled.
        //        • device.availableStorageGb: now the real free-space GB,
        //          parsed from S-02's unit string "% (149.2 GB)". ≤66 read
        //          S-02's rawValue, which is the percent — Honor reported
        //          "67.5" GB free while 149.2 GB were free. ≤66 values are
        //          percentages mislabelled as gigabytes.
        //        • scanStillness.isStill: threshold on the accel-magnitude
        //          population std-dev re-based 0.1 → 0.5 m/s². Seven field
        //          scans with the device flat on a table measured 0.2515–
        //          0.3723 (mean 0.2972, sd 0.0540) and ALL reported false
        //          under 0.1 — the flag fired on 100 % of correct
        //          placements and carried no information. 0.5 = mean+3·sd
        //          rounded up. ≤66 isStill is false on essentially every
        //          scan and is not comparable with ≥67.
        //          SCOPE (must reach the prompt): a HAND-HELD device
        //          measured 0.35, inside the motionless range, so this
        //          statistic cannot separate "held steady" from "on a
        //          table". isStill=true means ONLY "no gross motion
        //          (walking / carrying / shaking) during the scan" — never
        //          "the device was on a flat surface". accelStdDev = -1
        //          still means "could not measure" (<2 accel samples).
        //        • stressEffectiveness.satCaveat / saturationFailureReason:
        //          text only. ≤66 appended a fixed "likely due to OEM
        //          governor policy (Xiaomi/HyperOS class)" to EVERY C4
        //          failure — it shipped that attribution on a Samsung S10
        //          5G whose own governorCappingDetected read false. ≥67
        //          states only the measured fact (peak ratio vs nominal)
        //          and mentions a governor cap ONLY when
        //          governorCappingDetected is true. Numbers in these
        //          strings are now Locale.ROOT (≤66 emitted Arabic-Indic
        //          digits such as "٠٫٨٥" on ar-locale devices) and the C4
        //          ratio prints 3 decimals (2 rendered the field value
        //          0.84761906 as the self-contradicting "0.85 < 0.85").
        //        ADDITIVE: none.
        //        REMOVED: none.
        //        BEHAVIOURAL (no field change): governorCappingDetected is
        //        now passed into StressEffectivenessCalculator.compute()
        //        instead of being copy()'d on afterwards — same value,
        //        single source of truth, and the caveat text now reads the
        //        same flag the footer reports.
        //        NOT CHANGED (verified this round, no defect): dataQuality.
        //        cpuUsageFallbackFromSample = 0 is a genuine sample index —
        //        the absent value is -1 — so 0 means /proc/stat was already
        //        unreadable when the first sample was built; and
        //        footer.thermalStatusMax is present with real values in the
        //        raw files (S10 5G = 2, Honor = 0).
        //        Backend: systemPromptV6 must stop reading ≤66
        //        storagePercent / availableStorageGb as device specs, and
        //        should carry the isStill SCOPE sentence above.
        //   68 → 2026-10-08 (V8 batch 3, contract docs/FV68_CONTRACT.md).
        //        COMPLETE 2026-10-08, at the last commit of batch 3 (part 3); T-02 (the
        //        cycle count) was added INSIDE it the same day by the user's decision.
        //        THE STAMP WAS BUMPED AT THE FIRST COMMIT THAT CHANGED THE SHAPE OF
        //        THE PAYLOAD OR THE FILE (2afd392), NOT AT THE LAST ONE (rule of this
        //        batch, from the A70 incident: a shape change shipped under an old
        //        stamp). Every commit between that one and the last carried 68 on a
        //        shape that was incomplete BY DESIGN, and no release APK was built
        //        from any of them. This declaration changes no value and not the
        //        stamp (it stays 68). Releasing the APK is the user's decision.
        //        NOT IN 68 (deferred by the contract, see its section 7-d / 7-f):
        //        the four manual-test states and manualResultsHeader.hiddenByModel,
        //        the touch grid, the light-sensor coverage and "wait" (batch 10),
        //        SN-04 (batch 10). stressEffectiveness.peakBigFreqKhzInStressPhases and
        //        c4RatioStressWindow stay in the FILE only (user decision 2026-10-08).
        //        The server readers are batch 4; the device stage of the golden
        //        sample is the next field round.
        //        ADDITIVE (commit by commit in the contract sections 7 .. 7-f):
        //        • functional_results.measurements (Room 7 -> 8) and the
        //          "measurements" key on functional/manual result DTOs (2afd392);
        //          L-02 writes the satellite numbers there (78ddbe3).
        //        • THE SAMPLE WIDTH IS NOT CONSTANT BETWEEN DEVICES ANY MORE:
        //          v[0..10] are frozen (no index moves), then come the per-device
        //          channels that passed discovery - charge_counter_raw (unit
        //          "as_declared_by_device"), then cpu_freq_policy{N}_khz with N
        //          ascending - and variables[] of each file declares exactly its
        //          own columns. A reader resolves a column by NAME; a
        //          variables.size test is wrong (an 18-column V2 file exists).
        //          A failed read of such a channel leaves the last valid value in
        //          v[] and lists the sample index in footer.channelReadFailures
        //          (+ ...Counts, at most 200 indices); readers must leave those
        //          samples out (bc70908; names in ChannelNames: 02368ff).
        //        • footer.cpuPolicies[] (raw {policy, relatedCpus,
        //          cpuinfoMaxFreqKhz, unreadableReason}) and
        //          footer.channelDiscovery.reasons (the error TEXT of every channel
        //          that is not available, <= 200 chars) (bc70908).
        //        • footer.thermalHeadroomReadings[] (a call every 10 s, each with
        //          its own state) and thermalStatusEvents[] + their two states;
        //          they are LISTS, not v[] channels (c9f0992).
        //        • footer.batteryPairs[] (RAW voltageRaw/currentRaw/
        //          chargeCounterRaw/levelRaw/levelScale/tempRaw; the first 200)
        //          + batteryPairsState (051aa8b).
        //        • footer.cpuTempFinalC + cpuTempFinalSource (d1a062c).
        //        • footer.batteryHealth.cycleCount / cycleCountSource / cycleCountState
        //          (T-02, e7fa26e): ONE reader, BatteryCycleCount.read, used by the
        //          footer AND by B-08 — EXTRA_CYCLE_COUNT of the battery broadcast on
        //          API >= 34, then the sysfs file, then a declared absence
        //          (not_read:<sysfs reason>_and_<extra reason>); cycleCountSource is
        //          extra_api34 | sysfs:<path> | unavailable (the old values
        //          sysfs_cycle_count / battery_property_cycle_count_api_34 /
        //          no_battery_manager are gone). The property getIntProperty(8) it
        //          used to read is not public (javap on android.jar 33-36:
        //          BATTERY_PROPERTY_* 1..6 only; EXTRA_CYCLE_COUNT from 34).
        //        • ACTIVITY_RECOGNITION (API >= 29); SN-08 / SN-09 are NOT_TESTED
        //          without it and ship their raw readings (24a2d60).
        //        • timeSeries.timeSeriesState, footer.fieldsNotInFile and the
        //          explicit absent-with-a-state fields of card T-18:
        //          gpuComputeGemmState,
        //          criticalDefectInputsNotRun, transitionalAggregates (5e887f5);
        //          workersPinState (07407fe); scanStillness.stillnessState and
        //          preScanWarningsState, third state not_written (d7a930c,
        //          6200f4e); storageVerifyState (282370b); unmeasuredDefaults for
        //          the dataQuality literals (23c66bd); batteryHealth <name>State,
        //          cycleCount / charge / energy counters as read with a state,
        //          batteryTempRiseC absent with a state, levelDropPct signed
        //          (c4c1447, dbc2df1, bbf470c); a payload with no time series
        //          leaves its header and footer fields ABSENT (55ebb0f); a file
        //          stamped below 68 read by this builder names every fv68 key it
        //          lacks in fieldsNotInFile (872a3d8).
        //        • verificationResults, the three verification PRODUCERS: memory
        //          (C++ March C-, Walking Ones, a size-independent random verify,
        //          under a time cap; memoryVerify* incl. memoryPhaseWindows, and
        //          memoryVerifyFreeBytes with the free-memory entry condition)
        //          (4998437, 34466e4); storage read-back of the last 256 MiB
        //          (storageVerify*, storageWriteVerifyErrors) (4f77ecd, 55c0952);
        //          CPU known-answer checks D (cpuKnownAnswerState / Runs /
        //          TotalMsByPhase) (afac56f, d2d2867).
        //        REMOVED: footer.maxCpuTemp (replaced by cpuTempFinalC; still
        //        forwarded from a pre-68 file), verificationResults.
        //        storageSlcCacheSizeMb (the server derives it), the C++
        //        run_memory_stress (4998437); aiDelegateInitError is ABSENT, not "".
        //        DECLARED (no value change): variables[0] and gpu_fps source texts,
        //        v[8] / v[9] in MiB/s and storageThroughputSegmentsUnit; the
        //        gpu_fps window is cleared when phase 5's load stops (4a250c1).
        //        TEST SIDE (no wire change): AnalysisRequestSerializer is the one
        //        place the payload is flattened (fb82136); three golden request
        //        payloads from the real chain with a rule-39 guard live in
        //        app/src/test/resources/derived/ (acb773d).
        //        Backend: the readers of section 4 of the contract must be updated
        //        before this stamp is accepted (rule 25).
        //   69 → 2026-10-10 (V8 batch 10, "technician steps", contract
        //        docs/FV68_CONTRACT.md section 7-h). Stamped at the FIRST commit
        //        of the batch that changes the request payload's shape (user
        //        decision 2026-10-10: «fv69 في أوّل التزام يغيّر الشكل»), so every
        //        commit of the batch up to the last carries 69 and is incomplete
        //        BY DESIGN — no APK from an intermediate commit. The time-series
        //        FILE does not change; the REQUEST PAYLOAD does:
        //        • manualResultsHeader.hiddenByModel (N-17): the tests the device
        //          model does not have (not asked, no row). Always sent by the
        //          production path; [] = nothing hidden; absent = a build <= 68.
        //        • manualResults[].status gains "unavailable": the technician's
        //          «غير متوفر في هذا الجهاز» (five dialog buttons), stored as
        //          "not_tested" until 68 — the two answers are now distinct.
        //        • manualResults[M-02].measurements (N-19): the touch grid's raw —
        //          gridCols / gridRows / touchedCells / totalCells / elapsedMs (from the
        //          FIRST touch) / timeToFirstTouchMs / endReason / maxSimultaneousPointers.
        //          The 95/80 thresholds and the "warning" recommendation are gone from
        //          the app; the server puts the thresholds on the raw.
        //        • functionalResults[SN-04].measurements (N-20): the light sensor's raw in two
        //          lists split where the technician was asked to cover it — windowMs,
        //          coverPromptAtMs, readingsBeforeCover[], readingsDuringCover[] ({t, values}).
        //          status / rawValue / notes are what they were in 68 (the server classifies
        //          "not measured" from the raw; the app sends no not_measured).
        //        Backend: the readers of the contract's section 7-h must be updated
        //        before this stamp is accepted (rule 25).
        //   70 → 2026-10-13 (the CPU known-answer check D fix, contract
        //        docs/FV68_CONTRACT.md section 7-t). Stamped at the FIRST commit of the fix
        //        that changes the shape, so every commit of the fix up to the last carries 70
        //        and is incomplete BY DESIGN — no APK from an intermediate commit. P6-13 is NOT
        //        part of 70: it moves to fv71 (user decision 2026-10-13). The time-series FILE
        //        changes only inside the existing verificationResults.cpuKnownAnswerRuns[]
        //        elements (no new top-level key); the REQUEST PAYLOAD carries the same:
        //        • D's prime sieve runs to 1,000,000 (reference 78,498) instead of 10,000,000
        //          (reference 664,579): the device check measured the 10^7 sieve as 88-94 % of
        //          D's time. runPrimeSieveTest (not D) stays on 10,000,000.
        //        • cpuKnownAnswerRuns[].sieveLimit: the N the sieve was run to, raw, from the
        //          native layer (JNI sieve layout 2, six slots).
        //        • sieveStatus / sha256Status gain "skipped:time_budget" (both written; no t,
        //          tEnd, count, durations or limit) and cpuKnownAnswerState gains
        //          "partial:time_budget": a run is left out when (the time D has used in the
        //          phase + its longest run) is above 1 % of the phase's planned duration; the
        //          first run always runs.
        //        Backend: the readers of the contract's section 7-t must be updated before this
        //        stamp is accepted (rule 25).
        //   71 → 2026-10-18 (contract docs/FV68_CONTRACT.md section 7-ي: M-02's new grid, the volume buttons' raw, P6-13). Stamped at the FIRST
        //        commit that changes the request payload's shape (the contract's rule: 71 goes in the first shape-changing commit), so every commit of
        //        the three rows up to the last carries 71 and is incomplete BY DESIGN — no APK from an intermediate commit. The time-series FILE does not
        //        change. Row A-M02 (this commit): manualResults[M-02].measurements is the new grid's raw:
        //        • the grid is cells of 6 mm laid out from the touch surface and the screen's density: gridCols / gridRows / totalCells (= cols x rows
        //          minus cutoutCells) change MEANING (no longer 8 x 15 = 120), and auto_complete means every REQUIRED cell is lit;
        //        • new: surface*Px, display*Px, modePhysical*Px, xdpi / ydpi / densityDpi / pxPerMm / densitySource / densityCheck, cellTargetMm /
        //          cellWidthPx / cellHeightPx, untouchedCells / cutoutCells, gestureInsetsPx / mandatoryGestureInsetsPx / exclusionRectsPx /
        //          systemBarsHidden / cutoutRectsPx, roundedCornerRadiiPx OR roundedCornersState, absentFieldReasons (N71-01), strokes[] (every sample
        //          of every finger, raw), unbufferedDispatch, resampledFlagState, displayedSuspects (absent after an automatic completion), pathCap (N71-02).
        //        Backend: the readers of the contract's section 7-ي must be published before any APK sends 71 (rule 25).
        val fieldsVersion = 71 // 71: M-02 new grid (A-M02), then the volume buttons and P6-13 - declaring it complete and building an APK are not these rows' decisions - see the block above
        create("launch") {
            dimension = "version"
            applicationId = "net.neuralcheck"
            resValue("string", "app_name", "NeuralCheck")
            buildConfigField("boolean", "USE_AI_SCORING", "true")
            buildConfigField("boolean", "SHOW_PEER_INPUT", "false")
            buildConfigField("boolean", "SHOW_MAINTENANCE_SCAN", "false")
            buildConfigField("boolean", "REQUIRE_SUBSCRIPTION", "true")
            // Datacollect-only diagnostics (SEC-12 bootloader-lock, INFO-01 CSC,
            // SN-13 accel calibration). The launch flavor must NOT surface
            // region codes — they are PII the consumer build is not allowed to
            // log to Firestore.
            buildConfigField("boolean", "ENABLE_EXTENDED_INFO_TESTS", "false")
            buildConfigField("String", "API_BASE_URL", "\"https://api.neuralcheck.app\"")
            // V2 stress test feature flag. When true, StressTestService
            // delegates the 690s/5-phase V1 path to the 480s/8-phase V2
            // path (NDK kernels + adaptive intensity + dynamic throttling)
            // and AiScoringEngine calls buildV2() to populate the V2
            // stats fields. Master plan §2.
            buildConfigField("boolean", "USE_V2_STRESS", "true")
            // Phase 1.8 Task 5: relative-calibration feature flag.
            // false = scan runs the existing tier-based path
            // (AdaptiveIntensityController.aiConfigForTier) — bit-for-bit
            // identical to pre-Phase-1.8 behaviour. true = pre-scan
            // ProbePhase + WorkloadCalibration drive per-subsystem
            // workload params, footer carries the calibration block.
            // Stays false on every committed build until 1.8.E hardware
            // validation; flip locally to true to test on a device.
            buildConfigField("boolean", "USE_RELATIVE_CALIBRATION", "false")
            buildConfigField("int", "FIELDS_VERSION", "$fieldsVersion")
        }
        create("datacollect") {
            dimension = "version"
            applicationId = "net.neuralcheck.datacollect"
            resValue("string", "app_name", "NeuralCheck Dev")
            buildConfigField("boolean", "USE_AI_SCORING", "false")
            buildConfigField("boolean", "SHOW_PEER_INPUT", "true")
            buildConfigField("boolean", "SHOW_MAINTENANCE_SCAN", "true")
            buildConfigField("boolean", "REQUIRE_SUBSCRIPTION", "false")
            buildConfigField("boolean", "ENABLE_EXTENDED_INFO_TESTS", "true")
            buildConfigField("String", "API_BASE_URL", "\"https://api.neuralcheck.app\"")
            // datacollect: V1 path stays — internal diagnostic flavor uses the
            // legacy stable V1 chain so any drift in V2 doesn't leak into
            // baselines.
            buildConfigField("boolean", "USE_V2_STRESS", "false")
            // Phase 1.8 Task 5: datacollect flavor keeps the relative-
            // calibration flag off — it runs V1, not V2, and the
            // calibration plumbing is V2-only. Field is declared so
            // BuildConfig.USE_RELATIVE_CALIBRATION resolves uniformly
            // in shared code regardless of flavor.
            buildConfigField("boolean", "USE_RELATIVE_CALIBRATION", "false")
            buildConfigField("int", "FIELDS_VERSION", "$fieldsVersion")
        }
    }

    // Release-signing configuration. Phase 1.13 keystore rotation moved the
    // keystore path and passwords out of this file into keystore.properties
    // at the repo root (gitignored). The .exists() guard means Gradle sync
    // still succeeds on a fresh checkout without the secret file — the
    // resulting release variant just won't have a signing config until the
    // file is provisioned.
    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        // Debug shares the release keystore so a developer device with the
        // production APK installed can be updated with a debug build (and
        // vice versa) without uninstall + data loss. The Android Gradle
        // Plugin would otherwise auto-assign the per-machine debug
        // keystore (~/.android/debug.keystore), giving debug + release
        // mismatched signatures and triggering INSTALL_FAILED_UPDATE_
        // INCOMPATIBLE on side-by-side installs.
        getByName("debug") {
            signingConfig = signingConfigs.getByName("release")
        }
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            // Strip unused resources alongside dead code. Safe with our Compose
            // + resource-id-by-name lookups because every dynamic getIdentifier
            // call in the codebase resolves to a `test_*_ar` / `test_*_tr`
            // string that is also referenced by an explicit `R.string.foo`
            // somewhere — so R8 keeps them. If you add a new dynamic resource
            // lookup with NO direct R.string.* reference, register it via
            // tools:keep in res/raw/keep.xml or this stripper will drop it.
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }

    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }

    // Phase 1.6.A: don't compress .tflite assets — TensorFlow Lite mmaps
    // model files directly from the APK and the mmap fails on a deflated
    // entry. The size cost is negligible (~22 MB large model is already
    // FP32-packed; gzip would shave maybe 1-2 MB and break Interpreter
    // construction).
    androidResources {
        noCompress += listOf("tflite")
    }

    // Phase 1.6.A: unit tests need android.jar stubs to return defaults
    // instead of throwing "not mocked" RuntimeException. Without this
    // every Log.w / Log.i call inside InferenceEngineImpl explodes the
    // smoke test before it can exercise the real wrapper logic.
    testOptions {
        unitTests.isReturnDefaultValues = true
        // Order 2026-10-15 (batch 2, item 1): Robolectric loads the app's merged resources (R, strings, themes) for the layout test
        // of the "unavailable" line. Costs a resource-link step per unit-test variant (measured in the item-1 commit message).
        unitTests.isIncludeAndroidResources = true
    }

    lint {
        // Each test has paired *_ar / *_tr keys that are always fetched
        // explicitly by id regardless of the active locale, so the default
        // Missing/ExtraTranslation checks produce thousands of false
        // positives for this project.
        disable += setOf("MissingTranslation", "ExtraTranslation")
        abortOnError = true
        checkReleaseBuilds = true
    }
}

// A82: DiagnosticLoggingGuardTest (chore/guard-diag-logging, merged e4f5546)
// fails the build tree when NC.EvalDiag / EVAL-DIAG / NC.Diag logging or an
// unthrown diagnostic Throwable survives into app/src/main — but nothing
// actually ran it before an APK was produced: no dependsOn, no finalizedBy,
// no CI. assembleLaunchRelease/Debug could (and did) succeed on a tree the
// guard would have failed. Wired here for the launch flavor's own unit-test
// task, which is the task that executes the guard, so a red guard now fails
// the assemble instead of being silent. datacollect is intentionally left
// unwired: its diagnostic instrumentation is retained on purpose, and the
// launch flavor is the one that ships to consumers.
afterEvaluate {
    tasks.named("assembleLaunchDebug") { dependsOn("testLaunchDebugUnitTest") }
    tasks.named("assembleLaunchRelease") { dependsOn("testLaunchReleaseUnitTest") }

    // Order 2026-10-09 (user decision 6: backup off in 1.0.11). BackupDisabledManifestTest
    // reads the FINAL merged manifest of the variant it runs under — the one that goes into
    // the APK, with every library manifest merged in — not the source manifest. It cannot find
    // that file by itself without risking a STALE one from an older build (rule 34), so each
    // unit-test task depends on its own variant's manifest task and is handed that variant's
    // path. Wired for all four variants because assembleLaunchRelease depends on
    // testLaunchReleaseUnitTest above and every variant runs the same test classes; the
    // manifest task of a release variant only merges XML (no R8, no APK).
    listOf("launchDebug", "launchRelease", "datacollectDebug", "datacollectRelease").forEach { variant ->
        val cap = variant.replaceFirstChar { it.uppercase() }
        tasks.named("test${cap}UnitTest").configure {
            dependsOn("process${cap}Manifest")
            (this as Test).systemProperty(
                "nc.mergedManifest",
                layout.buildDirectory.file("intermediates/merged_manifests/$variant/AndroidManifest.xml").get().asFile.path
            )
        }
    }
}

dependencies {
    // Core
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-service:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")

    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.04.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.ui:ui-text-google-fonts")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
    // Order 2026-10-15 (batch 2, item 1): the layout test of the "unavailable" line (ManualUnavailableLineLayoutTest). Versions: see
    // the item-1 commit message. ui-test-junit4 takes its version from the BOM above (compose ui 1.6.6); ui-test-manifest declares
    // ComponentActivity in the DEBUG manifest, and the test registers the same activity itself so the release unit tests run too.
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.6")

    // Hilt DI
    implementation("com.google.dagger:hilt-android:2.50")
    ksp("com.google.dagger:hilt-compiler:2.50")
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")
    implementation("androidx.hilt:hilt-work:1.1.0")
    ksp("androidx.hilt:hilt-compiler:1.1.0")

    // Room Database
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // WorkManager (background sync)
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Security (EncryptedSharedPreferences)
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Biometric
    implementation("androidx.biometric:biometric:1.1.0")

    // Camera2
    implementation("androidx.camera:camera-camera2:1.3.1")
    implementation("androidx.camera:camera-lifecycle:1.3.1")
    implementation("androidx.camera:camera-view:1.3.1")

    // ZXing (QR codes)
    implementation("com.google.zxing:core:3.5.2")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")

    // Vico (charts)
    implementation("com.patrykandpatrick.vico:compose:1.13.1")
    implementation("com.patrykandpatrick.vico:compose-m3:1.13.1")

    // Gson (JSON serialization)
    implementation("com.google.code.gson:gson:2.10.1")

    // Phase 1.6: TensorFlow Lite — replaces synthetic stress kernels with
    // real AI inference workloads. Strategic goal: defeat governor capping
    // on locked-down Android (HyperOS, ColorOS) by making the stress test
    // indistinguishable from a legitimate AI benchmark — which the OS
    // does not aggressively throttle. The interpreter fronts NNAPI / GPU /
    // CPU delegates; tflite-support is needed for image-tensor utilities
    // when we feed real bitmaps into MobileNetV3.
    implementation("org.tensorflow:tensorflow-lite:2.16.1")
    implementation("org.tensorflow:tensorflow-lite-gpu:2.16.1")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4")

    // Firebase — Firestore/Storage for sync, Functions for AI analysis
    // (callable `analyzeDevice`), Auth for the identity required by callable
    // functions. The launch flavor drives the analysis pipeline; datacollect
    // never calls Functions at runtime but compiles against the same module.
    implementation(platform("com.google.firebase:firebase-bom:32.7.0"))
    implementation("com.google.firebase:firebase-storage")
    // KTX — needed by ShopSetupScreen so the logo upload goes through
    // `Firebase.storage` with the same Kotlin-friendly accessor pattern as
    // Firestore. The non-KTX `firebase-storage` is kept for any direct-SDK
    // call sites we still have in the analysis pipeline.
    implementation("com.google.firebase:firebase-storage-ktx")
    implementation("com.google.firebase:firebase-firestore")
    // KTX variant — used by ScanHistoryScreen so we can call
    // `Firebase.firestore` + Kotlin-friendly `whereEqualTo`/`orderBy`/`limit`
    // without plumbing a manual FirebaseFirestore.getInstance() everywhere.
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-functions-ktx")
    implementation("com.google.firebase:firebase-auth-ktx")
    // FCM — inbound push for payment activation / subscription state
    // changes. Tokens are refreshed via NeuralCheckMessagingService.onNewToken
    // and written to users/{uid}.fcmToken so the back-office can target a
    // specific user when a payment is approved.
    implementation("com.google.firebase:firebase-messaging-ktx")
    implementation("com.google.firebase:firebase-installations-ktx")

    // Google Identity — one-tap / Google Sign-In on LoginScreen. Used only
    // in the launch flavor (datacollect never shows the login flow) but is
    // compiled into both so a single R class works. The One-Tap client is
    // preferred over the deprecated GoogleSignInClient because it degrades
    // gracefully when Play Services is missing instead of crashing.
    implementation("com.google.android.gms:play-services-auth:21.0.0")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    // Phase 1.6.A — InferenceEngineSmokeTest needs a Context mock; the
    // android.jar stubs can't be instantiated directly under JVM tests.
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.2.1")
    // Order 2026-09-24 (القرار ٧٧) — Room migration JVM test. When written this module
    // did not have Robolectric (a test dependency since 3284cca; Room's own androidx.room:room-testing
    // is still not one: its MigrationTestHelper needs real Android SQLite via an instrumented
    // test — see this task's report), so the MIGRATION_5_6 SQL is
    // exercised directly against a real (not mocked) SQLite engine via
    // plain JDBC instead — pure-JVM, no Android dependency.
    testImplementation("org.xerial:sqlite-jdbc:3.46.1.3")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}

// ── Release build guard tasks (order 2026-09-23، القرار ٧٣، البند ١) ──
// See the execGit/gitCommitSha comment near the top of this file for the
// overall rationale. Scoped to `launchRelease` ONLY — the only variant this
// order's evidence (mapping.txt reference, APK sha256 citations) covers;
// `datacollectRelease` also runs R8 (isMinifyEnabled is set on the shared
// `release` buildType) but this order never discusses its mapping, so
// extending the letter-guard to it would be inventing an unverified
// reference (rule 13) — not done here.

// البند ١أ — الشجرة والالتزام + غير المتتبَّع داخل ما يُبنى فعلياً (order
// 2026-09-24، القرار ٧٧، البند ١). Two independent checks:
//   (a) any TRACKED file modified — `--untracked-files=no` matches the
//       ORIGINAL order's wording ("وغير المتتبَّع لا يمنع"), i.e. this
//       check alone must stay blind to untracked files, on purpose.
//   (b) any UNTRACKED file inside the source/resource trees Gradle
//       actually compiles/packages from — (a) alone cannot catch this: a
//       new .kt file dropped into app/src/main that nobody `git add`ed
//       still gets compiled into the APK by Gradle, which walks the
//       filesystem, not the git index. Scoped to `app/src` (every source
//       set — main/test/androidTest — the order says "app/src/**"
//       broadly, not main-only) and `app/guard` (the DeviceProfileKey
//       mapping guard's own input file, read directly by
//       checkDeviceProfileKeyMapping below). Uses `git status` itself
//       (which already understands .gitignore) rather than re-implementing
//       ignore-pattern matching — "لا يتوقّف على .gitignore وحده" is
//       about not treating "not gitignored" as the ONLY signal (an
//       untracked-but-NOT-ignored file must still be caught, which this
//       does), not about avoiding git's own gitignore-aware status.
//
// Runs at task-execution time only (doLast), so `./gradlew tasks`, IDE
// sync, and every debug/datacollect build are unaffected. Deliberately
// does NOT scope to the whole repo — `.worktrees/`, `AI/`, `docs/audits/`,
// `scripts/` are untracked today (pre-existing, unrelated to this task)
// but outside app/src and app/guard, so this guard must not (and does
// not) reject them; see this task's report for what it actually sees at
// HEAD.
val untrackedBuildFileAllowlist: Set<String> = setOf(
    // Empty today — every file under app/src/** and app/guard/** at HEAD
    // is tracked. Add an entry here ONLY with a written reason (order:
    // "ولكل بند فيها سبب مكتوب") — e.g. a generated file some future build
    // step legitimately leaves untracked inside these trees.
)

val checkReleaseTreeClean = tasks.register("checkReleaseTreeClean") {
    group = "verification"
    description = "Order 2026-09-23/24 (القرار ٧٣ البند ١أ + القرار ٧٧ البند ١) — " +
        "rejects launchRelease if a tracked file is modified, or if an " +
        "untracked file exists inside app/src/** or app/guard/**."
    doLast {
        val status = execGit("status", "--porcelain", "--untracked-files=no")
        if (status.isNotBlank()) {
            throw GradleException(
                "checkReleaseTreeClean: RELEASE BUILD REFUSED — tracked tree is " +
                    "modified. `git status --porcelain --untracked-files=no`:\n$status"
            )
        }

        val untrackedOutput = execGit(
            "status", "--porcelain", "--untracked-files=all", "--", "app/src", "app/guard"
        )
        val offending = untrackedOutput.lineSequence()
            .filter { it.startsWith("??") }
            .map { it.removePrefix("??").trim() }
            .filterNot { it in untrackedBuildFileAllowlist }
            .toList()
        if (offending.isNotEmpty()) {
            throw GradleException(
                "checkReleaseTreeClean: RELEASE BUILD REFUSED — untracked file(s) " +
                    "inside app/src/** or app/guard/** would silently enter the " +
                    "build (Gradle compiles/packages from the filesystem, not the " +
                    "git index):\n" + offending.joinToString("\n") +
                    "\nAdd to untrackedBuildFileAllowlist in app/build.gradle.kts " +
                    "ONLY with a written reason, or `git add` / remove the file."
            )
        }

        logger.lifecycle("checkReleaseTreeClean: tree clean, no stray untracked build files, at commit $gitCommitSha")
    }
}

// البند ١ب — أسماء DeviceProfileKey بعد R8.
val deviceProfileKeyMappingExpectedFile = file("guard/deviceProfileKeyMapping.expected.properties")
val DEVICE_PROFILE_KEY_CLASS = "net.neuralcheck.util.DeviceProfileKey"

/**
 * Same parsing rules as tools/guards/proguard_mapping.py's
 * extract_class_field_mapping — kept in sync BY HAND across the two
 * languages (Python has no place in the Android build's execution
 * classpath; see that file's own header for why the Python version isn't
 * shelled out to here instead). Both were exercised against the identical
 * committed fixtures under tools/guards/testdata/ — see this task's report
 * for the two runs' actual output, tool-sourced.
 */
fun extractDeviceProfileKeyMapping(mappingFile: File): Map<String, String>? {
    val classHeaderRe = Regex("""^([\w.$]+)\s*->\s*[\w.$]+:\s*$""")
    val fieldRe = Regex("""^ {4}[\w.\[\]]+\s+(\w+)\s*->\s*([\w$]+)\s*$""")
    val fields = LinkedHashMap<String, String>()
    var inClass = false
    var classFound = false
    mappingFile.forEachLine { rawLine ->
        if (rawLine.startsWith("#")) return@forEachLine
        val header = classHeaderRe.find(rawLine)
        if (header != null) {
            inClass = header.groupValues[1] == DEVICE_PROFILE_KEY_CLASS
            if (inClass) classFound = true
            return@forEachLine
        }
        if (!inClass) return@forEachLine
        val m = fieldRe.find(rawLine)
        if (m != null) {
            fields[m.groupValues[1]] = m.groupValues[2]
        }
    }
    // null = the class header is not in the file at all; an EMPTY map = the class is there and no field
    // line follows it, i.e. R8 kept every field under its own name (a kept field has no line in mapping.txt).
    return if (classFound) fields else null
}

fun loadExpectedProperties(file: File): Map<String, String> {
    val props = Properties()
    file.inputStream().use { props.load(it) }
    return props.entries.associate { (it.key as String) to (it.value as String) }
}

val checkDeviceProfileKeyMapping = tasks.register("checkDeviceProfileKeyMapping") {
    group = "verification"
    description = "Order 2026-09-23 (القرار ٧٣، البند ١ب), semantics changed 2026-10-09 (decision 5) — " +
        "rejects launchRelease if R8 renamed any DeviceProfileKey field (the release must emit the " +
        "SOURCE names; guard/deviceProfileKeyMapping.expected.properties lists each against itself; " +
        "a field with no line in mapping.txt counts as kept). Pass " +
        "-PmappingFile=<path> to check an arbitrary mapping.txt standalone " +
        "(testing, no R8, no APK built)."
    doLast {
        val mappingFile = if (project.hasProperty("mappingFile")) {
            // rootProject, not `file()` (which resolves relative to the
            // `:app` module dir) — -PmappingFile= paths are given relative
            // to the repo root, matching every other path this session's
            // reports cite.
            rootProject.file(project.property("mappingFile") as String)
        } else {
            file("$buildDir/outputs/mapping/launchRelease/mapping.txt")
        }
        if (!mappingFile.exists()) {
            throw GradleException("checkDeviceProfileKeyMapping: mapping file not found: $mappingFile")
        }
        val expected = loadExpectedProperties(deviceProfileKeyMappingExpectedFile)
        val actual = extractDeviceProfileKeyMapping(mappingFile)
            ?: throw GradleException(
                "checkDeviceProfileKeyMapping: class $DEVICE_PROFILE_KEY_CLASS not found in $mappingFile"
            )
        val problems = mutableListOf<String>()
        for ((fieldName, expectedName) in expected) {
            // No line under the class block = R8 kept the field's own name.
            val actualName = actual[fieldName] ?: fieldName
            if (actualName != expectedName) {
                problems += "  $fieldName: expected -> $expectedName, actual -> $actualName"
            }
        }
        for (fieldName in actual.keys - expected.keys) {
            problems += "  $fieldName: unexpected field not in $deviceProfileKeyMappingExpectedFile (actual -> ${actual[fieldName]})"
        }
        if (problems.isNotEmpty()) {
            throw GradleException(
                "checkDeviceProfileKeyMapping: RELEASE BUILD REFUSED — $DEVICE_PROFILE_KEY_CLASS " +
                    "name mismatch against $deviceProfileKeyMappingExpectedFile:\n" +
                    problems.joinToString("\n")
            )
        }
        logger.lifecycle("checkDeviceProfileKeyMapping: all ${expected.size} $DEVICE_PROFILE_KEY_CLASS field names match")

        // البند ١ب — "وبعد كل بناء محروس يُنسخ mapping.txt مع رقم الالتزام
        // إلى app/build/outputs/guard/<sha>/". Only for a real guarded
        // build (the default mappingFile path, not a standalone
        // -PmappingFile= test run against an arbitrary file).
        if (!project.hasProperty("mappingFile")) {
            val guardOutDir = file("$buildDir/outputs/guard/$gitCommitSha")
            guardOutDir.mkdirs()
            mappingFile.copyTo(File(guardOutDir, "mapping.txt"), overwrite = true)
            File(guardOutDir, "commit.txt").writeText(gitCommitSha + "\n")
            logger.lifecycle("checkDeviceProfileKeyMapping: copied mapping.txt + commit to $guardOutDir")
        }
    }
}

// Wiring — launchRelease only, execution-time dependency so debug/datacollect
// builds and non-assemble tasks never trigger either guard. `afterEvaluate`
// because AGP registers its own per-variant tasks (assembleLaunchRelease,
// minifyLaunchReleaseWithR8, packageLaunchRelease — names confirmed by a
// real `./gradlew :app:assembleLaunchRelease --dry-run`, not guessed) AFTER
// this script's top-level `android {}` block finishes configuring, so
// `tasks.named(...)` here would throw UnknownTaskException if called
// eagerly at that point.
project.afterEvaluate {
    tasks.named("assembleLaunchRelease").configure {
        dependsOn(checkReleaseTreeClean)
    }
    tasks.named("checkDeviceProfileKeyMapping").configure {
        // -PmappingFile=<path> is the documented standalone mode ("no APK built"): the file is handed
        // over, so there is nothing for R8 to produce. Before 2026-10-09 this dependsOn was
        // unconditional, so the "standalone" mode ran R8 first (found by --dry-run when the Kotlin
        // side of the guard had to be exercised on fixtures without a release build — rule 16).
        if (!project.hasProperty("mappingFile")) {
            dependsOn("minifyLaunchReleaseWithR8")
        }
    }
    tasks.named("packageLaunchRelease").configure {
        dependsOn(checkDeviceProfileKeyMapping)
    }
}
