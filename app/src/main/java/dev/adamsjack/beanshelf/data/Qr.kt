package dev.adamsjack.beanshelf.data

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** QR encoder for the shareable profile link. Crema-on-roast to match the app. */
object Qr {
    private const val DARK = 0xFF17100B.toInt()   // Roast
    private const val LIGHT = 0xFFF0E4D2.toInt()   // Parchment

    fun encode(text: String, sizePx: Int = 640): androidx.compose.ui.graphics.ImageBitmap? = runCatching {
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 1,
        )
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        for (y in 0 until sizePx) for (x in 0 until sizePx) {
            bmp.setPixel(x, y, if (matrix[x, y]) DARK else LIGHT)
        }
        bmp.asImageBitmap()
    }.getOrNull()
}
