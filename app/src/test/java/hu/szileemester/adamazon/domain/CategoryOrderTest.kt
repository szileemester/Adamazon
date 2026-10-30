package hu.szileemester.adamazon.domain

import hu.szileemester.adamazon.domain.CategoryOrder.SESSION_TIMEOUT_MS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CategoryOrderTest {
    private val categories = listOf("a", "b", "c", "d").mapIndexed { i, id -> Category(id, id, "", i) }
    private val store = Store(id = "s", name = "Bolt")

    private fun order(scores: Map<String, Double>) = CategoryOrder.ordered(categories, scores).map { it.id }

    @Test
    fun `without scores the default order is used`() {
        assertEquals(listOf("a", "b", "c", "d"), order(emptyMap()))
    }

    @Test
    fun `manual order is reproduced exactly`() {
        val scores = CategoryOrder.scoresFromOrder(listOf("d", "b", "a", "c"))
        assertEquals(listOf("d", "b", "a", "c"), order(scores))
    }

    @Test
    fun `checks within a session build the first-seen sequence`() {
        var s = store
        for ((i, cat) in listOf("c", "c", "a", "c", "b").withIndex()) {
            val r = CategoryOrder.onItemChecked(s, categories, cat, now = 1000L + i, openItemsLeft = 5)
            assertNull(r.newScores)
            s = s.copy(session = r.session)
        }
        assertEquals(listOf("c", "a", "b"), s.session!!.sequence)
    }

    @Test
    fun `session ends after inactivity and blends observed order`() {
        val session = ShoppingSession(0, 0, listOf("d", "c", "b", "a"))
        val r = CategoryOrder.onItemChecked(
            store.copy(session = session), categories, "a", now = SESSION_TIMEOUT_MS + 1, openItemsLeft = 3,
        )
        val scores = assertNotNull(r.newScores).let { r.newScores!! }
        assertEquals(0.3, scores.getValue("a"), 1e-9) // 0.7·0 + 0.3·1
        assertEquals(0.7, scores.getValue("d"), 1e-9) // 0.7·1 + 0.3·0
        assertEquals(listOf("a"), r.session!!.sequence)
    }

    @Test
    fun `repeated sessions converge to the walking order`() {
        var s = store
        repeat(10) {
            val r = CategoryOrder.finish(s, categories, ShoppingSession(0, 0, listOf("d", "c", "b", "a")))
            s = s.copy(categoryScores = r!!)
        }
        assertEquals(listOf("d", "c", "b", "a"), order(s.categoryScores))
    }

    @Test
    fun `checking the last item finishes the session immediately`() {
        val session = ShoppingSession(0, 0, listOf("b"))
        val r = CategoryOrder.onItemChecked(store.copy(session = session), categories, "a", now = 10, openItemsLeft = 0)
        assertNull(r.session)
        assertNotNull(r.newScores)
    }

    @Test
    fun `locked order never learns`() {
        val locked = store.copy(orderLocked = true, session = ShoppingSession(0, 0, listOf("b")))
        val r = CategoryOrder.onItemChecked(locked, categories, "a", now = 10, openItemsLeft = 0)
        assertNull(r.newScores)
    }

    @Test
    fun `a single category session teaches nothing`() {
        assertNull(CategoryOrder.finish(store, categories, ShoppingSession(0, 0, listOf("b"))))
    }
}
