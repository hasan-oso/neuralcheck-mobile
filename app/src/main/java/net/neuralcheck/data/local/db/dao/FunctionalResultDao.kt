package net.neuralcheck.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import net.neuralcheck.data.local.db.entity.FunctionalResultEntity

@Dao
interface FunctionalResultDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertResults(results: List<FunctionalResultEntity>)

    @Query("SELECT * FROM functional_results WHERE scanId = :scanId ORDER BY testId")
    suspend fun getResultsByScanId(scanId: String): List<FunctionalResultEntity>

    @Query("SELECT * FROM functional_results WHERE scanId = :scanId AND categoryName = :category ORDER BY testId")
    suspend fun getResultsByCategory(scanId: String, category: String): List<FunctionalResultEntity>

    // FunctionalResultEntity.resultId autogenerates, so a plain REPLACE-conflict
    // insert never finds the prior row for the same (scanId, testId) — each
    // re-tap of a manual chip produced a new row. Callers that update (rather
    // than append) a result should delete first, then insert.
    @Query("DELETE FROM functional_results WHERE scanId = :scanId AND testId = :testId")
    suspend fun deleteByScanIdAndTestId(scanId: String, testId: String)
}
