package com.kumbukaapp.recall.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kumbukaapp.recall.data.model.Recording

@Dao
interface RecordingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(recording: Recording)

    @Query("SELECT * FROM calls ORDER BY startTime DESC")
    suspend fun getAll(): List<Recording>

    @Query(
        "SELECT * FROM calls WHERE normalized_number LIKE '%' || :filter || '%' " +
            "OR raw_number LIKE '%' || :filter || '%' " +
            "OR contact_name LIKE '%' || :filter || '%' ORDER BY startTime DESC"
    )
    suspend fun search(filter: String): List<Recording>

    @Query("DELETE FROM calls")
    suspend fun clearAll()

    @Query("SELECT * FROM calls WHERE id = :id")
    suspend fun getById(id: Int): Recording?

    @Query("SELECT * FROM calls WHERE direction = :direction ORDER BY startTime DESC")
    suspend fun getByDirection(direction: String): List<Recording>

    @Query("SELECT * FROM calls WHERE direction = :direction AND startTime >= :startTime ORDER BY startTime DESC")
    suspend fun getByDirectionAfter(direction: String, startTime: Long): List<Recording>

    @Query("SELECT * FROM calls WHERE isDeleted = 0 ORDER BY startTime DESC")
    suspend fun getActive(): List<Recording>

    @Query("UPDATE calls SET isDeleted = 1 WHERE id IN (:ids)")
    suspend fun softDelete(ids: List<Int>)
}
