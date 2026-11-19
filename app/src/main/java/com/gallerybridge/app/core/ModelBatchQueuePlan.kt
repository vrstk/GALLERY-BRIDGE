package com.gallerybridge.app.core

/** A gallery's media must retain its own folder identity in a mixed photo queue. */
data class PreparedGallery(
    val sourceUrl: String,
    val sourceId: String,
    val model: String,
    val title: String,
    val displayedDate: String,
    val media: List<Media>
)

object ModelBatchQueuePlan {
    const val MAX_GALLERIES = 100
    const val MAX_FILES = 10000

    fun validate(galleries: List<PreparedGallery>): List<PreparedGallery> {
        require(galleries.isNotEmpty() && galleries.size <= MAX_GALLERIES) {
            "Выберите от 1 до 100 фотогалерей."
        }
        val seen = mutableSetOf<String>()
        var total = 0
        galleries.forEach { group ->
            val url = GalleryKeys.canonical(group.sourceUrl)
            require(url != null && group.sourceUrl.startsWith("https://")) {
                "Неверная ссылка выбранной галереи."
            }
            require(seen.add(url)) { "Одна галерея выбрана повторно." }
            require(GalleryKeys.validDate(group.displayedDate)) {
                "Неверная дата карточки."
            }
            require(group.media.isNotEmpty() && group.media.all { it.kind == Kind.PHOTO }) {
                "Для выбранной галереи не найдены подтверждённые фотографии."
            }
            val names = group.media.map { Paths.clean(it.name).lowercase() }
            require(names.size == names.distinct().size) {
                "В галерее есть одинаковые имена файлов — требуется проверка."
            }
            total += group.media.size
            require(total <= MAX_FILES) { "Слишком много фотографий для одной очереди." }
        }
        return galleries
    }

    fun folder(group: PreparedGallery, kind: Kind): List<String> {
        val identity = group.sourceId.takeIf { it.matches(Regex("[0-9]{1,15}")) }
            ?: GalleryKeys.key(group.sourceUrl)?.take(12)
            ?: error("Неверный идентификатор галереи.")
        val name = group.title.ifBlank { "Без названия" }
        // The date is exactly what the model listing displays, not a publication date.
        return Paths.folders(group.model.ifBlank { "Без имени" },
            group.displayedDate, "$name [$identity]", kind)
    }
}
