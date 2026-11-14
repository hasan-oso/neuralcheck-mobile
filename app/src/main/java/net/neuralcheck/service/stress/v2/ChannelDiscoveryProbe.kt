package net.neuralcheck.service.stress.v2

import android.content.Context
import android.util.Log
import net.neuralcheck.data.local.files.ChannelDiscoveryReport
import net.neuralcheck.data.local.files.ChannelNames
import net.neuralcheck.data.local.files.CpuPolicyInfo
import net.neuralcheck.data.local.files.ExtraChannel
import net.neuralcheck.data.local.files.ScanChannelLayout
import net.neuralcheck.service.collector.BatteryReader
import net.neuralcheck.service.collector.SystemFileReader
import net.neuralcheck.util.ChannelRead
import net.neuralcheck.util.FailureText
import net.neuralcheck.util.FileTextRead
import net.neuralcheck.util.ReasonText
import net.neuralcheck.util.StressNative
import net.neuralcheck.util.ThermalFallback

/**
 * V5 schema (Task D): one-shot probe of every signal source the sampler
 * would otherwise call lazily. Runs at scan start, before the first
 * sample is collected, so the footer's [ChannelDiscoveryReport] tells the
 * AI exactly which channels are device-blocked vs missing vs available.
 *
 * The probe is intentionally cheap (one read per channel, all wrapped in
 * runCatching) and never throws. Each attempt produces one of three
 * outcomes, in priority order:
 *
 *   1. the failure says the source REFUSED access (a [SecurityException], or
 *      error TEXT naming EACCES / "Permission denied") → "blocked_by_selinux"
 *   2. read returned null / sentinel (-1, 0, NaN, Long.MIN_VALUE) or failed
 *      with any other text → "missing"
 *   3. otherwise → "available"
 *
 * fieldsVersion 68 (S-02, N-07, S-35, rule 21): every channel that is not
 * available also carries its REASON — the error text, or a statement of what
 * the source returned — in [ChannelDiscoveryReport.reasons], at most 200
 * characters. The app declares the reason and the server names the state
 * (rule 20); the two legacy lists stay for compatibility. The decision between
 * the two lists rests on the TEXT of the failure, because one exception class
 * (FileNotFoundException) covers both EACCES and ENOENT. A [SecurityException]
 * is still taken as "refused" by its class — that class is not ambiguous.
 * Readers that swallow their own errors internally (cpu / gpu temperature,
 * cpu usage, headroom, status) cannot give text; their reason says so plainly
 * instead of inventing one.
 *
 * It also decides which per-device channels this scan carries in `v[]`
 * (fieldsVersion 68, S-01): `charge_counter_raw` and one
 * `cpu_freq_policy{N}_khz` per readable policy, in that order, N ascending
 * (N-22). A channel that fails discovery is NOT added — no column, and its
 * state and reason are in the report.
 *
 * Channels are attempted in a fixed order so the footer is stable across
 * runs and the AI can diff successive scans by index.
 *
 * Pattern matches the prompt in MOBILE_V5_SCHEMA_REPORT §TASK D.
 */
object ChannelDiscoveryProbe {
    private const val TAG = "NeuralCheck.V5.Discovery"

    /** Unit text of `charge_counter_raw` (N-01): the device's own, unconverted. */
    const val CHARGE_COUNTER_UNIT = "as_declared_by_device"

    /** Source text of `charge_counter_raw` (N-01). */
    const val CHARGE_COUNTER_SOURCE = "BatteryManager.getLongProperty(BATTERY_PROPERTY_CHARGE_COUNTER)"

    /**
     * Channels probed, in stable order. fieldsVersion 68: the former
     * `charge_counter` slot is the raw channel [ChannelNames.CHARGE_COUNTER_RAW]
     * (same position, so older diffs by index still line up); the per-policy
     * channels are appended after these. `internal` (batch 3 part 3): the golden
     * request-payload generator walks THIS list to script one outcome per base
     * channel, instead of keeping a second copy of the names (rule 24).
     */
    internal val CHANNELS = listOf(
        ChannelNames.CPU_TEMP_C,
        ChannelNames.GPU_TEMP_C,
        ChannelNames.SKIN_TEMP_C_REAL,
        ChannelNames.BATTERY_TEMP_C,
        ChannelNames.BATTERY_VOLTAGE_MV,
        ChannelNames.BATTERY_CURRENT_UA,
        ChannelNames.CPU_FREQ_MAX_KHZ,
        ChannelNames.CPU_USAGE_PCT,
        ChannelNames.THERMAL_HEADROOM,
        ChannelNames.THERMAL_STATUS,
        ChannelNames.CHARGE_COUNTER_RAW,
        ChannelNames.ENERGY_COUNTER
    )

    /** Everything discovery decides for one scan. */
    data class ChannelDiscoveryResult(
        val report: ChannelDiscoveryReport,
        /** The columns this scan's samples carry: base block + admitted channels. */
        val layout: ScanChannelLayout,
        /** Raw policy topology (footer `cpuPolicies`), ascending policy number. */
        val cpuPolicies: List<CpuPolicyInfo>
    )

    /**
     * Runs every probe once and returns the aggregated result.
     *
     * @param context for ThermalFallback (skin sysfs walk).
     * @param batteryReader long-lived battery reader (already registered).
     * @param systemFileReader long-lived sysfs reader.
     */
    fun run(
        context: Context,
        batteryReader: BatteryReader,
        systemFileReader: SystemFileReader
    ): ChannelDiscoveryResult {
        val outcomes = LinkedHashMap<String, ProbeOutcome>()
        val chargeCounter = batteryReader.readChargeCounterRaw()

        for (channel in CHANNELS) {
            outcomes[channel] = when (channel) {
                ChannelNames.CHARGE_COUNTER_RAW -> outcomeOf(chargeCounter)
                else -> probeOne(channel, context, batteryReader, systemFileReader)
            }
        }

        return assemble(
            baseOutcomes = outcomes,
            chargeCounter = chargeCounter,
            readChargeCounter = { batteryReader.readChargeCounterRaw() },
            policies = CpuPolicyDiscovery.discover(
                candidateCount = Runtime.getRuntime().availableProcessors(),
                read = systemFileReader::readTextDetailed
            ),
            readText = systemFileReader::readTextDetailed
        )
    }

    /**
     * The pure half of discovery: from the base outcomes, the charge-counter
     * read, and the discovered policies, builds the report, the layout and
     * the policy records. Order of admitted extras is the contract's N-22:
     * `charge_counter_raw` first, then policies by ascending N.
     *
     * @param baseOutcomes the [CHANNELS] outcomes, in [CHANNELS] order.
     * @param readChargeCounter the reader the sampler will call per sample.
     * @param readText the file reader the per-policy channels will call per sample.
     */
    internal fun assemble(
        baseOutcomes: LinkedHashMap<String, ProbeOutcome>,
        chargeCounter: ChannelRead,
        readChargeCounter: () -> ChannelRead,
        policies: List<CpuPolicyDiscovery.PolicyOutcome>,
        readText: (String) -> FileTextRead
    ): ChannelDiscoveryResult {
        val outcomes = LinkedHashMap(baseOutcomes)
        val extras = ArrayList<ExtraChannel>()

        if (chargeCounter is ChannelRead.Value) {
            extras += ExtraChannel(
                name = ChannelNames.CHARGE_COUNTER_RAW,
                unit = CHARGE_COUNTER_UNIT,
                source = CHARGE_COUNTER_SOURCE,
                seed = chargeCounter.value,
                read = readChargeCounter
            )
        }

        for (policy in policies.sortedBy { it.policy }) {
            outcomes[policy.channelName] = outcomeOf(policy.currentFreq)
            val first = policy.currentFreq
            if (first is ChannelRead.Value) {
                val path = CpuPolicyDiscovery.curFreqPath(policy.policy)
                extras += ExtraChannel(
                    name = policy.channelName,
                    unit = "kHz",
                    source = path,
                    seed = first.value,
                    read = { CpuPolicyDiscovery.parseKhz(path, readText(path)) }
                )
            }
        }

        val available = outcomes.filterValues { it.result == ProbeResult.AVAILABLE }.keys.toList()
        val blocked = outcomes.filterValues { it.result == ProbeResult.BLOCKED }.keys.toList()
        val missing = outcomes.filterValues { it.result == ProbeResult.MISSING }.keys.toList()
        val reasons = LinkedHashMap<String, String>()
        for ((channel, outcome) in outcomes) {
            if (outcome.result != ProbeResult.AVAILABLE) reasons[channel] = ReasonText.cap(outcome.reason!!)
        }

        val summary = "discovery: ${available.size}/${outcomes.size} available, " +
            "${blocked.size} blocked, ${missing.size} missing"
        Log.i(TAG, summary)
        if (blocked.isNotEmpty()) Log.i(TAG, "blocked: $blocked")
        if (missing.isNotEmpty()) Log.i(TAG, "missing: $missing")

        return ChannelDiscoveryResult(
            report = ChannelDiscoveryReport(
                attempted = outcomes.keys.toList(),
                available = available,
                blockedBySelinux = blocked,
                missing = missing,
                summary = summary,
                reasons = reasons
            ),
            layout = ScanChannelLayout(extras),
            cpuPolicies = policies.sortedBy { it.policy }.map { it.info }
        )
    }

    internal enum class ProbeResult { AVAILABLE, BLOCKED, MISSING }

    /**
     * The outcome of one channel: [result], and — unless the channel is
     * available — the [reason] text (never null in that case: a channel that
     * is not available always says why).
     */
    internal data class ProbeOutcome(val result: ProbeResult, val reason: String?) {
        init {
            require(result == ProbeResult.AVAILABLE || reason != null) {
                "a channel that is not available must carry its reason"
            }
        }

        companion object {
            val AVAILABLE = ProbeOutcome(ProbeResult.AVAILABLE, null)

            /** The reader gave a sentinel and, swallowing its own errors, no text. */
            fun noValue(reader: String, returned: String) = ProbeOutcome(
                ProbeResult.MISSING,
                "$reader returned $returned; the reader does not expose its error text"
            )
        }
    }

    /**
     * Decides an outcome from a read: a value is AVAILABLE; a failure is
     * BLOCKED when its TEXT says access was refused (rule 21), MISSING for
     * anything else — "anything else" includes text that names neither
     * EACCES nor ENOENT, which the legacy two-way lists cannot express; the
     * exact text is in the reason either way.
     */
    internal fun outcomeOf(read: ChannelRead): ProbeOutcome = when (read) {
        is ChannelRead.Value -> ProbeOutcome.AVAILABLE
        is ChannelRead.Failed -> ProbeOutcome(
            if (FailureText.classify(read.reason) == FailureText.Kind.DENIED) ProbeResult.BLOCKED
            else ProbeResult.MISSING,
            ReasonText.cap(read.reason)
        )
    }

    /**
     * fieldsVersion 66 — pure decision for the `skin_temp_c_real` channel.
     *
     * [ThermalFallback.read] fills the skin slot with battery temperature
     * when no native skin zone exists, so a positive value alone proved
     * nothing: both field devices (Honor MTN-NX1, Samsung S10 5G) were
     * tagged "available" while 100 % of their skin readings were the
     * battery proxy. A real skin sensor requires BOTH a positive reading
     * AND the sysfs provenance flag. The proxy case reports MISSING (the
     * channel is literally named `_real`; "proxy" would add a fourth
     * value outside the documented available|blocked|missing tri-state,
     * and the proxy provenance is already carried exactly by
     * dataQuality.skinTempSourceBreakdown["battery_proxy"]).
     */
    internal fun classifySkin(realReading: Float, fromSysfs: Boolean): ProbeResult =
        if (realReading > 0f && fromSysfs) ProbeResult.AVAILABLE else ProbeResult.MISSING

    private fun probeOne(
        channel: String,
        context: Context,
        batteryReader: BatteryReader,
        systemFileReader: SystemFileReader
    ): ProbeOutcome {
        return try {
            when (channel) {
                ChannelNames.CPU_TEMP_C -> {
                    val value = systemFileReader.readCpuTemp()
                    if (value > 0f) ProbeOutcome.AVAILABLE
                    else ProbeOutcome.noValue("readCpuTemp", "$value (not > 0)")
                }
                ChannelNames.GPU_TEMP_C -> {
                    val value = systemFileReader.readGpuTemp()
                    if (value > 0f) ProbeOutcome.AVAILABLE
                    else ProbeOutcome.noValue("readGpuTemp", "$value (not > 0)")
                }
                ChannelNames.SKIN_TEMP_C_REAL -> {
                    // Distinguish a real skin sysfs hit from the
                    // ThermalFallback battery proxy. Only the native zone
                    // walk counts as "real" — the proxy is always available
                    // when battery_temp is, and would mask blocked sysfs.
                    val arr = ThermalFallback.read(context)
                    val real = arr.getOrNull(StressNative.THERMAL_SKIN) ?: -1f
                    // fieldsVersion 66: consult the provenance mask — the
                    // value alone is the battery proxy on skin-less devices.
                    val fromSysfs = ThermalFallback.isLastReadFromSysfs(StressNative.THERMAL_SKIN)
                    if (classifySkin(realReading = real, fromSysfs = fromSysfs) == ProbeResult.AVAILABLE) {
                        ProbeOutcome.AVAILABLE
                    } else {
                        ProbeOutcome(
                            ProbeResult.MISSING,
                            "no native skin sensor: ThermalFallback reading=$real, fromSysfs=$fromSysfs " +
                                "(a battery proxy is not a skin sensor)"
                        )
                    }
                }
                ChannelNames.BATTERY_TEMP_C -> {
                    val v = batteryReader.snapshot().tempCelsius
                    if (!v.isNaN() && v > 0f) ProbeOutcome.AVAILABLE
                    else ProbeOutcome.noValue("BatteryReader.snapshot().tempCelsius", "$v (not > 0)")
                }
                ChannelNames.BATTERY_VOLTAGE_MV -> {
                    val v = batteryReader.snapshot().voltageMv
                    if (v > 0) ProbeOutcome.AVAILABLE
                    else ProbeOutcome.noValue("BatteryReader.snapshot().voltageMv", "$v (not > 0)")
                }
                ChannelNames.BATTERY_CURRENT_UA -> {
                    val v = batteryReader.snapshot().currentUa
                    if (v != 0) ProbeOutcome.AVAILABLE
                    else ProbeOutcome.noValue("BatteryReader.snapshot().currentUa", "0 (the invalid marker is filtered to 0)")
                }
                ChannelNames.CPU_FREQ_MAX_KHZ -> {
                    val coreCount = Runtime.getRuntime().availableProcessors()
                    val big = systemFileReader.readCpuFrequency(coreCount - 1)
                    val little = systemFileReader.readCpuFrequency(0)
                    val mid = if (coreCount > 4) {
                        systemFileReader.readCpuFrequency(coreCount / 2)
                    } else -1L
                    val maxFreq = maxOf(big, mid, little)
                    if (maxFreq > 0L) ProbeOutcome.AVAILABLE
                    else ProbeOutcome.noValue("readCpuFrequency", "$maxFreq for every probed core (not > 0)")
                }
                ChannelNames.CPU_USAGE_PCT -> {
                    val v = systemFileReader.readCpuUsage()
                    if (v >= 0f && v.isFinite()) ProbeOutcome.AVAILABLE
                    else ProbeOutcome.noValue("readCpuUsage", "$v (negative or non-finite)")
                }
                ChannelNames.THERMAL_HEADROOM -> {
                    val v = systemFileReader.readThermalHeadroom()
                    // Sentinel: NaN means unsupported API or platform error.
                    if (!v.isNaN() && v.isFinite()) ProbeOutcome.AVAILABLE
                    else ProbeOutcome.noValue("readThermalHeadroom", "$v (unsupported API or platform error)")
                }
                ChannelNames.THERMAL_STATUS -> {
                    val v = systemFileReader.readThermalStatus()
                    if (v >= 0) ProbeOutcome.AVAILABLE
                    else ProbeOutcome.noValue("readThermalStatus", "$v (negative)")
                }
                ChannelNames.ENERGY_COUNTER -> {
                    val health = batteryReader.snapshotHealth(systemFileReader)
                    val v = health.energyCounterNwh
                    if (v != null && v != 0L) ProbeOutcome.AVAILABLE
                    else ProbeOutcome.noValue("snapshotHealth().energyCounterNwh", "${v ?: "null"} (null or 0)")
                }
                else -> {
                    Log.w(TAG, "unknown channel '$channel' — treating as missing")
                    ProbeOutcome(ProbeResult.MISSING, "channel '$channel' has no probe in this build")
                }
            }
        } catch (e: SecurityException) {
            // SELinux on stock Samsung / HyperOS / MIUI typically maps to
            // a SecurityException on the deny path. Surface this distinct
            // from "missing" so AI can tell apart "device blocks us" from
            // "device doesn't have the sensor". SecurityException is the one
            // exception class that is unambiguous about the cause, so the
            // class alone decides here; the message is still the reason.
            Log.i(TAG, "channel '$channel' blocked: ${e.javaClass.simpleName}: ${e.message}")
            ProbeOutcome(ProbeResult.BLOCKED, ReasonText.of(e))
        } catch (e: Throwable) {
            // Anything else (NPE on uninitialised reader, IO, etc.): the TEXT
            // decides (rule 21) — an EACCES in the message is a refusal, the
            // rest is a channel that doesn't reliably exist on this device.
            Log.i(TAG, "channel '$channel' threw ${e.javaClass.simpleName}: ${e.message}")
            outcomeOf(ChannelRead.Failed(ReasonText.of(e)))
        }
    }
}
