package net.neuralcheck.service.collector

import android.os.PowerManager
import net.neuralcheck.data.local.files.ThermalListStates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

/**
 * fieldsVersion 68 (ت-03): the real [PowerManagerThermalPlatform] — the layer
 * the collector's tests substitute — against a mocked `PowerManager`, so the
 * mapping platform-answer → (headroom, state) is itself tested (rule 37).
 */
class PowerManagerThermalPlatformTest {

    @Test
    fun `a finite return is ok and keeps the value as returned`() {
        val pm = mock<PowerManager> { on { getThermalHeadroom(0) } doReturn 1.07f }
        val read = PowerManagerThermalPlatform(pm, sdkInt = 35).readHeadroom()
        assertEquals(1.07f, read.headroom!!, 0f)
        assertEquals(ThermalListStates.HEADROOM_OK, read.state)
    }

    @Test
    fun `a zero return is a real zero - not an absent value`() {
        val pm = mock<PowerManager> { on { getThermalHeadroom(0) } doReturn 0f }
        val read = PowerManagerThermalPlatform(pm, sdkInt = 35).readHeadroom()
        assertEquals(0f, read.headroom!!, 0f)
        assertEquals(ThermalListStates.HEADROOM_OK, read.state)
    }

    @Test
    fun `NaN is no number and the state nan`() {
        val pm = mock<PowerManager> { on { getThermalHeadroom(0) } doReturn Float.NaN }
        val read = PowerManagerThermalPlatform(pm, sdkInt = 35).readHeadroom()
        assertNull(read.headroom)
        assertEquals(ThermalListStates.HEADROOM_NAN, read.state)
    }

    @Test
    fun `infinity is declared, never clamped and never written as a number`() {
        val pm = mock<PowerManager> { on { getThermalHeadroom(0) } doReturn Float.POSITIVE_INFINITY }
        val read = PowerManagerThermalPlatform(pm, sdkInt = 35).readHeadroom()
        assertNull(read.headroom)
        assertEquals("non_finite:Infinity", read.state)
    }

    @Test
    fun `an exception is its message text and the class name is not part of it`() {
        val pm = mock<PowerManager> {
            on { getThermalHeadroom(0) } doThrow UnsupportedOperationException("not supported by this HAL")
        }
        val read = PowerManagerThermalPlatform(pm, sdkInt = 35).readHeadroom()
        assertNull(read.headroom)
        assertEquals("exception:not supported by this HAL", read.state)
        assertFalse(read.state.contains("UnsupportedOperationException"))
    }

    @Test
    fun `an exception without a message gets the explicit no-message text`() {
        val pm = mock<PowerManager> { on { getThermalHeadroom(0) } doThrow IllegalStateException() }
        val read = PowerManagerThermalPlatform(pm, sdkInt = 35).readHeadroom()
        assertEquals("exception:exception without message", read.state)
    }

    @Test
    fun `below API 30 the platform is not called at all`() {
        val pm = mock<PowerManager>()
        val read = PowerManagerThermalPlatform(pm, sdkInt = 29).readHeadroom()
        assertNull(read.headroom)
        assertEquals(ThermalListStates.API_BELOW_30, read.state)
        verify(pm, never()).getThermalHeadroom(any())
    }

    @Test
    fun `a missing PowerManager is a stated exception, not a silent absence`() {
        val read = PowerManagerThermalPlatform(null, sdkInt = 35).readHeadroom()
        assertNull(read.headroom)
        assertTrue(read.state.startsWith("exception:PowerManager unavailable"))
    }

    @Test
    fun `no call is cached - two calls reach the platform twice`() {
        val pm = mock<PowerManager> { on { getThermalHeadroom(0) } doReturn 0.3f }
        val platform = PowerManagerThermalPlatform(pm, sdkInt = 35)
        platform.readHeadroom()
        platform.readHeadroom()
        verify(pm, org.mockito.kotlin.times(2)).getThermalHeadroom(0)
    }

    @Test
    fun `current status is the raw platform integer`() {
        val pm = mock<PowerManager> { on { currentThermalStatus } doReturn 3 }
        assertEquals(3, PowerManagerThermalPlatform(pm, sdkInt = 35).currentStatus())
    }
}
