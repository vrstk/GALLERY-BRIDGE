package com.gallerybridge.app.ui

import android.annotation.SuppressLint
import android.webkit.DownloadListener
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
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
                    // Only the visible, primary HTML5 player. Never collect all
                    // <a> tags, suggested clips, advertisements or page thumbnails.
                    val js = """(function() {
                        const players = Array.from(document.querySelectorAll('video'))
                            .map(v => ({video: v, box: v.getBoundingClientRect()}))
                            .filter(x => x.box.width >= 140 && x.box.height >= 90 &&
                                x.box.bottom > 0 && x.box.top < window.innerHeight);
                        players.sort((a, b) =>
                            (b.box.width * b.box.height) - (a.box.width * a.box.height));
                        if (players.length === 0) return JSON.stringify([]);
                        const player = players[0].video;
                        const sources = [player.currentSrc, player.src,
                            ...Array.from(player.querySelectorAll('source[src]'))
                                .map(el => el.src)];
                        const result = [];
                        for (const value of sources) {
                            if (!value) continue;
                            try {
                                const url = new URL(value, document.baseURI);
                                if (url.protocol !== 'https:') continue;
                                const path = url.pathname.toLowerCase();
                                if (['.mp4','.m4v','.mov','.webm'].some(ext => path.endsWith(ext))) {
                                    if (!result.includes(url.href)) result.push(url.href);
                                }
                            } catch (e) {}
                        }
                        return JSON.stringify(result.slice(0, 8));
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
                                message = "В видимом видеоплеере прямой MP4 не найден. " +
                                    "Прокрутите к основному видео и запустите его. " +
                                    "Рекламные и рекомендованные ролики не добавляются."
                            }
                        } catch (_: Exception) {
                            message = "Не удалось прочитать ссылку основного плеера."
                        }
                    }
                }
            ) { Text("Видео основного плеера") }
        }
        var directVideo by remember { mutableStateOf("") }
        OutlinedTextField(
            value = directVideo,
            onValueChange = { directVideo = it },
            label = { Text("Точная HTTPS-ссылка MP4 (необязательно)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedButton(
            onClick = {
                val link = directVideo.trim()
                val uri = android.net.Uri.parse(link)
                val extension = uri.lastPathSegment.orEmpty().substringAfterLast('.', "").lowercase()
                if (uri.scheme == "https" && !uri.host.isNullOrBlank() &&
                    extension in setOf("mp4", "m4v", "mov", "webm") &&
                    currentPage.startsWith("https://")
                ) {
                    onMediaFound(listOf(link), currentPage, pageTitle)
                } else {
                    message = "Откройте страницу публикации и укажите точную HTTPS-ссылку на видео."
                }
            },
            enabled = currentPage.startsWith("https://") && directVideo.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) { Text("Добавить только эту ссылку") }
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
                            onDownloadLink(downloadUrl, fileName, currentPage)
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
