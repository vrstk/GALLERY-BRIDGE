package com.gallerybridge.app.network

import android.content.Context
import android.net.Uri
import com.gallerybridge.app.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.CancellationException
import kotlin.coroutines.coroutineContext
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.Jsoup
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.UUID

object Services {
    private val http = OkHttpClient.Builder().followRedirects(true).build()

    /** Abort already-running OkHttp network calls when the user presses Stop. */
    fun cancelActiveTransfers() { http.dispatcher.cancelAll() }

    // Повторяем только временные сетевые ошибки и перегрузку сервера.
    // Ограниченный backoff: не зацикливаемся при недоступности DNS.
    private fun isTemporaryNetworkFailure(e: Throwable): Boolean =
        generateSequence(e) { it.cause }.any {
            it is UnknownHostException || it is SocketTimeoutException || it is ConnectException
        }

    private fun mailCall(request: Request): Response {
        for (attempt in 0..2) {
            try {
                val response = http.newCall(request).execute()
                if (response.code !in setOf(429, 500, 502, 503, 504) || attempt == 2) {
                    return response
                }
                response.close()
            } catch (e: IOException) {
                if (attempt == 2 || !isTemporaryNetworkFailure(e)) throw e
            }
            Thread.sleep(if (attempt == 0) 1500L else 3500L)
        }
        error("Нет ответа от Mail WebDAV")
    }

    private fun mailUrl(path: List<String>): HttpUrl =
        path.fold("https://webdav.cloud.mail.ru/".toHttpUrl()) { url, segment ->
            url.newBuilder().addPathSegment(Paths.clean(segment)).build()
        }

    private fun mailExists(url: HttpUrl, auth: String): Boolean =
        mailCall(Request.Builder().url(url).header("Authorization", auth)
            .header("Depth", "0").method("PROPFIND", null).build()).use { r ->
            when (r.code) {
                200, 207 -> true
                404 -> false
                else -> error("Mail WebDAV PROPFIND: HTTP ${r.code}")
            }
        }
    private val ext = Regex("\\.(jpe?g|png|webp|gif|avif|bmp|mp4|m4v|mov|webm)$", RegexOption.IGNORE_CASE)
    private fun kind(path: String) = if (Regex("\\.(mp4|m4v|mov|webm)$", RegexOption.IGNORE_CASE).containsMatchIn(path)) Kind.VIDEO else Kind.PHOTO
    private fun request(url: String) = Request.Builder().url(url).header("User-Agent", "GalleryBridge/0.1").build()

    suspend fun scan(raw: String): Scan = withContext(Dispatchers.IO) {
        val url = raw.trim().toHttpUrlOrNull() ?: error("Неверный URL")
        require(url.scheme == "https") { "Нужна HTTPS-ссылка" }
        if (ext.containsMatchIn(url.encodedPath)) return@withContext Scan("Прямая ссылка", "", listOf(Media(url.toString(), Paths.clean(url.pathSegments.last()), kind(url.encodedPath), "Прямой URL")))
        http.newCall(request(url.toString())).execute().use { response ->
            if (!response.isSuccessful) error("Сайт вернул HTTP ${response.code}")
            val type = response.header("Content-Type").orEmpty()
            if (type.startsWith("image/") || type.startsWith("video/"))
                return@use Scan("Прямой файл", "", listOf(Media(response.request.url.toString(), Paths.clean(response.request.url.pathSegments.last()), if (type.startsWith("video/")) Kind.VIDEO else Kind.PHOTO, "Прямой файл")))
            require(type.contains("html") || type.isBlank()) { "Не HTML-страница" }
            val html = response.body?.string().orEmpty().take(3_000_000)
            val doc = Jsoup.parse(html, response.request.url.toString())
            val urls = linkedMapOf<String, Media>()
            fun add(rawUrl: String, source: String) {
                val parsed = rawUrl.toHttpUrlOrNull() ?: return
                if (parsed.scheme != "https" || !ext.containsMatchIn(parsed.encodedPath)) return
                urls.putIfAbsent(parsed.toString(), Media(parsed.toString(), Paths.clean(parsed.pathSegments.last()), kind(parsed.encodedPath), source))
            }
            doc.select("a[href]").forEach { add(it.absUrl("href"), "Ссылка") }
            doc.select("img[src],img[data-src],img[data-original],img[data-full],img[srcset],img[data-srcset],source[srcset]").forEach {
                add(it.absUrl("data-original"), "HTML data-original (качество не подтверждено)")
                add(it.absUrl("data-full"), "HTML data-full (качество не подтверждено)")
                add(it.absUrl("data-src"), "Изображение (качество не подтверждено)")
                add(it.absUrl("src"), "Изображение (качество не подтверждено)")
                for (attribute in listOf("srcset", "data-srcset")) {
                    val value = it.attr(attribute)
                    for (part in value.split(',')) {
                        val candidate = part.trim().split(Regex("\\s+"))
                        if (candidate.isEmpty()) continue
                        val absolute = it.baseUri().toHttpUrlOrNull()
                            ?.resolve(candidate.first())?.toString().orEmpty()
                        val width = candidate.drop(1).firstOrNull { descriptor ->
                            Regex("\\d{3,4}w").matches(descriptor)
                        }.orEmpty()
                        add(absolute, "srcset $width (размер из HTML, не проверен)")
                    }
                }
            }
            doc.select("video[src],source[src]").forEach { add(it.absUrl("src"), "Видео") }
            val title = doc.selectFirst("meta[property=og:title]")?.attr("content")?.ifBlank { doc.title() } ?: doc.title()
            val date = listOf("meta[property=article:published_time]", "time[datetime]").firstNotNullOfOrNull {
                selector -> doc.selectFirst(selector)?.let { el -> Regex("\\d{4}-\\d{2}-\\d{2}").find(el.attr(if (selector.startsWith("time")) "datetime" else "content"))?.value }
            }.orEmpty()
            Scan(title.ifBlank { "Без названия" }, date, urls.values.take(300))
        }
    }

    private fun download(context: Context, media: Media): Pair<File, String> {
        val file = File(context.cacheDir, "gb_${UUID.randomUUID()}.part")
        try {
            http.newCall(request(media.url)).execute().use { r ->
                if (!r.isSuccessful) error("HTTP ${r.code}")
                val mime = r.header("Content-Type")?.substringBefore(';').orEmpty()
                if (mime.startsWith("text/") || mime.contains("html")) error("Вместо файла получен текст")
                val body = r.body ?: error("Пустой файл")
                val expected = body.contentLength()
                if (expected > 0 && context.cacheDir.usableSpace < expected + 20_000_000) error("Недостаточно памяти")
                body.byteStream().use { input -> file.outputStream().use { output -> input.copyTo(output) } }
                if (file.length() == 0L || expected >= 0 && file.length() != expected) error("Неполный файл")
                return file to (mime.ifBlank { "application/octet-stream" })
            }
        } catch (e: Exception) { file.delete(); throw e }
    }

    private fun webdavFolders(email: String, password: String, path: List<String>) {
        var current = "https://webdav.cloud.mail.ru/"
        val auth = Credentials.basic(email, password)
        for (segment in path) {
            current = current.toHttpUrl().newBuilder().addPathSegment(Paths.clean(segment)).build().toString()
            val check = Request.Builder().url(current).header("Authorization", auth).header("Depth", "0").method("PROPFIND", null).build()
            val exists = mailCall(check).use { r ->
                when (r.code) { 200, 207 -> true; 404 -> false; else -> error("WebDAV проверка HTTP ${r.code}") }
            }
            if (!exists) {
                val mkcol = Request.Builder().url(current).header("Authorization", auth).method("MKCOL", null).build()
                mailCall(mkcol).use { r -> if (r.code !in listOf(200, 201, 204, 405)) error("WebDAV папка HTTP ${r.code}") }
            }
        }
    }


    private fun driveRequest(token: String, body: RequestBody, url: String): Request = Request.Builder().url(url).header("Authorization", "Bearer $token").post(body).build()
    private fun driveFolders(token: String, root: String, path: List<String>): String {
        var parent = root
        for (part in path) {
            val name = Paths.clean(part)
            val q = "'${parent.replace("'", "\\'")}' in parents and name = '${name.replace("'", "\\'")}' and trashed = false and mimeType = 'application/vnd.google-apps.folder'"
            val findUrl = "https://www.googleapis.com/drive/v3/files".toHttpUrl().newBuilder().addQueryParameter("q", q).addQueryParameter("fields", "files(id)").build()
            val found = http.newCall(Request.Builder().url(findUrl).header("Authorization", "Bearer $token").build()).execute().use { r ->
                if (!r.isSuccessful) error("Drive список HTTP ${r.code}")
                JSONObject(r.body?.string().orEmpty()).optJSONArray("files")?.optJSONObject(0)?.optString("id")?.ifBlank { null }
            }
            parent = found ?: run {
                val json = JSONObject().put("name", name).put("mimeType", "application/vnd.google-apps.folder").put("parents", JSONArray().put(parent))
                http.newCall(driveRequest(token, json.toString().toRequestBody("application/json".toMediaType()), "https://www.googleapis.com/drive/v3/files?fields=id")).execute().use { r ->
                    if (!r.isSuccessful) error("Drive создание HTTP ${r.code}")
                    JSONObject(r.body?.string().orEmpty()).getString("id")
                }
            }
        }
        return parent
    }

    private fun driveUpload(token: String, folder: String, name: String, file: File, mime: String): Boolean {
        val q = "'${folder.replace("'", "\\'")}' in parents and name = '${name.replace("'", "\\'")}' and trashed = false"
        val findUrl = "https://www.googleapis.com/drive/v3/files".toHttpUrl().newBuilder().addQueryParameter("q", q).addQueryParameter("fields", "files(id)").build()
        val exists = http.newCall(Request.Builder().url(findUrl).header("Authorization", "Bearer $token").build()).execute().use { r ->
            if (!r.isSuccessful) error("Drive список HTTP ${r.code}")
            (JSONObject(r.body?.string().orEmpty()).optJSONArray("files")?.length() ?: 0) > 0
        }
        if (exists) return false
        val metadata = JSONObject().put("name", name).put("parents", JSONArray().put(folder))
        val session = http.newCall(Request.Builder().url("https://www.googleapis.com/upload/drive/v3/files?uploadType=resumable")
            .header("Authorization", "Bearer $token").header("X-Upload-Content-Type", mime)
            .post(metadata.toString().toRequestBody("application/json".toMediaType())).build()).execute().use { r ->
            if (!r.isSuccessful) error("Drive сессия HTTP ${r.code}")
            r.header("Location") ?: error("Нет URL для загрузки")
        }
        val target = session.toHttpUrl()
        require(target.scheme == "https" && target.host == "www.googleapis.com") { "Неверный Drive URL" }
        http.newCall(Request.Builder().url(target).header("Authorization", "Bearer $token")
            .put(file.asRequestBody(mime.toMediaType())).build()).execute().use { r -> if (!r.isSuccessful) error("Drive загрузка HTTP ${r.code}") }
        return true
    }

    suspend fun transfer(
        context: Context,
        media: List<Media>,
        model: String,
        title: String,
        date: String,
        target: String,
        username: String,
        password: String,
        root: String,
        token: String?,
        progress: suspend (String) -> Unit,
        onItemResult: suspend (Media, String) -> Unit = { _, _ -> }
    ): String = withContext(Dispatchers.IO) {
        var uploaded = 0
        var skipped = 0
        var failed = 0
        var attempted = 0
        var consecutiveNetworkFailures = 0
        var stoppedReason: String? = null
        val failures = mutableListOf<String>()
        val readyMailFolders = mutableSetOf<Kind>()
        val driveFoldersByKind = mutableMapOf<Kind, String>()
        val documentFoldersByKind = mutableMapOf<Kind, Uri>()
        val mailAuth = if (target == "Mail WebDAV") Credentials.basic(username, password) else ""
        require(target in setOf("Mail WebDAV", "Google Drive", "Папка Android")) { "Неизвестное облако" }

        for ((index, item) in media.withIndex()) {
            coroutineContext.ensureActive()
            attempted++
            progress("${index + 1}/${media.size}: ${item.name}")
            try {
                val folders = Paths.folders(model, date, title, item.kind)
                if (target == "Mail WebDAV") {
                    val cloudPath = Paths.splitRoot(root) + folders
                    // После успеха не проверяем одни и те же папки перед каждым файлом.
                    if (item.kind !in readyMailFolders) {
                        webdavFolders(username, password, cloudPath)
                        readyMailFolders += item.kind
                    }
                    val destination = mailUrl(cloudPath + Paths.clean(item.name))
                    // Пропускаем ранее сохранённый файл до повторного скачивания.
                    if (mailExists(destination, mailAuth)) {
                        coroutineContext.ensureActive()
                        skipped++
                        onItemResult(item, "SKIPPED")
                        consecutiveNetworkFailures = 0
                        continue
                    }
                    val (local, mime) = download(context, item)
                    try {
                        coroutineContext.ensureActive()
                        mailCall(Request.Builder().url(destination)
                            .header("Authorization", mailAuth)
                            .put(local.asRequestBody(mime.toMediaType())).build()).use { response ->
                            if (response.code !in listOf(200, 201, 204)) {
                                error("Mail WebDAV PUT: HTTP ${response.code}")
                            }
                        }
                        coroutineContext.ensureActive()
                        uploaded++
                        onItemResult(item, "DONE")
                        consecutiveNetworkFailures = 0
                    } finally {
                        local.delete()
                    }
                } else if (target == "Папка Android") {
                    val destFolder = documentFoldersByKind.getOrPut(item.kind) {
                        SafDestination.resolveFolder(context, root, folders)
                    }
                    // Check before source download; previously every resume fetched
                    // already existing files again, wasting time and traffic.
                    if (SafDestination.fileExists(context, destFolder, item.name)) {
                        skipped++
                        onItemResult(item, "SKIPPED")
                    } else {
                        val (local, mime) = download(context, item)
                        try {
                            coroutineContext.ensureActive()
                            if (SafDestination.uploadFile(context, destFolder, item.name, local, mime)) {
                                coroutineContext.ensureActive()
                                uploaded++
                                onItemResult(item, "DONE")
                            } else {
                                skipped++
                                onItemResult(item, "SKIPPED")
                            }
                        } finally {
                            local.delete()
                        }
                    }
                } else {
                    val folderId = driveFoldersByKind.getOrPut(item.kind) {
                        driveFolders(token ?: error("Нет Google OAuth"), root, folders)
                    }
                    val (local, mime) = download(context, item)
                    try {
                        coroutineContext.ensureActive()
                        if (driveUpload(token ?: error("Нет Google OAuth"), folderId, Paths.clean(item.name), local, mime)) {
                            coroutineContext.ensureActive()
                            uploaded++
                            onItemResult(item, "DONE")
                        } else {
                            coroutineContext.ensureActive()
                            skipped++
                            onItemResult(item, "SKIPPED")
                        }
                    } finally {
                        local.delete()
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                coroutineContext.ensureActive()
                failed++
                onItemResult(item, "FAILED")
                if (target == "Папка Android" && (
                    e.message.orEmpty().contains("дубликат", ignoreCase = true) ||
                    e.message.orEmpty().contains("одноимённые", ignoreCase = true) ||
                    e.message.orEmpty().contains("Создание заблокировано", ignoreCase = true) ||
                    e.message.orEmpty().contains("Новые папки не создавались", ignoreCase = true)
                )) {
                    stoppedReason = "Обнаружена ошибка структуры папок или провайдера. " +
                        "Передача остановлена, чтобы не создавать новые дубли."
                    if (failures.size < 20) failures.add(item.name + ": " + e.message)
                    break
                }
                if (failures.size < 20) {
                    failures.add("${item.name}: ${e.message ?: e.javaClass.simpleName}")
                }
                if (target == "Mail WebDAV" && isTemporaryNetworkFailure(e)) {
                    consecutiveNetworkFailures++
                    if (consecutiveNetworkFailures >= 2) {
                        stoppedReason = "Передача остановлена: нет связи с источником или облаком (DNS/сеть). Проверь интернет и повтори позже."
                        break
                    }
                } else {
                    consecutiveNetworkFailures = 0
                }
            }
        }
        coroutineContext.ensureActive()
        val remaining = media.size - attempted
        buildString {
            append("Загружено: $uploaded; пропущено: $skipped; ошибок: $failed")
            if (remaining > 0) append("; не обработано: $remaining")
            if (stoppedReason != null) append("\n").append(stoppedReason)
            if (failures.isNotEmpty()) {
                append("\nПричины ошибок:")
                failures.forEach { append("\n• ").append(it) }
            }
            append("\nУже существующие файлы пропускаются по имени. Целостность старых файлов ещё не проверяется.")
        }
    }
}
