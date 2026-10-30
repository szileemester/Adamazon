package hu.szileemester.adamazon.ui.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import hu.szileemester.adamazon.domain.Category
import hu.szileemester.adamazon.domain.Item
import hu.szileemester.adamazon.domain.Normalizer
import hu.szileemester.adamazon.ui.components.EmojiPickerDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemEditSheet(
    item: Item,
    categories: List<Category>,
    onDismiss: () -> Unit,
    onSave: (Item) -> Unit,
    onDelete: () -> Unit,
) {
    var name by remember { mutableStateOf(item.name) }
    var quantity by remember { mutableStateOf(item.quantity.orEmpty()) }
    var categoryId by remember { mutableStateOf(item.categoryId) }
    var emoji by remember { mutableStateOf(item.emoji) }
    var pickingEmoji by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { pickingEmoji = true }, modifier = Modifier.size(64.dp)) {
                    Text(emoji.ifBlank { "?" }, fontSize = 26.sp)
                }
                Spacer(Modifier.width(12.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Név") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = quantity,
                onValueChange = { quantity = it },
                label = { Text("Mennyiség (pl. 2 kg)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            Text("Kategória", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(4.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                categories.forEach { category ->
                    FilterChip(
                        selected = category.id == categoryId,
                        onClick = {
                            // Keep a custom icon, but follow the category if the icon was the category's own.
                            val old = categories.firstOrNull { it.id == categoryId }
                            if (old == null || emoji == old.emoji || emoji.isBlank()) emoji = category.emoji
                            categoryId = category.id
                        },
                        label = { Text("${category.emoji} ${category.name}") },
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDelete) { Text("Törlés", color = MaterialTheme.colorScheme.error) }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("Mégse") }
                Button(
                    enabled = name.isNotBlank(),
                    onClick = {
                        onSave(
                            item.copy(
                                name = Normalizer.clean(name),
                                quantity = quantity.trim().ifBlank { null },
                                categoryId = categoryId,
                                emoji = emoji,
                            ),
                        )
                    },
                ) { Text("Mentés") }
            }
        }
    }

    if (pickingEmoji) {
        EmojiPickerDialog(
            current = emoji,
            onDismiss = { pickingEmoji = false },
            onPick = {
                emoji = it
                pickingEmoji = false
            },
        )
    }
}
