package net.neuralcheck.data.local.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "peer_inputs",
    foreignKeys = [ForeignKey(
        entity = ScanEntity::class,
        parentColumns = ["scanId"],
        childColumns = ["scanId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class PeerInputEntity(
    @PrimaryKey val scanId: String,
    val batteryDrainsFast: Boolean?,     // null = skipped
    val overheats: Boolean?,
    val sluggish: Boolean?,
    val timestamp: Long
)
