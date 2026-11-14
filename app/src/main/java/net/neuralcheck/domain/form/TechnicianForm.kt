package net.neuralcheck.domain.form

import net.neuralcheck.domain.evaluation.EvaluationAnswers

/**
 * The technician's form - row A-P6-08 (استمارة جهاز الفنّي), `plan/PLAN-06-DATA-COLLECTION.md` section 10 (lines 1179-1376), the protocol contract A5 (copy at
 * `evidence/run-2026-10-18/protocol-contract-A1-A13-neuralcheck-backend-8e5c426.md`, lines 74-96, from neuralcheck-backend@8e5c426 `docs/PROTOCOL_CONTRACT.md`; a copy, it drifts), and the
 * approved wording of the levels (`plan/TECH-LEVELS-WORDING-FINAL-2026-10-10.md`).
 *
 * This file is the form's LOGIC and its WIRE, with no text and no Android in it: what a technician can answer ([GradeCategory] x [GradeAnswer], the functional rows, the symptoms, the battery
 * health, the note), what the state of one form is ([TechnicianFormState]), what each tap does to it (the functions below, each a value in and a value out), when a submit is blocked, shown the
 * contradiction prompt or sent ([decideSubmit]), and the exact request that leaves the phone ([TechnicianFormRequest.build]). The screen that draws it needed texts that were not approved
 * (contract A5 / PC:321: "B2 not approved"); they are approved and written since 2026-10-23 and the screen sits behind a guard (`TECH_FORM_TEXTS_APPROVED`, `TechnicianFormSurface.kt`), open since the change that flipped it.
 *
 * Rules 20 and 22: the app sends what the technician did, raw - an answer not given is an EXPLICIT null in the request (never `not_tested`, never a default: «غير المُجاب `null` صريحاً»,
 * contract A5), `not_tested` is an answer («لم أفحصها»: a fifth option, not a level), the times are the device clock's, and every judgement the SERVER makes (the quality flags `fast` and
 * `allFourGradesEqual`, blindness, the form's state) is the server's - this file computes none of them. Only the one thing the technician is shown, the contradiction prompt, is decided here,
 * because it is a screen, and what it saw and answered is reported ([PromptState]).
 *
 * The wire literals are in [TechnicianFormWire] (rule 24): one source. Names that the contract copy gives are marked [A5]; names it does NOT give were this app's choice ([APP]) until 2026-10-18, when
 * they were matched to the server's reader, which was built and tested (e2e 44/44) after the contract copy left them open: those are marked [SRV] with the server file and line they were read from -
 * `technicianFormContract.js` and `technicianForm.js` as copied (rule 29, a copy that drifts) in `evidence/run-2026-10-18/technician-form-server-wire-neuralcheck-backend-05275ba.md`
 * (neuralcheck-backend@05275ba). The server marks its own field names as suggested (technicianFormContract.js:18, «أسماء الحقول أدناه اقتراحي»); the order of 2026-10-18 (A-P6-08 wire match) is that
 * the app follows the side that has a tested reader.
 */
object TechnicianFormWire {
    /** The new callable of the form (contract A5: «`submitTechnicianForm` (الاستمارة)», E6, approved 2026-10-15). */
    const val SUBMIT_CALLABLE = "submitTechnicianForm"

    /** The form's configuration callable (contract A5: «`getTechnicianFormConfig` (إعدادها)», E6). Its request is `{sessionId}` [SRV technicianForm.js:150-157 `readCallerAndSession`]. */
    const val CONFIG_CALLABLE = "getTechnicianFormConfig"

    /** `formContractVersion: 2` [A5: «`FORM_CONTRACT_VERSION`»; SRV technicianForm.js:28 «formContractVersion: 2»]. */
    const val FORM_CONTRACT_VERSION = 2

    const val KEY_SESSION_ID = "sessionId" // [SRV technicianForm.js:154] the form's id is `{sessionId}_{slot}` [A5]
    const val KEY_SLOT = "slot" // [A5 A7]
    const val KEY_FORM_CONTRACT_VERSION = "formContractVersion" // [A5]
    const val KEY_TECHNICIAN_ID = "technicianId" // [A5 A7]
    const val KEY_GRADES = "grades" // [SRV technicianFormContract.js:175]
    const val KEY_FUNCTIONAL = "functional" // [SRV technicianFormContract.js:177]
    const val KEY_DEFECTS = "defects" // [SRV technicianFormContract.js:179] (until 2026-10-18 `visibleDefects`, the name the older evaluation card uses; the server reads `defects`)
    const val KEY_SYMPTOMS = "symptoms" // [SRV technicianFormContract.js:184]
    const val KEY_SYMPTOM_ORDER_SHOWN = "symptomOrderShown" // [A5]
    const val KEY_KNOWN_FAULT = "known_fault_before_scan" // [A5]
    const val KEY_BATTERY_HEALTH = "batteryHealth" // [SRV technicianFormContract.js:203]
    const val KEY_BATTERY_KIND = "kind" // [A5]
    const val KEY_BATTERY_PERCENT = "percent" // [A5]
    const val KEY_BATTERY_STATE_KEY = "stateKey" // [A5]
    const val KEY_GAME_TRIED = "gameTried" // [A5]
    const val KEY_OBSERVED_NOTE = "observedNote" // [A5]
    const val KEY_OPENED_AT = "openedAt" // [A5]
    const val KEY_FIRST_TAP_AT = "firstTapAt" // [A5]

    /** An array of numbers, the device clock's epoch milliseconds, each between `openedAt` and `submittedAt` [SRV technicianFormContract.js:146-159 `readTimes`, the array check at :155-157]. */
    const val KEY_PER_ANSWER_AT = "perAnswerAt" // [A5]
    const val KEY_SUBMITTED_AT = "submittedAt" // [A5]
    const val KEY_ANSWERS_BEFORE_PROMPT = "answersBeforePrompt" // [A5]
    const val KEY_ANSWERS_AFTER_PROMPT = "answersAfterPrompt" // [A5]
    const val KEY_PROMPT_ACTION = "promptAction" // [A5 name; the values are [SRV technicianFormContract.js:34 `PROMPT_ACTION`]]

    /** The refusal the contract names for a second form by the first technician (A7: «`FORM_SLOT_SAME_TECHNICIAN`»). The other fourteen `FORM_*` codes are not named in the copy. */
    const val REFUSAL_SLOT_SAME_TECHNICIAN = "FORM_SLOT_SAME_TECHNICIAN"

    /** The prefix every refusal of the form carries [A5: «15 رمزاً `FORM_*` في `FORM_REFUSAL`»]. */
    const val REFUSAL_PREFIX = "FORM_"
}

/** The four grades [A5: `battery` · `performance` · `thermal` · `overall`; no memory, no storage]. */
enum class GradeCategory(val wire: String) {
    BATTERY("battery"),
    PERFORMANCE("performance"),
    THERMAL("thermal"),
    OVERALL("overall")
}

/**
 * What a technician answers for a grade [A5: the keys `excellent/good/fair/weak`, and `not_tested` "a fifth option, not a level"]. [isLevel] is true for the four levels.
 * [belowGood] is the rule that opens the mandatory note («أيّ درجة دون «جيّد» تفتح سطر «ما الذي لاحظته؟» إلزامياً», PLAN-06:1193): the two levels under good. `not_tested` is not a level, so it
 * is not under good - the decision is the app's reading of that sentence, reported.
 */
enum class GradeAnswer(val wire: String, val isLevel: Boolean) {
    EXCELLENT("excellent", true),
    GOOD("good", true),
    FAIR("fair", true),
    WEAK("weak", true),
    NOT_TESTED("not_tested", false);

    val belowGood: Boolean get() = this == FAIR || this == WEAK
}

/** The six functional rows [A5: `screen_touch` · `camera` · `audio` · `buttons` · `charging` · `network`]. */
enum class FunctionalRow(val wire: String) {
    SCREEN_TOUCH("screen_touch"),
    CAMERA("camera"),
    AUDIO("audio"),
    BUTTONS("buttons"),
    CHARGING("charging"),
    NETWORK("network")
}

/**
 * The three visible-defect rows [A5, E10: `replaced_screen` · `water_damage` · `prior_repair`], answered with the same three states as the functional rows (PLAN-06:1254). The keys are the EXISTING list's
 * (contract A5: «= القائمة القائمة [كود] `evaluationContract.js:49-52`»), so they are read from the one place that already spells them, [EvaluationAnswers] (rule 24) - the old evaluation card's constants, which
 * move here when that card goes (P6-09). `weak_battery`, the list's fifth, is not a row of the form (the battery health replaces it).
 */
enum class DefectRow(val wire: String) {
    REPLACED_SCREEN(EvaluationAnswers.DEFECT_REPLACED_SCREEN),
    WATER_DAMAGE(EvaluationAnswers.DEFECT_WATER_DAMAGE),
    PRIOR_REPAIR(EvaluationAnswers.DEFECT_PRIOR_REPAIR)
}

/** The answer of a functional or defect row [A5: `works` | `faulty` | `not_tested`]; a row not answered is null in the state and an explicit null on the wire. */
enum class RowAnswer(val wire: String) {
    WORKS("works"),
    FAULTY("faulty"),
    NOT_TESTED("not_tested")
}

/** The twelve symptoms and `none_noticed` outside them [A5, PLAN-06:1264-1278]. The declaration order is the contract's table order = the order shown when the server names none. */
enum class Symptom(val wire: String) {
    BATTERY_DIES_BEFORE_DAY_END("battery_dies_before_day_end"),
    BATTERY_JUMPS_OR_SHUTS_DOWN("battery_jumps_or_shuts_down"),
    BATTERY_SWOLLEN("battery_swollen"),
    HOT_WHILE_CHARGING("hot_while_charging"),
    CHARGING_SLOW("charging_slow"),
    SLOW_OR_FREEZES_DAILY("slow_or_freezes_daily"),
    RESTARTS_UNDER_LOAD("restarts_under_load"),
    HEATS_FAST_IN_USE("heats_fast_in_use"),
    SYSTEM_HEAT_WARNING("system_heat_warning"),
    GAME_STUTTER_AFTER_MINUTES("game_stutter_after_minutes"),
    GAME_SCREEN_LINES_OR_FLICKER("game_screen_lines_or_flicker"),
    GAMES_FREEZE_OR_CLOSE("games_freeze_or_close"),

    /** «لم ألاحظ شيئاً»: an explicit chip outside the twelve - the empty selection is "not answered", never "no symptoms" (PLAN-06:1284). */
    NONE_NOTICED("none_noticed");

    val isTwelve: Boolean get() = this != NONE_NOTICED

    companion object {
        /** The twelve, in the contract's table order. */
        val TWELVE: List<Symptom> = values().filter { it.isTwelve }
    }
}

/** What the battery health the technician read on the phone is [A5: `kind` ∈ `percent` | `state` | `not_shown`]. */
enum class BatteryHealthKind(val wire: String) {
    PERCENT("percent"),
    STATE("state"),
    NOT_SHOWN("not_shown")
}

/** `stateKey` of a battery health read as a state [A5: `normal` · `weak` · `bad`]. */
enum class BatteryStateKey(val wire: String) {
    NORMAL("normal"),
    WEAK("weak"),
    BAD("bad")
}

/** The battery health answer [A5, PLAN-06:1288-1298]: optional - an unanswered one is null in the state; «لا تظهر» is an answer ([NotShown]), the empty field is not. */
sealed interface BatteryHealth {
    /** A percentage read in the phone's settings: an integer 0..100 the technician typed. */
    data class Percent(val value: Int) : BatteryHealth {
        init {
            require(value in 0..100) { "percent is 0..100" }
        }
    }

    data class State(val key: BatteryStateKey) : BatteryHealth

    /** «لا تظهر»: the phone shows neither. */
    data object NotShown : BatteryHealth
}

/**
 * What the technician did with the contradiction prompt [PLAN-06:1340: «نعم، أرسل» · «سأراجع»]. The design names the buttons, not the values; the two wire values are the server's
 * [SRV technicianFormContract.js:34 `PROMPT_ACTION`, refused otherwise at :230 `PROMPT_ACTION_NOT_ALLOWED`] - the server says they are suggested, and it has the reader (until 2026-10-18 this app sent
 * `confirmed` / `revise`, which the server refuses).
 */
enum class PromptAction(val wire: String) {
    /** «نعم، أرسل»: the technician confirmed the answers and sent. */
    CONFIRMED("confirmed_send"),

    /** «سأراجع»: the technician went back to review. */
    REVISE("will_review")
}

/** The contradiction prompt's state: shown at most once per form ("مرّة واحدة", PLAN-06:1353). [answersBefore] is the four grades as they were when it appeared (null = not answered). */
sealed interface PromptState {
    data object NotShown : PromptState
    data class Shown(val answersBefore: Map<GradeCategory, GradeAnswer?>, val action: PromptAction?) : PromptState
}

/** One judgement control changing: [item] is the item's wire key (the grade's, the row's, `symptoms`, `known_fault_before_scan`, `batteryHealth`, `gameTried`, `observedNote`), [atMs] the device clock. */
data class AnswerEvent(val item: String, val atMs: Long)

/** The one form of one session slot, as the technician has filled it so far. Immutable: every tap is a new value. */
data class TechnicianFormState(
    val sessionId: String,
    val slot: Int = 1,
    /** The technician chosen in «من يقيّم؟» (an opaque id the server gave; no name is ever in this app). null = nobody chosen. */
    val technicianId: String? = null,
    val grades: Map<GradeCategory, GradeAnswer> = emptyMap(),
    val functional: Map<FunctionalRow, RowAnswer> = emptyMap(),
    val defects: Map<DefectRow, RowAnswer> = emptyMap(),
    val symptoms: Set<Symptom> = emptySet(),
    /** The order the symptom chips were SHOWN in (stored with every form: the order changes with time and shapes the choice - PLAN-06:130). */
    val symptomOrderShown: List<Symptom> = Symptom.TWELVE,
    val knownFaultBeforeScan: Boolean? = null,
    val batteryHealth: BatteryHealth? = null,
    val gameTried: Boolean? = null,
    val observedNote: String = "",
    /** When the form was first shown (device clock, epoch ms). */
    val openedAtMs: Long,
    /** The first judgement tap; null until one happened. */
    val firstTapAtMs: Long? = null,
    val perAnswerAt: List<AnswerEvent> = emptyList(),
    val prompt: PromptState = PromptState.NotShown
) {
    /** Whether any grade is below good, which makes the note mandatory. */
    val noteRequired: Boolean get() = grades.values.any { it.belowGood }

    /** Whether the technician judged anything at all. */
    val hasAnyAnswer: Boolean get() = perAnswerAt.isNotEmpty()
}

object TechnicianFormLimits {
    /**
     * The longest note the input accepts, in characters. The contract copy leaves the cap to this order («سقف طوله يُحسم في أمر P6-08»; the precedent is the technician's name, 120). This app's choice: 200.
     * It is a limit on the INPUT ([withNote] refuses a longer text, nothing is cut) and the request builder REFUSES a longer one rather than truncating it (a cut note is an irreversible loss, rule 20).
     * The server validates it too (a named refusal); the value is reported to it.
     */
    const val NOTE_MAX_CHARS = 200
}

// ---- the taps ----

private fun TechnicianFormState.recorded(item: String, nowMs: Long): TechnicianFormState =
    copy(firstTapAtMs = firstTapAtMs ?: nowMs, perAnswerAt = perAnswerAt + AnswerEvent(item, nowMs))

/** A new form, opened at [nowMs]. [symptomOrderShown] is the order the chips are about to be shown in. */
fun openForm(sessionId: String, slot: Int, symptomOrderShown: List<Symptom>, nowMs: Long): TechnicianFormState =
    TechnicianFormState(sessionId = sessionId, slot = slot, symptomOrderShown = symptomOrderShown, openedAtMs = nowMs)

/** «من يقيّم؟»: choosing who judges is not a judgement - it is no answer event and does not start the clock of `firstTapAt`. */
fun TechnicianFormState.withTechnician(technicianId: String?): TechnicianFormState = copy(technicianId = technicianId?.takeIf { it.isNotBlank() })

fun TechnicianFormState.withGrade(category: GradeCategory, answer: GradeAnswer, nowMs: Long): TechnicianFormState =
    if (grades[category] == answer) this else copy(grades = grades + (category to answer)).recorded(category.wire, nowMs)

fun TechnicianFormState.withFunctional(row: FunctionalRow, answer: RowAnswer, nowMs: Long): TechnicianFormState =
    if (functional[row] == answer) this else copy(functional = functional + (row to answer)).recorded(row.wire, nowMs)

fun TechnicianFormState.withDefect(row: DefectRow, answer: RowAnswer, nowMs: Long): TechnicianFormState =
    if (defects[row] == answer) this else copy(defects = defects + (row to answer)).recorded(row.wire, nowMs)

/**
 * A symptom chip toggled. The twelve are independent; [Symptom.NONE_NOTICED] excludes them and they exclude it (choosing «لم ألاحظ شيئاً» clears the others, choosing a symptom clears it) - the design
 * makes it a chip outside the twelve, and the two together would say both "nothing" and "something" (the exclusion is this app's reading).
 */
fun TechnicianFormState.toggleSymptom(symptom: Symptom, nowMs: Long): TechnicianFormState {
    val next = when {
        symptom in symptoms -> symptoms - symptom
        symptom == Symptom.NONE_NOTICED -> setOf(Symptom.NONE_NOTICED)
        else -> (symptoms - Symptom.NONE_NOTICED) + symptom
    }
    return copy(symptoms = next).recorded(KEY_SYMPTOMS_ITEM, nowMs)
}

private const val KEY_SYMPTOMS_ITEM = TechnicianFormWire.KEY_SYMPTOMS

/** The «عطب معروف قبل الفحص» chip: tapped = true, tapped again = back to not answered (null). There is no "no" control, so `false` is never produced. */
fun TechnicianFormState.toggleKnownFault(nowMs: Long): TechnicianFormState =
    copy(knownFaultBeforeScan = if (knownFaultBeforeScan == true) null else true).recorded(TechnicianFormWire.KEY_KNOWN_FAULT, nowMs)

fun TechnicianFormState.withBatteryHealth(answer: BatteryHealth?, nowMs: Long): TechnicianFormState =
    if (batteryHealth == answer) this else copy(batteryHealth = answer).recorded(TechnicianFormWire.KEY_BATTERY_HEALTH, nowMs)

fun TechnicianFormState.withGameTried(answer: Boolean, nowMs: Long): TechnicianFormState =
    if (gameTried == answer) this else copy(gameTried = answer).recorded(TechnicianFormWire.KEY_GAME_TRIED, nowMs)

/**
 * The note's text. Refused (the state is returned unchanged) when it is longer than [TechnicianFormLimits.NOTE_MAX_CHARS] - the input never lets a longer text in, and nothing is cut. The first
 * edit of the form's note is an answer event (one per form, not one per key press); later edits are not.
 */
fun TechnicianFormState.withNote(text: String, nowMs: Long): TechnicianFormState {
    if (text.length > TechnicianFormLimits.NOTE_MAX_CHARS) return this
    if (text == observedNote) return this
    val first = perAnswerAt.none { it.item == TechnicianFormWire.KEY_OBSERVED_NOTE }
    val next = copy(observedNote = text)
    return if (first) next.recorded(TechnicianFormWire.KEY_OBSERVED_NOTE, nowMs) else next
}

// ---- the contradiction prompt and the submit ----

/**
 * The contradiction rule [A5, TL:64]: the overall grade is `excellent` or `good` while a category is `weak` («العامّ «ممتاز» أو «جيد» مع فئة واحدة «ضعيف»»; the key the server counts it by is
 * `overall_high_with_weak_category`). "A category" is any of the three that are not the overall one; two weak ones is at least as contradictory as one, so any is read as enough - the app's reading.
 * This is the one rule decided here, because it opens a screen; the server counts its own contradiction rules from the stored answers.
 */
fun contradictionApplies(grades: Map<GradeCategory, GradeAnswer>): Boolean {
    val overall = grades[GradeCategory.OVERALL] ?: return false
    if (overall != GradeAnswer.EXCELLENT && overall != GradeAnswer.GOOD) return false
    return listOf(GradeCategory.BATTERY, GradeCategory.PERFORMANCE, GradeCategory.THERMAL).any { grades[it] == GradeAnswer.WEAK }
}

/** Why a submit does not go out. */
enum class SubmitBlock {
    /** Nobody is chosen in «من يقيّم؟»: the form has no identity, and the double-evaluation rule (`FORM_SLOT_SAME_TECHNICIAN`) needs one. */
    NO_TECHNICIAN,

    /** A grade is below good and the note is empty (blank counts as empty). */
    NOTE_REQUIRED,

    /** Nothing was judged: an empty form says nothing. */
    NO_ANSWERS
}

sealed interface SubmitDecision {
    /** The submit is refused on the device for [reason]; the form stays as it is. */
    data class Blocked(val reason: SubmitBlock) : SubmitDecision

    /** The contradiction prompt must be shown first ([PromptState.Shown] is in [state]); the form is not sent. */
    data class ShowPrompt(val state: TechnicianFormState) : SubmitDecision

    /** The form is sent as [state] is. */
    data class Send(val state: TechnicianFormState) : SubmitDecision
}

/**
 * What pressing «أرسل التقييم» does. The checks run in this order, and the first that applies decides: no technician, a mandatory note missing, nothing answered (each [SubmitDecision.Blocked]); then the
 * contradiction prompt, once per form, if the rule applies and it was not shown ([SubmitDecision.ShowPrompt]); else [SubmitDecision.Send]. The quality flags never block (PLAN-06:1195: "تُحفظ ولا تمنع").
 */
fun TechnicianFormState.decideSubmit(): SubmitDecision {
    if (technicianId == null) return SubmitDecision.Blocked(SubmitBlock.NO_TECHNICIAN)
    if (noteRequired && observedNote.isBlank()) return SubmitDecision.Blocked(SubmitBlock.NOTE_REQUIRED)
    if (!hasAnyAnswer) return SubmitDecision.Blocked(SubmitBlock.NO_ANSWERS)
    if (prompt == PromptState.NotShown && contradictionApplies(grades)) {
        return SubmitDecision.ShowPrompt(copy(prompt = PromptState.Shown(answersBefore = gradesWithNulls(), action = null)))
    }
    return SubmitDecision.Send(this)
}

/** «نعم، أرسل»: records that the technician confirmed (only meaningful when the prompt was shown). */
fun TechnicianFormState.confirmPrompt(): TechnicianFormState =
    (prompt as? PromptState.Shown)?.let { copy(prompt = it.copy(action = PromptAction.CONFIRMED)) } ?: this

/** «سأراجع»: records that the technician went back to review. */
fun TechnicianFormState.revisePrompt(): TechnicianFormState =
    (prompt as? PromptState.Shown)?.let { copy(prompt = it.copy(action = PromptAction.REVISE)) } ?: this

/** The four grades in contract order, each answered or null. */
fun TechnicianFormState.gradesWithNulls(): Map<GradeCategory, GradeAnswer?> = GradeCategory.values().associateWith { grades[it] }

// ---- the request ----

/** The request of `submitTechnicianForm`, built from a state; or the named reason it cannot be. */
sealed interface FormRequestBuild {
    /** [request]: a map in the wire's key order, null values explicit. */
    data class Built(val request: Map<String, Any?>) : FormRequestBuild

    /** The state cannot be sent as it is: [reason] names why - the request is NOT built and nothing is cut or defaulted. */
    data class Refused(val reason: Reason) : FormRequestBuild {
        enum class Reason { NOTE_TOO_LONG, NO_TECHNICIAN }
    }
}

object TechnicianFormRequest {
    /**
     * The request, key by key in this order. Every item the technician did not answer is an explicit null: the four grades, the six functional rows, the three defect rows, the battery health, the game
     * question, the known-fault chip. `symptoms` is the chosen keys in the order they were SHOWN (an empty list = not answered; `none_noticed` is an element); `perAnswerAt` is the answer events' times
     * in order, a plain array of device-clock milliseconds as the server reads it [SRV technicianFormContract.js:155-157] - the item each time belongs to stays in [TechnicianFormState.perAnswerAt]
     * on the phone and is not on the wire (the server has no key for it; a key for it would be a new name to agree with the server: pending, not guessed). The prompt keys are null when the prompt
     * was not shown. The device clock's times are epoch milliseconds. `sentOffline` is NOT sent: the server reads it as "sent without a network" [SRV technicianFormContract.js:263, absent = false]
     * and this app has no offline send - a form is built and sent in the same call, a failed call leaves it editable and the next press stamps a new `submittedAt` - so there is nothing true to
     * put in it (a future offline queue must send `sentOffline: true`; `TechnicianFormRequestTest` fails if the key appears before that exists). Nothing the server decides is here.
     */
    fun build(state: TechnicianFormState, submittedAtMs: Long): FormRequestBuild {
        val technician = state.technicianId ?: return FormRequestBuild.Refused(FormRequestBuild.Refused.Reason.NO_TECHNICIAN)
        if (state.observedNote.length > TechnicianFormLimits.NOTE_MAX_CHARS) return FormRequestBuild.Refused(FormRequestBuild.Refused.Reason.NOTE_TOO_LONG)
        val shown = state.prompt as? PromptState.Shown
        val request = linkedMapOf<String, Any?>()
        request[TechnicianFormWire.KEY_SESSION_ID] = state.sessionId
        request[TechnicianFormWire.KEY_SLOT] = state.slot
        request[TechnicianFormWire.KEY_FORM_CONTRACT_VERSION] = TechnicianFormWire.FORM_CONTRACT_VERSION
        request[TechnicianFormWire.KEY_TECHNICIAN_ID] = technician
        request[TechnicianFormWire.KEY_GRADES] = gradesMap(state.gradesWithNulls())
        request[TechnicianFormWire.KEY_FUNCTIONAL] = FunctionalRow.values().associateWithLinked({ it.wire }) { state.functional[it]?.wire }
        request[TechnicianFormWire.KEY_DEFECTS] = DefectRow.values().associateWithLinked({ it.wire }) { state.defects[it]?.wire }
        request[TechnicianFormWire.KEY_SYMPTOMS] = (state.symptomOrderShown + Symptom.NONE_NOTICED).filter { it in state.symptoms }.map { it.wire }
        request[TechnicianFormWire.KEY_SYMPTOM_ORDER_SHOWN] = state.symptomOrderShown.map { it.wire }
        request[TechnicianFormWire.KEY_KNOWN_FAULT] = state.knownFaultBeforeScan
        request[TechnicianFormWire.KEY_BATTERY_HEALTH] = state.batteryHealth?.let { batteryHealthMap(it) }
        request[TechnicianFormWire.KEY_GAME_TRIED] = state.gameTried
        request[TechnicianFormWire.KEY_OBSERVED_NOTE] = state.observedNote.takeIf { it.isNotBlank() }
        request[TechnicianFormWire.KEY_OPENED_AT] = state.openedAtMs
        request[TechnicianFormWire.KEY_FIRST_TAP_AT] = state.firstTapAtMs
        request[TechnicianFormWire.KEY_PER_ANSWER_AT] = state.perAnswerAt.map { it.atMs }
        request[TechnicianFormWire.KEY_SUBMITTED_AT] = submittedAtMs
        request[TechnicianFormWire.KEY_ANSWERS_BEFORE_PROMPT] = shown?.let { gradesMap(it.answersBefore) }
        request[TechnicianFormWire.KEY_ANSWERS_AFTER_PROMPT] = shown?.let { gradesMap(state.gradesWithNulls()) }
        request[TechnicianFormWire.KEY_PROMPT_ACTION] = shown?.action?.wire
        return FormRequestBuild.Built(request)
    }

    private fun gradesMap(grades: Map<GradeCategory, GradeAnswer?>): Map<String, Any?> =
        GradeCategory.values().associateWithLinked({ it.wire }) { grades[it]?.wire }

    private fun batteryHealthMap(health: BatteryHealth): Map<String, Any?> = when (health) {
        is BatteryHealth.Percent -> linkedMapOf(
            TechnicianFormWire.KEY_BATTERY_KIND to BatteryHealthKind.PERCENT.wire,
            TechnicianFormWire.KEY_BATTERY_PERCENT to health.value,
            TechnicianFormWire.KEY_BATTERY_STATE_KEY to null
        )
        is BatteryHealth.State -> linkedMapOf(
            TechnicianFormWire.KEY_BATTERY_KIND to BatteryHealthKind.STATE.wire,
            TechnicianFormWire.KEY_BATTERY_PERCENT to null,
            TechnicianFormWire.KEY_BATTERY_STATE_KEY to health.key.wire
        )
        BatteryHealth.NotShown -> linkedMapOf(
            TechnicianFormWire.KEY_BATTERY_KIND to BatteryHealthKind.NOT_SHOWN.wire,
            TechnicianFormWire.KEY_BATTERY_PERCENT to null,
            TechnicianFormWire.KEY_BATTERY_STATE_KEY to null
        )
    }

    private inline fun <T> Array<T>.associateWithLinked(key: (T) -> String, value: (T) -> Any?): Map<String, Any?> {
        val out = linkedMapOf<String, Any?>()
        for (item in this) out[key(item)] = value(item)
        return out
    }
}
