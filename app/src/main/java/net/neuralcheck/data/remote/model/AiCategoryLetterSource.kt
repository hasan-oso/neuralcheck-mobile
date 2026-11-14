package net.neuralcheck.data.remote.model

/**
 * Single exported source for the V7 CATEGORY-level `letterSource`
 * discriminator (`categories.*.letterSource`).
 *
 * NOT the same vocabulary as [AiOverallV7.letterSource] — that one is
 * gate-name-based free text (e.g. "GATE:ROOT_DETECTED"), documented on
 * [AiOverallV7.letterSource] itself. This object covers ONLY the two-value
 * enum documented for the per-category field of the same name:
 *
 *   "DETERMINISTIC للفئة: DETERMINISTIC | LLM_ADJUSTED. ومعه السبب ومرجع
 *   الدليل — فالمستخدم يعرف لماذا نزلت الفئة، ومن أنزلها."
 *   (relayed verbatim in the Phase 3-b task; see NEURALCHECK-FINDINGS.md)
 *
 * Every category in the golden sample
 * (`app/src/test/resources/contract/v7-client-payload-sample.json`) is
 * "DETERMINISTIC" — the LLM_ADJUSTED shape is unconfirmed by a positive
 * sample value (see [AiCategoryV7.adjustmentReason] doc) and is exercised
 * in this codebase only via an artificial fixture built directly in
 * `AiV7PresentationTest`, labeled artificial there, never presented as
 * sample-derived.
 *
 * Project rule (A102 — the "v7_findings_based" drift across seven files):
 * a literal a distinction is built on lives in exactly one exported place,
 * never copy-pasted per call site. [net.neuralcheck.util.AiV7Presentation]
 * is the only reader of these two constants.
 */
object AiCategoryLetterSource {
    const val DETERMINISTIC = "DETERMINISTIC"
    const val LLM_ADJUSTED = "LLM_ADJUSTED"
}
