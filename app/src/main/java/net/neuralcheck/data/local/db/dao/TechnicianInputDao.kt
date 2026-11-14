package net.neuralcheck.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import net.neuralcheck.data.local.db.entity.TechnicianInputEntity

@Dao
interface TechnicianInputDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(input: TechnicianInputEntity)

    @Query("SELECT * FROM technician_inputs WHERE scanId = :scanId")
    suspend fun getByScanId(scanId: String): TechnicianInputEntity?
}
