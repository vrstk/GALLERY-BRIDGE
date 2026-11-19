package com.gallerybridge.app.core

import org.junit.Assert.*
import org.junit.Test

class ModelBatchQueuePlanTest {
    private fun gallery(id: String, name: String = "Same Title") = PreparedGallery(
        "https://www.elitebabes.com/gallery-$id/", id, "Alisa",
        name, "2019-01-02",
        listOf(Media("https://cdn.elitebabes.com/photo-$id.jpg", "photo.jpg", Kind.PHOTO, "Ссылка")))

    @Test fun sameTitlesProduceDifferentFolders() {
        val first = gallery("100")
        val second = gallery("200")
        assertEquals(2, ModelBatchQueuePlan.validate(listOf(first, second)).size)
        assertNotEquals(ModelBatchQueuePlan.folder(first, Kind.PHOTO),
            ModelBatchQueuePlan.folder(second, Kind.PHOTO))
        assertEquals("♀️ Alisa", ModelBatchQueuePlan.folder(first, Kind.PHOTO).first())
    }

    @Test fun repeatedUrlIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            ModelBatchQueuePlan.validate(listOf(gallery("100"), gallery("100")))
        }
    }

    @Test fun videosAndEmptyMediaCannotEnterPhotoQueue() {
        assertThrows(IllegalArgumentException::class.java) {
            ModelBatchQueuePlan.validate(listOf(gallery("100").copy(media = emptyList())))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ModelBatchQueuePlan.validate(listOf(gallery("100").copy(
                media = listOf(Media("https://cdn.elitebabes.com/video.mp4",
                    "video.mp4", Kind.VIDEO, "Видео")))))
        }
    }

    @Test fun unknownIdUsesStableUrlHashAndNeverTitle() {
        val withoutId = gallery("100").copy(sourceId = "", title = "")
        val path = ModelBatchQueuePlan.folder(withoutId, Kind.PHOTO)
        assertTrue(path.last().contains("["))
        assertTrue(path.last().contains("Без названия"))
    }
}
