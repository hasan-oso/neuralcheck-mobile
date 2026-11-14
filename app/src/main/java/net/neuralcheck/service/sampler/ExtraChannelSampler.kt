package net.neuralcheck.service.sampler

import net.neuralcheck.data.local.files.ChannelReadFailures
import net.neuralcheck.data.local.files.ExtraChannel
import net.neuralcheck.util.ChannelRead
import net.neuralcheck.util.ReasonText

/**
 * Reads the per-device channels of one scan into the tail of each sample
 * (fieldsVersion 68, S-27), and keeps the record of every read that failed.
 *
 * The decision (order 2026-10-08): `v[]` stays numeric — "no NaN, no null in
 * the array" (S-01) — so a failed read cannot be written as a gap. It
 * carries the LAST VALID value, and the failure is declared separately, per
 * channel, as the indices of the failed samples ([failures]) so that a reader
 * excludes exactly those samples. The value left in `v[]` at a failed index
 * is therefore stale by construction and must never be read as a
 * measurement.
 *
 * Two readings are failures although the source "answered": a non-finite
 * value (Gson cannot write `NaN`/`Infinity`: it would fail serialisation of
 * the whole sample file and of the request payload), and a reader that
 * throws.
 *
 * Thread-safety: the sampler calls [fill] from its single sampling thread
 * while the service may call [failures] from another at the end of the scan,
 * hence the monitor.
 */
class ExtraChannelSampler(val channels: List<ExtraChannel>) {

    /** Number of floats this sampler adds to every sample. */
    val width: Int get() = channels.size

    private val lastValid = FloatArray(channels.size)
    private val failureIndices = List(channels.size) { ArrayList<Int>() }
    private val failureCounts = IntArray(channels.size)

    init {
        reset()
    }

    /**
     * Starts a scan over: the last valid value of each channel goes back to
     * its discovery seed and every failure record is cleared.
     */
    @Synchronized
    fun reset() {
        for (i in channels.indices) {
            lastValid[i] = channels[i].seed
            failureIndices[i].clear()
            failureCounts[i] = 0
        }
    }

    /**
     * Reads every channel once and writes the results into [target] from
     * [offset]. [sampleIndex] is the 0-based position this sample will have
     * in `samples[]`; it is what a failure is recorded under.
     */
    @Synchronized
    fun fill(target: FloatArray, offset: Int, sampleIndex: Int) {
        for (i in channels.indices) {
            val read: ChannelRead = try {
                channels[i].read()
            } catch (t: Exception) {
                ChannelRead.Failed(ReasonText.of(t))
            }
            val value: Float? = when (read) {
                is ChannelRead.Value -> read.value.takeIf { it.isFinite() }
                is ChannelRead.Failed -> null
            }
            if (value != null) {
                lastValid[i] = value
                target[offset + i] = value
            } else {
                target[offset + i] = lastValid[i]
                failureCounts[i]++
                if (failureIndices[i].size < ChannelReadFailures.CAP) failureIndices[i].add(sampleIndex)
            }
        }
    }

    /**
     * The failure record of the scan so far. Every channel is a key (see
     * [ChannelReadFailures]); a channel with no failed read maps to an empty
     * list and a count of 0.
     */
    @Synchronized
    fun failures(): ChannelReadFailures = ChannelReadFailures(
        indices = channels.indices.associate { channels[it].name to failureIndices[it].toList() },
        counts = channels.indices.associate { channels[it].name to failureCounts[it] }
    )
}
