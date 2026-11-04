package com.loisebastiano.claudelimits

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OutOfQuotaPolicy
import androidx.work.workDataOf
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class UsageWorker(c: Context, p: WorkerParameters) : CoroutineWorker(c, p) {
    override suspend fun doWork(): Result {
        val source = runCatching { UsageRepository.Source.valueOf(inputData.getString(KEY_SOURCE) ?: "") }
            .getOrDefault(UsageRepository.Source.BACKGROUND)
        UsageRepository.refresh(applicationContext, source)
        return Result.success()
    }

    /** Only used on Android 8–11, where expedited work runs as a short foreground service. */
    override suspend fun getForegroundInfo(): ForegroundInfo {
        val nm = applicationContext.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, applicationContext.getString(R.string.notif_channel),
            NotificationManager.IMPORTANCE_MIN))
        val n = Notification.Builder(applicationContext, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(applicationContext.getString(R.string.w_loading))
            .build()
        return ForegroundInfo(NOTIF_ID, n)
    }

    companion object {
        private const val KEY_SOURCE = "source"
        private const val CHANNEL = "refresh"
        private const val NOTIF_ID = 1
        private val net = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        /**
         * Refresh as soon as possible. Runs as an *expedited* job: Android starts it immediately and
         * grants it network access, which a background broadcast receiver may not have
         * (observed: "TypeError: Failed to fetch" when fetching from the widget receiver).
         */
        fun refreshNow(c: Context, source: UsageRepository.Source = UsageRepository.Source.BACKGROUND) {
            val req = OneTimeWorkRequestBuilder<UsageWorker>()
                .setConstraints(net)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setInputData(workDataOf(KEY_SOURCE to source.name))
                .build()
            WorkManager.getInstance(c).enqueueUniqueWork("refresh", ExistingWorkPolicy.REPLACE, req)
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
