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
    const val MAX_FILES = 10000

    data class Preparation(
        val galleries: List<PreparedGallery>,
        val skipped: Map<String, String>,
        val needsReview: Map<String, String> = emptyMap()
    ) {
        val photoCount: Int get() = galleries.sumOf { it.media.size }
    }

    /** Ephemeral preparation; the durable queue is created only after confirmation. */
    data class Preview(
        val selectedUrls: Set<String>, val target: String, val root: String,
        val account: String, val previousQueueId: Long?,
        val preparation: Preparation
    )

    class ReviewRequired(message: String) : IllegalStateException(message)

    suspend fun prepareAvailable(
        publications: List<ModelListingSources.Publication>,
        prepare: suspend (Int, ModelListingSources.Publication) -> PreparedGallery
    ): Preparation {
        val ready = mutableListOf<PreparedGallery>()
        val skipped = linkedMapOf<String, String>()
        val needsReview = linkedMapOf<String, String>()
        var files = 0
        for ((index, publication) in publications.withIndex()) {
            val gallery = try {
                prepare(index, publication).also { validate(listOf(it)) }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (review: ReviewRequired) {
                needsReview[publication.url] = publication.title.ifBlank { publication.sourceId } +
                    " — " + (review.message ?: "требуется проверка").take(180)
                continue
            } catch (failure: Exception) {
                skipped[publication.url] = publication.title.ifBlank { publication.sourceId } +
                    " — " + (failure.message ?: "ошибка страницы").take(180)
                continue
            }
            files += gallery.media.size
            checkFileCount(files)
            ready += gallery
        }
        return Preparation(ready, skipped, needsReview)
    }

    fun checkFileCount(count: Int) {
        require(count <= MAX_FILES) {
            "В одной очереди можно передать до $MAX_FILES фотографий. Выберите меньше галерей."
        }
    }

    fun validate(galleries: List<PreparedGallery>): List<PreparedGallery> {
        require(galleries.isNotEmpty()) {
            "Выберите хотя бы одну фотогалерею."
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
            checkFileCount(total)
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
