package hu.szileemester.adamazon.ui.list

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import hu.szileemester.adamazon.AppContainer
import hu.szileemester.adamazon.data.CategoryRepository
import hu.szileemester.adamazon.domain.Category
import hu.szileemester.adamazon.domain.CategoryOrder
import hu.szileemester.adamazon.domain.Normalizer
import hu.szileemester.adamazon.domain.Store
import hu.szileemester.adamazon.ui.appViewModel
import hu.szileemester.adamazon.ui.components.ColorPicker
import hu.szileemester.adamazon.ui.components.ConfirmDialog
import hu.szileemester.adamazon.ui.components.StoreDescriptionField
import hu.szileemester.adamazon.ui.components.StoreLogo
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

class StoreSettingsViewModel(private val c: AppContainer, private val storeId: String) : ViewModel() {
    val state = combine(
        c.stores.store(storeId),
        c.categories.categories.map { it.ifEmpty { CategoryRepository.DEFAULT_CATEGORIES } },
    ) { store, categories ->
        store to CategoryOrder.ordered(categories, store?.categoryScores.orEmpty())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun save(name: String, domain: String?, color: Long, description: String?) =
        c.stores.update(storeId, name, domain, color, description)

    suspend fun fetchLogo(domain: String): Boolean {
        val logo = c.logos.fetch(domain) ?: return false
        c.stores.setLogo(storeId, logo)
        return true
    }

    fun pickLogo(uri: Uri) = viewModelScope.launch {
        runCatching { c.images.logo(uri) }.onSuccess { c.stores.setLogo(storeId, it) }
    }

    fun removeLogo() = c.stores.setLogo(storeId, null)

    fun setOrder(ids: List<String>, locked: Boolean) =
        c.stores.setCategoryOrder(storeId, CategoryOrder.scoresFromOrder(ids), locked)

    fun setLocked(locked: Boolean) = c.stores.setOrderLocked(storeId, locked)

    fun resetOrder() = c.stores.setCategoryOrder(storeId, emptyMap(), locked = false)

    fun delete() {
        c.appScope.launch { c.stores.delete(storeId) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoreSettingsScreen(storeId: String, back: () -> Unit, deleted: () -> Unit) {
    val vm = appViewModel(key = "settings-$storeId") { StoreSettingsViewModel(it, storeId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val (store, ordered) = state ?: return
    if (store == null) return
    StoreSettingsContent(store, ordered, vm, back, deleted)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StoreSettingsContent(
    store: Store,
    ordered: List<Category>,
    vm: StoreSettingsViewModel,
    back: () -> Unit,
    deleted: () -> Unit,
) {
    var name by remember { mutableStateOf(store.name) }
    var domain by remember { mutableStateOf(store.domain.orEmpty()) }
    var description by remember { mutableStateOf(store.description.orEmpty()) }
    var color by remember { mutableLongStateOf(store.colorArgb) }
    var confirmDelete by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val pickLogo = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(vm::pickLogo)
    }

    var order by remember { mutableStateOf(ordered) }
    LaunchedEffect(ordered) { order = ordered }
    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val fromIndex = order.indexOfFirst { it.id == from.key }
        val toIndex = order.indexOfFirst { it.id == to.key }
        if (fromIndex >= 0 && toIndex >= 0) {
            order = order.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Üzlet beállításai") },
                navigationIcon = {
                    IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Vissza") }
                },
                actions = {
                    IconButton(
                        enabled = name.isNotBlank(),
                        onClick = {
                            vm.save(Normalizer.clean(name), domain.trim().ifBlank { null }, color, Store.cleanDescription(description))
                            back()
                        },
                    ) { Icon(Icons.Default.Check, contentDescription = "Mentés") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            item(key = "details") {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        name, { name = it },
                        label = { Text("Név") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        domain, { domain = it },
                        label = { Text("Weboldal (a logóhoz)") },
                        placeholder = { Text("pl. auchan.hu") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    StoreDescriptionField(description, { description = it })
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StoreLogo(store, size = 56.dp)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            TextButton(
                                enabled = domain.isNotBlank(),
                                onClick = {
                                    scope.launch {
                                        if (!vm.fetchLogo(domain)) snackbar.showSnackbar("Ehhez a weboldalhoz nem található logó.")
                                    }
                                },
                            ) { Text("Logó letöltése a weboldalról") }
                            Row {
                                TextButton(onClick = {
                                    pickLogo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                }) { Text("Galériából") }
                                if (store.logo != null) TextButton(onClick = vm::removeLogo) { Text("Eltávolítás") }
                            }
                        }
                    }
                    Text("Szín", style = MaterialTheme.typography.labelLarge)
                    ColorPicker(color) { color = it }
                }
                HorizontalDivider()
            }
            item(key = "order-header") {
                Column(Modifier.padding(16.dp)) {
                    Text("Kategóriák sorrendje", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Ahogy a boltban haladsz. Húzással átrendezheted; vásárlás közben az app a pipálás " +
                            "sorrendjéből magától is tanul.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                        Text("Sorrend rögzítése (nem tanul tovább)", Modifier.weight(1f))
                        Switch(checked = store.orderLocked, onCheckedChange = vm::setLocked)
                    }
                    OutlinedButton(onClick = vm::resetOrder, modifier = Modifier.padding(top = 4.dp)) {
                        Text("Alapsorrend visszaállítása")
                    }
                }
            }
            items(order, key = { it.id }) { category ->
                ReorderableItem(reorderState, key = category.id) { dragging ->
                    ListItem(
                        headlineContent = { Text("${category.emoji}  ${category.name}") },
                        trailingContent = {
                            Icon(
                                Icons.Default.DragHandle,
                                contentDescription = "Áthelyezés",
                                modifier = Modifier.draggableHandle(
                                    onDragStopped = { vm.setOrder(order.map { it.id }, store.orderLocked) },
                                ),
                            )
                        },
                        tonalElevation = if (dragging) 4.dp else 0.dp,
                        colors = ListItemDefaults.colors(),
                    )
                }
            }
            item(key = "delete") {
                Spacer(Modifier.height(24.dp))
                TextButton(onClick = { confirmDelete = true }, modifier = Modifier.padding(horizontal = 8.dp)) {
                    Text("Üzlet törlése", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Törlöd: ${store.name}?",
            text = "Az üzlet és a teljes listája véglegesen törlődik mindkettőtöknél.",
            confirmLabel = "Törlés",
            onDismiss = { confirmDelete = false },
            onConfirm = {
                vm.delete()
                deleted()
            },
        )
    }
}
