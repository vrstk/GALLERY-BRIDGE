package com.gallerybridge.app.network

import android.content.Context
import android.net.Uri
import android.media.MediaMetadataRetriever
import com.gallerybridge.app.core.*
import com.gallerybridge.app.transfer.QueueStore
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
import org.jsoup.Jsoup
import java.io.File
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.UUID
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

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

    private fun mailLookup(url: HttpUrl, auth: String, directory: Boolean): WebDavResourcePolicy.Lookup =
        mailCall(Request.Builder().url(url).header("Authorization", auth)
            .header("Depth", "0").method("PROPFIND", null).build()).use { response ->
            WebDavResourcePolicy.inspect(
                response.code, response.body?.string(), url.toString(), expectDirectory = directory
            )
        }
    private val ext = Regex("\\.(jpe?g|png|webp|gif|avif|bmp|mp4|m4v|mov|webm)$", RegexOption.IGNORE_CASE)
    private fun kind(path: String) = if (Regex("\\.(mp4|m4v|mov|webm)$", RegexOption.IGNORE_CASE).containsMatchIn(path)) Kind.VIDEO else Kind.PHOTO
    private fun request(url: String) = Request.Builder().url(url).header("User-Agent", "GalleryBridge/0.1").build()

    suspend fun scan(raw: String): Scan = scanPageSource(raw, photoOnly = false)

    suspend fun scanPhotoPublication(raw: String): Scan = scanPageSource(raw, photoOnly = true)

    suspend fun scanVideoPublication(raw: String, watchUrl: String): Scan =
        scanPageSource(raw, photoOnly = false, catalogWatchUrl = watchUrl)

    private suspend fun scanPageSource(raw: String, photoOnly: Boolean, catalogWatchUrl: String = ""): Scan = withContext(Dispatchers.IO) {
        val url = raw.trim().toHttpUrlOrNull() ?: error("Неверный URL")
        require(url.scheme == "https") { "Нужна HTTPS-ссылка" }
        if (url.host in setOf("elitebabes.com", "www.elitebabes.com") &&
            url.encodedPath.startsWith("/model/")
        ) {
            error("Это каталог модели, а не отдельная фотогалерея. Откройте раздел «Публикации модели» через поиск.")
        }
        if (ext.containsMatchIn(url.encodedPath)) return@withContext Scan("Прямая ссылка", "", listOf(Media(url.toString(), Paths.clean(url.pathSegments.last()), kind(url.encodedPath), "Прямой URL")))
        http.newCall(request(url.toString())).execute().use scanPage@ { response ->
            if (!response.isSuccessful) error("Сайт вернул HTTP ${response.code}")
            val type = response.header("Content-Type").orEmpty()
            if (type.startsWith("image/") || type.startsWith("video/"))
                return@scanPage Scan("Прямой файл", "", listOf(Media(response.request.url.toString(), Paths.clean(response.request.url.pathSegments.last()), if (type.startsWith("video/")) Kind.VIDEO else Kind.PHOTO, "Прямой файл")))
            require(type.contains("html") || type.isBlank()) { "Не HTML-страница" }
            val html = response.body?.string().orEmpty().take(3_000_000)
            if (url.host in setOf("elitebabes.com", "www.elitebabes.com")) {
                require(!EliteBabesModelAdapter.isUnavailable(html)) {
                    "Сайт временно недоступен или не вернул страницу галереи. " +
                        "Подтверждённых фотографий нет; повторите проверку позже."
                }
            }
            val doc = Jsoup.parse(html, response.request.url.toString())
            // Single-video pages must use only the main player's sources.
            // Do not attach sidebar recommendations, ad clips or unrelated MP4s.
            val gallery = PhotoGallerySources.extract(doc, response.request.url.toString())
            if (photoOnly) {
                val title = PageMetadata.publicationTitle(doc)
                val metadata = PageMetadata.extract(doc, response.request.url.toString(), title)
                return@scanPage Scan(title, metadata.publishedDate, gallery.orEmpty(),
                    metadata.model, metadata.modelSource, metadata.dateSource,
                    metadata.suggestedDate, metadata.suggestedDateSource,
                    photoDiagnostic = if (gallery.isNullOrEmpty())
                        PhotoGalleryDiagnosis.inspect(doc, response.request.url.toString()).summary()
                    else "")
            }
            val primary = PrimaryVideoSources.extract(doc, response.request.url.toString())
            val linkedVideo = XeroticaBridge.externalPage(doc, response.request.url.toString())
                ?: XeroticaBridge.watchTarget(catalogWatchUrl)
            if (PrimaryVideoSources.isVideoPage(doc, response.request.url.toString()) &&
                (primary.isNotEmpty() || linkedVideo == null)) {
                val videoTitle = doc.selectFirst("meta[property=og:title]")?.attr("content")
                    ?.ifBlank { doc.title() } ?: doc.title()
                val videoMetadata = PageMetadata.extract(doc, response.request.url.toString(), videoTitle)
                return@scanPage Scan(videoTitle.ifBlank { "Без названия" },
                    videoMetadata.publishedDate, primary,
                    videoMetadata.model, videoMetadata.modelSource,
                    videoMetadata.dateSource,
                    posterUrl = PrimaryVideoSources.poster(doc, response.request.url.toString()))
            }
            // Follow only an explicitly linked XErotica watch page inside the
            // selected Elite Babes publication, not an unrelated cover or advert.
            if (linkedVideo != null) {
                val externalClient = http.newBuilder()
                    .followRedirects(false).followSslRedirects(false)
                    .callTimeout(25L, TimeUnit.SECONDS).build()
                externalClient.newCall(request(linkedVideo)).execute().use { external ->
                    require(external.code == 200 &&
                        external.request.url.toString() == linkedVideo) {
                        "Страница видеопубликации недоступна: HTTP ${external.code}."
                    }
                    require(external.header("Content-Type").orEmpty().let {
                        it.isBlank() || it.contains("html", true)
                    }) {
                        "Вместо видеопубликации получен другой тип содержимого."
                    }
                    val htmlExternal = external.body?.string()?.take(2_000_000).orEmpty()
                    require(!EliteBabesModelAdapter.isUnavailable(htmlExternal)) {
                        "Сайт вернул Site Unavailable вместо видеоплеера."
                    }
                    val videoPage = Jsoup.parse(htmlExternal, linkedVideo)
                    val videos = PrimaryVideoSources.extract(videoPage, linkedVideo)
                    require(videos.isNotEmpty()) {
                        "На связанной странице не найден подтверждённый MP4 плеера."
                    }
                    val title = PageMetadata.publicationTitle(doc)
                    val metadata = PageMetadata.extract(doc, response.request.url.toString(), title)
                    return@scanPage Scan(title.ifBlank { "Видеопубликация" },
                        metadata.publishedDate, videos, metadata.model,
                        metadata.modelSource, metadata.dateSource,
                        posterUrl = PrimaryVideoSources.poster(videoPage, linkedVideo))
                }
            }
            // Prefer the real gallery lightbox when the site has a stable
            // gallery structure. Do not mix related sets, ads or site icons.
            if (gallery != null) {
                val title = PageMetadata.publicationTitle(doc)
                val metadata = PageMetadata.extract(doc, response.request.url.toString(), title)
                return@scanPage Scan(
                    title.ifBlank { "Без названия" },
                    metadata.publishedDate,
                    gallery,
                    metadata.model, metadata.modelSource, metadata.dateSource,
                    metadata.suggestedDate, metadata.suggestedDateSource
                )
            }
            // Elite Babes catalog thumbnails (_masonry_400/_600) are not originals.
            // Without a verified lightbox group, refuse to guess image URLs.
            if (url.host in setOf("elitebabes.com", "www.elitebabes.com")) {
                val title = PageMetadata.publicationTitle(doc)
                val metadata = PageMetadata.extract(doc, response.request.url.toString(), title)
                return@scanPage Scan(title.ifBlank { "Без названия" },
                    metadata.publishedDate, emptyList(), metadata.model,
                    metadata.modelSource, metadata.dateSource,
                    photoDiagnostic = PhotoGalleryDiagnosis
                        .inspect(doc, response.request.url.toString()).summary())
            }
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
            val title = PageMetadata.publicationTitle(doc)
            val metadata = PageMetadata.extract(doc, response.request.url.toString(), title)
            Scan(
                title.ifBlank { "Без названия" },
                metadata.publishedDate,
                urls.values.take(300),
                metadata.model,
                metadata.modelSource,
                metadata.dateSource,
                metadata.suggestedDate,
                metadata.suggestedDateSource
            )
        }
    }

    /**
     * Bounded discovery of server-rendered model publication cards.
     * The website's separate infinite-scroll endpoint is not documented by
     * the HTML snapshots: never claim that the first visible 20 are complete.
     * No gallery images or videos are downloaded here.
     */
    /**
     * Bounded HTML discovery with resumable checked pages. HTML checkpoints
     * are metadata only: neither photos nor videos are downloaded.
     */
    suspend fun scanModelIndex(
        raw: String,
        progress: suspend (Int, Int) -> Unit,
        recovered: List<ModelListingSources.Page> = emptyList(),
        onPage: suspend (ModelListingSources.Page) -> Unit = {}
    ): ModelListingSources.Result = withContext(Dispatchers.IO) {
        val expected = ModelListingSources.address(raw)
            ?: error("Нужна HTTPS-ссылка на страницу поддерживаемой модели.")
        val listingClient = http.newBuilder()
            .followRedirects(false).followSslRedirects(false)
            .callTimeout(25L, TimeUnit.SECONDS).build()

        suspend fun readPage(link: String): ModelListingSources.Page {
            coroutineContext.ensureActive()
            require(ModelListingSources.address(link)?.slug == expected.slug) {
                "Ссылка страницы ведёт в другой каталог."
            }
            val response = listingClient.newCall(request(link)).execute()
            return response.use { r ->
                require(r.code == 200) { "Каталог вернул HTTP ${r.code}." }
                require(ModelListingSources.address(r.request.url.toString())?.slug == expected.slug) {
                    "Адрес страницы изменился."
                }
                val mime = r.header("Content-Type").orEmpty().lowercase()
                require(mime.isBlank() || mime.contains("html")) {
                    "Вместо каталога получен другой тип содержимого."
                }
                val stream = r.body?.byteStream() ?: error("Пустой ответ каталога.")
                val data = ByteArrayOutputStream()
                stream.use { input ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = input.read(buffer)
                        if (n < 0) break
                        if (n == 0) continue
                        require(data.size() + n <= 2_000_000) {
                            "Страница каталога превышает безопасный лимит 2 МБ."
                        }
                        data.write(buffer, 0, n)
                    }
                }
                val doc = Jsoup.parse(data.toString(Charsets.UTF_8.name()), link)
                ModelListingSources.parse(doc, link)
                    ?: error("На странице не найдена ожидаемая структура фотопубликаций.")
            }
        }

        val pages = recovered.filter { it.address.slug == expected.slug }
            .distinctBy { it.address.number }.toMutableList()
        val errors = mutableListOf<String>()
        val attempted = pages.mapTo(mutableSetOf()) { it.address.number }
        val found = sortedMapOf<Int, String>()
        if (pages.isEmpty()) {
            val first = readPage(raw)
            pages.add(first)
            attempted.add(first.address.number)
            onPage(first)
        }
        val apiUrl = pages.firstOrNull()?.apiUrl.orEmpty()
        if (apiUrl.isNotBlank()) {
            val first = pages.first()
            val seen = pages.flatMap { it.photos + it.videoCards }.mapTo(mutableSetOf()) {
                if (it.sourceId.isNotBlank()) "elitebabes:" + it.sourceId else GalleryKeys.canonical(it.url) ?: it.url
            }
            var complete = false
            val start = (pages.maxOfOrNull { it.address.number } ?: 1) + 1
            for (number in start..ModelListingSources.MAX_PAGES) {
                coroutineContext.ensureActive()
                if (ModelListingSources.coversDeclaredListing(pages)) break
                val apiPage = EliteBabesModelAdapter.nextUrl(apiUrl, number)
                try {
                    listingClient.newCall(request(apiPage)).execute().use { r ->
                        require(r.code == 200 && r.request.url.toString() == apiPage) {
                            "API вернул HTTP ${r.code} или перенаправление."
                        }
                        val mime = r.header("Content-Type").orEmpty().lowercase()
                        require(mime.isBlank() || mime.contains("html") ||
                            mime.contains("json") || mime.contains("text/plain")) {
                            "Неподдерживаемый тип ответа API."
                        }
                        val payload = ByteArrayOutputStream()
                        val input = r.body?.byteStream() ?: error("Пустое тело API.")
                        input.use { stream ->
                            val block = ByteArray(8192)
                            while (true) {
                                coroutineContext.ensureActive()
                                val n = stream.read(block)
                                if (n < 0) break
                                if (n == 0) continue
                                require(payload.size() + n <= 2_000_000) {
                                    "Порция API превышает 2 МБ."
                                }
                                payload.write(block, 0, n)
                            }
                        }
                        val body = payload.toString(Charsets.UTF_8.name())
                        val batch = EliteBabesModelAdapter.parseApiResponse(body, apiPage, first.model)
                        if (batch.end) {
                            complete = true
                        } else {
                            var fresh = 0
                            batch.cards.forEach {
                                val key = if (it.sourceId.isNotBlank()) "elitebabes:" + it.sourceId
                                    else GalleryKeys.canonical(it.url) ?: it.url
                                if (seen.add(key)) fresh++
                            }
                            require(fresh > 0) {
                                "API повторил карточки на странице №$number; конец не подтверждён."
                            }
                            val page = ModelListingSources.Page(
                                ModelListingSources.Address(expected.slug, number), first.model,
                                batch.cards.filter { it.type == "photo" },
                                batch.cards.count { it.type == "video" }, batch.cards.size,
                                mapOf(number to (raw.trimEnd('/') + "/mpage/$number")),
                                true, first.advertisedCount, apiUrl,
                                batch.cards.filter { it.type == "video" })
                            pages.add(page)
                            onPage(page)
                        }
                    }
                    progress(pages.size, ModelListingSources.MAX_PAGES)
                    if (complete) break
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    errors.add("Ошибка API на порции №$number: ${e.message ?: "нет ответа"}.")
                    break
                }
            }
            return@withContext ModelListingSources.combine(
                pages, errors, capped = !complete && !ModelListingSources.coversDeclaredListing(pages),
                apiConfirmedComplete = complete)
        }
        pages.forEach { page ->
            page.pages.forEach { (number, link) ->
                if (number !in attempted && ModelListingSources.address(link)?.slug == expected.slug)
                    found.putIfAbsent(number, link)
            }
        }
        var capped = false
        var fallbackActive = false
        progress(pages.size, pages.size + found.size)
        while (true) {
            coroutineContext.ensureActive()
            if (pages.size >= ModelListingSources.MAX_PAGES) {
                // Reaching the hard limit never proves the server has no more pages.
                capped = true
                break
            }
            if (found.isEmpty()) {
                val next = ModelListingSources.nextNumberedPage(pages, attempted)
                    ?: break
                // Only follow an HTML page-number pattern demonstrated by the server.
                found[next.first] = next.second
                fallbackActive = true
            }
            val (number, link) = found.entries.first().let { it.key to it.value }
            found.remove(number)
            if (!attempted.add(number)) continue
            try {
                val page = readPage(link)
                if (page.address.slug != expected.slug || page.address.number != number) {
                    error("Номер страницы не совпадает с ожидаемым.")
                }
                // Sites sometimes return the last page again for out-of-range numbers.
                if (page.cardCount == 0 ||
                    pages.any { it.cardCount > 0 && it.photos.map { p -> p.url } ==
                        page.photos.map { p -> p.url } && it.videos == page.videos }
                ) {
                    if (page.cardCount == 0 && page.photos.isEmpty()) break
                    errors.add("Сайт повторил уже прочитанную страницу №$number.")
                    break
                }
                pages.add(page)
                onPage(page)
                page.pages.forEach { (num, next) ->
                    if (num !in attempted && num !in found &&
                        ModelListingSources.address(next)?.slug == expected.slug)
                        found[num] = next
                }
                // A strictly increasing numbered scan is deliberately bounded.
                if (fallbackActive && pages.sumOf { it.cardCount } >=
                    (pages.mapNotNull { it.advertisedCount }.maxOrNull() ?: Int.MAX_VALUE)
                ) break
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errors.add("Не удалось прочитать страницу №$number: ${e.message ?: "сеть недоступна"}.")
                // Do not infer farther page numbers after a failed request.
                if (fallbackActive) break
            }
            progress(pages.size, (pages.size + found.size).coerceAtMost(ModelListingSources.MAX_PAGES))
        }
        ModelListingSources.combine(pages, errors, capped)
    }

    private fun freshPrimaryVideoSource(media: Media): Media {
        if (!PrimaryVideoSources.supportsTransfer(media)) return media
        val page = media.referrer
        val client = http.newBuilder().followRedirects(false).followSslRedirects(false)
            .callTimeout(25L, TimeUnit.SECONDS).build()
        client.newCall(request(page)).execute().use { response ->
            require(response.code == 200 && response.request.url.toString() == page) {
                "Страница источника видео недоступна для обновления ссылки."
            }
            require(response.header("Content-Type").orEmpty().let {
                it.isBlank() || it.contains("html", true)
            }) {
                "Вместо страницы плеера получен другой файл."
            }
            val html = response.body?.string()?.take(2_000_000).orEmpty()
            require(!EliteBabesModelAdapter.isUnavailable(html)) {
                "Страница видеоплеера недоступна."
            }
            val doc = Jsoup.parse(html, page)
            val candidates = PrimaryVideoSources.extract(doc, page)
            val old = media.url.toHttpUrlOrNull() ?: error("Некорректная ссылка MP4.")
            // Same original file/quality only; no guessing from a CDN directory.
            return candidates.singleOrNull {
                val now = it.url.toHttpUrlOrNull()
                now?.host == old.host && now.encodedPath == old.encodedPath
            } ?: error("Та же версия MP4 больше не доступна. Откройте публикацию заново.")
        }
    }

    private fun download(context: Context, media: Media): Pair<File, String> {
        val file = File(context.cacheDir, "gb_${UUID.randomUUID()}.part")
        try {
            if (media.url.startsWith("content://")) {
                require(media.source == LocalMediaImport.SOURCE) { "Локальный URI не был импортирован" }
                val uri = Uri.parse(media.url)
                val mime = context.contentResolver.getType(uri)?.substringBefore(';')
                    ?.takeIf { it.startsWith("video/") || it.startsWith("image/") }
                    ?: if (media.kind == Kind.VIDEO) "video/mp4" else "image/jpeg"
                context.contentResolver.openInputStream(uri)?.use { input ->
                    file.outputStream().use { output -> input.copyTo(output) }
                } ?: error("Нет доступа к выбранному локальному файлу. Импортируйте его заново.")
                require(file.length() > 0L) { "Локальный файл пустой" }
                return file to mime
            }
            // Re-read the selected public player before each video attempt.
            // Signed CDN query parameters are never reconstructed or truncated.
            val sourceMedia = freshPrimaryVideoSource(media)
            val sourceRequest = BrowserSessionHeaders.withContext(
                request(sourceMedia.url).newBuilder(), sourceMedia, context
            ).build()
            http.newCall(sourceRequest).execute().use { r ->
                if (!r.isSuccessful) throw SourceDownloadFailure.HttpError(r.code)
                val mime = r.header("Content-Type")?.substringBefore(';').orEmpty()
                if (mime.startsWith("text/") || mime.contains("html")) error("Вместо файла получен текст")
                val body = r.body ?: error("Пустой файл")
                if (media.kind == Kind.VIDEO) {
                    VideoDownloadIntegrity.requireCompleteResponse(
                        r.code, mime, r.header("Content-Range"), body.contentLength())
                }
                val expected = body.contentLength()
                if (expected > 0 && context.cacheDir.usableSpace < expected + 20_000_000) error("Недостаточно памяти")
                body.byteStream().use { input -> file.outputStream().use { output -> input.copyTo(output) } }
                if (file.length() == 0L || expected >= 0 && file.length() != expected) error("Неполный файл")
                if (media.kind == Kind.PHOTO) {
                    PhotoSourceValidation.verifyFile(file, mime)
                } else if (media.kind == Kind.VIDEO) {
                    val metadata = MediaMetadataRetriever()
                    try {
                        metadata.setDataSource(file.absolutePath)
                        val hasVideo = metadata.extractMetadata(
                            MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO)
                        val duration = metadata.extractMetadata(
                            MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                        require(hasVideo == "yes" && duration > 0) {
                            "Получен файл без воспроизводимой видеодорожки."
                        }
                    } finally {
                        metadata.release()
                    }
                }
                return file to (mime.ifBlank { "application/octet-stream" })
            }
        } catch (e: Exception) { file.delete(); throw e }
    }

    private fun webdavFolders(email: String, password: String, path: List<String>) {
        var current = "https://webdav.cloud.mail.ru/"
        val auth = Credentials.basic(email, password)
        for (segment in path) {
            current = current.toHttpUrl().newBuilder().addPathSegment(Paths.clean(segment)).build().toString()
            val exists = mailLookup(current.toHttpUrl(), auth, directory = true)
            if (exists is WebDavResourcePolicy.Lookup.Missing) {
                val mkcol = Request.Builder().url(current).header("Authorization", auth).method("MKCOL", null).build()
                mailCall(mkcol).use { r -> if (r.code !in listOf(200, 201, 204, 405)) error("WebDAV папка HTTP ${r.code}") }
            }
        }
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
        progress: suspend (String) -> Unit,
        onItemResult: suspend (Int, Media, String, String, String) -> Unit = { _, _, _, _, _ -> },
        foldersPerFile: List<List<String>>? = null,
        onBeforeDestinationWrite: suspend (Int, Media) -> Unit
    ): String = withContext(Dispatchers.IO) {
        var uploaded = 0
        var skipped = 0
        var failed = 0
        var attempted = 0
        var consecutiveNetworkFailures = 0
        var stoppedReason: String? = null
        val failures = mutableListOf<String>()
        val readyMailFolders = mutableSetOf<List<String>>()
        val documentFoldersByKind = mutableMapOf<List<String>, Uri>()
        val mailAuth = if (target == "Mail WebDAV") Credentials.basic(username, password) else ""
        require(TransferTargets.isSupported(target)) { "Устаревший или неизвестный способ передачи" }
        // A granted tree URI can be revoked while the app is in the background.
        // Fail before downloading any source bytes or creating provider folders.
        if (target == TransferTargets.FOLDER) {
            require(SafFolderAccess.inspect(context, root).ready) {
                "Доступ к папке назначения утрачен. Выберите исходную папку снова."
            }
        }

        require(foldersPerFile == null || foldersPerFile.size == media.size)
        for ((index, item) in media.withIndex()) {
            coroutineContext.ensureActive()
            var destinationWriteStarted = false
            var existingNeedsVerification = false
            var readingSource = false
            fun readSource(): Pair<File, String> {
                readingSource = true
                return download(context, item).also { readingSource = false }
            }
            attempted++
            progress("${index + 1}/${media.size}: ${item.name}")
            try {
                val folders = foldersPerFile?.get(index)
                    ?: Paths.folders(model, date, title, item.kind)
                if (target == "Mail WebDAV") {
                    val cloudPath = Paths.splitRoot(root) + folders
                    // После успеха не проверяем одни и те же папки перед каждым файлом.
                    if (folders !in readyMailFolders) {
                        webdavFolders(username, password, cloudPath)
                        readyMailFolders += folders
                    }
                    val destination = mailUrl(cloudPath + Paths.clean(item.name))
                    // Пропускаем ранее сохранённый файл до повторного скачивания.
                    val existingMail = mailLookup(destination, mailAuth, directory = false)
                    if (existingMail is WebDavResourcePolicy.Lookup.Existing) {
                        // Presence alone is not evidence that the remote bytes match.
                        // Never overwrite an existing file on a mismatch.
                        existingNeedsVerification = true
                        val (local, _) = readSource()
                        try {
                            mailCall(Request.Builder().url(destination)
                                .header("Authorization", mailAuth).get().build()).use { response ->
                                ExistingFileVerifier.requireSafeWebDavGet(
                                    response.code,
                                    response.request.url.scheme,
                                    response.request.url.host,
                                    response.header("Content-Type").orEmpty()
                                )
                                val body = response.body ?: throw ExistingFileVerifier.Unverified(
                                    "Mail WebDAV вернул пустое содержимое фотографии."
                                )
                                val digest = ExistingFileVerifier.verify(
                                    local, body.byteStream(), existingMail.bytes
                                )
                                coroutineContext.ensureActive()
                                skipped++
                                onItemResult(index, item, "SKIPPED", "", digest.sha256)
                            }
                            consecutiveNetworkFailures = 0
                        } finally {
                            local.delete()
                        }
                        continue
                    }
                    val (local, mime) = readSource()
                    try {
                        val sourceDigest = PhotoContentDigest.sha256(local)
                        coroutineContext.ensureActive()
                        onBeforeDestinationWrite(index, item)
                        destinationWriteStarted = true
                        coroutineContext.ensureActive()
                        mailCall(Request.Builder().url(destination)
                            .header("Authorization", mailAuth)
                            .put(local.asRequestBody(mime.toMediaType())).build()).use { response ->
                            if (response.code !in listOf(200, 201, 204)) {
                                error("Mail WebDAV PUT: HTTP ${response.code}")
                            }
                        }
                        coroutineContext.ensureActive()
                        val remote = mailLookup(destination, mailAuth, directory = false)
                        if (remote !is WebDavResourcePolicy.Lookup.Existing ||
                            remote.bytes != local.length()
                        ) throw WebDavResourcePolicy.UnsafeResource(
                            "После PUT размер файла в Mail WebDAV не подтверждён: " +
                                "локально ${local.length()} байт, в облаке " +
                                (remote as? WebDavResourcePolicy.Lookup.Existing)?.bytes
                        )
                        coroutineContext.ensureActive()
                        uploaded++
                        onItemResult(index, item, "DONE", "", sourceDigest)
                        consecutiveNetworkFailures = 0
                    } finally {
                        local.delete()
                    }
                } else if (target == "Папка Android") {
                    val destFolder = documentFoldersByKind.getOrPut(folders) {
                        SafDestination.resolveFolder(context, root, folders)
                    }
                    // Check before source download; previously every resume fetched
                    // already existing files again, wasting time and traffic.
                    val existingUri = SafDestination.fileForRead(context, destFolder, item.name)
                    if (existingUri != null) {
                        existingNeedsVerification = true
                        val (local, _) = readSource()
                        try {
                            val existing = context.contentResolver.openInputStream(existingUri)
                                ?: throw ExistingFileVerifier.Unverified(
                                    "Провайдер Android не разрешил чтение существующего файла."
                                )
                            val digest = ExistingFileVerifier.verify(local, existing)
                            coroutineContext.ensureActive()
                            skipped++
                            onItemResult(index, item, "SKIPPED", "", digest.sha256)
                        } finally {
                            local.delete()
                        }
                    } else {
                        val (local, mime) = readSource()
                        try {
                            val sourceDigest = PhotoContentDigest.sha256(local)
                            coroutineContext.ensureActive()
                            onBeforeDestinationWrite(index, item)
                            destinationWriteStarted = true
                            coroutineContext.ensureActive()
                            if (SafDestination.uploadFile(context, destFolder, item.name, local, mime)) {
                                coroutineContext.ensureActive()
                                uploaded++
                                onItemResult(index, item, "DONE", "", sourceDigest)
                            } else {
                                // The file appeared after preflight; never equate presence with identical bytes.
                                throw ExistingFileVerifier.Unverified(
                                    "Файл появился во время передачи. Проверьте его SHA-256 вручную."
                                )
                            }
                        } finally {
                            local.delete()
                        }
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException || e is QueueStore.CheckpointFailure) throw e
                coroutineContext.ensureActive()
                val folderConflict = target == "Папка Android" && listOf(
                    "дубликат", "одноимённые", "Создание заблокировано", "Новые папки не создавались"
                ).any { e.message.orEmpty().contains(it, ignoreCase = true) }
                val unsafe = destinationWriteStarted || existingNeedsVerification ||
                    e is ExistingFileVerifier.Unverified ||
                    e is SafWritePolicy.UncertainWrite ||
                    e is WebDavResourcePolicy.UnsafeResource || folderConflict
                // Only generic reasons are persisted, never raw URLs or credentials.
                val reason = when {
                    e is SafWritePolicy.UncertainWrite ->
                        "Провайдер Android не подтвердил запись; проверьте файл и размер в папке."
                    e is WebDavResourcePolicy.UnsafeResource ->
                        "WebDAV не подтвердил состояние файла; проверьте облако."
                    folderConflict -> "Неоднозначное состояние папки; возможны одноимённые файлы."
                    e is ExistingFileVerifier.Unverified ->
                        "Существующий файл не прошёл проверку SHA-256 или недоступен для чтения."
                    existingNeedsVerification ->
                        "Не удалось проверить байты существующего файла; ручная проверка обязательна."
                    destinationWriteStarted ->
                        "Результат начатой передачи не подтверждён; проверьте файл в назначении."
                    e is SourceDownloadFailure.HttpError ->
                        "Источник вернул HTTP " + e.statusCode + "; проверьте доступность файла."
                    isTemporaryNetworkFailure(e) ->
                        if (readingSource) "Прервано соединение с источником при чтении файла." else "Нет связи с назначением ($target)."
                    else -> "Ошибка до записи в назначение: " + e.javaClass.simpleName
                }
                failed++
                onItemResult(index, item, if (unsafe) "BLOCKED" else "FAILED", reason, "")
                if (e is SourceDownloadFailure.HttpError &&
                    SourceDownloadFailure.shouldPauseQueue(e.statusCode)
                ) {
                    stoppedReason = "Источник временно ограничил доступ или перегружен (HTTP ${e.statusCode}). " +
                        "Остальная очередь сохранена. Проверьте доступность источника и продолжите позже."
                    if (failures.size < 20) failures.add(item.name + ": " + e.message)
                    break
                }
                if (e is SafWritePolicy.UncertainWrite) {
                    stoppedReason = "Провайдер папки Android не подтвердил целостность записи. " +
                        "Проверьте файл и состояние папки перед продолжением очереди; дубли не создаются."
                    if (failures.size < 20) failures.add(item.name + ": " + e.message)
                    break
                }
                if (e is WebDavResourcePolicy.UnsafeResource) {
                    stoppedReason = "Проверка Mail WebDAV выявила неопределённое или повреждённое состояние. " +
                        "Очередь остановлена без перезаписи существующих файлов."
                    if (failures.size < 20) failures.add(item.name + ": " + e.message)
                    break
                }
                if (folderConflict) {
                    stoppedReason = "Обнаружена ошибка структуры папок или провайдера. " +
                        "Передача остановлена, чтобы не создавать новые дубли."
                    if (failures.size < 20) failures.add(item.name + ": " + e.message)
                    break
                }
                if (unsafe) {
                    stoppedReason = "После начала записи результат не подтверждён. " +
                        "Автоматический повтор файла заблокирован до ручной проверки."
                    if (failures.size < 20) failures.add(item.name + ": " + reason)
                    break
                }
                if (failures.size < 20) {
                    failures.add(item.name + ": " + reason)
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
            append("\nСуществующие фотографии пропускаются только после SHA-256 сравнения с источником; это не гарантия синхронизации облака.")
        }
    }
}
