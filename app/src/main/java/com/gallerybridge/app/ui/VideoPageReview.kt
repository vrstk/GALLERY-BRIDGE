package com.gallerybridge.app.ui

import android.net.Uri
import android.webkit.WebSettings
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil3.compose.AsyncImage
import com.gallerybridge.app.core.Media
import com.gallerybridge.app.core.PrimaryVideoSources

/**
 * The JPG poster is displayed until the user presses Play.
 * A media variant comes from the exact public watch page; no inferred CDN URLs.
 */
@Composable
internal fun VideoPageReview(
    title: String,
    posterUrl: String,
    variants: List<Media>,
    selectedUrl: String?,
    onSelect: (String) -> Unit,
    onBack: () -> Unit
) {
    val media = variants.firstOrNull { it.url == selectedUrl } ?: variants.firstOrNull()
    var playing by remember(media?.url) { mutableStateOf(false) }
    Column(modifier = Modifier.widthIn(max = GalleryContentWidth).fillMaxSize()
        .padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title.ifBlank { "Видеопубликация" }, style = MaterialTheme.typography.titleLarge)
        if (media != null) {
            if (playing) {
                AndroidView(
                    factory = { context ->
                        VideoView(context).apply {
                            val headers = if (media.source == PrimaryVideoSources.SOURCE)
                                mapOf("Referer" to media.referrer,
                                    "User-Agent" to WebSettings.getDefaultUserAgent(context))
                            else emptyMap()
                            setVideoURI(Uri.parse(media.url), headers)
                            setMediaController(MediaController(context).also {
                                it.setAnchorView(this)
                            })
                            setOnPreparedListener { it.isLooping = false; start() }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                )
            } else {
                if (posterUrl.isNotBlank()) {
                    AsyncImage(model = posterUrl, contentDescription = "JPG-обложка плеера",
                        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                }
                Button(onClick = { playing = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Воспроизвести выбранный вариант")
                }
            }
        }
        Text("Видео загружается только после вашего выбора. " +
            "Каждый вариант MP4 и его параметры получены со страницы плеера.",
            style = MaterialTheme.typography.bodySmall)
        variants.forEach { candidate ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = candidate.url == media?.url,
                    onClick = { playing = false; onSelect(candidate.url) })
                Text(candidate.name, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Text("Для передачи одного ролика выберите вариант и нажмите «Загрузить» " +
            "внизу экрана. Проверка HTTP 206 не заменяет проверку полного MP4.",
            style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = onBack) { Text("Назад") }
    }
}
