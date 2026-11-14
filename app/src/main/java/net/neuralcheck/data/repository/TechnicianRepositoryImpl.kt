package net.neuralcheck.data.repository

import net.neuralcheck.data.local.db.dao.TechnicianInputDao
import net.neuralcheck.data.local.db.entity.TechnicianInputEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TechnicianRepositoryImpl @Inject constructor(
    private val dao: TechnicianInputDao
) : TechnicianRepository {

    override suspend fun save(input: TechnicianInputEntity) {
        dao.insert(input)
    }

    override suspend fun getByScanId(scanId: String): TechnicianInputEntity? {
        return dao.getByScanId(scanId)
    }
}
