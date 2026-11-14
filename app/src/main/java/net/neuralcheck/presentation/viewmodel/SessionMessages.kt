package net.neuralcheck.presentation.viewmodel

import android.content.Context
import android.util.Log
import androidx.annotation.StringRes
import net.neuralcheck.R
import net.neuralcheck.data.remote.ReasonCodeRead
import net.neuralcheck.data.remote.SessionErrorCode
import net.neuralcheck.domain.scoring.ScanRefusalText
import net.neuralcheck.domain.scoring.networkErrorMessageOrNull
import net.neuralcheck.util.EvalCardLog

/**
 * What the user reads when a join or a cancel is refused — batch 2, item 2 (order 2026-10-15; the user's decisions D1-D5 and decision 90, the
 * texts approved verbatim; the server contract is `SessionContract.kt`, relayed from the backend diagnosis at `bbb90a1`, not verified here).
 *
 * ── What decides the text ──
 * The refusal's `details.reasonCode` ([ReasonCodeRead]) and the CALL — nothing else. Not the Functions code, not the message the server
 * sent (rule 21): the same reason arrives with a different Functions code and a different message depending on the call and, in the join, on
 * the phase (lookup or transaction). The text shown comes from this app's resources, never from the server.
 *
 * ── The two tables (the only place a code is tied to a text) ──
 * [JOIN_TEXTS] for the two join entry points, [CANCEL_TEXTS] for the owner's cancel and the customer's leave. A code that is in no table has no
 * approved text (user's order: `CANCEL_*`, `SESSION_NOT_PARTICIPANT`, `SESSION_EXPIRES_AT_INVALID`, `SESSION_STATUS_UNKNOWN`,
 * `SESSION_CLAIM_AGE_UNKNOWN`, a code this app does not know, a refusal with no `reasonCode`): it gets the text that call already showed before
 * this change - NOT a new one - and a `session-refused` log line with the code or `absent` (rule 22: an absence is a named state, never read as a known code):
 *  - typed join: the no-connection text when the failure says there is no connection ([networkErrorMessageOrNull] - the one reading, batch 4, item 1: the error text
 *    of the failure and of its `cause` chain, never for a failure that carries a `reasonCode`), else `error_generic`;
 *  - QR join: the no-connection text when the failure says there is no connection - the same reading and the same text as the typed join, from the one source
 *    (batch 5, item 1; user decision 1, 2026-10-16: «الانضمام بـ QR بلا اتّصال: يعرض نصّ الاتّصال أيضاً، من المصدر الواحد نفسه») - else the fixed chip text `qr_join_failed`,
 *    which this point showed for EVERY failure, a disconnect included, until then;
 *  - cancel / leave: the no-connection text when it applies, else `error_generic`. (For the customer's LEAVE a failure that is no connection never reaches this:
 *    `SessionLeaver` leaves locally - batch 3, item 1 - and `SESSION_EXPIRED` is a successful leave. A refusal that carries an UNRECOGNIZED `reasonCode` whose message
 *    says network gets `error_generic` since batch 4, item 1: the server answered, so it is not a connection failure, and the leave does not happen. Until then that
 *    one case showed the connection text, because the reading was message-only and applied the `reasonCode` exception to a KNOWN code alone. The owner's cancel still gets `error_generic` for `SESSION_NOT_PARTICIPANT`; the customer's LEAVE never does - it
 *    leaves locally, batch 4, item 2.)
 * (A reading of «النصّ القائم اليوم» for each point - named in the report of this change. A code with an approved text wins over the
 * connection heuristic: the server answered.)
 *
 * `SESSION_EXPIRED` at the join shows the existing «الكود غير صالح أو منتهي الصلاحية» (`join_session_invalid`), as does `SESSION_CODE_INVALID`
 * (decision D4). The resource `session_expired` («انتهت صلاحية الكود») is NOT used for a join refusal. `SESSION_COMPLETED` has two texts: the join's and the cancel's.
 */
internal enum class SessionCall(val wire: String) {
    /** The code typed on `JoinSessionScreen` (`JoinSessionViewModel`). */
    JOIN_TYPED("join-typed"),

    /** The QR scanner (`QRScannerViewModel`'s session branch). */
    JOIN_QR("join-qr"),

    /** The owner's «إلغاء الجلسة» button (`cancelled_by_owner`). */
    CANCEL_OWNER("cancel-owner"),

    /** The customer leaves (`left_by_customer`). */
    CANCEL_CUSTOMER("cancel-customer"),

    /**
     * The owner's phone creates a session (`GenerateSessionViewModel.create`). A-SUB wave 1 (order 2026-10-17): `createScanSession`'s two refusals carry `details.reasonCode` now
     * (`SHOP_NO_CREDIT`, `SHOP_SUBSCRIPTION_EXPIRED`), so this call has a table like the others - [CREATE_TEXTS]; since the order of 2026-10-20 the table also names `SHOP_PLAN_REQUIRED`.
     */
    CREATE("create");

    val isJoin: Boolean get() = this == JOIN_TYPED || this == JOIN_QR
}

/** An approved text: its resource and the key the log line prints (explicit, R8-safe). */
internal enum class SessionText(@StringRes val resId: Int, val key: String) {
    JOIN_INVALID_OR_EXPIRED(R.string.join_session_invalid, "join_session_invalid"),
    JOIN_ALREADY_JOINED(R.string.session_join_already_joined, "session_join_already_joined"),
    JOIN_CANCELLED(R.string.session_join_cancelled, "session_join_cancelled"),
    JOIN_COMPLETED(R.string.session_join_completed, "session_join_completed"),
    CANCEL_COMPLETED(R.string.session_cancel_completed, "session_cancel_completed"),
    CANCEL_IN_FLIGHT(R.string.session_cancel_analysis_in_flight, "session_cancel_analysis_in_flight"),

    /** K1 (A-SUB wave 1, user decision 2): the join refused because the shop's subscription ended. One resource with the analysis' text (rule 24): named through [ScanRefusalText]. */
    JOIN_SHOP_SUBSCRIPTION_EXPIRED(ScanRefusalText.SHOP_SUBSCRIPTION_EXPIRED.resId, ScanRefusalText.SHOP_SUBSCRIPTION_EXPIRED.key),

    /**
     * The OWNER's phone, `SHOP_SUBSCRIPTION_EXPIRED` at the create (coordinator's decision in the order of 2026-10-17: the account's owner is the one who reads it): K2, the personal
     * «انتهى اشتراكك، يرجى التجديد» - not K1 (that one says «تواصل مع الإدارة», for a reader who is not the owner). Named through [ScanRefusalText] (rule 24).
     */
    CREATE_SUBSCRIPTION_EXPIRED(ScanRefusalText.SUBSCRIPTION_EXPIRED.resId, ScanRefusalText.SUBSCRIPTION_EXPIRED.key),

    /** The owner's phone, `SHOP_NO_CREDIT` at the create: the existing `subscription_no_scans` («لا يوجد رصيد فحوصات. يرجى شراء اشتراك.»), as the order's decision says. */
    CREATE_NO_CREDIT(ScanRefusalText.NO_CREDIT.resId, ScanRefusalText.NO_CREDIT.key),

    /**
     * The owner's phone, `SHOP_PLAN_REQUIRED` at the create (order 2026-10-20; the user's decision of that date: the Arabic text approved as it is and the Turkish one approved,
     * `DECISIONS-FOR-HASAN-2026-10-18.md` section 2 row 2). A text of the create alone, so its resource is named here, once (rule 24); the server's own message for it is never shown.
     */
    CREATE_PLAN_REQUIRED(R.string.shop_plan_required, "shop_plan_required")
}

/** The join's approved texts, by `reasonCode` (user decisions of 2026-10-10 and 2026-10-15 D4; the table of the order of this batch). */
internal val JOIN_TEXTS: Map<SessionErrorCode, SessionText> = mapOf(
    SessionErrorCode.SESSION_CODE_INVALID to SessionText.JOIN_INVALID_OR_EXPIRED,
    SessionErrorCode.SESSION_EXPIRED to SessionText.JOIN_INVALID_OR_EXPIRED,
    SessionErrorCode.SESSION_ALREADY_JOINED to SessionText.JOIN_ALREADY_JOINED,
    SessionErrorCode.SESSION_CANCELLED to SessionText.JOIN_CANCELLED,
    SessionErrorCode.SESSION_COMPLETED to SessionText.JOIN_COMPLETED,
    // A-SUB wave 1 (order 2026-10-17; user decision 2: K1 as it is). The typed screen and the QR chip read the same table, so both show it.
    SessionErrorCode.SHOP_SUBSCRIPTION_EXPIRED to SessionText.JOIN_SHOP_SUBSCRIPTION_EXPIRED
)

/** The cancel's approved texts (the order of 2026-10-10): only the two refusals the user can act on. */
internal val CANCEL_TEXTS: Map<SessionErrorCode, SessionText> = mapOf(
    SessionErrorCode.SESSION_COMPLETED to SessionText.CANCEL_COMPLETED,
    SessionErrorCode.SESSION_ANALYSIS_IN_FLIGHT to SessionText.CANCEL_IN_FLIGHT
)

/**
 * The owner's create refusals (A-SUB wave 1, order 2026-10-17: «SHOP_SUBSCRIPTION_EXPIRED <- subscription_expired (K2)؛ SHOP_NO_CREDIT <- subscription_no_scans القائم»; and, order 2026-10-20,
 * `SHOP_PLAN_REQUIRED` <- `shop_plan_required`). A refusal with no `details` (a server that sends none), with a code outside this table, or with an unknown one shows `session_error_generic` as before,
 * with the retry button.
 */
internal val CREATE_TEXTS: Map<SessionErrorCode, SessionText> = mapOf(
    SessionErrorCode.SHOP_SUBSCRIPTION_EXPIRED to SessionText.CREATE_SUBSCRIPTION_EXPIRED,
    SessionErrorCode.SHOP_NO_CREDIT to SessionText.CREATE_NO_CREDIT,
    SessionErrorCode.SHOP_PLAN_REQUIRED to SessionText.CREATE_PLAN_REQUIRED
)

/** The approved text [code] has on [call], or null when it has none. */
internal fun approvedSessionText(call: SessionCall, code: SessionErrorCode): SessionText? =
    when {
        call.isJoin -> JOIN_TEXTS
        call == SessionCall.CREATE -> CREATE_TEXTS
        else -> CANCEL_TEXTS
    }[code]

/**
 * Whether a failed create was answered with a NAMED reason (one with an approved text on [SessionCall.CREATE]): the owner's retry button does nothing for it - the same call, the same
 * account, the same answer (A-SUB wave 1, user decision 4: «إخفاء إعادة المحاولة حين يصل رمز مسمّى»). A failure with no code, an unknown code or a code with no create text keeps its retry.
 */
internal fun createRefusalIsNamed(t: Throwable): Boolean =
    (ReasonCodeRead.of(t) as? ReasonCodeRead.Known)?.let { approvedSessionText(SessionCall.CREATE, it.code) } != null

/**
 * The owner's `createScanSession` answered, but without a session id or without a code - batch 2, item 4 (rule 22; user decision 2026-10-15: «رمز الجلسة
 * الفارغ في الشريحة … الرمز الفارغ أو الغائب حالة صريحة: إنشاء جلسة فشل. لا شريحة رمز فارغة»). Prints `session-create-failed` (which of the two was absent) and returns the
 * existing text the order named as the candidate, `session_error_generic` («تعذّر إنشاء الجلسة» / «Oturum oluşturulamadı»): it says exactly this failure.
 */
internal fun sessionCreateFailedMessage(context: Context, sessionIdPresent: Boolean, codePresent: Boolean): String {
    EvalCardLog.sessionCreateFailed(
        sessionId = if (sessionIdPresent) "present" else "absent",
        code = if (codePresent) "present" else "absent"
    )
    return context.getString(R.string.session_error_generic)
}

/**
 * The text for a failed join or cancel (see the class doc above), and the `session-refused` line that says why this text. Logged WITHOUT passing
 * [t] to `Log.e` (rule 36: an unbounded `toString()` and stack trace): the message is cut to 200 characters, as `createSessionErrorMessage` does.
 */
internal fun sessionErrorMessage(context: Context, call: SessionCall, t: Throwable): String {
    Log.e("NeuralCheck", "${call.wire} failed: ${t.javaClass.simpleName}: ${t.message?.take(200)}")
    val read = ReasonCodeRead.of(t)
    val approved = (read as? ReasonCodeRead.Known)?.let { approvedSessionText(call, it.code) }
    val text: String
    val shown: String
    if (approved != null) {
        text = context.getString(approved.resId)
        shown = approved.key
    } else {
        // a code the server named - known or not - is an answer from the server: the connection reading (an error TEXT match, in the message and the cause chain) is for
        // the failures that carry none, and `networkErrorMessageOrNull` already applies that exception (batch 4, item 1: one reading, not a second check here).
        // The QR join reads it too (batch 5, item 1): a disconnect shows the connection text there as everywhere; only what is NOT a disconnect keeps the chip's own text.
        // the owner's create has always shown its own generic text, a disconnect included (batch 2, item 4): no connection reading there
        val network = if (call == SessionCall.CREATE) null else networkErrorMessageOrNull(context, t)
        if (call == SessionCall.CREATE) {
            text = context.getString(R.string.session_error_generic)
            shown = "session_error_generic"
        } else if (network != null) {
            text = network
            shown = "network"
        } else if (call == SessionCall.JOIN_QR) {
            text = context.getString(R.string.qr_join_failed)
            shown = "qr_join_failed"
        } else {
            text = context.getString(R.string.error_generic)
            shown = "error_generic"
        }
    }
    EvalCardLog.sessionRefused(
        call = call.wire,
        reasonCode = when (read) {
            is ReasonCodeRead.Known -> read.code.wire
            is ReasonCodeRead.Unrecognized -> "unrecognized"
            ReasonCodeRead.Absent -> "absent"
        },
        raw = (read as? ReasonCodeRead.Unrecognized)?.raw,
        functionsException = t is com.google.firebase.functions.FirebaseFunctionsException,
        shown = shown
    )
    return text
}
