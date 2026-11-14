package net.neuralcheck.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import net.neuralcheck.R
import net.neuralcheck.presentation.components.NCPrimaryButton
import net.neuralcheck.presentation.navigation.afterJoinRoute
import net.neuralcheck.presentation.theme.*
import net.neuralcheck.presentation.viewmodel.JoinSessionViewModel
import net.neuralcheck.util.QrCodeHelper

/**
 * Customer-device screen: accepts a shop-owner's session code (typed or
 * pasted), claims it via `joinScanSession`, and — on success — routes
 * straight into the existing active_scan flow. The ScanSessionHolder
 * carries the sessionId through to AiScoringEngine so the scan uploads
 * even though this device is not signed into Firebase Auth.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JoinSessionScreen(
    navController: NavController,
    viewModel: JoinSessionViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var code by remember { mutableStateOf("") }

    // When the viewmodel resolves a sessionId, jump straight into the
    // general-scan flow. The holder the VM already populated is what
    // AiScoringEngine reads at analyzeDevice time — no extra nav arg.
    // A-P6-12 / ق15: through the consent screen when it is live AND the join's answer says the shop has the consent part (afterJoinRoute), else straight to the pre-scan screen as before.
    // The state sets the session and the consent part in one assignment; a null part means there is no join answer yet, and nothing navigates.
    LaunchedEffect(state.sessionId) {
        val sessionId = state.sessionId
        val consentPart = state.consentPart
        if (!sessionId.isNullOrBlank() && consentPart != null) {
            navController.navigate(afterJoinRoute(CONSENT_TEXTS_APPROVED, consentPart)) {
                popUpTo("join_session") { inclusive = true }
            }
        }
    }

    Scaffold(
        containerColor = DeepSpace,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.join_session_title), color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DeepSpace)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(DeepSpace)
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(24.dp))

            Text(
                stringResource(R.string.join_session_prompt),
                color = TextSecondary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = code,
                onValueChange = { input ->
                    // Accept both the raw code and a pasted deeplink —
                    // parseSessionCode normalises to "NC-XXXX" when it
                    // recognises the shape, otherwise keeps the user's
                    // text so they can keep editing.
                    code = QrCodeHelper.parseSessionCode(input) ?: input.uppercase()
                },
                label = { Text(stringResource(R.string.join_session_label)) },
                placeholder = { Text(stringResource(R.string.join_session_placeholder)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    capitalization = KeyboardCapitalization.Characters
                ),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NeuralGreen,
                    unfocusedBorderColor = BorderColor,
                    focusedLabelColor = NeuralGreen,
                    unfocusedLabelColor = TextSecondary,
                    cursorColor = NeuralGreen,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedContainerColor = CardDark,
                    unfocusedContainerColor = CardDark
                )
            )

            Spacer(Modifier.height(20.dp))

            // QR scan path. QRScannerScreen now decodes both formats:
            //  - `neuralcheck://session/NC-XXXXXX` from the shop owner's
            //    GenerateSessionScreen (this screen's main use case).
            //  - 36-char scan UUIDs for the legacy post-repair link flow.
            // Routes back into pre_scan/general/none on a successful join,
            // so navigating here is now a complete alternative to typing.
            Text(
                stringResource(R.string.join_session_or_scan),
                color = TextMuted,
                fontSize = 12.sp
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { navController.navigate("qr_scanner") },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = CardDark)
            ) {
                Icon(Icons.Default.QrCode, null, tint = TextSecondary)
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.join_session_open_camera),
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(Modifier.height(24.dp))

            // `state` is a delegated property, so `state.error` cannot be smart-cast: it is read once into a local. (The `?: join_session_invalid` that stood here only
            // gave Text a non-null type inside this very null check - it never ran, and it was not meant to fill an absence; batch 3, item 3.)
            val joinError = state.error
            if (joinError != null) {
                Surface(
                    color = StatusFail.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        joinError,
                        color = StatusFail,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(12.dp)
                    )
                }
                Spacer(Modifier.height(12.dp))
            }

            NCPrimaryButton(
                text = stringResource(R.string.join_session_start),
                icon = Icons.Default.PlayArrow,
                enabled = code.isNotBlank() && !state.isLoading,
                isLoading = state.isLoading,
                onClick = { viewModel.join(code) }
            )

            Spacer(Modifier.height(32.dp))
        }
    }
}
