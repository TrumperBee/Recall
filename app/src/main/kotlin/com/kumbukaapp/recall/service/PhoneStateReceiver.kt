package com.kumbukaapp.recall.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class PhoneStateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)

        when (state) {
            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                // Call active - schedule sync after short delay for call log to appear
                scheduleCallSync(context, 5000L)
            }
            TelephonyManager.EXTRA_STATE_IDLE,
            TelephonyManager.EXTRA_STATE_RINGING -> {
                // Nothing to do here; sync is scheduled when the call goes active
            }
        }
    }

    /** Schedule a one-time WorkManager job to sync call log after a delay. */
    private fun scheduleCallSync(context: Context, delayMillis: Long) {
        val request = OneTimeWorkRequestBuilder<CallSyncWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork("call_sync_after_call", ExistingWorkPolicy.REPLACE, request)
    }
}
