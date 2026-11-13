package com.gallerybridge.app.core

enum class Kind { PHOTO, VIDEO }
data class Media(val url: String, val name: String, val kind: Kind, val source: String)
data class Scan(val title: String, val date: String, val media: List<Media>)

object Paths {
    fun clean(s: String): String = s.replace(Regex("[\\u0000-\\u001f/\\\\<>:\"|?*]"), " ").replace(Regex("\\s+"), " ").trim(' ', '.').take(130).ifBlank { "Без названия" }
    fun folders(model: String, date: String, title: String, kind: Kind): List<String> =
        listOf("♀️ " + clean(model), if (kind == Kind.PHOTO) "📷 Photo" else "🎬 Video", "🍓 " + (date.takeIf { Regex("\\d{4}-\\d{2}-\\d{2}").matches(it) } ?: "Без даты") + " — " + clean(title))
}
