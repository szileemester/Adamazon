package hu.szileemester.adamazon.ui.list

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hu.szileemester.adamazon.data.VoiceRecognizer
import hu.szileemester.adamazon.domain.Item
import hu.szileemester.adamazon.domain.PastedText
import hu.szileemester.adamazon.domain.suggestions
import hu.szileemester.adamazon.ui.appViewModel
import hu.szileemester.adamazon.ui.components.StoreDescriptionDialog
import hu.szileemester.adamazon.ui.components.StoreLogo
import hu.szileemester.adamazon.ui.components.CategoryDialog
import hu.szileemester.adamazon.ui.components.ItemIcon
import hu.szileemester.adamazon.domain.Categorizer
import hu.szileemester.adamazon.domain.Category
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import hu.szileemester.adamazon.ui.theme.storeContainerColor
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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
    var editingCategory by remember { mutableStateOf<Category?>(null) }
    var input by rememberSaveable { mutableStateOf("") }
    var checkedExpanded by rememberSaveable { mutableStateOf(false) }
    var showingDescription by rememberSaveable { mutableStateOf(false) }
    var pasted by remember { mutableStateOf<List<PastePreview>?>(null) }
    var pastedTitle by remember { mutableStateOf("") }
    val otherStores by vm.otherStores.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val voice = remember { VoiceRecognizer(context) }
    val listening by voice.state.collectAsStateWithLifecycle()
    val voiceAvailable = remember { voice.isAvailable }
    DisposableEffect(voice) { onDispose { voice.cancel() } }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { voice.stop() }

    fun showMessage(text: String) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(text, duration = SnackbarDuration.Short)
        }
    }

    // One dictated item is added at once; several go to the same confirmation as a pasted list.
    fun startVoice() = voice.start { result ->
        when (result) {
            is VoiceRecognizer.Result.Failed -> showMessage(result.message)
            is VoiceRecognizer.Result.Heard -> {
                val lines = vm.voiceLines(result.utterances)
                when (lines.size) {
                    0 -> showMessage("Nem hallottam semmit.")
                    1 -> vm.add(lines.single())
                    else -> {
                        pastedTitle = "Diktált lista"
                        pasted = vm.previewLines(lines)
                    }
                }
            }
        }
    }
    val microphonePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startVoice() else showMessage("A diktáláshoz engedélyezni kell a mikrofont.")
    }

    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                is ListEvent.AlreadyOnList -> scope.launch { snackbar.showSnackbar("„${event.name}” már a listán van.") }
                is ListEvent.Deleted -> scope.launch {
                    val result = snackbar.showSnackbar(
                        "„${event.item.name}” törölve",
                        actionLabel = "Visszavonás",
                        duration = SnackbarDuration.Short,
                    )
                    if (result == SnackbarResult.ActionPerformed) vm.undo(event.undo)
                }
                is ListEvent.Undone -> scope.launch {
                    snackbar.currentSnackbarData?.dismiss()
                    snackbar.showSnackbar("Visszavonva: ${event.label}", duration = SnackbarDuration.Short)
                }
                is ListEvent.Moved -> scope.launch {
                    snackbar.currentSnackbarData?.dismiss()
                    snackbar.showSnackbar("„${event.name}” áthelyezve ide: ${event.storeName}", duration = SnackbarDuration.Short)
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
                        // Tapping the logo (or name) shows the store's description.
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clip(CircleShape).clickable { showingDescription = true },
                        ) {
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
                    onValueChange = { text ->
                        // A pasted multi-line list goes to the confirmation dialog instead of the field.
                        if (PastedText.isMultiLine(text)) {
                            pastedTitle = "Beillesztett lista"
                            pasted = vm.previewLines(PastedText.lines(text))
                            input = ""
                        } else {
                            input = text
                        }
                    },
                    onSubmit = {
                        // "tej, kenyér, 1,5 l kóla" is three items, confirmed like a pasted list.
                        val items = PastedText.typedItems(input)
                        if (items.size > 1) {
                            pastedTitle = "Több tétel"
                            pasted = vm.previewLines(items)
                        } else {
                            vm.add(input)
                        }
                        input = ""
                    },
                    suggestions = remember(input, state.items, learned) { suggestions(input, state.items, learned.values) },
                    onSuggestion = {
                        vm.add(it)
                        input = ""
                    },
                    canUndo = canUndo,
                    onUndo = vm::undo,
                    listening = listening,
                    onMic = if (!voiceAvailable) null else ({
                        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                            PackageManager.PERMISSION_GRANTED
                        when {
                            listening != null -> voice.stop()
                            granted -> startVoice()
                            else -> microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }),
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
                    // A long press edits the category (name, icon, delete) right from the list.
                    Row(
                        Modifier
                            .animateItem()
                            .fillMaxWidth()
                            .combinedClickable(onClick = {}, onLongClick = { editingCategory = section.category })
                            .padding(start = 20.dp, top = 16.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ItemIcon(section.category.emoji, size = 20.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            section.category.name,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
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

    pasted?.let { previews ->
        PasteDialog(
            previews = previews,
            title = pastedTitle,
            onDismiss = { pasted = null },
            onAdd = { lines ->
                vm.addAll(lines)
                pasted = null
            },
        )
    }

    if (showingDescription && store != null) {
        StoreDescriptionDialog(
            store = store,
            onDismiss = { showingDescription = false },
            onSave = {
                vm.setDescription(it)
                showingDescription = false
            },
        )
    }

    editing?.let { item ->
        ItemEditSheet(
            item = item,
            categories = state.categories,
            onDismiss = { editing = null },
            onCreateCategory = vm::createCategory,
            onUpdateCategory = vm::updateCategory,
            onDeleteCategory = vm::deleteCategory,
            currentStore = store,
            otherStores = otherStores,
            onSave = { edited, targetStoreId ->
                vm.save(item, edited, targetStoreId)
                editing = null
            },
            onDelete = {
                vm.delete(item)
                editing = null
            },
        )
    }

    editingCategory?.let { category ->
        CategoryDialog(
            category = category,
            existing = state.categories,
            onDismiss = { editingCategory = null },
            onSave = {
                vm.updateCategory(it)
                editingCategory = null
            },
            onDelete = if (category.id != Categorizer.FALLBACK_CATEGORY_ID) {
                {
                    vm.deleteCategory(category)
                    editingCategory = null
                }
            } else null,
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
    listening: VoiceRecognizer.Listening?,
    onMic: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
        // Suggestions above the field, so they stay visible right over the keyboard.
        if (suggestions.isNotEmpty() && listening == null) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 4.dp)) {
                items(suggestions) { s -> SuggestionChip(onClick = { onSuggestion(s) }, label = { Text(s) }) }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (listening != null) {
                ListeningField(listening, Modifier.weight(1f))
            } else OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Új elem, pl. 2 kg alma", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                // Keyboard stays open so several items can be typed in a row.
                keyboardActions = KeyboardActions(onDone = { onSubmit() }),
            )
            if (onMic != null) {
                Spacer(Modifier.width(4.dp))
                MicButton(listening = listening != null, onClick = onMic)
            }
            Spacer(Modifier.width(4.dp))
            FilledTonalIconButton(onClick = onUndo, enabled = canUndo, modifier = Modifier.size(52.dp)) {
                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Utolsó művelet visszavonása")
            }
        }
    }
}

/** Takes the text field's place while dictating: what has been heard so far, newest at the end. */
@Composable
private fun ListeningField(listening: VoiceRecognizer.Listening, modifier: Modifier = Modifier) {
    val heard = (listening.heard + listening.partial).filter { it.isNotBlank() }.joinToString(" · ")
    Box(
        modifier
            .height(56.dp)
            .border(2.dp, MaterialTheme.colorScheme.error, MaterialTheme.shapes.extraSmall)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            if (heard.isEmpty()) "Hallgatlak…" else heard.takeLast(80),
            color = if (heard.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** The mic starts dictation; while listening it turns into a pulsing red stop button. */
@Composable
private fun MicButton(listening: Boolean, onClick: () -> Unit) {
    if (listening) {
        val pulse by rememberInfiniteTransition(label = "mic").animateFloat(
            initialValue = 1f,
            targetValue = 1.12f,
            animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
            label = "micPulse",
        )
        FilledIconButton(
            onClick = onClick,
            modifier = Modifier.size(52.dp).scale(pulse),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            ),
        ) { Icon(Icons.Default.Stop, contentDescription = "Diktálás befejezése") }
    } else {
        FilledTonalIconButton(onClick = onClick, modifier = Modifier.size(52.dp)) {
            Icon(Icons.Default.Mic, contentDescription = "Diktálás")
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
        ItemIcon(item.emoji, size = 26.dp)
        Spacer(Modifier.width(10.dp))
        Text(
            item.name,
            style = MaterialTheme.typography.bodyLarge,
            textDecoration = if (item.checked) TextDecoration.LineThrough else null,
            color = if (item.checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        // The note itself is read in the editor; here a mark only says there is one.
        if (item.note != null) Text("📝", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(end = 6.dp))
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
