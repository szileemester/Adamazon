package hu.szileemester.adamazon.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FormatColorReset
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import hu.szileemester.adamazon.domain.Store
import hu.szileemester.adamazon.ui.theme.StoreColors
import kotlin.math.absoluteValue

/** Store logo in a circle, or the store's initial on a colour derived from its name. */
@Composable
fun StoreLogo(store: Store, size: Dp = 40.dp) {
    val modifier = Modifier.size(size).clip(CircleShape)
    if (store.logo != null) {
        AsyncImage(
            model = store.logo,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = modifier.background(Color.White).padding(size / 8),
        )
    } else {
        val palette = listOf(0xFF1E88E5, 0xFF43A047, 0xFFE53935, 0xFF8E24AA, 0xFFFB8C00, 0xFF00897B, 0xFF6D4C41)
        val color = Color(palette[store.name.hashCode().absoluteValue % palette.size])
        Box(modifier.background(color), contentAlignment = Alignment.Center) {
            Text(
                store.name.take(1).uppercase(),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.45f).sp,
            )
        }
    }
}

@Composable
fun ColorPicker(selected: Long, onSelect: (Long) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        StoreColors.forEach { argb ->
            val isSelected = argb == selected
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (argb == 0L) MaterialTheme.colorScheme.surface else Color(argb))
                    .border(
                        width = if (isSelected) 3.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        shape = CircleShape,
                    )
                    .clickable { onSelect(argb) },
                contentAlignment = Alignment.Center,
            ) {
                when {
                    isSelected -> Icon(Icons.Default.Check, contentDescription = null, tint = Color.Black.copy(alpha = 0.7f))
                    argb == 0L -> Icon(Icons.Default.FormatColorReset, contentDescription = "Nincs szín")
                }
            }
        }
    }
}

private val EMOJIS = (
    "🍅🥒🥕🥔🧅🧄🥬🥦🌽🌶️🫑🍆🥑🍄🫘🌱🌿🎃🍠" +
        "🍎🍏🍐🍌🍊🍋🍇🍓🫐🍒🍑🥝🍍🥭🍉🍈🥥" +
        "🍞🥖🥐🥨🥯🥙🌮🍕🍔🌭" +
        "🥩🍗🍖🥓🐟🦐🦑🥚🥛🧀🧈🍮🍦🧊" +
        "🍚🍝🍜🥣🥫🫒🌻🍯🥜🌰🧂🌾☕🍵🧁🍬🍫🍪🍰🎂🍩🍿🍟🍭" +
        "💧🥤🧃🧋🍺🍷🥂🍾🥃🍸" +
        "🧽🧴🧺🧹🧼🚽🧻🤧🦷🪒💄💅💊🩹👶🛁🌸" +
        "🏠📦🗑️🛍️🔋💡🕯️🔥🐈🐕🐾🧤🍽️🍴" +
        "🛒📰✉️🎁💐🚬🎟️👕🧦👟🧸🔌📓🖊️✏️📄🔧🔩💳"
    ).let { text ->
    // Split into grapheme clusters so multi-code-point emoji (with variation selectors) stay whole.
    val iterator = java.text.BreakIterator.getCharacterInstance()
    iterator.setText(text)
    generateSequence(iterator.first()) { iterator.next().takeIf { it != java.text.BreakIterator.DONE } }
        .zipWithNext { start, end -> text.substring(start, end) }
        .toList()
}

/** Pick from common shopping emoji, or type/paste any emoji. */
@Composable
fun EmojiPickerDialog(current: String, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    var custom by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ikon választása") },
        text = {
            Column {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(44.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
                ) {
                    items(EMOJIS) { emoji ->
                        Box(
                            Modifier.size(44.dp).clip(CircleShape).clickable { onPick(emoji) },
                            contentAlignment = Alignment.Center,
                        ) { Text(emoji, fontSize = 24.sp) }
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = custom,
                    onValueChange = { custom = it.take(16) },
                    label = { Text("Saját emoji") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = custom.isNotBlank(), onClick = { onPick(custom.trim()) }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Mégse") } },
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }) {
                Text(confirmLabel, color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Mégse") } },
    )
}
