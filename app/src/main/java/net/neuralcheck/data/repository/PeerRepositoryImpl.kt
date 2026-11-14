package net.neuralcheck.data.repository

import net.neuralcheck.data.local.db.dao.PeerInputDao
import net.neuralcheck.data.local.db.entity.PeerInputEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PeerRepositoryImpl @Inject constructor(
    private val dao: PeerInputDao
) : PeerRepository {

    override suspend fun save(input: PeerInputEntity) {
        dao.insert(input)
    }

    override suspend fun getByScanId(scanId: String): PeerInputEntity? {
        return dao.getByScanId(scanId)
    }
}
