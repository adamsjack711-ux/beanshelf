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
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.runtime.mutableStateMapOf
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
    var roastedOn by rememberSaveable { mutableStateOf(existing?.roastedOn ?: "") }
    var rating by rememberSaveable { mutableStateOf(existing?.rating ?: 0f) }
    var photoPath by rememberSaveable { mutableStateOf(existing?.photoPath) }
    var backPhotoPath by rememberSaveable { mutableStateOf(existing?.backPhotoPath) }

    var scanning by remember { mutableStateOf(false) }
    var scannedFields by remember { mutableStateOf<List<String>>(emptyList()) }
    // Manual crop target: path being cropped + whether it's the back photo.
    var cropTarget by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    // Tapping an existing photo only VIEWS it; the camera stays behind its buttons.
    var viewerPath by remember { mutableStateOf<String?>(null) }

    // Uncertain scan results wait here for the user's confirmation sheet.
    var unsureProposals by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }

    // Applies one scanned value to its field ONLY if the user left it blank.
    fun applyField(key: String, value: String): Boolean = when (key) {
        "name" -> name.isBlank().also { if (it) name = value }
        "roaster" -> roaster.isBlank().also { if (it) roaster = value }
        "origin" -> origin.isBlank().also { if (it) origin = value }
        "roast" -> roastLevel.isBlank().also { if (it) roastLevel = value }
        "process" -> process.isBlank().also { if (it) process = value }
        "notes" -> notes.isBlank().also { if (it) notes = value }
        "variety" -> variety.isBlank().also { if (it) variety = value }
        "elevation" -> elevation.isBlank().also { if (it) elevation = value }
        "producer" -> producer.isBlank().also { if (it) producer = value }
        "roasted" -> roastedOn.isBlank().also { if (it) roastedOn = value }
        else -> false
    }

    // Crop to the bag, show it, then OCR the label. Confident fields (keyword-
    // derived) fill blanks silently; guesses go to a confirmation sheet instead
    // of being written — the user removes mistakes before they land.
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
        val ask = mutableListOf<Pair<String, String>>()
        listOf(
            "name" to info.name, "roaster" to info.roaster, "origin" to info.origin,
            "roast" to info.roastLevel, "process" to info.process, "notes" to info.notes,
            "variety" to info.variety, "elevation" to info.elevation, "producer" to info.producer,
            "roasted" to info.roastedOn,
        ).forEach { (key, value) ->
            if (value == null) return@forEach
            when {
                key in info.unsure -> ask += key to value
                applyField(key, value) -> filled += key
            }
        }
        scannedFields = filled
        unsureProposals = ask
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
                // Edge-to-edge windows don't resize for the keyboard — without this
                // the bottom fields hide behind the IME and can't be scrolled to.
                .imePadding()
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
                onView = { photoPath?.let { viewerPath = it } },
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
                    onView = { backPhotoPath?.let { viewerPath = it } },
                )
            }

            viewerPath?.let { PhotoViewerDialog(path = it) { viewerPath = null } }

            cropTarget?.let { (path, isBack) ->
                CropDialog(path = path) { newPath ->
                    cropTarget = null
                    if (newPath != null) {
                        if (isBack) backPhotoPath = newPath else photoPath = newPath
                    }
                }
            }

            if (unsureProposals.isNotEmpty()) {
                ScanReviewSheet(
                    proposals = unsureProposals,
                    onDismiss = { unsureProposals = emptyList() },
                    onApply = { accepted ->
                        val applied = accepted.filter { (k, v) -> applyField(k, v) }.map { it.first }
                        scannedFields = scannedFields + applied
                        unsureProposals = emptyList()
                    },
                )
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
            LabeledField(roastedOn, { roastedOn = it }, "Roasted on — e.g. May 26")

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
                            roastedOn = roastedOn.trim(),
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
    onView: () -> Unit,
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
                // Empty slot: tap opens the camera. Existing photo: tap just views it.
                .clickable(onClick = if (photoPath == null) onCamera else onView),
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

/**
 * Confirmation sheet for scan results the parser wasn't sure about. Nothing in
 * this list has touched the form yet — the user unchecks mistakes, then applies.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScanReviewSheet(
    proposals: List<Pair<String, String>>,
    onDismiss: () -> Unit,
    onApply: (List<Pair<String, String>>) -> Unit,
) {
    val labels = mapOf(
        "name" to "Bean name", "roaster" to "Roaster", "origin" to "Origin",
        "roast" to "Roast level", "process" to "Process", "notes" to "Tasting notes",
        "variety" to "Variety", "elevation" to "Elevation", "producer" to "Producer",
        "roasted" to "Roast date",
    )
    val checked = remember(proposals) {
        mutableStateMapOf<String, Boolean>().apply { proposals.forEach { put(it.first, true) } }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Surface2) {
        Column(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 36.dp)) {
            Text("Check what I read", style = MaterialTheme.typography.titleLarge, color = Parchment)
            Text(
                "I'm not certain about these. Uncheck anything that's wrong, or fix it in the form after.",
                style = MaterialTheme.typography.bodyMedium,
                color = Dim,
                modifier = Modifier.padding(top = 6.dp, bottom = 10.dp),
            )
            proposals.forEach { (key, value) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { checked[key] = !(checked[key] ?: true) }
                        .padding(vertical = 6.dp),
                ) {
                    Checkbox(
                        checked = checked[key] ?: true,
                        onCheckedChange = { checked[key] = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = Crema,
                            checkmarkColor = Roast,
                            uncheckedColor = Dim,
                        ),
                    )
                    Column(Modifier.padding(start = 8.dp)) {
                        Eyebrow(labels[key] ?: key)
                        Text(value, style = MaterialTheme.typography.bodyLarge, color = Parchment)
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss) { Text("Skip all", color = Dim) }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = { onApply(proposals.filter { checked[it.first] == true }) },
                    colors = ButtonDefaults.buttonColors(containerColor = Crema, contentColor = Roast),
                ) { Text("Use checked") }
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
    onView: () -> Unit,
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
                .clickable(onClick = if (backPhotoPath == null) onCamera else onView),
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
