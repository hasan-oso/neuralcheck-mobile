package net.neuralcheck.data.remote

import kotlinx.coroutines.CancellationException
import net.neuralcheck.domain.consent.ConsentAnswerRead
import net.neuralcheck.domain.consent.ConsentLocale
import net.neuralcheck.domain.consent.ConsentRefusal
import net.neuralcheck.domain.consent.ConsentTextVersion
import net.neuralcheck.util.EvalCardLog

/**
 * Thrown by [FirebaseAnalysisService] when `recordScanConsent` is asked for while `CONSENT_TEXTS_APPROVED` is false: nothing was sent. Our own text, no server text in it.
 */
class ConsentCallsNotApprovedException(callable: String) :
    IllegalStateException("consent call '$callable' not sent: the consent screen's texts are not approved (CONSENT_TEXTS_APPROVED is false)")

/**
 * The consent screen's one call (A-P6-12; see [net.neuralcheck.domain.consent.ConsentWire]) as an outcome, never as an exception: the JOINED device records that its user accepted the text of version
 * [ConsentTextVersion.CURRENT] in [ConsentLocale]. Every way the call can end is one outcome, and each prints one `consent-record` line (booleans, a hashed id and fixed tokens only - never the text, never
 * an identifier of the device). A refusal is classified by `details.reasonCode` alone (rule 21: not by the Functions code or the message).
 *
 * The version is the app's own constant ([ConsentTextVersion.CURRENT]) and the language is the one the screen was drawn in: the caller passes it, nothing here chooses either (rule 22: no default).
 * Declining is not a call of this class: it is the session's cancellation with the reason `consent_declined` ([SessionLeaver.declineConsent]).
 *
 * Nothing is sent while the consent screen's texts are not approved: that is [FirebaseAnalysisService]'s gate (rule 35, at the point the request leaves the phone), and the answer to it here is
 * [RecordOutcome.GuardClosed] with NO log line - the server's half is not deployed on production, and this must be silent until the guard is true.
 */
class ScanConsentClient(private val service: FirebaseAnalysisService) {

    sealed interface RecordOutcome {
        /** The consent screen's texts are not approved: the call was not made (and not logged). */
        data object GuardClosed : RecordOutcome

        /**
         * The server says the consent is recorded. [storedTextVersion] and [storedLocale] are the STORED values; [alreadyRecorded] is the server's flag (null when the answer carried none); [differs] is true
         * when the stored version or language is not the one this call sent (a second call answers the first's: the evidence is that the user agreed to what he read then).
         */
        data class Recorded(val storedTextVersion: Int, val storedLocale: ConsentLocale, val alreadyRecorded: Boolean?, val differs: Boolean) : RecordOutcome

        /** The server refused: [reasonCode] is the token of `details.reasonCode`; [refusal] is the contract's name for it, null when it is none of the eight `CONSENT_*` (the four session codes included). */
        data class Refused(val reasonCode: String, val refusal: ConsentRefusal?) : RecordOutcome

        /** No `reasonCode`: a disconnect, a timeout, any other failure. NOT a recorded consent. */
        data object NotSent : RecordOutcome

        /** The call succeeded but its answer is not a recorded consent ([why]: a fixed token). NOT a recorded consent (PLAN-06:1402). */
        data class UnreadableAnswer(val why: String) : RecordOutcome
    }

    /** Records the consent of [sessionId] in [locale], version [ConsentTextVersion.CURRENT]. Never throws (cancellation aside). */
    suspend fun record(sessionId: String, locale: ConsentLocale): RecordOutcome {
        val outcome = try {
            when (val read = ConsentAnswerRead.of(service.recordScanConsent(sessionId, ConsentTextVersion.CURRENT, locale), sessionId)) {
                is ConsentAnswerRead.Accepted ->
                    RecordOutcome.Recorded(read.storedTextVersion, read.storedLocale, read.alreadyRecorded, differs = read.storedTextVersion != ConsentTextVersion.CURRENT || read.storedLocale != locale)
                is ConsentAnswerRead.Unreadable -> RecordOutcome.UnreadableAnswer(read.why)
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: ConsentCallsNotApprovedException) {
            return RecordOutcome.GuardClosed
        } catch (t: Throwable) {
            when (val read = ReasonCodeRead.of(t)) {
                is ReasonCodeRead.Known -> RecordOutcome.Refused(read.code.wire, null)
                is ReasonCodeRead.Unrecognized -> RecordOutcome.Refused(read.raw, ConsentRefusal.ofWire(read.raw))
                ReasonCodeRead.Absent -> RecordOutcome.NotSent
            }
        }
        val (name, detail) = when (outcome) {
            RecordOutcome.GuardClosed -> return outcome
            is RecordOutcome.Recorded -> "recorded" to recordedDetail(outcome)
            is RecordOutcome.Refused -> "refused" to outcome.reasonCode
            RecordOutcome.NotSent -> "not_sent" to "-"
            is RecordOutcome.UnreadableAnswer -> "unreadable_answer" to outcome.why
        }
        EvalCardLog.consentRecord(name, detail, sessionId)
        return outcome
    }

    private fun recordedDetail(outcome: RecordOutcome.Recorded): String {
        val base = when (outcome.alreadyRecorded) {
            true -> "already"
            false -> "new"
            null -> "flag_absent"
        }
        return if (outcome.differs) "${base}_differs" else base
    }
}
