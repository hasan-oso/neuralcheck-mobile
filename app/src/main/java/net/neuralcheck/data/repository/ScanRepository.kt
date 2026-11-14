package net.neuralcheck.data.repository

import kotlinx.coroutines.flow.Flow
import net.neuralcheck.data.local.db.entity.ScanEntity

interface ScanRepository {
    suspend fun createScan(scan: ScanEntity): String
    suspend fun updateScan(scan: ScanEntity)
    suspend fun getScanById(id: String): ScanEntity?
    fun getAllScans(): Flow<List<ScanEntity>>
    /**
     * Per-user scan list. Use this from screens that render the signed-in
     * user's own scans (Home, History) so an account switch on the same
     * device never leaks the previous user's data through the local cache.
     * Legacy rows where userId is NULL are excluded.
     */
    fun getAllScansByUserId(userId: String): Flow<List<ScanEntity>>
    fun getScansByType(type: String): Flow<List<ScanEntity>>
    fun getScansByTypeForUser(userId: String, type: String): Flow<List<ScanEntity>>
    suspend fun getLinkedScan(linkedId: String): ScanEntity?
    suspend fun deleteScan(id: String)
    fun searchScans(query: String): Flow<List<ScanEntity>>
    suspend fun getUnsyncedScans(): List<ScanEntity>
    suspend fun markSynced(scanId: String)
    suspend fun markUnsynced(scanId: String)
    suspend fun getScanCount(): Int
}
