package net.neuralcheck.service.stress.v2

import net.neuralcheck.data.local.files.ChannelNames
import net.neuralcheck.data.local.files.V2_SAMPLE_VAR_COUNT
import net.neuralcheck.service.stress.v2.ChannelDiscoveryProbe.ProbeOutcome
import net.neuralcheck.service.stress.v2.ChannelDiscoveryProbe.ProbeResult
import net.neuralcheck.util.ChannelRead
import net.neuralcheck.util.FileTextRead
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * fieldsVersion 68 — what channel discovery decides for a scan (S-01, S-02,
 * S-27 (d), S-35, N-01, N-07, N-22): which per-device channels get a column,
 * in which order, and the reason text of every channel that is not available.
 * The pure half ([ChannelDiscoveryProbe.assemble]) runs here with fake readers.
 */
class ChannelDiscoveryAssembleTest {

    private val base = CpuPolicyDiscovery.BASE_PATH

    private fun eacces(path: String) = "$path: open failed: EACCES (Permission denied)"
    private fun enoent(path: String) = "$path: open failed: ENOENT (No such file or directory)"

    private fun baseOutcomes(chargeCounter: ChannelRead): LinkedHashMap<String, ProbeOutcome> =
        linkedMapOf(
            ChannelNames.CPU_TEMP_C to ProbeOutcome.AVAILABLE,
            ChannelNames.GPU_TEMP_C to ProbeOutcome.noValue("readGpuTemp", "-1.0 (not > 0)"),
            ChannelNames.BATTERY_TEMP_C to ProbeOutcome.AVAILABLE,
            ChannelNames.CHARGE_COUNTER_RAW to ChannelDiscoveryProbe.outcomeOf(chargeCounter),
            ChannelNames.ENERGY_COUNTER to ProbeOutcome.noValue("snapshotHealth().energyCounterNwh", "null (null or 0)")
        )

    private fun policy(
        n: Int,
        cur: ChannelRead,
        related: String? = "$n",
        maxKhz: Long? = 2_000_000L,
        why: String? = null
    ) = CpuPolicyDiscovery.PolicyOutcome(
        policy = n,
        channelName = ChannelNames.cpuFreqPolicy(n),
        currentFreq = cur,
        info = net.neuralcheck.data.local.files.CpuPolicyInfo(n, related, maxKhz, why)
    )

    private fun assemble(
        charge: ChannelRead,
        policies: List<CpuPolicyDiscovery.PolicyOutcome>,
        readText: (String) -> FileTextRead = { FileTextRead.Failed("unused") }
    ) = ChannelDiscoveryProbe.assemble(
        baseOutcomes = baseOutcomes(charge),
        chargeCounter = charge,
        readChargeCounter = { ChannelRead.Value(123f) },
        policies = policies,
        readText = readText
    )

    // ── order and admission (S-01, N-22) ──

    @Test
    fun `admitted channels follow the base block - charge counter first then policies ascending`() {
        val result = assemble(
            charge = ChannelRead.Value(1_711_600f),
            // handed over out of order on purpose
            policies = listOf(
                policy(7, ChannelRead.Value(2_841_600f)),
                policy(0, ChannelRead.Value(1_804_800f)),
                policy(4, ChannelRead.Value(2_419_200f))
            )
        )

        assertEquals(
            listOf("charge_counter_raw", "cpu_freq_policy0_khz", "cpu_freq_policy4_khz", "cpu_freq_policy7_khz"),
            result.layout.extras.map { it.name }
        )
        assertEquals(V2_SAMPLE_VAR_COUNT + 4, result.layout.sampleWidth)
        assertEquals(listOf(0, 4, 7), result.cpuPolicies.map { it.policy })
    }

    @Test
    fun `a charge counter reading of zero is admitted - zero is a value`() {
        // S-27 (d): the probe used to count 0L as missing (snapshotHealth maps 0 to null).
        val result = assemble(charge = ChannelRead.Value(0f), policies = emptyList())

        assertEquals(listOf("charge_counter_raw"), result.layout.extras.map { it.name })
        assertEquals(0f, result.layout.extras.single().seed, 0f)
        assertTrue(ChannelNames.CHARGE_COUNTER_RAW in result.report.available)
    }

    @Test
    fun `charge counter column carries the contract unit and source and reads through the given reader`() {
        val result = assemble(charge = ChannelRead.Value(5596f), policies = emptyList())

        val v = result.layout.variables[V2_SAMPLE_VAR_COUNT]
        assertEquals("charge_counter_raw", v.name)
        assertEquals("as_declared_by_device", v.unit)
        assertEquals("BatteryManager.getLongProperty(BATTERY_PROPERTY_CHARGE_COUNTER)", v.source)
        assertEquals(ChannelRead.Value(123f), result.layout.extras.single().read())
    }

    @Test
    fun `a channel that fails discovery gets no column - its state and reason are in the report`() {
        val denied = ChannelRead.Failed("getLongProperty: java.lang.SecurityException: Permission denied")
        val result = assemble(charge = denied, policies = emptyList())

        assertTrue(result.layout.extras.isEmpty())
        assertEquals(V2_SAMPLE_VAR_COUNT, result.layout.sampleWidth)
        assertFalse(ChannelNames.CHARGE_COUNTER_RAW in result.report.available)
        // the TEXT said refusal -> legacy blocked list
        assertTrue(ChannelNames.CHARGE_COUNTER_RAW in result.report.blockedBySelinux)
        assertEquals(denied.reason, result.report.reasons!![ChannelNames.CHARGE_COUNTER_RAW])
    }

    @Test
    fun `a policy channel reads through the file reader and parses its text`() {
        val path = "$base/policy4/scaling_cur_freq"
        val result = assemble(
            charge = ChannelRead.Failed("x"),
            policies = listOf(policy(4, ChannelRead.Value(1_000_000f))),
            readText = { p -> if (p == path) FileTextRead.Text("2419200") else FileTextRead.Failed("wrong path $p") }
        )

        val ch = result.layout.extras.single()
        assertEquals(path, ch.source)
        assertEquals("kHz", ch.unit)
        assertEquals(1_000_000f, ch.seed, 0f)
        assertEquals("per-sample read is a fresh read, not the seed", ChannelRead.Value(2_419_200f), ch.read())
    }

    // ── classification by text (rule 21) and reasons (N-07, S-35) ──

    @Test
    fun `a refused policy goes to the blocked list and an absent charge counter to missing - decided by text`() {
        val refused = policy(
            4,
            ChannelRead.Failed(eacces("$base/policy4/scaling_cur_freq")),
            why = eacces("$base/policy4/cpuinfo_max_freq")
        )
        val result = assemble(
            charge = ChannelRead.Failed(enoent("charge")),
            policies = listOf(refused)
        )

        val report = result.report
        assertTrue("cpu_freq_policy4_khz" in report.blockedBySelinux)
        assertTrue("cpu_freq_policy4_khz" in report.attempted)
        assertFalse("cpu_freq_policy4_khz" in report.available)
        assertTrue(ChannelNames.CHARGE_COUNTER_RAW in report.missing)
        assertTrue(report.reasons!!.getValue("cpu_freq_policy4_khz").contains("EACCES"))
        assertTrue(report.reasons!!.getValue(ChannelNames.CHARGE_COUNTER_RAW).contains("ENOENT"))
        // the policy is still described in the raw topology record
        assertEquals(listOf(4), result.cpuPolicies.map { it.policy })
        assertNotNull(result.cpuPolicies.single().unreadableReason)
        assertTrue(result.layout.extras.isEmpty())
    }

    @Test
    fun `outcomeOf decides blocked vs missing from the failure text, not from a class`() {
        assertEquals(
            ProbeResult.BLOCKED,
            ChannelDiscoveryProbe.outcomeOf(ChannelRead.Failed(eacces("p"))).result
        )
        assertEquals(
            ProbeResult.MISSING,
            ChannelDiscoveryProbe.outcomeOf(ChannelRead.Failed(enoent("p"))).result
        )
        // neither named -> MISSING (the legacy lists cannot say "other") with the exact text kept
        val other = ChannelDiscoveryProbe.outcomeOf(ChannelRead.Failed("read failed: EIO (I/O error)"))
        assertEquals(ProbeResult.MISSING, other.result)
        assertEquals("read failed: EIO (I/O error)", other.reason)
        assertEquals(ProbeResult.AVAILABLE, ChannelDiscoveryProbe.outcomeOf(ChannelRead.Value(0f)).result)
        assertNull(ChannelDiscoveryProbe.outcomeOf(ChannelRead.Value(1f)).reason)
    }

    @Test
    fun `reasons exist for every channel that is not available and only for those`() {
        val result = assemble(
            charge = ChannelRead.Value(10f),
            policies = listOf(policy(0, ChannelRead.Value(1f)))
        )

        val report = result.report
        assertEquals(
            report.attempted.toSet() - report.available.toSet(),
            report.reasons!!.keys
        )
        assertTrue(ChannelNames.GPU_TEMP_C in report.reasons!!)
        assertTrue(ChannelNames.ENERGY_COUNTER in report.reasons!!)
        assertFalse(ChannelNames.CPU_TEMP_C in report.reasons!!)
        assertFalse(ChannelNames.CHARGE_COUNTER_RAW in report.reasons!!)
    }

    @Test
    fun `when every channel is available reasons is an empty map, not null`() {
        val all = linkedMapOf(ChannelNames.CPU_TEMP_C to ProbeOutcome.AVAILABLE)
        val result = ChannelDiscoveryProbe.assemble(
            baseOutcomes = all,
            chargeCounter = ChannelRead.Failed("n/a"),
            readChargeCounter = { ChannelRead.Failed("n/a") },
            policies = emptyList(),
            readText = { FileTextRead.Failed("unused") }
        )
        // the charge counter was not in baseOutcomes here, so only cpu_temp_c is attempted
        assertEquals(emptyMap<String, String>(), result.report.reasons)
    }

    @Test
    fun `a reason is capped at 200 characters`() {
        val long = "z".repeat(500)
        val result = assemble(charge = ChannelRead.Failed(long), policies = emptyList())

        assertEquals(200, result.report.reasons!!.getValue(ChannelNames.CHARGE_COUNTER_RAW).length)
    }

    @Test
    fun `attempted keeps the stable base order and appends policies after it`() {
        val result = assemble(
            charge = ChannelRead.Value(1f),
            policies = listOf(policy(0, ChannelRead.Value(1f)), policy(4, ChannelRead.Value(1f)))
        )
        assertEquals(
            listOf(
                ChannelNames.CPU_TEMP_C, ChannelNames.GPU_TEMP_C, ChannelNames.BATTERY_TEMP_C,
                ChannelNames.CHARGE_COUNTER_RAW, ChannelNames.ENERGY_COUNTER,
                "cpu_freq_policy0_khz", "cpu_freq_policy4_khz"
            ),
            result.report.attempted
        )
    }

    @Test
    fun `summary counts what was attempted`() {
        val result = assemble(charge = ChannelRead.Value(1f), policies = listOf(policy(0, ChannelRead.Value(1f))))
        val r = result.report
        assertEquals(
            "discovery: ${r.available.size}/${r.attempted.size} available, " +
                "${r.blockedBySelinux.size} blocked, ${r.missing.size} missing",
            r.summary
        )
        assertEquals(r.attempted.size, r.available.size + r.blockedBySelinux.size + r.missing.size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `an outcome that is not available must carry its reason`() {
        ProbeOutcome(ProbeResult.MISSING, null)
    }
}
