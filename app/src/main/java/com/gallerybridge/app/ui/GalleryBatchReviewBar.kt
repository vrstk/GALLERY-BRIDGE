package com.gallerybridge.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Manual navigation only. This component does not scan or transfer anything. */
@Composable
internal fun GalleryBatchReviewBar(
    position: Int,
    total: Int,
    canAdvance: Boolean,
    onNext: () -> Unit,
    onClose: () -> Unit
) {
    if (total == 0) return
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Пакетный просмотр: ${position + 1} из $total",
                style = MaterialTheme.typography.titleSmall)
            Text("Каждую публикацию проверяйте отдельно. Загрузка файлов не запускается автоматически.",
                style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onClose) { Text("Закончить") }
                Button(onClick = onNext, enabled = canAdvance) {
                    Text(if (position + 1 >= total) "Вернуться в каталог"
                        else "Следующая публикация")
                }
            }
        }
    }
}
