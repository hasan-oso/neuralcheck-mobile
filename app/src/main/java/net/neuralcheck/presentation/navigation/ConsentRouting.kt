package net.neuralcheck.presentation.navigation

import net.neuralcheck.domain.consent.ConsentPartRead

/**
 * Where a join leads (A-P6-12; PLAN-06:1388 «مسار `consent` بين الانضمام و`pre_scan`»; the two places a join ends are the ones the plan names, `QRScannerScreen.kt` and `JoinSessionScreen.kt`, row `ف١٠` `:952`):
 * to the consent screen when it is live AND the shop has the consent part on, else to the pre-scan screen as before this row. One function, so the two join entry points cannot disagree (rule 24); the caller says
 * whether the screen is live ([net.neuralcheck.presentation.screen.CONSENT_TEXTS_APPROVED]) and what the join's answer said about the shop's part ([ConsentPartRead], ق15) so no default can hide a missing answer.
 * Only [ConsentPartRead.Enabled] leads to the consent screen: a shop whose part is [ConsentPartRead.NotEnabled] has none, and an answer that did not carry the key ([ConsentPartRead.Absent]) leaves the scan
 * without a consent screen too - the server would refuse to record one - with the absence declared, not read as `false` (rule 22). The consent screen itself leaves for [PRE_SCAN_AFTER_JOIN_ROUTE] only when the
 * server has recorded the consent (`ConsentUi.Recorded`) or says there is none to record (`ConsentUi.NotApplicable`) - «الفحص لا يبدأ حتى تُسجَّل» (PLAN-06:1402) for a shop that has the part.
 */
internal const val CONSENT_ROUTE = "consent"

/** The pre-scan screen of a joined device: a general scan, linked to no earlier scan. */
internal const val PRE_SCAN_AFTER_JOIN_ROUTE = "pre_scan/general/none"

internal fun afterJoinRoute(consentLive: Boolean, consentPart: ConsentPartRead): String = when (consentPart) {
    ConsentPartRead.Enabled -> if (consentLive) CONSENT_ROUTE else PRE_SCAN_AFTER_JOIN_ROUTE
    ConsentPartRead.NotEnabled, ConsentPartRead.Absent -> PRE_SCAN_AFTER_JOIN_ROUTE
}
