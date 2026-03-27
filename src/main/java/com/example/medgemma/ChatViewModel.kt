package com.example.medgemma

import android.app.Application
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ChatMessage(
    val content: String,
    val isUser: Boolean,
    val thought: String? = null,
    val stats: String? = null,
    val imageUri: android.net.Uri? = null
)

sealed class ChatUiState {
    object Idle : ChatUiState()
    data class Loading(val message: String = "Loading...") : ChatUiState()
    data class Error(val message: String) : ChatUiState()
}

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val ggufManager = GgufInferenceManager()

    private val _messages = mutableStateListOf<ChatMessage>()
    val messages: List<ChatMessage> = _messages

    private val _uiState = MutableStateFlow<ChatUiState>(ChatUiState.Idle)
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _uiState.value = ChatUiState.Loading("Initializing GGUF Engine...")
            val result = ggufManager.initialize(
                modelPath = "/data/local/tmp/models/model.gguf",
                mmprojPath = "/data/local/tmp/models/mmproj.gguf"
            )
            
            if (result.isSuccess) {
                _uiState.value = ChatUiState.Idle
            } else {
                _uiState.value = ChatUiState.Error(result.exceptionOrNull()?.message ?: "Unknown init error")
            }
        }
    }

    fun sendMessage(text: String, imageBytes: ByteArray? = null, imageUri: android.net.Uri? = null) {
        if (text.isBlank() && imageBytes == null) return

        // Add user message to UI
        _messages.add(ChatMessage(text, isUser = true, imageUri = imageUri))
        
        viewModelScope.launch {
            _uiState.value = ChatUiState.Loading("Thinking...")
            
            val assistantMessage = ChatMessage("", isUser = false)
            _messages.add(assistantMessage)
            val assistantIndex = _messages.size - 1

            // Apply Gemma 3 / MedGemma 1.5 Chat Template
            // If image is present, the MTMD engine handles inserting the <image> tokens.
            val imageMarker = if (imageBytes != null) "<image>\n" else ""
            val formattedPrompt = "<start_of_turn>user\nYou are a helpful medical assistant.\n$imageMarker$text<end_of_turn>\n<start_of_turn>model\n"
            
            android.util.Log.d("ChatViewModel", "Formatted Prompt:\n$formattedPrompt")

            var fullResponse = ""
            var fullThought = ""
            var isThinking = false

            ggufManager.generateStream(formattedPrompt, imageBytes).collect { token ->
                when {
                    token.startsWith("Error: ") -> {
                        _uiState.value = ChatUiState.Error(token.removePrefix("Error: "))
                    }
                    token.startsWith("[STATS] ") -> {
                        val stats = token.removePrefix("[STATS] ")
                        _messages[assistantIndex] = assistantMessage.copy(
                            content = fullResponse, 
                            thought = if (fullThought.isNotBlank()) fullThought else null,
                            stats = stats
                        )
                    }
                    token == "[THOUGHT_START]" -> {
                        isThinking = true
                    }
                    token == "[THOUGHT_END]" -> {
                        isThinking = false
                    }
                    else -> {
                        if (isThinking) {
                            fullThought += token
                        } else {
                            fullResponse += token
                        }
                        
                        // Update the message in the list to trigger recomposition
                        if (assistantIndex >= 0 && assistantIndex < _messages.size) {
                            try {
                                _messages[assistantIndex] = assistantMessage.copy(
                                    content = fullResponse,
                                    thought = if (fullThought.isNotBlank()) fullThought else null
                                )
                            } catch (e: Exception) {
                                // Ignore concurrent modification issues during streaming
                            }
                        }
                    }
                }
                
                // Once we start getting tokens, we are no longer "Loading"
                if (_uiState.value is ChatUiState.Loading) {
                    _uiState.value = ChatUiState.Idle
                }
            }
            
            _uiState.value = ChatUiState.Idle
        }
    }
}
