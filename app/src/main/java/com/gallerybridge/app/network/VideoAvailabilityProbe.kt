package com.gallerybridge.app.network

import com.gallerybridge.app.core.Kind
import com.gallerybridge.app.core.Media
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

/** Bounded optional GET header check: never downloads the video body or bypasses access controls. */
object VideoAvailabilityProbe {
    const val MAX_SELECTED = 12
    enum class Verdict { ACCEPTED, BLOCKED, UNCERTAIN }
    data class Result(val url: String, val verdict: Verdict, val message: String) {
        val blocked: Boolean get() = verdict == Verdict.BLOCKED
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .build()

    fun classify(url: String, code: Int, contentType: String?): Result {
        val mime = contentType.orEmpty().substringBefore(';').trim().lowercase()
        return when {
            code in setOf(401, 403, 404, 410) -> Result(url, Verdict.BLOCKED,
                "Источник HTTP $code: видео недоступно")
            code in 200..299 && (mime.startsWith("text/") || mime.contains("html")) ->
                Result(url, Verdict.BLOCKED, "Источник вернул страницу вместо видео")
            code in 200..299 && mime.isNotEmpty() &&
                !mime.startsWith("video/") && mime !in setOf("application/octet-stream", "binary/octet-stream") ->
                Result(url, Verdict.UNCERTAIN, "HTTP $code: тип $mime, видео не подтверждено")
            code in 200..299 -> Result(url, Verdict.ACCEPTED,
                "HTTP $code: сервер ответил, весь файл не проверен")
            code == 429 -> Result(url, Verdict.UNCERTAIN, "HTTP 429: ограничение частоты запросов")
            else -> Result(url, Verdict.UNCERTAIN, "HTTP $code: доступ не подтверждён")
        }
    }

    fun candidates(items: List<Media>, selection: Set<String>): List<Media> =
        items.asSequence().filter { it.kind == Kind.VIDEO && it.url.startsWith("https://") && it.url in selection }
            .distinctBy { it.url }.take(MAX_SELECTED).toList()

    /** Count each discovered video URL exactly once, regardless of the selected quality. */
    data class Summary(
        val total: Int,
        val checked: Int,
        val blocked: Int,
        val accepted: Int,
        val uncertain: Int
    ) {
        val allBlocked: Boolean get() = total > 0 && checked == total && blocked == total
        val notChecked: Int get() = total - checked
    }

    fun summary(items: List<Media>, results: Map<String, Result>): Summary {
        val urls = items.asSequence().filter { it.kind == Kind.VIDEO && it.url.startsWith("https://") }
            .map { it.url }.distinct().toList()
        val observed = urls.mapNotNull { results[it] }
        return Summary(
            total = urls.size,
            checked = observed.size,
            blocked = observed.count { it.verdict == Verdict.BLOCKED },
            accepted = observed.count { it.verdict == Verdict.ACCEPTED },
            uncertain = observed.count { it.verdict == Verdict.UNCERTAIN }
        )
    }

    fun allCandidates(items: List<Media>): List<Media> =
        items.asSequence().filter { it.kind == Kind.VIDEO && it.url.startsWith("https://") }
            .distinctBy { it.url }.take(MAX_SELECTED).toList()

    suspend fun inspect(media: Media): Result = withContext(Dispatchers.IO) {
        coroutineContext.ensureActive()
        try {
            val url = media.url.toHttpUrlOrNull()
                ?: return@withContext Result(media.url, Verdict.BLOCKED, "Неверная ссылка")
            if (url.scheme != "https") return@withContext Result(media.url, Verdict.BLOCKED, "Нужен HTTPS")
            // Do not request a byte range: some CDNs handle Range differently.
            val request = Request.Builder().url(url)
                .header("User-Agent", "GalleryBridge/0.1").get().build()
            client.newCall(request).execute().use { response ->
                coroutineContext.ensureActive()
                if (response.request.url.scheme != "https") {
                    Result(media.url, Verdict.BLOCKED, "Переадресация на HTTP")
                } else {
                    classify(media.url, response.code, response.header("Content-Type"))
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result(media.url, Verdict.UNCERTAIN, "Не удалось проверить: ${e.message?.take(100) ?: "сетевая ошибка"}")
        }
    }
    fun cancel() = client.dispatcher.cancelAll()
}
