package com.gallerybridge.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.gallerybridge.app.ui.StorageDestinationCards
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** The picker must not claim a cloud upload was synchronized. */
class GalleryStorageDestinationsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun switchesStorageWithoutClaimingProviderSync() {
        val destination = mutableStateOf("Папка Android")
        compose.setContent {
            MaterialTheme {
                StorageDestinationCards(
                    selected = destination.value,
                    folderName = "Мои фотографии",
                    folderSelected = true,
                    folderReady = true,
                    mailConfigured = false,
                    enabled = true,
                    onSelect = { destination.value = it }
                )
            }
        }
        compose.onNodeWithTag("storage-destination-android").assertIsSelected()
        compose.onNodeWithText("Мои фотографии").assertIsDisplayed()
        compose.onNodeWithText("Доступ к папке предоставлен").assertIsDisplayed()
        compose.onNodeWithText("Введите почту и пароль приложения").assertIsDisplayed()
        compose.onNodeWithTag("storage-destination-mail").performClick()
        compose.onNodeWithTag("storage-destination-mail").assertIsSelected()
        compose.onNodeWithTag("storage-destination-android").assertIsNotSelected()
        compose.runOnIdle { assertEquals("Mail WebDAV", destination.value) }
    }

    @Test fun revokedSafPermissionIsNotShownAsConnected() {
        compose.setContent {
            MaterialTheme {
                StorageDestinationCards(
                    selected = "Папка Android", folderName = "Старая папка",
                    folderSelected = true, folderReady = false,
                    mailConfigured = true, enabled = false, onSelect = {}
                )
            }
        }
        compose.onNodeWithText("Нет доступа — выберите папку снова").assertIsDisplayed()
        compose.onNodeWithText("Почта и пароль указаны").assertIsDisplayed()
    }
}
