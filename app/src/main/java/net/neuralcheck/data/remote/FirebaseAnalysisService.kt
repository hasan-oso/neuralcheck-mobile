package net.neuralcheck.data.remote

import android.net.Uri
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import net.neuralcheck.data.remote.model.AiAnalysisResponse
import net.neuralcheck.data.remote.model.BehaviorFingerprint
import net.neuralcheck.data.remote.model.categoriesMissingLetterCeilingReason
import net.neuralcheck.data.remote.model.areIssuesDeclared
import net.neuralcheck.data.remote.model.isHarnessErrorDeclared
import net.neuralcheck.data.remote.model.isNarrativeIncompleteDeclared
import net.neuralcheck.data.remote.model.isNoDataAtAllDeclared
import net.neuralcheck.data.remote.model.isPartialAssessmentDeclared
import java.io.IOException
import java.text.Normalizer
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random
import net.neuralcheck.domain.consent.ConsentLocale
import net.neuralcheck.domain.consent.ConsentPartRead
import net.neuralcheck.domain.consent.ConsentWire
import net.neuralcheck.domain.form.ReportFirstOpenedWire
import net.neuralcheck.domain.form.TechnicianFormWire
import net.neuralcheck.domain.pair.PairLinkWire
import net.neuralcheck.domain.repeat.RepeatScanWire
import net.neuralcheck.presentation.screen.CONSENT_TEXTS_APPROVED
import net.neuralcheck.presentation.screen.PAIR_LIST_TEXTS_APPROVED
import net.neuralcheck.presentation.screen.REPEAT_TEXTS_APPROVED
import net.neuralcheck.presentation.screen.TECH_FORM_TEXTS_APPROVED
import net.neuralcheck.util.EvalCardLog

/** Hard wall-clock cap for any single callable. The Firebase SDK's own
 *  default timeout is 70 s — uncomfortable for a UI surfaced as a loading
 *  spinner. Caps every callable + storage upload at 30 s; on expiry we
 *  raise [IOException] so callers handle it the same way as a connection
 *  failure rather than a generic [TimeoutCancellationException]. */
private const val CALLABLE_TIMEOUT_MS = 30_000L
/** analyzeDevice runs the v5 precomputed pipeline on the backend
 *  (DeepSeek thinking-mode ~137 s + language validator targeted fix
 *  30–60 s + Firestore write). End-to-end p99 sits at 150–200 s; the
 *  Cloud Function itself is provisioned at timeoutSeconds=300, so we
 *  cap the client side at the same 300 s. Two independent caps are
 *  enforced and either can fire:
 *    1. HttpsCallable.timeout property (Firebase Functions SDK level)
 *       set to 300_000 ms at the call site below.
 *    2. This coroutine `withTimeout` wrapper, also 300_000 ms.
 *  They're redundant — the inner SDK timeout typically fires first
 *  with a cleaner exception — but the outer coroutine cap guarantees
 *  the suspend point unblocks even if a future SDK upgrade drops the
 *  inner one. Past 300 s, treat the call as a real outage. */
private const val ANALYZE_DEVICE_TIMEOUT_MS = 300_000L
/** Pre-AI cache lookup: read `reports/{scanId}` from Firestore to see if
 *  a previous attempt's result already lives in the cloud. Should be fast
 *  (<2 s on warm network) — if it stalls past 8 s we abandon the cache
 *  and fall through to the live call so a slow Firestore link doesn't
 *  delay every retry. */
private const val REPORT_FETCH_TIMEOUT_MS = 8_000L
private const val UPLOAD_TIMEOUT_MS = 60_000L  // larger because logo upload is slower

/**
 * Talks to the backend via Firebase callable Cloud Functions. Chosen over a
 * plain HTTPS API because:
 *   - No URL, JWT plumbing, or CORS config — the Firebase SDK handles identity
 *     end-to-end against `request.auth` on the server.
 *   - Same credentials as Firestore/Storage (already wired up for sync).
 *   - Works in both flavors with no `BuildConfig.API_BASE_URL` branch.
 *
 * The DTOs ([BehaviorFingerprint], [AiAnalysisResponse]) are serialised via
 * Gson on both legs: Kotlin data class → JSON string → `Map<String, Any>` for
 * the outbound call, and `Any` result → JSON string → data class on the way
 * back. Callable Functions require plain maps/lists, so the round-trip through
 * Gson is the simplest way to reuse our existing DTOs without teaching the
 * SDK about Kotlin types. `@SerializedName` annotations on the response
 * (e.g. `predicted_problems`) still apply — we call the default Gson so the
 * mapping is direct.
 */
@Singleton
class FirebaseAnalysisService @Inject constructor() {

    private val functions: FirebaseFunctions = Firebase.functions
    private val gson: Gson = AnalysisRequestSerializer.gson

    /**
     * A-P6-07: the repeat scan's answers say «not applicable» with a JSON null (`firstScanStartTemp`, `reason`, `childSessionId`, `childStatus`: contract A4 (5)), and the default [gson] above drops a null map value
     * when it builds a tree (no `serializeNulls()`: `AnalysisRequestSerializer`), which would turn the server's null into a missing key - two states that [net.neuralcheck.domain.repeat.RepeatOfferRead] keeps apart
     * (rules 22, 25). So the two repeat calls build their tree with this one, which keeps the nulls the server sent. It is derived from [gson] (`newBuilder()`: the one configuration, plus `serializeNulls()`) and not
     * built from scratch: `AppRequestPayloadGoldenTest` keeps this file from owning a Gson of its own.
     */
    private val gsonKeepingNulls: Gson = gson.newBuilder().serializeNulls().create()

    /**
     * Asserts there is a signed-in Firebase user before dispatching any
     * callable. Without an authenticated session the SDK would still POST to
     * the Cloud Functions endpoint without an `Authorization: Bearer` header;
     * the server rejects that with `UNAUTHENTICATED` (HTTP 401). Fail-fast
     * here turns that into a localized, recoverable error that the ViewModel
     * layer can route to `LoginScreen` instead of a generic network failure.
     */
    private fun requireAuth() {
        val uid = Firebase.auth.currentUser?.uid
        if (uid.isNullOrBlank()) {
            throw IllegalStateException(
                "Firebase Auth user is null — cannot invoke callable function"
            )
        }
    }

    /**
     * Calls the `analyzeDevice` callable with the fingerprint and returns the
     * AI verdict. Throws on any network / Functions error — callers should
     * catch and surface an error card with a retry affordance.
     *
     * When [sessionId] is non-null, this is a customer-device scan spawned
     * from a shop-owner QR code. The Cloud Function accepts the call as
     * authenticated-by-session (server validates the code against
     * `scanSessions/{id}`) instead of requiring a Firebase Auth user, so we
     * skip [requireAuth] in that path — the customer device may not be
     * signed in at all.
     */
    suspend fun analyzeDevice(
        scanId: String,
        fingerprint: BehaviorFingerprint,
        language: String,
        sessionId: String? = null
    ): AiAnalysisResponse {
        if (sessionId.isNullOrBlank()) requireAuth()
        // Callable Functions accept Map/List/primitive payloads, not arbitrary
        // Kotlin classes, so flatten via Gson first — in AnalysisRequestSerializer,
        // the same function the golden request-payload sample is generated with.
        val fingerprintMap: Map<String, Any> = AnalysisRequestSerializer.fingerprintToMap(fingerprint)

        val data = hashMapOf<String, Any>(
            "scanId" to scanId,
            "fingerprint" to fingerprintMap,
            "language" to language,
            // Phase 1.11 — opt this build into the v5 precomputed pipeline on
            // the backend (runAnalysisInternal). The shim returns snake_case
            // identical to the V3 envelope so AiAnalysisResponse + every
            // nested DTO deserialises unchanged. Flip back to false here if
            // we ever need to A/B against the legacy path without rebuilding
            // the Cloud Function.
            "usePrecomputed" to true
        )
        if (!sessionId.isNullOrBlank()) {
            data["sessionId"] = sessionId
        }

        val result = callWithTimeout("analyzeDevice", ANALYZE_DEVICE_TIMEOUT_MS) {
            // Firebase Functions Android SDK defaults the per-callable
            // timeout to 70 s — far below the v5 path's 150–200 s p99.
            // Bumping HttpsCallable.timeout to 300 s matches the backend
            // Function's `timeoutSeconds=300`, so the SDK waits long
            // enough to receive the actual response instead of cancelling
            // mid-flight while Gemini/DeepSeek is still computing the
            // verdict. V3 path (cached / simple AI calls) finishes well
            // under this budget so no regression there.
            val callable = functions.getHttpsCallable("analyzeDevice").apply {
                // HttpsCallable.timeout is a Java-style getter/setter pair on
                // the Firebase Functions Android SDK and the setter takes a
                // (Long, TimeUnit) pair, so the Kotlin property syntax
                // `timeout = 300_000` doesn't auto-synthesize. Call the
                // explicit setter instead. 5 minutes = backend Function's
                // timeoutSeconds=300.
                setTimeout(300_000L, TimeUnit.MILLISECONDS)
            }
            callable.call(data).await()
        }
        val resultJson = gson.toJson(result.data)
        val parsed = gson.fromJson(resultJson, AiAnalysisResponse::class.java)
        // See AiAnalysisResponse.categoriesMissingLetterCeilingReason's doc —
        // computed against the exact same JSON string just parsed, since
        // Gson's typed AiCategoryV7 decode alone cannot tell an absent
        // letterCeilingReason key apart from an explicit JSON null. Same
        // treatment for narrativeIncompleteDeclared — see that field's doc.
        return parsed.copy(
            categoriesMissingLetterCeilingReason = categoriesMissingLetterCeilingReason(resultJson),
            narrativeIncompleteDeclared = isNarrativeIncompleteDeclared(resultJson),
            issuesDeclared = areIssuesDeclared(resultJson),
            harnessErrorDeclared = isHarnessErrorDeclared(resultJson),
            noDataAtAllDeclared = isNoDataAtAllDeclared(resultJson),
            partialAssessmentDeclared = isPartialAssessmentDeclared(resultJson)
        )
    }

    /**
     * Try to fetch a previously-completed AI report from Firestore, keyed by
     * [scanId]. The Cloud Function writes the verdict to `reports/{scanId}`
     * the moment it finishes, so a subsequent retry — for example after the
     * client timed out before receiving the response, or after the user
     * killed the screen and reopened it — can rehydrate the result for the
     * cost of a single Firestore read instead of paying for a fresh Gemini
     * call.
     *
     * Returns null in three scenarios:
     *   - The doc doesn't exist (no prior successful run).
     *   - The doc exists but [isCachedReportComplete] says it isn't (e.g.
     *     the function wrote partial state before failing).
     *   - The Firestore read itself fails (offline, permission, timeout). The
     *     caller falls through to a live call.
     */
    suspend fun fetchExistingReport(scanId: String): AiAnalysisResponse? = try {
        val doc = callWithTimeout("fetchExistingReport", REPORT_FETCH_TIMEOUT_MS) {
            Firebase.firestore.collection("reports").document(scanId).get().await()
        }
        if (!doc.exists()) {
            android.util.Log.d("NeuralCheck.Report", "fetchExistingReport($scanId): doc does not exist")
            null
        } else {
            val data = doc.data
            android.util.Log.d(
                "NeuralCheck.Report",
                "fetchExistingReport($scanId): doc keys=${data?.keys}"
            )
            val cacheGateOk = isCachedReportComplete(data)
            if (data != null && cacheGateOk) {
                val flat = flattenReportDocument(data)
                val json = gson.toJson(flat)
                // Same treatment as analyzeDevice() above — see
                // AiAnalysisResponse.categoriesMissingLetterCeilingReason's
                // doc. Computed against `json`, the exact string handed to
                // fromJson below (the flattened-wrapper form), not the raw
                // `data` map, since that's what AiCategoryV7's typed decode
                // itself reads.
                val parsed = gson.fromJson(json, AiAnalysisResponse::class.java)
                    .copy(
                        categoriesMissingLetterCeilingReason = categoriesMissingLetterCeilingReason(json),
                        narrativeIncompleteDeclared = isNarrativeIncompleteDeclared(json),
                        issuesDeclared = areIssuesDeclared(json),
                        harnessErrorDeclared = isHarnessErrorDeclared(json),
                        noDataAtAllDeclared = isNoDataAtAllDeclared(json),
                        partialAssessmentDeclared = isPartialAssessmentDeclared(json),
                        // Order 2026-09-26 (القرار ١٠٢ البند ١) — read off
                        // the RAW `data` map, not `json`/`flat`: `createdAt`
                        // is a `com.google.firebase.Timestamp` on this SDK's
                        // `DocumentSnapshot.getData()` map, which Gson's
                        // normal JSON round-trip cannot represent correctly
                        // (see AiAnalysisResponse.createdAtMs's own doc).
                        createdAtMs = extractCreatedAtMs(data)
                    )
                android.util.Log.d(
                    "NeuralCheck.Report",
                    "fetchExistingReport($scanId): parsed " +
                        "summary=${parsed.summary?.take(40)} " +
                        "details=${parsed.details?.take(40)} " +
                        "issues=${parsed.issues?.size} " +
                        "predictedProblems=${parsed.predictedProblems?.size} " +
                        "categoryScores=${parsed.categoryScores?.size}"
                )
                parsed
            } else {
                android.util.Log.d(
                    "NeuralCheck.Report",
                    "fetchExistingReport($scanId): cache gate failed " +
                        "(pipeline=${data?.get("pipeline")} cacheGateOk=$cacheGateOk)"
                )
                null
            }
        }
    } catch (t: Throwable) {
        // Swallow EVERYTHING here — this is a cache-warm best-effort, never a
        // hard requirement. A failed lookup just means we pay for the live
        // call, never that the user sees an error banner.
        android.util.Log.w(
            "NeuralCheck.Report",
            "fetchExistingReport($scanId) failed: ${t.javaClass.simpleName}: ${t.message}"
        )
        null
    }

    /**
     * Wraps a Firebase callable invocation with [CALLABLE_TIMEOUT_MS] so a
     * stalled connection (captive portal, deep packet inspection, server
     * overload) surfaces as a clean [IOException] rather than a 70-second
     * spinner that the user assumes is a frozen app and force-quits.
     * Re-throws cancellation so coroutine cleanup still works correctly.
     */
    private suspend inline fun <T> callWithTimeout(
        name: String,
        timeoutMs: Long = CALLABLE_TIMEOUT_MS,
        crossinline block: suspend () -> T
    ): T = try {
        withTimeout(timeoutMs) { block() }
    } catch (t: TimeoutCancellationException) {
        throw IOException("Callable '$name' timed out after ${timeoutMs}ms", t)
    }

    /**
     * Shop-owner side: asks the backend to mint a fresh scan session.
     * Returns a human-typeable short code (e.g. "NC-7429") that the owner
     * shows to the customer alongside a QR. The session expires
     * server-side after [ScanSession.expiresInMinutes] minutes — until
     * then any device can join it by posting the code to
     * [joinScanSession].
     */
    suspend fun createScanSession(): ScanSession {
        requireAuth()
        val result = callWithTimeout("createScanSession") {
            functions.getHttpsCallable("createScanSession").call().await()
        }
        val json = gson.toJson(result.data)
        // an answer with no data is Gson's null: it is an answer with every field absent (the owner's screen names that a failure), not a NullPointerException
        return gson.fromJson(json, ScanSession::class.java) ?: ScanSession()
    }

    /**
     * THE exit-point gate of the technician's form's three callables (A-P6-08; rule 35: the permit is enforced where the request is sent, not where sending is requested). The three functions
     * (`getTechnicianFormConfig`, `submitTechnicianForm`, `recordReportFirstOpened`) are not deployed on production yet, so while `TECH_FORM_TEXTS_APPROVED` is false none of them reaches
     * `getHttpsCallable` - whoever asks (the form's view model behind the screen's guard, or the two report-first-opened call sites, which are NOT behind the screen) gets
     * [FormCallsNotApprovedException] and nothing leaves the phone. A test opens it for the real-path tests; the default reads the real guard, and one test runs it unopened.
     */
    internal var formCallsApproved: () -> Boolean = { TECH_FORM_TEXTS_APPROVED }

    /** Whether the form's callables are open at this moment (the same gate [requireFormCallsApproved] enforces): lets a caller skip work that exists only for them. */
    fun formCallsOpen(): Boolean = formCallsApproved()

    private fun requireFormCallsApproved(callable: String) {
        if (!formCallsApproved()) throw FormCallsNotApprovedException(callable)
    }

    /**
     * The technician's form's configuration for one session (A-P6-08; contract A5: `getTechnicianFormConfig`, E6). The request is `{sessionId}` - the server refuses a call without it
     * (`FORM_SESSION_ID_REQUIRED`) [SRV technicianForm.js:150-157, copy `evidence/run-2026-10-18/technician-form-server-wire-neuralcheck-backend-05275ba.md`]. Returns the answer as the raw JSON
     * ([TechnicianFormConfigRead] reads it; nothing is parsed into a class here). A JSON null or an answer with no data is a JSON null. A refusal is thrown as the SDK's own
     * `FirebaseFunctionsException`, untouched. Needs a signed-in owner. Behind [formCallsApproved].
     */
    suspend fun getTechnicianFormConfig(sessionId: String): JsonElement {
        requireFormCallsApproved(TechnicianFormWire.CONFIG_CALLABLE)
        requireAuth()
        val data = hashMapOf(TechnicianFormWire.KEY_SESSION_ID to sessionId)
        val result = callWithTimeout(TechnicianFormWire.CONFIG_CALLABLE) {
            functions.getHttpsCallable(TechnicianFormWire.CONFIG_CALLABLE).call(data).await()
        }
        return gson.toJsonTree(result.data)
    }

    /**
     * Sends the technician's form (A-P6-08; contract A5: `submitTechnicianForm`, E6). [request] is [net.neuralcheck.domain.form.TechnicianFormRequest.build]'s map, null values explicit. A refusal is thrown as
     * the SDK's own `FirebaseFunctionsException`, untouched (its `details.reasonCode` is what the caller reads: fifteen `FORM_*` codes, of which the contract copy names one). Nothing is read from the
     * success answer: the server's is `{sessionId, slot, formState}` and the app reads none of it. Needs a signed-in owner. Behind [formCallsApproved].
     */
    suspend fun submitTechnicianForm(request: Map<String, Any?>) {
        requireFormCallsApproved(TechnicianFormWire.SUBMIT_CALLABLE)
        requireAuth()
        callWithTimeout(TechnicianFormWire.SUBMIT_CALLABLE) {
            functions.getHttpsCallable(TechnicianFormWire.SUBMIT_CALLABLE).call(request).await()
        }
    }

    /**
     * The report was shown for the first time (A-P6-08, the form's blindness; see [net.neuralcheck.domain.form.ReportFirstOpenedWire]): `{sessionId}` to `recordReportFirstOpened`, which the server accepts from the
     * session's owner or its joined device. Returns the answer as the raw JSON ([net.neuralcheck.domain.form.ReportFirstOpenedRead] reads it). A refusal is thrown as the SDK's own
     * `FirebaseFunctionsException`, untouched. Needs a signed-in user (the owner's account, or the customer device's anonymous one). Behind [formCallsApproved] - the one gate of the two call sites.
     */
    suspend fun recordReportFirstOpened(sessionId: String): JsonElement {
        requireFormCallsApproved(ReportFirstOpenedWire.CALLABLE)
        requireAuth()
        val data = hashMapOf(ReportFirstOpenedWire.KEY_SESSION_ID to sessionId)
        val result = callWithTimeout(ReportFirstOpenedWire.CALLABLE) {
            functions.getHttpsCallable(ReportFirstOpenedWire.CALLABLE).call(data).await()
        }
        return gson.toJsonTree(result.data)
    }

    /**
     * THE exit-point gate of the repeat scan's two callables (A-P6-07; rule 35: the permit is enforced where the request is sent, not where sending is requested). `getRepeatScanOffer` and `startRepeatScan`
     * are the server's half of the row (neuralcheck-backend `cccd027`, inside `fd730ac`), NOT deployed on production, so while `REPEAT_TEXTS_APPROVED` is false neither reaches `getHttpsCallable` - whoever asks
     * (the technician's phone after its session completed, the scanned phone after its report) gets [RepeatCallsNotApprovedException] and nothing leaves the phone. A test opens it for the real-path tests; the
     * default reads the real guard, and one test runs it unopened.
     */
    internal var repeatCallsApproved: () -> Boolean = { REPEAT_TEXTS_APPROVED }

    /** Whether the repeat scan's callables are open at this moment (the same gate [requireRepeatCallsApproved] enforces): lets a caller skip work that exists only for them. */
    fun repeatCallsOpen(): Boolean = repeatCallsApproved()

    private fun requireRepeatCallsApproved(callable: String) {
        if (!repeatCallsApproved()) throw RepeatCallsNotApprovedException(callable)
    }

    /**
     * The repeat scan's offer for one (session, scan) pair (A-P6-07; contract A4 (5): `getRepeatScanOffer`, a name the server proposed). Authorised by the PAIR, so the scanned phone's anonymous account asks as
     * well as the session's owner. Returns the answer as the raw JSON ([net.neuralcheck.domain.repeat.RepeatOfferRead] reads it; nothing is parsed into a class here). A JSON null or an answer with no data is a
     * JSON null. A refusal is thrown as the SDK's own `FirebaseFunctionsException`, untouched. Needs a signed-in account. Behind [repeatCallsApproved].
     */
    suspend fun getRepeatScanOffer(sessionId: String, scanId: String): JsonElement {
        requireRepeatCallsApproved(RepeatScanWire.OFFER_CALLABLE)
        requireAuth()
        val result = callWithTimeout(RepeatScanWire.OFFER_CALLABLE) {
            functions.getHttpsCallable(RepeatScanWire.OFFER_CALLABLE).call(RepeatScanWire.offerRequest(sessionId, scanId)).await()
        }
        return gsonKeepingNulls.toJsonTree(result.data)
    }

    /**
     * Asks the server to start the repeat's child session for one (session, scan) pair (A-P6-07; contract A4 (4): `startRepeatScan`, PLAN-06:639). [startedBeforeCooling] is `true` only when the technician pressed
     * «ابدأ رغم ذلك» and is otherwise null (the key is then left out: the server stores its absence as null, not false). Returns the answer as the raw JSON ([net.neuralcheck.domain.repeat.RepeatStartRead]
     * reads it). A refusal is thrown as the SDK's own `FirebaseFunctionsException`, untouched. Needs a signed-in account. Behind [repeatCallsApproved].
     */
    suspend fun startRepeatScan(sessionId: String, scanId: String, startedBeforeCooling: Boolean?): JsonElement {
        requireRepeatCallsApproved(RepeatScanWire.START_CALLABLE)
        requireAuth()
        val result = callWithTimeout(RepeatScanWire.START_CALLABLE) {
            functions.getHttpsCallable(RepeatScanWire.START_CALLABLE).call(RepeatScanWire.startRequest(sessionId, scanId, startedBeforeCooling)).await()
        }
        return gsonKeepingNulls.toJsonTree(result.data)
    }

    /**
     * THE exit-point gate of the consent screen's one callable (A-P6-12; rule 35: the permit is enforced where the request is sent, not where sending is requested). `recordScanConsent` is the server's half of
     * the row (neuralcheck-backend `5ab5eaf`, inside `5361116`), NOT deployed on production, so while `CONSENT_TEXTS_APPROVED` is false it never reaches `getHttpsCallable` - whoever asks gets
     * [ConsentCallsNotApprovedException] and nothing leaves the phone. A test opens it for the real-path tests; the default reads the real guard, and one test runs it unopened. «لا أوافق» is NOT behind
     * this gate: it is `cancelScanSession`, which is deployed, and it is reachable only from the screen the guard keeps out.
     */
    internal var consentCallsApproved: () -> Boolean = { CONSENT_TEXTS_APPROVED }

    /** Whether the consent callable is open at this moment (the same gate [requireConsentCallsApproved] enforces). */
    fun consentCallsOpen(): Boolean = consentCallsApproved()

    private fun requireConsentCallsApproved(callable: String) {
        if (!consentCallsApproved()) throw ConsentCallsNotApprovedException(callable)
    }

    /**
     * The joined device records that its user accepted the consent text (A-P6-12; contract A8 (1): `recordScanConsent({sessionId, textVersion, locale})`, a name the server proposed). Returns the answer as the
     * raw JSON ([net.neuralcheck.domain.consent.ConsentAnswerRead] reads it; nothing is parsed into a class here). A refusal is thrown as the SDK's own `FirebaseFunctionsException`, untouched.
     *
     * Deliberately skips [requireAuth], like [cancelScanSession]: the caller is the customer device (an anonymous uid made at the join - see [joinScanSessionWithDeviceIdentity]); a device that joined with
     * NO identity is refused by the server with `CONSENT_NOT_AUTHENTICATED` (A8 (5)), and that answer is the one the app should read - a local `IllegalStateException` here would hide it. Behind [consentCallsApproved].
     */
    suspend fun recordScanConsent(sessionId: String, textVersion: Int, locale: ConsentLocale): JsonElement {
        requireConsentCallsApproved(ConsentWire.CALLABLE)
        val result = callWithTimeout(ConsentWire.CALLABLE) {
            functions.getHttpsCallable(ConsentWire.CALLABLE).call(ConsentWire.request(sessionId, textVersion, locale)).await()
        }
        return gsonKeepingNulls.toJsonTree(result.data)
    }

    /**
     * The owner's purpose chips (A-P6-03; contract A2: `setSessionPurpose`, "a new function for the owner; the change is accepted while the session is `pending`"). Asks the server to set
     * [purpose] on [sessionId], with the fault type [defectType] for a maintenance purpose (A2 addendum of 2026-10-22): the request is built by [SessionPurposeWire.request], which refuses - before
     * anything is sent - a maintenance purpose with no type and a type with `normal` ([PurposeRequestRejectedException]). A refusal of the server is thrown as the SDK's own
     * `FirebaseFunctionsException`, untouched (its `details.reasonCode` is what the caller reads - `PURPOSE_SESSION_NOT_PENDING` after the customer's phone joined).
     *
     * The answer's shape is unchanged and the app reads none of it but its key names ([SetSessionPurposeAnswer]). Needs a signed-in owner, like [createScanSession] (a real account makes the session). Nothing
     * calls this in production while `PURPOSE_CHIPS_TEXTS_APPROVED` is false.
     */
    suspend fun setSessionPurpose(sessionId: String, purpose: ScanPurposeChoice, defectType: DefectType?): SetSessionPurposeAnswer {
        val data = when (val built = SessionPurposeWire.request(sessionId, purpose, defectType)) {
            is PurposeRequestBuild.Built -> built.request
            is PurposeRequestBuild.Rejected -> throw PurposeRequestRejectedException(built.reason)
        }
        requireAuth()
        val result = callWithTimeout(SessionPurposeWire.CALLABLE) {
            functions.getHttpsCallable(SessionPurposeWire.CALLABLE).call(data).await()
        }
        val keys = (result.data as? Map<*, *>)?.keys?.map { it.toString() }?.toSortedSet()
        return SetSessionPurposeAnswer(replyKeys = keys)
    }

    /**
     * THE exit-point gate of the maintenance link's two callables (order of 2026-10-22; rule 35: the permit is enforced where the request is sent, not where sending is requested).
     * `listOpenMaintenanceBefores` and `confirmMaintenanceAfterLink` are the server's half of the row (S4 and S5 of the same order, NOT deployed on production), so while `PAIR_LIST_TEXTS_APPROVED`
     * is false neither reaches `getHttpsCallable` - whoever asks gets [PairCallsNotApprovedException] and nothing leaves the phone. A test opens it for the real-path tests; the default reads the real
     * guard, and one test runs it unopened.
     */
    internal var pairCallsApproved: () -> Boolean = { PAIR_LIST_TEXTS_APPROVED }

    /** Whether the maintenance link's callables are open at this moment (the same gate [requirePairCallsApproved] enforces): lets a caller skip work that exists only for them. */
    fun pairCallsOpen(): Boolean = pairCallsApproved()

    private fun requirePairCallsApproved(callable: String) {
        if (!pairCallsApproved()) throw PairCallsNotApprovedException(callable)
    }

    /**
     * The «before maintenance» scans the owner may link to the «after» session [sessionId] (contract A3 (2): `listOpenMaintenanceBefores({sessionId})`, a name the server proposed, ق33). Returns the answer as the
     * raw JSON ([net.neuralcheck.domain.pair.OpenBeforesRead] reads it; nothing is parsed into a class here). A JSON null or an answer with no data is a JSON null. A refusal is thrown as the SDK's own
     * `FirebaseFunctionsException`, untouched. Needs a signed-in owner. Behind [pairCallsApproved].
     */
    suspend fun listOpenMaintenanceBefores(sessionId: String): JsonElement {
        requirePairCallsApproved(PairLinkWire.LIST_CALLABLE)
        requireAuth()
        val result = callWithTimeout(PairLinkWire.LIST_CALLABLE) {
            functions.getHttpsCallable(PairLinkWire.LIST_CALLABLE).call(PairLinkWire.listRequest(sessionId)).await()
        }
        return gsonKeepingNulls.toJsonTree(result.data)
    }

    /**
     * Confirms the owner's choice of the «before» scan [beforeScanId] for the «after» session [sessionId] (contract A3 (3): `confirmMaintenanceAfterLink({sessionId, beforeScanId})`, a name the server proposed, ق33).
     * Returns the answer as the raw JSON ([net.neuralcheck.domain.pair.ConfirmAnswerRead] reads it). A refusal is thrown as the SDK's own `FirebaseFunctionsException`, untouched. Needs a signed-in owner. Behind [pairCallsApproved].
     */
    suspend fun confirmMaintenanceAfterLink(sessionId: String, beforeScanId: String): JsonElement {
        requirePairCallsApproved(PairLinkWire.CONFIRM_CALLABLE)
        requireAuth()
        val result = callWithTimeout(PairLinkWire.CONFIRM_CALLABLE) {
            functions.getHttpsCallable(PairLinkWire.CONFIRM_CALLABLE).call(PairLinkWire.confirmRequest(sessionId, beforeScanId)).await()
        }
        return gsonKeepingNulls.toJsonTree(result.data)
    }

    /**
     * Customer-device side: claims a pending scan session by its short
     * code so subsequent analyzeDevice calls from this device (which may
     * not have any Firebase Auth user) are tagged to the owner's session.
     * Deliberately skips [requireAuth] because the customer often lands
     * here from the LoginScreen's "I have a scan code" shortcut *before*
     * any sign-in.
     *
     * Since 2026-10-10 the two callers do NOT call this directly: they go
     * through [joinScanSessionWithDeviceIdentity], which signs the device in
     * anonymously first (when nobody is signed in) so the join carries a uid.
     * This function itself is unchanged — it still has no auth gate.
     */
    suspend fun joinScanSession(code: String): JoinSessionResult {
        val data = hashMapOf("code" to code)
        val result = callWithTimeout("joinScanSession") {
            functions.getHttpsCallable("joinScanSession").call(data).await()
        }
        // Order 2026-10-10 (permanent named log lines): the answer as the callable returned it, BEFORE Gson —
        // which flag keys are present and the raw type of each — then (below) what Gson made of it. Booleans,
        // kinds and hashed ids only; the joiner is this device's uid, hashed the way the server hashes it.
        val joiner = runCatching { Firebase.auth.currentUser?.uid }.getOrNull()
        EvalCardLog.joinResponseRaw(result.data, joiner)
        val json = gson.toJson(result.data)
        // gson turns a JSON null (an answer with no data) into null: that is an answer with every field absent (the join's named failure for a missing session id), not a NullPointerException
        val parsed = gson.fromJson(json, JoinSessionResult::class.java) ?: JoinSessionResult()
        EvalCardLog.joinResponseParsed(parsed.sessionId, parsed.ownerId, EvalCardLog.Flag.of(parsed.evaluationEnabled), joiner)
        // ق15: the shop's consent part, read once here and declared by name (`absent` is its own word, never `false`): the two join entry points route by it
        EvalCardLog.joinConsentPart(parsed.sessionId, EvalCardLog.Flag.of(parsed.consentEnabled))
        return parsed
    }

    /**
     * Cancels ONE named session: the owner's button (`cancelled_by_owner`) or the joined device leaving (`left_by_customer`; `consent_declined`
     * when the consent screen is built - decision D1). Batch 2, item 2. The request is `{sessionId, reason}`, both required (answer 1 of the
     * backend diagnosis at `bbb90a1`, relayed, not verified from this repository); the server decides who may send which reason.
     *
     * Deliberately skips [requireAuth], like [joinScanSession]: the caller is the owner (a real account) or the customer device (an
     * anonymous uid made at the join - see [joinScanSessionWithDeviceIdentity]); a device that joined with NO identity is refused by the server with
     * `SESSION_NOT_PARTICIPANT`, and that answer is the one the app should read - a local `IllegalStateException` here would hide it. (The owner's cancel shows its generic text; the
     * customer's leave leaves locally and prints `session-leave-local reason=not_participant` - batch 4, item 2, [SessionLeaver].)
     *
     * Returns the answer as the server sent it ([CancelSessionResult]; every field may be absent). Whether it IS a cancellation is
     * [CancelSessionResult.outcome]'s decision, made by the caller ([SessionCanceller]) - a success answer without `status: "cancelled"` is not a
     * cancellation. A refusal is thrown as the SDK's own `FirebaseFunctionsException`, untouched: its `details.reasonCode` is what the callers read.
     */
    suspend fun cancelScanSession(sessionId: String, reason: CancelReason): CancelSessionResult {
        val data = hashMapOf("sessionId" to sessionId, "reason" to reason.wire)
        val result = callWithTimeout("cancelScanSession") {
            functions.getHttpsCallable("cancelScanSession").call(data).await()
        }
        // gson turns a JSON null (an answer with no data) into null: that is an answer with every field absent, not a crash
        return gson.fromJson(gson.toJson(result.data), CancelSessionResult::class.java) ?: CancelSessionResult()
    }

    /**
     * feat/technician-evaluation: posts the technician's blind judgement
     * to the `submitEvaluation` callable (contract branch
     * feat/session-evaluation-contract). Deliberately skips [requireAuth]
     * — the caller is the customer device on an anonymous uid; the server
     * authorises via the payload's sessionId against scanSessions/{id},
     * mirroring the analyzeDevice billing pattern. A response carrying
     * `pending: true` is SUCCESS (the server completes gradeDistance via
     * a trigger once/if the report lands). Throws on network / callable
     * errors — callers keep the local row unsubmitted and retry later.
     */
    suspend fun submitEvaluation(payload: Map<String, Any>) {
        callWithTimeout("submitEvaluation") {
            functions.getHttpsCallable("submitEvaluation").call(payload).await()
        }
    }

    /** Returns the caller's current subscription snapshot. */
    suspend fun checkSubscription(): SubscriptionStatus {
        requireAuth()
        val result = callWithTimeout("checkSubscription") {
            functions.getHttpsCallable("checkSubscription").call().await()
        }
        val resultJson = gson.toJson(result.data)
        // Diagnostic: log the raw payload + parsed values side-by-side. When
        // a user reports "trial used" or "no scans" on a fresh account,
        // this is the only place that can prove whether the bug is on the
        // server (response missing the expected key) or on the client
        // (Gson @SerializedName alias not catching a snake_case variant).
        // Logged at INFO so it survives logcat verbosity filters.
        val parsed = gson.fromJson(resultJson, SubscriptionStatus::class.java)
        android.util.Log.i(
            "NeuralCheck.Sub",
            "checkSubscription raw=$resultJson → parsed plan='${parsed.plan}' " +
                "scansRemaining=${parsed.scansRemaining} scansTotal=${parsed.scansTotal} " +
                "isActive=${parsed.isActive}"
        )
        return parsed
    }

    /** Registers the freshly-authenticated user in Firestore. */
    suspend fun registerUser(name: String, phone: String, type: String): RegisterResult {
        requireAuth()
        val data = hashMapOf(
            "name" to name,
            "phone" to phone,
            "type" to type
        )
        val result = callWithTimeout("registerUser") {
            functions.getHttpsCallable("registerUser").call(data).await()
        }
        val resultJson = gson.toJson(result.data)
        return gson.fromJson(resultJson, RegisterResult::class.java)
    }

    /**
     * Redeems a promo code against the signed-in user's subscription. The
     * server validates the code, debits it, credits the reward (extra scans
     * and/or a free plan upgrade), and returns a [PromoResult]. The three
     * expected failure modes — invalid code, expired, already used by this
     * user — surface via [PromoResult.success] = false with [message] set to
     * a reason token ("invalid_code" / "expired" / "already_used"); the
     * calling ViewModel maps those tokens to localized copy.
     */
    suspend fun redeemPromoCode(code: String): PromoResult {
        requireAuth()
        val data = hashMapOf("code" to code)
        val result = callWithTimeout("redeemPromoCode") {
            functions.getHttpsCallable("redeemPromoCode").call(data).await()
        }
        val json = gson.toJson(result.data)
        return gson.fromJson(json, PromoResult::class.java)
    }

    /**
     * Persists the signed-in shop owner's storefront profile. Uploads
     * [logoUri] (when non-null) to `shops/{uid}/logo.jpg` in Cloud Storage,
     * then merges the resulting download URL plus the textual fields into
     * `users/{uid}` via the Firestore client SDK — no callable involved
     * because the writes are tightly scoped to the user's own doc and the
     * Storage rules already gate uploads to `request.auth.uid`.
     *
     * Allocates a [shopSlug] from [shopName] for use as the public profile
     * URL fragment (e.g. "mobile-house-aleppo"). Slug uniqueness is checked
     * once against the `users` collection; on collision a 4-digit numeric
     * suffix is appended. We accept the (vanishingly small) chance of a
     * second collision rather than recursing forever — the pattern matches
     * how most product-grade slugifiers degrade.
     *
     * Uses `set(payload, SetOptions.merge())` instead of `update()` so the
     * call also works the very first time (the `users/{uid}` doc may not yet
     * exist if the user signed up via a non-`registerUser` path or the
     * earlier registerUser write was lost).
     */
    suspend fun updateShopInfo(
        shopName: String,
        shopDescription: String,
        shopCity: String,
        shopPhone: String,
        shopAddress: String,
        logoUri: Uri?,
        customSlug: String? = null
    ) {
        requireAuth()
        val uid = Firebase.auth.currentUser!!.uid

        // 1. Logo upload — only when the user picked a new image. Uses
        //    `putFile` so the SDK streams the content URI directly without
        //    us having to copy the bytes into a temp buffer. Wrapped in a
        //    longer timeout because slow networks legitimately take >30 s
        //    to upload a multi-megabyte image.
        val logoUrl: String? = if (logoUri != null) {
            val ref = Firebase.storage.reference.child("shops/$uid/logo.jpg")
            callWithTimeout("uploadShopLogo", UPLOAD_TIMEOUT_MS) {
                ref.putFile(logoUri).await()
                ref.downloadUrl.await().toString()
            }
        } else null

        // 2. Slug resolution — three branches:
        //    a) [customSlug] supplied & valid → user chose their own URL.
        //       Re-check uniqueness here; the ViewModel's earlier check is
        //       advisory because Firestore offers no transactional uniqueness
        //       constraint and another user could land the same slug between
        //       the VM check and our save. On collision we suffix with a
        //       random 4-digit number rather than failing the whole save —
        //       matching the auto-generated path's degradation pattern.
        //    b) [customSlug] = null but a user already has one stored: we
        //       must NOT overwrite it with a fresh auto-gen because that
        //       would silently change their public URL on every shop edit.
        //       So we skip the slug field from the payload entirely (the
        //       Firestore merge leaves the existing value intact).
        //    c) [customSlug] = null and no slug stored yet: auto-generate
        //       from shop name, falling back to a random "shop-XXXXX" id
        //       when the name is pure-Arabic / non-Latin.
        val slugToWrite: String? = when {
            !customSlug.isNullOrBlank() -> ensureSlugUnique(uid, customSlug)
            else -> {
                val existing = readExistingSlug(uid)
                if (existing.isNullOrBlank()) ensureSlugUnique(uid, slugifyShopName(shopName)) else null
            }
        }

        // 3. Merge payload. We avoid wiping `shopLogoUrl` when the user
        //    saves without picking a new image — the existing URL stays put
        //    because we only add the field when [logoUrl] is non-null.
        //    Same shape applies to slug above.
        val payload = mutableMapOf<String, Any>(
            "shopName" to shopName,
            "shopDescription" to shopDescription,
            "shopCity" to shopCity,
            "shopPhone" to shopPhone,
            "shopAddress" to shopAddress
        )
        if (logoUrl != null) payload["shopLogoUrl"] = logoUrl
        if (slugToWrite != null) payload["shopSlug"] = slugToWrite

        callWithTimeout("updateShopInfo.firestoreSet") {
            Firebase.firestore.collection("users").document(uid)
                .set(payload, SetOptions.merge())
                .await()
        }
    }

    private suspend fun readExistingSlug(uid: String): String? = try {
        Firebase.firestore.collection("users").document(uid)
            .get().await().getString("shopSlug")
    } catch (_: Throwable) { null }

    /**
     * Returns [base] when no other user currently owns it, otherwise appends
     * a 4-digit random suffix. We accept the (vanishingly small) chance of a
     * second collision rather than recursing forever — the pattern matches
     * how most product-grade slugifiers degrade.
     */
    private suspend fun ensureSlugUnique(uid: String, base: String): String = try {
        val firstHit = Firebase.firestore.collection("users")
            .whereEqualTo("shopSlug", base)
            .limit(2)
            .get()
            .await()
        val takenByOther = firstHit.documents.any { it.id != uid }
        if (takenByOther) "$base-${Random.nextInt(1000, 9999)}" else base
    } catch (t: Throwable) {
        // Phase 1.15 hotfix — Cause 2 of the shop-setup permission_denied.
        // The per-user Firestore read rule (`users/{uid}: uid == userId`)
        // REJECTS this collection query for non-admins: it can match OTHER
        // users' docs, which the rule forbids, so `.get()` throws
        // permission_denied on first-time setup. Unwrapped, it failed the
        // whole save. Mirror isSlugTaken (ViewModels.kt): swallow and assume
        // the slug is free so the direct `.set()` write below still runs.
        //
        // CAVEAT (reported to the team): this query can NEVER succeed under
        // the current rules, so the client cannot actually enforce slug
        // uniqueness — and the save is a direct client write, not a callable,
        // so nothing server-side dedupes it either. True uniqueness needs a
        // server-side guarantee (a `shopSlugs/{slug}` registry claimed in a
        // transaction, or moving the slug write into a Cloud Function).
        android.util.Log.w("NeuralCheck", "ensureSlugUnique query failed: ${t.message}", t)
        base
    }

    private fun slugifyShopName(name: String): String {
        val ascii = Normalizer.normalize(name.trim(), Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .take(50)
        return if (ascii.isBlank()) "shop-${Random.nextInt(1000, 99999)}" else ascii
    }

    /** Records a pending Syrian Cash payment reference for back-office review. */
    suspend fun requestPayment(plan: String, reference: String): PaymentResult {
        requireAuth()
        val data = hashMapOf(
            "plan" to plan,
            "reference" to reference
        )
        val result = callWithTimeout("requestPayment") {
            functions.getHttpsCallable("requestPayment").call(data).await()
        }
        val resultJson = gson.toJson(result.data)
        return gson.fromJson(resultJson, PaymentResult::class.java)
    }
}

/**
 * Pure gate logic for [FirebaseAnalysisService.fetchExistingReport]: does
 * this raw `reports/{scanId}` Firestore doc map carry enough content to
 * skip a fresh `analyzeDevice` call? Extracted to a top-level function
 * (A110 precedent — `categoryScoresFromCachedAi` in ViewModels.kt) so it's
 * unit-testable without mocking `DocumentSnapshot`/`FirebaseFirestore`.
 *
 * A109: previously checked flat `grade`/`score` unconditionally — a V7 doc
 * (whose grade-equivalent is `overall.letter`, not top-level `grade`)
 * always failed, so every reopen of a V7 report re-called the AI. Now
 * reads the same declared field ([AiPipeline] + one required field per
 * shape) as [net.neuralcheck.data.remote.model.AiAnalysisResponse.isComplete] —
 * see that function's doc for why the V6/legacy branch is left as the
 * original two-field check rather than collapsed further.
 *
 * `data` is `doc.data` — a raw `Map<String, Any?>` (Firestore's Kotlin
 * SDK auto-converts nested JSON objects to nested `Map`s), not the typed
 * [net.neuralcheck.data.remote.model.AiAnalysisResponse] — this gate runs
 * BEFORE the wrapper-key flattening + Gson parse below it, exactly as the
 * original grade/score check did.
 *
 * Order 2026-09-24 (القرار ٨٠/٨٢ البند ٦), two corrections to the V7
 * branch below (real document shape confirmed against
 * `v7-reports-document-sample.json` / `-honor.json`, SHA256-verified —
 * see `app/src/test/resources/contract/`):
 *   1. `overall` lives at `data.aiResponse.overall`, NOT `data.overall` —
 *      the raw stored document nests the whole V7 shape under
 *      `aiResponse` (same object [FirebaseAnalysisService.fetchExistingReport]
 *      flattens a few lines below via the same `aiResponse` wrap key).
 *      Reading `data["overall"]` directly always returned `null` for
 *      every real V7 document, so this gate ALWAYS failed on V7 before
 *      this fix — not only for harnessError reports.
 *   2. The required field is `letterSource`, not `letter` — see
 *      [net.neuralcheck.data.remote.model.isComplete]'s doc for why:
 *      `letter` is legitimately `null` for a finished `noDataAtAll` /
 *      `HARNESS_ERROR` report, `letterSource` is not.
 */
internal fun isCachedReportComplete(data: Map<String, Any?>?): Boolean {
    if (data == null) return false
    val pipeline = data["pipeline"] as? String
    val aiResponse = data["aiResponse"] as? Map<*, *>
    val overall = aiResponse?.get("overall") as? Map<*, *>
    val hasV7Overall = (overall?.get("letterSource") as? String)?.isNotBlank() == true
    val gradeOk = (data["grade"] as? String)?.isNotBlank() == true
    val scoreOk = data["score"] is Number
    // Order 2026-09-26 (القرار ١٠٢ البند ٢): was a raw `pipeline ==
    // V7_FINDINGS_BASED` / `else` binary check — `cached` always fell into
    // `else` (root grade/score check) regardless of what it actually held.
    // A `cached` document copying a V7 source's content (root has no
    // grade/score at all — AiPipeline.CACHED's doc) always failed this
    // gate before, forcing a wasted live re-fetch even though the cached
    // copy was real and complete.
    return when (
        net.neuralcheck.data.remote.model.AiPipeline.classifyShape(
            pipeline,
            hasRootLetterOrGrade = gradeOk,
            hasV7Overall = hasV7Overall
        )
    ) {
        net.neuralcheck.data.remote.model.AiPipeline.ShapeKind.V7 -> hasV7Overall
        net.neuralcheck.data.remote.model.AiPipeline.ShapeKind.V6 -> gradeOk && scoreOk
        net.neuralcheck.data.remote.model.AiPipeline.ShapeKind.UNKNOWN -> false
    }
}

/**
 * Order 2026-09-26 (القرار ١٠٢ البند ١، القاعدة 41). Reads `createdAt` off
 * the RAW `reports/{scanId}` document map — the shape this actually
 * arrives in on the Android Firestore SDK is `com.google.firebase.Timestamp`,
 * not a plain number (that's `analyzedAt`'s shape) and not the
 * `{_seconds, _nanoseconds}` map a Node-side Admin SDK serializer writes
 * into a hand-authored test fixture. Reading it here, off `data` directly,
 * BEFORE [flattenReportDocument]/`gson.toJson` would round-trip it through
 * JSON (where a `Timestamp` object has no single correct textual shape to
 * commit to), avoids inventing a dual-shape JSON parser for a type Gson
 * was never going to represent faithfully anyway.
 *
 * Returns null — never a fabricated 0 — when the key is absent or isn't a
 * recognised `Timestamp` (rule 22).
 */
internal fun extractCreatedAtMs(data: Map<String, Any?>): Long? =
    (data["createdAt"] as? com.google.firebase.Timestamp)?.toDate()?.time

/**
 * Flattens a raw `reports/{scanId}` document so Gson's typed
 * [net.neuralcheck.data.remote.model.AiAnalysisResponse] parse finds every
 * field regardless of which wrapper key the backend nested it under.
 * Extracted from [FirebaseAnalysisService.fetchExistingReport] (same A110/
 * A109 precedent as [isCachedReportComplete]) so this specific step is
 * unit-testable against real document shapes.
 *
 * The Cloud Function might write the verdict under one of several shapes
 * depending on its version:
 *   1. Flat: top-level summary/details/issues/etc. (older V6 writes).
 *   2. Nested under `aiAnalysis` / `analysis` / `result` / `ai` (older
 *      wrapper experiments).
 *   3. Nested under `aiResponse` — the current V7 shape (order 2026-09-24،
 *      القرار ٨٠/٨٢ البند ٦): `overall`/`categories`/`issues`/`narrative`/
 *      `language`/`narrativeIncomplete*` ALL live here, confirmed against
 *      `v7-reports-document-sample.json` / `-honor.json`
 *      (`app/src/test/resources/contract/`, SHA256-verified). This was
 *      MISSING from the wrap-key list before this fix — `isCachedReportComplete`
 *      could pass yet this parse would still yield an
 *      `AiAnalysisResponse` with `overall`/`categories`/`narrative` all
 *      null, because nothing had copied `aiResponse`'s contents up.
 *
 * `putIfAbsent` leaves top-level keys winning, so a document with some
 * fields at the root (the four `V7_OPERATIONAL_FIELDS` — `shortId`/
 * `isFreeTrial`/`aiModel`/`deviceMarketingName` — plus `pipeline`/
 * `analyzedAt`/`deviceModel`/etc., ALL already at the document root) and
 * others nested doesn't lose data either way.
 */
internal fun flattenReportDocument(data: Map<String, Any?>): Map<String, Any?> {
    val flat = data.toMutableMap()
    listOf("aiResponse", "aiAnalysis", "analysis", "ai", "result", "verdict_data").forEach { wrapKey ->
        (data[wrapKey] as? Map<*, *>)?.forEach { (k, v) ->
            if (k is String) flat.putIfAbsent(k, v)
        }
    }
    return flat
}

/**
 * Subscription snapshot for the signed-in user. `expiresAt` is typed as
 * [Any] because Cloud Functions may encode Firestore timestamps as either a
 * numeric epoch or a `{seconds, nanoseconds}` map — the UI layer decides how
 * to render it once that is pinned down.
 *
 * Field-name aliases: the back-office has shipped the scan-count fields under
 * three different keys at various points (`scansRemaining`, `scans_remaining`,
 * `remainingScans`). Without [SerializedName.alternate] Gson silently drops
 * any non-matching key — leaving `scansRemaining = 0` even for a paid user
 * whose plan + expiresAt rendered correctly. Same defensive aliasing on
 * `expiresAt` covers the snake-case variant some Firestore export utilities
 * inject.
 */
data class SubscriptionStatus(
    @SerializedName(value = "plan") val plan: String = "none",
    @SerializedName(
        value = "scansRemaining",
        alternate = ["scans_remaining", "remainingScans", "remaining_scans"]
    ) val scansRemaining: Int = 0,
    @SerializedName(
        value = "scansTotal",
        alternate = ["scans_total", "totalScans", "total_scans"]
    ) val scansTotal: Int = 0,
    @SerializedName(value = "expiresAt", alternate = ["expires_at"]) val expiresAt: Any? = null,
    @SerializedName(value = "isActive", alternate = ["is_active", "active"]) val isActive: Boolean = false
)

/**
 * Result of the `registerUser` Cloud Function. [existing] is `true` when the
 * backend found a pre-existing `users/{uid}` document and kept its state
 * intact — in that case the client should treat the sign-in as a
 * *returning* user and skip any "welcome" messaging. For a brand-new
 * account the server creates the doc and returns `existing = false`.
 */
data class RegisterResult(
    val success: Boolean = false,
    @SerializedName(
        value = "scansRemaining",
        alternate = ["scans_remaining", "remainingScans", "remaining_scans"]
    ) val scansRemaining: Int = 0,
    val existing: Boolean = false
)

data class PaymentResult(
    @SerializedName(value = "paymentId", alternate = ["payment_id"]) val paymentId: String = "",
    val status: String = "",
    val message: String = ""
)

/**
 * Result of [FirebaseAnalysisService.redeemPromoCode]. On success the server
 * populates [plan] (the plan the code granted — may be blank for scan-only
 * top-ups) and [scans] (the number of scans credited). On failure [success]
 * is false and [message] carries a reason token: `invalid_code`, `expired`,
 * or `already_used`. Client code pattern-matches on [message] because the
 * server messages are intentionally not localized — each client picks its
 * own language.
 */
data class PromoResult(
    val success: Boolean = false,
    val plan: String = "",
    val scans: Int = 0,
    val message: String = ""
)

/**
 * Result of [FirebaseAnalysisService.createScanSession]. The caller drops
 * [code] into a QR + also surfaces it as human-readable text (e.g.
 * "NC-7429") so the customer device can either scan or type it. The
 * session self-expires server-side after [expiresInMinutes] minutes — we
 * don't track it locally except to render the countdown hint.
 */
data class ScanSession(
    /**
     * `null` = the answer did not carry it (batch 2, item 4, rule 22). It used to default to `""`, which drew an empty code chip and an empty QR on the owner's
     * screen for an answer that named no session. The owner's screen now treats a missing, null or blank id as a failure to create the session.
     */
    @SerializedName(value = "sessionId", alternate = ["session_id"]) val sessionId: String? = null,
    /** `null` = the answer did not carry a code (same as [sessionId]): a session with no code to type or scan is not a session. */
    val code: String? = null,
    /**
     * The session's lifetime the server named, in minutes. `null` = the answer did not carry it. Order 2026-10-15 (rule 22):
     * it used to default to 15, which drew a "15:00" countdown for a session the server never said lived 15 minutes (the
     * server's lifetime is 60 minutes, relayed - createScanSession.js:29, not verified from this repository). Now the
     * owner's screen shows a countdown only for a lifetime the server named.
     */
    @SerializedName(
        value = "expiresInMinutes",
        alternate = ["expires_in_minutes", "expiresIn"]
    ) val expiresInMinutes: Int? = null,
    /**
     * The shop's switched-on protocol parts, exactly as the answer carried them (A-P6-03; contract A2: `createScanSession` returns `protocolParts` in its answer so the chips can be drawn).
     * `null` = the key is missing; a JSON null is a `JsonNull`. It is kept as the raw JSON ON PURPOSE: the shape of each part is not named in the contract copy, so the reading is one named
     * function that tells a missing key, a part that is off and a value it has no case for ([ProtocolPartsRead.of] through [protocolPartsRead]) - the DTO decides none of it (rules 22, 25).
     */
    @SerializedName(ProtocolPartsRead.KEY) val protocolParts: JsonElement? = null
) {
    /** The reading of [protocolParts] (the one place it is read). */
    fun protocolPartsRead(): ProtocolPartsRead = ProtocolPartsRead.of(protocolParts)
}

/**
 * Result of [FirebaseAnalysisService.joinScanSession]. The customer
 * device stashes [sessionId] in [ScanSessionHolder] so the eventual
 * `analyzeDevice` call can be tagged with it instead of a Firebase Auth
 * uid. [status] echoes the server's view ("pending" → "scanning" etc.)
 * for the owner-side GenerateSessionScreen listener to render.
 */
data class JoinSessionResult(
    /**
     * `null` = the answer did not carry a session id (the key is missing, or it is a JSON null). Batch 3, item 3 (rule 22): it used to default to `""`, which the
     * QR path treated as a failure and the typed-code screen ignored in silence. [joinScanSessionWithDeviceIdentity] - the one function both join entry points call -
     * now ends an answer whose id is null or blank in a named failure ([JoinAnswerWithoutSessionIdException]) at both points, with a log line. Gson builds this class
     * through its no-argument constructor, so a missing key leaves the default, `null`.
     */
    @SerializedName(value = "sessionId", alternate = ["session_id"]) val sessionId: String? = null,
    @SerializedName(value = "ownerId", alternate = ["owner_id"]) val ownerId: String = "",
    val status: String = "",
    /**
     * feat/technician-evaluation: true when the session owner's shop
     * account is entitled to the technician-evaluation flow (server
     * contract feat/session-evaluation-contract). Gates the questions
     * card on the scanned device.
     *
     * `null` = the answer did NOT carry it (the key is missing, or it is a JSON null — the raw line
     * `join-response-raw` says which). Order 2026-10-15 (rule 22): it used to default to `false`, which
     * read a missing flag as the server saying "no". Now the absence stays absent: the holder, the scan
     * record and the card's gate each carry it as `null` and each declares it (`absent` on its log line);
     * the card is not shown for it — a decision made by the reader that gates the card, not a value
     * the DTO invents. Gson leaves it `null` when the key is missing (it builds the object without
     * running the constructor), so no default here can fill it.
     */
    @SerializedName(value = "evaluationEnabled", alternate = ["evaluation_enabled"])
    val evaluationEnabled: Boolean? = null,
    /**
     * ق15 (2026-10-23): true when the owner's shop has the consent part on (`consentPartDecision`, neuralcheck-backend `functions/src/sessions/consentPartState.js:36`, as the coordinator relayed it - rule 29: a
     * copy). The server answers a boolean, always; `null` = THIS answer did not carry it (the key is missing, or a JSON null - an older server). Rule 22: the absence stays absent - [consentPart] names it, the
     * join's state carries it, and the two join entry points route by it (no consent screen for [ConsentPartRead.NotEnabled] or [ConsentPartRead.Absent]). Gson leaves it `null` when the key is missing (it
     * builds the object without running the constructor), so no default here can fill it. No snake-case alternate: the contract names one key.
     */
    @SerializedName(ConsentWire.KEY_CONSENT_ENABLED)
    val consentEnabled: Boolean? = null
) {
    /** The shop's consent part as this answer says it: one named state, never a default (see [consentEnabled]). */
    fun consentPart(): ConsentPartRead = ConsentPartRead.of(consentEnabled)
}
