package hu.szileemester.adamazon.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import hu.szileemester.adamazon.domain.Links
import hu.szileemester.adamazon.domain.Store

/** Text field for a store's short description, capped at [Store.DESCRIPTION_MAX] with a counter. */
@Composable
fun StoreDescriptionField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.take(Store.DESCRIPTION_MAX)) },
        label = { Text("Leírás (nem kötelező)") },
        placeholder = { Text("pl. nyitva 7–21, csomagautomata a bejáratnál") },
        supportingText = { Text("${value.length} / ${Store.DESCRIPTION_MAX}") },
        minLines = 2,
        maxLines = 7,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = modifier.fillMaxWidth(),
    )
}

/** The text with its web links underlined and tappable (they open in the browser). */
@Composable
private fun linkified(text: String): AnnotatedString {
    val linkStyle = TextLinkStyles(
        SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline),
    )
    return remember(text, linkStyle) {
        buildAnnotatedString {
            append(text)
            Links.find(text).forEach { link ->
                addLink(LinkAnnotation.Url(link.url, linkStyle), link.range.first, link.range.last + 1)
            }
        }
    }
}

/** Shows a store's description; "Szerkesztés" turns it into an editable field in place. */
@Composable
fun StoreDescriptionDialog(store: Store, onDismiss: () -> Unit, onSave: (String?) -> Unit) {
    var editing by remember { mutableStateOf(store.description == null) }
    var text by remember { mutableStateOf(store.description.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { StoreLogo(store, size = 48.dp) },
        title = { Text(store.name) },
        text = {
            Column {
                if (editing) {
                    StoreDescriptionField(text, { text = it })
                } else {
                    Text(linkified(store.description.orEmpty()), style = MaterialTheme.typography.bodyLarge)
                }
            }
        },
        confirmButton = {
            if (editing) {
                TextButton(onClick = { onSave(Store.cleanDescription(text)) }) { Text("Mentés") }
            } else {
                TextButton(onClick = onDismiss) { Text("Bezárás") }
            }
        },
        dismissButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (editing) {
                    TextButton(onClick = onDismiss) { Text("Mégse") }
                } else {
                    TextButton(onClick = { editing = true }) { Text("Szerkesztés") }
                    Spacer(Modifier.width(4.dp))
                }
            }
        },
    )
}
