package hu.szileemester.adamazon.data

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * A household: everything its members share lives under groups/{id}. [needsLegacyImport] is set
 * for the group that is to take over the pre-group data and has not done so yet.
 */
data class Group(val id: String, val name: String, val needsLegacyImport: Boolean = false)

/** A group member as listed in Settings. */
data class Member(val email: String, val displayName: String)

/**
 * The repositories of one group. Opened when the signed-in user's group is known and replaced
 * when the user is moved to another group, so no screen can reach another group's data.
 */
class GroupSession(db: FirebaseFirestore, val groupId: String) {
    private val group = db.collection("groups").document(groupId)

    val categories = CategoryRepository(db, group)
    val stores = StoreRepository(db, group)
    val items = ItemRepository(db, group)
    val learned = LearnedRepository(group)
    val cards = CardRepository(db, group)
    val icons = IconRepository(group)
    val migration = LegacyMigration(db, group)

    /** The group's members, from their allow-list entries (readable within the group). */
    val members: Flow<List<Member>> =
        db.collection("allowedUsers").whereEqualTo("groupId", groupId).snapshotFlow().map { s ->
            s.documents.map { Member(it.id, it.getString("displayName").orEmpty()) }
                .sortedBy { it.displayName.ifBlank { it.email }.lowercase() }
        }
}
