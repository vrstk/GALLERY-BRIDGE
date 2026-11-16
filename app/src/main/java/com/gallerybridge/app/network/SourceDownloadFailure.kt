package com.gallerybridge.app.network

import java.io.IOException

/** Explain where a download failed without guessing about the remote site's access policy. */
object SourceDownloadFailure {
    class HttpError(val statusCode: Int) : IOException(describe(statusCode))

    /** Stop at server overload rather than requesting hundreds of other images. */
    fun shouldPauseQueue(statusCode: Int): Boolean = statusCode == 429 || statusCode in 500..599

    fun describe(statusCode: Int): String = when (statusCode) {
        401 -> "Источник медиа: HTTP 401 - для скачивания требуется авторизация."
        403 -> "Источник медиа: HTTP 403 - сервер отказал в доступе к файлу. Проверьте ссылку и разрешение на скачивание."
        404 -> "Источник медиа: HTTP 404 - файл недоступен или ссылка устарела."
        429 -> "Источник медиа: HTTP 429 - сервер ограничил частоту запросов."
        else -> "Источник медиа: HTTP $statusCode"
    }
}
