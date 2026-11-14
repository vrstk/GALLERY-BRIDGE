package com.gallerybridge.app.network

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.DocumentsContract
import com.gallerybridge.app.core.Kind
import com.gallerybridge.app.core.Media
import com.gallerybridge.app.core.Paths
import com.gallerybridge.app.transfer.QueueStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

/**
 * Read-only SHA-256 comparison of the selected source URLs and provider bytes.
 * This does not confirm remote server synchronization or the site's original quality.
 */
object IntegrityAudit {
    private const val LIMIT = 32L * 1024 * 1024
    private const val DIR = DocumentsContract.Document.MIME_TYPE_DIR
    private val http = OkHttpClient.Builder().callTimeout(90, TimeUnit.SECONDS).build()
    private data class Entry(val id: String, val name: String, val mime: String)
    private class TooLarge : Exception("Файл больше 32 МиБ")

    data class Result(
        val total: Int, val matched: Int, val different: Int,
        val unavailable: Int, val oversized: Int,
        val dimensions: List<String>, val issues: List<String>
    ) {
        fun summary(): String = buildString {
            append("SHA-256: совпадают " + matched + " из " + total + ".")
            append("\nОтличаются: " + different + "; недоступно: " + unavailable +
                "; больше лимита: " + oversized)
            if (dimensions.isNotEmpty()) {
                append("\nРазмеры доступных изображений:")
                dimensions.take(6).forEach { append("\n• " + it) }
            }
            if (issues.isNotEmpty()) {
                append("\nПодробности:")
                issues.take(12).forEach { append("\n• " + it) }
            }
            append("\nПроверяются байты Android-провайдера и URL источника. " +
                "Это не гарантия серверной синхронизации или оригинального качества сайта.")
        }
    }

    private fun children(context: Context, tree: Uri, parent: String): List<Entry> {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parent)
        val columns = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )
        val c = context.contentResolver.query(uri, columns, null, null, null)
            ?: error("Провайдер Android не вернул список папки")
        return c.use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(
                    Entry(cursor.getString(0), cursor.getString(1), cursor.getString(2))
                )
            }
        }
    }

    private fun existingFile(context: Context, tree: Uri, path: List<String>, name: String): Uri {
        var parent = DocumentsContract.getTreeDocumentId(tree)
        for (segment in path) {
            val found = children(context, tree, parent).filter { it.name == Paths.clean(segment) }
            require(found.size == 1 && found.single().mime == DIR) {
                "Нет однозначной папки: " + segment + " (" + found.size + " совпадений)"
            }
            parent = found.single().id
        }
        val found = children(context, tree, parent).filter { it.name == Paths.clean(name) }
        require(found.size == 1 && found.single().mime != DIR) {
            "Нет однозначного файла: " + name + " (" + found.size + " совпадений)"
        }
        return DocumentsContract.buildDocumentUriUsingTree(tree, found.single().id)
    }

    private suspend fun hash(stream: InputStream): String {
        val md = MessageDigest.getInstance("SHA-256")
        val buf = ByteArray(65536)
        var length = 0L
        stream.use { input ->
            while (true) {
                coroutineContext.ensureActive()
                val count = input.read(buf)
                if (count < 0) break
                if (count == 0) continue
                length += count
                if (length > LIMIT) throw TooLarge()
                md.update(buf, 0, count)
            }
        }
        require(length > 0L) { "Файл пустой" }
        return md.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    }

    private suspend fun sourceHash(media: Media): String {
        require(Uri.parse(media.url).scheme == "https") { "Источник должен быть HTTPS" }
        val req = Request.Builder().url(media.url).header("User-Agent", "GalleryBridge/0.1").build()
        return http.newCall(req).execute().use { response ->
            require(response.isSuccessful) { "Источник HTTP " + response.code }
            require(response.request.url.scheme == "https") { "Источник перенаправил на HTTP" }
            val type = response.header("Content-Type").orEmpty().lowercase()
            require(!type.startsWith("text/") && !type.contains("html")) {
                "Источник вернул текст вместо медиафайла"
            }
            val body = response.body ?: error("Источник вернул пустой ответ")
            if (body.contentLength() > LIMIT) throw TooLarge()
            hash(body.byteStream())
        }
    }

    private fun imageDimensions(context: Context, uri: Uri): String {
        return try {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            }
            if (opts.outWidth > 0 && opts.outHeight > 0)
                opts.outWidth.toString() + "×" + opts.outHeight else ""
        } catch (_: Exception) { "" }
    }

    suspend fun check(
        context: Context,
        queue: QueueStore.Snapshot,
        progress: suspend (Int, Int, String) -> Unit
    ): Result = withContext(Dispatchers.IO) {
        require(queue.target == "Папка Android" && queue.remaining == 0) {
            "Проверка доступна для завершённой очереди через папку Android"
        }
        val tree = Uri.parse(queue.root)
        require(tree.scheme == "content") { "Папка Android недоступна" }
        val media = queue.entries.map { it.media }
        var matched = 0
        var different = 0
        var unavailable = 0
        var oversized = 0
        val dimensions = mutableListOf<String>()
        val issues = mutableListOf<String>()
        for ((i, item) in media.withIndex()) {
            coroutineContext.ensureActive()
            progress(i + 1, media.size, item.name)
            try {
                val path = Paths.folders(queue.model, queue.date, queue.title, item.kind)
                val uri = SafDestination.existingFileForAudit(context, queue.root, path, item.name)
                val src = sourceHash(item)
                val dest = context.contentResolver.openInputStream(uri)?.let { hash(it) }
                    ?: error("Невозможно прочитать сохранённый файл")
                if (src == dest) {
                    matched++
                    if (item.kind == Kind.PHOTO && dimensions.size < 6) {
                        val size = imageDimensions(context, uri)
                        if (size.isNotBlank()) dimensions.add(item.name + ": " + size)
                    }
                } else {
                    different++
                    if (issues.size < 12) issues.add(item.name + ": SHA-256 не совпадает")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: TooLarge) {
                oversized++
                if (issues.size < 12) issues.add(item.name + ": размер превышает 32 МиБ")
            } catch (e: Exception) {
                unavailable++
                if (issues.size < 12) issues.add(item.name + ": " + (e.message ?: "ошибка чтения"))
            }
        }
        Result(media.size, matched, different, unavailable, oversized, dimensions, issues)
    }
}
