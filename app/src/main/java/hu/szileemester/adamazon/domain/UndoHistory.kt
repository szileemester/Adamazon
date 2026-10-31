package hu.szileemester.adamazon.domain

/** Where an item lives: items can move between stores, so undo needs both ids. */
data class ItemRef(val storeId: String, val itemId: String)

/**
 * One undoable list change: the state of every touched item before it happened
 * (null = the item did not exist there yet, so undoing deletes it).
 */
data class UndoEntry(val label: String, val before: Map<ItemRef, Item?>)

/** The last few list changes, newest last; older ones fall off beyond [capacity]. */
data class UndoHistory(val entries: List<UndoEntry> = emptyList(), val capacity: Int = 5) {
    val canUndo: Boolean get() = entries.isNotEmpty()

    fun push(entry: UndoEntry): UndoHistory = copy(entries = (entries + entry).takeLast(capacity))

    /** The newest entry and the history without it; null when there is nothing to undo. */
    fun pop(): Pair<UndoEntry, UndoHistory>? =
        entries.lastOrNull()?.let { it to copy(entries = entries.dropLast(1)) }
}
