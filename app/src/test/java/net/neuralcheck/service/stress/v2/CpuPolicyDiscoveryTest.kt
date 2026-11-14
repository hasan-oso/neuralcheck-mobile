package net.neuralcheck.service.stress.v2

import net.neuralcheck.util.ChannelRead
import net.neuralcheck.util.FileTextRead
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * fieldsVersion 68, S-05 / S-06 / N-06 / S-35: policies are found by READING
 * each candidate's files and telling the failures apart by their TEXT
 * (rule 21), against a fake file system whose messages are the ones Android
 * produces.
 */
class CpuPolicyDiscoveryTest {

    private val base = CpuPolicyDiscovery.BASE_PATH

    private fun enoent(path: String) =
        FileTextRead.Failed("$path: open failed: ENOENT (No such file or directory)")

    private fun eacces(path: String) =
        FileTextRead.Failed("$path: open failed: EACCES (Permission denied)")

    /** A fake sysfs: a path not in [files] behaves like a missing file. */
    private fun fs(files: Map<String, FileTextRead>): (String) -> FileTextRead =
        { path -> files[path] ?: enoent(path) }

    private fun policyFiles(
        n: Int,
        cur: FileTextRead,
        related: FileTextRead,
        max: FileTextRead
    ) = mapOf(
        "$base/policy$n/scaling_cur_freq" to cur,
        "$base/policy$n/related_cpus" to related,
        "$base/policy$n/cpuinfo_max_freq" to max
    )

    private fun ok(text: String) = FileTextRead.Text(text)

    @Test
    fun `three readable policies are found in ascending order with raw topology`() {
        val files = policyFiles(0, ok("1804800"), ok("0 1 2 3"), ok("1804800")) +
            policyFiles(4, ok("2419200"), ok("4 5 6"), ok("2419200")) +
            policyFiles(7, ok("2841600"), ok("7"), ok("2841600"))

        val found = CpuPolicyDiscovery.discover(candidateCount = 8, read = fs(files))

        assertEquals(listOf(0, 4, 7), found.map { it.policy })
        assertEquals(
            listOf("cpu_freq_policy0_khz", "cpu_freq_policy4_khz", "cpu_freq_policy7_khz"),
            found.map { it.channelName }
        )
        assertEquals(ChannelRead.Value(2_419_200f), found[1].currentFreq)
        // topology is raw: related_cpus text verbatim, no little/mid/big label
        assertEquals("4 5 6", found[1].info.relatedCpus)
        assertEquals(2_419_200L, found[1].info.cpuinfoMaxFreqKhz)
        assertNull(found[1].info.unreadableReason)
    }

    @Test
    fun `candidates whose every file is ENOENT leave no trace`() {
        val found = CpuPolicyDiscovery.discover(candidateCount = 8, read = fs(emptyMap()))
        assertTrue(found.isEmpty())
    }

    @Test
    fun `ENOTDIR on the policy directory is absence too`() {
        val read = { path: String ->
            FileTextRead.Failed("$path: open failed: ENOTDIR (Not a directory)")
        }
        assertTrue(CpuPolicyDiscovery.discover(4, read).isEmpty())
    }

    @Test
    fun `a policy that refuses the current frequency but answers the topology is kept with its reason`() {
        val p = "$base/policy4"
        val files = mapOf(
            "$p/scaling_cur_freq" to eacces("$p/scaling_cur_freq"),
            "$p/related_cpus" to ok("4 5 6"),
            "$p/cpuinfo_max_freq" to eacces("$p/cpuinfo_max_freq")
        )

        val found = CpuPolicyDiscovery.discover(8, fs(files)).single()

        assertEquals(4, found.policy)
        val freq = found.currentFreq as ChannelRead.Failed
        assertTrue(freq.reason, freq.reason.contains("EACCES"))
        assertEquals("4 5 6", found.info.relatedCpus)
        assertNull("max was refused, so it is absent", found.info.cpuinfoMaxFreqKhz)
        val why = found.info.unreadableReason!!
        assertTrue(why, why.contains("cpuinfo_max_freq") && why.contains("EACCES"))
    }

    @Test
    fun `a policy whose every file is refused still exists - EACCES is not ENOENT`() {
        val p = "$base/policy0"
        val files = mapOf(
            "$p/scaling_cur_freq" to eacces("$p/scaling_cur_freq"),
            "$p/related_cpus" to eacces("$p/related_cpus"),
            "$p/cpuinfo_max_freq" to eacces("$p/cpuinfo_max_freq")
        )

        val found = CpuPolicyDiscovery.discover(1, fs(files)).single()

        assertEquals(0, found.policy)
        assertNull(found.info.relatedCpus)
        assertNull(found.info.cpuinfoMaxFreqKhz)
        val why = found.info.unreadableReason!!
        assertTrue(why.contains("related_cpus") && why.contains("cpuinfo_max_freq"))
        assertTrue("reason texts joined, capped at 200 chars: ${why.length}", why.length <= 200)
        assertTrue(found.currentFreq is ChannelRead.Failed)
    }

    @Test
    fun `a failure text that names neither EACCES nor ENOENT is not treated as absence`() {
        val p = "$base/policy2"
        val files = mapOf(
            "$p/scaling_cur_freq" to FileTextRead.Failed("read failed: EIO (I/O error)")
        )
        val found = CpuPolicyDiscovery.discover(4, fs(files)).single()
        assertEquals(2, found.policy)
        assertEquals(ChannelRead.Failed("read failed: EIO (I/O error)"), found.currentFreq)
    }

    @Test
    fun `a zero frequency is a value`() {
        val files = policyFiles(0, ok("0"), ok("0 1"), ok("1000000"))
        val found = CpuPolicyDiscovery.discover(1, fs(files)).single()
        assertEquals(ChannelRead.Value(0f), found.currentFreq)
    }

    @Test
    fun `content that is not an integer is a failure that quotes it, capped`() {
        val files = policyFiles(0, ok("<unsupported>" + "x".repeat(300)), ok("0"), ok("not a number"))
        val found = CpuPolicyDiscovery.discover(1, fs(files)).single()

        val freq = found.currentFreq as ChannelRead.Failed
        assertTrue(freq.reason, freq.reason.contains("<unsupported>"))
        assertTrue("reason is capped at 200: ${freq.reason.length}", freq.reason.length <= 200)
        assertNull(found.info.cpuinfoMaxFreqKhz)
        assertTrue(found.info.unreadableReason!!.contains("not an integer"))
    }

    @Test
    fun `parseKhz maps a text read to a channel reading`() {
        assertEquals(ChannelRead.Value(1_200_000f), CpuPolicyDiscovery.parseKhz("f", ok("1200000")))
        assertEquals(ChannelRead.Failed("boom"), CpuPolicyDiscovery.parseKhz("f", FileTextRead.Failed("boom")))
        assertTrue(CpuPolicyDiscovery.parseKhz("f", ok("")) is ChannelRead.Failed)
    }

    @Test
    fun `candidate count bounds the search`() {
        val files = policyFiles(3, ok("1"), ok("3"), ok("1"))
        assertTrue(CpuPolicyDiscovery.discover(candidateCount = 3, read = fs(files)).isEmpty())
        assertEquals(listOf(3), CpuPolicyDiscovery.discover(candidateCount = 4, read = fs(files)).map { it.policy })
    }
}
