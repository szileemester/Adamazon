package hu.szileemester.adamazon.domain

/**
 * Per-store category ordering.
 *
 * Every category has a score in [0, 1]; lower comes first. A store without scores uses the shared
 * default order. During a shopping session we record the order in which categories are first
 * checked; when the session ends the observed positions are blended into the scores.
 */
object CategoryOrder {
    const val SESSION_TIMEOUT_MS = 3 * 60 * 60 * 1000L
    const val LEARNING_RATE = 0.3

    fun ordered(categories: List<Category>, scores: Map<String, Double>): List<Category> {
        val defaults = defaultScores(categories)
        return categories.sortedWith(
            compareBy<Category> { scores[it.id] ?: defaults.getValue(it.id) }.thenBy { it.sortIndex },
        )
    }

    /** Scores that reproduce [orderedIds] exactly – used when the order is set by hand. */
    fun scoresFromOrder(orderedIds: List<String>): Map<String, Double> {
        val last = (orderedIds.size - 1).coerceAtLeast(1)
        return orderedIds.mapIndexed { index, id -> id to index.toDouble() / last }.toMap()
    }

    data class CheckResult(
        val session: ShoppingSession?,
        /** Non-null when a session was finished and the store's scores should be replaced. */
        val newScores: Map<String, Double>?,
    )

    /**
     * Records that an item of [categoryId] was checked at [now] in a store.
     * [openItemsLeft] is the number of unchecked items after this check; reaching zero ends the session.
     */
    fun onItemChecked(
        store: Store,
        categories: List<Category>,
        categoryId: String,
        now: Long,
        openItemsLeft: Int,
    ): CheckResult {
        var scores: Map<String, Double>? = null
        val previous = store.session
        val session = if (previous == null || now - previous.lastActivityAt > SESSION_TIMEOUT_MS) {
            if (previous != null) scores = finish(store, categories, previous)
            ShoppingSession(startedAt = now, lastActivityAt = now, sequence = listOf(categoryId))
        } else {
            val sequence = if (categoryId in previous.sequence) previous.sequence else previous.sequence + categoryId
            previous.copy(lastActivityAt = now, sequence = sequence)
        }

        if (openItemsLeft == 0) {
            val base = if (scores != null) store.copy(categoryScores = scores) else store
            return CheckResult(session = null, newScores = finish(base, categories, session) ?: scores)
        }
        return CheckResult(session, scores)
    }

    /** Blends a finished session into the store's scores; null when nothing should change. */
    fun finish(store: Store, categories: List<Category>, session: ShoppingSession): Map<String, Double>? {
        if (store.orderLocked || session.sequence.size < 2) return null
        // Blend into the raw scores (not their ranks), so repeated sessions accumulate.
        val defaults = defaultScores(categories)
        val current = categories.associate { it.id to (store.categoryScores[it.id] ?: defaults.getValue(it.id)) }
        val observed = scoresFromOrder(session.sequence.filter { it in current })
        return current.mapValues { (id, old) ->
            val seen = observed[id] ?: return@mapValues old
            old * (1 - LEARNING_RATE) + seen * LEARNING_RATE
        }
    }

    private fun defaultScores(categories: List<Category>): Map<String, Double> =
        scoresFromOrder(categories.sortedBy { it.sortIndex }.map { it.id })
}
