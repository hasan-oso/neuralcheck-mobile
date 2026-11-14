package net.neuralcheck.domain.pair

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import net.neuralcheck.domain.consent.ConsentLocale
import net.neuralcheck.domain.consent.ConsentLocaleRead
import net.neuralcheck.domain.form.TechnicianFormWire
import net.neuralcheck.domain.repeat.RepeatOfferRead
import net.neuralcheck.domain.repeat.TextRead
import net.neuralcheck.util.MarketingNameWire
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * The link of a «before maintenance» scan to an «after maintenance» scan - the APP half of the order of 2026-10-22 (decisions of Hasan 1-7; `evidence/run-2026-10-18/WIRE-SPEC-MAINTENANCE-PAIRS-2026-10-22.md`, section 3).
 * The server half (S2-S7 of the same order, neuralcheck-backend) is NOT deployed and was not run from here; this app builds against the CONTRACT, as copied in
 * `app/src/test/resources/contract/protocol-contract-A2-A3-maintenance-pairs-neuralcheck-backend-c289a2d.md` (block A, section A3; from neuralcheck-backend@f3c1bab `docs/PROTOCOL_CONTRACT.md` - rule 29: a copy, it
 * drifts; the names of the two callables are the server's PROPOSALS, pending Hasan's approval, ق33). A difference found on the server later is a difference to report, not to patch here.
 *
 * The wire, from that copy (A3, items 2 and 3):
 *  - `listOpenMaintenanceBefores({sessionId})` [A3 (2)]: the OWNER only, for a session whose purpose is `maintenance_after`. The answer is `{validityHours, candidates: [{beforeScanId, deviceName, scannedAtMs}]}`, newest first.
 *    `validityHours` is the shop's setting, an integer (the server refuses with `PAIR_VALIDITY_SETTING_ABSENT` rather than default it); `deviceName` is `{state, model, names, reason}` - `state` is `mapped`
 *    (names `{ar, tr, en}`) or `name_unmapped` (`names` null, `model` the phone's own model string, `reason` why); `scannedAtMs` is an integer of milliseconds (the ORDER writes `scannedAt`: the contract says
 *    `scannedAtMs`, and says that it reported the difference - this reader follows the contract and a `scannedAt` key is declared, never read as the time). NO hardware identifier is in the answer (none is stored).
 *  - `confirmMaintenanceAfterLink({sessionId, beforeScanId})` [A3 (3)]: the OWNER, the session `pending` or `scanning` and `maintenance_after`. The answer is `{sessionId, beforeScanId, linkMethod: "manual"}`;
 *    choosing again replaces the earlier choice until the scan completes. Nothing is written on the «before» at confirmation: the link becomes real when the «after» scan completes (the server, A3 item 4).
 *  - the refusals carry `details.reasonCode` [A3 (5)]: [PairRefusal]'s seventeen, and the session contract's four (`SESSION_NOT_PARTICIPANT`, `SESSION_EXPIRED`, `SESSION_EXPIRES_AT_INVALID`, `SESSION_STATUS_UNKNOWN`), read in
 *    `SessionErrorCode` and not spelled here (rule 24).
 *
 * The same reading discipline as `RepeatOfferRead` (rules 22, 25): the raw JSON comes in and each piece is one named state - a missing key, a value of another shape, a key the app has no case for - never a default
 * that fills a gap. An answer that does not read is [OpenBeforesRead.Unreadable] with a fixed token; the app never draws half of it. «The deviceName that is not mapped» is a state, not a name made up.
 */
object PairLinkWire {
    const val LIST_CALLABLE = "listOpenMaintenanceBefores" // [A3 (2)] a proposed name (ق33)
    const val CONFIRM_CALLABLE = "confirmMaintenanceAfterLink" // [A3 (3)] a proposed name (ق33)

    const val KEY_SESSION_ID = TechnicianFormWire.KEY_SESSION_ID // [A3 (2) (3)] request and the confirm's answer
    const val KEY_BEFORE_SCAN_ID = "beforeScanId" // [A3 (2) (3)] a candidate, the confirm's request and answer
    const val KEY_VALIDITY_HOURS = "validityHours" // [A3 (2)] the list's answer
    const val KEY_CANDIDATES = "candidates" // [A3 (2)] the list's answer
    const val KEY_DEVICE_NAME = "deviceName" // [A3 (2)] a candidate
    const val KEY_SCANNED_AT_MS = "scannedAtMs" // [A3 (2)] a candidate (the ORDER's `scannedAt` is not the contract's)
    const val KEY_NAME_STATE = "state" // [A3 (2)] inside `deviceName`
    const val KEY_NAME_MODEL = "model" // [A3 (2)] inside `deviceName`
    const val KEY_NAME_NAMES = "names" // [A3 (2)] inside `deviceName`
    const val KEY_NAME_REASON = "reason" // [A3 (2)] inside `deviceName`
    const val KEY_LINK_METHOD = "linkMethod" // [A3 (3)] the confirm's answer

    /** The three languages of `deviceName.names` [A3 (2): `names = {ar, tr, en}`]. */
    const val NAMES_KEY_AR = "ar"
    const val NAMES_KEY_TR = "tr"
    const val NAMES_KEY_EN = "en"

    // `deviceName.state` is `mapped` or `name_unmapped` [A3 (2); A12's `resolveMarketingName`]: the app's one source of those two words is `MarketingNameWire` (rule 24), read below.

    /** The confirm's `linkMethod`: «`"manual"` وحدها» [A3 item 1, `protocolPending`]; `auto_confirmed` is never written. */
    const val LINK_METHOD_MANUAL = "manual"

    internal val LIST_ANSWER_KEYS: Set<String> = setOf(KEY_VALIDITY_HOURS, KEY_CANDIDATES)
    internal val CANDIDATE_KEYS: Set<String> = setOf(KEY_BEFORE_SCAN_ID, KEY_DEVICE_NAME, KEY_SCANNED_AT_MS)
    internal val DEVICE_NAME_KEYS: Set<String> = setOf(KEY_NAME_STATE, KEY_NAME_MODEL, KEY_NAME_NAMES, KEY_NAME_REASON)
    internal val CONFIRM_ANSWER_KEYS: Set<String> = setOf(KEY_SESSION_ID, KEY_BEFORE_SCAN_ID, KEY_LINK_METHOD)

    /** The request of the list: `{sessionId}` and nothing else [A3 (2)]. */
    fun listRequest(sessionId: String): Map<String, Any> = linkedMapOf(KEY_SESSION_ID to sessionId)

    /** The request of the confirmation: `{sessionId, beforeScanId}` [A3 (3)]. The scan id is the server's handle, never shown to the user. */
    fun confirmRequest(sessionId: String, beforeScanId: String): Map<String, Any> = linkedMapOf(KEY_SESSION_ID to sessionId, KEY_BEFORE_SCAN_ID to beforeScanId)
}

/**
 * The refusals of the two callables that carry a name of their own [A3 (5); contract vocabulary `PAIR_CALL_REFUSAL` (eleven) and `PAIR_LINK_REFUSAL` (six of its eight)]. `AMBIGUOUS_SHORT_ID` and
 * `SESSION_CODE_NOT_REPORT_CODE` belong to the manual way (scanning the report's code), which is a second phase and is NOT built; they are not here, so a server that sent one would be an unrecognized code.
 * The session contract's four (`SESSION_NOT_PARTICIPANT` ...) are `SessionErrorCode`'s and are read there.
 */
enum class PairRefusal(val wire: String) {
    NOT_AUTHENTICATED("PAIR_NOT_AUTHENTICATED"),
    SESSION_ID_REQUIRED("PAIR_SESSION_ID_REQUIRED"),
    SESSION_NOT_AFTER("PAIR_SESSION_NOT_AFTER"),
    SESSION_NOT_ACTIVE("PAIR_SESSION_NOT_ACTIVE"),
    SESSION_DEFECT_TYPE_ABSENT("PAIR_SESSION_DEFECT_TYPE_ABSENT"),
    PART_NOT_ENABLED("PAIR_PART_NOT_ENABLED"),
    VALIDITY_SETTING_ABSENT("PAIR_VALIDITY_SETTING_ABSENT"),
    BEFORE_SCAN_ID_REQUIRED("PAIR_BEFORE_SCAN_ID_REQUIRED"),
    BEFORE_UNREADABLE("PAIR_BEFORE_UNREADABLE"),
    SCAN_CAP_EXCEEDED("PAIR_SCAN_CAP_EXCEEDED"),

    /** S8 (added by the server at b76c791, in the contract copy @f3c1bab): the confirmation of a «before» whose `pair.ownerId` differs from the `userId` of its report - an anomaly the server names and refuses with. */
    OWNER_MISMATCH("PAIR_OWNER_MISMATCH"),
    BEFORE_NOT_FOUND("BEFORE_NOT_FOUND"),
    BEFORE_NOT_OWNED("BEFORE_NOT_OWNED"),
    BEFORE_WRONG_PURPOSE("BEFORE_WRONG_PURPOSE"),
    BEFORE_WRONG_DEFECT_TYPE("BEFORE_WRONG_DEFECT_TYPE"),
    BEFORE_ALREADY_PAIRED("BEFORE_ALREADY_PAIRED"),
    BEFORE_EXPIRED("BEFORE_EXPIRED");

    companion object {
        fun ofWire(raw: String): PairRefusal? = values().firstOrNull { it.wire == raw }
    }
}

/**
 * Which of the six failure texts a refusal (or a failure with no refusal) is shown with. The texts are PROPOSED (`TEXTS-FOR-APPROVAL-MAINTENANCE-PAIRS-2026-10-22.md`, rows ص14 to ص19) and not written anywhere:
 * this is the mapping that decides WHICH one, and it is total over [PairRefusal] (an `when` with no `else`: a refusal added to the enum without a place here does not compile - rule 25).
 * Every `reasonCode` the proposal names goes to its own text; every other one - a named refusal with no text of its own, one this app has no case for, a failure with no `reasonCode` at all, an answer that does not read -
 * goes to the general text (ص18), and the log line carries the code (`PairLinkClient`).
 */
enum class PairFailureKind(val token: String) {
    /** ص14 «هذا الفحص غير متاح للربط»: `BEFORE_NOT_FOUND` · `BEFORE_NOT_OWNED` · `BEFORE_WRONG_PURPOSE` · `PAIR_OWNER_MISMATCH` (the coordinator's decision after S8: no new text, the code in the log). */
    NOT_AVAILABLE("not_available"),

    /** ص15 «هذا الفحص مربوط من قبل»: `BEFORE_ALREADY_PAIRED`. */
    ALREADY_PAIRED("already_paired"),

    /** ص16 «انتهت مدّة هذا الفحص ولا يمكن ربطه»: `BEFORE_EXPIRED`. */
    EXPIRED("expired"),

    /** ص17 «نوع العطل في هذا الفحص يختلف عمّا اخترتَ»: `BEFORE_WRONG_DEFECT_TYPE`. */
    WRONG_DEFECT_TYPE("wrong_defect_type"),

    /** ص19 «ميزة الصيانة غير مفعَّلة لحسابك»: `PAIR_PART_NOT_ENABLED` · `PAIR_VALIDITY_SETTING_ABSENT`. */
    NOT_ENABLED("part_not_enabled"),

    /** ص18 «تعذّر الربط. حاول مرة أخرى»: every other failure. */
    GENERAL("general")
}

/** The failure text for a refusal of the pair calls. Total: no `else`. */
fun failureKindOf(refusal: PairRefusal): PairFailureKind = when (refusal) {
    PairRefusal.BEFORE_NOT_FOUND, PairRefusal.BEFORE_NOT_OWNED, PairRefusal.BEFORE_WRONG_PURPOSE, PairRefusal.OWNER_MISMATCH -> PairFailureKind.NOT_AVAILABLE
    PairRefusal.BEFORE_ALREADY_PAIRED -> PairFailureKind.ALREADY_PAIRED
    PairRefusal.BEFORE_EXPIRED -> PairFailureKind.EXPIRED
    PairRefusal.BEFORE_WRONG_DEFECT_TYPE -> PairFailureKind.WRONG_DEFECT_TYPE
    PairRefusal.PART_NOT_ENABLED, PairRefusal.VALIDITY_SETTING_ABSENT -> PairFailureKind.NOT_ENABLED
    // named by the contract, no text of its own in the proposal: the general text, with the code in the log
    PairRefusal.NOT_AUTHENTICATED, PairRefusal.SESSION_ID_REQUIRED, PairRefusal.SESSION_NOT_AFTER, PairRefusal.SESSION_NOT_ACTIVE,
    PairRefusal.SESSION_DEFECT_TYPE_ABSENT, PairRefusal.BEFORE_SCAN_ID_REQUIRED, PairRefusal.BEFORE_UNREADABLE, PairRefusal.SCAN_CAP_EXCEEDED -> PairFailureKind.GENERAL
}

/** The failure text for a raw `details.reasonCode`: the refusal's own, or the general one for any other text, one the session contract names, or none at all (`null`). */
fun failureKindOf(reasonCode: String?): PairFailureKind = reasonCode?.let { PairRefusal.ofWire(it) }?.let { failureKindOf(it) } ?: PairFailureKind.GENERAL

// ---- reading the list's answer ----

/** The name of the candidate's phone as the server's `deviceName` said it: two states and nothing in between. */
sealed interface DeviceNameRead {
    /** `state = "mapped"`: the marketing name in each language (each a [TextRead], a language the map has no name for stays not-a-text), and the phone's model string if the answer carried it. */
    data class Mapped(val ar: TextRead, val tr: TextRead, val en: TextRead, val model: TextRead) : DeviceNameRead

    /** `state = "name_unmapped"`: `names` is null; [model] is the phone's own `Build.MODEL` as the server returned it; [reason] is the server's token (`MODEL_NOT_IN_MAP`, `MAP_ABSENT`, `MAP_UNREADABLE`, `MODEL_ABSENT`). */
    data class Unmapped(val model: TextRead, val reason: TextRead) : DeviceNameRead
}

/** One candidate «before» scan. [beforeScanId] is the server's handle - sent back on the confirmation, NEVER shown. */
data class PairCandidate(val beforeScanId: String, val deviceName: DeviceNameRead, val scannedAtMs: Long)

/** The whole answer of `listOpenMaintenanceBefores`. */
sealed interface OpenBeforesRead {
    /** The answer does not read ([why]: a fixed token naming the first thing that did not). No candidate is drawn from half an answer. */
    data class Unreadable(val why: String) : OpenBeforesRead

    /**
     * An answer that reads. [candidates] may be EMPTY (the contract: «`candidates` مصفوفة (فارغة حين لا مرشَّح)»): that is the «none open» state, not a failure. [unknownKeys] and [candidateUnknownKeys] are the keys
     * (of the answer, and of any candidate or device name) that are none of the contract's - sorted, declared, never ignored silently.
     */
    data class Read(val validityHours: Int, val candidates: List<PairCandidate>, val unknownKeys: List<String>, val candidateUnknownKeys: List<String>) : OpenBeforesRead

    companion object {
        /** Reads [element] as the answer to the list. */
        fun of(element: JsonElement?): OpenBeforesRead {
            if (element == null || element.isJsonNull) return Unreadable("absent")
            if (!element.isJsonObject) return Unreadable("not_an_object_${RepeatOfferRead.kindOf(element)}")
            val obj = element.asJsonObject
            val hours = when (val h = wholeNumber(obj.get(PairLinkWire.KEY_VALIDITY_HOURS))) {
                is Whole.Value -> if (h.value >= 1 && h.value <= Int.MAX_VALUE) h.value.toInt() else return Unreadable("validity_hours_out_of_range")
                is Whole.Bad -> return Unreadable("validity_hours_${h.why}")
            }
            val candidatesElement = obj.get(PairLinkWire.KEY_CANDIDATES)
            if (candidatesElement == null || candidatesElement.isJsonNull) return Unreadable("candidates_absent")
            if (!candidatesElement.isJsonArray) return Unreadable("candidates_not_an_array_${RepeatOfferRead.kindOf(candidatesElement)}")
            val candidateUnknown = sortedSetOf<String>()
            val candidates = ArrayList<PairCandidate>()
            for ((index, item) in candidatesElement.asJsonArray.withIndex()) {
                if (!item.isJsonObject) return Unreadable("candidate_${index}_not_an_object_${RepeatOfferRead.kindOf(item)}")
                val candidate = item.asJsonObject
                candidateUnknown += candidate.keySet().filter { it !in PairLinkWire.CANDIDATE_KEYS }
                val id = when (val t = RepeatOfferRead.textOf(candidate.get(PairLinkWire.KEY_BEFORE_SCAN_ID))) {
                    is TextRead.Text -> t.value
                    TextRead.Absent, TextRead.Null -> return Unreadable("candidate_${index}_before_scan_id_absent")
                    is TextRead.NotAString -> return Unreadable("candidate_${index}_before_scan_id_not_a_text")
                    is TextRead.Blank -> return Unreadable("candidate_${index}_before_scan_id_blank")
                }
                val at = when (val a = wholeNumber(candidate.get(PairLinkWire.KEY_SCANNED_AT_MS))) {
                    is Whole.Value -> if (a.value > 0) a.value else return Unreadable("candidate_${index}_scanned_at_ms_not_positive")
                    is Whole.Bad -> return Unreadable("candidate_${index}_scanned_at_ms_${a.why}")
                }
                val name = when (val n = deviceNameOf(candidate.get(PairLinkWire.KEY_DEVICE_NAME), candidateUnknown)) {
                    is NameParse.Read -> n.name
                    is NameParse.Bad -> return Unreadable("candidate_${index}_device_name_${n.why}")
                }
                candidates += PairCandidate(id, name, at)
            }
            if (candidates.map { it.beforeScanId }.toSet().size != candidates.size) return Unreadable("duplicate_before_scan_id")
            return Read(hours, candidates, obj.keySet().filter { it !in PairLinkWire.LIST_ANSWER_KEYS }.sorted(), candidateUnknown.toList())
        }

        private sealed interface Whole {
            data class Value(val value: Long) : Whole
            data class Bad(val why: String) : Whole
        }

        /** A JSON number with no fraction: a missing key, a null, another kind and a fractional number are four different [Whole.Bad] tokens. */
        private fun wholeNumber(element: JsonElement?): Whole {
            if (element == null || element.isJsonNull) return Whole.Bad("absent")
            if (!element.isJsonPrimitive || !element.asJsonPrimitive.isNumber) return Whole.Bad("not_a_number_${RepeatOfferRead.kindOf(element)}")
            val d = element.asDouble
            if (d.isNaN() || d.isInfinite() || d != Math.rint(d) || Math.abs(d) > 9.0e15) return Whole.Bad("not_whole")
            return Whole.Value(d.toLong())
        }

        private sealed interface NameParse {
            data class Read(val name: DeviceNameRead) : NameParse
            data class Bad(val why: String) : NameParse
        }

        private fun deviceNameOf(element: JsonElement?, unknown: MutableSet<String>): NameParse {
            if (element == null || element.isJsonNull) return NameParse.Bad("absent")
            if (!element.isJsonObject) return NameParse.Bad("not_an_object_${RepeatOfferRead.kindOf(element)}")
            val obj = element.asJsonObject
            unknown += obj.keySet().filter { it !in PairLinkWire.DEVICE_NAME_KEYS }
            val state = when (val s = RepeatOfferRead.textOf(obj.get(PairLinkWire.KEY_NAME_STATE))) {
                is TextRead.Text -> s.value
                else -> return NameParse.Bad("state_not_a_text")
            }
            val model = RepeatOfferRead.textOf(obj.get(PairLinkWire.KEY_NAME_MODEL))
            return when (state) {
                MarketingNameWire.MAPPED -> {
                    val names = obj.get(PairLinkWire.KEY_NAME_NAMES)
                    if (names == null || names.isJsonNull || !names.isJsonObject) return NameParse.Bad("mapped_without_names")
                    val n: JsonObject = names.asJsonObject
                    NameParse.Read(DeviceNameRead.Mapped(RepeatOfferRead.textOf(n.get(PairLinkWire.NAMES_KEY_AR)), RepeatOfferRead.textOf(n.get(PairLinkWire.NAMES_KEY_TR)), RepeatOfferRead.textOf(n.get(PairLinkWire.NAMES_KEY_EN)), model))
                }
                MarketingNameWire.NAME_UNMAPPED -> NameParse.Read(DeviceNameRead.Unmapped(model, RepeatOfferRead.textOf(obj.get(PairLinkWire.KEY_NAME_REASON))))
                else -> NameParse.Bad("state_unrecognized")
            }
        }
    }
}

// ---- reading the confirmation's answer ----

/** The answer of `confirmMaintenanceAfterLink`: the echo of the request and `linkMethod: "manual"`, or a named reason it is not that. */
sealed interface ConfirmAnswerRead {
    /** The server confirmed the choice for exactly this session and scan. */
    data object Confirmed : ConfirmAnswerRead

    /** The answer is not the confirmation of THIS request ([why]: a fixed token). */
    data class Unreadable(val why: String) : ConfirmAnswerRead

    companion object {
        fun of(element: JsonElement?, sessionId: String, beforeScanId: String): ConfirmAnswerRead {
            if (element == null || element.isJsonNull) return Unreadable("absent")
            if (!element.isJsonObject) return Unreadable("not_an_object_${RepeatOfferRead.kindOf(element)}")
            val obj = element.asJsonObject
            for ((key, expected, label) in listOf(
                Triple(PairLinkWire.KEY_SESSION_ID, sessionId, "session_id"),
                Triple(PairLinkWire.KEY_BEFORE_SCAN_ID, beforeScanId, "before_scan_id")
            )) {
                when (val t = RepeatOfferRead.textOf(obj.get(key))) {
                    is TextRead.Text -> if (t.value != expected) return Unreadable("${label}_differs")
                    else -> return Unreadable("${label}_missing")
                }
            }
            return when (val m = RepeatOfferRead.textOf(obj.get(PairLinkWire.KEY_LINK_METHOD))) {
                is TextRead.Text -> if (m.value == PairLinkWire.LINK_METHOD_MANUAL) Confirmed else Unreadable("link_method_not_manual")
                else -> Unreadable("link_method_absent")
            }
        }
    }
}

// ---- what a row says ----

/** The name a row shows. Three states: the marketing name, the phone's own model string (NOT a marketing name), or no name - never one made up. */
sealed interface CandidateName {
    /** The marketing name in the language the screen is drawn in. */
    data class Marketing(val text: String) : CandidateName

    /** No marketing name was available; this is the model string the phone reported, as the server returned it. [why] is a fixed token for the log. */
    data class Model(val text: String, val why: String) : CandidateName

    /** Neither a marketing name nor a model: the row has no name to show ([why]: a fixed token). */
    data class NoName(val why: String) : CandidateName
}

/** The name of [read] for the screen's [language]. A language the screen is not drawn in (or none) has no marketing name to choose: the model string stands, declared. */
fun nameFor(read: DeviceNameRead, language: ConsentLocaleRead): CandidateName {
    fun textOf(t: TextRead): String? = (t as? TextRead.Text)?.value
    return when (read) {
        is DeviceNameRead.Unmapped -> textOf(read.model)?.let { CandidateName.Model(it, "unmapped") } ?: CandidateName.NoName("unmapped_no_model")
        is DeviceNameRead.Mapped -> {
            val (name, why) = when (language) {
                is ConsentLocaleRead.Known -> (if (language.locale == ConsentLocale.AR) textOf(read.ar) else textOf(read.tr)) to "language_name_absent"
                is ConsentLocaleRead.Unsupported -> null to "language_not_supported"
                ConsentLocaleRead.Absent -> null to "language_absent"
            }
            when {
                name != null -> CandidateName.Marketing(name)
                else -> textOf(read.model)?.let { CandidateName.Model(it, why) } ?: CandidateName.NoName("${why}_no_model")
            }
        }
    }
}

/** What one row of the list shows: the name, the date and the time of the «before» scan (the server's `scannedAtMs`, drawn in the phone's own zone and language). */
data class PairRowParts(val name: CandidateName, val date: String, val time: String)

/** The row of [candidate]: the name for [language], and its time as [zone] and [locale] write the date and the hour. No identifier and no scan id is part of it. */
fun candidateRow(candidate: PairCandidate, language: ConsentLocaleRead, zone: ZoneId, locale: Locale): PairRowParts {
    val moment = Instant.ofEpochMilli(candidate.scannedAtMs).atZone(zone)
    return PairRowParts(
        name = nameFor(candidate.deviceName, language),
        date = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(moment),
        time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).format(moment)
    )
}
