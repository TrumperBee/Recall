package com.kumbukaapp.recall.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kumbukaapp.recall.data.repository.RecordingRepository
import com.kumbukaapp.recall.util.Persistence

class StartupSyncWorker(
    context: Context,
    parameters: WorkerParameters
) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        return try {
            val context = applicationContext
            val repo = RecordingRepository(CallDatabaseFactory.getInstance(context))

            // Sync calls since last sync timestamp
            val syncer = CallLogSyncer(context, repo)
            val lastSync = Persistence.loadLastSync(context)
            syncer.syncSince(lastSync)

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
