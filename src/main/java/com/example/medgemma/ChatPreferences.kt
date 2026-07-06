package com.example.medgemma

import android.content.Context

object ChatPreferences {
    private const val PREFS = "heal_chat_prefs"
    private const val KEY_DISCLAIMER_ACK = "disclaimer_acknowledged"

    fun isDisclaimerAcknowledged(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_DISCLAIMER_ACK, false)

    fun setDisclaimerAcknowledged(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_DISCLAIMER_ACK, true)
            .apply()
    }
}

data class ConversationStarter(
    val label: String,
    val prompt: String,
    val withImage: Boolean = false
)

val conversationStarters = listOf(
    ConversationStarter("Describe a rash", "What could this skin rash indicate?", withImage = true),
    ConversationStarter("Headache causes", "What are common causes of a persistent headache?"),
    ConversationStarter("Lab results", "Help me understand what these lab results might mean."),
    ConversationStarter("Urgent symptoms", "Which symptoms would warrant seeing a doctor urgently?")
)