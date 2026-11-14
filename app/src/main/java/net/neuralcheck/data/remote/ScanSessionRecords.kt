package net.neuralcheck.data.remote

import android.content.SharedPreferences
import net.neuralcheck.util.EvalCardLog

/**
 * The session a scan STARTED in, kept per scan.
 *
 * Order 2026-10-14 (user decision, device check 03 round 2, the option «Record the session at scan start»), in the
 * user's words: "The scan keeps the session it started in; its analysis and its evaluation draft use that one, never
 * the session joined later. No new text."
 *
 * ── The defect this ends ──
 * [ScanSessionHolder] is ONE mutable slot for "the session this device last joined". The scan screen read it when
 * it was built, and `AiScoringEngine.calculateScore` read it again, minutes later — so a session joined in between
 * (B, after the scan started under A) was sent as the session of A's scan, and a rejection of that analysis cleared
 * the slot, so the retry for the same scan lost its session altogether. Server side (relayed by the coordinator from
 * neuralcheck-backend `a666036`: `analyzeDevice.js:371`, `submitEvaluation.js:97`; NOT read from this repository)
 * the server judges the session id it is sent, so the app must send the right one; "the app alone is enough".
 *
 * ── What this holds ──
 * [recordAtScanStart], called once by `StressTestService.startScan` the moment the scan's id is minted, copies the
 * holder's session (id and the join answer's `evaluationEnabled`) under that scan id. From then on the scan's
 * analysis ([Use.ANALYSIS], `AiScoringEngine`) and its evaluation draft ([Use.DRAFT],
 * `TechnicianEvaluationViewModel.bind`) ask [sessionFor] — never the holder. A scan with NO record is a personal
 * scan and stays one: [sessionFor] answers null whatever the holder holds now (rule 22: the absent record is a
 * named state — `scan-session-holder-ignored` when the holder holds a session the scan did not start in — never a
 * fall-back to the holder).
 *
 * ── Where it is kept, and why (the decision is mine; the order left it open) ──
 * A private SharedPreferences file, one entry per scan: `<scanId>` → `<evaluationEnabled>|<recordedAtMs>|<sessionId>`,
 *  where `<evaluationEnabled>` is `true`, `false` or — since 2026-10-15 (rule 22) — `absent`: the join answer did not carry
 *  the flag. Entries written before that carry only `true`/`false` and still read the same.
 *  - It must survive the process: the analysis can be re-run from the history after hours (a failed one is retried
 *    from the report screen, a killed app is reopened), long after the in-memory holder is gone. So not memory.
 *  - It needs no Room schema change. `NeuralCheckDatabase` is at schema 8 with a migration chain
 *    (`NeuralCheckDatabase.ALL_MIGRATIONS`) and `DatabaseModule` keeps `fallbackToDestructiveMigration()` as a net;
 *    a new `scans` column would be a v8 -> v9 migration for two small values, and the order said "better without a
 *    migration if possible". So not a Room column.
 *  - The evaluation row ([net.neuralcheck.data.local.db.entity.SessionEvaluationEntity]) already carries its own
 *    session id, but only once the technician answers, and only in evaluation sessions — not every scan in a session.
 *  - Same storage family as `DestructiveMigrationTracker` (plain, `MODE_PRIVATE`, on-device only, no network); the
 *    session id is already stored in Room by the evaluation row, so plain is no new exposure.
 * One value per scan with strict parsing (not three keys): a half-written entry cannot exist, and an entry that does
 * not parse is reported ([EvalCardLog.scanSessionUnreadable]) instead of being read as "no session".
 *
 * Entries older than [RETENTION_MS] are removed when a new one is written — a bound on the file, not a policy: the
 * server's session lifetime is 60 minutes from creation (relayed, `a666036`; NOT verified here), so a day-old record
 * can only be answered by "the session expired".
 *
 * A record is consumed ([consume]) after a successful analysis and KEPT after any failure, so the retry for the same
 * scan after a rejection still carries its session (the user's item 6; see `shouldConsumeRecordAfterAnalysis`).
 *
 * Every public call is synchronized: `StressTestService` (main thread), the view model (main) and the engine (an IO
 * coroutine) all reach it.
 */
class ScanSessionRecords(
    private val prefs: SharedPreferences,
    private val holder: ScanSessionHolder,
    private val nowMs: () -> Long = { System.currentTimeMillis() }
) {

    /**
     * What a scan started in: [sessionId] (never blank) and the join answer's [evaluationEnabled] at that moment —
     * `null` when the answer did not carry it (kept as absent, not read as `false`; the card's gate says what it does with it).
     */
    data class Recorded(val sessionId: String, val evaluationEnabled: Boolean?)

    /** Who asks; printed by the lines. Wire strings are explicit (R8-safe). */
    enum class Use(val wire: String) {
        /** `AiScoringEngine.calculateScore` — the analysis request. */
        ANALYSIS("analysis"),

        /** `TechnicianEvaluationViewModel.bind` — the evaluation draft's session snapshot. */
        DRAFT("draft"),

        /** Batch 2, item 2 (D2): [SessionLeaver] - the session a leave from the scan screen cancels (the one the scan started in). */
        LEAVE("leave"),

        /** Batch 2, item 2 (D5): [CustomerSessionWatch] - the session whose document the scan screen follows. */
        WATCH("watch"),

        /**
         * A-P6-08 (the technician form's blindness): `ReportViewModel.loadScan` - the session whose first report-open the customer's phone tells the server about
         * ([ReportFirstOpenedRecorder]). Read BEFORE the analysis, which spends the record on success; asked only while the form's gate is open, so nothing is read or printed for it today.
         */
        REPORT("report")
    }

    /**
     * Copies the holder's session under [scanId]; returns what was recorded, or null when the holder held none (a
     * personal scan: nothing is stored). Always prints `scan-session-recorded`, so the absence is visible in logcat.
     */
    @Synchronized
    fun recordAtScanStart(scanId: String): Recorded? {
        val info = holder.getInfo()?.takeIf { it.sessionId.isNotBlank() }
        val recorded = info?.let { Recorded(it.sessionId, it.evaluationEnabled) }
        if (scanId.isNotBlank()) {
            val now = nowMs()
            val editor = prefs.edit()
            for ((key, value) in prefs.all) {
                val entry = (value as? String)?.let(::decode)
                if (entry == null || now - entry.recordedAtMs > RETENTION_MS) editor.remove(key)
            }
            if (recorded != null) editor.putString(scanId, encode(recorded, now))
            editor.apply()
        }
        EvalCardLog.scanSessionRecorded(
            scanId, recorded?.sessionId, recorded?.let { EvalCardLog.Flag.of(it.evaluationEnabled) }
        )
        return recorded
    }

    /**
     * The session [scanId] started in, or null when it started in none (or the entry cannot be read — printed). Never
     * consults the holder for the ANSWER; the holder is read only to say, in a line, that it differs.
     */
    @Synchronized
    fun sessionFor(scanId: String, use: Use): Recorded? {
        val holderSessionId = holder.get()?.takeIf { it.isNotBlank() }
        // `all[scanId]`, not `getString`: the real getString throws ClassCastException on a value of another type, and
        // this runs ahead of the engine's try block — a value that is not a string is "unreadable", reported below.
        val raw: Any? = if (scanId.isBlank()) null else prefs.all[scanId]
        if (raw == null) {
            if (holderSessionId != null) EvalCardLog.scanSessionHolderIgnored(use.wire, scanId, holderSessionId)
            return null
        }
        val entry = (raw as? String)?.let(::decode)
        if (entry == null) {
            EvalCardLog.scanSessionUnreadable(use.wire, scanId)
            return null
        }
        if (entry.recorded.sessionId != holderSessionId) {
            EvalCardLog.scanSessionDiffers(use.wire, scanId, entry.recorded.sessionId, holderSessionId)
        }
        return entry.recorded
    }

    /** The scan's analysis succeeded: its session is spent. A scan with no record is a no-op (and prints nothing). */
    @Synchronized
    fun consume(scanId: String) {
        if (scanId.isBlank() || !prefs.contains(scanId)) return
        prefs.edit().remove(scanId).apply()
        EvalCardLog.scanSessionConsumed(scanId)
    }

    private class Entry(val recorded: Recorded, val recordedAtMs: Long)

    private fun encode(recorded: Recorded, atMs: Long): String =
        "${EvalCardLog.Flag.of(recorded.evaluationEnabled).wire}$SEPARATOR$atMs$SEPARATOR${recorded.sessionId}"

    /** Strict: three parts, a flag word (`true`, `false` or `absent`), a number, a non-blank id (the id may itself contain the separator). */
    private fun decode(raw: String): Entry? {
        val parts = raw.split(SEPARATOR, limit = 3)
        if (parts.size != 3) return null
        val evaluationEnabled: Boolean? = when (parts[0]) {
            EvalCardLog.Flag.TRUE.wire -> true
            EvalCardLog.Flag.FALSE.wire -> false
            EvalCardLog.Flag.ABSENT.wire -> null
            else -> return null
        }
        val at = parts[1].toLongOrNull() ?: return null
        if (parts[2].isBlank()) return null
        return Entry(Recorded(parts[2], evaluationEnabled), at)
    }

    companion object {
        /** The SharedPreferences file. */
        const val PREFS_NAME = "neuralcheck_scan_sessions"

        /** See the class doc: a bound on the file, not a policy. */
        const val RETENTION_MS: Long = 24L * 60 * 60 * 1000

        private const val SEPARATOR = "|"
    }
}
