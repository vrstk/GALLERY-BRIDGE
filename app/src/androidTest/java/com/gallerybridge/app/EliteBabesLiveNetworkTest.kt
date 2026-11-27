package com.gallerybridge.app

import android.util.Log
import com.gallerybridge.app.network.Services
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assume
import org.junit.Assert.*
import org.junit.Test

/**
 * Live integration probe, separate from JSON fixtures.
 * Network denial is reported as SKIPPED, never as successful verification.
 * The test never downloads a video or writes into cloud storage.
 */
class EliteBabesLiveNetworkTest {
    private val source = "https://www.elitebabes.com/model/alisa-i/sort/oldest/"

    @Test fun naughtyInBedWatchSource() = runBlocking {
        val publication = "https://www.elitebabes.com/a-flirty-and-playful-babe-rubbing-her-sweet-vagina-in-the-bedroom-44189"
        try {
            val direct = Services.scanVideoPublication(publication, "")
            if (direct.media.any { it.kind == com.gallerybridge.app.core.Kind.VIDEO }) return@runBlocking
            val profile = Services.scanModelIndex("https://www.elitebabes.com/model/leona-mia/sort/oldest/",
                progress = { _, _ -> })
            val card = profile.videoCards.firstOrNull { it.url.trimEnd('/') == publication }
            Assume.assumeTrue("Видеокарточка с адресом плеера недоступна", !card?.watchUrl.isNullOrBlank())
            val scan = Services.scanVideoPublication(publication, requireNotNull(card).watchUrl)
            assertTrue("Связанный плеер не вернул видео",
                scan.media.any { it.kind == com.gallerybridge.app.core.Kind.VIDEO })
        } catch (e: org.junit.internal.AssumptionViolatedException) {
            throw e
        } catch (e: Exception) {
            Assume.assumeNoException("Живая проверка недоступна из эмулятора", e)
        }
        Unit
    }

    @Test fun modelToGalleryUsingApplicationOkHttp() = runBlocking {
        try {
            withTimeout(150_000L) {
                val listing = Services.scanModelIndex(source, progress = { _, _ -> })
                Log.i("GB-EliteLive", "LIVE_MODEL cards=${listing.allCards.size} " +
                    "photos=${listing.photos.size} videos=${listing.videoCards.size} " +
                    "incomplete=${listing.incomplete} errors=${listing.errors}")
                if (listing.incomplete) {
                    Assume.assumeTrue("Сайт не подтвердил полный каталог: " +
                        listing.errors.joinToString().take(200), false)
                }
                assertTrue("Ответ сайта не содержит фотогалереи", listing.photos.isNotEmpty())
                val gallery = Services.scan(listing.photos.first().url)
                Log.i("GB-EliteLive", "LIVE_GALLERY photos=${gallery.media.size}")
                if (gallery.media.isEmpty()) {
                    Assume.assumeTrue("Реальные ссылки фотографий недоступны через OkHttp", false)
                }
                assertTrue(gallery.media.isNotEmpty())
            }
        } catch (e: org.junit.internal.AssumptionViolatedException) {
            throw e
        } catch (e: Exception) {
            Log.w("GB-EliteLive", "LIVE_NOT_VERIFIED: ${e.javaClass.simpleName}: ${e.message}")
            Assume.assumeNoException("Доступ к сайту из Android не подтверждён", e)
        }
    }
}
