package com.gallerybridge.app.network

import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Test

class SourceDownloadFailureTest {
    @Test fun forbiddenClearlyIdentifiesTheMediaSource() {
        val message = SourceDownloadFailure.describe(403)
        assertTrue(message.contains("Источник медиа"))
        assertTrue(message.contains("HTTP 403"))
        assertTrue(message.contains("отказал в доступе"))
    }

    @Test fun rateLimitAndServerOverloadPauseRemainingQueue() {
        assertTrue(SourceDownloadFailure.shouldPauseQueue(429))
        assertTrue(SourceDownloadFailure.shouldPauseQueue(503))
        assertTrue(SourceDownloadFailure.shouldPauseQueue(500))
        assertFalse(SourceDownloadFailure.shouldPauseQueue(404))
        assertFalse(SourceDownloadFailure.shouldPauseQueue(403))
        assertFalse(SourceDownloadFailure.shouldPauseQueue(200))
        val error = SourceDownloadFailure.HttpError(429)
        assertEquals(429, error.statusCode)
        assertTrue(error.message.orEmpty().contains("частоту запросов"))
    }

    @Test fun missingLinkAndRateLimitHaveDistinctDescriptions() {
        assertTrue(SourceDownloadFailure.describe(404).contains("ссылка устарела"))
        assertTrue(SourceDownloadFailure.describe(429).contains("частоту запросов"))
    }
}
