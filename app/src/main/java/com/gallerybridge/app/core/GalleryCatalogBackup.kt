package com.gallerybridge.app.core

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest

/**
 * Portable catalog-only JSON. No passwords, account names, queue snapshots,
 * local file content or storage grants are included in this format.
 */
object GalleryCatalogBackup {
    private const val FORMAT = "gallery-bridge-catalog"
    private const val VERSION = 1
    const val MAX_BYTES = 2 * 1024 * 1024

    data class Plan(
        val records: List<CatalogGallery>,
        val added: Int,
        val alreadyPresent: Int,
        val noSpace: Int
    )

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    fun export(entries: List<CatalogGallery>): String {
        require(entries.size <= GalleryCatalogStore.LIMIT) { "Каталог слишком большой" }
        val arrayText = GalleryCatalogStore.encode(entries)
        return JSONObject().put("format", FORMAT).put("version", VERSION)
            .put("count", entries.size).put("sha256", sha256(arrayText))
            .put("galleries", JSONArray(arrayText))
            .toString()
    }

    /** Reject corrupt and partially valid files rather than silently losing entries. */
    fun parse(raw: String): List<CatalogGallery> {
        require(raw.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "Файл больше 2 МБ" }
        val document = JSONObject(raw)
        require(document.optString("format") == FORMAT) { "Это не резервная копия каталога GALLERY BRIDGE" }
        require(document.optInt("version", -1) == VERSION) { "Неподдерживаемая версия резервной копии" }
        val array = document.getJSONArray("galleries")
        val count = array.length()
        require(count <= GalleryCatalogStore.LIMIT) { "Слишком много записей каталога" }
        require(document.optInt("count", -1) == count) { "Количество записей не совпадает" }
        val payload = array.toString()
        require(document.optString("sha256") == sha256(payload)) { "Контрольная сумма не совпадает" }
        val restored = GalleryCatalogStore.decode(payload)
        require(restored.size == count) { "Найдены повреждённые или повторяющиеся записи" }
        return restored
    }

    /** Never overwrite newer labels or completed transfers in an existing installation. */
    fun plan(current: List<CatalogGallery>, incoming: List<CatalogGallery>): Plan {
        val existingUrls = current.mapTo(HashSet()) { it.url }
        val missing = incoming.filter { it.url !in existingUrls }
        val free = (GalleryCatalogStore.LIMIT - current.size).coerceAtLeast(0)
        val added = missing.take(free)
        return Plan(
            records = (current + added).sortedWith(
                compareByDescending<CatalogGallery> { it.updatedAt }.thenBy { it.url }
            ),
            added = added.size,
            alreadyPresent = incoming.size - missing.size,
            noSpace = missing.size - added.size
        )
    }

    /** Bounded stream read: reject unexpectedly large or malformed downloads. */
    fun readLimited(input: InputStream): String {
        val output = ByteArrayOutputStream()
        val block = ByteArray(8192)
        while (true) {
            val read = input.read(block)
            if (read == -1) break
            if (read == 0) continue
            require(output.size() + read <= MAX_BYTES) { "Файл больше 2 МБ" }
            output.write(block, 0, read)
        }
        return output.toString(Charsets.UTF_8.name())
    }
}
