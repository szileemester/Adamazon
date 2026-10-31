package hu.szileemester.adamazon.data

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import hu.szileemester.adamazon.domain.CardBarcode
import kotlinx.coroutines.tasks.await

/** Finds the barcode on a card photo, on the device (ML Kit, bundled model – works offline). */
class BarcodeReader(private val context: Context) {
    private val scanner by lazy { BarcodeScanning.getClient() }

    suspend fun read(uri: Uri): CardBarcode? = runCatching {
        val barcodes = scanner.process(InputImage.fromFilePath(context, uri)).await()
        barcodes.firstNotNullOfOrNull { barcode ->
            val value = barcode.rawValue?.takeIf { it.isNotEmpty() } ?: return@firstNotNullOfOrNull null
            FORMATS[barcode.format]?.let { CardBarcode(value, it) }
        }
    }.getOrNull()

    private companion object {
        /** ML Kit format → ZXing format name, which is what the card stores and redraws. */
        val FORMATS = mapOf(
            Barcode.FORMAT_EAN_13 to "EAN_13",
            Barcode.FORMAT_EAN_8 to "EAN_8",
            Barcode.FORMAT_UPC_A to "UPC_A",
            Barcode.FORMAT_UPC_E to "UPC_E",
            Barcode.FORMAT_CODE_128 to "CODE_128",
            Barcode.FORMAT_CODE_39 to "CODE_39",
            Barcode.FORMAT_CODE_93 to "CODE_93",
            Barcode.FORMAT_CODABAR to "CODABAR",
            Barcode.FORMAT_ITF to "ITF",
            Barcode.FORMAT_QR_CODE to "QR_CODE",
            Barcode.FORMAT_DATA_MATRIX to "DATA_MATRIX",
            Barcode.FORMAT_PDF417 to "PDF_417",
            Barcode.FORMAT_AZTEC to "AZTEC",
        )
    }
}
