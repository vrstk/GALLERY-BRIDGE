package com.gallerybridge.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.gallerybridge.app.core.*
import com.gallerybridge.app.ui.ModelBatchPreflightDialog
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ModelBatchPreflightDialogTest {
    @get:Rule val compose = createComposeRule()

    private fun sample(withReady: Boolean): ModelBatchQueuePlan.Preview {
        val gallery = PreparedGallery("https://example.com/one", "1", "Model",
            "Title", "", listOf(Media("https://example.com/a.jpg", "a.jpg",
                Kind.PHOTO, "Ссылка")))
        return ModelBatchQueuePlan.Preview(
            setOf(gallery.sourceUrl, "https://example.com/two"),
            "Mail WebDAV", "Models", "test@example.org", null,
            ModelBatchQueuePlan.Preparation(
                if (withReady) listOf(gallery) else emptyList(),
                mapOf("https://example.com/two" to "Вторая галерея — HTTP 404")
            )
        )
    }

    @Test fun previewDoesNotStartTransferAndCancelIsExplicit() {
        var confirmed = 0
        var cancelled = 0
        compose.setContent {
            MaterialTheme {
                ModelBatchPreflightDialog(sample(true), { confirmed++ }, { cancelled++ })
            }
        }
        compose.onNodeWithText("Готово: 1 галерей · 1 фотографий").assertIsDisplayed()
        compose.onNodeWithText("Недоступно: 1").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, confirmed) }
        compose.onNodeWithTag("cancel-model-batch").performClick()
        compose.runOnIdle {
            assertEquals(0, confirmed)
            assertEquals(1, cancelled)
        }
    }

    @Test fun emptyPlanCannotBeStarted() {
        compose.setContent {
            MaterialTheme {
                ModelBatchPreflightDialog(sample(false), {}, {})
            }
        }
        compose.onNodeWithTag("confirm-model-batch").assertIsNotEnabled()
    }
}
