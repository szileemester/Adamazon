package hu.szileemester.adamazon.ui.cards

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import hu.szileemester.adamazon.AppContainer
import hu.szileemester.adamazon.ui.appViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class CardViewerViewModel(c: AppContainer, cardId: String) : ViewModel() {
    val card = combine(c.session.cards.card(cardId), c.session.stores.stores) { card, stores ->
        card?.let { it to stores.firstOrNull { store -> store.id == it.storeId } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val image = c.session.cards.image(cardId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

/**
 * Full screen at full brightness for the till: the store's logo at the top and the barcode
 * redrawn from the photo (scans most reliably). Without a recognised barcode the photo is shown.
 */
@Composable
fun CardViewerScreen(cardId: String, back: () -> Unit, edit: () -> Unit) {
    val vm = appViewModel(key = "card-$cardId") { CardViewerViewModel(it, cardId) }
    val loaded by vm.card.collectAsStateWithLifecycle()
    val image by vm.image.collectAsStateWithLifecycle()
    var showPhoto by rememberSaveable { mutableStateOf(false) }
    MaxBrightness()

    val (card, store) = loaded ?: (null to null)
    val barcode = card?.barcode
    Box(Modifier.fillMaxSize().background(Color.Black).systemBarsPadding()) {
        if (barcode != null && !showPhoto) {
            BarcodeView(barcode, Modifier.align(Alignment.Center).fillMaxWidth().padding(16.dp), showValue = false)
        } else {
            image?.let {
                AsyncImage(it, contentDescription = card?.name, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(8.dp))
            }
        }
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = back) { Icon(Icons.Default.Close, contentDescription = "Bezárás", tint = Color.White) }
            if (card != null) {
                CardLogo(card, store, size = 36.dp)
                Spacer(Modifier.width(12.dp))
            }
            Text(
                card?.name.orEmpty(),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = edit) { Icon(Icons.Default.Edit, contentDescription = "Szerkesztés", tint = Color.White) }
        }
        if (barcode != null && image != null) {
            TextButton(onClick = { showPhoto = !showPhoto }, modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)) {
                Text(if (showPhoto) "Vonalkód mutatása" else "Eredeti kép mutatása", color = Color.White)
            }
        }
    }
}

@Composable
private fun MaxBrightness() {
    val activity = LocalContext.current as? Activity ?: return
    DisposableEffect(activity) {
        val window = activity.window
        val previous = window.attributes.screenBrightness
        window.attributes = window.attributes.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL }
        onDispose {
            window.attributes = window.attributes.apply { screenBrightness = previous }
        }
    }
}
