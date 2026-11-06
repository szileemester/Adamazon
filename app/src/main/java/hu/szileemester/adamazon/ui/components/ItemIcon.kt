package hu.szileemester.adamazon.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hu.szileemester.adamazon.domain.GroupIcon
import hu.szileemester.adamazon.domain.IconRef
import androidx.compose.material3.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/** The group's photo stickers, decoded once, by id. */
val LocalIcons = staticCompositionLocalOf<Map<String, ImageBitmap>> { emptyMap() }

/** Shown for a sticker that was deleted (or has not synced yet). */
const val MISSING_ICON = "🛒"

/** Makes the group's stickers available to every [ItemIcon] in [content]. */
@Composable
fun ProvideIcons(icons: Flow<List<GroupIcon>>, content: @Composable () -> Unit) {
    val list by icons.collectAsStateWithLifecycle(emptyList())
    val bitmaps by produceState(emptyMap<String, ImageBitmap>(), list) {
        value = withContext(Dispatchers.Default) { list.mapNotNull { icon -> decodeSticker(icon.image)?.let { icon.id to it } }.toMap() }
    }
    CompositionLocalProvider(LocalIcons provides bitmaps, content = content)
}

/**
 * A sticker's picture, or null for one that is not a sane small image: the rules cap the bytes,
 * not the pixels, and one huge picture would otherwise crash every member's app on start.
 */
private fun decodeSticker(bytes: ByteArray): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || maxOf(bounds.outWidth, bounds.outHeight) > MAX_STICKER_SIDE) return null
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
}

private const val MAX_STICKER_SIDE = 512

/** An item or category icon: an emoji, or a photo sticker (see [IconRef]) drawn at the same size. */
@Composable
fun ItemIcon(icon: String, size: Dp = 24.dp, modifier: Modifier = Modifier) {
    val imageId = IconRef.imageId(icon)
    val bitmap = imageId?.let { LocalIcons.current[it] }
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        when {
            bitmap != null -> Image(bitmap, contentDescription = null, modifier = Modifier.size(size))
            // Emoji glyphs fill about 80 % of their font size; this keeps them as big as the stickers.
            else -> Text(if (imageId != null) MISSING_ICON else icon, fontSize = (size.value * 0.8f).sp, maxLines = 1)
        }
    }
}
