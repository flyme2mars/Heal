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
    object Idle : ChatUiState() // Engine is loaded and ready
    object NoModel : ChatUiState() // Models need downloading
    object ModelAvailable : ChatUiState() // Models downloaded but not loaded
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

    init {
        checkModelStatus()
    }

    fun checkModelStatus() {
        val llmPath = modelManager.getDownloadedLlmPath()
        val mmprojPath = modelManager.getDownloadedMmprojPath()
        
        if (llmPath != null && mmprojPath != null) {
            if (!ggufManager.isInitialized) {
                _uiState.value = ChatUiState.ModelAvailable
            } else {
                _uiState.value = ChatUiState.Idle
            }
        } else {
            _uiState.value = ChatUiState.NoModel
        }
    }

    fun initializeEngine() {
        viewModelScope.launch {
            val llmPath = modelManager.getDownloadedLlmPath()
            val mmprojPath = modelManager.getDownloadedMmprojPath()

            if (llmPath == null || mmprojPath == null) {
                _uiState.value = ChatUiState.Error("Please download both Model and mmproj first.")
                return@launch
            }

            _uiState.value = ChatUiState.Loading("Initializing GGUF Engine...")
            val result = ggufManager.initialize(
                modelPath = llmPath,
                mmprojPath = mmprojPath
            )
            
            if (result.isSuccess) {
                _uiState.value = ChatUiState.Idle
            } else {
                _uiState.value = ChatUiState.Error(result.exceptionOrNull()?.message ?: "Unknown init error")
            }
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
        
        if (!ggufManager.isInitialized) {
            _uiState.value = ChatUiState.Error("Engine not initialized. Please Load Engine from Model Hub.")
            return
        }

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
