package net.neuralcheck.data.local.files

/**
 * fieldsVersion 68 (ت-06, N-05, S-10, S-28 as corrected 2026-10-08, order of
 * batch 3 part 2-a): the battery readings taken AT each `ACTION_BATTERY_CHANGED`
 * broadcast — the moment the platform itself says the battery state changed —
 * kept as a list of RAW pairs in the footer.
 *
 * RAW means as the platform produced it, with nothing the app decided:
 *  - [voltageRaw] is `EXTRA_VOLTAGE` as it arrived — no ÷1000, no µV threshold
 *    (the sanitiser that guesses µV from magnitude is NOT applied: rule 20, the
 *    server infers the unit; S-28 corrected: «voltageRaw»);
 *  - [currentRaw] is `BATTERY_PROPERTY_CURRENT_NOW` as returned — no scale
 *    multiplier, no sign flip (S-24: the raw lives here, `v[3]` stays as it is);
 *  - [chargeCounterRaw] is `BATTERY_PROPERTY_CHARGE_COUNTER` as returned, in
 *    the device's own unit;
 *  - [levelRaw] and [levelScale] are `EXTRA_LEVEL` and `EXTRA_SCALE` — the
 *    pair, not the truncating integer percentage the app computes elsewhere
 *    (S-28 corrected: «levelRaw with levelScale»);
 *  - [tempRaw] is `EXTRA_TEMPERATURE`, tenths of °C as the platform states it.
 *
 * A field is `null` — and its key is ABSENT on the wire (the payload builder
 * has no serializeNulls) — exactly when that read was not a value: the
 * platform's failure marker (`Int.MIN_VALUE` / `Long.MIN_VALUE`), an exception,
 * or an intent without that extra. A failure marker is never written as a
 * number (it would be 2.1e9 µA on the wire); each such field is counted in
 * [BatteryPairsState.invalidReads].
 *
 * [t] is milliseconds since the scan started, the frame of `v[0]` (S-10).
 */
data class BatteryPair(
    val t: Long,
    val voltageRaw: Int?,
    val currentRaw: Int?,
    val chargeCounterRaw: Long?,
    val levelRaw: Int?,
    val levelScale: Int?,
    val tempRaw: Int?
)

/**
 * The explicit state that goes with `batteryPairs[]` (N-05): an empty list alone
 * cannot tell "the receiver was never registered" from "registered, and no
 * broadcast came" from "n broadcasts came and none were kept".
 *
 * Invariant: `broadcastCount == batteryPairs.size + droppedByCap`.
 *
 * @property receiverRegistered whether the battery receiver was registered
 *   when the scan's recording ended. `false` = the receiver never delivered
 *   anything and `broadcastCount` is 0 for that reason, not because the
 *   battery was quiet.
 * @property broadcastCount broadcasts the receiver delivered while recording.
 * @property droppedByCap broadcasts past the cap of 200 pairs — the list keeps
 *   the FIRST 200, not a selection (S-10; rule 20: no peak-picking).
 * @property invalidReads fields (of the six read per broadcast) that were not a
 *   value, counted over every broadcast including the dropped ones.
 */
data class BatteryPairsState(
    val receiverRegistered: Boolean,
    val broadcastCount: Int,
    val droppedByCap: Int,
    val invalidReads: Int
)

/** What the battery receiver recorded over a scan. */
data class BatteryPairsRecord(
    val pairs: List<BatteryPair>,
    val state: BatteryPairsState
)
