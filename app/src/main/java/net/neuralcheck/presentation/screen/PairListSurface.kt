package net.neuralcheck.presentation.screen

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.neuralcheck.R
import net.neuralcheck.domain.consent.ConsentLocaleRead
import net.neuralcheck.domain.pair.CandidateName
import net.neuralcheck.domain.pair.PairCandidate
import net.neuralcheck.domain.pair.PairFailureKind
import net.neuralcheck.domain.pair.candidateRow
import net.neuralcheck.presentation.theme.CardDark
import net.neuralcheck.presentation.theme.NeuralGreen
import net.neuralcheck.presentation.theme.TextPrimary
import net.neuralcheck.presentation.theme.TextSecondary
import net.neuralcheck.presentation.viewmodel.PairConfirmUi
import net.neuralcheck.presentation.viewmodel.PairFailure
import net.neuralcheck.presentation.viewmodel.PairListUi
import net.neuralcheck.util.NumberFormat
import java.time.ZoneId
import java.util.Locale

/**
 * THE guard of the «after maintenance» pair list (order of 2026-10-22, decisions of Hasan 1-7; `evidence/run-2026-10-18/WIRE-SPEC-MAINTENANCE-PAIRS-2026-10-22.md` section 3-c and 3-e). The texts of the list, its
 * empty state, the confirmation, the name of a phone with none and the refusals - rows ص9 to ص20 of `TEXTS-FOR-APPROVAL-MAINTENANCE-PAIRS-2026-10-22.md`, ص12 and ص13 being the existing `qr_linked_success_fmt` and
 * `btn_confirm` - were APPROVED by Hasan on 2026-10-22 («النصوص ص1-ص21 معتمدة كما كتبتَها، بالعربية والتركية») and are written, verbatim, to `values/strings.xml` and `values-tr/strings.xml` (the Turkish is approved before
 * a speaker review: TR-TEXTS-NEED-SPEAKER-REVIEW-2026-10-22.md). So this is TRUE: [pairLabels] reads them, the screen draws the list when the owner's session asks for it ([pairListVisible]), and
 * `FirebaseAnalysisService` lets the two callables leave the phone - its exit gate `pairCallsApproved` READS THIS GUARD (rule 35), whoever asks.
 *
 * What still decides whether anything happens for a shop is the SERVER: the list is asked for only after the server accepted a `maintenance_after` purpose (which needs the shop's two parts, `purposes` and
 * `batteryBeforeAfter`, Q31), and the server's own refusals (`PAIR_PART_NOT_ENABLED`, `PAIR_VALIDITY_SETTING_ABSENT`, ...) are what the screen shows as its refusal texts. The server's half (S4 and S5 of the same order,
 * neuralcheck-backend) is not deployed yet: an APK carrying this guard must not reach a shop before it is.
 *
 * It must be turned FALSE again if a slot of [PairTextSlot] is added without an approved text: `PairListGuardTest` fails on purpose the day this is true while a text is pending (rule 23: replaced in the same change).
 */
internal const val PAIR_LIST_TEXTS_APPROVED: Boolean = true

/** How a text of the pair list stands against the user's approvals. */
internal enum class PairTextApproval {
    /** Proposed, not approved: `TEXTS-FOR-APPROVAL-MAINTENANCE-PAIRS-2026-10-22.md` (or, for a slot added later, this builder's own proposal). */
    PENDING,

    /** Approved by Hasan on 2026-10-22 (`TEXTS-FOR-APPROVAL-MAINTENANCE-PAIRS-2026-10-22.md`, «ص1-ص21 معتمدة كما كتبتَها») and written, verbatim, to the resources in both languages. */
    APPROVED,

    /** An existing, already approved text the proposal reuses (ص12: `DECISIONS-FOR-HASAN-2026-10-18.md` A5b-3; ص13: the app's `btn_confirm`). */
    EXISTING_APPROVED
}

/**
 * Every text the surface can show - one slot per place, so the list of what needs the user's approval is the code's, not a memory. [code] is the proposal's row (ص9 ...), [approval] its standing - ten approved by
 * Hasan on 2026-10-22 and two that exist (ص12, ص13) - and [resource] the `R.string` that holds its text in `values` and `values-tr`: one source per text (rule 24), read by [pairLabelsOf]. [NO_DEVICE_NAME] (ص20) was
 * this builder's proposal for a candidate whose phone has neither a marketing name nor a model string, given its row by the coordinator and approved with the others.
 */
internal enum class PairTextSlot(val code: String, val approval: PairTextApproval, @StringRes val resource: Int) {
    /** ص9 «اختر فحص «قبل الصيانة» لهذا الهاتف». */
    PICK_PROMPT("ص9", PairTextApproval.APPROVED, R.string.pair_pick_prompt),

    /** ص10 «%1$s · %2$s · الساعة %3$s» (the name, the date, the hour). */
    ROW("ص10", PairTextApproval.APPROVED, R.string.pair_row_fmt),

    /** ص11 «لا فحوص «قبل الصيانة» مفتوحة من هذا النوع في آخر %1$d ساعة». */
    EMPTY("ص11", PairTextApproval.APPROVED, R.string.pair_empty_fmt),

    /** ص12 «تم الربط — فحص بتاريخ %1$s» (existing). */
    LINKED("ص12", PairTextApproval.EXISTING_APPROVED, R.string.qr_linked_success_fmt),

    /** ص13 «تأكيد» (existing: `btn_confirm`). */
    CONFIRM("ص13", PairTextApproval.EXISTING_APPROVED, R.string.btn_confirm),

    /** ص14 «هذا الفحص غير متاح للربط». */
    NOT_AVAILABLE("ص14", PairTextApproval.APPROVED, R.string.pair_failure_not_available),

    /** ص15 «هذا الفحص مربوط من قبل». */
    ALREADY_PAIRED("ص15", PairTextApproval.APPROVED, R.string.pair_failure_already_paired),

    /** ص16 «انتهت مدّة هذا الفحص ولا يمكن ربطه». */
    EXPIRED("ص16", PairTextApproval.APPROVED, R.string.pair_failure_expired),

    /** ص17 «نوع العطل في هذا الفحص يختلف عمّا اخترتَ». */
    WRONG_DEFECT_TYPE("ص17", PairTextApproval.APPROVED, R.string.pair_failure_wrong_defect_type),

    /** ص18 «تعذّر الربط. حاول مرة أخرى». */
    GENERAL_FAILURE("ص18", PairTextApproval.APPROVED, R.string.pair_failure_general),

    /** ص19 «ميزة الصيانة غير مفعَّلة لحسابك». */
    NOT_ENABLED("ص19", PairTextApproval.APPROVED, R.string.pair_failure_not_enabled),

    /** ص20 «جهاز بلا اسم تجاري» (the text this builder proposed; the coordinator gave it its row): the name of a row whose phone has no marketing name and no model string. */
    NO_DEVICE_NAME("ص20", PairTextApproval.APPROVED, R.string.pair_no_device_name)
}

/** One text the surface asks its labels for: the slot says which, the value says with what. */
internal sealed interface PairText {
    val slot: PairTextSlot

    data object PickPrompt : PairText { override val slot get() = PairTextSlot.PICK_PROMPT }

    /** [name], [date] and [time] are already written for the screen's language: the label only puts them in its sentence. */
    data class Row(val name: String, val date: String, val time: String) : PairText { override val slot get() = PairTextSlot.ROW }

    data class Empty(val validityHours: Int) : PairText { override val slot get() = PairTextSlot.EMPTY }

    data class Linked(val date: String) : PairText { override val slot get() = PairTextSlot.LINKED }

    data object Confirm : PairText { override val slot get() = PairTextSlot.CONFIRM }

    data object NoDeviceName : PairText { override val slot get() = PairTextSlot.NO_DEVICE_NAME }

    /** The failure text of [kind]: ص14 to ص19, chosen by `failureKindOf`. */
    data class Failure(val kind: PairFailureKind) : PairText {
        override val slot
            get() = when (kind) {
                PairFailureKind.NOT_AVAILABLE -> PairTextSlot.NOT_AVAILABLE
                PairFailureKind.ALREADY_PAIRED -> PairTextSlot.ALREADY_PAIRED
                PairFailureKind.EXPIRED -> PairTextSlot.EXPIRED
                PairFailureKind.WRONG_DEFECT_TYPE -> PairTextSlot.WRONG_DEFECT_TYPE
                PairFailureKind.GENERAL -> PairTextSlot.GENERAL_FAILURE
                PairFailureKind.NOT_ENABLED -> PairTextSlot.NOT_ENABLED
            }
    }
}

internal object PairTexts {
    /** Every slot of the surface. */
    val all: List<PairTextSlot> = PairTextSlot.values().toList()

    /** The slots that are not approved: the guard may be true only when this is empty. */
    val pending: List<PairTextSlot> get() = all.filter { it.approval == PairTextApproval.PENDING }
}

/** The labels of the surface: one text per [PairText]. */
internal typealias PairLabels = (PairText) -> String

/**
 * The approved labels, read from the resources in the language the phone is showing - or null while [PAIR_LIST_TEXTS_APPROVED] is false (two locks, as before: the screen draws the list only for a non-null result AND
 * the guard). Reading [LocalConfiguration] makes a change of language recompose the list with the other language's texts.
 */
@Composable
internal fun pairLabels(): PairLabels? {
    if (!PAIR_LIST_TEXTS_APPROVED) return null
    LocalConfiguration.current
    return pairLabelsOf(LocalContext.current.resources)
}

/**
 * Every text of the surface from [resources]: one `R.string` per [PairTextSlot] ([PairTextSlot.resource] - no text is written in Kotlin), the arguments put in its sentence, and digits written Latin as the app does
 * for every formatted text (`NumberFormat.toLatin`, as for ص12 in `QRScannerScreen`). The `when` has no `else`: a text added without its case does not compile.
 */
internal fun pairLabelsOf(resources: Resources): PairLabels = { text ->
    when (text) {
        PairText.PickPrompt, PairText.Confirm, PairText.NoDeviceName -> resources.getString(text.slot.resource)
        is PairText.Failure -> resources.getString(text.slot.resource)
        is PairText.Row -> NumberFormat.toLatin(resources.getString(text.slot.resource, text.name, text.date, text.time))
        is PairText.Empty -> NumberFormat.toLatin(resources.getString(text.slot.resource, text.validityHours))
        is PairText.Linked -> NumberFormat.toLatin(resources.getString(text.slot.resource, text.date))
    }
}

internal const val PAIR_LIST_TAG = "pairList"
internal fun pairTag(vararg parts: String): String = "pair_" + parts.joinToString("_")

/** What the list asks of its owner: one callback per tap. The candidate is named by its handle, which the screen never shows. */
internal class PairListActions(val onSelect: (String) -> Unit, val onConfirm: () -> Unit)

/** What a row needs to be written for this phone: the language of the name, the zone and the locale of the date and the hour. Injected so a test fixes them; [device] is the phone's own. */
internal data class PairRowEnvironment(val language: ConsentLocaleRead, val zone: ZoneId, val locale: Locale) {
    companion object {
        fun device(): PairRowEnvironment = PairRowEnvironment(ConsentLocaleRead.of(Locale.getDefault().language), ZoneId.systemDefault(), Locale.getDefault())
    }
}

/**
 * The pair list of the «after» session (order of 2026-10-22), drawn from [ui]: loading is a bar with NO text; a failed load shows its failure text; the empty state shows ص11 with the answer's `validityHours`; the
 * candidates are rows «name · date · hour» (the name is the marketing name in the screen's language, or the phone's own model string, or [PairText.NoDeviceName] - never one made up) with ص9 above them; a tap
 * on a row selects it; «تأكيد» (ص13) is enabled only with a selection and no call out; a refused confirmation shows its failure text; a confirmed one shows ص12 with the date of the chosen scan, and the rows stay so the
 * technician can choose again before the scan completes. The candidate's `beforeScanId` is NEVER drawn (not as text, not as a tag, not as a description): rows are tagged by their position, and no
 * hardware identifier exists in the answer. Every text comes from [labels]; this file holds none.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PairListSection(
    ui: PairListUi,
    labels: PairLabels,
    actions: PairListActions,
    environment: PairRowEnvironment = PairRowEnvironment.device(),
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth().testTag(PAIR_LIST_TAG), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (ui) {
            PairListUi.NotShown -> Unit
            PairListUi.Loading -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth().testTag(pairTag("loading")))
            is PairListUi.LoadFailed -> FailureText(ui.failure, labels, pairTag("failure"))
            is PairListUi.Empty -> Text(labels(PairText.Empty(ui.validityHours)), color = TextPrimary, fontSize = 14.sp, modifier = Modifier.testTag(pairTag("empty")))
            is PairListUi.Choosing -> {
                Text(labels(PairText.PickPrompt), color = TextSecondary, fontSize = 13.sp, modifier = Modifier.testTag(pairTag("prompt")))
                CandidateRows(ui.listing.candidates, selected = ui.selected, enabled = ui.confirm !is PairConfirmUi.InFlight, labels, actions, environment)
                if (ui.confirm is PairConfirmUi.InFlight) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().testTag(pairTag("confirmProgress")))
                (ui.confirm as? PairConfirmUi.Failed)?.let { FailureText(it.failure, labels, pairTag("confirmFailure")) }
                Button(
                    onClick = actions.onConfirm,
                    enabled = ui.selected != null && ui.confirm !is PairConfirmUi.InFlight,
                    modifier = Modifier.fillMaxWidth().testTag(pairTag("confirm"))
                ) { Text(labels(PairText.Confirm)) }
            }
            is PairListUi.Linked -> {
                val linkedDate = candidateRow(ui.linked, environment.language, environment.zone, environment.locale).date
                Text(labels(PairText.Linked(linkedDate)), color = NeuralGreen, fontSize = 14.sp, modifier = Modifier.testTag(pairTag("linked")))
                CandidateRows(ui.listing.candidates, selected = ui.linked.beforeScanId, enabled = true, labels, actions, environment)
            }
        }
    }
}

@Composable
private fun FailureText(failure: PairFailure, labels: PairLabels, tag: String) {
    Text(labels(PairText.Failure(failure.kind)), color = TextPrimary, fontSize = 13.sp, modifier = Modifier.testTag(tag))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CandidateRows(
    candidates: List<PairCandidate>,
    selected: String?,
    enabled: Boolean,
    labels: PairLabels,
    actions: PairListActions,
    environment: PairRowEnvironment
) {
    for ((index, candidate) in candidates.withIndex()) {
        val row = candidateRow(candidate, environment.language, environment.zone, environment.locale)
        val name = when (val n = row.name) {
            is CandidateName.Marketing -> n.text
            is CandidateName.Model -> n.text
            is CandidateName.NoName -> labels(PairText.NoDeviceName)
        }
        FilterChip(
            selected = candidate.beforeScanId == selected,
            onClick = { actions.onSelect(candidate.beforeScanId) },
            enabled = enabled,
            label = { Text(labels(PairText.Row(name, row.date, row.time)), fontSize = 13.sp, maxLines = 3, modifier = Modifier.fillMaxWidth()) },
            modifier = Modifier.fillMaxWidth().testTag(pairTag("row", index.toString())),
            colors = FilterChipDefaults.filterChipColors(
                containerColor = CardDark,
                labelColor = TextPrimary,
                selectedContainerColor = NeuralGreen.copy(alpha = 0.22f),
                selectedLabelColor = NeuralGreen
            )
        )
    }
}
