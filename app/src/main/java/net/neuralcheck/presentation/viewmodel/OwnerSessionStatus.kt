package net.neuralcheck.presentation.viewmodel

import androidx.annotation.StringRes
import net.neuralcheck.R
import net.neuralcheck.data.remote.CancelReason
import net.neuralcheck.data.remote.SessionStatus
import net.neuralcheck.presentation.viewmodel.GenerateSessionViewModel.Phase
import net.neuralcheck.util.EvalCardLog

/**
 * What the owner's screen shows for each status the server writes — batch 2, item 3 (user decision 2026-10-15: «ومستمع صاحب المحلّ يقرأ الحالات الخمس التي
 * يكتبها الخادم»). The server writes FIVE values into `scanSessions.status` ([SessionStatus]: relayed from the backend diagnosis at `bbb90a1`, answer 9, not verified
 * from this repository). The listener used to read eight - `waiting`, `joined`, `in_progress` and `done` too, which nobody writes - and to lower-case what it read.
 *
 * ONE `when` over the enum with no `else`: a sixth status added to [SessionStatus] does not compile here until someone decides what the owner sees for it (rules 22 and 25) -
 * and a text that is NOT one of the five ([SessionStatus.Read.Unknown]) or is not a text ([SessionStatus.Read.Absent]) never gets here: [GenerateSessionViewModel]
 * keeps the phase it was in, and `SessionDocWatcher` has already printed what the snapshot held (`session-watch-event status=unknown statusRaw=…`).
 *
 * `pending` is [Phase.WAITING]; `scanning` is [Phase.SCANNING] - the server writes it AT THE JOIN (there is no `joined` status), so the owner's screen goes from waiting
 * to scanning the moment the customer joins, as it always has in practice.
 */
internal fun ownerPhaseFor(status: SessionStatus): Phase = when (status) {
    SessionStatus.PENDING -> Phase.WAITING
    SessionStatus.SCANNING -> Phase.SCANNING
    SessionStatus.COMPLETED -> Phase.COMPLETED
    SessionStatus.EXPIRED -> Phase.EXPIRED
    SessionStatus.CANCELLED -> Phase.CANCELLED
}

/**
 * The text of the owner's cancelled state - batch 3, item 2 (user decision 5, 2026-10-16: «ما يراه المالك: (ب) «غادر العميل الجلسة» / «Müşteri oturumdan ayrıldı»: معتمد
 * بلغتيه»). An enum with the resource and the key the log line prints (explicit, R8-safe).
 */
internal enum class OwnerCancelledText(@StringRes val resId: Int, val key: String) {
    /** Text 3 (decision 90): the session was cancelled; the owner is not told by whom. */
    CANCELLED(R.string.session_cancelled_state, "session_cancelled_state"),

    /** The customer left (`cancelReason = left_by_customer`). */
    CUSTOMER_LEFT(R.string.session_customer_left_state, "session_customer_left_state")
}

/**
 * What the owner reads for a cancelled session, decided by the server's `cancelReason` ([CancelReason.Read], read by `SessionDocWatcher`):
 *  - `left_by_customer` -> [OwnerCancelledText.CUSTOMER_LEFT];
 *  - `cancelled_by_owner` and `consent_declined` -> [OwnerCancelledText.CANCELLED] (text 3; the text of «رفض الموافقة» comes when its screen is built, decision 5);
 *  - a `cancelReason` that is absent, or a text the app has no case for -> [OwnerCancelledText.CANCELLED] too, and [noteOwnerCancelledShown] prints which of the two
 *    it was (rule 22). It is never read as one of the three.
 *
 * The inner `when` is over the enum with no `else`: a fourth reason added to [CancelReason] does not compile here until someone decides what the owner sees for it
 * (rules 22 and 25), as [ownerPhaseFor] does for the statuses.
 */
internal fun ownerCancelledTextFor(reason: CancelReason.Read): OwnerCancelledText = when (reason) {
    is CancelReason.Read.Known -> when (reason.reason) {
        CancelReason.LEFT_BY_CUSTOMER -> OwnerCancelledText.CUSTOMER_LEFT
        CancelReason.CANCELLED_BY_OWNER -> OwnerCancelledText.CANCELLED
        CancelReason.CONSENT_DECLINED -> OwnerCancelledText.CANCELLED
    }
    is CancelReason.Read.Unknown -> OwnerCancelledText.CANCELLED
    CancelReason.Read.Absent -> OwnerCancelledText.CANCELLED
}

/**
 * The reason the owner's state holds after the owner's own cancel was answered. When THIS cancel is the one that cancelled the session (`alreadyCancelled == false`),
 * the reason is the one this app sent: [CancelReason.CANCELLED_BY_OWNER]. When the server says it was already cancelled (`true`) or does not say (`null`), the session was
 * cancelled by someone else first, possibly the customer: the reason stays what the snapshot already said ([previous], `Absent` until a snapshot arrives) - the owner's own
 * button does not overwrite the server's record of who cancelled.
 */
internal fun ownerCancelReasonAfterOwnCancel(previous: CancelReason.Read, outcome: net.neuralcheck.data.remote.CancelSessionResult.Outcome.Cancelled): CancelReason.Read =
    if (outcome.alreadyCancelled == false) CancelReason.Read.Known(CancelReason.CANCELLED_BY_OWNER) else previous

/** Where the owner's screen learned that the session is cancelled; wire strings are explicit (R8-safe). */
internal enum class OwnerCancelledSource(val wire: String) {
    /** The session document's snapshot said `status = cancelled`. */
    SNAPSHOT("snapshot"),

    /** This owner's own cancel was answered (the screen does not wait for the snapshot). */
    OWN_CANCEL("own-cancel")
}

/**
 * Prints `owner-cancelled-shown` for a cancelled state the owner's screen enters: the `cancelReason` as read (a known wire value, `unknown` with its bounded text, or
 * `absent`) and the key of the text that will be shown. One line per entry into the state, never per frame; a named state for an absent or unknown reason (rule 22).
 * Returns the decision it printed.
 */
internal fun noteOwnerCancelledShown(sessionId: String?, source: OwnerCancelledSource, reason: CancelReason.Read): OwnerCancelledText {
    val text = ownerCancelledTextFor(reason)
    EvalCardLog.ownerCancelledShown(
        sessionId = sessionId,
        source = source.wire,
        cancelReason = when (reason) {
            is CancelReason.Read.Known -> reason.reason.wire
            is CancelReason.Read.Unknown -> "unknown"
            CancelReason.Read.Absent -> "absent"
        },
        cancelReasonRaw = (reason as? CancelReason.Read.Unknown)?.raw,
        shown = text.key
    )
    return text
}
