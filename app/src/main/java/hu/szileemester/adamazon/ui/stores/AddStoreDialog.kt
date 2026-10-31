package hu.szileemester.adamazon.ui.stores

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import hu.szileemester.adamazon.data.StorePreset
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import hu.szileemester.adamazon.domain.Normalizer
import hu.szileemester.adamazon.domain.Store
import hu.szileemester.adamazon.ui.components.ColorPicker
import hu.szileemester.adamazon.ui.components.StoreDescriptionField

/**
 * Typing filters the predefined stores; picking one fills name, website and colour.
 * Anything else typed becomes a custom store ("Húsbolt").
 */
@Composable
fun AddStoreDialog(
    presets: List<StorePreset>,
    onDismiss: () -> Unit,
    onAdd: (name: String, domain: String?, colorArgb: Long, description: String?) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var domain by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var color by remember { mutableLongStateOf(0L) }
    val matches = remember(name) {
        val key = Normalizer.key(name)
        presets.filter { key.isEmpty() || Normalizer.key(it.name).contains(key) }
            .filter { Normalizer.key(it.name) != key }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Új üzlet") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Név") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (matches.isNotEmpty()) {
                    LazyColumn(Modifier.heightIn(max = 220.dp)) {
                        items(matches, key = { it.name }) { preset ->
                            ListItem(
                                headlineContent = { Text(preset.name) },
                                supportingContent = { Text(preset.domain) },
                                modifier = Modifier.clickable {
                                    name = preset.name
                                    domain = preset.domain
                                    color = preset.colorArgb
                                },
                            )
                            HorizontalDivider()
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = domain,
                    onValueChange = { domain = it.trim() },
                    label = { Text("Weboldal a logóhoz (nem kötelező)") },
                    placeholder = { Text("pl. auchan.hu") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                StoreDescriptionField(description, { description = it })
                Spacer(Modifier.height(4.dp))
                Text("Szín", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(4.dp))
                ColorPicker(color) { color = it }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onAdd(Normalizer.clean(name), domain.ifBlank { null }, color, Store.cleanDescription(description)) },
            ) { Text("Hozzáadás") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Mégse") } },
    )
}
