package com.example.medgemma

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Drives the shipped [ChatPromptPolicy] — first turn clears KV + full system prompt;
 * continuation reuses KV and sends only the new turn.
 */
class ChatPromptPolicyTest {

    @Test
    fun firstTurn_clearsContext_andIncludesSystemPrefix() {
        val plan = ChatPromptPolicy.planGeneration(
            isNewConversation = true,
            userText = "What is hypertension?",
            hasImage = false
        )

        assertTrue("first turn must clear native KV", plan.clearContext)
        assertTrue(plan.prompt.contains(ChatPromptPolicy.SYSTEM_PREFIX))
        assertTrue(plan.prompt.startsWith("<start_of_turn>user\n"))
        assertTrue(plan.prompt.contains("What is hypertension?"))
        assertTrue(plan.prompt.endsWith("<start_of_turn>model\n"))
        assertFalse(
            "first turn must not start with end_of_turn closer",
            plan.prompt.startsWith("<end_of_turn>")
        )
    }

    @Test
    fun firstTurn_withImage_includesImageMarker() {
        val plan = ChatPromptPolicy.planGeneration(
            isNewConversation = true,
            userText = "Describe this scan",
            hasImage = true
        )

        assertTrue(plan.clearContext)
        assertTrue(plan.prompt.contains("<start_of_image>\n"))
        assertTrue(plan.prompt.contains(ChatPromptPolicy.SYSTEM_PREFIX))
    }

    @Test
    fun continuation_reusesContext_andSendsOnlyNewUserTurn() {
        val plan = ChatPromptPolicy.planGeneration(
            isNewConversation = false,
            userText = "And treatment options?",
            hasImage = false
        )

        assertFalse("continuation must NOT clear KV", plan.clearContext)
        assertFalse(
            "system prefix belongs only on first turn",
            plan.prompt.contains(ChatPromptPolicy.SYSTEM_PREFIX)
        )
        assertTrue(plan.prompt.startsWith("<end_of_turn>\n"))
        assertTrue(plan.prompt.contains("<start_of_turn>user\n"))
        assertTrue(plan.prompt.contains("And treatment options?"))
        assertTrue(plan.prompt.endsWith("<start_of_turn>model\n"))
        // Must not re-embed an entire multi-turn history shape
        assertEquals(
            1,
            Regex("<start_of_turn>user").findAll(plan.prompt).count()
        )
        assertEquals(
            1,
            Regex("<start_of_turn>model").findAll(plan.prompt).count()
        )
    }

    @Test
    fun continuation_withImage_includesImageMarker_withoutSystem() {
        val plan = ChatPromptPolicy.planGeneration(
            isNewConversation = false,
            userText = "What does this show?",
            hasImage = true
        )

        assertFalse(plan.clearContext)
        assertTrue(plan.prompt.contains("<start_of_image>\n"))
        assertFalse(plan.prompt.contains(ChatPromptPolicy.SYSTEM_PREFIX))
    }

    @Test
    fun clearChatSemantics_nextSendIsFirstTurnAgain() {
        // Simulates ViewModel: after clearMessages(), isNewConversation = true
        val afterClear = ChatPromptPolicy.planGeneration(
            isNewConversation = true,
            userText = "Fresh start",
            hasImage = false
        )
        val afterFirst = ChatPromptPolicy.planGeneration(
            isNewConversation = false,
            userText = "Follow-up",
            hasImage = false
        )

        assertTrue(afterClear.clearContext)
        assertTrue(afterClear.prompt.contains(ChatPromptPolicy.SYSTEM_PREFIX))
        assertFalse(afterFirst.clearContext)
        assertFalse(afterFirst.prompt.contains(ChatPromptPolicy.SYSTEM_PREFIX))
    }

    @Test
    fun firstAndContinuation_promptsDiffer() {
        val first = ChatPromptPolicy.buildFirstTurnPrompt("hello", hasImage = false)
        val cont = ChatPromptPolicy.buildContinuationPrompt("hello", hasImage = false)
        assertTrue(first != cont)
        assertTrue(first.contains(ChatPromptPolicy.SYSTEM_PREFIX))
        assertFalse(cont.contains(ChatPromptPolicy.SYSTEM_PREFIX))
    }
}
