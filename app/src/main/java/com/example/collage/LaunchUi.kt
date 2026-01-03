package com.example.collage

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Collections
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
import com.yalantis.ucrop.UCrop
import kotlinx.coroutines.launch

private enum class Tab { TEMPLATES, ADJUST, EXPORT }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LaunchUiRoot(vm: CollageViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var tab by remember { mutableStateOf(Tab.TEMPLATES) }

    var activeSlot by remember { mutableIntStateOf(-1) }
    var activeCameraSlot by remember { mutableIntStateOf(-1) }    var showAdjustSheet by remember { mutableStateOf(false) }

    var showExportSheet by remember { mutableStateOf(false) }
    var lastExportUri by remember { mutableStateOf<Uri?>(null) }

    val cropLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { res ->
        when (res.resultCode) {
            Activity.RESULT_OK -> {
                val intent = res.data
                val out: Uri? = intent?.let { UCrop.getOutput(it) } ?: intent?.data
                if (out != null && activeSlot >= 0) {
                    vm.setSlotUri(activeSlot, out)
                } else {
                    // Crop finished but no explicit output: keep the committed slot image.
                    scope.launch { snackbar.showSnackbar("Crop finished") }
                }
            }
            UCrop.RESULT_ERROR -> {
                val err = UCrop.getError(res.data!!)
                scope.launch { snackbar.showSnackbar("Crop failed: ${err?.message ?: "unknown"}") }
            }
            else -> { /* cancelled */ }
        }
        if (activeSlot >= 0) vm.clearDraftCapture(activeSlot)
        activeSlot = -1
        activeCameraSlot = -1    }

    fun launchCrop(source: Uri) {
        cropLauncher.launch(UCropHelper.buildIntent(context, source))
    }

    val galleryPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null && activeSlot >= 0) {
            // Commit BEFORE crop (prevents "crop output missing" from blanking slot)
            vm.setSlotUri(activeSlot, uri)
            launchCrop(uri)
        }
        activeSlot = -1
        activeCameraSlot = -1    }

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

    fun exportNow(): Uri? =
        CollageRenderer.renderAndSave(
            context = context,
            template = vm.selectedTemplate.value,
            slotUris = vm.slotUris.mapIndexed { i, u -> u ?: vm.draftCaptureUris.getOrNull(i) }.toList(),
            slotTransforms = vm.slotTransforms.toList(),
            spacingPx = vm.spacingPx.value,
            cornerRadiusPx = vm.cornerRadiusPx.value,
            outSizePx = 2048
        )

    fun shareTo(packageName: String, uri: Uri) {
        val pm = context.packageManager
        val direct = Intent(Intent.ACTION_SEND).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            setPackage(packageName)
        }
        val finalIntent =
            if (direct.resolveActivity(pm) != null) direct
            else Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "image/*"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                },
                context.getString(R.string.app_name)
            )

        try {
            context.startActivity(finalIntent)
        } catch (_: Exception) {
            scope.launch { snackbar.showSnackbar("Unable to share") }
        }
    }

    fun shareChooser(uri: Uri) {
        val chooser = Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "image/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },
            "Share collage"
        )
        try {
            context.startActivity(chooser)
        } catch (_: Exception) {
            scope.launch { snackbar.showSnackbar("Unable to share") }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(context.getString(R.string.app_name), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            context.getString(R.string.tagline),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                // NOTE: no top-right export (requested)
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == Tab.TEMPLATES,
                    onClick = { tab = Tab.TEMPLATES },
                    icon = { Icon(Icons.Filled.Collections, contentDescription = null) },
                    label = { Text("Templates") }
                )
                NavigationBarItem(
                    selected = tab == Tab.ADJUST,
                    onClick = { tab = Tab.ADJUST; showAdjustSheet = true },
                    icon = { Icon(Icons.Filled.Tune, contentDescription = null) },
                    label = { Text("Adjust") }
                )
                NavigationBarItem(
                    selected = tab == Tab.EXPORT,
                    onClick = {
                        tab = Tab.EXPORT
                        val out = exportNow()
                        if (out != null) {
                            lastExportUri = out
                            showExportSheet = true
                            scope.launch { snackbar.showSnackbar("Saved to Gallery") }
                        } else {
                            scope.launch { snackbar.showSnackbar("Save failed") }
                        }
                    },
                    icon = { Icon(Icons.Filled.Upload, contentDescription = null) },
                    label = { Text("Export") }
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            ElevatedCard(shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Templates", style = MaterialTheme.typography.titleMedium)

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(CollageTemplates.all) { t ->
                            FilterChip(
                                selected = t.id == vm.selectedTemplate.value.id,
                                onClick = {
                                    vm.setTemplate(t)
                                    activeSlot = -1
                                    activeCameraSlot = -1
                                },
                                label = { Text(t.name) }
                            )
                        }
                    }
                }
            }

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
                onSlotTap = { idx -> startCamera(idx) },
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

// Camera action row (Cancel / Retake / Use) placed BELOW the slot window for a cleaner canvas.
if (activeCameraSlot >= 0) {
    val draft = vm.draftCaptureUris.getOrNull(activeCameraSlot)
    Spacer(Modifier.height(6.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedButton(
            onClick = {
                vm.clearDraftCapture(activeCameraSlot)
                activeSlot = -1
                activeCameraSlot = -1
            },
            modifier = Modifier.weight(1f)
        ) { Text("Cancel") }

        OutlinedButton(
            onClick = {
                // Retake keeps camera active but clears last draft
                vm.clearDraftCapture(activeCameraSlot)
            },
            modifier = Modifier.weight(1f),
            enabled = draft != null
        ) { Text("Retake") }

        Button(
            onClick = {
                val uri = draft ?: return@Button
                // Use -> commit and crop (same flow as before)
                vm.setSlotUri(activeCameraSlot, uri)
                activeSlot = activeCameraSlot
                launchCrop(uri)
            },
            modifier = Modifier.weight(1f),
            enabled = draft != null
        ) { Text("Use") }
    }
}

            Text(
                when (tab) {
                    Tab.TEMPLATES -> "Tap a slot to add photos. Pinch & drag to position."
                    Tab.ADJUST -> "Use Adjust to change spacing and corner radius."
                    Tab.EXPORT -> "Export saves the collage and shows share options."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
                Button(onClick = { showAdjustSheet = false }, modifier = Modifier.fillMaxWidth()) { Text("Done") }
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    if (showExportSheet) {
        val uri = lastExportUri
        ModalBottomSheet(onDismissRequest = { showExportSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Export ready", style = MaterialTheme.typography.titleMedium)
                Text("Share your collage:", color = MaterialTheme.colorScheme.onSurfaceVariant)

                Button(
                    onClick = { uri?.let { shareTo("com.instagram.android", it) } },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = uri != null
                ) { Text("Post to Instagram") }

                OutlinedButton(
                    onClick = { uri?.let { shareTo("com.whatsapp", it) } },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = uri != null
                ) { Text("Share on WhatsApp") }

                TextButton(
                    onClick = { uri?.let { shareChooser(it) } },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = uri != null
                ) { Text("More apps") }

                Spacer(Modifier.height(10.dp))
            }
        }
    }
}
