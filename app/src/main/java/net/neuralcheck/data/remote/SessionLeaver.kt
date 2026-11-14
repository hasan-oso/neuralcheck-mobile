package net.neuralcheck.data.remote

import net.neuralcheck.domain.scoring.failureSaysNoConnection
import net.neuralcheck.util.EvalCardLog
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The customer device LEAVES the session it joined — batch 2, item 2, decisions D2 and D3 (user, 2026-10-15, recommendations adopted); the offline and
 * expired cases are batch 3, item 1 (user decision 4, 2026-10-16: «المغادرة بلا اتّصال: (ب) مغادرة محلّية مع سطر سجلّ، و«الجلسة منتهية» تُعدّ مغادرة ناجحة»).
 *
 * D2, the user's reason: the session is `scanning` from the join (the server writes it then), so a customer who goes back from the pre-scan screen without a
 * cancellation leaves a running session until it expires, 60 minutes after creation (relayed). Leaving therefore SENDS `left_by_customer` — from the
 * stop dialog of the scan screen (and Back there) and from the pre-scan screen's leave confirmation (the button and Back) — through the one canceller
 * ([SessionCanceller]) the owner's button uses too.
 *
 * Batch 4 (user decisions 2 and 3, 2026-10-16, adopted as written): the server answers `SESSION_NOT_PARTICIPANT`: "(ب) مغادرة محلّية مع سطر سجلّ" (below,
 * [LeaveFailureRead.NotParticipant]); and a timeout while leaving: "يبقى فشلاً، وإعادة المحاولة تُرجع «أُلغيت سابقاً» فيغادر" - the timeout (`Callable 'cancelScanSession' timed
 * out after 30000ms`, an `IOException` that names no connection phrase) is a failed leave, the request may have arrived and cancelled the session; the retry is then answered
 * `status: "cancelled", alreadyCancelled: true`, which [SessionCanceller] returns as a confirmed cancellation like the first one, so the retry leaves. No code was needed for this:
 * it is what the code below already does, and `SessionLeavePathTest` runs the two attempts.
 *
 * What a leave does, in this order, and what it does not:
 *  1. Sends the cancellation. How it ends decides the rest ([LeaveFailureRead]):
 *     - the server confirms (including `alreadyCancelled`): the leave is done;
 *     - the server answers `SESSION_EXPIRED` ([LeaveFailureRead.SessionExpired]): the session is already over, so there is nothing to cancel and the leave is
 *       DONE, with its own log line (`session-leave-expired`);
 *     - the call never reached the server ([LeaveFailureRead.NoConnection]: no `reasonCode`, and the error TEXT says there is no connection): the phone
 *       leaves LOCALLY - as in a success, steps 2-4 below - and says so in `session-leave-local reason=offline`. The server's session stays `scanning` until it
 *       expires (60 minutes after creation, relayed) or the owner cancels it; that is the cost of a local leave the user accepted;
 *     - the server answers `SESSION_NOT_PARTICIPANT` ([LeaveFailureRead.NotParticipant]; relayed from the backend diagnosis: the answer to a device it does not know as a participant
 *       of that session, for example one that joined with no identity): there is nothing of this device's to cancel, so the phone leaves LOCALLY - as in a success, steps 2-4
 *       below - and says so in `session-leave-local reason=not_participant`. No `session-refused` line and no refusal text: the leave is done;
 *     - any other failure (every other refusal of the server, and a failure whose text does not say there is no
 *       connection, a timeout included): NOTHING else happens - the exception goes to the caller, which shows the text decided by `SessionMessages.kt` and stays
 *       where it is (the scan, if any, keeps running; the holder keeps the session).
 *  2. Empties [ScanSessionHolder] — only if it still holds THAT session ([ScanSessionHolder.clearIfHolding]): a session joined after it is not touched.
 *  3. When a scan was running, removes that scan's record ([ScanSessionRecords.consume]): the scan will never be analysed.
 *  4. Prints `session-leave` with the outcome. The caller then stops the scan (scan screen) and goes back to the start of the navigation graph (D3).
 *
 * Which session: from the pre-scan screen, the one the holder holds ([sessionOfPreScan]); from the scan screen, the one the SCAN started in
 * ([sessionOfScan] - a scan keeps the session it started in, order 2026-10-14). A scan with no recorded session is a personal scan: [sessionOfScan] says null
 * and the screen does what it always did.
 */
@Singleton
class SessionLeaver @Inject constructor(
    private val canceller: SessionCanceller,
    private val sessionHolder: ScanSessionHolder,
    private val records: ScanSessionRecords
) {
    /** Where the customer leaves from; wire strings are explicit (R8-safe). */
    enum class Phase(val wire: String) {
        PRE_SCAN("pre-scan"),
        SCAN("scan"),

        /** A-P6-12: the consent screen, between the join and the pre-scan screen. */
        CONSENT("consent")
    }

    /** The session a leave from the pre-scan screen cancels: the one the holder holds now, or null (the device is in no session). */
    fun sessionOfPreScan(): String? = sessionHolder.get()?.takeIf { it.isNotBlank() }

    /** The session a leave from the scan screen cancels: the one [scanId] started in, or null (a personal scan). Prints the records' own lines. */
    fun sessionOfScan(scanId: String): String? = records.sessionFor(scanId, ScanSessionRecords.Use.LEAVE)?.sessionId

    /**
     * Leaves [sessionId] from [phase]; [scanId] is the running scan, when there is one. Returns normally when the leave is done (confirmed, expired, or
     * local for lack of a connection or because the server does not know the device as a participant - each printed). Throws what the cancellation threw for every OTHER failure (a refusal keeps its `details.reasonCode`) -
     * after printing `session-leave result=failed`.
     */
    suspend fun leave(phase: Phase, sessionId: String, scanId: String? = null) =
        end(CancelReason.LEFT_BY_CUSTOMER, ScanSessionHolder.ClearReason.LEFT_BY_CUSTOMER, phase, sessionId, scanId)

    /**
     * The customer pressed «لا أوافق» on the consent screen (A-P6-12; contract A8 (4), PLAN-06:1394: «الجلسة تُلغى، ولا يُرسل معرّف»): the same cancellation as a leave - the call that has existed since batch 9 - with
     * the reason `consent_declined` instead of `left_by_customer`, sent by the JOINED device only ([CancelRole.CUSTOMER]). Nothing but `{sessionId, reason}` is sent. The rest is a leave's, as written above: confirmed, expired,
     * no connection and `SESSION_NOT_PARTICIPANT` all end it (each printed, with `phase=consent`); every other failure reaches the caller and nothing else happens. A DECISION of this row, not the plan's: the plan says
     * nothing of a decline that cannot reach the server; it is treated like a leave that cannot (the phone leaves, locally, and says so) because the one thing a refusal must always allow is to refuse. Nothing was recorded
     * on the server for a decline: `declined` is written to no server document [A8 (4)] - the refusal is counted from the session's `cancelReason` alone.
     */
    suspend fun declineConsent(sessionId: String) =
        end(CancelReason.CONSENT_DECLINED, ScanSessionHolder.ClearReason.CONSENT_DECLINED, Phase.CONSENT, sessionId, scanId = null)

    private suspend fun end(reason: CancelReason, clearReason: ScanSessionHolder.ClearReason, phase: Phase, sessionId: String, scanId: String?) {
        try {
            canceller.cancel(CancelRole.CUSTOMER, sessionId, reason)
        } catch (ce: kotlinx.coroutines.CancellationException) {
            throw ce
        } catch (t: Throwable) {
            when (LeaveFailureRead.of(t)) {
                LeaveFailureRead.SessionExpired -> EvalCardLog.sessionLeaveExpired(CancelRole.CUSTOMER.wire, phase.wire, sessionId)
                LeaveFailureRead.NoConnection ->
                    EvalCardLog.sessionLeaveLocal(CancelRole.CUSTOMER.wire, phase.wire, reason = LOCAL_REASON_OFFLINE, sessionId = sessionId)
                LeaveFailureRead.NotParticipant ->
                    EvalCardLog.sessionLeaveLocal(CancelRole.CUSTOMER.wire, phase.wire, reason = LOCAL_REASON_NOT_PARTICIPANT, sessionId = sessionId)
                LeaveFailureRead.Refused -> {
                    EvalCardLog.sessionLeave(CancelRole.CUSTOMER.wire, phase.wire, result = "failed", sessionId = sessionId, holder = "n/a")
                    throw t
                }
            }
        }
        val holderResult = sessionHolder.clearIfHolding(sessionId, clearReason)
        if (!scanId.isNullOrBlank()) records.consume(scanId)
        EvalCardLog.sessionLeave(CancelRole.CUSTOMER.wire, phase.wire, result = "left", sessionId = sessionId, holder = holderResult.wire)
    }

    private companion object {
        /** The `reason=` of `session-leave-local` when the cancellation could not reach the server. */
        const val LOCAL_REASON_OFFLINE = "offline"

        /** The `reason=` of `session-leave-local` when the server answered `SESSION_NOT_PARTICIPANT` (batch 4, item 2). */
        const val LOCAL_REASON_NOT_PARTICIPANT = "not_participant"
    }
}

/**
 * How a FAILED cancellation reads, for the customer who is leaving (batch 3, item 1, user decision 4). Four named states (batch 4, item 2 added [NotParticipant]), one `when` over them in
 * [SessionLeaver.leave]; every failure is exactly one of them.
 *
 * What counts as «no connection» is [failureSaysNoConnection] - the ONE reading of it (batch 4, item 1; it was built here in batch 3 and is now what the other five
 * places use too: the typed join, the QR join (batch 5, item 1), the owner's cancel, the evaluation submission and the analysis call; rule 24). It reads the error TEXT by the phrases of the app's
 * connection text, in the failure's message and in its `cause` chain, and never for a failure that carries a server `reasonCode` (known or not): the server
 * answered, so the phone is not offline, whatever the refusal's text says. The reasons are in that function's doc (rule 21; the SDK's own wrapping, measured from
 * firebase-functions 20.4.0).
 *
 * NOT counted as no connection (so the failure goes to the caller and the leave does not happen): a timeout (`Callable 'cancelScanSession' timed out`, and the
 * SDK's `DEADLINE_EXCEEDED` for a socket READ timeout) - the request may have reached the server; an `IOException` whose text names none of the phrases (a reset
 * connection, a TLS failure); the server's own `internal`; an answer that is not `status: "cancelled"` ([UnexpectedCancelAnswerException]).
 * Counted as no connection, and so a local leave (batch 5, item 2; user decision 2, 2026-10-16): a socket CONNECT timeout, whose cause's text is `failed to connect to ... after Nms` -
 * the SDK wraps it as `DEADLINE_EXCEEDED` too, and the text decides, not the code.
 */
internal sealed interface LeaveFailureRead {
    /** The server answered `SESSION_EXPIRED`: the session is over; there is nothing to cancel. */
    data object SessionExpired : LeaveFailureRead

    /** No answer from the server and the error text says there is no connection. */
    data object NoConnection : LeaveFailureRead

    /**
     * The server answered `SESSION_NOT_PARTICIPANT` (batch 4, item 2): it does not know this device as a participant of the session, so there is nothing of this device's to cancel.
     * Decided by the `reasonCode` alone, never by the Functions code or the message (rule 21): the same code arrives under different statuses and texts.
     */
    data object NotParticipant : LeaveFailureRead

    /** Everything else: the failure reaches the caller unchanged. */
    data object Refused : LeaveFailureRead

    companion object {
        fun of(t: Throwable): LeaveFailureRead = when (val read = ReasonCodeRead.of(t)) {
            is ReasonCodeRead.Known -> when (read.code) {
                SessionErrorCode.SESSION_EXPIRED -> SessionExpired
                SessionErrorCode.SESSION_NOT_PARTICIPANT -> NotParticipant
                else -> Refused
            }
            is ReasonCodeRead.Unrecognized -> Refused
            ReasonCodeRead.Absent -> if (failureSaysNoConnection(t)) NoConnection else Refused
        }
    }
}
