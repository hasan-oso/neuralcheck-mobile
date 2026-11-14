package net.neuralcheck.service.collector

import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

/**
 * fieldsVersion 68, batch 3 part 2-b (T-18 row 10): the cycle count, the charge
 * counter and the energy counter ship AS READ — a 0 included — with a declared
 * state; the platform's "not supported" sentinel and a failed call are states,
 * not values. Before, a 0 and a missing reading were the same absent value
 * (`> 0` / `!= 0L` dropped them), so the server could not tell "the device says
 * 0" from "the device says nothing".
 *
 * Runs the real [BatteryReader.snapshotHealth] against a mocked BatteryManager
 * and SystemFileReader. The API-34 branch is reached through the `sdkInt`
 * parameter, and the battery broadcast through `batteryIntent` (a mocked
 * `Intent` — a JVM cannot build a real one); what a real device returns is the
 * device round's to say.
 *
 * T-02 (rule 23): the cycle-count tests that pinned the old behaviour — sysfs
 * first, `getIntProperty(8)` on API 34, the vocabulary `sysfs_cycle_count` /
 * `battery_property_cycle_count_api_34` — are REPLACED by the ones below; the
 * reader's own classification is in [BatteryCycleCountTest].
 */
class BatteryReaderHealthTest {

    private fun reader(
        charge: () -> Long = { 2_000_000L },
        energy: () -> Long = { 8_000_000_000L }
    ): BatteryReader {
        val bm: BatteryManager = mock {
            on { getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER) } doAnswerOf charge
            on { getLongProperty(BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER) } doAnswerOf energy
        }
        val context: Context = mock { on { getSystemService(Context.BATTERY_SERVICE) } doReturn bm }
        return BatteryReader(context)
    }

    private infix fun <T> org.mockito.stubbing.OngoingStubbing<T>.doAnswerOf(f: () -> T) = thenAnswer { f() }

    private fun sysfs(cycle: Int?): SystemFileReader = mock {
        on { readBatteryCycleCountAt(any()) } doReturn cycle
    }

    /** A battery broadcast carrying `EXTRA_CYCLE_COUNT` = [count], or none when null. */
    private fun broadcast(count: Int?): Intent = mock {
        on { hasExtra(BatteryManager.EXTRA_CYCLE_COUNT) } doReturn (count != null)
        if (count != null) on { getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, BatteryCycleCount.EXTRA_DEFAULT) } doReturn count
    }

    // ───────────────────────── charge and energy counters ─────────────────────────

    @Test
    fun `a zero counter is a reading - shipped as 0 with state read`() {
        val h = reader(charge = { 0L }, energy = { 0L }).snapshotHealth(sysfs(null), sdkInt = 33)
        assertEquals("the old code dropped it to null", 0L, h.chargeCounterUah)
        assertEquals("read", h.chargeCounterUahState)
        assertEquals(0L, h.energyCounterNwh)
        assertEquals("read", h.energyCounterNwhState)
    }

    @Test
    fun `a real counter is shipped as read`() {
        val h = reader(charge = { 1_711_600L }, energy = { 5_000_000L }).snapshotHealth(sysfs(null), sdkInt = 33)
        assertEquals(1_711_600L, h.chargeCounterUah)
        assertEquals("read", h.chargeCounterUahState)
        assertEquals(5_000_000L, h.energyCounterNwh)
    }

    @Test
    fun `the platform's not-supported sentinel is a state - never a value`() {
        val h = reader(charge = { Long.MIN_VALUE }, energy = { Long.MIN_VALUE }).snapshotHealth(sysfs(null), sdkInt = 33)
        assertNull(h.chargeCounterUah)
        assertEquals("not_read:unsupported_by_device", h.chargeCounterUahState)
        assertNull(h.energyCounterNwh)
        assertEquals("not_read:unsupported_by_device", h.energyCounterNwhState)
    }

    @Test
    fun `a failed call is its message in the state - not a missing value`() {
        val h = reader(
            charge = { throw SecurityException("no permission for charge counter") },
            energy = { 7L }
        ).snapshotHealth(sysfs(null), sdkInt = 33)
        assertNull(h.chargeCounterUah)
        assertEquals("not_read:call_failed:no permission for charge counter", h.chargeCounterUahState)
        assertEquals("one failing channel does not take the other down", 7L, h.energyCounterNwh)
    }

    // ───────────────────────── cycle count (T-02: one reader, extra first) ─────────────────────────

    @Test
    fun `a zero cycle count from sysfs is a reading`() {
        val h = reader().snapshotHealth(sysfs(0), sdkInt = 33, batteryIntent = null)
        assertEquals("the old code dropped a 0 (> 0)", 0, h.cycleCount)
        assertEquals("sysfs:/sys/class/power_supply/battery/cycle_count", h.cycleCountSource)
        assertEquals("read", h.cycleCountState)
    }

    @Test
    fun `a sysfs cycle count is shipped as read`() {
        val h = reader().snapshotHealth(sysfs(247), sdkInt = 33, batteryIntent = null)
        assertEquals(247, h.cycleCount)
        assertEquals("read", h.cycleCountState)
    }

    @Test
    fun `on API 34 the count comes from the battery broadcast - a zero is a reading too`() {
        val h = reader().snapshotHealth(sysfs(null), sdkInt = 34, batteryIntent = broadcast(0))
        assertEquals(0, h.cycleCount)
        assertEquals("extra_api34", h.cycleCountSource)
        assertEquals("read", h.cycleCountState)
    }

    @Test
    fun `on API 34 the platform's unsupported marker in the broadcast is a state`() {
        val h = reader().snapshotHealth(sysfs(null), sdkInt = 34, batteryIntent = broadcast(Int.MIN_VALUE))
        assertNull(h.cycleCount)
        assertEquals("unavailable", h.cycleCountSource)
        assertEquals("not_read:sysfs_not_readable_or_absent_and_extra_unsupported_by_device", h.cycleCountState)
    }

    @Test
    fun `on API 34 a broadcast without the extra is its own state`() {
        val h = reader().snapshotHealth(sysfs(null), sdkInt = 34, batteryIntent = broadcast(null))
        assertNull(h.cycleCount)
        assertEquals("not_read:sysfs_not_readable_or_absent_and_extra_absent_from_broadcast", h.cycleCountState)
    }

    @Test
    fun `below API 34 with no sysfs reading the state names both - the sysfs reader keeps no error text`() {
        val h = reader().snapshotHealth(sysfs(null), sdkInt = 33, batteryIntent = broadcast(187))
        assertNull(h.cycleCount)
        assertEquals("not_read:sysfs_not_readable_or_absent_and_api_below_34", h.cycleCountState)
        val none = reader().snapshotHealth(null, sdkInt = 33, batteryIntent = null)
        assertEquals("not_read:no_sysfs_reader_and_api_below_34", none.cycleCountState)
    }

    @Test
    fun `the broadcast wins over sysfs on API 34 - including a 0 in the broadcast`() {
        val h = reader().snapshotHealth(sysfs(999), sdkInt = 34, batteryIntent = broadcast(0))
        assertEquals(0, h.cycleCount)
        assertEquals("extra_api34", h.cycleCountSource)
    }

    @Test
    fun `no battery manager is the state, and the source stays inside the card's vocabulary`() {
        val context: Context = mock { on { getSystemService(Context.BATTERY_SERVICE) } doReturn null }
        val h = BatteryReader(context).snapshotHealth(sysfs(247), sdkInt = 34, batteryIntent = broadcast(187))
        assertNull(h.cycleCount)
        assertEquals("unavailable", h.cycleCountSource)
        assertEquals("not_read:no_battery_manager", h.cycleCountState)
    }
}
