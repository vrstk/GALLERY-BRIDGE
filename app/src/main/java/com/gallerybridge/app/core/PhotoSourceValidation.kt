package com.gallerybridge.app.core

import java.io.File
import java.util.Locale

/**
 * Prevent saving a common failed-source response (HTML/JSON/text error page)
 * under a .jpg/.png name. This is not an image decoder or a claim of integrity:
 * true resolution/content integrity is verified separately.
 */
object PhotoSourceValidation {
    fun validateHeader(header: ByteArray, contentType: String?) {
        val mime = contentType.orEmpty().substringBefore(';').trim().lowercase(Locale.ROOT)
        require(mime.isBlank() || mime.startsWith("image/") ||
            mime == "application/octet-stream" || mime == "binary/octet-stream") {
            "Источник вернул $mime вместо изображения"
        }
        val sample = header.take(128).toByteArray().toString(Charsets.US_ASCII)
            .trimStart('\uFEFF', ' ', '\r', '\n', '\t').lowercase(Locale.ROOT)
        require(!sample.startsWith("<!") &&
            !sample.startsWith("<html") &&
            !sample.startsWith("<?xml") &&
            !sample.startsWith("<script") &&
            !sample.startsWith("{") &&
            !sample.startsWith("[") &&
            !sample.startsWith("error")) {
            "Источник вернул текстовую страницу вместо изображения"
        }
        require(header.isNotEmpty()) { "Источник вернул пустой файл" }
    }

    fun verifyFile(file: File, contentType: String?) {
        val header = file.inputStream().use { input ->
            val bytes = ByteArray(128)
            val size = input.read(bytes)
            if (size <= 0) ByteArray(0) else bytes.copyOf(size)
        }
        validateHeader(header, contentType)
    }
}
