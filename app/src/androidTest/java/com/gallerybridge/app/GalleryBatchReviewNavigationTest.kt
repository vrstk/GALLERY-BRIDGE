package com.gallerybridge.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.ViewModelProvider
import com.gallerybridge.app.core.CatalogGallery
import com.gallerybridge.app.core.GalleryCatalogStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class GalleryBatchReviewNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun anotherLinkPausesReviewAndARemovedPublicationIsSkipped() {
        val catalog = (1..3).map { index ->
            CatalogGallery(
                url = "https://example.com/gallery/batch-nav-fixture-$index",
                model = "Модель для проверки просмотра",
                title = "Проверка пропуска $index",
                date = "2026-10-23",
                updatedAt = (4 - index).toLong()
            )
        }
        val context = compose.activity.applicationContext
        GalleryCatalogStore.importMissing(context, catalog)
        try {
            compose.onNodeWithTag("nav-catalog").performClick()
            compose.onNodeWithTag("catalog-search").performTextReplacement("Проверка пропуска")
            compose.onNodeWithText("Пакетный просмотр").performClick()
            compose.onNodeWithText("Добавить из списка").performScrollTo().performClick()
            compose.onNodeWithText("Просмотреть (3)").performScrollTo().performClick()
            compose.onNodeWithText("Начать просмотр").performClick()
            compose.onNodeWithText("Пакетный просмотр: 1 из 3").assertIsDisplayed()

            compose.onNodeWithText("Ссылка на фотогалерею").performTextReplacement(
                "https://example.com/gallery/outside-batch"
            )
            compose.onNodeWithText("Просмотр приостановлен. Открыта другая ссылка.")
                .assertIsDisplayed()
            compose.onNodeWithText("Следующая публикация").assertDoesNotExist()
            compose.onNodeWithTag("nav-catalog").performClick()
            compose.onNodeWithText("Продолжить просмотр").performClick()
            compose.onNodeWithText("Пакетный просмотр: 1 из 3").assertIsDisplayed()
            compose.runOnIdle {
                val session = ViewModelProvider(compose.activity)[GallerySessionState::class.java]
                assertEquals(catalog[0].url, session.url)
            }

            assertTrue(GalleryCatalogStore.remove(context, catalog[1].url))
            compose.onNodeWithText("Следующая публикация").performClick()
            compose.onNodeWithText("Пакетный просмотр: 3 из 3").assertIsDisplayed()
            compose.onNodeWithText("Пропущено недоступных для просмотра публикаций: 1.")
                .assertIsDisplayed()
            compose.runOnIdle {
                val session = ViewModelProvider(compose.activity)[GallerySessionState::class.java]
                assertEquals(catalog[2].url, session.url)
                assertEquals(catalog[2].title, session.title)
                assertEquals(catalog[2].date, session.date)
            }
            compose.onNodeWithText("Вернуться в каталог").performClick()
            compose.onNodeWithTag("catalog-search").assertIsDisplayed()
        } finally {
            catalog.forEach { GalleryCatalogStore.remove(context, it.url) }
        }
    }
}
