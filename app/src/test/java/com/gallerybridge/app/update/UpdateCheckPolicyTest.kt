package com.gallerybridge.app.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckPolicyTest {
    private val day = UpdateCheckPolicy.SUCCESS_INTERVAL_MS
    private val retry = UpdateCheckPolicy.RETRY_INTERVAL_MS
    private val now = 1_800_000_000_000L

    @Test fun firstLaunchChecksUnlessDisabled() {
        assertTrue(UpdateCheckPolicy.shouldAutoCheck(true, now, 0, 0))
        assertFalse(UpdateCheckPolicy.shouldAutoCheck(false, now, 0, 0))
    }

    @Test fun successfulCheckIsFreshForADay() {
        assertFalse(UpdateCheckPolicy.shouldAutoCheck(true, now, now - day + 1, now - day))
        assertTrue(UpdateCheckPolicy.shouldAutoCheck(true, now, now - day, now - day))
    }

    @Test fun failureIsRetriedAfterFifteenMinutesNotOneDay() {
        assertFalse(UpdateCheckPolicy.shouldAutoCheck(true, now, 0, now - retry + 1))
        assertTrue(UpdateCheckPolicy.shouldAutoCheck(true, now, 0, now - retry))
        assertTrue(UpdateCheckPolicy.shouldAutoCheck(true, now, now - day - 1, now - retry))
    }

    @Test fun clockRollbackDoesNotBlockFutureAttempts() {
        assertTrue(UpdateCheckPolicy.shouldAutoCheck(true, now, now + day, now + retry))
    }
}
