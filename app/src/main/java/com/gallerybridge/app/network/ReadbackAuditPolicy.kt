package com.gallerybridge.app.network

import com.gallerybridge.app.core.Kind
import com.gallerybridge.app.core.Paths
import com.gallerybridge.app.transfer.QueueStore

/**
 * Pure rules for an optional, read-only destination audit.
 * A "DONE" checkpoint alone is not evidence that cloud synchronization finished.
 */
internal object ReadbackAuditPolicy {
    fun eligible(queue: QueueStore.Snapshot): Boolean =
        queue.remaining == 0 && queue.entries.isNotEmpty() &&
            queue.target in setOf("Папка Android", "Mail WebDAV")

    fun canVerifyBlocked(queue: QueueStore.Snapshot, entry: QueueStore.Entry): Boolean =
        entry.state == "BLOCKED" && entry.media.kind == Kind.PHOTO &&
            storedDigest(entry) != null && when (queue.target) {
                "Mail WebDAV" -> queue.account.isNotBlank()
                "Папка Android" -> queue.root.isNotBlank()
                else -> false
            }

    fun authorizedAccount(queue: QueueStore.Snapshot, email: String, password: String): Boolean =
        queue.target == "Mail WebDAV" &&
            email.isNotBlank() && password.isNotBlank() &&
            (queue.account.isBlank() || queue.account == email.trim())

    /** Match the exact per-file routing used by Services.transfer().
     * For batch queues, QueueStore.foldersFor reconstructs the publication-specific
     * folder including its stable identity, not the queue's generic title.
     */
    fun folders(queue: QueueStore.Snapshot, entry: QueueStore.Entry): List<String> =
        QueueStore.foldersFor(entry, queue)

    /** Used for remote WebDAV reads; the SAF provider uses folders() only. */
    fun remoteSegments(queue: QueueStore.Snapshot, entry: QueueStore.Entry): List<String> =
        Paths.splitRoot(queue.root) + folders(queue, entry) + Paths.clean(entry.media.name)

    /** A later read of the source cannot replace missing transfer-time evidence.
     * Legacy DONE/SKIPPED entries without SHA-256 must remain unverified.
     */
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
