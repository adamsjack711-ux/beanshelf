package dev.adamsjack.beanshelf.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import dev.adamsjack.beanshelf.data.BagCropper
import dev.adamsjack.beanshelf.data.LabelScanner
import androidx.compose.foundation.layout.padding
import dev.adamsjack.beanshelf.data.PhotoStore
import dev.adamsjack.beanshelf.model.Bean
import dev.adamsjack.beanshelf.model.PROCESSES
import dev.adamsjack.beanshelf.model.ROAST_LEVELS
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddEditScreen(
    existing: Bean?,
    onSave: (Bean) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var name by rememberSaveable { mutableStateOf(existing?.name ?: "") }
    var roaster by rememberSaveable { mutableStateOf(existing?.roaster ?: "") }
    var origin by rememberSaveable { mutableStateOf(existing?.origin ?: "") }
    var roastLevel by rememberSaveable { mutableStateOf(existing?.roastLevel ?: "") }
    var process by rememberSaveable { mutableStateOf(existing?.process ?: "") }
    var notes by rememberSaveable { mutableStateOf(existing?.notes ?: "") }
    var variety by rememberSaveable { mutableStateOf(existing?.variety ?: "") }
    var elevation by rememberSaveable { mutableStateOf(existing?.elevation ?: "") }
    var producer by rememberSaveable { mutableStateOf(existing?.producer ?: "") }
    var rating by rememberSaveable { mutableStateOf(existing?.rating ?: 0f) }
    var photoPath by rememberSaveable { mutableStateOf(existing?.photoPath) }
    var backPhotoPath by rememberSaveable { mutableStateOf(existing?.backPhotoPath) }

    var scanning by remember { mutableStateOf(false) }
    var scannedFields by remember { mutableStateOf<List<String>>(emptyList()) }
    // Manual crop target: path being cropped + whether it's the back photo.
    var cropTarget by remember { mutableStateOf<Pair<String, Boolean>?>(null) }

    // Crop to the bag, show it, then OCR the label and fill ONLY blank fields.
    // Works for both sides: the back merges into whatever is still blank.
    suspend fun scanLabel(path: String, isBack: Boolean = false) {
        scanning = true
        val cutPath = BagCropper.cutOutBag(context, path)
        if (isBack) backPhotoPath = cutPath else photoPath = cutPath
        val info = LabelScanner.scan(context, cutPath)
        scanning = false
        if (info == null) {
            scannedFields = emptyList()
            return
        }
        val filled = mutableListOf<String>()
        info.name?.takeIf { name.isBlank() }?.let { name = it; filled += "name" }
        info.roaster?.takeIf { roaster.isBlank() }?.let { roaster = it; filled += "roaster" }
        info.origin?.takeIf { origin.isBlank() }?.let { origin = it; filled += "origin" }
        info.roastLevel?.takeIf { roastLevel.isBlank() }?.let { roastLevel = it; filled += "roast" }
        info.process?.takeIf { process.isBlank() }?.let { process = it; filled += "process" }
        info.notes?.takeIf { notes.isBlank() }?.let { notes = it; filled += "notes" }
        info.variety?.takeIf { variety.isBlank() }?.let { variety = it; filled += "variety" }
        info.elevation?.takeIf { elevation.isBlank() }?.let { elevation = it; filled += "elevation" }
        info.producer?.takeIf { producer.isBlank() }?.let { producer = it; filled += "producer" }
        scannedFields = filled
    }

    val captureFile = remember { PhotoStore.cameraCaptureFile(context) }
    val captureUri = remember {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", captureFile)
    }
    // The same capture file is reused; this flag routes the result to front or back.
    var capturingBack by rememberSaveable { mutableStateOf(false) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) scope.launch {
            PhotoStore.importFile(context, captureFile)?.let { scanLabel(it, isBack = capturingBack) }
        }
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            PhotoStore.importUri(context, uri)?.let { scanLabel(it, isBack = capturingBack) }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (existing == null) "New bag" else "Edit bag",
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = Parchment,
                    navigationIconContentColor = Parchment,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            PhotoPicker(
                photoPath = photoPath,
                onCamera = { capturingBack = false; cameraLauncher.launch(captureUri) },
                onGallery = {
                    capturingBack = false
                    galleryLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onCrop = { photoPath?.let { cropTarget = it to false } },
            )

            if (photoPath != null) {
                BackPhotoStrip(
                    backPhotoPath = backPhotoPath,
                    onCamera = { capturingBack = true; cameraLauncher.launch(captureUri) },
                    onGallery = {
                        capturingBack = true
                        galleryLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onCrop = { backPhotoPath?.let { cropTarget = it to true } },
                )
            }

            cropTarget?.let { (path, isBack) ->
                CropDialog(path = path) { newPath ->
                    cropTarget = null
                    if (newPath != null) {
                        if (isBack) backPhotoPath = newPath else photoPath = newPath
                    }
                }
            }

            if (scanning) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    CircularProgressIndicator(
                        color = Crema,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        "Reading the label…",
                        style = MaterialTheme.typography.bodySmall,
                        color = Dim,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            } else if (scannedFields.isNotEmpty()) {
                Text(
                    "Filled from the label: ${scannedFields.joinToString(", ")} — double-check the details.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Crema,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            Eyebrow("The bag", Modifier.padding(top = 26.dp, bottom = 10.dp))
            LabeledField(name, { name = it }, "Bean name")
            LabeledField(roaster, { roaster = it }, "Roaster")
            LabeledField(origin, { origin = it }, "Origin — country or farm")

            Eyebrow("Details", Modifier.padding(top = 22.dp, bottom = 10.dp))
            LabeledField(producer, { producer = it }, "Producer — farmer or farm")
            LabeledField(variety, { variety = it }, "Variety — e.g. Pacas, Bourbon")
            LabeledField(elevation, { elevation = it }, "Elevation — e.g. 1,650 masl")

            Eyebrow("Roast", Modifier.padding(top = 22.dp, bottom = 6.dp))
            ChoiceChips(ROAST_LEVELS, roastLevel) { roastLevel = if (roastLevel == it) "" else it }

            Eyebrow("Process", Modifier.padding(top = 18.dp, bottom = 6.dp))
            ChoiceChips(PROCESSES, process) { process = if (process == it) "" else it }

            Eyebrow("Tasting notes", Modifier.padding(top = 22.dp, bottom = 10.dp))
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                placeholder = { Text("chocolate, black cherry, florals…", color = Dim) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors(),
            )

            Eyebrow("Your rating", Modifier.padding(top = 22.dp, bottom = 4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                RoastStamp(rating = rating, size = 56.dp)
                RatingSlider(
                    value = rating,
                    onChange = { rating = it },
                    modifier = Modifier.weight(1f).padding(start = 18.dp),
                )
            }
            if (rating > 0f) {
                TextButton(onClick = { rating = 0f }) { Text("Clear rating", color = Dim) }
            }

            Button(
                onClick = {
                    onSave(
                        Bean(
                            id = existing?.id ?: UUID.randomUUID().toString(),
                            name = name.trim(),
                            roaster = roaster.trim(),
                            origin = origin.trim(),
                            roastLevel = roastLevel,
                            process = process,
                            notes = notes.trim(),
                            variety = variety.trim(),
                            elevation = elevation.trim(),
                            producer = producer.trim(),
                            rating = rating,
                            photoPath = photoPath,
                            backPhotoPath = backPhotoPath,
                            createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                            brews = existing?.brews ?: emptyList(),
                        )
                    )
                },
                enabled = name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Crema,
                    contentColor = Roast,
                    disabledContainerColor = SurfaceHigh,
                    disabledContentColor = Dim,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 28.dp, bottom = 40.dp)
                    .height(52.dp),
            ) {
                Text(if (existing == null) "Put it on the shelf" else "Save changes")
            }
        }
    }
}

@Composable
private fun PhotoPicker(
    photoPath: String?,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onCrop: () -> Unit,
) {
    val photo by PhotoStore.rememberPhoto(photoPath, targetWidth = 900)
    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Surface2)
                .then(
                    if (photoPath == null) Modifier.drawBehind {
                        drawRoundRect(
                            color = Dim.copy(alpha = 0.6f),
                            cornerRadius = CornerRadius(12.dp.toPx()),
                            style = Stroke(
                                width = 1.5.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 12f)),
                            ),
                        )
                    } else Modifier
                )
                .clickable(onClick = onCamera),
            contentAlignment = Alignment.Center,
        ) {
            val p = photo
            if (p != null) {
                Image(
                    bitmap = p,
                    contentDescription = "Bag photo",
                    // Cutouts have transparent surroundings — never zoom-crop them.
                    contentScale = if (BagCropper.isCutout(photoPath)) ContentScale.Fit else ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().padding(if (BagCropper.isCutout(photoPath)) 12.dp else 0.dp),
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.PhotoCamera,
                        contentDescription = null,
                        tint = Crema,
                        modifier = Modifier.size(40.dp),
                    )
                    Text(
                        "Photograph the bag",
                        style = MaterialTheme.typography.titleMedium,
                        color = Parchment,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    Text(
                        "Tap to open the camera",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Dim,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
        Row(Modifier.padding(top = 6.dp)) {
            TextButton(onClick = onCamera) {
                Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = Crema, modifier = Modifier.size(16.dp))
                Text(if (photoPath == null) "Camera" else "Retake", color = Crema, modifier = Modifier.padding(start = 6.dp))
            }
            TextButton(onClick = onGallery) {
                Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = Crema, modifier = Modifier.size(16.dp))
                Text("Gallery", color = Crema, modifier = Modifier.padding(start = 6.dp))
            }
            if (photoPath != null) {
                TextButton(onClick = onCrop) {
                    Icon(Icons.Default.Crop, contentDescription = null, tint = Crema, modifier = Modifier.size(16.dp))
                    Text("Crop", color = Crema, modifier = Modifier.padding(start = 6.dp))
                }
            }
        }
    }
}

/** Optional back-of-bag capture: small thumbnail + actions; scanned for extra info. */
@Composable
private fun BackPhotoStrip(
    backPhotoPath: String?,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onCrop: () -> Unit,
) {
    val photo by PhotoStore.rememberPhoto(backPhotoPath, targetWidth = 300)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(width = 64.dp, height = 80.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Surface2)
                .then(
                    if (backPhotoPath == null) Modifier.drawBehind {
                        drawRoundRect(
                            color = Dim.copy(alpha = 0.5f),
                            cornerRadius = CornerRadius(8.dp.toPx()),
                            style = Stroke(
                                width = 1.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
                            ),
                        )
                    } else Modifier
                )
                .clickable(onClick = onCamera),
            contentAlignment = Alignment.Center,
        ) {
            val p = photo
            if (p != null) {
                Image(
                    bitmap = p,
                    contentDescription = "Back of bag",
                    contentScale = if (BagCropper.isCutout(backPhotoPath)) ContentScale.Fit else ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = Dim, modifier = Modifier.size(18.dp))
            }
        }
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(
                if (backPhotoPath == null) "Back of the bag (optional)" else "Back of the bag",
                style = MaterialTheme.typography.bodyMedium,
                color = Parchment,
            )
            Text(
                if (backPhotoPath == null) "Scan it for tasting notes and farm details."
                else "Scanned — details merged below.",
                style = MaterialTheme.typography.bodySmall,
                color = Dim,
            )
            Row {
                TextButton(onClick = onCamera, contentPadding = PaddingValues(0.dp)) {
                    Text(if (backPhotoPath == null) "Camera" else "Retake", color = Crema)
                }
                TextButton(onClick = onGallery, contentPadding = PaddingValues(0.dp), modifier = Modifier.padding(start = 16.dp)) {
                    Text("Gallery", color = Crema)
                }
                if (backPhotoPath != null) {
                    TextButton(onClick = onCrop, contentPadding = PaddingValues(0.dp), modifier = Modifier.padding(start = 16.dp)) {
                        Text("Crop", color = Crema)
                    }
                }
            }
        }
    }
}

@Composable
private fun LabeledField(value: String, onChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        colors = fieldColors(),
    )
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Crema,
    unfocusedBorderColor = Dim.copy(alpha = 0.4f),
    focusedLabelColor = Crema,
    unfocusedLabelColor = Dim,
    cursorColor = Crema,
    focusedTextColor = Parchment,
    unfocusedTextColor = Parchment,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChoiceChips(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { opt ->
            FilterChip(
                selected = selected == opt,
                onClick = { onSelect(opt) },
                label = { Text(opt) },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = Surface2,
                    labelColor = Dim,
                    selectedContainerColor = Crema,
                    selectedLabelColor = Roast,
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = selected == opt,
                    borderColor = Dim.copy(alpha = 0.35f),
                    selectedBorderColor = Crema,
                ),
            )
        }
    }
}
