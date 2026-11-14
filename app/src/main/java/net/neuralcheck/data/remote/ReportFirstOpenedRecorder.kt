package net.neuralcheck.data.remote

import kotlinx.coroutines.CancellationException
import net.neuralcheck.domain.form.ReportFirstOpenedRead
import net.neuralcheck.util.EvalCardLog

/**
 * Thrown by [FirebaseAnalysisService] when one of the technician's form's three callables is asked for while `TECH_FORM_TEXTS_APPROVED` is false: nothing was sent. Our own text, no server text in it.
 */
class FormCallsNotApprovedException(callable: String) : IllegalStateException("technician form call '$callable' not sent: the form's texts are not approved (TECH_FORM_TEXTS_APPROVED is false)")

/**
 * Records that the report of a session was shown for the first time - A-P6-08, the form's blindness (see [net.neuralcheck.domain.form.ReportFirstOpenedWire]). Two places call it, one per device:
 *  - the owner's phone, when the owner taps «عرض التقرير» of the completed session ([Source.OWNER], `GenerateSessionViewModel.onReportOpened`);
 *  - the customer's phone, when the analysis of the scan that started in that session has just produced the report it is about to show ([Source.CUSTOMER], `ReportViewModel.loadScan`, which reads the
 *    scan's session from [ScanSessionRecords] BEFORE the analysis consumes it).
 * "First" is the server's: it keeps the earliest call of either device and answers `recorded: false` to every later one, so a repeated call is harmless; the callers still ask once per session per
 * screen visit, not on every recomposition. The call leaves the UI's path: [record] never throws and the caller does not wait on it for anything it shows.
 *
 * Nothing is sent while the form's texts are not approved: that is [FirebaseAnalysisService]'s gate (rule 35, at the point the request leaves the phone), and the answer to it here is [Outcome.GuardClosed] with
 * NO log line - this runs on every report in production today and must be silent then.
 */
class ReportFirstOpenedRecorder(private val service: FirebaseAnalysisService) {

    enum class Source(val wire: String) {
        OWNER("owner"),
        CUSTOMER("customer")
    }

    /** What the call came to. Every way it can end is one of these; none is read as another. */
    sealed interface Outcome {
        /** The form's texts are not approved: the call was not made (and not logged). */
        data object GuardClosed : Outcome

        /** The server recorded it now (`recorded: true`). */
        data object Recorded : Outcome

        /** The server had it from an earlier call (`recorded: false`). */
        data object AlreadyRecorded : Outcome

        /** The server refused: [reasonCode] is the token of `details.reasonCode` (rule 21: the text, not the exception class), bounded by the log's printer. */
        data class Refused(val reasonCode: String) : Outcome

        /** No `reasonCode`: a disconnect, a timeout, no signed-in user, any other failure. */
        data object NotSent : Outcome

        /** The call succeeded but its answer cannot be read as `recorded: true | false` ([why]: a fixed token). */
        data class UnreadableAnswer(val why: String) : Outcome
    }

    suspend fun record(sessionId: String, source: Source): Outcome {
        val outcome = try {
            when (val read = ReportFirstOpenedRead.of(service.recordReportFirstOpened(sessionId))) {
                ReportFirstOpenedRead.Recorded -> Outcome.Recorded
                ReportFirstOpenedRead.AlreadyRecorded -> Outcome.AlreadyRecorded
                is ReportFirstOpenedRead.Unreadable -> Outcome.UnreadableAnswer(read.why)
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: FormCallsNotApprovedException) {
            return Outcome.GuardClosed
        } catch (t: Throwable) {
            when (val read = ReasonCodeRead.of(t)) {
                is ReasonCodeRead.Known -> Outcome.Refused(read.code.wire)
                is ReasonCodeRead.Unrecognized -> Outcome.Refused(read.raw)
                ReasonCodeRead.Absent -> Outcome.NotSent
            }
        }
        val (name, detail) = when (outcome) {
            Outcome.GuardClosed -> return outcome
            Outcome.Recorded -> "recorded" to "-"
            Outcome.AlreadyRecorded -> "already_recorded" to "-"
            is Outcome.Refused -> "refused" to outcome.reasonCode
            Outcome.NotSent -> "not_sent" to "-"
            is Outcome.UnreadableAnswer -> "unreadable_answer" to outcome.why
        }
        EvalCardLog.reportFirstOpened(sessionId, source.wire, name, detail)
        return outcome
    }
}
