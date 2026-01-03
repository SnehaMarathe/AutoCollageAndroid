package com.example.collage

import android.net.Uri
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.collage.ui.theme.BrandGradient

private enum class Screen { HOME, EDITOR }
private enum class EditorTab { TEMPLATES, ADJUST, BACKGROUND, EXPORT }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LaunchApp(vm: CollageViewModel) {
    val context = LocalContext.current
    var screen by remember { mutableStateOf(Screen.HOME) }

    LaunchedEffect(Unit) {
        vm.loadRecentExportsFromMediaStore(context)
    }

    AnimatedContent(
        targetState = screen,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "nav"
    ) { s ->
        when (s) {
            Screen.HOME -> HomeScreen(
                vm = vm,
                onStart = {
                    screen = Screen.EDITOR
                }
            )
            Screen.EDITOR -> EditorScreen(
                vm = vm,
                onBack = { screen = Screen.HOME }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(vm: CollageViewModel, onStart: () -> Unit) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("AutoCollage", style = MaterialTheme.typography.titleLarge)
                        Text("Create polished collages fast", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    Box(
                        modifier = Modifier
                            .padding(start = 14.dp)
                            .size(10.dp)
                            .clip(RoundedCornerShape(99.dp))
                            .background(BrandGradient())
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0f)),
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
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            ElevatedCard(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f))
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Start", style = MaterialTheme.typography.titleMedium)
                    Button(
                        onClick = onStart,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(vertical = 14.dp)
                    ) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null)
                        Spacer(Modifier.width(10.dp))
                        Text("New Collage")
                    }
                    Text("Tap ‘New Collage’ → fill slots → Export.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            if (vm.recentExports.isNotEmpty()) {
                Text("Recent exports", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(vm.recentExports.take(12)) { uri ->
                        RecentThumb(uri = uri, vm = vm)
                    }
                }
            }

            Text("Templates", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            TemplateGrid(
                selectedId = vm.selectedTemplate.value.id,
                onSelect = { vm.setTemplate(it) }
            )
        }
    }
}

@Composable
private fun RecentThumb(uri: Uri, vm: CollageViewModel) {
    val context = LocalContext.current
    var bmp by remember(uri) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }

    LaunchedEffect(uri) {
        vm.getCachedThumb(uri)?.let { bmp = it; return@LaunchedEffect }
        val loaded = ThumbnailLoader.loadThumbnail(context, uri, maxSizePx = 512)
        if (loaded != null) vm.putCachedThumb(uri, loaded)
        bmp = loaded
    }

    Box(
        modifier = Modifier
            .size(88.dp)
            .clip(RoundedCornerShape(18.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
        contentAlignment = Alignment.Center
    ) {
        if (bmp != null) {
            Image(bmp!!, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorScreen(vm: CollageViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var activeSlot by remember { mutableIntStateOf(-1) }
    var activeCameraSlot by remember { mutableIntStateOf(-1) }

    var showSlotActions by remember { mutableStateOf(false) }
    var tab by remember { mutableStateOf(EditorTab.TEMPLATES) }
    var showSheet by remember { mutableStateOf(false) }

    val cropLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { res ->
        when (res.resultCode) {
            android.app.Activity.RESULT_OK -> {
                val out = com.yalantis.ucrop.UCrop.getOutput(res.data!!)
                if (out != null && activeSlot >= 0) vm.setSlotUri(activeSlot, out)
            }
            com.yalantis.ucrop.UCrop.RESULT_ERROR -> {
                val err = com.yalantis.ucrop.UCrop.getError(res.data!!)
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

    val cameraPermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            activeSlot = -1
            activeCameraSlot = -1
            scope.launch { snackbarHostState.showSnackbar("Camera permission required") }
        }
    }

    fun startCamera(slotIdx: Int) {
        activeSlot = slotIdx
        activeCameraSlot = slotIdx
        cameraPermission.launch(android.Manifest.permission.CAMERA)
    }

    fun exportNow() {
        val outUri = CollageRenderer.renderAndSave(
            context = context,
            template = vm.selectedTemplate.value,
            slotUris = vm.slotUris.mapIndexed { i, u -> u ?: vm.draftCaptureUris.getOrNull(i) }.toList(),
            slotTransforms = vm.slotTransforms.toList(),
            spacingPx = vm.spacingPx.value,
            cornerRadiusPx = vm.cornerRadiusPx.value,
            outSizePx = 2048,
            backgroundColor = vm.backgroundColorArgb.value
        )
        if (outUri != null) vm.addRecentExport(outUri)
        scope.launch { snackbarHostState.showSnackbar(if (outUri != null) "Saved to Gallery" else "Save failed") }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Editor", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    IconButton(onClick = { tab = EditorTab.EXPORT; showSheet = true }) {
                        Icon(Icons.Filled.Upload, contentDescription = "Export")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.35f))
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.35f)) {
                EditorNavItem(EditorTab.TEMPLATES, tab, Icons.Filled.GridView, "Templates") { tab = it; showSheet = true }
                EditorNavItem(EditorTab.ADJUST, tab, Icons.Filled.Tune, "Adjust") { tab = it; showSheet = true }
                EditorNavItem(EditorTab.BACKGROUND, tab, Icons.Filled.Palette, "Background") { tab = it; showSheet = true }
                EditorNavItem(EditorTab.EXPORT, tab, Icons.Filled.Upload, "Export") { tab = it; showSheet = true }
            }
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
                onSlotTap = { idx ->
                    activeSlot = idx
                    showSlotActions = true
                },
                onSlotLongPress = { idx ->
                    // optional: long press opens gallery immediately
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

            ElevatedCard(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.45f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Tip: pinch/drag to position", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { tab = EditorTab.EXPORT; showSheet = true }) { Text("Export") }
                }
            }
        }

        if (showSlotActions && activeSlot >= 0) {
            ModalBottomSheet(onDismissRequest = { showSlotActions = false }) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Add photo", style = MaterialTheme.typography.titleMedium)
                    ListItem(
                        headlineContent = { Text("Camera") },
                        leadingContent = { Icon(Icons.Filled.PhotoCamera, contentDescription = null) },
                        modifier = Modifier.clickable {
                            showSlotActions = false
                            startCamera(activeSlot)
                        }
                    )
                    ListItem(
                        headlineContent = { Text("Gallery") },
                        leadingContent = { Icon(Icons.Filled.Collections, contentDescription = null) },
                        modifier = Modifier.clickable {
                            showSlotActions = false
                            galleryPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                    )
                    if (vm.slotUris.getOrNull(activeSlot) != null || vm.draftCaptureUris.getOrNull(activeSlot) != null) {
                        ListItem(
                            headlineContent = { Text("Remove") },
                            leadingContent = { Icon(Icons.Filled.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                            modifier = Modifier.clickable {
                                vm.clearSlot(activeSlot)
                                showSlotActions = false
                            }
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
        }

        if (showSheet) {
            when (tab) {
                EditorTab.TEMPLATES -> EditorSheet(title = "Templates", onClose = { showSheet = false }) {
                    TemplateGrid(selectedId = vm.selectedTemplate.value.id, onSelect = { vm.setTemplate(it) })
                }
                EditorTab.ADJUST -> EditorSheet(title = "Adjust", onClose = { showSheet = false }) {
                    Text("Spacing", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Slider(value = vm.spacingPx.value, onValueChange = { vm.spacingPx.value = it }, valueRange = 0f..64f)
                    Text("Corner radius", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Slider(value = vm.cornerRadiusPx.value, onValueChange = { vm.cornerRadiusPx.value = it }, valueRange = 0f..96f)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = {
                            vm.spacingPx.value = 14f
                            vm.cornerRadiusPx.value = 26f
                        }, modifier = Modifier.weight(1f)) { Text("Reset") }
                        Button(onClick = { showSheet = false }, modifier = Modifier.weight(1f)) { Text("Done") }
                    }
                }
                EditorTab.BACKGROUND -> EditorSheet(title = "Background", onClose = { showSheet = false }) {
                    BackgroundPicker(
                        selected = vm.backgroundColorArgb.value,
                        onSelect = { vm.backgroundColorArgb.value = it }
                    )
                    Button(onClick = { showSheet = false }, modifier = Modifier.fillMaxWidth()) { Text("Done") }
                }
                EditorTab.EXPORT -> EditorSheet(title = "Export", onClose = { showSheet = false }) {
                    Text("High quality export (2048px)", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(6.dp))
                    Button(
                        onClick = {
                            exportNow()
                            showSheet = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 14.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Filled.Upload, contentDescription = null)
                        Spacer(Modifier.width(10.dp))
                        Text("Save to Gallery")
                    }
                }
            }
        }
    }
}

@Composable
private fun EditorNavItem(tab: EditorTab, selected: EditorTab, icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: (EditorTab) -> Unit) {
    NavigationBarItem(
        selected = selected == tab,
        onClick = { onClick(tab) },
        icon = { Icon(icon, contentDescription = label) },
        label = { Text(label) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorSheet(title: String, onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(onDismissRequest = onClose) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Close") }
                }
                content()
                Spacer(Modifier.height(8.dp))
            }
        )
    }
}

@Composable
private fun TemplateGrid(selectedId: String, onSelect: (CollageTemplate) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        CollageTemplates.all.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { t ->
                    val selected = t.id == selectedId
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1.2f)
                            .clip(RoundedCornerShape(16.dp))
                            .border(
                                width = if (selected) 2.dp else 1.dp,
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                shape = RoundedCornerShape(16.dp)
                            )
                            .clickable { onSelect(t) },
                        color = MaterialTheme.colorScheme.surface.copy(alpha = if (selected) 0.65f else 0.45f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(t.name, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
                if (row.size < 3) repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun BackgroundPicker(selected: Int, onSelect: (Int) -> Unit) {
    val options = listOf(
        0xFF0B0F19.toInt(),
        0xFF111827.toInt(),
        0xFF0F172A.toInt(),
        0xFFFFFFFF.toInt(),
        0xFFF8FAFC.toInt(),
        0xFFFFF7ED.toInt()
    )
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        options.forEach { c ->
            val isSel = c == selected
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(c))
                    .border(2.dp, if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                    .clickable { onSelect(c) }
            )
        }
    }
}
