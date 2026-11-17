package com.gallerybridge.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
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

        compose.onNodeWithTag("fullscreen-photo-image").performTouchInput {
            click(Offset(size.width * 0.16f, size.height * 0.55f))
        }
        compose.waitForIdle()
        compose.onNodeWithTag("fullscreen-photo-controls-top").assertDoesNotExist()
        compose.onNodeWithTag("fullscreen-photo-controls-bottom").assertDoesNotExist()

        compose.onNodeWithTag("fullscreen-photo-image").performTouchInput {
            click(Offset(size.width * 0.16f, size.height * 0.55f))
        }
        compose.waitForIdle()
        compose.onNodeWithTag("fullscreen-photo-controls-top").assertIsDisplayed()
        compose.onNodeWithTag("fullscreen-photo-controls-bottom").assertIsDisplayed()
    }
}
