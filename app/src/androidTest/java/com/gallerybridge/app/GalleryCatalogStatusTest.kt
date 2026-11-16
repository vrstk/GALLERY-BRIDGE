package com.gallerybridge.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.gallerybridge.app.core.CatalogGallery
import com.gallerybridge.app.core.GalleryFields
import com.gallerybridge.app.core.Kind
import com.gallerybridge.app.core.Media
import com.gallerybridge.app.transfer.QueueStore
import com.gallerybridge.app.ui.GalleryCatalog
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Pure UI test: does not modify persisted queue, history or cloud contents. */
class GalleryCatalogStatusTest {
    @get:Rule val compose = createComposeRule()

    @Test fun reviewFilterShowsBlockedGalleryAndQueueNavigation() {
        val blocked = CatalogGallery("https://example.com/gallery/blocked", "Модель B",
            "Требует осмотра", "", 3)
        val processed = CatalogGallery("https://example.com/gallery/done", "Модель A",
            "Уже обработана", "", 2, "Mail WebDAV", "Завершена", 2, 2)
        val media = Media("https://example.com/image.jpg", "image.jpg", Kind.PHOTO, "Тест")
        val queue = QueueStore.Snapshot(42, "Модель B", "Требует осмотра", "",
            "Mail WebDAV", "Models", "",
            listOf(QueueStore.Entry(media, "BLOCKED", "Неясный результат записи")),
            blocked.url)
        var openedQueue = false
        compose.setContent {
            MaterialTheme {
                GalleryCatalog(
                    entries = listOf(blocked, processed), queue = queue,
                    onBatchReview = {},
                    onQueue = { openedQueue = true },
                    onOpen = {}, onRemove = {},
                    onEdit = { _: CatalogGallery, _: GalleryFields -> true },
                    onExport = {}, onImport = {},
                    backupStatus = "", historyCount = 0
                )
            }
        }

        compose.onNodeWithText("Требуют внимания").performClick()
        compose.onNodeWithText("Показано: 1 из 2").assertIsDisplayed()
        compose.onNodeWithText("Требует проверки").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("К очереди").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(openedQueue) }
        compose.onNodeWithText("В очереди").performClick()
        compose.onNodeWithText("Показано: 1 из 2").assertIsDisplayed()
        compose.onNodeWithText("Требует проверки").performScrollTo().assertIsDisplayed()
    }
}
