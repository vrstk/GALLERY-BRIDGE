package com.gallerybridge.app.core

enum class Kind { PHOTO, VIDEO }
data class Media(val url: String, val name: String, val kind: Kind, val source: String)
data class Scan(val title: String, val date: String?, val files: List<Media>)

object Paths {
    fun safe(s: String): String = s.trim()
        .replace(Regex("[\\u0000-\\u001F/\\\\<>:\"|?*]+"), " ")
        .replace(Regex("\\s+"), " ").trim('.', ' ').take(140).ifBlank { "Без названия" }
    fun folder(model: String, kind: Kind, date: String, title: String) = listOf(
        "♀️ " + safe(model),
        if (kind == Kind.PHOTO) "📷 Photo" else "🎬 Video",
        "🍓 " + (date.trim().takeIf { Regex("\\d{4}-\\d{2}-\\d{2}").matches(it) } ?: "Без даты") + " — " + safe(title)
    )
}
