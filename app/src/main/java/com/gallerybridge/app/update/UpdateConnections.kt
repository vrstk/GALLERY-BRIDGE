package com.gallerybridge.app.update

import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/** Same HTTPS allowlist for manifest requests and resumable APK streams. */
internal object UpdateConnections {
    fun connect(initial: String, startAt: Long = 0L): HttpURLConnection {
        require(startAt >= 0L)
        var address = initial
        repeat(6) {
            require(address == UpdatePolicy.API || address == UpdatePolicy.MANIFEST ||
                UpdatePolicy.permittedUrl(address) || UpdatePolicy.permittedRedirect(address)) {
                "Недопустимый HTTPS-источник"
            }
            val connection = URL(address).openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.setRequestProperty("User-Agent", "GALLERY-BRIDGE-updater")
            connection.setRequestProperty("Accept-Encoding", "identity")
            if (startAt > 0) connection.setRequestProperty("Range", "bytes=$startAt-")
            val code = connection.responseCode
            if (code in 300..399) {
                val location = connection.getHeaderField("Location")
                    ?: error("Нет адреса перенаправления")
                val next = URL(URL(address), location).toString()
                connection.disconnect()
                require(UpdatePolicy.permittedRedirect(next)) { "Небезопасное перенаправление" }
                address = next
            } else {
                if (code !in 200..299) {
                    connection.disconnect()
                    throw java.io.IOException("GitHub HTTP $code")
                }
                return connection
            }
        }
        error("Слишком много перенаправлений")
    }

    fun readText(url: String, max: Int): String =
        connect(url).useConnection { connection ->
            connection.inputStream.use { input ->
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(4096)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    require(out.size() + n <= max) { "Ответ GitHub слишком большой" }
                    out.write(buffer, 0, n)
                }
                out.toString(Charsets.UTF_8.name())
            }
        }

    private inline fun <T> HttpURLConnection.useConnection(
        block: (HttpURLConnection) -> T
    ): T = try { block(this) } finally { disconnect() }
}
