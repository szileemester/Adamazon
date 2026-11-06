package hu.szileemester.adamazon.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class PageImageTest {
    @Test
    fun `a product page gives its shared picture first`() {
        val html = """
            <meta name="twitter:image" content="https://cdn.shop.hu/tw.jpg">
            <meta property="og:image" content="/media/catalog/tejfol-20.jpg?w=600&amp;h=600" />
            <img class="header-logo" src="/logo.svg">
        """
        assertEquals(
            listOf(
                "https://shop.hu/media/catalog/tejfol-20.jpg?w=600&h=600",
                "https://cdn.shop.hu/tw.jpg",
                "https://shop.hu/logo.svg",
            ),
            PageImage.fromHtml(html, "https://shop.hu/termek/tejfol"),
        )
    }

    @Test
    fun `addresses with spaces and accents still resolve`() {
        assertEquals(
            listOf("https://shop.hu/képek/tejföl doboz.jpg"),
            PageImage.fromHtml("""<meta property="og:image" content="/képek/tejföl doboz.jpg">""", "https://shop.hu/hu/termek"),
        )
    }

    @Test
    fun `without shared pictures the logo candidates remain`() {
        assertEquals(
            listOf("https://vallalat.kik.hu/Icons/kik-logo.svg"),
            PageImage.fromHtml("""<img src="/Icons/kik-logo.svg" alt="">""", "https://vallalat.kik.hu/"),
        )
    }
}
