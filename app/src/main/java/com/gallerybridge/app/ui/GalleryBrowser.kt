package com.gallerybridge.app.ui

import android.annotation.SuppressLint
import android.webkit.DownloadListener
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.json.JSONArray
import org.json.JSONTokener

/**
 * Browse in the application's own WebView session.
 * Only explicit HTML media links and DownloadListener callbacks are collected.
 * No JavaScript bridge, request interception, hidden crawler or DRM bypass.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun GalleryBrowser(
    initialUrl: String,
    onMediaFound: (List<String>, String, String) -> Unit,
    onDownloadLink: (String, String, String) -> Unit
) {
    val context = LocalContext.current
    var address by remember(initialUrl) {
        mutableStateOf(initialUrl.takeIf { it.startsWith("https://") } ?: "https://")
    }
    var currentPage by remember { mutableStateOf("") }
    var pageTitle by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("Откройте страницу с видео или фотогалереей.") }
    var browser by remember { mutableStateOf<WebView?>(null) }

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = address,
                onValueChange = { address = it },
                singleLine = true,
                label = { Text("Адрес сайта") },
                modifier = Modifier.weight(1f)
            )
            Button(onClick = {
                val url = address.trim()
                if (url.startsWith("https://") && android.net.Uri.parse(url).host != null) {
                    browser?.loadUrl(url)
                } else message = "Укажите полный адрес HTTPS."
            }) { Text("Открыть") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                browser?.let { if (it.canGoBack()) it.goBack() }
            }) { Text("Назад") }
            OutlinedButton(onClick = { browser?.reload() }) { Text("Обновить") }
            Button(
                enabled = currentPage.startsWith("https://"),
                onClick = {
                    val web = browser ?: return@Button
                    // Only explicit media attributes from the current document.
                    // Cross-origin frames are intentionally not inspected.
                    val js = """(function() {
                        const values = [];
                        document.querySelectorAll('video,source,a[href],img[src]').forEach(el => {
                            const u = el.currentSrc || el.src || el.href || '';
                            if (/^https:\\/\\//i.test(u) && /\\.(mp4|m4v|mov|webm|jpe?g|png|webp|avif|gif)(?:[?#]|$)/i.test(u)) {
                                values.push(u);
                            }
                        });
                        return JSON.stringify([...new Set(values)].slice(0, 120));
                    })()"""
                    web.evaluateJavascript(js) { returned ->
                        try {
                            val value = JSONTokener(returned).nextValue() as? String ?: "[]"
                            val arr = JSONArray(value)
                            val list = (0 until arr.length()).mapNotNull {
                                arr.optString(it).takeIf { u -> u.startsWith("https://") }
                            }
                            if (list.isNotEmpty()) {
                                onMediaFound(list, currentPage, pageTitle)
                            } else {
                                message = "В HTML этой страницы прямые медиа не обнаружены. Попробуйте открыть видео или воспользоваться ссылкой загрузки."
                            }
                        } catch (_: Exception) {
                            message = "Не удалось разобрать ссылки на медиа."
                        }
                    }
                }
            ) { Text("Найти медиа") }
        }
        if (message.isNotBlank()) {
            Text(message, style = MaterialTheme.typography.bodySmall)
        }
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = {
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = true
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    webChromeClient = WebChromeClient()
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            return request.url.scheme !in listOf("https", "about")
                        }
                        override fun onPageFinished(view: WebView, url: String) {
                            currentPage = url
                            pageTitle = view.title.orEmpty()
                            address = url
                            message = "Откройте видео на странице и нажмите «Найти медиа»."
                        }
                    }
                    setDownloadListener(DownloadListener { downloadUrl, userAgent, disposition, mime, _ ->
                        if (downloadUrl.startsWith("https://")) {
                            val fileName = android.webkit.URLUtil.guessFileName(downloadUrl, disposition, mime)
                            onDownloadLink(downloadUrl, fileName, url.ifBlank { currentPage })
                        } else {
                            message = "Браузер получил ссылку, которую нельзя безопасно передать."
                        }
                    })
                    browser = this
                    if (address.startsWith("https://") && android.net.Uri.parse(address).host != null) {
                        loadUrl(address)
                    }
                }
            },
            onRelease = { view ->
                if (browser === view) browser = null
                view.stopLoading()
                view.destroy()
            }
        )
    }
}
