package hu.szileemester.adamazon.data

import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import hu.szileemester.adamazon.domain.CardBarcode
import hu.szileemester.adamazon.domain.Category
import hu.szileemester.adamazon.domain.Categorizer
import hu.szileemester.adamazon.domain.GroupIcon
import hu.szileemester.adamazon.domain.Item
import hu.szileemester.adamazon.domain.ItemRef
import hu.szileemester.adamazon.domain.LearnedMapping
import hu.szileemester.adamazon.domain.LoyaltyCard
import hu.szileemester.adamazon.domain.Normalizer
import hu.szileemester.adamazon.domain.ShoppingSession
import hu.szileemester.adamazon.domain.Store
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

// Writes are not awaited by callers that only need them queued: Firestore applies them to the
// local cache immediately and syncs when online.
//
// Every repository works inside one group: [group] is groups/{groupId}, and all collections live
// under it (see firestore.rules).

class CategoryRepository(private val db: FirebaseFirestore, group: DocumentReference) {
    private val collection = group.collection("categories")

    val categories: Flow<List<Category>> =
        collection.orderBy("sortIndex").snapshotFlow().map { s -> s.documents.map { it.toCategory() } }

    /** Creates the default categories once, when the server confirms there are none yet. */
    suspend fun seedDefaultsIfEmpty() {
        val existing = runCatching { collection.limit(1).get(Source.SERVER).await() }.getOrNull() ?: return
        if (!existing.isEmpty) return
        val batch = db.batch()
        DEFAULT_CATEGORIES.forEach { batch.set(collection.document(it.id), it.toMap()) }
        batch.commit().await()
    }

    /** Adds a category right before "Egyéb" (which stays last) and returns its id. */
    fun add(name: String, emoji: String, existing: List<Category>): String {
        val doc = collection.document()
        val others = existing.filter { it.id != Categorizer.FALLBACK_CATEGORY_ID }
        val batch = db.batch()
        batch.set(doc, Category(doc.id, name, emoji, others.size).toMap())
        existing.firstOrNull { it.id == Categorizer.FALLBACK_CATEGORY_ID }?.let {
            batch.set(collection.document(it.id), it.copy(sortIndex = others.size + 1).toMap())
        }
        batch.commit()
        return doc.id
    }

    fun update(category: Category) {
        collection.document(category.id).set(category.toMap())
    }

    fun delete(id: String) {
        if (id != Categorizer.FALLBACK_CATEGORY_ID) collection.document(id).delete()
    }

    fun reorder(orderedIds: List<String>) {
        val batch = db.batch()
        orderedIds.forEachIndexed { index, id -> batch.update(collection.document(id), "sortIndex", index) }
        batch.commit()
    }

    companion object {
        val DEFAULT_CATEGORIES = listOf(
            Category("zoldseg", "Zöldség", "🥬", 0),
            Category("gyumolcs", "Gyümölcs", "🍎", 1),
            Category("pekaru", "Pékáru", "🥖", 2),
            Category("hus", "Hús, hal", "🥩", 3),
            Category("tejtermek", "Tejtermék, tojás", "🧀", 4),
            Category("fagyasztott", "Fagyasztott", "🧊", 5),
            Category("tartos", "Tartós élelmiszer", "🥫", 6),
            Category("edesseg", "Édesség, snack", "🍫", 7),
            Category("ital", "Ital", "🥤", 8),
            Category("tisztito", "Tisztítószer", "🧽", 9),
            Category("drogeria", "Drogéria", "🧴", 10),
            Category("haztartas", "Háztartás", "🏠", 11),
            Category(Categorizer.FALLBACK_CATEGORY_ID, "Egyéb", "🛒", 12),
        )
    }
}

class StoreRepository(private val db: FirebaseFirestore, group: DocumentReference) {
    private val collection = group.collection("stores")

    val stores: Flow<List<Store>> =
        collection.orderBy("sortIndex").snapshotFlow().map { s -> s.documents.map { it.toStore() } }

    fun store(id: String): Flow<Store?> =
        collection.document(id).snapshotFlow().map { if (it.exists()) it.toStore() else null }

    fun add(name: String, domain: String?, colorArgb: Long, sortIndex: Int, description: String?): String {
        val doc = collection.document()
        doc.set(
            mapOf(
                "name" to name,
                "domain" to domain,
                "colorArgb" to colorArgb,
                "sortIndex" to sortIndex,
                "orderLocked" to false,
                "description" to description,
            ),
        )
        return doc.id
    }

    fun update(id: String, name: String, domain: String?, colorArgb: Long, description: String?) {
        collection.document(id).update(
            mapOf("name" to name, "domain" to domain, "colorArgb" to colorArgb, "description" to description),
        )
    }

    fun setDescription(id: String, description: String?) {
        collection.document(id).update("description", description)
    }

    fun setLogo(id: String, logo: ByteArray?) {
        collection.document(id).update("logo", logo?.toBlob() ?: FieldValue.delete())
    }

    fun setCategoryOrder(id: String, scores: Map<String, Double>, locked: Boolean) {
        collection.document(id).update(mapOf("categoryScores" to scores, "orderLocked" to locked))
    }

    fun setOrderLocked(id: String, locked: Boolean) {
        collection.document(id).update("orderLocked", locked)
    }

    fun updateSession(id: String, session: ShoppingSession?, newScores: Map<String, Double>?) {
        val fields = mutableMapOf<String, Any>("session" to (session.toMap() ?: FieldValue.delete()))
        if (newScores != null) fields["categoryScores"] = newScores
        collection.document(id).update(fields)
    }

    fun reorder(orderedIds: List<String>) {
        val batch = db.batch()
        orderedIds.forEachIndexed { index, id -> batch.update(collection.document(id), "sortIndex", index) }
        batch.commit()
    }

    suspend fun delete(id: String) {
        val doc = collection.document(id)
        val items = doc.collection("items").get().await()
        items.documents.chunked(400).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { batch.delete(it.reference) }
            batch.commit()
        }
        doc.delete()
    }
}

class ItemRepository(private val db: FirebaseFirestore, private val group: DocumentReference) {
    private fun items(storeId: String) = group.collection("stores").document(storeId).collection("items")

    fun itemsOf(storeId: String): Flow<List<Item>> =
        items(storeId).orderBy("createdAt").snapshotFlow().map { s -> s.documents.map { it.toItem() } }

    /** Adds the item and returns its new id. */
    fun add(storeId: String, item: Item): String {
        val doc = items(storeId).document()
        doc.set(item.toMap())
        return doc.id
    }

    /** A fresh item id, for writes that must know the id up front (batches, undo). */
    fun newId(storeId: String): String = items(storeId).document().id

    /**
     * Writes several items at once, possibly in different stores, as one batch: null deletes.
     * Used for undo (putting earlier states back), moving between stores and multi-line paste.
     */
    fun writeAll(states: Map<ItemRef, Item?>) {
        states.entries.chunked(400).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { (ref, item) ->
                val doc = items(ref.storeId).document(ref.itemId)
                if (item == null) batch.delete(doc) else batch.set(doc, item.toMap())
            }
            batch.commit()
        }
    }

    /**
     * Saves only the fields the user changed: the other user may have ticked the item or changed
     * something else meanwhile, and that must not be overwritten with the editor's old copy.
     */
    fun update(storeId: String, original: Item, edited: Item) {
        val before = original.toMap()
        val changed = edited.toMap().filter { (field, value) -> before[field] != value }
        if (changed.isNotEmpty()) items(storeId).document(edited.id).update(changed)
    }

    fun setChecked(storeId: String, itemId: String, checked: Boolean, now: Long) {
        items(storeId).document(itemId).update(mapOf("checked" to checked, "checkedAt" to if (checked) now else null))
    }

    fun restore(storeId: String, itemId: String, quantity: String?, now: Long) {
        items(storeId).document(itemId).update(
            mapOf("checked" to false, "checkedAt" to null, "quantity" to quantity, "createdAt" to now),
        )
    }

    fun delete(storeId: String, itemId: String) {
        items(storeId).document(itemId).delete()
    }

    fun deleteAll(storeId: String, itemIds: List<String>) {
        itemIds.chunked(400).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { batch.delete(items(storeId).document(it)) }
            batch.commit()
        }
    }
}

class LearnedRepository(group: DocumentReference) {
    private val collection = group.collection("learned")

    /** Keyed by [Normalizer.key] of the item name. */
    val learned: Flow<Map<String, LearnedMapping>> = collection.snapshotFlow().map { s ->
        s.documents.map { it.toLearned() }.associateBy { Normalizer.key(it.displayName) }
    }

    fun remember(name: String, categoryId: String, emoji: String) {
        collection.document(Normalizer.docId(name))
            .set(mapOf("displayName" to name, "categoryId" to categoryId, "emoji" to emoji))
    }
}

/** The group's photo stickers, referenced from emoji fields via [hu.szileemester.adamazon.domain.IconRef]. */
class IconRepository(group: DocumentReference) {
    private val collection = group.collection("icons")

    val icons: Flow<List<GroupIcon>> =
        collection.orderBy("createdAt").snapshotFlow().map { s -> s.documents.map { it.toIcon() } }

    /** Stores a sticker and returns its id. */
    fun add(name: String, image: ByteArray): String {
        val doc = collection.document()
        doc.set(mapOf("name" to name, "image" to image.toBlob(), "createdAt" to System.currentTimeMillis()))
        return doc.id
    }

    fun delete(id: String) {
        collection.document(id).delete()
    }
}

class CardRepository(private val db: FirebaseFirestore, group: DocumentReference) {
    private val collection = group.collection("cards")

    val cards: Flow<List<LoyaltyCard>> =
        collection.orderBy("sortIndex").snapshotFlow().map { s -> s.documents.map { it.toCard() } }

    fun card(id: String): Flow<LoyaltyCard?> =
        collection.document(id).snapshotFlow().map { if (it.exists()) it.toCard() else null }

    /** The card tied to a store, if any – without downloading every other card's thumbnail. */
    fun cardOf(storeId: String): Flow<LoyaltyCard?> =
        collection.whereEqualTo("storeId", storeId).limit(1).snapshotFlow().map { s -> s.documents.firstOrNull()?.toCard() }

    /** The card's photo, or null while it has none. */
    fun image(id: String): Flow<ByteArray?> =
        collection.document(id).collection("images").document(IMAGE_DOC).snapshotFlow()
            .map { it.getBlob("data")?.toBytes() }

    /** A newly picked photo with everything derived from it. */
    class NewImage(val image: ByteArray, val thumb: ByteArray, val barcode: CardBarcode?)

    /** Creates or updates a card; without [newImage] the current photo and barcode stay. */
    fun save(id: String?, name: String, storeId: String?, sortIndex: Int, newImage: NewImage?): String {
        val doc = if (id == null) collection.document() else collection.document(id)
        val batch = db.batch()
        val fields = mutableMapOf<String, Any?>("name" to name, "storeId" to storeId)
        if (id == null) fields["sortIndex"] = sortIndex
        if (newImage != null) {
            fields["thumb"] = newImage.thumb.toBlob()
            fields["barcodeValue"] = newImage.barcode?.value
            fields["barcodeFormat"] = newImage.barcode?.format
            batch.set(doc.collection("images").document(IMAGE_DOC), mapOf("data" to newImage.image.toBlob()))
            // Cards used to have a back side; drop any leftover.
            batch.delete(doc.collection("images").document(LEGACY_BACK_DOC))
        }
        batch.set(doc, fields, SetOptions.merge())
        batch.commit()
        return doc.id
    }

    fun reorder(orderedIds: List<String>) {
        val batch = db.batch()
        orderedIds.forEachIndexed { index, id -> batch.update(collection.document(id), "sortIndex", index) }
        batch.commit()
    }

    fun delete(id: String) {
        val doc = collection.document(id)
        val batch = db.batch()
        listOf(IMAGE_DOC, LEGACY_BACK_DOC).forEach { batch.delete(doc.collection("images").document(it)) }
        batch.delete(doc)
        batch.commit()
    }

    private companion object {
        const val IMAGE_DOC = "front"
        const val LEGACY_BACK_DOC = "back"
    }
}
