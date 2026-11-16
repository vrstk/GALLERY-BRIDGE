package com.gallerybridge.app.network

import java.io.File
import java.io.InputStream
import java.security.MessageDigest

/**
 * Read-only integrity gate before skipping an existing destination.
 * Unknown, truncated or differing bytes must NEVER be marked SKIPPED.
 * No remote file is changed and no source bytes are persisted in the queue.
 */
internal object ExistingFileVerifier {
    const val MAX_BYTES = 64L * 1024L * 1024L

    class Unverified(message: String, cause: Throwable? = null) :
        IllegalStateException(message, cause)

    data class Digest(val bytes: Long, val sha256: String)

    private fun hash(input: InputStream): Digest {
        val digest = MessageDigest.getInstance("SHA-256")
        var length = 0L
        val buffer = ByteArray(65536)
        input.use { stream ->
            while (true) {
                val n = stream.read(buffer)
                if (n < 0) break
                if (n == 0) continue
                length += n
                if (length > MAX_BYTES) throw Unverified(
                    "Размер существующей фотографии превышает лимит проверки 64 МиБ."
                )
                digest.update(buffer, 0, n)
            }
        }
        if (length == 0L) throw Unverified("Существующий файл пустой.")
        return Digest(length, digest.digest().joinToString("") {
            "%02x".format(it.toInt() and 255)
        })
    }

    fun verify(source: File, existing: InputStream, reportedSize: Long? = null) {
        try {
            if (!source.isFile || source.length() !in 1L..MAX_BYTES) {
                existing.close()
                throw Unverified("Источник недоступен или превышает лимит проверки 64 МиБ.")
            }
            val sourceHash = source.inputStream().use { hash(it) }
            val destinationHash = hash(existing)
            if (reportedSize != null && reportedSize != destinationHash.bytes) {
                throw Unverified("Размер существующего файла не совпадает с метаданными провайдера.")
            }
            if (sourceHash.bytes != destinationHash.bytes ||
                sourceHash.sha256 != destinationHash.sha256
            ) throw Unverified("SHA-256 существующей фотографии не совпадает с источником.")
        } catch (e: Unverified) {
            throw e
        } catch (e: Exception) {
            throw Unverified("Невозможно проверить существующий файл; автоматический пропуск запрещён.", e)
        }
    }

    fun requireSafeWebDavGet(status: Int, scheme: String, host: String, mime: String) {
        if (status != 200 || scheme != "https" || host != "webdav.cloud.mail.ru" ||
            mime.startsWith("text/", ignoreCase = true) ||
            mime.contains("html", ignoreCase = true)
        ) throw Unverified("Mail WebDAV не предоставил подтверждённые байты фотографии.")
    }
}
