package com.gallerybridge.app.update

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
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/**
 * Persistent user-initiated transfer. WorkManager re-enqueues on process death
 * and retries IO interruptions, continuing from an app-private .part file.
 * Never launches PackageInstaller: confirmation remains an explicit UI action.
 */
internal class UpdateDownloadWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    companion object {
        const val UNIQUE = "gallery_bridge_signed_apk_download"
        const val HASH = "sha256"
        const val CODE = "versionCode"
        const val BYTES = "downloadedBytes"
        const val ERROR = "downloadError"
        private const val CHANNEL = "gallery_bridge_apk_download"
        private const val NOTIFICATION = 456
        private const val MAX_RETRIES = 4

        fun enqueue(context: Context, release: UpdatePolicy.Release) {
            // The signed manifest is persisted by UpdateManager.check().
            val request = OneTimeWorkRequestBuilder<UpdateDownloadWorker>()
                .setInputData(workDataOf(HASH to release.hash, CODE to release.code))
                .addTag("apk:${release.hash}:${release.code}")
                .setConstraints(Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE, ExistingWorkPolicy.REPLACE, request
            )
        }

        fun matches(tags: Set<String>, release: UpdatePolicy.Release): Boolean =
            "apk:${release.hash}:${release.code}" in tags

        fun partialFile(context: Context, id: java.util.UUID): File =
            UpdatePartialFiles.part(File(context.cacheDir, "updates"), id)
    }

    private fun foreground(progress: Int): ForegroundInfo {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(
            CHANNEL, "Скачивание обновления", NotificationManager.IMPORTANCE_LOW))
        val notification: Notification =
            NotificationCompat.Builder(applicationContext, CHANNEL)
                .setSmallIcon(R.drawable.ic_download)
                .setContentTitle("Обновление GALLERY BRIDGE")
                .setContentText("Загружено $progress%")
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setProgress(100, progress.coerceIn(0, 100), false)
                .addAction(
                    R.drawable.ic_download, "Отменить",
                    WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)
                )
                .build()
        return if (Build.VERSION.SDK_INT >= 29)
            ForegroundInfo(NOTIFICATION, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else ForegroundInfo(NOTIFICATION, notification)
    }

    override suspend fun doWork(): Result {
        val wantedHash = inputData.getString(HASH) ?: return Result.failure()
        val wantedCode = inputData.getLong(CODE, -1L)
        val raw = applicationContext.getSharedPreferences(
            "gallery_bridge_updater", Context.MODE_PRIVATE
        ).getString("validatedManifest", null) ?: return Result.failure(
            workDataOf(ERROR to "Нет проверенного манифеста обновления.")
        )
        val release = runCatching { UpdatePolicy.parse(raw) }.getOrNull()
            ?.takeIf { it.hash == wantedHash && it.code == wantedCode }
            ?: return Result.failure(workDataOf(ERROR to "Манифест обновления изменился."))
        val file = partialFile(applicationContext, id)
        return try {
            withContext(Dispatchers.IO) {
                UpdatePartialFiles.cleanOrphans(file.parentFile!!, setOf(id))
            }
            setForeground(foreground(
                (file.length().coerceAtMost(release.bytes) * 100L / release.bytes).toInt()
            ))
            withContext(Dispatchers.IO) { downloadAndVerify(file, release) }
            Result.success()
        } catch (e: CancellationException) {
            // Preserve partial bytes for WorkManager interruptions: the same
            // WorkRequest ID will be resumed if the system reschedules it.
            throw e
        } catch (e: IOException) {
            if (runAttemptCount < MAX_RETRIES) Result.retry()
            else {
                file.delete()
                Result.failure(workDataOf(ERROR to
                    "Сеть недоступна после повторных попыток: ${e.message.orEmpty().take(160)}"))
            }
        } catch (e: Exception) {
            file.delete() // Never resume a corrupt or rejected APK.
            Result.failure(workDataOf(ERROR to
                "Загрузка отклонена: ${e.message.orEmpty().take(160)}"))
        }
    }

    private suspend fun downloadAndVerify(partial: File, release: UpdatePolicy.Release) {
        val folder = partial.parentFile ?: error("Нет временного каталога")
        require(folder.isDirectory || folder.mkdirs()) { "Нет доступа к временному каталогу" }
        val complete = File(folder, "verified.apk")
        var start = if (partial.isFile) partial.length() else 0L
        if (start > release.bytes) {
            partial.delete()
            start = 0L
        }
        if (start == release.bytes) {
            if (runCatching {
                UpdateArchiveVerifier.verify(applicationContext, partial, release)
            }.isSuccess) {
                replaceVerified(partial, complete)
                return
            }
            partial.delete()
            start = 0L
        }
        coroutineContext.ensureActive()
        val connection = UpdateConnections.connect(release.apkUrl, start)
        try {
            var lastReported = -1
            connection.inputStream.use { stream ->
                UpdatePartialFiles.receive(
                    part = partial,
                    total = release.bytes,
                    requestedOffset = start,
                    code = connection.responseCode,
                    contentRange = connection.getHeaderField("Content-Range"),
                    source = stream
                ) { count ->
                    val progress = (count * 100L / release.bytes).toInt()
                    if (progress != lastReported) {
                        lastReported = progress
                        setProgress(workDataOf(BYTES to count))
                        setForeground(foreground(progress))
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
        coroutineContext.ensureActive()
        UpdateArchiveVerifier.verify(applicationContext, partial, release)
        coroutineContext.ensureActive()
        replaceVerified(partial, complete)
    }

    private fun replaceVerified(partial: File, complete: File) {
        if (complete.exists()) require(complete.delete()) { "Не удалось очистить старый APK" }
        require(partial.renameTo(complete)) { "Не удалось сохранить проверенную APK" }
    }
}
