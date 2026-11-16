package com.gallerybridge.app.update

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date

@Composable
internal fun UpdateSettings(manager: UpdateManager) {
    val s = manager.state
    HorizontalDivider()
    Text("Обновления приложения", style = MaterialTheme.typography.titleMedium)
    Text("Установлена: ${manager.installedVersion()}")
    Row {
        Checkbox(checked = manager.automatic, onCheckedChange = manager::setAutoCheck)
        Text("Автоматически проверять не чаще раза в сутки", Modifier.padding(top = 12.dp))
    }
    if (s.lastCheck > 0) Text("Последняя проверка: ${DateFormat.getDateTimeInstance().format(Date(s.lastCheck))}")
    OutlinedButton(onClick = { manager.check() }, enabled = !s.checking && !s.downloading) {
        Text(if (s.checking) "Проверяем…" else "Проверить обновления")
    }
    val release = s.release
    if (release != null) {
        Text("Доступна: ${release.version} (код ${release.code})")
        if (release.notes.isNotBlank()) Text("Что нового: ${release.notes}")
        if (s.downloading) {
            LinearProgressIndicator(progress = { s.progress / 100f }, modifier = Modifier.fillMaxWidth())
            Text("Загружено: ${s.progress}%")
            TextButton(onClick = manager::cancel) { Text("Отмена") }
        } else if (s.ready) {
            Button(onClick = manager::install) { Text("Установить") }
        } else {
            Button(onClick = manager::download, enabled = !s.checking) { Text("Скачать APK") }
        }
    }
    if (s.message.isNotBlank()) Text(s.message, style = MaterialTheme.typography.bodySmall)
    Text("Установщик Android потребует подтверждение. При передаче фотографий установку нужно отложить.",
        style = MaterialTheme.typography.bodySmall)
}
