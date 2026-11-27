package com.gallerybridge.app.update

/**
 * Successful checks stay fresh for a day; errors only throttle retries.
 * A wall-clock rollback must not indefinitely suppress update checks.
 */
internal object UpdateCheckPolicy {
    const val SUCCESS_INTERVAL_MS = 24L * 60 * 60 * 1000
    const val RETRY_INTERVAL_MS = 15L * 60 * 1000

    fun shouldAutoCheck(enabled: Boolean, now: Long, lastSuccess: Long, lastAttempt: Long): Boolean {
        if (!enabled) return false
        return elapsed(now, lastSuccess) >= SUCCESS_INTERVAL_MS &&
            elapsed(now, lastAttempt) >= RETRY_INTERVAL_MS
    }

    private fun elapsed(now: Long, then: Long): Long =
        if (then <= 0 || now < then) Long.MAX_VALUE else now - then
}
