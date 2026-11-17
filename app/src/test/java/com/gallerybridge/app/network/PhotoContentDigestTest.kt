package com.gallerybridge.app.network

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.RandomAccessFile

class PhotoContentDigestTest {
    private fun withBytes(bytes: ByteArray, check: (File) -> Unit) {
        val file = File.createTempFile("gb-digest-test-", ".jpg")
        try {
            file.writeBytes(bytes)
            check(file)
        } finally {
            file.delete()
        }
    }

    @Test fun actualSourceBytesNotFilenameDetermineDigest() {
        withBytes("abc".toByteArray()) {
            assertEquals(
                "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                PhotoContentDigest.sha256(it)
            )
        }
        withBytes("abd".toByteArray()) {
            assertNotEquals(
                "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                PhotoContentDigest.sha256(it)
            )
        }
    }

    @Test fun zeroLengthSourceIsNotConfirmed() = withBytes(byteArrayOf()) {
        assertThrows(IllegalArgumentException::class.java) { PhotoContentDigest.sha256(it) }
    }

    @Test fun oversizedSourceIsRejectedBeforeReading() {
        val source = File.createTempFile("gb-oversize-", ".jpg")
        try {
            RandomAccessFile(source, "rw").use { it.setLength(PhotoContentDigest.MAX_SOURCE_BYTES + 1) }
            assertThrows(IllegalArgumentException::class.java) { PhotoContentDigest.sha256(source) }
        } finally {
            source.delete()
        }
    }
}
