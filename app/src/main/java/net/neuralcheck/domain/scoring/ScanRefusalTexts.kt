package net.neuralcheck.domain.scoring

import androidx.annotation.StringRes
import net.neuralcheck.R
import net.neuralcheck.data.remote.ReasonCodeRead
import net.neuralcheck.data.remote.SessionErrorCode

/**
 * What the user reads when an ANALYSIS is refused with a `details.reasonCode` - A-SUB wave 1 (order 2026-10-17; user decisions 2, 3 and 7, the four texts approved verbatim in
 * both languages, decision 90). One matrix, one place (rule 24): `reasonCode` x context. The text comes from this app's resources, never from the server's message (rule 21:
 * the reason is told by the code, not by the message or the Functions code; the same text is sent under two codes and one code under two texts).
 *
 * ── Source of the contract (RELAYED, NOT VERIFIED from this repository - the server is outside it) ──
 *  - the codes, and which context each is sent in: the order of 2026-10-17 from neuralcheck-backend `docs/PROTOCOL_CONTRACT.md` section 6-d-1 and 10-j at `47ec36e`
 *    (branch `asub-wave1`, NOT deployed), and the app-engineer report `app-engineer-asub-round1-fcb1d13-2026-10-17.md` section 4.3 (the matrix this file implements);
 *  - their wire bodies: `backend-engineer-asub-server-questions-3b7fbd2-2026-10-17.md` lines 109-128 (the server's own `probe-wire.js` at `3b7fbd2`, not production).
 *
 * ── The matrix (round 1, section 4.3; a cell that is not here "does not occur" by the server's contract) ──
 * | reasonCode                | personal analysis                | session analysis (the joined phone) |
 * | NO_CREDIT                 | subscription_no_scans (existing) | does not occur                       |
 * | SUBSCRIPTION_EXPIRED      | subscription_expired (K2)        | does not occur                       |
 * | FREE_DEVICE_USED          | free_device_used (K4)            | free_device_used (K4)                |
 * | SHOP_NO_CREDIT            | does not occur                   | shop_no_credit (K3)                  |
 * | SHOP_SUBSCRIPTION_EXPIRED | does not occur                   | shop_subscription_expired (K1)       |
 * | SCAN_REPORT_NOT_AVAILABLE | scan_report_not_available        | does not occur                       |
 * The last row is the order of 2026-10-20 (the user's decision of that date: the Arabic text approved as it is and the Turkish one approved, `DECISIONS-FOR-HASAN-2026-10-18.md` section 2 row 1): the neutral refusal of a
 * stored report that is not the caller's, on the account's own scan. The session path's code (`SESSION_REPORT_NOT_AVAILABLE`) is in no cell on purpose: its text stays the server's, as the order says.
 * The context is the one the ENGINE used for the scan (a scan recorded with a session is a session scan), not something read back from the code: `SHOP_` codes happen to mean
 * "session" but the matrix does not rely on it. What arrives outside a cell - one of the five in the wrong context, or a code this table has never heard of - shows the text that
 * point showed before (the server's own message for the intentional Functions codes, else `error_generic`) and prints `scan-refused ... combination=unexpected`: no guess (rule 22).
 * The session-state codes the analysis now carries (`SESSION_NOT_PARTICIPANT`, `SESSION_NOT_STARTED`, `SESSION_COMPLETED`, ...) have NO approved text in this wave (decision: the app
 * does not have to read them with new texts): they keep the text of before and print `combination=no-approved-text`.
 */
internal enum class RefusalContext(val wire: String) {
    /** The account's own scan (no session recorded for it). */
    PERSONAL("personal"),

    /** A scan that started in a shop session: the joined phone's analysis. */
    SESSION("session")
}

/** An approved analysis text: its resource and the key the log line prints (explicit, R8-safe). The ONE place these resources are named for an analysis refusal (the five of A-SUB wave 1, and the 2026-10-20 one). */
internal enum class ScanRefusalText(@StringRes val resId: Int, val key: String) {
    /** Existing text («لا يوجد رصيد فحوصات. يرجى شراء اشتراك.»): the personal no-credit refusal showed it before, and the pre-scan gate still does. */
    NO_CREDIT(R.string.subscription_no_scans, "subscription_no_scans"),

    /** K2. */
    SUBSCRIPTION_EXPIRED(R.string.subscription_expired, "subscription_expired"),

    /** K4. */
    FREE_DEVICE_USED(R.string.free_device_used, "free_device_used"),

    /** K3 (user decision 7: «نصّ K3 فقط» for the two-sessions-one-credit edge case). */
    SHOP_NO_CREDIT(R.string.shop_no_credit, "shop_no_credit"),

    /** K1 (user decision 2: «تواصل مع الإدارة» as it is). */
    SHOP_SUBSCRIPTION_EXPIRED(R.string.shop_subscription_expired, "shop_subscription_expired"),

    /**
     * The neutral refusal of a stored report that is not the caller's (user decision of 2026-10-20: the Arabic text as it is, the Turkish one approved). It says nothing about whether a report exists or whose it is,
     * and it is the only text the user reads for [net.neuralcheck.data.remote.SessionErrorCode.SCAN_REPORT_NOT_AVAILABLE]: never the server's message.
     */
    SCAN_REPORT_NOT_AVAILABLE(R.string.scan_report_not_available, "scan_report_not_available")
}

/** The matrix above. */
internal val ANALYSIS_REFUSAL_TEXTS: Map<RefusalContext, Map<SessionErrorCode, ScanRefusalText>> = mapOf(
    RefusalContext.PERSONAL to mapOf(
        SessionErrorCode.NO_CREDIT to ScanRefusalText.NO_CREDIT,
        SessionErrorCode.SUBSCRIPTION_EXPIRED to ScanRefusalText.SUBSCRIPTION_EXPIRED,
        SessionErrorCode.FREE_DEVICE_USED to ScanRefusalText.FREE_DEVICE_USED,
        SessionErrorCode.SCAN_REPORT_NOT_AVAILABLE to ScanRefusalText.SCAN_REPORT_NOT_AVAILABLE
    ),
    RefusalContext.SESSION to mapOf(
        SessionErrorCode.FREE_DEVICE_USED to ScanRefusalText.FREE_DEVICE_USED,
        SessionErrorCode.SHOP_NO_CREDIT to ScanRefusalText.SHOP_NO_CREDIT,
        SessionErrorCode.SHOP_SUBSCRIPTION_EXPIRED to ScanRefusalText.SHOP_SUBSCRIPTION_EXPIRED
    )
)

/** The scan refusals (the matrix's rows): the five of A-SUB wave 1 and, since 2026-10-20, the neutral stored-report one. */
internal val SCAN_REFUSAL_CODES: Set<SessionErrorCode> = setOf(
    SessionErrorCode.NO_CREDIT,
    SessionErrorCode.SUBSCRIPTION_EXPIRED,
    SessionErrorCode.FREE_DEVICE_USED,
    SessionErrorCode.SHOP_NO_CREDIT,
    SessionErrorCode.SHOP_SUBSCRIPTION_EXPIRED,
    SessionErrorCode.SCAN_REPORT_NOT_AVAILABLE
)

/** The approved text [code] has in [context], or null when its cell is empty. */
internal fun analysisRefusalText(context: RefusalContext, code: SessionErrorCode): ScanRefusalText? =
    ANALYSIS_REFUSAL_TEXTS.getValue(context)[code]

/** How a refusal's `reasonCode` sits against the matrix (what `scan-refused combination=` prints). */
internal enum class RefusalCombination(val wire: String) {
    /** The code has an approved text in this context. */
    EXPECTED("expected"),

    /** One of the five in the wrong context, a code the analysis is not expected to send, or a code this app does not know. */
    UNEXPECTED("unexpected"),

    /** A session-state code the analysis now carries ([SessionErrorCode.analyzeCodes]) with no approved text in this wave. */
    NO_APPROVED_TEXT("no-approved-text")
}

/** The combination of [read] in [context]; null for [ReasonCodeRead.Absent] (no reasonCode: not a classified refusal). */
internal fun refusalCombination(context: RefusalContext, read: ReasonCodeRead): RefusalCombination? = when (read) {
    ReasonCodeRead.Absent -> null
    is ReasonCodeRead.Unrecognized -> RefusalCombination.UNEXPECTED
    is ReasonCodeRead.Known -> when {
        analysisRefusalText(context, read.code) != null -> RefusalCombination.EXPECTED
        read.code in SCAN_REFUSAL_CODES -> RefusalCombination.UNEXPECTED
        read.code in SessionErrorCode.analyzeCodes -> RefusalCombination.NO_APPROVED_TEXT
        else -> RefusalCombination.UNEXPECTED
    }
}
