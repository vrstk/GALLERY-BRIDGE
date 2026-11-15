package com.gallerybridge.app.network

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile

/**
 * Persisted Android SAF folder permissions are independent from a saved URI string.
 * Cloud providers may revoke access or disappear between launches.
 */
object SafFolderAccess {
    internal data class Grant(val uri: String, val read: Boolean, val write: Boolean)
    data class Status(val ready: Boolean, val name: String, val message: String)

    internal fun hasReadWriteGrant(raw: String, grants: List<Grant>): Boolean =
        raw.isNotBlank() && grants.any { it.uri == raw && it.read && it.write }

    fun inspect(context: Context, raw: String): Status {
        if (raw.isBlank()) {
            return Status(false, "Папка не выбрана", "Выберите папку Android или облачного провайдера.")
        }
        return try {
            val tree = Uri.parse(raw)
            if (tree.scheme != "content" || !DocumentsContract.isTreeUri(tree)) {
                return Status(false, "Папка недоступна", "Выберите папку через системный проводник Android.")
            }
            val grants = context.contentResolver.persistedUriPermissions.map {
                Grant(it.uri.toString(), it.isReadPermission, it.isWritePermission)
            }
            if (!hasReadWriteGrant(raw, grants)) {
                return Status(false, "Доступ отозван", "Разрешение Android отозвано. Выберите прежнюю папку снова.")
            }
            val folder = DocumentFile.fromTreeUri(context, tree)
            if (folder?.isDirectory != true || !folder.canRead() || !folder.canWrite()) {
                Status(false, "Папка недоступна",
                    "Провайдер папки не подтверждает чтение и запись. Проверьте облачное приложение и выберите папку снова.")
            } else {
                Status(true, folder.name?.takeIf { it.isNotBlank() } ?: "Выбранная папка", "")
            }
        } catch (_: Exception) {
            Status(false, "Папка недоступна",
                "Не удалось получить доступ к папке. Проверьте облачное приложение и выберите папку снова.")
        }
    }
}
