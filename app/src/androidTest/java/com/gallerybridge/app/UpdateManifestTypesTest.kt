package com.gallerybridge.app

import com.gallerybridge.app.update.UpdatePolicy
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class UpdateManifestTypesTest {
    private fun manifest() = JSONObject()
        .put("versionCode", 103)
        .put("versionName", "0.99.4")
        .put("packageName", UpdatePolicy.PACKAGE)
        .put("certificateSha256", UpdatePolicy.CERTIFICATE)
        .put("sha256", "a".repeat(64))
        .put("size", 9047292)
        .put("apkUrl", "https://github.com/vrstk/GALLERY-BRIDGE/releases/download/v0.99.4-signed/GalleryBridge-v0.99.4-signed.apk")

    @Test fun androidJsonDoesNotCoerceManifestFields() {
        assertEquals(103L, UpdatePolicy.parse(manifest().toString()).code)
        for (key in listOf("versionCode", "size")) {
            for (value in listOf<Any>("9047292", 9047292.5, true, JSONObject.NULL)) {
                reject(manifest().put(key, value).toString())
            }
        }
        for (key in listOf("versionName", "packageName", "certificateSha256", "sha256", "apkUrl")) {
            reject(manifest().put(key, 123).toString())
        }
    }

    private fun reject(raw: String) {
        try {
            UpdatePolicy.parse(raw)
            fail("Invalid manifest was accepted")
        } catch (_: IllegalArgumentException) {
        } catch (_: org.json.JSONException) {
        }
    }
}
