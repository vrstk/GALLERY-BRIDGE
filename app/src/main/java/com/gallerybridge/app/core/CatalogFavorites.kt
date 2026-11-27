package com.gallerybridge.app.core

import android.content.Context

/** Local labels, not person identities. No catalog or queue writes. */
internal object CatalogFavorites {
    private const val PREFS = "gallery_bridge_model_favorites_v1"
    private const val KEY = "keys"
    internal const val LIMIT = 1500

    fun canFavorite(key: String): Boolean =
        key.isNotBlank() && key != GalleryCatalogModels.key("") &&
            key == GalleryCatalogModels.key(key)

    internal fun change(before: Set<String>, key: String, selected: Boolean): Set<String> {
        require(canFavorite(key)) { "Неизвестное имя модели." }
        if (!selected) return before - key
        require(key in before || before.size < LIMIT) { "Достигнут лимит избранного." }
        return before + key
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized fun load(context: Context): Set<String> =
        prefs(context).getStringSet(KEY, emptySet()).orEmpty().toSet()

    @Synchronized fun set(context: Context, key: String, selected: Boolean): Set<String> {
        val before = load(context)
        val after = change(before, key, selected)
        if (after != before) check(prefs(context).edit().putStringSet(KEY, after).commit()) {
            "Не удалось сохранить избранное."
        }
        return after
    }
}
