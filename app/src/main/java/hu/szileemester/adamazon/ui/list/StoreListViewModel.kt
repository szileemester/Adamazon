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
import hu.szileemester.adamazon.domain.ItemRef
import hu.szileemester.adamazon.domain.Normalizer
import hu.szileemester.adamazon.domain.QuantityParser
import hu.szileemester.adamazon.domain.LearnedMapping
import hu.szileemester.adamazon.domain.LoyaltyCard
import hu.szileemester.adamazon.domain.Store
import hu.szileemester.adamazon.domain.UndoEntry
import hu.szileemester.adamazon.domain.UndoHistory
import hu.szileemester.adamazon.domain.VoiceSplitter
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
    data class Deleted(val item: Item, val undo: UndoEntry) : ListEvent
    data class Undone(val label: String) : ListEvent
    data class Moved(val name: String, val storeName: String) : ListEvent
}

class StoreListViewModel(private val c: AppContainer, private val storeId: String) : ViewModel() {
    private val categories = c.session.categories.categories.map { it.ifEmpty { CategoryRepository.DEFAULT_CATEGORIES } }

    val learned: StateFlow<Map<String, LearnedMapping>> =
        c.session.learned.learned.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val state: StateFlow<StoreListState> = combine(
        c.session.stores.store(storeId),
        c.session.items.itemsOf(storeId),
        categories,
        c.session.cards.cardOf(storeId),
    ) { store, items, categories, card ->
        val ordered = CategoryOrder.ordered(categories, store?.categoryScores.orEmpty())
        val byCategory = items.filter { !it.checked }.groupBy { resolveCategory(it.categoryId, categories) }
        StoreListState(
            store = store,
            sections = ordered.mapNotNull { cat -> byCategory[cat.id]?.let { Section(cat, it) } },
            checked = items.filter { it.checked }.sortedByDescending { it.checkedAt ?: 0 },
            categories = categories,
            items = items,
            card = card,
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StoreListState())

    private val _events = Channel<ListEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val userEmail: String?
        get() = (c.auth.state.value as? AccessState.Allowed)?.user?.email

    /** Stores an item can be moved to. */
    val otherStores: StateFlow<List<Store>> = c.session.stores.stores.map { all -> all.filter { it.id != storeId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // In-memory, per screen: guards against mis-taps, not a full history. Learning side effects
    // (category order, learned categories) are deliberately not reverted.
    private val _undo = MutableStateFlow(UndoHistory())
    val canUndo: StateFlow<Boolean> = _undo.map { it.canUndo }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private fun here(itemId: String) = ItemRef(storeId, itemId)

    private fun record(label: String, before: Map<ItemRef, Item?>): UndoEntry =
        UndoEntry(label, before).also { entry -> _undo.update { it.push(entry) } }

    /** Undoes the newest change. */
    fun undo() {
        val (entry, rest) = _undo.value.pop() ?: return
        _undo.value = rest
        apply(entry)
    }

    /** Undoes exactly [entry], even if newer changes were made since; no-op if already undone. */
    fun undo(entry: UndoEntry) {
        _undo.value = _undo.value.take(entry) ?: return
        apply(entry)
    }

    private fun apply(entry: UndoEntry) {
        c.session.items.writeAll(entry.before)
        _events.trySend(ListEvent.Undone(entry.label))
    }

    private fun newItem(id: String, name: String, quantity: String?, categories: List<Category>, createdAt: Long): Item {
        val guess = c.categorizer.guess(name, learned.value) { cat -> categories.firstOrNull { it.id == cat }?.emoji }
        return Item(
            id = id,
            name = name,
            quantity = quantity,
            categoryId = guess.categoryId,
            emoji = guess.emoji,
            createdAt = createdAt,
            createdBy = userEmail,
        )
    }

    fun add(input: String) {
        val current = state.value
        when (val decision = decideAddItem(input, current.items)) {
            null -> Unit
            is AddItemDecision.AlreadyOnList -> _events.trySend(ListEvent.AlreadyOnList(decision.item.name))
            is AddItemDecision.Restore -> {
                record("„${decision.item.name}” felvétele", mapOf(here(decision.item.id) to decision.item))
                c.session.items.restore(storeId, decision.item.id, decision.quantity, System.currentTimeMillis())
            }
            is AddItemDecision.Create -> {
                val id = c.session.items.newId(storeId)
                val item = newItem(id, decision.name, decision.quantity, current.categories, System.currentTimeMillis())
                c.session.items.writeAll(mapOf(here(id) to item))
                record("„${decision.name}” felvétele", mapOf(here(id) to null))
            }
        }
    }

    /** Splits dictated utterances into items; a name already used here is never cut apart. */
    fun voiceLines(utterances: List<String>): List<String> {
        val known = state.value.items.map { it.key } + learned.value.keys
        val splitter = VoiceSplitter(c.categorizer::isProductWord) { first, second ->
            val pair = Normalizer.key("$first $second")
            c.categorizer.isOneProduct(first, second) || known.any { it == pair || it.startsWith("$pair ") }
        }
        return splitter.split(utterances)
    }

    /** What adding these pasted or dictated lines would do, line by line, for the confirmation dialog. */
    fun previewLines(lines: List<String>): List<PastePreview> {
        val current = state.value
        val seen = mutableSetOf<String>()
        return lines.map { line ->
            val parsed = QuantityParser.parse(line)
            val key = Normalizer.key(parsed.name)
            val decision = decideAddItem(line, current.items)
            val emoji = when (decision) {
                is AddItemDecision.Restore -> decision.item.emoji
                is AddItemDecision.AlreadyOnList -> decision.item.emoji
                else -> newItem("", parsed.name, parsed.quantity, current.categories, 0).emoji
            }
            val status = when {
                !seen.add(key) -> PastePreview.Status.DUPLICATE
                decision is AddItemDecision.AlreadyOnList -> PastePreview.Status.ON_LIST
                decision is AddItemDecision.Restore -> PastePreview.Status.RESTORE
                else -> PastePreview.Status.NEW
            }
            PastePreview(line, parsed.name, parsed.quantity, emoji, status)
        }
    }

    /** Adds several pasted or dictated lines in one batch; one undo step takes them all back. */
    fun addAll(lines: List<String>) {
        val current = state.value
        val now = System.currentTimeMillis()
        val items = current.items.toMutableList()
        val before = mutableMapOf<ItemRef, Item?>()
        val after = mutableMapOf<ItemRef, Item?>()
        lines.forEachIndexed { index, line ->
            when (val decision = decideAddItem(line, items)) {
                is AddItemDecision.Restore -> {
                    val restored = decision.item.copy(
                        checked = false, checkedAt = null, quantity = decision.quantity, createdAt = now + index,
                    )
                    before[here(restored.id)] = decision.item
                    after[here(restored.id)] = restored
                    items.replaceAll { if (it.id == restored.id) restored else it }
                }
                is AddItemDecision.Create -> {
                    val item = newItem(c.session.items.newId(storeId), decision.name, decision.quantity, current.categories, now + index)
                    before[here(item.id)] = null
                    after[here(item.id)] = item
                    items += item
                }
                is AddItemDecision.AlreadyOnList, null -> Unit
            }
        }
        if (after.isEmpty()) return
        record("${after.size} elem beillesztése", before)
        c.session.items.writeAll(after)
    }

    fun toggle(item: Item) {
        val now = System.currentTimeMillis()
        record(if (item.checked) "„${item.name}” visszaállítása" else "„${item.name}” kipipálása", mapOf(here(item.id) to item))
        if (item.checked) {
            c.session.items.setChecked(storeId, item.id, checked = false, now = now)
            return
        }
        c.session.items.setChecked(storeId, item.id, checked = true, now = now)
        val current = state.value
        val store = current.store ?: return
        val result = CategoryOrder.onItemChecked(
            store = store,
            categories = current.categories,
            categoryId = resolveCategory(item.categoryId, current.categories),
            now = now,
            openItemsLeft = current.items.count { !it.checked && it.id != item.id },
        )
        c.session.stores.updateSession(storeId, result.session, result.newScores)
    }

    fun delete(item: Item) {
        val entry = record("„${item.name}” törlése", mapOf(here(item.id) to item))
        c.session.items.delete(storeId, item.id)
        _events.trySend(ListEvent.Deleted(item, entry))
    }

    fun clearChecked() {
        val checked = state.value.checked
        if (checked.isEmpty()) return
        record("Kipipáltak törlése", checked.associate { here(it.id) to it })
        c.session.items.deleteAll(storeId, checked.map { it.id })
    }

    fun setDescription(description: String?) = c.session.stores.setDescription(storeId, description)

    /** Creates a category from the item editor and returns its id, so it can be selected at once. */
    fun createCategory(name: String, emoji: String): String =
        c.session.categories.add(name, emoji, state.value.categories)

    /**
     * Saves an edited item, moving it to [targetStoreId] when that differs from this store.
     * A changed category or icon is remembered for next time.
     */
    fun save(original: Item, edited: Item, targetStoreId: String = storeId) {
        if (targetStoreId != storeId) {
            val target = ItemRef(targetStoreId, original.id)
            record("„${original.name}” áthelyezése", mapOf(here(original.id) to original, target to null))
            c.session.items.writeAll(mapOf(here(original.id) to null, target to edited.copy(createdAt = System.currentTimeMillis())))
            val storeName = otherStores.value.firstOrNull { it.id == targetStoreId }?.name.orEmpty()
            _events.trySend(ListEvent.Moved(edited.name, storeName))
        } else {
            if (edited == original) return
            record("„${original.name}” szerkesztése", mapOf(here(original.id) to original))
            c.session.items.update(storeId, original, edited)
        }
        if (edited.categoryId != original.categoryId || edited.emoji != original.emoji || edited.key != original.key) {
            c.session.learned.remember(edited.name, edited.categoryId, edited.emoji)
        }
    }

    private fun resolveCategory(id: String, categories: List<Category>): String =
        if (categories.any { it.id == id }) id else Categorizer.FALLBACK_CATEGORY_ID
}

/** One pasted line in the confirmation dialog. */
data class PastePreview(
    val input: String,
    val name: String,
    val quantity: String?,
    val emoji: String,
    val status: Status,
) {
    enum class Status { NEW, RESTORE, ON_LIST, DUPLICATE }

    /** Lines that would change the list are pre-selected; the others are shown greyed out. */
    val addable: Boolean get() = status == Status.NEW || status == Status.RESTORE
}
