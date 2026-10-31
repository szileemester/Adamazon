package hu.szileemester.adamazon.data

import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Live query results; local (pending) writes are included so the UI updates instantly, also offline.
 *
 * A listener error never reaches the screens (an uncaught one would crash the app): the flow just
 * ends, keeping the last data. Losing access (moved to another group, removed) needs no message –
 * the screens are about to go away anyway; anything else is reported through [DataErrors].
 */
fun Query.snapshotFlow(): Flow<QuerySnapshot> = callbackFlow {
    val registration = addSnapshotListener(MetadataChanges.EXCLUDE) { snapshot, error ->
        if (error != null) end(error) else if (snapshot != null) trySend(snapshot)
    }
    awaitClose { registration.remove() }
}

fun DocumentReference.snapshotFlow(): Flow<DocumentSnapshot> = callbackFlow {
    val registration = addSnapshotListener { snapshot, error ->
        if (error != null) end(error) else if (snapshot != null) trySend(snapshot)
    }
    awaitClose { registration.remove() }
}

private fun <T> ProducerScope<T>.end(error: FirebaseFirestoreException) {
    if (error.code != FirebaseFirestoreException.Code.PERMISSION_DENIED) DataErrors.report(error)
    close()
}

/** Data errors to show the user (see AppRoot). */
object DataErrors {
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val messages: SharedFlow<String> = _messages

    fun report(error: FirebaseFirestoreException) {
        _messages.tryEmit(
            when (error.code) {
                FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED ->
                    "Elfogyott a mai ingyenes adatkeret. Holnap reggel újra működik."
                else -> "Nem sikerült betölteni az adatokat (${error.code.name})."
            },
        )
    }
}
