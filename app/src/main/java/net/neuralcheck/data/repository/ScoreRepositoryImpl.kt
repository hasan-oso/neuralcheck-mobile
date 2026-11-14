package net.neuralcheck.data.repository

import net.neuralcheck.data.local.db.dao.ScanScoreDao
import net.neuralcheck.data.local.db.entity.ScanScoreEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScoreRepositoryImpl @Inject constructor(
    private val dao: ScanScoreDao
) : ScoreRepository {

    override suspend fun save(score: ScanScoreEntity) {
        dao.insert(score)
    }

    override suspend fun getByScanId(scanId: String): ScanScoreEntity? {
        return dao.getByScanId(scanId)
    }
}
