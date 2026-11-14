package net.neuralcheck.service.stress.v2

import net.neuralcheck.data.local.files.ChannelNames
import net.neuralcheck.data.local.files.CpuPolicyInfo
import net.neuralcheck.util.ChannelRead
import net.neuralcheck.util.FailureText
import net.neuralcheck.util.FileTextRead
import net.neuralcheck.util.ReasonText

/**
 * fieldsVersion 68 (S-05, S-06, N-06): which cpufreq policies this device has,
 * as the kernel exposes them — one channel `cpu_freq_policy{N}_khz` per
 * policy, no little / mid / big naming (that classification is an inference
 * and belongs to the server, rule 20).
 *
 * HOW the policies are found. A policy `N` is the first CPU of its cluster,
 * so `N < cpuCount`; each candidate `0 until cpuCount` is tried by READING
 * its files, not by listing the directory. Reading needs only the traversal
 * permission, while listing `/sys/devices/system/cpu/cpufreq` can be refused
 * on a device that still lets the files be read — and the open question of
 * whether SELinux allows the listing on every model is unanswered from this
 * repository (contract ت-05). The error TEXT then does the discriminating
 * (rule 21): an `ENOENT` on every file of candidate N means there is no
 * policy N and it leaves no trace; an `EACCES` (or any text that is not
 * ENOENT) on a file means the policy is there and unreadable, and it is
 * reported with that text instead of being dropped.
 *
 * Pure: the file system is the [read] function, so the discovery runs under a
 * JVM test with a fake one.
 */
object CpuPolicyDiscovery {

    const val BASE_PATH = "/sys/devices/system/cpu/cpufreq"
    private const val CUR_FREQ = "scaling_cur_freq"
    private const val MAX_FREQ = "cpuinfo_max_freq"
    private const val RELATED = "related_cpus"

    /** Longest slice of file content quoted in an "unparsable" reason. */
    private const val QUOTED_CONTENT_CHARS = 40

    /**
     * What discovery found for one policy.
     *
     * @property channelName `cpu_freq_policy{N}_khz`.
     * @property currentFreq the read of `scaling_cur_freq` that discovery did:
     *   [ChannelRead.Value] admits the channel (the value seeds it);
     *   [ChannelRead.Failed] leaves the policy as a [CpuPolicyInfo] only.
     * @property info the policy's raw topology record for the footer.
     */
    data class PolicyOutcome(
        val policy: Int,
        val channelName: String,
        val currentFreq: ChannelRead,
        val info: CpuPolicyInfo
    )

    fun curFreqPath(policy: Int): String = "$BASE_PATH/policy$policy/$CUR_FREQ"

    /**
     * Parses the content of a `scaling_cur_freq` read into a channel reading.
     * A `0` is a value (S-27 (d) applies to every raw channel: 0 is a value
     * and the server decides); only text that is not an integer is a failure,
     * and it is quoted (capped) so the failure explains itself.
     */
    fun parseKhz(file: String, read: FileTextRead): ChannelRead = when (read) {
        is FileTextRead.Failed -> ChannelRead.Failed(read.reason)
        is FileTextRead.Text -> {
            val khz = read.text.toLongOrNull()
            if (khz != null) {
                ChannelRead.Value(khz.toFloat())
            } else {
                ChannelRead.Failed(
                    ReasonText.cap(
                        "$file content is not an integer: '${read.text.take(QUOTED_CONTENT_CHARS)}'"
                    )
                )
            }
        }
    }

    /**
     * Discovers the policies among candidates `0 until candidateCount`,
     * returned in ascending N (the order the channels will have, N-22).
     */
    fun discover(candidateCount: Int, read: (path: String) -> FileTextRead): List<PolicyOutcome> {
        val found = ArrayList<PolicyOutcome>()
        for (policy in 0 until candidateCount) {
            val dir = "$BASE_PATH/policy$policy"
            val cur = read("$dir/$CUR_FREQ")
            val related = read("$dir/$RELATED")
            val max = read("$dir/$MAX_FREQ")

            // A policy exists if ANY of its files answered, or refused for a
            // reason other than "no such file".
            val exists = listOf(cur, related, max).any { r ->
                r is FileTextRead.Text ||
                    (r is FileTextRead.Failed && FailureText.classify(r.reason) != FailureText.Kind.ABSENT)
            }
            if (!exists) continue

            val maxKhz: Long? = (max as? FileTextRead.Text)?.text?.toLongOrNull()
            val failures = ArrayList<String>()
            if (related is FileTextRead.Failed) failures += related.reason
            when (max) {
                is FileTextRead.Failed -> failures += max.reason
                is FileTextRead.Text -> if (maxKhz == null) {
                    failures += "$dir/$MAX_FREQ content is not an integer: '${max.text.take(QUOTED_CONTENT_CHARS)}'"
                }
            }

            found += PolicyOutcome(
                policy = policy,
                channelName = ChannelNames.cpuFreqPolicy(policy),
                currentFreq = parseKhz("$dir/$CUR_FREQ", cur),
                info = CpuPolicyInfo(
                    policy = policy,
                    relatedCpus = (related as? FileTextRead.Text)?.text,
                    cpuinfoMaxFreqKhz = maxKhz,
                    unreadableReason = failures.takeIf { it.isNotEmpty() }
                        ?.let { ReasonText.cap(it.joinToString(" | ")) }
                )
            )
        }
        return found
    }
}
