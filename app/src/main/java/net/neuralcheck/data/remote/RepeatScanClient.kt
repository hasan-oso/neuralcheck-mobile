package net.neuralcheck.data.remote

import kotlinx.coroutines.CancellationException
import net.neuralcheck.domain.repeat.RepeatDecision
import net.neuralcheck.domain.repeat.RepeatOfferRead
import net.neuralcheck.domain.repeat.RepeatRefusal
import net.neuralcheck.domain.repeat.RepeatStartRead
import net.neuralcheck.domain.repeat.decide
import net.neuralcheck.util.EvalCardLog

/**
 * Thrown by [FirebaseAnalysisService] when one of the repeat scan's two callables is asked for while `REPEAT_TEXTS_APPROVED` is false: nothing was sent. Our own text, no server text in it.
 */
class RepeatCallsNotApprovedException(callable: String) :
    IllegalStateException("repeat scan call '$callable' not sent: the repeat screens' texts are not approved (REPEAT_TEXTS_APPROVED is false)")

/**
 * The repeat scan's two calls (A-P6-07; see [net.neuralcheck.domain.repeat.RepeatScanWire]) as outcomes, never as exceptions: both devices call it - the technician's phone (the session's owner) to learn that its
 * scanned phone was chosen for a repeat, the scanned phone to learn the reference temperature and to start the child session. Every way a call can end is one outcome, and each prints one `repeat-scan` line
 * (booleans, hashed ids and fixed tokens only). A refusal is classified by `details.reasonCode` alone (rule 21: not by the Functions code or the message).
 *
 * Nothing is sent while the repeat screens' texts are not approved: that is [FirebaseAnalysisService]'s gate (rule 35, at the point the request leaves the phone), and the answer to it here is [OfferOutcome.GuardClosed] /
 * [StartOutcome.GuardClosed] with NO log line - this runs on every session in production today and must be silent then.
 */
class RepeatScanClient(private val service: FirebaseAnalysisService) {

    enum class Role(val wire: String) {
        /** The technician's phone: the session's owner. */
        OWNER("owner"),

        /** The scanned phone: the account that joined the session. */
        SCANNED("scanned")
    }

    sealed interface OfferOutcome {
        /** The repeat screens' texts are not approved: the call was not made (and not logged). */
        data object GuardClosed : OfferOutcome

        /** The server answered; [decision] is what the answer means ([net.neuralcheck.domain.repeat.decide]). */
        data class Answered(val decision: RepeatDecision) : OfferOutcome

        /** The server refused: [reasonCode] is the token of `details.reasonCode`; [refusal] is the contract's name for it, null when it is none of the twelve. */
        data class Refused(val reasonCode: String, val refusal: RepeatRefusal?) : OfferOutcome

        /** No `reasonCode`: a disconnect, a timeout, no signed-in user, any other failure. */
        data object NotSent : OfferOutcome
    }

    sealed interface StartOutcome {
        data object GuardClosed : StartOutcome

        /** The child session exists: [childSessionId] is the one the scan must run in; [expiresInMinutes] is the server's lifetime or null when the answer named none. */
        data class Started(val childSessionId: String, val expiresInMinutes: Int?) : StartOutcome

        data class Refused(val reasonCode: String, val refusal: RepeatRefusal?) : StartOutcome
        data object NotSent : StartOutcome

        /** The call succeeded but its answer is not a start ([why]: a fixed token). */
        data class UnreadableAnswer(val why: String) : StartOutcome
    }

    /** Asks for the offer of the pair ([sessionId], [scanId]). Never throws (cancellation aside). */
    suspend fun offer(sessionId: String, scanId: String, role: Role): OfferOutcome {
        val outcome = try {
            OfferOutcome.Answered(decide(RepeatOfferRead.of(service.getRepeatScanOffer(sessionId, scanId), sessionId, scanId)))
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: RepeatCallsNotApprovedException) {
            return OfferOutcome.GuardClosed
        } catch (t: Throwable) {
            when (val read = ReasonCodeRead.of(t)) {
                is ReasonCodeRead.Known -> OfferOutcome.Refused(read.code.wire, null)
                is ReasonCodeRead.Unrecognized -> OfferOutcome.Refused(read.raw, RepeatRefusal.ofWire(read.raw))
                ReasonCodeRead.Absent -> OfferOutcome.NotSent
            }
        }
        val (name, detail) = when (outcome) {
            OfferOutcome.GuardClosed -> return outcome
            is OfferOutcome.Answered -> "answered" to outcome.decision.token
            is OfferOutcome.Refused -> "refused" to outcome.reasonCode
            OfferOutcome.NotSent -> "not_sent" to "-"
        }
        EvalCardLog.repeatScan(CALL_OFFER, role.wire, sessionId, name, detail)
        return outcome
    }

    /** Asks the server to start the child session of the pair ([sessionId], [scanId]); [startedBeforeCooling] is `true` only when the technician pressed «ابدأ رغم ذلك», else null. Never throws (cancellation aside). */
    suspend fun start(sessionId: String, scanId: String, startedBeforeCooling: Boolean?, role: Role): StartOutcome {
        val outcome = try {
            when (val read = RepeatStartRead.of(service.startRepeatScan(sessionId, scanId, startedBeforeCooling), sessionId)) {
                is RepeatStartRead.Started -> StartOutcome.Started(read.childSessionId, read.expiresInMinutes)
                is RepeatStartRead.Unreadable -> StartOutcome.UnreadableAnswer(read.why)
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: RepeatCallsNotApprovedException) {
            return StartOutcome.GuardClosed
        } catch (t: Throwable) {
            when (val read = ReasonCodeRead.of(t)) {
                is ReasonCodeRead.Known -> StartOutcome.Refused(read.code.wire, null)
                is ReasonCodeRead.Unrecognized -> StartOutcome.Refused(read.raw, RepeatRefusal.ofWire(read.raw))
                ReasonCodeRead.Absent -> StartOutcome.NotSent
            }
        }
        val (name, detail) = when (outcome) {
            StartOutcome.GuardClosed -> return outcome
            is StartOutcome.Started -> "started" to (if (startedBeforeCooling == true) "before_cooling" else "cooled")
            is StartOutcome.Refused -> "refused" to outcome.reasonCode
            StartOutcome.NotSent -> "not_sent" to "-"
            is StartOutcome.UnreadableAnswer -> "unreadable_answer" to outcome.why
        }
        EvalCardLog.repeatScan(CALL_START, role.wire, sessionId, name, detail)
        return outcome
    }

    private companion object {
        const val CALL_OFFER = "offer"
        const val CALL_START = "start"
    }
}
