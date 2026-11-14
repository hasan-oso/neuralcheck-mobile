package net.neuralcheck.presentation.viewmodel

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.EventListener
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.HttpsCallableReference
import com.google.firebase.functions.HttpsCallableResult
import kotlinx.coroutines.cancel
import net.neuralcheck.data.auth.AuthManager
import net.neuralcheck.data.local.db.NeuralCheckDatabase
import net.neuralcheck.data.local.db.dao.SessionEvaluationDao
import net.neuralcheck.data.local.db.entity.SessionEvaluationEntity
import net.neuralcheck.data.remote.FirebaseAnalysisService
import net.neuralcheck.data.remote.ScanSessionHolder
import net.neuralcheck.data.remote.SessionCanceller
import net.neuralcheck.data.remote.ScanSessionRecords
import net.neuralcheck.data.remote.SubscriptionRefreshBus
import net.neuralcheck.data.repository.ScanRepository
import net.neuralcheck.util.EvalCardLog
import net.neuralcheck.util.InMemorySharedPreferences
import org.junit.Assert.fail
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.mockito.invocation.InvocationOnMock
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.stubbing.Answer
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The real join path, with ONLY the network / identity boundary replaced (rule 37).
 *
 * Order 2026-10-10 (coordinator, relaying the user's item 3: "an app test that replays the real
 * path: join with an identity -> scan -> the evaluation form appears").
 *
 * ── What is REAL in what this builds ──
 *  - [FirebaseAnalysisService] — its real constructor (`Firebase.functions` field initialiser and
 *    `AnalysisRequestSerializer.gson`), its real `joinScanSession` (`callWithTimeout`, then
 *    `gson.toJson(result.data)` then `gson.fromJson(json, JoinSessionResult::class.java)`) and its
 *    real `checkSubscription`.
 *  - [AuthManager] — real: `isLoggedIn`, `signInAnonymously()`, `uidFlow` (the real `callbackFlow` that
 *    registers the real `AuthStateListener`), `signOut()`.
 *  - `joinScanSessionWithDeviceIdentity`, [ScanSessionHolder], [JoinSessionViewModel],
 *    [QRScannerViewModel], [PreScanViewModel], [TechnicianEvaluationViewModel].
 *  - `HttpsCallableResult` is the real class (its package-private constructor, by reflection).
 *    `Tasks.forResult` is the real GMS task. `data` is what Firebase's `Serializer.decode` gives
 *    (read from firebase-functions 20.4.0 bytecode): a `HashMap<String, Object>` whose values are
 *    `Boolean` / `String` / `Integer` and `null` for a JSON null; a JS `undefined` is an ABSENT key.
 *
 * ── What is REPLACED (the boundaries) ──
 *  1. `FirebaseFunctions.getInstance()` (static, what the `Firebase.functions` extension calls —
 *     checked in firebase-functions 20.4.0 `FunctionsKt.getFunctions`) returns a mock whose
 *     `getHttpsCallable(name).call(..)` answers an already-completed task holding
 *     [joinResponse] / [checkSubscriptionResponse]. So the HTTP round trip is the only thing not run.
 *  2. `FirebaseAuth.getInstance()` (static, what `Firebase.auth` calls) returns a stateful mock:
 *     `currentUser`, `signInAnonymously()`, `add/removeAuthStateListener`. [fireAuthStateChanged]
 *     delivers an auth-state event to every registered listener the way Firebase does.
 *  3. [NeuralCheckDatabase] and [Context] are mocks; nothing here touches a database or a file.
 *
 * ── Declared substitute ──
 * [FakeSessionEvaluationDao] stands in for Room: when written this module lacked Robolectric, so a Room
 * in-memory database (which needs Android's SQLite) could not be built on the JVM (Robolectric is a test dependency since 3284cca; no test here builds one). The Dao's
 * `getByScanId` / `upsert` are the only two calls [TechnicianEvaluationViewModel] makes.
 *
 * `mockStatic` is thread-local: every object that reads one of the two statics is built on the test
 * thread, and the view models run on `Dispatchers.Main` = `UnconfinedTestDispatcher`, i.e. the test
 * thread, so the calls they make through those objects need no static.
 */
internal class RealJoinPathHarness(
    signedInAtStart: Boolean = false,
    /**
     * The [Context] the join view models are built with. Default: a bare mock (the texts are then whatever the test stubs).
     * Batch 2 (order 2026-10-15, item 2): a test that checks the TEXT the user reads passes a real one (Robolectric's, with the app's resources).
     */
    private val viewModelContext: Context? = null
) : AutoCloseable {

    // ---- what the test sets ----

    /** `HttpsCallableResult.data` of the next `joinScanSession` call, exactly as Firebase would decode it. */
    @Volatile var joinResponse: Any? = null

    /**
     * When set, the next `joinScanSession` call FAILS with it instead of answering [joinResponse] (batch 2, item 2). The caller builds it the way the
     * SDK does for a server refusal - see [SessionWireFixtures.exceptionFromWire], which runs the SDK's own parser over the wire JSON.
     */
    @Volatile var joinFailure: Exception? = null

    /** `HttpsCallableResult.data` of the next `cancelScanSession` call (batch 2, item 2). */
    @Volatile var cancelResponse: Any? = hashMapOf<String, Any?>(
        "sessionId" to "sess-1", "status" to "cancelled", "alreadyCancelled" to false
    )

    /** When set, the next `cancelScanSession` call FAILS with it. */
    @Volatile var cancelFailure: Exception? = null

    /**
     * When set, the next `cancelScanSession` call is HELD: its task stays incomplete until the test completes it (a call that is in flight).
     * The test answers it with `hold.setResult(callableResultOf(data))` or fails it with `hold.setException(e)`.
     */
    @Volatile var cancelHold: com.google.android.gms.tasks.TaskCompletionSource<HttpsCallableResult>? = null

    /** The argument of every `cancelScanSession` callable call. */
    val cancelRequests = CopyOnWriteArrayList<Any?>()

    /**
     * `HttpsCallableResult.data` of the next `createScanSession` call, as Firebase decodes it (batch 2). Default: a normal answer
     * (session id, code, the 60-minute lifetime the server names). A test sets it to an answer that lacks a key to see what the owner's screen does.
     */
    @Volatile var createResponse: Any? = hashMapOf<String, Any?>("sessionId" to "sess-1", "code" to "NC-1234", "expiresInMinutes" to 60)

    /** When set, the next `createScanSession` call FAILS with it. */
    @Volatile var createFailure: Exception? = null

    /**
     * `HttpsCallableResult.data` of the next `setSessionPurpose` call (A-P6-03). The contract copy does not name the success answer's shape, so the harness answers an EMPTY object unless a test
     * sets another - it invents no key.
     */
    @Volatile var purposeResponse: Any? = hashMapOf<String, Any?>()

    /** When set, the next `setSessionPurpose` call FAILS with it. */
    @Volatile var purposeFailure: Exception? = null

    /** When set, the next `setSessionPurpose` call is HELD (a call in flight), as [cancelHold] does for the cancel. */
    @Volatile var purposeHold: com.google.android.gms.tasks.TaskCompletionSource<HttpsCallableResult>? = null

    /** The argument of every `setSessionPurpose` callable call. */
    val purposeRequests = CopyOnWriteArrayList<Any?>()

    /**
     * `HttpsCallableResult.data` of the next `getTechnicianFormConfig` call (A-P6-08). A test builds the one it wants; the real server's two answers are in the evidence copy
     * `technician-form-server-wire-neuralcheck-backend-05275ba.md` (technicianForm.js:312 and :325-336), and `TechnicianFormViewModelTest` builds them from it. Default: an empty object (which the app cannot read).
     */
    @Volatile var techConfigResponse: Any? = hashMapOf<String, Any?>()

    /** The argument of every `getTechnicianFormConfig` callable call (the server wants `{sessionId}`). */
    val techConfigRequests = CopyOnWriteArrayList<Any?>()

    /** `HttpsCallableResult.data` of the next `recordReportFirstOpened` call (A-P6-08): the server answers `{sessionId, recorded}` (technicianForm.js:377). Default: `recorded: true`. */
    @Volatile var reportOpenedResponse: Any? = hashMapOf<String, Any?>("sessionId" to "sess-1", "recorded" to true)

    /** When set, the next `recordReportFirstOpened` call FAILS with it. */
    @Volatile var reportOpenedFailure: Exception? = null

    /** The argument of every `recordReportFirstOpened` callable call. */
    val reportOpenedRequests = CopyOnWriteArrayList<Any?>()

    /**
     * `HttpsCallableResult.data` of the next `getRepeatScanOffer` call (A-P6-07): the answer is the contract's seven keys (A4 (5), neuralcheck-backend@fd730ac, copied in
     * `evidence/run-2026-10-18/protocol-contract-A0-A10-neuralcheck-backend-fd730ac.md`) - written by the test, NOT produced by the server (its half is not deployed). Default: an empty object (which the app cannot read).
     */
    @Volatile var repeatOfferResponse: Any? = hashMapOf<String, Any?>()

    /** When set, the next `getRepeatScanOffer` call FAILS with it. */
    @Volatile var repeatOfferFailure: Exception? = null

    /** The argument of every `getRepeatScanOffer` callable call (the contract: `{sessionId, scanId}`). */
    val repeatOfferRequests = CopyOnWriteArrayList<Any?>()

    /** `HttpsCallableResult.data` of the next `startRepeatScan` call (A-P6-07): the contract's `{sessionId (the child), parentSessionId, state: "STARTED", expiresInMinutes}` (A4 (4)). Default: an empty object. */
    @Volatile var repeatStartResponse: Any? = hashMapOf<String, Any?>()

    /** When set, the next `startRepeatScan` call FAILS with it. */
    @Volatile var repeatStartFailure: Exception? = null

    /** When set, the next `startRepeatScan` call is HELD (a call in flight). */
    @Volatile var repeatStartHold: com.google.android.gms.tasks.TaskCompletionSource<HttpsCallableResult>? = null

    /** The argument of every `startRepeatScan` callable call (the contract: `{sessionId, scanId}` and, only when true, `startedBeforeCooling`). */
    val repeatStartRequests = CopyOnWriteArrayList<Any?>()

    /**
     * `HttpsCallableResult.data` of the next `recordScanConsent` call (A-P6-12): the contract's `{sessionId, state: "accepted", textVersion, locale, alreadyRecorded}` (A8 (1), neuralcheck-backend@5361116, copied in
     * `evidence/run-2026-10-18/protocol-contract-A8-A11-A12-neuralcheck-backend-5361116.md`) - written by the test, NOT produced by the server (its half is not deployed). Default: an empty object (which the app cannot read).
     */
    @Volatile var consentResponse: Any? = hashMapOf<String, Any?>()

    /** When set, the next `recordScanConsent` call FAILS with it. */
    @Volatile var consentFailure: Exception? = null

    /** When set, the next `recordScanConsent` call is HELD (a call in flight). */
    @Volatile var consentHold: com.google.android.gms.tasks.TaskCompletionSource<HttpsCallableResult>? = null

    /** The argument of every `recordScanConsent` callable call (the contract: `{sessionId, textVersion, locale}`). */
    val consentRequests = CopyOnWriteArrayList<Any?>()

    /**
     * `HttpsCallableResult.data` of the next `listOpenMaintenanceBefores` call (order of 2026-10-22): the contract's `{validityHours, candidates: [{beforeScanId, deviceName, scannedAtMs}]}` (A3 (2), copy of
     * neuralcheck-backend@f3c1bab in `app/src/test/resources/contract/`) - written by the test, NOT produced by the server (its half is not deployed). Default: an empty object (which the app cannot read).
     */
    @Volatile var pairListResponse: Any? = hashMapOf<String, Any?>()

    /** When set, the next `listOpenMaintenanceBefores` call FAILS with it. */
    @Volatile var pairListFailure: Exception? = null

    /** When set, the next `listOpenMaintenanceBefores` call is HELD (a call in flight). */
    @Volatile var pairListHold: com.google.android.gms.tasks.TaskCompletionSource<HttpsCallableResult>? = null

    /** The argument of every `listOpenMaintenanceBefores` callable call (the contract: `{sessionId}`). */
    val pairListRequests = CopyOnWriteArrayList<Any?>()

    /** `HttpsCallableResult.data` of the next `confirmMaintenanceAfterLink` call: the contract's `{sessionId, beforeScanId, linkMethod: "manual"}` (A3 (3)) - written by the test. Default: an empty object. */
    @Volatile var pairConfirmResponse: Any? = hashMapOf<String, Any?>()

    /** When set, the next `confirmMaintenanceAfterLink` call FAILS with it. */
    @Volatile var pairConfirmFailure: Exception? = null

    /** When set, the next `confirmMaintenanceAfterLink` call is HELD (a call in flight). */
    @Volatile var pairConfirmHold: com.google.android.gms.tasks.TaskCompletionSource<HttpsCallableResult>? = null

    /** The argument of every `confirmMaintenanceAfterLink` callable call (the contract: `{sessionId, beforeScanId}`). */
    val pairConfirmRequests = CopyOnWriteArrayList<Any?>()

    /** When set, the next `getTechnicianFormConfig` call FAILS with it. */
    @Volatile var techConfigFailure: Exception? = null

    /** When set, the next `submitTechnicianForm` call FAILS with it. */
    @Volatile var techSubmitFailure: Exception? = null

    /** When set, the next `submitTechnicianForm` call is HELD (a call in flight). */
    @Volatile var techSubmitHold: com.google.android.gms.tasks.TaskCompletionSource<HttpsCallableResult>? = null

    /** The argument of every `submitTechnicianForm` callable call. */
    val techSubmitRequests = CopyOnWriteArrayList<Any?>()

    /**
     * When set, the next `submitEvaluation` call FAILS with it (batch 4, item 1: the evaluation's submission, through the real [FirebaseAnalysisService]); otherwise it
     * answers a success with an empty answer (what the real function does is "pending: true" at most - the app reads nothing from it).
     */
    @Volatile var submitEvaluationFailure: Exception? = null

    /** The argument of every `submitEvaluation` callable call. */
    val submitEvaluationRequests = CopyOnWriteArrayList<Any?>()

    /**
     * When set, the next `analyzeDevice` call FAILS with it (batch 4, item 1: the analysis, through the real [FirebaseAnalysisService]). Without it the call answers
     * [analyzeResponse], which is `null` here: a test that wants a successful analysis sets a real one - the harness does not invent a verdict.
     */
    @Volatile var analyzeFailure: Exception? = null
    @Volatile var analyzeResponse: Any? = null

    /** The argument of every `analyzeDevice` callable call. */
    val analyzeRequests = CopyOnWriteArrayList<Any?>()

    /**
     * What reading `reports/{scanId}` does (the analysis asks for a finished report before it pays for a new one): by default it FAILS the way an offline Firestore
     * client does (`UNAVAILABLE`, "Failed to get document because the client is offline"), which `fetchExistingReport` swallows - no cached report, so the live call
     * follows. Set to `null` for "no such document".
     */
    @Volatile var reportReadFailure: Exception? =
        FirebaseFirestoreException("Failed to get document because the client is offline.", FirebaseFirestoreException.Code.UNAVAILABLE)

    /** The scan ids whose `reports/{scanId}` document was read. */
    val reportReads = CopyOnWriteArrayList<String>()

    /** `HttpsCallableResult.data` of the next `checkSubscription` call. */
    @Volatile var checkSubscriptionResponse: Any? =
        hashMapOf<String, Any?>("plan" to "free", "scansRemaining" to 0, "scansTotal" to 0, "isActive" to false)

    /** When set, the next `checkSubscription` call FAILS with it (A-SUB wave 1, item 5: the pre-scan gate's check, through the real [FirebaseAnalysisService]). */
    @Volatile var checkSubscriptionFailure: Exception? = null

    // ---- what the test reads ----

    /** Order of the boundary events: `auth:signInAnonymously`, `auth:listeners-fired`, `callable:<name>`. */
    val events = CopyOnWriteArrayList<String>()

    /** The argument of every `joinScanSession` callable call. */
    val joinRequests = CopyOnWriteArrayList<Any?>()

    val authListeners = CopyOnWriteArrayList<FirebaseAuth.AuthStateListener>()

    @Volatile var signInAnonymouslyCalls = 0
        private set

    fun callableCalls(name: String): Int = events.count { it == "callable:$name" }

    // ---- the substituted boundaries ----

    // The user a real `signInAnonymously()` produces IS anonymous (FirebaseUser.isAnonymous = true): A-SUB wave 1 reads that flag (AuthManager.kindOf), so the stand-in says it, as the real one does.
    private val anonymousUser: FirebaseUser = mock {
        on { uid } doReturn "anon-uid-1"
        on { isAnonymous } doReturn true
    }

    /** A signed-in ACCOUNT (an email or Google user): [FirebaseUser.isAnonymous] false. For a test that needs the device to hold an account, see [signInAs]. */
    val registeredUser: FirebaseUser = mock {
        on { uid } doReturn "registered-uid-1"
        on { isAnonymous } doReturn false
    }

    @Volatile private var signedInUser: FirebaseUser? = if (signedInAtStart) anonymousUser else null

    /** Makes the device hold [user] (null = signed out), as a sign-in or sign-out of the real Firebase would, and tells the auth-state listeners. */
    fun signInAs(user: FirebaseUser?) {
        signedInUser = user
        fireAuthStateChanged()
    }

    private val firebaseAuth: FirebaseAuth = mock()
    private val joinCallable: HttpsCallableReference = mock()
    private val cancelCallable: HttpsCallableReference = mock()
    private val createCallable: HttpsCallableReference = mock()
    private val purposeCallable: HttpsCallableReference = mock()
    private val techConfigCallable: HttpsCallableReference = mock()
    private val techSubmitCallable: HttpsCallableReference = mock()
    private val reportOpenedCallable: HttpsCallableReference = mock()
    private val repeatOfferCallable: HttpsCallableReference = mock()
    private val repeatStartCallable: HttpsCallableReference = mock()
    private val consentCallable: HttpsCallableReference = mock()
    private val pairListCallable: HttpsCallableReference = mock()
    private val pairConfirmCallable: HttpsCallableReference = mock()
    private val checkSubscriptionCallable: HttpsCallableReference = mock()
    private val submitEvaluationCallable: HttpsCallableReference = mock()
    private val analyzeCallable: HttpsCallableReference = mock()
    private val functions: FirebaseFunctions = mock()

    private val functionsStatic: MockedStatic<FirebaseFunctions> =
        Mockito.mockStatic(FirebaseFunctions::class.java)
    private val authStatic: MockedStatic<FirebaseAuth> = Mockito.mockStatic(FirebaseAuth::class.java)

    /** An `HttpsCallableResult` holding [data] (see the class doc: the real class, its package-private constructor, by reflection). */
    fun callableResultOf(data: Any?): HttpsCallableResult = callableResult(data)

    private fun callableResult(data: Any?): HttpsCallableResult {
        val ctor = HttpsCallableResult::class.java.getDeclaredConstructor(Any::class.java)
        ctor.isAccessible = true
        return ctor.newInstance(data)
    }

    init {
        whenever(joinCallable.call(any())).thenAnswer { inv ->
            events.add("callable:joinScanSession")
            joinRequests.add(inv.getArgument<Any?>(0))
            val failure = joinFailure
            if (failure != null) Tasks.forException<HttpsCallableResult>(failure) else Tasks.forResult(callableResult(joinResponse))
        }
        whenever(createCallable.call()).thenAnswer {
            events.add("callable:createScanSession")
            val failure = createFailure
            if (failure != null) Tasks.forException<HttpsCallableResult>(failure) else Tasks.forResult(callableResult(createResponse))
        }
        whenever(techConfigCallable.call(any())).thenAnswer { inv ->
            events.add("callable:getTechnicianFormConfig")
            techConfigRequests.add(inv.getArgument<Any?>(0))
            val failure = techConfigFailure
            if (failure != null) Tasks.forException<HttpsCallableResult>(failure) else Tasks.forResult(callableResult(techConfigResponse))
        }
        whenever(techSubmitCallable.call(any())).thenAnswer { inv ->
            events.add("callable:submitTechnicianForm")
            techSubmitRequests.add(inv.getArgument<Any?>(0))
            val hold = techSubmitHold
            val failure = techSubmitFailure
            if (hold != null) hold.task
            else if (failure != null) Tasks.forException<HttpsCallableResult>(failure)
            else Tasks.forResult(callableResult(hashMapOf<String, Any?>()))
        }
        whenever(reportOpenedCallable.call(any())).thenAnswer { inv ->
            events.add("callable:recordReportFirstOpened")
            reportOpenedRequests.add(inv.getArgument<Any?>(0))
            val failure = reportOpenedFailure
            if (failure != null) Tasks.forException<HttpsCallableResult>(failure) else Tasks.forResult(callableResult(reportOpenedResponse))
        }
        whenever(repeatOfferCallable.call(any())).thenAnswer { inv ->
            events.add("callable:getRepeatScanOffer")
            repeatOfferRequests.add(inv.getArgument<Any?>(0))
            val failure = repeatOfferFailure
            if (failure != null) Tasks.forException<HttpsCallableResult>(failure) else Tasks.forResult(callableResult(repeatOfferResponse))
        }
        whenever(repeatStartCallable.call(any())).thenAnswer { inv ->
            events.add("callable:startRepeatScan")
            repeatStartRequests.add(inv.getArgument<Any?>(0))
            val hold = repeatStartHold
            val failure = repeatStartFailure
            if (hold != null) hold.task
            else if (failure != null) Tasks.forException<HttpsCallableResult>(failure)
            else Tasks.forResult(callableResult(repeatStartResponse))
        }
        whenever(consentCallable.call(any())).thenAnswer { inv ->
            events.add("callable:recordScanConsent")
            consentRequests.add(inv.getArgument<Any?>(0))
            val hold = consentHold
            val failure = consentFailure
            if (hold != null) hold.task
            else if (failure != null) Tasks.forException<HttpsCallableResult>(failure)
            else Tasks.forResult(callableResult(consentResponse))
        }
        whenever(pairListCallable.call(any())).thenAnswer { inv ->
            events.add("callable:listOpenMaintenanceBefores")
            pairListRequests.add(inv.getArgument<Any?>(0))
            val hold = pairListHold
            val failure = pairListFailure
            if (hold != null) hold.task
            else if (failure != null) Tasks.forException<HttpsCallableResult>(failure)
            else Tasks.forResult(callableResult(pairListResponse))
        }
        whenever(pairConfirmCallable.call(any())).thenAnswer { inv ->
            events.add("callable:confirmMaintenanceAfterLink")
            pairConfirmRequests.add(inv.getArgument<Any?>(0))
            val hold = pairConfirmHold
            val failure = pairConfirmFailure
            if (hold != null) hold.task
            else if (failure != null) Tasks.forException<HttpsCallableResult>(failure)
            else Tasks.forResult(callableResult(pairConfirmResponse))
        }
        whenever(purposeCallable.call(any())).thenAnswer { inv ->
            events.add("callable:setSessionPurpose")
            purposeRequests.add(inv.getArgument<Any?>(0))
            val hold = purposeHold
            val failure = purposeFailure
            if (hold != null) hold.task
            else if (failure != null) Tasks.forException<HttpsCallableResult>(failure)
            else Tasks.forResult(callableResult(purposeResponse))
        }
        whenever(cancelCallable.call(any())).thenAnswer { inv ->
            events.add("callable:cancelScanSession")
            cancelRequests.add(inv.getArgument<Any?>(0))
            val hold = cancelHold
            val failure = cancelFailure
            if (hold != null) hold.task
            else if (failure != null) Tasks.forException<HttpsCallableResult>(failure)
            else Tasks.forResult(callableResult(cancelResponse))
        }
        whenever(checkSubscriptionCallable.call()).thenAnswer {
            events.add("callable:checkSubscription")
            val failure = checkSubscriptionFailure
            if (failure != null) Tasks.forException<HttpsCallableResult>(failure) else Tasks.forResult(callableResult(checkSubscriptionResponse))
        }
        whenever(submitEvaluationCallable.call(any())).thenAnswer { inv ->
            events.add("callable:submitEvaluation")
            submitEvaluationRequests.add(inv.getArgument<Any?>(0))
            val failure = submitEvaluationFailure
            if (failure != null) Tasks.forException<HttpsCallableResult>(failure) else Tasks.forResult(callableResult(hashMapOf<String, Any?>("pending" to true)))
        }
        whenever(analyzeCallable.call(any())).thenAnswer { inv ->
            events.add("callable:analyzeDevice")
            analyzeRequests.add(inv.getArgument<Any?>(0))
            val failure = analyzeFailure
            if (failure != null) Tasks.forException<HttpsCallableResult>(failure) else Tasks.forResult(callableResult(analyzeResponse))
        }
        whenever(functions.getHttpsCallable(any<String>())).thenAnswer { inv ->
            when (val name = inv.getArgument<String>(0)) {
                "joinScanSession" -> joinCallable
                "cancelScanSession" -> cancelCallable
                "createScanSession" -> createCallable
                "setSessionPurpose" -> purposeCallable
                "getTechnicianFormConfig" -> techConfigCallable
                "submitTechnicianForm" -> techSubmitCallable
                "recordReportFirstOpened" -> reportOpenedCallable
                "getRepeatScanOffer" -> repeatOfferCallable
                "startRepeatScan" -> repeatStartCallable
                "recordScanConsent" -> consentCallable
                "listOpenMaintenanceBefores" -> pairListCallable
                "confirmMaintenanceAfterLink" -> pairConfirmCallable
                "checkSubscription" -> checkSubscriptionCallable
                "submitEvaluation" -> submitEvaluationCallable
                "analyzeDevice" -> analyzeCallable
                else -> throw AssertionError("the real path asked FirebaseFunctions for an unexpected callable: $name")
            }
        }
        functionsStatic.`when`<FirebaseFunctions> { FirebaseFunctions.getInstance() }.thenReturn(functions)

        whenever(firebaseAuth.currentUser).thenAnswer { signedInUser }
        whenever(firebaseAuth.signInAnonymously()).thenAnswer {
            events.add("auth:signInAnonymously")
            signInAnonymouslyCalls++
            signedInUser = anonymousUser
            // Firebase notifies the listeners when the sign-in lands; here that is before the task is returned.
            fireAuthStateChanged()
            val result = mock<AuthResult> { on { user } doReturn anonymousUser }
            Tasks.forResult(result)
        }
        // Both are void methods: stubbed with doAnswer, not whenever.
        doAnswer { inv ->
            authListeners.add(inv.getArgument<FirebaseAuth.AuthStateListener>(0))
            null
        }.whenever(firebaseAuth).addAuthStateListener(any<FirebaseAuth.AuthStateListener>())
        doAnswer { inv ->
            authListeners.remove(inv.getArgument<FirebaseAuth.AuthStateListener>(0))
            null
        }.whenever(firebaseAuth).removeAuthStateListener(any<FirebaseAuth.AuthStateListener>())
        authStatic.`when`<FirebaseAuth> { FirebaseAuth.getInstance() }.thenReturn(firebaseAuth)
    }

    // ---- Firestore: the session documents (batch 2) ----
    //
    // Declared substitute: the Firestore network. `FirebaseFirestore.getInstance()` (static, what the `Firebase.firestore` extension calls) returns a mock
    // whose `collection("scanSessions").document(id).addSnapshotListener(l)` keeps `l`; [deliverSnapshot] then hands it a snapshot the way the SDK does
    // (a `DocumentSnapshot` whose `exists()` and `get(field)` answer), and [deliverSnapshotError] an error. The snapshot is a mock because a real one
    // cannot be built off a Firestore instance; what is real is everything that READS it.

    /** The snapshot listener last registered on `scanSessions/{id}`, by document id. */
    val snapshotListeners = ConcurrentHashMap<String, EventListener<DocumentSnapshot>>()

    /** The document ids whose listener registration was removed, in order. */
    val removedListenerIds = CopyOnWriteArrayList<String>()

    private val firestore: FirebaseFirestore = mock()
    private val sessionsCollection: CollectionReference = mock()
    private val reportsCollection: CollectionReference = mock()
    private val firestoreStatic: MockedStatic<FirebaseFirestore> = Mockito.mockStatic(FirebaseFirestore::class.java)

    init {
        whenever(firestore.collection(any<String>())).thenAnswer { inv ->
            when (val name = inv.getArgument<String>(0)) {
                "scanSessions" -> sessionsCollection
                "reports" -> reportsCollection
                else -> throw AssertionError("the real path asked Firestore for an unexpected collection: $name")
            }
        }
        whenever(reportsCollection.document(any<String>())).thenAnswer { inv ->
            val id = inv.getArgument<String>(0)
            val ref = mock<DocumentReference>()
            whenever(ref.get()).thenAnswer {
                reportReads.add(id)
                val failure = reportReadFailure
                if (failure != null) {
                    Tasks.forException<DocumentSnapshot>(failure)
                } else {
                    val missing = mock<DocumentSnapshot>()
                    whenever(missing.exists()).thenReturn(false)
                    Tasks.forResult(missing)
                }
            }
            ref
        }
        whenever(sessionsCollection.document(any<String>())).thenAnswer { inv ->
            val id = inv.getArgument<String>(0)
            val ref = mock<DocumentReference>()
            whenever(ref.addSnapshotListener(any<EventListener<DocumentSnapshot>>())).thenAnswer { l ->
                snapshotListeners[id] = l.getArgument<EventListener<DocumentSnapshot>>(0)
                val registration = mock<ListenerRegistration>()
                doAnswer {
                    removedListenerIds.add(id)
                    null
                }.whenever(registration).remove()
                registration
            }
            ref
        }
        firestoreStatic.`when`<FirebaseFirestore> { FirebaseFirestore.getInstance() }.thenReturn(firestore)
    }

    /** The snapshot Firestore delivers for `scanSessions/{sessionId}`: [exists] and the fields (a missing field answers null, as Firestore's `get` does). */
    fun deliverSnapshot(sessionId: String, exists: Boolean = true, fields: Map<String, Any?> = emptyMap()) {
        val listener = snapshotListeners[sessionId] ?: throw AssertionError("no snapshot listener is registered on scanSessions/$sessionId")
        val snapshot = mock<DocumentSnapshot>()
        whenever(snapshot.exists()).thenReturn(exists)
        whenever(snapshot.get(any<String>())).thenAnswer { inv -> if (exists) fields[inv.getArgument<String>(0)] else null }
        // getString answers null for a missing field and THROWS for a field of another type, as the real one does
        whenever(snapshot.getString(any<String>())).thenAnswer { inv ->
            val value = if (exists) fields[inv.getArgument<String>(0)] else null
            if (value != null && value !is String) throw ClassCastException("Field '${inv.getArgument<String>(0)}' is not a String")
            value as String?
        }
        listener.onEvent(snapshot, null)
    }

    /** The error Firestore delivers instead of a snapshot (the listener is over after it). */
    fun deliverSnapshotError(sessionId: String, error: FirebaseFirestoreException) {
        val listener = snapshotListeners[sessionId] ?: throw AssertionError("no snapshot listener is registered on scanSessions/$sessionId")
        listener.onEvent(null, error)
    }

    /** What Firebase does on every sign-in / sign-out: call each registered listener with the auth instance. */
    fun fireAuthStateChanged() {
        authListeners.toList().forEach { it.onAuthStateChanged(firebaseAuth) }
        events.add("auth:listeners-fired")
    }

    // ---- the permanent log lines (NeuralCheck.EvalCard), read instead of printed ----

    /** Every [EvalCardLog] line printed while this harness is open, in order. */
    val logLines = CopyOnWriteArrayList<String>()
    private val sinkBefore: (String) -> Unit = EvalCardLog.sink

    init {
        EvalCardLog.sink = { logLines.add(it) }
    }

    // ---- the real objects, built after the statics are in place ----

    /** AuthManager.signOut deletes <filesDir>/timeseries: point it at a temp directory, never the working directory. */
    private val filesDir = Files.createTempDirectory("evalcard-harness").toFile()

    val holder = ScanSessionHolder()

    /**
     * The REAL per-scan record of the session a scan started in (order 2026-10-14), over an in-memory preferences file
     * (declared substitute for the on-device file, chosen when the module lacked Robolectric - a test dependency since 3284cca). [scanPrefs] is the file; a test
     * that wants "the process restarted" builds a second [ScanSessionRecords] over `scanPrefs.sameFileAs()`.
     */
    val scanPrefs = InMemorySharedPreferences()
    val records = ScanSessionRecords(scanPrefs, holder)

    /**
     * What `StressTestService.startScan` does the moment it mints a scan's id: the REAL [ScanSessionRecords.recordAtScanStart]
     * (the service cannot run on the JVM, so its one call site is pinned as text in `ScanSessionAtScanStartSourceShapeTest`).
     */
    fun startScan(scanId: String) = records.recordAtScanStart(scanId)
    val authManager = AuthManager(
        database = mock<NeuralCheckDatabase>(),
        appContext = mock<Context> { on { this.filesDir } doReturn filesDir },
        scanSessionHolder = holder
    )
    val service = FirebaseAnalysisService()

    /**
     * The technician form's three callables (`getTechnicianFormConfig`, `submitTechnicianForm`, `recordReportFirstOpened`) are behind `TECH_FORM_TEXTS_APPROVED` at the point the request leaves the phone
     * (A-P6-08, rule 35). That guard is TRUE since 2026-10-23, so the REAL gate is open and this changes nothing; it stays so that a test says what it relies on (rule 37: one path through production with
     * nothing replaced). A test that wants the gate shut calls [closeFormCalls].
     */
    fun openFormCalls() {
        service.formCallsApproved = { true }
    }

    /** The gate of the form's three callables, shut (the real one reads the guard, which is true: this is a test's way to see what a shut gate does - nothing leaves, nothing is printed). */
    fun closeFormCalls() {
        service.formCallsApproved = { false }
    }

    /**
     * The repeat scan's two callables (`getRepeatScanOffer`, `startRepeatScan`) are behind `REPEAT_TEXTS_APPROVED` at the point the request leaves the phone (A-P6-07, rule 35). That guard is TRUE since
     * 2026-10-23, so the REAL gate is open and this changes nothing; it stays so that a test says what it relies on (rule 37). A test that wants the gate shut calls [closeRepeatCalls].
     */
    fun openRepeatCalls() {
        service.repeatCallsApproved = { true }
    }

    /** The gate of the repeat scan's two callables, shut (the real one reads the guard, which is true: this is a test's way to see what a shut gate does - nothing leaves, nothing is printed). */
    fun closeRepeatCalls() {
        service.repeatCallsApproved = { false }
    }

    /**
     * The consent screen's one callable (`recordScanConsent`) is behind `CONSENT_TEXTS_APPROVED` at the point the request leaves the phone (A-P6-12, rule 35). That guard is TRUE since 2026-10-23, so the REAL gate is
     * open and this changes nothing; it stays so that a test says what it relies on (rule 37). A test that wants the gate shut calls [closeConsentCalls].
     */
    fun openConsentCalls() {
        service.consentCallsApproved = { true }
    }

    /** The gate of the consent callable, shut (the real one reads the guard, which is true: this is a test's way to see what a shut gate does - nothing leaves, nothing is printed). */
    fun closeConsentCalls() {
        service.consentCallsApproved = { false }
    }

    /**
     * The maintenance link's two callables (`listOpenMaintenanceBefores`, `confirmMaintenanceAfterLink`) are behind `PAIR_LIST_TEXTS_APPROVED` at the point the request leaves the phone (order of 2026-10-22,
     * rule 35). That guard is TRUE since Hasan approved the texts on 2026-10-22, so the REAL gate is open and this changes nothing; it stays so that a test says what it relies on (rule 37: one path through production
     * with nothing replaced). A test that wants the gate shut calls [closePairCalls].
     */
    fun openPairCalls() {
        service.pairCallsApproved = { true }
    }

    /** The gate of the maintenance link's two callables, shut (the real one reads the guard, which is true: this is a test's way to see what a shut gate does - nothing leaves, nothing is printed). */
    fun closePairCalls() {
        service.pairCallsApproved = { false }
    }

    private val savedLocale: Locale = Locale.getDefault()
    private val preScanViewModels = mutableListOf<PreScanViewModel>()

    private val scanRepository: ScanRepository = mock {
        // QRScannerViewModel.preRepairScans switches to this on every uid event.
        on { getScansByTypeForUser(any(), any()) } doReturn flowOf(emptyList())
    }

    /** The one canceller both the owner's button and the customer's leave go through (batch 2, item 2), over the real [service]. */
    val canceller = SessionCanceller(service)

    fun joinViewModel() = JoinSessionViewModel(
        context = viewModelContext ?: mock<Context>(),
        analysisService = service,
        authManager = authManager,
        sessionHolder = holder
    )

    fun scannerViewModel() = QRScannerViewModel(
        context = viewModelContext ?: mock<Context>(),
        scanRepository = scanRepository,
        authManager = authManager,
        analysisService = service,
        sessionHolder = holder
    )

    fun preScanViewModel(): PreScanViewModel = PreScanViewModel(
        context = richContext(),
        authManager = authManager,
        firebaseService = service,
        sessionHolder = holder,
        subscriptionRefreshBus = SubscriptionRefreshBus(),
        warningsHolder = PreScanWarningsHolder()
    ).also { preScanViewModels.add(it) }

    fun evaluationViewModel(dao: SessionEvaluationDao) = TechnicianEvaluationViewModel(dao, holder, records)

    /** The real sign-out of the real AuthManager (the call the Settings / Profile buttons make). */
    fun signOut() = runBlocking { authManager.signOut() }

    /**
     * Waits for the draft AND for the log line `bind` prints right after it: the IO coroutine writes the draft
     * first and the line second, so a test that stopped at the draft could leave the line to arrive after it ended.
     */
    fun awaitDraft(vm: TechnicianEvaluationViewModel) =
        awaitCondition("the draft and its log line") {
            vm.draft != null && logLines.any { it.startsWith("eval-bind-draft") }
        }

    /**
     * A [Context] complete enough for the real `checkConditions()` loop PreScanViewModel's init starts
     * (same construction as PreScanViewModelTrialBannerSessionTest): Strings come back empty, Context-returning
     * calls return the same mock, and the types LocaleHelper.wrap reads are stubbed.
     */
    private fun richContext(): Context {
        val prefs = mock<SharedPreferences> { on { getString(anyOrNull(), anyOrNull()) } doReturn "ar" }
        val resources = mock<Resources>()
        lateinit var ctx: Context
        ctx = Mockito.mock(Context::class.java, Answer<Any?> { inv: InvocationOnMock ->
            when (inv.method.returnType) {
                String::class.java -> ""
                Context::class.java -> ctx
                SharedPreferences::class.java -> prefs
                Resources::class.java -> resources
                else -> Mockito.RETURNS_DEFAULTS.answer(inv)
            }
        })
        return ctx
    }

    override fun close() {
        // The conditions loop is `while (true) { …; delay(2000) }`; stop it.
        preScanViewModels.forEach { it.viewModelScope.cancel() }
        preScanViewModels.clear()
        Locale.setDefault(savedLocale)
        EvalCardLog.sink = sinkBefore
        filesDir.deleteRecursively()
        functionsStatic.close()
        authStatic.close()
        firestoreStatic.close()
    }
}

/**
 * Declared substitute for Room (see [RealJoinPathHarness]): an in-memory [SessionEvaluationDao].
 * [getByScanIdCalls] is how a test sees that `bind` did (or did not) start its IO work.
 */
internal class FakeSessionEvaluationDao : SessionEvaluationDao {
    val rows = ConcurrentHashMap<String, SessionEvaluationEntity>()
    @Volatile var getByScanIdCalls = 0

    /** When set, [getByScanId] throws it (after counting the call). */
    @Volatile var readFailure: Throwable? = null

    /**
     * When set, [getByScanId] blocks on it (after counting the call, before it answers) — a read that has started and
     * not returned, the state the `eval-bind-read-start` line without an `eval-bind-read-done` line stands for.
     * Order 2026-10-14. The caller counts it down to let the read return.
     */
    @Volatile var readGate: java.util.concurrent.CountDownLatch? = null

    override suspend fun upsert(evaluation: SessionEvaluationEntity) {
        rows[evaluation.scanId] = evaluation
    }

    override suspend fun getByScanId(scanId: String): SessionEvaluationEntity? {
        getByScanIdCalls++
        readGate?.await()
        readFailure?.let { throw it }
        return rows[scanId]
    }

    override suspend fun markSubmitted(scanId: String) {
        rows[scanId]?.let { rows[scanId] = it.copy(submitted = true) }
    }

    override suspend fun markFinalFailure(scanId: String) {
        rows[scanId]?.let { rows[scanId] = it.copy(finalFailure = true) }
    }

    override suspend fun deleteByScanId(scanId: String) {
        rows.remove(scanId)
    }

    override suspend fun getPending(): List<SessionEvaluationEntity> =
        rows.values.filter { !it.submitted && !it.finalFailure }.sortedBy { it.shownAtMs }
}

/**
 * Waits for [cond] (the IO coroutine `TechnicianEvaluationViewModel.bind` launches on `Dispatchers.IO`
 * cannot be given a test dispatcher — it is hard-coded). Fails with [what] on timeout.
 */
internal fun awaitCondition(what: String, timeoutMs: Long = 10_000, cond: () -> Boolean) {
    val deadline = System.nanoTime() + timeoutMs * 1_000_000
    while (!cond()) {
        if (System.nanoTime() > deadline) fail("timed out after ${timeoutMs}ms waiting for $what")
        Thread.sleep(2)
    }
}
