package hu.szileemester.adamazon.data

import android.content.Context
import android.graphics.Bitmap
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Precision
import coil3.svg.SvgDecoder
import coil3.toBitmap
import hu.szileemester.adamazon.domain.PageImage
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A downloaded response: its bytes and content type. */
class Download(val bytes: ByteArray, val contentType: String)

/**
 * Downloads and draws web images, SVG included (Android cannot draw SVG by itself). Shared by the
 * store logos and the photo stickers.
 */
class WebImages(private val context: Context) {
    private val loader = ImageLoader.Builder(context).components { add(SvgDecoder.Factory()) }.build()

    /**
     * The image behind a link: the file itself for an image link, or, for a web page (a shop's
     * product page), the picture it shares itself with ([PageImage]).
     */
    suspend fun fromLink(link: String): ByteArray? = withContext(Dispatchers.IO) {
        val url = link.trim().let { if (it.startsWith("http://") || it.startsWith("https://")) it else "https://$it" }
        val response = download(url) ?: return@withContext null
        if (!response.contentType.contains("html")) return@withContext response.bytes
        PageImage.fromHtml(response.bytes.toString(Charsets.UTF_8), url).take(MAX_CANDIDATES)
            .firstNotNullOfOrNull { candidate -> download(candidate)?.takeIf { !it.contentType.contains("html") }?.bytes }
    }

    /** Draws any image Coil can read (PNG, JPEG, WebP, ICO, SVG) at most [maxSide] px; null if it is none. */
    suspend fun draw(bytes: ByteArray, maxSide: Int): Bitmap? {
        val request = ImageRequest.Builder(context)
            .data(bytes)
            .size(maxSide)
            .precision(Precision.INEXACT)
            .allowHardware(false)
            .build()
        return (loader.execute(request) as? SuccessResult)?.image?.toBitmap()?.scaledToFit(maxSide)
    }

    fun download(url: String): Download? = runCatching {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        // Some shops send an empty page to clients that do not look like a browser.
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) Adamazon")
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return@runCatching null
            val bytes = connection.inputStream.use { it.readLimited() } ?: return@runCatching null
            Download(bytes, connection.contentType.orEmpty().lowercase())
        } finally {
            connection.disconnect()
        }
    }.getOrNull()

    /** A picked file's bytes, or null if it cannot be read or is too big. */
    fun read(uri: android.net.Uri): ByteArray? =
        runCatching { context.contentResolver.openInputStream(uri)?.use { it.readLimited() } }.getOrNull()

    private companion object {
        const val MAX_CANDIDATES = 4
        const val MAX_BYTES = 3 * 1024 * 1024

        /** The whole stream, or null past [MAX_BYTES] (readNBytes needs API 33). */
        fun InputStream.readLimited(): ByteArray? {
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val read = read(buffer)
                if (read < 0) return out.toByteArray()
                out.write(buffer, 0, read)
                if (out.size() > MAX_BYTES) return null
            }
        }
    }
}
