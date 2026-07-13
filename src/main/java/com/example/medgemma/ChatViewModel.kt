package com.example.medgemma

import android.app.Application
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    /** True once the first Compose frame has been painted — gates heavy auto-load. */
    private var uiReady = false

    init {
        // Disk status only (cached paths) — no native load / mmap on ViewModel creation.
        checkModelStatus()
    }

    /**
     * Called after the first UI frame so cold start paints before loading weights / JNI.
     */
    fun onUiReady() {
        if (uiReady) return
        uiReady = true
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
        if (!uiReady) return
        viewModelScope.launch {
            // Refresh cache off main if needed, then load only when both GGUFs exist.
            withContext(Dispatchers.IO) {
                modelManager.refreshDownloadedCache()
            }
            checkModelStatus()
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
            // Auto-load after download only once UI has already been ready this session.
            maybeAutoLoadEngine()
        }
    }

    fun cancelDownload(model: GgufModel) {
        modelManager.cancelDownload(model.fileName)
    }

    fun sendMessage(text: String, imageBytes: ByteArray? = null, imageUri: android.net.Uri? = null) {
        if (text.isBlank() && imageBytes == null) return
        if (!ggufManager.isInitialized) return

        viewModelScope.launch {
            // Prefer app-private file URI so thumbs survive process death / grant expiry.
            // Always on IO — never block the send frame with a multi‑MB copy.
            val stableImageUri = withContext(Dispatchers.IO) {
                imageUri?.let { uri ->
                    if (uri.scheme == "file" && uri.path?.contains("/chat_images/") == true) {
                        uri
                    } else {
                        ChatAttachmentStore.persistImage(getApplication(), uri) ?: uri
                    }
                }
            }

            _messages.add(ChatMessage(text, isUser = true, imageUri = stableImageUri))

            _isGenerating.value = true
            _uiState.value = ChatUiState.Loading(
                if (imageBytes != null) "Analyzing image…" else "Thinking…"
            )
            val assistantMessage = ChatMessage("", isUser = false)
            _messages.add(assistantMessage)
            val assistantIndex = _messages.size - 1

            // Reuse native KV on continuation turns; only the new user turn is tokenized.
            val plan = ChatPromptPolicy.planGeneration(
                isNewConversation = isNewConversation,
                userText = text,
                hasImage = imageBytes != null || imageUri != null
            )
            isNewConversation = false

            val buffer = StreamContentBuffer()
            fun applyUi(action: StreamContentBuffer.Action.UpdateUi) {
                _messages[assistantIndex] = assistantMessage.copy(
                    content = action.content,
                    thought = action.thought,
                    stats = action.stats
                )
            }

            // map+flowOn: accept/buffer on Default; collect (UI) stays on Main.
            ggufManager.generateStream(plan.prompt, imageBytes, clearContext = plan.clearContext)
                .map { token -> buffer.accept(token) }
                .flowOn(Dispatchers.Default)
                .collect { action ->
                    when (action) {
                        is StreamContentBuffer.Action.Error ->
                            _uiState.value = ChatUiState.Error(action.message)
                        is StreamContentBuffer.Action.UpdateUi -> {
                            applyUi(action)
                            if (_uiState.value is ChatUiState.Loading) {
                                _uiState.value = ChatUiState.Idle
                            }
                        }
                        StreamContentBuffer.Action.None -> Unit
                    }
                }
            when (val final = withContext(Dispatchers.Default) { buffer.finish() }) {
                is StreamContentBuffer.Action.UpdateUi -> applyUi(final)
                else -> Unit
            }
            _isGenerating.value = false
            _uiState.value = ChatUiState.Idle
        }
    }
}