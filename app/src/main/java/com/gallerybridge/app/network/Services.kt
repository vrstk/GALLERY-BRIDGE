package com.gallerybridge.app.network

import android.content.Context
import com.gallerybridge.app.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
import java.util.UUID

object Services {
    private val http = OkHttpClient.Builder().followRedirects(true).build()
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
            doc.select("img[src],img[data-src],img[data-original]").forEach {
                add(it.absUrl("data-original"), "Изображение (качество не подтверждено)")
                add(it.absUrl("data-src"), "Изображение (качество не подтверждено)")
                add(it.absUrl("src"), "Изображение (качество не подтверждено)")
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
            val exists = http.newCall(check).execute().use { r ->
                when (r.code) { 200, 207 -> true; 404 -> false; else -> error("WebDAV проверка HTTP ${r.code}") }
            }
            if (!exists) {
                val mkcol = Request.Builder().url(current).header("Authorization", auth).method("MKCOL", null).build()
                http.newCall(mkcol).execute().use { r -> if (r.code !in listOf(200, 201, 204, 405)) error("WebDAV папка HTTP ${r.code}") }
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

    suspend fun transfer(context: Context, media: List<Media>, model: String, title: String, date: String, target: String, username: String, password: String, root: String, token: String?, progress: suspend (String) -> Unit): String = withContext(Dispatchers.IO) {
        var ok = 0; var skipped = 0; var failed = 0
        for ((index, item) in media.withIndex()) {
            progress("${index+1}/${media.size}: ${item.name}")
            try {
                val folders = Paths.folders(model, date, title, item.kind)
                val driveFolder = if (target == "Google Drive") driveFolders(token ?: error("Нет Google OAuth"), root, folders) else ""
                if (target == "Mail WebDAV") webdavFolders(username, password, listOf(root) + folders)
                val (file, mime) = download(context, item)
                try {
                    val uploaded = if (target == "Google Drive") driveUpload(token!!, driveFolder, Paths.clean(item.name), file, mime) else {
                        val components = listOf(root) + folders + Paths.clean(item.name)
                        val url = components.fold("https://webdav.cloud.mail.ru/".toHttpUrl()) { acc, segment -> acc.newBuilder().addPathSegment(Paths.clean(segment)).build() }
                        val auth = Credentials.basic(username, password)
                        val check = http.newCall(Request.Builder().url(url).header("Authorization", auth).header("Depth", "0").method("PROPFIND", null).build()).execute().use { r ->
                            when (r.code) { 200, 207 -> true; 404 -> false; else -> error("WebDAV проверка HTTP ${r.code}") }
                        }
                        if (check) false else {
                            http.newCall(Request.Builder().url(url).header("Authorization", auth).put(file.asRequestBody(mime.toMediaType())).build()).execute().use { r -> if (r.code !in listOf(200,201,204)) error("WebDAV загрузка HTTP ${r.code}") }
                            true
                        }
                    }
                    if (uploaded) ok++ else skipped++
                } finally { file.delete() }
            } catch (_: Exception) { failed++ }
        }
        "Загружено: $ok; пропущено: $skipped; ошибок: $failed"
    }
}
