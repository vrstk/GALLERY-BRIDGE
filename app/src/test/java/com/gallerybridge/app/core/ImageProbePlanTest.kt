package com.gallerybridge.app.core

import org.junit.Assert.*
import org.junit.Test

class ImageProbePlanTest {
    private fun photo(i: Int, kind: Kind = Kind.PHOTO) =
        Media("https://cdn.example.org/$i.jpg", "$i.jpg", kind, "Ссылка")

    @Test fun inspectsOnlySelectedPhotos() {
        val photos = listOf(photo(1), photo(2), photo(3, Kind.VIDEO))
        assertEquals(listOf(photos[1]), ImageProbePlan.candidates(photos, setOf(photos[1].url, photos[2].url)))
    }

    @Test fun boundsRequestsWithoutChangingInput() {
        val photos = (0 until 300).map(::photo)
        val urls = photos.map { it.url }.toSet()
        assertEquals(300, ImageProbePlan.selectedPhotoCount(photos, urls))
        assertEquals(40, ImageProbePlan.candidates(photos, urls).size)
        assertEquals(300, photos.size)
    }

    @Test fun emptySelectionNeverMakesRequests() {
        assertTrue(ImageProbePlan.candidates(listOf(photo(1)), emptySet()).isEmpty())
    }
}
