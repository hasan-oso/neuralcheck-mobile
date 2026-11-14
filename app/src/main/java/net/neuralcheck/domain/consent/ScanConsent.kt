package net.neuralcheck.domain.consent

import com.google.gson.JsonElement
import net.neuralcheck.domain.form.TechnicianFormWire

/**
 * The consent screen of the scanned phone - row A-P6-12, the APP half (`plan/PLAN-06-DATA-COLLECTION.md` section 11, lines 1377-1413; the row `:1053`; the screen `ف١٠`, `:952`). The server half is S-P6-12
 * (neuralcheck-backend `5ab5eaf`, inside `5361116`, NOT deployed on production). The server records the consent's VERSION and LANGUAGE and nothing else: the text itself is shown by this app and never passes
 * through the server (contract A8, «لا نصّ يمرّ بالخادم»).
 *
 * The wire, from the protocol contract's A8 as copied in `evidence/run-2026-10-18/protocol-contract-A8-A11-A12-neuralcheck-backend-5361116.md` (items (1), (4) and (5) of the 2026-10-21 addendum;
 * neuralcheck-backend@5361116, `docs/PROTOCOL_CONTRACT.md:293-316` - rule 29: a copy, it drifts; this app aligns to the server's code, not the reverse). The names of the callable, of its fields and of the
 * refusals are the SERVER'S PROPOSALS (A8 «اسم الدالّة والمدخل والردّ ورموز الرفض وقيمتا اللغة `ar` · `tr` اقتراحية»), and a difference found on the server later is a difference to report, not to patch here:
 *  - `recordScanConsent({sessionId, textVersion, locale})` [A8 (1)]: the JOINED device only, the session `scanning` only. `textVersion` is an integer from 1 to the server's current version (1 was E11,
 *    approved 2026-10-15; 2 from the server's commit of 2026-10-23, [ConsentTextVersion]); `locale` is `ar` or `tr`; NONE has a default on the server: a missing one is a refusal. The answer is `{sessionId, state: "accepted", textVersion, locale, alreadyRecorded}` -
 *    the STORED values: a second call answers the first one's, whatever it sent.
 *  - «لا أوافق» is NOT a call of this file: it is `cancelScanSession({sessionId, reason: "consent_declined"})`, the call that has existed since batch 9 [A8 (4)] (`SessionLeaver.decline`); no identifier is sent.
 *  - the refusals carry `details.reasonCode` [A8 (5)]: nine `CONSENT_*` codes ([ConsentRefusal]) and four the session contract already names (`SessionErrorCode`: `SESSION_NOT_PARTICIPANT`,
 *    `SESSION_EXPIRED`, `SESSION_EXPIRES_AT_INVALID`, `SESSION_STATUS_UNKNOWN`).
 *
 * The same reading discipline as `RepeatOfferRead` (rules 22, 25): the raw JSON comes in and each piece is one named state - a missing key, a value of another shape, a state that is not «accepted» - never a
 * default that fills a gap, because «الموافقة غير المسجَّلة لا تُقرأ موافقة» (PLAN-06:1402): an answer that cannot be read is NOT a recorded consent.
 */
object ConsentWire {
    const val CALLABLE = "recordScanConsent" // [A8 (1)] a name the server proposed

    const val KEY_SESSION_ID = TechnicianFormWire.KEY_SESSION_ID // [A8 (1)] request and answer
    const val KEY_TEXT_VERSION = "textVersion" // [A8 (1)] request and answer
    const val KEY_LOCALE = "locale" // [A8 (1)] request and answer
    const val KEY_STATE = "state" // [A8 (1)] the answer
    const val KEY_ALREADY_RECORDED = "alreadyRecorded" // [A8 (1)] the answer

    /** The key of `joinScanSession`'s answer that says whether the shop has the consent part on (ق15); read through [ConsentPartRead]. The one place it is spelled (rule 24). */
    const val KEY_CONSENT_ENABLED = "consentEnabled"

    /** The one `state` the call answers [A8 (1): `state: "accepted"`]; `declined` is never written by the server [A8 (4)]. */
    const val STATE_ACCEPTED = "accepted"

    /** The five keys of the answer [A8 (1)]; any other key is declared, never ignored silently. */
    internal val ANSWER_KEYS: Set<String> = setOf(KEY_SESSION_ID, KEY_STATE, KEY_TEXT_VERSION, KEY_LOCALE, KEY_ALREADY_RECORDED)

    /** The request: `{sessionId, textVersion, locale}` - the three keys the server requires, none with a default [A8 (1)]. */
    fun request(sessionId: String, textVersion: Int, locale: ConsentLocale): Map<String, Any> =
        hashMapOf(KEY_SESSION_ID to sessionId, KEY_TEXT_VERSION to textVersion, KEY_LOCALE to locale.wire)
}

/**
 * The version of the consent text this app shows [A8: `CONSENT_TEXT_VERSION`; version 1 was E11, approved 2026-10-15: `evidence/station2-plan/table-p623-remaining-26-2026-10-15.md:24`]. The server accepts a
 * version from 1 to its own current one: an older one is recorded as the version the user read, a newer one is refused. «رفع `CONSENT_TEXT_VERSION` يلزم عند كل تعديل لنصّ شاشة الموافقة بأيّ لغة» [A8 (5)
 * decision 1]: `ConsentTextVersionTest` pins the wording of the current version, so a change of a word fails it until this is raised.
 *
 * VERSION 2 (2026-10-23): the text was reworded under Hasan's delegation of 2026-10-22 (the sentence about keeping a code «لا يمكن أن يُستعاد» and the retention sentence «تُحفظ نتائج الفحص سنتين» are gone - the
 * code proves neither; the microphone sentence is its own paragraph; the failure texts exist) and both languages are written in the resources. The server's own current version is 2 (the coordinator's message of
 * 2026-10-23: `protocolVocabulary.js:55`): a build that sends 2 must not reach a shop before the server's commit that raises it is deployed - a newer version than the server's own is REFUSED.
 */
object ConsentTextVersion {
    const val CURRENT = 2
}

/**
 * Whether the shop has the consent part on, as the join's answer says it (contract A8 after ق15: `joinScanSession` answers `{sessionId, ownerId, status, evaluationEnabled, consentEnabled}`; `consentEnabled` is a
 * boolean, always, and is true if and only if the owner's `protocolParts.consent` is true - `consentPartDecision`, neuralcheck-backend `functions/src/sessions/consentPartState.js:36`, as the coordinator relayed it
 * on 2026-10-23: a copy, it drifts, rule 29). The app reads it ONCE, at the join, and the screens that end a join route by it: the consent screen is for [Enabled] only. A shop whose part is [NotEnabled] has no
 * consent screen and the scan goes on; an answer that does not carry the key (an older server, a JSON null) is [Absent] - a state of its own, never `false` and never `true` (rule 22): it also has no consent
 * screen, because a consent that the server will not record must not be asked for, and the absence is declared (`join-consent-part ... consentEnabled=absent`, and the join's state carries it).
 */
sealed interface ConsentPartRead {
    data object Enabled : ConsentPartRead
    data object NotEnabled : ConsentPartRead
    data object Absent : ConsentPartRead

    companion object {
        fun of(consentEnabled: Boolean?): ConsentPartRead = when (consentEnabled) {
            true -> Enabled
            false -> NotEnabled
            null -> Absent
        }
    }
}

/** The two values of `locale` [A8 (1): `ar` · `tr`, a closed list]. */
enum class ConsentLocale(val wire: String) {
    AR("ar"),
    TR("tr");

    companion object {
        fun ofWire(raw: String): ConsentLocale? = values().firstOrNull { it.wire == raw }
    }
}

/**
 * The language the screen is DRAWN in, read as the contract's value. There is no default (rule 22 and its addendum): a language the server has no value for is [Unsupported], a missing one is [Absent], and the
 * screen draws nothing for either - the consent it would record would be for a text the user did not read.
 */
sealed interface ConsentLocaleRead {
    data class Known(val locale: ConsentLocale) : ConsentLocaleRead

    /** A language that is neither `ar` nor `tr` ([raw] is the app's own text). */
    data class Unsupported(val raw: String) : ConsentLocaleRead
    data object Absent : ConsentLocaleRead

    companion object {
        fun of(language: String?): ConsentLocaleRead {
            if (language.isNullOrBlank()) return Absent
            val known = ConsentLocale.ofWire(language) ?: return Unsupported(language)
            return Known(known)
        }
    }
}

/**
 * The nine `CONSENT_*` refusals of `recordScanConsent` [A8 (5): `details.reasonCode`; the ninth, [PART_NOT_ENABLED], is ق40 of 2026-10-23]. The four the session contract already names are read as `SessionErrorCode`
 * and are not spelled here (rule 24). The server's message is its technical code and is never shown (A8 (5): «لا نصّ عربي جديد يصل مستخدماً»).
 */
enum class ConsentRefusal(val wire: String) {
    NOT_AUTHENTICATED("CONSENT_NOT_AUTHENTICATED"),
    SESSION_ID_REQUIRED("CONSENT_SESSION_ID_REQUIRED"),
    TEXT_VERSION_REQUIRED("CONSENT_TEXT_VERSION_REQUIRED"),
    TEXT_VERSION_INVALID("CONSENT_TEXT_VERSION_INVALID"),
    LOCALE_REQUIRED("CONSENT_LOCALE_REQUIRED"),
    LOCALE_INVALID("CONSENT_LOCALE_INVALID"),

    /** The session is not `scanning` any more: it completed, was cancelled or expired. */
    SESSION_NOT_SCANNING("CONSENT_SESSION_NOT_SCANNING"),

    /** A consent record that is stored in a shape the server cannot read: it is not written over. */
    STORED_UNREADABLE("CONSENT_STORED_UNREADABLE"),

    /**
     * ق40 (`failed-precondition`, neuralcheck-backend `sessionNames.js:193`, as relayed 2026-10-23): the shop has no consent part, so the server refuses to record one. This is NOT a failure that stops the scan:
     * it means «لا ينطبق» - there is no consent to ask of this shop (`ConsentUi.NotApplicable`, the screen moves on). It reaches the app only if the join's `consentEnabled` said true and the part was switched
     * off before the user pressed accept.
     */
    PART_NOT_ENABLED("CONSENT_PART_NOT_ENABLED");

    companion object {
        fun ofWire(raw: String): ConsentRefusal? = values().firstOrNull { it.wire == raw }
    }
}

/** The whole answer of `recordScanConsent`. */
sealed interface ConsentAnswerRead {
    /** No answer, a JSON null, or not an object; or a piece of it is missing, of another shape, or not what a recorded consent is ([why]: a fixed token). NOT a recorded consent. */
    data class Unreadable(val why: String) : ConsentAnswerRead

    /**
     * The server says the consent is recorded: `state` is `accepted`, the answer is for THIS session, and the stored version and language are readable. [storedTextVersion] and [storedLocale] are the STORED
     * values (a second call answers the first's); [alreadyRecorded] is the server's flag, `null` when the answer did not carry it (kept: not read as false); [unknownKeys] are the keys that are none of the
     * five (sorted).
     */
    data class Accepted(val storedTextVersion: Int, val storedLocale: ConsentLocale, val alreadyRecorded: Boolean?, val unknownKeys: List<String>) : ConsentAnswerRead

    companion object {
        /** Reads [element] as the answer to a consent recorded for [sessionId]. */
        fun of(element: JsonElement?, sessionId: String): ConsentAnswerRead {
            if (element == null || element.isJsonNull) return Unreadable("absent")
            if (!element.isJsonObject) return Unreadable("not_an_object_${kindOf(element)}")
            val obj = element.asJsonObject

            val echo = obj.get(ConsentWire.KEY_SESSION_ID)
            if (echo == null || echo.isJsonNull || !echo.isJsonPrimitive || !echo.asJsonPrimitive.isString) return Unreadable("session_id_missing")
            if (echo.asString != sessionId) return Unreadable("session_id_differs")

            val state = obj.get(ConsentWire.KEY_STATE)
            if (state == null || state.isJsonNull) return Unreadable("state_absent")
            if (!state.isJsonPrimitive || !state.asJsonPrimitive.isString) return Unreadable("state_not_a_string_${kindOf(state)}")
            if (state.asString != ConsentWire.STATE_ACCEPTED) return Unreadable("state_not_accepted")

            val version = obj.get(ConsentWire.KEY_TEXT_VERSION)
            if (version == null || version.isJsonNull) return Unreadable("text_version_absent")
            if (!version.isJsonPrimitive || !version.asJsonPrimitive.isNumber) return Unreadable("text_version_not_a_number_${kindOf(version)}")
            val v = version.asDouble
            if (v.isNaN() || v.isInfinite() || v != Math.floor(v) || v < 1.0 || v > Int.MAX_VALUE.toDouble()) return Unreadable("text_version_not_a_version")

            val locale = obj.get(ConsentWire.KEY_LOCALE)
            if (locale == null || locale.isJsonNull) return Unreadable("locale_absent")
            if (!locale.isJsonPrimitive || !locale.asJsonPrimitive.isString) return Unreadable("locale_not_a_string_${kindOf(locale)}")
            val storedLocale = ConsentLocale.ofWire(locale.asString) ?: return Unreadable("locale_unrecognized")

            val already = obj.get(ConsentWire.KEY_ALREADY_RECORDED)
            val alreadyRecorded: Boolean? = when {
                already == null || already.isJsonNull -> null
                already.isJsonPrimitive && already.asJsonPrimitive.isBoolean -> already.asBoolean
                else -> return Unreadable("already_recorded_not_a_boolean_${kindOf(already)}")
            }
            return Accepted(v.toInt(), storedLocale, alreadyRecorded, obj.keySet().filter { it !in ConsentWire.ANSWER_KEYS }.sorted())
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
}
