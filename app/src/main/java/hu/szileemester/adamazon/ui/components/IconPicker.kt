package hu.szileemester.adamazon.ui.components

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import hu.szileemester.adamazon.domain.Links
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.material.icons.filled.Link
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hu.szileemester.adamazon.data.Sticker
import hu.szileemester.adamazon.data.StickerResult
import hu.szileemester.adamazon.domain.GroupIcon
import hu.szileemester.adamazon.domain.IconRef
import hu.szileemester.adamazon.domain.Normalizer
import hu.szileemester.adamazon.ui.appContainer
import kotlinx.coroutines.launch

/** Hand-picked shopping emoji, shown first. */
private val SHOPPING = (
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

/** Unicode groups in the order a shopper needs them, with their Hungarian titles. */
private val GROUP_TITLES = linkedMapOf(
    "food" to "Ételek és italok",
    "nature" to "Állatok és természet",
    "objects" to "Tárgyak",
    "activities" to "Tevékenységek",
    "travel" to "Utazás és helyek",
    "symbols" to "Szimbólumok",
    "smileys" to "Hangulatjelek",
    "people" to "Emberek",
    "flags" to "Zászlók",
)

private const val SEARCH_LIMIT = 300

/**
 * Full-screen icon picker: the group's photo stickers (new ones from a photo), the recently used
 * icons, shopping emoji and then every emoji by group – or, while typing, the search results.
 * [suggestedName] names a new sticker (usually the item's name).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun IconPicker(current: String, suggestedName: String = "", onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val container = appContainer()
    val catalog = remember { container.emojis }
    val recent by container.recentIcons.icons.collectAsStateWithLifecycle()
    val stickers by container.session.icons.icons.collectAsStateWithLifecycle(emptyList())
    val bitmaps = LocalIcons.current
    var query by remember { mutableStateOf("") }
    var making by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<StickerResult?>(null) }
    var deleting by remember { mutableStateOf<GroupIcon?>(null) }
    var linking by remember { mutableStateOf(false) }
    var linkFailed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun pick(icon: String) {
        container.recentIcons.used(icon)
        onPick(icon)
    }

    val photos = rememberPhotoPicker { uri: Uri ->
        making = true
        scope.launch {
            preview = runCatching { container.stickers.make(uri) }.getOrNull()
            making = false
        }
    }
    val sources = StickerSources(photos.fromCamera, photos.fromGallery, fromLink = { linking = true })

    fun loadLink(link: String) {
        linking = false
        making = true
        scope.launch {
            val result = runCatching { container.stickers.fromLink(link) }.getOrNull()
            making = false
            if (result == null) linkFailed = true else preview = result
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
                    IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Vissza") }
                    Text("Ikon választása", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    ItemIcon(current, size = 32.dp, modifier = Modifier.padding(end = 16.dp))
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Keresés, pl. sajt, kutya, mosószer") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = if (query.isNotEmpty()) {
                        { IconButton(onClick = { query = "" }) { Icon(Icons.Default.Clear, contentDescription = "Törlés") } }
                    } else null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(52.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                ) {
                    val words = Normalizer.key(query)
                    if (words.isNotBlank()) {
                        val ownHits = stickers.filter { Normalizer.key(it.name).contains(words) }
                        val hits = catalog.search(query).take(SEARCH_LIMIT)
                        items(ownHits, key = { "s-" + it.id }) { StickerCell(it, bitmaps, ::pick) { deleting = it } }
                        items(hits, key = { "q-" + it.emoji }) { EmojiCell(it.emoji, ::pick) }
                        if (ownHits.isEmpty() && hits.isEmpty()) {
                            header("Nincs találat. Próbáld más szóval, vagy készíts ikont fotóból:")
                            stickerSources(sources)
                        }
                    } else {
                        header("Saját képek")
                        stickerSources(sources)
                        items(stickers, key = { "s-" + it.id }) { StickerCell(it, bitmaps, ::pick) { deleting = it } }
                        // A deleted sticker is dropped from the recent ones, not shown as 🛒.
                        val recentAlive = recent.filter { icon -> IconRef.imageId(icon)?.let { id -> stickers.any { it.id == id } } ?: true }
                        if (recentAlive.isNotEmpty()) {
                            header("Legutóbbiak")
                            items(recentAlive, key = { "r-$it" }) { EmojiCell(it, ::pick) }
                        }
                        header("Vásárlás")
                        items(SHOPPING, key = { "v-$it" }) { EmojiCell(it, ::pick) }
                        GROUP_TITLES.forEach { (group, title) ->
                            val entries = catalog.inGroup(group)
                            if (entries.isNotEmpty()) {
                                header(title)
                                items(entries, key = { "$group-${it.emoji}" }) { EmojiCell(it.emoji, ::pick) }
                            }
                        }
                    }
                }
            }
        }
    }

    if (making) {
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = { Text("Matrica készítése…") },
            text = { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() } },
        )
    }

    preview?.let { result ->
        StickerPreviewDialog(
            cutOut = result.cutOut,
            round = result.round,
            modelMissing = result.modelMissing,
            suggestedName = suggestedName,
            onDismiss = { preview = null },
            onSave = { name, sticker ->
                preview = null
                pick(IconRef.of(container.session.icons.add(name, sticker.image)))
            },
        )
    }

    if (linking) LinkDialog(onDismiss = { linking = false }, onLoad = ::loadLink)

    if (linkFailed) {
        AlertDialog(
            onDismissRequest = { linkFailed = false },
            title = { Text("Nincs kép") },
            text = { Text("Ezen a linken nem találtam képet. Próbáld a kép saját linkjével (hosszan nyomva a képen: „Kép címének másolása”).") },
            confirmButton = { TextButton(onClick = { linkFailed = false }) { Text("OK") } },
        )
    }

    deleting?.let { icon ->
        ConfirmDialog(
            title = "„${icon.name}” törlése?",
            text = "A képet a csoport minden tagja elveszíti. Ahol használtátok, ott helyette 🛒 jelenik meg.",
            confirmLabel = "Törlés",
            onDismiss = { deleting = null },
            onConfirm = { container.session.icons.delete(icon.id) },
        )
    }
}

private fun LazyGridScope.header(title: String) {
    item(span = { GridItemSpan(maxLineSpan) }) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 4.dp),
        )
    }
}

/** Where a new sticker can come from. */
private class StickerSources(val fromCamera: () -> Unit, val fromGallery: () -> Unit, val fromLink: () -> Unit)

/** Fotó · Galéria · Link, side by side across the whole row. */
private fun LazyGridScope.stickerSources(sources: StickerSources) {
    item(key = "sources", span = { GridItemSpan(maxLineSpan) }) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            ActionCell(Icons.Default.PhotoCamera, "Fotó", sources.fromCamera, Modifier.weight(1f))
            ActionCell(Icons.Default.Image, "Galéria", sources.fromGallery, Modifier.weight(1f))
            ActionCell(Icons.Default.Link, "Link", sources.fromLink, Modifier.weight(1f))
        }
    }
}

@Composable
private fun EmojiCell(emoji: String, onPick: (String) -> Unit) {
    Box(
        Modifier.size(52.dp).clip(CircleShape).combinedClickable(onClick = { onPick(emoji) }),
        contentAlignment = Alignment.Center,
    ) { ItemIcon(emoji, size = 34.dp) }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StickerCell(
    icon: GroupIcon,
    bitmaps: Map<String, androidx.compose.ui.graphics.ImageBitmap>,
    onPick: (String) -> Unit,
    onLongPress: () -> Unit,
) {
    Box(
        Modifier
            .size(52.dp)
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(onClick = { onPick(IconRef.of(icon.id)) }, onLongClick = onLongPress),
        contentAlignment = Alignment.Center,
    ) {
        bitmaps[icon.id]?.let { Image(it, contentDescription = icon.name, modifier = Modifier.size(40.dp)) }
    }
}

@Composable
private fun ActionCell(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit, modifier: Modifier) {
    Column(
        modifier
            .height(56.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .combinedClickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(22.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

/** Shows the new sticker on light and dark backgrounds, with its name; the round crop is the fallback. */
@Composable
private fun StickerPreviewDialog(
    cutOut: Sticker?,
    round: Sticker,
    modelMissing: Boolean,
    suggestedName: String,
    onDismiss: () -> Unit,
    onSave: (name: String, sticker: Sticker) -> Unit,
) {
    var useRound by remember { mutableStateOf(cutOut == null) }
    var name by remember { mutableStateOf(suggestedName) }
    val sticker = if (useRound || cutOut == null) round else cutOut
    val bitmap = remember(sticker) { BitmapFactory.decodeByteArray(sticker.image, 0, sticker.image.size)?.asImageBitmap() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Új ikon") },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.inverseSurface).forEach { background ->
                        Box(
                            Modifier.weight(1f).height(110.dp).clip(RoundedCornerShape(12.dp)).background(background),
                            contentAlignment = Alignment.Center,
                        ) { bitmap?.let { Image(it, contentDescription = null, modifier = Modifier.size(88.dp)) } }
                    }
                }
                if (cutOut == null) {
                    Text(
                        if (modelMissing) {
                            "A kivágáshoz szükséges modul még töltődik, ezért most kör alakú részletet használok. Pár perc múlva próbáld újra."
                        } else {
                            "Nem találtam a képen kivágható terméket, ezért kör alakú részletet használok."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                        Text("Kivágás nélkül", modifier = Modifier.weight(1f))
                        Switch(checked = useRound, onCheckedChange = { useRound = it })
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(50) },
                    label = { Text("Név (a kereséshez)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onSave(Normalizer.clean(name), sticker) }) { Text("Mentés") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Mégse") } },
    )
}

/** Asks for an image or product-page link; a link on the clipboard is filled in. */
@Composable
private fun LinkDialog(onDismiss: () -> Unit, onLoad: (String) -> Unit) {
    val clipboard = LocalClipboardManager.current
    var link by remember {
        mutableStateOf(clipboard.getText()?.text?.let { Links.find(it).firstOrNull()?.url }.orEmpty())
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ikon linkből") },
        text = {
            Column {
                Text(
                    "Egy kép linkje, vagy egy webshop termékoldala (onnan a termék képét veszem).",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = link,
                    onValueChange = { link = it.trim() },
                    label = { Text("Link") },
                    placeholder = { Text("https://…") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(enabled = link.contains('.'), onClick = { onLoad(link) }) { Text("Betöltés") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Mégse") } },
    )
}
