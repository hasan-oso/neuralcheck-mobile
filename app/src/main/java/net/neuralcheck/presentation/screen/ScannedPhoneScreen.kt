package net.neuralcheck.presentation.screen

/**
 * A-P6-09 («الهاتف المفحوص بشريط وحده», `plan/PLAN-06-DATA-COLLECTION.md:1050`): what the scan screen of the SCANNED phone shows while a scan runs, decided in ONE place.
 *
 * What the plan says (quoted; the screen is row ف١٢):
 *  - the user's decision 1 (PLAN-06:1182): «والهاتف المفحوص لا يعرض إلا شريط تقدّم»;
 *  - PLAN-06:954 (ف١٢ «الفحص: شريط تقدّم وحده»): «`ActiveScanScreen.kt:349-354` (البطاقة تخرج) والرسمان `:435-450` يخرجان في الجلسة», and the one sentence «جارٍ الفحص. اترك الهاتف ولا تلمسه.»;
 *  - PLAN-06:1241 «وبطاقة هاتف الزبون تخرج (P6-09)» and PLAN-06:1583 «إخفاء الرسوم على الهاتف المفحوص (P6-09)».
 * At the commit the plan's line numbers were written against (`f1c7db9`) `:349-354` is `TechnicianEvaluationCard(evalViewModel)` and `:435-450` are the two charts, `CpuUsageChart` and `BatteryTempChart`.
 * So the first build of this row (`2e2e024`) took out of a SESSION scan the two charts and the card, and nothing else: the plan names nothing else.
 *
 * The coordinator's decision of 2026-10-21 (the follow-up the previous builder recommended; the plan's own sentence is decision 1, PLAN-06:1182) takes out two more: the LIVE TEMPERATURE tile and the LIVE
 * CPU-USAGE tile. The charts drew the same two readings the tiles show, so with the charts gone the tiles kept the live measurement in front of the technician, who evaluates blind. What stays on a
 * session scan: the phase name and its five dots, the minute and the percentage, the tips, the tiles of TIME REMAINING and BATTERY LEVEL (functional - how long is left, how much charge - not a
 * reading of the scan's result), the stop button and the cover-the-sensor prompt. A personal scan (no session) keeps every tile and every chart.
 *
 * The parts, each a plain function of two facts:
 *  - [Parts.charts] and [Parts.liveTiles]: ONLY a scan known to have started in no session (`startedInSession == false`). `null` is the scan whose id the service has not minted yet - its session is not
 *    known, and rule 22 says an absence is its own state, not a `false`: nothing of the two is drawn for it, so no chart or reading ever flashes on the screen of a scan that turns out to be a session's.
 *    (A personal scan gets its charts and its two tiles a moment later, when the first Running state names the scan.) The two are separate parts although they read the same fact today: the screen
 *    obeys one line for each, and a later decision about one does not touch the other.
 *  - [Parts.evaluationCard]: only while the technician's form is NOT live. The card is today's only evaluation path; the form of A-P6-08 replaces it (PLAN-06:1239 «تُطفأ بطاقة التقييم على هاتف الزبون ... نعم = مسار
 *    تقييم واحد»), and the form is behind [TECH_FORM_TEXTS_APPROVED] (open since 2026-10-23). So the card leaves with the same switch that lets the form in: one change, never two paths. NOT never none: the
 *    switch is the app's, not the shop's - a shop whose form part is off (`ConfigUi.NotEnabled`) gets no form, and the card is gone for it too (pinned by `FormLiveScreenShapeTest`, reported as a known effect).
 *    The card keeps its own gates (an evaluation session, a draft) - this is only the line above them.
 *  - [Parts.inProgressSentence]: a session scan with the form live - the sentence is [FormText.ScanInProgress] (approved by delegation 2026-10-23), read through the same labels (null while the guard is shut).
 */
internal object ScannedPhoneScreen {

    /** What the scan screen draws, beyond what every scan draws (the progress bar, the time-remaining and battery tiles, and what is not named above). */
    data class Parts(val charts: Boolean, val liveTiles: Boolean, val evaluationCard: Boolean, val inProgressSentence: Boolean)

    /**
     * @param startedInSession `true`: the scan started in a session; `false`: it started in none (a personal scan); `null`: not known yet (the scan has no id).
     * @param formLive whether the technician's form is live ([TECH_FORM_TEXTS_APPROVED]).
     */
    fun parts(startedInSession: Boolean?, formLive: Boolean): Parts = Parts(
        charts = startedInSession == false,
        liveTiles = startedInSession == false,
        evaluationCard = !formLive,
        inProgressSentence = startedInSession == true && formLive
    )
}
