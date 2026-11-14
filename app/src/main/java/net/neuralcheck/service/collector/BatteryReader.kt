package net.neuralcheck.service.collector

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import net.neuralcheck.data.local.files.BatteryPairsRecord
import net.neuralcheck.util.ChannelRead
import net.neuralcheck.util.ReasonText
import kotlin.math.abs

/**
 * Battery data reader using BroadcastReceiver + BatteryManager direct reads (ADR 4.5).
 *
 * Bug 2 fix: BATTERY_PROPERTY_CURRENT_NOW is documented to return µA with negative
 * sign for discharge. In practice (per AccuBattery's published research over 8+
 * years across hundreds of device models) only some HTC devices follow that
 * standard. Samsung returns mA AND inverts the sign. Xiaomi/Oppo/Honor variants
 * use mA without sign inversion. Some return zero. All combinations are seen
 * in the wild.
 *
 * This class implements per-session scaling+sign auto-detection so battery_current_ua
 * samples are normalised to the AOSP-spec convention (µA, negative=discharge)
 * regardless of vendor. The detected scaling is exposed via [getCurrentScalingApplied]
 * for footer transparency.
 */
class BatteryReader(private val context: Context) {

    companion object {
        private const val TAG = "NeuralCheck"

        // V2 fix #56 (2.2): tightened thresholds + multi-evidence locking.
        // The grey zone between MA and UA was responsible for never-locking
        // sessions on Samsung S24 Ultra under stress (raw 1500-3500 mA).
        // Old: UA=100k, MA=1k → grey zone 1000-100,000 left default heuristic.
        // New: UA=50k, MA=5k → grey zone 5,000-50,000 still requires multi-
        //       evidence to disambiguate, but covers more decisive readings.
        private const val UA_LOCK_THRESHOLD = 50_000
        private const val MA_LOCK_THRESHOLD = 5_000

        // V2 fix #56 (2.2): require N consistent decisive readings before
        // locking, so a single transient blip can't lock to the wrong format.
        private const val LOCK_EVIDENCE_THRESHOLD = 3

        // V2 fix #56 (2.2): forced fallback if no decisive reading arrives
        // after this many samples. Picks the manufacturer-default heuristic
        // and locks it as a "best guess" so AI sees a deterministic source.
        private const val FORCED_LOCK_AFTER_SAMPLES = 30

        // V2 fix #56 (1.2): expanded vendor list. Was 11 prefixes; now
        // covers the major OEMs in MEA/SEA markets where the app sees
        // most usage. The list is a default heuristic only — data-driven
        // multi-evidence locking overrides it as soon as raw readings
        // disambiguate.
        private val MA_FORMAT_VENDOR_PREFIXES = listOf(
            // existing
            "samsung", "xiaomi", "redmi", "poco", "oppo", "realme",
            "oneplus", "honor", "huawei", "vivo", "iqoo",
            // V2 fix #56 additions
            "motorola", "moto",        // Moto G/E
            "lge", "lg",               // LG legacy
            "nokia", "hmd",            // Nokia / HMD Global
            "tcl", "alcatel",          // TCL group
            "infinix", "tecno", "itel", // Transsion
            "asus", "rog",             // ASUS / ROG Phone
            "lenovo",
            "sony",                    // Sony Xperia
            "fairphone"                // Fairphone
        )
    }

    private val batteryManager: BatteryManager? = try {
        context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
    } catch (t: Throwable) {
        Log.w(TAG, "BATTERY_SERVICE unavailable: ${t.message}")
        null
    }
    private var lastBatteryIntent: Intent? = null
    private var isRegistered = false

    // ─────── Scaling/sign detection state ────────────────────────────
    // Once locked, scaleMultiplier and signFlip are applied to every
    // subsequent reading. Volatile because snapshot() may be called
    // from a different thread than register().
    @Volatile private var scaleMultiplier: Int = 1
    @Volatile private var signFlip: Int = 1
    @Volatile private var scalingLocked: Boolean = false
    @Volatile private var lockSource: String = "unresolved"

    // V2 fix #56 (2.2): evidence counters for multi-evidence locking.
    @Volatile private var uaEvidenceCount: Int = 0
    @Volatile private var maEvidenceCount: Int = 0
    @Volatile private var sampleCount: Int = 0
    @Volatile private var initialProbeFailed: Boolean = false

    // fieldsVersion 65 — measurement-integrity telemetry:
    /** sampleCount value at the moment the scaling lock committed. -1 =
     *  never locked. Lets the AI re-interpret samples that PRECEDED the
     *  lock (they were normalised with the soft default heuristic). */
    @Volatile private var lockAtSampleIndex: Int = -1
    /** Count of Int.MIN_VALUE CURRENT_NOW readings filtered mid-scan.
     *  Each one was surfaced downstream as the 0 "no data" sentinel
     *  instead of leaking ±2.1e9 µA into v[3]/v[7]. */
    @Volatile private var invalidCurrentReadings: Int = 0
    /** Count of EXTRA_VOLTAGE readings detected as µV and divided by
     *  1000 (see [BatteryChannelSanitizer.VOLTAGE_UV_THRESHOLD]). */
    @Volatile private var voltageUvConvertedCount: Int = 0

    /**
     * fieldsVersion 68 (ت-06): the raw pairs taken at each battery broadcast,
     * for the scan that [startBatteryPairs] began. Idle (and free) otherwise.
     */
    private val pairRecorder = BatteryPairRecorder()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            lastBatteryIntent = intent
            if (intent != null) recordPair(intent)
        }
    }

    /**
     * fieldsVersion 68 (ت-06): at a battery broadcast, read the raw pair. The
     * two property reads here are SEPARATE from [snapshot]'s: they do not feed
     * the scaling-lock evidence counters (`uaEvidenceCount` /
     * `maEvidenceCount`) nor `invalidCurrentReadings` — those must only
     * count the sampler's own reads, or a broadcast-time read would shift the
     * lock decision (the v65 comment at [normaliseCurrent] describes that
     * failure). Nothing here may throw: this runs inside a BroadcastReceiver.
     */
    private fun recordPair(intent: Intent) {
        if (!pairRecorder.isRecording()) return
        try {
            val bm = batteryManager
            val inputs = BatteryPairReads.fromIntent(
                intent = intent,
                readCurrentNow = {
                    (bm ?: throw IllegalStateException("BatteryManager unavailable"))
                        .getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
                },
                readChargeCounter = {
                    (bm ?: throw IllegalStateException("BatteryManager unavailable"))
                        .getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
                }
            )
            pairRecorder.onBroadcast(SystemClock.elapsedRealtime(), inputs)
        } catch (t: Throwable) {
            Log.w(TAG, "recordPair failed: ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    /**
     * fieldsVersion 68 (ت-06): starts recording the raw battery pairs for a scan
     * whose `v[0]` clock started at [scanStartElapsedMs]. Call after
     * [register]; a receiver that failed to register is reported by
     * [finishBatteryPairs], not hidden.
     */
    fun startBatteryPairs(scanStartElapsedMs: Long) = pairRecorder.start(scanStartElapsedMs)

    /**
     * Ends the recording and returns the pairs with their explicit state, or
     * `null` when no recording was started. Call BEFORE [unregister]: the
     * state's `receiverRegistered` is read here.
     */
    fun finishBatteryPairs(): BatteryPairsRecord? = pairRecorder.finish(receiverRegistered = isRegistered)

    /**
     * V2 fix #56 (2.1): register lifecycle hardened.
     *  1. Apply default heuristic FIRST (so even if the receiver registration
     *     fails, the reader has a sane fallback for callers).
     *  2. Register the BroadcastReceiver.
     *  3. Sanity-probe BatteryManager.BATTERY_PROPERTY_CURRENT_NOW once.
     *     If the probe returns INT_MIN or 0, BatteryManager is stale —
     *     we tag lockSource accordingly so AI can downweight the data.
     *
     * Returns true on success, false when BatteryManager is unavailable
     * or the receiver registration throws. False return means the reader
     * still works in a degraded mode (default heuristic only) — callers
     * should treat false as "data quality reduced" rather than "abort".
     */
    fun register(): Boolean {
        if (isRegistered) return true

        // 1. Default heuristic FIRST so any failure below still leaves
        // the reader producing reasonable values for its callers.
        applyDefaultHeuristic()

        if (batteryManager == null) {
            lockSource = "register_failed_no_battery_manager"
            Log.e(TAG, "register: BatteryManager unavailable on this device")
            return false
        }

        return try {
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            lastBatteryIntent = context.registerReceiver(receiver, filter)
            isRegistered = true

            // 3. Sanity probe — confirm BatteryManager is live before any
            // sampler reads from us. INT_MIN/0 means the IPC succeeded but
            // the battery service hasn't populated the property yet (rare
            // on cold boot, more common on emulators).
            val probe = try {
                batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            } catch (_: Throwable) { Int.MIN_VALUE }

            if (probe == Int.MIN_VALUE || probe == 0) {
                Log.w(TAG, "register: initial CURRENT_NOW probe = $probe, " +
                    "BatteryManager may be stale; tag lockSource for AI awareness")
                initialProbeFailed = true
                lockSource = "${lockSource}_probe_$probe"
            } else {
                Log.i(TAG, "register: probe OK (CURRENT_NOW=$probe), source=$lockSource " +
                    "scale=$scaleMultiplier flip=$signFlip")
            }
            true
        } catch (e: Exception) {
            isRegistered = false
            lockSource = "register_failed_${e.javaClass.simpleName}"
            Log.e(TAG, "register failed: ${e.message}", e)
            false
        }
    }

    fun unregister() {
        if (!isRegistered) return
        try {
            context.unregisterReceiver(receiver)
            isRegistered = false
        } catch (e: Exception) {
            Log.w(TAG, "Error unregistering battery receiver: ${e.message}")
        }
    }

    private fun applyDefaultHeuristic() {
        val manufacturer = (Build.MANUFACTURER ?: "").lowercase()
        val isMaFormatVendor = MA_FORMAT_VENDOR_PREFIXES.any { manufacturer.contains(it) }
        scaleMultiplier = if (isMaFormatVendor) 1000 else 1
        signFlip = if (manufacturer.contains("samsung")) -1 else 1
        scalingLocked = false  // soft default — overridden by data when decisive
        lockSource = "default_$manufacturer"
        Log.i(TAG, "BatteryReader default heuristic: manufacturer=$manufacturer " +
            "scale=$scaleMultiplier signFlip=$signFlip")
    }

    /**
     * V2 fix #56 (2.2): multi-evidence scaling lock.
     *
     * Old behaviour: a single decisive raw reading (|raw| > UA_THRESHOLD or
     * < MA_THRESHOLD) immediately locked the scale. Problem: a transient
     * blip (e.g. wakelock spike on a charging device) could lock the wrong
     * format for the rest of the run.
     *
     * New behaviour: each decisive reading increments the corresponding
     * counter (uaEvidenceCount or maEvidenceCount). Lock fires only when
     * one counter reaches LOCK_EVIDENCE_THRESHOLD. Grey-zone readings
     * touch neither counter — they wait for evidence to accumulate.
     *
     * If neither counter reaches the threshold after FORCED_LOCK_AFTER_SAMPLES
     * total samples, the heuristic's manufacturer-default is "forced-locked"
     * so AI sees a deterministic source rather than perpetual "default_…".
     */
    private fun tryLockFromReading(raw: Int) {
        if (scalingLocked) return
        if (raw == 0) return  // zero is not informative

        val absRaw = abs(raw)
        when {
            absRaw > UA_LOCK_THRESHOLD -> {
                uaEvidenceCount++
                if (uaEvidenceCount >= LOCK_EVIDENCE_THRESHOLD) {
                    finalizeLock(
                        newScale = 1,
                        raw = raw,
                        source = "data_ua_format_evidence_$uaEvidenceCount"
                    )
                }
            }
            absRaw < MA_LOCK_THRESHOLD -> {
                maEvidenceCount++
                if (maEvidenceCount >= LOCK_EVIDENCE_THRESHOLD) {
                    finalizeLock(
                        newScale = 1000,
                        raw = raw,
                        source = "data_ma_format_evidence_$maEvidenceCount"
                    )
                }
            }
            // grey zone — neither counter advances; wait for clearer evidence
        }
    }

    /** V2 fix #56 (2.2): commits scale/sign with sign inference from raw + charging state. */
    private fun finalizeLock(newScale: Int, raw: Int, source: String) {
        // Determine sign: discharging device under AOSP convention reads
        // negative. If charging state is unknown (intent not received yet),
        // we keep the existing signFlip (set by default heuristic).
        val charging = isCharging()
        val newSignFlip = when {
            !charging && raw > 0 -> -1   // discharging but reads positive → vendor inverts
            !charging && raw < 0 -> 1    // discharging and reads negative → AOSP-compliant
            charging && raw < 0 -> -1    // charging but reads negative → vendor inverts
            charging && raw > 0 -> 1     // charging and reads positive → AOSP-compliant
            else -> signFlip
        }

        scaleMultiplier = newScale
        signFlip = newSignFlip
        scalingLocked = true
        lockSource = source
        // fieldsVersion 65: remember WHEN the lock happened so samples
        // before this index can be re-interpreted by the AI/training side.
        lockAtSampleIndex = sampleCount
        Log.i(TAG, "BatteryReader scaling LOCKED: raw=$raw scale=$newScale " +
            "signFlip=$newSignFlip source=$source charging=$charging " +
            "evidence(ua=$uaEvidenceCount,ma=$maEvidenceCount) atSample=$lockAtSampleIndex")
    }

    /**
     * V2 fix #56 (2.2): forced fallback when no decisive reading arrives.
     * After FORCED_LOCK_AFTER_SAMPLES, we lock to the manufacturer default
     * heuristic. Better deterministic-but-possibly-wrong than perpetually
     * "default_<vendor>" with no commitment.
     */
    private fun maybeForceLockAfterSamples() {
        if (scalingLocked) return
        if (sampleCount < FORCED_LOCK_AFTER_SAMPLES) return
        // applyDefaultHeuristic already set scale/flip to manufacturer default.
        // Just promote the soft default to a hard lock with a tagged source.
        scalingLocked = true
        lockAtSampleIndex = sampleCount  // fieldsVersion 65
        lockSource = "forced_after_${sampleCount}_samples_${lockSource}"
        Log.w(TAG, "BatteryReader scaling FORCED-LOCKED to default: $lockSource " +
            "scale=$scaleMultiplier flip=$signFlip " +
            "(no decisive reading after $FORCED_LOCK_AFTER_SAMPLES samples)")
    }

    /**
     * Apply the active scale + sign to a raw reading and return the
     * normalised value in µA with AOSP-compliant sign (negative=discharge).
     * Returns 0 unchanged so downstream "no data" handling is preserved.
     */
    private fun normaliseCurrent(raw: Int): Int {
        if (raw == 0) return 0
        // fieldsVersion 65 — belt-and-braces: the platform failure sentinel
        // must never reach the evidence counters. abs(Int.MIN_VALUE) stays
        // negative, so before this guard an invalid reading was counted as
        // mA-format evidence (absRaw < MA_LOCK_THRESHOLD) and could commit
        // a wrong 1000× lock for the rest of the scan. Callers filter via
        // BatteryChannelSanitizer first; this guard covers future callers.
        if (BatteryChannelSanitizer.isInvalidCurrent(raw)) {
            invalidCurrentReadings++
            return 0
        }
        // V2 fix #56 (2.2): observe the sample first, then normalise.
        // Counts toward sampleCount even when we can't lock (so forced
        // fallback fires after the right number of samples).
        sampleCount++
        tryLockFromReading(raw)
        maybeForceLockAfterSamples()
        // Cast to Long to avoid overflow when scale=1000 and raw is large.
        val scaled = raw.toLong() * scaleMultiplier.toLong() * signFlip.toLong()
        // Clamp to Int range; values above ~2.1 GA would saturate but
        // any realistic phone current stays well within ±5 A = ±5,000,000.
        return when {
            scaled > Int.MAX_VALUE -> Int.MAX_VALUE
            scaled < Int.MIN_VALUE -> Int.MIN_VALUE
            else -> scaled.toInt()
        }
    }

    /**
     * Returns the active scaling decision in a footer-friendly tag:
     *   "x1_no_flip"     — AOSP-compliant (HTC, Pixel)
     *   "x1000_no_flip"  — mA-format without sign inversion (Xiaomi, Oppo, Honor)
     *   "x1000_flip"     — Samsung-style (mA + inverted sign)
     *   "x1_flip"        — rare (sign inverted but units AOSP-compliant)
     *   "default_<vendor>" prefix when no decisive reading was ever seen
     */
    fun getCurrentScalingApplied(): String {
        if (!scalingLocked) return lockSource
        val scaleTag = if (scaleMultiplier == 1000) "x1000" else "x1"
        val flipTag = if (signFlip == -1) "flip" else "no_flip"
        return "${scaleTag}_${flipTag}"
    }

    /**
     * Returns instantaneous package power in milliwatts.
     *
     * fieldsVersion 65: now a thin view over [snapshot] — power is
     * computed from the SAME (voltage, current) read that feeds v[2]/v[3],
     * so v[7] is always reproducible as |v[2] × v[3]| / 1e6. The previous
     * implementation issued its own CURRENT_NOW read: an audit found ~3 %
     * of samples where v[7] could not be reproduced from v[2] × v[3]
     * because the two reads landed on different sides of a load change.
     * The double read also advanced the scaling-lock sampleCount twice
     * per tick, halving the forced-lock window.
     * Returns -1 when current is unavailable (existing convention).
     */
    fun readPackagePowerMw(): Float = snapshot().packagePowerMw

    /**
     * Returns current battery snapshot with all available data.
     * The currentUa field is normalised — scaled to µA and sign-corrected
     * to the AOSP convention (negative=discharge) regardless of vendor quirks.
     *
     * fieldsVersion 65 hardening (measurement integrity):
     *  - Int.MIN_VALUE CURRENT_NOW readings are filtered HERE (not just in
     *    the register() probe): they surface as the 0 "no data" sentinel,
     *    never reach the scaling-lock evidence counters, and are tallied
     *    in [invalidCurrentReadings] → footer dataQuality.
     *  - EXTRA_VOLTAGE is µV-detected: raw > 100 000 is divided by 1000
     *    and tallied in [voltageUvConvertedCount] → footer dataQuality.
     *  - [BatterySnapshot.packagePowerMw] is computed from this same
     *    single (voltage, current) pair — one read per tick feeds both
     *    v[3] and v[7].
     */
    fun snapshot(): BatterySnapshot {
        // V2 fix #56 (2.1): late-registration safeguard. If a caller
        // never invoked register(), warn loudly and attempt to recover.
        // Without this, a stress run could complete with scaleMultiplier=1
        // (Int default) on a Samsung mA-format device, producing 1000×
        // wrong battery_current_ua values.
        if (!isRegistered) {
            Log.e(TAG, "snapshot() called on unregistered reader — late register attempt")
            register()
        }
        val intent = lastBatteryIntent
        val rawCurrent = try {
            batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) ?: 0
        } catch (e: Exception) { 0 }
        // fieldsVersion 65: sanitise BEFORE normalisation so the failure
        // sentinel can't feed the evidence counters or the power product.
        val currentUa = if (BatteryChannelSanitizer.isInvalidCurrent(rawCurrent)) {
            invalidCurrentReadings++
            Log.w(TAG, "snapshot: CURRENT_NOW returned Int.MIN_VALUE " +
                "(invalid #$invalidCurrentReadings) — surfacing 0 (no data)")
            0
        } else {
            normaliseCurrent(rawCurrent)
        }
        val rawVoltage = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
        val voltage = BatteryChannelSanitizer.normaliseVoltageToMv(rawVoltage)
        if (voltage.uvConversionApplied) {
            voltageUvConvertedCount++
            if (voltageUvConvertedCount == 1) {
                Log.w(TAG, "snapshot: EXTRA_VOLTAGE=$rawVoltage detected as µV — " +
                    "dividing by 1000 (→ ${voltage.millivolts} mV) for this scan")
            }
        }
        return BatterySnapshot(
            tempCelsius = (intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f,
            voltageMv = voltage.millivolts,
            currentUa = currentUa,
            levelPercent = calculateLevel(),
            health = intent?.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN) ?: BatteryManager.BATTERY_HEALTH_UNKNOWN,
            isCharging = isCharging(),
            plugType = getPlugType(),
            technology = intent?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: "",
            status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN) ?: BatteryManager.BATTERY_STATUS_UNKNOWN,
            packagePowerMw = BatteryChannelSanitizer.computePackagePowerMw(
                voltageMv = voltage.millivolts,
                currentUa = currentUa
            )
        )
    }

    fun getLevelPercent(): Int = calculateLevel()

    fun getTempCelsius(): Float {
        return (lastBatteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f
    }

    fun isCharging(): Boolean {
        val status = lastBatteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        return status == BatteryManager.BATTERY_STATUS_CHARGING ||
               status == BatteryManager.BATTERY_STATUS_FULL
    }

    private fun calculateLevel(): Int {
        val intent = lastBatteryIntent ?: return -1
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        return if (level >= 0 && scale > 0) (level * 100) / scale else -1
    }

    private fun getPlugType(): String {
        return when (lastBatteryIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)) {
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_AC -> "AC"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
            else -> "None"
        }
    }

    /**
     * V2 fix #56 (1.3): full scaling diagnostic for the AI footer.
     *
     * Previously the footer carried only `getCurrentScalingApplied()` which
     * collapses many distinct states into a single string. With this struct
     * the AI sees:
     *   - whether register() was even called
     *   - whether scaling locked or stayed in default heuristic
     *   - how much evidence accumulated for the lock decision
     *   - the source tag (data_ua_format_evidence_3, default_samsung_probe_0,
     *     forced_after_30_samples_default_xiaomi, etc.)
     *
     * AI uses these flags to gate confidence in battery_current_ua samples:
     * un-locked + un-registered = downweight; locked via evidence = trust.
     */
    fun getScalingDiagnostic(): ScalingDiagnostic = ScalingDiagnostic(
        locked = scalingLocked,
        source = lockSource,
        scaleMultiplier = scaleMultiplier,
        signFlip = signFlip,
        uaEvidenceCount = uaEvidenceCount,
        maEvidenceCount = maEvidenceCount,
        sampleCount = sampleCount,
        isRegistered = isRegistered,
        initialProbeFailed = initialProbeFailed,
        lockAtSampleIndex = lockAtSampleIndex,
        invalidReadingCount = invalidCurrentReadings,
        voltageUvConvertedCount = voltageUvConvertedCount
    )

    /**
     * fieldsVersion 68 (N-01, S-09, S-27 (d)): one RAW read of
     * `BATTERY_PROPERTY_CHARGE_COUNTER` — the channel `charge_counter_raw`.
     * "Raw" means as the device declares it: no unit conversion (the unit is
     * the device's own — Honor MTN-NX1 reports mAh-sized values, Samsung S10
     * 5G µAh-sized — and the server infers which, rule 20), and no zero
     * filtering: [snapshotHealth] maps 0 to null, which for a channel is a
     * real value ("0 is a value, the server decides"); only
     * `Long.MIN_VALUE` — the documented "unsupported" return of
     * `getLongProperty` — and an exception are failures, each with its text
     * (never a sentinel). `Long` → `Float` is exact up to 2^24 = 16 777 216
     * and loses the last µAh above it; that limit is declared in the contract
     * (ت-01), not hidden here.
     *
     * Does not touch the current-scaling evidence counters
     * (`uaEvidenceCount` / `maEvidenceCount`): it reads a different property.
     */
    fun readChargeCounterRaw(): ChannelRead {
        val bm = batteryManager
            ?: return ChannelRead.Failed("BatteryManager unavailable (BATTERY_SERVICE returned null)")
        return try {
            val raw = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
            if (raw == Long.MIN_VALUE) {
                ChannelRead.Failed(
                    "getLongProperty(BATTERY_PROPERTY_CHARGE_COUNTER) returned Long.MIN_VALUE " +
                        "(the platform's unsupported marker)"
                )
            } else {
                ChannelRead.Value(raw.toFloat())
            }
        } catch (t: Exception) {
            ChannelRead.Failed(ReasonText.of(t))
        }
    }

    /**
     * V2 fix #56 (2.3): comprehensive battery health snapshot.
     *
     * Six fields the AI needs but the existing pipeline didn't surface:
     *   - design capacity (mAh): from sysfs charge_full_design or
     *     PowerProfile reflection. Identifies dual-cell halving when
     *     device reports half the published capacity.
     *   - current capacity (mAh): from Android 14+ STATE_OF_HEALTH,
     *     sysfs charge_full, or coulomb extrapolation. Tells SoH directly.
     *   - cycle count: EXTRA_CYCLE_COUNT of the battery broadcast (API 34+), then the
     *     sysfs cycle_count file, then a declared absence — [BatteryCycleCount.read], the
     *     one reader (B-08 calls the same). Critical for "is this battery worn out?".
     *   - charge counter (µAh): always available via BatteryManager,
     *     useful for backend coulomb tracking.
     *   - energy counter (nWh): same idea, energy units.
     *   - SoH official %: when STATE_OF_HEALTH is available, the OS's
     *     own SoH number — the gold standard.
     *
     * Each field has a *_source tag identifying where the value came from,
     * so the AI can weight differently (sysfs > reflection > -1).
     *
     * Sources for the lookup paths:
     *   - charge_full_design / charge_full: Linux Power Supply class spec
     *   - PowerProfile reflection: AOSP internal API (works across vendors)
     *   - (2026-10-08, javap on android.jar 33-36) the public BatteryManager has
     *     BATTERY_PROPERTY_* 1 to 6 only — CHARGE_COUNTER 1, CURRENT_NOW 2, CURRENT_AVERAGE 3,
     *     CAPACITY 4, ENERGY_COUNTER 5, STATUS 6. There is NO public STATE_OF_HEALTH or
     *     CYCLE_COUNT property (this comment used to name them as ids 7 and 8, and the cycle
     *     count was read with getIntProperty(8); the count is a broadcast extra,
     *     EXTRA_CYCLE_COUNT = "android.os.extra.CYCLE_COUNT", in android.jar 34, 35 and 36).
     *   - CHARGE_COUNTER and ENERGY_COUNTER: standard since Android 5.0
     */
    /** A long BatteryManager property as read: the value (a 0 included) and its state. */
    private fun readLongProperty(bm: BatteryManager, property: Int): Pair<Long?, String> {
        val call = runCatching { bm.getLongProperty(property) }
        val v = call.getOrNull()
        return when {
            call.isFailure -> null to BatteryReadStates.notRead(
                BatteryReadStates.REASON_CALL_FAILED_PREFIX + ReasonText.of(call.exceptionOrNull()!!)
            )
            v == Long.MIN_VALUE -> null to BatteryReadStates.notRead(BatteryReadStates.REASON_UNSUPPORTED_BY_DEVICE)
            else -> v to BatteryReadStates.READ
        }
    }

    fun snapshotHealth(
        systemFileReader: SystemFileReader? = null,
        /** The platform API level; a parameter only so the API-34 path can be tested on the JVM. */
        sdkInt: Int = Build.VERSION.SDK_INT,
        /** The latest battery broadcast the receiver saw (a parameter only so a JVM test can supply one). */
        batteryIntent: Intent? = lastBatteryIntent
    ): BatteryHealthSnapshot {
        val bm = batteryManager
        if (bm == null) {
            return BatteryHealthSnapshot(
                designCapacityMah = null,
                designCapacitySource = "no_battery_manager",
                cycleCount = null,
                cycleCountSource = CycleCountSources.UNAVAILABLE,
                cycleCountState = BatteryReadStates.notRead(BatteryReadStates.REASON_NO_BATTERY_MANAGER),
                chargeCounterUah = null,
                chargeCounterUahState = BatteryReadStates.notRead(BatteryReadStates.REASON_NO_BATTERY_MANAGER),
                energyCounterNwh = null,
                energyCounterNwhState = BatteryReadStates.notRead(BatteryReadStates.REASON_NO_BATTERY_MANAGER)
            )
        }

        // 1. Design capacity (mAh). Two sources tried in order.
        var designCap: Int? = null
        var designSrc = "unavailable"

        val sysDesignUah = systemFileReader?.readChargeFullDesign()
        if (sysDesignUah != null && sysDesignUah > 0) {
            designCap = (sysDesignUah / 1000).toInt()  // µAh → mAh
            designSrc = "sysfs_charge_full_design"
        } else {
            // PowerProfile reflection (com.android.internal.os).
            // Available across most AOSP forks; SecurityException on a few
            // OEMs that sealed the internal API. Suppressed silently.
            runCatching {
                val ppClass = Class.forName("com.android.internal.os.PowerProfile")
                val ctor = ppClass.getConstructor(Context::class.java)
                val pp = ctor.newInstance(context)
                val method = ppClass.getMethod("getBatteryCapacity")
                val cap = method.invoke(pp) as? Double
                if (cap != null && cap > 0) {
                    designCap = cap.toInt()
                    designSrc = "powerprofile_reflection"
                }
            }
        }

        // fieldsVersion 63 (2026-05-18): removed the entire "current
        // capacity" + sohOfficialPercent computation pipeline (was sections
        // 2, 2c, sohOfficial accumulation). All three tiers (Android 14
        // STATE_OF_HEALT, sysfs charge_full, AccuBattery-style
        // chargeCounter÷level extrapolation) produced unreliable values
        // — 102 % on a Samsung S24 Ultra, 100 % on a 2019 S10 5G — and
        // the resulting field was the single biggest source of "this
        // metric is obviously wrong" reports in field testing. Battery
        // wear is now surfaced via the 6 behavioral indicators in
        // hardware/battery/BatteryIndicators.kt + the cycle count below;
        // see docs/architecture/BATTERY_INDICATORS.md for the full
        // contract. Removing the dead computation here also drops the
        // `estimateBatterySoh()` helper from the hot path (it's still
        // referenced for one diagnostic spot, kept for now).

        // 3. Cycle count. fieldsVersion 68: ONE reader, BatteryCycleCount.read — the same call B-08 makes —
        // EXTRA_CYCLE_COUNT of the battery broadcast on API >= 34, then the sysfs file, then a declared
        // absence (T-02). Shipped AS READ, zero included, with a declared state (T-18 row 10): it used to
        // drop a 0 (`> 0`), so "the device reports 0 cycles" and "the device reports nothing" were the same
        // absent value; the server judges what a 0 means (rule 20). The getIntProperty(8) it used before is
        // gone: that property is not in the public API (javap on android.jar 34 and 35: BATTERY_PROPERTY_* 1..6).
        // The sysfs reader returns null for ANY failure and keeps no error text (rule 21 limit — it is not
        // rewritten here), so its null is "not readable or absent", never a guessed cause.
        val cycle = BatteryCycleCount.read(
            intent = batteryIntent,
            sdkInt = sdkInt,
            readSysfs = systemFileReader?.let { reader -> { path: String -> reader.readBatteryCycleCountAt(path) } }
        )

        // 4. Charge counter (µAh) — always available since Android 5.0. Shipped as read:
        // a 0 is a reading; Long.MIN_VALUE is the platform's "not supported" and is a
        // state, not a value.
        val chargeRead = readLongProperty(bm, BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)

        // 5. Energy counter (nWh) — Android 5.0+, vendor-dependent. Same rule.
        val energyRead = readLongProperty(bm, BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER)

        return BatteryHealthSnapshot(
            designCapacityMah = designCap,
            designCapacitySource = designSrc,
            cycleCount = cycle.count,
            cycleCountSource = cycle.source,
            cycleCountState = cycle.state,
            chargeCounterUah = chargeRead.first,
            chargeCounterUahState = chargeRead.second,
            energyCounterNwh = energyRead.first,
            energyCounterNwhState = energyRead.second
        )
    }
}

/**
 * V2 fix #56 (1.3): full scaling diagnostic for AI footer transparency.
 * Lets the AI know whether battery_current_ua samples come from a
 * data-locked decision (high confidence) or a default heuristic (lower
 * confidence) or no registration at all (untrusted).
 */
data class ScalingDiagnostic(
    val locked: Boolean,
    val source: String,
    val scaleMultiplier: Int,
    val signFlip: Int,
    val uaEvidenceCount: Int,
    val maEvidenceCount: Int,
    val sampleCount: Int,
    val isRegistered: Boolean,
    val initialProbeFailed: Boolean,
    // fieldsVersion 65 — measurement-integrity telemetry:
    /** sampleCount at the moment the scaling lock committed; -1 = never
     *  locked. Samples with index < this were normalised with the soft
     *  default heuristic and can be re-interpreted downstream. */
    val lockAtSampleIndex: Int = -1,
    /** Int.MIN_VALUE CURRENT_NOW readings filtered mid-scan (each was
     *  surfaced as the 0 "no data" sentinel instead of ±2.1e9 µA). */
    val invalidReadingCount: Int = 0,
    /** EXTRA_VOLTAGE readings detected as µV and divided by 1000. */
    val voltageUvConvertedCount: Int = 0
)

/**
 * V2 fix #56 (2.3): battery health snapshot built once at scan completion.
 * Consumed by [BatteryIndicators.compute] to derive the 6 behavioral
 * indicators surfaced in footer.batteryHealth. Source tags let the AI
 * weight by reliability:
 *   sysfs_* > powerprofile_reflection > unavailable
 *
 * fieldsVersion 63 (2026-05-18): dropped `currentCapacityMah`,
 * `currentCapacitySource`, and `sohOfficialPercent`. All three were
 * computed from the discredited single-point estimator (102 % on
 * S24 Ultra, 100 % on 2019 S10 5G) and no consumer still reads them.
 * See docs/architecture/BATTERY_INDICATORS.md for the migration.
 */
data class BatteryHealthSnapshot(
    val designCapacityMah: Int?,
    val designCapacitySource: String,
    /** As read, a 0 included (fieldsVersion 68, T-18 row 10); `null` only when [cycleCountState] says it was not read. */
    val cycleCount: Int?,
    val cycleCountSource: String,
    /** `read` | `not_read:<reason>` ([BatteryReadStates]). */
    val cycleCountState: String,
    /** µAh as read, a 0 included; `null` only when [chargeCounterUahState] says it was not read. */
    val chargeCounterUah: Long?,
    val chargeCounterUahState: String,
    /** nWh as read, a 0 included; `null` only when [energyCounterNwhState] says it was not read. */
    val energyCounterNwh: Long?,
    val energyCounterNwhState: String
)

/**
 * fieldsVersion 68 (batch 3 part 2-b, T-18 row 10): the state of a battery-health
 * reading — `read` | `not_read:<reason>`. The state NAMES are the agent's [م]
 * (the user decided: "cycles, the charge counter and the energy counter ship as
 * read, the zero with them, with a declared state; the server judges"). A reason
 * with text after its colon carries an exception's MESSAGE (rule 21).
 */
object BatteryReadStates {
    const val READ = "read"
    const val NOT_READ_PREFIX = "not_read:"

    const val REASON_NO_BATTERY_MANAGER = "no_battery_manager"

    /** The platform answered its "not supported" sentinel (Integer.MIN_VALUE / Long.MIN_VALUE). */
    const val REASON_UNSUPPORTED_BY_DEVICE = "unsupported_by_device"

    /** The BatteryManager call threw: `call_failed:<message>`. */
    const val REASON_CALL_FAILED_PREFIX = "call_failed:"

    /**
     * API below 34 has no `EXTRA_CYCLE_COUNT`, and the sysfs reader (which keeps no error text) returned nothing.
     * Composed by [BatteryCycleCount] as "<sysfs reason>_and_<extra reason>"; kept as a named constant because
     * it is the outcome of the S10 (API 30), the device the real golden recording comes from.
     */
    const val REASON_SYSFS_NOT_READABLE_AND_API_BELOW_34 =
        BatteryCycleCount.SYSFS_REASON_NOT_READABLE_OR_ABSENT + "_and_" + BatteryCycleCount.EXTRA_REASON_API_BELOW_34

    /** API below 34, and no sysfs reader was supplied to try. */
    const val REASON_NO_SYSFS_READER_AND_API_BELOW_34 =
        BatteryCycleCount.SYSFS_REASON_NO_READER + "_and_" + BatteryCycleCount.EXTRA_REASON_API_BELOW_34

    fun notRead(reason: String): String = NOT_READ_PREFIX + reason
}

/**
 * Pure helper for AccuBattery-style SoH estimation. Extracted as a top-
 * level function so the algorithm is unit-testable without an Android
 * Context. Used as the third tier in [BatteryReader.snapshotHealth] when
 * the SoH API and the sysfs charge_full readouts are both unavailable
 * (the common case on Samsung Exynos devices pre-Android 14).
 *
 * Linear-range gate: Li-ion batteries hold a near-constant V(charge)
 * slope between roughly 20–90 % SOC. Outside that band (CV stage near
 * 100 %, voltage cliff under 20 %) the charge_counter readout is no
 * longer a linear function of state-of-charge and the extrapolation
 * to 100 % would be off by 10–30 %. We refuse to estimate in that case
 * and surface the reason as a source tag so the report UI can prompt
 * the user to rescan with the battery in range.
 *
 * @return [BatterySohEstimate] with non-null [BatterySohEstimate.estimatedFullMah]
 *         + [BatterySohEstimate.sohPercent] when the estimate succeeded,
 *         or both null + a diagnostic [BatterySohEstimate.source] when it
 *         was refused. Source tags:
 *         - "estimated_from_charge_level": estimate produced
 *         - "outside_linear_range":        level outside 20–90 %
 *         - "missing_charge_counter":      bm.getLongProperty unavailable / ≤0
 *         - "missing_design_capacity":     can't compute SoH without design
 */
internal fun estimateBatterySoh(
    chargeCounterUah: Long?,
    batteryLevelPct: Int,
    designCapacityMah: Int?
): BatterySohEstimate {
    if (chargeCounterUah == null || chargeCounterUah <= 0L) {
        return BatterySohEstimate(null, null, "missing_charge_counter")
    }
    if (batteryLevelPct !in 20..90) {
        return BatterySohEstimate(null, null, "outside_linear_range")
    }
    if (designCapacityMah == null || designCapacityMah <= 0) {
        return BatterySohEstimate(null, null, "missing_design_capacity")
    }
    // chargeCounter is current capacity at this SOC. Extrapolate to 100 %:
    //   full_uah = chargeCounter * 100 / level
    // Then convert µAh → mAh.
    val estimatedFullUah = chargeCounterUah * 100L / batteryLevelPct.toLong()
    val estimatedFullMah = (estimatedFullUah / 1000L).toInt()
    // 110 % ceiling: fresh / under-cycled batteries can briefly read
    // above their published design capacity (manufacturers under-rate
    // by a few %). Anything above 110 % is almost certainly a unit /
    // sign error and we clamp it so a misread doesn't show "SoH 142 %".
    val sohPercent = ((estimatedFullMah.toFloat() / designCapacityMah) * 100f)
        .coerceIn(0f, 110f).toInt()
    return BatterySohEstimate(
        estimatedFullMah = estimatedFullMah,
        sohPercent = sohPercent,
        source = "estimated_from_charge_level"
    )
}

internal data class BatterySohEstimate(
    val estimatedFullMah: Int?,
    val sohPercent: Int?,
    /** "estimated_from_charge_level" on success; diagnostic tag on refusal. */
    val source: String
)

data class BatterySnapshot(
    val tempCelsius: Float,
    val voltageMv: Int,
    val currentUa: Int,
    val levelPercent: Int,
    val health: Int,
    val isCharging: Boolean,
    val plugType: String,
    val technology: String,
    val status: Int,
    /**
     * fieldsVersion 65 — package power in mW computed from THIS
     * snapshot's own (voltageMv, currentUa) pair, so v[7] is always
     * reproducible from v[2] × v[3]. -1f = "no data" (either input
     * missing), matching the readPackagePowerMw convention.
     */
    val packagePowerMw: Float = -1f
)
