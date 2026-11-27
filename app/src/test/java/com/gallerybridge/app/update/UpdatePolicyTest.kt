package com.gallerybridge.app.update

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class UpdatePolicyTest {
    private fun payload(code: Int = 33, version: String = "0.33.0",
                        pkg: String = UpdatePolicy.PACKAGE, cert: String = UpdatePolicy.CERTIFICATE,
                        hash: String = "a".repeat(64), size: Long = 11411119L,
                        url: String = "https://github.com/vrstk/GALLERY-BRIDGE/releases/download/v0.33.0-signed/GalleryBridge-v0.33.0-signed.apk"): String =
        JSONObject().put("versionCode", code).put("versionName", version)
            .put("packageName", pkg).put("certificateSha256", cert)
            .put("sha256", hash).put("size", size).put("apkUrl", url)
            .put("changes", "Новый менеджер обновлений").toString()

    @Test fun versionComparison() {
        assertTrue(UpdatePolicy.isNewer(33, 32))
        assertFalse(UpdatePolicy.isNewer(32, 32))
        assertFalse(UpdatePolicy.isNewer(31, 32))
    }
    @Test fun validManifest() { assertEquals(33L, UpdatePolicy.parse(payload()).code) }
    @Test fun manifestRejectsCoercedNumericValues() {
        for (key in listOf("versionCode", "size")) {
            for (value in listOf<Any>("33", 33.5, true, JSONObject.NULL, JSONObject())) {
                rejects(JSONObject(payload()).put(key, value).toString())
            }
        }
        rejects(payload().replace("\"versionCode\":33", "\"versionCode\":33.0"))
        rejects(payload().replace("\"versionCode\":33", "\"versionCode\":3.3e1"))
    }

    @Test fun manifestRejectsNonStringSecurityFields() {
        for (key in listOf("versionName", "packageName", "certificateSha256", "sha256", "apkUrl")) {
            for (value in listOf<Any>(33, true, JSONObject.NULL, JSONObject())) {
                rejects(JSONObject(payload()).put(key, value).toString())
            }
        }
    }

    @Test fun integerVersionCodeIsNotTruncatedToInt() {
        val json = JSONObject(payload()).put("versionCode", 2147483648L).toString()
        assertEquals(2147483648L, UpdatePolicy.parse(json).code)
    }

    @Test fun wrongPackage() = rejects(payload(pkg = "com.invalid"))
    @Test fun wrongCertificate() = rejects(payload(cert = "b".repeat(64)))
    @Test fun invalidSha() = rejects(payload(hash = "corrupted"))
    @Test fun invalidSize() = rejects(payload(size = 0))
    @Test fun maliciousUrl() = rejects(payload(url = "https://malicious.example/update.apk"))
    @Test fun versionUrlMismatch() = rejects(payload(version = "0.34.0"))
    @Test fun malformedJson() = rejects("{bad")
    @Test fun redirectPolicy() {
        assertTrue(UpdatePolicy.permittedRedirect("https://release-assets.githubusercontent.com/path"))
        assertFalse(UpdatePolicy.permittedRedirect("http://release-assets.githubusercontent.com/path"))
        assertFalse(UpdatePolicy.permittedRedirect("https://evil.example/file"))
    }
    private fun rejects(json: String) {
        try { UpdatePolicy.parse(json); fail("Expected rejection") }
        catch (_: IllegalArgumentException) { }
        catch (_: org.json.JSONException) { }
    }
}
