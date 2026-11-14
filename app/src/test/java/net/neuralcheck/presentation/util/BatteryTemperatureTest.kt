package net.neuralcheck.presentation.util

import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import androidx.test.core.app.ApplicationProvider
import net.neuralcheck.domain.repeat.TempRead
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The phone's own battery temperature for the cooling screen - A-P6-07 (PLAN-06:634). The unit is the broadcast's: tenths of a degree. What is asserted, with the real sticky broadcast (Robolectric delivers
 * it to the real `registerReceiver(null, filter)` the reader calls): a reading is tenths; a missing intent, a missing extra and a 0 are each their own named failure (rule 22) - never a temperature, as the scan's
 * pre-flight's default (`getIntExtra(.., 0) / 10f`, 25 degrees without an intent) would make them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BatteryTemperatureTest {

    private fun intent(temperature: Int?) = Intent(Intent.ACTION_BATTERY_CHANGED).also { if (temperature != null) it.putExtra(BatteryManager.EXTRA_TEMPERATURE, temperature) }

    @Test
    fun `an intent is read in tenths - and a missing intent, a missing extra and a zero are three named failures`() {
        assertEquals(TempRead.Present(331), BatteryTemperature.of(intent(331)))
        assertEquals("below zero is a reading", TempRead.Present(-50), BatteryTemperature.of(intent(-50)))
        assertEquals(TempRead.Unreadable("no_battery_intent"), BatteryTemperature.of(null))
        assertEquals(TempRead.Unreadable("no_temperature_extra"), BatteryTemperature.of(intent(null)))
        assertEquals("0 is the platform's value for a missing extra and the server's sentinel: not a temperature", TempRead.Unreadable("zero_is_the_missing_value"), BatteryTemperature.of(intent(0)))
    }

    @Test
    fun `the real sticky broadcast is what read gives - no receiver is left registered`() {
        val context: Context = ApplicationProvider.getApplicationContext()
        @Suppress("DEPRECATION")
        context.sendStickyBroadcast(intent(335))
        assertEquals(TempRead.Present(335), BatteryTemperature.read(context))
        @Suppress("DEPRECATION")
        context.sendStickyBroadcast(intent(342))
        assertEquals("the next broadcast is the next reading", TempRead.Present(342), BatteryTemperature.read(context))
    }

    @Test
    fun `a context that refuses the registration is a named failure - and no exception text is in the token`() {
        val refusing: Context = mock { on { registerReceiver(anyOrNull(), any()) } doThrow SecurityException("secret text") }
        assertEquals(TempRead.Unreadable("receiver_failed"), BatteryTemperature.read(refusing))
    }
}
