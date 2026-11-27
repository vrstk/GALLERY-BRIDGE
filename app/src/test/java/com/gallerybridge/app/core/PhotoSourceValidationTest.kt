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
    @Test fun recognizesSupportedPhotoSignatures() {
        PhotoSourceValidation.validateHeader(
            byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a),
            "image/png"
        )
        PhotoSourceValidation.validateHeader("GIF89a....".toByteArray(), "image/gif")
        PhotoSourceValidation.validateHeader("RIFF1234WEBP".toByteArray(), "image/webp")
        PhotoSourceValidation.validateHeader(
            "BM".toByteArray() + ByteArray(12), "image/x-ms-bmp"
        )
        PhotoSourceValidation.validateHeader(
            byteArrayOf(0, 0, 0, 24) + "ftypavif".toByteArray() + ByteArray(12),
            "image/avif"
        )
    }

    @Test fun rejectsMimeMismatchAndRandomBinary() {
        val jpeg = byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte(), 0xe0.toByte(), 0)
        PhotoSourceValidation.validateHeader(jpeg, "application/octet-stream")
        assertThrows(IllegalArgumentException::class.java) {
            PhotoSourceValidation.validateHeader(jpeg, "image/png")
        }
        assertThrows(IllegalArgumentException::class.java) {
            PhotoSourceValidation.validateHeader(byteArrayOf(0, 1, 2, 3), "image/jpeg")
        }
    }

    @Test fun rejectsTruncatedImageOrDisguisedVideoHeader() {
        assertThrows(IllegalArgumentException::class.java) {
            PhotoSourceValidation.validateHeader(
                byteArrayOf(0x89.toByte(), 0x50, 0x4e), "image/png"
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            PhotoSourceValidation.validateHeader(
                byteArrayOf(0xff.toByte(), 0xd8.toByte()), "image/jpeg"
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            PhotoSourceValidation.validateHeader(
                byteArrayOf(0, 0, 0, 18) + "ftypisom".toByteArray(), "image/avif"
            )
        }
    }

}
