package hu.szileemester.adamazon.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import hu.szileemester.adamazon.domain.Categorizer
import hu.szileemester.adamazon.domain.Category
import hu.szileemester.adamazon.domain.Normalizer

/**
 * Full-screen category chooser with search. Every row can be edited (name, icon) or deleted with
 * ✏️; a search without a match offers to create a category of that name.
 */
@Composable
fun CategoryPicker(
    categories: List<Category>,
    selectedId: String,
    onDismiss: () -> Unit,
    onSelect: (Category) -> Unit,
    onCreate: (name: String, emoji: String) -> String,
    onUpdate: (Category) -> Unit,
    onDelete: (Category) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<Category?>(null) }
    val key = Normalizer.key(query)
    val shown = if (key.isEmpty()) categories else categories.filter { Normalizer.key(it.name).contains(key) }
    val exact = categories.any { Normalizer.key(it.name) == key }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            modifier = Modifier.safeDrawingPadding().imePadding(),
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = { editing = Category("", Normalizer.clean(query), "🛒", 0) },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Új kategória") },
                )
            },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
                    IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Vissza") }
                    Text("Kategória", style = MaterialTheme.typography.titleLarge)
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Keresés") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = if (query.isNotEmpty()) {
                        { IconButton(onClick = { query = "" }) { Icon(Icons.Default.Clear, contentDescription = "Törlés") } }
                    } else null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp)) {
                    items(shown, key = { it.id }) { category ->
                        val selected = category.id == selectedId
                        ListItem(
                            leadingContent = { ItemIcon(category.emoji, size = 28.dp) },
                            headlineContent = { Text(category.name) },
                            trailingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (selected) Icon(Icons.Default.Check, contentDescription = "Kiválasztva", tint = MaterialTheme.colorScheme.primary)
                                    IconButton(onClick = { editing = category }) { Icon(Icons.Default.Edit, contentDescription = "Szerkesztés") }
                                }
                            },
                            colors = if (selected) {
                                ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                            } else ListItemDefaults.colors(),
                            modifier = Modifier.clickable { onSelect(category) },
                        )
                    }
                    if (key.isNotEmpty() && !exact) {
                        item(key = "create") {
                            ListItem(
                                leadingContent = { Icon(Icons.Default.Add, contentDescription = null) },
                                headlineContent = { Text("„${Normalizer.clean(query)}” kategória létrehozása") },
                                modifier = Modifier.clickable { editing = Category("", Normalizer.clean(query), "🛒", 0) },
                            )
                        }
                    }
                }
            }
        }
    }

    editing?.let { category ->
        CategoryDialog(
            category = category,
            existing = categories,
            onDismiss = { editing = null },
            onSave = { saved ->
                editing = null
                if (saved.id.isEmpty()) {
                    // A category created here is what the user was looking for: choose it at once.
                    val id = onCreate(saved.name, saved.emoji)
                    onSelect(saved.copy(id = id))
                } else {
                    onUpdate(saved)
                }
            },
            onDelete = if (category.id.isNotEmpty() && category.id != Categorizer.FALLBACK_CATEGORY_ID) {
                {
                    editing = null
                    onDelete(category)
                }
            } else null,
        )
    }
}
