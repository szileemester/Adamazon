package hu.szileemester.adamazon.ui.stores

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import hu.szileemester.adamazon.AppContainer
import hu.szileemester.adamazon.data.StorePreset
import hu.szileemester.adamazon.domain.Item
import hu.szileemester.adamazon.domain.Store
import hu.szileemester.adamazon.ui.appViewModel
import hu.szileemester.adamazon.ui.components.ItemIcon
import hu.szileemester.adamazon.ui.components.StoreLogo
import hu.szileemester.adamazon.ui.theme.storeContainerColor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class StoreSummary(val store: Store, val openItems: List<Item>)

@OptIn(ExperimentalCoroutinesApi::class)
class StoresViewModel(private val c: AppContainer) : ViewModel() {
    val summaries = c.session.stores.stores.flatMapLatest { stores ->
        if (stores.isEmpty()) return@flatMapLatest flowOf(emptyList())
        combine(stores.map { store -> c.session.items.itemsOf(store.id).map { items -> StoreSummary(store, items.filter { !it.checked }) } }) {
            it.toList()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val presets: List<StorePreset> get() = c.presets

    fun addStore(name: String, domain: String?, colorArgb: Long, description: String?) {
        val id = c.session.stores.add(name, domain, colorArgb, sortIndex = summaries.value?.size ?: 0, description = description)
        if (!domain.isNullOrBlank()) {
            // Taken now: the logo arrives later, possibly after a switch to another group.
            val stores = c.session.stores
            c.appScope.launch { c.logos.fetch(domain)?.let { stores.setLogo(id, it) } }
        }
    }

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoresScreen(bottomBar: @Composable () -> Unit, openStore: (String) -> Unit, openOrder: () -> Unit) {
    val vm = appViewModel { StoresViewModel(it) }
    val summaries by vm.summaries.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Adamazon") },
                actions = {
                    IconButton(onClick = openOrder) {
                        Icon(Icons.Default.SwapVert, contentDescription = "Boltok sorrendje")
                    }
                },
            )
        },
        bottomBar = bottomBar,
        floatingActionButton = {
            FloatingActionButton(onClick = { adding = true }) { Icon(Icons.Default.Add, contentDescription = "Új üzlet") }
        },
    ) { padding ->
        val list = summaries
        when {
            list == null -> Unit
            list.isEmpty() -> Text(
                "Még nincs üzlet.\nA + gombbal vehetsz fel egyet, pl. Auchan, DM vagy Foxpost.",
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
            )
            else -> StoreGrid(list, Modifier.padding(padding), openStore)
        }
    }

    if (adding) {
        AddStoreDialog(
            presets = vm.presets,
            onDismiss = { adding = false },
            onAdd = { name, domain, color, description ->
                vm.addStore(name, domain, color, description)
                adding = false
            },
        )
    }
}

/** Keep-style grid in the stores' order of importance; the order itself is set in [StoreOrderScreen]. */
@Composable
private fun StoreGrid(summaries: List<StoreSummary>, modifier: Modifier, openStore: (String) -> Unit) {
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Adaptive(160.dp),
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalItemSpacing = 12.dp,
    ) {
        items(summaries, key = { it.store.id }) { summary ->
            StoreCard(summary = summary, onClick = { openStore(summary.store.id) }, modifier = Modifier.animateItem())
        }
    }
}

@Composable
private fun StoreCard(summary: StoreSummary, onClick: () -> Unit, modifier: Modifier) {
    val store = summary.store
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = storeContainerColor(store.colorArgb)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = if (store.colorArgb == 0L) CardDefaults.outlinedCardBorder() else null,
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StoreLogo(store, size = 32.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    store.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (summary.openItems.isNotEmpty()) Badge { Text("${summary.openItems.size}") }
            }
            if (summary.openItems.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                summary.openItems.take(PREVIEW_COUNT).forEach { item ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ItemIcon(item.emoji, size = 18.dp)
                        Spacer(Modifier.width(6.dp))
                        Text(item.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                val more = summary.openItems.size - PREVIEW_COUNT
                if (more > 0) {
                    Text("+$more további", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private const val PREVIEW_COUNT = 5
