package net.neuralcheck.presentation.viewmodel

import net.neuralcheck.data.remote.DefectType
import net.neuralcheck.data.remote.PartRead
import net.neuralcheck.data.remote.ProtocolPart
import net.neuralcheck.data.remote.ProtocolPartsRead
import net.neuralcheck.data.remote.PurposeRequestRejection
import net.neuralcheck.data.remote.ReasonCodeRead
import net.neuralcheck.data.remote.ScanPurposeChoice
import net.neuralcheck.data.remote.SetSessionPurposeAnswer
import net.neuralcheck.data.remote.SessionErrorCode
import net.neuralcheck.data.remote.purposeRequestRejection
import net.neuralcheck.util.EvalCardLog

/**
 * The purpose chips of the owner's session screen as a STATE - row A-P6-03 (غرض الفحص), `plan/PLAN-06-DATA-COLLECTION.md` section 2 (lines 449-517): the logic of the chips, with no text and no
 * Compose in it, so it is tested as a value and the screen only draws it (and draws nothing while the chips' texts are unapproved - see `PURPOSE_CHIPS_TEXTS_APPROVED`).
 *
 * What the plan says, and where each part lives:
 *  - "the session is still created at once, as `normal`/`default`" - [PurposeConfirmed.AtCreation]: the create's answer does not carry the purpose (contract A2 names only `protocolParts`
 *    in it), so what is shown before any tap is the CONTRACT's word, not an echo from the server, and it is a state of its own, apart from a chosen «normal» ([PurposeConfirmed.Chosen]);
 *  - "four chips under the QR, «عادي» highlighted" - three today, `return` having been dropped (contract A2) and the two battery chips replaced by «قبل الصيانة» / «بعد الصيانة» (order of 2026-10-22): [ScanPurposeChoice];
 *  - a maintenance purpose is never sent without its fault type (contract A2 addendum of 2026-10-22): the sent value is a [PurposeSelection], which has no value of a maintenance purpose without one;
 *  - "a tap on another chip calls `setSessionPurpose`; the change is accepted while the session is `pending`; after the join the chips are locked" - [purposeTapDecision]: the session is
 *    pending exactly while the owner's screen is in [GenerateSessionViewModel.Phase.WAITING] (the listener moves it to SCANNING when the customer's phone joins), and
 *    [PurposeFailure.NotPending] (the server's `PURPOSE_SESSION_NOT_PENDING`, a join that happened between the tap and the call) locks it for good;
 *  - "the shop's parts decide whether the chips exist" - [purposeUiFor] reads `protocolParts` from the create's answer: ONLY `purposes` read as enabled offers them.
 *
 * Rules 20 and 22: the app does not decide `purposeSource` (the server writes `default` for a session nobody touched and `selected` for one that was chosen), so a tap on the chip that is
 * already highlighted is SENT - otherwise the server could never tell a chosen «normal» from the default one. Rule 21: a failure is classified by `details.reasonCode`, never by the
 * exception's type or message. Rule 25: every value of every enumeration that reaches here has a named place; a value with no case is declared in the log line, never read as another.
 */
sealed interface PurposeUi {
    /** The create has not answered yet (or the screen is not in a state that has a session): nothing to draw. */
    data object NotRead : PurposeUi

    /** The create answered and the chips do not exist for this session: [why] is the reason, printed in the log. */
    data class NotOffered(val why: Why) : PurposeUi {
        enum class Why(val token: String) {
            /** No `protocolParts` in the answer (a server that does not send it, or a JSON null). */
            PARTS_ABSENT("parts_absent"),

            /** `protocolParts` is there but not an object. */
            PARTS_NOT_AN_OBJECT("parts_not_an_object"),

            /** `purposes` is `false`. */
            PURPOSES_DISABLED("purposes_disabled"),

            /** `purposes` is not among the listed parts: «الغائب «غير مفعَّل»» (PLAN-06:787-788). */
            PURPOSES_NOT_LISTED("purposes_not_listed"),

            /** `purposes` is there with a value that is not a boolean. */
            PURPOSES_UNRECOGNIZED("purposes_unrecognized")
        }
    }

    /**
     * The chips exist. [confirmed] is what the session is, as far as the app knows; [inFlight] is the choice whose call is out (at most one); [failure] is the last call's end if it was not an
     * acceptance, until the next tap starts a call.
     */
    data class Offered(
        val confirmed: PurposeConfirmed = PurposeConfirmed.AtCreation,
        val inFlight: PurposeSelection? = null,
        val failure: PurposeFailure? = null,
        /**
         * A maintenance chip was tapped and its fault type is still to be chosen (order of 2026-10-22: «بعد اختيار «قبل الصيانة» أو «بعد الصيانة» يُختار النوع من الخمسة»). While it is set NOTHING has been sent
         * for that chip, and the only way to send a maintenance purpose is a tap on one of the five types ([purposeTypeTapDecision]): there is no path that sends it without a type, and no default type.
         */
        val awaitingType: ScanPurposeChoice? = null,
        /**
         * Whether the shop has the two MAINTENANCE chips and the fault-type list - BOTH `purposes` and `batteryBeforeAfter` ([ProtocolPartsRead.maintenanceOffered], decision Q31 of 2026-10-22). False = only «عادي» is drawn and
         * a maintenance tap, type or call is ignored with `maintenance_not_offered`: the server would refuse it with `PURPOSE_PART_NOT_ENABLED`. The default is the contract's «الغائب غير مفعَّل» (rule 22).
         */
        val maintenanceOffered: Boolean = false
    ) : PurposeUi {
        /** The chips drawn, in order: «عادي» always (it needs `purposes` alone), and the two maintenance ones only for a shop with both parts. */
        fun choices(): List<ScanPurposeChoice> = ScanPurposeChoice.values().filter { !it.needsDefectType || maintenanceOffered }

        /** The chip that is highlighted: the confirmed one. A choice still in flight is not highlighted as chosen (the server has not said yes). */
        fun highlighted(): ScanPurposeChoice = when (confirmed) {
            PurposeConfirmed.AtCreation -> ScanPurposeChoice.NORMAL
            is PurposeConfirmed.Chosen -> confirmed.selection.choice
        }

        /** The maintenance selection the server accepted, or null (the creation default, a chosen «normal»). */
        private fun confirmedMaintenance(): PurposeSelection.Maintenance? = (confirmed as? PurposeConfirmed.Chosen)?.selection as? PurposeSelection.Maintenance

        /** The chip whose list of fault types is on the screen: the one awaiting its type, or else the accepted maintenance chip (so the technician sees which type he chose). Null = no list. */
        fun typeListFor(): ScanPurposeChoice? = if (!maintenanceOffered) null else awaitingType ?: confirmedMaintenance()?.choice

        /** The fault type highlighted in that list: the accepted one, and only while the list is the accepted chip's. A type in flight is not highlighted (the server has not said yes). */
        fun highlightedType(): DefectType? = confirmedMaintenance()?.takeIf { it.choice == typeListFor() }?.defectType

        /**
         * The chip the row SHOWS as chosen (order of 2026-10-28: the technician's tap answered at once, not when a cold server answers - 4-5 s after a pause, `ROUND1-VERIFICATION-REPORT-2026-10-28.md`): the chip whose call is
         * out, else the maintenance chip that awaits its type, else the confirmed one ([highlighted]). It claims nothing the server has not said: [highlighted] and [confirmed] are untouched (they are what the app knows), and
         * what is shown ahead of them is a REQUEST in progress - a spinner marks a call out. While [locked] (the customer's phone joined) or after a call that did not end in an acceptance ([failure]) only the confirmed one is
         * shown, so a chip the server never took is not left looking chosen.
         */
        fun shownChoice(locked: Boolean): ScanPurposeChoice =
            if (locked || failure != null) highlighted() else inFlight?.choice ?: awaitingType ?: highlighted()

        /** The fault type the list SHOWS as chosen: the type whose call is out (for the chip whose list is on the screen), else the accepted one ([highlightedType]); the same two limits as [shownChoice]. */
        fun shownType(locked: Boolean): DefectType? {
            if (locked || failure != null) return highlightedType()
            val asked = (inFlight as? PurposeSelection.Maintenance)?.takeIf { it.choice == typeListFor() }?.defectType
            return asked ?: highlightedType()
        }
    }
}

/**
 * What a purpose call asks for: «عادي» alone, or a maintenance purpose WITH its fault type. There is no value of this type that is a maintenance purpose without a type or a «normal» with one (rule 22: the
 * absence of the type is not read as «no type needed»): [Maintenance] refuses to be built otherwise, and [of] is the one place that decides which combination is which.
 */
sealed interface PurposeSelection {
    val choice: ScanPurposeChoice
    val defectType: DefectType?

    /** «عادي»: no fault type. */
    data object Normal : PurposeSelection {
        override val choice: ScanPurposeChoice get() = ScanPurposeChoice.NORMAL
        override val defectType: DefectType? get() = null
    }

    /** «قبل الصيانة» or «بعد الصيانة» with the fault type the technician chose. */
    data class Maintenance(override val choice: ScanPurposeChoice, override val defectType: DefectType) : PurposeSelection {
        init {
            require(choice.needsDefectType) { "${choice.wire} carries no fault type" }
        }
    }

    companion object {
        /** The selection of [choice] with [defectType], or the named reason they do not go together ([purposeRequestRejection]). */
        fun of(choice: ScanPurposeChoice, defectType: DefectType?): PurposeSelectionBuild =
            when (val rejection = purposeRequestRejection(choice, defectType)) {
                null -> PurposeSelectionBuild.Built(if (defectType == null) Normal else Maintenance(choice, defectType))
                else -> PurposeSelectionBuild.Rejected(rejection)
            }
    }
}

/** The result of [PurposeSelection.of]. */
sealed interface PurposeSelectionBuild {
    data class Built(val selection: PurposeSelection) : PurposeSelectionBuild
    data class Rejected(val reason: PurposeRequestRejection) : PurposeSelectionBuild
}

/** What the session's purpose is, as far as the app knows. Two named states: the contract's creation default, and a choice the server accepted. */
sealed interface PurposeConfirmed {
    /** Nothing was tapped: `normal` / `default` by the contract (not echoed by the server). */
    data object AtCreation : PurposeConfirmed

    /** A `setSessionPurpose` for [selection] was accepted (the server then holds it as `selected`, even for «normal»). */
    data class Chosen(val selection: PurposeSelection) : PurposeConfirmed
}

/** How the last purpose call ended when it was not accepted. */
sealed interface PurposeFailure {
    /** `PURPOSE_SESSION_NOT_PENDING`: the customer's phone joined. The chips are locked for good. */
    data object NotPending : PurposeFailure

    /**
     * `PURPOSE_PART_NOT_ENABLED` (Q31): the server refused a purpose the shop's parts do not allow. The chips are NOT locked (a «normal» can still be chosen) and the surface shows the text of row ص21
     * ([net.neuralcheck.presentation.screen.PurposeChipLabels.partNotEnabled]); the app does not send such a purpose, so this is the safety net (see [PurposeUi.Offered.maintenanceOffered]).
     */
    data object PartNotEnabled : PurposeFailure

    /** The server refused with another `details.reasonCode` ([token]: the code's wire string, or `unrecognized`); the chips stay as they were and can be tried again. */
    data class Refused(val token: String) : PurposeFailure

    /** No `reasonCode`: a disconnect, a timeout, any other failure. The chips stay as they were and can be tried again. */
    data object Failed : PurposeFailure
}

/** What a tap on a chip does: sent, or ignored for a named reason. */
sealed interface PurposeTap {
    data object Send : PurposeTap
    data class Ignore(val reason: String) : PurposeTap
}

/**
 * The chips for the shop the create answered about. Only `purposes` read as enabled offers them (and then «عادي»); every other state of the part, and a key that is missing or not an object, does not. The two maintenance
 * chips and the fault-type list are offered only with `batteryBeforeAfter` enabled too ([ProtocolPartsRead.maintenanceOffered]: the one rule).
 */
internal fun purposeUiFor(read: ProtocolPartsRead): PurposeUi = when (read) {
    ProtocolPartsRead.Absent -> PurposeUi.NotOffered(PurposeUi.NotOffered.Why.PARTS_ABSENT)
    is ProtocolPartsRead.NotAnObject -> PurposeUi.NotOffered(PurposeUi.NotOffered.Why.PARTS_NOT_AN_OBJECT)
    is ProtocolPartsRead.Read -> when (read.parts[ProtocolPart.PURPOSES]) {
        PartRead.Enabled -> PurposeUi.Offered(maintenanceOffered = read.maintenanceOffered())
        PartRead.Disabled -> PurposeUi.NotOffered(PurposeUi.NotOffered.Why.PURPOSES_DISABLED)
        PartRead.NotListed -> PurposeUi.NotOffered(PurposeUi.NotOffered.Why.PURPOSES_NOT_LISTED)
        is PartRead.Unrecognized -> PurposeUi.NotOffered(PurposeUi.NotOffered.Why.PURPOSES_UNRECOGNIZED)
        // a map built by the reader always has the key; a hand-built one without it is the same as not listed
        null -> PurposeUi.NotOffered(PurposeUi.NotOffered.Why.PURPOSES_NOT_LISTED)
    }
}

/** The token of one part for the log line: `enabled`, `disabled`, `not_listed` or `unrecognized_<kind>`. */
private fun partToken(read: ProtocolPartsRead.Read, part: ProtocolPart): String = when (val value = read.parts[part]) {
    PartRead.Enabled -> "enabled"
    PartRead.Disabled -> "disabled"
    PartRead.NotListed, null -> "not_listed"
    is PartRead.Unrecognized -> "unrecognized_${value.kind}"
}

/**
 * The log line of the create's reading (one per create): the two parts the chips depend on, `purposes` and `batteryBeforeAfter` (since Q31 a shop with the first alone is offered «عادي» only, and the line is where a field
 * check sees why). The tokens and the shape carry no `:` (the printer keeps letters, digits and `_.-`).
 */
internal fun logProtocolPartsRead(read: ProtocolPartsRead) {
    val shape: String
    val purposes: String
    val batteryBeforeAfter: String
    val unknown: List<String>
    when (read) {
        ProtocolPartsRead.Absent -> { shape = "absent"; purposes = "none"; batteryBeforeAfter = "none"; unknown = emptyList() }
        is ProtocolPartsRead.NotAnObject -> { shape = "not_an_object_${read.kind}"; purposes = "none"; batteryBeforeAfter = "none"; unknown = emptyList() }
        is ProtocolPartsRead.Read -> {
            shape = "object"
            unknown = read.unknownKeys
            purposes = partToken(read, ProtocolPart.PURPOSES)
            batteryBeforeAfter = partToken(read, ProtocolPart.BATTERY_BEFORE_AFTER)
        }
    }
    EvalCardLog.sessionProtocolParts(shape, purposes, batteryBeforeAfter, unknown)
}

/**
 * Whether the chips can be tapped: only while the session is `pending` (the owner's screen is WAITING) and no refusal said it is not any more.
 * [GenerateSessionViewModel.Phase.WAITING] is the one phase the listener leaves when the customer's phone joins.
 */
internal fun purposeChipsLocked(phase: GenerateSessionViewModel.Phase, ui: PurposeUi.Offered): Boolean =
    phase != GenerateSessionViewModel.Phase.WAITING || ui.failure is PurposeFailure.NotPending

/** What a tap does for the session in [phase] showing [ui] ([hasSession]: the screen holds a session id). The order of the checks is the order of the reasons the log names. */
internal fun purposeTapDecision(ui: PurposeUi, phase: GenerateSessionViewModel.Phase, hasSession: Boolean): PurposeTap {
    val offered = ui as? PurposeUi.Offered ?: return PurposeTap.Ignore("not_offered")
    if (!hasSession) return PurposeTap.Ignore("no_session")
    if (phase != GenerateSessionViewModel.Phase.WAITING) return PurposeTap.Ignore("not_pending")
    if (offered.failure is PurposeFailure.NotPending) return PurposeTap.Ignore("locked")
    if (offered.inFlight != null) return PurposeTap.Ignore("in_flight")
    return PurposeTap.Send
}

/**
 * What a tap on a CHIP does (order of 2026-10-22): «عادي» is sent at once (it needs no type); a maintenance chip sends NOTHING - it opens its list of fault types ([PurposeChipTap.OpenTypes]); or the tap is ignored for the
 * same named reasons as [purposeTapDecision]'s (the chip decision is that decision, then the chip's kind).
 */
sealed interface PurposeChipTap {
    data object SendNormal : PurposeChipTap
    data class OpenTypes(val choice: ScanPurposeChoice) : PurposeChipTap
    data class Ignore(val reason: String) : PurposeChipTap
}

internal fun purposeChipTapDecision(ui: PurposeUi, phase: GenerateSessionViewModel.Phase, hasSession: Boolean, choice: ScanPurposeChoice): PurposeChipTap =
    when (val base = purposeTapDecision(ui, phase, hasSession)) {
        is PurposeTap.Ignore -> PurposeChipTap.Ignore(base.reason)
        PurposeTap.Send -> when {
            // a maintenance chip for a shop without both parts is not drawn; a tap that arrives anyway goes nowhere (Q31)
            choice.needsDefectType && !(ui as PurposeUi.Offered).maintenanceOffered -> PurposeChipTap.Ignore("maintenance_not_offered")
            choice.needsDefectType -> PurposeChipTap.OpenTypes(choice)
            else -> PurposeChipTap.SendNormal
        }
    }

/**
 * What a tap on one of the five fault types does: sent as the maintenance selection of the chip whose list of types is on the screen ([PurposeUi.Offered.typeListFor]: the chip awaiting its type, or - since the order of
 * 2026-10-28 - the chip whose purpose the server already accepted, so a type chosen by mistake is changed with one tap, the session being still pending), or ignored for a named reason. A type with no list on the
 * screen is never sent.
 */
sealed interface PurposeTypeTap {
    data class Send(val selection: PurposeSelection.Maintenance) : PurposeTypeTap
    data class Ignore(val reason: String) : PurposeTypeTap
}

internal fun purposeTypeTapDecision(ui: PurposeUi, phase: GenerateSessionViewModel.Phase, hasSession: Boolean, type: DefectType): PurposeTypeTap {
    val base = purposeTapDecision(ui, phase, hasSession)
    if (base is PurposeTap.Ignore) return PurposeTypeTap.Ignore(base.reason)
    val offered = ui as PurposeUi.Offered
    if (!offered.maintenanceOffered) return PurposeTypeTap.Ignore("maintenance_not_offered")
    // the reason's token is the one the logs and the field sheets already name: "no chip's list of types is on the screen"
    val listChoice = offered.typeListFor() ?: return PurposeTypeTap.Ignore("no_purpose_awaiting_type")
    return PurposeTypeTap.Send(PurposeSelection.Maintenance(listChoice, type))
}

/** The state when the call for [selection] is out: a maintenance call keeps its chip's list of types open (it is the chip awaiting its type until the server answers), «normal» closes it. */
internal fun PurposeUi.Offered.started(selection: PurposeSelection): PurposeUi.Offered =
    copy(inFlight = selection, failure = null, awaitingType = selection.choice.takeIf { it.needsDefectType })

/** The state when the server accepted [selection]: nothing is awaited any more. */
internal fun PurposeUi.Offered.accepted(selection: PurposeSelection): PurposeUi.Offered =
    copy(confirmed = PurposeConfirmed.Chosen(selection), inFlight = null, failure = null, awaitingType = null)

/** The state when the technician tapped the maintenance chip [choice]: its list of fault types opens, nothing is sent, an earlier failure is cleared. Only a choice that needs a type can await one. */
internal fun PurposeUi.Offered.typeRequested(choice: ScanPurposeChoice): PurposeUi.Offered {
    require(choice.needsDefectType) { "${choice.wire} carries no fault type" }
    require(maintenanceOffered) { "the shop has not both parts: no maintenance chip to ask a type for" }
    return copy(awaitingType = choice, failure = null)
}

/** The state when the call ended in [t]: the confirmed purpose is untouched (a refused or failed call changed nothing the app knows of), and the failure is classified by `details.reasonCode` (rule 21). */
internal fun PurposeUi.Offered.failed(t: Throwable): PurposeUi.Offered = copy(inFlight = null, failure = purposeFailureOf(t))

/** The classification of a failed `setSessionPurpose` by its `details.reasonCode` and nothing else (rule 21). */
internal fun purposeFailureOf(t: Throwable): PurposeFailure = when (val read = ReasonCodeRead.of(t)) {
    is ReasonCodeRead.Known ->
        when (read.code) {
            SessionErrorCode.PURPOSE_SESSION_NOT_PENDING -> PurposeFailure.NotPending
            SessionErrorCode.PURPOSE_PART_NOT_ENABLED -> PurposeFailure.PartNotEnabled
            else -> PurposeFailure.Refused(read.code.wire)
        }
    is ReasonCodeRead.Unrecognized -> PurposeFailure.Refused("unrecognized")
    ReasonCodeRead.Absent -> PurposeFailure.Failed
}

/** A tap that was not sent: the one line says why ([PurposeTap.Ignore.reason]). */
internal fun logPurposeIgnored(reason: String) = EvalCardLog.sessionPurposeIgnored(reason)

/** The server accepted [selection] for [sessionId]; the line carries the key names of its answer (a dash for none, `not_an_object`), because the contract copy does not name the answer's shape. */
internal fun logPurposeAccepted(sessionId: String, selection: PurposeSelection, answer: SetSessionPurposeAnswer) {
    val keys = answer.replyKeys
    EvalCardLog.sessionPurposeSet(
        sessionId, selection.choice.wire, selection.defectType?.wire ?: "-", outcome = "accepted", reasonCode = "-",
        replyKeys = if (keys == null) "not_an_object" else if (keys.isEmpty()) "-" else keys.joinToString(".")
    )
}

/** The call for [selection] ended in [t]: `refused` with the reasonCode token, or `failed` when it carried none (rule 21: by reasonCode only). */
internal fun logPurposeEnded(sessionId: String, selection: PurposeSelection, t: Throwable) {
    val read = ReasonCodeRead.of(t)
    EvalCardLog.sessionPurposeSet(
        sessionId, selection.choice.wire, selection.defectType?.wire ?: "-",
        outcome = if (read == ReasonCodeRead.Absent) "failed" else "refused",
        reasonCode = when (read) {
            is ReasonCodeRead.Known -> read.code.wire
            is ReasonCodeRead.Unrecognized -> "unrecognized"
            ReasonCodeRead.Absent -> "absent"
        },
        replyKeys = "-"
    )
}
