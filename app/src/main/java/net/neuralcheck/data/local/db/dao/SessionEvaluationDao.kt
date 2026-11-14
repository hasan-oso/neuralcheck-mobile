package net.neuralcheck.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import net.neuralcheck.data.local.db.entity.SessionEvaluationEntity

@Dao
interface SessionEvaluationDao {

    /** Whole-row upsert — every technician tap rewrites the row. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(evaluation: SessionEvaluationEntity)

    @Query("SELECT * FROM session_evaluations WHERE scanId = :scanId")
    suspend fun getByScanId(scanId: String): SessionEvaluationEntity?

    @Query("UPDATE session_evaluations SET submitted = 1 WHERE scanId = :scanId")
    suspend fun markSubmitted(scanId: String)

    /**
     * Order 2026-09-30 («أمر — التقييم (ب)...»، البند ١): terminal
     * rejection — see SessionEvaluationEntity.finalFailure's doc. Never
     * cleared once set.
     */
    @Query("UPDATE session_evaluations SET finalFailure = 1 WHERE scanId = :scanId")
    suspend fun markFinalFailure(scanId: String)

    /**
     * Order 2026-09-30 («أمر — التقييم (ب)...»، البند ١): drops a
     * cancelled scan's evaluation entirely — called from
     * ActiveScanViewModel.stopScan(). A no-op (0 rows affected) when no
     * row exists yet, e.g. the technician cancelled before the first tap.
     */
    @Query("DELETE FROM session_evaluations WHERE scanId = :scanId")
    suspend fun deleteByScanId(scanId: String)

    /** Unsent, not-yet-final rows, oldest first — consumed by the sync
     *  retry path. `finalFailure = 0` excludes a confirmed terminal
     *  rejection (order 2026-09-30، البند ١) — the sync path must never
     *  re-attempt those; only transient failures (network, etc.) stay
     *  eligible here. */
    @Query("SELECT * FROM session_evaluations WHERE submitted = 0 AND finalFailure = 0 ORDER BY shownAtMs ASC")
    suspend fun getPending(): List<SessionEvaluationEntity>
}
