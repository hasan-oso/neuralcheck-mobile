package net.neuralcheck.files

import android.content.Context
import com.google.gson.JsonObject
import kotlinx.coroutines.runBlocking
import net.neuralcheck.data.local.files.BatteryPair
import net.neuralcheck.data.local.files.BatteryPairsState
import net.neuralcheck.data.local.files.ChannelDiscoveryReport
import net.neuralcheck.data.local.files.ChannelNames
import net.neuralcheck.data.local.files.CpuPolicyInfo
import net.neuralcheck.data.local.files.ExtraChannel
import net.neuralcheck.data.local.files.ScanChannelLayout
import net.neuralcheck.data.local.files.ThermalHeadroomReading
import net.neuralcheck.data.local.files.ThermalStatusEvent
import net.neuralcheck.data.local.files.TimeSeriesFileReader
import net.neuralcheck.data.local.files.TimeSeriesFileWriter
import net.neuralcheck.data.local.files.TimeSeriesFooter
import net.neuralcheck.data.local.files.TimeSeriesHeader
import net.neuralcheck.data.local.files.TimeSeriesSample
import net.neuralcheck.data.local.files.V2_SAMPLE_VAR_COUNT
import net.neuralcheck.data.remote.AnalysisRequestSerializer
import net.neuralcheck.data.remote.BehaviorFingerprintBuilder
import net.neuralcheck.domain.model.DeviceInfo
import net.neuralcheck.domain.model.ScanMetadata
import net.neuralcheck.domain.model.ScanReport
import net.neuralcheck.domain.model.ScanType
import net.neuralcheck.service.sampler.ExtraChannelSampler
import net.neuralcheck.util.ChannelRead
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.io.File

/**
 * fieldsVersion 68 — the per-scan layout through the REAL chain, with nothing
 * substituted (rule 37): the extra-channel sampler fills real sample arrays,
 * the real [TimeSeriesFileWriter] writes the `.ts.json.gz` declaring the
 * layout, the real [TimeSeriesFileReader] reads it back, the real
 * [BehaviorFingerprintBuilder] builds the request payload from what the
 * reader returned, and the payload is serialised with the very Gson
 * configuration FirebaseAnalysisService uses (`AnalysisRequestSerializer.gson`).
 *
 * What is locked here is the part of rule 41 that can run in a JVM without a
 * device: the shape the next layer actually receives. What it cannot reach —
 * the real DisciplinedSampler / StressTestService (they need a Context, a
 * BatteryManager and sysfs) and a file from a real scan — is the device
 * round's job (contract §٣ بند 7 مرحلة ٢).
 *
 * The scan modelled: a 3-policy device (0, 4, 7) whose charge counter read
 * fails on samples 1 and 2 and whose policy-4 frequency read fails on sample 3.
 */
class PerScanLayoutRoundTripTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var context: Context

    /** The production configuration: AnalysisRequestSerializer.kt (no serializeNulls). */
    private val wireGson = AnalysisRequestSerializer.gson

    @Before
    fun setUp() {
        context = mock<Context> { on { filesDir } doReturn tmp.newFolder("filesdir") }
    }

    private fun scripted(name: String, unit: String, seed: Float, vararg script: ChannelRead): ExtraChannel {
        val queue = ArrayDeque(script.toList())
        var last: ChannelRead = script.last()
        return ExtraChannel(name, unit, "src of $name", seed) {
            if (queue.isNotEmpty()) last = queue.removeFirst()
            last
        }
    }

    private val charge = scripted(
        ChannelNames.CHARGE_COUNTER_RAW, "as_declared_by_device", 1_711_600f,
        ChannelRead.Value(1_711_600f), ChannelRead.Failed("binder died"), ChannelRead.Failed("binder died"),
        ChannelRead.Value(1_711_000f), ChannelRead.Value(1_710_900f)
    )
    private val p0 = scripted(
        ChannelNames.cpuFreqPolicy(0), "kHz", 1_804_800f,
        ChannelRead.Value(1_804_800f), ChannelRead.Value(1_900_000f), ChannelRead.Value(1_900_000f),
        ChannelRead.Value(1_804_800f), ChannelRead.Value(1_804_800f)
    )
    private val p4 = scripted(
        ChannelNames.cpuFreqPolicy(4), "kHz", 2_419_200f,
        ChannelRead.Value(2_419_200f), ChannelRead.Value(2_419_200f), ChannelRead.Value(2_000_000f),
        ChannelRead.Failed("p4: open failed: EACCES (Permission denied)"), ChannelRead.Value(2_419_200f)
    )
    private val p7 = scripted(
        ChannelNames.cpuFreqPolicy(7), "kHz", 2_841_600f,
        ChannelRead.Value(2_841_600f)
    )

    private val layout = ScanChannelLayout(listOf(charge, p0, p4, p7))

    /** ت-03: a call every 10 s; the second returned NaN, the third threw. */
    private val thermalHeadroom = listOf(
        ThermalHeadroomReading(0L, 0.42f, "ok"),
        ThermalHeadroomReading(10_000L, null, "nan"),
        ThermalHeadroomReading(20_000L, null, "exception:getThermalHeadroom: rate limited"),
        ThermalHeadroomReading(30_000L, 1.05f, "ok")
    )

    /** ت-06: three broadcasts, the second with a failed CURRENT_NOW (null field = absent key). */
    private val batteryPairs = listOf(
        BatteryPair(2_000L, 4_350_000, -812, 5_596L, 72, 100, 305),
        BatteryPair(61_000L, 4_330_000, null, 5_590L, 72, 100, 307),
        BatteryPair(120_000L, 4_300_000, -900, 5_580L, 71, 100, 311)
    )
    private val batteryPairsState = BatteryPairsState(
        receiverRegistered = true, broadcastCount = 5, droppedByCap = 2, invalidReads = 1
    )

    /** ت-04: a read at registration (status 0), then two callbacks. */
    private val thermalStatus = listOf(
        ThermalStatusEvent(0L, 0),
        ThermalStatusEvent(95_000L, 2),
        ThermalStatusEvent(180_000L, 3)
    )

    private val policies = listOf(
        CpuPolicyInfo(0, "0 1 2 3", 1_804_800L, null),
        CpuPolicyInfo(4, "4 5 6", null, "/sys/devices/system/cpu/cpufreq/policy4/cpuinfo_max_freq: open failed: EACCES (Permission denied)"),
        CpuPolicyInfo(7, "7", 2_841_600L, null)
    )

    private fun writeScan(): Pair<String, ExtraChannelSampler> = runBlocking {
        val sampler = ExtraChannelSampler(layout.extras)
        val writer = TimeSeriesFileWriter(context)
        writer.enableV2Mode()
        writer.setVariableDefinitions(layout.variables)
        writer.startNewFile(
            TimeSeriesHeader(
                scanId = "scan-layout", deviceFingerprint = "fp", scanType = "general",
                appVersion = "test", androidApiLevel = 35, deviceModel = "UnitTest", cpuCores = 8,
                totalRamMb = 8192, startTimestamp = 1_000L, batteryLevelStart = 80, batteryTempStart = 25f
            )
        )
        for (i in 0 until 5) {
            val values = FloatArray(layout.sampleWidth)
            values[0] = (i * 250).toFloat()          // timestamp_ms
            values[1] = 30f + i                      // battery_temp_c
            values[4] = 80f - i                      // battery_level_pct
            values[6] = 40f + i                      // cpu_usage_pct
            values[10] = 60f                         // gpu_fps
            sampler.fill(values, V2_SAMPLE_VAR_COUNT, i)
            writer.addSample(TimeSeriesSample(ts = 10_000L + i, phase = 3, v = values))
        }
        val failures = sampler.failures()
        val footer = TimeSeriesFooter(
            endTimestamp = 20_000L, totalSamples = 5, isComplete = true, earlyStopReason = null,
            batteryLevelEnd = 75, maxBatteryTemp = 34f,
            cpuTempFinalC = null, cpuTempFinalSource = "absent:test fixture",
            fieldsVersion = 68,
            channelDiscovery = ChannelDiscoveryReport(
                attempted = listOf("cpu_temp_c", "charge_counter_raw", "cpu_freq_policy0_khz"),
                available = listOf("charge_counter_raw", "cpu_freq_policy0_khz"),
                blockedBySelinux = emptyList(),
                missing = listOf("cpu_temp_c"),
                summary = "discovery: 2/3 available, 0 blocked, 1 missing",
                reasons = mapOf("cpu_temp_c" to "readCpuTemp returned -1.0 (not > 0); the reader does not expose its error text")
            ),
            cpuPolicies = policies,
            channelReadFailures = failures.indices,
            channelReadFailureCounts = failures.counts,
            thermalHeadroomReadings = thermalHeadroom,
            thermalHeadroomState = "ok",
            thermalStatusEvents = thermalStatus,
            thermalStatusState = "ok",
            batteryPairs = batteryPairs,
            batteryPairsState = batteryPairsState
        )
        val path = writer.finalize(footer)
        assertNotNull("finalize must write the file", path)
        path!! to sampler
    }

    private fun report() = ScanReport(
        scanId = "scan-layout", scanType = ScanType.GENERAL, linkedScanId = null,
        deviceInfo = DeviceInfo("fp", "Model", "Mfr", "14", 34, "1080x2400", 8),
        scanMetadata = ScanMetadata(0L, 0L, 100, 95, 27f, 0L, true, null, "1.0.0"),
        functionalResults = emptyList(), timeSeriesSummary = null, antiTamperFlags = emptyList(),
        technicianInput = null, peerInput = null
    )

    // ───────────────────────── the file ─────────────────────────

    @Test
    fun `the file declares exactly the columns its samples have, in layout order`() {
        val (path, _) = writeScan()
        val data = runBlocking { TimeSeriesFileReader(context).readFromPath(path) }!!

        assertEquals(15, data.variables.size)
        assertEquals(
            listOf(
                "timestamp_ms", "battery_temp_c", "battery_voltage_mv", "battery_current_ua", "battery_level_pct",
                "cpu_freq_max_khz", "cpu_usage_pct", "package_power_mw", "storage_throughput_mbps",
                "memory_bandwidth_mbps", "gpu_fps",
                "charge_counter_raw", "cpu_freq_policy0_khz", "cpu_freq_policy4_khz", "cpu_freq_policy7_khz"
            ),
            data.variables.map { it.name }
        )
        data.variables.forEachIndexed { position, def -> assertEquals(position, def.index) }
        assertEquals("as_declared_by_device", data.variables[11].unit)
        assertEquals(5, data.samples.size)
        assertTrue("every sample is as wide as variables[]", data.samples.all { it.v.size == data.variables.size })
    }

    @Test
    fun `a failed read is a stale last-valid number in v and a declared index in the footer`() {
        val (path, _) = writeScan()
        val data = runBlocking { TimeSeriesFileReader(context).readFromPath(path) }!!
        val col = data.variables.associate { it.name to it.index }

        // charge counter: samples 1 and 2 failed -> they carry sample 0's value
        val chargeCol = col.getValue("charge_counter_raw")
        assertEquals(
            listOf(1_711_600f, 1_711_600f, 1_711_600f, 1_711_000f, 1_710_900f),
            data.samples.map { it.v[chargeCol] }
        )
        // policy 4: sample 3 failed -> 2_000_000 (its last valid), not 0 and not NaN
        val p4Col = col.getValue("cpu_freq_policy4_khz")
        assertEquals(
            listOf(2_419_200f, 2_419_200f, 2_000_000f, 2_000_000f, 2_419_200f),
            data.samples.map { it.v[p4Col] }
        )
        assertTrue(data.samples.all { s -> s.v.all { it.isFinite() } })

        assertEquals(
            mapOf(
                "charge_counter_raw" to listOf(1, 2),
                "cpu_freq_policy0_khz" to emptyList<Int>(),
                "cpu_freq_policy4_khz" to listOf(3),
                "cpu_freq_policy7_khz" to emptyList<Int>()
            ),
            data.footer.channelReadFailures
        )
        assertEquals(
            mapOf(
                "charge_counter_raw" to 2,
                "cpu_freq_policy0_khz" to 0,
                "cpu_freq_policy4_khz" to 1,
                "cpu_freq_policy7_khz" to 0
            ),
            data.footer.channelReadFailureCounts
        )
    }

    @Test
    fun `policies and discovery reasons survive the file`() {
        val (path, _) = writeScan()
        val footer = runBlocking { TimeSeriesFileReader(context).readFromPath(path) }!!.footer

        assertEquals(policies, footer.cpuPolicies)
        assertEquals(
            "readCpuTemp returned -1.0 (not > 0); the reader does not expose its error text",
            footer.channelDiscovery!!.reasons!!["cpu_temp_c"]
        )
    }

    @Test
    fun `the by-name summary reader serves the longer layout without declaring anything unresolved`() {
        val (path, _) = writeScan()
        val reader = TimeSeriesFileReader(context)
        val data = runBlocking { reader.readFromPath(path) }!!

        val summary = reader.calculateSummary(data)

        assertTrue(summary.unresolvedColumns.isEmpty())
        assertEquals(1000f, summary.durationMs, 0f)               // timestamp_ms: 0 .. 1000
        assertEquals(42f, summary.avgCpuUsage, 0.001f)            // cpu_usage_pct: 40..44
        assertEquals(60f, summary.avgGpuFps, 0.001f)              // gpu_fps
        assertEquals(34f, summary.maxBatteryTemp, 0f)             // battery_temp_c: 30..34
        assertEquals("cpu_temp_c is not a V2 column", -1f, summary.maxCpuTemp, 0f)
    }

    @Test
    fun `the thermal lists survive the file and reach the payload with their states`() {
        val (path, _) = writeScan()
        val data = runBlocking { TimeSeriesFileReader(context).readFromPath(path) }!!

        // the file: element for element, the NaN one with a null headroom and its state
        assertEquals(thermalHeadroom, data.footer.thermalHeadroomReadings)
        assertEquals("ok", data.footer.thermalHeadroomState)
        assertEquals(thermalStatus, data.footer.thermalStatusEvents)
        assertEquals("ok", data.footer.thermalStatusState)

        // the payload: the same elements as maps
        val fp = BehaviorFingerprintBuilder().build(report(), data)
        val readings = fp.timeSeries.footer.thermalHeadroomReadings!!
        assertEquals(listOf(0L, 10_000L, 20_000L, 30_000L), readings.map { it["t"] })
        assertEquals(listOf("ok", "nan", "exception:getThermalHeadroom: rate limited", "ok"), readings.map { it["state"] })
        assertEquals(listOf(0, 2, 3), fp.timeSeries.footer.thermalStatusEvents!!.map { it["status"] })
        assertEquals("ok", fp.timeSeries.footer.thermalHeadroomState)
        assertEquals("ok", fp.timeSeries.footer.thermalStatusState)

        // the wire (AnalysisRequestSerializer.gson, no serializeNulls): a headroom that is
        // null is an ABSENT key, and the element still says why
        val footer = wireGson.toJsonTree(fp).asJsonObject
            .getAsJsonObject("timeSeries").getAsJsonObject("footer")
        val wireReadings = footer.getAsJsonArray("thermalHeadroomReadings")
        assertEquals(4, wireReadings.size())
        assertEquals(0.42f, wireReadings[0].asJsonObject.get("headroom").asFloat, 0f)
        assertFalse("NaN is not written as a number", wireReadings[1].asJsonObject.has("headroom"))
        assertEquals("nan", wireReadings[1].asJsonObject.get("state").asString)
        assertFalse(wireReadings[2].asJsonObject.has("headroom"))
        assertTrue(wireReadings[2].asJsonObject.get("state").asString.startsWith("exception:"))
        val wireStatus = footer.getAsJsonArray("thermalStatusEvents")
        assertEquals(3, wireStatus.size())
        assertEquals(95_000L, wireStatus[1].asJsonObject.get("t").asLong)
        assertEquals(2, wireStatus[1].asJsonObject.get("status").asInt)
        assertEquals("ok", footer.get("thermalHeadroomState").asString)
        assertEquals("ok", footer.get("thermalStatusState").asString)
    }

    @Test
    fun `the raw battery pairs and their state survive the file and the payload`() {
        val (path, _) = writeScan()
        val data = runBlocking { TimeSeriesFileReader(context).readFromPath(path) }!!

        assertEquals(batteryPairs, data.footer.batteryPairs)
        assertEquals(batteryPairsState, data.footer.batteryPairsState)

        val fp = BehaviorFingerprintBuilder().build(report(), data)
        val footer = wireGson.toJsonTree(fp).asJsonObject
            .getAsJsonObject("timeSeries").getAsJsonObject("footer")

        val wire = footer.getAsJsonArray("batteryPairs")
        assertEquals(3, wire.size())
        val first = wire[0].asJsonObject
        assertEquals(2_000L, first.get("t").asLong)
        assertEquals("µV-sized voltage as the platform gave it, not divided", 4_350_000, first.get("voltageRaw").asInt)
        assertEquals("negative current as the platform gave it, no flip", -812, first.get("currentRaw").asInt)
        assertEquals(5_596L, first.get("chargeCounterRaw").asLong)
        assertEquals(72, first.get("levelRaw").asInt)
        assertEquals(100, first.get("levelScale").asInt)
        assertEquals(305, first.get("tempRaw").asInt)
        assertFalse("a failed read is an absent key, never a sentinel number", wire[1].asJsonObject.has("currentRaw"))

        val state = footer.getAsJsonObject("batteryPairsState")
        assertTrue(state.get("receiverRegistered").asBoolean)
        assertEquals(5, state.get("broadcastCount").asInt)
        assertEquals(2, state.get("droppedByCap").asInt)
        assertEquals(1, state.get("invalidReads").asInt)
    }

    // ───────────────────────── the request payload ─────────────────────────

    @Test
    fun `the payload built from what the reader returned carries the layout and the failure record`() {
        val (path, _) = writeScan()
        val data = runBlocking { TimeSeriesFileReader(context).readFromPath(path) }!!

        val fp = BehaviorFingerprintBuilder().build(report(), data)

        val ts = fp.timeSeries
        assertEquals(data.variables.map { it.name }, ts.variables.map { it.name })
        assertTrue(ts.samples.all { it.v.size == ts.variables.size })
        assertEquals(data.footer.channelReadFailures, ts.footer.channelReadFailures)
        assertEquals(data.footer.channelReadFailureCounts, ts.footer.channelReadFailureCounts)
        assertEquals(listOf(0, 4, 7), ts.footer.cpuPolicies!!.map { it["policy"] })
        assertEquals(
            "readCpuTemp returned -1.0 (not > 0); the reader does not expose its error text",
            (ts.footer.channelDiscovery!!["reasons"] as Map<*, *>)["cpu_temp_c"]
        )
    }

    @Test
    fun `on the wire an absent value is an absent key and a failure is a listed index`() {
        val (path, _) = writeScan()
        val data = runBlocking { TimeSeriesFileReader(context).readFromPath(path) }!!
        val fp = BehaviorFingerprintBuilder().build(report(), data)

        val json = wireGson.toJsonTree(fp).asJsonObject
        val footer = json.getAsJsonObject("timeSeries").getAsJsonObject("footer")

        // variables[] has no `index` on the payload (VariableDto): position is the index
        val variables = json.getAsJsonObject("timeSeries").getAsJsonArray("variables")
        assertEquals(15, variables.size())
        assertEquals("charge_counter_raw", variables[11].asJsonObject.get("name").asString)
        assertFalse(variables[11].asJsonObject.has("index"))

        // every sample row is 15 numbers
        val samples = json.getAsJsonObject("timeSeries").getAsJsonArray("samples")
        assertTrue(samples.all { it.asJsonObject.getAsJsonArray("v").size() == 15 })

        // failure record: every channel is a key, indices listed
        val failures = footer.getAsJsonObject("channelReadFailures")
        assertEquals(setOf("charge_counter_raw", "cpu_freq_policy0_khz", "cpu_freq_policy4_khz", "cpu_freq_policy7_khz"), failures.keySet())
        assertEquals("[1,2]", failures.get("charge_counter_raw").toString())
        assertEquals("[]", failures.get("cpu_freq_policy0_khz").toString())
        assertEquals(2, footer.getAsJsonObject("channelReadFailureCounts").get("charge_counter_raw").asInt)

        // policies: policy 4's missing max is an ABSENT key, and its reason is present
        val cpuPolicies = footer.getAsJsonArray("cpuPolicies")
        assertEquals(3, cpuPolicies.size())
        val policy4: JsonObject = cpuPolicies[1].asJsonObject
        assertEquals(4, policy4.get("policy").asInt)
        assertEquals("4 5 6", policy4.get("relatedCpus").asString)
        assertFalse("null becomes an absent key", policy4.has("cpuinfoMaxFreqKhz"))
        assertTrue(policy4.get("unreadableReason").asString.contains("EACCES"))
        val policy0: JsonObject = cpuPolicies[0].asJsonObject
        assertEquals(1_804_800L, policy0.get("cpuinfoMaxFreqKhz").asLong)
        assertFalse(policy0.has("unreadableReason"))

        // discovery reasons are on the wire
        assertTrue(
            footer.getAsJsonObject("channelDiscovery").getAsJsonObject("reasons").has("cpu_temp_c")
        )
    }
}
