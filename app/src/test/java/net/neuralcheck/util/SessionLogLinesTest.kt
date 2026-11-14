package net.neuralcheck.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The `session-*` lines of [EvalCardLog] (batch 2, order 2026-10-15): their exact shape, that an id is only ever printed hashed, and that a server-sent text
 * is bounded and reduced to a safe alphabet (a refusal can carry text this app has no case for - `raw=` must not become a way to print a message).
 * Same contract as the lines `EvalCardLogTest` pins: booleans, named states and hashed ids; no raw id; no stack trace.
 */
class SessionLogLinesTest {

    private val session = "session-id-raw-9"
    private val scan = "scan-id-raw-7"
    private val hashedSession = LogRedaction.hashId(session)
    private val hashedScan = LogRedaction.hashId(scan)

    private fun assertNoRawId(line: String) {
        assertFalse(line, line.contains(session) || line.contains(scan))
    }

    @Test
    fun `token - bounded to forty characters of a safe alphabet, a dash for null, empty for an empty text`() {
        assertEquals("-", EvalCardLog.token(null))
        assertEquals("empty", EvalCardLog.token(""))
        assertEquals("SESSION_BRAND_NEW.v2-x", EvalCardLog.token("SESSION_BRAND_NEW.v2-x"))
        assertEquals("a?b?c", EvalCardLog.token("a b\nc"))
        assertEquals("non-ASCII letters (an Arabic message) are not carried", "?????", EvalCardLog.token("الكود"))
        assertEquals(40, EvalCardLog.token("x".repeat(500)).length)
    }

    @Test
    fun `session-refused - the call, the code or its named state, whether it was a Functions error, and the text key shown`() {
        assertEquals(
            "session-refused call=join-typed reasonCode=SESSION_COMPLETED raw=- functionsException=true shown=session_join_completed",
            EvalCardLog.sessionRefusedLine("join-typed", "SESSION_COMPLETED", null, true, "session_join_completed")
        )
        assertEquals(
            "session-refused call=cancel-owner reasonCode=unrecognized raw=SESSION_BRAND_NEW functionsException=true shown=error_generic",
            EvalCardLog.sessionRefusedLine("cancel-owner", "unrecognized", "SESSION_BRAND_NEW", true, "error_generic")
        )
        assertEquals(
            "session-refused call=join-qr reasonCode=absent raw=- functionsException=false shown=qr_join_failed",
            EvalCardLog.sessionRefusedLine("join-qr", "absent", null, false, "qr_join_failed")
        )
    }

    @Test
    fun `cancel lines carry the role, the reason and a hashed session id only`() {
        val sent = EvalCardLog.sessionCancelSentLine("customer", "left_by_customer", session)
        assertEquals("session-cancel-sent role=customer reason=left_by_customer session=$hashedSession", sent)
        assertNoRawId(sent)
        val answer = EvalCardLog.sessionCancelAnswerLine("owner", "unexpected", "unknown", "done", session)
        assertEquals("session-cancel-answer role=owner outcome=unexpected status=unknown statusRaw=done session=$hashedSession", answer)
        assertNoRawId(answer)
        assertEquals(
            "session-cancel-answer role=owner outcome=already-cancelled status=cancelled statusRaw=- session=$hashedSession",
            EvalCardLog.sessionCancelAnswerLine("owner", "already-cancelled", "cancelled", null, session)
        )
    }

    @Test
    fun `leave and clear-skipped lines print the holder's outcome with hashed ids`() {
        val leave = EvalCardLog.sessionLeaveLine("customer", "pre-scan", "left", session, "kept-other-session")
        assertEquals("session-leave role=customer phase=pre-scan result=left session=$hashedSession holder=kept-other-session", leave)
        assertNoRawId(leave)
        val skipped = EvalCardLog.holderClearSkippedLine("left-by-customer", session, scan)
        assertEquals("holder-clear-skipped reason=left-by-customer requested=$hashedSession held=$hashedScan", skipped)
        assertNoRawId(skipped)
        assertEquals(
            "holder-clear-skipped reason=cancelled-by-owner requested=$hashedSession held=missing",
            EvalCardLog.holderClearSkippedLine("cancelled-by-owner", session, null)
        )
    }

    @Test
    fun `the local and the expired leave lines name the role, the phase, the reason and a hashed session id only`() {
        val local = EvalCardLog.sessionLeaveLocalLine("customer", "scan", "offline", session)
        assertEquals("session-leave-local role=customer phase=scan reason=offline session=$hashedSession", local)
        assertNoRawId(local)
        val expired = EvalCardLog.sessionLeaveExpiredLine("customer", "pre-scan", session)
        assertEquals("session-leave-expired role=customer phase=pre-scan session=$hashedSession", expired)
        assertNoRawId(expired)
        assertEquals("a missing session prints the word the other lines print", "session-leave-local role=customer phase=scan reason=offline session=missing", EvalCardLog.sessionLeaveLocalLine("customer", "scan", "offline", null))
        // batch 4, item 2: the same line with the other reason (the one the leaver prints for SESSION_NOT_PARTICIPANT)
        val notParticipant = EvalCardLog.sessionLeaveLocalLine("customer", "pre-scan", "not_participant", session)
        assertEquals("session-leave-local role=customer phase=pre-scan reason=not_participant session=$hashedSession", notParticipant)
        assertNoRawId(notParticipant)
    }

    @Test
    fun `owner-cancelled-shown names the reason as read, the bounded unknown text, the source and the text key, with a hashed session id`() {
        val known = EvalCardLog.ownerCancelledShownLine(session, "snapshot", "left_by_customer", null, "session_customer_left_state")
        assertEquals("owner-cancelled-shown session=$hashedSession source=snapshot cancelReason=left_by_customer cancelReasonRaw=- shown=session_customer_left_state", known)
        assertNoRawId(known)
        assertEquals(
            "owner-cancelled-shown session=$hashedSession source=own-cancel cancelReason=unknown cancelReasonRaw=left_by_someone_else shown=session_cancelled_state",
            EvalCardLog.ownerCancelledShownLine(session, "own-cancel", "unknown", "left_by_someone_else", "session_cancelled_state")
        )
        assertEquals(
            "an Arabic text is reduced to a safe alphabet, never carried",
            "owner-cancelled-shown session=$hashedSession source=snapshot cancelReason=unknown cancelReasonRaw=?????? shown=session_cancelled_state",
            EvalCardLog.ownerCancelledShownLine(session, "snapshot", "unknown", "غادرالعميل".take(6), "session_cancelled_state")
        )
    }

    @Test
    fun `watch lines name the document and the status as known, unknown or absent - with the unknown text bounded`() {
        val start = EvalCardLog.sessionWatchStartLine(scan, session)
        assertEquals("session-watch-start scan=$hashedScan session=$hashedSession", start)
        assertNoRawId(start)
        val event = EvalCardLog.sessionWatchEventLine(session, "present", "unknown", "joined", "absent", null)
        assertEquals("session-watch-event session=$hashedSession doc=present status=unknown statusRaw=joined cancelReason=absent cancelReasonRaw=-", event)
        assertNoRawId(event)
        assertEquals(
            "session-watch-event session=$hashedSession doc=missing status=absent statusRaw=- cancelReason=absent cancelReasonRaw=-",
            EvalCardLog.sessionWatchEventLine(session, "missing", "absent", null, "absent", null)
        )
        assertEquals("session-watch-error session=$hashedSession code=permission-denied", EvalCardLog.sessionWatchErrorLine(session, "permission-denied"))
        assertEquals("session-watch-stop session=$hashedSession reason=removed", EvalCardLog.sessionWatchStopLine(session, "removed"))
    }

    @Test
    fun `session-join-failed names whether the session id was absent or blank`() {
        assertEquals("session-join-failed sessionId=absent", EvalCardLog.sessionJoinFailedLine("absent"))
        assertEquals("session-join-failed sessionId=blank", EvalCardLog.sessionJoinFailedLine("blank"))
    }

    @Test
    fun `session-create-failed names which of the two answers was absent`() {
        assertEquals("session-create-failed sessionId=absent code=present", EvalCardLog.sessionCreateFailedLine("absent", "present"))
        assertTrue(EvalCardLog.sessionCreateFailedLine("present", "absent").endsWith("code=absent"))
    }
}
