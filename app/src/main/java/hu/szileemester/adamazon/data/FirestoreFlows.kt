package hu.szileemester.adamazon.data

import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Live query results; local (pending) writes are included so the UI updates instantly, also offline.
 * Losing access (the user was moved to another group or removed) just ends the flow: the screens
 * are about to go away anyway, and must not crash on the way.
 */
fun Query.snapshotFlow(): Flow<QuerySnapshot> = callbackFlow {
    val registration = addSnapshotListener(MetadataChanges.EXCLUDE) { snapshot, error ->
        if (error != null) close(error.unlessDenied()) else if (snapshot != null) trySend(snapshot)
    }
    awaitClose { registration.remove() }
}

fun DocumentReference.snapshotFlow(): Flow<DocumentSnapshot> = callbackFlow {
    val registration = addSnapshotListener { snapshot, error ->
        if (error != null) close(error.unlessDenied()) else if (snapshot != null) trySend(snapshot)
    }
    awaitClose { registration.remove() }
}

private fun FirebaseFirestoreException.unlessDenied(): FirebaseFirestoreException? =
    takeUnless { code == FirebaseFirestoreException.Code.PERMISSION_DENIED }
