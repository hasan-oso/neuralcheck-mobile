package net.neuralcheck.presentation.screen

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.Brush
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.android.gms.auth.api.identity.BeginSignInRequest
import com.google.android.gms.auth.api.identity.Identity
import net.neuralcheck.R
import net.neuralcheck.presentation.theme.*
import net.neuralcheck.presentation.viewmodel.LoginViewModel

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onJoinSession: () -> Unit = {},
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            try {
                val credential = Identity.getSignInClient(context)
                    .getSignInCredentialFromIntent(result.data)
                viewModel.onGoogleSignInResult(credential.googleIdToken)
            } catch (e: Exception) {
                viewModel.onGoogleSignInResult(null)
            }
        } else {
            viewModel.onGoogleSignInResult(null)
        }
    }

    // Email + password are the only two free-text fields on the screen now.
    // Google sign-in mints accounts server-side; "I have a code" skips auth
    // entirely; everything else (display name, phone, shop info) is collected
    // post-login from ProfileScreen.
    var email by remember { mutableStateOf("") }
    var pw by remember { mutableStateOf("") }

    LaunchedEffect(uiState.isLoggedIn) {
        if (uiState.isLoggedIn) onLoginSuccess()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NeuralDark)
            // Phase 1.15 (edge-to-edge fix): no Scaffold here, so inset the
            // whole column under the status bar (Google button at top) and
            // above the nav bar ("I have a code" CTA at bottom).
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(48.dp))

        Text(
            "NeuralCheck",
            fontWeight = FontWeight.ExtraBold,
            fontSize = 32.sp,
            color = NeuralGreen,
        )
        Text(
            stringResource(R.string.app_tagline),
            color = TextSecondary,
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.login_description),
            color = TextTertiary,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        )

        Spacer(Modifier.height(28.dp))

        // ── 1) Google Sign-In — primary CTA, sits first because it is the
        // canonical recommended path (one tap, no password to remember) ──
        OutlinedButton(
            onClick = {
                viewModel.signInWithGoogle()
                val webClientId = context.getString(R.string.default_web_client_id)
                if (webClientId == "PLACEHOLDER") {
                    viewModel.onGoogleSignInResult(null)
                    return@OutlinedButton
                }
                val signInRequest = BeginSignInRequest.builder()
                    .setGoogleIdTokenRequestOptions(
                        BeginSignInRequest.GoogleIdTokenRequestOptions.builder()
                            .setSupported(true)
                            .setServerClientId(webClientId)
                            .setFilterByAuthorizedAccounts(false)
                            .build()
                    )
                    .build()
                Identity.getSignInClient(context)
                    .beginSignIn(signInRequest)
                    .addOnSuccessListener { result ->
                        launcher.launch(
                            IntentSenderRequest.Builder(result.pendingIntent.intentSender).build()
                        )
                    }
                    .addOnFailureListener {
                        viewModel.onGoogleSignInResult(null)
                    }
            },
            enabled = !uiState.isGoogleLoading,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            border = BorderStroke(1.dp, Color(0xFFDADCE0)),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White),
        ) {
            if (uiState.isGoogleLoading) {
                CircularProgressIndicator(
                    Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = Color(0xFF4285F4),
                )
            } else {
                GoogleGlyph()
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(R.string.login_google),
                    color = Color(0xFF3C4043),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        // ── "أو" divider ──
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            HorizontalDivider(Modifier.weight(1f), color = BorderColor)
            Text(
                stringResource(R.string.login_or),
                modifier = Modifier.padding(horizontal = 16.dp),
                color = TextTertiary,
                fontSize = 13.sp,
            )
            HorizontalDivider(Modifier.weight(1f), color = BorderColor)
        }

        Spacer(Modifier.height(18.dp))

        // ── 2) Email + password fields ──
        LoginField(
            value = email,
            onValueChange = { email = it },
            label = stringResource(R.string.login_email),
            icon = Icons.Default.Email,
            keyboard = KeyboardType.Email,
        )
        Spacer(Modifier.height(12.dp))

        LoginSecureField(
            value = pw,
            onValueChange = { pw = it },
            label = stringResource(R.string.login_credential),
        )

        Spacer(Modifier.height(14.dp))

        // ── Error / info banners. error = red (validation / wrong password),
        // info = green (password reset link sent). Mutually exclusive in the
        // ViewModel — only one of the two is populated at any moment. ──
        if (uiState.error != null) {
            Text(
                uiState.error!!,
                color = StatusFail,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
        }
        if (uiState.info != null) {
            Text(
                uiState.info!!,
                color = NeuralGreen,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
        }

        // ── 3) Two side-by-side buttons: login + register ──
        // Login is the primary (gradient fill). Register is the secondary
        // (outlined). Both gated by the same email/password validity check
        // so a user can't tap them with junk input.
        val emailPwValid = !uiState.isLoading && email.isNotBlank() && pw.length >= 6
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Login (primary)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        brush = if (emailPwValid) {
                            Brush.horizontalGradient(listOf(NeuralGreen, SkyCyan))
                        } else {
                            Brush.horizontalGradient(listOf(Surface, Surface))
                        }
                    )
                    .clickable(enabled = emailPwValid) {
                        viewModel.login(email.trim(), pw)
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = NeuralDark,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(
                        stringResource(R.string.login_button),
                        color = if (emailPwValid) NeuralDark else TextMuted,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp,
                    )
                }
            }
            // Register (secondary)
            OutlinedButton(
                onClick = { viewModel.register(email.trim(), pw) },
                enabled = emailPwValid,
                modifier = Modifier.weight(1f).height(52.dp),
                border = BorderStroke(1.dp, NeuralGreen.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = CardDark),
            ) {
                Text(
                    stringResource(R.string.register_button),
                    color = NeuralGreen,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        // ── Forgot password link ──
        TextButton(
            onClick = { viewModel.forgotPassword(email) },
            enabled = !uiState.isLoading,
            modifier = Modifier.align(Alignment.End),
        ) {
            Text(
                stringResource(R.string.login_forgot_password),
                color = SkyCyan,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
        }

        Spacer(Modifier.height(18.dp))

        // ── Bottom divider before the secondary "I have a code" CTA ──
        HorizontalDivider(color = BorderColor, modifier = Modifier.fillMaxWidth())

        Spacer(Modifier.height(18.dp))

        // ── 4) "I have a scan code" — skips Firebase Auth entirely. The
        // JoinSession flow tags subsequent callables with the owner's
        // sessionId instead of an authenticated uid. ──
        OutlinedButton(
            onClick = onJoinSession,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            border = BorderStroke(1.dp, NeuralGreen.copy(alpha = 0.4f)),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(containerColor = CardDark),
        ) {
            Icon(Icons.Default.QrCode, null, tint = NeuralGreen)
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.login_have_code),
                color = NeuralGreen,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Spacer(Modifier.height(32.dp))
    }
}

/**
 * Four-colour "G" glyph that reads as the Google mark at a glance without an
 * SVG asset. Compose lacks a native multi-colour character renderer, so we
 * stack four stretched letters with per-glyph tint — cheaper than a vector
 * drawable and it keeps the whole sign-in row in this one file.
 */
@Composable
private fun GoogleGlyph() {
    Box(contentAlignment = Alignment.Center) {
        Text("G", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4285F4))
    }
}

@Composable
private fun LoginTab(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) NeuralGreen.copy(alpha = 0.15f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (selected) NeuralGreen else TextSecondary,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            fontSize = 14.sp,
        )
    }
}

@Composable
private fun LoginField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: ImageVector,
    keyboard: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        leadingIcon = { Icon(icon, null, tint = TextSecondary) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        singleLine = true,
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
            unfocusedContainerColor = CardDark,
        ),
    )
}

@Composable
private fun LoginSecureField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
) {
    var visible by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        leadingIcon = { Icon(Icons.Default.Lock, null, tint = TextSecondary) },
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    null,
                    tint = TextSecondary,
                )
            }
        },
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        singleLine = true,
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
            unfocusedContainerColor = CardDark,
        ),
    )
}
