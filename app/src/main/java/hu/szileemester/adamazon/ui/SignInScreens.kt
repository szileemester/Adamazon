package hu.szileemester.adamazon.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import hu.szileemester.adamazon.R
import kotlinx.coroutines.launch

@Composable
fun SignInScreen() {
    val container = appContainer()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    CenteredMessage(
        title = "Adamazon",
        message = "Közös bevásárlólista. Jelentkezz be a Google-fiókoddal.",
    ) {
        Button(
            enabled = !busy,
            onClick = {
                busy = true
                scope.launch {
                    error = container.auth.signIn(context)
                    busy = false
                }
            },
        ) { Text(if (busy) "Bejelentkezés…" else "Bejelentkezés Google-fiókkal") }
        error?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun NoAccessScreen(email: String) {
    val container = appContainer()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    CenteredMessage(
        title = "Nincs hozzáférésed",
        message = "A(z) $email fiók nincs az engedélyezett felhasználók között. " +
            "Kérd meg az alkalmazás gazdáját, hogy vegyen fel.",
    ) {
        Button(onClick = container.auth::refresh) { Text("Újra próbálom") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { scope.launch { container.auth.signOut(context) } }) { Text("Másik fiók") }
    }
}

@Composable
fun ErrorScreen(message: String, onRetry: (() -> Unit)? = null) {
    val container = appContainer()
    CenteredMessage(title = "Nem sikerült csatlakozni", message = message) {
        Button(onClick = onRetry ?: container.auth::refresh) { Text("Újra") }
    }
}

@Composable
fun NoGroupScreen(email: String) {
    val container = appContainer()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // No retry button: the app notices by itself when the account is put into a group.
    CenteredMessage(
        title = "Nincs csoporthoz rendelve",
        message = "A(z) $email fiók engedélyezett, de még nem tartozik egyik csoporthoz sem. " +
            "Kérd meg az alkalmazás gazdáját, hogy tegyen be egy csoportba.",
    ) {
        OutlinedButton(onClick = { scope.launch { container.auth.signOut(context) } }) { Text("Másik fiók") }
    }
}

@Composable
private fun CenteredMessage(title: String, message: String, actions: @Composable () -> Unit) {
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, Modifier.size(160.dp))
            Text(title, style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            Text(message, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(24.dp))
            actions()
        }
    }
}
