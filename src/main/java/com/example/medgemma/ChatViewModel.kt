package com.example.medgemma

import android.app.Application
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SnackbarMessage(
    val text: String,
    val isError: Boolean = false
)

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
    private val ggufManager = GgufInferenceManager(application)
    val modelManager = ModelManager(application)
    private val _messages = mutableStateListOf<ChatMessage>()
    val messages: List<ChatMessage> = _messages
    private val _uiState = MutableStateFlow<ChatUiState>(ChatUiState.NoModel)
    val uiState = _uiState.asStateFlow()
    private val _isGenerating = MutableStateFlow(false)
    val isGenerating = _isGenerating.asStateFlow()
    private val _snackbar = MutableSharedFlow<SnackbarMessage>()
    val snackbar = _snackbar.asSharedFlow()
    private var isNewConversation = true

    init {
        checkModelStatus()
        maybeAutoLoadEngine()
    }

    fun stopGeneration() {
        ggufManager.stopGeneration()
        _isGenerating.value = false
    }

    fun clearMessages() {
        _messages.clear()
        isNewConversation = true
        if (ggufManager.isInitialized) ggufManager.resetContext()
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

    private fun maybeAutoLoadEngine() {
        viewModelScope.launch {
            val llmPath = modelManager.getDownloadedLlmPath()
            val mmprojPath = modelManager.getDownloadedMmprojPath()
            if (llmPath != null && mmprojPath != null && !ggufManager.isInitialized) {
                initializeEngine()
            }
        }
    }

    fun initializeEngine() {
        viewModelScope.launch {
            val llmPath = modelManager.getDownloadedLlmPath()
            val mmprojPath = modelManager.getDownloadedMmprojPath()
            if (llmPath == null || mmprojPath == null) return@launch
            _uiState.value = ChatUiState.Loading("Loading model weights…")
            android.util.Log.i("ChatViewModel", "Loading model: $llmPath")
            val result = ggufManager.initialize(llmPath, mmprojPath)
            android.util.Log.i("ChatViewModel", "Init result: ${if (result.isSuccess) "OK" else result.exceptionOrNull()?.message}")
            if (result.isSuccess) {
                _uiState.value = ChatUiState.Idle
                _snackbar.emit(SnackbarMessage("Model loaded — you're ready to chat"))
            } else {
                val error = "Init failed: ${result.exceptionOrNull()?.message}"
                _uiState.value = ChatUiState.Error(error)
                _snackbar.emit(SnackbarMessage(error, isError = true))
            }
        }
    }

    fun downloadModel(model: GgufModel) {
        viewModelScope.launch {
            modelManager.downloadModel(model)
            checkModelStatus()
            maybeAutoLoadEngine()
        }
    }

    fun sendMessage(text: String, imageBytes: ByteArray? = null, imageUri: android.net.Uri? = null) {
        if (text.isBlank() && imageBytes == null) return
        if (!ggufManager.isInitialized) return

        _messages.add(ChatMessage(text, isUser = true, imageUri = imageUri))

        viewModelScope.launch {
            _isGenerating.value = true
            _uiState.value = ChatUiState.Loading(
                if (imageBytes != null) "Analyzing image…" else "Thinking…"
            )
            val assistantMessage = ChatMessage("", isUser = false)
            _messages.add(assistantMessage)
            val assistantIndex = _messages.size - 1

            // Full conversation history is embedded in the prompt; native side clears KV each turn.
            val prompt = buildConversationPrompt()
            isNewConversation = false
            var fullResponse = ""
            var fullThought = ""
            var isThinking = false

            ggufManager.generateStream(prompt, imageBytes, clearContext = true).collect { token ->
                when {
                    token.startsWith("Error: ") -> _uiState.value = ChatUiState.Error(token.removePrefix("Error: "))
                    token.startsWith("[STATS] ") -> _messages[assistantIndex] = assistantMessage.copy(
                        content = fullResponse,
                        thought = fullThought.ifBlank { null },
                        stats = token.removePrefix("[STATS] ")
                    )
                    token == "[THOUGHT_START]" -> isThinking = true
                    token == "[THOUGHT_END]" -> isThinking = false
                    else -> {
                        if (isThinking) fullThought += token else fullResponse += token
                        _messages[assistantIndex] = assistantMessage.copy(
                            content = fullResponse,
                            thought = fullThought.ifBlank { null }
                        )
                    }
                }
                if (_uiState.value is ChatUiState.Loading) _uiState.value = ChatUiState.Idle
            }
            _isGenerating.value = false
            _uiState.value = ChatUiState.Idle
        }
    }

    private fun buildConversationPrompt(): String {
        val sb = StringBuilder()
        // Exclude the empty assistant placeholder added for streaming UI updates.
        val history = _messages.dropLast(1)
        var firstUserTurn = true

        for (msg in history) {
            if (msg.isUser) {
                sb.append("<start_of_turn>user\n")
                if (firstUserTurn) {
                    sb.append(SYSTEM_PREFIX).append("\n\n")
                    firstUserTurn = false
                }
                if (msg.imageUri != null) {
                    sb.append("<start_of_image>\n")
                }
                sb.append(msg.content.trim())
                sb.append("<end_of_turn>\n")
            } else if (msg.content.isNotBlank()) {
                sb.append("<start_of_turn>model\n")
                sb.append(msg.content.trim())
                sb.append("<end_of_turn>\n")
            }
        }

        sb.append("<start_of_turn>model\n")
        return sb.toString()
    }

    companion object {
        // Gemma/MedGemma: no system role — prefix is merged into the first user turn (see unsloth chat_template.jinja).
        private const val SYSTEM_PREFIX =
            "You are a helpful medical assistant. This is not medical advice — always consult a healthcare professional."
    }
}