package hu.szileemester.adamazon.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import hu.szileemester.adamazon.AppContainer
import hu.szileemester.adamazon.domain.Categorizer
import hu.szileemester.adamazon.domain.Category
import hu.szileemester.adamazon.ui.appViewModel
import hu.szileemester.adamazon.ui.components.CategoryDialog
import hu.szileemester.adamazon.ui.components.ConfirmDialog
import hu.szileemester.adamazon.ui.components.ItemIcon
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

class CategoriesViewModel(c: AppContainer) : ViewModel() {
    private val repository = c.session.categories
    val categories = repository.categories.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun save(category: Category) {
        if (category.id.isEmpty()) repository.add(category.name, category.emoji, categories.value) else repository.update(category)
    }

    fun delete(category: Category) = repository.delete(category.id)
    fun reorder(ids: List<String>) = repository.reorder(ids)
}

/** The group's categories: drag to reorder, tap to edit, + to add. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(back: () -> Unit) {
    val vm = appViewModel { CategoriesViewModel(it) }
    val categories by vm.categories.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Category?>(null) }
    var deleting by remember { mutableStateOf<Category?>(null) }

    var order by remember { mutableStateOf(categories) }
    LaunchedEffect(categories) { order = categories }
    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val fromIndex = order.indexOfFirst { it.id == from.key }
        val toIndex = order.indexOfFirst { it.id == to.key }
        if (fromIndex >= 0 && toIndex >= 0) order = order.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kategóriák") },
                navigationIcon = {
                    IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Vissza") }
                },
            )
        },
        // Always at hand, however long the list grows.
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editing = Category("", "", "🛒", 0) },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Új kategória") },
            )
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 96.dp),
        ) {
            item(key = "intro") {
                Text(
                    "Ez az alapsorrend az új üzleteknél. Üzletenként külön is beállítható, és magától tanul. " +
                        "A ✏️ a nevet és az ikont szerkeszti, a ≡ jelet húzva átrendezheted.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
            items(order, key = { it.id }) { category ->
                ReorderableItem(reorderState, key = category.id) { dragging ->
                    ListItem(
                        headlineContent = { Text(category.name) },
                        leadingContent = { ItemIcon(category.emoji, size = 28.dp) },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { editing = category }) { Icon(Icons.Default.Edit, contentDescription = "Szerkesztés") }
                                // "Egyéb" collects the items of deleted categories, so it stays.
                                if (category.id != Categorizer.FALLBACK_CATEGORY_ID) {
                                    IconButton(onClick = { deleting = category }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Törlés", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                                Icon(
                                    Icons.Default.DragHandle,
                                    contentDescription = "Áthelyezés",
                                    modifier = Modifier.padding(start = 4.dp).draggableHandle(onDragStopped = { vm.reorder(order.map { it.id }) }),
                                )
                            }
                        },
                        tonalElevation = if (dragging) 4.dp else 0.dp,
                        modifier = Modifier.clickable { editing = category },
                    )
                }
            }
        }
    }

    deleting?.let { category ->
        ConfirmDialog(
            title = "„${category.name}” törlése?",
            text = "Az ide tartozó tételek az „Egyéb” kategóriába kerülnek.",
            confirmLabel = "Törlés",
            onDismiss = { deleting = null },
            onConfirm = { vm.delete(category) },
        )
    }

    editing?.let { category ->
        CategoryDialog(
            category = category,
            existing = categories,
            onDismiss = { editing = null },
            onSave = {
                vm.save(it)
                editing = null
            },
            onDelete = if (category.id.isNotEmpty() && category.id != Categorizer.FALLBACK_CATEGORY_ID) {
                {
                    vm.delete(category)
                    editing = null
                }
            } else null,
        )
    }
}
