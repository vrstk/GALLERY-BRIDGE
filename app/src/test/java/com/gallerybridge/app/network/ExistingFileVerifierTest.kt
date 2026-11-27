package com.gallerybridge.app.network

import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File

class ExistingFileVerifierTest {
    private fun withSource(bytes: ByteArray, block: (File) -> Unit) {
        val file = File.createTempFile("gb-existing-test", ".jpg")
        try {
            file.writeBytes(bytes)
            block(file)
        } finally {
            file.delete()
        }
    }

    @Test fun identicalBytesMayBeSkipped() = withSource(byteArrayOf(1, 2, 3, 4)) { source ->
        val verified = ExistingFileVerifier.verify(
            source, ByteArrayInputStream(byteArrayOf(1, 2, 3, 4)), 4
        )
        org.junit.Assert.assertEquals(4L, verified.bytes)
        org.junit.Assert.assertEquals(
            "9f64a747e1b97f131fabb6b447296c9b6f0201e79fb3c5356e6c77e89b6a806a",
            verified.sha256
        )
    }

    @Test fun sameLengthDifferentBytesAreBlocked() = withSource(byteArrayOf(1, 2, 3, 4)) { source ->
        assertThrows(ExistingFileVerifier.Unverified::class.java) {
            ExistingFileVerifier.verify(source, ByteArrayInputStream(byteArrayOf(1, 2, 4, 3)), 4)
        }
    }

    @Test fun truncationIsBlocked() = withSource(byteArrayOf(1, 2, 3, 4)) { source ->
        assertThrows(ExistingFileVerifier.Unverified::class.java) {
            ExistingFileVerifier.verify(source, ByteArrayInputStream(byteArrayOf(1, 2)), 2)
        }
    }

    @Test fun inconsistentProviderSizeIsBlocked() = withSource(byteArrayOf(1, 2, 3)) { source ->
        assertThrows(ExistingFileVerifier.Unverified::class.java) {
            ExistingFileVerifier.verify(source, ByteArrayInputStream(byteArrayOf(1, 2, 3)), 900)
        }
    }

    @Test fun unknownProviderSizeUsesSha256() = withSource(byteArrayOf(1, 2, 3)) { source ->
        ExistingFileVerifier.verify(source, ByteArrayInputStream(byteArrayOf(1, 2, 3)), null)
    }

    @Test fun emptyDestinationIsBlocked() = withSource(byteArrayOf(1)) { source ->
        assertThrows(ExistingFileVerifier.Unverified::class.java) {
            ExistingFileVerifier.verify(source, ByteArrayInputStream(byteArrayOf()), 0)
        }
    }

    @Test fun oversizedSourceIsBlocked() {
        val file = File.createTempFile("gb-large-existing", ".jpg")
        try {
            file.outputStream().use { it.channel.truncate(ExistingFileVerifier.MAX_BYTES + 1) }
            // truncate cannot grow a file; use RandomAccessFile to create a sparse file.
            java.io.RandomAccessFile(file, "rw").use {
                it.setLength(ExistingFileVerifier.MAX_BYTES + 1)
            }
            assertThrows(ExistingFileVerifier.Unverified::class.java) {
                ExistingFileVerifier.verify(file, ByteArrayInputStream(byteArrayOf(1)))
            }
        } finally { file.delete() }
    }

    @Test fun webDavGetMustBeHttpsSameHostAndMedia() {
        ExistingFileVerifier.requireSafeWebDavGet(200, "https", "webdav.cloud.mail.ru", "image/jpeg")
        for ((code, scheme, host, type) in listOf(
            listOf("404", "https", "webdav.cloud.mail.ru", "image/jpeg"),
            listOf("200", "http", "webdav.cloud.mail.ru", "image/jpeg"),
            listOf("200", "https", "example.com", "image/jpeg"),
            listOf("200", "https", "webdav.cloud.mail.ru", "text/html")
        )) {
            assertThrows(ExistingFileVerifier.Unverified::class.java) {
                ExistingFileVerifier.requireSafeWebDavGet(code.toInt(), scheme, host, type)
            }
        }
    }
}
