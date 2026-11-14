package net.neuralcheck.data.remote

import com.google.firebase.functions.FirebaseFunctionsException
import com.google.gson.annotations.SerializedName

/**
 * The wire values of the scan-session contract between this app and the server — the ONE source of each literal (rule 24).
 * Nothing else in the app spells `"cancelled"`, `"left_by_customer"` or `"SESSION_COMPLETED"`: every reader and writer goes through
 * the enums below, and the server-side facts they mirror are named here with their source.
 *
 * Source of every value below (batch 2, 2026-10-15): the read-only diagnosis of the backend agent at neuralcheck-backend `bbb90a1`
 * (= the deployed `d14c558` for the session files), relayed by the coordinator in the order of this batch (Appendix A, answers 1-10) —
 * RELAYED, NOT VERIFIED from this repository (the server is outside it). The server lines it cites: `sessionNames.js:26-32` (the five
 * statuses), `:54-58` (the three reasons), `joinScanSession.js:101` and `cancelScanSession.js:155` (every refusal carries
 * `details.reasonCode`), `joinScanSession.js:60-65` and `cancelScanSession.js:78-92` (codes and messages). A difference found on the
 * server later is a difference to report, not to patch here.
 */

/**
 * What the server writes into `scanSessions.status`: FIVE values and no other (answer 9). `waiting`, `joined`, `in_progress` and `done` are
 * written by nobody — the owner's listener used to read all four and the app now does not (batch 2, item 3). A value outside these five
 * is [Read.Unknown], never one of the five; a missing or non-text value is [Read.Absent] (rules 22, 25).
 */
enum class SessionStatus(val wire: String) {
    /** `createScanSession` writes it; only the owner can cancel a session in it. */
    PENDING("pending"),

    /** `joinScanSession` writes it at the join (there is no `joined` status): the owner or the joined device can cancel. */
    SCANNING("scanning"),

    /** `analyzeDevice` writes it when the scan's analysis is done. */
    COMPLETED("completed"),

    /** Written by the expiry paths (and the scheduled sweep). */
    EXPIRED("expired"),

    /** `cancelScanSession` writes it, with `cancelledAt` and `cancelReason`. */
    CANCELLED("cancelled");

    /** How a raw `status` reads. A named state for what is not one of the five (rule 22): it is printed, and no caller maps it onto a known value. */
    sealed interface Read {
        data class Known(val status: SessionStatus) : Read

        /** A text that is none of the five: [raw] is the server's own text, bounded by the printer. */
        data class Unknown(val raw: String) : Read

        /** The key is missing, null, or not a text. */
        data object Absent : Read
    }

    companion object {
        /** Exact match (the server writes lower case): `Scanning` is [Read.Unknown], not [SCANNING]. */
        fun read(raw: Any?): Read {
            val text = raw as? String ?: return Read.Absent
            val known = values().firstOrNull { it.wire == text } ?: return Read.Unknown(text)
            return Read.Known(known)
        }
    }
}

/** The three reasons `cancelScanSession` accepts (answer 1) and writes to `cancelReason` (answer 4). The role decides which are allowed (`sessionNames.js:64-67`). */
enum class CancelReason(val wire: String) {
    /** Joiner only: the customer declined the consent screen (the screen is not built yet - D1: it is wired in the order that builds it). */
    CONSENT_DECLINED("consent_declined"),

    /** Joiner only: the customer left (the leave confirmation, Back from the scan screen's stop dialog). */
    LEFT_BY_CUSTOMER("left_by_customer"),

    /** Owner only. */
    CANCELLED_BY_OWNER("cancelled_by_owner");

    /** How a raw `cancelReason` reads, with the same three named states as [SessionStatus.Read]. */
    sealed interface Read {
        data class Known(val reason: CancelReason) : Read
        data class Unknown(val raw: String) : Read
        data object Absent : Read
    }

    companion object {
        fun read(raw: Any?): Read {
            val text = raw as? String ?: return Read.Absent
            val known = values().firstOrNull { it.wire == text } ?: return Read.Unknown(text)
            return Read.Known(known)
        }
    }
}

/**
 * The `details.reasonCode` values the session callables send (answer 3) and, since the A-SUB wave (2026-10-17), the scan-refusal ones, and since 2026-10-20 [SCAN_REPORT_NOT_AVAILABLE] and [SHOP_PLAN_REQUIRED]. Which call throws which is in
 * [joinCodes] / [cancelCodes] / [createCodes] / [analyzeCodes]; the text each one shows is decided in `SessionMessages.kt` (the session calls) and
 * `ScanRefusalTexts.kt` (the analysis), one table each, and only for the codes the user approved a text for.
 *
 * The scan-refusal codes ([NO_CREDIT] .. [SHOP_SUBSCRIPTION_EXPIRED]) and [SESSION_NOT_STARTED] are RELAYED, NOT VERIFIED from this repository (the server is outside it): the order of
 * 2026-10-17 (A-SUB wave 1) names them from neuralcheck-backend `docs/PROTOCOL_CONTRACT.md` section 6-d-1 and 10-j at `47ec36e` (branch `asub-wave1`, NOT deployed), and their wire bodies
 * come from the backend report `backend-engineer-asub-server-questions-3b7fbd2-2026-10-17.md` lines 109-128 (the server's own `probe-wire.js` at `3b7fbd2`, not production).
 * A difference found on the server later is a difference to report, not to patch here.
 *
 * Wire strings are explicit, never `Enum.name` (R8 renames enum classes in the release build).
 */
enum class SessionErrorCode(val wire: String) {
    SESSION_CODE_INVALID("SESSION_CODE_INVALID"),
    SESSION_ALREADY_JOINED("SESSION_ALREADY_JOINED"),
    SESSION_CANCELLED("SESSION_CANCELLED"),
    SESSION_COMPLETED("SESSION_COMPLETED"),
    SESSION_EXPIRED("SESSION_EXPIRED"),
    SESSION_ANALYSIS_IN_FLIGHT("SESSION_ANALYSIS_IN_FLIGHT"),
    SESSION_EXPIRES_AT_INVALID("SESSION_EXPIRES_AT_INVALID"),
    SESSION_STATUS_UNKNOWN("SESSION_STATUS_UNKNOWN"),
    SESSION_NOT_PARTICIPANT("SESSION_NOT_PARTICIPANT"),
    SESSION_CLAIM_AGE_UNKNOWN("SESSION_CLAIM_AGE_UNKNOWN"),
    CANCEL_NOT_AUTHENTICATED("CANCEL_NOT_AUTHENTICATED"),
    CANCEL_SESSION_ID_REQUIRED("CANCEL_SESSION_ID_REQUIRED"),
    CANCEL_REASON_REQUIRED("CANCEL_REASON_REQUIRED"),
    CANCEL_REASON_INVALID("CANCEL_REASON_INVALID"),
    CANCEL_REASON_NOT_ALLOWED_FOR_ROLE("CANCEL_REASON_NOT_ALLOWED_FOR_ROLE"),

    /** `analyzeDevice` on a session whose status is `pending` (the join never happened): new in the A-SUB wave, no approved text. */
    SESSION_NOT_STARTED("SESSION_NOT_STARTED"),

    /** A personal analysis refused: the account has no scan credit left. */
    NO_CREDIT("NO_CREDIT"),

    /** A personal analysis refused: the account's subscription ended. */
    SUBSCRIPTION_EXPIRED("SUBSCRIPTION_EXPIRED"),

    /** A free-plan analysis refused: the scanned device already used the free scan (personal or session). */
    FREE_DEVICE_USED("FREE_DEVICE_USED"),

    /** A session analysis refused, or `createScanSession` refused: the shop (the session's owner) has no scan credit left. */
    SHOP_NO_CREDIT("SHOP_NO_CREDIT"),

    /** A session analysis, the join or `createScanSession` refused: the shop's subscription ended. */
    SHOP_SUBSCRIPTION_EXPIRED("SHOP_SUBSCRIPTION_EXPIRED"),

    /**
     * `setSessionPurpose` after the session left `pending` (the customer's phone joined): the purpose can no longer change. Contract A2 (copy of neuralcheck-backend@8e5c426
     * `docs/PROTOCOL_CONTRACT.md`): «رمز رفض `setSessionPurpose` بعد الانضمام `PURPOSE_SESSION_NOT_PENDING`» - `[ق]` E5, approved 2026-10-15. RELAYED, NOT VERIFIED from this repository. The text the
     * technician reads for it is NOT written (contract A2: «نصّ الرفض الذي يراه المستخدم لم يُكتب»), so it is in no text table.
     */
    PURPOSE_SESSION_NOT_PENDING("PURPOSE_SESSION_NOT_PENDING"),

    /**
     * `setSessionPurpose` with `maintenance_before` / `maintenance_after` and NO `defectType` key (or an `undefined`): contract A2 addendum of 2026-10-22 (copy of neuralcheck-backend@f3c1bab, `PURPOSE_REFUSAL`,
     * proposal A2, pending the user's approval of the names - ق33). This app never sends such a request ([SessionPurposeWire.request] refuses it locally), so the code is named for completeness: a server that
     * answers it anyway is read as itself, not as an unrecognized code. RELAYED, NOT VERIFIED from this repository. No text is written for it.
     */
    DEFECT_TYPE_REQUIRED("DEFECT_TYPE_REQUIRED"),

    /** `setSessionPurpose` with a `defectType` that is not one of the five `DEFECT_TYPE` values, exactly as spelled (a lower-case `"battery"`, `"OTHER"`, an empty text, a number, `null`): same source and standing as [DEFECT_TYPE_REQUIRED]. */
    DEFECT_TYPE_INVALID("DEFECT_TYPE_INVALID"),

    /** `setSessionPurpose` with a `defectType` key and `normal` (even a `null` counts as sent): same source and standing as [DEFECT_TYPE_REQUIRED]. */
    DEFECT_TYPE_NOT_APPLICABLE("DEFECT_TYPE_NOT_APPLICABLE"),

    /**
     * `setSessionPurpose` with a `purpose` that is none of the three the server accepts at creation - the two old battery values (`battery_before`, `battery_after`) among them: contract A2 addendum, copy @f3c1bab
     * («فنداء `setSessionPurpose` بأيّ منهما ⟵ `SET_PURPOSE_INVALID`»), and one of the server's golden reply samples (`setSessionPurpose.refused.purpose-invalid.battery-before`, found by
     * `MaintenancePairWireSamplesTest`: before it, a server that answered this was read as «unrecognized» and the log lost the code). This app never sends one - [SessionPurposeWire.request] builds only the three - so the
     * code is named for completeness. RELAYED, NOT VERIFIED from this repository. No text is written for it.
     */
    SET_PURPOSE_INVALID("SET_PURPOSE_INVALID"),

    /**
     * `setSessionPurpose` for a shop that lacks the part the purpose needs: `purposes` for any purpose but «normal», and also `batteryBeforeAfter` for `maintenance_*` (decision of Hasan, Q31, 2026-10-22; contract A2, copy
     * @f3c1bab, `PURPOSE_REFUSAL`/`PART_NOT_ENABLED`, `failed-precondition`; the message is the code itself and the part that was missing is in the SERVER's log, not in the body). The app does not send a purpose its shop does not
     * have ([ProtocolPartsRead.maintenanceOffered] decides what is drawn), so this refusal is a safety net - the shop's parts changed after the session was created, or a part the app read is not the one the server reads.
     * The text the technician reads for it is row ص21 of `TEXTS-FOR-APPROVAL-MAINTENANCE-PAIRS-2026-10-22.md`, pending: `PurposeChipLabels.partNotEnabled`. RELAYED, NOT VERIFIED from this repository (the server's half is not deployed).
     */
    PURPOSE_PART_NOT_ENABLED("PURPOSE_PART_NOT_ENABLED"),

    /**
     * The account's OWN scan (no session) refused because the stored report it asks for is not the caller's to see: permission-denied with this `details.reasonCode` and one neutral text that says nothing about whether the
     * report exists or whose it is (order of 2026-10-20, relayed from neuralcheck-backend@05275ba `functions/src/analysis/storedReportAccess.js:23-31`). RELAYED, NOT VERIFIED from this repository: the order does not name the callable, so
     * the only place this app can show it is the analysis' refusal text ([net.neuralcheck.domain.scoring.analysisErrorMessage]) - the one reading of a permission-denied refusal on a personal scan. The SESSION path has its own code,
     * `SESSION_REPORT_NOT_AVAILABLE`, whose text stays the server's and which is deliberately NOT in this enum (the order: it exists and is not to be changed).
     */
    SCAN_REPORT_NOT_AVAILABLE("SCAN_REPORT_NOT_AVAILABLE"),

    /**
     * `createScanSession` refused: the shop's plan does not allow a session (order of 2026-10-20, relayed from neuralcheck-backend@05275ba `functions/src/sessions/createScanSession.js:108-109` and `sessionNames.js:139`; on the
     * deployed server 8e5c426). RELAYED, NOT VERIFIED from this repository. Before this code was named here it read as [ReasonCodeRead.Unrecognized] and the owner saw the generic create text with a retry button.
     */
    SHOP_PLAN_REQUIRED("SHOP_PLAN_REQUIRED");

    companion object {
        /** `joinScanSession` (answers 3 and 7: five of the six named codes, plus the two it adds, plus [SHOP_SUBSCRIPTION_EXPIRED] since A-SUB: order 2026-10-17, relayed); `SESSION_ANALYSIS_IN_FLIGHT` is the cancel call's alone. */
        val joinCodes: Set<SessionErrorCode> = setOf(
            SESSION_CODE_INVALID, SESSION_ALREADY_JOINED, SESSION_CANCELLED, SESSION_COMPLETED, SESSION_EXPIRED,
            SESSION_EXPIRES_AT_INVALID, SESSION_STATUS_UNKNOWN, SHOP_SUBSCRIPTION_EXPIRED
        )

        /** `cancelScanSession` (answer 3, table b). */
        val cancelCodes: Set<SessionErrorCode> = setOf(
            CANCEL_NOT_AUTHENTICATED, CANCEL_SESSION_ID_REQUIRED, CANCEL_REASON_REQUIRED, CANCEL_REASON_INVALID,
            SESSION_NOT_PARTICIPANT, CANCEL_REASON_NOT_ALLOWED_FOR_ROLE, SESSION_COMPLETED, SESSION_EXPIRED,
            SESSION_EXPIRES_AT_INVALID, SESSION_STATUS_UNKNOWN, SESSION_ANALYSIS_IN_FLIGHT, SESSION_CLAIM_AGE_UNKNOWN
        )

        /**
         * `createScanSession` since A-SUB (order 2026-10-17, relayed from `PROTOCOL_CONTRACT.md` 6-d-1 at `47ec36e`): its two refusals carry `details.reasonCode`; before, neither carried `details`.
         * Plus [SHOP_PLAN_REQUIRED] (order 2026-10-20, relayed from neuralcheck-backend@05275ba `createScanSession.js:108-109`): the plan refusal.
         */
        val createCodes: Set<SessionErrorCode> = setOf(SHOP_NO_CREDIT, SHOP_SUBSCRIPTION_EXPIRED, SHOP_PLAN_REQUIRED)

        /** `setSessionPurpose` (A-P6-03; contract A2, relayed): the lock after the join, and (A2 addendum of 2026-10-22 and, since Q31, the part check; copy @f3c1bab) the refusals of the fault type and of the parts. */
        val purposeCodes: Set<SessionErrorCode> = setOf(
            PURPOSE_SESSION_NOT_PENDING, DEFECT_TYPE_REQUIRED, DEFECT_TYPE_INVALID, DEFECT_TYPE_NOT_APPLICABLE, SET_PURPOSE_INVALID, PURPOSE_PART_NOT_ENABLED
        )

        /**
         * `analyzeDevice` (order 2026-10-17, relayed): the five scan refusals, and for a SESSION scan the session-state ones (`SESSION_NOT_STARTED` is new; the others
         * existed as join/cancel codes). Which of them this app has an approved text for is `ScanRefusalTexts.kt`'s matrix, not this set. Plus [SCAN_REPORT_NOT_AVAILABLE] (order 2026-10-20): the
         * personal scan's neutral stored-report refusal - listed here because the analysis' refusal text is where the app reads it, not because the order names the callable.
         */
        val analyzeCodes: Set<SessionErrorCode> = setOf(
            NO_CREDIT, SUBSCRIPTION_EXPIRED, FREE_DEVICE_USED, SHOP_NO_CREDIT, SHOP_SUBSCRIPTION_EXPIRED, SCAN_REPORT_NOT_AVAILABLE,
            SESSION_NOT_PARTICIPANT, SESSION_NOT_STARTED, SESSION_COMPLETED, SESSION_CANCELLED, SESSION_EXPIRED,
            SESSION_EXPIRES_AT_INVALID, SESSION_STATUS_UNKNOWN
        )
    }
}

/**
 * What an exception says about WHY the server refused: [details][FirebaseFunctionsException.details]`.reasonCode`, and nothing else.
 *
 * The Functions code (`NOT_FOUND`, `FAILED_PRECONDITION`, ...) and the message text are deliberately NOT read (rule 21: the reason is told
 * by the server's own text, not by an exception type): for the SAME `reasonCode` the server sends a different Functions code and a different
 * message depending on the call and, in the join, on the phase (lookup or transaction) — answer 3 — so matching either would classify
 * one reason as two, or two as one.
 *
 * Three named states (rule 22): [Known], [Unrecognized] (the server sent a text this app has no case for), [Absent] (no `reasonCode` at all:
 * a refusal such as «الكود مطلوب» or `internal`, a transport failure, any other throwable).
 */
sealed interface ReasonCodeRead {
    data class Known(val code: SessionErrorCode) : ReasonCodeRead
    data class Unrecognized(val raw: String) : ReasonCodeRead
    data object Absent : ReasonCodeRead

    companion object {
        /** The key inside `details` (answer 3: `details.reasonCode`). */
        const val DETAILS_KEY = "reasonCode"

        fun of(t: Throwable): ReasonCodeRead {
            val details = (t as? FirebaseFunctionsException)?.details as? Map<*, *> ?: return Absent
            val raw = details[DETAILS_KEY] as? String ?: return Absent
            val known = SessionErrorCode.values().firstOrNull { it.wire == raw } ?: return Unrecognized(raw)
            return Known(known)
        }
    }
}

/**
 * The success answer of `cancelScanSession` (answer 2): `{sessionId, status: "cancelled", alreadyCancelled}`. Every field is nullable:
 * a field the answer did not carry stays absent (rule 22), and [outcome] says what that means.
 */
data class CancelSessionResult(
    @SerializedName("sessionId") val sessionId: String? = null,
    @SerializedName("status") val status: String? = null,
    @SerializedName("alreadyCancelled") val alreadyCancelled: Boolean? = null
) {
    sealed interface Outcome {
        /** The session is cancelled. [alreadyCancelled] is the server's own flag, `null` when the answer did not carry it. */
        data class Cancelled(val alreadyCancelled: Boolean?) : Outcome

        /** The call succeeded but the answer does not say the session is cancelled (a different or missing `status`): not a success. */
        data class Unexpected(val status: SessionStatus.Read) : Outcome
    }

    fun outcome(): Outcome = when (val read = SessionStatus.read(status)) {
        is SessionStatus.Read.Known ->
            if (read.status == SessionStatus.CANCELLED) Outcome.Cancelled(alreadyCancelled) else Outcome.Unexpected(read)
        else -> Outcome.Unexpected(read)
    }
}

/**
 * Thrown by [joinScanSessionWithDeviceIdentity] when the `joinScanSession` callable succeeded but its answer carries no session id - the key is missing or null
 * ([absent] = true), or the text is blank ([absent] = false). Not a join (batch 3, item 3, rule 22): both join entry points show their existing text for a failure
 * that has no approved text. The message names no connection phrase on purpose (`networkErrorMessageOrNull` must not read it as «no connection»).
 */
class JoinAnswerWithoutSessionIdException(val absent: Boolean) :
    RuntimeException("joinScanSession answered without a session id")

/** Thrown by [FirebaseAnalysisService.cancelScanSession] when the callable succeeded but the answer is not «cancelled» (see [CancelSessionResult.Outcome.Unexpected]). */
class UnexpectedCancelAnswerException(val status: SessionStatus.Read) :
    RuntimeException("cancelScanSession answered without status=cancelled")
