package com.gallerybridge.app.core

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Common trust boundary for source adapters. Hosts must be explicitly configured;
 * recognizing a URL never implies permission to retrieve its contents.
 */
internal class SourceOriginPolicy(private val allowedHosts: Set<String>) {
    fun parse(raw: String): HttpUrl? {
        val url = raw.toHttpUrlOrNull() ?: return null
        if (url.scheme != "https" || url.port != 443 ||
            url.host !in allowedHosts || url.encodedUsername.isNotEmpty() ||
            url.encodedPassword.isNotEmpty() || url.fragment != null) return null
        return url
    }
}

internal object SourceOrigins {
    // Existing source only. New adapters need their own independently reviewed policy.
    val modelIndex = SourceOriginPolicy(setOf("elitebabes.com", "www.elitebabes.com"))
}
