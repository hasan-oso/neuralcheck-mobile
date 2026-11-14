package net.neuralcheck.data.repository

import net.neuralcheck.data.local.db.entity.PeerInputEntity

interface PeerRepository {
    suspend fun save(input: PeerInputEntity)
    suspend fun getByScanId(scanId: String): PeerInputEntity?
}
