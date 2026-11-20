package com.gallerybridge.app.core

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.LocalDate

/**
 * Adapter for an Elite Babes MODEL index. Model nr comes only from the page's
 * own apiUrl; preview images are never treated as full-resolution originals.
 */
internal object EliteBabesModelAdapter {
    private const val SOURCE = "elitebabes"
    private val apiPattern = Regex(
        """(?:(?:https?:)?//(?:www\.)?elitebabes\.com)?/gridapi/\?content=model(?:&|&amp;)nr=[0-9]{1,10}(?:&|&amp;)sort=[a-z][a-z0-9-]{0,30}""",
        RegexOption.IGNORE_CASE
    )
    private val unavailable = Regex(
        """(?i)\b(site unavailable|access denied|temporarily unavailable|service unavailable)\b"""
    )
    data class Diagnostic(
        val model: String, val profileUrl: String, val apiUrl: String,
        val cards: List<ModelListingSources.Publication>, val advertised: Int?
    ) {
        fun catalogEntries(now: Long): List<CatalogGallery> = cards.map { card ->
            CatalogGallery(card.url, card.model, card.title, card.displayedDate, now,
                source = SOURCE, sourceId = card.sourceId, originalCaption = card.caption,
                studio = card.studio, displayedDate = card.displayedDate,
                type = card.type, previewUrl = card.previewUrl)
        }
        fun listing(): ModelListingSources.Result {
            val photos = cards.filter { it.type == "photo" }
            val videos = cards.filter { it.type == "video" }
            return ModelListingSources.Result(photos, videos.size, cards.size, 1,
                advertised, advertised != null && advertised != cards.size,
                emptyList(), videoCards = videos)
        }
    }

    fun validatedApiUrl(raw: String): String? {
        val url = raw.replace("&amp;", "&").toHttpUrlOrNull() ?: return null
        if (url.scheme != "https" || url.port != 443 ||
            url.host !in setOf("elitebabes.com", "www.elitebabes.com") ||
            url.encodedPath != "/gridapi/" || url.fragment != null ||
            url.queryParameterNames != setOf("content", "nr", "sort") ||
            url.queryParameter("content") != "model" ||
            url.queryParameter("nr")?.matches(Regex("[0-9]{1,10}")) != true ||
            url.queryParameter("sort")?.matches(Regex("[a-z][a-z0-9-]{0,30}")) != true) return null
        return url.toString()
    }

    fun nextUrl(apiUrl: String, page: Int): String {
        val base = validatedApiUrl(apiUrl) ?: error("Недопустимый API модели.")
        require(page in 2..ModelListingSources.MAX_PAGES)
        return base.toHttpUrlOrNull()!!.newBuilder()
            .setQueryParameter("mpage", page.toString()).build().toString()
    }

    fun isUnavailable(raw: String): Boolean =
        raw.isBlank() || unavailable.containsMatchIn(raw.take(8192))

    fun splitCaption(caption: String): Pair<String, String> {
        val value = caption.trim()
        if (value.startsWith("at ", ignoreCase = true)) return "" to value.drop(3).trim()
        val at = value.lastIndexOf(" at ", ignoreCase = true)
        return if (at < 0) value to ""
            else value.substring(0, at).trim() to value.substring(at + 4).trim()
    }

    private fun preview(figure: Element): String {
        val image = figure.selectFirst("img") ?: return ""
        for (raw in listOf(image.absUrl("data-src"), image.absUrl("src"),
            image.absUrl("data-original"))) {
            val url = raw.toHttpUrlOrNull() ?: continue
            if (url.scheme == "https" && url.port == 443 &&
                (url.host == "elitebabes.com" || url.host.endsWith(".elitebabes.com"))) {
                return url.toString()
            }
        }
        return ""
    }

    private fun identity(card: ModelListingSources.Publication): String =
        if (card.sourceId.isNotBlank()) "elitebabes:" + card.sourceId
        else GalleryKeys.canonical(card.url) ?: card.url

    fun parseCards(document: Document, model: String): List<ModelListingSources.Publication> {
        val cards = linkedMapOf<String, ModelListingSources.Publication>()
        for (figure in document.select(".list-gallery:not(.static) > li figure")
            .take(ModelListingSources.MAX_CARDS_PER_PAGE)) {
            val anchor = figure.selectFirst(".img-overlay > p > a[href]") ?: continue
            // The cover anchor of a video may leave Elite Babes. Use caption link.
            val url = ModelListingSources.publicationUrl(anchor.absUrl("href")) ?: continue
            val caption = anchor.text().trim().take(250)
            val (title, studio) = splitCaption(caption)
            val dateText = figure.selectFirst(".img-label > span")?.text()?.trim().orEmpty()
            val date = runCatching { LocalDate.parse(dateText).toString() }.getOrDefault("")
            val sourceId = sequenceOf(figure.attr("data-postid"),
                figure.parent()?.attr("data-postid").orEmpty(),
                figure.selectFirst(".img-overlay .simplefavorite-button[data-postid]")
                    ?.attr("data-postid").orEmpty())
                .firstOrNull { it.matches(Regex("[0-9]{1,12}")) }.orEmpty()
            val video = figure.selectFirst("i.icon-play, .icon-play, video, [data-av-bridged]") != null ||
                figure.parent()?.classNames()?.any { it.contains("video", ignoreCase = true) } == true
            val card = ModelListingSources.Publication(
                url, title, date, model, sourceId, caption, studio, date,
                if (video) "video" else "photo", preview(figure),
                if (video) XeroticaBridge.watchTarget(figure.selectFirst("figure > a[href]")
                    ?.absUrl("href").orEmpty()).orEmpty() else "")
            cards.putIfAbsent(identity(card), card)
        }
        return cards.values.toList()
    }

    fun apiUrl(document: Document): String? {
        val sources = buildList {
            document.select("[data-api-url], [data-apiurl]").forEach {
                add(it.attr("data-api-url").ifBlank { it.attr("data-apiurl") })
            }
            document.select("script").forEach { add(it.data().ifBlank { it.html() }) }
        }
        for (source in sources) {
            for (match in apiPattern.findAll(source.replace("\\/", "/"))) {
                val raw = match.value.replace("&amp;", "&")
                val absolute = if (raw.startsWith("/")) "https://www.elitebabes.com" + raw else raw
                validatedApiUrl(absolute)?.let { return it }
            }
        }
        return null
    }

    fun parseFirst(doc: Document, url: String): ModelListingSources.Page? {
        val address = ModelListingSources.address(url) ?: return null
        if (address.number != 1 || isUnavailable(doc.text().take(300)) ||
            doc.selectFirst(".list-gallery:not(.static)") == null) return null
        val api = apiUrl(doc) ?: return null
        val model = (doc.selectFirst("main#content article.module-model h1")
            ?: doc.selectFirst("article.module-model h1"))?.text()?.trim()?.take(130)
            ?.takeIf { it.isNotBlank() } ?: return null
        val cards = parseCards(doc, model)
        if (cards.isEmpty()) return null
        val count = Regex("""\(([0-9]{1,5})\s+Galleries\)""", RegexOption.IGNORE_CASE)
            .find(doc.title())?.groupValues?.get(1)?.toIntOrNull()
        return ModelListingSources.Page(address, model,
            cards.filter { it.type == "photo" }, cards.count { it.type == "video" },
            cards.size, mapOf(1 to url), true, count,
            apiUrl = api, videoCards = cards.filter { it.type == "video" })
    }

    data class ApiBatch(val cards: List<ModelListingSources.Publication>, val end: Boolean)

    /** Only an explicitly empty response may signal end-of-stream. */
    fun parseApiResponse(raw: String, sourceUrl: String, model: String): ApiBatch {
        require(raw.isNotBlank() && raw.length <= 2_000_000 && !isUnavailable(raw)) {
            "API вернул пустой ответ или заглушку Site Unavailable."
        }
        val trimmed = raw.trim()
        fun htmlFromItems(items: JSONArray): String {
            return (0 until items.length()).joinToString("") { index ->
                val item = items.get(index)
                val fragment = when (item) {
                    is String -> item
                    is JSONObject -> sequenceOf(item.opt("html"), item.opt("content"))
                        .filterIsInstance<String>().firstOrNull { it.isNotBlank() }.orEmpty()
                    else -> ""
                }
                require(fragment.isNotBlank()) { "Неизвестная структура элемента API модели." }
                fragment
            }
        }
        val html = when {
            trimmed.startsWith("[") -> htmlFromItems(JSONArray(trimmed))
            trimmed.startsWith("{") -> {
                val json = JSONObject(trimmed)
                val field = listOf("html", "content", "data", "items", "posts")
                    .firstOrNull(json::has) ?: error("Неизвестный формат API модели.")
                when (val value = json.opt(field)) {
                    is String -> value
                    is JSONArray -> htmlFromItems(value)
                    else -> error("Неизвестная структура карточек API модели.")
                }
            }
            else -> raw
        }
        if (html.isBlank()) return ApiBatch(emptyList(), true)
        require(!isUnavailable(html)) { "Сайт вернул Site Unavailable вместо карточек." }
        // gridapi may return standalone <li> fragments without the surrounding UL.
        val markup = if (html.trimStart().startsWith("<li", ignoreCase = true))
            "<ul class=\"list-gallery\">" + html + "</ul>"
        else html
        val document = Jsoup.parse(markup, sourceUrl)
        val cards = parseCards(document, model)
        if (cards.isNotEmpty()) return ApiBatch(cards, false)
        if (document.selectFirst(".list-gallery:not(.static)") != null &&
            document.select(".list-gallery:not(.static) > li").isEmpty())
            return ApiBatch(emptyList(), true)
        error("Не удалось проверить окончание списка: API не вернул карточки.")
    }

    fun parseDiagnostic(raw: String): Diagnostic {
        require(raw.toByteArray(Charsets.UTF_8).size in 1..2 * 1024 * 1024)
        val doc = JSONObject(raw)
        require(doc.optString("report") == "GB EliteBabes diagnostics") {
            "Это не диагностика Elite Babes из Soul."
        }
        val profileUrl = doc.getString("pageUrl")
        require(ModelListingSources.address(profileUrl)?.number == 1) {
            "Неверный адрес профиля."
        }
        val model = doc.getJSONObject("profile").getString("name").trim().take(130)
        require(model.isNotBlank())
        val urls = doc.optJSONArray("inlineApiUrls") ?: JSONArray()
        val api = (0 until urls.length()).firstNotNullOfOrNull {
            validatedApiUrl(urls.optString(it))
        }.orEmpty()
        val galleries = doc.getJSONArray("galleries")
        require(galleries.length() in 1..ModelListingSources.MAX_CARDS_PER_PAGE)
        val cards = linkedMapOf<String, ModelListingSources.Publication>()
        for (i in 0 until galleries.length()) {
            val item = galleries.getJSONObject(i)
            val url = ModelListingSources.publicationUrl(item.getString("url"))
                ?: error("Недопустимый URL карточки №${i + 1}.")
            val type = item.getString("type")
            require(type == "photo" || type == "video")
            val id = item.optString("sourceId").take(20)
            require(id.isBlank() || id.matches(Regex("[0-9]{1,12}")))
            val caption = item.optString("caption").trim().take(250)
            val (name, studio) = splitCaption(caption)
            val displayed = item.optString("displayedDate").trim()
            require(GalleryKeys.validDate(displayed))
            val preview = item.optString("previewUrl")
            val parsedPreview = preview.toHttpUrlOrNull()
            require(preview.isBlank() || (parsedPreview?.scheme == "https" &&
                parsedPreview.port == 443 && parsedPreview.host.endsWith(".elitebabes.com")))
            val card = ModelListingSources.Publication(
                url, name, displayed, model, id, caption, studio, displayed, type, preview)
            require(cards.putIfAbsent(identity(card), card) == null) {
                "Повтор sourceId или URL в диагностике."
            }
        }
        require(doc.optInt("loadedCards", -1) == cards.size &&
            doc.optInt("photoCards", -1) == cards.values.count { it.type == "photo" } &&
            doc.optInt("videoCards", -1) == cards.values.count { it.type == "video" }) {
            "Число карточек в диагностике не совпадает."
        }
        return Diagnostic(model, profileUrl, api, cards.values.toList(),
            doc.optInt("titleDeclaredCount").takeIf { it > 0 })
    }
}
