package net.neuralcheck.data.repository

import net.neuralcheck.data.local.db.entity.TechnicianInputEntity

interface TechnicianRepository {
    suspend fun save(input: TechnicianInputEntity)
    suspend fun getByScanId(scanId: String): TechnicianInputEntity?
}
