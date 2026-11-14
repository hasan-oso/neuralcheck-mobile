package net.neuralcheck.data.remote

import android.util.Log
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull
import net.neuralcheck.data.auth.AuthManager
import net.neuralcheck.util.EvalCardLog

/**
 * How long the device waits for its anonymous sign-in before it gives up and
 * joins without an identity (the pre-1.0.11 behaviour). The sign-in is a network
 * round trip made BEFORE the `joinScanSession` callable, and that callable's own
 * 30 s cap (`CALLABLE_TIMEOUT_MS` in FirebaseAnalysisService) starts only after
 * it — so without a bound here a stalled connection would stretch the "joining"
 * spinner past the cap the join always had. No measured value backs 10 s; it is
 * an assumption (order 2026-10-10 fixed the behaviour, not the number).
 */
internal const val DEVICE_IDENTITY_SIGN_IN_TIMEOUT_MS = 10_000L

private const val TAG = "NeuralCheck.Join"

/**
 * Order 2026-10-10 (station 1, before the 1.0.11 build) — the customer device
 * signs in anonymously BEFORE it calls `joinScanSession`, so the join carries the
 * device's own uid (`request.auth.uid` on the server) and the server can tell the
 * joining device from another one (a re-join from the same uid is accepted with
 * no message and no side effect — server commit, other repository).
 *
 * Why here, and why both callers: the join has two entry points, the typed-code
 * screen ([net.neuralcheck.presentation.viewmodel.JoinSessionViewModel.join]) and
 * the QR scanner ([net.neuralcheck.presentation.viewmodel.QRScannerViewModel]'s
 * session branch). Both call this one function instead of
 * [FirebaseAnalysisService.joinScanSession] directly; patching only the typed-code
 * ViewModel would leave the QR path joining anonymously-less. It is not placed
 * inside `FirebaseAnalysisService.joinScanSession` itself because that class
 * cannot be built in a JVM test (its `Firebase.functions` field initialiser needs
 * an initialised FirebaseApp), so the order "sign in first, then join" could not
 * be proven there.
 *
 * Rules:
 *  - already signed in (any user, anonymous or real) -> no new sign-in;
 *  - not signed in -> `authManager.signInAnonymously()` first, then the join;
 *  - never `registerUser`, never a `users/{uid}` document: this is the same
 *    throwaway-uid sign-in `AiScoringEngine` already performs at analysis time
 *    for session scans, just earlier;
 *  - the sign-in failing (network or anything else) or timing out does NOT fail
 *    the join: it proceeds without an identity exactly as before, one log line
 *    says why, and nothing new reaches the user. The only thing that is rethrown
 *    is the caller's own cancellation.
 *
 * The log line carries the exception class and at most 200 characters of its
 * message, without passing the throwable (rule 36 — same treatment as
 * `joinSessionErrorMessage`).
 *
 * Batch 3, item 3 (rule 22): an answer WITHOUT a session id - the key missing, a JSON null, or a blank text - is not a join. It ends here, for both entry points at
 * once, in [JoinAnswerWithoutSessionIdException] and one `session-join-failed` line; each caller then shows the text its own point already showed for a failure that has
 * no approved text (`SessionMessages.kt`). What comes back is a [JoinedSession], whose id is a non-blank `String` - the callers do not look for a blank one.
 */
internal suspend fun joinScanSessionWithDeviceIdentity(
    authManager: AuthManager,
    analysisService: FirebaseAnalysisService,
    code: String
): JoinedSession {
    if (!authManager.isLoggedIn) {
        try {
            val finished = withTimeoutOrNull(DEVICE_IDENTITY_SIGN_IN_TIMEOUT_MS) {
                authManager.signInAnonymously()
                true
            }
            if (finished == null) {
                Log.w(
                    TAG,
                    "anonymous sign-in did not finish within ${DEVICE_IDENTITY_SIGN_IN_TIMEOUT_MS}ms; " +
                        "joining without a device identity"
                )
            }
        } catch (t: Throwable) {
            // The coroutine's own cancellation must propagate; any other failure
            // (a Task cancelled inside the SDK included) only costs the identity.
            currentCoroutineContext().ensureActive()
            Log.w(
                TAG,
                "anonymous sign-in failed (${t.javaClass.simpleName}: ${t.message?.take(200)}); " +
                    "joining without a device identity"
            )
        }
    }
    val answer = analysisService.joinScanSession(code)
    val sessionId = answer.sessionId?.takeIf { it.isNotBlank() }
    if (sessionId == null) {
        val absent = answer.sessionId == null
        EvalCardLog.sessionJoinFailed(if (absent) "absent" else "blank")
        throw JoinAnswerWithoutSessionIdException(absent)
    }
    return JoinedSession(sessionId, answer)
}

/** A join the server answered with a session id: [sessionId] is the answer's id, never null or blank; [answer] is the whole answer (the holder keeps its owner and flag). */
internal class JoinedSession(val sessionId: String, val answer: JoinSessionResult)
