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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import com.example.medgemma.ui.components.frostedGlassBar
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.medgemma.ui.theme.MedGemmaTheme
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.regex.Pattern

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
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
                    ChatScreen()
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
fun ChatScreen(viewModel: ChatViewModel = viewModel()) {
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
    var autoScrollEnabled by remember { mutableStateOf(true) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            selectedImageUri = uri
            if (uri != null) {
                scope.launch {
                    imageBytes = withContext(Dispatchers.IO) { uriToByteArray(context, uri) }
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

    LaunchedEffect(listState) {
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val nearBottom = layoutInfo.isNearBottom()
            listState.isScrollInProgress to nearBottom
        }.collect { (isScrolling, nearBottom) ->
            when {
                nearBottom -> autoScrollEnabled = true
                isScrolling -> autoScrollEnabled = false
            }
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isEmpty()) return@LaunchedEffect
        autoScrollEnabled = true
        listState.animateScrollToBottom(messages.lastIndex)
    }

    LaunchedEffect(isGenerating) {
        if (!isGenerating) return@LaunchedEffect
        snapshotFlow {
            messages.lastOrNull()?.let { msg ->
                msg.content.length + (msg.thought?.length ?: 0)
            } ?: 0
        }.collect {
            if (autoScrollEnabled && messages.isNotEmpty()) {
                listState.scrollChatToBottom(messages.lastIndex)
            }
        }
    }

    fun sendCurrentMessage() {
        if (!isInputEnabled) return
        if (inputText.isBlank() && imageBytes == null) return
        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
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
            itemsIndexed(messages) { index, message ->
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
                .frostedGlassBar(hazeState)
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
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .onSizeChanged { bottomBarHeightPx = it.height }
                .frostedGlassBar(hazeState)
                .imePadding()
                .navigationBarsPadding()
        ) {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
                    AnimatedVisibility(visible = selectedImageUri != null) {
                        Box(
                            modifier = Modifier
                                .padding(16.dp)
                                .size(80.dp)
                                .clip(MaterialTheme.shapes.medium)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        ) {
                            AsyncImage(model = selectedImageUri, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                            IconButton(
                                onClick = { selectedImageUri = null; imageBytes = null },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp)
                                    .size(36.dp)
                                    .background(
                                        MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f),
                                        CircleShape
                                    )
                            ) {
                                Icon(
                                    Icons.Default.Clear,
                                    contentDescription = "Remove image",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.inverseOnSurface
                                )
                            }
                        }
                    }
                    Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        FilledTonalIconButton(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            enabled = uiState is ChatUiState.Idle && !isGenerating,
                            modifier = Modifier.size(44.dp),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            )
                        ) {
                            Icon(Icons.Default.Image, contentDescription = "Attach image", modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        val inputPlaceholder = when (uiState) {
                            is ChatUiState.NoModel -> "Download a model to chat…"
                            is ChatUiState.ModelAvailable -> "Load model to chat…"
                            is ChatUiState.Loading -> if (messages.isEmpty()) "Loading model…" else "Heal is responding…"
                            is ChatUiState.Error -> "Fix error in settings…"
                            is ChatUiState.Idle -> "Message Heal…"
                        }
                        TextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            modifier = Modifier
                                .weight(1f)
                                .clip(MaterialTheme.shapes.extraLarge),
                            placeholder = {
                                Text(
                                    inputPlaceholder,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            },
                            shape = MaterialTheme.shapes.extraLarge,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                cursorColor = MaterialTheme.colorScheme.primary,
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
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
                        Spacer(modifier = Modifier.width(12.dp))
                        val isSendEnabled = (uiState is ChatUiState.Idle || isGenerating) &&
                            (inputText.isNotBlank() || imageBytes != null || isGenerating)
                        FilledIconButton(
                            onClick = {
                                if (isGenerating) {
                                    view.performHapticFeedback(HapticFeedbackConstants.REJECT)
                                    viewModel.stopGeneration()
                                } else if (isSendEnabled) {
                                    sendCurrentMessage()
                                }
                            },
                            enabled = isSendEnabled,
                            modifier = Modifier.size(44.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = when {
                                    isGenerating -> MaterialTheme.colorScheme.error
                                    isSendEnabled -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.surfaceContainerHigh
                                },
                                contentColor = when {
                                    isGenerating -> MaterialTheme.colorScheme.onError
                                    isSendEnabled -> MaterialTheme.colorScheme.onPrimary
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) {
                            Icon(
                                imageVector = if (isGenerating) Icons.Default.Stop else Icons.AutoMirrored.Filled.Send,
                                contentDescription = if (isGenerating) "Stop" else "Send",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = with(density) { bottomBarHeightPx.toDp() })
        )

        if (!autoScrollEnabled && messages.isNotEmpty()) {
            SmallFloatingActionButton(
                onClick = {
                    scope.launch {
                        autoScrollEnabled = true
                        listState.animateScrollToBottom(messages.lastIndex)
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(
                        end = 16.dp,
                        bottom = with(density) { bottomBarHeightPx.toDp() } + 16.dp
                    ),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = "Scroll to latest message"
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
    val uiState by viewModel.uiState.collectAsState()
    var tokenInput by remember { mutableStateOf(viewModel.modelManager.hfToken ?: "") }
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
                val llmPath = viewModel.modelManager.getDownloadedLlmPath()
                val mmprojPath = viewModel.modelManager.getDownloadedMmprojPath()
                if (llmPath != null && mmprojPath != null && uiState !is ChatUiState.Idle && uiState !is ChatUiState.Loading) { HealButton(text = "Load model", onClick = { viewModel.initializeEngine() }, modifier = Modifier.width(140.dp)) }
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
        viewModel.modelManager.availableLlmModels.forEach { model -> ModelItem(model = model, isDownloaded = viewModel.modelManager.isModelDownloaded(model.fileName), downloadProgress = downloadProgress[model.fileName], onDownload = { viewModel.downloadModel(model) }); Spacer(modifier = Modifier.height(8.dp)) }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            "VISION COMPONENTS",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(12.dp))
        viewModel.modelManager.availableMmprojModels.forEach { model -> ModelItem(model = model, isDownloaded = viewModel.modelManager.isModelDownloaded(model.fileName), downloadProgress = downloadProgress[model.fileName], onDownload = { viewModel.downloadModel(model) }); Spacer(modifier = Modifier.height(8.dp)) }
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

private fun uriToByteArray(context: android.content.Context, uri: Uri, maxDim: Int = 448): ByteArray? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri)
        val originalBitmap = BitmapFactory.decodeStream(inputStream)
        inputStream?.close()
        if (originalBitmap == null) return null
        val finalBitmap = Bitmap.createScaledBitmap(originalBitmap, maxDim, maxDim, true)
        val pixels = IntArray(maxDim * maxDim)
        finalBitmap.getPixels(pixels, 0, maxDim, 0, 0, maxDim, maxDim)
        val rgbBytes = ByteArray(maxDim * maxDim * 3)
        for (i in 0 until maxDim * maxDim) { val p = pixels[i]; rgbBytes[i * 3 + 0] = ((p shr 16) and 0xFF).toByte(); rgbBytes[i * 3 + 1] = ((p shr 8) and 0xFF).toByte(); rgbBytes[i * 3 + 2] = (p and 0xFF).toByte() }
        if (finalBitmap != originalBitmap) finalBitmap.recycle()
        originalBitmap.recycle()
        rgbBytes
    } catch (e: Exception) { null }
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

private const val SCROLL_BOTTOM_THRESHOLD_PX = 96

private fun LazyListLayoutInfo.isNearBottom(thresholdPx: Int = SCROLL_BOTTOM_THRESHOLD_PX): Boolean {
    if (totalItemsCount == 0) return true
    val lastVisible = visibleItemsInfo.lastOrNull() ?: return false
    return lastVisible.index >= totalItemsCount - 1 &&
        lastVisible.offset + lastVisible.size >= viewportEndOffset - thresholdPx
}

private suspend fun LazyListState.animateScrollToBottom(index: Int) {
    if (index < 0) return
    animateScrollToItem(index, scrollOffset = Int.MAX_VALUE)
}

private suspend fun LazyListState.scrollChatToBottom(index: Int) {
    if (index < 0) return
    scrollToItem(index, scrollOffset = Int.MAX_VALUE)
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
    val alignment = if (message.isUser) Alignment.CenterEnd else Alignment.CenterStart
    val containerColor = if (message.isUser) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val contentColor = if (message.isUser) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val shape = if (message.isUser) {
        RoundedCornerShape(24.dp, 24.dp, 4.dp, 24.dp)
    } else {
        RoundedCornerShape(24.dp, 24.dp, 24.dp, 4.dp)
    }
    var isThoughtExpanded by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = alignment
    ) {
        Box {
            Surface(
                color = containerColor,
                contentColor = contentColor,
                shape = shape,
                tonalElevation = if (message.isUser) 0.dp else 1.dp,
                modifier = Modifier
                    .widthIn(max = 340.dp)
                    .animateContentSize()
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
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
                if (message.imageUri != null) AsyncImage(model = message.imageUri, contentDescription = null, modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp).clip(RoundedCornerShape(12.dp)).padding(bottom = 12.dp), contentScale = androidx.compose.ui.layout.ContentScale.FillWidth)
                if (!message.isUser && message.thought != null) {
                    val thoughtPreview = message.thought.lineSequence().firstOrNull { it.isNotBlank() }
                        ?.take(80)
                        ?.let { if (message.thought.length > 80) "$it…" else it }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            .clickable { isThoughtExpanded = !isThoughtExpanded }
                            .padding(12.dp)
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
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Reasoning",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Icon(
                                    imageVector = if (isThoughtExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = if (isThoughtExpanded) "Collapse reasoning" else "Expand reasoning",
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
                if (message.isUser) Text(text = message.content, style = MaterialTheme.typography.bodyLarge, lineHeight = 24.sp, fontWeight = FontWeight.Medium)
                else {
                    if (message.content.isEmpty()) {
                        if (message.thought == null) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                HeartbeatIndicator()
                            }
                        } else {
                            NeuralPulse(modifier = Modifier.padding(top = 4.dp))
                        }
                    } else {
                        MarkdownText(
                            text = message.content,
                            style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 24.sp),
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
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

@Composable
fun MarkdownText(text: String?, style: androidx.compose.ui.text.TextStyle, modifier: Modifier = Modifier) {
    if (text == null) return
    val lines = remember(text) { text.split("\n") }
    val linkColor = MaterialTheme.colorScheme.primary
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        lines.forEach { line ->
            when {
                line.matches(Regex("^[-*] .+")) -> Row {
                    Text("• ", style = style, modifier = Modifier.padding(end = 2.dp))
                    Text(buildInlineMarkdown(line.drop(2).trim(), style, linkColor), style = style)
                }
                line.matches(Regex("^\\d+\\. .+")) -> Row {
                    val dotIndex = line.indexOf(". ")
                    val prefix = line.substring(0, dotIndex + 1)
                    Text("$prefix ", style = style, modifier = Modifier.padding(end = 2.dp))
                    Text(buildInlineMarkdown(line.substring(dotIndex + 2).trim(), style, linkColor), style = style)
                }
                line.isBlank() -> Spacer(modifier = Modifier.height(4.dp))
                else -> Text(buildInlineMarkdown(line, style, linkColor), style = style)
            }
        }
    }
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
