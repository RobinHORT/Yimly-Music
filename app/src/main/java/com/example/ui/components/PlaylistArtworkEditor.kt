package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistArtworkEditor(
    playlistId: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var bitmap by remember { mutableStateOf<ImageBitmap?>(null) }

    val launcher = rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri: Uri? ->
        selectedImageUri = uri
        uri?.let {
            coroutineScope.launch {
                val decoded = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(it)?.use { stream ->
                        BitmapFactory.decodeStream(stream)
                    }
                }
                bitmap = decoded?.asImageBitmap()
                // Reset transform
                scale = 1f
                offset = Offset.Zero
            }
        }
    }

    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    
    var isSaving by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Artwork") },
                navigationIcon = {
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Cancel") }
                },
                actions = {
                    if (bitmap != null && !isSaving) {
                        IconButton(onClick = {
                            isSaving = true
                            coroutineScope.launch(Dispatchers.IO) {
                                val resultBitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
                                val canvas = android.graphics.Canvas(resultBitmap)
                                canvas.drawColor(android.graphics.Color.BLACK)
                                
                                val sourceBitmap = bitmap!!.asAndroidBitmap()
                                // Calculate how the image is drawn in the container
                                val boxSize = containerSize.width.toFloat()
                                if (boxSize > 0) {
                                    // Scale to match the 512x512 final size
                                    val drawScale = 512f / boxSize
                                    
                                    canvas.scale(drawScale, drawScale)
                                    
                                    val imageAspectRatio = sourceBitmap.width.toFloat() / sourceBitmap.height.toFloat()
                                    val canvasAspectRatio = 1f
                                    
                                    var drawWidth = boxSize
                                    var drawHeight = boxSize
                                    var startX = 0f
                                    var startY = 0f
                                    
                                    if (imageAspectRatio > canvasAspectRatio) {
                                        drawHeight = boxSize
                                        drawWidth = drawHeight * imageAspectRatio
                                        startX = (boxSize - drawWidth) / 2f
                                    } else {
                                        drawWidth = boxSize
                                        drawHeight = drawWidth / imageAspectRatio
                                        startY = (boxSize - drawHeight) / 2f
                                    }
                                    
                                    canvas.translate(boxSize / 2f, boxSize / 2f)
                                    canvas.scale(scale, scale)
                                    canvas.translate(-boxSize / 2f + offset.x, -boxSize / 2f + offset.y)
                                    
                                    val srcRect = android.graphics.Rect(0, 0, sourceBitmap.width, sourceBitmap.height)
                                    val dstRect = android.graphics.RectF(startX, startY, startX + drawWidth, startY + drawHeight)
                                    canvas.drawBitmap(sourceBitmap, srcRect, dstRect, null)
                                }
                                
                                val file = File(context.filesDir, "playlist_cover_${playlistId}_${System.currentTimeMillis()}.jpg")
                                FileOutputStream(file).use { out ->
                                    resultBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                                }
                                val uri = file.absolutePath
                                
                                withContext(Dispatchers.Main) {
                                    isSaving = false
                                    onSave("file://$uri")
                                }
                            }
                        }) {
                            Icon(Icons.Default.Done, contentDescription = "Save")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .background(Color.Black)
                    .onSizeChanged { containerSize = it }
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(0.5f, 5f)
                            offset += pan
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                bitmap?.let { img ->
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val canvasWidth = size.width
                        val canvasHeight = size.height
                        
                        val imageAspectRatio = img.width.toFloat() / img.height.toFloat()
                        val viewAspectRatio = canvasWidth / canvasHeight
                        
                        var drawWidth = canvasWidth
                        var drawHeight = canvasHeight
                        var startX = 0f
                        var startY = 0f
                        
                        if (imageAspectRatio > viewAspectRatio) {
                            drawHeight = canvasHeight
                            drawWidth = drawHeight * imageAspectRatio
                            startX = (canvasWidth - drawWidth) / 2f
                        } else {
                            drawWidth = canvasWidth
                            drawHeight = drawWidth / imageAspectRatio
                            startY = (canvasHeight - drawHeight) / 2f
                        }
                        
                        androidx.compose.ui.graphics.drawscope.withTransform({
                            translate(offset.x, offset.y)
                            scale(scale, scale, pivot = Offset(canvasWidth / 2f, canvasHeight / 2f))
                        }) {
                            drawImage(
                                image = img,
                                dstSize = IntSize(drawWidth.toInt(), drawHeight.toInt()),
                                dstOffset = androidx.compose.ui.unit.IntOffset(startX.toInt(), startY.toInt())
                            )
                        }
                    }
                } ?: run {
                    Text("No image selected", color = Color.White)
                }
                
                // Crop overlay guides
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f))
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Transparent)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val strokeWidth = 2.dp.toPx()
                        drawRect(
                            color = Color.White,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(onClick = { launcher.launch("image/*") }, modifier = Modifier.testTag("upload_image_button")) {
                Text(if (bitmap == null) "Upload Image" else "Change Image")
            }
            if (bitmap != null) {
                Text("Drag to reposition, pinch to zoom", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
            }
            if (isSaving) {
                CircularProgressIndicator(modifier = Modifier.padding(top = 16.dp))
            }
        }
    }
}
