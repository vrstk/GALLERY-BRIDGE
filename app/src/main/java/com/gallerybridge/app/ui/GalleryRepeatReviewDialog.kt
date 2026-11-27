package com.gallerybridge.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * A possible repeated filename is a prompt for review, not verified file identity.
 * No transfer begins until an explicit action from the user.
 */
@Composable
internal fun GalleryRepeatReviewDialog(
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    matchedCount: Int = 0,
    totalCount: Int = 0,
    onOnlyUnmarked: (() -> Unit)? = null
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Проверка повторной передачи") },
        text = {
            Column {
                Text(
                    "Эта публикация уже встречалась в истории передач или сохранённой очереди. " +
                        "Совпадения по имени файла не доказывают совпадение байтов или синхронизацию облака."
                )
                if (matchedCount > 0) {
                    Spacer(Modifier.height(8.dp))
                    Text("С отметкой о предыдущей обработке: $matchedCount из $totalCount выбранных фотографий.")
                    Text("Отметки действуют только для той же публикации и назначения.")
                } else {
                    Spacer(Modifier.height(8.dp))
                    Text("Подробные результаты предыдущих фотографий могут быть недоступны.")
                }
                if (onOnlyUnmarked != null && matchedCount > 0 && matchedCount < totalCount) {
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onOnlyUnmarked) {
                        Text("Передать только без отметок (${totalCount - matchedCount})")
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onConfirm) { Text("Подтвердить повтор всех выбранных") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Вернуться") } }
    )
}
