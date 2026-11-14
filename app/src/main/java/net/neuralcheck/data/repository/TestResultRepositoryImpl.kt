package net.neuralcheck.data.repository

import net.neuralcheck.data.local.db.dao.FunctionalResultDao
import net.neuralcheck.data.local.db.entity.FunctionalResultEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TestResultRepositoryImpl @Inject constructor(
    private val dao: FunctionalResultDao
) : TestResultRepository {

    override suspend fun saveResults(results: List<FunctionalResultEntity>) {
        dao.insertResults(results)
    }

    override suspend fun upsertResults(results: List<FunctionalResultEntity>) {
        // Delete-then-insert instead of a REPLACE insert because the primary
        // key auto-generates and onConflict only fires on primary-key collisions.
        for (r in results) {
            dao.deleteByScanIdAndTestId(r.scanId, r.testId)
        }
        dao.insertResults(results)
    }

    override suspend fun getResults(scanId: String): List<FunctionalResultEntity> {
        return dao.getResultsByScanId(scanId)
    }

    override suspend fun getResultsByCategory(scanId: String, category: String): List<FunctionalResultEntity> {
        return dao.getResultsByCategory(scanId, category)
    }
}
