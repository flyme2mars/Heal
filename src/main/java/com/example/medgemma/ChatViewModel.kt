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
    val stats: String? = null
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

    fun sendMessage(text: String) {
        if (text.isBlank()) return

        // Add user message to UI
        _messages.add(ChatMessage(text, isUser = true))
        
        viewModelScope.launch {
            _uiState.value = ChatUiState.Loading("Thinking...")
            
            val assistantMessage = ChatMessage("", isUser = false)
            _messages.add(assistantMessage)
            val assistantIndex = _messages.size - 1

            // Apply Gemma 3 / MedGemma 1.5 Chat Template
            // Note: System instructions go inside the first user turn for Gemma
            val formattedPrompt = "<start_of_turn>user\nYou are a helpful medical assistant.\n$text<end_of_turn>\n<start_of_turn>model\n"

            var fullResponse = ""
            ggufManager.generateStream(formattedPrompt).collect { token ->
                if (token.startsWith("[STATS] ")) {
                    val stats = token.removePrefix("[STATS] ")
                    _messages[assistantIndex] = assistantMessage.copy(content = fullResponse, stats = stats)
                } else {
                    fullResponse += token
                    // Update the message in the list to trigger recomposition
                    _messages[assistantIndex] = assistantMessage.copy(content = fullResponse)
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
