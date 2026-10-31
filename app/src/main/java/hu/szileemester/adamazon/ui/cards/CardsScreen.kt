package hu.szileemester.adamazon.ui.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import hu.szileemester.adamazon.AppContainer
import hu.szileemester.adamazon.domain.LoyaltyCard
import hu.szileemester.adamazon.domain.Store
import hu.szileemester.adamazon.ui.appViewModel
import hu.szileemester.adamazon.ui.components.StoreLogo
import hu.szileemester.adamazon.ui.theme.storeContainerColor
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class CardsViewModel(c: AppContainer) : ViewModel() {
    val cards = combine(c.session.cards.cards, c.session.stores.stores) { cards, stores ->
        cards.map { card -> card to stores.firstOrNull { it.id == card.storeId } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardsScreen(bottomBar: @Composable () -> Unit, openCard: (String) -> Unit, newCard: () -> Unit) {
    val vm = appViewModel { CardsViewModel(it) }
    val cards by vm.cards.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Kártyák") }) },
        bottomBar = bottomBar,
        floatingActionButton = {
            FloatingActionButton(onClick = newCard) { Icon(Icons.Default.Add, contentDescription = "Új kártya") }
        },
    ) { padding ->
        val list = cards
        when {
            list == null -> Unit
            list.isEmpty() -> Text(
                "Még nincs kártya.\nA + gombbal felvehetsz egyet egy fotóval vagy képernyőképpel.",
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
            )
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(160.dp),
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(list, key = { it.first.id }) { (card, store) ->
                    CardTile(card, store, onClick = { openCard(card.id) })
                }
            }
        }
    }
}

@Composable
private fun CardTile(card: LoyaltyCard, store: Store?, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = storeContainerColor(store?.colorArgb ?: 0L)),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 20.dp, horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CardLogo(card, store, size = 56.dp)
            Spacer(Modifier.height(10.dp))
            Text(
                card.name,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

/** The linked store's logo, or the card's initial when it belongs to no store. */
@Composable
fun CardLogo(card: LoyaltyCard, store: Store?, size: Dp) {
    StoreLogo(store ?: Store(id = card.id, name = card.name), size = size)
}

/** Standard ID-1 card proportions (85.6 × 54 mm). */
const val CARD_RATIO = 1.586f
