package com.gallerybridge.app

import androidx.test.platform.app.InstrumentationRegistry
import com.gallerybridge.app.core.CatalogFavorites
import org.junit.Assert.*
import org.junit.Test

class CatalogFavoritesPersistenceTest {
    @Test fun explicitFavoriteSurvivesFreshPreferencesReadAndCanBeRemoved() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val key = "qa model favorite 121"
        val original = CatalogFavorites.load(context)
        try {
            CatalogFavorites.set(context, key, true)
            assertTrue(key in CatalogFavorites.load(context.applicationContext))
            CatalogFavorites.set(context, key, false)
            assertFalse(key in CatalogFavorites.load(context))
            assertEquals(original - key, CatalogFavorites.load(context))
        } finally {
            CatalogFavorites.set(context, key, key in original)
        }
    }
}
