package com.gallerybridge.app

import com.gallerybridge.app.core.Kind
import com.gallerybridge.app.core.Media
import com.gallerybridge.app.transfer.QueueStore
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class QueueReviewTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun completedMailQueueOffersReadOnlySha256Audit() {
        val id = 352038L
        val file = Media("https://example.com/verified.jpg", "verified.jpg", Kind.PHOTO, "test")
        compose.runOnIdle {
            QueueStore.save(compose.activity, QueueStore.Snapshot(
                id = id, model = "Тест", title = "Проверка Mail", date = "",
                target = "Mail WebDAV", root = "Models", account = "audit@example.com",
                entries = listOf(QueueStore.Entry(file, "DONE", contentSha256 = "a".repeat(64)))
            ))
        }
        try {
            compose.activityRule.scenario.recreate()
            compose.onNodeWithTag("nav-downloads").performClick()
            compose.onNodeWithContentDescription("Открыть очередь").performClick()
            compose.onNodeWithText("Проверить SHA-256 сохранённых файлов")
                .performScrollTo().assertIsDisplayed()
            compose.onNodeWithText(
                "Только чтение Mail WebDAV: проверяются сохранённые SHA-256 и реальные байты. " +
                    "Файлы и очередь не изменяются. Лимит: 32 МиБ на файл."
            ).assertIsDisplayed()
            compose.runOnIdle {
                assertEquals("DONE", QueueStore.load(compose.activity)!!.entries.single().state)
            }
        } finally {
            compose.runOnIdle { QueueStore.clearIfId(compose.activity, id) }
        }
    }

    @Test fun failedPhotoRetryNeedsConfirmationAndCancellationKeepsQueue() {
        val id = 352036L
        val failed = Media("https://example.com/retry-a.jpg", "retry-a.jpg", Kind.PHOTO, "test")
        val pending = Media("https://example.com/retry-b.jpg", "retry-b.jpg", Kind.PHOTO, "test")
        compose.runOnIdle {
            QueueStore.save(compose.activity, QueueStore.Snapshot(
                id = id, model = "Тест", title = "Выборочный повтор", date = "",
                target = "Mail WebDAV", root = "Models", account = "",
                entries = listOf(
                    QueueStore.Entry(failed, "FAILED", "Временная ошибка сети"),
                    QueueStore.Entry(pending, "PENDING")
                )
            ))
        }
        try {
            compose.activityRule.scenario.recreate()
            compose.onNodeWithTag("nav-downloads").performClick()
            compose.onNodeWithContentDescription("Открыть очередь").performClick()
            compose.onNodeWithText("Повторить только этот файл").performScrollTo().performClick()
            compose.onNodeWithText("Повторить только эту фотографию?").assertIsDisplayed()
            compose.onNodeWithText("Отмена").performClick()
            compose.runOnIdle {
                val queue = QueueStore.load(compose.activity)!!
                assertEquals(listOf("FAILED", "PENDING"), queue.entries.map { it.state })
                assertEquals("Временная ошибка сети", queue.entries[0].failureReason)
            }
        } finally {
            compose.runOnIdle { QueueStore.clearIfId(compose.activity, id) }
        }
    }

    @Test fun blockedFileNeedsConfirmationAndNeverUnlocksAnotherFile() {
        val id = 352035L
        val first = Media("https://example.com/test-a.jpg", "test-a.jpg", Kind.PHOTO, "test")
        val second = Media("https://example.com/test-b.jpg", "test-b.jpg", Kind.PHOTO, "test")
        compose.runOnIdle {
            QueueStore.save(compose.activity, QueueStore.Snapshot(
                id = id, model = "Test", title = "Повтор после проверки", date = "",
                target = "Mail WebDAV", root = "Models", account = "",
                entries = listOf(
                    QueueStore.Entry(first, "BLOCKED", "Проверьте частичный файл"),
                    QueueStore.Entry(second, "PENDING")
                )
            ))
        }
        try {
            compose.activityRule.scenario.recreate()
            compose.onNodeWithTag("nav-downloads").performClick()
            compose.onNodeWithContentDescription("Открыть очередь").performClick()
            compose.onNodeWithText("Требуют ручной проверки: 1").assertIsDisplayed()
            compose.onNodeWithText("Проверьте частичный файл").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Разрешить повтор после проверки").performScrollTo().performClick()
            compose.onNodeWithText("Разрешить повтор этого файла?").assertIsDisplayed()
            compose.onNodeWithText("Отмена").performClick()
            compose.runOnIdle {
                val queue = QueueStore.load(compose.activity)!!
                assertEquals("BLOCKED", queue.entries[0].state)
                assertEquals(1, queue.blocked)
            }
            compose.onNodeWithText("Разрешить повтор после проверки").performScrollTo().performClick()
            compose.onNodeWithText("Я проверил файл").performClick()
            compose.runOnIdle {
                val queue = QueueStore.load(compose.activity)!!
                assertEquals(listOf("PENDING", "PENDING"), queue.entries.map { it.state })
                assertEquals(0, queue.blocked)
                assertEquals(2, queue.retryable)
                assertTrue(queue.entries[1].failureReason.isBlank())
            }
        } finally {
            compose.runOnIdle { QueueStore.clearIfId(compose.activity, id) }
        }
    }
}
