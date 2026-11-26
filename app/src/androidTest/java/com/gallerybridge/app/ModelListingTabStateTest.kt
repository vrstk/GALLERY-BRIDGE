package com.gallerybridge.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.ViewModelProvider
import com.gallerybridge.app.core.ModelListingSources
import org.junit.Rule
import org.junit.Test

class ModelListingTabStateTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun preservesSearchScrollAndSelectionAfterLeavingDownloads() {
        val publications = (1..30).map { index ->
            ModelListingSources.Publication(
                url = "https://example.com/model-listing-fixture-$index",
                title = "Публикация $index",
                date = "",
                model = "Пример",
                studio = if (index <= 20) "Студия А" else "Студия Б"
            )
        }
        compose.runOnIdle {
            val session = ViewModelProvider(compose.activity)[GallerySessionState::class.java]
            session.url = "https://example.com/model-listing-fixture"
            session.modelListing = ModelListingSources.Result(
                photos = publications, videos = 0, cards = 30, loadedPages = 1,
                advertisedCount = 30, incomplete = false, errors = emptyList()
            )
        }

        compose.onNodeWithTag("nav-downloads").performClick()
        compose.onNodeWithTag("model-listing-search").performTextReplacement("Студия А")
        compose.onNodeWithText("Найдено: 20 из 30").assertIsDisplayed()
        compose.onNodeWithTag("model-listing-list").performScrollToIndex(12)
        compose.onNodeWithTag("model-listing-select-11").performClick()
        compose.onNodeWithTag("nav-catalog").performClick()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("nav-downloads").performClick()

        compose.onNodeWithTag("model-listing-card-11").assertIsDisplayed()
        compose.onNodeWithTag("model-listing-select-11").assertIsOn()
        compose.onNodeWithTag("model-listing-list").performScrollToIndex(0)
        compose.onNodeWithText("Найдено: 20 из 30").assertIsDisplayed()
    }
}
