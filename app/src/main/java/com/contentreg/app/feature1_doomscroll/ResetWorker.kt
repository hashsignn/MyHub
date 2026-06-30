package com.contentreg.app.feature1_doomscroll

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.contentreg.app.core.data.di.ServiceLocator
import com.contentreg.app.feature1_doomscroll.budget.HourWindowResetter
import java.util.concurrent.TimeUnit

/**
 * M1.4 — WorkManager backup for the hourly reset. The live service already resets via timestamps;
 * this exists so the persisted state still freshens up if the service was killed and the user
 * hasn't reopened a feed in a while. Cheap and idempotent.
 */
class ResetWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        HourWindowResetter.resetIfStale(ServiceLocator.budgetRepository)
        return Result.success()
    }

    companion object {
        private const val UNIQUE_NAME = "budget_hourly_reset"

        /** Schedules the periodic backup reset (no-op if already scheduled). */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ResetWorker>(1, TimeUnit.HOURS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
