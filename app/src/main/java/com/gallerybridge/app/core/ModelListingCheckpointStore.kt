package com.gallerybridge.app.core

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject

/**
 * Application-private, atomic page checkpoints. Store page metadata only:
 * no images, cookies, credentials, file bodies, queue or cloud state.
 * The request ID prevents a replaced worker from overwriting new scan data.
 */
internal object ModelListingCheckpointStore {
    private const val FILE = "model-listing-checkpoint.json"
    private const val MAX_BYTES = 8 * 1024 * 1024

    data class Checkpoint(
        val requestId: String,
        val source: String,
        val pages: List<ModelListingSources.Page> = emptyList(),
        val finished: Boolean = false,
        val capped: Boolean = false,
        val errors: List<String> = emptyList(),
        val apiComplete: Boolean = false
    ) {
        fun result(): ModelListingSources.Result =
            ModelListingSources.combine(pages, errors, capped || !finished, apiComplete)
    }

    internal fun encode(s: Checkpoint): String {
        val pages = JSONArray()
        s.pages.take(ModelListingSources.MAX_PAGES).forEach { page ->
            val numbered = JSONObject()
            page.pages.forEach { (n, u) -> numbered.put(n.toString(), u) }
            val photos = JSONArray()
            page.photos.take(ModelListingSources.MAX_CARDS_PER_PAGE).forEach { p ->
                photos.put(JSONObject().put("url", p.url).put("title", p.title)
                    .put("date", p.date).put("model", p.model)
                    .put("sourceId", p.sourceId).put("caption", p.caption)
                    .put("studio", p.studio).put("displayedDate", p.displayedDate)
                    .put("type", p.type).put("previewUrl", p.previewUrl).put("watchUrl", p.watchUrl))
            }
            val videos = JSONArray()
            page.videoCards.forEach { p ->
                videos.put(JSONObject().put("url", p.url).put("title", p.title)
                    .put("date", p.date).put("model", p.model).put("sourceId", p.sourceId)
                    .put("caption", p.caption).put("studio", p.studio)
                    .put("displayedDate", p.displayedDate).put("type", "video")
                    .put("previewUrl", p.previewUrl).put("watchUrl", p.watchUrl))
            }
            pages.put(JSONObject().put("apiUrl", page.apiUrl).put("videoCards", videos)
                .put("source", page.pages[page.address.number])
                .put("model", page.model).put("photos", photos)
                .put("videos", page.videos).put("cards", page.cardCount)
                .put("numbered", numbered).put("loader", page.infiniteLoader)
                .put("advertised", page.advertisedCount ?: JSONObject.NULL))
        }
        return JSONObject().put("format", 1).put("id", s.requestId)
            .put("source", s.source).put("finished", s.finished)
            .put("capped", s.capped).put("errors", JSONArray(s.errors))
            .put("apiComplete", s.apiComplete)
            .put("pages", pages).toString()
    }

    internal fun decode(raw: String): Checkpoint? = runCatching {
        val objectRoot = JSONObject(raw)
        require(objectRoot.getInt("format") == 1)
        val source = objectRoot.getString("source")
        val address = requireNotNull(ModelListingSources.address(source))
        val id = objectRoot.getString("id")
        require(id.length in 8..90)
        val jsonPages = objectRoot.getJSONArray("pages")
        require(jsonPages.length() <= ModelListingSources.MAX_PAGES)
        val pages = mutableListOf<ModelListingSources.Page>()
        for (i in 0 until jsonPages.length()) {
            val p = jsonPages.getJSONObject(i)
            val pageUrl = p.getString("source")
            val addressPage = requireNotNull(ModelListingSources.address(pageUrl))
            require(addressPage.slug == address.slug)
            val links = linkedMapOf<Int, String>()
            val numbered = p.getJSONObject("numbered")
            val keys = numbered.keys()
            while (keys.hasNext()) {
                val n = keys.next().toIntOrNull() ?: continue
                val target = numbered.getString(n.toString())
                if (ModelListingSources.address(target) == ModelListingSources.Address(address.slug, n))
                    links[n] = target
            }
            links[addressPage.number] = pageUrl
            val photos = mutableListOf<ModelListingSources.Publication>()
            fun parseItem(item: JSONObject): ModelListingSources.Publication? {
                val link = ModelListingSources.publicationUrl(item.optString("url")) ?: return null
                return ModelListingSources.Publication(link, item.optString("title").take(250),
                    item.optString("date").take(10), item.optString("model").take(130),
                    item.optString("sourceId").take(20), item.optString("caption").take(250),
                    item.optString("studio").take(130),
                    item.optString("displayedDate").take(10),
                    item.optString("type", "photo"), item.optString("previewUrl").take(2048),
                    XeroticaBridge.watchTarget(item.optString("watchUrl")).orEmpty())
            }
            val array = p.getJSONArray("photos")
            require(array.length() <= ModelListingSources.MAX_CARDS_PER_PAGE)
            for (j in 0 until array.length()) {
                val item = array.getJSONObject(j)
                parseItem(item)?.let(photos::add)
            }
            val videoCards = mutableListOf<ModelListingSources.Publication>()
            val videoJson = p.optJSONArray("videoCards") ?: JSONArray()
            require(videoJson.length() <= ModelListingSources.MAX_CARDS_PER_PAGE)
            for (j in 0 until videoJson.length()) {
                parseItem(videoJson.getJSONObject(j))?.let(videoCards::add)
            }
            pages.add(ModelListingSources.Page(
                addressPage, p.optString("model").take(130), photos,
                p.optInt("videos").coerceIn(0, ModelListingSources.MAX_CARDS_PER_PAGE),
                p.optInt("cards").coerceIn(0, ModelListingSources.MAX_CARDS_PER_PAGE),
                links, p.optBoolean("loader"), p.optInt("advertised").takeIf { it > 0 },
                EliteBabesModelAdapter.validatedApiUrl(p.optString("apiUrl")).orEmpty(), videoCards))
        }
        require(pages.map { it.address.number }.distinct().size == pages.size)
        val errors = objectRoot.optJSONArray("errors")
        Checkpoint(id, source, pages, objectRoot.optBoolean("finished"),
            objectRoot.optBoolean("capped"),
            (0 until (errors?.length() ?: 0)).take(100).map { errors!!.optString(it).take(250) },
            objectRoot.optBoolean("apiComplete"))
    }.getOrNull()

    @Synchronized fun read(context: Context): Checkpoint? = runCatching {
        val file = AtomicFile(context.getFileStreamPath(FILE))
        file.openRead().use { stream ->
            val bytes = stream.readBytes()
            require(bytes.size in 1..MAX_BYTES)
            decode(String(bytes, Charsets.UTF_8))
        }
    }.getOrNull()

    @Synchronized private fun write(context: Context, data: Checkpoint) {
        val bytes = encode(data).toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_BYTES) { "Слишком много данных каталога." }
        val file = AtomicFile(context.getFileStreamPath(FILE))
        val out = file.startWrite()
        try {
            out.write(bytes)
            file.finishWrite(out)
        } catch (e: Exception) {
            file.failWrite(out)
            throw e
        }
    }

    @Synchronized fun begin(context: Context, id: String, source: String) {
        require(ModelListingSources.address(source) != null) { "Неподдерживаемая ссылка модели." }
        val previous = read(context)?.takeIf {
            it.source == source && (!it.finished || it.capped || it.errors.isNotEmpty())
        }
        // A new worker takes ownership of the saved pages; stale jobs cannot write.
        write(context, Checkpoint(id, source, pages = previous?.pages.orEmpty()))
    }

    @Synchronized fun add(context: Context, id: String, page: ModelListingSources.Page) {
        val current = read(context) ?: error("Контрольная точка поиска отсутствует.")
        // An old worker must never erase or modify a new scan.
        if (current.requestId != id) return
        require(ModelListingSources.address(current.source)?.slug == page.address.slug)
        if (current.pages.none { it.address.number == page.address.number }) {
            write(context, current.copy(pages = current.pages + page))
        }
    }

    @Synchronized fun finish(context: Context, id: String,
                              result: ModelListingSources.Result, apiComplete: Boolean = false) {
        val current = read(context) ?: return
        if (current.requestId != id) return
        write(context, current.copy(
            finished = true,
            errors = result.errors.take(100),
            // Preserve every incomplete condition, including the hard page cap.
            capped = result.incomplete && !apiComplete,
            apiComplete = apiComplete
        ))
    }
}
