package com.gallerybridge.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.gallerybridge.app.core.Kind
import com.gallerybridge.app.core.Media
import com.gallerybridge.app.ui.FullscreenPhotoViewer
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class FullscreenPhotoViewerTest {
    @get:Rule val compose = createComposeRule()

    @Test fun singleTapTogglesCompactOverlaysAndSelectionWorks() {
        val photo = Media("https://example.invalid/photo-one.jpg", "photo-one.jpg",
            Kind.PHOTO, "Тест")
        var selections = 0
        compose.setContent {
            var selected by remember { mutableStateOf(setOf<String>()) }
            MaterialTheme {
                FullscreenPhotoViewer(
                    photos = listOf(photo),
                    initialIndex = 0,
                    selected = selected,
                    onSelection = { media, checked ->
                        selected = if (checked) selected + media.url else selected - media.url
                        selections++
                    },
                    onClose = {}
                )
            }
        }
        compose.onNodeWithTag("fullscreen-photo-pager").assertIsDisplayed()
        compose.onNodeWithTag("fullscreen-photo-controls-top").assertIsDisplayed()
        compose.onNodeWithTag("fullscreen-photo-controls-bottom").assertIsDisplayed()
        compose.onNodeWithText("1 / 1").assertIsDisplayed()
        compose.onNodeWithTag("fullscreen-photo-selection").performClick()
        compose.runOnIdle { assertEquals(1, selections) }

        compose.onNodeWithTag("fullscreen-photo-viewer").performClick()
        // Combined clicks wait for the Android double-tap timeout before
        // committing a *single* tap. waitForIdle alone can return too early.
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag("fullscreen-photo-controls-top")
                .fetchSemanticsNodes().isEmpty()
        }
        compose.onNodeWithTag("fullscreen-photo-controls-top").assertDoesNotExist()
        compose.onNodeWithTag("fullscreen-photo-controls-bottom").assertDoesNotExist()

        compose.onNodeWithTag("fullscreen-photo-viewer").performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag("fullscreen-photo-controls-top")
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("fullscreen-photo-controls-top").assertIsDisplayed()
        compose.onNodeWithTag("fullscreen-photo-controls-bottom").assertIsDisplayed()
    }
}
