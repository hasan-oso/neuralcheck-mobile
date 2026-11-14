package net.neuralcheck.presentation.screen

import android.content.res.Resources
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import net.neuralcheck.domain.form.BatteryHealth
import net.neuralcheck.domain.form.BatteryStateKey
import net.neuralcheck.domain.form.DefectRow
import net.neuralcheck.domain.form.FunctionalRow
import net.neuralcheck.domain.form.GradeAnswer
import net.neuralcheck.domain.form.GradeCategory
import net.neuralcheck.domain.form.RowAnswer
import net.neuralcheck.domain.form.SlotClosedReason
import net.neuralcheck.domain.form.SubmitBlock
import net.neuralcheck.domain.form.Symptom
import net.neuralcheck.domain.form.TechnicianFormConfigWire
import net.neuralcheck.presentation.theme.TextPrimary
import net.neuralcheck.presentation.theme.TextSecondary
import net.neuralcheck.presentation.viewmodel.TechnicianFormViewModel

/**
 * THE guard of the technician's form (A-P6-08; contract A5 / PC:321: «نصوص B2 (§١٠-ب-٨) غير معتمدة»). The form's logic, state, request and view model are built and tested; the screen draws the form only while
 * this is true and [technicianFormLabels] has labels, and `FirebaseAnalysisService` lets the form's three callables leave the phone only while this is true (its exit gate `formCallsApproved` READS THIS GUARD,
 * rule 35). It may be true only when every text of [FormText] is approved: [FormTexts.pending] is the list of those that are not, and `TechnicianFormGuardTest` fails if this is true while it is not empty.
 *
 * Every text is approved and written since 2026-10-23: the 38 approved before (the levels, their descriptions, the rules, the symptoms, the game question) and the 48 that were pending - Hasan delegated them on
 * 2026-10-22 and each was reviewed against the code on 2026-10-23 (`scratchpad/app-delegated/texts-review.md`; the consent's final legal wording is NOT among them: it waits for the KVKK lawyer). They are in
 * `values/strings.xml` (Arabic only: the technician's interface is Arabic only), and the one sentence the scanned phone shows ([FormText.ScanInProgress]) also in `values-tr/strings.xml`. [FormText.resource]
 * maps each text to its one resource and [formLabelsOf] reads it.
 *
 * It is TRUE since the change that followed (2026-10-23, its own commit). What flipping it did BESIDES showing the form: the scanned phone's old evaluation card leaves EVERY scan screen
 * ([ScannedPhoneScreen.Parts.evaluationCard] is `!formLive`), whether or not the shop has the form part on - a shop without it has no evaluation on either phone (the form draws nothing for it:
 * `ConfigUi.NotEnabled`; `FormLiveScreenShapeTest` pins this known effect); the form's three callables can leave the phone (the owner's phone asks for the configuration and sends the form; the owner's and the
 * customer's phones tell the server the report was opened - `ViewModels.kt`, `sessionAtScan`, is also read for the repeat offer); and the sentence of a session scan shows on the scanned phone. The server's half
 * of the form is NOT deployed on production yet: until it is, the configuration call fails and the owner's phone shows the «تعذّر تحميل الاستمارة» screen with its retry - an APK carrying this guard must not
 * reach a shop before the server's half does.
 */
internal const val TECH_FORM_TEXTS_APPROVED: Boolean = true

/** How a text of the form stands against the user's approvals. */
internal enum class TextApproval {
    /** The levels' words and descriptions and the three rules above the form: `plan/TECH-LEVELS-WORDING-FINAL-2026-10-10.md`, final approval 2026-10-10. */
    APPROVED_LEVELS,

    /** The twelve symptoms (and «لم ألاحظ شيئاً»): Arabic approved, PLAN-06:1262-1278; «جُرّبت لعبة: نعم/لا»: the user's own wording, TL:31. */
    APPROVED_SYMPTOMS_OR_USER_WORDING,

    /**
     * The texts that were pending (proposed by the plan, or written by no one): Hasan delegated them on 2026-10-22 («اعتمد بتقديرك الأنسب كل القرارات والنصوص المعلّقة») and each was reviewed against the code on
     * 2026-10-23 - approved as proposed, or reworded where the code does not do what the text says (`texts-review.md`). Written, verbatim, to the resources.
     */
    APPROVED_BY_DELEGATION,

    /** An existing text of the app that the form reuses: [FormText.ConfigRetry] is `btn_retry`. */
    EXISTING_APPROVED,

    /** Proposed, not approved: a slot added later without its wording. The guard may not be true while any text is this. */
    PENDING
}

/**
 * Every text the surface shows - one entry per place, so the list of what needs the user's approval is the code's, not a memory (and the one text of the scanned phone's screen, [FormTexts.drawnByScannedPhone]). [key] is unique per text.
 */
internal sealed interface FormText {
    val approval: TextApproval

    data object Title : FormText { override val approval = TextApproval.APPROVED_BY_DELEGATION }
    data object WhoJudges : FormText { override val approval = TextApproval.APPROVED_BY_DELEGATION }
    data class GradeRow(val category: GradeCategory) : FormText { override val approval = TextApproval.APPROVED_BY_DELEGATION }

    /** The level words, and «لم أفحصها» (TL:11: not a level). */
    data class Level(val answer: GradeAnswer) : FormText { override val approval = TextApproval.APPROVED_LEVELS }

    /** The description with an example of one level of one category (TL:18-62). */
    data class LevelDescription(val category: GradeCategory, val answer: GradeAnswer) : FormText { override val approval = TextApproval.APPROVED_LEVELS }

    /** The three rules above the form (TL:13-16). */
    data class Rule(val number: Int) : FormText { override val approval = TextApproval.APPROVED_LEVELS }

    data class FunctionalRowTitle(val row: FunctionalRow) : FormText { override val approval = TextApproval.APPROVED_BY_DELEGATION }
    data class DefectRowTitle(val row: DefectRow) : FormText { override val approval = TextApproval.APPROVED_BY_DELEGATION }
    data class RowAnswerText(val answer: RowAnswer) : FormText { override val approval = TextApproval.APPROVED_BY_DELEGATION }
    data object SymptomsHeader : FormText { override val approval = TextApproval.APPROVED_BY_DELEGATION }
    data class SymptomText(val symptom: Symptom) : FormText { override val approval = TextApproval.APPROVED_SYMPTOMS_OR_USER_WORDING }
    data object KnownFault : FormText { override val approval = TextApproval.APPROVED_BY_DELEGATION }
    data object BatteryHealthTitle : FormText { override val approval = TextApproval.APPROVED_BY_DELEGATION }
    data object BatteryPercent : FormText { override val approval = TextApproval.APPROVED_BY_DELEGATION }
    data object BatteryStateTitle : FormText { override val approval = TextApproval.APPROVED_BY_DELEGATION }
    data class BatteryStateText(val key: BatteryStateKey) : FormText { override val approval = TextApproval.APPROVED_BY_DELEGATION }
    data object BatteryNotShown : FormText { override val approval = TextApproval.APPROVED_BY_DELEGATION }
    data object GameTried : FormText { override val approval = TextApproval.APPROVED_SYMPTOMS_OR_USER_WORDING }
    data object Yes : FormText { override val approval = TextApproval.APPROVED_BY_DELEGATION }
    data object No : FormText { override val approval = TextApproval.APPROVED_BY_DELEGATION }
    data object NoteLabel : FormText { override val approval = TextApproval.APPROVED_BY_DELEGATION }
    data object PromptBody : FormText { override val approval = TextApproval.APPROVED_BY_DELEGATION }
    data object PromptConfirm : FormText { override val approval = TextApproval.APPROVED_BY_DELEGATION }
    data object PromptRevise : FormText { override val approval = TextApproval.APPROVED_BY_DELEGATION }
    data object Submit : FormText { override val approval = TextApproval.APPROVED_BY_DELEGATION }
    data object Sending : FormText { override val approval = TextApproval.APPROVED_BY_DELEGATION }
    data object Submitted : FormText { override val approval = TextApproval.APPROVED_BY_DELEGATION }
    data class Blocked(val reason: SubmitBlock) : FormText { override val approval = TextApproval.APPROVED_BY_DELEGATION }
    data object FailureSameTechnician : FormText { override val approval = TextApproval.APPROVED_BY_DELEGATION }
    data object FailureRefused : FormText { override val approval = TextApproval.APPROVED_BY_DELEGATION }
    data object FailureNotSent : FormText { override val approval = TextApproval.APPROVED_BY_DELEGATION }
    data object ConfigLoading : FormText { override val approval = TextApproval.APPROVED_BY_DELEGATION }
    data object ConfigFailed : FormText { override val approval = TextApproval.APPROVED_BY_DELEGATION }
    data object ConfigRetry : FormText { override val approval = TextApproval.EXISTING_APPROVED }
    data object NoTechnicians : FormText { override val approval = TextApproval.APPROVED_BY_DELEGATION }

    /** A-P6-10 (screen ف٩ of PLAN-06:951; A5g-1): above the SECOND form, the one the double evaluation asks of another technician. Proposed there, approved by delegation 2026-10-23. */
    data object SecondEvalNeeded : FormText { override val approval = TextApproval.APPROVED_BY_DELEGATION }

    /** A-P6-10 (screen ف٩ of PLAN-06:951; A5g-3): under the line above it. Proposed there, approved by delegation 2026-10-23. */
    data object HandOverDevice : FormText { override val approval = TextApproval.APPROVED_BY_DELEGATION }

    /**
     * A-P6-09 (screen ف١٢ of PLAN-06:954; A4-1): the one sentence of the scanned phone while a SESSION scan runs - proposed there, approved by delegation 2026-10-23, in Arabic and Turkish (the customer sees that phone;
     * the Turkish is proposed and needs a speaker). It is drawn by the SCAN screen (`ActiveScanScreen`, under the progress bar), not by [TechnicianFormSurface]; it is listed here so that the list of what needs the
     * user's approval - and the guard above - is one list ([FormTexts.drawnByScannedPhone]).
     */
    data object ScanInProgress : FormText { override val approval = TextApproval.APPROVED_BY_DELEGATION }
}

internal object FormTexts {
    private val levelsAnswered = GradeAnswer.values().filter { it.isLevel }

    /** Every text the surface can show. */
    val all: List<FormText> = buildList {
        add(FormText.Title); add(FormText.WhoJudges)
        for (c in GradeCategory.values()) {
            add(FormText.GradeRow(c))
            for (l in levelsAnswered) add(FormText.LevelDescription(c, l))
        }
        for (a in GradeAnswer.values()) add(FormText.Level(a))
        for (n in 1..3) add(FormText.Rule(n))
        for (r in FunctionalRow.values()) add(FormText.FunctionalRowTitle(r))
        for (r in DefectRow.values()) add(FormText.DefectRowTitle(r))
        for (a in RowAnswer.values()) add(FormText.RowAnswerText(a))
        add(FormText.SymptomsHeader)
        for (s in Symptom.values()) add(FormText.SymptomText(s))
        add(FormText.KnownFault)
        add(FormText.BatteryHealthTitle); add(FormText.BatteryPercent); add(FormText.BatteryStateTitle)
        for (k in BatteryStateKey.values()) add(FormText.BatteryStateText(k))
        add(FormText.BatteryNotShown)
        add(FormText.GameTried); add(FormText.Yes); add(FormText.No)
        add(FormText.NoteLabel)
        add(FormText.PromptBody); add(FormText.PromptConfirm); add(FormText.PromptRevise)
        add(FormText.Submit); add(FormText.Sending); add(FormText.Submitted)
        for (b in SubmitBlock.values()) add(FormText.Blocked(b))
        add(FormText.FailureSameTechnician); add(FormText.FailureRefused); add(FormText.FailureNotSent)
        add(FormText.ConfigLoading); add(FormText.ConfigFailed); add(FormText.ConfigRetry); add(FormText.NoTechnicians)
        add(FormText.SecondEvalNeeded); add(FormText.HandOverDevice)
        add(FormText.ScanInProgress)
    }

    /** The entries of [all] that the scan screen of the SCANNED phone draws (through the same labels), not the technician's surface: the surface's own slots are `all - drawnByScannedPhone`. */
    val drawnByScannedPhone: Set<FormText> = setOf(FormText.ScanInProgress)

    /** The texts that are not approved: the guard may be true only when this is empty. */
    val pending: List<FormText> get() = all.filter { it.approval == TextApproval.PENDING }
}

/** The labels of the surface: one text per [FormText]. */
internal typealias FormLabels = (FormText) -> String

/**
 * The approved labels, read from the resources in the language the phone is showing - or null while [TECH_FORM_TEXTS_APPROVED] is false (two locks, as before: the screens draw only for a non-null result AND the
 * guard). Reading [LocalConfiguration] makes a change of language recompose with the other language's texts (the technician's texts have no Turkish: Android falls back to the Arabic of `values/`, on purpose).
 */
@Composable
internal fun technicianFormLabels(): FormLabels? {
    if (!TECH_FORM_TEXTS_APPROVED) return null
    LocalConfiguration.current
    return formLabelsOf(LocalContext.current.resources)
}

/** Every text of the form from [resources]: the one `R.string` of [FormText.resource], read as it is (no text of the form takes an argument). */
internal fun formLabelsOf(resources: Resources): FormLabels = { text -> resources.getString(text.resource()) }

internal const val TECH_FORM_TAG = "technicianForm"
internal fun formTag(vararg parts: String): String = "form_" + parts.joinToString("_")

/** What the surface asks of its owner: one callback per tap, all of them the view model's functions. */
internal class TechnicianFormActions(
    val chooseTechnician: (String) -> Unit,
    val grade: (GradeCategory, GradeAnswer) -> Unit,
    val functional: (FunctionalRow, RowAnswer) -> Unit,
    val defect: (DefectRow, RowAnswer) -> Unit,
    val toggleSymptom: (Symptom) -> Unit,
    val toggleKnownFault: () -> Unit,
    val batteryHealth: (BatteryHealth?) -> Unit,
    val gameTried: (Boolean) -> Unit,
    val note: (String) -> Unit,
    val submit: () -> Unit,
    val confirmPrompt: () -> Unit,
    val revisePrompt: () -> Unit,
    val retryConfig: () -> Unit
)

/**
 * The form, one column. It draws what [ui] says and reports each tap: the editable controls are enabled only while [TechnicianFormViewModel.Submission.Editing] and no prompt is open; once the server accepted the
 * form every control is disabled and [FormText.Submitted] shows. Every text is read through [labels]; this file holds none.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TechnicianFormSurface(ui: TechnicianFormViewModel.UiState, labels: FormLabels, actions: TechnicianFormActions, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth().testTag(TECH_FORM_TAG), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        when (val config = ui.config) {
            TechnicianFormViewModel.ConfigUi.NotLoaded, TechnicianFormViewModel.ConfigUi.Loading -> Text(labels(FormText.ConfigLoading), color = TextSecondary, fontSize = 13.sp)
            // a failed call and an answer that cannot be read are two states of the view model (named apart in the log) and one screen: the form did not load, ask again - the existing texts, none new
            TechnicianFormViewModel.ConfigUi.Failed, is TechnicianFormViewModel.ConfigUi.Unreadable -> {
                Text(labels(FormText.ConfigFailed), color = TextPrimary, fontSize = 14.sp)
                TextButton(onClick = actions.retryConfig, modifier = Modifier.testTag(formTag("retryConfig"))) { Text(labels(FormText.ConfigRetry)) }
            }
            // the shop has the form off: there is no form and nothing to say - not "no technicians" (A-P6-08, rule 22), and no new text
            TechnicianFormViewModel.ConfigUi.NotEnabled -> Unit
            is TechnicianFormViewModel.ConfigUi.SlotClosed -> when (config.reason) {
                SlotClosedReason.SUBMITTED -> Text(labels(FormText.Submitted), color = TextPrimary, fontSize = 14.sp, modifier = Modifier.testTag(formTag("slotSubmitted")))
                SlotClosedReason.NOT_DRAWN -> Unit
            }
            TechnicianFormViewModel.ConfigUi.NoTechnicians -> Text(labels(FormText.NoTechnicians), color = TextPrimary, fontSize = 14.sp)
            is TechnicianFormViewModel.ConfigUi.Ready -> {
                val form = ui.form ?: return@Column
                val editable = ui.submission is TechnicianFormViewModel.Submission.Editing && !ui.promptOpen
                // A-P6-10 (ف٩): the second form of the double evaluation is another technician's - the screen says so, and says to hand the phone over, above a form that is NEW (the view model cleared the first one's)
                if (form.slot == TechnicianFormConfigWire.SLOT_SECOND) {
                    Text(labels(FormText.SecondEvalNeeded), color = TextPrimary, fontSize = 14.sp, modifier = Modifier.testTag(formTag("secondEval")))
                    Text(labels(FormText.HandOverDevice), color = TextSecondary, fontSize = 13.sp, modifier = Modifier.testTag(formTag("handOver")))
                }
                Text(labels(FormText.Title), color = TextPrimary, fontSize = 16.sp)
                for (n in 1..3) Text(labels(FormText.Rule(n)), color = TextSecondary, fontSize = 12.sp)

                Text(labels(FormText.WhoJudges), color = TextPrimary, fontSize = 14.sp)
                ChipRow {
                    for (t in config.technicians) {
                        FilterChip(
                            selected = form.technicianId == t.id, onClick = { actions.chooseTechnician(t.id) }, enabled = editable,
                            label = { Text(t.displayName) }, modifier = Modifier.testTag(formTag("technician", t.id))
                        )
                    }
                }

                for (category in GradeCategory.values()) {
                    Text(labels(FormText.GradeRow(category)), color = TextPrimary, fontSize = 14.sp)
                    ChipRow {
                        for (answer in GradeAnswer.values()) {
                            FilterChip(
                                selected = form.grades[category] == answer, onClick = { actions.grade(category, answer) }, enabled = editable,
                                label = { Text(labels(FormText.Level(answer))) }, modifier = Modifier.testTag(formTag("grade", category.wire, answer.wire))
                            )
                        }
                    }
                    form.grades[category]?.takeIf { it.isLevel }?.let { Text(labels(FormText.LevelDescription(category, it)), color = TextSecondary, fontSize = 12.sp) }
                }

                for (row in FunctionalRow.values()) AnswerRow(labels(FormText.FunctionalRowTitle(row)), form.functional[row], editable, "functional_${row.wire}", labels) { actions.functional(row, it) }
                for (row in DefectRow.values()) AnswerRow(labels(FormText.DefectRowTitle(row)), form.defects[row], editable, "defect_${row.wire}", labels) { actions.defect(row, it) }

                Text(labels(FormText.SymptomsHeader), color = TextPrimary, fontSize = 14.sp)
                ChipRow {
                    for (s in form.symptomOrderShown + Symptom.NONE_NOTICED) {
                        FilterChip(
                            selected = s in form.symptoms, onClick = { actions.toggleSymptom(s) }, enabled = editable,
                            label = { Text(labels(FormText.SymptomText(s))) }, modifier = Modifier.testTag(formTag("symptom", s.wire))
                        )
                    }
                    FilterChip(
                        selected = form.knownFaultBeforeScan == true, onClick = actions.toggleKnownFault, enabled = editable,
                        label = { Text(labels(FormText.KnownFault)) }, modifier = Modifier.testTag(formTag("knownFault"))
                    )
                }

                Text(labels(FormText.BatteryHealthTitle), color = TextPrimary, fontSize = 14.sp)
                OutlinedTextField(
                    value = (form.batteryHealth as? BatteryHealth.Percent)?.value?.toString().orEmpty(),
                    onValueChange = { text ->
                        val digits = text.filter { it.isDigit() }.take(3)
                        val value = digits.toIntOrNull()
                        // emptied: back to not answered, but only when the field held the percentage (a state chip's answer is not the field's to clear)
                        if (digits.isEmpty()) { if (form.batteryHealth is BatteryHealth.Percent) actions.batteryHealth(null) }
                        else if (value != null && value in 0..100) actions.batteryHealth(BatteryHealth.Percent(value))
                    },
                    enabled = editable, singleLine = true, label = { Text(labels(FormText.BatteryPercent)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().testTag(formTag("batteryPercent"))
                )
                Text(labels(FormText.BatteryStateTitle), color = TextSecondary, fontSize = 12.sp)
                ChipRow {
                    for (key in BatteryStateKey.values()) {
                        FilterChip(
                            selected = (form.batteryHealth as? BatteryHealth.State)?.key == key, onClick = { actions.batteryHealth(BatteryHealth.State(key)) }, enabled = editable,
                            label = { Text(labels(FormText.BatteryStateText(key))) }, modifier = Modifier.testTag(formTag("batteryState", key.wire))
                        )
                    }
                    FilterChip(
                        selected = form.batteryHealth == BatteryHealth.NotShown, onClick = { actions.batteryHealth(BatteryHealth.NotShown) }, enabled = editable,
                        label = { Text(labels(FormText.BatteryNotShown)) }, modifier = Modifier.testTag(formTag("batteryNotShown"))
                    )
                }

                Text(labels(FormText.GameTried), color = TextPrimary, fontSize = 14.sp)
                ChipRow {
                    FilterChip(selected = form.gameTried == true, onClick = { actions.gameTried(true) }, enabled = editable, label = { Text(labels(FormText.Yes)) }, modifier = Modifier.testTag(formTag("game", "yes")))
                    FilterChip(selected = form.gameTried == false, onClick = { actions.gameTried(false) }, enabled = editable, label = { Text(labels(FormText.No)) }, modifier = Modifier.testTag(formTag("game", "no")))
                }

                OutlinedTextField(
                    value = form.observedNote, onValueChange = actions.note, enabled = editable,
                    label = { Text(labels(FormText.NoteLabel)) }, modifier = Modifier.fillMaxWidth().testTag(formTag("note"))
                )

                ui.blocked?.let { Text(labels(FormText.Blocked(it)), color = TextPrimary, fontSize = 13.sp, modifier = Modifier.testTag(formTag("blocked"))) }
                (ui.submission as? TechnicianFormViewModel.Submission.Editing)?.lastFailure?.let { failure ->
                    val slot = when (failure) {
                        TechnicianFormViewModel.FormFailure.SameTechnician -> FormText.FailureSameTechnician
                        is TechnicianFormViewModel.FormFailure.Refused -> FormText.FailureRefused
                        TechnicianFormViewModel.FormFailure.NotSent, TechnicianFormViewModel.FormFailure.Unbuildable -> FormText.FailureNotSent
                    }
                    Text(labels(slot), color = TextPrimary, fontSize = 13.sp, modifier = Modifier.testTag(formTag("failure")))
                }

                when (ui.submission) {
                    TechnicianFormViewModel.Submission.Submitted -> Text(labels(FormText.Submitted), color = TextPrimary, fontSize = 14.sp, modifier = Modifier.testTag(formTag("submitted")))
                    TechnicianFormViewModel.Submission.Sending -> Text(labels(FormText.Sending), color = TextSecondary, fontSize = 13.sp)
                    is TechnicianFormViewModel.Submission.Editing ->
                        Button(onClick = actions.submit, enabled = editable, modifier = Modifier.fillMaxWidth().testTag(formTag("submit"))) { Text(labels(FormText.Submit)) }
                }

                if (ui.promptOpen) {
                    AlertDialog(
                        onDismissRequest = {},
                        text = { Text(labels(FormText.PromptBody)) },
                        confirmButton = { TextButton(onClick = actions.confirmPrompt, modifier = Modifier.testTag(formTag("promptConfirm"))) { Text(labels(FormText.PromptConfirm)) } },
                        dismissButton = { TextButton(onClick = actions.revisePrompt, modifier = Modifier.testTag(formTag("promptRevise"))) { Text(labels(FormText.PromptRevise)) } },
                        modifier = Modifier.testTag(formTag("prompt"))
                    )
                }
            }
        }
    }
}

/** The chips of one question, wrapping to the next line when the width runs out (a 360 dp phone holds three Arabic words a line). */
@OptIn(ExperimentalLayoutApi::class) // FlowRow: experimental in foundation-layout 1.6.x (BOM 2024.04.01), as ReportScreen opts in
@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) { content() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AnswerRow(title: String, answer: RowAnswer?, editable: Boolean, tag: String, labels: FormLabels, onAnswer: (RowAnswer) -> Unit) {
    Text(title, color = TextPrimary, fontSize = 14.sp)
    ChipRow {
        for (a in RowAnswer.values()) {
            FilterChip(selected = answer == a, onClick = { onAnswer(a) }, enabled = editable, label = { Text(labels(FormText.RowAnswerText(a))) }, modifier = Modifier.testTag(formTag(tag, a.wire)))
        }
    }
}

/**
 * The form of one session on the owner's screen: the view model's state drawn by [TechnicianFormSurface] with the view model's functions as the taps. Called from `GenerateSessionScreen` only behind
 * [TECH_FORM_TEXTS_APPROVED] and approved [labels]; it opens the form of [sessionId] when it enters the composition.
 */
@Composable
internal fun TechnicianFormSection(sessionId: String, labels: FormLabels, viewModel: TechnicianFormViewModel = hiltViewModel()) {
    val ui by viewModel.state.collectAsState()
    LaunchedEffect(sessionId) { viewModel.open(sessionId) }
    val actions = remember(viewModel) {
        TechnicianFormActions(
            chooseTechnician = viewModel::chooseTechnician, grade = viewModel::grade, functional = viewModel::functional, defect = viewModel::defect,
            toggleSymptom = viewModel::toggleSymptom, toggleKnownFault = viewModel::toggleKnownFault, batteryHealth = viewModel::batteryHealth, gameTried = viewModel::gameTried,
            note = viewModel::note, submit = viewModel::submit, confirmPrompt = viewModel::confirmPrompt, revisePrompt = viewModel::revisePrompt, retryConfig = viewModel::retryConfig
        )
    }
    TechnicianFormSurface(ui = ui, labels = labels, actions = actions)
}
