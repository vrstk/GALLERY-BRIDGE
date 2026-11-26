package com.gallerybridge.app.update

import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class UpdateConnectionsTest {
    private val apk = "https://github.com/vrstk/GALLERY-BRIDGE/releases/download/v0.96.0-signed/GalleryBridge-v0.96.0-signed.apk"
    private val asset = "https://release-assets.githubusercontent.com/fixture"

    @Test fun rejectedRangeRestartsFromTheVersionedUrlAndReplacesThePartialFile() = runBlocking {
        val fullBytes = byteArrayOf(1, 2, 3, 4, 5)
        val requests = mutableListOf<Response>()
        val directory = Files.createTempDirectory("gb-updater-http-").toFile()

        try {
            val part = UpdatePartialFiles.part(directory, UUID.randomUUID())
            val prefix = byteArrayOf(9, 9)
            part.writeBytes(prefix)
            val offset = part.length()
            val connection = UpdateConnections.connect(apk, offset) { url ->
                when (requests.size) {
                    0, 2 -> Response(url, 302, location = asset)
                    1 -> Response(url, 416)
                    3 -> Response(url, 200, body = fullBytes)
                    else -> error("Неожиданный запрос")
                }.also(requests::add)
            }

            assertEquals(listOf(apk, asset, apk, asset), requests.map { it.url.toString() })
            assertEquals(listOf("bytes=2-", "bytes=2-", null, null),
                requests.map { it.getRequestProperty("Range") })
            assertTrue(requests.take(3).all { it.disconnected })
            assertArrayEquals(prefix, part.readBytes())

            connection.inputStream.use { stream ->
                UpdatePartialFiles.receive(part, fullBytes.size.toLong(), offset,
                    connection.responseCode, connection.getHeaderField("Content-Range"), stream)
            }
            connection.disconnect()
            assertArrayEquals(fullBytes, part.readBytes())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test fun anotherRangeRejectionStopsTheFallbackWithoutLooping() {
        val requests = mutableListOf<Response>()
        assertThrows(IOException::class.java) {
            UpdateConnections.connect(apk, 2L) { url ->
                Response(url, 416).also(requests::add)
            }
        }

        assertEquals(listOf("bytes=2-", null), requests.map { it.getRequestProperty("Range") })
        assertTrue(requests.all { it.disconnected })
    }

    @Test fun fallbackRejectsAnUnsolicitedPartialResponse() {
        val requests = mutableListOf<Response>()
        assertThrows(IOException::class.java) {
            UpdateConnections.connect(apk, 2L) { url ->
                Response(url, if (requests.isEmpty()) 416 else 206).also(requests::add)
            }
        }
        assertEquals(2, requests.size)
        assertTrue(requests.all { it.disconnected })
    }

    private class Response(
        url: URL,
        private val status: Int,
        private val location: String? = null,
        private val body: ByteArray = byteArrayOf()
    ) : HttpURLConnection(url) {
        var disconnected = false
            private set

        override fun connect() = Unit
        override fun disconnect() { disconnected = true }
        override fun usingProxy() = false
        override fun getResponseCode() = status
        override fun getHeaderField(name: String?) = if (name == "Location") location else null
        override fun getInputStream() = ByteArrayInputStream(body)
    }
}
