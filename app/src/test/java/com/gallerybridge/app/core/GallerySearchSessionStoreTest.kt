package com.gallerybridge.app.core

import org.junit.Assert.*
import org.junit.Test

class GallerySearchSessionStoreTest {
    @Test fun restoresPhotoSelectionWithoutInventingExtraPhotos() {
        val one = Media("https://cdn.example.com/one.jpg", "one.jpg", Kind.PHOTO, "Ссылка")
        val two = Media("https://cdn.example.com/two.jpg", "two.jpg", Kind.PHOTO, "Ссылка")
        val snapshot = GallerySearchSessionStore.Snapshot(
            "https://www.example.com/gallery/", "M", "T", "", "DOWNLOADS",
            listOf(one, two), setOf(one.url), setOf(one.url, two.url))
        val parsed = requireNotNull(GallerySearchSessionStore.decode(GallerySearchSessionStore.encode(snapshot)))
        assertEquals(2, parsed.items.size)
        assertEquals(setOf(one.url), parsed.selected)
        assertEquals(setOf(one.url, two.url), parsed.recommended)
        assertEquals(snapshot.url, parsed.url)
    }

    @Test fun invalidUrlsAndVideosAreNotRestoredAndSelectionIsBoundToItems() {
        val photo = Media("https://example.com/g.jpg", "g.jpg", Kind.PHOTO, "test")
        val video = Media("https://example.com/v.mp4", "v.mp4", Kind.VIDEO, "test")
        val unsafe = Media("file:///tmp/secrets.jpg", "secrets.jpg", Kind.PHOTO, "test")
        val snapshot = GallerySearchSessionStore.Snapshot(
            "", "", "", "", "HOME", listOf(photo, video, unsafe),
            setOf(photo.url, video.url, unsafe.url, "https://example.com/not-listed"),
            setOf(video.url))
        val parsed = requireNotNull(GallerySearchSessionStore.decode(GallerySearchSessionStore.encode(snapshot)))
        assertEquals(listOf(photo), parsed.items)
        assertEquals(setOf(photo.url), parsed.selected)
        assertTrue(parsed.recommended.isEmpty())
    }

    @Test fun rejectsBadState() {
        assertNull(GallerySearchSessionStore.decode("{}"))
        assertNull(GallerySearchSessionStore.decode(
            """{"format":1,"url":"http://example.com","items":[]}"""))
    }
}
