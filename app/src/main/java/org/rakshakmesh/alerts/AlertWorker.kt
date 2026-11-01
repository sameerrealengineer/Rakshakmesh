package org.rakshakmesh.alerts

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
import org.rakshakmesh.RakshakApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.rakshakmesh.core.AlertFeedParser
import org.rakshakmesh.core.RefreshPolicy
import java.util.concurrent.TimeUnit

/** Refreshes the district alert cache. Runs only with network, never in cyclone mode. */
class AlertWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as RakshakApp
        val settings = app.settings.get()
        if (settings.cycloneMode) return Result.success()
        val district = app.db.profile().get()?.district?.takeIf { it.isNotBlank() } ?: return Result.success()
        val result = app.alertRepo.refresh(district)
        // Re-plan the interval: 30 min while orange/red is active, otherwise 3 h (FR-ALR-02).
        AlertScheduler.schedule(applicationContext, currentIntervalMinutes(app, district))
        return if (result.isSuccess) Result.success() else Result.retry()
    }

    private suspend fun currentIntervalMinutes(app: RakshakApp, district: String): Long {
        val levels = app.db.alerts().forDistrict(district).map { it.level to it.validUntil }
        return RefreshPolicy.intervalMinutes(AlertFeedParser.highestActiveOf(levels, System.currentTimeMillis()))
    }
}

object AlertScheduler {
    private const val PERIODIC = "alerts-periodic"
    private const val NOW = "alerts-now"
    private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    suspend fun schedule(context: Context, intervalMinutes: Long = 180) = withContext(Dispatchers.IO) {
        val req = PeriodicWorkRequestBuilder<AlertWorker>(intervalMinutes, TimeUnit.MINUTES)
            .setConstraints(online)
            .addTag("interval-$intervalMinutes")
            .build()
        val wm = WorkManager.getInstance(context)
        val existing = runCatching { wm.getWorkInfosForUniqueWork(PERIODIC).get() }.getOrNull().orEmpty()
        val sameInterval = existing.any { !it.state.isFinished && "interval-$intervalMinutes" in it.tags }
        wm.enqueueUniquePeriodicWork(
            PERIODIC,
            if (sameInterval) ExistingPeriodicWorkPolicy.KEEP else ExistingPeriodicWorkPolicy.UPDATE,
            req,
        )
    }

    fun refreshNow(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            NOW, ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<AlertWorker>().setConstraints(online).build(),
        )
    }

    /** Cyclone mode: no background work at all (FR-BAT-01). */
    fun cancelAll(context: Context) {
        WorkManager.getInstance(context).cancelAllWork()
    }
}
