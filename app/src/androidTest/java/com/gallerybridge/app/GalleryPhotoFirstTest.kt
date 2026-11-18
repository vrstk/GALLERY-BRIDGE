package com.gallerybridge.app

import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.gallerybridge.app.ui.GalleryPhotoHeader
import com.gallerybridge.app.ui.GalleryTheme
import org.junit.Rule
import org.junit.Test

/** Preview-only UI: no network, queue or on-device storage used. */
class GalleryPhotoFirstTest {
    @get:Rule val compose = createComposeRule()

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
