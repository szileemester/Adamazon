package hu.szileemester.adamazon.ui.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Confirms a multi-line paste: one row per line with the guessed icon and quantity. Lines already
 * on the list (or pasted twice) are shown but not selectable; the rest can be unticked.
 */
@Composable
fun PasteDialog(previews: List<PastePreview>, onDismiss: () -> Unit, onAdd: (List<String>) -> Unit) {
    val selected = remember(previews) { mutableStateListOf(*previews.map { it.addable }.toTypedArray()) }
    val count = selected.count { it }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Beillesztett lista") },
        text = {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                itemsIndexed(previews) { index, preview ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = preview.addable) { selected[index] = !selected[index] },
                    ) {
                        Checkbox(
                            checked = selected[index],
                            enabled = preview.addable,
                            onCheckedChange = { selected[index] = it },
                        )
                        Text(preview.emoji, fontSize = 20.sp, modifier = Modifier.width(32.dp))
                        Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
                            Text(preview.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            statusText(preview)?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        preview.quantity?.let {
                            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = count > 0,
                onClick = { onAdd(previews.filterIndexed { i, _ -> selected[i] }.map { it.input }) },
            ) { Text("Mind felvétele ($count)") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Mégse") } },
    )
}

private fun statusText(preview: PastePreview): String? = when (preview.status) {
    PastePreview.Status.NEW -> null
    PastePreview.Status.RESTORE -> "korábban kipipálva – visszakerül a listára"
    PastePreview.Status.ON_LIST -> "már a listán van"
    PastePreview.Status.DUPLICATE -> "kétszer szerepel a beillesztésben"
}
