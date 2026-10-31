package hu.szileemester.adamazon.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class LinksTest {
    private fun urls(text: String) = Links.find(text).map { it.url }

    @Test
    fun `finds http and https links`() {
        assertEquals(listOf("https://my.linistry.com/x?a=1"), urls("Sorszám: https://my.linistry.com/x?a=1"))
        assertEquals(listOf("http://example.hu"), urls("http://example.hu"))
    }

    @Test
    fun `www links get https`() {
        assertEquals(listOf("https://www.auchan.hu/akciok"), urls("Akciók: www.auchan.hu/akciok"))
    }

    @Test
    fun `trailing punctuation is not part of the link`() {
        val text = "Nézd meg: https://dm.hu/akcio."
        val link = Links.find(text).single()
        assertEquals("https://dm.hu/akcio", link.url)
        assertEquals("https://dm.hu/akcio", text.substring(link.range))
    }

    @Test
    fun `several links and plain text`() {
        assertEquals(listOf("https://a.hu", "https://www.b.hu"), urls("első https://a.hu, második www.b.hu"))
        assertEquals(emptyList<String>(), urls("Nyitva 7-21, nincs link"))
    }
}
