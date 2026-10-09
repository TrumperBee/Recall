package com.kumbukaapp.recall.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.kumbukaapp.recall.data.model.Recording
import com.kumbukaapp.recall.data.model.Transcript
import com.kumbukaapp.recall.data.model.CallAnalysis

@Database(entities = [Recording::class, Transcript::class, CallAnalysis::class], version = 4, exportSchema = true)
abstract class CallDatabase : RoomDatabase() {
    abstract fun recordingDao(): RecordingDao
    abstract fun transcriptDao(): TranscriptDao
    abstract fun callAnalysisDao(): CallAnalysisDao
}