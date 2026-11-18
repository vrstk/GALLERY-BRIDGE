package com.gallerybridge.app.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

internal class UpdateManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val transferring: () -> Boolean
) {
    data class State(
        val checking: Boolean = false,
        val downloading: Boolean = false,
        val progress: Int = 0,
        val release: UpdatePolicy.Release? = null,
        val message: String = "",
        val lastCheck: Long = 0,
        val ready: Boolean = false
    )

    private val preferences = context.getSharedPreferences("gallery_bridge_updater", Context.MODE_PRIVATE)
    private val folder = File(context.cacheDir, "updates")
    private val complete = File(folder, "verified.apk")
    private var checkJob: Job? = null
    var automatic by mutableStateOf(preferences.getBoolean("automatic", true))
        private set
    private val storedRelease = preferences.getString("validatedManifest", null)
        ?.let { raw -> runCatching { UpdatePolicy.parse(raw) }.getOrNull() }
        ?.takeIf { UpdatePolicy.isNewer(it.code, installedCode()) }
    var state by mutableStateOf(State(
        lastCheck = preferences.getLong("lastSuccess", preferences.getLong("lastCheck", 0L)),
        release = storedRelease))
        private set

    init {
        // Durable work is observed across Activity recreation and app process restart.
        scope.launch {
            WorkManager.getInstance(context.applicationContext)
                .getWorkInfosForUniqueWorkFlow(UpdateDownloadWorker.UNIQUE)
                .collect { work ->
                    val release = state.release ?: return@collect
                    val matching = work.filter { info ->
                        UpdateDownloadWorker.matches(info.tags, release)
                    }
                    val current = matching.firstOrNull { !it.state.isFinished }
                        ?: matching.firstOrNull() ?: return@collect
                    when (current.state) {
                        WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING, WorkInfo.State.BLOCKED -> {
                            val loaded = current.progress.getLong(UpdateDownloadWorker.BYTES, 0L)
                            state = state.copy(downloading = true, ready = false,
                                progress = (loaded.coerceIn(0L, release.bytes) * 100L /
                                    release.bytes).toInt(),
                                message = if (current.state == WorkInfo.State.RUNNING)
                                    "Скачивание APK продолжается, можно свернуть приложение."
                                    else "Скачивание ожидает доступной сети.")
                        }
                        WorkInfo.State.SUCCEEDED -> {
                            val valid = withContext(Dispatchers.IO) {
                                runCatching {
                                    UpdateArchiveVerifier.verify(context, complete, release)
                                    true
                                }.getOrDefault(false)
                            }
                            state = state.copy(downloading = false, ready = valid,
                                progress = if (valid) 100 else 0,
                                message = if (valid)
                                    "APK проверена. Установка требует подтверждения Android."
                                else "Скачанная APK не прошла повторную проверку.")
                        }
                        WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> {
                            state = state.copy(downloading = false, ready = false,
                                progress = 0,
                                message = if (current.state == WorkInfo.State.CANCELLED)
                                    "Загрузка отменена."
                                else current.outputData.getString(UpdateDownloadWorker.ERROR)
                                    ?: "Загрузка прервана. Повторите попытку.")
                        }
                    }
                    if (current.state.isFinished) {
                        // WorkManager exposes terminal state after cancellation;
                        // keep the partial bytes of all still-running work IDs.
                        withContext(Dispatchers.IO) {
                            UpdatePartialFiles.cleanOrphans(
                                folder,
                                work.filter { !it.state.isFinished }.map { it.id }.toSet()
                            )
                        }
                    }
                }
        }
        // Verify cached complete APK independently of WorkManager's last result.
        val release = storedRelease
        if (release != null && complete.isFile) scope.launch {
            val valid = withContext(Dispatchers.IO) {
                runCatching {
                    UpdateArchiveVerifier.verify(context, complete, release)
                    true
                }.getOrDefault(false)
            }
            if (valid && state.release == release && !state.downloading) {
                state = state.copy(ready = true, progress = 100,
                    message = "Ранее скачанная APK повторно проверена.")
            }
        }
    }
    private var lastAttempt = preferences.getLong("lastAttempt", state.lastCheck)

    fun setAutoCheck(enabled: Boolean) {
        automatic = enabled
        preferences.edit().putBoolean("automatic", enabled).apply()
    }

    fun check(auto: Boolean = false) {
        if (checkJob?.isActive == true || state.downloading) return
        val now = System.currentTimeMillis()
        if (auto && !UpdateCheckPolicy.shouldAutoCheck(automatic, now, state.lastCheck, lastAttempt)) return
        lastAttempt = now
        preferences.edit().putLong("lastAttempt", now).apply()
        checkJob = scope.launch {
            state = state.copy(checking = true, message = "Проверяем обновления…")
            try {
                val (manifest, release) = withContext(Dispatchers.IO) {
                    val response = JSONObject(UpdateConnections.readText(UpdatePolicy.API, 200_000))
                    val assets = response.getJSONArray("assets")
                    var found = false
                    for (index in 0 until assets.length()) {
                        val a = assets.getJSONObject(index)
                        if (a.getString("name") == "update.json" &&
                            a.getString("browser_download_url") == UpdatePolicy.MANIFEST &&
                            a.getLong("size") in 1L..50_000L) found = true
                    }
                    require(found) { "В preview-signed ещё нет update.json" }
                    val json = UpdateConnections.readText(UpdatePolicy.MANIFEST, 50_000)
                    json to UpdatePolicy.parse(json)
                }
                val newer = UpdatePolicy.isNewer(release.code, installedCode())
                // Retain the verified APK for the same release. Only a changed
                // manifest invalidates the previously downloaded package.
                val changed = state.release?.hash != release.hash ||
                    state.release?.code != release.code
                if (!newer || changed) {
                    withContext(Dispatchers.IO) {
                        complete.delete()
                    }
                }
                val checked = System.currentTimeMillis()
                state = state.copy(release = release.takeIf { newer },
                    ready = if (newer && !changed) state.ready else false,
                    lastCheck = checked,
                    message = if (newer) "Доступна версия ${release.version}"
                        else "Установлена актуальная версия")
                preferences.edit().putLong("lastSuccess", checked)
                    .apply {
                        if (newer) putString("validatedManifest", manifest)
                        else remove("validatedManifest")
                    }.apply()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                state = state.copy(message = "Проверка не удалась: ${e.message ?: "нет соединения"}")
            } finally {
                state = state.copy(checking = false)
            }
        }
    }

    fun download() {
        val release = state.release ?: return
        if (state.downloading || checkJob?.isActive == true) return
        // WorkManager persists the transfer, retries transient errors and resumes
        // a partial file. Installing is never automatic.
        state = state.copy(downloading = true, ready = false,
            progress = 0, message = "Скачивание APK поставлено в очередь.")
        try {
            WorkManager.getInstance(context.applicationContext)
                .let { UpdateDownloadWorker.enqueue(context.applicationContext, release) }
        } catch (e: Exception) {
            state = state.copy(downloading = false,
                message = "Не удалось запустить загрузку: ${e.message ?: "ошибка"}")
        }
    }

    fun cancel() {
        WorkManager.getInstance(context.applicationContext)
            .cancelUniqueWork(UpdateDownloadWorker.UNIQUE)
        state = state.copy(downloading = false, ready = false,
            progress = 0, message = "Загрузка отменяется.")
    }

    fun install() {
        val release = state.release ?: return
        if (transferring()) {
            state = state.copy(message = "Завершите передачу фотографий перед установкой.")
            return
        }
        if (!state.ready) return
        try {
            UpdateArchiveVerifier.verify(context, complete, release)
            if (Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
                context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                state = state.copy(message = "Разрешите установку для GALLERY BRIDGE и нажмите «Установить» снова.")
                return
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.updater.provider", complete)
            val intent = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            state = state.copy(message = "Подтвердите установку в системном окне Android.")
        } catch (e: Exception) {
            state = state.copy(message = "Установка недоступна: ${e.message}")
        }
    }

    fun installedVersion(): String =
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "—"

    @Suppress("DEPRECATION")
    private fun installedCode(): Long {
        val p = context.packageManager.getPackageInfo(context.packageName, 0)
        return if (Build.VERSION.SDK_INT >= 28) p.longVersionCode else p.versionCode.toLong()
    }

}
