package com.gallerybridge.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.gallerybridge.app.ui.GalleryPhotoHeader
import com.gallerybridge.app.ui.CompactPhotoSelection
import com.gallerybridge.app.ui.GalleryTheme
import org.junit.Rule
import org.junit.Test

/** Preview-only UI: no network, queue or on-device storage used. */
class GalleryPhotoFirstTest {
    @get:Rule val compose = createComposeRule()

    @Test fun compactPhotoSelectionKeepsLargeHitAreaAndSmallVisibleMark() {
        var selected by androidx.compose.runtime.mutableStateOf(true)
        compose.setContent {
            GalleryTheme(dark = true) {
                CompactPhotoSelection(
                    checked = selected, enabled = true,
                    photoName = "001.jpg", onClick = { selected = !selected }
                )
            }
        }
        val target = compose.onNodeWithTag("gallery-photo-selection", useUnmergedTree = true)
        val visual = compose.onNodeWithTag("gallery-photo-selection-visual", useUnmergedTree = true)
        target.assertIsDisplayed()
        visual.assertIsDisplayed()
        compose.runOnIdle {
            val targetWidth = target.getUnclippedBoundsInRoot().width.value
            val visualWidth = visual.getUnclippedBoundsInRoot().width.value
            org.junit.Assert.assertTrue("Visual indicator must be smaller than hit area",
                visualWidth < targetWidth * 0.7f)
        }
        compose.onNodeWithContentDescription("Снять выделение: 001.jpg").performClick()
        compose.onNodeWithContentDescription("Выбрать фото: 001.jpg").assertIsDisplayed()
    }

    @Test fun realPublicationLabelsAppearAbovePhotos() {
        compose.setContent {
            GalleryTheme(dark = true) {
                GalleryPhotoHeader(
                    visibleCount = 12, totalCount = 32, foundCount = 48, extraCount = 16,
                    publicationModel = "Модель Альфа", publicationTitle = "Фотосессия у моря"
                )
            }
        }
        compose.onNodeWithTag("gallery-publication-summary").assertIsDisplayed()
        compose.onNodeWithText("Модель Альфа").assertIsDisplayed()
        compose.onNodeWithText("Фотосессия у моря").assertIsDisplayed()
        compose.onNodeWithText("Фотографии: 12 из 32").assertIsDisplayed()
        compose.onNodeWithText("Всего найдено: 48 · Дополнительных вариантов: 16")
            .assertIsDisplayed()
    }

    @Test fun missingMetadataStaysMissingAndPhotoCountRemainsVisible() {
        compose.setContent {
            GalleryTheme(dark = false) {
                GalleryPhotoHeader(
                    visibleCount = 1, totalCount = 1, foundCount = 1, extraCount = 0,
                    publicationModel = "", publicationTitle = ""
                )
            }
        }
        compose.onNodeWithTag("gallery-publication-summary").assertDoesNotExist()
        compose.onNodeWithText("ПУБЛИКАЦИЯ").assertDoesNotExist()
        compose.onNodeWithText("Фотографии: 1").assertIsDisplayed()
        compose.onNodeWithText("Все найденные фотографии показаны ниже").assertIsDisplayed()
    }
}
