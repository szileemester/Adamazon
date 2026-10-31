package hu.szileemester.adamazon.ui.list

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hu.szileemester.adamazon.domain.Item
import hu.szileemester.adamazon.domain.suggestions
import hu.szileemester.adamazon.ui.appViewModel
import hu.szileemester.adamazon.ui.components.StoreLogo
import hu.szileemester.adamazon.ui.theme.storeContainerColor
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoreListScreen(
    storeId: String,
    back: () -> Unit,
    openSettings: () -> Unit,
    openCard: (String) -> Unit,
) {
    val vm = appViewModel(key = storeId) { StoreListViewModel(it, storeId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val learned by vm.learned.collectAsStateWithLifecycle()
    val canUndo by vm.canUndo.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<Item?>(null) }
    var input by rememberSaveable { mutableStateOf("") }
    var checkedExpanded by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                is ListEvent.AlreadyOnList -> snackbar.showSnackbar("„${event.name}” már a listán van.")
                is ListEvent.Deleted -> scope.launch {
                    val result = snackbar.showSnackbar(
                        "„${event.item.name}” törölve",
                        actionLabel = "Visszavonás",
                        duration = SnackbarDuration.Short,
                    )
                    if (result == SnackbarResult.ActionPerformed) vm.undo()
                }
                is ListEvent.Undone -> scope.launch {
                    snackbar.currentSnackbarData?.dismiss()
                    snackbar.showSnackbar("Visszavonva: ${event.label}", duration = SnackbarDuration.Short)
                }
            }
        }
    }
    // The store was deleted (possibly by the other user) while we were looking at it.
    LaunchedEffect(state.loaded, state.store) { if (state.loaded && state.store == null) back() }

    val store = state.store
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (store != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StoreLogo(store, size = 32.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(store.name)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Vissza") }
                },
                actions = {
                    state.card?.let { card ->
                        IconButton(onClick = { openCard(card.id) }) {
                            Icon(Icons.Default.CreditCard, contentDescription = "Kártya: ${card.name}")
                        }
                    }
                    IconButton(onClick = openSettings) { Icon(Icons.Default.Settings, contentDescription = "Üzlet beállításai") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = storeContainerColor(store?.colorArgb ?: 0L),
                ),
            )
        },
        // Quick-add sits at the bottom, within thumb reach, and rides above the keyboard.
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                QuickAdd(
                    value = input,
                    onValueChange = { input = it },
                    onSubmit = {
                        vm.add(input)
                        input = ""
                    },
                    suggestions = remember(input, state.items, learned) { suggestions(input, state.items, learned.values) },
                    onSuggestion = {
                        vm.add(it)
                        input = ""
                    },
                    canUndo = canUndo,
                    onUndo = vm::undo,
                    modifier = Modifier.navigationBarsPadding().imePadding(),
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            if (state.loaded && state.sections.isEmpty()) {
                item(key = "empty") {
                    Text(
                        "A lista üres.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                    )
                }
            }
            state.sections.forEach { section ->
                item(key = "header-${section.category.id}") {
                    Text(
                        "${section.category.emoji}  ${section.category.name}",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.animateItem().padding(start = 20.dp, top = 16.dp, bottom = 4.dp),
                    )
                }
                items(section.items, key = { it.id }) { item ->
                    SwipeToDelete(onDelete = { vm.delete(item) }, modifier = Modifier.animateItem()) {
                        ItemRow(item, onToggle = { vm.toggle(item) }, onClick = { editing = item })
                    }
                }
            }
            if (state.checked.isNotEmpty()) {
                item(key = "checked-header") {
                    HorizontalDivider(Modifier.padding(top = 16.dp))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { checkedExpanded = !checkedExpanded }
                            .padding(start = 12.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(if (checkedExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Kipipált elemek (${state.checked.size})", Modifier.weight(1f))
                        TextButton(onClick = vm::clearChecked) { Text("Törlés") }
                    }
                }
                if (checkedExpanded) {
                    items(state.checked, key = { it.id }) { item ->
                        SwipeToDelete(onDelete = { vm.delete(item) }, modifier = Modifier.animateItem()) {
                            ItemRow(item, onToggle = { vm.toggle(item) }, onClick = { editing = item })
                        }
                    }
                }
            }
        }
    }

    editing?.let { item ->
        ItemEditSheet(
            item = item,
            categories = state.categories,
            onDismiss = { editing = null },
            onCreateCategory = vm::createCategory,
            onSave = { edited ->
                vm.save(item, edited)
                editing = null
            },
            onDelete = {
                vm.delete(item)
                editing = null
            },
        )
    }
}

@Composable
private fun QuickAdd(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    suggestions: List<String>,
    onSuggestion: (String) -> Unit,
    canUndo: Boolean,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
        // Suggestions above the field, so they stay visible right over the keyboard.
        if (suggestions.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 4.dp)) {
                items(suggestions) { s -> SuggestionChip(onClick = { onSuggestion(s) }, label = { Text(s) }) }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Új elem, pl. 2 kg paradicsom") },
                leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                // Keyboard stays open so several items can be typed in a row.
                keyboardActions = KeyboardActions(onDone = { onSubmit() }),
            )
            Spacer(Modifier.width(4.dp))
            FilledTonalIconButton(onClick = onUndo, enabled = canUndo, modifier = Modifier.size(52.dp)) {
                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Utolsó művelet visszavonása")
            }
        }
    }
}

@Composable
private fun ItemRow(item: Item, onToggle: () -> Unit, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(start = 4.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = item.checked, onCheckedChange = { onToggle() })
        Text(item.emoji, fontSize = 22.sp, modifier = Modifier.width(36.dp))
        Text(
            item.name,
            style = MaterialTheme.typography.bodyLarge,
            textDecoration = if (item.checked) TextDecoration.LineThrough else null,
            color = if (item.checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        item.quantity?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SwipeToDelete(onDelete: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val state = rememberSwipeToDismissBoxState()
    LaunchedEffect(state.currentValue) {
        if (state.currentValue == SwipeToDismissBoxValue.EndToStart) {
            onDelete()
            state.snapTo(SwipeToDismissBoxValue.Settled)
        }
    }
    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.errorContainer).padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(Icons.Default.Delete, contentDescription = "Törlés", tint = MaterialTheme.colorScheme.onErrorContainer)
            }
        },
    ) { content() }
}
