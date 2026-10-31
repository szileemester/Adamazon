package hu.szileemester.adamazon.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import hu.szileemester.adamazon.AppContainer
import hu.szileemester.adamazon.BuildConfigInfo
import hu.szileemester.adamazon.data.AccessState
import hu.szileemester.adamazon.data.ThemeMode
import hu.szileemester.adamazon.domain.Categorizer
import hu.szileemester.adamazon.domain.Category
import hu.szileemester.adamazon.ui.appViewModel
import hu.szileemester.adamazon.ui.components.CategoryDialog
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

class SettingsViewModel(val c: AppContainer) : ViewModel() {
    val categories = c.categories.categories.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val email: String? get() = (c.auth.state.value as? AccessState.Allowed)?.user?.email

    fun save(category: Category) {
        if (category.id.isEmpty()) {
            c.categories.add(category.name, category.emoji, categories.value)
        } else {
            c.categories.update(category)
        }
    }

    fun delete(category: Category) = c.categories.delete(category.id)
    fun reorder(ids: List<String>) = c.categories.reorder(ids)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(bottomBar: @Composable () -> Unit) {
    val vm = appViewModel { SettingsViewModel(it) }
    val categories by vm.categories.collectAsStateWithLifecycle()
    val themeMode by vm.c.theme.mode.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<Category?>(null) }

    var order by remember { mutableStateOf(categories) }
    LaunchedEffect(categories) { order = categories }
    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val fromIndex = order.indexOfFirst { it.id == from.key }
        val toIndex = order.indexOfFirst { it.id == to.key }
        if (fromIndex >= 0 && toIndex >= 0) order = order.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Beállítások") }) }, bottomBar = bottomBar) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item(key = "account") {
                ListItem(
                    headlineContent = { Text("Bejelentkezve") },
                    supportingContent = { Text(vm.email.orEmpty()) },
                    trailingContent = {
                        TextButton(onClick = { scope.launch { vm.c.auth.signOut(context) } }) {
                            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Kijelentkezés")
                        }
                    },
                )
                HorizontalDivider()
                Column(Modifier.padding(16.dp)) {
                    Text("Megjelenés", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        ThemeMode.entries.forEachIndexed { index, mode ->
                            SegmentedButton(
                                selected = themeMode == mode,
                                onClick = { vm.c.theme.set(mode) },
                                shape = SegmentedButtonDefaults.itemShape(index, ThemeMode.entries.size),
                            ) { Text(mode.label) }
                        }
                    }
                }
                HorizontalDivider()
                Column(Modifier.padding(16.dp)) {
                    Text("Kategóriák", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Ez az alapsorrend az új üzleteknél. Üzletenként külön is beállítható, és magától tanul.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(order, key = { it.id }) { category ->
                ReorderableItem(reorderState, key = category.id) { dragging ->
                    ListItem(
                        headlineContent = { Text(category.name) },
                        leadingContent = { Text(category.emoji, fontSize = 22.sp) },
                        trailingContent = {
                            Icon(
                                Icons.Default.DragHandle,
                                contentDescription = "Áthelyezés",
                                modifier = Modifier.draggableHandle(onDragStopped = { vm.reorder(order.map { it.id }) }),
                            )
                        },
                        tonalElevation = if (dragging) 4.dp else 0.dp,
                        modifier = Modifier.clickable { editing = category },
                    )
                }
            }
            item(key = "add") {
                OutlinedButton(onClick = { editing = Category("", "", "🛒", 0) }, modifier = Modifier.padding(16.dp)) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Új kategória")
                }
                HorizontalDivider()
                Text(
                    "Adamazon ${BuildConfigInfo.versionName(context)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
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
