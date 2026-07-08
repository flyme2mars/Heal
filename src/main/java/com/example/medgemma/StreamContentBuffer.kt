package com.example.medgemma

/**
 * Batches streaming model tokens before UI list mutations.
 *
 * Flushes when either [maxTokensPerFlush] content tokens arrive or
 * [intervalMs] elapses since the last flush (whichever first), and always
 * on stats / finish so the final assistant text matches the full generation.
 */
class StreamContentBuffer(
    private val maxTokensPerFlush: Int = DEFAULT_MAX_TOKENS_PER_FLUSH,
    private val intervalMs: Long = DEFAULT_INTERVAL_MS,
    private val nowMs: () -> Long = System::currentTimeMillis
) {
    var content: String = ""
        private set
    var thought: String = ""
        private set
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
                    thought += token
                } else {
                    content += token
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
            content = content,
            thought = thought.ifBlank { null },
            stats = stats
        )
    }

    companion object {
        const val DEFAULT_MAX_TOKENS_PER_FLUSH = 4
        const val DEFAULT_INTERVAL_MS = 50L
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
