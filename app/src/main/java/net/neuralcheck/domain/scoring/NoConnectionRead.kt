package net.neuralcheck.domain.scoring

import android.content.Context
import net.neuralcheck.data.remote.ReasonCodeRead

/**
 * The ONE reading of «this failure is no connection» (batch 4, item 1; user decision 1, 2026-10-16, option (b); rule 24: one exported source, not a copy per caller).
 * Six places decide on it - the typed join and the QR join (batch 5, item 1), the owner's cancel and the customer's leave (`SessionMessages.kt`, `SessionLeaver.kt`), the
 * evaluation submission (`EvaluationSubmitter.kt`) and the analysis call (`AiScoringEngine.kt`) - and all of them come through here: the ones that show a text through
 * [networkErrorMessageOrNull] (the two joins and the owner's cancel by `sessionErrorMessage`, the evaluation submission, the analysis call), the leave through
 * [LeaveFailureRead][net.neuralcheck.data.remote.LeaveFailureRead]. There is no second way to read it.
 *
 * What it reads, and why (rule 21: the error TEXT decides, never the exception class - a connectivity failure can surface as an `IOException`, an
 * `UnknownHostException`, a Firebase Functions SDK exception, etc., and no single class covers all of them without also covering unrelated causes):
 *  - the phrases of [messageLooksLikeNoNetwork];
 *  - in the failure's message AND in the messages of its `cause` chain, [NO_CONNECTION_CAUSE_DEPTH_MAX] deep at most. Reason, measured (batch 3, item 1): the
 *    Firebase Functions SDK (firebase-functions 20.4.0, `FirebaseFunctions$2.onFailure`, read from its bytecode) turns an `IOException` of the transport into
 *    `FirebaseFunctionsException("INTERNAL", INTERNAL, null, cause = the IOException)` - its own message is the bare code name, the connection text is in the cause.
 *    Reading the message alone never sees a real offline call (it still sees the bare `IOException` that older tests built). The reading used to be
 *    message-only everywhere but the leave: a real disconnect showed «حدث خطأ غير متوقع» at the other four;
 *  - a failure that carries a server `reasonCode` (known or not) is never «no connection»: the server answered, so the phone is not offline, whatever the text of the
 *    refusal says. ([ReasonCodeRead] is the same reading `sessionErrorMessage` uses for its own texts.)
 *
 * NOT «no connection»: a timeout of the call (`Callable 'x' timed out after Nms`) and a socket READ timeout (the SDK's `DEADLINE_EXCEEDED` whose cause's text names none of the
 * phrases) - the request may have reached the server; an `IOException` whose text names none of the phrases (a reset connection, a TLS failure); the server's own `internal`;
 * any failure with a `reasonCode`.
 *
 * IS «no connection»: a socket CONNECT timeout (batch 5, item 2; user decision 2, 2026-10-16: «مهلة الاتّصال («failed to connect») تُعدّ «لا اتّصال» فيغادر محلّياً، ومهلة القراءة لا
 * تُعدّ»). The SDK wraps it as `DEADLINE_EXCEEDED` exactly as it wraps the read timeout, and what tells them apart is the TEXT of the cause - `failed to connect to <host> ... after Nms`,
 * which the phrase `failed to connect` reads - never the code (rule 21): no connection was ever made, so the request cannot have arrived. Nothing here changed for it in batch 5:
 * the reading already did this, and the change is the tests that pin it.
 */
internal fun failureSaysNoConnection(t: Throwable): Boolean {
    if (ReasonCodeRead.of(t) !is ReasonCodeRead.Absent) return false
    var current: Throwable? = t
    var depth = 0
    while (current != null && depth < NO_CONNECTION_CAUSE_DEPTH_MAX) {
        if (messageLooksLikeNoNetwork(current.message.orEmpty().lowercase())) return true
        val next = current.cause
        current = if (next === current) null else next
        depth++
    }
    return false
}

/** How deep the `cause` chain is read: enough for the SDK's one wrapper and an OkHttp or JVM exception under it, and a bound against a cycle. */
internal const val NO_CONNECTION_CAUSE_DEPTH_MAX = 8

/**
 * Order 2026-09-24 (القرار ٨٠/٨٢ البند ٦). Returns the localized, fixed «no connection» text when [failureSaysNoConnection] says [t] is one, `null` otherwise (the
 * caller then shows what it showed before for any other failure). Reuses the detection idiom of
 * [net.neuralcheck.presentation.viewmodel.LoginViewModel.mapFirebaseError] for the same real-world cause (`"network" in msg`, lowercased), extended with the plain
 * JVM/Android phrasings a Functions-callable failure is more likely to surface when there is genuinely no connectivity - see [messageLooksLikeNoNetwork].
 */
internal fun networkErrorMessageOrNull(context: Context, t: Throwable): String? =
    if (failureSaysNoConnection(t)) context.getString(net.neuralcheck.R.string.login_error_network) else null

/**
 * The phrases read as «no connection» in an error text - the one list (rule 24). `"network"` alone (LoginViewModel's exact phrase) matches Firebase Auth's own
 * crafted message ("A network error ... has occurred") but NOT the plain JVM/Android exceptions a Firebase *Functions* callable failure is more likely to surface when
 * there is genuinely no connectivity - `java.net.UnknownHostException`'s message is "Unable to resolve host ...: No address associated with hostname", which contains
 * neither "network" nor any word derived from it. Several known real phrasings (still error TEXT, not exception TYPE - rule 21) rather than one avoid silently missing
 * the single most common real-world case. Private: only [failureSaysNoConnection] reads it, so nothing can read a text for these phrases by another road.
 * [lowercasedMessage] is an error message already lower-cased by the caller.
 */
private fun messageLooksLikeNoNetwork(lowercasedMessage: String): Boolean =
    "network" in lowercasedMessage ||
        "unable to resolve host" in lowercasedMessage ||
        "no address associated with hostname" in lowercasedMessage ||
        "unknownhostexception" in lowercasedMessage ||
        "failed to connect" in lowercasedMessage
