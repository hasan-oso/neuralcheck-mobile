package net.neuralcheck.service.collector

import android.content.Intent
import android.os.BatteryManager
import net.neuralcheck.util.ReasonText

/**
 * The values of `footer.batteryHealth.cycleCountSource` (fieldsVersion 68, T-02) — one source (rule 24).
 *
 *  - [EXTRA_API34]: `BatteryManager.EXTRA_CYCLE_COUNT` of the battery broadcast, API >= 34.
 *  - `sysfs:<path>` ([sysfs]): the file at `<path>` (today only [SystemFileReader.BATTERY_CYCLE_COUNT_PATH]).
 *  - [UNAVAILABLE]: no count; `cycleCountState` says why.
 *
 * The three values the card lists. The vocabulary before fieldsVersion 68 (`sysfs_cycle_count`,
 * `battery_property_cycle_count_api_34`, `no_battery_manager`) is gone: the second named a property that
 * is not in the public API (javap on android.jar 33-36 lists BATTERY_PROPERTY_* 1 to 6 only), and
 * `no_battery_manager` is a STATE, not a source.
 */
object CycleCountSources {
    const val EXTRA_API34 = "extra_api34"
    const val SYSFS_PREFIX = "sysfs:"
    const val UNAVAILABLE = "unavailable"

    fun sysfs(path: String): String = SYSFS_PREFIX + path
}

/** A cycle count as read: [count] is `null` exactly when [state] is `not_read:<reason>`; [source] says where it came from. */
data class CycleCountReading(val count: Int?, val source: String, val state: String)

/**
 * THE reader of the battery's charge-cycle count (fieldsVersion 68, T-02). `BatteryReader.snapshotHealth`
 * (the footer's `batteryHealth.cycleCount`) and `BatteryTests` B-08 both call [read], so the footer and the
 * functional test can no longer give two verdicts on the same battery (the card: "today two readers with two
 * verdicts, they become one").
 *
 * Order: (1) `EXTRA_CYCLE_COUNT` of the battery broadcast, API >= [FIRST_API_WITH_EXTRA]; (2) the sysfs file(s)
 * in [SYSFS_PATHS]; (3) a declared absence. The first step that yields a count wins — a 0 included: it ships
 * as read and the server judges what it means (rule 20).
 *
 * The property `BatteryManager.getIntProperty(8)` this class replaces is gone: javap on `android.jar` of API
 * 34 and 35 lists BATTERY_PROPERTY_* 1..6 only (CAPACITY 4, CHARGE_COUNTER 1, CURRENT_AVERAGE 3, CURRENT_NOW 2,
 * ENERGY_COUNTER 5, STATUS 6) — a count is published as the broadcast extra, not as a property.
 *
 * Pure with respect to Android state: the broadcast and the sysfs read come in as parameters, so a JVM test
 * drives the real classification (with the platform's `Intent` mocked — the layer a JVM cannot instantiate).
 */
object BatteryCycleCount {

    /** `EXTRA_CYCLE_COUNT` exists in android.jar from API 34 (javap: absent in 33, present in 34, 35 and 36). */
    const val FIRST_API_WITH_EXTRA = 34

    /**
     * The default handed to `getIntExtra(EXTRA_CYCLE_COUNT, …)`. It is `Int.MIN_VALUE` — never a count:
     * it is the platform's own "unsupported" marker for int values (the convention of `getIntProperty`, which
     * BatteryReader already honours for CURRENT_NOW) — and it is only reachable AFTER `hasExtra` said the
     * extra is there. A present extra that equals it is read as "unsupported by the device", NEVER as a count
     * (rule 22, appendix: this default is enumerated with its reason in the contract's inventory, §١.١١).
     */
    const val EXTRA_DEFAULT: Int = Int.MIN_VALUE

    /** The sysfs files tried, in order. One today — the one B-08 and the footer both read before this change. */
    val SYSFS_PATHS: List<String> = listOf(SystemFileReader.BATTERY_CYCLE_COUNT_PATH)

    // ── the reasons (the text after `not_read:`); a final absence is "<sysfs reason>_and_<extra reason>" ──
    private const val REASON_JOIN = "_and_"

    /** The extra step did not apply: the platform predates `EXTRA_CYCLE_COUNT`. */
    const val EXTRA_REASON_API_BELOW_34 = "api_below_34"

    /** API >= 34 and there is no battery broadcast to read (the receiver registered nothing / returned `null`). */
    const val EXTRA_REASON_NO_BATTERY_BROADCAST = "no_battery_broadcast"

    /** The broadcast exists and does not carry the extra. */
    const val EXTRA_REASON_ABSENT_FROM_BROADCAST = "extra_absent_from_broadcast"

    /** The extra is there and equals [EXTRA_DEFAULT]: the platform's "unsupported" marker. */
    const val EXTRA_REASON_UNSUPPORTED_BY_DEVICE = "extra_unsupported_by_device"

    /** The extra is a negative number other than the marker — not a count; the raw value is in the text. */
    const val EXTRA_REASON_NEGATIVE_PREFIX = "extra_negative_value="

    /** Reading the extra threw; the text carries the exception's MESSAGE (rule 21). */
    const val EXTRA_REASON_READ_FAILED_PREFIX = "extra_read_failed="

    /** No file answered with a count: the sysfs reader keeps no error text (rule 21 limit), so this names no cause. */
    const val SYSFS_REASON_NOT_READABLE_OR_ABSENT = "sysfs_not_readable_or_absent"

    /** No sysfs reader was supplied to try. */
    const val SYSFS_REASON_NO_READER = "no_sysfs_reader"

    /** A file answered with a negative number — not a count; the raw value is in the text. */
    const val SYSFS_REASON_NEGATIVE_PREFIX = "sysfs_negative_value="

    /** A declared absence: no count, source [CycleCountSources.UNAVAILABLE], and the [reason] as `not_read:<reason>`. */
    fun absent(reason: String): CycleCountReading =
        CycleCountReading(null, CycleCountSources.UNAVAILABLE, BatteryReadStates.notRead(reason))

    /**
     * @param intent the latest `ACTION_BATTERY_CHANGED` broadcast (sticky), or `null` when there is none.
     * @param sdkInt the platform API level (a parameter so the API-34 path is testable on the JVM).
     * @param readSysfs reads one sysfs file as an integer, `null` for any failure; `null` = no reader supplied.
     */
    fun read(intent: Intent?, sdkInt: Int, readSysfs: ((path: String) -> Int?)?): CycleCountReading {
        // Step 1 — the broadcast extra.
        val extraReason: String = when {
            sdkInt < FIRST_API_WITH_EXTRA -> EXTRA_REASON_API_BELOW_34
            intent == null -> EXTRA_REASON_NO_BATTERY_BROADCAST
            else -> try {
                if (!intent.hasExtra(BatteryManager.EXTRA_CYCLE_COUNT)) {
                    EXTRA_REASON_ABSENT_FROM_BROADCAST
                } else {
                    val value = intent.getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, EXTRA_DEFAULT)
                    when {
                        value == EXTRA_DEFAULT -> EXTRA_REASON_UNSUPPORTED_BY_DEVICE
                        value < 0 -> EXTRA_REASON_NEGATIVE_PREFIX + value
                        else -> return CycleCountReading(value, CycleCountSources.EXTRA_API34, BatteryReadStates.READ)
                    }
                }
            } catch (t: Exception) {
                EXTRA_REASON_READ_FAILED_PREFIX + ReasonText.of(t)
            }
        }

        // Step 2 — sysfs.
        var sysfsReason = if (readSysfs == null) SYSFS_REASON_NO_READER else SYSFS_REASON_NOT_READABLE_OR_ABSENT
        if (readSysfs != null) {
            for (path in SYSFS_PATHS) {
                val value = readSysfs(path) ?: continue
                if (value >= 0) return CycleCountReading(value, CycleCountSources.sysfs(path), BatteryReadStates.READ)
                sysfsReason = SYSFS_REASON_NEGATIVE_PREFIX + value
            }
        }

        // Step 3 — a declared absence that names what each step found.
        return absent(sysfsReason + REASON_JOIN + extraReason)
    }
}
