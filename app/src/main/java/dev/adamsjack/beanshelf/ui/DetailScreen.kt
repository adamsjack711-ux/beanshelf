package dev.adamsjack.beanshelf.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import android.content.Intent
import android.net.Uri
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import dev.adamsjack.beanshelf.data.Affiliate
import dev.adamsjack.beanshelf.data.BagCropper
import dev.adamsjack.beanshelf.data.PhotoStore
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.text.input.KeyboardType
import dev.adamsjack.beanshelf.model.BREW_METHODS
import dev.adamsjack.beanshelf.model.Bean
import dev.adamsjack.beanshelf.model.Brew
import dev.adamsjack.beanshelf.model.formatGrams
import dev.adamsjack.beanshelf.model.formatRating
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

private val dateFmt = SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    bean: Bean,
    allBrews: List<Brew>, // every brew across the shelf, newest first — used to prefill equipment
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onLogBrew: (Brew) -> Unit,
) {
    var showDelete by remember { mutableStateOf(false) }
    var showBrewSheet by rememberSaveable { mutableStateOf(false) }
    var viewerPath by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 48.dp),
    ) {
        item { Hero(bean, onBack, onEdit, { showDelete = true }, onPhotoTap = { bean.photoPath?.let { viewerPath = it } }) }
        item { TitleBlock(bean) }
        item { MetaRow(bean) }
        item { BuyRow(bean) }
        if (bean.notes.isNotBlank()) item { NotesBlock(bean.notes) }
        if (bean.backPhotoPath != null) item { BackPhotoBlock(bean.backPhotoPath) }
        item { BrewLogHeader(bean.brews.size) { showBrewSheet = true } }
        if (bean.brews.isEmpty()) {
            item {
                Text(
                    "No brews yet. Log the first cup.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Dim,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
            }
        } else {
            items(bean.brews.size) { i -> BrewRow(bean.brews[i]) }
        }
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            containerColor = Surface2,
            title = { Text("Take it off the shelf?", style = MaterialTheme.typography.titleLarge, color = Parchment) },
            text = { Text("This removes the bag, its photo, and its brew log.", color = Dim) },
            confirmButton = {
                TextButton(onClick = { showDelete = false; onDelete() }) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) { Text("Keep it", color = Dim) }
            },
        )
    }

    if (showBrewSheet) {
        LogBrewSheet(
            allBrews = allBrews,
            onDismiss = { showBrewSheet = false },
            onLog = { showBrewSheet = false; onLogBrew(it) },
        )
    }

    viewerPath?.let { PhotoViewerDialog(path = it) { viewerPath = null } }
}

@Composable
private fun Hero(bean: Bean, onBack: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit, onPhotoTap: () -> Unit) {
    val photo by PhotoStore.rememberPhoto(bean.photoPath, targetWidth = 1200)
    Box(Modifier.fillMaxWidth().height(380.dp)) {
        val p = photo
        if (p != null) {
            Image(
                bitmap = p,
                contentDescription = bean.name,
                // Cutout: show the whole silhouette on the roastery dark; photo: fill.
                contentScale = if (BagCropper.isCutout(bean.photoPath)) ContentScale.Fit else ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(if (BagCropper.isCutout(bean.photoPath)) 20.dp else 0.dp)
                    .clickable(onClick = onPhotoTap),
            )
        } else {
            Box(Modifier.fillMaxSize().background(SurfaceHigh), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.LocalCafe, contentDescription = null, tint = Dim, modifier = Modifier.size(64.dp))
            }
        }
        // scrim into the page background so the photo melts into the roastery dark
        Box(
            Modifier
                .fillMaxWidth()
                .height(140.dp)
                .align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Roast))),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(WindowInsets.statusBars.asPaddingValues())
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            ScrimIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack)
            Box(Modifier.weight(1f))
            ScrimIconButton(Icons.Default.Edit, "Edit bag", onEdit)
            ScrimIconButton(Icons.Default.Delete, "Remove bag", onDelete)
        }
    }
}

@Composable
private fun ScrimIconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, onClick: () -> Unit) {
    Surface(color = Roast.copy(alpha = 0.55f), shape = CircleShape, modifier = Modifier.padding(4.dp)) {
        IconButton(onClick = onClick) {
            Icon(icon, contentDescription = desc, tint = Parchment)
        }
    }
}

@Composable
private fun TitleBlock(bean: Bean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 20.dp, top = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            if (bean.roaster.isNotBlank()) Eyebrow(bean.roaster, color = Crema)
            Text(
                bean.name,
                style = MaterialTheme.typography.headlineMedium,
                color = Parchment,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        if (bean.rating > 0f) RoastStamp(bean.rating, size = 64.dp, modifier = Modifier.padding(start = 12.dp))
    }
}

@Composable
private fun MetaRow(bean: Bean) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            MetaCell("Origin", bean.origin)
            MetaCell("Roast", bean.roastLevel)
            MetaCell("Process", bean.process)
        }
        if (listOf(bean.producer, bean.variety, bean.elevation).any { it.isNotBlank() }) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier.padding(top = 16.dp),
            ) {
                MetaCell("Producer", bean.producer)
                MetaCell("Variety", bean.variety)
                MetaCell("Elevation", bean.elevation)
            }
        }
    }
}

@Composable
private fun MetaCell(label: String, value: String) {
    Column {
        Eyebrow(label)
        Text(
            value.ifBlank { "—" },
            style = MaterialTheme.typography.bodyLarge,
            color = Parchment,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** Restock actions: affiliate-tagged retailer links, or a maps search for stockists nearby. */
@Composable
private fun BuyRow(bean: Bean) {
    val context = LocalContext.current
    val query = listOf(bean.roaster, bean.name, "coffee").filter { it.isNotBlank() }.joinToString(" ")
    var shopMenu by remember { mutableStateOf(false) }
    Row(Modifier.padding(start = 16.dp, bottom = 8.dp)) {
        Box {
            TextButton(onClick = { shopMenu = true }) {
                Icon(Icons.Default.ShoppingCart, contentDescription = null, tint = Crema, modifier = Modifier.size(16.dp))
                Text("Shop online", color = Crema, modifier = Modifier.padding(start = 6.dp))
            }
            DropdownMenu(
                expanded = shopMenu,
                onDismissRequest = { shopMenu = false },
                containerColor = SurfaceHigh,
            ) {
                Affiliate.shopsFor(query).forEach { shop ->
                    DropdownMenuItem(
                        text = { Text(shop.label, color = Parchment) },
                        onClick = {
                            shopMenu = false
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(shop.url)))
                            }
                        },
                    )
                }
            }
        }
        TextButton(onClick = {
            val near = (bean.roaster.ifBlank { bean.name }) + " coffee"
            val geo = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(near)))
            runCatching { context.startActivity(geo) }.onFailure {
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/" + Uri.encode(near)))
                    )
                }
            }
        }) {
            Icon(Icons.Default.Storefront, contentDescription = null, tint = Crema, modifier = Modifier.size(16.dp))
            Text("Find nearby", color = Crema, modifier = Modifier.padding(start = 6.dp))
        }
    }
}

@Composable
private fun NotesBlock(notes: String) {
    Row(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 20.dp)) {
        Box(
            Modifier
                .width(3.dp)
                .height(44.dp)
                .background(Crema, RoundedCornerShape(2.dp)),
        )
        Text(
            notes,
            style = MaterialTheme.typography.bodyLarge,
            fontStyle = FontStyle.Italic,
            color = Parchment.copy(alpha = 0.85f),
            modifier = Modifier.padding(start = 14.dp),
        )
    }
}

@Composable
private fun BackPhotoBlock(path: String) {
    val photo by PhotoStore.rememberPhoto(path, targetWidth = 1000)
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 20.dp)) {
        Eyebrow("The back", Modifier.padding(bottom = 8.dp))
        val p = photo
        if (p != null) {
            Image(
                bitmap = p,
                contentDescription = "Back of bag",
                contentScale = if (expanded) ContentScale.FillWidth else ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (expanded) Modifier else Modifier.height(160.dp))
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { expanded = !expanded },
            )
        }
    }
}

@Composable
private fun BrewLogHeader(count: Int, onLog: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Eyebrow(if (count == 0) "Brew log" else "Brew log · $count", Modifier.weight(1f))
        TextButton(onClick = onLog) {
            Icon(Icons.Default.LocalCafe, contentDescription = null, tint = Crema, modifier = Modifier.size(16.dp))
            Text("Log a brew", color = Crema, modifier = Modifier.padding(start = 6.dp))
        }
    }
}

@Composable
private fun BrewRow(brew: Brew) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(color = Surface2, shape = RoundedCornerShape(6.dp)) {
            Text(
                brew.method,
                style = MaterialTheme.typography.labelLarge,
                color = Crema,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            if (brew.note.isNotBlank()) {
                Text(brew.note, style = MaterialTheme.typography.bodyMedium, color = Parchment)
            }
            // Recipe line: "18g → 300g (1:16.7) · Ode @ 6"
            val recipe = buildList {
                val d = brew.doseG
                val w = brew.waterG
                when {
                    d != null && w != null -> add("${formatGrams(d)}g → ${formatGrams(w)}g" +
                        (brew.ratio?.let { " ($it)" } ?: ""))
                    d != null -> add("${formatGrams(d)}g dose")
                    w != null -> add("${formatGrams(w)}g water")
                }
                if (brew.grinder.isNotBlank() || brew.grindSize.isNotBlank()) {
                    add(listOf(brew.grinder, brew.grindSize).filter { it.isNotBlank() }.joinToString(" @ "))
                }
            }.joinToString("  ·  ")
            if (recipe.isNotBlank()) {
                Text(
                    recipe,
                    style = MaterialTheme.typography.bodySmall,
                    color = Crema.copy(alpha = 0.8f),
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Text(
                dateFmt.format(Date(brew.timestamp)),
                style = MaterialTheme.typography.bodySmall,
                color = Dim,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        if (brew.rating > 0f) {
            Text(
                formatRating(brew.rating),
                style = MaterialTheme.typography.titleMedium,
                color = StampInk,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LogBrewSheet(allBrews: List<Brew>, onDismiss: () -> Unit, onLog: (Brew) -> Unit) {
    var method by rememberSaveable { mutableStateOf(BREW_METHODS.first()) }
    var rating by rememberSaveable { mutableStateOf(0f) }
    var note by rememberSaveable { mutableStateOf("") }
    var dose by rememberSaveable { mutableStateOf("") }
    var water by rememberSaveable { mutableStateOf("") }
    // Equipment rarely changes — prefill from the last brew; grind follows the method.
    var grinder by rememberSaveable {
        mutableStateOf(allBrews.firstOrNull { it.grinder.isNotBlank() }?.grinder ?: "")
    }
    var grindSize by rememberSaveable { mutableStateOf("") }
    var grindTouched by rememberSaveable { mutableStateOf(false) }

    fun prefillForMethod(m: String) {
        if (grindTouched) return
        allBrews.firstOrNull { it.method == m }?.let { last ->
            if (last.grindSize.isNotBlank()) grindSize = last.grindSize
            if (dose.isBlank() && last.doseG != null) dose = formatGrams(last.doseG)
            if (water.isBlank() && last.waterG != null) water = formatGrams(last.waterG)
        }
    }
    LaunchedEffect(Unit) { prefillForMethod(method) }

    val ratioPreview = run {
        val d = dose.toFloatOrNull()
        val w = water.toFloatOrNull()
        if (d != null && w != null && d > 0f && w > 0f) {
            val r = w / d
            "1:" + if (r % 1f < 0.05f || r % 1f > 0.95f) "%.0f".format(r) else "%.1f".format(r)
        } else null
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Surface2) {
        Column(
            Modifier
                .imePadding() // keep fields and button above the keyboard
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 36.dp),
        ) {
            Text("Log a brew", style = MaterialTheme.typography.titleLarge, color = Parchment)

            Eyebrow("Method", Modifier.padding(top = 18.dp, bottom = 6.dp))
            ChoiceChips(BREW_METHODS, method) { method = it; prefillForMethod(it) }

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 18.dp, bottom = 6.dp)) {
                Eyebrow("Recipe", Modifier.weight(1f))
                if (ratioPreview != null) {
                    Text(ratioPreview, style = MaterialTheme.typography.titleMedium, color = Crema)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BrewField(dose, { dose = it }, "Dose (g)", numeric = true, Modifier.weight(1f))
                BrewField(water, { water = it }, "Water / yield (g)", numeric = true, Modifier.weight(1f))
            }

            Eyebrow("Equipment", Modifier.padding(top = 16.dp, bottom = 6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BrewField(grinder, { grinder = it }, "Grinder", numeric = false, Modifier.weight(1.2f))
                BrewField(grindSize, { grindSize = it; grindTouched = true }, "Grind", numeric = false, Modifier.weight(1f))
            }

            Eyebrow("The cup", Modifier.padding(top = 16.dp, bottom = 4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                RoastStamp(rating = rating, size = 48.dp)
                RatingSlider(
                    value = rating,
                    onChange = { rating = it },
                    modifier = Modifier.weight(1f).padding(start = 16.dp),
                )
            }
            BrewField(
                note, { note = it }, "Tasted like…", numeric = false,
                Modifier.fillMaxWidth().padding(top = 10.dp), minLines = 2,
            )

            Button(
                onClick = {
                    onLog(
                        Brew(
                            id = UUID.randomUUID().toString(),
                            method = method,
                            rating = rating,
                            note = note.trim(),
                            timestamp = System.currentTimeMillis(),
                            doseG = dose.toFloatOrNull(),
                            waterG = water.toFloatOrNull(),
                            grinder = grinder.trim(),
                            grindSize = grindSize.trim(),
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = Crema, contentColor = Roast),
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp).height(50.dp),
            ) {
                Text("Log it")
            }
        }
    }
}

@Composable
private fun BrewField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    numeric: Boolean,
    modifier: Modifier = Modifier,
    minLines: Int = 1,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = minLines == 1,
        minLines = minLines,
        keyboardOptions = if (numeric) KeyboardOptions(keyboardType = KeyboardType.Decimal) else KeyboardOptions.Default,
        modifier = modifier,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Crema,
            unfocusedBorderColor = Dim.copy(alpha = 0.4f),
            focusedLabelColor = Crema,
            unfocusedLabelColor = Dim,
            cursorColor = Crema,
            focusedTextColor = Parchment,
            unfocusedTextColor = Parchment,
        ),
    )
}
