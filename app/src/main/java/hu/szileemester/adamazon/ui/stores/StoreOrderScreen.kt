package hu.szileemester.adamazon.ui.stores

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import hu.szileemester.adamazon.AppContainer
import hu.szileemester.adamazon.domain.Store
import hu.szileemester.adamazon.ui.appViewModel
import hu.szileemester.adamazon.ui.components.StoreLogo
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

class StoreOrderViewModel(private val c: AppContainer) : ViewModel() {
    val stores = c.session.stores.stores.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun save(order: List<Store>) = c.session.stores.reorder(order.map { it.id })
}

/**
 * Sets the stores' order of importance with drag handles. The main screen keeps its Keep-style
 * grid; a plain numbered list makes the ranking unambiguous while editing it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoreOrderScreen(back: () -> Unit) {
    val vm = appViewModel { StoreOrderViewModel(it) }
    val stores by vm.stores.collectAsStateWithLifecycle()

    // Local copy while dragging; replaced whenever Firestore sends a new list.
    var order by remember { mutableStateOf(stores) }
    LaunchedEffect(stores) { order = stores }
    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val fromIndex = order.indexOfFirst { it.id == from.key }
        val toIndex = order.indexOfFirst { it.id == to.key }
        if (fromIndex >= 0 && toIndex >= 0) order = order.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Boltok sorrendje") },
                navigationIcon = {
                    IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Vissza") }
                },
            )
        },
    ) { padding ->
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(padding)) {
            item(key = "hint") {
                Text(
                    "Fogd meg a jobb oldali fogantyút, és húzd a boltot a helyére. A legfontosabb legyen felül; " +
                        "a főképernyőn is ebben a sorrendben jelennek meg.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
            items(order, key = { it.id }) { store ->
                ReorderableItem(reorderState, key = store.id) { dragging ->
                    val position = order.indexOfFirst { it.id == store.id } + 1
                    ListItem(
                        leadingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "$position.",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.widthIn(min = 28.dp),
                                )
                                Spacer(Modifier.width(12.dp))
                                StoreLogo(store, size = 40.dp)
                            }
                        },
                        headlineContent = { Text(store.name, style = MaterialTheme.typography.titleMedium) },
                        trailingContent = {
                            Icon(
                                Icons.Default.DragHandle,
                                contentDescription = "Áthelyezés",
                                modifier = Modifier.draggableHandle(onDragStopped = { vm.save(order) }),
                            )
                        },
                        tonalElevation = if (dragging) 6.dp else 0.dp,
                        shadowElevation = if (dragging) 6.dp else 0.dp,
                    )
                }
            }
        }
    }
}
