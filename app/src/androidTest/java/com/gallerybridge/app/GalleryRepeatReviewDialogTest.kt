package com.gallerybridge.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.gallerybridge.app.ui.GalleryRepeatReviewDialog
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class GalleryRepeatReviewDialogTest {
    @get:Rule val compose = createComposeRule()

    @Test fun repeatNeedsAnExplicitConfirmation() {
        var accepted = 0
        var dismissed = 0
        compose.setContent {
            MaterialTheme {
                GalleryRepeatReviewDialog(
                    onConfirm = { accepted++ },
                    onCancel = { dismissed++ }
                )
            }
        }
        compose.onNodeWithText("Проверка повторной передачи").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, accepted) }
        compose.onNodeWithText("Вернуться").performClick()
        compose.runOnIdle {
            assertEquals(0, accepted)
            assertEquals(1, dismissed)
        }
        compose.onNodeWithText("Подтвердить повтор").performClick()
        compose.runOnIdle { assertEquals(1, accepted) }
    }
}
