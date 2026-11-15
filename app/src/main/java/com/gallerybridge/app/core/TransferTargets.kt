package com.gallerybridge.app.core

/** Only these destinations can create or resume transfers in the current version. */
object TransferTargets {
    const val FOLDER = "Папка Android"
    const val MAIL = "Mail WebDAV"

    fun isSupported(target: String): Boolean = target == FOLDER || target == MAIL

    /** Older installed versions stored the removed Google Drive API target. */
    fun restore(value: String?): String = if (value == MAIL) MAIL else FOLDER
}
