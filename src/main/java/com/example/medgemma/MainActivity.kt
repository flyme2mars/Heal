package com.example.medgemma

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.medgemma.ui.theme.MedGemmaTheme
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.withStyle
import java.util.regex.Pattern
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.ui.graphics.graphicsLayer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
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
        initialValue = 1f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1000
                1f at 0
                1.4f at 100
                1f at 200
                1.4f at 300
                1f at 400
                1f at 1000
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "scale"
    )

    Icon(
        imageVector = Icons.Default.Favorite,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .size(16.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    )
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
    val listState = rememberLazyListState()

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri -> 
            selectedImageUri = uri 
            if (uri != null) {
                scope.launch {
                    imageBytes = withContext(Dispatchers.IO) {
                        uriToByteArray(context, uri)
                    }
                }
            } else {
                imageBytes = null
            }
        }
    )

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "Heal", 
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
                        val engineStatus = when (uiState) {
                            is ChatUiState.NoModel -> "No Model"
                            is ChatUiState.ModelAvailable -> "Model Downloaded"
                            is ChatUiState.Loading -> "Initializing..."
                            is ChatUiState.Error -> "Engine Error"
                            is ChatUiState.Idle -> "Ready"
                        }
                        Text(
                            engineStatus,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (uiState is ChatUiState.Idle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showModelSheet = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Models")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.primary
                )
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 3.dp,
                modifier = Modifier.navigationBarsPadding()
            ) {
                Column {
                    // Image Preview Area
                    AnimatedVisibility(visible = selectedImageUri != null) {
                        Box(
                            modifier = Modifier
                                .padding(12.dp)
                                .size(100.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            AsyncImage(
                                model = selectedImageUri,
                                contentDescription = "Selected image",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                            )
                            IconButton(
                                onClick = { 
                                    selectedImageUri = null
                                    imageBytes = null
                                },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(24.dp)
                                    .background(
                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                                        CircleShape
                                    )
                                    .padding(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Remove image",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            enabled = uiState is ChatUiState.Idle,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Attach image",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        TextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            modifier = Modifier
                                .weight(1f)
                                .clip(CircleShape),
                            placeholder = { Text("Ask medical questions...") },
                            colors = TextFieldDefaults.colors(
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                disabledIndicatorColor = Color.Transparent
                            ),
                            enabled = uiState is ChatUiState.Idle,
                            maxLines = 4
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        val isSendEnabled = uiState is ChatUiState.Idle && (inputText.isNotBlank() || imageBytes != null)
                        FloatingActionButton(
                            onClick = {
                                if (isSendEnabled) {
                                    viewModel.sendMessage(inputText, imageBytes, selectedImageUri)
                                    inputText = ""
                                    selectedImageUri = null
                                    imageBytes = null
                                }
                            },
                            containerColor = if (isSendEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (isSendEnabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                            modifier = Modifier.size(48.dp),
                            shape = CircleShape,
                            elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Send, 
                                contentDescription = "Send",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                state = listState,
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(messages) { message ->
                    ChatMessageItem(message)
                }
                
                // Show loading indicator only for initialization, not for chat generation
                if (uiState is ChatUiState.Loading && messages.isEmpty()) {
                    item {
                        TypingIndicator((uiState as ChatUiState.Loading).message)
                    }
                }

                if (uiState is ChatUiState.Error) {
                    item {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Text(
                                text = "⚠️ ENGINE ERROR",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.labelMedium
                            )
                            Text(
                                text = (uiState as ChatUiState.Error).message,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { showModelSheet = true },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer)
                            ) {
                                Text("Check Models")
                            }
                        }
                    }
                }

                if (uiState is ChatUiState.NoModel) {
                    item {
                        Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("Model not found", fontWeight = FontWeight.Bold)
                                Text("Download MedGemma to start chatting", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                                Spacer(modifier = Modifier.height(24.dp))
                                Button(onClick = { showModelSheet = true }) {
                                    Text("Open Model Hub")
                                }
                            }
                        }
                    }
                }

                if (uiState is ChatUiState.ModelAvailable) {
                    item {
                        Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("Model downloaded", fontWeight = FontWeight.Bold)
                                Text("Load the model to start chatting", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                                Spacer(modifier = Modifier.height(24.dp))
                                Button(onClick = { viewModel.initializeEngine() }) {
                                    Text("Load MedGemma")
                                }
                                TextButton(onClick = { showModelSheet = true }) {
                                    Text("Manage Models")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showModelSheet) {
        ModalBottomSheet(
            onDismissRequest = { showModelSheet = false },
            sheetState = rememberModalBottomSheetState()
        ) {
            ModelHubContent(viewModel)
        }
    }
}

@Composable
fun ModelHubContent(viewModel: ChatViewModel) {
    val downloadProgress by viewModel.modelManager.downloadProgress.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    var tokenInput by remember { mutableStateOf(viewModel.modelManager.hfToken ?: "") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("Model Hub", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Manage your medical AI brain", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
        
        Spacer(modifier = Modifier.height(24.dp))

        // HF Token Section
        Text("Hugging Face Access Token", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("Required for gated models. Get it at huggingface.co/settings/tokens", style = MaterialTheme.typography.labelSmall)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = tokenInput,
            onValueChange = { 
                tokenInput = it
                viewModel.modelManager.hfToken = it 
            },
            placeholder = { Text("hf_xxxxxxxxxxxxxxxxx") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            trailingIcon = {
                if (tokenInput.isNotBlank()) {
                    IconButton(onClick = { 
                        tokenInput = ""
                        viewModel.modelManager.hfToken = ""
                    }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                    }
                }
            }
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Engine Status Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = when(uiState) {
                    is ChatUiState.Idle -> MaterialTheme.colorScheme.primaryContainer
                    is ChatUiState.Loading -> MaterialTheme.colorScheme.surfaceVariant
                    is ChatUiState.Error -> MaterialTheme.colorScheme.errorContainer
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
            )
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Engine Status", style = MaterialTheme.typography.labelMedium)
                    val statusText = when(uiState) {
                        is ChatUiState.Idle -> "Ready to assist"
                        is ChatUiState.Loading -> "Initializing..."
                        is ChatUiState.Error -> "Initialization failed"
                        is ChatUiState.NoModel -> "Models missing"
                        else -> "Unknown"
                    }
                    Text(statusText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                
                val llmPath = viewModel.modelManager.getDownloadedLlmPath()
                val mmprojPath = viewModel.modelManager.getDownloadedMmprojPath()
                
                if (llmPath != null && mmprojPath != null && uiState !is ChatUiState.Idle && uiState !is ChatUiState.Loading) {
                    Button(onClick = { viewModel.initializeEngine() }) {
                        Text("Load Engine")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text("Select Language Model", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("Reasoning models optimized for medical analysis", style = MaterialTheme.typography.labelSmall)
        Spacer(modifier = Modifier.height(12.dp))

        viewModel.modelManager.availableLlmModels.forEach { model ->
            ModelItem(
                model = model,
                isDownloaded = viewModel.modelManager.isModelDownloaded(model.fileName),
                downloadProgress = downloadProgress[model.fileName],
                onDownload = { viewModel.downloadModel(model) }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("Select Vision Encoder (mmproj)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("Required for medical image processing", style = MaterialTheme.typography.labelSmall)
        Spacer(modifier = Modifier.height(12.dp))

        viewModel.modelManager.availableMmprojModels.forEach { model ->
            ModelItem(
                model = model,
                isDownloaded = viewModel.modelManager.isModelDownloaded(model.fileName),
                downloadProgress = downloadProgress[model.fileName],
                onDownload = { viewModel.downloadModel(model) }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun ModelItem(
    model: GgufModel,
    isDownloaded: Boolean,
    downloadProgress: DownloadProgress?,
    onDownload: () -> Unit
) {
    val isDownloading = downloadProgress?.isDownloading == true

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDownloaded) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f) 
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        border = if (isDownloaded) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)) else null
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(model.name, fontWeight = FontWeight.Bold)
                    Text(model.fileName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }

                if (isDownloaded) {
                    Icon(
                        Icons.Default.CheckCircle, 
                        contentDescription = "Downloaded",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                } else if (isDownloading) {
                    CircularProgressIndicator(
                        progress = downloadProgress?.progress ?: 0f,
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 3.dp
                    )
                } else {
                    IconButton(onClick = onDownload) {
                        Icon(Icons.Default.Download, contentDescription = "Download", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            
            if (isDownloading) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = downloadProgress?.progress ?: 0f,
                    modifier = Modifier.fillMaxWidth().clip(CircleShape),
                )
                Text(
                    "Downloading... ${( (downloadProgress?.progress ?: 0f) * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            
            if (downloadProgress?.error != null) {
                Text(
                    "Error: ${downloadProgress.error}",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

private fun uriToByteArray(
    context: android.content.Context,
    uri: Uri,
    maxDim: Int = 448
): ByteArray? {
    return try {
        val contentResolver = context.contentResolver
        val inputStream = contentResolver.openInputStream(uri)
        val originalBitmap = BitmapFactory.decodeStream(inputStream)
        inputStream?.close()

        if (originalBitmap == null) return null

        // Force exactly 448x448 as required by MedGemma MTMD optimized path
        val finalBitmap = Bitmap.createScaledBitmap(originalBitmap, maxDim, maxDim, true)

        val width = finalBitmap.width
        val height = finalBitmap.height
        val pixels = IntArray(width * height)
        finalBitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        
        val rgbBytes = ByteArray(width * height * 3)
        for (i in 0 until width * height) {
            val p = pixels[i]
            rgbBytes[i * 3 + 0] = ((p shr 16) and 0xFF).toByte() // R
            rgbBytes[i * 3 + 1] = ((p shr 8) and 0xFF).toByte()  // G
            rgbBytes[i * 3 + 2] = (p and 0xFF).toByte()         // B
        }
        
        if (finalBitmap != originalBitmap) finalBitmap.recycle()
        originalBitmap.recycle()
        
        rgbBytes
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

@Composable
fun EKGHeartbeat(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "heartbeat")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    val color = MaterialTheme.colorScheme.primary
    
    Canvas(modifier = modifier.height(24.dp).width(60.dp)) {
        val width = size.width
        val height = size.height
        val centerY = height / 2
        val path = Path()
        
        path.moveTo(0f, centerY)
        
        // Simple EKG shape: flat -> spike up -> spike down -> flat
        val segmentWidth = width / 5
        path.lineTo(segmentWidth * 1.5f, centerY)
        path.lineTo(segmentWidth * 1.8f, centerY - height * 0.4f)
        path.lineTo(segmentWidth * 2.2f, centerY + height * 0.4f)
        path.lineTo(segmentWidth * 2.5f, centerY)
        path.lineTo(width, centerY)

        drawPath(
            path = path,
            color = color.copy(alpha = 0.3f),
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )

        // Animated overlay
        val scanX = phase * width
        val clipPath = Path().apply {
            addRect(androidx.compose.ui.geometry.Rect(0f, 0f, scanX, height))
        }
        
        // We can't easily clip path in Canvas without native canvas, so we'll just pulse the whole path for now
        // Or simpler: Pulse alpha
    }
}

@Composable
fun NeuralPulse(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    this.alpha = alpha
                }
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
        )
        Text(
            "Working",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun ChatMessageItem(message: ChatMessage) {
    val alignment = if (message.isUser) Alignment.CenterEnd else Alignment.CenterStart
    val containerColor = if (message.isUser) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }
    val contentColor = if (message.isUser) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }
    
    val shape = if (message.isUser) {
        RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp)
    } else {
        RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp)
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
                .widthIn(max = 320.dp)
                .animateContentSize()
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                // Sent Image (if present)
                if (message.imageUri != null) {
                    AsyncImage(
                        model = message.imageUri,
                        contentDescription = "Sent image",
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .padding(bottom = 12.dp),
                        contentScale = androidx.compose.ui.layout.ContentScale.FillWidth
                    )
                }

                // Thought section (if present)
                if (!message.isUser && message.thought != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .clickable { isThoughtExpanded = !isThoughtExpanded }
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "🧠",
                                        fontSize = 14.sp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Thinking",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Icon(
                                    imageVector = if (isThoughtExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            
                            AnimatedVisibility(visible = isThoughtExpanded) {
                                Column {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Divider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f))
                                    Spacer(modifier = Modifier.height(8.dp))
                                    MarkdownText(
                                        text = message.thought,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontSize = 13.sp,
                                            lineHeight = 18.sp
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }

                // Main content
                if (message.isUser) {
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyLarge,
                        lineHeight = 22.sp
                    )
                } else {
                    if (message.content.isEmpty()) {
                        if (message.thought == null) {
                            // Initial prefill / Loading state
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "Consulting MedGemma...",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                                // Creative EKG-like pulse
                                HeartbeatIndicator()
                            }
                        } else {
                            // Generating thinking but no content yet
                            NeuralPulse(modifier = Modifier.padding(top = 4.dp))
                        }
                    } else {
                        MarkdownText(
                            text = message.content,
                            style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 22.sp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                
                if (message.stats != null && !message.isUser) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "⚡",
                                fontSize = 10.sp
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = message.stats,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MarkdownText(
    text: String?,
    style: androidx.compose.ui.text.TextStyle,
    modifier: Modifier = Modifier
) {
    if (text == null) return

    val annotatedString = remember(text) {
        buildAnnotatedString {
            var lastIndex = 0
            try {
                val combinedPattern = Pattern.compile("(\\*\\*.*?\\*\\*|\\*.*?\\*)", Pattern.DOTALL)
                val matcher = combinedPattern.matcher(text)
                
                while (matcher.find()) {
                    append(text.substring(lastIndex, matcher.start()))
                    val match = matcher.group()
                    
                    if (match.length >= 4 && match.startsWith("**") && match.endsWith("**")) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(match.substring(2, match.length - 2))
                        }
                    } else if (match.length >= 2 && match.startsWith("*") && match.endsWith("*")) {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                            append(match.substring(1, match.length - 1))
                        }
                    } else {
                        append(match)
                    }
                    lastIndex = matcher.end()
                }
            } catch (e: Exception) {
                // Fallback for any regex/substring issues
            }
            if (lastIndex < text.length) {
                append(text.substring(lastIndex))
            }
        }
    }
    
    Text(
        text = annotatedString,
        style = style,
        modifier = modifier
    )
}

@Composable
fun TypingIndicator(message: String) {
    Row(
        modifier = Modifier
            .padding(vertical = 8.dp)
            .background(
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp)
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            message, 
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )
        CircularProgressIndicator(
            modifier = Modifier.size(12.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.primary
        )
    }
}
