package net.neuralcheck.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * feat/technician-evaluation — one row per QR-session scan holding the
 * technician's blind judgement, captured on the SCANNED device while the
 * scan is still running (i.e. structurally before our verdict exists).
 *
 * Written incrementally: every tap upserts the whole row on Dispatchers.IO.
 * This is a structural necessity, not an optimisation — on completion
 * ActiveScanScreen auto-navigates to manual_tests and pops active_scan off
 * the back stack (ActiveScanScreen.kt Completed branch), so any answer not
 * already persisted at that instant is lost.
 *
 * [sessionId] is snapshotted from ScanSessionHolder when the questions
 * card first appears and persisted with the first answer. NEVER read the
 * holder at submission time: AiScoringEngine clears it after the first
 * analyzeDevice (success or failure), and sign-out clears it too.
 *
 * No free-text columns by design (technician types nothing) and no
 * technicianName / ownerId — attribution is server-side via sessionId.
 */
@Entity(tableName = "session_evaluations")
data class SessionEvaluationEntity(
    @PrimaryKey val scanId: String,
    /** Session snapshot taken at card init; the server resolves ownerId
     *  from scanSessions/{sessionId} exactly like analyzeDevice billing. */
    val sessionId: String,
    /** "excellent" | "good" | "fair" | "weak" — stored verbatim (raw
     *  verbal scale); A·B·C·D mapping is the server's job. Null = the
     *  technician never answered Q1. */
    val technicianGradeRaw: String? = null,
    /** JSON object of answered depth categories only, e.g.
     *  {"battery":"good","storage":"not_tested"}. An ABSENT key means
     *  "did not answer"; the VALUE "not_tested" means the technician
     *  explicitly answered "لم أفحصه" — two different attestations. */
    val categoryRatingsJson: String = "{}",
    /** JSON array of selected visible defects, e.g.
     *  ["weak_battery","prior_repair"] or ["none_noticed"]. Empty array
     *  = question never answered. */
    val visibleDefectsJson: String = "[]",
    /** Wall-clock ms when the questions card was first shown — start of
     *  the durationMs window ("من ظهور أول سؤال"). */
    val shownAtMs: Long,
    /** Wall-clock ms of the most recent answer tap — end of the
     *  durationMs window. Null until the first answer (row is only ever
     *  written on an answer, so effectively never null on disk). */
    val lastAnswerAtMs: Long? = null,
    /** True once submitEvaluation succeeded (a `pending: true` response
     *  IS success — the server finishes gradeDistance via a trigger).
     *  False rows are retried by the sync path. */
    val submitted: Boolean = false,
    /**
     * Order 2026-09-30 («أمر — التقييم (ب)، ومهلة الجلسة، والبنود المعلّقة»،
     * البند ١): true once submitEvaluation has rejected this scanId with a
     * CONFIRMED terminal cause — the session is invalid or expired
     * (submitEvaluation.js:73-74 / :76-80, see
     * [net.neuralcheck.presentation.viewmodel.isFinalEvaluationRejection]
     * for the exact code+text classification). A row can never become
     * un-rejected, so this is never cleared. Distinct from [submitted]:
     * a permanently-failed row is NOT "sent successfully", but must ALSO
     * never be retried — SessionEvaluationDao.getPending() excludes rows
     * where EITHER flag is true.
     *
     * New column, Room schema v6→v7 — see
     * NeuralCheckDatabase.MIGRATION_6_7 (plain `ALTER TABLE ... ADD COLUMN
     * ... DEFAULT 0`, same additive pattern as MIGRATION_1_2/2_3/3_4 — no
     * existing row's data changes, every pre-migration row reads back
     * `finalFailure = false`).
     */
    val finalFailure: Boolean = false
)
