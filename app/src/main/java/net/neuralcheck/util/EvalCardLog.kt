package net.neuralcheck.util

import android.util.Log

/**
 * Permanent, named log lines on the technician-evaluation card's chain.
 *
 * Order 2026-10-10 (user decision, the option «Permanent named log lines»): "Add a few log lines to the app itself
 * (the received flag, holder set/clear, the card's gate; booleans and hashed ids only), shipped in the next build, so
 * any device's logcat shows the chain." Why: in DC-03 (1.0.11) the card did not show on a customer phone while the
 * server answered `evaluationEnabled=true`, and the app printed nothing about the join answer, the holder or the
 * card's gate — so no logcat could say where the chain broke.
 *
 * ── The contract of these lines ──
 *  - ONE tag, [TAG] (rule 24). It is none of the three tags `DiagnosticLoggingGuardTest` forbids (that test scans
 *    the source text, comments included, so they are not spelled out here): those are TEMPORARY diagnosis tags;
 *    these lines are permanent by the user's decision.
 *  - Booleans, named states and ids hashed by [LogRedaction.hashId] ONLY. No session id, owner id, uid, session
 *    code or scan id is ever printed raw. Every id enters this file as a `String?` and leaves as `#xxxxxxxxxxxx`
 *    / `missing` / `invalid-type`.
 *  - One line per EVENT, never per frame: nothing here is called from a composable's body, and none from a loop.
 *    `TechnicianEvaluationCard` (measurement-safety contract: "NO LaunchedEffect/timer here") has no line.
 *  - Named values are written as explicit wire strings, never `Enum.name`, a class name or a stack trace: R8
 *    renames classes in the release build, so those would print letters.
 *  - A failure inside a log line never changes behaviour: every emit is wrapped, and nothing here throws.
 *  - Not a displayed text (decision 90: no new displayed text without approval): logcat only.
 *
 * `evaluationEnabled=` (and `previousEvaluationEnabled=`) print a [Flag]: `true`, `false`, or `absent` — the join answer did
 * not carry the flag (order 2026-10-15, rule 22: an absence is declared, never printed as `false`); `none` is the OTHER
 * thing, there is no holder or record to have a flag at all.
 *
 * The lines (event, then `key=value` pairs; hashes are the server's format, so `session=#…`, `owner=#…` and
 * `joiner=#…` match the server's `[joinScanSession] … session=#… owner=#… joiner=#…` line):
 *   join-response-raw     data= evaluationEnabled= evaluation_enabled= session= owner= joiner=     (before Gson)
 *   join-response-parsed  evaluationEnabled= session= owner= joiner=                               (after Gson)
 *   join-consent-part     consentEnabled= session=                                                 (ق15: true|false|absent; the consent screen is for true only)
 *   holder-set           via= stored= session= owner= evaluationEnabled= previousEvaluationEnabled=
 *   holder-clear          reason= hadInfo= session= evaluationEnabled=
 *   eval-vm-built         snapshot= evaluationEnabled= isEvaluationSession= session=
 *   eval-bind             result= scan=
 *   eval-bind-read-start  scan=                      (Room read of an earlier row: about to run, on the IO dispatcher)
 *   eval-bind-read-done   found= scan=              (the same read returned: found=true|false; no line on a failure)
 *   eval-bind-draft       state= scan=
 *   eval-bind-read-failed error= scan=
 *   eval-bind-draft-failed error= scan=             (building the draft threw; no draft, so the card does not show)
 *   eval-dismissed        draftReady= session=
 *   eval-handoff          isEvaluationSession= draftReady= dismissed= scan=      (scan screen -> manual_tests)
 *   scan-session-recorded scan= session= evaluationEnabled=   (ScanSessionRecords: scan start; session=missing = a personal scan)
 *   scan-session-differs  use= scan= recorded= holder=        (the scan's recorded session is used and the holder's differs)
 *   scan-session-holder-ignored use= scan= holder=            (no record for the scan; the holder holds a session; the scan stays personal)
 *   scan-session-unreadable use= scan=                        (a stored record did not parse; treated as no session)
 *   scan-session-consumed scan=                               (the scan's analysis succeeded; its record is removed)
 *   session-refused       call= reasonCode= raw= functionsException= shown=   (a join or cancel call failed; see below)
 *   scan-refused          context= reasonCode= raw= combination= shown=   (A-SUB: an analysis was refused WITH a details.reasonCode; context=personal|session, combination=expected|unexpected|no-approved-text, shown= the text key)
 *   scan-gate             decision=allowed-unverified reason=check-failed error=   (A-SUB: the pre-scan subscription check threw; the scan is allowed UNVERIFIED, analyzeDevice re-checks; error= the exception's class name, no message)
 *   session-cancel-sent   role= reason= session=             (cancelScanSession is about to be called)
 *   session-cancel-answer role= outcome= status= statusRaw= session=   (the call succeeded; outcome says whether the answer is a real "cancelled")
 *   session-leave         role= phase= result= session= holder=  (the customer leaves: the holder is emptied only if it still holds that session)
 *   session-leave-local   role= phase= reason= session=      (batch 3: the leave was LOCAL - the cancellation could not reach the server; reason=offline)
 *   session-leave-expired role= phase= session=              (batch 3: the server answered SESSION_EXPIRED to the leave; counted as a successful leave)
 *   session-watch-start   scan= session=                     (the customer device starts reading its session document)
 *   session-watch-event   session= doc= status= statusRaw= cancelReason= cancelReasonRaw=
 *   session-watch-error   session= code=                     (the read ended with an error; the scan carries on)
 *   session-watch-stop    session= reason=
 *   holder-clear-skipped  reason= requested= held=           (ScanSessionHolder.clearIfHolding: the holder held another session, or none; nothing was emptied)
 *   session-create-failed sessionId= code=                   (createScanSession answered without a session id or a code: a named failure, not an empty chip)
 *   session-join-failed   sessionId=                         (batch 3: joinScanSession answered without a usable session id - absent or blank; a named failure at both join entry points)
 *   owner-cancelled-shown session= source= cancelReason= cancelReasonRaw= shown=   (batch 3: the owner's screen entered the cancelled state; shown= is the text key; cancelReason= absent or unknown is named, never read as a known reason)
 *
 * Batch 2 (order 2026-10-15): the session-* lines above the scan-session ones. Same contract: ids hashed, wire strings, no new screen text.
 * reasonCode= of session-refused is the server's details.reasonCode as this app reads it: a known code's wire string,
 * unrecognized (a text the app has no case for; raw= is that text bounded and reduced to [A-Za-z0-9_.-]) or absent (the refusal
 * carried no reasonCode, or it was not a Functions error at all: functionsException= says which). shown= is the key of the text that
 * reached the screen (an approved text, network, error_generic or qr_join_failed).
 *
 * Order 2026-10-14 (user decision, device check 03 round 2, the option «Diagnostic build, then 1 session»: "P1 only: two
 * log lines around the Room read (eval-bind-read-start, eval-bind-read-done found=true|false), no behavior change, no
 * new text, payload/fv unchanged"): `eval-bind-read-start` and `eval-bind-read-done` bracket the one
 * `SessionEvaluationDao.getByScanId` call in `TechnicianEvaluationViewModel.bind`, inside its IO coroutine. Reading a
 * logcat: no start line after `eval-bind result=started` = the coroutine never ran; a start line with no done line (and
 * no `eval-bind-read-failed`) = the Room read never returned.
 *
 * Order 2026-10-14 (user decision, device check 03 round 2, the option «Root fix + guards»): "A failure while building the
 * draft is caught and logged as eval-bind-draft-failed error=<class> - no silent death; no new screen text."
 * `eval-bind-draft` (state=new|restored) is the line that means the draft is READY; `eval-bind-draft-failed` means
 * building it threw, so no draft exists and the card does not show. Why it exists: on 1.0.8-1.0.13 the object that
 * builds the draft failed to initialise in the release build (R8 stripped a generic signature Gson needs), the
 * coroutine died (`NeuralCheckApp`'s handler keeps the process alive on a background thread and logs the failure
 * under another tag), and this tag showed `eval-bind-read-done` and then nothing — the silence this line ends.
 * `error=` is the exception's class name (an `Error` such as `ExceptionInInitializerError` counts: it is caught
 * as a Throwable).
 *
 * Order 2026-10-14 (user decision, device check 03 round 2, the option «Record the session at scan start»): the five
 * `scan-session-*` lines are printed by `ScanSessionRecords`, which copies the holder's session under the scan's id at
 * scan start and answers the scan's analysis and its evaluation draft from that copy afterwards (never from the holder
 * as it is then). `use=analysis|draft` says who asked. `scan-session-differs` is the line the order asked for: a
 * recorded session is used although the holder's differs (`holder=missing` = the holder is empty, as after the first
 * failed analysis cleared it, or after a sign-out). `scan-session-holder-ignored` is the converse: the scan started in no
 * session, the holder holds one now (joined later), and the scan stays personal. Same contract as every line here:
 * ids hashed, no raw id, no text on screen.
 *
 * [sink] is where a finished line goes; the default prints it with `Log.i` (INFO survives logcat verbosity
 * filters, as the existing `NeuralCheck.Sub` line notes). Tests replace it to read the lines.
 */
object EvalCardLog {

    const val TAG = "NeuralCheck.EvalCard"

    const val VIA_JOIN = "join"
    const val VIA_LEGACY = "legacy"

    /** The scanned phone was handed the repeat scan's CHILD session by `startRepeatScan` (A-P6-07): no code, no join. */
    const val VIA_REPEAT = "repeat"

    /**
     * How a join answer's `evaluationEnabled` prints. [ABSENT] = the answer did not carry it (key missing, or JSON null):
     * a named state, never `false` (order 2026-10-15, rule 22). Wire strings are explicit (R8-safe), and they are ALSO the
     * tokens `ScanSessionRecords` stores in its preferences file (one source for the three words, rule 24): changing one
     * here needs a read of the old tokens there.
     */
    enum class Flag(val wire: String) {
        TRUE("true"),
        FALSE("false"),
        ABSENT("absent");

        companion object {
            fun of(value: Boolean?): Flag = when (value) {
                true -> TRUE
                false -> FALSE
                null -> ABSENT
            }
        }
    }

    /** What `bind` decided. Wire strings are explicit (R8-safe). */
    enum class BindResult(val wire: String) {
        STARTED("started"),
        ALREADY_BOUND("already-bound"),
        SCAN_ID_BLANK("scan-id-blank"),
        /** The view model was built outside an evaluation session, or the scan's recorded session is not one. */
        NOT_EVALUATION_SESSION("not-evaluation-session"),

        /** Order 2026-10-15: the session's join answer did not carry `evaluationEnabled` — refused BY NAME, not read as `false`. */
        FLAG_ABSENT("flag-absent"),

        /** Order 2026-10-14: no session is RECORDED for this scan (it started in none) — it stays a personal scan. */
        NO_SESSION_ID("no-session-id")
    }

    /** How the draft that un-hides the card was obtained. */
    enum class DraftState(val wire: String) {
        NEW("new"),
        RESTORED("restored")
    }

    private val defaultSink: (String) -> Unit = { Log.i(TAG, it) }

    @Volatile
    internal var sink: (String) -> Unit = defaultSink

    internal fun resetSink() {
        sink = defaultSink
    }

    private inline fun emit(build: () -> String) {
        try {
            sink(build())
        } catch (_: Throwable) {
            // A log line must never change what the app does.
        }
    }

    // ---- the join answer ----

    /**
     * The answer exactly as the callable returned it (`HttpsCallableResult.data`), BEFORE Gson sees it: whether each
     * of the two flag keys is present and the type of its raw value. [joinerUid] is this device's Firebase uid.
     */
    fun joinResponseRaw(data: Any?, joinerUid: String?) = emit { joinResponseRawLine(data, joinerUid) }

    /** The answer after Gson turned it into the DTO. */
    fun joinResponseParsed(sessionId: String?, ownerId: String?, evaluationEnabled: Flag, joinerUid: String?) =
        emit { joinResponseParsedLine(sessionId, ownerId, evaluationEnabled, joinerUid) }

    /**
     * ق15: whether the join answer carried the shop's consent part - `true`, `false` or `absent` (the key was missing, or a JSON null: an older server; a named state, never `false`). The session id enters hashed. The
     * two join entry points route by it: the consent screen is for `true` only.
     */
    fun joinConsentPart(sessionId: String?, consentEnabled: Flag) = emit { joinConsentPartLine(sessionId, consentEnabled) }

    internal fun joinConsentPartLine(sessionId: String?, consentEnabled: Flag): String =
        "join-consent-part consentEnabled=${consentEnabled.wire} session=${LogRedaction.hashId(sessionId)}"

    internal fun joinResponseRawLine(data: Any?, joinerUid: String?): String {
        val map = data as? Map<*, *>
        val shape = when {
            data == null -> "null"
            map != null -> "Map"
            else -> "Other"
        }
        return "join-response-raw data=$shape" +
            " evaluationEnabled=${rawKind(map, "evaluationEnabled")}" +
            " evaluation_enabled=${rawKind(map, "evaluation_enabled")}" +
            " session=${LogRedaction.hashId(map?.get("sessionId"))}" +
            " owner=${LogRedaction.hashId(map?.get("ownerId"))}" +
            " joiner=${LogRedaction.hashId(joinerUid)}"
    }

    internal fun joinResponseParsedLine(
        sessionId: String?,
        ownerId: String?,
        evaluationEnabled: Flag,
        joinerUid: String?
    ): String =
        "join-response-parsed evaluationEnabled=${evaluationEnabled.wire}" +
            " session=${LogRedaction.hashId(sessionId)}" +
            " owner=${LogRedaction.hashId(ownerId)}" +
            " joiner=${LogRedaction.hashId(joinerUid)}"

    /**
     * `n/a` (the answer is not a map), `absent` (no such key), `null` (key present, value null), or the raw value's
     * kind: `Boolean:true|false`, `String:true|false|other` (a string is judged the way `Boolean.parseBoolean`
     * would, which is what Gson does with it), `Number`, `Map`, `List`, `Other`.
     */
    internal fun rawKind(map: Map<*, *>?, key: String): String = when {
        map == null -> "n/a"
        !map.containsKey(key) -> "absent"
        else -> when (val v = map[key]) {
            null -> "null"
            is Boolean -> "Boolean:$v"
            is String -> "String:" + when {
                v.equals("true", ignoreCase = true) -> "true"
                v.equals("false", ignoreCase = true) -> "false"
                else -> "other"
            }
            is Number -> "Number"
            is Map<*, *> -> "Map"
            is List<*> -> "List"
            else -> "Other"
        }
    }

    // ---- the holder ----

    /**
     * [via] is [VIA_JOIN], [VIA_REPEAT] or [VIA_LEGACY]; [stored] is false when the id was blank and the holder was emptied;
     * [previousEvaluationEnabled] is what the holder held before (null = nothing).
     */
    fun holderSet(
        via: String,
        sessionId: String?,
        ownerId: String?,
        evaluationEnabled: Flag,
        stored: Boolean,
        previousEvaluationEnabled: Flag?
    ) = emit {
        holderSetLine(via, sessionId, ownerId, evaluationEnabled, stored, previousEvaluationEnabled)
    }

    internal fun holderSetLine(
        via: String,
        sessionId: String?,
        ownerId: String?,
        evaluationEnabled: Flag,
        stored: Boolean,
        previousEvaluationEnabled: Flag?
    ): String =
        "holder-set via=$via stored=$stored" +
            " session=${LogRedaction.hashId(sessionId)}" +
            " owner=${LogRedaction.hashId(ownerId)}" +
            " evaluationEnabled=${evaluationEnabled.wire}" +
            " previousEvaluationEnabled=${previousEvaluationEnabled?.wire ?: "none"}"

    /** [reason] is a wire string of `ScanSessionHolder.ClearReason`, passed by every caller. */
    fun holderClear(reason: String, hadInfo: Boolean, sessionId: String?, evaluationEnabled: Flag?) =
        emit { holderClearLine(reason, hadInfo, sessionId, evaluationEnabled) }

    internal fun holderClearLine(reason: String, hadInfo: Boolean, sessionId: String?, evaluationEnabled: Flag?): String =
        "holder-clear reason=$reason hadInfo=$hadInfo" +
            " session=${LogRedaction.hashId(sessionId)}" +
            " evaluationEnabled=${evaluationEnabled?.wire ?: "none"}"

    // ---- the card's view model ----

    fun viewModelBuilt(
        snapshotPresent: Boolean,
        snapshotEvaluationEnabled: Flag?,
        isEvaluationSession: Boolean,
        sessionId: String?
    ) = emit { viewModelBuiltLine(snapshotPresent, snapshotEvaluationEnabled, isEvaluationSession, sessionId) }

    internal fun viewModelBuiltLine(
        snapshotPresent: Boolean,
        snapshotEvaluationEnabled: Flag?,
        isEvaluationSession: Boolean,
        sessionId: String?
    ): String =
        "eval-vm-built snapshot=${if (snapshotPresent) "present" else "absent"}" +
            " evaluationEnabled=${snapshotEvaluationEnabled?.wire ?: "none"}" +
            " isEvaluationSession=$isEvaluationSession" +
            " session=${LogRedaction.hashId(sessionId)}"

    fun bindResult(result: BindResult, scanId: String?) = emit { bindResultLine(result, scanId) }

    internal fun bindResultLine(result: BindResult, scanId: String?): String =
        "eval-bind result=${result.wire} scan=${LogRedaction.hashId(scanId)}"

    /** About to read an earlier row for [scanId] (first statement of the IO coroutine `bind` launches). */
    fun bindReadStart(scanId: String?) = emit { bindReadStartLine(scanId) }

    internal fun bindReadStartLine(scanId: String?): String =
        "eval-bind-read-start scan=${LogRedaction.hashId(scanId)}"

    /** The read returned without throwing; [found] is whether an earlier row exists. A failed read prints [draftReadFailed] instead. */
    fun bindReadDone(found: Boolean, scanId: String?) = emit { bindReadDoneLine(found, scanId) }

    internal fun bindReadDoneLine(found: Boolean, scanId: String?): String =
        "eval-bind-read-done found=$found scan=${LogRedaction.hashId(scanId)}"

    fun draftReady(state: DraftState, scanId: String?) = emit { draftReadyLine(state, scanId) }

    internal fun draftReadyLine(state: DraftState, scanId: String?): String =
        "eval-bind-draft state=${state.wire} scan=${LogRedaction.hashId(scanId)}"

    /** The read of an earlier row failed; the draft is built new anyway (the behaviour `bind` always had). */
    fun draftReadFailed(error: Throwable, scanId: String?) = emit { draftReadFailedLine(error, scanId) }

    internal fun draftReadFailedLine(error: Throwable, scanId: String?): String =
        "eval-bind-read-failed error=${error.javaClass.name.take(ERROR_NAME_MAX)} scan=${LogRedaction.hashId(scanId)}"

    /** Building the draft threw ([error] is any Throwable, an Error included); no draft exists, the card does not show. */
    fun draftFailed(error: Throwable, scanId: String?) = emit { draftFailedLine(error, scanId) }

    internal fun draftFailedLine(error: Throwable, scanId: String?): String =
        "eval-bind-draft-failed error=${error.javaClass.name.take(ERROR_NAME_MAX)} scan=${LogRedaction.hashId(scanId)}"

    fun dismissed(draftReady: Boolean, sessionId: String?) = emit { dismissedLine(draftReady, sessionId) }

    internal fun dismissedLine(draftReady: Boolean, sessionId: String?): String =
        "eval-dismissed draftReady=$draftReady session=${LogRedaction.hashId(sessionId)}"

    fun handoff(isEvaluationSession: Boolean, draftReady: Boolean, dismissed: Boolean, scanId: String?) =
        emit { handoffLine(isEvaluationSession, draftReady, dismissed, scanId) }

    internal fun handoffLine(isEvaluationSession: Boolean, draftReady: Boolean, dismissed: Boolean, scanId: String?): String =
        "eval-handoff isEvaluationSession=$isEvaluationSession draftReady=$draftReady dismissed=$dismissed" +
            " scan=${LogRedaction.hashId(scanId)}"

    // ---- the session a scan started in (ScanSessionRecords) ----

    /** At scan start: what the holder held then. [sessionId] null = it held none (a personal scan). */
    fun scanSessionRecorded(scanId: String?, sessionId: String?, evaluationEnabled: Flag?) =
        emit { scanSessionRecordedLine(scanId, sessionId, evaluationEnabled) }

    internal fun scanSessionRecordedLine(scanId: String?, sessionId: String?, evaluationEnabled: Flag?): String =
        "scan-session-recorded scan=${LogRedaction.hashId(scanId)}" +
            " session=${LogRedaction.hashId(sessionId)}" +
            " evaluationEnabled=${evaluationEnabled?.wire ?: "none"}"

    /** The recorded session is used ([use] = a `ScanSessionRecords.Use` wire string) and the holder's is different or empty. */
    fun scanSessionDiffers(use: String, scanId: String?, recordedSessionId: String?, holderSessionId: String?) =
        emit { scanSessionDiffersLine(use, scanId, recordedSessionId, holderSessionId) }

    internal fun scanSessionDiffersLine(use: String, scanId: String?, recordedSessionId: String?, holderSessionId: String?): String =
        "scan-session-differs use=$use scan=${LogRedaction.hashId(scanId)}" +
            " recorded=${LogRedaction.hashId(recordedSessionId)}" +
            " holder=${LogRedaction.hashId(holderSessionId)}"

    /** No record for the scan, the holder holds a session: the scan stays personal. */
    fun scanSessionHolderIgnored(use: String, scanId: String?, holderSessionId: String?) =
        emit { scanSessionHolderIgnoredLine(use, scanId, holderSessionId) }

    internal fun scanSessionHolderIgnoredLine(use: String, scanId: String?, holderSessionId: String?): String =
        "scan-session-holder-ignored use=$use scan=${LogRedaction.hashId(scanId)}" +
            " holder=${LogRedaction.hashId(holderSessionId)}"

    /** A stored record did not parse; it is treated as no session, and this says so. */
    fun scanSessionUnreadable(use: String, scanId: String?) = emit { scanSessionUnreadableLine(use, scanId) }

    internal fun scanSessionUnreadableLine(use: String, scanId: String?): String =
        "scan-session-unreadable use=$use scan=${LogRedaction.hashId(scanId)}"

    /** The scan's analysis succeeded; its record is removed. */
    fun scanSessionConsumed(scanId: String?) = emit { scanSessionConsumedLine(scanId) }

    internal fun scanSessionConsumedLine(scanId: String?): String =
        "scan-session-consumed scan=${LogRedaction.hashId(scanId)}"

    // ---- the session contract (batch 2): refusals, cancel, leave, watch, create ----

    /** [raw]: a server-sent text, kept to [TOKEN_MAX] characters of [A-Za-z0-9_.-]; null prints a dash, an empty text prints "empty". */
    internal fun token(raw: String?): String {
        if (raw == null) return "-"
        if (raw.isEmpty()) return "empty"
        return raw.take(TOKEN_MAX).map { if ((it.isLetterOrDigit() && it.code < 128) || it == '_' || it == '.' || it == '-') it else '?' }.joinToString("")
    }

    /** A join or cancel call failed. [reasonCode] is ReasonCodeRead's token; [raw] the unrecognized text (else null); [shown] the key of the text shown. */
    fun sessionRefused(call: String, reasonCode: String, raw: String?, functionsException: Boolean, shown: String) =
        emit { sessionRefusedLine(call, reasonCode, raw, functionsException, shown) }

    internal fun sessionRefusedLine(call: String, reasonCode: String, raw: String?, functionsException: Boolean, shown: String): String =
        "session-refused call=$call reasonCode=$reasonCode raw=${token(raw)} functionsException=$functionsException shown=$shown"

    /**
     * An analysis was refused and the refusal carried a `details.reasonCode` (A-SUB wave 1, 2026-10-17). [context] is `personal` or `session` (what the engine used for this scan);
     * [reasonCode] is ReasonCodeRead's token (a known code's wire string, or `unrecognized` with [raw]); [combination] says whether the code is one of the cells of the approved
     * matrix for that context (`expected`), one of the five scan refusals outside its cell or a code the analysis was not expected to send (`unexpected`), or a known session-state
     * code that has no approved text (`no-approved-text`); [shown] is the key of the text that reached the card. A refusal with NO reasonCode prints no such line (the analysis' own
     * `backend call failed` line says it).
     */
    fun scanRefused(context: String, reasonCode: String, raw: String?, combination: String, shown: String) =
        emit { scanRefusedLine(context, reasonCode, raw, combination, shown) }

    internal fun scanRefusedLine(context: String, reasonCode: String, raw: String?, combination: String, shown: String): String =
        "scan-refused context=$context reasonCode=$reasonCode raw=${token(raw)} combination=$combination shown=$shown"

    /**
     * The pre-scan gate's `checkSubscription` threw and the scan was allowed without its answer (A-SUB wave 1, item 5; user decision 5; rule 22): [errorClass] is the exception's simple class
     * name - never its message (rule 36) - reduced like every server-or-SDK text here by [token]. The only decision this line names is `allowed-unverified`: a normal allow or block prints no
     * line (the gate's verdict reading, with `decision=allowed|blocked`, is a later item of the wave).
     */
    fun scanGateUnverified(errorClass: String) = emit { scanGateUnverifiedLine(errorClass) }

    internal fun scanGateUnverifiedLine(errorClass: String): String =
        "scan-gate decision=allowed-unverified reason=check-failed error=${token(errorClass)}"

    fun sessionCancelSent(role: String, reason: String, sessionId: String?) = emit { sessionCancelSentLine(role, reason, sessionId) }

    internal fun sessionCancelSentLine(role: String, reason: String, sessionId: String?): String =
        "session-cancel-sent role=$role reason=$reason session=${LogRedaction.hashId(sessionId)}"

    /** [outcome] one of cancelled, already-cancelled, cancelled-flag-absent, unexpected; [status] the answer's status token (known wire, unknown, absent); [statusRaw] the unknown text. */
    fun sessionCancelAnswer(role: String, outcome: String, status: String, statusRaw: String?, sessionId: String?) =
        emit { sessionCancelAnswerLine(role, outcome, status, statusRaw, sessionId) }

    internal fun sessionCancelAnswerLine(role: String, outcome: String, status: String, statusRaw: String?, sessionId: String?): String =
        "session-cancel-answer role=$role outcome=$outcome status=$status statusRaw=${token(statusRaw)} session=${LogRedaction.hashId(sessionId)}"

    /** [phase] pre-scan or scan; [result] left, failed or no-session; [holder] what the holder did: cleared, kept-other-session, was-empty, n/a. */
    fun sessionLeave(role: String, phase: String, result: String, sessionId: String?, holder: String) =
        emit { sessionLeaveLine(role, phase, result, sessionId, holder) }

    internal fun sessionLeaveLine(role: String, phase: String, result: String, sessionId: String?, holder: String): String =
        "session-leave role=$role phase=$phase result=$result session=${LogRedaction.hashId(sessionId)} holder=$holder"

    /**
     * The customer left WITHOUT the server confirming (batch 3, item 1, user decision 4): the phone left locally. [reason] says why the cancellation was not
     * confirmed (`offline`: it did not reach the server - the error text says there is no connection; `not_participant`: the server answered `SESSION_NOT_PARTICIPANT`, batch 4, item 2). Printed BEFORE the `session-leave ... result=left` line.
     */
    fun sessionLeaveLocal(role: String, phase: String, reason: String, sessionId: String?) =
        emit { sessionLeaveLocalLine(role, phase, reason, sessionId) }

    internal fun sessionLeaveLocalLine(role: String, phase: String, reason: String, sessionId: String?): String =
        "session-leave-local role=$role phase=$phase reason=$reason session=${LogRedaction.hashId(sessionId)}"

    /** The server answered `SESSION_EXPIRED` to the customer's leave: the session is over, so the leave counts as done (batch 3, item 1, user decision 4). */
    fun sessionLeaveExpired(role: String, phase: String, sessionId: String?) = emit { sessionLeaveExpiredLine(role, phase, sessionId) }

    internal fun sessionLeaveExpiredLine(role: String, phase: String, sessionId: String?): String =
        "session-leave-expired role=$role phase=$phase session=${LogRedaction.hashId(sessionId)}"

    fun sessionWatchStart(scanId: String?, sessionId: String?) = emit { sessionWatchStartLine(scanId, sessionId) }

    internal fun sessionWatchStartLine(scanId: String?, sessionId: String?): String =
        "session-watch-start scan=${LogRedaction.hashId(scanId)} session=${LogRedaction.hashId(sessionId)}"

    /** One line for a snapshot: [doc] present or missing; [status]/[cancelReason] known wire, unknown or absent; the raw texts are the unknown ones (else null). */
    fun sessionWatchEvent(sessionId: String?, doc: String, status: String, statusRaw: String?, cancelReason: String, cancelReasonRaw: String?) =
        emit { sessionWatchEventLine(sessionId, doc, status, statusRaw, cancelReason, cancelReasonRaw) }

    internal fun sessionWatchEventLine(sessionId: String?, doc: String, status: String, statusRaw: String?, cancelReason: String, cancelReasonRaw: String?): String =
        "session-watch-event session=${LogRedaction.hashId(sessionId)} doc=$doc status=$status statusRaw=${token(statusRaw)}" +
            " cancelReason=$cancelReason cancelReasonRaw=${token(cancelReasonRaw)}"

    /** [code] is the Firestore error code as a lower-case token (permission-denied, ...) or other. */
    fun sessionWatchError(sessionId: String?, code: String) = emit { sessionWatchErrorLine(sessionId, code) }

    internal fun sessionWatchErrorLine(sessionId: String?, code: String): String =
        "session-watch-error session=${LogRedaction.hashId(sessionId)} code=$code"

    fun sessionWatchStop(sessionId: String?, reason: String) = emit { sessionWatchStopLine(sessionId, reason) }

    internal fun sessionWatchStopLine(sessionId: String?, reason: String): String =
        "session-watch-stop session=${LogRedaction.hashId(sessionId)} reason=$reason"

    /** clearIfHolding emptied nothing: [heldSessionId] is what the holder held (null = nothing), [requestedSessionId] what the caller asked to clear. */
    fun holderClearSkipped(reason: String, requestedSessionId: String?, heldSessionId: String?) =
        emit { holderClearSkippedLine(reason, requestedSessionId, heldSessionId) }

    internal fun holderClearSkippedLine(reason: String, requestedSessionId: String?, heldSessionId: String?): String =
        "holder-clear-skipped reason=$reason requested=${LogRedaction.hashId(requestedSessionId)} held=${LogRedaction.hashId(heldSessionId)}"

    /**
     * The owner's screen entered the cancelled state (batch 3, item 2). [source] is `snapshot` or `own-cancel`; [cancelReason] the server's `cancelReason` as read - a known
     * wire value, `unknown` (then [cancelReasonRaw] is its bounded text) or `absent`; [shown] the key of the text that is displayed.
     */
    fun ownerCancelledShown(sessionId: String?, source: String, cancelReason: String, cancelReasonRaw: String?, shown: String) =
        emit { ownerCancelledShownLine(sessionId, source, cancelReason, cancelReasonRaw, shown) }

    internal fun ownerCancelledShownLine(sessionId: String?, source: String, cancelReason: String, cancelReasonRaw: String?, shown: String): String =
        "owner-cancelled-shown session=${LogRedaction.hashId(sessionId)} source=$source cancelReason=$cancelReason cancelReasonRaw=${token(cancelReasonRaw)} shown=$shown"

    /** The join call succeeded but its answer had no usable session id (batch 3, item 3): [sessionId] is `absent` (no key, or null) or `blank`. */
    fun sessionJoinFailed(sessionId: String) = emit { sessionJoinFailedLine(sessionId) }

    internal fun sessionJoinFailedLine(sessionId: String): String = "session-join-failed sessionId=$sessionId"

    /** [sessionId]/[code] are present or absent. */
    fun sessionCreateFailed(sessionId: String, code: String) = emit { sessionCreateFailedLine(sessionId, code) }

    internal fun sessionCreateFailedLine(sessionId: String, code: String): String =
        "session-create-failed sessionId=$sessionId code=$code"

    // ---- the technician's form (A-P6-08) ----

    /**
     * What `getTechnicianFormConfig` said, one line per load: [shape] is `absent`, `not_an_object_<kind>` or `object`; [symptomOrderSource] is `server`, `contract_order` (the order the chips were
     * shown in came from the answer, or is the contract's table order because the answer named no usable list) or `-` (no form was offered, so no order was used); [technicians] is how many usable
     * technicians it named; [offer] is what the form made of it (`FormOffer.token`): `offered`, `not_enabled`, `slot_submitted`, `slot_not_drawn`, `no_technicians` or `unreadable_<why>`.
     */
    fun technicianFormConfig(shape: String, symptomOrderSource: String, technicians: Int, offer: String) =
        emit { technicianFormConfigLine(shape, symptomOrderSource, technicians, offer) }

    internal fun technicianFormConfigLine(shape: String, symptomOrderSource: String, technicians: Int, offer: String): String =
        "technician-form-config shape=$shape symptomOrder=$symptomOrderSource technicians=$technicians offer=${token(offer)}"

    /**
     * The app asked the server to record that the report of a session was shown for the first time (A-P6-08, the form's blindness) and the call has an end: [source] is `owner` or `customer`; [outcome] is
     * `recorded`, `already_recorded`, `refused` ([detail]: the token of `details.reasonCode`), `not_sent` (no `reasonCode`: a disconnect, a timeout, any other failure) or `unreadable_answer` ([detail]: a
     * fixed token). Nothing is printed while the form's texts are not approved (the call is not made). The session id enters hashed.
     */
    fun reportFirstOpened(sessionId: String?, source: String, outcome: String, detail: String) =
        emit { reportFirstOpenedLine(sessionId, source, outcome, detail) }

    internal fun reportFirstOpenedLine(sessionId: String?, source: String, outcome: String, detail: String): String =
        "report-first-opened session=${LogRedaction.hashId(sessionId)} source=$source outcome=$outcome detail=${token(detail)}"

    // ---- the repeat scan (A-P6-07) ----

    /**
     * One call of the repeat scan has an end (`RepeatScanClient`): [call] is `offer` or `start`; [role] is `owner` (the technician's phone) or `scanned` (the scanned phone); [outcome] is `answered` ([detail]: the
     * decision's token, e.g. `startable_selected`, `not_applicable_not_selected`, `unreadable_<why>`), `started` ([detail]: `cooled` or `before_cooling`), `refused` ([detail]: the token of `details.reasonCode`),
     * `not_sent` (no `reasonCode`: a disconnect, a timeout, any other failure) or `unreadable_answer` ([detail]: a fixed token). Nothing is printed while the repeat screens' texts are not approved (the call is
     * not made). The session id enters hashed; no temperature is ever printed.
     */
    fun repeatScan(call: String, role: String, sessionId: String?, outcome: String, detail: String) =
        emit { repeatScanLine(call, role, sessionId, outcome, detail) }

    internal fun repeatScanLine(call: String, role: String, sessionId: String?, outcome: String, detail: String): String =
        "repeat-scan call=$call role=$role session=${LogRedaction.hashId(sessionId)} outcome=$outcome detail=${token(detail)}"

    // ---- the maintenance link (order of 2026-10-22) ----

    /**
     * One call of the maintenance link has an end (`PairLinkClient`): [call] is `list` or `confirm`; [outcome] is `listed` ([detail]: `n<candidates>_v<validityHours>`), `confirmed`, `refused` ([detail]: the token of
     * `details.reasonCode` - named by the contract or not, the line is where an unnamed one is seen), `not_sent` (no `reasonCode`: a disconnect, a timeout, any other failure) or `unreadable_answer` ([detail]: a fixed
     * token). [unknownKeys] are the keys of the list's answer that are none of the contract's (bounded, sorted, `-` for none). Nothing is printed while the pair list's texts are not approved (the call is not made). The
     * session id enters hashed; no scan id, no device name, no model and no time is ever printed.
     */
    fun pairLink(call: String, sessionId: String?, outcome: String, detail: String, unknownKeys: List<String>) =
        emit { pairLinkLine(call, sessionId, outcome, detail, unknownKeys) }

    internal fun pairLinkLine(call: String, sessionId: String?, outcome: String, detail: String, unknownKeys: List<String>): String =
        "pair-link call=$call session=${LogRedaction.hashId(sessionId)} outcome=$outcome detail=${token(detail)} unknownKeys=" +
            (if (unknownKeys.isEmpty()) "-" else unknownKeys.take(UNKNOWN_KEYS_MAX).joinToString(",") { token(it) })

    /**
     * A tap on the pair list that was not applied (`PairListUi`): [event] is `ignored`, [detail] a bounded token naming why (`no_session`, `not_visible`, `not_choosing`, `in_flight`, `unknown_candidate`,
     * `nothing_selected`). The session id enters hashed; no scan id and no name is printed.
     */
    fun pairListEvent(event: String, sessionId: String?, detail: String) = emit { pairListEventLine(event, sessionId, detail) }

    internal fun pairListEventLine(event: String, sessionId: String?, detail: String): String =
        "pair-list event=$event session=${LogRedaction.hashId(sessionId)} detail=${token(detail)}"

    /**
     * The consent screen's call (A-P6-12, `ScanConsentClient`): [outcome] one of `recorded`, `refused`, `not_sent`, `unreadable_answer`; [detail] a bounded token (`new` / `already` / `flag_absent`, with `_differs` when the
     * stored version or language is not the one sent; a reasonCode; the unreadable answer's reason). Nothing is printed while the consent screen's texts are not approved (the call is not made). The session id enters
     * hashed; no text, no version and no identifier of the device is ever printed.
     */
    fun consentRecord(outcome: String, detail: String, sessionId: String?) = emit { consentRecordLine(outcome, detail, sessionId) }

    internal fun consentRecordLine(outcome: String, detail: String, sessionId: String?): String =
        "consent-record outcome=$outcome detail=${token(detail)} session=${LogRedaction.hashId(sessionId)}"

    /**
     * One event of the form: [event] is a fixed token (`config-failed`, `submit-blocked`, `prompt-shown`, `prompt-revise`, `unbuildable`, `submitted`, `submit-failed`), [detail] a bounded token (a reason, a
     * reasonCode, the slot). The session id enters hashed; no answer, no note and no technician id is ever printed.
     */
    fun technicianFormEvent(event: String, sessionId: String?, detail: String) = emit { technicianFormEventLine(event, sessionId, detail) }

    internal fun technicianFormEventLine(event: String, sessionId: String?, detail: String): String =
        "technician-form event=$event session=${LogRedaction.hashId(sessionId)} detail=${token(detail)}"

    // ---- the purpose chips (A-P6-03) ----

    /**
     * What `createScanSession`'s answer said about the shop's protocol parts (contract A2), one line per create: [shape] is `absent` (no key), `not_an_object_<kind>` or `object`;
     * [purposes] is the reading of the `purposes` part and [batteryBeforeAfter] of the part the maintenance chips also need (`enabled`, `disabled`, `not_listed`, `unrecognized_<kind>`;
     * `none` when the key was not an object); [unknownKeys] are the keys of the object that are none of the six (bounded, sorted, `-` for none).
     */
    fun sessionProtocolParts(shape: String, purposes: String, batteryBeforeAfter: String, unknownKeys: List<String>) =
        emit { sessionProtocolPartsLine(shape, purposes, batteryBeforeAfter, unknownKeys) }

    internal fun sessionProtocolPartsLine(shape: String, purposes: String, batteryBeforeAfter: String, unknownKeys: List<String>): String =
        "session-protocol-parts shape=$shape purposes=$purposes batteryBeforeAfter=$batteryBeforeAfter unknownKeys=" +
            (if (unknownKeys.isEmpty()) "-" else unknownKeys.take(UNKNOWN_KEYS_MAX).joinToString(",") { token(it) })

    /**
     * The owner tapped a purpose chip and the call has an end: [outcome] is `accepted`, `refused` (with [reasonCode], the token of the refusal's `details.reasonCode`, `absent` for none) or
     * `failed` (no `reasonCode`: a disconnect, a timeout, any other failure). [replyKeys] are the key names of the success answer (`-` = none, `not_an_object`), because the contract copy does
     * not name its shape and the first field check should show it. [sessionId] enters hashed.
     */
    fun sessionPurposeSet(sessionId: String?, purpose: String, defectType: String, outcome: String, reasonCode: String, replyKeys: String) =
        emit { sessionPurposeSetLine(sessionId, purpose, defectType, outcome, reasonCode, replyKeys) }

    /** [defectType] is the fault type's wire value for a maintenance purpose and `-` for none (a `normal` purpose carries no type). */
    internal fun sessionPurposeSetLine(sessionId: String?, purpose: String, defectType: String, outcome: String, reasonCode: String, replyKeys: String): String =
        "session-purpose-set session=${LogRedaction.hashId(sessionId)} purpose=$purpose defectType=${token(defectType)} outcome=$outcome reasonCode=$reasonCode replyKeys=${token(replyKeys)}"

    /** A tap on a purpose chip that was not sent, and why: `not_offered`, `no_session`, `not_pending`, `locked`, `in_flight`. */
    fun sessionPurposeIgnored(reason: String) = emit { sessionPurposeIgnoredLine(reason) }

    internal fun sessionPurposeIgnoredLine(reason: String): String = "session-purpose-ignored reason=$reason"

    /**
     * Platform exception classes (`java.*`, `android.*`) keep their names in the release build; a library class
     * (Room's) may print as letters there. The name is bounded.
     */
    private const val ERROR_NAME_MAX = 80

    /** Bound of a server-sent text in a line (raw=, statusRaw=): enough to tell two codes apart, too short to carry a message. */
    private const val TOKEN_MAX = 40

    /** How many unknown keys of `protocolParts` one line names. */
    private const val UNKNOWN_KEYS_MAX = 6
}
