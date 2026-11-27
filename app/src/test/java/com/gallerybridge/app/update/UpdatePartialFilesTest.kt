package com.gallerybridge.app.update

import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class UpdatePartialFilesTest {
    private fun fixture(): ByteArray =
        ByteArray(150_007) { ((it * 37) % 251).toByte() }

    private fun directory(): File =
        java.nio.file.Files.createTempDirectory("update-resume-").toFile()

    @Test fun networkFailureThenRangeResumePreservesExactBytes() = runBlocking {
        val directory = directory()
        try {
            val data = fixture()
            val part = UpdatePartialFiles.part(directory, UUID.randomUUID())
            val interrupted = object : InputStream() {
                var position = 0
                override fun read(): Int {
                    if (position == 45_000) throw IOException("Сеть отключилась")
                    return data[position++].toInt() and 0xff
                }
                override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
                    if (position == 45_000) throw IOException("Сеть отключилась")
                    val n = minOf(length, 45_000 - position)
                    System.arraycopy(data, position, bytes, offset, n)
                    position += n
                    return n
                }
            }
            try {
                UpdatePartialFiles.receive(part, data.size.toLong(), 0, 200,
                    null, interrupted)
                fail("Ожидалось сетевое прерывание")
            } catch (_: IOException) {}
            assertEquals(45_000L, part.length())
            UpdatePartialFiles.receive(
                part, data.size.toLong(), part.length(), 206,
                "bytes 45000-${data.lastIndex}/${data.size}",
                ByteArrayInputStream(data, 45_000, data.size - 45_000)
            )
            assertArrayEquals(data, part.readBytes())
            assertArrayEquals(
                MessageDigest.getInstance("SHA-256").digest(data),
                MessageDigest.getInstance("SHA-256").digest(part.readBytes())
            )
        } finally { directory.deleteRecursively() }
    }

    @Test fun ignoredRangeMustReplaceRatherThanAppend() = runBlocking {
        val directory = directory()
        try {
            val data = fixture()
            val part = UpdatePartialFiles.part(directory, UUID.randomUUID())
            part.writeBytes(data.copyOf(4_096))
            UpdatePartialFiles.receive(part, data.size.toLong(), 4_096, 200,
                null, ByteArrayInputStream(data))
            assertEquals(data.size.toLong(), part.length())
            assertArrayEquals(data, part.readBytes())
        } finally { directory.deleteRecursively() }
    }

    @Test fun invalidRangeMustNotOverwriteExistingPart() = runBlocking {
        val directory = directory()
        try {
            val data = fixture()
            val part = UpdatePartialFiles.part(directory, UUID.randomUUID())
            val prefix = data.copyOf(512)
            part.writeBytes(prefix)
            try {
                UpdatePartialFiles.receive(part, data.size.toLong(), 512, 206,
                    "bytes 500-${data.lastIndex}/${data.size}",
                    ByteArrayInputStream(data, 512, data.size - 512))
                fail("Неверный Content-Range должен быть отклонён")
            } catch (_: IllegalArgumentException) {}
            assertArrayEquals(prefix, part.readBytes())
        } finally { directory.deleteRecursively() }
    }

    @Test fun cancellationStopsWritingAndCleanupPreservesVerifiedApk() = runBlocking {
        val directory = directory()
        try {
            val data = fixture()
            val id = UUID.randomUUID()
            val part = UpdatePartialFiles.part(directory, id)
            val verified = File(directory, "verified.apk")
            verified.writeText("already verified")
            try {
                UpdatePartialFiles.receive(part, data.size.toLong(), 0, 200,
                    null, ByteArrayInputStream(data)) { count ->
                    if (count > 0) throw CancellationException("Отмена пользователем")
                }
                fail("Отмена должна остановить загрузку")
            } catch (_: CancellationException) {}
            assertTrue(part.length() > 0)
            assertEquals(0, UpdatePartialFiles.cleanOrphans(directory, setOf(id)))
            assertTrue(part.exists())
            assertEquals(1, UpdatePartialFiles.cleanOrphans(directory, emptySet()))
            assertFalse(part.exists())
            assertEquals("already verified", verified.readText())
            assertEquals(0, UpdatePartialFiles.cleanOrphans(directory, emptySet()))
        } finally { directory.deleteRecursively() }
    }

    @Test fun cleanupTouchesOnlyKnownUpdaterPartNames() {
        val directory = directory()
        try {
            val active = UUID.randomUUID()
            val stale = UUID.randomUUID()
            val kept = UpdatePartialFiles.part(directory, active)
            val remove = UpdatePartialFiles.part(directory, stale)
            kept.writeText("keep")
            remove.writeText("remove")
            File(directory, "partial.apk").writeText("legacy")
            File(directory, "arbitrary.part").writeText("not ours")
            File(directory, "verified.apk").writeText("verified")
            assertEquals(2, UpdatePartialFiles.cleanOrphans(directory, setOf(active)))
            assertTrue(kept.exists())
            assertFalse(remove.exists())
            assertTrue(File(directory, "arbitrary.part").exists())
            assertTrue(File(directory, "verified.apk").exists())
        } finally { directory.deleteRecursively() }
    }
}
