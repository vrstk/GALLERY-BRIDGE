package com.gallerybridge.app.network

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.gallerybridge.app.R
import com.gallerybridge.app.core.ModelListingCheckpointStore
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

/** User-initiated, resumable, no-credentials HTML-index scan. */
internal class ModelListingScanWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    companion object {
        const val UNIQUE = "gallery_bridge_model_index_scan"
        const val SOURCE = "source"
        const val LOADED = "loaded"
        const val TOTAL = "total"
        private const val CHANNEL = "gallery_bridge_scanning"
        private const val NOTIFICATION = 451

        fun enqueue(context: Context, source: String) {
            val request = OneTimeWorkRequestBuilder<ModelListingScanWorker>()
                .setInputData(workDataOf(SOURCE to source))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30L, TimeUnit.SECONDS)
                .build()
            // Begin checkpoint before the new WorkInfo becomes visible.
            ModelListingCheckpointStore.begin(context, request.id.toString(), source)
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE, ExistingWorkPolicy.REPLACE, request)
        }
    }

    private fun notification(loaded: Int): Notification {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(
            CHANNEL, "Поиск фотогалерей", NotificationManager.IMPORTANCE_LOW))
        return NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle("Поиск фотогалерей")
            .setContentText("Обработано страниц: $loaded")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    override suspend fun doWork(): Result {
        val source = inputData.getString(SOURCE) ?: return Result.failure()
        val checkpoint = ModelListingCheckpointStore.read(applicationContext)
            ?: return Result.failure()
        if (checkpoint.source != source || checkpoint.requestId != id.toString())
            return Result.success() // Obsolete request, superseded without touching the new index.
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                setForeground(ForegroundInfo(NOTIFICATION, notification(checkpoint.pages.size),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC))
            } else {
                setForeground(ForegroundInfo(NOTIFICATION, notification(checkpoint.pages.size)))
            }
            val result = Services.scanModelIndex(
                source,
                progress = { loaded, total ->
                    setProgress(workDataOf(LOADED to loaded, TOTAL to total))
                },
                recovered = checkpoint.pages,
                onPage = { page ->
                    ModelListingCheckpointStore.add(applicationContext, id.toString(), page)
                    setProgress(workDataOf(LOADED to
                        (ModelListingCheckpointStore.read(applicationContext)?.pages?.size ?: 0)))
                })
            ModelListingCheckpointStore.finish(applicationContext, id.toString(), result,
                apiComplete = !result.incomplete && result.errors.isEmpty() &&
                    ModelListingCheckpointStore.read(applicationContext)?.pages
                        ?.any { it.apiUrl.isNotBlank() } == true)
            return Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            return if (runAttemptCount < 3) Result.retry() else Result.failure()
        } catch (_: Exception) {
            return Result.failure()
        }
    }
}
