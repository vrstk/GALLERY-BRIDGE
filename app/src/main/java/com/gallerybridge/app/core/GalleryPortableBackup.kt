package com.gallerybridge.app.core

import com.gallerybridge.app.transfer.HistoryStore
import com.gallerybridge.app.transfer.PortableHistoryBackup
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/**
 * Versioned portable backup for user-controlled catalog and history.
 * No queue, cloud account, password, SAF permission, or photo bytes are exported.
 * Legacy catalog-only backups from 0.29 remain importable.
 */
object GalleryPortableBackup {
    private const val FORMAT = "gallery-bridge-portable"
    private const val VERSION = 1

    data class Payload(
        val galleries: List<CatalogGallery>,
        val history: List<HistoryStore.Record>,
        val darkTheme: Boolean?
    )

    private fun sha256(data: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(data.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private fun checksum(catalog: JSONObject, history: JSONArray, dark: Boolean): String =
        sha256(
            "gallery-bridge-portable|1\n" +
                catalog.getJSONArray("galleries").toString() + "\n" +
                history.toString() + "\n" + dark.toString()
        )

    fun export(
        galleries: List<CatalogGallery>,
        history: List<HistoryStore.Record>,
        darkTheme: Boolean
    ): String {
        val catalog = JSONObject(GalleryCatalogBackup.export(galleries))
        val transfers = PortableHistoryBackup.encode(history)
        val payload = JSONObject()
            .put("catalog", catalog)
            .put("history", transfers)
            .put("darkTheme", darkTheme)
        val result = JSONObject()
            .put("format", FORMAT).put("version", VERSION)
            .put("payload", payload)
            .put("sha256", checksum(catalog, transfers, darkTheme))
            .toString()
        require(result.toByteArray(Charsets.UTF_8).size <= GalleryCatalogBackup.MAX_BYTES) {
            "Резервная копия превышает 2 МБ"
        }
        return result
    }

    fun parse(raw: String): Payload {
        require(raw.toByteArray(Charsets.UTF_8).size <= GalleryCatalogBackup.MAX_BYTES) {
            "Файл больше 2 МБ"
        }
        val root = JSONObject(raw)
        if (root.optString("format") == "gallery-bridge-catalog") {
            return Payload(GalleryCatalogBackup.parse(raw), emptyList(), null)
        }
        require(root.optString("format") == FORMAT) { "Неизвестный формат резервной копии" }
        require(root.getInt("version") == VERSION) { "Неподдерживаемая версия копии" }
        val payload = root.getJSONObject("payload")
        val catalog = payload.getJSONObject("catalog")
        val history = payload.getJSONArray("history")
        val dark = payload.getBoolean("darkTheme")
        require(root.getString("sha256") == checksum(catalog, history, dark)) {
            "Не совпадает контрольная сумма резервной копии"
        }
        return Payload(
            GalleryCatalogBackup.parse(catalog.toString()),
            PortableHistoryBackup.decode(history),
            dark
        )
    }
}
