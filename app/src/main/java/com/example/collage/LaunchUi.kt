package com.example.collage

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yalantis.ucrop.UCrop
import kotlinx.coroutines.launch

/**
 * V1 Launch UI Pack: Home -> Editor with bottom tabs and slot action sheet.
 * This file is intentionally self-contained and conservative to ensure CI compilation.
 */
private enum class Screen { HOME, EDITOR }
private enum class EditorTab { TEMPLATES, ADJUST, BACKGROUND, EXPORT }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LaunchUiRoot(vm: CollageViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var screen by remember { mutableStateOf(Screen.HOME) }
    var tab by remember { mutableStateOf(EditorTab.TEMPLATES) }

    var activeSlot by remember { mutableIntStateOf(-1) }
    var activeCameraSlot by remember { mutableIntStateOf(-1) }

    // slot action sheet
    var slotSheetFor by remember { mutableIntStateOf(-1) }

    // adjust sheet toggle (optional)
    var showAdjustSheet by remember { mutableStateOf(false) }

    // Crop launcher
    val cropLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { res ->
        when (res.resultCode) {
            Activity.RESULT_OK -> {
                val out = UCrop.getOutput(res.data!!)
                if (out != null && activeSlot >= 0) vm.setSlotUri(activeSlot, out)
                else scope.launch { snackbar.showSnackbar("Crop output missing") }
            }
            UCrop.RESULT_ERROR -> {
                val err = UCrop.getError(res.data!!)
                scope.launch { snackbar.showSnackbar("Crop failed: ${err?.message ?: "unknown"}") }
            }
            else -> { /* cancelled */ }
        }
        if (activeSlot >= 0) vm.clearDraftCapture(activeSlot)
        activeSlot = -1
        activeCameraSlot = -1
        slotSheetFor = -1
    }

    fun launchCrop(source: Uri) {
        cropLauncher.launch(UCropHelper.buildIntent(context, source))
    }

    // Gallery picker
    val galleryPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null && activeSlot >= 0) launchCrop(uri)
        activeSlot = -1
        activeCameraSlot = -1
        slotSheetFor = -1
    }

    // Permission for camera
    val cameraPermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            activeSlot = -1
            activeCameraSlot = -1
            scope.launch { snackbar.showSnackbar("Camera permission required") }
        }
    }

    fun startCamera(slotIdx: Int) {
        activeSlot = slotIdx
        activeCameraSlot = slotIdx
        cameraPermission.launch(Manifest.permission.CAMERA)
    }

    fun exportNow() {
        val outUri = CollageRenderer.renderAndSave(
            context = context,
            template = vm.selectedTemplate.value,
            slotUris = vm.slotUris.mapIndexed { i, u -> u ?: vm.draftCaptureUris.getOrNull(i) }.toList(),
            slotTransforms = vm.slotTransforms.toList(),
            spacingPx = vm.spacingPx.value,
            cornerRadiusPx = vm.cornerRadiusPx.value,
            outSizePx = 2048
        )
        scope.launch {
            if (outUri != null) {
                snackbar.showSnackbar("Saved to Gallery")
            } else {
                snackbar.showSnackbar("Save failed")
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        if (screen == Screen.HOME) "AutoCollage" else "Editor",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                actions = {
                    if (screen == Screen.EDITOR) {
                        IconButton(onClick = { exportNow() }) {
                            Icon(Icons.Filled.Upload, contentDescription = "Export")
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (screen == Screen.EDITOR) {
                NavigationBar {
                    NavigationBarItem(
                        selected = tab == EditorTab.TEMPLATES,
                        onClick = { tab = EditorTab.TEMPLATES },
                        icon = { Icon(Icons.Filled.Collections, contentDescription = null) },
                        label = { Text("Templates") }
                    )
                    NavigationBarItem(
                        selected = tab == EditorTab.ADJUST,
                        onClick = { tab = EditorTab.ADJUST; showAdjustSheet = true },
                        icon = { Icon(Icons.Filled.Tune, contentDescription = null) },
                        label = { Text("Adjust") }
                    )
                    NavigationBarItem(
                        selected = tab == EditorTab.BACKGROUND,
                        onClick = { tab = EditorTab.BACKGROUND },
                        icon = { Icon(Icons.Filled.PhotoLibrary, contentDescription = null) },
                        label = { Text("Background") }
                    )
                    NavigationBarItem(
                        selected = tab == EditorTab.EXPORT,
                        onClick = { tab = EditorTab.EXPORT; exportNow() },
                        icon = { Icon(Icons.Filled.Upload, contentDescription = null) },
                        label = { Text("Export") }
                    )
                }
            }
        }
    ) { padding ->
        when (screen) {
            Screen.HOME -> HomeScreen(
                modifier = Modifier.padding(padding),
                vm = vm,
                onNew = {
                    vm.setTemplate(vm.selectedTemplate.value)
                    screen = Screen.EDITOR
                },
                onPickTemplate = {
                    vm.setTemplate(it)
                    screen = Screen.EDITOR
                }
            )
            Screen.EDITOR -> EditorScreen(
                modifier = Modifier.padding(padding),
                vm = vm,
                activeCameraSlot = activeCameraSlot,
                onBack = { screen = Screen.HOME },
                onSlotTap = { idx ->
                    slotSheetFor = idx
                },
                onSlotLongPress = { idx ->
                    activeSlot = idx
                    galleryPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                onTransformChange = { i, t -> vm.setSlotTransform(i, t) },
                onCameraCaptured = { slotIdx, capturedUri ->
                    vm.setSlotUri(slotIdx, capturedUri)
                    activeSlot = slotIdx
                    launchCrop(capturedUri)
                },
                onCameraCancel = {
                    if (activeCameraSlot >= 0) vm.clearDraftCapture(activeCameraSlot)
                    activeSlot = -1
                    activeCameraSlot = -1
                }
            )
        }
    }

    // Slot action sheet (Camera / Gallery / Remove)
    if (slotSheetFor >= 0 && screen == Screen.EDITOR) {
        ModalBottomSheet(onDismissRequest = { slotSheetFor = -1 }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Add photo", style = MaterialTheme.typography.titleMedium)
                Button(
                    onClick = {
                        val idx = slotSheetFor
                        slotSheetFor = -1
                        startCamera(idx)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Camera") }

                OutlinedButton(
                    onClick = {
                        activeSlot = slotSheetFor
                        slotSheetFor = -1
                        galleryPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Gallery") }

                TextButton(
                    onClick = {
                        val idx = slotSheetFor
                        if (idx >= 0) {
                            vm.slotUris[idx] = null
                            vm.clearDraftCapture(idx)
                        }
                        slotSheetFor = -1
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Remove", color = MaterialTheme.colorScheme.error) }

                Spacer(Modifier.height(10.dp))
            }
        }
    }

    if (showAdjustSheet && screen == Screen.EDITOR) {
        ModalBottomSheet(onDismissRequest = { showAdjustSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("Adjust layout", style = MaterialTheme.typography.titleMedium)
                Text("Spacing", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Slider(value = vm.spacingPx.value, onValueChange = { vm.spacingPx.value = it }, valueRange = 0f..64f)
                Text("Corner radius", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Slider(value = vm.cornerRadiusPx.value, onValueChange = { vm.cornerRadiusPx.value = it }, valueRange = 0f..96f)
                Button(onClick = { showAdjustSheet = false }, modifier = Modifier.fillMaxWidth()) { Text("Done") }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun HomeScreen(
    modifier: Modifier = Modifier,
    vm: CollageViewModel,
    onNew: () -> Unit,
    onPickTemplate: (CollageTemplate) -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ElevatedCard(shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Create a collage", style = MaterialTheme.typography.titleLarge)
                Text("Choose a layout and add photos from Camera or Gallery.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = onNew, modifier = Modifier.fillMaxWidth()) { Text("New Collage") }
            }
        }

        ElevatedCard(shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Templates", style = MaterialTheme.typography.titleMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(CollageTemplates.all) { t ->
                        AssistChip(
                            onClick = { onPickTemplate(t) },
                            label = { Text(t.name) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EditorScreen(
    modifier: Modifier = Modifier,
    vm: CollageViewModel,
    activeCameraSlot: Int,
    onBack: () -> Unit,
    onSlotTap: (Int) -> Unit,
    onSlotLongPress: (Int) -> Unit,
    onTransformChange: (Int, SlotTransform) -> Unit,
    onCameraCaptured: (Int, Uri) -> Unit,
    onCameraCancel: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CollagePreview(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(22.dp)),
            template = vm.selectedTemplate.value,
            slotUris = vm.slotUris,
            slotTransforms = vm.slotTransforms,
            spacingPx = vm.spacingPx.value,
            cornerRadiusPx = vm.cornerRadiusPx.value,
            vm = vm,
            activeCameraSlot = activeCameraSlot,
            onSlotTap = onSlotTap,
            onSlotLongPress = onSlotLongPress,
            onTransformChange = onTransformChange,
            onCameraCaptured = onCameraCaptured,
            onCameraCancel = onCameraCancel
        )
        Text(
            "Tip: tap a slot to choose Camera/Gallery. Pinch & drag to position.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
