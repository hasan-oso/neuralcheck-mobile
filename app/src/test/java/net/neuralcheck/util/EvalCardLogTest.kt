package net.neuralcheck.util

import net.neuralcheck.data.remote.ScanSessionHolder
import net.neuralcheck.util.EvalCardLog.Flag
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Order 2026-10-10 (user decision, permanent named log lines): the text of each [EvalCardLog] line, for EVERY value of
 * every named state it can carry (rule 30 — the whole enumeration, not the one a sample showed), and that no line
 * carries a raw id.
 *
 * These are the PURE halves: the line builders called with known inputs. That the lines are printed at their events,
 * through the real join path, is `EvalCardLogRealPathTest`. How the lines are READ here: [EvalCardLog.sink] is
 * replaced (a plain `(String) -> Unit` the object prints through; `isReturnDefaultValues` alone would swallow
 * `Log.i` and leave nothing to read).
 *
 * The ids below ("sess-1", "owner-1", "anon-uid-1", "scan-0001") are the ones the real-path harness uses; their hash
 * prefixes were computed with Node (see [LogRedactionTest]).
 */
class EvalCardLogTest {

    private val rawIds = listOf("sess-1", "owner-1", "anon-uid-1", "scan-0001", "NC-AB3F7K")

    @After
    fun tearDown() {
        EvalCardLog.resetSink()
    }

    private fun assertNoRawId(line: String) {
        rawIds.forEach { assertFalse("`$line` carries the raw id $it", line.contains(it)) }
    }

    // ---- the tag ----

    @Test
    fun `the tag is the one exported constant`() {
        assertEquals("NeuralCheck.EvalCard", EvalCardLog.TAG)
    }

    // ---- the raw kind of a flag value, every kind ----

    @Test
    fun `rawKind names every kind a raw value can have`() {
        val answer = mapOf<String, Any?>(
            "t" to true, "f" to false, "n" to null,
            "st" to "true", "sT" to "TRUE", "sf" to "false", "so" to "yes", "se" to "",
            "num" to 1, "dbl" to 1.0,
            "map" to mapOf("a" to 1), "list" to listOf(true), "obj" to Any()
        )
        val expected = mapOf(
            "t" to "Boolean:true", "f" to "Boolean:false", "n" to "null",
            "st" to "String:true", "sT" to "String:true", "sf" to "String:false", "so" to "String:other",
            "se" to "String:other",
            "num" to "Number", "dbl" to "Number",
            "map" to "Map", "list" to "List", "obj" to "Other",
            "gone" to "absent"
        )
        expected.forEach { (key, kind) -> assertEquals(key, kind, EvalCardLog.rawKind(answer, key)) }
        assertEquals("an answer that is not a map", "n/a", EvalCardLog.rawKind(null, "t"))
    }

    // ---- the join answer, as it came ----

    @Test
    fun `the raw line - a boolean flag - exact text, hashed ids`() {
        val data = hashMapOf<String, Any?>(
            "sessionId" to "sess-1", "ownerId" to "owner-1", "status" to "scanning", "evaluationEnabled" to true
        )
        val line = EvalCardLog.joinResponseRawLine(data, joinerUid = "anon-uid-1")
        assertEquals(
            "join-response-raw data=Map evaluationEnabled=Boolean:true evaluation_enabled=absent" +
                " session=#abe633f3a47a owner=#391887cbcf92 joiner=#9bfcd2b216cf",
            line
        )
        assertNoRawId(line)
    }

    @Test
    fun `the raw line - when the answer is not a map, or ids are absent or of the wrong type`() {
        assertEquals(
            "join-response-raw data=null evaluationEnabled=n/a evaluation_enabled=n/a" +
                " session=missing owner=missing joiner=missing",
            EvalCardLog.joinResponseRawLine(null, joinerUid = null)
        )
        assertEquals(
            "join-response-raw data=Other evaluationEnabled=n/a evaluation_enabled=n/a" +
                " session=missing owner=missing joiner=missing",
            EvalCardLog.joinResponseRawLine("a string, not a map", joinerUid = "")
        )
        assertEquals(
            "join-response-raw data=Map evaluationEnabled=absent evaluation_enabled=Boolean:false" +
                " session=invalid-type owner=missing joiner=missing",
            EvalCardLog.joinResponseRawLine(mapOf("sessionId" to 7, "evaluation_enabled" to false), joinerUid = null)
        )
    }

    @Test
    fun `the flag - every value, and of() puts a Boolean-or-null on it - an absence is never false`() {
        // order 2026-10-15 (rule 22; rule 30: the whole enumeration). The three words are ALSO the tokens ScanSessionRecords stores.
        assertEquals(listOf("true", "false", "absent"), Flag.values().map { it.wire })
        assertEquals(Flag.TRUE, Flag.of(true))
        assertEquals(Flag.FALSE, Flag.of(false))
        assertEquals(Flag.ABSENT, Flag.of(null))
    }

    @Test
    fun `the parsed line - exact text, hashed ids, a blank session id reads missing`() {
        val line = EvalCardLog.joinResponseParsedLine("sess-1", "owner-1", Flag.TRUE, "anon-uid-1")
        assertEquals(
            "join-response-parsed evaluationEnabled=true session=#abe633f3a47a owner=#391887cbcf92 joiner=#9bfcd2b216cf",
            line
        )
        assertNoRawId(line)
        assertEquals(
            "join-response-parsed evaluationEnabled=false session=missing owner=missing joiner=missing",
            EvalCardLog.joinResponseParsedLine("", "", Flag.FALSE, null)
        )
        // order 2026-10-15 (rule 22): an answer that did not carry the flag prints as such - not as false
        assertEquals(
            "join-response-parsed evaluationEnabled=absent session=#abe633f3a47a owner=#391887cbcf92 joiner=#9bfcd2b216cf",
            EvalCardLog.joinResponseParsedLine("sess-1", "owner-1", Flag.ABSENT, "anon-uid-1")
        )
    }

    /** ق15: the join's consent part, printed once at the join - exact text, a hashed session id, and `absent` as its own word (rule 22). */
    @Test
    fun `the consent-part line - exact text, the hashed session id, and an absent answer is absent not false`() {
        val line = EvalCardLog.joinConsentPartLine("sess-1", Flag.TRUE)
        assertEquals("join-consent-part consentEnabled=true session=#abe633f3a47a", line)
        assertNoRawId(line)
        assertEquals("join-consent-part consentEnabled=false session=#abe633f3a47a", EvalCardLog.joinConsentPartLine("sess-1", Flag.FALSE))
        assertEquals("join-consent-part consentEnabled=absent session=#abe633f3a47a", EvalCardLog.joinConsentPartLine("sess-1", Flag.ABSENT))
        assertEquals("join-consent-part consentEnabled=absent session=missing", EvalCardLog.joinConsentPartLine(null, Flag.ABSENT))
    }

    // ---- the holder ----

    @Test
    fun `the holder-set line - both ways in, stored or not, with what it replaced`() {
        val join = EvalCardLog.holderSetLine(EvalCardLog.VIA_JOIN, "sess-1", "owner-1", Flag.TRUE, true, null)
        assertEquals(
            "holder-set via=join stored=true session=#abe633f3a47a owner=#391887cbcf92" +
                " evaluationEnabled=true previousEvaluationEnabled=none",
            join
        )
        assertEquals(
            "holder-set via=join stored=true session=#abe633f3a47a owner=#391887cbcf92" +
                " evaluationEnabled=false previousEvaluationEnabled=true",
            EvalCardLog.holderSetLine(EvalCardLog.VIA_JOIN, "sess-1", "owner-1", Flag.FALSE, true, Flag.TRUE)
        )
        // order 2026-10-15: an absent flag, and an absent flag that was replaced - `absent` is not `none` (no holder)
        assertEquals(
            "holder-set via=join stored=true session=#abe633f3a47a owner=#391887cbcf92" +
                " evaluationEnabled=absent previousEvaluationEnabled=absent",
            EvalCardLog.holderSetLine(EvalCardLog.VIA_JOIN, "sess-1", "owner-1", Flag.ABSENT, true, Flag.ABSENT)
        )
        assertEquals(
            "holder-set via=legacy stored=false session=missing owner=missing evaluationEnabled=false" +
                " previousEvaluationEnabled=false",
            EvalCardLog.holderSetLine(EvalCardLog.VIA_LEGACY, "", null, Flag.FALSE, false, Flag.FALSE)
        )
        assertNoRawId(join)
    }

    @Test
    fun `the holder-clear line - every named reason, with and without something to clear`() {
        // The enumeration of reasons is the enum's: a new reason must be given a wire string and appear here.
        assertEquals(
            // Batch 2, item 2 (replaces [sign-out, analysis-complete], rule 23): left-by-customer (D2) and cancelled-by-owner (D5)
            // A-P6-12 (2026-10-21; replaces the four, rule 23): consent-declined, the customer's «لا أوافق» on the consent screen
            listOf(
                "sign-out" to "SIGN_OUT", "analysis-complete" to "ANALYSIS_COMPLETE",
                "left-by-customer" to "LEFT_BY_CUSTOMER", "consent-declined" to "CONSENT_DECLINED", "cancelled-by-owner" to "CANCELLED_BY_OWNER"
            ),
            ScanSessionHolder.ClearReason.values().map { it.wire to it.toString() }
        )
        ScanSessionHolder.ClearReason.values().forEach { reason ->
            val full = EvalCardLog.holderClearLine(reason.wire, true, "sess-1", Flag.TRUE)
            assertEquals(
                "holder-clear reason=${reason.wire} hadInfo=true session=#abe633f3a47a evaluationEnabled=true",
                full
            )
            assertNoRawId(full)
            assertEquals(
                "holder-clear reason=${reason.wire} hadInfo=false session=missing evaluationEnabled=none",
                EvalCardLog.holderClearLine(reason.wire, false, null, null)
            )
            assertEquals(
                "holder-clear reason=${reason.wire} hadInfo=true session=#abe633f3a47a evaluationEnabled=absent",
                EvalCardLog.holderClearLine(reason.wire, true, "sess-1", Flag.ABSENT)
            )
        }
    }

    // ---- the card's view model ----

    @Test
    fun `the view-model-built line - a snapshot with either flag, and no snapshot`() {
        val yes = EvalCardLog.viewModelBuiltLine(true, Flag.TRUE, true, "sess-1")
        assertEquals("eval-vm-built snapshot=present evaluationEnabled=true isEvaluationSession=true session=#abe633f3a47a", yes)
        assertNoRawId(yes)
        assertEquals(
            "eval-vm-built snapshot=present evaluationEnabled=false isEvaluationSession=false session=#abe633f3a47a",
            EvalCardLog.viewModelBuiltLine(true, Flag.FALSE, false, "sess-1")
        )
        assertEquals(
            "eval-vm-built snapshot=present evaluationEnabled=absent isEvaluationSession=false session=#abe633f3a47a",
            EvalCardLog.viewModelBuiltLine(true, Flag.ABSENT, false, "sess-1")
        )
        assertEquals(
            "eval-vm-built snapshot=absent evaluationEnabled=none isEvaluationSession=false session=missing",
            EvalCardLog.viewModelBuiltLine(false, null, false, null)
        )
    }

    @Test
    fun `the bind line - every named result, and a blank scan id reads missing`() {
        assertEquals(
            listOf("started", "already-bound", "scan-id-blank", "not-evaluation-session", "flag-absent", "no-session-id"),
            EvalCardLog.BindResult.values().map { it.wire }
        )
        EvalCardLog.BindResult.values().forEach { result ->
            val line = EvalCardLog.bindResultLine(result, "scan-0001")
            assertEquals("eval-bind result=${result.wire} scan=#d6b64f1f202b", line)
            assertNoRawId(line)
        }
        assertEquals("eval-bind result=scan-id-blank scan=missing", EvalCardLog.bindResultLine(EvalCardLog.BindResult.SCAN_ID_BLANK, ""))
    }

    @Test
    fun `the read lines - the start line, and the done line for found true and for found false`() {
        // Order 2026-10-14 (diagnostic build): the two lines bracketing the Room read in `bind`. Written out in full,
        // not built from the arguments, so each string below is, literally, what logcat shows.
        val start = EvalCardLog.bindReadStartLine("scan-0001")
        assertEquals("eval-bind-read-start scan=#d6b64f1f202b", start)
        assertNoRawId(start)
        assertEquals("eval-bind-read-start scan=missing", EvalCardLog.bindReadStartLine(""))
        assertEquals("eval-bind-read-start scan=missing", EvalCardLog.bindReadStartLine(null))

        val found = EvalCardLog.bindReadDoneLine(true, "scan-0001")
        assertEquals("eval-bind-read-done found=true scan=#d6b64f1f202b", found)
        assertNoRawId(found)
        val notFound = EvalCardLog.bindReadDoneLine(false, "scan-0001")
        assertEquals("eval-bind-read-done found=false scan=#d6b64f1f202b", notFound)
        assertNoRawId(notFound)
        assertEquals("eval-bind-read-done found=false scan=missing", EvalCardLog.bindReadDoneLine(false, null))
    }

    @Test
    fun `the draft lines - both states, and a failed read names the exception class`() {
        assertEquals(listOf("new", "restored"), EvalCardLog.DraftState.values().map { it.wire })
        EvalCardLog.DraftState.values().forEach { state ->
            val line = EvalCardLog.draftReadyLine(state, "scan-0001")
            assertEquals("eval-bind-draft state=${state.wire} scan=#d6b64f1f202b", line)
            assertNoRawId(line)
        }
        val failed = EvalCardLog.draftReadFailedLine(java.io.IOException("scan-0001 could not be read"), "scan-0001")
        assertEquals("eval-bind-read-failed error=java.io.IOException scan=#d6b64f1f202b", failed)
        assertNoRawId(failed) // the exception's MESSAGE (which here holds the id) is never printed
    }

    @Test
    fun `the draft-failed line names the exception class - an Error too - and never its message`() {
        // Order 2026-10-14 («Root fix + guards»): "eval-bind-draft-failed error=<class>". Written out in full, so each
        // string below is, literally, what logcat shows. The three classes are the ones the failure on 1.0.8-1.0.13
        // would have produced: the first access throws ExceptionInInitializerError (an Error, not an Exception), every
        // later one NoClassDefFoundError.
        val plain = EvalCardLog.draftFailedLine(IllegalStateException("TypeToken must be created with a type argument for sess-1"), "scan-0001")
        assertEquals("eval-bind-draft-failed error=java.lang.IllegalStateException scan=#d6b64f1f202b", plain)
        assertNoRawId(plain) // the message (which here holds an id) is never printed

        val init = EvalCardLog.draftFailedLine(ExceptionInInitializerError(IllegalStateException("x")), "scan-0001")
        assertEquals("eval-bind-draft-failed error=java.lang.ExceptionInInitializerError scan=#d6b64f1f202b", init)

        val later = EvalCardLog.draftFailedLine(NoClassDefFoundError("Could not initialize class EvaluationAnswers"), "scan-0001")
        assertEquals("eval-bind-draft-failed error=java.lang.NoClassDefFoundError scan=#d6b64f1f202b", later)

        assertEquals("eval-bind-draft-failed error=java.lang.IllegalStateException scan=missing", EvalCardLog.draftFailedLine(IllegalStateException(), null))
    }

    @Test
    fun `the scan-session lines - exact text, hashed ids, missing and none for an absent value`() {
        // Order 2026-10-14 («Record the session at scan start»): written out in full, so each string below is,
        // literally, what logcat shows. Prefixes: sess-1 = #abe633f3a47a, sess-2 = #2b8ea9758113, scan-0001 = #d6b64f1f202b.
        val recorded = EvalCardLog.scanSessionRecordedLine("scan-0001", "sess-1", Flag.TRUE)
        assertEquals("scan-session-recorded scan=#d6b64f1f202b session=#abe633f3a47a evaluationEnabled=true", recorded)
        assertNoRawId(recorded)
        assertEquals(
            "scan-session-recorded scan=#d6b64f1f202b session=#abe633f3a47a evaluationEnabled=false",
            EvalCardLog.scanSessionRecordedLine("scan-0001", "sess-1", Flag.FALSE)
        )
        assertEquals(
            "scan-session-recorded scan=#d6b64f1f202b session=#abe633f3a47a evaluationEnabled=absent",
            EvalCardLog.scanSessionRecordedLine("scan-0001", "sess-1", Flag.ABSENT)
        )
        assertEquals(
            "a personal scan: no session, no flag",
            "scan-session-recorded scan=#d6b64f1f202b session=missing evaluationEnabled=none",
            EvalCardLog.scanSessionRecordedLine("scan-0001", null, null)
        )

        val differs = EvalCardLog.scanSessionDiffersLine("analysis", "scan-0001", "sess-1", "sess-2")
        assertEquals("scan-session-differs use=analysis scan=#d6b64f1f202b recorded=#abe633f3a47a holder=#2b8ea9758113", differs)
        assertNoRawId(differs)
        assertEquals(
            "scan-session-differs use=draft scan=#d6b64f1f202b recorded=#abe633f3a47a holder=missing",
            EvalCardLog.scanSessionDiffersLine("draft", "scan-0001", "sess-1", null)
        )

        val ignored = EvalCardLog.scanSessionHolderIgnoredLine("analysis", "scan-0001", "sess-2")
        assertEquals("scan-session-holder-ignored use=analysis scan=#d6b64f1f202b holder=#2b8ea9758113", ignored)
        assertNoRawId(ignored)

        assertEquals("scan-session-unreadable use=draft scan=#d6b64f1f202b", EvalCardLog.scanSessionUnreadableLine("draft", "scan-0001"))
        assertEquals("scan-session-consumed scan=#d6b64f1f202b", EvalCardLog.scanSessionConsumedLine("scan-0001"))
        assertEquals("scan-session-consumed scan=missing", EvalCardLog.scanSessionConsumedLine(null))
    }

    @Test
    fun `the dismissed and handoff lines - every combination of the booleans`() {
        listOf(true, false).forEach { ready ->
            val line = EvalCardLog.dismissedLine(ready, "sess-1")
            assertEquals("eval-dismissed draftReady=$ready session=#abe633f3a47a", line)
            assertNoRawId(line)
        }
        for (session in listOf(true, false)) for (ready in listOf(true, false)) for (dismissed in listOf(true, false)) {
            val line = EvalCardLog.handoffLine(session, ready, dismissed, "scan-0001")
            assertEquals(
                "eval-handoff isEvaluationSession=$session draftReady=$ready dismissed=$dismissed scan=#d6b64f1f202b",
                line
            )
            assertNoRawId(line)
        }
    }

    // ---- a line never changes behaviour ----

    @Test
    fun `a sink that throws does not escape a log call`() {
        EvalCardLog.sink = { throw IllegalStateException("the sink broke") }

        EvalCardLog.bindResult(EvalCardLog.BindResult.STARTED, "scan-0001")
        EvalCardLog.bindReadStart("scan-0001")
        EvalCardLog.bindReadDone(true, "scan-0001")
        EvalCardLog.draftFailed(IllegalStateException("x"), "scan-0001")
        EvalCardLog.holderClear("sign-out", true, "sess-1", Flag.TRUE)
        EvalCardLog.joinResponseRaw(hashMapOf<String, Any?>("x" to 1), "anon-uid-1")

        assertTrue("reached here", true)
    }

    @Test
    fun `lines go to the sink as given`() {
        val lines = mutableListOf<String>()
        EvalCardLog.sink = { lines.add(it) }

        EvalCardLog.bindResult(EvalCardLog.BindResult.ALREADY_BOUND, "scan-0001")

        assertEquals(listOf("eval-bind result=already-bound scan=#d6b64f1f202b"), lines)
    }

    @Test
    fun `the two read lines go to the sink as given`() {
        val lines = mutableListOf<String>()
        EvalCardLog.sink = { lines.add(it) }

        EvalCardLog.bindReadStart("scan-0001")
        EvalCardLog.bindReadDone(found = true, scanId = "scan-0001")
        EvalCardLog.bindReadDone(found = false, scanId = "scan-0001")

        assertEquals(
            listOf(
                "eval-bind-read-start scan=#d6b64f1f202b",
                "eval-bind-read-done found=true scan=#d6b64f1f202b",
                "eval-bind-read-done found=false scan=#d6b64f1f202b"
            ),
            lines
        )
    }

    @Test
    fun `the draft-failed line goes to the sink as given`() {
        val lines = mutableListOf<String>()
        EvalCardLog.sink = { lines.add(it) }

        EvalCardLog.draftFailed(ExceptionInInitializerError(), "scan-0001")

        assertEquals(listOf("eval-bind-draft-failed error=java.lang.ExceptionInInitializerError scan=#d6b64f1f202b"), lines)
    }
}
