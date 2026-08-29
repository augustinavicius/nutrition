package io.github.augustinavicius.nutrition.update

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.augustinavicius.nutrition.NutritionApp
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Periodically asks GitHub whether a newer release exists and, if so, raises a notification.
 * Downloading is left to the user: an APK is a chunky download to start unprompted.
 */
class UpdateCheckWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as NutritionApp).container
        val settings = container.settingsStore.updateSettings.first()
        if (!settings.autoCheck) return Result.success()

        return when (val status = container.updateRepository.check()) {
            is UpdateStatus.Available -> {
                if (status.versionCode > settings.skippedVersionCode) {
                    UpdateNotifications.showUpdateAvailable(applicationContext, status.release.tagName)
                }
                Result.success()
            }
            is UpdateStatus.Error -> Result.retry()
            else -> Result.success()
        }
    }

    companion object {
        private const val UNIQUE_NAME = "periodic-update-check"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(6, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setInitialDelay(15, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME)
        }
    }
}
