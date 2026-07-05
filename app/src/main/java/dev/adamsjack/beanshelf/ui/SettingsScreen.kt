package dev.adamsjack.beanshelf.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.adamsjack.beanshelf.data.ThemePrefs

/** Appearance settings — pick a palette; applies live and persists. */
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val activeKey = ThemeHolder.palette.key

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 40.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 0.dp, top = 12.dp)) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Parchment)
                    }
                    Text("Appearance", style = MaterialTheme.typography.headlineMedium, color = Parchment)
                }
                Eyebrow("Theme", Modifier.padding(start = 4.dp, top = 4.dp, bottom = 4.dp))
                Text(
                    "Pick a look. Your shelf and everything else stay the same — just the colors change.",
                    style = MaterialTheme.typography.bodyMedium, color = Dim,
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
                )
            }
        }
        items(Palettes.all) { palette ->
            PaletteCard(palette, selected = palette.key == activeKey) {
                setPalette(palette)
                ThemePrefs.saveKey(context, palette.key)
            }
        }
    }
}

@Composable
private fun PaletteCard(palette: Palette, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(palette.background)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) Crema else palette.textMuted.copy(alpha = 0.3f),
                shape = RoundedCornerShape(14.dp),
            )
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        // mini mock: a bag card + a shelf plank + swatches, in the palette's own colors
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1.5f)
                .clip(RoundedCornerShape(8.dp))
                .background(palette.surface),
            contentAlignment = Alignment.BottomCenter,
        ) {
            // a little "bag" standing on the plank
            Box(
                Modifier
                    .padding(bottom = 8.dp)
                    .size(width = 30.dp, height = 40.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(palette.surfaceHigh),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(16.dp).clip(CircleShape).background(palette.accent))
            }
            Box(Modifier.fillMaxWidth().height(6.dp).background(palette.plankLight))
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                palette.label,
                fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold,
                color = palette.textPrimary,
                modifier = Modifier.weight(1f),
            )
            if (selected) {
                Box(Modifier.size(22.dp).clip(CircleShape).background(palette.accent), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Check, contentDescription = "Selected", tint = palette.onAccent, modifier = Modifier.size(14.dp))
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(palette.accent, palette.plankLight, palette.textPrimary).forEach {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(it))
                    }
                }
            }
        }
    }
}
