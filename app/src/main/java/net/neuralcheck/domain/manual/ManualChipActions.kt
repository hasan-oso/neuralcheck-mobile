package net.neuralcheck.domain.manual

/**
 * What tapping one of a manual row's answer chips («يعمل» / «لا يعمل» / «لم أختبر», and the fourth answer's own line) does to the RAW already stored
 * with the row — the one place it is decided.
 *
 * Pure Kotlin, no Android and no Compose: the decision lives here, where a plain test can call it without drawing the row, and the row only
 * carries it out.
 *
 * Decision history, newest first:
 *  - User decision 2026-10-21 (after the device round on the APK of f1a38be; the user's words: «M-09 وM-10 (رفع وخفض الصوت): «يعمل» لا يفتح الحوار،
 *    كما M-02. قرار M7(ب) يسري عليهما»): the rule of M7(ب) below holds on the two volume rows too. This REPLACES user approval E22 of 2026-10-15
 *    («M-09/M-10: «يعمل» يفتح الحوار…»), which [PASS_OPENS_DIALOG] (M-09 and M-10) carried out from order A-VOL of 2026-10-18 until then: that set, the
 *    «open the dialog» action and the decision function around them are GONE — no row's chip opens a dialog any more. «يعمل» inside the volume
 *    dialog (`user_marked_working`) is the dialog's own button and did not change.
 *  - On M-02 (the touch grid): user decision 2026-10-18 (M7(ب), the user's words: «تأشير «يعمل» لا يفتح الاختبار تلقائياً، بل يؤشّر الفنّي بلا فتحه»):
 *    «يعمل» on M-02 WRITES `pass` like on every other row and opens nothing. The grid opens only from the row's «ابدأ الاختبار» button
 *    (`ManualRowActions.onStartTest`). This REVERSES the decision of 2026-10-13 (commit 9ca01ec: «pass on M-02 from the grid alone», the
 *    «يعمل» chip of M-02 opened the grid).
 *  - What a `pass` on M-02 / M-09 / M-10 means is therefore two things: a technician's tap (no raw of its own) and a finished run (the grid's
 *    [TouchGridRecorder.EndReasons.AUTO_COMPLETE], the volume dialog's `auto_detected`, written together with its raw). The raw is what tells them apart.
 *
 * What does NOT change: the view model's rule that the latest write for a test replaces both the answer and its raw
 * (`ManualTestsViewModel.setResult`) — it holds for every test, with the one exception [rawToStore] names: a chip tap on a row that records a raw
 * run (any of its answers, E21 item 3, approved 2026-10-15; the two volume rows since 2026-10-21) writes the answer only and keeps the raw stored.
 */
object ManualChipActions {

    /**
     * The rows where a chip tap — ANY of the four answers: works, not works, not tested, unavailable — writes the answer and nothing but the
     * answer: the raw already stored with the row stays — ONE source (rule 24). Today: the three rows whose answer comes with a raw run — M-02 (the
     * grid's) and M-09 / M-10 (the volume dialog's); a row's «يعمل» no longer opens any dialog, so what a tap writes is only ever the technician's word.
     *
     * User decision 2026-10-15 ~18:22 +03, E21 item 3 of docs/FV68_CONTRACT.md section 7-ي (approved), for M-02: «نقرة شريحة (يعمل ⇒ يفتح الحوار ·
     * لا يعمل · لم أختبر · غير متوفر) تكتب الجواب فقط وتُبقي الخامّ الموجود» — the raw says how THAT run ended (`endReason`), the row says the
     * technician's last answer, and the reader never derives one from the other. Rule 20: the raw is data and a tap is not a run.
     * The order of 2026-10-21 (app-engineer-6 — an instruction of the order that applies E21 item 3 to the two volume rows once the user's decision made
     * their «يعمل» a plain write; not a quoted decision of the user): «نقرات الشرائح الأربع (E21-3) على M-09/M-10 تكتب جوابها وتُبقي الخامّ المخزَّن إن وُجد
     * كما M-02» — M-09 and M-10 join. (M7(ب), 2026-10-18, first kept it for a works tap over a stored `pass` on M-02 only; E21 item 3
     * widened it to the other chips and to any stored answer.)
     */
    val TAP_KEEPS_STORED_RAW: Set<String> =
        setOf(TouchGridRecorder.TEST_ID, VolumeKeyRecorder.TEST_ID_UP, VolumeKeyRecorder.TEST_ID_DOWN)

    /** Whether a write with no run on [testId] has to look at what is stored before it writes ([rawToStore]) — whatever the answer. */
    fun keepsStoredRaw(testId: String): Boolean = testId in TAP_KEEPS_STORED_RAW

    /**
     * The raw (the `measurements` column text) a write stores — the one rule of what a write does to the raw already there.
     *
     * - A write that comes with a run's raw ([runRaw] not null) stores it: the run is the newest word, as ever (a new run replaces the old
     *   one; two runs are never kept side by side).
     * - A write with no run (a chip tap) on a row in [TAP_KEEPS_STORED_RAW] keeps the stored raw, whatever answer it writes and whatever answer
     *   was stored — which may itself be null.
     * - Every other write with no run stores no raw (any other row has no raw but a dialog's own).
     *
     * @param storedRaw the stored `measurements` of that row, or null
     */
    fun rawToStore(testId: String, runRaw: String?, storedRaw: String?): String? = when {
        runRaw != null -> runRaw
        keepsStoredRaw(testId) -> storedRaw
        else -> null
    }
}
