package com.gallerybridge.app.network

import org.junit.Assert.assertTrue
import org.junit.Test

class SourceDownloadFailureTest {
    @Test fun forbiddenClearlyIdentifiesTheMediaSource() {
        val message = SourceDownloadFailure.describe(403)
        assertTrue(message.contains("Источник медиа"))
        assertTrue(message.contains("HTTP 403"))
        assertTrue(message.contains("отказал в доступе"))
    }

    @Test fun missingLinkAndRateLimitHaveDistinctDescriptions() {
        assertTrue(SourceDownloadFailure.describe(404).contains("ссылка устарела"))
        assertTrue(SourceDownloadFailure.describe(429).contains("частоту запросов"))
    }
}
