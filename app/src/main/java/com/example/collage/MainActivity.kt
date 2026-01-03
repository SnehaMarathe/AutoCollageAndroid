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
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.collage.ui.theme.AutoCollageTheme
import com.example.collage.ui.theme.BrandGradient
import com.yalantis.ucrop.UCrop
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AutoCollageTheme { CollageApp() } }
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
    var showAdjustSheet by remember { mutableStateOf(false) }

    val cropLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { res ->
        when (res.resultCode) {
            Activity.RESULT_OK -> {
                val out = UCrop.getOutput(res.data!!)
                if (out != null && activeSlot >= 0) vm.setSlotUri(activeSlot, out)
                else scope.launch { snackbarHostState.showSnackbar("Crop output missing") }
            }
            UCrop.RESULT_ERROR -> {
                val err = UCrop.getError(res.data!!)
                scope.launch { snackbarHostState.showSnackbar("Crop failed: ${err?.message ?: "unknown"}") }
            }
            else -> { /* cancelled */ }
        }
        if (activeSlot >= 0) vm.clearDraftCapture(activeSlot)
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
        scope.launch { snackbarHostState.showSnackbar(if (outUri != null) "Saved to Gallery" else "Save failed") }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("AutoCollage", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "Tap slot • Long-press gallery",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0f)),
                navigationIcon = {
                    Box(
                        modifier = Modifier
                            .padding(start = 12.dp)
                            .size(10.dp)
                            .clip(RoundedCornerShape(99.dp))
                            .background(BrandGradient())
                    )
                },
                actions = {
                    IconButton(onClick = { showAdjustSheet = true }) { Icon(Icons.Filled.Tune, contentDescription = "Adjust") }
                    FilledTonalIconButton(onClick = { exportNow() }) { Icon(Icons.Filled.Upload, contentDescription = "Export") }
                    Spacer(Modifier.width(10.dp))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BrandGradient(), RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
                    .padding(bottom = 2.dp)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ElevatedCard(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f))
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Templates", style = MaterialTheme.typography.titleSmall)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(CollageTemplates.all) { t ->
                            FilterChip(
                                selected = t.id == vm.selectedTemplate.value.id,
                                onClick = {
                                    vm.setTemplate(t)
                                    activeSlot = -1
                                    activeCameraSlot = -1
                                },
                                label = { Text(t.name) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                                    selectedLabelColor = MaterialTheme.colorScheme.onSurface,
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    borderColor = MaterialTheme.colorScheme.outline,
                                    selectedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                )
                            )
                        }
                    }
                }
            }

            CollagePreview(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(22.dp))
                    .animateContentSize(),
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
                    galleryPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                onTransformChange = { idx, t -> vm.setSlotTransform(idx, t) },
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AssistChip(onClick = { showAdjustSheet = true }, label = { Text("Adjust layout") })
                Text("Export from top-right", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (showAdjustSheet) {
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
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { showAdjustSheet = false }, modifier = Modifier.fillMaxWidth()) { Text("Done") }
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
}
