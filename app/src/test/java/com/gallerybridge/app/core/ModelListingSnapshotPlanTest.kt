package com.gallerybridge.app.core

import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test

class ModelListingSnapshotPlanTest {
    private fun page(number: Int, count: Int, slug: String = "example-model"): ModelListingSources.Page {
        val url = "https://www.elitebabes.com/model/$slug/sort/oldest/mpage/$number/"
        val cards = (1..count).joinToString("") {
            """<li><figure><a href="/photo-$number-$it/"></a><div class="img-overlay">
                <p><a href="/photo-$number-$it/">Photo $number-$it</a></p>
                </div></figure></li>"""
        }
        val pagination = (1..3).joinToString("") {
            """<a href="/model/$slug/sort/oldest/mpage/$it/">$it</a>"""
        }
        return ModelListingSources.parse(Jsoup.parse(
            """<html><head><title>Example Model (432 Galleries)</title></head>
               <body><main id="content"><article class="module-model">
               <h1>Example Model</h1></article><ul class="list-gallery has-mobile-menu">
               $cards</ul><div id="pagination_infinite">$pagination</div></main></body></html>""",url
        ),url)!!
    }

    @Test fun missingPageTwoIsExplicitAndDoesNotLieAboutCompleteness() {
        val planned = ModelListingSnapshotPlan.merge(emptyList(),listOf(page(1,20),page(3,20)))
        assertEquals(40,planned.result.photos.size)
        assertEquals(listOf("Нет снимка страницы №2."),planned.result.errors)
        assertTrue(planned.result.incomplete)
    }

    @Test fun laterFullPageReplacesInitialTwentyWithoutDoubleCounting() {
        val first = ModelListingSnapshotPlan.merge(emptyList(),listOf(page(1,20),page(3,20)))
        val merged = ModelListingSnapshotPlan.merge(first.pages,listOf(page(2,150),page(1,80)))
        assertEquals(3,merged.pages.size)
        assertEquals(250,merged.result.cards)
        assertEquals(250,merged.result.photos.size)
        assertTrue(merged.result.errors.isEmpty())
        assertTrue(merged.result.incomplete) // 432 published and 250 actually present
    }

    @Test fun mixedModelsCannotBeMerged() {
        assertThrows(IllegalArgumentException::class.java) {
            ModelListingSnapshotPlan.merge(listOf(page(1,10)),
                listOf(page(2,10,"other-model")))
        }
    }

    @Test fun samePageDuplicateNeverDoublesCount() {
        val planned = ModelListingSnapshotPlan.merge(emptyList(),listOf(page(1,20),page(1,20)))
        assertEquals(1,planned.pages.size)
        assertEquals(20,planned.result.cards)
    }
}
