package com.gallerybridge.app

import android.content.Context
import com.gallerybridge.app.core.Kind
import com.gallerybridge.app.core.Media
import com.gallerybridge.app.transfer.HistoryStore
import com.gallerybridge.app.transfer.QueueStore
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class GalleryHomeTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun homeSearchOpensDownloadsAndKeepsResultsWhenChangingTabs() {
        compose.onNodeWithText("GALLERY BRIDGE").assertIsDisplayed()
        compose.onNodeWithText("Ссылка на фотогалерею").performTextInput(
            "https://example.com/fixture.jpg"
        )
        compose.onNodeWithText("Найти фотографии").performClick()
        awaitSearchResult()

        compose.onNodeWithText("Очередь").performClick()
        compose.onNodeWithText("Очередь загрузок").assertIsDisplayed()
        compose.onNodeWithText("История").performClick()
        compose.onNodeWithText("История загрузок").assertIsDisplayed()
        compose.onNodeWithText("Главная").performClick()
        compose.onNodeWithText("Открыть результаты").performClick()
        awaitSearchResult()
    }

    @Test
    fun mosaicAndPhotoSelectionSurviveActivityRecreation() {
        compose.onNodeWithText("Ссылка на фотогалерею").performTextInput(
            "https://example.com/rotation-fixture.jpg"
        )
        compose.onNodeWithText("Найти фотографии").performClick()
        awaitSearchResult()
        compose.onNodeWithText("Плитка").assertDoesNotExist()
        compose.onNodeWithText("Список без фото").assertDoesNotExist()
        compose.onNodeWithText("Снять выделение").performClick()
        compose.onNodeWithText("Выбрано: 0").assertIsDisplayed()

        // Simulates Android destroying and recreating Activity on rotation.
        compose.activityRule.scenario.recreate()

        compose.onNodeWithText("Фотографии: 1").assertIsDisplayed()
        compose.onNodeWithText("Выбрано: 0").assertIsDisplayed()
        compose.onNodeWithText("Плитка").assertDoesNotExist()
        compose.onNodeWithText("Список без фото").assertDoesNotExist()
    }


    @Test
    fun fullscreenViewerRemainsOpenAfterRotationAndCanClose() {
        compose.onNodeWithText("Ссылка на фотогалерею").performTextInput(
            "https://example.com/rotation-fixture.jpg"
        )
        compose.onNodeWithText("Найти фотографии").performClick()
        awaitSearchResult()
        compose.onNodeWithTag("gallery-mosaic-photo-https://example.com/rotation-fixture.jpg")
            .performClick()
        // A dialog uses a separate Android window. Verify the actual pager,
        // rather than assuming its overlay text is visible in every test frame.
        compose.waitUntil(timeoutMillis = 8_000) {
            compose.onAllNodesWithTag("fullscreen-photo-pager")
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("fullscreen-photo-pager").assertExists()
        compose.activityRule.scenario.recreate()
        compose.waitUntil(timeoutMillis = 8_000) {
            compose.onAllNodesWithTag("fullscreen-photo-pager")
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("fullscreen-photo-pager").assertExists()
        compose.onNodeWithContentDescription("Закрыть фото").performClick()
        compose.onNodeWithText("Фотографии: 1").assertIsDisplayed()
    }

    @Test
    fun galleryCatalogOpensAndReturnsHomeWithoutStartingScan() {
        compose.onNodeWithText("Каталог галерей").performClick()
        compose.onNodeWithText("Каталог галерей").assertIsDisplayed()
        compose.onNodeWithText("Каталог пока пуст.", substring = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("Назад").performClick()
        compose.onNodeWithText("GALLERY BRIDGE").assertIsDisplayed()
    }

    @Test
    fun storageShortcutOpensAndroidFolderAndThemePersistsAcrossActivityRecreation() {
        compose.onNodeWithTag("storage-android-folder").performScrollTo().performClick()
        compose.onNodeWithText("Выбрать папку в Android").assertIsDisplayed()
        compose.onNodeWithText("Тёмная тема").assertIsOn().performClick()
        compose.runOnIdle {
            assertFalse(compose.activity.getSharedPreferences("settings", Context.MODE_PRIVATE).getBoolean("dark", true))
        }
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Тёмная тема").assertIsOff().performClick()
        compose.runOnIdle {
            assertTrue(compose.activity.getSharedPreferences("settings", Context.MODE_PRIVATE).getBoolean("dark", false))
        }
        compose.onNodeWithContentDescription("Назад").performClick()
        compose.onNodeWithText("GALLERY BRIDGE").assertIsDisplayed()
    }

    @Test
    fun resultsKeepSelectionVisibleAndOpenDestinationForm() {
        compose.onNodeWithText("Ссылка на фотогалерею").performTextInput("https://example.com/fixture.jpg")
        compose.onNodeWithText("Найти фотографии").performClick()
        awaitSearchResult()

        compose.onNodeWithText("Фотографии: 1").assertIsDisplayed()
        compose.onNodeWithText("На главную").assertIsDisplayed()
        compose.onNodeWithText("Поиск по ссылке").assertDoesNotExist()
        compose.onNodeWithText("Выбрано: 1").assertIsDisplayed()
        compose.onNodeWithText("Снять выделение").performClick()
        compose.onNodeWithText("Выбрано: 0").assertIsDisplayed()
        compose.onNodeWithText("Загрузить").assertIsNotEnabled()
        compose.onNodeWithText("Выбрать показанные").performClick()
        compose.onNodeWithText("Загрузить").performClick()

        compose.onNodeWithText("Куда загрузить").assertIsDisplayed()
        compose.onNode(hasSetTextAction() and hasText("Дата ГГГГ-ММ-ДД"))
            .performScrollTo().performTextReplacement("2026-02-31")
        compose.onNodeWithText("Укажите реальную дату").assertExists()
        compose.onNodeWithText("Начать загрузку").performClick()
        compose.onNodeWithText("Укажите реальную дату в формате ГГГГ-ММ-ДД или оставьте поле пустым.").assertExists()
        compose.onNodeWithText("Отмена").performClick()
        compose.onNodeWithText("Выбрано: 1").assertIsDisplayed()
    }

    @Test
    fun removedGoogleApiIsNotOfferedInDestinations() {
        compose.onNodeWithText("Google Drive API").assertDoesNotExist()
        compose.onNodeWithTag("storage-android-folder").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("storage-mail-webdav").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun switchingMailAccountClearsUnsavedPassword() {
        compose.onNodeWithTag("storage-mail-webdav").performClick()
        compose.onNodeWithText("Почта Mail").performTextInput("first@example.com")
        compose.onNodeWithText("Пароль внешнего приложения").performTextInput("secret-for-first")
        compose.onNodeWithText("Почта Mail").performTextReplacement("second@example.com")
        compose.onNodeWithText("Показать").performClick()
        compose.onNodeWithText("secret-for-first").assertDoesNotExist()
    }

    @Test
    fun closeFailedQueueKeepsHistoryAndReturnsToNewUpload() {
        compose.runOnIdle {
            val media = Media("https://example.com/video.mp4", "video.mp4", Kind.VIDEO, "test")
            val queue = QueueStore.newSnapshot("Example", "Blocked video", "", "Mail WebDAV",
                "Models", "", listOf(media))
            QueueStore.save(compose.activity, queue.copy(entries = listOf(QueueStore.Entry(media, "FAILED"))))
        }
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Очередь").performClick()
        compose.onNodeWithText("Закрыть очередь и начать новую").performScrollTo().performClick()
        compose.onNodeWithText("Закрыть незавершённую очередь?").assertIsDisplayed()
        compose.onNodeWithText("Закрыть очередь").performClick()
        compose.onNodeWithText("GALLERY BRIDGE").assertIsDisplayed()
        compose.runOnIdle {
            assertTrue(QueueStore.load(compose.activity) == null)
            assertTrue(HistoryStore.load(compose.activity).any { it.outcome == "Закрыта вручную" })
        }
    }

    private fun awaitSearchResult() {
        compose.waitUntil(timeoutMillis = 8_000) {
            compose.onNodeWithText("Фотографии: 1").isDisplayed()
        }
    }
}
