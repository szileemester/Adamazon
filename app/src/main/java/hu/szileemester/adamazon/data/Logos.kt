package hu.szileemester.adamazon.data

import android.graphics.BitmapFactory
import android.net.Uri
import hu.szileemester.adamazon.domain.LogoCandidates
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Store logos, stored as a small PNG ([ImageCompressor.LOGO_SIZE] px).
 *
 * From a website: Google's favicon service first; when that only has a tiny icon (kik.hu has a
 * 16 px one), the site's own page is searched for a touch icon or logo image ([LogoCandidates]).
 * Both paths, and picked files, also take SVG (see [WebImages]).
 */
class LogoFetcher(private val web: WebImages) {

    suspend fun fetch(domain: String): ByteArray? = withContext(Dispatchers.IO) {
        val host = domain.trim().removePrefix("https://").removePrefix("http://").substringBefore('/')
        if (host.isBlank()) return@withContext null
        favicon(host) ?: siteLogo(host)
    }

    /** A picked image file: photo, PNG or SVG. Null if it cannot be read. */
    suspend fun fromFile(uri: Uri): ByteArray? = withContext(Dispatchers.IO) { web.read(uri)?.let { render(it) } }

    private fun favicon(host: String): ByteArray? {
        val url = "https://www.google.com/s2/favicons?domain=${URLEncoder.encode(host, "UTF-8")}&sz=${ImageCompressor.LOGO_SIZE}"
        // A missing logo comes back as 404 with a generic 16 px globe – treat it as "no logo".
        val bytes = web.download(url)?.bytes ?: return null
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        if (bitmap.width < MIN_SIZE) return null
        return ImageCompressor.png(bitmap.scaledToFit(ImageCompressor.LOGO_SIZE))
    }

    private suspend fun siteLogo(host: String): ByteArray? {
        val page = "https://$host/"
        val html = web.download(page)?.bytes?.toString(Charsets.UTF_8) ?: return null
        return LogoCandidates.fromHtml(html, page).take(MAX_CANDIDATES)
            .firstNotNullOfOrNull { url -> web.download(url)?.bytes?.let { render(it) } }
    }

    private suspend fun render(bytes: ByteArray): ByteArray? {
        val bitmap = web.draw(bytes, ImageCompressor.LOGO_SIZE) ?: return null
        if (maxOf(bitmap.width, bitmap.height) < MIN_SIZE) return null
        return ImageCompressor.png(bitmap)
    }

    private companion object {
        const val MIN_SIZE = 32
        const val MAX_CANDIDATES = 4
    }
}
