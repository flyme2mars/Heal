package com.example.medgemma

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.items
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
import androidx.core.view.WindowCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            MedGemmaTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) { ChatScreen() }
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
        topBar = {
            Column(modifier = Modifier.background(Color.Black.copy(alpha = 0.9f)).statusBarsPadding()) {
                CenterAlignedTopAppBar(
                    navigationIcon = {
                        IconButton(onClick = { showModelSheet = true }, enabled = !isGenerating) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White)
                        }
                    },
                    title = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Heal", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge, color = Color.White)
                            val engineStatus = when (uiState) {
                                is ChatUiState.NoModel -> "Offline"
                                is ChatUiState.ModelAvailable -> "Ready"
                                is ChatUiState.Loading -> "Loading"
                                is ChatUiState.Error -> "Error"
                                is ChatUiState.Idle -> "Ready"
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(if (uiState is ChatUiState.Idle) MaterialTheme.colorScheme.primary else Color.Gray))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(engineStatus, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.clearMessages() }, enabled = messages.isNotEmpty() && !isGenerating) {
                            Icon(Icons.Default.Add, contentDescription = "New Chat", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent, titleContentColor = Color.White)
                )
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(brush = Brush.horizontalGradient(colors = listOf(Color.Transparent, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), Color.Transparent))))
            }
        },
        bottomBar = {
            Surface(color = Color.Black, modifier = Modifier.imePadding().navigationBarsPadding()) {
                Column {
                    Divider(color = Color.White.copy(alpha = 0.05f))
                    AnimatedVisibility(visible = selectedImageUri != null) {
                        Box(modifier = Modifier.padding(16.dp).size(80.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF1A1A1A))) {
                            AsyncImage(model = selectedImageUri, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                            IconButton(onClick = { selectedImageUri = null; imageBytes = null }, modifier = Modifier.align(Alignment.TopEnd).size(20.dp).background(Color.Black.copy(alpha = 0.6f), CircleShape)) {
                                Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.White)
                            }
                        }
                    }
                    Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, enabled = uiState is ChatUiState.Idle && !isGenerating, modifier = Modifier.size(40.dp).clip(CircleShape).background(Color(0xFF1A1A1A))) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        TextField(value = inputText, onValueChange = { inputText = it }, modifier = Modifier.weight(1f).clip(RoundedCornerShape(24.dp)), placeholder = { Text("Message Heal...", color = Color.Gray, style = MaterialTheme.typography.bodyMedium) },
                            colors = TextFieldDefaults.colors(focusedContainerColor = Color(0xFF1A1A1A), unfocusedContainerColor = Color(0xFF1A1A1A), disabledContainerColor = Color(0xFF0A0A0A), focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, cursorColor = MaterialTheme.colorScheme.primary, focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                            enabled = uiState is ChatUiState.Idle && !isGenerating, maxLines = 5)
                        Spacer(modifier = Modifier.width(12.dp))
                        val isSendEnabled = (uiState is ChatUiState.Idle || isGenerating) && (inputText.isNotBlank() || imageBytes != null || isGenerating)
                        IconButton(onClick = { if (isGenerating) viewModel.stopGeneration() else if (isSendEnabled) { viewModel.sendMessage(inputText, imageBytes, selectedImageUri); inputText = ""; selectedImageUri = null; imageBytes = null } }, enabled = isSendEnabled, modifier = Modifier.size(40.dp).clip(CircleShape).background(if (isSendEnabled) Color.White else Color(0xFF1A1A1A))) {
                            Icon(imageVector = if (isGenerating) Icons.Default.Stop else Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp), tint = if (isSendEnabled) MaterialTheme.colorScheme.primary else Color.Gray)
                        }
                    }
                }
            }
        },
        containerColor = Color.Black
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize(), state = listState, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            items(messages) { ChatMessageItem(it) }
            if (uiState is ChatUiState.Loading && messages.isEmpty()) { item { Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) { TypingIndicator((uiState as ChatUiState.Loading).message) } } }
            if (uiState is ChatUiState.Error) { item { ErrorState((uiState as ChatUiState.Error).message) { showModelSheet = true } } }
            if (uiState is ChatUiState.NoModel) { item { EmptyState(icon = Icons.Default.Download, title = "Model Required", subtitle = "Select a model to begin chatting.", actionText = "Open Settings", onAction = { showModelSheet = true }) } }
            if (uiState is ChatUiState.ModelAvailable) { item { EmptyState(icon = Icons.Default.CheckCircle, title = "Ready to start", subtitle = "Models are downloaded. Start chatting now.", actionText = "Load model", onAction = { viewModel.initializeEngine() }) } }
        }
    }
    if (showModelSheet) { ModalBottomSheet(onDismissRequest = { showModelSheet = false }, sheetState = rememberModalBottomSheetState(), containerColor = Color(0xFF0A0A0A), contentColor = Color.White, dragHandle = { BottomSheetDefaults.DragHandle(color = Color.DarkGray) }) { ModelHubContent(viewModel) } }
}

@Composable
fun LazyItemScope.EmptyState(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, actionText: String, onAction: () -> Unit) {
    Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Box(modifier = Modifier.size(80.dp).clip(CircleShape).background(Color(0xFF1A1A1A)), contentAlignment = Alignment.Center) { Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(32.dp), tint = Color.White.copy(alpha = 0.5f)) }
            Spacer(modifier = Modifier.height(24.dp))
            Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge, color = Color.White)
            Spacer(modifier = Modifier.height(8.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Color.Gray, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(32.dp))
            HealButton(text = actionText, onClick = onAction, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
fun HealButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, isPrimary: Boolean = true) {
    Surface(onClick = onClick, modifier = modifier.height(48.dp), color = if (isPrimary) Color(0xFF222222) else Color.Transparent, shape = RoundedCornerShape(12.dp), border = if (isPrimary) androidx.compose.foundation.BorderStroke(width = 1.dp, color = Color.White.copy(alpha = 0.1f)) else null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(text = text, color = if (isPrimary) Color.White else Color.Gray, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp) }
    }
}

@Composable
fun ErrorState(message: String, onCheck: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF1A0000)), shape = RoundedCornerShape(16.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF3D0000))) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Clear, contentDescription = null, tint = Color.Red, modifier = Modifier.size(16.dp)); Spacer(modifier = Modifier.width(8.dp)); Text("Error", color = Color.Red, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold) }
            Spacer(modifier = Modifier.height(8.dp)); Text(message, color = Color.Gray, style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(16.dp)); HealButton(text = "Settings", onClick = onCheck, isPrimary = false, modifier = Modifier.fillMaxWidth())
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
        Text("Manage your models", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
        Spacer(modifier = Modifier.height(32.dp))
        Text("Token", style = MaterialTheme.typography.labelLarge, color = Color.Gray, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(value = tokenInput, onValueChange = { tokenInput = it; viewModel.modelManager.hfToken = it }, placeholder = { Text("hf_...", color = Color.DarkGray) }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color.White, unfocusedBorderColor = Color.DarkGray, cursorColor = Color.White))
        Spacer(modifier = Modifier.height(32.dp))
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A))) {
            Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("STATUS", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    val statusText = when(uiState) { is ChatUiState.Idle -> "Ready"; is ChatUiState.Loading -> "Loading"; is ChatUiState.Error -> "Error"; is ChatUiState.NoModel -> "No model"; else -> "Ready" }
                    Text(statusText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                }
                val llmPath = viewModel.modelManager.getDownloadedLlmPath()
                val mmprojPath = viewModel.modelManager.getDownloadedMmprojPath()
                if (llmPath != null && mmprojPath != null && uiState !is ChatUiState.Idle && uiState !is ChatUiState.Loading) { HealButton(text = "Load model", onClick = { viewModel.initializeEngine() }, modifier = Modifier.width(140.dp)) }
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
        Text("LANGUAGE MODELS", style = MaterialTheme.typography.labelLarge, color = Color.Gray, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))
        viewModel.modelManager.availableLlmModels.forEach { model -> ModelItem(model = model, isDownloaded = viewModel.modelManager.isModelDownloaded(model.fileName), downloadProgress = downloadProgress[model.fileName], onDownload = { viewModel.downloadModel(model) }); Spacer(modifier = Modifier.height(8.dp)) }
        Spacer(modifier = Modifier.height(24.dp))
        Text("VISION COMPONENTS", style = MaterialTheme.typography.labelLarge, color = Color.Gray, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))
        viewModel.modelManager.availableMmprojModels.forEach { model -> ModelItem(model = model, isDownloaded = viewModel.modelManager.isModelDownloaded(model.fileName), downloadProgress = downloadProgress[model.fileName], onDownload = { viewModel.downloadModel(model) }); Spacer(modifier = Modifier.height(8.dp)) }
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun ModelItem(model: GgufModel, isDownloaded: Boolean, downloadProgress: DownloadProgress?, onDownload: () -> Unit) {
    val isDownloading = downloadProgress?.isDownloading == true
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = if (isDownloaded) Color(0xFF1A1A1A) else Color(0xFF0A0A0A)), border = if (isDownloaded) androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)) else null) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) { Text(model.name, fontWeight = FontWeight.Bold, color = Color.White); Text(model.fileName, style = MaterialTheme.typography.labelSmall, color = Color.Gray) }
                if (isDownloaded) Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                else if (isDownloading) CircularProgressIndicator(progress = downloadProgress?.progress ?: 0f, modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                else IconButton(onClick = onDownload, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.Download, contentDescription = null, tint = Color.Gray) }
            }
            if (isDownloading) { Spacer(modifier = Modifier.height(12.dp)); LinearProgressIndicator(progress = downloadProgress?.progress ?: 0f, modifier = Modifier.fillMaxWidth().height(2.dp).clip(CircleShape), color = Color.White, trackColor = Color.DarkGray) }
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
fun ChatMessageItem(message: ChatMessage) {
    val alignment = if (message.isUser) Alignment.CenterEnd else Alignment.CenterStart
    val containerColor = if (message.isUser) Color.White else Color(0xFF0F0F0F)
    val contentColor = if (message.isUser) Color.Black else Color(0xFFE5E5E5)
    val shape = if (message.isUser) RoundedCornerShape(24.dp, 24.dp, 4.dp, 24.dp) else RoundedCornerShape(24.dp, 24.dp, 24.dp, 4.dp)
    var isThoughtExpanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = alignment) {
        Surface(color = containerColor, contentColor = contentColor, shape = shape, modifier = Modifier.widthIn(max = 300.dp).animateContentSize()) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
                if (message.imageUri != null) AsyncImage(model = message.imageUri, contentDescription = null, modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp).clip(RoundedCornerShape(12.dp)).padding(bottom = 12.dp), contentScale = androidx.compose.ui.layout.ContentScale.FillWidth)
                if (!message.isUser && message.thought != null) {
                    Box(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.05f)).clickable { isThoughtExpanded = !isThoughtExpanded }.padding(12.dp)) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text(text = "Thought", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f))
                                Icon(imageVector = if (isThoughtExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.Gray)
                            }
                            AnimatedVisibility(visible = isThoughtExpanded) { Column { Spacer(modifier = Modifier.height(8.dp)); MarkdownText(text = message.thought, style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray, fontSize = 12.sp, lineHeight = 16.sp), modifier = Modifier.fillMaxWidth()) } }
                        }
                    }
                }
                if (message.isUser) Text(text = message.content, style = MaterialTheme.typography.bodyLarge, lineHeight = 24.sp, fontWeight = FontWeight.Medium)
                else { if (message.content.isEmpty()) { if (message.thought == null) Column(horizontalAlignment = Alignment.CenterHorizontally) { HeartbeatIndicator() } else NeuralPulse(modifier = Modifier.padding(top = 4.dp)) } else MarkdownText(text = message.content, style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 24.sp), modifier = Modifier.fillMaxWidth()) }
                if (message.stats != null && !message.isUser) { Spacer(modifier = Modifier.height(12.dp)); Text(text = message.stats.uppercase(), style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = Color.DarkGray, letterSpacing = 1.sp) }
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
                    if (match.startsWith("**")) withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Color.White)) { append(match.substring(2, match.length - 2)) }
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
        Text(message.uppercase(), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.5f), letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = Color.White)
    }
}
