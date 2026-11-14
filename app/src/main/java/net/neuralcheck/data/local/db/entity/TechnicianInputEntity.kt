package net.neuralcheck.data.local.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "technician_inputs",
    foreignKeys = [ForeignKey(
        entity = ScanEntity::class,
        parentColumns = ["scanId"],
        childColumns = ["scanId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class TechnicianInputEntity(
    @PrimaryKey val scanId: String,
    val isPreRepair: Boolean,              // true = pre, false = post
    val customerComplaint: String?,         // Free text (pre-repair)
    val faultCategories: String,           // JSON array of selected categories
    val technicianConfidence: Int?,         // 1-5 (pre-repair only)
    val repairActions: String?,            // JSON array (post-repair)
    val repairResult: String?,             // "fully_resolved", "partially", "not_resolved"
    val additionalNotes: String?,
    val timestamp: Long
)
