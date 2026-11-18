package com.gallerybridge.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
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

    @Test fun publicationCardShowsOnlySavedLabelsAndDate() {
        val entry = CatalogGallery(
            "https://example.com/gallery/real", "Модель из каталога",
            "Название публикации", "2026-10-12", 10L
        )
        compose.setContent {
            MaterialTheme {
                GalleryCatalog(
                    entries = listOf(entry), queue = null,
                    onBatchReview = {}, onQueue = {}, onOpen = {},
                    onRemove = {}, onEdit = { _, _ -> true },
                    onExport = {}, onImport = {}, backupStatus = "", historyCount = 0
                )
            }
        }
        compose.onNodeWithText("Модель из каталога").performScrollTo().assertIsDisplayed()
        // The clickable card merges descendant semantics. The decorative preview
        // is intentionally unlabelled, so inspect the unmerged tree instead.
        compose.onNodeWithTag("catalog-publication-preview", useUnmergedTree = true)
            .assertExists()
        compose.onNodeWithText("Модель из каталога").assertIsDisplayed()
        compose.onNodeWithText("Название публикации").assertIsDisplayed()
        compose.onNodeWithText("2026-10-12").assertIsDisplayed()
        compose.onNodeWithText("Сохранена ссылка").assertIsDisplayed()
    }

    @Test fun overflowKeepsDestructiveActionBehindConfirmation() {
        val entry = CatalogGallery("https://example.com/gallery/one", "Модель",
            "Съёмка", "", 1L)
        var removed = false
        compose.setContent {
            MaterialTheme {
                GalleryCatalog(
                    entries = listOf(entry), queue = null,
                    onBatchReview = {}, onQueue = {}, onOpen = {},
                    onRemove = { removed = true },
                    onEdit = { _: CatalogGallery, _: GalleryFields -> true },
                    onExport = {}, onImport = {},
                    backupStatus = "", historyCount = 0
                )
            }
        }
        compose.onNodeWithContentDescription("Действия с публикацией")
            .performScrollTo().performClick()
        compose.onNodeWithText("Убрать").performClick()
        compose.onNodeWithText("Убрать галерею из каталога?").assertIsDisplayed()
        compose.runOnIdle { assertTrue(!removed) }
        compose.onNodeWithText("Отмена").performClick()
        compose.runOnIdle { assertTrue(!removed) }
    }

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

        compose.onNodeWithText("Фильтры").performClick()
        compose.onNodeWithText("Требуют внимания").performClick()
        compose.onNodeWithText("Показано: 1 из 2", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Требует проверки").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Очередь: обработано 0/1; требуют проверки 1")
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Очередь").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(openedQueue) }
        compose.onNodeWithText("В очереди").performClick()
        compose.onNodeWithText("Показано: 1 из 2", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Требует проверки").performScrollTo().assertIsDisplayed()
    }
}
