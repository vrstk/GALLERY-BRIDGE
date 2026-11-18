package com.gallerybridge.app.update

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.UUID
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

/** Private updater bytes only. Nothing outside cacheDir/updates is ever inspected or deleted. */
internal object UpdatePartialFiles {
    private const val LEGACY_PART = "partial.apk"

    fun part(folder: File, id: UUID): File = File(folder, "$id.part")

    /**
     * Retain active WorkManager tasks (including ones rescheduled after process
     * death). Delete cancelled/replaced/orphaned parts. verified.apk is never
     * removed by cleanup; installation always re-verifies it separately.
     */
    fun cleanOrphans(folder: File, active: Set<UUID>): Int {
        if (!folder.isDirectory) return 0
        var removed = 0
        folder.listFiles()?.forEach { f ->
            if (!f.isFile) return@forEach
            val orphan = when {
                f.name == LEGACY_PART -> true
                f.name.endsWith(".part") -> {
                    val id = runCatching { UUID.fromString(f.name.removeSuffix(".part")) }
                        .getOrNull()
                    id != null && id !in active
                }
                else -> false
            }
            if (orphan && f.delete()) removed++
        }
        return removed
    }

    /**
     * Full transfer and resume both go through exactly this bounded writer.
     * Never append after an ignored/malformed Range response. On process death
     * or IO exception, a closed partial file is left for a retry of the same ID.
     */
    suspend fun receive(
        part: File,
        total: Long,
        requestedOffset: Long,
        code: Int,
        contentRange: String?,
        source: InputStream,
        onProgress: suspend (Long) -> Unit = {}
    ) {
        val action = UpdateRangePolicy.decide(requestedOffset, code, contentRange, total)
        require(part.parentFile?.isDirectory == true) { "Каталог временного файла недоступен" }
        require((if (part.isFile) part.length() else 0L) == requestedOffset) {
            "Временный файл изменился во время загрузки"
        }
        val append = action == UpdateRangePolicy.Action.APPEND
        var count = if (append) requestedOffset else 0L
        coroutineContext.ensureActive()
        FileOutputStream(part, append).use { out ->
            val buffer = ByteArray(65536)
            while (true) {
                coroutineContext.ensureActive()
                val n = source.read(buffer)
                if (n == -1) break
                if (n == 0) continue
                require(count + n <= total) { "Размер APK превышен" }
                out.write(buffer, 0, n)
                count += n
                onProgress(count)
            }
            out.flush()
        }
        coroutineContext.ensureActive()
        if (count != total) throw IOException("Неполная загрузка APK")
    }
}
