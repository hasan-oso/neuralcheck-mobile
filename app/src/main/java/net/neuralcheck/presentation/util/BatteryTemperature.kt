package net.neuralcheck.presentation.util

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import net.neuralcheck.domain.repeat.TempRead

/**
 * The phone's own battery temperature, from the sticky `ACTION_BATTERY_CHANGED` broadcast, for the repeat scan's cooling screen (A-P6-07; PLAN-06:634: «التطبيق يقرأ حرارة البطارية من البثّ كما في
 * `StressTestService.kt:595-597`»). The unit is the broadcast's: `EXTRA_TEMPERATURE` in TENTHS of a degree Celsius, an integer.
 *
 * What it does NOT do, on purpose (rule 22): the scan's pre-flight reads the extra with a default (`getIntExtra(EXTRA_TEMPERATURE, 0)`, then `/ 10f`, with 25 °C when there is no intent at all) - a default that fills the
 * gap with a temperature. Here a missing intent and a missing extra are [TempRead.Unreadable] with their own token, and a reading of exactly 0 is Unreadable too: the platform's default for a missing extra is 0, and the server's
 * contract treats `0.0` in the same place as the «no broadcast» sentinel (`docs/FV68_CONTRACT.md` 1.6, A4 (1) `SENTINEL_ZERO`), so a 0 cannot be told from a gap and is never a temperature. No exception text enters a token.
 */
object BatteryTemperature {
    const val UNREADABLE_NO_BATTERY_INTENT = "no_battery_intent"
    const val UNREADABLE_NO_TEMPERATURE_EXTRA = "no_temperature_extra"
    const val UNREADABLE_ZERO = "zero_is_the_missing_value"
    const val UNREADABLE_RECEIVER_FAILED = "receiver_failed"

    /** What one battery intent says. */
    fun of(intent: Intent?): TempRead {
        if (intent == null) return TempRead.Unreadable(UNREADABLE_NO_BATTERY_INTENT)
        if (!intent.hasExtra(BatteryManager.EXTRA_TEMPERATURE)) return TempRead.Unreadable(UNREADABLE_NO_TEMPERATURE_EXTRA)
        val tenths = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
        return if (tenths == 0) TempRead.Unreadable(UNREADABLE_ZERO) else TempRead.Present(tenths)
    }

    /** The sticky broadcast now (no receiver is left registered). */
    fun read(context: Context): TempRead {
        val intent = try {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        } catch (e: RuntimeException) {
            return TempRead.Unreadable(UNREADABLE_RECEIVER_FAILED)
        }
        return of(intent)
    }
}
