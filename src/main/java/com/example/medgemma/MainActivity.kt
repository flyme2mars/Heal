package com.example.medgemma

import android.animation.ValueAnimator
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import com.example.medgemma.ui.components.GlassStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.medgemma.ui.theme.HealRed
import com.example.medgemma.ui.theme.MedGemmaTheme
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.util.regex.Pattern
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : ComponentActivity() {
    /** Splash stays until the first Compose frame is ready to paint. */
    private val keepSplash = AtomicBoolean(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        splashScreen.setKeepOnScreenCondition { keepSplash.get() }

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                AndroidColor.TRANSPARENT,
                AndroidColor.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.auto(
                AndroidColor.TRANSPARENT,
                AndroidColor.TRANSPARENT
            )
        )
        super.onCreate(savedInstanceState)
        setContent {
            MedGemmaTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ChatScreen(
                        onFirstFrame = {
                            keepSplash.set(false)
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel = viewModel(),
    onFirstFrame: () -> Unit = {}
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    var inputText by remember { mutableStateOf("") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var imageBytes by remember { mutableStateOf<ByteArray?>(null) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var showDisclaimerDialog by remember {
        mutableStateOf(!ChatPreferences.isDisclaimerAcknowledged(context))
    }
    val messages = viewModel.messages
    val uiState by viewModel.uiState.collectAsState()
    val isGenerating by viewModel.isGenerating.collectAsState()
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val density = LocalDensity.current
    val reducedMotion = rememberReducedMotion()
    var topBarHeightPx by remember { mutableIntStateOf(0) }
    var bottomBarHeightPx by remember { mutableIntStateOf(0) }
    val isInputEnabled = uiState is ChatUiState.Idle && !isGenerating
    // Follow streaming/new messages only while the user is pinned to the latest.
    var autoScrollEnabled by remember { mutableStateOf(true) }
    // Ignore NestedScroll callbacks caused by our own scroll-to-bottom calls.
    var programmaticScrollDepth by remember { mutableIntStateOf(0) }

    // First frame paints → drop splash, then allow deferred JNI / model auto-load.
    LaunchedEffect(Unit) {
        yield()
        onFirstFrame()
        viewModel.onUiReady()
    }

    // FAB visibility is purely positional — not tied to autoScrollEnabled — so the
    // button shows whenever the last message's end is off-screen (including mid-
    // bubble on a tall reply).
    val showScrollToBottom by remember {
        derivedStateOf {
            messages.isNotEmpty() && !listState.layoutInfo.isNearBottom()
        }
    }

    val userScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (programmaticScrollDepth == 0 && source == NestedScrollSource.UserInput) {
                    // Positive y: finger drags down → content moves down → viewing older msgs.
                    if (available.y > 0f) {
                        autoScrollEnabled = false
                    }
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (programmaticScrollDepth == 0 && source == NestedScrollSource.UserInput) {
                    if (listState.layoutInfo.isNearBottom()) {
                        autoScrollEnabled = true
                    } else if (consumed.y > 0f) {
                        autoScrollEnabled = false
                    }
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (programmaticScrollDepth == 0) {
                    autoScrollEnabled = listState.layoutInfo.isNearBottom()
                }
                return Velocity.Zero
            }
        }
    }

    suspend fun scrollChatToLatest(animated: Boolean) {
        val index = messages.lastIndex
        if (index < 0) return
        programmaticScrollDepth++
        try {
            autoScrollEnabled = true
            if (animated) {
                listState.animateScrollToBottom(index)
            } else {
                listState.scrollChatToBottom(index)
            }
            autoScrollEnabled = true
        } finally {
            programmaticScrollDepth--
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri == null) {
                selectedImageUri = null
                imageBytes = null
                return@rememberLauncherForActivityResult
            }
            scope.launch {
                // Persist immediately so composer preview + chat history outlive the picker grant.
                val stable = withContext(Dispatchers.IO) {
                    ChatAttachmentStore.persistImage(context, uri) ?: uri
                }
                selectedImageUri = stable
                imageBytes = withContext(Dispatchers.IO) { uriToRgbByteArray(context, stable) }
            }
        }
    )

    LaunchedEffect(viewModel) {
        viewModel.snackbar.collect { event ->
            snackbarHostState.showSnackbar(
                message = event.text,
                withDismissAction = event.isError
            )
        }
    }

    // Re-enable follow mode when the user manually returns to the bottom.
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.isNearBottom() }
            .collect { nearBottom ->
                if (nearBottom && programmaticScrollDepth == 0) {
                    autoScrollEnabled = true
                }
            }
    }

    LaunchedEffect(messages.size) {
        if (messages.isEmpty()) return@LaunchedEffect
        // Only auto-follow when the user is pinned to the bottom (or just re-enabled).
        if (autoScrollEnabled) {
            scrollChatToLatest(animated = true)
        }
    }

    // Throttle follow-scroll during stream (~8Hz) so measure/scroll don't thrash every flush.
    LaunchedEffect(isGenerating) {
        if (!isGenerating) return@LaunchedEffect
        snapshotFlow {
            messages.lastOrNull()?.content?.length ?: 0
        }.collect {
            if (autoScrollEnabled && messages.isNotEmpty()) {
                scrollChatToLatest(animated = false)
            }
            kotlinx.coroutines.delay(SCROLL_FOLLOW_THROTTLE_MS)
        }
    }

    fun sendCurrentMessage() {
        if (!isInputEnabled) return
        if (inputText.isBlank() && imageBytes == null) return
        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        // Sending always re-pins to the latest turn.
        autoScrollEnabled = true
        viewModel.sendMessage(inputText, imageBytes, selectedImageUri)
        inputText = ""
        selectedImageUri = null
        imageBytes = null
    }

    if (showDisclaimerDialog) {
        AlertDialog(
            onDismissRequest = {},
            icon = { Icon(Icons.Default.Info, contentDescription = null) },
            title = { Text("Medical disclaimer") },
            text = {
                Text(
                    "Heal provides general health information only. It is not a substitute " +
                        "for professional medical advice, diagnosis, or treatment. Always consult " +
                        "a qualified healthcare provider."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    ChatPreferences.setDisclaimerAcknowledged(context)
                    showDisclaimerDialog = false
                }) {
                    Text("I understand")
                }
            }
        )
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Start new chat?") },
            text = { Text("This clears the current conversation and resets context. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearMessages()
                    showClearConfirm = false
                }) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(userScrollConnection),
            state = listState,
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = with(density) { topBarHeightPx.toDp() } + 20.dp,
                bottom = with(density) { bottomBarHeightPx.toDp() } + 20.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            itemsIndexed(
                items = messages,
                // Stable identity while assistant content streams (index slot).
                key = { index, msg -> if (msg.isUser) "u-$index" else "a-$index" }
            ) { index, message ->
                ChatMessageItem(
                    message = message,
                    isStreaming = isGenerating &&
                        index == messages.lastIndex &&
                        !message.isUser
                )
            }
            if (uiState is ChatUiState.Idle && messages.isEmpty()) {
                item {
                    ConversationStarters(
                        enabled = isInputEnabled,
                        onStarterSelected = { starter ->
                            if (starter.withImage) {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                            inputText = starter.prompt
                        }
                    )
                }
            }
            val showLoading = messages.isEmpty() &&
                (uiState is ChatUiState.Loading || uiState is ChatUiState.ModelAvailable)
            if (showLoading) {
                item {
                    Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                        TypingIndicator(
                            message = "Loading…",
                            subtitle = "First launch may take a minute."
                        )
                    }
                }
            }
            if (uiState is ChatUiState.Error) {
                item {
                    ErrorState((uiState as ChatUiState.Error).message) {
                        viewModel.retryLastError()
                    }
                }
            }
            if (uiState is ChatUiState.NoModel) {
                item {
                    EmptyState(
                        title = "Model not found",
                        subtitle = "Push model.gguf and mmproj.gguf to /data/local/tmp/models, then return.",
                        actionText = "Check again",
                        onAction = { viewModel.rescanModels() }
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .onSizeChanged { topBarHeightPx = it.height }
                .statusBarsPadding()
        ) {
            CenterAlignedTopAppBar(
                navigationIcon = {
                    // Same width as the new-chat action so the title stays centered.
                    Spacer(modifier = Modifier.size(48.dp))
                },
                title = {
                    Text(
                        "Heal",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                actions = {
                    IconButton(
                        onClick = { showClearConfirm = true },
                        enabled = messages.isNotEmpty() && !isGenerating
                    ) {
                        Icon(
                            Icons.Default.EditNote,
                            contentDescription = "New chat",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }

        val lookingAtImage = messages.lastOrNull { it.isUser }?.imageUri != null
        ChatComposerBar(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .onSizeChanged { bottomBarHeightPx = it.height }
                .background(MaterialTheme.colorScheme.surface)
                .imePadding()
                .navigationBarsPadding(),
            inputText = inputText,
            onInputChange = { inputText = it },
            selectedImageUri = selectedImageUri,
            onRemoveImage = {
                selectedImageUri = null
                imageBytes = null
            },
            isInputEnabled = isInputEnabled,
            isGenerating = isGenerating,
            canAttach = uiState is ChatUiState.Idle && !isGenerating,
            canSend = (uiState is ChatUiState.Idle || isGenerating) &&
                (inputText.isNotBlank() || imageBytes != null || isGenerating),
            placeholder = when {
                isGenerating && lookingAtImage -> "Looking at image…"
                isGenerating -> "Responding"
                else -> "Message Heal…"
            },
            reducedMotion = reducedMotion,
            onAttach = {
                photoPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onSendOrStop = {
                if (isGenerating) {
                    view.performHapticFeedback(HapticFeedbackConstants.REJECT)
                    viewModel.stopGeneration()
                } else {
                    sendCurrentMessage()
                }
            }
        )

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = with(density) { bottomBarHeightPx.toDp() })
        )

        val jumpDuration = motionDurationMs(reducedMotion, 180)
        AnimatedVisibility(
            visible = showScrollToBottom,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(
                    end = 16.dp,
                    bottom = with(density) { bottomBarHeightPx.toDp() } + 14.dp
                ),
            enter = fadeIn(motionTween(jumpDuration)),
            exit = fadeOut(motionTween(jumpDuration))
        ) {
            Surface(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable {
                        scope.launch { scrollChatToLatest(animated = true) }
                    },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
                border = GlassStyle.border(0.14f)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Scroll to latest message",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ConversationStarters(
    enabled: Boolean,
    onStarterSelected: (ConversationStarter) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            "Try asking",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            items(conversationStarters) { starter ->
                SuggestionChip(
                    onClick = { if (enabled) onStarterSelected(starter) },
                    label = { Text(starter.label) },
                    enabled = enabled,
                    icon = {
                        if (starter.withImage) {
                            Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun LazyItemScope.EmptyState(title: String, subtitle: String, actionText: String, onAction: () -> Unit) {
    Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            HealButton(text = actionText, onClick = onAction, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
fun HealButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, isPrimary: Boolean = true) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        color = if (isPrimary) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent,
        shape = MaterialTheme.shapes.medium,
        border = if (isPrimary) {
            androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant
            )
        } else null
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = text,
                color = if (isPrimary) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun ErrorState(message: String, onCheck: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        shape = MaterialTheme.shapes.large,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            HealRed.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Clear,
                    contentDescription = null,
                    tint = HealRed,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Error",
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                message,
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(16.dp))
            HealButton(text = "Try again", onClick = onCheck, isPrimary = false, modifier = Modifier.fillMaxWidth())
        }
    }
}

/**
 * Composer geometry — shared so attach, text, and send share one baseline.
 *
 *  • [ComposerHitSize]  — compact resting height (keyboard closed)
 *  • [ComposerExpandedMinHeight] — roomier field while IME is open
 *  • [ComposerGlyphSize] — filled disc / icon well diameter
 */
private val ComposerHitSize = 48.dp
private val ComposerExpandedMinHeight = 92.dp
private val ComposerGlyphSize = 40.dp
private val ComposerIconSize = 22.dp
private val ComposerInnerPad = 6.dp
/**
 * Top pad so the first text line (~22sp) optically centers with the 48dp
 * side buttons. Bottom pad keeps multi-line content from hugging the edge.
 */
private val ComposerTextPadTop = 13.dp
private val ComposerTextPadBottom = 13.dp

/**
 * Chat input bar: one glass pill with three perfectly aligned controls
 * (attach · text · send/stop).
 *
 * Keyboard closed: compact single-line height.
 * Keyboard open: field expands; text + placeholder start at the **top**,
 * side actions stay on the first-line baseline, extra space grows below.
 */
@Composable
private fun ChatComposerBar(
    inputText: String,
    onInputChange: (String) -> Unit,
    selectedImageUri: Uri?,
    onRemoveImage: () -> Unit,
    isInputEnabled: Boolean,
    isGenerating: Boolean,
    canAttach: Boolean,
    canSend: Boolean,
    placeholder: String,
    onAttach: () -> Unit,
    onSendOrStop: () -> Unit,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier
) {
    val fieldActive = isInputEnabled || isGenerating
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val density = LocalDensity.current
    val expandMs = motionDurationMs(reducedMotion, 120, reducedMs = 80)
    val chipFadeMs = motionDurationMs(reducedMotion, 120, reducedMs = 80)

    // True while the soft keyboard is visible (after imePadding is applied).
    val imeOpen = WindowInsets.ime.getBottom(density) > 0
    val fieldMinHeight by animateDpAsState(
        targetValue = if (imeOpen) ComposerExpandedMinHeight else ComposerHitSize,
        animationSpec = motionTween<Dp>(expandMs),
        label = "composer-field-min"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            color = if (fieldActive) GlassStyle.field() else GlassStyle.fieldDisabled(),
            border = GlassStyle.border(if (fieldActive) 0.12f else 0.07f),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(ComposerInnerPad)
            ) {
                AnimatedVisibility(
                    visible = selectedImageUri != null,
                    enter = fadeIn(motionTween(chipFadeMs)),
                    exit = fadeOut(motionTween(chipFadeMs))
                ) {
                    selectedImageUri?.let { uri ->
                        InputImageChip(
                            uri = uri,
                            onRemove = onRemoveImage,
                            modifier = Modifier.padding(
                                start = 6.dp,
                                top = 4.dp,
                                end = 6.dp,
                                bottom = 6.dp
                            )
                        )
                    }
                }

                // Top-aligned: first text line, placeholder, attach, and send share one row.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = fieldMinHeight),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    // 1 — Attach (sits on first-line baseline)
                    ComposerSideButton(
                        onClick = onAttach,
                        enabled = canAttach,
                        contentDescription = "Attach image",
                        filled = false
                    ) {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = null,
                            modifier = Modifier.size(ComposerIconSize)
                        )
                    }

                    // 2 — Text: always grows downward from the top
                    val textColor = if (isInputEnabled) onSurface else onVariant
                    BasicTextField(
                        value = inputText,
                        onValueChange = onInputChange,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = fieldMinHeight)
                            .padding(horizontal = 6.dp),
                        enabled = isInputEnabled,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = textColor,
                            lineHeight = 22.sp
                        ),
                        cursorBrush = SolidColor(onSurface),
                        maxLines = 6,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                if (canSend && !isGenerating) onSendOrStop()
                            }
                        ),
                        decorationBox = { innerTextField ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = fieldMinHeight)
                                    .padding(
                                        top = ComposerTextPadTop,
                                        bottom = ComposerTextPadBottom
                                    ),
                                contentAlignment = Alignment.TopStart
                            ) {
                                // Placeholder stacked on the same origin as the caret.
                                if (inputText.isEmpty()) {
                                    Text(
                                        text = placeholder,
                                        color = onVariant.copy(alpha = 0.58f),
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            lineHeight = 22.sp
                                        ),
                                        maxLines = 1
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )

                    // 3 — Send / stop (same top baseline as attach)
                    SendHaltButton(
                        isGenerating = isGenerating,
                        enabled = canSend,
                        onClick = onSendOrStop,
                        reducedMotion = reducedMotion
                    )
                }
            }
        }
    }
}

/**
 * Side control (attach): 48dp hit target, 40dp quiet circular well so it
 * mirrors the send disc and both sit on the same visual baseline.
 */
@Composable
private fun ComposerSideButton(
    onClick: () -> Unit,
    enabled: Boolean,
    contentDescription: String,
    filled: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val contentColor = if (enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.42f)
    }

    Box(
        modifier = modifier
            .size(ComposerHitSize)
            .semantics {
                role = Role.Button
                this.contentDescription = contentDescription
            }
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = ripple(bounded = false, radius = 24.dp),
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(ComposerGlyphSize)
                .clip(CircleShape)
                .background(
                    if (filled) GlassStyle.iconWell()
                    else Color.Transparent
                ),
            contentAlignment = Alignment.Center
        ) {
            CompositionLocalProvider(
                LocalContentColor provides contentColor
            ) {
                content()
            }
        }
    }
}

/**
 * Attachment thumbnail — rounded square with a clean remove affordance.
 */
@Composable
private fun InputImageChip(
    uri: Uri,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val thumbShape = RoundedCornerShape(12.dp)
    val chipContext = LocalContext.current
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .padding(top = 4.dp, end = 10.dp)
                .size(56.dp)
                .clip(thumbShape)
                .border(GlassStyle.border(0.14f), thumbShape)
                .background(GlassStyle.inset())
        ) {
            AsyncImage(
                model = ImageRequest.Builder(chipContext)
                    .data(uri)
                    .size(168)
                    .crossfade(false)
                    .build(),
                contentDescription = "Attached image",
                modifier = Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )
        }
        // Remove — always reachable 48dp? visual 22dp is fine for secondary chip action
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(24.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.96f))
                .border(GlassStyle.border(0.16f), CircleShape)
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Remove image",
                modifier = Modifier.size(13.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * Send / halt — same 48dp hit target as attach.
 * Idle/ready: HealRed disc + send arrow.
 * Generating: static stop icon (no orbit / breath).
 */
@Composable
private fun SendHaltButton(
    isGenerating: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val interaction = remember { MutableInteractionSource() }
    val fillMs = motionDurationMs(reducedMotion, 120, reducedMs = 80)
    val swapMs = motionDurationMs(reducedMotion, 120, reducedMs = 80)

    val fillAlpha by animateFloatAsState(
        targetValue = when {
            isGenerating -> 0.14f
            enabled -> 1f
            else -> 0.14f
        },
        animationSpec = motionTween(fillMs),
        label = "send-fill"
    )
    val isFilled = enabled && !isGenerating

    Box(
        modifier = modifier
            .size(ComposerHitSize)
            .semantics {
                role = Role.Button
                contentDescription = if (isGenerating) "Stop generating" else "Send message"
            }
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = ripple(bounded = false, radius = 24.dp),
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(ComposerGlyphSize)
                .clip(CircleShape)
                .background(
                    if (isFilled) HealRed
                    else onSurface.copy(alpha = fillAlpha)
                ),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = isGenerating,
                transitionSpec = {
                    fadeIn(motionTween(swapMs)).togetherWith(fadeOut(motionTween(swapMs)))
                },
                label = "send-halt"
            ) { generating ->
                if (generating) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = null,
                        tint = onSurface,
                        modifier = Modifier.size(18.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = null,
                        tint = if (isFilled) {
                            Color.White
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                        },
                        modifier = Modifier
                            .size(18.dp)
                            .padding(start = 1.dp)
                    )
                }
            }
        }
    }
}

private const val SCROLL_BOTTOM_THRESHOLD_PX = 120
/** Max follow-scroll rate while generating (~8 Hz). */
private const val SCROLL_FOLLOW_THROTTLE_MS = 120L

/**
 * Bottom edge of the *usable* chat area: viewport end minus [afterContentPadding].
 *
 * LazyColumn contentPadding.bottom reserves space for the frosted input bar. Aligning
 * to raw [viewportEndOffset] parks the last tokens under that bar during generate /
 * jump-to-bottom; the padded edge keeps them fully readable above the input.
 */
private fun LazyListLayoutInfo.contentBottomEdge(): Int =
    viewportEndOffset - afterContentPadding

/**
 * True when the conversation end is at (or within [thresholdPx] of) the usable bottom.
 *
 * Distance from bottom = (lastItemBottom) - contentBottomEdge. Positive means more
 * content below the fold (including the lower half of a tall last bubble).
 */
private fun LazyListLayoutInfo.isNearBottom(thresholdPx: Int = SCROLL_BOTTOM_THRESHOLD_PX): Boolean {
    if (totalItemsCount == 0) return true
    val lastItem = visibleItemsInfo.lastOrNull() ?: return false
    if (lastItem.index != totalItemsCount - 1) return false
    val distanceFromBottom = (lastItem.offset + lastItem.size) - contentBottomEdge()
    return distanceFromBottom <= thresholdPx
}

/**
 * Scroll so the end of [index] sits at the usable bottom (above the input bar padding).
 *
 * [LazyListState.animateScrollToItem] alone only pins the *start* of the item;
 * for a long assistant bubble that leaves the user stuck mid-message. After the
 * item is brought into view we repeatedly [animateScrollBy]/[scrollBy] any remaining gap.
 */
private suspend fun LazyListState.animateScrollToBottom(index: Int) {
    if (index < 0) return
    animateScrollToItem(index)
    alignItemEndToContentBottom(index, animated = true)
}

private suspend fun LazyListState.scrollChatToBottom(index: Int) {
    if (index < 0) return
    scrollToItem(index)
    alignItemEndToContentBottom(index, animated = false)
}

private suspend fun LazyListState.alignItemEndToContentBottom(index: Int, animated: Boolean) {
    // Prefer 2 passes (was 4) — enough for tall bubbles without thrashing every flush.
    repeat(if (animated) 3 else 2) {
        val info = layoutInfo
        val item = info.visibleItemsInfo.lastOrNull { it.index == index } ?: return
        // Positive gap: item extends past the padded bottom → scroll forward to reveal
        // its end *above* the input bar (not under it).
        val gap = (item.offset + item.size) - info.contentBottomEdge()
        if (gap <= 1) return
        if (animated) animateScrollBy(gap.toFloat()) else scrollBy(gap.toFloat())
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Heal response", text))
}

private fun shareText(context: Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "Share response"))
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatMessageItem(message: ChatMessage, isStreaming: Boolean = false) {
    val context = LocalContext.current
    val view = LocalView.current
    var showMessageMenu by remember { mutableStateOf(false) }
    val horizontal = if (message.isUser) Alignment.End else Alignment.Start
    // Monochrome glass: user slightly more opaque, assistant quieter — no brand fill.
    val containerColor = if (message.isUser) GlassStyle.userBubble() else GlassStyle.assistantBubble()
    val contentColor = MaterialTheme.colorScheme.onSurface
    val shape = if (message.isUser) {
        RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp)
    } else {
        RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp)
    }
    val hasImage = message.imageUri != null
    val hasCaption = message.content.isNotBlank()
    val showBubble = hasCaption
    if (!hasImage && !showBubble) return
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = horizontal
    ) {
        if (hasImage) {
            MessageImageThumb(
                uri = message.imageUri!!,
                modifier = Modifier.padding(bottom = if (showBubble) 6.dp else 0.dp)
            )
        }
        if (showBubble) {
            Box {
                Surface(
                    color = containerColor,
                    contentColor = contentColor,
                    shape = shape,
                    border = GlassStyle.border(if (message.isUser) 0.12f else 0.08f),
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                    modifier = Modifier
                        .widthIn(max = 340.dp)
                        // animateContentSize during stream is pure layout thrash.
                        .then(if (!isStreaming) Modifier.animateContentSize() else Modifier)
                        .then(
                            if (!message.isUser && message.content.isNotBlank() && !isStreaming) {
                                Modifier.combinedClickable(
                                    onClick = {},
                                    onLongClick = {
                                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                        showMessageMenu = true
                                    }
                                )
                            } else {
                                Modifier
                            }
                        )
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        if (message.isUser) {
                            if (hasCaption) {
                                Text(
                                    text = message.content,
                                    style = MaterialTheme.typography.bodyLarge,
                                    lineHeight = 22.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        } else if (message.content.isNotEmpty()) {
                            MarkdownText(
                                text = message.content,
                                style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 22.sp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        if (!message.isUser && message.content.isNotBlank() && !isStreaming) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Not medical advice. Consult a healthcare professional.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                            )
                        }
                    }
                }
                DropdownMenu(
                    expanded = showMessageMenu,
                    onDismissRequest = { showMessageMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Copy") },
                        onClick = {
                            copyToClipboard(context, message.content)
                            showMessageMenu = false
                        },
                        leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Share") },
                        onClick = {
                            shareText(context, message.content)
                            showMessageMenu = false
                        },
                        leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) }
                    )
                }
            }
        }
    }
}

/** Small square thumb outside the text bubble (above it); tap opens a lightbox. */
@Composable
private fun MessageImageThumb(
    uri: Uri,
    modifier: Modifier = Modifier
) {
    var showFullscreen by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    val thumbContext = LocalContext.current
    Box(
        modifier = modifier
            .size(72.dp)
            .clip(shape)
            .border(GlassStyle.border(0.14f), shape)
            .background(GlassStyle.inset())
            .clickable { showFullscreen = true }
    ) {
        AsyncImage(
            model = ImageRequest.Builder(thumbContext)
                .data(uri)
                .size(216) // 72dp @ 3x
                .crossfade(false)
                .build(),
            contentDescription = "Attached image, tap to expand",
            modifier = Modifier
                .fillMaxSize()
                .clip(shape),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop
        )
    }
    if (showFullscreen) {
        FullscreenImageViewer(
            uri = uri,
            onDismiss = { showFullscreen = false }
        )
    }
}

/** Near full-screen image lightbox — tap background or close to dismiss. */
@Composable
private fun FullscreenImageViewer(
    uri: Uri,
    onDismiss: () -> Unit
) {
    val reducedMotion = rememberReducedMotion()
    val fadeMs = motionDurationMs(reducedMotion, 200, reducedMs = 80)
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val alpha by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = motionTween(fadeMs),
        finishedListener = { if (!shown) onDismiss() },
        label = "lightbox-fade"
    )
    fun requestDismiss() {
        if (fadeMs == 0) {
            onDismiss()
        } else {
            shown = false
        }
    }
    Dialog(
        onDismissRequest = { requestDismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { this.alpha = alpha }
                .background(Color.Black.copy(alpha = 0.92f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { requestDismiss() }
                )
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            val viewerContext = LocalContext.current
            val displayMetrics = viewerContext.resources.displayMetrics
            val maxSide = maxOf(displayMetrics.widthPixels, displayMetrics.heightPixels)
            AsyncImage(
                model = ImageRequest.Builder(viewerContext)
                    .data(uri)
                    .size(maxSide)
                    .crossfade(false)
                    .build(),
                contentDescription = "Expanded image",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 48.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    ),
                contentScale = androidx.compose.ui.layout.ContentScale.Fit
            )
            IconButton(
                onClick = { requestDismiss() },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.12f))
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color.White
                )
            }
        }
    }
}

/** Compiled once — never on the stream hot path. */
private val INLINE_MARKDOWN: Pattern = Pattern.compile(
    """(\*\*.*?\*\*|\*.*?\*|`[^`]+`|\[([^\]]+)]\(([^)]+)\)|(https?://\S+))""",
    Pattern.DOTALL
)
private val BULLET_LINE_PATTERN: Pattern = Pattern.compile("^[-*] .+")
private val NUMBERED_LINE_PATTERN: Pattern = Pattern.compile("^\\d+\\. .+")

private fun buildInlineMarkdown(
    text: String,
    style: androidx.compose.ui.text.TextStyle,
    linkColor: Color
) = buildAnnotatedString {
    var lastIndex = 0
    val matcher = INLINE_MARKDOWN.matcher(text)
    while (matcher.find()) {
        append(text.substring(lastIndex, matcher.start()))
        val match = matcher.group()
        when {
            match.startsWith("**") && match.length > 4 -> {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = style.color)) {
                    append(match.substring(2, match.length - 2))
                }
            }
            match.startsWith("*") && !match.startsWith("**") && match.length > 2 -> {
                withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = style.color)) {
                    append(match.substring(1, match.length - 1))
                }
            }
            match.startsWith("`") && match.length > 2 -> {
                withStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        color = style.color,
                        background = style.color.copy(alpha = 0.08f)
                    )
                ) {
                    append(match.substring(1, match.length - 1))
                }
            }
            match.startsWith("[") && matcher.group(2) != null && matcher.group(3) != null -> {
                val label = matcher.group(2)!!
                val url = matcher.group(3)!!
                withLink(
                    LinkAnnotation.Url(
                        url,
                        TextLinkStyles(style = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))
                    )
                ) { append(label) }
            }
            match.startsWith("http") -> {
                val url = match.trimEnd('.', ',', ';')
                withLink(
                    LinkAnnotation.Url(
                        url,
                        TextLinkStyles(style = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))
                    )
                ) { append(url) }
            }
            else -> append(match)
        }
        lastIndex = matcher.end()
    }
    if (lastIndex < text.length) {
        appendIncompleteMarkdown(text.substring(lastIndex), style)
    }
}

/**
 * Full-message markdown → single [AnnotatedString] (one Text node).
 * Cheaper to layout while streaming than a Column of per-line Texts.
 */
private fun buildMarkdownAnnotated(
    text: String,
    style: androidx.compose.ui.text.TextStyle,
    linkColor: Color
): AnnotatedString = buildAnnotatedString {
    val lines = text.split("\n")
    lines.forEachIndexed { index, line ->
        when {
            BULLET_LINE_PATTERN.matcher(line).matches() -> {
                append("• ")
                append(buildInlineMarkdown(line.drop(2).trim(), style, linkColor))
            }
            NUMBERED_LINE_PATTERN.matcher(line).matches() -> {
                val dotIndex = line.indexOf(". ")
                append(line.substring(0, dotIndex + 1))
                append(" ")
                append(buildInlineMarkdown(line.substring(dotIndex + 2).trim(), style, linkColor))
            }
            line.isBlank() -> {
                // Blank line → visual gap via double newline
            }
            else -> append(buildInlineMarkdown(line, style, linkColor))
        }
        if (index < lines.lastIndex) append("\n")
    }
}

private fun AnnotatedString.Builder.appendIncompleteMarkdown(
    text: String,
    style: androidx.compose.ui.text.TextStyle
) {
    if (text.isEmpty()) return

    val boldStart = text.lastIndexOf("**")
    if (boldStart >= 0 && text.indexOf("**", boldStart + 2) < 0) {
        append(text.substring(0, boldStart))
        val boldContent = text.substring((boldStart + 2).coerceAtMost(text.length))
        if (boldContent.isNotEmpty()) {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = style.color)) {
                append(boldContent)
            }
        } else {
            append("**")
        }
        return
    }

    val italicStart = text.lastIndexOf('*')
    if (italicStart >= 0 &&
        !text.regionMatches(italicStart, "**", 0, 2) &&
        text.indexOf('*', italicStart + 1) < 0
    ) {
        append(text.substring(0, italicStart))
        val italicContent = text.substring((italicStart + 1).coerceAtMost(text.length))
        if (italicContent.isNotEmpty()) {
            withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = style.color)) {
                append(italicContent)
            }
        } else {
            append("*")
        }
        return
    }

    val codeStart = text.lastIndexOf('`')
    if (codeStart >= 0 && text.indexOf('`', codeStart + 1) < 0) {
        append(text.substring(0, codeStart))
        val codeContent = text.substring((codeStart + 1).coerceAtMost(text.length))
        if (codeContent.isNotEmpty()) {
            withStyle(
                SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    color = style.color,
                    background = style.color.copy(alpha = 0.08f)
                )
            ) {
                append(codeContent)
            }
        } else {
            append("`")
        }
        return
    }

    append(text)
}

@Composable
fun MarkdownText(text: String?, style: androidx.compose.ui.text.TextStyle, modifier: Modifier = Modifier) {
    if (text == null) return
    val linkColor = MaterialTheme.colorScheme.onSurface
    // One AnnotatedString → one Text. Live markdown, far fewer layout nodes than per-line Columns.
    val annotated = remember(text, style, linkColor) {
        buildMarkdownAnnotated(text, style, linkColor)
    }
    Text(
        text = annotated,
        style = style,
        modifier = modifier
    )
}

@Composable
fun TypingIndicator(message: String, subtitle: String? = null) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
        Text(
            message,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        if (subtitle != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
        CircularProgressIndicator(
            modifier = Modifier.size(28.dp),
            strokeWidth = 2.5.dp,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        val scale = try {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            )
        } catch (_: Throwable) {
            1f
        }
        scale == 0f || !ValueAnimator.areAnimatorsEnabled()
    }
}

private fun motionDurationMs(
    reducedMotion: Boolean,
    normalMs: Int,
    reducedMs: Int = 80
): Int {
    if (!ValueAnimator.areAnimatorsEnabled()) return 0
    return if (reducedMotion) reducedMs else normalMs
}

private fun <T> motionTween(durationMs: Int): FiniteAnimationSpec<T> {
    return if (durationMs <= 0) {
        snap()
    } else {
        tween(durationMillis = durationMs, easing = FastOutSlowInEasing)
    }
}
