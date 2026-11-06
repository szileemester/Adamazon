package hu.szileemester.adamazon.data

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Turns picked photos into small JPEGs that fit in a Firestore document (max 1 MiB), so no
 * Cloud Storage (and no billing account) is needed.
 */
class ImageCompressor(private val resolver: ContentResolver) {

    suspend fun cardImage(uri: Uri): ByteArray = withContext(Dispatchers.Default) {
        jpeg(decode(uri, maxSide = 1600), maxBytes = 600_000)
    }

    suspend fun thumbnail(uri: Uri): ByteArray = withContext(Dispatchers.Default) {
        jpeg(decode(uri, maxSide = 360), maxBytes = 60_000)
    }

    suspend fun logo(uri: Uri): ByteArray = withContext(Dispatchers.Default) {
        png(decode(uri, maxSide = LOGO_SIZE))
    }

    /** Reads a picked photo upright (EXIF rotation applied), at most [maxSide] px on its longer side. */
    fun decode(uri: Uri, maxSide: Int): Bitmap {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // ImageDecoder applies EXIF rotation, so camera photos come out upright.
            val source = ImageDecoder.createSource(resolver, uri)
            return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val (w, h) = info.size.width to info.size.height
                val scale = minOf(1f, maxSide.toFloat() / maxOf(w, h))
                decoder.setTargetSize((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1))
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val bitmap = resolver.openInputStream(uri).use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: error("A kép nem olvasható.")
        return bitmap.scaledToFit(maxSide)
    }

    companion object {
        const val LOGO_SIZE = 128

        fun jpeg(bitmap: Bitmap, maxBytes: Int): ByteArray {
            var quality = 90
            while (true) {
                val bytes = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, quality, it) }
                if (bytes.size() <= maxBytes || quality <= 40) return bytes.toByteArray()
                quality -= 10
            }
        }

        fun png(bitmap: Bitmap): ByteArray =
            ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }
}

fun Bitmap.scaledToFit(maxSide: Int): Bitmap {
    val scale = maxSide.toFloat() / maxOf(width, height)
    if (scale >= 1f) return this
    return Bitmap.createScaledBitmap(this, (width * scale).toInt().coerceAtLeast(1), (height * scale).toInt().coerceAtLeast(1), true)
}

/** Downloads a store's logo from Google's public favicon service. */
class LogoFetcher {
    suspend fun fetch(domain: String): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            val host = domain.trim().removePrefix("https://").removePrefix("http://").substringBefore('/')
            val url = URL("https://www.google.com/s2/favicons?domain=${URLEncoder.encode(host, "UTF-8")}&sz=${ImageCompressor.LOGO_SIZE}")
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            try {
                // A missing logo comes back as 404 with a generic 16 px globe – treat it as "no logo".
                if (connection.responseCode != HttpURLConnection.HTTP_OK) return@runCatching null
                val bitmap = connection.inputStream.use { BitmapFactory.decodeStream(it) } ?: return@runCatching null
                if (bitmap.width < 32) return@runCatching null
                ImageCompressor.png(bitmap.scaledToFit(ImageCompressor.LOGO_SIZE))
            } finally {
                connection.disconnect()
            }
        }.getOrNull()
    }
}
