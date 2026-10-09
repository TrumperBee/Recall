package com.kumbukaapp.recall.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.kumbukaapp.recall.service.CallSyncWorker
import com.kumbukaapp.recall.service.StartupSyncWorker
import java.util.concurrent.TimeUnit

/**
 * Re-schedules call-log sync after the device boots. Call-log sync is non-idempotent
 * (deduped against stored calls), so re-running after boot is safe.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }

        val workManager = WorkManager.getInstance(context)

        // One-time sync shortly after boot
        workManager.enqueueUniqueWork(
            "boot_sync",
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<StartupSyncWorker>().build()
        )

        // Keep the periodic sync scheduled
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val periodic = PeriodicWorkRequestBuilder<CallSyncWorker>(
            6,
            TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .build()
        workManager.enqueueUniquePeriodicWork(
            "periodic_call_sync",
            ExistingPeriodicWorkPolicy.UPDATE,
            periodic
        )
    }
}