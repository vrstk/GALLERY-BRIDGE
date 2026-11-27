package com.gallerybridge.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import com.gallerybridge.app.core.Kind
import com.gallerybridge.app.core.Media
import com.gallerybridge.app.ui.FullscreenPhotoViewer
import org.junit.Rule
import org.junit.Test

class FullscreenSwipeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun oneFingerSwipeNavigatesAndCanReturnToPreviousPhoto() {
        val images = (1..3).map { index ->
            Media("https://example.com/photo$index.jpg", "photo$index.jpg", Kind.PHOTO, "test")
        }
        compose.setContent {
            MaterialTheme {
                FullscreenPhotoViewer(
                    photos = images,
                    initialIndex = 0,
                    selected = emptySet(),
                    onSelection = { _, _ -> },
                    onClose = {}
                )
            }
        }
        compose.onNodeWithText("1 / 3").assertIsDisplayed()
        compose.onNodeWithTag("fullscreen-photo-pager").performTouchInput { swipeLeft() }
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("2 / 3").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("fullscreen-photo-pager").performTouchInput { swipeRight() }
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("1 / 3").fetchSemanticsNodes().isNotEmpty()
        }
    }
}
