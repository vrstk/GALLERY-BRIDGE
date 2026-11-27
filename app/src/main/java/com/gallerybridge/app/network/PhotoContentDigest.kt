package com.gallerybridge.app.network

import java.io.File
import java.security.MessageDigest

/**
 * SHA-256 of downloaded source bytes, before any destination write.
 * This is NOT a claim that a cloud provider has synchronized those bytes.
 */
internal object PhotoContentDigest {
    const val MAX_SOURCE_BYTES: Long = 256L * 1024 * 1024

    fun sha256(source: File): String {
        require(source.isFile && source.length() in 1L..MAX_SOURCE_BYTES) {
            "Исходная фотография недоступна или превышает 256 МиБ."
        }
        val expectedSize = source.length()
        val digest = MessageDigest.getInstance("SHA-256")
        var readSize = 0L
        source.inputStream().use { input ->
            val buffer = ByteArray(65536)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count == 0) continue
                readSize += count
                require(readSize <= MAX_SOURCE_BYTES) {
                    "Исходная фотография превышает 256 МиБ."
                }
                digest.update(buffer, 0, count)
            }
        }
        require(readSize == expectedSize && readSize > 0) {
            "Исходная фотография изменилась во время чтения."
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    }
}
