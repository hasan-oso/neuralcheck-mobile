package net.neuralcheck.presentation.viewmodel

import net.neuralcheck.data.remote.PairLinkClient
import net.neuralcheck.domain.pair.PairCandidate
import net.neuralcheck.domain.pair.PairFailureKind
import net.neuralcheck.domain.pair.failureKindOf
import net.neuralcheck.util.EvalCardLog

/**
 * The «after maintenance» pair list of the owner's session screen as a STATE - the order of 2026-10-22 (decisions of Hasan 1-7; `evidence/run-2026-10-18/WIRE-SPEC-MAINTENANCE-PAIRS-2026-10-22.md` section 3-c):
 * the logic of the list, with no text and no Compose in it, so it is tested as a value and the screen only draws it (and draws nothing while the list's texts are unapproved - see `PAIR_LIST_TEXTS_APPROVED`).
 *
 * What the order says, and where each part lives:
 *  - "when the session is `maintenance_after` and pending it asks for the list" - [PairListUi.Loading] is entered by the view model when the server ACCEPTED a `maintenance_after` purpose (the purpose the server
 *    holds, not the chip the technician tapped), and the list is asked for the session of that acceptance;
 *  - "states: loading, empty, list, a choice then a confirmation, linked, every refusal by its own text" - [PairListUi]: [PairListUi.Loading], [PairListUi.Empty] (with the `validityHours` the answer carried),
 *    [PairListUi.Choosing] (the candidates, the choice and the state of the confirmation), [PairListUi.Linked], [PairListUi.LoadFailed], and [PairConfirmUi.Failed] for a refused confirmation. A refusal is
 *    shown by [PairFailureKind] - the text the user approved for that `reasonCode`, or the general one for any other, the code going to the log;
 *  - "choosing again before completion" - [PairListUi.Linked] keeps the listing, and a new choice moves it back to [PairListUi.Choosing] with the new candidate selected;
 *  - the candidate's `beforeScanId` is a handle the screen sends back and NEVER shows, and a candidate carries no hardware identifier (the contract has none).
 *
 * Rules 21 and 22: a failure is classified by `details.reasonCode` (in [PairLinkClient]); an answer that does not read is a failure with a fixed token, not an empty list; the absence of a list is not the
 * presence of an empty one.
 */
data class PairListing(val validityHours: Int, val candidates: List<PairCandidate>)

/** Why a call of the pair list failed: the text it is shown with ([kind]) and a bounded token for the log ([reasonToken]: the `reasonCode`, `not_sent`, or `unreadable_<why>`). */
data class PairFailure(val kind: PairFailureKind, val reasonToken: String)

/** The confirmation of the technician's choice. */
sealed interface PairConfirmUi {
    data object Idle : PairConfirmUi

    /** The call for [beforeScanId] is out: the confirm button is disabled, a second tap does nothing. */
    data class InFlight(val beforeScanId: String) : PairConfirmUi

    /** The server refused, or the call failed: [failure] says with which text. The choice stays as it was. */
    data class Failed(val failure: PairFailure) : PairConfirmUi
}

sealed interface PairListUi {
    /** The session is not a confirmed `maintenance_after` (or nothing was asked): nothing to draw. */
    data object NotShown : PairListUi

    /** The list is being asked for. */
    data object Loading : PairListUi

    /** The list could not be had: [failure] is shown with its text. */
    data class LoadFailed(val failure: PairFailure) : PairListUi

    /** The server answered and no «before» scan is open for this phone, this fault type and this window: «لا فحوص «قبل الصيانة» مفتوحة من هذا النوع في آخر N ساعة». */
    data class Empty(val validityHours: Int) : PairListUi

    /** The candidates; [selected] is the `beforeScanId` the technician tapped (null = none), and [confirm] the state of the confirmation for it. */
    data class Choosing(val listing: PairListing, val selected: String?, val confirm: PairConfirmUi) : PairListUi {
        fun selectedCandidate(): PairCandidate? = selected?.let { id -> listing.candidates.firstOrNull { it.beforeScanId == id } }
    }

    /** The server confirmed [linked]: «تم الربط — فحص بتاريخ ...». The listing stays, so the technician can choose again before the scan completes. */
    data class Linked(val listing: PairListing, val linked: PairCandidate) : PairListUi
}

/** The state for what the list's call came to. [PairLinkClient.ListOutcome.GuardClosed] is [PairListUi.NotShown]: nothing was asked, nothing is drawn. */
internal fun pairListFor(outcome: PairLinkClient.ListOutcome): PairListUi = when (outcome) {
    PairLinkClient.ListOutcome.GuardClosed -> PairListUi.NotShown
    is PairLinkClient.ListOutcome.Listed ->
        if (outcome.read.candidates.isEmpty()) PairListUi.Empty(outcome.read.validityHours)
        else PairListUi.Choosing(PairListing(outcome.read.validityHours, outcome.read.candidates), selected = null, confirm = PairConfirmUi.Idle)
    is PairLinkClient.ListOutcome.UnreadableAnswer -> PairListUi.LoadFailed(PairFailure(PairFailureKind.GENERAL, "unreadable_${outcome.why}"))
    is PairLinkClient.ListOutcome.Refused -> PairListUi.LoadFailed(PairFailure(failureKindOf(outcome.reasonCode), outcome.reasonCode))
    PairLinkClient.ListOutcome.NotSent -> PairListUi.LoadFailed(PairFailure(PairFailureKind.GENERAL, "not_sent"))
}

/** The failure of a confirmation's call, or null when it was a confirmation ([PairLinkClient.ConfirmOutcome.Confirmed]) or nothing was sent ([PairLinkClient.ConfirmOutcome.GuardClosed]). */
internal fun pairConfirmFailureFor(outcome: PairLinkClient.ConfirmOutcome): PairFailure? = when (outcome) {
    PairLinkClient.ConfirmOutcome.GuardClosed, PairLinkClient.ConfirmOutcome.Confirmed -> null
    is PairLinkClient.ConfirmOutcome.UnreadableAnswer -> PairFailure(PairFailureKind.GENERAL, "unreadable_${outcome.why}")
    is PairLinkClient.ConfirmOutcome.Refused -> PairFailure(failureKindOf(outcome.reasonCode), outcome.reasonCode)
    PairLinkClient.ConfirmOutcome.NotSent -> PairFailure(PairFailureKind.GENERAL, "not_sent")
}

/**
 * Whether the list is on the screen for a session in [phase]: while it is `pending` (WAITING) and while it is `scanning` (SCANNING) - the contract lets the owner confirm in both (A3 (3): «الجلسة `pending` أو
 * `scanning`»), and choosing again is open until the scan completes - and never once it is completed, cancelled, expired or failed. [PairListUi.NotShown] draws nothing in any phase.
 */
internal fun pairListVisible(phase: GenerateSessionViewModel.Phase, ui: PairListUi): Boolean =
    ui != PairListUi.NotShown &&
        (phase == GenerateSessionViewModel.Phase.WAITING || phase == GenerateSessionViewModel.Phase.SCANNING)

/** What a tap on a candidate does: applied, or ignored for a named reason. */
sealed interface PairSelectTap {
    data class Select(val beforeScanId: String) : PairSelectTap
    data class Ignore(val reason: String) : PairSelectTap
}

/** What a tap on the confirm button does: sent for the selected candidate, or ignored for a named reason. */
sealed interface PairConfirmTap {
    data class Confirm(val beforeScanId: String) : PairConfirmTap
    data class Ignore(val reason: String) : PairConfirmTap
}

/** A tap on the candidate [beforeScanId]: it selects it when the list is on the screen and the id is one of the listed (Choosing, or Linked - choosing again), and no confirmation is out. */
internal fun pairSelectDecision(ui: PairListUi, phase: GenerateSessionViewModel.Phase, hasSession: Boolean, beforeScanId: String): PairSelectTap {
    if (!hasSession) return PairSelectTap.Ignore("no_session")
    if (!pairListVisible(phase, ui)) return PairSelectTap.Ignore("not_visible")
    val listing = when (ui) {
        is PairListUi.Choosing -> {
            if (ui.confirm is PairConfirmUi.InFlight) return PairSelectTap.Ignore("in_flight")
            ui.listing
        }
        is PairListUi.Linked -> ui.listing
        else -> return PairSelectTap.Ignore("not_choosing")
    }
    if (listing.candidates.none { it.beforeScanId == beforeScanId }) return PairSelectTap.Ignore("unknown_candidate")
    return PairSelectTap.Select(beforeScanId)
}

/** A tap on the confirm button: it confirms the selected candidate when the list is on the screen, one is selected, and no confirmation is out. */
internal fun pairConfirmDecision(ui: PairListUi, phase: GenerateSessionViewModel.Phase, hasSession: Boolean): PairConfirmTap {
    if (!hasSession) return PairConfirmTap.Ignore("no_session")
    if (!pairListVisible(phase, ui)) return PairConfirmTap.Ignore("not_visible")
    val choosing = ui as? PairListUi.Choosing ?: return PairConfirmTap.Ignore("not_choosing")
    if (choosing.confirm is PairConfirmUi.InFlight) return PairConfirmTap.Ignore("in_flight")
    val selected = choosing.selected ?: return PairConfirmTap.Ignore("nothing_selected")
    return PairConfirmTap.Confirm(selected)
}

/** The state after the technician selected [beforeScanId] (a decision of [pairSelectDecision]): Choosing with it selected, a refusal of the earlier confirmation cleared. A linked list goes back to choosing. */
internal fun PairListUi.selected(beforeScanId: String): PairListUi = when (this) {
    is PairListUi.Choosing -> copy(selected = beforeScanId, confirm = PairConfirmUi.Idle)
    is PairListUi.Linked -> PairListUi.Choosing(listing, beforeScanId, PairConfirmUi.Idle)
    else -> this
}

/** The state when the confirmation of [beforeScanId] is out. */
internal fun PairListUi.confirmStarted(beforeScanId: String): PairListUi = when (this) {
    is PairListUi.Choosing -> copy(confirm = PairConfirmUi.InFlight(beforeScanId))
    else -> this
}

/** The state when the confirmation was not made (the exit gate was closed): back to idle, the choice stays. */
internal fun PairListUi.confirmNotMade(): PairListUi = when (this) {
    is PairListUi.Choosing -> copy(confirm = PairConfirmUi.Idle)
    else -> this
}

/** The state when the server confirmed [beforeScanId]: Linked, with the candidate the technician chose. If the list does not hold it (it cannot happen: the id came from the list) nothing changes. */
internal fun PairListUi.confirmed(beforeScanId: String): PairListUi = when (this) {
    is PairListUi.Choosing -> listing.candidates.firstOrNull { it.beforeScanId == beforeScanId }?.let { PairListUi.Linked(listing, it) } ?: this
    else -> this
}

/** The state when the confirmation failed: the choice stays and the failure is shown. */
internal fun PairListUi.confirmFailed(failure: PairFailure): PairListUi = when (this) {
    is PairListUi.Choosing -> copy(confirm = PairConfirmUi.Failed(failure))
    else -> this
}

/** A tap that was not applied: the one line says why. */
internal fun logPairTapIgnored(sessionId: String?, reason: String) = EvalCardLog.pairListEvent("ignored", sessionId, reason)
