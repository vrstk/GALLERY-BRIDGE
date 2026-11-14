package com.gallerybridge.app

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
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
        compose.onNodeWithText("Ссылка на галерею или видео").performTextInput(
            "https://example.com/fixture.jpg"
        )
        compose.onNodeWithText("Найти медиафайлы").performClick()
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
    fun storageShortcutOpensDriveAndThemePersistsAcrossActivityRecreation() {
        compose.onNodeWithText("Google Drive", substring = true).performClick()
        compose.onNodeWithText("ID папки Google Drive").assertIsDisplayed()
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
        compose.onNodeWithText("Ссылка на галерею или видео").performTextInput("https://example.com/fixture.mp4")
        compose.onNodeWithText("Найти медиафайлы").performClick()
        awaitSearchResult()

        compose.onNodeWithText("Фото (0)").assertIsNotEnabled()
        compose.onNodeWithText("Видео (1)").performClick()
        compose.onNodeWithText("Выбрано: 1").assertIsDisplayed()
        compose.onNodeWithText("Снять выделение").performClick()
        compose.onNodeWithText("Выбрано: 0").assertIsDisplayed()
        compose.onNodeWithText("Загрузить").assertIsNotEnabled()
        compose.onNodeWithText("Выбрать все в фильтре").performClick()
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
    fun switchingMailAccountClearsUnsavedPassword() {
        compose.onNodeWithText("Mail WebDAV", substring = true).performClick()
        compose.onNodeWithText("Почта Mail").performTextInput("first@example.com")
        compose.onNodeWithText("Пароль внешнего приложения").performTextInput("secret-for-first")
        compose.onNodeWithText("Почта Mail").performTextReplacement("second@example.com")
        compose.onNodeWithText("Показать").performClick()
        compose.onNodeWithText("secret-for-first").assertDoesNotExist()
    }

    private fun awaitSearchResult() {
        compose.waitUntil(timeoutMillis = 8_000) {
            compose.onNodeWithText("Найдено файлов: 1").isDisplayed()
        }
    }
}
