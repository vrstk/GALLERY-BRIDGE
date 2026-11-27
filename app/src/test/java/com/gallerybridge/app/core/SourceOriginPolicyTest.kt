package com.gallerybridge.app.core

import org.junit.Assert.*
import org.junit.Test

class SourceOriginPolicyTest {
    private val policy = SourceOriginPolicy(setOf("example.com", "www.example.com"))

    @Test fun permitsOnlyExplicitHttpsOrigins() {
        assertNotNull(policy.parse("https://example.com/gallery"))
        assertNotNull(policy.parse("https://www.example.com/gallery?sort=oldest"))
        for (url in listOf(
            "http://example.com/gallery",
            "https://example.com:8443/gallery",
            "https://other.example.com/gallery",
            "https://example.com.evil.test/gallery",
            "https://evil.test/example.com",
            "https://user:password@example.com/gallery",
            "https://example.com/gallery#fragment",
            "file:///tmp/gallery"
        )) assertNull(url, policy.parse(url))
    }

    @Test fun modelListingRejectsCredentialsAndOverflowedPage() {
        val base = "https://www.elitebabes.com/model/example-model/sort/oldest/"
        assertTrue(ModelListingSources.accepts(base))
        assertTrue(ModelListingSources.accepts(base + "mpage/100/"))
        assertFalse(ModelListingSources.accepts(base + "mpage/101/"))
        assertFalse(ModelListingSources.accepts(base + "mpage/999999999999999999999/"))
        assertFalse(ModelListingSources.accepts(base.replace("https://", "https://user@")))
        assertNull(ModelListingSources.publicationUrl("https://user@www.elitebabes.com/example/"))
    }

    @Test fun apiRejectsAmbiguousQueryAndEmbeddedCredentials() {
        val base = "https://www.elitebabes.com/gridapi/?content=model&nr=1&sort=oldest"
        assertNotNull(EliteBabesModelAdapter.validatedApiUrl(base))
        assertNull(EliteBabesModelAdapter.validatedApiUrl(base + "&nr=2"))
        assertNull(EliteBabesModelAdapter.validatedApiUrl(base.replace("https://", "https://user@")))
    }
}
