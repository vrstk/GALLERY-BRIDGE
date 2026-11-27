package com.gallerybridge.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
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

    @Test fun modelIndexCanChangeSortWithoutOpeningOrEditingRecords() {
        val entries = listOf(
            CatalogGallery("https://example.com/a", "Zeta", "A", "", 30),
            CatalogGallery("https://example.com/b", "Alpha", "B", "", 20),
            CatalogGallery("https://example.com/c", "Alpha", "C", "", 10)
        )
        var opened = 0
        var edited = 0
        compose.setContent {
            MaterialTheme {
                GalleryCatalog(entries = entries, queue = null,
                    onQueue = {}, onBatchReview = {}, onOpen = { opened++ },
                    onRemove = {}, onEdit = { _, _ -> edited++; true },
                    onExport = {}, onImport = {}, backupStatus = "", historyCount = 0)
            }
        }
        compose.onNodeWithTag("catalog-model-mode").performClick()
        compose.onNodeWithTag("catalog-sort-recent").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("catalog-sort-count").performScrollTo().performClick()
        compose.onNodeWithText("Alpha").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("catalog-sort-name").performScrollTo().performClick()
        compose.onNodeWithText("Zeta").performScrollTo().assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(0, opened)
            assertEquals(0, edited)
            assertEquals(3, entries.size)
        }
    }

    @Test fun favoriteFilterDoesNotOpenOrEditPublications() {
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val key = "qa model alpha"
        val original = com.gallerybridge.app.core.CatalogFavorites.load(context)
        val entries = listOf(
            CatalogGallery("https://example.com/qa-alpha", "QA Model Alpha", "A", "", 20),
            CatalogGallery("https://example.com/qa-beta", "QA Model Beta", "B", "", 10))
        var opened = 0
        try {
            com.gallerybridge.app.core.CatalogFavorites.set(context, key, false)
            compose.setContent {
                MaterialTheme {
                    GalleryCatalog(entries = entries, queue = null, onQueue = {},
                        onBatchReview = {}, onOpen = { opened++ }, onRemove = {},
                        onEdit = { _, _ -> true }, onExport = {}, onImport = {},
                        backupStatus = "", historyCount = 0)
                }
            }
            compose.onNodeWithTag("catalog-model-mode").performClick()
            compose.onNodeWithTag("catalog-favorite:" + key).performScrollTo().performClick()
            compose.onNodeWithTag("catalog-favorites-only").performScrollTo().performClick()
            compose.onNodeWithText("Моделей: 1", substring = true).assertIsDisplayed()
            compose.onNodeWithText("QA Model Alpha").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("QA Model Beta").assertDoesNotExist()
            compose.runOnIdle { assertEquals(0, opened) }
        } finally {
            com.gallerybridge.app.core.CatalogFavorites.set(context, key, key in original)
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
    @Test fun modelSearchAndStatusFiltersKeepTheCurrentModelAndPreviousSearch() {
        val recent = CatalogGallery("https://example.com/mia-a", "Leona Mia", "Открытая съёмка", "", 3)
        val older = CatalogGallery("https://example.com/mia-b", "Leona Mia", "Архивная съёмка", "", 2)
        val other = CatalogGallery("https://example.com/alba", "Alba", "Архивная съёмка", "", 1)
        compose.setContent {
            MaterialTheme {
                GalleryCatalog(
                    entries = listOf(recent, older, other), queue = null,
                    onQueue = {}, onBatchReview = {}, onOpen = {},
                    onRemove = {}, onEdit = { _, _ -> true },
                    onExport = {}, onImport = {}, backupStatus = "", historyCount = 0
                )
            }
        }

        compose.onNodeWithTag("catalog-model-mode").performClick()
        compose.onNodeWithTag("catalog-search").performTextReplacement("Leona")
        compose.onNodeWithText("Leona Mia").performScrollTo().performClick()
        compose.onNodeWithTag("catalog-search").performTextReplacement("Архивная")
        compose.onNodeWithText("Показано: 1 из 2", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Архивная съёмка").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Alba").assertDoesNotExist()

        compose.onNodeWithText("Фильтры").performScrollTo().performClick()
        compose.onNodeWithTag("catalog-status-filter-errors").performScrollTo().performClick()
        compose.onNodeWithText("Показано: 0 из 2", substring = true).assertIsDisplayed()
        compose.onNodeWithTag("catalog-model-back").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("catalog-status-filter-all").performScrollTo().performClick()
        compose.onNodeWithTag("catalog-model-back").performScrollTo().performClick()
        compose.onNodeWithText("Моделей: 1", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Alba").assertDoesNotExist()
    }

}
