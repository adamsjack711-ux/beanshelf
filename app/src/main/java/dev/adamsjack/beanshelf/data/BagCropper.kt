package dev.adamsjack.beanshelf.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.DetectedObject
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.math.max
import kotlin.math.min

/**
 * Crops a bag photo to its prominent subject (on-device ML Kit object detection,
 * bundled model). Rewrites the file in place; a failed or implausible detection
 * leaves the photo untouched. Must run BEFORE the path is handed to the UI so
 * the decode cache never sees the uncropped version.
 */
object BagCropper {

    private const val PADDING = 0.05f
    private const val JPEG_QUALITY = 88

    suspend fun cropToSubject(context: Context, path: String): Unit = withContext(Dispatchers.IO) {
        runCatching {
            val bmp = BitmapFactory.decodeFile(path) ?: return@runCatching
            val detector = ObjectDetection.getClient(
                ObjectDetectorOptions.Builder()
                    .setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE)
                    .build()
            )
            val objects = suspendCancellableCoroutine<List<DetectedObject>> { cont ->
                detector.process(InputImage.fromBitmap(bmp, 0))
                    .addOnSuccessListener { if (cont.isActive) cont.resume(it) }
                    .addOnFailureListener { if (cont.isActive) cont.resume(emptyList()) }
            }
            detector.close()

            val box = objects.firstOrNull()?.boundingBox ?: return@runCatching
            val padX = (box.width() * PADDING).toInt()
            val padY = (box.height() * PADDING).toInt()
            val left = max(0, box.left - padX)
            val top = max(0, box.top - padY)
            val right = min(bmp.width, box.right + padX)
            val bottom = min(bmp.height, box.bottom + padY)
            val w = right - left
            val h = bottom - top

            // Implausibly small → probably grabbed a scoop or a shadow, not the bag.
            if (w < bmp.width * 0.25f || h < bmp.height * 0.25f) return@runCatching
            // Already effectively full frame → nothing to gain.
            if (w > bmp.width * 0.95f && h > bmp.height * 0.95f) return@runCatching

            val cropped = Bitmap.createBitmap(bmp, left, top, w, h)
            java.io.File(path).outputStream().use {
                cropped.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it)
            }
        }
    }
}
