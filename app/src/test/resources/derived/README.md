# derived/ - files copied from elsewhere, never edited

`fv67-golden-1.0.8-v6beta-from-neuralcheck-backend-19fc7bc-34dff2b8.ts.json`

- A byte-for-byte copy of `neuralcheck-backend@19fc7bc:functions/test/fixtures/golden/34dff2b8.ts.json`.
  sha256 `e451306b31e74b6c77d6513989341cfe7a5f0408ed2119bcc085bf251b1720d3` (checked after the copy).
- Read with node: `header.appVersion = 1.0.8-v6beta`, `footer.fieldsVersion = 67`, 1606 samples, 11 variables.
- The server replaced three values with random ones of the same shape (neuralcheck-backend@d6c71ea,
  2026-09-24): `header.deviceFingerprint`, `header.scanId`, `footer.samplesSha256`. The last no longer
  matches the samples.
- Not verified: whether it is the device's file or the server's copy of `timeSeries` from the payload.
- The closest fv67 file available to the app test (`LegacyFv67FileTest`); it is NOT a file from a device
  (the user's decision: no file from the user's device enters the repository). The real check is on the
  next device round: S10 on 1.0.10 -> a scan that is not analysed -> update to 1.0.11 without uninstalling
  -> re-analysis.
- The test makes its variants in memory; this file is never modified (rule 9).

## fv71 app request payload samples (made in batch 3 part 3 as fv68, regenerated for fv69 in batch 10, for fv70 in the D fix and for fv71 in A-M02)

**fv71 (2026-10-18):** the three files were named `fv70-app-request-payload-derived-from-...` until A-M02 (the new M-02 grid); their content
changed (`footer.fieldsVersion` is 71, and `manualResults[M-02].measurements` carries what section 7-ي of `docs/FV68_CONTRACT.md` adds), so they
were renamed with `git mv` (`.gitattributes` follows: the `-text` pattern names `fv71-...`) and the fv70 versions are in git history at `077bbf2`.
What changed in the samples is listed in "fv71 (A-M02): what changed in the samples" below. Row A-VOL (this README's "fv71 (A-VOL)" section) adds the volume rows; the row A-P6-13 of the same contract section
changes the samples again, in its own commit (rule 39).

**fv70 (2026-10-13):** the three files were named `fv69-app-request-payload-derived-from-...` until the D fix; their content changed
(`footer.fieldsVersion` is 70, and the CPU known-answer runs carry what section 7-t of `docs/FV68_CONTRACT.md` adds), so they were
renamed (`.gitattributes` follows: the `-text` pattern names `fv70-...`) and the fv69 versions are in git history at `c22251f`. What
changed in the samples is listed in "fv70 (the D fix): what changed in the samples" below.

**fv69 (2026-10-10):** the three files were named `fv68-app-request-payload-derived-from-...` until batch 10; their content changed
(`footer.fieldsVersion` is 69, and the payload carries what section 7-h of `docs/FV68_CONTRACT.md` adds), so they were renamed with
`git mv` and the fv68 versions are in git history at `99c0548` (their sha256 are in the contract, section 7-f / 7-g). What changed in
the samples is listed in "fv69 (batch 10): what changed in the samples" below.

Three golden samples of what the app sends to the `analyzeDevice` callable, and beside each a `.keys.txt`
(one key path per line, sorted; `[]` after an array; a key whose name is data, such as
`channelReadFailures.cpu_freq_policy2_khz`, is listed literally). They are DERIVED files (rule 9): a new file
under a name that says so, with the list of everything that did not come from its real producer. Nothing here
edits the real recording.

### What each file is

The value the callable receives as `data.fingerprint` — `AnalysisRequestSerializer.fingerprintToMap(fingerprint)`.
NOT the whole callable `data` (`scanId`, `language`, ... are added around it by `FirebaseAnalysisService.analyzeDevice`).

| file (after `fv71-app-request-payload-derived-from-fv67-golden-34dff2b8-`) | columns in `variables[]` | why this layout |
|---|---|---|
| `11ch-nothing-admitted.json` | 11 | discovery admitted no per-device channel: the charge counter is unsupported and no cpufreq policy is readable. `cpuPolicies` is `[]` (discovery ran and found none), `channelReadFailures` is `{}`. The base layout every reader must still handle. |
| `13ch-selinux-blocked-policy.json` | 13 (`charge_counter_raw`, `cpu_freq_policy0_khz`) | policy 4 is refused by SELinux (EACCES): the absence is visible ONLY in `channelDiscovery` (`blockedBySelinux`, `reasons`) and `cpuPolicies[]` (`unreadableReason`), never in `v[]`. |
| `16ch-four-policies-truncated-failures.json` | 16 (`charge_counter_raw`, policies 0, 2, 4, 6) | the longest layout; one policy's failed reads (230) exceed the 200-index cap, so its list is cut at 200 and its count says 230; also an early-stopped scan (`isComplete` false, 959 of 1606 rows). |

Each sample also carries `functionalResults` and `manualResults` (inside the same `BehaviorFingerprint`).

The battery cycle count (`footer.batteryHealth.cycleCount`, T-02, read by `BatteryCycleCount.read`) tells a different story in each, so a reader
sees all three sources and the absence: the 11-column sample (API 30, as the real S10) has none — `cycleCountSource` `unavailable`, `cycleCount`
absent, state `not_read:sysfs_not_readable_or_absent_and_api_below_34`; the 13-column sample (API 34) reads 187 from `EXTRA_CYCLE_COUNT`, source
`extra_api34`; the 16-column sample (API 34, no extra in the broadcast) reads 212 from sysfs, source
`sysfs:/sys/class/power_supply/battery/cycle_count`.

### How they are made — rule 37, nothing in the chain replaced

`app/src/test/java/net/neuralcheck/files/AppRequestPayloadGolden.kt` runs:

1. the real `TimeSeriesFileWriter` (a mocked `Context` for `filesDir` ONLY) writes a `.ts.json.gz`;
2. the real `TimeSeriesFileReader` reads it back;
3. the real `BehaviorFingerprintBuilder.build` builds the payload;
4. the production serialization: `AnalysisRequestSerializer.fingerprintToMap` (`AnalysisRequestSerializer.kt`, the
   Gson at `:32` and the flattening at `:42-45`), the function `FirebaseAnalysisService.analyzeDevice` calls
   (`FirebaseAnalysisService.kt:121`). `AppRequestPayloadGoldenTest` fails if the service stops calling it.

A key whose value is `null` is an ABSENT key (no `serializeNulls`); a whole-valued number is written without a
fraction, as the Firebase SDK sends it; keys are sorted; the text is LF only (`.gitattributes` marks the files `-text`).

### Source of the values

- REAL raw: `fv67-golden-1.0.8-v6beta-from-neuralcheck-backend-19fc7bc-34dff2b8.ts.json` in this folder
  (sha256 `e451306b31e74b6c77d6513989341cfe7a5f0408ed2119bcc085bf251b1720d3`, a recording of a Samsung Galaxy S10 5G on 1.0.8-v6beta):
  `v[0..10]` of its 1606 rows with their `ts` and phase tags, its header, and the footer values listed below.
- PRODUCED by the real production code from those or from scripted inputs: the helper is named in the table below.
- SCRIPTED: invented by the generator; every one is a line of `AppRequestPayloadGolden.kt` marked `SCRIPTED:`,
  and every real value it keeps is marked `REAL:`.

The real file is a recording of an fv67 scan, so its footer lacks every fv68 key; the fv68 keys come from the
producers. As a cross-check of the chain: `batteryHealth` is recomputed by `BatteryIndicators` from the real rows, and
in the 11-column and 13-column samples `dischargeRateUnderLoadMa` (1647.8497596153845), `voltageStabilityMv`
(46.45574339814751), `batteryTempStartC`, `batteryTempMaxC`, `batteryTempRiseC`, `levelDropPct` and
`dischargeVsDesignRatioMah` equal the values the real device wrote (asserted by `AppRequestPayloadGoldenTest`, test `batteryHealth recomputed from the real rows equals what the real device wrote`).

### Modifications (everything that is not the real value from its real producer)

On the REAL rows and header:
1. `v[8]` and `v[9]` (and `verificationResults.storageThroughputSegments[].mbps`) multiplied by 1e6/2^20: the 1.0.8 file
   is decimal MB/s, an fv68 file declares MiB/s (`storageThroughputSegmentsUnit`).
2. `v[9]` set to `0` inside the declared `memory_kernel` window (the real file predates the kernel; while it runs nothing publishes `v[9]`).
3. `header.appVersion` = `1.0.11` (a literal, not `BuildConfig.VERSION_NAME`, so a version bump is not a golden change).
   `header.deviceFingerprint` and `header.scanId` are random values of the real file's shape, replaced on the server before this repository had it.
4. Per-device columns (`charge_counter_raw`, `cpu_freq_policy{N}_khz`) do not exist in the real file: fabricated.
   The charge counter starts at the real footer's `chargeCounterUah` (1711600) and falls by the real level drop (5 %) of the real design capacity
   (4400 mAh); a policy's frequency is the real `cpu_freq_max_khz` column scaled by (the policy's max / the real peak 2730000), floored to MHz.
5. The 16-column sample stops at 70 % of COMBINED_PEAK (959 of 1606 rows), as if the user cancelled.
6. `header.androidApiLevel` is 34 in the 13- and 16-column samples (the real file is API 30), with the matching `device.apiLevel` /
   `androidVersion` and the thermal platform's SDK, so the cycle count can come from `EXTRA_CYCLE_COUNT` (API >= 34). The device identity
   (`deviceModel`, `cpuModel`) is the real file's and is NOT changed: an S10 5G does not run API 34, which is one more reason these two are scripted.

Scripted per sample (see the generator for the numbers): the per-device sysfs and its refusals; which reads fail and when;
the thermal platform's answers (`nan`, exception, listener registration failure); the battery broadcasts (their values come from the real rows;
the charge counter and the invalid current reads are scripted); the pre-scan holder (`read`, `not_written`, `read_failed:<text>`); the CPU temperature
inputs; the native results of the memory, storage and known-answer kernels (including a partial run, a fallback read, a mismatch, a thrown call, an
insufficient-memory refusal); the GPU / AI / pinning story of the 16-column sample (STUB delegate with its init error, NEON fallback, pins attempted,
GEMM not run); one throttling event; stress-effectiveness overrides (per-CPU usage available in the 11-column sample; `satCaveat` in the 13-column;
`saturationFailureReason` in the 16-column); `dataQuality` procstat provenance in the 11-column sample; `deviceProfileKey` (placeholders);
every `functionalResults` / `manualResults` row (hand-built `TestResult`s, all six statuses) EXCEPT the rows built by a real producer
(SN-04, L-02, SN-08, SN-09 and M-02's raw: see fv69 items 3-4 and the 1.0.12 section below).

### fv69 (batch 10): what changed in the samples

Every sample carries these (the generator marks each `SCRIPTED:` / `PRODUCER:`):
1. `manualResultsHeader.hiddenByModel` (N-17), built by the real `ManualTestVisibility.hiddenByModel`. The PRODUCTION rule table is empty, so
   the 11- and 16-column samples carry `[]` (the explicit "nothing hidden"); the 13-column sample runs two SCRIPTED rules and shows
   `["M-14", "M-15"]` — those two tests have no row in `manualResults`. No production rule exists yet.
2. `manualResults` holds all four manual answers: `pass` (M-02, M-07), `fail` (M-19, scripted), `not_tested` (M-11) and
   `unavailable` (M-08, scripted: «غير متوفر في هذا الجهاز»). `unavailable` was written as `not_tested` until fv68.
3. (fv69 and fv70; replaced by the new grid in fv71 — see "fv71 (A-M02)" below) `manualResults[M-02].measurements` (N-19) is the raw of the real `TouchGridRecorder` driven by SCRIPTED pointers on a SCRIPTED clock
   (`touchGridRun` in the generator; a real run needs a screen and a finger). The three samples show the three ends: 11 columns — every cell
   lit, `auto_complete`, `pass`, a three-finger moment; 13 columns — 87 of 120 cells, the technician presses «لا يعمل»
   (`user_marked_not_working`, `fail`); 16 columns — nothing touched, `user_marked_not_tested`, `not_tested`, and `elapsedMs` /
   `timeToFirstTouchMs` ABSENT (no first touch). `elapsedMs` counts from the first touch; `timeToFirstTouchMs` from opening.
4. `functionalResults[SN-04].measurements` (N-20) comes from the real `SensorTests.testSensor` over a SCRIPTED `SensorManager` on virtual time
   (`lightSensorResult` in the generator): two lists, split at the moment the technician is asked to cover the sensor (1 000 ms), `windowMs` 4 000.
   11 columns: covered (the values fall) — `pass`; 13 columns: NOT covered (the values stay) — `pass` too: the app cannot tell, the server
   classifies; 16 columns: every reading 0 — `warning`. Status, `rawValue` and `notes` are what the app computed in fv68.

### fv70 (the D fix): what changed in the samples

Every sample's `verificationResults.cpuKnownAnswerRuns[]` comes from the REAL `CpuKnownAnswerScheduler` over a SCRIPTED native layer (a fake
that replaces `libstress.so`, rule 37 — the scheduler and the readers are real, the kernels are not) on a virtual clock. What differs from fv69:
1. every run that reached the sieve carries `sieveLimit` = 1000000 (SCRIPTED: the number a fv70 native layer returns in slot 5 of its layout-2
   array; `KnownAnswerReferenceTest` proves it is `PRIME_SIEVE_1M_LIMIT` in `reference_values.h`), and `sievePrimeCount` is 78498 (the reference
   for that limit; 664579 in fv69, whose limit was 10,000,000 and was not shipped). The mismatch scenario (13 columns, phase 5's second run)
   ships 78499; a `native_call_failed` run (16 columns, phase 3's second run) has NO `sieveLimit` — the call threw, nothing was reported.
2. The 11-column sample shows the TIME BUDGET: SCRIPTED, phase 3's first run takes 380 ms in the sieve + 30 ms in the SHA = 410 ms
   (`AppRequestPayloadGolden.KnownAnswerPlan.sieveMsAtCall`), and the REAL `CpuKnownAnswerScheduler` — 1 % of the planned 75 000 ms = 750 ms,
   (410 + 410) x 100 > 75 000 — leaves runs 2 and 3 of phase 3 out: each is `{phase, plannedAtPct, sieveStatus, sha256Status}` with both statuses
   `skipped:time_budget` and NO `t`, `tEnd`, count, duration or `sieveLimit`. Phase 5 is decided on its own (60 000 ms, limit 600 ms) and runs all
   three at the default 120 ms. `cpuKnownAnswerState` is `partial:time_budget` and `cpuKnownAnswerTotalMsByPhase` is `{"3": 410, "5": 360}`
   (the skipped runs add nothing). The 13- and 16-column samples skip nothing and stay `ran`.
3. `footer.fieldsVersion` is 70.

### fv71 (A-M02): what changed in the samples

Two things: `footer.fieldsVersion` is 71, and `manualResults[M-02].measurements` is the raw of the NEW grid (contract section 7-ي ف-01..ف-03; user
approvals E18-E22 of 2026-10-15). Everything else in the samples is what fv70 had.

**PRODUCER:** the real `TouchGridRecorder`, `TouchGridLayout` and `DensitySelector`, then the real `MeasurementsJson` encode/decode and the production
serialization. **SCRIPTED** (every one is a line of `AppRequestPayloadGolden.kt` marked `SCRIPTED:`): (a) the platform's answers — `xdpi`, the display and
mode sizes, the insets, the cut-out, the corners, the API level (`TouchGridEnvironment`; the Android reader that fills it in the app is not run by a JVM
test); (b) the touch samples; (c) the two clocks (`elapsedRealtime` opens at 50 000 and `uptimeMillis` at 7 200 000 on purpose: they are never subtracted).

| sample | scripted phone | grid | the run | what it shows |
|---|---|---|---|---|
| 11 columns | S10-5G-like, API 30: display 1080 x 2280 in a mode of 1440 x 3040, xdpi 502 | surface 1080 x 2100, cells 90 x 87.5 px, 12 x 24 = 288 | one finger zigzags every row at 3 mm per 8 ms (624 samples): every cell lit, `auto_complete`, `pass`, `timeToFirstTouchMs` 1730, `elapsedMs` 4968; the finger still down (`open_at_end`) | `densitySource` `XDPI_MODE_SCALED` (the naive width 54.6 mm fails the 55-85 mm test, the scaled one, 72.9 mm, passes), `densityCheck` `[]`; `displayedSuspects` ABSENT (an automatic completion shows no marks); `roundedCornersState` and `resampledFlagState` `not_available_api`; no `absentFieldReasons`; no cut-out (`cutoutRectsPx` `[]`) |
| 13 columns | S21-Ultra-like, API 34: 1440 x 3200, xdpi 515, a punch-hole camera, rounded corners | surface 1440 x 2960, cells 120 x 123.3 px, 12 x 24; the hole covers cells 5 and 6 (`cutoutCells`) so `totalCells` is 286 | three strokes: rows 0-9 with a `gap_jump` (60 ms, 600 px) on row 4 and a `coord_jump` (8 ms, 500 px) on row 6, lifted at the left edge; rows 10-17, lifted at the left edge; a 190 px palm ended `cancel_flagged`. The button after 15 s: `user_marked_not_working`, `fail`; 208 required cells lit, 78 not | `displayedSuspects` = four marks (`gap_jump`, `coord_jump`, `edge_exit` twice); the cells across the two jumps are untouched (the segment is not filled over a jump); `roundedCornerRadiiPx` written, not the state |
| 16 columns | the same phone, but the display mode is unreadable and the unbuffered-dispatch request is refused | the same 12 x 24, 286 required | one finger scribbles along one row, 15 300 samples: the 15 000 cap is reached (`pathCap` `{15000, true, 300}`), 12 cells lit, the finger still down; «لم أختبر» after 130 s: `user_marked_not_tested`, `not_tested` | `absentFieldReasons` names `modePhysicalWidthPx`, `modePhysicalHeightPx` and `unbufferedDispatch` (each `read_failed:<text>`) and the keys are left out; with no mode there is one density candidate: `XDPI`; `displayedSuspects` `[]` (present: a button ended the run, the marks were shown, none seen) |

Not shown by a sample, proved by unit tests (`TouchGridRecorderTest`, `TouchGridReadingsTest`, `TouchGridLayoutTest`): the `api_below_29` reasons and a platform
that refuses; a run with no touch (`strokes` `[]`, `elapsedMs` and `timeToFirstTouchMs` ABSENT, the surface still written); a missing `touchMajorPx`
(`strokes.touchMajorPx` in `absentFieldReasons`); a non-finite sample dropped and counted; the density ambiguity cases.
Sizes (measured): the 16-column sample is 544 358 bytes; the real producer's 15 000 points encode to 443 706 bytes
(`TouchGridRecorderTest`, line `TOUCHGRID_RAW_BYTES_AT_15000_POINTS`), under the 900 000-byte report-raw guard.
The earlier tables below that name `fv70-...` files describe the fv70 samples (history).

### fv71 (M-02 «frame + X», 2026-10-21): what changed in the samples

User decision 2026-10-21 («M-02: استبدل الشبكة الحالية باختبار لمس من نوع «إطار + X»»): a run no longer requires every cell, it requires the FRAME (the cells of the first and last row
and column) and the two DIAGONALS (`TouchGridPattern`) less the cells the camera covers. The three samples were regenerated from the real producer in the same commit as the change (rule 39);
`footer.fieldsVersion` stays 71. What changed in `manualResults[M-02].measurements` (the "fv71 (A-M02)" table above describes the grid BEFORE this change — history, not edited):

- two keys are new: `gridPattern` = `"FRAME_AND_DIAGONALS"` and `notRequiredCells` (the cells the pattern leaves out and the camera does not cover; sorted indexes);
- `totalCells` = `gridCols x gridRows - |cutoutCells| - |notRequiredCells|` = the required cells; `touchedCells` counts REQUIRED cells that were lit; `untouchedCells` lists required cells not lit;
  `endReason` is `auto_complete` exactly when every required cell was lit; `strokes[]` is unchanged: every sample, the touches on the left-out cells included (rule 20).

| sample | grid | before (every cell required) | now (frame + X) |
|---|---|---|---|
| 11 columns | 12 x 24 = 288, no camera | `totalCells` 288, 288 lit, `untouchedCells` `[]` | `totalCells` 108 (frame 68 + diagonals 40), 108 lit, `untouchedCells` `[]`, `notRequiredCells` 180 cells, `auto_complete` |
| 13 columns | 12 x 24, the hole covers cells 5 and 6 (frame, top row) | 286 required, 208 lit, 78 untouched | 106 required (108 - the 2 camera cells), 75 lit, 31 untouched: the 2 required cells across the scripted jumps (row 4 column 9, row 6 column 8) and 29 in rows 18-23 (the palm lit one); `notRequiredCells` 180 |
| 16 columns | the same, the display mode unreadable | 286 required, 12 lit | 106 required, 4 lit: the strip along row 5 crosses 12 cells, 4 of them required (the frame's two, the diagonals' two) |

The values are the real producer's output; the scripted touches and platform answers are the ones of the A-M02 section above. The sha256 of the three files and their keys files are in the block below.

### fv71 (M-02 in the shape of the system's touch test, 2026-10-21): what changed in the samples

User order 2026-10-21: M-02 takes the shape of the touch test of the Samsung menu — square cells (about 18 columns, the count computed from the screen), a frame one cell thick, the two diagonals drawn from the
inner corner of the frame to the opposite one with no gap at the ends and no block of their own at the centre, no text over the cells, and an empty strip above the grid. The three samples were regenerated from
the real producer in the same commit as the change (rule 39); `footer.fieldsVersion` stays 71; **no key and no value is new** (`gridPattern` is still `"FRAME_AND_DIAGONALS"`, the keys of the previous section stay) —
what changed is the geometry the app writes, in `manualResults[M-02].measurements`:

- `cellTargetMm` is 4.0 (it was 6.0) and the cell is a square: `cellWidthPx` = `cellHeightPx`; `gridCols` x `gridRows` come from the window, the cell and the strip (the rows are the whole cells that fit under a strip of two cells);
- the touch surface is the grid's region of the window, not the window: `surfaceHeightPx` = `gridRows` x `cellHeightPx` and `surfaceOriginYPx` = the strip's height (the server places each sample in a cell in the surface's own frame);
  a touch in the strip would have a negative `y` (no sample of these three is in it: the unit tests carry that case);
- the diagonals are every cell the line between the inner corners of the frame passes through (`TouchGridPattern`, contract section 7-ي paragraph 2 of 2026-10-21), so the required cells are about twice as many;
- the scripted camera of the 13- and 16-column samples is a notch 80 x 226 px at the top centre reaching 90 px into the grid (SCRIPTED: `AppRequestPayloadGolden.s21Environment`): a real hole lies in the strip and takes no cell.

| sample | grid | before (frame + X, 6 mm cells on the whole window) | now |
|---|---|---|---|
| 11 columns | surface 1080 x 1980 at y 120 (window 1080 x 2100, strip 120 px), cells 60 x 60 px | 12 x 24, `totalCells` 108, 108 lit | 18 x 33 = 594, `totalCells` 188 (frame 98 + diagonals 90), 188 lit, `untouchedCells` `[]`, `notRequiredCells` 406, `auto_complete` |
| 13 columns | surface 1440 x 2800 at y 160 (window 1440 x 2960, strip 160 px), cells 80 x 80 px | 12 x 24, hole cells 5 and 6, 106 required, 75 lit, 31 untouched | 18 x 35 = 630, `cutoutCells` `[8, 9, 26, 27]`, `totalCells` 194, 97 lit, 97 untouched (the two across the scripted jumps, row 4 column 15 and row 6 column 14, and rows 18-34 but the palm's cell), `notRequiredCells` 432 |
| 16 columns | the same grid, the display mode unreadable | 106 required, 4 lit | `totalCells` 194, 6 lit: the strip along row 5 crosses 18 cells, 6 of them required (the frame's two and two cells of each diagonal) |

Checked apart from the code that wrote them (rule 38): `tools-m02-shape-reference.js` (the workspace's reference of the shape, which shares no step with `TouchGridPattern`) gives the same grid, cells and totals, and the server's own path reader
(`analyzeTouchPath` of neuralcheck-backend, read only) re-derives from each sample's raw strokes exactly the touched and untouched cells the app wrote: `cells.state = MATCH` on all three (`tools-m02-golden-crosscheck.js`).

### fv71 (A-VOL): what changed in the samples

`manualResults[M-09]` and `manualResults[M-10]` carry `measurements` — the raw of the volume-button run (contract section 7-ي ف-04; approvals E22 and
N71-04 / N71-05 of 2026-10-15). Before it both were hand-built with no raw (M-09 was a `warning` row; M-10 had none).

**PRODUCER:** the real `VolumeKeyRecorder` (and, for the dismissed run, the real `TouchGridRecorder.recordOnDismiss`), then the real `MeasurementsJson` encode/decode
and the production serialization. **SCRIPTED:** the key events (key code, down / up, event time on `uptimeMillis`, repeat, device id 5, source 257), the clock,
and the media stream's levels (the dialog's polling and its window tap are Android code, not run by a JVM test). `windowMs` is the time the window stayed;
a detection keeps the dialog open 600 ms for the key's `up`.

| sample | M-09 (expects key 24) | M-10 (expects key 25) |
|---|---|---|
| 11 columns | `pass`, `auto_detected`, `detectionSource` `key_event`: down at 2310 ms, up at 2410 ms, `holdMs` 100, `timeToFirstExpectedDownMs` 2310, levels 15 / 7 / 8, `windowMs` 2910 | `pass`, `auto_detected`, `detectionSource` `volume_level_change`: NO key event reached the dialog (`events` `[]`, which does not mean not pressed), the level moved 7 -> 6, `windowMs` 2460 |
| 13 columns | `pass`, `user_marked_working` (a claim): the technician pressed the WRONG key first (key 25: `otherKeyDownCount` 1, `expectedDownCount` 0), then «يعمل» at 9700 ms; no `detectionSource` | `fail`, `user_marked_not_working`: no event, levels 15 / 7 / 7, `windowMs` 12400 |
| 16 columns | `not_tested`, `user_marked_not_tested`: no event; the stream had no room for a mid level (max 1), so the three level keys are ABSENT | `not_tested`, `dismissed`: the wrong key (24) down at 1200 ms and up at 1290 ms, then back at 3000 ms; recorded because an event arrived, with the answer the test held (none: `not_tested`) |

Together the samples show every `endReason` the contract lists (`auto_detected`, `user_marked_working`, `user_marked_not_working`, `user_marked_not_tested`,
`dismissed`) and both `detectionSource` values. `timeToFirstExpectedDownMs` and `holdMs` are ABSENT whenever no expected key was pressed (rule 22).
Not shown (unit-tested, `VolumeKeyRecorderTest`): the 64-event cap with `eventsTotal` above it, a held key's repeats, the mute key, a non-volume key ignored.

### fv71 (A-P6-13): what changed in the samples

Three conditions of the scan are now declared (contract section 7-ي ف-05; approvals N71-06 to N71-08 of 2026-10-15): the power-saving state, the battery window the build applied,
and what came of holding the screen's brightness. **PRODUCER:** the real `PowerSaveReading` (over the scripted phone's answer), the real `ScanBatteryWindow` and the real
`BrightnessLock.lock` (over a scripted window), then the real writer, reader and builder and the production serialization. **SCRIPTED:** the phone's power answer and the window.

| sample | `header.powerSaveState` · `isPowerSaveMode` · root `powerSaveState` / `powerSaveMode` | `footer.batteryWindowPct` | `footer.brightnessLock` |
|---|---|---|---|
| 11 columns | `off` · `false` · `off` / `false` | `{minPct: 30, maxPct: 85}` | `{state: not_attempted:value_not_decided}` — what production writes today: no brightness is decided (PENDING), value and method are ABSENT |
| 13 columns | `off` · `false` · `off` / `false` | the same | `{state: applied, value: 0.5, method: window_attribute}` — the shape of a build that had decided on a half brightness (SCRIPTED value) |
| 16 columns | `read_failed` · NO `isPowerSaveMode` key · `read_failed` / NO `powerSaveMode` key — the scripted power service refused the read; a failure is no longer written as `false` | the same | `{state: apply_failed:the window refused the brightness attribute, value: 0.4, method: window_attribute}` |

The header's `isPowerSaveMode` of the 11- and 13-column samples was the REAL source's `false`; it now comes from `PowerSaveReading` (`off` ⇒ `false`). `powerSaveState` `on` is not in a sample:
a scan with the mode on is stopped before its file exists (unit-tested in `ConditionsWireTest`). A file stamped below 71 has none of the three keys and `fieldsNotInFile` names the two footer ones.

### Batch 1.0.12 (item 4, user decision 2026-10-13): L-02, SN-08 and SN-09 come from their real producers

No stamp change (fv70) and no new shape on the wire: every key below was already written by the app's code; the samples did not show them.
What differs from the samples as committed at `dc5e5ce`:
1. `functionalResults[L-02]` is the output of the real `GpsTests.testSatellites` over a SCRIPTED `LocationManager` on virtual time
   (`gpsSatellitesResult` in the generator; a real run needs a sky and 30 s). `measurements` now carries all nine keys the producer writes:
   `gpsProviderEnabled`, `gnssCallbackState`, `locationUpdatesState`, `satelliteStatusCallbacks`, `visibleSatellites`, `usedSatellites`,
   `locationUpdateCount`, `timeToFirstFixMs` and `timeToFirstFixState` (the hand-built row had four of them). SCRIPTED events: 31 satellite-status
   callbacks, the last of which (9 visible, 4 used) decides; the first fix at 8 123 ms; 22 location updates. `rawValue` is `"4 / 9"` — the real
   code's text; the hand-built row said `"4"` — and the status is `pass`. The state values shown are `registered`, `requested` and `measured`;
   the failure states (`registration_failed:<text>`, `request_failed:<text>`, `not_requested:gnss_callback_registration_failed`,
   `absent:no_first_fix_in_window`) are unit tests (`GpsTestsL02Test`), not samples.
2. `functionalResults[SN-08]` (step detector) and `[SN-09]` (step counter) are NEW rows: the real `SensorTests.testSensor` over a SCRIPTED
   `SensorManager` on virtual time (`stepSensorResult`), the device scripted as API 34 with ACTIVITY_RECOGNITION granted. `measurements` is
   `windowMs` (3000) and `readings[]` of `{t, values}` (`t` in ms since the listener was registered, `values` as the sensor gave them).
   SCRIPTED events: SN-08 three steps at 700, 1450 and 2200 ms; SN-09 the counter's running total, 4021 at 900 ms and 4024 at 2300 ms. The
   status (`pass`), `rawValue` and `notes` are what the real code computes. The "no readings" case (an empty list, status `warning`) and the
   permission-denied case (`not_tested`, no `measurements`) are unit tests (`SensorTestsStepSensorsTest`), not samples.
3. The three samples carry the same three rows: 15 `functionalResults` rows (13 before). Nothing else changed — only `functionalResults` differs
   from the `dc5e5ce` samples, and only in L-02, SN-08 and SN-09.
4. `AppRequestPayloadGoldenTest` has a new guard (`every key a real producer writes into the measurements of L-02, SN-08 and SN-09 appears
   in a sample row of that test…`): the key paths each real producer can write — read off its own output (`L02Measurements.build` and
   `providerOnly`, `SensorTests.stepSensorMeasurements`) — must appear in a committed sample row of that test, and no such row may show a key the
   producer does not write. Before it, the guard walked the keys of the builder only, so a row hand-built from four numbers passed. Run against
   the samples as committed at `dc5e5ce` it fails and names `gnssCallbackState, gpsProviderEnabled, locationUpdateCount, locationUpdatesState,
   timeToFirstFixState` as missing from L-02 and the absent SN-08 / SN-09 rows. `device.build*` stays declared absent (`Build.*` are null in a JVM test).

sha256, before and after (the "after" column is also the block at the end of this file, which the regeneration rewrites):

| file (after the prefix `fv70-app-request-payload-derived-from-fv67-golden-34dff2b8-`) | sha256 before (`dc5e5ce`) | sha256 after |
|---|---|---|
| `11ch-nothing-admitted.json` | `6d5e06fb0275e4174412f04bef66e312ba6d614e18e498d72d663f912d38d538` | `1f85392014ecd79c0d2bd89b4985c36f0c785c0471647204b3434db46db4dadb` |
| `11ch-nothing-admitted.keys.txt` | `a1eaa4810d703b7a8f4aff6d5b61548717b1483ea90068a7db36968aaea08742` | `0dc19ce36290f17fa5b528ca6968fcca6eec4dac97aa9446083c591059d589b3` |
| `13ch-selinux-blocked-policy.json` | `3ccfd1d377dbb80bb32e385df2fbf0a87fb9e57bcec82fb6fbe958c55f329ff3` | `a46f60f163d3ed71c14d82d9ff86df77304de4d81cec96c3b6f59dee5b525e92` |
| `13ch-selinux-blocked-policy.keys.txt` | `096bbe4b1b0fa451a4bd8cf68b64a266434132b967e68e39d30041eb849c214c` | `1634ec58f3b4cb3ab0799ac64047a4f0076635e96de587ac6c244c4f27113c32` |
| `16ch-four-policies-truncated-failures.json` | `c413f9820b2a39b524750c91cdb1fe5fff2dc11867b0ad502b717784d9cd88cd` | `841dbd4ff0fdce3a7392d862cd094bafbeb5c588e5550c9af2635f410b85cb9e` |
| `16ch-four-policies-truncated-failures.keys.txt` | `32a8c4e2f282c448e2e987195de6c8a5e986f78933a3fe4cd9c5b2656ed8e02d` | `2e5f17855a5a252a4521c836f648a7d7b5e2240ce19fb9269f2a81e08863285a` |

### Footer keys assembled BY HAND (what `StressTestService.completeScan` builds from live state)

JVM stage 1 cannot run `DisciplinedSampler` or `completeScan`, so these `footer` keys come from a hand-built `TimeSeriesFooter(...)` in the
generator; if `completeScan` drifts from it, only the device stage (below) shows it. "from" says where the VALUE comes from.

| key | assembled by | from |
|---|---|---|
| `totalSamples` | HAND | the rows written (1606; 959 in the stopped sample) |
| `endTimestamp` | HAND | REAL; scripted in the stopped sample |
| `isComplete` | HAND | REAL; false in the stopped sample |
| `earlyStopReason` | HAND | REAL (absent); `user_cancelled` in the stopped sample |
| `batteryLevelEnd` | HAND | REAL: the last row's `v[4]` |
| `maxBatteryTemp` | HAND | REAL: the maximum of `v[1]` over the rows |
| `thermalHeadroomAvg` | HAND | REAL (a transitional aggregate; not recomputed from the list) |
| `thermalHeadroomPeak` | HAND | REAL (same) |
| `thermalStatusMax` | HAND | REAL (same) |
| `tempSource` | HAND | REAL |
| `intensityFactor` | HAND | REAL (a declared constant) |
| `deviceTierMeasured` | HAND | REAL (a declared constant) |
| `tempRiseRateInProbe` | HAND | REAL |
| `batteryCurrentScalingApplied` | HAND | REAL |
| `throttlingEvents` | HAND | REAL (empty list); one scripted event in the 13-column sample |
| `verificationResults` | HAND (`VerificationSummary(...)`) | REAL for the GPU hash counts, render times, `memoryTestedBytes`, the AI counters, `gpuFenceTimeoutCount`, `storageBytesWritten`, `storageWriteDurationMs`, the storage segments (unit converted); the rest by `MemoryVerifyOutcome.fromNative`, `StorageVerifyOutcome.fromNative`, `StorageWriteErrors.count`, `GpuGemmOutcome.derive`, `CriticalDefectOutcome.derive`, `WorkersPinOutcome.stateOf` and the real `CpuKnownAnswerScheduler` (virtual time), over scripted native arrays |
| `phaseCompletionStatus` | HAND | REAL; scripted in the stopped sample |
| `stressEffectiveness` | HAND | REAL, with the scripted overrides above |
| `batteryHealth` | HAND (`BatteryIndicators.footerMap`) | `BatteryIndicators.compute` over the real rows; the `BatteryHealthSnapshot` is hand-built from the real footer's values and scripted states, except its cycle-count fields, which are `BatteryCycleCount.read` (the real reader) over a MOCKED `Intent` and a scripted sysfs answer |
| `dataQuality` | HAND | REAL; `CpuUsageProvenance` override in the 11-column sample |
| `knownCapacityClassification` | HAND | REAL |
| `footerDiagnostics` | HAND | REAL (8 keys) plus `channelReadFailures`, `thermalLists`, `batteryPairs` set to `ok` (scripted) |
| `channelDiscovery` | HAND | `ChannelDiscoveryProbe.assemble` over scripted per-channel outcomes (the real file's classification) and scripted sysfs |
| `thermalSensorAvailability` | HAND | `ThermalSensorAvailability.fromDiscovery` |
| `scanStillness` | HAND | `StillnessClassifier.report` over the real `accelStdDev` and `sampleCount` |
| `unmeasuredDefaults` | HAND | `UnmeasuredDefaults.forScan(true)` |
| `expectedSamples` | HAND | `completeScan`'s own formula (sum of phase seconds x Hz = 1605) |
| `flushFailureCount` | HAND | `TimeSeriesFileWriter.getFlushFailureCount()` |
| `cpuPolicies` | HAND | `CpuPolicyDiscovery.discover` over scripted sysfs |
| `channelReadFailures` | HAND | `ExtraChannelSampler.failures()` |
| `channelReadFailureCounts` | HAND | `ExtraChannelSampler.failures()` |
| `thermalHeadroomReadings` | HAND | the real `ThermalListCollector` over a scripted `ThermalPlatform` |
| `thermalHeadroomState` | HAND | same |
| `thermalStatusEvents` | HAND | same |
| `thermalStatusState` | HAND | same |
| `batteryPairs` | HAND | the real `BatteryPairRecorder` over scripted broadcasts |
| `batteryPairsState` | HAND | same |
| `transitionalAggregates` | HAND | `TransitionalAggregates.NAMES` |
| `cpuTempFinalC` | HAND | `StressTestService.selectFooterCpuTemp` (real inputs in the 11-column sample: absent; scripted in the others) |
| `cpuTempFinalSource` | HAND | same |
| `preScanWarningsState` | HAND | `PreScanWarningsStates.of` over a scripted `Result` |
| `batteryWindowPct` | HAND | `ScanBatteryWindow` (the real object: `{minPct: 30, maxPct: 85}`) |
| `brightnessLock` | HAND | the real `BrightnessLock.lock` over a scripted window (`not_attempted`, `applied`, `apply_failed`) |
| `fieldsVersion` | HAND | `BuildConfig.FIELDS_VERSION` |
| `samplesSha256` | WRITER | `TimeSeriesFileWriter.finalize` |
| `fieldsNotInFile` | BUILDER | `BehaviorFingerprintBuilder` |

File-only keys the generator also hands the writer (never on the wire): `phaseBoundaries` (with the `memory_kernel` and `memory_workload`
windows), `preScanWarnings`, `maxCpuTemp` (null).

### Keys the JVM stage cannot show (declared absent, with the reason, in `AppRequestPayloadGoldenTest.declaredAbsent`)

`device.buildFingerprint`, `device.buildBoard`, `device.buildHardware`, `device.buildBootloader` (`android.os.Build.*` are null in a JVM test);
`timeSeries.footer.maxCpuTemp` (written only from a pre-68 file); `phase3CpuFreqRatioMid` and `phase3CpuFreqRatioLittle`
(always null in the V5 schema). The guard fails if a key the builder can write is in no sample and not declared.

### Regeneration (rule 39: in the SAME commit as any contract change)

```
( cd C:/Users/Efendi.Tec/NeuralChek && export JAVA_HOME="C:\Program Files\Android\Android Studio\jbr" \
  && export PATH="/c/Program Files/Android/Android Studio/jbr/bin:$PATH" && export NC_REGENERATE_GOLDEN=1 \
  && ./gradlew --no-daemon cleanTestLaunchDebugUnitTest testLaunchDebugUnitTest \
     --tests net.neuralcheck.files.AppRequestPayloadGoldenTest )
```

It rewrites the three samples, their keys files and the sha256 block below; without `NC_REGENERATE_GOLDEN=1` the same test compares the
committed files with what the chain builds today, path by path, and fails on any difference.

### Copy and stage 2

- Copied to `neuralcheck-backend` ONLY, byte for byte, by the backend agent after the sha256 below is checked. NOT to `wepsite`
  (user decision 2026-10-08: the website reads no field of the app payload).
- Stage 2, on a device (contract section 3 item 7): a real `.ts.json.gz` from a scan on an fv71 build, copied UNMODIFIED to
  `app/src/test/resources/contract/`, goes through the same chain and a test compares its key set with these samples'
  (`*.keys.txt`); a key in one and not the other fails. Until then rule 41 is not satisfied for the hand-built footer keys above.

### sha256 of the committed files (rewritten by the regeneration, checked by the test)

<!-- golden-sha256:begin -->
- `fv71-app-request-payload-derived-from-fv67-golden-34dff2b8-11ch-nothing-admitted.json` sha256 `ffaa4c0eb639436f81fac7154046b0e1d23abd78e270db961459010adc5e4268`
- `fv71-app-request-payload-derived-from-fv67-golden-34dff2b8-11ch-nothing-admitted.keys.txt` sha256 `41115aafd3cedd2f5a4dc8d5f6c8b99edefdb8f80a12cb9ed3a417195739775e`
- `fv71-app-request-payload-derived-from-fv67-golden-34dff2b8-13ch-selinux-blocked-policy.json` sha256 `76bd343269d75fe024390a8a9a5b2c84ec79fc4c08ad1e752ce7f64b36b9b335`
- `fv71-app-request-payload-derived-from-fv67-golden-34dff2b8-13ch-selinux-blocked-policy.keys.txt` sha256 `98f7884e8adaeff66ad63bce8dc24026f389d942a169e18ece07ecc1b1931b5e`
- `fv71-app-request-payload-derived-from-fv67-golden-34dff2b8-16ch-four-policies-truncated-failures.json` sha256 `82c11219cea5e917a9693bd85e455e65a84f1928c370779bca1d57f81247106a`
- `fv71-app-request-payload-derived-from-fv67-golden-34dff2b8-16ch-four-policies-truncated-failures.keys.txt` sha256 `4765ebae1fe675ea164f796b2e607ab3be0219ffce1ac6dc95bc4e519ff2f3a3`
<!-- golden-sha256:end -->

## technician-form request samples (A-P6-08, 2026-10-18)

Three golden samples of what the app sends to the `submitTechnicianForm` callable (the technician's form), made by the real `TechnicianFormRequest.build` from a scripted form and compared BYTE FOR BYTE by
`TechnicianFormRequestTest` (regenerate with `NC_REGENERATE_GOLDEN=1`, in the same commit as any change to the request - rule 39). They are for the server's reader (rule 41) and are copied byte for byte
(`-text` in `.gitattributes`). They are NOT the analysis payload: `fieldsVersion` is not involved, and the `fv71-app-request-payload-derived-from-*` samples above are unchanged by this row.

Wire match, 2026-10-18 (A-P6-08): until then several names were this app's own choice ([APP]) - the protocol contract copy (A5) names the callable and the values but not those keys - and three of them were not the
server's. They were matched to the server's reader (neuralcheck-backend@05275ba, `technicianFormContract.js`, as copied in `evidence/run-2026-10-18/technician-form-server-wire-neuralcheck-backend-05275ba.md`;
rule 29: a copy, it drifts): the defects container is `defects` (was `visibleDefects`), `perAnswerAt` is a plain array of device-clock milliseconds (was `{item, at}` entries - the item each time belongs to
stays on the phone), and the two `promptAction` values are `confirmed_send` / `will_review` (were `confirmed` / `revise`). `sentOffline` is not sent: the app has no offline send. What is NOT checked against
the server's own file is the enumerated VALUES (`protocolVocabulary.js` is not in the copy): the grade, row, defect, symptom and battery values are those of contract A5, pinned by `TechnicianFormModelTest`.
Every item the technician did not answer is an explicit JSON null; nothing the server decides (`fast`, `allFourGradesEqual`, blindness, `receivedAt`, `formState`) is in the request. Times are the device clock's, epoch milliseconds.

| file | what it shows |
|---|---|
| `technician-form-request-all-answered-no-prompt.json` | every grade, functional row and defect row answered, `none_noticed`, a battery percentage, `gameTried: false`, no note, no prompt (the three prompt keys are null) |
| `technician-form-request-partial-explicit-nulls.json` | two grades (one `not_tested`), one functional row, a symptom in a server-chosen order, the known-fault chip, a battery state, a note; everything else an explicit null |
| `technician-form-request-contradiction-revised.json` | overall good with a weak battery: the prompt was shown, the technician chose «سأراجع» (`will_review`) and lowered overall to fair - `answersBeforePrompt` and `answersAfterPrompt` differ |

<!-- technician-form-sha256:begin -->
- `technician-form-request-all-answered-no-prompt.json` sha256 `1160a728cc95dd4b09e1a0d67be47089b1d74362c5196fc5f44238f5c42d1083`
- `technician-form-request-partial-explicit-nulls.json` sha256 `183ba711e09b8dcc8d0005f7b11062d442946742c6c2fac22e9a1a865ad49124`
- `technician-form-request-contradiction-revised.json` sha256 `6fa603ec70087515a8d0ff9660bf8dcc74db94f11e4a991e8f9c6443e60609d3`
<!-- technician-form-sha256:end -->

## maintenance-pair request samples (order of 2026-10-22)

Five golden samples of what the app sends for the maintenance before/after link: `setSessionPurpose` with its fault type (two maintenance purposes, and `normal` with NO `defectType` key), `listOpenMaintenanceBefores` and
`confirmMaintenanceAfterLink`. Made by the real builders (`SessionPurposeWire.request`, `PairLinkWire.listRequest`, `PairLinkWire.confirmRequest` - the functions `FirebaseAnalysisService` hands to the SDK) and compared
BYTE FOR BYTE by `MaintenancePairRequestTest` (regenerate with `NC_REGENERATE_GOLDEN=1`, in the same commit as any change to the wire - rule 39). `MaintenancePairRequestRealPathTest` proves the SDK receives exactly these
requests from the real service. They are for the server's reader (rule 41), copied byte for byte (`-text` in `.gitattributes`) to `neuralcheck-backend` BY ITS AGENT - this repository writes nothing there - and NOT to
`wepsite`. They are NOT the analysis payload: `fieldsVersion` is not involved. The ids are made-up (`sess-golden-*`, `scan-golden-before-1`); the contract they follow is the copy
`app/src/test/resources/contract/protocol-contract-A2-A3-maintenance-pairs-neuralcheck-backend-c289a2d.md` (neuralcheck-backend@f3c1bab, a copy: it drifts), and the test compares their values with its vocabulary table.

| file | what it shows |
|---|---|
| `maintenance-pair-request-set-session-purpose-maintenance-before-battery.json` | «before maintenance» with the fault type BATTERY: `{sessionId, purpose: maintenance_before, defectType: BATTERY}` |
| `maintenance-pair-request-set-session-purpose-maintenance-after-charging-port.json` | «after maintenance» with CHARGING_PORT |
| `maintenance-pair-request-set-session-purpose-normal-no-defect-type.json` | `normal`: the `defectType` key is ABSENT, not null (the server counts a key that is there as sent: `DEFECT_TYPE_NOT_APPLICABLE`) |
| `maintenance-pair-request-list-open-maintenance-befores.json` | the list asked for an «after» session: `{sessionId}` and nothing else |
| `maintenance-pair-request-confirm-maintenance-after-link.json` | the owner's choice: `{sessionId, beforeScanId}` |

<!-- maintenance-pair-sha256:begin -->
- `maintenance-pair-request-set-session-purpose-maintenance-before-battery.json` sha256 `cc0589180dc89bdc4ddba094e9ce03d6c76364b5a0e61b871195758f37c1683e`
- `maintenance-pair-request-set-session-purpose-maintenance-after-charging-port.json` sha256 `1cd639ca23ee64198e2042f9e53282da5ef13420a213f684f03730c23bb684bb`
- `maintenance-pair-request-set-session-purpose-normal-no-defect-type.json` sha256 `04ab287ab80679ffc32e79324d2db729607d5b51e1a595cdf5fbc16078e74bcb`
- `maintenance-pair-request-list-open-maintenance-befores.json` sha256 `661fdbabc87f26d7ab15f0222252ff7fcc24e386118f90eedd3ce5ee090cf2f7`
- `maintenance-pair-request-confirm-maintenance-after-link.json` sha256 `4481f33e1df3c384c58e3ffbc765c2827998f0e502a7f5037f574ad9e45375d1`
<!-- maintenance-pair-sha256:end -->
