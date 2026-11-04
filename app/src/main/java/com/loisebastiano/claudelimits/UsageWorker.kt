package com.loisebastiano.claudelimits

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class UsageWorker(c: Context, p: WorkerParameters) : CoroutineWorker(c, p) {
    override suspend fun doWork(): Result {
        UsageRepository.refresh(applicationContext)
        return Result.success()
    }

    companion object {
        private val net = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        fun schedule(c: Context, replace: Boolean) {
            val req = PeriodicWorkRequestBuilder<UsageWorker>(Prefs.interval(c).toLong(), TimeUnit.MINUTES)
                .setConstraints(net).build()
            WorkManager.getInstance(c).enqueueUniquePeriodicWork("periodic",
                if (replace) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP, req)
        }

        fun refreshNow(c: Context) {
            val req = OneTimeWorkRequestBuilder<UsageWorker>().setConstraints(net).build()
            WorkManager.getInstance(c).enqueueUniqueWork("now", ExistingWorkPolicy.KEEP, req)
        }

        fun cancel(c: Context) {
            WorkManager.getInstance(c).cancelUniqueWork("periodic")
        }
    }
}
