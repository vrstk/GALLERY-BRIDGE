package com.gallerybridge.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.gallerybridge.app.core.Kind
import com.gallerybridge.app.core.Media
import com.gallerybridge.app.transfer.HistoryStore
import com.gallerybridge.app.transfer.QueueStore
import com.gallerybridge.app.ui.GalleryHistoryCard
import com.gallerybridge.app.ui.GalleryQueueSummary
import org.junit.Rule
import org.junit.Test

/** UI-only tests; no file writes or network calls. */
class GalleryTransferCardsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun historyKeepsOutcomeVisibleAndExpandsExactCounters() {
        val record = HistoryStore.Record(
            id = 7L, updatedAt = 1700000000000L,
            model = "Модель", title = "Длинная публикация",
            date = "2024-01-01", target = "Mail WebDAV",
            total = 12, uploaded = 8, skipped = 1, failed = 1,
            outcome = "Остановлено"
        )
        compose.setContent { MaterialTheme { GalleryHistoryCard(record) } }
        compose.onNodeWithText("Модель").assertIsDisplayed()
        compose.onNodeWithText("Mail WebDAV").assertIsDisplayed()
        compose.onNodeWithText("· Остановлено").assertIsDisplayed()
        compose.onNodeWithText("Готово: 9 из 12 · Осталось: 3").assertIsDisplayed()
        compose.onNodeWithText("Загружено: 8 · Уже были: 1 · Ошибки: 1").assertDoesNotExist()
        compose.onNodeWithContentDescription("Показать статистику").performClick()
        compose.onNodeWithText("Загружено: 8 · Уже были: 1 · Ошибки: 1").assertIsDisplayed()
        compose.onNodeWithContentDescription("Скрыть статистику").performClick()
        compose.onNodeWithText("Загружено: 8 · Уже были: 1 · Ошибки: 1").assertDoesNotExist()
    }

    @Test fun queueSummaryPreservesBlockedCountAndExactProgress() {
        val queue = QueueStore.Snapshot(
            id = 8L, model = "Модель B", title = "Публикация", date = "",
            target = "Папка Android", root = "Models", account = "",
            entries = listOf(
                QueueStore.Entry(Media("https://example.com/a.jpg", "a.jpg", Kind.PHOTO, "test"), "DONE"),
                QueueStore.Entry(Media("https://example.com/b.jpg", "b.jpg", Kind.PHOTO, "test"), "BLOCKED")
            )
        )
        compose.setContent { MaterialTheme { GalleryQueueSummary(queue) } }
        compose.onNodeWithText("Готово: 1 из 2").assertIsDisplayed()
        compose.onNodeWithText("Передано: 1 · Уже были: 0 · Ошибки: 1 · Осталось: 1").assertIsDisplayed()
        compose.onNodeWithText("Требуют ручной проверки: 1").assertIsDisplayed()
        compose.onNodeWithText("Синхронизация зависит от выбранного провайдера папки.").assertIsDisplayed()
    }
}
