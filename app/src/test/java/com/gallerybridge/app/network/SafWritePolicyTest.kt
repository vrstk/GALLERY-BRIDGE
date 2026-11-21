package com.gallerybridge.app.network

import org.junit.Assert.assertThrows
import org.junit.Test

class SafWritePolicyTest {
    @Test fun matchingNameAndSizeAreAccepted() {
        SafWritePolicy.validate("photo.jpg", 32000L, "photo.jpg", 32000L)
    }

    @Test fun unknownProviderSizeIsNotAssumedToBeZero() {
        SafWritePolicy.validate("photo.jpg", 32000L, "photo.jpg", null)
    }

    @Test fun zeroOrTruncatedDestinationIsNotMarkedComplete() {
        assertThrows(SafWritePolicy.UncertainWrite::class.java) {
            SafWritePolicy.validate("photo.jpg", 32000L, "photo.jpg", 0L)
        }
        assertThrows(SafWritePolicy.UncertainWrite::class.java) {
            SafWritePolicy.validate("photo.jpg", 32000L, "photo.jpg", 12345L)
        }
    }

    @Test fun providerRenameAndMissingReceiptAreNotMarkedComplete() {
        assertThrows(SafWritePolicy.UncertainWrite::class.java) {
            SafWritePolicy.validate("photo.jpg", 32000L, "photo (1).jpg", 32000L)
        }
        assertThrows(SafWritePolicy.UncertainWrite::class.java) {
            SafWritePolicy.validate("photo.jpg", 32000L, null, null)
        }
    }

    @Test fun emptySourceCannotPassVerification() {
        assertThrows(SafWritePolicy.UncertainWrite::class.java) {
            SafWritePolicy.validate("photo.jpg", 0L, "photo.jpg", 0L)
        }
    }
}
