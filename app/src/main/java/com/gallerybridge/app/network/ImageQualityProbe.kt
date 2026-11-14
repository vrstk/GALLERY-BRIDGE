package com.gallerybridge.app.network

import android.graphics.BitmapFactory
import com.gallerybridge.app.core.Media
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

/**
 * Voluntary, read-only verification of actual image dimensions.
 * Only explicitly linked HTTPS media are requested; no speculative URLs.
 * Reads a bounded prefix of the response to avoid downloading full galleries.
 */
object ImageQualityProbe {
    private const val HEADER_LIMIT = 4L * 1024L * 1024L
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    data class Result(
        val url: String,
        val width: Int? = null,
        val height: Int? = null,
        val message: String = ""
    ) {
        val verified: Boolean get() = width != null && height != null && width > 0 && height > 0
        fun display(): String = if (verified) "${width}×${height} px (проверено по файлу)"
            else "Не удалось проверить: ${message.ifBlank { "размер неизвестен" }}"
    }

    /**
     * Some providers place EXIF/ICC metadata before image dimensions.
     * Limit bytes actually consumed, including single-byte reads.
     */
    private class BoundedStream(source: InputStream, private val limit: Long) : FilterInputStream(source) {
        private var used = 0L
        override fun read(): Int {
            if (used >= limit) throw IOException("Превышен лимит чтения заголовка")
            val byte = super.read()
            if (byte >= 0) used++
            return byte
        }
        override fun read(buffer: ByteArray, off: Int, len: Int): Int {
            if (used >= limit) throw IOException("Превышен лимит чтения заголовка")
            val count = super.read(buffer, off, minOf(len.toLong(), limit - used).toInt())
            if (count > 0) used += count
            return count
        }
        override fun skip(n: Long): Long {
            val skipped = super.skip(minOf(n, limit - used))
            used += skipped
            return skipped
        }
    }

    /** Cancel only quality inspections, never background uploads. */
    fun cancel() = client.dispatcher.cancelAll()

    suspend fun inspect(media: Media): Result = withContext(Dispatchers.IO) {
        coroutineContext.ensureActive()
        try {
            val request = Request.Builder()
                .url(media.url)
                .header("User-Agent", "GalleryBridge/0.1")
                .build()
            require(request.url.scheme == "https") { "Требуется HTTPS" }
            client.newCall(request).execute().use { response ->
                coroutineContext.ensureActive()
                require(response.isSuccessful) { "HTTP ${response.code}" }
                require(response.request.url.scheme == "https") { "Перенаправление на HTTP" }
                val mime = response.header("Content-Type").orEmpty().lowercase()
                require(!mime.startsWith("text/") && !mime.contains("html")) {
                    "Ответ сайта не является изображением"
                }
                require(mime.isBlank() || mime.startsWith("image/") ||
                        mime.startsWith("application/octet-stream")) {
                    "Неподдерживаемый тип: $mime"
                }
                val body = response.body ?: error("Пустой ответ")
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BoundedStream(body.byteStream(), HEADER_LIMIT).use { bounded ->
                    BitmapFactory.decodeStream(bounded, null, options)
                }
                if (options.outWidth > 0 && options.outHeight > 0) {
                    Result(media.url, options.outWidth, options.outHeight)
                } else Result(media.url, message = "Android не распознал размеры изображения")
            }
        } catch (e: Exception) {
            Result(media.url, message = e.message?.take(140) ?: "ошибка запроса")
        }
    }
}
