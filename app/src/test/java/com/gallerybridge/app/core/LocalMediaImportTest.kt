package com.gallerybridge.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocalMediaImportTest {
    @Test fun recognizesVideoFilesFromContentProviderMime() {
        assertEquals(Kind.VIDEO, LocalMediaImport.kindFor("video/mp4", "opaque-name"))
        assertEquals(Kind.VIDEO, LocalMediaImport.kindFor("application/octet-stream", "movie.MP4"))
    }

    @Test fun recognizesPhotosFromDeclaredTypeOrFileExtension() {
        assertEquals(Kind.PHOTO, LocalMediaImport.kindFor("image/jpeg", "photo"))
        assertEquals(Kind.PHOTO, LocalMediaImport.kindFor(null, "picture.webp"))
    }

    @Test fun rejectsUnrelatedMediaTypes() {
        assertNull(LocalMediaImport.kindFor("application/pdf", "document.pdf"))
        assertNull(LocalMediaImport.kindFor("audio/mpeg", "sound.mp3"))
    }
}
