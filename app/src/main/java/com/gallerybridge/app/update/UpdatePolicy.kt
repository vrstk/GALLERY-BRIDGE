package com.gallerybridge.app.update

import org.json.JSONObject
import java.net.URI

internal object UpdatePolicy {
    const val PACKAGE = "com.gallerybridge.app.preview.signed"
    const val CERTIFICATE = "06f59535fa8cb13507a2d77305daed1b81016459bc5884b5d837c3823f9664a1"
    const val API = "https://api.github.com/repos/vrstk/GALLERY-BRIDGE/releases/tags/preview-signed"
    const val MANIFEST = "https://github.com/vrstk/GALLERY-BRIDGE/releases/download/preview-signed/update.json"

    data class Release(val code: Long, val version: String, val apkUrl: String,
                       val bytes: Long, val hash: String, val notes: String)

    fun parse(json: String): Release {
        val o = JSONObject(json)
        val code = o.getLong("versionCode")
        val ver = o.getString("versionName")
        val pkg = o.getString("packageName")
        val size = o.getLong("size")
        val hash = o.getString("sha256").lowercase()
        val cert = o.getString("certificateSha256").lowercase()
        val url = o.getString("apkUrl")
        require(code > 0 && ver.matches(Regex("""\d+\.\d+\.\d+"""))) { "Неверная версия" }
        require(pkg == PACKAGE) { "Неверный package ID" }
        require(cert == CERTIFICATE) { "Не совпадает сертификат" }
        require(hash.matches(Regex("[0-9a-f]{64}"))) { "Неверный SHA-256" }
        require(size in 1024L..200_000_000L) { "Неверный размер" }
        val expected = "https://github.com/vrstk/GALLERY-BRIDGE/releases/download/v${ver}-signed/GalleryBridge-v${ver}-signed.apk"
        require(url == expected && permittedUrl(url)) { "Запрещённый источник APK" }
        return Release(code, ver, url, size, hash, o.optString("changes").take(2000))
    }

    fun isNewer(remote: Long, installed: Long) = remote > installed

    fun permittedUrl(value: String): Boolean = try {
        val u = URI(value)
        u.scheme == "https" && u.host == "github.com" && u.port == -1 &&
            u.rawUserInfo == null && u.rawQuery == null && u.rawFragment == null &&
            u.path.startsWith("/vrstk/GALLERY-BRIDGE/releases/download/")
    } catch (_: Exception) { false }

    fun permittedRedirect(value: String): Boolean = try {
        val u = URI(value)
        u.scheme == "https" && u.port == -1 && u.rawUserInfo == null &&
            (u.host == "release-assets.githubusercontent.com" ||
                u.host == "objects.githubusercontent.com" ||
                permittedUrl(value))
    } catch (_: Exception) { false }
}
