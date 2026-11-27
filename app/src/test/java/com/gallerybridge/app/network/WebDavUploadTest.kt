package com.gallerybridge.app.network

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.IOException
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assert.*
import org.junit.Test

class WebDavUploadTest {
    @Test fun lostResponseAfterReceivingFileDoesNotRepeatPut() {
        withServer({ exchange -> exchange.close() }) { upload, url, attempts ->
            assertThrows(IOException::class.java) {
                upload.put(url, "Basic fixture", "photo".toRequestBody()).close()
            }
            assertEquals(1, attempts.get())
        }
    }

    @Test fun retryAfterZeroDoesNotRepeatPut() {
        withServer({ exchange ->
            exchange.responseHeaders.add("Retry-After", "0")
            exchange.sendResponseHeaders(503, -1)
        }) { upload, url, attempts ->
            upload.put(url, "Basic fixture", "photo".toRequestBody()).use {
                assertEquals(503, it.code)
            }
            assertEquals(1, attempts.get())
        }
    }

    @Test fun existingFilePreconditionIsPassedThrough() {
        withServer({ exchange ->
            val status = if (exchange.requestHeaders.getFirst("If-None-Match") == "*") 412 else 204
            exchange.sendResponseHeaders(status, -1)
        }) { upload, url, attempts ->
            upload.put(url, "Basic fixture", "photo".toRequestBody()).use {
                assertEquals(412, it.code)
                assertEquals("*", it.request.header("If-None-Match"))
            }
            assertEquals(1, attempts.get())
        }
    }

    private fun withServer(
        respond: (HttpExchange) -> Unit,
        test: (WebDavUpload, HttpUrl, AtomicInteger) -> Unit
    ) {
        val attempts = AtomicInteger()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/file.jpg") { exchange ->
            try {
                exchange.requestBody.use { it.readBytes() }
                attempts.incrementAndGet()
                respond(exchange)
            } finally {
                exchange.close()
            }
        }
        server.start()
        try {
            test(WebDavUpload(OkHttpClient()), "http://127.0.0.1:${server.address.port}/file.jpg".toHttpUrl(), attempts)
        } finally {
            server.stop(0)
        }
    }
}
