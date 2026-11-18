package com.gallerybridge.app

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.gallerybridge.app.ui.GalleryPage
import com.gallerybridge.app.ui.GalleryShell
import com.gallerybridge.app.ui.GalleryTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Visual identity regression: no network, credentials, or persistent app data. */
class GalleryVisualThemeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun darkAndLightPalettesRemainDistinctAndAccessible() {
        val dark = mutableStateOf(true)
        var sampled: ColorScheme? = null
        compose.setContent {
            GalleryTheme(dark = dark.value) {
                val colors = MaterialTheme.colorScheme
                SideEffect { sampled = colors }
                Text("Цветовая тема")
            }
        }
        compose.onNodeWithText("Цветовая тема").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(Color(0xFFC58BFF), sampled!!.primary)
            assertEquals(Color(0xFF0C0B12), sampled!!.background)
            assertEquals(Color(0xFFF7F4FF), sampled!!.onBackground)
            dark.value = false
        }
        compose.runOnIdle {
            assertEquals(Color(0xFF7939B8), sampled!!.primary)
            assertEquals(Color(0xFFFAF8FD), sampled!!.background)
            assertEquals(Color(0xFF221C2D), sampled!!.onBackground)
        }
    }

    @Test fun brandedHeaderRetainsReadableTextAndExistingNavigation() {
        val page = mutableStateOf(GalleryPage.HOME)
        compose.setContent {
            GalleryTheme(dark = true) {
                GalleryShell(
                    page = page.value, onNavigate = { page.value = it },
                    onSettings = {}, onBack = {}
                ) {
                    Text("Содержимое экрана")
                }
            }
        }
        compose.onNodeWithText("GALLERY BRIDGE").assertIsDisplayed()
        compose.onNodeWithText("Главная").assertIsDisplayed()
        compose.onNodeWithText("Очередь").performClick()
        compose.onNodeWithText("Очередь загрузок").assertIsDisplayed()
    }
}
