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
    object NoModel : ChatUiState()
    object ModelAvailable : ChatUiState()
    data class Loading(val message: String = "Loading...") : ChatUiState()
    data class Error(val message: String) : ChatUiState()
}

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val ggufManager = GgufInferenceManager()
    val modelManager = ModelManager(application)
    private val _messages = mutableStateListOf<ChatMessage>()
    val messages: List<ChatMessage> = _messages
    private val _uiState = MutableStateFlow<ChatUiState>(ChatUiState.NoModel)
    val uiState = _uiState.asStateFlow()
    private val _isGenerating = MutableStateFlow(false)
    val isGenerating = _isGenerating.asStateFlow()

    init { checkModelStatus() }

    fun stopGeneration() {
        ggufManager.stopGeneration()
        _isGenerating.value = false
    }

    fun clearMessages() {
        _messages.clear()
    }

    fun checkModelStatus() {
        val llmPath = modelManager.getDownloadedLlmPath()
        val mmprojPath = modelManager.getDownloadedMmprojPath()
        if (llmPath != null && mmprojPath != null) {
            _uiState.value = if (!ggufManager.isInitialized) ChatUiState.ModelAvailable else ChatUiState.Idle
        } else {
            _uiState.value = ChatUiState.NoModel
        }
    }

    fun initializeEngine() {
        viewModelScope.launch {
            val llmPath = modelManager.getDownloadedLlmPath()
            val mmprojPath = modelManager.getDownloadedMmprojPath()
            if (llmPath == null || mmprojPath == null) return@launch
            _uiState.value = ChatUiState.Loading("Initializing...")
            val result = ggufManager.initialize(llmPath, mmprojPath)
            _uiState.value = if (result.isSuccess) ChatUiState.Idle else ChatUiState.Error("Init failed")
        }
    }

    fun downloadModel(model: GgufModel) {
        viewModelScope.launch {
            modelManager.downloadModel(model)
            checkModelStatus()
        }
    }

    fun sendMessage(text: String, imageBytes: ByteArray? = null, imageUri: android.net.Uri? = null) {
        if (text.isBlank() && imageBytes == null) return
        if (!ggufManager.isInitialized) return
        _messages.add(ChatMessage(text, isUser = true, imageUri = imageUri))
        viewModelScope.launch {
            _isGenerating.value = true
            _uiState.value = ChatUiState.Loading("Thinking...")
            val assistantMessage = ChatMessage("", isUser = false)
            _messages.add(assistantMessage)
            val assistantIndex = _messages.size - 1
            val imageMarker = if (imageBytes != null) "<image>\n" else ""
            val prompt = "<start_of_turn>user\nYou are a helpful medical assistant.\n$imageMarker$text<end_of_turn>\n<start_of_turn>model\n"
            var fullResponse = ""
            var fullThought = ""
            var isThinking = false
            ggufManager.generateStream(prompt, imageBytes).collect { token ->
                when {
                    token.startsWith("Error: ") -> _uiState.value = ChatUiState.Error(token)
                    token.startsWith("[STATS] ") -> _messages[assistantIndex] = assistantMessage.copy(content = fullResponse, thought = fullThought.ifBlank { null }, stats = token.removePrefix("[STATS] "))
                    token == "[THOUGHT_START]" -> isThinking = true
                    token == "[THOUGHT_END]" -> isThinking = false
                    else -> {
                        if (isThinking) fullThought += token else fullResponse += token
                        if (assistantIndex >= 0) _messages[assistantIndex] = assistantMessage.copy(content = fullResponse, thought = fullThought.ifBlank { null })
                    }
                }
                if (_uiState.value is ChatUiState.Loading) _uiState.value = ChatUiState.Idle
            }
            _isGenerating.value = false
            _uiState.value = ChatUiState.Idle
        }
    }
}
