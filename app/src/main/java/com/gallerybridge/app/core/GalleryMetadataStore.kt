package com.gallerybridge.app.core

import android.content.Context
import org.json.JSONObject
import java.net.URI
import java.security.MessageDigest
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.Locale

data class GalleryFields(val model: String, val title: String, val date: String)

/** Uses the actual gallery URL as identity, never a title or model name. */
object GalleryKeys {
    fun canonical(raw: String): String? {
        val uri = runCatching { URI(raw.trim()).normalize() }.getOrNull() ?: return null
        if (!uri.scheme.equals("https", ignoreCase = true)) return null
        val host = uri.host?.lowercase(Locale.ROOT)?.takeIf { it.isNotBlank() } ?: return null
        val port = if (uri.port < 0 || uri.port == 443) "" else ":" + uri.port
        val path = uri.rawPath.orEmpty().ifEmpty { "/" }.trimEnd('/').ifEmpty { "/" }
        val query = uri.rawQuery?.let { "?" + it }.orEmpty()
        return "https://" + host + port + path + query
    }

    fun same(first: String, second: String): Boolean {
        val one = canonical(first)
        return one != null && one == canonical(second)
    }

    fun validDate(raw: String): Boolean {
        if (raw.isBlank()) return true
        if (!Regex("""(?:19|20)\d{2}-\d{2}-\d{2}""").matches(raw)) return false
        return try {
            LocalDate.parse(raw)
            true
        } catch (_: DateTimeParseException) {
            false
        }
    }

    fun key(raw: String): String? {
        val url = canonical(raw) ?: return null
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(url.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}

/**
 * Stores only explicitly approved metadata; passwords, media URLs, and file bodies are not stored.
 * Each entry is keyed by a SHA-256 hash of the gallery URL.
 */
object GalleryMetadataStore {
    private const val PREF_NAME = "gallery_metadata_v1"

    fun load(context: Context, url: String): GalleryFields? {
        val key = GalleryKeys.key(url) ?: return null
        val raw = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(key, null) ?: return null
        return runCatching {
            val json = JSONObject(raw)
            GalleryFields(
                json.optString("model").take(130),
                json.optString("title").take(250),
                json.optString("date").take(10)
            )
        }.getOrNull()
    }

    fun save(context: Context, url: String, fields: GalleryFields) {
        val key = GalleryKeys.key(url) ?: error("Нужна корректная HTTPS-ссылка")
        require(fields.model.isNotBlank() && fields.title.isNotBlank()) {
            "Укажи имя модели и название галереи"
        }
        require(GalleryKeys.validDate(fields.date.trim())) {
            "Дата должна быть реальной и иметь формат YYYY-MM-DD"
        }
        val json = JSONObject()
            .put("model", fields.model.trim().take(130))
            .put("title", fields.title.trim().take(250))
            .put("date", fields.date.trim())
        check(context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putString(key, json.toString()).commit()) {
            "Не удалось сохранить данные галереи на устройстве"
        }
    }
}
