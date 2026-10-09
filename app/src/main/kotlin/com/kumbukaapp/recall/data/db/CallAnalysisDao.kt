package com.kumbukaapp.recall.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kumbukaapp.recall.data.model.CallAnalysis
import com.kumbukaapp.recall.data.model.Recording

@Dao
interface CallAnalysisDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(analysis: CallAnalysis)

    @Query("SELECT * FROM call_analyses WHERE call_id = :callId")
    suspend fun getByCallId(callId: Int): CallAnalysis?

    @Query("DELETE FROM call_analyses WHERE call_id = :callId")
    suspend fun deleteByCallId(callId: Int)

    @Query("DELETE FROM call_analyses")
    suspend fun deleteAll()

    @Query("SELECT * FROM call_analyses WHERE status = :status")
    suspend fun getByStatus(status: String): List<CallAnalysis>

    /** Calls whose analysis summary matches the filter (used by search). */
    @Query(
        "SELECT c.* FROM calls c INNER JOIN call_analyses a ON a.call_id = c.id " +
            "WHERE a.summary LIKE '%' || :filter || '%' ORDER BY c.startTime DESC"
    )
    suspend fun searchBySummary(filter: String): List<Recording>
}
