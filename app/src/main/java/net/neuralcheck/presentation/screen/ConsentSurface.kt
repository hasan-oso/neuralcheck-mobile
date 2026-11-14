package net.neuralcheck.presentation.screen

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.neuralcheck.R
import net.neuralcheck.domain.consent.ConsentLocale
import net.neuralcheck.presentation.theme.TextPrimary
import net.neuralcheck.presentation.viewmodel.ConsentFailure
import net.neuralcheck.presentation.viewmodel.ConsentUi
import java.util.Locale

/**
 * THE guard of the consent screen (A-P6-12; PLAN-06 section 11, screen `ف١٠` `:952`). The screen and its call are built and tested; NOTHING is drawn and NOTHING is sent while this is false: the two places a join
 * ends route through [net.neuralcheck.presentation.navigation.afterJoinRoute], which says «consent» only when this is true AND the join's answer said the shop has the consent part (ق15: `consentEnabled: true`;
 * the pre-scan screen otherwise, as before this row), and `FirebaseAnalysisService` refuses `recordScanConsent` at the exit while it is false (rule 35; its exit gate READS this guard). It may be true only when every text of
 * [ConsentText] is approved and written in BOTH languages: [ConsentTexts.pending] is the list of those that are not, and `ConsentGuardTest` fails if this is true while it is not empty.
 *
 * The texts are VERSION 2 of the consent text ([net.neuralcheck.domain.consent.ConsentTextVersion]), approved by delegation (Hasan, 2026-10-22: «اعتمد بتقديرك الأنسب كل القرارات والنصوص المعلّقة») and reviewed
 * against the code on 2026-10-23 (`scratchpad/app-delegated/texts-review.md`, section «الموافقة»): the Arabic of the title and the two buttons is the user's approved version-1 wording, unchanged; the measures
 * paragraph and the «does not read» paragraph were reworded to what the code proves; the retention sentence is gone and the microphone sentence is its own paragraph; the three failure texts are written. They live
 * in `values/strings.xml` and `values-tr/strings.xml` and NOWHERE in Kotlin (rule 24). The Turkish is proposed and needs a speaker. **Final legal wording: pending Hasan - the KVKK lawyer later**: nothing here is a
 * legal claim, and the text promises only what the code does (the fingerprint is a hash of the identifiers; the app reads none of the listed data; the recording is deleted and not sent).
 *
 * (The text's truthfulness in the `datacollect` flavor waits on P6-28: `plan/TEXTS-AWAITING-APPROVAL-2026-10-09.md:21`.)
 *
 * It is TRUE since the change that followed (2026-10-23, its own commit). What flipping it did: a joined phone whose join answer says `consentEnabled: true` goes to the consent screen before the pre-scan screen, in
 * the phone's language (Arabic or Turkish), and `recordScanConsent` (version 2, the language drawn) can leave the phone; «أوافق» moves on only when the server records it (or says `CONSENT_PART_NOT_ENABLED`:
 * «لا ينطبق»), «لا أوافق» cancels the session with `consent_declined`. A shop without the part (`false`), and an answer that does not carry the key (`absent`: a server that has not got ق15 yet), have NO consent
 * screen and the scan goes on exactly as before - so an APK carrying this against a server that has not deployed ق15 shows no consent screen at all (the safe direction). KNOWN LIMIT: a phone whose language is
 * neither Arabic nor Turkish ([net.neuralcheck.domain.consent.ConsentLocaleRead.Unsupported]) joined to a shop WITH the part draws nothing and Back leaves: it cannot give this consent (reported).
 */
internal const val CONSENT_TEXTS_APPROVED: Boolean = true

/** How a text of the consent screen stands against the user's approvals. */
internal enum class ConsentTextApproval {
    /**
     * Approved by delegation (Hasan, 2026-10-22) and reviewed against the code on 2026-10-23, written verbatim in both languages. The Arabic of [ConsentText.Title], [ConsentText.Accept] and
     * [ConsentText.Decline] is the user's own approved version-1 wording, unchanged.
     */
    APPROVED_BY_DELEGATION,

    /** Proposed, not approved: a slot added later without its wording. The guard may not be true while any text is this. */
    PENDING
}

/**
 * Every text the screen shows - one entry per place, so the list of what needs the user's approval is the code's, not a memory. [resource] is the `R.string` that holds the text in `values` (Arabic) AND
 * `values-tr` (Turkish): one source per text, in both languages, and no wording in Kotlin. [approval] is the text's standing.
 */
internal enum class ConsentText(@StringRes val resource: Int, val approval: ConsentTextApproval) {
    /** م1 / A7-ت1 - the title. */
    Title(R.string.consent_title, ConsentTextApproval.APPROVED_BY_DELEGATION),

    /** م2 / A7-ت2 - what the test measures, and that it computes a code from the device's identifiers instead of using them. */
    Measures(R.string.consent_measures, ConsentTextApproval.APPROVED_BY_DELEGATION),

    /** م3 / A7-ت3 - what it does not read, and the satellite test that keeps no location. */
    DoesNotRead(R.string.consent_does_not_read, ConsentTextApproval.APPROVED_BY_DELEGATION),

    /** م4′ / A7-ت3 - the microphone test: a short recording played back, deleted, not sent (replaces the retention sentence, which the code does not prove). */
    Microphone(R.string.consent_microphone, ConsentTextApproval.APPROVED_BY_DELEGATION),

    /** م5 / A7-ت5 - the button. */
    Accept(R.string.consent_accept, ConsentTextApproval.APPROVED_BY_DELEGATION),

    /** م6 / A7-ت6 - the button. */
    Decline(R.string.consent_decline, ConsentTextApproval.APPROVED_BY_DELEGATION),

    /** A7-ف1 - any failure that has no text of its own (no connection, an answer that is not a recorded consent, a closed gate). */
    RecordFailed(R.string.consent_record_failed, ConsentTextApproval.APPROVED_BY_DELEGATION),

    /** A7-ف2 - `CONSENT_SESSION_NOT_SCANNING`. */
    RecordSessionNotScanning(R.string.consent_record_session_not_scanning, ConsentTextApproval.APPROVED_BY_DELEGATION),

    /** A7-ف3 (with the last sentence of ف4, «اطلب من الفنّي») - `CONSENT_STORED_UNREADABLE`. */
    RecordStoredUnreadable(R.string.consent_record_stored_unreadable, ConsentTextApproval.APPROVED_BY_DELEGATION)
}

/** The labels of the surface in ONE language: one text per [ConsentText]. */
internal typealias ConsentLabels = (ConsentText) -> String

internal object ConsentTexts {
    /** Every text the screen can show. */
    val all: List<ConsentText> = ConsentText.values().toList()

    /** The texts that are not approved: the guard may be true only when this is empty. */
    val pending: List<ConsentText> get() = all.filter { it.approval == ConsentTextApproval.PENDING }

    /**
     * The labels in [locale] - the language the screen is drawn in AND records - read from the resources of THAT language (`values` for `ar`, `values-tr` for `tr`), whatever the phone's own resource choice was:
     * what is recorded is the language of the text the user read. Null while any text is pending: the screen draws nothing without labels.
     */
    fun labelsFor(context: Context, locale: ConsentLocale): ConsentLabels? {
        if (pending.isNotEmpty()) return null
        val configuration = Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(locale.wire)) }
        val resources = context.createConfigurationContext(configuration).resources
        return { text -> resources.getString(text.resource) }
    }

    /** The text a failed recording shows. */
    fun forFailure(failure: ConsentFailure): ConsentText = when (failure) {
        ConsentFailure.SESSION_NOT_SCANNING -> ConsentText.RecordSessionNotScanning
        ConsentFailure.STORED_UNREADABLE -> ConsentText.RecordStoredUnreadable
        ConsentFailure.OTHER -> ConsentText.RecordFailed
    }
}

internal const val CONSENT_TAG = "scanConsent"
internal fun consentTag(vararg parts: String): String = "consent_" + parts.joinToString("_")

/** What the screen asks of its owner: one callback per press. */
internal class ConsentActions(val accept: () -> Unit, val decline: () -> Unit)

/**
 * The consent text and its two buttons, drawn from [ui]: the title, the three paragraphs (what it measures, what it does not read, the microphone), the text of a failed recording when there is one (the text
 * of a failed refusal is the session cancel's, carried in [ui]), a bar while a call is out, «أوافق وأبدأ الفحص» and «لا أوافق». Both buttons are disabled while a call is out and once the screen is finished
 * (recorded, not applicable, declined, no session). Every text is read through [labels]; this file holds none.
 */
@Composable
internal fun ConsentSection(ui: ConsentUi, labels: ConsentLabels, actions: ConsentActions, modifier: Modifier = Modifier) {
    val busy = ui is ConsentUi.Recording || ui is ConsentUi.Declining
    val finished = ui is ConsentUi.Recorded || ui is ConsentUi.NotApplicable || ui is ConsentUi.Declined || ui is ConsentUi.NoSession
    val enabled = !busy && !finished
    Column(modifier = modifier.fillMaxWidth().testTag(CONSENT_TAG), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(labels(ConsentText.Title), color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.testTag(consentTag("title")))
        Text(labels(ConsentText.Measures), color = TextPrimary, fontSize = 15.sp, modifier = Modifier.testTag(consentTag("measures")))
        Text(labels(ConsentText.DoesNotRead), color = TextPrimary, fontSize = 15.sp, modifier = Modifier.testTag(consentTag("doesNotRead")))
        Text(labels(ConsentText.Microphone), color = TextPrimary, fontSize = 15.sp, modifier = Modifier.testTag(consentTag("microphone")))
        (ui as? ConsentUi.RecordFailed)?.let { failed ->
            Text(labels(ConsentTexts.forFailure(failed.failure)), color = TextPrimary, fontSize = 14.sp, modifier = Modifier.testTag(consentTag("failure")))
        }
        (ui as? ConsentUi.DeclineFailed)?.let { failed ->
            Text(failed.message, color = TextPrimary, fontSize = 14.sp, modifier = Modifier.testTag(consentTag("declineFailure")))
        }
        if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().testTag(consentTag("progress")))
        Button(onClick = actions.accept, enabled = enabled, modifier = Modifier.fillMaxWidth().testTag(consentTag("accept"))) { Text(labels(ConsentText.Accept)) }
        OutlinedButton(onClick = actions.decline, enabled = enabled, modifier = Modifier.fillMaxWidth().testTag(consentTag("decline"))) { Text(labels(ConsentText.Decline)) }
    }
}
