package com.gallerybridge.app.network

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser

/**
 * Checks a Depth: 0 WebDAV PROPFIND response before creating or skipping files.
 * HTTP 207 by itself does NOT establish that the requested resource exists:
 * the response must contain a successful propstat for the exact requested URL.
 *
 * This is a metadata/size check, not a SHA-256 verification of remote bytes.
 */
object WebDavResourcePolicy {
    class UnsafeResource(message: String) : IllegalStateException(message)

    sealed class Lookup {
        data object Missing : Lookup()
        data class Existing(val bytes: Long?, val directory: Boolean) : Lookup()
    }

    private fun localName(element: Element): String =
        element.tagName().substringAfterLast(':').lowercase()

    private fun direct(element: Element, name: String): Element? =
        element.children().firstOrNull { localName(it) == name }

    private fun code(status: String): Int? =
        Regex("""HTTP/\d(?:\.\d)?\s+(\d{3})(?:\s|$)""", RegexOption.IGNORE_CASE)
            .find(status.trim())?.groupValues?.get(1)?.toIntOrNull()

    fun inspect(
        httpCode: Int, body: String?,
        requestedUrl: String, expectDirectory: Boolean
    ): Lookup {
        if (httpCode == 404) return Lookup.Missing
        if (httpCode != 207) throw UnsafeResource("Mail WebDAV PROPFIND: HTTP $httpCode")
        val request = requestedUrl.toHttpUrlOrNull()
            ?: throw UnsafeResource("Не удалось проверить адрес файла Mail WebDAV")
        if (body.isNullOrBlank()) throw UnsafeResource(
            "Mail WebDAV вернул пустой PROPFIND. Нельзя подтвердить существование файла."
        )
        if (body.length > 250_000) throw UnsafeResource("Слишком большой ответ Mail WebDAV")
        val doc = Jsoup.parse(body, "", Parser.xmlParser())
        val responses = doc.getAllElements().filter { localName(it) == "response" }
        val matching = responses.filter { entry ->
            val href = direct(entry, "href")?.text()?.trim().orEmpty()
            if (href.isBlank()) return@filter false
            val url = request.resolve(href) ?: return@filter false
            url.scheme == request.scheme && url.host == request.host &&
                url.port == request.port &&
                url.pathSegments.dropLastWhile { it.isBlank() } ==
                request.pathSegments.dropLastWhile { it.isBlank() }
        }
        if (matching.size != 1) throw UnsafeResource(
            "Mail WebDAV не подтвердил точный адрес файла (совпадений: ${matching.size})."
        )
        val response = matching.single()
        val directStatus = direct(response, "status")?.text().orEmpty()
        if (directStatus.isNotBlank()) {
            val statusCode = code(directStatus)
                ?: throw UnsafeResource("Mail WebDAV вернул непонятный статус ресурса")
            if (statusCode == 404) return Lookup.Missing
            if (statusCode !in 200..299) throw UnsafeResource(
                "Mail WebDAV: статус ресурса HTTP $statusCode"
            )
        }
        val successfulProps = response.children()
            .filter { localName(it) == "propstat" && code(direct(it, "status")?.text().orEmpty()) in 200..299 }
            .mapNotNull { direct(it, "prop") }
        if (successfulProps.isEmpty()) throw UnsafeResource(
            "Mail WebDAV не подтвердил свойства файла или папки."
        )
        val properties = successfulProps.flatMap { it.children() }
        val resourceTypes = properties.filter { localName(it) == "resourcetype" }
        val directory = resourceTypes.any { type ->
            type.getAllElements().any { it !== type && localName(it) == "collection" }
        }
        if (expectDirectory) {
            if (!directory) throw UnsafeResource(
                "Mail WebDAV не подтвердил, что адрес принадлежит папке."
            )
            return Lookup.Existing(null, directory = true)
        }
        if (directory) throw UnsafeResource(
            "В Mail WebDAV вместо фотографии обнаружена папка."
        )
        val sizes = properties.filter { localName(it) == "getcontentlength" }.map { property ->
            property.text().trim().toLongOrNull()
                ?: throw UnsafeResource("Mail WebDAV вернул непонятный размер файла.")
        }.distinct()
        if (sizes.size != 1) throw UnsafeResource(
            "Mail WebDAV не сообщил однозначный размер файла; пропуск небезопасен."
        )
        val size = sizes.single()
        if (size <= 0L) throw UnsafeResource(
            "В Mail WebDAV обнаружен пустой файл (0 байт). Не пропускаем и не перезаписываем."
        )
        return Lookup.Existing(size, directory = false)
    }
}
