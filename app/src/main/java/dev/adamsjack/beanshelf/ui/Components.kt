package dev.adamsjack.beanshelf.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.getValue
import dev.adamsjack.beanshelf.data.PhotoStore
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.adamsjack.beanshelf.model.formatRating

/** Full-screen photo viewer — tap anywhere to close. Just looking, no editing. */
@Composable
fun PhotoViewerDialog(path: String, onDismiss: () -> Unit) {
    val photo by PhotoStore.rememberPhoto(path, targetWidth = 1600)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.95f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            photo?.let {
                Image(
                    bitmap = it,
                    contentDescription = "Bag photo",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(12.dp),
                )
            }
        }
    }
}

/** Letter-spaced uppercase metadata label — the bag-label vernacular. */
@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier, color: Color = Dim) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = modifier,
    )
}

/**
 * The signature element: a rotated circular "roast stamp" holding the rating.
 * Double ring + serif numerals on a dark scrim, like an inked stamp on kraft paper.
 */
@Composable
fun RoastStamp(rating: Float, size: Dp, modifier: Modifier = Modifier) {
    val fontSize = with(size.value) { (this * 0.30f).sp }
    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer { rotationZ = -12f },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size)) {
            val r = this.size.minDimension / 2f
            drawCircle(color = Roast.copy(alpha = 0.88f), radius = r)
            drawCircle(color = StampInk, radius = r - 1.5.dp.toPx(), style = Stroke(width = 2.dp.toPx()))
            drawCircle(color = StampInk.copy(alpha = 0.7f), radius = r - 5.5.dp.toPx(), style = Stroke(width = 1.dp.toPx()))
        }
        Text(
            text = if (rating <= 0f) "—" else formatRating(rating),
            color = StampInk,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold,
            fontSize = fontSize,
        )
    }
}

/** Quarter-step 0–5 rating slider with a live stamp preview beside it. */
@Composable
fun RatingSlider(value: Float, onChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    Slider(
        value = value,
        onValueChange = { onChange((it * 4).toInt() / 4f) },
        valueRange = 0f..5f,
        steps = 19,
        modifier = modifier,
        colors = SliderDefaults.colors(
            thumbColor = Crema,
            activeTrackColor = Crema,
            inactiveTrackColor = SurfaceHigh,
            activeTickColor = Roast.copy(alpha = 0.3f),
            inactiveTickColor = Dim.copy(alpha = 0.2f),
        ),
    )
}

/**
 * A wooden shelf plank: thin lit top edge, gradient front face, soft shadow below.
 * Drawn full-bleed; bags sit on top of it.
 */
@Composable
fun ShelfPlank(modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().height(30.dp)) {
        val w = this.size.width
        val edge = 3.dp.toPx()
        val face = 18.dp.toPx()
        // lit top edge
        drawRect(color = PlankEdge, size = androidx.compose.ui.geometry.Size(w, edge))
        // front face
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(PlankLight, PlankDark),
                startY = edge,
                endY = edge + face,
            ),
            topLeft = Offset(0f, edge),
            size = androidx.compose.ui.geometry.Size(w, face),
        )
        // faint wood grain on the face
        val grainY = listOf(0.35f, 0.62f)
        grainY.forEach { fy ->
            drawLine(
                color = PlankDark.copy(alpha = 0.5f),
                start = Offset(0f, edge + face * fy),
                end = Offset(w, edge + face * fy),
                strokeWidth = 1f,
            )
        }
        // shadow cast below the plank
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent),
                startY = edge + face,
                endY = this.size.height,
            ),
            topLeft = Offset(0f, edge + face),
            size = androidx.compose.ui.geometry.Size(w, this.size.height - edge - face),
        )
    }
}
