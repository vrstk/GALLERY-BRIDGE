package com.gallerybridge.app

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import com.gallerybridge.app.transfer.QueueStore
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueStateRecoveryTest {
    @Test fun missingAndUnknownPersistedStatesNeverEnterAutomaticRetry() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("gallery_bridge_queue", Context.MODE_PRIVATE)
        val previous = prefs.getString("snapshot", null)
        val digest = "a".repeat(64)
        val entries = JSONArray()
        listOf(null, "UNRECOGNIZED", "DONE", "PENDING").forEachIndexed { index, state ->
            val entry = JSONObject()
                .put("url", "https://example.com/queue-recovery/$index.jpg")
                .put("name", "$index.jpg")
                .put("kind", "PHOTO")
                .put("contentSha256", digest)
            if (state != null) entry.put("state", state)
            entries.put(entry)
        }
        val raw = JSONObject()
            .put("id", 993L)
            .put("model", "Тест")
            .put("title", "Восстановление очереди")
            .put("target", "Mail WebDAV")
            .put("root", "Models")
            .put("entries", entries)
            .toString()
        try {
            assertTrue(prefs.edit().putString("snapshot", raw).commit())
            val restored = requireNotNull(QueueStore.load(context))
            assertEquals(listOf("BLOCKED", "BLOCKED", "DONE", "PENDING"),
                restored.entries.map { it.state })
            assertEquals(listOf(3), QueueStore.retryIndices(restored))
            assertEquals(2, restored.blocked)
            assertTrue(restored.entries.take(2).all {
                it.failureReason.isNotBlank() && it.contentSha256 == digest
            })
            assertEquals(raw, prefs.getString("snapshot", null))
        } finally {
            assertTrue(prefs.edit().putString("snapshot", previous).commit())
        }
    }
}
