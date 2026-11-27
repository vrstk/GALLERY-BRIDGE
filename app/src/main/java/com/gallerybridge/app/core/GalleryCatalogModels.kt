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
        val latestUpdate: Long get() = entries.maxOfOrNull { it.updatedAt } ?: 0L
    }

    enum class Sort { NAME, RECENT, COUNT }

    fun sorted(groups: List<Group>, sort: Sort): List<Group> {
        val knownFirst = compareBy<Group> { it.key == "\u0000" }
        val ordered = when (sort) {
            Sort.NAME -> knownFirst
            Sort.RECENT -> knownFirst.thenByDescending { it.latestUpdate }
            Sort.COUNT -> knownFirst.thenByDescending { it.count }
        }
        return groups.sortedWith(ordered.thenBy { it.label.lowercase(Locale.ROOT) }
            .thenBy { it.key })
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
        return sorted(groups.map { (id, publications) ->
            Group(id, labels.getValue(id), publications.toList())
        }, Sort.NAME)
    }

    fun select(groups: List<Group>, key: String): List<CatalogGallery> =
        groups.firstOrNull { it.key == key }?.entries.orEmpty()
}
