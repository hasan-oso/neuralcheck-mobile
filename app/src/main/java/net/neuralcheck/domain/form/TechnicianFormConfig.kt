package net.neuralcheck.domain.form

import com.google.gson.JsonElement

/**
 * What `getTechnicianFormConfig` answers - row A-P6-08. The request is `{sessionId}` ([TechnicianFormWire.KEY_SESSION_ID]); the answer is read by the names the server's handler spells
 * [SRV technicianForm.js:293-341 `getTechnicianFormConfigHandler`, as copied in `evidence/run-2026-10-18/technician-form-server-wire-neuralcheck-backend-05275ba.md` from
 * neuralcheck-backend@05275ba - rule 29: a copy, it drifts]. There are TWO answers:
 *  - the shop is not enabled (`evaluationEnabled`): `{sessionId, formContractVersion, enabled: false, formState: "not_enabled"}` and nothing else [SRV :312];
 *  - the shop is enabled: `{sessionId, formContractVersion, enabled: true, slots: [{slot: 1, formState}, {slot: 2, formState, drawn}], technicians: [{technicianId, displayName}],
 *    symptomOrder: [...], settingsVersion}` [SRV :325-336]. `formState` of a slot is `submitted` or `not_submitted` [SRV :37 and :330-331]; `drawn` is whether the second form was drawn
 *    (double evaluation); the technicians are the owner's active ones (`active` is the server's filter and is not on the wire); `symptomOrder` is the contract table order today (the server counts
 *    nothing yet: [SRV :39-40], pending the user's decision), `settingsVersion` the server's «absent» state until P6-14.
 *
 * The same reading discipline as `ProtocolPartsRead` (rules 22, 25): the raw JSON comes in, and each piece is one named state - a missing key, a value of another shape, a list with entries it
 * cannot use - never an empty list that says "none", never a default that fills a gap. Above all, "the shop is not enabled" is its own state: it is NOT "no technicians" (until 2026-10-18 the
 * reader knew only `symptomOrder` and `technicians`, and an answer that carried neither - this one - read as "nobody to choose from").
 *
 * [decideOffer] turns the read into the one thing the screen needs: offer the form, say there is none, say the slot is closed, say nobody can be chosen, or say the answer cannot be read. The one place
 * a default order appears, [effectiveSymptomOrder], is declared and recorded: the order SHOWN is stored with every form (`symptomOrderShown`), so a form shown in the contract's table order says so itself.
 */
object TechnicianFormConfigWire {
    const val KEY_SESSION_ID = TechnicianFormWire.KEY_SESSION_ID // [SRV :312 :326] known, not compared
    const val KEY_FORM_CONTRACT_VERSION = TechnicianFormWire.KEY_FORM_CONTRACT_VERSION // [SRV :312 :327]
    const val KEY_ENABLED = "enabled" // [SRV :312 :328]
    const val KEY_FORM_STATE = "formState" // [SRV :312 (the not-enabled answer), :330-331 (each slot)]
    const val KEY_SLOTS = "slots" // [SRV :329]
    const val KEY_SLOT = TechnicianFormWire.KEY_SLOT // [SRV :330-331]
    const val KEY_DRAWN = "drawn" // [SRV :331] on the second slot only
    const val KEY_SYMPTOM_ORDER = "symptomOrder" // [SRV :334]
    const val KEY_TECHNICIANS = "technicians" // [SRV :333]
    const val KEY_TECHNICIAN_ID = TechnicianFormWire.KEY_TECHNICIAN_ID // [SRV :321]
    const val KEY_DISPLAY_NAME = "displayName" // [SRV :321; A10]
    const val KEY_ACTIVE = "active" // [A10] not on the wire today (the server filters on it); an explicit `active: false` is still never offered
    const val KEY_SETTINGS_VERSION = "settingsVersion" // [SRV :335]

    /** The value of `formState` when the shop is not enabled [SRV :312 `FORM_STATE.NOT_ENABLED`; the order's «{formState: "not_enabled"}»]. */
    const val FORM_STATE_NOT_ENABLED = "not_enabled"

    /** The two values of a slot's `formState` [SRV :37 «submitted | not_submitted», :330-331]. */
    const val FORM_STATE_SUBMITTED = "submitted"
    const val FORM_STATE_NOT_SUBMITTED = "not_submitted"

    /** The two slots of a session [SRV technicianFormContract.js:41 `FORM_SLOT`]. */
    const val SLOT_FIRST = 1
    const val SLOT_SECOND = 2

    internal val KNOWN_KEYS: Set<String> = setOf(
        KEY_SESSION_ID, KEY_FORM_CONTRACT_VERSION, KEY_ENABLED, KEY_FORM_STATE, KEY_SLOTS, KEY_TECHNICIANS, KEY_SYMPTOM_ORDER, KEY_SETTINGS_VERSION
    )
}

/** One technician the owner can choose: an opaque id and the name the admin gave (shown, never stored in this app). */
data class FormTechnician(val id: String, val displayName: String)

/** How the symptom order of the answer reads. */
sealed interface SymptomOrderRead {
    /** No `symptomOrder` key (or a JSON null). */
    data object Absent : SymptomOrderRead

    /** The key is there but is not a list ([kind]: `string`, `number`, `boolean`, `object`). */
    data class NotAList(val kind: String) : SymptomOrderRead

    /** A list: [keys] are the entries that are one of the twelve, first occurrence only, in the server's order; [unusable] counts the entries that are none of the twelve (a text of another key, `none_noticed`, a number) and [duplicates] the repeats. */
    data class Listed(val keys: List<Symptom>, val unusable: Int, val duplicates: Int) : SymptomOrderRead
}

/** How the technicians of the answer read. */
sealed interface TechniciansRead {
    data object Absent : TechniciansRead
    data class NotAList(val kind: String) : TechniciansRead

    /** A list: [entries] are the usable ones (an object with a non-blank `technicianId` and `displayName`, not marked `active: false`), [unusable] the others, [inactive] the ones marked `active: false`. */
    data class Listed(val entries: List<FormTechnician>, val unusable: Int, val inactive: Int) : TechniciansRead
}

/** `enabled`: whether the shop has the form on. A missing or non-boolean one is NOT read as either answer. */
sealed interface EnabledRead {
    data object Absent : EnabledRead
    data class NotABoolean(val kind: String) : EnabledRead
    data class Value(val enabled: Boolean) : EnabledRead
}

/** The top-level `formState` (the not-enabled answer carries it; the enabled one does not, its slots do). */
sealed interface FormStateRead {
    data object Absent : FormStateRead
    data class NotAString(val kind: String) : FormStateRead
    data object NotEnabled : FormStateRead

    /** A text that is not `not_enabled` ([raw] is the server's text; bounded when printed). */
    data class Unrecognized(val raw: String) : FormStateRead
}

/** `formContractVersion` of the answer: the form this app builds is version [TechnicianFormWire.FORM_CONTRACT_VERSION]. */
sealed interface ContractVersionRead {
    data object Absent : ContractVersionRead

    /** Not an integer: [kind] is `string`, `boolean`, `object`, `array`, or `number` (a fraction). */
    data class NotAnInteger(val kind: String) : ContractVersionRead
    data class Value(val version: Int) : ContractVersionRead
}

/** What a slot's `formState` is. */
enum class SlotFormState(val wire: String) {
    SUBMITTED(TechnicianFormConfigWire.FORM_STATE_SUBMITTED),
    NOT_SUBMITTED(TechnicianFormConfigWire.FORM_STATE_NOT_SUBMITTED)
}

sealed interface SlotStateRead {
    data object Absent : SlotStateRead
    data class NotAString(val kind: String) : SlotStateRead
    data class Known(val state: SlotFormState) : SlotStateRead
    data class Unrecognized(val raw: String) : SlotStateRead
}

/** `drawn` of a slot: whether the double evaluation drew it (the server writes it on the second slot). A missing one is not read as `false`. */
sealed interface DrawnRead {
    data object Absent : DrawnRead
    data class NotABoolean(val kind: String) : DrawnRead
    data class Value(val drawn: Boolean) : DrawnRead
}

data class SlotRead(val slot: Int, val formState: SlotStateRead, val drawn: DrawnRead)

/** How the `slots` of the answer read. */
sealed interface SlotsRead {
    data object Absent : SlotsRead
    data class NotAList(val kind: String) : SlotsRead

    /** A list: [entries] are the usable ones (an object whose `slot` is the integer 1 or 2), first occurrence of a slot only; [unusable] the others and [duplicates] the repeats. */
    data class Listed(val entries: List<SlotRead>, val unusable: Int, val duplicates: Int) : SlotsRead
}

/** `settingsVersion`: the server's «absent» state until P6-14; this app acts on none of it, it only says what it received. [raw] is the text of the value (bounded when printed). */
sealed interface SettingsVersionRead {
    data object Absent : SettingsVersionRead
    data class Present(val kind: String, val raw: String) : SettingsVersionRead
}

/** The whole answer. */
sealed interface TechnicianFormConfigRead {
    /** No answer, or a JSON null. */
    data object Absent : TechnicianFormConfigRead

    data class NotAnObject(val kind: String) : TechnicianFormConfigRead

    /** An object: [unknownKeys] are the keys that are none of the eight the server names (sorted; declared, never ignored silently). */
    data class Read(
        val contractVersion: ContractVersionRead,
        val enabled: EnabledRead,
        val formState: FormStateRead,
        val slots: SlotsRead,
        val symptomOrder: SymptomOrderRead,
        val technicians: TechniciansRead,
        val settingsVersion: SettingsVersionRead,
        val unknownKeys: List<String>
    ) : TechnicianFormConfigRead

    companion object {
        fun of(element: JsonElement?): TechnicianFormConfigRead {
            if (element == null || element.isJsonNull) return Absent
            if (!element.isJsonObject) return NotAnObject(kindOf(element))
            val obj = element.asJsonObject
            return Read(
                contractVersion = contractVersionOf(obj.get(TechnicianFormConfigWire.KEY_FORM_CONTRACT_VERSION)),
                enabled = enabledOf(obj.get(TechnicianFormConfigWire.KEY_ENABLED)),
                formState = formStateOf(obj.get(TechnicianFormConfigWire.KEY_FORM_STATE)),
                slots = slotsOf(obj.get(TechnicianFormConfigWire.KEY_SLOTS)),
                symptomOrder = symptomOrderOf(obj.get(TechnicianFormConfigWire.KEY_SYMPTOM_ORDER)),
                technicians = techniciansOf(obj.get(TechnicianFormConfigWire.KEY_TECHNICIANS)),
                settingsVersion = settingsVersionOf(obj.get(TechnicianFormConfigWire.KEY_SETTINGS_VERSION)),
                unknownKeys = obj.keySet().filter { it !in TechnicianFormConfigWire.KNOWN_KEYS }.sorted()
            )
        }

        private fun contractVersionOf(element: JsonElement?): ContractVersionRead {
            if (element == null || element.isJsonNull) return ContractVersionRead.Absent
            val n = integerOf(element) ?: return ContractVersionRead.NotAnInteger(kindOf(element))
            return ContractVersionRead.Value(n)
        }

        private fun enabledOf(element: JsonElement?): EnabledRead {
            if (element == null || element.isJsonNull) return EnabledRead.Absent
            if (!element.isJsonPrimitive || !element.asJsonPrimitive.isBoolean) return EnabledRead.NotABoolean(kindOf(element))
            return EnabledRead.Value(element.asBoolean)
        }

        private fun formStateOf(element: JsonElement?): FormStateRead {
            if (element == null || element.isJsonNull) return FormStateRead.Absent
            if (!element.isJsonPrimitive || !element.asJsonPrimitive.isString) return FormStateRead.NotAString(kindOf(element))
            val raw = element.asString
            return if (raw == TechnicianFormConfigWire.FORM_STATE_NOT_ENABLED) FormStateRead.NotEnabled else FormStateRead.Unrecognized(raw)
        }

        private fun slotsOf(element: JsonElement?): SlotsRead {
            if (element == null || element.isJsonNull) return SlotsRead.Absent
            if (!element.isJsonArray) return SlotsRead.NotAList(kindOf(element))
            val entries = ArrayList<SlotRead>()
            var unusable = 0
            var duplicates = 0
            for (entry in element.asJsonArray) {
                if (!entry.isJsonObject) { unusable++; continue }
                val obj = entry.asJsonObject
                val slot = integerOf(obj.get(TechnicianFormConfigWire.KEY_SLOT))?.takeIf { it == TechnicianFormConfigWire.SLOT_FIRST || it == TechnicianFormConfigWire.SLOT_SECOND }
                if (slot == null) { unusable++; continue }
                if (entries.any { it.slot == slot }) { duplicates++; continue }
                entries.add(SlotRead(slot, slotStateOf(obj.get(TechnicianFormConfigWire.KEY_FORM_STATE)), drawnOf(obj.get(TechnicianFormConfigWire.KEY_DRAWN))))
            }
            return SlotsRead.Listed(entries, unusable, duplicates)
        }

        private fun slotStateOf(element: JsonElement?): SlotStateRead {
            if (element == null || element.isJsonNull) return SlotStateRead.Absent
            if (!element.isJsonPrimitive || !element.asJsonPrimitive.isString) return SlotStateRead.NotAString(kindOf(element))
            val raw = element.asString
            val known = SlotFormState.values().firstOrNull { it.wire == raw }
            return if (known != null) SlotStateRead.Known(known) else SlotStateRead.Unrecognized(raw)
        }

        private fun drawnOf(element: JsonElement?): DrawnRead {
            if (element == null || element.isJsonNull) return DrawnRead.Absent
            if (!element.isJsonPrimitive || !element.asJsonPrimitive.isBoolean) return DrawnRead.NotABoolean(kindOf(element))
            return DrawnRead.Value(element.asBoolean)
        }

        private fun settingsVersionOf(element: JsonElement?): SettingsVersionRead {
            if (element == null || element.isJsonNull) return SettingsVersionRead.Absent
            return SettingsVersionRead.Present(kindOf(element), if (element.isJsonPrimitive) element.asString else "")
        }

        private fun symptomOrderOf(element: JsonElement?): SymptomOrderRead {
            if (element == null || element.isJsonNull) return SymptomOrderRead.Absent
            if (!element.isJsonArray) return SymptomOrderRead.NotAList(kindOf(element))
            val twelve = Symptom.TWELVE.associateBy { it.wire }
            val seen = LinkedHashSet<Symptom>()
            var unusable = 0
            var duplicates = 0
            for (entry in element.asJsonArray) {
                val key = if (entry.isJsonPrimitive && entry.asJsonPrimitive.isString) twelve[entry.asString] else null
                when {
                    key == null -> unusable++
                    !seen.add(key) -> duplicates++
                }
            }
            return SymptomOrderRead.Listed(seen.toList(), unusable, duplicates)
        }

        private fun techniciansOf(element: JsonElement?): TechniciansRead {
            if (element == null || element.isJsonNull) return TechniciansRead.Absent
            if (!element.isJsonArray) return TechniciansRead.NotAList(kindOf(element))
            val entries = ArrayList<FormTechnician>()
            var unusable = 0
            var inactive = 0
            for (entry in element.asJsonArray) {
                if (!entry.isJsonObject) { unusable++; continue }
                val obj = entry.asJsonObject
                val id = obj.get(TechnicianFormConfigWire.KEY_TECHNICIAN_ID)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString?.takeIf { it.isNotBlank() }
                val name = obj.get(TechnicianFormConfigWire.KEY_DISPLAY_NAME)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString?.takeIf { it.isNotBlank() }
                val active = obj.get(TechnicianFormConfigWire.KEY_ACTIVE)
                if (id == null || name == null) { unusable++; continue }
                if (active != null && active.isJsonPrimitive && active.asJsonPrimitive.isBoolean && !active.asBoolean) { inactive++; continue }
                entries.add(FormTechnician(id, name))
            }
            return TechniciansRead.Listed(entries, unusable, inactive)
        }

        /** An integer value of a JSON number (`1` and `1.0` both), or null for anything else - a fraction, a text, a boolean. */
        private fun integerOf(element: JsonElement?): Int? {
            if (element == null || !element.isJsonPrimitive || !element.asJsonPrimitive.isNumber) return null
            val d = element.asDouble
            return if (d == Math.rint(d) && d >= Int.MIN_VALUE && d <= Int.MAX_VALUE) d.toInt() else null
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

/** Where the order of the chips came from: the server's list, or the contract's table order because the answer named none usable. Recorded in the log; the order itself is stored in the form. */
enum class SymptomOrderSource(val token: String) {
    SERVER("server"),
    CONTRACT_ORDER("contract_order")
}

/** The order the twelve chips are shown in, and where it came from. */
data class SymptomOrder(val order: List<Symptom>, val source: SymptomOrderSource)

/**
 * The twelve in the order to show. A usable server list wins, in its order; the keys it did not list are appended in the contract's table order (the form shows all twelve: «≤ 12»), and `none_noticed` is
 * never part of it (it is a chip outside the twelve, always drawn last). A missing or unusable list gives the contract's table order, declared as [SymptomOrderSource.CONTRACT_ORDER] - the form stores the
 * order it showed either way.
 */
fun effectiveSymptomOrder(read: TechnicianFormConfigRead): SymptomOrder {
    val listed = ((read as? TechnicianFormConfigRead.Read)?.symptomOrder as? SymptomOrderRead.Listed)?.keys.orEmpty()
    if (listed.isEmpty()) return SymptomOrder(Symptom.TWELVE, SymptomOrderSource.CONTRACT_ORDER)
    return SymptomOrder(listed + Symptom.TWELVE.filter { it !in listed }, SymptomOrderSource.SERVER)
}

/** The technicians to choose from, or null when the answer named none usable (null is "nothing to choose from", a state the screen must say, not an empty list that looks like a choice). */
fun choosableTechnicians(read: TechnicianFormConfigRead): List<FormTechnician>? =
    ((read as? TechnicianFormConfigRead.Read)?.technicians as? TechniciansRead.Listed)?.entries?.takeIf { it.isNotEmpty() }

/** Why a slot's form is not offered although the shop has the form on. */
enum class SlotClosedReason(val token: String) {
    /** The slot's form was already sent: the server refuses a second one (`FORM_ALREADY_SUBMITTED`). */
    SUBMITTED("slot_submitted"),

    /** The second slot, and the double evaluation did not draw it (`FORM_SLOT_INVALID`). */
    NOT_DRAWN("slot_not_drawn")
}

/** What the screen is to do with the answer, for one slot. [token] is the one word the log prints. */
sealed interface FormOffer {
    val token: String

    /** Offer the form: these technicians, this chip order. */
    data class Offer(val technicians: List<FormTechnician>, val symptomOrder: SymptomOrder) : FormOffer {
        override val token: String get() = "offered"
    }

    /** The shop has the form off (`enabled: false`, `formState: not_enabled`): there is no form. This is not "no technicians". */
    data object NotEnabled : FormOffer {
        override val token: String get() = "not_enabled"
    }

    data class SlotClosed(val reason: SlotClosedReason) : FormOffer {
        override val token: String get() = reason.token
    }

    /** The shop has the form on and a usable list that holds nobody to choose (every entry unusable or inactive, or none). */
    data object NoTechnicians : FormOffer {
        override val token: String get() = "no_technicians"
    }

    /** The answer cannot be read as one of the above; [why] is a fixed token naming the first thing that did not read (no server text in it). Never read as "not enabled" or "no technicians". */
    data class Unreadable(val why: String) : FormOffer {
        override val token: String get() = "unreadable_$why"
    }
}

/**
 * The decision for [slot], in this order (the first that applies decides): an answer that is not an object; a `formContractVersion` that is missing or is not this app's; `enabled` missing or not a boolean;
 * `enabled: false` (with `formState: not_enabled`; without it the answer contradicts itself); a top-level `formState` on an enabled answer (the server puts none there); `slots` missing or not a list; the
 * slot not in it or its `formState` not one of the two; the slot already submitted; for the second slot, `drawn` missing or not a boolean, and false; `technicians` missing or not a list; no usable
 * technician. Anything else is [FormOffer.Offer].
 */
fun decideOffer(read: TechnicianFormConfigRead, slot: Int): FormOffer {
    val r = when (read) {
        TechnicianFormConfigRead.Absent -> return FormOffer.Unreadable("absent")
        is TechnicianFormConfigRead.NotAnObject -> return FormOffer.Unreadable("not_an_object_${read.kind}")
        is TechnicianFormConfigRead.Read -> read
    }
    when (val v = r.contractVersion) {
        ContractVersionRead.Absent -> return FormOffer.Unreadable("contract_version_absent")
        is ContractVersionRead.NotAnInteger -> return FormOffer.Unreadable("version_not_an_int_${v.kind}")
        is ContractVersionRead.Value -> if (v.version != TechnicianFormWire.FORM_CONTRACT_VERSION) return FormOffer.Unreadable("contract_version_differs")
    }
    val enabled = when (val e = r.enabled) {
        EnabledRead.Absent -> return FormOffer.Unreadable("enabled_absent")
        is EnabledRead.NotABoolean -> return FormOffer.Unreadable("enabled_not_a_boolean_${e.kind}")
        is EnabledRead.Value -> e.enabled
    }
    if (!enabled) return if (r.formState == FormStateRead.NotEnabled) FormOffer.NotEnabled else FormOffer.Unreadable("not_enabled_no_form_state")
    if (r.formState != FormStateRead.Absent) return FormOffer.Unreadable("enabled_with_form_state")

    val entry = when (val s = r.slots) {
        SlotsRead.Absent -> return FormOffer.Unreadable("slots_absent")
        is SlotsRead.NotAList -> return FormOffer.Unreadable("slots_not_a_list_${s.kind}")
        is SlotsRead.Listed -> s.entries.firstOrNull { it.slot == slot } ?: return FormOffer.Unreadable("slot_${slot}_absent")
    }
    when (val st = entry.formState) {
        SlotStateRead.Absent -> return FormOffer.Unreadable("slot_state_absent")
        is SlotStateRead.NotAString -> return FormOffer.Unreadable("slot_state_not_string_${st.kind}")
        is SlotStateRead.Unrecognized -> return FormOffer.Unreadable("slot_state_unrecognized")
        is SlotStateRead.Known -> if (st.state == SlotFormState.SUBMITTED) return FormOffer.SlotClosed(SlotClosedReason.SUBMITTED)
    }
    if (slot == TechnicianFormConfigWire.SLOT_SECOND) {
        when (val d = entry.drawn) {
            DrawnRead.Absent -> return FormOffer.Unreadable("drawn_absent")
            is DrawnRead.NotABoolean -> return FormOffer.Unreadable("drawn_not_a_boolean_${d.kind}")
            is DrawnRead.Value -> if (!d.drawn) return FormOffer.SlotClosed(SlotClosedReason.NOT_DRAWN)
        }
    }
    when (val t = r.technicians) {
        TechniciansRead.Absent -> return FormOffer.Unreadable("technicians_absent")
        is TechniciansRead.NotAList -> return FormOffer.Unreadable("technicians_not_list_${t.kind}")
        is TechniciansRead.Listed -> Unit
    }
    val technicians = choosableTechnicians(read) ?: return FormOffer.NoTechnicians
    return FormOffer.Offer(technicians, effectiveSymptomOrder(read))
}

/** Which slot the screen opens, and its [offer]. */
data class FormOpening(val slot: Int, val offer: FormOffer)

/**
 * The double evaluation (A-P6-10; PLAN-06 section 10-b item 6, row ف٩): after the FIRST form is sent, whether the screen moves to the SECOND slot - the form of another technician for a phone the server's draw picked
 * (`slots[1].drawn`, contract A7 (2)). The answer is [decideOffer] for the second slot, read from the configuration already in hand (the draw is made once, when the session is created, and never changes):
 *  - an offer, or a named state that says why it cannot be offered (`NoTechnicians`, [FormOffer.Unreadable] - `drawn` missing or not a boolean is NOT read as «not drawn», rule 22): the screen moves to the second
 *    slot, whose opening is returned;
 *  - [FormOffer.SlotClosed] (the draw did not pick this phone, or the second form was already sent): null - there is no second form to do, and the first form's «sent» stands as it was.
 */
fun decideSecondSlot(read: TechnicianFormConfigRead): FormOpening? = when (val second = decideOffer(read, TechnicianFormConfigWire.SLOT_SECOND)) {
    is FormOffer.Offer, FormOffer.NoTechnicians, is FormOffer.Unreadable -> FormOpening(TechnicianFormConfigWire.SLOT_SECOND, second)
    is FormOffer.SlotClosed, FormOffer.NotEnabled -> null
}

/**
 * What a screen that starts from the FIRST slot opens: the first slot's offer while it is not sent; once it is sent, the second slot when [decideSecondSlot] says there is one, and otherwise the first slot's
 * «sent» as before. This is what lets a technician's screen that is built again (the owner left the session screen and came back, the phone was handed over and the app restarted) go straight to the form
 * that is waiting, instead of to a «sent» that hides it.
 */
fun decideOpening(read: TechnicianFormConfigRead): FormOpening {
    val first = decideOffer(read, TechnicianFormConfigWire.SLOT_FIRST)
    if (first != FormOffer.SlotClosed(SlotClosedReason.SUBMITTED)) return FormOpening(TechnicianFormConfigWire.SLOT_FIRST, first)
    return decideSecondSlot(read) ?: FormOpening(TechnicianFormConfigWire.SLOT_FIRST, first)
}
