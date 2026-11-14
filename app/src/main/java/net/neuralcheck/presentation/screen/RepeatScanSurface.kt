package net.neuralcheck.presentation.screen

import android.content.Context
import android.content.res.Resources
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import net.neuralcheck.domain.repeat.CoolingDecision
import net.neuralcheck.domain.repeat.RepeatRefusal
import net.neuralcheck.domain.repeat.TempRead
import net.neuralcheck.presentation.theme.TextPrimary
import net.neuralcheck.presentation.theme.TextSecondary
import net.neuralcheck.presentation.util.BatteryTemperature
import net.neuralcheck.presentation.viewmodel.RepeatStartUi
import net.neuralcheck.presentation.viewmodel.ScannedRepeatUi

/**
 * THE guard of the repeat scan's two screens (A-P6-07; PLAN-06 section 7, rows ف١٣ and ف١٤). Their texts were PROPOSED in the plan («كل نصّ أدناه مقترح للاعتماد (القرار ٩٠)», PLAN-06:926; the refusal texts and the
 * failure text were proposed by the server's builder and by the app's), and Hasan delegated them on 2026-10-22 («اعتمد بتقديرك الأنسب كل القرارات والنصوص المعلّقة»): each was reviewed against the code on 2026-10-23
 * (`scratchpad/app-delegated/texts-review.md`) - two were reworded because the code does not do what they said ([RepeatText.NotCooledWarning], [RepeatText.NoticeBody]) - and all sixteen are written, verbatim, to
 * `values/strings.xml` and, for the scanned phone's fourteen (the customer sees that phone), to `values-tr/strings.xml` (the Turkish is proposed and needs a speaker). [RepeatText.resource] maps each text to its one
 * resource and [repeatLabelsOf] reads it.
 *
 * The screens draw only while this is true and [repeatLabels] has labels, and `FirebaseAnalysisService` lets the two callables (`getRepeatScanOffer`, `startRepeatScan`) leave the phone only while this is true (its
 * exit gate `repeatCallsApproved` READS THIS GUARD, rule 35; the server's half is not deployed). It may be true only when every text of [RepeatText] is approved: [RepeatTexts.pending] is the list of those that
 * are not, and `RepeatScanGuardTest` fails if this is true while it is not empty.
 *
 * It is TRUE since the change that followed (2026-10-23, its own commit, after the form's). What flipping it did: the owner's phone draws the notice «اختير هذا الهاتف لفحص مكرّر بارد…» once the session is completed
 * and the server chose the scanned phone (RepeatNoticeUi.Notice; a scan the server did not choose shows nothing); the scanned phone draws the cooling screen after its report when the server offered a repeat, in the
 * phone's language, and reads its own battery temperature every two seconds while it is up; «ابدأ رغم ذلك» sends `startedBeforeCooling: true`; and `getRepeatScanOffer` and `startRepeatScan` can leave the phone
 * (the report's view model also reads the scan's session record before the analysis, `ViewModels.kt` `sessionAtScan`, for either gate). The server's half is NOT deployed on production yet: until it is, the offer
 * call fails (logged, nothing shown - the cooling screen never opens) - an APK carrying this guard must not reach a shop before the server's half does.
 */
internal const val REPEAT_TEXTS_APPROVED: Boolean = true

/**
 * Every text the two surfaces show - one entry per place, so the list of what needs the user's approval is the code's, not a memory. [approval] reuses the form's scale; all sixteen are approved by delegation.
 */
internal sealed interface RepeatText {
    val approval: TextApproval

    // ---- row ف١٤: the technician's phone (PLAN-06:956; Arabic only) ----
    /** A5j-1 «اختير هذا الهاتف لفحص مكرّر بارد. أعده حين يبرد.» - the plan's «يقيس ثبات النتائج» was dropped: this app measures nothing between the two scans. */
    data object NoticeBody : RepeatText { override val approval = TextApproval.APPROVED_BY_DELEGATION }

    /** A5j-2 «ليس الآن» */
    data object NotNow : RepeatText { override val approval = TextApproval.APPROVED_BY_DELEGATION }

    // ---- row ف١٣: the scanned phone (PLAN-06:955; Arabic and Turkish) ----
    /** A5i-1 «فحص مكرّر: انتظر حتى يبرد الهاتف» */
    data object WaitTitle : RepeatText { override val approval = TextApproval.APPROVED_BY_DELEGATION }

    /** A5i-2 «جاهز — ابدأ الفحص المكرّر» */
    data object Ready : RepeatText { override val approval = TextApproval.APPROVED_BY_DELEGATION }

    /** A5i-3 «لم يبرد الهاتف بعد — يُرسَل الفحص على أنه تكرار ساخن» - the plan's «يُسجَّل» was reworded to what the app does: it sends `startedBeforeCooling: true`. */
    data object NotCooledWarning : RepeatText { override val approval = TextApproval.APPROVED_BY_DELEGATION }

    /** A5i-4 «ابدأ رغم ذلك» */
    data object StartAnyway : RepeatText { override val approval = TextApproval.APPROVED_BY_DELEGATION }

    /** A5i-5 «انتظر» */
    data object Wait : RepeatText { override val approval = TextApproval.APPROVED_BY_DELEGATION }

    /** A5i-6 «تعذّر قراءة حرارة البطارية» */
    data object TemperatureUnreadable : RepeatText { override val approval = TextApproval.APPROVED_BY_DELEGATION }

    // ---- not in the plan: proposed by the server's builder (its report, fd730ac) and by this row's builder; approved by delegation (A6) ----
    /** A6-1 to A6-7: the seven refusals of `startRepeatScan` that a technician can meet and the server's builder proposed a text for (see [RepeatTexts.refusalsWithText]). */
    data class StartRefused(val refusal: RepeatRefusal) : RepeatText { override val approval = TextApproval.APPROVED_BY_DELEGATION }

    /** A6-8: any other failure of the start (a disconnect, an unreadable answer, a refusal that has no text of its own). */
    data object StartNotSent : RepeatText { override val approval = TextApproval.APPROVED_BY_DELEGATION }
}

internal object RepeatTexts {
    /**
     * The seven refusals the server's builder wrote a proposed text for (`backend-engineer-3-rows-a-fd730ac-2026-10-21.md`, section «المعلّق»): the others are input errors of a wrong client and are not shown
     * (`REPEAT_PAIR_NOT_FOUND` uses the server's own existing text, «جلسة الفحص غير صالحة», which the app never shows from the server).
     */
    val refusalsWithText: List<RepeatRefusal> = listOf(
        RepeatRefusal.PARENT_JOINER_UNKNOWN, RepeatRefusal.ALREADY_STARTED, RepeatRefusal.NOT_SELECTED, RepeatRefusal.SESSION_NOT_COMPLETED,
        RepeatRefusal.PARENT_OWNER_UNKNOWN, RepeatRefusal.RECORD_ABSENT, RepeatRefusal.STATE_UNKNOWN
    )

    /** Every text the surfaces can show. */
    val all: List<RepeatText> = buildList {
        add(RepeatText.NoticeBody); add(RepeatText.NotNow)
        add(RepeatText.WaitTitle); add(RepeatText.Ready); add(RepeatText.NotCooledWarning); add(RepeatText.StartAnyway); add(RepeatText.Wait); add(RepeatText.TemperatureUnreadable)
        for (r in refusalsWithText) add(RepeatText.StartRefused(r))
        add(RepeatText.StartNotSent)
    }

    /** The texts that are not approved: the guard may be true only when this is empty. */
    val pending: List<RepeatText> get() = all.filter { it.approval == TextApproval.PENDING }

    /** The text a failed start shows: the refusal's own when it has one, else the general one. */
    fun forFailure(failure: RepeatStartUi.Failed): RepeatText =
        failure.refusal?.takeIf { it in refusalsWithText }?.let { RepeatText.StartRefused(it) } ?: RepeatText.StartNotSent
}

/** The labels of the surfaces: one text per [RepeatText]. */
internal typealias RepeatLabels = (RepeatText) -> String

/**
 * The approved labels, read from the resources in the language the phone is showing - or null while [REPEAT_TEXTS_APPROVED] is false (two locks, as before: the screens draw only for a non-null result AND the
 * guard). Reading [LocalConfiguration] makes a change of language recompose with the other language's texts (the technician's notice has no Turkish: Android falls back to the Arabic of `values/`, on purpose).
 */
@Composable
internal fun repeatLabels(): RepeatLabels? {
    if (!REPEAT_TEXTS_APPROVED) return null
    LocalConfiguration.current
    return repeatLabelsOf(LocalContext.current.resources)
}

/** Every text of the repeat scan from [resources]: the one `R.string` of [RepeatText.resource], read as it is (no text of the repeat scan takes an argument). */
internal fun repeatLabelsOf(resources: Resources): RepeatLabels = { text -> resources.getString(text.resource()) }

internal const val REPEAT_TAG = "repeatScan"
internal fun repeatTag(vararg parts: String): String = "repeat_" + parts.joinToString("_")

/** The technician's notice (ف١٤): the one sentence and «ليس الآن». Pressing it hides the notice on this screen only - nothing is sent (the plan names no call for it: A4's «معلّق»). */
@Composable
internal fun RepeatNoticeSection(labels: RepeatLabels, onNotNow: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth().testTag(repeatTag("notice")), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(labels(RepeatText.NoticeBody), color = TextPrimary, fontSize = 14.sp)
        TextButton(onClick = onNotNow, modifier = Modifier.testTag(repeatTag("notNow"))) { Text(labels(RepeatText.NotNow)) }
    }
}

/** What the cooling screen asks of its owner: one callback per press. */
internal class CoolingActions(
    val onTemperature: (TempRead) -> Unit,
    val startReady: () -> Unit,
    val startAnyway: () -> Unit,
    val wait: () -> Unit
)

/** How often the phone's own battery temperature is read while the cooling screen is up. The broadcast itself changes slowly; two seconds keeps the bar and the button current. */
internal const val COOLING_POLL_MS = 2_000L

/**
 * The scanned phone's cooling screen (ف١٣), drawn from [ui]: the title and a bar toward the target with NO number (PLAN-06:635); «جاهز — ابدأ الفحص المكرّر» only when the phone is within the degree; before that the
 * warning with «ابدأ رغم ذلك» and «انتظر» (until «انتظر» is pressed); a failed read shows «تعذّر قراءة حرارة البطارية» and NO start button (PLAN-06:670). No temperature is ever written on this screen. Every
 * control is disabled while the start call is out, and a failed start shows its text under the controls. While it is composed it reads the phone's own temperature every [COOLING_POLL_MS] and reports it.
 */
@Composable
internal fun CoolingWaitSection(ui: ScannedRepeatUi.Cooling, labels: RepeatLabels, actions: CoolingActions, readTemperature: (Context) -> TempRead = BatteryTemperature::read, pollMs: Long = COOLING_POLL_MS, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val report by rememberUpdatedState(actions.onTemperature)
    LaunchedEffect(Unit) {
        while (true) {
            report(readTemperature(context))
            delay(pollMs)
        }
    }
    val enabled = ui.start !is RepeatStartUi.InFlight
    Column(modifier = modifier.fillMaxWidth().testTag(repeatTag("cooling")), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(labels(RepeatText.WaitTitle), color = TextPrimary, fontSize = 16.sp)
        when (val decision = ui.decision) {
            // no reading yet: a bar that does not move toward anything
            null -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth().testTag(repeatTag("bar", "unknown")))
            CoolingDecision.Ready -> Button(onClick = actions.startReady, enabled = enabled, modifier = Modifier.fillMaxWidth().testTag(repeatTag("ready"))) { Text(labels(RepeatText.Ready)) }
            is CoolingDecision.NotCooled -> {
                LinearProgressIndicator(progress = { decision.progress }, modifier = Modifier.fillMaxWidth().testTag(repeatTag("bar", "progress")))
                if (!ui.waitChosen) {
                    Text(labels(RepeatText.NotCooledWarning), color = TextSecondary, fontSize = 13.sp, modifier = Modifier.testTag(repeatTag("warning")))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = actions.startAnyway, enabled = enabled, modifier = Modifier.testTag(repeatTag("anyway"))) { Text(labels(RepeatText.StartAnyway)) }
                        TextButton(onClick = actions.wait, enabled = enabled, modifier = Modifier.testTag(repeatTag("wait"))) { Text(labels(RepeatText.Wait)) }
                    }
                }
            }
            is CoolingDecision.TemperatureUnreadable -> Text(labels(RepeatText.TemperatureUnreadable), color = TextPrimary, fontSize = 14.sp, modifier = Modifier.testTag(repeatTag("unreadable")))
        }
        (ui.start as? RepeatStartUi.Failed)?.let { failure ->
            Text(labels(RepeatTexts.forFailure(failure)), color = TextPrimary, fontSize = 13.sp, modifier = Modifier.testTag(repeatTag("failure")))
        }
    }
}
