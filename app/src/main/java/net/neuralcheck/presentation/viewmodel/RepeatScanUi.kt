package net.neuralcheck.presentation.viewmodel

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import net.neuralcheck.data.remote.RepeatScanClient
import net.neuralcheck.domain.repeat.Cooling
import net.neuralcheck.domain.repeat.CoolingDecision
import net.neuralcheck.domain.repeat.RepeatDecision
import net.neuralcheck.domain.repeat.RepeatRefusal
import net.neuralcheck.domain.repeat.StartableReference
import net.neuralcheck.domain.repeat.TempRead
import net.neuralcheck.domain.repeat.coolingDecision
import net.neuralcheck.domain.repeat.excessTenths

/**
 * The repeat scan's state on its two screens (A-P6-07; see [net.neuralcheck.domain.repeat.RepeatScanWire]). The screens that show it are behind `REPEAT_TEXTS_APPROVED` (their texts are approved and written
 * since 2026-10-23, and the switch is open) and the two callables leave the phone only through `FirebaseAnalysisService`'s gate on the same switch (the server's half is not deployed: the offer call fails and
 * is only logged, so nothing is drawn).
 */

// ---- the technician's phone (the session's owner): PLAN-06 screen ف١٤ ----

/** What the technician's screen knows about the repeat of the scan its completed session ended in. Every way the question can end is one of these; none is read as another (rule 22). */
sealed interface RepeatNoticeUi {
    /** The question was not asked: the session is not completed yet, or the call is not open. */
    data object NotAsked : RepeatNoticeUi

    /** The call is out. */
    data object Asking : RepeatNoticeUi

    /** The scanned phone was chosen for a cold repeat and the repeat has not started: the notice is drawn - until the technician presses «ليس الآن» ([dismissed]; on this screen only, nothing is sent). */
    data class Notice(val dismissed: Boolean = false) : RepeatNoticeUi

    /** The server answered and there is nothing to offer ([token]: the decision's, for the log). No text exists for it: nothing is drawn. */
    data class NothingToOffer(val token: String) : RepeatNoticeUi

    /** The call failed, was refused, or its answer cannot be read ([token]). No text exists for it: nothing is drawn, and the log says which. */
    data class Unavailable(val token: String) : RepeatNoticeUi
}

/** What the technician's screen makes of the offer call's outcome. */
fun repeatNoticeFor(outcome: RepeatScanClient.OfferOutcome): RepeatNoticeUi = when (outcome) {
    RepeatScanClient.OfferOutcome.GuardClosed -> RepeatNoticeUi.NotAsked
    is RepeatScanClient.OfferOutcome.Answered -> when (val d = outcome.decision) {
        is RepeatDecision.Startable -> RepeatNoticeUi.Notice()
        is RepeatDecision.NotApplicable, is RepeatDecision.NotStartable -> RepeatNoticeUi.NothingToOffer(d.token)
        is RepeatDecision.Unreadable -> RepeatNoticeUi.Unavailable(d.token)
    }
    is RepeatScanClient.OfferOutcome.Refused -> RepeatNoticeUi.Unavailable("refused_${outcome.reasonCode}")
    RepeatScanClient.OfferOutcome.NotSent -> RepeatNoticeUi.Unavailable("not_sent")
}

// ---- the scanned phone: PLAN-06 screen ف١٣ ----

/** How the child session's start stands on the scanned phone. */
sealed interface RepeatStartUi {
    data object Idle : RepeatStartUi

    /** The call is out: both buttons are disabled. */
    data object InFlight : RepeatStartUi

    /** The start failed; the technician can press again. [refusal] is the contract's name for a refusal, null for a disconnect, an unreadable answer or a code that is none of the twelve. */
    data class Failed(val refusal: RepeatRefusal?, val token: String) : RepeatStartUi
}

/** What the scanned phone's report screen knows about its repeat. */
sealed interface ScannedRepeatUi {
    /** Nothing asked: no report of a session scan has been shown, or the call is not open. */
    data object Idle : ScannedRepeatUi

    data object Asking : ScannedRepeatUi

    /** The server's answer is not an offer the screen draws ([token]): not eligible, not selected, already started or done, no usable reference temperature, unreadable, refused or not sent. Nothing is drawn. */
    data class NotOffered(val token: String) : ScannedRepeatUi

    /**
     * The phone was chosen and must cool back to the first scan's start temperature ([referenceTenths]). [decision] is null until the first reading of the phone's own temperature has arrived; [waitChosen] is
     * «انتظر» (the warning and «ابدأ رغم ذلك» go away, on this screen only).
     */
    data class Cooling(
        val referenceTenths: Int,
        val decision: CoolingDecision?,
        val waitChosen: Boolean,
        val start: RepeatStartUi
    ) : ScannedRepeatUi

    /** The child session exists and this device now holds it: the screen goes to the scan ([childSessionId] is for the log and the test). */
    data class Started(val childSessionId: String) : ScannedRepeatUi
}

/**
 * The scanned phone's repeat, with nothing of Android in it. [onReportShown] asks the server once per (session, scan) - the pair is the authorisation, the scanned phone holds both; the answer decides whether a
 * cooling screen exists at all ([ScannedRepeatUi.Cooling] only for a startable offer that carries a usable first-scan temperature). [onTemperature] feeds the phone's own battery temperature (tenths of a degree, or
 * a named reason there is none); the decision is [coolingDecision]. [start] is the two buttons: «جاهز — ابدأ الفحص المكرّر» (only while the decision is Ready, sends no flag) and «ابدأ رغم ذلك» (only while it is NotCooled,
 * sends `startedBeforeCooling: true`); a press at any other moment does nothing. A start that succeeded hands the child session to [adoptChild] (the holder, so the scan that begins is recorded in it) and moves to
 * [ScannedRepeatUi.Started]; no code is typed and nothing is joined or signed out (PLAN-06:639).
 */
class ScannedRepeatCooling(
    private val client: RepeatScanClient,
    private val adoptChild: (String) -> Unit
) {
    private val _state = MutableStateFlow<ScannedRepeatUi>(ScannedRepeatUi.Idle)
    val state: StateFlow<ScannedRepeatUi> = _state

    private var askedFor: Pair<String, String>? = null
    private var parent: Pair<String, String>? = null
    private var peakExcessTenths = 0
    private var lastRead: TempRead? = null

    suspend fun onReportShown(sessionId: String, scanId: String) {
        if (askedFor == sessionId to scanId) return
        askedFor = sessionId to scanId
        _state.value = ScannedRepeatUi.Asking
        when (val outcome = client.offer(sessionId, scanId, RepeatScanClient.Role.SCANNED)) {
            RepeatScanClient.OfferOutcome.GuardClosed -> {
                askedFor = null
                _state.value = ScannedRepeatUi.Idle
            }
            is RepeatScanClient.OfferOutcome.Refused -> _state.value = ScannedRepeatUi.NotOffered("refused_${outcome.reasonCode}")
            RepeatScanClient.OfferOutcome.NotSent -> _state.value = ScannedRepeatUi.NotOffered("not_sent")
            is RepeatScanClient.OfferOutcome.Answered -> when (val d = outcome.decision) {
                is RepeatDecision.Startable -> when (val temp = d.reference) {
                    is StartableReference.Usable -> {
                        parent = sessionId to scanId
                        peakExcessTenths = 0
                        val reference = Cooling.tenthsOf(temp.valueC)
                        _state.value = ScannedRepeatUi.Cooling(reference, decisionFor(reference, lastRead), waitChosen = false, start = RepeatStartUi.Idle)
                    }
                    // chosen, but the reference the check needs is not usable: PLAN-06:669 names the server's classification (`COOLING_REFERENCE_ABSENT`), not a screen - nothing is drawn (reported)
                    is StartableReference.NotUsable -> _state.value = ScannedRepeatUi.NotOffered("reference_${temp.state.wire.lowercase()}")
                }
                is RepeatDecision.NotApplicable, is RepeatDecision.NotStartable -> _state.value = ScannedRepeatUi.NotOffered(d.token)
                is RepeatDecision.Unreadable -> _state.value = ScannedRepeatUi.NotOffered(d.token)
            }
        }
    }

    /** A reading of the phone's own battery temperature. Remembered even before the offer's answer arrives. */
    fun onTemperature(read: TempRead) {
        lastRead = read
        _state.update { s ->
            if (s is ScannedRepeatUi.Cooling) s.copy(decision = decisionFor(s.referenceTenths, read)) else s
        }
    }

    private fun decisionFor(referenceTenths: Int, read: TempRead?): CoolingDecision? {
        if (read == null) return null
        if (read is TempRead.Present) peakExcessTenths = maxOf(peakExcessTenths, excessTenths(referenceTenths, read.tenths))
        return coolingDecision(referenceTenths, read, peakExcessTenths)
    }

    /** «انتظر». */
    fun chooseWait() {
        _state.update { s -> if (s is ScannedRepeatUi.Cooling && s.decision is CoolingDecision.NotCooled) s.copy(waitChosen = true) else s }
    }

    /** «جاهز — ابدأ الفحص المكرّر»: only while the phone is within the degree. */
    suspend fun startReady() = start(requireReady = true)

    /** «ابدأ رغم ذلك»: only while the phone is not within the degree. */
    suspend fun startAnyway() = start(requireReady = false)

    private suspend fun start(requireReady: Boolean) {
        val s = _state.value as? ScannedRepeatUi.Cooling ?: return
        if (s.start is RepeatStartUi.InFlight) return
        val decision = s.decision ?: return
        if (requireReady && decision != CoolingDecision.Ready) return
        if (!requireReady && decision !is CoolingDecision.NotCooled) return
        val (sessionId, scanId) = parent ?: return
        _state.value = s.copy(start = RepeatStartUi.InFlight)
        when (val outcome = client.start(sessionId, scanId, if (requireReady) null else true, RepeatScanClient.Role.SCANNED)) {
            RepeatScanClient.StartOutcome.GuardClosed -> _state.value = s.copy(start = RepeatStartUi.Failed(null, "guard_closed"))
            is RepeatScanClient.StartOutcome.Started -> {
                adoptChild(outcome.childSessionId)
                _state.value = ScannedRepeatUi.Started(outcome.childSessionId)
            }
            is RepeatScanClient.StartOutcome.Refused -> _state.value = s.copy(start = RepeatStartUi.Failed(outcome.refusal, outcome.reasonCode))
            RepeatScanClient.StartOutcome.NotSent -> _state.value = s.copy(start = RepeatStartUi.Failed(null, "not_sent"))
            is RepeatScanClient.StartOutcome.UnreadableAnswer -> _state.value = s.copy(start = RepeatStartUi.Failed(null, "unreadable_${outcome.why}"))
        }
    }

    /** The screen went to the scan: the controller is spent (a second start of the same pair would be refused `REPEAT_ALREADY_STARTED`). */
    fun startedConsumed() {
        if (_state.value is ScannedRepeatUi.Started) _state.value = ScannedRepeatUi.NotOffered("started_consumed")
    }
}
