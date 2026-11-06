package hu.szileemester.adamazon.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Precision
import coil3.svg.SvgDecoder
import coil3.toBitmap
import hu.szileemester.adamazon.domain.LogoCandidates
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Store logos, stored as a small PNG ([ImageCompressor.LOGO_SIZE] px).
 *
 * From a website: Google's favicon service first; when that only has a tiny icon (kik.hu has a
 * 16 px one), the site's own page is searched for a touch icon or logo image ([LogoCandidates]).
 * Both paths, and picked files, also take SVG, which Android cannot draw by itself.
 */
class LogoFetcher(private val context: Context) {
    private val loader = ImageLoader.Builder(context).components { add(SvgDecoder.Factory()) }.build()

    suspend fun fetch(domain: String): ByteArray? = withContext(Dispatchers.IO) {
        val host = domain.trim().removePrefix("https://").removePrefix("http://").substringBefore('/')
        if (host.isBlank()) return@withContext null
        favicon(host) ?: siteLogo(host)
    }

    /** A picked image file: photo, PNG or SVG. Null if it cannot be read. */
    suspend fun fromFile(uri: Uri): ByteArray? = withContext(Dispatchers.IO) {
        runCatching { context.contentResolver.openInputStream(uri)?.use { it.readLimited() } }.getOrNull()?.let { render(it) }
    }

    private suspend fun favicon(host: String): ByteArray? {
        val url = "https://www.google.com/s2/favicons?domain=${URLEncoder.encode(host, "UTF-8")}&sz=${ImageCompressor.LOGO_SIZE}"
        // A missing logo comes back as 404 with a generic 16 px globe – treat it as "no logo".
        val bytes = download(url) ?: return null
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        if (bitmap.width < MIN_SIZE) return null
        return ImageCompressor.png(bitmap.scaledToFit(ImageCompressor.LOGO_SIZE))
    }

    private suspend fun siteLogo(host: String): ByteArray? {
        val page = "https://$host/"
        val html = download(page)?.toString(Charsets.UTF_8) ?: return null
        return LogoCandidates.fromHtml(html, page).take(MAX_CANDIDATES).firstNotNullOfOrNull { url -> download(url)?.let { render(it) } }
    }

    /** Draws any image Coil can read (PNG, JPEG, WebP, ICO, SVG) as the logo PNG. */
    private suspend fun render(bytes: ByteArray): ByteArray? {
        val request = ImageRequest.Builder(context)
            .data(bytes)
            .size(ImageCompressor.LOGO_SIZE)
            .precision(Precision.INEXACT)
            .allowHardware(false)
            .build()
        val bitmap: Bitmap = (loader.execute(request) as? SuccessResult)?.image?.toBitmap() ?: return null
        if (maxOf(bitmap.width, bitmap.height) < MIN_SIZE) return null
        return ImageCompressor.png(bitmap.scaledToFit(ImageCompressor.LOGO_SIZE))
    }

    private fun download(url: String): ByteArray? = runCatching {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        // Some shops send an empty page to clients that do not look like a browser.
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) Adamazon")
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) null else connection.inputStream.use { it.readLimited() }
        } finally {
            connection.disconnect()
        }
    }.getOrNull()

    /** The whole stream, or null past [MAX_BYTES] (readNBytes needs API 33). */
    private fun InputStream.readLimited(): ByteArray? {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (true) {
            val read = read(buffer)
            if (read < 0) return out.toByteArray()
            out.write(buffer, 0, read)
            if (out.size() > MAX_BYTES) return null
        }
    }

    private companion object {
        const val MIN_SIZE = 32
        const val MAX_CANDIDATES = 4
        const val MAX_BYTES = 3 * 1024 * 1024
    }
}
