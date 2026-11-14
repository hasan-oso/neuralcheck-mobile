package net.neuralcheck.data.remote

import net.neuralcheck.data.remote.ScanSessionRecords.Recorded
import net.neuralcheck.data.remote.ScanSessionRecords.Use
import net.neuralcheck.util.EvalCardLog
import net.neuralcheck.util.InMemorySharedPreferences
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Order 2026-10-14 (user decision, device check 03 round 2, «Record the session at scan start»): "The scan keeps the
 * session it started in; its analysis and its evaluation draft use that one, never the session joined later."
 *
 * The REAL [ScanSessionRecords] and the REAL [ScanSessionHolder], over an in-memory preferences file (declared
 * substitute for the on-device file, chosen when the module lacked Robolectric - a test dependency since 3284cca). The clock is injected only so that retention can
 * be tested; every other behaviour is the production code. The lines are read through [EvalCardLog.sink], as in the
 * other log tests.
 *
 * What this cannot show: that `StressTestService.startScan` calls [ScanSessionRecords.recordAtScanStart] at the right
 * moment, and that `AiScoringEngine` / `TechnicianEvaluationViewModel` call [ScanSessionRecords.sessionFor] — those
 * are `ScanSessionAtScanStartSourceShapeTest` (text), `ScanSessionAtScanStartRealPathTest` (the view model, through
 * the real join path) and `AiScoringEngineSessionTest` (the engine).
 */
class ScanSessionRecordsTest {

    private companion object {
        const val SESSION_1 = "#abe633f3a47a" // sess-1
        const val SESSION_2 = "#2b8ea9758113" // sess-2
        const val SCAN_1 = "#d6b64f1f202b"    // scan-0001
        const val SCAN_2 = "#244e7653ddcd"    // scan-0002
        val RAW_IDS = listOf("sess-1", "sess-2", "owner-1", "owner-2", "scan-0001", "scan-0002")
    }

    private lateinit var holder: ScanSessionHolder
    private lateinit var prefs: InMemorySharedPreferences
    private var now = 1_000_000L
    private lateinit var records: ScanSessionRecords
    private val lines = mutableListOf<String>()

    @Before
    fun setUp() {
        lines.clear()
        EvalCardLog.sink = { lines.add(it) }
        holder = ScanSessionHolder()
        prefs = InMemorySharedPreferences()
        records = ScanSessionRecords(prefs, holder) { now }
        lines.clear() // the holder's own lines are not under test here
    }

    @After
    fun tearDown() {
        EvalCardLog.resetSink()
    }

    /** [evaluationEnabled] `null` = a join answer that did not carry the flag (order 2026-10-15, rule 22). */
    private fun join(sessionId: String, ownerId: String = "owner-1", evaluationEnabled: Boolean? = true) =
        holder.setFromJoin(JoinSessionResult(sessionId = sessionId, ownerId = ownerId, status = "scanning", evaluationEnabled = evaluationEnabled))

    private fun scanLines() = lines.filter { it.startsWith("scan-session-") }

    private fun assertNoRawId() = lines.forEach { line ->
        RAW_IDS.forEach { assertFalse("`$line` carries the raw id $it", line.contains(it)) }
    }

    // ======================= recording =======================

    @Test
    fun `a scan started in a session records that session and the join answer's flag, and says so`() {
        join("sess-1", evaluationEnabled = true)
        lines.clear()

        val recorded = records.recordAtScanStart("scan-0001")

        assertEquals(Recorded("sess-1", true), recorded)
        assertEquals(Recorded("sess-1", true), records.sessionFor("scan-0001", Use.DRAFT))
        assertEquals(listOf("scan-session-recorded scan=$SCAN_1 session=$SESSION_1 evaluationEnabled=true"), scanLines())
        assertNoRawId()
    }

    @Test
    fun `evaluationEnabled false is recorded as false - not defaulted`() {
        join("sess-1", evaluationEnabled = false)

        assertEquals(Recorded("sess-1", false), records.recordAtScanStart("scan-0001"))
        assertEquals(Recorded("sess-1", false), records.sessionFor("scan-0001", Use.ANALYSIS))
    }

    @Test
    fun `a join answer that did not carry the flag is recorded as absent - not as false - says absent, and reads back as absent after a restart`() {
        join("sess-1", evaluationEnabled = null)
        lines.clear()

        assertEquals(Recorded("sess-1", null), records.recordAtScanStart("scan-0001"))
        assertEquals(
            listOf("scan-session-recorded scan=$SCAN_1 session=$SESSION_1 evaluationEnabled=absent"),
            scanLines()
        )
        assertEquals("the stored token is the word absent", "absent|$now|sess-1", prefs.snapshot()["scan-0001"])
        val restarted = ScanSessionRecords(prefs.sameFileAs(), ScanSessionHolder()) { now }
        assertEquals(Recorded("sess-1", null), restarted.sessionFor("scan-0001", Use.DRAFT))
    }

    @Test
    fun `a scan started in no session records nothing, says so, and has no session afterwards (a personal scan)`() {
        val recorded = records.recordAtScanStart("scan-0001")

        assertNull(recorded)
        assertNull(records.sessionFor("scan-0001", Use.ANALYSIS))
        assertEquals("nothing is stored for a personal scan", emptyMap<String, Any?>(), prefs.snapshot())
        assertEquals(
            listOf("scan-session-recorded scan=$SCAN_1 session=missing evaluationEnabled=none"),
            scanLines()
        )
    }

    @Test
    fun `a blank session id in the holder is not a session`() {
        // ScanSessionHolder never stores a blank id (setFromJoin empties the slot), so this goes through the legacy setter's
        // blank guard: the holder is empty, and the scan is personal.
        holder.set("   ")

        assertNull(records.recordAtScanStart("scan-0001"))
        assertEquals(emptyMap<String, Any?>(), prefs.snapshot())
    }

    @Test
    fun `a blank scan id is never stored`() {
        join("sess-1")

        assertEquals(Recorded("sess-1", true), records.recordAtScanStart(""))
        assertEquals(emptyMap<String, Any?>(), prefs.snapshot())
        assertNull(records.sessionFor("", Use.ANALYSIS))
    }

    // ======================= the scan keeps the session it started in =======================

    @Test
    fun `a scan that started under A is answered with A after B is joined - for the analysis and for the draft`() {
        join("sess-1", ownerId = "owner-1")
        records.recordAtScanStart("scan-0001")
        join("sess-2", ownerId = "owner-2") // joined later
        lines.clear()

        assertEquals("sess-2", holder.get())
        assertEquals(Recorded("sess-1", true), records.sessionFor("scan-0001", Use.ANALYSIS))
        assertEquals(Recorded("sess-1", true), records.sessionFor("scan-0001", Use.DRAFT))
        assertEquals(
            "each use names itself, and says the holder differs",
            listOf(
                "scan-session-differs use=analysis scan=$SCAN_1 recorded=$SESSION_1 holder=$SESSION_2",
                "scan-session-differs use=draft scan=$SCAN_1 recorded=$SESSION_1 holder=$SESSION_2"
            ),
            scanLines()
        )
        assertNoRawId()
    }

    @Test
    fun `no line when the recorded session is the one the holder holds`() {
        join("sess-1")
        records.recordAtScanStart("scan-0001")
        lines.clear()

        records.sessionFor("scan-0001", Use.ANALYSIS)

        assertEquals(emptyList<String>(), scanLines())
    }

    @Test
    fun `a scan that started in no session stays personal after a session is joined - and the line says the holder was ignored`() {
        records.recordAtScanStart("scan-0001") // personal
        join("sess-2", ownerId = "owner-2")
        lines.clear()

        assertNull(records.sessionFor("scan-0001", Use.ANALYSIS))
        assertNull(records.sessionFor("scan-0001", Use.DRAFT))
        assertEquals(
            listOf(
                "scan-session-holder-ignored use=analysis scan=$SCAN_1 holder=$SESSION_2",
                "scan-session-holder-ignored use=draft scan=$SCAN_1 holder=$SESSION_2"
            ),
            scanLines()
        )
        assertNoRawId()
    }

    @Test
    fun `a scan with no record and an empty holder is personal and prints nothing`() {
        assertNull(records.sessionFor("scan-0001", Use.ANALYSIS))
        assertEquals(emptyList<String>(), scanLines())
    }

    @Test
    fun `two scans keep their own sessions`() {
        join("sess-1", ownerId = "owner-1")
        records.recordAtScanStart("scan-0001")
        join("sess-2", ownerId = "owner-2", evaluationEnabled = false)
        records.recordAtScanStart("scan-0002")

        assertEquals(Recorded("sess-1", true), records.sessionFor("scan-0001", Use.ANALYSIS))
        assertEquals(Recorded("sess-2", false), records.sessionFor("scan-0002", Use.ANALYSIS))
    }

    // ======================= what the holder's emptying does not touch =======================

    @Test
    fun `the holder being emptied (a failed analysis, a sign-out) does not take the scan's session - the retry still has it`() {
        join("sess-1")
        records.recordAtScanStart("scan-0001")
        holder.clear(ScanSessionHolder.ClearReason.ANALYSIS_COMPLETE)
        lines.clear()

        assertNull("the holder is empty", holder.getInfo())
        assertEquals(Recorded("sess-1", true), records.sessionFor("scan-0001", Use.ANALYSIS))
        assertEquals(
            listOf("scan-session-differs use=analysis scan=$SCAN_1 recorded=$SESSION_1 holder=missing"),
            scanLines()
        )
    }

    @Test
    fun `the record survives a process restart - a second instance over the same file answers`() {
        join("sess-1")
        records.recordAtScanStart("scan-0001")

        // "the process restarted": a new holder (in memory, empty), a new records object, the same file
        val restarted = ScanSessionRecords(prefs.sameFileAs(), ScanSessionHolder()) { now }

        assertEquals(Recorded("sess-1", true), restarted.sessionFor("scan-0001", Use.ANALYSIS))
    }

    // ======================= consuming =======================

    @Test
    fun `consume removes the scan's record - and only that scan's`() {
        join("sess-1")
        records.recordAtScanStart("scan-0001")
        records.recordAtScanStart("scan-0002")
        lines.clear()

        records.consume("scan-0001")

        assertNull(records.sessionFor("scan-0001", Use.ANALYSIS))
        assertEquals(Recorded("sess-1", true), records.sessionFor("scan-0002", Use.ANALYSIS))
        assertEquals(listOf("scan-session-consumed scan=$SCAN_1"), scanLines().filter { it.startsWith("scan-session-consumed") })
        assertNoRawId()
    }

    @Test
    fun `consuming a scan that has no record changes nothing and prints nothing`() {
        join("sess-1")
        records.recordAtScanStart("scan-0001")
        lines.clear()

        records.consume("scan-0002")
        records.consume("")

        assertEquals(Recorded("sess-1", true), records.sessionFor("scan-0001", Use.ANALYSIS))
        assertEquals(emptyList<String>(), scanLines())
    }

    // ======================= a record that does not parse =======================

    @Test
    fun `every form of unreadable entry is reported and read as no session - never as a session`() {
        val unreadable = listOf(
            "",                                  // empty
            "true",                              // one part
            "true|1000000",                      // two parts
            "yes|1000000|sess-1",                // not a boolean word
            "TRUE|1000000|sess-1",               // wrong case is not the word we write
            "ABSENT|1000000|sess-1",             // nor is this one
            "none|1000000|sess-1",               // `none` is a LOG word (no holder), never a stored token
            "true|soon|sess-1",                  // not a number
            "true|1000000|",                     // empty id
            "true|1000000|   "                   // blank id
        )
        unreadable.forEach { raw ->
            prefs.edit().putString("scan-0001", raw).apply()
            lines.clear()

            assertNull("`$raw` must not read as a session", records.sessionFor("scan-0001", Use.ANALYSIS))
            assertEquals(
                "`$raw` is reported",
                listOf("scan-session-unreadable use=analysis scan=$SCAN_1"),
                scanLines()
            )
        }
    }

    @Test
    fun `an entry that is not a string is unreadable too - and does not throw (the real getString would)`() {
        prefs.edit().putLong("scan-0001", 5L).apply()
        lines.clear()

        assertNull(records.sessionFor("scan-0001", Use.ANALYSIS))
        assertEquals(listOf("scan-session-unreadable use=analysis scan=$SCAN_1"), scanLines())
    }

    @Test
    fun `a session id that contains the separator round-trips`() {
        join("sess|pipe|two")

        assertEquals(Recorded("sess|pipe|two", true), records.recordAtScanStart("scan-0001"))
        assertEquals(Recorded("sess|pipe|two", true), records.sessionFor("scan-0001", Use.ANALYSIS))
    }

    // ======================= retention =======================

    @Test
    fun `entries older than the retention are removed when the next scan is recorded - younger ones stay`() {
        join("sess-1")
        now = 1_000_000L
        records.recordAtScanStart("scan-0001")
        now += ScanSessionRecords.RETENTION_MS - 1
        records.recordAtScanStart("scan-0002") // scan-0001 is one ms short of the limit: stays
        assertEquals(Recorded("sess-1", true), records.sessionFor("scan-0001", Use.ANALYSIS))

        now += 2 // scan-0001 is now past it
        records.recordAtScanStart("scan-0003")

        assertNull("past retention: removed", records.sessionFor("scan-0001", Use.ANALYSIS))
        assertEquals("younger: kept", Recorded("sess-1", true), records.sessionFor("scan-0002", Use.ANALYSIS))
        assertEquals(Recorded("sess-1", true), records.sessionFor("scan-0003", Use.ANALYSIS))
    }

    @Test
    fun `garbage in the file is removed by the next record - the file cannot grow with it`() {
        prefs.edit().putString("scan-junk", "not a record").putLong("scan-long", 3L).apply()
        join("sess-1")

        records.recordAtScanStart("scan-0001")

        assertEquals(setOf("scan-0001"), prefs.snapshot().keys)
    }

    @Test
    fun `the use wire strings are the ones the lines print`() {
        // Batch 2, item 2 (replaces [analysis, draft], rule 23): leave (SessionLeaver) and watch (CustomerSessionWatch) are the two new askers
        // A-P6-08 (replaces [analysis, draft, leave, watch], rule 23): report (ReportViewModel.loadScan) is the fifth - the customer's phone telling the server the report was shown for the first time
        assertEquals(listOf("analysis", "draft", "leave", "watch", "report"), Use.values().map { it.wire })
        assertTrue(ScanSessionRecords.PREFS_NAME.isNotBlank())
    }
}
