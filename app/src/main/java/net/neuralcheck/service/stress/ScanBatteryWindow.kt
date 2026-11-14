package net.neuralcheck.service.stress

/**
 * The battery window a scan may start in — fieldsVersion 71, row A-P6-13 (توحيد الظروف); contract docs/FV68_CONTRACT.md section 7-ي ف-05 (name N71-06,
 * approved 2026-10-15); `plan/PLAN-06-DATA-COLLECTION.md` §١٢ and the user's decision, verbatim: «بداية الشحن بين 30 و85% تُفرض».
 *
 * ONE source (rule 24) for the two limits: it REPLACES the lone floor `PRE_FLIGHT_MIN_BATTERY_PCT` (25, a temporary field-trial value of 2026-09-11) in its four places —
 * the service's gate, the pre-scan screen's bands, the refusal dialog's quoted number — and the old name is gone. The payload declares the window the app APPLIED
 * (`batteryWindowPct`, N71-07), so the server holds no copy of the two numbers (rule 24) and a build with other limits says so.
 *
 * What it decides: a KNOWN level below [MIN_PCT] or above [MAX_PCT] is refused (a level of 0..100); an unknown or implausible level (null, or outside 0..100) PASSES,
 * exactly as the gate always did (a device whose level cannot be read is not locked out; `batteryLevelStart` still declares the unknown — `-1` in the file, absent in the
 * payload). What the phone shows on a refusal ABOVE the window is A5h-2 (approved by delegation 2026-10-23, `R.string.scan_error_battery_window_fmt`; ف١١, P6:1054): the refusal carries the token
 * [REFUSAL_ABOVE] and its level, and the dialog fills the text with this object's two limits and the level - no number is written in the text (rule 24).
 *
 * Pure Kotlin, no Android.
 */
object ScanBatteryWindow {

    /** The lowest level a scan may start at, percent [ح: user decision 7 — 30; replaces the temporary 25]. */
    const val MIN_PCT: Int = 30

    /** The highest level a scan may start at, percent [ح: «و85%»]. */
    const val MAX_PCT: Int = 85

    /** The pre-scan indicator is yellow within this many points above [MIN_PCT] (kept from the bands the 25 % floor had: "within 10 %"). */
    const val YELLOW_BAND_POINTS: Int = 10

    /** The refusal tokens (the service's `SafetyStopped` reason is `<token>:<level>`; `BATTERY_TOO_LOW` is the existing one). */
    const val REFUSAL_BELOW: String = "BATTERY_TOO_LOW"
    const val REFUSAL_ABOVE: String = "BATTERY_TOO_HIGH"

    /** Where a level stands against the window. [UNKNOWN] passes the gate. */
    enum class Position { BELOW, WITHIN, ABOVE, UNKNOWN }

    fun positionOf(levelPct: Int?): Position = when {
        levelPct == null || levelPct !in 0..100 -> Position.UNKNOWN
        levelPct < MIN_PCT -> Position.BELOW
        levelPct > MAX_PCT -> Position.ABOVE
        else -> Position.WITHIN
    }

    /** True for a known level outside the window — the gate refuses it. */
    fun refuses(levelPct: Int?): Boolean = positionOf(levelPct).let { it == Position.BELOW || it == Position.ABOVE }

    /** The wire keys of `batteryWindowPct` (N71-07). */
    const val KEY_MIN_PCT: String = "minPct"
    const val KEY_MAX_PCT: String = "maxPct"

    /** What the footer declares the build applied: `{minPct, maxPct}`. */
    fun applied(): Map<String, Any?> = linkedMapOf(KEY_MIN_PCT to MIN_PCT, KEY_MAX_PCT to MAX_PCT)
}
