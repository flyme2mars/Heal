package com.example.medgemma

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.medgemma.ui.theme.MedGemmaTheme
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import java.util.regex.Pattern
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle

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
    Icon(imageVector = Icons.Default.Favorite, contentDescription = null, tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(18.dp).graphicsLayer { scaleX = scale; scaleY = scale })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(viewModel: ChatViewModel = viewModel()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var inputText by remember { mutableStateOf("") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var imageBytes by remember { mutableStateOf<ByteArray?>(null) }
    var showModelSheet by remember { mutableStateOf(false) }
    val messages = viewModel.messages
    val uiState by viewModel.uiState.collectAsState()
    val isGenerating by viewModel.isGenerating.collectAsState()
    val listState = rememberLazyListState()

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri -> selectedImageUri = uri; if (uri != null) { scope.launch { imageBytes = withContext(Dispatchers.IO) { uriToByteArray(context, uri) } } } else { imageBytes = null } }
    )

    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Column(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.92f))
                    .statusBarsPadding()
            ) {
                CenterAlignedTopAppBar(
                    navigationIcon = {
                        IconButton(onClick = { showModelSheet = true }, enabled = !isGenerating) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = "Settings",
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
                            val engineStatus = when (uiState) {
                                is ChatUiState.NoModel -> "Offline"
                                is ChatUiState.ModelAvailable -> "Ready"
                                is ChatUiState.Loading -> "Loading"
                                is ChatUiState.Error -> "Error"
                                is ChatUiState.Idle -> "Ready"
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
                            onClick = { viewModel.clearMessages() },
                            enabled = messages.isNotEmpty() && !isGenerating
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = "New Chat",
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
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                )
            }
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.background,
                modifier = Modifier.imePadding().navigationBarsPadding()
            ) {
                Column {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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
                                    .size(20.dp)
                                    .background(
                                        MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f),
                                        CircleShape
                                    )
                            ) {
                                Icon(
                                    Icons.Default.Clear,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
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
                            Icon(Icons.Default.Add, contentDescription = "Add image", modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        TextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            modifier = Modifier.weight(1f).clip(MaterialTheme.shapes.extraLarge),
                            placeholder = {
                                Text(
                                    "Message Heal...",
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
                            enabled = uiState is ChatUiState.Idle && !isGenerating,
                            maxLines = 5
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        val isSendEnabled = (uiState is ChatUiState.Idle || isGenerating) &&
                            (inputText.isNotBlank() || imageBytes != null || isGenerating)
                        FilledIconButton(
                            onClick = {
                                if (isGenerating) viewModel.stopGeneration()
                                else if (isSendEnabled) {
                                    viewModel.sendMessage(inputText, imageBytes, selectedImageUri)
                                    inputText = ""
                                    selectedImageUri = null
                                    imageBytes = null
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
                                imageVector = if (isGenerating) Icons.Default.Stop else Icons.Default.Send,
                                contentDescription = if (isGenerating) "Stop" else "Send",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .consumeWindowInsets(padding),
            state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
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
            if (uiState is ChatUiState.Loading && messages.isEmpty()) { item { Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) { TypingIndicator((uiState as ChatUiState.Loading).message) } } }
            if (uiState is ChatUiState.Error) { item { ErrorState((uiState as ChatUiState.Error).message) { showModelSheet = true } } }
            if (uiState is ChatUiState.NoModel) { item { EmptyState(icon = Icons.Default.Download, title = "Model Required", subtitle = "Select a model to begin chatting.", actionText = "Open Settings", onAction = { showModelSheet = true }) } }
            if (uiState is ChatUiState.ModelAvailable) { item { EmptyState(icon = Icons.Default.CheckCircle, title = "Ready to start", subtitle = "Models are downloaded. Start chatting now.", actionText = "Load model", onAction = { viewModel.initializeEngine() }) } }
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
        Text("Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "Manage your models",
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

@Composable
fun ChatMessageItem(message: ChatMessage, isStreaming: Boolean = false) {
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
        Surface(
            color = containerColor,
            contentColor = contentColor,
            shape = shape,
            tonalElevation = if (message.isUser) 0.dp else 1.dp,
            modifier = Modifier
                .widthIn(max = 340.dp)
                .animateContentSize()
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
                if (message.imageUri != null) AsyncImage(model = message.imageUri, contentDescription = null, modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp).clip(RoundedCornerShape(12.dp)).padding(bottom = 12.dp), contentScale = androidx.compose.ui.layout.ContentScale.FillWidth)
                if (!message.isUser && message.thought != null) {
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
                                Text(
                                    text = "Thought",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Icon(
                                    imageVector = if (isThoughtExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
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
                    } else if (isStreaming) {
                        Text(
                            text = message.content,
                            style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 24.sp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        MarkdownText(
                            text = message.content,
                            style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 24.sp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                if (message.stats != null && !message.isUser) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = message.stats.uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}

@Composable
fun MarkdownText(text: String?, style: androidx.compose.ui.text.TextStyle, modifier: Modifier = Modifier) {
    if (text == null) return
    val annotatedString = remember(text) {
        buildAnnotatedString {
            var lastIndex = 0
            try {
                val pattern = Pattern.compile("(\\*\\*.*?\\*\\*|\\*.*?\\*)", Pattern.DOTALL); val matcher = pattern.matcher(text)
                while (matcher.find()) {
                    append(text.substring(lastIndex, matcher.start()))
                    val match = matcher.group()
                    if (match.startsWith("**")) withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = style.color)) { append(match.substring(2, match.length - 2)) }
                    else withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(match.substring(1, match.length - 1)) }
                    lastIndex = matcher.end()
                }
            } catch (e: Exception) {}
            if (lastIndex < text.length) append(text.substring(lastIndex))
        }
    }
    Text(text = annotatedString, style = style, modifier = modifier)
}

@Composable
fun TypingIndicator(message: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            message.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 2.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))
        CircularProgressIndicator(
            modifier = Modifier.size(24.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.primary
        )
    }
}
