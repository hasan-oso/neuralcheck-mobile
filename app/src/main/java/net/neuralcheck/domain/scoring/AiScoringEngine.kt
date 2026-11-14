package net.neuralcheck.domain.scoring

import android.content.Context
import android.util.Log
import com.google.firebase.functions.FirebaseFunctionsException
import dagger.hilt.android.qualifiers.ApplicationContext
import net.neuralcheck.data.local.files.TimeSeriesStates
import net.neuralcheck.data.remote.BehaviorFingerprintBuilder
import net.neuralcheck.data.remote.FirebaseAnalysisService
import net.neuralcheck.data.remote.ScanSessionHolder
import net.neuralcheck.data.repository.TimeSeriesRepository
import net.neuralcheck.data.remote.ReasonCodeRead
import net.neuralcheck.data.remote.model.AiAnalysisResponse
import net.neuralcheck.data.remote.model.AiCategoryScore
import net.neuralcheck.data.remote.model.ManualResultsHeaderDto
import net.neuralcheck.data.remote.model.isComplete
import net.neuralcheck.domain.manual.ManualTestVisibility
import net.neuralcheck.domain.model.AiAnalysisResult
import net.neuralcheck.domain.model.IssueSeverity
import net.neuralcheck.domain.model.PredictedIssue
import net.neuralcheck.domain.model.ScanReport
import net.neuralcheck.domain.model.TestStatus
import net.neuralcheck.util.AiVerdictPresentation
import net.neuralcheck.util.DeviceFingerprint
import net.neuralcheck.util.DeviceProfileKey
import net.neuralcheck.util.LogRedaction
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Launch-flavor scoring engine. The backend is the single source of truth for
 * the user-facing report — we do not run the rule-based scoring at all, so
 * nothing from the local scoring system (category/axis breakdowns, critical
 * failures derived from thresholds, grade description) leaks into the launch
 * report. When the backend is unreachable the user sees an error card with a
 * retry affordance instead of a rule-based fallback.
 *
 * Flow:
 *  1. Compute the anti-tamper penalty locally via [AntiTamperDetector]. This
 *     is the one local signal we keep — it's a *detector* of spoofing, not a
 *     scoring layer, and the backend wants it inside the fingerprint so its
 *     own heuristics can weigh tampered scans differently.
 *  2. Load the raw 18-channel time-series for this scan from disk.
 *  3. Condense scan + series into a [net.neuralcheck.data.remote.model.BehaviorFingerprint].
 *  4. Call the `analyzeDevice` Cloud Function.
 *  5. On success, wrap the response in a [ScoringResult] whose grade/score
 *     come from the backend and whose breakdown maps are all empty — the UI
 *     layer (ReportScreen) keys off `BuildConfig.USE_AI_SCORING` to render
 *     only the AI section in launch builds.
 *  6. On failure, return the same empty result shape with `error` populated.
 *     `aiAnalysis` stays null so the UI can tell success/failure apart even
 *     if the backend ever returns an empty verdict.
 */
@Singleton
class AiScoringEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fingerprintBuilder: BehaviorFingerprintBuilder,
    private val firebaseService: FirebaseAnalysisService,
    private val timeSeriesRepository: TimeSeriesRepository,
    private val scanSessionHolder: ScanSessionHolder,
    // Order 2026-10-14 («Record the session at scan start»): the session each scan STARTED in, copied by
    // StressTestService when the scan's id was minted. The analysis asks THIS, never the holder.
    private val scanSessionRecords: net.neuralcheck.data.remote.ScanSessionRecords,
    private val authManager: net.neuralcheck.data.auth.AuthManager,
    // feat/technician-evaluation: posts the persisted technician judgement
    // right after a successful analysis (see the success branch).
    private val evaluationSubmitter: net.neuralcheck.domain.evaluation.EvaluationSubmitter,
    // A20: coarse progress for the ~3-minute wait. Observation only — it
    // never changes the payload, the verdict, or any measurement.
    private val analysisProgress: AnalysisProgress
) : ScoringEngine {

    private companion object {
        const val TAG = "NeuralCheck"
    }

    override suspend fun calculateScore(scanReport: ScanReport): ScoringResult {
        val antiTamperPenalty = AntiTamperDetector.calculatePenalty(
            scanReport.antiTamperFlags,
            scanReport.scanMetadata.startBatteryTemp
        )
        val isIncomplete = !scanReport.scanMetadata.isComplete

        analysisProgress.begin()
        // fieldsVersion 68 (T-18 row 11): when there is no time series the
        // payload says WHY (no_file:<reason>) instead of shipping zeros — the
        // reason is the error text of the failed read, or the plain fact that
        // the reader found neither the file nor a footer backup.
        var noTimeSeriesReason: String? = null
        val timeSeries = try {
            timeSeriesRepository.getReader().readFile(scanReport.scanId).also {
                if (it == null) noTimeSeriesReason = TimeSeriesStates.REASON_READER_RETURNED_NULL
            }
        } catch (t: Throwable) {
            Log.w(TAG, "AiScoringEngine: failed to read time-series: ${t.message}")
            noTimeSeriesReason = net.neuralcheck.util.ReasonText.of(t)
            null
        }

        // Compute the five-layer DeviceProfileKey here rather than passing it
        // from StressTestService, because StressTestService doesn't talk to
        // BehaviorFingerprintBuilder directly and the key inputs (KeyStore
        // signature, FID, ANDROID_ID, build, sensors) are deterministic — same
        // device → same key, so a fresh computation matches whatever was
        // persisted as ScanEntity.deviceFingerprint.primary at scan time.
        // After the first scan the KeyStore alias exists and the call is
        // ~10ms (sign + hash); only the very first invocation pays the
        // RSA-2048 keypair generation cost.
        val deviceProfileKey = runCatching {
            DeviceFingerprint.generateProfileKey(context)
        }.getOrElse {
            Log.w(TAG, "generateProfileKey failed in AiScoringEngine: ${it.message}")
            DeviceProfileKey(
                primary = "unknown_fp",
                layerA_keystore = "no_keystore",
                layerB_firebaseId = "no_fid",
                layerC_androidId = "no_android_id",
                layerD_buildSig = "unknown",
                layerE_sensorSig = "no_sensors"
            )
        }

        // V2 path: when USE_V2_STRESS is true and the time-series has
        // V2-shaped data (verificationResults populated, footer carries
        // intensityFactor != default), call buildV2 to produce the
        // enriched fingerprint with PhaseAnalysis + 30 V2 stats.
        // V1 path stays as the fallback for legacy reports cached
        // before V2 landed.
        // Single build path now — V1/V2 distinction lived in the
        // summary computation, which we removed. Raw samples have the
        // same shape in both, only the variables[] schema differs
        // (18 entries V1, 22 V2). The schema rides along in the payload.
        analysisProgress.set(AnalysisStage.PREPARING_PAYLOAD)
        val fingerprint = fingerprintBuilder.build(
            scanReport = scanReport,
            timeSeries = timeSeries,
            deviceProfileKey = deviceProfileKey,
            noTimeSeriesReason = noTimeSeriesReason,
            // fieldsVersion 69 (N-17): ALWAYS shipped from here — an empty list is the
            // explicit "nothing was hidden"; the builder's own default (absent) would
            // read as "this build told us nothing".
            manualResultsHeader = ManualResultsHeaderDto(
                hiddenByModel = ManualTestVisibility.hiddenByModel(context)
            )
        )

        Log.i(TAG, "fingerprint built: " +
            "scanId=${scanReport.scanId} " +
            "samples=${fingerprint.timeSeries.samples.size} " +
            "variables=${fingerprint.timeSeries.variables.size} " +
            "functional=${fingerprint.functionalResults.size} " +
            "manual=${fingerprint.manualResults.size}")

        val language = Locale.getDefault().language.ifBlank { "ar" }
        // When the scan started in a shop-owner session (a customer device has no
        // Firebase Auth user until the anonymous sign-in below), the session id is the one
        // RECORDED for this scan at scan start (ScanSessionRecords; the join entry points
        // populated the holder and StressTestService.startScan copied it - comment rewritten
        // 2026-10-15, it named only the typed-code screen and the holder). Passing it through
        // lets the backend validate via scanSessions/{id} instead of request.auth; see
        // FirebaseAnalysisService.analyzeDevice for the auth-gate logic.
        //
        // Order 2026-10-14 (user decision «Record the session at scan start»): "The scan keeps the session it started
        // in; its analysis ... use that one, never the session joined later." This read used to be
        // `scanSessionHolder.get()` — the holder as it is NOW, minutes after the scan started, so a session joined in
        // between (B) was sent for a scan that started under A. The holder is no longer consulted for the request.
        // A scan with no record started in no session and stays personal (null), whatever the holder holds.
        val sessionId = scanSessionRecords.sessionFor(
            scanReport.scanId,
            net.neuralcheck.data.remote.ScanSessionRecords.Use.ANALYSIS
        )?.sessionId

        // Customer-device bootstrap. Firebase callable functions REQUIRE the
        // call to be made by an authenticated user even when the function
        // itself accepts an anonymous one (server treats `request.auth.uid`
        // as the device identity, then validates the sessionId separately).
        // Without this we hit the SDK-level guard with "Firebase Auth user
        // is null — cannot invoke callable" before the function's own
        // sessionId branch ever runs. The signInAnonymously call mints a
        // throwaway uid scoped to this device install — cheap, idempotent
        // (no-op if already signed in), and the resulting account never
        // gets data attached to it because the report is filed against the
        // owner's userId via the sessionId binding server-side.
        if (!sessionId.isNullOrBlank() && !authManager.isLoggedIn) {
            try {
                Log.d(TAG, "AiScoringEngine: bootstrapping anonymous auth for sessionId=${LogRedaction.hashId(sessionId)}")
                authManager.signInAnonymously()
            } catch (t: Throwable) {
                Log.w(TAG, "Anonymous sign-in failed (will let analyzeDevice surface the error): ${t.message}")
            }
        }

        return try {
            // Cache-first: did a previous attempt for this scanId already land
            // a verdict in `reports/{scanId}`? If yes, rehydrate from
            // Firestore for the cost of one read instead of paying for a
            // fresh Gemini call. Covers two real scenarios:
            //   1) Client timed out at 30 s but the function actually finished
            //      a few seconds later — the report is now in Firestore even
            //      though we never received it. Retrying without this lookup
            //      double-bills the same scan.
            //   2) User left and came back to the report screen, which
            //      triggers loadScan → calculateScore again. Without this
            //      cache check we'd spend another AI call on every reopen.
            // Failure here is non-fatal: any error returns null and we fall
            // through to the live call exactly as before.
            analysisProgress.set(AnalysisStage.CHECKING_CACHE)
            val cached = firebaseService.fetchExistingReport(scanReport.scanId)
            val cachedComplete = cached?.isComplete() == true
            val response = if (cached != null && cachedComplete) {
                Log.d(TAG, "AiScoringEngine: using complete cached report for ${scanReport.scanId} (skipping Gemini)")
                cached
            } else {
                if (cached != null) {
                    Log.d(TAG, "AiScoringEngine: cached report incomplete (only metadata), calling analyzeDevice fresh")
                }
                analysisProgress.set(AnalysisStage.ANALYSING)
                firebaseService.analyzeDevice(
                    scanId = scanReport.scanId,
                    fingerprint = fingerprint,
                    language = language,
                    sessionId = sessionId
                )
            }
            Log.d(
                TAG,
                "AiScoringEngine: response shape — pipeline=${response.pipeline} " +
                    "summary=${response.summary?.take(30)} " +
                    "issues=${response.issues?.size} " +
                    "predictedProblems=${response.predictedProblems?.size} " +
                    "categoryScores=${response.categoryScores?.size} " +
                    "overall=${response.overall != null} " +
                    "categories=${response.categories?.size} " +
                    "narrative=${response.narrative != null}"
            )
            // Phase 3-a / rule-22: `pipeline` has three recognised states
            // (v7_findings_based / v6_raw_timeseries / absent-so-v6, see
            // AiPipeline) — a fourth, unrecognised value must be RECORDED
            // here, not silently absorbed into the V6 fallback branch that
            // AiAnalysisResponse.isComplete() / AiAnalysisResult.isComplete()
            // take for "anything that isn't v7_findings_based". This is the
            // one place every fresh AND cache-hit response passes through,
            // so it's the point where an unknown pipeline value from a
            // future backend change becomes visible instead of quietly
            // behaving like V6.
            if (response.pipeline != null &&
                response.pipeline != net.neuralcheck.data.remote.model.AiPipeline.V7_FINDINGS_BASED &&
                response.pipeline != net.neuralcheck.data.remote.model.AiPipeline.V6_RAW_TIMESERIES
            ) {
                Log.w(
                    TAG,
                    "AiScoringEngine: UNRECOGNISED pipeline value '${response.pipeline}' — " +
                        "neither v7_findings_based, v6_raw_timeseries, nor absent. Falling " +
                        "back to V6-shaped reading for this response; every V6 field below " +
                        "may be empty if this is actually a new, unmapped shape."
                )
            }
            // Diagnostic: confirm the backend's shortId arrived and made it
            // through Gson deserialization. If this logs `null` while the
            // user can see a short ID on the server side, the casing of the
            // JSON field doesn't match what AiAnalysisResponse expects.
            Log.d(TAG, "shortId = ${response.shortId}")

            // feat/technician-evaluation: the analysis has landed —
            // reports/{scanId} now exists, so the server can compute
            // gradeDistance immediately.
            //
            // Order 2026-09-30 («أمر — التقييم (ب)، ومهلة الجلسة، والبنود
            // المعلّقة»، البند ١): this call is now a FALLBACK ONLY. The
            // primary trigger moved to ActiveScanViewModel's `Completed`
            // transition (option ب: fires the instant the scan finishes,
            // before this analysis call, while the session is still
            // "scanning" — see that class's own doc). By the time THIS line
            // runs, a not-yet-sent row would be rejected anyway —
            // analyzeDevice.js:662-671 flips the session to "completed"
            // server-side before returning, and submitEvaluation.js:73-74
            // rejects any non-"scanning" session with PERMISSION_DENIED
            // (both relayed by the coordinator from
            // neuralcheck-backend@c7446a4, not verified from this repo —
            // القاعدة ٢) — this call, alone, was exactly the bug this order
            // fixed by moving the trigger earlier. It stays as a harmless
            // fallback: EvaluationSubmitter.submitOne's `row.submitted` /
            // `row.finalFailure` guards make it a no-op for a row
            // ActiveScanViewModel already resolved (sent, or permanently
            // rejected) — never a duplicate send, never a retry of a
            // confirmed-terminal rejection. Fire-and-forget: the submitter
            // reads the Room row (sessionId snapshotted at answer time, NOT
            // this engine's sessionId variable) and never delays the
            // report; a transient failure leaves submitted=false for the
            // sync-path retry. No-op when no evaluation row exists.
            analysisProgress.set(AnalysisStage.BUILDING_REPORT)

            evaluationSubmitter.submitAsync(scanReport.scanId)
            // Order 2026-09-24 (القرار ٨٠/٨٢ البند ٦): the rest of this
            // success branch (realCounts reconciliation → AiAnalysisResult
            // → ScoringResult) is extracted to the top-level
            // buildScoringResultFromResponse() below the class, so
            // ReportViewModel.loadScan's Room-miss fallback (which also
            // calls fetchExistingReport, the exact same AiAnalysisResponse
            // shape, but has no ScanReport/functional-results to reconcile
            // against) builds the IDENTICAL ScoringResult/AiAnalysisResult
            // shape instead of a second, hand-maintained copy that could
            // drift from this one.
            val realCounts: Map<String, Pair<Int, Int>> = scanReport.functionalResults
                .groupBy { it.categoryName.trim().lowercase(Locale.US) }
                .mapValues { (_, items) ->
                    val total = items.size
                    val pass = items.count { it.status == TestStatus.PASS }
                    pass to total
                }
            buildScoringResultFromResponse(
                response = response,
                antiTamperPenalty = antiTamperPenalty,
                isIncomplete = isIncomplete,
                realCounts = realCounts
            )
        } catch (t: Throwable) {
            // ملحق ثانٍ بالقرار ١٠٤ البند ٣: كتلة الاستثناء (الصنف والرسالة
            // الخامّة) إلى السجلّ لتمييز السبب (rule 21) — القاعدة 36: لم
            // تُنقَّ، بحكم مُتَّخَذ صراحة أدناه عند `error =`، لا صمتاً.
            Log.w(TAG, "AiScoringEngine: backend call failed: ${t.javaClass.simpleName}: ${t.message}")
            // fieldsVersion 69 (S-26): "still processing" is a state of its own, not an error card.
            val inProgress = isAnalysisInProgress(t)
            // Empty result — the ReportScreen shows the error card + retry and
            // intentionally renders NO rule-based fallback. Keeping overallScore
            // at 0 and grade at "?" means the few downstream consumers that
            // still read the field (e.g. ScoreRepository.save) persist an
            // obviously-invalid placeholder we can filter out later.
            ScoringResult(
                overallScore = 0f,
                overallGrade = "?",
                gradeDescription = "",
                gradeDescriptionTr = "",
                axisScores = emptyMap(),
                categoryScores = emptyMap(),
                individualScores = emptyMap(),
                criticalFailures = emptyList(),
                antiTamperPenalty = antiTamperPenalty,
                isIncomplete = isIncomplete,
                scoringMethod = "ai_v1",
                aiAnalysis = null,
                // Technical detail only — AiAnalysisErrorCard already renders
                // the localized headline (report_ai_error_title) above this
                // line, so a hardcoded-Arabic prefix here just showed the
                // wrong language to Turkish users.
                //
                // Order 2026-09-24 (القرار ٨٠/٨٢ البند ٦): "بطاقة الخطأ دون
                // شبكة: نصّها الثابت وحده، بلا نصّ الاستثناء الخام." A raw
                // connectivity failure (no network, DNS lookup, socket
                // reset — the SDK layers involved don't share one
                // exception TYPE, only overlapping wording) used to reach
                // the card verbatim as `t.message`, e.g. "Unable to
                // resolve host \"...\": No address associated with
                // hostname" — an unlocalized, implementation-detail
                // string. Rule 21: distinguish by the error TEXT, not the
                // exception class — networkErrorMessageOrNull() (NoConnectionRead.kt)
                // extends LoginViewModel.mapFirebaseError's exact idiom
                // (`"network" in msg`, which alone only matches Firebase
                // Auth's own crafted wording) with the plain JVM/Android
                // phrasings a Functions-callable failure is more likely to
                // actually surface — see that function's own doc for why
                // the single-phrase idiom alone missed the most common
                // real case.
                //
                // UPDATED — ملحق ثانٍ بالقرار ١٠٤ البند ٣ (2026-09-26):
                // the paragraph above used to end "every OTHER failure
                // cause (timeout, server error, auth) is unchanged and
                // still shows the raw message — only the no-network case
                // was named in this order." That is no longer true: a
                // 300s analyzeDevice timeout and a server-side HttpsError
                // are both field-observed non-network failures, and the
                // raw `t.message` reaching the card for THOSE cases is an
                // unlocalized, implementation-detail string exactly like
                // the connectivity case this file already fixed —
                // `error_generic` ("حدث خطأ غير متوقع" / "Beklenmeyen bir
                // hata oluştu", already committed with a Turkish
                // translation — values/strings.xml:825,
                // values-tr/strings.xml:599) now covers every non-network
                // cause uniformly. The raw class+message is NOT lost —
                // logged just above (rule 21: the log keeps the real
                // diagnostic text, only the ON-SCREEN copy is genericized).
                //
                // Rule 36 judgment (asked for explicitly, not a silent
                // default): NOT sanitized before logging. `t` here comes
                // from calling THIS APP'S OWN backend (`analyzeDevice`) via
                // the standard Firebase Callable Functions Android SDK —
                // not a third-party provider whose response body is
                // arbitrary, uncontrolled text (the rule's own precedent:
                // a vendor's raw 401 body echoing part of an API key). The
                // Callable Functions contract itself is designed so a
                // server-thrown `HttpsError`'s message is deliberately
                // client-safe text the backend author chose to expose, and
                // any OTHER uncaught server-side exception is redacted by
                // Firebase itself to a bare "internal" error before it
                // ever reaches this SDK — not something this app's code
                // decides. A client-side timeout's message is a fixed
                // JVM/Kotlin coroutine string, no secret surface at all.
                // Residual uncertainty disclosed rather than hidden: the
                // ACTUAL text neuralcheck-backend's HttpsError calls use is
                // not independently verified here (neuralcheck-backend is
                // a separate repository from this one, rule 2) — the
                // judgment rests on the Callable Functions SDK's own
                // documented contract, not on having read the backend's
                // literal strings.
                // A-SUB wave 1 (2026-10-17): the context the matrix reads is the scan's own - a scan recorded with a session is a session scan (ScanRefusalTexts.kt).
                error = if (inProgress) null else analysisErrorMessage(
                    context, t,
                    if (sessionId.isNullOrBlank()) RefusalContext.PERSONAL else RefusalContext.SESSION
                ),
                analysisInProgress = inProgress
            )
        }.also {
            // A20: clear the stage on success AND failure — a spinner left
            // running after an error is the same silence this feature
            // exists to end. This .also already covers both paths.
            analysisProgress.finish()
            // One-shot: consume the sessionId so a follow-up scan by a
            // signed-in user doesn't accidentally reuse the anonymous
            // customer-device context. Cleared on success and on every failure
            // EXCEPT "the server is still processing this scan" (fieldsVersion 69,
            // S-26): that retry is for the SAME scan and needs the same session.
            //
            // Order 2026-10-14: that is the HOLDER (the device's "session I joined last"), and it is now emptied only
            // when it still holds the session THIS analysis used — a session joined meanwhile (B) is not this scan's
            // to consume. The retry's session no longer depends on the holder: it is the scan's own record, which
            // survives a failure and is removed only by a success (shouldConsumeRecordAfterAnalysis).
            if (shouldClearSessionAfterAnalysis(sessionId, scanSessionHolder.get(), it)) {
                scanSessionHolder.clear(ScanSessionHolder.ClearReason.ANALYSIS_COMPLETE)
            }
            if (shouldConsumeRecordAfterAnalysis(sessionId, it)) {
                scanSessionRecords.consume(scanReport.scanId)
            }
        }
    }
}

/**
 * القرار ١٠٤ الملحق الرابع البند ١، والملحق الخامس البند ١. `analyzeDevice`
 * throws exactly TEN `HttpsError` calls across SIX distinct Functions
 * codes — منقول من
 * neuralcheck-backend:functions/src/analysis/analyzeDevice.js، غير
 * مُتحقَّق من هذا المستودع (rule 2):
 *   - `PERMISSION_DENIED`   — :332, :377, :389, :404
 *   - `INVALID_ARGUMENT`    — :349, :367
 *   - `FAILED_PRECONDITION` — :338
 *   - `UNAUTHENTICATED`     — :345
 *   - `ALREADY_EXISTS`      — :463
 *   - `UNAVAILABLE`         — :533
 * These ten calls are deliberate, human-authored Arabic-facing strings
 * telling the technician what to do (credit, subscription, session,
 * "already in progress") — القرار ٨٢ banned the raw TECHNICAL
 * exception, not our own backend's deliberate message; shown verbatim
 * for exactly these six codes. `INTERNAL` and `UNKNOWN` are explicitly
 * excluded, as is any other exception type or any other function
 * ([net.neuralcheck.domain.usecase.GenerateReportUseCase]'s own
 * `calculateScore` call is unaffected — still `error_generic` for
 * everything, since it never reaches this six-code branch unless it too
 * throws a matching `FirebaseFunctionsException`, and it has no live
 * caller regardless — see this file's other doc).
 *
 * fieldsVersion 69 (S-26): `ALREADY_EXISTS` no longer reaches this function from the engine — it is the
 * "still processing" state ([isAnalysisInProgress]) and shows the «wait» card instead of an error message.
 * The mapping below still covers it for any other caller.
 *
 * Behaviour, in order (A-SUB wave 1, 2026-10-17: step 2 is new): (1) the network-fixed string
 * ([networkErrorMessageOrNull]: the error text of the failure and of its cause
 * chain, and never for a failure that carries a server reasonCode - batch 4,
 * item 1) first; (2) a refusal that carries a details.reasonCode IN a cell of [ANALYSIS_REFUSAL_TEXTS] for [refusalContext]: this app's own text for it (ScanRefusalTexts.kt), and a
 * `scan-refused` log line; outside a cell the line says `combination=unexpected` or `no-approved-text` and the text is the one below; (3) for a real
 * [FirebaseFunctionsException] whose code is one of the six above AND
 * whose `cause` is NOT an [java.io.IOException], its message verbatim;
 * otherwise `error_generic`. The `cause`-based check is a conservative
 * choice: whether this SDK's own construction paths can ever attach one
 * of these six codes to a locally-originated (non-server) exception is
 * not settled here, so an `IOException`-typed `cause` is treated as
 * "does not reliably indicate a genuine `analyzeDevice` server
 * response" even when the code matches.
 *
 * Known limitations:
 *   - Two of the six codes can ALSO be produced by the Cloud Functions
 *     FRAMEWORK itself, before `analyzeDevice.js`'s own handler ever
 *     runs, with a fixed ENGLISH string — منقول من
 *     `node_modules/firebase-functions` 7.2.5 في neuralcheck-backend،
 *     غير مُتحقَّق من هذا المستودع: `INVALID_ARGUMENT` -> "Bad Request"
 *     (`https.js:421`) and `UNAUTHENTICATED` -> "Unauthenticated"
 *     (`https.js:442,452`). Shown verbatim either way — an English
 *     "Bad Request"/"Unauthenticated" is a possible, accepted outcome
 *     for these two codes specifically, not a bug in this function.
 *   - The SDK's own distinction between a genuine parsed server
 *     response and a locally-constructed exception carrying one of
 *     these six codes is not settled — the conservative `cause` check
 *     above is the current stand-in pending that.
 *
 * Extracted (A110 precedent — same rationale as
 * [net.neuralcheck.presentation.viewmodel.categoryScoresFromCachedAi] and
 * [net.neuralcheck.presentation.viewmodel.SingleFlightGuard]) so this
 * exact composition is unit-testable with a mocked [Context] and a
 * mocked [FirebaseFunctionsException] — see
 * `BuildScoringResultFromResponseTest.kt` (this repo's test source,
 * `analysisErrorMessage` group).
 */
internal fun analysisErrorMessage(context: Context, t: Throwable, refusalContext: RefusalContext): String {
    networkErrorMessageOrNull(context, t)?.let { return it }
    // A-SUB wave 1 (2026-10-17): a refusal that carries a details.reasonCode is told by the CODE and the scan's context (ScanRefusalTexts.kt's matrix), with this app's own texts - never by
    // the server's message or its Functions code (rule 21). A refusal outside the matrix, and any with no reasonCode, falls through to what this function showed before.
    val read = ReasonCodeRead.of(t)
    val combination = refusalCombination(refusalContext, read)
    val approved = (read as? ReasonCodeRead.Known)?.let { analysisRefusalText(refusalContext, it.code) }
    // the text this function showed for an intentional analyzeDevice code before (the server's own message), and when it still does
    val serverMessageApplies = t is FirebaseFunctionsException &&
        t.code in ANALYZE_DEVICE_INTENTIONAL_CODES &&
        t.cause !is java.io.IOException
    if (combination != null) {
        net.neuralcheck.util.EvalCardLog.scanRefused(
            context = refusalContext.wire,
            reasonCode = when (read) {
                is ReasonCodeRead.Known -> read.code.wire
                is ReasonCodeRead.Unrecognized -> "unrecognized"
                ReasonCodeRead.Absent -> "absent"
            },
            raw = (read as? ReasonCodeRead.Unrecognized)?.raw,
            combination = combination.wire,
            shown = approved?.key ?: if (serverMessageApplies) "server_message" else "error_generic"
        )
    }
    if (approved != null) return context.getString(approved.resId)
    if (serverMessageApplies) {
        return t.message?.takeIf { it.isNotBlank() }
            ?: context.getString(net.neuralcheck.R.string.error_generic)
    }
    return context.getString(net.neuralcheck.R.string.error_generic)
}

/**
 * fieldsVersion 69 (S-26, contract docs/FV68_CONTRACT.md section 7-h (d)): the server answered that it is
 * STILL PROCESSING this scan's analysis — `ALREADY_EXISTS` (HTTP 409, «قيد المعالجة»). **منقول، غير
 * مُتحقَّق من هذا المستودع:** that this code means "in progress" and is thrown at `analyzeDevice.js:463`
 * is relayed from `neuralcheck-backend` (see [analysisErrorMessage]'s doc), not read here.
 *
 * The code, not the message text, decides: it is the protocol status (the HTTP mapping), not an exception
 * class, and the same `cause !is IOException` condition as [analysisErrorMessage] keeps a locally built
 * exception from passing as a server answer. Any other code, and any other kind of failure, is NOT this state.
 */
internal fun isAnalysisInProgress(t: Throwable): Boolean =
    t is FirebaseFunctionsException &&
        t.code == FirebaseFunctionsException.Code.ALREADY_EXISTS &&
        t.cause !is java.io.IOException

/**
 * Whether [AiScoringEngine.calculateScore] empties the `ScanSessionHolder` after this analysis: yes when the
 * analysis used a session ([usedSessionId]), the answer is final (success, or a failure the user retries from
 * scratch) and the holder STILL holds that session ([holderSessionId]); NO when the server is still processing —
 * the retry that follows is for the same scan and needs the same session — and NO when the holder holds another
 * session (order 2026-10-14: one joined after this scan started belongs to the next scan, and emptying it would turn
 * that scan personal). Any OTHER failure still empties the holder (a known limit, contract section 7-h (d)) — what
 * the retry sends no longer depends on it: see [shouldConsumeRecordAfterAnalysis].
 */
internal fun shouldClearSessionAfterAnalysis(
    usedSessionId: String?,
    holderSessionId: String?,
    result: ScoringResult
): Boolean =
    !usedSessionId.isNullOrBlank() && !result.analysisInProgress && holderSessionId == usedSessionId

/**
 * Whether [AiScoringEngine.calculateScore] removes the scan's own record (`ScanSessionRecords`) after this analysis:
 * only when it used a session and SUCCEEDED. Order 2026-10-14 (the user's item 6): a retry after a rejection in a
 * session must not lose the session, so a failure — any failure, the "still processing" answer included — keeps the
 * record for the resend. Success is `aiAnalysis != null` (the engine's own marker: "On failure ... `aiAnalysis` stays
 * null so the UI can tell success/failure apart", class doc, step 6), not the absence of an error text.
 */
internal fun shouldConsumeRecordAfterAnalysis(usedSessionId: String?, result: ScoringResult): Boolean =
    !usedSessionId.isNullOrBlank() && result.aiAnalysis != null

/**
 * The six Functions codes `analyzeDevice.js` throws on purpose — see
 * [analysisErrorMessage]'s doc for the exact ten call sites (path+line,
 * rule 24: the literal distinguishing values live in exactly one place,
 * this one, not copy-pasted per call site).
 */
private val ANALYZE_DEVICE_INTENTIONAL_CODES = setOf(
    FirebaseFunctionsException.Code.PERMISSION_DENIED,
    FirebaseFunctionsException.Code.INVALID_ARGUMENT,
    FirebaseFunctionsException.Code.FAILED_PRECONDITION,
    FirebaseFunctionsException.Code.UNAUTHENTICATED,
    FirebaseFunctionsException.Code.ALREADY_EXISTS,
    FirebaseFunctionsException.Code.UNAVAILABLE
)

/**
 * Builds a [ScoringResult] (+ its nested [AiAnalysisResult]) from a raw
 * [AiAnalysisResponse] — the exact DTO type returned by BOTH
 * `analyzeDevice()` (fresh call, [AiScoringEngine.calculateScore]'s
 * success branch above) and `fetchExistingReport()` (Firestore cache —
 * used both by that same success branch's cache-hit path, and by
 * [net.neuralcheck.presentation.viewmodel.ReportViewModel.loadScan]'s
 * Room-miss fallback, order 2026-09-24 القرار ٨٠/٨٢ البند ٦). Extracted
 * so all three call sites build the IDENTICAL shape instead of hand-
 * maintained copies that could drift — see A110/A109 precedent comments
 * elsewhere in this codebase for the same reasoning applied to smaller
 * pieces of this same conversion.
 *
 * [realCounts] is the on-device pass/fail tally per category (from the
 * scan's actual functional-result rows), used only to repair a category
 * the AI graded but reported 0/0 for — empty when there is no local
 * functional-result data to reconcile against (the Room-miss fallback
 * path has no Room row for this scan at all), in which case every
 * category's server-reported counts are used exactly as sent — see the
 * inline comment at the merge site below for why this must never
 * back-fill an UNMEASURED category.
 */
fun buildScoringResultFromResponse(
    response: AiAnalysisResponse,
    antiTamperPenalty: Float,
    isIncomplete: Boolean,
    realCounts: Map<String, Pair<Int, Int>> = emptyMap()
): ScoringResult {
    // Merge backend `category_scores` with on-device counts so the
    // report can always render "passCount/totalCount" correctly even
    // when AI returned zeros for either field. Grade/score/note stay
    // exactly as AI authored them — we're only reconciling the count.
    val mergedCategoryScores: Map<String, AiCategoryScore>? =
        response.categoryScores?.mapValues { (key, value) ->
            // Unmeasured categories are left EXACTLY as the server
            // sent them (0/0). Back-filling them from on-device rows
            // would turn "5 tests, none run" into "0 of 5 passed" —
            // i.e. re-label a category we never measured as a
            // category that failed everything. The count-repair
            // below exists for a different bug (the AI returning
            // 0/0 for a category it DID grade) and must not swallow
            // this one.
            if (net.neuralcheck.util.CategoryPresentation.isUnmeasured(
                    measured = value.measured,
                    grade = value.grade,
                    totalCount = value.totalCount
                )
            ) {
                return@mapValues value
            }
            val (realPass, realTotal) = realCounts[key.trim().lowercase(Locale.US)]
                ?: (value.passCount to value.totalCount)
            val effectiveTotal = if (value.totalCount > 0) value.totalCount else realTotal
            val effectivePass = if (value.totalCount > 0 && value.passCount > 0) {
                value.passCount
            } else {
                realPass.coerceAtMost(effectiveTotal)
            }
            value.copy(
                passCount = effectivePass,
                totalCount = effectiveTotal
            )
        }

    val analysis = AiAnalysisResult(
        summary = response.summary.orEmpty(),
        details = response.details.orEmpty(),
        verdict = response.verdict.orEmpty(),
        issues = response.issues.orEmpty().map {
            PredictedIssue(
                severity = IssueSeverity.fromString(it.severity.orEmpty()),
                title = it.title.orEmpty(),
                description = it.description.orEmpty(),
                evidence = it.evidence,
                category = it.category
            )
        },
        grade = response.grade,
        score = response.score,
        predictedProblems = response.predictedProblems.orEmpty(),
        categoryNotes = response.categoryNotes.orEmpty(),
        // Enriched AI breakdown — the UI reads `note` from here. We keep
        // `null` distinct from "empty map" so ReportScreen can tell
        // "backend didn't ship this field" from "backend shipped no
        // categories" if we ever want different rendering for the two.
        categoryScores = mergedCategoryScores,
        // Optional human-readable ID minted by the backend. Surfaces
        // in the report header + QR caption when present; the UI
        // falls back to the raw scanId UUID otherwise.
        shortId = response.shortId?.takeIf { it.isNotBlank() },
        // New AI-authored fields shipped alongside the verdict:
        // rootCause = single-paragraph root-cause hypothesis;
        // buyerAdvice = per-persona recommendations;
        // aiModel = model identifier for footer attribution.
        // We pass these straight through — the report layer decides
        // which sections to render based on null/blank checks.
        rootCause = response.rootCause?.takeIf { it.isNotBlank() && it != "null" },
        buyerAdvice = response.buyerAdvice,
        aiModel = response.aiModel?.takeIf { it.isNotBlank() },
        // Backend marks the free-trial scan with this flag — ReportScreen
        // hides Save / QR / Share when set, and ReportPdfGenerator
        // refuses to render a PDF as a defense-in-depth.
        isFreeTrial = response.isFreeTrial == true,
        // Backend-resolved device name. Persisted to ScanEntity by
        // the caller (ReportViewModel) so every subsequent screen
        // and the cloud-sync payload all show the same canonical
        // name, regardless of which client first scanned the device.
        deviceMarketingName = response.deviceMarketingName?.takeIf { it.isNotBlank() },
        // V2 fields. Pass through from the backend; UI guards on
        // null to render the relevant section conditionally.
        expectedPerformance = response.expectedPerformance,
        changeAnalysis = response.changeAnalysis,
        // V7 findings-based pipeline (Phase 3-a) — same
        // straight pass-through as expectedPerformance/
        // changeAnalysis above. NOT read by ReportScreen/PDF
        // yet (that's Phase 3-b); captured here so the read
        // layer is complete and the Room round-trip
        // (AiAnalysisResult.isComplete) has something to gate
        // on. See AiAnalysisResult.kt's doc on these fields.
        pipeline = response.pipeline,
        language = response.language,
        overall = response.overall,
        categories = response.categories,
        narrative = response.narrative,
        narrativeIncomplete = response.narrativeIncomplete,
        narrativeIncompleteFields = response.narrativeIncompleteFields,
        categoriesMissingLetterCeilingReason = response.categoriesMissingLetterCeilingReason,
        narrativeIncompleteDeclared = response.narrativeIncompleteDeclared,
        issuesDeclared = response.issuesDeclared,
        harnessErrorDeclared = response.harnessErrorDeclared,
        noDataAtAllDeclared = response.noDataAtAllDeclared,
        partialAssessmentDeclared = response.partialAssessmentDeclared
    )
    // Translate the AI map into the rule-based CategoryScore shape that
    // `ScoringResult.categoryScores` has always carried. This way any
    // non-launch consumer (PDF generator, analytics) sees the AI's
    // per-category verdict through the same type it already handles.
    // `nameAr` falls back to the raw key because the backend returns
    // its own localized names in `note`; mapping each key to a fixed
    // Arabic string would either duplicate or override that. `nameTr`
    // stays empty for the same reason. `failCount` is derived so the
    // "X/Y" chip in the datacollect CategoryRow still renders.
    val catScores: Map<String, CategoryScore> = mergedCategoryScores
        ?.mapValues { (key, value) ->
            CategoryScore(
                name = key,
                nameAr = key,
                nameTr = "",
                score = value.score.toFloat(),
                grade = value.grade,
                passCount = value.passCount,
                failCount = (value.totalCount - value.passCount).coerceAtLeast(0),
                totalCount = value.totalCount,
                // Carry the unmeasured contract into the domain type
                // so the PDF (which consumes CategoryScore, not the
                // DTO) can render the neutral state instead of
                // dropping the row or printing it red.
                measured = value.measured,
                declaredCount = value.declaredCount,
                excludedCount = value.excludedCount,
                note = value.note
            )
        }
        .orEmpty()
    return ScoringResult(
        // Order 2026-09-24 (القرار ٧٦، البند ١): the absent score
        // stays absent — was `(response.score ?: 0).toFloat()`,
        // which coerced every V7 response (never carries
        // `response.score`) to a fabricated 0f. See
        // ScoringResult.overallScore's doc.
        overallScore = response.score?.toFloat(),
        // Phase 3-c fix: this used to read `response.grade ?: "?"`
        // unconditionally. `response.grade` is documented null for
        // EVERY V7 response (AiAnalysisResponse's pipeline doc:
        // "grade/score/summary/... above are NOT populated for this
        // shape") — the real letter lives at `response.overall.letter`
        // instead. Reading only `grade` turned every V7 report's
        // overallGrade into the "?" sentinel here, feeding
        // GradeBadge/GradeSummaryCard/ReportPdfGenerator's grade
        // hero with a permanently-unknown letter even when the
        // backend sent a real one. See
        // AiVerdictPresentation.resolveOverallGrade's doc — this is
        // the same `?: "?"` absence-handling gap that class already
        // flagged as "out of scope for this phase" when it was
        // written for the on-screen verdict card in Phase 3-b; this
        // is that fix, one layer up, now that ReportPdfGenerator
        // (this field's remaining unbuilt consumer) is unblocked.
        overallGrade = AiVerdictPresentation.resolveOverallGrade(
            pipeline = response.pipeline,
            v6Grade = response.grade,
            v7Letter = response.overall?.letter
        ),
        gradeDescription = "",
        gradeDescriptionTr = "",
        axisScores = emptyMap(),
        categoryScores = catScores,
        individualScores = emptyMap(),
        criticalFailures = emptyList(),
        antiTamperPenalty = antiTamperPenalty,
        isIncomplete = isIncomplete,
        scoringMethod = "ai_v1",
        aiAnalysis = analysis,
        // Mirror the AI struct's shortId so screens can read it
        // straight off the top-level result without unwrapping
        // the nested AI block. Same value either way.
        shortId = analysis.shortId,
        error = null
    )
}
