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
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import hu.szileemester.adamazon.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

sealed interface AccessState {
    data object Loading : AccessState
    data object SignedOut : AccessState
    data class NoAccess(val email: String) : AccessState
    data class Allowed(val user: FirebaseUser) : AccessState
    data class Error(val message: String) : AccessState
}

/**
 * Google sign-in plus the allow-list check. Being signed in is not enough: the user's e-mail
 * must exist in `allowedUsers`, which the Firestore security rules enforce on the server too.
 */
class AuthRepository(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<AccessState>(AccessState.Loading)
    val state: StateFlow<AccessState> = _state.asStateFlow()

    init {
        auth.addAuthStateListener { scope.launch { refresh() } }
    }

    suspend fun refresh() {
        val user = auth.currentUser ?: run {
            _state.value = AccessState.SignedOut
            return
        }
        val email = user.email?.lowercase() ?: run {
            _state.value = AccessState.NoAccess("")
            return
        }
        _state.value = AccessState.Loading
        _state.value = try {
            val allowed = db.collection("allowedUsers").document(email).get().await().exists()
            if (allowed) AccessState.Allowed(user) else AccessState.NoAccess(email)
        } catch (e: FirebaseFirestoreException) {
            if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                AccessState.NoAccess(email)
            } else {
                AccessState.Error(e.message ?: e.code.name)
            }
        }
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
