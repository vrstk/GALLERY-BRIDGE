package com.gallerybridge.app

import android.content.Context
import com.gallerybridge.app.core.GalleryCatalogStore
import com.gallerybridge.app.core.Kind
import com.gallerybridge.app.core.Media
import com.gallerybridge.app.transfer.HistoryStore
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

    @Test fun interruptedWriteRemainsBlockedAcrossActivityRecreation() {
        val id = 352039L
        val photo = Media("https://example.com/interrupted.jpg", "interrupted.jpg", Kind.PHOTO, "test")
        val next = photo.copy(name = "next.jpg")
        val source = "https://example.com/gallery-review-352039"
        val historyPrefs = compose.activity.getSharedPreferences("gallery_bridge_history", Context.MODE_PRIVATE)
        val previousHistory = historyPrefs.getString("records", null)
        compose.runOnIdle {
            QueueStore.save(compose.activity, QueueStore.Snapshot(
                id = id, model = "Тест", title = "Прерванная запись", date = "",
                target = "Mail WebDAV", root = "Models", account = "audit@example.com",
                entries = listOf(QueueStore.Entry(photo, "PENDING"), QueueStore.Entry(next, "PENDING")),
                sourceUrl = source
            ))
            QueueStore.beginWrite(compose.activity, id, 0, photo, "a".repeat(64))
            compose.activity.getSharedPreferences(
                com.gallerybridge.app.transfer.TransferService.PREFS, android.content.Context.MODE_PRIVATE
            ).edit().putBoolean(com.gallerybridge.app.transfer.TransferService.KEY_RUNNING, true).commit()
        }
        try {
            compose.activityRule.scenario.recreate()
            compose.runOnIdle {
                val restored = QueueStore.load(compose.activity)!!
                assertEquals(listOf("BLOCKED", "PENDING"), restored.entries.map { it.state })
                assertEquals(listOf(1), QueueStore.retryIndices(restored))
                assertTrue(!compose.activity.getSharedPreferences(
                    com.gallerybridge.app.transfer.TransferService.PREFS, android.content.Context.MODE_PRIVATE
                ).getBoolean(com.gallerybridge.app.transfer.TransferService.KEY_RUNNING, false))
                val digest = "a".repeat(64)
                assertEquals(digest, restored.entries[0].contentSha256)
                assertTrue(HistoryStore.record(compose.activity, restored, "Прервано"))
                assertTrue(!QueueStore.confirmBlockedReadback(compose.activity, restored, 0, "b".repeat(64)))
                assertEquals("Прервано", HistoryStore.load(compose.activity).single { it.id == id }.outcome)
                assertTrue(QueueStore.confirmBlockedReadback(compose.activity, restored, 0, digest))
                assertTrue(!QueueStore.confirmBlockedReadback(compose.activity, restored, 0, digest))
                assertEquals(listOf("DONE", "PENDING"), QueueStore.load(compose.activity)!!.entries.map { it.state })
                val record = HistoryStore.load(compose.activity).single { it.id == id }
                assertEquals(1, record.uploaded)
                assertEquals(0, record.failed)
                assertEquals("Частично", record.outcome)
                val gallery = GalleryCatalogStore.load(compose.activity).single { it.url == source }
                assertEquals(1, gallery.completed)
                assertEquals(2, gallery.total)
                assertEquals("Частично", gallery.outcome)
            }
        } finally {
            compose.runOnIdle {
                QueueStore.clearIfId(compose.activity, id)
                GalleryCatalogStore.remove(compose.activity, source)
                historyPrefs.edit().putString("records", previousHistory).commit()
            }
        }
    }

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
