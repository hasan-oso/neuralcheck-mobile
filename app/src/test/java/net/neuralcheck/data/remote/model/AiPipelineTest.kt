package net.neuralcheck.data.remote.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 3-a. [AiPipeline] is the single exported source for the
 * `pipeline` discriminator literal (rule 24 — "v7_findings_based" must
 * not be copy-pasted per call site). This test pins the exact literal
 * values (a typo here would silently reclassify every V7 report as
 * unparseable V6-legacy, or vice versa) and the three-state /
 * fourth-unknown-value semantics of [AiPipeline.isRecognizedFlatShape].
 */
class AiPipelineTest {

    @Test
    fun `literal values are exactly what the relayed contract states`() {
        assertEquals("v7_findings_based", AiPipeline.V7_FINDINGS_BASED)
        assertEquals("v6_raw_timeseries", AiPipeline.V6_RAW_TIMESERIES)
        // Order 2026-09-26 (القرار ١٠٢ البند ٢) — منقول من neuralcheck-backend،
        // غير مُتحقَّق من هذا المستودع.
        assertEquals("v3_legacy", AiPipeline.V3_LEGACY)
        assertEquals("v5_precomputed", AiPipeline.V5_PRECOMPUTED)
        assertEquals("cached", AiPipeline.CACHED)
    }

    @Test
    fun `absent pipeline (legacy, pre-dates the field) is a recognized flat shape`() {
        assertTrue(AiPipeline.isRecognizedFlatShape(null))
    }

    @Test
    fun `explicit v6 marker is a recognized flat shape`() {
        assertTrue(AiPipeline.isRecognizedFlatShape(AiPipeline.V6_RAW_TIMESERIES))
    }

    @Test
    fun `v7 is NOT a recognized flat shape`() {
        assertFalse(AiPipeline.isRecognizedFlatShape(AiPipeline.V7_FINDINGS_BASED))
    }

    @Test
    fun `an unknown fourth pipeline value is NOT a recognized flat shape`() {
        // Rule 22 — absence/unknown must never read as a silent success.
        // A future pipeline value this app doesn't know about yet must be
        // treated the same as V7: NOT flat, not silently read as V6.
        assertFalse(AiPipeline.isRecognizedFlatShape("v8_something_new"))
        assertFalse(AiPipeline.isRecognizedFlatShape(""))
    }

    // Order 2026-09-26 (القرار ١٠٢ البند ٢): v3_legacy/v5_precomputed
    // widened isRecognizedFlatShape — both root-shape V6.

    @Test
    fun `v3_legacy is a recognized flat shape`() {
        assertTrue(AiPipeline.isRecognizedFlatShape(AiPipeline.V3_LEGACY))
    }

    @Test
    fun `v5_precomputed is a recognized flat shape`() {
        assertTrue(AiPipeline.isRecognizedFlatShape(AiPipeline.V5_PRECOMPUTED))
    }

    @Test
    fun `cached is NOT a recognized flat shape by tag alone - its shape depends on content`() {
        assertFalse(AiPipeline.isRecognizedFlatShape(AiPipeline.CACHED))
    }

    // ── classifyShape (القرار ١٠٢ البند ٢) — القاعدة 30: every enumerated
    // value tested with every one of its possible outcomes, not one
    // sample. ──

    @Test
    fun `classifyShape - v7_findings_based is always V7, regardless of content signals`() {
        assertEquals(
            AiPipeline.ShapeKind.V7,
            AiPipeline.classifyShape(AiPipeline.V7_FINDINGS_BASED, hasRootLetterOrGrade = true, hasV7Overall = false)
        )
        assertEquals(
            AiPipeline.ShapeKind.V7,
            AiPipeline.classifyShape(AiPipeline.V7_FINDINGS_BASED, hasRootLetterOrGrade = false, hasV7Overall = false)
        )
    }

    @Test
    fun `classifyShape - null, v6_raw_timeseries, v3_legacy, v5_precomputed are always V6`() {
        for (tag in listOf(null, AiPipeline.V6_RAW_TIMESERIES, AiPipeline.V3_LEGACY, AiPipeline.V5_PRECOMPUTED)) {
            assertEquals(
                "tag=$tag",
                AiPipeline.ShapeKind.V6,
                AiPipeline.classifyShape(tag, hasRootLetterOrGrade = false, hasV7Overall = false)
            )
        }
    }

    @Test
    fun `classifyShape - cached routes by content - V7 overall wins, then root grade, then UNKNOWN`() {
        assertEquals(
            AiPipeline.ShapeKind.V7,
            AiPipeline.classifyShape(AiPipeline.CACHED, hasRootLetterOrGrade = true, hasV7Overall = true)
        )
        assertEquals(
            AiPipeline.ShapeKind.V6,
            AiPipeline.classifyShape(AiPipeline.CACHED, hasRootLetterOrGrade = true, hasV7Overall = false)
        )
        assertEquals(
            AiPipeline.ShapeKind.UNKNOWN,
            AiPipeline.classifyShape(AiPipeline.CACHED, hasRootLetterOrGrade = false, hasV7Overall = false)
        )
    }

    @Test
    fun `classifyShape - a wholly unrecognised tag is always UNKNOWN, regardless of content signals`() {
        assertEquals(
            AiPipeline.ShapeKind.UNKNOWN,
            AiPipeline.classifyShape("v8_something_new", hasRootLetterOrGrade = true, hasV7Overall = true)
        )
    }
}
