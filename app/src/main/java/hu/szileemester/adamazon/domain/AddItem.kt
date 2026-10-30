package hu.szileemester.adamazon.domain

sealed interface AddItemDecision {
    data class Create(val name: String, val quantity: String?) : AddItemDecision
    data class Restore(val item: Item, val quantity: String?) : AddItemDecision
    data class AlreadyOnList(val item: Item) : AddItemDecision
}

/** Decides what quick-add input should do given the items already in the store. */
fun decideAddItem(input: String, items: List<Item>): AddItemDecision? {
    if (input.isBlank()) return null
    val parsed = QuantityParser.parse(input)
    if (parsed.name.isBlank()) return null
    val key = Normalizer.key(parsed.name)
    val sameName = items.filter { it.key == key }
    sameName.firstOrNull { !it.checked }?.let { return AddItemDecision.AlreadyOnList(it) }
    sameName.maxByOrNull { it.checkedAt ?: 0 }?.let {
        return AddItemDecision.Restore(it, parsed.quantity ?: it.quantity)
    }
    return AddItemDecision.Create(parsed.name, parsed.quantity)
}

/** Name suggestions for quick-add: previously bought items of this store first, then learned names. */
fun suggestions(
    query: String,
    items: List<Item>,
    learned: Collection<LearnedMapping>,
    limit: Int = 6,
): List<String> {
    val key = Normalizer.key(QuantityParser.parse(query).name)
    if (key.length < 2) return emptyList()
    val open = items.filter { !it.checked }.map { it.key }.toSet()
    val candidates = items.filter { it.checked }.sortedByDescending { it.checkedAt ?: 0 }.map { it.name } +
        learned.map { it.displayName }
    return candidates
        .distinctBy { Normalizer.key(it) }
        .filter {
            val candidate = Normalizer.key(it)
            candidate != key && candidate !in open && (candidate.startsWith(key) || " $key" in " $candidate")
        }
        .take(limit)
}
