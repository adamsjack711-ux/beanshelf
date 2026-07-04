package dev.adamsjack.beanshelf.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.DetectedObject
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.math.max
import kotlin.math.min

/**
 * Isolates the bag in a photo. Preferred path: ML Kit subject segmentation —
 * removes the background entirely and saves a transparent PNG cutout (path
 * changes .jpg → .png). Fallback (model not yet downloaded / low confidence):
 * rectangular crop to the detected object's box, same JPEG path.
 *
 * Returns the path to display — callers must use the returned path, and must
 * call this BEFORE handing any path to the UI so the decode cache stays clean.
 */
object BagCropper {

    private const val PADDING = 0.05f
    private const val JPEG_QUALITY = 88

    /** Cutout PNGs render shelf-style (no card, ground shadow); JPEGs get the card look. */
    fun isCutout(path: String?): Boolean = path?.endsWith(".png") == true

    suspend fun cutOutBag(context: Context, path: String): String = withContext(Dispatchers.Default) {
        val bmp = BitmapFactory.decodeFile(path) ?: return@withContext path
        segmentSubject(bmp)?.let { cutout ->
            trimTransparentBounds(cutout)?.let { trimmed ->
                // Plausibility: a bag fills a decent chunk of a deliberate photo.
                if (trimmed.width >= bmp.width * 0.2f || trimmed.height >= bmp.height * 0.2f) {
                    val pngFile = File(path.removeSuffix(".jpg") + ".png")
                    pngFile.outputStream().use {
                        trimmed.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                    if (pngFile.absolutePath != path) File(path).delete()
                    return@withContext pngFile.absolutePath
                }
            }
        }
        rectangleCrop(bmp, path)
        path
    }

    /** Foreground-with-alpha bitmap from subject segmentation, or null if unavailable. */
    private suspend fun segmentSubject(bmp: Bitmap): Bitmap? = runCatching {
        val segmenter = SubjectSegmentation.getClient(
            SubjectSegmenterOptions.Builder()
                .enableForegroundBitmap()
                .build()
        )
        val result = suspendCancellableCoroutine<Bitmap?> { cont ->
            segmenter.process(InputImage.fromBitmap(bmp, 0))
                .addOnSuccessListener { if (cont.isActive) cont.resume(it.foregroundBitmap) }
                .addOnFailureListener { if (cont.isActive) cont.resume(null) }
        }
        segmenter.close()
        result
    }.getOrNull()

    /** Crops a transparent-background bitmap to its opaque bounds (small padding). */
    private fun trimTransparentBounds(bmp: Bitmap): Bitmap? {
        val w = bmp.width
        val h = bmp.height
        val pixels = IntArray(w * h)
        bmp.getPixels(pixels, 0, w, 0, 0, w, h)

        var left = w; var right = -1; var top = h; var bottom = -1
        val step = 2 // every other pixel is plenty at ≤1600px
        var y = 0
        while (y < h) {
            var x = 0
            val row = y * w
            while (x < w) {
                if ((pixels[row + x] ushr 24) > 24) { // alpha above noise floor
                    if (x < left) left = x
                    if (x > right) right = x
                    if (y < top) top = y
                    if (y > bottom) bottom = y
                }
                x += step
            }
            y += step
        }
        if (right < 0 || bottom < 0) return null

        val padX = ((right - left) * 0.03f).toInt()
        val padY = ((bottom - top) * 0.03f).toInt()
        left = max(0, left - padX)
        top = max(0, top - padY)
        right = min(w - 1, right + padX)
        bottom = min(h - 1, bottom + padY)
        if (right - left < 32 || bottom - top < 32) return null
        return Bitmap.createBitmap(bmp, left, top, right - left + 1, bottom - top + 1)
    }

    /** Fallback: crop the JPEG in place to the prominent object's bounding box. */
    private suspend fun rectangleCrop(bmp: Bitmap, path: String) {
        runCatching {
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

            val box = objects.firstOrNull()?.boundingBox ?: return
            val padX = (box.width() * PADDING).toInt()
            val padY = (box.height() * PADDING).toInt()
            val left = max(0, box.left - padX)
            val top = max(0, box.top - padY)
            val right = min(bmp.width, box.right + padX)
            val bottom = min(bmp.height, box.bottom + padY)
            val w = right - left
            val h = bottom - top

            if (w < bmp.width * 0.25f || h < bmp.height * 0.25f) return
            if (w > bmp.width * 0.95f && h > bmp.height * 0.95f) return

            val cropped = Bitmap.createBitmap(bmp, left, top, w, h)
            File(path).outputStream().use {
                cropped.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it)
            }
        }
    }
}
