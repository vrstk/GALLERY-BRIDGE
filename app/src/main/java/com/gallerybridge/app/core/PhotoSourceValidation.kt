package com.gallerybridge.app.core

import java.io.File
import java.util.Locale

/**
 * Reject disguised HTML/error bodies and unsupported binary data before a
 * supposed photo is sent to SAF or WebDAV. Signature checks are not a full
 * image decode, quality audit or proof of remote persistence.
 */
object PhotoSourceValidation {
    private fun has(data: ByteArray, offset: Int, value: String): Boolean {
        val bytes = value.toByteArray(Charsets.US_ASCII)
        return data.size >= offset + bytes.size &&
            bytes.indices.all { data[offset + it] == bytes[it] }
    }

    private fun detect(header: ByteArray): String? {
        if (header.size >= 3 &&
            (header[0].toInt() and 255) == 255 &&
            (header[1].toInt() and 255) == 216 &&
            (header[2].toInt() and 255) == 255) return "jpeg"
        if (header.size >= 8 &&
            header.copyOfRange(0, 8).contentEquals(byteArrayOf(
                0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a
            ))) return "png"
        if (has(header, 0, "GIF87a") || has(header, 0, "GIF89a")) return "gif"
        if (has(header, 0, "RIFF") && has(header, 8, "WEBP")) return "webp"
        if (header.size >= 14 && has(header, 0, "BM")) return "bmp"
        if (has(header, 4, "ftyp") && header.size >= 12 &&
            (8 until minOf(header.size - 3, 96) step 4).any {
                has(header, it, "avif") || has(header, it, "avis")
            }) return "avif"
        return null
    }

    fun validateHeader(header: ByteArray, contentType: String?) {
        val mime = contentType.orEmpty().substringBefore(';').trim().lowercase(Locale.ROOT)
        require(mime.isBlank() || mime.startsWith("image/") ||
            mime == "application/octet-stream" || mime == "binary/octet-stream") {
            "Источник вернул $mime вместо изображения"
        }
        require(header.isNotEmpty()) { "Источник вернул пустой файл" }
        val sample = header.take(128).toByteArray().toString(Charsets.US_ASCII)
            .trimStart('\uFEFF', ' ', '\r', '\n', '\t').lowercase(Locale.ROOT)
        require(!sample.startsWith("<!") && !sample.startsWith("<html") &&
            !sample.startsWith("<?xml") && !sample.startsWith("<script") &&
            !sample.startsWith("{") && !sample.startsWith("[") &&
            !sample.startsWith("error")) {
            "Источник вернул текстовую страницу вместо изображения"
        }
        val found = detect(header)
        require(found != null) {
            "Источник вернул неподдерживаемый или повреждённый заголовок изображения"
        }
        val expected = when (mime) {
            "image/jpeg", "image/jpg", "image/pjpeg" -> "jpeg"
            "image/png", "image/x-png" -> "png"
            "image/gif" -> "gif"
            "image/webp" -> "webp"
            "image/bmp", "image/x-ms-bmp" -> "bmp"
            "image/avif" -> "avif"
            else -> null
        }
        require(expected == null || found == expected) {
            "Источник объявил формат $mime, но содержимое не соответствует ему"
        }
    }

    fun verifyFile(file: File, contentType: String?) {
        val header = file.inputStream().use { input ->
            val buffer = ByteArray(128)
            val n = input.read(buffer)
            if (n <= 0) ByteArray(0) else buffer.copyOf(n)
        }
        validateHeader(header, contentType)
    }
}
