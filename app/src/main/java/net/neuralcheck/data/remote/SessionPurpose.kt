package net.neuralcheck.data.remote

import com.google.gson.JsonElement

/**
 * The scan purpose a session is created for - row A-P6-03 (غرض الفحص), `plan/PLAN-06-DATA-COLLECTION.md` section 2 (lines 449-517) and the protocol contract A2
 * (copy at `evidence/run-2026-10-18/protocol-contract-A1-A13-neuralcheck-backend-8e5c426.md`, from neuralcheck-backend@8e5c426 `docs/PROTOCOL_CONTRACT.md`; a copy, it drifts), as AMENDED on 2026-10-22
 * (copy `app/src/test/resources/contract/protocol-contract-A2-A3-maintenance-pairs-neuralcheck-backend-c289a2d.md`, from neuralcheck-backend@f3c1bab).
 *
 * The ONE source (rule 24) of each wire literal in this feature. The server's `SCAN_PURPOSE` has more values than the three below - `repeat_cold`, `repeat_hot` and
 * `repeat_unclassified` (derived by the server from a repeat flow), `NO_SESSION` (the owner scanning their own phone), `UNDECLARED` (a session made before the purpose existed) and
 * `battery_before` / `battery_after` (kept by the server as HISTORICAL read values, no longer choosable: contract A2 addendum) - and this app asks for none of them: only the three the technician
 * can choose at the session's creation (`SETTABLE_SESSION_PURPOSE`: `normal`, `maintenance_before`, `maintenance_after`). The two old battery values are not spelled anywhere in the app any more
 * (`SessionPurposeGuardTest` keeps them out). `return` was dropped as a chip on 2026-10-10 and is NOT exported (contract A2: «`return` أُسقطت … لا تُصدَّران»; a test keeps it out).
 *
 * What is NOT here, on purpose: `purposeSource` (`default | selected | repeat_flow | feature_disabled`) is written by the SERVER (a session is created `normal`/`default`, a
 * `setSessionPurpose` makes it `selected`, `feature_disabled` is the shop without the part - PLAN-06:473-478); the app neither sends nor decides it (rule 20: a decision applied in
 * the app freezes its error in the data). That is why a tap on the chip that is already highlighted is still SENT ([net.neuralcheck.presentation.viewmodel.SessionPurposeUi]): the
 * server can only tell a chosen «normal» from the default one if the choice reaches it (PLAN-06:478, rule 22).
 */
enum class ScanPurposeChoice(val wire: String, val needsDefectType: Boolean) {
    /** «عادي»: what every session is created as. It never carries a fault type. */
    NORMAL("normal", needsDefectType = false),

    /** «قبل الصيانة» (contract A2 addendum of 2026-10-22: `maintenance_before` replaces `battery_before` among the values a technician can choose). It always carries a [DefectType]. */
    MAINTENANCE_BEFORE("maintenance_before", needsDefectType = true),

    /** «بعد الصيانة» (`maintenance_after` replaces `battery_after`; the list of open «before» scans it opens is [net.neuralcheck.domain.pair.PairLinkWire], not this row). It always carries a [DefectType]. */
    MAINTENANCE_AFTER("maintenance_after", needsDefectType = true)
}

/**
 * The fault a maintenance scan is about - the FIVE values of contract A2/A3 `DEFECT_TYPE` (decided by the user 2026-10-22, E47): a closed list, no «other» and no free text; the screen is not a type
 * (the app does not run on a broken screen). The wire value is the constant's own spelling, in this exact case (`"battery"` is outside the list), and it is explicit, never `Enum.name` (R8).
 * A technician who does not find his fault leaves the purpose «عادي» (decision 2 of 2026-10-22): there is no sixth value. `MaintenancePairContractCopyTest` pins this list to the contract copy's table.
 */
enum class DefectType(val wire: String) {
    /** The battery was changed. */
    BATTERY("BATTERY"),

    /** The charging port or its flex cable. */
    CHARGING_PORT("CHARGING_PORT"),

    /** Cleaning or thermal paste. */
    THERMAL("THERMAL"),

    /** Random restarts, heating or network on the board. */
    MOTHERBOARD("MOTHERBOARD"),

    /** Flash, factory reset or a system update: the control group. */
    SOFTWARE("SOFTWARE")
}

/** Why a `setSessionPurpose` request was refused LOCALLY, before anything left the phone: the choice and the fault type do not go together (contract A2 addendum). */
enum class PurposeRequestRejection(val token: String) {
    /** A maintenance choice with no fault type: «`defectType` مطلوب حين `purpose ∈ {maintenance_before, maintenance_after}`». */
    DEFECT_TYPE_MISSING("defect_type_missing"),

    /** A fault type with `normal`: «ممنوع حين `normal`» (the server counts even a `null` key as sent; the app never sends the key). */
    DEFECT_TYPE_NOT_APPLICABLE("defect_type_not_applicable")
}

/** The result of building a `setSessionPurpose` request: the map to send, or the named local rejection. */
sealed interface PurposeRequestBuild {
    data class Built(val request: Map<String, Any>) : PurposeRequestBuild
    data class Rejected(val reason: PurposeRequestRejection) : PurposeRequestBuild
}

/** Thrown by [FirebaseAnalysisService.setSessionPurpose] when the request cannot be built ([reason]): nothing was sent. Our own text, no server text in it. */
class PurposeRequestRejectedException(val reason: PurposeRequestRejection) :
    IllegalArgumentException("setSessionPurpose request not built: ${reason.token}")

/**
 * The wire of the owner's callable `setSessionPurpose` (contract A2: «دالّة جديدة للمالك؛ يُقبل التغيير ما دامت الجلسة `pending`»), as amended on 2026-10-22: the request is
 * `{sessionId, purpose, defectType?}` - `defectType` is REQUIRED for `maintenance_before` and `maintenance_after` and FORBIDDEN for `normal` (copy `app/src/test/resources/contract/protocol-contract-A2-A3-maintenance-pairs-neuralcheck-backend-c289a2d.md`,
 * block A, A2 addendum «`setSessionPurpose({sessionId, purpose, defectType?})`»; from neuralcheck-backend@f3c1bab, a copy, it drifts - rule 29). The answer is unchanged (`{sessionId, purpose, purposeSource}`) and the app still reads none of it.
 *
 * [KEY_SESSION_ID] and [KEY_PURPOSE] follow the one pattern the app's other owner/participant session calls have (`cancelScanSession` is `{sessionId, reason}`); the server's own handler reads
 * them as `sessionId` and `purpose` (the contract's request line names them). Nothing of this reaches production meanwhile: the screen that would call it is behind
 * [net.neuralcheck.presentation.screen.PURPOSE_CHIPS_TEXTS_APPROVED].
 */
object SessionPurposeWire {
    /** The callable's name (contract A2). */
    const val CALLABLE = "setSessionPurpose"

    /** Request key (contract A2: `setSessionPurpose({sessionId, purpose, defectType?})`). */
    const val KEY_SESSION_ID = "sessionId"

    /** Request key (same line). */
    const val KEY_PURPOSE = "purpose"

    /** Request key, present ONLY for a maintenance purpose (same line): the key of a `normal` request is absent, not a `null` (the server counts a key that is there as sent). */
    const val KEY_DEFECT_TYPE = "defectType"

    /**
     * The request of `setSessionPurpose` for [choice] and [defectType]. A maintenance choice with no type, or `normal` with one, is [PurposeRequestBuild.Rejected] HERE - the app never sends a
     * combination the contract forbids (the server would refuse it with `DEFECT_TYPE_REQUIRED` / `DEFECT_TYPE_NOT_APPLICABLE`; a request that cannot be right does not leave the phone). The keys are
     * in the contract's order, so the golden samples are byte-stable.
     */
    fun request(sessionId: String, choice: ScanPurposeChoice, defectType: DefectType?): PurposeRequestBuild {
        purposeRequestRejection(choice, defectType)?.let { return PurposeRequestBuild.Rejected(it) }
        val request = linkedMapOf<String, Any>(KEY_SESSION_ID to sessionId, KEY_PURPOSE to choice.wire)
        if (defectType != null) request[KEY_DEFECT_TYPE] = defectType.wire
        return PurposeRequestBuild.Built(request)
    }
}

/**
 * Whether [choice] and [defectType] go together, and why not when they do not (`null` = they do): a maintenance choice needs a type, `normal` forbids one (contract A2 addendum of 2026-10-22). The one place
 * this rule is written: the wire builder ([SessionPurposeWire.request]) and the owner's state (`PurposeSelection`) both ask it.
 */
fun purposeRequestRejection(choice: ScanPurposeChoice, defectType: DefectType?): PurposeRequestRejection? = when {
    choice.needsDefectType && defectType == null -> PurposeRequestRejection.DEFECT_TYPE_MISSING
    !choice.needsDefectType && defectType != null -> PurposeRequestRejection.DEFECT_TYPE_NOT_APPLICABLE
    else -> null
}

/**
 * The success answer of `setSessionPurpose`, as far as the app reads it: nothing. The contract copy does not name its shape, so [replyKeys] only carries WHICH keys came back (null = the
 * answer was not an object) for the log line, and a success is the callable returning without a refusal (every refusal of the session callables is a `details.reasonCode` throw,
 * `SessionContract.kt`). The first field check tells the shape; until then no field of it is invented.
 */
data class SetSessionPurposeAnswer(val replyKeys: Set<String>?)

/**
 * The six parts a shop's protocol can have switched on (`users/{uid}.protocolParts`; contract A10: «`{purposes, batteryBeforeAfter, coldRepeat, returns, doubleEval, consent}`»;
 * «الغائب «غير مفعَّل»» - PLAN-06:787-788). The wire key is explicit, never `Enum.name` (R8). `batteryPairs` was the design's name and collided with an existing payload field; it was
 * renamed `batteryBeforeAfter` (contract A10, E42) and the old name is NOT exported - a reply that still carries it lands in [ProtocolPartsRead.Read.unknownKeys].
 */
enum class ProtocolPart(val wire: String) {
    PURPOSES("purposes"),
    BATTERY_BEFORE_AFTER("batteryBeforeAfter"),
    COLD_REPEAT("coldRepeat"),
    RETURNS("returns"),
    DOUBLE_EVAL("doubleEval"),
    CONSENT("consent")
}

/** How one part reads inside an object that lists parts. Four named states (rules 22, 25): none is read as another. */
sealed interface PartRead {
    /** The value is the JSON `true`. */
    data object Enabled : PartRead

    /** The value is the JSON `false`. */
    data object Disabled : PartRead

    /**
     * The part is not in the object. The contract gives this one a meaning - «الغائب «غير مفعَّل»» - so it is a declared state, not a guessed one: the part is off, and it is a
     * different state from [Disabled] (the server said no) and from [Unrecognized] (the server said something this app has no case for).
     */
    data object NotListed : PartRead

    /**
     * The key is there but its value is not a boolean ([kind]: `string`, `number`, `object`, `array`, `null`). The contract copy does not say a part is anything but on or off, so a
     * value of another shape is declared and NOT read as on: the part stays off for the app and the log says so.
     */
    data class Unrecognized(val kind: String) : PartRead
}

/**
 * What `createScanSession`'s answer says about the shop's parts. The reader of the key: it names what it did not find (rule 25) and tells a missing key from a part that is off.
 */
sealed interface ProtocolPartsRead {
    /** The answer carried no `protocolParts` (the key is missing or a JSON null): a server that does not send it yet, or an answer the app cannot say more about. No part is switched on. */
    data object Absent : ProtocolPartsRead

    /** The key is there but is not an object ([kind]: `string`, `number`, `boolean`, `array`). Nothing is read from it. */
    data class NotAnObject(val kind: String) : ProtocolPartsRead

    /** An object: [parts] has an entry for each of the six, and [unknownKeys] are the keys in it that are none of the six (sorted; declared, never ignored silently). */
    data class Read(val parts: Map<ProtocolPart, PartRead>, val unknownKeys: List<String>) : ProtocolPartsRead

    companion object {
        /** The JSON key in `createScanSession`'s answer (contract A2: «`createScanSession` تعيد `protocolParts` في ردّها»). */
        const val KEY = "protocolParts"

        fun of(element: JsonElement?): ProtocolPartsRead {
            if (element == null || element.isJsonNull) return Absent
            if (!element.isJsonObject) return NotAnObject(kindOf(element))
            val obj = element.asJsonObject
            val parts = ProtocolPart.values().associateWith { part ->
                if (!obj.has(part.wire)) {
                    PartRead.NotListed
                } else {
                    val value = obj.get(part.wire)
                    when {
                        value.isJsonPrimitive && value.asJsonPrimitive.isBoolean ->
                            if (value.asBoolean) PartRead.Enabled else PartRead.Disabled
                        else -> PartRead.Unrecognized(kindOf(value))
                    }
                }
            }
            val known = ProtocolPart.values().map { it.wire }.toSet()
            return Read(parts, obj.keySet().filter { it !in known }.sorted())
        }

        private fun kindOf(element: JsonElement): String = when {
            element.isJsonNull -> "null"
            element.isJsonObject -> "object"
            element.isJsonArray -> "array"
            element.asJsonPrimitive.isBoolean -> "boolean"
            element.asJsonPrimitive.isNumber -> "number"
            else -> "string"
        }
    }

    /** Whether the shop has the purpose chips: ONLY a part that reads [PartRead.Enabled]. Every other state of every other shape is "no". */
    fun purposesOffered(): Boolean = (this as? Read)?.parts?.get(ProtocolPart.PURPOSES) == PartRead.Enabled

    /**
     * Whether the shop has the two MAINTENANCE chips and the fault-type list: BOTH `purposes` AND `batteryBeforeAfter` read as enabled (decision of Hasan, Q31, 2026-10-22: the server's `setSessionPurpose` refuses a maintenance
     * purpose with `PURPOSE_PART_NOT_ENABLED` unless both parts are on, and refuses any other than «normal» unless `purposes` is on - so a shop with `purposes` alone must not be shown a chip that is then refused).
     * The ONE place this rule is written (rule 24): «عادي» needs [purposesOffered] alone. Every other state of either part, and a missing key, is "no" (rule 22).
     */
    fun maintenanceOffered(): Boolean = purposesOffered() && (this as? Read)?.parts?.get(ProtocolPart.BATTERY_BEFORE_AFTER) == PartRead.Enabled
}
