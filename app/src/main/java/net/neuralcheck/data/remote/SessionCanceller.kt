package net.neuralcheck.data.remote

import net.neuralcheck.util.EvalCardLog
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Who sends a cancellation, and which [CancelReason]s that role may send — the server enforces the pairing (`sessionNames.js:64-67`, answer 1,
 * relayed from the backend diagnosis at `bbb90a1`, not verified here); this app does not send a pair the server would refuse.
 */
enum class CancelRole(val wire: String, val allowedReasons: Set<CancelReason>) {
    OWNER("owner", setOf(CancelReason.CANCELLED_BY_OWNER)),
    CUSTOMER("customer", setOf(CancelReason.CONSENT_DECLINED, CancelReason.LEFT_BY_CUSTOMER))
}

/**
 * The one place a cancellation is sent from (batch 2, item 2): the owner's button ([CancelRole.OWNER]) and the customer leaving
 * ([CancelRole.CUSTOMER]) both call [cancel], so both print the same two log lines (`session-cancel-sent`, `session-cancel-answer`) and both
 * treat an answer that is not `status: "cancelled"` the same way - as a failure ([UnexpectedCancelAnswerException]), never as a success.
 *
 * A second cancel of a session that is already cancelled is a SUCCESS on the server (`alreadyCancelled: true`, answer 2): this returns it as
 * [CancelSessionResult.Outcome.Cancelled] like the first.
 */
@Singleton
class SessionCanceller @Inject constructor(private val service: FirebaseAnalysisService) {

    /**
     * Cancels [sessionId] for [role] with [reason]. Returns the confirmed outcome; throws the call's own exception (a refusal keeps its
     * `details.reasonCode`) or [UnexpectedCancelAnswerException]. Only the NAMED session is touched: nothing here looks at any other.
     */
    suspend fun cancel(role: CancelRole, sessionId: String, reason: CancelReason): CancelSessionResult.Outcome.Cancelled {
        require(reason in role.allowedReasons) { "role ${role.wire} may not send reason ${reason.wire}" }
        require(sessionId.isNotBlank()) { "a cancellation names a session" }
        EvalCardLog.sessionCancelSent(role.wire, reason.wire, sessionId)
        val answer = service.cancelScanSession(sessionId, reason)
        return when (val outcome = answer.outcome()) {
            is CancelSessionResult.Outcome.Cancelled -> {
                EvalCardLog.sessionCancelAnswer(
                    role = role.wire,
                    outcome = when (outcome.alreadyCancelled) {
                        false -> "cancelled"
                        true -> "already-cancelled"
                        null -> "cancelled-flag-absent"
                    },
                    status = SessionStatus.CANCELLED.wire,
                    statusRaw = null,
                    sessionId = sessionId
                )
                outcome
            }
            is CancelSessionResult.Outcome.Unexpected -> {
                EvalCardLog.sessionCancelAnswer(
                    role = role.wire,
                    outcome = "unexpected",
                    status = when (val s = outcome.status) {
                        is SessionStatus.Read.Known -> s.status.wire
                        is SessionStatus.Read.Unknown -> "unknown"
                        SessionStatus.Read.Absent -> "absent"
                    },
                    statusRaw = (outcome.status as? SessionStatus.Read.Unknown)?.raw,
                    sessionId = sessionId
                )
                throw UnexpectedCancelAnswerException(outcome.status)
            }
        }
    }
}
