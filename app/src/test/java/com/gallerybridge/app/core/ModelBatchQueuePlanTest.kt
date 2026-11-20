package com.gallerybridge.app.core

import org.junit.Assert.*
import org.junit.Test

class ModelBatchQueuePlanTest {
    private fun gallery(id: String, title: String = "Same"): PreparedGallery =
        PreparedGallery("https://www.elitebabes.com/gallery-$id/", id,
            "Alisa", title, "2018-11-15",
            listOf(Media("https://cdn.elitebabes.com/content/$id/full.jpg",
                "full.jpg", Kind.PHOTO, "Ссылка")))

    @Test fun sameNamesInDifferentGalleriesKeepSeparateFolders() {
        val a = gallery("123")
        val b = gallery("456")
        assertEquals(2, ModelBatchQueuePlan.validate(listOf(a, b)).size)
        assertNotEquals(ModelBatchQueuePlan.folder(a, Kind.PHOTO),
            ModelBatchQueuePlan.folder(b, Kind.PHOTO))
        assertTrue(ModelBatchQueuePlan.folder(a, Kind.PHOTO).last().contains("123"))
    }

    @Test fun wholeModelFitsWhenItsPhotosFitOneQueue() {
        val galleries = (1..361).map { gallery(it.toString()) }
        assertEquals(361, ModelBatchQueuePlan.validate(galleries).size)
        ModelBatchQueuePlan.checkFileCount(10000)
        assertThrows(IllegalArgumentException::class.java) { ModelBatchQueuePlan.checkFileCount(10001) }
    }

    @Test fun oneUnavailablePageDoesNotDiscardOtherGalleries() = kotlinx.coroutines.runBlocking {
        val cards = (1..3).map {
            ModelListingSources.Publication(gallery(it.toString()).sourceUrl, "Gallery $it",
                "2018-11-15", "Alisa", sourceId = it.toString())
        }
        val result = ModelBatchQueuePlan.prepareAvailable(cards) { index, card ->
            if (index == 1) throw java.io.IOException("HTTP 404")
            gallery(card.sourceId)
        }
        assertEquals(listOf("1", "3"), result.galleries.map { it.sourceId })
        assertEquals(setOf(cards[1].url), result.skipped.keys)
    }

    @Test fun duplicateGalleryIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            ModelBatchQueuePlan.validate(listOf(gallery("123"), gallery("123")))
        }
    }

    @Test fun emptyAndVideoMediaCannotEnterPhotoBatch() {
        assertThrows(IllegalArgumentException::class.java) {
            ModelBatchQueuePlan.validate(listOf(gallery("123").copy(media = emptyList())))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ModelBatchQueuePlan.validate(listOf(gallery("123").copy(
                media = listOf(Media("https://cdn.example/clip.mp4",
                    "clip.mp4", Kind.VIDEO, "Основной плеер")))))
        }
    }

    @Test fun duplicateFilenamesWithinGalleryAreRejected() {
        val first = gallery("1")
        val files = first.media + first.media.first().copy(
            url = "https://cdn.elitebabes.com/other/full.jpg")
        assertThrows(IllegalArgumentException::class.java) {
            ModelBatchQueuePlan.validate(listOf(first.copy(media = files)))
        }
    }

    @Test fun sourceIdIsNotInferredFromTitle() {
        val source = gallery("1").copy(sourceId = "")
        assertNotEquals(ModelBatchQueuePlan.folder(source, Kind.PHOTO),
            ModelBatchQueuePlan.folder(gallery("1"), Kind.PHOTO))
    }
}
