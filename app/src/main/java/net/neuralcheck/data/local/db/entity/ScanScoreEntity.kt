package net.neuralcheck.data.local.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "scan_scores",
    foreignKeys = [ForeignKey(
        entity = ScanEntity::class,
        parentColumns = ["scanId"],
        childColumns = ["scanId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class ScanScoreEntity(
    @PrimaryKey val scanId: String,
    // Order 2026-09-24 (القرار ٧٦/٧٧): was `Float` NOT NULL — a V7 report
    // (no `response.score`) coerced to 0f, a fabricated score persisted to
    // Room and synced to Firestore. Nullable now; see MIGRATION_5_6 in
    // NeuralCheckDatabase.kt (SQLite has no ALTER COLUMN, so this is the
    // "recreate table" pattern) and ScoringResult.overallScore's doc for
    // the full read-site inventory.
    val overallScore: Float?,             // 0.0-100.0, null = no score to show
    val overallGrade: String,             // "A", "B", "C", "D"
    val axisScoresJson: String,           // JSON of 4 AxisScore objects
    val categoryScoresJson: String,       // JSON of 13 CategoryScore objects
    val criticalFailuresJson: String,     // JSON array of test IDs
    val antiTamperPenalty: Float,
    val isIncomplete: Boolean,
    val scoringMethod: String,            // "rule_based_v1"
    val calculatedAt: Long,
    // Backend-minted human-readable scan ID (e.g. "NC-A7K9M2"). Persisted
    // here so re-opening the report from history shows the same ID printed
    // on the QR / PDF — even if the AI re-run on re-open fails (offline,
    // backend outage) or returns a different value. The first successful
    // AI call mints this and we treat it as canonical thereafter.
    val shortId: String? = null,
    /**
     * Full [net.neuralcheck.domain.model.AiAnalysisResult] serialised as
     * JSON. Persisted on the FIRST successful AI run so re-opening the
     * report from history can rehydrate every section (summary, details,
     * verdict, issues, root_cause, buyer_advice, category_scores, predicted_
     * problems, category_notes) without re-calling Gemini and without
     * fetching from Firestore. The back-office can also no longer fingerprint
     * a re-open as a fresh AI call.
     *
     * Null when scoring has not yet completed for this scanId — the loader
     * falls back to the live scoring flow in that case.
     *
     * Phase 3-a: this column now holds TWO possible JSON shapes, both
     * deserialised through the SAME plain `Gson().fromJson(json,
     * AiAnalysisResult::class.java)` call in ReportViewModel.loadScan — no
     * schema migration on this column, no version tag needed, because
     * every field added for the V7 shape (`pipeline`/`overall`/
     * `categories`/`narrative`/...) is a NEW, distinct JSON key on the
     * SAME class, not a reused key with a changed shape (the one case
     * that would need a custom TypeAdapter — see PredictedProblem.kt for
     * what that looks like when it IS needed). A row written before this
     * phase simply deserialises with those new fields null; a row written
     * after carries them. [net.neuralcheck.domain.model.isComplete] reads
     * `pipeline` to decide which set of fields to trust.
     */
    val aiAnalysisJson: String? = null
)
