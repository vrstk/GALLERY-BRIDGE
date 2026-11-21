package com.gallerybridge.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollTo
import com.gallerybridge.app.core.CatalogGallery
import com.gallerybridge.app.ui.GalleryCatalog
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** UI-only: model browsing must not save records or start transfers. */
class GalleryCatalogModelsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun browseModelsSelectAndReturnWithoutChangingCatalog() {
        val first = CatalogGallery("https://example.com/a", "Leona Mia", "A", "", 20)
        val second = CatalogGallery("https://example.com/b", "leona mia", "B", "", 19)
        val third = CatalogGallery("https://example.com/c", "Alba", "C", "", 18)
        val entries = listOf(first, second, third)
        var opened: CatalogGallery? = null
        compose.setContent {
            MaterialTheme {
                GalleryCatalog(
                    entries = entries, queue = null,
                    onQueue = {}, onBatchReview = {}, onOpen = { opened = it },
                    onRemove = {}, onEdit = { _, _ -> true },
                    onExport = {}, onImport = {}, backupStatus = "", historyCount = 0
                )
            }
        }
        compose.onNodeWithTag("catalog-model-mode").performClick()
        compose.onNodeWithText("Моделей: 2", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Leona Mia").performScrollTo().performClick()
        compose.onNodeWithText("A").assertIsDisplayed()
        compose.onNodeWithText("B").assertIsDisplayed()
        compose.onNodeWithText("Все модели").performClick()
        compose.onNodeWithText("Alba").performScrollTo().assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(null, opened)
            assertEquals(listOf(first, second, third), entries)
        }
    }

    @Test fun searchFiltersModelGroupsWithoutMutatingEntries() {
        val first = CatalogGallery("https://example.com/one", "Leona Mia", "Только здесь", "", 3)
        val second = CatalogGallery("https://example.com/two", "Alba", "Другая", "", 2)
        compose.setContent {
            MaterialTheme {
                GalleryCatalog(
                    entries = listOf(first, second), queue = null,
                    onQueue = {}, onBatchReview = {}, onOpen = {},
                    onRemove = {}, onEdit = { _, _ -> true },
                    onExport = {}, onImport = {}, backupStatus = "", historyCount = 0
                )
            }
        }
        compose.onNodeWithTag("catalog-model-mode").performClick()
        compose.onNodeWithTag("catalog-search").performTextInput("Leona")
        compose.onNodeWithText("Моделей: 1", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Leona Mia").performScrollTo().assertIsDisplayed()
    }
}
