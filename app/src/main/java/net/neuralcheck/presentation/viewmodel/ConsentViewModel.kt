package net.neuralcheck.presentation.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import net.neuralcheck.data.remote.FirebaseAnalysisService
import net.neuralcheck.data.remote.ScanConsentClient
import net.neuralcheck.data.remote.SessionLeaver
import net.neuralcheck.domain.consent.ConsentLocale
import javax.inject.Inject

/**
 * The consent screen's view model (A-P6-12): the logic is [ScanConsentController] (over the real [ScanConsentClient] and the real [SessionLeaver]); this holds it for the screen. A failed refusal shows the text
 * the session's cancel already shows for that failure ([SessionCall.CANCEL_CUSTOMER], `SessionMessages.kt`: the two approved texts, else the generic one) - no new text. The screen that would show this is behind
 * `CONSENT_TEXTS_APPROVED`, so nothing here runs in production yet.
 */
@HiltViewModel
class ConsentViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    analysisService: FirebaseAnalysisService,
    leaver: SessionLeaver
) : ViewModel() {

    private val controller = ScanConsentController(
        client = ScanConsentClient(analysisService),
        leaver = leaver,
        currentSession = { leaver.sessionOfPreScan() },
        failureMessage = { t -> sessionErrorMessage(context, SessionCall.CANCEL_CUSTOMER, t) }
    )

    val state: StateFlow<ConsentUi> = controller.state

    /** «أوافق وأبدأ الفحص», in [locale] - the language the screen was drawn in. */
    fun accept(locale: ConsentLocale) {
        viewModelScope.launch { controller.accept(locale) }
    }

    /** «لا أوافق». */
    fun decline() {
        viewModelScope.launch { controller.decline() }
    }
}
