package net.neuralcheck.domain.manual

import net.neuralcheck.data.local.db.converter.MeasurementsJson
import net.neuralcheck.domain.manual.VolumeKeyRecorder.DetectionSources
import net.neuralcheck.domain.manual.VolumeKeyRecorder.EndReasons
import net.neuralcheck.domain.manual.VolumeKeyRecorder.EventKeys
import net.neuralcheck.domain.manual.VolumeKeyRecorder.Keys
import net.neuralcheck.domain.model.ManualStatuses
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * fieldsVersion 71 (contract docs/FV68_CONTRACT.md 7-ي ف-04; user approvals E22 and N71-04 / N71-05 of 2026-10-15; order A-VOL of 2026-10-18): the raw of
 * one M-09 / M-10 run, on a scripted clock and scripted key events — the REAL recorder; only the clock (`uptimeMillis`) and the events are scripted.
 * The dialog's window tap and its level polling (Android) are not run by a JVM test: they are field-round items.
 */
class VolumeKeyRecorderTest {

    private var uptime = 100_000L

    private fun recorder(expected: Int = VolumeKeyRecorder.KEYCODE_VOLUME_UP, maxEvents: Int = VolumeKeyRecorder.MAX_EVENTS): VolumeKeyRecorder {
        uptime = 100_000L
        return VolumeKeyRecorder(expected, { uptime }, maxEvents)
    }

    private val up = VolumeKeyRecorder.KEYCODE_VOLUME_UP
    private val down = VolumeKeyRecorder.KEYCODE_VOLUME_DOWN

    private fun VolumeKeyRecorder.press(keyCode: Int, atMs: Long, repeat: Int = 0) =
        onKeyEvent(keyCode, true, 100_000L + atMs, repeat, deviceId = 7, source = 0x101)

    private fun VolumeKeyRecorder.release(keyCode: Int, atMs: Long) =
        onKeyEvent(keyCode, false, 100_000L + atMs, 0, deviceId = 7, source = 0x101)

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.events(): List<Map<String, Any?>> = this[Keys.EVENTS] as List<Map<String, Any?>>

    // ───────────────────────── the contract's names ─────────────────────────

    @Test
    fun `the wire names and values are the contract's - one source, spelled out here so a rename fails`() {
        assertEquals("M-09", VolumeKeyRecorder.TEST_ID_UP)
        assertEquals("M-10", VolumeKeyRecorder.TEST_ID_DOWN)
        assertEquals(24, VolumeKeyRecorder.KEYCODE_VOLUME_UP)
        assertEquals(25, VolumeKeyRecorder.KEYCODE_VOLUME_DOWN)
        assertEquals(64, VolumeKeyRecorder.MAX_EVENTS)
        assertEquals(24, VolumeKeyRecorder.expectedKeyCodeFor("M-09"))
        assertEquals(25, VolumeKeyRecorder.expectedKeyCodeFor("M-10"))
        assertThrows(IllegalArgumentException::class.java) { VolumeKeyRecorder.expectedKeyCodeFor("M-08") }
        assertEquals(
            listOf(
                "expectedKeyCode", "events", "eventsTotal", "timeToFirstExpectedDownMs", "expectedDownCount", "otherKeyDownCount", "holdMs", "windowMs",
                "endReason", "detectionSource", "volumeStreamMax", "volumeLevelAtOpen", "volumeLevelAtEnd"
            ),
            with(Keys) {
                listOf(
                    EXPECTED_KEY_CODE, EVENTS, EVENTS_TOTAL, TIME_TO_FIRST_EXPECTED_DOWN_MS, EXPECTED_DOWN_COUNT, OTHER_KEY_DOWN_COUNT, HOLD_MS, WINDOW_MS,
                    END_REASON, DETECTION_SOURCE, VOLUME_STREAM_MAX, VOLUME_LEVEL_AT_OPEN, VOLUME_LEVEL_AT_END
                )
            }
        )
        assertEquals(listOf("tMs", "keyCode", "action", "repeat", "deviceId", "source"), with(EventKeys) { listOf(T_MS, KEY_CODE, ACTION, REPEAT, DEVICE_ID, SOURCE) })
        assertEquals(listOf("down", "up"), listOf(VolumeKeyRecorder.Actions.DOWN, VolumeKeyRecorder.Actions.UP))
        assertEquals(listOf("auto_detected", "user_marked_working", "user_marked_not_working", "user_marked_not_tested", "dismissed"), EndReasons.ALL)
        assertEquals(listOf("key_event", "volume_level_change"), DetectionSources.ALL)
    }

    @Test
    fun `each end reason records its own answer - the detection and the technician's works button are both pass, and a dismissal records none of its own`() {
        assertEquals(ManualStatuses.PASS, VolumeKeyRecorder.statusFor(EndReasons.AUTO_DETECTED))
        assertEquals(ManualStatuses.PASS, VolumeKeyRecorder.statusFor(EndReasons.USER_MARKED_WORKING))
        assertEquals(ManualStatuses.FAIL, VolumeKeyRecorder.statusFor(EndReasons.USER_MARKED_NOT_WORKING))
        assertEquals(ManualStatuses.NOT_TESTED, VolumeKeyRecorder.statusFor(EndReasons.USER_MARKED_NOT_TESTED))
        assertNull(VolumeKeyRecorder.statusFor(EndReasons.DISMISSED))
        assertEquals("every end reason is covered", 5, EndReasons.ALL.size)
    }

    // ───────────────────────── a press ─────────────────────────

    @Test
    fun `a press is recorded raw - tMs is the event time minus the opening, and the first press, the hold and the counts come from the events`() {
        val r = recorder()
        assertTrue("the first press of the expected key is reported once", r.press(up, 730))
        assertFalse(r.release(up, 830))
        uptime = 100_000L + 1_330L                       // the dialog closes 600 ms after the detection (the grace)
        val m = r.measurements(EndReasons.AUTO_DETECTED, DetectionSources.KEY_EVENT, VolumeKeyRecorder.VolumeLevels(15, 7, 8))
        assertEquals(24, m[Keys.EXPECTED_KEY_CODE])
        assertEquals(
            listOf(
                linkedMapOf<String, Any?>("tMs" to 730L, "keyCode" to 24, "action" to "down", "repeat" to 0, "deviceId" to 7, "source" to 0x101),
                linkedMapOf<String, Any?>("tMs" to 830L, "keyCode" to 24, "action" to "up", "repeat" to 0, "deviceId" to 7, "source" to 0x101)
            ),
            m.events()
        )
        assertEquals(2, m[Keys.EVENTS_TOTAL])
        assertEquals(730L, m[Keys.TIME_TO_FIRST_EXPECTED_DOWN_MS])
        assertEquals(1, m[Keys.EXPECTED_DOWN_COUNT])
        assertEquals(0, m[Keys.OTHER_KEY_DOWN_COUNT])
        assertEquals("from the first press to its up: 830 - 730", 100L, m[Keys.HOLD_MS])
        assertEquals(1_330L, m[Keys.WINDOW_MS])
        assertEquals("auto_detected", m[Keys.END_REASON])
        assertEquals("key_event", m[Keys.DETECTION_SOURCE])
        assertEquals(15, m[Keys.VOLUME_STREAM_MAX]); assertEquals(7, m[Keys.VOLUME_LEVEL_AT_OPEN]); assertEquals(8, m[Keys.VOLUME_LEVEL_AT_END])
    }

    @Test
    fun `a held key is one press - its repeats are events with repeat above 0 and are counted nowhere else`() {
        val r = recorder()
        assertTrue(r.press(up, 500))
        assertFalse("a repeat is not a first press", r.press(up, 900, repeat = 1))
        r.press(up, 950, repeat = 2)
        r.release(up, 1_200)
        val m = r.measurements(EndReasons.USER_MARKED_NOT_WORKING, null, null)
        assertEquals(4, m[Keys.EVENTS_TOTAL])
        assertEquals("presses, not repeats", 1, m[Keys.EXPECTED_DOWN_COUNT])
        assertEquals(listOf(0, 1, 2, 0), m.events().map { it[EventKeys.REPEAT] })
        assertEquals(700L, m[Keys.HOLD_MS])
    }

    @Test
    fun `a second press does not move the first-press time and the hold is the FIRST press to ITS first up`() {
        val r = recorder()
        r.press(up, 100); r.release(up, 180); r.press(up, 400); r.release(up, 900)
        val m = r.measurements(EndReasons.USER_MARKED_WORKING, null, null)
        assertEquals(2, m[Keys.EXPECTED_DOWN_COUNT])
        assertEquals(100L, m[Keys.TIME_TO_FIRST_EXPECTED_DOWN_MS])
        assertEquals(80L, m[Keys.HOLD_MS])
    }

    @Test
    fun `the other volume key is counted apart - it is no press of the expected key and does not stop the dialog`() {
        val r = recorder(expected = up)
        assertFalse(r.press(down, 300))
        assertFalse(r.expectedPressSeen)
        val m = r.measurements(EndReasons.DISMISSED, null, null)
        assertEquals(0, m[Keys.EXPECTED_DOWN_COUNT])
        assertEquals(1, m[Keys.OTHER_KEY_DOWN_COUNT])
        assertFalse("no expected press: the time is ABSENT, not 0 (rule 22)", m.containsKey(Keys.TIME_TO_FIRST_EXPECTED_DOWN_MS))
        assertFalse("no expected press: no hold", m.containsKey(Keys.HOLD_MS))
        assertEquals("the other key is in events[]", 1, m.events().size)
        assertEquals(25, m.events().single()[EventKeys.KEY_CODE])
    }

    @Test
    fun `M-10 expects key 25 - its own key is the expected one and key 24 is the other`() {
        val r = recorder(expected = down)
        assertFalse(r.press(up, 100))
        assertTrue(r.press(down, 200))
        val m = r.measurements(EndReasons.AUTO_DETECTED, DetectionSources.KEY_EVENT, null)
        assertEquals(25, m[Keys.EXPECTED_KEY_CODE])
        assertEquals(1, m[Keys.EXPECTED_DOWN_COUNT]); assertEquals(1, m[Keys.OTHER_KEY_DOWN_COUNT])
    }

    @Test
    fun `an up with no press before it, an up of the other key, and a second up do not make a hold`() {
        val r = recorder()
        r.release(up, 50)                                // an up first: nothing is held
        r.press(up, 100)
        r.release(down, 130)                             // the other key's up
        var m = r.measurements(EndReasons.DISMISSED, null, null)
        assertFalse(m.containsKey(Keys.HOLD_MS))
        r.release(up, 220)
        r.release(up, 400)
        m = r.measurements(EndReasons.DISMISSED, null, null)
        assertEquals("the first up of the expected key after the first press", 120L, m[Keys.HOLD_MS])
    }

    @Test
    fun `a key that is not a volume key, and an action that is neither down nor up, are not recorded`() {
        val r = recorder()
        assertFalse(r.onKeyEvent(4, true, 100_100L, 0, 1, 1))                                      // KEYCODE_BACK
        assertFalse(r.onKeyEvent(VolumeKeyRecorder.KEYCODE_VOLUME_UP, null, 100_200L, 0, 1, 1))    // ACTION_MULTIPLE
        assertEquals(0, r.eventCount)
        assertEquals(emptyList<Any>(), r.measurements(EndReasons.DISMISSED, null, null).events())
    }

    @Test
    fun `the mute key is a volume key - recorded, and an other key`() {
        val r = recorder()
        r.press(VolumeKeyRecorder.KEYCODE_VOLUME_MUTE, 100)
        val m = r.measurements(EndReasons.DISMISSED, null, null)
        assertEquals(1, m[Keys.EVENTS_TOTAL]); assertEquals(1, m[Keys.OTHER_KEY_DOWN_COUNT]); assertEquals(164, m.events().single()[EventKeys.KEY_CODE])
    }

    @Test
    fun `past the cap the list stops and the counts do not - eventsTotal is the number BEFORE the cap`() {
        val r = recorder(maxEvents = 5)
        for (i in 0 until 8) r.press(down, 100L + i * 10, repeat = i)
        r.press(up, 900)
        val m = r.measurements(EndReasons.DISMISSED, null, null)
        assertEquals(5, m.events().size)
        assertEquals(9, m[Keys.EVENTS_TOTAL])
        assertEquals("the press at 900 is past the list but not past the counts", 1, m[Keys.EXPECTED_DOWN_COUNT])
        assertEquals(900L, m[Keys.TIME_TO_FIRST_EXPECTED_DOWN_MS])
        assertEquals("the cap is the contract's 64 by default", 64, VolumeKeyRecorder.MAX_EVENTS)
    }

    // ───────────────────────── the end, the detection, the levels ─────────────────────────

    @Test
    fun `no event at all - the lists are empty, the counts are real zeros, and every time that needs a press is ABSENT`() {
        val r = recorder()
        uptime += 12_000L
        val m = r.measurements(EndReasons.USER_MARKED_NOT_TESTED, null, null)
        assertEquals(emptyList<Any>(), m.events())
        assertEquals(0, m[Keys.EVENTS_TOTAL]); assertEquals(0, m[Keys.EXPECTED_DOWN_COUNT]); assertEquals(0, m[Keys.OTHER_KEY_DOWN_COUNT])
        assertEquals(12_000L, m[Keys.WINDOW_MS])
        assertEquals(
            setOf(Keys.EXPECTED_KEY_CODE, Keys.EVENTS, Keys.EVENTS_TOTAL, Keys.EXPECTED_DOWN_COUNT, Keys.OTHER_KEY_DOWN_COUNT, Keys.WINDOW_MS, Keys.END_REASON),
            m.keys
        )
    }

    @Test
    fun `detectionSource belongs to auto_detected alone - and an auto_detected run must name its way`() {
        val r = recorder()
        for (source in DetectionSources.ALL) assertEquals(source, r.measurements(EndReasons.AUTO_DETECTED, source, null)[Keys.DETECTION_SOURCE])
        assertThrows(IllegalArgumentException::class.java) { r.measurements(EndReasons.AUTO_DETECTED, null, null) }
        assertThrows(IllegalArgumentException::class.java) { r.measurements(EndReasons.AUTO_DETECTED, "polling", null) }
        for (reason in EndReasons.ALL - EndReasons.AUTO_DETECTED) {
            assertThrows(reason, IllegalArgumentException::class.java) { r.measurements(reason, DetectionSources.KEY_EVENT, null) }
            assertFalse(reason, r.measurements(reason, null, null).containsKey(Keys.DETECTION_SOURCE))
        }
        assertThrows(IllegalArgumentException::class.java) { r.measurements("warning", null, null) }
    }

    @Test
    fun `the three levels are written when read - and each is ABSENT when it was not, none standing in as 0`() {
        val r = recorder()
        val some = r.measurements(EndReasons.USER_MARKED_WORKING, null, VolumeKeyRecorder.VolumeLevels(15, null, 9))
        assertEquals(15, some[Keys.VOLUME_STREAM_MAX])
        assertFalse(some.containsKey(Keys.VOLUME_LEVEL_AT_OPEN))
        assertEquals(9, some[Keys.VOLUME_LEVEL_AT_END])
        val none = r.measurements(EndReasons.USER_MARKED_WORKING, null, null)
        assertFalse(none.containsKey(Keys.VOLUME_STREAM_MAX) || none.containsKey(Keys.VOLUME_LEVEL_AT_OPEN) || none.containsKey(Keys.VOLUME_LEVEL_AT_END))
        val zero = r.measurements(EndReasons.USER_MARKED_WORKING, null, VolumeKeyRecorder.VolumeLevels(15, 0, 0))
        assertEquals("a real level of 0 is a level", 0, zero[Keys.VOLUME_LEVEL_AT_OPEN])
    }

    @Test
    fun `the map holds exactly the contract's keys - no verdict, no threshold`() {
        val r = recorder()
        r.press(up, 300); r.release(up, 400)
        val keys = r.measurements(EndReasons.AUTO_DETECTED, DetectionSources.VOLUME_LEVEL_CHANGE, VolumeKeyRecorder.VolumeLevels(15, 7, 8)).keys
        assertEquals(
            setOf(
                Keys.EXPECTED_KEY_CODE, Keys.EVENTS, Keys.EVENTS_TOTAL, Keys.TIME_TO_FIRST_EXPECTED_DOWN_MS, Keys.EXPECTED_DOWN_COUNT, Keys.OTHER_KEY_DOWN_COUNT,
                Keys.HOLD_MS, Keys.WINDOW_MS, Keys.END_REASON, Keys.DETECTION_SOURCE, Keys.VOLUME_STREAM_MAX, Keys.VOLUME_LEVEL_AT_OPEN, Keys.VOLUME_LEVEL_AT_END
            ),
            keys
        )
    }

    @Test
    fun `the raw survives the Room codec - integers still integers, objects and lists still objects and lists`() {
        val r = recorder()
        r.press(up, 730); r.release(up, 830)
        uptime += 1_330L
        val back = MeasurementsJson.decode(
            MeasurementsJson.encode(r.measurements(EndReasons.AUTO_DETECTED, DetectionSources.KEY_EVENT, VolumeKeyRecorder.VolumeLevels(15, 7, 8)))
        )!!
        assertEquals(24L, back[Keys.EXPECTED_KEY_CODE])
        assertEquals(730L, back[Keys.TIME_TO_FIRST_EXPECTED_DOWN_MS])
        assertEquals(100L, back[Keys.HOLD_MS])
        assertEquals("key_event", back[Keys.DETECTION_SOURCE])
        @Suppress("UNCHECKED_CAST") val events = back[Keys.EVENTS] as List<Map<String, Any?>>
        assertEquals(listOf(730L, 830L), events.map { it["tMs"] })
        assertEquals(listOf("down", "up"), events.map { it["action"] })
        assertEquals(0x101L, events[0]["source"])
    }

    @Test
    fun `the clock is the uptime clock - an event time is subtracted from the opening on that clock and from nothing else`() {
        val r = recorder()
        r.onKeyEvent(up, true, 100_000L + 55L, 0, 3, 0x1002)
        val m = r.measurements(EndReasons.DISMISSED, null, null)
        assertEquals(55L, m.events().single()[EventKeys.T_MS])
        assertEquals(3, m.events().single()[EventKeys.DEVICE_ID])
        assertEquals(0x1002, m.events().single()[EventKeys.SOURCE])
    }
}
