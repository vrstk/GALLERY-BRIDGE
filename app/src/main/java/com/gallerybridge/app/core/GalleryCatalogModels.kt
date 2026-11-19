package com.gallerybridge.app.core

import java.util.Locale

/**
 * Read-only navigation index over existing catalog records.
 * Model names are display metadata, not verified person identities.
 * Entries, URLs, progress, and their stored order are never rewritten.
 */
internal object GalleryCatalogModels {
    private val whitespace = Regex("\\s+")

    data class Group(
        val key: String,
        val label: String,
        val entries: List<CatalogGallery>
    ) {
        val count: Int get() = entries.size
    }

    fun key(model: String): String {
        val normalized = model.trim().replace(whitespace, " ")
        return if (normalized.isEmpty()) "\u0000" else normalized.lowercase(Locale.ROOT)
    }

    fun group(entries: List<CatalogGallery>): List<Group> {
        val groups = linkedMapOf<String, MutableList<CatalogGallery>>()
        val labels = linkedMapOf<String, String>()
        entries.forEach { entry ->
            val name = entry.model.trim().replace(whitespace, " ")
            val key = key(entry.model)
            labels.putIfAbsent(key, name.ifEmpty { "Без имени" })
            groups.getOrPut(key) { mutableListOf() }.add(entry)
        }
        return groups.map { (id, publications) ->
            Group(id, labels.getValue(id), publications.toList())
        }.sortedWith(compareBy<Group> { it.key == "\u0000" }
            .thenBy { it.label.lowercase(Locale.ROOT) }
            .thenBy { it.key })
    }

    fun select(groups: List<Group>, key: String): List<CatalogGallery> =
        groups.firstOrNull { it.key == key }?.entries.orEmpty()
}
