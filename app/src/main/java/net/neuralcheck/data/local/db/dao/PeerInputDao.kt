package net.neuralcheck.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import net.neuralcheck.data.local.db.entity.PeerInputEntity

@Dao
interface PeerInputDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(input: PeerInputEntity)

    @Query("SELECT * FROM peer_inputs WHERE scanId = :scanId")
    suspend fun getByScanId(scanId: String): PeerInputEntity?
}
