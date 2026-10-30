package hu.szileemester.adamazon.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class QuantityParserTest {
    @Test
    fun `leading amount with unit`() {
        assertEquals(ParsedInput("Paradicsom", "2 kg"), QuantityParser.parse("2 kg paradicsom"))
        assertEquals(ParsedInput("Sonka", "10 dkg"), QuantityParser.parse("10 dkg sonka"))
        assertEquals(ParsedInput("Tej", "1,5 l"), QuantityParser.parse("1,5 l tej"))
    }

    @Test
    fun `leading amount without unit means pieces`() {
        assertEquals(ParsedInput("Tojás", "10 db"), QuantityParser.parse("10 tojás"))
        assertEquals(ParsedInput("Sör", "6 db"), QuantityParser.parse("6x sör"))
    }

    @Test
    fun `unit is not mistaken for the start of a word`() {
        assertEquals(ParsedInput("Gyümölcs", "2 db"), QuantityParser.parse("2 gyümölcs"))
        assertEquals(ParsedInput("Liszt", "1 db"), QuantityParser.parse("1 liszt"))
    }

    @Test
    fun `trailing amount`() {
        assertEquals(ParsedInput("Tej", "2 l"), QuantityParser.parse("tej 2 l"))
        assertEquals(ParsedInput("Kifli", "6 db"), QuantityParser.parse("kifli 6"))
    }

    @Test
    fun `plain names stay untouched apart from capitalisation`() {
        assertEquals(ParsedInput("Wc papír", null), QuantityParser.parse("  wc   papír "))
        assertEquals(ParsedInput("Vitamin B12", null), QuantityParser.parse("vitamin B12"))
        assertEquals(ParsedInput("100%-os narancslé", null), QuantityParser.parse("100%-os narancslé"))
    }
}
