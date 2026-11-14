package net.neuralcheck.domain.form

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject

/**
 * A LOCAL COPY of the rules the server judges a technician's form by - a Kotlin port of `normalizeTechnicianForm` and the helpers it calls, `readClosedMap`, `readDistinctKeys`,
 * `readBatteryHealth`, `readTimes` and `isSafeDocId`, and the session-id check of `readCallerAndSession`, from `technicianFormContract.js` as copied in
 * `evidence/run-2026-10-18/technician-form-server-wire-neuralcheck-backend-05275ba.md` (neuralcheck-backend@05275ba; rule 29: a copy, it drifts). Rule 41: a reader is tested with a sample made by the
 * real producer; this file lets the app's real producer ([TechnicianFormRequest.build]) be judged by the server's rules without reading the server repository.
 *
 * What it IS: the structure of the server's judgement, in the server's order - the version, the slot, the technician id, the closed maps (a key outside the list, or a value outside it, is refused),
 * the symptoms and their shown order, the booleans, the battery health's three shapes, the note rule (any grade below good makes the note mandatory), the times (`openedAt` <= `firstTapAt` <=
 * `submittedAt`, every `perAnswerAt` entry a number between them), the prompt record (all three or none), and the empty-form refusal. A refusal is named by the KEY of `FORM_REFUSAL` the copy uses
 * (`CONTRACT_VERSION_INVALID`, `TIMES_INVALID`, ...) and carries the copy's own `cause=` text; the refusal CODE STRINGS (`FORM_...`) are not in the copy and are not invented here.
 *
 * What it is NOT, and the report says so: the server's enumerated VALUES live in `protocolVocabulary.js`, which is not in the copy. So [Vocabulary] is filled from this app's own enums (checked
 * against contract A5 by `TechnicianFormModelTest`) - for the VALUES this copy therefore agrees with the app by construction and proves nothing; for the NAMES, SHAPES and RULES it is the server's.
 * The only vocabulary the copy does spell: the prompt actions (`PROMPT_ACTION`, :34), the slots (`FORM_SLOT`, :41), the four grade categories (the header comment, :9), the battery kinds
 * (`readBatteryHealth`'s comment, :122) and the contract version 2 (technicianForm.js:28).
 */
internal object ServerFormRulesCopy {

    /** The values the server accepts, filled from the app's enums - NOT from the copy (see the class doc). */
    object Vocabulary {
        val gradeCategories = listOf("battery", "performance", "thermal", "overall") // the copy: technicianFormContract.js:9
        val gradeOptions = GradeAnswer.values().map { it.wire }
        val functionalChecks = FunctionalRow.values().map { it.wire }
        val functionalAnswers = RowAnswer.values().map { it.wire }
        val defects = DefectRow.values().map { it.wire }
        val symptoms = Symptom.values().map { it.wire }
        val symptomChips = Symptom.TWELVE.map { it.wire }
        const val symptomNoneNoticed = "none_noticed"
        val batteryKinds = listOf("percent", "state", "not_shown") // the copy: technicianFormContract.js:122
        val batteryStates = BatteryStateKey.values().map { it.wire }
        val promptActions = listOf("confirmed_send", "will_review") // the copy: technicianFormContract.js:34
        val belowGood = listOf("fair", "weak")
        const val formContractVersion = 2 // the copy: technicianForm.js:28
        val slots = listOf(1, 2) // the copy: technicianFormContract.js:41
        const val maxPerAnswerEntries = 200 // the copy: technicianFormContract.js:55
    }

    sealed interface Verdict {
        /** [form] is what the server would store, by the names `normalizeTechnicianForm` returns (technicianFormContract.js:249-270). */
        data class Accepted(val form: Map<String, Any?>) : Verdict

        data class Refused(val refusal: String, val extra: String) : Verdict
    }

    private class Stop(val refusal: String, val extra: String) : RuntimeException(null, null, false, false)

    private fun refuse(code: String, extra: String = ""): Nothing = throw Stop(code, extra)

    private fun isAbsent(v: JsonElement?) = v == null || v.isJsonNull

    private fun num(v: JsonElement?): Double? =
        if (v != null && v.isJsonPrimitive && v.asJsonPrimitive.isNumber) v.asDouble.takeIf { it.isFinite() && it >= 0 } else null

    private fun string(v: JsonElement?): String? = if (v != null && v.isJsonPrimitive && v.asJsonPrimitive.isString) v.asString else null

    private fun isSafeDocId(v: JsonElement?): Boolean {
        val s = string(v) ?: return false
        val t = s.trim()
        return t.isNotEmpty() && t.length <= 128 && !t.contains("/") && t != "." && t != ".."
    }

    /** readClosedMap: missing or null = every key null; a key outside [keys], or a value outside [values] = refused (the copy's `cause=` text is returned in the refusal). */
    private fun closedMap(raw: JsonElement?, keys: List<String>, values: List<String>): Pair<Map<String, String?>, String?> {
        val out = linkedMapOf<String, String?>().also { m -> keys.forEach { m[it] = null } }
        if (isAbsent(raw)) return out to null
        if (!raw!!.isJsonObject) return out to " shape=NOT_A_MAP"
        for ((k, v) in raw.asJsonObject.entrySet()) {
            if (k !in keys) return out to " cause=UNKNOWN_KEY"
            if (isAbsent(v)) continue
            val s = string(v)
            if (s == null || s !in values) return out to " cause=VALUE_NOT_ALLOWED key=$k"
            out[k] = s
        }
        return out to null
    }

    private fun distinctKeys(raw: JsonElement?, allowed: List<String>): Pair<List<String>, String?> {
        if (raw == null || !raw.isJsonArray) return emptyList<String>() to " shape=NOT_AN_ARRAY"
        val seen = LinkedHashSet<String>()
        for (v in raw.asJsonArray) {
            val s = string(v)
            if (s == null || s !in allowed) return emptyList<String>() to " cause=VALUE_NOT_ALLOWED"
            if (!seen.add(s)) return emptyList<String>() to " cause=DUPLICATE"
        }
        return seen.toList() to null
    }

    /** The copy's `readBatteryHealth`: [Triple] of (value or null, state `answered` | `not_answered`, refusal extra or null). */
    private fun batteryHealth(raw: JsonElement?): Triple<Map<String, Any?>?, String, String?> {
        if (isAbsent(raw)) return Triple(null, "not_answered", null)
        if (!raw!!.isJsonObject) return Triple(null, "", " shape=NOT_A_MAP")
        val obj = raw.asJsonObject
        for (k in obj.keySet()) if (k !in listOf("kind", "percent", "stateKey")) return Triple(null, "", " cause=UNKNOWN_KEY")
        val kind = string(obj.get("kind"))
        if (kind == null || kind !in Vocabulary.batteryKinds) return Triple(null, "", " cause=KIND_NOT_ALLOWED")
        val percentEl = obj.get("percent").takeUnless { isAbsent(it) }
        val stateKey = string(obj.get("stateKey").takeUnless { isAbsent(it) })
        val stateKeyPresent = !isAbsent(obj.get("stateKey"))
        val percent = percentEl?.let { e -> if (e.isJsonPrimitive && e.asJsonPrimitive.isNumber) e.asDouble else Double.NaN }
        when (kind) {
            "percent" -> {
                if (percent == null || percent.isNaN() || percent != Math.rint(percent) || percent < 0 || percent > 100) return Triple(null, "", " cause=PERCENT_NOT_INTEGER_0_100")
                if (stateKeyPresent) return Triple(null, "", " cause=STATE_KEY_WITH_PERCENT")
            }
            "state" -> {
                if (stateKey == null || stateKey !in Vocabulary.batteryStates) return Triple(null, "", " cause=STATE_KEY_NOT_ALLOWED")
                if (percentEl != null) return Triple(null, "", " cause=PERCENT_WITH_STATE")
            }
            else -> if (percentEl != null || stateKeyPresent) return Triple(null, "", " cause=VALUE_WITH_NOT_SHOWN")
        }
        return Triple(linkedMapOf("kind" to kind, "percent" to percent?.toLong(), "stateKey" to stateKey), "answered", null)
    }

    /** The copy's `readTimes` (technicianFormContract.js:146-159). */
    private fun times(d: JsonObject): Map<String, Any?> {
        for (k in listOf("openedAt", "firstTapAt", "submittedAt")) {
            if (num(d.get(k)) == null) refuse("TIMES_INVALID", " cause=${if (isAbsent(d.get(k))) "ABSENT" else "NOT_A_NONNEGATIVE_NUMBER"} field=$k")
        }
        val opened = num(d.get("openedAt"))!!
        val firstTap = num(d.get("firstTapAt"))!!
        val submitted = num(d.get("submittedAt"))!!
        if (firstTap < opened) refuse("TIMES_INVALID", " cause=FIRST_TAP_BEFORE_OPENED")
        if (submitted < firstTap) refuse("TIMES_INVALID", " cause=SUBMITTED_BEFORE_FIRST_TAP")
        val per = d.get("perAnswerAt")
        if (per == null || !per.isJsonArray) refuse("TIMES_INVALID", " cause=${if (isAbsent(per)) "ABSENT" else "NOT_AN_ARRAY"} field=perAnswerAt")
        val arr: JsonArray = per!!.asJsonArray
        if (arr.size() > Vocabulary.maxPerAnswerEntries) refuse("TIMES_INVALID", " cause=TOO_MANY_ENTRIES field=perAnswerAt")
        val entries = ArrayList<Long>()
        for (t in arr) {
            val n = num(t)
            if (n == null || n < opened || n > submitted) refuse("TIMES_INVALID", " cause=ENTRY_OUT_OF_RANGE field=perAnswerAt")
            entries.add(n!!.toLong())
        }
        return linkedMapOf("openedAt" to opened.toLong(), "firstTapAt" to firstTap.toLong(), "perAnswerAt" to entries, "submittedAt" to submitted.toLong())
    }

    /** `normalizeTechnicianForm(data)` (technicianFormContract.js:172-271), in the same order of refusals. */
    fun normalize(d: JsonObject): Verdict = try {
        Verdict.Accepted(normalizeOrStop(d))
    } catch (s: Stop) {
        Verdict.Refused(s.refusal, s.extra)
    }

    private fun normalizeOrStop(d: JsonObject): Map<String, Any?> {
        // technicianForm.js:150-157 `readCallerAndSession`, which the handler runs before `normalizeTechnicianForm`: a session id that is not a safe document id is refused
        if (!isSafeDocId(d.get("sessionId"))) refuse("SESSION_ID_REQUIRED")
        val version = d.get("formContractVersion")
        if (num(version) != Vocabulary.formContractVersion.toDouble()) {
            refuse("CONTRACT_VERSION_INVALID", " cause=${if (isAbsent(version)) "ABSENT" else "NOT_THE_CURRENT_VERSION"}")
        }
        val slotEl = d.get("slot")
        val slot = num(slotEl)?.takeIf { it == Math.rint(it) }?.toInt()
        if (slot == null || slot !in Vocabulary.slots) refuse("SLOT_INVALID", " cause=${if (isAbsent(slotEl)) "ABSENT" else "NOT_1_OR_2"}")
        val techEl = d.get("technicianId")
        if (!isSafeDocId(techEl)) refuse("TECHNICIAN_UNKNOWN", " cause=${if (isAbsent(techEl)) "ABSENT" else "NOT_A_SAFE_ID"}")

        val (grades, gradesErr) = closedMap(d.get("grades"), Vocabulary.gradeCategories, Vocabulary.gradeOptions)
        if (gradesErr != null) refuse("GRADES_INVALID", gradesErr)
        val (functional, functionalErr) = closedMap(d.get("functional"), Vocabulary.functionalChecks, Vocabulary.functionalAnswers)
        if (functionalErr != null) refuse("FUNCTIONAL_INVALID", functionalErr)
        val (defects, defectsErr) = closedMap(d.get("defects"), Vocabulary.defects, Vocabulary.functionalAnswers)
        if (defectsErr != null) refuse("DEFECTS_INVALID", defectsErr)

        var symptoms: List<String>? = null
        if (!isAbsent(d.get("symptoms"))) {
            val (s, err) = distinctKeys(d.get("symptoms"), Vocabulary.symptoms)
            if (err != null) refuse("SYMPTOMS_INVALID", err)
            if (Vocabulary.symptomNoneNoticed in s && s.size > 1) refuse("SYMPTOMS_INVALID", " cause=NONE_NOTICED_WITH_OTHERS")
            symptoms = s.takeIf { it.isNotEmpty() }
        }
        val orderEl = d.get("symptomOrderShown")
        val (order, orderErr) = distinctKeys(orderEl, Vocabulary.symptomChips)
        if (orderErr != null) refuse("SYMPTOM_ORDER_INVALID", if (isAbsent(orderEl)) " cause=ABSENT" else orderErr)
        if (order.isEmpty()) refuse("SYMPTOM_ORDER_INVALID", " cause=EMPTY")
        if (symptoms != null && symptoms.any { it != Vocabulary.symptomNoneNoticed && it !in order }) refuse("SYMPTOMS_INVALID", " cause=SELECTED_NOT_SHOWN")

        var knownFault: Boolean? = null
        if (!isAbsent(d.get("known_fault_before_scan"))) {
            val el = d.get("known_fault_before_scan")
            if (!el.isJsonPrimitive || !el.asJsonPrimitive.isBoolean) refuse("KNOWN_FAULT_INVALID", " cause=NOT_A_BOOLEAN")
            knownFault = el.asBoolean
        }
        val (battery, batteryState, batteryErr) = batteryHealth(d.get("batteryHealth"))
        if (batteryErr != null) refuse("BATTERY_HEALTH_INVALID", batteryErr)
        var gameTried: Boolean? = null
        if (!isAbsent(d.get("gameTried"))) {
            val el = d.get("gameTried")
            if (!el.isJsonPrimitive || !el.asJsonPrimitive.isBoolean) refuse("GAME_TRIED_INVALID", " cause=NOT_A_BOOLEAN")
            gameTried = el.asBoolean
        }

        var observedNote: String? = null
        if (!isAbsent(d.get("observedNote"))) {
            val s = string(d.get("observedNote")) ?: refuse("NOTE_INVALID", " cause=NOT_A_STRING")
            observedNote = s.trim().takeIf { it.isNotEmpty() }
        }
        if (grades.values.any { it in Vocabulary.belowGood } && observedNote == null) refuse("NOTE_INVALID", " cause=REQUIRED_BELOW_GOOD")

        val t = times(d)

        var before: Map<String, String?>? = null
        var after: Map<String, String?>? = null
        var promptAction: String? = null
        if (!isAbsent(d.get("answersBeforePrompt")) || !isAbsent(d.get("answersAfterPrompt")) || !isAbsent(d.get("promptAction"))) {
            if (isAbsent(d.get("answersBeforePrompt")) || isAbsent(d.get("answersAfterPrompt")) || isAbsent(d.get("promptAction"))) refuse("GRADES_INVALID", " cause=PROMPT_RECORD_INCOMPLETE")
            val action = string(d.get("promptAction"))
            if (action == null || action !in Vocabulary.promptActions) refuse("GRADES_INVALID", " cause=PROMPT_ACTION_NOT_ALLOWED")
            val (b, bErr) = closedMap(d.get("answersBeforePrompt"), Vocabulary.gradeCategories, Vocabulary.gradeOptions)
            val (a, aErr) = closedMap(d.get("answersAfterPrompt"), Vocabulary.gradeCategories, Vocabulary.gradeOptions)
            if (bErr != null || aErr != null) refuse("GRADES_INVALID", " cause=PROMPT_SNAPSHOT_${if (bErr == null) "AFTER" else "BEFORE"}_INVALID")
            before = b
            after = a
            promptAction = action
        }

        val empty = grades.values.all { it == null } && functional.values.all { it == null } && defects.values.all { it == null } && symptoms == null &&
            batteryState == "not_answered" && gameTried == null && knownFault == null && observedNote == null
        if (empty) refuse("EMPTY")

        return linkedMapOf(
            "slot" to slot, "technicianId" to string(techEl)!!.trim(), "grades" to grades, "functional" to functional, "defects" to defects, "symptoms" to symptoms,
            "symptomOrderShown" to order, "knownFaultBeforeScan" to knownFault, "batteryHealth" to battery, "batteryHealthState" to batteryState, "gameTried" to gameTried,
            "observedNote" to observedNote
        ) + t + mapOf(
            "answersBeforePrompt" to before, "answersAfterPrompt" to after, "promptAction" to promptAction,
            // technicianFormContract.js:268: `d.sentOffline === true`, absent = false
            "sentOffline" to (d.get("sentOffline")?.let { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean && it.asBoolean } ?: false)
        )
    }
}
