package com.example.medgemma

/**
 * Batches streaming model tokens before UI list mutations.
 *
 * Flushes when either [maxTokensPerFlush] content tokens arrive or
 * [intervalMs] elapses since the last flush (whichever first), and always
 * on stats / finish so the final assistant text matches the full generation.
 *
 * Uses [StringBuilder] so long answers do not pay O(n²) string copies.
 * Safe to call from a background collector; only flushes should hop to Main.
 */
class StreamContentBuffer(
    private val maxTokensPerFlush: Int = DEFAULT_MAX_TOKENS_PER_FLUSH,
    private val intervalMs: Long = DEFAULT_INTERVAL_MS,
    private val nowMs: () -> Long = System::currentTimeMillis
) {
    private val contentBuilder = StringBuilder()
    private val thoughtBuilder = StringBuilder()

    val content: String
        get() = contentBuilder.toString()
    val thought: String
        get() = thoughtBuilder.toString()
    var isThinking: Boolean = false
        private set
    /** Number of UI-facing flushes (content updates and final stats). */
    var flushCount: Int = 0
        private set
    /** Content tokens accepted since the last flush (excludes control tokens). */
    var tokensSinceFlush: Int = 0
        private set

    private var lastFlushMs: Long = nowMs()

    sealed class Action {
        data class UpdateUi(
            val content: String,
            val thought: String?,
            val stats: String? = null
        ) : Action()

        data class Error(val message: String) : Action()
        object None : Action()
    }

    fun accept(token: String): Action {
        when {
            token.startsWith("Error: ") -> {
                return Action.Error(token.removePrefix("Error: "))
            }
            token.startsWith("[STATS] ") -> {
                return forceFlush(stats = token.removePrefix("[STATS] "))
            }
            token == "[THOUGHT_START]" -> {
                isThinking = true
                return Action.None
            }
            token == "[THOUGHT_END]" -> {
                isThinking = false
                // Flush so thought UI can hide/show without waiting for content tokens.
                return if (tokensSinceFlush > 0) forceFlush() else Action.None
            }
            else -> {
                if (isThinking) {
                    thoughtBuilder.append(token)
                } else {
                    contentBuilder.append(token)
                }
                tokensSinceFlush++
                return maybeFlush()
            }
        }
    }

    /** Force a final UI update if anything is still buffered. */
    fun finish(): Action {
        return if (tokensSinceFlush > 0) forceFlush() else Action.None
    }

    private fun maybeFlush(): Action {
        val elapsed = nowMs() - lastFlushMs
        val should = StreamBatchPolicy.shouldFlush(
            tokensSinceLastFlush = tokensSinceFlush,
            elapsedMsSinceLastFlush = elapsed,
            maxTokens = maxTokensPerFlush,
            intervalMs = intervalMs,
            force = false
        )
        return if (should) forceFlush() else Action.None
    }

    private fun forceFlush(stats: String? = null): Action {
        flushCount++
        tokensSinceFlush = 0
        lastFlushMs = nowMs()
        return Action.UpdateUi(
            content = contentBuilder.toString(),
            thought = thoughtBuilder.toString().ifBlank { null },
            stats = stats
        )
    }

    companion object {
        // Slightly looser than 4/50ms: fewer markdown layouts while still feeling live.
        const val DEFAULT_MAX_TOKENS_PER_FLUSH = 8
        const val DEFAULT_INTERVAL_MS = 80L
    }
}

/** Pure flush predicate — unit-tested independently of buffer state. */
object StreamBatchPolicy {
    fun shouldFlush(
        tokensSinceLastFlush: Int,
        elapsedMsSinceLastFlush: Long,
        maxTokens: Int = StreamContentBuffer.DEFAULT_MAX_TOKENS_PER_FLUSH,
        intervalMs: Long = StreamContentBuffer.DEFAULT_INTERVAL_MS,
        force: Boolean = false
    ): Boolean {
        if (force) return true
        if (tokensSinceLastFlush <= 0) return false
        if (tokensSinceLastFlush >= maxTokens) return true
        if (elapsedMsSinceLastFlush >= intervalMs) return true
        return false
    }
}
