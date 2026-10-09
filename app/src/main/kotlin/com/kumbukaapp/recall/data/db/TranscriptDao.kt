package com.kumbukaapp.recall.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kumbukaapp.recall.data.model.Recording
import com.kumbukaapp.recall.data.model.Transcript

@Dao
interface TranscriptDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transcript: Transcript)

    @Query("SELECT * FROM transcripts WHERE call_id = :callId")
    suspend fun getByCallId(callId: Int): Transcript?

    @Query("DELETE FROM transcripts WHERE call_id = :callId")
    suspend fun deleteByCallId(callId: Int)

    @Query("DELETE FROM transcripts")
    suspend fun deleteAll()

    @Query("SELECT * FROM transcripts WHERE status = :status")
    suspend fun getByStatus(status: String): List<Transcript>

    /** Calls whose transcript text matches the filter (used by search). */
    @Query(
        "SELECT c.* FROM calls c INNER JOIN transcripts t ON t.call_id = c.id " +
            "WHERE t.text LIKE '%' || :filter || '%' ORDER BY c.startTime DESC"
    )
    suspend fun searchByTranscriptText(filter: String): List<Recording>
}
