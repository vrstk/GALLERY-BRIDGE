package com.gallerybridge.app.network

import org.junit.Assert.*
import org.junit.Test

class WebDavResourcePolicyTest {
    private val url = "https://webdav.cloud.mail.ru/Models/picture.jpg"
    private fun response(href: String, props: String, status: String = "HTTP/1.1 200 OK"): String =
        """<?xml version="1.0" encoding="utf-8"?>
        <d:multistatus xmlns:d="DAV:">
          <d:response><d:href>$href</d:href>
            <d:propstat><d:prop>$props</d:prop><d:status>$status</d:status></d:propstat>
          </d:response>
        </d:multistatus>"""

    @Test fun missingFileCanBeCreated() {
        assertEquals(WebDavResourcePolicy.Lookup.Missing,
            WebDavResourcePolicy.inspect(404, null, url, false))
    }

    @Test fun successfulNonEmptyPhotoHasConfirmedSize() {
        val xml = response("/Models/picture.jpg",
            "<d:getcontentlength>2048</d:getcontentlength><d:resourcetype/>")
        assertEquals(WebDavResourcePolicy.Lookup.Existing(2048, false),
            WebDavResourcePolicy.inspect(207, xml, url, false))
    }

    @Test fun emptyFileIsUnsafeNotACompletedTransfer() {
        val xml = response("/Models/picture.jpg",
            "<d:getcontentlength>0</d:getcontentlength><d:resourcetype/>")
        assertThrows(WebDavResourcePolicy.UnsafeResource::class.java) {
            WebDavResourcePolicy.inspect(207, xml, url, false)
        }
    }

    @Test fun multistatusWithoutMatchingHrefIsNeverSuccessful() {
        val xml = response("/Models/unrelated.jpg",
            "<d:getcontentlength>2048</d:getcontentlength><d:resourcetype/>")
        assertThrows(WebDavResourcePolicy.UnsafeResource::class.java) {
            WebDavResourcePolicy.inspect(207, xml, url, false)
        }
    }

    @Test fun nestedFailureStatusDoesNotClaimFileExists() {
        val xml = response("/Models/picture.jpg", "<d:resourcetype/>", "HTTP/1.1 404 Not Found")
        assertThrows(WebDavResourcePolicy.UnsafeResource::class.java) {
            WebDavResourcePolicy.inspect(207, xml, url, false)
        }
    }

    @Test fun ambiguousSizeOrDirectoryInsteadOfPhotoIsUnsafe() {
        val xml = response("/Models/picture.jpg", "<d:resourcetype/>")
        assertThrows(WebDavResourcePolicy.UnsafeResource::class.java) {
            WebDavResourcePolicy.inspect(207, xml, url, false)
        }
        val folder = response("/Models/picture.jpg",
            "<d:resourcetype><d:collection/></d:resourcetype>")
        assertThrows(WebDavResourcePolicy.UnsafeResource::class.java) {
            WebDavResourcePolicy.inspect(207, folder, url, false)
        }
    }

    @Test fun folderMustBeACollection() {
        val folderUrl = "https://webdav.cloud.mail.ru/Models/"
        val folder = response("/Models/",
            "<d:resourcetype><d:collection/></d:resourcetype>")
        assertEquals(WebDavResourcePolicy.Lookup.Existing(null, true),
            WebDavResourcePolicy.inspect(207, folder, folderUrl, true))
        assertThrows(WebDavResourcePolicy.UnsafeResource::class.java) {
            WebDavResourcePolicy.inspect(207,
                response("/Models/", "<d:resourcetype/>"), folderUrl, true)
        }
    }

    @Test fun multipleMatchingEntriesDoNotSilentlyPickFirst() {
        val first = response("/Models/picture.jpg",
            "<d:getcontentlength>1</d:getcontentlength>").substringAfter("<d:multistatus xmlns:d=\"DAV:\">").substringBefore("</d:multistatus>")
        val xml = "<d:multistatus xmlns:d=\"DAV:\">$first$first</d:multistatus>"
        assertThrows(WebDavResourcePolicy.UnsafeResource::class.java) {
            WebDavResourcePolicy.inspect(207, xml, url, false)
        }
    }
}
