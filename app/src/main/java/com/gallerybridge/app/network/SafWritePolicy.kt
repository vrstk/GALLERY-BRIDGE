package com.gallerybridge.app.network

/**
 * Verifies the Android provider's receipt without claiming remote cloud sync.
 * Many SAF cloud providers cannot report a file size; null is unknown, not zero.
 */
object SafWritePolicy {
    class UncertainWrite(message: String, cause: Throwable? = null) :
        IllegalStateException(message, cause)

    fun validate(
        expectedName: String,
        expectedBytes: Long,
        actualName: String?,
        reportedBytes: Long?
    ) {
        if (expectedBytes <= 0L) {
            throw UncertainWrite("Исходный файл пустой; загрузка не подтверждена.")
        }
        if (actualName != expectedName) {
            throw UncertainWrite(
                "Провайдер изменил имя файла: ожидалось $expectedName, получено ${actualName ?: "нет данных"}."
            )
        }
        if (reportedBytes != null && reportedBytes != expectedBytes) {
            throw UncertainWrite(
                "Провайдер сообщил размер $reportedBytes байт, но отправлено $expectedBytes. " +
                    "Файл оставлен для проверки; автоматическая перезапись заблокирована."
            )
        }
    }
}
