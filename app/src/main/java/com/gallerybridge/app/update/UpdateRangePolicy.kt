package com.gallerybridge.app.update

/**
 * Validate HTTP byte-range responses before appending anything.
 * A server that ignores Range and sends 200 must restart from byte zero;
 * malformed 206 or a different total must never mix two APK payloads.
 */
internal object UpdateRangePolicy {
    enum class Action { REPLACE, APPEND }

    fun decide(startAt: Long, status: Int, header: String?, totalBytes: Long): Action {
        require(startAt >= 0 && totalBytes > 0 && startAt < totalBytes) {
            "Недопустимое смещение загрузки"
        }
        if (status == 200) return Action.REPLACE
        require(status == 206 && startAt > 0L) {
            "Сервер вернул неожиданный HTTP $status"
        }
        val match = Regex("""bytes ([0-9]+)-([0-9]+)/([0-9]+)""")
            .matchEntire(header.orEmpty())
            ?: error("Некорректный Content-Range")
        val (begin, end, count) = match.destructured
        val first = begin.toLongOrNull()
        val last = end.toLongOrNull()
        val length = count.toLongOrNull()
        require(first != null && last != null && length != null &&
            first == startAt && length == totalBytes &&
            last >= first && last < length) {
            "Получен другой диапазон или размер APK"
        }
        return Action.APPEND
    }
}
