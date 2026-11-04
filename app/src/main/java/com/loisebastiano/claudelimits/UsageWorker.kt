package com.loisebastiano.claudelimits

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
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

        fun refreshNow(c: Context) {
            val req = OneTimeWorkRequestBuilder<UsageWorker>().setConstraints(net).build()
            WorkManager.getInstance(c).enqueueUniqueWork("now", ExistingWorkPolicy.KEEP, req)
        }
    }
}

/**
 * Refresh cadence.
 * - WorkManager every 15 min (Android's minimum for periodic work): survives reboots and app kills.
 * - For 1–10 min a non-waking alarm: it fires only while the phone is awake, so it costs
 *   nothing with the screen off, and the widget is fresh when you look at it.
 */
object Scheduler {
    private val net = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun start(c: Context, replace: Boolean) {
        val minutes = Prefs.interval(c).coerceAtLeast(15).toLong()
        val req = PeriodicWorkRequestBuilder<UsageWorker>(minutes, TimeUnit.MINUTES).setConstraints(net).build()
        WorkManager.getInstance(c).enqueueUniquePeriodicWork("periodic",
            if (replace) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP, req)
        scheduleTick(c)
    }

    fun scheduleTick(c: Context) {
        val am = c.getSystemService(AlarmManager::class.java)
        val pi = tickIntent(c)
        val minutes = Prefs.interval(c)
        if (minutes >= 15 || Prefs.mode(c) == null) { am.cancel(pi); return }
        val ms = minutes * 60_000L
        am.setWindow(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + ms, ms / 4, pi)
    }

    fun stop(c: Context) {
        WorkManager.getInstance(c).cancelUniqueWork("periodic")
        c.getSystemService(AlarmManager::class.java).cancel(tickIntent(c))
    }

    private fun tickIntent(c: Context) = PendingIntent.getBroadcast(c, 2,
        Intent(c, UsageWidgetProvider::class.java).setAction(UsageWidgetProvider.ACTION_TICK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
}
