package hu.szileemester.adamazon.data

import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.DocumentSnapshot
import hu.szileemester.adamazon.domain.CardBarcode
import hu.szileemester.adamazon.domain.Category
import hu.szileemester.adamazon.domain.GroupIcon
import hu.szileemester.adamazon.domain.Item
import hu.szileemester.adamazon.domain.LearnedMapping
import hu.szileemester.adamazon.domain.LoyaltyCard
import hu.szileemester.adamazon.domain.ShoppingSession
import hu.szileemester.adamazon.domain.Store

// Firestore documents are mapped by hand: no reflection, so R8 needs no keep rules.

fun DocumentSnapshot.toCategory() = Category(
    id = id,
    name = getString("name").orEmpty(),
    emoji = getString("emoji").orEmpty(),
    sortIndex = getLong("sortIndex")?.toInt() ?: 0,
)

fun Category.toMap(): Map<String, Any> = mapOf("name" to name, "emoji" to emoji, "sortIndex" to sortIndex)

fun DocumentSnapshot.toStore(): Store {
    @Suppress("UNCHECKED_CAST")
    val scores = (get("categoryScores") as? Map<String, Any?>).orEmpty()
        .mapNotNull { (key, value) -> (value as? Number)?.let { key to it.toDouble() } }
        .toMap()
    @Suppress("UNCHECKED_CAST")
    val session = (get("session") as? Map<String, Any?>)?.let {
        ShoppingSession(
            startedAt = (it["startedAt"] as? Number)?.toLong() ?: 0,
            lastActivityAt = (it["lastActivityAt"] as? Number)?.toLong() ?: 0,
            sequence = (it["sequence"] as? List<*>).orEmpty().filterIsInstance<String>(),
        )
    }
    return Store(
        id = id,
        name = getString("name").orEmpty(),
        domain = getString("domain"),
        description = getString("description"),
        colorArgb = getLong("colorArgb") ?: 0,
        logo = getBlob("logo")?.toBytes(),
        sortIndex = getLong("sortIndex")?.toInt() ?: 0,
        categoryScores = scores,
        orderLocked = getBoolean("orderLocked") ?: false,
        session = session,
    )
}

fun ShoppingSession?.toMap(): Map<String, Any>? = this?.let {
    mapOf("startedAt" to startedAt, "lastActivityAt" to lastActivityAt, "sequence" to sequence)
}

fun DocumentSnapshot.toItem() = Item(
    id = id,
    name = getString("name").orEmpty(),
    quantity = getString("quantity"),
    categoryId = getString("categoryId").orEmpty(),
    emoji = getString("emoji").orEmpty(),
    checked = getBoolean("checked") ?: false,
    checkedAt = getLong("checkedAt"),
    createdAt = getLong("createdAt") ?: 0,
    createdBy = getString("createdBy"),
)

fun Item.toMap(): Map<String, Any?> = mapOf(
    "name" to name,
    "quantity" to quantity,
    "categoryId" to categoryId,
    "emoji" to emoji,
    "checked" to checked,
    "checkedAt" to checkedAt,
    "createdAt" to createdAt,
    "createdBy" to createdBy,
)

fun DocumentSnapshot.toLearned() = LearnedMapping(
    displayName = getString("displayName").orEmpty(),
    categoryId = getString("categoryId").orEmpty(),
    emoji = getString("emoji").orEmpty(),
)

fun DocumentSnapshot.toCard() = LoyaltyCard(
    id = id,
    name = getString("name").orEmpty(),
    storeId = getString("storeId"),
    thumb = getBlob("thumb")?.toBytes(),
    sortIndex = getLong("sortIndex")?.toInt() ?: 0,
    barcode = getString("barcodeValue")?.let { value ->
        getString("barcodeFormat")?.let { format -> CardBarcode(value, format) }
    },
)

fun DocumentSnapshot.toIcon() = GroupIcon(
    id = id,
    name = getString("name").orEmpty(),
    image = getBlob("image")?.toBytes() ?: ByteArray(0),
    createdAt = getLong("createdAt") ?: 0,
)

fun ByteArray.toBlob(): Blob = Blob.fromBytes(this)
