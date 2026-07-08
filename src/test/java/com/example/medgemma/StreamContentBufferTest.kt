package com.example.medgemma

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Drives the shipped [StreamContentBuffer] / [StreamBatchPolicy].
 * N streaming tokens must produce fewer than N UI flushes; final text equals full generation.
 */
class StreamContentBufferTest {

    @Test
    fun batchPolicy_doesNotFlushBeforeThreshold() {
        assertFalse(
            StreamBatchPolicy.shouldFlush(
                tokensSinceLastFlush = 1,
                elapsedMsSinceLastFlush = 0,
                maxTokens = 4,
                intervalMs = 50
            )
        )
        assertFalse(
            StreamBatchPolicy.shouldFlush(
                tokensSinceLastFlush = 3,
                elapsedMsSinceLastFlush = 10,
                maxTokens = 4,
                intervalMs = 50
            )
        )
    }

    @Test
    fun batchPolicy_flushesOnTokenCount() {
        assertTrue(
            StreamBatchPolicy.shouldFlush(
                tokensSinceLastFlush = 4,
                elapsedMsSinceLastFlush = 0,
                maxTokens = 4,
                intervalMs = 50
            )
        )
    }

    @Test
    fun batchPolicy_flushesOnIntervalWhenTokensPending() {
        assertTrue(
            StreamBatchPolicy.shouldFlush(
                tokensSinceLastFlush = 1,
                elapsedMsSinceLastFlush = 50,
                maxTokens = 4,
                intervalMs = 50
            )
        )
    }

    @Test
    fun batchPolicy_zeroTokensNeverFlushesUnlessForced() {
        assertFalse(
            StreamBatchPolicy.shouldFlush(
                tokensSinceLastFlush = 0,
                elapsedMsSinceLastFlush = 1000,
                maxTokens = 4,
                intervalMs = 50
            )
        )
        assertTrue(
            StreamBatchPolicy.shouldFlush(
                tokensSinceLastFlush = 0,
                elapsedMsSinceLastFlush = 0,
                force = true
            )
        )
    }

    @Test
    fun nTokens_produceFewerThanNFlushes_andFinalEqualsFullText() {
        // Frozen clock so only token-count batching fires.
        var clock = 0L
        val buffer = StreamContentBuffer(
            maxTokensPerFlush = 4,
            intervalMs = 50,
            nowMs = { clock }
        )

        val tokens = listOf("Hel", "lo", " ", "world", "!", " How", " are", " you", "?")
        val n = tokens.size
        assertTrue("test needs N>1", n > 1)

        var lastUiContent: String? = null
        var uiFlushEvents = 0
        for (t in tokens) {
            when (val action = buffer.accept(t)) {
                is StreamContentBuffer.Action.UpdateUi -> {
                    uiFlushEvents++
                    lastUiContent = action.content
                }
                else -> Unit
            }
        }
        when (val final = buffer.finish()) {
            is StreamContentBuffer.Action.UpdateUi -> {
                uiFlushEvents++
                lastUiContent = final.content
            }
            else -> Unit
        }

        val expected = tokens.joinToString("")
        assertEquals(expected, buffer.content)
        assertEquals(expected, lastUiContent)
        assertTrue(
            "expected fewer UI flushes than tokens: flushes=$uiFlushEvents tokens=$n",
            uiFlushEvents < n
        )
        // 9 tokens / 4 per flush → 2 mid-stream + maybe finish for remainder
        assertEquals(buffer.flushCount, uiFlushEvents)
        assertTrue(buffer.flushCount >= 2)
        assertTrue(buffer.flushCount <= 3)
    }

    @Test
    fun statsToken_forceFlushesWithCompleteContent() {
        var clock = 0L
        val buffer = StreamContentBuffer(maxTokensPerFlush = 4, intervalMs = 50, nowMs = { clock })
        buffer.accept("A")
        buffer.accept("B")
        val statsAction = buffer.accept("[STATS] 2 tokens • 0.10s • 20.00 t/s (native 22.00 t/s)")
        assertTrue(statsAction is StreamContentBuffer.Action.UpdateUi)
        val ui = statsAction as StreamContentBuffer.Action.UpdateUi
        assertEquals("AB", ui.content)
        assertEquals("2 tokens • 0.10s • 20.00 t/s (native 22.00 t/s)", ui.stats)
        assertEquals(1, buffer.flushCount)
    }

    @Test
    fun thoughtTokens_doNotCorruptContent() {
        var clock = 0L
        val buffer = StreamContentBuffer(maxTokensPerFlush = 2, intervalMs = 50, nowMs = { clock })
        buffer.accept("[THOUGHT_START]")
        buffer.accept("reason")
        buffer.accept("[THOUGHT_END]")
        buffer.accept("Answer")
        buffer.accept("!")
        val final = buffer.finish()
        assertEquals("Answer!", buffer.content)
        assertEquals("reason", buffer.thought)
        assertTrue(final is StreamContentBuffer.Action.UpdateUi || buffer.flushCount >= 1)
        when (final) {
            is StreamContentBuffer.Action.UpdateUi -> assertEquals("Answer!", final.content)
            else -> Unit
        }
    }

    @Test
    fun errorToken_surfacesErrorAction() {
        val buffer = StreamContentBuffer()
        val action = buffer.accept("Error: eval failed")
        assertTrue(action is StreamContentBuffer.Action.Error)
        assertEquals("eval failed", (action as StreamContentBuffer.Action.Error).message)
    }
}
