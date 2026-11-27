package com.gallerybridge.app.network

import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okio.BufferedSink

/** A lost PUT response leaves the write uncertain. Read requests may retry independently. */
internal class WebDavUpload(client: OkHttpClient) {
    private val writer = client.newBuilder()
        .retryOnConnectionFailure(false)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    fun put(url: HttpUrl, authorization: String, body: RequestBody): Response {
        // Mark the body non-repeatable so HTTP follow-ups (including 503 with
        // Retry-After: 0) cannot send the same payload a second time.
        val singleAttemptBody = object : RequestBody() {
            override fun contentType() = body.contentType()
            override fun contentLength() = body.contentLength()
            override fun isOneShot() = true
            override fun writeTo(sink: BufferedSink) = body.writeTo(sink)
        }
        return writer.newCall(Request.Builder().url(url)
            .header("Authorization", authorization)
            .header("If-None-Match", "*")
            .put(singleAttemptBody)
            .build()).execute()
    }
}
