package com.gallerybridge.app.core

import org.junit.Assert.*
import org.junit.Test

class VariantComparisonTest {
    private fun image(name: String, dir: String = "gallery", source: String = "Ссылка") =
        Media("https://cdn.example.org/$dir/$name", name, Kind.PHOTO, source)

    @Test fun matchesKnownVariantOnly() {
        val main = image("0002-01_1200.jpg")
        val preview = image("0002-01_w400.jpg", source="Изображение (качество не подтверждено)")
        val unrelated = image("another_w400.jpg")
        val pairs = VariantComparison.pairs(listOf(main, preview, unrelated), setOf(main.url))
        assertEquals(1, pairs.size)
        assertEquals(preview.url, pairs.single().alternative.url)
    }

    @Test fun neverMatchesDifferentDirectories() {
        val main = image("0002-01_1200.jpg")
        val alt = image("0002-01_1800.jpg", dir="other")
        assertTrue(VariantComparison.pairs(listOf(main, alt), setOf(main.url)).isEmpty())
    }

    @Test fun limitsToTwentyPairs() {
        val all = (1..120).flatMap { n ->
            listOf(image("img${n}_1200.jpg"), image("img${n}_w400.jpg"))
        }
        val selected = all.filter { it.name.endsWith("_1200.jpg") }.map { it.url }.toSet()
        val pairs = VariantComparison.pairs(all, selected)
        assertEquals(20, pairs.size)
        assertEquals(40, VariantComparison.candidates(pairs).size)
    }

    @Test fun compareActualPixelAreaAndNoReplacementOnFailure() {
        val main = image("0002-01_1200.jpg")
        val alt = image("0002-01_1800.jpg")
        val pair = VariantComparison.Pairing(main, alt)
        val actual = mapOf(
            main.url to VariantComparison.Dimensions(1200, 1800),
            alt.url to VariantComparison.Dimensions(1800, 1800)
        )
        assertEquals(1, VariantComparison.comparableCount(listOf(pair), actual))
        assertEquals(alt.url, VariantComparison.improvements(listOf(pair), actual).single().alternative.url)
        assertTrue(VariantComparison.improvements(listOf(pair), mapOf(main.url to actual.getValue(main.url))).isEmpty())
    }

    @Test fun smallerThumbnailNeverConsideredImprovement() {
        val main = image("0002-01_1200.jpg")
        val alt = image("0002-01_w400.jpg")
        val measured = mapOf(
            main.url to VariantComparison.Dimensions(1200, 1800),
            alt.url to VariantComparison.Dimensions(400, 600)
        )
        assertTrue(VariantComparison.improvements(listOf(VariantComparison.Pairing(main, alt)), measured).isEmpty())
    }
}
