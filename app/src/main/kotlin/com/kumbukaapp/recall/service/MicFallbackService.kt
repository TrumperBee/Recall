package com.kumbukaapp.recall.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.kumbukaapp.recall.R
import timber.log.Timber

const val MIC_FALLBACK_CHANNEL_ID = "mic_fallback_service"
const val MIC_FALLBACK_NOTIFICATION_ID = 1003

/** Starts the mic fallback foreground service */
fun startMicFallbackService(context: Context) {
    val intent = Intent(context, MicFallbackService::class.java).apply { action = "start" }
    context.startForegroundService(intent)
}

/** Stops the mic fallback foreground service */
fun stopMicFallbackService(context: Context) {
    val intent = Intent(context, MicFallbackService::class.java)
    context.stopService(intent)
}

/** The mic fallback foreground service */
class MicFallbackService : Service() {

    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false
    private var recordingPath: String? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action ?: "start") {
            "start" -> {
                // Must call startForeground() promptly after startForegroundService()
                startForeground(MIC_FALLBACK_NOTIFICATION_ID, buildRecordingNotification())
                startRecording()
            }
            "stop" -> {
                stopRecording()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }

        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                MIC_FALLBACK_CHANNEL_ID,
                "Mic Fallback Recording",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background microphone recording during calls"
                enableLights(false)
                enableVibration(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun startRecording() {
        if (isRecording) return

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Timber.w("RECORD_AUDIO not granted; stopping mic fallback")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        val path = "${getExternalFilesDir(null)?.absolutePath}/recording_${System.currentTimeMillis()}.m4a"
        recordingPath = path

        mediaRecorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setOutputFile(path)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioChannels(1)
            setAudioSamplingRate(44100)
            setAudioEncodingBitRate(128000)

            try {
                prepare()
                start()
                isRecording = true
                Timber.d("Mic fallback recording started to $path")
            } catch (e: Exception) {
                Timber.e(e, "Failed to start mic fallback recording")
                releaseRecorder()
            }
        }
    }

    private fun stopRecording() {
        if (!isRecording) return
        isRecording = false

        try {
            mediaRecorder?.stop()
        } catch (e: Exception) {
            // stop() throws if no valid recording was captured - clean up the partial file
            recordingPath?.let { java.io.File(it).delete() }
        }
        releaseRecorder()

        Timber.d("Mic fallback recording stopped")
    }

    private fun releaseRecorder() {
        try {
            mediaRecorder?.reset()
            mediaRecorder?.release()
        } catch (_: Exception) {
        }
        mediaRecorder = null
    }

    private fun buildRecordingNotification(): Notification {
        return NotificationCompat.Builder(this, MIC_FALLBACK_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_menu_recording)
            .setContentTitle("Recall")
            .setContentText("Recording call audio via microphone")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRecording()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
