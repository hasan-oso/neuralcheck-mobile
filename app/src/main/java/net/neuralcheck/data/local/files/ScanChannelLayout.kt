package net.neuralcheck.data.local.files

import net.neuralcheck.util.ChannelRead

/**
 * One per-device channel appended after the V2 base block `v[0..10]`
 * (fieldsVersion 68, docs/FV68_CONTRACT.md §٢.١, S-01 / S-27): it exists in
 * this scan only because it PASSED discovery, and [read] is the exact reader
 * the sampler calls once per sample.
 *
 * @property seed the value discovery read when it admitted the channel. It is
 *   what a sample carries when the very first reads of the scan fail, before
 *   any later valid value exists (S-27: "the last valid value"; the seed is
 *   the last valid value known at scan start). Always finite.
 * @property read one read of the source. Must not throw; if it does the
 *   sampler records a failure with the exception text.
 */
class ExtraChannel(
    val name: String,
    val unit: String,
    val source: String,
    val seed: Float,
    val read: () -> ChannelRead
) {
    init {
        require(name.isNotBlank()) { "channel name must not be blank" }
        require(seed.isFinite()) { "channel '$name': seed must be finite, was $seed" }
    }
}

/**
 * The column layout of ONE scan's samples (fieldsVersion 68): the frozen base
 * block ([V2_VARIABLE_DEFINITIONS], `v[0..10]`) followed by [extras] in the
 * order they were given — the producer (ChannelDiscoveryProbe) gives them in
 * the contract order N-22: `charge_counter_raw`, then `cpu_freq_policy{N}_khz`
 * with N ascending. [variables] is what that scan's file declares as
 * `variables[]`, and every sample of the scan is exactly [sampleWidth] floats
 * wide, so a column is found by NAME in [variables] — its index is only
 * meaningful inside the same file.
 */
class ScanChannelLayout(val extras: List<ExtraChannel>) {

    init {
        val baseNames = V2_VARIABLE_DEFINITIONS.map { it.name }.toSet()
        val seen = HashSet<String>()
        for (channel in extras) {
            require(channel.name !in baseNames) {
                "extra channel '${channel.name}' collides with a base column"
            }
            require(seen.add(channel.name)) {
                "extra channel '${channel.name}' appears twice"
            }
        }
    }

    /** `variables[]` of this scan's file: base block, then [extras] at index 11, 12, ... */
    val variables: List<VariableDefinition> = V2_VARIABLE_DEFINITIONS +
        extras.mapIndexed { i, c -> VariableDefinition(V2_SAMPLE_VAR_COUNT + i, c.name, c.unit, c.source) }

    /** Floats per sample of this scan. */
    val sampleWidth: Int = V2_SAMPLE_VAR_COUNT + extras.size

    companion object {
        /** No per-device channel: exactly the 11-column base. */
        val BASE_ONLY = ScanChannelLayout(emptyList())
    }
}

/**
 * Samples whose read of a per-device channel failed (S-27). Present in the
 * footer so a failure is declared at the top level, not hidden in `v[]`: the
 * array stays numeric (the failed read carries the last valid value), and
 * this record names exactly which samples to leave out.
 *
 * @property indices per channel, the 0-based positions in `samples[]` whose
 *   read failed, ascending, at most [CAP] of them. EVERY extra channel of the
 *   scan is a key (empty list = no failed read), so a missing key means "not
 *   tracked" — a file written without this record — never "no failures".
 * @property counts per channel, the TOTAL number of failed reads, including
 *   those past [CAP]; `counts[c] > indices[c].size` means the list is
 *   truncated and the reader must treat the channel's tail as unverified.
 */
data class ChannelReadFailures(
    val indices: Map<String, List<Int>>,
    val counts: Map<String, Int>
) {
    companion object {
        /** Cap on the indices kept per channel (same bound as batteryPairs, S-10). */
        const val CAP: Int = 200

        val NONE = ChannelReadFailures(emptyMap(), emptyMap())
    }
}
