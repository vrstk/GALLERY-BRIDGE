package com.gallerybridge.app.core

import org.jsoup.nodes.Document
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.util.Locale

/**
 * Reads only metadata present in the fetched HTML.
 * Does not infer publication dates from IDs, image names, URLs, or today's date.
 */
object PageMetadata {
    data class Fields(
        val model: String = "",
        val modelSource: String = "",
        val publishedDate: String = "",
        val dateSource: String = "",
        val suggestedDate: String = "",
        val suggestedDateSource: String = ""
    )

    private val modelLead = Regex(
        """^([\p{Lu}][\p{L}.'-]+(?:\s+[\p{Lu}][\p{L}.'-]+){0,3}?)\s+(?:Nude|Naked|Topless|in)\b"""
    )
    private val isoDate = Regex("""(?:19|20)\d{2}-\d{2}-\d{2}""")
    private val monthFormats = listOf(
        "MMMM d, uuuu", "MMM d, uuuu", "d MMMM uuuu", "d MMM uuuu"
    ).map {
        DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern(it)
            .toFormatter(Locale.ENGLISH)
    }

    private fun dateFrom(raw: String?): String? {
        val value = raw?.trim().orEmpty().take(140)
        if (value.isBlank()) return null
        val match = isoDate.find(value)?.value
        if (match != null) {
            val parsed = runCatching {
                LocalDate.of(
                    match.substring(0, 4).toInt(),
                    match.substring(5, 7).toInt(),
                    match.substring(8, 10).toInt()
                )
            }.getOrNull()
            if (parsed != null) return parsed.toString()
        }
        for (format in monthFormats) {
            val parsed = runCatching { LocalDate.parse(value, format) }.getOrNull()
            if (parsed != null) return parsed.toString()
        }
        return null
    }

    private fun isGalleryPageType(raw: Any?): Boolean {
        val values: List<String> = when (raw) {
            is String -> listOf(raw)
            is JSONArray -> (0 until raw.length()).mapNotNull { raw.optString(it).takeIf(String::isNotBlank) }
            else -> emptyList()
        }
        return values.any {
            it.substringAfterLast('/').lowercase(Locale.ROOT) in setOf(
                "article", "blogposting", "newsarticle", "imagegallery",
                "videoobject", "photograph", "mediaobject"
            )
        }
    }

    private fun publishedFromJson(node: Any?, depth: Int = 0): String? {
        if (depth > 8) return null
        return when (node) {
            is JSONArray -> (0 until node.length()).firstNotNullOfOrNull {
                publishedFromJson(node.opt(it), depth + 1)
            }
            is JSONObject -> {
                if (isGalleryPageType(node.opt("@type"))) {
                    dateFrom(node.optString("datePublished")).let { if (it != null) return it }
                }
                // JSON-LD graph nodes may be embedded within arrays / objects.
                publishedFromJson(node.opt("@graph"), depth + 1)
                    ?: publishedFromJson(node.opt("mainEntity"), depth + 1)
                    ?: publishedFromJson(node.opt("itemListElement"), depth + 1)
            }
            else -> null
        }
    }

    private fun published(doc: Document): Pair<String, String> {
        val allowedProperties = setOf("article:published_time", "og:published_time")
        val allowedNames = setOf("pubdate", "publish_date", "datepublished")
        for (tag in doc.select("meta")) {
            val property = tag.attr("property").lowercase(Locale.ROOT)
            val name = tag.attr("name").lowercase(Locale.ROOT)
            val itemprop = tag.attr("itemprop").lowercase(Locale.ROOT)
            if (property !in allowedProperties && name !in allowedNames && itemprop != "datepublished") continue
            val date = dateFrom(tag.attr("content"))
            if (date != null) return date to "Метаданные даты публикации"
        }
        for (tag in doc.select("script[type=application/ld+json]")) {
            val script = tag.data().ifBlank { tag.html() }.take(150_000)
            if (script.isBlank()) continue
            val root: Any = runCatching {
                if (script.trimStart().startsWith("[")) JSONArray(script) else JSONObject(script)
            }.getOrNull() ?: continue
            val date = publishedFromJson(root)
            if (date != null) return date to "Структурированные данные публикации"
        }
        for (tag in doc.select(
            "article time[datetime], main time[datetime], " +
            "article [itemprop=datePublished], main [itemprop=datePublished], " +
            "article .entry-date, article .post-date, main .entry-date"
        )) {
            val date = dateFrom(tag.attr("datetime").ifBlank { tag.attr("content") }.ifBlank { tag.text() })
            if (date != null) return date to "Дата в блоке публикации"
        }
        return "" to ""
    }

    /**
     * A gallery page's visible headline is more meaningful than the SEO title
     * in the browser tab. Do not use a generic site header if none exists.
     */
    fun publicationTitle(doc: Document): String {
        val visible = listOf(
            "header#top h1", "main h1", "article h1", "h1"
        ).firstNotNullOfOrNull { selector ->
            doc.selectFirst(selector)?.text()?.trim()?.takeIf { it.isNotBlank() }
        }
        return (visible ?: doc.selectFirst("meta[property=og:title]")?.attr("content")
            ?.takeIf { it.isNotBlank() } ?: doc.title()).trim().take(200)
    }

    /**
     * A six-digit directory in image URLs may resemble YYMMDD. It is only a
     * *hint*, never publication metadata. Require a consensus among several
     * links from the same actual photo-gallery group; sidebar links excluded.
     */
    private fun galleryDirectoryDateHint(doc: Document, host: String): Pair<String, String> {
        if (host != "elitebabes.com" && !host.endsWith(".elitebabes.com")) return "" to ""
        val galleryAnchors = doc.select("a.fancy[data-fancybox=gallery-01][href]")
        if (galleryAnchors.size < 3) return "" to ""
        val pattern = Regex("""/content/(\\d{6})/""")
        val codes = galleryAnchors.mapNotNull { anchor ->
            pattern.find(anchor.absUrl("href"))?.groupValues?.getOrNull(1)
        }
        if (codes.size < 3 || codes.size != galleryAnchors.size || codes.distinct().size != 1) return "" to ""
        val code = codes.singleOrNull() ?: codes.first()
        val year = 2000 + (code.substring(0, 2).toIntOrNull() ?: return "" to "")
        val month = code.substring(2, 4).toIntOrNull() ?: return "" to ""
        val day = code.substring(4, 6).toIntOrNull() ?: return "" to ""
        val parsed = runCatching { LocalDate.of(year, month, day) }.getOrNull() ?: return "" to ""
        if (parsed.isAfter(LocalDate.now().plusDays(1))) return "" to ""
        return parsed.toString() to "Код каталога фотографий /content/$code/; дата не подтверждена"
    }

    fun extract(doc: Document, pageUrl: String, title: String): Fields {
        val host = runCatching { URI(pageUrl).host.orEmpty().lowercase(Locale.ROOT) }
            .getOrDefault("")
        // A title alone is not enough to identify a model on arbitrary sites.
        val specificSite = host == "elitebabes.com" || host.endsWith(".elitebabes.com")
        val suggestedModel = if (specificSite)
            modelLead.find(title.trim())?.groupValues?.getOrNull(1).orEmpty().take(100)
        else ""
        val (date, dateSource) = published(doc)
        val (hint, hintSource) = if (date.isBlank()) galleryDirectoryDateHint(doc, host) else "" to ""
        return Fields(
            model = suggestedModel,
            modelSource = if (suggestedModel.isBlank()) "" else "Предположительно по заголовку",
            publishedDate = date,
            dateSource = dateSource,
            suggestedDate = hint,
            suggestedDateSource = hintSource
        )
    }
}
