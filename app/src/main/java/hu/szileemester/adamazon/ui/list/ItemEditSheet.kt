package hu.szileemester.adamazon.ui.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import hu.szileemester.adamazon.domain.Category
import hu.szileemester.adamazon.domain.Item
import hu.szileemester.adamazon.domain.Normalizer
import hu.szileemester.adamazon.domain.Quantities
import hu.szileemester.adamazon.domain.Quantity
import hu.szileemester.adamazon.domain.Store
import hu.szileemester.adamazon.ui.components.CategoryDialog
import hu.szileemester.adamazon.ui.components.EmojiPickerDialog
import hu.szileemester.adamazon.ui.components.StoreLogo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemEditSheet(
    item: Item,
    categories: List<Category>,
    currentStore: Store?,
    otherStores: List<Store>,
    onDismiss: () -> Unit,
    onCreateCategory: (name: String, emoji: String) -> String,
    onSave: (item: Item, targetStoreId: String) -> Unit,
    onDelete: () -> Unit,
) {
    val parsedQuantity = remember(item) { Quantities.parse(item.quantity) }
    var name by remember { mutableStateOf(item.name) }
    // A quantity that is not "number + unit" ("egy kevés") is kept as free text in the amount field.
    var amount by remember { mutableStateOf(parsedQuantity?.let { Quantities.formatAmount(it.amount) } ?: item.quantity.orEmpty()) }
    var unit by remember { mutableStateOf(parsedQuantity?.unit ?: if (item.quantity == null) "db" else "") }
    var categoryId by remember { mutableStateOf(item.categoryId) }
    var emoji by remember { mutableStateOf(item.emoji) }
    var targetStoreId by remember { mutableStateOf(currentStore?.id.orEmpty()) }
    var pickingEmoji by remember { mutableStateOf(false) }
    var creatingCategory by remember { mutableStateOf(false) }

    fun selectCategory(id: String, categoryEmoji: String) {
        // Keep a custom icon, but follow the category if the icon was the category's own.
        val old = categories.firstOrNull { it.id == categoryId }
        if (old == null || emoji == old.emoji || emoji.isBlank()) emoji = categoryEmoji
        categoryId = id
    }

    fun quantityText(): String? {
        if (amount.isBlank()) return null
        val number = Quantities.parseAmount(amount) ?: return "${amount.trim()} $unit".trim()
        return Quantities.format(Quantity(number, unit))
    }

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
            Spacer(Modifier.height(12.dp))
            Text("Mennyiség", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(4.dp))
            QuantityEditor(
                amount = amount,
                unit = unit,
                onAmountChange = { amount = it },
                onUnitChange = { unit = it },
            )
            if (currentStore != null && otherStores.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text("Bolt", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(4.dp))
                StorePicker(
                    stores = listOf(currentStore) + otherStores,
                    selectedId = targetStoreId,
                    onSelect = { targetStoreId = it },
                )
            }
            Spacer(Modifier.height(16.dp))
            Text("Kategória", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(4.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                categories.forEach { category ->
                    FilterChip(
                        selected = category.id == categoryId,
                        onClick = { selectCategory(category.id, category.emoji) },
                        label = { Text("${category.emoji} ${category.name}") },
                    )
                }
                AssistChip(
                    onClick = { creatingCategory = true },
                    label = { Text("Új kategória") },
                    leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, Modifier.size(18.dp)) },
                )
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
                                quantity = quantityText(),
                                categoryId = categoryId,
                                emoji = emoji,
                            ),
                            targetStoreId.ifEmpty { currentStore?.id.orEmpty() },
                        )
                    },
                ) { Text(if (targetStoreId != currentStore?.id) "Áthelyezés" else "Mentés") }
            }
        }
    }

    if (creatingCategory) {
        CategoryDialog(
            category = Category("", "", "🛒", 0),
            existing = categories,
            onDismiss = { creatingCategory = false },
            onSave = { new ->
                selectCategory(onCreateCategory(new.name, new.emoji), new.emoji)
                creatingCategory = false
            },
        )
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

/** − [amount] + [unit ▾]; the steps follow the unit (1 db, 0,5 kg, 10 dkg, 100 g…). */
@Composable
private fun QuantityEditor(
    amount: String,
    unit: String,
    onAmountChange: (String) -> Unit,
    onUnitChange: (String) -> Unit,
) {
    val number = Quantities.parseAmount(amount)
    val canStep = amount.isBlank() || number != null
    fun step(direction: Int) {
        val next = Quantities.stepped(number, unit, direction)
        onAmountChange(next?.let(Quantities::formatAmount).orEmpty())
    }
    var unitMenu by remember { mutableStateOf(false) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        FilledTonalIconButton(onClick = { step(-1) }, enabled = canStep && number != null) {
            Icon(Icons.Default.Remove, contentDescription = "Kevesebb")
        }
        OutlinedTextField(
            value = amount,
            onValueChange = { onAmountChange(it.take(20)) },
            placeholder = { Text("–", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(textAlign = TextAlign.Center),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.width(96.dp).padding(horizontal = 4.dp),
        )
        FilledTonalIconButton(onClick = { step(+1) }, enabled = canStep) {
            Icon(Icons.Default.Add, contentDescription = "Több")
        }
        Spacer(Modifier.width(12.dp))
        Box {
            OutlinedButton(onClick = { unitMenu = true }) {
                Text(unit.ifEmpty { "nincs" })
                Icon(Icons.Default.ArrowDropDown, contentDescription = "Mértékegység")
            }
            DropdownMenu(expanded = unitMenu, onDismissRequest = { unitMenu = false }) {
                val units = (Quantities.UNITS + unit).filter { it.isNotEmpty() }.distinct()
                DropdownMenuItem(text = { Text("nincs mértékegység") }, onClick = { onUnitChange(""); unitMenu = false })
                units.forEach { u ->
                    DropdownMenuItem(text = { Text(u) }, onClick = { onUnitChange(u); unitMenu = false })
                }
            }
        }
    }
}

@Composable
private fun StorePicker(stores: List<Store>, selectedId: String, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val selected = stores.firstOrNull { it.id == selectedId } ?: stores.first()
    Box {
        OutlinedButton(onClick = { open = true }) {
            StoreLogo(selected, size = 24.dp)
            Spacer(Modifier.width(8.dp))
            Text(selected.name)
            Icon(Icons.Default.ArrowDropDown, contentDescription = "Bolt választása")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            stores.forEach { store ->
                DropdownMenuItem(
                    leadingIcon = { StoreLogo(store, size = 24.dp) },
                    text = { Text(store.name) },
                    onClick = { onSelect(store.id); open = false },
                )
            }
        }
    }
}
