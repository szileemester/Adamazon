package hu.szileemester.adamazon.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuantitiesTest {
    @Test
    fun `parses stored quantities`() {
        assertEquals(Quantity(2.0, "kg"), Quantities.parse("2 kg"))
        assertEquals(Quantity(1.5, "l"), Quantities.parse("1,5 l"))
        assertEquals(Quantity(3.0, ""), Quantities.parse("3"))
        assertEquals(Quantity(2.0, "l"), Quantities.parse("2 liter"))
        assertEquals(Quantity(4.0, "csomag"), Quantities.parse("4 cs."))
    }

    @Test
    fun `free text is not a quantity`() {
        assertNull(Quantities.parse("egy kevés"))
        assertNull(Quantities.parse(""))
        assertNull(Quantities.parse(null))
    }

    @Test
    fun `formats with decimal comma`() {
        assertEquals("2 kg", Quantities.format(Quantity(2.0, "kg")))
        assertEquals("1,5 l", Quantities.format(Quantity(1.5, "l")))
        assertEquals("0,25 kg", Quantities.format(Quantity(0.25, "kg")))
        assertEquals("3", Quantities.format(Quantity(3.0, "")))
    }

    @Test
    fun `stepper uses unit-sized steps and drops to no quantity`() {
        assertEquals(1.0, Quantities.stepped(null, "db", +1)!!, 1e-9)
        assertEquals(2.5, Quantities.stepped(2.0, "kg", +1)!!, 1e-9)
        assertEquals(20.0, Quantities.stepped(10.0, "dkg", +1)!!, 1e-9)
        assertEquals(400.0, Quantities.stepped(500.0, "g", -1)!!, 1e-9)
        assertNull(Quantities.stepped(1.0, "db", -1))
        assertNull(Quantities.stepped(0.5, "kg", -1))
    }

    @Test
    fun `quick-add units match the editor's units`() {
        assertEquals(ParsedInput("Tej", "2 l"), QuantityParser.parse("2 liter tej"))
        assertEquals(ParsedInput("Kenyér", "1 db"), QuantityParser.parse("1 darab kenyér"))
    }
}
