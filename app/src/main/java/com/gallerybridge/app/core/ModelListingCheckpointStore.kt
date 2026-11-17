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
        val errors: List<String> = emptyList()
    ) {
        fun result(): ModelListingSources.Result =
            ModelListingSources.combine(pages, errors, capped || !finished)
    }

    internal fun encode(s: Checkpoint): String {
        val pages = JSONArray()
        s.pages.take(ModelListingSources.MAX_PAGES).forEach { page ->
            val numbered = JSONObject()
            page.pages.forEach { (n, u) -> numbered.put(n.toString(), u) }
            val photos = JSONArray()
            page.photos.take(ModelListingSources.MAX_CARDS_PER_PAGE).forEach { p ->
                photos.put(JSONObject().put("url", p.url).put("title", p.title)
                    .put("date", p.date).put("model", p.model))
            }
            pages.put(JSONObject()
                .put("source", page.pages[page.address.number])
                .put("model", page.model).put("photos", photos)
                .put("videos", page.videos).put("cards", page.cardCount)
                .put("numbered", numbered).put("loader", page.infiniteLoader)
                .put("advertised", page.advertisedCount ?: JSONObject.NULL))
        }
        return JSONObject().put("format", 1).put("id", s.requestId)
            .put("source", s.source).put("finished", s.finished)
            .put("capped", s.capped).put("errors", JSONArray(s.errors))
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
            val array = p.getJSONArray("photos")
            require(array.length() <= ModelListingSources.MAX_CARDS_PER_PAGE)
            for (j in 0 until array.length()) {
                val item = array.getJSONObject(j)
                val link = item.getString("url")
                val verified = ModelListingSources.publicationUrl(link) ?: continue
                photos.add(ModelListingSources.Publication(
                    verified, item.optString("title").take(250),
                    item.optString("date").take(10),
                    item.optString("model").take(130)))
            }
            pages.add(ModelListingSources.Page(
                addressPage, p.optString("model").take(130), photos,
                p.optInt("videos").coerceIn(0, ModelListingSources.MAX_CARDS_PER_PAGE),
                p.optInt("cards").coerceIn(0, ModelListingSources.MAX_CARDS_PER_PAGE),
                links, p.optBoolean("loader"), p.optInt("advertised").takeIf { it > 0 }))
        }
        require(pages.map { it.address.number }.distinct().size == pages.size)
        val errors = objectRoot.optJSONArray("errors")
        Checkpoint(id, source, pages, objectRoot.optBoolean("finished"),
            objectRoot.optBoolean("capped"),
            (0 until (errors?.length() ?: 0)).take(100).map { errors!!.optString(it).take(250) })
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
        write(context, Checkpoint(id, source))
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
                              result: ModelListingSources.Result) {
        val current = read(context) ?: return
        if (current.requestId != id) return
        write(context, current.copy(
            finished = true,
            errors = result.errors.take(100),
            capped = result.incomplete && result.errors.isEmpty() &&
                !current.pages.any { it.infiniteLoader || it.cardCount >= ModelListingSources.MAX_CARDS_PER_PAGE } &&
                result.cards >= (result.advertisedCount ?: Int.MAX_VALUE)
        ))
    }
}
