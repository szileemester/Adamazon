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
import kotlinx.coroutines.withTimeoutOrNull

/** A downloaded response: its bytes, content type and final address (after redirects). */
class Download(val bytes: ByteArray, val contentType: String, val url: String)

/**
 * Downloads and draws web images, SVG included (Android cannot draw SVG by itself). Shared by the
 * store logos and the photo stickers.
 */
class WebImages(private val context: Context) {
    private val loader = ImageLoader.Builder(context).components { add(SvgDecoder.Factory()) }.build()

    /**
     * The image behind a link: the file itself for an image link, or, for a web page (a shop's
     * product page), the picture it shares itself with ([PageImage]). Gives up after [LINK_TIMEOUT_MS].
     */
    suspend fun fromLink(link: String): ByteArray? = withTimeoutOrNull(LINK_TIMEOUT_MS) {
        withContext(Dispatchers.IO) {
            val url = link.trim().let { if (it.startsWith("http://") || it.startsWith("https://")) it else "https://$it" }
            val response = download(url) ?: return@withContext null
            if (!response.contentType.contains("html")) return@withContext response.bytes
            // Relative addresses belong to the page where the redirects ended.
            PageImage.fromHtml(response.bytes.toString(Charsets.UTF_8), response.url).take(MAX_CANDIDATES)
                .firstNotNullOfOrNull { candidate -> download(candidate)?.takeIf { !it.contentType.contains("html") }?.bytes }
        }
    }

    /**
     * Draws any image Coil can read (PNG, JPEG, WebP, ICO, SVG) at most [maxSide] px; null if it is
     * none. [data] is the bytes or a picked file's Uri (read subsampled, so big photos are fine).
     */
    suspend fun draw(data: Any, maxSide: Int): Bitmap? {
        val request = ImageRequest.Builder(context)
            .data(data)
            .size(maxSide)
            .precision(Precision.INEXACT)
            .allowHardware(false)
            .build()
        return (loader.execute(request) as? SuccessResult)?.image?.toBitmap()?.scaledToFit(maxSide)
    }

    /**
     * Follows up to [MAX_REDIRECTS] redirects itself: HttpURLConnection does not go from http to
     * https, and only http(s) addresses are allowed anywhere on the way.
     */
    fun download(url: String): Download? {
        var address = url
        repeat(MAX_REDIRECTS + 1) {
            val target = runCatching { URL(address) }.getOrNull()?.takeIf { it.protocol == "http" || it.protocol == "https" }
                ?: return null
            val connection = runCatching { target.openConnection() as HttpURLConnection }.getOrNull() ?: return null
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 10_000
                connection.readTimeout = 10_000
                // Some shops send an empty page to clients that do not look like a browser.
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) Adamazon")
                val code = runCatching { connection.responseCode }.getOrNull() ?: return null
                if (code in 300..399) {
                    address = connection.getHeaderField("Location")?.let { runCatching { URL(target, it).toString() }.getOrNull() }
                        ?: return null
                    return@repeat
                }
                if (code != HttpURLConnection.HTTP_OK) return null
                val bytes = runCatching { connection.inputStream.use { it.readLimited() } }.getOrNull() ?: return null
                return Download(bytes, connection.contentType.orEmpty().lowercase(), target.toString())
            } finally {
                connection.disconnect()
            }
        }
        return null
    }

    private companion object {
        const val MAX_CANDIDATES = 4
        const val MAX_REDIRECTS = 4
        const val MAX_BYTES = 3 * 1024 * 1024
        const val LINK_TIMEOUT_MS = 25_000L
        const val READ_DEADLINE_MS = 15_000L

        /**
         * The whole stream, or null past [MAX_BYTES] or [READ_DEADLINE_MS] – a server trickling a
         * byte at a time would otherwise keep the read going for hours (readNBytes needs API 33).
         */
        fun InputStream.readLimited(): ByteArray? {
            val deadline = System.currentTimeMillis() + READ_DEADLINE_MS
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val read = read(buffer)
                if (read < 0) return out.toByteArray()
                out.write(buffer, 0, read)
                if (out.size() > MAX_BYTES || System.currentTimeMillis() > deadline) return null
            }
        }
    }
}
