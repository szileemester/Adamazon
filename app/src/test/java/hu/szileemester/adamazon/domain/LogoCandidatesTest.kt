package hu.szileemester.adamazon.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LogoCandidatesTest {
    @Test
    fun `finds an svg logo image in the page header`() {
        // As on vallalat.kik.hu: only a 16 px favicon, the real logo is an SVG <img>.
        val html = """
            <link rel="shortcut icon" href="/favicon.ico">
            <a href="/"><img src="/_assets/3bdd/Icons/kik-logo.svg" width="68" height="68" alt=""/></a>
            <img src="/fileadmin/banner.jpg" alt="Akció">
        """
        assertEquals(
            listOf("https://vallalat.kik.hu/_assets/3bdd/Icons/kik-logo.svg"),
            LogoCandidates.fromHtml(html, "https://vallalat.kik.hu/"),
        )
    }

    @Test
    fun `touch icons and sized icons come before logo images`() {
        val html = """
            <img class="site-logo" src="https://cdn.example.com/logo.png">
            <link rel="icon" sizes="192x192" href="icon-192.png">
            <link rel="icon" sizes="16x16" href="icon-16.png">
            <link rel='apple-touch-icon' href='/apple.png'>
        """
        assertEquals(
            listOf("https://example.com/apple.png", "https://example.com/shop/icon-192.png", "https://cdn.example.com/logo.png"),
            LogoCandidates.fromHtml(html, "https://example.com/shop/"),
        )
    }

    @Test
    fun `ignores inline images and pages without a logo`() {
        assertTrue(LogoCandidates.fromHtml("""<img src="data:image/png;base64,xx" alt="logo">""", "https://a.hu/").isEmpty())
        assertTrue(LogoCandidates.fromHtml("<p>Nincs itt semmi</p>", "https://a.hu/").isEmpty())
    }
}
