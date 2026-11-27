package com.gallerybridge.app.core

/** Only these destinations can create or resume transfers in the current version. */
object TransferTargets {
    const val FOLDER = "Папка Android"
    const val MAIL = "Mail WebDAV"

    fun isSupported(target: String): Boolean = target == FOLDER || target == MAIL

    /** A saved WebDAV queue must never be redirected to the current settings account.
     * Old queues with no recorded owner need manual review, not an implicit binding.
     * The password is not persisted or included in errors.
     */
    fun requireQueueCredentials(target: String, savedAccount: String, email: String, password: String) {
        if (target != MAIL) return
        require(email.isNotBlank() && password.isNotBlank()) {
            "Укажите почту и пароль приложения Mail WebDAV."
        }
        require(savedAccount.isNotBlank()) {
            "В старой очереди не указан аккаунт Mail. Проверьте файлы в облаке вручную; автоматическое продолжение заблокировано."
        }
        require(savedAccount.trim() == email.trim()) {
            "Очередь принадлежит другому аккаунту Mail. Выберите исходный аккаунт в настройках."
        }
    }

    /** Older installed versions stored the removed Google Drive API target. */
    fun restore(value: String?): String = if (value == MAIL) MAIL else FOLDER
}
