package com.gallerybridge.app

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import com.gallerybridge.app.core.ModelListingSources
import com.gallerybridge.app.ui.GalleryPage
import com.gallerybridge.app.ui.GalleryShell
import com.gallerybridge.app.ui.GalleryTheme
import com.gallerybridge.app.ui.ModelListingReview
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Reproduces a long API error plus many cards in the Downloads screen. */
class ModelListingScrollTest {
    @get:Rule val compose = createComposeRule()

    @Test fun longDiagnosticsDoNotTrapCardsBelowBottomNavigation() {
        val publications = (1..60).map { index ->
            ModelListingSources.Publication(
                url = "https://elitebabes.com/fixture-gallery-$index/",
                title = "Галерея $index",
                date = "", model = "Модель"
            )
        }
        val result = ModelListingSources.Result(
            photos = publications, videos = 0, cards = 60, loadedPages = 23,
            advertisedCount = 60, incomplete = true,
            errors = listOf("Ошибка API на порции №23: " + "нет ответа. ".repeat(50))
        )
        val chosen = mutableStateOf<Set<String>>(emptySet())

        compose.setContent {
            GalleryTheme(dark = true) {
                GalleryShell(
                    page = GalleryPage.DOWNLOADS, onNavigate = {},
                    onSettings = {}, onBack = {}
                ) {
                    ModelListingReview(
                        result = result, existing = emptyList(), selected = chosen.value,
                        message = "Список может быть неполным.", busy = false, scanning = false,
                        onSelect = { url, checked ->
                            chosen.value = if (checked) chosen.value + url else chosen.value - url
                        },
                        onSelectAllPhotos = { chosen.value = publications.map { it.url }.toSet() },
                        onClear = { chosen.value = emptySet() },
                        onSave = {}, onQueueSelected = {},
                        onStop = {}, onContinue = {}, onOpen = {}, onBack = {}
                    )
                }
            }
        }

        compose.onNodeWithText("Галереи модели").assertIsDisplayed()
        compose.onNodeWithText("К карточкам (60)").assertIsDisplayed().performClick()
        compose.onNodeWithTag("model-listing-card-0").assertIsDisplayed()

        // Header occupies more than a phone screen. The last card must still
        // become fully visible above the fixed Android bottom navigation.
        compose.onNodeWithTag("model-listing-list").performScrollToIndex(60)
        compose.onNodeWithTag("model-listing-card-59").assertIsDisplayed()
        compose.onNodeWithTag("nav-downloads").assertIsDisplayed()
        compose.onNodeWithTag("model-listing-select-59").performClick()
        compose.onNodeWithTag("model-listing-select-59").assertIsOn()
        compose.runOnIdle { assertEquals(setOf(publications.last().url), chosen.value) }

        compose.onNodeWithTag("model-listing-list").performScrollToIndex(0)
        compose.onNodeWithText("Галереи модели").assertIsDisplayed()
    }
}
