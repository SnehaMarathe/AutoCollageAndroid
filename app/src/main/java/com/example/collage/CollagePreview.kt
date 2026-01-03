package com.example.collage

import android.net.Uri
import android.view.Surface
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CollagePreview(
    template: CollageTemplate,
    slotUris: List<Uri?>,
    slotTransforms: List<SlotTransform>,
    spacingPx: Float,
    cornerRadiusPx: Float,
    vm: CollageViewModel,
    activeCameraSlot: Int,
    onSlotTap: (Int) -> Unit,
    onSlotLongPress: (Int) -> Unit,
    onTransformChange: (Int, SlotTransform) -> Unit,
    onCameraCaptured: (Int, Uri) -> Unit,
    onCameraCancel: () -> Unit
) {
    val context = LocalContext.current

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
    ) {
        val density = LocalDensity.current
        val wPx = with(density) { maxWidth.toPx() }
        val hPx = with(density) { maxHeight.toPx() }

        val spacing = spacingPx.coerceAtLeast(0f)
        val radiusDp = with(density) { (cornerRadiusPx / density.density).dp }

        template.slots.forEachIndexed { idx, r ->
            val left = r.x * wPx + spacing / 2f
            val top = r.y * hPx + spacing / 2f
            val slotW = r.w * wPx - spacing
            val slotH = r.h * hPx - spacing
            val slotAspect = if (slotH > 0f) slotW / slotH else 1f

            val uri = slotUris.getOrNull(idx)
            val currentT = slotTransforms.getOrNull(idx) ?: SlotTransform()

            var thumb by remember(uri) { mutableStateOf<ImageBitmap?>(null) }

            LaunchedEffect(uri) {
                thumb = null
                if (uri == null) return@LaunchedEffect
                vm.getCachedThumb(uri)?.let { thumb = it; return@LaunchedEffect }
                val loaded = ThumbnailLoader.loadThumbnail(context, uri, maxSizePx = 1024)
                if (loaded != null) vm.putCachedThumb(uri, loaded)
                thumb = loaded
            }

            val transformState = rememberTransformableState { zoomChange, panChange, _ ->
                val newScale = (currentT.scale * zoomChange).coerceIn(1f, 4f)
                val nx = if (slotW > 0f) currentT.offsetX + (panChange.x / slotW) * 2f else currentT.offsetX
                val ny = if (slotH > 0f) currentT.offsetY + (panChange.y / slotH) * 2f else currentT.offsetY

                onTransformChange(
                    idx,
                    SlotTransform(
                        scale = newScale,
                        offsetX = nx.coerceIn(-1f, 1f),
                        offsetY = ny.coerceIn(-1f, 1f)
                    )
                )
            }

            Box(
                modifier = Modifier
                    .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
                    .width(with(density) { slotW.toDp() })
                    .height(with(density) { slotH.toDp() })
                    .clip(RoundedCornerShape(radiusDp))
                    .background(MaterialTheme.colorScheme.surface)
                    .combinedClickable(
                        onClick = { onSlotTap(idx) },
                        onLongClick = { onSlotLongPress(idx) }
                    ),
                contentAlignment = Alignment.Center
            ) {
                when {
                    idx == activeCameraSlot -> {
                        CameraSlot(
                            modifier = Modifier.fillMaxSize(),
                            slotAspect = slotAspect,
                            onUse = { capturedUri -> onCameraCaptured(idx, capturedUri) },
                            onCancel = onCameraCancel
                        )
                    }

                    uri != null && thumb != null -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .transformable(state = transformState)
                        ) {
                            Image(
                                bitmap = thumb!!,
                                contentDescription = "Slot photo",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer(
                                        scaleX = currentT.scale,
                                        scaleY = currentT.scale,
                                        translationX = (currentT.offsetX / 2f) * slotW,
                                        translationY = (currentT.offsetY / 2f) * slotH
                                    ),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }

                    else -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("+", style = MaterialTheme.typography.displaySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Camera", style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CameraSlot(
    modifier: Modifier = Modifier,
    slotAspect: Float,
    onUse: (Uri) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var capturedUri by remember { mutableStateOf<Uri?>(null) }
    var capturedThumb by remember { mutableStateOf<ImageBitmap?>(null) }
    var isBound by remember { mutableStateOf(false) }

    // We keep PreviewView stable across recompositions
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    // Bind camera whenever slot aspect changes (different slot)
    LaunchedEffect(slotAspect) {
        val provider = ProcessCameraProvider.getInstance(context).get()
        val aspect = CameraAspect.closestCameraXAspect(slotAspect)
        val rotation = previewView.display?.rotation ?: Surface.ROTATION_0

        val preview = Preview.Builder()
            .setTargetAspectRatio(aspect)
            .setTargetRotation(rotation)
            .build()
            .also { it.setSurfaceProvider(previewView.surfaceProvider) }

        val capture = ImageCapture.Builder()
            .setTargetAspectRatio(aspect)
            .setTargetRotation(rotation)
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()

        imageCapture = capture
        capturedUri = null
        capturedThumb = null

        try {
            provider.unbindAll()
            provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
            isBound = true
        } catch (_: Exception) {
            isBound = false
        }
    }

    Box(modifier = modifier) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize(),
            update = {
                val rot = it.display?.rotation ?: Surface.ROTATION_0
                imageCapture?.targetRotation = rot
            }
        )

        // Confirm overlay when captured
        if (capturedThumb != null) {
            Image(
                bitmap = capturedThumb!!,
                contentDescription = "Captured",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        // Safe frame
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.Transparent)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(14.dp)
                )
        )

        // Bottom controls: Instagram-like capture button
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onCancel) { Text("Cancel") }

            if (capturedUri == null) {
                CaptureButton(
                    enabled = isBound,
                    onClick = {
                        val cap = imageCapture ?: return@CaptureButton
                        val file = File(context.cacheDir, "slot_capture_${System.currentTimeMillis()}.jpg")

                        val output = ImageCapture.OutputFileOptions.Builder(file).build()
                        cap.takePicture(
                            output,
                            ContextCompat.getMainExecutor(context),
                            object : ImageCapture.OnImageSavedCallback {
                                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                    val contentUri = FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        file
                                    )
                                    capturedUri = contentUri
                                }
                                override fun onError(exception: ImageCaptureException) {
                                    // user can retry
                                }
                            }
                        )
                    }
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { capturedUri = null; capturedThumb = null }) { Text("Retake") }
                    Button(onClick = { onUse(capturedUri!!) }) { Text("Use") }
                }
            }

            Spacer(modifier = Modifier.width(64.dp))
        }
    }

    LaunchedEffect(capturedUri) {
        val u = capturedUri ?: return@LaunchedEffect
        capturedThumb = ThumbnailLoader.loadThumbnail(context, u, 1600)
    }
}

@Composable
private fun CaptureButton(enabled: Boolean, onClick: () -> Unit) {
    // Instagram-ish: outer ring + inner filled circle
    Box(
        modifier = Modifier.size(64.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.size(64.dp),
            shape = RoundedCornerShape(100),
            color = Color.White.copy(alpha = if (enabled) 0.95f else 0.5f),
            tonalElevation = 2.dp,
            shadowElevation = 6.dp,
            onClick = onClick,
            enabled = enabled
        ) {}
        Surface(
            modifier = Modifier.size(50.dp),
            shape = RoundedCornerShape(100),
            color = Color.White.copy(alpha = if (enabled) 1f else 0.6f)
        ) {}
    }
}
