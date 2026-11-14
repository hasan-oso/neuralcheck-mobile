package net.neuralcheck.domain.manual

import net.neuralcheck.domain.model.ManualStatuses

/**
 * The raw record of ONE run of the M-09 / M-10 volume-button test (fieldsVersion 71, contract docs/FV68_CONTRACT.md section 7-ي ف-04; user
 * approvals E22 and N71-04 / N71-05 of 2026-10-15; order A-VOL of 2026-10-18; diagnosis
 * `evidence/device-check-03/app-engineer-b8-diagnosis-addenda-ce7c517-2026-10-14.md` §3).
 *
 * Pure Kotlin, no Android and no Compose: the dialog feeds it the key events its window receives (the window's own `dispatchKeyEvent`, so no
 * focus is needed) and the volume levels it read, and the tests feed it scripted events on a scripted clock. It records and COUNTS; it does not
 * judge (rule 20): the thresholds — how long a hold is a press, what a repeat means — are the server's.
 *
 * What it keeps:
 *  - every volume-key event the window got, up to [MAX_EVENTS]: `{tMs, keyCode, action, repeat, deviceId, source}` — `tMs` is the event's time
 *    (`uptimeMillis`, the clock `KeyEvent.eventTime` is on) minus the moment the dialog opened; `eventsTotal` is the count BEFORE the cap;
 *  - `expectedKeyCode` (24 up / 25 down), `expectedDownCount` and `otherKeyDownCount` (PRESSES: a down with repeat 0 — a held key's repeats are in
 *    `events[]` with `repeat > 0` and counted nowhere else), `timeToFirstExpectedDownMs` (ABSENT without a press: not 0, rule 22), `holdMs` (the
 *    first expected press to its first up; ABSENT while no up arrived), `windowMs`;
 *  - how the run ended ([EndReasons]) and, when it ended by detection, HOW it was detected ([DetectionSources]);
 *  - the media stream's max / level at the opening / level at the end — the inputs of the level-change detection (rule 20: what a decision in
 *    the app is made from is stored). ABSENT when they could not be read or the stream has no room to set a mid level.
 *
 * `[]` in `events[]` does NOT mean "not pressed": the event may never have reached the dialog (a ROM that handles the key before the app), and
 * `detectionSource` says which way detection went. `flags` (FLAG_FROM_SYSTEM, optional, VK:203) is not read.
 *
 * @param uptimeMs the clock key events are stamped on (the dialog passes `SystemClock.uptimeMillis`).
 * @param maxEvents the cap on listed events ([MAX_EVENTS] in production; the tests lower it).
 */
class VolumeKeyRecorder(
    val expectedKeyCode: Int,
    private val uptimeMs: () -> Long,
    private val maxEvents: Int = MAX_EVENTS
) {

    companion object {
        /** The test ids of the two volume rows — the one source of the literals (rule 24): the row's dialog route and `ManualChipActions.TAP_KEEPS_STORED_RAW` read them. */
        const val TEST_ID_UP = "M-09"
        const val TEST_ID_DOWN = "M-10"

        /** `KeyEvent.KEYCODE_VOLUME_UP` / `_DOWN` / `_MUTE`, spelled here so this file stays pure Kotlin. */
        const val KEYCODE_VOLUME_UP = 24
        const val KEYCODE_VOLUME_DOWN = 25
        const val KEYCODE_VOLUME_MUTE = 164

        /** The keys recorded: the volume family. Any other key (back, power…) is not a volume button and is ignored. */
        val VOLUME_KEYCODES: Set<Int> = setOf(KEYCODE_VOLUME_UP, KEYCODE_VOLUME_DOWN, KEYCODE_VOLUME_MUTE)

        /** The cap on `events[]` [ح: contract ف-04 — 64]. */
        const val MAX_EVENTS = 64

        /** The expected key of each row's test id. */
        fun expectedKeyCodeFor(testId: String): Int = when (testId) {
            TEST_ID_UP -> KEYCODE_VOLUME_UP
            TEST_ID_DOWN -> KEYCODE_VOLUME_DOWN
            else -> throw IllegalArgumentException("not a volume row: $testId")
        }

        /** The answer a run that ended for [endReason] records; a dismissal records none of its own. */
        fun statusFor(endReason: String): String? = when (endReason) {
            EndReasons.AUTO_DETECTED, EndReasons.USER_MARKED_WORKING -> ManualStatuses.PASS
            EndReasons.USER_MARKED_NOT_WORKING -> ManualStatuses.FAIL
            EndReasons.USER_MARKED_NOT_TESTED -> ManualStatuses.NOT_TESTED
            else -> null
        }
    }

    /** The values of `measurements.endReason` [ح: contract ف-04, E22 — the manual «يعمل» is `user_marked_working`, distinct from `auto_detected`] — one source (rule 24). */
    object EndReasons {
        const val AUTO_DETECTED = "auto_detected"
        const val USER_MARKED_WORKING = "user_marked_working"
        const val USER_MARKED_NOT_WORKING = "user_marked_not_working"
        const val USER_MARKED_NOT_TESTED = "user_marked_not_tested"
        const val DISMISSED = "dismissed"
        val ALL: List<String> = listOf(AUTO_DETECTED, USER_MARKED_WORKING, USER_MARKED_NOT_WORKING, USER_MARKED_NOT_TESTED, DISMISSED)
    }

    /** The values of `detectionSource` (N71-04, approved): how an `auto_detected` press was seen — one source (rule 24). */
    object DetectionSources {
        const val KEY_EVENT = "key_event"
        const val VOLUME_LEVEL_CHANGE = "volume_level_change"
        val ALL: List<String> = listOf(KEY_EVENT, VOLUME_LEVEL_CHANGE)
    }

    /** The keys of the `measurements` map [ح: contract ف-04 and N71-04 / N71-05] — one source (rule 24). */
    object Keys {
        const val EXPECTED_KEY_CODE = "expectedKeyCode"
        const val EVENTS = "events"
        const val EVENTS_TOTAL = "eventsTotal"
        const val TIME_TO_FIRST_EXPECTED_DOWN_MS = "timeToFirstExpectedDownMs"
        const val EXPECTED_DOWN_COUNT = "expectedDownCount"
        const val OTHER_KEY_DOWN_COUNT = "otherKeyDownCount"
        const val HOLD_MS = "holdMs"
        const val WINDOW_MS = "windowMs"
        const val END_REASON = "endReason"
        const val DETECTION_SOURCE = "detectionSource"
        const val VOLUME_STREAM_MAX = "volumeStreamMax"
        const val VOLUME_LEVEL_AT_OPEN = "volumeLevelAtOpen"
        const val VOLUME_LEVEL_AT_END = "volumeLevelAtEnd"
    }

    /** The keys of one `events[]` element [ح: contract ف-04]. */
    object EventKeys {
        const val T_MS = "tMs"
        const val KEY_CODE = "keyCode"
        const val ACTION = "action"
        const val REPEAT = "repeat"
        const val DEVICE_ID = "deviceId"
        const val SOURCE = "source"
    }

    /** The values of an event's `action`. */
    object Actions {
        const val DOWN = "down"
        const val UP = "up"
    }

    private class Event(val tMs: Long, val keyCode: Int, val down: Boolean, val repeat: Int, val deviceId: Int, val source: Int)

    private val openedAtUptimeMs: Long = uptimeMs()
    private val events = ArrayList<Event>()
    private var eventsTotal = 0
    private var expectedDownCount = 0
    private var otherKeyDownCount = 0
    private var firstExpectedDownAtMs: Long? = null
    private var firstExpectedUpAfterMs: Long? = null

    /** Whether a press of the EXPECTED key has been seen: the window got a down of it with repeat 0. */
    val expectedPressSeen: Boolean get() = expectedDownCount > 0

    /** How many volume-key events the window got (before the cap). */
    val eventCount: Int get() = eventsTotal

    /**
     * One key event the dialog's window received: [down] is `ACTION_DOWN`, otherwise `ACTION_UP`; [eventTimeMs] is `KeyEvent.eventTime` on the
     * `uptimeMillis` clock. A key that is not a volume key, and an action that is neither down nor up, are not recorded (return false).
     * Returns true when this event is the first press of the expected key.
     */
    fun onKeyEvent(keyCode: Int, down: Boolean?, eventTimeMs: Long, repeatCount: Int, deviceId: Int, source: Int): Boolean {
        if (keyCode !in VOLUME_KEYCODES || down == null) return false
        eventsTotal++
        if (events.size < maxEvents) events.add(Event(eventTimeMs - openedAtUptimeMs, keyCode, down, repeatCount, deviceId, source))
        var firstExpected = false
        if (down && repeatCount == 0) {
            if (keyCode == expectedKeyCode) {
                if (expectedDownCount == 0) { firstExpectedDownAtMs = eventTimeMs; firstExpected = true }
                expectedDownCount++
            } else otherKeyDownCount++
        } else if (!down && keyCode == expectedKeyCode && firstExpectedDownAtMs != null && firstExpectedUpAfterMs == null) {
            firstExpectedUpAfterMs = eventTimeMs
        }
        return firstExpected
    }

    /** The media stream's levels, as the dialog read them; a level the platform refused or could not set is null. */
    class VolumeLevels(val streamMax: Int?, val levelAtOpen: Int?, val levelAtEnd: Int?)

    /**
     * The raw for this run, as `measurements`. [detectionSource] must be given exactly when [endReason] is [EndReasons.AUTO_DETECTED] (there is
     * no default: a detection nobody named is not written). [levels] null = none could be read — the three keys are then ABSENT, as each is when
     * its own value is null.
     */
    fun measurements(endReason: String, detectionSource: String?, levels: VolumeLevels?): Map<String, Any?> {
        require(endReason in EndReasons.ALL) { "unknown endReason \"$endReason\"" }
        if (endReason == EndReasons.AUTO_DETECTED) {
            require(detectionSource in DetectionSources.ALL) { "an auto_detected run must say how: $detectionSource" }
        } else {
            require(detectionSource == null) { "detectionSource belongs to auto_detected only, not to $endReason" }
        }
        val map = LinkedHashMap<String, Any?>()
        map[Keys.EXPECTED_KEY_CODE] = expectedKeyCode
        map[Keys.EVENTS] = events.map { e ->
            linkedMapOf<String, Any?>(
                EventKeys.T_MS to e.tMs,
                EventKeys.KEY_CODE to e.keyCode,
                EventKeys.ACTION to if (e.down) Actions.DOWN else Actions.UP,
                EventKeys.REPEAT to e.repeat,
                EventKeys.DEVICE_ID to e.deviceId,
                EventKeys.SOURCE to e.source
            )
        }
        map[Keys.EVENTS_TOTAL] = eventsTotal
        val firstDown = firstExpectedDownAtMs
        if (firstDown != null) map[Keys.TIME_TO_FIRST_EXPECTED_DOWN_MS] = firstDown - openedAtUptimeMs
        map[Keys.EXPECTED_DOWN_COUNT] = expectedDownCount
        map[Keys.OTHER_KEY_DOWN_COUNT] = otherKeyDownCount
        val up = firstExpectedUpAfterMs
        if (firstDown != null && up != null) map[Keys.HOLD_MS] = up - firstDown
        map[Keys.WINDOW_MS] = uptimeMs() - openedAtUptimeMs
        map[Keys.END_REASON] = endReason
        if (detectionSource != null) map[Keys.DETECTION_SOURCE] = detectionSource
        levels?.streamMax?.let { map[Keys.VOLUME_STREAM_MAX] = it }
        levels?.levelAtOpen?.let { map[Keys.VOLUME_LEVEL_AT_OPEN] = it }
        levels?.levelAtEnd?.let { map[Keys.VOLUME_LEVEL_AT_END] = it }
        return map
    }
}
