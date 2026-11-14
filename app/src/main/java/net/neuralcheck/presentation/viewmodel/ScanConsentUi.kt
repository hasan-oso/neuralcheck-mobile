package net.neuralcheck.presentation.viewmodel

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import net.neuralcheck.data.remote.ScanConsentClient
import net.neuralcheck.data.remote.SessionLeaver
import net.neuralcheck.domain.consent.ConsentLocale
import net.neuralcheck.domain.consent.ConsentRefusal

/**
 * What the consent screen shows and does (A-P6-12; see [net.neuralcheck.domain.consent.ConsentWire]). The screen that shows it is behind `CONSENT_TEXTS_APPROVED` (the texts of version 2 are written in both
 * languages and the guard is open since 2026-10-23) and is reached only for a shop whose join answer said `consentEnabled: true` (ق15, `afterJoinRoute`); the recording call leaves the phone only through
 * `FirebaseAnalysisService`'s gate on the same switch (the server's half is not deployed).
 *
 * The rule the whole screen exists for (PLAN-06:1402): «فشل تسجيل الموافقة (شبكة) ⇐ الفحص لا يبدأ حتى تُسجَّل. فالموافقة غير المسجَّلة لا تُقرأ موافقة.» The only ways out of this screen into the scan are
 * [ConsentUi.Recorded] - the server's own answer that the consent is recorded (`state: "accepted"`, readable) - and [ConsentUi.NotApplicable] - the server's own refusal `CONSENT_PART_NOT_ENABLED` (ق40): the
 * shop has no consent part, so there is no consent to ask of it. A failed call, any other refusal, an answer that cannot be read, a closed gate are each [ConsentUi.RecordFailed] - the user stays, and may try
 * again or refuse.
 */
sealed interface ConsentUi {
    /** The text is on screen and nothing is happening. */
    data object Idle : ConsentUi

    /** «أوافق وأبدأ الفحص» was pressed and the call is out: both buttons do nothing more. */
    data object Recording : ConsentUi

    /** The consent was NOT recorded ([failure]): the user stays here. */
    data class RecordFailed(val failure: ConsentFailure) : ConsentUi

    /** The server recorded it: the screen moves on to the pre-scan screen. */
    data object Recorded : ConsentUi

    /**
     * The server refused with `CONSENT_PART_NOT_ENABLED` (ق40): the shop has no consent part, so nothing is to be recorded and nothing was recorded. «لا ينطبق» - NOT a failure that stops the scan: the screen
     * moves on to the pre-scan screen, as it does for [Recorded], and no text of a failure is shown.
     */
    data object NotApplicable : ConsentUi

    /** «لا أوافق» was pressed and the cancellation is out. */
    data object Declining : ConsentUi

    /** The refusal could not be sent and the phone stays: [message] is the text the session's cancel already shows for that failure (`SessionMessages.kt`, no new text). */
    data class DeclineFailed(val message: String) : ConsentUi

    /** The refusal is done (confirmed, or the leave ended locally, as a leave does): the screen leaves the funnel. */
    data object Declined : ConsentUi

    /** The holder holds no session: there is nothing to consent to (a process that was restored, a leave that happened elsewhere). The screen leaves the funnel. */
    data object NoSession : ConsentUi
}

/** Why a consent was not recorded, as the screen tells it: one text each ([net.neuralcheck.presentation.screen.ConsentText]). Every outcome that is not a recorded consent is exactly one of these. */
enum class ConsentFailure {
    /** The server said the session is not `scanning` any more (`CONSENT_SESSION_NOT_SCANNING`). */
    SESSION_NOT_SCANNING,

    /** The server could not read the consent already stored for the session (`CONSENT_STORED_UNREADABLE`). */
    STORED_UNREADABLE,

    /** Anything else: no connection, a refusal that has no text of its own, an answer that is not a recorded consent, a closed gate. */
    OTHER
}

/** What the screen makes of the call's outcome: a recorded consent is [ConsentUi.Recorded]; every other outcome is a failure with a reason. */
fun consentUiFor(outcome: ScanConsentClient.RecordOutcome): ConsentUi = when (outcome) {
    is ScanConsentClient.RecordOutcome.Recorded -> ConsentUi.Recorded
    // ق40: classified by the refusal's reasonCode alone (rule 21), and «لا ينطبق» - not a failure
    is ScanConsentClient.RecordOutcome.Refused ->
        if (outcome.refusal == ConsentRefusal.PART_NOT_ENABLED) ConsentUi.NotApplicable
        else ConsentUi.RecordFailed(
            when (outcome.refusal) {
                ConsentRefusal.SESSION_NOT_SCANNING -> ConsentFailure.SESSION_NOT_SCANNING
                ConsentRefusal.STORED_UNREADABLE -> ConsentFailure.STORED_UNREADABLE
                else -> ConsentFailure.OTHER
            }
        )
    ScanConsentClient.RecordOutcome.NotSent, ScanConsentClient.RecordOutcome.GuardClosed, is ScanConsentClient.RecordOutcome.UnreadableAnswer -> ConsentUi.RecordFailed(ConsentFailure.OTHER)
}

/**
 * The consent screen's controller: the two buttons' logic, apart from the view model that owns it (as `ScannedRepeatCooling` is apart from its view model), so it is tested over the real client and the real leaver.
 * [currentSession] is the session the device holds ([SessionLeaver.sessionOfPreScan]); [failureMessage] is the text of a failed refusal (the session cancel's, `SessionMessages.kt`).
 */
class ScanConsentController(
    private val client: ScanConsentClient,
    private val leaver: SessionLeaver,
    private val currentSession: () -> String?,
    private val failureMessage: (Throwable) -> String
) {
    private val _state = MutableStateFlow<ConsentUi>(ConsentUi.Idle)
    val state: StateFlow<ConsentUi> = _state

    /** «أوافق وأبدأ الفحص»: records the consent of the held session in [locale] (the language the screen was drawn in). A press while a call is out, or after the screen is finished, does nothing. */
    suspend fun accept(locale: ConsentLocale) {
        if (!canPress()) return
        val sessionId = currentSession()
        if (sessionId.isNullOrBlank()) {
            _state.value = ConsentUi.NoSession
            return
        }
        _state.value = ConsentUi.Recording
        _state.value = consentUiFor(client.record(sessionId, locale))
    }

    /** «لا أوافق»: cancels the held session with `consent_declined` and leaves. A press while a call is out, or after the screen is finished, does nothing. */
    suspend fun decline() {
        if (!canPress()) return
        val sessionId = currentSession()
        if (sessionId.isNullOrBlank()) {
            _state.value = ConsentUi.NoSession
            return
        }
        _state.value = ConsentUi.Declining
        _state.value = try {
            leaver.declineConsent(sessionId)
            ConsentUi.Declined
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            ConsentUi.DeclineFailed(failureMessage(t))
        }
    }

    private fun canPress(): Boolean = when (_state.value) {
        ConsentUi.Recording, ConsentUi.Declining, ConsentUi.Recorded, ConsentUi.NotApplicable, ConsentUi.Declined, ConsentUi.NoSession -> false
        ConsentUi.Idle, is ConsentUi.RecordFailed, is ConsentUi.DeclineFailed -> true
    }
}
