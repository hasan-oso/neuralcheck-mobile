package net.neuralcheck.data.remote

import com.google.firebase.functions.FirebaseFunctionsException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * The session contract the app reads (batch 2, order 2026-10-15, item 2): the wire values, and how an answer or an exception reads. The expected
 * values are written out here from Appendix A of the order (the backend diagnosis at neuralcheck-backend `bbb90a1`, relayed, not verified from this
 * repository) — a golden pin: an enum that drifts from the server's spelling fails here, not on a customer's phone.
 *
 * Shapes the real SDK parser cannot make (a `details` that is not a map, a `reasonCode` that is not a text) are fed through a mocked exception: the
 * reader must name them, not crash and not read them as a known code (rules 22, 25). The shapes the SDK DOES make are tested through the SDK's own
 * parser in `SessionJoinRefusalPathTest` and `SessionCancelPathTest`.
 */
class SessionContractTest {

    private fun exceptionWithDetails(details: Any?): FirebaseFunctionsException {
        val e = mock<FirebaseFunctionsException>()
        whenever(e.details).thenReturn(details)
        return e
    }

    @Test
    fun `the five statuses are exactly the five the server writes - no waiting, no joined, no in_progress, no done`() {
        assertEquals(
            listOf("pending", "scanning", "completed", "expired", "cancelled"),
            SessionStatus.values().map { it.wire }
        )
        for (notWritten in listOf("waiting", "joined", "in_progress", "done")) {
            assertEquals(notWritten, SessionStatus.Read.Unknown(notWritten), SessionStatus.read(notWritten))
        }
    }

    @Test
    fun `a status reads as known, unknown or absent - and an unknown is never one of the five`() {
        for (s in SessionStatus.values()) assertEquals(SessionStatus.Read.Known(s), SessionStatus.read(s.wire))
        assertEquals("exact match: the server writes lower case", SessionStatus.Read.Unknown("Scanning"), SessionStatus.read("Scanning"))
        assertEquals(SessionStatus.Read.Unknown(""), SessionStatus.read(""))
        assertEquals(SessionStatus.Read.Absent, SessionStatus.read(null))
        assertEquals("not a text", SessionStatus.Read.Absent, SessionStatus.read(5))
        assertEquals("not a text", SessionStatus.Read.Absent, SessionStatus.read(true))
    }

    @Test
    fun `the three cancel reasons are exactly the three the server accepts, and each role may send only its own`() {
        assertEquals(listOf("consent_declined", "left_by_customer", "cancelled_by_owner"), CancelReason.values().map { it.wire })
        assertEquals(setOf(CancelReason.CANCELLED_BY_OWNER), CancelRole.OWNER.allowedReasons)
        assertEquals(setOf(CancelReason.CONSENT_DECLINED, CancelReason.LEFT_BY_CUSTOMER), CancelRole.CUSTOMER.allowedReasons)
        assertEquals(CancelReason.Read.Known(CancelReason.LEFT_BY_CUSTOMER), CancelReason.read("left_by_customer"))
        assertEquals(CancelReason.Read.Unknown("something_else"), CancelReason.read("something_else"))
        assertEquals(CancelReason.Read.Absent, CancelReason.read(null))
    }

    /**
     * A-SUB wave 1 (order 2026-10-17) REPLACES this golden pin (rule 23): it listed the fifteen codes of Appendix A and the join's seven, and would fail the day the server's new codes
     * arrived. Now: the fifteen of Appendix A still, plus SESSION_NOT_STARTED and the five scan refusals (RELAYED from neuralcheck-backend PROTOCOL_CONTRACT.md 6-d-1 and 10-j at 47ec36e,
     * not verified from this repository); the join throws SHOP_SUBSCRIPTION_EXPIRED too; createScanSession throws the two shop refusals; the analysis throws the five and the session states.
     * A-P6-03 (order 2026-10-18) REPLACES it again (rule 23): plus PURPOSE_SESSION_NOT_PENDING, the one refusal of the new setSessionPurpose call (contract A2, relayed copy of neuralcheck-backend@8e5c426).
     * The order of 2026-10-20 (app-engineer-5) REPLACES it once more (rule 23): plus SCAN_REPORT_NOT_AVAILABLE (the analysis' neutral stored-report refusal on the account's own scan) and SHOP_PLAN_REQUIRED
     * (createScanSession's plan refusal) - both relayed from neuralcheck-backend@05275ba, not verified from this repository. SESSION_REPORT_NOT_AVAILABLE is deliberately NOT here (the order: it exists and stays as it is).
     * The order of 2026-10-22 (maintenance before/after) REPLACES it once more (rule 23): plus DEFECT_TYPE_REQUIRED, DEFECT_TYPE_INVALID and DEFECT_TYPE_NOT_APPLICABLE, the three refusals of
     * setSessionPurpose's fault type (contract A2 addendum, copy of neuralcheck-backend@f3c1bab, compared with the copy's table by MaintenancePairContractCopyTest), and SET_PURPOSE_INVALID, the refusal of a purpose
     * that is none of the three (named in the contract's text and carried by the server's reply samples: found by MaintenancePairWireSamplesTest) - four codes in all.
     * The decision Q31 of 2026-10-22 REPLACES it once more (rule 23): plus PURPOSE_PART_NOT_ENABLED, the refusal of a purpose the shop's parts do not allow (contract A2, copy @f3c1bab; carried by the
     * server's reply samples) - one code more in the set.
     */
    @Test
    fun `the reasonCodes are those of Appendix A plus the A-SUB ones, and which call throws which`() {
        assertEquals(
            setOf(
                "SESSION_CODE_INVALID", "SESSION_ALREADY_JOINED", "SESSION_CANCELLED", "SESSION_COMPLETED", "SESSION_EXPIRED",
                "SESSION_ANALYSIS_IN_FLIGHT", "SESSION_EXPIRES_AT_INVALID", "SESSION_STATUS_UNKNOWN", "SESSION_NOT_PARTICIPANT",
                "SESSION_CLAIM_AGE_UNKNOWN", "CANCEL_NOT_AUTHENTICATED", "CANCEL_SESSION_ID_REQUIRED", "CANCEL_REASON_REQUIRED",
                "CANCEL_REASON_INVALID", "CANCEL_REASON_NOT_ALLOWED_FOR_ROLE",
                "SESSION_NOT_STARTED", "NO_CREDIT", "SUBSCRIPTION_EXPIRED", "FREE_DEVICE_USED", "SHOP_NO_CREDIT", "SHOP_SUBSCRIPTION_EXPIRED",
                "PURPOSE_SESSION_NOT_PENDING", "DEFECT_TYPE_REQUIRED", "DEFECT_TYPE_INVALID", "DEFECT_TYPE_NOT_APPLICABLE", "SET_PURPOSE_INVALID", "PURPOSE_PART_NOT_ENABLED",
                "SCAN_REPORT_NOT_AVAILABLE", "SHOP_PLAN_REQUIRED"
            ),
            SessionErrorCode.values().map { it.wire }.toSet()
        )
        // table (a): the join throws five of the six named codes (not SESSION_ANALYSIS_IN_FLIGHT), adds two, and since A-SUB the shop's expiry
        assertEquals(
            setOf(
                "SESSION_CODE_INVALID", "SESSION_ALREADY_JOINED", "SESSION_CANCELLED", "SESSION_COMPLETED", "SESSION_EXPIRED", "SESSION_EXPIRES_AT_INVALID", "SESSION_STATUS_UNKNOWN",
                "SHOP_SUBSCRIPTION_EXPIRED"
            ),
            SessionErrorCode.joinCodes.map { it.wire }.toSet()
        )
        // table (b): the cancel throws twelve (unchanged by A-SUB)
        assertEquals(
            setOf(
                "CANCEL_NOT_AUTHENTICATED", "CANCEL_SESSION_ID_REQUIRED", "CANCEL_REASON_REQUIRED", "CANCEL_REASON_INVALID", "SESSION_NOT_PARTICIPANT",
                "CANCEL_REASON_NOT_ALLOWED_FOR_ROLE", "SESSION_COMPLETED", "SESSION_EXPIRED", "SESSION_EXPIRES_AT_INVALID", "SESSION_STATUS_UNKNOWN",
                "SESSION_ANALYSIS_IN_FLIGHT", "SESSION_CLAIM_AGE_UNKNOWN"
            ),
            SessionErrorCode.cancelCodes.map { it.wire }.toSet()
        )
        // createScanSession since A-SUB: its two refusals carry the shop's reason; since 2026-10-20 the plan refusal too
        assertEquals(setOf("SHOP_NO_CREDIT", "SHOP_SUBSCRIPTION_EXPIRED", "SHOP_PLAN_REQUIRED"), SessionErrorCode.createCodes.map { it.wire }.toSet())
        assertTrue(
            "SHOP_PLAN_REQUIRED is the create's alone",
            SessionErrorCode.SHOP_PLAN_REQUIRED !in SessionErrorCode.joinCodes + SessionErrorCode.cancelCodes + SessionErrorCode.analyzeCodes + SessionErrorCode.purposeCodes
        )
        assertTrue(
            "SCAN_REPORT_NOT_AVAILABLE is the analysis' alone (the account's own scan)",
            SessionErrorCode.SCAN_REPORT_NOT_AVAILABLE in SessionErrorCode.analyzeCodes &&
                SessionErrorCode.SCAN_REPORT_NOT_AVAILABLE !in SessionErrorCode.joinCodes + SessionErrorCode.cancelCodes + SessionErrorCode.createCodes + SessionErrorCode.purposeCodes
        )
        assertEquals("the wire strings are the server's, spelled out", listOf("SCAN_REPORT_NOT_AVAILABLE", "SHOP_PLAN_REQUIRED"), listOf(SessionErrorCode.SCAN_REPORT_NOT_AVAILABLE.wire, SessionErrorCode.SHOP_PLAN_REQUIRED.wire))
        // setSessionPurpose (A-P6-03; since 2026-10-22 the three of the fault type; and, decision Q31, the one of a shop without the part): its refusals, and no other call throws them
        assertEquals(
            setOf("PURPOSE_SESSION_NOT_PENDING", "DEFECT_TYPE_REQUIRED", "DEFECT_TYPE_INVALID", "DEFECT_TYPE_NOT_APPLICABLE", "SET_PURPOSE_INVALID", "PURPOSE_PART_NOT_ENABLED"),
            SessionErrorCode.purposeCodes.map { it.wire }.toSet()
        )
        for (code in SessionErrorCode.purposeCodes) {
            assertTrue(code.wire, code !in SessionErrorCode.joinCodes + SessionErrorCode.cancelCodes + SessionErrorCode.createCodes + SessionErrorCode.analyzeCodes)
        }
        assertTrue("SESSION_ANALYSIS_IN_FLIGHT is the cancel's alone", SessionErrorCode.SESSION_ANALYSIS_IN_FLIGHT !in SessionErrorCode.joinCodes)
        assertTrue(
            "SESSION_NOT_STARTED is the analysis' alone",
            SessionErrorCode.SESSION_NOT_STARTED !in SessionErrorCode.joinCodes + SessionErrorCode.cancelCodes + SessionErrorCode.createCodes
        )
    }

    @Test
    fun `a reasonCode reads from details reasonCode and nowhere else`() {
        for (code in SessionErrorCode.values()) {
            assertEquals(code.wire, ReasonCodeRead.Known(code), ReasonCodeRead.of(exceptionWithDetails(mapOf("reasonCode" to code.wire))))
        }
        assertEquals(ReasonCodeRead.Unrecognized("NEW_ONE"), ReasonCodeRead.of(exceptionWithDetails(mapOf("reasonCode" to "NEW_ONE"))))
        assertEquals("exact match", ReasonCodeRead.Unrecognized("session_completed"), ReasonCodeRead.of(exceptionWithDetails(mapOf("reasonCode" to "session_completed"))))
    }

    @Test
    fun `an exception that says nothing about a reason reads as absent - never as a known code`() {
        assertEquals("no details", ReasonCodeRead.Absent, ReasonCodeRead.of(exceptionWithDetails(null)))
        assertEquals("details is not a map", ReasonCodeRead.Absent, ReasonCodeRead.of(exceptionWithDetails("SESSION_COMPLETED")))
        assertEquals("a map without the key", ReasonCodeRead.Absent, ReasonCodeRead.of(exceptionWithDetails(mapOf("code" to "SESSION_COMPLETED"))))
        assertEquals("the key holds a null", ReasonCodeRead.Absent, ReasonCodeRead.of(exceptionWithDetails(mapOf("reasonCode" to null))))
        assertEquals("the key holds a number", ReasonCodeRead.Absent, ReasonCodeRead.of(exceptionWithDetails(mapOf("reasonCode" to 7))))
        assertEquals("not a Functions error at all", ReasonCodeRead.Absent, ReasonCodeRead.of(java.io.IOException("SESSION_COMPLETED")))
        assertEquals("not a Functions error at all", ReasonCodeRead.Absent, ReasonCodeRead.of(RuntimeException("SESSION_COMPLETED")))
    }

    @Test
    fun `the message of an exception is never read as a reason`() {
        val e = mock<FirebaseFunctionsException>()
        whenever(e.message).thenReturn("SESSION_COMPLETED")
        whenever(e.details).thenReturn(null)
        assertEquals(ReasonCodeRead.Absent, ReasonCodeRead.of(e))
    }

    @Test
    fun `a cancel answer is a cancellation only when its status is cancelled`() {
        assertEquals(
            CancelSessionResult.Outcome.Cancelled(alreadyCancelled = false),
            CancelSessionResult("s", "cancelled", false).outcome()
        )
        assertEquals(
            "a second cancel is a success on the server (alreadyCancelled: true)",
            CancelSessionResult.Outcome.Cancelled(alreadyCancelled = true),
            CancelSessionResult("s", "cancelled", true).outcome()
        )
        assertEquals(
            "the flag may be missing: kept absent, still a cancellation",
            CancelSessionResult.Outcome.Cancelled(alreadyCancelled = null),
            CancelSessionResult("s", "cancelled", null).outcome()
        )
        for (status in SessionStatus.values().filter { it != SessionStatus.CANCELLED }) {
            assertEquals(
                CancelSessionResult.Outcome.Unexpected(SessionStatus.Read.Known(status)),
                CancelSessionResult("s", status.wire, false).outcome()
            )
        }
        assertEquals(CancelSessionResult.Outcome.Unexpected(SessionStatus.Read.Absent), CancelSessionResult().outcome())
        assertEquals(CancelSessionResult.Outcome.Unexpected(SessionStatus.Read.Unknown("done")), CancelSessionResult("s", "done", true).outcome())
    }
}
