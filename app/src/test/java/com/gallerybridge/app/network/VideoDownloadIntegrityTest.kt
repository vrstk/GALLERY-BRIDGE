package com.gallerybridge.app.network

import org.junit.Assert.*
import org.junit.Test

class VideoDownloadIntegrityTest {
    @Test fun partialHttp206MustNeverPass() {
        assertThrows(IllegalArgumentException::class.java) {
            VideoDownloadIntegrity.requireCompleteResponse(
                206, "video/mp4", "bytes 0-1023/999999", 1024)
        }
        assertThrows(IllegalStateException::class.java) {
            VideoDownloadIntegrity.requireCompleteResponse(
                206, "video/mp4", null, 1024)
        }
    }

    @Test fun fullRangeAcceptedOnlyWithExactContentLength() {
        VideoDownloadIntegrity.requireCompleteResponse(
            206, "video/mp4", "bytes 0-1023/1024", 1024)
        assertThrows(IllegalArgumentException::class.java) {
            VideoDownloadIntegrity.requireCompleteResponse(
                206, "video/mp4", "bytes 0-1023/1024", 1023)
        }
    }

    @Test fun http200MustNotContainRangeAndMustBeVideo() {
        VideoDownloadIntegrity.requireCompleteResponse(200, "video/mp4", null, 1234)
        assertThrows(IllegalArgumentException::class.java) {
            VideoDownloadIntegrity.requireCompleteResponse(
                200, "text/html", null, 1234)
        }
        assertThrows(IllegalArgumentException::class.java) {
            VideoDownloadIntegrity.requireCompleteResponse(
                200, "video/mp4", "bytes 0-1023/2048", 1024)
        }
    }
}
