package dev.adamsjack.beanshelf.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.adamsjack.beanshelf.data.PhotoStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Full-screen manual crop: drag inside the frame to move it, drag a corner to
 * resize. Crop writes a NEW file (old one is deleted) so every cache and
 * composable refreshes off the path change. PNG stays PNG (cutout alpha kept).
 */
@Composable
fun CropDialog(path: String, onDone: (String?) -> Unit) {
    val scope = rememberCoroutineScope()
    var rotation by remember { mutableStateOf(0) } // 0/90/180/270, clockwise
    val loaded by produceState<Bitmap?>(initialValue = null, path) {
        value = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(path) }
    }
    // Camera EXIF is sometimes wrong — the Rotate button makes straightening deterministic.
    val bitmap = remember(loaded, rotation) {
        val src = loaded
        if (src == null || rotation == 0) src
        else Bitmap.createBitmap(
            src, 0, 0, src.width, src.height,
            android.graphics.Matrix().apply { postRotate(rotation.toFloat()) }, true,
        )
    }

    Dialog(
        onDismissRequest = { onDone(null) },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.96f))) {
            val bmp = bitmap ?: return@Box
            var container by remember { mutableStateOf(IntSize.Zero) }
            // Fit-rect of the displayed image inside the container.
            val imageRect = remember(container, bmp) {
                if (container == IntSize.Zero) Rect.Zero else {
                    val scale = min(
                        container.width / bmp.width.toFloat(),
                        container.height / bmp.height.toFloat(),
                    )
                    val w = bmp.width * scale
                    val h = bmp.height * scale
                    val l = (container.width - w) / 2f
                    val t = (container.height - h) / 2f
                    Rect(l, t, l + w, t + h)
                }
            }
            var crop by remember(imageRect) { mutableStateOf(imageRect) }
            val touchRadius = with(LocalDensity.current) { 36.dp.toPx() }
            val minSide = with(LocalDensity.current) { 56.dp.toPx() }

            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "Crop photo",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 96.dp)
                    .onSizeChanged { container = it },
            )

            if (imageRect != Rect.Zero) {
                Canvas(
                    Modifier
                        .fillMaxSize()
                        .padding(bottom = 96.dp)
                        .pointerInput(imageRect) {
                            var mode = ""
                            detectDragGesturesLocal(
                                onStart = { pos ->
                                    val corners = mapOf(
                                        "tl" to Offset(crop.left, crop.top),
                                        "tr" to Offset(crop.right, crop.top),
                                        "bl" to Offset(crop.left, crop.bottom),
                                        "br" to Offset(crop.right, crop.bottom),
                                    )
                                    mode = corners.entries
                                        .firstOrNull { (it.value - pos).getDistance() < touchRadius }
                                        ?.key
                                        ?: if (crop.contains(pos)) "move" else ""
                                },
                                onDrag = { drag ->
                                    val r = crop
                                    crop = when (mode) {
                                        "move" -> {
                                            val dx = drag.x.coerceIn(imageRect.left - r.left, imageRect.right - r.right)
                                            val dy = drag.y.coerceIn(imageRect.top - r.top, imageRect.bottom - r.bottom)
                                            r.translate(dx, dy)
                                        }
                                        "tl" -> Rect(
                                            (r.left + drag.x).coerceIn(imageRect.left, r.right - minSide),
                                            (r.top + drag.y).coerceIn(imageRect.top, r.bottom - minSide),
                                            r.right, r.bottom,
                                        )
                                        "tr" -> Rect(
                                            r.left,
                                            (r.top + drag.y).coerceIn(imageRect.top, r.bottom - minSide),
                                            (r.right + drag.x).coerceIn(r.left + minSide, imageRect.right),
                                            r.bottom,
                                        )
                                        "bl" -> Rect(
                                            (r.left + drag.x).coerceIn(imageRect.left, r.right - minSide),
                                            r.top, r.right,
                                            (r.bottom + drag.y).coerceIn(r.top + minSide, imageRect.bottom),
                                        )
                                        "br" -> Rect(
                                            r.left, r.top,
                                            (r.right + drag.x).coerceIn(r.left + minSide, imageRect.right),
                                            (r.bottom + drag.y).coerceIn(r.top + minSide, imageRect.bottom),
                                        )
                                        else -> r
                                    }
                                },
                            )
                        },
                ) {
                    // dim everything outside the crop frame
                    drawRect(Color.Black.copy(alpha = 0.55f), Offset.Zero, Size(size.width, crop.top))
                    drawRect(Color.Black.copy(alpha = 0.55f), Offset(0f, crop.bottom), Size(size.width, size.height - crop.bottom))
                    drawRect(Color.Black.copy(alpha = 0.55f), Offset(0f, crop.top), Size(crop.left, crop.height))
                    drawRect(Color.Black.copy(alpha = 0.55f), Offset(crop.right, crop.top), Size(size.width - crop.right, crop.height))
                    drawRect(Crema, crop.topLeft, crop.size, style = Stroke(2.dp.toPx()))
                    listOf(
                        Offset(crop.left, crop.top), Offset(crop.right, crop.top),
                        Offset(crop.left, crop.bottom), Offset(crop.right, crop.bottom),
                    ).forEach { drawCircle(Crema, radius = 7.dp.toPx(), center = it) }
                }
            }

            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { onDone(null) }) { Text("Cancel", color = Dim) }
                TextButton(onClick = { rotation = (rotation + 90) % 360 }) {
                    Text("Rotate ⟳", color = Crema)
                }
                TextButton(onClick = { crop = imageRect; rotation = 0 }, modifier = Modifier.padding(start = 4.dp)) {
                    Text("Reset", color = Parchment)
                }
                Box(Modifier.weight(1f))
                Button(
                    onClick = {
                        scope.launch {
                            val newPath = withContext(Dispatchers.IO) {
                                applyCrop(bmp, path, crop, imageRect, force = rotation != 0)
                            }
                            onDone(newPath)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Crema, contentColor = Roast),
                ) { Text("Save") }
            }
        }
    }
}

/** Maps the display-space frame back to bitmap pixels and writes a new file. */
private fun applyCrop(bmp: Bitmap, oldPath: String, crop: Rect, imageRect: Rect, force: Boolean = false): String? =
    runCatching {
        val scale = bmp.width / imageRect.width
        val l = ((crop.left - imageRect.left) * scale).toInt().coerceIn(0, bmp.width - 1)
        val t = ((crop.top - imageRect.top) * scale).toInt().coerceIn(0, bmp.height - 1)
        val w = (crop.width * scale).toInt().coerceIn(1, bmp.width - l)
        val h = (crop.height * scale).toInt().coerceIn(1, bmp.height - t)
        // No-op crop (and no rotation) → keep the original file.
        if (!force && l == 0 && t == 0 && abs(w - bmp.width) < 4 && abs(h - bmp.height) < 4) return@runCatching oldPath

        val cropped = Bitmap.createBitmap(bmp, l, t, w, h)
        val old = File(oldPath)
        val isPng = oldPath.endsWith(".png")
        val newFile = File(old.parentFile, "${UUID.randomUUID()}${if (isPng) ".png" else ".jpg"}")
        newFile.outputStream().use {
            cropped.compress(
                if (isPng) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG,
                if (isPng) 100 else 88,
                it,
            )
        }
        old.delete()
        newFile.absolutePath
    }.getOrNull()

/** Minimal drag detector with a start callback. */
private suspend fun PointerInputScope.detectDragGesturesLocal(
    onStart: (Offset) -> Unit,
    onDrag: (Offset) -> Unit,
) {
    detectDragGestures(
        onDragStart = { onStart(it) },
        onDrag = { change, amount ->
            change.consume()
            onDrag(amount)
        },
    )
}
