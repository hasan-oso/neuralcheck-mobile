package net.neuralcheck.data.remote.model

import com.google.gson.JsonParser

/**
 * Root-level counterpart to `V7RawFieldPresence.kt`'s
 * [categoriesMissingLetterCeilingReason] — deliberately a SEPARATE file,
 * not an addition to that one. Order 2026-09-23 (القرار ٧٢) item 3 froze
 * that file's existing mechanism pending a neuralcheck-backend answer
 * ("لا تغيّر شيئاً فيه الآن"); this is new code for a DIFFERENT field
 * (root `narrativeIncomplete`, not `categories.*.letterCeilingReason`),
 * using the same technique (an independent Gson tree-model walk of the
 * raw JSON, since a field-level Gson adapter is never invoked at all for
 * an absent key — see that file's class doc for the full reasoning) —
 * not a change to the frozen function.
 *
 * Same open question as the frozen mechanism, inherited rather than
 * re-decided here: whether the backend always sends `narrativeIncomplete`
 * (even as `false`) on every report, or only starting from some point.
 * Order item 2 requires the absent case not be read as "complete" either
 * way; how a caller defaults when EVEN THIS SIGNAL itself is unavailable
 * (e.g. an older Room-cached [net.neuralcheck.domain.model.AiAnalysisResult]
 * with `narrativeIncompleteDeclared == null`) is documented at that
 * field's own declaration, not decided here.
 */
fun isNarrativeIncompleteDeclared(json: String): Boolean {
    val root = runCatching { JsonParser.parseString(json) }.getOrNull() ?: return false
    if (!root.isJsonObject) return false
    return root.asJsonObject.has("narrativeIncomplete")
}

/**
 * Same technique as [isNarrativeIncompleteDeclared] above, for the ROOT
 * `issues` key. Added 2026-09-24 (جولة جرد القيم الافتراضية، البند ج،
 * القرار ٧٦) — see [AiAnalysisResponse.issuesDeclared]'s doc for why a plain
 * Gson-typed decode of [AiAnalysisResponse.issues] cannot answer this by
 * itself (an absent key and an explicit JSON `null` both land on Kotlin
 * `null`, and [net.neuralcheck.domain.scoring.AiScoringEngine] additionally
 * flattens that to an empty list before it reaches the domain model,
 * erasing the distinction a second time). `issues` is shared shape between
 * V6 and V7, so this check is meaningful for either pipeline's raw JSON.
 */
fun areIssuesDeclared(json: String): Boolean {
    val root = runCatching { JsonParser.parseString(json) }.getOrNull() ?: return false
    if (!root.isJsonObject) return false
    return root.asJsonObject.has("issues")
}

/**
 * Whether the raw JSON's `overall` object carries `overall.harnessError` at
 * all. Same technique as [isNarrativeIncompleteDeclared]/[areIssuesDeclared]
 * above, but the key checked is nested inside root `overall`, not at the
 * document root.
 *
 * Added 2026-09-24 (جولة جرد القيم الافتراضية، البند ح، القرار ٧٦), after
 * neuralcheck-backend's confirmed answer (relayed 2026-09-24, backend HEAD —
 * `functions/src/analysis/analyzeDevice.js:170/173/176`
 * (`partialAssessment: !!overall.partialAssessment`, `noDataAtAll: !!…`,
 * `harnessError: !!…`), `buildDisplayShapeV7.js:454/460`, and
 * `verifyReport`'s `!!pickOverall(...)` — all three of the app's read paths
 * for a V7 report; all three of a stored-doc/RPC-return/verifyReport sample
 * carry `harnessError=false · noDataAtAll=false · partialAssessment=true`,
 * no absence in any): the backend ALWAYS emits `harnessError` (coerced
 * through `!!`, so the JSON value is always a real `true`/`false`, never a
 * JSON `null`) on every path this app reads from. A `null`
 * [net.neuralcheck.data.remote.model.AiOverallV7.harnessError] at the
 * Kotlin level can therefore now only mean "this key was absent from the
 * raw report" — an older Room-cached [net.neuralcheck.domain.model.AiAnalysisResult]
 * (predates this signal being tracked), a pre-`!!`-coercion backend
 * response, or a malformed/partial doc — never "the backend said false".
 * [net.neuralcheck.util.AiV7Presentation.classifyOverallStatus] must not
 * read that absence as [net.neuralcheck.util.AiV7Presentation.OverallStatusKind.NORMAL].
 */
fun isHarnessErrorDeclared(json: String): Boolean = isOverallKeyDeclared(json, "harnessError")

/** Same as [isHarnessErrorDeclared], for `overall.noDataAtAll`. */
fun isNoDataAtAllDeclared(json: String): Boolean = isOverallKeyDeclared(json, "noDataAtAll")

/** Same as [isHarnessErrorDeclared], for `overall.partialAssessment`. */
fun isPartialAssessmentDeclared(json: String): Boolean = isOverallKeyDeclared(json, "partialAssessment")

private fun isOverallKeyDeclared(json: String, key: String): Boolean {
    val root = runCatching { JsonParser.parseString(json) }.getOrNull() ?: return false
    if (!root.isJsonObject) return false
    val overall = root.asJsonObject.get("overall")?.takeIf { it.isJsonObject } ?: return false
    return overall.asJsonObject.has(key)
}
