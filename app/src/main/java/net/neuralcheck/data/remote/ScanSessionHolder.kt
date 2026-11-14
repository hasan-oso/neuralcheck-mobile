package net.neuralcheck.data.remote

import net.neuralcheck.util.EvalCardLog
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Carries the Cloud Functions-issued `sessionId` from the moment
 * [FirebaseAnalysisService.joinScanSession] resolves it (in either join
 * entry point: the typed-code screen's view model or the QR scanner's)
 * to the moment the scoring pipeline fires the
 * `analyzeDevice` callable inside [net.neuralcheck.domain.scoring.AiScoringEngine]
 * - through the per-scan record, see the contract below.
 *
 * Why a mutable singleton instead of threading the value through
 * ActiveScanViewModel → StressTestService → GenerateReportUseCase →
 * ScoringEngine? The customer device that scans a shop-owner's QR has
 * no Firebase Auth user: `requireAuth()` in FirebaseAnalysisService will
 * reject `analyzeDevice` unless the request is tagged with a valid
 * sessionId instead. A holder lets us plumb that one extra string
 * without rewriting the interface of every ScoringEngine implementation
 * or poking a nullable through every DTO along the way.
 *
 * The contract (rewritten 2026-10-15: the first version of this comment predates the second join entry
 * point, the per-scan record and the "still processing" answer):
 *  - Each join entry point calls [setFromJoin] right after the
 *    `joinScanSession` Cloud Function returns (through
 *    `joinScanSessionWithDeviceIdentity`).
 *  - A-P6-07: the repeat scan's child session reaches this holder a third way, [adoptRepeatChild] - the scanned phone asked the server for it (`startRepeatScan`) instead of joining by a code.
 *  - `StressTestService.startScan` copies this holder's session under the scan's id
 *    ([ScanSessionRecords.recordAtScanStart]). From then on the scan's analysis
 *    (`AiScoringEngine`) and its evaluation draft read THAT record ([ScanSessionRecords.sessionFor]),
 *    not this holder - a session joined after the scan started is not the scan's.
 *  - This holder is read by the pre-scan screen's gate, the card's gate
 *    (`TechnicianEvaluationViewModel`) and the record at scan start.
 *  - The scoring engine calls [clear] after a FINAL analysis answer, and only when the holder still holds
 *    the session that analysis used (`shouldClearSessionAfterAnalysis`): a "still processing" answer
 *    does not clear (the retry is for the same scan), nor does a later session joined meanwhile.
 *    Sign-out clears it too. So a follow-up scan by a subsequently-authenticated user doesn't inherit
 *    the previous anonymous session.
 *
 * Order 2026-10-10 (user decision, permanent named log lines): every write and
 * every clear prints one [EvalCardLog] line (booleans and hashed ids only), and
 * [clear] takes a [ClearReason] from every caller so the line says WHY — not a
 * class name from a stack trace, which R8 renames in the release build.
 */
@Singleton
class ScanSessionHolder @Inject constructor() {

    /**
     * feat/technician-evaluation: the holder now carries the full join
     * context, not just the sessionId string — the evaluation card needs
     * [evaluationEnabled] to decide visibility, and keeping [ownerId]
     * costs nothing (it is NEVER sent by the client; attribution stays
     * server-side via sessionId).
     *
     * [evaluationEnabled] is `null` when the join answer did not carry it (order 2026-10-15, rule 22):
     * the absence is kept, not read as `false`; the gate that shows the card is the reader that decides
     * what an absence means, and it says so on its log line.
     */
    data class ScanSessionInfo(
        val sessionId: String,
        val ownerId: String,
        val evaluationEnabled: Boolean?
    )

    /** Why the holder was emptied; printed by the [clear] line. Wire strings are explicit (R8-safe). */
    enum class ClearReason(val wire: String) {
        /** [net.neuralcheck.data.auth.AuthManager.signOut]. */
        SIGN_OUT("sign-out"),

        /** [net.neuralcheck.domain.scoring.AiScoringEngine.calculateScore], after a final analysis answer. */
        ANALYSIS_COMPLETE("analysis-complete"),

        /** Batch 2, item 2 (D2): the customer left the session ([SessionLeaver]); emptied only if the holder still held THAT session. */
        LEFT_BY_CUSTOMER("left-by-customer"),

        /** A-P6-12: the customer pressed «لا أوافق» on the consent screen ([SessionLeaver.declineConsent]); emptied only if the holder still held THAT session. */
        CONSENT_DECLINED("consent-declined"),

        /** Batch 2, item 2 (D5): the owner cancelled the session this device was scanning in ([CustomerSessionWatch]); emptied only if the holder still held THAT session. */
        CANCELLED_BY_OWNER("cancelled-by-owner")
    }

    /** What [clearIfHolding] did. Wire strings are explicit (R8-safe); [SessionLeaver] prints them. */
    enum class ClearIfHoldingResult(val wire: String) {
        CLEARED("cleared"),

        /** The holder held a DIFFERENT session (one joined after the one being left): left untouched. */
        KEPT_OTHER_SESSION("kept-other-session"),

        /** The holder was already empty. */
        WAS_EMPTY("was-empty")
    }

    @Volatile
    private var info: ScanSessionInfo? = null

    /** Legacy single-string setter — kept for callers that only have a
     *  sessionId; it has no caller (ScanSessionHolderWriterSitesTest). It carries NO join answer, so
     *  evaluationEnabled is absent (`null`), not `false` (order 2026-10-15, rule 22) — the card does not show. */
    fun set(sessionId: String?) {
        val previous = info
        info = sessionId?.takeIf { it.isNotBlank() }
            ?.let { ScanSessionInfo(it, ownerId = "", evaluationEnabled = null) }
        EvalCardLog.holderSet(
            via = EvalCardLog.VIA_LEGACY,
            sessionId = sessionId,
            ownerId = null,
            evaluationEnabled = EvalCardLog.Flag.ABSENT,
            stored = info != null,
            previousEvaluationEnabled = previous?.let { EvalCardLog.Flag.of(it.evaluationEnabled) }
        )
    }

    /** Full-context setter used right after a successful joinScanSession. */
    @Synchronized
    fun setFromJoin(result: JoinSessionResult) {
        val previous = info
        info = result.sessionId?.takeIf { it.isNotBlank() }?.let {
            ScanSessionInfo(
                sessionId = it,
                ownerId = result.ownerId,
                evaluationEnabled = result.evaluationEnabled
            )
        }
        EvalCardLog.holderSet(
            via = EvalCardLog.VIA_JOIN,
            sessionId = result.sessionId,
            ownerId = result.ownerId,
            evaluationEnabled = EvalCardLog.Flag.of(result.evaluationEnabled),
            stored = info != null,
            previousEvaluationEnabled = previous?.let { EvalCardLog.Flag.of(it.evaluationEnabled) }
        )
    }

    /**
     * The scanned phone was handed the repeat scan's CHILD session by `startRepeatScan` (A-P6-07): no code was typed and nothing was joined (PLAN-06:639, «فلا رمز ولا انضمام ولا تسجيل خروج»), so the holder takes the
     * child's id and nothing else - the owner is not known to this device (the server's answer names none, and the id is never sent by the client) and `evaluationEnabled` is ABSENT, not false: the child session asks for
     * no form (PLAN-06:655, «الاستمارة في الفحص المكرّر: لا تُطلب») and the card's gate reads an absence as "no card" (order 2026-10-15, rule 22). The scan that starts next records this session
     * ([ScanSessionRecords.recordAtScanStart]) like any session's; a blank id empties the slot, as [setFromJoin] does. Prints one `holder-set via=repeat` line.
     */
    @Synchronized
    fun adoptRepeatChild(childSessionId: String) {
        val previous = info
        info = childSessionId.takeIf { it.isNotBlank() }?.let { ScanSessionInfo(it, ownerId = "", evaluationEnabled = null) }
        EvalCardLog.holderSet(
            via = EvalCardLog.VIA_REPEAT,
            sessionId = childSessionId,
            ownerId = null,
            evaluationEnabled = EvalCardLog.Flag.ABSENT,
            stored = info != null,
            previousEvaluationEnabled = previous?.let { EvalCardLog.Flag.of(it.evaluationEnabled) }
        )
    }

    /** Existing contract: the sessionId string (AiScoringEngine et al.). */
    fun get(): String? = info?.sessionId

    /** feat/technician-evaluation: full join context, or null. */
    fun getInfo(): ScanSessionInfo? = info

    /**
     * Empties the holder ONLY if it still holds [sessionId] (batch 2, item 2: «ربط تفريغ الحامل بمعرّف جلسته»). Leaving or losing a session must not empty the
     * holder of a session joined meanwhile - the same rule `shouldClearSessionAfterAnalysis` applies after an analysis. Compare-and-clear under one lock.
     * Prints `holder-clear` when it cleared, `holder-clear-skipped` when it did not.
     */
    @Synchronized
    fun clearIfHolding(sessionId: String, reason: ClearReason): ClearIfHoldingResult {
        val held = info
        return when {
            held == null -> {
                EvalCardLog.holderClearSkipped(reason.wire, sessionId, heldSessionId = null)
                ClearIfHoldingResult.WAS_EMPTY
            }
            held.sessionId != sessionId -> {
                EvalCardLog.holderClearSkipped(reason.wire, sessionId, held.sessionId)
                ClearIfHoldingResult.KEPT_OTHER_SESSION
            }
            else -> {
                clear(reason)
                ClearIfHoldingResult.CLEARED
            }
        }
    }

    /** Empties the holder; [reason] is what the log line says. Every caller passes its own. */
    @Synchronized
    fun clear(reason: ClearReason) {
        val previous = info
        info = null
        EvalCardLog.holderClear(
            reason = reason.wire,
            hadInfo = previous != null,
            sessionId = previous?.sessionId,
            evaluationEnabled = previous?.let { EvalCardLog.Flag.of(it.evaluationEnabled) }
        )
    }
}
