package com.gallerybridge.app.update

import org.junit.Assert.*
import org.junit.Test

class UpdateRangePolicyTest {
    private val bytes = 15_000_000L

    @Test fun fullResponseAlwaysRestartsInsteadOfAppending() {
        assertEquals(UpdateRangePolicy.Action.REPLACE,
            UpdateRangePolicy.decide(4_096, 200, null, bytes))
        assertEquals(UpdateRangePolicy.Action.REPLACE,
            UpdateRangePolicy.decide(0, 200, null, bytes))
    }

    @Test fun matchingPartialResponseMayAppend() {
        assertEquals(UpdateRangePolicy.Action.APPEND,
            UpdateRangePolicy.decide(4096, 206,
                "bytes 4096-14999999/15000000", bytes))
    }

    @Test fun rejectsWrongOffsetAndServerTotal() {
        rejects { UpdateRangePolicy.decide(4096, 206,
            "bytes 0-14999999/15000000", bytes) }
        rejects { UpdateRangePolicy.decide(4096, 206,
            "bytes 4096-14999999/15000001", bytes) }
        rejects { UpdateRangePolicy.decide(4096, 206,
            "bytes 4096-15000000/15000000", bytes) }
        rejects { UpdateRangePolicy.decide(4096, 206, null, bytes) }
    }

    @Test fun rejectsUnexpectedHttpAndImpossibleOffsets() {
        rejects { UpdateRangePolicy.decide(4096, 416, null, bytes) }
        rejects { UpdateRangePolicy.decide(0, 206,
            "bytes 0-14999999/15000000", bytes) }
        rejects { UpdateRangePolicy.decide(bytes, 200, null, bytes) }
        rejects { UpdateRangePolicy.decide(-1, 200, null, bytes) }
    }

    private fun rejects(block: () -> Unit) {
        try { block(); fail("Expected rejected response") }
        catch (_: IllegalArgumentException) { }
        catch (_: IllegalStateException) { }
    }
}
