package hu.szileemester.adamazon.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Shown instead of the app while a newer version waits in Google Play. [onUpdate] restarts Play's
 * own update screen; the store page is the way out when that does not open.
 */
@Composable
fun UpdateScreen(onUpdate: () -> Unit) {
    val context = LocalContext.current
    fun openStore() {
        val id = context.packageName
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$id")).setPackage("com.android.vending")
        try {
            context.startActivity(market)
        } catch (_: ActivityNotFoundException) {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$id"))) }
        }
    }

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("🔄", fontSize = 64.sp)
            Spacer(Modifier.height(24.dp))
            Text("Új verzió érhető el", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            Text(
                "Az Adamazon frissítése szükséges a további használathoz, hogy a csoport minden tagja " +
                    "ugyanazt a verziót használja. A listáitok nem vesznek el.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(32.dp))
            Button(onClick = onUpdate, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Frissítés") }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = ::openStore, modifier = Modifier.fillMaxWidth()) { Text("Megnyitás a Play Áruházban") }
        }
    }
}
