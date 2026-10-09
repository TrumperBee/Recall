package com.kumbukaapp.recall.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kumbukaapp.recall.R
import com.kumbukaapp.recall.data.repository.RecordingRepository

class CallSyncWorker(
    context: Context,
    parameters: WorkerParameters
) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        return try {
            val context = applicationContext
            syncCallHistory(context)
            Result.success()
        } catch (e: Exception) {
            Log.e("CallSyncWorker", "Sync failed", e)
            Result.retry()
        }
    }

    private suspend fun syncCallHistory(context: Context) {
        val repo = RecordingRepository(CallDatabaseFactory.getInstance(context))
        val syncer = CallLogSyncer(context, repo)
        val lastSync = com.kumbukaapp.recall.util.Persistence.loadLastSync(context)
        syncer.syncSince(lastSync)
    }
}
