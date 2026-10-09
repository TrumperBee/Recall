package com.kumbukaapp.recall.workmanager

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.kumbukaapp.recall.R

object WorkManagerPipeline {

    private const val CHANNEL_ID = "ai_analysis_channel"
    private const val NOTIFICATION_ID = 1004

    /** Enqueue the full analysis pipeline for a recording */
    fun enqueuePipeline(
        context: Context,
        recordingId: Int,
        languageHint: String? = null
    ) {
        val workRequest = OneTimeWorkRequestBuilder<AnalysisPipelineWorker>()
            .setInputData(
                AnalysisPipelineWorker.inputData(recordingId, languageHint)
            )
            .setConstraints(analysisConstraints)
            .build()

        WorkManager.getInstance(context).beginUniqueWork(
            "ai_pipeline_$recordingId",
            ExistingWorkPolicy.KEEP,
            workRequest
        ).enqueue()

        showNotification(context, "AI analyzing", "Transcribing and analyzing call recording...")
    }

    /** Manual "Process now" - force reprocess even if already done */
    fun forceReprocess(context: Context, recordingId: Int, languageHint: String? = null) {
        val workRequest = OneTimeWorkRequestBuilder<AnalysisPipelineWorker>()
            .setInputData(
                AnalysisPipelineWorker.inputData(recordingId, languageHint, force = true)
            )
            .setConstraints(analysisConstraints)
            .build()

        WorkManager.getInstance(context).beginUniqueWork(
            "ai_pipeline_$recordingId",
            ExistingWorkPolicy.REPLACE,
            workRequest
        ).enqueue()

        showNotification(context, "AI analyzing", "Reprocessing call recording...")
    }

    /** Show a quiet notification (no-op if POST_NOTIFICATIONS is not granted) */
    private fun showNotification(
        context: Context,
        title: String,
        text: String
    ) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "AI Analysis",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "AI transcription and analysis status"
                enableLights(false)
                enableVibration(false)
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_record)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }
}
