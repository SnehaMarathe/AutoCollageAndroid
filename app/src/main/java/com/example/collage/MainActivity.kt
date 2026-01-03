package com.example.collage

import android.Manifest
import android.app.Activity
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yalantis.ucrop.UCrop
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { Surface { CollageApp() } } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollageApp(vm: CollageViewModel = viewModel()) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var activeSlot by remember { mutableIntStateOf(-1) }
    var activeCameraSlot by remember { mutableIntStateOf(-1) }

    val cropLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { res ->
        when (res.resultCode) {
            Activity.RESULT_OK -> {
                val out = UCrop.getOutput(res.data!!)
                if (out != null && activeSlot >= 0) {
                    vm.setSlotUri(activeSlot, out)
                } else {
                    scope.launch { snackbarHostState.showSnackbar("Crop output missing") }
                }
            }
            UCrop.RESULT_ERROR -> {
                val err = UCrop.getError(res.data!!)
                scope.launch { snackbarHostState.showSnackbar("Crop failed: ${err?.message ?: "unknown"}") }
            }
            else -> { /* cancelled */ }
        }
        activeSlot = -1
        activeCameraSlot = -1
    }

    fun launchCrop(source: Uri) {
        cropLauncher.launch(UCropHelper.buildIntent(context, source))
    }

    val galleryPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null && activeSlot >= 0) launchCrop(uri)
        activeSlot = -1
        activeCameraSlot = -1
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            activeSlot = -1
            activeCameraSlot = -1
            scope.launch { snackbarHostState.showSnackbar("Camera permission required") }
        }
    }

    fun startInSlotCamera(slotIdx: Int) {
        activeSlot = slotIdx
        activeCameraSlot = slotIdx
        permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { CenterAlignedTopAppBar(title = { Text("AutoCollage") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TemplateRow(
                selectedId = vm.selectedTemplate.value.id,
                onSelect = {
                    vm.setTemplate(it)
                    activeSlot = -1
                    activeCameraSlot = -1
                }
            )

            CollagePreview(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .aspectRatio(1f),
                template = vm.selectedTemplate.value,
                slotUris = vm.slotUris,
                slotTransforms = vm.slotTransforms,
                spacingPx = vm.spacingPx.value,
                cornerRadiusPx = vm.cornerRadiusPx.value,
                vm = vm,
                activeCameraSlot = activeCameraSlot,
                onSlotTap = { idx -> startInSlotCamera(idx) },
                onSlotLongPress = { idx ->
                    activeSlot = idx
                    galleryPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onTransformChange = { idx, t -> vm.setSlotTransform(idx, t) },
                onCameraCaptured = { slotIdx, capturedUri ->
                    // ✅ Immediately commit the captured photo to the slot so it doesn't disappear
                    vm.setSlotUri(slotIdx, capturedUri)
                    activeSlot = slotIdx
                    launchCrop(capturedUri)
                },
                onCameraCancel = {
                    activeSlot = -1
                    activeCameraSlot = -1
                }
            )

            ElevatedCard {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Layout", style = MaterialTheme.typography.titleSmall)
                    Text("Spacing", style = MaterialTheme.typography.labelMedium)
                    Slider(vm.spacingPx.value, { vm.spacingPx.value = it }, valueRange = 0f..64f)
                    Text("Corner radius", style = MaterialTheme.typography.labelMedium)
                    Slider(vm.cornerRadiusPx.value, { vm.cornerRadiusPx.value = it }, valueRange = 0f..96f)
                }
            }

            Button(
                onClick = {
                    val outUri = CollageRenderer.renderAndSave(
                        context = context,
                        template = vm.selectedTemplate.value,
                        slotUris = vm.slotUris.toList(),
                        slotTransforms = vm.slotTransforms.toList(),
                        spacingPx = vm.spacingPx.value,
                        cornerRadiusPx = vm.cornerRadiusPx.value,
                        outSizePx = 2048
                    )
                    scope.launch { snackbarHostState.showSnackbar(if (outUri != null) "Saved to Gallery" else "Save failed") }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Export") }

            Text(
                text = "Tap slot: camera in-slot • Long-press: gallery",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TemplateRow(selectedId: String, onSelect: (CollageTemplate) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(CollageTemplates.all) { t ->
            FilterChip(
                selected = t.id == selectedId,
                onClick = { onSelect(t) },
                label = { Text(t.name) }
            )
        }
    }
}
