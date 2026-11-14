package net.neuralcheck.data.repository

import kotlinx.coroutines.flow.Flow
import net.neuralcheck.data.local.db.dao.ScanDao
import net.neuralcheck.data.local.db.entity.ScanEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScanRepositoryImpl @Inject constructor(
    private val scanDao: ScanDao
) : ScanRepository {

    override suspend fun createScan(scan: ScanEntity): String {
        scanDao.insertScan(scan)
        return scan.scanId
    }

    override suspend fun updateScan(scan: ScanEntity) {
        scanDao.updateScan(scan)
    }

    override suspend fun getScanById(id: String): ScanEntity? {
        return scanDao.getScanById(id)
    }

    override fun getAllScans(): Flow<List<ScanEntity>> {
        return scanDao.getAllScans()
    }

    override fun getAllScansByUserId(userId: String): Flow<List<ScanEntity>> {
        return scanDao.getAllScansByUserId(userId)
    }

    override fun getScansByType(type: String): Flow<List<ScanEntity>> {
        return scanDao.getScansByType(type)
    }

    override fun getScansByTypeForUser(userId: String, type: String): Flow<List<ScanEntity>> {
        return scanDao.getScansByTypeForUser(userId, type)
    }

    override suspend fun getLinkedScan(linkedId: String): ScanEntity? {
        return scanDao.getLinkedScan(linkedId)
    }

    override suspend fun deleteScan(id: String) {
        scanDao.deleteScan(id)
    }

    override fun searchScans(query: String): Flow<List<ScanEntity>> {
        return scanDao.searchScans(query)
    }

    override suspend fun getUnsyncedScans(): List<ScanEntity> {
        return scanDao.getUnsyncedScans()
    }

    override suspend fun markSynced(scanId: String) {
        scanDao.markAsSynced(scanId)
    }

    override suspend fun markUnsynced(scanId: String) {
        scanDao.markAsUnsynced(scanId)
    }

    override suspend fun getScanCount(): Int {
        return scanDao.getScanCount()
    }
}
