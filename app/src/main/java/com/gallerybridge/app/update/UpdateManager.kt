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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

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
    private val partial = File(folder, "partial.apk")
    private var checkJob: Job? = null
    private var downloadJob: Job? = null
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
        // The previous APK can be reused only after re-verification on each process start.
        val release = storedRelease
        if (release != null && complete.isFile) scope.launch {
            val valid = withContext(Dispatchers.IO) {
                runCatching { verifyArchive(complete, release); true }.getOrDefault(false)
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
        if (checkJob?.isActive == true || downloadJob?.isActive == true) return
        val now = System.currentTimeMillis()
        if (auto && !UpdateCheckPolicy.shouldAutoCheck(automatic, now, state.lastCheck, lastAttempt)) return
        lastAttempt = now
        preferences.edit().putLong("lastAttempt", now).apply()
        checkJob = scope.launch {
            state = state.copy(checking = true, message = "Проверяем обновления…")
            try {
                val (manifest, release) = withContext(Dispatchers.IO) {
                    val response = JSONObject(readText(UpdatePolicy.API, 200_000))
                    val assets = response.getJSONArray("assets")
                    var found = false
                    for (index in 0 until assets.length()) {
                        val a = assets.getJSONObject(index)
                        if (a.getString("name") == "update.json" &&
                            a.getString("browser_download_url") == UpdatePolicy.MANIFEST &&
                            a.getLong("size") in 1L..50_000L) found = true
                    }
                    require(found) { "В preview-signed ещё нет update.json" }
                    val json = readText(UpdatePolicy.MANIFEST, 50_000)
                    json to UpdatePolicy.parse(json)
                }
                val newer = UpdatePolicy.isNewer(release.code, installedCode())
                removeTemp()
                val checked = System.currentTimeMillis()
                state = state.copy(release = release.takeIf { newer }, ready = false, lastCheck = checked,
                    message = if (newer) "Доступна версия ${release.version}" else "Установлена актуальная версия")
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
        if (downloadJob?.isActive == true || checkJob?.isActive == true) return
        downloadJob = scope.launch {
            state = state.copy(downloading = true, ready = false, progress = 0, message = "Скачиваем APK…")
            try {
                withContext(Dispatchers.IO) {
                    folder.mkdirs()
                    partial.delete()
                    val md = MessageDigest.getInstance("SHA-256")
                    var bytes = 0L
                    connect(release.apkUrl).useConnection { connection ->
                        connection.inputStream.use { stream ->
                            partial.outputStream().use { output ->
                                val chunk = ByteArray(65536)
                                while (true) {
                                    coroutineContext.ensureActive()
                                    val n = stream.read(chunk)
                                    if (n < 0) break
                                    bytes += n
                                    require(bytes <= release.bytes) { "Размер APK превышен" }
                                    md.update(chunk, 0, n)
                                    output.write(chunk, 0, n)
                                    withContext(Dispatchers.Main) {
                                        state = state.copy(progress = (bytes * 100 / release.bytes).toInt())
                                    }
                                }
                            }
                        }
                    }
                    require(bytes == release.bytes) { "Неполная загрузка APK" }
                    require(md.digest().hex() == release.hash) { "SHA-256 APK не совпадает" }
                    verifyArchive(partial, release)
                    complete.delete()
                    require(partial.renameTo(complete)) { "Не удалось сохранить APK" }
                }
                state = state.copy(downloading = false, ready = true, progress = 100,
                    message = "APK проверена. Установка требует подтверждения Android.")
            } catch (e: CancellationException) {
                // Remove partial APK even after the parent coroutine was cancelled.
                withContext(NonCancellable + Dispatchers.IO) { removeTemp() }
                state = state.copy(downloading = false, ready = false, message = "Загрузка отменена")
            } catch (e: Exception) {
                withContext(Dispatchers.IO) { removeTemp() }
                state = state.copy(downloading = false, ready = false,
                    message = "Ошибка загрузки: ${e.message ?: "повторите попытку"}")
            }
        }
    }

    fun cancel() { downloadJob?.cancel() }

    fun install() {
        val release = state.release ?: return
        if (transferring()) {
            state = state.copy(message = "Завершите передачу фотографий перед установкой.")
            return
        }
        if (!state.ready) return
        try {
            verifyArchive(complete, release)
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

    @Suppress("DEPRECATION")
    private fun verifyArchive(apk: File, release: UpdatePolicy.Release) {
        require(apk.isFile && apk.length() == release.bytes) { "Неверный размер APK" }
        val digest = MessageDigest.getInstance("SHA-256")
        apk.inputStream().use { input ->
            val buffer = ByteArray(65536)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        require(digest.digest().hex() == release.hash) { "SHA-256 APK не совпадает" }
        val pm = context.packageManager
        val info = if (Build.VERSION.SDK_INT >= 33)
            pm.getPackageArchiveInfo(apk.absolutePath,
                PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()))
        else pm.getPackageArchiveInfo(apk.absolutePath,
            if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES)
        require(info != null && info.packageName == UpdatePolicy.PACKAGE) { "Неверный package ID APK" }
        val code = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
        require(code == release.code && UpdatePolicy.isNewer(code, installedCode())) { "Версия APK не подходит" }
        require(info.versionName == release.version) { "Версия APK отличается от манифеста" }
        val signatures = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures
        require(signatures?.size == 1) { "Подпись APK не найдена" }
        val certificate = MessageDigest.getInstance("SHA-256").digest(signatures[0].toByteArray()).hex()
        require(certificate == UpdatePolicy.CERTIFICATE) { "Неверная подпись APK" }
    }

    private fun removeTemp() { partial.delete(); complete.delete() }
    private fun ByteArray.hex() = joinToString("") { "%02x".format(it) }

    private inline fun <T> HttpURLConnection.useConnection(block: (HttpURLConnection) -> T): T =
        try { block(this) } finally { disconnect() }

    private fun connect(initial: String): HttpURLConnection {
        var address = initial
        repeat(6) {
            require(address == UpdatePolicy.API || address == UpdatePolicy.MANIFEST ||
                UpdatePolicy.permittedUrl(address) || UpdatePolicy.permittedRedirect(address)) {
                "Недопустимый HTTPS-источник"
            }
            val connection = URL(address).openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 15000
            connection.readTimeout = 30000
            connection.setRequestProperty("User-Agent", "GALLERY-BRIDGE-updater")
            val code = connection.responseCode
            if (code in 300..399) {
                val location = connection.getHeaderField("Location") ?: error("Нет адреса перенаправления")
                val next = URL(URL(address), location).toString()
                connection.disconnect()
                require(UpdatePolicy.permittedRedirect(next)) { "Небезопасное перенаправление" }
                address = next
            } else {
                if (code !in 200..299) {
                    connection.disconnect()
                    error("GitHub HTTP $code")
                }
                return connection
            }
        }
        error("Слишком много перенаправлений")
    }

    private fun readText(url: String, max: Int): String = connect(url).useConnection { connection ->
        connection.inputStream.use { input ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(4096)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                require(out.size() + n <= max) { "Ответ GitHub слишком большой" }
                out.write(buffer, 0, n)
            }
            out.toString(Charsets.UTF_8.name())
        }
    }
}
