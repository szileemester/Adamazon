package hu.szileemester.adamazon.data

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.MetadataChanges
import hu.szileemester.adamazon.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

sealed interface AccessState {
    data object Loading : AccessState
    data object SignedOut : AccessState
    data class NoAccess(val email: String) : AccessState
    /** On the allow-list, but not (yet) put into a group. */
    data class NoGroup(val email: String) : AccessState
    data class Allowed(val user: FirebaseUser, val group: Group) : AccessState
    data class Error(val message: String) : AccessState
}

/**
 * Google sign-in plus the allow-list and group check. Being signed in is not enough: the user's
 * e-mail must exist in `allowedUsers` with a `groupId`, which the Firestore security rules enforce
 * on the server too. Both the entry and the group are watched live, so a renamed group, a move to
 * another group or a removal from the list takes effect at once.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AuthRepository(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<AccessState>(AccessState.Loading)
    val state: StateFlow<AccessState> = _state.asStateFlow()
    private var watch: Job? = null

    init {
        auth.addAuthStateListener { refresh() }
    }

    /** (Re)starts watching the signed-in user's allow-list entry and group. */
    fun refresh() {
        watch?.cancel()
        val user = auth.currentUser ?: run {
            _state.value = AccessState.SignedOut
            return
        }
        val email = user.email?.lowercase() ?: run {
            _state.value = AccessState.NoAccess("")
            return
        }
        _state.value = AccessState.Loading
        watch = scope.launch {
            db.collection("allowedUsers").document(email).changes()
                .flatMapLatest { entry -> access(user, email, entry) }
                .catch { e -> emit(failure(e, email)) }
                .collect { _state.value = it }
        }
    }

    private fun access(user: FirebaseUser, email: String, entry: DocumentSnapshot): Flow<AccessState> {
        // Nothing cached yet (first start): wait for the server instead of saying "no access".
        if (!entry.exists()) return flowOf(if (entry.metadata.isFromCache) AccessState.Loading else AccessState.NoAccess(email))
        val groupId = entry.getString("groupId").orEmpty()
        if (groupId.isBlank()) return flowOf(AccessState.NoGroup(email))
        return db.collection("groups").document(groupId).changes()
            .map { group ->
                when {
                    group.exists() -> AccessState.Allowed(user, group.toGroup())
                    group.metadata.isFromCache -> AccessState.Loading
                    else -> AccessState.NoGroup(email)
                }
            }
            // Moved to another group: the old group may be refused before the new entry arrives.
            .catch { e -> if (e.isDenied()) emit(AccessState.Loading) else throw e }
    }

    private fun DocumentSnapshot.toGroup() = Group(
        id = id,
        name = getString("name").orEmpty().ifBlank { id },
        needsLegacyImport = getBoolean("legacyImport") == true && !contains("migratedAt"),
    )

    private fun failure(e: Throwable, email: String): AccessState =
        if (e.isDenied()) AccessState.NoAccess(email) else AccessState.Error(e.message ?: e.javaClass.simpleName)

    private fun Throwable.isDenied() =
        this is FirebaseFirestoreException && code == FirebaseFirestoreException.Code.PERMISSION_DENIED

    // Metadata changes are included so that "not cached" is followed by the server's answer even
    // when the document is missing on both sides.
    private fun DocumentReference.changes(): Flow<DocumentSnapshot> = callbackFlow {
        val registration = addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
            if (error != null) close(error) else if (snapshot != null) trySend(snapshot)
        }
        awaitClose { registration.remove() }
    }

    /** Shows the Google account picker. Returns an error message, or null on success or cancel. */
    suspend fun signIn(activityContext: Context): String? = try {
        val clientId = activityContext.getString(R.string.default_web_client_id)
        // The committed google-services.json is a placeholder; Google then rejects sign-in with a
        // generic "cancelled", so say what is actually wrong.
        if (PLACEHOLDER_MARKER in clientId) return CONFIG_MISSING
        val option = GetSignInWithGoogleOption.Builder(clientId).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val credential = CredentialManager.create(activityContext).getCredential(activityContext, request).credential
        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
            auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
            null
        } else {
            "Ismeretlen bejelentkezési mód."
        }
    } catch (_: GetCredentialCancellationException) {
        // Also reported when Google rejects the app (e.g. unregistered SHA-1), not only on Back.
        "A bejelentkezés megszakadt. Ha nem te léptél ki, ellenőrizd a Firebase-ben az SHA-1 ujjlenyomatot (SETUP.md)."
    } catch (e: Exception) {
        e.message ?: e.javaClass.simpleName
    }

    private companion object {
        const val PLACEHOLDER_MARKER = "placeholder"
        const val CONFIG_MISSING =
            "A Firebase még nincs beállítva: cseréld le az app/google-services.json fájlt a saját projektedére (SETUP.md 1. lépés)."
    }

    suspend fun signOut(context: Context) {
        auth.signOut()
        runCatching { CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest()) }
    }
}
