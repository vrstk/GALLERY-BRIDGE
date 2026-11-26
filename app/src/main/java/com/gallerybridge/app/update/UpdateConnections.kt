package com.gallerybridge.app.update

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Same HTTPS allowlist for manifest requests and resumable APK streams. */
internal object UpdateConnections {
    // HTTP 416 rejects the saved offset. Keep the part until a full HTTP 200
    // stream is available, then let the existing writer replace its contents.
    fun connect(
        initial: String,
        startAt: Long = 0L,
        open: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection }
    ): HttpURLConnection {
        require(startAt >= 0L)
        val connection = connectOnce(initial, startAt, open)
        if (startAt == 0L || connection.responseCode != 416) return connection
        connection.disconnect()

        val full = connectOnce(initial, 0L, open)
        if (full.responseCode == HttpURLConnection.HTTP_OK) return full
        val code = full.responseCode
        full.disconnect()
        throw IOException("GitHub HTTP $code вместо полной загрузки APK")
    }

    private fun connectOnce(
        initial: String,
        startAt: Long,
        open: (URL) -> HttpURLConnection
    ): HttpURLConnection {
        var address = initial
        repeat(6) {
            require(address == UpdatePolicy.API || address == UpdatePolicy.MANIFEST ||
                UpdatePolicy.permittedUrl(address) || UpdatePolicy.permittedRedirect(address)) {
                "Недопустимый HTTPS-источник"
            }
            val connection = open(URL(address))
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
                if (code !in 200..299 && !(code == 416 && startAt > 0)) {
                    connection.disconnect()
                    throw IOException("GitHub HTTP $code")
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
