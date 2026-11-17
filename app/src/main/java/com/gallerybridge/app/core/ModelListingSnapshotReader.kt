package com.gallerybridge.app.core

import org.jsoup.Jsoup
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.Locale

/**
 * Import the saved HTML *part* of a Chrome MHTML snapshot. Resource parts
 * (images, CSS, video) are never read or stored, even for very large files.
 * No JavaScript executes and no network requests are made.
 */
internal object ModelListingSnapshotReader {
    private const val MAX_LINE = 128 * 1024
    private const val MAX_HEADERS = 80
    private const val MAX_HTML_BYTES = 8 * 1024 * 1024
    private const val MAX_ENCODED_HTML = 12 * 1024 * 1024
    private val latin = StandardCharsets.ISO_8859_1
    private val boundaryPattern = Regex(
        """(?:^|;)\s*boundary\s*=\s*(?:"([^"]{1,180})"|([^;\s]{1,180}))""",
        RegexOption.IGNORE_CASE
    )

    data class Snapshot(val url: String, val html: String)

    private fun line(input: BufferedInputStream): String? {
        val bytes = ByteArrayOutputStream()
        while (true) {
            val ch = input.read()
            if (ch == -1) {
                if (bytes.size() == 0) return null
                break
            }
            if (ch == 10) break
            require(bytes.size() < MAX_LINE) { "Слишком длинная строка MHT." }
            bytes.write(ch)
        }
        val data = bytes.toByteArray()
        val length = if (data.isNotEmpty() && data.last() == 13.toByte()) data.size - 1 else data.size
        return String(data, 0, length, latin)
    }

    private fun headers(input: BufferedInputStream): Map<String, String> {
        val values = linkedMapOf<String, String>()
        var current: String? = null
        repeat(MAX_HEADERS) {
            val row = line(input) ?: error("Оборванные заголовки MHT.")
            if (row.isEmpty()) return values
            if (row.first().isWhitespace()) {
                val key = current ?: error("Неверные заголовки MHT.")
                values[key] = values.getValue(key) + " " + row.trim()
            } else {
                val colon = row.indexOf(':')
                require(colon in 1..70) { "Неверный заголовок MHT." }
                val key = row.substring(0, colon).lowercase(Locale.ROOT)
                values[key] = row.substring(colon + 1).trim()
                current = key
            }
        }
        error("Слишком много заголовков MHT.")
    }

    private fun decodeQuotedPrintable(row: String, output: ByteArrayOutputStream): Boolean {
        val source = row.toByteArray(latin)
        var i = 0
        var softBreak = false
        while (i < source.size) {
            if (source[i] == '='.code.toByte()) {
                if (i == source.lastIndex) {
                    softBreak = true
                    i++
                    continue
                }
                require(i + 2 < source.size) { "Повреждённое quoted-printable содержимое." }
                val first = Character.digit(source[i + 1].toInt().toChar(), 16)
                val second = Character.digit(source[i + 2].toInt().toChar(), 16)
                require(first >= 0 && second >= 0) { "Повреждённое quoted-printable содержимое." }
                output.write((first shl 4) or second)
                i += 3
            } else {
                output.write(source[i].toInt() and 255)
                i++
            }
        }
        return softBreak
    }

    fun read(input: InputStream): Snapshot {
        val source = BufferedInputStream(input, 64 * 1024)
        val envelope = headers(source)
        require(envelope["mime-version"]?.startsWith("1.0") == true &&
            envelope["content-type"]?.lowercase(Locale.ROOT)?.contains("multipart/related") == true
        ) { "Ожидался сохранённый файл MHT/MHTML." }
        val mimeType = envelope.getValue("content-type")
        val match = boundaryPattern.find(mimeType) ?: error("Не указан разделитель MHT.")
        val boundary = match.groups[1]?.value ?: match.groups[2]?.value
            ?: error("Неверный разделитель MHT.")
        val marker = "--$boundary"
        require(line(source) == marker) { "Не найден HTML-раздел MHT." }
        val part = headers(source)
        require(part["content-type"]?.lowercase(Locale.ROOT)?.startsWith("text/html") == true) {
            "Первый раздел MHT не содержит HTML."
        }
        val url = part["content-location"] ?: envelope["snapshot-content-location"]
            ?: error("Отсутствует адрес исходного каталога.")
        val address = ModelListingSources.address(url)
            ?: error("MHT не принадлежит поддерживаемому HTTPS-каталогу модели.")
        envelope["snapshot-content-location"]?.let { origin ->
            require(ModelListingSources.address(origin) == address) {
                "Адреса HTML и сохранённой страницы не совпадают."
            }
        }
        val encoding = part["content-transfer-encoding"]?.lowercase(Locale.ROOT).orEmpty()
        require(encoding in setOf("quoted-printable", "7bit", "8bit", "")) {
            "Неподдерживаемая кодировка HTML-раздела MHT."
        }
        val output = ByteArrayOutputStream()
        var rawSize = 0
        var finished = false
        while (true) {
            val row = line(source) ?: break
            if (row == marker || row == "$marker--") {
                finished = true
                break
            }
            rawSize += row.length + 2
            require(rawSize <= MAX_ENCODED_HTML) {
                "HTML-раздел MHT превышает 12 МБ."
            }
            val soft = if (encoding == "quoted-printable")
                decodeQuotedPrintable(row, output)
            else {
                output.write(row.toByteArray(latin))
                false
            }
            if (!soft) {
                output.write(13)
                output.write(10)
            }
            require(output.size() <= MAX_HTML_BYTES) { "HTML-раздел MHT превышает 8 МБ." }
        }
        require(finished && output.size() > 0) { "MHT повреждён: HTML-раздел не завершён." }
        val text = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(output.toByteArray())).toString()
        return Snapshot(url, text)
    }

    fun parse(input: InputStream): ModelListingSources.Page {
        val snapshot = read(input)
        val document = Jsoup.parse(snapshot.html, snapshot.url)
        return ModelListingSources.parse(document, snapshot.url)
            ?: error("В MHT не найдена структура карточек модели.")
    }
}
