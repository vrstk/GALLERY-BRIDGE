package com.gallerybridge.app.network

import com.gallerybridge.app.core.Kind
import com.gallerybridge.app.core.Media
import org.junit.Assert.*
import org.junit.Test

class VideoAvailabilityProbeTest {
    private val sample = Media("https://example.com/clip.mp4", "clip.mp4", Kind.VIDEO, "Видео")

    @Test fun forbiddenMediaIsBlocked() {
        for (code in listOf(401, 403, 404, 410)) {
            val result = VideoAvailabilityProbe.classify(sample.url, code, "text/html")
            assertTrue(result.blocked)
            assertTrue(result.message.contains("HTTP $code"))
        }
    }

    @Test fun htmlResponseDoesNotCountAsVideo() {
        assertTrue(VideoAvailabilityProbe.classify(sample.url, 200, "text/html; charset=UTF-8").blocked)
    }

    @Test fun successfulHeadersDoNotGuaranteeEntireFile() {
        val result = VideoAvailabilityProbe.classify(sample.url, 200, "video/mp4")
        assertEquals(VideoAvailabilityProbe.Verdict.ACCEPTED, result.verdict)
        assertTrue(result.message.contains("весь файл не проверен"))
    }

    @Test fun rateLimitsAreUncertain() {
        assertEquals(VideoAvailabilityProbe.Verdict.UNCERTAIN,
            VideoAvailabilityProbe.classify(sample.url, 429, null).verdict)
    }

    @Test fun summaryDoesNotCountUnverifiedLinksAsBlocked() {
        val variants = listOf(sample, sample.copy(url = "https://example.com/clip_720.mp4"))
        val denied = VideoAvailabilityProbe.classify(sample.url, 403, "text/html")
        val summary = VideoAvailabilityProbe.summary(variants, mapOf(sample.url to denied))
        assertEquals(2, summary.total)
        assertEquals(1, summary.checked)
        assertEquals(1, summary.notChecked)
        assertFalse(summary.allBlocked)
    }

    @Test fun allVariantsBlockedOnlyAfterEachWasChecked() {
        val variants = listOf(sample, sample.copy(url = "https://example.com/clip_180.mp4"))
        val results = variants.associate { it.url to VideoAvailabilityProbe.classify(it.url, 403, "text/html") }
        val summary = VideoAvailabilityProbe.summary(variants, results)
        assertTrue(summary.allBlocked)
        assertEquals(2, summary.blocked)
        assertEquals(0, summary.accepted)
    }

    @Test fun allCandidateCheckIsNotDependentOnSelectedQuality() {
        val variants = (1..3).map { sample.copy(url = "https://example.com/clip_${it}00.mp4") }
        assertEquals(3, VideoAvailabilityProbe.allCandidates(variants).size)
    }

    @Test fun capsSelectedVideosToTwelve() {
        val all = (1..30).map { sample.copy(url = "https://example.com/$it.mp4") }
        assertEquals(12, VideoAvailabilityProbe.candidates(all, all.map { it.url }.toSet()).size)
    }
}
