package net.neuralcheck.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import net.neuralcheck.data.local.db.entity.ScanScoreEntity

@Dao
interface ScanScoreDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(score: ScanScoreEntity)

    @Query("SELECT * FROM scan_scores WHERE scanId = :scanId")
    suspend fun getByScanId(scanId: String): ScanScoreEntity?
}
