package net.neuralcheck.presentation.viewmodel

import android.content.Context
import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.neuralcheck.R
import net.neuralcheck.data.auth.AuthManager
import net.neuralcheck.data.remote.FirebaseAnalysisService
import net.neuralcheck.data.remote.RegisterResult
import net.neuralcheck.util.InAppNotifier
import javax.inject.Inject

/**
 * Backs [net.neuralcheck.presentation.screen.LoginScreen] with the full
 * email/password + Google flow.
 *
 * On register we call `registerUser` right after the Auth account is created
 * so the Firestore `users/{uid}` document the callable Functions require is
 * in place before the user can reach HomeScreen → start a scan. The call is
 * idempotent on the server side, so a failure is logged but not surfaced —
 * the user would otherwise see a confusing "registration succeeded but…"
 * error. Same for Google Sign-In (new Google users get the same server-side
 * onboarding).
 *
 * Error strings live in [R.string] instead of hard-coded literals so the
 * user sees localized feedback regardless of the active locale; the raw
 * Firebase message is only used as a last-resort fallback.
 *
 * Google Sign-In uses the One-Tap `Identity` API — the actual
 * `BeginSignInRequest` construction and ActivityResult launcher stay in the
 * Composable because they need a Context. The ViewModel exposes
 * [signInWithGoogle] (flips the loading flag so the button disables) and
 * [onGoogleSignInResult] (handles the ID token or surfaces an error).
 */
@HiltViewModel
class LoginViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authManager: AuthManager,
    private val firebaseService: FirebaseAnalysisService,
    private val inAppNotifier: InAppNotifier,
) : ViewModel() {

    data class LoginUiState(
        val isLoading: Boolean = false,
        val isGoogleLoading: Boolean = false,
        val isLoggedIn: Boolean = false,
        val error: String? = null,
        // Non-error transient confirmation copy — currently used for the
        // "password reset link sent" message after [forgotPassword]. Kept
        // separate from [error] so the screen can render it in a different
        // colour without conflating the two.
        val info: String? = null,
    )

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    init {
        // If the user already has a valid Firebase session (e.g. they opened
        // the app, backed out of LoginScreen, and re-entered), skip straight
        // to home. MainActivity picks the start destination on cold start;
        // this handles the warm-start case.
        // A-SUB wave 1 (user decision 1, P1): an ANONYMOUS user is not a session to skip with - it is a customer's phone that joined by code, and login is where its next code
        // is typed (the graph starts there, AnonymousRouting.kt). Forwarding it to home would loop: home is closed to it.
        if (authManager.isLoggedIn && !authManager.isAnonymous) {
            _uiState.value = _uiState.value.copy(isLoggedIn = true)
        }
    }

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                authManager.signInWithEmail(email, password)
                persistFcmToken()
                _uiState.value = _uiState.value.copy(isLoading = false, isLoggedIn = true)
            } catch (t: Throwable) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = mapFirebaseError(t),
                )
            }
        }
    }

    /**
     * Email-only registration. Forwards to the canonical 4-arg [register]
     * with empty name/phone — the Cloud Function accepts blank values and
     * the user can fill them in later from ProfileScreen → Edit profile.
     * Added because the simplified LoginScreen no longer collects name +
     * phone up-front (Google sign-in is the canonical flow; email is the
     * fallback for users without a Google account).
     */
    fun register(email: String, password: String) {
        register(email = email, password = password, name = "", phone = "")
    }

    fun register(email: String, password: String, name: String, phone: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                // 1. Create the Firebase Auth account.
                authManager.createAccountWithEmail(email, password)
                // 2. Create the backing Firestore document. Failure here is
                //    logged but does not block the user — `analyzeDevice`
                //    checks this doc and will throw PERMISSION_DENIED with
                //    a message the user sees on the report screen if the
                //    onboarding call actually failed. That surface is a
                //    better place to retry than blocking login.
                val result: RegisterResult? = runCatching {
                    firebaseService.registerUser(
                        name = name,
                        phone = phone,
                        type = "individual"
                    )
                }.getOrNull()
                persistFcmToken()
                _uiState.value = _uiState.value.copy(isLoading = false, isLoggedIn = true)
                maybeWelcome(result)
            } catch (t: Throwable) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = mapFirebaseError(t),
                )
            }
        }
    }

    /**
     * Flips the Google-loading flag *before* the Composable launches the
     * One-Tap intent. The actual credential round-trip completes in
     * [onGoogleSignInResult] once the ActivityResult comes back.
     */
    fun signInWithGoogle() {
        _uiState.value = _uiState.value.copy(isGoogleLoading = true, error = null)
    }

    /**
     * Called with the Google ID token from the One-Tap
     * [com.google.android.gms.auth.api.identity.SignInCredential]. A null
     * token means either the user cancelled, the flow failed, or Google
     * Sign-In isn't configured on this build — in either case we reset the
     * loading flag and surface a localized error.
     */
    fun onGoogleSignInResult(idToken: String?, @StringRes fallbackErrorRes: Int = R.string.login_google_failed) {
        if (idToken == null) {
            _uiState.value = _uiState.value.copy(
                isGoogleLoading = false,
                error = context.getString(fallbackErrorRes),
            )
            return
        }
        viewModelScope.launch {
            try {
                authManager.signInWithGoogle(idToken)
                // Same registerUser idempotency rationale as in [register].
                // Returning Google users get `existing = true` from the
                // server so [maybeWelcome] suppresses the snackbar — we
                // don't want to greet an existing user every time they
                // sign in with Google.
                val result: RegisterResult? = runCatching {
                    firebaseService.registerUser(name = "", phone = "", type = "individual")
                }.getOrNull()
                persistFcmToken()
                _uiState.value = _uiState.value.copy(isGoogleLoading = false, isLoggedIn = true)
                maybeWelcome(result)
            } catch (t: Throwable) {
                _uiState.value = _uiState.value.copy(
                    isGoogleLoading = false,
                    error = mapFirebaseError(t),
                )
            }
        }
    }

    /**
     * Pushes the welcome snackbar only when the server confirms the user was
     * just created (`existing == false`). Any other state — a returning
     * user, a registerUser failure that fell through to [runCatching], a
     * legacy server response without the field — is treated as "returning"
     * and produces no snackbar. This matches the product rule that the
     * "مرحباً بك" message only fires the very first time a user signs in,
     * never on subsequent sign-ins.
     */
    private fun maybeWelcome(result: RegisterResult?) {
        if (result != null && !result.existing) {
            inAppNotifier.push(
                messageResId = R.string.in_app_welcome_new_user,
                severity = InAppNotifier.Severity.SUCCESS
            )
        }
    }

    /**
     * Cancels a Google-Sign-In loading state when the One-Tap client raises
     * an error before we ever get to the ActivityResult (e.g. no saved
     * credentials on device, Play Services missing). Separate from
     * [onGoogleSignInResult] so we can distinguish "user saw the sheet and
     * cancelled" from "sheet never opened".
     */
    fun cancelGoogleSignIn(@StringRes messageRes: Int? = null) {
        _uiState.value = _uiState.value.copy(
            isGoogleLoading = false,
            error = messageRes?.let { context.getString(it) },
        )
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    /**
     * Writes the current FCM token to `users/{uid}.fcmToken` so the
     * back-office can target push notifications (payment-approved,
     * subscription-expiring, etc.) at this specific user. Called after
     * every successful auth path because the token and the signed-in uid
     * are independently refreshed — the token may have rotated while the
     * user was signed out, so re-reading it here is the cheapest way to
     * keep Firestore in sync.
     *
     * Silent on failure: a missing token or a transient write error should
     * never block login. [NeuralCheckMessagingService.onNewToken] will
     * retry on the next token rotation, and the next login attempt will
     * try again regardless.
     */
    private fun persistFcmToken() {
        val uid = authManager.currentUser?.uid ?: return
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token ->
                FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(uid)
                    .update("fcmToken", token)
                    .addOnFailureListener { e ->
                        Log.w("LoginViewModel", "fcmToken write failed", e)
                    }
            }
            .addOnFailureListener { e ->
                Log.w("LoginViewModel", "FCM token fetch failed", e)
            }
    }

    /**
     * Sends Firebase's password-reset email. Always shows the "link sent"
     * confirmation regardless of whether the email exists in Firebase Auth
     * — leaking which addresses are registered would help attackers
     * enumerate accounts. Genuine errors (network down, malformed email,
     * SDK quota exceeded) still surface via [mapFirebaseError] because they
     * indicate an actionable client-side problem, not "user not found".
     */
    fun forgotPassword(email: String) {
        val trimmed = email.trim()
        if (trimmed.isBlank()) {
            _uiState.value = _uiState.value.copy(
                error = context.getString(R.string.login_password_reset_email_required),
                info = null,
            )
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, info = null)
            try {
                authManager.sendPasswordResetEmail(trimmed)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    info = context.getString(R.string.login_password_reset_sent),
                )
            } catch (t: Throwable) {
                // Pure no-such-user errors are masked: the SDK surfaces them
                // as "ERROR_USER_NOT_FOUND" / "no user record" but echoing
                // those would fingerprint registered emails. Treat ANY error
                // whose mapped resource is user_not_found as success copy.
                val mapped = mapFirebaseError(t)
                val notFoundCopy = context.getString(R.string.login_error_user_not_found)
                if (mapped == notFoundCopy) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        info = context.getString(R.string.login_password_reset_sent),
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = mapped,
                    )
                }
            }
        }
    }

    fun consumeInfo() {
        _uiState.value = _uiState.value.copy(info = null)
    }

    /**
     * Maps common FirebaseAuthException messages to localized strings. The
     * SDK does not expose stable error codes for every case we care about
     * (and the error messages are in English regardless of locale), so we
     * pattern-match defensively. Unknown errors fall through as the raw
     * message — better than a generic "something went wrong" when we want
     * the user to be able to file a useful bug report.
     */
    private fun mapFirebaseError(t: Throwable): String {
        val msg = t.message.orEmpty().lowercase()
        val resId = when {
            "network" in msg -> R.string.login_error_network
            "email" in msg && ("already" in msg || "in use" in msg) -> R.string.login_error_email_in_use
            "invalid" in msg && "email" in msg -> R.string.login_error_invalid_email
            "password is invalid" in msg || ("wrong" in msg && "password" in msg) -> R.string.login_error_wrong_password
            "weak" in msg && "password" in msg -> R.string.login_error_weak_password
            "user" in msg && "not" in msg && ("found" in msg || "exist" in msg) -> R.string.login_error_user_not_found
            "no user record" in msg -> R.string.login_error_user_not_found
            else -> null
        }
        return resId?.let { context.getString(it) } ?: t.message ?: context.getString(R.string.error_generic)
    }
}
