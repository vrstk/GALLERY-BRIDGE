package com.gallerybridge.app.core

import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test

class PageMetadataTest {
    private val site = "https://www.elitebabes.com/one-gallery-44797"

    @Test fun leonaMiaCanBeSuggestedButDateNotInvented() {
        val title = "Leona Mia Nude in Crystal - Free Metart Picture Gallery at Elite Babes"
        val fields = PageMetadata.extract(Jsoup.parse("<title>$title</title>"), site, title)
        assertEquals("Leona Mia", fields.model)
        assertTrue(fields.modelSource.isNotBlank())
        assertEquals("", fields.publishedDate)
    }

    @Test fun explicitArticleDateIsTrustedAndNormalized() {
        val html = """<html><head>
            <meta property="article:published_time" content="2020-10-13T09:00:00Z">
            </head></html>"""
        val fields = PageMetadata.extract(Jsoup.parse(html), site, "Leona Mia Nude in Crystal")
        assertEquals("2020-10-13", fields.publishedDate)
        assertTrue(fields.dateSource.isNotBlank())
    }

    @Test fun structuredArticleDateIsRead() {
        val html = """<script type="application/ld+json">
            {"@graph":[{"@type":"WebSite","datePublished":"2026-01-01"},
             {"@type":"ImageGallery","datePublished":"2020-10-13"}]}
            </script>"""
        val fields = PageMetadata.extract(Jsoup.parse(html), site, "Leona Mia Nude in Crystal")
        assertEquals("2020-10-13", fields.publishedDate)
    }

    @Test fun rejectsInvalidAndUnrelatedDates() {
        val html = """<head><meta name="modified" content="2020-10-13">
            <meta property="article:published_time" content="2020-02-30"></head>
            <aside><time datetime="2019-07-05"></time></aside>"""
        val f = PageMetadata.extract(Jsoup.parse(html), site, "Leona Mia Nude in Crystal")
        assertEquals("", f.publishedDate)
    }

    @Test fun readsDateOnlyFromMainArticle() {
        val html = """<aside><time datetime="2024-01-01"></time></aside>
            <article><time datetime="2020-10-13"></time></article>"""
        val f = PageMetadata.extract(Jsoup.parse(html), site, "Leona Mia Nude in Crystal")
        assertEquals("2020-10-13", f.publishedDate)
    }

    @Test fun doesNotGuessModelFromUnrelatedSiteOrGenericTitle() {
        val doc = Jsoup.parse("")
        assertEquals("", PageMetadata.extract(doc, "https://example.com/gallery", "Leona Mia Nude in Crystal").model)
        assertEquals("", PageMetadata.extract(doc, site, "Brunette Sweetheart Stuns Us").model)
    }

    @Test fun anotherSiteTitlePatternIsSupportedForEliteBabes() {
        val title = "Leona Mia in Sex Art set Opening Up - Elite Babes"
        assertEquals("Leona Mia", PageMetadata.extract(Jsoup.parse(""), site, title).model)
    }
}
