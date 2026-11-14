package net.neuralcheck.presentation.screen

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Order 2026-09-26 (القرار ١٠٢ البند ١). Proves [scanHeaderDateText]'s
 * decision directly — the date-vs-fallback branch this task's order named
 * ("يُعرض التقرير بلا وقت، مع نصّ «تاريخ الفحص: غير متوفر»"). The two
 * string resources it composes from (`report_scan_date`,
 * `report_v7_field_unavailable`) are resolved by the ONE real caller in
 * `ReportContent` — not re-resolved here, since this function takes the
 * already-resolved text as a plain parameter precisely so it can be
 * tested without drawing the screen (see the function's own doc).
 */
class ScanHeaderDateTextTest {

    @Test
    fun `a real timestamp does not fall back to the unavailable text`() {
        // Not asserting the exact formatted digits here: SimpleDateFormat
        // with Locale.getDefault() can render native (non-ASCII) digits
        // depending on the JVM's default locale — the pre-existing,
        // unchanged behaviour of this line before this order (this test
        // caught its own over-fitted `\d`-only regex assumption on a
        // non-Latin-digit default locale on first write). The one thing
        // this order actually changed — falling back when null — is what
        // this test proves; exact digit rendering is NumberFormat.toLatin's
        // job at the real call site in ReportContent, not this function's.
        val result = scanHeaderDateText(1789884505000L, unavailableText = "SHOULD_NOT_APPEAR")
        assertEquals(false, result.contains("SHOULD_NOT_APPEAR"))
    }

    @Test
    fun `a null timestamp returns the fallback text verbatim`() {
        val result = scanHeaderDateText(null, unavailableText = "تاريخ الفحص: غير متوفر")
        assertEquals("تاريخ الفحص: غير متوفر", result)
    }
}
