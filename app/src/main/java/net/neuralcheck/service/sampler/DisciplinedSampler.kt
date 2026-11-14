package net.neuralcheck.service.sampler

import android.content.Context
import android.os.SystemClock
import android.util.Log
import net.neuralcheck.data.local.files.ChannelReadFailures
import net.neuralcheck.data.local.files.ExtraChannel
import net.neuralcheck.data.local.files.TimeSeriesFileWriter
import net.neuralcheck.data.local.files.TimeSeriesSample
import net.neuralcheck.data.local.files.V2_SAMPLE_VAR_COUNT
import net.neuralcheck.service.collector.BatteryReader
import net.neuralcheck.service.collector.SensorCollector
import net.neuralcheck.service.collector.SystemFileReader
import net.neuralcheck.service.stress.GpuFpsTracker
import net.neuralcheck.util.StressNative
import net.neuralcheck.util.ThermalFallback
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Disciplined sampler that collects all 18 time-series variables at exact intervals (ADR 4.7, 4.8).
 * Switches between 1Hz (phases 1,2,4,5) and 4Hz (phase 3).
 */
class DisciplinedSampler(
    private val sensorCollector: SensorCollector,
    private val batteryReader: BatteryReader,
    private val systemFileReader: SystemFileReader,
    private val fileWriter: TimeSeriesFileWriter,
    /** V2: when non-null, the sampler emits 22-var samples (18 V1 +
     *  4 V2: skin_temp, package_power, storage_throughput,
     *  memory_bandwidth) and reads thermal via [ThermalFallback] so
     *  Samsung One UI / MIUI still produce skin_temp readings.
     *  Null in V1 mode for backward compatibility. */
    private val context: Context? = null
) {
    companion object {
        private const val TAG = "NeuralCheck"
    }

    private var executor: ScheduledExecutorService? = null
    private val startTimeMs = AtomicLong(0)
    private val currentPhase = AtomicInteger(1)
    private val sampleCount = AtomicInteger(0)
    private val stats = SamplerStats()
    /** V2 mode: emit 22-var samples instead of 18. Set via [enableV2Mode]
     *  before calling start(). When false, samples remain 18-var to
     *  preserve V1 binary compatibility. */
    @Volatile private var v2Mode: Boolean = false
    /** V2: storage throughput (binary MiB/s, ÷2^20 — A70 unified this with
     *  [memoryBandwidthMbps]'s convention; fieldsVersion <= 67 callers fed
     *  decimal MB/s here instead) live snapshot for v[8]. The storage phase
     *  populates this; other phases read 0. */
    private val storageThroughputMbps = AtomicLong(0)
    /** V2: memory bandwidth (binary MiB/s, ÷2^20) live snapshot for v[9]. */
    private val memoryBandwidthMbps = AtomicLong(0)

    /**
     * fieldsVersion 66 — v[6] provenance. Index of the first sample whose
     * cpu_usage came from SystemFileReader's process-level fallback
     * (sticky); -1 while every sample so far used /proc/stat. Read at
     * completeScan into dataQuality.cpuUsageFallbackFromSample.
     */
    private val cpuUsageFallbackFromSample = AtomicInteger(-1)

    fun getCpuUsageFallbackFromSample(): Int = cpuUsageFallbackFromSample.get()

    /** V2 — switch to 22-var sample format. Idempotent. */
    fun enableV2Mode() { v2Mode = true }

    /**
     * fieldsVersion 68: the per-device channels of THIS scan, appended after
     * the V2 base block `v[0..10]` in every sample (the layout is decided by
     * channel discovery, which must run before [start]; the same list must be
     * given to the file writer as `variables[]` — see ScanChannelLayout).
     * Empty (the default) = the plain 11-column base. V1 (datacollect) mode
     * ignores it.
     */
    fun configureExtraChannels(channels: List<ExtraChannel>) {
        extraChannels = ExtraChannelSampler(channels)
    }

    /**
     * fieldsVersion 68 (S-27): which samples had a failed read of which
     * per-device channel — to be written to the footer at the end of the scan.
     */
    fun getChannelReadFailures(): ChannelReadFailures = extraChannels.failures()

    @Volatile private var extraChannels: ExtraChannelSampler = ExtraChannelSampler(emptyList())

    /** V2 — caller (V2 service) reports instantaneous storage I/O
     *  rate. Resets to 0 between phases. */
    fun setStorageThroughput(mbps: Float) {
        storageThroughputMbps.set(mbps.toRawBits().toLong())
    }

    /** V2 — caller reports memory bandwidth from the in-flight memory
     *  test phase. Reset to 0 between phases. */
    fun setMemoryBandwidth(mbps: Float) {
        memoryBandwidthMbps.set(mbps.toRawBits().toLong())
    }

    private fun fromRawBits(l: Long): Float = Float.fromBits(l.toInt())

    private val _sampleFlow = MutableSharedFlow<TimeSeriesSample>(replay = 1, extraBufferCapacity = 64)
    val samples: SharedFlow<TimeSeriesSample> = _sampleFlow

    /**
     * V2 fix #54: in-memory buffer of all collected samples this scan.
     * Used by [StressEffectivenessCalculator] in completeScan to detect
     * "stress test failed to stress" without re-reading the temp file.
     *
     * Memory cost: 480s × 10Hz max × ~22 floats × 4 bytes ≈ 4800 × 88B
     * = ~420 KB peak. Bounded; cleared on start().
     */
    private val sampleBuffer: MutableList<TimeSeriesSample> =
        java.util.Collections.synchronizedList(mutableListOf())

    /** V2 fix #54: snapshot of all collected samples for footer
     *  diagnostics (stressEffectiveness). Returns a defensive copy. */
    fun getSamplesSnapshot(): List<TimeSeriesSample> =
        synchronized(sampleBuffer) { sampleBuffer.toList() }

    // fieldsVersion 66: the Phase 1.7.A `backfillMemoryBandwidthForPhase`
    // in-memory patch was removed. It rewrote v[9] in the sampler's buffer
    // AFTER the writer had already flushed most phase-2 samples to the
    // temp file (flush every 10), so the final .ts.json.gz kept zeros for
    // all but the ≤9 still-buffered tail samples — and no on-device
    // consumer ever read the patched values. v[9] is now published LIVE
    // by AiMemoryWorkload through [setMemoryBandwidth] (~1 Hz), the same
    // pattern the storage phase uses per 256 MiB segment.

    /**
     * @param initialPhase A46 fix: the phase tag applied to every sample
     * collected from the very first tick, set BEFORE [startAtFrequency]
     * schedules that tick. Previously [currentPhase] relied solely on its
     * AtomicInteger(1) field default, and callers set the real initial
     * phase via a separate, later [setPhase] call — for the V2 orchestrator
     * (StressTestService.executePhasesV2) that call happened inside a
     * SEPARATE coroutine (phaseDriver) launched after several other
     * coroutine launches, racing against this method's own
     * scheduleAtFixedRate(..., initialDelay = 0, ...) on its dedicated
     * "NeuralCheck-Sampler" thread. Whichever won the race decided whether
     * the first sample(s) got tagged with the correct phase (0 =
     * INIT_PROFILING) or the stale default (1) — an initialization race,
     * which is why it only reproduced in some scans and not others.
     * Defaults to 1 so V1's call site (StressTestService.executePhases,
     * which already calls setPhase(1) as its first statement anyway) is
     * unaffected.
     */
    fun start(startTime: Long, initialPhase: Int = 1) {
        startTimeMs.set(startTime)
        sampleCount.set(0)
        cpuUsageFallbackFromSample.set(-1)
        extraChannels.reset()
        stats.reset()
        synchronized(sampleBuffer) { sampleBuffer.clear() }
        // Must happen before startAtFrequency() below: that call schedules
        // the first tick with a 0ms initial delay on a separate thread, so
        // currentPhase has to already hold the right value by the time it
        // can possibly fire, not "soon after".
        currentPhase.set(initialPhase)
        // V2 fix #56-3 (fieldsVersion 58): cache per-CPU cpuinfo_max_freq once
        // at scan start so the calculator can derive cluster boundaries at
        // end-of-scan without a second sysfs walk. Best-effort — null when
        // sysfs is locked on this device (rare; per-CPU max_freq survives
        // SELinux on most builds we've seen).
        val perCpuMax = runCatching {
            net.neuralcheck.util.SocNominalFrequencies.readPerCpuMaxFreqKhz()
        }.getOrNull()
        stats.setPerCpuMaxFreqKhz(perCpuMax)
        startAtFrequency(1000L) // Start at 1Hz
    }

    fun setPhase(phase: Int) {
        currentPhase.set(phase)
    }

    fun switchTo4Hz() {
        restart(250L)
    }

    fun switchTo1Hz() {
        restart(1000L)
    }

    /** V2 — set sampling rate to N Hz. Used by per-phase config:
     *  INIT/COOLDOWN at 2 Hz, STORAGE/MEMORY at 1 Hz, CPU/GPU/COMBINED
     *  at 4 Hz, BURST at 10 Hz. */
    fun switchToHz(hz: Int) {
        if (hz <= 0) return
        restart((1000L / hz).coerceAtLeast(50L))
    }

    fun stop() {
        executor?.shutdownNow()
        executor = null
    }

    fun getSampleCount(): Int = sampleCount.get()

    fun snapshotStats(): SamplerStats.Snapshot = stats.snapshot()

    private fun startAtFrequency(periodMs: Long) {
        executor = Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "NeuralCheck-Sampler").apply { isDaemon = true }
        }
        executor?.scheduleAtFixedRate({ collectSample() }, 0, periodMs, TimeUnit.MILLISECONDS)
    }

    private fun restart(periodMs: Long) {
        executor?.shutdownNow()
        startAtFrequency(periodMs)
    }

    private fun collectSample() {
        try {
            // V5 schema: cpu/gpu/skin temps no longer occupy v[] slots — read
            // them here once and route them through the SamplerStats sidecar
            // so peak/avg temps still get computed for the footer.
            val ctx = context
            val thermalFallback: FloatArray = if (ctx != null) {
                runCatching { ThermalFallback.read(ctx) }
                    .getOrDefault(FloatArray(StressNative.THERMAL_COUNT) { -1f })
            } else FloatArray(StressNative.THERMAL_COUNT) { -1f }
            val cpuTempC = if (thermalFallback[StressNative.THERMAL_CPU] > 0f) {
                thermalFallback[StressNative.THERMAL_CPU]
            } else {
                systemFileReader.readCpuTemp()
            }
            val gpuTempC = if (thermalFallback[StressNative.THERMAL_GPU] > 0f) {
                thermalFallback[StressNative.THERMAL_GPU]
            } else {
                systemFileReader.readGpuTemp()
            }
            val skinTempC = thermalFallback.getOrNull(StressNative.THERMAL_SKIN) ?: -1f

            // V5 schema (Task E): feed accelerometer magnitude into
            // SamplerStats so the footer's scanStillness can be computed
            // at end-of-scan. Sensors snapshot is read once here; buildSample
            // also accesses sensorCollector internally for the V1 legacy
            // path — that snapshot is cached on the SensorCollector side
            // so the double call is cheap.
            val sensors = sensorCollector.snapshot()
            // fieldsVersion 68 (ت-18 row 6): only a DELIVERED accelerometer
            // reading enters the stillness statistic. An accelerometer that
            // never delivered used to feed zeros (a standard deviation of 0 =
            // "perfectly still"); now it feeds nothing and the footer says
            // insufficient_accel_samples.
            sensors.accelMagnitudeOrNull()?.let { stats.updateAccelMagnitude(it) }

            val sample = buildSample(thermalFallback)
            fileWriter.addSample(sample)
            _sampleFlow.tryEmit(sample)
            synchronized(sampleBuffer) { sampleBuffer.add(sample) }
            sampleCount.incrementAndGet()
            val phase = currentPhase.get()
            stats.update(sample, phase)
            // Sidecar thermal channels: PowerManager.getThermalHeadroom and
            // currentThermalStatus aren't part of the v[] sample array
            // (the array shape is locked by the file format) but they are
            // critical on Samsung where the sysfs zones are SELinux-blocked.
            // Read once per sample — the headroom call self-rate-limits to 1Hz.
            // V5 addition: cpu/gpu/skin temps also flow through this sidecar
            // since they are no longer in v[].
            val headroom = systemFileReader.readThermalHeadroom()
            val thermalStatus = systemFileReader.readThermalStatus()
            stats.updateThermalSidecar(
                headroom = headroom,
                thermalStatus = thermalStatus,
                cpuTempC = cpuTempC,
                gpuTempC = gpuTempC,
                skinTempC = skinTempC
            )
            // V2 fix #56-3 (fieldsVersion 58): per-CPU usage sidecar.
            // Sampler accumulates per-CPU utilization during phase 3 only
            // (see SamplerStats.updatePerCpuUsage). null when /proc/stat
            // per-CPU lines are unavailable (HyperOS / SELinux).
            val perCpuUsage = systemFileReader.readPerCpuUsage()
            stats.updatePerCpuUsage(perCpuUsage, phase)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to collect sample: ${e.message}")
        }
    }

    /**
     * Build a TimeSeriesSample.
     *
     * V2 mode: emits the V5 11-channel BASE layout (universal channels only),
     * followed — from fieldsVersion 68 — by the per-device channels this scan
     * admitted ([configureExtraChannels]); the sample is therefore
     * 11 + extras floats wide, a per-scan fact.
     * V1 mode: emits the legacy 18-channel layout (datacollect flavor).
     *
     * V5 layout — see [net.neuralcheck.data.local.files.V2_VARIABLE_DEFINITIONS]:
     *   v[0]  timestamp_ms
     *   v[1]  battery_temp_c
     *   v[2]  battery_voltage_mv
     *   v[3]  battery_current_ua
     *   v[4]  battery_level_pct
     *   v[5]  cpu_freq_max_khz       (max across clusters; replaces big/little/mid)
     *   v[6]  cpu_usage_pct
     *   v[7]  package_power_mw
     *   v[8]  storage_throughput_mbps
     *   v[9]  memory_bandwidth_mbps
     *   v[10] gpu_fps
     *
     * Per-device extras (cpu/gpu/skin temp, accel/gyro) no longer enter v[].
     * SensorCollector is still polled here so [SamplerStats] / footer
     * helpers can consume the readings via sidecars (scanStillness in Task E,
     * thermalSensorAvailability in Task F).
     */
    private fun buildSample(thermalFallback: FloatArray): TimeSeriesSample {
        val battery = batteryReader.snapshot()
        // V5: SensorCollector is still polled so scanStillness (Task E) can
        // consume the live magnitude stream, even though accel/gyro no
        // longer occupy v[] slots.
        val sensors = sensorCollector.snapshot()

        val cpuUsage = systemFileReader.readCpuUsage()
        // fieldsVersion 66: record the sample index at which v[6] stopped
        // being /proc/stat (system) and became the process-level fallback
        // (this app's share). sampleCount is still this sample's index
        // here — it increments after the sample is stored.
        if (!systemFileReader.isProcStatAvailable()) {
            cpuUsageFallbackFromSample.compareAndSet(-1, sampleCount.get())
        }
        val coreCount = Runtime.getRuntime().availableProcessors()

        // V5 v[5]: max frequency across the three cluster slots we can
        // sample. -1 sentinel readings drop out of the max via coerceAtLeast.
        val bigFreq = systemFileReader.readCpuFrequency(coreCount - 1)
        val littleFreq = systemFileReader.readCpuFrequency(0)
        val midFreq = if (coreCount > 4) systemFileReader.readCpuFrequency(coreCount / 2) else -1L
        val maxFreq = maxOf(bigFreq, midFreq, littleFreq)

        val gpuFps = GpuFpsTracker.getAndResetFps()

        // fieldsVersion 68: the V2 sample is the base block plus however many
        // per-device channels this scan admitted (its width is a per-scan fact).
        val extras = extraChannels
        val arraySize = if (v2Mode) V2_SAMPLE_VAR_COUNT + extras.width else 18
        val values = FloatArray(arraySize)

        if (v2Mode) {
            // V5 11-channel layout — universal channels only.
            values[0] = (SystemClock.elapsedRealtime() - startTimeMs.get()).toFloat()
            values[1] = battery.tempCelsius
            values[2] = battery.voltageMv.toFloat()
            values[3] = battery.currentUa.toFloat()
            values[4] = battery.levelPercent.toFloat()
            values[5] = maxFreq.toFloat()
            values[6] = cpuUsage
            // fieldsVersion 65: v[7] comes from the SAME battery snapshot
            // as v[2]/v[3] (one CURRENT_NOW read per tick) so the power
            // channel is always reproducible as |v[2] × v[3]| / 1e6. The
            // previous second read produced ~3 % non-reproducible samples
            // and double-advanced the scaling-lock sample counter.
            values[7] = battery.packagePowerMw
            values[8] = fromRawBits(storageThroughputMbps.get())
            values[9] = fromRawBits(memoryBandwidthMbps.get())
            values[10] = gpuFps
            // v[11..]: per-device channels, in layout order. sampleCount is
            // this sample's index in samples[] (it increments after the sample
            // is stored), which is what a failed read is recorded under.
            extras.fill(values, V2_SAMPLE_VAR_COUNT, sampleCount.get())
        } else {
            // V1 legacy 18-channel layout (datacollect flavor only).
            // Preserved verbatim so V1 consumers continue to parse.
            val cpuTemp = if (thermalFallback[StressNative.THERMAL_CPU] > 0f) {
                thermalFallback[StressNative.THERMAL_CPU]
            } else {
                systemFileReader.readCpuTemp()
            }
            val gpuTemp = if (thermalFallback[StressNative.THERMAL_GPU] > 0f) {
                thermalFallback[StressNative.THERMAL_GPU]
            } else {
                systemFileReader.readGpuTemp()
            }
            values[0] = (SystemClock.elapsedRealtime() - startTimeMs.get()).toFloat()
            values[1] = battery.tempCelsius
            values[2] = battery.voltageMv.toFloat()
            values[3] = battery.currentUa.toFloat()
            values[4] = battery.levelPercent.toFloat()
            values[5] = cpuTemp
            values[6] = bigFreq.toFloat()
            values[7] = littleFreq.toFloat()
            values[8] = midFreq.toFloat()
            values[9] = cpuUsage
            values[10] = gpuFps
            values[11] = gpuTemp
            values[12] = sensors.accelX
            values[13] = sensors.accelY
            values[14] = sensors.accelZ
            values[15] = sensors.gyroX
            values[16] = sensors.gyroY
            values[17] = sensors.gyroZ
        }

        return TimeSeriesSample(
            ts = System.currentTimeMillis(),
            phase = currentPhase.get(),
            v = values
        )
    }
}
