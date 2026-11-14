package net.neuralcheck.service.stress

/**
 * What the app learned about the power-saving mode at scan start — fieldsVersion 71, row A-P6-13; contract docs/FV68_CONTRACT.md section 7-ي ف-05:
 * `powerSaveState` ∈ {`off`, `on`, `read_failed`} (PLAN-06 P:1433), written beside the two booleans a pre-71 reader still reads
 * (`isPowerSaveMode` in the file header, `powerSaveMode` in the payload root): `on` ⇒ true, `off` ⇒ false, `read_failed` ⇒ the boolean is ABSENT (not `false`).
 *
 * Before: a failed read (or no power service at all) was written as `false` — an absence read as "not saving power" (rule 22).
 *
 * `on` still stops the scan (`POWER_SAVE_MODE`); `read_failed` does NOT: whether a failed read should refuse the start is «[اجتهاد]» in the plan (P:1433) with no decision
 * and no approved text, so this build starts the scan and declares the state (PENDING: «معلّق: مواصفة غائبة»).
 *
 * Pure Kotlin, no Android: [read] takes the platform call as a lambda.
 */
class PowerSaveReading private constructor(val state: String, val failure: String?) {

    companion object {
        const val OFF = "off"
        const val ON = "on"
        const val READ_FAILED = "read_failed"
        val ALL: List<String> = listOf(OFF, ON, READ_FAILED)

        /**
         * [platform] returns `PowerManager.isPowerSaveMode`, or null when there is no power service to ask; it may throw. Both a null and a throw are
         * `read_failed` (the message text of a throw is kept in [failure] for the log — rule 21 — and not on the wire: the contract's values are the three above).
         */
        fun read(platform: () -> Boolean?): PowerSaveReading = try {
            when (platform()) {
                true -> PowerSaveReading(ON, null)
                false -> PowerSaveReading(OFF, null)
                null -> PowerSaveReading(READ_FAILED, "no power service to ask")
            }
        } catch (e: Exception) {
            PowerSaveReading(READ_FAILED, e.message?.takeIf { it.isNotBlank() } ?: "exception without message")
        }
    }

    /** The boolean a pre-71 reader reads: true / false, or null (ABSENT) when the state could not be read. */
    val isPowerSaveMode: Boolean? get() = when (state) {
        ON -> true
        OFF -> false
        else -> null
    }

    /** Whether the scan must stop: the mode is on. */
    val stopsScan: Boolean get() = state == ON
}
