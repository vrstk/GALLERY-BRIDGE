package com.gallerybridge.app.update

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.security.MessageDigest

/** Re-check the exact signed APK before publishing as ready or invoking Android installer. */
internal object UpdateArchiveVerifier {
    @Suppress("DEPRECATION")
    fun verify(context: Context, apk: File, release: UpdatePolicy.Release) {
        require(apk.isFile && apk.length() == release.bytes) { "Неверный размер APK" }
        val digest = MessageDigest.getInstance("SHA-256")
        apk.inputStream().use { input ->
            val buffer = ByteArray(65536)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        require(digest.digest().hex() == release.hash) { "SHA-256 APK не совпадает" }
        val pm = context.packageManager
        val info = if (Build.VERSION.SDK_INT >= 33) {
            pm.getPackageArchiveInfo(
                apk.absolutePath,
                PackageManager.PackageInfoFlags.of(
                    PackageManager.GET_SIGNING_CERTIFICATES.toLong()
                )
            )
        } else {
            pm.getPackageArchiveInfo(
                apk.absolutePath,
                if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES
                else PackageManager.GET_SIGNATURES
            )
        }
        require(info != null && info.packageName == UpdatePolicy.PACKAGE) {
            "Неверный package ID APK"
        }
        val code = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode
            else info.versionCode.toLong()
        val installed = pm.getPackageInfo(context.packageName, 0)
        val installedCode = if (Build.VERSION.SDK_INT >= 28) installed.longVersionCode
            else installed.versionCode.toLong()
        require(code == release.code && UpdatePolicy.isNewer(code, installedCode)) {
            "Версия APK не подходит"
        }
        require(info.versionName == release.version) {
            "Версия APK отличается от манифеста"
        }
        val signatures = if (Build.VERSION.SDK_INT >= 28) {
            info.signingInfo?.apkContentsSigners
        } else info.signatures
        require(signatures?.size == 1) { "Подпись APK не найдена" }
        val cert = MessageDigest.getInstance("SHA-256")
            .digest(signatures[0].toByteArray()).hex()
        require(cert == UpdatePolicy.CERTIFICATE) { "Неверная подпись APK" }
    }

    private fun ByteArray.hex() = joinToString("") { "%02x".format(it) }
}
