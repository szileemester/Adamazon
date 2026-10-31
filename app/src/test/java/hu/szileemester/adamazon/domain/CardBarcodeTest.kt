package hu.szileemester.adamazon.domain

import com.google.zxing.BinaryBitmap
import com.google.zxing.LuminanceSource
import com.google.zxing.MultiFormatReader
import com.google.zxing.common.BitMatrix
import com.google.zxing.common.HybridBinarizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CardBarcodeTest {

    /** Renders the code and reads it back with ZXing, proving a scanner could read our drawing. */
    private fun roundTrip(barcode: CardBarcode, width: Int = 600, height: Int = 200): String {
        val matrix = BarcodeEncoder.encode(barcode, width, height)!!
        val result = MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(MatrixSource(matrix))))
        assertEquals(barcode.format, result.barcodeFormat.name)
        return result.text
    }

    @Test
    fun `typical loyalty card formats survive rendering`() {
        assertEquals("5998817311417", roundTrip(CardBarcode("5998817311417", "EAN_13")))
        assertEquals("AUCHAN-1234567", roundTrip(CardBarcode("AUCHAN-1234567", "CODE_128")))
        assertEquals("DM 99 001", roundTrip(CardBarcode("DM 99 001", "QR_CODE"), 400, 400))
    }

    @Test
    fun `invalid content or unknown format gives no drawing`() {
        assertNull(BarcodeEncoder.encode(CardBarcode("not digits", "EAN_13"), 600, 200))
        assertNull(BarcodeEncoder.encode(CardBarcode("123", "NOPE"), 600, 200))
    }

    @Test
    fun `square formats are recognised`() {
        assertEquals(true, CardBarcode("x", "QR_CODE").isSquare)
        assertEquals(false, CardBarcode("x", "EAN_13").isSquare)
        assertEquals("EAN-13", CardBarcode("x", "EAN_13").label)
    }

    private class MatrixSource(private val m: BitMatrix) : LuminanceSource(m.width, m.height) {
        override fun getRow(y: Int, row: ByteArray?): ByteArray {
            val out = if (row != null && row.size >= width) row else ByteArray(width)
            for (x in 0 until width) out[x] = if (m[x, y]) 0 else 255.toByte()
            return out
        }

        override fun getMatrix(): ByteArray = ByteArray(width * height).also { out ->
            for (y in 0 until height) for (x in 0 until width) out[y * width + x] = if (m[x, y]) 0 else 255.toByte()
        }
    }
}
