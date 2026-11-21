package com.gallerybridge.app.network

/**
 * HTTP 206 means only a byte range was returned. Accept it as complete only
 * if Content-Range explicitly spans every byte of the resource.
 * Neither this check nor HTTP 200 alone proves that an MP4 is decodable.
 */
internal object VideoDownloadIntegrity {
    private val range = Regex("""^bytes 0-([0-9]+)/([1-9][0-9]*)$""")

    fun requireCompleteResponse(
        code: Int, contentType: String, contentRange: String?, contentLength: Long
    ) {
        val mime = contentType.substringBefore(';').trim().lowercase()
        require(mime == "video/mp4" || mime == "application/octet-stream") {
            "Источник не подтвердил MP4: $mime"
        }
        if (code == 200) {
            require(contentRange.isNullOrBlank()) {
                "HTTP 200 с неполным диапазоном: завершение неизвестно."
            }
            return
        }
        require(code == 206) { "Источник видео вернул HTTP $code." }
        val match = range.matchEntire(contentRange.orEmpty().trim())
            ?: error("HTTP 206 без полного Content-Range: видео может быть обрезано.")
        val end = match.groupValues[1].toLongOrNull()
        val total = match.groupValues[2].toLongOrNull()
        require(total != null && end != null && end + 1 == total &&
            contentLength == total) {
            "HTTP 206 содержит только часть видео. Загрузка заблокирована."
        }
    }
}
