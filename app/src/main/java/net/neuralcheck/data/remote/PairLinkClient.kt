package net.neuralcheck.data.remote

import kotlinx.coroutines.CancellationException
import net.neuralcheck.domain.pair.ConfirmAnswerRead
import net.neuralcheck.domain.pair.OpenBeforesRead
import net.neuralcheck.domain.pair.PairRefusal
import net.neuralcheck.util.EvalCardLog

/**
 * Thrown by [FirebaseAnalysisService] when one of the maintenance link's two callables is asked for while `PAIR_LIST_TEXTS_APPROVED` is false: nothing was sent. Our own text, no server text in it.
 */
class PairCallsNotApprovedException(callable: String) :
    IllegalStateException("maintenance link call '$callable' not sent: the pair list's texts are not approved (PAIR_LIST_TEXTS_APPROVED is false)")

/**
 * The maintenance link's two calls (see [net.neuralcheck.domain.pair.PairLinkWire]) as outcomes, never as exceptions: the technician's phone (the owner of the «after» session) asks which «before» scans it may link and
 * confirms its choice. Every way a call can end is one outcome, and each prints one `pair-link` line (hashed ids and fixed tokens only; the line carries the `reasonCode` of every refusal, named or not, and the
 * keys of the answer that are none of the contract's). A refusal is classified by `details.reasonCode` alone (rule 21: not by the Functions code or the message).
 *
 * Nothing is sent while the pair list's texts are not approved: that is [FirebaseAnalysisService]'s gate (rule 35, at the point the request leaves the phone), and the answer to it here is [ListOutcome.GuardClosed] /
 * [ConfirmOutcome.GuardClosed] with NO log line - this runs on every session in production today and must be silent then.
 */
class PairLinkClient(private val service: FirebaseAnalysisService) {

    sealed interface ListOutcome {
        /** The pair list's texts are not approved: the call was not made (and not logged). */
        data object GuardClosed : ListOutcome

        /** The server answered and the answer reads: [read] has the candidates (possibly none). */
        data class Listed(val read: OpenBeforesRead.Read) : ListOutcome

        /** The call succeeded but its answer does not read ([why]: a fixed token). */
        data class UnreadableAnswer(val why: String) : ListOutcome

        /** The server refused: [reasonCode] is the token of `details.reasonCode`; [refusal] is the contract's name for it, null when it is none of the seventeen (a session-contract code or one this app has no case for). */
        data class Refused(val reasonCode: String, val refusal: PairRefusal?) : ListOutcome

        /** No `reasonCode`: a disconnect, a timeout, no signed-in user, any other failure. */
        data object NotSent : ListOutcome
    }

    sealed interface ConfirmOutcome {
        data object GuardClosed : ConfirmOutcome

        /** The server confirmed the choice. */
        data object Confirmed : ConfirmOutcome

        /** The call succeeded but its answer is not the confirmation of this request ([why]: a fixed token). */
        data class UnreadableAnswer(val why: String) : ConfirmOutcome

        data class Refused(val reasonCode: String, val refusal: PairRefusal?) : ConfirmOutcome
        data object NotSent : ConfirmOutcome
    }

    /** Asks for the open «before» scans of the «after» session [sessionId]. Never throws (cancellation aside). */
    suspend fun listOpen(sessionId: String): ListOutcome {
        val outcome = try {
            when (val read = OpenBeforesRead.of(service.listOpenMaintenanceBefores(sessionId))) {
                is OpenBeforesRead.Read -> ListOutcome.Listed(read)
                is OpenBeforesRead.Unreadable -> ListOutcome.UnreadableAnswer(read.why)
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: PairCallsNotApprovedException) {
            return ListOutcome.GuardClosed
        } catch (t: Throwable) {
            when (val read = ReasonCodeRead.of(t)) {
                is ReasonCodeRead.Known -> ListOutcome.Refused(read.code.wire, null)
                is ReasonCodeRead.Unrecognized -> ListOutcome.Refused(read.raw, PairRefusal.ofWire(read.raw))
                ReasonCodeRead.Absent -> ListOutcome.NotSent
            }
        }
        val (name, detail, unknown) = when (outcome) {
            ListOutcome.GuardClosed -> return outcome
            is ListOutcome.Listed -> Triple("listed", "n${outcome.read.candidates.size}_v${outcome.read.validityHours}", outcome.read.unknownKeys + outcome.read.candidateUnknownKeys)
            is ListOutcome.UnreadableAnswer -> Triple("unreadable_answer", outcome.why, emptyList<String>())
            is ListOutcome.Refused -> Triple("refused", outcome.reasonCode, emptyList<String>())
            ListOutcome.NotSent -> Triple("not_sent", "-", emptyList<String>())
        }
        EvalCardLog.pairLink(CALL_LIST, sessionId, name, detail, unknown)
        return outcome
    }

    /** Confirms the choice of the «before» scan [beforeScanId] for the «after» session [sessionId]. Never throws (cancellation aside). */
    suspend fun confirm(sessionId: String, beforeScanId: String): ConfirmOutcome {
        val outcome = try {
            when (val read = ConfirmAnswerRead.of(service.confirmMaintenanceAfterLink(sessionId, beforeScanId), sessionId, beforeScanId)) {
                ConfirmAnswerRead.Confirmed -> ConfirmOutcome.Confirmed
                is ConfirmAnswerRead.Unreadable -> ConfirmOutcome.UnreadableAnswer(read.why)
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: PairCallsNotApprovedException) {
            return ConfirmOutcome.GuardClosed
        } catch (t: Throwable) {
            when (val read = ReasonCodeRead.of(t)) {
                is ReasonCodeRead.Known -> ConfirmOutcome.Refused(read.code.wire, null)
                is ReasonCodeRead.Unrecognized -> ConfirmOutcome.Refused(read.raw, PairRefusal.ofWire(read.raw))
                ReasonCodeRead.Absent -> ConfirmOutcome.NotSent
            }
        }
        val (name, detail) = when (outcome) {
            ConfirmOutcome.GuardClosed -> return outcome
            ConfirmOutcome.Confirmed -> "confirmed" to "-"
            is ConfirmOutcome.UnreadableAnswer -> "unreadable_answer" to outcome.why
            is ConfirmOutcome.Refused -> "refused" to outcome.reasonCode
            ConfirmOutcome.NotSent -> "not_sent" to "-"
        }
        EvalCardLog.pairLink(CALL_CONFIRM, sessionId, name, detail, emptyList<String>())
        return outcome
    }

    private companion object {
        const val CALL_LIST = "list"
        const val CALL_CONFIRM = "confirm"
    }
}
