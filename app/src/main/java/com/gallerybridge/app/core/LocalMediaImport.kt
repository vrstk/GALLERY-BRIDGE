package com.gallerybridge.app.core

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

/**
 * Local media selected with ACTION_OPEN_DOCUMENT. The picker provides the
 * permission, and the Activity persists it for resumable background transfers.
 * We store content:// URIs, never filesystem paths or browser cookies.
 */
object LocalMediaImport {
    const val SOURCE = "Локальный файл"
    const val MAX_FILES = 50

    fun kindFor(mimeType: String?, name: String): Kind? {
        val mime = mimeType.orEmpty().substringBefore(';').trim().lowercase()
        val extension = name.substringAfterLast('.', "").lowercase()
        return when {
            mime.startsWith("video/") -> Kind.VIDEO
            mime.startsWith("image/") -> Kind.PHOTO
            mime.isNotEmpty() && mime != "application/octet-stream" -> null
            extension in setOf("mp4", "m4v", "mov", "webm") -> Kind.VIDEO
            extension in setOf("jpg", "jpeg", "png", "webp", "gif", "avif", "bmp") -> Kind.PHOTO
            else -> null
        }
    }

    fun fromDocument(context: Context, uri: Uri): Media? {
        if (uri.scheme != "content") return null
        val resolver = context.contentResolver
        val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (column >= 0 && cursor.moveToFirst()) cursor.getString(column) else null
            }?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment?.substringAfterLast('/')
            ?: "Локальный файл"
        val kind = kindFor(resolver.getType(uri), name) ?: return null
        return Media(uri.toString(), Paths.clean(name), kind, SOURCE)
    }
}
