package hu.szileemester.adamazon.domain

data class Category(
    val id: String,
    val name: String,
    val emoji: String,
    val sortIndex: Int,
)

data class ShoppingSession(
    val startedAt: Long,
    val lastActivityAt: Long,
    /** Category ids in the order they were first checked during this session. */
    val sequence: List<String>,
)

data class Store(
    val id: String,
    val name: String,
    val domain: String? = null,
    val colorArgb: Long = 0,
    val logo: ByteArray? = null,
    val sortIndex: Int = 0,
    val categoryScores: Map<String, Double> = emptyMap(),
    val orderLocked: Boolean = false,
    val session: ShoppingSession? = null,
) {
    override fun equals(other: Any?): Boolean =
        other is Store && id == other.id && name == other.name && domain == other.domain &&
            colorArgb == other.colorArgb && logo.contentEqualsNullable(other.logo) &&
            sortIndex == other.sortIndex && categoryScores == other.categoryScores && orderLocked == other.orderLocked &&
            session == other.session

    override fun hashCode(): Int = id.hashCode() * 31 + name.hashCode()
}

data class Item(
    val id: String,
    val name: String,
    val quantity: String? = null,
    val categoryId: String,
    val emoji: String,
    val checked: Boolean = false,
    val checkedAt: Long? = null,
    val createdAt: Long = 0,
    val createdBy: String? = null,
) {
    val key: String get() = Normalizer.key(name)
}

data class LoyaltyCard(
    val id: String,
    val name: String,
    val storeId: String? = null,
    val thumb: ByteArray? = null,
    val sortIndex: Int = 0,
) {
    override fun equals(other: Any?): Boolean =
        other is LoyaltyCard && id == other.id && name == other.name && storeId == other.storeId &&
            thumb.contentEqualsNullable(other.thumb) && sortIndex == other.sortIndex

    override fun hashCode(): Int = id.hashCode() * 31 + name.hashCode()
}

data class LearnedMapping(
    val displayName: String,
    val categoryId: String,
    val emoji: String,
)

private fun ByteArray?.contentEqualsNullable(other: ByteArray?): Boolean =
    if (this == null) other == null else other != null && contentEquals(other)
