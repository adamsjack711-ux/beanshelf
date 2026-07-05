package dev.adamsjack.beanshelf.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import dev.adamsjack.beanshelf.model.Bean
import dev.adamsjack.beanshelf.model.formatRating
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Renders a bean as a 1080x1350 share card — the roastery-dark look with the
 * bag photo, serif name, roast stamp, and tasting notes. Written to
 * cacheDir/share for FileProvider hand-off to any app.
 */
object ShareCard {

    private const val W = 1080
    private const val H = 1350
    private val ROAST = Color.parseColor("#17100B")
    private val PARCHMENT = Color.parseColor("#F0E4D2")
    private val DIM = Color.parseColor("#A38B72")
    private val CREMA = Color.parseColor("#D9A468")
    private val STAMP_INK = Color.parseColor("#E2B679")
    private val PLANK_LIGHT = Color.parseColor("#5C3F22")
    private val PLANK_DARK = Color.parseColor("#2C1B0C")

    suspend fun render(context: Context, bean: Bean): File = withContext(Dispatchers.Default) {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(ROAST)

        // ── photo ───────────────────────────────────────────────────────
        val photoBottom = 800f
        val photo = bean.photoPath?.let { BitmapFactory.decodeFile(it) }
        if (photo != null) {
            if (BagCropper.isCutout(bean.photoPath)) {
                // plank under the free-standing bag
                val plank = Paint()
                plank.shader = android.graphics.LinearGradient(
                    0f, photoBottom - 8f, 0f, photoBottom + 34f,
                    PLANK_LIGHT, PLANK_DARK, android.graphics.Shader.TileMode.CLAMP,
                )
                c.drawRect(0f, photoBottom - 8f, W.toFloat(), photoBottom + 34f, plank)
                val maxH = 640f
                val maxW = 760f
                val scale = minOf(maxW / photo.width, maxH / photo.height)
                val dw = photo.width * scale
                val dh = photo.height * scale
                val left = (W - dw) / 2f
                val top = photoBottom - dh
                c.drawBitmap(photo, null, RectF(left, top, left + dw, top + dh), Paint(Paint.FILTER_BITMAP_FLAG))
            } else {
                // cover-crop into a rounded frame
                val frame = RectF(60f, 60f, W - 60f, photoBottom)
                val path = android.graphics.Path().apply { addRoundRect(frame, 28f, 28f, android.graphics.Path.Direction.CW) }
                c.save()
                c.clipPath(path)
                val scale = maxOf(frame.width() / photo.width, frame.height() / photo.height)
                val dw = photo.width * scale
                val dh = photo.height * scale
                val left = frame.left + (frame.width() - dw) / 2f
                val top = frame.top + (frame.height() - dh) / 2f
                c.drawBitmap(photo, null, RectF(left, top, left + dw, top + dh), Paint(Paint.FILTER_BITMAP_FLAG))
                c.restore()
            }
        }

        // ── roast stamp ─────────────────────────────────────────────────
        if (bean.rating > 0f) {
            c.save()
            c.rotate(-12f, 920f, photoBottom - 40f)
            val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ROAST; alpha = 235 }
            c.drawCircle(920f, photoBottom - 40f, 96f, fill)
            val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = STAMP_INK; style = Paint.Style.STROKE; strokeWidth = 5f
            }
            c.drawCircle(920f, photoBottom - 40f, 92f, ring)
            ring.strokeWidth = 2.5f
            ring.alpha = 180
            c.drawCircle(920f, photoBottom - 40f, 78f, ring)
            val num = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = STAMP_INK
                typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
                textSize = 58f
                textAlign = Paint.Align.CENTER
            }
            c.drawText(formatRating(bean.rating), 920f, photoBottom - 20f, num)
            c.restore()
        }

        // ── text block ──────────────────────────────────────────────────
        var y = photoBottom + 96f
        if (bean.roaster.isNotBlank()) {
            val eyebrow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = CREMA; textSize = 34f; letterSpacing = 0.18f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            }
            c.drawText(bean.roaster.uppercase(), 72f, y, eyebrow)
            y += 26f
        }
        val namePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = PARCHMENT; textSize = 76f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        }
        val nameLayout = StaticLayout.Builder
            .obtain(bean.name, 0, bean.name.length, namePaint, W - 144)
            .setMaxLines(2).setEllipsize(android.text.TextUtils.TruncateAt.END)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL).build()
        c.save(); c.translate(72f, y); nameLayout.draw(c); c.restore()
        y += nameLayout.height + 34f

        val meta = listOf(bean.origin, bean.variety, bean.process).filter { it.isNotBlank() }
            .joinToString("  ·  ")
        if (meta.isNotBlank()) {
            val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = DIM; textSize = 38f }
            c.drawText(meta, 72f, y, metaPaint)
            y += 64f
        }
        if (bean.notes.isNotBlank()) {
            val notesPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(220, 240, 228, 210); textSize = 42f
                typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
            }
            val notesLayout = StaticLayout.Builder
                .obtain(bean.notes, 0, bean.notes.length, notesPaint, W - 144)
                .setMaxLines(3).setEllipsize(android.text.TextUtils.TruncateAt.END)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL).build()
            c.save(); c.translate(72f, y); notesLayout.draw(c); c.restore()
        }

        // ── footer wordmark ─────────────────────────────────────────────
        val footer = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = DIM; textSize = 30f; letterSpacing = 0.32f; textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        c.drawText("BEANSHELF", W / 2f, H - 52f, footer)

        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val out = File(dir, "beanshelf-card-${bean.id.take(8)}.png")
        out.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        photo?.recycle()
        bmp.recycle()
        out
    }
}
