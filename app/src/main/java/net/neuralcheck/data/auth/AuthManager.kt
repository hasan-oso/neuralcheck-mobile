package net.neuralcheck.data.auth

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import net.neuralcheck.data.local.db.NeuralCheckDatabase
import net.neuralcheck.data.remote.ScanSessionHolder
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin wrapper around [FirebaseAuth] for the launch flavor. Every callable
 * Cloud Function (see [net.neuralcheck.data.remote.FirebaseAnalysisService])
 * authenticates via `request.auth`, so the app needs *some* signed-in user
 * before the first analysis call — including the short-term anonymous
 * bootstrap we use until the real login UI ships.
 *
 * The datacollect flavor never calls this: `REQUIRE_SUBSCRIPTION` is false
 * and the scoring engine is rule-based, so the backend round-trip is
 * skipped altogether.
 */
@Singleton
class AuthManager @Inject constructor(
    private val database: NeuralCheckDatabase,
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context,
    private val scanSessionHolder: ScanSessionHolder
) {

    private val auth: FirebaseAuth = Firebase.auth

    val currentUser: FirebaseUser?
        get() = auth.currentUser

    val isLoggedIn: Boolean
        get() = auth.currentUser != null

    /**
     * What kind of identity this device has right now: [AuthKind.NONE] (no Firebase user), [AuthKind.ANONYMOUS] (a customer's phone: it joined a shop's session with a code and
     * the app signed it in anonymously - `JoinWithDeviceIdentity.kt`, `AiScoringEngine.kt`), or [AuthKind.REGISTERED] (any signed-in account). The ONE reading of
     * `FirebaseUser.isAnonymous` (A-SUB wave 1, user decision 1 «P1»; rule 24): navigation, the login screen and the report's «new scan» button all go through [kindOf] -
     * nothing else in the app reads the Firebase flag. No user is neither anonymous nor registered (rule 22).
     */
    val authKind: AuthKind
        get() = kindOf(auth.currentUser)

    /** True only for [AuthKind.ANONYMOUS]: a device with a user that is not an account. False for no user and for an account. */
    val isAnonymous: Boolean
        get() = authKind == AuthKind.ANONYMOUS

    /**
     * Reactive uid stream — emits the current Firebase Auth uid (or null when
     * signed out) every time `auth.addAuthStateListener` fires. Used by Home
     * and History view-models to flip their scan-list queries to the new
     * user's rows the instant sign-in completes, without waiting for a manual
     * re-fetch. Always emits the *current* uid on first collect so cold
     * subscribers don't have to bridge an initial state.
     */
    val uidFlow: Flow<String?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { fbAuth ->
            trySend(fbAuth.currentUser?.uid)
        }
        auth.addAuthStateListener(listener)
        // Emit current value synchronously so a cold collector doesn't sit on
        // the default until the first auth change happens.
        trySend(auth.currentUser?.uid)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    suspend fun signInWithEmail(email: String, password: String): FirebaseUser {
        val result = auth.signInWithEmailAndPassword(email, password).await()
        return result.user ?: error("signInWithEmailAndPassword returned null user")
    }

    suspend fun createAccountWithEmail(email: String, password: String): FirebaseUser {
        val result = auth.createUserWithEmailAndPassword(email, password).await()
        return result.user ?: error("createUserWithEmailAndPassword returned null user")
    }

    suspend fun signInWithGoogle(idToken: String): FirebaseUser {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val result = auth.signInWithCredential(credential).await()
        return result.user ?: error("signInWithCredential returned null user")
    }

    /**
     * Anonymous sign-in — used by the bootstrap in [net.neuralcheck.NeuralCheckApp]
     * so the first AI call doesn't fail with an unauthenticated error before
     * the real login screen is built.
     */
    suspend fun signInAnonymously(): FirebaseUser {
        val result = auth.signInAnonymously().await()
        return result.user ?: error("signInAnonymously returned null user")
    }

    /**
     * Triggers Firebase's password-reset email for the given address. The
     * SDK silently no-ops if no user exists with that email — to avoid
     * leaking which addresses are registered, callers should show the same
     * "تم إرسال الرابط" copy regardless of whether this throws or
     * succeeds. Throws on network / quota / malformed-email errors so the
     * ViewModel can surface a localised message via mapFirebaseError.
     */
    suspend fun sendPasswordResetEmail(email: String) {
        auth.sendPasswordResetEmail(email).await()
    }

    /**
     * Signs the current user out and wipes every per-user trace from the
     * device so the next account that signs in doesn't see the previous
     * user's scans on Home / History.
     *
     * Steps (each wrapped in its own try so a failure in one doesn't block
     * the rest — the whole point is best-effort cleanup):
     *  1. `auth.signOut()` — clears the Firebase Auth client state.
     *  2. `database.clearAllTables()` — drops every row across scans,
     *     functional results, scores, technician/peer inputs. The
     *     [HistoryViewModel] and the "this month / total" tiles on
     *     [HomeViewModel] read straight from Room with no userId filter,
     *     so without this they would keep showing the old user's data
     *     until manual data clear.
     *  3. `firestore.terminate()` + `clearPersistence()` — drops the
     *     offline Firestore cache. Without it, a query from the new user
     *     on a never-online connection could resolve from cached docs of
     *     the previous user. `terminate()` requires no active listeners,
     *     hence the try/catch — if a snapshot listener is still attached
     *     (e.g. user signed out from a screen that still has one wired
     *     up), we log and move on rather than blocking sign-out.
     *
     * Suspending so callers can `await()` the wipe before navigating to
     * the login screen, otherwise a fast nav + recomposition can race
     * with the DAO clear and briefly show the previous user's scans.
     */
    suspend fun signOut() {
        try {
            auth.signOut()
        } catch (t: Throwable) {
            Log.w(TAG, "signOut: auth.signOut failed: ${t.message}")
        }
        try {
            // clearAllTables blocks on the DB write thread; force it onto
            // an IO dispatcher so we don't grab a worker thread we don't
            // need from the dispatcher the caller suspended on.
            withContext(Dispatchers.IO) { database.clearAllTables() }
            Log.d(TAG, "signOut: Room database cleared")
        } catch (t: Throwable) {
            Log.w(TAG, "signOut: clearAllTables failed: ${t.message}")
        }
        try {
            val firestore = Firebase.firestore
            firestore.terminate().await()
            firestore.clearPersistence().await()
            Log.d(TAG, "signOut: Firestore persistence cleared")
        } catch (t: Throwable) {
            Log.w(TAG, "signOut: Firestore clearPersistence failed: ${t.message}")
        }
        // Wipe the on-disk time-series cache. These .ts.json.gz files contain
        // raw 18-channel sample data the previous user generated; without
        // explicit cleanup they live on under filesDir/timeseries/ and a new
        // account on the same device could open old reports via direct file
        // path if any UI ever falls back to the local file (the AI report
        // path does, on Firestore-cache miss).
        try {
            withContext(Dispatchers.IO) {
                File(appContext.filesDir, "timeseries").deleteRecursively()
                File(appContext.filesDir, "timeseries_temp").deleteRecursively()
            }
            Log.d(TAG, "signOut: time-series files cleared")
        } catch (t: Throwable) {
            Log.w(TAG, "signOut: time-series cleanup failed: ${t.message}")
        }
        // ScanSessionHolder is an in-memory @Singleton that survives sign-out
        // by default. Clear it so a cached customer-device sessionId from the
        // previous user can't accidentally tag the next user's first scan.
        try {
            scanSessionHolder.clear(ScanSessionHolder.ClearReason.SIGN_OUT)
        } catch (t: Throwable) {
            Log.w(TAG, "signOut: ScanSessionHolder.clear failed: ${t.message}")
        }
    }

    companion object {
        private const val TAG = "NeuralCheck"

        /** The one reading of a Firebase user: see [authKind]. A static function so the navigation graph (which has no AuthManager) reads it the same way. */
        fun kindOf(user: FirebaseUser?): AuthKind = when {
            user == null -> AuthKind.NONE
            user.isAnonymous -> AuthKind.ANONYMOUS
            else -> AuthKind.REGISTERED
        }
    }
}

/** What a device's Firebase user is - see [AuthManager.authKind]. */
enum class AuthKind {
    /** No user at all. */
    NONE,

    /** An anonymous user: the customer's phone that joined a shop session by code. Not an account: it owns no scans a later visitor may see, has no plan, no purchase. */
    ANONYMOUS,

    /** A signed-in account (email, Google). */
    REGISTERED
}
