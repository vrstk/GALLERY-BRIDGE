package com.gallerybridge.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import com.gallerybridge.app.ui.GalleryPublicationDateField
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** No catalog writes, transfers, credentials or external providers. */
class GalleryPublicationDateFieldTest {
    @get:Rule val compose = createComposeRule()

    @Test fun typingDigitsInsertsIsoSeparators() {
        val date = mutableStateOf("")
        compose.setContent {
            MaterialTheme {
                GalleryPublicationDateField(
                    value = date.value,
                    onValueChange = { date.value = it }
                )
            }
        }
        compose.onNodeWithTag("publication-date-field").performTextInput("20250829")
        compose.onNodeWithTag("publication-date-field").assertExists()
        compose.runOnIdle { assertEquals("2025-08-29", date.value) }
    }

    @Test fun invalidDayIsShownButNotSilentlyCorrected() {
        val date = mutableStateOf("")
        compose.setContent {
            MaterialTheme {
                GalleryPublicationDateField(
                    value = date.value, onValueChange = { date.value = it }
                )
            }
        }
        compose.onNodeWithTag("publication-date-field").performTextReplacement("20250230")
        compose.runOnIdle { assertEquals("2025-02-30", date.value) }
        compose.onNodeWithText("Укажите реальную дату").assertIsDisplayed()
    }
}
