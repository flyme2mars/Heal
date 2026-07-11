package com.example.medgemma

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Bundle
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
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.example.medgemma.ui.components.frostedGlassBar
import com.example.medgemma.ui.components.frostedGlassChip
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.rememberHazeState
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
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
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

@Composable
fun HeartbeatIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "heartbeat")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = keyframes { durationMillis = 1200; 1f at 0; 1.3f at 150; 1f at 300; 1.3f at 450; 1f at 600; 1f at 1200 },
            repeatMode = RepeatMode.Restart
        ), label = "scale"
    )
    Icon(imageVector = Icons.Default.Favorite, contentDescription = "Generating response", tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(18.dp).graphicsLayer { scaleX = scale; scaleY = scale })
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalHazeMaterialsApi::class)
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
    var showModelSheet by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var showDisclaimerDialog by remember {
        mutableStateOf(!ChatPreferences.isDisclaimerAcknowledged(context))
    }
    val messages = viewModel.messages
    val uiState by viewModel.uiState.collectAsState()
    val isGenerating by viewModel.isGenerating.collectAsState()
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val hazeState = rememberHazeState()
    val density = LocalDensity.current
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
            selectedImageUri = uri
            if (uri != null) {
                scope.launch {
                    imageBytes = withContext(Dispatchers.IO) { uriToRgbByteArray(context, uri) }
                }
            } else {
                imageBytes = null
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

    LaunchedEffect(isGenerating) {
        if (!isGenerating) return@LaunchedEffect
        snapshotFlow {
            messages.lastOrNull()?.let { msg ->
                msg.content.length + (msg.thought?.length ?: 0)
            } ?: 0
        }.collect {
            if (autoScrollEnabled && messages.isNotEmpty()) {
                scrollChatToLatest(animated = false)
            }
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
                .nestedScroll(userScrollConnection)
                .hazeSource(state = hazeState),
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
                        !message.isUser &&
                        message.stats == null
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
            if (uiState is ChatUiState.Loading && messages.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                        TypingIndicator(
                            message = (uiState as ChatUiState.Loading).message,
                            subtitle = "This may take a minute on first launch"
                        )
                    }
                }
            }
            if (uiState is ChatUiState.Error) {
                item { ErrorState((uiState as ChatUiState.Error).message) { showModelSheet = true } }
            }
            if (uiState is ChatUiState.NoModel) {
                item {
                    EmptyState(
                        icon = Icons.Default.Download,
                        title = "Model required",
                        subtitle = "Download a model to begin chatting.",
                        actionText = "Open models",
                        onAction = { showModelSheet = true }
                    )
                }
            }
            if (uiState is ChatUiState.ModelAvailable) {
                item {
                    EmptyState(
                        icon = Icons.Default.CheckCircle,
                        title = "Ready to start",
                        subtitle = "Models are downloaded. Load them into memory to chat.",
                        actionText = "Load model",
                        onAction = { viewModel.initializeEngine() }
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .onSizeChanged { topBarHeightPx = it.height }
                .frostedGlassBar(hazeState, soft = isGenerating)
                .statusBarsPadding()
        ) {
            CenterAlignedTopAppBar(
                    navigationIcon = {
                        IconButton(onClick = { showModelSheet = true }, enabled = !isGenerating) {
                            Icon(
                                Icons.Default.Tune,
                                contentDescription = "Models and settings",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    },
                    title = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "Heal",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            val engineStatus = when (val state = uiState) {
                                is ChatUiState.NoModel -> "Offline"
                                is ChatUiState.ModelAvailable -> "Ready to load"
                                is ChatUiState.Loading -> state.message
                                is ChatUiState.Error -> "Error"
                                is ChatUiState.Idle -> if (isGenerating) "Responding" else "Ready"
                            }
                            val isOnline = uiState is ChatUiState.Idle || uiState is ChatUiState.ModelAvailable
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isOnline) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    engineStatus,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
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
                        titleContentColor = MaterialTheme.colorScheme.onBackground
                    )
                )
            HorizontalDivider(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .onSizeChanged { bottomBarHeightPx = it.height }
                .frostedGlassBar(hazeState, soft = isGenerating)
                .imePadding()
                .navigationBarsPadding()
        ) {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
            )
            Row(
                modifier = Modifier
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.Bottom
            ) {
                FilledTonalIconButton(
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    enabled = uiState is ChatUiState.Idle && !isGenerating,
                    modifier = Modifier
                        .size(44.dp)
                        .border(GlassStyle.border(0.10f), CircleShape),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = GlassStyle.iconWell(),
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        disabledContainerColor = GlassStyle.fieldDisabled(),
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Icon(
                        Icons.Default.Image,
                        contentDescription = "Attach image",
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                val inputPlaceholder = when (uiState) {
                    is ChatUiState.NoModel -> "Download a model to chat…"
                    is ChatUiState.ModelAvailable -> "Load model to chat…"
                    is ChatUiState.Loading -> if (messages.isEmpty()) "Loading model…" else "Heal is responding…"
                    is ChatUiState.Error -> "Fix error in settings…"
                    is ChatUiState.Idle -> "Message Heal…"
                }
                val fieldShape = RoundedCornerShape(22.dp)
                // Composer field: compact image chip sits inside the glass field, not a huge block above.
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .border(GlassStyle.border(0.10f), fieldShape)
                        .clip(fieldShape)
                        .background(
                            if (isInputEnabled) GlassStyle.field() else GlassStyle.fieldDisabled()
                        )
                        .animateContentSize()
                ) {
                    AnimatedVisibility(visible = selectedImageUri != null) {
                        selectedImageUri?.let { uri ->
                            InputImageChip(
                                uri = uri,
                                onRemove = {
                                    selectedImageUri = null
                                    imageBytes = null
                                },
                                modifier = Modifier.padding(start = 10.dp, top = 10.dp, end = 10.dp)
                            )
                        }
                    }
                    TextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text(
                                inputPlaceholder,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        shape = fieldShape,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                            cursorColor = MaterialTheme.colorScheme.onSurface,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            disabledTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        enabled = isInputEnabled,
                        maxLines = 5,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                if (inputText.isNotBlank() || imageBytes != null) sendCurrentMessage()
                            }
                        )
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                val isSendEnabled = (uiState is ChatUiState.Idle || isGenerating) &&
                    (inputText.isNotBlank() || imageBytes != null || isGenerating)
                SendHaltButton(
                    isGenerating = isGenerating,
                    enabled = isSendEnabled,
                    onClick = {
                        if (isGenerating) {
                            view.performHapticFeedback(HapticFeedbackConstants.REJECT)
                            viewModel.stopGeneration()
                        } else if (isSendEnabled) {
                            sendCurrentMessage()
                        }
                    }
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = with(density) { bottomBarHeightPx.toDp() })
        )

        if (showScrollToBottom) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(
                        end = 16.dp,
                        bottom = with(density) { bottomBarHeightPx.toDp() } + 14.dp
                    )
                    .size(40.dp)
                    .clip(CircleShape)
                    .frostedGlassChip(hazeState, soft = isGenerating)
                    .border(GlassStyle.border(0.14f), CircleShape)
                    .clickable {
                        scope.launch { scrollChatToLatest(animated = true) }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Scroll to latest message",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
    if (showModelSheet) {
        ModalBottomSheet(
            onDismissRequest = { showModelSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = MaterialTheme.colorScheme.onSurface,
            dragHandle = {
                BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        ) {
            ModelHubContent(viewModel)
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
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Try asking",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            "Not medical advice — always consult a professional.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
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
fun LazyItemScope.EmptyState(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, actionText: String, onAction: () -> Unit) {
    Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                title,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(32.dp))
            HealButton(text = actionText, onClick = onAction, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
fun HealButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, isPrimary: Boolean = true) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        color = if (isPrimary) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
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
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp
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
            MaterialTheme.colorScheme.error.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Clear,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
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
            HealButton(text = "Settings", onClick = onCheck, isPrimary = false, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
fun ModelHubContent(viewModel: ChatViewModel) {
    val downloadProgress by viewModel.modelManager.downloadProgress.collectAsState()
    val downloadedFiles by viewModel.modelManager.downloadedFiles.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    var tokenInput by remember { mutableStateOf(viewModel.modelManager.hfToken ?: "") }
    // Paths from cached downloaded set — no listFiles() per recomposition.
    val llmReady = remember(downloadedFiles) {
        viewModel.modelManager.availableLlmModels.any { it.fileName in downloadedFiles }
    }
    val mmprojReady = remember(downloadedFiles) {
        viewModel.modelManager.availableMmprojModels.any { it.fileName in downloadedFiles }
    }
    Column(modifier = Modifier.fillMaxWidth().padding(24.dp).verticalScroll(rememberScrollState())) {
        Text("Models & settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "Download models and manage your token",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            "Token",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = tokenInput,
            onValueChange = { tokenInput = it; viewModel.modelManager.hfToken = it },
            placeholder = {
                Text("hf_...", color = MaterialTheme.colorScheme.onSurfaceVariant)
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                cursorColor = MaterialTheme.colorScheme.primary
            )
        )
        Spacer(modifier = Modifier.height(32.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "STATUS",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val statusText = when(uiState) { is ChatUiState.Idle -> "Ready"; is ChatUiState.Loading -> "Loading"; is ChatUiState.Error -> "Error"; is ChatUiState.NoModel -> "No model"; else -> "Ready" }
                    Text(
                        statusText,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                if (llmReady && mmprojReady && uiState !is ChatUiState.Idle && uiState !is ChatUiState.Loading) {
                    HealButton(
                        text = "Load model",
                        onClick = { viewModel.initializeEngine() },
                        modifier = Modifier.width(140.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            "LANGUAGE MODELS",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(12.dp))
        viewModel.modelManager.availableLlmModels.forEach { model ->
            ModelItem(
                model = model,
                isDownloaded = model.fileName in downloadedFiles,
                downloadProgress = downloadProgress[model.fileName],
                onDownload = { viewModel.downloadModel(model) }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            "VISION COMPONENTS",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(12.dp))
        viewModel.modelManager.availableMmprojModels.forEach { model ->
            ModelItem(
                model = model,
                isDownloaded = model.fileName in downloadedFiles,
                downloadProgress = downloadProgress[model.fileName],
                onDownload = { viewModel.downloadModel(model) }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun ModelItem(model: GgufModel, isDownloaded: Boolean, downloadProgress: DownloadProgress?, onDownload: () -> Unit) {
    val isDownloading = downloadProgress?.isDownloading == true
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (isDownloaded) {
                MaterialTheme.colorScheme.surfaceContainerHigh
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            }
        ),
        border = if (isDownloaded) {
            androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant
            )
        } else null
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        model.name,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        model.fileName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (model.sizeLabel.isNotBlank()) {
                        Text(
                            model.sizeLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
                if (isDownloaded) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                } else if (isDownloading) {
                    CircularProgressIndicator(
                        progress = { downloadProgress?.progress ?: 0f },
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    IconButton(onClick = onDownload, modifier = Modifier.size(24.dp)) {
                        Icon(
                            Icons.Default.Download,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            if (isDownloading) {
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { downloadProgress?.progress ?: 0f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .clip(CircleShape),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )
            }
        }
    }
}

@Composable
fun NeuralPulse(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(initialValue = 0.3f, targetValue = 1f, animationSpec = infiniteRepeatable(animation = tween(1000, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse), label = "alpha")
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(modifier = Modifier.size(6.dp).graphicsLayer { this.alpha = alpha }.clip(CircleShape).background(MaterialTheme.colorScheme.primary))
        Text("Thinking", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f), fontWeight = FontWeight.Bold)
    }
}

/**
 * Compact attachment preview inside the composer field — small rounded thumb +
 * glass remove badge, not a full-width block.
 */
@Composable
private fun InputImageChip(
    uri: Uri,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val thumbShape = RoundedCornerShape(12.dp)
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .padding(top = 4.dp, end = 6.dp)
                .size(width = 56.dp, height = 56.dp)
                .clip(thumbShape)
                .border(GlassStyle.border(0.14f), thumbShape)
                .background(GlassStyle.inset())
        ) {
            val chipContext = LocalContext.current
            AsyncImage(
                model = ImageRequest.Builder(chipContext)
                    .data(uri)
                    .size(168) // 56dp @ 3x — avoid full-res decode for composer chip
                    .crossfade(false)
                    .build(),
                contentDescription = "Attached image",
                modifier = Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )
            // Soft bottom scrim so the thumb reads as a chip, not a raw crop.
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(16.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                MaterialTheme.colorScheme.scrim.copy(alpha = 0.28f)
                            )
                        )
                    )
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(20.dp)
                .clip(CircleShape)
                .border(GlassStyle.border(0.16f), CircleShape)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Remove image",
                modifier = Modifier.size(12.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * Send / halt control.
 *
 * Idle + ready: solid monochrome disc with send chevron.
 * Generating: glass disc, soft dual orbital arcs (tokens in flight), and a small
 * rounded square halt glyph — no alarm red; the motion is the signal.
 */
@Composable
private fun SendHaltButton(
    isGenerating: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val surface = MaterialTheme.colorScheme.surface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val interaction = remember { MutableInteractionSource() }

    val infinite = rememberInfiniteTransition(label = "halt-orbit")
    val orbit by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbit"
    )
    val breath by infinite.animateFloat(
        initialValue = 0.28f,
        targetValue = 0.72f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breath"
    )

    Box(
        modifier = modifier
            .size(44.dp)
            .semantics {
                role = Role.Button
                contentDescription = if (isGenerating) "Stop generating" else "Send message"
            }
            .clip(CircleShape)
            .then(
                when {
                    isGenerating -> Modifier
                        .border(GlassStyle.border(0.16f), CircleShape)
                        .background(GlassStyle.iconWell())
                    enabled -> Modifier.background(onSurface)
                    else -> Modifier
                        .border(GlassStyle.border(0.10f), CircleShape)
                        .background(GlassStyle.iconWell())
                }
            )
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = ripple(bounded = true, radius = 22.dp),
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = isGenerating,
            transitionSpec = {
                (fadeIn(tween(160)) + scaleIn(initialScale = 0.82f, animationSpec = tween(180)))
                    .togetherWith(
                        fadeOut(tween(120)) + scaleOut(targetScale = 0.82f, animationSpec = tween(120))
                    )
            },
            label = "send-halt"
        ) { generating ->
            if (generating) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(4.dp)
                            .graphicsLayer { rotationZ = orbit }
                    ) {
                        val stroke = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round)
                        val inset = stroke.width / 2f
                        val arcSize = Size(size.width - stroke.width, size.height - stroke.width)
                        val topLeft = Offset(inset, inset)
                        // Two opposing arcs read as a token stream orbiting the halt square.
                        drawArc(
                            color = onSurface.copy(alpha = breath),
                            startAngle = -20f,
                            sweepAngle = 78f,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = stroke
                        )
                        drawArc(
                            color = onSurface.copy(alpha = breath * 0.45f),
                            startAngle = 160f,
                            sweepAngle = 52f,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = stroke
                        )
                    }
                    // Soft stop glyph — rounded square, same family as glass chrome.
                    Box(
                        modifier = Modifier
                            .size(11.dp)
                            .clip(RoundedCornerShape(2.5.dp))
                            .background(onSurface)
                    )
                }
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = null,
                    tint = if (enabled) surface else muted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

private const val SCROLL_BOTTOM_THRESHOLD_PX = 120

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
    // Tall / still-measuring streaming items can need more than one pass.
    repeat(4) {
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
    // Bubble only when there is text / assistant chrome — image lives outside it.
    val showBubble = hasCaption ||
        !message.isUser ||
        message.thought != null ||
        message.stats != null
    var isThoughtExpanded by remember { mutableStateOf(false) }
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
                        if (!message.isUser && message.thought != null) {
                            val thoughtPreview = message.thought.lineSequence()
                                .firstOrNull { it.isNotBlank() }
                                ?.take(80)
                                ?.let { if (message.thought.length > 80) "$it…" else it }
                            val insetShape = RoundedCornerShape(12.dp)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 10.dp)
                                    .clip(insetShape)
                                    .border(GlassStyle.border(0.08f), insetShape)
                                    .background(GlassStyle.inset())
                                    .clickable { isThoughtExpanded = !isThoughtExpanded }
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Column {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Default.Psychology,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Reasoning",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Icon(
                                            imageVector = if (isThoughtExpanded) {
                                                Icons.Default.KeyboardArrowUp
                                            } else {
                                                Icons.Default.KeyboardArrowDown
                                            },
                                            contentDescription = if (isThoughtExpanded) {
                                                "Collapse reasoning"
                                            } else {
                                                "Expand reasoning"
                                            },
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (!isThoughtExpanded && thoughtPreview != null) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = thoughtPreview,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1
                                        )
                                    }
                                    AnimatedVisibility(visible = isThoughtExpanded) {
                                        Column {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            MarkdownText(
                                                text = message.thought,
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    fontSize = 12.sp,
                                                    lineHeight = 16.sp
                                                ),
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        if (message.isUser) {
                            if (hasCaption) {
                                Text(
                                    text = message.content,
                                    style = MaterialTheme.typography.bodyLarge,
                                    lineHeight = 22.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        } else {
                            if (message.content.isEmpty()) {
                                if (message.thought == null) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        HeartbeatIndicator()
                                    }
                                } else {
                                    NeuralPulse(modifier = Modifier.padding(top = 4.dp))
                                }
                            } else {
                                // Live markdown while streaming (memoized parse in MarkdownText).
                                MarkdownText(
                                    text = message.content,
                                    style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 22.sp),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                        if (message.stats != null && !message.isUser) {
                            MessageStatsRow(stats = message.stats)
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
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.92f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
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
                    .crossfade(true)
                    .build(),
                contentDescription = "Expanded image",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 48.dp)
                    // Absorb taps on the image so only the dimmed chrome dismisses.
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    ),
                contentScale = androidx.compose.ui.layout.ContentScale.Fit
            )
            IconButton(
                onClick = onDismiss,
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

@Composable
private fun MessageStatsRow(stats: String) {
    var expanded by remember { mutableStateOf(false) }
    val parsed = remember(stats) { formatMessageStats(stats) }
    Spacer(modifier = Modifier.height(12.dp))
    Row(
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .clickable { expanded = !expanded }
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = parsed.summary,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(4.dp))
        Icon(
            imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            contentDescription = if (expanded) "Hide details" else "Show details",
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    AnimatedVisibility(visible = expanded) {
        Text(
            text = parsed.details,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

private fun buildInlineMarkdown(
    text: String,
    style: androidx.compose.ui.text.TextStyle,
    linkColor: Color
) = buildAnnotatedString {
    val pattern = Pattern.compile(
        """(\*\*.*?\*\*|\*.*?\*|`[^`]+`|\[([^\]]+)]\(([^)]+)\)|(https?://\S+))""",
        Pattern.DOTALL
    )
    var lastIndex = 0
    val matcher = pattern.matcher(text)
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

private val BULLET_LINE = Regex("^[-*] .+")
private val NUMBERED_LINE = Regex("^\\d+\\. .+")

@Composable
fun MarkdownText(text: String?, style: androidx.compose.ui.text.TextStyle, modifier: Modifier = Modifier) {
    if (text == null) return
    val linkColor = MaterialTheme.colorScheme.primary
    // Parse once per text value — not on every parent recompose with same content.
    val blocks = remember(text, style, linkColor) {
        text.split("\n").map { line ->
            when {
                line.matches(BULLET_LINE) -> MarkdownBlock.Bullet(
                    buildInlineMarkdown(line.drop(2).trim(), style, linkColor)
                )
                line.matches(NUMBERED_LINE) -> {
                    val dotIndex = line.indexOf(". ")
                    val prefix = line.substring(0, dotIndex + 1)
                    MarkdownBlock.Numbered(
                        prefix = prefix,
                        body = buildInlineMarkdown(line.substring(dotIndex + 2).trim(), style, linkColor)
                    )
                }
                line.isBlank() -> MarkdownBlock.Spacer
                else -> MarkdownBlock.Paragraph(buildInlineMarkdown(line, style, linkColor))
            }
        }
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Bullet -> Row {
                    Text("• ", style = style, modifier = Modifier.padding(end = 2.dp))
                    Text(block.body, style = style)
                }
                is MarkdownBlock.Numbered -> Row {
                    Text("${block.prefix} ", style = style, modifier = Modifier.padding(end = 2.dp))
                    Text(block.body, style = style)
                }
                MarkdownBlock.Spacer -> Spacer(modifier = Modifier.height(4.dp))
                is MarkdownBlock.Paragraph -> Text(block.body, style = style)
            }
        }
    }
}

private sealed class MarkdownBlock {
    data class Bullet(val body: AnnotatedString) : MarkdownBlock()
    data class Numbered(val prefix: String, val body: AnnotatedString) : MarkdownBlock()
    data class Paragraph(val body: AnnotatedString) : MarkdownBlock()
    data object Spacer : MarkdownBlock()
}

@Composable
fun TypingIndicator(message: String, subtitle: String? = null) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
        Text(
            message,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        if (subtitle != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
        CircularProgressIndicator(
            modifier = Modifier.size(28.dp),
            strokeWidth = 2.5.dp,
            color = MaterialTheme.colorScheme.primary
        )
    }
}
