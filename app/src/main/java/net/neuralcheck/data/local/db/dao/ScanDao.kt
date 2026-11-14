package net.neuralcheck.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import net.neuralcheck.data.local.db.entity.ScanEntity

@Dao
interface ScanDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScan(scan: ScanEntity)

    @Update
    suspend fun updateScan(scan: ScanEntity)

    @Query("SELECT * FROM scans WHERE scanId = :scanId")
    suspend fun getScanById(scanId: String): ScanEntity?

    @Query("SELECT * FROM scans ORDER BY timestamp DESC")
    fun getAllScans(): Flow<List<ScanEntity>>

    /**
     * Per-user scan list — used by Home / History on signed-in flows so an
     * account switch on the same device never surfaces another user's
     * scans. Includes rows where userId IS NULL because:
     *   1. Pre-v3 rows (created before the userId column was added) all
     *      backfilled to NULL during MIGRATION_2_3. Excluding them would
     *      hide the user's entire pre-update history.
     *   2. Customer-device session scans (joined via session code) carry the
     *      device's anonymous uid when the anonymous sign-in that now runs
     *      BEFORE joinScanSession succeeded (142975e), and userId=NULL when
     *      it failed (the join then proceeds without an identity). The owner
     *      sees the report on Firestore through the session; the customer
     *      device still sees its own rows locally (its own uid matches the
     *      first clause below, NULL matches the second).
     * The OR-NULL clause is safe because Room's signOut path calls
     * `clearAllTables()` — a different account on the same device won't
     * inherit anyone's NULL-userId rows.
     */
    @Query("SELECT * FROM scans WHERE userId = :userId OR userId IS NULL ORDER BY timestamp DESC")
    fun getAllScansByUserId(userId: String): Flow<List<ScanEntity>>

    @Query("SELECT * FROM scans WHERE userId = :userId OR userId IS NULL")
    suspend fun getScanCountByUserId(userId: String): List<ScanEntity>

    @Query("SELECT * FROM scans WHERE scanType = :scanType ORDER BY timestamp DESC")
    fun getScansByType(scanType: String): Flow<List<ScanEntity>>

    /**
     * Per-user, per-scanType list. Same OR-NULL fallback as
     * [getAllScansByUserId] so the pre-repair picker still surfaces scans
     * the user produced before the schema gained a userId column.
     */
    @Query("SELECT * FROM scans WHERE (userId = :userId OR userId IS NULL) AND scanType = :scanType ORDER BY timestamp DESC")
    fun getScansByTypeForUser(userId: String, scanType: String): Flow<List<ScanEntity>>

    @Query("SELECT * FROM scans WHERE scanId = :linkedScanId")
    suspend fun getLinkedScan(linkedScanId: String): ScanEntity?

    @Query("SELECT * FROM scans WHERE isSynced = 0")
    suspend fun getUnsyncedScans(): List<ScanEntity>

    @Query("UPDATE scans SET isSynced = 1 WHERE scanId = :scanId")
    suspend fun markAsSynced(scanId: String)

    @Query("UPDATE scans SET isSynced = 0 WHERE scanId = :scanId")
    suspend fun markAsUnsynced(scanId: String)

    @Query("DELETE FROM scans WHERE scanId = :scanId")
    suspend fun deleteScan(scanId: String)

    @Query("SELECT COUNT(*) FROM scans")
    suspend fun getScanCount(): Int

    @Query("SELECT * FROM scans WHERE deviceModel LIKE '%' || :query || '%' OR scanType LIKE '%' || :query || '%' ORDER BY timestamp DESC")
    fun searchScans(query: String): Flow<List<ScanEntity>>
}
