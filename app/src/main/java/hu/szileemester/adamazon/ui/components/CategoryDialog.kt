package hu.szileemester.adamazon.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import hu.szileemester.adamazon.domain.Category
import hu.szileemester.adamazon.domain.Normalizer

/**
 * Creates or edits a category (name + emoji). Shared by Settings and the item editor, so a new
 * category can be made right where it is needed. An empty [category] id means "new".
 */
@Composable
fun CategoryDialog(
    category: Category,
    existing: List<Category>,
    onDismiss: () -> Unit,
    onSave: (Category) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var name by remember { mutableStateOf(category.name) }
    var emoji by remember { mutableStateOf(category.emoji) }
    var picking by remember { mutableStateOf(false) }
    val duplicate = existing.any { it.id != category.id && Normalizer.key(it.name) == Normalizer.key(name) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (category.id.isEmpty()) "Új kategória" else "Kategória") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { picking = true }, modifier = Modifier.size(64.dp)) { Text(emoji, fontSize = 24.sp) }
                    Spacer(Modifier.width(12.dp))
                    OutlinedTextField(
                        name, { name = it },
                        label = { Text("Név") },
                        singleLine = true,
                        isError = duplicate,
                        supportingText = if (duplicate) {
                            { Text("Már van ilyen nevű kategória.") }
                        } else null,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (onDelete != null) {
                    Text(
                        "Törléskor az ide tartozó elemek az „Egyéb” csoportba kerülnek.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && !duplicate,
                onClick = { onSave(category.copy(name = Normalizer.clean(name), emoji = emoji)) },
            ) { Text("Mentés") }
        },
        dismissButton = {
            Row {
                onDelete?.let { TextButton(onClick = it) { Text("Törlés", color = MaterialTheme.colorScheme.error) } }
                TextButton(onClick = onDismiss) { Text("Mégse") }
            }
        },
    )

    if (picking) {
        EmojiPickerDialog(emoji, onDismiss = { picking = false }, onPick = { emoji = it; picking = false })
    }
}
