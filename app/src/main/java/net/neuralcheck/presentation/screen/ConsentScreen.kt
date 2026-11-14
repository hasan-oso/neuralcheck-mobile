package net.neuralcheck.presentation.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import net.neuralcheck.domain.consent.ConsentLocaleRead
import net.neuralcheck.presentation.navigation.CONSENT_ROUTE
import net.neuralcheck.presentation.navigation.PRE_SCAN_AFTER_JOIN_ROUTE
import net.neuralcheck.presentation.navigation.leaveToScanOrigin
import net.neuralcheck.presentation.theme.DeepSpace
import net.neuralcheck.presentation.viewmodel.ConsentUi
import net.neuralcheck.presentation.viewmodel.ConsentViewModel

/**
 * The consent screen of the scanned phone (A-P6-12; PLAN-06 section 11): between the join and the pre-scan screen (the route is reached only through `afterJoinRoute`, i.e. only while
 * [CONSENT_TEXTS_APPROVED] is true AND the join's answer said the shop has the consent part, ق15). It is drawn in the language the phone is showing - the language it records is that same reading
 * ([ConsentLocaleRead], from the screen's own configuration), so what is recorded is the language of the text the user read: the text is read from the resources OF THAT LANGUAGE ([ConsentTexts.labelsFor]); a language
 * that is neither Arabic nor Turkish ([ConsentLocaleRead.Unsupported], or no language) draws nothing and Back leaves (a known limit, reported: such a phone cannot give this consent).
 *
 * The only ways out: «أوافق وأبدأ الفحص» once the server has recorded it, or the server's `CONSENT_PART_NOT_ENABLED` (ق40: the shop has no consent part, «لا ينطبق») - both to the pre-scan screen - «لا أوافق» (the
 * session is cancelled with `consent_declined`; back to where the scan funnel started, as a leave does), and a holder with no session (nothing to consent to). Back does nothing: the refusal is the button's, not an accident of Back (the plan's decline is a recorded refusal, `cancelReason = consent_declined`).
 */
@Composable
fun ConsentScreen(navController: NavController, viewModel: ConsentViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val locale = (ConsentLocaleRead.of(LocalConfiguration.current.locales[0].language) as? ConsentLocaleRead.Known)?.locale
    val labels = locale?.let { ConsentTexts.labelsFor(context, it) }

    LaunchedEffect(state) {
        when (state) {
            // recorded, or the server says the shop has no consent part (ق40, «لا ينطبق»): either way the scan goes on
            ConsentUi.Recorded, ConsentUi.NotApplicable -> navController.navigate(PRE_SCAN_AFTER_JOIN_ROUTE) { popUpTo(CONSENT_ROUTE) { inclusive = true } }
            ConsentUi.Declined, ConsentUi.NoSession -> navController.leaveToScanOrigin()
            else -> Unit
        }
    }
    // with no labels nothing is drawn, and Back is the system's (a screen that cannot be answered must not trap the user)
    BackHandler(enabled = labels != null) { }
    if (locale == null || labels == null) return

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepSpace)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp)
    ) {
        ConsentSection(ui = state, labels = labels, actions = ConsentActions(accept = { viewModel.accept(locale) }, decline = viewModel::decline))
    }
}
