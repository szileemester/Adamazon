package hu.szileemester.adamazon.ui.cards

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import hu.szileemester.adamazon.AppContainer
import hu.szileemester.adamazon.data.CardRepository
import hu.szileemester.adamazon.domain.CardBarcode
import hu.szileemester.adamazon.domain.LoyaltyCard
import hu.szileemester.adamazon.domain.Store
import hu.szileemester.adamazon.ui.appViewModel
import hu.szileemester.adamazon.ui.components.ConfirmDialog
import java.io.File
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The photo shown in the editor: the saved one, or a newly picked one with its scan result. */
sealed interface CardImage {
    data class Saved(val bytes: ByteArray, val barcode: CardBarcode?) : CardImage
    data class Picked(val uri: Uri, val scanning: Boolean, val barcode: CardBarcode?) : CardImage
    data object None : CardImage
}

class CardEditViewModel(private val c: AppContainer, private val cardId: String?) : ViewModel() {
    val stores = c.session.stores.stores.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    suspend fun load(): Pair<LoyaltyCard?, ByteArray?> {
        if (cardId == null) return null to null
        return c.session.cards.card(cardId).first() to c.session.cards.image(cardId).first()
    }

    suspend fun scan(uri: Uri): CardBarcode? = c.barcodes.read(uri)

    suspend fun save(name: String, storeId: String?, image: CardImage) {
        val newImage = (image as? CardImage.Picked)?.let {
            CardRepository.NewImage(c.images.cardImage(it.uri), c.images.thumbnail(it.uri), it.barcode)
        }
        val count = c.session.cards.cards.first().size
        c.session.cards.save(cardId, name, storeId, sortIndex = count, newImage = newImage)
    }

    fun delete() = cardId?.let(c.session.cards::delete)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardEditScreen(cardId: String?, back: () -> Unit, deleted: () -> Unit) {
    val vm = appViewModel(key = "card-edit-${cardId ?: "new"}") { CardEditViewModel(it, cardId) }
    val stores by vm.stores.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var loaded by remember { mutableStateOf(cardId == null) }
    var name by remember { mutableStateOf("") }
    var storeId by remember { mutableStateOf<String?>(null) }
    var image by remember { mutableStateOf<CardImage>(CardImage.None) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(cardId) {
        if (cardId == null) return@LaunchedEffect
        val (card, bytes) = vm.load()
        name = card?.name.orEmpty()
        storeId = card?.storeId
        bytes?.let { image = CardImage.Saved(it, card?.barcode) }
        loaded = true
    }

    fun picked(uri: Uri) {
        image = CardImage.Picked(uri, scanning = true, barcode = null)
        scope.launch {
            val barcode = vm.scan(uri)
            // Ignore the result if another photo was picked meanwhile.
            if ((image as? CardImage.Picked)?.uri == uri) image = CardImage.Picked(uri, scanning = false, barcode = barcode)
        }
    }

    val scanning = (image as? CardImage.Picked)?.scanning == true
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (cardId == null) "Új kártya" else "Kártya szerkesztése") },
                navigationIcon = {
                    IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Vissza") }
                },
                actions = {
                    if (saving) {
                        CircularProgressIndicator(Modifier.padding(12.dp))
                    } else {
                        IconButton(
                            enabled = loaded && name.isNotBlank() && image != CardImage.None && !scanning,
                            onClick = {
                                saving = true
                                scope.launch {
                                    runCatching { vm.save(name.trim(), storeId, image) }
                                        .onSuccess { back() }
                                        .onFailure {
                                            error = "Nem sikerült menteni: ${it.message}"
                                            saving = false
                                        }
                                }
                            },
                        ) { Icon(Icons.Default.Check, contentDescription = "Mentés") }
                    }
                },
            )
        },
    ) { padding ->
        if (!loaded) return@Scaffold
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(name, { name = it }, label = { Text("Név, pl. Auchan kártya") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            StorePicker(stores, storeId) { storeId = it }
            ImageSlot(image, context, onPicked = ::picked)
            BarcodeStatus(image)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (cardId != null) {
                TextButton(onClick = { confirmDelete = true }) { Text("Kártya törlése", color = MaterialTheme.colorScheme.error) }
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Törlöd a kártyát?",
            text = "A kártya képe véglegesen törlődik mindkettőtöknél.",
            confirmLabel = "Törlés",
            onDismiss = { confirmDelete = false },
            onConfirm = {
                vm.delete()
                deleted()
            },
        )
    }
}

@Composable
private fun BarcodeStatus(image: CardImage) {
    val (scanning, barcode) = when (image) {
        is CardImage.Picked -> image.scanning to image.barcode
        is CardImage.Saved -> false to image.barcode
        CardImage.None -> return
    }
    when {
        scanning -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(12.dp))
            Text("Vonalkód keresése a képen…")
        }
        barcode != null -> Column {
            Text("Felismert vonalkód (${barcode.label}) – a pénztárnál ez jelenik meg:", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.size(8.dp))
            BarcodeView(barcode, Modifier.fillMaxWidth())
        }
        else -> Text(
            "Nem találtam vonalkódot a képen, ezért a pénztárnál maga a kép jelenik meg. " +
                "Élesebb, egyenesen fotózott kép segíthet.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StorePicker(stores: List<Store>, selectedId: String?, onSelect: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val selected = stores.firstOrNull { it.id == selectedId }
    Column {
        Text("Üzlethez kötve (a listáról egy gombbal elérhető)", style = MaterialTheme.typography.labelLarge)
        Box {
            OutlinedButton(onClick = { open = true }) { Text(selected?.name ?: "Nincs üzlethez kötve") }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                DropdownMenuItem(text = { Text("Nincs üzlethez kötve") }, onClick = { onSelect(null); open = false })
                stores.forEach { store ->
                    DropdownMenuItem(text = { Text(store.name) }, onClick = { onSelect(store.id); open = false })
                }
            }
        }
    }
}

@Composable
private fun ImageSlot(image: CardImage, context: Context, onPicked: (Uri) -> Unit) {
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(onPicked)
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) cameraUri?.let(onPicked)
    }

    Column {
        Text("Kártya képe", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.padding(top = 4.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(CARD_RATIO)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            when (image) {
                is CardImage.Saved -> AsyncImage(image.bytes, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                is CardImage.Picked -> AsyncImage(image.uri, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                CardImage.None -> Text("Nincs kép", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                Icon(Icons.Default.Image, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Galéria")
            }
            TextButton(onClick = {
                val uri = newCameraUri(context)
                cameraUri = uri
                camera.launch(uri)
            }) {
                Icon(Icons.Default.PhotoCamera, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Fénykép")
            }
        }
    }
}

/** The system camera app writes the photo here; no camera permission is needed. */
private fun newCameraUri(context: Context): Uri {
    val dir = File(context.cacheDir, "camera").apply { mkdirs() }
    val file = File(dir, "card_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}
