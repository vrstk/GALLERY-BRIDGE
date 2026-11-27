package com.gallerybridge.app.network

import org.json.JSONObject

/**
 * Read-only interpretation of a Google Drive file/folder listing.
 *
 * Uncertain results must stop a transfer rather than silently skip an
 * incomplete/empty file or create another folder with the same name.
 * Existing remote files are never removed or overwritten by this policy.
 */
object CloudListingPolicy {
    class UnsafeListing(message: String) : IllegalStateException(message)

    const val FOLDER_MIME = "application/vnd.google-apps.folder"
    const val FIELDS = "nextPageToken,incompleteSearch,files(id,name,mimeType,size)"

    fun searchQuery(parent: String, name: String, folderOnly: Boolean): String {
        fun quote(value: String) =
            value.replace("\\", "\\\\").replace("'", "\\'")
        val type = if (folderOnly) " and mimeType = '$FOLDER_MIME'" else ""
        return "'${quote(parent)}' in parents and name = '${quote(name)}' and trashed = false$type"
    }

    data class Entry(val id: String, val mime: String, val size: Long?)

    fun entries(json: String): List<Entry> {
        val body = JSONObject(json)
        if (body.optBoolean("incompleteSearch", false)) throw UnsafeListing(
            "Google Drive вернул неполные результаты поиска. Передача остановлена."
        )
        if (body.optString("nextPageToken").isNotBlank()) throw UnsafeListing(
            "Google Drive вернул ещё одну страницу результатов. Передача остановлена."
        )
        val files = body.getJSONArray("files")
        return buildList {
            for (i in 0 until files.length()) {
                val file = files.getJSONObject(i)
                val id = file.getString("id").takeIf { it.isNotBlank() }
                    ?: throw UnsafeListing("Google Drive вернул файл без ID")
                val size = if (file.has("size") && !file.isNull("size")) {
                    file.getLong("size").also {
                        if (it < 0L) throw UnsafeListing("Google Drive вернул неправильный размер файла")
                    }
                } else null
                add(Entry(id, file.optString("mimeType"), size))
            }
        }
    }

    fun folderIdOrNull(json: String, name: String): String? {
        val found = entries(json)
        if (found.size > 1) throw UnsafeListing(
            "В Google Drive несколько папок с именем $name. Выбери папку вручную."
        )
        val one = found.singleOrNull() ?: return null
        if (one.mime != FOLDER_MIME) throw UnsafeListing(
            "Google Drive вернул не папку для имени $name"
        )
        return one.id
    }

    fun existingFile(json: String, name: String): Boolean {
        val found = entries(json)
        if (found.size > 1) throw UnsafeListing(
            "В Google Drive несколько файлов с именем $name. Проверь дубликаты."
        )
        val one = found.singleOrNull() ?: return false
        if (one.mime == FOLDER_MIME) throw UnsafeListing(
            "Имя $name занято папкой, а не файлом."
        )
        if (one.size == 0L) throw UnsafeListing(
            "В Google Drive найден пустой файл $name (0 байт). Проверь его вручную."
        )
        // Unknown size is not a byte-for-byte integrity guarantee.
        return true
    }
}
