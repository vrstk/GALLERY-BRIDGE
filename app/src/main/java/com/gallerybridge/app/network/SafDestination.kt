package com.gallerybridge.app.network

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.gallerybridge.app.core.Paths
import java.io.File
import java.security.MessageDigest

/**
 * Stable Android Storage Access Framework (SAF) folder IDs.
 *
 * Some cloud providers do not immediately list newly created folders.
 * Persist exact document IDs and never interpret a failed listing as an
 * empty directory. This prevents recreating the same path after Resume.
 */
object SafDestination {
    private const val PREF = "saf_stable_folder_ids_v1"
    private const val PENDING = "#CREATING"
    private const val DIRECTORY = DocumentsContract.Document.MIME_TYPE_DIR

    private data class Child(val id: String, val name: String, val mime: String)
    private fun store(ctx: Context) = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    private fun cacheKey(root: String, names: List<String>): String {
        val data = (root + "\n" + names.joinToString("/")).toByteArray(Charsets.UTF_8)
        return MessageDigest.getInstance("SHA-256").digest(data).joinToString("") {
            "%02x".format(it.toInt() and 255)
        }
    }

    private fun documentUri(tree: Uri, id: String): Uri =
        DocumentsContract.buildDocumentUriUsingTree(tree, id)

    private fun metadata(ctx: Context, tree: Uri, id: String): Child? {
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )
        val cursor = ctx.contentResolver.query(documentUri(tree, id), projection, null, null, null)
            ?: error("Провайдер Android не отвечает. Новые папки не создавались.")
        return cursor.use { c ->
            if (!c.moveToFirst()) null
            else Child(c.getString(0), c.getString(1), c.getString(2))
        }
    }

    private fun children(ctx: Context, tree: Uri, parentId: String): List<Child> {
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        val cursor = ctx.contentResolver.query(uri, projection, null, null, null)
            ?: error("Не удалось проверить существующие файлы. Создание заблокировано.")
        return cursor.use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(Child(c.getString(0), c.getString(1), c.getString(2)))
                }
            }
        }
    }

    fun resolveFolder(ctx: Context, rootUri: String, folders: List<String>): Uri {
        val tree = Uri.parse(rootUri)
        require(tree.scheme == "content") { "Выбери папку через системный проводник Android" }
        val rootId = DocumentsContract.getTreeDocumentId(tree)
        require(metadata(ctx, tree, rootId)?.mime == DIRECTORY) {
            "Корневая папка недоступна"
        }
        var parentId = rootId
        val parts = mutableListOf<String>()
        for (part in folders) {
            val name = Paths.clean(part)
            parts += name
            val key = cacheKey(rootUri, parts)
            val cached = store(ctx).getString(key, null)

            if (cached != null && cached != PENDING) {
                val item = metadata(ctx, tree, cached)
                require(item?.name == name && item.mime == DIRECTORY) {
                    "Сохранённая папка " + name + " недоступна; новый дубль не создаётся"
                }
                parentId = cached
                continue
            }

            val candidates = children(ctx, tree, parentId).filter { it.name == name }
            require(candidates.size <= 1) {
                "Обнаружены одноимённые папки (" + candidates.size + "): " +
                    name + ". Исправь дубли перед новой загрузкой."
            }
            if (candidates.size == 1) {
                val selected = candidates.single()
                require(selected.mime == DIRECTORY) {
                    "Имя " + name + " занято файлом"
                }
                check(store(ctx).edit().putString(key, selected.id).commit())
                parentId = selected.id
                continue
            }
            require(cached != PENDING) {
                "Результат прежнего создания папки " + name +
                    " неизвестен. Дождись синхронизации перед новой попыткой."
            }
            // Persist BEFORE non-idempotent cloud create: on a crash, don't recreate.
            check(store(ctx).edit().putString(key, PENDING).commit())
            val created = DocumentsContract.createDocument(
                ctx.contentResolver, documentUri(tree, parentId), DIRECTORY, name
            ) ?: error("Провайдер не создал папку " + name)
            val id = DocumentsContract.getDocumentId(created)
            val actual = metadata(ctx, tree, id)
            require(actual?.name == name && actual.mime == DIRECTORY) {
                "Провайдер изменил имя папки " + name + "; передача остановлена"
            }
            check(store(ctx).edit().putString(key, id).commit())
            parentId = id
        }
        return documentUri(tree, parentId)
    }

    fun uploadFile(ctx: Context, folder: Uri, filename: String, local: File, mime: String): Boolean {
        val name = Paths.clean(filename)
        val folderId = DocumentsContract.getDocumentId(folder)
        val matches = children(ctx, folder, folderId).filter { it.name == name }
        require(matches.size <= 1) { "В папке уже есть дубли файла " + name }
        if (matches.size == 1) {
            require(matches.single().mime != DIRECTORY) { name + " — это папка, а не файл" }
            return false
        }
        val created = DocumentsContract.createDocument(
            ctx.contentResolver, folder, mime, name
        ) ?: error("Не удалось создать " + name)
        try {
            val output = ctx.contentResolver.openOutputStream(created, "w")
                ?: error("Нет потока записи для " + name)
            output.use { stream ->
                local.inputStream().use { input -> input.copyTo(stream) }
            }
            val actual = metadata(ctx, folder, DocumentsContract.getDocumentId(created))
            require(actual?.name == name) {
                "Имя файла изменено провайдером; ожидалось " + name
            }
        } catch (e: Exception) {
            try { DocumentsContract.deleteDocument(ctx.contentResolver, created) } catch (_: Exception) {}
            throw e
        }
        // Accepted by provider, but not necessarily synced to the remote server.
        return true
    }
}
