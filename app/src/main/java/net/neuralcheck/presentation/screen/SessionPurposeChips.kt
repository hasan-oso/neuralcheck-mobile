package net.neuralcheck.presentation.screen

import android.content.res.Resources
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.neuralcheck.R
import net.neuralcheck.data.remote.DefectType
import net.neuralcheck.data.remote.ScanPurposeChoice
import net.neuralcheck.presentation.theme.CardDark
import net.neuralcheck.presentation.theme.NeuralGreen
import net.neuralcheck.presentation.theme.TextMuted
import net.neuralcheck.presentation.theme.TextPrimary
import net.neuralcheck.presentation.theme.TextSecondary
import net.neuralcheck.presentation.viewmodel.PurposeFailure
import net.neuralcheck.presentation.viewmodel.PurposeSelection
import net.neuralcheck.presentation.viewmodel.PurposeUi

/**
 * THE guard of the purpose chips (A-P6-03, screen ف١ of `plan/PLAN-06-DATA-COLLECTION.md` section 7; and, since the order of 2026-10-22, the fault type and the refusal of a purpose the shop's parts do not allow:
 * rows ص1 to ص8 and ص21 of `TEXTS-FOR-APPROVAL-MAINTENANCE-PAIRS-2026-10-22.md`). Their texts were APPROVED by Hasan on 2026-10-22 («النصوص ص1-ص21 معتمدة كما كتبتَها، بالعربية والتركية») and are written, verbatim, to
 * `values/strings.xml` and `values-tr/strings.xml` (the Turkish is approved before a speaker review: TR-TEXTS-NEED-SPEAKER-REVIEW-2026-10-22.md). So this is TRUE: [purposeChipLabels] reads them from the resources and the
 * owner's screen draws the chips for a shop the create's answer offers them to - «عادي» for `purposes` alone, and the two maintenance chips with the fault-type list only for `purposes` AND `batteryBeforeAfter`
 * ([net.neuralcheck.data.remote.ProtocolPartsRead.maintenanceOffered], Q31); a shop with neither gets no chips and sends no call. What is drawn is the chips' LOGIC (`SessionPurposeUi.kt`, the view model's `onPurposeChip` /
 * `onDefectType` / `selectPurpose`) through the composable below.
 *
 * It must be turned FALSE again, and the labels with it, if a text of [PurposeChipLabels] stops being approved or a slot is added without one: `SessionPurposeGuardTest` and `MaintenancePairApprovedTextsTest` pin every
 * text (Arabic and Turkish) to the approval and fail on purpose when a resource or its translation is missing or changed (rule 23: replaced in the same change as the text).
 */
internal const val PURPOSE_CHIPS_TEXTS_APPROVED: Boolean = true

/**
 * The fifteen texts of the chips' surface (ص1 to ص8 of the proposal: the title, the three chips, the locked note, the fault type's title and hint, and the five types; ص21, [partNotEnabled]; and ص22-ص23 of 2026-10-28, the
 * title and hint of the fault list under «بعد الصيانة»). No default value: a
 * missing text must be a compile error, not a silent blank - and [of] has no `else`, so a purpose or a fault type added without a text does not compile. [purposeChipLabels] reads them from the resources
 * ([purposeChipLabelsOf]) and is `null` while the guard is false.
 */
internal data class PurposeChipLabels(
    val title: String,
    val normal: String,
    val maintenanceBefore: String,
    val maintenanceAfter: String,
    /** Shown under the chips once they are locked (the customer's phone joined). */
    val lockedNote: String,
    /** ص7: above the list of fault types. */
    val typeTitle: String,
    /** ص8: under it («اختر العطل الذي ستصلحه؛ إن لم تجده فافحص «عادي»»): the wording of a repair STILL TO BE DONE, so it is the list of «قبل الصيانة» only. */
    val typeHint: String,
    /** ص22 (2026-10-28, the manager's judgement by the delegation of 2026-10-23; the Turkish needs a speaker): above the list of fault types under «بعد الصيانة» - the repair is done. */
    val typeTitleAfter: String,
    /** ص23 (same standing as ص22): under it, the wording of «بعد الصيانة» (the repair done, then the «before» scan of this phone to pick). */
    val typeHintAfter: String,
    /** ص6-1 to ص6-5: one text per [DefectType]. */
    val battery: String,
    val chargingPort: String,
    val thermal: String,
    val motherboard: String,
    val software: String,
    /** ص21: shown under the chips when the server refused a purpose the shop's parts do not allow (`PURPOSE_PART_NOT_ENABLED`; «الغرض غير مفعَّل لحسابك»). */
    val partNotEnabled: String
) {
    fun of(choice: ScanPurposeChoice): String = when (choice) {
        ScanPurposeChoice.NORMAL -> normal
        ScanPurposeChoice.MAINTENANCE_BEFORE -> maintenanceBefore
        ScanPurposeChoice.MAINTENANCE_AFTER -> maintenanceAfter
    }

    /** The title of the fault list of [choice] (the chip whose list is on the screen): «بعد الصيانة» has its own, «قبل الصيانة» keeps ص7. */
    fun typeTitleFor(choice: ScanPurposeChoice): String = if (choice == ScanPurposeChoice.MAINTENANCE_AFTER) typeTitleAfter else typeTitle

    /** The hint of the fault list of [choice]: «بعد الصيانة» has its own, «قبل الصيانة» keeps ص8. */
    fun typeHintFor(choice: ScanPurposeChoice): String = if (choice == ScanPurposeChoice.MAINTENANCE_AFTER) typeHintAfter else typeHint

    fun of(type: DefectType): String = when (type) {
        DefectType.BATTERY -> battery
        DefectType.CHARGING_PORT -> chargingPort
        DefectType.THERMAL -> thermal
        DefectType.MOTHERBOARD -> motherboard
        DefectType.SOFTWARE -> software
    }
}

/**
 * The approved labels, read from the resources in the language the phone is showing - or null while [PURPOSE_CHIPS_TEXTS_APPROVED] is false (two locks, as before: the screen draws the chips only for a non-null result
 * AND the guard). Reading [LocalConfiguration] makes a change of language recompose the chips with the other language's texts.
 */
@Composable
internal fun purposeChipLabels(): PurposeChipLabels? {
    if (!PURPOSE_CHIPS_TEXTS_APPROVED) return null
    LocalConfiguration.current
    return purposeChipLabelsOf(LocalContext.current.resources)
}

/**
 * Every text of [PurposeChipLabels], from [resources] (one `R.string` each - no text is written in Kotlin; a missing resource does not compile). The Turkish texts are the `values-tr` ones of the same names: the
 * test reads this function under both locales and compares each text with the approval's table (`MaintenancePairApprovedTextsTest`).
 */
internal fun purposeChipLabelsOf(resources: Resources): PurposeChipLabels = PurposeChipLabels(
    title = resources.getString(R.string.purpose_chips_title),
    normal = resources.getString(R.string.purpose_chip_normal),
    maintenanceBefore = resources.getString(R.string.purpose_chip_maintenance_before),
    maintenanceAfter = resources.getString(R.string.purpose_chip_maintenance_after),
    lockedNote = resources.getString(R.string.purpose_chips_locked_note),
    typeTitle = resources.getString(R.string.defect_type_title),
    typeHint = resources.getString(R.string.defect_type_hint),
    typeTitleAfter = resources.getString(R.string.defect_type_title_after),
    typeHintAfter = resources.getString(R.string.defect_type_hint_after),
    battery = resources.getString(R.string.defect_type_battery),
    chargingPort = resources.getString(R.string.defect_type_charging_port),
    thermal = resources.getString(R.string.defect_type_thermal),
    motherboard = resources.getString(R.string.defect_type_motherboard),
    software = resources.getString(R.string.defect_type_software),
    partNotEnabled = resources.getString(R.string.purpose_part_not_enabled)
)

internal const val PURPOSE_CHIPS_TAG = "purposeChips"
internal fun purposeChipTag(choice: ScanPurposeChoice): String = "purposeChip_${choice.wire}"
internal const val PURPOSE_LOCKED_NOTE_TAG = "purposeChipsLockedNote"

/** The text of ص21 under the chips (only after a `PURPOSE_PART_NOT_ENABLED` refusal). */
internal const val PURPOSE_PART_NOT_ENABLED_TAG = "purposeChipsPartNotEnabled"

/** The list of the five fault types under the chips, and one tag per type. */
internal const val PURPOSE_TYPES_TAG = "purposeTypes"

/** The spinner on the chip or type whose call is out (order of 2026-10-28): the tap is shown at once, and this says the server has not answered yet. */
internal const val PURPOSE_PENDING_TAG = "purposePending"
internal fun purposeTypeTag(type: DefectType): String = "purposeType_${type.wire}"

/** The mark of a call out (order of 2026-10-28): a small spinner in the chip's leading slot; nothing else about the chip changes. */
private val pendingMark: @Composable () -> Unit = {
    CircularProgressIndicator(modifier = Modifier.size(16.dp).testTag(PURPOSE_PENDING_TAG), strokeWidth = 2.dp, color = NeuralGreen)
}

/**
 * The chips under the QR (ف١): one per [PurposeUi.Offered.choices] - «عادي» always, and the two maintenance ones only for a shop with both parts (Q31) - the chosen one highlighted at once ([PurposeUi.Offered.shownChoice]: the tapped chip
 * while its call is out or its type is awaited, else the confirmed one [PurposeUi.Offered.highlighted]), all disabled while [locked] or while a call is out
 * ([PurposeUi.Offered.inFlight]). A tap is reported for EVERY chip, the highlighted one included - the view model decides what a tap does: it sends «normal» over «normal» on purpose
 * (rule 22: a chosen «normal» is not the default one), and a maintenance chip sends nothing, it opens the list of the five fault types under the chips ([PurposeUi.Offered.typeListFor]) - one chip per
 * [DefectType], the accepted type highlighted, disabled like the chips, each reporting its own tap through [onSelectType]. The locked note shows only while [locked]. The labels come in as a value: this
 * file holds no text.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SessionPurposeChips(
    offered: PurposeUi.Offered,
    locked: Boolean,
    labels: PurposeChipLabels,
    onSelect: (ScanPurposeChoice) -> Unit,
    onSelectType: (DefectType) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth().testTag(PURPOSE_CHIPS_TAG), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(labels.title, color = TextSecondary, fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (choice in offered.choices()) {
                FilterChip(
                    selected = offered.shownChoice(locked) == choice,
                    onClick = { onSelect(choice) },
                    enabled = !locked && offered.inFlight == null,
                    leadingIcon = if (offered.inFlight is PurposeSelection.Normal && choice == ScanPurposeChoice.NORMAL) pendingMark else null,
                    label = { Text(labels.of(choice), fontSize = 13.sp, maxLines = 2, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
                    modifier = Modifier.weight(1f).testTag(purposeChipTag(choice)),
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = CardDark,
                        labelColor = TextPrimary,
                        selectedContainerColor = NeuralGreen.copy(alpha = 0.22f),
                        selectedLabelColor = NeuralGreen
                    )
                )
            }
        }
        val typeListChoice = offered.typeListFor()
        if (typeListChoice != null) {
            Spacer(Modifier.height(10.dp))
            Column(modifier = Modifier.fillMaxWidth().testTag(PURPOSE_TYPES_TAG), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(labels.typeTitleFor(typeListChoice), color = TextSecondary, fontSize = 13.sp)
                Text(labels.typeHintFor(typeListChoice), color = TextMuted, fontSize = 11.sp)
                for (type in DefectType.values()) {
                    FilterChip(
                        selected = offered.shownType(locked) == type,
                        onClick = { onSelectType(type) },
                        enabled = !locked && offered.inFlight == null,
                        leadingIcon = if ((offered.inFlight as? PurposeSelection.Maintenance)?.defectType == type) pendingMark else null,
                        label = { Text(labels.of(type), fontSize = 13.sp, maxLines = 3, modifier = Modifier.fillMaxWidth()) },
                        modifier = Modifier.fillMaxWidth().testTag(purposeTypeTag(type)),
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = CardDark,
                            labelColor = TextPrimary,
                            selectedContainerColor = NeuralGreen.copy(alpha = 0.22f),
                            selectedLabelColor = NeuralGreen
                        )
                    )
                }
            }
        }
        if (offered.failure is PurposeFailure.PartNotEnabled) {
            Spacer(Modifier.height(6.dp))
            Text(labels.partNotEnabled, color = TextPrimary, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.testTag(PURPOSE_PART_NOT_ENABLED_TAG))
        }
        if (locked) {
            Spacer(Modifier.height(6.dp))
            Text(labels.lockedNote, color = TextMuted, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.testTag(PURPOSE_LOCKED_NOTE_TAG))
        }
    }
}
