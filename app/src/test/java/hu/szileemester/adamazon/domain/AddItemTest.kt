package hu.szileemester.adamazon.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AddItemTest {
    private fun item(id: String, name: String, checked: Boolean = false, checkedAt: Long? = null, quantity: String? = null) =
        Item(id = id, name = name, categoryId = "x", emoji = "", checked = checked, checkedAt = checkedAt, quantity = quantity)

    @Test
    fun `new item is created with parsed quantity`() {
        assertEquals(AddItemDecision.Create("Tej", "2 l"), decideAddItem("tej 2 l", emptyList()))
    }

    @Test
    fun `open item with the same name is not duplicated`() {
        val tej = item("1", "Tej")
        assertEquals(AddItemDecision.AlreadyOnList(tej), decideAddItem("TEJ", listOf(tej)))
    }

    @Test
    fun `checked item is restored, keeping old quantity unless a new one is given`() {
        val old = item("1", "Tej", checked = true, checkedAt = 1, quantity = "1 l")
        val newer = item("2", "tej", checked = true, checkedAt = 5)
        assertEquals(AddItemDecision.Restore(newer, null), decideAddItem("tej", listOf(old, newer)))
        assertEquals(AddItemDecision.Restore(old, "1 l"), decideAddItem("tej", listOf(old)))
        assertEquals(AddItemDecision.Restore(old, "3 l"), decideAddItem("3 l tej", listOf(old)))
    }

    @Test
    fun `blank input does nothing`() {
        assertNull(decideAddItem("   ", emptyList()))
    }

    @Test
    fun `suggestions come from history, skip open items and match word starts`() {
        val items = listOf(
            item("1", "Paradicsom", checked = true, checkedAt = 1),
            item("2", "Koktélparadicsom", checked = true, checkedAt = 2),
            item("3", "Passata", checked = true, checkedAt = 3),
            item("4", "Paprika"),
        )
        val learned = listOf(LearnedMapping("Pálinka", "ital", "🥃"))
        assertEquals(listOf("Passata", "Paradicsom", "Pálinka"), suggestions("pa", items, learned))
        assertEquals(listOf("Paradicsom"), suggestions("parad", items, learned))
    }
}
