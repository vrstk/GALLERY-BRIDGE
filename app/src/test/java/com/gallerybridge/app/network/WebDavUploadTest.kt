package com.gallerybridge.app.network

import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.*
import org.junit.Test

class WebDavUploadTest {
    @Test fun lostResponseAfterReceivingFileDoesNotRepeatPut() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
            val upload = WebDavUpload(OkHttpClient())
            assertThrows(IOException::class.java) {
                upload.put(server.url("/file.jpg"), "Basic fixture", "photo".toRequestBody()).close()
            }
            val request = checkNotNull(server.takeRequest(1, TimeUnit.SECONDS))
            assertEquals("PUT", request.method)
            assertEquals("photo", request.body.readUtf8())
            assertEquals(1, server.requestCount)
        }
    }

    @Test fun retryAfterZeroDoesNotRepeatPut() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(503).setHeader("Retry-After", "0"))
            server.enqueue(MockResponse().setResponseCode(204))
            val upload = WebDavUpload(OkHttpClient())
            upload.put(server.url("/file.jpg"), "Basic fixture", "photo".toRequestBody()).use {
                assertEquals(503, it.code)
            }
            assertEquals(1, server.requestCount)
        }
    }

    @Test fun existingFilePreconditionIsSentAndRejectionIsPassedThrough() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(412))
            val upload = WebDavUpload(OkHttpClient())
            upload.put(server.url("/file.jpg"), "Basic fixture", "photo".toRequestBody()).use {
                assertEquals(412, it.code)
            }
            val request = checkNotNull(server.takeRequest(1, TimeUnit.SECONDS))
            assertEquals("*", request.getHeader("If-None-Match"))
            assertEquals(1, server.requestCount)
        }
    }
}
