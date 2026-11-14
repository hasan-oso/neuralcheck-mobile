package net.neuralcheck.service.collector

import android.content.Intent
import android.os.BatteryManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import java.io.File

/**
 * fieldsVersion 68, T-02 (rules 22, 30, 37): the ONE reader of the battery's charge-cycle count —
 * `EXTRA_CYCLE_COUNT` of the battery broadcast on API >= 34, then the sysfs file, then a declared absence.
 * The footer (`BatteryReader.snapshotHealth`) and B-08 (`BatteryTests`) both call it.
 *
 * The platform's `Intent` is MOCKED: `android.content.Intent` of the unit-test android.jar is a stub (every
 * method returns a default), so a real extras Bundle cannot be built in a JVM (rule 37 — the layer replaced
 * is the platform's Intent; what the broadcast carries on a real device is the device round's to say). The
 * reader itself, its classification and its vocabulary are the real code.
 */
class BatteryCycleCountTest {

    private val extraKey = BatteryManager.EXTRA_CYCLE_COUNT
    private val sysfsPath = SystemFileReader.BATTERY_CYCLE_COUNT_PATH

    /** A broadcast that carries the extra [value], or none when [value] is null. */
    private fun intent(value: Int?): Intent = mock {
        on { hasExtra(extraKey) } doReturn (value != null)
        if (value != null) on { getIntExtra(extraKey, BatteryCycleCount.EXTRA_DEFAULT) } doReturn value
    }

    private fun sysfs(value: Int?): (String) -> Int? = { value }

    private fun read(intent: Intent?, sdk: Int, sysfs: ((String) -> Int?)?) = BatteryCycleCount.read(intent, sdk, sysfs)

    // ───────────────────────── the extra, API >= 34 ─────────────────────────

    @Test
    fun `on API 34 and 35 a count in the broadcast is the reading, source extra_api34`() {
        for (sdk in listOf(34, 35, 36)) {
            val r = read(intent(187), sdk, sysfs(999))
            assertEquals(187, r.count)
            assertEquals("extra_api34", r.source)
            assertEquals("read", r.state)
        }
    }

    @Test
    fun `a zero in the extra is a reading - it ships as 0 and the sysfs file is not consulted`() {
        var sysfsCalls = 0
        val r = read(intent(0), 34) { sysfsCalls++; 999 }
        assertEquals(0, r.count)
        assertEquals("extra_api34", r.source)
        assertEquals("read", r.state)
        assertEquals("the first step that yields a count wins", 0, sysfsCalls)
    }

    @Test
    fun `the extra wins over sysfs when both have a count`() {
        assertEquals(187, read(intent(187), 34, sysfs(212)).count)
        assertEquals("extra_api34", read(intent(187), 34, sysfs(212)).source)
    }

    @Test
    fun `an extra the broadcast does not carry falls through to sysfs - a count there is read, source sysfs path`() {
        val r = read(intent(null), 34, sysfs(212))
        assertEquals(212, r.count)
        assertEquals("sysfs:/sys/class/power_supply/battery/cycle_count", r.source)
        assertEquals("read", r.state)
    }

    @Test
    fun `an extra equal to the unsupported marker is a state, never a count - and it falls through`() {
        val none = read(intent(Int.MIN_VALUE), 34, sysfs(null))
        assertNull(none.count)
        assertEquals("unavailable", none.source)
        assertEquals("not_read:sysfs_not_readable_or_absent_and_extra_unsupported_by_device", none.state)
        assertEquals(212, read(intent(Int.MIN_VALUE), 34, sysfs(212)).count)
    }

    @Test
    fun `a negative extra other than the marker is not a count - its raw value is in the state`() {
        val r = read(intent(-1), 34, sysfs(null))
        assertNull(r.count)
        assertEquals("not_read:sysfs_not_readable_or_absent_and_extra_negative_value=-1", r.state)
    }

    @Test
    fun `an Intent that throws is its message in the state - the scan goes on`() {
        val throwing: Intent = mock {
            on { hasExtra(extraKey) } doThrow IllegalStateException("bundle was recycled")
        }
        val r = read(throwing, 34, sysfs(null))
        assertNull(r.count)
        assertEquals("not_read:sysfs_not_readable_or_absent_and_extra_read_failed=bundle was recycled", r.state)
    }

    @Test
    fun `no broadcast on API 34 is its own state`() {
        val r = read(null, 34, sysfs(null))
        assertEquals("not_read:sysfs_not_readable_or_absent_and_no_battery_broadcast", r.state)
        assertEquals(212, read(null, 34, sysfs(212)).count)
    }

    @Test
    fun `a broadcast without the extra says so, not unsupported`() {
        val r = read(intent(null), 34, sysfs(null))
        assertEquals("not_read:sysfs_not_readable_or_absent_and_extra_absent_from_broadcast", r.state)
    }

    // ───────────────────────── below API 34 ─────────────────────────

    @Test
    fun `below API 34 the extra is not read at all - even a broadcast that carries one`() {
        val i = intent(187)
        val r = read(i, 33, sysfs(null))
        assertNull(r.count)
        assertEquals("not_read:sysfs_not_readable_or_absent_and_api_below_34", r.state)
        verify(i, never()).hasExtra(any())
        verify(i, never()).getIntExtra(any(), any())
    }

    @Test
    fun `below API 34 sysfs is the only source`() {
        val r = read(intent(187), 30, sysfs(247))
        assertEquals(247, r.count)
        assertEquals("sysfs:/sys/class/power_supply/battery/cycle_count", r.source)
        assertEquals("read", r.state)
    }

    // ───────────────────────── sysfs ─────────────────────────

    @Test
    fun `a zero from sysfs is a reading`() {
        val r = read(null, 30, sysfs(0))
        assertEquals(0, r.count)
        assertEquals("read", r.state)
    }

    @Test
    fun `a negative sysfs value is not a count - its raw value is in the state`() {
        val r = read(null, 30, sysfs(-5))
        assertNull(r.count)
        assertEquals("not_read:sysfs_negative_value=-5_and_api_below_34", r.state)
    }

    @Test
    fun `no sysfs reader is told apart from a reader that found nothing`() {
        assertEquals("not_read:no_sysfs_reader_and_api_below_34", read(null, 30, null).state)
        assertEquals("not_read:no_sysfs_reader_and_no_battery_broadcast", read(null, 34, null).state)
        assertEquals("not_read:sysfs_not_readable_or_absent_and_api_below_34", read(null, 30, sysfs(null)).state)
    }

    @Test
    fun `the states the footer used before are the same strings`() {
        // BatteryReadStates keeps these two as named constants (the S10, API 30, outcome of the real golden recording).
        assertEquals(BatteryReadStates.notRead(BatteryReadStates.REASON_SYSFS_NOT_READABLE_AND_API_BELOW_34), read(null, 30, sysfs(null)).state)
        assertEquals(BatteryReadStates.notRead(BatteryReadStates.REASON_NO_SYSFS_READER_AND_API_BELOW_34), read(null, 30, null).state)
    }

    // ───────────────────────── the whole vocabulary (rule 30) ─────────────────────────

    /**
     * Every input the reader can be given, crossed: 3 API levels x 7 broadcasts x 5 sysfs answers. For each,
     * the invariants that make the footer's three fields readable without guessing: a count exists exactly
     * when the state is `read`; the source is one of the card's three forms and is `unavailable` exactly when
     * there is no count; an absence state is `not_read:<sysfs reason>_and_<extra reason>` over the CLOSED sets
     * of reasons — nothing outside them.
     */
    @Test
    fun `every combination yields a source and a state from the closed vocabulary`() {
        val broadcasts: List<Pair<String, Intent?>> = listOf(
            "none" to null, "absent" to intent(null), "marker" to intent(Int.MIN_VALUE), "negative" to intent(-7),
            "zero" to intent(0), "count" to intent(187),
            "throws" to mock<Intent> { on { hasExtra(extraKey) } doThrow RuntimeException("x") }
        )
        val sysfsAnswers: List<((String) -> Int?)?> = listOf(null, sysfs(null), sysfs(-3), sysfs(0), sysfs(212))
        val extraReasons = listOf(
            BatteryCycleCount.EXTRA_REASON_API_BELOW_34, BatteryCycleCount.EXTRA_REASON_NO_BATTERY_BROADCAST,
            BatteryCycleCount.EXTRA_REASON_ABSENT_FROM_BROADCAST, BatteryCycleCount.EXTRA_REASON_UNSUPPORTED_BY_DEVICE,
            BatteryCycleCount.EXTRA_REASON_NEGATIVE_PREFIX, BatteryCycleCount.EXTRA_REASON_READ_FAILED_PREFIX
        )
        val sysfsReasons = listOf(
            BatteryCycleCount.SYSFS_REASON_NO_READER, BatteryCycleCount.SYSFS_REASON_NOT_READABLE_OR_ABSENT,
            BatteryCycleCount.SYSFS_REASON_NEGATIVE_PREFIX
        )
        val sources = setOf("extra_api34", "sysfs:$sysfsPath", "unavailable")
        var combinations = 0
        for (sdk in listOf(30, 34, 35)) for ((name, i) in broadcasts) for (sys in sysfsAnswers) {
            combinations++
            val r = read(i, sdk, sys)
            val where = "sdk=$sdk broadcast=$name sysfs=${sys?.invoke(sysfsPath)} -> $r"
            assertTrue("source outside the card's vocabulary: $where", r.source in sources)
            if (r.count != null) {
                assertEquals("a count means state read: $where", "read", r.state)
                assertTrue("a count is never negative: $where", r.count!! >= 0)
                assertFalse("a count is never `unavailable`: $where", r.source == "unavailable")
            } else {
                assertEquals("no count means unavailable: $where", "unavailable", r.source)
                assertTrue("an absence is not_read:<reason>: $where", r.state.startsWith("not_read:"))
                val reason = r.state.removePrefix("not_read:")
                val (sysPart, extraPart) = reason.split("_and_", limit = 2).let { it[0] to it[1] }
                assertTrue("sysfs reason outside the closed set: $where", sysfsReasons.any { sysPart.startsWith(it) })
                assertTrue("extra reason outside the closed set: $where", extraReasons.any { extraPart.startsWith(it) })
            }
        }
        assertEquals(3 * 7 * 5, combinations)
    }

    // ───────────────────────── one reader (rule 24) ─────────────────────────

    private fun mainSource(rel: String): String {
        val root = listOf(File("src/main/java"), File("app/src/main/java")).first { it.isDirectory }
        return File(root, rel).readText(Charsets.UTF_8)
    }

    @Test
    fun `the footer and B-08 call the same reader, and no second one exists`() {
        val battery = mainSource("net/neuralcheck/service/collector/BatteryReader.kt")
        val tests = mainSource("net/neuralcheck/hardware/battery/BatteryTests.kt")
        assertTrue("snapshotHealth must read the cycle count through BatteryCycleCount.read", battery.contains("BatteryCycleCount.read("))
        assertTrue("B-08 must read the cycle count through BatteryCycleCount.read", tests.contains("BatteryCycleCount.read("))

        val root = listOf(File("src/main/java"), File("app/src/main/java")).first { it.isDirectory }
        // Code only: the comments that explain why the property is gone name it.
        fun code(text: String) = text.lines().filterNot {
            val t = it.trim()
            t.startsWith("//") || t.startsWith("*") || t.startsWith("/*")
        }.joinToString("\n")
        val offenders = root.walkTopDown().filter { it.isFile && it.extension == "kt" }.flatMap { f ->
            val text = code(f.readText(Charsets.UTF_8))
            buildList {
                if (text.contains("getIntProperty(8)")) add("${f.name}: getIntProperty(8) — not a public property (javap: BATTERY_PROPERTY_* 1..6)")
                if (text.contains("readBatteryCycleCount()")) add("${f.name}: the removed second reader")
                if (f.name != "BatteryCycleCount.kt" && text.contains("BatteryManager.EXTRA_CYCLE_COUNT"))
                    add("${f.name}: reads EXTRA_CYCLE_COUNT outside BatteryCycleCount")
            }
        }.toList()
        assertTrue("a second cycle-count reader: $offenders", offenders.isEmpty())
    }

    @Test
    fun `the default handed to getIntExtra is the platform's unsupported marker - not an integer count`() {
        assertEquals(Int.MIN_VALUE, BatteryCycleCount.EXTRA_DEFAULT)
        assertEquals(34, BatteryCycleCount.FIRST_API_WITH_EXTRA)
        assertEquals(listOf(sysfsPath), BatteryCycleCount.SYSFS_PATHS)
    }
}
