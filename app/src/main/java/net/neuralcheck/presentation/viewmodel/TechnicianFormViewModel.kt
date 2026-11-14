package net.neuralcheck.presentation.viewmodel

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.neuralcheck.data.remote.FirebaseAnalysisService
import net.neuralcheck.data.remote.ReasonCodeRead
import net.neuralcheck.domain.form.BatteryHealth
import net.neuralcheck.domain.form.DefectRow
import net.neuralcheck.domain.form.FormOffer
import net.neuralcheck.domain.form.FormOpening
import net.neuralcheck.domain.form.FormRequestBuild
import net.neuralcheck.domain.form.FormTechnician
import net.neuralcheck.domain.form.FunctionalRow
import net.neuralcheck.domain.form.GradeAnswer
import net.neuralcheck.domain.form.GradeCategory
import net.neuralcheck.domain.form.RowAnswer
import net.neuralcheck.domain.form.SlotClosedReason
import net.neuralcheck.domain.form.SubmitBlock
import net.neuralcheck.domain.form.SubmitDecision
import net.neuralcheck.domain.form.Symptom
import net.neuralcheck.domain.form.SymptomOrder
import net.neuralcheck.domain.form.TechnicianFormConfigRead
import net.neuralcheck.domain.form.TechnicianFormConfigWire
import net.neuralcheck.domain.form.TechnicianFormRequest
import net.neuralcheck.domain.form.TechnicianFormState
import net.neuralcheck.domain.form.TechnicianFormWire
import net.neuralcheck.domain.form.confirmPrompt
import net.neuralcheck.domain.form.decideOffer
import net.neuralcheck.domain.form.decideOpening
import net.neuralcheck.domain.form.decideSecondSlot
import net.neuralcheck.domain.form.decideSubmit
import net.neuralcheck.domain.form.openForm
import net.neuralcheck.domain.form.revisePrompt
import net.neuralcheck.domain.form.toggleKnownFault
import net.neuralcheck.domain.form.toggleSymptom
import net.neuralcheck.domain.form.withBatteryHealth
import net.neuralcheck.domain.form.withDefect
import net.neuralcheck.domain.form.withFunctional
import net.neuralcheck.domain.form.withGameTried
import net.neuralcheck.domain.form.withGrade
import net.neuralcheck.domain.form.withNote
import net.neuralcheck.domain.form.withTechnician
import net.neuralcheck.util.EvalCardLog
import javax.inject.Inject

/**
 * The last technician this phone chose - one opaque id, never a name (PLAN-06:1307: "يُحفظ آخر اختيار للجهاز [اجتهاد]"; the names are the admin's and are never in this app). Read when a form opens, and
 * used only if the server's list still has that id.
 */
class TechnicianChoiceStore(private val prefs: SharedPreferences) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(context.getSharedPreferences(FILE, Context.MODE_PRIVATE))

    fun last(): String? = prefs.getString(KEY_LAST, null)?.takeIf { it.isNotBlank() }

    fun remember(technicianId: String) {
        prefs.edit().putString(KEY_LAST, technicianId).apply()
    }

    private companion object {
        const val FILE = "technician_form_choice"
        const val KEY_LAST = "last_technician_id"
    }
}

/**
 * The technician's form on the owner's phone - row A-P6-08. The state holder between the screen and [net.neuralcheck.domain.form]: it loads the form's configuration (the technicians to choose
 * from, the order of the symptom chips), opens one form per session slot, applies each tap through the pure functions of `TechnicianForm.kt`, and sends the form through `submitTechnicianForm`.
 * The screen that shows it is behind `TECH_FORM_TEXTS_APPROVED` (its texts are approved and written since 2026-10-23, and the guard is open); the server's half is not deployed yet, so until it is the configuration call fails.
 *
 * What it decides, and nothing more: the device clock stamps each tap; a submit that [decideSubmit] blocks, or that opens the contradiction prompt, is not sent; the form is final once the server accepts
 * it («وتُقفل عند الإرسال») - every later tap is ignored; a failed send is classified by `details.reasonCode` only (rule 21; `FORM_SLOT_SAME_TECHNICIAN` is the one the contract copy names, the other fourteen
 * `FORM_*` are named states with no text yet) and leaves the form editable and unchanged.
 */
@HiltViewModel
class TechnicianFormViewModel @Inject constructor(
    private val analysisService: FirebaseAnalysisService,
    private val choiceStore: TechnicianChoiceStore
) : ViewModel() {

    /** The configuration's state. */
    sealed interface ConfigUi {
        data object NotLoaded : ConfigUi
        data object Loading : ConfigUi

        /** The call failed (no `reasonCode` read from it matters here; the form's own gate, `FormCallsNotApprovedException`, lands here too): the form is not shown; [retryConfig] asks again. */
        data object Failed : ConfigUi

        /**
         * The answer arrived and cannot be read as any of the states below ([why]: the fixed token of `FormOffer.Unreadable`, a name for the log, never shown): the form is not offered; [retryConfig] asks again.
         * A missing or other-shaped key is this state - it is never read as "not enabled" or as "no technicians" (rule 22).
         */
        data class Unreadable(val why: String) : ConfigUi

        /** The shop has the form off (`enabled: false`, `formState: not_enabled`): there is no form. This is not [NoTechnicians] (until 2026-10-18 the two were one state). */
        data object NotEnabled : ConfigUi

        /** The shop has the form on but this slot's form is not to be filled ([reason]: it was already sent, or the second slot was not drawn). */
        data class SlotClosed(val reason: SlotClosedReason) : ConfigUi

        /** The answer is readable and the shop has the form on, but it names no technician to choose from (the admin has entered none, or every entry is unusable or inactive): a form with nobody to judge it is not offered. */
        data object NoTechnicians : ConfigUi

        data class Ready(val technicians: List<FormTechnician>, val symptomOrder: SymptomOrder) : ConfigUi
    }

    /** How a send failed. */
    sealed interface FormFailure {
        /** `FORM_SLOT_SAME_TECHNICIAN`: the second form was given by the technician who gave the first. */
        data object SameTechnician : FormFailure

        /** The server refused with another `details.reasonCode` ([token]: its wire text bounded by the log's printer, or `unrecognized`). */
        data class Refused(val token: String) : FormFailure

        /** No `reasonCode`: a disconnect, a timeout, any other failure - or the request could not be built ([Unbuildable]). */
        data object NotSent : FormFailure

        /** The state cannot be sent as it is (a note longer than the limit, no technician): nothing left the phone. */
        data object Unbuildable : FormFailure
    }

    sealed interface Submission {
        /** The technician is filling the form. [lastFailure] is how the last send ended, until the next send starts. */
        data class Editing(val lastFailure: FormFailure? = null) : Submission

        /** The call is out: every tap is ignored. */
        data object Sending : Submission

        /** The server accepted it. Final. */
        data object Submitted : Submission
    }

    data class UiState(
        val config: ConfigUi = ConfigUi.NotLoaded,
        val form: TechnicianFormState? = null,
        val submission: Submission = Submission.Editing(),
        /** Why the last press of «أرسل» did nothing on the device, until the next tap. */
        val blocked: SubmitBlock? = null,
        /** The contradiction prompt is on the screen, waiting for the technician's answer. */
        val promptOpen: Boolean = false
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    /** The device clock; a test replaces it. */
    internal var nowMs: () -> Long = { System.currentTimeMillis() }

    private var sessionId: String? = null

    /** The slot the screen asked for (A-P6-10): the first slot means «from the first on» - the screen moves to the second form by itself when the double evaluation wants one. NOT the slot shown: that is `form.slot`. */
    private var requestedSlot: Int = TechnicianFormConfigWire.SLOT_FIRST

    /** The configuration as it was last read: it already says whether the double evaluation drew the second form, so moving to it needs no second call. */
    private var lastRead: TechnicianFormConfigRead? = null

    /**
     * Opens the form of [sessionId] from [slot]: loads the configuration, then opens the form with the symptom order it names. From the FIRST slot (the default) it opens the first form while that is not sent, and
     * the second form - the double evaluation's, by another technician - when the first is sent and the server drew the second ([decideOpening]); [slot] = 2 asks for exactly the second. The technician this phone
     * chose last is preselected for the first form only, and only if the list still has him. Opening the form that is already open does nothing - the same session keeps what was filled in, and a screen built
     * again does not wipe the second form either; another session or slot starts a new form.
     */
    fun open(sessionId: String, slot: Int = TechnicianFormConfigWire.SLOT_FIRST) {
        if (this.sessionId == sessionId && this.requestedSlot == slot && _state.value.config != ConfigUi.NotLoaded) return
        this.sessionId = sessionId
        this.requestedSlot = slot
        lastRead = null
        _state.value = UiState()
        loadConfig()
    }

    /** Asks the configuration again after it failed or could not be read. */
    fun retryConfig() {
        val config = _state.value.config
        if (config is ConfigUi.Failed || config is ConfigUi.Unreadable) loadConfig()
    }

    private fun loadConfig() {
        val session = sessionId ?: return
        val slotAtCall = requestedSlot
        _state.update { it.copy(config = ConfigUi.Loading) }
        viewModelScope.launch {
            try {
                val read = TechnicianFormConfigRead.of(analysisService.getTechnicianFormConfig(session))
                // A-P6-10: from the first slot on, a first form already sent hands the screen to the second form when the server drew it (decideOpening); an explicit second slot is exactly that slot
                val opening = if (slotAtCall == TechnicianFormConfigWire.SLOT_FIRST) decideOpening(read) else FormOpening(slotAtCall, decideOffer(read, slotAtCall))
                val offered = opening.offer as? FormOffer.Offer
                EvalCardLog.technicianFormConfig(read.describe(), offered?.symptomOrder?.source?.token ?: "-", offered?.technicians?.size ?: 0, opening.offer.token)
                if (sessionId == session) lastRead = read
                _state.update { s -> if (sessionId != session) s else s.opened(session, opening) }
            } catch (ce: kotlinx.coroutines.CancellationException) {
                throw ce
            } catch (t: Throwable) {
                EvalCardLog.technicianFormEvent("config-failed", session, reasonToken(t))
                _state.update { s -> if (sessionId != session) s else s.copy(config = ConfigUi.Failed) }
            }
        }
    }

    /**
     * The state a screen is in once [opening] is decided: the form of that slot with an empty state and nobody chosen, or the named reason there is none. The technician this phone chose last is preselected for the
     * FIRST form only (A-P6-10): that technician is the one who gave the first form, and the second form is by ANOTHER one - preselecting him would put the refusal `FORM_SLOT_SAME_TECHNICIAN` one tap away.
     */
    private fun UiState.opened(session: String, opening: FormOpening): UiState = when (val offer = opening.offer) {
        is FormOffer.Offer -> {
            val remembered = if (opening.slot == TechnicianFormConfigWire.SLOT_FIRST) choiceStore.last()?.takeIf { id -> offer.technicians.any { it.id == id } } else null
            val form = openForm(session, opening.slot, offer.symptomOrder.order, nowMs()).withTechnician(remembered)
            copy(config = ConfigUi.Ready(offer.technicians, offer.symptomOrder), form = form)
        }
        FormOffer.NotEnabled -> copy(config = ConfigUi.NotEnabled)
        is FormOffer.SlotClosed -> copy(config = ConfigUi.SlotClosed(offer.reason))
        FormOffer.NoTechnicians -> copy(config = ConfigUi.NoTechnicians)
        is FormOffer.Unreadable -> copy(config = ConfigUi.Unreadable(offer.why))
    }

    // ---- the taps: ignored unless the form is open and editable ----

    private inline fun edit(change: (TechnicianFormState, Long) -> TechnicianFormState) {
        _state.update { s ->
            val form = s.form ?: return@update s
            if (s.submission !is Submission.Editing || s.promptOpen) return@update s
            s.copy(form = change(form, nowMs()), blocked = null)
        }
    }

    fun chooseTechnician(technicianId: String) = edit { f, _ -> f.withTechnician(technicianId) }
    fun grade(category: GradeCategory, answer: GradeAnswer) = edit { f, now -> f.withGrade(category, answer, now) }
    fun functional(row: FunctionalRow, answer: RowAnswer) = edit { f, now -> f.withFunctional(row, answer, now) }
    fun defect(row: DefectRow, answer: RowAnswer) = edit { f, now -> f.withDefect(row, answer, now) }
    fun toggleSymptom(symptom: Symptom) = edit { f, now -> f.toggleSymptom(symptom, now) }
    fun toggleKnownFault() = edit { f, now -> f.toggleKnownFault(now) }
    fun batteryHealth(answer: BatteryHealth?) = edit { f, now -> f.withBatteryHealth(answer, now) }
    fun gameTried(answer: Boolean) = edit { f, now -> f.withGameTried(answer, now) }
    fun note(text: String) = edit { f, now -> f.withNote(text, now) }

    // ---- the submit ----

    /** «أرسل التقييم». */
    fun submit() {
        val s = _state.value
        val form = s.form ?: return
        if (s.submission !is Submission.Editing || s.promptOpen) return
        when (val decision = form.decideSubmit()) {
            is SubmitDecision.Blocked -> {
                EvalCardLog.technicianFormEvent("submit-blocked", form.sessionId, decision.reason.name.lowercase())
                _state.update { it.copy(blocked = decision.reason) }
            }
            is SubmitDecision.ShowPrompt -> {
                EvalCardLog.technicianFormEvent("prompt-shown", form.sessionId, "overall_high_with_weak_category")
                _state.update { it.copy(form = decision.state, promptOpen = true, blocked = null) }
            }
            is SubmitDecision.Send -> send(decision.state)
        }
    }

    /** «نعم، أرسل»: the technician confirmed the answers the prompt questioned. */
    fun confirmPrompt() {
        val s = _state.value
        val form = s.form ?: return
        if (!s.promptOpen) return
        val confirmed = form.confirmPrompt()
        _state.update { it.copy(form = confirmed, promptOpen = false) }
        send(confirmed)
    }

    /** «سأراجع»: the technician goes back to the form. */
    fun revisePrompt() {
        val s = _state.value
        val form = s.form ?: return
        if (!s.promptOpen) return
        EvalCardLog.technicianFormEvent("prompt-revise", form.sessionId, "-")
        _state.update { it.copy(form = form.revisePrompt(), promptOpen = false) }
    }

    private fun send(form: TechnicianFormState) {
        val submittedAt = nowMs()
        val request = when (val built = TechnicianFormRequest.build(form, submittedAt)) {
            is FormRequestBuild.Built -> built.request
            is FormRequestBuild.Refused -> {
                EvalCardLog.technicianFormEvent("unbuildable", form.sessionId, built.reason.name.lowercase())
                _state.update { it.copy(submission = Submission.Editing(FormFailure.Unbuildable)) }
                return
            }
        }
        _state.update { it.copy(submission = Submission.Sending) }
        viewModelScope.launch {
            try {
                analysisService.submitTechnicianForm(request)
                // the phone remembers the technician of the FIRST form: the second one is by another technician and does not replace him (A-P6-10)
                if (form.slot == TechnicianFormConfigWire.SLOT_FIRST) form.technicianId?.let { choiceStore.remember(it) }
                EvalCardLog.technicianFormEvent("submitted", form.sessionId, "slot${form.slot}")
                // A-P6-10 (row ف٩, PLAN-06:1305: «وعلى الجهاز نفسه تُفرَّغ الشاشة بعد الإرسال»): when the first form is sent and the server drew the second, the screen is a NEW form - nothing of the first is kept, so the
                // second technician does not see the first one's answers. The answer already read says whether it was drawn, so there is no second call; a draw that cannot be read is a named state, not «not drawn».
                val second = if (form.slot == TechnicianFormConfigWire.SLOT_FIRST) lastRead?.let { decideSecondSlot(it) } else null
                if (second != null) EvalCardLog.technicianFormEvent("second-evaluation", form.sessionId, second.offer.token)
                _state.update { s ->
                    when {
                        s.form?.sessionId != form.sessionId -> s
                        second != null -> UiState().opened(form.sessionId, second)
                        else -> s.copy(submission = Submission.Submitted)
                    }
                }
            } catch (ce: kotlinx.coroutines.CancellationException) {
                throw ce
            } catch (t: Throwable) {
                val failure = failureOf(t)
                EvalCardLog.technicianFormEvent("submit-failed", form.sessionId, reasonToken(t))
                _state.update { if (it.form?.sessionId == form.sessionId) it.copy(submission = Submission.Editing(failure)) else it }
            }
        }
    }

    /** A failed send, classified by `details.reasonCode` only (rule 21). */
    internal fun failureOf(t: Throwable): FormFailure = when (val read = ReasonCodeRead.of(t)) {
        is ReasonCodeRead.Known -> FormFailure.Refused(read.code.wire)
        is ReasonCodeRead.Unrecognized ->
            if (read.raw == TechnicianFormWire.REFUSAL_SLOT_SAME_TECHNICIAN) FormFailure.SameTechnician
            else FormFailure.Refused(if (read.raw.startsWith(TechnicianFormWire.REFUSAL_PREFIX)) read.raw else "unrecognized")
        ReasonCodeRead.Absent -> FormFailure.NotSent
    }

    private fun reasonToken(t: Throwable): String = when (val read = ReasonCodeRead.of(t)) {
        is ReasonCodeRead.Known -> read.code.wire
        is ReasonCodeRead.Unrecognized -> read.raw
        ReasonCodeRead.Absent -> "absent"
    }

    private fun TechnicianFormConfigRead.describe(): String = when (this) {
        TechnicianFormConfigRead.Absent -> "absent"
        is TechnicianFormConfigRead.NotAnObject -> "not_an_object_$kind"
        is TechnicianFormConfigRead.Read -> "object"
    }
}
