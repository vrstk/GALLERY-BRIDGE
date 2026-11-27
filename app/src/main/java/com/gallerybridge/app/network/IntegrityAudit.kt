package com.gallerybridge.app.network

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.gallerybridge.app.core.Kind
import com.gallerybridge.app.core.Paths
import com.gallerybridge.app.transfer.QueueStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

/**
 * Optional, read-only SHA-256 audit of completed SAF and Mail WebDAV transfers.
 * A stored SHA-256 describes the source bytes at transfer time. No remote write
 * or queue mutation happens here. SAF acceptance never proves cloud sync.
 */
object IntegrityAudit {
    private const val LIMIT = 32L * 1024 * 1024
    // Authorization must never follow a redirect outside the trusted WebDAV origin.
    private val mailHttp = OkHttpClient.Builder()
        .followRedirects(false).followSslRedirects(false)
        .callTimeout(90, TimeUnit.SECONDS).build()
    private class TooLarge : Exception("Файл больше 32 МиБ")
    private data class Digest(val bytes: Long, val sha256: String)

    data class Result(
        val total: Int, val matched: Int, val different: Int,
        val unavailable: Int, val oversized: Int,
        val dimensions: List<String>, val issues: List<String>,
        val target: String, val missingDigest: Int = 0
    ) {
        fun summary(): String = buildString {
            append("Принято по очереди: $total. Проверено по SHA-256: $matched из $total.")
            append("\nРазличаются: $different; недоступно: $unavailable;" +
                " больше лимита: $oversized; нет исходного SHA-256: $missingDigest.")
            if (missingDigest > 0) append(
                "\nДля старых записей без хеша, сохранённого во время передачи, " +
                    "невозможно надёжно подтвердить исходные байты. " +
                    "Повторное скачивание источника не считается доказательством."
            )
            if (dimensions.isNotEmpty()) {
                append("\nРазмеры доступных изображений:")
                dimensions.take(6).forEach { append("\n• " + it) }
            }
            if (issues.isNotEmpty()) {
                append("\nПодробности:")
                issues.take(12).forEach { append("\n• " + it) }
            }
            if (target == "Папка Android") {
                append("\nSHA-256 подтверждает байты, возвращённые Android-провайдером. " +
                    "Синхронизация с удалённым облаком не подтверждена.")
            } else {
                append("\nSHA-256 подтверждает байты, прочитанные непосредственно из Mail WebDAV. " +
                    "Результат не является гарантией долговременного хранения.")
            }
            append("\nПроверка ничего не удаляет, не перезаписывает и не изменяет очередь.")
        }
    }

    private suspend fun hash(stream: InputStream): Digest {
        val md = MessageDigest.getInstance("SHA-256")
        val buf = ByteArray(65536)
        var length = 0L
        stream.use { input ->
            while (true) {
                coroutineContext.ensureActive()
                val count = input.read(buf)
                if (count < 0) break
                if (count == 0) continue
                length += count
                if (length > LIMIT) throw TooLarge()
                md.update(buf, 0, count)
            }
        }
        require(length > 0L) { "Файл пустой." }
        return Digest(length, md.digest().joinToString("") {
            "%02x".format(it.toInt() and 255)
        })
    }

    /**
     * Only PROPFIND and GET against the fixed Mail.ru HTTPS origin.
     * Treat redirects, missing objects and ambiguous metadata as unavailable,
     * not as an excuse to create or replace an object.
     */
    private suspend fun mailReadback(
        path: List<String>, authorization: String
    ): Pair<Digest, Long> {
        val destination = path.fold("https://webdav.cloud.mail.ru/".toHttpUrl()) {
            url, segment -> url.newBuilder().addPathSegment(Paths.clean(segment)).build()
        }
        val lookup = mailHttp.newCall(
            Request.Builder().url(destination).header("Authorization", authorization)
                .header("Depth", "0").method("PROPFIND", null).build()
        ).execute().use { response ->
            WebDavResourcePolicy.inspect(
                response.code, response.body?.string(), destination.toString(),
                expectDirectory = false
            )
        }
        val declared = (lookup as? WebDavResourcePolicy.Lookup.Existing)?.bytes
            ?: error("Mail WebDAV не подтвердил существование файла.")
        if (declared > LIMIT) throw TooLarge()
        val digest = mailHttp.newCall(
            Request.Builder().url(destination).header("Authorization", authorization)
                .get().build()
        ).execute().use { response ->
            ExistingFileVerifier.requireSafeWebDavGet(
                response.code, response.request.url.scheme, response.request.url.host,
                response.header("Content-Type").orEmpty()
            )
            val body = response.body ?: error("Mail WebDAV не вернул содержимое.")
            if (body.contentLength() > LIMIT) throw TooLarge()
            hash(body.byteStream())
        }
        return digest to declared
    }

    private data class DestinationReadback(
        val digest: Digest, val declaredBytes: Long?, val document: Uri? = null
    )

    private suspend fun readDestination(
        context: Context, queue: QueueStore.Snapshot, entry: QueueStore.Entry, authorization: String
    ): DestinationReadback {
        if (queue.target == "Mail WebDAV") {
            val (digest, bytes) = mailReadback(
                ReadbackAuditPolicy.remoteSegments(queue, entry), authorization
            )
            return DestinationReadback(digest, bytes)
        }
        val uri = SafDestination.existingFileForAudit(
            context, queue.root, ReadbackAuditPolicy.folders(queue, entry), entry.media.name
        )
        val stream = context.contentResolver.openInputStream(uri)
            ?: error("Провайдер Android не разрешил чтение файла.")
        return DestinationReadback(hash(stream), null, uri)
    }

    internal suspend fun verifyBlocked(
        context: Context, queue: QueueStore.Snapshot, entry: QueueStore.Entry,
        email: String, password: String
    ): String? = withContext(Dispatchers.IO) {
        require(ReadbackAuditPolicy.canVerifyBlocked(queue, entry))
        val authorization = if (queue.target == "Mail WebDAV") {
            require(ReadbackAuditPolicy.authorizedAccount(queue, email, password))
            Credentials.basic(email.trim(), password)
        } else ""
        val (digest, declaredBytes) = readDestination(context, queue, entry, authorization)
        digest.sha256.takeIf {
            ReadbackAuditPolicy.matches(entry.contentSha256, it, digest.bytes, declaredBytes)
        }
    }

    private fun imageDimensions(context: Context, uri: Uri): String = try {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        }
        if (opts.outWidth > 0 && opts.outHeight > 0)
            opts.outWidth.toString() + "×" + opts.outHeight else ""
    } catch (_: Exception) { "" }

    suspend fun check(
        context: Context, queue: QueueStore.Snapshot,
        mailEmail: String = "", mailPassword: String = "",
        progress: suspend (Int, Int, String) -> Unit
    ): Result = withContext(Dispatchers.IO) {
        require(ReadbackAuditPolicy.eligible(queue)) {
            "Проверка доступна только для завершённой очереди SAF или Mail WebDAV."
        }
        if (queue.target == "Mail WebDAV") {
            require(ReadbackAuditPolicy.authorizedAccount(queue, mailEmail, mailPassword)) {
                "Укажите пароль приложения для исходного аккаунта Mail."
            }
        } else {
            require(Uri.parse(queue.root).scheme == "content") {
                "Исходная папка Android недоступна."
            }
        }
        val authorization = if (queue.target == "Mail WebDAV")
            Credentials.basic(mailEmail.trim(), mailPassword) else ""
        var matched = 0
        var different = 0
        var unavailable = 0
        var oversized = 0
        var missingDigest = 0
        val dimensions = mutableListOf<String>()
        val issues = mutableListOf<String>()
        for ((i, entry) in queue.entries.withIndex()) {
            coroutineContext.ensureActive()
            val item = entry.media
            progress(i + 1, queue.entries.size, item.name)
            try {
                require(item.kind == Kind.PHOTO) { "Проверка видео не поддерживается." }
                // NEVER re-download the origin to fabricate missing transfer-time
                // evidence. The upstream content may have changed since upload.
                val expected = ReadbackAuditPolicy.storedDigest(entry)
                if (expected == null) {
                    missingDigest++
                    if (issues.size < 12) issues.add(
                        item.name + ": нет SHA-256, сохранённого при передаче; " +
                            "файл не может быть подтверждён."
                    )
                    continue
                }
                val (destination, reportedSize, safUri) =
                    readDestination(context, queue, entry, authorization)
                if (ReadbackAuditPolicy.matches(
                        expected, destination.sha256, destination.bytes, reportedSize
                    )
                ) {
                    matched++
                    if (safUri != null && dimensions.size < 6) {
                        val size = imageDimensions(context, safUri)
                        if (size.isNotBlank()) dimensions.add(item.name + ": " + size)
                    }
                } else {
                    different++
                    if (issues.size < 12) issues.add(
                        item.name + ": SHA-256 или размер сохранённого файла не совпадает."
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: TooLarge) {
                oversized++
                if (issues.size < 12) issues.add(item.name + ": превышен лимит проверки 32 МиБ.")
            } catch (_: Exception) {
                unavailable++
                // Don't expose URLs, private provider paths or auth data in reports.
                if (issues.size < 12) issues.add(
                    item.name + ": сохранённый файл недоступен для безопасной проверки."
                )
            }
        }
        Result(queue.entries.size, matched, different, unavailable, oversized,
            dimensions, issues, queue.target, missingDigest)
    }
}
