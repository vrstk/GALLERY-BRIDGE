package com.gallerybridge.app.network

import com.gallerybridge.app.transfer.QueueStore

/**
 * Pure rules for an optional, read-only destination audit.
 * A "DONE" checkpoint alone is not evidence that cloud synchronization finished.
 */
internal object ReadbackAuditPolicy {
    fun eligible(queue: QueueStore.Snapshot): Boolean =
        queue.remaining == 0 && queue.entries.isNotEmpty() &&
            queue.target in setOf("Папка Android", "Mail WebDAV")

    fun authorizedAccount(queue: QueueStore.Snapshot, email: String, password: String): Boolean =
        queue.target == "Mail WebDAV" &&
            email.isNotBlank() && password.isNotBlank() &&
            (queue.account.isBlank() || queue.account == email.trim())

    fun storedDigest(entry: QueueStore.Entry): String? =
        entry.contentSha256.takeIf(QueueStore::validContentHash)

    fun matches(expectedSha256: String, readbackSha256: String,
                readbackBytes: Long, declaredBytes: Long? = null): Boolean =
        QueueStore.validContentHash(expectedSha256) &&
            QueueStore.validContentHash(readbackSha256) &&
            expectedSha256 == readbackSha256 &&
            readbackBytes > 0L &&
            (declaredBytes == null || declaredBytes == readbackBytes)
}
