package com.gallerybridge.app

import androidx.core.content.FileProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UpdaterProviderTest {
    @Test fun servesTemporaryApkUsingContentUri() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "updates")
        assertTrue(directory.isDirectory || directory.mkdirs())
        val file = File(directory, "provider-probe.apk")
        val expected = byteArrayOf(1, 2, 3, 4)
        try {
            file.writeBytes(expected)
            val uri = FileProvider.getUriForFile(
                context, "${context.packageName}.updater.provider", file
            )
            assertEquals("content", uri.scheme)
            assertEquals("${context.packageName}.updater.provider", uri.authority)
            val actual = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
            assertArrayEquals(expected, actual)
        } finally { file.delete() }
    }
}
