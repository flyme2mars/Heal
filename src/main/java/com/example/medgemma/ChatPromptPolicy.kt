package com.example.medgemma

/**
 * Pure prompt + KV-context policy for multi-turn MedGemma chat.
 *
 * First turn of a conversation clears native KV and sends system prefix + user turn.
 * Continuation turns keep KV and send only the new user turn (plus model cue).
 * Callers must reset native context when starting a brand-new conversation.
 */
data class GenerationPlan(
    val prompt: String,
    val clearContext: Boolean
)

object ChatPromptPolicy {
    // Gemma/MedGemma: no system role — prefix is merged into the first user turn.
    const val SYSTEM_PREFIX =
        "You are a helpful medical assistant. This is not medical advice — always consult a healthcare professional."

    /**
     * @param isNewConversation true after clear-chat / first message of a session
     * @param userText latest user message body (may be blank when image-only)
     * @param hasImage whether this turn includes an image for the vision path
     */
    fun planGeneration(
        isNewConversation: Boolean,
        userText: String,
        hasImage: Boolean
    ): GenerationPlan {
        val clearContext = isNewConversation
        val prompt = if (clearContext) {
            buildFirstTurnPrompt(userText, hasImage)
        } else {
            buildContinuationPrompt(userText, hasImage)
        }
        return GenerationPlan(prompt = prompt, clearContext = clearContext)
    }

    /** Full first-turn prompt: system prefix once, user turn, model cue. */
    fun buildFirstTurnPrompt(userText: String, hasImage: Boolean): String {
        val sb = StringBuilder()
        sb.append("<start_of_turn>user\n")
        sb.append(SYSTEM_PREFIX).append("\n\n")
        if (hasImage) {
            sb.append("<start_of_image>\n")
        }
        sb.append(userText.trim())
        sb.append("<end_of_turn>\n")
        sb.append("<start_of_turn>model\n")
        return sb.toString()
    }

    /**
     * Incremental prompt for KV reuse.
     * Native generation stops at EOG without writing `<end_of_turn>` into the cache,
     * so we close the prior model turn before the next user turn.
     */
    fun buildContinuationPrompt(userText: String, hasImage: Boolean): String {
        val sb = StringBuilder()
        sb.append("<end_of_turn>\n")
        sb.append("<start_of_turn>user\n")
        if (hasImage) {
            sb.append("<start_of_image>\n")
        }
        sb.append(userText.trim())
        sb.append("<end_of_turn>\n")
        sb.append("<start_of_turn>model\n")
        return sb.toString()
    }
}
