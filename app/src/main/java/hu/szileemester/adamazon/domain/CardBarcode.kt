package hu.szileemester.adamazon.domain

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix

/** A loyalty card's code: its content and symbology, stored by ZXing [BarcodeFormat] name. */
data class CardBarcode(val value: String, val format: String) {
    val barcodeFormat: BarcodeFormat? get() = runCatching { BarcodeFormat.valueOf(format) }.getOrNull()

    /** QR, Data Matrix and Aztec are square; everything else is a wide 1D/stacked code. */
    val isSquare: Boolean
        get() = barcodeFormat in setOf(BarcodeFormat.QR_CODE, BarcodeFormat.DATA_MATRIX, BarcodeFormat.AZTEC)

    val label: String
        get() = when (barcodeFormat) {
            BarcodeFormat.EAN_13 -> "EAN-13"
            BarcodeFormat.EAN_8 -> "EAN-8"
            BarcodeFormat.UPC_A -> "UPC-A"
            BarcodeFormat.UPC_E -> "UPC-E"
            BarcodeFormat.CODE_128 -> "Code 128"
            BarcodeFormat.CODE_39 -> "Code 39"
            BarcodeFormat.CODE_93 -> "Code 93"
            BarcodeFormat.CODABAR -> "Codabar"
            BarcodeFormat.ITF -> "ITF"
            BarcodeFormat.QR_CODE -> "QR-kód"
            BarcodeFormat.DATA_MATRIX -> "Data Matrix"
            BarcodeFormat.PDF_417 -> "PDF417"
            BarcodeFormat.AZTEC -> "Aztec"
            else -> format
        }
}

object BarcodeEncoder {
    /**
     * Draws the code as a crisp black-and-white matrix. A clean rendering scans far more reliably
     * at the till than the original photo. Returns null if the value cannot be encoded.
     */
    fun encode(barcode: CardBarcode, width: Int, height: Int): BitMatrix? {
        val format = barcode.barcodeFormat ?: return null
        // Scanners need a blank "quiet zone" around the code: ~10 modules for 1D codes, 4 for 2D.
        val margin = if (barcode.isSquare) 4 else 10
        return runCatching {
            MultiFormatWriter().encode(barcode.value, format, width, height, mapOf(EncodeHintType.MARGIN to margin))
        }.getOrNull()
    }
}
