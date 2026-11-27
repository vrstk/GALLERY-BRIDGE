package com.gallerybridge.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import com.gallerybridge.app.core.CatalogGallery
import com.gallerybridge.app.core.GalleryFields
import com.gallerybridge.app.ui.GalleryCatalog
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** No actual downloads, permissions, credentials, queue storage or cloud access. */
class GalleryCatalogBatchTest {
    @get:Rule val compose = createComposeRule()

    @Test fun onlyFreshGalleriesArePlannedAfterExplicitConfirmation() {
        val fresh1 = CatalogGallery("https://example.com/new/1", "A", "First", "", 10)
        val fresh2 = CatalogGallery("https://example.com/new/2", "B", "Second", "", 11)
        val completed = CatalogGallery("https://example.com/done", "C", "Old", "", 12,
            "Mail WebDAV", "Завершено", 10, 10)
        var opened = emptyList<String>()
        compose.setContent {
            MaterialTheme {
                GalleryCatalog(
                    entries = listOf(fresh1, fresh2, completed),
                    queue = null, onQueue = {},
                    onBatchReview = { opened = it },
                    onOpen = {}, onRemove = {},
                    onEdit = { _: CatalogGallery, _: GalleryFields -> true },
                    onExport = {}, onImport = {}, backupStatus = "", historyCount = 0
                )
            }
        }
        compose.onNodeWithText("Пакетный просмотр").performClick()
        compose.onNodeWithText("Добавить из списка").performClick()
        compose.onNodeWithText("Просмотреть (2)").performClick()
        compose.onNodeWithText("Начать просмотр публикаций?").assertIsDisplayed()
        compose.runOnIdle { assertEquals(emptyList<String>(), opened) }
        compose.onNodeWithText("Отмена").performClick()
        compose.runOnIdle { assertEquals(emptyList<String>(), opened) }
        compose.onNodeWithText("Просмотреть (2)").performClick()
        compose.onNodeWithText("Начать просмотр").performClick()
        compose.runOnIdle { assertEquals(listOf(fresh2.url, fresh1.url), opened) }
    }
    @Test fun addingFilteredGalleriesKeepsSelectionsOutsideTheCurrentSearch() {
        val first = CatalogGallery("https://example.com/new/one", "Alba", "First", "", 1)
        val second = CatalogGallery("https://example.com/new/two", "Leona Mia", "Second", "", 2)
        val third = CatalogGallery("https://example.com/new/three", "Leona Mia", "Third", "", 3)
        var opened = emptyList<String>()
        compose.setContent {
            MaterialTheme {
                GalleryCatalog(
                    entries = listOf(first, second, third), queue = null, onQueue = {},
                    onBatchReview = { opened = it }, onOpen = {}, onRemove = {},
                    onEdit = { _, _ -> true }, onExport = {}, onImport = {},
                    backupStatus = "", historyCount = 0
                )
            }
        }
        compose.onNodeWithText("Пакетный просмотр").performClick()
        compose.onNodeWithTag("catalog-search").performTextReplacement("First")
        compose.onNodeWithText("Добавить из списка").performScrollTo().performClick()
        compose.onNodeWithText("Просмотреть (1)").assertIsDisplayed()

        compose.onNodeWithTag("catalog-search").performScrollTo().performTextReplacement("Second")
        compose.onNodeWithText("Вне текущего списка: 1").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Добавить из списка").performScrollTo().performClick()
        compose.onNodeWithText("Просмотреть (2)").performScrollTo().performClick()
        compose.onNodeWithText("Начать просмотр").performClick()
        compose.runOnIdle { assertEquals(listOf(first.url, second.url), opened) }
    }

}
