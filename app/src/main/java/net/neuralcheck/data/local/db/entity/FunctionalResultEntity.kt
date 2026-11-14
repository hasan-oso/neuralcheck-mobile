package net.neuralcheck.data.local.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "functional_results",
    foreignKeys = [ForeignKey(
        entity = ScanEntity::class,
        parentColumns = ["scanId"],
        childColumns = ["scanId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("scanId")]
)
data class FunctionalResultEntity(
    @PrimaryKey(autoGenerate = true) val resultId: Long = 0,
    val scanId: String,
    val testId: String,              // "B-01", "C-06", "SN-03", etc.
    val componentName: String,       // Arabic display name
    val categoryName: String,        // "battery", "cpu", "gpu", etc.
    val status: String,              // "pass", "fail", "warning", "unavailable", "not_tested"
    val score: Float?,               // 0-100 individual score (null if not scored)
    val rawValue: String?,           // Raw measurement value
    val rawValueUnit: String?,       // Unit of measurement
    val notes: String?,
    val timestamp: Long,
    /**
     * fieldsVersion 68 (S-29 (a), N-16, order 2026-10-08): the compound raw
     * measurements of this test (L-02 satellite numbers, touch-grid raw,
     * light-sensor reading lists, ...) as ONE JSON object text. A column of
     * its own, NOT [rawValue]: `rawValue` is display text that other code
     * parses (BehaviorFingerprintBuilder S-02), so mixing machine-readable
     * raw into it would corrupt both. NULL = the producer of this test
     * supplied no compound raw (every row written before schema 8, and every
     * test that has none) — it is NOT "measured zero". Decoded by
     * [net.neuralcheck.data.local.db.converter.MeasurementsJson]; added by
     * NeuralCheckDatabase.MIGRATION_7_8.
     */
    val measurements: String? = null
)
