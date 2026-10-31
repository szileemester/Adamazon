package hu.szileemester.adamazon.data

import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.tasks.await

/**
 * Copies the data from before groups existed (top-level collections) into the group flagged with
 * `legacyImport`, once. Document ids are kept, so cards still point at their stores and running it
 * twice (e.g. on two phones at once) gives the same result. The old data is left in place as a
 * backup; it is read-only by now (see firestore.rules).
 */
class LegacyMigration(private val db: FirebaseFirestore, private val group: DocumentReference) {

    suspend fun run() {
        refused.clear()
        copyAll(db.collection("categories"), group.collection("categories"), CATEGORY_FIELDS)
        copyAll(db.collection("learned"), group.collection("learned"), LEARNED_FIELDS)
        for (store in copyAll(db.collection("stores"), group.collection("stores"), STORE_FIELDS)) {
            copyAll(store.reference.collection("items"), group.collection("stores").document(store.id).collection("items"), ITEM_FIELDS)
        }
        for (card in copyAll(db.collection("cards"), group.collection("cards"), CARD_FIELDS)) {
            // Photos are up to ~1 MB each, so one per batch.
            copyAll(card.reference.collection("images"), group.collection("cards").document(card.id).collection("images"), IMAGE_FIELDS, batchSize = 1)
        }
        check(refused.isEmpty()) { "${refused.size} dokumentumot elutasított a szerver:\n" + refused.joinToString("\n") }
        group.update("migratedAt", System.currentTimeMillis()).await()
    }

    /**
     * Copies every document of [from] into [to] and returns the copied ones. Only the fields the
     * rules accept are kept: documents from early versions may carry fields that are gone since.
     */
    private suspend fun copyAll(
        from: CollectionReference,
        to: CollectionReference,
        fields: Set<String>,
        batchSize: Int = 400,
    ): List<DocumentSnapshot> {
        val docs = from.get(Source.SERVER).await().documents
        fun dataOf(doc: DocumentSnapshot) = doc.data.orEmpty().filterKeys { it in fields }
        docs.chunked(batchSize).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { doc -> batch.set(to.document(doc.id), dataOf(doc)) }
            // A refused batch names no culprit: write one by one, so the rest still gets copied and
            // the refused documents can be reported.
            if (runCatching { batch.commit().await() }.isFailure) {
                chunk.forEach { doc ->
                    runCatching { to.document(doc.id).set(dataOf(doc)).await() }
                        .onFailure { refused += "${doc.reference.path} ${describe(dataOf(doc))}" }
                }
            }
        }
        return docs
    }

    private val refused = mutableListOf<String>()

    private fun describe(data: Map<String, Any>): String =
        data.entries.joinToString(prefix = "{", postfix = "}") { (key, value) ->
            val shown = when (value) {
                is String -> "\"${value.take(40)}\" (${value.length})"
                is Blob -> "blob ${value.toBytes().size} B"
                else -> "$value (${value.javaClass.simpleName})"
            }
            "$key=$shown"
        }

    // Keep in sync with the validX functions in firestore.rules.
    private companion object {
        val CATEGORY_FIELDS = setOf("name", "emoji", "sortIndex")
        val LEARNED_FIELDS = setOf("displayName", "categoryId", "emoji")
        val STORE_FIELDS = setOf("name", "domain", "description", "colorArgb", "logo", "sortIndex", "orderLocked", "categoryScores", "session")
        val ITEM_FIELDS = setOf("name", "quantity", "categoryId", "emoji", "checked", "checkedAt", "createdAt", "createdBy")
        val CARD_FIELDS = setOf("name", "storeId", "sortIndex", "thumb", "barcodeValue", "barcodeFormat")
        val IMAGE_FIELDS = setOf("data")
    }
}
