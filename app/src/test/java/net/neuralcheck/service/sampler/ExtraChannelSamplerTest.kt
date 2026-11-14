package net.neuralcheck.service.sampler

import net.neuralcheck.data.local.files.ChannelReadFailures
import net.neuralcheck.data.local.files.ExtraChannel
import net.neuralcheck.util.ChannelRead
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * fieldsVersion 68, S-27 (order 2026-10-08): "آخر قيمة صالحة في المصفوفة +
 * قائمة العيّنات الفاشلة لكل قناة في التذييل". v[] stays numeric; a failed
 * read carries the last valid value and is DECLARED in the failure record.
 */
class ExtraChannelSamplerTest {

    /** A channel whose successive reads come from [script]; when it runs out it repeats the last. */
    private fun scripted(name: String, seed: Float, vararg script: ChannelRead): ExtraChannel {
        val queue = ArrayDeque(script.toList())
        var last: ChannelRead = script.last()
        return ExtraChannel(name, "u", "src", seed) {
            if (queue.isNotEmpty()) last = queue.removeFirst()
            last
        }
    }

    private fun value(v: Float) = ChannelRead.Value(v)
    private fun failed(text: String) = ChannelRead.Failed(text)

    private fun fillSamples(sampler: ExtraChannelSampler, count: Int, base: Int = 11): List<FloatArray> =
        (0 until count).map { i ->
            FloatArray(base + sampler.width).also { sampler.fill(it, base, i) }
        }

    @Test
    fun `clean reads are written at the offset in channel order and no failure is recorded`() {
        val sampler = ExtraChannelSampler(
            listOf(
                scripted("charge_counter_raw", 1f, value(100f), value(101f)),
                scripted("cpu_freq_policy0_khz", 1f, value(300_000f), value(400_000f))
            )
        )
        val samples = fillSamples(sampler, 2)

        assertEquals(100f, samples[0][11], 0f)
        assertEquals(300_000f, samples[0][12], 0f)
        assertEquals(101f, samples[1][11], 0f)
        assertEquals(400_000f, samples[1][12], 0f)
        // the base block is left alone
        assertTrue((0 until 11).all { samples[0][it] == 0f })

        val failures = sampler.failures()
        assertEquals(mapOf("charge_counter_raw" to emptyList<Int>(), "cpu_freq_policy0_khz" to emptyList<Int>()), failures.indices)
        assertEquals(mapOf("charge_counter_raw" to 0, "cpu_freq_policy0_khz" to 0), failures.counts)
    }

    @Test
    fun `a failed read carries the last valid value and its sample index is recorded`() {
        val sampler = ExtraChannelSampler(
            listOf(scripted("charge_counter_raw", 5f, value(100f), failed("EIO"), failed("EIO"), value(103f)))
        )
        val s = fillSamples(sampler, 4)

        assertEquals(100f, s[0][11], 0f)
        assertEquals("sample 1 failed: stale 100, not 0 and not NaN", 100f, s[1][11], 0f)
        assertEquals(100f, s[2][11], 0f)
        assertEquals("a later valid read resumes", 103f, s[3][11], 0f)
        assertEquals(listOf(1, 2), sampler.failures().indices.getValue("charge_counter_raw"))
        assertEquals(2, sampler.failures().counts.getValue("charge_counter_raw"))
    }

    @Test
    fun `failures before any valid read fall back to the discovery seed`() {
        val sampler = ExtraChannelSampler(
            listOf(scripted("cpu_freq_policy4_khz", 1_785_600f, failed("EACCES"), failed("EACCES"), value(2_000_000f)))
        )
        val s = fillSamples(sampler, 3)

        assertEquals("seed = the value discovery admitted the channel with", 1_785_600f, s[0][11], 0f)
        assertEquals(1_785_600f, s[1][11], 0f)
        assertEquals(2_000_000f, s[2][11], 0f)
        assertEquals(listOf(0, 1), sampler.failures().indices.getValue("cpu_freq_policy4_khz"))
    }

    @Test
    fun `a zero reading is a value, not a failure`() {
        // S-27 (d): 0 is a value and the server decides.
        val sampler = ExtraChannelSampler(listOf(scripted("charge_counter_raw", 7f, value(0f), value(0f))))
        val s = fillSamples(sampler, 2)

        assertEquals(0f, s[0][11], 0f)
        assertEquals(0f, s[1][11], 0f)
        assertEquals(emptyList<Int>(), sampler.failures().indices.getValue("charge_counter_raw"))
    }

    @Test
    fun `non-finite values never reach the array - they are failures carrying the last valid value`() {
        // Gson cannot write NaN / Infinity: one of them would fail the
        // serialisation of the whole sample file and of the request payload.
        val sampler = ExtraChannelSampler(
            listOf(
                scripted(
                    "charge_counter_raw", 1f,
                    value(50f), value(Float.NaN), value(Float.POSITIVE_INFINITY), value(Float.NEGATIVE_INFINITY), value(60f)
                )
            )
        )
        val s = fillSamples(sampler, 5)

        assertTrue(s.all { sample -> sample[11].isFinite() })
        assertEquals(listOf(50f, 50f, 50f, 50f, 60f), s.map { it[11] })
        assertEquals(listOf(1, 2, 3), sampler.failures().indices.getValue("charge_counter_raw"))
    }

    @Test
    fun `a reader that throws is a failure, not a crash`() {
        var calls = 0
        val sampler = ExtraChannelSampler(
            listOf(ExtraChannel("cpu_freq_policy0_khz", "kHz", "src", 9f) {
                calls++
                if (calls == 2) throw IllegalStateException("binder died")
                ChannelRead.Value(calls * 10f)
            })
        )
        val s = fillSamples(sampler, 3)

        assertEquals(listOf(10f, 10f, 30f), s.map { it[11] })
        assertEquals(listOf(1), sampler.failures().indices.getValue("cpu_freq_policy0_khz"))
    }

    @Test
    fun `indices are capped at 200 per channel while the count keeps going`() {
        val sampler = ExtraChannelSampler(listOf(scripted("charge_counter_raw", 3f, failed("always"))))
        fillSamples(sampler, 250)

        val failures = sampler.failures()
        val indices = failures.indices.getValue("charge_counter_raw")
        assertEquals(ChannelReadFailures.CAP, 200)
        assertEquals(200, indices.size)
        assertEquals("the first 200, ascending", (0 until 200).toList(), indices)
        assertEquals("the count is the true total", 250, failures.counts.getValue("charge_counter_raw"))
    }

    @Test
    fun `channels fail independently`() {
        val sampler = ExtraChannelSampler(
            listOf(
                scripted("a", 1f, value(1f), value(2f), value(3f)),
                scripted("b", 2f, value(10f), failed("x"), value(30f))
            )
        )
        fillSamples(sampler, 3)

        assertEquals(emptyList<Int>(), sampler.failures().indices.getValue("a"))
        assertEquals(listOf(1), sampler.failures().indices.getValue("b"))
        assertEquals(mapOf("a" to 0, "b" to 1), sampler.failures().counts)
    }

    @Test
    fun `reset starts the scan over - seeds back, failure record cleared`() {
        val sampler = ExtraChannelSampler(listOf(scripted("c", 4f, value(100f), failed("x"))))
        fillSamples(sampler, 2)
        assertEquals(1, sampler.failures().counts.getValue("c"))

        sampler.reset()

        assertEquals(0, sampler.failures().counts.getValue("c"))
        assertEquals(emptyList<Int>(), sampler.failures().indices.getValue("c"))
        // after a reset the first failed read falls back to the SEED (4), not to the old scan's 100
        val s = fillSamples(sampler, 1)
        assertEquals(4f, s[0][11], 0f)
    }

    @Test
    fun `a sampler with no channels adds nothing and records nothing`() {
        val sampler = ExtraChannelSampler(emptyList())
        val sample = FloatArray(11) { it.toFloat() }
        val before = sample.copyOf()

        sampler.fill(sample, 11, 0)

        assertEquals(0, sampler.width)
        assertArrayEquals(before, sample, 0f)
        assertEquals(ChannelReadFailures.NONE, sampler.failures())
        assertFalse(sampler.failures().indices.containsKey("anything"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a channel cannot be created with a non-finite seed`() {
        ExtraChannel("c", "u", "s", Float.NaN) { ChannelRead.Value(1f) }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a channel cannot be created with a blank name`() {
        ExtraChannel("  ", "u", "s", 1f) { ChannelRead.Value(1f) }
    }
}
