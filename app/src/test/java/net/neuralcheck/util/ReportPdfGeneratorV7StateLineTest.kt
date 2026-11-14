package net.neuralcheck.util

import android.content.Context
import net.neuralcheck.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Order 2026-09-24 (القرار ٨٢ البند ٤). `ReportPdfGenerator.v7StateLine`
 * (made `internal` this round, was `private`) is the PDF-side twin of
 * ReportScreen.kt's matching `when` block over
 * [AiV7Presentation.CategoryStateKind] — this is the only one of the two
 * that is directly unit-testable without drawing anything (ReportScreen's copy
 * is inline inside a `@Composable`), so it carries the direct proof that
 * ATTESTED_ONLY / HARNESS_ERROR now resolve to their own dedicated text
 * instead of the generic `report_v7_state_uninterpreted_fmt` template —
 * exactly the behaviour change this order made. NO_DATA is asserted to
 * stay on the generic template, unchanged (its own dedicated text is
 * القرار ٨٢ البند ٥, explicitly paused this round).
 */
class ReportPdfGeneratorV7StateLineTest {

    private fun fakeContext(): Context {
        val context = mock<Context>()
        whenever(context.getString(R.string.report_v7_state_partial)).thenReturn("PARTIAL_TEXT")
        whenever(context.getString(R.string.report_category_unmeasured)).thenReturn("UNMEASURED_TEXT")
        whenever(context.getString(R.string.report_v7_state_unknown)).thenReturn("UNKNOWN_TEXT")
        whenever(context.getString(R.string.report_v7_state_attested_only)).thenReturn("ATTESTED_ONLY_TEXT")
        whenever(context.getString(R.string.report_v7_state_harness_error)).thenReturn("HARNESS_ERROR_TEXT")
        // Stubbed for the exact argument this test passes — avoids
        // vararg-matcher ambiguity on Context.getString(Int, vararg Any?).
        whenever(context.getString(R.string.report_v7_state_uninterpreted_fmt, "NO_DATA"))
            .thenReturn("UNINTERPRETED(NO_DATA)")
        return context
    }

    @Test
    fun `MEASURED has no state line`() {
        val generator = ReportPdfGenerator(fakeContext())
        assertNull(generator.v7StateLine(AiV7Presentation.CategoryStateKind.MEASURED, "MEASURED"))
    }

    @Test
    fun `ATTESTED_ONLY resolves to its own dedicated text, not the generic uninterpreted template`() {
        val generator = ReportPdfGenerator(fakeContext())
        assertEquals(
            "ATTESTED_ONLY_TEXT",
            generator.v7StateLine(AiV7Presentation.CategoryStateKind.ATTESTED_ONLY, "ATTESTED_ONLY")
        )
    }

    @Test
    fun `HARNESS_ERROR resolves to its own dedicated text, not the generic uninterpreted template`() {
        val generator = ReportPdfGenerator(fakeContext())
        assertEquals(
            "HARNESS_ERROR_TEXT",
            generator.v7StateLine(AiV7Presentation.CategoryStateKind.HARNESS_ERROR, "HARNESS_ERROR")
        )
    }

    @Test
    fun `NO_DATA still falls back to the generic uninterpreted template, unchanged`() {
        val generator = ReportPdfGenerator(fakeContext())
        assertEquals(
            "UNINTERPRETED(NO_DATA)",
            generator.v7StateLine(AiV7Presentation.CategoryStateKind.NO_DATA, "NO_DATA")
        )
    }

    @Test
    fun `UNKNOWN still resolves to the pre-existing unknown text, unchanged`() {
        val generator = ReportPdfGenerator(fakeContext())
        assertEquals(
            "UNKNOWN_TEXT",
            generator.v7StateLine(AiV7Presentation.CategoryStateKind.UNKNOWN, "something_else")
        )
    }
}
