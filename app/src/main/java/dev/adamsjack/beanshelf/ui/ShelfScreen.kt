package dev.adamsjack.beanshelf.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.adamsjack.beanshelf.data.BagCropper
import dev.adamsjack.beanshelf.data.PhotoStore
import dev.adamsjack.beanshelf.model.Bean
import dev.adamsjack.beanshelf.model.formatRating

@Composable
fun ShelfScreen(
    beans: List<Bean>,
    onAdd: () -> Unit,
    onOpen: (Bean) -> Unit,
    onLeaderboard: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAdd,
                containerColor = Crema,
                contentColor = Roast,
            ) {
                Icon(Icons.Default.PhotoCamera, contentDescription = "Add a bag")
            }
        },
    ) { padding ->
        if (beans.isEmpty()) {
            EmptyShelf(onAdd = onAdd, modifier = Modifier.fillMaxSize().padding(padding))
        } else {
            val rows = remember(beans) { beans.chunked(3) }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = padding.calculateTopPadding(),
                    bottom = padding.calculateBottomPadding() + 96.dp,
                ),
            ) {
                item { ShelfHeader(beans, onLeaderboard) }
                items(rows.size) { i -> ShelfRow(rows[i], onOpen) }
            }
        }
    }
}

@Composable
private fun ShelfHeader(beans: List<Bean>, onLeaderboard: () -> Unit) {
    Column(Modifier.padding(start = 24.dp, end = 16.dp, top = 28.dp, bottom = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Beanshelf",
                style = MaterialTheme.typography.headlineLarge,
                color = Parchment,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onLeaderboard) {
                Icon(Icons.Default.EmojiEvents, contentDescription = "Leaderboard", tint = Crema)
            }
        }
        val rated = beans.filter { it.rating > 0f }
        val sub = buildString {
            append("${beans.size} ")
            append(if (beans.size == 1) "bag" else "bags")
            if (rated.isNotEmpty()) {
                append("  ·  avg ${formatRating(rated.map { it.rating }.average().toFloat())}")
            }
        }
        Eyebrow(sub, Modifier.padding(top = 6.dp))
    }
}

/** One shelf: up to three bags standing on a plank. */
@Composable
private fun ShelfRow(rowBeans: List<Bean>, onOpen: (Bean) -> Unit) {
    Box(Modifier.fillMaxWidth().height(224.dp)) {
        ShelfPlank(Modifier.align(Alignment.BottomCenter))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(start = 18.dp, end = 18.dp, bottom = 26.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            rowBeans.forEach { bean ->
                BagCard(bean, Modifier.weight(1f)) { onOpen(bean) }
            }
            repeat(3 - rowBeans.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun BagCard(bean: Bean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    // Seeded tilt per bag so the shelf reads as physical objects, not a grid.
    val tilt = remember(bean.id) { ((bean.id.hashCode() % 100) / 100f) * 3f - 1.5f }
    val photo by PhotoStore.rememberPhoto(bean.photoPath, targetWidth = 400)
    val cutout = BagCropper.isCutout(bean.photoPath)
    Box(
        modifier = modifier
            .aspectRatio(0.76f)
            .graphicsLayer {
                rotationZ = tilt
                transformOrigin = TransformOrigin(0.5f, 1f)
            }
            .then(
                // Cutout bags are free-standing silhouettes: no card chrome,
                // just a soft ground shadow where they meet the plank.
                if (cutout) Modifier.drawBehind {
                    val ow = size.width * 0.78f
                    val oh = 16.dp.toPx()
                    drawOval(
                        brush = Brush.radialGradient(
                            colors = listOf(Color.Black.copy(alpha = 0.5f), Color.Transparent),
                            center = Offset(size.width / 2f, size.height - oh / 2f),
                            radius = ow / 2f,
                        ),
                        topLeft = Offset((size.width - ow) / 2f, size.height - oh),
                        size = androidx.compose.ui.geometry.Size(ow, oh),
                    )
                }
                else Modifier
                    .shadow(10.dp, RoundedCornerShape(7.dp), clip = false)
                    .clip(RoundedCornerShape(7.dp))
                    .background(SurfaceHigh)
            )
            .clickable(onClick = onClick),
    ) {
        val p = photo
        if (p != null) {
            Image(
                bitmap = p,
                contentDescription = bean.name,
                contentScale = if (cutout) ContentScale.Fit else ContentScale.Crop,
                alignment = Alignment.BottomCenter,
                modifier = Modifier.fillMaxSize().padding(bottom = if (cutout) 2.dp else 0.dp),
            )
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Default.LocalCafe, contentDescription = null, tint = Dim, modifier = Modifier.size(28.dp))
                Text(
                    bean.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = Dim,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
        if (bean.rating > 0f) {
            RoastStamp(
                rating = bean.rating,
                size = 42.dp,
                modifier = Modifier.align(Alignment.BottomEnd).padding(5.dp),
            )
        }
    }
}

@Composable
private fun EmptyShelf(onAdd: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // one bare plank, waiting
        ShelfPlank(Modifier.fillMaxWidth())
        Text(
            "The shelf is empty",
            style = MaterialTheme.typography.headlineMedium,
            color = Parchment,
            modifier = Modifier.padding(top = 36.dp),
        )
        Text(
            "Photograph a bag of beans to start your collection.",
            style = MaterialTheme.typography.bodyLarge,
            color = Dim,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 10.dp, bottom = 28.dp),
        )
        Button(
            onClick = onAdd,
            colors = ButtonDefaults.buttonColors(containerColor = Crema, contentColor = Roast),
        ) {
            Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
            Text("Add your first bag", modifier = Modifier.padding(start = 8.dp))
        }
    }
}
