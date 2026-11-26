package com.gallerybridge.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performClick
import com.gallerybridge.app.core.ModelListingSources
import com.gallerybridge.app.ui.ModelListingReview
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ModelListingReviewTest {
    @get:Rule val compose = createComposeRule()

    @Test fun partialCatalogCanBeReviewedWithoutStartingTransfer() {
        val link = "https://www.elitebabes.com/example-gallery/"
        val result = ModelListingSources.Result(
            photos = listOf(ModelListingSources.Publication(
                link, "Example gallery", "2025-01-01", "Example model"
            )),
            videos = 1, cards = 2, loadedPages = 1,
            advertisedCount = 200, incomplete = true, errors = emptyList()
        )
        var selected = emptySet<String>()
        var saves = 0
        var opens = 0
        compose.setContent {
            MaterialTheme {
                ModelListingReview(
                    result, emptyList(), selected, "",
                    busy = false, scanning = false,
                    onSelectPhotos = {}, onClear = {}, onQueueSelected = {},
                    onStop = {}, onContinue = {},
                    onSelect = { url, checked ->
                        selected = if (checked) selected + url else selected - url
                    },
                    onSave = { saves++ },
                    onOpen = { opens++ },
                    onBack = {}
                )
            }
        }
        compose.onNodeWithText("Галереи модели").assertIsDisplayed()
        compose.onNodeWithText("Example gallery").assertIsDisplayed()
        compose.onNodeWithText("Открыть").performClick()
        compose.runOnIdle {
            assertEquals(1, opens)
            assertEquals(0, saves)
        }
    }
    @Test fun selectingSearchResultsPreservesPicksOutsideSearchAndExcludesVideos() {
        val outside = ModelListingSources.Publication("https://example.com/one", "First", "", "Model")
        val matching = ModelListingSources.Publication("https://example.com/two", "Second", "",
            "Model", studio = "Studio B")
        val video = ModelListingSources.Publication("https://example.com/video", "Clip", "",
            "Model", studio = "Studio B", type = "video")
        val chosen = mutableStateOf(setOf(outside.url))
        val result = ModelListingSources.Result(
            photos = listOf(outside, matching), videos = 1, cards = 3, loadedPages = 1,
            advertisedCount = 3, incomplete = false, errors = emptyList(), videoCards = listOf(video)
        )
        compose.setContent {
            MaterialTheme {
                ModelListingReview(
                    result, emptyList(), chosen.value, "", busy = false, scanning = false,
                    onSelect = { _, _ -> }, onSelectPhotos = { chosen.value = chosen.value + it },
                    onClear = {}, onSave = {}, onQueueSelected = {}, onStop = {},
                    onContinue = {}, onOpen = {}, onBack = {}
                )
            }
        }

        compose.onNodeWithTag("model-listing-search").performTextReplacement("studio b")
        compose.onNodeWithText("Найдено: 2 из 3").assertIsDisplayed()
        compose.onNodeWithText("Выбрать найденные фото").performScrollTo().performClick()
        compose.onNodeWithText("Вне поиска: 1.", substring = true).assertIsDisplayed()
        compose.runOnIdle { assertEquals(setOf(outside.url, matching.url), chosen.value) }
    }

}
