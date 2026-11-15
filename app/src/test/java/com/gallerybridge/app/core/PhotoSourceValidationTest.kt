package com.gallerybridge.app.core

import org.junit.Assert.assertThrows
import org.junit.Test

class PhotoSourceValidationTest {
    @Test fun acceptsJpegHeader() {
        PhotoSourceValidation.validateHeader(
            byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0x00),
            "image/jpeg"
        )
    }

    @Test fun rejectsHtmlEvenWhenPretendingToBeAnImage() {
        assertThrows(IllegalArgumentException::class.java) {
            PhotoSourceValidation.validateHeader("<!DOCTYPE html><html>403 denied</html>".toByteArray(), "image/jpeg")
        }
    }

    @Test fun rejectsJsonErrorAndUnexpectedVideoMime() {
        assertThrows(IllegalArgumentException::class.java) {
            PhotoSourceValidation.validateHeader("""{"error":"denied"}""".toByteArray(), "image/jpeg")
        }
        assertThrows(IllegalArgumentException::class.java) {
            PhotoSourceValidation.validateHeader(byteArrayOf(0x01), "video/mp4")
        }
    }

    @Test fun rejectsEmptyFiles() {
        assertThrows(IllegalArgumentException::class.java) {
            PhotoSourceValidation.validateHeader(byteArrayOf(), "image/png")
        }
    }
}
