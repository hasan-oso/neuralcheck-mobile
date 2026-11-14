package net.neuralcheck.domain.manual

/**
 * The manual-test rows that OFFER the answer `unavailable` (its text is the one string `manual_mark_unavailable`) — ONE list
 * (rule 24), read by both places that offer it: the row's own line under the chip row and the routed dialogs' own button. Batch 1.0.12, second
 * part (user decision 2026-10-13: M-08, M-12, M-14, M-15 — the four whose dialogs carried the button before there was a chip); narrowed by the
 * user's decision of 2026-10-15 (station-2 build) to M-08, M-14 and M-15: M-12 leaves.
 *
 * Before this list the four were implicit: each dialog hard-coded the button, the row offered nothing. Now a row that is not here has neither the
 * line nor the button; a row added here gets the line, and the dialog of that test reads it from the row
 * (`ManualTestRow` passes `offerUnavailable = isOffered(item.id)` to the dialogs that have the button). M-12 keeps its dialog and the dialog keeps
 * the `offerUnavailable` parameter (the shared battery-state detector it uses is M-14's too): the row passes `false` for M-12 because the row asks
 * THIS list, so M-12 has no button — adding it back to the list is the whole change.
 *
 * Not in the list, on purpose: the OTG (M-13) and IR (M-16) dialogs still hold the button in the source but have no route and are
 * retired from the catalog (`ReportScreen.kt`, `interactiveManualTests`); they are dead code and not offered.
 *
 * Pure Kotlin, so a plain JVM test can call it (`ManualUnavailableOfferTest`); `ManualRowSourceShapeTest` proves the line and the dialogs read it,
 * and `ManualUnavailableLineLayoutTest` (Robolectric, batch 2) renders the real row for each of these three and measures the line.
 */
object ManualUnavailableOffer {

    /** In catalog order. A row added or removed here is a user decision (2026-10-13; narrowed 2026-10-15), and the test names the three. */
    val TEST_IDS: List<String> = listOf("M-08", "M-14", "M-15")

    fun isOffered(testId: String): Boolean = testId in TEST_IDS
}
