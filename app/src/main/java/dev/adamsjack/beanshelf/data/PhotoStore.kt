package dev.adamsjack.beanshelf.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Photo pipeline: import (downscale to <=1600px, EXIF-upright, recompress) into
 * filesDir/photos, plus a cached decoder for showing photos at grid/detail sizes.
 */
object PhotoStore {

    private const val MAX_IMPORT_DIM = 1600
    private const val JPEG_QUALITY = 88

    private val cache = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 4).toInt(),
    ) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    fun photosDir(context: Context): File =
        File(context.filesDir, "photos").apply { mkdirs() }

    fun cameraCaptureFile(context: Context): File {
        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
        return File(dir, "capture.jpg")
    }

    /** Import a JPEG file (camera capture): returns the stored absolute path. */
    suspend fun importFile(context: Context, src: File): String? = withContext(Dispatchers.IO) {
        runCatching {
            val upright = decodeUpright(src, MAX_IMPORT_DIM) ?: return@runCatching null
            val dest = File(photosDir(context), "${UUID.randomUUID()}.jpg")
            dest.outputStream().use { upright.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
            upright.recycle()
            dest.absolutePath
        }.getOrNull()
    }

    /** Import from a gallery content URI (copied to cache first, then treated as a file). */
    suspend fun importUri(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val tmp = File(context.cacheDir, "import-${UUID.randomUUID()}.jpg")
            context.contentResolver.openInputStream(uri)?.use { input ->
                tmp.outputStream().use { input.copyTo(it) }
            } ?: return@runCatching null
            val path = importFile(context, tmp)
            tmp.delete()
            path
        }.getOrNull()
    }

    fun deletePhoto(path: String?) {
        path?.let { runCatching { File(it).delete() } }
    }

    private fun decodeUpright(src: File, maxDim: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(src.absolutePath, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxDim || bounds.outHeight / (sample * 2) >= maxDim) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val raw = BitmapFactory.decodeFile(src.absolutePath, opts) ?: return null

        val orientation = runCatching {
            ExifInterface(src.absolutePath)
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (degrees == 0f) return raw
        val m = Matrix().apply { postRotate(degrees) }
        val rotated = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
        if (rotated != raw) raw.recycle()
        return rotated
    }

    private fun loadScaled(path: String, targetWidth: Int): Bitmap? {
        val key = "$path@$targetWidth"
        cache.get(key)?.let { return it }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= targetWidth) sample *= 2
        val bmp = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return null
        cache.put(key, bmp)
        return bmp
    }

    @Composable
    fun rememberPhoto(path: String?, targetWidth: Int): State<ImageBitmap?> =
        produceState<ImageBitmap?>(initialValue = null, path, targetWidth) {
            value = if (path == null) null else withContext(Dispatchers.IO) {
                loadScaled(path, targetWidth)?.asImageBitmap()
            }
        }
}
