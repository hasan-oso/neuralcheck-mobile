package net.neuralcheck.data.repository

import net.neuralcheck.data.local.db.entity.ScanScoreEntity

interface ScoreRepository {
    suspend fun save(score: ScanScoreEntity)
    suspend fun getByScanId(scanId: String): ScanScoreEntity?
}
