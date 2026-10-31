package hu.szileemester.adamazon.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import hu.szileemester.adamazon.AppContainer
import hu.szileemester.adamazon.data.AccessState
import hu.szileemester.adamazon.data.CategoryRepository
import hu.szileemester.adamazon.domain.AddItemDecision
import hu.szileemester.adamazon.domain.Categorizer
import hu.szileemester.adamazon.domain.Category
import hu.szileemester.adamazon.domain.CategoryOrder
import hu.szileemester.adamazon.domain.Item
import hu.szileemester.adamazon.domain.LearnedMapping
import hu.szileemester.adamazon.domain.LoyaltyCard
import hu.szileemester.adamazon.domain.Store
import hu.szileemester.adamazon.domain.UndoEntry
import hu.szileemester.adamazon.domain.UndoHistory
import hu.szileemester.adamazon.domain.decideAddItem
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class Section(val category: Category, val items: List<Item>)

data class StoreListState(
    val store: Store? = null,
    val sections: List<Section> = emptyList(),
    val checked: List<Item> = emptyList(),
    val categories: List<Category> = emptyList(),
    val items: List<Item> = emptyList(),
    val card: LoyaltyCard? = null,
    val loaded: Boolean = false,
)

sealed interface ListEvent {
    data class AlreadyOnList(val name: String) : ListEvent
    data class Deleted(val item: Item) : ListEvent
    data class Undone(val label: String) : ListEvent
}

class StoreListViewModel(private val c: AppContainer, private val storeId: String) : ViewModel() {
    private val categories = c.categories.categories.map { it.ifEmpty { CategoryRepository.DEFAULT_CATEGORIES } }

    val learned: StateFlow<Map<String, LearnedMapping>> =
        c.learned.learned.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val state: StateFlow<StoreListState> = combine(
        c.stores.store(storeId),
        c.items.itemsOf(storeId),
        categories,
        c.cards.cards,
    ) { store, items, categories, cards ->
        val ordered = CategoryOrder.ordered(categories, store?.categoryScores.orEmpty())
        val byCategory = items.filter { !it.checked }.groupBy { resolveCategory(it.categoryId, categories) }
        StoreListState(
            store = store,
            sections = ordered.mapNotNull { cat -> byCategory[cat.id]?.let { Section(cat, it) } },
            checked = items.filter { it.checked }.sortedByDescending { it.checkedAt ?: 0 },
            categories = categories,
            items = items,
            card = cards.firstOrNull { it.storeId == storeId },
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StoreListState())

    private val _events = Channel<ListEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val userEmail: String?
        get() = (c.auth.state.value as? AccessState.Allowed)?.user?.email

    // In-memory, per screen: guards against mis-taps, not a full history. Learning side effects
    // (category order, learned categories) are deliberately not reverted.
    private val _undo = MutableStateFlow(UndoHistory())
    val canUndo: StateFlow<Boolean> = _undo.map { it.canUndo }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private fun record(label: String, before: Map<String, Item?>) {
        _undo.update { it.push(UndoEntry(label, before)) }
    }

    fun undo() {
        val (entry, rest) = _undo.value.pop() ?: return
        _undo.value = rest
        c.items.revert(storeId, entry.before)
        _events.trySend(ListEvent.Undone(entry.label))
    }

    fun add(input: String) {
        val current = state.value
        when (val decision = decideAddItem(input, current.items)) {
            null -> Unit
            is AddItemDecision.AlreadyOnList -> _events.trySend(ListEvent.AlreadyOnList(decision.item.name))
            is AddItemDecision.Restore -> {
                record("„${decision.item.name}” felvétele", mapOf(decision.item.id to decision.item))
                c.items.restore(storeId, decision.item.id, decision.quantity, System.currentTimeMillis())
            }
            is AddItemDecision.Create -> {
                val guess = c.categorizer.guess(decision.name, learned.value) { id ->
                    current.categories.firstOrNull { it.id == id }?.emoji
                }
                val id = c.items.add(
                    storeId,
                    Item(
                        id = "",
                        name = decision.name,
                        quantity = decision.quantity,
                        categoryId = guess.categoryId,
                        emoji = guess.emoji,
                        createdAt = System.currentTimeMillis(),
                        createdBy = userEmail,
                    ),
                )
                record("„${decision.name}” felvétele", mapOf(id to null))
            }
        }
    }

    fun toggle(item: Item) {
        val now = System.currentTimeMillis()
        record(if (item.checked) "„${item.name}” visszaállítása" else "„${item.name}” kipipálása", mapOf(item.id to item))
        if (item.checked) {
            c.items.setChecked(storeId, item.id, checked = false, now = now)
            return
        }
        c.items.setChecked(storeId, item.id, checked = true, now = now)
        val current = state.value
        val store = current.store ?: return
        val result = CategoryOrder.onItemChecked(
            store = store,
            categories = current.categories,
            categoryId = resolveCategory(item.categoryId, current.categories),
            now = now,
            openItemsLeft = current.items.count { !it.checked && it.id != item.id },
        )
        c.stores.updateSession(storeId, result.session, result.newScores)
    }

    fun delete(item: Item) {
        record("„${item.name}” törlése", mapOf(item.id to item))
        c.items.delete(storeId, item.id)
        _events.trySend(ListEvent.Deleted(item))
    }

    fun clearChecked() {
        val checked = state.value.checked
        if (checked.isEmpty()) return
        record("Kipipáltak törlése", checked.associateBy { it.id })
        c.items.deleteAll(storeId, checked.map { it.id })
    }

    fun setDescription(description: String?) = c.stores.setDescription(storeId, description)

    /** Creates a category from the item editor and returns its id, so it can be selected at once. */
    fun createCategory(name: String, emoji: String): String =
        c.categories.add(name, emoji, state.value.categories)

    /** Saves an edited item; a changed category or icon is remembered for next time. */
    fun save(original: Item, edited: Item) {
        if (edited == original) return
        record("„${original.name}” szerkesztése", mapOf(original.id to original))
        c.items.update(storeId, edited)
        if (edited.categoryId != original.categoryId || edited.emoji != original.emoji || edited.key != original.key) {
            c.learned.remember(edited.name, edited.categoryId, edited.emoji)
        }
    }

    private fun resolveCategory(id: String, categories: List<Category>): String =
        if (categories.any { it.id == id }) id else Categorizer.FALLBACK_CATEGORY_ID
}
