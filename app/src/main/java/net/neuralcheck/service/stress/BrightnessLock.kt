package net.neuralcheck.service.stress

import net.neuralcheck.util.ReasonText
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The screen's brightness held constant while the scan stresses the device — fieldsVersion 71, row A-P6-13 (توحيد الظروف); contract docs/FV68_CONTRACT.md section 7-ي ف-05
 * (N71-08, approved 2026-10-15): `brightnessLock: {state, value, method}` in the footer; `plan/PLAN-06-DATA-COLLECTION.md` §١٢-ب: «يثبّته التطبيق بنافذة شاشة الفحص طوال
 * الإجهاد، والقيمة والطريقة والنجاح في التذييل»; the user's decision: «وسطوع ثابت أثناء الإجهاد».
 *
 * `state` ∈ `applied` · `apply_failed:<the error's message text>` (rule 21) · `not_attempted:<reason>`; `value` is the brightness REQUESTED, a fraction in [0, 1]; `method` is
 * `window_attribute` (the scan screen's window attribute, the plan's choice) or `system_setting`. `value` and `method` are ABSENT when nothing was attempted (never 0).
 *
 * PENDING — «معلّق: مواصفة غائبة»: the VALUE to hold is nowhere in the sources (PLAN-06:1416 says «سطوع ثابت» with no figure; the contract names no number). So [VALUE] is null and
 * the scan screen asks the platform for NOTHING: the footer says `not_attempted:value_not_decided`. The whole path — the window call, the report, the holder, the footer — is built
 * and tested; deciding the number is one constant (and a field-round check that a window attribute holds on One UI).
 *
 * Pure Kotlin, no Android: [lock] takes the window call as a lambda.
 */
object BrightnessLock {

    /** The brightness to hold, 0..1. NULL = not decided (see the class comment): nothing is applied. */
    val VALUE: Float? = null

    /** The values of `brightnessLock.state` [ح: N71-08]. */
    object States {
        const val APPLIED: String = "applied"
        const val APPLY_FAILED_PREFIX: String = "apply_failed:"
        const val NOT_ATTEMPTED_PREFIX: String = "not_attempted:"
        fun applyFailed(t: Throwable): String = APPLY_FAILED_PREFIX + ReasonText.of(t)
        fun notAttempted(reason: String): String = NOT_ATTEMPTED_PREFIX + reason
    }

    /** The reasons `not_attempted:<reason>` carries [ق: the contract leaves them free]. */
    object NotAttemptedReasons {
        /** No brightness was decided for this build ([VALUE] is null). */
        const val VALUE_NOT_DECIDED: String = "value_not_decided"

        /** The scan ran with no scan screen to apply it (a background or data-collection path) — nothing reported. */
        const val NO_SCAN_SCREEN_REPORTED: String = "no_scan_screen_reported"
    }

    /** The values of `brightnessLock.method` [ح: N71-08]. */
    object Methods {
        const val WINDOW_ATTRIBUTE: String = "window_attribute"
        const val SYSTEM_SETTING: String = "system_setting"
        val ALL: List<String> = listOf(WINDOW_ATTRIBUTE, SYSTEM_SETTING)
    }

    /** The wire keys of `brightnessLock`. */
    object Keys {
        const val STATE: String = "state"
        const val VALUE: String = "value"
        const val METHOD: String = "method"
    }

    /** What happened: the state, and — when something was attempted — the value requested and the method used. */
    class Result(val state: String, val value: Float?, val method: String?) {
        fun toWire(): Map<String, Any?> {
            val m = LinkedHashMap<String, Any?>()
            m[Keys.STATE] = state
            if (value != null) m[Keys.VALUE] = value
            if (method != null) m[Keys.METHOD] = method
            return m
        }
    }

    /**
     * Holds [value] through [setWindowBrightness] (the scan screen's window attribute) and says what came of it. A null [value] attempts nothing:
     * `not_attempted:value_not_decided`. A value outside [0, 1] is a programming error and is refused loudly. A throw from the window call is `apply_failed:<message>` with the
     * value and the method that were tried.
     */
    fun lock(value: Float?, setWindowBrightness: (Float) -> Unit): Result {
        if (value == null) return Result(States.notAttempted(NotAttemptedReasons.VALUE_NOT_DECIDED), null, null)
        require(value in 0f..1f) { "a brightness is a fraction in [0, 1]: $value" }
        return try {
            setWindowBrightness(value)
            Result(States.APPLIED, value, Methods.WINDOW_ATTRIBUTE)
        } catch (e: Exception) {
            Result(States.applyFailed(e), value, Methods.WINDOW_ATTRIBUTE)
        }
    }

    /** The result when no scan screen reported one: the scan did not go through a screen that could apply it. */
    fun notReported(): Result = Result(States.notAttempted(NotAttemptedReasons.NO_SCAN_SCREEN_REPORTED), null, null)
}

/**
 * Hilt-singleton bridge between the scan screen (which holds the brightness) and [StressTestService] (which writes the footer): the screen reports what came of the lock
 * once, the service drains it when it builds the footer and forgets it, so the next scan does not inherit this one's. The same pattern as `PreScanWarningsHolder`.
 */
@Singleton
class BrightnessLockHolder @Inject constructor() {

    @Volatile
    private var result: BrightnessLock.Result? = null

    fun report(r: BrightnessLock.Result) {
        result = r
    }

    /** Drains the holder once: what the screen reported, or [BrightnessLock.notReported] when it reported nothing. */
    fun readAndClear(): BrightnessLock.Result {
        val r = result ?: BrightnessLock.notReported()
        result = null
        return r
    }
}
