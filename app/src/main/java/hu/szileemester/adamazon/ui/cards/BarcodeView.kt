package hu.szileemester.adamazon.ui.cards

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import hu.szileemester.adamazon.domain.BarcodeEncoder
import hu.szileemester.adamazon.domain.CardBarcode

/** The card's code redrawn black on white, with its number underneath – what the till scans. */
@Composable
fun BarcodeView(barcode: CardBarcode, modifier: Modifier = Modifier) {
    val bitmap = remember(barcode) { render(barcode) }
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(Color.White).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Vonalkód",
                contentScale = ContentScale.FillBounds,
                // Nearest-neighbour scaling keeps the bar edges sharp.
                filterQuality = FilterQuality.None,
                modifier = Modifier.fillMaxWidth(if (barcode.isSquare) 0.75f else 1f)
                    .aspectRatio(if (barcode.isSquare) 1f else 2.6f),
            )
        }
        Text(
            barcode.value,
            color = Color.Black,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

private fun render(barcode: CardBarcode): Bitmap? {
    val (w, h) = if (barcode.isSquare) 600 to 600 else 1200 to 460
    val matrix = BarcodeEncoder.encode(barcode, w, h) ?: return null
    val pixels = IntArray(matrix.width * matrix.height) { i ->
        if (matrix[i % matrix.width, i / matrix.width]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
    }
    return Bitmap.createBitmap(pixels, matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
}
