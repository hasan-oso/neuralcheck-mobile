package net.neuralcheck.files

import net.neuralcheck.data.local.files.ChannelNames
import net.neuralcheck.data.local.files.ExtraChannel
import net.neuralcheck.data.local.files.ScanChannelLayout
import net.neuralcheck.data.local.files.V2_SAMPLE_VAR_COUNT
import net.neuralcheck.data.local.files.V2_VARIABLE_DEFINITIONS
import net.neuralcheck.util.ChannelRead
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/** fieldsVersion 68 (S-01, N-22): the per-scan column layout = frozen base + admitted channels. */
class ScanChannelLayoutTest {

    private fun channel(name: String, unit: String = "u") =
        ExtraChannel(name, unit, "source of $name", 1f) { ChannelRead.Value(1f) }

    @Test
    fun `base-only layout is exactly the 11 base definitions`() {
        val layout = ScanChannelLayout.BASE_ONLY
        assertEquals(V2_VARIABLE_DEFINITIONS, layout.variables)
        assertEquals(11, layout.sampleWidth)
        assertEquals(V2_SAMPLE_VAR_COUNT, layout.sampleWidth)
    }

    @Test
    fun `extras are appended after v10 in the given order with consecutive indices`() {
        val layout = ScanChannelLayout(
            listOf(
                channel(ChannelNames.CHARGE_COUNTER_RAW, "as_declared_by_device"),
                channel(ChannelNames.cpuFreqPolicy(0), "kHz"),
                channel(ChannelNames.cpuFreqPolicy(4), "kHz"),
                channel(ChannelNames.cpuFreqPolicy(7), "kHz")
            )
        )

        assertEquals(15, layout.sampleWidth)
        assertEquals(15, layout.variables.size)
        // the base block is untouched, same objects in the same positions
        for (i in 0 until V2_SAMPLE_VAR_COUNT) assertSame(V2_VARIABLE_DEFINITIONS[i], layout.variables[i])
        assertEquals(
            listOf(
                11 to "charge_counter_raw",
                12 to "cpu_freq_policy0_khz",
                13 to "cpu_freq_policy4_khz",
                14 to "cpu_freq_policy7_khz"
            ),
            layout.variables.drop(V2_SAMPLE_VAR_COUNT).map { it.index to it.name }
        )
        assertEquals("as_declared_by_device", layout.variables[11].unit)
        assertEquals("source of cpu_freq_policy4_khz", layout.variables[13].source)
    }

    @Test
    fun `index equals position for every column`() {
        val layout = ScanChannelLayout(listOf(channel("x1"), channel("x2")))
        layout.variables.forEachIndexed { position, def -> assertEquals(position, def.index) }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `an extra channel cannot reuse a base column name`() {
        ScanChannelLayout(listOf(channel(ChannelNames.GPU_FPS)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `the same extra channel cannot appear twice`() {
        ScanChannelLayout(listOf(channel("cpu_freq_policy0_khz"), channel("cpu_freq_policy0_khz")))
    }
}
