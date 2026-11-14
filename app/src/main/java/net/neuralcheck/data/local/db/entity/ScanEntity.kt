package net.neuralcheck.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scans")
data class ScanEntity(
    @PrimaryKey val scanId: String,           // UUID
    val deviceFingerprint: String,            // SHA-256 hash
    val scanType: String,                     // "general", "pre_repair", "post_repair"
    val linkedScanId: String?,                // UUID of linked scan (before/after)
    val timestamp: Long,                      // Unix millis
    val batteryLevelStart: Int,
    val batteryLevelEnd: Int?,
    val ambientTempEstimate: Float?,
    val appVersion: String,
    val androidApiLevel: Int,
    val deviceModel: String,
    val androidVersion: String,
    val isComplete: Boolean,                  // false if safety-stopped
    val earlyStopReason: String?,             // null, "battery_temp_critical", etc.
    val earlyStopAtMs: Long?,
    val timeSeriesFilePath: String?,          // path to .ts.json.gz file
    val isSynced: Boolean = false,            // for cloud sync tracking
    /**
     * Firebase Auth uid of the user who started this scan. Null when the scan
     * was created in a signed-out state (legacy data, anonymous customer-
     * device scans via session code, or on-device install before sign-in).
     * Home / History queries filter by this column so signing in with a
     * different account on the same device never surfaces another user's
     * scans. Server-side mirror lives in `reports/{scanId}.userId` which the
     * Firestore rules also key on; the two MUST stay in sync.
     */
    val userId: String? = null
)
