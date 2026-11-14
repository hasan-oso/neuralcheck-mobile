package net.neuralcheck.domain.repeat

import com.google.gson.JsonElement
import net.neuralcheck.domain.form.TechnicianFormWire

/**
 * The cold and hot repeat scan - row A-P6-07, the APP half (`plan/PLAN-06-DATA-COLLECTION.md` section 4, lines 596-681; the row `:1048`). The server half is S-P6-07 (neuralcheck-backend `cccd027`, inside
 * `fd730ac`, NOT deployed on production): the server chooses which scans are offered a repeat, and classifies the second scan cold or hot (rule 20); this app only shows the offer, watches the phone cool,
 * and asks for the child session.
 *
 * The wire, from the protocol contract's A4 as copied in `evidence/run-2026-10-18/protocol-contract-A0-A10-neuralcheck-backend-fd730ac.md` (items (4), (5) and (6) of the 2026-10-21 addendum; neuralcheck-backend@fd730ac,
 * `docs/PROTOCOL_CONTRACT.md` - rule 29: a copy, it drifts; this app aligns to the server's code, not the reverse). Every name marked [A4] is that copy's; the server itself marks the names of the two callables'
 * fields as PROPOSED (A4 «اسم اقتراحي»), and a difference found on the server later is a difference to report, not to patch here:
 *  - `getRepeatScanOffer({sessionId, scanId})` [A4 (5); the name is the server's proposal - the design names no callable for the offer, PLAN-06:629-631]: the authorisation is the PAIR (any signed-in account that
 *    holds it, the anonymous one of the scanned phone included). The answer has SEVEN keys: `{sessionId, scanId, state, reason, firstScanStartTemp, childSessionId, childStatus}`. `state` is one of [RepeatState]'s
 *    eleven; `reason` is a text with `NOT_ELIGIBLE` only, else null; `firstScanStartTemp` is `{state, valueC}` (in °C, raw) or null for `NOT_ELIGIBLE` and `NOT_SELECTED` («لا ينطبق»); `childSessionId` a text or null;
 *    `childStatus` the child's status as read, or null. `EXPIRED` is computed at read time, never stored. The one side effect: the SESSION'S OWNER fetching a `SELECTED` offer moves it to `PROMPT_DELIVERED`, once.
 *  - `startRepeatScan({sessionId, scanId, startedBeforeCooling?})` [A4 (4); PLAN-06:639]: answer `{sessionId (the CHILD), parentSessionId, state: "STARTED", expiresInMinutes: 60}`. `startedBeforeCooling` is
 *    optional and a boolean: the client declares it when the technician presses «ابدأ رغم ذلك» (PLAN-06:648); its absence is stored as null, NOT as false, so this app sends it only as `true`.
 *  - the refusals carry `details.reasonCode` [A4 (6)]: twelve `REPEAT_*` codes and the existing `SESSION_STATUS_UNKNOWN` ([RepeatRefusal]).
 *
 * The same reading discipline as `TechnicianFormConfigRead` (rules 22, 25): the raw JSON comes in and each piece is one named state - a missing key, a value of another shape, a text that is none of the eleven -
 * never a default that fills a gap. The decisions ([decide], [coolingDecision]) are plain functions of what was read.
 */
object RepeatScanWire {
    const val OFFER_CALLABLE = "getRepeatScanOffer" // [A4 (5)] a proposed name
    const val START_CALLABLE = "startRepeatScan" // [A4 (4); PLAN-06:639]

    const val KEY_SESSION_ID = TechnicianFormWire.KEY_SESSION_ID // [A4 (4) (5)] the PARENT session on a request; the CHILD on the start's answer
    const val KEY_SCAN_ID = "scanId" // [A4 (4) (5)]
    const val KEY_STARTED_BEFORE_COOLING = "startedBeforeCooling" // [A4 (4)] optional, boolean
    const val KEY_STATE = "state" // [A4 (5)] and the start's answer
    const val KEY_REASON = "reason" // [A4 (5)]
    const val KEY_FIRST_SCAN_START_TEMP = "firstScanStartTemp" // [A4 (5)]
    const val KEY_TEMP_STATE = "state" // [A4 (1)] inside `firstScanStartTemp`
    const val KEY_TEMP_VALUE_C = "valueC" // [A4 (1)] inside `firstScanStartTemp`
    const val KEY_CHILD_SESSION_ID = "childSessionId" // [A4 (5)]
    const val KEY_CHILD_STATUS = "childStatus" // [A4 (5)]
    const val KEY_PARENT_SESSION_ID = "parentSessionId" // [A4 (4)] on the start's answer
    const val KEY_EXPIRES_IN_MINUTES = "expiresInMinutes" // [A4 (4)] on the start's answer

    /** The seven keys of the offer's answer [A4 (5)]; any other key is declared, never ignored silently. */
    internal val OFFER_KEYS: Set<String> = setOf(KEY_SESSION_ID, KEY_SCAN_ID, KEY_STATE, KEY_REASON, KEY_FIRST_SCAN_START_TEMP, KEY_CHILD_SESSION_ID, KEY_CHILD_STATUS)

    /** The four keys of the start's answer [A4 (4)]. */
    internal val START_KEYS: Set<String> = setOf(KEY_SESSION_ID, KEY_PARENT_SESSION_ID, KEY_STATE, KEY_EXPIRES_IN_MINUTES)

    /** The request of the offer: `{sessionId, scanId}`. */
    fun offerRequest(sessionId: String, scanId: String): Map<String, Any> = hashMapOf(KEY_SESSION_ID to sessionId, KEY_SCAN_ID to scanId)

    /**
     * The request of the start: `{sessionId, scanId}` and, only when the technician pressed «ابدأ رغم ذلك», `startedBeforeCooling: true` [A4 (4): its absence is stored as null; a `false` would be a claim the app
     * does not make - it knows the phone was cooled, but the contract's rule is the server's].
     */
    fun startRequest(sessionId: String, scanId: String, startedBeforeCooling: Boolean?): Map<String, Any> =
        hashMapOf<String, Any>(KEY_SESSION_ID to sessionId, KEY_SCAN_ID to scanId).also { if (startedBeforeCooling != null) it[KEY_STARTED_BEFORE_COOLING] = startedBeforeCooling }
}

/**
 * `repeat.state` [A4: the ten of PLAN-06:661-666 and the server's proposed eleventh, `COMPLETED_UNCLASSIFIED` - «الحالات العشر لا تحمل اكتمالاً لفحص ثانٍ غير مصنَّف»]. [startable] is the set the server
 * lets `startRepeatScan` accept («مختار للتكرار، ولم يبدأ»: A4 decision 3 - `DECLINED`, `PROMPT_DELIVERED` and `WAITING_COOLING` are not final).
 */
enum class RepeatState(val wire: String) {
    NOT_ELIGIBLE("NOT_ELIGIBLE"),
    NOT_SELECTED("NOT_SELECTED"),
    SELECTED("SELECTED"),
    PROMPT_DELIVERED("PROMPT_DELIVERED"),
    DECLINED("DECLINED"),
    WAITING_COOLING("WAITING_COOLING"),
    STARTED("STARTED"),
    COMPLETED_COLD("COMPLETED_COLD"),
    COMPLETED_HOT("COMPLETED_HOT"),
    COMPLETED_UNCLASSIFIED("COMPLETED_UNCLASSIFIED"),
    EXPIRED("EXPIRED");

    val startable: Boolean get() = this == SELECTED || this == PROMPT_DELIVERED || this == DECLINED || this == WAITING_COOLING
}

/** `firstScanStartTemp.state` [A4 (1): the five the server writes and reads back]. Only [PRESENT] carries a usable number. */
enum class FirstTempState(val wire: String) {
    PRESENT("PRESENT"),
    ABSENT("ABSENT"),

    /** `0.0` - the header's «no broadcast» sentinel (`docs/FV68_CONTRACT.md` 1.6): never a temperature. */
    SENTINEL_ZERO("SENTINEL_ZERO"),
    NOT_A_NUMBER("NOT_A_NUMBER"),
    STORED_MALFORMED("STORED_MALFORMED")
}

/** The refusals of the two callables [A4 (6): `details.reasonCode`]. `SESSION_STATUS_UNKNOWN` is the session contract's (`SessionErrorCode`) and is read there, not spelled here (rule 24). */
enum class RepeatRefusal(val wire: String) {
    NOT_AUTHENTICATED("REPEAT_NOT_AUTHENTICATED"),
    SESSION_ID_REQUIRED("REPEAT_SESSION_ID_REQUIRED"),
    SCAN_ID_REQUIRED("REPEAT_SCAN_ID_REQUIRED"),
    STARTED_BEFORE_COOLING_INVALID("REPEAT_STARTED_BEFORE_COOLING_INVALID"),
    PAIR_NOT_FOUND("REPEAT_PAIR_NOT_FOUND"),
    SESSION_NOT_COMPLETED("REPEAT_SESSION_NOT_COMPLETED"),
    RECORD_ABSENT("REPEAT_RECORD_ABSENT"),
    STATE_UNKNOWN("REPEAT_STATE_UNKNOWN"),
    NOT_SELECTED("REPEAT_NOT_SELECTED"),
    ALREADY_STARTED("REPEAT_ALREADY_STARTED"),
    PARENT_OWNER_UNKNOWN("REPEAT_PARENT_OWNER_UNKNOWN"),

    /** The one the server's user decision approved by name (E41): the parent session has no joined phone to copy to the child. */
    PARENT_JOINER_UNKNOWN("REPEAT_PARENT_JOINER_UNKNOWN");

    companion object {
        fun ofWire(raw: String): RepeatRefusal? = values().firstOrNull { it.wire == raw }
    }
}

// ---- reading the offer's answer ----

/** A text key of the answer: missing, null, not a text, or the text. A blank text is its own state, never "no text". */
sealed interface TextRead {
    data object Absent : TextRead
    data object Null : TextRead
    data class NotAString(val kind: String) : TextRead
    data class Blank(val raw: String) : TextRead
    data class Text(val value: String) : TextRead
}

/** `state` of the answer. */
sealed interface RepeatStateRead {
    data object Absent : RepeatStateRead
    data class NotAString(val kind: String) : RepeatStateRead
    data class Known(val state: RepeatState) : RepeatStateRead

    /** A text that is none of the eleven ([raw] is the server's, bounded when printed). */
    data class Unrecognized(val raw: String) : RepeatStateRead
}

/** `firstScanStartTemp` of the answer: the reference the phone must cool back to. */
sealed interface FirstTempRead {
    /** The key is missing. */
    data object Absent : FirstTempRead

    /** A JSON null: «لا ينطبق» (the server's answer for `NOT_ELIGIBLE` and `NOT_SELECTED`). */
    data object NotApplicable : FirstTempRead

    /** Not an object, or an object whose `state` is missing, not a text, or none of the five, or `PRESENT` without a finite number ([why]: a fixed token). */
    data class Malformed(val why: String) : FirstTempRead

    /** `PRESENT` with a finite number: [valueC] is the raw °C the server stored, never rounded here (rule 20). */
    data class Present(val valueC: Double) : FirstTempRead

    /** One of the four states that carry no usable temperature ([state] is never [FirstTempState.PRESENT]). */
    data class NotUsable(val state: FirstTempState) : FirstTempRead
}

/** The echo of the request in the answer (`sessionId`, `scanId`): an answer for another pair is not this request's answer. */
sealed interface EchoRead {
    data object Matches : EchoRead
    data class Differs(val key: String) : EchoRead
    data class Missing(val key: String) : EchoRead
}

/** The whole answer of `getRepeatScanOffer`. */
sealed interface RepeatOfferRead {
    /** No answer, a JSON null, or not an object ([why]: `absent`, `not_an_object_<kind>`). */
    data class Unreadable(val why: String) : RepeatOfferRead

    /** An object: each piece is its own named state. [unknownKeys] are the keys that are none of the seven (sorted). */
    data class Read(
        val echo: EchoRead,
        val state: RepeatStateRead,
        val reason: TextRead,
        val firstScanStartTemp: FirstTempRead,
        val childSessionId: TextRead,
        val childStatus: TextRead,
        val unknownKeys: List<String>
    ) : RepeatOfferRead

    companion object {
        /** Reads [element] as the answer to the offer asked for [sessionId] / [scanId]. */
        fun of(element: JsonElement?, sessionId: String, scanId: String): RepeatOfferRead {
            if (element == null || element.isJsonNull) return Unreadable("absent")
            if (!element.isJsonObject) return Unreadable("not_an_object_${kindOf(element)}")
            val obj = element.asJsonObject
            return Read(
                echo = echoOf(obj, sessionId, scanId),
                state = stateOf(obj.get(RepeatScanWire.KEY_STATE)),
                reason = textOf(obj.get(RepeatScanWire.KEY_REASON)),
                firstScanStartTemp = firstTempOf(obj.get(RepeatScanWire.KEY_FIRST_SCAN_START_TEMP)),
                childSessionId = textOf(obj.get(RepeatScanWire.KEY_CHILD_SESSION_ID)),
                childStatus = textOf(obj.get(RepeatScanWire.KEY_CHILD_STATUS)),
                unknownKeys = obj.keySet().filter { it !in RepeatScanWire.OFFER_KEYS }.sorted()
            )
        }

        private fun echoOf(obj: com.google.gson.JsonObject, sessionId: String, scanId: String): EchoRead {
            for ((key, expected) in listOf(RepeatScanWire.KEY_SESSION_ID to sessionId, RepeatScanWire.KEY_SCAN_ID to scanId)) {
                val v = obj.get(key)
                if (v == null || v.isJsonNull || !v.isJsonPrimitive || !v.asJsonPrimitive.isString) return EchoRead.Missing(key)
                if (v.asString != expected) return EchoRead.Differs(key)
            }
            return EchoRead.Matches
        }

        private fun stateOf(element: JsonElement?): RepeatStateRead {
            if (element == null || element.isJsonNull) return RepeatStateRead.Absent
            if (!element.isJsonPrimitive || !element.asJsonPrimitive.isString) return RepeatStateRead.NotAString(kindOf(element))
            val raw = element.asString
            val known = RepeatState.values().firstOrNull { it.wire == raw } ?: return RepeatStateRead.Unrecognized(raw)
            return RepeatStateRead.Known(known)
        }

        internal fun textOf(element: JsonElement?): TextRead {
            if (element == null) return TextRead.Absent
            if (element.isJsonNull) return TextRead.Null
            if (!element.isJsonPrimitive || !element.asJsonPrimitive.isString) return TextRead.NotAString(kindOf(element))
            val raw = element.asString
            return if (raw.isBlank()) TextRead.Blank(raw) else TextRead.Text(raw)
        }

        private fun firstTempOf(element: JsonElement?): FirstTempRead {
            if (element == null) return FirstTempRead.Absent
            if (element.isJsonNull) return FirstTempRead.NotApplicable
            if (!element.isJsonObject) return FirstTempRead.Malformed("not_an_object_${kindOf(element)}")
            val obj = element.asJsonObject
            val stateElement = obj.get(RepeatScanWire.KEY_TEMP_STATE)
            if (stateElement == null || stateElement.isJsonNull) return FirstTempRead.Malformed("state_absent")
            if (!stateElement.isJsonPrimitive || !stateElement.asJsonPrimitive.isString) return FirstTempRead.Malformed("state_not_a_string_${kindOf(stateElement)}")
            val state = FirstTempState.values().firstOrNull { it.wire == stateElement.asString } ?: return FirstTempRead.Malformed("state_unrecognized")
            if (state != FirstTempState.PRESENT) return FirstTempRead.NotUsable(state)
            val value = obj.get(RepeatScanWire.KEY_TEMP_VALUE_C)
            if (value == null || value.isJsonNull) return FirstTempRead.Malformed("present_without_value")
            if (!value.isJsonPrimitive || !value.asJsonPrimitive.isNumber) return FirstTempRead.Malformed("value_not_a_number_${kindOf(value)}")
            val d = value.asDouble
            if (d.isNaN() || d.isInfinite()) return FirstTempRead.Malformed("value_not_finite")
            return FirstTempRead.Present(d)
        }

        internal fun kindOf(element: JsonElement): String = when {
            element.isJsonNull -> "null"
            element.isJsonObject -> "object"
            element.isJsonArray -> "array"
            element.asJsonPrimitive.isBoolean -> "boolean"
            element.asJsonPrimitive.isNumber -> "number"
            else -> "string"
        }
    }
}

/** The reference temperature of a startable offer: a usable number, or one of the four states that carry none. */
sealed interface StartableReference {
    data class Usable(val valueC: Double) : StartableReference
    data class NotUsable(val state: FirstTempState) : StartableReference
}

/**
 * What the offer means, whoever asks. [Startable] is the only decision that offers a repeat: the state is one the server lets `startRepeatScan` accept. Everything else is a named reason to offer nothing,
 * and the screen draws nothing for it (the plan names no text for them) - but it is a state, in the log, never a blank.
 */
sealed interface RepeatDecision {
    val token: String

    /** `SELECTED`, `PROMPT_DELIVERED`, `DECLINED` or `WAITING_COOLING`: the phone was chosen and the repeat has not started. [reference] is the first scan's start temperature the answer carried, usable or not. */
    data class Startable(val state: RepeatState, val reference: StartableReference) : RepeatDecision {
        override val token: String get() = "startable_${state.wire.lowercase()}"
    }

    /** `NOT_ELIGIBLE` or `NOT_SELECTED`: no repeat for this scan ([reason] is the server's reason token for `NOT_ELIGIBLE`, `-` for the other). */
    data class NotApplicable(val state: RepeatState, val reason: String) : RepeatDecision {
        override val token: String get() = "not_applicable_${state.wire.lowercase()}"
    }

    /** `STARTED`, `COMPLETED_*` or `EXPIRED`: the repeat is under way, done or gone - nothing to offer. */
    data class NotStartable(val state: RepeatState) : RepeatDecision {
        override val token: String get() = "not_startable_${state.wire.lowercase()}"
    }

    /** The answer cannot be read as any of the above; [why] is a fixed token naming the first thing that did not read (no server text in it). */
    data class Unreadable(val why: String) : RepeatDecision {
        override val token: String get() = "unreadable_$why"
    }
}

/**
 * The decision for an offer's answer, in this order (the first that applies decides): an answer that is not an object; the echo of the request (missing, or for another pair); `state` missing, not a text, or none
 * of the eleven; for `NOT_ELIGIBLE` the reason - not a usable text is unreadable; for `NOT_ELIGIBLE` and `NOT_SELECTED` the reference must be null («لا ينطبق»); for a startable state the reference must be an object
 * (a usable one or a named «not usable»); the other states carry whatever the server sent and are not read further. Anything else is as the server said.
 */
fun decide(read: RepeatOfferRead): RepeatDecision {
    val r = when (read) {
        is RepeatOfferRead.Unreadable -> return RepeatDecision.Unreadable(read.why)
        is RepeatOfferRead.Read -> read
    }
    when (val e = r.echo) {
        EchoRead.Matches -> Unit
        is EchoRead.Missing -> return RepeatDecision.Unreadable("echo_${e.key}_missing")
        is EchoRead.Differs -> return RepeatDecision.Unreadable("echo_${e.key}_differs")
    }
    val state = when (val s = r.state) {
        RepeatStateRead.Absent -> return RepeatDecision.Unreadable("state_absent")
        is RepeatStateRead.NotAString -> return RepeatDecision.Unreadable("state_not_a_string_${s.kind}")
        is RepeatStateRead.Unrecognized -> return RepeatDecision.Unreadable("state_unrecognized")
        is RepeatStateRead.Known -> s.state
    }
    return when (state) {
        RepeatState.NOT_ELIGIBLE -> {
            val reason = when (val t = r.reason) {
                is TextRead.Text -> t.value
                else -> return RepeatDecision.Unreadable("not_eligible_without_reason")
            }
            if (r.firstScanStartTemp != FirstTempRead.NotApplicable) return RepeatDecision.Unreadable("not_applicable_temp_not_null")
            RepeatDecision.NotApplicable(state, reason)
        }
        RepeatState.NOT_SELECTED -> {
            if (r.firstScanStartTemp != FirstTempRead.NotApplicable) return RepeatDecision.Unreadable("not_applicable_temp_not_null")
            RepeatDecision.NotApplicable(state, "-")
        }
        else -> if (!state.startable) RepeatDecision.NotStartable(state) else when (val t = r.firstScanStartTemp) {
            FirstTempRead.Absent -> RepeatDecision.Unreadable("temp_absent")
            FirstTempRead.NotApplicable -> RepeatDecision.Unreadable("temp_null_on_startable")
            is FirstTempRead.Malformed -> RepeatDecision.Unreadable("temp_${t.why}")
            is FirstTempRead.Present -> RepeatDecision.Startable(state, StartableReference.Usable(t.valueC))
            is FirstTempRead.NotUsable -> RepeatDecision.Startable(state, StartableReference.NotUsable(t.state))
        }
    }
}

// ---- the answer of the start ----

/** How the answer of `startRepeatScan` reads: a child session, or a named reason it is not one. */
sealed interface RepeatStartRead {
    /** `state: "STARTED"`, a usable child id, the parent echoed. [expiresInMinutes] is the server's lifetime or null when the answer named none (never defaulted). */
    data class Started(val childSessionId: String, val expiresInMinutes: Int?) : RepeatStartRead

    /** The answer is not a start; [why] is a fixed token. */
    data class Unreadable(val why: String) : RepeatStartRead

    companion object {
        fun of(element: JsonElement?, parentSessionId: String): RepeatStartRead {
            if (element == null || element.isJsonNull) return Unreadable("absent")
            if (!element.isJsonObject) return Unreadable("not_an_object_${RepeatOfferRead.kindOf(element)}")
            val obj = element.asJsonObject
            val state = obj.get(RepeatScanWire.KEY_STATE)
            if (state == null || state.isJsonNull) return Unreadable("state_absent")
            if (!state.isJsonPrimitive || !state.asJsonPrimitive.isString) return Unreadable("state_not_a_string_${RepeatOfferRead.kindOf(state)}")
            if (state.asString != RepeatState.STARTED.wire) return Unreadable("state_not_started")
            val parent = RepeatOfferRead.textOf(obj.get(RepeatScanWire.KEY_PARENT_SESSION_ID))
            if (parent !is TextRead.Text) return Unreadable("parent_session_id_not_a_text")
            if (parent.value != parentSessionId) return Unreadable("parent_session_id_differs")
            val child = when (val c = RepeatOfferRead.textOf(obj.get(RepeatScanWire.KEY_SESSION_ID))) {
                is TextRead.Text -> c.value
                else -> return Unreadable("child_session_id_not_a_text")
            }
            if (child == parentSessionId) return Unreadable("child_session_id_is_the_parent")
            val minutes = obj.get(RepeatScanWire.KEY_EXPIRES_IN_MINUTES)
                ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }
                ?.asDouble?.takeIf { it == Math.rint(it) && it > 0 && it <= Int.MAX_VALUE }?.toInt()
            return Started(child, minutes)
        }
    }
}

// ---- the cooling check, on the scanned phone ----

/**
 * The cooling check [PLAN-06:633-636, «التحقّق التلقائي (القرار ٣)»: «التطبيق يقرأ حرارة البطارية من البثّ ... وحين |الحالية − الأولى| ≤ 1° يصير الزرّ «ابدأ الفحص المكرّر»»; and the decision's own words,
 * PLAN-06:599, «إلا إذا عادت حرارة البطارية إلى ±1° من بداية الفحص الأول»]. All arithmetic is in TENTHS of a degree, as integers: the broadcast is in tenths already, and the server rounds both temperatures to a
 * tenth before it takes the difference (A4 (1): `32.2 - 31.2 = 1.0000000000000036` in floating point), so the same threshold read two ways would disagree at exactly one degree.
 */
object Cooling {
    /** ±1 degree [PLAN-06:599, :636; the server's `thresholdC: 1`, A4 (1)], in tenths. */
    const val THRESHOLD_TENTHS = 10

    /** The raw °C of the first scan's start, rounded to a tenth (half up) as the server rounds it. */
    fun tenthsOf(valueC: Double): Int = Math.round(valueC * 10.0).toInt()
}

/** One reading of the phone's battery temperature: tenths of a degree, or a named reason there is none (PLAN-06:670: «فشل قراءة الحرارة في الهاتف»). */
sealed interface TempRead {
    data class Present(val tenths: Int) : TempRead

    /** [why] is a fixed token (`no_battery_intent`, `no_temperature_extra`, `zero_is_the_missing_value`). */
    data class Unreadable(val why: String) : TempRead
}

/** Which side of the reference the phone is on when it is not within ±1 degree. */
enum class CoolingSide { ABOVE, BELOW }

sealed interface CoolingDecision {
    /** Within ±1 degree of the first scan's start: «جاهز — ابدأ الفحص المكرّر». */
    data object Ready : CoolingDecision

    /**
     * Not within ±1 degree. [progress] is 0..1 toward the target, for a bar with no number (PLAN-06:635, «شريطاً نحو الهدف بلا أرقام»): the share of the largest excess seen so far that has been shed; 0 when the
     * phone is [CoolingSide.BELOW] the reference (PLAN-06 names no state for a phone colder than the first scan's start by more than a degree: it is treated as not ready, like a warm one - reported).
     */
    data class NotCooled(val side: CoolingSide, val progress: Float) : CoolingDecision

    /** There is no reading: no «ready» button, and the text «تعذّر قراءة حرارة البطارية» (PLAN-06:670). */
    data class TemperatureUnreadable(val why: String) : CoolingDecision
}

/** The excess over the cooled band's upper edge, in tenths (> 0 only when the phone is warmer than the reference by more than a degree). */
fun excessTenths(referenceTenths: Int, currentTenths: Int): Int = (currentTenths - (referenceTenths + Cooling.THRESHOLD_TENTHS)).coerceAtLeast(0)

/**
 * The decision for the phone's [read] against the first scan's [referenceTenths]; [peakExcessTenths] is the largest [excessTenths] seen so far on this screen (the caller keeps it), which makes the bar's
 * progress the share already shed.
 */
fun coolingDecision(referenceTenths: Int, read: TempRead, peakExcessTenths: Int): CoolingDecision = when (read) {
    is TempRead.Unreadable -> CoolingDecision.TemperatureUnreadable(read.why)
    is TempRead.Present -> {
        val diff = read.tenths - referenceTenths
        when {
            Math.abs(diff) <= Cooling.THRESHOLD_TENTHS -> CoolingDecision.Ready
            diff < 0 -> CoolingDecision.NotCooled(CoolingSide.BELOW, 0f)
            else -> {
                val excess = excessTenths(referenceTenths, read.tenths)
                val peak = maxOf(peakExcessTenths, excess)
                CoolingDecision.NotCooled(CoolingSide.ABOVE, (1f - excess.toFloat() / peak.toFloat()).coerceIn(0f, 1f))
            }
        }
    }
}
