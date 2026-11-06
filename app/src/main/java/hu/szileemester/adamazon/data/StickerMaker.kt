package hu.szileemester.adamazon.data

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import java.io.ByteArrayOutputStream
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/** A sticker made from a photo; [cutOut] is false when no product could be found in it. */
class Sticker(val image: ByteArray, val cutOut: Boolean)

/**
 * Both versions of a photo: the cut-out (null if none was found) and the round crop.
 * [modelMissing]: Play services is still downloading the cut-out model (first use on a device).
 */
class StickerResult(val cutOut: Sticker?, val round: Sticker, val modelMissing: Boolean)

/**
 * Turns a product photo into an emoji-sized sticker: the product is cut out of its background on
 * the device (ML Kit subject segmentation, no network), outlined in white and given a soft shadow,
 * like the stickers of messaging apps. Without a recognisable product it falls back to a round crop.
 */
class StickerMaker(private val images: ImageCompressor, private val web: WebImages) {

    suspend fun make(uri: Uri): StickerResult =
        make(withContext(Dispatchers.Default) { images.decode(uri, maxSide = PHOTO_SIZE) })

    /**
     * From an image or product-page link; null if no image was found there. A picture that is
     * already cut out (a logo PNG or SVG with a transparent background) only gets the outline.
     */
    suspend fun fromLink(link: String): StickerResult? {
        val bytes = web.fromLink(link) ?: return null
        val picture = web.draw(bytes, PHOTO_SIZE) ?: return null
        if (!hasTransparentBackground(picture)) return make(picture)
        return withContext(Dispatchers.Default) {
            val cutOut = trimmed(picture)?.let { Sticker(encode(outlined(it)), cutOut = true) }
            StickerResult(cutOut, Sticker(encode(roundCrop(picture)), cutOut = false), modelMissing = false)
        }
    }

    /** A clear corner means the background is already gone. */
    private fun hasTransparentBackground(bitmap: Bitmap): Boolean =
        bitmap.hasAlpha() && listOf(0 to 0, bitmap.width - 1 to 0, 0 to bitmap.height - 1, bitmap.width - 1 to bitmap.height - 1)
            .count { (x, y) -> Color.alpha(bitmap.getPixel(x, y)) < ALPHA_THRESHOLD } >= 2

    private suspend fun make(photo: Bitmap): StickerResult {
        val segmented = runCatching { segment(photo) }
        val modelMissing = (segmented.exceptionOrNull() as? MlKitException)?.errorCode == MlKitException.UNAVAILABLE
        return withContext(Dispatchers.Default) {
            val cutOut = segmented.getOrNull()?.let(::trimmed)?.let { Sticker(encode(outlined(it)), cutOut = true) }
            StickerResult(cutOut, Sticker(encode(roundCrop(photo)), cutOut = false), modelMissing)
        }
    }

    private suspend fun segment(photo: Bitmap): Bitmap? {
        val options = SubjectSegmenterOptions.Builder().enableForegroundBitmap().build()
        val segmenter = SubjectSegmentation.getClient(options)
        return try {
            segmenter.process(InputImage.fromBitmap(photo, 0)).await().foregroundBitmap
        } finally {
            segmenter.close()
        }
    }

    /** The visible part of a cut-out, or null if (almost) nothing is left. */
    private fun trimmed(bitmap: Bitmap): Bitmap? {
        val pixels = IntArray(bitmap.width * bitmap.height).also { bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height) }
        var left = bitmap.width; var top = bitmap.height; var right = -1; var bottom = -1
        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
            if (Color.alpha(pixels[y * bitmap.width + x]) > ALPHA_THRESHOLD) {
                if (x < left) left = x
                if (x > right) right = x
                if (y < top) top = y
                if (y > bottom) bottom = y
            }
        }
        if (right < 0) return null
        val box = Rect(left, top, right + 1, bottom + 1)
        // A speck is not a product: then the round crop is the better sticker.
        if (box.width() * box.height() < bitmap.width * bitmap.height / 50) return null
        return Bitmap.createBitmap(bitmap, box.left, box.top, box.width(), box.height())
    }

    private fun outlined(subject: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val room = SIZE - 2 * (OUTLINE + MARGIN)
        val scale = room.toFloat() / maxOf(subject.width, subject.height)
        val w = subject.width * scale
        val h = subject.height * scale
        val target = RectF((SIZE - w) / 2, (SIZE - h) / 2, (SIZE + w) / 2, (SIZE + h) / 2)
        val smooth = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        // Shadow: the silhouette, blurred and a little lower.
        val shadow = Paint(smooth).apply {
            colorFilter = PorterDuffColorFilter(Color.argb(90, 0, 0, 0), PorterDuff.Mode.SRC_IN)
            maskFilter = BlurMaskFilter(OUTLINE * 1.5f, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawBitmap(subject, null, RectF(target).apply { offset(0f, OUTLINE / 2f) }, shadow)

        // Outline: the silhouette in white, stamped around a circle.
        val white = Paint(smooth).apply { colorFilter = PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN) }
        for (step in 0 until 24) {
            val angle = step * Math.PI * 2 / 24
            val shifted = RectF(target).apply { offset((cos(angle) * OUTLINE).toFloat(), (sin(angle) * OUTLINE).toFloat()) }
            canvas.drawBitmap(subject, null, shifted, white)
        }
        canvas.drawBitmap(subject, null, target, smooth)
        return out
    }

    private fun roundCrop(photo: Bitmap): Bitmap {
        val side = minOf(photo.width, photo.height)
        val source = Rect((photo.width - side) / 2, (photo.height - side) / 2, (photo.width + side) / 2, (photo.height + side) / 2)
        val out = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val inset = (MARGIN + OUTLINE).toFloat()
        val circle = RectF(inset, inset, SIZE - inset, SIZE - inset)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawOval(RectF(circle).apply { inset(-OUTLINE.toFloat(), -OUTLINE.toFloat()) }, paint.apply { color = Color.WHITE })
        val layer = canvas.saveLayer(circle, null)
        canvas.drawOval(circle, paint)
        canvas.drawBitmap(photo, source, circle, Paint(paint).apply { xfermode = android.graphics.PorterDuffXfermode(PorterDuff.Mode.SRC_IN) })
        canvas.restoreToCount(layer)
        return out
    }

    private fun encode(bitmap: Bitmap): ByteArray {
        @Suppress("DEPRECATION")
        val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
        return ByteArrayOutputStream().also { bitmap.compress(format, 85, it) }.toByteArray()
    }

    private companion object {
        const val SIZE = 128
        const val PHOTO_SIZE = 1024
        const val OUTLINE = 5
        const val MARGIN = 4
        const val ALPHA_THRESHOLD = 40
    }
}
