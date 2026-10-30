package hu.szileemester.adamazon.ui.cards

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import hu.szileemester.adamazon.AppContainer
import hu.szileemester.adamazon.data.CardSide
import hu.szileemester.adamazon.ui.appViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

class CardViewerViewModel(c: AppContainer, cardId: String) : ViewModel() {
    val card = c.cards.card(cardId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val images = c.cards.images(cardId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

/** Full-screen card on black, at full brightness so the till's scanner can read it. */
@Composable
fun CardViewerScreen(cardId: String, back: () -> Unit, edit: () -> Unit) {
    val vm = appViewModel(key = "card-$cardId") { CardViewerViewModel(it, cardId) }
    val card by vm.card.collectAsStateWithLifecycle()
    val images by vm.images.collectAsStateWithLifecycle()
    MaxBrightness()

    val pages = CardSide.entries.mapNotNull { side -> images?.get(side) }
    Box(Modifier.fillMaxSize().background(Color.Black).systemBarsPadding()) {
        if (pages.isNotEmpty()) {
            val pager = rememberPagerState { pages.size }
            HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
                AsyncImage(
                    pages[page],
                    contentDescription = card?.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                )
            }
            if (pages.size > 1) {
                Text(
                    if (pager.currentPage == 0) "Előlap · lapozz a hátlaphoz" else "Hátlap",
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp),
                )
            }
        } else if (images != null) {
            Text("Ehhez a kártyához nincs kép.", color = Color.White, modifier = Modifier.align(Alignment.Center))
        }
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = back) { Icon(Icons.Default.Close, contentDescription = "Bezárás", tint = Color.White) }
            Text(card?.name.orEmpty(), color = Color.White, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = edit) { Icon(Icons.Default.Edit, contentDescription = "Szerkesztés", tint = Color.White) }
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
