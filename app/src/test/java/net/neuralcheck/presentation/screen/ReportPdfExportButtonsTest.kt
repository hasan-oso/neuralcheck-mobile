package net.neuralcheck.presentation.screen

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Order 2026-09-23 (القرار ٧٣، البند ٢). Test ceiling per the order's own
 * text: "أقصاه أن المكوّن يقرأ الراية؛ والرسم يُحسم على الجهاز" — this
 * confirms the single flag's current build value. That `ReportScreen.kt`'s
 * Share/Save `TextButton`s are each wrapped in
 * `if (!ReportPdfExportButtons.HIDDEN)` (and the QR button is NOT) is
 * verified separately by reading that file directly (when written this module did not have
 * Robolectric/Compose UI test infrastructure to prove the Composable
 * actually omits them on a rendered screen; both are test dependencies since 3284cca, and no test renders it — see
 * [ReportPdfExportButtons]'s own doc).
 */
class ReportPdfExportButtonsTest {

    @Test
    fun `current build value is HIDDEN - Share and Save buttons do not render`() {
        assertTrue(ReportPdfExportButtons.HIDDEN)
    }
}
