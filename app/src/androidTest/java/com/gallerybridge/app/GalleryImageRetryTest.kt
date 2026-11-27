package com.gallerybridge.app

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.gallerybridge.app.core.Kind
import com.gallerybridge.app.core.Media
import com.gallerybridge.app.ui.CachedGalleryImage
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class GalleryImageRetryTest {
    @get:Rule val compose = createComposeRule()

    @Test fun aFailedPreviewCanBeRetriedAtTheSameUrl() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File.createTempFile("gb-preview-retry-", ".png", context.cacheDir)
        val rendered = AtomicBoolean(false)
        val media = Media(Uri.fromFile(file).toString(), "Тестовое изображение", Kind.PHOTO, "Тест")
        val retryLabel = "Повторить загрузку изображения: ${media.name}"
        try {
            compose.setContent {
                MaterialTheme {
                    CachedGalleryImage(
                        media = media,
                        modifier = Modifier.size(88.dp),
                        contentScale = ContentScale.Fit,
                        onAspectRatio = { rendered.set(true) }
                    )
                }
            }
            compose.waitUntil(5_000) {
                compose.onAllNodesWithContentDescription(retryLabel).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithContentDescription(retryLabel).assertIsDisplayed()
            val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
            try {
                file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            } finally {
                bitmap.recycle()
            }
            compose.onNodeWithContentDescription(retryLabel).performClick()
            compose.waitUntil(5_000) { rendered.get() }
            compose.onNodeWithContentDescription(retryLabel).assertDoesNotExist()
        } finally {
            file.delete()
        }
    }
}
